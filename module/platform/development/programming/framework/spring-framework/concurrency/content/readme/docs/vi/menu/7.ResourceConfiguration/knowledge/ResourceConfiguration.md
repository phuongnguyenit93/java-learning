<a id="back-to-top"></a>

# Cấu hình tài nguyên

## Menu
- [Tách bộ thực thi cho các nhóm công việc có đặc điểm khác nhau](#resource-configuration-001)
- [Chọn số luồng cho tác vụ nặng CPU và tác vụ thường chờ nhập xuất](#resource-configuration-002)
- [Giới hạn theo số kết nối cơ sở dữ liệu hoặc dịch vụ đích](#resource-configuration-003)
- [Chọn sức chứa hàng đợi theo bộ nhớ và thời gian chờ cho phép](#resource-configuration-004)
- [Phân biệt giới hạn đồng thời với giới hạn số yêu cầu theo thời gian](#resource-configuration-005)
- [Ước lượng công suất rồi kiểm chứng bằng phép đo tải](#resource-configuration-006)
- [Cấu hình bằng @Bean hoặc namespace task trong XML](#resource-configuration-007)
- [Đưa tham số ra cấu hình ngoài và kiểm tra giá trị không hợp lệ](#resource-configuration-008)
- [Phân biệt cấu hình Spring Framework với tự động cấu hình Spring Boot](#resource-configuration-009)

## <a id="resource-configuration-001">Tách bộ thực thi cho các nhóm công việc có đặc điểm khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Tách bộ thực thi cho các nhóm công việc có đặc điểm khác nhau trong Spring Framework

## Mục tiêu

Ứng dụng Spring thường đồng thời xử lý nhiều nhóm tác vụ: kiểm tra nghiệp vụ ngắn cần phản hồi nhanh, tính toán CPU, gọi API bên ngoài có thể chờ lâu, đồng bộ dữ liệu và tạo báo cáo nền. Nếu mọi nhóm dùng chung một executor/hàng đợi, tác vụ chậm có thể chiếm toàn bộ worker, khiến tác vụ nhanh phải xếp hàng và trễ hạn.

Bài học trình bày cách **phân nhóm tác vụ theo đặc tính tài nguyên và mục tiêu vận hành**, cấp `ThreadPoolTaskExecutor` riêng cho từng nhóm, cấu hình số luồng, hàng đợi, chính sách từ chối, timeout, vòng đời và quan sát số liệu. Qua một ví dụ chạy được với Java 17+ và Spring Framework, bạn sẽ kiểm chứng tác vụ nhanh có thể hoàn thành khi nhóm chậm vẫn đang bị chặn.

Sau bài này, bạn có thể:

1. Phân biệt công việc CPU-bound, blocking I/O, latency-sensitive, background/batch và tác vụ phụ thuộc dịch vụ ngoài dễ nghẽn.
2. Giải thích tại sao một pool dùng chung tạo head-of-line blocking, và vì sao nhiều pool giúp cô lập một phần tải theo mô hình **bulkhead**.
3. Tạo nhiều bean executor, chọn đúng pool bằng `@Async("beanName")` hoặc `@Qualifier` và tránh sai sót do proxy.
4. Chọn thông số core/max/queue/rejection phù hợp với giới hạn tài nguyên và dữ liệu đo được.
5. Thiết kế lỗi, timeout, giới hạn downstream, logging context, shutdown và metrics cho từng nhóm.
6. Hiểu giới hạn: tách thread pool không tự tách CPU, database connection, heap, lock hay quota dịch vụ bên ngoài.

**Điểm cốt lõi:** Một executor là nơi tiếp nhận và phân phối tác vụ; việc tách executor giúp tránh *worker và queue của nhóm A* bị nhóm B chiếm. Nó không tự làm công việc blocking trở thành non-blocking hoặc bảo đảm độ trễ tối đa trong mọi tình huống.

## 1. Một executor chung làm nghẽn nhóm nhanh như thế nào?

Giả sử pool chung chỉ có hai worker. Hai tác vụ gọi API ngoài bằng HTTP client blocking đều treo khi chờ phản hồi. Một tác vụ tính toán nhẹ được gửi sau:

```text
                      SHARED EXECUTOR
                       queue [FAST]
                               |
                 +-------------+--------------+
                 |                            |
            worker 1                       worker 2
            SLOW A                         SLOW B
            chờ network                   chờ network

              FAST phải đợi worker rảnh dù chỉ cần 2 ms CPU.
```

Thời gian đợi của FAST đến từ **queueing delay**, không phải độ phức tạp phép tính. Nếu pool và queue có hạn, FAST còn có thể bị từ chối khi cả hai đã đầy. Nếu queue rất lớn, caller có thể nhận `Future` sớm nhưng kết quả thực tế trễ rất lâu.

Phân nhóm theo nhu cầu:

```text
FAST tasks -> [fastExecutor queue] -> [fast workers] -> response
SLOW tasks -> [slowExecutor queue] -> [slow workers] -> integration result
BATCH      -> [batchExecutor queue] -> [batch workers] -> background result
```

Một slow worker chờ I/O không lấy mất fast worker. Nhóm có mức ưu tiên cao có thể có queue, budget thời gian và chính sách quá tải khác. Đây là **cách ly tại tầng thread execution**; các pool vẫn chạy trong cùng process/JVM và có thể tranh chấp CPU, bộ nhớ hoặc connection.

Tách pool đáng giá khi nhóm có khác biệt rõ về thời gian chạy, phương thức chờ, SLA, giới hạn downstream hoặc hậu quả khi quá tải. Không tạo một pool riêng cho mỗi method một cách máy móc: quá nhiều pool làm tăng tổng thread, heap, chi phí quản lý và khó cân bằng tài nguyên.

## 2. Nhận dạng nhóm công việc

| Đặc tính | Ví dụ | Hướng tiếp cận |
| --- | --- | --- |
| **CPU-bound** | Hash, tính toán lớn, xử lý dữ liệu trong bộ nhớ | Giới hạn worker gần ngân sách CPU thực tế; đo CPU, p95/p99 và context switching. |
| **Blocking I/O** | JDBC, HTTP client blocking, đọc file chậm | Có thể cần nhiều worker hơn CPU nhưng phải giới hạn theo connection pool, timeout, quota, memory. |
| **Latency-sensitive** | Bước bắt buộc để trả response API | Cấp capacity và queue riêng, theo dõi độ trễ đuôi, có fail-fast/overload policy. |
| **Background/batch** | Tạo báo cáo, rebuild index, đồng bộ theo đợt | Giới hạn throughput riêng, không chen vào fast pool; muốn bền vững cần job store/broker. |
| **Unreliable downstream** | Dịch vụ bên thứ ba có timeout/429 nhiều | Pool/bulkhead riêng, timeout, rate limit, circuit breaker và retry hữu hạn khi phù hợp. |
| **Scheduled work** | Job chạy mỗi phút/đêm | `TaskScheduler` quyết định lịch; có thể giao phần việc nặng sang executor khác. |

Các nhãn có thể trùng nhau. Ví dụ “gọi thanh toán” vừa là I/O chậm tiềm tàng, vừa latency-sensitive và bị quota giới hạn. Quyết định nhóm cần dựa trên **hành vi thực tế**, không chỉ tên package.

Trước khi phân nhóm, thu thập: request/task per second, thời gian CPU, thời gian I/O wait, queue wait, p95/p99, timeout, số tác vụ đồng thời, số connection DB/HTTP được phép và giới hạn CPU container. Số luồng hợp lý được quyết định bởi cả những tài nguyên này.

## 3. Spring cung cấp những thành phần gì?

`TaskExecutor` là abstraction của Spring cho việc chạy `Runnable`, trên nền khái niệm `Executor` của JDK. `AsyncTaskExecutor` mở rộng khả năng submit tác vụ và nhận `Future`. `ThreadPoolTaskExecutor` là bean thuận tiện bọc `java.util.concurrent.ThreadPoolExecutor`, cho phép cấu hình pool qua API Spring.

`@EnableAsync` kích hoạt xử lý các phương thức `@Async`; `@Async("fastExecutor")` chọn bean executor tên `fastExecutor`, còn `@Async("slowExecutor")` chọn bean khác. Khi không chỉ định tên, Spring dùng cơ chế chọn executor mặc định (cấu hình `AsyncConfigurer`, bean phù hợp, hoặc fallback tùy môi trường). **Không nên để việc lựa chọn pool quan trọng phụ thuộc vào phỏng đoán về mặc định.**

Dòng chảy lời gọi:

```text
Caller thread
  -> Spring proxy của WorkService
       -> interceptor đọc @Async("slowExecutor")
       -> lấy bean slowExecutor
       -> submit method invocation
              -> slow worker thực thi thân method
              -> hoàn tất Future hoặc báo lỗi

Caller có Future/CompletableFuture, chưa chắc đã có value.
```

Cơ chế proxy mặc định chỉ chặn lời gọi **đi qua proxy**. Nếu cùng bean gọi `this.slowMethod()`, annotation thường không phát huy tác dụng. Đồng thời, `@Async` không tự động thay đổi bean scope, thread safety hay transaction context.

Hai bean `ThreadPoolTaskExecutor` thực sự độc lập khi chúng sở hữu các thread pool bên dưới **khác nhau**. Nếu hai adapter cùng bọc một JDK `Executor` duy nhất, việc đặt hai tên bean không tạo cách ly thực chất. Hãy kiểm tra cả instance delegate và metrics.

## 4. Hiểu đúng core, max, queue và rejection

| Thuộc tính | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `corePoolSize` | Mức worker lõi mà `ThreadPoolExecutor` có thể duy trì. | Không phải giới hạn concurrency tối đa khi max lớn hơn. |
| `maxPoolSize` | Số worker tối đa của pool. | Không tăng ngay tới max chỉ vì có task mới nếu queue còn nhận được. |
| `queueCapacity` | Giới hạn số task đang chờ. | Queue dương thường được cấp dưới dạng `LinkedBlockingQueue`; zero dùng `SynchronousQueue`. |
| `threadNamePrefix` | Nhận diện worker trong log/thread dump. | Không thay thế việc đo số worker, queue wait và rejection. |
| `RejectedExecutionHandler` | Quyết định khi pool không còn khả năng nhận thêm. | Policy có thể ném lỗi, chạy trên caller hoặc bỏ task. |
| Lifecycle | Đóng pool và xử lý task đang chạy/chờ khi context shutdown. | Cần chính sách timeout và xử lý công việc còn dở. |

Với `ThreadPoolExecutor` có queue hữu hạn dương, trình tự thường là: tạo worker cho tới mức core; sau đó xếp task vào queue khi còn chỗ; khi queue đầy mới tăng worker tới max; cuối cùng reject nếu không còn capacity. Vì vậy:

```text
core=2, max=20, queue=10000
```

không có nghĩa pool nhanh chóng tạo 20 worker khi có 100 tác vụ. Nó có thể giữ hai worker và xếp phần lớn task vào queue. Cấu hình queue quá lớn đôi khi che lấp overload, làm p99 và memory xấu hơn.

Với `queueCapacity=0`, không có hàng đợi chứa task chờ thông thường; pool có thể tạo worker tới max và từ chối sớm khi đã bão hòa. Không có con số “đúng” mặc định: phải lựa chọn theo SLA, lượng tải burst, thời gian xử lý, tổng memory và chính sách hạ nguồn.

## 5. Ví dụ chạy được: FAST hoàn tất khi SLOW vẫn chờ

Ví dụ dùng Java 17+, Maven, Spring Framework `6.2.12`; **không yêu cầu Spring Boot hoặc HTTP server**. Cấu hình hai `ThreadPoolTaskExecutor`:

- `fastExecutor`: một worker, queue bốn task; một tác vụ nhẹ cần hoàn thành sớm.
- `slowExecutor`: một worker, queue hai task; mô phỏng công việc I/O chờ lâu.

Thay vì `Thread.sleep()` rồi đo thời gian (dễ không ổn định), dùng `CountDownLatch` để **chủ động giữ slow worker**, xác nhận worker đã bắt đầu, rồi gửi FAST. Nếu FAST hoàn tất khi latch vẫn khóa thì hai pool đã cung cấp vùng thực thi tách biệt trong ví dụ.

Cấu trúc thư mục:

```text
separate-executors-demo/
├── pom.xml
└── src/main/java/example/SeparateExecutorsDemo.java
```

Tạo `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>separate-executors-demo</artifactId>
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
                    <mainClass>example.SeparateExecutorsDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tạo `src/main/java/example/SeparateExecutorsDemo.java`:

```java
package example;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class SeparateExecutorsDemo {

    public record Result(String value, String threadName) {
    }

    public static class Gates {
        final CountDownLatch slowStarted = new CountDownLatch(1);
        final CountDownLatch allowSlowFinish = new CountDownLatch(1);
    }

    public static class WorkService {
        private final Gates gates;

        public WorkService(Gates gates) {
            this.gates = gates;
        }

        @Async("slowExecutor")
        public CompletableFuture<Result> slowIntegration(String id) {
            gates.slowStarted.countDown();
            try {
                if (!gates.allowSlowFinish.await(10, TimeUnit.SECONDS)) {
                    return CompletableFuture.failedFuture(
                        new IllegalStateException("Slow task timed out")
                    );
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return CompletableFuture.failedFuture(ex);
            }

            return CompletableFuture.completedFuture(
                new Result("slow:" + id, Thread.currentThread().getName())
            );
        }

        @Async("fastExecutor")
        public CompletableFuture<Result> fastCalculation(String id) {
            return CompletableFuture.completedFuture(
                new Result("fast:" + id, Thread.currentThread().getName())
            );
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
        public WorkService workService(Gates gates) {
            return new WorkService(gates);
        }

        @Bean("fastExecutor")
        public ThreadPoolTaskExecutor fastExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(4);
            executor.setThreadNamePrefix("fast-");
            return executor;
        }

        @Bean("slowExecutor")
        public ThreadPoolTaskExecutor slowExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(2);
            executor.setThreadNamePrefix("slow-");
            return executor;
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(Config.class)) {

            Gates gates = context.getBean(Gates.class);
            WorkService service = context.getBean(WorkService.class);
            ThreadPoolTaskExecutor fastPool =
                context.getBean("fastExecutor", ThreadPoolTaskExecutor.class);
            ThreadPoolTaskExecutor slowPool =
                context.getBean("slowExecutor", ThreadPoolTaskExecutor.class);

            CompletableFuture<Result> slow = service.slowIntegration("A");

            try {
                if (!gates.slowStarted.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Slow worker did not start");
                }

                CompletableFuture<Result> fast = service.fastCalculation("B");
                Result fastResult = fast.get(10, TimeUnit.SECONDS);

                boolean fastFinishedWhileSlowBlocked = !slow.isDone();

                gates.allowSlowFinish.countDown();
                Result slowResult = slow.get(10, TimeUnit.SECONDS);

                System.out.println("separateExecutors=" + (fastPool != slowPool));
                System.out.println("fastFinishedWhileSlowBlocked="
                    + fastFinishedWhileSlowBlocked);
                System.out.println("fastUsesFastPool="
                    + fastResult.threadName().startsWith("fast-"));
                System.out.println("slowUsesSlowPool="
                    + slowResult.threadName().startsWith("slow-"));
                System.out.println("fastValue=" + fastResult.value());
                System.out.println("slowValue=" + slowResult.value());
            } finally {
                gates.allowSlowFinish.countDown();
            }
        }
    }
}
```

Chạy trong thư mục `separate-executors-demo`:

```bash
mvn -q compile exec:java
```

Sáu dòng do **chương trình** in ra khi chạy thành công (log Maven nếu có nằm ngoài các dòng này):

```text
separateExecutors=true
fastFinishedWhileSlowBlocked=true
fastUsesFastPool=true
slowUsesSlowPool=true
fastValue=fast:B
slowValue=slow:A
```

### Diễn giải luồng thực thi và kết quả

1. Context tạo hai executor bean khác nhau, mỗi bean có worker pool riêng. So sánh `fastPool != slowPool` cho `true`.
2. Caller nhận `CompletableFuture<Result>` khi gửi `slowIntegration("A")` qua Spring proxy. Worker slow gọi `slowStarted.countDown()` rồi chờ `allowSlowFinish`.
3. Main chờ `slowStarted.await(...)`, do đó biết task chậm đã thực sự bắt đầu.
4. Main gọi `fastCalculation("B")` qua proxy, lần này dùng `fastExecutor` chứ không chờ worker slow. `fast.get(...)` hoàn tất trước khi main nhả slow latch.
5. Biểu thức `!slow.isDone()` cho `true` trước khi latch được nhả. Đây là bằng chứng có kiểm soát rằng nhóm nhanh chạy độc lập với worker đang chờ của nhóm chậm.
6. Main nhả latch, lấy kết quả slow rồi in theo thứ tự xác định; các tiền tố `fast-`/`slow-` chứng minh tên worker tương ứng.

Cặp latch chỉ dành cho **thí nghiệm**. Ứng dụng thật phải dùng timeout HTTP/database và kiểm soát concurrency thích hợp, không giữ worker bằng latch như xử lý nghiệp vụ. Con số một worker mỗi pool nhằm dễ quan sát, không phải công thức cấu hình production.

Với `@Async`, phương thức thật trả `CompletableFuture.completedFuture(result)` **trong worker**; Spring interceptor trả một completion handle cho caller. Điều đó không biến thân phương thức thành lời gọi đồng bộ trên caller. Nếu caller gọi `.get()` hoặc `.join()` ngay, caller vẫn có thể chờ.

## 6. Các cách chọn executor theo nhóm

### Dùng @Async theo tên

`@Async("fastExecutor")` và `@Async("slowExecutor")` là lựa chọn trực quan khi công việc được tổ chức thành method của bean Spring. Đặt tên bean theo **mục đích và SLA**, như `paymentExecutor`, `externalIoExecutor`, `reportExecutor`, thay vì `pool1`, `pool2`.

Nếu một phương thức được gọi qua `this.method()` trong chính bean, lời gọi không qua proxy mặc định và annotation không chuyển công việc sang executor dự kiến. Giải pháp thường là tách tác vụ async sang bean khác được inject rồi gọi qua reference của Spring.

### Inject executor tường minh

Khi cần xây nhiều task trong một phương thức, hoặc service điều phối task động, inject executor theo qualifier:

```java
@Service
public class ReportDispatcher {
    private final AsyncTaskExecutor reportExecutor;

    public ReportDispatcher(
            @Qualifier("reportExecutor") AsyncTaskExecutor reportExecutor) {
        this.reportExecutor = reportExecutor;
    }

    public Future<String> dispatch(String id) {
        return reportExecutor.submit(() -> "report:" + id);
    }
}
```

Đây là snippet cho ứng dụng Spring, không phải file Java độc lập; cần import Spring annotation, `AsyncTaskExecutor`, `Future` và khai báo bean `reportExecutor`. Ý nghĩa: mỗi lần `dispatch` gửi một `Callable` tới **pool đã được chọn** và trả handle; mã nghiệp vụ phải tiếp tục thiết kế xử lý lỗi, timeout và tính bền vững.

### Executor mặc định

`AsyncConfigurer` có thể cung cấp executor mặc định cho phương thức `@Async` không ghi giá trị. Chỉ dùng default khi ranh giới tài nguyên không quan trọng hoặc đã thống nhất chính sách. Với các nhóm cần cách ly, `@Async("namedExecutor")` giúp reviewers và vận hành nhìn thấy rõ pool nào chịu tải.

Spring không tự phân tích rằng method “gọi API” nên vào I/O pool hay method “tính toán” nên vào CPU pool. Phân loại và routing là quyết định của thiết kế.

## 7. Cách ước lượng số thread cho từng nhóm

### CPU-bound

Với tác vụ dùng CPU liên tục, số worker hữu hiệu thường gần số CPU khả dụng của tiến trình/container, còn phụ thuộc workload khác và mục tiêu độ trễ. Ví dụ một container có hai CPU không nên mặc nhiên cấp năm executor CPU-bound, mỗi executor tám worker, rồi kỳ vọng 40 thread chạy song song thực sự. Tổng CPU vẫn chỉ khoảng hai CPU; có thể tăng contention và context switching.

Bắt đầu với ngân sách CPU tổng thể, đo throughput, CPU utilization, p95/p99 và thời gian queue. Điều chỉnh dựa trên profile và giới hạn CPU quota thực tế.

### Blocking I/O

Nếu task có nhiều thời gian chờ network/DB, pool có thể cần nhiều worker hơn CPU. Một công thức sơ bộ trong mô hình nhất định là:

\[
N_{\text{threads}} \approx N_{\text{CPU}} \times U_{\text{target}} \times (1 + W/C)
\]

Ở đây `W` là thời gian chờ I/O, `C` là thời gian CPU, `U_target` là mức sử dụng CPU mục tiêu. **Đây là giả định bắt đầu**, không phải quy tắc áp dụng cơ học. Pool 100 worker không hữu ích khi DB chỉ cấp 10 connection hoặc API giới hạn 20 calls đồng thời. Ràng buộc thực thường là min của CPU, connection pool, quota, memory, timeout và ngân sách downstream.

Theo Little's Law ở tải ổn định, lượng công việc đồng thời trung bình liên hệ với tốc độ đến và thời gian xử lý/ở trong hệ thống. Chẳng hạn 20 request/s, mỗi request cần khoảng 0,25 s I/O, thì trung bình có khoảng 5 request đang trong giai đoạn đó. Phải dự phòng burst và đo latency thực; không coi tính toán trung bình là cam kết p99.

### Nhóm yêu cầu latency thấp

Queue nhỏ có kiểm soát có thể bảo vệ deadline hơn queue rất lớn. Nếu đầu vào vượt khả năng phục vụ, cần fail-fast, degraded response, retry sau, hoặc admission control. Một `Future` được trả ngay khi submit không có nghĩa kết quả có mặt trước deadline.

### Background và batch

Có thể chấp nhận nhiều thời gian chờ hơn nhưng vẫn phải giới hạn queue/memory. Những việc phải hoàn tất sau restart **không thể chỉ dựa vào queue trong `ThreadPoolTaskExecutor`**, vì RAM không bền vững. Dùng persistent job store/broker và thiết kế idempotency khi cần.

## 8. Backpressure và chính sách từ chối

| Policy | Khi bão hòa | Cần lưu ý |
| --- | --- | --- |
| `AbortPolicy` | Ném lỗi từ chối khi submit. Spring thường biểu diễn thành `TaskRejectedException`. | Caller biết overload; tầng nghiệp vụ phải quyết định phản hồi/retry/fallback. |
| `CallerRunsPolicy` | Caller tự chạy task nếu pool chưa shutdown. | Tạo backpressure nhưng có thể chạy việc rất chậm trên HTTP request thread, làm suy yếu cách ly. |
| `DiscardPolicy` | Bỏ task. | Không dùng cho việc cần bảo đảm xử lý; dễ mất dữ liệu âm thầm. |
| `DiscardOldestPolicy` | Bỏ phần tử cũ nhất trong queue rồi thử nhận task mới. | Có thể mất tác vụ đã được nhận, gây đảo ưu tiên/thứ tự. |

Ví dụ setter khi cấu hình bean:

```java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(2);
executor.setMaxPoolSize(4);
executor.setQueueCapacity(8);
executor.setThreadNamePrefix("integration-");
executor.setRejectedExecutionHandler(
    new java.util.concurrent.ThreadPoolExecutor.AbortPolicy()
);
```

Đây là **mảnh cấu hình minh họa**, cần trả `executor` từ `@Bean` để Spring khởi tạo/quản lý vòng đời.

Khi đã quá tải, retry ngay lập tức và không giới hạn lên cùng pool có thể tạo **retry storm**. Cần giới hạn số lần, backoff, jitter, timeout, circuit breaker hoặc rate limit tùy nghiệp vụ. Chọn chính sách bỏ task phải được quyết định bởi yêu cầu dữ liệu; không vì “tăng throughput” mà làm mất thanh toán, audit hoặc công việc quan trọng.

## 9. Giới hạn của việc tách pool

**CPU chung.** Nhiều pool vẫn tranh chấp CPU của cùng máy/container. Nếu batch chiếm toàn bộ CPU, FAST có thể chậm dù giữ worker riêng.

**Database chung.** `fastExecutor` và `slowExecutor` có thể cùng dùng DataSource. Khi nhóm slow giữ hết connection, nhóm fast vẫn kẹt tại bước lấy connection. Giới hạn concurrent DB calls, transaction duration, query timeout; nếu cần cách ly nghiêm ngặt phải có chính sách ở tầng connection hoặc storage.

**HTTP client và dịch vụ ngoài.** Có thể bị nghẽn chung connection pool, DNS, socket hoặc quota rate limit. Hai nhóm riêng trong một JVM không đảm bảo tổng số call qua nhiều replica nằm dưới quota.

**Heap/GC.** Mỗi queued task giữ object và closure. Nhiều queue lớn làm tăng heap, GC pause, memory pressure. Pool riêng không ngăn JVM bị OOM.

**Lock và state.** Hai nhóm có thể cùng tranh một monitor, cache lock hoặc row database. Tách worker không thay thế đồng bộ hóa và transaction đúng.

**Shutdown/crash.** Queue trong bộ nhớ bị mất khi process chết. Task cần bền vững phải được lưu nhận trạng thái bên ngoài và có cơ chế khôi phục.

Do đó executor bulkhead chỉ là **một tầng** trong chiến lược chống nghẽn. Với downstream cần bảo vệ, cân nhắc semaphore bulkhead, connection pool riêng, rate limiter, timeout và circuit breaker phù hợp.

## 10. Lỗi, timeout, cancellation và context

Phân biệt:

1. **Gửi task bị từ chối:** executor/queue bão hòa hoặc đang shutdown; lời gọi tại điểm submit có thể ném `TaskRejectedException`. Không có bảo đảm đã nhận task.
2. **Task chạy rồi thất bại:** `Future.get()` có thể ném `ExecutionException`; `CompletableFuture.join()` thường báo `CompletionException`. Quan sát future/callback để không mất lỗi.
3. **Caller chờ quá lâu:** `Future.get(timeout, unit)` ném `TimeoutException` nhưng **không tự dừng task**; `CompletableFuture.orTimeout` cũng không bảo đảm worker bị interrupt.
4. **Hủy:** cancellation/interruption phụ thuộc hợp tác của tác vụ và thư viện I/O; không bảo đảm rollback hiệu ứng phụ ở dịch vụ ngoài.

Với `@Async` trả `void`, caller không có handle nhận lỗi. Cấu hình `AsyncUncaughtExceptionHandler` nếu thật sự cần, nhưng các công việc quan trọng nên có kênh kết quả/trạng thái.

Ví dụ quan sát `CompletableFuture` (giả định đã có `WorkService service` của ví dụ chính):

```java
service.fastCalculation("C").whenComplete((value, error) -> {
    if (error != null) {
        System.err.println("Failed: " + error.getMessage());
    } else {
        System.out.println(value.value());
    }
});
```

`whenComplete` quan sát lỗi và thành công; `exceptionally` hoặc `handle` có thể được dùng để quyết định fallback. Callback `thenApply` không tự bảo đảm chạy trên một thread khác; dùng biến thể `...Async(..., executor)` khi cần chọn executor cho bước tiếp theo, tránh vô tình chuyển việc sang common pool của JDK.

`@Async` tạo ranh giới thread: MDC logging, `SecurityContext`, request-scoped context, `ThreadLocal`, transaction của thread caller **không mặc nhiên** chuyển sang worker. Có thể dùng `TaskDecorator` hoặc cơ chế truyền context có kiểm soát, phải khôi phục/xóa trong `finally` vì worker được tái sử dụng. Transaction của caller không trở thành transaction của worker; nếu task nền cần giao dịch, thiết kế ranh giới riêng qua Spring bean/proxy phù hợp.

Trong Spring MVC, controller gọi `.get()`/`.join()` ngay sẽ chặn controller thread dù service dùng executor khác. Giải phóng Servlet request thread cần hợp đồng xử lý HTTP async phù hợp, độc lập với quyết định phân pool của `@Async`.

## 11. Lifecycle, shutdown và độ bền

Mỗi `ThreadPoolTaskExecutor` bean có vòng đời do Spring container quản lý. Khi đóng `ApplicationContext`, Spring thực hiện quy trình shutdown theo hợp đồng lifecycle. Với ứng dụng thực, cần định nghĩa:

- Có dừng nhận công việc mới trong giai đoạn shutdown không?
- Task đã nhận nhưng còn đang chạy hoặc ở queue được phép hoàn tất trong bao lâu?
- Nếu deadline shutdown hết, có thể interrupt/cancel hay phải ghi lại trạng thái?
- Nếu một executor được nhiều thành phần sử dụng, ai sở hữu quyền shutdown?
- Với việc có giá trị nghiệp vụ, đã có idempotency, retry và persistent job record chưa?

`ThreadPoolTaskExecutor` có các tùy chọn shutdown như `setWaitForTasksToCompleteOnShutdown` và `setAwaitTerminationSeconds` (xem API phiên bản đang dùng). Không đồng nhất “pool tách riêng” với “công việc đã submit chắc chắn chạy thành công”. Trong ví dụ chính, main chờ cả hai `Future` hoàn tất rồi mới đóng context, nên không phải xử lý recovery khi restart.

## 12. Theo dõi và đánh giá hiệu quả thực tế

Gắn nhãn theo executor, ví dụ `pool=fast`, `pool=slow`, rồi quan sát:

| Chỉ số | Ý nghĩa |
| --- | --- |
| `active threads`, `pool size`, `queue depth` | Có bị bão hòa worker/queue không, ở nhóm nào? |
| Queue waiting time và execution duration | Độ trễ đến từ chờ hay thời gian xử lý thật? |
| p50/p95/p99 end-to-end latency | Việc tách pool có bảo vệ SLA của FAST không? |
| Submitted/succeeded/failed/rejected/cancelled | Có lỗi hoặc công việc bị mất không? |
| CPU, heap, GC, tổng số thread | Phân pool có tăng overhead hoặc memory pressure không? |
| DB/HTTP connection usage, 429 và downstream timeout | Nghẽn còn nằm ở tài nguyên dùng chung nào? |

`ThreadPoolTaskExecutor` cung cấp một số snapshot như `getPoolSize()`, `getActiveCount()`, `getQueueSize()` (theo API phiên bản). Snapshot không đủ để thực hiện logic “queue còn chỗ, chắc submit sẽ thành công”: trạng thái có thể đổi trước lúc gửi.

Một vòng cải tiến có kiểm soát:

1. Đo baseline khi dùng pool chung; xác định nhóm nào tạo queue wait/tail latency.
2. Xác định nhóm cần cách ly và tổng ngân sách CPU/thread/connection/memory.
3. Khai báo executor riêng, giới hạn queue và rejection theo hậu quả nghiệp vụ.
4. Tạo tải nhóm slow vượt ngưỡng, quan sát FAST có còn hoàn tất trong mục tiêu không.
5. Thử đồng thời CPU/DB/downstream saturation để phát hiện nút cổ chai chia sẻ.
6. Theo dõi metrics sau triển khai và tinh chỉnh dựa trên tải thực.

Nếu dùng Spring Boot Actuator/Micrometer, có thể instrument executor theo integration phù hợp, nhưng cần kiểm tra thiết lập metrics trong chính ứng dụng; Spring Framework lõi không tự có dashboard hoàn chỉnh.

## 13. Sai lầm thường gặp

| Sai lầm | Hệ quả | Cách sửa |
| --- | --- | --- |
| Tất cả `@Async` dùng một pool mặc định | Task chậm chiếm worker/queue của task nhanh. | Route tường minh theo nhóm tài nguyên. |
| Tạo hai bean tên khác nhưng cùng delegate | Vẫn chung thread pool và hàng đợi. | Kiểm tra instance/metrics thật. |
| Đặt `maxPoolSize` lớn và queue cực lớn rồi chờ tự tăng thread | Queue thường đầy trước khi mở rộng vượt core. | Hiểu thứ tự worker–queue–max và đo tải. |
| Cho queue không giới hạn để tránh rejection | Tăng tail latency/memory, che overload. | Queue hữu hạn, admission control và lỗi có chủ đích. |
| Dùng `CallerRunsPolicy` cho nhóm cần latency thấp | Task chậm có thể chạy trong HTTP caller. | Chọn overload policy theo SLA. |
| Tách pool CPU nhưng tổng worker lớn hơn nhiều CPU quota | Tranh chấp CPU, context switching. | Tính ngân sách CPU toàn process. |
| Chỉ tách pool mà dùng chung connection pool đang bão hòa | FAST vẫn nghẽn phía DB/HTTP. | Giới hạn downstream, thêm quota/timeout. |
| Quên `@EnableAsync` hoặc dùng self-invocation | Annotation không đi qua interceptor. | Gọi qua Spring bean/proxy. |
| `@Async` xong controller lập tức `join()` | Caller vẫn bị chặn. | Chọn HTTP async/composition phù hợp. |
| Không xử lý rejection, task failure và timeout | Mất kết quả hoặc retry storm. | Quan sát Future, giới hạn retry, định nghĩa fallback. |
| Tin rằng queue RAM đảm bảo công việc qua restart | Task có thể bị mất khi crash. | Dùng job store/broker nếu cần durability. |
| Mang `ThreadLocal` sang worker rồi không dọn | Có thể rò rỉ context giữa request. | Propagation và cleanup trong `finally`. |

## 14. Tự kiểm tra

**Câu 1.** Hai tác vụ HTTP blocking đang giữ hai worker của pool chung. Một tác vụ tính toán 2 ms được submit. Nó có chắc nhận kết quả sau 2 ms không?

**Đáp án:** Không. Queue waiting time có thể lớn hơn rất nhiều thời gian xử lý. Tách nhóm và timeout I/O giúp bảo vệ worker của tác vụ nhanh.

**Câu 2.** Hai executor riêng nhưng đều dùng cùng một JDBC connection pool tối đa bốn connection. Bốn tác vụ slow giữ hết connection. FAST có chắc được cách ly không?

**Đáp án:** Không. Chỉ thread pool được tách; JDBC connection là tài nguyên chia sẻ. Cần quản lý concurrency/timeout ở tầng dữ liệu.

**Câu 3.** `corePoolSize=2`, `maxPoolSize=20`, `queueCapacity=10000`. Khi nhận 100 task trong thời gian ngắn, pool có chắc tạo 20 worker không?

**Đáp án:** Không. Với queue dương, task thường được xếp hàng sau khi đủ core trước khi tăng worker vượt core.

**Câu 4.** `CallerRunsPolicy` có giữ chắc giả định phương thức `@Async` chạy trên worker riêng khi pool quá tải không?

**Đáp án:** Không. Caller có thể phải tự chạy task bị từ chối, gây tăng latency. Đây là dạng backpressure với đánh đổi cần cân nhắc.

**Câu 5.** Vì sao trong ví dụ dùng `CountDownLatch` thay cho `Thread.sleep()`?

**Đáp án:** Để xác nhận slow worker thực sự đã vào method và đang bị giữ lại trước khi submit FAST, tránh kết luận dựa trên lịch chạy may rủi.

**Câu 6.** Có năm executor CPU-bound, mỗi pool tám worker, nhưng container chỉ được hai CPU. Việc phân pool có tạo 40 CPU xử lý song song thật không?

**Đáp án:** Không. Các thread vẫn tranh chấp hai CPU; tổng concurrency cần được điều chỉnh theo quota và số liệu thực tế.

**Câu 7.** Một báo cáo nền phải tiếp tục sau khi ứng dụng restart. Tách `reportExecutor` riêng đã đủ chưa?

**Đáp án:** Chưa. Thread pool/queue RAM không bền vững; cần lưu trữ job và kế hoạch retry/khôi phục/idempotency.

**Câu 8.** Nhận `CompletableFuture` từ `@Async` có chứng tỏ công việc hoàn tất hoặc caller không bao giờ chờ không?

**Đáp án:** Không. Future là handle của kết quả; `get()`/`join()` vẫn có thể chặn, còn task có thể đang chạy hoặc thất bại.

### Điều cần nhớ

Để tách bộ thực thi đúng cách, hãy xác định **đặc tính tác vụ, yêu cầu latency, giới hạn downstream, tổng ngân sách tài nguyên và cách xử lý overload**. Cấp mỗi nhóm thật sự cần cách ly một executor/queue có kiểm soát, theo dõi số liệu riêng và kiểm nghiệm dưới tải. Cách ly worker giúp giảm ảnh hưởng chéo, nhưng an toàn hệ thống còn phụ thuộc CPU, DB, heap, lock, timeout và độ bền công việc.

### Tài liệu tham khảo

- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): `TaskExecutor`, `ThreadPoolTaskExecutor`, `@Async`, qualifier và rejection.
- [Spring Framework 6.2 — ThreadPoolTaskExecutor API](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): core/max/queue, monitoring, lifecycle.
- [Java 17 — ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html): worker/queue, saturation và chính sách từ chối.
- [Java 17 — CountDownLatch API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): phối hợp thời điểm thực thi và happens-before.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-002">Chọn số luồng cho tác vụ nặng CPU và tác vụ thường chờ nhập xuất</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn số luồng cho tác vụ nặng CPU và tác vụ thường chờ nhập xuất

**Module:** Spring Framework - concurrency  
**Nhóm kiến thức:** Cấu hình tài nguyên  
**Mục tiêu:** Hiểu vì sao tác vụ chủ yếu dùng CPU và tác vụ thường xuyên chờ I/O cần hai chiến lược cấp phát luồng khác nhau; biết đặt `corePoolSize`, `maxPoolSize`, `queueCapacity`, giới hạn tài nguyên, đo đạc và điều chỉnh một cách có căn cứ trong Spring.

## 1. Tại sao số luồng không thể chọn theo một công thức cố định?

Một luồng Java có thể ở trạng thái **đang tiêu thụ CPU** hoặc **đang chờ** mạng, cơ sở dữ liệu, ổ đĩa, khóa, semaphore, timer và các tài nguyên khác. Với số lượng CPU hữu hạn, thêm luồng có thể tăng throughput khi nhiều luồng vốn đang chờ, nhưng có thể làm một hệ thống CPU đã bão hòa chậm hơn vì chuyển ngữ cảnh, tranh chấp cache và lập lịch.

Ví dụ có bốn lõi CPU khả dụng:

- **Bốn tác vụ tính toán liên tục:** khoảng bốn worker chạy thực sự song song nếu mỗi tác vụ chỉ dùng một lõi và máy không bị công việc khác chiếm nhiều CPU. Tăng lên 100 worker không tạo ra 100 lõi.
- **Bốn tác vụ chờ HTTP/DB trong phần lớn vòng đời:** mỗi worker chiếm thread nhưng không dùng CPU liên tục. Nhiều worker hơn có thể tận dụng CPU trong lúc các worker khác chờ, **cho đến khi** đạt giới hạn connection, remote service, bộ nhớ, băng thông hoặc timeout.
- **Bốn tác vụ gọi API theo kiểu non-blocking:** số request đang chờ mạng có thể lớn hơn số thread; không áp dụng nguyên xi tư duy “mỗi request chờ = một thread bị giữ”.

Do đó cần phân loại khối công việc, đo thời gian **tính toán (compute)** và **chờ (wait)**, biết giới hạn tài nguyên, rồi đặt một pool có hàng đợi và cơ chế từ chối rõ ràng.

### Hai mục tiêu thường xung đột

1. **Throughput:** số công việc hoàn thành mỗi giây.
2. **Latency:** thời gian từ lúc nhận việc đến khi trả kết quả; cần tách thành **queue wait** và **execution time**.

Thêm thread đôi khi cải thiện throughput nhưng khiến tail latency xấu đi khi downstream bị dồn tải. Một pool có queue lớn có thể ít từ chối tác vụ trước mắt nhưng làm hàng đợi kéo dài nhiều giây hoặc phút. **Số luồng không thể tối ưu riêng lẻ mà bỏ qua queue và downstream.**

## 2. Nhận diện CPU-bound, blocking I/O-bound và non-blocking I/O

| Loại tác vụ | Phần lớn thời gian | Dấu hiệu đo đạc | Hướng chọn concurrency |
| --- | --- | --- | --- |
| **CPU-bound** | Chạy phép toán, nén, mã hóa, parse nặng, xử lý ảnh, biến đổi dữ liệu trong RAM | CPU gần bão hòa; thêm worker tăng context switch nhưng throughput không tăng | Số worker hoạt động xấp xỉ số lõi CPU khả dụng; pool thường cố định, queue hữu hạn |
| **Blocking I/O-bound** | Thread bị giữ trong `InputStream.read`, JDBC, REST client đồng bộ, socket call, chờ lock | CPU còn dư trong khi nhiều thread `WAITING`/`TIMED_WAITING`/blocked ở stack trace | Có thể dùng nhiều worker hơn lõi CPU, nhưng giới hạn bởi connection pool và nguồn bên ngoài |
| **Non-blocking I/O** | Đăng ký hoạt động I/O và nhận callback/signal khi có dữ liệu; không giữ một worker để đợi từng request | Ít event-loop thread xử lý rất nhiều kết nối, CPU không bị chặn ở I/O | Không tăng event-loop thread theo số request; tránh chạy CPU hoặc blocking code trên event loop |
| **Mixed workload** | Có cả tính toán nặng và gọi I/O chặn | CPU lúc cao, lúc thấp; pool chung có hàng chờ không ổn định | Tách CPU pool và blocking I/O pool hoặc giới hạn riêng từng loại; đo bằng tải thực |

Một công việc có thể *cả hai*: ví dụ nhận HTTP, parse JSON, tính chữ ký, gửi JDBC query, biến đổi ảnh. Phân loại dựa vào nơi **thời gian thực sự trôi qua**, không chỉ tên method hay framework.

Phân biệt rất quan trọng:

- Gọi `Thread.sleep(...)` là **chặn thread**, nhưng không phải I/O thực. Dùng sleep chỉ để mô phỏng thời gian chờ trong ví dụ kiểm thử.
- Gọi `CompletableFuture.supplyAsync` không tự biến một JDBC call đồng bộ thành non-blocking I/O; nó chỉ chuyển công việc chặn sang một worker khác.
- WebFlux, Reactor/Netty giúp tổ chức I/O bất đồng bộ khi driver/client thực sự non-blocking. Đặt phép tính CPU dài hoặc JDBC chặn trên event loop vẫn gây nghẽn.
- Java 21+ có virtual threads: nhiều thao tác blocking có thể dùng virtual thread để giảm chi phí giữ *platform thread*. Điều này **không** làm CPU vô hạn và không tự tăng giới hạn DB connections, rate limit hay khả năng của dịch vụ ngoài.

## 3. Tính đúng số CPU thực sự khả dụng

Dùng một điểm xuất phát:

```java
int processors = Runtime.getRuntime().availableProcessors();
System.out.println("reportedProcessors=" + processors);
```

Đoạn Java trên là **minh họa** về cách lấy số bộ xử lý được JVM báo cáo, không phải toàn bộ chương trình độc lập. Giá trị có thể phản ánh giới hạn affinity/container tùy phiên bản JVM và môi trường, nhưng vẫn phải kiểm tra thực tế **CPU quota, cgroup, số lõi bị tranh chấp, SMT/hyper-threading và giới hạn hạ tầng**.

Đặt:

- `P` = lượng năng lực CPU khả dụng cho **quy trình này** (đơn vị lõi CPU tương đương, có thể nhỏ hơn số lõi máy vật lý).
- `U` = mức sử dụng CPU mục tiêu cho pool, ví dụ `0.75` đến `0.9` để còn dư cho HTTP, GC và tác vụ khác.
- `N_cpu` = số **worker đồng thời có thể chạy CPU**.

Điểm khởi đầu thường dùng cho công việc CPU-bound thuần:

\[
N_{\mathrm{cpu}} \approx P
\]

Nếu JVM còn chạy HTTP, GC, scheduler, hoặc nhiều pool CPU, cần chia tổng ngân sách CPU giữa chúng. Với CPU rất ít hoặc quota lẻ, số worker phải là số nguyên ít nhất một; không diễn giải công thức như một bảo đảm throughput tối ưu.

### Ví dụ

Giả sử container có ngân sách CPU tương đương **4 lõi**, tác vụ làm phép tính dài và không đợi I/O. Bắt đầu thử **3–4 worker CPU** cho riêng pool này, dùng queue hữu hạn, đo throughput và p95/p99. Tăng lên 8 chỉ có ích nếu tác vụ thực sự có phần chờ đáng kể hoặc CPU còn dư; nếu CPU luôn 100%, có thể làm trễ xử lý vì tranh CPU.

**Đừng tạo một pool CPU 4 worker cho từng endpoint** rồi cộng thành hàng chục pool chạy tối đa đồng thời. Tổng concurrency của tất cả pool và request thread mới là yếu tố gây quá tải CPU.

### Dấu hiệu CPU-bound trên hệ thống thực

- CPU process/container cao liên tục; runnable thread nhiều hơn CPU khả dụng.
- Profiling cho thấy thời gian tích lũy ở hàm tính toán, mã hóa, giải nén, JSON conversion, vòng lặp.
- Khi tăng từ 4 lên 8 worker: throughput không tăng, CPU giữ nguyên, context switching và p99 tăng.
- Nhiều worker cùng cập nhật một cấu trúc dữ liệu hoặc lock có thể **không** mở rộng theo số lõi: phải sửa contention, chia nhỏ dữ liệu hoặc giảm critical section.

## 4. Sizing cho blocking I/O: thời gian chờ là yếu tố chủ chốt

Với tác vụ blocking, đặt:

- `C` = thời gian CPU thực sự dùng cho một công việc, chẳng hạn **20 ms**.
- `W` = thời gian thread chờ I/O, chẳng hạn **80 ms**.
- `P` = số lõi CPU khả dụng.
- `U` = CPU utilization mục tiêu trong phạm vi pool.

Một heuristic để **ước lượng thử nghiệm ban đầu**, với các việc độc lập và điều kiện tương đối ổn định, là:

\[
N_{\mathrm{blocking}} \approx P \times U \times \left(1 + \frac{W}{C}\right)
\]

Ví dụ `P = 4`, `U = 0.8`, `W = 80 ms`, `C = 20 ms`:

\[
N \approx 4 \times 0.8 \times (1+4)=16
\]

Giải thích: một worker dành khoảng 20% thời gian tính toán và 80% để đợi; cần nhiều tác vụ đang hoạt động để giữ CPU hữu ích trong lúc các tác vụ khác chờ.

Đây là **ước lượng, không phải đáp số bắt buộc**. Công thức không thể thay cho load test, và thường trở nên sai khi có bottleneck downstream, thay đổi tỷ lệ W/C, contention, batching, cache, CPU burst, timeout hoặc tác vụ không độc lập.

### Giới hạn I/O thật quan trọng hơn phép tính 16

Giả sử 16 worker đều gọi cùng một database, nhưng pool JDBC chỉ có **8 connection**. Chỉ tối đa 8 query có thể dùng connection cùng lúc; các worker còn lại có thể chờ lấy connection ngay trong JVM. Tăng tiếp lên 64 worker có thể làm tăng thời gian chờ, request timeout và bộ nhớ mà không tăng query throughput.

Giới hạn cần xét:

- JDBC connection pool, giới hạn connection đồng thời của database và transaction duration.
- HTTP connection pool, số request đồng thời được đối tác cho phép, rate limit và latency mạng.
- File descriptors, sockets, memory/thread stack, heap pressure, GC.
- Độ trễ hàng đợi downstream và retry amplification: khi remote service chậm, số task đồng thời có thể bùng lên.
- SLA: mỗi request có **deadline**, timeout và chính sách retry; queue không được giữ các task đã quá hạn.

Đặt một **concurrency limit riêng** bằng semaphore/bulkhead ở sát tài nguyên nếu cần. `N_threads = 16` không nhất thiết đồng nghĩa cho phép 16 truy vấn DB đồng thời.

### Little's Law để kiểm tra tải

Ở trạng thái ổn định:

\[
L = \lambda \times T
\]

Trong đó `L` là số công việc trung bình đang ở trong hệ thống, `λ` là throughput (việc/giây), `T` là tổng thời gian một công việc lưu lại trong hệ thống (giây), **bao gồm thời gian xếp hàng** nếu đo cả hệ thống. Ví dụ throughput 100 việc/giây với thời gian trung bình 0,2 giây thì có khoảng 20 công việc in-flight trung bình.

Không suy ra “luôn cần 20 thread” từ con số đó: công việc có thể dùng non-blocking I/O, nhiều job có thể đang chờ trong queue và các pool phía sau có thể dùng tài nguyên khác. Little's Law giúp kiểm tra quan hệ tải và độ trễ, không thay thế sizing cho từng pool.

## 5. Cấu trúc ThreadPool trong Spring: `core`, `max` và queue liên hệ thế nào?

`ThreadPoolTaskExecutor` là lựa chọn tiện dụng của Spring để quản lý pool theo bean. Với một cấu hình thông thường:

```java
executor.setCorePoolSize(4);
executor.setMaxPoolSize(12);
executor.setQueueCapacity(40);
```

Các giá trị trên chỉ là **ví dụ để hiểu cơ chế**, không tự động phù hợp mọi dịch vụ.

Trong `ThreadPoolExecutor` được Spring bọc, khi một task mới đến:

1. Nếu số worker đang tồn tại **nhỏ hơn `corePoolSize`**, executor ưu tiên tạo worker mới để xử lý.
2. Khi đạt core, executor **ưu tiên đưa task vào queue** nếu còn chỗ.
3. **Chỉ khi queue không nhận thêm**, executor mới tạo thêm worker vượt core, tối đa `maxPoolSize`.
4. Nếu worker đã đạt max và queue không nhận thêm, task bị từ chối theo `RejectedExecutionHandler`.

```text
Task đến
  |
  +-- Worker count < core? ---- Có ----> Tạo worker, chạy task
  |
  +-- Queue còn chỗ? --------- Có ----> Xếp hàng chờ
  |
  +-- Worker count < max? ----- Có ----> Tạo worker vượt core
  |
  +-- Từ chối / thực thi ở caller tùy rejection policy
```

Hệ quả dễ gây bất ngờ: **`maxPoolSize=100` không có nghĩa pool sẽ tăng đến 100 khi CPU bận**. Nếu queue đủ rộng hoặc không giới hạn, sau khi đạt core, công việc có thể cứ xếp hàng mà không kích hoạt worker mới.

Cần hiểu các thuộc tính:

| Thuộc tính | Vai trò | Lưu ý |
| --- | --- | --- |
| `corePoolSize` | Số worker mục tiêu duy trì ở tải thường | Không phải tổng số task đang chạy và chờ |
| `maxPoolSize` | Giới hạn worker pool trong các đợt tăng | Chỉ phát huy khi queue không nhận thêm task |
| `queueCapacity` | Số task **đang chờ**, không tính task đang chạy | Cần hạn mức hữu hạn theo bộ nhớ/độ trễ cho production |
| `keepAliveSeconds` | Thời gian idle trước khi worker vượt core được thu hồi | Không giải quyết queue quá lớn |
| `threadNamePrefix` | Giúp nhận diện thread trong log/thread dump | Tách tên pool CPU/IO để quan sát |
| `RejectedExecutionHandler` | Chính sách khi worker/queue không còn sức chứa | `AbortPolicy` báo lỗi; `CallerRunsPolicy` có thể chạy task trên thread gửi |
| `TaskDecorator` | Bọc callback chạy task (context/metrics) | Cần cleanup `ThreadLocal`; không tự chuyển transaction |

**Mặc định của lớp Spring `ThreadPoolTaskExecutor`** có core 1, max rất lớn và queue capacity rất lớn. Vì queue mặc định không giới hạn theo nhu cầu thực, không nên chỉ cấu hình `maxPoolSize` mà kỳ vọng tự mở rộng nhanh; chủ động quyết định core, max và queue.

Một `ThreadPoolTaskExecutor` với queue capacity **0** dùng cơ chế chuyển giao trực tiếp tương tự `SynchronousQueue`, thường có thể mở rộng nhanh khi task đến; nếu max quá lớn, lại có nguy cơ tăng thread đột biến. Dùng khi đã hiểu hành vi từ chối và áp lực tải.

## 6. Tách pool CPU và blocking I/O trong ứng dụng Spring

Nếu dùng chung một executor cho cả nén file nặng CPU và JDBC/HTTP blocking, các task blocking có thể chiếm hết worker khiến công việc CPU không được chạy dù CPU đang rảnh. Ngược lại, CPU-bound task có thể chiếm pool và trì hoãn I/O. Có thể dùng **hai bean executor**, mỗi bean có queue và giới hạn riêng.

Ví dụ **Spring Framework 6.x, Java 17+**, dành cho ứng dụng Spring Boot dùng `spring-boot-starter-web` hoặc cấu hình Spring có `spring-context`.

Lưu file `src/main/java/com/example/sizing/ExecutorConfiguration.java`:

```java
package com.example.sizing;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class ExecutorConfiguration {

    @Bean("cpuExecutor")
    public ThreadPoolTaskExecutor cpuExecutor() {
        int available = Runtime.getRuntime().availableProcessors();

        // Trần 8 là giới hạn minh họa của ứng dụng, không phải quy tắc chung.
        int cpuWorkers = Math.max(1, Math.min(8, available));
        return createExecutor("cpu-", cpuWorkers, cpuWorkers, 32);
    }

    @Bean("blockingIoExecutor")
    public ThreadPoolTaskExecutor blockingIoExecutor() {
        // 8/16/64 chỉ là cấu hình khởi đầu để đo; kiểm tra giới hạn DB/API.
        return createExecutor("blocking-io-", 8, 16, 64);
    }

    private static ThreadPoolTaskExecutor createExecutor(
            String prefix, int core, int max, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
```

Lưu file `src/main/java/com/example/sizing/WorkService.java`:

```java
package com.example.sizing;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class WorkService {

    @Async("cpuExecutor")
    public CompletableFuture<Long> calculate(int upperBound) {
        if (upperBound < 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("upperBound must be >= 0"));
        }
        long sum = 0;
        for (int i = 1; i <= upperBound; i++) {
            sum += i;
        }
        return CompletableFuture.completedFuture(sum);
    }

    @Async("blockingIoExecutor")
    public CompletableFuture<String> fetchLegacy(String key) {
        try {
            // Chỉ mô phỏng 50 ms blocking I/O; không phải lời gọi mạng thật.
            TimeUnit.MILLISECONDS.sleep(50);
            return CompletableFuture.completedFuture("data-" + key);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(ex);
        }
    }
}
```

Hai file trên là **các class hoàn chỉnh trong một ứng dụng Spring**; muốn chạy dưới Spring Boot cần có class `@SpringBootApplication` và dependency tương ứng. Không thể dùng `javac` thuần khi chưa có Spring trên classpath. Ví dụ `fetchLegacy` chỉ mô phỏng thời gian bị chặn; trong ứng dụng thật, thay bằng JDBC/HTTP client đồng bộ có timeout và đo phần chờ thực.

Giải thích những quyết định:

- `cpuExecutor`: fixed-size pool theo một ngân sách CPU giới hạn, tránh tạo thread vượt số lõi có ích.
- `blockingIoExecutor`: cho phép concurrency cao hơn vì mỗi worker có phần chờ; nhưng `8`, `16`, `64` **không phải giá trị production mặc định**. Phải đo `W/C` và giới hạn downstream.
- Queue hữu hạn giúp biết điểm bão hòa; với `AbortPolicy`, Spring báo `TaskRejectedException` khi không nhận việc. Bên gửi task phải xử lý tình trạng bận theo hợp đồng API.
- `@Async("cpuExecutor")` và `@Async("blockingIoExecutor")` buộc Spring chọn bean có tên rõ ràng; tránh dùng mặc định một pool cho mọi loại việc.
- `@EnableAsync` trong cấu hình proxy mặc định chỉ intercept lời gọi qua **Spring proxy**. Gọi `this.calculate()` trong cùng bean bỏ qua proxy và có thể thực thi ngay trên thread gọi.
- `@Async` làm việc ở thread khác: transaction, request context và các `ThreadLocal` tùy biến không tự động đi theo. Worker nên có ranh giới transaction riêng và truyền dữ liệu cần thiết một cách tường minh.
- Bean `ThreadPoolTaskExecutor` được Spring quản lý lifecycle khi đăng ký đúng cách; tránh tự tạo pool mới trong mỗi request hoặc gọi `shutdown` trong controller.

Nếu ứng dụng cần nhiều CPU pool, tính **tổng** số worker CPU đồng thời qua mọi pool, không xem `availableProcessors()` là hạn mức riêng cho từng bean.

## 7. Java 17 chạy độc lập: cùng số job, hai kiểu công việc và overload

Để hiểu cơ chế mà không cần khởi động Spring, chương trình JDK sau chạy:

1. **12 tác vụ CPU:** mỗi tác vụ cộng các số từ `1` đến `100_000`, dùng pool 2 worker.
2. **12 tác vụ mô phỏng blocking I/O:** mỗi tác vụ chờ `20 ms`, dùng pool 4 worker.
3. **Một pool có 1 worker + 1 chỗ queue:** giữ worker bằng `CountDownLatch`, xếp thêm 1 job, rồi kiểm tra job thứ ba bị từ chối theo `AbortPolicy`.

Cả hai kích thước pool chỉ nhằm minh họa. Không lấy thời gian chạy của ví dụ nhỏ làm kết luận benchmark. Điều được kiểm chứng là **kết quả không mất việc**, hành vi chờ khác nhau và queue hữu hạn có thể từ chối tác vụ.

Lưu toàn bộ vào **`WorkloadSizingDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WorkloadSizingDemo {

    static long cpuWork() {
        long total = 0;
        for (int i = 1; i <= 100_000; i++) {
            total += i;
        }
        return total;
    }

    static long runJobs(int workers, int jobs, Callable<Long> task)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < jobs; i++) {
                futures.add(pool.submit(task));
            }

            long total = 0;
            for (Future<Long> future : futures) {
                total += future.get();
            }
            return total;
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    static boolean rejectWhenFull() throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());

        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<?> first = pool.submit(() -> {
                running.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });

            if (!running.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker did not start");
            }

            // 1 worker đang bận; job này chiếm đúng 1 vị trí queue.
            Future<?> second = pool.submit(() -> { });

            boolean rejected = false;
            try {
                pool.execute(() -> { });
            } catch (RejectedExecutionException ex) {
                rejected = true;
            }

            release.countDown();
            first.get();
            second.get();
            return rejected;
        } finally {
            release.countDown();
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    public static void main(String[] args) throws Exception {
        long cpuTotal = runJobs(2, 12, WorkloadSizingDemo::cpuWork);

        long ioTotal = runJobs(4, 12, () -> {
            TimeUnit.MILLISECONDS.sleep(20); // Mô phỏng chờ I/O.
            return 1L;
        });

        System.out.println("cpuTotal=" + cpuTotal);
        System.out.println("ioTotal=" + ioTotal);
        System.out.println("rejected=" + rejectWhenFull());
    }
}
```

Chạy:

```bash
javac WorkloadSizingDemo.java
java WorkloadSizingDemo
```

**Stdout khi chương trình hoàn tất bình thường:**

```text
cpuTotal=60000600000
ioTotal=12
rejected=true
```

Tại sao kết quả ổn định?

- Một job CPU cộng `1 + 2 + ... + 100000 = 5.000.050.000`; nhân 12 job được `60.000.600.000`. Dùng `long` để không tràn số 32 bit.
- Mỗi job I/O mô phỏng trả `1`, tổng 12 là `12`; chỉ thứ tự/thời gian task chạy là không xác định.
- Trong `rejectWhenFull`, latch xác nhận worker duy nhất đã thực sự bắt đầu và đang chờ; sau đó job thứ hai đi vào queue có sức chứa một, nên job thứ ba không còn worker/queue và bị từ chối. `finally` luôn mở latch và dừng pool để không treo chương trình.

**Lưu ý quan trọng:** `Executors.newFixedThreadPool` dùng queue không giới hạn theo lượng bộ nhớ nên chỉ phù hợp để tạo ví dụ đơn giản ở đây. Trong dịch vụ chịu tải, cấu hình `ThreadPoolExecutor`/Spring `ThreadPoolTaskExecutor` có **bounded queue** và rejection policy phù hợp. `Future.get()` trong `runJobs` có mục đích đợi kết quả kiểm thử; controller HTTP production cần cân nhắc khi nào được phép chặn.

## 8. Làm thế nào để chọn kích thước pool có thể kiểm chứng?

Thay vì nhìn CPU count rồi điền một số cố định, hãy đo ở **mức tải mục tiêu**, gồm tải thường và peak.

### Bước 1 — Xác định một đơn vị công việc

Ví dụ `renderPdf`, `recalculatePrice`, `callPartnerApi`, `sendEmail`. Đo riêng từng loại để không trộn CPU và blocking I/O. Nếu một task chứa nhiều giai đoạn, có thể đo CPU time và wall time cho từng giai đoạn.

Thông số gợi ý:

- Arrival rate `λ` (task/s), completion rate (task/s).
- CPU time trung bình/task và wall-clock duration/task.
- Phân phối waiting time, đặc biệt p95/p99 của JDBC/HTTP.
- Số task đang chạy, số đang queue, queue wait p50/p95/p99.
- Số active threads, pool size, số lần reject.
- CPU utilization theo **container quota**, heap/GC và memory.
- JDBC pool: active, idle, pending acquisition, acquisition timeout; HTTP pool và remote error/timeout.

### Bước 2 — Chọn điểm xuất phát

**Tác vụ CPU:** bắt đầu `N ≈ P` cho tổng ngân sách công việc CPU tương ứng; nếu JVM còn nhiều trách nhiệm, giảm hoặc chia ngân sách. Với 4 lõi, thử 3–4 worker.

**Blocking I/O:** đo `W/C`, dùng `P×U×(1+W/C)` để có điểm xuất phát, rồi **giới hạn bằng tài nguyên I/O**. Ví dụ heuristic cho 16 worker nhưng JDBC pool chỉ có 8 connections: cần kiểm tra DB, không chọn 16 chỉ vì công thức trả về 16.

**Mixed:** chia pool theo tài nguyên/giai đoạn nếu lợi ích cô lập tải rõ ràng. Ví dụ một pool CPU cho chuyển đổi ảnh và một pool I/O cho upload. Nếu chi phí tách và chuyển task không đáng, có thể giữ pool chung nhỏ nhưng phải đo hiện tượng starvation.

### Bước 3 — Chọn queue bằng **ngân sách chờ**

Giả sử pool CPU xử lý được trung bình 40 job/s trong peak ngắn, client không muốn một job đợi quá 0,5 giây *chỉ trong queue*. Một queue 20 job tương ứng khoảng 0,5 giây đợi theo tốc độ xử lý giả định trong điều kiện bão hòa ổn định (`20/40 = 0,5 s`). Đây chỉ là **ước lượng giản lược**: với arrival burst, thời gian phục vụ biến động, task ưu tiên hay nhiều loại job, queue latency thực có thể khác rất nhiều.

Queue rất lớn không giải quyết thiếu throughput nếu arrival rate dài hạn vượt completion rate. Khi đó thời gian chờ tăng không giới hạn cho đến khi tràn tài nguyên. Chọn queue hữu hạn theo deadline/SLI, sau đó xác định hệ thống phản ứng ra sao khi đầy.

### Bước 4 — Load test theo ma trận, giữ tiêu chí chấp nhận

Ví dụ cho CPU pool có ngân sách 4 lõi: thử worker `2, 3, 4, 6` với cùng dữ liệu và arrival pattern. Với I/O: thử `4, 8, 12, 16`, đồng thời đo connection pool và remote throughput. Mỗi tổ hợp đo:

- Throughput thành công và tỷ lệ lỗi/rejection.
- p50/p95/p99 từ lúc nhận việc tới xong, tách riêng queue wait.
- CPU throttling, time in GC, memory, lock contention.
- Downstream timeouts, JDBC pending/connection wait.
- Thời gian phục hồi sau burst.

Chọn **cấu hình nhỏ nhất đáp ứng SLO** ở peak dự kiến, còn khoảng dư cho lỗi/thay đổi tải. Một con số tốt trong máy dev có thể tệ trên container bị giới hạn 1 CPU hoặc ở production có 20 replica.

### Bước 5 — Theo dõi để điều chỉnh

`ThreadPoolTaskExecutor` hỗ trợ introspection như:

```java
int active = cpuExecutor.getActiveCount();
int currentPoolSize = cpuExecutor.getPoolSize();
int queueDepth =
        cpuExecutor.getThreadPoolExecutor().getQueue().size();
```

Đây là **đoạn mã minh họa** trong bean có tham chiếu `cpuExecutor`; phải gọi sau khi executor được khởi tạo. Kết hợp metrics từ Spring Boot Actuator/Micrometer hoặc bộ đo đang dùng để quan sát hàng đợi, số task bị từ chối, hiệu năng và tài nguyên. Đừng chỉ nhìn active thread mà kết luận mọi worker đang dùng CPU: chúng có thể đang chờ I/O hoặc lock.

## 9. Backpressure, timeout và công việc không thể nhận thêm

Khi pool/queue chạm hạn mức, cần **một hợp đồng phản hồi**. Tùy loại hệ thống:

1. API đồng bộ có thể trả `429 Too Many Requests` hoặc `503 Service Unavailable` theo chính sách tải, kèm gợi ý retry khi phù hợp.
2. Job API trả `202 Accepted` chỉ sau khi đã **nhận công việc** theo mức bảo đảm của nó. Nếu executor trong RAM từ chối, không được báo đã chấp nhận thành công.
3. Job quan trọng cần lưu bền vững: DB/job queue, message broker, transactional outbox, retry có giới hạn và idempotency. Thread pool Java/Spring **không** thay cho hệ thống hàng đợi bền vững.
4. Có thể giới hạn số lời gọi downstream đồng thời bằng semaphore/bulkhead; nếu không lấy permit trong deadline, từ chối sớm thay vì giữ một worker chờ vô hạn.
5. Đặt timeout cho HTTP/JDBC/DB lock phù hợp với deadline tổng; retry phải có backoff, jitter và giới hạn thử lại để tránh nhân tải khi bên ngoài đang lỗi.

`CallerRunsPolicy` của JDK là một dạng backpressure: khi không nhận được task và executor chưa shutdown, thread **gửi task** có thể tự chạy task. Nếu đó là Servlet request thread, nó có thể bị giữ trong tác vụ nặng; không nên coi chính sách này là “luôn trả lỗi khi đầy”. Với Spring `ThreadPoolTaskExecutor`, nếu muốn báo quá tải rõ ràng có thể chọn `AbortPolicy`, bắt `TaskRejectedException` ở ranh giới giao việc và chuyển thành response phù hợp.

### Không mở rộng pool để che giấu lỗi nội bộ

- Pool I/O tràn vì thiếu timeout HTTP: sửa timeout/connection management trước.
- Pool CPU quá bận vì mỗi job giữ khóa toàn cục: profile và giảm contention trước.
- JDBC connection pool cạn vì transaction mở trong khi gọi remote API: thu hẹp transaction; thêm worker chỉ khiến chờ dài hơn.
- Queue tăng nhanh vì upstream gửi nhanh hơn công suất lâu dài: cần kiểm soát tốc độ/scale thực sự, không chỉ tăng `queueCapacity`.

## 10. Các tình huống dễ chọn sai

| Hiện tượng | Giả định dễ sai | Cách xử lý đúng |
| --- | --- | --- |
| CPU 100%, tăng từ 4 lên 40 worker | “Nhiều luồng luôn xử lý nhanh hơn” | Giới hạn gần CPU hữu ích, đo context switch và throughput |
| CPU thấp, nhiều thread chờ DB | “Cứ tăng thread lên vài trăm” | Kiểm tra connection pool, query latency, locking và pending acquisition |
| `maxPoolSize=32`, core=4, queue rất lớn | “Pool sẽ dùng ngay 32 thread khi bận” | Hiểu queue-before-grow; đặt queue hữu hạn và core phù hợp |
| `queueCapacity` cực lớn | “Không còn lỗi overload” | Queue wait kéo dài, tràn bộ nhớ; chọn giới hạn theo deadline |
| `@Async` gọi từ cùng bean qua `this.method()` | “Tự động chạy trên executor” | Dùng lời gọi qua Spring proxy, kiểm tra cấu hình `@EnableAsync` |
| Chạy JDBC blocking trong Netty event loop | “WebFlux đã non-blocking cho mọi dependency” | Driver non-blocking hoặc chuyển blocking work sang scheduler phù hợp có giới hạn |
| Dùng virtual thread cho hàng nghìn query cùng lúc | “Virtual thread giải quyết luôn giới hạn DB” | Vẫn giới hạn connections/rate/deadline; virtual thread chủ yếu giảm chi phí chờ thread |
| Một pool chung cho tác vụ CPU và chờ mạng | “Pool đủ lớn sẽ công bằng” | Tách hoặc hạn mức theo loại việc; theo dõi starvation |
| Pool từ request tạo mới liên tục | “Mỗi request có pool riêng sẽ song song hơn” | Dùng bean/pool sống cùng ứng dụng, quản lý lifecycle |
| Trả `202` dù executor từ chối task | “Gửi API thành công nghĩa là đã ghi nhận job” | Chỉ báo accepted khi nhận việc chắc chắn, có lỗi overload rõ ràng |
| Dựa vào `availableProcessors()` như số lõi máy | “Giá trị này là năng lực CPU ổn định mọi môi trường” | Kiểm tra container quota/CPU throttling và đo tải thật |
| Chọn số thread qua `sleep` benchmark đơn giản | “IO lúc nào cũng tỉ lệ 80% chờ” | Đo W/C và phân phối latency của I/O production |

## 11. Tự kiểm tra

**Câu 1.** Một server có ngân sách CPU 4 lõi, xử lý tác vụ thuần tính toán không block. Chọn 40 thread có giúp đạt throughput cao hơn 4 thread gấp 10 lần không?  
**Trả lời:** Không. Số lõi giới hạn công việc CPU song song; thêm nhiều thread thường tăng lập lịch/tranh chấp, cần đo nếu có các yếu tố khác.

**Câu 2.** Công thức heuristic `N ≈ P × U × (1 + W/C)` với `P=4`, `U=0,8`, `W/C=4` cho bao nhiêu?  
**Trả lời:** 16 worker để **thử nghiệm ban đầu**. Phải kiểm tra số connection, remote quota và thời gian chờ queue; không coi 16 là cấu hình tối ưu chắc chắn.

**Câu 3.** Spring `ThreadPoolTaskExecutor(core=4,max=16,queue=100)` có tăng ngay lên 16 thread khi 5 job CPU đang chạy không?  
**Trả lời:** Không nhất thiết. Khi đã đạt core, pool ưu tiên xếp task vào queue trước; chỉ khi queue không nhận thêm mới thử mở rộng vượt core.

**Câu 4.** Có 32 worker làm JDBC, nhưng pool connection chỉ có 8 và CPU thấp. Tăng lên 64 worker có chắc tăng throughput không?  
**Trả lời:** Không. Khả năng phục vụ của DB/connection giới hạn; có thể chỉ tăng thời gian chờ và memory. Cần profile query, connection wait và concurrency limit.

**Câu 5.** Vì sao nên tách CPU executor và blocking I/O executor?  
**Trả lời:** Để worker chờ I/O không giữ hết slot dành cho CPU, có thể đặt queue/budget/backpressure riêng và đo độc lập.

**Câu 6.** Một API trả `202` ngay khi gọi `@Async`, nhưng executor đang đầy và task bị từ chối. Hợp đồng có đúng không?  
**Trả lời:** Không, nếu chưa có cơ chế nhận việc bền vững khác. Phải xử lý `TaskRejectedException` và phản hồi rằng hệ thống chưa nhận job.

**Câu 7.** Virtual threads có cho phép bỏ giới hạn remote connection, request timeout và rate limit không?  
**Trả lời:** Không. Chúng thay đổi chi phí lưu giữ thread khi blocking; tài nguyên vật lý, CPU và downstream vẫn hữu hạn.

**Câu 8.** `queueDepth` liên tục tăng trong khi `activeCount == maxPoolSize`; nên tăng `queueCapacity` trước hay điều tra throughput?  
**Trả lời:** Điều tra arrival/completion rate, thời gian xử lý, timeout, bottleneck và giới hạn downstream; queue lớn hơn chỉ kéo dài thời gian chờ nếu tốc độ đến vượt tốc độ xử lý lâu dài.

## 12. Ghi nhớ và tài liệu tham khảo

- **CPU-bound:** khởi đầu gần ngân sách lõi CPU khả dụng, tránh pool quá đông; ưu tiên profiler và đo throughput/tail latency.
- **Blocking I/O-bound:** có thể cần nhiều worker hơn số lõi; dùng tỷ lệ chờ/tính toán để ước lượng, sau đó chặn trên bằng giới hạn connection, memory, timeout và load test.
- **Non-blocking I/O:** concurrency request có thể lớn mà không giữ một platform thread cho mỗi lời gọi; không đưa blocking work lên event loop.
- **Trong Spring:** cấu hình rõ `corePoolSize`, `maxPoolSize` và `queueCapacity`. Queue được dùng trước khi vượt core; dùng pool hữu hạn, rejection policy và tách executor theo tài nguyên khi thích hợp.
- **Trong production:** sizing là một vòng lặp **đo → giả thuyết → thử tải → quan sát → điều chỉnh**, không phải công thức cho một giá trị cố định.

Tài liệu chính thức:

- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html) — abstraction, cấu hình thread pool, hành vi queue trước khi tăng worker.
- [Spring Framework – `ThreadPoolTaskExecutor` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html) — các thuộc tính core/max/queue, trạng thái pool, rejection.
- [Java SE 17 – `ThreadPoolExecutor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html) — nguyên tắc chọn thread/queue và rejection policy.
- [Java SE 17 – `ExecutorService`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html) — submit, shutdown và lifecycle.
- [Spring Framework – `@Async`](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-async) — khai báo, proxy, chọn executor.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-003">Giới hạn theo số kết nối cơ sở dữ liệu hoặc dịch vụ đích</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn theo số kết nối cơ sở dữ liệu hoặc dịch vụ đích

## 1. Vì sao phải giới hạn công việc đồng thời theo tài nguyên đích?

Trong ứng dụng Spring, việc tăng số request thread, dùng `@Async`, mở rộng `ThreadPoolTaskExecutor` hoặc bật virtual threads cho phép nhiều tác vụ tiến triển cùng thời điểm. Nhưng mỗi tác vụ vẫn có thể cần **kết nối JDBC**, **kết nối HTTP**, **suất xử lý của API đối tác**, **giao dịch dài**, bộ nhớ hoặc CPU. Năng lực tiếp nhận việc của executor không đồng nghĩa với năng lực phục vụ của tài nguyên bên dưới.

Ví dụ: một instance Spring có 40 worker xử lý báo cáo, nhưng datasource chỉ cho phép 10 kết nối JDBC. Khi cả 40 worker cùng truy vấn database, chỉ tối đa 10 kết nối của pool đó có thể được cấp đồng thời; các worker còn lại có thể bị giữ trong lúc chờ mượn connection, rồi timeout. Việc tăng worker lên 80 không tạo thêm connection. Nếu database cũng đang bão hòa, tăng pool lên 80 có thể còn làm tình hình tệ hơn vì DB phải xử lý nhiều transaction cùng lúc.

Bài học này giải thích cách **xác định ngân sách tài nguyên**, **giới hạn số hoạt động đồng thời tại điểm gọi**, **cấu hình connection pool và executor**, **xử lý khi chạm trần**, **quan sát hệ thống** và **kiểm chứng dưới tải**. Trọng tâm là mối quan hệ giữa Spring concurrency và số connection/khả năng tiếp nhận của database hoặc dịch vụ đích.

### Bốn giới hạn khác nhau

| Giới hạn | Đếm cái gì? | Ví dụ | Không tự bảo đảm |
| --- | --- | --- | --- |
| Executor concurrency | Số task đang chạy trên một executor | 8 worker `@Async` | Chỉ tối đa 8 truy vấn SQL trên toàn ứng dụng |
| JDBC connection pool | Số connection vật lý pool quản lý, gồm idle và active | HikariCP `maximumPoolSize=12` | Database đủ CPU hoặc mọi transaction nhanh |
| HTTP connection pool | Số kết nối mạng do client quản lý, thường theo remote host | Reactor Netty `maxConnections=16` | Đúng 16 request in-flight, đặc biệt với HTTP/2 |
| Target bulkhead/admission limit | Số cuộc gọi nghiệp vụ được phép đồng thời tới một dependency | 6 thao tác đồng thời gọi service thanh toán | Rate limit 60 requests/giây hoặc quota toàn cluster |

**Connection**, **thread**, **task**, **HTTP request**, **transaction** và **request/giây** là những đơn vị khác nhau. Muốn bảo vệ dịch vụ đích phải chọn giới hạn đúng theo đơn vị dịch vụ đó thực sự bị quá tải.

## 2. Hiểu cơ chế nghẽn: từ executor tới database và API bên ngoài

Một đường xử lý đồng bộ thường có dạng:

```text
HTTP request / scheduled job / message
           |
           v
    Spring controller/service
           |
           v
        executor              <-- có thể có worker/queue riêng
           |
      +----+------+
      |           |
      v           v
 HikariCP      HTTP client    <-- hai loại connection pool khác nhau
      |           |
      v           v
 Database      Remote API     <-- server đích cũng có giới hạn riêng
```

Giả sử luồng xử lý như sau:

1. Request vào Spring; một task được nhận vào executor, chiếm một worker.
2. Task gọi `JdbcTemplate`/JPA; connection pool cho mượn một connection nếu có sẵn.
3. Khi pool hết connection, task có thể **đợi tại bước mượn connection**. Worker trong lúc đó vẫn bị chiếm nếu dùng mô hình blocking.
4. Nếu hết thời gian chờ, HikariCP ném lỗi lấy connection; nếu mượn được, SQL còn phải chờ lock, CPU, I/O hoặc tài nguyên DB.
5. Task trả connection về pool khi phạm vi sử dụng/transaction kết thúc, rồi có thể gọi dịch vụ HTTP khác, lại chịu quy tắc connection pool, timeout và hạn mức phía server.

**Vì sao latency tăng đột ngột khi quá tải?** Công việc đến nhanh hơn năng lực hoàn thành thì người dùng bắt đầu chờ ở nhiều hàng đợi: executor queue, JDBC connection acquisition, DB locks, HTTP pool pending acquisitions hoặc hàng đợi của downstream. Mỗi lớp đều có thể làm tổng thời gian phản hồi dài thêm.

**Vì sao tăng thread không giải quyết được?** Thread chờ kết nối không giúp DB xử lý thêm việc; nó vẫn tốn bộ nhớ, chiếm vị trí trong pool executor và có thể kéo dài timeout của request khác. Số request đang hoạt động tăng mà throughput không tăng tương ứng.

## 3. Giới hạn theo DB connection: HikariCP trong Spring Boot

### 3.1. `maximumPoolSize` và `connectionTimeout`

Với một datasource dùng **HikariCP**, hai cấu hình cơ bản là:

- **`maximumPoolSize`:** số connection tối đa mà pool quản lý, tính cả connection đang được mượn và connection nhàn rỗi. Đây là giới hạn **mỗi pool trên mỗi instance**, không phải giới hạn của toàn DB cluster.
- **`connectionTimeout`:** thời gian tối đa một caller đợi lấy connection từ pool khi chưa có connection sẵn. Đến hạn, HikariCP báo lỗi; đó **không phải** timeout cho câu SQL sau khi đã mượn được connection.

Ví dụ cấu hình trong **`application.yml`** của ứng dụng Spring Boot 3.x dùng HikariCP:

```yaml
spring:
  datasource:
    hikari:
      pool-name: application-db
      maximum-pool-size: 12
      connection-timeout: 2000
```

Đây là **đoạn cấu hình tích hợp**: datasource URL/credentials và JDBC driver phải được cấu hình ở nơi thích hợp. `connection-timeout: 2000` có đơn vị **mili-giây** (2 giây). Khi cả 12 connection đang được dùng và không có connection nhàn rỗi, yêu cầu lấy connection mới sẽ chờ tối đa theo thời hạn đó, rồi báo lỗi nếu không có connection.

Spring Boot thường chọn HikariCP khi HikariCP có trên classpath; các starter JDBC/JPA phổ biến thường kéo HikariCP vào. Nếu ứng dụng chủ động dùng datasource hay connection pool khác, thuộc tính `spring.datasource.hikari.*` **không nhất thiết áp dụng**. Hãy xác minh bean `DataSource` thực tế và các pool được cấu hình, nhất là ứng dụng có nhiều datasource.

Đừng nhầm:

- `maximumPoolSize=12` không có nghĩa là “12 SQL mỗi giây”.
- Cùng một connection có thể chạy nhiều câu SQL **nối tiếp** trong một transaction.
- Một request có thể mượn connection nhiều lần hoặc có lúc giữ nhiều connection tùy thiết kế/transaction.
- Connection được mượn nhưng đang chờ DB lock vẫn chiếm một suất trong pool.
- Tăng `maximumPoolSize` không tự tăng sức mạnh CPU/IOPS của database.

### 3.2. Vì sao transaction dài làm nghẽn pool?

Trong xử lý JDBC thông thường, một transaction đang hoạt động có thể giữ connection cho tới commit hoặc rollback. Nếu code mở transaction rồi gọi HTTP bên ngoài với độ trễ 3 giây, connection có thể bị giữ dù DB không làm việc trong khoảng đó. Tải tăng sẽ làm cạn pool nhanh.

Ví dụ **mẫu cần xem xét lại**:

```java
@Transactional
public void updateAndNotify(Order order) {
    repository.save(order);
    remoteClient.sendNotification(order.getId()); // Có thể đợi mạng lâu.
}
```

Đây là snippet minh họa, không phải class chạy độc lập. Nó còn có rủi ro về nhất quán: DB có thể rollback nhưng remote notification đã gửi; hoặc remote thất bại sau khi tác động ra ngoài. Thiết kế phù hợp thường là giảm phạm vi transaction, tách xử lý ngoài DB ra sau commit hoặc dùng **transactional outbox** khi cần phát sự kiện đáng tin cậy. Không được tự ý tách transaction nếu nghiệp vụ cần bảo đảm nguyên tử ở DB; phải phân tích yêu cầu consistency trước.

Các dấu hiệu cần kiểm tra: thời gian transaction, số connection active, số request chờ mượn connection, truy vấn chậm, lock wait, N+1 query, leak connection và timeout trong lúc DB cao tải.

### 3.3. Connection leak và tài nguyên phải được đóng

Với JDBC thuần, luôn trả connection, statement và result set về đúng vòng đời, thường bằng `try-with-resources`:

```java
try (Connection connection = dataSource.getConnection();
     PreparedStatement statement =
             connection.prepareStatement("SELECT COUNT(*) FROM orders");
     ResultSet rows = statement.executeQuery()) {
    if (rows.next()) {
        return rows.getLong(1);
    }
    return 0L;
}
```

Đây là method-body snippet với `dataSource` đã được inject; class/chữ ký/exception phải được bổ sung khi tích hợp. Không tự đóng connection bên trong một transaction do Spring quản lý theo cách phá vòng đời của transaction. Với `JdbcTemplate`, JPA và quản lý transaction của Spring, ưu tiên cơ chế framework quản lý connection đúng ranh giới.

**Một pool cạn** có thể do tải hợp lệ, transaction giữ connection quá lâu, leak, deadlock hoặc backend chậm. Đừng kết luận cứ “pool nhỏ” là phải tăng size.

## 4. Lập ngân sách kết nối theo số instance và workload

### 4.1. Giới hạn toàn DB và giới hạn mỗi pod

Giả sử database cho phép tối đa **100 connections**, ta dành **20** cho vận hành/phiên quản trị/bảo trì và **20** cho các hệ thống khác. Còn ngân sách **60 connections** dành cho dịch vụ Spring. Nếu dịch vụ chạy **4 instance** và chỉ có một datasource mỗi instance, một trần sơ bộ là:

```text
Ngân sách dịch vụ = 100 - 20 - 20 = 60 connections
Trần sơ bộ mỗi instance = floor(60 / 4) = 15 connections
```

Con số 15 là **ngưỡng ngân sách kết nối**, chưa phải lời khuyên đặt pool đúng 15. Database có thể đạt giới hạn CPU, I/O hoặc lock contention khi số connection active thấp hơn nhiều. Cần thử tải và theo dõi throughput, p95/p99 latency, query wait, DB saturation để chọn giá trị phù hợp.

Nếu autoscaling từ 4 lên 8 instance nhưng giữ `maximumPoolSize=15`, riêng nhóm này có thể giữ tới **120 connections** (cả idle lẫn active tùy vòng đời pool), vượt ngân sách 60. Tính giới hạn theo **số instance cực đại**, không chỉ số pod đang chạy ở thời điểm cấu hình.

Cách tính tổng quát cho các pool tương tự:

```text
Tổng trần connections từ ứng dụng
  = số instance × số pool mỗi instance × maximumPoolSize của từng pool
  (nếu mọi pool cùng kích thước; nếu khác thì cộng trần từng pool)

Ngân sách khả dụng
  = giới hạn DB - phần dành cho hệ thống khác - dự phòng vận hành
```

Cần tính cả datasource read/write, batch worker, migration, admin client, background job và các ứng dụng dùng cùng database. Việc ứng dụng không sử dụng hết connections tại một thời điểm **không** phải lý do để bỏ qua khả năng cùng tăng tải lúc peak.

### 4.2. Không lấy số task bằng số connection một cách máy móc

Nếu một task luôn mượn tối đa **một** connection và không có luồng nào khác truy cập database, executor giới hạn 6 task có thể là một biện pháp kiểm soát số demand. Trong hệ thống thật thường có các luồng HTTP trực tiếp, scheduler, listener và executor khác cùng dùng pool; chúng không bị giới hạn bởi pool executor đó.

Ngoài ra, một tác vụ có thể song song chạy hai query qua hai transaction khác nhau, hoặc giữ connection trong transaction trong khi chờ việc khác. Vì vậy không thể áp công thức chung `maxThreads = maximumPoolSize`.

**Nguyên tắc:** xem connection pool là hàng rào vật lý cuối cùng, còn concurrency limit/bulkhead ở **đúng điểm sinh tải** là lớp kiểm soát phía trước. Dành công suất cho đường xử lý quan trọng; không để batch/background chiếm hết pool khiến request tương tác không còn connection.

## 5. Giới hạn việc nền Spring để dành tài nguyên DB cho request chính

Giả sử pool JDBC có tối đa 12 connection. Ta chỉ cho nhóm tác vụ xuất báo cáo nền sử dụng **tối đa 4 worker cùng lúc**, để các request tương tác còn có cơ hội lấy connection. Đây là giới hạn cho **một nhóm tác vụ**, không phải bảo đảm cứng luôn còn đúng 8 connection vì các luồng khác vẫn có thể chiếm pool.

**Cấu hình Spring Framework/Spring Boot trong ứng dụng có bật async:**

```java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncResourceConfiguration {
    @Bean("reportDbExecutor")
    public ThreadPoolTaskExecutor reportDbExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("report-db-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor; // Spring quản lý initialize/shutdown theo lifecycle.
    }
}
```

**Sử dụng ở một Spring-managed service:**

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportCountService {
    private final JdbcTemplate jdbc;

    public ReportCountService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Async("reportDbExecutor")
    public CompletableFuture<Long> countOrders() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders", Long.class);
        return CompletableFuture.completedFuture(count);
    }
}
```

Đây là hai class tích hợp vào **dự án Spring Boot/Spring Framework đã có datasource, bảng `orders` và dependencies JDBC**, không phải chương trình `javac` độc lập. Với executor này, tối đa bốn task **đang chạy trong nhóm báo cáo** tại một thời điểm; tối đa 16 task nữa có thể đợi trong queue. Khi cả worker và queue đều đầy, chính sách `AbortPolicy` làm submission bị từ chối. Cần xử lý lỗi từ chối tại biên gọi, ví dụ trả phản hồi quá tải hoặc đưa việc vào job store thích hợp.

**Ranh giới quan trọng:**

- `@Async("reportDbExecutor")` chỉ sử dụng executor này khi phương thức được gọi **qua Spring proxy**. Lời gọi nội bộ `this.countOrders()` trong cùng bean thường bỏ qua proxy.
- Bốn task đang chạy không đồng nghĩa chính xác bốn connection đang mượn ở mọi thời điểm: có thể đang tính toán, chờ connection hoặc không dùng DB.
- Các API request thông thường dùng DB trực tiếp không tự đi qua executor `reportDbExecutor`.
- Dữ liệu nghiệp vụ cần transaction phải mở transaction ở **thread làm việc thực sự**. Gọi `@Async` không tự mang transaction imperative của caller sang worker khác.
- `queueCapacity=16` là số chỗ chờ trong executor; nó **không** tăng connection. Queue lớn làm thời gian chờ tăng; đo queue delay để đặt giới hạn.
- Nếu muốn ưu tiên request tương tác khi nghẽn, chỉ giới hạn background worker có thể chưa đủ: vẫn cần đánh giá DB total capacity, query cost và admission control ở các đường khác.

### Khi dùng `SimpleAsyncTaskExecutor` hay virtual threads

`SimpleAsyncTaskExecutor` không tái sử dụng thread, và `setConcurrencyLimit(n)` giới hạn số task đang thực thi qua nó; theo mặc định caller có thể bị chặn khi hết suất. Với Java 21+ và Spring hỗ trợ virtual threads, tạo nhiều virtual thread rẻ hơn platform threads nhưng **không** làm tăng số connection mà HikariCP hoặc downstream cấp được. Vì vậy giới hạn đồng thời tại dependency vẫn cần thiết.

## 6. Giới hạn với dịch vụ đích: connection pool chưa đủ

Đối với dịch vụ HTTP ngoài, có ít nhất ba thứ cần quản lý:

1. **Số kết nối mạng** client có thể mở tới đích.
2. **Số request/operation đang được xử lý** đồng thời tại dịch vụ đích.
3. **Tốc độ request theo thời gian** (requests/giây hoặc requests/phút) mà đối tác cho phép.

Ba đơn vị này thường không bằng nhau. Với HTTP/1.1, request đang sử dụng kết nối thường giữ một connection trong thời gian đó, nhưng còn các chi tiết keep-alive, pooling và proxy. Với **HTTP/2**, nhiều stream/request có thể dùng **cùng một TCP connection**; vì vậy giới hạn 10 connections không có nghĩa chỉ có 10 request đồng thời. Tính năng multiplexing và giới hạn concurrent streams phụ thuộc protocol/implementation.

**Nếu hợp đồng đối tác nói “tối đa 20 request in-flight”**, cần cơ chế admission/bulkhead giới hạn **operation**, không chỉ HTTP connection pool. Nếu hợp đồng nói **100 requests/giây**, cần thêm rate limiter. Nếu đối tác báo 429 hoặc Retry-After, cần chiến lược retry/backoff phù hợp và không gửi dồn cùng lúc.

### Tại sao phải đặt giới hạn theo từng dependency?

Ví dụ service A gọi đồng thời:

- Inventory API: tối đa 10 cuộc gọi in-flight.
- Payment API: tối đa 4 cuộc gọi in-flight.
- Shipping API: tối đa 20 cuộc gọi in-flight.

Đặt chung executor 40 worker **không bảo đảm** từng API được giữ dưới ngưỡng riêng của nó; request có thể cùng chọn Payment và vượt 4. Thiết kế tốt thường có **bulkhead riêng cho Payment, Inventory, Shipping** hoặc pool/bộ điều tiết riêng ở tầng client.

**Bulkhead** là cơ chế cách ly lượng công việc đang hoạt động của từng dependency, giúp một dịch vụ chậm không chiếm toàn bộ tài nguyên của các dịch vụ khác. Nó không thay thế timeout, circuit breaker, rate limiter hay retry budget.

## 7. Ví dụ Java 17 độc lập: giới hạn hai cuộc gọi bằng Semaphore

`java.util.concurrent.Semaphore` là công cụ hữu ích để hiểu cơ chế cấp **permit**: mỗi cuộc gọi muốn đi vào phần bảo vệ phải lấy một permit, và phải trả permit trong `finally`. Ví dụ sau mô phỏng dependency chỉ chịu **hai** operation đồng thời và làm kết quả xác định bằng `CountDownLatch`.

Tạo tệp **`DependencyPermitDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DependencyPermitDemo {
    public static void main(String[] args) throws Exception {
        Semaphore permits = new Semaphore(2);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();

        Runnable remoteCall = () -> {
            if (!permits.tryAcquire()) {
                throw new IllegalStateException("No capacity for worker");
            }

            try {
                int current = inFlight.incrementAndGet();
                maxInFlight.accumulateAndGet(current, Math::max);
                started.countDown();

                try {
                    // Giữ cả hai permit để kiểm tra tình huống đã đầy.
                    releaseWorkers.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                inFlight.decrementAndGet();
                permits.release();
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(remoteCall);
            Future<?> second = pool.submit(remoteCall);

            try {
                if (!started.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Workers did not start");
                }

                System.out.println("maxInFlight=" + maxInFlight.get());

                boolean thirdAccepted = permits.tryAcquire();
                if (thirdAccepted) {
                    permits.release();
                }
                System.out.println("thirdAccepted=" + thirdAccepted);
            } finally {
                releaseWorkers.countDown();
            }

            // Future.get() xác nhận hai tác vụ hoàn tất hoặc báo lỗi.
            first.get();
            second.get();

            System.out.println("availableAfter=" + permits.availablePermits());
        } finally {
            releaseWorkers.countDown();
            pool.shutdownNow();
        }
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac DependencyPermitDemo.java
java DependencyPermitDemo
```

**Standard output dự kiến của chương trình:**

```text
maxInFlight=2
thirdAccepted=false
availableAfter=2
```

**Giải thích:**

- Hai task đã lấy hai permit, tăng số operation đang thực thi và cùng chờ latch. Lời gọi thứ ba từ main có `tryAcquire() == false` vì không còn permit.
- Main mở latch trong `finally`; hai task hoàn thành, trả permit dù một task bị interrupt hoặc lỗi trong vùng bảo vệ.
- Sau `Future.get()` cho cả hai, `availablePermits() == 2`. Như vậy không có permit bị thất lạc trong đường đi bình thường của ví dụ.
- Kết quả dùng latch nên không phụ thuộc vào thứ tự worker được hệ điều hành lên lịch. Đây là mô phỏng **giới hạn operation trong một JVM**, không phải chương trình thực sự gọi database/HTTP.

### Đưa nguyên tắc này vào ứng dụng

Ví dụ method-body snippet cho một **HTTP client blocking**, nơi mọi đường gọi cùng dependency đều qua một gateway có chung semaphore:

```java
private final Semaphore targetSlots = new Semaphore(6);

public TargetResult callTarget(Request request) {
    if (!targetSlots.tryAcquire()) {
        throw new TargetBusyException("Target concurrency limit reached");
    }
    try {
        return blockingHttpClient.call(request);
    } finally {
        targetSlots.release();
    }
}
```

Đoạn trên **chỉ minh họa logic**; `TargetResult`, `Request`, `TargetBusyException` và `blockingHttpClient` là kiểu/biến nghiệp vụ phải định nghĩa khi tích hợp. Tác vụ phải có timeout I/O và xử lý ngoại lệ từ client. Dùng `tryAcquire()` là **fail-fast**; nếu muốn chờ có hạn, có thể dùng `tryAcquire(timeout, unit)` trên thread chặn phù hợp, đồng thời đưa thời gian chờ đó vào end-to-end deadline.

**Ranh giới:** nếu gọi API reactive trả `Mono`/`Flux`, **không được** viết `try { return webClient...bodyToMono(...); } finally { slots.release(); }`; `finally` chạy ngay khi trả publisher, trước khi request async thật hoàn tất. Phải giữ permit cho đến tín hiệu terminal/cancel (ví dụ reactive operator `doFinally`), dùng bulkhead reactive tương thích, và đặc biệt xử lý trường hợp subscription/hủy đúng cách. Không chặn event loop bằng semaphore blocking wait.

Trong môi trường Spring, thư viện như **Resilience4j Bulkhead** cung cấp semaphore bulkhead có `maxConcurrentCalls` và `maxWaitDuration`, phù hợp khi cần tách giới hạn theo từng downstream. Với reactive code, chọn operator tích hợp đúng để permit bao phủ **thời gian thực thi**, không chỉ thời gian dựng publisher.

## 8. HTTP connection pool với WebClient/Reactor Netty

Khi dùng `WebClient` với Reactor Netty, có thể đặt cấu hình connection pool cho một HTTP client riêng. Ví dụ trong ứng dụng Spring WebFlux/WebClient có Reactor Netty:

```java
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration(proxyBeanMethods = false)
public class TargetClientConfiguration {
    @Bean(destroyMethod = "dispose")
    public ConnectionProvider targetConnectionProvider() {
        return ConnectionProvider.builder("target-api-pool")
                .maxConnections(16)
                .pendingAcquireMaxCount(24)
                .pendingAcquireTimeout(Duration.ofMillis(700))
                .build();
    }

    @Bean
    public WebClient targetWebClient(ConnectionProvider targetConnectionProvider) {
        HttpClient httpClient = HttpClient.create(targetConnectionProvider)
                .responseTimeout(Duration.ofSeconds(3));

        return WebClient.builder()
                .baseUrl("https://api.example.com")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
```

Đây là ví dụ **tích hợp**, không chạy độc lập nếu thiếu các dependency `spring-webflux` và `reactor-netty-http`, cũng như endpoint thật. Mục đích của từng cấu hình:

- `maxConnections(16)`: trần connection trong mỗi pool của provider (thường tách theo remote host/port), **không phải** trần số HTTP operation hay requests/giây.
- `pendingAcquireMaxCount(24)`: giới hạn số yêu cầu mượn connection có thể **đợi** khi pool đầy. Hàng đợi pending không nên đặt vô hạn.
- `pendingAcquireTimeout(700 ms)`: thời gian đợi lấy connection. Hết hạn này là lỗi acquire, **không nhất thiết** là downstream response timeout.
- `responseTimeout(3 s)`: giới hạn thời gian response theo cơ chế Reactor Netty; không thay thế connect timeout, DNS deadline hoặc end-to-end API deadline ở mọi đường lỗi.
- `@Bean(destroyMethod = "dispose")`: yêu cầu giải phóng provider khi application context đóng.

**Điểm cần nhớ với HTTP/2:** có thể có nhiều stream trên một kết nối, và quản lý pool/stream tùy phiên bản Reactor Netty. Nếu mục tiêu là “không quá N operation đang chạy đến inventory API”, thêm semaphore/bulkhead theo request ở lớp gọi API vẫn thường rõ nghĩa hơn.

Nhiều client khác như Apache HttpClient, JDK `HttpClient`, Feign hoặc client gRPC có cơ chế quản lý connection riêng. Không sao chép cấu hình Reactor Netty sang các client đó. Hãy kiểm tra implementation được Spring Boot hoặc project thực sự sử dụng, rồi áp các thuộc tính tương ứng.

## 9. Đồng thời, tốc độ và timeout: ba chính sách phải phối hợp

### 9.1. Concurrency limit khác rate limit

- **Concurrency limit:** tối đa **N operation đang xử lý đồng thời**. Một operation 100 ms và một operation 10 giây giữ permit trong các khoảng khác nhau.
- **Rate limit:** tối đa **R requests trên một khoảng thời gian**, ví dụ 60 requests/giây; không tự giới hạn tổng số đang chờ response nếu từng request rất chậm.
- **Connection limit:** số connection của client/server; với HTTP/2, nó không tương đương số request.

Theo Little's Law, trong hệ thống ổn định và cùng đơn vị đo, số công việc trung bình đang trong hệ thống xấp xỉ `L = λ × W` (tốc độ hoàn thành × thời gian ở trong hệ thống). Ví dụ nhận 50 request/giây với thời gian phục vụ 0,4 giây tạo ra khoảng 20 request đang thực thi trung bình, **không phải cam kết p99 hoặc peak**. Độ trễ tăng lên 2 giây mà tốc độ đến giữ nguyên thì nhu cầu in-flight có thể tăng mạnh, khiến queue tích tụ.

Do đó hãy xem giới hạn như một **tài nguyên ngân sách** gắn với lưu lượng, service time, timeout, số instance và đặc điểm phân phối độ trễ. Không tự động nâng concurrency khi thấy queue dài: nếu target đã bão hòa, tăng concurrent calls thường chỉ tăng lỗi và timeout.

### 9.2. Tránh cộng dồn các thời gian chờ

Một request có thể mất thời gian tại nhiều tầng:

```text
Executor queue
    + Hikari connectionTimeout wait
    + DB execution / lock wait
    + HTTP connection pending acquire
    + HTTP connect / response wait
    + retry backoff
    = end-to-end latency (có thể vượt deadline client)
```

Đặt từng timeout 10 giây nhưng HTTP client chỉ cho người dùng chờ 5 giây là không hợp lý nếu các tầng nối tiếp nhau. Ưu tiên xác định **deadline tổng**, phân bổ ngân sách cho từng bước, hủy/từ chối khi quá hạn, và không giữ DB transaction/connection trong khi chờ dịch vụ ngoài lâu hơn cần thiết.

**Hikari `connectionTimeout`** chỉ kiểm soát thời gian mượn connection, không ngắt SQL đang chạy. Đặt query timeout/transaction timeout phù hợp với driver/database; với HTTP client có connection timeout, response timeout và tổng request deadline; phân biệt từng loại để đọc metric đúng.

### 9.3. Retry có thể làm tải đích nặng thêm

Khi DB hay service đích đã đầy, retry tức thời từ hàng trăm request đồng loạt có thể gây **retry storm**: càng chậm càng retry, càng retry càng cạn connection. Retry cần có điều kiện (chỉ lỗi phù hợp), giới hạn số lần, backoff với jitter, tổng deadline và idempotency.

Nếu API bên ngoài yêu cầu rate limit hoặc trả `429 Too Many Requests` kèm `Retry-After`, hãy xử lý theo hợp đồng thay vì chỉ tăng số connection. Với những thao tác có side effect như thanh toán, retry phải gắn với idempotency key/transaction design để không thực hiện trùng.

## 10. Giới hạn cục bộ và giới hạn toàn hệ thống

Giả sử có 5 pod, mỗi pod dùng semaphore 6 permit cho Payment. **Tổng hệ thống có thể có 30 Payment calls đồng thời** nếu mọi pod cùng dùng hết 6 suất. Nếu đối tác chỉ cho phép 10 call in-flight tính trên toàn khách hàng, một semaphore cục bộ mỗi JVM không đủ.

Các hướng xử lý:

| Nhu cầu | Cơ chế thường phù hợp | Điều phải kiểm chứng |
| --- | --- | --- |
| Cần giới hạn thread/task trong một JVM | Executor pool có cấu hình rõ | Tất cả nguồn sinh tải có đi qua executor đó không? |
| Cần giới hạn DB connection mỗi instance | HikariCP `maximumPoolSize` | Tổng trên mọi datasource, pod và workload khác |
| Cần giới hạn operation tới một downstream trong một JVM | Semaphore/Resilience4j bulkhead | Mọi đường gọi có chia sẻ cùng permit không? |
| Cần giới hạn tốc độ chung nhiều pod | Gateway/distributed rate limiter | Đồng hồ, quota, retry, failure mode |
| Cần giới hạn concurrency toàn cluster | Admission control/queue đồng bộ có ownership hoặc service gateway | Lease, fairness, xử lý crash và thu hồi permit |
| Cần công việc bền vững và retry sau restart | Message broker/job queue + bounded consumer | Idempotency, dead-letter, backlog, ordering |

Một giải pháp distributed semaphore/lock cần tính đến tình huống pod crash giữa lúc đang giữ suất: TTL/lease, fencing hoặc xác định khi nào có thể thu hồi permit. Đối với DB, nhiều khi sử dụng **transaction + constraint + atomic update** đúng điều kiện nghiệp vụ sẽ an toàn và đơn giản hơn so với khóa phân tán tự xây.

**Không dùng giới hạn connection làm thay thế kiểm soát dữ liệu.** Một database có 10 connection vẫn có thể gặp lost update, overselling hoặc duplicate insert nếu transaction/constraint không đúng.

## 11. Quan sát và kiểm thử trước khi tối ưu

### 11.1. Những metric cần theo dõi

**Với HikariCP** (khi ứng dụng có Micrometer/Actuator và metric được bật):

- `hikaricp.connections.active`: connection đang được mượn.
- `hikaricp.connections.idle`: connection nhàn rỗi.
- `hikaricp.connections.pending`: caller đang chờ mượn connection.
- `hikaricp.connections.max`: giới hạn connection của pool.
- Timeout khi mượn connection, query latency, slow queries, DB CPU/IOPS, lock waits và transaction duration.

**Với executor:** active threads, pool size, queue size, task reject count, task execution time và thời gian nằm trong queue. Chú ý rằng `maxPoolSize` không bảo đảm executor sẽ nhanh chóng tăng worker tới max nếu hàng đợi còn chỗ.

**Với HTTP/target API:** active/idle/pending connections, pending acquire duration, request in-flight, latency p50/p95/p99, timeout, 429/503/5xx, retry count, quota remaining. Reactor Netty cung cấp metrics cho `reactor.netty.connection.provider.*` khi bật cấu hình phù hợp.

Phải xem **nhiều biểu đồ cùng lúc**: Hikari active=12/12 + pending cao có thể cho biết pool đang đầy, nhưng nếu DB CPU rất thấp và transaction dài do chờ HTTP, giải pháp có thể là giảm thời gian giữ connection, không phải tăng pool.

### 11.2. Bài thử tải có mục tiêu

1. **Chạy tải thấp** để lấy baseline throughput, latency, DB active/idle và downstream response time.
2. **Tăng đồng thời từ từ**, giữ nguyên số pod và workload, xác định điểm throughput ngừng tăng trong khi p95/p99 hoặc pending tăng.
3. **Làm chậm dependency có kiểm soát** (test environment): DB query mất lâu hơn hoặc mock API trễ thêm; xem số task chờ và timeout ở từng tầng.
4. **Đẩy quá hạn mức đã cấu hình**: xác nhận request bị reject/chờ đúng kỳ vọng, queue không tăng vô hạn và các đường nghiệp vụ khác còn khả năng phục vụ.
5. **Thử autoscaling/đa instance**: quan sát tổng DB connections, tổng downstream in-flight và quota khi số pod tăng.
6. **Thử retry và lỗi**: timeout, 429, 503, connection reset, request cancel, shutdown; bảo đảm permit/connection được trả lại, không leak.
7. **Điều chỉnh từng tham số**, rồi đo lại; tránh đồng thời tăng pool, queue, số thread và retry khiến không biết thay đổi nào có tác dụng.

Cần tránh một kiểu test sai: chạy 100 task nhưng không đợi kết quả rồi lập tức đọc metric, kết luận hệ thống không quá tải. Đảm bảo tất cả task đã được submit/counted, có timeout của test, và kiểm tra số task thực thi tối đa, số task bị từ chối, backlog và số task hoàn thành.

## 12. Những lỗi thiết kế thường gặp

### 12.1. “Executor có 50 thread thì Hikari cũng cần 50 connections”

Không nhất thiết. Một task có thể không truy cập DB; ngược lại, luồng HTTP khác cũng dùng DB. Số connection cần dựa trên **mức sử dụng tài nguyên thật** và công suất DB. Với CPU/IOPS yếu, pool lớn thường làm tăng tranh chấp, không tăng throughput.

### 12.2. “Pool 10 connections nghĩa là tối đa 10 request mỗi giây”

Sai: connection có thể xử lý nhiều request nối tiếp; tốc độ còn phụ thuộc query time, transaction length và mức độ sử dụng connection. Đừng trộn **in-flight** với **throughput**.

### 12.3. “Đã giới hạn HTTP connections nên không thể vượt quota API”

Sai với HTTP/2 multiplexing, nhiều request trên một connection hoặc nhiều client/pod. Nếu quota là operation in-flight hay requests/giây, dùng bulkhead/rate limiter đúng nghĩa và tính toàn hệ thống.

### 12.4. “Tăng connectionTimeout sẽ giải quyết pool cạn”

Nó chỉ cho phép caller **chờ lâu hơn** trước khi lỗi; nếu công suất không tăng, queue và latency có thể lớn hơn. Ưu tiên tìm nguyên nhân transaction dài, connection leak, DB lock, slow query và tải vượt năng lực.

### 12.5. “Có @Async thì không giữ request thread”

Một request thread có thể được giải phóng ở phần xử lý phù hợp, nhưng một executor khác vẫn có thể bị kẹt chờ connection; submission cũng có thể bị chặn hoặc từ chối khi pool/queue đầy. Không suy ra hệ thống non-blocking chỉ từ `@Async`.

### 12.6. “Semaphore.tryAcquire() bao quanh việc tạo Mono là đủ”

Sai: với reactive pipeline, tạo `Mono` chỉ mô tả công việc; phép gọi mạng thực tế thường xảy ra lúc subscribe và kết thúc sau đó. Permit phải bao trọn thời gian thực thi, kể cả error/cancel. Dùng operator reactive/bulkhead hỗ trợ lifecycle của publisher.

### 12.7. “Hạn mức 20 per pod bảo đảm tổng không quá 20”

Sai khi chạy nhiều pod. Hãy tính tổng theo số instance cực đại hoặc sử dụng điểm điều phối giới hạn chung.

### 12.8. “Retry nhanh sẽ giúp thoát lỗi quá tải”

Sai khi cùng một dependency đang nghẽn: các đợt retry dồn có thể kéo dài nghẽn và ảnh hưởng request lành. Cần backoff, jitter, tổng retry budget, circuit breaker và idempotency.

### 12.9. “Giữ transaction mở khi gọi remote API không ảnh hưởng pool”

Trong nhiều đường JDBC transaction, connection có thể bị giữ đến khi transaction kết thúc; remote call chậm kéo dài thời gian giữ connection và tăng pending acquisition. Thiết kế lại ranh giới transaction và dùng outbox nếu cần consistency giữa DB và event ngoài.

### 12.10. “Một số cấu hình đẹp là đủ, không cần metric”

Không thể chọn công suất chỉ từ số CPU hoặc lời khuyên chung. Đo dưới tải và lỗi thực tế, kiểm tra cả saturations, p95/p99 và tổng số kết nối qua mọi instance.

## 13. Quy trình lựa chọn cấu hình và tài liệu đối chiếu

Một quy trình thực tế để quyết định concurrency theo DB hoặc dịch vụ đích:

1. **Xác nhận điểm nghẽn:** DB connections, query CPU/locks, HTTP sockets, downstream in-flight quota hay rate quota? Mỗi thứ đòi hỏi cơ chế khác.
2. **Xác định phạm vi:** mỗi task, mỗi executor, mỗi datasource, mỗi remote host, mỗi pod hay toàn cluster. Đếm mọi nguồn tải và số instance cực đại.
3. **Lập ngân sách:** dự phòng cho request ưu tiên và hệ thống khác; chọn concurrency và queue ban đầu theo công suất, không chạy hết trần vật lý.
4. **Giới hạn tại đúng điểm sinh tải:** Hikari pool cho connection; executor cho task nền; semaphore/bulkhead cho operation; rate limiter cho requests/giây; connector pool cho socket.
5. **Quy định hành vi khi đầy:** đợi bao lâu, từ chối như thế nào, thông báo API ra sao, retry ra sao, có cần lưu job bền vững không?
6. **Kiểm soát vòng đời tài nguyên:** đóng connection/permit đúng chỗ, timeout, cancellation, transaction scope, shutdown và handling cho crash/retry.
7. **Kiểm thử có tải và sự cố:** cả khi chạy một instance và nhiều instance, đối chiếu throughput/latency/queue/saturation trước khi thay đổi cấu hình.

### Tài liệu tham khảo

- [Spring Boot 3.5 – SQL Databases](https://docs.spring.io/spring-boot/3.5/reference/data/sql.html): datasource auto-configuration và các loại connection pool.
- [HikariCP – README và cấu hình pool](https://github.com/brettwooldridge/HikariCP): ý nghĩa `maximumPoolSize` và `connectionTimeout`.
- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `TaskExecutor`, `@Async`, thread pool và queue/rejection behavior.
- [Reactor Netty – HTTP Client Reference](https://projectreactor.io/docs/netty/release/reference/http-client.html): connection provider, max connections, pending acquire và metrics.
- [Resilience4j – Bulkhead](https://github.com/resilience4j/resilience4j#73-bulkhead): cách cô lập số concurrent calls theo dependency.
- [Java SE 17 – Semaphore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html): permit, `tryAcquire` và `release`.

**Ghi nhớ:** số thread chỉ cho biết năng lực điều phối task; **số connection và năng lực của DB/remote service mới giới hạn phần việc có thể thực hiện đồng thời**. Cấu hình đúng phải bảo vệ từng tài nguyên ở phạm vi phù hợp, quy định rõ chờ/từ chối khi đầy và được kiểm chứng bằng metric cùng thử tải.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-004">Chọn sức chứa hàng đợi theo bộ nhớ và thời gian chờ cho phép</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn sức chứa hàng đợi theo bộ nhớ và thời gian chờ cho phép

## 1. Vì sao kích thước hàng đợi là quyết định về tài nguyên?

Trong một ứng dụng Spring, nhiều yêu cầu có thể cùng gửi công việc tới `ThreadPoolTaskExecutor`: tính toán báo cáo, gọi hệ thống ngoài, xử lý ảnh, ghi nhận sự kiện hoặc cập nhật dữ liệu. Khi tất cả worker đang bận, tác vụ mới có thể phải **đợi trong hàng đợi** trước khi được một worker lấy ra xử lý.

Giới hạn hàng đợi (`queue capacity`) trả lời câu hỏi: **cho phép bao nhiêu tác vụ đang chờ trong bộ nhớ của tiến trình?** Đây không phải số worker và cũng không phải thời gian được phép chờ.

Chọn hàng đợi quá lớn có thể khiến:
- Nhiều `Runnable`/`Callable`, `Future` và đối tượng bị chúng giữ tham chiếu tồn tại lâu trong heap, làm tăng áp lực GC hoặc gây `OutOfMemoryError`.
- Tác vụ mới nhận được thông báo đã gửi thành công nhưng phải đợi quá lâu mới bắt đầu chạy, đến khi chạy thì dữ liệu có thể hết hạn.
- Việc quá tải bị che giấu: hàng đợi tiếp tục nhận việc dù hệ thống không xử lý kịp.
- Thời gian dừng/redeploy và việc thu hồi, hủy các tác vụ chưa chạy trở nên khó kiểm soát.

Chọn hàng đợi quá nhỏ cũng có chi phí: một đợt tăng tải ngắn có thể gây từ chối nhiều tác vụ mặc dù hệ thống có thể xử lý chúng ngay sau đó. Vì vậy kích thước tốt không phải “càng lớn càng an toàn” hay “luôn đặt bằng 0”, mà là điểm cân bằng có thể **đo, giải thích và kiểm chứng**.

Bài này hướng dẫn chọn một giới hạn ban đầu từ **ngân sách bộ nhớ** và **ngân sách thời gian đợi**, hiểu tương tác giữa hàng đợi với số worker của Spring, rồi kiểm tra bằng tải thực tế.

## 2. Mô hình: worker, tác vụ đang chờ và tổng thời gian

Một tác vụ được gửi đến bộ thực thi có thể đi qua các giai đoạn:

```text
Người gọi gửi công việc
         |
         v
      execute() / submit()
         |
         v
  Còn worker có thể nhận ngay? ---- Có ---> Đang chạy (active)
         |
         | Không
         v
  Còn chỗ chờ trong queue? ------- Có ---> Đang xếp hàng (queued)
         |                                      |
         |                                      v
         |                                Worker rảnh -> chạy
         v
  Cơ chế pool có thể tăng worker? -- Có ---> Tăng worker, chạy
         |
         | Không
         v
      TỪ CHỐI (rejected)
```

Sơ đồ này giúp hình dung, nhưng quy tắc **thực tế của `ThreadPoolExecutor`** chi tiết hơn và được giải thích ở mục 3.

Phân biệt các số liệu:

| Thuật ngữ | Ký hiệu | Ý nghĩa |
|---|---|---|
| Số tác vụ đang chạy | `A` | Tác vụ đã được worker nhận và chưa xong |
| Số tác vụ đang chờ | `Q_current` | Tác vụ hiện nằm trong work queue |
| Sức chứa hàng đợi | `Q_capacity` | Giới hạn số tác vụ được xếp hàng |
| Số worker có thể chạy | `c` | Mức đồng thời của pool tại thời điểm xét |
| Thời gian chờ trước khi chạy | `Wq` | Từ lúc tác vụ được nhận đến lúc worker bắt đầu thực thi |
| Thời gian thực thi | `S` | Từ lúc worker bắt đầu đến khi tác vụ kết thúc |
| Thời gian trong hệ thống thực thi | `T` | Xấp xỉ `Wq + S`, chưa tính xử lý trước/sau của người gọi |
| Tốc độ gửi | `λ` | Số tác vụ đến mỗi giây, cần phân biệt gửi vào và được chấp nhận |
| Tốc độ hoàn thành | `μ` | Số tác vụ hoàn thành mỗi giây ở tải đo được |

**`Q_capacity = 100` nghĩa là tối đa 100 tác vụ chờ, không phải 100 tác vụ chạy đồng thời, cũng không phải 100 MB.** Hệ thống có thể đồng thời chứa tác vụ đang chạy, tác vụ nằm trong queue và những dữ liệu được giữ ở tầng HTTP/message broker phía trước.

Đối với một yêu cầu có mục tiêu phản hồi trong 500 ms, thời gian dành cho queue có thể chỉ là 100 ms sau khi trừ thời gian gọi cơ sở dữ liệu, xử lý nghiệp vụ, truyền mạng và các bước khác. Không nên dùng toàn bộ hạn mức độ trễ cho `Wq`.

## 3. ThreadPoolExecutor xử lý queue trước khi tăng đến maxPoolSize

`ThreadPoolTaskExecutor` của Spring dựa trên `java.util.concurrent.ThreadPoolExecutor`. Ba cấu hình quan trọng là:

- `corePoolSize`: số worker lõi mà pool cố gắng tạo khi nhận công việc (theo quy tắc tạo worker).
- `maxPoolSize`: giới hạn số worker tối đa.
- `queueCapacity`: số tác vụ có thể chờ trong work queue.

Quy tắc của `ThreadPoolExecutor.execute(...)` ở điều kiện bình thường:

1. Khi số worker ít hơn `corePoolSize`, pool ưu tiên tạo một worker mới để nhận việc.
2. Khi đã đạt mức lõi, pool **ưu tiên đưa việc vào hàng đợi**.
3. Chỉ khi việc **không thể vào queue** mới thử tăng số worker lên tới `maxPoolSize`.
4. Nếu pool không thể nhận việc qua cả worker lẫn queue, `RejectedExecutionHandler` quyết định cách từ chối/xử lý.

Ví dụ cấu hình `corePoolSize=4`, `maxPoolSize=8`, `queueCapacity=20`:

```text
Các task đầu -> tạo tới 4 worker lõi
4 worker bận -> những task tiếp theo chờ (tối đa 20 vị trí)
Queue đầy -> pool mới có thể tăng worker thứ 5 ... thứ 8
8 worker bận + queue đầy -> từ chối hoặc dùng chính sách từ chối
```

Đây là **sơ đồ trạng thái lý tưởng khi task đủ lâu để pool đang tải**. Trong thực tế, worker có thể hoàn tất và lấy việc khỏi queue ngay khi tác vụ khác gửi vào, nên không thể chỉ nhìn số thứ tự task để suy ra chính xác kết quả.

### 3.1. Queue rất lớn làm maxPoolSize gần như không có tác dụng

Nếu queue có sức chứa cực lớn, pool gần như luôn xếp hàng sau khi đạt `corePoolSize`. `maxPoolSize` sẽ không được dùng để tăng số worker trong điều kiện thông thường vì queue chưa đầy.

Đặc biệt, `ThreadPoolTaskExecutor` **khi không được cấu hình riêng** có giá trị mặc định trong Spring Framework là `corePoolSize=1`, `maxPoolSize=Integer.MAX_VALUE` và `queueCapacity=Integer.MAX_VALUE`. Đó là mặc định của **chính lớp Spring này**; cấu hình tự động của một phiên bản Spring Boot cụ thể có thể khác và cần xem cấu hình hiệu lực. Không nên dựa vào giá trị mặc định khi triển khai hệ thống có yêu cầu tải/độ trễ.

**Kết luận kỹ thuật:** Tăng `maxPoolSize` mà không hiểu `queueCapacity` có thể không làm tăng số worker trong lúc tải cao. Hãy kiểm tra quy tắc xếp hàng trước khi điều chỉnh số luồng.

### 3.2. queueCapacity=0 khác với queueCapacity dương

Trong `ThreadPoolTaskExecutor`:

| `queueCapacity` | Loại queue được tạo | Tác động |
|---|---|---|
| Giá trị dương, ví dụ `16` | `LinkedBlockingQueue` có giới hạn tương ứng | Cho phép tối đa 16 tác vụ chờ |
| `0` | `SynchronousQueue` | Không lưu tác vụ trong hàng đợi; chuyển trực tiếp cho worker hoặc thử tạo worker khác |
| Giá trị cực lớn/mặc định | `LinkedBlockingQueue` có giới hạn rất lớn về mặt số nguyên | Có nguy cơ tích lũy nhiều công việc trong heap trước khi đạt giới hạn đếm |

`SynchronousQueue` không lưu task và không phải queue có một vị trí. Với `queueCapacity=0`, nếu mọi worker đã bận và không thể tạo worker mới, việc bị từ chối theo policy; mức `maxPoolSize` hữu hạn là thiết yếu để kiểm soát tài nguyên. Không đặt `maxPoolSize` không giới hạn một cách vô thức chỉ vì không dùng queue.

`queueCapacity` thường cần được đặt **trước khi executor được khởi tạo**. Thay property trên bean đã chạy không có nghĩa underlying queue tự được thay bằng queue có dung lượng khác.

## 4. Ràng buộc thứ nhất: mỗi tác vụ đang chờ chiếm bao nhiêu bộ nhớ?

Một phần tử queue không chỉ là con trỏ tới tác vụ. Nó giữ tham chiếu tới một `Runnable`, đối tượng `FutureTask` hoặc wrapper của framework. Tác vụ có thể giữ cả một đồ thị đối tượng (`object graph`):

- Lambda/callback capture `request`, `byte[]` chứa ảnh hoặc file, danh sách bản ghi, JSON parse tree.
- Đối tượng `Future`, ngữ cảnh, metadata, dữ liệu tạo bởi `TaskDecorator` và closure liên quan.
- Các tham số, collection, cache tạm hoặc tham chiếu tới bean, tài liệu, dữ liệu của cả request.
- Cấu trúc nội bộ của queue, ví dụ nút của `LinkedBlockingQueue`, cộng với phần quản lý của executor.

Ví dụ:

```java
byte[] payload = loadPayload();
executor.execute(() -> process(payload));
```

Mã trên **chỉ là đoạn minh họa**, với `loadPayload()`/`process(...)` là phương thức ứng dụng. Lambda giữ tham chiếu tới `payload`; nếu công việc chờ lâu, mảng vẫn có thể được giữ sống dù request đã trả về. Một queue đếm 1.000 phần tử có thể rất nhẹ nếu mỗi task nhỏ, hoặc rất nặng nếu mỗi task giữ mảng nhiều MB.

Không thể lấy kích thước mã lambda hoặc kích thước reference (4/8 byte) để suy ra tổng bộ nhớ. Cần ước lượng **bộ nhớ còn được giữ lại do việc xếp hàng** (`retained heap`), bao gồm đối tượng chỉ còn sống nhờ task. Dùng profiling/heap dump hoặc đo mức tiêu thụ heap với tải đại diện; tránh cộng trùng các đối tượng được nhiều task chia sẻ.

### 4.1. Lập ngân sách heap thực tế

Đặt:

- `M_queue`: số byte heap **được phép dành cho những task đang xếp hàng**, sau khi trừ tải nền, dữ liệu của tác vụ đang chạy, các cache và khoảng dự phòng GC.
- `B_task`: ước lượng bảo thủ số byte heap bị giữ bởi **một tác vụ đang chờ**, gồm wrapper, queue node và dữ liệu được giữ tham chiếu.
- `Q_memory`: giới hạn queue tính từ ngân sách bộ nhớ.

Công thức sơ bộ:

\[
Q_{\text{memory}} = \left\lfloor \frac{M_{\text{queue}}}{B_{\text{task}}} \right\rfloor
\]

Ví dụ dành 20 MiB heap cho queue và giả định **mỗi tác vụ giữ tối đa khoảng 96 KiB trong mô hình thử nghiệm**:

\[
Q_{\text{memory}} =
\left\lfloor \frac{20 \times 1024}{96} \right\rfloor
= 213\ \text{tác vụ}
\]

Đây chỉ là **giới hạn theo bộ nhớ**, chưa trả lời liệu chờ 213 tác vụ có chấp nhận được hay không. Trong production, `B_task` biến thiên; nên đo đạc ở nhiều payload, lấy cận trên thực tế cùng hệ số dự phòng. Một giá trị trung bình hoặc p95 không phải cận trên tuyệt đối và không đảm bảo sẽ không có task lớn bất thường.

Ngân sách queue còn phải tách khỏi bộ nhớ cho: task đang chạy, thread stacks (chủ yếu ngoài Java heap), connection, buffer trực tiếp (`direct buffer`), cache, response đang trả và các bộ phận khác. Đừng gán toàn bộ `-Xmx` cho hàng đợi.

### 4.2. Giảm dữ liệu bị task giữ lại

Nếu task chỉ cần một ID, thay vì capture cả một request/DTO lớn, có thể capture ID bất biến và tải dữ liệu cần thiết tại nơi thực thi (cân nhắc thêm chi phí I/O và khả năng dữ liệu thay đổi). Không giữ `HttpServletRequest`, stream đang mở hay object có vòng đời request trong queue dài.

Các cách tối ưu khác là đưa payload lớn vào vùng lưu trữ thích hợp và chỉ queue một reference có độ bền, chia lô theo kích thước kiểm soát được, hoặc loại bỏ dữ liệu tạm trước khi submit. Khi nhiệm vụ phải tồn tại qua restart, queue trong heap vốn không phải giải pháp lưu công việc bền vững.

## 5. Ràng buộc thứ hai: tác vụ được phép chờ bao lâu?

Ngân sách độ trễ của hàng đợi `Wq_budget` cần được suy ra từ yêu cầu người dùng/nguồn gọi:

```text
Thời hạn đầu-cuối của yêu cầu
  - chi phí tạo/nhận yêu cầu
  - thời gian thực thi nghiệp vụ dự kiến (đặc biệt p95/p99)
  - thời gian trả kết quả/đường truyền
  - khoảng dự phòng
= ngân sách tối đa dành cho queue wait
```

Nếu end-to-end SLO là 600 ms, worker có thể phải dùng 350 ms ở phân vị cao, các giai đoạn khác mất 100 ms và cần dự phòng 50 ms, thì queue wait chỉ có ngân sách khoảng 100 ms. Dồn 500 ms để đợi queue sẽ làm mục tiêu 600 ms trở nên khó đạt.

Một phép tính **gần đúng để tìm ứng viên cấu hình**:

\[
Q_{\text{time}} \approx
\left\lfloor \mu_{\text{effective}} \times W_{q,\text{budget}}\right\rfloor
\]

Trong đó `μ_effective` là **tốc độ tác vụ thực tế có thể bắt đầu/hoàn thành** ở điều kiện tải và cấu hình worker được đo, tính theo task/giây. Nếu pool đang bận và xử lý ổn định, một queue có `Q` công việc phía trước cần khoảng `Q / μ_effective` giây để được giải phóng; phải cộng tác động của tác vụ đang chạy, độ dao động thời gian xử lý và lịch thực thi.

Ví dụ tốc độ hoàn thành có thể duy trì khoảng **80 tác vụ/giây**, và ứng dụng chỉ cho phép **0,20 giây** chờ:

\[
Q_{\text{time}} \approx \lfloor 80 \times 0.20 \rfloor = 16
\]

Con số 16 là **điểm bắt đầu để thử tải**, không phải cam kết mọi task đều bắt đầu trong 200 ms. Với workload có job rất dài, phân phối không đồng đều, GC pause hoặc downstream chậm bất thường, ngay cả queue ngắn vẫn có thể vượt ngân sách.

### 5.1. Đừng nhầm với định luật Little

Với hệ thống ổn định xét trên khoảng thời gian đủ dài, định luật Little cho **giá trị trung bình**:

\[
L_q = \lambda_{\text{accepted}} \times \overline{W_q}
\]

Trong đó `Lq` là số task trung bình trong queue, `λ_accepted` là thông lượng task được **nhận thực sự** (và hoàn thành ở trạng thái ổn định), còn `Wq_bar` là thời gian chờ **trung bình**. Phương trình này giúp đối chiếu số liệu đo: nếu thông lượng 80 task/s và trung bình chờ 0,1 s thì có trung bình khoảng 8 task đang chờ.

**Không thể lấy định luật Little làm bảo đảm cho p95/p99 hoặc thời gian chờ tối đa.** Nó không cho biết tail latency, và phép dùng trạng thái ổn định không thể che giấu việc queue đang tăng vô hạn trong tình huống đầu vào vượt năng lực phục vụ.

Nếu yêu cầu là “99% task bắt đầu trong 200 ms”, phải đo trực tiếp `p99 queue wait` với tải đại diện, không chỉ tính bằng tốc độ trung bình.

## 6. Cách gộp hai ngân sách để chọn giới hạn ban đầu

Với một loại tác vụ tương đối đồng nhất, cách khởi đầu có thể kiểm toán:

\[
Q_{\text{candidate}} =
\max\left(0,\;
\min(Q_{\text{memory}}, Q_{\text{time}})\right)
\]

Nếu có một khoảng dự phòng riêng, có thể chọn **nhỏ hơn** `Q_candidate` và dùng thử tải để điều chỉnh. Công thức này không biến queue thành cơ chế bảo đảm SLO; nó chỉ tránh chọn một con số vượt quá một trong hai ngân sách ngay từ bước thiết kế.

Lấy cùng bộ dữ liệu ví dụ:

| Đại lượng | Giá trị giả định |
|---|---:|
| Bộ nhớ heap dành cho tác vụ đang chờ | 20 MiB |
| Bộ nhớ mỗi task queued (ước lượng có dự phòng) | 96 KiB |
| Giới hạn theo bộ nhớ | 213 task |
| Tốc độ hoàn tất đo được của pool | 80 task/s |
| Ngân sách chờ queue | 200 ms |
| Giới hạn theo thời gian | 16 task |
| **Ứng viên `queueCapacity`** | **16 task** |

**Giới hạn đang quyết định là độ trễ, không phải bộ nhớ.** Nếu đặt queue 213 chỉ vì heap còn chỗ, thì ở tốc độ 80 task/s, việc đứng sau khoảng 213 task có thể phải chờ cỡ 2,66 giây trong mô hình đơn giản, vượt 200 ms rất xa.

Ngược lại, nếu mỗi task giữ 2 MiB và chỉ dành 20 MiB heap cho queue thì giới hạn bộ nhớ khoảng 10 task, dù thời gian chờ cho phép 16. Khi đó chọn 10 hoặc ít hơn, rồi quyết định cách từ chối/giảm tải.

### 6.1. Ví dụ Java 17 tính hai giới hạn

**Tệp `QueueBudgetDemo.java` — chương trình độc lập, không cần Spring**

```java
public class QueueBudgetDemo {
    public static void main(String[] args) {
        long budgetBytes = 20L * 1024 * 1024;
        long estimatedBytesPerQueuedTask = 96L * 1024;

        double measuredThroughputPerSecond = 80.0;
        double queueWaitBudgetSeconds = 0.20;

        long byMemory = budgetBytes / estimatedBytesPerQueuedTask;
        long byTime = (long) Math.floor(
                measuredThroughputPerSecond * queueWaitBudgetSeconds);

        long initialCapacity = Math.max(0L,
                Math.min(byMemory, byTime));

        System.out.println("Giới hạn theo bộ nhớ: " + byMemory);
        System.out.println("Giới hạn theo thời gian: " + byTime);
        System.out.println("Ứng viên cấu hình: " + initialCapacity);
    }
}
```

```bash
javac QueueBudgetDemo.java
java QueueBudgetDemo
```

Đầu ra xác định:

```text
Giới hạn theo bộ nhớ: 213
Giới hạn theo thời gian: 16
Ứng viên cấu hình: 16
```

Chương trình cho thấy cách tính, **không đo heap hoặc tốc độ hệ thống thay cho bạn**. Các đầu vào trong code là giả định để luyện tập; production cần thay bằng dữ liệu đo được.

## 7. Điều gì xảy ra khi tải đầu vào cao hơn năng lực xử lý?

Gọi `λ_in` là tốc độ task đến và `μ_effective` là tốc độ pool phục vụ ổn định.

- Nếu `λ_in` thường thấp hơn năng lực phục vụ, queue có cơ hội trở về mức thấp sau mỗi đợt tăng tải.
- Nếu `λ_in` liên tục lớn hơn năng lực phục vụ, lượng công việc chờ có xu hướng tăng khoảng `λ_in - μ_effective` task/giây cho đến khi chạm giới hạn và xảy ra từ chối, chặn caller hoặc chính sách khác.
- Nếu tốc độ trung bình gần năng lực tối đa, thời gian chờ vẫn có thể tăng mạnh do dao động thời gian xử lý, đợt đến theo cụm và tranh chấp tài nguyên.

Ví dụ đầu vào duy trì **120 task/giây**, pool chỉ hoàn tất được **80 task/giây**: dư khoảng **40 task/giây**. Nếu worker đã bận và queue đang trống, queue 16 sẽ đầy sau cỡ **0,4 giây** trong mô hình liên tục đơn giản. Queue 160 chỉ dời thời điểm quá tải ra khoảng 4 giây, **không tăng thông lượng thực của pool**.

Đây là lập luận tốc độ chênh lệch để hiểu tình trạng quá tải, không phải dự báo thời gian đầy chính xác cho pool có thể mở rộng từ core tới max, nhiều loại job, hay nguồn đến biến thiên. Trong thực tế phải đo `μ_effective` theo cấu hình và tải thật.

**Giải pháp cho quá tải kéo dài:** giảm lưu lượng, áp dụng giới hạn đồng thời ở đầu vào, điều chỉnh tác vụ, tăng năng lực xử lý nếu tài nguyên cho phép, hoặc chuyển công việc sang hàng đợi bền vững. Chỉ tăng queue không giải quyết sự mất cân bằng dài hạn.

### 7.1. Một đợt tăng tải ngắn khác quá tải kéo dài

Giả sử pool có 8 worker đang rảnh và đồng thời nhận 20 tác vụ, mỗi tác vụ chạy đủ lâu để không kết thúc ngay. Tối đa 8 tác vụ có thể chạy sớm trên 8 worker; phần còn lại có thể đợi hoặc được chuyển cho worker mở rộng tùy `core/max/queue`. Queue 16 có thể hấp thụ một phần đợt tăng, nhưng điều đó không chứng minh nó xử lý được **mọi** burst 20 task với mức `p99 Wq` mong muốn.

Cần đo cả **biên độ burst** (bao nhiêu task đến dồn), **thời lượng burst**, **khoảng nghỉ để queue thoát tải**, và **tương quan giữa thời gian chạy task**. Một đợt tăng 5 giây và một đợt tăng 5 phút không tương đương về ngân sách.

## 8. Áp dụng vào ThreadPoolTaskExecutor của Spring

Cấu hình minh họa dùng `corePoolSize=8`, `maxPoolSize=12`, `queueCapacity=16`. Đây là **một ứng viên để kiểm thử**, không phải cấu hình tiêu chuẩn đúng cho mọi hệ thống.

**Tệp `AsyncExecutorConfig.java` — cấu hình Spring, cần dependency Spring Framework và ApplicationContext**

```java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncExecutorConfig {

    @Bean("boundedTaskExecutor")
    public ThreadPoolTaskExecutor boundedTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("bounded-worker-");

        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());

        return executor;
    }
}
```

Giải thích:

1. `corePoolSize=8` cho pool khả năng mở rộng tới 8 worker lõi khi có nhu cầu.
2. `maxPoolSize=12` cho phép tối đa 12 worker (không phải luôn duy trì đủ 12).
3. `queueCapacity=16` đặt giới hạn cho **tác vụ đang chờ** trong `LinkedBlockingQueue`.
4. Khi core worker bận, queue được sử dụng trước; chỉ khi queue không nhận thêm việc thì pool thử tạo worker thứ 9–12.
5. `AbortPolicy` cho phép người gọi nhận biết tình trạng từ chối. Spring có thể biểu diễn trường hợp này bằng `TaskRejectedException` qua giao diện `TaskExecutor`.

Phương thức `@Bean` trả về object để **Spring quản lý vòng đời**; ở cách đăng ký bean thông thường, Spring gọi phương thức khởi tạo cần thiết cho executor. Không cần gọi `initialize()` thủ công từ phương thức bean khi bean lifecycle được quản lý đúng. Nếu tự `new ThreadPoolTaskExecutor()` ở ngoài container thì phải tự xử lý khởi tạo và đóng.

Ví dụ một service dùng executor đã đăng ký:

**Tệp `ReportService.java` — thành phần Spring, không có `main` độc lập**

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    @Async("boundedTaskExecutor")
    public CompletableFuture<String> buildReport(String reportId) {
        String result = "Đã tạo báo cáo " + reportId;
        return CompletableFuture.completedFuture(result);
    }
}
```

Phương thức cần được gọi **qua Spring proxy** từ một bean khác thì `@Async` mới đưa công việc sang executor trong cấu hình proxy phổ biến. Gọi `this.buildReport(...)` nội bộ thường bỏ qua proxy. Một lệnh `@Async` có thể bị từ chối ngay khi gửi nếu pool đầy; caller phải có chính sách xử lý.

Không viết test dựa vào thứ tự in log của các task bất đồng bộ; lịch chạy phụ thuộc thực thi. Các class Spring trên là mã cấu hình/thành phần để ghép vào ứng dụng Spring phù hợp, **không phải** chương trình Java SE thuần chỉ cần `javac`.

## 9. Java 17: thí nghiệm queue đầy và từ chối có kết quả xác định

Để thấy rõ ý nghĩa `queueCapacity` mà không cần server Spring, dùng `ThreadPoolExecutor` Java chuẩn với **1 worker** và queue chứa **2** tác vụ. Chúng ta giữ task đầu tiên ở một `CountDownLatch`; vì vậy hai tác vụ tiếp theo chắc chắn chờ trong queue khi kiểm tra.

**Tệp `BoundedQueueDemo.java` — chương trình Java 17 độc lập**

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BoundedQueueDemo {
    public static void main(String[] args)
            throws InterruptedException {

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch allowWorkerFinish = new CountDownLatch(1);

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2),
                new ThreadPoolExecutor.AbortPolicy());

        try {
            pool.execute(() -> {
                workerStarted.countDown();
                try {
                    allowWorkerFinish.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });

            workerStarted.await();

            pool.execute(() -> {});
            pool.execute(() -> {});
            System.out.println("Đang xếp hàng: "
                    + pool.getQueue().size());

            try {
                pool.execute(() -> {});
                System.out.println("Không mong đợi: nhận task thứ tư");
            } catch (RejectedExecutionException rejected) {
                System.out.println("Tác vụ thứ tư bị từ chối");
            }

            System.out.println("Hàng đợi sau từ chối: "
                    + pool.getQueue().size());
        } finally {
            allowWorkerFinish.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
```

```bash
javac BoundedQueueDemo.java
java BoundedQueueDemo
```

Đầu ra xác định (không phụ thuộc thứ tự worker thực thi các task chờ):

```text
Đang xếp hàng: 2
Tác vụ thứ tư bị từ chối
Hàng đợi sau từ chối: 2
```

Tại sao kết quả chắc chắn?

- `workerStarted.await()` bảo đảm tác vụ đầu tiên đã vào worker.
- Worker đầu tiên chặn ở `allowWorkerFinish.await()` **trước khi** chúng ta gửi task thứ hai và thứ ba.
- Pool có tối đa một worker, vì vậy hai task tiếp theo chỉ có thể đợi trong queue dung lượng 2.
- Task thứ tư bị từ chối bởi `AbortPolicy` vì worker đang bận và queue đã đầy.
- Chỉ trong `finally` mới giải phóng latch, cho worker hoàn tất và xử lý các task chờ; `shutdown()`/`awaitTermination(...)` giúp chương trình thoát có kiểm soát.

Trong production, `getQueue().size()` chỉ là snapshot tại thời điểm gọi và có thể thay đổi ngay. Thí nghiệm có kết quả cố định nhờ latch, không có nghĩa mọi hệ thống luôn chính xác theo thứ tự task 1/2/3/4 dưới tải cạnh tranh.

Cũng lưu ý: ví dụ dùng `ArrayBlockingQueue` của Java để trình diễn khái niệm. Spring `ThreadPoolTaskExecutor` với `queueCapacity > 0` dùng `LinkedBlockingQueue`; hai queue đều có giới hạn đếm nhưng khác cấu trúc nội bộ, chi phí bộ nhớ và đặc tính hiệu năng.

## 10. Khi queue đầy: phải có chính sách backpressure

Chọn `queueCapacity` hữu hạn nghĩa là **công khai thừa nhận hệ thống có giới hạn**. Cần xác định người gọi thấy gì khi không thể nhận việc.

Một số `RejectedExecutionHandler` của `ThreadPoolExecutor`:

| Chính sách | Hành vi khi quá tải | Hệ quả cần cân nhắc |
|---|---|---|
| `AbortPolicy` | Ném `RejectedExecutionException` | Phản hồi rõ ràng, cho tầng trên quyết định từ chối/điều tiết |
| `CallerRunsPolicy` | Khi executor chưa shutdown, caller tự chạy task trên luồng gửi | Tạo áp lực ngược vì caller chậm lại; có thể chặn HTTP/request thread |
| `DiscardPolicy` | Bỏ task bị từ chối mà không ném lỗi | Rủi ro mất công việc âm thầm |
| `DiscardOldestPolicy` | Bỏ phần tử đầu queue rồi thử gửi task mới | Có thể mất tác vụ cũ và phá trật tự nghiệp vụ |

`CallerRunsPolicy` có thể phù hợp khi caller **được phép chặn** và hệ thống chấp nhận áp lực ngược. Nhưng trong request thread cần đáp ứng nhanh, thực thi tác vụ nặng trên caller có thể gây timeout; khi executor đã shutdown, policy này cũng có thể bỏ tác vụ thay vì chạy nó. Không nên coi `CallerRunsPolicy` là lời giải phổ quát.

Một luồng xử lý lỗi có thể là:

```text
Pool đầy / từ chối
        |
        v
Đây là công việc mất được hay phải bảo đảm?
        |
        +-- Mất được --> báo metrics/log theo chính sách, không "nuốt" âm thầm
        |
        +-- Phải bảo đảm --> đưa vào hệ thống lưu trữ bền vững phù hợp,
        |                   hoặc trả lỗi có thể retry an toàn
        |
        +-- Người gọi đồng bộ --> trả tín hiệu quá tải rõ ràng
                              (ví dụ HTTP 429 hoặc 503 theo hợp đồng API)
```

Chính sách retry phải có giới hạn, khoảng dãn và tránh đồng loạt gửi lại; task có tác động bên ngoài cần idempotency để không thực hiện trùng khi timeout/retry. Retry liên tục trong cùng pool đang đầy chỉ làm tăng mức quá tải.

**Hàng đợi bị giới hạn là nơi bắt đầu kiểm soát tải, không thay thế rate limiting, bulkhead, circuit breaker, timeout hay hàng đợi bền vững** nếu bài toán cần những cơ chế đó.

## 11. Đừng dùng thời gian chờ của Future để thay thế giới hạn queue

Người gọi có thể đặt:

```java
result.get(200, TimeUnit.MILLISECONDS);
```

Đây là đoạn mã minh họa khi `result` là `Future<T>`. `get(timeout)` giới hạn **thời gian luồng gọi chờ kết quả**, không đặt giới hạn thời gian một task được giữ trong executor queue.

Nếu `get(...)` hết hạn, task có thể vẫn đang xếp hàng hoặc đang chạy; lời gọi không tự động xóa task khỏi queue. `cancel(true)` chỉ là yêu cầu hủy/ngắt theo hợp đồng, và việc loại bỏ ngay task đã hủy khỏi underlying queue phụ thuộc cơ chế executor/task wrapper.

Do đó, nếu nội dung chỉ còn giá trị tới một hạn chót, tác vụ nên nhận deadline rõ ràng và kiểm tra khi bắt đầu chạy. Nếu tác vụ quá hạn, nên tránh thực hiện side effect không còn hợp lệ. Khi hủy, phải giám sát tỷ lệ hủy và khả năng queue còn giữ các wrapper chưa được dọn.

Một hệ thống có `Wq_budget=200 ms` không nên chỉ đơn giản đặt `Future.get(200 ms)` rồi kết luận “queue đáp ứng SLO”; điều đó chỉ thay thời gian chờ của caller và có thể để lại công việc nền không còn cần thiết.

## 12. Hai loại giới hạn đi cùng nhau: pool, queue, downstream

Giả sử pool có `maxPoolSize=12` và `queueCapacity=16`. Tại một thời điểm, executor có thể giữ tối đa khoảng 12 tác vụ đang chạy và 16 tác vụ chờ (chưa kể công việc của các tầng khác). Nhưng **số luồng lớn hơn không tự bảo đảm thông lượng cao hơn**.

Với tác vụ CPU-bound, vượt số lõi hữu dụng có thể tăng chuyển ngữ cảnh và tranh chấp CPU, khiến `S` lớn hơn. Với I/O-bound, thêm worker có thể tăng mức đồng thời nhưng cũng có thể làm cạn connection pool, DB hoặc quota của dịch vụ ngoài. Khi downstream bị nghẽn, `μ_effective` có thể **giảm** dù tăng `maxPoolSize`.

Xem xét các tài nguyên liên quan:

- CPU cores, thời gian xử lý từng task và mức sử dụng CPU.
- Bộ nhớ heap cho queue và task đang chạy, cùng bộ nhớ native cho thread stacks.
- Số connection DB, HTTP connection, connection timeout và quota API.
- Thread pool khác của HTTP server, scheduler hoặc message consumer.
- Hàng đợi ở broker, reverse proxy, servlet request và chính executor. Các hàng đợi nối tiếp sẽ cộng dồn độ trễ.
- Độ trễ mạng/GC pause có thể làm p99 tệ hơn giá trị trung bình rất nhiều.

Nếu database pool chỉ có 8 connection mà 50 worker cùng chờ DB, tăng `maxPoolSize` từ 12 lên 50 có thể chỉ tạo thêm hàng chờ **ở connection pool**. Việc chọn queue phải căn cứ vào cả năng lực xử lý cuối chuỗi, không chỉ số worker trong một bean.

## 13. Đo queue wait thay vì chỉ đo queue size

Không có một con số `queueCapacity` tốt cho mọi lưu lượng. Khi chạy thử tải, tối thiểu theo dõi:

| Chỉ số | Câu hỏi nó giúp trả lời |
|---|---|
| `queue.size` hiện tại và mức đỉnh | Có thường xuyên chạm giới hạn không? |
| `activeCount`, `poolSize`, `maxPoolSize` | Có bao nhiêu worker đang phục vụ và khả năng tăng thêm ra sao? |
| Thời gian chờ task p50/p95/p99 | Task có bắt đầu trong ngân sách không? |
| Thời gian thực thi task p50/p95/p99 | Nút thắt là queue hay bản thân công việc? |
| Throughput hoàn thành và tốc độ gửi | Có đang tích lũy công việc hay không? |
| Số task từ chối / tỷ lệ rejection | Hệ thống đã vượt giới hạn ở đâu? |
| Heap, GC pauses, retained size theo payload | Queue có đang giữ quá nhiều đối tượng? |
| Timeout, canceled, deadline-expired | Bao nhiêu việc trở nên vô ích vì chờ lâu? |

`ThreadPoolTaskExecutor` cung cấp các phương thức theo dõi như `getActiveCount()`, `getPoolSize()` và `getThreadPoolExecutor()` (sau khởi tạo); underlying executor có `getQueue().size()` và các bộ đếm liên quan. Những số liệu này là quan sát theo thời điểm, không phải snapshot nguyên tử của mọi phần hệ thống.

**Đo queue wait đúng cách:** ghi timestamp (đơn điệu, ví dụ `System.nanoTime()`) lúc task được nhận/gửi và timestamp khi nó **thực sự bắt đầu chạy**, tính hiệu số. Đừng dùng thời gian từ khi nhận HTTP request làm queue wait nếu giữa request và submit còn các bước khác. Với task bị từ chối, phải ghi riêng vì nó chưa có thời điểm bắt đầu.

Trong Spring có thể dùng `TaskDecorator` để trang trí `Runnable` và đo thời gian đến lúc chạy, nhưng phải hiểu rằng task truyền tới decorator có thể là wrapper của framework (đặc biệt khi `submit`); không suy luận việc bắt ngoại lệ ở wrapper sẽ thấy mọi lỗi của task. Lỗi từ `Future` cần được quan sát qua cơ chế phù hợp.

### 13.1. Mẫu kiểm chứng tải

1. Chạy ở tải thấp để đo thời gian xử lý và mức giữ heap khi queue gần như rỗng.
2. Tạo burst ngắn để đo queue đạt đỉnh và thời gian thoát queue.
3. Giữ tải gần ngưỡng năng lực để quan sát p95/p99 queue wait, GC và số worker.
4. Tạo quá tải kéo dài có kiểm soát để xác nhận rejection policy và đường xử lý phía caller.
5. Tăng kích thước payload và thời gian I/O để kiểm chứng ước lượng `B_task` và `μ_effective`.
6. Kiểm thử hủy, shutdown, redeploy và tác vụ có side effect; không để công việc vô ích chiếm chỗ vô hạn.

Mỗi lần chỉ nên đổi một nhóm tham số có quan hệ rõ ràng (ví dụ queue và giới hạn luồng sau khi đã đo), so sánh p99 và lỗi, không chỉ thông lượng trung bình.

## 14. Những sai lầm phổ biến khi quyết định queueCapacity

| Sai lầm | Vì sao sai | Cách sửa |
|---|---|---|
| Đặt hàng đợi rất lớn “cho khỏi từ chối task” | Chỉ dời quá tải sang heap và thời gian chờ | Đặt ngân sách memory/time rồi xử lý rejection |
| Chỉ tính số phần tử mà quên payload bị capture | Một task có thể giữ cả request hoặc mảng lớn | Profile retained heap của task thực tế |
| Dùng `-Xmx` làm toàn bộ ngân sách queue | Heap còn cần GC headroom, cache, task đang chạy | Phân bổ `M_queue` sau các tải khác |
| Dùng Little's law để cam kết p99 | Quan hệ là trung bình ở trạng thái ổn định | Đo p99 queue wait trực tiếp |
| Tăng `maxPoolSize` nhưng queue gần như vô hạn | Pool ưu tiên queue sau core, không tăng thêm worker | Cấu hình queue có giới hạn phù hợp |
| Dùng `queueCapacity=0` nhưng không giới hạn `maxPoolSize` | Có thể tạo nhiều luồng khi tải tăng | Đặt max và chính sách từ chối rõ ràng |
| Không dự liệu việc task bị từ chối | Task quan trọng có thể mất hoặc caller lỗi | Thiết kế backpressure, lỗi, retry/idempotency |
| Dùng `CallerRunsPolicy` mà nghĩ luôn bất đồng bộ | Task có thể chạy ngay trên request thread | Cân nhắc hợp đồng độ trễ và trường hợp shutdown |
| Chỉ đo queue size trung bình | Không thấy burst, p99 và task chờ quá hạn | Đo đỉnh queue, thời gian chờ, rejection, GC |
| Tăng queue khi downstream đã quá tải | Không tạo thêm năng lực phục vụ | Tìm bottleneck, giới hạn đầu vào |
| Dùng queue nội bộ cho công việc phải sống qua restart | Queue trong heap không bền vững | Dùng hệ thống lưu công việc phù hợp |
| Chọn cấu hình một lần rồi bỏ mặc | Tải và kích thước task đổi theo thời gian | Theo dõi, thử tải và hiệu chỉnh có kiểm soát |

## 15. Bài tập thực hành

**Bài 1 — Tính hai ngân sách.** Một pool có ngân sách queue 12 MiB, task giữ khoảng 64 KiB, tốc độ xử lý đo được 50 task/s và thời gian đợi cho phép 0,2 s. Tính `Q_memory`, `Q_time` và ứng viên queueCapacity. Vì sao chỉ chọn theo bộ nhớ sẽ nguy hiểm?

**Đáp án định hướng:** `Q_memory = floor(12×1024/64)=192`; `Q_time=floor(50×0,2)=10`; ứng viên tối đa 10 trong mô hình đơn giản. Phải đo p95/p99 và để dự phòng.

**Bài 2 — Max pool có tăng không?** Pool có `corePoolSize=2`, `maxPoolSize=10`, queue gần như vô hạn, và 100 tác vụ dài được gửi dồn. Giải thích tại sao phần lớn tác vụ có thể xếp hàng và pool không tự nhảy lên 10 worker.

**Đáp án định hướng:** Sau khi có hai worker lõi, `ThreadPoolExecutor` ưu tiên queue; khi queue chưa đầy, không có lý do theo thuật toán để tăng worker trên core.

**Bài 3 — Phân tích p99.** QueueCapacity là 16 và throughput đo được 80 task/s. Có thể khẳng định 99% task không bao giờ chờ hơn 200 ms không? Đưa ra ít nhất ba nguyên nhân khiến kết luận sai.

**Đáp án định hướng:** Không. Thời gian task dao động, worker đang bận, GC, I/O chậm, burst, thay đổi throughput hoặc các queue khác đều làm p99 khác phép ước lượng trung bình.

**Bài 4 — Chạy thí nghiệm Java 17.** Biên dịch `BoundedQueueDemo.java`, thay queue 2 bằng 3 nhưng vẫn gửi bốn task. Dự đoán khi nào rejection xảy ra nếu thêm task thứ năm trong lúc worker còn chờ latch.

**Đáp án định hướng:** Với 1 worker và queue 3, task 2/3/4 được xếp hàng, task thứ năm bị từ chối trong điều kiện worker vẫn chặn.

**Bài 5 — Điều chỉnh cho workload lớn.** Task mới giữ thêm ảnh 2 MiB trong closure. Nếu vẫn cho phép queue 1000 phần tử, rủi ro là gì? Đề xuất một cách giảm retained heap và một cách kiểm soát mất việc khi queue đầy.

**Đáp án định hướng:** Queue có thể giữ cỡ vài GiB dữ liệu từ ảnh trước khi tính overhead; chỉ truyền ID hoặc reference bền vững, giới hạn queue và dùng xử lý từ chối có tín hiệu/đường lưu công việc thích hợp.

**Bài 6 — Kịch bản phụ thuộc tài nguyên.** Task cần một connection DB, nhưng pool DB chỉ cho phép 8 connection trong khi executor có 40 worker. Nếu p99 tăng, nên tăng queueCapacity ngay không? Giải thích điều cần đo trước.

**Đáp án định hướng:** Không tự động. Cần đo thời gian chờ connection, throughput của DB, active workers và Wq; queue lớn chỉ che giấu nút thắt nếu DB đã bão hòa.

## 16. Quy trình quyết định cấu hình trong dự án

Áp dụng sáu bước theo thứ tự:

1. **Xác định hợp đồng:** task có phải hoàn tất nhanh, có được từ chối, có deadline, có bắt buộc thực hiện dù restart hay không.
2. **Đo độ lớn và thời gian:** phân phối retained bytes của một task chờ; phân phối thời gian xử lý trên worker; tốc độ gửi và các đợt burst.
3. **Chia ngân sách:** giới hạn heap cho queue sau khi dành chỗ cho các thành phần khác, và giới hạn queue wait lấy từ SLO.
4. **Tính ứng viên:** `min(Q_memory, Q_time)`, xem xét hệ số an toàn và nhu cầu chịu burst. Nếu cần buffer lớn hơn giới hạn thời gian, phải thay đổi thiết kế/cách tiếp nhận công việc hoặc thỏa thuận SLO, không tự tăng queue.
5. **Cấu hình đồng bộ:** `corePoolSize`, `maxPoolSize`, `queueCapacity` và chính sách rejection phải phù hợp cùng nhau; xem cả downstream.
6. **Kiểm thử và theo dõi:** p95/p99 queue wait, queue depth, rejection, heap/GC, timeouts và thời gian phục hồi sau burst; điều chỉnh theo chứng cứ.

Có thể ghi nhớ quy tắc: **hàng đợi là ngân sách cho công việc đang chờ, không phải nguồn năng lực xử lý.** Chọn dung lượng từ khả năng giữ trong bộ nhớ **và** thời gian chờ chấp nhận được, sau đó kiểm chứng dưới tải thật.

### Tài liệu tham khảo chính thức

- [Spring Framework Javadoc — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Java SE 17 Javadoc — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Java SE 17 Javadoc — Executors](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executors.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-005">Phân biệt giới hạn đồng thời với giới hạn số yêu cầu theo thời gian</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt giới hạn đồng thời với giới hạn số yêu cầu theo thời gian

> **Phạm vi:** Spring Framework – concurrency, phần cấu hình tài nguyên. Bài học giải thích hai kiểu bảo vệ hệ thống thường bị nhầm với nhau: **concurrency limit** (giới hạn số công việc đang diễn ra đồng thời) và **rate limit** (giới hạn số lượt yêu cầu được phép bắt đầu trong một khoảng thời gian). Chúng đo hai đại lượng khác nhau, giải quyết những rủi ro khác nhau và thường phải phối hợp.

## 1. Mục tiêu học tập

Sau bài học, bạn có thể:

1. Phân biệt số yêu cầu đang xử lý (*in-flight/concurrent requests*) với lưu lượng yêu cầu theo thời gian (*requests per second – RPS*, *requests per minute – RPM*).
2. Giải thích tại sao giới hạn 10 tác vụ đồng thời không đồng nghĩa 10 request/giây, và giới hạn 100 request/giây cũng không đồng nghĩa tối đa 100 tác vụ chạy song song.
3. Sử dụng `Semaphore` hoặc bulkhead để giới hạn công việc đồng thời; hiểu các thuật toán fixed window, sliding window và token bucket để giới hạn tốc độ.
4. Liên hệ các giới hạn này với `ThreadPoolTaskExecutor`, HTTP controller, tác vụ `@Async` và các trường hợp nhiều instance Spring.
5. Phân biệt quyết định xếp hàng, từ chối, chờ, HTTP 429 và 503; đặt giới hạn có thể giám sát và kiểm thử được.
6. Viết và chạy ví dụ Java 17 có đầu ra xác định, không dùng `Thread.sleep` làm bằng chứng cho tính đúng đắn.

## 2. Hai giới hạn trả lời hai câu hỏi khác nhau

### 2.1. Giới hạn đồng thời (concurrency limit)

**Concurrency limit** khống chế số lượng công việc đang **giữ quyền sử dụng một tài nguyên** tại một thời điểm.

Ví dụ: một semaphore có **8 permit** cho phép tối đa 8 lời gọi tới dịch vụ xử lý báo cáo được thực thi trong cùng thời điểm trong một JVM. Lời gọi thứ chín sẽ phải đợi hoặc bị từ chối, tùy chính sách của chương trình. Khi một công việc hoàn thành hoặc thất bại, nó **trả lại permit** để công việc khác sử dụng.

Cách ký hiệu:

~~~text
In-flight(t) = số công việc đã vào vùng được bảo vệ
               nhưng chưa rời vùng đó tại thời điểm t

Yêu cầu: In-flight(t) <= C ở mọi thời điểm t
         C = giới hạn số công việc đồng thời
~~~

Đơn vị của `C` là **công việc đang diễn ra**, không phải công việc/giây.

Phạm vi “công việc đang diễn ra” phải được xác định trước: một HTTP request từ lúc vào controller đến lúc response kết thúc, một cuộc gọi downstream, một tác vụ đang chạy trong worker, hay cả tác vụ đang nằm trong queue? Đây là các phạm vi khác nhau; một giới hạn chỉ có ý nghĩa nếu permit được lấy và trả ở đúng ranh giới.

### 2.2. Giới hạn yêu cầu theo thời gian (rate limit)

**Rate limit** khống chế số **lượt được chấp nhận/bắt đầu** trong một khoảng thời gian, hoặc tốc độ cấp quyền trung bình theo một thuật toán.

Ví dụ: một người dùng chỉ được bắt đầu **60 yêu cầu trong 60 giây**. Mỗi yêu cầu được chấp nhận thường tiêu thụ một lượt trong quota; việc yêu cầu kết thúc **không lập tức hoàn trả lượt đó**. Quyền tiếp tục được cấp khi thời gian/window/tokens được làm mới theo thuật toán.

Một mô hình cửa sổ đơn giản:

~~~text
Accepted(user, window_60s) <= 60

Đơn vị: yêu cầu / 60 giây (hoặc requests/second)
~~~

Rate limit có thể được tính theo user, API key, IP, tenant, endpoint, toàn dịch vụ hoặc theo kết hợp nhiều tiêu chí. Một quota toàn dịch vụ và quota từng khách hàng không thể thay thế nhau.

### 2.3. Bảng đối chiếu cốt lõi

| Tiêu chí | Giới hạn đồng thời | Giới hạn theo thời gian |
| --- | --- | --- |
| Câu hỏi | “Bao nhiêu việc đang chạy/chiếm tài nguyên ngay lúc này?” | “Bao nhiêu lượt được phép đi vào trong khoảng thời gian?” |
| Đại lượng | In-flight count, permits đang giữ | Requests/giây, requests/phút, token/period |
| Đơn vị | Job/request/call **đồng thời** | Job/request/call **mỗi đơn vị thời gian** |
| Khi lấy quyền | Trước khi bắt đầu vùng tài nguyên cần bảo vệ | Khi request được phép đi vào theo quota |
| Khi trả/khôi phục quyền | Khi công việc hoàn thành, lỗi hoặc bị hủy; thường trong `finally` | Theo thời gian hoặc chính sách refill; hoàn thành task không tự trả token |
| Cơ chế quen thuộc | `Semaphore`, semaphore bulkhead, giới hạn active worker | Fixed/sliding window, token bucket, rate limiter |
| Rủi ro giảm thiểu | Cạn connection/worker/memory do quá nhiều việc kéo dài | Burst, gọi API quá dày, vượt quota đối tác, lạm dụng theo user |
| Có bảo đảm giới hạn còn lại? | **Không bảo đảm RPS** | **Không bảo đảm số in-flight** |
| Phạm vi triển khai | Một JVM, service, pool hoặc toàn cụm nếu có bộ điều phối chung | Một process, API key, gateway hoặc toàn cụm tùy nơi lưu quota |

**Nhớ ngắn:** concurrency limit là **sức chứa tại một thời điểm**; rate limit là **số lượt đi qua theo thời gian**.

## 3. Ví dụ trực giác: cùng giới hạn nhưng throughput rất khác

Giả sử một dịch vụ cho tối đa **10 request đồng thời**.

- Nếu mỗi request mất trung bình **0,05 giây** và luôn có việc chờ, trong một mô hình lý tưởng không có overhead và nút thắt khác, công suất xấp xỉ `10 / 0,05 = 200 request/giây`.
- Nếu mỗi request mất trung bình **5 giây**, cùng giới hạn 10 request đồng thời chỉ cho công suất xấp xỉ `10 / 5 = 2 request/giây`.

Vì vậy, nói “pool có 10 thread nên API giới hạn 10 RPS” là sai. Số thread giới hạn mức song song của công việc chạy trên pool, nhưng số task hoàn thành mỗi giây còn phụ thuộc độ dài tác vụ và giới hạn tài nguyên khác. Nếu có queue, task đã được nhận nhưng chưa chạy càng không đồng nhất với active thread.

Chiều ngược lại, giả sử cấp **100 request/giây** và mỗi request mất trung bình **10 giây**. Nếu lưu lượng ổn định đủ lâu và không có giới hạn khác, số request đồng thời trung bình có thể tiến đến khoảng `100 × 10 = 1.000`, dù rate limit vẫn hoàn toàn tuân thủ. Dịch vụ có 50 kết nối downstream sẽ sớm quá tải nếu không có một lớp admission/concurrency control thích hợp.

### Một hình dung nhanh

~~~text
RATE LIMIT:          100 lượt được phép bắt đầu trong 1 giây
                       | | | | | | | | ...
                       v v v v v v v v
                         hàng đợi/tác vụ
                              |
CONCURRENCY LIMIT:        [ 8 slot đang giữ ]
                          [request 1 ... 8]
                              |
                          hoàn thành -> trả slot
~~~

Hai lớp có thể nằm ở những vị trí khác nhau: rate limit tại API gateway, concurrency limit sát downstream/database. Trật tự và vị trí của chúng quyết định tài nguyên nào được bảo vệ trước tiên.

## 4. Mối liên hệ toán học: Little's Law

Trong hệ thống **ổn định**, xét trung bình dài hạn với cách định nghĩa phạm vi nhất quán, **Little's Law**:

\[
L = \lambda W
\]

Trong đó:

- `L`: số công việc trung bình **đang nằm trong hệ thống đang xét**.
- `λ`: tốc độ trung bình các công việc đi qua hệ thống, đơn vị công việc/giây.
- `W`: thời gian trung bình một công việc ở trong hệ thống, đơn vị giây.

Nếu đang đo riêng đoạn xử lý trong service, `W` là thời gian giữ tài nguyên tương ứng; nếu đo cả queue và xử lý, `W` phải bao gồm **cả thời gian chờ queue**. Không được lấy throughput ở một phạm vi rồi nhân với latency ở phạm vi khác.

Ví dụ khi hệ thống ổn định:

| Throughput `λ` | Thời gian trung bình `W` | In-flight trung bình `L` |
| ---: | ---: | ---: |
| 20 req/s | 0,1 s | 2 |
| 20 req/s | 1 s | 20 |
| 20 req/s | 5 s | 100 |
| 100 req/s | 0,2 s | 20 |

Điều đáng chú ý: **cùng RPS nhưng latency tăng khiến in-flight tăng**. Khi downstream chậm lại, chỉ áp rate limit thường chưa đủ để bảo vệ connection pool hoặc memory.

Little's Law là quan hệ **trung bình**, không chứng minh số in-flight tối đa và không thay thế kiểm tra p95/p99, burst, queue hay timeout. Không thể tự suy một hard concurrency cap chỉ từ một giá trị RPS.

## 5. Concurrency limiter hoạt động ra sao?

Một mô hình điển hình dùng `Semaphore(C)`:

~~~text
Request vào
    |
    v
Thử lấy permit
    |
    +-- Có permit: active++ -> làm việc -> finally release -> active--
    |
    +-- Hết permit:
           +-- Từ chối ngay
           +-- Hoặc chờ có giới hạn (timeout, queue)
~~~

Quan trọng: permit chỉ được trả nếu đã lấy thành công, và phải trả **đúng một lần** ở tất cả đường hoàn thành, exception và cancel.

Ví dụ Java:

~~~java
Semaphore slots = new Semaphore(8);

if (!slots.tryAcquire()) {
    throw new IllegalStateException("capacity exhausted");
}

try {
    performBlockingWork(); // Có tối đa 8 invocation trong vùng này
} finally {
    slots.release();
}
~~~

**Đoạn minh họa:** cần có `performBlockingWork()` thật trong ứng dụng. `tryAcquire()` không chờ; vì vậy request thứ chín bị từ chối ngay. Nếu dùng `tryAcquire(timeout, TimeUnit.MILLISECONDS)`, caller có thể chờ và giữ request thread; phải chọn timeout hợp lý, xử lý `InterruptedException` và đánh giá tác động tài nguyên.

### Giới hạn “đang chạy” khác giới hạn “đã nhận vào queue”

Khi lấy permit:

1. **Ngay trước khi submit vào thread pool:** permit có thể bị giữ trong cả thời gian **đang xếp hàng**, và chỉ trả sau khi task kết thúc. Đây là giới hạn tổng số job đã nhận nhưng chưa hoàn tất, có ích để chặn backlog.
2. **Bên trong worker ngay lúc bắt đầu xử lý:** permit chỉ đếm job **đang thực thi**, còn queue có thể chứa nhiều task hơn, cần giới hạn queue riêng.
3. **Trong HTTP controller đồng bộ:** permit bao quanh lời gọi nghiệp vụ, đếm số invocation đang ở vùng này; không nhất thiết trùng với tổng request đang mở socket.

Không có cách chọn nào mặc định đúng cho mọi hệ thống. Hãy đặt tên chỉ số đúng phạm vi, chẳng hạn `downstream_in_flight`, `executor_active` và `executor_queued`.

## 6. Rate limiter hoạt động ra sao?

Rate limiter quyết định có cấp **lượt bắt đầu** tại thời điểm yêu cầu đến hay không, dựa vào chính sách thời gian.

### 6.1. Fixed window

Cho tối đa 60 request trong mỗi phút lịch:

~~~text
Phút A:  [60 lần được nhận]  [lần 61: từ chối]
Phút B:  [đếm lại từ 0]
~~~

Ưu điểm là dễ hiểu và triển khai. Nhược điểm: ở **ranh giới** hai cửa sổ, một client có thể gửi 60 request sát cuối phút A và 60 request sát đầu phút B, tạo burst 120 request trong khoảng rất ngắn. Vì thế “60/phút fixed window” không tương đương “mọi khoảng trượt dài 60 giây chỉ được 60”.

### 6.2. Sliding window

Đếm các request trong **khoảng thời gian trượt** (hoặc dùng xấp xỉ/bucket). Cách này kiểm soát burst sát biên tốt hơn, nhưng trạng thái và độ phức tạp có thể cao hơn fixed window. Phải xác định chính xác window tính theo thời điểm nào, dữ liệu lưu ở đâu, và cách cleanup.

### 6.3. Token bucket

Bucket chứa tối đa `B` token; hệ thống bổ sung token theo tốc độ `r` token/giây. Mỗi request tiêu thụ token (hoặc số token tỷ lệ chi phí). Nếu không đủ token, request chờ hoặc bị từ chối.

- `r` xác định tốc độ nạp trung bình dài hạn.
- `B` xác định burst tối đa có thể được hấp thụ nếu bucket đầy.
- Token đã dùng không được “trả lại” chỉ vì request kết thúc; chúng được bổ sung theo thời gian.

Ví dụ `r = 10 token/s` và `B = 20` có thể chấp nhận burst 20 request ngay khi bucket đầy, rồi tiếp tục cấp theo nhịp bổ sung. Đó không phải bảo đảm tối đa 10 request trong **mọi** khoảng 1 giây.

### 6.4. Leaky bucket và hàng đợi có kiểm soát

Một bộ điều tiết có thể cho request đi ra với nhịp tương đối đều, đặt phần dư vào queue hữu hạn. Nếu queue đầy thì từ chối. Giải pháp này giảm burst đến downstream nhưng đổi lại độ trễ chờ. **Queue phải có capacity và timeout**: một hàng đợi không giới hạn không biến hệ thống thành vô hạn tài nguyên.

### 6.5. Quyền theo thời gian không giới hạn thời lượng xử lý

Giới hạn “10 requests mỗi giây” vẫn có thể tạo ra 100 invocation đồng thời nếu mỗi invocation cần 10 giây. Vì vậy, khi rủi ro là cạn số connection hoặc tác vụ chậm chiếm tài nguyên, cần thêm concurrency limiter/bulkhead.

## 7. Java 17 chạy được: mô phỏng hai giới hạn độc lập

Ví dụ sau sử dụng **cùng một chương trình** để minh họa:

- `Semaphore(2)` chỉ cho hai tác vụ giữ permit đồng thời.
- Bộ đếm fixed window chỉ cho ba lượt được phép trong mỗi cửa sổ 1.000 ms.
- `CountDownLatch` khóa các mốc của thí nghiệm để output ổn định. Các mốc thời gian của fixed window được **truyền tường minh** cho mục đích kiểm thử; bản demo không khẳng định đây là rate limiter production hoàn chỉnh.

Lưu vào tệp **`LimitsDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class LimitsDemo {
    static final class FixedWindow {
        private final int quota;
        private final long periodMillis;
        private long currentWindow = Long.MIN_VALUE;
        private int used;

        FixedWindow(int quota, long periodMillis) {
            if (quota <= 0 || periodMillis <= 0) {
                throw new IllegalArgumentException("limits must be positive");
            }
            this.quota = quota;
            this.periodMillis = periodMillis;
        }

        synchronized boolean allowAt(long millis) {
            long window = Math.floorDiv(millis, periodMillis);
            if (window != currentWindow) {
                currentWindow = window;
                used = 0;
            }
            if (used >= quota) {
                return false;
            }
            used++;
            return true;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Semaphore slots = new Semaphore(2);
        CountDownLatch bothInside = new CountDownLatch(2);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        Runnable slowJob = () -> {
            if (!slots.tryAcquire()) {
                throw new IllegalStateException("unexpected rejection");
            }
            try {
                int now = inFlight.incrementAndGet();
                peak.accumulateAndGet(now, Math::max);
                bothInside.countDown();

                try {
                    releaseWorkers.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                inFlight.decrementAndGet();
                slots.release();
            }
        };

        Thread first = new Thread(slowJob);
        Thread second = new Thread(slowJob);
        first.start();
        second.start();

        bothInside.await(); // Chắc chắn cả hai đang giữ permit.
        boolean thirdAccepted = slots.tryAcquire();
        if (thirdAccepted) {
            slots.release();
        }

        System.out.println("inFlight = " + inFlight.get());
        System.out.println("thirdAccepted = " + thirdAccepted);

        releaseWorkers.countDown();
        first.join();
        second.join();
        System.out.println("peakInFlight = " + peak.get());
        System.out.println("permitsAfter = " + slots.availablePermits());

        FixedWindow rate = new FixedWindow(3, 1_000);
        int accepted = 0;
        for (int i = 0; i < 5; i++) {
            if (rate.allowAt(0)) {
                accepted++;
            }
        }
        System.out.println("t=0ms accepted = " + accepted);
        System.out.println("t=999ms accepted = " + rate.allowAt(999));
        System.out.println("t=1000ms accepted = " + rate.allowAt(1_000));

        // Hai cửa sổ liền kề đều có quota riêng.
        FixedWindow boundary = new FixedWindow(3, 1_000);
        int boundaryAccepted = 0;
        for (int i = 0; i < 3; i++) {
            if (boundary.allowAt(999)) boundaryAccepted++;
        }
        for (int i = 0; i < 3; i++) {
            if (boundary.allowAt(1_000)) boundaryAccepted++;
        }
        System.out.println("boundaryAccepted = " + boundaryAccepted);
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac LimitsDemo.java
java LimitsDemo
~~~

**Stdout xác định:**

~~~text
inFlight = 2
thirdAccepted = false
peakInFlight = 2
permitsAfter = 2
t=0ms accepted = 3
t=999ms accepted = false
t=1000ms accepted = true
boundaryAccepted = 6
~~~

**Vì sao kết quả xác định?**

1. Hai worker nhận đúng hai permit và gọi `bothInside.countDown()` sau khi tăng `inFlight`; main chỉ tiếp tục khi cả hai đã vào vùng được bảo vệ.
2. Cả hai worker đợi ở `releaseWorkers.await()`, nên lần `tryAcquire()` của main chắc chắn thất bại và in `false`.
3. Main mở latch, dùng `join()` trước khi kiểm tra peak/permit; tránh race trong phép quan sát kết quả.
4. Bộ fixed-window được gọi với mốc thời gian giả lập `0`, `999`, `1000`, không cần dựa vào đồng hồ thực hoặc `sleep`.
5. `boundaryAccepted = 6` cho thấy ba lượt ở cuối một window và ba lượt ở đầu window tiếp theo đều được nhận, dù khoảng cách giữa hai mốc chỉ **1 ms**.

**Giới hạn của ví dụ:** `FixedWindow.allowAt` đồng bộ trong một đối tượng JVM và cho phép người gọi đưa vào thời gian. Trong hệ thống thật phải dùng nguồn thời gian đáng tin, xử lý lệch đồng hồ, làm mới trạng thái, khóa atomic và nhiều replica; nên dùng thư viện/gateway phù hợp thay vì copy class này sang production.

## 8. Áp dụng giới hạn đồng thời trong Spring MVC

Với một HTTP endpoint **đồng bộ**, có thể khai báo semaphore thành bean và bao lời gọi nghiệp vụ bằng permit.

### 8.1. Bean cấu hình

Trong dự án Spring Web có cấu hình component scan tương ứng, tệp **`CapacityConfiguration.java`**:

~~~java
import java.util.concurrent.Semaphore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CapacityConfiguration {
    @Bean("reportSlots")
    public Semaphore reportSlots() {
        return new Semaphore(8);
    }
}
~~~

Đây là cấu hình Spring: container quản lý một instance semaphore mặc định trên **mỗi application context**, phục vụ những consumer inject cùng bean. Nó không tự tạo quota toàn cụm khi triển khai nhiều JVM.

### 8.2. Service bảo vệ đoạn xử lý

Tệp **`ReportService.java`**:

~~~java
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    private final Semaphore reportSlots;

    public ReportService(@Qualifier("reportSlots") Semaphore reportSlots) {
        this.reportSlots = reportSlots;
    }

    public String generate(long id) {
        if (!reportSlots.tryAcquire()) {
            throw new CapacityExceededException();
        }
        try {
            // Trong ứng dụng thật, đây là lời gọi nghiệp vụ đồng bộ.
            return "report-" + id;
        } finally {
            reportSlots.release();
        }
    }

    public static final class CapacityExceededException
            extends RuntimeException {
    }
}
~~~

Tệp **`ReportController.java`**:

~~~java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {
    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/reports/{id}")
    public String getReport(@PathVariable long id) {
        return reports.generate(id);
    }
}
~~~

Các class này là **thành phần của dự án Spring MVC**, cần dependency Web và ứng dụng Spring đã khởi động; chúng không phải chương trình Java SE có `main`. Các class đặt trong cùng package (hoặc bổ sung `import` phù hợp) để tham chiếu `ReportService`.

### 8.3. Phản hồi quá tải

Để minh họa lỗi tài nguyên tạm thời, tệp **`CapacityAdvice.java`**:

~~~java
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CapacityAdvice {
    @ExceptionHandler(ReportService.CapacityExceededException.class)
    public ResponseEntity<String> busy() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Server capacity exhausted");
    }
}
~~~

Ở đây dùng **503 Service Unavailable** để diễn đạt quá tải tạm thời của server. Khi quota theo user/khách hàng vượt ngưỡng **số request trong thời gian**, **429 Too Many Requests** thường có ý nghĩa rõ hơn. Đây là cách ánh xạ theo mục đích; chuẩn HTTP không bắt buộc mọi sự kiện thiếu permit phải trả đúng một mã duy nhất.

Ví dụ trả chuỗi ngay chỉ cho thấy cấu trúc lấy/trả permit; nó không tái hiện thời gian xử lý dài. Trong dự án thật, vùng `try/finally` phải bao trọn lời gọi downstream, truy xuất hoặc tính toán cần giới hạn. Đừng trả permit ngay sau khi chỉ **submit** một công việc bất đồng bộ nếu công việc đó còn đang chạy.

### 8.4. Khi service trả CompletableFuture hoặc Mono

Với lời gọi bất đồng bộ, ranh giới tài nguyên thường kéo dài tới khi công việc thực sự **hoàn thành, thất bại hoặc bị hủy**, thay vì chỉ khi phương thức schedule trả về.

Ví dụ mẫu `CompletableFuture` (giả định `asyncClient.fetch(id)` trả `CompletableFuture<String>` và `slots` là semaphore):

~~~java
if (!slots.tryAcquire()) {
    return CompletableFuture.failedFuture(
            new IllegalStateException("busy"));
}
try {
    return asyncClient.fetch(id)
            .whenComplete((result, failure) -> slots.release());
} catch (RuntimeException ex) {
    slots.release();
    throw ex;
}
~~~

**Đoạn minh họa không độc lập**, cần context khai báo `slots` và `asyncClient`. Permit được trả bởi completion callback hoặc khi việc tạo future thất bại đồng bộ. Cần kiểm soát thêm các đường cancel/timeout của client thật. Với Reactor `Mono`/`Flux`, thời điểm subscription, terminal signal và cancellation có ý nghĩa riêng; một cấu trúc như `doFinally(...)` thường phù hợp để dọn tài nguyên, nhưng phải thiết kế để permit được lấy tại đúng thời điểm subscribe và được trả đúng một lần. Không áp dụng máy móc `try/finally` bao quanh lúc tạo Publisher rồi tưởng rằng tác vụ reactive đã xong.

## 9. ThreadPoolTaskExecutor có phải rate limiter không?

**Không.** `ThreadPoolTaskExecutor` quản lý **số worker và hàng đợi** trong một JVM:

~~~java
@Bean("reportExecutor")
ThreadPoolTaskExecutor reportExecutor() {
    ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
    e.setCorePoolSize(4);
    e.setMaxPoolSize(4);
    e.setQueueCapacity(20);
    e.setThreadNamePrefix("reports-");
    return e;
}
~~~

Đây là **snippet cấu hình Spring**, cần các import `@Bean`, `ThreadPoolTaskExecutor` và lớp `@Configuration`. Container quản lý khởi tạo và shutdown. Với ví dụ cấu hình này:

- Tối đa **4 worker** của pool chạy task cùng lúc theo cách phân bổ thông thường.
- Có thể có tối đa **20 task đang chờ trong queue** trước khi xảy ra từ chối, nếu worker đang bận và task không kết thúc kịp.
- Những task rất ngắn có thể xử lý hàng trăm hoặc hàng nghìn lần mỗi giây; `corePoolSize=4` không có nghĩa 4 RPS.
- Task trong queue tiêu thụ RAM và kéo dài thời gian chờ, dù không tính vào `getActiveCount()`.
- Handler từ chối như `CallerRunsPolicy` có thể cho task chạy **trên thread nộp việc**, nên ngay cả giả định “chỉ 4 execution đồng thời trên toàn JVM” cũng không đúng nếu caller-runs cho phép thực thi ngoài pool. `AbortPolicy` từ chối minh bạch hơn khi không thể nhận thêm task.

**Giới hạn pool thread** không đồng nhất với giới hạn tổng HTTP in-flight, số connection DB hoặc số invocation đồng thời tới downstream. Khi cần bảo vệ một tài nguyên cụ thể, đặt semaphore/bulkhead **sát ranh giới gọi tài nguyên đó**.

Một executor với queue rất dài đôi khi che giấu sự quá tải bằng cách tăng latency. Hãy đo queue depth, task wait time và rejected count. Không thiết kế một “rate limiter” chỉ bằng `Thread.sleep(...)` trong worker: sleep vẫn giữ worker và có thể tạo backlog lớn.

## 10. Hai lớp bảo vệ phối hợp như thế nào?

Giả sử API tra cứu tài liệu có hai ràng buộc:

- Mỗi API key: tối đa **60 request/phút**, để kiểm soát mức sử dụng công bằng.
- Mỗi instance service: tối đa **8 lời gọi tới hệ thống tìm kiếm** cùng lúc, vì downstream có connection/quota hữu hạn.

Luồng đề xuất:

~~~text
HTTP / API Gateway
    |
    +-- Xác thực, xác định API key / tenant
    |
    v
Rate limiter: 60 yêu cầu/phút/key
    |
    +-- Vượt quota -> 429, có thể kèm Retry-After
    |
    v
Spring controller / service
    |
    v
Semaphore / Bulkhead: tối đa 8 lời gọi downstream/instance
    |
    +-- Hết slot -> chờ ngắn có kiểm soát hoặc 503
    |
    v
Call downstream -> trả permit -> response
~~~

Có thể đảo thứ tự ở những trường hợp khác, nhưng phải cân nhắc chi phí: nếu làm I/O tốn kém **trước** khi rate limit thì người dùng vượt quota vẫn chiếm tài nguyên; nếu giữ semaphore trong lúc xếp hàng chờ rate token thì permit bị tiêu hao do chờ, không phải do xử lý nghiệp vụ.

| Câu hỏi cấu hình | Trả lời cần có |
| --- | --- |
| Quota theo ai? | Từng user, API key, tenant hay toàn hệ thống? |
| Đếm sự kiện nào? | Mọi request đến, request hợp lệ, request được chấp nhận hay lời gọi downstream? |
| Giới hạn đồng thời đoạn nào? | Controller synchronous, HTTP client, DB connection hay một task worker? |
| Khi quá ngưỡng? | Từ chối ngay, chờ timeout, xếp hàng hữu hạn hay retry ở tầng trên? |
| Phạm vi chia sẻ? | Mỗi process, mỗi pod, mỗi region hay toàn cụm? |
| Cần giữ sau restart? | Quota ngắn hạn ở local memory hay trạng thái bền vững? |
| Có burst không? | Burst tối đa `B`, period và thuật toán window/token bucket? |

Một hệ thống có thể cần cả **rate limiter ở đầu vào** lẫn **concurrency limiter ở các tài nguyên trọng yếu**; hai lớp đo các sự kiện riêng và có thể có chỉ số/tên policy riêng.

## 11. Thư viện phù hợp: Bulkhead và RateLimiter

Trong ứng dụng Spring, có thể dùng thư viện **Resilience4j** thay cho tự viết các bộ giới hạn production:

- **Semaphore Bulkhead**: giới hạn số lời gọi đồng thời tới một dependency (`maxConcurrentCalls`); permit được trả khi phạm vi gọi kết thúc theo hợp đồng thư viện.
- **ThreadPool Bulkhead**: dùng pool riêng với queue hữu hạn để cách ly workload; cấu hình core/max pool và queue.
- **RateLimiter**: cấp quyền theo `limitForPeriod` và `limitRefreshPeriod`, với `timeoutDuration` quyết định thời gian chờ để được cấp quyền.

Một cấu hình **minh họa** trong `application.yml` của dự án đã cài Resilience4j Spring Boot starter tương thích:

~~~yaml
resilience4j:
  ratelimiter:
    instances:
      partnerApi:
        limitForPeriod: 30
        limitRefreshPeriod: 1s
        timeoutDuration: 0
  bulkhead:
    instances:
      partnerApi:
        maxConcurrentCalls: 8
        maxWaitDuration: 0
~~~

Ý nghĩa của cấu hình: RateLimiter cấp tối đa 30 permit mỗi chu kỳ 1 giây theo thuật toán của thư viện, không chờ thêm khi hết quota; Bulkhead cho tối đa 8 lời gọi chạy đồng thời và không chờ khi đầy. Đây là **hai policy độc lập**; cấu hình này chỉ có hiệu lực nếu ứng dụng đã thêm dependency/aspect/decorator và thực sự áp chúng cho lời gọi mong muốn.

Ví dụ annotate method trong ứng dụng có AOP Resilience4j được bật:

~~~java
@RateLimiter(name = "partnerApi")
@Bulkhead(name = "partnerApi")
public String requestPartner(String id) {
    return partnerClient.fetch(id);
}
~~~

Đây là **method snippet**, giả định có `partnerClient` và các import `io.github.resilience4j.ratelimiter.annotation.RateLimiter`, `io.github.resilience4j.bulkhead.annotation.Bulkhead`. Khi dùng AOP, cần hiểu thứ tự aspect, self-invocation, phương thức async/reactive và cách mapper xử lý exception (chẳng hạn `RequestNotPermitted` hoặc `BulkheadFullException`). Không mặc nhiên suy ra các annotation tự hoạt động nếu thiếu starter/config hoặc lời gọi không đi qua proxy.

**Đặc biệt:** Resilience4j RateLimiter giới hạn theo permission/cycle; nó không tự tạo quota **tập trung giữa nhiều pod** nếu mỗi pod có limiter local. Với quota global, lựa chọn API gateway hoặc backend trạng thái dùng chung sẽ thích hợp hơn.

## 12. Nhiều instance Spring: cộng gộp giới hạn ra sao?

Giả sử triển khai **3 pod**, mỗi pod có:

~~~text
Semaphore(8)
RateLimiter(60 request/phút)
~~~

Nếu mỗi pod giữ trạng thái độc lập và load balancer phân phối đều, mức thực tế toàn cụm **có thể** lên tới khoảng **24 lời gọi đồng thời** và **180 lượt/phút** trong mô hình lý tưởng. Không được quảng bá “8 concurrent toàn hệ thống” hoặc “60/phút toàn hệ thống” khi implementation chỉ bảo vệ từng pod.

Mô hình tổng quát:

~~~text
N pod độc lập, mỗi pod có C slot  ->  tối đa khoảng N*C slot
N pod độc lập, mỗi pod quota R   ->  có thể nhận tổng gần N*R/period
~~~

Thực tế phụ thuộc cách phân phối request, churn/rolling deployment, replica scale và thuật toán; không phải cam kết tổng cố định.

Muốn giới hạn toàn cụm:

- **Rate limit global:** áp tại API gateway hoặc trạng thái chia sẻ như Redis với **thao tác cấp quyền atomic**. Key phải biểu diễn đúng phạm vi user/tenant/API; đặt TTL/expiry đúng và có chính sách khi store lỗi.
- **Concurrency limit global:** cần cơ chế điều phối số lease/slot trên toàn cụm, timeout/heartbeat và giải phóng khi node chết. Đây là vấn đề khó hơn chỉ dùng local `Semaphore`; có thể giới hạn sát tài nguyên downstream thay vì dùng lock phân tán nếu kiến trúc cho phép.
- **Kết hợp:** rate global cho fairness/quota, concurrency local để bảo vệ tài nguyên của mỗi pod, và có thể thêm cap ở downstream.
- **Clock:** đối với điều tiết thời gian trong một tiến trình, dùng nguồn monotonic để tính elapsed time khi phù hợp; với limiter tập trung, nên dựa trên đồng hồ/logic phía store để hạn chế lệch giờ giữa replica.

Nếu hệ thống autoscale, quota local thay đổi tổng sức chứa khi tăng/giảm số pod. Hãy ghi rõ policy là **per instance** hay **cluster-wide** và kiểm thử khi scale.

## 13. HTTP 429 và 503, Retry-After và backpressure

### 13.1. Khi nào dùng 429?

**HTTP 429 Too Many Requests** theo RFC 6585 biểu thị client gửi quá nhiều request trong một khoảng thời gian. Nó phù hợp khi một user/API key vượt quota rate limit. Server có thể gửi `Retry-After` để gợi ý thời điểm thử lại khi biết chính sách hạn mức.

~~~http
HTTP/1.1 429 Too Many Requests
Retry-After: 30
Content-Type: text/plain

Rate limit exceeded
~~~

`Retry-After: 30` biểu thị gợi ý chờ **30 giây**, không phải số permit hoặc mức concurrency. Đừng tự đặt thời gian chờ nếu thuật toán không thể xác định thời điểm có quyền tiếp theo.

### 13.2. Khi nào dùng 503?

**HTTP 503 Service Unavailable** theo RFC 9110 thường dùng cho dịch vụ tạm thời không xử lý nổi vì quá tải hoặc bảo trì. Nó có thể phù hợp khi global capacity/bulkhead đã đầy. Có thể kèm `Retry-After` khi có cơ sở dự đoán khoảng chờ.

~~~http
HTTP/1.1 503 Service Unavailable
Content-Type: text/plain

Service temporarily busy
~~~

Mã lỗi cụ thể còn phụ thuộc API contract: cùng một semaphore có thể thực hiện **quota theo khách hàng** hoặc **bảo vệ sức chứa server**. Phải phân tích nguyên nhân và hợp đồng với client, không biến các mã 429/503 thành một luật cứng cho mọi trường hợp.

### 13.3. “Chờ” là quyết định về tài nguyên

Khi vượt ngưỡng có thể:

1. **Fail fast:** từ chối ngay, giữ latency nhỏ và tránh tồn đọng. Cần client retry có backoff + jitter khi hợp lệ.
2. **Wait có timeout:** cho một khoảng chờ ngắn, nhưng request thread/connection cũng chiếm tài nguyên trong lúc đợi.
3. **Queue hữu hạn:** đổi burst thành backlog; cần capacity, deadline, metrics và chính sách hủy.
4. **Persist rồi xử lý nền:** trả job ID và dùng queue/job store bền vững nếu nghiệp vụ cho phép bất đồng bộ.

Chờ vô hạn, queue vô hạn và retry không giới hạn có thể biến một sự cố nhỏ thành hàng loạt request tồn đọng. Timeout phải được thiết kế từ deadline tổng của request, không đặt riêng lẻ từng lớp mà tổng thời gian vượt SLA.

## 14. Chọn ngưỡng đúng: đo trước rồi hiệu chỉnh

Một quy trình thực tế:

1. **Xác định nút thắt:** database pool 20 connections, downstream chỉ chịu 8 request đồng thời, CPU, memory, hoặc quota API đối tác 100 request/phút.
2. **Tách hai yêu cầu:** cap concurrency để bảo vệ số slot hữu hạn; rate cap để kiểm soát tốc độ/chi phí/khách hàng.
3. **Đo workload:** thời gian trung bình và p95/p99, số active, queue wait, throughput, tỷ lệ lỗi, timeout, tải CPU, saturation downstream.
4. **Tính sơ bộ bằng Little's Law:** nếu throughput mục tiêu 40 req/s và thời gian giữ tài nguyên trung bình 0,2 s, cần khoảng 8 in-flight *trung bình*; cộng headroom hợp lý dựa trên burst/tail latency, không coi 8 là hard-cap tối ưu chắc chắn.
5. **Chọn thuật toán rate:** cần burst 10? dùng token bucket có capacity thích hợp; cần quota chính xác mỗi 24 giờ? dùng cơ chế đếm/window phù hợp.
6. **Giới hạn queue và wait:** có deadline, mô tả phản hồi khi không còn capacity.
7. **Áp policy đúng scope:** per-user, per-instance hoặc toàn cụm; có kế hoạch thay đổi khi autoscale.
8. **Load test và chaos test:** vừa tải đều vừa burst, downstream chậm, timeout/cancel, nhiều pod, rolling deployment và store rate limit lỗi.

Ví dụ chọn ngưỡng:

| Bài toán | Giới hạn phù hợp hơn | Lý do |
| --- | --- | --- |
| DB chỉ có 20 kết nối, query chậm | Concurrency cap nhỏ hơn hoặc tương ứng tài nguyên thực | Ngăn quá nhiều truy vấn cùng chiếm connection |
| API đối tác cho 300 calls/phút | Rate cap có state chính xác theo scope của quota | Tuân thủ hạn mức gọi theo thời gian |
| Cần tránh một tenant chiếm hết API | Rate quota theo tenant + concurrency cap tùy nhu cầu | Cân bằng lượt truy cập và tài nguyên đang dùng |
| Hàng nghìn tác vụ nhanh trong vài giây | Rate bucket có burst được định nghĩa | Điều tiết peak dù thời gian chạy rất ngắn |
| Một số task chạy treo nhiều phút | Concurrency cap + timeout/cancel | Rate cap đơn lẻ không thu hồi tài nguyên đang bị giữ |
| Process có nhiều executor | Giới hạn theo dependency, không chỉ worker count | Nhiều pool có thể cùng gọi một dependency |

## 15. Metrics và kiểm thử hai lớp tách biệt

### Metrics cho concurrency

- `in_flight`: gauge số permit đang giữ tại một thời điểm, gắn nhãn phạm vi tài nguyên.
- `available_permits`, số lần `tryAcquire` thất bại, thời gian đợi permit, timeout.
- Thread pool: `activeCount`, `poolSize`, `queueSize`, số lần reject.
- Thời gian giữ slot, số task bị hủy và việc release permit đúng một lần.

### Metrics cho rate

- Accepted/denied requests theo khoảng thời gian, API key/tenant (kiểm soát cardinality).
- Token còn lại hoặc quota còn lại, refresh cycles, số lượt chờ token.
- Tỷ lệ HTTP 429, tốc độ gửi thực tế, burst và sai khác giữa replica.
- Khi một policy đổi ngưỡng, theo dõi hiệu ứng với latency và 5xx, không chỉ tỷ lệ 429.

### Các bài test cần có

**Test concurrency:** dùng latch cho một nhóm tác vụ giữ slot và thử thêm task. Assert số đang giữ **không bao giờ vượt C**, sau đó release và xác nhận permit được trả kể cả khi exception.

**Test rate:** dùng **đồng hồ giả lập hoặc clock abstraction** để kiểm tra chính xác cửa sổ, refill, ranh giới hai window và các yêu cầu cạnh tranh cùng lúc. Tránh dùng `Thread.sleep(1000)` làm điều kiện pass.

**Test kết hợp:** nộp nhiều tác vụ chậm trong khi vẫn ở dưới quota RPS. Rate limiter có thể vẫn cho đi qua, nhưng concurrency limiter phải bảo vệ downstream. Ngược lại, nộp nhiều tác vụ cực nhanh: concurrency có thể luôn thấp, nhưng rate limiter phải chặn burst vượt quota.

**Test phân tán:** khởi động nhiều instance cùng dùng key quota và kiểm tra tổng số lượt nhận; kiểm tra scale-up/rolling restart, timeout của store và request đồng thời ở ranh giới thời gian.

Khi kiểm tra kết quả, hãy phân biệt `allowed`, `queued`, `started`, `completed`, `rejected`; năm trạng thái này không phải một chỉ số.

## 16. Những sai lầm phổ biến

1. **“10 worker = 10 RPS.”** Sai: worker hạn chế việc chạy đồng thời, RPS còn phụ thuộc thời gian xử lý. Với task ngắn, RPS có thể lớn hơn nhiều.
2. **“100 RPS = tối đa 100 request đang chạy.”** Sai: request kéo dài nhiều giây có thể tạo số in-flight lớn hơn nhiều.
3. **“ThreadPoolTaskExecutor đã là rate limiter.”** Sai: pool và queue không tự cấp quota theo thời gian.
4. **“Semaphore.release() khi submit Future là được.”** Sai nếu công việc thực sự còn chạy; release phải gắn với completion/cancel/exception đúng nghĩa.
5. **“Token được trả ngay khi response hoàn thành.”** Sai với rate limiter thời gian; quota phục hồi theo policy, không theo thời lượng request.
6. **“Fixed window 60/phút bảo đảm mọi 60 giây trượt chỉ 60.”** Sai ở ranh giới cửa sổ.
7. **“Giới hạn ở từng pod chính là global limit.”** Sai nếu các pod không chia sẻ cùng state/coordinator.
8. **“Queue lớn giúp hệ thống khỏe hơn mà không có giá.”** Sai: tăng thời gian đợi và RAM, có thể che giấu overload tới lúc deadline hết.
9. **“Cứ vượt semaphore là HTTP 429.”** Không nhất thiết; khi server hết capacity tạm thời, 503 có thể mô tả nguyên nhân tốt hơn.
10. **“Chỉ cần tăng concurrency limit để tăng throughput.”** Sai khi downstream/database/CPU đã bão hòa; tăng song song có thể làm latency và tỷ lệ lỗi tăng.
11. **“Chờ permit vô hạn là an toàn.”** Sai: caller thread và request connection tiếp tục bị giữ, dễ khuếch đại overload.
12. **“Đã có @Async thì không cần giới hạn.”** Sai: `@Async` chỉ điều phối thực thi, không tự bảo vệ downstream và không tự tạo rate quota.
13. **“Chỉ đọc số request/giây là biết hệ thống an toàn.”** Sai: cần thêm in-flight, tail latency, queue, lỗi, saturation.
14. **“Sử dụng một HashMap và System.currentTimeMillis không cần đồng bộ là đủ.”** Sai: rate state có thể race, đồng hồ wall-clock có thể bị chỉnh, phân tán càng phức tạp.

## 17. Câu hỏi tự kiểm tra

**Câu 1.** Một API áp `Semaphore(5)`, xử lý mỗi request trung bình 100 ms. Điều đó có nghĩa tối đa 5 RPS không?

**Đáp án:** không. `5` là số công việc đồng thời. Trong mô hình lý tưởng với 5 slot luôn bận và mỗi task 0,1 giây, throughput xấp xỉ 50 req/s; không bảo đảm con số đó nếu có overhead hoặc nút thắt khác.

**Câu 2.** Một API giới hạn 30 request/giây, nhưng mỗi request mất 4 giây. Có nguy cơ hơn 30 in-flight không?

**Đáp án:** có. Với tải ổn định, Little's Law cho khoảng `30 × 4 = 120` in-flight trung bình nếu không bị chặn bởi tài nguyên khác.

**Câu 3.** Pool có 4 worker và queue 100. Tại sao nhiều task được “accept” nhưng chỉ 4 task đang chạy?

**Đáp án:** `ThreadPoolTaskExecutor` giữ nhiều task trong hàng đợi; `active worker` khác `accepted/queued job`. Không phải rate limiter.

**Câu 4.** Fixed window 60 request/phút cho phép 60 lượt ở cuối phút cũ và 60 lượt ở đầu phút mới không?

**Đáp án:** có thể, tùy thời điểm reset cửa sổ. Đây là vấn đề burst sát ranh giới; cân nhắc sliding window/token bucket nếu yêu cầu kiểm soát burst tốt hơn.

**Câu 5.** Có ba pod, mỗi pod `Semaphore(10)`. Khi không có coordinator chung, có thể báo cáo “tối đa 10 request đồng thời toàn cụm” không?

**Đáp án:** không. Trong điều kiện đều có tải, tổng có thể lên gần 30 permit; 10 chỉ là ngưỡng cục bộ từng pod.

**Câu 6.** Đang dùng `@Async`, nên release semaphore ngay sau lời gọi phương thức async hay khi future hoàn tất?

**Đáp án:** nếu permit bảo vệ công việc async đang thực hiện, phải gắn release với **hoàn thành/thất bại/hủy** của công việc, không phải ngay lúc nhận future, và cần bảo đảm không release hai lần.

**Câu 7.** Một user bị chặn vì vượt 100 request/phút; nên dùng 429 hay 503?

**Đáp án:** 429 mô tả quota theo thời gian của client phù hợp hơn. Nếu service đang quá tải hệ thống và từ chối tạm thời do hết năng lực xử lý, 503 có thể phù hợp hơn.

## 18. Checklist trước khi triển khai

- [ ] Đã ghi rõ cap là **concurrency** hay **rate**, có đơn vị cụ thể và phạm vi đo.
- [ ] Concurrency: xác định sự kiện lấy permit, thời điểm hoàn trả, xử lý lỗi/cancel và chờ có giới hạn.
- [ ] Rate: xác định thuật toán, window/refill, burst, key quota, nơi lưu state và chính sách chờ.
- [ ] Đã phân biệt task **queued**, **running**, **in-flight request** và **completed**.
- [ ] Có giới hạn riêng cho thread pool, queue, DB connection và downstream nếu cần.
- [ ] Đã kiểm tra behavior trên nhiều pod và khi autoscale.
- [ ] Đã thiết kế HTTP 429/503, thông điệp lỗi và `Retry-After` khi có thể đưa ra.
- [ ] Metrics có cả rate, in-flight, queue, latency và rejection.
- [ ] Test có burst, slow dependency, cancellation, overload, shutdown và giới hạn toàn cụm.
- [ ] Không coi `Semaphore` hoặc `RateLimiter` là giải pháp persistence, idempotency hay durability cho job.

## 19. Tổng kết

**Concurrency limit** trả lời: *“Có bao nhiêu công việc được phép giữ tài nguyên cùng lúc?”* Quyền thường được **trả lại khi công việc kết thúc**. **Rate limit** trả lời: *“Trong thời gian này có bao nhiêu lượt được phép bắt đầu?”* Quyền thường được **phục hồi theo thời gian**.

Vì tốc độ và thời lượng tác vụ khác nhau, hai giới hạn độc lập về mặt ý nghĩa. Trong Spring, dùng một **Semaphore/Bulkhead** để bảo vệ số lời gọi in-flight tới tài nguyên khan hiếm, một **RateLimiter/gateway** cho quota theo thời gian, và cấu hình `ThreadPoolTaskExecutor` cùng queue hữu hạn để quản lý worker. Đo, kiểm thử và mô tả đúng phạm vi local/global của từng giới hạn trước khi mở rộng ứng dụng.

## Tài liệu tham khảo

- Java SE 17 — `Semaphore`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html
- Java SE 17 — `ThreadPoolExecutor`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
- Java SE 17 — `CountDownLatch`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html
- Spring Framework — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework — `ThreadPoolTaskExecutor` API: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Resilience4j — RateLimiter: https://resilience4j.readme.io/docs/ratelimiter
- Resilience4j — Bulkhead: https://resilience4j.readme.io/docs/bulkhead
- [RFC 6585, Section 4 — 429 Too Many Requests](https://www.rfc-editor.org/rfc/rfc6585.html)
- [RFC 9110, Section 15.6.4 — 503 Service Unavailable](https://www.rfc-editor.org/rfc/rfc9110.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-006">Ước lượng công suất rồi kiểm chứng bằng phép đo tải</a>

<details>
<summary>Xem chi tiết</summary>

# Ước lượng công suất rồi kiểm chứng bằng phép đo tải

## 1. Mục tiêu: từ dự đoán đến giới hạn đã được đo

Trong ứng dụng Spring Framework dùng `TaskExecutor`, đặc biệt là `ThreadPoolTaskExecutor`, cấu hình số thread và hàng đợi thường bắt đầu bằng một **ước lượng**: hệ thống phải nhận bao nhiêu tác vụ mỗi giây, mỗi tác vụ giữ worker trong bao lâu, và dịch vụ phụ thuộc có thể xử lý bao nhiêu việc song song? Sau đó phải **kiểm chứng bằng tải thực tế** để biết ước lượng ấy có đáp ứng mục tiêu về độ trễ, lỗi và tài nguyên không.

Ví dụ một service nhận yêu cầu tạo báo cáo. Nếu chỉ nhìn CPU hiện tại rồi đặt `maxPoolSize=100`, rất có thể pool thực sự không chạy tới 100 thread vì queue còn rộng, hoặc 100 thread cùng tranh chấp 10 kết nối database. Tăng thread có thể làm backlog, timeout và độ trễ tăng. Cách tiếp cận đúng là đặt giả thuyết định lượng, chọn cấu hình thử nghiệm, đo ở nhiều mức tải và sửa cấu hình theo **điểm nghẽn được quan sát**.

Mạch quyết định:

```text
Nhu cầu (jobs/s) + thời gian mỗi job + SLO
                    |
                    v
          Ước lượng concurrency
                    |
                    v
      Chọn core / max / queue / giới hạn phụ thuộc
                    |
                    v
          Sinh tải có kiểm soát
                    |
                    v
 So offered rate, throughput, latency, queue, lỗi
                    |
                    v
      Điều chỉnh và chạy lại cùng kịch bản
```

**Phạm vi:** bài này tập trung vào công suất xử lý tác vụ trong Spring executor và cách xác thực bằng load test. Công suất của **một instance** khác công suất toàn hệ thống nhiều instance; cả hai phải được phân biệt khi lập kế hoạch.

## 2. Thống nhất đơn vị đo trước khi tính

| Đại lượng | Ký hiệu | Cách hiểu |
| --- | --- | --- |
| Tải được gửi đến (*offered rate*) | `λ_in` | Số task/giây mà phía gọi **cố gắng** gửi |
| Tải được nhận | `λ_accepted` | Số task/giây executor thực sự nhận |
| Tốc độ hoàn tất (*throughput*) | `λ_done` | Số task hoàn tất/giây trong khoảng đo |
| Thời gian xử lý trong worker | `S` (giây) | Từ lúc task **bắt đầu chạy** đến lúc kết thúc; gồm thời gian task chờ I/O trong worker |
| Thời gian chờ queue | `W_q` (giây) | Từ lúc tác vụ được nhận đến khi có worker bắt đầu chạy |
| Thời gian hoàn tất toàn tác vụ | `W` (giây) | Gần bằng `W_q + S`, chưa kể các bước nằm ngoài ranh giới đo |
| Số worker đồng thời | `m` | Số thread tối đa có thể xử lý task tại thời điểm đó |
| Số việc đang chờ | `Q` | Độ dài hàng đợi nội bộ executor |
| Tỷ lệ worker bận | `u` | Xấp xỉ busy workers trung bình / số worker khả dụng |

**Đơn vị phải nhất quán.** Nếu `λ=100 jobs/s`, còn `S=80 ms`, hãy đổi thành `0,080 s` trước khi nhân. Chỉ số trung bình dùng để tính công suất ban đầu; các percentile p95/p99 được dùng để đánh giá độ trễ người dùng và tail latency. Không lấy p95 thay vào định luật Little rồi coi đó là công thức chính xác.

Một điểm rất dễ nhầm: HTTP endpoint có thể trả `202 Accepted` ngay sau khi enqueue task. Khi ấy `http_req_duration` đo **thời gian nhận việc**, không đo **thời gian task hoàn tất**. Để tính công suất xử lý hậu trường, phải đo riêng `submit → start → finish`.

## 3. Ước lượng từ arrival rate và thời gian giữ worker

Ở trạng thái đủ ổn định, nếu trung bình nhận và xử lý `λ` task mỗi giây, mỗi task chiếm một worker trong `S` giây, thì số worker bận trung bình xấp xỉ:

```text
busyWorkers ≈ λ × S
```

Đây là cách áp dụng logic cân bằng luồng công việc (liên quan đến định luật Little), **khi tải không tăng backlog vô hạn** và phép đo cùng một ranh giới. Để chừa khoảng trống cho biến động, có thể đặt tỷ lệ chiếm dụng worker mục tiêu `u_target` (ví dụ 70%) rồi ước lượng:

```text
m_initial = ceil((λ × S) / u_target)
```

Giá trị `u_target` là **giả định thiết kế** để tạo headroom, không phải quy tắc bắt buộc của Java/Spring. Đây cũng **không phải** công thức trực tiếp tính CPU utilization: một worker có thể đang chờ network hoặc database trong phần lớn thời gian.

### Ví dụ tính tay

Giả sử một instance dự kiến nhận 100 tác vụ/giây; đo ban đầu cho thấy mỗi tác vụ giữ worker trung bình 80 ms (`S=0,08 s`). Muốn tỷ lệ worker bận ở tải mục tiêu khoảng 70%:

```text
busyWorkers    = 100 × 0.08 = 8
m_initial      = ceil(8 / 0.70) = 12
idealCeiling   = 12 / 0.08 = 150 tasks/s
```

Diễn giải:

- Ở 100 task/s, trung bình khoảng **8 worker** cùng bận nếu `S` giữ ổn định.
- **12 worker** là một điểm bắt đầu để thử nghiệm, không phải kết luận cần đặt đúng 12 trong production.
- **150 task/s** là trần toán học lý tưởng nếu mỗi worker luôn hoàn thành một task trong 80 ms, không có tranh chấp và không tăng `S` khi đồng thời cao hơn. Công suất được chấp nhận theo SLO thường **thấp hơn** trần này.
- Nếu workload hoàn toàn dùng CPU, 100 task/s × 80 ms CPU mỗi task đòi khoảng 8 CPU-seconds/second trước overhead; không thể lấy 12 threads thay thế cho năng lực CPU. Nếu 80 ms chủ yếu là chờ I/O, số CPU cần có thể thấp hơn nhưng giới hạn kết nối/I/O trở thành vấn đề chính.

### Chương trình Java 17+ tính điểm xuất phát

Lưu nội dung sau thành `CapacityEstimate.java`:

```java
import java.util.Locale;

public final class CapacityEstimate {
    public static void main(String[] args) {
        double arrivalRate = 100.0;       // jobs/second
        double workerSeconds = 0.080;     // seconds/job, không tính queue wait
        double targetWorkerUse = 0.70;    // giả định headroom

        double busyWorkers = arrivalRate * workerSeconds;
        int proposedThreads = (int) Math.ceil(busyWorkers / targetWorkerUse);
        double idealCeiling = proposedThreads / workerSeconds;

        System.out.printf(Locale.ROOT,
                "busy workers = %.1f%n", busyWorkers);
        System.out.println("initial threads = " + proposedThreads);
        System.out.printf(Locale.ROOT,
                "ideal ceiling = %.0f jobs/s%n", idealCeiling);
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac CapacityEstimate.java
java CapacityEstimate
```

Stdout xác định:

```text
busy workers = 8.0
initial threads = 12
ideal ceiling = 150 jobs/s
```

Chương trình **chỉ tính giả thuyết**. Nó không đo queue, tỷ lệ lỗi, dịch vụ ngoài hay độ trễ p95. Những giá trị đó phải thu từ load test.

## 4. Ước lượng queue và thời gian chờ khi xuất hiện burst

Pool hữu hạn có thể chịu một đợt đến nhanh trong thời gian ngắn bằng cách xếp hàng. Với công suất hoàn tất bền vững `μ` (jobs/s) đã được **đo thực tế**, nếu đợt tải đến `λ_peak > μ` và không có điều kiện đặc biệt khác, backlog tăng gần:

```text
backlogAdded ≈ (λ_peak - μ) × durationSeconds
```

Ví dụ **minh họa giả định**: giả sử hệ thống thực sự xử lý được 150 jobs/s không giảm tốc. Một burst 180 jobs/s kéo dài 5 giây có thể tăng backlog thêm khoảng `(180-150)×5 = 150 jobs`. Nếu sau đó tải giảm còn 100 jobs/s và công suất vẫn là 150 jobs/s, backlog có thể giảm với tốc độ ròng khoảng 50 jobs/s, tức cần gần 3 giây để giải phóng 150 jobs. Đây là **mô hình đơn giản**, chưa xét phân bố thời gian xử lý, biến động arrivals, rejection và thay đổi công suất khi quá tải.

Queue có 200 ô **có thể chứa 150 job** trong ví dụ trên, nhưng “chứa được” không đồng nghĩa “đáp ứng SLO”: những job cuối burst phải chờ lâu hơn. Queue quá dài đôi khi giữ tỷ lệ lỗi thấp trong ngắn hạn nhưng lại tạo timeout hoặc kết quả trễ tới mức không còn giá trị.

Từ định luật Little ở trạng thái ổn định:

```text
L_system = λ_done × W
```

`L_system` là số task trung bình trong **toàn phạm vi đo** (đang chờ + đang chạy), còn `W` là thời gian trung bình từ khi vào tới khi ra phạm vi ấy. Ví dụ nếu hệ thống hoàn tất 100 jobs/s và thời gian hoàn tất trung bình là 0,25 s thì trung bình có khoảng 25 task cùng tồn tại trong hệ thống. **Không dùng hệ thức trạng thái ổn định để hợp thức hóa một hàng đợi liên tục tăng trong overload**.

## 5. Áp dụng giả thuyết vào ThreadPoolTaskExecutor

`ThreadPoolTaskExecutor` của Spring bọc `java.util.concurrent.ThreadPoolExecutor`. Ba tham số quyết định việc nhận tải:

- `corePoolSize`: khi số worker dưới ngưỡng core, pool thường tạo thêm worker cho task mới.
- `queueCapacity`: sau khi đạt core, pool thường ưu tiên đặt task vào queue còn chỗ.
- `maxPoolSize`: pool chỉ mở rộng quá core khi **queue không nhận thêm task**. Khi queue cũng đầy và đã đạt max, task có thể bị từ chối.

Vì thế **không thể chọn `maxPoolSize` độc lập với `queueCapacity`**. Giá trị mặc định của class `ThreadPoolTaskExecutor` là core 1, max rất lớn, queue rất lớn; không nên xem đó là cấu hình sản xuất đã được kiểm chứng. Spring Boot có cơ chế tự cấu hình riêng theo điều kiện/phiên bản, nên hãy xem bean **thực sự** được dùng bởi `@Async` hoặc service.

Ví dụ cấu hình đầu tiên cho kịch bản 12 worker cố định, queue giới hạn, dùng trong một Spring application:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class WorkerCapacityConfig {
    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(12);
        pool.setMaxPoolSize(12);
        pool.setQueueCapacity(200);
        pool.setThreadNamePrefix("report-");
        return pool;
    }
}
```

Đây là **class cấu hình Spring**, cần `spring-context` tương thích và bean được đưa vào ApplicationContext; nó không biên dịch độc lập bằng chỉ JDK. Container quản lý lifecycle của bean. Cấu hình này được gọi là **giả thuyết thử nghiệm**, không phải lời khuyên mọi hệ thống đều dùng 12/12/200.

Chọn core=max giúp phép đo ban đầu dễ diễn giải hơn: pool không có bước “chờ đầy queue mới tăng lên max”. Sau khi có dữ liệu, ta có thể so sánh cấu hình core nhỏ hơn, max lớn hơn và queue nhỏ hơn để cải thiện cách hấp thụ burst. Nhưng lúc đó phải nhớ thứ tự **core → queue → max → rejection**: nếu queue quá rộng, max lớn chưa chắc tạo thêm concurrency.

`TaskExecutor.execute(Runnable)` chỉ giao việc, không tự cam kết task đã hoàn thành. Với tác vụ async cần thống kê thời gian hoàn tất, phải đo **trong worker**, không chỉ lấy thời gian trả về từ `execute`.

## 6. Điểm nghẽn thật không nhất thiết nằm ở số thread

Trước khi tăng pool, hãy hỏi mỗi task đợi tài nguyên nào:

| Loại tải | Thứ có thể giới hạn trước | Hệ quả khi tăng thread |
| --- | --- | --- |
| Tính toán CPU-bound | Số lõi CPU, CPU quota container, GC | Quá nhiều thread gây chuyển ngữ cảnh, tranh chấp và tăng latency |
| Truy vấn database | Kích thước connection pool, lock/query plan, IOPS | Thread tăng nhưng cùng chờ kết nối hoặc lock |
| HTTP gọi dịch vụ ngoài | Connection pool, timeout, quota/rate limit đối tác | Nhiều call đồng thời hơn có thể gây throttling và retry |
| Tác vụ đọc/ghi tệp | Disk throughput, latency, file descriptor | Thêm thread không bù được giới hạn I/O |
| Hệ thống đồng bộ | Lock contention, critical section, shared state | Số worker tăng nhưng throughput gần như đứng yên |

Nếu pool có 40 threads, nhưng database chỉ cấp 10 connections và mỗi task cần một connection suốt thời gian xử lý, tạo thêm threads có thể chỉ làm tăng thời gian chờ connection. Một executor nhận được nhiều task **không tự chứng minh nghiệp vụ đã xử lý được nhiều task hơn**.

Với nhiều instance, cũng phải tính tổng tải lên dependency. Ví dụ 4 pods × 12 threads = tối đa 48 tác vụ đồng thời vào đoạn xử lý; nếu mỗi tác vụ dùng một connection, database pool toàn cụm và giới hạn DB phải đủ đáp ứng. Hệ thống tăng replicas còn có thể làm tổng số kết nối và tốc độ retry tăng.

## 7. Xác định tiêu chí chấp nhận trước khi load test

Một phép đo chỉ có ý nghĩa khi biết thế nào là “đủ”. Ví dụ **mục tiêu giả định** cho endpoint tạo công việc:

- Ở tải **100 yêu cầu nhận việc/giây** trên cấu hình hạ tầng đã xác định, ít nhất 99,9% công việc được nhận hợp lệ và cuối cùng hoàn tất; có kiểm tra mất/nhân đôi công việc theo hợp đồng nghiệp vụ.
- `p95` của **thời gian hoàn tất end-to-end** không vượt **1 giây**, không chỉ thời gian HTTP trả `202`.
- **Không có từ chối task** ở tải mục tiêu; `queueSize` tăng trong burst thì phải giảm về vùng ổn định sau đó.
- CPU, heap, GC, connection pool và quota dịch vụ ngoài còn khoảng dự phòng đã thống nhất.

Các con số này chỉ minh họa **cách phát biểu tiêu chí**, không phải SLO mặc định của Spring. Thay bằng yêu cầu vận hành thật và ghi rõ thời gian duy trì của phép đo. Việc qua SLO ở 100 jobs/s không có nghĩa sẽ qua ở 150 jobs/s.

**Đặc biệt:** hãy tách “yêu cầu được nhận” khỏi “job được xử lý thành công”. Nếu HTTP trả 202 nhưng task bị `TaskRejectedException` rồi thất lạc, đồ thị HTTP có thể trông đẹp trong khi nghiệp vụ sai.

## 8. Lập kế hoạch phép đo tải có thể tái lập

Một kế hoạch load test nên có những pha sau, thực hiện trên môi trường được phép tạo tải:

| Pha | Mục đích | Dấu hiệu cần quan sát |
| --- | --- | --- |
| Warm-up | Làm nóng JVM/JIT, kết nối, cache, thread | Độ trễ bắt đầu ổn định, tránh nhầm chi phí khởi động với steady state |
| Baseline thấp | Đo `S` khi ít tranh chấp | Thời gian worker, throughput, CPU/GC, thời gian DB/API |
| Tăng tải từng mức | Tìm vùng tăng gần tuyến tính | Accepted/done rate, p95/p99, active threads, queue |
| Tải mục tiêu kéo dài | Kiểm chứng SLO bền vững | Backlog không tăng liên tục, ít lỗi, GC/CPU/connection ổn định |
| Burst ngắn | Đánh giá giới hạn queue và khôi phục | Rejection, đỉnh queue, queue-wait, thời gian drain |
| Overload có kiểm soát | Xác định điểm mất SLO và cơ chế bảo vệ | Timeout, `TaskRejectedException`, lỗi downstream, retry amplification |
| Recovery / soak | Xem hệ thống trở lại bình thường và có rò rỉ không | Pool/queue trở lại nền, memory không tăng vô hạn, hoàn tất task tồn |

Để phép so sánh cấu hình công bằng, giữ nguyên loại tác vụ, payload, dữ liệu thử, số instance, CPU/memory quota, connection pool, trạng thái cache và cách đo. Ghi lại cấu hình ở mỗi lượt. Nếu đồng thời thay dữ liệu, network và `maxPoolSize`, sẽ khó xác định nguyên nhân cải thiện hoặc suy giảm.

Nguồn tạo tải cũng có giới hạn; theo dõi CPU/network của load generator và số request thực sự phát được. Dùng payload đại diện: tác vụ “sleep 10ms” không thể thay cho báo cáo thật chứa nhiều truy vấn DB hoặc mã hóa tệp.

## 9. Ví dụ k6: đo tải đến theo jobs/giây

Công cụ như **Grafana k6** hỗ trợ kịch bản `constant-arrival-rate`: bắt đầu iteration theo tốc độ mục tiêu, tương đối độc lập với thời gian response. Điều này hữu ích khi kiểm tra “server có chịu 100 yêu cầu/giây không?”. Mô hình số VU cố định có thể làm tốc độ đến **tự giảm** khi server chậm, che bớt điểm quá tải.

Giả sử ứng dụng thử nghiệm có API **minh họa** `POST /api/jobs` trả `202` sau khi nhận công việc. Tạo tệp `load.js`:

```javascript
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    jobs: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || '50'),
      timeUnit: '1s',
      duration: '4m',
      preAllocatedVUs: 100,
      maxVUs: 250
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
    dropped_iterations: ['count==0'],
    checks: ['rate>0.99']
  }
};

export default function () {
  const payload = JSON.stringify({
    requestKey: 'perf-' + __VU + '-' + __ITER
  });

  const response = http.post(
    __ENV.BASE_URL + '/api/jobs',
    payload,
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(response, {
    'accepted with 202': (r) => r.status === 202
  });
}
```

Đây là **mẫu k6 chạy độc lập với Java**, nhưng **chỉ chạy đúng khi môi trường thử nghiệm có endpoint `/api/jobs` với hợp đồng 202 và payload như ví dụ**. Nếu API thật khác, điều chỉnh đường dẫn, payload, xác thực và định nghĩa thành công trước khi đo. Không sử dụng trực tiếp để gây tải trên một hệ thống đang phục vụ người dùng nếu chưa có kế hoạch thử tải.

Tại máy có cài k6, có thể thử các mức được kiểm soát riêng:

```bash
k6 run -e BASE_URL=http://localhost:8080 -e RATE=20 load.js
k6 run -e BASE_URL=http://localhost:8080 -e RATE=50 load.js
k6 run -e BASE_URL=http://localhost:8080 -e RATE=100 load.js
k6 run -e BASE_URL=http://localhost:8080 -e RATE=140 load.js
```

Các lệnh minh họa **các lượt chạy riêng**, không phải output có sẵn. Chạy lại mỗi mức sau khi môi trường đã trở về trạng thái nền; không tăng tải một cách mù quáng nếu đã có dấu hiệu không an toàn hoặc đã vượt giới hạn được phép.

Ở đây `rate` nghĩa là **iterations/giây**. Mỗi iteration có đúng một HTTP POST nên xấp xỉ số lần gửi request/giây, miễn là có đủ VU và mạng không giới hạn. `dropped_iterations` tăng có thể là do k6 thiếu VU hoặc server trở nên quá chậm khiến VU không được giải phóng; không được quy ngay nguyên nhân cho executor Spring. Mẫu không thêm `sleep()` cuối iteration vì `constant-arrival-rate` đã điều tiết tốc độ gửi.

Threshold `p(95)<500` trong script chỉ kiểm tra **HTTP admission latency** dưới 500 ms; nó không xác nhận thời gian job chạy xong. Phải có metric nghiệp vụ hoặc phép truy vấn trạng thái job (nếu hợp đồng có hỗ trợ) để kiểm tra end-to-end SLO, tỷ lệ hoàn tất, tính đúng và mất việc.

## 10. Những metric phải quan sát đồng thời

Tại thời điểm chạy test, nên vẽ các chuỗi thời gian theo **cùng timestamp**:

| Nhóm | Metric gợi ý | Câu hỏi được trả lời |
| --- | --- | --- |
| Tải vào | tasks submitted/s, HTTP RPS, offered rate k6 | Gửi bao nhiêu việc tới hệ thống? |
| Tiếp nhận | tasks accepted/s, rejected/s, HTTP 2xx/4xx/5xx | Bao nhiêu việc thực sự vào queue/pool? |
| Xử lý | tasks completed/s, failed/s | Công suất thực sự hoàn tất là bao nhiêu? |
| Executor | active threads, current pool size, queue size/capacity | Pool bận hay hàng đợi đang tích tụ? |
| Thời gian | queue wait, execution time, end-to-end p50/p95/p99 | Người dùng chờ ở giai đoạn nào? |
| JVM | CPU, GC pause, heap, live threads | Runtime có phải điểm nghẽn? |
| Phụ thuộc | DB connections active/pending, query latency, outbound HTTP latency/quota | Task nghẽn ở DB/dịch vụ ngoài hay executor? |
| Client tạo tải | actual iterations/s, dropped iterations, VUs, generator CPU | Load generator có phát đúng tải mục tiêu không? |

Trong `ThreadPoolTaskExecutor`, các API như `getActiveCount()`, `getPoolSize()`, `getQueueSize()` và `getThreadPoolExecutor().getCompletedTaskCount()` cung cấp số đo theo thời điểm (có thể xấp xỉ khi concurrency cao). Micrometer có binder cho các executor được đăng ký thích hợp; **không mặc định mọi bean đều tự xuất đầy đủ metric** trong mọi cấu hình Spring thuần.

Để đo **queue wait** chính xác hơn, có thể bọc task: ghi `submittedAt` trước khi gọi `execute`, ghi `startedAt` trong `Runnable.run()`, ghi `finishedAt` trong `finally`, rồi tính `startedAt - submittedAt` và `finishedAt - startedAt` bằng `System.nanoTime()`. Cần ghi nhận cả nhánh rejected/failed để mẫu đo không thiên về các task thành công. Khi ghi metric, tránh tạo tag có độ biến thiên quá lớn như mỗi `requestId`.

**Nhìn vào xu hướng, không chỉ snapshot.** `queueSize=100` tại một thời điểm có thể là một burst sắp hết; queue 100 rồi 150 rồi 200 trong nhiều phút ở tải không đổi cho thấy backlog có thể không được giải phóng.

## 11. Cách đọc kết quả và quyết định điều chỉnh

Phân tích theo bảng dấu hiệu:

| Triệu chứng dưới tải | Giả thuyết cần kiểm tra | Hành động thử có kiểm soát |
| --- | --- | --- |
| Queue tăng, active threads gần core, chưa tới max | Queue còn rộng nên chưa mở rộng vượt core | Xem lại core và queue; đo lại tail latency |
| Queue đầy, active threads gần max, rejection tăng | Tải vượt công suất thực tế | Giảm offered rate, đặt giới hạn đầu vào, tối ưu task, cân nhắc thêm năng lực thực |
| CPU sát quota, nhiều context switches, throughput không tăng khi thêm threads | CPU-bound/overthreading | Giảm concurrency hoặc tăng CPU; đo CPU time/task |
| CPU thấp nhưng threads bận, DB pool chờ connection | Nghẽn database/connection pool | Tối ưu query, giảm thời gian giữ connection, chỉnh giới hạn phù hợp |
| HTTP trả 202 nhanh nhưng thời gian job hoàn tất tăng | Queue/workers phía sau đang nghẽn | Đo end-to-end latency và backlog, không dùng mỗi HTTP p95 |
| Tăng thread làm p99 xấu hơn, lỗi downstream nhiều | Tăng áp lực lên phụ thuộc hoặc tranh chấp | So sánh cùng tải với cấu hình cũ; hạn chế concurrency |
| Queue giảm sau burst, p95 phục hồi, không mất job | Hấp thụ burst và phục hồi có vẻ tốt | Kiểm chứng lại ở burst dài hơn và tải nền khác |
| k6 dropped iterations tăng | Có thể thiếu VU/generator hoặc server chậm | Kiểm tra giới hạn generator và latency trước khi kết luận capacity |

Phải phân biệt bốn giới hạn:

1. **Admission capacity:** tốc độ hệ thống có thể nhận request/task mà chưa bắt đầu trả lỗi.
2. **Processing capacity:** tốc độ task thực sự hoàn tất đúng.
3. **SLO capacity:** tốc độ cao nhất vẫn đạt yêu cầu latency, error, recovery và tài nguyên.
4. **Burst tolerance:** mức vượt tải trong thời gian ngắn mà queue, deadline và chính sách từ chối vẫn kiểm soát được.

Giới hạn được dùng để hoạch định vận hành thường là **SLO capacity**, thấp hơn các con số “đẩy được task vào queue” hoặc “có thể tạo thêm thread”.

## 12. Khi nào có thể kết luận đã kiểm chứng?

Một cấu hình có thể được chấp nhận tạm thời khi các lượt test đại diện cho tải kỳ vọng cho thấy:

- Có quan sát được **tốc độ gửi, tốc độ nhận và tốc độ hoàn tất**; không lấy HTTP 202 làm bằng chứng task xong.
- Trong pha tải mục tiêu kéo dài, throughput hoàn tất theo kịp offered rate hợp lệ, backlog không tăng không giới hạn, p95/p99 và lỗi đáp ứng SLO đã đặt.
- Giới hạn CPU, heap, GC, DB/API connections, retry, pool size và queue được đánh giá trên đúng loại dữ liệu và số instance dự kiến.
- Khi burst hoặc overload có kiểm soát, hệ thống từ chối/điều tiết theo chính sách rõ ràng và phục hồi; không âm thầm mất hoặc lặp job ngoài hợp đồng.
- Kết quả được chạy lại, lưu kèm phiên bản build, cấu hình executor, kích thước pool phụ thuộc, mức tải, thời gian đo và biểu đồ; thay đổi thông số thì kiểm tra lại.

**Lưu ý:** load test không chứng minh mọi điều kiện tương lai đều an toàn. Các khác biệt về kích thước dữ liệu, cache lạnh, sự cố downstream, GC, mạng và phân phối thời gian xử lý có thể làm công suất thay đổi. Ước lượng + phép đo là một vòng lặp hiệu chỉnh, không phải công thức dùng một lần.

## 13. Sai lầm phổ biến

1. **Lấy số CPU cores nhân một hằng số cố định để đặt thread pool cho mọi workload.** Cần đo thời gian giữ worker, tỷ lệ CPU/I/O wait và giới hạn phụ thuộc.
2. **Dùng p95 thay cho thời gian trung bình trong công thức Little.** Định luật dùng trung bình ở trạng thái ổn định; p95 là ràng buộc độ trễ riêng.
3. **Chỉ tăng `maxPoolSize` nhưng để queue rất rộng.** Pool có thể giữ gần core mãi vì queue chưa đầy.
4. **Chỉ đo HTTP latency cho tác vụ bất đồng bộ.** Phải theo dõi thời gian hoàn tất thật và tỷ lệ job thành công.
5. **Nhầm tốc độ gửi với throughput.** Nếu 200 jobs/s được gửi nhưng 120 jobs/s hoàn tất, backlog/lỗi có thể tích tụ.
6. **Load test chỉ 15 giây rồi kết luận công suất bền vững.** Hệ thống có thể cần thời gian warm-up và đủ lâu để bộc lộ queue growth, GC hoặc dependency saturation.
7. **Dùng số VU cố định mà không đo arrival rate.** Khi server chậm, VU bị giữ lâu có thể làm tốc độ gửi giảm và che quá tải.
8. **Queue cực lớn để tránh rejection.** Backlog lớn gây timeout, memory pressure và công việc hết giá trị trước khi được xử lý.
9. **Tăng threads mà không theo dõi database và API bên ngoài.** Cải thiện ở executor có thể chuyển nghẽn sang dependency.
10. **Không xác định khả năng của load generator.** Iterations bị rơi do công cụ tạo tải không đủ khiến phép đo capacity sai.
11. **So sánh hai cấu hình trên môi trường khác nhau.** Số pod, CPU quota, dữ liệu, cache và network có thể làm kết quả không còn so sánh được.
12. **Chỉ nhìn throughput cao nhất.** Một cấu hình có thể xử lý nhiều hơn trong vài giây nhưng vi phạm p99, tỷ lệ lỗi hoặc không phục hồi.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Có 100 jobs/s, mỗi job chiếm worker trung bình 80 ms, muốn tải worker quanh 70%. Nên bắt đầu thử bao nhiêu worker?  
**Đáp án:** `ceil((100×0,08)/0,70)=12` worker; đó chỉ là giả thuyết ban đầu.

**Câu 2.** Đặt `core=4`, `max=32`, queue rất lớn. Khi 4 worker bận, pool có tự tăng ngay lên 32 không?  
**Đáp án:** Thường không. Nó ưu tiên xếp hàng nếu queue còn nhận task.

**Câu 3.** Tại mức tải 120 jobs/s, ứng dụng trả HTTP 202 đúng 120 lần/s nhưng chỉ hoàn tất 70 jobs/s. Có thể nói đủ công suất 120 jobs/s không?  
**Đáp án:** Không. Admission rate và processing rate khác nhau; backlog có thể tăng hoặc task lỗi/mất.

**Câu 4.** Vì sao tăng từ 12 lên 40 workers có thể làm p99 tệ hơn dù CPU còn thấp?  
**Đáp án:** Task có thể tranh chấp DB connection, lock, quota API hoặc gây timeout/retry nhiều hơn.

**Câu 5.** Lúc thử tải, queue tăng một phút rồi giảm và về nền; lúc khác queue tăng mãi ở cùng offered rate. Hai tình huống có giống nhau không?  
**Đáp án:** Không. Một tình huống có thể là burst được hấp thụ, tình huống kia gợi ý công suất hoàn tất không theo kịp tải đến.

**Câu 6.** Dựa vào metric nào để phân biệt công suất thực với khả năng tạo tải?  
**Đáp án:** So sánh offered iterations/s, dropped iterations, accepted/s, completed/s và error/latency ở cả load generator lẫn server.

### Tài liệu đối chiếu

- [Spring Framework — ThreadPoolTaskExecutor JavaDoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Java SE 17 — ThreadPoolExecutor: core/max, queue, rejection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Micrometer — JVM and Executor metrics](https://docs.micrometer.io/micrometer/reference/1.12/reference/jvm.html)
- [Grafana k6 — Constant arrival rate](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/)
- [Grafana k6 — Open and closed load models](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/open-vs-closed/)
- [Grafana k6 — Thresholds](https://grafana.com/docs/k6/latest/using-k6/thresholds/)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-007">Cấu hình bằng @Bean hoặc namespace task trong XML</a>

<details>
<summary>Xem chi tiết</summary>

# Cấu hình TaskExecutor và TaskScheduler bằng @Bean hoặc namespace task trong XML

## 1. Mục tiêu của cấu hình tài nguyên đồng thời

Spring Framework cung cấp các abstraction để **thực thi tác vụ** (`TaskExecutor`) và **lên lịch tác vụ** (`TaskScheduler`). Ứng dụng phải tạo và cấu hình implementation phù hợp, đưa nó vào Spring ApplicationContext và tiêm cho những thành phần cần dùng.

Có hai cách khai báo phổ biến trong Spring Framework:

1. **Java configuration**: viết method `@Bean` trong lớp `@Configuration`, tạo đối tượng executor/scheduler và cấu hình bằng setter.
2. **XML configuration**: dùng phần tử thuộc **task namespace**, chẳng hạn `<task:executor>`, `<task:scheduler>` và `<task:annotation-driven>`. XML cũng hỗ trợ cách thông thường qua `<bean class="...">` và `<property>`.

Hai cách có chung mục tiêu: đăng ký bean trong container, để business code dùng interface thay vì tự quản lý vòng đời thread pool. Việc chuyển Java sang XML không tự làm ứng dụng nhanh hơn; lựa chọn phụ thuộc vào cách cấu hình module, yêu cầu vận hành, cấu trúc dự án và mức độ dễ bảo trì.

```text
             Configuration, one choice per application/context
                  /                             \
       Java @Configuration                 Spring XML
       @Bean executor()                    <task:executor .../>
       @Bean scheduler()                   <task:scheduler .../>
                  \                             /
                   \                           /
               ApplicationContext / bean registry
                       |              |
                     TaskExecutor   TaskScheduler
                       |              |
                 submit Runnable   schedule a task
                       |              |
                     workers / clock / concurrency controls
```

Điểm thiết kế quan trọng: **bean definition** quyết định cách tạo và quản lý tài nguyên; **client code** chỉ cần biết hợp đồng cần dùng.

## 2. Phân biệt TaskExecutor và TaskScheduler trước khi cấu hình

| Đặc tính | `TaskExecutor` | `TaskScheduler` |
| --- | --- | --- |
| Mục đích | Nhận task để thực thi theo chính sách executor | Chạy task tại thời điểm chỉ định hoặc theo lịch |
| API tiêu biểu | `execute(Runnable)` | `schedule(...)`, các biến thể fixed-rate/fixed-delay |
| Implementation hay gặp | `ThreadPoolTaskExecutor` | `ThreadPoolTaskScheduler` |
| Cấu hình Java | Bean `ThreadPoolTaskExecutor` | Bean `ThreadPoolTaskScheduler` |
| Cấu hình XML rút gọn | `<task:executor .../>` | `<task:scheduler .../>` |
| Annotation liên quan | `@Async` | `@Scheduled` |

**Không nhầm lẫn:** chỉ khai báo bean executor không tự động kích hoạt `@Async`; chỉ khai báo scheduler không tự động kích hoạt `@Scheduled` trong Spring Framework thuần. Để xử lý annotation, cấu hình Java cần `@EnableAsync` / `@EnableScheduling` tương ứng, hoặc XML dùng `<task:annotation-driven .../>`.

Hai loại tài nguyên có thể tồn tại cùng lúc và phục vụ hai loại công việc khác nhau. Việc scheduler quyết định *khi nào* task chạy không giống executor thông thường quyết định *cách nào* task được nhận và phân phối cho worker.

## 3. Cấu hình ThreadPoolTaskExecutor bằng @Bean

Ví dụ method bean trong một lớp cấu hình Java:

```java
@Configuration(proxyBeanMethods = false)
public class TaskInfrastructureConfig {

    @Bean("workExecutor")
    public ThreadPoolTaskExecutor workExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("work-");
        return executor;
    }
}
```

Đây là **fragment**; cần import `org.springframework.context.annotation.Configuration`, `org.springframework.context.annotation.Bean` và `org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor`. Bản chạy đầy đủ sẽ có ở mục 7.

Phân tích:

- `@Configuration` cho biết class chứa bean definitions. `proxyBeanMethods=false` phù hợp khi các method `@Bean` không gọi trực tiếp lẫn nhau để lấy bean do container quản lý.
- `@Bean("workExecutor")` đặt định danh ổn định cho executor. Có thể inject theo tên khi ứng dụng có nhiều executor.
- `corePoolSize=2`: pool tạo đến 2 core worker khi task được gửi (mặc định không nhất thiết tạo sẵn).
- `maxPoolSize=4`: giới hạn worker tối đa trong trường hợp queue không nhận được task.
- `queueCapacity=8`: chọn `LinkedBlockingQueue` có tối đa 8 task nằm chờ. Nếu `queueCapacity=0`, Spring dùng `SynchronousQueue` để handoff, không lưu backlog.
- `threadNamePrefix="work-"`: giúp nhận dạng threads trong log, thread dump và profiler.

Với queue dương, quy tắc tiếp nhận thông thường của `ThreadPoolExecutor` là **tạo core threads → xếp vào queue → tăng đến max nếu queue đầy → từ chối nếu pool/queue đều hết chỗ**. Do đó tăng riêng `maxPoolSize` mà giữ queue rất lớn có thể không làm pool mở rộng như mong đợi.

Các con số 2/4/8 trong bài chỉ phục vụ ví dụ. Trong production, phải dựa trên tải, CPU, thời gian task, giới hạn DB/HTTP, timeout và yêu cầu độ trễ để chọn giá trị.

### Lifecycle của bean executor

Khi `ThreadPoolTaskExecutor` là một Spring-managed bean, container áp dụng các lifecycle callback cần thiết để khởi tạo executor và phối hợp shutdown. Không cần gọi `afterPropertiesSet()` thủ công từ method `@Bean` rồi lại để Spring khởi tạo một lần nữa.

Trong chương trình độc lập tự tạo bằng `new ThreadPoolTaskExecutor()` mà **không** dùng container, người viết phải khởi tạo và dừng pool đúng cách. Cấu hình thông qua bean giúp một nơi chịu trách nhiệm này. Đối với tác vụ nền quan trọng, vẫn phải xem cấu hình chính sách shutdown/await-termination; không mặc định rằng tất cả task luôn hoàn tất khi ứng dụng thoát.

## 4. Cấu hình ThreadPoolTaskScheduler bằng @Bean

Để lên lịch công việc, có thể dùng:

```java
@Bean("workScheduler")
public ThreadPoolTaskScheduler workScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("schedule-");
    return scheduler;
}
```

Đây cũng là fragment, đặt trong lớp `@Configuration` và cần import `ThreadPoolTaskScheduler`. Nó có pool để xử lý các lần kích hoạt lịch, **không phải queueCapacity của executor thường**.

Nếu muốn Spring nhận diện method `@Scheduled`:

```java
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Các bean TaskScheduler và bean có method @Scheduled.
}
```

Một method được Spring quản lý có thể khai báo:

```java
@Scheduled(fixedDelay = 5_000)
public void refreshCache() {
    // Phần xử lý làm mới cache.
}
```

Các fragment này cần `org.springframework.scheduling.annotation.EnableScheduling` và `Scheduled`. Đây là **minh họa ngữ nghĩa**, không phải chương trình chạy độc lập. `fixedDelay` đo độ trễ sau khi **lần thực thi trước kết thúc**; `fixedRate` biểu diễn chu kỳ tính từ mốc bắt đầu theo cơ chế scheduler. Với nhiều thread, tác vụ dài, retry và tài nguyên chung, phải phân tích khả năng chồng lấn, độ trễ và tính idempotent; không coi scheduling là bảo đảm xử lý chính xác một lần trong hệ phân tán.

## 5. Namespace task trong Spring XML

Cú pháp namespace cho phép mô tả cấu hình tài nguyên mà không phải liệt kê đầy đủ tên lớp cho mỗi bean.

### 5.1. Khai báo namespace và schema

Phần đầu một XML context hợp lệ:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:task="http://www.springframework.org/schema/task"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/task
           https://www.springframework.org/schema/task/spring-task.xsd">

    <!-- Các bean definitions sẽ nằm ở đây. -->
</beans>
```

Namespace `task` chỉ là một prefix được ánh xạ tới URI schema của Spring; bản thân chuỗi `task` không phải tên bean. Nếu thiếu `xmlns:task` hoặc cặp URI/schemaLocation, XML parser có thể báo lỗi schema hoặc prefix không xác định.

### 5.2. Tạo executor bằng <task:executor>

```xml
<task:executor id="workExecutor"
               pool-size="2-4"
               queue-capacity="8"
               rejection-policy="ABORT"
               keep-alive="60"/>
```

Ở đây:

- `id="workExecutor"` là tên bean Spring; cùng tên này có thể được tham chiếu từ `@Qualifier`, `@Async("workExecutor")` hay thuộc tính XML.
- `pool-size="2-4"` đặt core size bằng 2, max size bằng 4. Một giá trị duy nhất như `pool-size="4"` có nghĩa core và max đều bằng 4 (pool cố định về giới hạn worker).
- `queue-capacity="8"` tạo queue lưu tối đa 8 phần tử; `queue-capacity="0"` chọn handoff queue không chứa backlog.
- `rejection-policy="ABORT"`: khi executor quá tải, tác vụ bị từ chối theo abort policy; Spring có thể báo `TaskRejectedException`.
- `keep-alive="60"`: thời gian nhàn rỗi (giây) trước khi non-core workers có thể bị thu hồi.

Cách viết XML này tương đương về các thông số cốt lõi với method `@Bean("workExecutor")` ở mục 3. Phần Java có thêm `threadNamePrefix` tùy chỉnh; ở dạng namespace, Spring sử dụng `id` làm cơ sở đặt prefix cho thread names. Không giả định tên thread sẽ giống hệt giữa hai cách cấu hình.

Ví dụ khác minh họa backpressure từ caller:

```xml
<task:executor id="limitedExecutor"
               pool-size="3-6"
               queue-capacity="20"
               rejection-policy="CALLER_RUNS"/>
```

Khi pool và queue quá tải, `CALLER_RUNS` có thể khiến thread gọi tự thực hiện tác vụ (nếu executor vẫn đang hoạt động). Chính sách này làm caller chậm lại, nhưng có thể gây latency lớn nếu caller là HTTP request worker hoặc event-loop thread. Nó không phù hợp mặc định cho mọi hệ thống. Các chính sách loại bỏ task như `DISCARD` cần được đánh giá cẩn thận để tránh mất nghiệp vụ.

### 5.3. Tạo scheduler bằng <task:scheduler>

```xml
<task:scheduler id="workScheduler" pool-size="2"/>
```

Phần tử này tạo một `ThreadPoolTaskScheduler` có pool size 2. Nếu không khai báo `pool-size`, Spring dùng một thread theo cấu hình namespace mặc định. Không áp `queue-capacity` của `<task:executor>` sang `<task:scheduler>`; đây là hai loại bean khác nhau.

### 5.4. Kích hoạt @Async và @Scheduled qua XML

```xml
<task:annotation-driven
    executor="workExecutor"
    scheduler="workScheduler"/>
```

Ở đây `executor` tham chiếu bean dùng xử lý method `@Async` và `scheduler` tham chiếu bean dùng quản lý `@Scheduled`. Việc khai báo này khác với chỉ tạo executor/scheduler beans: nó **bật xử lý annotation tương ứng**.

Tương đương về mục đích ở Java configuration là sử dụng `@EnableAsync` và/hoặc `@EnableScheduling`, cùng một hay các class `@Configuration` thích hợp. Nếu chỉ có công việc gửi bằng `TaskExecutor.execute(...)`, không bắt buộc bật annotation processing.

Lưu ý: Với Spring proxy mode thường dùng, gọi một method `@Async` từ method khác trên **cùng đối tượng** (`this.asyncMethod()`) không qua proxy, nên không chuyển sang executor như khi gọi từ bên ngoài qua Spring-managed proxy.

### 5.5. Lên lịch hoàn toàn trong XML

Nếu không dùng annotation `@Scheduled`, namespace cũng có thể đăng ký lời gọi định kỳ vào bean:

```xml
<task:scheduler id="reportScheduler" pool-size="2"/>

<task:scheduled-tasks scheduler="reportScheduler">
    <task:scheduled ref="reportService"
                    method="generate"
                    fixed-delay="5000"
                    initial-delay="1000"/>
</task:scheduled-tasks>
```

Đây là **fragment XML**, yêu cầu `reportService` là bean đang có method `generate()` tương thích. `fixed-delay="5000"` tương ứng 5.000 ms sau khi lần trước hoàn thành, `initial-delay="1000"` là 1.000 ms trước lần chạy đầu; cũng có thể khai báo lịch bằng `fixed-rate` hoặc `cron` theo nhu cầu.

Đừng đăng ký *cùng một công việc* hai lần qua cả `@Scheduled` và `<task:scheduled-tasks>` nếu không chủ đích; việc trùng trigger có thể gây xử lý lặp lại.

## 6. XML task namespace khác gì với XML <bean> thông thường?

Ngoài cú pháp rút gọn, có thể khai báo class cụ thể:

```xml
<bean id="workExecutor"
      class="org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor">
    <property name="corePoolSize" value="2"/>
    <property name="maxPoolSize" value="4"/>
    <property name="queueCapacity" value="8"/>
    <property name="threadNamePrefix" value="work-"/>
</bean>
```

Đây là alternative XML **thay thế** `<task:executor id="workExecutor".../>`, không khai báo song song hai bean trùng id trong cùng context.

| Tiêu chí | `@Bean` Java | XML `<task:executor>` | XML `<bean>` |
| --- | --- | --- | --- |
| Ngôn ngữ cấu hình | Java có type checking khi biên dịch | Schema-based XML rút gọn | XML liệt kê class/property |
| Dễ gắn điều kiện/logic | `@Profile`, các conditional configs, Java code | Tùy cấu trúc XML/profile | Tùy cấu trúc XML/profile |
| Tùy chỉnh property đặc thù | Truy cập API setter trực tiếp | Giới hạn vào attributes mà namespace cung cấp | Truy cập bean properties của implementation |
| Tên implementation | Nằm trong mã Java | Được namespace ẩn sau cú pháp | Nằm trong thuộc tính `class` |
| Vòng đời bean | Do Spring container quản lý | Do Spring container quản lý | Do Spring container quản lý |

Với trường hợp cần cấu hình đặc biệt như `taskDecorator` mà `<task:executor>` không biểu diễn trực tiếp, hãy chọn method `@Bean` hoặc XML `<bean>` với những property chính thức được hỗ trợ.

Không kết luận XML là “legacy không chạy nữa”: Spring Framework vẫn hỗ trợ XML bean definitions và `task` namespace; tuy vậy, trong các ứng dụng viết mới, cấu hình Java thường dễ được IDE kiểm tra và refactor hơn.

## 7. Thực nghiệm chạy được: Java config và XML cùng một service

Mục tiêu là **thay cách đăng ký bean nhưng giữ nguyên code nghiệp vụ**. Ví dụ dùng Java 21, Maven và Spring Framework 6.2.1; không yêu cầu Spring Boot. Chương trình có cả một method gửi task trực tiếp vào executor và một method dùng `@Async`.

### 7.1. Cấu trúc

```text
spring-task-config-demo/
├── pom.xml
└── src/main/
    ├── java/demo/TaskConfigDemo.java
    └── resources/task-context.xml
```

**Tệp `pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-task-config-demo</artifactId>
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
          <mainClass>demo.TaskConfigDemo</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

**Tệp `src/main/java/demo/TaskConfigDemo.java`:**

```java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class TaskConfigDemo {

    public static class WorkService {
        private final TaskExecutor executor;

        public WorkService(TaskExecutor executor) {
            this.executor = executor;
        }

        public CompletableFuture<String> submit(String job) {
            CompletableFuture<String> result = new CompletableFuture<>();
            executor.execute(() -> result.complete("processed:" + job));
            return result;
        }

        @Async("workExecutor")
        public CompletableFuture<String> asyncHandle(String job) {
            return CompletableFuture.completedFuture("async:" + job);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class JavaConfig {
        @Bean("workExecutor")
        public ThreadPoolTaskExecutor workExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(4);
            executor.setQueueCapacity(8);
            executor.setThreadNamePrefix("work-");
            return executor;
        }

        @Bean("workScheduler")
        public ThreadPoolTaskScheduler workScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(2);
            scheduler.setThreadNamePrefix("schedule-");
            return scheduler;
        }

        @Bean("workService")
        public WorkService workService(
                @Qualifier("workExecutor") TaskExecutor executor) {
            return new WorkService(executor);
        }
    }

    public static void main(String[] args) throws Exception {
        boolean xml = args.length > 0 && "xml".equals(args[0]);
        String mode = xml ? "xml" : "java";

        try (ConfigurableApplicationContext context = xml
                ? new ClassPathXmlApplicationContext("task-context.xml")
                : new AnnotationConfigApplicationContext(JavaConfig.class)) {
            WorkService service =
                    context.getBean("workService", WorkService.class);

            String result = service.submit("J1")
                    .get(5, TimeUnit.SECONDS);
            String asyncResult = service.asyncHandle("J2")
                    .get(5, TimeUnit.SECONDS);

            System.out.println("mode=" + mode);
            System.out.println("execute=" + result);
            System.out.println("annotation=" + asyncResult);
            System.out.println("executor=" + context.getBean("workExecutor")
                    .getClass().getSimpleName());
            System.out.println("scheduler=" + context.getBean("workScheduler")
                    .getClass().getSimpleName());
        }
    }
}
```

Service có hai đường sử dụng executor: `submit()` gọi `execute()` trực tiếp; `asyncHandle()` dùng annotation và được Spring proxy chuyển sang executor. Khối `main` nhận cùng một interface làm việc bất kể chọn context XML hay Java.

**Tệp `src/main/resources/task-context.xml`:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:task="http://www.springframework.org/schema/task"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/task
           https://www.springframework.org/schema/task/spring-task.xsd">

    <task:executor id="workExecutor"
                   pool-size="2-4"
                   queue-capacity="8"
                   rejection-policy="ABORT"
                   keep-alive="60"/>
    <task:scheduler id="workScheduler" pool-size="2"/>
    <task:annotation-driven
            executor="workExecutor" scheduler="workScheduler"/>

    <bean id="workService" class="demo.TaskConfigDemo$WorkService">
        <constructor-arg ref="workExecutor"/>
    </bean>
</beans>
```

Dấu `$` trong `class="demo.TaskConfigDemo$WorkService"` là cú pháp tên **nested static class** trong JVM; không thay bằng dấu chấm ở chuỗi class XML này. Bean `workService` được tạo qua constructor có tham số `TaskExecutor`, đúng như cách Java config đã tạo.

### 7.2. Chạy bằng Java configuration

Trong thư mục gốc dự án:

```bash
mvn -q -DskipTests compile exec:java
```

**Output chuẩn của chương trình** (không tính thông báo từ Maven/JVM nếu môi trường có thêm log):

```text
mode=java
execute=processed:J1
annotation=async:J2
executor=ThreadPoolTaskExecutor
scheduler=ThreadPoolTaskScheduler
```

Chương trình đã được kiểm chứng bằng Maven và trình biên dịch với `maven.compiler.release=21`.

### 7.3. Chạy bằng XML task namespace

Vẫn giữ nguyên source code service, chỉ truyền một đối số:

```bash
mvn -q -Dexec.args=xml exec:java
```

**Output chuẩn của chương trình:**

```text
mode=xml
execute=processed:J1
annotation=async:J2
executor=ThreadPoolTaskExecutor
scheduler=ThreadPoolTaskScheduler
```

Bản XML cũng đã được chạy kiểm chứng. Tùy phiên bản Spring và cách khởi tạo infrastructure beans, log ứng dụng **có thể xuất hiện cảnh báo BeanPostProcessorChecker** trong lúc đăng ký processor cho `@Async`/`@Scheduled`; ví dụ kiểm chứng với Spring 6.2.1 đã có cảnh báo đó, nhưng các bean vẫn được tạo và tác vụ hoàn thành. Khi vận hành, cần phân biệt warning lúc tạo context với lỗi cấu hình hoặc lỗi thực thi thật sự; không tự giả định mọi warning đều vô hại trong mọi dự án.

Nếu muốn dùng scheduler bean để chạy `@Scheduled` trong **nhánh Java**, cần bổ sung `@EnableScheduling` vào configuration; ở ví dụ này nhánh Java chỉ bật `@EnableAsync` vì không có method `@Scheduled`. Nhánh XML có `<task:annotation-driven executor=... scheduler=.../>` để minh họa cả hai khả năng.

### 7.4. Những gì ví dụ chứng minh

- Java `@Bean` và XML `task` namespace đều đăng ký executor/scheduler theo tên bean thống nhất.
- `WorkService` không biết bean được định nghĩa bằng ngôn ngữ cấu hình nào.
- `@Async("workExecutor")` hoạt động khi annotation processing đã được kích hoạt và method được gọi thông qua Spring-managed proxy.
- `CompletableFuture.get(timeout)` giúp chương trình chờ có giới hạn và kiểm tra kết quả trước khi đóng ApplicationContext.
- Cấu hình executor chỉ giải quyết việc nhận và chạy task trong tiến trình hiện tại; không tự tạo hàng đợi bền vững hoặc đảm bảo task hoàn thành khi process chết.

## 8. Kết nối service qua interface và tên bean

Nếu có nhiều executors, Spring phải biết service nào sử dụng pool nào. Với Java configuration, có thể khai báo:

```java
@Bean
public ExportService exportService(
        @Qualifier("workExecutor") TaskExecutor executor) {
    return new ExportService(executor);
}
```

Đây là fragment; `ExportService` là một service bất kỳ có constructor nhận `TaskExecutor`. Phần `@Qualifier("workExecutor")` chọn bean theo vai trò, không ép kiểu phụ thuộc vào `ThreadPoolTaskExecutor`.

Bản XML có thể truyền dependency bằng constructor:

```xml
<bean id="exportService" class="demo.ExportService">
    <constructor-arg ref="workExecutor"/>
</bean>
```

Lưu ý phải có class `demo.ExportService` trong ứng dụng nếu dùng fragment này; nó chỉ minh họa wiring. Với chỉ một bean phù hợp kiểu `TaskExecutor`, Spring có thể tự chọn; khi có nhiều bean, qualifier/tên rõ ràng giúp tránh ambiguity hoặc vô tình sử dụng pool mặc định.

Một service được viết tốt chỉ gọi `executor.execute(task)` khi cần gửi việc; nó không cần biết `pool-size`, `queue-capacity` hay XML schema. Những cấu hình này thuộc infrastructure.

## 9. @Async, @Scheduled và khác biệt giữa định nghĩa bean với kích hoạt annotation

Có ba thao tác khác nhau:

1. **Đăng ký implementation:** tạo bean `ThreadPoolTaskExecutor` hoặc `ThreadPoolTaskScheduler`.
2. **Đăng ký xử lý annotation:** dùng `@EnableAsync` / `@EnableScheduling` hoặc `<task:annotation-driven>`.
3. **Gắn method với cơ chế:** đặt `@Async` / `@Scheduled` đúng chỗ, hoặc đăng ký `<task:scheduled-tasks>` nếu muốn cấu hình lịch bằng XML.

Thiếu (1), tác vụ có thể dùng executor mặc định/fallback hoặc không đúng giới hạn mong muốn. Thiếu (2), method mang annotation không nhất thiết được xử lý như kỳ vọng. Thiếu (3), bean executor có thể được tạo nhưng không ai gửi công việc cho nó.

### 9.1. Chọn đúng executor cho @Async

Trong Java:

```java
@Async("workExecutor")
public CompletableFuture<String> generatePreview() {
    // Đoạn minh họa method trên Spring-managed bean.
    return CompletableFuture.completedFuture("done");
}
```

Method cần được gọi từ client đi qua Spring proxy. Việc đặt tên trong `@Async("workExecutor")` giúp rõ bean được dùng. Trong XML, cũng có thể đặt `executor="workExecutor"` trên `<task:annotation-driven>` để chọn executor mặc định cho annotation không ghi tên cụ thể.

Một lỗi phổ biến là nghĩ `@Async` tự biến JDBC/HTTP blocking thành nonblocking I/O: lời gọi blocking vẫn chiếm worker của executor trong khoảng chờ; thay đổi chỉ chuyển điểm chặn sang thread khác.

### 9.2. Chọn scheduler cho @Scheduled

Scheduler quyết định lịch kích hoạt; nó cũng có pool size và lifecycle. Nếu có quá nhiều job chậm trên scheduler một thread, các lần chạy khác có thể bị trì hoãn. Không dùng scheduler bean chỉ để chạy một `Runnable` một lần như executor thông thường trừ khi thiết kế thực sự cần một thời điểm kích hoạt.

Cần nhớ rằng trong hệ thống nhiều instance, mỗi instance có ApplicationContext và có thể có lịch chạy riêng. Nếu nghiệp vụ chỉ muốn một instance thực hiện, cần coordination hoặc distributed locking phù hợp; XML/annotation đơn lẻ không cung cấp leader election.

## 10. Quản trị cấu hình theo môi trường

### 10.1. Java @Profile

Có thể thay bean theo profile:

```java
@Bean("workExecutor")
@Profile("dev")
public TaskExecutor developmentExecutor() {
    return new SyncTaskExecutor();
}

@Bean("workExecutor")
@Profile("!dev")
public ThreadPoolTaskExecutor productionExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(100);
    return executor;
}
```

Đây là fragment cần imports `@Profile`, `TaskExecutor`, `SyncTaskExecutor`, `ThreadPoolTaskExecutor` và chứa trong lớp cấu hình. Hai method tạo cùng tên bean nhưng chỉ một profile branch nên được kích hoạt trong một context theo điều kiện. Lưu ý `SyncTaskExecutor` chạy công việc **ngay trên thread gọi**, nên hành vi timing khác hoàn toàn. Không nên mặc định test đồng bộ mô phỏng đầy đủ race conditions của production.

### 10.2. XML profile

XML cho phép dùng `profile` trong cấu trúc bean definitions, nhưng phải tôn trọng quy tắc schema, phạm vi context và đặt tên bean không trùng khi profile khác nhau. Ví dụ một fragment mô tả bean theo môi trường:

```xml
<beans profile="dev">
    <bean id="workExecutor"
          class="org.springframework.core.task.SyncTaskExecutor"/>
</beans>
<beans profile="prod">
    <task:executor id="workExecutor"
                   pool-size="4-8"
                   queue-capacity="100"/>
</beans>
```

Đây là **fragment đặt bên trong root `<beans>` phù hợp**, không phải một XML file hoàn chỉnh. Nếu muốn dùng `<task:executor>` trong nested `<beans>`, cần xác nhận schema và version cho phép cách cấu trúc đó. Cũng có thể tách nhiều XML config files rồi chọn file cần import theo môi trường. Luôn kiểm tra thực tế bean nào có trong context sau khi profile được bật.

### 10.3. Phân biệt Spring Framework và Spring Boot

Spring Boot có các cơ chế **auto-configuration** và properties như `spring.task.execution.pool.core-size`, `spring.task.execution.pool.max-size` và `spring.task.execution.pool.queue-capacity` theo phiên bản Boot. Các properties này chỉ áp dụng khi auto-configured executor tương ứng đang được sử dụng; không nên giả định một `@Bean("workExecutor")` do ứng dụng tự tạo sẽ tự nhận cấu hình pool từ Boot.

Nếu ứng dụng chỉ dùng Spring Framework, các method `@Bean` và XML `task` namespace ở trên hoạt động mà **không cần** Spring Boot hay `application.properties`. Tập trung vào các bean thực sự được tạo trong ApplicationContext.

## 11. Lưu ý về lỗi cấu hình, quá tải và vận hành

### 11.1. Tên bean và ambiguity

Đặt tên bean rõ chức năng: `emailExecutor`, `reportExecutor`, `scheduledJobs`. Đừng đặt tên chung như `executor` rồi tạo nhiều bean cùng kiểu mà không xác định injection point. Trùng bean name giữa Java configuration và XML nhập vào cùng một context có thể gây conflict, bị override hoặc bị chặn tùy thiết lập; tránh khai báo lặp cùng tên khi cả hai đường cấu hình cùng được load.

### 11.2. Quá tải, queue và rejection

`ThreadPoolTaskExecutor` có pool và queue giới hạn riêng. Khi queue và worker đều bận, mặc định tác vụ có thể bị từ chối (Spring biểu diễn bằng `TaskRejectedException`). Một `execute()` trả về bình thường không bảo đảm nội dung task đã xử lý thành công; với bất đồng bộ, exception bên trong worker có đường quan sát riêng.

`rejection-policy="CALLER_RUNS"` là một lựa chọn điều tiết, nhưng có thể làm thread gọi chặn dài; nếu request server đang dùng event loop hoặc bị giới hạn số worker, cần đánh giá trước khi dùng. Các policy discard có thể đánh mất việc và không phù hợp với tác vụ bắt buộc hoàn tất.

### 11.3. Timeout và context propagation

Tác vụ chạy ở thread khác có thể không mang theo ThreadLocal, MDC, security context hoặc transaction context từ thread gọi. Cần truyền input cần thiết rõ ràng; dùng task decorator hoặc cơ chế propagation phù hợp nếu muốn mang theo metadata quan sát.

Nếu task phụ thuộc database/network, cấu hình timeout tại client/dependency, kiểm tra connection pool và theo dõi queue wait; tăng threads/queue không thay thế tối ưu bottleneck. Khi method `@Async` trả về `Future`, cần thu nhận exception hoặc xử lý kết quả đúng cách. Với method `void`, lỗi không tự được trả về caller.

### 11.4. Shutdown và môi trường chạy

Khi context đóng, threads và tasks đang chạy chịu chính sách shutdown của từng implementation. Kiểm tra cài đặt await termination, accept-after-close và phương án không mất tác vụ quan trọng. Không nên dùng in-memory executor như hệ thống hàng đợi bền vững cho nghiệp vụ cần replay sau restart.

### 11.5. Kiểm chứng thực tế

Sau khi chọn cách cấu hình, hãy kiểm tra:

- Bean có tên và kiểu đúng trong ApplicationContext; không bị hai nhánh Java/XML cùng định nghĩa.
- `corePoolSize`, `maxPoolSize`, `queueCapacity`, số scheduler workers và thread name prefix thực sự khớp mong đợi.
- Method `@Async`/`@Scheduled` đã kích hoạt annotation processing và lời gọi đi đúng qua proxy.
- Dưới tải, quan sát `activeCount`, `queueSize`, rejection, throughput, task execution time, p95/p99 wait time và memory.
- Bảo đảm dừng context không bỏ mặc threads và không giả định công việc trong queue là durable.

## 12. Các lỗi thường gặp và cách sửa

| Lỗi | Hậu quả | Cách kiểm tra/sửa |
| --- | --- | --- |
| Chỉ có `@Bean executor` nhưng kỳ vọng `@Async` chạy | Annotation không được xử lý như mong muốn | Bật `@EnableAsync` hoặc `<task:annotation-driven>` và gọi qua proxy |
| Chỉ tạo scheduler rồi kỳ vọng `@Scheduled` hoạt động | Job không được đăng ký theo annotation | Bật `@EnableScheduling` hoặc XML annotation-driven |
| Viết XML thiếu namespace/schema | Context không parse được | Kiểm tra `xmlns:task` và cặp `schemaLocation` |
| Viết `pool-size="2-4"` nhưng hiểu là luôn có 4 workers | Sai ước lượng concurrency | Phân biệt core/max; chú ý queue-capacity |
| Để queue rất lớn và chỉ tăng maxPoolSize | Task chờ lâu, pool hiếm khi mở rộng | Đo queue wait và cân chỉnh cùng core, max, queue |
| Nhập cả Java config và XML có bean trùng id | Xung đột/ghi đè definition | Chọn đường cấu hình rõ ràng, kiểm tra bean registry |
| `@Async` self-invocation trong cùng bean | Không đi qua proxy mặc định | Gọi từ Spring-managed client khác |
| Dùng `TaskExecutor` cho scheduling | Thiếu semantics thời gian kích hoạt | Dùng `TaskScheduler` và `@Scheduled`/scheduled-tasks |
| Tự tạo executor bằng `new` trong service rồi quên shutdown | Rò rỉ threads/tài nguyên | Đưa executor thành Spring-managed bean |
| Coi task đã submit là task đã thành công | Bỏ sót worker exception | Dùng Future/CompletionStage, quan sát error/rejection |
| Tin XML namespace tự áp dụng mọi feature của implementation | Mất các tùy chỉnh đặc thù | Dùng `@Bean` hoặc `<bean>` cho property không được namespace hỗ trợ |

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Có `ThreadPoolTaskExecutor` bean tên `workExecutor`, nhưng method `@Async("workExecutor")` chạy đồng bộ khi gọi từ cùng bean. Điều gì cần kiểm tra?

Hãy kiểm tra `@EnableAsync`/`<task:annotation-driven>` đã có chưa; đặc biệt kiểm tra lời gọi có đang đi qua `this.method()` và bỏ qua Spring proxy hay không. Kiểm tra tiếp loại executor được tiêm và có dùng `SyncTaskExecutor` không.

**Câu 2.** XML `<task:executor pool-size="3-9" queue-capacity="100">` có tự tạo 9 thread ngay khi khởi động không?

Không. Nó cấu hình core/max worker và queue capacity; core threads thường được tạo dần khi có task, và pool chỉ mở rộng ngoài core khi queue không nhận thêm. Cần chạy tải và quan sát thực tế.

**Câu 3.** Ứng dụng vừa có `@Bean("workExecutor")` vừa import XML khai báo `<task:executor id="workExecutor">`. Có nên giữ cả hai không?

Không nên khai báo hai nguồn chủ sở hữu cùng một bean name trong một ApplicationContext, trừ khi có cơ chế override được thiết kế rõ ràng. Chọn một nguồn cấu hình hoặc tổ chức các profile/files để mỗi context chỉ có một definition hiệu lực.

**Câu 4.** Có thể thay `ThreadPoolTaskExecutor` bằng `SyncTaskExecutor` trong test mà không sửa service không?

Có, nếu service phụ thuộc interface `TaskExecutor`. Tuy nhiên test synchronous chỉ kiểm chứng logic gửi và thực hiện theo thứ tự; concurrency issues thực tế vẫn cần test riêng.

**Câu 5.** Tại sao trong XML ví dụ có cả `<task:scheduler>` lẫn `<task:annotation-driven scheduler="...">`?

Phần tử thứ nhất **tạo tài nguyên scheduler**; phần tử thứ hai **đăng ký cơ chế dùng scheduler đó để xử lý annotation**. Cần tách rõ tạo bean và kích hoạt tính năng.

## 14. Tóm tắt thực hành

- **Java configuration:** dùng `@Bean` để tạo `ThreadPoolTaskExecutor`/`ThreadPoolTaskScheduler`; bật `@EnableAsync` và/hoặc `@EnableScheduling` khi dùng annotation.
- **XML task namespace:** `<task:executor>`, `<task:scheduler>` định nghĩa tài nguyên; `<task:annotation-driven>` kích hoạt xử lý `@Async`/`@Scheduled`; `<task:scheduled-tasks>` có thể khai báo lịch mà không cần annotation.
- **DI bằng interface:** business service phụ thuộc `TaskExecutor`/`TaskScheduler`, để Java config hay XML quyết định implementation, sizing và lifecycle.
- **Không bỏ qua vận hành:** thread pool size, queue-capacity, policy từ chối, shutdown, trạng thái proxy và monitoring phải được xác minh trên bean thực tế.

### Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework 6.2 — ThreadPoolTaskExecutor API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework 6.2 — ThreadPoolTaskScheduler API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html)
- [Spring Framework 6.2 — @EnableAsync API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-008">Đưa tham số ra cấu hình ngoài và kiểm tra giá trị không hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Đưa tham số nhóm luồng ra cấu hình ngoài và kiểm tra giá trị không hợp lệ

Trong Spring Framework, cấu hình tài nguyên cho `ThreadPoolTaskExecutor` quyết định cách ứng dụng nhận và xử lý tác vụ: số worker cơ bản, giới hạn worker, dung lượng hàng đợi, thời gian giữ worker nhàn rỗi và hành vi khi quá tải. Những giá trị này thường cần thay đổi giữa máy phát triển, môi trường kiểm thử và production. Nếu chúng bị viết cứng rải rác trong mã Java, việc tinh chỉnh dễ tạo sai lệch và khó truy vết.

Bài này hướng dẫn hai mục tiêu liên quan:

1. **Externalize configuration**: lưu giá trị ngoài phần logic tạo executor; đọc chúng qua cơ chế cấu hình của Spring thay vì thay mã nguồn mỗi lần chỉnh tài nguyên.
2. **Validate trước khi dùng**: kiểm tra kiểu, khoảng giá trị, điều kiện giữa các trường và ý nghĩa vận hành; nếu cấu hình sai, báo lỗi rõ ràng khi khởi động thay vì chạy với tham số bất hợp lý.

Phần ví dụ chính dùng **Java 17+, Spring Framework 6.x và Spring Boot 3.x**. `@ConfigurationProperties` thuộc **Spring Boot**, không phải một tính năng tự động có sẵn của mọi ứng dụng dùng Spring Framework thuần. Phần cuối có ví dụ `Environment`/`@PropertySource` cho Spring Framework không dùng Boot.

## 1. Vì sao cần đưa tham số executor ra ngoài?

Xét cấu hình viết cứng:

~~~java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(4);
executor.setMaxPoolSize(8);
executor.setQueueCapacity(64);
executor.setKeepAliveSeconds(60);
~~~

Đây là **đoạn mã minh họa**, chưa phải chương trình Spring hoàn chỉnh. Nó hoạt động, nhưng không thể đổi profile hoặc cấu hình triển khai mà không sửa cách tạo bean. Tệ hơn, nếu mỗi executor đọc giá trị riêng theo các cách khác nhau, ứng dụng khó kiểm soát tính nhất quán.

Một mô hình rõ ràng hơn:

~~~text
application.yml / file bên ngoài / biến môi trường / CLI
                         |
                         v
                Spring Environment
                         |
                         v
               Bind vào PoolProperties
                         |
                         v
                Validate kiểu + giới hạn
                         |
           +-------------+-------------+
           |                           |
         hợp lệ                    không hợp lệ
           |                           |
           v                           v
     Tạo executor                Báo lỗi khởi động
           |
           v
     @Async / TaskExecutor
~~~

**Cấu hình ngoài không đồng nghĩa với cập nhật nóng.** Trong các ví dụ dưới đây, các giá trị được đọc lúc tạo bean. Sửa file hoặc biến môi trường sau khi ứng dụng chạy **không tự động** thay đổi pool hiện tại. Muốn cập nhật động cần cơ chế reload riêng, kiểm tra lại điều kiện và hiểu tham số nào có thể thay đổi khi executor đã khởi tạo.

## 2. Nhận diện chính xác những tham số cần kiểm tra

| Thuộc tính | Ý nghĩa | Quy tắc có thể áp dụng cho ứng dụng ví dụ |
| --- | --- | --- |
| `corePoolSize` | Số worker lõi; pool thường ưu tiên tạo worker tới ngưỡng này khi nhận task | Số nguyên từ 1 đến 32 |
| `maxPoolSize` | Số worker tối đa khi cần mở rộng | Số nguyên từ 1 đến 64 **và không nhỏ hơn core** |
| `queueCapacity` | Số task chờ được chứa trước khi tính tới mở thêm worker | Số nguyên từ 0 đến 2.000 |
| `keepAliveSeconds` | Thời gian nhàn rỗi cho worker vượt core trước khi bị thu hồi | Số nguyên từ 1 đến 600 |
| `threadNamePrefix` | Tiền tố tên worker để dễ quan sát thread dump/log | Chuỗi không trống hoặc chỉ có khoảng trắng |
| `RejectedExecutionHandler` | Cách xử lý khi pool không còn nhận được task | Chọn một chính sách có chủ đích, không nên chọn tùy tiện qua chuỗi bất kỳ |

**Các ngưỡng 32, 64, 2.000 và 600 là chính sách của ví dụ, không phải giới hạn chuẩn của Spring hay JVM.** Dự án thật chọn chúng từ CPU quota, bộ nhớ, tốc độ tác vụ, độ trễ dịch vụ phụ thuộc và yêu cầu vận hành.

Có ít nhất ba lớp hợp lệ cần phân biệt:

- **Hợp lệ về kiểu:** `corePoolSize: abc` không chuyển thành số nguyên được; `queueCapacity: 999999999999999999` vượt phạm vi `int`.
- **Hợp lệ về miền và quan hệ:** `corePoolSize=0` bị cấm theo chính sách của ví dụ; `maxPoolSize < corePoolSize` mâu thuẫn; `queueCapacity < 0` không được ứng dụng này chấp nhận.
- **Hợp lý về năng lực vận hành:** `maxPoolSize=64` có thể hợp lệ theo annotation nhưng vẫn quá cao với database pool chỉ có 10 kết nối. Validation tĩnh **không thay thế thử tải và capacity planning**.

Lưu ý ở mức API: với `ThreadPoolTaskExecutor`, `queueCapacity > 0` tạo `LinkedBlockingQueue`; giá trị 0 tạo `SynchronousQueue`, nghĩa là bàn giao trực tiếp, **không giữ task trong hàng đợi**. Vì vậy việc cấm `queueCapacity=0` là một quyết định sản phẩm, không phải luật bắt buộc của Spring.

## 3. Cách làm được khuyến nghị trong Spring Boot: @ConfigurationProperties

`@ConfigurationProperties` gom các khóa có chung prefix vào một Java object có kiểu rõ ràng. Spring Boot bind dữ liệu từ `Environment` rồi, khi bean được đánh dấu `@Validated` và có Bean Validation provider, kiểm tra các ràng buộc `jakarta.validation`.

Ưu điểm so với đặt nhiều `@Value` rời rạc: cùng một chỗ mô tả các thuộc tính, dễ tái sử dụng, dễ kiểm thử lỗi cấu hình, hỗ trợ relaxed binding và metadata cho IDE.

### 3.1. Bước 1 — Khai báo application.yml

Ví dụ tệp `src/main/resources/application.yml`:

~~~yaml
app:
  executor:
    io:
      core-pool-size: ${APP_IO_CORE_POOL_SIZE:4}
      max-pool-size: ${APP_IO_MAX_POOL_SIZE:8}
      queue-capacity: ${APP_IO_QUEUE_CAPACITY:64}
      keep-alive-seconds: ${APP_IO_KEEP_ALIVE_SECONDS:60}
      thread-name-prefix: ${APP_IO_THREAD_NAME_PREFIX:io-}
~~~

Cú pháp `${ENV_NAME:default}` có nghĩa: dùng biến hoặc property tên `ENV_NAME` trong `Environment` nếu tìm thấy, nếu không dùng giá trị sau dấu `:`. Giá trị mẫu nằm trong YAML nên ứng dụng vẫn có cấu hình rõ ràng khi chạy local.

Ta đã chủ động đặt tên biến môi trường riêng, ví dụ `APP_IO_CORE_POOL_SIZE`. **Đừng nhầm các tên placeholder này với phép biến đổi tự động của relaxed binding**. Trong relaxed binding trực tiếp, tên canonical `app.executor.io.core-pool-size` được chuyển sang dạng biến môi trường bằng cách thay dấu chấm bằng gạch dưới, **loại bỏ dấu gạch nối** và viết hoa, tức `APP_EXECUTOR_IO_COREPOOLSIZE`. Với placeholder trong YAML, ta có thể dùng tên khác miễn nhất quán.

Ví dụ chạy trên shell Unix:

~~~bash
APP_IO_CORE_POOL_SIZE=6 APP_IO_MAX_POOL_SIZE=12 java -jar app.jar
~~~

Hoặc truyền trực tiếp property của Spring Boot:

~~~bash
java -jar app.jar --app.executor.io.core-pool-size=6 --app.executor.io.max-pool-size=12
~~~

Trên Windows PowerShell, có thể đặt các biến môi trường cho tiến trình shell hiện tại bằng `$env:APP_IO_CORE_POOL_SIZE = "6"` và `$env:APP_IO_MAX_POOL_SIZE = "12"` rồi chạy `java -jar app.jar`.

Các ví dụ lệnh giả định `app.jar` là một file Spring Boot executable JAR đã được build; không phải tên JAR cố định của mọi dự án.

### 3.2. Bước 2 — Có Bean Validation provider trên classpath

Trong dự án Gradle Spring Boot 3.x, cần một implementation của Jakarta Bean Validation, thường được cung cấp bởi starter:

~~~groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-validation'
}
~~~

Đây là **đoạn build.gradle minh họa**: dự án phải đã áp dụng cơ chế dependency/version management tương thích Spring Boot. Nếu chỉ thêm `@Validated` mà không có validator phù hợp, không được mặc nhiên cho rằng các annotation `@Min`, `@Max`, `@AssertTrue` sẽ thực thi như dự kiến. Hãy kiểm tra dependency và behavior bằng một trường hợp cấu hình sai.

### 3.3. Bước 3 — Định nghĩa record chứa cấu hình và validation

Tệp `IoExecutorProperties.java`:

~~~java
package example.concurrency;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(
        prefix = "app.executor.io",
        ignoreUnknownFields = false
)
public record IoExecutorProperties(
        @Min(1) @Max(32) int corePoolSize,
        @Min(1) @Max(64) int maxPoolSize,
        @Min(0) @Max(2000) int queueCapacity,
        @Min(1) @Max(600) int keepAliveSeconds,
        @NotBlank String threadNamePrefix
) {
    @AssertTrue(message = "max-pool-size phải >= core-pool-size")
    public boolean isMaxAtLeastCore() {
        return maxPoolSize >= corePoolSize;
    }
}
~~~

Đây là **mã tích hợp Spring Boot 3.x**, cần có dependencies Spring Boot và Jakarta Validation. Java record cho phép constructor binding theo các component mà không cần khai báo setter. Spring Boot nhận các khóa kebab-case như `max-pool-size` và ánh xạ đến `maxPoolSize`.

Các ràng buộc làm gì?

- `@Min`/`@Max`: kiểm tra giá trị sau khi bind đã nằm trong miền hỗ trợ của ví dụ. Với tham số kiểu `int` bị thiếu, giá trị mặc định 0 sẽ bị `@Min(1)` bắt ở những trường yêu cầu tối thiểu 1. Tốt hơn vẫn là quản lý khóa bắt buộc hoặc default rõ ràng.
- `@NotBlank`: chặn tiền tố tên luồng `null`, rỗng hoặc chỉ chứa whitespace.
- `@AssertTrue` trên phương thức getter Boolean `isMaxAtLeastCore()`: kiểm tra điều kiện **liên trường** mà các annotation độc lập không thể diễn đạt.
- `ignoreUnknownFields=false`: yêu cầu binder báo lỗi khi bắt gặp thuộc tính ngoài cấu trúc đã định nghĩa trong phạm vi prefix được bind, hữu ích để phát hiện typo. Vẫn cần kiểm thử để phát hiện các property đặt sai prefix hoàn toàn hoặc nguồn cấu hình không được đọc.

`@Validated` đến từ Spring (`org.springframework.validation.annotation.Validated`). Các constraint đến từ `jakarta.validation.constraints`, không phải `javax.validation` của các thế hệ Spring Boot cũ.

Trong ứng dụng thật, có thể chọn tên property và nhóm theo workload như `io`, `cpu`, `reports`, nhưng chỉ tạo nhiều pool khi có lý do giới hạn tài nguyên riêng; quá nhiều executor khiến việc tuning và giám sát khó khăn hơn.

### 3.4. Bước 4 — Tạo ThreadPoolTaskExecutor bằng các giá trị đã bind

Tệp `ExecutorConfiguration.java`:

~~~java
package example.concurrency;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableConfigurationProperties(IoExecutorProperties.class)
public class ExecutorConfiguration {

    @Bean("ioExecutor")
    public ThreadPoolTaskExecutor ioExecutor(IoExecutorProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setKeepAliveSeconds(properties.keepAliveSeconds());
        executor.setThreadNamePrefix(properties.threadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
~~~

Phần này là **bean Spring**, không phải chương trình độc lập chạy bằng `javac`. `@EnableConfigurationProperties(IoExecutorProperties.class)` đăng ký properties record để Boot bind/validate; `@EnableAsync` bật cơ chế `@Async`. Bean `ThreadPoolTaskExecutor` được Spring quản lý, bao gồm quá trình gọi lifecycle initialization. Vì vậy ví dụ không cần gọi `initialize()` thủ công ngay trong factory method.

Có chủ ý chọn `AbortPolicy` để quá tải sinh tín hiệu rejection rõ ràng. Đây **không phải chính sách an toàn duy nhất**; có thể dùng chính sách khác nếu đã đánh giá cách tác động tới thread gọi, retry và yêu cầu không mất tác vụ. Nếu policy là lựa chọn cần externalize, hãy bind thành **enum với tập giá trị cho phép**, sau đó ánh xạ enum sang một tập `RejectedExecutionHandler` được kiểm soát; không nhận tên class tùy ý từ config để tạo bằng reflection.

### 3.5. Bước 5 — Dùng đúng executor cho @Async

Tệp `FileExportService.java`:

~~~java
package example.concurrency;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class FileExportService {

    @Async("ioExecutor")
    public CompletableFuture<String> export(String fileId) {
        // Công việc minh họa; I/O thật cần timeout và kiểm soát lỗi.
        return CompletableFuture.completedFuture("exported:" + fileId);
    }
}
~~~

Một bean khác gọi `fileExportService.export("file-7")` qua Spring proxy sẽ submit công việc vào `ioExecutor`. Các tham số được nạp từ properties record lúc tạo executor. Lời gọi bên trong cùng instance (`this.export(...)`) thường **bỏ qua proxy** ở chế độ `@Async` mặc định, làm công việc không được chuyển sang pool như dự kiến.

Nếu executor đã đầy, lỗi có thể phát sinh ngay khi **submit**; nếu task đã nhận nhưng chạy thất bại, kết quả tương ứng có thể hoàn thành theo trạng thái thất bại. Caller cần phân biệt hai dạng lỗi. Không nghĩ rằng khai báo `CompletableFuture` làm việc gửi task luôn thành công.

## 4. Validation được thực hiện khi nào?

Đối với thiết kế trong mục 3, tiến trình thường là:

~~~text
Khởi động ApplicationContext
          |
      Thu thập config
          |
      Bind giá trị có kiểu
          |
      Chạy Bean Validation
          |
  +-------+-------------+
  |                     |
 Hợp lệ               Sai/không bind được
  |                     |
  v                     v
 Tạo bean executor    Khởi động thất bại
  |
  v
 Bắt đầu nhận task
~~~

Nếu có giá trị `max-pool-size: 3` và `core-pool-size: 8`, giá trị riêng từng trường có thể nằm trong khoảng hợp lệ nhưng **quan hệ giữa chúng sai**; `@AssertTrue` cho thông báo xác định điều kiện bị vi phạm. Nếu số `core-pool-size` thành chữ `many`, binding vào `int` sẽ lỗi từ tầng chuyển kiểu **trước hoặc trong quá trình bind**, không cần chờ kiểm tra `@Min`.

Thông báo lỗi cụ thể, tên exception và thứ tự các chi tiết hiển thị tùy phiên bản Boot, Validation provider và đường khởi tạo bean. **Không nên bắt/nuốt lỗi validation rồi tự đổi thành con số ngẫu nhiên** để buộc ứng dụng chạy. Với thiết lập tài nguyên sai, fail-fast thường giúp giảm rủi ro sự cố khó chẩn đoán ở runtime.

### 4.1. Bảng trường hợp hợp lệ và không hợp lệ

Giả sử giữ `queueCapacity=64`, `keepAliveSeconds=60`, `threadNamePrefix=io-` trừ khi ghi khác:

| Input | Dự kiến | Nguyên nhân |
| --- | --- | --- |
| core=4, max=8 | **PASS** | Hai số trong miền và max ≥ core |
| core=0, max=8 | **FAIL** | Vi phạm `@Min(1)` của core |
| core=12, max=8 | **FAIL** | Vi phạm quan hệ `max ≥ core` |
| core=4, max=0 | **FAIL** | `@Min(1)` của max; đồng thời max < core |
| queue=-1 | **FAIL** | Chính sách ví dụ chỉ chấp nhận queue ≥ 0 |
| queue=0 | **PASS** | Là cấu hình bàn giao trực tiếp bằng `SynchronousQueue` |
| queue=5000 | **FAIL** | Vượt `@Max(2000)` của ví dụ |
| keep-alive-seconds=0 | **FAIL** | Ví dụ bắt buộc ≥ 1 giây |
| thread-name-prefix chỉ có dấu cách | **FAIL** | `@NotBlank` |
| core=abc | **FAIL** | Không bind được chuỗi thành `int` |
| core không được cung cấp trong mọi source và không có default | **FAIL theo mẫu này** | Primitive `int` nhận 0, vi phạm `@Min(1)` |

Trường hợp không có default cần hiểu đúng: nếu `application.yml` còn giá trị mặc định từ mục 3, việc **không đặt biến môi trường** không gây lỗi, vì binder vẫn nhận số mặc định từ file. Muốn property bắt buộc phải có giá trị triển khai, không đặt default trong config, dùng kiểu biểu diễn thiếu vắng rõ ràng và validation tương ứng, và kiểm tra khởi động không có biến môi trường.

### 4.2. Cross-field validation bằng @AssertTrue và phương án khác

Các annotation trên từng trường chỉ bảo đảm mỗi giá trị riêng lẻ nằm trong miền. Chúng không tự hiểu quan hệ `maxPoolSize ≥ corePoolSize`. Với quan hệ đơn giản, getter `isMaxAtLeastCore()` mang `@AssertTrue` đủ dễ đọc.

Khi có nhiều quan hệ phức tạp — ví dụ budget tổng các pool hoặc quy tắc phụ thuộc môi trường — có thể dùng class-level custom constraint/validator, hoặc một phương thức kiểm tra tập trung trước lúc tạo bean. Mục tiêu là báo lỗi **gắn với tên tham số và điều kiện sai**, không tạo ra một loạt validation rời rạc khó bảo trì.

Không chuyển mọi quy tắc vận hành thành `@Max` bất biến: ngưỡng cho workload có thể thay đổi khi tăng CPU quota, DB capacity và SLA. Hãy giữ miền an toàn ở properties, còn lựa chọn giá trị thực tế phải được đo đạc.

## 5. Java 17 độc lập: kiểm tra cấu hình mà không cần chạy Spring

Ví dụ sau mô phỏng **bước parse và kiểm tra tính nhất quán** bằng record Java 17. Nó không thay thế Bean Validation của Spring Boot, nhưng cho thấy vì sao cần cả kiểm tra kiểu lẫn kiểm tra giữa các tham số.

Tạo tệp `PoolSettingsValidationDemo.java`:

~~~java
public class PoolSettingsValidationDemo {
    record PoolSettings(int core, int max, int queue) {
        public PoolSettings {
            if (core < 1) {
                throw new IllegalArgumentException("core must be >= 1");
            }
            if (max < core) {
                throw new IllegalArgumentException("max must be >= core");
            }
            if (queue < 0) {
                throw new IllegalArgumentException("queue must be >= 0");
            }
        }
    }

    static void check(String core, String max, String queue) {
        try {
            PoolSettings settings = new PoolSettings(
                    Integer.parseInt(core),
                    Integer.parseInt(max),
                    Integer.parseInt(queue));

            System.out.printf("VALID core=%d max=%d queue=%d%n",
                    settings.core(), settings.max(), settings.queue());
        } catch (NumberFormatException e) {
            System.out.println("INVALID: value must be integer");
        } catch (IllegalArgumentException e) {
            System.out.println("INVALID: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        check("4", "8", "64");
        check("0", "8", "64");
        check("12", "8", "64");
        check("4", "8", "-1");
        check("x", "8", "64");
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 PoolSettingsValidationDemo.java
java PoolSettingsValidationDemo
~~~

Kết quả xác định:

~~~text
VALID core=4 max=8 queue=64
INVALID: core must be >= 1
INVALID: max must be >= core
INVALID: queue must be >= 0
INVALID: value must be integer
~~~

Giải thích: `Integer.parseInt(...)` bắt lỗi kiểu trước; compact constructor của `record` kiểm tra miền và quan hệ, sau đó mới có object cấu hình hợp lệ. `NumberFormatException` là subclass của `IllegalArgumentException` nên khối catch cho lỗi số phải đứng **trước** khối catch tổng quát. Các trường hợp được kiểm chứng với JDK hỗ trợ record; ví dụ trong Spring ở mục 3 tương đương về ý tưởng, nhưng dùng binder và Jakarta Bean Validation thay vì tự parse từng chuỗi.

Nếu input như `" 4 "` chưa được chuẩn hóa, `Integer.parseInt` thuần Java sẽ báo lỗi thay vì tự trim. Spring Boot binder có quy tắc chuyển kiểu riêng, vì vậy không suy diễn hành vi whitespace của Java thuần sang mọi property source.

## 6. Spring Framework thuần: đọc Environment và kiểm tra thủ công

Không sử dụng Spring Boot thì **không mặc nhiên có application.yml auto-load và binder @ConfigurationProperties như mục 3**. Spring Framework core vẫn có `Environment`, `@PropertySource`, `@Value` và cơ chế validation nhưng ứng dụng phải cấu hình nguồn và bước validate phù hợp.

Tạo tệp `executor.properties` trên classpath:

~~~properties
app.executor.core=4
app.executor.max=8
app.executor.queue=64
app.executor.keep-alive=60
~~~

Ví dụ class Spring Framework tích hợp:

~~~java
package example.concurrency;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@PropertySource("classpath:executor.properties")
public class PlainSpringExecutorConfig {

    @Bean("ioExecutor")
    public ThreadPoolTaskExecutor ioExecutor(Environment environment) {
        int core = environment.getRequiredProperty(
                "app.executor.core", Integer.class);
        int max = environment.getRequiredProperty(
                "app.executor.max", Integer.class);
        int queue = environment.getRequiredProperty(
                "app.executor.queue", Integer.class);
        int keepAlive = environment.getRequiredProperty(
                "app.executor.keep-alive", Integer.class);

        if (core < 1 || max < core || queue < 0 || keepAlive < 1) {
            throw new IllegalArgumentException(
                    "Invalid executor config: require core >= 1, " +
                    "max >= core, queue >= 0, keepAlive >= 1");
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setKeepAliveSeconds(keepAlive);
        executor.setThreadNamePrefix("io-");
        return executor;
    }
}
~~~

Đây là **mã tích hợp**: cần Spring Context, đăng ký/scan cấu hình và resource `executor.properties`; không chạy chỉ bằng một lệnh `javac`. Nếu thiếu khóa, `getRequiredProperty` báo lỗi; nếu không thể chuyển thành số nguyên, conversion báo lỗi; nếu sai quan hệ, phương thức `@Bean` ném `IllegalArgumentException` khiến bean không khởi tạo thành công.

Ví dụ cho thấy hai lớp khác nhau:

- **Spring Framework core:** cung cấp trừu tượng property source và environment để ứng dụng tự đọc, tự dựng bean và tự quyết định cách validate.
- **Spring Boot:** ngoài các cơ chế nền tảng trên còn cung cấp config data, relaxed binding, `@ConfigurationProperties`, tích hợp validation và thứ tự override cấu hình để chuẩn hóa quy trình.

Không phải mọi ứng dụng Spring Framework đều sử dụng Spring Boot. Tài liệu, mã và cách cấu hình cần ghi rõ đang dựa trên lớp nào.

## 7. Thứ tự ưu tiên nguồn cấu hình và profile

### 7.1. Nguồn cấu hình có thể ghi đè nhau

Trong Spring Boot, `application.yml` nằm trong JAR, `application-{profile}.yml`, file cấu hình bên ngoài JAR, biến môi trường, system properties và command-line arguments đều có thể cung cấp cấu hình. Boot có **quy tắc ưu tiên của PropertySource**, vì vậy hai nguồn cùng khai báo một khóa sẽ không có nghĩa cả hai cùng được dùng.

Trước khi kết luận ứng dụng “không nhận cấu hình”, phải xác định **giá trị hiệu lực cuối cùng** và nguồn đang thắng. Ví dụ CLI `--app.executor.io.max-pool-size=12` có thể ghi đè giá trị trong `application.yml` theo thứ tự mặc định của Boot. Đừng khẳng định một thứ tự ưu tiên chung cho mọi dự án nếu chúng đã custom `PropertySources` hoặc tắt xử lý command-line properties.

### 7.2. Profile thay đổi giá trị, không thay đổi quy tắc validate

Tệp profile ví dụ `application-prod.yml`:

~~~yaml
app:
  executor:
    io:
      core-pool-size: 8
      max-pool-size: 16
      queue-capacity: 128
~~~

Các khóa khác vẫn lấy từ cấu hình cơ sở nếu không có nguồn ưu tiên cao hơn ghi đè. Các constraint của `IoExecutorProperties` **vẫn được áp dụng** sau khi bind giá trị cuối cùng; profile không phải lý do để bỏ qua kiểm tra tính hợp lệ.

Cần lưu ý việc bật profile bằng `spring.profiles.active=prod`, biến môi trường hoặc tham số CLI phải phù hợp cơ chế deployment; không giả định file có tên `application-prod.yml` sẽ được nạp nếu profile `prod` chưa active.

### 7.3. Cấu hình phải quan sát được nhưng không làm rò rỉ thông tin

Tham số core/max/queue thường không nhạy cảm, có thể log **giá trị hiệu lực đã được kiểm tra** ở mức phù hợp hoặc xuất metrics cấu hình. Nhưng không in toàn bộ `Environment` vì cùng nguồn đó có thể chứa token, mật khẩu hoặc credential. Khi dùng Actuator `/actuator/configprops`, cần giới hạn truy cập và tận dụng cơ chế sanitization; nó có thể giúp kiểm tra bean config nhưng không thay thế logging và metric định hướng.

## 8. Kiểm tra thực tế: trường hợp lỗi phải không khởi động bình thường

Một cách kiểm thử có giá trị là chạy các kịch bản cấu hình tách biệt (local hoặc integration test) rồi kiểm tra Spring ApplicationContext.

| Kịch bản | Đầu vào ví dụ | Kỳ vọng |
| --- | --- | --- |
| Hợp lệ | `core=4,max=8,queue=64` | Context tạo `ioExecutor` với giá trị tương ứng |
| Sai kiểu | `core=abc` | Binding lỗi; không khởi tạo executor như bình thường |
| Sai khoảng | `queue=-1` | Bean Validation từ chối |
| Sai quan hệ | `core=12,max=8` | `isMaxAtLeastCore()` trả false; validation từ chối |
| Thiếu tiền tố tên | `thread-name-prefix="   "` | `@NotBlank` từ chối |
| Giá trị sát biên | `queue=0`, `core=max=1` | Hợp lệ về constraint; queue là SynchronousQueue |
| Giới hạn trên | `max=65` | Constraint `@Max(64)` từ chối |
| Khóa gõ sai trong prefix | `max-pool-szie=8` | Với `ignoreUnknownFields=false`, dự kiến báo lỗi bind cho thuộc tính không hợp lệ; vẫn kiểm tra việc nguồn chứa khóa được nạp |

Dùng các biến cấu hình/CLI để tạo riêng từng case, không sửa cấu hình production chỉ để thử lỗi. Khi chạy integration test, bảo đảm không dùng lại singleton executor từ một context đã khởi tạo thành công vì điều đó che giấu quá trình bind mới. Với unit test validation thuần, có thể tạo `Validator` và gọi `validate(properties)`, nhưng việc test startup thật còn kiểm tra bước đọc nguồn, conversion, đăng ký bean và lifecycle.

**Không viết test chỉ xác nhận một method `isMaxAtLeastCore()` trả false** rồi kết luận toàn bộ Boot validation đã hoạt động; cần một kiểm tra cấu hình sai thực sự khiến khởi động context thất bại.

## 9. Lỗi phổ biến và cách sửa

### 9.1. Đưa ra ngoài nhưng vẫn có hard-code xung đột

Nếu YAML định nghĩa `core=4` nhưng mã `@Bean` vẫn gọi `setCorePoolSize(16)` sau khi đọc properties, cấu hình ngoài không còn là nguồn quyết định. Chỉ dùng các giá trị cuối cùng đã bind/validate. Nếu cần fallback, đặt ở lớp cấu hình hoặc mặc định được mô tả, tránh nhiều lớp fallback mâu thuẫn.

### 9.2. Dùng @Value cho nhiều trường rồi quên kiểm tra quan hệ

`@Value("${app.executor.io.core-pool-size}")` phù hợp để inject một scalar đơn giản, nhưng không tự xây dựng quy tắc `max >= core` hoặc biểu diễn nhóm thuộc tính có liên hệ. Với một nhóm tham số pool, `@ConfigurationProperties` thường rõ ràng hơn. Nếu bắt buộc dùng `@Value`, phải có bước validate tập trung trước khi tạo executor.

### 9.3. Chỉ validate số dương, không validate max >= core

`@Min(1)` trên core và max **không chứng minh max lớn hơn hoặc bằng core**. `core=16,max=8` vượt qua kiểm tra riêng từng field nếu nằm trong giới hạn. Cross-field constraint hoặc kiểm tra một lần ở factory method là cần thiết.

### 9.4. Nhầm queue=0 với queue bị lỗi

`queueCapacity=0` là hành vi hợp lệ của Spring `ThreadPoolTaskExecutor`: `SynchronousQueue` không giữ công việc chờ, buộc giao trực tiếp/cho phép mở rộng workers đến max rồi reject khi cần. Nếu workload không phù hợp thì chính sách ứng dụng có thể cấm giá trị này bằng `@Min(1)`, nhưng phải nói rõ đó là lựa chọn riêng.

### 9.5. Tưởng tăng maxPoolSize là tăng ngay concurrency

Sau khi tới core, `ThreadPoolExecutor` thường ưu tiên queue. Khi queue chưa đầy, pool **chưa cần** mở thêm workers tới max. Có thể thấy queue dài với `poolSize` bằng core dù max lớn. Đặt max cao mà queue gần như vô hạn không tạo ra concurrency như kỳ vọng.

### 9.6. Ràng buộc quá lỏng hoặc quá chặt

Không có validation làm lỗi đi sâu tới executor/JVM và khó đọc. Ngược lại, giới hạn 32 threads có thể hợp lý cho workload này nhưng quá thấp cho workload blocking I/O khác. Các giới hạn của record phải có chủ đích: ngăn giá trị rõ ràng nguy hiểm, trong khi điều chỉnh năng lực phải dựa trên thử tải và hệ thống phía sau.

### 9.7. Tưởng sửa YAML làm pool thay đổi ngay

Khi ứng dụng đang chạy, `IoExecutorProperties` đã được bind và executor đã khởi tạo. Dù một số setter của `ThreadPoolTaskExecutor` có thể thay đổi runtime, ví dụ trên **không có logic hot reload**. Queue type/capacity là quyết định lúc khởi tạo, không đổi tự động chỉ vì file thay đổi. Khi triển khai cấu hình mới, cách đơn giản và dễ kiểm soát là restart/rolling rollout phù hợp.

### 9.8. Giấu lỗi bằng default im lặng

Nếu production thiếu một khóa quan trọng nhưng default ẩn vẫn khiến ứng dụng chạy với `core=1`, sự cố có thể chỉ bộc lộ dưới tải. Hãy phân loại khóa nào được phép mặc định và khóa nào phải cung cấp khi deploy. Với khóa bắt buộc, dùng binding/validation làm thiếu cấu hình thành lỗi khởi động rõ ràng.

### 9.9. Đánh đồng lỗi khởi động với quá tải lúc chạy

Sai kiểu, sai khoảng và sai quan hệ là **lỗi cấu hình**; nên được phát hiện trước khi nhận traffic. Pool bão hòa/rejection xảy ra **ở runtime ngay cả với cấu hình hợp lệ** khi tải vượt năng lực. Cần theo dõi `getActiveCount()`, `getPoolSize()`, queue length, task duration, rejection, timeout và tài nguyên downstream. Annotation validation không thể bảo đảm sẽ không có rejection dưới tải.

## 10. Quy trình đưa cấu hình executor lên production

1. **Xác định workload**: CPU-bound hay blocking I/O; đo số task mỗi giây, thời gian giữ worker, độ trễ queue, CPU, bộ nhớ và giới hạn database/network.
2. **Chọn nhóm thuộc tính**: thống nhất prefix, tên biến môi trường, default, đơn vị đo và lý do từng giá trị. Với thời gian, ưu tiên định dạng rõ đơn vị khi thiết kế schema mới; ví dụ ở đây dùng `keepAliveSeconds` để khớp API Spring.
3. **Bind một lần theo kiểu rõ ràng**: Boot dùng `@ConfigurationProperties` record; Framework thuần dùng `Environment` và mapping/validator do ứng dụng quản lý.
4. **Validate hai lớp**: từng trường và quan hệ; xử lý missing/unknown/non-integer; báo lỗi ngay ở bootstrap.
5. **Định tuyến đúng bean**: `@Async("ioExecutor")` hoặc injection bằng qualifier để bảo đảm workload dùng pool đã cấu hình.
6. **Kiểm tra môi trường triển khai**: chạy các trường hợp invalid trong test/staging, xác nhận ứng dụng không khởi động với config sai; xác nhận profile/env/CLI override theo đúng ý định.
7. **Triển khai và quan sát**: ghi nhận bản cấu hình hiệu lực không nhạy cảm, theo dõi active workers, queue, rejection, p95/p99 và downstream.
8. **Thay đổi có kiểm soát**: tăng/giảm core/max/queue khi có bằng chứng; kiểm tra kết quả sau rollout, tránh đổi nhiều biến cùng lúc.

## 11. Câu hỏi tự kiểm tra

**1. Vì sao `@ConfigurationProperties` phù hợp với nhóm tham số executor hơn nhiều `@Value` rải rác?**

Vì nó gom các tham số thành object có kiểu, hỗ trợ relaxed binding, metadata, injection và validation tập trung. Đặc biệt, ta có chỗ rõ ràng để kiểm tra các điều kiện liên trường.

**2. Chỉ có `@Min(1)` trên `corePoolSize` và `maxPoolSize` đã đủ chưa?**

Chưa. `core=12,max=8` vẫn thỏa `@Min(1)` ở từng trường nhưng sai quan hệ. Cần `@AssertTrue`, custom constraint hoặc validate ở điểm tạo executor.

**3. Spring Boot có tự chạy `@Validated` của properties record nếu không có Bean Validation provider không?**

Không được giả định như vậy. Cần implementation Jakarta Bean Validation tương thích, thường thêm `spring-boot-starter-validation`, và kiểm tra bằng startup test với cấu hình sai.

**4. `queueCapacity=0` có phải luôn sai?**

Không. Nó chọn cơ chế `SynchronousQueue` trong `ThreadPoolTaskExecutor`. Tính phù hợp phụ thuộc chiến lược nhận task và tải; ví dụ bài này cho phép 0 và cấm số âm.

**5. Tại sao một cấu hình có max=64 nhưng số worker vẫn chỉ có 4?**

Vì sau khi đạt core, task thường được đưa vào queue trước. Khi queue còn nhận được, executor chưa cần tăng tới max. Phải đọc cả core, queue, activeCount và trạng thái downstream.

**6. Nếu thay biến môi trường khi JVM đang chạy thì executor có nhận số mới không?**

Không tự động theo ví dụ. Config được bind lúc khởi tạo context; muốn thay runtime cần cơ chế cập nhật riêng có kiểm tra an toàn hoặc rolling restart.

**7. Nếu validation PASS thì có thể bỏ giám sát rejection/latency không?**

Không. Validation chỉ ngăn cấu hình sai theo các quy tắc đã khai báo. Một pool hợp lệ vẫn có thể quá tải, bị chặn I/O, cạn connection hoặc tạo queue wait rất dài.

## 12. Tài liệu tham khảo chính thức

- Spring Boot 3.3 — Externalized Configuration, property sources, relaxed binding và @ConfigurationProperties Validation: https://docs.spring.io/spring-boot/3.3/reference/features/external-config.html
- Spring Framework 6.x — ThreadPoolTaskExecutor API (core/max/queue/keep-alive): https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Spring Framework — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Java SE 17 — ThreadPoolExecutor và RejectedExecutionHandler: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
- Jakarta Bean Validation — javax/jakarta constraints (@Min, @Max, @NotBlank, @AssertTrue): https://jakarta.ee/specifications/bean-validation/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-009">Phân biệt cấu hình Spring Framework với tự động cấu hình Spring Boot</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt cấu hình Spring Framework với tự động cấu hình Spring Boot

## 1. Mục tiêu và phạm vi

Trong Spring, bạn có thể viết `@Configuration` và `@Bean` để tạo một `ThreadPoolTaskExecutor` hoặc `ThreadPoolTaskScheduler`. Khi dùng Spring Boot, ứng dụng đôi khi đã có các bean tương tự dù bạn không hề viết cấu hình Java. Hai hiện tượng này dễ khiến người học kết luận sai rằng “Spring Framework tự tạo thread pool”, rằng “thêm `@Async` là Boot đã tự bật async”, hoặc rằng “đổi `spring.task.execution.pool.*` luôn sửa được mọi executor”.

Bài này phân biệt **hai lớp trách nhiệm**:

1. **Spring Framework** cung cấp IoC container, cấu hình bean, các abstraction `TaskExecutor`/`TaskScheduler`, các implementation, proxy xử lý `@Async` và cơ chế đăng ký `@Scheduled`. Bạn có thể khai báo chúng trong ứng dụng Spring thuần mà không cần Spring Boot.
2. **Spring Boot** đứng trên Spring Framework, cung cấp dependency management, khởi tạo ứng dụng, **auto-configuration có điều kiện**, cấu hình ngoài qua properties/YAML và những mặc định thuận tiện. Khi điều kiện phù hợp, Boot tạo bean executor/scheduler thay bạn; khi có bean do người dùng tạo, kết quả có thể khác.

Nội dung tập trung vào **cấu hình tài nguyên thực thi** trong chủ đề concurrency: executor, scheduler, lựa chọn bean, thread pool, tính hiệu lực của properties, lifecycle và cách xác minh. Người học cần biết cách truy ra: **bean nào tồn tại, do ai tạo, ai sử dụng, cấu hình nào đang có hiệu lực và ai đóng tài nguyên**.

Ví dụ mã Java sử dụng Java 17+ với Spring Framework 6.1 / Spring Boot 3.3 làm mốc có thể kiểm chứng; phần virtual threads yêu cầu Java 21+. Nguyên tắc áp dụng rộng hơn, còn default và điều kiện auto-configuration phải đối chiếu phiên bản thực tế.

## 2. Hiểu đúng các tầng

```text
                MÃ NGHIỆP VỤ
    @Async service, @Scheduled task, caller
                       |
            Chọn executor/scheduler
                       |
        +--------------+--------------+
        |                             |
  Spring Framework              Spring Boot
  - @Configuration              - @SpringBootApplication
  - @Bean                       - @EnableAutoConfiguration
  - @EnableAsync                - AutoConfiguration conditions
  - @EnableScheduling           - @ConfigurationProperties
  - TaskExecutor API            - spring.task.execution.*
  - TaskScheduler API           - spring.task.scheduling.*
  - ThreadPoolTaskExecutor      - Default bean nếu đủ điều kiện
  - ThreadPoolTaskScheduler
        |                             |
        +--------------+--------------+
                       |
                Java / JVM / OS
         ThreadPoolExecutor / virtual threads
```

**Mấu chốt:** Boot **sử dụng** các thành phần của Framework và thêm logic cấu hình tự động. Boot không thay đổi bản chất của `ThreadPoolTaskExecutor` hay Java Memory Model. Framework không cần Boot để chạy task; Boot không thể làm cho shared mutable state tự trở nên thread-safe.

### 2.1. Bảng so sánh

| Góc nhìn | Spring Framework thuần | Spring Boot |
| --- | --- | --- |
| Mục tiêu | Hạ tầng DI, bean lifecycle, AOP, task execution/scheduling SPI | Khởi động nhanh, thiết lập thành phần theo classpath và cấu hình |
| Nơi khai báo tài nguyên | Thường `@Configuration`/`@Bean`, XML, bean definitions | Bean thủ công **hoặc** auto-configured bean |
| Cách bật annotation async | `@EnableAsync` hoặc cấu hình XML tương ứng | Vẫn cần `@EnableAsync` khi dùng cơ chế annotation đó |
| Cách bật scheduling | `@EnableScheduling` hoặc XML tương ứng | Vẫn cần cơ chế enable scheduling, thường `@EnableScheduling` |
| Cách chọn executor | `@Async("beanName")`, `AsyncConfigurer`, lookup mặc định | Cùng quy tắc Framework, thêm executor tự cấu hình và một số tích hợp Boot |
| Chỉnh pool | Gọi setter khi tạo bean hoặc dùng properties do ứng dụng tự bind | Có `spring.task.execution.*` cho auto-configured executor phù hợp |
| Chỉnh scheduler | Tạo bean scheduler hoặc qua `SchedulingConfigurer` | Có `spring.task.scheduling.*` khi auto-configured scheduler phù hợp |
| Điều kiện tự tạo bean | Không có cơ chế Boot auto-config mặc định | Classpath + điều kiện bean hiện có + settings + phiên bản |
| Vòng đời | Bean do container tạo thường được container quản lý; tự tạo ngoài container thì tự đóng | Container vẫn quản lý bean, Boot hỗ trợ wiring/config các bean được auto-configure |
| Kiểm chứng | Đọc config, liệt kê beans và quan sát thread/executor | Tương tự, bổ sung condition evaluation và Actuator khi có |

Điều quan trọng là phân biệt bốn việc **khác nhau**: (1) tạo bean executor, (2) bật cơ chế xử lý annotation, (3) chọn executor thực sự của lời gọi và (4) cấu hình tài nguyên của executor đó. Làm được một việc không tự động chứng minh ba việc còn lại.

## 3. Spring Framework thuần: bạn khai báo bean có chủ đích

Trong ứng dụng chỉ dùng Spring Framework, `@Configuration` và `@Bean` là cách trực tiếp khai báo tài nguyên. Framework thực hiện dependency injection, bean initialization/destroy và proxy theo annotation đã được bật. Nó không tự đọc namespace `spring.task.execution.pool.*` của Spring Boot.

### 3.1. Tạo một pool và gắn rõ vào `@Async`

Ví dụ hoàn chỉnh có thể chạy bằng **Java 17 + Maven** và **Spring Framework 6.1.x**, không sử dụng Spring Boot.

Cấu trúc:

```text
framework-only/
├── pom.xml
└── src/main/java/demo/FrameworkOnlyDemo.java
```

Tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>framework-only</artifactId>
    <version>1.0.0</version>
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
</project>
```

Tệp `src/main/java/demo/FrameworkOnlyDemo.java`:

```java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class FrameworkOnlyDemo {
    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class AppConfig {
        @Bean("manualExecutor")
        ThreadPoolTaskExecutor manualExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setThreadNamePrefix("manual-");
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(2);
            executor.setQueueCapacity(10);
            return executor;
        }

        @Bean
        Worker worker() {
            return new Worker();
        }
    }

    public static class Worker {
        @Async("manualExecutor")
        public CompletableFuture<Boolean> checkExecutor() {
            boolean selected =
                    Thread.currentThread().getName().startsWith("manual-");
            return CompletableFuture.completedFuture(selected);
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            Worker worker = context.getBean(Worker.class);
            boolean selected = worker.checkExecutor()
                    .get(5, TimeUnit.SECONDS);
            System.out.println("manual-executor=" + selected);
        }
    }
}
```

Chạy ở thư mục chứa POM:

```bash
mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=demo.FrameworkOnlyDemo
```

Dòng output của chương trình (Maven có thể có thông báo riêng tùy môi trường):

```text
manual-executor=true
```

**Vì sao đúng:** `@EnableAsync` đăng ký hạ tầng xử lý `@Async`; method được gọi qua Spring-managed bean nên proxy có cơ hội intercept; `@Async("manualExecutor")` chỉ rõ bean executor; task kiểm tra prefix từ thread thực thi. `try-with-resources` đóng application context, qua đó Spring xử lý lifecycle bean `ThreadPoolTaskExecutor`.

Không thấy `SpringApplication.run`, `@SpringBootApplication` hay property `spring.task.execution.*` trong ví dụ; vì đây là **Spring Framework thuần**. Bạn có thể thay tên và thông số executor hoàn toàn qua cấu hình bean.

### 3.2. Nếu Framework không có executor bạn tự tạo?

Khi `@EnableAsync` được dùng mà không cấu hình executor riêng, cơ chế async của Framework có quy tắc tìm một `TaskExecutor` phù hợp (hoặc `Executor` mang tên quy ước `taskExecutor`) và có fallback như `SimpleAsyncTaskExecutor`. Fallback này **không phải một pool bounded được Boot cấu hình**, và không có nghĩa một tài nguyên phù hợp tải đã được tạo.

Vì vậy, “code chạy được” chỉ xác nhận lời gọi đã thực thi; chưa chứng minh số worker, queue, giới hạn tải hoặc shutdown phù hợp. Với ứng dụng sản xuất, hãy chỉ định executor và kiểm tra hành vi quá tải.

### 3.3. Không quên phần scheduler

Framework cung cấp `TaskScheduler`, `ThreadPoolTaskScheduler` và `@EnableScheduling`. Bạn có thể khai báo thủ công:

```java
@Configuration
@EnableScheduling
class FrameworkSchedulingConfig {
    @Bean("maintenanceScheduler")
    ThreadPoolTaskScheduler maintenanceScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("manual-schedule-");
        return scheduler;
    }
}
```

Đây là **fragment** trong một dự án Spring có import tương ứng, không phải file Java tự chạy độc lập. Không nhầm `@EnableScheduling` (kích hoạt xử lý annotation) với bean scheduler (cung cấp tài nguyên). Nếu có nhiều scheduler, cần xem cách Spring chọn mặc định hoặc cấu hình rõ theo `SchedulingConfigurer`; đặt một tên bean bất kỳ không chứng minh mọi `@Scheduled` dùng bean đó.

## 4. Spring Boot: bean được tạo bởi auto-configuration có điều kiện

`@SpringBootApplication` bao gồm cơ chế auto-configuration của Boot (qua `@EnableAutoConfiguration`) cùng cấu hình/scan phù hợp. Boot đọc classpath, các bean đã có và giá trị configuration properties, rồi đánh giá nhiều điều kiện như `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty`.

**Auto-configuration có điều kiện** nghĩa là Boot thường tự tạo tài nguyên **khi thiếu những bean tương ứng và điều kiện được thỏa**. Nó không bảo đảm luôn có cùng loại bean trong mọi ứng dụng Boot. Việc bạn thêm một bean executor tùy chỉnh có thể khiến auto-configuration của executor mặc định **back off** (không tạo bean đó). Điều kiện chính xác thay đổi theo phiên bản Boot và loại bean.

Với **Spring Boot 3.3.x**, trong ngữ cảnh không có `Executor` tùy chỉnh:

- Boot có thể auto-configure một `AsyncTaskExecutor`. Khi không bật virtual threads, implementation mặc định là `ThreadPoolTaskExecutor`.
- Khi dùng Java 21+ và bật `spring.threads.virtual.enabled=true`, executor tự cấu hình có thể là `SimpleAsyncTaskExecutor` dùng virtual threads.
- Tích hợp async của Spring, Spring MVC async request và những thành phần khác có quy tắc sử dụng bean auto-configured khác nhau; tên bean và kiểu bean quan trọng.
- Khi cần scheduled execution với `@EnableScheduling`, Boot có thể auto-configure scheduler; với platform threads thường là `ThreadPoolTaskScheduler`, với virtual threads có thể là `SimpleAsyncTaskScheduler`.
- Boot cung cấp properties `spring.task.execution.*` và `spring.task.scheduling.*` để chỉnh **các tài nguyên do Boot tự cấu hình**, theo điều kiện tương ứng.

Những điều trên được mô tả trong tài liệu chính thức Spring Boot 3.3.x; với Boot 4.x hoặc release khác phải mở đúng version reference.

### 4.1. Ví dụ Boot: chỉ cấu hình qua application.yml

Giả định một dự án **Spring Boot 3.3.x**, có `spring-boot-starter`, dùng Java 17+, chưa khai báo bean `Executor` hay `AsyncConfigurer` tùy chỉnh, và không bật virtual threads.

Tệp `src/main/resources/application.yml`:

```yaml
spring:
  main:
    web-application-type: none
  threads:
    virtual:
      enabled: false
  task:
    execution:
      thread-name-prefix: boot-work-
      pool:
        core-size: 2
        max-size: 4
        queue-capacity: 20
        keep-alive: 30s
    scheduling:
      thread-name-prefix: boot-schedule-
      pool:
        size: 2
```

Dự án không cần có class `@Configuration` nào tự tạo `ThreadPoolTaskExecutor` để Boot có thể cấu hình executor theo các thuộc tính trên. Nhưng **để annotation `@Async` được xử lý** vẫn cần `@EnableAsync`; và để xử lý `@Scheduled` thông thường cần `@EnableScheduling`.

Tệp `src/main/java/demo/BootAutoExecutorDemo.java`:

```java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BootAutoExecutorDemo {
    @Bean
    Worker worker() {
        return new Worker();
    }

    public static class Worker {
        @Async("applicationTaskExecutor")
        public CompletableFuture<Boolean> checkExecutor() {
            boolean selected =
                    Thread.currentThread().getName().startsWith("boot-work-");
            return CompletableFuture.completedFuture(selected);
        }
    }

    public static void main(String[] args) throws Exception {
        try (ConfigurableApplicationContext context =
                     SpringApplication.run(BootAutoExecutorDemo.class, args)) {
            AsyncTaskExecutor executor =
                    context.getBean("applicationTaskExecutor",
                                    AsyncTaskExecutor.class);
            boolean selected = context.getBean(Worker.class)
                    .checkExecutor().get(5, TimeUnit.SECONDS);

            System.out.println("bean-type=" +
                    executor.getClass().getSimpleName());
            System.out.println("boot-executor=" + selected);
        }
    }
}
```

Chạy trong dự án Boot đã cấu hình Gradle wrapper và đúng Java:

```bash
./gradlew bootRun
```

Hai dòng ứng dụng kỳ vọng trên **Boot 3.3.x với đúng điều kiện nêu trên**, ngoài log startup:

```text
bean-type=ThreadPoolTaskExecutor
boot-executor=true
```

Boot tạo bean `applicationTaskExecutor` khi thỏa điều kiện; `@Async("applicationTaskExecutor")` chọn nó một cách minh bạch; prefix `boot-work-` đến từ `application.yml`. Việc lấy bean theo tên và kiểu đồng thời tránh tình trạng “có task chạy rồi nên nghĩ là thuộc đúng pool”.

Nếu bật virtual threads, thêm bean executor thủ công, thay phiên bản Boot hoặc thiếu starter phù hợp thì loại bean và output có thể khác. Ví dụ được ràng buộc điều kiện rõ ràng để tránh xem một tình huống là mặc định bất biến.

### 4.2. Chỉ thêm properties không tự bật xử lý annotation

Ba cấu hình có vai trò khác nhau:

| Thứ bạn thêm | Điều nó làm | Điều nó không tự làm |
| --- | --- | --- |
| `@EnableAsync` | Bật cơ chế chặn lời gọi qua Spring proxy cho `@Async` | Không tự bảo đảm tạo một bounded pool đáp ứng tải |
| `spring.task.execution.pool.max-size=...` | Chỉnh một property của Boot-managed pool khi property được áp dụng | Không tự đăng ký `@Async` hoặc thay đổi bean tùy chỉnh không bind property |
| `@Async("someExecutor")` | Chỉ định executor cho một method khi advice thực sự chạy | Không tự tạo bean tên `someExecutor` |
| `@EnableScheduling` | Bật xử lý annotation `@Scheduled` | Không biến cron thành job bền vững |
| `spring.task.scheduling.pool.size=...` | Tinh chỉnh Boot-managed thread pool scheduler khi có hiệu lực | Không nhất thiết tác động lên scheduler do code tự tạo |
| `@Bean ThreadPoolTaskExecutor` | Đưa resource tùy chỉnh vào container | Không có nghĩa Boot sẽ áp dụng mọi `spring.task.execution.*` cho bean đó |

Lời gọi nội bộ `this.doAsync()` trong cùng bean thường đi vòng qua proxy `@Async` mặc định. Dù Boot đã tạo `applicationTaskExecutor` đúng cách, self-invocation vẫn có thể làm method chạy trên caller thread. Đây là vấn đề cơ chế proxy của Framework, **không phải lỗi Boot auto-configuration**.

## 5. Khi bạn tự khai báo Executor trong Spring Boot thì sao?

Spring Boot tôn trọng khả năng tùy chỉnh của ứng dụng. Khi một `Executor` đã được khai báo, auto-configuration có thể ngừng tạo executor mặc định; đồng thời cách các integration chọn executor vẫn có yêu cầu tên và kiểu riêng. Đây là nguyên nhân thường gặp nhất của câu “tôi chỉnh YAML nhưng số thread không đổi”.

### 5.1. Mẫu bean tùy chỉnh

Trong ứng dụng Spring Boot, cấu hình sau tạo `ThreadPoolTaskExecutor` do **bạn** chịu trách nhiệm về các thông số:

```java
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
class CustomExecutionConfig {
    @Bean("domainExecutor")
    ThreadPoolTaskExecutor domainExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(30);
        executor.setThreadNamePrefix("domain-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
```

Đây là **fragment Spring**, đặt trong project có dependencies. Nếu method có `@Async("domainExecutor")` và được gọi qua Spring proxy, nó sẽ dùng bean này. Thay `spring.task.execution.pool.core-size` trong YAML **không tự động thay** giá trị 3 được đặt bằng setter trong mã trên.

Có thể đưa các tham số vào `@ConfigurationProperties` riêng, hoặc dùng builder do Boot cung cấp (ví dụ `ThreadPoolTaskExecutorBuilder` ở các phiên bản tương ứng) để tận dụng các customizer/thiết lập, nhưng hãy kiểm tra rõ thuộc tính nào đã bind. Tự tạo bean bằng `new` không đồng nghĩa với việc Boot tự bind mọi property cho nó.

### 5.2. Phân biệt bean `taskExecutor` và `applicationTaskExecutor`

Đây là điểm dễ gây nhầm giữa **method async thông thường** và **các integration**:

- Framework xử lý `@Async`: có thể tìm executor mặc định theo `AsyncConfigurer`, bean `TaskExecutor` phù hợp hoặc tên quy ước `taskExecutor`; `@Async("beanName")` là cách chỉ rõ từng method.
- Theo tài liệu **Spring Boot 3.3**, khi có nhiều bean `Executor`, cơ chế async thông thường có thể fallback theo tên `taskExecutor`.
- Với Spring MVC async request và Spring WebFlux blocking execution support trên Boot 3.3, integration mong đợi `AsyncTaskExecutor` phù hợp có tên `applicationTaskExecutor`.
- `ThreadPoolTaskExecutor` là `AsyncTaskExecutor`, nhưng `java.util.concurrent.ExecutorService` đơn thuần không tự đáp ứng mọi yêu cầu của Spring MVC async integration.
- Hai bean khác nhau có thể cùng tồn tại cho các mục tiêu khác nhau; việc bean “được dùng cho `@Async`” không chứng minh nó cũng xử lý `Callable`/`WebAsyncTask` của MVC.

Bảng tình huống trên Boot 3.3.x:

| Bối cảnh | Điều cần kiểm chứng |
| --- | --- |
| Không khai báo `Executor`, dùng Boot auto-config | `applicationTaskExecutor` tồn tại, loại bean và prefix/size đúng |
| Có một bean `Executor` tự khai báo | Boot có back off không? `@Async` chọn bean nào? MVC còn executor hợp lệ không? |
| Có nhiều bean `Executor` | `AsyncConfigurer`, bean tên `taskExecutor` và `@Async("...")` giải quyết lựa chọn nào? |
| Muốn MVC async dùng bean tùy chỉnh | Có `AsyncTaskExecutor` đúng tên `applicationTaskExecutor` hoặc cấu hình MVC riêng không? |
| Muốn pool riêng cho tác vụ nghiệp vụ | Dùng `@Async("domainExecutor")` và inject bean theo `@Qualifier` |
| Có virtual threads | Bean auto-configured thuộc loại nào? Property pool-size còn có hiệu lực không? |

**Không nên** đổi tên một bean bừa bãi chỉ để làm ứng dụng khởi động được. Tên là một phần hợp đồng wiring cho nhiều integration; thay tên có thể khiến một thành phần dùng pool khác.

### 5.3. Nếu muốn giữ mặc định Boot cho MVC và pool riêng cho domain

Một thiết kế chủ động thường là:

- Cho Spring MVC async dùng bean `applicationTaskExecutor` thuộc kiểu `AsyncTaskExecutor`.
- Cho method nghiệp vụ nặng dùng `@Async("domainExecutor")`.
- Khai báo rõ bean nào phục vụ nhiệm vụ nào, sử dụng `AsyncConfigurer` nếu cần chọn mặc định cho `@Async`.
- Kiểm tra điều kiện auto-configuration với đúng phiên bản Boot: thêm `domainExecutor` có thể khiến Boot **không còn tạo** `applicationTaskExecutor` tự động. Khi đó phải khai báo bean MVC riêng hoặc dùng cơ chế cấu hình được Boot hỗ trợ.
- Đặt queue, rejection, timeout, metrics và shutdown riêng theo loại tải.

Không giả định “Boot luôn bổ sung pool còn thiếu” nếu đã có executor tùy chỉnh. Kiểm chứng danh sách bean thật từ application context sau khi khởi động.

## 6. Ví dụ thực tế: sửa một lỗi cấu hình không có hiệu lực

Giả sử ứng dụng Boot đang có đoạn:

```yaml
spring:
  task:
    execution:
      pool:
        core-size: 8
        max-size: 16
        queue-capacity: 100
```

Nhưng trong source lại có:

```java
@Bean("fileExecutor")
ThreadPoolTaskExecutor fileExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(1000);
    return executor;
}
```

Và method xử lý file:

```java
@Async("fileExecutor")
public CompletableFuture<String> processFile(String name) {
    return CompletableFuture.completedFuture(name);
}
```

Các fragment trên cần imports/project phù hợp. Nếu lời gọi chạy qua proxy, executor dùng là `fileExecutor` với core/max **2**, queue **1000** theo cấu hình Java; sửa YAML của Boot-managed executor không tự thay các setter đã áp dụng lên bean `fileExecutor`.

**Quy trình sửa:**

1. Xác định `@Async("fileExecutor")` đang chỉ đúng bean nào; bảo đảm lời gọi qua proxy.
2. Quyết định một nguồn cấu hình: Java setter, custom `@ConfigurationProperties` được bind vào bean, hay builder/tích hợp Boot được kiểm chứng.
3. Chỉnh giá trị tại nguồn thực sự dùng cho `fileExecutor`, không đổi một property không được tham chiếu.
4. Xác minh `getCorePoolSize()`, `getMaxPoolSize()`, queue capacity và thread prefix trên **bean đã chạy task**.
5. Load test để kiểm tra hành vi queue → tăng worker → rejection; không chỉ so sánh thông số cấu hình.

Nếu thêm một `Executor` khiến auto-config của Boot back off và một integration khác mất bean `applicationTaskExecutor`, phải giải quyết wiring của integration ấy nữa. Đây là tác động kiến trúc cần phát hiện, không chỉ vấn đề cỡ pool.

## 7. Điều kiện auto-configuration hoạt động như thế nào?

Một auto-configuration thường diễn tả quyết định có điều kiện, ví dụ về mặt **khái niệm**:

```text
Nếu có các class/feature liên quan trên classpath
  và chế độ thực thi phù hợp
  và chưa có bean người dùng thuộc điều kiện loại trừ
thì tạo executor/scheduler mặc định
ngược lại, bỏ qua việc tạo mặc định.
```

Đây là mô hình tư duy, **không phải mã điều kiện chính xác** của một release Boot cụ thể. Các annotation như `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty` được dùng trong cơ chế auto-configuration; tên class condition, tên bean và kiểu bean cần đọc trong phiên bản hiện hành.

### 7.1. Thứ tự và thời điểm điều kiện được đánh giá

- Auto-configuration được Boot nhập vào application context, không phải được bạn gọi như hàm thủ công.
- Điều kiện đánh giá dựa trên bean definitions, classpath, environment và cấu hình đã có tại thời điểm thích hợp.
- Nếu một bean tùy chỉnh thỏa điều kiện loại trừ, auto-config sẽ không tạo bản mặc định của Boot; đó là hành vi thiết kế để ứng dụng nắm quyền kiểm soát.
- Nếu source có `@Bean` nhưng class không nằm trong phạm vi scan/import, bean có thể **không tồn tại** ở context bạn đang kiểm tra.
- Nếu một method có `@Async` nhưng không bật async processing hoặc gọi qua đối tượng tự `new`, annotation có thể không tạo lời gọi async.
- Một `@Configuration` của Framework có thể hoạt động cả trong Boot, vì Boot dùng cùng container; chỉ khác ở việc Boot thêm các auto-configurations.

### 7.2. Cách xem nguyên nhân bean được tạo hoặc không tạo

Trong Boot, có thể:

1. Khởi động với debug condition evaluation (ví dụ cờ `--debug` theo phiên bản hỗ trợ) và xem condition evaluation report.
2. Nếu có Actuator, kiểm tra endpoint conditions và beans khi được bật/cho phép, ví dụ `/actuator/conditions`, `/actuator/beans`. Tránh mở những endpoint nhạy cảm công khai.
3. Kiểm tra danh sách bean thực trong `ApplicationContext` và loại class runtime.
4. Xem các cấu hình `@ConditionalOnMissingBean`, `@ConditionalOnClass` và property binding trong documentation/source của đúng Boot release.
5. Kiểm tra profile active và thứ tự config external overrides. `application.yml` không nhất thiết là nguồn cuối cùng nếu có environment variable hoặc command-line override.
6. Đối chiếu observed thread name, pool metrics và context wiring. Tên thread trùng prefix một mình chưa đủ để chứng minh mọi task dùng cùng executor, nhưng kết hợp với bean ID và future giúp chẩn đoán.

Với Framework thuần không có Boot, bạn vẫn xem bean definitions, `getBeansOfType` và cấu hình Java/XML; **không** có condition report của Boot nếu không sử dụng Boot.

## 8. Kiểm chứng runtime bằng code, không dựa vào phỏng đoán

### 8.1. Đếm các bean Executor và Scheduler

Fragment sau có thể đặt trong một component của dự án Boot hoặc Spring Framework; giả định đã inject `ApplicationContext context`, đã import Java `Map`, `Executor`, `TaskScheduler`, `ThreadPoolTaskExecutor`.

```java
Map<String, Executor> executors =
        context.getBeansOfType(Executor.class);

executors.forEach((name, executor) -> {
    String type = executor.getClass().getName();
    System.out.println("Executor " + name + " -> " + type);

    if (executor instanceof ThreadPoolTaskExecutor pool) {
        System.out.println("core=" + pool.getCorePoolSize());
        System.out.println("max=" + pool.getMaxPoolSize());
        System.out.println("poolSize=" + pool.getPoolSize());
        System.out.println("active=" + pool.getActiveCount());
    }
});

Map<String, TaskScheduler> schedulers =
        context.getBeansOfType(TaskScheduler.class);

schedulers.forEach((name, scheduler) ->
        System.out.println("Scheduler " + name + " -> " +
                           scheduler.getClass().getName()));
```

Đây là **fragment tích hợp**; không phải lớp Java độc lập. Nó giúp biết **bean hiện hữu và cấu hình** trong context. Tuy vậy, có bean không có nghĩa method thực tế đang dùng nó; phải truy thêm `@Async` qualifier, `AsyncConfigurer`, injection và các integration MVC/WebFlux.

Đừng lấy `getPoolSize()` rồi cho đó là `maxPoolSize`: pool size là số worker đang tồn tại tại thời điểm quan sát; max là cấu hình giới hạn. Và khi task đã chạy xong, `activeCount` có thể bằng 0 dù trước đó executor có làm việc.

### 8.2. Kiểm tra method `@Async` có thực sự đổi thread

Fragment cho service:

```java
@Async("domainExecutor")
public CompletableFuture<Boolean> onDomainPool() {
    return CompletableFuture.completedFuture(
            Thread.currentThread().getName().startsWith("domain-"));
}
```

Caller thuộc **bean khác** gọi service qua Spring proxy rồi kiểm tra `future.get(timeout)`. Không gọi `this.onDomainPool()` từ chính service để kiểm tra. Có thể ghi thêm trace ID, executor name và duration để đối chiếu trong môi trường test.

Với method trả `void`, không có future để truyền lỗi về caller; dùng `AsyncUncaughtExceptionHandler`/logging/monitoring phù hợp. Với `CompletableFuture`, exception được phản ánh khi future hoàn tất exceptional và caller quan sát nó.

### 8.3. Kiểm tra scheduling

Trong Spring, việc hiện diện một `ThreadPoolTaskScheduler` không chứng minh tất cả method `@Scheduled` đã được kích hoạt. Cần xem:

- Có `@EnableScheduling` và bean method được Spring quản lý không?
- Có scheduler phù hợp và được chọn đúng không?
- Cron zone, fixed rate/delay, concurrency và task overlap đã được hiểu đúng chưa?
- Có nhiều instance ứng dụng cùng chạy lịch không?
- Kết quả có yêu cầu exactly-once/durability vượt khả năng local scheduler không?

Một scheduled callback ngắn có thể chạy trên scheduler; callback kéo dài thường cần thiết kế tách tác vụ. Với nhiều replica, `@Scheduled` tự nó không bảo đảm chỉ chạy một lần toàn cụm.

## 9. Executor và scheduler: hai hệ auto-config riêng

Một ứng dụng có thể cần `@Async` nhưng không cần `@Scheduled`, hoặc ngược lại. Vì vậy cần phân biệt:

| Nhu cầu | Abstraction Framework | Hỗ trợ Boot có điều kiện | Property namespace tham khảo |
| --- | --- | --- | --- |
| Submit task bất đồng bộ | `TaskExecutor` / `AsyncTaskExecutor` | Executor auto-configured nếu thỏa điều kiện | `spring.task.execution.*` |
| Giới hạn pool worker async | `ThreadPoolTaskExecutor` | Chỉnh pool nếu loại bean đang là thread pool và được Boot quản lý | `spring.task.execution.pool.*` |
| Lên lịch task | `TaskScheduler` | Scheduler auto-configured khi cần và đủ điều kiện | `spring.task.scheduling.*` |
| Giới hạn scheduler worker | `ThreadPoolTaskScheduler` | Pool properties nếu dùng implementation pool tương ứng | `spring.task.scheduling.pool.*` |
| Chạy task với virtual threads | `SimpleAsyncTaskExecutor`/virtual-thread executor tùy phiên bản | Boot 3.3 hỗ trợ kích hoạt qua property | `spring.threads.virtual.enabled` |

Ví dụ Boot 3.3.x có thể dùng:

```yaml
spring:
  task:
    execution:
      thread-name-prefix: background-
      pool:
        core-size: 4
        max-size: 8
        queue-capacity: 64
    scheduling:
      thread-name-prefix: clock-
      pool:
        size: 2
```

Nếu chỉ khai báo `@EnableScheduling`, Boot có thể tự tạo scheduler theo điều kiện; nếu ứng dụng tự khai báo `ThreadPoolTaskScheduler`, đừng mặc định `spring.task.scheduling.pool.size` áp dụng vào bean đó. Nếu dùng virtual-thread scheduler của Boot, pool-size có thể không liên quan. Phải truy đúng object đang dùng.

### 9.1. Scheduler mặc định không phải durable job queue

Boot giúp tạo scheduler thuận tiện, nhưng việc tạo một bean scheduler không tự giải quyết:

- job phải sống sau khi JVM crash/restart;
- distributed lock cho nhiều instance;
- retry và idempotency;
- hệ thống lưu trạng thái thành công/thất bại;
- đảm bảo công việc hoàn thành một lần duy nhất trên toàn cluster.

Đó là bài toán quản lý job nghiệp vụ hoặc orchestration bên ngoài. Không biến auto-configuration thành lời hứa durability.

## 10. Virtual threads và khác biệt theo phiên bản Boot

Ở Java 21+, có thể dùng virtual threads cho nhiều công việc **blocking I/O**; điều đó không làm database/API downstream có vô hạn connection hay làm CPU-bound nhanh hơn số lõi.

Với Spring Boot 3.3.x khi có hỗ trợ phù hợp, `spring.threads.virtual.enabled=true` khiến auto-configured executor/scheduler thay đổi implementation. Boot có thể dùng:

- `SimpleAsyncTaskExecutor` với virtual threads thay cho `ThreadPoolTaskExecutor` được auto-configured;
- `SimpleAsyncTaskScheduler` với virtual threads thay cho `ThreadPoolTaskScheduler` trong trường hợp scheduling phù hợp.

Khi đó các property thuộc **pool platform threads** như core/max/queue/pool.size có thể không còn điều khiển cơ chế mới. Điều này chỉ áp dụng cho đường auto-configured tương ứng, không tự biến mọi custom `@Bean ThreadPoolTaskExecutor` thành virtual-thread executor.

Ví dụ Boot 3.3.x chạy trên Java 21+:

```yaml
spring:
  threads:
    virtual:
      enabled: true
  main:
    keep-alive: true
```

Virtual threads là daemon; với ứng dụng background/scheduled không có non-daemon thread khác giữ JVM sống, cấu hình keep-alive thích hợp giúp tránh thoát process ngoài ý muốn. Kiểm tra đúng phiên bản Boot và runtime triển khai.

**Không dùng “virtual thread = bật cờ xong hết vấn đề”.** Cần vẫn đo active tasks, CPU, memory, connection pool, timeout và backpressure. Với virtual-thread scheduler, một số chế độ fixed-delay có đặc tính scheduler thread cần lưu ý; đừng suy diễn rằng pool-size property sẽ mở rộng song song như thread pool cũ.

## 11. Ai tạo, ai đóng tài nguyên?

Không chỉ bean nào tồn tại mà **quyền sở hữu lifecycle** cũng khác nhau.

### 11.1. Tài nguyên được Spring quản lý

Khi `ThreadPoolTaskExecutor`/`ThreadPoolTaskScheduler` là bean có lifecycle được Spring nhận biết, context sẽ thực hiện quá trình initialization/destroy phù hợp. Cả bean thủ công trong Framework và bean do Boot auto-configure đều có thể được Spring container quản lý.

Cấu hình shutdown của `ThreadPoolTaskExecutor` cần cân nhắc:

- `setWaitForTasksToCompleteOnShutdown(...)`: chính sách đợi/tiếp tục với task đã nhận khi context đóng, tùy behavior của Spring version.
- `setAwaitTerminationSeconds(...)`: giới hạn thời gian context đợi executor kết thúc theo cơ chế hỗ trợ; không phải cam kết mọi task sẽ hoàn tất.
- Retry/cancellation/timeout và external graceful shutdown nếu task có thể bị interrupt hay bị kill do container.

Nếu tác vụ nghiệp vụ bắt buộc tồn tại sau restart, executor trong memory không đủ: dùng durable job/queue/workflow phù hợp và đặt chính sách xử lý duplicate/idempotency.

### 11.2. Tài nguyên không do container quản lý

Ví dụ:

```java
public void process() {
    ExecutorService executor = Executors.newFixedThreadPool(4);
    executor.submit(() -> runWork());
}
```

Đây là **fragment sai về vòng đời** nếu không giữ/đóng `executor`. Spring/Boot không tự phát hiện và shutdown mọi executor được tạo trong local method. Cách hợp lý thường là tạo bean quản lý dùng lại hoặc bảo đảm code sở hữu executor đóng nó bằng `shutdown()`, chờ completion/hủy theo yêu cầu, và xử lý lỗi.

### 11.3. Lifecycle ownership theo nguồn bean

| Nguồn tài nguyên | Ai chọn cấu hình? | Ai thường quản lý lifecycle? |
| --- | --- | --- |
| `@Bean ThreadPoolTaskExecutor` trong Spring thuần | Code ứng dụng | Spring container nếu là managed bean |
| Auto-configured executor trong Boot | Boot + properties + conditions | Spring container |
| Custom `@Bean` trong Boot | Code ứng dụng / binding có chủ đích | Spring container |
| Local `Executors.new...` | Method/class tạo executor | Chính owner phải đóng |
| Jakarta EE managed executor | Container ứng dụng | Container bên ngoài; app không đóng tùy tiện |
| Broker/job worker | Infrastructure + worker manager | Theo vòng đời hệ job riêng |

Vì vậy, “Spring Framework manual” và “Spring Boot auto” khác nhau chủ yếu ở **nguồn bean definition và policy cấu hình**, còn lifecycle thực tế vẫn có thể cùng đi qua Spring container.

## 12. Cấu hình theo môi trường: local, test, staging, production

Trong Spring Framework thuần, bạn có thể tạo bean conditionally qua `@Profile`, `@Conditional` hoặc tự bind configuration ngoài. Trong Boot, auto-configuration và property sources giúp giảm boilerplate, nhưng dễ gây nhầm rằng giá trị ghi trong một YAML chắc chắn là giá trị cuối.

Các nguồn cấu hình Boot có thể gồm application.yml, profile-specific files, environment variables, system properties, command-line arguments và config import. Độ ưu tiên phụ thuộc quy tắc externalized configuration của Boot. Phải xác minh effective environment và bean runtime.

Ví dụ tưởng tượng:

- `application.yml`: `spring.task.execution.pool.core-size=4`;
- production profile: thay thành 8;
- custom `@Bean`: gọi `setCorePoolSize(2)`, không bind properties;
- task thực tế: `@Async("domainExecutor")` chạy trên custom bean.

Trong ví dụ, người xem YAML có thể nghĩ production chạy tám worker, nhưng **domainExecutor** vẫn có core bằng hai nếu không có logic khác ghi đè. Chính xác phải đọc bean đang chạy task và đường binding, không chỉ file cấu hình.

### 12.1. Một nguồn cấu hình có thể kiểm tra được

Nếu quyết định dùng custom bean vì yêu cầu nghiệp vụ đặc biệt, có thể làm như sau:

- Tạo namespace riêng, ví dụ `app.executors.domain.core-size` / `queue-capacity`, được bind rõ qua `@ConfigurationProperties`.
- Dùng values đó khi tạo `ThreadPoolTaskExecutor`.
- Validate giới hạn hợp lệ, ví dụ core không vượt max, queue không âm khi code không xử lý giá trị đặc biệt.
- Ghi metric và test property binding dưới profile production.
- Không đồng thời dùng hai nguồn mơ hồ mà không biết nguồn nào có ưu tiên.

Nếu chọn auto-configuration thuần, tránh tạo bean `Executor` không cần thiết có thể làm thay đổi conditions. Có thể cần một helper component chỉ inject `AsyncTaskExecutor` mà không khai báo executor mới.

## 13. Quan hệ với Spring MVC, WebFlux và transaction context

Spring MVC xử lý request trên servlet container threads, trong khi `@Async` task thường chạy trên executor của ứng dụng; MVC async controller return types có đường tích hợp executor riêng. Trong Boot 3.3, `applicationTaskExecutor` có ý nghĩa đối với một số tích hợp web. Nếu bạn dùng bean tên `domainExecutor` cho `@Async`, **đừng tự suy ra** Spring MVC async request cũng dùng chính bean đó.

Spring WebFlux ưu tiên non-blocking pipeline; không nên dùng pool tùy chỉnh rồi gọi blocking API trực tiếp trên event-loop. Hỗ trợ offload blocking của framework có quy tắc tích hợp riêng; xem hướng dẫn theo phiên bản và stack đang dùng.

Với transaction, Spring `@Transactional` thường gắn với thread thực thi theo cơ chế proxy/context. Chuyển sang `@Async` không khiến transaction ở caller tự động đi theo worker. Security context, MDC, request-scoped state cũng cần propagation có chủ đích. Những vấn đề này tồn tại trong cả Spring Framework thuần và Boot, vì Boot không làm biến mất thread boundary.

Một `TaskDecorator` có thể hỗ trợ sao chép một số context được phép, nhưng cần cleanup và kiểm thử khi thread pool tái sử dụng worker. Không chuyển toàn bộ request object hoặc EntityManager qua worker chỉ để giữ ngữ cảnh.

## 14. Các nguyên nhân lỗi và cách xử lý

| Hiện tượng | Khả năng thường gặp | Bước kiểm chứng |
| --- | --- | --- |
| `@Async` chạy trên request thread | Thiếu `@EnableAsync`, self-invocation, đối tượng không phải bean | In caller/worker thread, kiểm tra proxy và cấu hình |
| Có hai pool, task chạy “sai pool” | Nhiều `Executor`, tên/qualifier/default khác | Kiểm tra `@Async("...")`, `AsyncConfigurer`, bean `taskExecutor` |
| Đổi `spring.task.execution.pool.*` nhưng không thay đổi | Executor tự khai báo hoặc virtual-thread strategy | In actual class/core/max và đọc condition report |
| Không thấy bean `applicationTaskExecutor` | Auto-config back off do custom bean/conditions | Liệt kê beans và xem Boot conditions |
| MVC async không dùng custom `Executor` | Không đúng kiểu `AsyncTaskExecutor` hoặc tên phù hợp | Kiểm tra yêu cầu integration, cấu hình MVC |
| `@Scheduled` không chạy | Chưa enable hoặc method không phải Spring-managed bean | Xem `@EnableScheduling` và context |
| Lịch chạy nhiều lần trên cluster | Mỗi instance có scheduler riêng | Dùng coordination/lock, job idempotency |
| Shutdown còn nhiều task | Queue dài, task chậm, thiếu cancellation/timeout | Theo dõi queue, graceful shutdown và deadline |
| Boot dùng virtual thread nhưng pool-size không tác dụng | Implementation không phải thread pool platform threads | Xem class runtime, property virtual threads |
| Bean executor có tên đúng nhưng không có metric | Thiếu instrument hoặc lấy nhầm bean | Lấy object identity/name, thêm metric đúng pool |
| Lỗi thiếu bean khi đổi từ Framework sang Boot | Tin rằng starter luôn kích hoạt mọi annotation | Xác minh @EnableAsync/@EnableScheduling và conditions |
| Test chạy ổn nhưng production lỗi | Test dùng SyncTaskExecutor, runtime dùng pool async | Test có executor thật và đồng bộ bằng latch |

### 14.1. Thứ tự điều tra khi “properties không có hiệu lực”

1. Lấy chính xác phiên bản Java, Spring Framework và Spring Boot.
2. Xác định tác vụ ở đâu: `@Async` method, MVC async controller, scheduler callback hay gọi trực tiếp `executor.execute()`.
3. Xác định bean thực được chọn bởi qualifier/configurer/integration.
4. In bean name, runtime type, core/max/queue, thread prefix; đo bằng metric.
5. Kiểm tra bean đó do app `@Bean` tạo hay do Boot auto-configured.
6. Kiểm tra effective environment, profiles, properties và config binding.
7. Đọc condition evaluation report để biết tại sao Boot có/không tạo bean mặc định.
8. Sửa ở đúng nguồn cấu hình rồi tái kiểm tra task bằng thread name và load test.

Không tăng max thread khi chưa hiểu queue policy. Với `ThreadPoolExecutor`, pool thường dùng core workers trước, sau đó đưa task vào queue; chỉ khi queue không nhận thêm mới cần tăng worker tới max. Queue vô hạn có thể khiến max không phát huy như kỳ vọng.

## 15. Học qua hai trường hợp so sánh

### Tình huống A: Spring Framework thuần, muốn xử lý import 200 file

Bạn khai báo `@Bean("importExecutor")` với `ThreadPoolTaskExecutor`, đặt core/max/queue và rejection policy. Dùng `@EnableAsync`, đặt `@Async("importExecutor")` trên method thuộc Spring-managed bean và gọi từ bean khác.

Phương pháp kiểm tra: code `@Configuration` là nguồn bean definition; thread prefix xác nhận task dùng đúng pool; context close kiểm tra lifecycle. Nếu muốn chỉnh theo profile, bạn phải chủ động xây cơ chế nhận giá trị cấu hình (hoặc điều kiện bean phù hợp).

### Tình huống B: Spring Boot 3.3.x, ứng dụng MVC cần background work nhẹ

Nếu không cần custom executor, có thể để Boot auto-configure `applicationTaskExecutor`, dùng `spring.task.execution.*` để điều chỉnh. Vẫn bật `@EnableAsync` cho method annotation. Khi thêm custom `Executor` cho import, kiểm tra lại đường auto-configuration và MVC async integration.

Phương pháp kiểm tra: Boot condition report xác định bean auto-config, `ApplicationContext` xác nhận tên/kiểu, thread prefix và metrics xác nhận thực thi; properties chỉ được xem là hiệu lực khi thuộc đúng bean đã bind.

### Cùng một code nghiệp vụ, hai đường cấu hình

```java
@Service
public class ImportService {
    @Async("importExecutor")
    public CompletableFuture<Integer> importFiles(List<String> paths) {
        return CompletableFuture.completedFuture(paths.size());
    }
}
```

Đây là **fragment cần import và project Spring**; bạn phải có bean `importExecutor` trong cả hai môi trường nếu dùng qualifier này. Dùng Spring Boot **không tự suy ra** rằng một bean tên `importExecutor` được tạo bởi auto-configuration: Boot có thể tạo bean mặc định tên khác. Để dùng `@Async` không có qualifier, bạn lại phải kiểm chứng executor mặc định được resolve ở từng môi trường.

## 16. Bài thực hành kiểm tra sự khác biệt

Hãy xây **hai ứng dụng nhỏ** có cùng service `WorkerService` dùng `@Async`, cùng phương thức tính một kết quả đơn giản:

**Phần 1 — Framework thuần:**
1. Tạo `AnnotationConfigApplicationContext`, `@Configuration` và `@EnableAsync`.
2. Định nghĩa `@Bean("manualExecutor") ThreadPoolTaskExecutor` có prefix `manual-`, core 2, max 4, queue 10.
3. Dùng `@Async("manualExecutor")`, gọi qua một Spring-managed bean.
4. In thread name và chờ `CompletableFuture.get(timeout)`; xác nhận prefix.
5. Đóng context và xác minh task không nhận thêm khi executor đã shutdown.

**Phần 2 — Boot:**
1. Tạo dự án Boot 3.3.x, thêm `@SpringBootApplication` và `@EnableAsync`.
2. Không tạo `Executor` tùy chỉnh; đặt `spring.task.execution.thread-name-prefix=boot-` và các thuộc tính pool.
3. Liệt kê bean `applicationTaskExecutor` và quan sát class/runtime config.
4. Thêm `@Bean("customExecutor")` với prefix `custom-`; khởi động lại.
5. Ghi nhận bean nào còn tồn tại, vì sao auto-config có thể back off, method `@Async` sử dụng bean nào.
6. Đặt `@Async("customExecutor")`, kiểm tra task chuyển sang pool đúng.
7. Nếu dùng Spring MVC async, kiểm tra riêng bean `applicationTaskExecutor` và kiểu `AsyncTaskExecutor`.

**Phần 3 — Scheduling và môi trường:**
1. Thêm `@EnableScheduling` và một method `@Scheduled(fixedDelay = 5000)`.
2. Khi chưa có custom scheduler, quan sát Boot scheduler bean khi điều kiện thỏa.
3. Thêm custom `ThreadPoolTaskScheduler`, xem property `spring.task.scheduling.pool.size` còn tác dụng với bean nào.
4. Chạy trên Java 21+ và bật virtual threads (nếu release hỗ trợ), so sánh loại executor/scheduler và ý nghĩa pool-size.
5. Chạy hai replica mô phỏng và xác minh scheduler tại mỗi process; đề xuất cơ chế phối hợp nếu chỉ được chạy một job toàn cụm.

**Phần 4 — Kiểm thử và quan sát:**
1. Dùng queue hữu hạn, nộp nhiều task hơn capacity để quan sát rejection. Không dựa vào `sleep` để đoán thứ tự; dùng latch để giữ worker.
2. Đo core/max, active count, queue depth và độ trễ.
3. Đóng context khi còn task; kiểm tra shutdown policy và timeout.
4. Ghi lại “nguồn cấu hình → bean → caller/annotation → thread → lifecycle” cho từng tình huống.

### Checklist tự đánh giá

- [ ] Tôi phân biệt được `@Configuration`/`@Bean` của Framework và auto-configuration theo điều kiện của Boot.
- [ ] Tôi biết `@EnableAsync`/`@EnableScheduling` và việc tạo resource executor/scheduler là hai hành động riêng.
- [ ] Tôi có thể dự đoán **khi nào** Boot không tạo bean mặc định vì custom `Executor`.
- [ ] Tôi hiểu khác biệt về tên/kiểu bean `taskExecutor`, `applicationTaskExecutor` và qualifier của `@Async`.
- [ ] Tôi không mặc định `spring.task.execution.pool.*` chỉnh mọi custom executor.
- [ ] Tôi biết vì sao Spring MVC async và method `@Async` có thể dùng executor khác nhau.
- [ ] Tôi biết virtual threads làm thay đổi ý nghĩa pool properties ở đường auto-config tương ứng.
- [ ] Tôi xác định được ai sở hữu executor và ai shutdown khi ứng dụng dừng.
- [ ] Tôi có thể chứng minh bằng runtime bean inspection, condition report và test task thực sự chạy.
- [ ] Tôi biết kiểm chứng các quy tắc trên theo **Spring Boot release đang dùng**, thay vì sao chép một default từ version khác.

**Kết luận kiến thức:** Spring Framework cung cấp **cơ chế và abstraction** để cấu hình tài nguyên thực thi; Spring Boot cung cấp **bean và giá trị mặc định có điều kiện** nhằm giảm cấu hình thủ công. Để hiểu ứng dụng concurrency đang chạy ra sao, phải lần theo **annotation → executor/scheduler bean → nguồn tạo bean → properties/binding → thread và vòng đời thực tế**.

## 17. Tài liệu chính thức tham chiếu theo phiên bản

- Spring Framework — [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `TaskExecutor`, `TaskScheduler`, `@EnableAsync`, `@EnableScheduling`, lựa chọn implementation và qualification.
- Spring Boot 3.3 — [Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.3/reference/features/task-execution-and-scheduling.html): điều kiện auto-configured executor/scheduler, bean name `applicationTaskExecutor`, pool properties và virtual threads.
- Spring Boot — [Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html): property sources, profile và quy tắc override.
- Spring Boot — [SpringApplication và Virtual Threads](https://docs.spring.io/spring-boot/reference/features/spring-application.html): JDK yêu cầu, property `spring.threads.virtual.enabled` và keep-alive.

Tài liệu hiện hành có thể thay đổi khi nâng Spring Framework/Boot. Với một dự án cụ thể, **kiểm tra tài liệu khớp phiên bản dependency và bean/condition report tại runtime** trước khi áp dụng mặc định vào production.


</details>

- [Quay lại đầu trang](#back-to-top)
