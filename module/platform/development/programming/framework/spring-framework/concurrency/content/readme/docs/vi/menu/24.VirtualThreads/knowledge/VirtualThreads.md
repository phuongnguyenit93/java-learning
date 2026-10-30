<a id="back-to-top"></a>

# Luồng ảo

## Menu
- [Luồng ảo giải quyết chi phí chờ của luồng mà không làm CPU nhanh hơn](#virtual-threads-001)
- [Yêu cầu JDK và phiên bản Spring hỗ trợ](#virtual-threads-002)
- [Bật virtualThreads cho SimpleAsyncTaskExecutor](#virtual-threads-003)
- [SimpleAsyncTaskScheduler và cách thực thi tương thích luồng ảo](#virtual-threads-004)
- [Không tạo nhóm luồng ảo chỉ để mô phỏng nhóm luồng nền tảng](#virtual-threads-005)
- [Vẫn giới hạn truy cập tài nguyên hữu hạn như kết nối và dịch vụ đích](#virtual-threads-006)
- [ThreadLocal, ngắt và vòng đời vẫn cần được quản lý](#virtual-threads-007)
- [Nhận diện tình huống giữ luồng nền tảng và khác biệt theo phiên bản JDK](#virtual-threads-008)
- [Đo tải thực tế trước khi thay cách thực thi](#virtual-threads-009)

## <a id="virtual-threads-001">Luồng ảo giải quyết chi phí chờ của luồng mà không làm CPU nhanh hơn</a>

<details>
<summary>Xem chi tiết</summary>

# Luồng ảo giải quyết chi phí chờ của luồng mà không làm CPU nhanh hơn

**Module:** Spring Framework - concurrency  
**Nhóm kiến thức:** Luồng ảo  
**Đề bài:** Luồng ảo giải quyết chi phí chờ của luồng mà không làm CPU nhanh hơn.

> **Ghi nhớ:** Virtual threads giúp ứng dụng Java duy trì rất nhiều tác vụ đồng thời đang **chờ I/O** mà không phải giữ một OS thread riêng cho từng tác vụ. Chúng không làm CPU tính nhanh hơn, không tạo thêm lõi CPU và không tăng dung lượng database connection pool.

## 1. Vì sao cần virtual threads?

Một HTTP request có thể mất 150 ms để hoàn tất, nhưng chỉ dùng 4 ms CPU và dành 146 ms còn lại để chờ database hoặc remote HTTP service. Với kiểu thread-per-request truyền thống trên **platform threads**, mỗi request thường giữ một OS thread trong toàn bộ thời gian chờ.

Một server với vài trăm hoặc vài nghìn requests đồng thời có thể phải duy trì rất nhiều OS threads, mỗi thread có stack, tài nguyên kernel và chi phí context switching. Dù CPU còn rảnh, số platform threads và khả năng quản lý chúng có thể giới hạn throughput.

Virtual threads được chuẩn hóa trong **Java 21 / JEP 444** để giữ ưu điểm code đồng bộ, tuần tự, dễ debug nhưng giảm chi phí thread khi phần lớn thời gian là chờ:

```java
Order order = repository.load(orderId);
RiskScore score = remoteRiskService.check(order);
return decide(order, score);
```

Đây là **đoạn minh họa**, các kiểu/method nghiệp vụ thuộc ứng dụng. Trên virtual thread và các thao tác chờ được JVM/thư viện hỗ trợ, request có thể tạm dừng trong lúc chờ query hoặc network response và **nhường carrier thread** để phục vụ request khác. Không cần biến toàn bộ business logic thành các callback lồng nhau chỉ để đạt concurrency cao.

```text
Request A: [CPU 2 ms] [CHỜ JDBC .............] [CPU 1 ms]
Request B:      [CPU] [CHỜ HTTP .................] [CPU]
Request C:           [CPU] [CHỜ JDBC ...........] [CPU]

Platform thread per request:
    Mỗi request giữ riêng một OS thread trong khoảng CHỜ.

Virtual thread per request:
    Request A/B/C có virtual thread riêng.
    Khi chờ được hỗ trợ, JVM unmount virtual thread khỏi carrier.
    Carrier platform thread rảnh để chạy request khác.
```

**Không hiểu nhầm:** virtual thread chỉ tiết kiệm **chi phí giữ OS thread khi chờ**. Một truy vấn SQL chậm 200 ms không tự trở thành truy vấn 20 ms, và CPU thực thi Java bytecode vẫn cần thời gian CPU thật.

## 2. Platform thread, virtual thread và carrier

Java có hai loại `Thread`:

| Thuộc tính | Platform thread | Virtual thread |
| --- | --- | --- |
| Hạ tầng | Gắn với một OS thread trong vòng đời | Do JVM lên lịch trên carrier platform threads |
| Chi phí tạo nhiều thread | Tương đối đắt, có stack OS/kernel | Nhẹ hơn; khả năng tạo rất nhiều thread |
| Khi blocking I/O phù hợp | OS thread thường phải chờ | Có thể unmount, nhường carrier |
| Khi đang tính toán CPU | Chạy trên CPU | Cũng phải chạy trên CPU |
| API | `Thread`, `Runnable`, `ExecutorService` | Cùng API, `Thread.isVirtual()` phân biệt |
| Tái sử dụng | Thường dùng thread pool để giảm chi phí | **Không pool virtual threads**; thread-per-task |
| Thread-local | Theo từng platform thread | Theo từng virtual thread; cần để ý chi phí bộ nhớ |
| Thích hợp | Tác vụ CPU hoặc pool có giới hạn | Nhiều tác vụ concurrent chủ yếu chờ I/O |

**Carrier** là platform thread được JVM dùng để chạy một virtual thread trong những đoạn cần CPU. Virtual thread có thể được **mount** trên carrier để thực thi, **unmount** khi đợi hoạt động blocking tương thích, và sau đó **remount** trên một carrier khác khi sự kiện chờ hoàn tất.

`Thread.currentThread()` trong code của virtual thread luôn trả về chính virtual thread, không trả về carrier. Một virtual thread vẫn có stack trace, exception, interrupt, `ThreadLocal` và nhiều khả năng lập trình thông thường.

### Mount/unmount theo thời gian

```text
Carrier #1:
 [VT-A làm CPU] [VT-B làm CPU] [VT-C làm CPU] ... [VT-A tiếp tục]

VT-A:
       chạy --> chờ database ..................... --> tiếp tục

VT-B:
                 chạy --> chờ HTTP .............. --> tiếp tục

VT-C:
                                chạy --> chờ DB ...........
```

Không có ánh xạ một-một cố định giữa carrier và virtual thread. Số virtual threads đang tồn tại có thể lớn hơn nhiều số carriers đang chạy CPU thực tế.

Tuy nhiên **không phải mọi thao tác chờ đều tự unmount** ở mọi phiên bản JDK và mọi thư viện. Native/foreign calls, các điểm giữ carrier và pinning có thể giới hạn scalability. Cần kiểm tra bằng JVM diagnostics thay vì chỉ nhìn số lượng virtual threads.

## 3. Mấu chốt: scale khác speed

Oracle mô tả virtual threads như một cách cải thiện **scale (throughput)**, không phải tăng **speed (latency một request)**.

- **Concurrency:** số công việc đang tồn tại (đang chạy hoặc đang chờ).
- **Parallelism:** số công việc thật sự chạy CPU trong cùng thời điểm.
- **Throughput:** số công việc hoàn tất trên một đơn vị thời gian.
- **Latency:** thời gian hoàn tất một công việc cụ thể.

Virtual threads có thể giúp **concurrency** rất cao, từ đó tăng **throughput** nếu trước đây hệ thống thiếu platform threads để chịu các thao tác I/O chờ lâu. Nhưng chúng không tăng **parallelism CPU** vượt khả năng phần cứng và không bảo đảm **latency** giảm.

### Một ví dụ định lượng với Little's Law

Giả sử mỗi request có:

- 3 ms CPU;
- 147 ms chờ I/O;
- 150 ms thời gian xử lý trung bình khi hệ thống không bị nghẽn.

Nếu muốn phục vụ 2.000 requests/giây, số requests trung bình đang tồn tại:

```text
L = arrival_rate × average_latency
  = 2.000 requests/giây × 0,150 giây
  = 300 concurrent requests
```

Tổng nhu cầu CPU:

```text
2.000 requests/giây × 0,003 CPU-giây/request
= 6 CPU-giây trên một giây thực
```

Tức là về lý thuyết cần khoảng **6 core hoạt động 100%** chỉ cho phép tính này, chưa kể OS/GC và chi phí phụ. Tạo 300 virtual threads giúp tránh giữ 300 OS threads khi nhiều request chờ, **không biến một máy 4 core thành 6 core**.

Nếu tăng tải đến mức CPU 100%, virtual threads có thể không còn tăng throughput dù request vẫn có I/O wait. Bottleneck đã chuyển từ chi phí platform threads sang CPU, DB hoặc downstream.

### Bảng dự đoán lợi ích

| Workload | Tác dụng kỳ vọng | Lý do |
| --- | --- | --- |
| Spring MVC + JDBC blocking, rất nhiều requests | Thường phù hợp để thử virtual threads | Nhiều request chờ, có thể giải phóng carrier |
| HTTP service gọi API đối tác độ trễ cao | Có thể tăng concurrency/throughput | Chờ network nhiều |
| Tính hash/mã hóa/parse JSON nặng | Không làm phép tính nhanh hơn | CPU-bound |
| Xử lý video hoặc matrix liên tục | Không tăng CPU core hay SIMD | Vẫn cạnh tranh CPU |
| HikariCP chỉ có 10 connections | Không tăng số query được phép qua pool | Connection limit vẫn giữ nguyên |
| Remote API có quota 100 RPS | Không nới quota | Rate limit do downstream |
| Có nhiều native blocking calls | Lợi ích có thể bị giới hạn | Carrier pinning/tài nguyên native |
| Hàng triệu task nằm chờ permit | Có thể tốn heap rất lớn | Virtual threads không miễn phí |

## 4. API Java 21+ để tạo virtual threads

Các API chính thức từ **JDK 21**:

```java
Thread t = Thread.ofVirtual()
        .name("report-vt")
        .start(() -> runReport());

t.join();
```

Đây là **đoạn minh họa**, `runReport` thuộc ứng dụng. `t.join()` giúp caller đợi completion và **vẫn là một thao tác chờ** đối với logical caller. Lệnh này không tự biến một job đồng bộ thành non-blocking từ góc nhìn người gọi.

Cách thuận tiện cho tác vụ độc lập:

```java
try (ExecutorService executor =
             Executors.newVirtualThreadPerTaskExecutor()) {

    Future<Result> future =
            executor.submit(() -> fetchResult());

    Result result = future.get();
    process(result);
}
```

Đoạn minh họa cần `Result`, `fetchResult` và `process`. `newVirtualThreadPerTaskExecutor()` **tạo virtual thread mới cho mỗi task**, số lượng task được nhận không bị giới hạn bởi một fixed-size pool; do đó cần admission control và quota tài nguyên riêng. `Future.get()` chặn logical caller khi chờ kết quả. `ExecutorService.close()` theo try-with-resources sẽ chờ các task đã nhận kết thúc trước khi đóng executor.

**Không pool virtual threads.** Platform threads được pool vì OS thread đắt. Với virtual threads, pattern dự kiến là một virtual thread cho mỗi logical request/task, không xây “fixed virtual-thread pool size=100” chỉ để bắt chước cấu hình cũ. Giới hạn **tài nguyên bị tranh chấp** bằng semaphore, bounded request queue, rate limiter hoặc connection pool.

### `CompletableFuture` không tự đổi executor vì ta bật virtual threads

`CompletableFuture.supplyAsync(supplier)` **không truyền executor** vẫn dùng quy tắc executor mặc định của JDK, thường là `ForkJoinPool.commonPool()`. Nó không tự được gửi sang Spring virtual executor.

Nếu một method `@Async` đã chạy trên virtual thread rồi gọi thêm `supplyAsync` và `.join()` để chờ, có thể tạo thêm một scheduling boundary vô ích. Nếu business work đã nằm trong `@Async` method, tính trực tiếp rồi dùng `CompletableFuture.completedFuture(value)` sẽ rõ ràng hơn.

## 5. Ví dụ chạy độc lập trên JDK 21

Chương trình sau chứng minh:

- 12 logical tasks có thể cùng chờ một latch mà không tạo 12 platform threads riêng cho chúng.
- Các task đều thực thi trên virtual threads.
- `Semaphore(2)` giới hạn concurrent work tại một điểm tài nguyên, dù executor nhận nhiều tasks.
- Không tuyên bố tốc độ CPU tăng và không phụ thuộc thời gian chạy máy để in kết quả.

Tệp **`VirtualThreadWaitDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualThreadWaitDemo {

    public static void main(String[] args) throws Exception {
        int tasks = 12;
        CountDownLatch started = new CountDownLatch(tasks);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {

            List<Future<Boolean>> futures = new ArrayList<>();

            for (int i = 0; i < tasks; i++) {
                futures.add(executor.submit(() -> {
                    started.countDown();
                    release.await();
                    return Thread.currentThread().isVirtual();
                }));
            }

            boolean ready = started.await(10, TimeUnit.SECONDS);
            release.countDown();

            if (!ready) {
                throw new IllegalStateException(
                        "Tasks did not reach latch");
            }

            int virtualWorkers = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) {
                    virtualWorkers++;
                }
            }

            System.out.println("submitted=" + tasks);
            System.out.println("allReady=" + ready);
            System.out.println("virtualWorkers=" + virtualWorkers);
        }

        Semaphore slots = new Semaphore(2);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {

            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    slots.acquire();
                    try {
                        int current = active.incrementAndGet();
                        peak.accumulateAndGet(current, Math::max);

                        // Chờ giả lập, KHÔNG phải đo benchmark JDBC.
                        Thread.sleep(20);
                    } finally {
                        active.decrementAndGet();
                        slots.release();
                    }
                    return null;
                }));
            }

            for (Future<Void> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            System.out.println("boundedTasks=8");
            System.out.println("peakAtMost2=" + (peak.get() <= 2));
            System.out.println("activeAfter=" + active.get());
        }
    }
}
```

Cần **Java 21 trở lên**. Java 17 không có `Executors.newVirtualThreadPerTaskExecutor()` chính thức. Chạy:

```bash
javac VirtualThreadWaitDemo.java
java VirtualThreadWaitDemo
```

**Stdout khi chương trình hoàn tất bình thường:**

```text
submitted=12
allReady=true
virtualWorkers=12
boundedTasks=8
peakAtMost2=true
activeAfter=0
```

Phân tích:

1. `allReady=true`: 12 virtual threads đã tới trạng thái chờ trước khi được thả.
2. `virtualWorkers=12`: `Thread.currentThread().isVirtual()` trả true cho tất cả.
3. `boundedTasks=8`: có tám tasks trong pha thứ hai.
4. `peakAtMost2=true`: semaphore bảo vệ **chỉ hai permits**; dù tám virtual threads tồn tại, tối đa hai task cùng dùng vùng tài nguyên.
5. `activeAfter=0`: `finally` trả permits và cập nhật bộ đếm, không để sót lượt giữ permit.
6. `Thread.sleep(20)` chỉ là điểm chờ mô phỏng. Không suy ra HTTP client hoặc driver đang dùng chắc chắn unmount, cũng không dùng thời gian demo để khẳng định hiệu năng.

Các `Future.get(timeout)` và latch có deadline để test không chờ vô hạn. Khi máy bị nghẽn quá mức, demo có thể ném timeout; **stdout trên là kỳ vọng khi chạy thành công**, không phải cam kết tuyệt đối về scheduling thời gian thực.

## 6. Vì sao các công việc CPU-bound không nhanh lên?

CPU phải thực hiện cùng số phép cộng, mã hóa, kiểm tra điều kiện hoặc phép biến đổi. Một ví dụ:

```text
100 jobs × 100 ms CPU mỗi job
= 10 CPU-seconds công việc thật

Máy 4 cores:
Thời gian hoàn thành lý tưởng thấp nhất xấp xỉ 2,5 giây
(chưa tính GC, OS, contention, sync và scheduling).
```

Chạy bằng 100 virtual threads **không** biến 10 CPU-seconds thành 2 CPU-seconds. Nếu tất cả đều liên tục tiêu thụ CPU, JVM vẫn phải đưa các virtual threads lên carrier/OS threads chạy trên bốn cores hiện có.

```text
I/O-bound:
[CPU ngắn][CHỜ ...................][CPU ngắn]
           ^ carrier có thể chạy task khác

CPU-bound:
[CPU CPU CPU CPU CPU CPU CPU ...]
           ^ CPU đang bận thật, không có thời gian chờ để nhường
```

Một hệ thống hỗn hợp có thể hưởng lợi ban đầu rồi CPU trở thành bottleneck mới khi lượng request tăng. Vì vậy cần đo CPU utilization, throughput, p95/p99 latency và CPU profile, không chỉ nhìn số thread.

**Khi công việc nặng CPU:** sử dụng một bounded executor hoặc cơ chế giới hạn parallelism thích hợp với số cores, batch size và memory; đừng dùng hàng nghìn virtual threads làm một pool CPU chạy nhanh hơn.

## 7. Ranh giới năng lực thật: database, HTTP và bộ nhớ

Virtual threads không làm:

- HikariCP pool 20 connections thành 20.000 connections.
- Database server có thêm CPU/IOPS/transaction throughput.
- Remote API bỏ quota, rate limiting hoặc circuit breaker.
- Một lock/transaction dài trở thành không tranh chấp.
- JVM có vô hạn heap để giữ hàng triệu request payloads, stack frames và ThreadLocal values.
- Retry storm biến mất khi downstream lỗi.

Khi bottleneck là connection pool, tạo nhiều virtual threads hơn có thể chỉ làm nhiều tasks **đứng chờ connection** hơn, tăng latency và áp lực heap.

### Pattern giới hạn I/O đúng chỗ

```java
private final Semaphore dbPermits = new Semaphore(20);

public Report load(String reportId) throws InterruptedException {
    if (!dbPermits.tryAcquire(200, TimeUnit.MILLISECONDS)) {
        throw new ServiceBusyException("DB is at capacity");
    }

    try {
        return repository.load(reportId);
    } finally {
        dbPermits.release();
    }
}
```

Đây là đoạn **thành viên class minh họa**, cần imports `Semaphore`, `TimeUnit` và các kiểu `Report`, `ServiceBusyException`, `repository` của ứng dụng.

`tryAcquire(timeout)` tránh việc request tích lũy vô hạn khi backend quá tải; nhưng **admission control ở ingress** vẫn quan trọng. Nếu hàng triệu request đều được nhận trước khi chờ semaphore, ứng dụng vẫn có thể hết bộ nhớ dù mọi request dùng virtual thread.

Semaphore trong một JVM cũng **không giới hạn toàn bộ các replicas**. Rate limit/lease trên cluster là một bài toán khác.

### Pool virtual thread không phải giải pháp quota

Không nên tạo “virtual thread pool 100 threads” chỉ vì DB có 100 connections. Đó là hai loại tài nguyên khác nhau. Hãy cho logical task có virtual thread riêng **khi đã chấp nhận request**, còn quyền truy cập DB phụ thuộc connection pool/semaphore và timeouts. Nếu ứng dụng cần CPU worker pool, có thể dùng platform threads hữu hạn cho đoạn CPU nặng và virtual threads cho I/O waiting.

## 8. Pinning: khác nhau giữa JDK 21 và JDK 24

**Pinning** nghĩa là virtual thread phải tiếp tục chiếm carrier dù đang chờ, làm carrier không thể phục vụ virtual thread khác. Đây là một nguyên nhân có thể khiến ứng dụng không scale như dự kiến.

### Với JDK 21–23

Trên những phiên bản này, blocking I/O xảy ra trong `synchronized` block/method có thể **pin carrier**; native/foreign function calls cũng có nguy cơ. Ví dụ:

```java
private final Object monitor = new Object();

void refresh() {
    synchronized (monitor) {
        blockingRemoteCall();
    }
}
```

Đây là đoạn minh họa với method `blockingRemoteCall` của ứng dụng. Nếu `blockingRemoteCall()` chờ lâu dưới monitor trên JDK 21, carrier có thể bị giữ trong suốt thời gian đó.

**Cách xử lý trên JDK 21:** giảm thời gian giữ monitor, tránh I/O lâu dưới `synchronized`; cân nhắc `ReentrantLock` khi semantics cho phép; đo JFR pinning events. Không phải mọi đoạn `synchronized` ngắn đều có vấn đề.

### Từ JDK 24: JEP 491

**JEP 491 — Synchronize Virtual Threads without Pinning** cải tiến cơ chế monitor/synchronized, loại bỏ nguồn pinning do chờ synchronized theo kiểu cũ. Bởi vậy câu “cứ có synchronized là virtual threads bị pin” là **lời khuyên lỗi thời đối với JDK 24+**.

Một số native/foreign calls và điểm runtime khác vẫn có thể giữ carrier; JDK 24 không xóa mọi rủi ro. Đánh giá theo JVM build, driver, thư viện và workload đang chạy; JDK mới hơn cũng không tăng CPU cores.

### Diagnosing

- **JDK Flight Recorder (JFR)** giúp xem CPU, virtual-thread-related events, pinning khi có và lock contention.
- `jcmd <PID> Thread.dump_to_file -format=json <file>` tạo dump có virtual threads trên JVM hỗ trợ.
- `-Djdk.tracePinnedThreads=full` là công cụ phù hợp cho chẩn đoán **JDK 21**; tùy các bản mới mà lựa chọn diagnostic khác.
- Một virtual thread ở trạng thái waiting không phải lỗi: mục tiêu là cho **nhiều virtual threads chờ rẻ hơn**, không phải loại bỏ sự chờ của nghiệp vụ.

## 9. Spring Boot 3.5: bật virtual threads khi đủ điều kiện

Spring Boot 3.5 hỗ trợ virtual threads trên **JDK 21+**. Trong `application.yml`:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Khi không có custom `Executor` bean làm auto-configuration back off, Boot sẽ tự cấu hình `AsyncTaskExecutor` là `SimpleAsyncTaskExecutor` dùng virtual threads. Nếu virtual threads tắt, mặc định thường là `ThreadPoolTaskExecutor`.

Dù có property trên, **không có nghĩa tất cả task đều tự chuyển sang virtual**:

- App tạo `Executors.newFixedThreadPool()` thì nó vẫn dùng platform threads nếu không dùng virtual ThreadFactory.
- `CompletableFuture.supplyAsync(...)` không truyền executor thường dùng JDK common pool.
- Custom `ThreadPoolTaskExecutor`, `AsyncConfigurer`, `@Async("namedExecutor")` có thể quyết định thread model riêng.
- Thread thực tế của một request Spring MVC/`@Async` còn phụ thuộc stack và auto-configuration tại runtime.
- Spring WebFlux/Reactor có event-loop semantics riêng; không tự nhanh hơn chỉ vì bật property.

Nếu cần xác minh, log/test `Thread.currentThread().isVirtual()` trong chính callback và kiểm tra executor bean được chọn.

### `@Async` và proxy

```java
@Service
public class ReportService {

    @Async
    public CompletableFuture<Boolean> check() {
        return CompletableFuture.completedFuture(
                Thread.currentThread().isVirtual());
    }
}
```

Đây là class minh họa, cần `@Service`, `@Async`, `CompletableFuture`, `@EnableAsync` và lời gọi qua Spring-managed proxy. `completedFuture` không submit task thứ hai: phương thức đã được proxy `@Async` gửi vào executor.

Nếu gọi `this.check()` trong cùng bean, proxy async thường bị bỏ qua. Nếu trong `@Async` body lại gọi `supplyAsync` mặc định, bạn có thể tạo **hai tầng scheduling** không cần thiết, inner không được bảo đảm chạy virtual.

## 10. Spring Boot runnable demo: `@Async` trên virtual thread

Ứng dụng CLI sau dùng **JDK 21+**, **Spring Boot 3.5.7**, Maven. Nó không mở web server; `@Async` dùng Boot auto-configured virtual executor theo property và `main` đọc kết quả.

### Cấu trúc dự án

```text
spring-virtual-demo/
├── pom.xml
└── src/main/
    ├── java/com/example/virtualdemo/
    │   ├── VirtualDemoApplication.java
    │   └── AsyncJobService.java
    └── resources/application.yml
```

### `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.7</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>spring-virtual-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>21</java.version>
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

### `src/main/resources/application.yml`

```yaml
spring:
  threads:
    virtual:
      enabled: true
  main:
    banner-mode: "off"
    log-startup-info: false

logging:
  level:
    root: OFF
```

### `src/main/java/com/example/virtualdemo/AsyncJobService.java`

```java
package com.example.virtualdemo;

import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncJobService {

    @Async
    public CompletableFuture<Boolean> isVirtual() {
        return CompletableFuture.completedFuture(
                Thread.currentThread().isVirtual());
    }
}
```

### `src/main/java/com/example/virtualdemo/VirtualDemoApplication.java`

```java
package com.example.virtualdemo;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class VirtualDemoApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication app =
                new SpringApplication(VirtualDemoApplication.class);

        app.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext context = app.run(args)) {
            AsyncJobService jobs =
                    context.getBean(AsyncJobService.class);

            boolean virtual =
                    jobs.isVirtual().get(5, TimeUnit.SECONDS);

            System.out.println("asyncVirtual=" + virtual);
        }
    }
}
```

Chạy tại thư mục dự án:

```bash
mvn -q spring-boot:run
```

**Dòng `System.out` mong đợi khi chạy thành công với JDK 21+ và cấu hình trên:**

```text
asyncVirtual=true
```

Maven/JVM có thể in thông tin công cụ; code đặt logging mức OFF để tập trung vào kết quả. Nếu kết quả `false`, kiểm tra:

1. `java -version` của Maven có thật từ JDK 21+ không?
2. `application.yml` có được load, property có đúng không?
3. `@EnableAsync` có trong Spring context chưa?
4. Lời gọi có đi qua Spring proxy hay bị self-invocation?
5. Có bean `Executor`/`AsyncConfigurer` tự khai báo làm đổi auto-configuration không?

**Phân biệt:** `main` dùng `Future.get(timeout)` chỉ để demo hoàn tất trước khi in, không phải hướng dẫn block servlet request trong production.

### Executor tường minh trong Spring Framework

Có thể tạo executor riêng khi muốn tách loại workload:

```java
@Bean("ioVirtualExecutor")
public SimpleAsyncTaskExecutor ioVirtualExecutor() {
    SimpleAsyncTaskExecutor executor =
            new SimpleAsyncTaskExecutor("io-vt-");
    executor.setVirtualThreads(true);
    executor.setConcurrencyLimit(100);
    return executor;
}
```

Đây là method minh họa thuộc class `@Configuration`, cần imports Spring `@Bean`, `SimpleAsyncTaskExecutor`. Sau đó `@Async("ioVirtualExecutor")` chọn bean này. `SimpleAsyncTaskExecutor` **không tái sử dụng một pool virtual threads**, mà tạo thread cho các task; concurrency limit là một chính sách riêng để bảo vệ workload, không phải giới hạn carrier cores của JVM.

## 11. Scheduler, daemon threads và lifecycle

Virtual threads là **daemon threads**: chúng không mặc nhiên ngăn JVM kết thúc khi không còn non-daemon threads.

Một Spring Boot application chỉ có `@Scheduled` hoặc background jobs mà không có web-server non-daemon thread có thể kết thúc ngoài ý muốn nếu dựa vào virtual threads để giữ JVM sống. Spring Boot có property:

```yaml
spring:
  threads:
    virtual:
      enabled: true
  main:
    keep-alive: true
```

`spring.main.keep-alive` giúp ứng dụng tiếp tục sống khi không còn non-daemon threads. Nó **không** giúp job chịu được máy chết, crash hay application restart.

Khi bật virtual threads, Spring Boot có thể auto-configure `SimpleAsyncTaskScheduler`; những property pool-size của scheduler truyền thống không còn ý nghĩa cũ. Riêng **fixed-delay** trên `SimpleAsyncTaskScheduler` chạy trên scheduler thread đơn theo semantics implementation, nên không mặc định suy ra mọi scheduled callback sẽ có virtual worker riêng.

Kiểm tra `@Scheduled`, `@Async`, executor/scheduler beans, lifecycle shutdown và cấu hình resource limits độc lập.

## 12. ThreadLocal, transaction và context propagation

Virtual threads **vẫn là `Thread` thật ở tầng Java**, nên hỗ trợ `ThreadLocal`. Nhưng virtual-thread-per-task có thể làm số lượng thread rất lớn. Vì vậy:

- `ThreadLocal.withInitial()` tạo cache nặng cho mỗi virtual thread có thể làm tăng heap/GC đáng kể so với ít platform pool workers tái sử dụng.
- MDC, trace ID và SecurityContext nên được capture/restore có kiểm soát khi đổi thread. Giá trị gắn với HTTP caller không tự đi sang mọi thread mới.
- `@Transactional` thường gắn tài nguyên vào thread hiện tại; không copy JDBC connection, EntityManager hoặc transaction của caller sang virtual worker.
- Nếu method `@Async` thao tác database có transaction, thiết lập transaction boundary thích hợp **trong** worker.
- Đừng giữ `HttpServletRequest` hoặc request-scope resource ngoài vòng đời HTTP request chỉ vì virtual thread rẻ.
- Truyền dữ liệu nghiệp vụ bằng parameters/DTO bất biến thường rõ ràng hơn việc dùng ThreadLocal làm state bắt buộc.

Context propagation có sẵn từ Spring/Micrometer/Spring Security cho những context tương ứng; không phải một flag virtual threads tự copy mọi ThreadLocal của ứng dụng.

## 13. Pinpoint hiệu năng, overload và cancellation

Tăng concurrent tasks có thể làm bottleneck chuyển sang CPU, DB hoặc downstream. Rollout production nên đo:

1. `CPU utilization`, GC, heap và allocation rate.
2. Throughput, p50/p95/p99 request latency và error rates.
3. Số requests đang hoạt động/chờ.
4. HikariCP active/idle/pending connections và connection acquisition latency.
5. Downstream 429/503, timeout, retry storm và HTTP connection limits.
6. JFR pinning, carrier contention và thread dumps theo JVM/version.
7. Semaphore/queue wait, rejection/timeout, số inflight tasks được nhận.
8. Cancellation/interrupt, graceful shutdown và context cleanup.

**Không dùng `Thread.sleep()` như một performance benchmark.** Ví dụ JDK ở trên dùng sleep chỉ để tạo chờ mô phỏng; không đại diện một JDBC driver hay remote API.

Với `Future.cancel(true)`, virtual thread có thể nhận interrupt, nhưng operation database/HTTP cụ thể có dừng đúng lúc không phụ thuộc driver/client. `Future.get(timeout)` chỉ giới hạn thời gian caller chờ, không đảm bảo task và I/O phía sau đã được hủy. Cần deadline và timeout thực ở I/O client, cleanup bằng `finally`, idempotency khi retry.

Nếu lượng concurrent tasks vẫn tăng không giới hạn, hãy giới hạn request trước khi spawn các tasks, vì virtual threads **nhẹ chứ không miễn phí**.

## 14. Khi nào nên sử dụng virtual threads?

| Tình huống | Quyết định có cơ sở |
| --- | --- |
| Spring MVC blocking nhiều JDBC/HTTP requests | Đáng thử, đo throughput/latency với workload thật |
| Remote APIs chậm khiến nhiều threads ngồi chờ | Có thể hưởng lợi, bắt buộc có rate limit/timeout |
| Hashing, compression, video, CPU-heavy parsing | Không kỳ vọng nhanh hơn; dùng bounded CPU parallelism |
| WebFlux/Reactor đã non-blocking tốt | Không bật theo phong trào; đo lợi ích thực tế |
| HikariCP chỉ 10 connections | Đừng kỳ vọng hơn 10 queries qua pool đó |
| JDK 21–23 và synchronized blocking lâu | Profile pinning, thu hẹp monitor/upgrade JDK |
| JDK 24+ | Monitor pinning được cải thiện nhưng CPU/DB vẫn hữu hạn |
| Scheduled-only Spring Boot app | Cân nhắc keep-alive và scheduler semantics |
| Job không được mất khi restart | Dùng durable job queue/store, không dựa vào thread model |

**Quy trình triển khai an toàn:**

1. Đo bottleneck hiện tại: threads đang chờ, CPU, DB pool, queue và remote latency.
2. Kiểm tra yêu cầu **JDK 21+** và khả năng tương thích của Spring/framework/libraries.
3. Bật trên một nhóm service I/O-bound có đối chứng.
4. Giữ admission control và giới hạn tài nguyên downstream.
5. Quan sát JFR/pinning, throughput và p95/p99; đừng chỉ nhìn số virtual threads.
6. Kiểm tra transaction, ThreadLocal, error handling, shutdown và cancellation.
7. Rollout rộng hơn khi số đo thực tế tốt hơn và không làm downstream bị nghẽn.

## 15. Các lỗi phổ biến

| Nhầm lẫn | Vì sao sai? | Cách sửa |
| --- | --- | --- |
| “Virtual thread tăng tốc CPU” | Không tăng cores hay làm instruction nhanh hơn | Tối ưu CPU code/parallelism thật |
| Cứ tạo nhiều virtual threads là throughput sẽ tăng mãi | DB/CPU/memory/quota có giới hạn | Đo bottleneck và admission control |
| Tạo fixed pool virtual threads để reuse | Không cần thiết với thread-per-task model | Tạo một virtual thread mỗi accepted task |
| Gửi hàng triệu requests chờ semaphore | Heap và latency tăng | Giới hạn trước khi nhận/spawn tasks |
| Tăng Hikari pool vô hạn | DB quá tải | Capacity planning và query optimization |
| `@Async` luôn virtual khi bật property | Có thể có custom executor hoặc self-invocation | Kiểm tra Spring proxy và executor |
| `CompletableFuture.supplyAsync` mặc định dùng virtual | Thường dùng common pool | Truyền executor hoặc không cần nested async |
| `synchronized` luôn pin carrier trên mọi JDK | JDK 24 có JEP 491 | Phân biệt Java version |
| Cho rằng mọi native I/O đều unmount | Không đúng trong mọi thư viện/JVM | JFR và integration load test |
| ThreadLocal làm cache lớn từng task | Hàng loạt allocations, GC | Cache đúng scope |
| Dùng virtual threads để giữ JVM sống | Virtual threads là daemon | Keep-alive và lifecycle đúng |
| `get(timeout)` tự hủy JDBC query | Chỉ timeout bên caller | Timeout/cancel ở client |
| Bật VT trong WebFlux rồi mặc định nhanh hơn | Hai mô hình concurrency khác nhau | Đo và giữ simplicity |
| Không có metrics trước/sau migration | Không biết bottleneck mới | Baseline, A/B, profile |

## 16. Câu hỏi tự kiểm tra

**1.** Virtual threads có tăng tốc phép tính CPU không?  
**Đáp:** Không. CPU instructions và lõi CPU vẫn như cũ.

**2.** Ở đâu virtual threads hữu ích nhất?  
**Đáp:** Nhiều concurrent tasks phần lớn chờ I/O, như blocking JDBC/HTTP calls được runtime hỗ trợ.

**3.** “Carrier” là gì?  
**Đáp:** Platform thread mà JVM dùng để chạy virtual thread khi nó đang thực thi; virtual thread có thể unmount/remount.

**4.** `newVirtualThreadPerTaskExecutor()` có phải fixed virtual thread pool không?  
**Đáp:** Không. Mỗi task dùng một virtual thread mới; số task không được giới hạn bởi pool cố định.

**5.** Máy 4 core chạy 1.000 CPU tasks liên tục có làm tổng CPU nhanh hơn nhờ virtual threads?  
**Đáp:** Không; chúng vẫn cạnh tranh cùng CPU và có thể tăng overhead.

**6.** Database pool 10 connections phục vụ bao nhiêu queries đồng thời từ 1.000 virtual threads?  
**Đáp:** Qua pool đó vẫn bị giới hạn theo số connection/transaction dùng được, không thành 1.000.

**7.** Bật `spring.threads.virtual.enabled=true` có tự thay mọi custom executor không?  
**Đáp:** Không. Phụ thuộc Boot auto-config và các bean/qualifier được chọn.

**8.** `@Async` method dùng `completedFuture(value)` có tạo thêm virtual thread thứ hai không?  
**Đáp:** Không. Tạo `completedFuture` chỉ chứa kết quả, không submit task mới.

**9.** Tại sao phải phân biệt JDK 21 và JDK 24 với synchronized?  
**Đáp:** Pinning do monitor trên JDK 21 có thể giảm scalability; JDK 24/JEP 491 cải thiện cơ chế này.

**10.** Virtual threads có tự giữ JVM sống không?  
**Đáp:** Không. Chúng là daemon threads; với scheduled-only app cần cấu hình lifecycle/keep-alive phù hợp.

**11.** `Future.get(timeout)` có chắc đã hủy remote HTTP request khi quá hạn không?  
**Đáp:** Không. Phải có timeout/cancellation ở client thực.

**12.** Virtual threads tăng throughput nhưng p99 latency xấu đi có thể xảy ra không?  
**Đáp:** Có, nếu lượng concurrency tăng làm DB/CPU/downstream bị nghẽn. Phải đo và giới hạn tải.

## 17. Tóm tắt và tài liệu chính thức

**Một câu:** *Virtual threads giúp rất nhiều request **đợi rẻ hơn**, không làm phần **tính CPU nhanh hơn***.

- JDK **21+**: `Thread.ofVirtual()` hoặc `Executors.newVirtualThreadPerTaskExecutor()`.
- Nhiều virtual threads được JVM sắp trên carrier platform threads; I/O wait tương thích cho phép nhường carrier.
- Mục tiêu: **scalability/throughput** của workload chờ nhiều, không đảm bảo giảm latency.
- Không pool virtual threads; quản lý **DB connections, CPU, quotas, heap** và admission control.
- Java 21–23 có rủi ro monitor pinning; **JDK 24/JEP 491** cải thiện synchronized-related pinning.
- Spring Boot 3.5: `spring.threads.virtual.enabled=true` với JDK 21+ tác động tới auto-configured executor/scheduler, không tự thay mọi custom executor.
- Kiểm tra `@Async` proxy, ThreadLocal, transaction boundaries, daemon/keep-alive và cancellation.
- Đo hiệu năng thực; thread count riêng lẻ không đủ để khẳng định thành công.

**Tài liệu tham khảo:**

- [Oracle Java 21 — Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html): carrier, blocking, scalability và diagnostics.
- [OpenJDK JEP 444 — Virtual Threads](https://openjdk.org/jeps/444): chuẩn hóa virtual threads từ Java 21.
- [OpenJDK JEP 491 — Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491): cải tiến JDK 24.
- [Oracle Java 24 — Significant Changes](https://docs.oracle.com/en/java/javase/24/migrate/significant-changes-jdk-24.html): đồng bộ virtual threads không pin carrier.
- [Java 21 — `Executors` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html): `newVirtualThreadPerTaskExecutor`.
- [Spring Boot 3.5 — Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html): `SimpleAsyncTaskExecutor`, auto-config và `@Async`.
- [Spring Boot 3.5 — Spring Application](https://docs.spring.io/spring-boot/3.5/reference/features/spring-application.html): enable virtual threads, daemon và keep-alive.
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): Spring executors và virtual-thread configuration.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-002">Yêu cầu JDK và phiên bản Spring hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Yêu cầu JDK và phiên bản Spring hỗ trợ

## 1. Bối cảnh: bật virtual threads cần cả JVM lẫn framework đúng phiên bản

**Virtual threads (luồng ảo)** là tính năng của Java giúp xử lý rất nhiều công việc đồng thời, đặc biệt các công việc **chờ I/O** theo cách viết đồng bộ thông thường. Virtual thread vẫn là một `java.lang.Thread`, nhưng được JVM lập lịch lên một số lượng platform threads (carrier threads); khi gặp nhiều thao tác blocking I/O được hỗ trợ, JVM có thể tháo virtual thread khỏi carrier để carrier làm việc khác.

Trong ứng dụng Spring, sử dụng virtual threads có ít nhất **ba tầng tương thích**:

1. **JDK đang chạy JVM:** phải hỗ trợ API và runtime virtual threads.
2. **Spring Framework:** phiên bản nào cung cấp integration như `VirtualThreadTaskExecutor`, `SimpleAsyncTaskExecutor#setVirtualThreads` và `SimpleAsyncTaskScheduler`.
3. **Spring Boot (nếu dùng):** phiên bản nào hỗ trợ bật virtual threads bằng property, đồng thời auto-configure executor/scheduler/embedded server **theo điều kiện**.

Không thể chỉ thêm một dòng YAML rồi kết luận mọi luồng thực thi trong ứng dụng đã thành virtual threads. Ngược lại, **có thể sử dụng virtual threads chỉ bằng JDK 21 mà không cần Spring Boot**; Spring Framework/Boot cung cấp những cách tích hợp và quản lý thuận tiện hơn.

**Mục tiêu học tập:**

- Phân biệt Java 19–20 preview với Java 21+ chính thức.
- Nêu chính xác yêu cầu runtime, compiler, Maven/Gradle toolchain và container image.
- Hiểu mốc Spring Framework **6.1+** cho virtual-thread integration chuyên dụng và Spring Boot **3.2+** cho property tự động cấu hình.
- Phân biệt baseline Java 17 của Spring Framework 6.x với **Java 21 bắt buộc để bật virtual threads chính thức**.
- Chọn executor/scheduler, hiểu cấu hình `@Async`, `@Scheduled` và những trường hợp Boot auto-configuration không áp dụng.
- Chạy được ví dụ JDK 21 và Spring Framework trên JDK 21 có kiểm tra `Thread.isVirtual()`.
- Tránh nhầm virtual threads với thread pool, `CompletableFuture` common pool, transaction propagation, khả năng mở vô hạn kết nối DB hoặc lời hứa tăng tốc mọi thuật toán.
- Chẩn đoán lỗi version, toolchain và runtime thực tế thay vì chỉ đọc `pom.xml`.

**Phạm vi bài:** các điều kiện để **dùng virtual threads trong Spring concurrency** và cách xác thực môi trường. Đây không phải bài tổng quát về mọi cơ chế lập lịch hoặc tuning toàn bộ hạ tầng Java.

## 2. Kết luận nhanh về các mốc phiên bản

| Thành phần | Mốc liên quan | Điều cần nhớ |
| --- | --- | --- |
| **JDK 17** | LTS, baseline của Spring Framework 6.x | Không có virtual threads chính thức trong API Java 17 |
| **JDK 19** | JEP 425, preview | Không phải đường production ổn định cho virtual threads |
| **JDK 20** | JEP 436, preview lần hai | Vẫn là preview; yêu cầu toolchain/flags tương ứng |
| **JDK 21** | JEP 444, virtual threads chính thức | **Mốc tối thiểu để dùng API virtual threads tiêu chuẩn** |
| **JDK 21+** | Các JDK mới hơn | Dùng được khi phiên bản Spring và dependencies được nhà cung cấp hỗ trợ |
| Spring Framework **6.0** | Baseline Java 17 | Chưa có bộ integration chuyên dụng ra mắt trong Framework 6.1 |
| Spring Framework **6.1** | Hỗ trợ tích hợp với virtual threads/JDK 21 | Giới thiệu `VirtualThreadTaskExecutor`, chế độ virtual của `SimpleAsyncTaskExecutor`, `SimpleAsyncTaskScheduler` |
| Spring Framework **6.2** | Tiếp tục hỗ trợ virtual threads | Có thêm một số tùy chọn lifecycle/executor; kiểm tra từng API theo Javadoc 6.2 |
| Spring Boot **3.1** | Trước mốc Boot virtual-thread auto-config | Không có property integration như Boot 3.2 |
| Spring Boot **3.2** | Bổ sung hỗ trợ virtual threads | Java 21+ và `spring.threads.virtual.enabled=true` |
| Spring Boot **3.3 / 3.4** | Tiếp tục cơ chế của dòng 3.x | Auto-config executor/scheduler có điều kiện, tùy bean và hạ tầng thực tế |
| Spring Boot các dòng mới hơn | Tài liệu tương ứng từng release | Không tự suy từ tài liệu dòng 3.x rằng mọi điều kiện đều không đổi |

**Phân biệt quan trọng:** Spring Framework 6.1 có thể chạy trên **Java 17** vì baseline của dòng 6.x là Java 17; nhưng chỉ khi JVM là **Java 21+** mới có thể bật các API/chế độ virtual thread yêu cầu Java 21. Nói “Spring 6.1 yêu cầu Java 21 cho mọi ứng dụng” là sai.

Ngược lại, nói “chạy Java 21 nên `@Async`/`@Scheduled` mặc định là virtual threads” cũng sai. **Java 21 cung cấp khả năng**; executor/scheduler thực tế và cấu hình mới quyết định sử dụng khả năng đó hay không.

### 2.1. Hai câu hỏi riêng về tương thích

- **Có thể chạy ứng dụng Spring trên JDK X không?** Phụ thuộc baseline, supported Java range, thư viện liên quan và runtime container.
- **Có thể bật virtual threads cho đường thực thi Y không?** Phụ thuộc JDK 21+, phiên bản Spring API/Boot property và bean/executor/scheduler thật sự phục vụ đường Y.

Ví dụ một Spring Framework 6.2 project có thể chạy hoàn toàn trên Java 17 với `ThreadPoolTaskExecutor`. Khi nâng runtime lên Java 21 và cấu hình `VirtualThreadTaskExecutor`, chỉ các task gửi vào executor mới được chuyển theo đường virtual-thread đó. Những executor custom khác vẫn dùng platform threads nếu chưa đổi.

## 3. JDK: 19/20 preview khác 21 chính thức ở đâu?

### 3.1. Java 19 và Java 20

Virtual threads bắt đầu xuất hiện dưới dạng **preview** qua Project Loom:

- **JDK 19 — JEP 425:** preview.
- **JDK 20 — JEP 436:** preview lần tiếp theo.
- Các preview features yêu cầu đúng compiler/runtime version cùng cơ chế `--enable-preview`; API/semantics có thể thay đổi giữa bản phát hành.

Không lấy ví dụ blog cũ có `--enable-preview` trên Java 19 và kết luận đó là lệnh cần cho Java 21. Môi trường dùng Java 19/20 không phải đường khuyến nghị để triển khai virtual threads chính thức trong Spring hiện đại.

### 3.2. Java 21 — JEP 444

Virtual threads trở thành tính năng chính thức của Java SE từ **JDK 21**. Các API hữu ích:

```java
Thread.ofVirtual()
Thread.startVirtualThread(Runnable task)
Thread.currentThread().isVirtual()
Executors.newVirtualThreadPerTaskExecutor()
```

Bốn dòng trên là **danh sách API**, không phải chương trình biên dịch độc lập; cần các import tương ứng cho `Executors`, và method `startVirtualThread` nhận `Runnable`.

Khi dùng **Java 21 chính thức**, **không cần `--enable-preview` chỉ để dùng virtual threads**. Nếu project đang dùng một preview feature *khác*, nó vẫn có thể cần flag cho feature đó; đừng lẫn với virtual threads.

### 3.3. Java 17 không có các API này

Trên JDK 17, code:

```java
Thread.ofVirtual().start(() -> System.out.println("hello"));
```

là **fragment minh họa lỗi compatibility**: Java 17 không có `Thread.ofVirtual()`. Nếu project compile với `--release 17`, lời gọi này không hợp lệ dù máy đang có một JDK mới hơn cung cấp compiler.

Giải pháp khi vẫn phải hỗ trợ Java 17 là dùng platform threads/executors tương thích và cô lập code Java 21 vào module/profile riêng theo yêu cầu deployment, **không cố “bật” virtual threads bằng một thuộc tính Spring trên JVM Java 17**.

### 3.4. Java mới hơn 21

Các JDK mới hơn có các cải tiến về scalability và tương tác blocking/pinning, nhưng phạm vi hỗ trợ chính thức của **từng** Spring Framework/Spring Boot/dependencies/container phải xem ở release notes tương ứng. Không dùng “Java 25 chạy được virtual threads” để suy tự động rằng mọi phiên bản Spring cũ đều đã được test và hỗ trợ đầy đủ trên Java 25.

## 4. Kiểm tra đúng JVM: build khác runtime

Một nguyên nhân lỗi rất phổ biến là:

- Laptop có `java -version` = 21.
- Maven sử dụng JDK 17 từ `JAVA_HOME` hoặc toolchain.
- Docker image chạy `eclipse-temurin:17-jre`.
- CI job sử dụng container có JDK 17.
- IDE compile bằng “Project SDK 21” nhưng chạy bằng runtime 17.
- Gradle daemon/toolchain dùng một JDK khác lệnh `java` trong terminal.

Các lệnh kiểm tra (shell):

```bash
java -version
javac -version
mvn -version
```

Đối với Gradle:

```bash
./gradlew --version
```

Các lệnh này là những lệnh **kiểm tra môi trường**, không có một stdout cố định áp dụng mọi máy. Cần đọc:

- Java runtime vendor/version/architecture;
- `javac` version;
- Maven Java version / Java home;
- Gradle daemon JVM và toolchain;
- Container runtime image;
- CI/CD pipeline JDK đang cài và sử dụng.

**Tối thiểu cho code virtual threads chính thức:** compiler phát code phù hợp Java 21, và **JVM cuối cùng thực thi ứng dụng cũng phải là 21+**.

### 4.1. --release 21 và class file version

Trong Maven:

```xml
<properties>
    <maven.compiler.release>21</maven.compiler.release>
</properties>
```

Với compiler JDK 21+, flag tương ứng:

```bash
javac --release 21 VirtualThreadProbe.java
```

`--release 21` chọn ngôn ngữ/API mục tiêu Java 21, không có phép màu biến runtime Java 17 thành Java 21. Java 21 class files có **major version 65**; Java 17 chỉ hỗ trợ các major version đến **61**. Chạy bytecode 65 trên Java 17 có thể gây `UnsupportedClassVersionError`.

### 4.2. Mối quan hệ source/target/release

| Trường hợp | Có đúng không? | Vì sao |
| --- | --- | --- |
| Compile JDK 21, `--release 21`, chạy JVM 21 | Có | Ngôn ngữ, API, bytecode, runtime phù hợp |
| Compile JDK 21, `--release 17`, gọi `Thread.ofVirtual()` | Không | `--release 17` chỉ cho API Java 17 |
| Compile JDK 17, đặt Maven release 21 | Không | Compiler JDK 17 không hỗ trợ target release 21 |
| Compile JDK 21 target 21, chạy JVM 17 | Không | Bytecode/API Java 21 không phù hợp JVM 17 |
| Compile Java 17, chạy trên JVM 21 | Thường có | Tương thích ngược ở Java, nhưng vẫn phải test dependencies |
| Chạy Spring Framework 6.2 trên Java 17 mà không bật virtual | Có thể | Dòng Spring 6.x hỗ trợ baseline Java 17 |
| Dùng Spring Boot 3.3 + Java 21, chưa bật property | Chưa đủ để tự động dùng virtual | Phải cấu hình đường thực thi cụ thể |

### 4.3. Docker và Kubernetes

Khi xây image, đảm bảo image build/runtime đều phù hợp:

```dockerfile
# Ví dụ multi-stage, cần điều chỉnh tên jar và Maven project.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/app.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Đây là **mẫu Dockerfile**: tên `app.jar` phụ thuộc cách Maven tạo artifact; cần sửa cho project thực tế. Mục đích là chứng minh **runtime image** cũng phải chứa Java 21+, không chỉ build stage.

Trong Kubernetes, kiểm tra image tag/digest thực được deploy và environment vars/config maps cho `spring.threads.virtual.enabled`; việc Helm values có Java 21 không phải bằng chứng container đang chạy Java 21.

## 5. Ví dụ Java 21 độc lập: nhận biết platform và virtual thread

Tệp `VirtualThreadProbe.java`:

```java
public class VirtualThreadProbe {
    public static void main(String[] args) throws Exception {
        System.out.println("main-virtual=" +
                Thread.currentThread().isVirtual());

        Thread worker = Thread.ofVirtual()
                .name("probe-vt")
                .start(() -> {
                    System.out.println("worker-virtual=" +
                            Thread.currentThread().isVirtual());
                    System.out.println("worker-name=" +
                            Thread.currentThread().getName());
                });

        worker.join();
    }
}
```

Biên dịch/chạy bằng **JDK 21+**:

```bash
javac --release 21 VirtualThreadProbe.java
java VirtualThreadProbe
```

Output từ chương trình chạy qua Java launcher chuẩn:

```text
main-virtual=false
worker-virtual=true
worker-name=probe-vt
```

Giải thích:

1. `main` thông thường chạy trên platform thread.
2. `Thread.ofVirtual()` tạo builder cho virtual thread.
3. `.name("probe-vt")` thiết lập tên thread.
4. `.start(...)` khởi chạy tác vụ trên virtual thread mới.
5. `Thread.currentThread().isVirtual()` trả `true` trong task.
6. `join()` chờ virtual thread kết thúc, đảm bảo main không thoát trước khi có output.

Không cần Spring, `@Async`, `@EnableScheduling` hay một property Boot để chạy ví dụ này. Đây là kiểm thử **khả năng của JVM**, chưa kiểm thử executor do Spring chọn.

### 5.1. Nếu javac báo cannot find symbol

Lỗi gần dạng:

```text
cannot find symbol: method ofVirtual()
```

có thể nghĩa là project đang compile against Java 17 (hoặc thấp hơn), dù runtime trong terminal khác. Kiểm tra `javac -version`, Maven compiler release/toolchain, IDE project SDK, Gradle toolchain. **Không** “khắc phục” bằng cách thêm import; `ofVirtual()` là API trên `java.lang.Thread` của Java 21.

## 6. Ví dụ Java 21 độc lập: một virtual thread cho mỗi task

Java 21 có `Executors.newVirtualThreadPerTaskExecutor()`: mỗi task được submit sẽ chạy trên một virtual thread mới, **không phải một pool virtual threads dùng lại**.

Tệp `VirtualThreadPerTaskDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class VirtualThreadPerTaskDemo {
    public static void main(String[] args) throws Exception {
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {

            List<Future<Boolean>> futures = new ArrayList<>();

            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(
                        () -> Thread.currentThread().isVirtual()));
            }

            boolean allVirtual = true;
            for (Future<Boolean> future : futures) {
                allVirtual &= future.get(5, TimeUnit.SECONDS);
            }

            System.out.println("task-count=" + futures.size());
            System.out.println("all-virtual=" + allVirtual);
        }
    }
}
```

Lệnh chạy:

```bash
javac --release 21 VirtualThreadPerTaskDemo.java
java VirtualThreadPerTaskDemo
```

Output:

```text
task-count=4
all-virtual=true
```

Đoạn code cố ý thu kết quả qua `Future.get` **theo thứ tự danh sách**, không print từ các worker để tránh output bị đảo thứ tự do scheduling. `try-with-resources` gọi `ExecutorService.close()`; trong Java 21, API này đợi các task được submit hoàn tất trước khi đóng, đồng thời `get(timeout)` trong ví dụ đặt giới hạn chờ cho các future.

**Điểm cần nhớ:** tạo một virtual thread cho mỗi task không có nghĩa tạo thêm bốn CPU cores. Virtual threads hữu ích khi nhiều task thường chờ I/O, không phải cách tăng tốc thuật toán CPU-bound thuần túy.

## 7. Spring Framework: sự khác biệt giữa 6.0, 6.1 và 6.2

### 7.1. Spring Framework 6.0: baseline Java 17

Spring Framework 6.0 được xây dựng với baseline **Java 17**. Ở phiên bản này, bạn vẫn có thể:

- Dùng `ThreadPoolTaskExecutor` hoặc `TaskExecutor` để chạy công việc nền.
- Dùng `@EnableAsync`/`@Async` qua Spring AOP proxy.
- Dùng `@EnableScheduling`/`@Scheduled` với `ThreadPoolTaskScheduler`.
- Viết Java code tương thích JDK đang chạy và dùng executor thủ công, nhưng **không có các lựa chọn virtual-thread integration chuyên dụng được giới thiệu ở Framework 6.1**.

Không nên khẳng định mọi ứng dụng Spring 6.0 có JDK 21 sẽ lỗi, nhưng muốn sử dụng virtual threads có **hỗ trợ tích hợp chuẩn, rõ ràng**, nên chọn phiên bản Spring Framework đã công bố những API đó, đi kèm các thư viện được hỗ trợ và kiểm thử.

### 7.2. Spring Framework 6.1: mốc tích hợp virtual threads

Từ Spring Framework **6.1**, dự án công bố:

- `VirtualThreadTaskExecutor`: executor nhẹ, tạo virtual thread cho mỗi task.
- `SimpleAsyncTaskExecutor#setVirtualThreads(true)`: chọn virtual threads thay platform threads.
- `SimpleAsyncTaskScheduler`: scheduler thread-per-task strategy có chế độ virtual threads, đặc biệt thích hợp với Java 21.
- Cải tiến lifecycle và integration liên quan JDK 21.

**Yêu cầu kép:** project có Spring Framework 6.1+ **và** chạy trên JDK 21+ mới có thể sử dụng virtual-thread mode của các API trên. `setVirtualThreads(true)` yêu cầu Java 21+, không tự chuyển Java 17 thành Java 21.

### 7.3. Spring Framework 6.2 và các dòng tiếp theo

Spring Framework 6.2 tiếp tục cung cấp các API virtual-thread integration. Một số options có thêm trên `ExecutorConfigurationSupport` trong 6.2, ví dụ khả năng chọn virtual-thread factory ở lifecycle-aware executors; **không sao chép cấu hình 6.2 vào 6.1 rồi kỳ vọng API đã có**.

Nếu dùng Spring 7 hoặc dòng mới hơn, đọc Javadoc và system requirements của đúng version đang build. Không suy “mới hơn luôn tương đương” trong những API đã đổi signature/semantics.

### 7.4. Bảng API và điều kiện tối thiểu

| API/tính năng | JDK cần để dùng virtual | Spring Framework |
| --- | --- | --- |
| `Thread.ofVirtual()` | 21+ | Không cần Spring |
| `Executors.newVirtualThreadPerTaskExecutor()` | 21+ | Không cần Spring |
| `Thread.isVirtual()` | 21+ | Không cần Spring |
| `VirtualThreadTaskExecutor` | 21+ | 6.1+ |
| `SimpleAsyncTaskExecutor#setVirtualThreads(true)` | 21+ | 6.1+ |
| `SimpleAsyncTaskScheduler#setVirtualThreads(true)` | 21+ | 6.1+ |
| `@Async` dùng một executor virtual cụ thể | 21+ nếu virtual | Annotation đã tồn tại trước 6.1; executor virtual tích hợp là mốc 6.1+ |
| `@Scheduled` với virtual scheduler | 21+ nếu virtual | Scheduler virtual chuyên dụng là mốc 6.1+ |
| `ExecutorConfigurationSupport#setVirtualThreads(...)` | 21+ | Một số support thêm ở 6.2+; tra Javadoc đúng class/version |

**Tương thích API khác với cách gọi thực tế:** một method có `@Async` không mặc nhiên chạy trên `VirtualThreadTaskExecutor` chỉ vì class này có trên classpath. Nó phải được chọn qua bean/qualifier hoặc cơ chế executor selection tương ứng.

## 8. Spring Framework Java 21+: project chạy thật với @Async và TaskScheduler

Ví dụ Spring thuần dùng **JDK 21+**, Maven, **Spring Framework 6.2.14**. Mục tiêu:

- Dùng `VirtualThreadTaskExecutor` cho `@Async`.
- Dùng `SimpleAsyncTaskScheduler` với `setVirtualThreads(true)` để chạy một task theo lịch.
- Kiểm tra `Thread.isVirtual()` **ngay bên trong task**, không chỉ quan sát tên class.
- Dùng `CountDownLatch` và `Future.get(timeout)` tránh race/treo test.

### 8.1. Cấu trúc project

```text
framework-virtual-demo/
├── pom.xml
└── src/main/java/demo/FrameworkVirtualDemo.java
```

### 8.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>framework-virtual-demo</artifactId>
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

### 8.3. src/main/java/demo/FrameworkVirtualDemo.java

```java
package demo;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.core.task.VirtualThreadTaskExecutor;

public class FrameworkVirtualDemo {

    public static class AsyncWorker {
        @Async("virtualExecutor")
        public CompletableFuture<Boolean> check() {
            return CompletableFuture.completedFuture(
                    Thread.currentThread().isVirtual());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class Config {
        @Bean("virtualExecutor")
        public VirtualThreadTaskExecutor virtualExecutor() {
            return new VirtualThreadTaskExecutor("demo-virtual-");
        }

        @Bean
        public AsyncWorker worker() {
            return new AsyncWorker();
        }

        @Bean("taskScheduler")
        public SimpleAsyncTaskScheduler taskScheduler() {
            SimpleAsyncTaskScheduler scheduler =
                    new SimpleAsyncTaskScheduler();
            scheduler.setVirtualThreads(true);
            scheduler.setThreadNamePrefix("demo-scheduled-");
            return scheduler;
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            AsyncWorker worker = context.getBean(AsyncWorker.class);
            Boolean asyncVirtual =
                    worker.check().get(5, TimeUnit.SECONDS);

            SimpleAsyncTaskScheduler scheduler =
                    context.getBean(SimpleAsyncTaskScheduler.class);
            CountDownLatch finished = new CountDownLatch(1);
            AtomicBoolean scheduleVirtual = new AtomicBoolean();

            scheduler.schedule(() -> {
                try {
                    scheduleVirtual.set(
                            Thread.currentThread().isVirtual());
                } finally {
                    finished.countDown();
                }
            }, Instant.now());

            if (!finished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "scheduled callback not executed");
            }

            System.out.println("async-virtual=" + asyncVirtual);
            System.out.println("scheduled-virtual=" +
                    scheduleVirtual.get());
        }
    }
}
```

### 8.4. Lệnh chạy và output

Trong thư mục có `pom.xml`:

```bash
mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=demo.FrameworkVirtualDemo
```

Hai dòng do chương trình in trong điều kiện chạy bình thường trên JDK 21+:

```text
async-virtual=true
scheduled-virtual=true
```

Maven/Spring có thể ghi log riêng. Ví dụ có **hai bằng chứng runtime**:

1. `AsyncWorker.check()` được gọi qua **Spring-managed proxy**, vì Spring tạo bean `AsyncWorker` và `@EnableAsync` kích hoạt async advice. Qualifier `virtualExecutor` chọn `VirtualThreadTaskExecutor`.
2. `SimpleAsyncTaskScheduler` được Spring tạo như bean, bật virtual threads và gọi `schedule(Runnable, Instant)` trực tiếp; callback one-shot chạy trên virtual thread. `CountDownLatch` xác minh callback đã chạy xong trước khi in kết quả.

**Chú ý:** dùng `schedule(...)` programmatically nên ở demo này **không cần `@EnableScheduling`**. Nếu muốn Spring tự phát hiện methods có `@Scheduled`, phải thêm `@EnableScheduling` trong context thích hợp.

### 8.5. Không áp dụng kết quả one-shot cho mọi kiểu schedule

Với `SimpleAsyncTaskScheduler`, Javadoc lưu ý `fixedDelay` là **ngoại lệ**: callback fixed-delay chạy trên **scheduler thread**, không áp dụng cách bàn giao per-task thông thường như fixed-rate/cron/one-shot. Vì vậy `scheduled-virtual=true` trong ví dụ một lần **không chứng minh** fixed-delay cũng chạy trên một virtual worker độc lập.

### 8.6. Khi phải ở Java 17

Nếu một project bắt buộc Java 17, có thể thay:

- `VirtualThreadTaskExecutor` bằng `ThreadPoolTaskExecutor` hoặc `SimpleAsyncTaskExecutor` dùng platform threads.
- `SimpleAsyncTaskScheduler` virtual mode bằng `ThreadPoolTaskScheduler`, hoặc dùng scheduler phù hợp không bật virtual threads.
- `Thread.isVirtual()` khỏi code Java 17, vì method này chỉ có từ Java 21.

Đây là một **phương án tương thích khác**, không phải cứ thay property `virtual.enabled=false` là Java 17 biên dịch được source chứa `Thread.ofVirtual()`.

## 9. Spring Boot: property chính thức xuất hiện từ 3.2

Spring Boot **3.2** bổ sung hỗ trợ virtual threads thông qua cấu hình:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Điều kiện quan trọng:

- **JDK 21+** đang chạy runtime.
- **Spring Boot 3.2+** hoặc phiên bản tương ứng có documented support cho property.
- Đường thực thi phải dùng một **executor/scheduler/server component được Boot auto-configure có virtual-thread support**.
- Không có custom bean/qualifier thay thế đường thực thi đó theo cách bỏ qua auto-config.

**Không đủ:** đặt `spring.threads.virtual.enabled=true` trên Boot 3.1/Java 17 rồi mong virtual threads tự xuất hiện.

### 9.1. Ví dụ application.yml cho ứng dụng Boot 3.3.x / Java 21+

```yaml
spring:
  threads:
    virtual:
      enabled: true
  main:
    keep-alive: true
  task:
    execution:
      thread-name-prefix: app-vt-
    scheduling:
      thread-name-prefix: scheduled-vt-
```

`spring.main.keep-alive=true` quan trọng trong những ứng dụng background jobs/scheduled-only, vì virtual threads là **daemon threads**. Nếu JVM chỉ còn daemon threads, process có thể thoát mặc dù công việc nền đang chờ; Boot cung cấp keep-alive để giúp ứng dụng tiếp tục sống khi cần. Một web server hoặc thành phần khác có non-daemon threads có thể thay đổi tình huống; hãy kiểm tra điều kiện vận hành thực tế.

**Không** áp dụng mù quáng keep-alive như cách hứa job sẽ tồn tại qua restart. Khi process chết, các virtual threads vẫn chết; job quan trọng cần durable queue/job store.

### 9.2. Boot auto-configure @Async executor

Theo tài liệu dòng Boot 3.2/3.3:

- Khi điều kiện auto-config task execution thỏa và virtual threads bật, Boot tạo `SimpleAsyncTaskExecutor` dùng virtual threads thay vì `ThreadPoolTaskExecutor` mặc định trên platform threads.
- `@EnableAsync`/`@Async` dùng executor được chọn theo quy tắc Spring và bean hiện có.
- Spring MVC async processing, WebFlux blocking execution, GraphQL tích hợp có các điều kiện/bean name riêng, không phải mọi đoạn code trong mọi framework tự đổi thread.
- Nếu bạn khai báo **custom `Executor` bean** hoặc `@Async("customPool")`, Boot có thể **back off** hoặc method dùng pool được chỉ định, cho dù property virtual threads đang bật.

Ví dụ method:

```java
@Service
public class DemoService {
    @Async
    public CompletableFuture<Boolean> inspect() {
        boolean virtual = Thread.currentThread().isVirtual();
        return CompletableFuture.completedFuture(virtual);
    }
}
```

Đây là **Spring Boot integration fragment**, chưa có controller/app class và phải gọi qua Spring proxy trong context có `@EnableAsync`. Khi runtime Java 21+, Boot auto-config đúng và không có executor khác chiếm quyền, future có thể trả `true`. Không hứa output cụ thể nếu chưa kiểm tra bean runtime.

### 9.3. Boot auto-configure scheduler

Khi có scheduling infrastructure cần một scheduler và auto-configuration được áp dụng:

- **Virtual threads bật:** Boot dùng `SimpleAsyncTaskScheduler` có virtual-thread mode.
- **Virtual threads tắt:** Boot thường dùng `ThreadPoolTaskScheduler`.
- `spring.task.scheduling.pool.size` là property của mô hình **pool-based scheduler**; trên `SimpleAsyncTaskScheduler` virtual thread, các pooling-related properties được bỏ qua.
- `@EnableScheduling` vẫn cần để Spring phát hiện/register `@Scheduled` trong cấu hình annotation-driven phù hợp; property virtual threads không tự thay thế nó.

**Ngoại lệ cần nhớ:** `SimpleAsyncTaskScheduler` dùng một scheduler thread và **fixed-delay callbacks chạy trên scheduler thread** theo Javadoc. Không kết luận rằng mọi `@Scheduled(fixedDelay)` task được offload sang virtual worker riêng chỉ vì bật property Boot.

### 9.4. Embedded web server cũng có điều kiện

Trong Boot 3.2 support, Tomcat/Jetty có khả năng dùng virtual threads cho request processing khi Boot cấu hình tương ứng. Nhưng:

- Tên/version connector/container phải được kiểm chứng.
- Nếu bạn tự cấu hình connector hoặc executor, property không tự ghi đè mọi cấu hình tùy chỉnh.
- Spring WebFlux event loop không mặc nhiên chuyển thành mô hình một virtual thread cho mỗi reactive callback.
- Blocking work trong WebFlux phải được offload đúng ngữ cảnh; không chặn event-loop thread và tuyên bố “đã bật virtual rồi nên an toàn”.
- Các thư viện DB/HTTP/driver có thể có giới hạn riêng; virtual threads không làm một pool connection 20 thành 20.000 connections.

## 10. Cấu hình Framework bằng @Bean khác với cấu hình Boot bằng property

### 10.1. Spring Framework thuần

Bạn chủ động tạo bean:

```java
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
public class VirtualConfig {

    @Bean("virtualExecutor")
    public VirtualThreadTaskExecutor virtualExecutor() {
        return new VirtualThreadTaskExecutor("async-vt-");
    }

    @Bean("taskScheduler")
    public SimpleAsyncTaskScheduler taskScheduler() {
        SimpleAsyncTaskScheduler scheduler =
                new SimpleAsyncTaskScheduler();
        scheduler.setVirtualThreads(true);
        return scheduler;
    }
}
```

Đây là **fragment Spring Framework 6.1+ trên Java 21+**, cần imports. `@EnableAsync` và `@EnableScheduling` có mục đích khác nhau; bean `virtualExecutor` chỉ được dùng khi được chọn đúng bởi `@Async`/`AsyncConfigurer`/resolver tương ứng.

### 10.2. Spring Boot

Bạn có thể dùng property để Boot quyết định các auto-configured beans **khi điều kiện cho phép**. Nhưng nếu project khai báo một `ThreadPoolTaskExecutor` custom làm default executor, việc bật `spring.threads.virtual.enabled=true` không tự biến bean tùy chỉnh đó thành virtual-thread executor.

Ví dụ:

```java
@Bean("myLegacyExecutor")
public ThreadPoolTaskExecutor myLegacyExecutor() {
    ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
    pool.setCorePoolSize(8);
    return pool;
}

@Async("myLegacyExecutor")
public CompletableFuture<Void> legacyJob() {
    // Vẫn dùng executor mà qualifier chỉ định.
    return CompletableFuture.completedFuture(null);
}
```

Đây là **fragment**, không phải lớp runnable; cần đầy đủ class/imports/config. Nếu `@Async("myLegacyExecutor")` được gọi đúng qua proxy, nó tiếp tục sử dụng custom pool đó, không tự đổi sang virtual threads chỉ bởi property của Boot.

### 10.3. Tên bean và việc chọn executor

Trong một ứng dụng có nhiều executors:

- `@Async("virtualExecutor")` chọn bean được chỉ định qua qualifier.
- `@Async("legacyPool")` chọn bean khác.
- `@Async` không qualifier dùng executor theo resolver/configuration/default của Spring.
- Boot auto-config tạo `applicationTaskExecutor` theo điều kiện; một bean `Executor` custom có thể thay đổi outcome.
- Đừng suy tất cả `CompletableFuture.supplyAsync(...)` sẽ dùng Spring executor; overload không truyền executor thường dùng default Java async facility, không mặc nhiên là virtual threads.

Cách kiểm chứng đúng là **in runtime bean type và `Thread.currentThread().isVirtual()` ngay bên trong callback**.

## 11. Cần kiểm tra ba nơi khi bật virtual threads

### 11.1. JDK thực tế

```bash
java -version
mvn -version
```

Yêu cầu runtime Java 21+ và compiler/build phù hợp.

### 11.2. Spring bean thực tế

Kiểm tra:

- Bean nào implement `AsyncTaskExecutor`/`Executor`?
- Có `applicationTaskExecutor` hoặc bean tự đặt tên được framework sử dụng?
- Bean nào implement `TaskScheduler`?
- Có `SimpleAsyncTaskExecutor`, `VirtualThreadTaskExecutor`, `ThreadPoolTaskExecutor` hoặc `SimpleAsyncTaskScheduler`?
- Có user-defined bean khiến Boot auto-config back off?
- `@EnableAsync` và `@EnableScheduling` có được kích hoạt trong đúng context?

Có thể thêm một `ApplicationRunner` hoặc health diagnostics code **chỉ trong môi trường kiểm thử** để log class của bean cần dùng, nhưng cần tránh kết luận chỉ từ tên class nếu nó có factory/adapter.

### 11.3. Thread đang thực thi công việc

Đo ngay tại callback:

```java
boolean virtual = Thread.currentThread().isVirtual();
String threadName = Thread.currentThread().getName();
```

Đây là **fragment Java 21+**. Dùng trong method `@Async`/`@Scheduled` thật để trả answer quan sát được. Một thread name có chữ `virtual` không phải bằng chứng chắc chắn; `isVirtual()` mới kiểm tra loại thread.

### 11.4. Checklist kiểm chứng minimal

| Lớp | Câu hỏi |
| --- | --- |
| Runtime | JVM chạy thật là 21+? |
| Compiler | Java toolchain và `--release`/Maven release >=21? |
| Spring | Framework đã có virtual-thread integration API? |
| Boot | Version có hỗ trợ property? Property được nạp từ đúng profile/env? |
| Executor | `@Async` gọi qua proxy và dùng executor nào? |
| Scheduler | `@Scheduled` dùng scheduler nào? Fixed-delay có ngoại lệ? |
| Web | Container/request handler thực tế chạy thread loại nào? |
| I/O | DB/HTTP pool, driver và blocking strategy có phù hợp? |
| Lifecycle | Daemon virtual threads có khiến process thoát sớm? |
| Operations | Có metrics, traces, deadline và giới hạn downstream? |

## 12. Hiểu virtual threads đúng: không phải một thread pool mới

Một **platform thread** thường tương ứng với một OS thread trong suốt vòng đời. Một virtual thread là Java thread được JVM scheduling lên carrier threads. Khi gặp blocking I/O được hỗ trợ, virtual thread có thể unmount, trả carrier cho nhiệm vụ khác.

```text
Virtual thread A ---- running ---- wait I/O ----- resume ---- done
                         |           |               |
Carrier platform thread X ----------free------------>
                               phục vụ virtual B
Virtual thread B -------- running / waiting / resume -----
```

Mục tiêu chính của virtual threads là **scale number of concurrent tasks**, không phải làm phép tính CPU nhanh hơn.

### 12.1. “Thread per task” không phải “pool virtual threads”

`Executors.newVirtualThreadPerTaskExecutor()` tạo một virtual thread mới **cho từng task**. `VirtualThreadTaskExecutor` của Spring cũng theo ý tưởng không tái sử dụng một virtual thread cố định cho nhiều task như pooled platform workers.

**Không nên xây một “virtual-thread pool size 20” chỉ vì quen cấu hình thread pool cũ.** Nếu mục tiêu là giới hạn kết nối API/DB đến 20, dùng semaphore, connection pool, rate limiter hoặc concurrency guard cho **tài nguyên khan hiếm**, không coi virtual thread là tài nguyên khan hiếm chính.

### 12.2. Virtual threads thường tốt cho blocking I/O

Phù hợp để thử nghiệm:

- HTTP clients gọi downstream và chờ phản hồi.
- JDBC operations chờ database, với driver và connection pool phù hợp.
- File I/O hoặc các blocking operations khác mà JDK có integration tốt.
- Các request kiểu thread-per-request hoặc task-per-request với lượng concurrency cao.

Không mong cải thiện lớn cho:

- Thuật toán CPU-bound đã saturate các cores.
- Reactive pipelines vốn không block và dùng ít threads.
- Ứng dụng có request rate thấp, không bị giới hạn bởi worker threads.
- Những thư viện/native calls đang giữ carrier lâu hoặc có bottleneck downstream.

**Đo throughput, latency, memory, connection saturation và error rates** trước/sau thay đổi.

### 12.3. Pinning phụ thuộc phiên bản JDK

Trong **JDK 21**, virtual thread có thể bị *pinned* khi blocking trong một vùng `synchronized` hoặc một số native/foreign calls; carrier không được giải phóng trong thời gian pin. Pinning không làm logic sai nhưng có thể làm throughput kém.

Các JDK mới hơn có cải tiến để giảm pinning trong nhiều trường hợp; **đừng áp dụng máy móc lời khuyên JDK 21 cho mọi JDK mới**. Với JDK 21, hãy phát hiện bằng JFR/số liệu thực tế, tránh giữ monitor `synchronized` qua blocking I/O dài; cân nhắc lock và thiết kế state phù hợp.

### 12.4. ThreadLocal và transaction

Virtual threads vẫn hỗ trợ `ThreadLocal` và nhiều mô hình imperative của Spring, nhưng không có nghĩa transaction/security/request context tự chuyển qua **một virtual thread mới** khi bạn submit công việc:

- Context được gắn với **thread thực thi**, không tự chia sẻ giữa mọi thread.
- `@Async` vẫn thay đổi execution boundary và có thể làm mất caller context nếu không propagate đúng.
- Không share một Hibernate `Session`/`EntityManager` giữa worker threads.
- Không cache các object nặng bằng `ThreadLocal` với giả định một số ít threads sẽ tái sử dụng suốt đời; virtual thread thường sống theo task.

Virtual threads là **chiến lược thực thi**, không phải cơ chế propagation tự động.

## 13. @Scheduled và SimpleAsyncTaskScheduler: ngoại lệ cần biết

Spring Framework 6.1+ có `SimpleAsyncTaskScheduler`, phù hợp với virtual threads cho những tác vụ theo lịch nhất định.

Điểm khác biệt:

- Một scheduler thread giữ thời điểm kích hoạt.
- Task one-shot, fixed-rate và cron phù hợp được handoff cho task thread hoặc target executor.
- **Fixed-delay callbacks thực thi ngay trên scheduler thread** theo đặc điểm class, không theo worker-per-task như những kiểu khác.
- Nếu một fixed-delay callback chậm/blocking, nó có thể giữ scheduler thread, ảnh hưởng tới các trigger khác.
- Nếu fixed-delay callback chỉ gọi một `@Async` worker rồi return, scheduler có thể tính delay từ thời điểm **handoff**, không phải completion nghiệp vụ.

Bật virtual threads vì thế **không xóa yêu cầu hiểu fixedDelay/fixedRate/cron, overlap và business completion**.

### 13.1. Spring Boot virtual scheduling

Boot có thể chọn `SimpleAsyncTaskScheduler` khi bật virtual threads và điều kiện auto-configuration được thỏa. Tuy nhiên, nếu user tự khai báo `ThreadPoolTaskScheduler` hoặc `TaskScheduler` custom, app có thể đang dùng loại scheduler khác.

`spring.task.scheduling.pool.size` không chỉnh số virtual threads trên `SimpleAsyncTaskScheduler`. Mọi giới hạn simultaneous jobs cần được định nghĩa theo downstream và execution policy.

## 14. Không có giới hạn I/O tự động: ví dụ với Semaphore

Một ứng dụng có thể xử lý nhiều task trên virtual threads, nhưng một database/API chỉ cho phép số kết nối hoặc request đồng thời hữu hạn. Dùng concurrency guard thể hiện chính xác **tài nguyên cần bảo vệ**.

Tệp `VirtualThreadBoundedDemo.java`, Java 21+:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualThreadBoundedDemo {
    public static void main(String[] args) throws Exception {
        Semaphore permits = new Semaphore(2);
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> tasks = new ArrayList<>();

            for (int i = 0; i < 6; i++) {
                tasks.add(executor.submit(() -> {
                    try {
                        permits.acquire();
                        int now = inside.incrementAndGet();
                        maxObserved.accumulateAndGet(now, Math::max);

                        try {
                            Thread.sleep(20);
                        } finally {
                            inside.decrementAndGet();
                            permits.release();
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                }));
            }

            for (Future<?> task : tasks) {
                task.get(5, TimeUnit.SECONDS);
            }
        }

        System.out.println("max-within-limit="
                + (maxObserved.get() <= 2));
        System.out.println("active-after=" + inside.get());
    }
}
```

Biên dịch/chạy:

```bash
javac --release 21 VirtualThreadBoundedDemo.java
java VirtualThreadBoundedDemo
```

Kết quả trong điều kiện các task hoàn tất bình thường:

```text
max-within-limit=true
active-after=0
```

Ví dụ tạo 6 virtual threads, nhưng chỉ **2 task tại một thời điểm** được bước vào vùng mô phỏng I/O. Nó không khẳng định lúc nào cũng có đúng 2 task active; tùy scheduler có thể ít hơn. `Semaphore` chỉ quản lý quota cục bộ, không tự tăng database connections hoặc giới hạn toàn cụm.

**Trong ứng dụng JDBC thật**, connection pool đã có cơ chế đợi slot. Không nhất thiết thêm một semaphore trùng lặp với pool đó; quyết định dựa trên downstream quota, admission policy và metrics.

## 15. Các lỗi version và configuration thường gặp

### 15.1. Lỗi compile khi dùng Java 17

**Triệu chứng:** `cannot find symbol ofVirtual()`, `isVirtual()` hoặc `newVirtualThreadPerTaskExecutor()`.

**Nguyên nhân:** compiler/API target thấp hơn 21. Kiểm tra Maven `<release>`, toolchain, Gradle `java.toolchain.languageVersion` và IDE.

### 15.2. Lỗi “release version 21 not supported”

**Triệu chứng:** Maven compiler plugin không thể build với target 21.

**Nguyên nhân:** compiler JDK thực tế là 17 hoặc cũ hơn. Nâng JDK của build/toolchain, không chỉ sửa property.

### 15.3. UnsupportedClassVersionError khi deploy

**Triệu chứng:** ứng dụng compile thành công nhưng JVM runtime báo class file version không được hỗ trợ.

**Nguyên nhân:** build bằng Java 21, runtime vẫn Java 17 (ví dụ Docker runtime stage). Kiểm tra image và `java -version` **bên trong container đang chạy**.

### 15.4. Spring Framework không tìm thấy VirtualThreadTaskExecutor

**Triệu chứng:** `cannot find symbol VirtualThreadTaskExecutor` hoặc `ClassNotFoundException`.

**Nguyên nhân:** Spring Framework dependency quá cũ, bị conflict/transitive version hoặc import sai. Kiểm tra `mvn dependency:tree` / Gradle runtimeClasspath và BOM.

### 15.5. Boot property không có hiệu lực

**Triệu chứng:** `spring.threads.virtual.enabled=true` nhưng method `@Async` vẫn báo `isVirtual=false`.

Kiểm tra lần lượt:

1. Spring Boot có phải **3.2+**?
2. Runtime có phải JDK 21+?
3. Property nằm trong profile/env thật sự được nạp?
4. `@EnableAsync` đã bật và method được gọi **qua Spring proxy**?
5. Có custom `Executor`/`AsyncConfigurer`/qualifier thay đổi executor selection?
6. Code có đang chạy trong `CompletableFuture.supplyAsync(...)` không truyền Spring executor?
7. Có phần nào của hệ thống vẫn dùng platform thread theo thiết kế?

### 15.6. @Async bị bỏ qua do self-invocation

```java
@Service
public class BadService {
    public void outer() {
        inner(); // this.inner(), không đi qua Spring async proxy.
    }

    @Async("virtualExecutor")
    public void inner() {
        // Có thể vẫn chạy đồng bộ ngay trong caller thread.
    }
}
```

Đây là **fragment phản ví dụ**. Khắc phục bằng cách tách `inner()` sang Spring bean khác được inject, hoặc sử dụng execution mechanism khác với hợp đồng rõ ràng.

### 15.7. @Scheduled vẫn chạy trên platform thread

Lý do có thể là:

- Spring scheduler thực tế vẫn là `ThreadPoolTaskScheduler`.
- Bạn dùng custom scheduler mà Boot không thay thế.
- Spring `@EnableScheduling` chưa bật trong context.
- Task là `fixedDelay` với đặc điểm scheduler thread riêng.
- Runtime/configuration chọn đường khác với dự đoán.

Không sửa bằng cách đổi tên thread thành `virtual-*`; hãy kiểm tra runtime bean type và `Thread.isVirtual()` trong callback.

### 15.8. Ứng dụng thoát dù còn scheduled work

Virtual threads là daemon. Một ứng dụng background-only có thể thoát khi không còn non-daemon threads. Trên Boot hỗ trợ, cân nhắc `spring.main.keep-alive=true` và lifecycle policy. Nhưng keep-alive không phải job durability.

### 15.9. Throughput giảm khi chuyển sang virtual threads

Một số nguyên nhân:

- CPU-bound workload đã saturate cores.
- Pinning trên JDK phiên bản đang chạy.
- JDBC connection pool quá nhỏ hoặc API downstream throttling.
- Task bị unlimited fanout, memory/GC pressure.
- Lock contention, `ThreadLocal` caches đắt, shared mutable state.
- Code vẫn dùng một executor platform thread nhỏ ở đường thực thi thật.
- Benchmark tải chưa đại diện production.

Đừng kết luận “virtual threads chậm” hoặc “virtual threads luôn nhanh” chỉ từ một lần benchmark. Dùng JFR, CPU, allocation, queue/connection metrics và traces theo task.

## 16. Kiểm thử tương thích trong CI/CD

Một bộ test tối thiểu nên bao gồm:

### 16.1. Java smoke test

- Build JDK 21+ và chạy `VirtualThreadProbe`.
- Assert `Thread.currentThread().isVirtual()` là `true` trong virtual worker.
- Nếu project có Java 17 compatibility module, build module đó với `--release 17` và **không** tham chiếu API virtual từ Java 21.
- Xác minh CI JDK/toolchain và runtime image bằng lệnh version.

### 16.2. Spring bean/executor integration test

- Dùng Spring context thật để kiểm tra `@Async("virtualExecutor")` gọi qua proxy.
- Từ bên trong async method trả `Thread.isVirtual()` về caller.
- Đừng dựa vào tên method, tên bean hoặc thread-name prefix làm bằng chứng duy nhất.
- Kiểm tra trường hợp cố ý dùng custom platform `ThreadPoolTaskExecutor` để chứng minh qualifier thực sự quyết định đường chạy.
- Với Boot, test với và không với property virtual-thread, đồng thời không có custom bean, rồi thêm custom bean để quan sát thay đổi auto-configuration.

### 16.3. Scheduling integration test

- Programmatically schedule một task và dùng `CountDownLatch.await(timeout)` đợi callback.
- Kiểm tra scheduler runtime type.
- Tránh suy từ kết quả one-shot ra fixed-delay semantics.
- Đặt lệnh `@Scheduled` trong context có `@EnableScheduling`.
- Kiểm tra error handling, shutdown và stop policy.

### 16.4. Tải và vận hành

- Đo concurrent requests, throughput, p95/p99 latency và CPU.
- Đo DB connection pool wait, remote API latency và rejection.
- Với JDK 21, phát hiện pinning bằng JFR event `jdk.VirtualThreadPinned`.
- Kiểm tra ThreadLocal/MDC/security/transaction propagation qua async boundary.
- Kiểm tra job khi shutdown/restart, daemon behavior và keep-alive.
- Chạy load test có cùng dependency versions với production, không chỉ Java stdlib demo.

## 17. Quan sát virtual threads bằng JFR và thread dumps

Java 21 hỗ trợ các công cụ quan sát virtual threads:

- Java Flight Recorder (JFR) với những event như `jdk.VirtualThreadStart`, `jdk.VirtualThreadEnd` và `jdk.VirtualThreadPinned` theo cấu hình/trang thái JDK.
- `jcmd` có thể tạo thread dump bao gồm virtual threads bằng `Thread.dump_to_file`.
- `Thread.currentThread().isVirtual()` để kiểm chứng tại code path thật.

Ví dụ lệnh chụp thread dump (thay PID và đường dẫn thực tế):

```bash
jcmd 12345 Thread.dump_to_file -format=text threads.txt
```

Hoặc JSON:

```bash
jcmd 12345 Thread.dump_to_file -format=json threads.json
```

Các lệnh này **không có một stdout xác định**, cần chạy với quyền phù hợp và JDK tooling có sẵn. Với JFR:

```bash
jfr print --events jdk.VirtualThreadPinned recording.jfr
```

Phải có file JFR đã được tạo với sự kiện tương ứng được enable. Event names và hiệu quả chẩn đoán có thể thay đổi theo JDK release, đặc biệt khi pinning được cải tiến ở JDK mới.

**Không** log mọi thread ID như một metrics label: virtual threads có thể rất nhiều và tạo cardinality cao. Metrics nên tập trung vào request/job type, outcome, queue wait, backend saturation và aggregate execution timings.

## 18. Ma trận quyết định nâng cấp

### 18.1. Đang dùng Java 17 + Spring Framework 6.0

- Nếu chưa nâng JDK: tiếp tục dùng `ThreadPoolTaskExecutor`/`ThreadPoolTaskScheduler` và tối ưu overload bằng backpressure.
- Nếu muốn virtual threads: lên kế hoạch nâng runtime/compiler lên Java 21+ và framework lên dòng cung cấp API tích hợp, ví dụ Spring Framework 6.1/6.2.
- Kiểm tra thư viện bên thứ ba, servlet container, JDBC drivers và test suite trước khi đổi production runtime.

### 18.2. Đang dùng Java 17 + Spring Framework 6.1/6.2

- Framework version **đủ mới để có virtual-thread API**, nhưng Java 17 **chưa đủ** để kích hoạt.
- Không gọi `setVirtualThreads(true)` khi runtime còn Java 17.
- Sau khi nâng Java 21, chọn rõ executor/scheduler cần chuyển; phần khác có thể tiếp tục dùng platform threads.

### 18.3. Đang dùng Java 21 + Spring Framework 6.1/6.2, không dùng Boot

- Dùng `VirtualThreadTaskExecutor` hoặc `SimpleAsyncTaskExecutor#setVirtualThreads(true)` cho jobs `@Async`.
- Dùng `SimpleAsyncTaskScheduler#setVirtualThreads(true)` nếu phù hợp scheduling semantics.
- Chọn bean qua qualifier, cấu hình lifecycle, error handling, timeout và giới hạn downstream.
- Test `isVirtual()` ngay trong worker/scheduled callback.

### 18.4. Đang dùng Java 21 + Spring Boot 3.2/3.3/3.4

- Bật `spring.threads.virtual.enabled=true` nếu muốn dùng Boot integration.
- Xác minh auto-configured executor/scheduler có được tạo, không bị custom beans thay thế.
- Không nghĩ pooling properties sẽ chỉnh số lượng virtual threads trên non-pool implementation.
- Xem xét `spring.main.keep-alive=true` cho background-only app.
- Dùng actuator/conditions/bean inspection trong môi trường cho phép để xác minh thực tế.
- Đo tải trước/sau thay đổi, không dựa vào giả thuyết “ít threads = luôn tốt hơn”.

### 18.5. Dùng JDK và Spring mới hơn ví dụ

- Đọc **system requirements**, compatibility range, migration guide và Javadoc của đúng version.
- Xem thay đổi liên quan pinning, virtual-thread scheduler, reactive integration và lifecycle.
- Giữ Java 21 example như một **mốc tối thiểu ổn định**, không coi con số version minh họa trong bài là phiên bản mới nhất tại mọi thời điểm.

## 19. Các câu hỏi thường gặp

**Câu 1. Spring Framework 6.1 bắt buộc Java 21?**

Không. Baseline Java của dòng Spring Framework 6.x là Java 17. Tuy nhiên, tính năng **virtual threads** yêu cầu JVM Java 21+.

**Câu 2. Chạy JDK 21 với Spring Boot 3.1, thêm `spring.threads.virtual.enabled=true` được không?**

Không nên kỳ vọng property này hoạt động; Boot virtual-thread auto-configuration ra mắt từ **3.2**. Bạn vẫn có thể sử dụng API Java 21 thủ công nếu phù hợp, nhưng nên nâng Boot lên dòng có hỗ trợ chính thức khi cần tích hợp rộng.

**Câu 3. Java 21 có cần `--enable-preview` cho virtual threads?**

Không. Virtual threads chính thức từ JDK 21 theo JEP 444. Flags preview thuộc lịch sử JDK 19/20 hoặc các preview feature khác.

**Câu 4. Spring Boot 3.3 đặt property true thì mọi @Async sẽ virtual?**

Không phải mọi trường hợp. Nó phụ thuộc auto-configuration, bean `Executor` đang tồn tại, `AsyncConfigurer`, qualifier `@Async`, proxy và đường gọi thực tế. Dùng `Thread.isVirtual()` bên trong callback để kiểm chứng.

**Câu 5. Dùng `CompletableFuture.supplyAsync(...)` có tự là virtual?**

Không. Khi không truyền executor, `CompletableFuture` Java thông thường dùng default async execution facility, thường common pool, không phải executor virtual do Spring Boot tạo. Nếu muốn virtual, truyền executor phù hợp.

**Câu 6. Bật virtual threads có làm @Scheduled fixedDelay chạy song song?**

Không thể khẳng định như vậy. Với `SimpleAsyncTaskScheduler`, fixed-delay callbacks chạy trên scheduler thread; nếu callback lại submit async worker rồi return, completion semantics còn thay đổi. Xem scheduler và đường callback cụ thể.

**Câu 7. Có cần pool 200 virtual threads để chạy 200 tasks?**

Không phải mô hình thông thường. Virtual thread-per-task tạo một thread cho mỗi task, không tái sử dụng thread như platform pool. Giới hạn nên áp vào downstream/connection/operation concurrency khi cần.

**Câu 8. Vì sao code Java 21 chạy trên IDE nhưng lỗi ở container?**

Rất thường do runtime image vẫn JDK 17, hoặc container có JRE/JDK khác với toolchain. Kiểm tra `java -version` trong container thật và classfile target của artifact.

**Câu 9. Virtual threads có chuyển transaction vào @Async không?**

Không. Spring imperative transaction thường gắn thread; async boundary cần transaction riêng hoặc context propagation có chủ đích. Virtual threads không tự làm một EntityManager hoặc transaction shareable giữa worker threads.

**Câu 10. Bật virtual threads có đảm bảo job không mất khi restart?**

Không. Virtual threads nằm trong JVM. Nếu job cần durability, dùng database job state, transactional outbox hoặc durable queue với idempotency/retry.

## 20. Bài thực hành và checklist

### Bài 1 — Xác minh phiên bản Java

Chạy `java -version`, `javac -version`, `mvn -version` rồi biên dịch `VirtualThreadProbe.java` bằng JDK 21. Chứng minh `main-virtual=false` và `worker-virtual=true`. Thử đặt `--release 17` trong một môi trường test riêng và giải thích lỗi API.

### Bài 2 — Project Spring Framework thuần

Chạy `FrameworkVirtualDemo` bằng Maven Java 21+. Ghi nhận hai dòng `async-virtual=true` và `scheduled-virtual=true`. Đổi `@Async` qualifier sang executor platform-thread do bạn tạo, xác minh chỉ đường `@Async` tương ứng thay đổi trong khi scheduler virtual vẫn giữ nguyên.

### Bài 3 — Spring Boot property

Tạo project Spring Boot 3.3.x Java 21 với `@EnableAsync` và `@EnableScheduling`. Chạy một `@Async` service qua proxy và một `@Scheduled` callback. Thử bật/tắt `spring.threads.virtual.enabled`, kiểm tra runtime bean names/types và `Thread.isVirtual()`. Nếu có custom Executor bean, đối chiếu outcome và giải thích auto-config backoff.

### Bài 4 — Fixed-delay caveat

Với `SimpleAsyncTaskScheduler` virtual mode, tạo một fixed-rate job và một fixed-delay job, log thread name/isVirtual/time/actual completion. Giải thích tại sao `fixedDelay` trên scheduler này có execution semantics khác với one-shot task trong `FrameworkVirtualDemo`.

### Bài 5 — Quota và downstream

Dùng `VirtualThreadBoundedDemo`, thay `Semaphore(2)` bằng quota lớn hơn, đo active tasks và thời gian. Sau đó giải thích tại sao trong dự án thật cần dựa vào DB connection pool, HTTP rate limiter và timeout chứ không chỉ số lượng virtual threads.

### Bài 6 — Pinning và ThreadLocal

Trên **JDK 21**, tạo một workload blocking I/O trong vùng monitor dài, quan sát `jdk.VirtualThreadPinned` bằng JFR. So với workload không giữ monitor dài. Với JDK mới hơn, đọc thay đổi JDK trước khi diễn giải kết quả. Kiểm tra một ThreadLocal cache nặng được tạo một lần cho mỗi virtual task và phân tích memory cost.

### Bài 7 — CI/container

Xây Maven jar với Java 21, đưa vào runtime image Java 21. Cố tình thử Java 17 runtime ở một môi trường test riêng, giải thích `UnsupportedClassVersionError`. Sau đó sửa image và xác minh lại `Thread.isVirtual()` từ ứng dụng thật.

### Checklist

- [ ] Tôi phân biệt được Java 19/20 preview và Java 21 chính thức.
- [ ] Tôi biết JDK 21+ là điều kiện để dùng virtual threads chuẩn.
- [ ] Tôi không nhầm Java 17 baseline Spring 6.x với yêu cầu virtual-thread runtime.
- [ ] Tôi biết Spring Framework 6.1+ cung cấp API tích hợp virtual threads.
- [ ] Tôi biết Spring Boot 3.2+ giới thiệu `spring.threads.virtual.enabled`.
- [ ] Tôi hiểu property Boot chỉ điều chỉnh các đường auto-configured phù hợp.
- [ ] Tôi kiểm tra `java`/`javac`/Maven/Gradle/container runtime riêng biệt.
- [ ] Tôi hiểu `--release 21` không đổi runtime của container.
- [ ] Tôi có thể chạy `Thread.ofVirtual()` và `Executors.newVirtualThreadPerTaskExecutor()`.
- [ ] Tôi biết `VirtualThreadTaskExecutor` và `SimpleAsyncTaskExecutor#setVirtualThreads(true)`.
- [ ] Tôi biết cách xác nhận `@Async` gọi qua Spring proxy.
- [ ] Tôi biết `SimpleAsyncTaskScheduler` có ngoại lệ fixed-delay.
- [ ] Tôi không nhầm virtual-thread per task với pool cần cấu hình size.
- [ ] Tôi biết virtual threads không làm CPU, DB connections và mạng vô hạn.
- [ ] Tôi biết thread-local context, transaction và Hibernate Session không tự chia sẻ giữa tasks.
- [ ] Tôi đã xem xét daemon/keep-alive, shutdown và job durability.
- [ ] Tôi dùng `Thread.isVirtual()` trong đúng code path và đo workload thật.

**Kết luận:** **JDK 21+** cung cấp virtual threads chính thức; **Spring Framework 6.1+** cung cấp executor/scheduler integration, còn **Spring Boot 3.2+** hỗ trợ bật chúng bằng property khi các điều kiện auto-configuration được đáp ứng. Kiểm tra đủ **JVM thực thi, Spring dependency, bean executor/scheduler và thread của callback** mới chứng minh ứng dụng đang dùng virtual threads. Nâng cấp thành công đòi hỏi cả tính tương thích lẫn khả năng quản lý tài nguyên, transaction, lifecycle và độ tin cậy nghiệp vụ.

## 21. Tài liệu chính thức

- OpenJDK — [JEP 444: Virtual Threads](https://openjdk.org/jeps/444): virtual threads chính thức trong JDK 21.
- OpenJDK — [JEP 425](https://openjdk.org/jeps/425), [JEP 436](https://openjdk.org/jeps/436): các mốc preview Java 19/20.
- Oracle Java 21 — [Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html): API, scheduling, carrier, daemon, pinning, JFR và `newVirtualThreadPerTaskExecutor`.
- Spring Framework — [6.1 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.1-Release-Notes): hỗ trợ JDK 21/virtual threads và các executor/scheduler APIs.
- Spring Framework — [VirtualThreadTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/core/task/VirtualThreadTaskExecutor.html): executor dùng virtual thread.
- Spring Framework — [SimpleAsyncTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/core/task/SimpleAsyncTaskExecutor.html): `setVirtualThreads` và thread-per-task semantics.
- Spring Framework — [SimpleAsyncTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/SimpleAsyncTaskScheduler.html): virtual scheduling và fixed-delay exception.
- Spring Framework — [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `TaskExecutor`, `TaskScheduler`, `@Async`, `@Scheduled`.
- Spring Boot — [3.2 Virtual Threads](https://docs.spring.io/spring-boot/docs/3.2.10/reference/html/features.html): property và điều kiện executor/scheduler.
- Spring Boot — [3.3 Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.3/reference/features/task-execution-and-scheduling.html): auto-config virtual executor/scheduler và pooling-properties exception.
- Spring Boot — [Current Spring Application Reference](https://docs.spring.io/spring-boot/reference/features/spring-application.html): virtual thread enablement và keep-alive, nên đối chiếu với version đang dùng.

**Lưu ý về phiên bản:** Những ví dụ ghi rõ **Spring Framework 6.2.14 + Java 21** được chọn làm môi trường minh họa, không phải tuyên bố đây là bản Spring mới nhất. Với dự án mới hoặc nâng cấp sau này, hãy dùng system requirements và release documentation của đúng phiên bản dự án, bao gồm các dependencies bên thứ ba và môi trường triển khai thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-003">Bật virtualThreads cho SimpleAsyncTaskExecutor</a>

<details>
<summary>Xem chi tiết</summary>

# Bật virtualThreads cho SimpleAsyncTaskExecutor

> **Module:** Spring Framework – concurrency  
> **Nhóm:** Luồng ảo  
> **Mục tiêu:** Cấu hình `SimpleAsyncTaskExecutor.setVirtualThreads(true)` để mỗi task được thực thi trên **virtual thread** của Java 21+, nối executor vào Spring `@Async` hoặc dùng trực tiếp, tự kiểm tra bằng `Thread.currentThread().isVirtual()`, đồng thời hiểu giới hạn thực tế: không có thread pool, không tự tạo backpressure, không tăng tốc CPU, không tự bảo đảm transaction/context propagation, timeout hoặc tính bền vững sau restart.

## 1. Kết luận và điều kiện tiên quyết

**Bật virtual threads** cho một `SimpleAsyncTaskExecutor` trong Spring Framework 6.1 trở lên bằng:

~~~java
SimpleAsyncTaskExecutor executor =
    new SimpleAsyncTaskExecutor("virtual-");
executor.setVirtualThreads(true);
~~~

Đây là đoạn mã **tích hợp Java/Spring**, cần import `org.springframework.core.task.SimpleAsyncTaskExecutor`. API `setVirtualThreads(boolean)` đã xuất hiện từ Spring Framework **6.1** và **đòi hỏi Java 21+ khi bật**. Giá trị mặc định của cờ là `false`: nếu không bật, executor này tạo **platform thread** cho mỗi lần thực thi, không phải virtual thread.

Phải có đủ **cả hai điều kiện**:

1. **Runtime Java 21 hoặc cao hơn**, vì virtual threads trở thành tính năng chính thức của JDK ở Java 21 (JEP 444).
2. **Executor được gọi thực tế phải là instance đã bật cờ**, không chỉ một bean nằm đâu đó trong `ApplicationContext`.

Nếu method `@Async` vẫn được route sang một `ThreadPoolTaskExecutor` khác, `setVirtualThreads(true)` của bean bạn tạo **không tác động** đến lời gọi đó. Vì vậy cách kiểm chứng tốt nhất là vừa inspect wiring, vừa thực thi task và đọc `Thread.currentThread().isVirtual()` ở **bên trong task**.

Virtual thread giúp sử dụng tài nguyên thread hiệu quả hơn khi có rất nhiều việc chờ I/O. Nó **không** thay đổi hợp đồng nghiệp vụ: DB connection pool, HTTP rate limit, transaction, idempotency, retry, shutdown và lượng memory vẫn cần được kiểm soát riêng.

## 2. Virtual thread là gì và vì sao hữu ích với I/O?

Java có hai loại `Thread` phổ biến:

| Tiêu chí | Platform thread | Virtual thread |
| --- | --- | --- |
| Quan hệ với OS thread | Gắn với OS thread trong vòng đời chạy | JVM quản lý, được mount/unmount trên **carrier** platform thread |
| Chi phí khi chờ một I/O tương thích | Có thể giữ một OS thread | JVM thường có thể unmount virtual thread để carrier làm việc khác |
| Cách sử dụng thông thường | Dùng pool vì OS threads khan hiếm | Tạo **một virtual thread cho mỗi task**, không pool virtual threads |
| Task CPU-bound | Cần CPU thực tế | Vẫn cần CPU thực tế, không tạo thêm CPU |
| Thread API, stack, exceptions | `Thread` quen thuộc | Vẫn là `Thread`, dùng được `isVirtual()`, `interrupt()`... |
| Số lượng thực tế | Bị giới hạn bởi OS threads/memory | Có thể rất lớn, nhưng **không vô hạn** vì heap, stack và downstream vẫn hữu hạn |

Khi task gọi JDBC hoặc HTTP client theo kiểu blocking, virtual thread có thể bị tạm dừng mà không giữ chặt carrier trong nhiều trường hợp. Điều này cho phép viết code đồng bộ dễ đọc mà vẫn có throughput tốt khi hàng nghìn task đang chờ.

**Không nhầm throughput với latency:** một SQL query mất 200ms không tự thành 20ms chỉ vì nó chạy trên virtual thread. Virtual thread chủ yếu làm giảm chi phí **giữ thread trong thời gian chờ**, còn thời gian DB xử lý/đường truyền không được rút ngắn tự động.

**Không pool virtual threads:** mỗi task thường được cấp một virtual thread mới. Giới hạn số kết nối DB hoặc số lời gọi HTTP đồng thời nên được đặt ở resource đó hoặc ở concurrency limiter có chủ đích, không phải bắt virtual threads quay vòng trong một pool cố định như platform threads.

## 3. SimpleAsyncTaskExecutor có đặc điểm gì?

`SimpleAsyncTaskExecutor` triển khai `AsyncTaskExecutor`, dùng được ở Spring task execution và `@Async`. Điểm nổi bật: **không tái sử dụng thread**. Mỗi task được thực thi trên một thread mới; `setVirtualThreads(true)` chuyển cách tạo thread mới sang **virtual thread**.

~~~text
Caller
   |
   | executor.execute(task) / executor.submit(task)
   v
SimpleAsyncTaskExecutor
   |
   +-- kiểm tra concurrencyLimit (nếu đã đặt)
   |
   +-- tạo thread mới cho task
   |       setVirtualThreads(false) -> platform thread
   |       setVirtualThreads(true)  -> virtual thread (JDK 21+)
   |
   v
task.run() trên thread tương ứng
   |
   +-- thành công / exception / kết thúc
~~~

Khác với `ThreadPoolTaskExecutor`, ở đây không có đội worker cố định chờ lấy việc từ một work queue chung. `SimpleAsyncTaskExecutor` có **throttling** nhưng không phải một thread pool có `corePoolSize`/`maxPoolSize`/`queueCapacity` như `ThreadPoolTaskExecutor`.

Các API đáng chú ý:

- `execute(Runnable)`: giao một task để chạy, không trả business result.
- `submit(Runnable)` / `submit(Callable<T>)`: trả `Future` để quan sát completion/exception khi phù hợp.
- `setVirtualThreads(true)`: chọn virtual threads.
- `setConcurrencyLimit(int)`: giới hạn số task được phép chạy đồng thời; mặc định `-1`, không giới hạn tại tầng executor.
- `setRejectTasksWhenLimitReached(true)` (Spring **6.2.6+**): thay hành vi chờ khi hết slot bằng **từ chối ngay** với `TaskRejectedException`.
- `setTaskTerminationTimeout(long)`: cấu hình cách chờ task kết thúc khi `close()`; cần hiểu chi phí theo dõi task và lifecycle.

API này phù hợp cho những tác vụ độc lập, ngắn hoặc chờ I/O, khi muốn mỗi invocation được thực thi trên virtual thread. Nó không phải durable job queue, scheduler theo lịch cron, hoặc transaction manager.

## 4. Hai đường sử dụng: submit trực tiếp và @Async

**Đường 1 – dùng executor trực tiếp:**

~~~java
SimpleAsyncTaskExecutor executor =
    new SimpleAsyncTaskExecutor("demo-vt-");
executor.setVirtualThreads(true);

Future<Boolean> result =
    executor.submit(() -> Thread.currentThread().isVirtual());
boolean virtual = result.get();
~~~

Đây là **snippet tích hợp**, cần imports `Future`, và method chứa code xử lý `InterruptedException`/`ExecutionException`. Kết quả `virtual` là `true` nếu task thật sự chạy trên virtual thread và được executor này nhận.

**Đường 2 – dùng Spring `@Async`:**

~~~java
@Configuration
@EnableAsync
class AsyncConfig {
    @Bean("virtualExecutor")
    SimpleAsyncTaskExecutor virtualExecutor() {
        SimpleAsyncTaskExecutor executor =
            new SimpleAsyncTaskExecutor("orders-vt-");
        executor.setVirtualThreads(true);
        return executor;
    }
}

@Service
class OrderAsyncService {
    @Async("virtualExecutor")
    public CompletableFuture<Boolean> check() {
        return CompletableFuture.completedFuture(
            Thread.currentThread().isVirtual());
    }
}
~~~

Đây là **đoạn cấu hình tích hợp Spring**, cần imports cho annotation, `CompletableFuture` và service được Spring đăng ký. `@Async("virtualExecutor")` **chọn đúng bean executor** theo qualifier/name. Annotation chỉ được interceptor xử lý khi lời gọi đi qua Spring-managed proxy (với chế độ proxy mặc định); gọi `new OrderAsyncService().check()` hoặc self-invocation bằng `this.check()` sẽ không chứng minh được async routing.

Trong ví dụ trên, method trả `CompletableFuture<Boolean>` đã hoàn tất từ bên trong worker; Spring cung cấp một future ở phía caller để quan sát kết quả của **invocation qua proxy**. `CompletableFuture.completedFuture(...)` chỉ là hình thức return nội bộ theo hợp đồng `@Async`, không có nghĩa công việc đã chạy đồng bộ trên caller.

## 5. Ví dụ Maven Java 21 + Spring 6.2.9: chạy và chứng minh kết quả

Ví dụ bên dưới sử dụng Spring Framework **6.2.9**, compiler target **Java 21**, có hai kiểm chứng thực tế:

1. `@Async("virtualExecutor")` và lệnh `executor.submit(...)` đều chạy trên virtual thread, cho `isVirtual() == true`.
2. Với executor khác có `concurrencyLimit=1` và `rejectTasksWhenLimitReached=true`, khi task đầu đang chiếm slot, submit thứ hai bị từ chối. Dùng `CountDownLatch` để tạo trạng thái bận một cách xác định, thay vì may rủi dùng `sleep`.

### Cấu trúc project

~~~text
virtual-simple-async-demo/
├── pom.xml
└── src/main/java/demo/VirtualSimpleAsyncDemo.java
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>virtual-simple-async-demo</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.release>21</maven.compiler.release>
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
      <version>3.13.0</version>
      <artifactId>maven-compiler-plugin</artifactId>
    </plugin>
    <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>exec-maven-plugin</artifactId>
      <version>3.5.1</version>
    </plugin>
  </plugins></build>
</project>
~~~

### src/main/java/demo/VirtualSimpleAsyncDemo.java

~~~java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

public class VirtualSimpleAsyncDemo {
    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class AppConfig {
        @Bean("virtualExecutor")
        SimpleAsyncTaskExecutor virtualExecutor() {
            SimpleAsyncTaskExecutor executor =
                new SimpleAsyncTaskExecutor("virtual-work-");
            executor.setVirtualThreads(true);
            executor.setConcurrencyLimit(4);
            executor.setRejectTasksWhenLimitReached(true);
            return executor;
        }

        @Bean
        AsyncProbe asyncProbe() {
            return new AsyncProbe();
        }
    }

    public static class AsyncProbe {
        @Async("virtualExecutor")
        public CompletableFuture<Boolean> runsOnVirtualThread() {
            return CompletableFuture.completedFuture(
                Thread.currentThread().isVirtual());
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {
            boolean asyncVirtual = context.getBean(AsyncProbe.class)
                .runsOnVirtualThread().get(3, TimeUnit.SECONDS);
            System.out.println("@Async is virtual: " + asyncVirtual);

            SimpleAsyncTaskExecutor executor =
                context.getBean("virtualExecutor",
                    SimpleAsyncTaskExecutor.class);
            boolean directVirtual = executor.submit(
                () -> Thread.currentThread().isVirtual())
                .get(3, TimeUnit.SECONDS);
            System.out.println("Direct is virtual: " + directVirtual);
        }

        SimpleAsyncTaskExecutor limited =
            new SimpleAsyncTaskExecutor("limited-virtual-");
        limited.setVirtualThreads(true);
        limited.setConcurrencyLimit(1);
        limited.setRejectTasksWhenLimitReached(true);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> first = limited.submit(() -> {
                started.countDown();
                try {
                    if (!release.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("release timed out");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            });
            if (!started.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("task never started");
            }

            boolean rejected = false;
            try {
                limited.execute(() -> {});
            } catch (TaskRejectedException expected) {
                rejected = true;
            }
            System.out.println("Limit rejected: " + rejected);
            release.countDown();
            first.get(3, TimeUnit.SECONDS);
            System.out.println("First task finished: true");
        } finally {
            release.countDown();
            limited.close();
        }
    }
}
~~~

Chạy từ thư mục gốc project bằng **JDK 21+** và Maven:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.VirtualSimpleAsyncDemo
~~~

**Stdout của chương trình** trong môi trường không bị timeout do tải quá nặng:

~~~text
@Async is virtual: true
Direct is virtual: true
Limit rejected: true
First task finished: true
~~~

Mã đã được **biên dịch với `--release 21` và chạy thành công** bằng JDK 24 cùng Spring Framework 6.2.9; bốn dòng trên được đối chiếu chính xác. Khi chạy trên máy quá chậm hoặc scheduler không còn phục vụ, các lệnh chờ `3s` có thể phát sinh timeout thay vì in đủ bốn dòng; đó là cơ chế fail-fast của demo, không phải khác biệt về tính chất virtual thread.

**Không phải Java 17:** `Thread.isVirtual()` và tính năng virtual threads chuẩn xuất hiện ở Java 21; nếu cố chạy code này trên Java 17, bài thực hành không thể hoạt động. Cấu hình target Maven `21` ở đây là có chủ đích.

## 6. Giải thích từng kết quả của ví dụ

Dòng `@Async is virtual: true` xác nhận lời gọi `AsyncProbe.runsOnVirtualThread()` đi qua Spring async interceptor và chạy bên trong task của bean tên `virtualExecutor`; nếu chạy trực tiếp trên caller `main` là platform thread, `Thread.currentThread().isVirtual()` sẽ không cho cùng kết quả đó.

Dòng `Direct is virtual: true` xác nhận chính `SimpleAsyncTaskExecutor.submit(Callable)` cũng tạo virtual thread, không phụ thuộc annotation. Như vậy cờ `setVirtualThreads(true)` là thuộc tính của **executor**, còn `@Async` là **cơ chế route invocation** của Spring.

Dòng `Limit rejected: true` minh họa một **cơ chế admission**. Task đầu dùng `CountDownLatch` giữ một slot của executor giới hạn 1. Sau khi chắc chắn task đó đã bắt đầu, lệnh `execute` thứ hai nhận `TaskRejectedException` khi `rejectTasksWhenLimitReached=true`. Không có queue pool để tự động chứa request thứ hai.

Dòng `First task finished: true` xuất hiện sau khi `release.countDown()` và `first.get(...)` trả về, chứng minh tài nguyên của task đầu đã được giải phóng trước khi đóng executor. `finally` luôn giải phóng latch, giúp tránh kẹt task nếu một assertion thất bại.

Chương trình dùng hai executor **cố ý**: một là bean Spring được `@Async` dùng, cái còn lại chỉ để kiểm tra admission limit trong local test. Chúng không chia sẻ concurrency limit; mỗi executor có chính sách riêng.

## 7. Phân biệt ba tầng: @Async proxy, executor và virtual thread

Một lời gọi `@Async` có nhiều bước:

~~~text
Caller thread
   |
   | gọi bean Spring qua proxy
   v
@Async interceptor
   |
   | xác định qualifier/bean Executor
   v
SimpleAsyncTaskExecutor đã setVirtualThreads(true)
   |
   | kiểm tra concurrencyLimit, có thể BLOCK/REJECT caller
   v
Tạo virtual thread cho task
   |
   v
Thực thi method trên virtual thread
   |
   +-- method hoàn tất -> Future completed
   +-- method lỗi -> Future exceptional / void handler
~~~

**Mỗi tầng có trách nhiệm riêng:**

- **Proxy/interceptor** quyết định method có bị chạy async và executor nào được dùng.
- **Executor** quyết định cách nộp công việc, policy concurrency và cách tạo thread.
- **Virtual thread** quyết định task được JVM chạy ra sao trên carrier, nhưng không thay đổi transaction, error contract hay kết quả của lời gọi mạng.

Nếu method không đi qua proxy, bạn có thể thấy `isVirtual=false` dù bean executor được cấu hình đúng. Nếu executor bean tên khác với qualifier trong `@Async`, lời gọi có thể thất bại hoặc chọn sai executor. Nếu có hai bean cùng tên không hợp lệ hoặc cấu hình `AsyncConfigurer` khác, phải xác minh wiring qua integration test.

Lưu ý: **lời gọi `@Async` không luôn lập tức trả về**. Nếu `setConcurrencyLimit(n)` được dùng và mặc định behavior là block khi đầy, thread caller có thể phải chờ slot **ngay trong bước nộp**, trước khi method nghiệp vụ chạy trên virtual thread. Với reject mode, caller có thể nhận `TaskRejectedException` ngay.

## 8. Concurrency limit: mặc định không giới hạn, và hai hành vi lúc đầy

`SimpleAsyncTaskExecutor.setConcurrencyLimit(int)` đặt giới hạn số **task đồng thời** được phép chạy qua executor, không phải kích thước của virtual thread pool.

| Cấu hình | Hành vi |
| --- | --- |
| Không gọi `setConcurrencyLimit` | Mặc định `-1`: không throttling từ executor; vẫn giới hạn bởi tài nguyên JVM và downstream |
| `setConcurrencyLimit(8)` | Cùng lúc tối đa 8 task được chấp nhận để thực thi theo bộ đếm của executor |
| `setConcurrencyLimit(8)` với mặc định reject=false | Caller nộp task thứ 9 có thể **bị block** cho đến khi có slot |
| `setConcurrencyLimit(8)` và `setRejectTasksWhenLimitReached(true)` | Caller nộp vượt limit có thể nhận **`TaskRejectedException` ngay** |
| `setConcurrencyLimit(0)` | Chế độ không cho phép concurrency; cần policy phù hợp và không dùng như giới hạn production thông thường |

**Chú ý version:** `setRejectTasksWhenLimitReached(boolean)` xuất hiện từ Spring Framework **6.2.6**. Nếu dự án dùng bản 6.1 hoặc 6.2 đời đầu, flag bật virtual threads vẫn có, nhưng API chọn reject này **có thể chưa tồn tại**. Đừng sao chép demo Spring 6.2.9 sang phiên bản thấp hơn mà không nâng dependency hoặc điều chỉnh strategy admission.

**Không nhầm block submitter với queue có giới hạn:** Khi một platform thread request phải chờ slot của SimpleAsyncTaskExecutor, platform thread ấy vẫn bị chiếm. Ở service latency-sensitive, nên dùng bounded ingress, timeout/tryAcquire/semaphore, hoặc mode reject để có **backpressure** rõ.

Với executor không giới hạn task mà downstream DB chỉ có 10 connections, bạn không nên tạo 100.000 virtual threads cùng chờ `getConnection()` rồi coi ứng dụng an toàn: memory, connection wait, timeout và pool contention vẫn có thể cao.

## 9. Khi nào cần Semaphore thay vì bóp số virtual threads?

Virtual threads được thiết kế để **không cần pool**. Nhưng tài nguyên bên dưới như database, HTTP rate limit, broker producer hay API thanh toán vẫn có số lượng hữu hạn.

Ví dụ giới hạn 20 lời gọi đến một API:

~~~java
private final Semaphore downstreamSlots = new Semaphore(20);

public Response callRemote(Request request) throws InterruptedException {
    if (!downstreamSlots.tryAcquire(300, TimeUnit.MILLISECONDS)) {
        throw new TooBusyException("downstream is saturated");
    }
    try {
        return httpClient.execute(request);
    } finally {
        downstreamSlots.release();
    }
}
~~~

Đây là **mẫu ghép vào service**, cần tự định nghĩa `Response`, `Request`, `TooBusyException`, `httpClient` và imports `Semaphore`, `TimeUnit`. Đây **không phải** Java program độc lập. `tryAcquire` giúp định nghĩa rõ thời gian chờ. Một timeout tách biệt vẫn phải cấu hình ở **HTTP client** để resource không bị giữ mãi.

Không xếp chồng semaphore lên connection pool một cách máy móc: pool có thể đã là bộ giới hạn hữu hiệu đối với DB. Hãy chọn ranh giới phù hợp, tránh nhiều tầng giới hạn không rõ nhiệm vụ gây starvation hoặc khó chẩn đoán.

Hai chiến lược khác nhau:

1. **Executor admission limit:** giới hạn tổng số task ở executor, có thể ảnh hưởng mọi loại việc dùng chung bean.
2. **Downstream semaphore/rate limiter:** giới hạn đúng loại thao tác đối với một tài nguyên cụ thể, thường linh hoạt hơn khi nhiều workload chia sẻ executor.

Có thể kết hợp nếu có mục tiêu cụ thể về cả memory/concurrency tổng và quota của downstream, nhưng phải đo và hiểu thời gian chờ ở từng tầng.

## 10. Virtual threads và ThreadPoolTaskExecutor không phải cấu hình tương đương

`ThreadPoolTaskExecutor` dùng pool platform threads/queue theo cấu hình thông dụng. `SimpleAsyncTaskExecutor` ở chế độ virtual tạo một virtual thread **mới cho mỗi task**, không tái sử dụng.

| Thuộc tính | ThreadPoolTaskExecutor | SimpleAsyncTaskExecutor + virtualThreads |
| --- | --- | --- |
| Kiến trúc | Worker thread pool, có thể có task queue | Thread-per-task, **không pool** |
| Cấu hình chính | `corePoolSize`, `maxPoolSize`, `queueCapacity` | `virtualThreads`, `concurrencyLimit`, reject mode, lifecycle |
| Khi worker bận | Có thể xếp hàng hoặc reject tùy pool/queue | Tạo thêm virtual thread hoặc chặn/từ chối theo concurrency limit |
| Task I/O blocking nhiều | Platform thread thường giữ tài nguyên thread khi chờ | Virtual thread có thể nhường carrier trong nhiều thao tác I/O |
| Task CPU-bound | Giới hạn concurrency theo pool | Không tăng số CPU; tạo quá nhiều task có thể tạo thêm overhead |
| Bền vững qua restart | Không | Không |
| Bảo đảm một lần thực thi | Không | Không |
| Nơi nên đặt backpressure | Bounded pool/queue/ingress | Ingress, concurrency limit, downstream limiter và quota |

Không “nâng cấp” một `ThreadPoolTaskExecutor` sang virtual threads bằng cách chỉ đặt `maxPoolSize` thật lớn. Đó vẫn là mô hình pool truyền thống. Nếu cần thread-per-task virtual, chọn executor hỗ trợ hoặc JDK virtual-thread-per-task executor.

Nếu hệ thống cần queue, số lượng job lớn phải sống qua restart và retry, cần **job store/queue bền vững**. `SimpleAsyncTaskExecutor` không cung cấp durable queue dù task chạy bằng virtual thread.

## 11. Java 21+ là yêu cầu bắt buộc, không thể chạy JDK 17

Cờ `setVirtualThreads(true)` gọi vào cơ chế tạo virtual thread của JDK. Trên Java 17 không có API chuẩn `Thread.isVirtual()`, `Thread.ofVirtual()` và `Executors.newVirtualThreadPerTaskExecutor()`. Vì vậy:

- Spring 6.1/6.2 **có thể** chạy trên Java 17 cho các tác vụ thông thường, nhưng **chế độ virtual threads đòi runtime Java 21+**.
- Không nên thêm một fallback im lặng `catch (...) { setVirtualThreads(false); }` rồi báo hệ thống đã bật virtual threads. Fallback có thể thay đổi đáng kể mô hình resource của production.
- Khi nâng JDK, test cả driver, logging, tracing, ThreadLocal, bean lifecycle, scheduler và performance, không chỉ test một method trả `isVirtual=true`.
- Build release `21` trong ví dụ giúp loại bỏ mâu thuẫn giữa source và runtime. Dùng JDK 24 để compile `--release 21` vẫn hợp lệ, nhưng deploy thực tế phải cung cấp runtime 21+.
- Nếu dự án dùng Java 17 và không thể nâng, tiếp tục dùng platform-thread executor/queue có giới hạn hoặc các kỹ thuật asynchronous/reactive thích hợp, thay vì đánh dấu tính năng virtual là đã hoạt động.

Một câu lệnh kiểm tra runtime đơn giản:

~~~bash
java -version
~~~

Nó chỉ kiểm tra version. **Bằng chứng executor dùng virtual threads** vẫn nên là `Thread.currentThread().isVirtual()` được chạy trong task thực tế, như mục 5.

## 12. Spring Boot và property spring.threads.virtual.enabled=true

Với **Spring Boot 3.2+** trên Java 21+, thuộc tính:

~~~properties
spring.threads.virtual.enabled=true
~~~

cho phép Boot auto-configure các thành phần phù hợp dùng virtual threads theo version và điều kiện auto-configuration. Trong một cấu hình Boot thông dụng, nếu không có `Executor` bean tùy chỉnh thay thế, Boot có thể cung cấp một `SimpleAsyncTaskExecutor` sử dụng virtual threads cho async execution.

**Đây không phải cùng cơ chế với việc gọi `setVirtualThreads(true)` thủ công.**

- **Spring Framework thuần:** property Boot không tự nhiên có hiệu lực nếu ứng dụng không dùng Boot auto-configuration tương ứng; hãy khai báo bean executor rõ ràng.
- **Spring Boot:** auto-configuration có điều kiện, có thể bị thay thế bởi custom `Executor`/ `AsyncConfigurer`, khác nhau theo version. `@Async("someExecutor")` luôn cần kiểm tra đúng bean được chọn.
- **Không có nghĩa tất cả Spring task đổi sang virtual:** scheduler, HTTP request handling, messaging và các integration khác có policy riêng. Xem tài liệu Boot đúng version.
- **Không đồng nghĩa mọi task chạy virtual:** một custom `ThreadPoolTaskExecutor` có thể vẫn đang làm việc cho method khác.

Cách kiểm chứng: gọi `@Async` qua Spring proxy thật, ghi một giá trị `isVirtual()` tại worker trong integration test, cùng với log bean name/class đang dùng. **Không chỉ kiểm tra YAML** rồi tuyên bố hệ thống đã chạy virtual threads.

## 13. AsyncConfigurer, qualifier và lỗi chọn nhầm executor

Spring hỗ trợ `@Async("virtualExecutor")` để chỉ rõ executor. Nếu muốn một default executor cho toàn bộ `@Async`, có thể dùng `AsyncConfigurer.getAsyncExecutor()` hoặc cơ chế chọn bean mặc định; tuy nhiên cần nhất quán với các integration khác.

Ví dụ cấu hình default (snippet):

~~~java
@Configuration
@EnableAsync
public class VirtualExecutorConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        SimpleAsyncTaskExecutor executor =
            new SimpleAsyncTaskExecutor("default-vt-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(100);
        return executor;
    }
}
~~~

Đây là **snippet tích hợp**, cần imports `Executor`, `AsyncConfigurer`, annotations và xem xét lifecycle bean. Cách trả một object trực tiếp như trên có thể khó quản lý lifecycle hơn việc khai báo **`@Bean`** và trả bean đó; production nên đặt executor vào container để đóng đúng lúc và đo được.

Không khai báo nhiều default executors cạnh tranh rồi tin Spring tự chọn “cái có virtualThreads=true”. Quy tắc chọn dựa trên annotation qualifier, `AsyncConfigurer`, bean type/name và cấu hình. Nếu có nhiều bean, hãy **đặt tên rõ và chỉ định nơi sử dụng**, đồng thời test.

Trong demo mục 5, `@Async("virtualExecutor")` làm tiêu chí kiểm chứng đơn giản: method chắc chắn phải đi qua executor đã bật virtual nếu proxy hoạt động bình thường.

## 14. Method @Async phải được intercept: self-invocation không đủ

Trong chế độ proxy mặc định, advice của `@Async` được áp lên lời gọi **đi qua proxy Spring**. Ví dụ anti-pattern:

~~~java
@Service
public class Reports {
    public void start() {
        this.generateAsync(); // self-invocation: thường bỏ qua @Async advice
    }

    @Async("virtualExecutor")
    public void generateAsync() {
        // Có thể chạy trên thread hiện tại khi gọi qua this.
    }
}
~~~

Đây là **snippet minh họa**, không phải class độc lập. Một lời gọi `this.generateAsync()` không đi qua proxy nên không bảo đảm chạy trên virtual thread, dù executor bean được cấu hình hoàn hảo. Tương tự, tạo service bằng `new` bên ngoài Spring rồi gọi `@Async` không kích hoạt interceptor.

Giải pháp: gọi qua một bean khác được Spring inject, dùng đúng proxy/architecture, hoặc gọi executor trực tiếp nếu đó là điều thực sự cần. Không lạm dụng lấy proxy của chính mình chỉ để né thiết kế dependency thiếu rõ ràng.

Cần cân nhắc các giới hạn proxy như phương thức không phù hợp để intercept (private, final trong class-based proxy, method không được Spring quản lý). Một integration test với `Thread.currentThread().isVirtual()` trong thân phương thức sẽ phát hiện lỗi routing tốt hơn việc chỉ assert annotation có mặt.

## 15. @Async void và CompletableFuture: kênh xử lý lỗi không đổi

Bật virtual thread **không thay đổi hợp đồng trả về** của method `@Async`.

- Method trả `void`: exception chưa bắt trong worker được xử lý qua `AsyncUncaughtExceptionHandler` của Spring, không tự truyền exception về stack của caller sau khi task chạy.
- Method trả `Future<T>` hoặc `CompletableFuture<T>`: caller có thể nhận exceptional completion và quan sát qua `get()`, `join()`, `whenComplete()`, v.v.
- Lỗi **nộp task** khi concurrency limit đã đầy và reject mode bật có thể xuất hiện **ngay ở caller** dưới dạng `TaskRejectedException`; phân biệt với exception của thân method sau khi đã nộp thành công.
- `CompletableFuture<Void>` vẫn là một future; nó không tương đương với return type `void`.
- Nếu method `@Async void` khởi chạy thêm future khác rồi bỏ qua, lỗi phát sinh muộn trong future con không tự được `AsyncUncaughtExceptionHandler` theo dõi.

Ví dụ:

~~~java
@Async("virtualExecutor")
public CompletableFuture<String> fetchStatus(long id) {
    String status = remoteClient.readStatus(id);
    return CompletableFuture.completedFuture(status);
}
~~~

Đây là **snippet tích hợp**, `remoteClient` giả định đã được inject, có timeout và error handling. Virtual thread có thể chạy một lời gọi blocking I/O rồi trả kết quả theo hợp đồng Spring. Caller nên kiểm tra lỗi future, không chỉ bỏ qua reference rồi tin hệ thống có trạng thái thành công.

Nếu công việc là fire-and-forget quan trọng (ví dụ thanh toán), `void` không phải giải pháp đảm bảo delivery. Cần job record/queue có trạng thái, idempotency key và cơ chế phục hồi sau crash.

## 16. Transaction, SecurityContext và MDC không tự dịch chuyển an toàn

Virtual thread là **một thread khác** với caller. Các context gắn với thread thường không tự được truyền:

- **Spring imperative transaction:** `@Transactional` dựa vào transaction context gắn với thread. Một lời gọi `@Async` trên virtual thread **không tiếp tục transaction của caller** theo cách đồng bộ.
- **SecurityContext:** tùy cấu hình Spring Security, executor wrapper và strategy. Không giả định authentication/request principal của caller có sẵn.
- **MDC/tracing:** các thread-local logging context không tự được sao chép chỉ vì thread là virtual; có thể cần `TaskDecorator`, context snapshot hoặc tracing instrumentation.
- **Request scope:** request attributes, HTTP response object hoặc `HttpServletRequest` của caller có lifecycle riêng; không nên giữ chúng trong task nền lâu hơn request.
- **JPA EntityManager/Hibernate Session:** object managed/lazy proxy có thể không an toàn khi chuyển sang thread khác; gửi ID/immutable DTO, mở transaction mới đúng ranh giới.

Ví dụ chuyển một correlation ID tối thiểu bằng task decorator (đoạn tích hợp, cần chọn thư viện MDC đang dùng và kiểm thử):

~~~java
SimpleAsyncTaskExecutor executor =
    new SimpleAsyncTaskExecutor("correlated-vt-");
executor.setVirtualThreads(true);
executor.setTaskDecorator(task -> {
    String traceId = MDC.get("traceId");
    return () -> {
        try {
            if (traceId != null) MDC.put("traceId", traceId);
            task.run();
        } finally {
            MDC.remove("traceId");
        }
    };
});
~~~

Đây là **mã ghép vào ứng dụng**, yêu cầu import `MDC` từ logging facade đang dùng; không phải chương trình Maven độc lập. Việc capture cần xảy ra khi task được submit và restore/cleanup phải tuân theo framework tracing/security của dự án. Không sao chép toàn bộ ThreadLocal hoặc bí mật người dùng vào hàng trăm nghìn virtual threads mà không có chính sách giới hạn/lifecycle.

## 17. ThreadLocal nhiều quá có thể tăng heap và GC

Virtual threads hỗ trợ `ThreadLocal`, nhưng việc tạo nhiều thread mới cho nhiều task làm thay đổi giả định thường có của code cũ: “tôi lưu một object đắt tiền trong ThreadLocal và sẽ dùng lại nhiều lần trên một worker pooled thread”.

Với thread-per-task, mỗi virtual thread chỉ sống cho một task; một `ThreadLocal.withInitial(() -> new ExpensiveParser())` có thể tạo ra **một instance cho mỗi task**. Khi khối lượng task lớn, heap allocation và GC pressure có thể tăng đáng kể.

Đối với object immutable/thread-safe như `DateTimeFormatter`, có thể dùng một instance dùng chung. Đối với dữ liệu request-specific, truyền rõ tham số hoặc dùng context propagation framework; không tự tạo một cache vô hạn trên mỗi thread.

Không khẳng định virtual thread không hỗ trợ ThreadLocal: **có hỗ trợ**, nhưng cách dùng có thể khiến lợi ích scale bị triệt tiêu. Bằng cách đo allocation rate và profiler, có thể tìm các thư viện cũ vô tình tạo nhiều object theo `Thread.currentThread()`.

Tương tự, giữ reference tới nhiều `Thread` sau khi task xong, hoặc lưu `Future` không được dọn trong map, sẽ gây memory retention mặc dù virtual thread tương đối nhẹ.

## 18. Pinning: phải nói rõ phiên bản JDK

Một số thao tác blocking có thể khiến virtual thread không thể nhường carrier, ảnh hưởng throughput. Nhưng **quy tắc pinning đã thay đổi theo version Java**:

- Trên **JDK 21–23**, blocking kéo dài khi giữ monitor `synchronized` có thể **pin** virtual thread lên carrier platform thread. Native/foreign calls cũng có những tình huống gây pinning. Không phải mọi `synchronized` đều xấu; rủi ro là block I/O **lâu và thường xuyên** trong critical section.
- Với **JDK 24**, **JEP 491 — Synchronize Virtual Threads without Pinning** cải thiện việc mount/unmount khi virtual thread block trong `synchronized`. Vì vậy không nên tuyên bố rằng mọi synchronized I/O trên JDK 24+ chắc chắn còn pin carrier như trước.
- Một số trường hợp pinning/giới hạn khác vẫn có thể tồn tại tùy JDK, native integration và implementation. Hãy đo bằng Java Flight Recorder hoặc công cụ của JDK tương ứng.

Mẫu cần kiểm tra trên JDK 21:

~~~java
synchronized (sharedLock) {
    blockingNetworkCall(); // JDK 21-23: có thể pin nếu chờ lâu.
}
~~~

Đây là **phản ví dụ tích hợp**, `sharedLock`/`blockingNetworkCall` cần được định nghĩa. Nếu thread chỉ lock ngắn để cập nhật một biến in-memory thì không có lý do tự động đổi mọi `synchronized` thành lock phức tạp.

JDK Flight Recorder có event liên quan virtual threads, trong đó có `jdk.VirtualThreadPinned`. Tên/khả năng quan sát và ngưỡng mặc định có thể khác giữa JDK versions; kiểm tra tài liệu runtime và dùng profiling **trên môi trường thực tế**.

## 19. Timeout và cancellation không trở nên đơn giản hơn

Virtual threads có thể block rẻ hơn trong nhiều thao tác I/O, nhưng một task bị timeout hoặc canceled **không tự động khiến remote service ngừng xử lý**.

- Khi gọi JDBC, cấu hình connection pool acquisition timeout, statement/query timeout, driver socket timeout và transaction rule đúng chỗ.
- Khi gọi HTTP, cấu hình connect timeout và request/response timeout trên HTTP client, có deadline tổng theo nghiệp vụ.
- Khi `Future.cancel(true)` được gọi, JVM có thể yêu cầu interrupt thread; thư viện I/O/driver không được bảo đảm sẽ dừng tức thời, rollback hoặc xác nhận remote chưa làm side effect.
- Với `CompletableFuture.orTimeout(...)`, future có thể được hoàn tất ngoại lệ trong khi blocking code bên dưới **vẫn chạy**. Timeout ở lớp future không thay thế timeout ở I/O boundary.
- Nếu task phải sống qua JVM restart hoặc đảm bảo delivery, dùng durable queue/job store. Executor không lưu bền dữ liệu và không tự replay job.

Không retry nóng hàng nghìn virtual tasks khi downstream đang quá tải. Virtual threads rẻ **không** khiến downstream database hoặc broker mạnh hơn; retry cần exponential backoff + jitter, max attempts, deadline và idempotency.

## 20. Lifecycle và shutdown của SimpleAsyncTaskExecutor

`SimpleAsyncTaskExecutor` không phải một pooled executor có work queue và coordinated lifecycle stop giống mọi implementation của `ThreadPoolTaskExecutor`. Trong Spring 6.1+, nó có khả năng `close()` và `setTaskTerminationTimeout(long)` để **chờ task termination ở mức chính sách tương ứng**.

Nếu không cấu hình task termination timeout, đừng mặc định `close()` sẽ chờ mọi việc đang chạy hoàn tất: mặc định timeout 0 không phải cơ chế bảo đảm graceful completion cho tất cả tasks. Bật tracking timeout dương có **chi phí quản lý mỗi submitted task** và cần benchmark theo khối lượng công việc.

Trong ví dụ, method `@Async` đã được `Future.get()` chờ hoàn tất trước khi đóng `ApplicationContext`. Executor giới hạn cũng giải phóng latch và đợi task đầu bằng `Future.get()` trước khi gọi `close()`. Vì thế kết quả test không phụ thuộc vào một chính sách shutdown ngầm.

Đối với production:

1. Có cơ chế **ngừng nhận job mới** khi shutdown bắt đầu.
2. Định nghĩa thời gian chờ task in-flight và cách hủy/cancel khi quá hạn.
3. Kiểm soát DB/HTTP timeout để thread không giữ kết nối mãi.
4. Lưu trạng thái nhiệm vụ quan trọng trước khi chấp nhận task, hoặc dùng durable queue.
5. Có cơ chế phục hồi task unfinished sau restart và xử lý duplicate an toàn.
6. Test graceful shutdown dưới tải, không chỉ test JVM thoát khi idle.

Không coi `Thread.isVirtual()` là bằng chứng task tự động có lifecycle an toàn.

## 21. Quan sát trong production: đo hiệu quả thật

Để đánh giá bật virtual threads có giúp ích hay không, nên benchmark **trước và sau** với cùng workload, data size và giới hạn downstream.

Những thước đo cần tách:

| Metric / hiện tượng | Lý do |
| --- | --- |
| Incoming request rate, accepted/rejected tasks | Biết admission policy có đúng không |
| Active virtual tasks, in-flight operations | Task chưa xong có thể giữ memory/context/connection |
| Latency p50/p95/p99 và throughput | Đánh giá thành quả với SLO thật |
| HikariCP active/idle/pending connections | DB connection pool có thể trở thành bottleneck |
| HTTP client pool wait, 429/503, timeout | Remote API không tự scale theo số virtual threads |
| CPU utilization, heap, allocation/GC | Virtual threads không làm CPU/heap vô hạn |
| Future exceptional completion, uncaught async errors | Lỗi runtime có thể bị bỏ sót |
| Task submission blocked/rejected duration | `setConcurrencyLimit` có thể dời bottleneck sang caller |
| JFR virtual-thread events / pinning | Phát hiện carrier starvation hoặc native/driver vấn đề |
| Shutdown unfinished task count | Kiểm tra khả năng dừng/khôi phục workflow |

Đừng xuất raw user ID, order ID, task name động hoặc HTTP URL chứa token thành high-cardinality metric label. Dùng tracing/log có kiểm soát cho correlation ID và dữ liệu nghiệp vụ khi thực sự cần.

Khi test, nhớ so sánh trên **cùng JDK version**; riêng JDK 24 có cải tiến pinning so với 21–23. Nếu workload CPU-bound, thay executor có thể không cải thiện throughput dù `isVirtual=true`.

## 22. Bộ kiểm thử gợi ý cho việc bật virtualThreads

Một test plan tốt cần xác minh cả **cơ chế** lẫn **độ an toàn**:

1. **Java version:** runtime và target release ít nhất 21; khởi động không fallback im lặng.
2. **Direct execution:** gọi `submit(() -> Thread.currentThread().isVirtual())` và assert `true`.
3. **Spring @Async:** lấy bean qua context, gọi `@Async("virtualExecutor")`, `Future.get()` trả `true`.
4. **Wrong routing:** giả lập một executor khác và xác nhận qualifier chọn đúng bean.
5. **Self-invocation:** test gọi `this.asyncMethod()` và chứng minh proxy advice có thể bị bỏ qua.
6. **Limit behavior:** giữ task đầu bằng latch, nộp task thứ hai; kiểm tra blocking hoặc `TaskRejectedException` theo cấu hình.
7. **Task error:** một `@Async CompletableFuture` ném lỗi; xác nhận returned future exceptional, không bỏ qua exception.
8. **@Async void:** một method ném lỗi; kiểm tra `AsyncUncaughtExceptionHandler` tương ứng.
9. **Downstream exhaustion:** cố ý giới hạn DB pool/HTTP quota; xác nhận có backpressure, timeout và retry budget thay vì tạo vô hạn task.
10. **Context propagation:** test logging MDC, security principal và transaction boundary theo architecture của ứng dụng.
11. **ThreadLocal allocation:** kiểm tra các thư viện dùng ThreadLocal cache có tạo quá nhiều object.
12. **Shutdown:** dừng context khi task đang chờ I/O; đo policy chờ, cancel và unfinished work.
13. **JDK pinning:** dùng JFR/test tải trên version JVM thực tế, đặc biệt nếu đang chạy JDK 21–23.
14. **High load:** đo p95/p99, CPU, heap và GC; không chỉ so sánh một task đơn lẻ.
15. **Idempotency/restart:** nếu có side effect, giả lập crash/retry/duplicate và verify business state cuối.

Các test concurrency nên dùng `CountDownLatch` hoặc synchronization primitives để tái hiện thứ tự; đừng dùng `Thread.sleep(10)` và kỳ vọng hai task tranh nhau theo một thứ tự cố định.

## 23. Những hiểu nhầm thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “Có `SimpleAsyncTaskExecutor` nghĩa là chạy virtual thread.” | Phải bật `setVirtualThreads(true)` và có Java 21+. |
| “Bật property Spring Boot là mọi executor trong app chuyển sang virtual.” | Auto-config có điều kiện, custom executor có thể không bị thay. |
| “Virtual threads là một thread pool nhẹ.” | `SimpleAsyncTaskExecutor` tạo thread mới cho mỗi task, không pool. |
| “Virtual thread khiến mỗi SQL query nhanh hơn.” | Chủ yếu cải thiện khả năng scale khi chờ I/O, không thay đổi thời gian DB xử lý. |
| “Tạo bao nhiêu virtual threads cũng không sao.” | Heap, DB pool, file descriptors và downstream vẫn hữu hạn. |
| “Có `concurrencyLimit=8` thì executor có queue capacity=8.” | Đây là giới hạn số task; không phải một pooled work queue. |
| “Limit đầy luôn từ chối request.” | Mặc định có thể block submitter; reject mode phải được bật trên version hỗ trợ. |
| “Cả Spring 6.1 đã có `setRejectTasksWhenLimitReached`.” | API chọn reject được thêm từ 6.2.6. |
| “@Async sẽ chạy virtual bất kể cách gọi.” | Cần proxy, đúng executor bean và routing. |
| “Virtual thread kế thừa transaction của caller.” | Imperative transaction gắn với thread/context, không tự chuyển. |
| “ThreadLocal hoàn toàn không dùng được với virtual thread.” | Dùng được, nhưng dễ tạo chi phí mỗi task lớn. |
| “Mọi synchronized I/O trên JDK24 đều pin.” | JEP 491 đã thay đổi đáng kể monitor-related pinning. |
| “Cancel future đồng nghĩa remote chưa thực hiện gì.” | Remote side effect có thể đã commit dù caller hết hạn/hủy. |
| “Virtual thread tự giúp `@Async void` truyền exception về caller.” | Hợp đồng xử lý lỗi `void` và `Future` không đổi. |
| “Executor lưu job qua restart.” | `SimpleAsyncTaskExecutor` không phải queue durable. |

## 24. Checklist code review trước khi triển khai

- Spring Framework version hỗ trợ `setVirtualThreads(true)`, runtime JDK 21+; build/test đúng Java 21 hoặc cao hơn.
- `SimpleAsyncTaskExecutor` đã bật `setVirtualThreads(true)`, và `@Async` chọn đúng bean executor.
- Có integration test với `Thread.currentThread().isVirtual()` ở **trong task**, không chỉ ở caller.
- Không nhầm `SimpleAsyncTaskExecutor` với `ThreadPoolTaskExecutor` hoặc `SimpleAsyncTaskScheduler`.
- Tính concurrency limit và chính sách reject/block được quyết định theo SLO; nhớ `setRejectTasksWhenLimitReached` yêu cầu Spring 6.2.6+.
- Có backpressure/rate limiter và giới hạn kết nối cho DB, HTTP, broker; không cố tạo virtual threads vô hạn.
- Task có timeout I/O, deadline, retry budget và idempotency phù hợp.
- `@Async` được gọi qua Spring proxy, tránh self-invocation và dùng `CompletableFuture` khi caller cần trạng thái.
- SecurityContext, MDC/tracing và transaction được xử lý rõ; không tin ThreadLocal tự được sao chép.
- Đã đánh giá các thư viện cache lớn trong ThreadLocal và nguy cơ allocation/GC.
- Đã kiểm tra pinning **theo đúng JDK version**, không dùng giả định Java21 cho JDK24+.
- Có chính sách shutdown, chờ/hủy/khôi phục task in-flight; không coi virtual thread là durable job.
- Có benchmark throughput, latency và resource utilization, không chỉ `isVirtual=true`.
- Đối với tác vụ quan trọng, có trạng thái bền vững, cơ chế nhận lại job sau crash và chống duplicate.

**Kết luận:** `SimpleAsyncTaskExecutor.setVirtualThreads(true)` là cách rõ ràng trong Spring Framework để **tạo virtual thread cho mỗi task** trên Java 21+. Có thể dùng executor trực tiếp hoặc nối vào `@Async` qua bean/qualifier; kiểm chứng bằng `Thread.currentThread().isVirtual()` ngay trong worker. Virtual threads giúp scale các công việc I/O blocking, nhưng **không** thay thế concurrency limits, backpressure, transaction/context propagation, timeout, error handling hay durable job processing.

## Tài liệu tham khảo chính thức

- [Spring Framework – SimpleAsyncTaskExecutor Javadoc (6.2.9)](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/task/SimpleAsyncTaskExecutor.html): `setVirtualThreads`, `setConcurrencyLimit`, `setRejectTasksWhenLimitReached`, `setTaskTerminationTimeout` và `submit`.
- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): sự khác nhau giữa `SimpleAsyncTaskExecutor`, `ThreadPoolTaskExecutor`, cơ chế `@Async` và cấu hình executor.
- [Spring Framework – @Async Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/annotation/Async.html): return types, qualifier và advice.
- [Spring Framework – @EnableAsync Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html): bật interception, lựa chọn executor và proxy mode.
- [Spring Boot 3.5 – Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html): điều kiện auto-config của `spring.threads.virtual.enabled`.
- [Oracle Java 21 – Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html): kiến trúc virtual/carrier threads, thread-per-task, semaphore, ThreadLocal và profiling.
- [OpenJDK – JEP 444: Virtual Threads](https://openjdk.org/jeps/444): virtual threads là tính năng chuẩn từ Java 21.
- [OpenJDK – JEP 491: Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491): thay đổi monitor-related pinning trong JDK 24.
- [Java SE 21 – Thread.isVirtual](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html): nhận diện một virtual thread tại runtime.
- [Java SE 21 – Semaphore](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Semaphore.html): giới hạn concurrency đối với tài nguyên bên dưới.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-004">SimpleAsyncTaskScheduler và cách thực thi tương thích luồng ảo</a>

<details>
<summary>Xem chi tiết</summary>

# SimpleAsyncTaskScheduler và cách thực thi tương thích luồng ảo

## 1. Bối cảnh: từ pool worker sang một thread cho mỗi lượt

Trong ứng dụng Spring, những tác vụ định kỳ như đồng bộ API đối tác, quét đơn hàng đến hạn, xử lý email hay làm mới cache thường được gọi qua `@Scheduled` hoặc `TaskScheduler`. Một scheduler kiểu truyền thống như `ThreadPoolTaskScheduler` dùng một số **platform threads** cố định để thực thi nhiệm vụ. Khi job làm I/O blocking, worker của pool có thể bị giữ cho tới khi I/O xong.

Từ **Spring Framework 6.1**, Spring cung cấp `org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler`. Thiết kế này gắn với khả năng **virtual threads** của **Java 21+**:

- Có **một scheduler thread** chịu trách nhiệm xác định thời điểm các lần chạy.
- Với lịch **fixed-rate**, **cron**, và các lần kích hoạt phù hợp khác, scheduler thường **bàn giao từng lượt thực thi** cho một thread riêng, thay vì giữ một pool worker cố định.
- Khi bật `setVirtualThreads(true)` trên JDK 21+, các thread được tạo để thực thi lượt công việc theo mô hình thread-per-task có thể là **virtual threads** nhẹ.
- **Ngoại lệ rất quan trọng:** lịch **fixed-delay** được thực thi **ngay trên một scheduler thread chung** để giữ semantics “kết thúc lần trước rồi nghỉ”. Vì vậy công việc blocking fixed-delay có thể làm nghẽn cả scheduler.
- Một `ScheduledFuture<?>` ở cơ chế bàn giao có thể biểu diễn **việc đến hạn và hand-off**, **không phải** bằng chứng công việc nghiệp vụ trên thread mới đã hoàn tất.

**Mấu chốt:** `SimpleAsyncTaskScheduler` **không phải “ThreadPoolTaskScheduler dùng nhiều virtual threads hơn”**. Đây là mô hình **một bộ điều phối thời gian + thread mới cho mỗi lượt** (ngoại trừ fixed delay). Thread mới có thể là platform thread nếu chưa bật virtual-thread mode; dùng nó đúng đòi hỏi hiểu trigger, concurrency, lifecycle và tài nguyên phía sau.

### 1.1. Ví dụ trực quan

~~~text
                         SimpleAsyncTaskScheduler
                           [1 scheduler thread]
                                  |
            cron / fixed rate tới hạn?
                      |
                      +--> hand-off run A --> virtual thread A --> HTTP API
                      |
                      +--> hand-off run B --> virtual thread B --> DB query
                      |
                      +--> hand-off run C --> virtual thread C --> ghi kết quả

Nhưng fixed delay:
      [scheduler thread] -- run D trực tiếp -- chờ D kết thúc -- nghỉ delay
                  |
                  +-- các fixed-delay registration khác cũng đợi thread này
~~~

Một virtual thread không phải một CPU core riêng. JVM sắp lịch nhiều virtual threads lên một tập nhỏ platform carrier threads; khi blocking operation tương thích có thể unmount virtual thread, nhường carrier cho việc khác. Điều này đặc biệt hữu ích cho **nhiều thao tác I/O blocking độc lập** nhưng không biến CPU-bound computation thành chạy nhanh hơn số lõi vật lý.

## 2. Điều kiện phiên bản và khái niệm đúng

| Thành phần | Điều kiện | Ghi nhớ |
| --- | --- | --- |
| `SimpleAsyncTaskScheduler` | Spring Framework **6.1+** | Cung cấp implementation mới của `TaskScheduler` |
| `setVirtualThreads(true)` | JDK **21+** | Bật virtual threads; mặc định là `false` nếu tự tạo scheduler |
| Spring Boot tự cấu hình virtual scheduler | Boot 3.2+ với JDK 21+ và `spring.threads.virtual.enabled=true`, khi điều kiện auto-config thỏa | Có thể tạo `SimpleAsyncTaskScheduler` thay `ThreadPoolTaskScheduler` |
| Java 17 | Spring 6.1 vẫn chạy được trên Java 17 | `SimpleAsyncTaskScheduler` có thể dùng **platform threads**, không có virtual threads JDK |
| `ThreadPoolTaskScheduler` | Spring truyền thống | Dùng pool platform threads, lượt task chạy trên worker pool |
| Virtual threads JDK | `Thread.ofVirtual()` / `Executors.newVirtualThreadPerTaskExecutor()` từ Java 21 | API JDK, **khác** Spring scheduler abstraction |

Việc framework hỗ trợ virtual threads **không có nghĩa** một Spring Boot app chạy Java 17 trở thành ứng dụng virtual-thread chỉ bằng property. Cần xác minh **JDK thực sự dùng lúc chạy**, Spring version, auto-config và scheduler bean đang được chọn.

### 2.1. Phân biệt scheduler và executor

`TaskScheduler` quyết định **khi nào** khởi động một lượt công việc. `TaskExecutor`/`Executor` quyết định **thread nào** chạy việc được gửi. `SimpleAsyncTaskScheduler` vừa triển khai interface lập lịch vừa kế thừa khả năng thực thi task của `SimpleAsyncTaskExecutor`; tuy vậy:

- Có một thread xử lý trigger/timer.
- Với fixed-rate/cron, có thể tạo thread mới mỗi execution.
- Với fixed-delay, chạy trực tiếp trên scheduler thread.
- Nó không có `corePoolSize`, `maxPoolSize`, `queueCapacity` giống `ThreadPoolTaskScheduler`.
- Nó không tự cấp quyền, giới hạn connection DB, định nghĩa job retry hoặc ngăn xử lý trùng ở nhiều pod.

Vì tên gọi có chữ “Async”, nhiều người nhầm rằng **mọi** đường scheduling đều được offload. Điều đó sai ở fixed-delay; phải đọc contract của từng trigger.

## 3. Các API liên quan trong Spring

`SimpleAsyncTaskScheduler` là một implementation của `TaskScheduler` nên dùng được các phương thức quen thuộc:

~~~java
// Chữ ký rút gọn để so sánh; không phải chương trình độc lập.
ScheduledFuture<?> schedule(Runnable task, Instant startTime);

ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Duration period);

ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Instant startTime, Duration period);

ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Duration delay);

ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Instant startTime, Duration delay);

ScheduledFuture<?> schedule(
        Runnable task, Trigger trigger);
~~~

`Duration` và `Instant` thuộc `java.time`; `ScheduledFuture<?>` thuộc `java.util.concurrent`; `Trigger` là Spring scheduling abstraction. `schedule(task, new CronTrigger(...))` là cách programmatic để đăng ký cron.

Các thao tác cốt lõi trên implementation:

~~~java
// Snippet trong @Configuration trên Java 21+.
SimpleAsyncTaskScheduler scheduler =
        new SimpleAsyncTaskScheduler();

scheduler.setVirtualThreads(true);   // Java 21+
scheduler.setThreadNamePrefix("sync-vt-");
scheduler.setConcurrencyLimit(16);   // Giới hạn số lượt song song
~~~

`setVirtualThreads(true)` là cấu hình của lớp cơ sở `SimpleAsyncTaskExecutor`; nó không thay thế lifecycle Spring bean. `setConcurrencyLimit(16)` không phải số thread trong pool hay số CPU được tăng: nó giới hạn số lượt đồng thời đi qua concurrency throttle. Khi đến giới hạn, chính sách ở phiên bản tương ứng có thể khiến nơi dispatch phải chờ; **đừng mặc định** sẽ có một queue bền vững hay trả 429 cho HTTP caller.

**Cảnh báo khi copy code:** nếu deploy trên JDK 17, dòng `setVirtualThreads(true)` không tạo được virtual threads. Trên JDK 17, giữ chế độ platform threads hoặc dùng `ThreadPoolTaskScheduler`; muốn virtual threads phải nâng runtime lên Java 21+.

## 4. Một scheduler thread, nhiều execution threads

Tưởng tượng ba lịch riêng:

~~~java
// Snippet trong service có sẵn bean TaskScheduler.
scheduler.scheduleAtFixedRate(
        this::refreshExchangeRates,
        Duration.ofSeconds(5));

scheduler.scheduleAtFixedRate(
        this::pollSupplierStock,
        Duration.ofSeconds(5));

scheduler.schedule(
        this::sendDailyDigest,
        new CronTrigger("0 0 8 * * MON-FRI"));
~~~

Ba callback là method no-arg trong bean. Đây là **ba registrations khác nhau** và có thể đủ điều kiện kích hoạt gần cùng lúc. Với `SimpleAsyncTaskScheduler` bật virtual threads, scheduler có thể bàn giao mỗi invocation cho một virtual thread riêng. Do đó một job đang đợi HTTP không nhất thiết chặn những jobs khác chỉ vì scheduler có **một timer thread**.

### 4.1. Thread-per-task không phải thread pool

| `ThreadPoolTaskScheduler` | `SimpleAsyncTaskScheduler` |
| --- | --- |
| Một pool scheduler/worker cố định, thường platform threads | Một scheduler thread + thread mới cho mỗi lượt được hand-off |
| Số worker tăng qua `poolSize` | Không cấu hình `poolSize` theo kiểu pool |
| Worker pool có thể bị chiếm bởi I/O blocking | Virtual thread của một lượt blocking có thể nhường carrier |
| Fixed-delay task chạy trên pool worker và tuần tự trong registration | Fixed-delay chạy **trên scheduler thread chung** |
| `ScheduledFuture` thường gắn với chuỗi execution do pool quản lý | Với hand-off, Future có thể phản ánh dispatch chứ không phải business completion |
| Hạn chế song song bằng pool size/queue | Giới hạn rõ qua `setConcurrencyLimit` hoặc bulkhead khác |
| Hợp tác với `@Scheduled` và `TaskScheduler` | Cũng hợp tác với `@Scheduled` và `TaskScheduler` |

Không chọn loại scheduler chỉ vì “thread count nhỏ hơn” hoặc “ảo là nhanh hơn”. Xác định workload là CPU-bound hay I/O-bound, có cần nghỉ **sau khi hoàn tất thực sự** hay không, và có cần kiểm soát số request đang in-flight không.

### 4.2. Khả năng overlap của cùng một registration

Với fixed rate, giả sử mỗi lần chạy mất 7 giây nhưng period là 2 giây:

~~~text
scheduled t=0 --> giao cho virtual thread A --> kết thúc t~7
scheduled t=2 --> giao cho virtual thread B --> kết thúc t~9
scheduled t=4 --> giao cho virtual thread C --> kết thúc t~11
~~~

**Có thể có nhiều invocation đồng thời của cùng một đăng ký fixed-rate**, vì scheduler thread chỉ hand-off. Điều này khác chuỗi periodic task trên `ScheduledThreadPoolExecutor` có cơ chế không overlap các execution của cùng recurring command.

Với cron, các mốc lịch khác nhau cũng có thể dispatch trong khi job cũ vẫn chạy. Một thao tác cập nhật kho hàng hoặc chạy migration **không được** giả định rằng `@Scheduled(fixedRate=...)` mang lại mutex.

### 4.3. Vì sao không thể dùng “pool size = 1” để chữa overlap?

`SimpleAsyncTaskScheduler` không lấy pool size làm giới hạn mặc định cho execution threads, và Spring Boot có thể **bỏ qua** `spring.task.scheduling.pool.size` khi auto-config virtual scheduler. Muốn giới hạn song song, dùng `setConcurrencyLimit` hoặc semaphore/bulkhead theo workload; muốn mỗi job không overlap có thể dùng `AtomicBoolean` per job, lease/DB claim hoặc scheduler khác.

Nhớ rằng giới hạn global bằng 1 cũng có thể gây nghẽn toàn bộ các registrations không liên quan. Chia workload theo nhóm tài nguyên (partner API, DB, CPU reporting) thường tốt hơn.

## 5. Ngoại lệ rất quan trọng: fixed-delay trên scheduler thread

Đây là điểm dễ thiết kế sai nhất.

`SimpleAsyncTaskScheduler.scheduleWithFixedDelay(...)` giữ ý nghĩa “lượt trước hoàn tất rồi mới đợi delay”, nhưng để làm được vậy, Spring **thực thi fixed-delay task ngay trên một scheduler thread**, không tạo thread mới cho mỗi lượt.

~~~text
SimpleAsyncTaskScheduler: 1 scheduler thread

Job A: fixedDelay = 5 s
    start A -- blocking HTTP 12 s -- end A -- đợi 5 s -- next A

Job B: fixedDelay = 1 s
    Job B cũng phải dùng scheduler thread đó.
    Khi A đang blocking, B chưa thể được chạy trên scheduler thread.

Job C: fixedRate
    Những trigger khác có thể bị trễ do scheduler thread đang bận.
~~~

Do đó, với mô hình virtual-thread-aligned này, **tài liệu Spring khuyên dùng fixed-rate hoặc cron thay vì fixed-delay**. Điều này không có nghĩa `fixedDelay` bị cấm, mà là nó không phù hợp với yêu cầu nhiều tác vụ blocking độc lập và cần lịch được kích hoạt đều.

### 5.1. Hai fixed-delay registrations có thể chặn lẫn nhau

~~~java
// Anti-pattern nếu cả hai gọi API blocking lâu trên SimpleAsyncTaskScheduler.
@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
public void partnerA() {
    slowPartnerA.fetch();
}

@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
public void partnerB() {
    slowPartnerB.fetch();
}
~~~

Đây là snippet trong Spring-managed bean. Cả hai là fixed-delay tasks; với **SimpleAsyncTaskScheduler chung**, khi A đang blocking trên scheduler thread, B không thể đồng thời dùng scheduler thread đó. Nếu B bị trễ, nguyên nhân không nhất thiết là pool config sai; đó là thiết kế runtime của loại scheduler.

Nếu nghiệp vụ thực sự cần **nghỉ X giây sau khi I/O kết thúc**, cân nhắc:

1. Dùng `ThreadPoolTaskScheduler` với pool size đủ và fixedDelay; mỗi registration hoàn tất trên worker pool.
2. Hoặc thiết kế một job controller có state/lease, đưa công việc lên virtual thread riêng rồi **reschedule sau completion** bằng contract rõ; không chỉ dispatch và return.
3. Nếu chấp nhận lịch theo nhịp cố định và có cơ chế chống overlap khác, dùng fixedRate/cron với `SimpleAsyncTaskScheduler`.

### 5.2. Cẩn thận với một Runnable chỉ submit việc khác

~~~java
// Sai nếu muốn delay tính từ lúc report đã làm xong:
scheduler.scheduleWithFixedDelay(() -> {
    reportExecutor.execute(() -> buildReport());
}, Duration.ofSeconds(5));
~~~

Ở đây scheduler thấy Runnable **kết thúc sau khi gửi việc**, không phải sau khi `buildReport()` hoàn tất. Khoảng nghỉ 5s được tính từ **dispatch completion**, có thể chồng lấp business reports. Dùng virtual thread không làm Spring tự biết vòng đời một công việc đã được chuyển sang executor khác.

Nếu đợi bằng `future.join()` ngay trên scheduler thread để “giữ fixedDelay”, tác vụ I/O bị chờ đó lại có thể khiến mọi fixed-delay job khác bị kẹt. Chọn abstraction phù hợp từ đầu thường tốt hơn giải pháp blocking chắp vá.

## 6. Spring Boot tự cấu hình virtual scheduler

Spring Boot 3.2+ có thể auto-config scheduler theo JDK và property. Với **Java 21+**, trong một ứng dụng bật scheduling annotation:

~~~properties
# Java 21+; Spring Boot 3.2+/3.3+, auto-config khi không có bean ghi đè
spring.threads.virtual.enabled=true

# Virtual threads là daemon; quan trọng cho ứng dụng chỉ có scheduled jobs.
spring.main.keep-alive=true
~~~

Và trong cấu hình:

~~~java
// Snippet: main Spring Boot app, không cần @EnableAsync cho scheduling.
@SpringBootApplication
@EnableScheduling
public class WorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
~~~

Khi điều kiện auto-configuration thỏa, Boot chọn `SimpleAsyncTaskScheduler` dùng virtual threads cho scheduling thay vì `ThreadPoolTaskScheduler`. Với cấu hình này, không nên kỳ vọng:

~~~properties
# Có thể bị bỏ qua trong chế độ virtual scheduler auto-config:
spring.task.scheduling.pool.size=8
~~~

Lý do: implementation mới **không dùng pool worker theo kiểu cũ**. Đây là một khác biệt của **configuration provided by application** và **default**: nếu app tự tạo `@Bean TaskScheduler`, bean đó có thể khiến auto-config rút lui. Đừng kiểm tra một property và suy ra scheduler thực tế; hãy kiểm tra loại bean trong ApplicationContext.

### 6.1. Tại sao cần keep-alive?

Virtual threads là **daemon threads**. Khi ứng dụng không còn platform non-daemon threads giữ JVM sống (ví dụ worker command-line chỉ có các scheduled jobs), JVM có thể thoát mặc dù còn các tác vụ lập lịch. `spring.main.keep-alive=true` trong Spring Boot giúp ngăn process kết thúc ngoài ý muốn trong tình huống đó.

Một web application có server/platform threads giữ process sống có thể không gặp cùng triệu chứng, nhưng không nên mặc định mọi deployment đều có. Khi triển khai worker-only với virtual scheduler, hãy test **process lifetime** chứ không chỉ xem log khởi động.

### 6.2. Phân biệt các namespace cấu hình

| Property/bean | Vai trò |
| --- | --- |
| `spring.threads.virtual.enabled` | Yêu cầu Boot dùng virtual threads ở các chỗ hỗ trợ, khi Java runtime đủ điều kiện |
| `spring.task.scheduling.pool.size` | Cỡ pool của `ThreadPoolTaskScheduler`; không có ý nghĩa tương đương với SimpleAsyncTaskScheduler |
| `spring.task.scheduling.thread-name-prefix` | Tên thread do auto-config hỗ trợ; không biến platform thread thành virtual thread |
| `spring.task.execution.*` | Thường dành cho executor xử lý tác vụ, **khác** namespace scheduler |
| `@Bean SimpleAsyncTaskScheduler` | Ứng dụng tự chỉ định implementation/config |
| `@Scheduled(scheduler = "beanName")` | Chọn scheduler bean theo qualifier trong Spring 6.1+ |

Không dùng `spring.task.execution.pool.max-size` để suy ra số lượng virtual threads dành cho `@Scheduled`. Với ứng dụng có cả `@Async` và `@Scheduled`, có thể cần các bean/giới hạn riêng để hai luồng công việc không tranh nhau.

## 7. Tự cấu hình SimpleAsyncTaskScheduler có kiểm soát

Với JDK 21+:

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

@Configuration
public class VirtualSchedulerConfiguration {
    @Bean("virtualScheduler")
    SimpleAsyncTaskScheduler virtualScheduler() {
        SimpleAsyncTaskScheduler scheduler =
                new SimpleAsyncTaskScheduler();
        scheduler.setVirtualThreads(true);
        scheduler.setThreadNamePrefix("schedule-vt-");
        scheduler.setConcurrencyLimit(24);
        return scheduler;
    }
}
~~~

Đây là một class Spring configuration hoàn chỉnh, nhưng cần nằm trong một ứng dụng Spring có JDK21+ và classpath của Spring 6.1+. `setConcurrencyLimit(24)` là con số minh họa cho cấu hình của **ứng dụng**, không phải default framework. Giới hạn thích hợp phụ thuộc quota API/DB, RAM, network và thời gian I/O; không sao chép máy móc.

Một job được gắn với bean cụ thể:

~~~java
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StockPollingJob {
    private final StockService service;

    public StockPollingJob(StockService service) {
        this.service = service;
    }

    @Scheduled(
            fixedRate = 10,
            timeUnit = TimeUnit.SECONDS,
            scheduler = "virtualScheduler")
    public void poll() {
        service.fetchAndPersist();
    }
}
~~~

`StockService` là bean nghiệp vụ giả định, không có trong ví dụ demo độc lập ở mục 9. `scheduler` là attribute của `@Scheduled` có từ Spring 6.1, **không phải** tham số của method `poll()`. Phương thức scheduled vẫn phải không nhận đối số.

Nếu có nhiều scheduler beans, chỉ rõ bean name/qualifier giúp tránh không biết job đang chạy trên executor nào. Có thể chọn một `ThreadPoolTaskScheduler` riêng cho CPU-heavy hoặc fixed-delay job, và một `SimpleAsyncTaskScheduler` cho blocking I/O nhẹ theo cron/fixed-rate.

## 8. Java 21 virtual threads: lợi ích và giới hạn

Virtual thread là Java `Thread` thực sự, có call stack, interruption, `ThreadLocal` và hoạt động với nhiều API blocking quen thuộc. Virtual thread **nhẹ hơn về chi phí giữ một thread đang đợi**, nhưng không làm giảm số lượng connection DB/HTTP hoặc số request mà đối tác có thể xử lý.

### 8.1. Có lợi với I/O-bound nhiều tác vụ độc lập

Ví dụ có 500 schedules/API requests đang đợi network. Platform thread pool 16 worker có thể bị bận chờ, khiến những yêu cầu khác phải xếp hàng. Virtual threads cho phép nhiều task có trạng thái “đang đợi I/O” đồng thời với chi phí thường thấp hơn. Tuy nhiên, nếu DB chỉ có 20 connections, 500 tasks vẫn phải cạnh tranh **20 connection slots** và có thể timeout nếu không giới hạn.

### 8.2. Không giúp CPU-bound chạy nhanh hơn số lõi

Nếu mỗi job làm tính toán hash/hình ảnh nặng liên tục, 500 virtual threads có thể chỉ gây scheduler overhead. Với CPU-bound workload, pool platform threads gần số CPU hiệu dụng, batch, queue và concurrency limit rõ thường dễ kiểm soát hơn.

### 8.3. Virtual threads vẫn có vấn đề pinning/giữ tài nguyên

Trong JDK 21–23, blocking trong một số vùng `synchronized` hoặc native/foreign calls có thể làm virtual thread bị **pin** vào carrier thread. Java/JDK mới hơn có cải thiện, nên không đánh đồng tất cả phiên bản JDK. Với Java 21, có thể dùng JFR event `jdk.VirtualThreadPinned` hoặc chẩn đoán JVM thích hợp để tìm pinning thực tế; không mặc định thấy `Thread.currentThread().isVirtual()==true` là app không còn bottleneck.

Nên đo **p95/p99 latency, throughput, CPU, heap, pinned events, DB active connections, requests inflight, timeouts, queue/backpressure**, chứ không chỉ đếm virtual threads đang chạy. Dùng context propagation (MDC/security) phù hợp, không lấy lại stale context từ platform thread trước hay tin rằng Spring tự truyền HTTP request context vào scheduled worker.

### 8.4. Mỗi virtual thread vẫn cần interruption, timeout, cleanup

`cancel(true)` và `Thread.interrupt()` là yêu cầu hủy, không bảo đảm một JDBC/HTTP call đã commit tự rollback. Với job tác động DB hoặc thanh toán, cần I/O timeout thật, idempotency key, checkpoint và reconciliation khi app restart.

Virtual threads giảm chi phí **thread đang chờ**, không giải quyết exactly-once, distributed locks, retry storm hay tài nguyên đối tác bị bão hòa.

## 9. Demo Spring Boot 3.3.5 + Java 21 chứng minh hai đường thực thi

Dự án này dùng trực tiếp `SimpleAsyncTaskScheduler` với `setVirtualThreads(true)` và hai phép thử:

1. **Fixed rate:** giữ lượt 1 đang chờ bằng `CountDownLatch`; lượt 2 vẫn bắt đầu trên **virtual thread khác**, chứng minh có thể overlap.
2. **Fixed delay:** hai registrations A/B dùng **cùng scheduler thread**; giữ A chưa kết thúc thì B **chưa bắt đầu**, rồi giải phóng A thì B chạy.

Nhờ latches, không cần đo thời gian chính xác hay dựa vào `Thread.sleep` để ra stdout. Toàn bộ ví dụ chạy trên **JDK 21+**; không dùng API này trên Java 17 mà kỳ vọng `isVirtual()` có sẵn.

### 9.1. Cấu trúc

~~~text
simple-virtual-scheduler/
├── pom.xml
└── src/main/java/com/example/vtscheduler/
    └── VirtualSchedulerApplication.java
~~~

### 9.2. pom.xml

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
    <artifactId>simple-virtual-scheduler</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>21</java.version>
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

### 9.3. VirtualSchedulerApplication.java

~~~java
package com.example.vtscheduler;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

@SpringBootApplication
public class VirtualSchedulerApplication {
    public static void main(String[] args) {
        try (ConfigurableApplicationContext context =
                SpringApplication.run(
                        VirtualSchedulerApplication.class, args)) {
            // Context đóng sau khi ApplicationRunner hoàn tất.
        }
    }

    @Bean("virtualScheduler")
    SimpleAsyncTaskScheduler virtualScheduler() {
        SimpleAsyncTaskScheduler scheduler =
                new SimpleAsyncTaskScheduler();
        scheduler.setVirtualThreads(true);
        scheduler.setThreadNamePrefix("example-vt-");
        scheduler.setConcurrencyLimit(4);
        return scheduler;
    }

    @Bean
    ApplicationRunner verify(
            @Qualifier("virtualScheduler") TaskScheduler scheduler) {
        return args -> {
            testFixedRate(scheduler);
            testFixedDelay(scheduler);
        };
    }

    private static void await(CountDownLatch latch, String name)
            throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "Timed out waiting for " + name);
        }
    }

    private static void testFixedRate(TaskScheduler scheduler)
            throws Exception {
        AtomicInteger count = new AtomicInteger();
        AtomicReference<Thread> firstThread = new AtomicReference<>();
        AtomicReference<Thread> secondThread = new AtomicReference<>();

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstFinished = new CountDownLatch(1);

        ScheduledFuture<?> handle = scheduler.scheduleAtFixedRate(
                () -> {
                    int n = count.incrementAndGet();
                    if (n == 1) {
                        firstThread.set(Thread.currentThread());
                        firstStarted.countDown();
                        try {
                            releaseFirst.await();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        } finally {
                            firstFinished.countDown();
                        }
                    } else if (n == 2) {
                        secondThread.set(Thread.currentThread());
                        secondStarted.countDown();
                    }
                },
                Duration.ofMillis(200));

        try {
            await(firstStarted, "first fixed-rate tick");
            await(secondStarted, "second fixed-rate tick");

            System.out.println("RATE_FIRST_VIRTUAL="
                    + firstThread.get().isVirtual());
            System.out.println("RATE_SECOND_VIRTUAL="
                    + secondThread.get().isVirtual());
            System.out.println("RATE_DISTINCT_THREADS="
                    + (firstThread.get() != secondThread.get()));
            System.out.println("RATE_SECOND_OVERLAPS_FIRST="
                    + (firstFinished.getCount() == 1));
        } finally {
            handle.cancel(false);
            releaseFirst.countDown();
            await(firstFinished, "first fixed-rate completion");
        }
    }

    private static void testFixedDelay(TaskScheduler scheduler)
            throws Exception {
        AtomicReference<Thread> firstThread = new AtomicReference<>();
        AtomicReference<Thread> secondThread = new AtomicReference<>();

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstFinished = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        ScheduledFuture<?> firstHandle =
                scheduler.scheduleWithFixedDelay(() -> {
                    firstThread.compareAndSet(
                            null, Thread.currentThread());
                    firstStarted.countDown();
                    try {
                        releaseFirst.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        firstFinished.countDown();
                    }
                }, Duration.ofDays(1));

        ScheduledFuture<?> secondHandle = null;
        try {
            await(firstStarted, "first fixed-delay tick");

            secondHandle = scheduler.scheduleWithFixedDelay(
                    () -> {
                        secondThread.compareAndSet(
                                null, Thread.currentThread());
                        secondStarted.countDown();
                    }, Duration.ofDays(1));

            // B đã đăng ký nhưng A vẫn giữ scheduler thread.
            System.out.println("DELAY_B_DURING_A="
                    + secondStarted.await(150, TimeUnit.MILLISECONDS));

            releaseFirst.countDown();
            await(firstFinished, "first fixed-delay completion");
            await(secondStarted, "second fixed-delay tick");

            System.out.println("DELAY_SAME_SCHEDULER_THREAD="
                    + (firstThread.get() == secondThread.get()));
        } finally {
            releaseFirst.countDown();
            firstHandle.cancel(false);
            if (secondHandle != null) {
                secondHandle.cancel(false);
            }
        }
    }
}
~~~

**Chi tiết về bài kiểm chứng:**

- Mỗi `test...` giữ `ScheduledFuture<?>` và gọi `cancel(false)` sau khi đã quan sát đủ. Không gọi `get()` trên recurring future vì nó không trả kết quả từng tick như một Future bình thường.
- `testFixedRate` tạo hai lời gọi trên hai virtual threads. Lượt 1 vẫn chưa được giải phóng khi lượt 2 báo đã bắt đầu, nên code kiểm tra được overlap mà không cần đo thời gian chính xác.
- `testFixedDelay` đăng ký A trước và giữ A tại một latch. Sau đó B được đăng ký trên cùng scheduler. Trong khoảng A còn giữ scheduler thread, B chưa thể bắt đầu; sau khi thả A, B chạy trên **cùng thread identity** với A.
- `Duration.ofDays(1)` trong fixed-delay demo chỉ để tránh lần thứ hai của **mỗi registration** xuất hiện trước lúc test kết thúc. Đây không phải production polling interval.
- Không in tên thread vào expected stdout để tránh phụ thuộc thay đổi naming của runtime. `Thread.isVirtual()` có từ Java 21, nên các dòng kiểm chứng virtual thread không thể biên dịch trên Java 17.
- Bài test không chứng minh hiệu năng hay fairness dưới tải; nó kiểm tra **execution model** của đúng implementation Spring.

### 9.4. Chạy và stdout

Dùng JDK 21+, Maven, thư mục chứa `pom.xml`:

~~~bash
java -version
mvn spring-boot:run
~~~

Bỏ qua log Spring Boot/Maven, sáu dòng do chương trình in trong điều kiện các lượt chạy bình thường là:

~~~text
RATE_FIRST_VIRTUAL=true
RATE_SECOND_VIRTUAL=true
RATE_DISTINCT_THREADS=true
RATE_SECOND_OVERLAPS_FIRST=true
DELAY_B_DURING_A=false
DELAY_SAME_SCHEDULER_THREAD=true
~~~

Công thức kiểm chứng là từ **hai execution threads** của fixed-rate vs **một scheduler thread** của fixed-delay; không trông cậy timestamp tuyệt đối. Nếu môi trường cực kỳ chậm và một latch không được giải phóng trong 5 giây, chương trình fail rõ bằng timeout thay vì chờ vô hạn.

Với `SimpleAsyncTaskScheduler`, `setVirtualThreads(true)` có hiệu lực khi runtime Java 21+. Nếu bạn thấy `RATE_FIRST_VIRTUAL=false`, kiểm tra JDK, bean đang dùng, version framework và cấu hình virtual-thread; không cố chỉnh `poolSize` để chữa.

## 10. Lựa chọn khác: Java 17 và ThreadPoolTaskScheduler

Spring Framework 6.1 chạy trên Java 17, nhưng virtual-thread API của JDK không có ở Java 17. Hai lựa chọn hợp lệ:

1. **Dùng Java 17 + `ThreadPoolTaskScheduler`** với pool phù hợp khi còn chạy platform threads.
2. Dùng Java 17 + `SimpleAsyncTaskScheduler` **không bật virtual mode**, tức vẫn có một scheduler thread và thread mới kiểu platform cho các hand-off; đây có thể tốn tài nguyên hơn pool khi rất nhiều executions.

Ví dụ `ThreadPoolTaskScheduler`:

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// Snippet bên trong @Configuration; chạy với Java 17+.
@Bean("legacyScheduler")
ThreadPoolTaskScheduler legacyScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(4);
    scheduler.setThreadNamePrefix("legacy-poll-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    return scheduler;
}
~~~

Mẫu này thích hợp nếu ứng dụng cần fixedDelay không làm tất cả các jobs khác bị chặn trên một scheduler thread, hoặc nếu chưa nâng lên JDK 21. Từng recurring registration vẫn phải được kiểm thử với policy cụ thể; nhiều jobs cùng tranh pool có thể trễ và cần timeout.

**Không phải lựa chọn đúng:** cấu hình `spring.threads.virtual.enabled=true` trên Java 17 rồi ghi tài liệu rằng job đã chạy trên virtual threads. Tính năng có yêu cầu JDK version; xác nhận bằng `Thread.currentThread().isVirtual()` trên Java21+ hoặc quan sát task thread và bean thực tế.

## 11. Vòng đời, ScheduledFuture và shutdown

`SimpleAsyncTaskScheduler` thực hiện việc lập lịch/dispatch; theo Javadoc của Spring 6.1, với các tác vụ đã hand-off vào execution thread, `ScheduledFuture<?>` đại diện **thời điểm bàn giao** nhiều hơn là **thời điểm side effect hoàn tất**.

### 11.1. Khi Future đã done không chứng minh job đã xong

~~~java
// Snippet: thời điểm Instant đã được chọn.
ScheduledFuture<?> handle =
        scheduler.schedule(
                () -> slowExternalUpload(),
                Instant.now());
~~~

Có thể `handle.isDone()` trở thành `true` sau khi task đã được chuyển cho một thread chạy việc, trong khi `slowExternalUpload()` vẫn đang hoạt động. Đừng dùng `isDone()` để cập nhật “đã upload thành công” cho customer.

Nếu cần biết business completion, để chính task ghi `SUCCEEDED/FAILED` vào job ledger, dùng `CompletableFuture` tự quản, callback, semaphore/latch trong test hoặc transactional outbox. Tách **schedule accepted**, **execution started**, **business completed** thành các trạng thái khác nhau.

### 11.2. Hủy schedule và ngắt task

~~~java
// Snippet tại tầng quản lý job.
ScheduledFuture<?> handle =
        scheduler.scheduleAtFixedRate(
                this::pollRemote, Duration.ofSeconds(10));

boolean stopped = handle.cancel(false);
~~~

`cancel(false)` thường ngăn các lần kích hoạt mới. Nó không tự hủy các request đang chạy trên virtual threads do scheduler đã hand-off. `cancel(true)` cũng không được coi là transaction rollback hoặc bảo đảm task side effect đã dừng. Với việc quan trọng, thiết kế cancellation token, HTTP timeout, DB transaction và job state riêng.

### 11.3. Graceful shutdown và task tracking

Theo Spring 6.1, `SimpleAsyncTaskScheduler` có khả năng giới hạn concurrency và hỗ trợ chờ kết thúc các execution threads thông qua `setTaskTerminationTimeout(long)`, với **chi phí theo dõi task**. Nhưng lifecycle chỉ bảo đảm điều mà implementation được cấu hình để quản lý: việc dừng trigger **không tự chứng minh** tất cả công việc đã bàn giao hoàn tất và ghi dữ liệu an toàn.

Nếu ứng dụng cần graceful shutdown:

- Ngừng nhận job mới và ngừng firing triggers.
- Chờ có giới hạn cho các job in-flight được theo dõi/đăng ký đúng.
- Ghi checkpoint/error status khi job hoàn tất hoặc cần retry.
- Kiểm tra interruption, timeout và idempotency với external side effects.
- Khi pod bị kill không graceful, dùng DB/job ledger hoặc queue để phục hồi sau restart.

Không phụ thuộc vào việc JVM có vài virtual threads đang chạy để giữ process sống; với worker-only app, cân nhắc Boot `spring.main.keep-alive=true`.

## 12. Tài nguyên, giới hạn concurrency và overload

### 12.1. Virtual threads không miễn phí tuyệt đối

Một virtual thread nhỏ hơn platform thread, nhưng mỗi task vẫn có:

- Object graph, stack chunks và memory riêng.
- Một request tới downstream service và có thể sử dụng connection.
- Context logging/security và dữ liệu job.
- Rủi ro giữ quá nhiều Future/payload trong RAM.
- CPU/GC overhead nếu tạo số lượng cực lớn.

Nếu 10.000 jobs mỗi phút đụng DB pool chỉ có 20 connections, tăng virtual threads sẽ tăng số job đợi connection, không giúp throughput vượt khả năng DB.

### 12.2. Giới hạn theo tài nguyên downstream

~~~java
// Snippet: cấu hình chính sách ứng dụng, không phải default.
SimpleAsyncTaskScheduler scheduler =
        new SimpleAsyncTaskScheduler();

scheduler.setVirtualThreads(true);
scheduler.setConcurrencyLimit(32);
~~~

Global concurrency limit là một lớp bảo vệ, nhưng có thể làm chậm scheduler thread ở pha hand-off tùy implementation; không thay cho limit per partner, per tenant, DB pool hoặc message queue.

Một lựa chọn khác là:

~~~java
// Snippet quản lý quyền vào một API cụ thể.
Semaphore permits = new Semaphore(8);

Runnable poll = () -> {
    boolean acquired = permits.tryAcquire();
    if (!acquired) {
        skippedRunsCounter.increment();
        return;
    }
    try {
        partnerApi.poll();
    } finally {
        permits.release();
    }
};
~~~

`skippedRunsCounter` và `partnerApi` là dependencies ứng dụng; snippet không độc lập. Khi scheduler tới hạn mà đã có 8 lượt poll, code **bỏ lượt mới** theo một policy hiển thị, thay vì tăng load vô hạn. Nếu không được phép bỏ dữ liệu, dùng queue bền vững/cursor thay vì một flag skip đơn giản.

### 12.3. Overlap vs idempotency

Với fixedRate/cron, một job cũ có thể vẫn đang chạy khi job mới bắt đầu. Đối soát phải có idempotency key, DB unique constraint/claim, optimistic locking hoặc distributed lease. Trong cụm nhiều pod, mỗi pod tạo scheduler của mình; một semaphore chỉ giới hạn ở **một JVM**, không là distributed lock.

## 13. Giám sát và kiểm thử thực tế

### 13.1. Các tín hiệu nên thu thập

| Tín hiệu | Mục đích |
| --- | --- |
| `scheduler_implementation` / bean name | Xác nhận đang chạy SimpleAsyncTaskScheduler hay ThreadPoolTaskScheduler |
| `thread.isVirtual()` trong callback | Kiểm chứng loại execution thread, không chỉ xem property |
| `scheduled_dispatches_total` | Số lượt đã đến hạn/được bàn giao |
| `job_started_total` / `job_finished_total` | Tách dispatch khỏi business completion |
| `job_inflight` | Phát hiện quá nhiều executions overlap |
| `job_failed_total` / `job_timeout_total` | Theo dõi failure, không chỉ log |
| `job_duration_seconds` | Quan sát thời gian business work, không chỉ trigger time |
| `db_pool_active/pending` | Giới hạn thực tế ở downstream |
| `scheduler_lag` | Mốc dự kiến so với thời điểm bắt đầu thực |
| `virtual_thread_pinning` / JFR | Chẩn đoán khi carrier bị giữ do blocking không tương thích |

Với production, thêm alert khi job không thành công trong một khoảng SLA, khi inflight vượt giới hạn, khi fixedDelay job chiếm scheduler thread quá lâu hoặc DB pending connections tăng. Tránh gắn tenant/user ID không giới hạn vào metric labels vì cardinality.

### 13.2. Kiểm thử chức năng

1. Java runtime thật là 21+ và `@Scheduled` đang dùng bean `SimpleAsyncTaskScheduler` mong muốn?
2. Với fixedRate, một task bị giữ bằng latch có cho phép tick tiếp theo chạy trên **virtual thread khác**?
3. Với fixedDelay, hai registrations có dùng cùng scheduler thread và job B phải đợi khi A bị giữ?
4. Nếu dùng `ThreadPoolTaskScheduler` thay vào đó, fixedDelay jobs khác nhau có thể chạy trên hai worker khi pool đủ lớn không?
5. Nếu task ném exception, có log/metric và chính sách continuation đúng?
6. Nếu task chạy quá period, có bao nhiêu executions chồng lấp, có rate limiting hoặc lease không?
7. `ScheduledFuture.isDone()` có bị hiểu nhầm thành business success không?
8. Khi timeout/cancel/shutdown, side effect đã commit có bị retry trùng không?
9. Virtual thread có dùng đúng context/tenant/security đã xác thực không?
10. Khi bật Boot property, pool properties còn áp dụng không và process có giữ sống đúng không?

### 13.3. Kiểm thử tải và điều kiện dừng

Chạy test tải ở mức I/O latency và concurrency thực tế. So sánh platform pool vs virtual scheduler bằng **throughput, p95/p99, error rate, CPU, heap, DB pool waits và latency downstream**. Không dùng benchmark giả chỉ tăng số threads để kết luận “virtual threads nhanh gấp N lần”.

Test kill/restart pod khi một job đang ghi DB; xác nhận checkpoint và idempotency cho phép phục hồi. Với fixedRate, test một job lâu hơn period; với fixedDelay, test hai jobs riêng cạnh tranh scheduler thread. Bài demo dùng latch vì cần kết quả có tính quyết định, không dựa vào sleep ngẫu nhiên.

## 14. Các lỗi phổ biến và cách sửa

| Hiện tượng | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| Bật virtual threads nhưng vẫn thấy pool thread | Bean scheduler do app tự định nghĩa/auto-config đã rút lui; JDK chưa đủ | Kiểm tra actual bean, version JDK, property và `Thread.isVirtual()` |
| `setVirtualThreads(true)` không chạy trên JDK 17 | Virtual threads là API Java 21+ | Nâng JDK hoặc dùng platform scheduler |
| Hai fixedDelay jobs không chạy song song | Cùng dùng **một scheduler thread** trên SimpleAsyncTaskScheduler | Dùng ThreadPoolTaskScheduler hoặc thiết kế trigger/lease khác |
| Fixed-rate job overlap gây ghi DB trùng | Thread per execution, không có mutex per job | Idempotency/lease/bulkhead/concurrency limit |
| `spring.task.scheduling.pool.size=8` không tăng throughput | Virtual scheduler không dùng pool size kiểu cũ | Kiểm tra downstream limits, setConcurrencyLimit hoặc chọn pool scheduler |
| `ScheduledFuture.isDone()==true` nhưng upload chưa xong | Future biểu diễn hand-off | Job result/state riêng, không đánh dấu thành công sớm |
| Job mất sau restart | Lịch in-memory không bền vững | Job store/cursor/outbox/broker |
| Process worker-only thoát sớm | Virtual threads daemon | `spring.main.keep-alive=true` hoặc lifecycle quản lý rõ |
| Hàng nghìn virtual threads nhưng API vẫn chậm | Downstream connection/quota/latency mới là nút thắt | Limit per source, timeout, backpressure |
| CPU usage tăng, throughput không tăng | CPU-bound công việc không có đủ cores | Bounded platform pool, batch/algorithm optimization |
| MDC/security/tenant không đúng | Context không tự propagate sang thread mới | Truyền DTO xác thực/context propagation có scope |
| Exception bị bỏ qua | Chỉ theo dõi dispatch, không theo dõi business outcome | Callback/log/metric/job ledger |
| Tưởng `@EnableAsync` đủ cho `@Scheduled` | Hai infrastructure khác nhau | Bật `@EnableScheduling` nếu dùng annotation |
| Đổi config mà app vẫn dùng scheduler cũ | Bean/annotation được resolve khi context startup | Xác minh runtime, reschedule hoặc restart có chủ đích |

## 15. Checklist trước khi dùng trong production

- [ ] Đã xác minh Spring Framework ≥6.1 và **JDK 21+** cho virtual mode?
- [ ] Đã xác minh scheduler bean thực được chọn, không chỉ tên property?
- [ ] Đã phân loại job là fixedRate/cron hay fixedDelay, đo **business completion** đúng nghĩa?
- [ ] Không đặt nhiều fixedDelay blocking jobs chung một SimpleAsyncTaskScheduler mà kỳ vọng song song?
- [ ] Với fixedRate/cron, đã có giải pháp chống overlap/duplicate writes khi cần?
- [ ] Đã đặt giới hạn concurrency, HTTP/DB timeouts, retry có giới hạn và idempotency?
- [ ] Có observability cho dispatch, start, success, failure, inflight, queue lag, DB pool waits?
- [ ] Có xử lý graceful shutdown, cancellation và job state bền vững qua restart?
- [ ] Nếu worker-only application, đã kiểm tra daemon thread/keep-alive?
- [ ] Đã test context/security/tenant không bị dùng sai, và transaction chạy trên đúng worker?
- [ ] Đã kiểm tra behavior trên **đúng** version Spring Boot/JDK của production?
- [ ] Có test bằng latch để phân biệt fixed-rate overlap và fixed-delay serialization?

## 16. Câu hỏi tự kiểm tra

1. `SimpleAsyncTaskScheduler` dùng bao nhiêu scheduler threads để điều phối các mốc, và nó tạo execution threads như thế nào?
2. Trên Java 17, bật `setVirtualThreads(true)` có tạo được virtual threads không?
3. Vì sao `@Scheduled(fixedDelay=...)` có thể làm job khác bị chậm khi dùng SimpleAsyncTaskScheduler?
4. Một fixedRate job mỗi 2s nhưng mỗi lượt mất 8s có thể có bao nhiêu executions đang chạy chồng lấp?
5. `ThreadPoolTaskScheduler` và `SimpleAsyncTaskScheduler` khác nhau về pool size và ScheduledFuture completion ra sao?
6. Spring Boot property nào bật virtual-thread integration và điều kiện Java nào phải thỏa?
7. Vì sao cấu hình `spring.task.scheduling.pool.size` có thể bị bỏ qua trong virtual scheduler?
8. Tại sao `spring.main.keep-alive=true` có thể quan trọng cho một app chỉ gồm scheduled jobs?
9. Với `ScheduledFuture.isDone()`, tại sao không thể suy luận rằng HTTP upload/DB write đã thành công?
10. `setConcurrencyLimit(32)` có thay thế được DB pool capacity và distributed job lease không?
11. Một virtual thread chờ connection DB có làm tăng số connection available không?
12. Với task bị cancel sau khi side effect đã commit, phải làm gì để tránh xử lý trùng khi retry?
13. Vì sao không được đánh giá virtual-thread scalability bằng số thread tối đa mà không đo p95/throughput?
14. Nếu cần nghỉ đúng 10s **sau khi business hoàn tất**, nên dùng thiết kế nào thay vì chỉ hand-off một Future rồi trả về?
15. Khi Spring chạy nhiều pod, SimpleAsyncTaskScheduler có tự đảm bảo một job chạy một lần trên toàn cụm không?

**Kết luận:** `SimpleAsyncTaskScheduler` là scheduler **một thread điều phối thời gian + thread riêng cho mỗi execution được hand-off**, phù hợp với **Java 21 virtual threads** khi job I/O cần nhiều lượt độc lập. **Fixed-delay là ngoại lệ**: thực thi trên cùng một scheduler thread và có thể gây nghẽn. Với fixed-rate/cron, phải coi overlap là khả năng thật; với job quan trọng, cần concurrency limits, timeout, error handling, idempotency, durable status, và shutdown rõ ràng. Đừng nhầm “đã lên lịch/đã bàn giao” với “nghiệp vụ đã hoàn tất”.

## Tài liệu chính thức

- [Spring Framework 6.1 — SimpleAsyncTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.1.16/javadoc-api/org/springframework/scheduling/concurrent/SimpleAsyncTaskScheduler.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework 6.1 — @Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.1.16/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Boot 3.3 — Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.3/reference/features/task-execution-and-scheduling.html)
- [Spring Boot — Virtual Threads and keep-alive](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.virtual-threads)
- [Java 21 — Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
- [Java 21 — Thread.ofVirtual API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#ofVirtual())


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-005">Không tạo nhóm luồng ảo chỉ để mô phỏng nhóm luồng nền tảng</a>

<details>
<summary>Xem chi tiết</summary>

# Không tạo nhóm luồng ảo chỉ để mô phỏng nhóm luồng nền tảng

## 1. Nguyên tắc cốt lõi: virtual thread không phải platform thread thu nhỏ để đem đi pooling

Từ **Java 21 (JEP 444)**, virtual threads là tính năng chuẩn của Java. Chúng cho phép ứng dụng viết mã đồng bộ, dễ đọc theo mô hình *một tác vụ — một luồng*, trong khi JVM điều phối rất nhiều virtual threads trên một tập **carrier platform threads** nhỏ hơn.

Khi chuyển ứng dụng Spring Framework từ platform threads sang virtual threads, một sai lầm dễ mắc là thay thread factory nhưng giữ nguyên thiết kế fixed-size pool cũ:

```java
// Phản ví dụ: pool cố định gồm 8 worker là virtual threads.
ExecutorService wrong = Executors.newFixedThreadPool(
        8, Thread.ofVirtual().factory());
```

Đoạn code trên **có thể biên dịch ở JDK 21+**, nhưng nó không phải cách dùng virtual threads được khuyến nghị. Nó tạo tối đa tám worker virtual threads **được tái sử dụng**, khiến các tác vụ tiếp theo phải chờ worker rảnh, gần giống mô hình pool platform threads cũ. Việc đặt worker trên virtual thread không biến một kiến trúc pooling thành mô hình thread-per-task.

Cách tiếp cận phổ biến đúng:

```java
// JDK 21+: tạo một virtual thread MỚI cho mỗi task.
try (ExecutorService executor =
        Executors.newVirtualThreadPerTaskExecutor()) {
    Future<String> future =
            executor.submit(() -> "Kết quả");
    System.out.println(future.get());
}
```

Đây là **mảnh Java** cần import `java.util.concurrent.*` hoặc các lớp tương ứng. `newVirtualThreadPerTaskExecutor()` tạo executor có vòng đời quản lý được; **mỗi lần submit sinh một virtual thread mới**, không lấy một virtual thread cũ trong pool để tái sử dụng.

**Thông điệp của bài:** Đừng tạo *pool virtual threads* chỉ vì trước đây ứng dụng cần pool platform threads. Khi cần giới hạn tài nguyên hữu hạn, hãy **giới hạn tại tài nguyên thực sự bị giới hạn** bằng `Semaphore`, connection pool, rate limiter, bulkhead hay admission control; đừng dùng số worker virtual threads cố định làm công cụ duy nhất để kiểm soát tải.

Đây cũng là lời khuyên chính thức trong tài liệu Oracle về virtual threads: chúng được thiết kế để tạo mới cho từng tác vụ và không cần một nhóm thread được dựng sẵn.

## 2. Virtual thread và carrier platform thread là hai tầng khác nhau

Một **platform thread** của Java thường tương ứng với một thread của hệ điều hành. Tạo nhiều platform threads có chi phí hệ điều hành và stack đáng kể; chúng thường được giới hạn và tái sử dụng trong thread pool.

Một **virtual thread** vẫn là một `java.lang.Thread`, nhưng JVM có thể *mount* nó trên một **carrier thread** để chạy mã. Khi tác vụ chờ những thao tác blocking có khả năng unmount, JVM tạm ngưng virtual thread và trả carrier cho tác vụ khác.

```text
Tác vụ ứng dụng                JVM scheduler / OS
----------------------        ---------------------------
request-A -> virtual-A  --\
request-B -> virtual-B  ---->  carrier-1 (platform thread)
request-C -> virtual-C  --\    carrier-2 (platform thread)
request-D -> virtual-D  ---->  carrier-3 (...)
request-E -> virtual-E  --/

virtual-B chờ I/O
  -> được tạm ngưng và có thể unmount
  -> carrier được dùng cho virtual-D
  -> khi I/O sẵn sàng, virtual-B tiếp tục trên carrier phù hợp
```

Hai khái niệm không được đánh đồng:

- **Số virtual threads**: phản ánh số tác vụ đang tồn tại, bao gồm đang chạy và đang chờ.
- **Số carrier platform threads**: do JVM điều phối, liên quan mức song song CPU và các tác vụ đang cần carrier.

Ứng dụng không phải tạo một pool virtual threads có số worker bằng CPU để “quản lý carrier”. JVM đã đảm nhiệm phần điều phối virtual threads. Một virtual thread có thể tiếp tục trên carrier khác sau điểm blocking; **không dựa vào tên carrier hoặc thread identity của platform thread** để quản lý trạng thái nghiệp vụ.

### 2.1. Virtual threads không làm CPU chạy nhanh hơn

Oracle nhấn mạnh: virtual threads hướng đến **khả năng mở rộng và throughput của tác vụ chờ nhiều**, không tự làm một phép tính CPU hoàn tất nhanh hơn.

| Workload | Virtual thread có thể giúp? | Giới hạn thật |
|---|---|---|
| Nhiều request HTTP đồng thời thường chờ I/O | Có tiềm năng lớn | Connection pools, network, downstream |
| Nhiều JDBC calls đang chờ | Có thể giảm chi phí thread chờ | Số kết nối DB, DB capacity, locks |
| CPU-bound tính toán nặng | Không tự nhanh hơn | Số lõi CPU, thuật toán |
| Native call dài không unmount hiệu quả | Có thể ít lợi ích | Carrier occupation/pinning |
| Hàng triệu task giữ object/payload lớn | Vẫn có thể cạn bộ nhớ | Heap, buffers, ThreadLocal, queue/admission |

**Độ rẻ của virtual thread không đồng nghĩa task và tài nguyên mà task nắm giữ cũng rẻ.** 100.000 virtual threads chờ database với payload 1 MB mỗi task vẫn có thể gây áp lực bộ nhớ và làm hệ thống quá tải.

## 3. Tại sao platform threads từng cần pool?

Trong mô hình cũ:

```java
ExecutorService pool = Executors.newFixedThreadPool(16);
```

Pool chứa một số platform threads giới hạn, tái sử dụng chúng qua các tác vụ. Có hai mục đích thường gắn chung:

1. **Tiết kiệm và tái sử dụng threads**: thread của OS có chi phí đáng kể.
2. **Giới hạn số tác vụ chạy đồng thời**: khi 16 worker bận, task còn lại chờ queue.

Khi dùng virtual threads, mục đích thứ nhất hầu như không còn lý do chính: một virtual thread mới có chi phí tương đối nhỏ và được thiết kế sống theo một tác vụ. Nhưng mục đích thứ hai **vẫn còn** nếu tác vụ cùng truy cập một tài nguyên hữu hạn.

Sai lầm là giữ cả hai mục đích trong cùng một fixed virtual-thread pool. Kết quả:

```text
Fixed pool 16 virtual workers:
Task #1 .. #16 -> chiếm 16 virtual workers
Task #17 ..    -> nằm trong executor queue
                  dù virtual threads mới có thể được tạo rẻ
```

Một pool như vậy biến virtual threads thành những worker dùng lại lâu dài, làm mất nhiều lợi ích quan sát và vòng đời thread-per-task. Nếu cần **giới hạn truy cập DB tối đa 16**, phải thể hiện giới hạn ấy như một điều kiện của **DB resource**, thay vì cố giả lập pool platform threads.

### 3.1. “Không pool” không có nghĩa “không cần quản lý ExecutorService”

`Executors.newVirtualThreadPerTaskExecutor()` vẫn trả một `ExecutorService`, và có thể giữ nó trong suốt một đợt công việc để submit nhiều task. Điểm khác là **executor không tái sử dụng virtual thread**: mỗi task có một virtual thread riêng.

```text
Một ExecutorService có vòng đời rõ ràng
        |
        +-- submit task A -> virtual-A mới
        +-- submit task B -> virtual-B mới
        +-- submit task C -> virtual-C mới
        |
        +-- close()/shutdown() quản lý việc nhận và kết thúc task
```

Một **executor object dùng chung** và một **pool virtual threads** là hai khái niệm khác nhau. Không tạo executor mới cho mỗi request chỉ để trông “đúng virtual”; hãy chọn owner và lifecycle thích hợp.

## 4. Ba API Java 21 để dùng virtual threads theo mỗi task

### 4.1. Thread.startVirtualThread

Khi chỉ muốn tạo một virtual thread cho một việc rõ ràng:

```java
Thread worker = Thread.startVirtualThread(() -> {
    System.out.println(Thread.currentThread().isVirtual());
});
worker.join();
```

Đây là **mảnh code trong method có `throws InterruptedException`**. `Thread.startVirtualThread` tạo và khởi chạy một virtual thread. `join()` đợi thread kết thúc, không buộc thread thành platform thread.

### 4.2. Thread.ofVirtual() để điều chỉnh thread factory

```java
ThreadFactory factory = Thread.ofVirtual()
        .name("lookup-", 0)
        .factory();

Thread thread = factory.newThread(() -> {
    // Công việc của một lượt.
});
thread.start();
thread.join();
```

Mảnh code cần import `java.util.concurrent.ThreadFactory` và xử lý `InterruptedException`. Factory tạo virtual threads mới với tên gợi ý, hữu ích cho logging/thread dump. **Đừng truyền factory vào một fixed thread pool chỉ để hạn chế số virtual threads**.

### 4.3. Executors.newVirtualThreadPerTaskExecutor

```java
try (ExecutorService executor =
        Executors.newVirtualThreadPerTaskExecutor()) {

    Future<Integer> value = executor.submit(() -> 6 * 7);
    System.out.println(value.get()); // 42
}
```

Đây là mảnh method cần `ExecutorService`, `Executors`, `Future` và xử lý `InterruptedException`/`ExecutionException` hoặc khai báo `throws Exception`.

- Mỗi task tạo một virtual thread mới.
- `Future.get()` cho phép caller nhận kết quả/ngoại lệ.
- `ExecutorService.close()` trong Java 21 là một phần của hợp đồng `AutoCloseable`, cho phép đợi các task kết thúc khi rời `try-with-resources`.
- Với task không kết thúc hoặc chờ I/O không có timeout, đóng executor **có thể chờ lâu**; thiết kế cancellation/deadline.
- Virtual threads là **daemon threads**, nên không được dựa vào chúng để giữ JVM sống mãi khi main và các non-daemon threads khác đã kết thúc.

**JDK yêu cầu:** `Executors.newVirtualThreadPerTaskExecutor()` và `Thread.isVirtual()` là API chuẩn từ **Java 21**. Java 17 không có chúng; không đặt ví dụ này dưới `maven.compiler.release=17` rồi tuyên bố biên dịch được.

## 5. Phản ví dụ: fixed pool với virtual ThreadFactory

Một người có thể thử:

```java
ExecutorService virtualWorkers = Executors.newFixedThreadPool(
        8, Thread.ofVirtual().factory());
```

Đoạn này chỉ tạo tối đa **tám virtual worker threads** theo hợp đồng của fixed thread pool, rồi **tái sử dụng** chúng để chạy nhiều task. Khi tám worker bận, task khác được xếp vào queue của `ThreadPoolExecutor`.

Vấn đề không phải “Java cấm cách viết ấy”; Java API cho phép. Vấn đề là **thiết kế sai mục tiêu**:

- Virtual threads được thiết kế cho mô hình **một task, một thread**.
- Pool giữ worker virtual threads tồn tại để tái sử dụng, trong khi chúng vốn không cần tái sử dụng.
- Giới hạn “8” áp lên mọi loại công việc, kể cả công việc không tranh chấp cùng tài nguyên.
- Nếu task chờ network/DB, việc chỉ có 8 workers không tận dụng khả năng có nhiều virtual threads đang chờ.
- Queue có thể lớn hoặc không giới hạn mà developer không nhận ra.
- Các `ThreadLocal` trên worker tái sử dụng có thể giữ dữ liệu cũ nếu code không cleanup đúng; mô hình one-thread-per-task làm vòng đời thread bám sát tác vụ hơn.

Điều này đặc biệt dễ xảy ra khi “nâng cấp Loom” bằng cách thay đúng một dòng thread factory mà không xem lại ý nghĩa của thread pool.

**Sửa hướng thiết kế:**

```java
// Thay vì:
Executors.newFixedThreadPool(8, Thread.ofVirtual().factory());

// Dùng virtual thread mới cho mỗi task:
Executors.newVirtualThreadPerTaskExecutor();

// Và giới hạn phần thật sự hữu hạn:
Semaphore databaseSlots = new Semaphore(8);
```

Đây là các mảnh cú pháp minh họa, không phải một chương trình độc lập. Sau khi có semaphore, phải sử dụng nó **quanh lời gọi tới tài nguyên hữu hạn**, với `acquire`/`release` an toàn trong `finally`. Chỉ khai báo semaphore mà không dùng là vô nghĩa.

## 6. Giới hạn tài nguyên bằng Semaphore, không pool virtual threads

Ví dụ một remote service chỉ chịu được **hai lời gọi đang diễn ra cùng lúc**. Ta muốn bốn tác vụ có virtual threads riêng, nhưng chỉ tối đa hai tác vụ giữ quyền truy cập remote service.

```text
4 tasks -> 4 virtual threads riêng
                  |
                  v
             Semaphore(2)
             /           \
         permit #1     permit #2
             |             |
             +---- gọi remote service
                  |
          2 virtual threads khác chờ permit
          (không chiếm thêm hai worker OS cố định)
```

Với `Semaphore`, số permit mô tả **khả năng của service**, không phải số virtual threads được phép tồn tại. Khi kết thúc lời gọi, permit phải được trả về ngay cả nếu có exception.

Mẫu method:

```java
private final Semaphore remoteSlots = new Semaphore(2);

public Result callRemote() throws InterruptedException {
    remoteSlots.acquire();
    try {
        return remoteClient.call();
    } finally {
        remoteSlots.release();
    }
}
```

Đây là **mảnh code trong class**, với `Result` và `remoteClient` của ứng dụng. Chỉ dùng `finally` sau khi `acquire` đã thành công; nếu bị interrupt khi chờ permit và `acquire` ném `InterruptedException`, không được `release` một permit chưa lấy.

### 6.1. Semaphore không thay thế timeout

Nếu remote service treo mãi và hai task giữ permit không trả, những virtual threads khác có thể chờ vô hạn. Cần timeout ở remote client, deadline của nghiệp vụ và giới hạn thời gian chờ permit:

```java
if (!remoteSlots.tryAcquire(200, TimeUnit.MILLISECONDS)) {
    throw new IllegalStateException("Remote service quá tải");
}
try {
    return remoteClient.callWithTimeout();
} finally {
    remoteSlots.release();
}
```

Mảnh code cần import `TimeUnit`, method `throws InterruptedException` hoặc xử lý riêng, và remote client thực thi timeout thật. `tryAcquire` giúp đưa ra quyết định **từ chối, retry hoặc backpressure** theo chính sách thay vì giữ vô số virtual threads chờ quá lâu.

### 6.2. Connection pool có thể là giới hạn sẵn có

Nếu database connection pool có tối đa 10 connections, chính pool đã hạn chế số tác vụ có thể **đồng thời giữ connection**. Đặt thêm một `Semaphore(10)` bên ngoài thường không cần thiết và có thể gây hai tầng xếp hàng khó đoán.

Phân tích tài nguyên cụ thể trước khi thêm throttle:

- Database: `HikariCP maximumPoolSize` và `connectionTimeout`.
- HTTP outbound: connection pool, max concurrent requests, remote quota, timeout.
- Rate limit: requests/second, khác với concurrent in-flight requests.
- CPU: số core và mức độ song song phù hợp.
- Memory: số task và payload được nhận cùng lúc.

**Đừng dùng một giới hạn global 8 virtual threads nếu hệ thống có cả hai service độc lập với khả năng khác nhau.** Một bulkhead theo tài nguyên bảo vệ được đúng chỗ nghẽn hơn.

## 7. Demo đầy đủ JDK 21: một thread cho mỗi task, nhưng chỉ hai tác vụ truy cập tài nguyên

Đây là một **chương trình Java độc lập**, không dùng Spring. Nó tạo **bốn virtual threads riêng biệt** thông qua `newVirtualThreadPerTaskExecutor()`, nhưng chỉ cho phép **hai tác vụ** đi qua semaphore cùng lúc. `CountDownLatch` tạo các mốc rõ ràng, nên không cần dùng `Thread.sleep(100)` và đoán thứ tự chạy.

**Tệp `VirtualThreadsNotPooledDemo.java` (JDK 21+):**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualThreadsNotPooledDemo {

    public static void main(String[] args) throws Exception {
        Semaphore permits = new Semaphore(2, true);
        CountDownLatch firstTwoEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        Set<Long> threadIds = ConcurrentHashMap.newKeySet();
        List<Future<String>> results = new ArrayList<>();

        try (ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor()) {

            for (int i = 1; i <= 4; i++) {
                final int taskId = i;

                results.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        int now = active.incrementAndGet();
                        peak.accumulateAndGet(now, Math::max);
                        threadIds.add(
                                Thread.currentThread().threadId());
                        firstTwoEntered.countDown();

                        try {
                            release.await();
                            return "task-" + taskId
                                    + ": virtual="
                                    + Thread.currentThread().isVirtual();
                        } finally {
                            active.decrementAndGet();
                        }
                    } finally {
                        permits.release();
                    }
                }));
            }

            try {
                if (!firstTwoEntered.await(
                        5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Hai tác vụ chưa lấy được permit");
                }

                System.out.println("Đang dùng tài nguyên: "
                        + active.get());
            } finally {
                release.countDown();
            }

            for (Future<String> result : results) {
                System.out.println(
                        result.get(5, TimeUnit.SECONDS));
            }

            System.out.println("Số virtual threads: "
                    + threadIds.size());
            System.out.println("Đồng thời tối đa: "
                    + peak.get());
            System.out.println("Permit còn lại: "
                    + permits.availablePermits());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac VirtualThreadsNotPooledDemo.java
java VirtualThreadsNotPooledDemo
```

**Đầu ra bình thường theo thứ tự in của main:**

```text
Đang dùng tài nguyên: 2
task-1: virtual=true
task-2: virtual=true
task-3: virtual=true
task-4: virtual=true
Số virtual threads: 4
Đồng thời tối đa: 2
Permit còn lại: 2
```

Vì sao đầu ra có thể kiểm chứng?

1. Mỗi lần `executor.submit(...)` tạo một virtual thread riêng, tổng cộng bốn task và bốn thread IDs khác nhau.
2. `Semaphore(2,true)` có hai permits. Dù scheduler cho thứ tự giành permit khác nhau, không hơn hai tác vụ cùng vào khối được bảo vệ.
3. Hai tác vụ đầu tiên lấy được permit sẽ gọi `firstTwoEntered.countDown()` và chờ `release`, nên khi main in dòng đầu, `active` đúng bằng 2.
4. Main mở `release` trong `finally`, cho hai tác vụ đang giữ permit hoàn tất và các tác vụ còn lại lần lượt vào.
5. `Future.get(...)` được duyệt **theo thứ tự danh sách submit** nên các dòng `task-1` đến `task-4` được in đúng thứ tự ngay cả nếu worker hoàn tất không theo thứ tự.
6. `peak` không quá 2, `availablePermits()` trở về 2 sau khi mọi task đã hoàn tất.
7. Có timeout 5 giây khi đợi latch/Future. Nếu môi trường quá tải, chương trình báo lỗi thay vì khẳng định một kết quả chưa quan sát.
8. `try-with-resources` đóng executor khi công việc hoàn tất.

**Phần quan trọng nhất:** Chương trình **không tạo virtual-thread pool có hai worker**. Nó tạo **bốn virtual threads**, nhưng giới hạn **chỉ hai lời gọi tài nguyên** được thực thi đồng thời. Số virtual threads có thể lớn hơn số permit mà không cần cố giữ một tập virtual worker lâu dài.

### 7.1. Latch và Semaphore phục vụ hai mục tiêu khác nhau

- `Semaphore` là cơ chế **giới hạn đồng thời** ở runtime.
- `CountDownLatch` trong demo chỉ để **kiểm thử và điều phối thời điểm in kết quả**.

Không đặt `CountDownLatch` vào sản phẩm chỉ để giả lập một queue hoặc giới hạn số worker. Trong dịch vụ thật, hãy dùng semaphore/connection pool/timeouts và các API có hợp đồng tải rõ ràng.

### 7.2. Tránh hiểu sai giới hạn

Nếu cho 100.000 task cùng tạo virtual thread rồi chờ `Semaphore(2)`, mỗi task vẫn có thể giữ memory/context trong lúc chờ. Vì vậy semaphore giới hạn **in-flight resource users**, nhưng không giải quyết mọi vấn đề về **admission control**. Khi lượng request có thể không giới hạn, cần từ chối/giới hạn số task được chấp nhận từ biên, đặt timeout chờ và đo backlog.

## 8. Tại sao giới hạn số virtual threads không tương đương quản lý tải?

Số **tác vụ đang tồn tại**, số **lời gọi tới hệ thống ngoài**, số **CPU-bound tasks đang tính**, số **kết nối DB** và số **request được nhận** là những đại lượng khác nhau.

Một ví dụ:

```text
1000 virtual threads:
  - 800 đang chờ phản hồi HTTP
  - 100 đang chờ connection pool
  - 70 đang chờ một semaphore
  - 30 đang chạy CPU / chạy code ứng dụng
```

Đây là **số liệu giả định**. Nếu dùng fixed pool 30 virtual workers, bạn đang giới hạn **toàn bộ loại việc** ở 30, không chỉ 30 task đang cần CPU. Khi HTTP call dài, worker pool có thể giữ queue lớn trong khi carrier threads thực tế có thể phục vụ nhiều tác vụ I/O hơn.

Tuy nhiên mở unbounded admission cũng có thể quá tải:

- Heap cho request objects, stack chunks, buffers và `ThreadLocal`.
- Downstream quota / connection pool.
- Thread scheduler bookkeeping.
- Queue/waiting list trong các API.
- Logging/tracing và số span đang mở.
- Tăng độ trễ đuôi (tail latency).

**Phương án:** Tách các policy: số request được nhận, thời gian chờ, concurrent downstream calls, DB connections, CPU work và memory budgets. Virtual thread chỉ là **cách biểu diễn concurrent tasks nhẹ hơn**, không tự thay thế control plane của ứng dụng.

## 9. Quy tắc định lượng: throughput, latency và concurrency

Trong hệ thống có các request ổn định, Little's Law gợi ý:

\[
L \approx \lambda \times W
\]

Trong đó:

- `L`: số công việc đồng thời đang ở trong hệ thống.
- `λ`: thông lượng trung bình (jobs/s).
- `W`: thời gian mỗi công việc ở hệ thống (giây).

Ví dụ giả định 200 request/s, mỗi request mất trung bình 0,5 giây vì chờ I/O, thì khoảng 100 request có thể đang tồn tại cùng lúc. Với platform-thread-per-request, số OS threads lớn có thể gây chi phí. Với virtual-thread-per-request, một phần chi phí thread chờ được giảm.

Nhưng nếu database chỉ xử lý hiệu quả 20 truy vấn đồng thời, tạo thêm 10.000 virtual threads **không làm DB có thêm 10.000 connections**. Những yêu cầu còn lại phải chờ, retry hoặc bị từ chối theo chiến lược tải.

Không coi công thức trên là một SLA dự báo chính xác khi lưu lượng không ổn định, có burst, timeout hoặc retry. Nó là công cụ giúp phân biệt **khả năng treo nhiều công việc chờ** với **năng lực của tài nguyên hữu hạn**.

## 10. Khi nào nên chọn platform thread pool?

“Không pool virtual threads” **không** có nghĩa platform thread pools đã lỗi thời.

| Nhu cầu | Giải pháp thường phù hợp | Lý do |
|---|---|---|
| Nhiều công việc blocking I/O độc lập | Virtual-thread-per-task | Giảm overhead của thread chờ |
| CPU-bound transformations | Executor phù hợp số cores, thường platform threads | CPU song song bị giới hạn bởi cores |
| Thư viện native/thiết bị có giới hạn | Bulkhead, semaphore, dedicated executor | Kiểm soát resource và integration |
| Bounded queue/backpressure theo task | Queue và admission control rõ | Không mặc định tạo vô hạn task |
| Tác vụ có yêu cầu kiểm soát lifecycle chặt | Executor với shutdown/tracking rõ | Dừng/đợi/cancel là bài toán riêng |
| Số kết nối DB hữu hạn | Connection pool có timeout | Đúng tầng tài nguyên cần kiểm soát |

Đối với CPU-bound processing, một `ThreadPoolExecutor` dùng platform threads có thể vẫn là lựa chọn hợp lý để giới hạn số phép tính chạy song song. Không biến CPU-intensive task thành virtual-thread workload rồi kỳ vọng thông lượng CPU tăng vượt số core.

### 10.1. Một anti-pattern khác: blocking spin thay cho I/O

Virtual threads có lợi khi runtime có thể tạm ngưng thread ở điểm blocking thích hợp. Một vòng lặp bận như:

```java
while (!condition()) {
    // Busy-spin; vẫn chiếm CPU.
}
```

không được “tối ưu thành không tốn CPU” chỉ vì đang chạy trong virtual thread. Cần blocking synchronization, event-driven signals hoặc thuật toán phù hợp.

## 11. Tích hợp Spring: SimpleAsyncTaskExecutor với virtual threads

Spring Framework **6.1 trở lên** cung cấp `SimpleAsyncTaskExecutor.setVirtualThreads(true)` khi ứng dụng chạy **Java 21+**.

`SimpleAsyncTaskExecutor` vốn khởi chạy **một thread mới cho mỗi task**; nó **không tái sử dụng threads** như `ThreadPoolTaskExecutor`. Khi bật `setVirtualThreads(true)`, mỗi tác vụ có virtual thread riêng:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
public class VirtualExecutionConfig {

    @Bean("virtualTaskExecutor")
    public SimpleAsyncTaskExecutor virtualTaskExecutor() {
        SimpleAsyncTaskExecutor executor =
                new SimpleAsyncTaskExecutor("virtual-work-");

        executor.setVirtualThreads(true);
        return executor;
    }
}
```

Đây là **Spring configuration class**, không có `main` độc lập. Dù biến được gọi là executor, nó **không phải virtual thread pool**: một lượt `execute`/`submit` tạo một virtual thread mới.

### 11.1. @Async sử dụng đúng executor được cấu hình

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    @Async("virtualTaskExecutor")
    public CompletableFuture<String> summarize(String id) {
        String result = blockingDocumentServiceCall(id);
        return CompletableFuture.completedFuture(result);
    }

    private String blockingDocumentServiceCall(String id) {
        return "done-" + id; // Mã đại diện cho I/O trong bài học.
    }
}
```

Đây là một **Spring component**, cần cấu hình `@EnableAsync` trong context và lời gọi phải đi qua **Spring proxy**. `@Async("virtualTaskExecutor")` chọn đúng executor với virtual threads. Một lời gọi `this.summarize(...)` bên trong cùng bean thông thường **không qua proxy** ở chế độ proxy mặc định, nên không có việc submit bất đồng bộ như mong đợi.

**Chú ý:** `@Async` chuyển công việc sang thread khác, không có nghĩa mọi bước `CompletableFuture.thenApplyAsync(...)` sau đó đều chạy trên cùng executor. Continuation không truyền executor có thể chạy ở common pool.

### 11.2. Vì sao không dùng ThreadPoolTaskExecutor để “pool virtual threads”?

`ThreadPoolTaskExecutor` là một thread pool theo hợp đồng và có khái niệm core/max/queue. Nếu thay `ThreadFactory` của nó thành virtual threads mà vẫn giữ worker reuse, bạn đang **áp dụng cơ chế pooling cũ** cho virtual workers.

Spring mới có thêm các tùy chọn virtual-thread factory/lifecycle trên một số executor implementation, nhưng điểm quan trọng là **hiểu rõ hợp đồng của executor bạn chọn**. Với nhu cầu **một virtual thread mới cho một task**, `SimpleAsyncTaskExecutor.setVirtualThreads(true)` thể hiện ý đồ trực tiếp hơn. Không chỉ nhìn vào tên bean có chữ `virtual` mà kết luận đã bỏ pooling.

## 12. Demo Maven chạy được: @Async thực sự tạo virtual thread cho từng task

Dự án này dùng **Java 21 + Spring Framework 6.2.7**; không cần Spring Boot hay HTTP server. Hai lời gọi vào service được Spring proxy chuyển tới `SimpleAsyncTaskExecutor`. Mỗi method chạy trong một virtual thread riêng và trả thông tin để main kiểm chứng.

### 12.1. Cấu trúc thư mục

```text
spring-virtual-per-task/
├── pom.xml
└── src/main/java/lesson/SpringVirtualTaskDemo.java
```

### 12.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>lesson</groupId>
    <artifactId>spring-virtual-per-task</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
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

`spring-context` mang các thành phần configuration và `@EnableAsync` cần thiết qua dependencies của Spring Framework. `maven.compiler.release=21` bắt buộc vì demo dùng API virtual-thread được chuẩn hóa từ Java 21.

### 12.3. src/main/java/lesson/SpringVirtualTaskDemo.java

```java
package lesson;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

public class SpringVirtualTaskDemo {

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class)) {

            WorkService service = context.getBean(WorkService.class);

            CompletableFuture<ThreadInfo> first =
                    service.inspect("A");
            CompletableFuture<ThreadInfo> second =
                    service.inspect("B");

            ThreadInfo a = first.get(5, TimeUnit.SECONDS);
            ThreadInfo b = second.get(5, TimeUnit.SECONDS);

            System.out.println("A: virtual=" + a.virtual());
            System.out.println("B: virtual=" + b.virtual());
            System.out.println("Hai thread khác nhau: "
                    + (a.threadId() != b.threadId()));
        }
    }

    public record ThreadInfo(
            String id, boolean virtual, long threadId) {
    }

    @Configuration
    @EnableAsync
    public static class Config {

        @Bean("virtualExecutor")
        public SimpleAsyncTaskExecutor virtualExecutor() {
            SimpleAsyncTaskExecutor executor =
                    new SimpleAsyncTaskExecutor("spring-vt-");

            executor.setVirtualThreads(true);
            return executor;
        }

        @Bean
        public WorkService workService() {
            return new WorkService();
        }
    }

    public static class WorkService {

        @Async("virtualExecutor")
        public CompletableFuture<ThreadInfo> inspect(String id) {
            Thread current = Thread.currentThread();

            ThreadInfo info = new ThreadInfo(
                    id,
                    current.isVirtual(),
                    current.threadId());

            return CompletableFuture.completedFuture(info);
        }
    }
}
```

### 12.4. Chạy và kết quả

```bash
mvn -q compile exec:java -Dexec.mainClass=lesson.SpringVirtualTaskDemo
```

Các dòng do **chương trình** in trong môi trường hoạt động bình thường:

```text
A: virtual=true
B: virtual=true
Hai thread khác nhau: true
```

Giải thích:

1. `@EnableAsync` đăng ký async infrastructure trong Spring context.
2. `WorkService` được tạo như bean Spring; main lấy bean qua `context.getBean(...)` nên gọi qua proxy phù hợp.
3. `@Async("virtualExecutor")` gửi mỗi invocation vào `SimpleAsyncTaskExecutor` đã bật virtual threads.
4. `inspect("A")` và `inspect("B")` được chạy trong hai virtual threads mới, khác `threadId()`.
5. Trả `CompletableFuture.completedFuture(info)` từ target method là hợp lệ: Spring async proxy cung cấp handle của việc thực thi target method bất đồng bộ.
6. `get(5, TimeUnit.SECONDS)` giúp demo chờ có thời hạn. Nếu worker không được chạy kịp, chương trình báo timeout, không khẳng định thành công giả.
7. `try-with-resources` đóng context sau khi hai Future đã hoàn tất. `SimpleAsyncTaskExecutor` có khác biệt lifecycle so với `ThreadPoolTaskExecutor`; ở đây không dựa vào context close để chờ một khối lượng lớn task nền chưa hoàn tất.

**Không có fixed-size virtual worker pool** trong ví dụ. Executor có một đối tượng cấu hình chung, nhưng tạo virtual thread mới **mỗi khi nhận task**.

## 13. Nếu cần giới hạn tải trong Spring thì làm thế nào?

### 13.1. Giới hạn tài nguyên bằng Semaphore trong service

Một bean có thể sở hữu semaphore theo service cụ thể:

```java
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class LimitedRemoteService {
    private final Semaphore remoteSlots =
            new Semaphore(10, true);

    public String fetch(String key)
            throws InterruptedException {

        if (!remoteSlots.tryAcquire(
                300, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException(
                    "Remote service quá tải");
        }

        try {
            return callRemoteWithTimeout(key);
        } finally {
            remoteSlots.release();
        }
    }

    private String callRemoteWithTimeout(String key) {
        return "result-" + key;
    }
}
```

Đây là **mảnh Spring component** minh họa; cần import `org.springframework.stereotype.Service`, và method giả lập `callRemoteWithTimeout` cần được thay bằng client có timeout thật khi triển khai. Semaphore giới hạn **số lời gọi vào remote service** chứ không giới hạn tất cả virtual threads dùng cho nhiều loại nghiệp vụ khác.

### 13.2. SimpleAsyncTaskExecutor.setConcurrencyLimit có phải pooling không?

**Không.** Spring `SimpleAsyncTaskExecutor` có:

```java
SimpleAsyncTaskExecutor executor =
        new SimpleAsyncTaskExecutor("vt-");
executor.setVirtualThreads(true);
executor.setConcurrencyLimit(10);
```

Đây là **mảnh code**, Spring 6.1+ và Java 21+. Dù giới hạn 10 lần thực thi đồng thời, executor vẫn **không tái sử dụng virtual threads**. `setConcurrencyLimit` là một **concurrency throttle ở nơi submit**, không tạo fixed worker pool.

Theo Javadoc Spring 6.2.7:

- Mặc định `setConcurrencyLimit(-1)`: không throttle ở executor.
- Nếu đạt limit và không đổi chính sách, caller có thể **bị chặn khi submit** cho đến khi có slot.
- Từ Spring 6.2.6 có `setRejectTasksWhenLimitReached(true)` để chọn **từ chối ngay** thay vì block submitter khi đạt giới hạn.

```java
executor.setConcurrencyLimit(10);
executor.setRejectTasksWhenLimitReached(true);
```

Đây là **mảnh cấu hình** cho Spring 6.2.6+, và reject có thể ném `TaskRejectedException`. Hai cách throttling có hệ quả vận hành khác nhau: chặn caller có thể kéo dài độ trễ request, còn reject cần API phản hồi quá tải/retry hợp lý.

**Cách chọn:** Nếu giới hạn chỉ dành cho một DB hoặc một API, semaphore/connection pool **ngay tại tài nguyên** biểu đạt đúng hơn. Nếu bạn muốn tổng số task được executor nhận **có giới hạn**, `setConcurrencyLimit` có thể hữu ích, nhưng vẫn phải hiểu nó không tạo queue quản lý như `ThreadPoolTaskExecutor`.

### 13.3. Tránh “giới hạn kép” không có chủ đích

Nếu connection pool đã giới hạn 10 kết nối, thêm một semaphore 10 permits và `setConcurrencyLimit(10)` có thể tạo **ba nơi chờ**: executor submit, semaphore và connection acquire. Điều này gây khó hiểu khi đo latency và khi cancellation/timeout xảy ra.

Chỉ thêm mỗi giới hạn khi nó bảo vệ **một chính sách độc lập có đo lường và nhu cầu rõ ràng**.

## 14. Spring Boot: bật virtual threads không tương đương tự cấu hình pool size

Trong Spring Boot hỗ trợ Java 21+, có thể bật:

```properties
spring.threads.virtual.enabled=true
```

Khi Boot auto-configures `AsyncTaskExecutor` và không có custom executor thay thế theo điều kiện cấu hình, Boot có thể chọn `SimpleAsyncTaskExecutor` với virtual threads thay vì `ThreadPoolTaskExecutor` platform threads.

**Không diễn giải thuộc tính này là “tự động tạo pool virtual threads với N worker”.** Trong chế độ này, nhiều thuộc tính dành cho thread pool như `spring.task.execution.pool.*` không còn ý nghĩa như khi dùng `ThreadPoolTaskExecutor`.

Điều phải kiểm tra trong ứng dụng thật:

1. **JDK 21+** và Spring Boot version hỗ trợ.
2. `spring.threads.virtual.enabled=true` được đọc từ cấu hình runtime chính xác.
3. `@Async` có thực sự dùng **Boot auto-configured executor**, hay đang chọn một bean executor khác qua `@Async("name")`?
4. Có custom `Executor`/`AsyncConfigurer` khiến auto-configuration chọn đường khác không?
5. Tải thực có cần global admission control, connection pool limit, remote quotas và timeout không?
6. Logging/tracing/security context được truyền đúng khi đổi thread không?

Tài liệu Boot cũng lưu ý: virtual threads là **daemon**, có thể làm JVM thoát khi không còn non-daemon threads. Nếu ứng dụng chỉ sống nhờ job scheduling/background virtual threads, cấu hình `spring.main.keep-alive=true` có thể cần thiết. Không áp điều này như một bắt buộc tuyệt đối cho mọi web server, nơi có những thành phần khác giữ process sống.

### 14.1. Cẩn thận với tự khai báo Executor bean

Nếu bạn đã khai báo một bean `ThreadPoolTaskExecutor` và `@Async` chỉ rõ tên bean đó, bật property virtual threads của Boot **không có nghĩa bean custom ấy tự biến thành thread-per-task executor**. Spring có các quy tắc bean selection và qualifiers; hãy kiểm tra bean thực tế được dùng.

Một kiểm thử gọn là gọi method `@Async` qua Spring proxy và ghi `Thread.currentThread().isVirtual()` như demo ở mục 12. Chỉ kiểm tra sự tồn tại của property không đủ.

## 15. @Scheduled và virtual threads: không pool, nhưng còn ngữ nghĩa Trigger

Spring Framework 6.1+ có `SimpleAsyncTaskScheduler`, một implementation thích hợp cho lập lịch kiểu thread-per-task với virtual threads:

```java
SimpleAsyncTaskScheduler scheduler =
        new SimpleAsyncTaskScheduler();
scheduler.setVirtualThreads(true);
```

Đây là **mảnh code cấu hình**, cần import `org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler` và có vòng đời Spring đúng.

Tài liệu Spring nhấn mạnh một ngoại lệ: **fixed-delay tasks của `SimpleAsyncTaskScheduler` thực thi trên một scheduler thread đơn để giữ ngữ nghĩa fixed delay**. Trong khi đó, fixed-rate và cron thuận hơn cho mô hình tách task sang thread riêng.

| Loại lịch | Điều cần lưu ý khi dùng SimpleAsyncTaskScheduler |
|---|---|
| `fixedRate` | Có thể phù hợp với thread-per-task dispatch |
| `cron` | Có thể phù hợp khi tính các mốc Trigger |
| `fixedDelay` | Chạy trên scheduler thread đơn; I/O blocking dài có thể chặn lịch khác |
| `ThreadPoolTaskScheduler` | Một implementation khác, có pool threads và lifecycle riêng |

**Không chuyển mọi `@Scheduled(fixedDelay=...)` sang virtual threads mà không đo tác động.** Một fixed-delay job giữ scheduler thread một thời gian dài có thể làm các job khác đến muộn. Khi nghiệp vụ cần “nghỉ sau khi job thực sự hoàn tất”, cũng không thể sửa bằng cách submit task con rồi trả ngay mà không theo dõi completion.

### 15.1. Cấu hình Boot cho scheduling

Khi Spring Boot được bật virtual threads và sử dụng auto-configured scheduler, nó có thể tạo `SimpleAsyncTaskScheduler` thay vì `ThreadPoolTaskScheduler`. Thuộc tính cấu hình pool size của scheduler không nhất thiết còn tác dụng như trước. Xác minh bean scheduler thật và timeline của workload fixed-delay.

**Bật virtual threads không tự chuyển ứng dụng sang distributed scheduler**. Một `@Scheduled` trên ba pod vẫn có thể có ba registrations.

## 16. Cancellation, interruption và thời gian sống của task

Một virtual thread là một Java `Thread`, nên code vẫn cần quản lý:

- `InterruptedException` và cờ `Thread.currentThread().isInterrupted()`.
- Timeout khi chờ database, HTTP, semaphore hoặc queue.
- `Future.cancel(true)` khi policy cho phép.
- `ExecutorService.shutdown()/close()` và lifetime của caller.
- Idempotency và bù trừ khi task có side effect ngoài JVM.

Mảnh code:

```java
Future<?> future = executor.submit(() -> {
    try {
        blockingCallWithTimeout();
    } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
    }
});

future.cancel(true);
```

Đây là **mảnh minh họa**, `executor` và `blockingCallWithTimeout` do ứng dụng cung cấp; exception method signature cần được thiết kế thực tế. `cancel(true)` yêu cầu interrupt task, không bảo đảm mọi thư viện I/O dừng ngay hay đã rollback giao dịch.

### 16.1. Không dựa vào daemon virtual thread để duy trì công việc

Virtual threads mặc định là daemon; nếu JVM không còn non-daemon threads, chương trình có thể kết thúc trong khi task còn chạy. Với một chương trình Java độc lập, `Future.get()`/`join()`/`ExecutorService.close()` cho điểm chờ cụ thể. Với Spring, cần hiểu lifecycle và graceful shutdown của executor/scheduler đang dùng; không biến một job quan trọng thành “fire-and-forget” không có handle/trạng thái.

### 16.2. SimpleAsyncTaskExecutor không giống ThreadPoolTaskExecutor về lifecycle

Spring `SimpleAsyncTaskExecutor` không tham gia mọi cơ chế coordinated lifecycle stop như `ThreadPoolTaskExecutor`. Javadoc cho phép `setTaskTerminationTimeout(...)` để theo dõi/đợi task khi `close()`, nhưng có overhead tracking cho mỗi task.

Không mặc định việc đóng `ApplicationContext` sẽ đảm bảo mọi virtual task không được theo dõi đã hoàn thành nghiệp vụ, đặc biệt với task chạy rất lâu. Cần xác định ownership, shutdown semantics và deadline theo yêu cầu của ứng dụng.

## 17. ThreadLocal, MDC và SecurityContext: luồng mới không có nghĩa context tự chuyển

Mô hình **một virtual thread mới cho một task** giúp rõ ràng về vòng đời thread, nhưng không tự giải quyết việc truyền ngữ cảnh từ caller:

- `ThreadLocal` của HTTP request thread không mặc nhiên có trong async virtual thread.
- MDC logging cần được truyền/khôi phục theo cơ chế logging/context propagation phù hợp.
- Spring Security context có thể cần wrapper/adapter hoặc truyền identity theo hợp đồng được kiểm soát.
- Spring transaction/resource gắn với thread **không thể sao chép mù quáng sang worker**.

Ví dụ:

```java
private static final ThreadLocal<String> REQUEST_ID =
        new ThreadLocal<>();

REQUEST_ID.set("R-42");
executor.execute(() -> {
    // Giá trị có thể null: không tự sao chép từ caller.
    System.out.println(REQUEST_ID.get());
});
REQUEST_ID.remove();
```

Đây là mảnh code trong class có `executor`. Không suy rằng “virtual thread lightweight” đồng nghĩa “thừa hưởng đầy đủ request context”.

Spring có `TaskDecorator` và `ContextPropagatingTaskDecorator` kết hợp Micrometer Context Propagation khi thư viện/accessors thích hợp có sẵn. Với custom context, xác định đúng `ThreadLocalAccessor` và scope cleanup; đừng log token/password hoặc giữ cả request object lớn trong virtual thread.

### 17.1. ThreadLocal lớn nhân với số task

Khi số virtual threads có thể lớn, mỗi thread giữ một `ThreadLocal` có payload 1 MB có thể tạo áp lực heap lớn. Các giá trị context nên nhỏ, bất biến khi có thể, và được giải phóng khi task hoàn tất.

Một request ID dạng String khác xa việc giữ toàn bộ HTTP request, transaction/session hoặc binary payload trong thread-local.

## 18. Pinning: phân biệt JDK 21 và JDK 24+

Pinning xảy ra khi một virtual thread không thể unmount khỏi carrier trong một số đoạn blocking. Tác vụ vẫn có thể đúng về chức năng, nhưng carrier bị giữ lâu hơn, làm giảm khả năng mở rộng.

**Với Java 21–23**, blocking dài trong một số tình huống `synchronized` và native/foreign calls có thể gây pinning. Đây là lý do các tài liệu ban đầu thường khuyên không làm I/O dài khi giữ monitor `synchronized`.

**Từ JDK 24 (JEP 491)**, JVM đã cải tiến `synchronized` để virtual threads có thể dùng monitor mà không chịu kiểu pinning cũ trong nhiều trường hợp. Do đó không nên lặp lại khẳng định “mọi synchronized block luôn pin virtual thread” cho các JDK hiện đại.

Một số dạng native/foreign blocking vẫn cần kiểm tra theo JDK thực tế. Hãy đo bằng **JDK Flight Recorder (JFR)**, thread dumps và metrics trước khi refactor.

### 18.1. Đừng sửa nhầm vấn đề

Nếu performance tệ sau khi bật virtual threads, có thể do:

- DB connection pool chỉ có vài kết nối.
- HTTP client có giới hạn đồng thời hoặc không hỗ trợ I/O theo cách tối ưu.
- CPU-bound code/GC quá tải.
- Một lock bị contention.
- Native call hoặc các dạng pinning còn tồn tại.
- Task bị giữ trong queue hoặc chờ semaphore.
- Logging/tracing tạo allocation quá nhiều.

Không mặc định cứ thấy throughput giảm là phải tăng “virtual pool size”. Với virtual-thread-per-task, tham số pool như vậy có thể không tồn tại hoặc không phù hợp.

## 19. Quan sát virtual threads và tài nguyên thật

Một dashboard chỉ hiển thị số virtual threads không đủ. Nên quan sát:

| Đại lượng | Ý nghĩa |
|---|---|
| Task accepted/rejected | Biết biên nhận việc có đang quá tải |
| Task in-flight / waiting | Số công việc đang ở hệ thống |
| Remote calls in-flight | Có chạm giới hạn downstream không |
| Semaphore available permits/waiting | Thấy điểm nghẽn theo tài nguyên |
| Connection pool active/idle/pending | Thấy tải DB/HTTP |
| CPU usage/carrier saturation | Biết giới hạn CPU |
| GC pause, heap, allocation | Biết chi phí payload/thread locals |
| Latency p50/p95/p99 | Biết trải nghiệm thực tế |
| Errors, timeouts, retries | Đánh giá chất lượng xử lý |
| Virtual thread pinning events | Xác định blocking không unmount phù hợp |

JDK có những công cụ giúp phân tích:

- **JFR** để theo dõi các sự kiện virtual thread, pinning và blocking theo runtime/version.
- `jcmd` với thread dump thích hợp cho virtual threads trên JDK hỗ trợ.
- JVM/JMX metrics và dữ liệu của Spring/Micrometer.
- Logging có correlation ID, không log bừa toàn bộ `ThreadLocal` hay request context.

Tùy JDK, tên và tập sự kiện JFR có thể thay đổi. Luôn tham khảo tài liệu của chính JDK triển khai thay vì copy một lệnh cũ rồi giả định nó quan sát đủ mọi virtual threads.

## 20. Thí nghiệm so sánh đúng và sai: đo điều gì?

Khi review một PR chuyển từ `ThreadPoolTaskExecutor` platform threads sang virtual threads, nên xác định một bài đo có thể lặp lại:

1. Cùng số request đến, cùng payload, cùng downstream capacity.
2. Có baseline platform-thread pool cũ.
3. Có phiên bản `newVirtualThreadPerTaskExecutor()` hoặc Spring `SimpleAsyncTaskExecutor` virtual.
4. Không dùng fixed virtual pool chỉ để “giữ cấu hình maxPoolSize tương tự”.
5. Đặt cùng HTTP/DB timeouts, quota, authentication, logging/tracing.
6. Đo throughput và latency distribution, không chỉ thời gian một tác vụ.
7. Đo DB connection pending, heap/GC, các request bị từ chối, retry volume.
8. Kiểm tra shutdown/cancel khi đang có tải.
9. Kiểm tra thread-local context và bảo mật khi đổi execution model.
10. Chỉ tăng concurrency nếu tài nguyên thật sự chịu được.

Đừng làm benchmark một lượt task CPU đơn lẻ rồi tuyên bố virtual threads chậm/nhiều hơn; lợi ích thiết kế nằm ở workload **nhiều tác vụ chờ I/O**.

### 20.1. Giới hạn chính xác tài nguyên nào?

Một thiết kế có thể có ba mức kiểm soát độc lập:

```text
Incoming requests
   |
   +-- admission limit / queue size / deadline
   |
   v
Virtual-thread-per-task execution
   |
   +-- database connection pool
   +-- HTTP remote semaphore / rate limiter
   +-- CPU service executor / bulkhead
   |
   v
Return result or timeout / reject
```

Nếu mỗi mức giới hạn không có owner/trạng thái rõ ràng, lỗi thường là **nhiều tầng queue**, gây latency cao và khó hủy. Đặt giới hạn tại đúng nghẽn, đo số chờ, giới hạn thời gian chờ, và chọn phản hồi quá tải phù hợp.

## 21. Các hiểu lầm thường gặp

| Nhầm lẫn | Sự thật |
|---|---|
| Virtual threads vẫn cần fixed pool để tái sử dụng | Chúng được thiết kế rẻ và thường tạo mới mỗi task |
| `newVirtualThreadPerTaskExecutor()` là fixed pool ngầm | Nó tạo virtual thread mới cho mỗi task, không giới hạn số lượng bằng worker pool |
| Muốn giới hạn DB thì giới hạn tổng virtual threads | Nên giới hạn DB connections/concurrent calls và chờ có timeout |
| Dùng `Semaphore` nghĩa là đang pool virtual threads | Semaphore giữ số permit, không tái sử dụng thread |
| Càng nhiều virtual threads CPU càng nhanh | CPU-bound vẫn giới hạn bởi core/algorithms |
| Virtual threads làm I/O bên ngoài nhanh hơn | Chúng giúp tận dụng carrier khi chờ, không thay đổi tốc độ remote server |
| Virtual threads không có ThreadLocal | Vẫn có, nhưng phải cân nhắc footprint/context |
| Chuyển `@Async` sang virtual tự lan truyền SecurityContext | Không; cần propagation/authorization policy |
| Bật `spring.threads.virtual.enabled` sẽ thay mọi custom executor | Không; phải xem bean selection và auto-configuration |
| Các property pool size của Boot vẫn điều khiển số virtual workers | Không theo mô hình SimpleAsyncTaskExecutor của Boot |
| `setConcurrencyLimit(10)` tạo pool 10 virtual threads | Không, đây là throttle, không phải thread reuse |
| `cancel(true)` luôn dừng I/O/rollback | Interrupt là hợp tác, không đảm bảo rollback |
| Java 21 và Java 24 có cùng vấn đề synchronized pinning | JDK 24 đã có JEP 491 giảm pinning liên quan monitor |
| Virtual threads giữ JVM sống như non-daemon workers | Virtual threads là daemon |
| Không pool nghĩa là tạo vô hạn request mà không throttle | Admission, memory, connection pools và timeout vẫn bắt buộc |

## 22. Bài tập thực hành

### Bài 1 — Phân biệt mục đích

Một ứng dụng có `newFixedThreadPool(32)` để tránh tạo quá nhiều OS threads. Khi lên Java 21, developer thay thread factory bằng `Thread.ofVirtual().factory()` nhưng vẫn giữ 32 worker. Vì sao việc này không tận dụng đúng virtual threads?

**Gợi ý:** Đã giữ mô hình worker reuse và queue cũ; trong khi virtual threads rẻ, thích hợp cho một task/thread. Nên dùng per-task executor và kiểm soát tài nguyên hữu hạn riêng.

### Bài 2 — Giới hạn DB

Hệ thống có DB connection pool 10 connections nhưng 1000 request đang chờ query. Có nhất thiết phải tạo fixed virtual pool 10 worker không?

**Gợi ý:** Không. Connection pool đã giới hạn số truy vấn giữ kết nối. Cần timeout/admission control để tránh giữ quá nhiều task chờ, không thêm throttle trùng mà thiếu lý do.

### Bài 3 — Semaphore

Trong demo `VirtualThreadsNotPooledDemo` có bốn virtual threads nhưng semaphore chỉ có hai permits. Vì sao `Số virtual threads: 4` đồng thời với `Đồng thời tối đa: 2` không mâu thuẫn?

**Gợi ý:** Hai đại lượng mô tả threads đã tạo và số task đang giữ quyền vào một tài nguyên, không phải cùng một giới hạn.

### Bài 4 — Spring @Async

Một bean `SimpleAsyncTaskExecutor` được cấu hình `setVirtualThreads(true)`, nhưng `@Async` lại trỏ tới bean khác. Method có nhất thiết dùng virtual threads không?

**Gợi ý:** Không. Kiểm tra tên executor, proxy, `@EnableAsync` và quan sát `Thread.currentThread().isVirtual()` trong worker.

### Bài 5 — ConcurrencyLimit

`executor.setConcurrencyLimit(5)` trên `SimpleAsyncTaskExecutor` có nghĩa năm virtual threads được tái sử dụng mãi không?

**Gợi ý:** Không. Giới hạn số lượt thực thi đồng thời, mỗi task vẫn có thread mới. Khi đạt giới hạn, caller có thể bị block hoặc rejection theo version/policy.

### Bài 6 — Throughput không cải thiện

Chuyển 500 platform-thread requests thành 5000 virtual-thread requests nhưng throughput DB không tăng. Nêu ba nguyên nhân có thể.

**Gợi ý:** DB connection pool, khả năng xử lý của DB, lock contention/query cost, remote latency, CPU hoặc rate limits vẫn là điểm nghẽn.

### Bài 7 — Daemon lifecycle

Một chương trình chỉ khởi chạy virtual threads rồi `main` kết thúc, không `join/get/close`. Có bảo đảm task đã hoàn tất?

**Gợi ý:** Không. Virtual threads là daemon; cần lifecycle/point of completion rõ ràng. Spring Boot có `spring.main.keep-alive` cho tình huống cần giữ ứng dụng sống.

### Bài 8 — Pinning theo JDK

Tại sao lời khuyên “mọi `synchronized` gây pinning nghiêm trọng” không còn chính xác như một khẳng định chung với JDK 24+?

**Gợi ý:** JEP 491 đã cải thiện monitor synchronization của virtual threads. Vẫn đo JFR và kiểm tra native/foreign blocking phù hợp với runtime thực tế.

### Bài 9 — Chạy Java 21

Chạy `VirtualThreadsNotPooledDemo.java`, giải thích vì sao các dòng task được in theo ID dù worker thực thi không nhất thiết theo thứ tự đó.

**Gợi ý:** Main duyệt `List<Future<String>>` theo thứ tự submit rồi mới in kết quả; `get()` đợi từng task hoàn tất.

### Bài 10 — Chạy Spring demo

Chạy `SpringVirtualTaskDemo.java`; giải thích vì sao hai lần gọi `@Async("virtualExecutor")` có thể trả về `threadId` khác nhau, và vì sao code gọi `this.inspect(...)` bên trong bean không thể dùng làm bằng chứng.

**Gợi ý:** `SimpleAsyncTaskExecutor` tạo thread mới cho mỗi task, nhưng lời gọi async phải đi qua Spring proxy; self-invocation có thể bỏ qua advice.

### Bài 11 — Quá nhiều task chờ

Một endpoint nhận 200.000 requests rồi tạo cùng số virtual threads đang chờ `Semaphore(10)` vô thời hạn. Có thể gặp lỗi gì dù không pool virtual threads?

**Gợi ý:** Heap/memory/queueing/cancellation/backpressure. Cần admission control, timeout `tryAcquire`, rate limit và phản hồi quá tải.

### Bài 12 — CPU-bound work

Một ứng dụng tính toán mã hóa nặng chiếm toàn bộ CPU. Có nên tăng số virtual threads lên một triệu để tăng tốc không?

**Gợi ý:** Không. Đo workload, song song CPU thực tế, thuật toán và dùng executor/bulkhead theo tài nguyên CPU nếu phù hợp.

## 23. Checklist triển khai trên Spring Framework

1. **Java 21+** đã được dùng ở runtime và release target; virtual threads không phải tính năng chuẩn Java 17.
2. Đã xác định workload chủ yếu **chờ I/O** thay vì CPU-bound.
3. Không tạo `newFixedThreadPool(N, Thread.ofVirtual().factory())` chỉ để tái hiện pool platform threads.
4. Đã chọn `newVirtualThreadPerTaskExecutor()` hoặc Spring `SimpleAsyncTaskExecutor.setVirtualThreads(true)` khi muốn mỗi task có virtual thread riêng.
5. Có owner và lifecycle rõ cho executor, không tạo executor vô hạn theo từng request.
6. Kiểm soát concurrency tại tài nguyên thật: DB connection pool, HTTP quota, semaphore/bulkhead.
7. Có timeout, admission control và chính sách reject/backpressure để không giữ vô hạn tasks chờ.
8. Không nhầm `setConcurrencyLimit` của Spring với pooling; biết nó có thể block caller khi đầy.
9. `@Async` có `@EnableAsync`, đúng bean executor và lời gọi qua Spring proxy.
10. Nếu dùng Boot virtual threads, đã kiểm tra auto-configuration và custom executor selection.
11. Nếu dùng `@Scheduled`/`SimpleAsyncTaskScheduler`, hiểu fixed-delay chạy trên scheduler thread đơn.
12. Có chính sách interruption/cancellation, shutdown/drain và không dựa vào daemon tasks để bảo đảm hoàn tất.
13. Context propagation, security và transaction được kiểm tra riêng ở các ranh giới thread.
14. Dữ liệu `ThreadLocal` nhỏ và được dọn; không giữ payload lớn cho hàng loạt tasks.
15. Đã kiểm tra JDK version và các tình huống pinning, không áp nguyên cảnh báo JDK 21 lên mọi JDK 24+.
16. Đo throughput, p95/p99, CPU, DB connections, memory, backlog và JFR trước/sau.
17. Test với blocking I/O, overload, timeout và lỗi, không chỉ in `isVirtual()==true`.
18. Không tuyên bố virtual threads giúp xử lý “exactly once”, distributed locking hoặc job persistence.

## 24. Tổng kết

- **Virtual threads không nên được pool chỉ để giống platform threads.** Tạo virtual thread mới theo mỗi task là mô hình được Java 21 thiết kế hỗ trợ.
- **`Executors.newVirtualThreadPerTaskExecutor()`** và **`SimpleAsyncTaskExecutor.setVirtualThreads(true)`** thể hiện ý đồ thread-per-task; việc giữ một đối tượng executor để quản lý lifecycle không đồng nghĩa pooling.
- **Giới hạn số truy cập vào tài nguyên hữu hạn bằng cơ chế tương ứng**: `Semaphore`, connection pool, rate limiter, bulkhead và timeout.
- **Không có nghĩa “unbounded admission”.** Virtual threads nhẹ nhưng mỗi task vẫn có memory, payload, timeout, tracing, queue và downstream cost.
- **Spring Boot virtual-thread mode thay đổi kiểu auto-configured executor**, không bảo đảm mọi custom pool đã thay đổi; phải xác minh bean thực tế.
- **Không mặc định virtual threads làm CPU nhanh hơn**, không thay thế cancellation, context propagation, security và transaction design.
- **JDK version rất quan trọng**: Java 21 chuẩn hóa virtual threads; JDK 24 cải thiện `synchronized` pinning theo JEP 491.
- **Đo và kiểm thử bằng điều kiện có thể xác minh**: nhiều virtual threads, giới hạn resource concurrency, theo dõi overload và graceful shutdown.

### Tài liệu chính thức

- [JEP 444 — Virtual Threads (Java 21)](https://openjdk.org/jeps/444)
- [Oracle JDK 21 — Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
- [Oracle JDK 21 API — Executors.newVirtualThreadPerTaskExecutor](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html)
- [Oracle JDK 21 API — Thread](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html)
- [Oracle JDK 21 API — Semaphore](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Semaphore.html)
- [Spring Framework 6.2.7 API — SimpleAsyncTaskExecutor](https://www.springframework.org/spring/docs/6.2.7/javadoc-api/org/springframework/core/task/SimpleAsyncTaskExecutor.html)
- [Spring Framework API — SimpleAsyncTaskScheduler](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/scheduling/concurrent/SimpleAsyncTaskScheduler.html)
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Boot Reference — Task Execution and Scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)
- [Spring Boot Reference — Virtual Threads and Application Lifecycle](https://docs.spring.io/spring-boot/reference/features/spring-application.html)
- [JEP 491 — Synchronize Virtual Threads without Pinning (JDK 24)](https://openjdk.org/jeps/491)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-006">Vẫn giới hạn truy cập tài nguyên hữu hạn như kết nối và dịch vụ đích</a>

<details>
<summary>Xem chi tiết</summary>

# Vẫn giới hạn truy cập tài nguyên hữu hạn như kết nối và dịch vụ đích

## 1. Luồng ảo không làm tăng năng lực của cơ sở dữ liệu hay hệ thống đích

**Virtual threads (luồng ảo)** được hỗ trợ chính thức từ **Java 21**. Chúng cho phép lập trình theo phong cách “một tác vụ, một luồng” mà không phải chiếm một luồng hệ điều hành (*platform thread*) trong toàn bộ thời gian chờ I/O. Trong ứng dụng Spring, virtual threads có thể giúp xử lý nhiều request hoặc công việc blocking đồng thời hơn, giảm chi phí giữ hàng nghìn platform threads chỉ để chờ.

Nhưng điều này **không** có nghĩa các tài nguyên bên ngoài trở nên vô hạn:

- **Database** vẫn có số kết nối cho phép, CPU, IOPS, lock, buffer pool và ngưỡng giao dịch đồng thời.
- **JDBC connection pool** như HikariCP vẫn giới hạn số connection được cấp phát. Luồng ảo thứ 101 không tự tạo được connection thứ 101 nếu `maximumPoolSize=10`.
- **HTTP client** vẫn có connection limit, timeout, DNS, TLS, socket, ephemeral ports và quota của server đích.
- **Dịch vụ thứ ba** vẫn có rate limit (request/giây), giới hạn số requests đang xử lý, quota toàn tenant và khả năng chịu lỗi.
- **CPU, RAM, đĩa, network bandwidth, memory allocation** của chính ứng dụng và downstream vẫn hữu hạn.
- **Transaction/lock** vẫn có thể cạnh tranh hoặc deadlock. Thread ảo chỉ làm việc chờ nhẹ hơn ở phía JVM, không khiến câu SQL chạy nhanh hơn hoặc transaction trở nên ngắn hơn.

**Kết luận đầu tiên:** virtual threads giúp ứng dụng **chờ** nhiều tác vụ blocking đồng thời với chi phí thread thấp hơn; chúng không làm database, API đối tác hay network phục vụ nhanh hơn. Khi bỏ giới hạn do platform-thread pool tạo ra, một bottleneck **ở downstream** có thể lộ rõ hơn.

Bài này hướng dẫn nhận diện ngưỡng tài nguyên, giới hạn concurrency theo từng dependency, sử dụng `Semaphore`/connection pool, đặt timeout và backpressure, cấu hình Spring, kiểm thử bằng Java 21 và đánh giá bằng metric.

### Điều không nên tối ưu trước tiên

Không bắt đầu bằng cách “tăng tối đa virtual threads lên 100.000” hay “tăng HikariCP lên 1.000” chỉ vì tải cao. Trước tiên cần biết:

1. Công việc đang **chờ tài nguyên nào**?
2. Tài nguyên đó có **bao nhiêu suất phục vụ đồng thời**?
3. Chờ bao lâu thì request không còn giá trị?
4. Khi vượt ngưỡng thì **chờ có hạn, từ chối, retry hay xếp hàng bền vững**?
5. Giới hạn này áp dụng **trên một JVM, một pod hay toàn cluster**?

## 2. Cơ chế virtual thread: carrier được giải phóng, kết nối DB thì không

Một virtual thread vẫn là đối tượng `java.lang.Thread`. Java runtime lập lịch virtual threads chạy trên một nhóm platform threads gọi là **carrier threads**. Khi virtual thread thực hiện blocking operation được hỗ trợ, runtime có thể tạm dừng (*unmount*) nó khỏi carrier; carrier được dùng để chạy virtual thread khác.

```text
REQUEST A                           REQUEST B
    |                                   |
virtual thread A                  virtual thread B
    |                                   |
được mount trên carrier          được mount trên carrier
    |                                   |
gọi I/O blocking được hỗ trợ     chạy mã Java của B
    |
A bị park/unmount
    |
carrier có thể chạy B hoặc C
    |
I/O có kết quả
    |
A tiếp tục trên một carrier khả dụng
```

Sự tối ưu nằm ở **việc không giữ một platform thread trong khi chờ**. Nhưng nếu I/O đó là một câu SQL:

```text
Virtual thread A
    |
    +--> HikariDataSource.getConnection()
              |
              +--> cần một JDBC Connection thật
                         |
                         +--> Database server chạy SQL
```

Trong suốt thời gian transaction đang dùng connection, connection đó vẫn thuộc về một lời gọi/transaction, bất kể virtual thread có đang mount trên carrier hay không. Runtime Java không thể cấp thêm năng lực SQL chỉ bằng cách unmount virtual thread.

**Hai trạng thái cùng đúng:**

- Virtual thread đang bị park, dùng rất ít tài nguyên hệ điều hành để chờ.
- Một connection, transaction, lock hoặc request slot của hệ thống khác vẫn **đang bị chiếm**.

Do đó, “số thread hoạt động” và “số request thật đang xử lý tại dependency” là **hai loại metric khác nhau**.

### Vì sao virtual threads thường giúp throughput hơn latency?

Khi nhiều tác vụ ngắn có thời gian chờ I/O lớn, việc giữ platform thread cho mỗi request giới hạn số request có thể phục vụ đồng thời. Virtual threads giảm rào cản đó. Nhưng độ trễ của **một** truy vấn DB vẫn phụ thuộc DB, lock, network và dữ liệu. Nếu DB đã quá tải, mở thêm request đồng thời có thể làm **latency p99 tệ hơn** và throughput không tăng.

Theo tài liệu Java, virtual threads là công cụ tăng khả năng mở rộng cho kiểu ứng dụng blocking I/O, **không phải “thread nhanh hơn”**.

## 3. Ba mức giới hạn phải nhìn cùng lúc

Một hệ thống dùng virtual threads thường bị kiểm soát bởi ít nhất ba mức:

| Mức | Ví dụ | Điều bị giới hạn | Thước đo |
| --- | --- | --- | --- |
| **JVM/application** | CPU quota, heap, virtual-thread state, backlog | Khả năng xử lý và giữ các request | CPU, heap, active tasks, GC, p99 |
| **Client resource** | HikariCP 10 connections, HTTP connection pool 20 | Số thao tác chiếm tài nguyên ở phía client | active/idle/pending, acquisition wait |
| **Downstream service** | DB max connections, API 50 concurrent calls, 200 RPS | Năng lực thực của server/tenant | 429/503, queue, server CPU, latency |

Một tầng có thể giới hạn trước tầng khác. Ví dụ:

```text
10.000 virtual threads có thể được tạo
                 |
                 v
HikariCP maximumPoolSize = 12
                 |
         +-------+-------+
         |               |
    12 connections    phần còn lại đợi pool
         |
         v
Database chỉ chịu được 8 truy vấn nặng đồng thời
         |
         v
Dù pool có 12, DB vẫn có thể quá tải.
```

Số 10.000, 12 và 8 ở đây là **giả định để giải thích**, không phải khuyến nghị cấu hình. Điểm cần nhớ là không có ánh xạ “một virtual thread tương đương một connection”. Virtual threads có thể tồn tại trong khi **chưa được** cấp connection, hoặc sau khi đã trả connection.

**Phân biệt giới hạn “số connection” và “số query DB đang chạy”:** một JDBC connection có thể đang idle trong pool, đang đợi khóa, đang thực hiện query hoặc đang giữ transaction mở. `maximumPoolSize` giới hạn connection ở pool, nhưng không trực tiếp bảo đảm throughput hoặc số truy vấn nặng tối ưu ở DB.

## 4. Khi HikariCP cạn kết nối, điều gì thực sự xảy ra?

Trong ứng dụng Spring Boot có JDBC/JPA và HikariCP, thuộc tính thường dùng:

```properties
# Ví dụ, cần điều chỉnh theo capacity thực tế:
spring.datasource.hikari.maximum-pool-size=12
spring.datasource.hikari.connection-timeout=1500
```

- `maximum-pool-size=12` giới hạn số connection trong **pool của instance này**, gồm connection đang dùng và đang idle.
- `connection-timeout=1500` nghĩa caller có thể chờ tối đa khoảng 1.500 ms để lấy connection từ HikariCP trước khi báo lỗi acquisition (khi không có connection khả dụng). Nó **không phải** giới hạn tổng thời gian một SQL query chạy.
- Nếu pool hết connection, các virtual threads gọi `getConnection()` có thể chờ; chúng có thể rẻ hơn platform threads khi park, **nhưng lượng request đang chờ vẫn có thể tăng**.
- Sau timeout, HikariCP thông thường ném exception thuộc họ `SQLException` ở lớp JDBC; qua Spring Data/JPA, exception được dịch hoặc bọc theo stack cụ thể.

### Kịch bản thực tế

Giả sử 500 request Spring MVC cùng cần JDBC, trong khi pool chỉ có 12 connection. Khi cả 12 connection đang bận, tối đa 488 request có thể chưa lấy được connection ở thời điểm đó, tùy trạng thái xử lý. Đây là **backlog**, không phải “488 truy vấn SQL đang đồng thời chạy”.

Thêm virtual threads chỉ khiến JVM có thể giữ nhiều lời gọi chờ connection hơn mà không cần 500 platform threads. Nó không tăng khả năng thực thi SQL.

### Vì sao không tăng pool size vô hạn?

Database có giới hạn về memory mỗi connection, số backend processes/threads, transaction locks, CPU và I/O. Thêm 50 connections vào mỗi pod có thể khiến DB tranh chấp nặng hơn.

Nếu có 8 pods, mỗi pod cấu hình HikariCP tối đa 12 connections, tổng số connections **có thể** lên tới gần 96, chưa tính các ứng dụng khác và connection quản trị. Số connection thực tế phụ thuộc nhu cầu và chính sách pool; giới hạn cấu hình không có nghĩa lúc nào cũng mở đủ 96.

**Thiết kế đúng:** xét DB max_connections, các application pools khác, headroom cho vận hành, số pod khi autoscale và độ dài transaction. Đo `active`, `idle`, `pending`, `acquire time` trước khi quyết định tăng kết nối.

### Có cần thêm Semaphore quanh HikariCP không?

**Không mặc định cần.** Pool connection đã đóng vai trò một bộ giới hạn concurrent connection. Thêm một `Semaphore` cùng capacity ngay trước `getConnection()` có thể tạo **hai hàng đợi** và khó phân tích timeout mà chưa tăng lợi ích.

Chỉ thêm tầng admission nếu có **mục tiêu riêng**: giới hạn một truy vấn rất nặng xuống dưới mức toàn pool, đảm bảo quota theo tenant, tách workload ưu tiên hoặc từ chối sớm để không dồn quá nhiều request vào pool. Khi đó hãy định nghĩa owner, metric và timeout từng tầng rõ ràng.

## 5. HTTP và dịch vụ đích: rate limit không giống concurrency limit

Một đối tác có thể ghi trong hợp đồng:

- Không quá **20 request đang xử lý đồng thời**.
- Không quá **100 request/giây**.
- Hoặc chỉ cho **5 connections** được mở từ một client.
- Hoặc có quota **1.000 calls/ngày** trên một API key.

Đây là **bốn quy tắc khác nhau**.

| Khái niệm | Câu hỏi | Công cụ phù hợp |
| --- | --- | --- |
| **Concurrency limit / bulkhead** | Đang có bao nhiêu lời gọi **in-flight**? | `Semaphore`, bulkhead theo dependency |
| **Rate limit** | Có bao nhiêu lời gọi được **khởi tạo trong một khoảng thời gian**? | Token bucket, sliding window, distributed rate limiter |
| **Connection limit** | Có bao nhiêu socket/HTTP connections được dùng? | Giới hạn connection pool/client |
| **Quota** | Đã dùng bao nhiêu lượt trong giờ/ngày/tháng? | Counter bền vững, quota manager |

Một `Semaphore(20)` **không** bảo đảm dưới 100 RPS: nếu mỗi request hoàn tất trong 10 ms, 20 slots có thể phục vụ rất nhiều requests/giây. Ngược lại, rate limiter 100 RPS không ngăn 100 requests đó đều đang chờ 30 giây cùng lúc.

**Cần phối hợp đúng tầng:** đối tác có cả 20 concurrent và 100 RPS thì phải kiểm soát **cả hai** theo hợp đồng, không thay thế một bằng một.

### Retry storm với virtual threads

Khi service đích trả 429/503 và hàng nghìn virtual threads cùng retry ngay lập tức, hệ thống có thể tạo vòng phản hồi xấu:

```text
downstream chậm
     ↓
request giữ permit/connection lâu hơn
     ↓
nhiều virtual threads đang chờ
     ↓
timeouts / 429 / 503 tăng
     ↓
retry không backoff
     ↓
lượng request đổ xuống downstream tăng thêm
     ↓
downstream càng chậm
```

Giải pháp: budget retry, exponential backoff + jitter, đọc `Retry-After` khi áp dụng, circuit breaker, giới hạn concurrency theo từng đối tác, timeout tổng và cơ chế từ chối/bỏ bớt tải hợp lý.

## 6. Vì sao không tạo “pool virtual threads” để giới hạn tài nguyên?

Java khuyến nghị **không pool virtual threads**. Chúng được thiết kế để mỗi tác vụ có thể có một virtual thread riêng, rồi kết thúc khi công việc xong.

Dùng một fixed-size thread pool theo mẫu cũ:

```java
// Không phải cách chính để giới hạn downstream khi đang dùng
// virtual-thread-per-task design:
ExecutorService pool = Executors.newFixedThreadPool(10);
```

`newFixedThreadPool(10)` tạo/giữ **platform worker threads** theo cách thông thường. Nó không tự biến thành một virtual-thread executor chỉ vì ứng dụng đã bật Java 21.

Ngược lại:

```java
ExecutorService executor =
        Executors.newVirtualThreadPerTaskExecutor();
```

Executor này tạo **một virtual thread mới cho mỗi tác vụ** được submit; nó **không tự giới hạn** số tác vụ đồng thời bằng một queue pool 10 workers. Vì vậy muốn giới hạn gọi API đối tác, dùng cơ chế gắn với **API đó**, ví dụ semaphore hoặc rate limiter, chứ không cố biến virtual threads thành một pool 10 threads.

### Tại sao Semaphore là công cụ đúng?

`Semaphore` có số permits hữu hạn. Một task phải lấy được permit trước khi vào đoạn code sử dụng resource, và trả permit trong `finally`.

```java
private final Semaphore partnerSlots = new Semaphore(8);

public Response callPartner(Request input) throws Exception {
    if (!partnerSlots.tryAcquire(200, TimeUnit.MILLISECONDS)) {
        throw new TooBusyException("Partner concurrency exceeded");
    }
    try {
        return partnerClient.call(input);
    } finally {
        partnerSlots.release();
    }
}
```

Đây là **mảnh code nghiệp vụ**, cần khai báo/import `Semaphore`, `TimeUnit`, `Response`, `Request`, `TooBusyException` và `partnerClient`. Không phải chương trình `javac` độc lập.

Ý nghĩa:

- Tối đa 8 lời gọi **đang ở bên trong** khối protected của instance này.
- Task thứ chín chờ permit tối đa 200 ms, sau đó **bị từ chối có kiểm soát**, không chờ vô hạn.
- Tất cả code gọi dependency, kể cả code ném lỗi, phải trả permit qua `finally`.
- Nếu `tryAcquire(timeout)` bị interrupt, cần khôi phục cờ interrupt hoặc truyền `InterruptedException` đúng contract.
- Slot được trả sau khi client call **thật sự kết thúc**, không trả ngay sau khi chỉ gửi request bất đồng bộ.

**Lưu ý cực quan trọng:** đừng lấy permit rồi gọi async API trả ngay `CompletableFuture` và thả permit tức thì nếu network request vẫn chạy. Khi đó semaphore đo thời gian **submit**, không đo khoảng in-flight thực sự. Hãy gắn release vào completion callback được thực thi đúng một lần, hoặc giữ lời gọi đồng bộ nếu đã chọn kiểu blocking virtual thread.

## 7. Ví dụ Java 21 độc lập: nhiều luồng ảo nhưng chỉ hai tác vụ được vào tài nguyên

Thí nghiệm dùng **JDK 21+**, hoàn toàn không phụ thuộc Spring/Maven/DB. Ta tạo 6 tasks trên `Executors.newVirtualThreadPerTaskExecutor()`, nhưng đặt `Semaphore(2)` trước một đoạn giả lập dịch vụ hữu hạn.

Để kết quả xác định, hai task đầu được vào vùng được bảo vệ sẽ đợi một `CountDownLatch`. Những task còn lại có thể đã có virtual thread nhưng chưa lấy được permit. Main chỉ mở chốt sau khi biết chắc hai tasks đã vào.

Tạo tệp **`VirtualThreadFiniteResourceDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class VirtualThreadFiniteResourceDemo {
    public static void main(String[] args) throws Exception {
        final int numberOfTasks = 6;
        Semaphore serviceSlots = new Semaphore(2);
        CountDownLatch firstTwoEntered = new CountDownLatch(2);
        CountDownLatch allowFinish = new CountDownLatch(1);

        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        AtomicBoolean sawVirtualThread = new AtomicBoolean();

        List<Future<Void>> futures = new ArrayList<>();

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                for (int i = 0; i < numberOfTasks; i++) {
                    futures.add(executor.submit(() -> {
                        if (!serviceSlots.tryAcquire(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Timed out waiting for resource");
                        }
                        try {
                            int nowActive = active.incrementAndGet();
                            maxActive.accumulateAndGet(
                                    nowActive, Math::max);
                            sawVirtualThread.set(
                                    Thread.currentThread().isVirtual());

                            firstTwoEntered.countDown();
                            if (!allowFinish.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException(
                                        "Release gate timed out");
                            }
                            completed.incrementAndGet();
                            return null;
                        } finally {
                            active.decrementAndGet();
                            serviceSlots.release();
                        }
                    }));
                }

                if (!firstTwoEntered.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Two tasks did not enter");
                }

                System.out.println(
                        "virtualThreads=" + sawVirtualThread.get());
                System.out.println(
                        "activeBeforeRelease=" + active.get());
                System.out.println(
                        "completedBeforeRelease=" + completed.get());

                allowFinish.countDown();

                for (Future<Void> future : futures) {
                    future.get(5, TimeUnit.SECONDS);
                }
                System.out.println("submittedTasks=" + futures.size());
                System.out.println(
                        "maxConcurrentServiceCalls=" + maxActive.get());
                System.out.println("completed=" + completed.get());
                System.out.println("activeAfter=" + active.get());
            } finally {
                // Mở chốt kể cả khi assertion/timeout thất bại.
                allowFinish.countDown();
            }
        }
    }
}
```

Chạy bằng JDK 21 trở lên:

```bash
javac --release 21 VirtualThreadFiniteResourceDemo.java
java VirtualThreadFiniteResourceDemo
```

**Standard output của đường chạy thành công:**

```text
virtualThreads=true
activeBeforeRelease=2
completedBeforeRelease=0
submittedTasks=6
maxConcurrentServiceCalls=2
completed=6
activeAfter=0
```

### Vì sao output xác định?

1. `ExecutorService` tạo virtual thread cho mỗi task và `Thread.currentThread().isVirtual()` xác nhận worker đang chạy là virtual thread. Không cần đoán tên thread.
2. Chỉ có **hai permits**, nên dù 6 tasks được submit, tối đa hai task cùng được vào đoạn gọi resource.
3. `firstTwoEntered` chỉ được giảm **sau khi đã lấy permit và tăng `active`**. Main đợi latch này về 0, trong lúc `allowFinish` vẫn đóng. Vì vậy `activeBeforeRelease=2`, `completedBeforeRelease=0`.
4. Sau khi main mở chốt, hai task đầu trả permit; bốn task còn lại lần lượt lấy permit và chạy. `maxActive` không vượt 2 trong mọi lịch thực thi.
5. `Future.get(5, TimeUnit.SECONDS)` xác nhận cả sáu task đã hoàn tất. Không dùng một lần `Thread.sleep(100)` để kết luận tasks đã xong.
6. `try-with-resources` đóng virtual-thread executor sau khi tác vụ hoàn tất. Thí nghiệm không tạo ra một virtual-thread pool có 2 workers; giới hạn hai nằm ở **serviceSlots**.

Nếu môi trường CI bị pause quá lâu, các bước có timeout có thể ném exception thay vì in đủ bảy dòng. Đây là **một test báo lỗi đúng**, không phải một lý do để công bố số liệu throughput đoán mò.

### Thí nghiệm chưa chứng minh điều gì?

- Không có database hoặc HTTP thật; permit chỉ **mô phỏng** một tài nguyên hữu hạn.
- Không đo CPU, memory hoặc hiệu năng của virtual threads so với platform threads.
- Không chứng minh nhà cung cấp API sẽ chấp nhận hai requests; mức 2 chỉ do bài demo tự đặt.
- Không chứng minh mọi socket/driver trong hệ thống thật đều unmount hoàn hảo; còn phụ thuộc JDK, driver và đoạn native/blocking.
- Không chứng minh semaphore tự giới hạn **tổng số task đang chờ**: bốn task ngoài vùng service vẫn có thể chờ permit, và một hệ thống production có thể phát sinh hàng trăm nghìn task chờ nếu không thêm admission control.

Thí nghiệm tập trung đúng bài toán: **số tác vụ chạy trên virtual threads có thể lớn hơn số lời gọi thực sự được phép đi vào một dịch vụ có sức chứa hữu hạn**.

## 8. Phân biệt ba trạng thái: đang chạy, đang chờ permit và đã bị từ chối

Không có đủ permits, một lời gọi có thể:

1. **Được nhận ngay:** permit sẵn có, đi vào dependency.
2. **Chờ có hạn:** permit hết, được chấp nhận nếu permit rảnh trước timeout.
3. **Bị từ chối:** timeout, policy fail-fast hoặc quota buộc trả 429/503/exception phù hợp.

```text
               incoming work
                    |
                    v
             Admission policy
               /           \
           reject         accepted
                             |
                             v
                       Semaphore
                      /         \
                  acquire     wait <= deadline
                    |            |
                    v            +--> timeout -> fail fast
              call downstream
                    |
                    v
                 finally
                release()
```

Số permits **không phải tổng capacity cho mọi bước pipeline**. Một task có thể lấy permit đối tác rồi đợi DB pool, khiến slot đối tác bị giữ dù chưa thực sự gọi partner. Hãy đặt permit **sát đoạn cần bảo vệ**, không ôm quá nhiều logic unrelated vào critical section.

### Khi một trăm nghìn virtual threads cùng chờ permit

Virtual threads có footprint nhỏ hơn platform threads, nhưng không miễn phí. Mỗi task vẫn có object, stack state, request metadata, closure, timeout, logging context và có thể giữ tài nguyên application khác.

Nếu upstream tiếp tục sinh request không giới hạn, hàng đợi virtual threads chờ `Semaphore.acquire()` có thể làm tăng memory, latency và số request vô ích. Do đó **bounded concurrency** cần đi kèm **bounded waiting/admission**.

Một thiết kế thực tế có thể giới hạn:

- Tối đa 8 calls đang xử lý tại partner.
- Tối đa 100 request đang chờ được nhận vào hệ thống (ví dụ queue/admission gate).
- Mỗi request chỉ được chờ permit 200 ms.
- Deadline tổng 1 giây.
- Nếu không thể phục vụ, trả lỗi quá tải có chủ đích thay vì làm JVM chờ vô hạn.

Các con số này là **minh họa**, không phải giá trị mặc định nên dùng. Tối ưu dựa vào SLA, latency distribution, tải nền và quota thực tế của downstream.

## 9. Không giữ permit sai vòng đời khi dùng CompletableFuture

Dù chương này tập trung vào virtual threads, ứng dụng Spring đôi khi kết hợp `@Async`, `CompletableFuture` và HTTP async client. Khi đó, phải kiểm tra **thời điểm resource thực sự được dùng**.

**Lỗi phổ biến:**

```java
semaphore.acquire();
try {
    return httpClient.sendAsync(request, responseBodyHandler);
} finally {
    semaphore.release(); // SAI: request có thể vẫn đang chạy!
}
```

Đây là **snippet minh họa**, không có các biến/import đầy đủ. Hàm trả `CompletableFuture` có thể trả ngay khi request vừa được submit. Nhả permit tại `finally` làm concurrency limit trở thành giới hạn **thời gian gửi lệnh**, không còn bảo vệ concurrent HTTP calls thật.

**Phương án đúng về ý tưởng:**

```java
if (!semaphore.tryAcquire()) {
    return CompletableFuture.failedFuture(
            new IllegalStateException("Service busy"));
}
try {
    return httpClient.sendAsync(request, responseBodyHandler)
            .whenComplete((response, error) -> {
                semaphore.release(); // Sau khi async call hoàn tất.
            });
} catch (RuntimeException ex) {
    semaphore.release(); // Submit thất bại đồng bộ.
    throw ex;
}
```

Đây vẫn là snippet cần imports/biến/client hỗ trợ. **Giả định** `sendAsync(...)` hoặc việc gắn callback không ném lỗi bất thường bên ngoài phạm vi catch; production nên gom quản lý permit vào một abstraction được test kỹ, xử lý cancellation/timeout và đảm bảo release **đúng một lần**.

Một điều kiện biên: nếu bạn chỉ gắn callback vào stage phụ thuộc rồi **cancel stage phụ thuộc** trước khi HTTP operation thực sự hoàn tất, stage đó có thể complete mà request nguồn vẫn tiếp tục. Khi cần đúng **in-flight network request**, phải ràng buộc permit với completion/cancellation handle của **operation gốc**, kiểm tra semantics client và giữ quyền kiểm soát cleanup. Không dùng mỗi `CompletableFuture.isDone()` của một stage đã timeout để tuyên bố tài nguyên phía remote đã được trả.

Với virtual threads, mã đồng bộ kiểu:

```java
semaphore.acquire();
try {
    return blockingHttpCall();
} finally {
    semaphore.release();
}
```

dễ giữ semantics hơn **nếu** `blockingHttpCall()` chỉ trả sau khi operation thật đã hoàn thành, timeout hoặc bị hủy và cleanup đúng. Đừng chuyển mọi HTTP async API sang blocking chỉ để có semaphore nếu bạn đang ở **reactive event-loop**; chặn event-loop có thể gây starvation.

## 10. Spring Boot: bật virtual threads không đổi hạn mức của JDBC pool

Với ứng dụng Spring Boot sử dụng JDK 21+, một tùy chọn cấu hình:

```properties
spring.threads.virtual.enabled=true

# Những thuộc tính HikariCP vẫn giới hạn tài nguyên JDBC:
spring.datasource.hikari.maximum-pool-size=12
spring.datasource.hikari.connection-timeout=1500
```

Trong Spring Boot 3.5 (khi điều kiện auto-configuration đáp ứng):

- Khi virtual threads được bật, auto-configured `AsyncTaskExecutor` thường là `SimpleAsyncTaskExecutor` dùng virtual threads, thay vì `ThreadPoolTaskExecutor` dựa trên platform-thread pool.
- Nếu có một `Executor` bean tùy chỉnh, `AsyncConfigurer` hoặc cấu hình đặc biệt, việc chọn executor có thể thay đổi. Cần kiểm tra **bean thật** thay vì mặc định mọi lời gọi `@Async` chuyển sang virtual threads.
- Những thuộc tính cấu hình thread pool dành cho executor auto-configured dựa trên platform threads có thể **không còn tác dụng** khi implementation virtual-thread-per-task được chọn.
- HikariCP vẫn giữ quy tắc về connections và timeout của nó. Virtual threads không đổi `spring.datasource.hikari.maximum-pool-size` thành “unlimited”.
- Servlet connector, HTTP client, database driver hoặc các thư viện khác có thể có cấu hình concurrency riêng. Một flag ứng dụng không thay thế việc hiểu từng integration.

### Ví dụ cấu hình chỉ minh họa, không phải khuyến nghị kích thước

```properties
spring.threads.virtual.enabled=true

# DB pool riêng cho mỗi ứng dụng instance
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.connection-timeout=1000

# Một số timeout HTTP client phải cấu hình tại chính client;
# các key dưới đây KHÔNG phải key chuẩn chung cho mọi HTTP client.
```

Không tự ý thêm một thuộc tính `spring.http.timeout` rồi tin rằng mọi RestClient, WebClient, Apache HttpClient và SDK đối tác đều nhận nó. Mỗi client có API connect/request/response/read timeout riêng, phải đọc tài liệu của implementation đang dùng.

### Chọn rõ executor khi cần cấu hình tùy chỉnh

Trong Spring Framework 6.2, bạn có thể tự khai báo bean:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class VirtualExecutorConfiguration {

    @Bean("virtualIoExecutor")
    public SimpleAsyncTaskExecutor virtualIoExecutor() {
        SimpleAsyncTaskExecutor executor =
                new SimpleAsyncTaskExecutor("io-vt-");
        executor.setVirtualThreads(true); // Java 21+.
        return executor;
    }
}
```

Đây là **class cấu hình Spring**; cần dependencies phù hợp, application context và JDK 21+. `SimpleAsyncTaskExecutor` không tái sử dụng thread theo pool mà tạo thread cho task. Lập trình viên vẫn phải kiểm soát concurrent calls tới service đích bằng bulkhead/connection pool.

**Lưu ý:** `SimpleAsyncTaskExecutor.setConcurrencyLimit(...)` có thể giới hạn concurrent tasks của executor và chặn người submit theo cấu hình. Nhưng đó là ngưỡng **chung cho executor**, không thay thế hạn mức riêng của DB, partner A và partner B. Nếu bạn muốn bảo vệ từng downstream độc lập, nên đặt giới hạn **gần client/service adapter** tương ứng. Không dùng một concurrency limit toàn cục để vô tình khiến dịch vụ A chậm làm mọi yêu cầu đến B cũng chậm.

## 11. Ví dụ Spring service: đặt bulkhead ngay trước API đối tác

Một service dùng blocking client có thể giữ code đồng bộ, rồi được gọi từ Spring MVC request thread/virtual-thread task theo cấu hình application. Đoạn dưới là **snippet tích hợp**, không phải chương trình JDK standalone vì cần Spring components và một `PartnerGateway` do ứng dụng định nghĩa.

```java
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

@Service
public class PartnerService {
    private final Semaphore partnerSlots = new Semaphore(8, true);
    private final PartnerGateway gateway;

    public PartnerService(PartnerGateway gateway) {
        this.gateway = gateway;
    }

    public String lookup(String key) {
        boolean acquired;
        try {
            acquired = partnerSlots.tryAcquire(
                    200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for partner", ex);
        }

        if (!acquired) {
            // Map thành 429/503 hoặc lỗi tầng service theo API contract.
            throw new IllegalStateException(
                    "Partner concurrency limit reached");
        }

        try {
            return gateway.lookup(key); // Blocking I/O có timeout riêng.
        } finally {
            partnerSlots.release();
        }
    }
}
```

**Giải thích:**

- Semaphore là một instance state của Spring singleton bean; tối đa 8 concurrent calls **trong instance này**.
- Đặt `tryAcquire(200ms)` giới hạn thời gian chờ một slot. Nếu không có permit, ứng dụng từ chối công việc mà **chưa gọi đối tác**.
- `new Semaphore(8, true)` bật chính sách fairness để giảm khả năng một số waiter bị bỏ đói; fairness có overhead và không bảo đảm mọi callback Java đều xếp thứ tự tuyệt đối ở mọi ranh giới.
- `gateway.lookup()` phải đặt timeout HTTP hợp lý và đóng response/body/resources. Không giữ permit qua cả các công việc DB không liên quan.
- `InterruptedException` cần khôi phục interrupt status; cleanup `finally` đảm bảo trả slot kể cả khi gateway ném lỗi.
- Nếu service client trả dữ liệu streaming, việc trả String không thể biểu diễn hết vòng đời stream. Khi trả stream/response cho nơi khác tiêu thụ, cần sở hữu permit cho tới khi stream/response **thực sự đóng**.

### Nếu dùng @Async?

Có thể gắn `@Async("virtualIoExecutor")` cho một method ở bean khác để chạy blocking method này trên virtual thread và trả `CompletableFuture<T>`. Nhưng:

- Phải gọi **qua Spring proxy**; gọi `this.asyncMethod()` từ cùng bean trong chế độ proxy mặc định có thể bypass `@Async`.
- `@Transactional` của caller không tự truyền transaction sang virtual thread mới; transaction context gắn với thread thực thi và proxy boundary.
- Nếu `@Async` method trả `CompletableFuture`, caller quan sát lỗi qua completion/callback. Đừng cho rằng lỗi trong worker tự ném về thread nộp request.
- Đừng submit công việc vào một executor khác hoặc common pool rồi mặc định Spring flag có hiệu lực ở đó.
- Quy tắc về **số concurrent calls tới partner** vẫn do semaphore/client quản lý, không do sự nhẹ của virtual threads.

## 12. Admission control: giới hạn cả số người chờ, không chỉ người đang dùng

Một semaphore thuần có thể bảo vệ concurrency **bên trong** dependency nhưng vẫn cho hàng triệu virtual threads xếp hàng chờ. Chờ rất đông dẫn đến memory, timeout và latency không còn giá trị nghiệp vụ.

Cần phân biệt hai hợp đồng:

**Bulkhead (execution capacity):** tối đa bao nhiêu request **đang dùng** dịch vụ? Ví dụ 8.

**Admission (backlog capacity):** tối đa bao nhiêu request tổng cộng được phép **đang chờ hoặc chạy**? Ví dụ 108, gồm 8 đang chạy và tối đa 100 chờ.

Một phương án khi có nguồn task chủ động:

```text
Incoming requests
       |
       v
Admission gate (bounded queue / concurrency admission / queue cap)
       |
       +--> vượt giới hạn: reject sớm, trả 429/503
       |
       v
Bulkhead per downstream (Semaphore)
       |
       v
Client connection pool và timeout
       |
       v
Downstream service
```

**Không dùng một Semaphore khác với cùng giá trị mà không hiểu hàng đợi**. Nếu muốn thêm cổng admission, hãy định nghĩa rõ số active+waiting được tính thế nào, khi nào permit được giữ/trả, và liệu HTTP server đã có giới hạn số requests chờ ở tầng khác chưa.

Ví dụ:

- Cổng tổng cộng **100** request cho một loại API.
- Bên trong, chỉ **8** concurrent calls tới partner.
- 92 request còn lại có thể đang chờ hoặc thực thi phần khác.
- Nếu cổng tổng cộng hết, reject ngay, thay vì tạo thêm virtual threads hoặc giữ thêm request contexts.

Thứ tự nhận permit phải tránh deadlock: không giữ DB transaction/connection rồi ngồi chờ partner slot lâu nếu có thể gọi partner trước; không giữ partner slot trong lúc chờ DB connection mà không có lý do nghiệp vụ.

### Quá tải: chờ bao lâu thì hợp lý?

Chọn thời gian chờ từ **deadline tổng của request**, không dùng một timeout độc lập quá lớn ở mỗi tầng. Ví dụ deadline tổng là 800 ms:

```text
Tổng ngân sách: 800ms
  - admission/permit wait: ≤ 100ms
  - connection acquisition: ≤ 100ms
  - downstream call: ≤ 450ms
  - serialization + safety margin: phần còn lại
```

Các con số chỉ minh họa. Timeout của mỗi tầng cần được diễn giải theo API và không tự tạo một deadline xuyên suốt: nếu ba giai đoạn đều cấu hình 800 ms thì tổng request có thể vượt rất xa 800 ms.

Nếu request đã hết deadline trước khi vào dependency, thường nên dừng/huỷ thay vì chờ tiếp và gửi một call vô ích. Các side effects đã bắt đầu không được đảo ngược chỉ bằng việc caller hết hạn; thiết kế idempotency/compensation khi cần.

## 13. Chọn giữa Semaphore, RateLimiter, Connection Pool và CircuitBreaker

| Vấn đề | Biện pháp chính | Không nên kỳ vọng |
| --- | --- | --- |
| HikariCP hết connection | Pool sizing hợp lý + acquisition timeout + giảm thời gian giữ connection | Tăng virtual threads sẽ tăng số connection |
| Đối tác chỉ chịu 8 concurrent calls | `Semaphore(8)` hoặc `SemaphoreBulkhead` | Semaphore tự giới hạn RPS |
| Đối tác chỉ cho 100 requests/giây | Token bucket / RateLimiter | RateLimiter tự chặn 100 calls kéo dài 30s cùng lúc |
| Đối tác liên tục lỗi/timeout | CircuitBreaker + timeout + retry budget | CircuitBreaker tự “sửa” API đích |
| Đường gọi tích tụ backlog | Bounded admission/queue + reject/drop theo SLA | Virtual threads biến backlog vô hạn thành miễn phí |
| Job quá lâu giữ transaction | Tách remote call khỏi transaction khi có thể; transaction timeout, SQL tuning | Carrier thread được unmount thì transaction tự đóng |
| Nhiều pod cùng tăng tải | Quota/limiter dùng shared state hoặc chia ngân sách mỗi pod | Semaphore trong một JVM giới hạn toàn cluster |
| Bị 429/503 | Backoff/jitter, Retry-After, hạn mức và fail-fast | Retry càng nhanh càng tăng thành công |
| CPU-bound computations | Giảm workload, vectorization/caching hoặc giới hạn parallelism theo CPU | Virtual threads làm mã CPU chạy nhanh hơn |

### Resilience4j Bulkhead

Nếu không muốn tự quản lý semaphore thủ công, có thể dùng **Resilience4j SemaphoreBulkhead**. Nó giới hạn số concurrent calls qua một component có cấu hình và hỗ trợ metrics phù hợp với dự án. Đây là thư viện ngoài JDK/Spring core, cần dependency và config riêng.

```java
BulkheadConfig config = BulkheadConfig.custom()
        .maxConcurrentCalls(8)
        .maxWaitDuration(Duration.ofMillis(100))
        .build();

Bulkhead bulkhead = Bulkhead.of("partner-service", config);
```

Đây là **snippet cần import Resilience4j** và một hàm gọi được decorate/execute qua bulkhead. Việc chỉ tạo object `bulkhead` chưa bảo vệ gì cho tới khi bạn thực sự dùng nó quanh đoạn gọi dependency.

**Đừng cài đồng thời ba lớp bulkhead cùng capacity nếu không cần.** Ví dụ admission gate, Resilience4j bulkhead và HTTP pool đều có thể giữ hàng đợi. Mỗi tầng nên có mục tiêu cụ thể, metric riêng và deadline chung.

## 14. Nhiều instance Spring Boot: semaphore cục bộ không giới hạn toàn cluster

Giả sử đối tác chỉ cho **20 requests đồng thời cho một API key**, còn hệ thống của bạn chạy **5 pods**.

Nếu mỗi pod có `Semaphore(20)`, cluster có thể gửi tối đa tới **100 requests** đồng thời (5 × 20), vượt quota của partner. Một Spring singleton bean chỉ là singleton **trong một ApplicationContext/JVM**, không phải singleton trên Kubernetes cluster.

Ba cách kiểm soát phổ biến:

1. **Chia ngân sách cục bộ:** ví dụ tổng 20 chia cho 5 pods thành 4 permits/pod. Đơn giản nhưng phải cập nhật khi autoscale; nếu chỉ 2 pods active thì có thể lãng phí capacity.
2. **Rate limiter/bulkhead chia sẻ:** dùng một coordinator hoặc bộ đếm/lease phân tán có semantics chính xác. Cần chú ý tính sẵn sàng, consistency, network failure và clock.
3. **Gateway/worker tập trung:** mọi traffic đến một dependency đi qua tầng admission/queue chung có quota. Tăng tính kiểm soát nhưng thêm điểm nghẽn cần HA và observability.

Ngay cả khi có distributed limit, không có lời hứa exactly-once cho side effect. Khi request timeout, upstream retry có thể gửi lại công việc đã xử lý ở đối tác. Dùng idempotency key nếu API hỗ trợ, và lưu trạng thái nghiệp vụ có thể khôi phục.

### Autoscaling có thể làm quota tăng theo số pod

HPA tăng từ 5 lên 10 pods khi latency tăng. Nếu mỗi pod vẫn có 20 permits, tổng potential concurrent calls nhảy từ 100 lên 200 trong lúc partner đang chậm. Khi scaling, phải đảm bảo **ngân sách kết nối/quota cluster** được thiết kế có tính đến số replicas tối đa.

Tương tự, mỗi pod cấu hình HikariCP 20 connections, 10 pods có thể mở tới 200 connections; DB còn phải dành chỗ cho read replicas, migration, admin tools và các services khác.

## 15. Giới hạn khác: CPU, memory, pinning và thread-local context

Virtual threads không miễn phí về CPU/RAM:

- Tạo rất nhiều virtual threads và giữ chúng chờ vẫn tốn object/stack state và application request context.
- CPU-bound code vẫn chạy trên carrier/platform threads và cạnh tranh số CPU cores hữu hạn. Không dùng virtual threads như cách “làm tính toán CPU nhanh hơn”.
- Virtual threads có thể dùng `ThreadLocal`, nhưng số thread nhiều hơn có thể nhân chi phí context nếu mỗi thread giữ map lớn, transaction context hoặc object nhạy cảm.
- Nếu gọi native/foreign code hoặc blocking không được JVM unmount thuận lợi, hiệu quả có thể suy giảm.
- Trong **Java 21**, blocking bên trong `synchronized` có thể tạo tình huống virtual thread bị **pinned** vào carrier. Ở **Java 24+**, JEP 491 đã cải thiện vấn đề pinning do `synchronized`; tuy nhiên vẫn cần quan sát các trường hợp native/foreign blocking và đo JDK đang chạy. **Không copy lời khuyên pinning cho JDK 21 sang JDK 24+ mà bỏ qua khác biệt phiên bản.**

Một hệ thống có thể có 100.000 virtual threads đang chờ, 12 JDBC connections bận và 2 CPU cores. Tối ưu hóa số lượng virtual threads sẽ không làm hai cores xử lý nhanh hơn hoặc tăng 12 connections thành 1.200.

### Cách quan sát pinning và backlog

- Dùng **JDK Flight Recorder (JFR)** để quan sát virtual-thread events, blocked operations, CPU hotspots và GC; tên/semantics event có thể thay đổi theo JDK.
- Dùng thread dump/công cụ `jcmd` có hỗ trợ virtual threads để biết task đang đợi ở đâu.
- Kết hợp với Hikari metrics: active, idle, pending, connection acquire duration.
- Kiểm tra HTTP pool, timeouts, server-side 429/503 và latency theo dependency.
- Không suy luận một graph “tổng virtual threads” là đủ để xác định bottleneck.

## 16. Kiểm thử đúng hơn việc chỉ tăng thread và đo throughput

Một thử nghiệm migration từ platform-thread pool sang virtual threads nên có baseline được kiểm soát:

| Thử nghiệm | Kiểm tra gì? | Kết quả kỳ vọng |
| --- | --- | --- |
| 20 requests, DB pool 10 | Khi hết connection, có đợi pool đúng không? | Không có connection thứ 11 từ cùng pool |
| 500 requests, DB pool 10 | Pending/acquisition timeout thế nào? | Có backpressure/fail-fast thay vì backlog không giới hạn |
| 100 requests tới partner limit 8 | Có bao nhiêu calls thật in-flight? | Không vượt 8 trong một instance |
| 429 từ partner | Retry có backoff và budget không? | Không tạo retry storm |
| 5 pods, 20 permits/pod | Tổng concurrent calls có vượt quota cluster không? | Phải có thiết kế quota tổng |
| Timeout xảy ra trước acquire | Client có gửi call vô ích sau deadline không? | Bỏ/reject theo hợp đồng |
| HTTP async call trả Future | Permit giữ đến khi network operation kết thúc? | Không release quá sớm |
| Hikari pool bị cạn | Có báo đúng lỗi acquisition? | Không nhầm với query timeout |
| Transaction giữ connection trong lúc gọi remote | DB connection bị chiếm bao lâu? | Tránh giữ connection khi chờ remote không cần thiết |
| Java 21 vs Java 24+ | Có pinning/CPU/latency khác nhau? | Đo theo JDK, không suy luận giống nhau |
| CPU-bound workload | Virtual threads có thật sự giúp? | Thường không tăng tính toán trên CPU hữu hạn |
| Graceful shutdown/cancel | Permit/connection có được release khi lỗi? | Không leak slots hoặc transaction |

### Thí nghiệm có tải thật cần đo những gì?

1. **Throughput** thành công, không chỉ request nộp vào executor.
2. **Latency p50/p95/p99**, phân rã thành queue wait, acquire wait và actual downstream service time.
3. **Timeout/reject/429/503**, retries và error budget.
4. **Số active/pending connections** ở mỗi JDBC/HTTP pool.
5. **Số concurrent calls thực** theo partner, tenant, pod và toàn cluster.
6. **Memory/GC/CPU** khi tạo nhiều virtual threads hoặc request contexts.
7. **Thời gian giữ transaction/connection** và lock wait trong database.
8. **Số virtual threads đang chờ**, không chỉ số virtual threads đã tạo.

Nếu đổi executor rồi thấy throughput tăng nhưng DB CPU=100%, Hikari pending tăng và p99 xấu đi, đó không nhất thiết là thành công của toàn hệ thống. Có thể bạn chỉ đẩy nghẽn từ platform-thread pool sang DB.

### Định luật Little như một công cụ tư duy

Ở trạng thái tải ổn định, số yêu cầu trung bình đang tồn tại trong hệ thống gần bằng:

```text
L ≈ λ × W

L: trung bình số request đang chờ/được xử lý
λ: throughput ổn định (request/giây)
W: thời gian trung bình một request ở trong hệ thống (giây)
```

**Ví dụ minh họa**: nếu hệ thống hoàn thành trung bình 50 calls/giây và một call tồn tại trung bình 0,2 giây, số calls in-flight trung bình xấp xỉ 10. Đây không phải capacity tối đa hay công thức chọn permits trực tiếp. Trong overload, arrival rate có thể vượt completion rate và backlog tăng; phải đo thực tế.

Nếu tăng virtual-thread concurrency làm W tăng vì DB/partner bị nghẽn mà λ không tăng tương ứng, L có thể tăng mạnh. Nghĩa là thêm tasks đang chờ chứ không thêm giá trị.

## 17. Những sai lầm phổ biến

### “Có 10.000 virtual threads thì tôi có thể mở 10.000 connection”

**Sai.** Virtual threads là đơn vị thực thi Java; connection là tài nguyên TCP/DB do pool, OS và server quản lý. HikariCP `maximumPoolSize=10` vẫn giới hạn tối đa 10 connections từ pool đó.

### “Tăng maximumPoolSize càng lớn càng tốt”

**Sai.** DB có max_connections, memory, CPU, lock contention và IOPS hữu hạn. Pool quá lớn còn có thể làm query latency xấu hơn. Chọn theo workload, sizing guidance của DB và toàn cluster.

### “Có virtual threads nên không cần backpressure”

**Sai.** Mỗi request vẫn mang state, giữ quota và có thể tích tụ vô hạn. Cần giới hạn số đang đợi, timeout/deadline và reject/load shedding.

### “Semaphore(8) bảo đảm dưới 8 requests/giây”

**Sai.** Semaphore là **concurrency limiter**, không phải rate limiter. Khi service trả rất nhanh, tổng calls/giây có thể cao hơn nhiều.

### “Tôi có Semaphore(8) ở mỗi pod, vậy cluster chỉ gửi 8 requests”

**Sai.** Mỗi pod có 8 permits riêng. Cần chia quota tổng hoặc dùng cơ chế phân tán khi downstream quota áp dụng toàn cluster.

### “@Async và Spring flag tự động làm mọi công việc chạy trên virtual threads”

**Không mặc định.** Executor phụ thuộc cấu hình Boot, `AsyncConfigurer`, named beans, lời gọi qua proxy và các subsystem khác. Phải kiểm tra runtime.

### “Đặt timeout cho Future là đủ để đóng HTTP request”

**Sai.** Timeout/cancellation của một future có thể thay trạng thái kết quả mà operation nền vẫn chạy. Cần client timeout/cancellation API thật và cleanup.

### “Nhả permit sau khi sendAsync trả về là đúng”

**Sai.** Lúc đó network request có thể vẫn in-flight. Permit phải theo lifetime của resource/operation được bảo vệ.

### “Blocking trên virtual thread luôn không tiêu tốn tài nguyên”

**Sai.** Virtual thread có thể park nhưng JDBC connection, HTTP request, transaction hay lock vẫn bị giữ. Ngoài ra CPU/heap/GC vẫn hữu hạn.

### “Tôi đã bật virtual threads nên CPU-bound code chạy nhanh hơn”

**Sai.** Virtual threads phù hợp với nhiều blocking tasks; không làm phép tính chạy nhanh hơn số CPU cores cho phép.

### “Connection pool mặc định luôn đủ cho mọi loại truy vấn”

**Sai.** Một truy vấn 5 ms và một truy vấn 30 giây cùng giữ connection nhưng tác động tới pool hoàn toàn khác. Cần đo durations, DB plans, lock waits và chia workload khi phù hợp.

### “Dùng Hikari timeout 1 giây tức SQL chỉ chạy tối đa 1 giây”

**Sai.** `connectionTimeout` giới hạn **chờ lấy connection**. Query timeout, transaction timeout và read/socket timeout cần cấu hình riêng.

### “Tôi thử 1.000 requests thành công, production chắc chắn an toàn”

**Chưa đủ.** Cần đo burst, autoscaling, slow downstream, 429, retries, GC, leak, saturation và cả hành vi khi không còn capacity.

## 18. Quy trình lựa chọn giới hạn và tài liệu tham khảo

### Checklist production

1. **Đếm tài nguyên thật**: DB max connections, pool size mỗi pod, API concurrent limit, RPS/quota và CPU/network.
2. **Xác định vùng cần bảo vệ**: mỗi dependency hoặc loại truy vấn có capacity khác nhau; không dùng một con số chung cho tất cả.
3. **Đặt limit đúng tầng**: connection pool để giới hạn DB connections, semaphore/bulkhead cho concurrent calls, rate limiter cho RPS.
4. **Có admission/backlog policy**: khi không thể nhận thêm, chờ bao lâu, từ chối thế nào và người gọi có retry không.
5. **Đặt timeout theo deadline tổng**: acquire, connect, response/read, query/transaction và retry budget có quan hệ với nhau.
6. **Đóng tài nguyên trong finally**: `Connection.close()` trả connection về pool; permit được release, response/stream được đóng.
7. **Không giữ resource ngoài phạm vi cần thiết**: tránh giữ connection khi chờ remote API nếu transaction không đòi hỏi.
8. **Không nhầm async completion**: Future hoàn thành/timeout/hủy không nhất thiết đồng nghĩa remote call thực sự đã dừng.
9. **Chú ý mỗi pod**: semaphore và Hikari pool thường có limit cục bộ; tính quota cluster khi autoscale.
10. **Tách rate và concurrency**: bảo vệ cả peak requests/giây lẫn số requests in-flight khi service đích có cả hai giới hạn.
11. **Có retry budget và jitter**: không retry storm khi downstream trả 429/503 hoặc timeout.
12. **Test bằng Java 21+**: JDK 17 không có virtual threads production API; ví dụ `newVirtualThreadPerTaskExecutor()` cần Java 21+.
13. **Kiểm tra Spring executor thực**: custom Executor/AsyncConfigurer có thể khiến Boot auto-configuration chọn khác.
14. **Quan sát bottleneck end-to-end**: pool pending, p99, service error rate, active calls và resource saturation.
15. **Giữ tính đúng đắn nghiệp vụ**: idempotency, transaction boundaries và consistency không tự có khi đổi loại thread.
16. **Không dựa vào một con số benchmark**: thử nhiều tải, nhiều pod, nhiều kiểu downstream latency và các failure modes.

### Tài liệu chính thức và đáng tin cậy

1. [Oracle Java 21 – Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html): thiết kế, không pool virtual threads, sử dụng `Semaphore` với tài nguyên hữu hạn và việc database connection pool đã có chức năng giới hạn.
2. [OpenJDK JEP 444 – Virtual Threads](https://openjdk.org/jeps/444): tính năng Java 21 và mô hình thread-per-task.
3. [Spring Boot 3.5 – Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html): lựa chọn `AsyncTaskExecutor` khi bật virtual threads, custom executor và scheduler.
4. [Spring Boot – Virtual Threads](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.virtual-threads): `spring.threads.virtual.enabled`, điều kiện Java 21+ và lifecycle.
5. [HikariCP – Configuration](https://github.com/brettwooldridge/HikariCP#frequently-used): `maximumPoolSize` và `connectionTimeout`.
6. [Spring Boot 3.5 – Common Application Properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/): `spring.datasource.hikari.maximum-pool-size` và các thuộc tính datasource.
7. [Java SE 21 – Executors](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html): `newVirtualThreadPerTaskExecutor()`.
8. [Java SE 21 – Semaphore](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Semaphore.html): phép `acquire`, `tryAcquire` và `release`.
9. [Java SE 21 – CountDownLatch](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CountDownLatch.html): đồng bộ bài test không phụ thuộc `Thread.sleep`.
10. [Resilience4j – Bulkhead](https://github.com/resilience4j/resilience4j#73-bulkhead): kiểm soát concurrent calls bằng SemaphoreBulkhead.
11. [OpenJDK JEP 491 – Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491): thay đổi pinning trong JDK 24; cần phân biệt với hành vi JDK 21.

**Kết luận:** virtual threads giảm chi phí giữ **luồng thực thi trong lúc chờ**, không tăng số **JDBC connections**, khả năng xử lý SQL, HTTP connections, quota API hay CPU của hệ thống đích. Giữ giới hạn đúng tầng — connection pool cho DB, `Semaphore`/bulkhead cho concurrent partner calls, rate limiter cho RPS và deadline/admission cho backlog — mới là cách dùng virtual threads an toàn trong Spring. Nếu bỏ giới hạn tài nguyên và chỉ tăng số task, ta có thể biến một bottleneck nhỏ thành hàng nghìn request đang chờ và một hệ thống quá tải lớn hơn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-007">ThreadLocal, ngắt và vòng đời vẫn cần được quản lý</a>

<details>
<summary>Xem chi tiết</summary>

# ThreadLocal, ngắt và vòng đời vẫn cần được quản lý

> **Module:** Spring Framework – concurrency  
> **Nhóm kiến thức:** Luồng ảo (Virtual Threads)  
> **Phiên bản:** Java **21+** (virtual threads là tính năng chính thức từ JDK 21), Spring Framework **6.2.x**.  
> **Thông điệp chính:** Luồng ảo làm giảm chi phí duy trì rất nhiều công việc chờ I/O, nhưng **vẫn là các `Thread` độc lập**. Chúng có `ThreadLocal`/`InheritableThreadLocal`, cờ ngắt (`interrupt status`), khả năng bị hủy, kết quả/thất bại và vòng đời tạo–chạy–kết thúc. Không được xem “luồng ảo nhẹ” là lý do bỏ quản lý context, deadline, cancellation, tài nguyên, shutdown hoặc trạng thái nghiệp vụ.

Một luồng ảo thường được **tạo mới cho mỗi tác vụ** thay vì dùng thread pool; khi nó chờ I/O có hỗ trợ, runtime có thể tháo nó khỏi carrier platform thread để carrier phục vụ luồng khác. Điều này làm **tăng khả năng mở rộng cho workload chờ I/O**, không làm CPU hoặc cơ sở dữ liệu xử lý nhanh vô hạn. ThreadLocal vẫn gắn với **virtual thread**, không gắn với carrier. Interrupt vẫn là một **yêu cầu hợp tác**, không phải lệnh cưỡng bức rollback mọi tác dụng phụ.

## 1. Mục tiêu học tập

Sau bài học, bạn có thể:

1. Giải thích quan hệ giữa **virtual thread**, **platform thread carrier**, task và executor trong Java 21+.
2. Phân biệt `ThreadLocal` và `InheritableThreadLocal` ở luồng ảo; biết tại sao context không “tự bám theo carrier”.
3. Xác định các lỗi do capture hoặc truyền context HTTP, MDC, security, transaction vào tác vụ sau request.
4. Quản lý `ThreadLocal.set/get/remove` theo phạm vi task, có `try/finally` và DTO snapshot tối thiểu khi thích hợp.
5. Giải thích tại sao **ThreadLocal cache cho đối tượng đắt đỏ** không có lợi khi mỗi virtual thread chỉ phục vụ một task.
6. Dùng `Thread.currentThread().interrupt()` đúng khi bắt `InterruptedException` và thoát công việc một cách hợp tác.
7. Phân biệt `Future.cancel(true)`, `shutdown()`, `shutdownNow()`, `ExecutorService.close()` và giới hạn của việc hủy.
8. Hiểu `Thread.ofVirtual().start(...)`, `Thread.join()`, `Executors.newVirtualThreadPerTaskExecutor()` và việc virtual thread là **daemon**.
9. Tích hợp `@Async` trong Spring với `VirtualThreadTaskExecutor`/`SimpleAsyncTaskExecutor.setVirtualThreads(true)` mà không quên proxy, result và lifecycle.
10. Áp dụng giới hạn concurrency cho DB/HTTP downstream, timeout/deadline và trạng thái job bền vững.
11. Phân biệt khả năng pin carrier trong JDK 21–23 và thay đổi từ JDK 24; không coi mọi vấn đề blocking là đã biến mất.
12. Xây test xác định với latch/future, không dựa vào `Thread.sleep` hay thứ tự bất định của worker.

## 2. Luồng ảo cũng là Thread

Trong JDK 21, `java.lang.Thread` có hai loại:

- **Platform thread:** thường gắn với một OS thread trong toàn bộ đời sống; có stack/tài nguyên OS đáng kể.
- **Virtual thread:** được Java runtime quản lý, có thể được mount lên một **carrier** (platform thread) rồi unmount khi chờ một số thao tác blocking; số virtual threads có thể lớn hơn rất nhiều số carrier.

Cả hai cùng có:

- `Thread.currentThread()`, tên thread, thread ID, stack trace.
- `ThreadLocal`, `InheritableThreadLocal`.
- Interrupt status và cơ chế `interrupt()`/`isInterrupted()`.
- Kết thúc bình thường hoặc kết thúc do exception.
- Ownership của task, tài nguyên phải đóng, logging và khả năng bị bỏ quên.
- Tính đồng thời và rủi ro race khi cùng truy cập dữ liệu mutable.

~~~text
Nhiều task đang hoạt động
    |
    +-- virtual thread A -- waiting I/O -- unmounted
    +-- virtual thread B -- waiting I/O -- unmounted
    +-- virtual thread C -- running -----> carrier #1
    +-- virtual thread D -- running -----> carrier #2
    |
    +-- mỗi virtual thread có ThreadLocal và interrupt status riêng
    |
Java runtime scheduler điều phối virtual threads lên carrier threads
~~~

**Carrier là cơ chế thực thi, không phải identity nghiệp vụ.** Một virtual thread có thể tiếp tục trên carrier khác sau một lần chờ; `Thread.currentThread()` trong code vẫn đại diện **cùng virtual thread**. Vì thế đừng đọc/ghi context bằng cách bám vào tên hay ID của carrier.

### 2.1. Vì sao virtual thread không phải “async không có Thread”?

`CompletableFuture` hoặc reactive operators có thể chạy callback trên nhiều thread tùy executor. Một virtual thread thì **vẫn thực sự có Thread object và call stack logic của chính task**. Code blocking tuần tự sẽ dễ đọc hơn, nhưng bạn vẫn phải thực hiện mọi quy tắc quản lý lifecycle như khi dùng platform thread.

### 2.2. Virtual thread phù hợp cho loại công việc nào?

Tốt cho lượng lớn tác vụ độc lập **thường xuyên chờ I/O**: HTTP downstream, JDBC/network, các thao tác request/response khi library hỗ trợ. Nó **không** biến một thuật toán CPU-bound chạy 10 giây thành chạy 1 giây; số core CPU vẫn giới hạn song song thực tế.

Nếu bottleneck là database connection pool chỉ có 20 kết nối, tạo 100.000 virtual threads vẫn có thể làm 99.980 task chờ ở pool; cần điều khiển **concurrency đối với tài nguyên khan hiếm**, không chỉ số thread.

## 3. Java 21: API tạo và chờ virtual thread

### 3.1. Tạo trực tiếp

~~~java
Thread worker = Thread.ofVirtual()
        .name("import-1")
        .start(() -> System.out.println(
                "virtual = " + Thread.currentThread().isVirtual()));

worker.join();
~~~

Đây là **snippet Java 21+**; kết quả in từ worker là `virtual = true` nếu chạy thành công. `join()` khiến caller đợi worker xong trước khi tiếp tục. Không gọi `run()` trực tiếp để mong tạo luồng mới: gọi `run()` là lời gọi method synchronous trên thread hiện hành.

### 3.2. Executor tạo một virtual thread cho mỗi task

~~~java
try (ExecutorService executor =
             Executors.newVirtualThreadPerTaskExecutor()) {
    Future<String> future = executor.submit(() ->
            "virtual=" + Thread.currentThread().isVirtual());
    System.out.println(future.get());
}
~~~

Đây là **snippet Java 21+**, cần imports `ExecutorService`, `Executors`, `Future`. Giá trị in là `virtual=true`. `newVirtualThreadPerTaskExecutor()` **không phải** pool tái sử dụng một nhóm virtual thread; mỗi task được submit có một virtual thread mới.

### 3.3. Tạo nhiều luồng ảo không miễn phí hoàn toàn

Vẫn có chi phí object `Thread`, stack segments, references, task payload, `ThreadLocal` entries, heap allocation, I/O handles, tracing, connection pool waiters. Thậm chí có thể có **rất nhiều thread đang chờ** và giữ 1–10 KiB dữ liệu/thread, khiến RAM tăng mạnh. Đừng cho phép request đầu vào tự spawn không giới hạn tác vụ kéo dài hoặc giữ body lớn.

## 4. ThreadLocal trên virtual thread: quy tắc không đổi

`ThreadLocal<T>` lưu giá trị **theo từng Thread instance**. Một virtual thread có bảng ThreadLocal của riêng nó. Việc cùng chạy qua một carrier **không** làm hai virtual threads tự dùng chung ThreadLocal của carrier.

~~~java
private static final ThreadLocal<String> CORRELATION_ID =
        new ThreadLocal<>();

void process(String correlationId) {
    CORRELATION_ID.set(correlationId);
    try {
        doBusinessWork();
    } finally {
        CORRELATION_ID.remove();
    }
}
~~~

Đây là **snippet** trong một class ứng dụng; cần implement `doBusinessWork()`. Dùng `finally` để dọn context cả khi exception/interruption, nhất là khi một task còn tạo subtask/callback. Nếu mỗi virtual thread chỉ tồn tại đúng một tác vụ và đã kết thúc, ThreadLocal của nó có thể được GC cùng thread; nhưng **không được lấy giả định đó làm lý do giữ đối tượng nhạy cảm/đắt đỏ hoặc bỏ cleanup tài nguyên**.

### 4.1. Ngộ nhận: carrier thread thay đổi làm mất ThreadLocal

Sai. Khi virtual thread tạm dừng rồi được mount lên carrier khác, trạng thái ThreadLocal thuộc **virtual thread**, không thuộc carrier. Nếu code của bạn thấy `ThreadLocal.get()` trả `null`, hãy xem việc **đang chạy ở Thread khác** (ví dụ `CompletableFuture` callback/@Async), inheritance và propagation, không vội kết luận do “virtual thread đổi carrier”.

### 4.2. Ngộ nhận: mọi task do @Async gửi đi đều nhìn thấy request context

Sai. `RequestContextHolder`, SLF4J MDC và `SecurityContextHolder` có thể dựa vào ThreadLocal hoặc cơ chế context khác. Một virtual thread mới không tự chia sẻ **regular ThreadLocal** của request thread, kể cả scheduler chạy cùng JVM.

Một số context dùng `InheritableThreadLocal` có thể được thừa kế **tại lúc tạo child thread**. Việc có thể thừa kế không có nghĩa an toàn: request/session/security objects có thể đã hết lifecycle khi task chạy, hoặc bị giữ quá lâu. Hãy trích xuất dữ liệu tối thiểu trong request boundary và truyền vào argument.

### 4.3. ThreadLocal không phải nơi lưu dữ liệu giữa các task

Ở pool platform threads, một worker thread có thể tồn tại qua hàng nghìn task: quên `remove()` có nguy cơ tác vụ khác thấy dữ liệu tác vụ trước. Ở per-task virtual threads, một virtual thread không được tái sử dụng cho task khác; **cross-task leak từ reuse cùng thread giảm đi**, nhưng context inheritance, object capture, callback trên thread khác, static cache và giữ Future/Thread references vẫn có thể gây rò rỉ thông tin/memory retention.

## 5. InheritableThreadLocal: thừa kế tại lúc tạo, không phải sync hai chiều

`InheritableThreadLocal<T>` truyền giá trị ban đầu từ parent sang child thread khi child được **tạo**, trừ khi builder/factory tắt inheritance. Nó không phải “live reference tới giá trị của parent” theo nghĩa mọi lần parent thay `set` thì child tự cập nhật. Nếu value là object mutable, các luồng vẫn có thể cùng trỏ tới **một object** trừ khi có copy/clone phù hợp.

### 5.1. Builder cho phép tắt thừa kế

~~~java
Thread child = Thread.ofVirtual()
        .inheritInheritableThreadLocals(false)
        .start(() -> {
            // Không kế thừa InheritableThreadLocal của creator.
            processWithExplicitInput();
        });
child.join();
~~~

Đây là **snippet Java 21+** có method nghiệp vụ cần implement. Với ứng dụng cần ranh giới security rõ ràng, tắt inheritance ở thread factory rồi **truyền context explicit** là một lựa chọn. `Executors.newVirtualThreadPerTaskExecutor()` không cung cấp một cấu hình chung “truyền mọi context đúng”, nên cần xem factory/executor thực tế.

### 5.2. Vì sao inheritance có thể nguy hiểm trong Spring web?

Nếu `RequestContextHolder` bị cấu hình dùng inheritable context, một virtual thread tạo trong request có thể nhận `ServletRequestAttributes`; các attributes có thể giữ tham chiếu tới `HttpServletRequest` và response. Sau khi HTTP request hoàn tất, job nền không được giả định object đó còn hợp lệ. Thậm chí nếu task không gọi request nữa, references vẫn có thể bị giữ đến khi task/closure kết thúc.

Không bật inheritable request context để “@Async nhìn thấy header” như một thủ thuật chung. Thay bằng DTO nhỏ và bền cho task: `actorId`, `tenantId` đã xác thực, `correlationId` đã validate, `jobId`, các business IDs cần thiết.

## 6. Vì sao ThreadLocal cache nguy hiểm hơn trên luồng ảo?

Một kiểu cũ trong fixed platform thread pool:

~~~java
static final ThreadLocal<SimpleDateFormat> FORMATTER =
        ThreadLocal.withInitial(
                () -> new SimpleDateFormat("yyyy-MM-dd"));
~~~

**Snippet Java**: ở pool 10 platform workers, có thể chỉ tạo khoảng 10 instances formatter nếu mọi worker đều sử dụng. Nhưng với **100.000 virtual threads**, mỗi thread có ThreadLocal riêng và có thể tạo **100.000 instances** trong cùng đợt tác vụ — đúng ngược mục đích cache tiết kiệm cấp phát.

Thay bằng một formatter bất biến dùng chung:

~~~java
static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ISO_LOCAL_DATE;
~~~

**Snippet Java**; cần import `java.time.format.DateTimeFormatter`. `DateTimeFormatter` là immutable và thread-safe, nên chia sẻ trong nhiều virtual threads được.

### 6.1. Context nhỏ khác cache đối tượng đắt đỏ

Truyền một chuỗi correlation ID vào phạm vi task có thể hợp lý. Cache `SimpleDateFormat`, JDBC `Connection`, HTTP request/response, parser mutable hoặc hàng MB `byte[]` theo ThreadLocal thì không nên. Khi mỗi task tương ứng một virtual thread, “cache theo thread” gần như trở thành “allocate theo task”.

Ví dụ để hình dung: một giá trị 16 KiB cho 200.000 virtual threads đang tồn tại có thể giữ khoảng **3,05 GiB** dữ liệu tính riêng giá trị đó, chưa tính stack/object overhead. Đây là phép tính minh họa dung lượng, không phải kích thước cố định của virtual thread.

### 6.2. Kết thúc thread vẫn không giải phóng tài nguyên ngoài JVM

Ngay cả khi virtual thread đã chết và ThreadLocal map không còn reachable, `Connection` chưa được trả cho pool nếu không `close()`, file descriptor chưa chắc đã đóng đúng thời điểm, transaction có thể đang lỗi và external job không được đánh dấu. `ThreadLocal.remove()` **không** thay thế `Connection.close()`, try-with-resources, transaction cleanup hoặc công việc business compensation.

## 7. Java 21 chạy độc lập: ThreadLocal và inheritance

Chương trình sau kiểm chứng ba điều bằng kết quả rõ ràng:

- Regular `ThreadLocal` của main **không tự truyền** sang virtual thread task.
- Mỗi virtual thread per task có giá trị riêng, không lấy giá trị của task khác.
- `InheritableThreadLocal` có thể truyền mặc định từ creator sang virtual child; tắt bằng `inheritInheritableThreadLocals(false)`.

Tạo tệp **`VirtualThreadContextDemo.java`**:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class VirtualThreadContextDemo {

    private static final ThreadLocal<String> TRACE =
            new ThreadLocal<>();
    private static final InheritableThreadLocal<String> INHERITED =
            new InheritableThreadLocal<>();

    public static void main(String[] args) throws Exception {
        TRACE.set("main-request");
        INHERITED.set("parent-context");

        try {
            try (ExecutorService executor =
                         Executors.newVirtualThreadPerTaskExecutor()) {

                Future<String> first = executor.submit(() -> {
                    String before = TRACE.get();
                    TRACE.set("task-A");
                    try {
                        return Thread.currentThread().isVirtual()
                                + " / " + before
                                + " -> " + TRACE.get();
                    } finally {
                        TRACE.remove();
                    }
                });

                Future<String> second = executor.submit(() -> {
                    String before = TRACE.get();
                    TRACE.set("task-B");
                    try {
                        return Thread.currentThread().isVirtual()
                                + " / " + before
                                + " -> " + TRACE.get();
                    } finally {
                        TRACE.remove();
                    }
                });

                System.out.println("main = " + TRACE.get());
                System.out.println("task A = " + first.get());
                System.out.println("task B = " + second.get());
            }

            AtomicReference<String> yes = new AtomicReference<>();
            Thread inherited = Thread.ofVirtual().start(
                    () -> yes.set(INHERITED.get()));
            inherited.join();

            AtomicReference<String> no = new AtomicReference<>();
            Thread isolated = Thread.ofVirtual()
                    .inheritInheritableThreadLocals(false)
                    .start(() -> no.set(INHERITED.get()));
            isolated.join();

            System.out.println("inherited = " + yes.get());
            System.out.println("disabled = " + no.get());
        } finally {
            TRACE.remove();
            INHERITED.remove();
        }
    }
}
~~~

Biên dịch và chạy trên **JDK 21+**:

~~~bash
javac VirtualThreadContextDemo.java
java VirtualThreadContextDemo
~~~

**Stdout chính xác khi chạy thành công**:

~~~text
main = main-request
task A = true / null -> task-A
task B = true / null -> task-B
inherited = parent-context
disabled = null
~~~

### Vì sao không phụ thuộc thứ tự scheduler?

Hai worker chỉ **trả String** qua `Future`. Main thread tự gọi `first.get()` rồi `second.get()` nên thứ tự output rõ ràng dù thứ tự worker chạy thực tế thay đổi. `ThreadLocal` của main không được thừa kế vì đó là **regular** ThreadLocal. `AtomicReference` và `join()` bảo đảm kết quả child đã được công bố trước khi main in.

**Giới hạn:** demo cho thấy inheritance ở một child tạo trực tiếp; không tuyên bố tất cả Spring executors cấu hình inheritance giống nhau. Khi chạy `@Async`, cơ chế proxy/executor/task decorator quyết định context được truyền gì. Thiết kế nên dùng arguments explicit và không dựa vào việc inheritance “tình cờ hoạt động”.

## 8. ThreadLocal an toàn hơn khi có phạm vi rõ ràng

Một nguyên tắc tốt là **tạo context trước khi vào task**, đặt vào ThreadLocal trong suốt scope cần dùng và dọn sạch sau đó.

~~~java
static Runnable withCorrelation(
        ThreadLocal<String> local,
        String correlationId,
        Runnable delegate) {

    return () -> {
        String previous = local.get();
        try {
            if (correlationId == null) {
                local.remove();
            } else {
                local.set(correlationId);
            }
            delegate.run();
        } finally {
            if (previous == null) {
                local.remove();
            } else {
                local.set(previous);
            }
        }
    };
}
~~~

Đây là **method snippet Java**; nó restore giá trị trước đó để an toàn khi nested scopes dùng cùng thread. Nếu thread luôn mới, previous thường null; nhưng việc restore giúp contract chính xác hơn ở implementation khác hoặc khi code được tái sử dụng với platform workers.

### 8.1. Truyền gì vào virtual thread?

| Nhu cầu | Nên truyền | Không nên truyền |
| --- | --- | --- |
| Correlation log | `correlationId` được validate, giới hạn độ dài | Cả `HttpServletRequest` hoặc tất cả headers/cookies |
| Audit | `actorId`, `tenantId` đã xác thực | Bearer token, password, mutable `Authentication` khi không cần |
| Business job | `jobId`, `orderId`, `documentId` | Request-scoped bean/session object có lifecycle ngắn |
| Xử lý file | Storage key sau khi staging thành công | `MultipartFile` hoặc `ServletInputStream` có thể bị cleanup |
| Deadline | `Instant deadline` hoặc timeout policy | HTTP response cũ để hỏi xem client còn kết nối |
| Transaction | Lệnh nghiệp vụ + service có transaction mới | EntityManager/Connection/transaction ThreadLocal của request thread |

Với dữ liệu mutable, record Java chỉ làm field references final; nếu field là `byte[]` hoặc `List<MutableDto>`, cần copy/immutable representation phù hợp. Không chuyển cả object request sang virtual thread chỉ để lấy hai header sau này.

### 8.2. Virtual threads không thay thế scoped context API

Ở JDK 21, `ScopedValue` và structured concurrency còn thuộc API preview (cần bật preview và chấp nhận contract/version phù hợp). Các phiên bản Java mới hơn có thể thay đổi trạng thái API, nhưng bài này **không yêu cầu preview** để chạy. Có thể dùng ScopedValue trong dự án phù hợp nếu phiên bản JDK hỗ trợ, song **cũng cần quản lý scope và lifecycle**, không phải giải pháp để giữ raw HTTP request mãi.

Nếu codebase là Java 21 không bật preview, tránh copy ví dụ ScopedValue của một JDK mới rồi gọi đó là Java 21 source hợp lệ.

## 9. Spring RequestContextHolder, MDC và SecurityContext

Spring và các thư viện thường lưu context gắn thread. Các ngữ cảnh khác nhau có lifecycle khác nhau:

- `RequestContextHolder`: `RequestAttributes` cho request thread, có thể gắn Servlet request và response.
- `SecurityContextHolder`: authentication/security context theo chiến lược lưu trữ (thường ThreadLocal).
- Logging MDC: correlation/trace fields thường dùng ThreadLocal của logging adapter.
- Spring transaction: transaction synchronization/resource association thường thread-bound, không phải object tự di chuyển giữa executors.

### 9.1. Không đọc RequestContextHolder trong worker sau response

Một mẫu dễ lỗi:

~~~java
@Async("virtualExecutor")
public void generate() {
    ServletRequestAttributes attrs =
            (ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes();

    HttpServletRequest request = attrs.getRequest();
    String user = request.getHeader("X-User");
    processLater(user);
}
~~~

Đây là **snippet phản ví dụ**, cần Spring web imports và service `processLater`. Nó có thể ném lỗi nếu scope không active hoặc truy cập request sau lifecycle. Đừng sửa bằng cách truyền `ServletRequestAttributes` nguyên bản qua ThreadLocal inheritable; cách đó có thể giữ reference tới toàn bộ request/response.

### 9.2. Snapshot trong controller, xử lý bằng virtual thread

~~~java
record ImportInput(
        String jobId, String actorId,
        String tenantId, String documentId) { }

@PostMapping("/imports/{documentId}")
public CompletableFuture<String> importDocument(
        @PathVariable String documentId,
        Principal principal) {

    String actor = principal.getName();
    String tenant = authorizeAndResolveTenant(actor, documentId);

    ImportInput input = new ImportInput(
            newJobId(), actor, tenant, documentId);

    return importService.process(input);
}
~~~

Đây là **Spring MVC snippet** với `importService`, `authorizeAndResolveTenant`, `newJobId` và imports do ứng dụng cung cấp. Controller tạo dữ liệu nghiệp vụ nhỏ trước khi giao việc; worker chỉ đọc `ImportInput`. Với yêu cầu xử lý bền lâu và trả `202 Accepted`, cần durable job store thay vì chỉ giữ `CompletableFuture` trong RAM.

### 9.3. MDC: propagation có chủ đích và cleanup

Nếu cần correlation ID trong log của virtual thread, truyền một string và đặt MDC trong scope task; dọn hoặc restore trong `finally`.

~~~java
static void runWithMdc(String correlationId, Runnable task) {
    String before = MDC.get("correlationId");
    try {
        MDC.put("correlationId", correlationId);
        task.run();
    } finally {
        if (before == null) {
            MDC.remove("correlationId");
        } else {
            MDC.put("correlationId", before);
        }
    }
}
~~~

Đây là **snippet tích hợp** cần `org.slf4j.MDC` và logging implementation phù hợp. Nếu correlationId có thể null, xử lý `remove` thay cho `put` tùy contract của MDC adapter. Không copy **toàn bộ MDC map** nếu nó chứa dữ liệu nhạy cảm/không cần thiết.

### 9.4. SecurityContext không tự chuyển từ HTTP caller

Một virtual thread mới không mặc nhiên có mọi regular ThreadLocal security state của request thread. Nếu cần access control tại worker, tốt hơn truyền **actor/tenant ID đã kiểm chứng** và dùng business authorization policy phù hợp với thời điểm thực thi. Với các trường hợp cần delegation bảo mật trong một phạm vi ngắn, Spring Security cung cấp các wrapper `DelegatingSecurityContext...`; chúng vẫn phải được dùng theo đúng lifecycle/context ownership.

Không serialize nguyên `SecurityContext`, bearer token hoặc session ra job queue lâu dài chỉ để “tiện gọi downstream”. Quyền có thể hết hạn hoặc bị thu hồi trước khi job chạy.

### 9.5. Transaction không “đi theo virtual thread”

Một transaction mở trên servlet/platform thread được Spring liên kết với thread đó. Tạo virtual thread mới **không tự chuyển** connection/transaction context từ caller; và bạn không nên cố chuyển `Connection` hay `EntityManager` bằng ThreadLocal. Giao việc nền qua method service có `@Transactional` hoạt động đúng proxy, mở transaction **trên thread thực thi nghiệp vụ** rồi commit/rollback trong scope của nó.

## 10. Interrupt: virtual thread cũng phải hợp tác

`Thread.interrupt()` đặt interrupt status hoặc khiến một thao tác blocking interruptible phản ứng theo API. Nó **không** giết cưỡng bức Java thread, không reset mọi state và không tự rollback lệnh SQL/HTTP/payment đã gửi đi.

Những API có thể phản ứng:

- `Thread.sleep(...)`: ném `InterruptedException`.
- `CountDownLatch.await()`, `BlockingQueue.take()`, `Semaphore.acquire()`: các overload interruptible có thể ném `InterruptedException`.
- `Future.get()` ở waiting caller: có thể ném `InterruptedException` nếu **caller** bị ngắt.
- Nhiều thao tác blocking I/O hiện đại cũng có xử lý ngắt, nhưng **không được giả định mọi driver/library đều dừng ngay**.
- Code CPU-bound không thường xuyên kiểm tra interrupt có thể chạy tiếp lâu dù đã được yêu cầu ngắt.

### 10.1. Hai cách đọc interrupt status

~~~java
boolean interruptedNow =
        Thread.currentThread().isInterrupted();

boolean statusAndClear =
        Thread.interrupted();
~~~

**Snippet Java**. `isInterrupted()` đọc cờ nhưng **không xóa**; `Thread.interrupted()` là static method đọc **và xóa** interrupt status của **thread hiện hành**. Nếu dùng `Thread.interrupted()` vô ý chỉ để log, bạn có thể làm mất tín hiệu hủy.

### 10.2. Bắt InterruptedException đúng

~~~java
try {
    queue.take();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
~~~

**Snippet** trong một method `void` có `queue`; `return` kết thúc task, và restore interrupt status để tầng gọi/cleanup có thể nhận biết. Với method khai báo `throws InterruptedException`, thường tốt hơn **propagate** thay vì nuốt exception:

~~~java
void consume() throws InterruptedException {
    String item = queue.take();
    process(item);
}
~~~

**Snippet** này giả sử `BlockingQueue<String> queue` và `process`. Đừng dùng `catch (InterruptedException ignored) { }` rồi tiếp tục retry vô hạn.

### 10.3. Vì sao restore cờ rồi thoát?

Nhiều phương thức như `sleep` hoặc `await` xóa interrupt status khi ném `InterruptedException`. Nếu một task không thể ném checked exception lên caller, nên restore bằng `Thread.currentThread().interrupt()` và **ngừng công việc có ý nghĩa**, thay vì reset cờ rồi tiếp tục thực hiện side effect như bình thường.

Nếu API/method phải làm cleanup, thực hiện cleanup có deadline trong `finally`; đừng tạo một vòng cleanup không thể hủy và vô thời hạn.

## 11. Future.cancel(true) khác một task đã dừng

Khi gọi:

~~~java
Future<?> future = executor.submit(task);
boolean requested = future.cancel(true);
~~~

Đây là **snippet**. Với một Future đang chạy, `cancel(true)` sẽ cố interrupt thread thực thi task; nếu task không hợp tác hoặc đang chờ I/O không interruptible, thực thi vật lý có thể còn tiếp tục. `Future.isCancelled()`/`get()` mô tả **trạng thái Future**, không phải bằng chứng mọi side effect đã rollback.

Các tình huống cần phân biệt:

| Tình huống | Future | Công việc nghiệp vụ |
| --- | --- | --- |
| Cancel trước khi task bắt đầu | Có thể được đánh dấu cancelled | Task có thể chưa chạy |
| Cancel khi đang `CountDownLatch.await` | Thường nhận interrupt và thoát | Cleanup vẫn phải hoàn thành |
| Cancel khi CPU loop không kiểm tra status | Future có thể cancelled | Code CPU có thể tiếp tục |
| Cancel sau khi remote request đã commit | Future cancelled/timeout | Remote effect có thể đã thành công |
| `shutdownNow()` executor | Cố ngắt active tasks, không nhận task mới | Không bảo đảm driver dừng/rollback |
| Caller `get(timeout)` hết hạn | `TimeoutException` | Task **không tự cancel** chỉ vì caller timeout |

### 11.1. Timeout không đồng nghĩa cancellation

~~~java
try {
    future.get(2, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    future.cancel(true);
    markTimedOut();
}
~~~

**Snippet**; cần handling các checked exceptions khác từ `Future.get`. Việc gọi `get(timeout)` đơn độc chỉ dừng **waiting caller**; nếu muốn hủy task, phải quyết định và gọi cancellation, sau đó xử lý tình huống task có thể đã tạo side effect.

### 11.2. Dùng deadline chung thay vì nhiều timeout không liên kết

Nếu một request deadline là 10 giây mà phải gọi ba downstream, không cho mỗi call thêm 10 giây cộng dồn ngoài contract. Hãy truyền một deadline/remaining time đến từng dependency, kết hợp HTTP connect/read timeout, DB statement timeout và job cancellation. Virtual thread giúp đợi I/O rẻ hơn nhưng **không thay thế timeout chính xác cho tài nguyên**.

## 12. Java 21 chạy độc lập: kiểm chứng interrupt và cancel bằng latch

Chương trình sau làm rõ:

1. Worker là virtual thread thực.
2. Worker vào `CountDownLatch.await()` và chờ, nên chắc chắn còn đang hoạt động khi caller cancel.
3. `Future.cancel(true)` trả true trong kịch bản này, worker nhận `InterruptedException`.
4. Worker khôi phục interrupt status, ghi dấu đã quan sát interrupt và thoát `finally`.
5. `Future.get()` của Future đã hủy ném `CancellationException`; chờ `finished` riêng để chắc chắn cleanup đã chạy.

Tạo tệp **`VirtualInterruptDemo.java`**:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class VirtualInterruptDemo {

    public static void main(String[] args) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean observedInterrupt = new AtomicBoolean(false);

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {

            Future<String> future = executor.submit(() -> {
                started.countDown();
                try {
                    neverReleased.await();
                    return "unexpected";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    observedInterrupt.set(
                            Thread.currentThread().isInterrupted());
                    return "interrupted";
                } finally {
                    finished.countDown();
                }
            });

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker not started");
            }

            System.out.println("cancel requested = "
                    + future.cancel(true));

            try {
                future.get();
                throw new AssertionError("expected cancellation");
            } catch (CancellationException expected) {
                System.out.println("future = cancelled");
            }

            if (!finished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker not finished");
            }

            System.out.println("interrupt observed = "
                    + observedInterrupt.get());
        }
    }
}
~~~

Chạy:

~~~bash
javac VirtualInterruptDemo.java
java VirtualInterruptDemo
~~~

**Stdout xác định khi thành công:**

~~~text
cancel requested = true
future = cancelled
interrupt observed = true
~~~

### Vì sao output xác định?

- `started.countDown()` xảy ra khi worker bắt đầu, trước khi caller cancel. Vì `neverReleased` không được mở, worker chưa thể hoàn tất bình thường.
- `future.cancel(true)` yêu cầu interrupt active virtual thread và chuyển Future sang cancelled state.
- `CountDownLatch.await()` phản ứng với interrupt bằng `InterruptedException`, worker đặt lại cờ và đánh dấu `observedInterrupt`.
- `finished.countDown()` trong `finally` cho caller một điểm đồng bộ *sau khi worker đã xử lý exception*; `future.get` riêng có thể ném cancellation ngay cả khi cleanup vẫn đang hoàn tất.
- Không in từ worker; main in sau các latch, nên thứ tự output được kiểm soát.
- Không có `Thread.sleep` dùng để đoán rằng scheduler đã chạy “đủ lâu”.

**Giới hạn:** đây là trường hợp **blocking API hỗ trợ interrupt**. Không được suy rằng mọi request HTTP/SQL hoặc việc native I/O sẽ phản ứng nhanh tương tự. Khi nghi ngờ, thử đúng driver và timeout thực trong integration tests.

## 13. Ngắt vòng lặp CPU và xử lý bất đồng bộ dài

Một vòng lặp chỉ tính toán có thể tiếp tục mãi nếu không quan sát interrupt/deadline:

~~~java
while (true) {
    calculateNextChunk();
}
~~~

**Snippet phản ví dụ**. Một phiên bản hợp tác:

~~~java
while (!Thread.currentThread().isInterrupted()) {
    if (!hasMoreWork()) {
        break;
    }
    calculateNextChunk();
}
~~~

**Snippet**, yêu cầu implement `hasMoreWork`/`calculateNextChunk`. Với task có timeout, còn phải kiểm tra deadline hoặc ngân sách xử lý. Dù có interrupt status, một method đang chạy CPU trên carrier vẫn chiếm tài nguyên cho tới khi tự yield/chặn/dừng.

### 13.1. Không nuốt interrupt trong retry loop

~~~java
for (int attempt = 0; attempt < 3; attempt++) {
    try {
        callPartner();
        return;
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
    }
}
~~~

**Snippet minh họa** với `callPartner` được khai báo `throws InterruptedException`. Đừng phân loại `InterruptedException` là lỗi mạng transient để tiếp tục retry theo backoff vô hạn. Nếu là cancel, phải dừng logical operation hoặc để tầng orchestrator xử lý.

### 13.2. Cẩn thận với interrupt ở finally

Nếu task đã bị interrupt, một cleanup method tiếp tục gọi `Thread.sleep`/`BlockingQueue.put` có thể lập tức ném `InterruptedException` hoặc làm cleanup thất bại. Hãy thiết kế cleanup có ranh giới rõ: giải phóng lock, đóng resource idempotent, best-effort log/status trong deadline. Không xóa interrupt status toàn cục chỉ để làm cho cleanup “chạy mượt”.

## 14. Vòng đời virtual thread: daemon, join, close và shutdown

Virtual threads **là daemon threads**. Điều này có nghĩa chúng **không giữ JVM sống** chỉ nhờ đang chạy, khác nhiều platform threads non-daemon. Nếu `main` return hoặc tiến trình shutdown, một virtual thread fire-and-forget đang ghi dữ liệu có thể bị kết thúc cùng JVM mà không hề báo business success/failure.

### 14.1. Khi tạo trực tiếp, giữ reference và join

~~~java
Thread t = Thread.ofVirtual().start(() -> doWork());
t.join();
~~~

**Snippet** cần `doWork` và xử lý `InterruptedException` của `join`. Nếu caller bỏ qua `t` và return, không có bảo đảm task sẽ xong trước khi JVM exit.

### 14.2. Khi dùng ExecutorService, đóng có chủ đích

~~~java
try (ExecutorService executor =
             Executors.newVirtualThreadPerTaskExecutor()) {
    Future<?> a = executor.submit(this::runA);
    Future<?> b = executor.submit(this::runB);

    a.get();
    b.get();
}
~~~

**Snippet** trong một object có `runA/runB`; `ExecutorService.close()` trong try-with-resources thực hiện orderly shutdown và chờ tasks kết thúc. Nếu caller bị interrupt trong `close`, contract của `ExecutorService.close()` có những quy tắc xử lý interrupt/shutdown; cần hiểu API và tránh một task không thể dừng làm close chờ quá lâu.

### 14.3. shutdown không phải đóng hết công việc ngay

- `shutdown()`: không nhận task mới, cố thực hiện các task đã gửi theo contract.
- `awaitTermination(timeout, unit)`: chờ executor kết thúc trong một khoảng giới hạn.
- `shutdownNow()`: cố stop tasks active qua interruption, trả những task chưa bắt đầu nếu implementation có, **không bảo đảm** dừng mọi blocking I/O hoặc rollback side effect.
- `ExecutorService.close()`: orderly shutdown và chờ các task; nếu cần **giới hạn thời gian shutdown** hoặc fail fast, dùng explicit policy có deadline và cancellation.

### 14.4. Không tạo ExecutorService mỗi request rồi bỏ reference

Nếu mỗi HTTP request tạo một per-task executor, nộp nhiều task mà **không close/join** và quên mọi Future, application sẽ không biết lúc nào hoàn tất, lỗi nào đã xảy ra hoặc lúc shutdown phải cancel ai. Executor per request có thể hợp lý cho một **fan-out có scope và join rõ ràng**; với công việc chạy lâu sau response, cần owner/job manager hoặc queue bền.

### 14.5. Quản lý ngoại lệ cũng là lifecycle

`execute(Runnable)` và `submit(Callable)` có đường quan sát failure khác nhau: `Future.get` ném `ExecutionException` chứa cause khi task thất bại; `Future.cancel` dẫn tới `CancellationException`. Không chỉ log một dòng “submitted” rồi tuyên bố job thành công. Với Spring `@Async void`, cấu hình `AsyncUncaughtExceptionHandler`; với `CompletableFuture`, xử lý exception path từ future/callback/HTTP response.

## 15. Không bảo đảm xử lý tác vụ bền qua HTTP response

Một request HTTP nhận 202, code gọi:

~~~java
virtualExecutor.execute(
        () -> exportLargeFile(documentId));
return ResponseEntity.accepted().build();
~~~

Đây là **Spring MVC snippet**, cần bean `virtualExecutor`, `documentId` và method nghiệp vụ. Nó chỉ đảm bảo **đã cố gắng dispatch trong JVM hiện tại**. Nếu tiến trình restart sau khi response trả về, task daemon có thể mất; không có job store để client kiểm tra. Đây là vấn đề **delivery guarantee**, không phải vấn đề platform/virtual thread.

Khi cần đảm bảo job được nhận và theo dõi:

~~~text
HTTP request
  |
  +-- Xác thực + validate
  +-- Ghi JobInput nhỏ, immutable / durable job intent
  +-- Commit job ID và trạng thái ACCEPTED
  +-- Trả 202 + jobId
           |
           v
      Durable queue / outbox
           |
           v
      Bounded worker (có thể virtual thread)
           |
           +-- deadline, retry budget, idempotency
           +-- transaction and external calls
           +-- status SUCCEEDED/FAILED
~~~

Virtual thread là **cơ chế thực thi** cho job worker, không thay job store, retries và reconciliation sau crash.

## 16. Tích hợp Spring: @Async trên virtual thread

Spring Framework 6.1+ có `VirtualThreadTaskExecutor`, một `AsyncTaskExecutor` tạo virtual threads trên Java 21+. Nó đơn giản, cấu hình chính là thread name prefix; không cung cấp sẵn mọi giới hạn workload/queue như fixed platform thread pool.

~~~java
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfig {

    @Bean("virtualExecutor")
    VirtualThreadTaskExecutor virtualExecutor() {
        return new VirtualThreadTaskExecutor("business-vt-");
    }
}
~~~

Đây là **Spring configuration snippet**, cần imports `@Configuration`/`@Bean`/`@EnableAsync` và `org.springframework.core.task.VirtualThreadTaskExecutor`.

Một service:

~~~java
@Service
class InvoiceService {
    @Async("virtualExecutor")
    public CompletableFuture<String> calculate(String invoiceId) {
        String summary = calculateBusinessValue(invoiceId);
        return CompletableFuture.completedFuture(summary);
    }
}
~~~

Đây là **snippet** có method nghiệp vụ cần implement. Khi được gọi **qua Spring proxy**, method chạy trên virtual thread. Lệnh gọi từ cùng bean qua `this.calculate(...)` có thể bypass `@Async` trong proxy mode mặc định.

### 16.1. Không mặc định propagate ThreadLocal từ caller

Nếu HTTP controller có `MDC.put(...)` hoặc `RequestContextHolder`, virtual worker mới có thể không thấy các regular ThreadLocals đó. Nếu cần, truyền correlation/actor ID theo argument nhỏ, rồi task tự thiết lập context trong phạm vi làm việc; hoặc dùng `TaskDecorator`/Micrometer context propagation **một cách có chọn lọc**, tránh kéo nguyên request/session object ra khỏi lifecycle.

### 16.2. Không truyền raw HttpServletRequest vào @Async method

~~~java
@Async("virtualExecutor")
public void wrong(HttpServletRequest request) {
    String tenant = request.getHeader("X-Tenant");
    doWork(tenant);
}
~~~

**Snippet phản ví dụ**. `@Async` không tự mở rộng vòng đời của raw Servlet request. Nếu tác vụ cần chạy sau response, lấy tenant đã xác thực và job input trong controller, đừng giữ request wrapper/stream/session.

### 16.3. Return CompletableFuture giúp quan sát lỗi

~~~java
CompletableFuture<String> result = worker.calculate("inv-7");

result.whenComplete((value, error) -> {
    if (error != null) {
        reportFailure(error);
    } else {
        reportSuccess(value);
    }
});
~~~

**Snippet**, các methods báo trạng thái do ứng dụng cung cấp. `CompletableFuture` có thể hoàn thành exceptional và cần được quan sát; `@Async void` không trả handle cho caller. Nếu controller trả `CompletableFuture` cho Spring MVC, MVC có cơ chế Servlet async riêng và có thể giữ logical response mở đến completion/timeout — khác với fire-and-forget 202.

### 16.4. Bản thân Spring bean không làm job bền

Bean `VirtualThreadTaskExecutor` không phải job store hoặc distributed scheduler. Đóng context không đảm bảo mọi task fire-and-forget hoàn tất đúng business contract trừ khi bạn đã thiết kế ownership/lifecycle. Với công việc trọng yếu, dùng durable queue/job state, bounded in-flight tasks, task cancellation và graceful shutdown.

## 17. Maven Java 21 + Spring 6.2: demo @Async có context scope

Ví dụ này tạo một bean `VirtualThreadTaskExecutor` và một `@Async` method; kết quả được chờ bằng `Future.get(timeout)`. Trong worker, correlation ID được đặt vào regular ThreadLocal và dọn qua `finally`. Output chứng minh Spring đã dispatch lên virtual thread, không phụ thuộc tên carrier hoặc thời điểm scheduler.

### 17.1. Cấu trúc

~~~text
spring-virtual-context/
├── pom.xml
└── src/main/java/demo/SpringVirtualContextDemo.java
~~~

Tệp **`pom.xml`**:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-virtual-context</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>21</maven.compiler.release>
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

Tệp **`src/main/java/demo/SpringVirtualContextDemo.java`**:

~~~java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

public class SpringVirtualContextDemo {

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class Config {
        @Bean("virtualExecutor")
        VirtualThreadTaskExecutor virtualExecutor() {
            return new VirtualThreadTaskExecutor("demo-vt-");
        }

        @Bean
        Worker worker() {
            return new Worker();
        }
    }

    public static class Worker {
        private static final ThreadLocal<String> CORRELATION =
                new ThreadLocal<>();

        @Async("virtualExecutor")
        public CompletableFuture<String> run(String correlationId) {
            CORRELATION.set(correlationId);
            try {
                String answer =
                        "virtual=" + Thread.currentThread().isVirtual()
                        + ", correlation=" + CORRELATION.get();
                return CompletableFuture.completedFuture(answer);
            } finally {
                CORRELATION.remove();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            Worker worker = context.getBean(Worker.class);

            String result =
                    worker.run("corr-42").get(5, TimeUnit.SECONDS);
            System.out.println(result);
        }
    }
}
~~~

Chạy (Maven với JDK 21+):

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.SpringVirtualContextDemo
~~~

**Stdout do class demo in khi chạy thành công**:

~~~text
virtual=true, correlation=corr-42
~~~

### 17.2. Những gì demo này kiểm chứng

- `@EnableAsync` bật Spring async interceptor.
- Bean `Worker` do container quản lý; main gọi method **qua proxy** khi lấy bean từ context.
- Bean `VirtualThreadTaskExecutor` chọn virtual thread per task cho `@Async("virtualExecutor")`.
- ThreadLocal được set/remove **trong chính worker**; không cần thừa kế raw request context.
- Return type `CompletableFuture` cho phép caller chờ result/exception và có timeout.
- Đóng Spring context sau khi future kết thúc, không để code demo tự thoát JVM khi còn work chưa quan sát.

### 17.3. Những gì demo không chứng minh

Demo không mô phỏng Servlet request lifecycle, DB transaction hoặc sự cố process crash. Nó cũng không chứng minh một triệu requests được xử lý an toàn: cần load test, giới hạn tài nguyên downstream, khả năng interrupt của driver và đo retained heap.

## 18. Lựa chọn executor trong Spring 6.2

| Lựa chọn | Cơ chế | Dùng khi nào | Lưu ý |
| --- | --- | --- | --- |
| `VirtualThreadTaskExecutor` | Một virtual thread cho mỗi task | Cấu hình nhanh cho `@Async`, workload I/O | Ít lựa chọn cấu hình; tự có bulkhead ở service/downstream |
| `SimpleAsyncTaskExecutor` + `setVirtualThreads(true)` | Tạo virtual thread mỗi task | Cần concurrency limit/task decoration/closure tracking | Không phải thread pool tái sử dụng; phải quản lý giới hạn và close rõ |
| `ThreadPoolTaskExecutor` platform thread | Pool worker giới hạn, queue capacity | Code cũ hoặc workload cần pooling và backpressure | Dùng platform worker theo cấu hình mặc định, không đồng nghĩa virtual |
| JDK `Executors.newVirtualThreadPerTaskExecutor()` | ExecutorService per-task | Java SE, fan-out scoped với try-with-resources | Không có giới hạn tài nguyên tự động |
| Spring Boot virtual-thread configuration | Tùy version/auto-config cụ thể | App Boot có cấu hình kiểm soát | Không tự khiến mọi executor, custom bean, callback thay đổi |

### 18.1. SimpleAsyncTaskExecutor hỗ trợ virtual thread từ Spring 6.1

~~~java
@Bean("limitedVirtualExecutor")
SimpleAsyncTaskExecutor limitedVirtualExecutor() {
    SimpleAsyncTaskExecutor e =
            new SimpleAsyncTaskExecutor("limited-vt-");
    e.setVirtualThreads(true);
    e.setConcurrencyLimit(32);
    return e;
}
~~~

Đây là **Spring configuration snippet** cần import `org.springframework.core.task.SimpleAsyncTaskExecutor`, `@Bean` và Java 21+. `setConcurrencyLimit(32)` hạn chế lượng task active qua executor wrapper; giới hạn này có thể **block submitter** khi đầy (tùy policy), nên xem thêm `setRejectTasksWhenLimitReached(true)` (Spring 6.2) nếu muốn fail fast với rejection rõ. Concurrency limit là **chiến lược áp vào tài nguyên**, không phải 32 virtual carrier threads.

### 18.2. Vì sao không gọi VirtualThreadTaskExecutor là một “pool virtual threads”?

Mô hình Java được khuyến nghị là **thread-per-task**. Pool các virtual threads nhỏ rồi tái sử dụng cho nhiều task làm mất ý nghĩa và lợi thế của việc có logical thread riêng. Nếu bạn cần bảo vệ DB quota, hãy giới hạn **work in flight** bằng semaphore/bounded queue hoặc service bulkhead, không pool virtual threads như platform workers.

### 18.3. Cấu hình ExecutorConfigurationSupport.setVirtualThreads

Spring Framework 6.2 có `setVirtualThreads(boolean)` trên lớp hỗ trợ cấu hình một số executor/scheduler có lifecycle management. Đây là một tùy chọn **riêng cho class và version phù hợp**, không được tự suy tất cả Spring thread pools trở thành `newVirtualThreadPerTaskExecutor` y hệt. Đọc Javadoc implementation thực tế, kiểm tra số thread, scheduling và shutdown semantics.

## 19. Giới hạn database và HTTP downstream bằng semaphore

Nếu có 30 DB connections nhưng mở 100.000 virtual threads cùng gọi DB, số thread nhẹ không làm tăng số connections. Một cách kiểm soát:

~~~java
final Semaphore dbPermits = new Semaphore(20);

void callDatabase(JobInput input) throws InterruptedException {
    dbPermits.acquire();
    try {
        databaseService.process(input);
    } finally {
        dbPermits.release();
    }
}
~~~

Đây là **snippet service**, giả định `JobInput` và `databaseService`. Chỉ release nếu đã acquire thành công; đặt `acquire()` ngoài `try` đảm bảo khi acquire ném `InterruptedException` chưa có permit, không release nhầm. Semaphore chỉ giới hạn ở **một JVM**; downstream còn cần quota và rate limit trên cụm.

### 19.1. Timeout khi xin permit

~~~java
boolean acquired =
        dbPermits.tryAcquire(2, TimeUnit.SECONDS);
if (!acquired) {
    throw new IllegalStateException("DB busy");
}
try {
    databaseService.process(input);
} finally {
    dbPermits.release();
}
~~~

**Snippet** ném `InterruptedException` từ `tryAcquire` lên caller. Hết thời gian xin permit nên có một error/retry policy rõ, không biến thành hàng chờ vô tận. Semaphore không thay timeout query hoặc rollback transaction.

### 19.2. Chỉ nâng concurrency nếu downstream chịu được

Dùng metrics như connection pool waiting count, p95/p99 latency, timeouts, heap retention, queued jobs và external 429/503 để chọn concurrency budget. Tăng limit từ 20 lên 2.000 trong JVM có thể làm latency/khả năng sập DB tệ hơn dù virtual threads rẻ.

## 20. Pinning carrier và khác biệt giữa các phiên bản Java

Trong **JDK 21–23**, virtual thread có thể bị **pin** vào carrier trong một số tình huống, đáng chú ý khi blocking trong `synchronized` monitor hoặc native/foreign code. Một virtual thread pinned đang chờ có thể giữ carrier, khiến throughput giảm nếu lặp lại thường xuyên. Đây là vấn đề **khả năng mở rộng**, không mặc nhiên sai logic hay race.

Từ **JDK 24**, thay đổi trong JEP 491 loại bỏ phần lớn trường hợp pin do `synchronized` monitor: virtual thread có thể block/yield quanh monitor mà không giữ carrier theo cách cũ. Tuy nhiên, không nên kết luận **mọi pinning hoặc mọi blocking library đều không còn tồn tại**; native/foreign và các ranh giới JVM/driver đặc thù vẫn phải đo.

### 20.1. Không “sửa” đồng bộ chỉ vì chạy virtual thread

~~~java
synchronized (lock) {
    updateSharedState();
}
~~~

**Snippet**. `synchronized` bảo vệ dữ liệu đồng thời đúng theo Java Memory Model. Không bỏ synchronized để tránh pinning mà tạo race. Trên JDK 21–23, tránh giữ monitor **quá lâu quanh I/O có thể block** ở điểm nóng. Trên JDK 24+, đánh giá lại theo JEP 491; vẫn cần lock scope hợp lý và bounded I/O.

### 20.2. Quan sát JFR và thread dumps

JDK Flight Recorder có event liên quan virtual threads như `jdk.VirtualThreadPinned` trong các JDK phù hợp. Dùng JFR, `jcmd` và thread dumps để tìm tasks bị block, pinning, long waits, lock contention. Khả năng event/threshold có thể thay đổi theo JDK version; kiểm tra tài liệu đúng phiên bản và baseline thực tế trước khi kết luận.

Một command minh họa khi đã có recording:

~~~bash
jfr print --events jdk.VirtualThreadPinned recording.jfr
~~~

Đây là **command** để đọc JFR đã được tạo, không tự tạo recording. Không đợi event xuất hiện mới kiểm tra DB pool/HTTP timeouts; virtual thread slowdown có thể do downstream saturation chứ không chỉ do carrier pinning.

### 20.3. Carrier count không phải concurrency limit của DB

Runtime có thể dùng một số carrier threads tương đối nhỏ để phục vụ nhiều virtual threads đang chờ. Nhưng khi 10.000 tasks cùng yêu cầu DB, chúng vẫn tranh connection pool/locks, có thể giữ lượng lớn heap state và timeouts. Điều chỉnh scheduler parallelism không thay việc quản lý số job active.

## 21. Error handling và exception khi dùng virtual threads

### 21.1. submit: Future là kênh quan sát lỗi

~~~java
try (ExecutorService executor =
             Executors.newVirtualThreadPerTaskExecutor()) {
    Future<String> f = executor.submit(() -> {
        throw new IllegalStateException("business failed");
    });

    try {
        f.get();
    } catch (ExecutionException ex) {
        System.out.println(ex.getCause().getMessage());
    }
}
~~~

Đây là **snippet Java 21+**, cần imports `Executors`, `ExecutorService`, `Future`, `ExecutionException`. Output từ catch là `business failed`. `Future.get()` không tự log lỗi nếu caller không đọc; cần expose kết quả/metric/status.

### 21.2. execute: exception có thể đi qua uncaught handler

Nếu một virtual thread trực tiếp ném unchecked exception ra khỏi `Runnable.run()`, uncaught exception handling áp dụng theo Java Thread semantics. Khi dùng executor wrapper, exception handling có thể thay đổi (ví dụ FutureTask giữ exception của `submit`). Đừng chỉ gắn một `Thread.UncaughtExceptionHandler` rồi cho rằng đã quan sát mọi lỗi của `submit`/Spring `@Async`.

### 21.3. Spring @Async void

Một method `@Async void` không trả Future cho caller; Spring có `AsyncUncaughtExceptionHandler` dành cho exception chưa bắt của async void invocation. Với `@Async CompletableFuture`, exception nên được quan sát qua returned Future hoặc job status. Lỗi sau response 202 **không** thể sửa HTTP response đã hoàn tất.

### 21.4. InterruptedException khác business failure có thể retry

Một yêu cầu dừng có chủ đích thường phải được báo là `CANCELLED`/`TIMED_OUT`, không phải transient network error để auto-retry vô hạn. Nếu retry đã tạo side effect không chắc chắn, cần idempotency key và reconciliation trước khi gửi lại, kể cả task đã bị interrupt.

## 22. Kiểm thử virtual-thread lifecycle đúng cách

### 22.1. Xác minh có thật là virtual thread

Dùng `Thread.currentThread().isVirtual()` **bên trong task đang chạy**, không chỉ xem bean configuration. Trong Spring demo, chuỗi `virtual=true` là bằng chứng method thực thi trên một virtual thread.

### 22.2. Test ThreadLocal isolation

- Main đặt regular ThreadLocal = A.
- Tạo virtual thread mới; trong đó `get()` phải là null (nếu không có propagation tùy chỉnh).
- Task B đặt B; task C ở thread khác không nhìn thấy B.
- Khi có child inheritance, test riêng `InheritableThreadLocal` và factory option.
- Đừng đọc private ThreadLocal map bằng reflection để xác minh vì không cần và dễ phụ thuộc implementation.

### 22.3. Test interrupt bằng latch, không sleep

- Worker báo `started` rồi block ở API interruptible.
- Caller nhận `started` và gọi `cancel(true)`.
- Đợi latch `finished` và assert worker nhận interrupt.
- Assert Future bị cancelled; **không** lấy Future cancelled làm bằng chứng cleanup đã chạy nếu chưa đợi `finished`.

### 22.4. Test không giữ request sau response

Tạo mock/fake `HttpServletRequest` chỉ cho đọc trong request boundary. Controller tạo snapshot rồi gửi `ImportInput` sang async service. Sau khi request kết thúc, worker không gọi `RequestContextHolder` hoặc raw request. Có thể dùng latch để kiểm soát thứ tự. Test không dựa vào việc container có ném exception khi request bị recycle hay không.

### 22.5. Test transaction riêng trên worker

Với `@Transactional` ở service method thực thi trên virtual thread, assert commit/rollback theo quy tắc proxy; không giả định caller transaction đã được copy qua. Test dùng database thật hoặc integration fixture khi cần để kiểm tra resource closing và connection pool limits.

### 22.6. Test shutdown

Tạo vài blocking jobs, bắt đầu graceful shutdown với deadline, kiểm tra task completed/cancelled đúng policy, đợi scope và không rò resource. Thử process interruption, application context close, timeout và pending queue. Không dùng `System.exit` làm “cleanup”.

### 22.7. Test downstream bulkhead

Cho 50 virtual tasks tranh semaphore 5 permits, đo maximum active calls không vượt 5 trong một JVM. Dùng counter + latch, không dùng `Thread.sleep` làm assertion cứng. Thử interrupt khi task đang đợi permit và xác minh không release permit chưa sở hữu.

### 22.8. Test pinned/blocked performance

Chạy cùng workload trên JDK 21–23 và JDK 24+ nếu dự án nâng phiên bản; dùng JFR, p95 latency, carrier utilization và DB pool metrics để phân biệt pinning với thiếu downstream capacity. Không mặc định một “benchmark nhỏ trên laptop” chứng minh mọi phiên bản production an toàn.

## 23. Những sai lầm thường gặp

**1. “Virtual thread không có ThreadLocal.”** Sai. Nó có ThreadLocal riêng như Thread khác.

**2. “ThreadLocal của virtual thread nằm trên carrier.”** Sai. Nó gắn với virtual Thread object; đổi carrier không đồng nghĩa đổi logical ThreadLocal.

**3. “Dùng virtual threads thì ThreadLocal cache sẽ càng tiết kiệm.”** Không đúng với mô hình thread-per-task: mỗi task có thể khởi tạo object mới trong cache riêng.

**4. “Virtual thread nhẹ nên có thể giữ toàn bộ HttpServletRequest trong job.”** Sai. Request lifecycle vẫn có giới hạn và giữ object graph có thể tạo memory retention.

**5. “@Async tự mang mọi RequestContextHolder/MDC/SecurityContext sang virtual thread.”** Sai. Cần explicit propagation phù hợp lifecycle hoặc snapshot nhỏ.

**6. “InheritableThreadLocal là copy sâu tự động.”** Sai. Nó nhận initial value; các references tới object mutable có thể dùng chung.

**7. “Giá trị parent ThreadLocal đổi sẽ live-update ở child.”** Sai với InheritableThreadLocal mặc định. Child lấy initial value vào lúc tạo.

**8. “Thế hệ virtual threads mới không cần gọi ThreadLocal.remove().”** Không nên dùng làm quy tắc chung; cleanup có giá trị, đặc biệt khi code có nested contexts, references nhạy cảm và tái sử dụng với platform workers.

**9. “Future.cancel(true) giết cưỡng bức cả HTTP/SQL side effects.”** Sai. Đó là yêu cầu interrupt, không phải rollback distributed transaction.

**10. “Future.get(timeout) hết giờ sẽ tự hủy task.”** Sai. Timeout của caller không đồng nghĩa cancellation của task.

**11. “InterruptedException là transient error; cứ retry.”** Sai trong nhiều trường hợp, vì nó thường báo cancellation/shutdown.

**12. “Thread.interrupted() chỉ đọc cờ.”** Sai. Nó còn xóa cờ; `isInterrupted()` không xóa.

**13. “Catch InterruptedException rồi bỏ qua là an toàn.”** Sai. Có thể làm shutdown treo hoặc task tiếp tục side effect sau khi caller đã hủy.

**14. “Virtual thread giữ JVM chạy tới khi job xong.”** Sai. Nó là daemon; cần join/owner/durable store theo yêu cầu.

**15. “ExecutorService.close() chỉ gửi shutdown và không chờ.”** Sai: nó orderly shutdown và chờ termination theo contract.

**16. “Executor virtual-thread-per-task là một pool virtual threads.”** Sai. Mỗi submit có logical virtual thread riêng.

**17. “Tăng virtual threads từ 100 lên 100.000 làm SQL nhanh hơn 1.000 lần.”** Không. Connection pool, locks, CPU và external quota vẫn giới hạn throughput.

**18. “Bật SimpleAsyncTaskExecutor.setVirtualThreads(true) cũng đã có bounded queue.”** Không tự bảo đảm; cần concurrency/rejection policy cụ thể, và loại executor đó không phải pool tái sử dụng.

**19. “@Async tự hoạt động khi gọi this.asyncMethod().”** Trong proxy mode mặc định, self-invocation có thể bypass proxy.

**20. “@Async void nếu lỗi sẽ tự trả lỗi HTTP cho response đã kết thúc.”** Sai. Phải quan sát qua handler/job state; response cũ không thể sửa.

**21. “JDK 24 giải quyết mọi hình thức pinning hoặc blocking.”** Sai. Nó xử lý phần quan trọng do synchronized monitor; các hạn chế khác vẫn cần đo.

**22. “Không có jdk.VirtualThreadPinned event nghĩa là không có latency issue.”** Sai. DB queue, I/O timeouts, CPU contention và oversubscription vẫn xảy ra.

**23. “Một task chạy xong là business workflow chắc đã commit.”** Sai nếu có async downstream, message delivery hoặc external side effects chưa được xác nhận.

**24. “Semaphore trong một JVM bảo đảm quota toàn cluster.”** Sai. Cần distributed rate limits/ownership khi có nhiều pods.

**25. “Snapshot record tự deep-immutable mọi field.”** Sai nếu chứa mảng hoặc mutable objects.

**26. “ThreadLocal.remove là đủ để đóng Connection.”** Sai. Resource phải close/rollback trong đúng scope.

## 24. Câu hỏi tự kiểm tra

**Câu 1:** Virtual thread có thể dùng `ThreadLocal<String>` không?

**Đáp án:** có. Mỗi virtual Thread có bảng ThreadLocal riêng. Chuyển carrier không xóa hay trao đổi context đó.

**Câu 2:** Vì sao một `ThreadLocal<SimpleDateFormat>` có thể làm allocation tăng trên vthreads?

**Đáp án:** một virtual thread thường chỉ đại diện một task và không tái sử dụng; hàng nghìn threads có thể khởi tạo hàng nghìn formatter thay vì vài formatter trên pool platform workers.

**Câu 3:** Nếu main đặt regular `ThreadLocal=main` rồi tạo virtual child mới, child luôn thấy “main” chứ?

**Đáp án:** không. Regular ThreadLocal không có inheritance mặc định; muốn truyền context cần explicit mechanism. `InheritableThreadLocal` có semantics riêng.

**Câu 4:** Có được truyền `ServletRequestAttributes` qua `InheritableThreadLocal` để chạy sau response?

**Đáp án:** không nên. Nó có thể giữ request/response ngoài lifecycle và tạo rủi ro memory/security. Truyền immutable job snapshot nhỏ.

**Câu 5:** `Thread.interrupted()` khác `isInterrupted()` thế nào?

**Đáp án:** static `Thread.interrupted()` đọc rồi xóa interrupt status của thread hiện hành; instance `isInterrupted()` chỉ đọc, không xóa.

**Câu 6:** Sau khi catch `InterruptedException` mà không thể propagate, thường nên làm gì?

**Đáp án:** restore interrupt status và thoát/hoàn tất cleanup có giới hạn, không bỏ qua để tiếp tục vô hạn.

**Câu 7:** `Future.cancel(true)` trả true có chứng minh DB transaction đã rollback?

**Đáp án:** không. Future có thể cancelled trong khi task thực tế/remote operation đã tạo side effect; cần idempotency và reconciliation.

**Câu 8:** Vì sao không chỉ dựa `future.get(timeout)` để hủy task?

**Đáp án:** `TimeoutException` chỉ cho biết caller chờ quá hạn, không tự gửi interrupt. Cần cancellation/deadline policy rõ.

**Câu 9:** Virtual threads có giữ JVM chạy nếu main return không?

**Đáp án:** không; chúng là daemon. Cần join/ExecutorService.close hoặc owner/durable job store tùy SLA.

**Câu 10:** Spring `VirtualThreadTaskExecutor` có cần Java 21 không?

**Đáp án:** có, class dựa trên virtual threads của JDK 21+. Với Spring 6.2, cấu hình `@Async("virtualExecutor")` và gọi qua proxy.

**Câu 11:** Vì sao tăng số virtual threads không giải quyết được DB connections chỉ có 20?

**Đáp án:** mọi query vẫn cần connection. Một lượng lớn tasks chỉ làm nhiều thao tác cùng chờ; cần bulkhead/semaphore và query timeouts.

**Câu 12:** JDK 24 có làm mọi synchronized block trở nên không cần lock?

**Đáp án:** không. JEP 491 thay đổi chuyện pinning khi blocking trong monitor, không xóa nhu cầu đồng bộ dữ liệu.

**Câu 13:** Task có trace ID thì nên giữ nguyên `HttpServletRequest` để log về sau?

**Đáp án:** không. Lấy một correlation ID hợp lệ trong request boundary và truyền qua argument hoặc wrapper có `try/finally`.

**Câu 14:** Một `@Async void` bị lỗi sau khi HTTP response 202 đã trả về, client biết trạng thái thế nào?

**Đáp án:** cần jobId/durable status/log/notification riêng; không thể sửa response đã hoàn tất.

## 25. Checklist code review và vận hành

- [ ] Xác nhận JDK **21+**, Spring version và executor thực sự tạo virtual thread.
- [ ] Không gọi virtual threads là pool threads; mỗi task có logical Thread riêng.
- [ ] Workload chính chờ I/O, không nhầm virtual threads với tăng tốc CPU-bound.
- [ ] Regular ThreadLocal trên virtual thread được đặt/đọc/xóa theo phạm vi rõ.
- [ ] Đã audit `InheritableThreadLocal`; tránh kế thừa raw request/security objects không cần thiết.
- [ ] Không cache mutable/expensive resources trong ThreadLocal theo task.
- [ ] Không giữ raw `HttpServletRequest`, `MultipartFile`, `ServletRequestAttributes` hay session trong background task.
- [ ] Task input là snapshot nhỏ, immutable, validated/authorized, không chứa token hoặc toàn bộ body.
- [ ] MDC/trace context được truyền có chọn lọc; cleanup/restore trong `finally`.
- [ ] `@Async` gọi qua Spring proxy; return Future được quan sát hoặc handler cho void được cấu hình.
- [ ] Không giả định caller transaction/SecurityContext tự di chuyển tới worker.
- [ ] `InterruptedException` được propagate hoặc restore status và dừng hợp tác.
- [ ] Phân biệt `Thread.interrupted` (clear) với `isInterrupted` (read).
- [ ] `Future.cancel(true)`/`shutdownNow` chỉ cố ngắt; blocking I/O và side effect có timeout/compensation riêng.
- [ ] `Future.get(timeout)` không bị hiểu nhầm là tự hủy task.
- [ ] Có deadline, connect/read/query timeouts, retry budget và idempotency.
- [ ] Trực tiếp tạo thread thì `join` hoặc owner phù hợp; executor thì close/shutdown theo lifecycle.
- [ ] Không coi virtual daemon threads là durable background jobs sau response.
- [ ] Với việc cần sống qua restart, persist job state/queue và hỗ trợ reconciliation.
- [ ] Có semaphore/bulkhead/bounded in-flight jobs cho database và downstream.
- [ ] Có metric active tasks, pending waits, cancellation, timeouts, failure và pool saturation.
- [ ] Pinning/JFR được kiểm chứng theo JDK version; không áp kết luận JDK 21 cho JDK 24+ máy móc.
- [ ] Unit test ThreadLocal/inheritance và interrupt bằng Future/latch, không phụ thuộc sleep.
- [ ] Integration test Spring context/proxy, DB transaction và graceful shutdown.
- [ ] Khi đóng application, các task in-flight được xử lý/cancel/mark trạng thái theo business policy.
- [ ] Không sửa các tác dụng phụ nghiệp vụ bằng việc đơn thuần giảm số virtual threads hoặc tăng carrier threads.

## 26. Tổng kết

**Virtual thread vẫn là Thread.** Nó có ThreadLocal, inheritance semantics, interrupt status, lifecycle và failure giống những luồng Java khác ở tầng hợp đồng lập trình. Mỗi virtual thread đại diện một task nên dễ viết code I/O tuần tự, nhưng **không** tự mang theo mọi Spring request/security/transaction context và không biến ThreadLocal cache thành tài nguyên dùng chung có hiệu quả.

Với Java 21+, dùng `Thread.ofVirtual` hoặc `Executors.newVirtualThreadPerTaskExecutor`, quản lý task bằng `join`, `Future.get` và `try-with-resources`/shutdown. Với Spring 6.2, dùng `VirtualThreadTaskExecutor` hoặc `SimpleAsyncTaskExecutor.setVirtualThreads(true)` với `@Async`, truyền **snapshot đầu vào tối thiểu**, đặt/dọn context trong phạm vi task và theo dõi exception. Khi hủy, hiểu `interrupt/cancel` là **cooperative cancellation**; vẫn cần timeout, cleanup, idempotency và durable status cho việc quan trọng.

Virtual threads mở rộng khả năng chịu tải I/O, **không** mở rộng vô hạn DB pool/CPU/quota. Quản lý tốt vòng đời, context và nguồn tài nguyên mới là điều kiện để triển khai an toàn.

## Tài liệu tham khảo chính thức

- Java SE 21 — Virtual Threads, ThreadLocal và per-task executor: https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html
- JDK 21 — `Thread`/`Thread.Builder` (virtual/inheritance/interrupt): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html
- JDK 21 — `ThreadLocal`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html
- JDK 21 — `InheritableThreadLocal`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/InheritableThreadLocal.html
- JDK 21 — `ExecutorService` và `Future`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html
- JDK 21 — `Executors.newVirtualThreadPerTaskExecutor`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html
- JDK 21 — `CountDownLatch`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CountDownLatch.html
- OpenJDK JEP 444 — Virtual Threads (JDK 21): https://openjdk.org/jeps/444
- OpenJDK JEP 491 — Synchronize Virtual Threads without Pinning (JDK 24): https://openjdk.org/jeps/491
- Spring 6.2 — `VirtualThreadTaskExecutor`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/task/VirtualThreadTaskExecutor.html
- Spring 6.2 — `SimpleAsyncTaskExecutor`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/task/SimpleAsyncTaskExecutor.html
- Spring 6.2 — ExecutorConfigurationSupport virtual thread lifecycle options: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ExecutorConfigurationSupport.html
- Spring Framework — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Security — Concurrency Support: https://docs.spring.io/spring-security/reference/servlet/integrations/concurrency.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-008">Nhận diện tình huống giữ luồng nền tảng và khác biệt theo phiên bản JDK</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện tình huống giữ luồng nền tảng và khác biệt theo phiên bản JDK

## 1. Mục tiêu: hiểu "giữ carrier" trước khi tối ưu virtual threads

**Virtual thread** là một `java.lang.Thread` được JVM quản lý ở mức nhẹ hơn so với **platform thread** vốn gắn với OS thread. Khi virtual thread chạy, JVM **mount** nó lên một platform thread (gọi là **carrier**). Khi virtual thread thực hiện thao tác chờ mà JDK hỗ trợ **unmount**, JVM tháo nó khỏi carrier để carrier phục vụ virtual thread khác.

**Pinning** là tình huống virtual thread **không thể unmount** khi nó sắp thực hiện một thao tác chờ, khiến carrier platform thread bị giữ và có thể bị block cùng với virtual thread. Pinning kéo dài hoặc xảy ra thường xuyên khiến một số ít carriers quý giá bị chiếm, làm giảm khả năng mở rộng concurrency.

Có hai ý phải phân biệt:

- **Đang chạy CPU**: virtual thread cần một carrier để thực thi Java instructions. Carrier bận trong lúc CPU chạy không tự gọi là pinning; thêm virtual threads không giúp tác vụ CPU thuần nhanh hơn.
- **Đang chờ nhưng không thể nhả carrier**: đây mới là điểm nguy hiểm của **pinning**. Virtual thread lẽ ra có thể chờ mà carrier được dùng lại, nhưng một frame/lý do đặc biệt khiến carrier phải bị giữ.

~~~text
Unpinned blocking (JDK hỗ trợ):

virtual A -- mount --> carrier-1 -- thực thi -->
             bắt đầu chờ I/O
virtual A -- unmount --> trạng thái chờ
carrier-1 -----------> chạy virtual B
I/O xong, virtual A có thể mount lên carrier khác


Pinned blocking:

virtual A -- mount --> carrier-1
virtual A -- chờ trong trạng thái bị pin
carrier-1 -- bị giữ/block --> không phục vụ virtual B
                           trong thời gian pinned wait


CPU-bound:

virtual A -- mount --> carrier-1 -- thực thi CPU liên tục
carrier-1 đang có việc, nhưng không suy ra pinning.
~~~

**Không thể bỏ qua phiên bản JDK.** Trong **JDK 21–23**, `synchronized` là một nguồn pinning quan trọng khi virtual thread thực hiện blocking operation bên trong vùng giữ monitor. Từ **JDK 24**, **JEP 491 — Synchronize Virtual Threads without Pinning** loại bỏ nguyên nhân monitor-related pinning phổ biến này. Tuy nhiên **native/JNI/foreign-function frames** và một số trạng thái VM đặc biệt vẫn có thể khiến virtual thread giữ carrier; phải đo bằng công cụ trên runtime đang chạy.

Bài thuộc nhóm **Luồng ảo** của Spring Framework – concurrency, tập trung vào nhận diện carrier pinning, thay đổi theo JDK, các tình huống trong Spring/ORM/HTTP và chiến lược kiểm chứng thực tế.

## 2. Thuật ngữ: virtual thread, carrier, mount, unmount, pinning

| Khái niệm | Nghĩa | Điều cần hiểu |
| --- | --- | --- |
| Virtual thread | Java Thread do JVM lập lịch, có stack/lifecycle ảo | Phù hợp với nhiều công việc I/O-bound đồng thời |
| Platform thread | Java Thread gắn với một OS thread | Số lượng hữu hạn, tạo/duy trì đắt hơn |
| Carrier | Platform thread tạm thực thi virtual thread | Một carrier phục vụ các virtual threads lần lượt |
| Mount | Virtual thread được đặt lên carrier để chạy | Trong lúc chạy CPU vẫn dùng carrier |
| Unmount | Virtual thread nhường carrier khi chờ được hỗ trợ | Carrier rảnh phục vụ công việc khác |
| Pinning | Virtual thread không thể unmount trong một blocking operation | Carrier bị giữ cho tới khi điều kiện pinning kết thúc |
| Blocking operation | Sleep, park, I/O, chờ lock hoặc điều kiện khác | Không phải mọi blocking call đều pin |
| Monitor | Cơ chế khóa nội tại dùng bởi `synchronized` | Ý nghĩa với pinning thay đổi mạnh ở JDK 24 |
| Native/foreign frame | Đoạn thực thi thông qua JNI hoặc Foreign Function & Memory API | Còn là nguồn pinning tiềm năng ở JDK mới |
| Carrier starvation | Các carriers bị chiếm khiến virtual threads khác khó được chạy | Có thể do pinning kéo dài, CPU bão hòa hoặc nguồn khác; cần chẩn đoán |

Một số tài liệu mô tả virtual threads như "mỗi request một thread" và nói có thể tạo rất nhiều virtual threads. Điều đó không có nghĩa JVM có vô hạn CPU, connections, file descriptors hay tài nguyên DB. Virtual threads **giảm chi phí giữ một thread lúc chờ**, chứ không làm downstream trở nên vô hạn.

### 2.1. Các loại chờ không giống nhau

- `Thread.sleep(...)` trên virtual thread thông thường có thể unmount nếu không gặp nguyên nhân pinning.
- Một số socket/network I/O JDK hỗ trợ unmount khi chờ.
- `ReentrantLock.lock()` được thiết kế để phối hợp với cơ chế park phù hợp; trong JDK 21–23 nó có thể là phương án thay monitor khi phải đợi I/O dài trong critical section.
- `synchronized` có hành vi **theo phiên bản**; không copy kết luận JDK 21 sang JDK 25.
- Một lời gọi qua JNI/FFM có thể chứa native frame khiến virtual thread không thể unmount trong một chỗ chờ đặc biệt.
- Một virtual thread chạy vòng lặp xử lý CPU mà không chờ sẽ liên tục chiếm carrier khi được chạy; đây là **CPU saturation**, không phải pinning.

### 2.2. Pinning không tự động là lỗi chức năng

Một virtual thread bị pin vài microseconds có thể không ảnh hưởng gì đáng kể. Ngay cả khi một đoạn có `synchronized`, không phải mọi lần đi vào synchronized đều gây blocking, và không phải mọi blocking đều đủ lâu để ảnh hưởng throughput.

**Điều đáng quan tâm** là **tần suất × thời gian giữ carrier × số concurrent executions** trong đúng workload, cộng với trạng thái pool, database và external API. Bắt gặp từ khóa `synchronized` trong code không đủ để kết luận "production có vấn đề pinning".

## 3. Bảng khác biệt theo phiên bản JDK

Virtual threads được chuẩn hóa trong **JDK 21** (JEP 444); các nhánh JDK 19 và 20 từng cung cấp dưới dạng preview nên không dùng hành vi preview làm baseline production ở đây.

| Phiên bản | Trong `synchronized` rồi chặn | Native/JNI/FFM | Công cụ chẩn đoán |
| --- | --- | --- | --- |
| JDK 21 | Có nguy cơ pin carrier khi blocking trong monitor | Có thể pin | JFR `jdk.VirtualThreadPinned`; `-Djdk.tracePinnedThreads=full/short` |
| JDK 22 | Tương tự JDK 21 về monitor-related pinning | Có thể pin | JFR và legacy trace property |
| JDK 23 | Tương tự JDK 21 về monitor-related pinning | Có thể pin | JFR và legacy trace property |
| **JDK 24** | **JEP 491 bỏ monitor-related pinning thông thường** | Một số native/foreign paths vẫn có thể pin | **JFR**, với thông tin reason/carrier được cải tiến |
| JDK 25 (LTS) | Kế thừa mô hình JDK 24, không mặc nhiên pin do `synchronized` | Vẫn có các trường hợp phải kiểm tra | JFR và thread dumps |
| JDK 26+ | Tài liệu Oracle tiếp tục không liệt kê monitor là nguyên nhân pinning thông thường | Vẫn cần cảnh giác native/foreign và VM cases | JFR, `jcmd`, JMX scheduler metrics |

**Nguồn tham khảo chính:** Oracle JDK 21 Virtual Threads Guide mô tả `synchronized` và native/foreign code là nguồn pinning; Oracle JDK 24 Migration Guide xác nhận JEP 491; Oracle JDK 25/26 Virtual Threads Guide nêu native/foreign là nguyên nhân còn lại.

**Không nói:** "JDK 24 xóa toàn bộ pinning", "JDK 21 mọi synchronized đều pin" hoặc "JDK 25 không còn cần JFR". Những mệnh đề đó đều sai hoặc quá rộng.

### 3.1. JEP 491 đã thay đổi gì?

Trước JDK 24, khi virtual thread đang giữ Java monitor qua `synchronized`, JVM không thể unmount nó tại một số blocking points. Nếu thread gọi `Thread.sleep(300)` hoặc blocking I/O bên trong synchronized section, carrier có thể phải ở lại chờ.

Từ JDK 24, monitor implementation được thay đổi để virtual thread có thể **unmount khi chặn bên trong synchronized**, khi chờ enter monitor đang bị tranh chấp hoặc trong các tình huống monitor wait được hỗ trợ. Mục tiêu là tránh carrier starvation do Java intrinsic monitors.

Điều đó **không thay đổi semantics mutual exclusion**. Nếu 100 virtual threads cùng tranh một object lock, mỗi lần chỉ có một thread giữ monitor, bất kể JDK có pin hay không. Vấn đề **lock contention** vẫn tồn tại, nhưng nó không phải cùng vấn đề **carrier pinning**.

### 3.2. Vì sao không nên khuyên "thay hết synchronized bằng ReentrantLock"?

Với **JDK 21–23**, việc chuyển **những synchronized critical sections chứa blocking lâu và thường xuyên** sang `ReentrantLock` có thể giảm monitor-related pinning, nếu code bảo đảm lock/unlock đúng và giữ nguyên memory/ordering semantics.

Với **JDK 24+**, thay synchronized **chỉ vì tránh pinning do Java monitor** nhìn chung không còn cần. Nên chọn `ReentrantLock` khi cần những semantics riêng như `tryLock`, fairness, interruptible acquisition hoặc `Condition`; không thay hàng loạt nếu không có số liệu.

Trên mọi JDK, **thu hẹp critical section và không giữ khóa qua remote I/O khi có thể** vẫn giúp giảm contention và tail latency. JEP 491 giải quyết carrier availability, không làm serialized critical sections trở thành parallel.

## 4. Tình huống 1: synchronized + Thread.sleep hoặc blocking I/O

Mã minh họa:

~~~java
private static final Object LOCK = new Object();

void callRemoteUnderLock() throws InterruptedException {
    synchronized (LOCK) {
        Thread.sleep(300); // mô phỏng một chờ tương đối dài
    }
}
~~~

Đây là **mảnh code**, cần đặt trong class, imports khi có thêm kiểu. `Thread.sleep` dùng để mô phỏng blocking; ứng dụng thực có thể là socket read, JDBC query hoặc HTTP request.

- **JDK 21–23:** nếu virtual thread đi vào synchronized rồi bị block ở sleep, có nguy cơ giữ carrier thay vì unmount. Với nhiều locks độc lập và nhiều virtual threads, hậu quả đáng kể có thể là các carriers bị chiếm đồng thời.
- **JDK 24+:** Java monitor không còn tự gây pinning trong trường hợp blocking như trên. Dù vậy việc giữ monitor 300 ms vẫn làm các callers khác cần cùng monitor phải chờ.
- **Mọi JDK:** nếu remote call cần timeout, lock không thay thế timeout. Giữ monitor qua external I/O thường không cần thiết và làm tăng contention.

### 4.1. Tránh I/O trong critical section khi có thể

~~~java
Result result = remoteClient.fetch(); // I/O nằm ngoài monitor

synchronized (stateLock) {
    state.apply(result);              // chỉ cập nhật state cục bộ
}
~~~

Đây là mảnh code minh họa, cần định nghĩa `Result`, `remoteClient`, `stateLock`, `state`. **Chỉ chuyển I/O ra ngoài lock nếu không làm hỏng tính đúng đắn**: nếu trạng thái cần được bảo vệ xuyên suốt fetch/apply như một operation atomic, phải thay bằng protocol khác (version/compare-and-set, transaction hoặc idempotent replay) chứ không được tự ý bỏ lock.

### 4.2. ReentrantLock là lựa chọn khi cần semantics phù hợp

~~~java
private final ReentrantLock lock = new ReentrantLock();

void runWithLock() {
    lock.lock();
    try {
        performBlockingOperation();
    } finally {
        lock.unlock();
    }
}
~~~

Mảnh này cần `ReentrantLock` import và method `performBlockingOperation`. Trên JDK 21–23, `ReentrantLock` là một mitigation thực tế cho những vùng buộc phải khóa qua blocking lâu. Nhưng **không nên khóa một remote call dài trừ khi nghiệp vụ yêu cầu**. `ReentrantLock` vẫn serialize callers và vẫn có thể dẫn đến starvation hoặc deadlock nếu code sai.

## 5. Tình huống 2: JNI, native calls và Foreign Function & Memory API

Khi Java gọi native code qua **JNI** hoặc Foreign Function & Memory API, có những trường hợp frame native/foreign ngăn virtual thread unmount đúng lúc nó cần chờ. Ngay cả trên JDK 24+, nguồn pinning này **không bị JEP 491 loại bỏ hoàn toàn**.

~~~text
Virtual thread
   |
   +--> Java code
          |
          +--> JNI / native / foreign call
                    |
                    +--> native frame cần giữ carrier
                    |
                    +--> callback / blocking path
                              |
                              +--> carrier có thể bị giữ
~~~

**Không kết luận rằng cứ gọi native method là chắc chắn đã gây một sự kiện pinning kéo dài.** Vấn đề quan trọng là **thời điểm virtual thread phải chờ nhưng không thể unmount** vì native/VM frames; mức độ phụ thuộc đường thực thi và phiên bản JDK.

Ví dụ các nơi nên khảo sát khi JFR chỉ ra native/foreign reasons:

- Thư viện JNI để nén/giải nén, encryption, image/codec, thiết bị ngoại vi.
- Wrapper truy cập thư viện C/C++ hoặc native database bindings.
- Foreign downcall hoặc upcall phức tạp, đặc biệt có callback sang Java rồi chờ.
- Một số tình huống class loading/class initialization hoặc VM internal frames.
- Một số đường file I/O local có behavior phụ thuộc JDK, driver và hệ điều hành.

**Phân biệt phạm vi:** một JDBC driver thuần Java không tự đồng nghĩa có JNI pinning. Ngược lại, bản thân các tầng TLS, DNS, filesystem hoặc native access bên dưới cũng có thể ảnh hưởng; cần nhìn stack/JFR chứ không đoán từ tên framework.

### 5.1. Không nhầm CPU native dài với carrier waiting pinning

Một native function thực hiện tính toán CPU 300 ms có thể giữ một OS thread để chạy native code. Dù như vậy vẫn giảm capacity, nó không giống một virtual thread **chờ I/O mà JVM lẽ ra có thể unmount**. Đọc JFR event, CPU profile và thời gian native để phân loại. Điều chỉnh thread count không giải quyết phép tính CPU nặng.

## 6. Tình huống 3: class initialization, contention và chờ bất thường

Một số lộ trình trong JVM liên quan tới class initialization và VM frames có thể làm một virtual thread chờ mà vẫn giữ carrier, đặc biệt khi class initializer của thread khác còn chạy. Oracle JDK mới có ví dụ `jdk.VirtualThreadPinned` gắn với **chờ một class initializer**.

Ví dụ minh họa:

~~~java
final class SlowInitialization {
    static {
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void use() { }
}
~~~

Đây là mảnh code minh họa, không phải chương trình phát hiện pinning. Nếu nhiều threads cố load/use cùng class lúc initialization đang diễn ra, cần xem runtime/JFR để xác định có thực sự bị pin hay chỉ chờ lock/initialization bình thường.

**Khuyến nghị vận hành:**

- Đừng thực hiện remote I/O dài trong static initializer.
- Không dùng class initialization như cơ chế đồng bộ tác vụ nền.
- Chủ động warm up các libraries có startup cost lớn khi phù hợp.
- Không tự sửa native/classloading theo suy đoán; quan sát **pinned reason, blocking operation, stack và duration** trước.

## 7. Tình huống 4: chờ tài nguyên không phải carrier pinning

Một ứng dụng chuyển sang virtual threads rồi throughput vẫn thấp. Những nguyên nhân phổ biến có thể là **tài nguyên downstream** chứ không phải pinning.

| Triệu chứng | Nguyên nhân có thể | Cần kiểm tra |
| --- | --- | --- |
| Nhiều virtual threads chờ JDBC | DB connection pool nhỏ, DB chậm, lock | HikariCP active/pending, query metrics |
| Nhiều virtual threads chờ HTTP | Remote API rate limit, connection pool | Response latency, 429/5xx, pool wait |
| CPU 100% khi tăng số virtual threads | CPU-bound work, GC, cryptography | CPU profiler, allocations, GC logs |
| Rất nhiều threads chờ một lock | Lock contention/serialization | Thread dumps, lock profiling |
| Ít carriers rảnh và nhiều JFR pin events | Carrier pinning đáng kể | JFR `jdk.VirtualThreadPinned` |
| Event pinning hiếm nhưng latency cao | Chưa chắc pinning, có thể I/O/DB bottleneck | End-to-end trace và dependency metrics |
| Đã đổi JDK 25 nhưng vẫn chậm | Resource bottleneck, native pinning, CPU | So sánh JFR với DB, HTTP và CPU metrics |

**Không diễn giải `Thread.State.WAITING` là dấu hiệu pinning.** Virtual thread đang WAITING có thể đã unmount và không giữ carrier. Ngược lại, virtual thread đang RUNNABLE có thể chiếm carrier để tính toán CPU — cũng không có nghĩa bị pin.

## 8. Ví dụ chạy được: cùng một Java source, so sánh JDK 21 và 24+

Chương trình JDK thuần dưới đây dùng `Thread.ofVirtual()` và một vùng `synchronized` bao quanh `Thread.sleep(300)`. Nó minh họa **cùng một đoạn mã** được thực thi trên JDK có semantics pinning khác nhau.

**File `VirtualPinningDemo.java`:**

~~~java
import java.util.concurrent.atomic.AtomicBoolean;

public final class VirtualPinningDemo {
    private static final Object MONITOR = new Object();

    public static void main(String[] args) throws InterruptedException {
        AtomicBoolean virtualInside = new AtomicBoolean(false);
        AtomicBoolean finishedInside = new AtomicBoolean(false);

        Thread worker = Thread.ofVirtual()
                .name("pinning-demo")
                .start(() -> {
                    synchronized (MONITOR) {
                        virtualInside.set(
                                Thread.currentThread().isVirtual());
                        try {
                            Thread.sleep(300);
                            finishedInside.set(true);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });

        worker.join();

        System.out.println("executed on virtual thread: " +
                virtualInside.get());
        System.out.println("sleep finished: " +
                finishedInside.get());
        System.out.println("virtual thread terminated: " +
                !worker.isAlive());
    }
}
~~~

**Biên dịch và chạy bằng JDK 21+**:

~~~bash
javac VirtualPinningDemo.java
java VirtualPinningDemo
~~~

**Ba dòng stdout của chương trình, với điều kiện không có interrupt từ bên ngoài:**

~~~text
executed on virtual thread: true
sleep finished: true
virtual thread terminated: true
~~~

Điều quan trọng: **ba dòng trên giống nhau ở JDK 21 và JDK 24+**. Chúng chứng minh logical behavior của code, **không chứng minh rằng carrier đã pin hoặc unmount**. Muốn thấy khác biệt phải quan sát JVM/JFR.

### 8.1. Chạy với JDK Flight Recorder

Bắt đầu JFR khi chạy chương trình:

~~~bash
java -XX:StartFlightRecording=filename=pinning.jfr,dumponexit=true VirtualPinningDemo
jfr print --events jdk.VirtualThreadPinned pinning.jfr
~~~

Lệnh này yêu cầu JDK có JFR. Có thể thêm JVM log khi JFR bắt đầu. Trên **JDK 21–23**, `synchronized` + `Thread.sleep(300)` là một đường ứng viên rõ cho sự kiện `jdk.VirtualThreadPinned` nếu event/threshold đang bật và runtime thực hiện chờ đủ dài.

Trên **JDK 24+**, không nên kỳ vọng một event pinning **do Java monitor** từ chính ví dụ trên, vì JEP 491 đã thay đổi cơ chế này. Tuy nhiên nếu JVM/agent/native path khác tạo lý do pinning, vẫn có thể xuất hiện event; việc không xuất hiện event không chứng minh "không có nghẽn" trong mọi hoàn cảnh.

**JFR mặc định** có ngưỡng pinned event khoảng 20 ms trong các JDK được tài liệu Oracle mô tả. Nếu demo chỉ chờ vài millisecond thì có thể không có event; ví dụ cố tình dùng 300 ms để tăng khả năng quan sát trong JDK 21–23.

### 8.2. Thử diagnostic flag cũ ở JDK 21–23

Oracle JDK 21 hướng dẫn:

~~~bash
java -Djdk.tracePinnedThreads=full VirtualPinningDemo
~~~

Đối số `full` in stack trace cho những blocking-while-pinned frames được cơ chế cũ phát hiện. Có thể dùng `short` để ít chi tiết hơn. **Không áp dụng cách này cho JDK 24+**: JEP 491 loại bỏ cơ chế trace pinning cũ; hãy sử dụng JFR thay thế. Đừng chẩn đoán "không pin" chỉ vì đặt flag cũ ở JDK 25 mà không thấy log.

### 8.3. So sánh có kiểm soát

1. Chạy cùng source, cùng số lượng jobs/workload trên **JDK 21** với JFR.
2. Chạy trên **JDK 24/25** với JFR.
3. So sánh số event, duration, stack/pinned reason, actual throughput và tail latency.
4. Cố định những biến quan trọng: CPU limit, container memory, database pool, OS, load generator, traffic profile và dependency versions.
5. Không kết luận dựa vào thời gian một lần `sleep(300)` hoặc một điểm benchmark đơn lẻ.

Nếu kiểm thử trên JDK 21 có nhiều monitor-related pinning events nhưng trên JDK 25 không còn, đó là bằng chứng có giá trị **cho workload đó**. Nếu throughput vẫn thấp sau upgrade, hãy điều tra downstream resources và CPU thay vì tiếp tục thay synchronized vô cớ.

## 9. JFR: công cụ chính để xác định pinning đang ảnh hưởng thực tế

**JDK Flight Recorder (JFR)** là lựa chọn tốt nhất khi cần kiểm tra virtual-thread pinning trong production-like workload.

### 9.1. Những sự kiện cần biết

- `jdk.VirtualThreadPinned`: ghi nhận một virtual thread bị pin trong blocking operation vượt threshold cấu hình. JDK mới cung cấp thêm thông tin như `blockingOperation`, `pinnedReason`, `carrierThread` và stack trace.
- `jdk.VirtualThreadStart` và `jdk.VirtualThreadEnd`: sự kiện lifecycle, thường disabled mặc định để giảm overhead nếu có rất nhiều virtual threads.
- `jdk.VirtualThreadSubmitFailed`: lỗi khi JVM không thể submit/unpark virtual thread, là tín hiệu khác với application task rejection.
- Các events như `jdk.SocketRead`, `jdk.ThreadSleep`, CPU/lock events: giúp phân biệt I/O latency hoặc contention với pinning.

**Không xem việc thiếu `jdk.VirtualThreadPinned` là bằng chứng chứng minh mọi thứ là nonblocking.** Có thể event threshold cao, workload không trúng path, event disabled, JFR chưa thu đủ thời gian hoặc bottleneck không phải pinning.

### 9.2. Ghi JFR lúc process đang chạy

Ví dụ nếu có PID phù hợp:

~~~bash
jcmd 12345 JFR.start name=pin-check settings=profile duration=60s filename=pin-check.jfr
jfr print --events jdk.VirtualThreadPinned pin-check.jfr
~~~

Đây là **lệnh minh họa**, thay `12345` bằng PID Java process thật; đợi recording hoàn tất hoặc dùng một lệnh dump tương ứng trước khi đọc nếu chưa có file. Cấu hình `settings=profile` có overhead nhất định, cần kiểm tra policy quan sát production.

Để in sự kiện và stack chi tiết:

~~~bash
jfr print --events jdk.VirtualThreadPinned --stack-depth 30 pin-check.jfr
~~~

Nếu phiên bản `jfr` tool không hỗ trợ option theo dạng này, xem `jfr help print` của đúng JDK đang sử dụng và điều chỉnh. Không dựa vào output format của một bản JDK để parse JFR của mọi phiên bản.

### 9.3. Diễn giải stack theo JDK version

**JDK 21–23:** stack có thể cho thấy method giữ monitor và chờ tại `Thread.sleep`, socket read, lock wait hoặc library code. Nếu có vùng `synchronized` dài chứa I/O, xem đây là ứng viên giảm pinning.

**JDK 24+:** inspect `pinnedReason`, `blockingOperation` và native/VM frames. `synchronized` nằm trong stack **không đủ** để nói monitor là thủ phạm; có thể chỉ là bối cảnh một native-related pinned wait. Nhìn root cause theo event của **runtime thực**.

**Không tạo kết luận từ một event riêng lẻ:** kiểm tra duration histogram, tần suất, số carriers, throughput, OS scheduling và workload. Các events dài nhưng hiếm có thể không đáng sửa bằng những thay đổi risky; events thường xuyên và kéo dài trong code hot path mới cần ưu tiên.

## 10. jcmd và thread dumps: bổ trợ chẩn đoán JFR

JDK hỗ trợ dump virtual threads bằng:

~~~bash
jcmd 12345 Thread.dump_to_file -format=text virtual-threads.txt
jcmd 12345 Thread.dump_to_file -format=json virtual-threads.json
~~~

Thay `12345` bằng PID thật. Thread dump có thể cho thấy virtual threads đang chờ tại đâu, stack nào đang chạy, các locks và các tác vụ có số lượng lớn. Nhưng thread dump là **ảnh chụp trạng thái**, không tự chứng minh carrier đã bị giữ suốt khoảng chờ. Kết hợp JFR events và performance metrics để phân biệt đúng.

Ở một số JDK mới có thêm `jdk.management.VirtualThreadSchedulerMXBean` với thông tin scheduler target parallelism và số threads queued. Công cụ JMX có thể hỗ trợ chẩn đoán capacity; API/số liệu cụ thể thay đổi theo phiên bản, nên kiểm tra Javadocs trước khi viết monitoring adapter.

### 10.1. Phân biệt triệu chứng trước khi sửa code

~~~text
Nhiều virtual threads chờ I/O
          |
          +-- JFR pinned events nhiều, carrier bị giữ?
          |       |
          |       +-- JDK 21-23: monitor + blocking?
          |       +-- JDK 24+: native/foreign/VM reason?
          |
          +-- JFR pinning không đáng kể?
                  |
                  +-- DB pool hết connection?
                  +-- Remote API chậm/rate limit?
                  +-- lock contention?
                  +-- CPU / GC bão hòa?
~~~

Mục tiêu là tìm **nguyên nhân đầu tiên khống chế throughput**. Chuyển sang virtual threads không tự động giải phóng một resource pool mà mọi requests đều phải chờ.

## 11. Ứng dụng Spring Framework: @Async và TaskExecutor

Spring Framework 6.1 hỗ trợ virtual threads theo một số cách:

- `VirtualThreadTaskExecutor` là TaskExecutor chuyên dụng dùng virtual threads trên JDK 21+.
- `SimpleAsyncTaskExecutor` có `setVirtualThreads(true)`, tạo virtual thread riêng cho mỗi task và không dùng thread pool cố định kiểu `ThreadPoolTaskExecutor`.
- `SimpleAsyncTaskScheduler` có chế độ virtual threads; phù hợp với nhiều job được dispatch theo cron/fixed rate, nhưng **fixed delay vẫn chạy trên scheduler thread đơn** theo lưu ý javadoc của Spring.
- Một `@Async` method thực sự chạy trên virtual thread **chỉ nếu executor được cấu hình và chọn đúng**. Annotation một mình không đảm bảo `Thread.currentThread().isVirtual() == true`.

### 11.1. Ví dụ cấu hình @Async với virtual threads

~~~java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("virtualExecutor")
    public AsyncTaskExecutor virtualExecutor() {
        return new VirtualThreadTaskExecutor("vt-worker-");
    }
}

@Service
public class RemoteService {

    @Async("virtualExecutor")
    public CompletableFuture<String> fetchAsync() {
        boolean virtual = Thread.currentThread().isVirtual();
        return CompletableFuture.completedFuture(
                "ran-on-virtual=" + virtual);
    }
}
~~~

Đây là **hai class code minh họa**, cần import Spring `@Bean`, `@Configuration`, `@EnableAsync`, `@Async`, `@Service`, `AsyncTaskExecutor`, `VirtualThreadTaskExecutor` và `CompletableFuture`. Cần gọi `fetchAsync()` **qua Spring proxy từ một bean/caller khác**; `this.fetchAsync()` trong cùng bean bỏ qua async proxy mặc định.

Điều này giúp Spring dispatch vào virtual thread, nhưng **không làm biến mất nguồn pinning của JDK/runtime**. Nếu method gọi một thư viện JNI blocking dài, nó vẫn có thể giữ carrier; nếu method dùng `synchronized` với I/O trên JDK 21, có thể pin; trên JDK 25, monitor-related case này đã được JEP 491 xử lý.

### 11.2. Spring Boot property không thay mọi custom executor

Spring Boot từ nhánh hỗ trợ virtual threads (ví dụ Boot 3.2+) có cấu hình:

~~~properties
spring.threads.virtual.enabled=true
spring.main.keep-alive=true
~~~

Property đầu yêu cầu Boot auto-configure executor/scheduler theo virtual threads **khi điều kiện auto-configuration phù hợp** và JDK 21+. Nếu ứng dụng khai báo custom `Executor`/`TaskScheduler`, không thể mặc định rằng mọi job đã dùng virtual threads. Hãy kiểm tra runtime bằng `Thread.currentThread().isVirtual()` trong đường công việc đúng.

Property `spring.main.keep-alive=true` có giá trị khi app phụ thuộc các tác vụ nền virtual threads mà không có non-daemon thread giữ JVM sống. **Virtual threads là daemon threads**, nên riêng chúng không ngăn JVM kết thúc khi chỉ còn daemon threads.

Không xem Boot property là một "fix pinning" cho JDK 21. Nó chỉ thay **cách chọn threads**; vấn đề pinned carrier cần đo và xử lý ở đúng JDK/library.

## 12. JDBC, @Transactional và các thư viện Spring khi dùng virtual threads

Một request chạy trên virtual thread có thể gọi Spring `@Transactional`, JPA repository hoặc HTTP client theo style đồng bộ. Các API đó vẫn có thread-bound semantics của giao dịch imperative, nhưng `@Transactional` **không tự truyền transaction sang một virtual thread con** do bạn tạo riêng.

~~~java
@Transactional
public void processOrder(Long id) {
    orderRepository.markProcessing(id);
    // Nếu tự tạo một virtual thread khác,
    // transaction của caller không tự theo thread con.
}
~~~

Đây là mảnh code minh họa, cần Spring bean và repository. **Pinning** và **transaction propagation** là hai vấn đề độc lập. Một method hoàn toàn không pin carrier vẫn có thể sai nếu chia sẻ EntityManager/Session thực qua nhiều threads.

### 12.1. Native pinning hay connection pool nghẽn?

Ví dụ HikariCP chỉ có 10 connections, nhưng 2.000 virtual threads cùng thực hiện JDBC. Có thể rất nhiều virtual threads **chờ acquire connection**. Số lượng threads WAITING tăng mạnh, latency cao, nhưng nguyên nhân gốc có thể là **DB pool**. Không suy ra có pinning chỉ từ queue wait.

Nếu JFR có nhiều `VirtualThreadPinned` events trong stack JDBC/library native, xem **runtime version** và library version; một số monitor-related pinning trong driver cũ ở JDK 21 sẽ giảm khi JDK 24+ xử lý monitors. Nhưng một driver Java thuần, connection pool hoặc DB lock vẫn có thể là bottleneck dù không pin.

### 12.2. Tại sao ThreadLocal vẫn cần chú ý?

Virtual threads hỗ trợ `ThreadLocal`, nhưng một virtual thread thường có lifecycle ngắn theo task. Một cache gắn với `ThreadLocal` có thể tạo rất nhiều objects khi số virtual threads lớn; đó là **allocation/memory issue**, không phải carrier pinning. MDC/security/request context phải được truyền đúng cách khi chuyển thread, và cleanup sau mỗi task vẫn cần được đảm bảo.

Không dùng ThreadLocal để "chia sẻ" transaction/JPA EntityManager giữa virtual threads. Spring imperative transaction resources gắn với thread/unit of work, không phải cơ chế handoff an toàn cho stateful ORM context.

## 13. Phân biệt pinning với monitor contention và CPU saturation

Hai đoạn code có thể đều chậm nhưng lý do khác nhau.

**Trường hợp A: tất cả threads tranh một monitor**:

~~~java
synchronized (sharedLock) {
    updateInMemoryState();
}
~~~

Ở JDK 24+, việc monitor enter/wait thông thường không còn là nguồn carrier pinning như ở JDK 21–23, nhưng **chỉ một thread giữ lock mỗi lần**. Nếu critical section quá dài, throughput vẫn thấp do serialization.

**Trường hợp B: CPU-bound computation**:

~~~java
long checksum = 0;
for (long i = 0; i < 100_000_000L; i++) {
    checksum += i;
}
~~~

Mảnh code minh họa này sử dụng CPU thật khi chạy; virtual threads không làm một vòng lặp CPU thuần có khả năng song song vượt quá cores. Cần CPU profiling và bounded parallelism; đừng gắn nhãn "pinned" khi không có event blocking-while-pinned.

**Trường hợp C: remote I/O với timeout dài**:

~~~java
Response response = client.callWithTimeout();
~~~

Đây là mảnh minh họa, phụ thuộc client. Trên JDK có virtual-thread-friendly blocking I/O, thread có thể unmount khi chờ; nếu remote API mất 30 giây, latency vẫn 30 giây dù không pin. Giải pháp là timeout, retry budget, circuit breaker, rate limits và tối ưu remote service, không phải thay lock.

## 14. Những lựa chọn khắc phục theo phiên bản

### 14.1. Với JDK 21–23

Khi JFR cho thấy pinned waits do monitor:

1. Tìm đúng các đoạn `synchronized` **đang giữ monitor trong lúc blocking lâu**.
2. Tối ưu loại bỏ I/O dài khỏi lock nếu giữ nguyên correctness.
3. Nếu cần lock xuyên I/O, cân nhắc `ReentrantLock` với `try/finally` và kiểm thử concurrency.
4. Kiểm tra dependencies: HTTP clients, JDBC drivers, logging, security libraries, serialization có synchronized hot paths không.
5. Đo lại JFR/throughput dưới tải tương đương; tránh sửa mọi `synchronized` trên codebase.
6. Đánh giá **nâng JDK 24+** thay vì duy trì workaround lớn chỉ vì monitor-related pinning.

**Không thay khóa theo kiểu tìm-và-thay:** `synchronized` và `ReentrantLock` khác cách sử dụng, monitor wait/notify, điều kiện chờ và interruptible acquisition. Phải review semantics khi refactor.

### 14.2. Với JDK 24+

JEP 491 đã giải quyết phần lớn monitor-related pinning, vì vậy ưu tiên:

1. JFR `jdk.VirtualThreadPinned` để phát hiện các **native/foreign/VM paths còn lại**.
2. Kiểm tra native/FFM operations có tạo blocking callbacks hoặc classloading waits.
3. Nếu CPU cao, đo CPU/GC chứ không chỉ tìm pinning.
4. Nếu I/O latency cao, xem connection pools, rate limits, thread counts, downstream metrics.
5. Không thay synchronized chỉ vì một tài liệu viết cho JDK 21.
6. Nếu dùng native dependencies không thể đổi, cô lập và giới hạn concurrency hoặc sử dụng architecture phù hợp; không có một API magic để "unpin" mọi native wait.

### 14.3. Nâng JDK có phải luôn là cách chữa?

Nâng từ JDK 21 lên 24/25 loại bỏ một nguồn pinning thường gặp, nhưng:

- Có thể vẫn còn native/FFM cases.
- Một monitor vẫn serialize truy cập shared state.
- DB/HTTP connection pools vẫn có giới hạn.
- Library/framework compatibility và chính sách support phải được kiểm thử.
- JFR không phát event vì monitor nữa không có nghĩa request latency đã giảm.
- CPU saturation do nhiều tasks parallel vẫn cần admission control.

Vì vậy, nâng JDK là một **biện pháp kỹ thuật mạnh** nhưng không phải thay cho load test và profiling.

## 15. Chẩn đoán production: quy trình từng bước

### Bước 1 — Ghi lại runtime thật

~~~bash
java -version
jcmd 12345 VM.version
~~~

Thay PID bằng tiến trình thật. Kiểm tra **runtime mà Spring process đang chạy**, không chỉ JDK đang cài trên máy build. Container base image, JAVA_HOME của service hoặc CI khác nhau có thể dẫn tới behavior khác nhau.

### Bước 2 — Xác minh đường chạy dùng virtual threads

Trong code quan sát:

~~~java
boolean virtual = Thread.currentThread().isVirtual();
~~~

Có thể log giá trị ở đúng service method dùng `@Async`/HTTP request và thread name/tracing ID. Đừng suy từ một bean config trong Spring rằng mọi paths đều virtual threads.

### Bước 3 — Thu JFR dưới tải giống production

~~~bash
jcmd 12345 JFR.start name=vt-check settings=profile duration=60s filename=vt-check.jfr
jfr print --events jdk.VirtualThreadPinned vt-check.jfr
~~~

Các lệnh minh họa yêu cầu PID/permissions phù hợp; sau khi recording hoàn tất mới đọc file. Nếu muốn đặt ngưỡng event khác mặc định, dùng cấu hình JFR tùy chỉnh và ghi rõ threshold trong kết quả để so sánh công bằng.

### Bước 4 — Phân loại event dựa trên JDK

- JDK 21–23: tìm blocking trong `synchronized` hoặc native/foreign frames.
- JDK 24+: ưu tiên `pinnedReason`/`blockingOperation` và native/VM reasons; không kết luận monitor pinning từ việc stack có synchronized.
- Tính frequency × duration; đừng chỉ đếm events.
- Xem khoảng thời gian có throughput tụt, carrier busy, queued virtual threads và P99 latency.

### Bước 5 — Đối chiếu các bottlenecks không liên quan pinning

- DB connection pool active/idle/pending.
- API timeouts, rate limits, connection reuse.
- CPU utilization, GC pauses, allocation rate.
- Lock contention và thời gian giữ monitor.
- Job queue, backpressure, retry storms.
- Spring transaction duration, blocking remote calls.

### Bước 6 — Thay đổi nhỏ và đo lại

Nếu là monitor pinning trên 21–23, cân nhắc nâng JDK hoặc thay một critical section có bằng chứng. Nếu là native/foreign path, thay dependency hoặc cô lập blocking. Nếu là DB bottleneck, giới hạn concurrent JDBC requests và tối ưu queries/pool. Rollout có canary và load test, không suy từ một biểu đồ CPU ngắn.

## 16. Sai lầm thường gặp

| Nhận định sai | Điều đúng |
| --- | --- |
| "Mọi synchronized đều pin virtual thread." | Không. JDK 21–23 có risk khi **blocking dưới monitor**; JDK 24+ đã thay đổi theo JEP 491 |
| "JDK 25 không còn pinning." | Native/foreign và VM cases vẫn cần kiểm tra |
| "Thread.sleep luôn pin carrier." | Thường có thể unmount; pinning phụ thuộc trạng thái/frame và JDK |
| "CPU chạy lâu là pinning." | CPU-bound work đang sử dụng carrier để tính toán, không phải blocking-while-pinned |
| "Thread.State.WAITING chứng minh pinned." | WAITING không nói carrier có bị giữ hay đã unmount |
| "Đổi toàn bộ synchronized sang ReentrantLock giúp JDK 25 nhanh hơn." | JEP 491 xử lý monitor pinning; thay phải có lý do và dữ liệu |
| "Chỉ dùng -Djdk.tracePinnedThreads trên mọi JDK." | Tùy JDK: legacy property ở 21–23; JDK 24+ dùng JFR |
| "Không thấy JFR pinned event tức là code tối ưu." | Có thể mắc nghẽn DB, HTTP, CPU hoặc threshold JFR |
| "Virtual threads làm JDBC pool vô hạn." | Pool, DB và network vẫn có giới hạn |
| "spring.threads.virtual.enabled=true luôn chuyển mọi executor custom." | Auto-config chỉ có tác dụng khi điều kiện phù hợp |
| "Virtual threads làm @Transactional tự lan sang thread con." | Transaction imperative gắn theo thread, không truyền tự động |
| "Pinning là exception khiến method sai." | Chủ yếu là scalability/performance issue |
| "Một event dài chứng minh phải sửa code ngay." | Cần tần suất, duration và tương quan throughput |
| "JDK 24+ không còn cần monitor/lock profiling." | Contention/serialization vẫn tồn tại dù không pin |
| "Thêm hàng triệu virtual threads giúp CPU-heavy jobs nhanh hơn." | CPU vẫn bị giới hạn bởi cores, memory và downstream |

## 17. Checklist review ứng dụng Spring

- [ ] Xác nhận chính xác JDK runtime của ứng dụng, đặc biệt ranh giới **21–23** và **24+**.
- [ ] Xác nhận các paths cần đo thực sự chạy trên virtual threads.
- [ ] Hiểu carrier mount/unmount và pinning; không nhầm với CPU saturation.
- [ ] Với JDK 21–23, tìm `synchronized` bao quanh chờ I/O dài ở hot path.
- [ ] Với JDK 24+, không báo lỗi pinning chỉ vì có synchronized.
- [ ] Rà soát native/JNI/FFM libraries và class initialization khi JFR nêu reason.
- [ ] Không tự suy native call luôn pin nếu không có blocking/stack evidence.
- [ ] Thu `jdk.VirtualThreadPinned` events theo workload thật và ghi threshold.
- [ ] Dùng `jcmd Thread.dump_to_file` bổ trợ nhưng không thay thế JFR.
- [ ] Đối chiếu duration/frequency với P99 latency, throughput và carrier availability.
- [ ] Kiểm tra DB pool, remote HTTP pool, locks, CPU và GC trước khi sửa code.
- [ ] Với Spring, biết `VirtualThreadTaskExecutor`, `SimpleAsyncTaskExecutor` và `@Async` chọn executor thế nào.
- [ ] Với Spring Boot, kiểm tra auto-config và custom executors khi bật `spring.threads.virtual.enabled`.
- [ ] Nhớ virtual threads là daemon; bảo đảm JVM keep-alive nếu app chỉ còn background virtual jobs.
- [ ] Kiểm tra `@Transactional`/ORM/ThreadLocal boundary nếu tạo thread mới.
- [ ] Không share EntityManager/Session thực giữa threads chỉ vì threads là virtual.
- [ ] Không dùng nhiều virtual threads để thay thế semaphore/backpressure cho downstream.
- [ ] Nếu refactor locks, bảo đảm mutual exclusion và try/finally cleanup.
- [ ] Đo lại sau thay JDK/dependency/code bằng cùng benchmark profile.

## 18. Câu hỏi tự kiểm tra

1. **Carrier thread là gì?** Platform thread tạm thực thi virtual thread đang mount.
2. **Unmount có lợi gì?** Virtual thread chờ mà carrier được trả để chạy công việc khác.
3. **Khi nào gọi là pinning?** Khi virtual thread cần chờ nhưng không thể unmount nên giữ carrier.
4. **CPU-bound code chiếm carrier có phải pinning?** Không tự động; nó đang thực thi CPU.
5. **JDK 21–23 dễ pin trong tình huống nào?** Blocking dưới `synchronized`/monitor, hoặc native/foreign frames.
6. **Điểm khác của JDK 24 là gì?** JEP 491 loại bỏ pinning thông thường do Java intrinsic monitors.
7. **JDK 25 có cần quan sát pinning không?** Có; native/foreign và VM cases còn có thể pin.
8. **Tại sao `synchronized` trên JDK 25 vẫn có thể gây chậm?** Mutual exclusion và lock contention vẫn serialize callers.
9. **Vì sao không thay hết synchronized?** Dễ làm sai synchronization và JDK 24+ đã xử lý monitor-related pinning.
10. **Dùng `ReentrantLock` khi nào?** Khi semantics thích hợp hoặc cần mitigation cho hot blocking monitor trên JDK 21–23.
11. **Một virtual thread chờ DB connection có nhất thiết pin carrier?** Không; pool exhaustion là bottleneck khác.
12. **Event nào của JFR dùng phát hiện pinned wait?** `jdk.VirtualThreadPinned`.
13. **Ngưỡng mặc định thường được Oracle mô tả là bao nhiêu?** Khoảng 20 ms cho pinned event.
14. **Legacy trace property trên JDK 21 dùng thế nào?** `-Djdk.tracePinnedThreads=full` hoặc `short`.
15. **JDK 24+ dùng gì thay legacy property?** JFR pinned event, thread dumps và profiling.
16. **Demo synchronized + sleep chứng minh pin bằng stdout không?** Không; phải xem JFR theo đúng JDK.
17. **`Thread.currentThread().isVirtual()` chứng minh gì?** Thread đang chạy là virtual; không nói nó pinned.
18. **Spring `@Async` tự tạo virtual threads khi chưa cấu hình executor?** Không nên giả định; kiểm tra executor runtime.
19. **Spring Boot property bật virtual threads có thay mọi custom executor không?** Không có bảo đảm tổng quát.
20. **Virtual threads có truyền `@Transactional` sang thread con không?** Không, transaction imperative vẫn thread-bound.
21. **JDK 24 bỏ monitor pinning có sửa DB pool exhaustion không?** Không.
22. **Một native function dài chắc chắn tạo JFR pinned event không?** Không; phải phân biệt CPU work và blocking-while-pinned cùng threshold.
23. **Vì sao phải kiểm tra đúng JDK đang chạy production?** Một build target 21 không đồng nghĩa runtime pinning semantics ở JDK 21.
24. **Thông điệp chính?** Để nhận diện carrier pinning phải xác định **blocking point, runtime JDK, JFR event và bottleneck thực**; không đưa lời khuyên cho JDK 21 áp dụng máy móc lên JDK 24+.

**Kết luận:** Virtual threads mở rộng concurrency nhờ khả năng unmount khỏi carrier khi chờ. **JDK 21–23** có rủi ro monitor-related pinning khi chặn trong `synchronized`; **JDK 24+** giải quyết trường hợp phổ biến ấy qua **JEP 491**, nhưng không loại bỏ mọi native/foreign/VM pinning. Dùng JFR để xác định pinned wait thật, phân biệt nó với CPU, lock contention và downstream exhaustion, rồi mới quyết định nâng JDK, sửa code hoặc điều chỉnh resource limits trong Spring.

### Tài liệu chính thức

- [JEP 444 — Virtual Threads (finalized JDK 21)](https://openjdk.org/jeps/444)
- [JEP 491 — Synchronize Virtual Threads without Pinning (JDK 24)](https://openjdk.org/jeps/491)
- [Oracle JDK 21 — Virtual Threads Guide](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
- [Oracle JDK 24 — Significant Changes](https://docs.oracle.com/en/java/javase/24/migrate/significant-changes-jdk-24.html)
- [Oracle JDK 25 — Virtual Threads Guide](https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html)
- [Oracle JDK 26 — Virtual Threads Guide](https://docs.oracle.com/en/java/javase/26/core/virtual-threads.html)
- [Oracle JDK 27 — Virtual Threads Guide](https://docs.oracle.com/en/java/javase/27/core/virtual-threads.html)
- [Spring Framework 6.1 — VirtualThreadTaskExecutor](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/task/VirtualThreadTaskExecutor.html)
- [Spring Framework 6.1 — SimpleAsyncTaskExecutor](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/task/SimpleAsyncTaskExecutor.html)
- [Spring Framework 6.1 — SimpleAsyncTaskScheduler](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/concurrent/SimpleAsyncTaskScheduler.html)
- [Spring Boot — Virtual Threads](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.virtual-threads)
- [Java 21 API — Thread](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-009">Đo tải thực tế trước khi thay cách thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Đo tải thực tế trước khi thay cách thực thi

## 1. Vấn đề: luồng ảo không tự động làm ứng dụng nhanh hơn

Từ **Java 21**, virtual threads (luồng ảo, JEP 444) là một tính năng chính thức của JDK. Chúng cho phép tổ chức nhiều tác vụ đồng thời theo phong cách **một thread cho mỗi task/request**, đặc biệt khi các tác vụ **chờ I/O đồng bộ** trong phần lớn thời gian. Khi virtual thread chờ ở những điểm blocking được JVM hỗ trợ, nó thường có thể **unmount** khỏi platform thread dùng làm carrier, nhờ đó carrier phục vụ virtual thread khác.

Lợi ích tiềm năng rất lớn nhưng không phải phép màu:

- **Có thể nâng throughput hoặc số request đồng thời** khi ứng dụng trước đây bị giới hạn bởi số platform worker đang chờ I/O.
- **Không làm một phép tính CPU chạy nhanh hơn**; tác vụ CPU-bound vẫn phải cạnh tranh cho số core/quota CPU thực.
- **Không làm database có thêm connection**, API bên ngoài tăng rate limit, hoặc network nhanh hơn.
- **Không bảo đảm giảm latency từng request**. Thậm chí có thể tăng p95/p99 nếu thả quá nhiều request cùng tranh chấp database pool, bộ nhớ hoặc downstream.
- **Không tự giải quyết cancellation, timeout, idempotency, context propagation và transaction**.
- **Không biến mọi Executor trong Spring thành virtual executor** chỉ vì bật một cấu hình; còn phụ thuộc Spring Boot version, auto-configuration và các executor do ứng dụng tự tạo.

Bởi vậy, **“Đo tải thực tế trước khi thay cách thực thi”** là nguyên tắc quan trọng: phải xác định bottleneck, thu thập số liệu baseline có thể lặp lại, thử một phương án virtual threads có kiểm soát, rồi so sánh dưới **cùng workload và cùng điều kiện tài nguyên**. Không ra quyết định chỉ từ việc một chương trình tạo được hàng chục nghìn virtual threads hoặc một demo `Thread.sleep` chạy nhanh hơn.

Bài tập trung vào cách chọn workload đại diện, metric, thiết kế A/B, ví dụ Java 21+ đo một workload tổng hợp, diễn giải kết quả với giới hạn connection pool, các thay đổi cấu hình Spring, pinning theo JDK version, JFR, cách triển khai canary và điều kiện rollback.

## 2. Phân biệt platform thread, virtual thread và carrier

| Đối tượng | Ai quản lý? | Đặc tính liên quan tới đo tải |
| --- | --- | --- |
| Platform thread | JVM ánh xạ tới OS thread | Đắt hơn về stack và OS resource; pool thường có số thread cố định/hữu hạn |
| Virtual thread | JVM lập lịch lên platform carrier threads | Nhẹ và tạo theo task; nhiều thread có thể đang chờ I/O mà không giữ carrier |
| Carrier thread | Platform thread phục vụ một virtual thread khi nó chạy | Carrier vẫn bị giới hạn bởi CPU và một số tình huống blocking/pinning |
| Task/request | Đơn vị công việc nghiệp vụ | Kết quả được đo bằng response, side effect, latency, error, không chỉ số thread |
| Downstream connection | Tài nguyên DB/HTTP client/broker | Dù virtual threads rất nhiều, connection/rate limit vẫn hữu hạn |

Virtual thread **không đồng nghĩa “một CPU core mới”**. Mỗi khi virtual thread thực sự chạy bytecode trên CPU, nó vẫn cần một carrier và CPU time. Điểm khác là nhiều tác vụ chờ có thể không chiếm carrier cùng lúc.

Điều này giải thích vì sao workload **blocking I/O** có thể có lợi: khi platform pool chỉ có 40 worker và 40 request đang chờ remote API, request thứ 41 phải queue; virtual threads có thể cho nhiều request tiến vào vùng I/O chờ, **nếu** remote API và connection pools chịu được. Nhưng khi database chỉ cho 20 connection, việc tạo 10.000 virtual threads chỉ biến bottleneck thành hàng nghìn tác vụ chờ connection.

Trong Java, cách cơ bản dùng virtual threads là:

```java
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    Future<String> result = executor.submit(() ->
            Thread.currentThread().isVirtual() ? "virtual" : "platform");
    System.out.println(result.get());
}
```

Đây là **mảnh Java 21+**, cần imports `ExecutorService`, `Executors`, `Future`. Executor tạo một virtual thread mới cho mỗi task được nhận; nó **không phải virtual-thread pool tái sử dụng các virtual threads**. `Thread.isVirtual()` dùng để xác nhận loại thread đang chạy task, không phải đo hiệu năng.

## 3. Trước khi chọn công nghệ, hãy xác định nút thắt hiện tại

Một quyết định chuyển execution model chỉ có cơ sở khi biết **hệ thống đang chậm do đâu**:

1. **CPU-bound:** phần lớn thời gian là mã hóa, tính toán, nén, parsing, transformation. Tăng số runnable threads không tạo thêm core; context switching, allocation hoặc GC có thể tăng.
2. **Blocking I/O-bound:** phần lớn thời gian request đợi socket, DB driver, file I/O hoặc remote response. Nếu platform worker pool cạn trong khi CPU còn rảnh, virtual threads đáng thử.
3. **Downstream-bound:** DB connection pool, transaction locks, API quota, message broker hoặc rate limiter đã bão hòa. Virtual threads có thể khiến số request **chờ tài nguyên** tăng mà throughput không tăng.
4. **Queue-bound:** task ở queue của `ThreadPoolTaskExecutor` lâu trước khi bắt đầu. Cần phân biệt queue do thiếu platform workers với queue do backpressure có chủ ý.
5. **Memory/GC-bound:** nhiều object, buffer, request context hoặc `ThreadLocal` giữ heap và tăng pause. Virtual threads không làm dữ liệu nghiệp vụ biến mất.
6. **Lock contention:** synchronized block, `ReentrantLock`, database locks hoặc các critical sections khiến nhiều task đợi. Vấn đề cần xem scope lock và fairness, không chỉ thread type.
7. **Reactive/event-loop-bound:** ứng dụng đã nonblocking qua Reactor/R2DBC/Netty. Chuyển toàn bộ thành thread-per-request không tự có lợi nếu bottleneck đã nằm ở network hoặc DB.

**Bằng chứng cần thấy ở baseline:** CPU còn headroom nhưng platform thread pool active gần max và queue wait/p99 tăng **trong lúc request đợi I/O**. Nếu CPU 100% và run queue cao, không nên kỳ vọng virtual threads tăng năng lực CPU.

Một biểu đồ “thread count giảm hoặc tăng” không thay thế được metric về **completed requests per second với mức lỗi và latency được chấp nhận**.

## 4. Mục tiêu đo: định nghĩa thành công trước khi chạy test

Đừng bắt đầu từ mục tiêu “virtual threads phải nhanh hơn”. Hãy đặt tiêu chí theo SLO/SLA thật, ví dụ sau chỉ là **mẫu tiêu chí**, không phải kết quả đo:

| Tiêu chí | Cách đo | Điều kiện quyết định gợi ý |
| --- | --- | --- |
| Throughput | **Completed successful requests/s**, không phải submitted/s | Tăng đáng kể tại cùng error/latency budget |
| End-to-end latency | p50, p95, p99 từ lúc request đến hệ thống tới lúc response | Không làm xấu p99 vượt SLO |
| Error/timeout | 5xx, timeout, rejection, downstream 429/503 | Không tăng vượt error budget |
| CPU | CPU process và cgroup/container quota | Không đạt CPU saturation sớm hơn |
| Memory | Heap used/live, RSS, allocation rate, GC pauses | Không tăng OOM/GC pressure không chấp nhận |
| Pool/queue | Executor active/queued, DB connections pending, HTTP pool wait | Nút thắt được giải quyết hay chỉ chuyển vị trí? |
| Stability | Soak test, burst, restart/shutdown, cancellation | Không xuất hiện treo, leak hoặc tác vụ mất bất thường |
| Operational cost | CPU-seconds/request, memory/request, số replicas cần giữ SLO | Tổng chi phí/số request không xấu đi |

**Ví dụ tiêu chí trước thử nghiệm:** “Giữ p99 dưới 500 ms và error rate dưới 0,5% ở 300 successful RPS; giảm ít nhất 15% số replicas hoặc tăng throughput 20% ở cùng tài nguyên.” Đây là **ngưỡng giả định để minh họa cách viết quyết định**, phải thay bằng SLO và chi phí thật của hệ thống; không được tự coi là chuẩn Spring hay Java.

Chốt **đơn vị** của từng metric: request/s, milliseconds, MiB, CPU cores, số connections, số in-flight tasks. Không so nhầm p95 của một method nội bộ với p95 end-to-end HTTP.

## 5. Các điểm thời gian nên đo riêng

Một request có thể đi qua nhiều ranh giới xếp hàng:

```text
t0: client phát request
    |
    +-- load balancer/network
    |
t1: request tới server
    |
    +-- chờ servlet/executor worker
    |
t2: bắt đầu xử lý ứng dụng
    |
    +-- chờ semaphore hoặc lấy DB connection
    |
t3: vào vùng I/O thật
    |
    +-- database/API/network
    |
t4: I/O xong
    |
    +-- mapping/serialization/commit
    |
t5: response tới client
```

Metric cần phân biệt:

- **Arrival-to-start / queue wait:** thời gian chờ được worker/đi vào xử lý.
- **Resource wait:** thời gian chờ connection, semaphore, lock hoặc quota.
- **Service time:** thời gian thực hiện nghiệp vụ, bao gồm blocking I/O sau khi đã có resource.
- **End-to-end latency:** từ request đến response, bao gồm mọi lần chờ, retry và network.
- **Concurrency / in-flight:** số request đang được xử lý hoặc đang chờ; đo đúng phạm vi.
- **Completion throughput:** số việc thật sự hoàn tất trong khoảng thời gian quan sát.

Khi đổi platform pool sang virtual threads, **queue wait ở executor có thể giảm nhưng resource wait tăng**. Nếu chỉ nhìn queue của executor cũ thấy trống, rất dễ kết luận sai rằng hệ thống đã cải thiện, trong khi yêu cầu đang nằm chờ ở database connection pool.

### Công thức Little's Law

Ở trạng thái tương đối ổn định:

```text
L ≈ λ × W

L = số request trung bình trong hệ thống
λ = tốc độ hoàn tất trung bình (request/giây)
W = thời gian request trung bình ở hệ thống (giây)
```

Ví dụ **giả định** có 120 request/s và thời gian xử lý/đợi trung bình 0,4 giây, số request đồng thời trung bình khoảng **48**. Nếu chỉ có 20 platform workers và phần lớn 0,4 giây là chờ I/O, thread pool có thể tạo backlog. Nhưng nếu DB chỉ cho 10 connections và mỗi request giữ connection 0,2 giây, giới hạn DB lý thuyết khoảng **50 lượt dùng connection/s** cho hoạt động đó, chưa tính transaction locks và overhead; virtual threads không vượt qua giới hạn vật lý này.

Đây là **ước lượng năng lực để đặt giả thuyết**, không phải bằng chứng benchmark. Phải đo trực tiếp throughput, tail latency và wait time của từng resource.

## 6. Thiết kế phép thử A/B công bằng

Tối thiểu cần so sánh:

- **A1 – baseline hiện tại:** platform threads với đúng pool size/queue/rejection/config hiện đang triển khai.
- **A2 – platform threads đã tuning hợp lý:** nếu tăng platform pool từ 4 lên 8 đã đủ giải bottleneck, phải đưa phương án ít thay đổi ấy vào phép so sánh.
- **B – virtual threads:** dùng Java 21+ và virtual executor/đường request thực tế; giới hạn downstream phù hợp.
- **B2 – virtual threads có admission limit:** nếu B không giới hạn làm DB/HTTP nghẽn, thử bảo vệ tài nguyên bằng `Semaphore`, HTTP connection pool, rate limiter hoặc concurrency bulkhead.

Để giảm nhiễu, giữ ổn định giữa các nhóm:

1. **Cùng JDK version, build, GC và JVM flags** khi so riêng execution model. Không so “JDK 17 + platform” với “JDK 24 + virtual” rồi quy toàn bộ khác biệt cho virtual threads; nếu bắt buộc nâng JDK, dùng thí nghiệm ma trận tách riêng hiệu ứng JDK và execution model.
2. **Cùng Spring Boot/Framework và dependency versions**, cấu hình datasource/HTTP client, timeout, pool size, dữ liệu test và network path.
3. **Cùng máy/container CPU quota và memory limit**, cùng số replicas hoặc định nghĩa rõ mục tiêu cost-normalized.
4. **Cùng kịch bản request mix**, payload, hành vi cache, authentication, transaction, tỷ lệ lỗi giả lập và downstream latency distribution.
5. **Cùng chiến lược warm-up**, thời gian đo steady state, số lần lặp, thời điểm chạy và cách loại nhiễu môi trường.
6. **Cùng cách xác định success**, loại trừ response HTTP 200 nhưng business side effect chưa hoàn tất nếu mục tiêu thực là completion.

Đừng tự ý tăng `maxConnections` trong phương án virtual rồi giữ nguyên ở baseline; đó là một thay đổi khác có thể giải thích lợi ích. Nếu muốn kiểm tra hiệu quả điều chỉnh nhiều tham số, **thực hiện từng nhánh và lưu config đầy đủ**.

Không dùng môi trường production chịu giao dịch thật để tạo tải quá ngưỡng an toàn. Phép thử stress/fault injection nên có môi trường tách biệt, dữ liệu tổng hợp, giới hạn tải và rollback.

## 7. Open-loop và closed-loop: tránh đo thiếu tail latency

Một lỗi benchmark thường gặp là chỉ dùng vài client gửi request mới **sau khi request trước hoàn tất**. Đây là mô hình **closed-loop** với số clients cố định. Khi hệ thống chậm, nguồn phát cũng tự giảm tốc, khiến tải bị giảm đúng lúc cần đánh giá nghẽn.

Trong thử nghiệm HTTP production-like nên có thêm **open-loop / constant arrival rate**:

```text
Closed-loop:
client -> request -> wait response -> request mới
                         ^
                     server chậm => tốc độ gửi tự giảm

Open-loop:
arrival schedule -> request A, B, C, ...
                     |
               server chậm => in-flight/backlog tăng
```

Với open-loop, lưu thời điểm **đáng lẽ request được gửi** và thời điểm thực sự gửi, để biết load generator có bị nghẽn hay không. Nếu chỉ đo từ lúc client bắt đầu gửi request **sau khi đã bị chậm lịch**, kết quả có thể bỏ sót độ trễ dưới tải (**coordinated omission**). Phải kiểm tra công cụ load test có hỗ trợ arrival scheduling và histogram latency phù hợp.

**Không cần cực đoan:** closed-loop vẫn hữu ích để đo user think-time hoặc một client pool cố định. Vấn đề là phải **biết mô hình tải nào đang đại diện production**, và không thay đổi nó khi chuyển execution model.

### Workload mix đề xuất

Tái tạo những tuyến thực sự có lưu lượng và chi phí cao:

- Request đọc DB nhẹ.
- Request chờ remote HTTP gấp nhiều lần CPU time.
- Request giao dịch DB có lock và commit.
- Endpoint CPU-heavy (JSON mapping/crypto/batch).
- Request có payload lớn, streaming hoặc upload/download.
- Request với rate limit, timeout/retry, cancellation.
- Burst từ cron/webhook hoặc callback bất đồng bộ.
- Các workload có nhiều `ThreadLocal`/MDC/security context.

Những tỷ lệ này phải rút từ telemetry thực tế, không tự giả định 100% request đều chỉ `Thread.sleep`.

## 8. Ma trận tải cần chạy

| Pha | Tải và điều kiện | Quan sát bắt buộc |
| --- | --- | --- |
| Baseline idle | Không tải hoặc tải rất thấp | Startup, idle RSS, thread count, overhead |
| Warm-up | Request mix đại diện cho tới khi JIT/cache ổn | Tránh lấy warm-up làm kết quả chính |
| Steady state | Mức request/s phổ biến | p50/p95/p99, throughput, CPU, connection pool |
| Target SLO | RPS ứng với mục tiêu kinh doanh | Đạt SLO/error budget hay không |
| Stress/ramp | Tăng arrival rate đến điểm bão hòa có giới hạn | Capacity knee, queue wait, rejection, recovery |
| Burst | Tăng đột ngột rồi giảm | Có tích backlog, OOM/GC hay không |
| Downstream slow | Tăng latency DB/API có kiểm soát | Resource wait và p99 có phình không |
| Downstream cap | Thu nhỏ DB/HTTP pool hoặc semaphore | Năng lực bị giới hạn ở đâu |
| CPU-heavy | Chạy workload tốn CPU thực | Virtual không tự tạo CPU; kiểm tra overhead |
| Soak | Duy trì lâu ở tải có ý nghĩa | Heap/RSS drift, GC, leak, thread-local retention |
| Failure & shutdown | Retry, timeout, interrupt, restart | Correctness, cleanup, mất/trùng việc |
| Canary | Lưu lượng thật có giới hạn an toàn | Chênh lệch SLO và hành vi downstream |

Chỉ so một điểm “500 concurrent tasks hoàn tất” không đủ. Cần cả **đường cong latency theo throughput** và **ngưỡng bắt đầu mất SLO**.

Với tài nguyên giới hạn, báo cáo không chỉ `max RPS` mà cả số replicas để đạt RPS đó, CPU-seconds/request, memory footprint, số connection đang chờ và thời gian phục hồi sau burst.

## 9. Ví dụ Java 21+: đo queue wait, resource wait và latency

Chương trình dưới đây là **một thí nghiệm kiểm tra giả thuyết**, không phải công cụ benchmark production. Nó cố ý mô phỏng một tác vụ chờ I/O bằng `Thread.sleep(70)` và một tài nguyên downstream hữu hạn bằng `Semaphore`.

Chương trình chạy **ba execution variants**:

- `platform-4`: fixed pool 4 platform workers.
- `platform-8`: fixed pool 8 platform workers để có nhánh tuning baseline.
- `virtual`: virtual-thread-per-task executor.

Chương trình lặp với hai mức tài nguyên downstream:

- **8 permits:** có tối đa tám tác vụ đồng thời vào vùng I/O mô phỏng.
- **2 permits:** downstream rất hạn chế, dù worker pool có thể lớn.

Nó đo per-task **queue wait**, **permit wait**, **end-to-end latency** từ submit tới completion, và báo p50/p95/p99 theo nearest-rank. Đồng thời đếm **peak trong vùng sử dụng tài nguyên** và xác nhận `Thread.currentThread().isVirtual()` phù hợp với execution variant.

### Mã chạy độc lập

Lưu vào `VirtualThreadLoadProbe.java`:

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class VirtualThreadLoadProbe {

    record Sample(
            long queueNanos,
            long permitNanos,
            long latencyNanos,
            boolean virtual) {}

    record Report(
            String mode,
            int tasks,
            int peakInService,
            long batchMillis,
            long p50Millis,
            long p95Millis,
            long p99Millis,
            long avgQueueMillis,
            long avgPermitMillis,
            boolean correctThreadKind) {}

    static long percentileMillis(List<Sample> samples, double q) {
        long[] sorted = samples.stream()
                .mapToLong(Sample::latencyNanos)
                .sorted()
                .toArray();

        return TimeUnit.NANOSECONDS.toMillis(
                sorted[Math.max(0,
                        (int) Math.ceil(q * sorted.length) - 1)]);
    }

    static Report run(String mode, int tasks, int permits, int waitMs)
            throws Exception {

        boolean virtual = mode.equals("virtual");
        int workers = mode.equals("platform-8") ? 8 : 4;
        Semaphore downstream = new Semaphore(permits);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        List<Future<Sample>> futures = new ArrayList<>();

        long batchStart = System.nanoTime();

        try (ExecutorService executor = virtual
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Executors.newFixedThreadPool(workers)) {

            for (int i = 0; i < tasks; i++) {
                long submitted = System.nanoTime();

                futures.add(executor.submit(() -> {
                    long started = System.nanoTime();
                    boolean isVirtual = Thread.currentThread().isVirtual();

                    downstream.acquire();
                    long acquired = System.nanoTime();

                    int now = active.incrementAndGet();
                    peak.accumulateAndGet(now, Math::max);

                    try {
                        Thread.sleep(waitMs); // Chờ mô phỏng, không phải DB thật.
                    } finally {
                        active.decrementAndGet();
                        downstream.release();
                    }

                    long finished = System.nanoTime();
                    return new Sample(
                            started - submitted,
                            acquired - started,
                            finished - submitted,
                            isVirtual);
                }));
            }

            List<Sample> samples = new ArrayList<>();
            for (Future<Sample> future : futures) {
                samples.add(future.get(30, TimeUnit.SECONDS));
            }

            long elapsed = System.nanoTime() - batchStart;
            boolean kindCorrect = samples.stream()
                    .allMatch(sample -> sample.virtual() == virtual);

            return new Report(
                    mode,
                    samples.size(),
                    peak.get(),
                    TimeUnit.NANOSECONDS.toMillis(elapsed),
                    percentileMillis(samples, 0.50),
                    percentileMillis(samples, 0.95),
                    percentileMillis(samples, 0.99),
                    TimeUnit.NANOSECONDS.toMillis((long) samples.stream()
                            .mapToLong(Sample::queueNanos)
                            .average().orElse(0)),
                    TimeUnit.NANOSECONDS.toMillis((long) samples.stream()
                            .mapToLong(Sample::permitNanos)
                            .average().orElse(0)),
                    kindCorrect);
        }
    }

    public static void main(String[] args) throws Exception {
        for (int permits : new int[] {8, 2}) {
            System.out.println("downstream permits=" + permits);

            for (String mode : new String[] {
                    "platform-4", "platform-8", "virtual"}) {

                Report r = run(mode, 40, permits, 70);

                System.out.printf(
                    "%s: tasks=%d, kind=%s, peak=%d/%d, "
                    + "batchMs=%d, p50Ms=%d, p95Ms=%d, p99Ms=%d, "
                    + "avgQueueMs=%d, avgPermitMs=%d%n",
                    r.mode(), r.tasks(), r.correctThreadKind(),
                    r.peakInService(), permits, r.batchMillis(),
                    r.p50Millis(), r.p95Millis(), r.p99Millis(),
                    r.avgQueueMillis(), r.avgPermitMillis());
            }
        }
    }
}
```

**Yêu cầu:** Java 21 trở lên. Không cần Spring hoặc thư viện thứ ba. Những API `Executors.newVirtualThreadPerTaskExecutor()`, `Thread.isVirtual()` và `try-with-resources` cho `ExecutorService` đều khả dụng trên Java 21+.

Biên dịch/chạy:

```bash
javac VirtualThreadLoadProbe.java
java VirtualThreadLoadProbe
```

### Ví dụ kết quả quan sát được

Đã chạy logic tương đương qua JShell trên **OpenJDK 24.0.2, Windows**; một lượt đo cụ thể cho kết quả:

```text
downstream permits=8
platform-4: tasks=40, kind=true, peak=4/8, batchMs=715, p50Ms=355, p95Ms=708, p99Ms=708, avgQueueMs=320, avgPermitMs=0
platform-8: tasks=40, kind=true, peak=8/8, batchMs=355, p50Ms=213, p95Ms=355, p99Ms=355, avgQueueMs=142, avgPermitMs=0
virtual: tasks=40, kind=true, peak=8/8, batchMs=374, p50Ms=225, p95Ms=367, p99Ms=367, avgQueueMs=10, avgPermitMs=142
downstream permits=2
platform-4: tasks=40, kind=true, peak=2/2, batchMs=1414, p50Ms=707, p95Ms=1343, p99Ms=1414, avgQueueMs=605, avgPermitMs=67
platform-8: tasks=40, kind=true, peak=2/2, batchMs=1414, p50Ms=705, p95Ms=1340, p99Ms=1411, avgQueueMs=479, avgPermitMs=190
virtual: tasks=40, kind=true, peak=2/2, batchMs=1431, p50Ms=714, p95Ms=1360, p99Ms=1430, avgQueueMs=0, avgPermitMs=679
```

**Các con số thời gian là một lần đo tại môi trường thử, không phải output tất định.** CPU scheduling, JIT, tình trạng máy, bản JDK, contention và độ phân giải thời gian làm thay đổi chúng. Kết quả chính xác theo logic hơn là: có 40 task hoàn tất mỗi variant, `kind=true` xác nhận loại thread khớp, và `peak` không vượt số permits. Chương trình dùng phép mô phỏng `Thread.sleep` chứ **không phải thử tải HTTP, JDBC, TLS, serialization hoặc mạng thật**.

### Diễn giải đúng kết quả

**Khi permits = 8:**

- `platform-4` chỉ có 4 worker vào vùng I/O cùng lúc; 40 tác vụ cần nhiều đợt hơn.
- `platform-8` có thể sử dụng 8 permits; thời gian batch gần với `virtual`.
- `virtual` giảm queue wait tại executor nhưng nhiều task chờ **Semaphore**; đo `avgPermitMs` để thấy việc dời điểm chờ.
- Kết luận **không phải** “virtual nhanh gấp đôi platform”; so `platform-4` với `virtual` là so cả **mức concurrency được phép**. Nhánh `platform-8` cho thấy chỉ tuning pool cũng có thể giải phần lớn giới hạn trong bài test nhỏ.

**Khi permits = 2:**

- Cả ba mô hình cùng bị giới hạn tối đa hai task sử dụng downstream một lúc.
- Tổng thời gian batch xấp xỉ nhau vì chỉ có hai permits và mỗi task cần chờ I/O mô phỏng 70 ms.
- `virtual` có queue wait thấp, nhưng permit wait tăng rất mạnh. Nếu chỉ nhìn executor queue, ta sẽ **bỏ sót bottleneck thực**.

Bằng phép tính đơn giản, 40 tác vụ × 70 ms = 2.800 ms tổng nhu cầu vùng service; với hai permits, **ngưỡng thời gian lý tưởng không thể thấp hơn khoảng 1.400 ms** nếu service time không đổi và không overlap khác. Kết quả quan sát gần ngưỡng ấy. Với tám permits, ngưỡng lý tưởng ~350 ms. Đây là mô phỏng lý tưởng hoá, không suy ra throughput thật của hệ thống production.

### Giới hạn của thí nghiệm

- Nó dùng **burst** nộp 40 task gần như cùng lúc; không phải arrival process thực tế.
- Nó không có HTTP server, authentication, connection pool thật, database locks, GC pressure đáng kể hay retry.
- Nó so single-run với sample nhỏ; p99 trên 40 samples gần với maximum, không đủ tin cậy cho tail latency production.
- Nó có một Semaphore giả định tài nguyên đồng đều; trong thực tế tài nguyên nhiều tầng và service time có tail dài.
- Nó không chứng minh virtual threads luôn giảm chi phí; hãy dùng benchmark nhiều lượt và application load test ngoài thực tế.

**Giá trị học tập** nằm ở cách instrument ranh giới queue/permit/latency, cách so thêm tuned platform baseline, và khả năng thấy bottleneck chuyển từ executor sang downstream.

## 10. Áp dụng đúng vào Spring Boot/Spring Framework

Spring Boot **3.2+** có hỗ trợ bật virtual threads khi chạy Java 21 trở lên (tùy version và integration). Một cấu hình cơ bản cho ứng dụng phù hợp:

```properties
spring.threads.virtual.enabled=true
spring.main.keep-alive=true
```

Đây là cấu hình trong `application.properties`; cần xác nhận **phiên bản Spring Boot và JDK thực sự đang deploy**. `spring.main.keep-alive=true` hữu ích với ứng dụng chỉ dựa vào công việc nền/`@Scheduled` vì virtual threads là **daemon threads** và không tự giữ JVM sống khi không còn non-daemon threads.

Trong Spring Boot 3.4.x, khi **không có custom `Executor` bean thay thế lựa chọn auto-config**, `spring.threads.virtual.enabled=true` khiến Boot auto-configure một `SimpleAsyncTaskExecutor` dùng virtual threads thay vì `ThreadPoolTaskExecutor` mặc định; scheduler auto-config cũng có thể trở thành `SimpleAsyncTaskScheduler`. Những property kiểu `spring.task.execution.pool.max-size`/`queue-capacity` hoặc `spring.task.scheduling.pool.size` **không điều khiển loại executor/scheduler virtual ấy như một thread pool platform thông thường**.

**Không khái quát rằng property này biến mọi đường thực thi thành virtual thread.** Nếu ứng dụng đã tự định nghĩa `Executor`, `AsyncConfigurer`, bean tên `taskExecutor`/`applicationTaskExecutor` hoặc dùng executor từ thư viện khác, một số `@Async`, request handlers, listeners, scheduled tasks hay `CompletableFuture.supplyAsync` có thể vẫn chạy trên platform threads. Trước và sau thay đổi, xác nhận **executor được chọn thực tế** ở từng integration.

Một phép xác thực tại worker:

```java
@Async("myExecutor")
public CompletableFuture<Boolean> whichThread() {
    return CompletableFuture.completedFuture(
            Thread.currentThread().isVirtual());
}
```

Đây là **mảnh code trong Spring-managed bean**, cần `@EnableAsync`, `@Async`, `CompletableFuture`, bean `myExecutor` và lời gọi qua Spring proxy. Nó chỉ trả loại thread, không phải phép đo performance. Để không tự kiểm tra nhầm, gọi bean này từ **bean khác** qua Spring proxy; self-invocation `this.whichThread()` thường bỏ qua async advice trong proxy mode.

### Nếu tự tạo executor

Đối với module thực sự muốn thử virtual threads, có thể cấu hình:

```java
@Bean("virtualWorker")
public AsyncTaskExecutor virtualWorker() {
    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("vt-");
    executor.setVirtualThreads(true);
    return executor;
}
```

Đây là **mảnh Spring Framework 6.1+**, không phải class độc lập; cần imports `@Bean`, `AsyncTaskExecutor`, `SimpleAsyncTaskExecutor` và JDK 21+. `SimpleAsyncTaskExecutor` tạo thread cho mỗi task thay vì tái sử dụng một fixed-size thread pool. Nếu cần giới hạn số concurrent operations, dùng các bulkhead/connection/rate limits hoặc `setConcurrencyLimit` theo semantics phù hợp; đừng giả định `corePoolSize`/`maxPoolSize` của một `ThreadPoolTaskExecutor` vẫn áp dụng.

Đặc biệt cần phân biệt **giới hạn admitted tasks** với **giới hạn đồng thời gọi database**. Một global semaphore có thể làm mọi tác vụ bị nghẽn; nếu các downstream độc lập, giới hạn theo từng dependency và đặt deadline/cancellation rõ ràng.

### Chú ý với @Scheduled

Trong Spring Framework 6.1+, `SimpleAsyncTaskScheduler` tối ưu kiểu lập lịch phù hợp virtual threads: một scheduler thread và các task executions thường chạy trên thread riêng, **nhưng fixed-delay tasks vận hành trên một scheduler thread**, vì vậy Spring khuyến nghị cân nhắc fixed rate/cron cho phương án này.

Không đổi `@Scheduled(fixedDelay=...)` sang fixed rate một cách máy móc: fixed rate có thể tạo **các lần xử lý chồng lấp** nếu job lâu hơn chu kỳ. Phải giữ business invariant (no overlap/once per period) và có admission/claim policy phù hợp.

## 11. Pinning: kết luận phải gắn với phiên bản JDK

**Pinning** là tình huống virtual thread **không thể unmount khỏi carrier** khi chờ, khiến một platform carrier bị giữ. Nếu xảy ra thường xuyên, kéo dài và cùng nhiều tác vụ I/O, throughput có thể giảm.

### Java 21–23

Trên các JDK này, một virtual thread thực hiện thao tác blocking **bên trong `synchronized` method/block** có thể bị pin vào carrier. Mẫu dễ gặp:

```java
synchronized (lock) {
    callSlowBlockingApi();
}
```

Đây là **mảnh phản ví dụ trong ngữ cảnh Java 21–23**, cần `lock` và `callSlowBlockingApi` do ứng dụng định nghĩa. Không có nghĩa phải loại bỏ mọi `synchronized`: đoạn critical section ngắn, hoàn toàn trong RAM có thể không đáng kể. Vấn đề là **blocking dài và lặp lại khi đang giữ monitor**.

Trên JDK 21 có thể xem **JDK Flight Recorder** event `jdk.VirtualThreadPinned`, và một số phiên bản hỗ trợ `-Djdk.tracePinnedThreads=full` để tìm stack liên quan; phải kiểm tra tính tương thích/overhead của tùy chọn theo JDK đang dùng.

### Java 24 trở lên

**JEP 491 (JDK 24)** thay đổi cơ chế synchronization để virtual threads thực hiện blocking trong synchronized construct **không còn bị pin carrier chỉ vì đang ở trong monitor** như Java 21. Vì vậy không được viết một checklist năm 2026 kiểu “synchronized luôn pin virtual threads”, cũng không nên tự động thay tất cả synchronized bằng `ReentrantLock` mà không có bằng chứng.

Pinning còn có thể tồn tại ở **native/foreign function** hoặc các đường chạy phụ thuộc implementation; kiểm tra bằng JFR và version-specific documentation. **Không dùng JDK 24 test để suy ra ứng dụng deploy JDK 21 đã hết pinning**, và không dùng kết quả JDK 21 để phủ nhận cải tiến JDK 24.

Đây là một lý do mạnh để lập **ma trận cùng-version**: so platform và virtual trên chính JDK dự kiến triển khai, rồi tách riêng tác động của việc nâng JDK khi cần.

## 12. Công cụ đo và chẩn đoán

### JDK Flight Recorder (JFR)

JFR giúp xem CPU, allocation, GC, blocked/waiting states, thread scheduling và một số event virtual-thread đặc thù. Một cách bắt đầu bản ghi trong môi trường thử có quyền:

```bash
java -XX:StartFlightRecording=filename=virtual-load.jfr,duration=120s,settings=profile -jar app.jar
```

Đây là **lệnh minh họa** cho app có file jar thật, không phải lệnh đã chạy. Có thể dùng `jcmd` để điều khiển recording trên tiến trình đang chạy nếu môi trường cho phép. Cần chọn duration và profile phù hợp, tránh bật logging/profiling nặng ngoài giới hạn tác động được chấp nhận.

Trong JFR, tìm các event có sẵn với JDK đang chạy như `jdk.VirtualThreadPinned`, `jdk.VirtualThreadSubmitFailed`, `jdk.VirtualThreadStart`/`End` (một số event không bật mặc định), CPU sampling và allocation hotspots. Đọc **tỷ lệ, thời gian, stack trace và bản JDK**, không chỉ số lượng events.

### Thread dumps có virtual threads

Để chẩn đoán task đang chờ ở đâu trên các JDK hỗ trợ:

```bash
jcmd 12345 Thread.dump_to_file -format=json threads.json
```

`12345` là **một PID ví dụ**; khi sử dụng thực, thay bằng PID đã xác định và chọn đường dẫn output an toàn. Dump giúp quan sát virtual threads đang chờ socket/lock hoặc những stack được hỗ trợ, nhưng không thay thế số liệu latency/throughput. Dump virtual-thread có thể không có mọi thông tin mà platform-thread dump truyền thống hiển thị.

Trong ứng dụng Spring Boot có Actuator/Micrometer, nên kiểm tra:

- `http.server.requests` hoặc các HTTP metrics tương đương để xem RPS, error và histogram p95/p99.
- `jdbc.connections.active/idle/pending` hoặc HikariCP metrics theo registry/driver thực.
- Executor queue wait, activeCount/poolSize chỉ có nghĩa với **executor thực sự là thread pool**; với per-task virtual executor cần metric khác về in-flight and admission.
- Client HTTP pool pending, latency và rate limit/429.
- JVM process CPU, cgroup CPU throttling, heap/nonheap, RSS, GC pause/allocation rate.
- DB CPU, locks, transaction time, slow queries, connection utilization.

Tên metric có thể khác theo Spring Boot/Micrometer/driver/registry; **đừng đo một tên và coi không thấy metric là không có vấn đề**. Xem dashboard/Actuator thực tế và cả hệ thống downstream.

## 13. Đừng “pool virtual threads”: kiểm soát concurrency ở đúng tài nguyên

JDK khuyến nghị không tạo một fixed-size virtual-thread pool để tái sử dụng virtual threads; chúng được thiết kế **tạo mới cho mỗi task**. Nếu muốn hạn chế 20 concurrent DB calls, hãy hạn chế **sử dụng DB**:

```java
private final Semaphore dbPermits = new Semaphore(20);

public Result queryDatabase(Query query) throws InterruptedException {
    dbPermits.acquire();
    try {
        return databaseClient.execute(query);
    } finally {
        dbPermits.release();
    }
}
```

Đây là **mảnh code ứng dụng**, cần khai báo `Result`, `Query`, `databaseClient` và injection lifecycle phù hợp. Nó minh họa một bulkhead, không phải class tự chạy. Trong production, nên xét `tryAcquire(timeout)` để tránh hàng nghìn task chờ vô hạn, và thu metric **permit wait, timeout, rejection**.

Một pool connection như HikariCP vốn giới hạn số DB connections, nhưng nhiều virtual threads có thể đồng thời **chờ** pool. Việc chờ là rẻ hơn về OS thread, **không miễn phí về heap, object graph, request context, SLA và latency**.

Nếu chỉ tăng concurrency mà không có deadline, có thể gây:

- DB queue dài dẫn tới p99 cao.
- Nhiều giao dịch giữ locks/connection lâu hơn.
- Remote service bị bắn vượt rate limit.
- Memory retention của request payload và ThreadLocal.
- Retry storm khi timeout tăng.
- Hệ thống mất ổn định dù CPU application còn thấp.

Một chính sách admission rõ ràng (bounded in-flight, timeout, backpressure, circuit breaker) thường quan trọng không kém loại thread.

## 14. ThreadLocal, transaction và virtual threads trong Spring

Virtual threads vẫn là những `Thread` Java; `ThreadLocal` vẫn hoạt động. Nhưng số virtual threads có thể lớn hơn rất nhiều so với số platform workers thường được tái sử dụng, vì vậy hãy xem lại:

- **ThreadLocal cache:** nếu mỗi virtual thread khởi tạo một object/buffer lớn, việc tạo thread cho từng task làm cache được tạo lại nhiều lần, tăng allocation/GC. Không dùng `ThreadLocal` như “global cache theo request”.
- **MDC/log context:** phải thiết lập đúng vào virtual thread xử lý request, không tự giả định mọi executor/async boundary được tự động propagate. Dọn context theo scope.
- **Spring SecurityContext:** xác nhận integration và cơ chế propagation được hỗ trợ, tránh tự sao chép mutable security context không kiểm soát.
- **Imperative transaction:** `@Transactional` và transaction manager theo thread vẫn cần invocation qua Spring proxy/transaction boundary. Virtual threads **không cho phép dùng cùng `EntityManager`/JDBC connection đồng thời giữa nhiều threads**.
- **JPA lazy data:** không mang attached entity/session qua async task ngoài vòng đời transaction mà không có hợp đồng rõ.
- **TaskDecorator:** hữu ích với một số context propagation, nhưng không dùng để chuyển nguyên DB transaction sang worker.
- **Cancellation:** interrupt/timeout không tự rollback HTTP call đã thực hiện; cần deadlines và idempotency.

Khi thay executor, **thread name/ThreadLocal lifecycle** có thể khác. Một lỗi cache hoặc context leak có thể khiến bài test 30 giây trông tốt nhưng soak test vài giờ xuất hiện GC pressure.

## 15. CPU-bound và reactive workloads: đừng đo sai thứ cần tối ưu

Virtual threads phù hợp nhất với thread-per-task **blocking** work. Hai nhóm cần đặc biệt thận trọng:

**CPU-bound:** một chương trình hash/encrypt/parse chạy toàn CPU không có nhiều cơ hội unmount. Khi core đã full, thêm virtual threads không làm mỗi task chạy nhanh hơn. Với CPU-heavy batch, một bounded pool cỡ thích hợp với CPU quota, tối ưu thuật toán hoặc giảm allocation thường có giá trị hơn.

**Reactive/WebFlux:** nếu ứng dụng đã sử dụng Reactor và nonblocking drivers đúng cách, có thể xử lý nhiều in-flight operations với ít event-loop threads. Đổi sang virtual thread-per-request **không tự bổ sung năng lực downstream** và có thể tăng overhead/simplify code theo những đánh đổi khác. Không trộn blocking JDBC lên Netty event-loop chỉ vì một executor virtual tồn tại ở nơi khác.

Nên so sánh trên các **đường request cụ thể**: Spring MVC servlet với blocking JDBC/HTTP có profile khác Spring WebFlux reactive với R2DBC. Không kết luận từ một microbenchmark rằng toàn bộ hệ thống phải chuyển một mô hình.

## 16. Quy trình thu thập và báo cáo kết quả

Một báo cáo có thể kiểm chứng cần ghi lại:

| Trường | Nội dung cần ghi |
| --- | --- |
| Build & Git revision | Mã ứng dụng chính xác, cùng commit hoặc có diff ghi rõ |
| JDK | Major/minor, vendor, GC, JVM flags, cấu hình carrier nếu có |
| Framework | Spring Boot/Framework, Tomcat/Jetty/Netty, JDBC/HTTP libs |
| Hạ tầng | CPU quota, memory limit, replicas, node type |
| Baseline execution | Executor/pool/queue/worker count, rejection |
| Virtual execution | Configuration flag, executor thực sự, admission limits |
| Database | Max connections, active/pending, lock/slow-query metrics |
| Load driver | Open/closed-loop, concurrency, arrival RPS, warmup |
| Dataset & request mix | Payload sizes, tỷ lệ endpoint, read/write mix |
| Measurement window | Duration, lần lặp, startup/warmup excluded |
| Results | Successful RPS, p50/p95/p99, error, CPU/RSS/GC, resource waits |
| Operational check | Logs/JFR, pinning, shutdown, timeout, context safety |
| Decision | Keep/roll back/tune, tại sao dựa trên số liệu |

**Mẫu kết luận có logic đúng:**

- “Ở cùng JDK và cùng 8 DB permits, phương án virtual làm giảm executor queue wait; nhưng tuned platform pool 8 workers cũng giải phần lớn backlog. Không có đủ bằng chứng để migration vì p99/CPU/cost chưa được cải thiện trong workload thật.”
- “Khi DB pool chỉ còn 2 permits, các phương án đều chậm tương tự. Ưu tiên tối ưu DB query, transaction duration hoặc admission policy trước.”
- “Nếu ở thử tải thật đạt thêm completed RPS mà p99/error/DB health vẫn trong SLO và cost tốt hơn sau nhiều lần chạy, có thể bắt đầu canary.”

Các câu trên là **mẫu suy luận**, không phải kết quả production đã quan sát. Luôn gắn kết luận với dataset, thời gian chạy và số liệu đầy đủ.

### Phân tích sai số

Một run đo p95 thấp hơn 4% chưa chắc có ý nghĩa nếu biến thiên qua nhiều run là 7%. Cần lặp đủ lần, giữ môi trường ổn định, báo median và khoảng biến thiên/CI khi phù hợp. Với tail latency, tăng thời gian đo và số lượng samples; không dùng p99 của 40 task demo để quyết định SLA.

Cần phân biệt **throughput offered** và **throughput completed successfully**. Một load generator gửi 1.000 RPS nhưng server chỉ hoàn tất 500 RPS kèm hàng đợi tăng không có nghĩa hệ thống đạt 1.000 RPS. Đo thêm số task pending và thời gian drain sau khi ngừng phát tải.

## 17. Canary rollout và điều kiện rollback

Thay execution model có thể ảnh hưởng nhiều tầng nên rollout theo bước:

1. **Đo baseline production-like** trước khi đổi và lưu dashboard/config.
2. **Bật virtual threads ở staging** với JDK/framework chính xác; kiểm tra thread kind, transaction, security context, timeout, cancellation và shutdown.
3. **Chạy ma trận tải** ở cả môi trường bình thường, burst, downstream chậm và CPU-bound.
4. **So sánh tuned baseline** để tránh quy cải thiện tuning cho virtual threads.
5. **Canary ít traffic có giới hạn** với feature flag hoặc cấu hình phân nhóm instance.
6. **Theo dõi p95/p99, error, connection wait, CPU/RSS, GC, pinning/JFR và DB locks**; nếu thấy rủi ro, hạ tải hoặc rollback.
7. **Tăng traffic có điều kiện**, không tự động tăng vô hạn vì virtual threads tạo được nhiều task.
8. **Soak test và runbook** cho restart, graceful shutdown, job cancellation và xử lý công việc dở.
9. **Tổng kết theo SLO và chi phí**, giữ phương án chỉ khi có lợi ích có thể lặp lại.

Với Spring Boot, cấu hình `spring.threads.virtual.enabled` là một điểm kiểm soát thuận tiện nhưng **rollback phải tính cả loại executor/scheduler được auto-config**. Khi quay lại platform executor, pool/queue parameters và rejection policy có thể trở lại hiệu lực; nếu workload đã quen concurrency cao, phải điều chỉnh admission để tránh hồi quy.

Không thay lập lịch nghiệp vụ quan trọng hoặc đưa job state sang RAM mà không đánh giá durability. Virtual threads giải quyết **cách thực thi**, không giải quyết **đảm bảo giao nhận và phục hồi**.

## 18. Những lỗi thường gặp khi chuyển mà không đo

1. **Cho rằng virtual thread nhanh hơn platform thread trên mỗi phép tính.** CPU không tăng.
2. **So JDK khác version mà không tách hiệu ứng JDK.** JDK24 có thay đổi lớn về pinning so với JDK21.
3. **Chỉ đo trung bình latency.** p95/p99 có thể xấu khi downstream nghẽn.
4. **Chỉ đo submitted RPS.** Completion throughput và error mới phản ánh năng lực.
5. **Không đo resource wait.** Executor queue giảm có thể chỉ chuyển nghẽn vào DB pool.
6. **Bỏ qua tuned platform baseline.** Tăng worker hợp lý có thể đã đạt mục tiêu.
7. **Bật virtual mà không giới hạn downstream.** Có thể tạo hàng nghìn request chờ, gây OOM/429/lock contention.
8. **Dùng thread pool để “pool virtual threads”.** Virtual threads tạo theo task; quản lý concurrency bằng semaphore/bulkhead hoặc resource limit.
9. **Chạy demo `Thread.sleep` rồi tuyên bố production nhanh hơn X lần.** Nó chỉ mô phỏng chờ, không đại diện dữ liệu/network/JPA.
10. **Tưởng `spring.task.execution.pool.max-size` vẫn giới hạn virtual executor auto-config.** Boot có thể chuyển sang `SimpleAsyncTaskExecutor`, không còn pool tương đương.
11. **Tưởng mọi `@Async`/request/scheduled task đều đã chạy virtual.** Custom executors và proxy/qualifier có thể thay đổi.
12. **Dùng `fixedDelay` trên `SimpleAsyncTaskScheduler` mà không hiểu single scheduler thread.** Có thể mất lợi ích cách ly.
13. **Gắn mọi synchronized với pinning như JDK21.** JEP491 đã thay đổi hành vi monitor ở JDK24.
14. **Không đo ThreadLocal allocations/GC.** Số thread lớn có thể làm ThreadLocal cache tạo nhiều object.
15. **Không thử dependency slow/failure.** Lợi ích steady-state có thể biến thành latency spike khi DB chậm.
16. **Không thử shutdown/restart.** Các tác vụ đang dở cần cancellation/transaction/idempotency đúng.
17. **Không phân biệt platform pool và servlet container pool.** Phải đo đúng ranh giới đang nghẽn.
18. **Bỏ qua CPU quota container.** Host nhiều cores nhưng pod bị throttle vẫn là CPU-bound.
19. **Thử tải bằng closed-loop rồi kết luận chịu được RPS mở.** Coordinated omission có thể che tail latency.
20. **Không có tiêu chí rollback.** Một thay đổi làm xấu p99 hay DB health phải có phương án trở lại an toàn.

## 19. Tự kiểm tra

**Câu 1.** Có 1000 virtual threads đang chờ I/O; có nghĩa có 1000 CPU cores đang chạy không?  
**Đáp án:** Không. Nhiều virtual threads có thể unmount khi chờ, cùng chia sẻ số carrier/CPU cores hữu hạn.

**Câu 2.** Vì sao khi `Semaphore` giảm từ 8 permits xuống 2, platform và virtual có batch time gần nhau trong demo?  
**Đáp án:** Downstream chỉ cho hai tác vụ vào vùng service đồng thời. Nút thắt là permits/service time chứ không phải số worker thread được tạo.

**Câu 3.** Muốn biết virtual threads đã giúp gì, ngoài số completed/s cần đo gì?  
**Đáp án:** End-to-end p50/p95/p99, error/timeouts, CPU/RSS/GC, queue wait, DB/HTTP connection wait, resource utilization và cost.

**Câu 4.** So sánh platform trên JDK17 với virtual trên JDK24 có chứng minh chính virtual threads tốt hơn không?  
**Đáp án:** Chưa. Cần kiểm soát version/JVM differences hoặc làm ma trận tách hiệu ứng.

**Câu 5.** Khi nào virtual threads đáng thử nhất?  
**Đáp án:** Nhiều tác vụ độc lập, phần lớn thời gian chờ blocking I/O, platform worker pool đang cạn trong khi CPU và downstream còn headroom.

**Câu 6.** Vì sao Java24 không nên áp dụng nguyên xi checklist synchronized pinning của Java21?  
**Đáp án:** JEP491 cho phép virtual threads blocking trong synchronized construct không giữ carrier chỉ vì monitor như trước. Vẫn cần đo các trường hợp pinning còn lại.

**Câu 7.** Bật `spring.threads.virtual.enabled=true` có bảo đảm mọi Executor bean là virtual không?  
**Đáp án:** Không. Auto-config phụ thuộc Spring Boot version, sự hiện diện của custom Executor, AsyncConfigurer và đường integration thực tế.

**Câu 8.** Vì sao `ThreadLocal` cache có thể làm GC tăng khi dùng virtual threads?  
**Đáp án:** Nhiều thread ngắn đời cùng khởi tạo cache/object riêng thay vì tái sử dụng ít platform workers; tổng allocations có thể tăng.

**Câu 9.** Có thể kết luận phương án tốt hơn từ một run demo 40 task có p99 thấp hơn không?  
**Đáp án:** Không. Demo nhỏ, burst, I/O giả lập và sample ít; phải chạy nhiều lượt, workload thật, đủ samples và so tuned platform baseline.

**Câu 10.** Đổi sang virtual threads đã tạo durable queue và exactly-once processing chưa?  
**Đáp án:** Chưa. Đây là cách thực thi task; recovery/idempotency phải có giải pháp riêng.

**Ghi nhớ:** **Thay loại thread là một giả thuyết hiệu năng, không phải mục tiêu.** Đo tải thực tế, xác định tài nguyên bị nghẽn, so **cùng JDK và cùng workload**, xem p95/p99, completed throughput, CPU/RSS/GC, DB/HTTP wait và lỗi. Chỉ chuyển khi kết quả có thể lặp lại và SLO/cost/correctness thực sự tốt hơn.

### Tài liệu tham khảo

- [OpenJDK JEP 444 — Virtual Threads](https://openjdk.org/jeps/444)
- [OpenJDK JEP 491 — Synchronize Virtual Threads without Pinning (JDK 24)](https://openjdk.org/jeps/491)
- [Oracle Java 21 — Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
- [Oracle Java 24 — Core Libraries, Virtual Threads](https://docs.oracle.com/en/java/javase/24/core/virtual-threads.html)
- [Oracle Java 24 — Significant Changes, JEP 491](https://docs.oracle.com/en/java/javase/24/migrate/significant-changes-jdk-24.html)
- [Spring Boot 3.4 — Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.4/reference/features/task-execution-and-scheduling.html)
- [Spring Boot — SpringApplication, Virtual Threads and keep-alive](https://docs.spring.io/spring-boot/reference/features/spring-application.html)
- [Spring Framework — Task Execution and Scheduling, SimpleAsyncTaskScheduler](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Java 21 — Executors.newVirtualThreadPerTaskExecutor](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html#newVirtualThreadPerTaskExecutor())


</details>

- [Quay lại đầu trang](#back-to-top)
