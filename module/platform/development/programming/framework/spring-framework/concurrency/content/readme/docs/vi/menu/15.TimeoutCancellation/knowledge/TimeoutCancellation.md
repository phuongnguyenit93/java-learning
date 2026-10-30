<a id="back-to-top"></a>

# Hết hạn và hủy

## Menu
- [Phân biệt thời gian chờ trong hàng đợi, thời gian chạy và thời gian bên gọi chờ](#timeout-cancellation-001)
- [Future.get(timeout) không tự dừng công việc nền](#timeout-cancellation-002)
- [orTimeout() và completeOnTimeout() không mặc nhiên dừng thao tác đang chạy](#timeout-cancellation-003)
- [Future.cancel(true) và cơ chế ngắt có tính hợp tác](#timeout-cancellation-004)
- [CompletableFuture.cancel() không bảo đảm ngắt luồng xử lý](#timeout-cancellation-005)
- [Xử lý InterruptedException và bảo toàn tín hiệu ngắt phù hợp](#timeout-cancellation-006)
- [Đặt timeout riêng cho truy cập cơ sở dữ liệu hoặc lời gọi mạng](#timeout-cancellation-007)
- [Ngăn công việc hết giá trị tiếp tục tạo tác dụng phụ](#timeout-cancellation-008)
- [Kiểm tra hủy trước khi bắt đầu và hủy khi tác vụ đang chạy](#timeout-cancellation-009)

## <a id="timeout-cancellation-001">Phân biệt thời gian chờ trong hàng đợi, thời gian chạy và thời gian bên gọi chờ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt thời gian chờ trong hàng đợi, thời gian chạy và thời gian bên gọi chờ

## 1. Bài toán: cùng một task nhưng ba loại độ trễ

Khi một ứng dụng Spring gửi `Runnable` hoặc `Callable<T>` vào `ThreadPoolTaskExecutor`, công việc có thể chưa bắt đầu ngay: worker đang bận, task phải nằm trong hàng đợi, rồi mới được chạy. Trong khi đó, thread gọi có thể xử lý việc khác, chờ kết quả bằng `Future.get()` hoặc đã trả response HTTP.

Vì thế phải phân biệt **ba khoảng thời gian**:

1. **Thời gian chờ trong hàng đợi (queue wait, submit-to-start)**: từ lúc task được gửi/chấp nhận đến khi worker bắt đầu chạy. Khoảng submit-to-start đo được thường có thêm chút overhead của việc gửi/dispatch, không nhất thiết toàn bộ là thời gian nằm vật lý trong `BlockingQueue`.
2. **Thời gian chạy (execution time, runtime)**: từ khi worker bắt đầu phần việc được đo đến khi phần việc kết thúc. Đây là elapsed wall-clock time; có thể gồm cả CPU, JDBC/HTTP I/O, chờ lock, sleep hoặc pause do scheduler.
3. **Thời gian bên gọi chờ (caller wait time)**: khoảng thread gọi bị chặn trong một lời gọi lấy kết quả, ví dụ `Future.get()`, `get(timeout, unit)` hoặc `CompletableFuture.join()`. Caller có thể chờ ngay, chờ muộn, chờ nhiều lần hoặc không chờ lần nào.

**Điểm cốt lõi:** hai loại đầu đo vòng đời của **task**, loại thứ ba đo hành vi của **caller**. Không nên cộng cả ba lại để tìm tổng thời gian hoàn tất tác vụ, vì caller wait thường chồng lên queue wait/runtime.

Một task có thể chờ trong queue 300 ms, chạy 200 ms, nhưng caller lấy kết quả sau 600 ms nên `get()` trả gần như ngay lập tức. Ngược lại, caller có thể timeout sau 100 ms dù task còn chưa bắt đầu chạy và sau đó vẫn hoàn tất bình thường.

## 2. Timeline và công thức đo chính xác

Đặt các timestamp trên cùng đồng hồ đơn điệu của JVM:

- `t0`: thời điểm gần nhất quán với việc submit task.
- `t1`: worker bắt đầu thực hiện task.
- `t2`: task body kết thúc thành công hoặc thất bại.
- `t3`: caller bắt đầu một lần gọi `get()` để chờ kết quả.
- `t4`: lời gọi `get()` đó kết thúc, bằng kết quả, lỗi, timeout hoặc interruption.

```text
Task lifecycle:
t0 submit                 t1 worker begins              t2 task finishes
|--------------------------|-----------------------------|
          queue wait               execution time

Caller thread (independent interval):
                     t3 get() begins          t4 get() returns/throws
                     |------------------------|
                            caller wait
```

Nếu timestamp đặt ở những ranh giới thống nhất:

```text
queueWaitNanos      ≈ t1 - t0
executionNanos       = t2 - t1
submitToFinishNanos ≈ t2 - t0
oneCallerWaitNanos   = t4 - t3

submitToFinish ≈ queueWait + execution
callerWait KHÔNG phải một số hạng cộng thêm vào tổng phía trên.
```

Dấu xấp xỉ nhắc rằng `t0` có thể được ghi ngay trước lệnh `submit()` và vì thế bao gồm chi phí dispatch; `t2` có thể được ghi trước một khoảng nhỏ để Future hoàn tất/đăng kết quả. Để phân tích SLO chính xác, cần mô tả cụ thể mỗi timestamp đo ở boundary nào.

Trong một JVM, dùng `System.nanoTime()` để tính **duration**, không dùng `System.currentTimeMillis()` cho phép trừ thời gian đòi hỏi độ ổn định trước việc chỉnh đồng hồ hệ thống. Giá trị `nanoTime()` là tương đối, **không so sánh trực tiếp giữa hai process/máy khác nhau**.

## 3. Ví dụ số: caller wait phụ thuộc lúc gọi get()

Giả sử:

- Submit tại 0 ms.
- Worker bắt đầu tại 300 ms.
- Task kết thúc tại 500 ms.

Khi ấy queue wait khoảng **300 ms**, execution time **200 ms**, submit-to-finish **500 ms**.

| Caller hành động | Caller wait xấp xỉ | Diễn giải |
| --- | ---: | --- |
| Gọi `get()` ngay sau submit | 500 ms | Chờ cả queue và execution |
| Đến 350 ms mới gọi `get()` | 150 ms | Chỉ chờ phần task chưa hoàn tất |
| Đến 600 ms mới gọi `get()` | Gần 0 ms | Future đã có kết quả |
| Gọi `get(100 ms)` ngay từ 0 ms | 100 ms rồi `TimeoutException` | Task còn ở queue và vẫn có thể tiếp tục |
| Không gọi `get()` | 0 ms | Task vẫn có thể chạy độc lập |

Đây là timeline minh họa lý tưởng, không phải stdout thực tế. Chi phí scheduler, GC và wrapper có thể khiến các mốc chênh lệch nhỏ.

Nếu ứng dụng trả HTTP 202 ngay sau submit, **request latency có thể thấp** trong khi business-job latency rất cao. Nếu controller lập tức gọi `get()`, thread HTTP có thể bị chặn dù task chạy trên pool riêng.

## 4. Queue wait hình thành như thế nào trong ThreadPoolTaskExecutor?

`ThreadPoolTaskExecutor` cấu hình `ThreadPoolExecutor` bên dưới. Quy tắc tiếp nhận task thường là:

1. Ưu tiên tạo core worker khi chưa có đủ `corePoolSize`.
2. Khi đã đủ core worker, thử đưa task vào queue.
3. Nếu queue không nhận thêm được, thử tạo worker đến `maxPoolSize`.
4. Nếu worker và queue đã hết khả năng nhận, áp dụng rejection policy.

Cấu hình `queueCapacity` thay đổi cách xử lý:

- Với `queueCapacity > 0`, Spring dùng `LinkedBlockingQueue` dung lượng đã đặt. Task có thể đợi trước khi worker được giải phóng.
- Với `queueCapacity = 0`, Spring dùng `SynchronousQueue`, không lưu backlog; task được handoff trực tiếp hoặc pool thử mở worker khác.
- Với queue dung lượng rất lớn, số task chờ có thể tích lũy nhiều, làm p95/p99 queue wait và memory retention tăng, dù `maxPoolSize` đã đặt cao.

**queueCapacity là số phần tử, không phải thời gian.** Không tồn tại ý nghĩa “queueCapacity=100 tức chờ tối đa 100 ms”. Thời gian chờ phụ thuộc tốc độ gửi task, tốc độ hoàn thành, pool size và lịch xử lý.

### 4.1. Queue size không bằng queue wait

`executor.getQueueSize()` là một snapshot về lượng task đang chờ, không phải tuổi của task. Một hàng đợi chỉ có một task vẫn có thể chứa task đang chờ rất lâu nếu worker bị kẹt. Ngược lại, queue size bằng 0 vẫn có thể đi kèm runtime cao hoặc nhiều caller đang chờ Future.

Với `SynchronousQueue`, queue size về mặt cấu trúc bằng 0, không đủ để kết luận executor không bị quá tải. Cần theo dõi `activeCount`, throughput, rejection và phân phối submit-to-start latency.

### 4.2. Task bị reject hoặc bị hủy trước khi chạy

Nếu submit ném `TaskRejectedException`, task chưa được chấp nhận. Nếu bị cancel khi chưa bắt đầu, `t1/t2` không tồn tại theo nghĩa task body. Vì vậy không đưa các job này vào histogram “execution time của task đã chạy” với giá trị 0 giả. Ghi riêng **rejected, cancelled-before-start, expired-before-start** để tránh sai lệch thống kê.

## 5. Runtime: thời gian trôi qua, không phải thời gian dùng CPU

Một worker có thể chạy task trong 1 giây nhưng chỉ dùng vài mili giây CPU nếu phần lớn thời gian chờ JDBC, HTTP response, lock, filesystem hoặc semaphore. Đó vẫn là **execution time 1 giây** khi đo elapsed time ở boundary của task.

Phân biệt hai hướng tối ưu:

- Queue wait cao nhưng runtime thấp: thường cần xem backlog, sizing core/max/queue, admission control và fairness.
- Runtime cao nhưng queue wait thấp: thường cần xem logic, I/O, query, lock, downstream timeouts hoặc giới hạn connection pool.

Chỉ tăng số thread khi bottleneck thật sự cho phép có thêm concurrency. Với downstream DB đã cạn connections, tăng thread pool có thể khiến cả queue wait lẫn runtime tệ hơn.

Một fragment đo runtime:

```java
long startedNanos = System.nanoTime();
try {
    return work.call();
} finally {
    long executionNanos = System.nanoTime() - startedNanos;
    recordExecutionTime(executionNanos);
}
```

Đoạn này là fragment, đòi hỏi `Callable<T> work` và hàm `recordExecutionTime(...)` đã khai báo. Nếu metrics callback ném lỗi thì nó có thể làm thay đổi kết quả task; observer nên không ném exception.

## 6. Caller wait: giới hạn của get(timeout) là giới hạn chờ, không phải giới hạn chạy

`Future.get(timeout, unit)` chờ tối đa khoảng được cung cấp cho **lần gọi đó**. Nếu kết quả chưa sẵn sàng, nó ném `TimeoutException`. Quan trọng:

- Timeout của `get` **không tự gọi `future.cancel(...)`**.
- Nó **không dừng** worker và không xóa task khỏi queue.
- Task có thể bắt đầu sau khi caller đã timeout và tiếp tục thực hiện side effect.
- Nếu caller gọi `get(100ms)` nhiều lần, mỗi lần được một budget chờ mới; tổng thời gian caller bị block có thể vượt 100 ms.
- Nếu caller bắt đầu `get(100ms)` sau 900 ms kể từ submit, thời gian toàn workflow có thể khoảng 1.000 ms hoặc hơn, không phải hạn 100 ms từ lúc submit.

Ví dụ fragment:

```java
try {
    String value = future.get(100, TimeUnit.MILLISECONDS);
    use(value);
} catch (TimeoutException ex) {
    // Future vẫn có thể đang ở queue hoặc đang chạy.
    // Quyết định rõ: tiếp tục theo dõi, cancel, trả trạng thái pending...
}
```

Đây là fragment; `Future<String> future`, hàm `use` và các imports/cách xử lý `InterruptedException`/`ExecutionException` phải có trong ứng dụng thật.

### 6.1. Request latency không trùng caller wait

Request latency còn bao gồm validation, serialization, database, logic HTTP và nhiều bước khác. Caller wait chỉ là khoảng thời gian request thread bị chặn do Future. Một request không gọi `get()` có caller wait bằng 0 dù job nền mất nhiều phút.

Ở phía người dùng, cần đặt SLO tách biệt cho **request acknowledgement latency** và **business job completion latency**.

## 7. Deadline, timeout và cancellation: ba ý nghĩa khác nhau

Vì đề bài thuộc nhóm **Hết hạn và hủy**, cần làm rõ sự khác biệt:

| Cơ chế | Hạn chế điều gì? | Có tự dừng task không? |
| --- | --- | --- |
| `Future.get(timeout)` | Thời gian **caller chờ trong một lần** | Không |
| Deadline nghiệp vụ | Hạn cuối mà kết quả còn có giá trị | Không, nếu không có kiểm tra/chính sách |
| Timeout của HTTP/JDBC client trong task | Thời gian I/O/downstream được phép chờ | Phụ thuộc API và cách cấu hình |
| `Future.cancel(true)` | Cố gắng hủy Future và có thể yêu cầu interrupt task | Best-effort, không bảo đảm task dừng ngay |
| `CompletableFuture.orTimeout(...)` | Complete Future bằng lỗi timeout khi chưa xong | Không cam kết interrupt công việc nền |
| `CompletableFuture.completeOnTimeout(...)` | Complete Future bằng giá trị fallback | Không cam kết hủy việc thật |

### 7.1. Hết hạn khi còn ở queue

Một task có deadline 10:00, nhưng đến 10:01 worker mới nhận được. Nếu không có guard, task có thể vẫn thực hiện một công việc đã vô ích. Có thể truyền deadline vào payload rồi kiểm tra **ngay khi bắt đầu thực thi**:

```java
long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);

Callable<String> work = () -> {
    if (System.nanoTime() - deadlineNanos >= 0) {
        throw new TimeoutException("Expired before work started");
    }
    return performWork();
};
```

Đây là fragment, cần `Callable`, `TimeUnit`, `TimeoutException` và hàm `performWork()` có sẵn. Nó không lấy task ra khỏi queue trước khi start; nó chỉ bỏ qua phần việc sau khi đã có worker nếu deadline hết hạn.

Để thực sự điều khiển backlog, cần thêm bounded queue, admission control, policy loại bỏ việc cũ và quy trình theo dõi trạng thái.

### 7.2. Hết thời gian chạy

Một task đã nhận worker có thể mắc kẹt ở network/DB. Lúc này `get(timeout)` của caller không đủ; phải cấu hình timeout ở client/downstream và/hoặc cooperative cancellation trong task.

Một phương thức `Future.cancel(true)` của FutureTask có thể gửi interrupt cho worker đang chạy, nhưng không thể khẳng định HTTP/socket/JDBC call nào cũng dừng ngay. `CompletableFuture.cancel(true)` có semantics riêng, không dùng tham số `mayInterruptIfRunning` để bảo đảm interrupt.

### 7.3. Không retry side effect chỉ vì người gọi hết kiên nhẫn

Nếu user gửi thanh toán hoặc email, caller có thể timeout trước khi task ghi xong. Gửi lại cùng một việc ngay lập tức có thể tạo hai side effect. Dùng idempotency key, job status, transaction/outbox hoặc đối soát phù hợp.

Timeout của người gọi phản ánh **thiếu kết quả đúng hạn**, không tự chứng minh công việc chưa xảy ra.

## 8. Thực nghiệm runnable Java 21 + Spring Framework 6.2.1

Chương trình này cố ý tạo hai lần timeout của cùng một `Future<Integer>`:

1. Pool có **1 worker** và queueCapacity=2.
2. Task A chiếm worker, chờ latch.
3. Task B được submit, nhưng không thể chạy vì A đang giữ worker.
4. Caller gọi `B.get(30ms)` và timeout **khi B còn ở queue**.
5. Thả A, task B bắt đầu và bị giữ bởi latch khác.
6. Caller lại `B.get(30ms)` và timeout **khi B đang chạy**.
7. Thả B, đọc kết quả 42. Hai lần timeout không làm Future bị hủy.

Không dùng `Thread.sleep()` để đoán lịch thực thi. Tất cả mốc quan trọng có `CountDownLatch` bảo vệ.

### 8.1. Cấu trúc Maven

```text
task-timing-demo/
├── pom.xml
└── src/main/java/demo/TaskTimingDemo.java
```

**Tệp `pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>task-timing-demo</artifactId>
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
          <mainClass>demo.TaskTimingDemo</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

**Tệp `src/main/java/demo/TaskTimingDemo.java`:**

```java
package demo;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class TaskTimingDemo {
    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean("workExecutor")
        public ThreadPoolTaskExecutor workExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(2);
            executor.setThreadNamePrefix("timed-");
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(5);
            return executor;
        }
    }

    private static void await(CountDownLatch latch, String step)
            throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException(step + " not reached");
        }
    }

    private static boolean timedOut(
            Future<?> future, AtomicLong callerWaitNanos)
            throws InterruptedException, ExecutionException {
        long beforeWait = System.nanoTime();
        try {
            future.get(30, TimeUnit.MILLISECONDS);
            return false;
        } catch (TimeoutException expected) {
            return true;
        } finally {
            callerWaitNanos.addAndGet(System.nanoTime() - beforeWait);
        }
    }

    public static void main(String[] args) throws Exception {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            ThreadPoolTaskExecutor executor =
                    context.getBean("workExecutor", ThreadPoolTaskExecutor.class);

            CountDownLatch blockerStarted = new CountDownLatch(1);
            CountDownLatch releaseBlocker = new CountDownLatch(1);
            CountDownLatch taskStarted = new CountDownLatch(1);
            CountDownLatch releaseTask = new CountDownLatch(1);
            AtomicLong startedNanos = new AtomicLong();
            AtomicLong finishedNanos = new AtomicLong();
            AtomicLong callerWaitNanos = new AtomicLong();

            try {
                Future<Void> blocker = executor.submit((Callable<Void>) () -> {
                    blockerStarted.countDown();
                    releaseBlocker.await();
                    return null;
                });
                await(blockerStarted, "blockerStarted");

                long submittedNanos = System.nanoTime();
                Future<Integer> task = executor.submit((Callable<Integer>) () -> {
                    startedNanos.set(System.nanoTime());
                    taskStarted.countDown();
                    try {
                        releaseTask.await();
                        return 42;
                    } finally {
                        finishedNanos.set(System.nanoTime());
                    }
                });

                boolean timeoutInQueue = timedOut(task, callerWaitNanos);
                releaseBlocker.countDown();
                await(taskStarted, "taskStarted");
                boolean timeoutWhileRunning = timedOut(task, callerWaitNanos);
                releaseTask.countDown();

                int result = task.get(5, TimeUnit.SECONDS);
                blocker.get(5, TimeUnit.SECONDS);

                long queueWaitNanos = startedNanos.get() - submittedNanos;
                long executionNanos = finishedNanos.get() - startedNanos.get();

                System.out.println("timeoutInQueue=" + timeoutInQueue);
                System.out.println("timeoutWhileRunning=" + timeoutWhileRunning);
                System.out.println("result=" + result);
                System.out.println("cancelled=" + task.isCancelled());
                System.out.println("queueWaitPositive=" + (queueWaitNanos > 0));
                System.out.println("executionPositive=" + (executionNanos > 0));
                System.out.println("callerWaitPositive=" +
                        (callerWaitNanos.get() > 0));
            } finally {
                releaseBlocker.countDown();
                releaseTask.countDown();
            }
        }
    }
}
```

### 8.2. Biên dịch và chạy

Yêu cầu Maven và JDK 21 trở lên. Từ thư mục chứa `pom.xml`:

```bash
mvn -q -DskipTests compile exec:java
```

**Stdout đã biên dịch và kiểm thử thực tế:**

```text
timeoutInQueue=true
timeoutWhileRunning=true
result=42
cancelled=false
queueWaitPositive=true
executionPositive=true
callerWaitPositive=true
```

Chương trình in boolean để tránh khẳng định các số mili giây không ổn định giữa các môi trường. Kết quả 42 và hai lần timeout đã được xác nhận bằng `Future`; `cancelled=false` cho thấy việc timeout ở caller không làm Future bị hủy.

### 8.3. Giải thích ba phép đo trong code

- `submittedNanos` được lấy trước khi gửi B.
- `startedNanos` được ghi khi worker đã bắt đầu B, sau khi A được giải phóng.
- `finishedNanos` được ghi trong `finally` của B khi body B kết thúc.
- `callerWaitNanos` được cộng thêm thời gian elapsed của **hai lời gọi `get(30ms)`** trong hàm `timedOut`.

Do đó:

```java
long queueWaitNanos = startedNanos.get() - submittedNanos;
long executionNanos = finishedNanos.get() - startedNanos.get();
long callerBlockedNanos = callerWaitNanos.get();
```

Đây là fragment giải thích, lấy từ các biến đã khai báo trong ví dụ. Nó đo khoảng submit-to-start, thời gian chạy của B và **tổng thời gian chờ của đúng hai lần get(timeout) được instrument**. Nó không tính lời gọi `get(5, SECONDS)` cuối để nhận 42; muốn đo toàn bộ caller wait của một flow phải bọc tất cả những lần chờ cần tính.

Có thể thêm đoạn sau để xem các số đo trên máy:

```java
System.out.printf(
        "queue=%.3fms, execution=%.3fms, callerWait=%.3fms%n",
        queueWaitNanos / 1_000_000.0,
        executionNanos / 1_000_000.0,
        callerBlockedNanos / 1_000_000.0);
```

Các giá trị này **sẽ thay đổi theo scheduling**, không có stdout số cố định. Hai lần caller wait chồng lên một phần thời gian queue/execution, nên không cộng chúng vào submit-to-finish.

### 8.4. Vì sao test ổn định?

`blockerStarted` bảo đảm worker đã chiếm bởi A. `releaseBlocker` bảo đảm B còn ở queue trong lần timeout đầu. `taskStarted` xác nhận B đã chuyển từ queue sang running. `releaseTask` giữ B đang chạy trong lần timeout thứ hai. `finally` giải phóng cả hai latch nếu assertion xảy ra lỗi, tránh để worker kẹt mãi.

Đây là kỹ thuật test concurrency tốt hơn dựa vào `Thread.sleep(100)`, vì thứ tự thực thi không được quyết định bằng cảm giác rằng thread có lẽ đã chạy.

## 9. Đo thực tế bằng wrapper khi không dùng latch

Trong production có thể bọc `Callable<T>` tại điểm submit:

```java
// Fragment utility: imports Callable, Future, LongConsumer, AsyncTaskExecutor.
public static <T> Future<T> submitMeasured(
        AsyncTaskExecutor executor,
        Callable<T> work,
        LongConsumer queueObserver,
        LongConsumer runtimeObserver) {

    long submittedAt = System.nanoTime();

    return executor.submit(() -> {
        long startedAt = System.nanoTime();
        queueObserver.accept(startedAt - submittedAt);
        try {
            return work.call();
        } finally {
            runtimeObserver.accept(System.nanoTime() - startedAt);
        }
    });
}
```

Đây là fragment cần nằm trong một class và cần observer thread-safe, nhanh, không ném exception; nếu observer ném lỗi có thể làm task hỏng hoặc che mất lỗi nghiệp vụ. Vùng đo submit-to-start bao gồm chi phí dispatch, không phải chỉ riêng thời gian trong cấu trúc queue.

**Nếu submit bị reject**, wrapper chưa từng chạy; phải ghi nhận `TaskRejectedException` ở caller hoặc instrumentation của executor. Nếu task bị hủy trước start, cũng không có runtime sample như task đã chạy.

### 9.1. TaskDecorator của Spring có đủ không?

`ThreadPoolTaskExecutor.setTaskDecorator(...)` có thể bọc callback để thiết lập MDC, context hoặc đo timing. Nhưng với `submit(...)`, callback mà decorator thấy có thể là `FutureTask` đã bọc task gốc; lỗi của task có thể được lưu trong Future thay vì thoát ra khỏi `run()`.

Vì vậy `TaskDecorator` không đương nhiên cho bạn exception và timestamp gốc một cách hoàn chỉnh. Hãy xác định nơi capture submit time, cách nhận diện task, và ai quan sát Future outcome. Không mặc định `catch(Throwable)` quanh `Runnable.run()` sẽ bắt được mọi lỗi từ task submit.

### 9.2. Những chỉ số cần xem cùng nhau

- Histogram queue wait (p50/p95/p99), runtime (p50/p95/p99).
- Caller wait phân theo endpoint/call site.
- Submit-to-completion latency của công việc thật.
- Accepted, rejected, expired, cancelled, failed, succeeded counts.
- `getPoolSize()`, `getActiveCount()`, `getQueueSize()` theo thời gian.
- CPU, GC, thread count, DB/HTTP pool wait, downstream timeout và retry rate.

Một snapshot queue size tại một thời điểm không thay thế được distribution của thời gian chờ.

## 10. Deadline chung cho toàn workflow thay vì reset get(timeout)

Giả sử request có ngân sách **800 ms** tính từ lúc bắt đầu, nhưng mất 500 ms cho validation. Nếu sau đó gọi `future.get(800ms)`, tổng thời gian có thể lên đến 1.300 ms.

Một cách tính budget còn lại bằng monotonic clock:

```java
long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(800);

// ... nhiều bước khác đã trôi qua ...

long remaining = deadline - System.nanoTime();
if (remaining <= 0) {
    throw new TimeoutException("Overall deadline reached");
}

String value = future.get(remaining, TimeUnit.NANOSECONDS);
```

Đây là fragment; cần `Future<String> future` và xử lý các checked exceptions. Mỗi bước tiếp theo phải tính lại `remaining` từ **cùng deadline**. Điều này giới hạn thời gian chờ tổng của caller tốt hơn việc tạo timeout mới mỗi bước, nhưng **chưa tự buộc task dừng đúng deadline**.

Khi truyền qua nhiều JVM, không gửi nguyên giá trị `System.nanoTime()` như timestamp toàn cục: gốc monotonic clock khác nhau. Có thể truyền ngân sách thời gian còn lại hoặc một deadline theo wall clock được xử lý có tính đến sai lệch đồng hồ.

## 11. Spring @Async và CompletableFuture: dễ nhầm phạm vi đo

### 11.1. @Async chỉ chạy qua proxy khi đủ điều kiện

Trong `AdviceMode.PROXY` mặc định, `@Async` cần Spring-managed bean, cấu hình `@EnableAsync` (hoặc XML annotation-driven) và caller gọi qua proxy. Lời gọi `this.asyncMethod()` trong cùng target bypass proxy; lúc đó method có thể chạy đồng bộ, không tồn tại một lượt async submission mới cho method đó.

Nếu muốn đo queue wait của `@Async`, cần instrument tại executor/submission boundary thay vì chỉ lấy timestamp đầu method. Đừng cố sửa “queue wait=0” bằng tăng `maxPoolSize` khi thực ra không có proxy interception.

### 11.2. Future của proxy khác giá trị Future tạm của target

Một method `@Async` khai báo `CompletableFuture<T>` có thể trả `CompletableFuture.completedFuture(value)` trong body. Đây là Future tạm chuyển giá trị từ target; caller nhận Future của proxy. Future của proxy có thể còn pending cho đến khi worker chạy và bridge kết quả hoàn tất.

Khi đo caller wait, đo trên Future caller thực tế cầm; khi đo runtime, xác định thời gian của invocation target/bridge; khi đo queue wait, bắt đầu từ điểm Spring submit invocation task.

### 11.3. get(timeout), orTimeout và completeOnTimeout khác nhau

- `future.get(timeout, unit)` timeout lời gọi **chờ**, không tự thay đổi completion state của Future.
- `CompletableFuture.orTimeout(timeout, unit)` làm Future hoàn tất bằng lỗi timeout nếu vẫn chưa xong, nhưng không cam kết hủy/interrupt worker.
- `CompletableFuture.completeOnTimeout(defaultValue, timeout, unit)` cho phép Future hoàn tất bằng fallback, không chứng minh công việc nền đã dừng.

Không dùng ba API này thay thế cho timeout của JDBC/HTTP client hay contract cancel nghiệp vụ.

## 12. Khi caller timeout: tiếp tục, hủy hay theo dõi?

Các lựa chọn cần dựa trên tính chất công việc:

**Tác vụ có thể bỏ qua**, ví dụ phép tính gợi ý nhanh: caller có thể thử `cancel(...)` hoặc đánh dấu kết quả không còn cần. Task phải hỗ trợ cooperative cancellation nếu muốn thật sự tiết kiệm worker.

**Tác vụ bắt buộc hoàn tất**, ví dụ phát hành hóa đơn: caller timeout không làm yêu cầu nghiệp vụ hết hiệu lực. Cần lưu job ID, trạng thái durable, idempotency, retry có kiểm soát hoặc outbox/broker thích hợp; request nên có cách tra cứu kết quả.

**Tác vụ đã quá deadline nghiệp vụ**, ví dụ refresh một cache snapshot cũ: nên kiểm tra deadline trước khi bắt đầu side effect hoặc tại các safe points trong task và ghi trạng thái expired. Đừng giả vờ queue automatically biết deadline nếu chỉ cấu hình `queueCapacity`.

Tách biệt **ngưng chờ của người gọi**, **ngưng xử lý của worker** và **ngưng hiệu lực nghiệp vụ**.

## 13. Những sai lầm phổ biến

| Sai lầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Xem queue wait, runtime và caller wait là một | Đo sai latency và nguyên nhân | Capture timestamp ở ba boundary riêng |
| Cộng caller wait vào queue + runtime | Đếm trùng thời gian | Đặt timeline song song |
| Cho rằng `get(timeout)` giới hạn execution time | Chỉ giới hạn một lần caller đợi | Thêm deadline/timeout bên trong task |
| Dùng `queueCapacity=100` như queue timeout 100 ms | Capacity là số task | Đo tuổi task và kiểm tra deadline |
| Đợi Future sau khi task đã xong rồi suy runtime từ get() | Caller wait gần 0 dù runtime có thể lớn | Đo start/end trong task |
| Dùng `get(100ms)` nhiều lần và nghĩ tổng là 100ms | Mỗi lời gọi có budget mới | Dùng deadline chung |
| Tin `cancel(true)` luôn kill worker | Interrupt best-effort, phụ thuộc API | Cooperative cancellation và cleanup |
| Chỉ nhìn `getQueueSize() == 0` | Không phản ánh task đang chạy/chờ caller | Theo dõi activeCount, runtime, wait latency |
| Tăng queue để “giải quyết” runtime cao | Chỉ giữ thêm backlog | Tìm bottleneck của task/downstream |
| Gắn timestamp đầu `@Async` và coi đó là submit time | Bỏ mất queue wait | Đo tại proxy/executor boundary |
| Chỉ nhìn `Future.isDone()` và coi success | Done bao gồm cancel/exception | Phân loại outcome riêng |
| Bỏ qua rejection/cancel-before-start | Metrics thiếu những task không chạy | Ghi rejected/cancelled/expired riêng |
| Retry việc tạo side effect ngay sau timeout | Có thể chạy hai lần | Idempotency key và job status |
| Trừ `nanoTime()` của hai máy | Gốc monotonic clock khác nhau | Truyền budget/deadline phù hợp |

## 14. Bài tập tự kiểm tra

**Câu 1.** Submit tại 0 ms, start tại 250 ms, finish tại 400 ms, caller bắt đầu get lúc 300 ms. Tính ba đại lượng.

Queue wait 250 ms, runtime 150 ms, caller wait xấp xỉ 100 ms. Submit-to-finish 400 ms, **không cộng thêm** 100 ms caller wait.

**Câu 2.** Caller nhận `TimeoutException` khi task chưa bắt đầu. Task có bị hủy không?

Không. `Future.get(timeout)` không tự hủy. Task có thể được worker chạy sau khi queue có chỗ giải phóng.

**Câu 3.** Queue wait 900 ms, runtime 10 ms so với queue wait 10 ms, runtime 900 ms — tổng latency gần bằng nhau; cách xử lý có giống nhau không?

Không. Ca đầu cần xem backlog và executor scheduling, ca sau cần xem tác vụ/I/O/lock/downstream. Chỉ tăng queue capacity có thể khiến trường hợp đầu tệ hơn.

**Câu 4.** Một caller không gọi `get()` mà trả HTTP 202 ngay. Caller wait bằng bao nhiêu? Có chứng minh task đã xong không?

Caller wait đối với Future bằng 0; không chứng minh task đã hoàn tất. Cần job completion metric và API trạng thái.

**Câu 5.** Cùng một Future, caller gọi `get(100ms)` ba lần liên tiếp và đều timeout. Tổng thời gian chờ thực tế có thể bao nhiêu?

Khoảng 300 ms cộng overhead, dù mỗi get dùng timeout 100 ms. Để hạn chế cả workflow, dùng một deadline chung.

**Câu 6.** Vì sao `ThreadPoolTaskExecutor.getQueueSize() == 0` không có nghĩa không có người chờ?

Vì task có thể đang chạy lâu, caller đang đợi Future hoặc executor dùng `SynchronousQueue` vốn không giữ phần tử. Queue size không phải caller wait hay runtime.

## 15. Checklist production

Trước khi đưa hệ thống vào vận hành, hãy xác nhận:

- Có định nghĩa rõ mốc submit, worker-start, worker-finish, caller-get-start/end chưa?
- Các timestamp đo duration trong cùng process bằng monotonic clock?
- Có histogram p50/p95/p99 queue wait, runtime, caller wait và submit-to-completion?
- Có tách accepted/rejected/cancelled/expired task khỏi nhóm đã thực thi?
- Có hiểu đúng `corePoolSize`, `maxPoolSize`, `queueCapacity`, rejection policy và pool thực sự được tiêm?
- Nếu dùng `@Async`, lời gọi có đi qua proxy thay vì self-invocation?
- Có deadline chung và tính remaining budget trước mỗi `get(timeout)`?
- Có timeout riêng cho DB/HTTP/lock trong task không?
- Nếu caller timeout, task có nên chạy tiếp hay cố hủy? Ai giữ trạng thái cuối?
- Với side effect, có idempotency/retry/durable job semantics?
- Caller có đang block event-loop hoặc request worker không cần thiết?
- Test có dùng `CountDownLatch`/phương pháp điều phối để phân biệt queued và running, tránh phụ thuộc `Thread.sleep()`?

## 16. Tóm tắt

**Queue wait** là khoảng từ khi submit đến khi worker bắt đầu. **Execution time** là khoảng worker thực thi code được đo. **Caller wait** là khoảng thread gọi đợi Future; nó có thể bao trùm cả queue+runtime, chỉ một phần, gần 0 hoặc kết thúc bằng timeout trong khi task chưa chạy.

Trong Spring, đo ba khoảng thời gian tại ba boundary khác nhau và quan sát cùng với executor metrics. Đặc biệt, **hết thời gian caller chờ không đồng nghĩa task hết hạn hoặc bị hủy**. Thiết kế deadline, timeout downstream, cancellation và idempotency dựa trên yêu cầu nghiệp vụ, không chỉ dựa vào `Future.get(timeout)`.

### Tài liệu tham khảo

- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Spring Framework 6.2.1 — ThreadPoolTaskExecutor API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework 6.2.1 — AsyncTaskExecutor API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/core/task/AsyncTaskExecutor.html)
- [Java 21 — Future API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html)
- [Java 21 — CompletableFuture API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java 21 — System.nanoTime](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#nanoTime())

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="timeout-cancellation-002">Future.get(timeout) không tự dừng công việc nền</a>

<details>
<summary>Xem chi tiết</summary>

# Future.get(timeout) không tự dừng công việc nền

Trong ứng dụng Spring Framework, một request có thể gửi tác vụ tạo báo cáo, gọi API ngoài hoặc xử lý dữ liệu vào `ExecutorService`/`ThreadPoolTaskExecutor`, rồi chờ kết quả bằng `Future.get(timeout, unit)`. Nếu hết thời gian chờ, Java ném `TimeoutException`. **Điều đó chỉ có nghĩa thread đang chờ chưa nhận được kết quả trong khoảng thời gian cho phép; nó không có nghĩa task đã bị hủy, worker đã dừng, hoặc tác động nghiệp vụ đã được hoàn tác.**

Đây là phân biệt cốt lõi trong nhóm **Hết hạn và hủy** của module **Spring Framework – concurrency**. Cần hiểu độc lập ba câu hỏi:

1. **Caller còn chờ hay đã ngừng chờ?** `Future.get(timeout)` kiểm soát thời gian chặn của caller.
2. **Future đang ở trạng thái gì?** Future có thể chưa hoàn tất, hoàn tất bình thường, hoàn tất lỗi hoặc bị hủy.
3. **Công việc thật trên worker đã dừng chưa?** Việc dừng cần cơ chế cancellation/interruption hợp tác và timeout ở từng tài nguyên; không được suy từ exception của caller.

Một hệ thống có thể trả response “hết thời gian chờ” trong khi worker vẫn gọi database, ghi file hoặc gửi email. Nếu client retry mà không có idempotency, tác động có thể xảy ra **hai lần**.

## 1. Mô hình thời gian: caller hết hạn trước, worker tiếp tục

```text
Thời gian ──────────────────────────────────────────────────────────────>

HTTP caller / main thread
  | submit(task)
  |--------------- Future<?> ----------------|
  | get(100, MILLISECONDS)
  |          └──── 100 ms ────> TimeoutException
  |                              |
  |                              └─ Caller tiếp tục / trả HTTP response
  |
Worker thread
  |        [chờ queue] [bắt đầu] [đang xử lý I/O ..................]
  |                                                        [ghi DB]
  |                                                          [xong]
  |
  +-- Timeout của caller KHÔNG tự gửi interrupt cho worker ----------+
```

Hai dòng thời gian **độc lập**:

- `get(timeout)` kết thúc việc **chờ kết quả** khi hết hạn.
- Task đã giao cho executor có thể vẫn **QUEUED** hoặc **RUNNING**.
- Worker có thể hoàn thành bình thường ngay sau lúc caller nhận `TimeoutException`.
- Task có thể thất bại sau timeout và không ai quan sát exception nếu hệ thống bỏ quên handle.
- Một task có thể đã tác động ra ngoài rồi, dù caller cho rằng request “thất bại”.

Vì vậy, phản hồi chính xác nên là **“đã hết thời gian chờ kết quả”**, không tự động là **“đã hủy công việc”** hoặc **“giao dịch không xảy ra”**.

## 2. Chính xác Future.get(timeout) bảo đảm điều gì?

API JDK:

```java
V get(long timeout, TimeUnit unit)
        throws InterruptedException,
               ExecutionException,
               TimeoutException;
```

Trên một `Future<V>`, lời gọi này **chờ tối đa khoảng thời gian được yêu cầu** để phép tính hoàn tất. Các khả năng:

| Kết quả từ `get(timeout)` | Ý nghĩa chắc chắn | Điều không nên suy ra |
| --- | --- | --- |
| Trả về giá trị `V` | Tính toán hoàn thành bình thường theo hợp đồng của Future | Mọi side effect bên ngoài đã durable nếu code chưa bảo đảm |
| `TimeoutException` | Không nhận được kết quả trong thời gian chờ | Task đã hủy/đã dừng/đã rollback |
| `ExecutionException` | Tác vụ hoàn tất ngoại lệ; exception gốc nằm trong `getCause()` | Task chưa từng thực hiện side effect |
| `InterruptedException` | **Thread đang chờ `get`** bị interrupt khi chờ | Worker đã tự động bị interrupt hoặc dừng |
| `CancellationException` | Future đã bị hủy theo trạng thái Future | Worker đã chắc chắn ngừng ngay tức khắc |

`Future.get()` không có timeout chờ cho đến khi kết quả sẵn có hoặc đường lỗi/interruption xảy ra. `Future.get(timeout, unit)` thay đổi **giới hạn đợi**, không thêm khả năng “kill task”.

**Lưu ý về timeout:** thời gian được tính cho bước chờ trên caller, không phải deadline thực thi của nghiệp vụ. Nếu task chờ trong executor queue 90 ms rồi bắt đầu chạy, `get(100ms)` có thể chỉ còn rất ít thời gian để thấy kết quả. Nếu task đã chạy 10 phút trước khi caller gọi `get(100ms)`, lệnh này chỉ chờ thêm tối đa khoảng 100 ms tại thời điểm gọi.

`get` là thao tác blocking. Nếu bị gọi ngay trên HTTP request thread, request thread có thể bị chiếm suốt khoảng timeout. Hãy chọn mô hình trả `CompletableFuture` trực tiếp, polling job status hoặc async HTTP response khi phù hợp.

### Ba trạng thái không giống nhau

```text
Caller wait state:  WAITING --timeout--> NOT_WAITING
Future state:       PENDING -----------→ PENDING / SUCCESS / FAILURE / CANCELLED
Worker state:       QUEUED ------------→ RUNNING --------→ TERMINATED
```

**Không có mũi tên tự động từ `TimeoutException` sang `CANCELLED`**. Chỉ có code chủ động hủy hoặc chính sách hệ thống khác mới có thể phát tín hiệu hủy, và ngay cả khi yêu cầu hủy đã được ghi nhận thì worker có thể không dừng tức thì.

## 3. Ví dụ Java 17 chạy độc lập: timeout rồi task vẫn hoàn thành

Để chứng minh mà không phụ thuộc tốc độ máy hay thread scheduler, hãy dùng `CountDownLatch`. Worker bắt đầu rồi đợi một latch; caller đợi `Future.get(50, MILLISECONDS)`. Vì latch **chưa được mở**, worker không thể hoàn tất trước timeout.

Tạo tệp **TimeoutDoesNotStopDemo.java**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class TimeoutDoesNotStopDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch allowFinish = new CountDownLatch(1);
        AtomicBoolean businessEffect = new AtomicBoolean(false);

        try {
            Future<String> future = pool.submit(() -> {
                started.countDown();
                allowFinish.await(); // Cố ý chưa cho worker hoàn tất
                businessEffect.set(true);
                return "report-saved";
            });

            started.await(); // Bảo đảm task đã bắt đầu

            try {
                future.get(50, TimeUnit.MILLISECONDS);
                throw new AssertionError("Task should still be waiting");
            } catch (TimeoutException expected) {
                System.out.println("wait timed out");
            }

            System.out.println("future done after timeout: "
                    + future.isDone());
            System.out.println("effect after timeout: "
                    + businessEffect.get());

            allowFinish.countDown(); // Worker giờ mới được hoàn tất
            System.out.println("later result: " + future.get());
            System.out.println("effect after release: "
                    + businessEffect.get());
        } finally {
            allowFinish.countDown();
            pool.shutdownNow();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac TimeoutDoesNotStopDemo.java
java TimeoutDoesNotStopDemo
```

**Stdout xác định khi chương trình chạy bình thường:**

```text
wait timed out
future done after timeout: false
effect after timeout: false
later result: report-saved
effect after release: true
```

Giải thích từng bước:

1. `pool.submit` **chấp nhận** callable và trả về `Future<String>`, không đợi công việc hoàn tất.
2. `started.await()` chứng minh worker đã vào task; worker chờ `allowFinish.await()`. Đây là điểm đồng bộ có chủ đích, không phải giả định “ngủ 50 ms là đủ”.
3. `future.get(50, MILLISECONDS)` không có cách nhận result khi latch đang đóng, nên ném `TimeoutException`.
4. `Future.isDone()` vẫn `false` và cờ nghiệp vụ vẫn `false` vì worker chưa tới phần ghi kết quả.
5. Caller mở latch; **cùng task trước đó tiếp tục xử lý**, đặt cờ rồi trả `report-saved`.
6. `future.get()` lần hai nhận kết quả thành công. Không có task thứ hai nào được tạo bởi lần gọi `get` này.

**Không được suy từ demo rằng cứ timeout là `isDone() == false`.** Trong hệ thống thực, worker có thể hoàn tất **ngay sau** thời điểm `get` ném `TimeoutException` nhưng **trước** khi caller đọc `isDone()`, nên lần đọc có thể là `true`. Latch trong ví dụ cố ý loại bỏ race để cho thấy cơ chế.

Cờ `businessEffect` chỉ mô phỏng tác động trong bộ nhớ, không chứng minh database commit hay hệ thống bên ngoài đã thực hiện. Bài học là **quyền tiếp tục chạy của worker không biến mất vì caller dừng đợi**.

## 4. `get(timeout)`, `cancel(...)` và interrupt là ba thao tác khác nhau

Sau khi bắt `TimeoutException`, code có thể quyết định **không hủy** hoặc **yêu cầu hủy**. Lựa chọn này thuộc về chính sách nghiệp vụ.

```java
try {
    return future.get(200, TimeUnit.MILLISECONDS);
} catch (TimeoutException e) {
    // Không có cancel tự động tại đây.
    // Nếu nghiệp vụ cho phép hủy:
    boolean requested = future.cancel(true);
    throw e;
}
```

Đây là **fragment**, không phải class độc lập: `future` phải được khởi tạo và method/caller phải khai báo hoặc xử lý exception. Quan trọng là dòng `future.cancel(true)` **do lập trình viên viết thêm**, không phải hành vi nằm bên trong `get(timeout)`.

### 4.1. `cancel(false)`

Đối với `FutureTask`/nhiều task do `ExecutorService.submit` tạo, `cancel(false)` có thể đánh dấu Future là **cancelled** mà **không phát interrupt** tới worker. Nếu task đang chạy, code có thể vẫn tiếp tục side effect dù người gọi nhìn thấy `future.isCancelled() == true`. Không được mô tả `cancel(false)` như một lệnh “dừng chắc chắn nếu chưa chạy và đợi chạy xong nếu đã chạy”.

### 4.2. `cancel(true)`

Với `FutureTask` chạy trong `ThreadPoolExecutor` thông thường, `cancel(true)` **cố yêu cầu interrupt** thread đang thực hiện task. Nhưng:

- Task có thể đã hoàn tất trước khi gọi cancel; khi đó `cancel` trả `false`.
- Nếu task đang chờ queue, cancellation có thể làm nó không cần chạy; việc xóa phần tử đã cancel khỏi queue phụ thuộc executor và chính sách cleanup.
- Nếu task đang chạy, interrupt là **tín hiệu hợp tác**, không phải cưỡng bức dừng JVM thread.
- `Thread.sleep`, `Object.wait`, `CountDownLatch.await` và nhiều blocking method của JDK có thể ném `InterruptedException`.
- Tác vụ CPU vòng lặp không kiểm tra trạng thái interrupt có thể tiếp tục chạy.
- Một số I/O/SDK không phản ứng với interrupt hoặc đã gửi tác động ra ngoài trước khi tín hiệu đến.
- `isCancelled() == true` và `isDone() == true` phản ánh trạng thái Future; **không phải bằng chứng worker vật lý đã chết hoặc mọi side effect đã hoàn tác**.

### 4.3. `Thread.interrupt()` không phải `Thread.stop()`

`Thread.interrupt()` đặt cờ interrupt hoặc khiến một blocking operation hỗ trợ interrupt ném `InterruptedException`. Nó không cưỡng chế hủy thread và không rollback transaction ở hệ thống khác.

`Thread.stop()` đã bị deprecated vì không an toàn; không sử dụng để “chữa timeout”. Cách đúng là **cooperative cancellation**: code định kỳ kiểm tra cờ, cấu hình timeout ở các API blocking, giải phóng tài nguyên trong `finally` và không bắt rồi nuốt interrupt.

## 5. Java 17 runnable: cancel(true) chỉ phát yêu cầu interrupt có hợp tác

Ví dụ này dùng một task cố ý chờ trên `CountDownLatch`, một operation **interruptible**. Chỉ khi worker nhận interrupt và chạy nhánh `catch`, ta mới có bằng chứng task đã quan sát tín hiệu.

Lưu thành **CooperativeCancelDemo.java**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CooperativeCancelDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch observedInterrupt = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);

        try {
            Future<?> future = pool.submit(() -> {
                started.countDown();
                try {
                    neverReleased.await();
                } catch (InterruptedException ex) {
                    observedInterrupt.countDown();
                    Thread.currentThread().interrupt();
                }
            });

            started.await();
            boolean requested = future.cancel(true);
            observedInterrupt.await();

            System.out.println("cancel requested: " + requested);
            System.out.println("future cancelled: "
                    + future.isCancelled());
            System.out.println("worker observed interrupt: true");
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac CooperativeCancelDemo.java
java CooperativeCancelDemo
```

**Stdout trong điều kiện demo bình thường:**

```text
cancel requested: true
future cancelled: true
worker observed interrupt: true
```

`started.await()` bảo đảm callable đã vào phần chờ trước khi main gọi `cancel(true)`. Trong executor mặc định này, `FutureTask` gửi interrupt tới runner. Khi `CountDownLatch.await()` bị interrupt, worker chạy `catch` và đánh dấu `observedInterrupt`; main chờ latch đó trước khi in. Vì vậy **không in “observed” chỉ dựa vào `isCancelled()`**.

Lưu ý demo **cố ý hợp tác với interrupt**. Nếu thay `neverReleased.await()` bằng một lời gọi thư viện không hỗ trợ interrupt, hoặc một vòng CPU vô hạn bỏ qua cờ interrupt, kết quả “worker observed interrupt” không còn được bảo đảm. Trong production nên tránh chờ vô hạn khi kiểm thử và thêm deadline/timeout test phù hợp.

### 5.1. Xử lý InterruptedException thế nào?

Code hay gặp:

```java
try {
    blockingOperation();
} catch (InterruptedException ex) {
    // Bắt rồi bỏ qua là nguy hiểm.
}
```

Nếu có thể truyền exception lên, cho method khai báo `throws InterruptedException`. Nếu phải bắt và không thể ném lại exception cùng loại, thường cần:

```java
catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Dọn tài nguyên / ghi nhận trạng thái hủy phù hợp.
    return;
}
```

Đây là fragment nằm trong một method có return type phù hợp. **Khôi phục cờ interrupt** giúp tầng trên nhận thấy tín hiệu đã có; sau đó không tiếp tục chạy những side effect không cần thiết. Không biến mọi interrupt thành “nghiệp vụ thất bại vĩnh viễn” nếu đó là yêu cầu shutdown hợp lệ.

## 6. `CompletableFuture`: timeout/cancel còn có giới hạn riêng

`CompletableFuture` vừa triển khai `Future`, vừa là `CompletionStage`. Nó có các API timeout tiện dụng từ Java 9 trở lên, nhưng **không tự dừng supplier/worker bên dưới**.

| Cách dùng | Tác động lên việc chờ/trạng thái Future | Có tự interrupt worker không? |
| --- | --- | --- |
| `cf.get(100, MILLISECONDS)` | Caller chờ tối đa 100 ms; có thể ném `TimeoutException` | **Không** |
| `cf.orTimeout(100, MILLISECONDS)` | Sắp xếp để **chính cf** hoàn tất ngoại lệ `TimeoutException` nếu chưa xong khi tới hạn | **Không** |
| `cf.completeOnTimeout(fallback, 100, MILLISECONDS)` | Sắp xếp để **chính cf** hoàn tất bình thường bằng fallback nếu chưa xong | **Không** |
| `cf.cancel(true)` | Hoàn tất `cf` theo trạng thái cancellation nếu còn có thể | Với implementation `CompletableFuture` của JDK: **không** |
| `futureTask.cancel(true)` | Yêu cầu hủy FutureTask; có thể gửi interrupt runner | **Có thể gửi**, nhưng dừng là best effort |

Javadoc `CompletableFuture.cancel(boolean)` nói rõ `mayInterruptIfRunning` **không có tác dụng trong implementation này**, vì `CompletableFuture` không dùng interrupt để điều khiển xử lý. Đây là khác biệt quan trọng so với `FutureTask` từ `ExecutorService.submit(...)`.

### 6.1. `orTimeout` là “hết hạn kết quả”, không phải “dừng supplier”

```java
CompletableFuture<String> cf =
        CompletableFuture.supplyAsync(() -> callSlowService(), executor);

cf.orTimeout(100, TimeUnit.MILLISECONDS);
```

Đây là fragment; `callSlowService` và `executor` phải tồn tại. Nếu supplier chưa xong sau 100 ms, `cf` được hoàn tất ngoại lệ vì timeout. **Supplier bên dưới vẫn có thể chạy và gây tác động ngoài**. Khi nó về kết quả muộn, nó không thể thay giá trị `cf` đã hoàn tất ngoại lệ.

Hệ quả: `cf.get()` sau đó có thể ném `ExecutionException` với cause là `TimeoutException`; khác với việc `get(100, MILLISECONDS)` trực tiếp ném `TimeoutException` do caller chờ quá hạn.

### 6.2. `completeOnTimeout` có thể che kết quả muộn

```java
CompletableFuture<String> cf =
        CompletableFuture.supplyAsync(() -> callSlowService(), executor)
                .completeOnTimeout("cached-fallback", 100,
                        TimeUnit.MILLISECONDS);
```

Khi hết hạn, caller có thể thấy `"cached-fallback"` như một **giá trị kết quả thành công của Future**, nhưng supplier gốc chưa chắc dừng. Nếu supplier đang ghi dữ liệu, kết quả muộn vẫn có thể gây side effect. Do đó **không dùng fallback completion làm bằng chứng nghiệp vụ gốc đã được hủy**.

Một fallback hợp lệ phải được thiết kế theo domain: trả dữ liệu cache cũ cho read-only query có thể chấp nhận; “thu tiền thất bại nên thử lại” khi provider chưa xác định trạng thái là nguy hiểm.

### 6.3. Ví dụ mini về sự khác nhau của hai timeout

```java
CompletableFuture<String> pending = new CompletableFuture<>();
try {
    pending.get(10, TimeUnit.MILLISECONDS);
} catch (TimeoutException ex) {
    System.out.println("caller timed out");
}
System.out.println("still pending: " + !pending.isDone());
```

Đây là **fragment** yêu cầu imports JDK. Vì `pending` không được hoàn tất bởi thread nào, dòng cuối là `still pending: true`. Nếu thay bằng `pending.orTimeout(10, TimeUnit.MILLISECONDS)` rồi đợi stage, chính future đó sẽ chuyển sang trạng thái hoàn tất **ngoại lệ**, nhưng không có worker nào bị ngắt vì ví dụ không hề có worker.

**Quy tắc nhớ:** `get(timeout)` là **timeout của phép đợi**, `orTimeout` là **timeout của kết quả Future**, cả hai khác với **timeout của operation thật**.

## 7. Bốn lớp timeout trong ứng dụng Spring

Đừng dùng một giá trị `1000 ms` rồi nghĩ nó kiểm soát được mọi thứ. Thường có ít nhất bốn lớp:

| Lớp | Ví dụ | Tài nguyên chịu tác động | Sau timeout điều gì có thể còn chạy? |
| --- | --- | --- | --- |
| HTTP client timeout | Client chờ response quá hạn | Phía client/người gọi | Server vẫn có thể xử lý request/job |
| HTTP async request timeout | Servlet async/MVC `DeferredResult`/`WebAsyncTask` | Vòng đời HTTP response | Work nền có thể tiếp tục nếu không nối cancellation |
| Future wait timeout | `future.get(200, MILLISECONDS)` | Thread đang chờ | Future task vẫn queued/running |
| Dependency operation timeout | JDBC query timeout, HTTP connect/read/response timeout | Lời gọi I/O cụ thể | Có thể còn logic cleanup/retry; tác động đã gửi có thể vẫn xảy ra |
| Job business deadline | Deadline kiểm tra trong worker/job store | Trạng thái và chính sách nghiệp vụ | Bước đã bắt đầu có thể cần reconciliation/compensation |

**Chúng không tự động kế thừa nhau.** HTTP client đóng kết nối không bảo đảm Spring chủ động hủy worker; `Future.get(timeout)` cũng không tự gắn timeout cho JDBC query hay HTTP client bên trong worker.

### 7.1. Nếu có HTTP request deadline

Một API được khách hàng chờ tối đa 2 giây không có nghĩa báo cáo 30 giây phải bị giết ở giây thứ hai. Có hai thiết kế khác nhau:

- **Xử lý đồng bộ có deadline:** giới hạn bước I/O, trả lỗi timeout theo hợp đồng, quyết định có yêu cầu hủy và giải phóng tài nguyên hay không.
- **Xử lý nền:** xác nhận job được tiếp nhận, trả HTTP **202 Accepted + job ID** và cung cấp endpoint trạng thái. HTTP response kết thúc nhưng job có thể tiếp tục; không phải sự cố.

Điểm bắt buộc là **truyền thông chính xác cho client**, vì “hết thời gian chờ response” khác “không có job”, và “job đã nhận” khác “job đã hoàn thành”.

### 7.2. Transaction timeout khác Future timeout

Với Spring `@Transactional`, transaction thường gắn với thread thực thi và điều khiển bởi transaction manager. Một caller đợi `Future.get(timeout)` bị timeout **không tự rollback transaction đang chạy ở một worker khác**. Ngược lại, transaction timeout có thể khiến hoạt động DB thất bại tại một điểm kiểm tra nhất định, nhưng không giải quyết mọi tác động lên dịch vụ ngoài.

Đừng dựa vào interrupt để đảo một giao dịch đã commit. Với nghiệp vụ nhiều hệ thống, cần idempotency, transaction boundary và compensation/reconciliation riêng.

## 8. Spring Boot 3.x: minh họa endpoint hết hạn đợi nhưng worker tiếp tục

Ví dụ sau dùng **Java 17+**, **Spring Boot 3.x / Spring Framework 6.x**, dependency `spring-boot-starter-web`. Nó minh họa rõ:

- `ThreadPoolTaskExecutor` nhận tác vụ nền.
- Controller đợi kết quả tối đa **100 ms**.
- Nếu controller hết hạn đợi, nó trả **202 Accepted** kèm ID để tra cứu, **không gọi `cancel`**.
- Worker được mô phỏng mất **600 ms**, tiếp tục cập nhật trạng thái trong `ConcurrentHashMap`.
- Đây chỉ là **demo in-memory**, không có job store bền vững hay bảo đảm xử lý sau crash.

Đặt toàn bộ code vào **src/main/java/com/example/demo/TimeoutApplication.java**:

```java
package com.example.demo;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableAsync
public class TimeoutApplication {
    public static void main(String[] args) {
        SpringApplication.run(TimeoutApplication.class, args);
    }

    @Bean("reportExecutor")
    ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("report-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}

@Service
class JobStates {
    private final ConcurrentMap<String, String> states =
            new ConcurrentHashMap<>();

    void set(String id, String state) {
        states.put(id, state);
    }

    String get(String id) {
        return states.get(id);
    }

    void remove(String id) {
        states.remove(id);
    }
}

@Service
class ReportWorker {
    private final JobStates jobs;

    ReportWorker(JobStates jobs) {
        this.jobs = jobs;
    }

    @Async("reportExecutor")
    public CompletableFuture<String> generate(String jobId) {
        jobs.set(jobId, "RUNNING");
        try {
            Thread.sleep(600); // Chỉ mô phỏng I/O blocking.
            jobs.set(jobId, "SUCCEEDED");
            return CompletableFuture.completedFuture("report:" + jobId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            jobs.set(jobId, "FAILED");
            return CompletableFuture.failedFuture(ex);
        }
    }
}

@RestController
class JobController {
    private final ReportWorker worker;
    private final JobStates jobs;

    JobController(ReportWorker worker, JobStates jobs) {
        this.worker = worker;
        this.jobs = jobs;
    }

    @PostMapping("/jobs")
    public ResponseEntity<?> submit() {
        String id = UUID.randomUUID().toString();
        jobs.set(id, "QUEUED");

        final CompletableFuture<String> result;
        try {
            result = worker.generate(id);
        } catch (TaskRejectedException rejected) {
            jobs.remove(id);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "executor_overloaded"));
        }

        try {
            String report = result.get(100, TimeUnit.MILLISECONDS);
            return ResponseEntity.ok(
                    Map.of("jobId", id, "state", "SUCCEEDED",
                            "result", report));
        } catch (TimeoutException timeout) {
            // Không gọi result.cancel(): worker vẫn có thể chạy.
            return ResponseEntity.accepted()
                    .location(URI.create("/jobs/" + id))
                    .body(Map.of("jobId", id, "state", "PROCESSING",
                            "statusUrl", "/jobs/" + id));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("jobId", id, "error", "caller_interrupted"));
        } catch (ExecutionException failed) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("jobId", id, "error", "worker_failed"));
        }
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> status(@PathVariable String id) {
        String state = jobs.get(id);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("jobId", id, "state", state));
    }
}
```

Chạy bằng công cụ build Spring Boot của dự án:

```bash
./gradlew bootRun
curl -i -X POST http://localhost:8080/jobs
```

Trên Windows có thể dùng `gradlew.bat bootRun`. Vì worker giả lập ngủ 600 ms, còn caller đợi 100 ms, khi pool nhận task và JVM/scheduler hoạt động bình thường, endpoint thường trả:

```http
HTTP/1.1 202 Accepted
Location: /jobs/<id>

{"jobId":"<id>","state":"PROCESSING","statusUrl":"/jobs/<id>"}
```

**`<id>` là ký hiệu cho một UUID mới thực tế**, không phải chuỗi response cố định. Với overload, có thể trả 503; với tác vụ hoàn tất đủ nhanh trước thời gian chờ có thể trả 200. Sau khi dùng job ID thật gọi `GET /jobs/{id}`, trạng thái có thể là `QUEUED`, `RUNNING`, rồi `SUCCEEDED` khi worker kết thúc.

Ví dụ không tự bảo đảm task hoàn tất sau 600 ms tuyệt đối: `Thread.sleep(600)` chỉ chặn worker ít nhất khoảng thời gian đó theo điều kiện thực thi; worker còn có thể phải chờ queue và scheduling. **Đó chính là lý do API không nên hứa thời điểm hoàn thành chỉ từ timeout của caller.**

### 8.1. Các dòng code then chốt

```java
String report = result.get(100, TimeUnit.MILLISECONDS);
```

Dòng này **đợi Future trên controller thread**, nhưng không hủy khi đợi hết hạn.

```java
catch (TimeoutException timeout) {
    return ResponseEntity.accepted()
            .location(URI.create("/jobs/" + id))
            .body(Map.of("jobId", id, "state", "PROCESSING"));
}
```

Đây là fragment rút gọn. Nó trả thông điệp chính xác rằng job vẫn được xử lý, không nói “đã thất bại”. Worker đã được gửi vào pool sẽ chạy độc lập cho tới khi hoàn tất hoặc gặp lỗi khác.

```java
@Async("reportExecutor")
public CompletableFuture<String> generate(String jobId) {
    // ...
}
```

`@Async` chỉ có hiệu lực khi lời gọi đi qua **Spring proxy** (ở ví dụ, controller gọi bean `ReportWorker` khác). Nếu code trong chính `ReportWorker` gọi `this.generate(id)`, self-invocation thường bỏ qua proxy trong mode mặc định; khi đó không còn ranh giới executor như thiết kế.

### 8.2. Giới hạn bảo đảm của ví dụ Spring

Đây là bài demo, không phải nền tảng job production:

- `JobStates` là `ConcurrentHashMap` **trong RAM**, chỉ dùng trên một instance. Restart/crash làm mất trạng thái và task chưa hoàn tất; multi-replica không chia sẻ job.
- HTTP 202 trong demo chỉ xác nhận task được chấp nhận vào executor theo policy hiện tại, **không cam kết durable delivery**.
- Nếu request thread bị interrupt thay vì timeout, trạng thái job vẫn có thể tiếp tục được worker cập nhật. Cần chính sách cancel rõ ràng nếu muốn hủy.
- Worker chỉ mô phỏng việc xử lý bằng `sleep`; `SUCCEEDED` là thành công của demo chứ không chứng minh file báo cáo đã được lưu.
- `ThreadPoolTaskExecutor` cần sizing core/max/queue/rejection phù hợp, và nên quan sát số task accepted/completed/failed/rejected.
- Trong API thực, polling trạng thái cần authorization, retention/TTL, idempotency key và phương án phục hồi job stuck.

**Một ngoại lệ khác:** Một custom `RejectedExecutionHandler` như `CallerRunsPolicy` có thể khiến task chạy ngay trên caller khi pool quá tải. `@Async` không bảo đảm request thread luôn được trả lại nhanh trong mọi cấu hình; hãy hiểu policy executor đang sử dụng.

## 9. Nếu thực sự muốn dừng tác vụ sau timeout

Việc dừng yêu cầu thiết kế ở **nhiều cấp độ**, không chỉ thêm `get(timeout)`:

1. **Xác định có được hủy không.** Một thao tác gửi email best-effort có thể hủy trước khi gửi, nhưng xử lý thanh toán đã đến provider có thể phải đối soát trước khi đánh dấu hủy.
2. **Giữ handle tới task có thể yêu cầu cancel.** Với `ExecutorService.submit` là `Future` có thể gọi `cancel(true)`; với `CompletableFuture`, `cancel(true)` không tự interrupt supplier.
3. **Task hợp tác.** Kiểm tra cờ interrupt/deadline ở những điểm an toàn, thoát sớm khi phù hợp và cleanup trong `finally`.
4. **Đặt timeout cho từng blocking operation.** HTTP client connect/read timeout, JDBC query timeout, lock acquisition timeout hoặc các API theo deadline thực tế.
5. **Giảm rủi ro side effect một phần.** Ghi nhận idempotency key, transaction boundary, trạng thái trung gian và chương trình reconciliation.
6. **Phân biệt requested cancellation với confirmed stop.** Có thể có `CANCEL_REQUESTED` rồi `CANCELLED` chỉ khi worker xác nhận đã dừng ở điểm an toàn, hoặc `COMPLETED_AFTER_CANCEL_REQUEST` nếu không thể ngăn side effect.
7. **Theo dõi job còn chạy sau caller timeout.** Cần metric, log correlation ID, job store và công cụ vận hành.

### 9.1. Mẫu cooperative deadline trong task CPU-bound

Đây là một **fragment** minh họa sử dụng deadline dựa trên `System.nanoTime()`:

```java
long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);

for (WorkItem item : items) {
    if (Thread.currentThread().isInterrupted()) {
        throw new CancellationException("Interrupted");
    }
    if (System.nanoTime() - deadline >= 0) {
        throw new TimeoutException("Job deadline reached");
    }
    processOneItem(item);
}
```

Đoạn này không phải chương trình biên dịch độc lập: `WorkItem`, `items` và `processOneItem` cần được khai báo; method phải xử lý/kê khai `TimeoutException` và import các lớp `TimeUnit`, `CancellationException`. `System.nanoTime()` thích hợp đo **khoảng thời gian** hơn đồng hồ wall-clock khi tính deadline.

**Caveat:** kiểm tra deadline giữa các item chỉ có hiệu lực khi `processOneItem` trả quyền điều khiển. Nếu một lời gọi JDBC/HTTP blocking mắc kẹt, deadline trong vòng lặp không tự giải phóng worker. Phải đặt timeout ở thư viện I/O bên dưới.

### 9.2. Nếu task đang gọi hệ thống ngoài

Giả sử worker gọi cổng thanh toán, caller `get(1, SECONDS)` timeout. **Không kết luận giao dịch thất bại**. Có thể provider đã thực hiện request nhưng response bị mất. Nếu retry tạo transaction mới, có rủi ro thu tiền hai lần.

Thiết kế an toàn hơn:

- Dùng **idempotency key** ổn định cho yêu cầu logic.
- Query trạng thái cuối từ provider bằng transaction/reference ID.
- Chỉ đánh dấu `FAILED`/`SUCCEEDED` khi có chứng cứ theo hợp đồng.
- Nếu trạng thái không chắc chắn, ghi `UNKNOWN` hoặc `RECONCILING` thay vì đoán.
- Hạn chế retry, thêm backoff/jitter và tránh lặp side effect không idempotent.

Đây là lý do cancellation là **quyết định nghiệp vụ có tác động**, không phải một lệnh kỹ thuật tùy tiện trong mọi `catch (TimeoutException)`.

## 10. Hết thời gian chờ không đồng nghĩa task đã hỏng

Một task có thể rơi vào nhiều trạng thái khi caller dừng chờ:

| Lúc caller timeout | Điều có thể xảy ra tiếp | Hành động theo nhu cầu |
| --- | --- | --- |
| Task còn trong queue | Worker có thể lấy và chạy muộn | Poll status, deadline check trước khi bắt đầu |
| Task đang CPU-bound | Tiếp tục tính toán | Cooperative interrupt/deadline |
| Task đang đợi DB connection | Tiếp tục chờ tới khi có connection/timeout | Đặt timeout cho acquire/query; giới hạn concurrency |
| Task đang chờ API ngoài | Request có thể vẫn đi đến provider | Timeout client phù hợp, idempotency/reconciliation |
| Task vừa hoàn tất bình thường | `Future.isDone()` có thể chuyển true ngay sau timeout | Đọc lại result nếu contract cho phép, tránh tạo job trùng |
| Task đã lỗi trong worker | Future có thể hoàn tất ngoại lệ sau khi caller bỏ chờ | Quan sát Future callback/metrics/status |
| Task đã được yêu cầu cancel | Future có thể cancelled trước khi worker thực sự dừng | Theo dõi xác nhận từ worker |
| Application đang shutdown | Queue/task có thể bị gián đoạn theo lifecycle | Graceful shutdown và durable job processing |

Đừng tự gán trạng thái job `FAILED` khi chỉ có `TimeoutException` của **caller**; nếu cần một trạng thái, hãy dùng `WAIT_TIMEOUT` cho request/wait attempt, vẫn giữ job là `QUEUED`/`RUNNING`/`UNKNOWN` tùy sự thật.

## 11. Race condition khi cancel, kiểm tra trạng thái và retry

### 11.1. Race giữa timeout và completion

```text
T1: future.get(timeout) ném TimeoutException
T2: worker hoàn tất thành công
T1: kiểm tra future.isDone() -> true
```

Không mâu thuẫn: timeout chỉ mô tả tình trạng chưa có kết quả **trước mốc chờ**, còn kết quả có thể đến ngay sau đó. `isDone()` là một snapshot tại thời điểm đọc, không phải chứng cứ worker ở trạng thái đó lúc `get` vừa ném exception.

### 11.2. Race giữa cancel(true) và task hoàn tất

`Future.cancel(true)` trả `false` nếu task đã hoàn tất hoặc không thể hủy theo state machine. Nó có thể trả `true` trước khi worker kịp xử lý interrupt. Do đó, **giá trị return chỉ là kết quả yêu cầu cancellation ở cấp Future**, không phải thời điểm chính xác side effect đã dừng.

### 11.3. Retry sau HTTP timeout

Client không nhận response có thể gửi lại cùng request trong khi server đã nhận task lần đầu. Nếu thiếu request ID/idempotency key, cả hai job có thể chạy và cùng tạo side effect. Hệ thống nên lưu key cùng request hash, có unique constraint và trả lại job ID cũ nếu payload trùng.

### 11.4. Task hoàn tất sau khi request kết thúc

HTTP request-scoped objects, security context, MDC hoặc transaction của request thread có thể **không còn hiệu lực** khi worker tiếp tục. Không giữ tham chiếu mutable tới request/response để dùng sau timeout; truyền dữ liệu tối thiểu và có kiểm soát vào job, mở transaction mới khi cần và cleanup thread-local ở worker pool.

## 12. Phân biệt các API timeout/cancellation dễ nhầm

| API | Tác dụng chính | Điểm cần nhớ |
| --- | --- | --- |
| `Future.get(timeout, unit)` | Giới hạn thời gian chờ của **caller** | Không tự gọi cancel |
| `Future.cancel(true)` | Yêu cầu cancellation, có thể interrupt runner tùy implementation | Không bảo đảm ngừng side effect |
| `CompletableFuture.orTimeout(...)` | Đặt thời hạn kết quả của **CompletableFuture** | Không interrupt supplier |
| `CompletableFuture.completeOnTimeout(...)` | Hoàn tất CF bằng fallback nếu chưa xong | Work bên dưới vẫn có thể tiếp tục |
| `ExecutorService.invokeAll(tasks, timeout, unit)` | Chờ batch trong thời hạn; API **có cơ chế cancel các task chưa hoàn tất** khi hết hạn | Không giống `Future.get(timeout)`, nhưng cancellation vẫn best effort |
| `ExecutorService.shutdown()` | Ngừng nhận task mới, xử lý task đã gửi theo executor | Không phải hủy mọi task |
| `ExecutorService.shutdownNow()` | Cố dừng task đang chạy và trả task chưa bắt đầu | Dùng interrupt best effort; không ép dừng |
| `ExecutorService.awaitTermination(...)` | Chờ executor kết thúc tối đa khoảng thời gian | Không tự gửi cancel nếu chỉ đợi hết hạn |
| HTTP client read timeout | Giới hạn thời gian chờ I/O ở client | Server/provider có thể đã xử lý |
| Spring MVC async request timeout | Giới hạn vòng đời async HTTP response | Không tự bảo đảm background job dừng |

Điểm quan trọng: **không được đánh đồng tên cùng chứa “timeout”**. Có API giới hạn chờ, API hoàn tất trạng thái Future, API timeout I/O và API có hành vi cancellation riêng.

## 13. Lựa chọn kiến trúc khi công việc có thể lâu hơn request timeout

Nếu tạo báo cáo mất 30 giây nhưng reverse proxy cho phép HTTP request tối đa 5 giây, thay vì cho request gọi `get(5, SECONDS)` rồi báo lỗi mơ hồ, có thể dùng giao thức job nền:

```text
POST /jobs + Idempotency-Key
    |
    v
Xác thực, kiểm tra đầu vào
    |
    v
Ghi job vào DB / queue bền vững, commit
    |
    v
HTTP 202 + jobId + statusUrl
    |
    v
Worker riêng xử lý, retry theo chính sách
    |
    v
RUNNING → SUCCEEDED / FAILED / CANCELLED / RECONCILING
    |
    v
GET /jobs/{jobId} hoặc webhook để lấy kết quả cuối
```

**Lợi ích:**

- HTTP client không cần giữ một kết nối chờ toàn bộ xử lý.
- Job có trạng thái và ID độc lập với HTTP request.
- Mức độ bền vững tùy job store/queue đã chọn, không phụ thuộc RAM của một process.
- Client có thể retry request bằng idempotency key mà không tạo thêm job logic.
- Worker có thể xử lý retry, crash recovery và dead-letter riêng.
- Nếu muốn hủy, API `POST /jobs/{id}/cancel` có thể ghi `CANCEL_REQUESTED` và để worker xác nhận an toàn.

**Không hứa exactly-once side effects chỉ vì dùng queue.** Message broker có thể phát lại, nên worker cần idempotency và cơ chế reconciliation. 202 sau khi commit job bền vững là lời xác nhận **đã tiếp nhận bền vững**, không phải “nghiệp vụ xong”.

## 14. Các lỗi thường gặp

**Lỗi 1 — Bắt `TimeoutException` rồi ghi log “Task cancelled”.** Sai nếu không có yêu cầu hủy và xác nhận worker dừng. Chỉ biết caller hết thời gian chờ.

**Lỗi 2 — Đặt `get(100ms)` và nghĩ JDBC query cũng có timeout 100 ms.** Query có deadline riêng; cần cấu hình driver/query/transaction đúng.

**Lỗi 3 — Nghĩ Future chưa xong sau timeout sẽ bị GC và ngừng chạy.** Task đã được executor giữ và worker có thể tiếp tục; việc bỏ tham chiếu Future của caller không hủy task.

**Lỗi 4 — Gọi `cancel(true)` rồi tin mọi tác động đã đảo ngược.** Interrupt không phải rollback HTTP, database đã commit hay message đã gửi.

**Lỗi 5 — Bắt `InterruptedException` rồi im lặng tiếp tục.** Dễ bỏ qua shutdown/cancellation, khiến task giữ resource và tạo side effect sau khi đã yêu cầu dừng.

**Lỗi 6 — Dùng `CompletableFuture.cancel(true)` để “ép interrupt” supplier.** Tham số boolean không có tác dụng interrupt trong implementation CF của JDK.

**Lỗi 7 — Dùng `orTimeout` mà nghĩ backend I/O được hủy.** Nó hoàn tất CF ngoại lệ, không tự dừng supplier gốc.

**Lỗi 8 — Dùng `completeOnTimeout` với fallback và tuyên bố nghiệp vụ gốc thành công/thất bại.** Fallback chỉ là kết quả của Future theo contract hiện tại; work nền vẫn có thể tạo tác động.

**Lỗi 9 — Retry vô hạn sau timeout trên cùng operation không idempotent.** Có thể phát sinh hai lần gửi email, tạo file hoặc thanh toán.

**Lỗi 10 — Không phân biệt thời gian xếp hàng và thời gian chạy.** `get(timeout)` có thể hết hạn vì task chưa có worker, không phải logic bên trong task bị treo.

**Lỗi 11 — Chờ `Future.get(timeout)` trên thread của single-thread executor rồi cần một task khác trên cùng pool để hoàn tất.** Có thể starvation/deadlock; timeout giới hạn thời gian bị kẹt nhưng không sửa thiết kế phụ thuộc vòng.

**Lỗi 12 — Chỉ nhìn `future.isCancelled()` để kết luận worker đã dừng.** Cancellation state và thực tế worker rời code có thể khác nhau.

**Lỗi 13 — Không quản lý task lỗi muộn.** Caller đã timeout nhưng worker ném exception sau đó; cần callback/metric/job store để quan sát kết quả cuối.

**Lỗi 14 — Gắn trạng thái `FAILED` lên job chỉ vì client đóng kết nối.** Client timeout là góc nhìn từ caller, không tự có nghĩa job không thể tiếp tục.

**Lỗi 15 — Không dọn executor.** Pool dùng chung phải có lifecycle shutdown; trong Spring nên khai báo executor bean có tên và để context quản lý.

## 15. Checklist triển khai timeout an toàn

- [ ] Xác định **timeout của ai**: HTTP client, request server, `Future.get`, dependency I/O hay deadline nghiệp vụ.
- [ ] Sau `TimeoutException`, **không tự nhận định task đã dừng**; cần giữ handle/job ID và quan sát trạng thái.
- [ ] Quyết định có được phép yêu cầu hủy không, và ai có quyền hủy.
- [ ] Nếu yêu cầu hủy bằng `Future.cancel(true)`, task có **cooperative interrupt handling** và timeout cho blocking I/O.
- [ ] Nếu dùng `CompletableFuture.orTimeout/completeOnTimeout`, biết chúng quản lý **completion state**, không dừng supplier.
- [ ] Đặt transaction/commit, timeout của HTTP/JDBC/downstream và chính sách retry rõ ràng.
- [ ] Tránh retry tác động không idempotent; dùng idempotency key và reconciliation.
- [ ] Không chuyển job sang `FAILED` hay `CANCELLED` chỉ vì caller hết thời gian chờ.
- [ ] Với task dài, ưu tiên job store bền vững + HTTP 202 + status endpoint nếu hợp đồng cần.
- [ ] Theo dõi job chạy sau timeout, worker active, queue age, task duration, cancellation requested/confirmed, success/failure.
- [ ] Kiểm thử bằng `CountDownLatch` để loại bỏ timing ngẫu nhiên; kiểm thử riêng timeout, cancel(false), cancel(true), interrupt và shutdown.
- [ ] Kiểm tra việc cleanup thread-local, resource và exception khi request kết thúc trước background job.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** `future.get(1, TimeUnit.SECONDS)` ném `TimeoutException`. Worker có bị interrupt tự động không?

**Đáp án:** Không. Caller đã ngừng chờ kết quả trong giới hạn một giây; worker có thể đang chạy hoặc vẫn chờ queue.

**Câu 2.** Ngay sau `TimeoutException`, `future.isDone()` trả `true`. Có mâu thuẫn không?

**Đáp án:** Không. Task có thể vừa hoàn tất ngay sau mốc timeout nhưng trước lần đọc `isDone()`. Cần phân biệt thời điểm hai phép quan sát.

**Câu 3.** `cancel(true)` trả `true`. Có thể khẳng định giao dịch ngoài đã được rollback không?

**Đáp án:** Không. Cancellation cấp Future không chứng minh worker đã dừng mọi bước hoặc provider/DB đã hoàn tác tác động.

**Câu 4.** Vì sao `CompletableFuture.cancel(true)` không tương đương `FutureTask.cancel(true)`?

**Đáp án:** Implementation `CompletableFuture` không dùng interrupt để điều khiển thực thi; `mayInterruptIfRunning` không có tác dụng. FutureTask có thể gửi interrupt tới runner nhưng cũng chỉ là tín hiệu hợp tác.

**Câu 5.** `cf.orTimeout(500, MILLISECONDS)` có bảo đảm HTTP call nằm trong supplier kết thúc sau 500 ms không?

**Đáp án:** Không. Nó chỉ có thể hoàn tất CF ngoại lệ nếu chưa xong; HTTP call cần timeout/cancellation của HTTP client riêng.

**Câu 6.** Một report job được nhận, caller timeout, rồi client retry ngay. Nguy cơ lớn nhất là gì?

**Đáp án:** Job cũ vẫn có thể chạy; retry có thể sinh job mới và side effect trùng. Dùng idempotency key, job ID và truy vấn trạng thái trước khi tạo thêm việc.

**Câu 7.** Nếu code bắt `InterruptedException` trong worker thì nên làm gì?

**Đáp án:** Thường phải dọn resource, tôn trọng yêu cầu hủy, truyền exception lên hoặc khôi phục cờ bằng `Thread.currentThread().interrupt()` nếu không truyền tiếp được; không im lặng tiếp tục nghiệp vụ.

**Câu 8.** `ExecutorService.invokeAll(tasks, timeout, unit)` có hoàn toàn giống gọi `get(timeout)` trên từng Future không?

**Đáp án:** Không. `invokeAll` có timeout có quy tắc cancellation cho các task chưa hoàn tất khi thời hạn hết; `get(timeout)` của một Future chỉ giới hạn caller wait.

**Câu 9.** Khi HTTP response đã trả “timeout”, Spring transaction trong background worker có tự rollback không?

**Đáp án:** Không chỉ vì response timeout. Transaction boundary và quy tắc rollback của worker riêng; còn side effect ở service ngoài cần cơ chế đối soát/compensation.

**Câu 10.** Bằng chứng nào mạnh hơn `future.isCancelled()` khi cần xác minh worker ngừng an toàn?

**Đáp án:** Có tín hiệu trạng thái do worker cập nhật sau khi rời bước xử lý/cleanup, hoặc job store phản ánh `CANCELLED` theo state machine đã định nghĩa, cùng log/metric và đối soát các side effect.

## Kết luận

**`Future.get(timeout)` giới hạn thời gian caller chờ kết quả, không tự hủy công việc nền.** `TimeoutException` của caller, `Future.cancel(...)` và worker thực sự dừng là ba sự kiện khác nhau.

- Muốn **hết chờ**: dùng `get(timeout)` hoặc cơ chế HTTP timeout phù hợp.
- Muốn **yêu cầu hủy**: cần gọi API cancellation và làm cho task/operation hợp tác với interrupt/deadline.
- Muốn **biết job hoàn thành hay chưa**: dùng result/status/exception có thể quan sát.
- Muốn **đảm bảo không mất hoặc không lặp side effect**: cần job store, idempotency, transaction boundary và reconciliation ở tầng nghiệp vụ.

Đừng biến timeout của một thread thành tuyên bố rằng công việc, giao dịch hay tác động bên ngoài đã biến mất.

## Tài liệu tham khảo

1. Java SE 17 — `Future` (timed get, cancellation, `isDone`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html
2. Java SE 17 — `CompletableFuture` (timeouts, `cancel`, execution policies): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html
3. Java SE 17 — `ExecutorService` (`invokeAll` timeout, shutdown): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
4. Java SE 17 — `Thread` (interrupt semantics): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
5. Spring Framework — `@Async` and task execution: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
6. Spring Framework — `ThreadPoolTaskExecutor` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
7. HTTP Semantics — status 202 Accepted: https://httpwg.org/specs/rfc9110.html#status.202


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="timeout-cancellation-003">orTimeout() và completeOnTimeout() không mặc nhiên dừng thao tác đang chạy</a>

<details>
<summary>Xem chi tiết</summary>

# orTimeout() và completeOnTimeout() không mặc nhiên dừng thao tác đang chạy

## 1. Vì sao Future hết hạn nhưng tác vụ vẫn chạy?

Khi xử lý bất đồng bộ trong Java hoặc Spring, một `CompletableFuture<T>` thường biểu diễn **kết quả có thể nhận trong tương lai**, nhưng nó **không nhất thiết có quyền điều khiển vòng đời của công việc đã được gửi vào executor**. Đây là sự khác biệt quan trọng khi dùng:

- `orTimeout(long timeout, TimeUnit unit)` — nếu future chưa hoàn tất, cho **chính future đó** hoàn tất **exceptionally** bằng `TimeoutException` sau thời hạn.
- `completeOnTimeout(T value, long timeout, TimeUnit unit)` — nếu future chưa hoàn tất, cho **chính future đó** hoàn tất **bình thường** với giá trị dự phòng sau thời hạn.

**Cả hai đều thay đổi cách caller quan sát kết quả; không mặc nhiên ngắt thread đang chạy, hủy lệnh HTTP, dừng query database hay rollback tác dụng phụ của tác vụ.** Worker có thể tiếp tục làm việc sau khi future đã timeout hoặc đã trả fallback.

### Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt **trạng thái của `CompletableFuture`** với **trạng thái thật của computation/I/O bên dưới**.
2. Giải thích `orTimeout`, `completeOnTimeout`, `get(timeout)`, `cancel(true)` và giá trị trả về của từng API.
3. Xác định “first completion wins”: kết quả muộn thường không thay thế một future đã hoàn tất vì timeout/fallback.
4. Viết chương trình Java 17+ có kết quả xác định, sử dụng `CountDownLatch` để chứng minh worker còn bận sau khi future đã hoàn tất.
5. Áp dụng với `ThreadPoolTaskExecutor` của Spring, lựa chọn executor riêng, quản lý tài nguyên và timeout của I/O thật.
6. Tránh các lỗi production: retry chồng tác vụ, dùng fallback che lỗi, giữ connection quá lâu, gây đầy pool, hoặc tưởng `CompletableFuture.cancel(true)` đã interrupt worker.
7. Kiểm thử cả **phản hồi đúng hạn** và **giải phóng tài nguyên thật**, thay vì chỉ assert trạng thái future.

**Phiên bản:** `orTimeout` và `completeOnTimeout` có từ **Java 9**, chạy trong ví dụ **Java 17+**. Ví dụ Spring dùng **Spring Framework 6.1.3**; các API timeout là của JDK, không phải tính năng riêng của Spring.

## 2. Bốn đại lượng thời gian không giống nhau

Một request bất đồng bộ thường có ít nhất bốn mốc khác nhau:

| Loại thời gian | Đo điều gì? | API/cơ chế thường dùng |
| --- | --- | --- |
| **Thời gian chờ của caller** | Caller đợi kết quả tối đa bao lâu | `Future.get(timeout, unit)` |
| **Thời hạn hoàn tất Future** | Khi nào caller nhìn thấy timeout hoặc fallback | `orTimeout` / `completeOnTimeout` |
| **Thời gian thao tác thật** | HTTP/SQL/file I/O thực thi bao lâu | Timeout của HTTP client, JDBC, SDK, socket, query, driver |
| **Vòng đời worker/task** | Task đã chạy xong, bị interrupt, bị cancel hay vẫn đang chiếm worker | Executor, `FutureTask`, interrupt hợp tác, resource-specific cancellation |

Không thể chỉ nhìn trạng thái của Future để suy ra mọi tầng bên dưới đều đã dừng.

Ví dụ một `Supplier` gọi API đồng bộ trong 8 giây, nhưng `completeOnTimeout("cached", 1, SECONDS)` trả fallback sau 1 giây: caller thấy “cached” nhưng HTTP request đồng bộ **có thể vẫn chạy 7 giây nữa**. Nếu nhiều request như vậy xảy ra, worker và kết nối vẫn có thể bị chiếm, bất kể API của bạn trả lời nhanh.

### Timeline minh họa

~~~text
t = 0 ms       Caller submit task --> worker bắt đầu HTTP call
                 |
t = 1000 ms    orTimeout: Future hoàn tất với TimeoutException
                 |         hoặc
                 +------ completeOnTimeout: Future hoàn tất với fallback
                 |
                 |        Worker vẫn có thể đang đợi HTTP/DB
                 |        Không có interrupt tự động từ hai API trên
                 |
t = 8000 ms    HTTP call kết thúc, Supplier trả kết quả "real"
                 |
                 +------ Future đã timeout/fallback trước đó:
                         kết quả muộn không ghi đè kết quả thắng trước
~~~

Timeline này minh họa tình huống tác vụ thực tế chạy lâu hơn deadline; không phải quy định thread luôn chạy đủ 8 giây. Nếu có timeout đúng tầng I/O, worker có thể kết thúc sớm hơn.

## 3. API Java 17: các điểm chính xác nhất

Hai phương thức instance của `java.util.concurrent.CompletableFuture<T>`:

~~~java
// Chữ ký API rút gọn, không phải Java class độc lập.
CompletableFuture<T> orTimeout(long timeout, TimeUnit unit);

CompletableFuture<T> completeOnTimeout(
        T value, long timeout, TimeUnit unit);
~~~

Javadoc của **cả hai** đều ghi **returns: this CompletableFuture**. Chúng **không tạo một Future mới độc lập** chỉ vì phương thức có kiểu trả về `CompletableFuture<T>`. Chúng lập lịch một hành động cố hoàn tất **đối tượng gốc** nếu đối tượng đó chưa hoàn tất vào lúc timeout.

### 3.1. `orTimeout()`: hoàn tất bằng lỗi timeout

~~~java
// Fragment minh họa, giả sử future là CompletableFuture<String>.
CompletableFuture<String> timed =
        future.orTimeout(2, TimeUnit.SECONDS);
boolean sameObject = timed == future; // true
~~~

Nếu task đã hoàn tất trước 2 giây, kết quả bình thường/lỗi sẵn có được giữ nguyên. Nếu timeout thắng, future hoàn tất **exceptionally** với `TimeoutException`.

- `future.isDone()` trở thành `true`.
- `future.isCompletedExceptionally()` trở thành `true`.
- `future.isCancelled()` **không tự trở thành true**: timeout không đồng nghĩa cancellation.
- `future.join()` thường ném `CompletionException` với `getCause()` là `TimeoutException`.
- `future.get()` theo hợp đồng Future ném `ExecutionException` với cause là `TimeoutException`.
- Worker/I/O thực tế không tự bị ngắt bởi lệnh timeout này.

Không viết code kiểu `catch (TimeoutException ex)` trực tiếp quanh `join()`: `join()` không khai báo checked `TimeoutException`; cần bắt `CompletionException` và xem cause. `get(timeout, unit)` và `get()` có kiểu exception khác, hãy phân biệt chúng.

### 3.2. `completeOnTimeout()`: hoàn tất bằng fallback

~~~java
// Fragment minh họa.
CompletableFuture<String> recovered =
        future.completeOnTimeout("cached", 2, TimeUnit.SECONDS);
boolean sameObject = recovered == future; // true
~~~

Nếu future chưa hoàn tất sau 2 giây, nó hoàn tất **bình thường** với `"cached"`. Khi đó:

- `future.isDone()` là `true`.
- `future.isCompletedExceptionally()` là `false`, vì fallback được xem là **giá trị thành công của Future**.
- `future.join()` trả `"cached"`.
- Supplier vẫn có thể tiếp tục xử lý, truy cập database hoặc gọi API khác.
- Kết quả thực tới sau sẽ không làm `join()` tự chuyển từ `"cached"` sang giá trị thực.

Vì fallback được coi là thành công ở mức future, cần **đánh dấu rõ nguồn giá trị trong nghiệp vụ** (ví dụ `Result(value, fromFallback)`, metric hay log); nếu không, tầng gọi có thể không phân biệt dữ liệu mới với dữ liệu cache.

### 3.3. `get(timeout)` chỉ giới hạn thời gian caller đợi

~~~java
// Fragment: giới hạn lần đợi hiện tại.
String result = future.get(2, TimeUnit.SECONDS);
~~~

Nếu quá hạn, `get(timeout, unit)` ném **`TimeoutException` vào thread đang chờ**, nhưng **không tự hoàn tất hoặc hủy Future**. Nếu task sau đó trả kết quả thành công, bạn vẫn có thể gọi `future.get()`/`join()` lần nữa để lấy kết quả thực.

Đây là khác biệt quyết định:

| Đặc điểm | `get(2, SECONDS)` | `orTimeout(2, SECONDS)` | `completeOnTimeout("cached", 2, SECONDS)` |
| --- | --- | --- | --- |
| Mục đích | Giới hạn một lần caller **chờ** | Đặt hạn hoàn tất Future bằng lỗi | Đặt hạn hoàn tất Future bằng fallback |
| Trả về | Giá trị `T` hoặc ném exception | **Cùng đối tượng** `CompletableFuture<T>` | **Cùng đối tượng** `CompletableFuture<T>` |
| Khi quá hạn | Caller nhận `TimeoutException` | Future hoàn tất exceptionally với `TimeoutException` | Future hoàn tất bình thường với `"cached"` |
| `isDone()` của Future sau timeout | Có thể vẫn `false` | `true` | `true` |
| Kết quả thực đến muộn | **Vẫn có thể lấy được** | Thường không thay trạng thái Future đã timeout | Thường không thay giá trị fallback đã thắng |
| Ngắt worker tự động | **Không** | **Không** | **Không** |
| Hủy HTTP/SQL tự động | **Không** | **Không** | **Không** |

**Lưu ý race:** Nếu kết quả thật hoàn tất ngay gần lúc timeout, chính thao tác hoàn tất nào **thắng trước** quyết định trạng thái. Không viết test dựa vào việc hai sự kiện sát nhau nhất định diễn ra theo một thứ tự duy nhất.

## 4. Một Future đã timeout khác một task đã kết thúc

`CompletableFuture` là một cơ chế **hoàn tất giá trị** và **ghép stage**. `CompletableFuture.supplyAsync(supplier, executor)` đưa một tác vụ vào `Executor`, nhưng `CompletableFuture` không nhất thiết có handle để ngắt, dừng hoặc rollback việc `Supplier` đang làm.

Trong `CompletableFuture`, những lời gọi cố hoàn tất cùng future từ nhiều thread có cơ chế cạnh tranh: **chỉ một bên thắng** ở completion thông thường (`complete`, `completeExceptionally`, `cancel`). Khi deadline đã hoàn tất future trước, worker chạy xong muộn thường chỉ cố đưa kết quả vào một future đã hoàn tất; giá trị muộn không ghi đè completion thắng trước.

**Tác vụ vẫn có thể gây side effect sau thời điểm caller nhận timeout.** Ví dụ:

- Một giao dịch/chuyển khoản vẫn được gửi tới remote service.
- Lệnh INSERT/UPDATE có thể vẫn chạy, dù HTTP client đã trả fallback.
- Bộ nhớ, socket, database connection và thread của executor vẫn bị giữ.
- Task xong muộn có thể kích hoạt log/metric hoặc một side effect khác trong chính Supplier.
- Nếu caller retry ngay vì timeout, hai thao tác có thể **chạy chồng nhau**.

Không lấy `future.isDone() == true` làm tín hiệu rằng thread/socket/transaction đều đã hoàn thành và tài nguyên được giải phóng.

## 5. Ví dụ Java 17+ chạy độc lập: ba kiểu timeout và một worker còn bận

Đây là thí nghiệm cốt lõi của bài. Ta dùng `CountDownLatch` để **giữ Supplier ở trạng thái đang chạy** cho đến khi main chủ động mở khóa. Nhờ thế có thể chứng minh `orTimeout` và `completeOnTimeout` hoàn tất Future **trong khi Supplier chưa hoàn tất**.

Chương trình có ba nhánh:

1. `orTimeout`: Future báo `TimeoutException` nhưng worker vẫn đợi.
2. `completeOnTimeout`: Future trả `fallback` nhưng worker vẫn đợi.
3. `get(timeout)`: caller nhận `TimeoutException` nhưng Future vẫn pending, sau đó lấy được kết quả thật.

Lưu thành `TimeoutDoesNotStopDemo.java`:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class TimeoutDoesNotStopDemo {

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            showOrTimeout(pool);
            showCompleteOnTimeout(pool);
            showTimedGet(pool);
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    private static CompletableFuture<String> blockedWork(
            ExecutorService pool,
            CountDownLatch started,
            CountDownLatch release,
            CountDownLatch workerReachedFinally) {

        return CompletableFuture.supplyAsync(() -> {
            started.countDown();
            try {
                release.await();
                return "real";
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return "interrupted";
            } finally {
                workerReachedFinally.countDown();
            }
        }, pool);
    }

    private static void showOrTimeout(ExecutorService pool)
            throws Exception {

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch reachedFinally = new CountDownLatch(1);
        CompletableFuture<String> future = blockedWork(
                pool, started, release, reachedFinally);

        try {
            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker did not start");
            }

            boolean same = future.orTimeout(
                    100, TimeUnit.MILLISECONDS) == future;
            System.out.println("orSameFuture=" + same);

            try {
                future.join();
                System.out.println("unexpected-or-success");
            } catch (CompletionException ex) {
                System.out.println("orCause="
                        + ex.getCause().getClass().getSimpleName());
            }

            System.out.println("orWorkerFinishedAtTimeout="
                    + (reachedFinally.getCount() == 0));
        } finally {
            release.countDown(); // giải phóng ngay cả khi nhánh đợi lỗi
        }

        if (!reachedFinally.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("worker did not exit");
        }
        System.out.println("orStillExceptional="
                + future.isCompletedExceptionally());
    }

    private static void showCompleteOnTimeout(ExecutorService pool)
            throws Exception {

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch reachedFinally = new CountDownLatch(1);
        CompletableFuture<String> future = blockedWork(
                pool, started, release, reachedFinally);

        try {
            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker did not start");
            }

            boolean same = future.completeOnTimeout(
                    "fallback", 100, TimeUnit.MILLISECONDS) == future;
            System.out.println("fallbackSameFuture=" + same);
            System.out.println("fallbackValue=" + future.join());
            System.out.println("fallbackWorkerFinishedAtTimeout="
                    + (reachedFinally.getCount() == 0));
        } finally {
            release.countDown();
        }

        if (!reachedFinally.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("worker did not exit");
        }
        System.out.println("fallbackAfterRealWork=" + future.join());
    }

    private static void showTimedGet(ExecutorService pool)
            throws Exception {

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch reachedFinally = new CountDownLatch(1);
        CompletableFuture<String> future = blockedWork(
                pool, started, release, reachedFinally);

        try {
            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker did not start");
            }

            try {
                future.get(100, TimeUnit.MILLISECONDS);
                System.out.println("unexpected-get-success");
            } catch (TimeoutException ex) {
                System.out.println("getTimedOut=true");
            }

            System.out.println("getFutureDoneAtTimeout="
                    + future.isDone());
        } finally {
            release.countDown();
        }

        if (!reachedFinally.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("worker did not exit");
        }
        System.out.println("getValueAfterRealWork=" + future.join());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac TimeoutDoesNotStopDemo.java
java TimeoutDoesNotStopDemo
~~~

**Đầu ra kỳ vọng khi chạy bình thường:**

~~~text
orSameFuture=true
orCause=TimeoutException
orWorkerFinishedAtTimeout=false
orStillExceptional=true
fallbackSameFuture=true
fallbackValue=fallback
fallbackWorkerFinishedAtTimeout=false
fallbackAfterRealWork=fallback
getTimedOut=true
getFutureDoneAtTimeout=false
getValueAfterRealWork=real
~~~

### Giải thích tính xác định của ví dụ

- `started.await(...)` chờ đúng tín hiệu worker **đã bắt đầu**; task sau đó đợi `release`.
- Khi main gọi `join()` sau `orTimeout` hoặc `completeOnTimeout`, worker vẫn bị `release.await()` giữ lại. Vì vậy in `...WorkerFinishedAtTimeout=false`, không cần đoán worker có ngủ đủ lâu hay không.
- `orTimeout` hoàn tất future bằng lỗi, nên `join()` báo `CompletionException` với nguyên nhân `TimeoutException`. Worker đến `finally` sau khi được mở latch **không thể biến future đó thành thành công**.
- `completeOnTimeout` đưa `"fallback"` vào chính future; khi worker cuối cùng trả `"real"`, `future.join()` vẫn là `"fallback"`.
- `get(100, MILLISECONDS)` chỉ giới hạn lần đợi; sau khi giải phóng worker, Future chưa bị ép timeout nên trả được `"real"`.
- Các dòng output chỉ được in bởi **main**, không in đồng thời từ worker, nên không phụ thuộc thứ tự log giữa threads.
- Mỗi phương thức có `finally` mở latch; `main` gọi `shutdown/awaitTermination` để dọn worker. Trong một môi trường không khởi chạy được worker trong vòng 5 giây, chương trình chủ động ném lỗi khởi động thay vì giả định kết quả chuẩn.

**Lưu ý kỹ thuật:** Latch `workerReachedFinally` xác nhận Supplier **đã đi đến `finally`**; ngay sau đó thread còn phải trả về khỏi lambda và executor cập nhật completion. Phép kiểm tra ở đây chỉ cần chứng minh lúc deadline xảy ra Supplier **chưa đến `finally`**, không dựa vào việc một latch là cơ chế công bố trạng thái công việc theo hợp đồng Java `Future`.

## 6. Vì sao không nên sử dụng Thread.sleep để “chứng minh” timeout?

Một ví dụ đơn giản thường làm `Thread.sleep(500)` rồi `orTimeout(100, MILLISECONDS)`. Nó có thể minh họa ý tưởng, nhưng nếu kiểm thử chỉ nhìn thời điểm in hoặc thread scheduling, máy CI quá tải có thể làm thứ tự khó dự đoán. Dùng `CountDownLatch` trong bài trên tốt hơn vì worker không thể vượt qua điểm chờ trước khi main mở latch.

Trong production, timeout đo bằng **thời gian thực** và hoàn toàn có race giữa tác vụ và deadline. Test nên tập trung vào **hành vi cần bảo đảm**: Future trả fallback hoặc lỗi timeout trong điều kiện tác vụ chưa được giải phóng, không tự hủy I/O, đồng thời pool được dọn sạch sau test. Không viết assertion rằng timer luôn kích hoạt chính xác ở mili giây thứ 100.

## 7. Spring Framework: TaskExecutor không tự đổi cơ chế timeout của CompletableFuture

`ThreadPoolTaskExecutor` trong Spring quản lý `java.util.concurrent.ThreadPoolExecutor`, cung cấp các tham số `corePoolSize`, `maxPoolSize` và `queueCapacity`. Bạn có thể dùng bean này như `Executor` được truyền vào `CompletableFuture.supplyAsync(supplier, executor)`.

Điểm cần nhớ:

- `orTimeout` và `completeOnTimeout` vẫn là **API của `CompletableFuture` trong Java**, không mang chức năng dừng task của `ThreadPoolTaskExecutor`.
- Chúng có thể hoàn tất future **trong khi worker Spring còn bận chờ I/O**.
- Tăng tốc phản hồi cho caller không tự tăng khả năng phục vụ: pool và downstream vẫn có thể đầy nếu work tiếp tục quá lâu.
- `AsyncTaskExecutor.submitCompletable(...)` có từ **Spring Framework 6.0+**, cũng trả `CompletableFuture`. Khi áp timeout lên future đó, không được mặc định rằng worker chạy `Runnable/Callable` đã bị interrupt.
- `TaskExecutor` bản cơ sở chỉ cần `execute(Runnable)`; `CompletableFuture.supplyAsync(..., taskExecutor)` vẫn hợp lệ vì `TaskExecutor` kế thừa `Executor`. Không nhầm với overload `submitCompletable` của `AsyncTaskExecutor`.
- Với `@Async`, chọn đúng executor và gọi method qua Spring proxy; timeout được gắn ở **future caller nhận** vẫn cần kết hợp timeout/cancellation ở tầng business operation.

### 7.1. Dự án Maven Spring có thể chạy độc lập

Ví dụ sử dụng **Spring Framework 6.1.3** và **JDK 17+**, tạo một `ThreadPoolTaskExecutor` dưới dạng bean Spring, rồi truyền trực tiếp vào `supplyAsync`. Một `CountDownLatch` giữ worker đang chạy để chứng minh `completeOnTimeout` trả fallback nhưng worker chưa dừng.

Cấu trúc:

~~~text
spring-timeout-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── SpringTimeoutDemo.java
~~~

Nội dung `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-timeout-demo</artifactId>
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
                    <mainClass>demo.SpringTimeoutDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Nội dung `src/main/java/demo/SpringTimeoutDemo.java`:

~~~java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public final class SpringTimeoutDemo {

    @Configuration
    public static class PoolConfiguration {

        @Bean("ioExecutor")
        public ThreadPoolTaskExecutor ioExecutor() {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(1);
            pool.setMaxPoolSize(1);
            pool.setQueueCapacity(2);
            pool.setThreadNamePrefix("io-");
            return pool;
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             PoolConfiguration.class)) {

            ThreadPoolTaskExecutor executor =
                    context.getBean("ioExecutor", ThreadPoolTaskExecutor.class);

            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch workerReachedFinally = new CountDownLatch(1);

            CompletableFuture<String> future =
                    CompletableFuture.supplyAsync(() -> {
                        started.countDown();
                        try {
                            release.await();
                            return "remote-result";
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            return "interrupted";
                        } finally {
                            workerReachedFinally.countDown();
                        }
                    }, executor);

            try {
                if (!started.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("worker did not start");
                }

                future.completeOnTimeout(
                        "cached-result", 100, TimeUnit.MILLISECONDS);

                System.out.println("springFallback=" + future.join());
                System.out.println("springWorkerFinishedAtFallback="
                        + (workerReachedFinally.getCount() == 0));
            } finally {
                release.countDown();
            }

            if (!workerReachedFinally.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker did not exit");
            }

            System.out.println("springResultAfterWork=" + future.join());
        }
    }
}
~~~

Chạy trong thư mục `spring-timeout-demo`:

~~~bash
mvn -q compile exec:java
~~~

Đầu ra do chương trình Java in:

~~~text
springFallback=cached-result
springWorkerFinishedAtFallback=false
springResultAfterWork=cached-result
~~~

**Vì sao khác kết quả thật?** Worker của `ThreadPoolTaskExecutor` đang chờ `release`, còn future đã hoàn tất với `"cached-result"`. Khi worker được mở latch và trả `"remote-result"`, phép hoàn tất không ghi đè future đã có kết quả. `context.close()` trong `try-with-resources` kích hoạt container dọn executor bean.

**Không cần `@EnableAsync`** ở ví dụ này: chương trình dùng trực tiếp `CompletableFuture.supplyAsync(..., executor)`, không gọi một method `@Async` qua proxy. Đừng nhầm việc cấu hình executor bean với việc Spring tự chọn executor cho mọi `supplyAsync(supplier)` **không truyền tham số thứ hai**.

Đây là thí nghiệm có thời hạn 100 ms để quan sát; production phải chọn deadline dựa trên SLA thực, không lấy 100 ms làm mặc định cho tác vụ mạng.

## 8. Timeout của CompletableFuture khác timeout thật của HTTP, database và socket

Khi Supplier làm I/O đồng bộ như:

~~~java
// Phản ví dụ kiến trúc: remoteClient.fetch(id) là lời gọi I/O chặn.
// Mã chỉ minh họa; các biến/hàm không định nghĩa ở đây.
CompletableFuture<String> future =
        CompletableFuture.supplyAsync(
                () -> remoteClient.fetch(id), ioExecutor)
        .completeOnTimeout("cached", 1, TimeUnit.SECONDS);
~~~

Caller có thể lấy `"cached"` sau 1 giây, nhưng `remoteClient.fetch` vẫn có thể chiếm một worker và connection. Cần **deadline cho operation thực**, ví dụ:

- **HTTP client:** connection timeout, response/read/request timeout tương thích với thư viện đang dùng. Với `java.net.http.HttpRequest.Builder.timeout(...)`, request timeout liên quan đến chính yêu cầu HTTP; khi dùng client SDK khác phải xem API của SDK.
- **JDBC/SQL:** query timeout ở `Statement.setQueryTimeout(...)` khi driver hỗ trợ, timeout giao dịch/bên database và chính sách cancel query. `Future.orTimeout` không phát lệnh cancel SQL.
- **Socket/SDK đồng bộ:** timeout đọc/ghi/tổng thời gian, khả năng hủy request và xử lý khi thread bị interrupt khác nhau giữa thư viện.
- **Bộ nhớ / hàng đợi:** pool bounded (`corePoolSize`, `maxPoolSize`, `queueCapacity`), semaphore/rate limit và admission control tránh số task “đã timeout nhưng vẫn còn chạy” tăng không giới hạn.
- **Vòng đời request:** nếu caller đã ngắt kết nối hoặc deadline hết, hệ thống vẫn phải quyết định business operation nên tiếp tục, hủy hợp tác hay lưu bền vững.

### Quy tắc phân bổ deadline

Giả sử API có SLA 2 giây. Không nên cho một DB query có thể chạy 30 giây trong khi Future trả fallback sau 2 giây rồi nghĩ hệ thống đã giải phóng tài nguyên. Hãy cân nhắc: thời gian queue wait, connect timeout, read/query timeout, retry budget và thời gian serialization/response.

**Một timeout ở tầng phản hồi không thay thế timeout ở tầng I/O.** Nếu thao tác phải bị hủy thật, cần dùng primitive mà thư viện cung cấp để hủy query/request, hoặc thiết kế cancellation có hợp đồng rõ ràng. Ngay cả khi có hủy, remote service có thể đã thực hiện side effect; cancellation không đồng nghĩa rollback.

## 9. Nếu cần hủy thật: cancel là yêu cầu, không phải phép màu

`CompletableFuture.cancel(boolean mayInterruptIfRunning)` của JDK Java 17 được tài liệu hóa như một dạng **hoàn tất exceptionally bằng `CancellationException`**; tham số `mayInterruptIfRunning` **không dùng để ngắt computation** trong implementation `CompletableFuture`.

~~~java
// Fragment minh họa, future là CompletableFuture<T> đã có.
boolean cancelled = future.cancel(true);
boolean flag = future.isCancelled();
~~~

Dù `cancelled` có thể là true, điều đó không đủ để khẳng định worker đang chạy đã dừng. `CompletableFuture` không nhất thiết giữ handle của `Thread`/`FutureTask` đang thực thi Supplier.

Một `Future<?>` nhận được từ `ExecutorService.submit(...)` hoặc `AsyncTaskExecutor.submit(...)` có thể có khả năng `cancel(true)` để **thử interrupt worker** tùy implementation. Nhưng interrupt là **tín hiệu hợp tác**, không phải đảm bảo dừng mọi socket I/O, giao dịch, hoặc side effect.

~~~java
// Fragment: rawExecutor là ExecutorService có sẵn.
// Cần xử lý/khai báo InterruptedException và ExecutionException.
Future<String> operation = rawExecutor.submit(() -> remoteCall());
try {
    return operation.get(1, TimeUnit.SECONDS);
} catch (TimeoutException ex) {
    operation.cancel(true); // thử interrupt nếu implementation hỗ trợ
    throw ex;
}
~~~

Đây là fragment trong method có return type phù hợp, import `Future`/`TimeoutException`/`TimeUnit` và hàm `remoteCall()` của ứng dụng. `cancel(true)` **không thay thế timeout của HTTP/DB client**. Nếu caller đã gửi lệnh cập nhật dữ liệu, server nhận lệnh có thể vẫn xử lý dù local thread bị ngắt.

### Cooperative cancellation trong code tự viết

Với tác vụ tự điều khiển vòng lặp, có thể kiểm tra `Thread.currentThread().isInterrupted()` hoặc một token hủy có thread-safety phù hợp, đồng thời thoát trong thời gian hợp lý. Nhưng phải phân biệt:

1. **Future completion deadline** — khi caller được kết quả/lỗi.
2. **Cancellation request** — ai phát tín hiệu hủy, gửi tới đâu.
3. **Worker termination** — code thực thi thật đã dừng chưa.
4. **Resource cleanup** — connection, transaction, lock đã được đóng/hoàn tác phù hợp chưa.

Không có một lời gọi `completeOnTimeout` nào thực hiện đủ bốn trách nhiệm đó.

## 10. Tránh làm timeout che khuất kết quả và lỗi muộn

### 10.1. Kết quả muộn không tự được ghi vào Future đã hoàn tất

Nếu timeout/fallback đã thắng, một lần `Supplier` trả thành công sau đó thường không làm thay đổi giá trị caller thấy. Điều này đúng cho các completion bình thường của `CompletableFuture`: **first completion wins**. Tương tự, một exception xảy ra muộn trong Supplier có thể không còn hiển thị qua chính Future đã hoàn tất fallback.

Nếu tác vụ quan trọng, đừng chỉ log `future.whenComplete(...)` trên future đã dùng `completeOnTimeout` rồi cho rằng metric ấy phản ánh **thời gian chạy thật**. Nó có thể chạy lúc fallback xuất hiện, **trước khi worker kết thúc**.

Để đo công việc thật, instrument **bên trong Supplier** hoặc quanh thao tác mạng:

~~~java
// Fragment: ioExecutor và remoteClient là dependency đã có.
CompletableFuture<String> future =
        CompletableFuture.supplyAsync(() -> {
            long startedAt = System.nanoTime();
            try {
                return remoteClient.fetch();
            } finally {
                recordOperationDuration(System.nanoTime() - startedAt);
            }
        }, ioExecutor).completeOnTimeout("cached", 1, TimeUnit.SECONDS);
~~~

Đây là fragment, không phải chương trình độc lập; `recordOperationDuration` và `remoteClient` chỉ để minh họa. Metric đo trong `finally` chạy khi **Supplier thật đến cuối**, khác với metric deadline của Future. Nếu cần biết operation có bị hủy hay không, phải đo thêm trạng thái/cancellation từ thư viện I/O.

### 10.2. Nếu cần tách Future của operation khỏi Future phản hồi

Vì `orTimeout`/`completeOnTimeout` trả **chính Future đã gọi**, áp timeout lên `upstream` sẽ thay trạng thái của chính upstream.

Nếu cần giữ **một Future gốc** để theo dõi kết quả thật, có thể dùng `copy()` (có từ Java 9) trước khi đặt timeout ở bản sao:

~~~java
// Fragment: upstream là CompletableFuture<String> đang chờ operation.
CompletableFuture<String> response =
        upstream.copy()
                .completeOnTimeout("cached", 1, TimeUnit.SECONDS);
~~~

`response` là một Future khác được đặt deadline. Nếu fallback thắng, nó không làm `upstream` tự chuyển thành `"cached"`; upstream vẫn có thể hoàn tất với giá trị/thất bại thật. **Tuy nhiên bản sao cũng không hủy thao tác bên dưới**. Đây là cách tách ngữ nghĩa completion, không phải cách quản lý I/O resources.

Đừng dùng `obtrudeValue(...)` để cưỡng bức sửa kết quả đã timeout rồi xem như giải quyết được việc cập nhật muộn; đây là API đặc biệt có thể gây bất nhất với dependent stages và không phải cơ chế timeout/late-result thông thường.

### 10.3. Nhiều timeout trên cùng Future có thể cạnh tranh nhau

~~~java
// Fragment minh họa một cách cấu hình dễ gây nhầm.
future.orTimeout(1, TimeUnit.SECONDS);
future.completeOnTimeout("cached", 2, TimeUnit.SECONDS);
~~~

Hai lời gọi trên tác động lên **cùng một future**. Nếu chưa hoàn tất và bộ hẹn 1 giây chạy trước, Future đã lỗi timeout; fallback 2 giây sau **không chuyển nó thành thành công**. Đừng xem chúng là hai Future độc lập có chính sách riêng.

Nếu có ý định báo fallback sau 1 giây nhưng cũng muốn lưu kết quả thật sau này, hãy giữ original Future và tạo một **response future khác** hoặc thiết kế explicit callback, deadline và state machine có kiểm soát.

## 11. Hệ quả production: retry, idempotency, resource exhaustion

### 11.1. Retry chồng lên thao tác chưa kết thúc

Một rủi ro nghiêm trọng:

1. Caller gửi `chargePayment(orderId)`.
2. `completeOnTimeout("pending", 2, SECONDS)` trả `"pending"` khi thao tác remote chưa xong.
3. Caller hiểu nhầm task đã dừng và gửi lại.
4. Hai yêu cầu thanh toán có thể đang xử lý đồng thời.

**Timeout không chứng minh tác vụ thất bại hoặc bị rollback.** Với nghiệp vụ có side effect, cần idempotency key, deduplication, trạng thái “unknown/pending”, kiểm tra kết quả ở nguồn sự thật và quy trình reconciliation. Đừng tự retry một lệnh có side effect chỉ vì một Future timeout.

### 11.2. Worker/connection vẫn bị giữ dù response nhanh

Ví dụ API fallback trong 100 ms nhưng HTTP client còn timeout 60 giây: nếu request đến liên tục, những task đã “trả kết quả” vẫn có thể giữ phần lớn pool, gây **`TaskRejectedException`** khi pool/queue đầy. Vì vậy monitoring chỉ đếm `CompletableFuture` hoàn tất không đủ; cần đọc active worker, queue depth, in-flight calls và timeout thật của downstream.

### 11.3. Cấu hình timeout theo ngân sách

- **Deadline ở API boundary:** caller có thể phản hồi sau mốc này, nhưng phải ghi nhận trạng thái operation là chưa chắc đã kết thúc.
- **Request/read/query timeout:** dừng chờ I/O ở tầng thư viện có khả năng kiểm soát hoạt động thực.
- **Retry budget:** số lần thử, backoff, jitter, idempotency và tổng thời hạn; tránh retry storm.
- **Concurrency limit:** không để số thao tác chạy nền tiếp tục tăng sau khi caller đã timeout.
- **Shutdown/cancellation:** xác định task nào phải chạy đến cùng, task nào được phép bỏ và giao thức giải phóng tài nguyên.

Không có một giá trị timeout “tối ưu chung” cho mọi hệ thống; cần đo p95/p99 latency, tỉ lệ timeout, tỉ lệ false fallback, queue wait và tải downstream.

## 12. Bảng xử lý lỗi đúng tầng

| Sự kiện | Future/caller nhận gì | Tầng cần xử lý |
| --- | --- | --- |
| Supplier hoàn tất trước deadline | Giá trị thật | Nghiệp vụ thông thường |
| `orTimeout` thắng | Future hoàn tất exceptionally với `TimeoutException` | Caller/response policy, metric deadline |
| `completeOnTimeout` thắng | Future hoàn tất bình thường với fallback | Gắn nhãn fallback, độ mới dữ liệu, cảnh báo |
| `get(timeout)` hết hạn | Caller nhận `TimeoutException`, Future **có thể còn pending** | Caller quyết định chờ lại, hủy hay thoát |
| Task đã được nhận nhưng ném lỗi | Future có thể hoàn tất exceptionally | `handle`/`exceptionally`, phân loại cause |
| Executor từ chối nộp task | Lỗi có thể ném **ngay tại `supplyAsync/submit`** | Catch quanh submission, capacity/lifecycle |
| Caller/worker bị interrupt | `InterruptedException` tại API chặn phù hợp, nếu implementation hỗ trợ | Phục hồi interrupt, cleanup hợp tác |
| Client HTTP đã ngắt kết nối | Future không tự biết mọi tài nguyên/remote work phải dừng | Request lifecycle và cancellation policy |

Với `ThreadPoolTaskExecutor`, rejection thường là `TaskRejectedException`, kế thừa `RejectedExecutionException` của JDK. Nó có thể xuất hiện **trước khi nhận được Future**; `future.exceptionally(...)` không thể xử lý một lời gọi chưa trả về Future.

Nếu task trả `CompletableFuture<T>` và đã hoàn tất exceptionally, `join()` thường ném unchecked `CompletionException` với cause gốc; `get()` thường ném checked `ExecutionException`. Với `orTimeout`, cause cần phân biệt là `TimeoutException`, không phải mọi exception đều do deadline.

## 13. Checklist kiểm thử và vận hành

### 13.1. Test hai trạng thái độc lập

1. Dùng latch để giữ Supplier **chưa hoàn tất**.
2. Đặt `orTimeout` và xác nhận Future hoàn tất lỗi `TimeoutException`, **worker vẫn chờ latch**.
3. Đặt `completeOnTimeout` ở một lần chạy khác và xác nhận Future nhận fallback trong khi Supplier vẫn chờ.
4. Mở latch và xác nhận Supplier đi tới `finally`; Future đã timeout/fallback **không đổi theo kết quả muộn**.
5. Dùng `get(timeout)` trên một Future chưa có timeout để xác nhận Future vẫn pending, rồi lấy được kết quả sau khi mở latch.
6. Test nhánh operation hoàn tất **trước** deadline để bảo đảm kết quả thật được giữ.
7. Test exception thật, rejection, interrupt, cancellation và cleanup; tránh test bằng `sleep` ngắn rồi assert millisecond tuyệt đối.
8. Kiểm tra connection/worker thật được giải phóng, không chỉ `isDone()` của Future.

### 13.2. Những số liệu nên theo dõi

- **Future deadline/fallback rate:** bao nhiêu response bị timeout hoặc dùng dữ liệu dự phòng.
- **Underlying operation duration:** HTTP/DB/SDK gọi bao lâu, kể cả sau khi response Future hoàn tất.
- **In-flight operations, active workers, pool size, queue depth:** công việc còn chiếm tài nguyên.
- **Rejection rate và downstream saturation:** tài nguyên cạn, queue đầy hoặc executor shutdown.
- **Late completion/late errors:** số operation về sau deadline; metric này không thể suy ra từ future đã hoàn tất sớm.
- **Business correctness:** duplicate operations, idempotency conflicts và giao dịch ở trạng thái không rõ kết quả.

### 13.3. Quy tắc lựa chọn

- Dùng `get(timeout)` nếu chỉ muốn **giới hạn thời gian caller đợi** và vẫn giữ Future gốc có khả năng hoàn tất sau.
- Dùng `orTimeout` nếu hợp đồng muốn **Future tự chuyển thành thất bại** ở mốc deadline và caller/dependent stages thấy lỗi.
- Dùng `completeOnTimeout` nếu nghiệp vụ chấp nhận **trả giá trị dự phòng** như kết quả của Future.
- Dùng **timeout của I/O thực, cancellation API và giới hạn concurrency** nếu cần giải phóng tài nguyên, tránh side effect muộn hoặc giảm áp lực lên thread pool.
- Dùng **bản sao hoặc Future phản hồi riêng** nếu muốn quan sát kết quả thật của operation mà không cho timeout làm mất trạng thái upstream.

## 14. Sai lầm thường gặp

1. **“`orTimeout` interrupt thread khi hết hạn.”** Sai. Nó hoàn tất `CompletableFuture` với lỗi; worker có thể vẫn chạy.
2. **“`completeOnTimeout` dừng HTTP rồi mới trả fallback.”** Sai. Nó chỉ hoàn tất Future với một giá trị nếu deadline thắng.
3. **“`future.get(timeout)` cũng sửa trạng thái Future thành timeout.”** Sai. Nó chỉ giới hạn một lần caller đợi.
4. **“`completeOnTimeout` trả Future mới, giữ nguyên Future cũ.”** Sai. Javadoc ghi **returns this CompletableFuture**.
5. **“Future đã `isDone` thì worker và connection chắc chắn đã được giải phóng.”** Sai khi timeout/fallback đã hoàn tất Future trước operation.
6. **“Có fallback thì không cần log lỗi muộn của task.”** Sai. Exception trong Supplier có thể xảy ra sau khi Future đã có fallback; cần quan sát operation thật.
7. **“`cancel(true)` trên CompletableFuture nhất định sẽ interrupt worker.”** Sai. Cờ `mayInterruptIfRunning` không điều khiển interrupt trong implementation này.
8. **“Đặt nhiều timeout trên cùng Future là tạo nhiều nhánh độc lập.”** Sai. Các lời gọi instance sửa cùng đối tượng.
9. **“Tăng thread pool là hết vấn đề.”** Sai. Downstream HTTP/SQL có thể tiếp tục quá lâu, làm cạn kết nối và nhân tải.
10. **“Retry sau timeout chắc chắn an toàn vì lần cũ đã dừng.”** Sai. Cần idempotency và xác minh trạng thái giao dịch.
11. **“Nếu `whenComplete` của Future đã chạy thì operation thật đã kết thúc.”** Sai khi timeout/fallback xảy ra trước Supplier completion.
12. **“Cần đổi sang `@Async` là mọi timeout đều tự ngắt operation.”** Sai. Spring async executor không thay đổi hợp đồng của `CompletableFuture` về timeout.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** `orTimeout(1, SECONDS)` hoàn tất Future với `TimeoutException`. SQL query bên trong Supplier có chắc đã dừng không?  
**Trả lời:** Không. Future completion không tự phát lệnh hủy SQL. Cần query timeout/cancellation ở tầng database/JDBC.

**Câu 2.** `completeOnTimeout("cached", 1, SECONDS)` thắng, sau đó Supplier trả `"fresh"`. `future.join()` trả gì?  
**Trả lời:** `"cached"`, vì Future đã hoàn tất bình thường với fallback; kết quả tới muộn không ghi đè completion trước.

**Câu 3.** `future.get(1, SECONDS)` ném `TimeoutException`, nhưng Future chưa gắn `orTimeout`. Có thể chờ tiếp và nhận kết quả thật không?  
**Trả lời:** Có, nếu task sau đó hoàn tất; timed `get` không hoàn tất/hủy Future.

**Câu 4.** `orTimeout` và `completeOnTimeout` trả về đối tượng nào?  
**Trả lời:** **Chính `CompletableFuture` được gọi**, không phải một bản sao.

**Câu 5.** `future.isDone()` đúng ngay sau `completeOnTimeout`. Có thể kết luận worker không còn hoạt động không?  
**Trả lời:** Không. `isDone` chỉ phản ánh Future; worker có thể đang giữ socket, DB connection hoặc tiếp tục side effect.

**Câu 6.** `CompletableFuture.cancel(true)` có bảo đảm interrupt Supplier không?  
**Trả lời:** Không. Java 17 Javadoc nêu `mayInterruptIfRunning` không có tác dụng điều khiển processing của `CompletableFuture`.

**Câu 7.** Nếu cần giữ kết quả thực sau timeout nhưng caller chỉ chờ tối đa 1 giây, lựa chọn nào đơn giản?  
**Trả lời:** Có thể dùng `get(1, SECONDS)` để giới hạn lần đợi, hoặc tạo `upstream.copy().completeOnTimeout(...)` để tách Future phản hồi khỏi upstream. Cả hai **không tự hủy task**.

**Câu 8.** Tại sao metric đếm Future hoàn tất không đủ để phát hiện quá tải?  
**Trả lời:** Future có thể đã hoàn tất sớm do deadline/fallback dù các operations thật vẫn đang giữ worker và connection; cần đo underlying in-flight work, queue và resource usage.

## 16. Tài liệu tham khảo chính thức

- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): `orTimeout`, `completeOnTimeout` (trả `this`), `copy`, `cancel`, completion semantics và `join`.
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): `get(timeout)`, `cancel`, `isDone`, interrupt và cơ chế quan sát kết quả.
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): nộp task, `shutdown`, `Future` và lifecycle executor.
- [Java SE 17 — HttpRequest.Builder](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpRequest.Builder.html): timeout ở tầng yêu cầu HTTP đối với Java HTTP client.
- [Java SE 17 — Statement](https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/Statement.html): `setQueryTimeout` và khả năng driver xử lý query timeout.
- [Spring Framework 6.1.3 — AsyncTaskExecutor](https://www.springframework.org/spring/docs/6.1.3/javadoc-api/org/springframework/core/task/AsyncTaskExecutor.html): `submitCompletable` (từ Spring 6.0), `Future` và rejection.
- [Spring Framework 6.1.3 — ThreadPoolTaskExecutor](https://www.springframework.org/spring/docs/6.1.3/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): cấu hình thread pool, queue, exception và lifecycle.
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): lựa chọn Spring `TaskExecutor`, `@Async`, quản lý pool và xử lý tác vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="timeout-cancellation-004">Future.cancel(true) và cơ chế ngắt có tính hợp tác</a>

<details>
<summary>Xem chi tiết</summary>

# Future.cancel(true) và cơ chế ngắt có tính hợp tác

**Hủy một Future không đồng nghĩa với cưỡng bức dừng một luồng Java.** Đây là nguyên tắc quan trọng nhất khi xử lý tác vụ hết hạn, người dùng hủy yêu cầu hoặc ứng dụng đóng bộ thực thi trong **Spring Framework - concurrency**.

Với Future được tạo bởi **ExecutorService.submit** và thường được triển khai bằng **FutureTask**, lời gọi **cancel(true)** cố chuyển Future sang trạng thái hủy; nếu công việc đã bắt đầu và implementation biết thread thực thi, nó có thể gửi yêu cầu **Thread.interrupt()** đến thread đó. Việc công việc có thực sự dừng nhanh và giải phóng tài nguyên hay không **phụ thuộc vào code của chính công việc và những API nó đang chờ**.

Tương phản rất quan trọng: **CompletableFuture.cancel(true)** của Java chuẩn coi hủy như một dạng **exceptional completion**; tham số true **không có tác dụng ngắt computation** trong implementation này. Vì thế không thể nhìn thấy tên phương thức cancel giống nhau rồi kết luận hai implementation có cùng hành vi đối với worker.

Bài học thuộc nhóm **Hết hạn và hủy**. Sau bài này, bạn cần có thể phân biệt trạng thái Future với sự sống còn của task, viết vòng lặp có kiểm tra ngắt, xử lý InterruptedException đúng, thiết kế deadline/cancellation trong Spring @Async và kiểm thử bằng đồng bộ xác định thay vì dựa vào Thread.sleep đoán thời điểm.

## 1. Ba khái niệm khác nhau: Future, tác vụ và luồng

Khi một request gửi tác vụ vào ThreadPoolTaskExecutor hoặc ExecutorService, có ít nhất ba thực thể:

1. **Future**: đối tượng đại diện cho kết quả hoặc trạng thái hủy/hoàn tất theo hợp đồng API.
2. **Task**: logic thực sự chạy; có thể đang nằm trong queue, đang thực thi, bị chặn I/O hoặc sắp kết thúc.
3. **Worker thread**: tài nguyên thực thi task; trong thread pool, worker thường được **tái sử dụng** cho những task tiếp theo.

~~~text
HTTP caller                         ExecutorService / ThreadPool
     |                                        |
     +-- submit(callable) ------------------->| queue/task
     |                                        |     |
     |<------------------------ Future<T> ----+     v
     |                                          worker chạy task
     |
     +-- future.cancel(true)
     |          |
     |          +-- đề nghị hủy Future
     |          +-- có thể interrupt worker nếu implementation hỗ trợ
     |
     +-- future.isCancelled() == true
                        |
                        +-- KHÔNG chứng minh worker đã ngừng mọi side effect
~~~

**isDone() == true** chỉ cho biết Future đã ở trạng thái hoàn tất theo hợp đồng, bao gồm **thành công, lỗi hoặc hủy**; đây không phải câu trả lời “mọi mã Java bên trong task đã thoát và đã dọn tài nguyên”.

Khi thiết kế nghiệp vụ, cần tách:

- **Cancellation requested**: một thành phần yêu cầu ngừng.
- **Future cancelled**: đối tượng Future đã đánh dấu hủy.
- **Execution stopped**: mã task đã thoát vòng xử lý hoặc kết thúc đoạn chặn.
- **Cleanup complete**: tài nguyên, lock, connection, file và trạng thái nghiệp vụ đã được thu hồi/xử lý.
- **Business effect committed**: tác động bên ngoài, ví dụ đã chuyển tiền hoặc ghi DB, có thể không đảo ngược chỉ vì caller hủy Future.

Một ứng dụng chỉ nhìn Future.isCancelled() để xác nhận tất cả các mức trên là sai về thiết kế.

## 2. Hợp đồng Future.cancel(mayInterruptIfRunning)

Java khai báo:

~~~java
boolean cancel(boolean mayInterruptIfRunning);
boolean isCancelled();
boolean isDone();
V get() throws InterruptedException, ExecutionException;
V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;
~~~

Đây là **chữ ký tóm lược của interface Future<V>**, không phải file Java biên dịch độc lập.

Theo hợp đồng **Future**:

- Nếu công việc chưa bắt đầu, khi hủy thành công nó **không nên được chạy**.
- Nếu công việc đã bắt đầu, **mayInterruptIfRunning = true** cho phép implementation **yêu cầu interrupt thread đang thực thi**, khi implementation biết thread đó.
- Nếu **mayInterruptIfRunning = false**, không yêu cầu ngắt thread đang chạy; công việc đang thực thi có thể tiếp tục.
- Nếu task đã hoàn thành, việc hủy thường không còn tác dụng.
- Giá trị **boolean** do cancel trả về phản ánh kết quả nỗ lực hủy theo implementation; **đừng dùng riêng nó để suy luận worker đã dừng**. Dùng isCancelled() để xem trạng thái Future.
- Sau khi Future được hủy, **get()** hoặc **get(timeout, unit)** sẽ ném **CancellationException** (unchecked), không trả về kết quả tính toán.
- **isDone()** trả true cho Future đã hoàn tất bình thường, lỗi hoặc bị hủy; một task không thể được coi là “thành công” chỉ từ isDone().

### So sánh cancel(false) và cancel(true)

| Tình huống | cancel(false) | cancel(true) |
| --- | --- | --- |
| Task chưa bắt đầu | Có thể đánh dấu hủy để body không chạy | Tương tự |
| Task đang chạy trong FutureTask | Có thể đánh dấu Future đã hủy nhưng **không gửi interrupt** | Cố **interrupt** thread chạy task |
| Code đang chặn bằng sleep/wait/CountDownLatch.await | Không nhờ tham số false để đánh thức | Interrupt có thể làm API ném InterruptedException |
| Code tính toán CPU không kiểm tra cờ ngắt | Có thể tiếp tục chạy | **Vẫn có thể tiếp tục chạy** |
| Code có side effect đã commit | Không hoàn tác side effect | Không hoàn tác side effect |
| CompletableFuture chuẩn | Đánh dấu Future bị hủy | **Cùng semantics; true không điều khiển interrupt** |

**Đừng nhầm cancel(false) với “không thể hủy task đang chạy”.** Chẳng hạn, FutureTask có thể chuyển trạng thái Future sang cancelled mà **cho phép phần computation đang chạy tiếp tục**. Kết quả sau cùng không được Future trả cho caller, nhưng side effect bên trong task vẫn có thể xảy ra.

### Các trạng thái minh họa

~~~text
NEW (queued) --cancel--> CANCELLED  (body không nên chạy)
  |
  +--worker starts--> RUNNING --return--> SUCCESS
                            |
                            +--throws--> FAILURE
                            |
                            +--cancel(true)--> Future CANCELLED
                                                |
                                                +--worker may continue
                                                +--or cooperate and exit
~~~

Đây là mô hình khái niệm, **không phải enum trạng thái chuẩn của mọi Future implementation**.

## 3. Thread.interrupt() là tín hiệu, không phải Thread.stop()

Một lệnh interrupt gửi **yêu cầu hợp tác** tới một thread; nó không an toàn nếu hiểu như “tiêu diệt thread ngay lập tức”.

Khi Thread.interrupt() xảy ra, thường có hai tình huống:

1. **Thread đang chờ trong một API nhạy với interrupt**: ví dụ Thread.sleep(), Object.wait(), Thread.join() hoặc CountDownLatch.await(). API thường ném **InterruptedException**, đồng thời **xóa interrupt flag** theo hợp đồng của API.
2. **Thread đang chạy code CPU hoặc API không đáp ứng interrupt**: cờ interrupt được đặt; code phải **kiểm tra và thoát** tại điểm hợp lý, hoặc API đang chặn phải hỗ trợ interrupt theo hợp đồng riêng.

Interrupt có thể có hiệu ứng khác với NIO InterruptibleChannel, Selector và một số blocking operation chuyên biệt. Không được mặc định mọi socket client, JDBC driver, HTTP client hoặc native call đều ngừng ngay khi interrupt.

**Không dùng Thread.stop()** để “ép” task dừng. Cưỡng bức dừng luồng có thể để lại invariants không nhất quán và lock/tài nguyên ở trạng thái nguy hiểm. Thiết kế đúng thường yêu cầu signal + điểm kiểm tra ngắt + cleanup có chủ đích.

### Cờ interrupt và hai cách kiểm tra

~~~java
boolean interrupted = Thread.currentThread().isInterrupted();

boolean wasInterruptedAndClearFlag = Thread.interrupted();
~~~

Đây là **hai dòng minh họa**, không phải file Java độc lập.

- **isInterrupted()**: kiểm tra trạng thái mà **không xóa cờ**.
- **Thread.interrupted()**: phương thức static kiểm tra trạng thái của **thread hiện tại** và **xóa cờ interrupt**.

Vì vậy, trong một vòng CPU-bound có điều kiện ngắt, thường dùng:

~~~java
while (!Thread.currentThread().isInterrupted()) {
    performSmallUnitOfWork();
}
~~~

Đây là **đoạn minh họa** với performSmallUnitOfWork đã được định nghĩa. Nếu code dùng Thread.interrupted() nhiều lần không có lý do, nó có thể tiêu thụ tín hiệu ngắt rồi khiến tầng khác không quan sát được yêu cầu hủy.

## 4. InterruptedException: phải xử lý để hợp tác dừng

Nhiều API chặn ở Java có chữ ký **throws InterruptedException**. Khi nhận exception này, một trong hai hướng đúng thường là:

**A. Nếu method có thể khai báo throws InterruptedException, hãy truyền lên tầng sở hữu chính sách:**

~~~java
void waitForSignal(CountDownLatch latch)
        throws InterruptedException {
    latch.await();
}
~~~

Đây là **đoạn method minh họa** có import CountDownLatch. Việc truyền exception lên cho phép caller quyết định hủy và cleanup.

**B. Nếu không thể truyền InterruptedException (ví dụ run() của Runnable), hãy khôi phục cờ ngắt rồi thoát hợp lý:**

~~~java
@Override
public void run() {
    try {
        blockingOperation();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return; // Không coi gián đoạn là công việc hoàn tất thành công.
    } finally {
        releaseOwnedResources();
    }
}
~~~

Đây là **đoạn minh họa** trong class có blockingOperation và releaseOwnedResources.

Giải thích việc khôi phục cờ: nhiều API như **sleep/await** xóa interrupt status khi ném InterruptedException. Nếu không truyền exception lên được, gọi **Thread.currentThread().interrupt()** giúp bảo toàn thông tin có yêu cầu ngắt trước khi kết thúc method. **Không tiếp tục làm nghiệp vụ** như chưa có chuyện gì xảy ra.

**Cách sai:**

~~~java
try {
    Thread.sleep(1_000);
} catch (InterruptedException ignored) {
    // Nuốt mất tín hiệu và tiếp tục vòng lặp vô hạn.
}
~~~

Nếu đây là vòng lặp chạy nhiều lần, mỗi lần interrupt có thể bị nuốt rồi task vẫn tiếp tục giữ worker. Caller tưởng đã hủy nhưng hệ thống vẫn ghi log, gọi DB hoặc gửi request.

### Khi nào không nên đơn giản re-interrupt rồi tiếp tục?

- Khi method có thể **throw InterruptedException**: để caller có chính sách hủy rõ ràng thường tốt hơn.
- Khi phải hoàn tất một bước cleanup quan trọng có thể bị interrupt: đặt chiến lược cleanup cụ thể, deadline và quy tắc khôi phục cờ.
- Khi task sở hữu transaction/connection: tránh tiếp tục commit logic nghiệp vụ sau khi đã nhận hủy, trừ khi hợp đồng nghiệp vụ yêu cầu hoàn tất một bước đã bắt đầu.

Một catch chỉ ghi log “interrupted” rồi **không ngừng công việc** thường phá hợp đồng cancellation.

## 5. Ví dụ Java 17 đầy đủ: cancel(true) đánh thức task đang chặn

Ví dụ đầu tiên dùng **ExecutorService.submit()**, trả về Future của task, rồi hủy bằng **cancel(true)**. Task dùng CountDownLatch.await() để chờ; khi nhận interrupt, nó ghi nhận tín hiệu, khôi phục cờ ngắt, thoát và chạy cleanup trong finally.

Tạo file **CooperativeCancelDemo.java**:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CooperativeCancelDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean sawInterrupt = new AtomicBoolean(false);
        AtomicBoolean cleanup = new AtomicBoolean(false);

        try {
            Future<String> future = pool.submit(() -> {
                started.countDown();
                try {
                    neverReleased.await();
                    return "unreachable";
                } catch (InterruptedException e) {
                    sawInterrupt.set(true);
                    Thread.currentThread().interrupt();
                    return "ignored-result";
                } finally {
                    cleanup.set(true);
                    finished.countDown();
                }
            });

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker chưa bắt đầu");
            }

            boolean accepted = future.cancel(true);

            if (!finished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Worker không thoát sau yêu cầu ngắt");
            }

            boolean cancelledGet = false;
            try {
                future.get();
            } catch (CancellationException expected) {
                cancelledGet = true;
            }

            System.out.println("cancel(true) thành công: " + accepted);
            System.out.println("Future isCancelled: "
                    + future.isCancelled());
            System.out.println("Future isDone: " + future.isDone());
            System.out.println("Worker thấy interrupt: "
                    + sawInterrupt.get());
            System.out.println("Cleanup đã chạy: " + cleanup.get());
            System.out.println("get ném CancellationException: "
                    + cancelledGet);
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Pool chưa kết thúc");
            }
        }
    }
}
~~~

Biên dịch và chạy bằng **JDK 17+**:

~~~bash
javac -encoding UTF-8 CooperativeCancelDemo.java
java CooperativeCancelDemo
~~~

**Stdout**, khi môi trường cho worker khởi động và thoát trong các thời hạn test:

~~~text
cancel(true) thành công: true
Future isCancelled: true
Future isDone: true
Worker thấy interrupt: true
Cleanup đã chạy: true
get ném CancellationException: true
~~~

**Vì sao ví dụ xác định?**

1. Main đợi **started**, bảo đảm task đã thực sự bắt đầu trước khi gọi cancel.
2. Task chờ ở **CountDownLatch.await()**, nên không thể tự hoàn thành nếu không có interrupt hoặc không có ai mở latch.
3. **FutureTask** do ExecutorService.submit điển hình dùng có khả năng interrupt thread đang chạy. **cancel(true)** gửi tín hiệu.
4. Lệnh await đáp ứng interrupt bằng InterruptedException; task đánh dấu **sawInterrupt**, chạy finally, rồi dừng.
5. Main đợi **finished** trước khi kiểm tra cleanup. Vì thế stdout **không bị race** giữa thread đang cleanup và thread đang in.
6. **future.get()** ném CancellationException vì trạng thái Future đã bị hủy. Giá trị **ignored-result** mà Callable có thể trả về **không được công bố như một kết quả thành công**.
7. Main đóng executor trong finally để tránh rò rỉ worker sau ví dụ.

**Giới hạn:** các boolean này xác nhận hành vi trong ví dụ đang dùng FutureTask và CountDownLatch. Chúng **không** là lời hứa rằng cancel(true) sẽ làm một lời gọi JDBC, HTTP client hoặc native I/O cụ thể ngừng ngay lập tức.

## 6. cancel(true) không đủ nếu code CPU không kiểm tra ngắt

Một tác vụ CPU-bound như parse file lớn, duyệt hàng triệu phần tử hoặc tính toán theo batch có thể chạy mà không gọi bất kỳ hàm chặn nào ném InterruptedException. Khi đó, interrupt chủ yếu làm **cờ interrupt của thread được đặt**. Nếu vòng lặp không kiểm tra cờ hoặc cancellation token, công việc có thể tiếp tục cho đến khi tự kết thúc.

Ví dụ về một vòng lặp hợp tác:

~~~java
long calculate(List<Integer> numbers) {
    long total = 0;

    for (int i = 0; i < numbers.size(); i++) {
        if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException(
                    "Bị yêu cầu dừng");
        }

        total += numbers.get(i);
    }

    return total;
}
~~~

Đây là **đoạn method minh họa**; cần import java.util.List. Vòng lặp kiểm tra interrupt ở mỗi bước, sau đó dừng tại checkpoint an toàn. Đối với phép toán rất nhỏ, kiểm tra cờ quá dày có thể tốn overhead; có thể kiểm tra theo từng khối, miễn deadline và khả năng dừng được chấp nhận.

**Điểm chính:** cancellation là một protocol giữa phía yêu cầu và phía xử lý. Đặt checkpoint tại ranh giới nghiệp vụ có thể dừng an toàn, không phải ở giữa một thao tác cần hoàn thành nguyên tử.

### Đừng suy từ Future.isDone() rằng vòng lặp đã thoát

Nếu một FutureTask được cancel trong khi task vẫn đang chạy một vòng lặp không kiểm tra cờ, **Future.isDone() có thể là true**, còn worker vẫn sử dụng CPU. Từ thời điểm bị cancel, Future có thể không còn cung cấp một kết quả hay tín hiệu “worker thật sự đã dừng” đáng tin. Nếu cần xác nhận cleanup, phải có **một tín hiệu hoàn tất thực thi riêng** hoặc quan sát lifecycle/resource.

## 7. Ví dụ Java 17: Future đã hủy nhưng task vẫn còn chạy

Ví dụ sau cố ý **bỏ qua cờ ngắt** trong vòng CPU để chứng minh khác biệt giữa **Future cancellation** và **kết thúc thực thi**. Nó có một AtomicBoolean riêng để main giải phóng task, tránh giữ worker vô hạn.

Tạo file **NonCooperativeCancelDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class NonCooperativeCancelDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean(false);

        try {
            Future<?> future = pool.submit(() -> {
                started.countDown();
                try {
                    while (!stop.get()) {
                        // Cố ý không kiểm tra interrupt.
                        Thread.onSpinWait();
                    }
                } finally {
                    finished.countDown();
                }
            });

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker chưa bắt đầu");
            }

            boolean cancelled = future.cancel(true);

            System.out.println("Future cancel(true): " + cancelled);
            System.out.println("Future isDone: " + future.isDone());
            System.out.println("Future isCancelled: "
                    + future.isCancelled());
            System.out.println("Worker vẫn đang chạy: "
                    + (finished.getCount() == 1));

            // Tín hiệu riêng cho phép vòng lặp kết thúc.
            stop.set(true);

            if (!finished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker chưa thoát");
            }

            System.out.println("Worker đã kết thúc: true");
        } finally {
            stop.set(true);
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Pool chưa kết thúc");
            }
        }
    }
}
~~~

Lệnh:

~~~bash
javac -encoding UTF-8 NonCooperativeCancelDemo.java
java NonCooperativeCancelDemo
~~~

**Stdout** trong điều kiện thông thường:

~~~text
Future cancel(true): true
Future isDone: true
Future isCancelled: true
Worker vẫn đang chạy: true
Worker đã kết thúc: true
~~~

**Giải thích:**

- started xác nhận Runnable đã vào vòng xử lý trước khi main cancel.
- **cancel(true)** chuyển Future của công việc sang cancelled và có thể đặt interrupt flag trên worker.
- **Vòng lặp chỉ quan sát stop**, không kiểm tra cờ interrupt; nó tiếp tục chạy.
- Main kiểm tra **finished.getCount() == 1** trước khi set stop, nên có bằng chứng worker chưa vào finally ở thời điểm Future đã cancelled.
- Khi main set stop=true, worker thoát, đếm ngược finished và công việc vật lý kết thúc.

Đây là ví dụ **cố ý tiêu cực** nhằm minh họa điều không nên làm ở production: một vòng spin tiêu tốn CPU. Nó chỉ chạy rất ngắn trong test và có finally để giải phóng worker. Vòng tính toán thật nên dùng checkpoint hợp tác và không dùng busy-spin làm cơ chế chờ.

## 8. Hủy một tác vụ còn trong queue

FutureTask có thể được hủy **trước khi công việc bắt đầu**. Một worker sau đó lấy wrapper đã bị hủy từ queue thì phần Callable/Runnable thực sự không nên chạy.

Với **ExecutorService.newSingleThreadExecutor**, có thể kiểm chứng bằng cách giữ worker ở task đầu tiên, đẩy task thứ hai vào queue rồi cancel task thứ hai. Đây là cơ chế khác với interrupt thread đang chạy.

Tạo file **CancelQueuedDemo.java**:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CancelQueuedDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean secondRan = new AtomicBoolean(false);

        try {
            Future<?> first = pool.submit(() -> {
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            if (!firstStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Task đầu chưa chạy");
            }

            Future<?> queued = pool.submit(
                    () -> secondRan.set(true));

            boolean cancelled = queued.cancel(false);

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);

            // Barrier phía sau task bị hủy: xác nhận worker đã
            // xử lý hết các mục queue gửi trước barrier.
            Future<?> barrier = pool.submit(() -> { });
            barrier.get(5, TimeUnit.SECONDS);

            boolean getCancelled = false;
            try {
                queued.get();
            } catch (CancellationException expected) {
                getCancelled = true;
            }

            System.out.println("Hủy trước khi chạy: " + cancelled);
            System.out.println("Body task thứ hai đã chạy: "
                    + secondRan.get());
            System.out.println("get báo hủy: " + getCancelled);
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Pool chưa kết thúc");
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 CancelQueuedDemo.java
java CancelQueuedDemo
~~~

**Stdout:**

~~~text
Hủy trước khi chạy: true
Body task thứ hai đã chạy: false
get báo hủy: true
~~~

**Ý nghĩa:** cancel(false) vẫn có thể hủy một task **chưa bắt đầu**. Không cần yêu cầu interrupt worker, bởi worker đầu tiên vẫn đang xử lý một task khác. Sau khi releaseFirst mở, wrapper task thứ hai đã hủy có thể được lấy khỏi hàng đợi nhưng **body không được chạy**.

**Đừng đồng nhất task không chạy với queue đã sạch ngay lập tức.** Việc hủy một FutureTask không nhất thiết xóa wrapper của nó ra khỏi **mọi implementation hàng đợi ngay tại thời điểm cancel**. Các loại executor (đặc biệt scheduled executor) có chính sách loại task hủy riêng. Cần quan sát queue retention/cleanup theo loại executor, không dựa vào một quy tắc chung.

## 9. Khác biệt quan trọng: CompletableFuture.cancel(true) không gửi interrupt

**CompletableFuture** của Java SE 17 cũng implements Future, nhưng Javadoc nêu rõ: cancellation ở implementation này tương đương một dạng **exceptional completion bằng CancellationException**, còn tham số **mayInterruptIfRunning không có tác dụng** vì interrupts không được dùng để điều khiển phần processing.

Ví dụ:

~~~java
CompletableFuture<String> result =
        CompletableFuture.supplyAsync(
                () -> blockingComputation(), executor);

result.cancel(true);
~~~

Đây là **đoạn minh họa** với blockingComputation và executor trong scope. Bạn **không thể suy ra** việc gọi cancel(true) trên CompletableFuture đã interrupt worker của blockingComputation. Kết quả Future có thể bị đánh dấu canceled, trong khi supplier vẫn chạy và có side effect.

### Ví dụ Java 17 xác định

Tạo file **CompletableCancelDemo.java**:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CompletableCancelDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean gotInterruptedException = new AtomicBoolean(false);

        try {
            CompletableFuture<String> future =
                    CompletableFuture.supplyAsync(() -> {
                        started.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            gotInterruptedException.set(true);
                            Thread.currentThread().interrupt();
                        } finally {
                            finished.countDown();
                        }
                        return "physical-computation-finished";
                    }, pool);

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker chưa bắt đầu");
            }

            boolean cancelled = future.cancel(true);

            System.out.println("CompletableFuture cancel: "
                    + cancelled);
            System.out.println("CompletableFuture isCancelled: "
                    + future.isCancelled());
            System.out.println("Worker chưa thoát await: "
                    + (finished.getCount() == 1));

            release.countDown();

            if (!finished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker chưa kết thúc");
            }

            boolean getCancelled = false;
            try {
                future.get();
            } catch (CancellationException expected) {
                getCancelled = true;
            }

            System.out.println("Worker bị interrupt do cancel: "
                    + gotInterruptedException.get());
            System.out.println("get báo CancellationException: "
                    + getCancelled);
        } finally {
            release.countDown();
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Pool chưa kết thúc");
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 CompletableCancelDemo.java
java CompletableCancelDemo
~~~

**Stdout:**

~~~text
CompletableFuture cancel: true
CompletableFuture isCancelled: true
Worker chưa thoát await: true
Worker bị interrupt do cancel: false
get báo CancellationException: true
~~~

**Diễn giải:**

1. Supplier chờ ở release.await, và main biết worker đã chạy từ started.
2. **future.cancel(true)** đổi trạng thái CompletableFuture thành canceled, **không interrupt worker** trong implementation chuẩn.
3. Worker vẫn đang đợi release, nên finished chưa về 0.
4. Main tự mở release để supplier kết thúc; việc supplier có trả một String không đổi trạng thái **đã canceled** của future.
5. **get()** ném CancellationException; output không phải physical-computation-finished.

**Không nên biến điều này thành nhận định “mọi API trả CompletableFuture không bao giờ có thể interrupt”.** Một framework/library có thể tạo thêm cơ chế cancellation bên ngoài hoặc một Future khác có quyền điều khiển task. Điều được chứng minh ở đây là **lời gọi CompletableFuture.cancel(true) trên implementation JDK chuẩn không tự gửi interrupt**.

## 10. Tác động lên các dependent stages của CompletableFuture

Một pipeline có nhiều bước:

~~~java
CompletableFuture<Integer> source =
        CompletableFuture.supplyAsync(
                () -> loadValue(), ioExecutor);

CompletableFuture<String> output = source
        .thenApply(n -> n * 2)
        .thenApplyAsync(n -> "score:" + n, cpuExecutor);
~~~

Đây là **đoạn minh họa** với các dependency trong scope.

Nếu hủy **source** trước khi source hoàn tất, những dependent stages chưa hoàn tất sẽ bị đưa vào **exceptional completion** theo semantics của CompletableFuture; có thể có **CompletionException với CancellationException làm nguyên nhân**.

Nếu hủy **output**, đó là thay đổi trạng thái của stage output. **Không mặc nhiên coi việc hủy output là lời yêu cầu interrupt/tự hủy toàn bộ công việc upstream**. Nhiều stage đã lên lịch vẫn có thể đang chạy hoặc có side effect.

Điều này ảnh hưởng tới việc sử dụng:

- **thenApply/thenCompose**: composition tạo quan hệ completion, không tự tạo một protocol physical stop cho toàn bộ cây task.
- **allOf**: tổng hợp completion của các futures, không phải “cancel all children” tự động.
- **anyOf**: một nhánh hoàn tất không mặc nhiên dừng những nhánh còn lại.
- **orTimeout**: chuyển trạng thái future khi quá hạn, **không tự interrupt các nguồn công việc đang chạy**.
- **completeOnTimeout**: có thể đặt giá trị fallback khi hết hạn, nhưng không tự dừng supplier I/O ở phía dưới.
- **exceptionally/handle**: hồi phục giá trị theo lỗi completion, không biến một việc đang chạy thành đã ngừng một cách vật lý.

Muốn **cancel nhiều công việc con**, bạn phải quản lý riêng các handles có khả năng cancel, deadline và tài nguyên của từng công việc. Nếu API bên dưới cung cấp cơ chế cancel riêng, gọi nó theo hợp đồng của API ấy và thu thập xác nhận cleanup thích hợp.

## 11. Timeout không đồng nghĩa cancellation

Một caller có thể chờ Future:

~~~java
try {
    return future.get(2, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    // Caller không còn muốn đợi quá 2 giây.
}
~~~

Đây là **đoạn minh họa** trong phương thức có future và kiểu dữ liệu trả về phù hợp. **TimeoutException** nghĩa là **caller đợi quá thời hạn**, không đồng nghĩa task tự động ngừng. Nếu code bắt TimeoutException rồi trả response, worker có thể tiếp tục chạy trong background.

Nếu mục tiêu là **không chỉ ngừng đợi mà còn yêu cầu dừng task**, có thể chủ động gọi cancel(true) trên một Future phù hợp:

~~~java
try {
    return future.get(2, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    future.cancel(true);
    throw e;
}
~~~

Đây là **đoạn minh họa**, không phải service hoàn chỉnh. **cancel(true)** vẫn chỉ là nỗ lực dừng có tính hợp tác, không bảo đảm ngắt được mọi I/O. Đồng thời cần phân biệt:

- Timeout **chờ kết quả** của người gọi.
- Timeout **HTTP client/JDBC/socket** của downstream.
- Timeout **queue wait** trước khi task bắt đầu.
- Timeout **hạn xử lý nghiệp vụ** (deadline), tính cả nhiều retry/hops.
- Timeout **shutdown** đợi worker hoàn thành.

Đặt timeout ở một tầng mà không ở tầng khác có thể khiến hệ thống vẫn giữ kết nối và worker vượt qua deadline. Một deadline chung nên được truyền dưới dạng dữ liệu immutable (ví dụ Instant) và được kiểm tra tại các checkpoint phù hợp.

### Race giữa completion và timeout

Ngay lúc get(timeout) trả TimeoutException, task có thể **vừa kịp hoàn thành** hoặc đang về đích. Gọi cancel(true) lúc đó có thể trả false hoặc có kết quả khác tùy Future implementation. Không được suy từ timeout rằng task chắc chắn chưa có side effect hoặc có thể hoàn tác giao dịch.

## 12. Hai loại dừng hợp tác: interrupt và cancellation token

**Thread interrupt** là tín hiệu ở mức **thread**, hữu dụng cho những API blocking có hỗ trợ. Nhưng có những công việc cần dừng theo **nghiệp vụ**, xuyên qua nhiều stage hoặc qua nhiều thread. Khi đó một **cancellation token** riêng có thể giúp chuyển ý định hủy tới các hàm sử dụng nhiều executor.

Ví dụ kiểu token đơn giản:

~~~java
import java.util.concurrent.atomic.AtomicBoolean;

public final class CancelToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
~~~

Đây là **một tệp Java 17 độc lập tên CancelToken.java**, không có main. Token có thể được truyền vào các service đã biết quyền sở hữu công việc.

Một worker tính theo nhiều batch:

~~~java
for (Batch batch : batches) {
    if (token.isCancelled()
            || Thread.currentThread().isInterrupted()) {
        throw new java.util.concurrent.CancellationException(
                "Job đã bị hủy");
    }

    processBatch(batch);
}
~~~

Đây là **đoạn minh họa**, giả sử có Batch, batches và processBatch. Với mỗi batch, cần xác định công việc nào đã có side effect; dừng tại checkpoint sau một batch không tự hoàn tác batch trước.

**Hạn chế của token:**

- Một **AtomicBoolean** không tự interrupt thread đang chặn trong I/O.
- Future.cancel(true) **không tự set token**; phải thiết kế một owner của tác vụ gọi cả hai khi phù hợp.
- Token nằm trong JVM; khi chạy trên nhiều service/process, phải có trạng thái hủy được truyền qua giao thức/queue/store.
- Không phải lúc nào dừng ngay cũng an toàn; ví dụ đang cập nhật một cặp dữ liệu cần transaction hoặc compensate có thể phải hoàn thành bước đang tiến hành trước khi dừng.

Một API hủy có chất lượng nên xác định **được nhận yêu cầu hủy**, **đã ngừng nhận việc mới**, **đã ngừng phần tính toán** và **đã dọn xong** là những trạng thái khác nhau.

## 13. Thiết kế cleanup: finally, try-with-resources và transaction

Khi task dừng vì interrupt, nó vẫn phải thả resource mà nó sở hữu: lock, stream, file handle, connection, semaphore permit và những phần state tạm thời.

Mẫu logic:

~~~java
void work() throws InterruptedException {
    Resource resource = acquireResource();
    try {
        performSteps(resource);
    } finally {
        resource.close();
    }
}
~~~

Đây là **pseudocode Java** với Resource, acquireResource và performSteps do ứng dụng định nghĩa. Nó minh họa **ownership/cleanup**, không phải tệp chạy độc lập.

Với resource implements AutoCloseable, **try-with-resources** giúp đóng khi task thoát bình thường hoặc vì exception. Tuy nhiên, cleanup cũng có thể thất bại, bị chặn hoặc cần deadline; không nên mặc định một finally luôn thành công trong mọi hạ tầng.

### Tình huống có giao dịch

Một task đang ghi DB rồi nhận interrupt:

- Interrupt không tự động **ROLLBACK** mọi transaction bằng một quy tắc phổ quát.
- Nếu thực thi trong Spring **@Transactional**, việc transaction commit/rollback phụ thuộc exception, cấu hình rollback rules và ranh giới transaction thực tế.
- Khi task đã gửi request thanh toán hoặc gọi API bên ngoài, kết quả có thể đã được thực hiện ở downstream dù caller hủy Future.
- Retry sau cancellation phải có **idempotency key** và chiến lược kiểm tra trạng thái/compensation.

Đừng đặt một **catch(InterruptedException)** trả về trạng thái “success” rồi tiếp tục commit nghiệp vụ nếu yêu cầu dừng phải làm hủy tiến trình.

### Tài nguyên của task chưa từng bắt đầu

Nếu task bị hủy **khi còn trong queue**, **finally nằm bên trong body task có thể không bao giờ chạy**, vì body chưa khởi động. Các resource được phân bổ **trước lúc submit** cần owner và cleanup bên ngoài task hoặc trong cancellation callback riêng. Đây là lý do hạn chế mở connection/file handle trước khi task thực sự chạy và đẩy chúng vào queue lâu.

## 14. Spring @Async: khả năng cancel phụ thuộc loại Future và executor

Trong Spring, **@Async** dùng cơ chế proxy/interceptor để gửi method vào một executor. Bean cần được Spring quản lý, async support được bật và lời gọi phải đi **qua proxy**; tự gọi **this.method()** trong cùng bean thường bỏ qua proxy.

Khi method có kiểu trả về Future hoặc CompletableFuture, caller nhận một handle đại diện cho kết quả. Nhưng **khả năng interrupt tác vụ thực** không giống nhau cho mọi kiểu trả về và mọi adapter:

- Với một Future của **executor.submit(Callable)** được hiện thực bởi **FutureTask**, cancel(true) thường có khả năng interrupt worker đang chạy.
- Với **CompletableFuture** chuẩn, **cancel(true) không tự interrupt** supplier/body. Điều này vẫn quan trọng khi CompletableFuture được trả ra từ Spring @Async.
- Một framework có thể dùng wrapper khác, delegation hoặc executor-specific behavior; không khẳng định cancel trên một Future bên ngoài luôn truyền đúng tới task gốc mà không kiểm tra implementation.
- **@Async void** không trả Future cho caller để gọi cancel. Muốn hủy, phải thiết kế command/job handle, deadline, token hoặc API quản lý job riêng.

### Ví dụ cấu hình ThreadPoolTaskExecutor trong Spring

~~~java
package example.concurrent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class WorkConfig {
    @Bean("workExecutor")
    public ThreadPoolTaskExecutor workExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("work-");
        return executor;
    }
}
~~~

Đây là **tệp cấu hình trong ứng dụng có Spring Context**, không thể biên dịch với javac thuần khi không có dependency Spring. Ví dụ chọn core/max/queue hữu hạn để mô tả pool; kích thước thực tế phải theo năng lực tài nguyên.

### Ví dụ gửi Callable để có Future có thể yêu cầu interrupt

~~~java
package example.concurrent;

import java.util.concurrent.Future;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    private final ThreadPoolTaskExecutor workExecutor;

    public ReportService(ThreadPoolTaskExecutor workExecutor) {
        this.workExecutor = workExecutor;
    }

    public Future<String> submitReport(String id) {
        return workExecutor.submit(() -> {
            try {
                return generateReport(id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        });
    }

    private String generateReport(String id)
            throws InterruptedException {
        Thread.sleep(1000);
        return "report:" + id;
    }
}
~~~

Đây là **tệp minh họa trong Spring application** dùng bean ThreadPoolTaskExecutor. Nó chủ động sử dụng **submit(Callable)** để trả Future; generateReport ngủ mô phỏng một thao tác chờ có hỗ trợ interruption. Trong production cần timeout và resource cleanup riêng, không dùng sleep để mô phỏng nghiệp vụ thật.

Caller có thể giữ Future của task rồi **cancel(true)**. Với implementation ThreadPoolTaskExecutor dựa trên ThreadPoolExecutor, handle có cơ chế hủy gần với FutureTask. Tuy nhiên, việc hủy ở giữa tác vụ không tự undo logic đã chạy trước đó.

**Cần tránh một suy luận sai:** dùng **@Async** trả **CompletableFuture** không giống với việc caller đang cầm trực tiếp FutureTask do executor.submit. Nếu mục tiêu là dừng worker thực sự, hãy kiểm tra cơ chế scheduling/cancellation của implementation và cân nhắc thiết kế quản lý job có token/deadline rõ.

## 15. Spring CompletableFuture, lỗi và thông báo kết quả

Một ví dụ method Spring:

~~~java
@Async("workExecutor")
public CompletableFuture<String> buildReport(String id) {
    String result = compute(id);
    return CompletableFuture.completedFuture(result);
}
~~~

Đây là **đoạn minh họa** trong bean có compute và imports tương ứng. Khi gọi qua Spring proxy, method được dispatch theo executor; bên trong body có thể trả completedFuture vì computation đã hoàn thành ở body thread. Caller quan sát future được proxy cung cấp.

Nếu caller gọi **future.cancel(true)** trên một **CompletableFuture** trả về, **đừng mặc định nó sẽ interrupt body thread**. Cancel ở tầng completion có thể chỉ đánh dấu kết quả đã hủy. Với task thật sự có side effect, hệ thống cần cancellation protocol riêng nếu muốn dừng physical processing.

### Các trạng thái lỗi phải phân biệt

| Sự kiện | Điều caller có thể quan sát | Ý nghĩa |
| --- | --- | --- |
| Executor không nhận task | Có thể ném **TaskRejectedException** ngay tại submit/proxy | Công việc có thể chưa được nhận |
| Worker ném exception | Future hoàn thành với lỗi, get thường cho **ExecutionException** | Task đã chạy và thất bại |
| Future bị hủy | **CancellationException**, isCancelled true | Handle đã canceled; chưa kết luận cleanup |
| Caller chờ quá deadline | **TimeoutException** từ get(timeout) | Caller hết thời gian chờ, task có thể còn chạy |
| CompletableFuture.orTimeout | Future hoàn thành ngoại lệ với timeout | Không tự interrupt supplier |
| Caller bị interrupt khi đợi get | **InterruptedException** ở caller | Đây là interrupt của thread đợi, không đồng nghĩa worker đã bị cancel |

**Không catch mọi exception rồi coi là task được cancel.** Điều này làm sai metrics và gây những vòng retry không cần thiết. Phân biệt exception ở **submit**, **wait** và **worker execution**.

## 16. Không interrupt bừa worker thread của pool

Khi dùng ThreadPoolTaskExecutor, worker có thể được tái sử dụng để phục vụ nhiều request khác nhau. Không nên **giữ trực tiếp Thread worker rồi tự Thread.interrupt()** như một cách hủy job chung nếu executor/framework đang quản lý thread đó.

Lý do:

- Bạn có thể gửi interrupt nhầm **task khác** sau khi worker đã kết thúc task ban đầu và được tái sử dụng.
- Bạn có thể phá protocol đồng bộ nội bộ của executor nếu bypass API.
- Bạn phải tự giải quyết tình huống race giữa lúc task hoàn thành và thread bắt đầu task tiếp.
- Không có hợp đồng quản lý ownership/interrupt nếu chỉ lưu tên thread hoặc tham chiếu Thread dùng chung.

**Ưu tiên hủy qua handle của công việc** (Future hợp lệ, job token, API của executor/cancelable task), không thao tác trực tiếp vào thread worker dùng chung.

### InterruptedException trong pool thread

Trong task, khi API blocking ném InterruptedException, hãy giữ ý nghĩa yêu cầu dừng. Nếu không thể propagate, khôi phục flag và thoát khỏi body. Không đặt Thread.interrupted() để “làm sạch” flag giữa chừng rồi tiếp tục xử lý. Việc reset trạng thái trước task tiếp theo thuộc trách nhiệm cấu trúc executor và quy tắc task, không phải giấy phép cho một task nuốt interrupt.

## 17. shutdown(), shutdownNow() và hủy riêng một task

Đây là ba thao tác khác nhau:

| API | Phạm vi | Hành vi chủ đạo |
| --- | --- | --- |
| **future.cancel(true)** | Một Future/task handle | Yêu cầu hủy, có thể interrupt đúng task theo implementation |
| **executor.shutdown()** | Cả ExecutorService | Ngừng nhận task mới, thông thường cho phép task đã nhận hoàn tất |
| **executor.shutdownNow()** | Cả ExecutorService | Cố ngừng những task đang thực thi (thường bằng interrupt), ngăn task chờ bắt đầu và trả danh sách task chưa chạy |

**shutdownNow() cũng là best effort**, không bảo đảm dừng mọi worker tức thì nếu task không hợp tác. Một ứng dụng có lifecycle đúng thường gọi shutdown, chờ một thời hạn, rồi cân nhắc shutdownNow khi quá deadline.

~~~java
pool.shutdown();
if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
    pool.shutdownNow();
}
~~~

Đây là **đoạn minh họa** trong một phương thức có pool và xử lý InterruptedException phù hợp. Nếu thread gọi awaitTermination bị interrupt, nó phải xử lý theo chính sách lifecycle (có thể shutdownNow rồi khôi phục flag) thay vì nuốt ngoại lệ.

Spring quản lý vòng đời ThreadPoolTaskExecutor theo cấu hình và lifecycle của ApplicationContext. **Không tự tắt shared Spring executor sau từng HTTP request**; như vậy có thể làm hỏng những request khác đang dùng pool. Hủy một công việc bằng handle/token của chính công việc đó; shutdown pool khi toàn ứng dụng hoặc component sở hữu pool dừng theo thiết kế.

## 18. HTTP disconnect, timeout request và business cancellation

Trong một web application, người dùng đóng trình duyệt, client ngắt kết nối hoặc gateway trả HTTP 504 **không đồng nghĩa worker do bạn tự gửi vào executor đã tự động dừng**.

Cần xác định:

- **HTTP request timeout** chỉ giới hạn phần giao tiếp theo cấu hình framework/container.
- **Future.get(timeout)** giới hạn thời gian đợi của một caller cụ thể.
- **Client socket disconnect** có thể báo lỗi ở tầng I/O nhưng không tự hủy job nghiệp vụ đã tách khỏi request.
- **Business cancellation** là một hành động có hợp đồng rõ: tìm job bằng jobId, xác thực quyền hủy, ghi trạng thái cancel requested và truyền tín hiệu đến worker/job processor.
- **Durable background job** thường cần trạng thái lưu trong DB/queue, vì một Future trong RAM mất ý nghĩa qua process restart hoặc khi job được xử lý bởi worker process khác.

Một API có thể trả lời:

~~~text
POST /reports       -> 202 Accepted, trả jobId (nếu đã lưu nhận việc)
DELETE /reports/{id} -> ghi nhận cancel requested
GET /reports/{id}    -> PENDING / RUNNING / CANCEL_REQUESTED /
                        CANCELLED / SUCCEEDED / FAILED
~~~

Đây là **phác thảo API**, không phải hợp đồng mặc định của Spring. Có thể cần trạng thái CANCEL_FAILED hoặc FINISHED_WITH_SIDE_EFFECT khi hủy quá muộn. **Cancel requested** và **cancelled** không nên bị coi như cùng một sự kiện.

Một HTTP endpoint trả **202 Accepted** sau khi một TaskExecutor đã reject là sai nếu ý nghĩa 202 là công việc đã được nhận. Cần kiểm tra trạng thái submit hoặc durable enqueue trước khi xác nhận.

## 19. Side effects và race khi cancel quá muộn

Ví dụ một tác vụ:

~~~text
[validate] -> [write DB] -> [call payment gateway] -> [send email]
                     ^
                     |
                cancel(true) ở thời điểm này
~~~

Nếu cancel xảy ra sau khi DB commit hoặc external API đã nhận lệnh, interrupt worker **không làm đảo ngược** tác động đã xảy ra.

Thiết kế đúng cho một workflow có side effect cần:

1. Chọn checkpoint có thể dừng an toàn **trước khi** bắt đầu một bước không đảo ngược.
2. Kiểm tra cả **deadline** và **cancel token** ở checkpoint.
3. Với step đã bắt đầu, quyết định **chờ kết quả, kiểm tra trạng thái hoặc compensate**; không mặc định “thread đã bị ngắt” nghĩa là downstream chưa chạy.
4. Dùng **idempotency key** khi retry hoặc khi caller có thể gửi lặp lại yêu cầu.
5. Lưu trạng thái/job version để tránh hai phía cùng báo CANCELLED và SUCCEEDED do race.
6. Ghi **sự kiện hoàn tất cleanup** khi worker đã thả tài nguyên, không chỉ khi Future.isDone.

**Câu hỏi nghiệp vụ bắt buộc:** “hủy có nghĩa là không bắt đầu bước mới, hay phải cố dừng cả bước đang chạy?” Hai hợp đồng này có tác động khác nhau lên timeout, compensation, quota và thông báo cho người dùng.

## 20. Kiểm thử cancellation không dựa vào may rủi scheduler

Một test đúng cần tạo được trạng thái trước khi cancel:

### Test task đang chạy và nhạy interrupt

1. Dùng **CountDownLatch started** để biết worker đã bắt đầu.
2. Đưa worker vào **await()**, bảo đảm chưa tự kết thúc.
3. Main gọi **Future.cancel(true)**.
4. Worker ghi nhận InterruptedException và thông báo cleanup bằng **finished** latch.
5. Main chờ finished có timeout rồi mới assert.
6. Xác minh Future.isCancelled() và Future.get() báo CancellationException.

Đó là mô hình của **CooperativeCancelDemo**.

### Test task không hợp tác

1. Task cố ý bỏ qua interrupt nhưng có **AtomicBoolean stop** riêng để cleanup.
2. Main đợi task bắt đầu, gọi cancel(true).
3. Assert Future.isDone/isCancelled true **nhưng finished vẫn chưa được signal**.
4. Set stop và đợi finished; tránh test rò worker.

Đó là mô hình của **NonCooperativeCancelDemo**.

### Test queued task

1. Pool một worker đang bận.
2. Task thứ hai nằm trong queue.
3. cancel(false) task thứ hai trước khi giải phóng worker.
4. Sau khi worker đầu hoàn tất, dùng một barrier task để đợi queue được xử lý.
5. Assert body task thứ hai **không chạy**.

Đó là mô hình của **CancelQueuedDemo**.

### Test CompletableFuture

1. Supplier chạy và chờ latch không tự hoàn tất.
2. Main gọi CompletableFuture.cancel(true).
3. Assert Future đánh dấu canceled **nhưng worker vẫn chờ**.
4. Tự mở latch, xác nhận worker hoàn tất và **không quan sát InterruptedException do cancellation**.
5. get vẫn báo CancellationException.

Đó là mô hình của **CompletableCancelDemo**.

**Không lấy Thread.sleep(100) làm bằng chứng task đã bắt đầu hay đã thoát.** Thread.sleep có thể dùng khi công việc thực sự cần delay, nhưng test concurrency phải có công cụ đồng bộ để đặt thứ tự sự kiện. Luôn đặt timeout cho await và get để CI không treo vĩnh viễn.

## 21. Các nhầm lẫn phổ biến

**“Future.cancel(true) giết task ngay lập tức.”** Sai. Với FutureTask, nó có thể yêu cầu interrupt; task phải hợp tác để dừng hoặc API đang chặn phải hỗ trợ interrupt.

**“cancel trả true chứng tỏ worker đã cleanup xong.”** Sai. Đó là kết quả nỗ lực hủy Future, không phải tín hiệu cleanup vật lý.

**“isDone() chỉ true khi task thành công.”** Sai. Exception và cancellation đều có thể khiến isDone true.

**“Future.cancel(false) không bao giờ hủy được task.”** Sai. Nó có thể hủy queued task, và FutureTask có thể đánh dấu canceled ngay cả khi task đang chạy mà không interrupt.

**“CompletableFuture.cancel(true) luôn interrupt supplier.”** Sai theo Javadoc Java 17: mayInterruptIfRunning **không có tác dụng** ở CompletableFuture chuẩn.

**“Thread.interrupt() giống Thread.stop().”** Sai. Interrupt là yêu cầu hợp tác; Thread.stop cưỡng bức dừng nguy hiểm và đã bị deprecated.

**“InterruptedException không quan trọng nên có thể nuốt.”** Sai. Nuốt exception rồi tiếp tục làm việc thường làm vô hiệu hóa hủy và gây nghẽn pool.

**“Thread.interrupted() chỉ đọc cờ, không đổi gì.”** Sai. Nó **đọc và xóa** interrupt flag của thread hiện tại.

**“Thread.currentThread().isInterrupted() cũng xóa cờ.”** Sai. Nó chỉ quan sát trạng thái.

**“get(timeout) hết hạn sẽ tự gọi cancel.”** Sai. Timeout chỉ giới hạn thời gian đợi; muốn cố dừng task cần hành động riêng.

**“CompletableFuture.orTimeout tự ngắt HTTP client/JDBC đang chặn.”** Sai. Nó điều khiển completion của future, không phải một cơ chế hủy mọi nguồn công việc bên dưới.

**“@Async trả CompletableFuture thì cancel(true) tương đương cancel FutureTask.”** Không thể giả định. Cần kiểm tra implementation và truyền cancellation token/deadline phù hợp.

**“Future canceled nghĩa là chưa commit side effect.”** Sai. Task có thể đã ghi DB, gửi email hoặc gọi dịch vụ ngoài trước khi bị hủy.

**“Có thể tự interrupt shared worker qua tên thread.”** Nguy hiểm. Worker có thể đang chạy task khác do thread pool tái sử dụng.

**“shutdownNow chắc chắn dừng mọi task tức thì.”** Sai. Đây là best-effort interruption, không ép task thiếu hợp tác ngừng vật lý.

**“Worker finally chắc chắn chạy khi hủy queued task.”** Sai. Nếu body chưa chạy, finally trong body không có cơ hội được thực thi.

**“HTTP client disconnect tự hủy job đã gửi qua executor.”** Không mặc nhiên. Job cần cancellation contract riêng.

## 22. Bài tập tự kiểm tra

**Bài 1.** Một Callable đang Thread.sleep(10_000), caller gọi cancel(true) trên FutureTask. Điều gì có khả năng xảy ra?

**Đáp án:** Future có thể chuyển thành cancelled, Thread.sleep nhận interrupt và ném InterruptedException; Callable cần dừng/cleanup hợp tác. Không khẳng định mọi API I/O khác cũng có phản ứng y hệt.

**Bài 2.** Future.isDone() và Future.isCancelled() đều true nhưng metrics CPU của worker vẫn tăng. Có mâu thuẫn không?

**Đáp án:** Không. Future có thể đã bị hủy về trạng thái, còn task chưa ngừng vì không kiểm tra ngắt hoặc không phản ứng.

**Bài 3.** Task đã nằm trong queue, chưa bắt đầu. cancel(false) có thể thành công không?

**Đáp án:** Có. Không cần interrupt một thread đang chạy chính task đó; queued task có thể bị đánh dấu cancelled để body không chạy.

**Bài 4.** Tại sao catch(InterruptedException) nên trả lại interrupt flag khi Runnable không thể propagate exception?

**Đáp án:** Nhiều API xóa cờ khi ném InterruptedException. Khôi phục flag bảo toàn tín hiệu ngắt cho tầng có thể quan sát nó, đồng thời body nên thoát có chủ đích.

**Bài 5.** Một CompletableFuture được tạo bằng supplyAsync đang chờ latch, gọi cf.cancel(true). Có chắc latch.await ném InterruptedException không?

**Đáp án:** Không. CompletableFuture chuẩn không dùng mayInterruptIfRunning để interrupt supplier. Muốn dừng chờ, phải có tín hiệu hoặc hủy nguồn khác.

**Bài 6.** Caller gọi Future.get(1, SECONDS) và bắt TimeoutException, nhưng không gọi cancel. Worker có tự dừng không?

**Đáp án:** Không được bảo đảm. Timeout ở get chỉ dừng chờ của caller.

**Bài 7.** Một tác vụ đã gửi yêu cầu thanh toán rồi bị cancel. Có được ghi trạng thái “chắc chắn chưa thanh toán” không?

**Đáp án:** Không. Phải kiểm tra kết quả ở hệ thống thanh toán, dùng idempotency/compensation theo hợp đồng nghiệp vụ.

**Bài 8.** Vì sao cần finished latch riêng sau cancel(true)?

**Đáp án:** Vì Future.isDone/isCancelled không chứng minh code cuối cùng/finally của worker đã chạy. Tín hiệu riêng cho phép xác minh hoàn tất thực thi hoặc cleanup.

**Bài 9.** Có nên dùng Thread.interrupted() trong mọi điều kiện vòng lặp dài thay cho isInterrupted()?

**Đáp án:** Không mặc nhiên. Thread.interrupted() xóa cờ, dễ làm tầng sau bỏ lỡ tín hiệu. isInterrupted() quan sát mà không xóa.

**Bài 10.** Spring @Async void có cung cấp Future cho caller để gọi cancel(true) không?

**Đáp án:** Không. Muốn hủy phải thiết kế job handle, token, deadline hoặc cơ chế quản lý công việc khác.

**Bài 11.** shutdown() và shutdownNow() khác nhau như thế nào?

**Đáp án:** shutdown không nhận task mới và cho phép task đã nhận tiếp tục theo hợp đồng; shutdownNow cố ngắt task đang chạy và ngăn task chờ bắt đầu, nhưng vẫn là best effort.

**Bài 12.** Một FutureTask đang chạy nhận cancel(false), sau đó Callable vẫn ghi database. Đây có phải lỗi của Java không?

**Đáp án:** Không. cancel(false) không yêu cầu interrupt worker; Future có thể đã canceled nhưng execution vẫn chạy. Thiết kế ứng dụng phải xử lý side effects và quyền hủy.

## 23. Checklist để triển khai hủy task an toàn

- [ ] Xác định Future implementation thực tế: **FutureTask, CompletableFuture hoặc wrapper framework**.
- [ ] Phân biệt **Future cancelled**, **execution stopped** và **cleanup complete**.
- [ ] Biết rõ **cancel(true)** có phát yêu cầu interrupt tới worker hay không trong implementation được dùng.
- [ ] Với task CPU dài: đặt **interrupt/cancel-token checkpoints** ở vị trí dừng an toàn.
- [ ] Với blocking operations: dùng API có **timeout/cancellation** được hỗ trợ, không tin chỉ interrupt.
- [ ] Khi bắt InterruptedException: **propagate hoặc khôi phục cờ và thoát**, không nuốt tín hiệu.
- [ ] Dùng **finally/try-with-resources** để cleanup resource đã được task sở hữu.
- [ ] Giải quyết resource được cấp phát **trước khi task bắt đầu**, vì queued task hủy có thể không chạy body/finally.
- [ ] Không tự interrupt worker của shared Spring thread pool ngoài hợp đồng task.
- [ ] Đặt **timeout tổng** và timeout riêng cho HTTP/DB/socket phù hợp.
- [ ] Với CompletableFuture, tách cancellation của **completion** khỏi cancellation của **computation**.
- [ ] Kiểm soát concurrency/rejection/queue để không giữ công việc quá deadline.
- [ ] Tạo trạng thái job **CANCEL_REQUESTED** khác **CANCELLED/CLEANED** nếu cần quan sát chính xác.
- [ ] Xác minh DB/external side effects, dùng idempotency và compensation khi cần.
- [ ] Test bằng **CountDownLatch** và Future.get(timeout), không chỉ sleep.
- [ ] Trong integration test Spring, xác định @Async proxy và executor đúng; kiểm tra hành vi cancel của handle thật.

## Kết luận

**Future.cancel(true) là lời yêu cầu hủy, không phải lệnh cưỡng bức dừng Java thread.** Với FutureTask do ExecutorService.submit thường tạo ra, phương thức có thể chuyển Future sang cancelled và cố interrupt worker đang thực thi; task muốn dừng nhanh phải **hợp tác** bằng việc phản ứng với InterruptedException, kiểm tra cờ interrupt hoặc cancellation token tại checkpoint an toàn, rồi cleanup. Với **CompletableFuture** chuẩn, **cancel(true) không interrupt computation** mà đánh dấu một exceptional completion bằng CancellationException.

Trong Spring, đừng chỉ nhìn return type Future/CompletableFuture rồi giả định khả năng cancel. Xác định executor, wrapper, proxy và hợp đồng ứng dụng; tách timeout chờ, yêu cầu ngừng, kết thúc vật lý và trạng thái side effect. Thiết kế tốt phải trả lời được **công việc đã bị yêu cầu hủy chưa, nó có thực sự dừng chưa và tài nguyên/đơn hàng liên quan đã ở trạng thái nhất quán chưa**.

**Tài liệu tham khảo chính thức:**

- Java SE 17 [Future API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html).
- Java SE 17 [FutureTask API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/FutureTask.html).
- Java SE 17 [Thread.interrupt, interrupted và isInterrupted](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html).
- Java SE 17 [ExecutorService shutdown, submit, cancel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html).
- Java SE 17 [CompletableFuture cancel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html).
- Spring Framework [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="timeout-cancellation-005">CompletableFuture.cancel() không bảo đảm ngắt luồng xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# CompletableFuture.cancel() không bảo đảm ngắt luồng xử lý

Khi xây dựng tác vụ bất đồng bộ trong Java và Spring Framework, một hiểu nhầm phổ biến là gọi `future.cancel(true)` thì luồng đang thực thi công việc sẽ lập tức bị ngắt. **Điều này không đúng với `java.util.concurrent.CompletableFuture`.** Theo hợp đồng Java SE 17, tham số `mayInterruptIfRunning` của `CompletableFuture.cancel(boolean)` **không có hiệu lực**: implementation này không dùng interruption để kiểm soát quá trình xử lý.

`cancel(true)` chủ yếu chuyển đối tượng `CompletableFuture` sang **trạng thái đã hủy**, khiến bên gọi không còn nhận được kết quả bình thường. Một worker đã bắt đầu có thể vẫn tiếp tục chờ I/O, ghi database hoặc thực hiện side effect **sau khi `future.isCancelled()` đã bằng `true`**. Việc hủy kết quả không đồng nghĩa chấm dứt công việc.

Bài học thuộc nhóm **Hết hạn và hủy** trong **Spring Framework - concurrency**. Nội dung giải thích ngữ nghĩa chuẩn Java 17, so sánh với `FutureTask`, các kiểu timeout, cách áp dụng cho `@Async`, thiết kế hủy hợp tác và ví dụ biên dịch/chạy được.

## 1. Tách trạng thái kết quả khỏi tiến trình xử lý

Có ba thực thể liên quan nhưng khác nhau:

- **Bên gọi (caller):** có thể quyết định không muốn nhận hoặc chờ kết quả nữa.
- **Đối tượng kết quả `CompletableFuture<T>`:** giữ trạng thái pending, thành công, lỗi hoặc hủy; hỗ trợ composition với `CompletionStage`.
- **Công việc thực tế (worker/task):** thao tác trên thread, DB, network hoặc phần mềm khác. Nó chỉ thực sự dừng nếu logic hoặc tài nguyên điều khiển việc đó.

Một CompletableFuture có thể được tạo bằng `new CompletableFuture<>()` và được code khác hoàn tất bằng `complete(value)`, **không liên kết với worker cụ thể**. Vì vậy cancel của đối tượng kết quả không thể biết chắc thread nào cần ngắt.

~~~text
Caller                   CompletableFuture                 Worker
  |                             |                             |
  |--- runAsync(task, pool) ---->|--- dispatch --------------->|
  |                             |                         task bắt đầu
  |                             |                         đang chạy/chờ
  |--- future.cancel(true) ----->|                             |
  |<-- trạng thái đã hủy --------|                             |
  |                             |                             |
  |   isCancelled() = true      |                        worker vẫn có thể
  |   join() ném cancellation   |                        thực hiện tiếp
  |                             |                             |
  |                             |                        kết thúc theo logic
~~~

Điều quan trọng là **`cancel(true) == true` không xác nhận worker đã dừng**. Bên gọi nhận `CancellationException` từ Future có thể đang nhìn một kết quả đã hủy, trong khi công việc vẫn chạy hoặc một request remote đã được xử lý.

### 1.1. Hai câu hỏi khác nhau

1. **Future còn cung cấp kết quả bình thường không?** Khi hủy thành công, không. `complete(value)` thông thường không ghi đè trạng thái đã được hủy.
2. **Worker có dừng và cleanup chưa?** Không suy ra được từ `isCancelled()`, `isDone()` hay `cancel()`. Phải có trạng thái công việc, tín hiệu worker exit hoặc xác nhận từ hệ thống điều phối.

“Cancel” ở API có thể chỉ là **cancel quyền chờ/nhận kết quả**; “stop job” là một giao thức nghiệp vụ mạnh hơn.

## 2. Hợp đồng của CompletableFuture.cancel(boolean)

Chữ ký API trong Java SE 17:

~~~java
public boolean cancel(boolean mayInterruptIfRunning)
~~~

Đây là **chữ ký rút gọn để tham khảo**, không phải method cần tự định nghĩa. Hành vi chính:

- Nếu Future chưa hoàn tất, `cancel(...)` hoàn thành nó bằng `CancellationException`.
- Tham số `mayInterruptIfRunning` không tác động đến cách implementation này điều khiển worker. `cancel(true)` và `cancel(false)` đều là thao tác đánh dấu hủy trạng thái.
- Nếu Future đã có kết quả bình thường hoặc lỗi trước đó, cancel không thay thế kết quả đã xác lập.
- Dependent CompletableFuture chưa hoàn tất sẽ hoàn tất đặc biệt do cancellation nguồn; lỗi ở stage dẫn xuất có thể là `CompletionException` với cause `CancellationException`.
- Giá trị trả về từ `cancel` là thông tin về trạng thái **đối tượng Future**. Nó không phải kết quả của một lời gọi `Thread.interrupt()` tới worker.

| API | Quan sát khi hủy thành công |
| --- | --- |
| `future.cancel(true)` | Future chuyển sang trạng thái hủy; không buộc ngắt worker |
| `future.isCancelled()` | `true`: Future bị hủy |
| `future.isDone()` | `true`: đã có trạng thái hoàn tất; không có nghĩa worker đã thoát |
| `future.isCompletedExceptionally()` | `true`: hủy là một dạng hoàn tất đặc biệt |
| `future.join()` | Ném `CancellationException` đối với Future gốc bị hủy trực tiếp |
| `future.get()` | Ném `CancellationException` đối với Future gốc bị hủy |
| `future.complete(value)` sau hủy | Không đổi trạng thái hủy sang giá trị bình thường |

`isDone()==true` cũng xuất hiện khi một Future **thành công** hoặc **lỗi**, nên không dùng nó làm tín hiệu nghiệp vụ “task đã thành công”. Tương tự, `isCancelled()==true` là trạng thái logic chứ không chứng minh đã rollback side effect.

### 2.1. Vì sao đặt mayInterruptIfRunning=true vẫn không giúp?

`CompletableFuture` không cần gắn một thread với Future: nó có thể được hoàn tất từ callback, completion stage, executor, event loop hoặc người gọi trực tiếp. API hủy của nó định nghĩa thay đổi trạng thái kết quả. JDK 17 ghi rõ tham số này không có tác dụng trong implementation vì interrupts không được dùng để điều khiển processing.

Không suy luận dựa vào **tên tham số** mà phải dựa vào **hợp đồng class cụ thể**. Đây là khác biệt quan trọng với `FutureTask.cancel(true)`.

## 3. Java 17 chạy độc lập: Future đã hủy nhưng worker còn chờ

Ví dụ sử dụng `CountDownLatch` để điều khiển chính xác: worker đã bắt đầu nhưng không thể kết thúc trước khi main mở latch. Main hủy CompletableFuture trước khi thả worker.

Tạo tệp **`CompletableFutureCancelDemo.java`**:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CompletableFutureCancelDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch workerEnded = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        try {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                } finally {
                    workerEnded.countDown();
                }
            }, pool);
            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker did not start");
            }
            System.out.println("1. cancel(true): " + future.cancel(true));
            System.out.println("2. isCancelled: " + future.isCancelled());
            try {
                future.join();
            } catch (CancellationException expected) {
                System.out.println("3. join(): CancellationException");
            }
            System.out.println("4. Worker ended before release: "
                    + (workerEnded.getCount() == 0));
            release.countDown();
            if (!workerEnded.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker did not finish");
            }
            System.out.println("5. Worker interrupted by cancel: "
                    + interrupted.get());
            System.out.println("6. isDone after worker exit: "
                    + future.isDone());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
~~~

Lệnh biên dịch/chạy bằng JDK 17+:

~~~bash
javac --release 17 CompletableFutureCancelDemo.java
java CompletableFutureCancelDemo
~~~

Stdout đã kiểm chứng khi chạy chương trình:

~~~text
1. cancel(true): true
2. isCancelled: true
3. join(): CancellationException
4. Worker ended before release: false
5. Worker interrupted by cancel: false
6. isDone after worker exit: true
~~~

**Giải thích bằng các ràng buộc đồng bộ:**

1. `started.await(...)` bảo đảm worker thật sự đã chạy, không phải chỉ đang nằm trong queue.
2. Worker đang chặn tại `release.await()`. Nó không thể thoát khi main chưa mở latch, trừ khi bị interrupt.
3. `future.cancel(true)` đánh dấu kết quả hủy; `join()` đã nhìn thấy `CancellationException`.
4. Dòng thứ tư in `false` vì worker còn chờ, chứng minh trạng thái Future hủy **trước khi worker rời tác vụ**.
5. Main mở latch, worker thoát bình thường. `interrupted` là `false`, nên chính lệnh `cancel(true)` đã không ngắt worker trong kịch bản có kiểm soát này.
6. `isDone()` ở dòng cuối chỉ lặp lại trạng thái Future đã có từ trước, không phải một acknowledgement mới rằng worker kết thúc.

`shutdownNow()` chỉ được gọi ở `finally` **sau** khi kết quả đã được quan sát. Nếu đảo thứ tự, tín hiệu ngắt từ shutdown sẽ làm sai ý nghĩa thí nghiệm.

### 3.1. Vì sao không dùng Thread.sleep(100) để đoán?

Một test chạy task rồi ngủ 100 ms trước khi cancel có thể bị thay đổi bởi tải CI, CPU scheduling và thời gian khởi động worker. Latch giúp xác định **đã start** và **chưa thể finish**, nhờ đó tránh kết luận từ timing ngẫu nhiên.

## 4. So sánh: FutureTask.cancel(true) có thể yêu cầu ngắt

Interface `Future` có nhiều implementation. `FutureTask` thường được sử dụng khi `ExecutorService.submit(...)` trả một `Future<?>`. Với implementation đó, `cancel(true)` có thể yêu cầu `Thread.interrupt()` trên thread đang thực thi. Tuy nhiên **interruption vẫn là tín hiệu hợp tác**, không phải cơ chế giết thread cưỡng bức.

Tạo tệp độc lập **`InterruptibleFutureDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterruptibleFutureDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch observed = new CountDownLatch(1);
        AtomicBoolean sawInterrupt = new AtomicBoolean(false);
        try {
            Future<?> handle = pool.submit(() -> {
                started.countDown();
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException ex) {
                    sawInterrupt.set(true);
                    Thread.currentThread().interrupt();
                } finally {
                    observed.countDown();
                }
            });
            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker did not start");
            }
            System.out.println("1. Future.cancel(true): " + handle.cancel(true));
            if (!observed.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker did not observe interruption");
            }
            System.out.println("2. Worker observed interruption: " + sawInterrupt.get());
            System.out.println("3. Future isCancelled: " + handle.isCancelled());
        } finally {
            pool.shutdownNow();
        }
    }
}
~~~

Chạy:

~~~bash
javac --release 17 InterruptibleFutureDemo.java
java InterruptibleFutureDemo
~~~

Stdout đã kiểm chứng:

~~~text
1. Future.cancel(true): true
2. Worker observed interruption: true
3. Future isCancelled: true
~~~

Worker đang ở thao tác `Thread.sleep(...)` có khả năng phản ứng với interrupt bằng `InterruptedException`. `FutureTask.cancel(true)` gửi yêu cầu ngắt và chương trình quan sát được.

Dù vậy, nếu worker phớt lờ cờ interrupt, chặn trong một I/O không hỗ trợ interruption hoặc đã commit một giao dịch, `Future.cancel(true)` **không thể bảo đảm worker dừng ngay** và cũng không rollback side effect.

| Cơ chế | Ý nghĩa trực tiếp | Có bảo đảm dừng ngay? |
| --- | --- | --- |
| `CompletableFuture.cancel(true)` | Đánh dấu kết quả hủy bằng `CancellationException`; bỏ qua mayInterruptIfRunning | Không |
| `FutureTask.cancel(true)` | Yêu cầu hủy, có thể gửi interrupt tới thread đang chạy | Không; task phải hợp tác |
| `Thread.interrupt()` | Gửi tín hiệu ngắt; một số blocking API quan sát và ném `InterruptedException` | Không |
| `ExecutorService.shutdownNow()` | Cố gắng ngắt task đang chạy và dừng nhận việc | Không; cơ chế best-effort |
| Token `AtomicBoolean` | Gửi cờ dừng do ứng dụng định nghĩa | Không; worker phải kiểm tra cờ |

Không dùng `Thread.stop()` để ép task chấm dứt: đó là API không an toàn và đã bị deprecated. Không gọi interrupt tùy tiện vào thread của pool dùng chung khi không có quyền sở hữu công việc.

## 5. Cancellation đối với chuỗi CompletionStage

Với `thenApply`, `thenCompose`, `whenComplete` và các stage khác, cần phân biệt **Future gốc** và **stage phụ thuộc**.

~~~java
CompletableFuture<String> source = new CompletableFuture<>();
CompletableFuture<Integer> dependent =
        source.thenApply(String::length);
source.cancel(true);
~~~

Đây là mã Java minh họa (cần import `CompletableFuture`). Nếu nguồn chưa hoàn tất và bị hủy:

- `source.isCancelled()` là `true`; `source.join()` ném `CancellationException`.
- `dependent` chưa hoàn tất sẽ hoàn thành đặc biệt, thông thường bằng `CompletionException` mang cause `CancellationException`.
- Function `String::length` không nhận kết quả bình thường để xử lý.
- `dependent.isCancelled()` **không nhất thiết là `true`**, vì stage dẫn xuất có thể chỉ mang trạng thái exception, không phải object được gọi `cancel(...)` trực tiếp.

**Hủy một dependent stage không mặc nhiên hủy upstream**, và hủy một source Future không tự biết cách hủy HTTP request, JDBC query hoặc công việc đã được chuyển cho một hệ thống khác. Nếu cần lan tín hiệu hủy qua pipeline, phải xây dựng một cancellation protocol riêng.

Ví dụ callback phân loại trên Future gốc:

~~~java
source.whenComplete((value, error) -> {
    if (error instanceof java.util.concurrent.CancellationException) {
        System.out.println("Result cancelled");
    } else if (error != null) {
        System.out.println("Result failed: " + error);
    } else {
        System.out.println("Result completed: " + value);
    }
});
~~~

Đây là snippet minh họa, không có stdout cố định nếu không biết trạng thái `source` lúc đăng ký. Trên dependent stage, exception có thể được bọc trong `CompletionException`; kiểm tra cause thay vì chỉ so sánh kiểu ngoài cùng.

Callback hoàn tất có thể chạy trên thread hoàn thành/hủy; các biến thể `*Async` dùng cơ chế scheduling khác. Tránh block dài trong callback mà không biết thread nào sẽ chạy nó.

## 6. Timeout không tự dừng worker

Các API timeout của Java thể hiện **quyền chờ kết quả**, **trạng thái kết quả** hoặc **timeout I/O**, là ba trách nhiệm khác nhau.

| API | Hành vi khi hết thời gian | Có tự ngắt worker? |
| --- | --- | --- |
| `future.get(2, SECONDS)` | Caller ngừng chờ, nhận `TimeoutException` | Không |
| `future.orTimeout(2, SECONDS)` | Chính CompletableFuture hoàn thành đặc biệt bằng `TimeoutException` nếu còn pending | Không |
| `future.completeOnTimeout(fallback, 2, SECONDS)` | Chính CompletableFuture hoàn thành bằng giá trị fallback nếu còn pending | Không |
| `future.cancel(true)` | CompletableFuture chuyển sang hủy nếu còn có thể | Không, tham số true bị bỏ qua |
| HTTP/JDBC client timeout | Giới hạn thao tác I/O theo API cụ thể | Tùy thư viện, không được đồng nhất với cancel Future |

`orTimeout(...)` và `completeOnTimeout(...)` trả **chính CompletableFuture được gọi**, không phải một bản sao độc lập của kết quả. Nếu underlying task vẫn đang chạy, nó có thể tiếp tục ghi dữ liệu dù Future đã trả lỗi timeout hoặc fallback.

Ví dụ tích hợp với một công việc chậm:

~~~java
CompletableFuture<String> result =
        CompletableFuture.supplyAsync(() -> slowOperation(), executor);
result.orTimeout(2, TimeUnit.SECONDS);
~~~

Snippet trên cần có `slowOperation()`, `executor` và imports. Nó **không bảo đảm ngắt** `slowOperation()` khi hết 2 giây. Muốn deadline thật cho network/DB phải cấu hình timeout trong thư viện tương ứng, và có kiểm soát khi side effect đã xảy ra.

### 6.1. Timeout từ góc nhìn toàn hệ thống

Một task có thể mất 5 giây trong executor queue rồi 10 giây thực thi. Nếu SLA cho phép tổng 8 giây, một I/O timeout 10 giây trên worker là chưa đủ. Nên theo dõi `t_submit`, `t_start`, `t_finish`, áp dụng deadline end-to-end, và kiểm tra thời hạn ở điểm bắt đầu để tránh thực thi một nhiệm vụ đã hết giá trị.

**Timeout ở caller, trạng thái hủy của Future và ngừng thực hiện side effect là ba sự kiện cần xác nhận riêng.**

## 7. Với Spring @Async, cần phân biệt Future và worker

Trong Spring Framework, phương thức `@Async` có thể trả `CompletableFuture<T>` để caller nhận kết quả, ghép callback hoặc theo dõi lỗi. Tuy nhiên, **không có hợp đồng chung rằng gọi `cancel(true)` trên Future được trả về sẽ ngắt target method đang chạy trong ThreadPoolTaskExecutor**.

Những yếu tố cần kiểm tra:

1. `@EnableAsync` được bật, bean thực sự do Spring quản lý.
2. Caller đi qua Spring proxy. Self-invocation như `this.doWork()` thường bypass interceptor trong proxy mode mặc định.
3. `@Async("jobExecutor")` chọn đúng executor. Method có thể đang queue hoặc đã bắt đầu lúc caller nhận Future.
4. Nếu executor không chấp nhận việc, `TaskRejectedException` có thể xuất hiện **ngay lúc gửi**, không phải một trạng thái cancellation của task đã chạy.
5. `@Async void` không có Future trả về để caller gọi `cancel`; cần kênh request-cancel khác nếu nghiệp vụ hỗ trợ.
6. Với `CompletableFuture<T>`, kể cả `cancel(true)` thành công ở handle, vẫn phải xác minh worker đã thực sự dừng theo logic/metric của ứng dụng.

Không triển khai nút Hủy ở API chỉ bằng `storedFuture.cancel(true)` rồi trả kết quả “job đã dừng chắc chắn”. Future có thể đã bị hủy còn worker đang gọi remote service.

## 8. Hủy hợp tác bằng cancellation token trong Spring

Nếu yêu cầu là **dừng công việc**, hãy thiết kế cơ chế hợp tác thay vì trông chờ trạng thái Future:

1. Service điều phối tạo một token cho mỗi job.
2. Worker kiểm tra token tại các điểm an toàn giữa những bước xử lý.
3. Endpoint hủy đặt token thành yêu cầu dừng.
4. Worker dừng, cleanup và báo lại trạng thái cuối.
5. Hệ thống phân biệt `CANCEL_REQUESTED` với `CANCELLED`: đã nhận yêu cầu khác với xác nhận đã dừng.

Một token trong cùng JVM có thể là `AtomicBoolean`. Nó thread-safe nhưng **không tự đánh thức blocking I/O**, nên công việc dài vẫn cần timeout và cơ chế interruption riêng nếu muốn phản hồi nhanh.

### 8.1. Cấu hình ThreadPoolTaskExecutor

Tệp `AsyncPoolConfiguration.java` (Spring Framework 6.x và Java 17+):

~~~java
package example.concurrency;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncPoolConfiguration {
    @Bean("jobExecutor")
    public ThreadPoolTaskExecutor jobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("job-");
        return executor;
    }
}
~~~

Đây là **mã tích hợp Spring** cần dependency Spring Context và được đăng ký qua ApplicationContext; không biên dịch bằng `javac` riêng một tệp không có classpath. Các số pool chỉ là minh họa, không phải tuning chung cho mọi workload.

### 8.2. Worker kiểm tra tín hiệu dừng

Tệp `ReportWorker.java`:

~~~java
package example.concurrency;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportWorker {
    @Async("jobExecutor")
    public CompletableFuture<String> build(
            String reportId, AtomicBoolean cancelRequested) {
        try {
            for (int part = 1; part <= 10; part++) {
                if (cancelRequested.get()
                        || Thread.currentThread().isInterrupted()) {
                    throw new CancellationException(
                            "Report cancellation requested: " + reportId);
                }
                // Mô phỏng I/O; trong ứng dụng thật đặt timeout cho HTTP/DB.
                TimeUnit.MILLISECONDS.sleep(100);
            }
            return CompletableFuture.completedFuture("ready:" + reportId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(ex);
        }
    }
}
~~~

Mã này minh họa **điểm kiểm tra token** trong worker. Khi một bước chạy xong, vòng lặp mới quan sát cờ. Nếu worker đang ở thao tác `sleep(...)`, chỉ đổi `AtomicBoolean` không tự đánh thức nó. Không dùng `Thread.sleep` làm I/O giả lập trong production mà bỏ qua timeout thực tế.

Caller ở **bean khác** có thể dùng:

~~~java
AtomicBoolean stopRequested = new AtomicBoolean(false);
CompletableFuture<String> result =
        reportWorker.build("R-17", stopRequested);

// Một lúc khác người dùng yêu cầu ngừng:
stopRequested.set(true);

// Theo dõi worker hoàn tất theo giao thức hợp tác:
result.whenComplete((value, error) -> {
    if (error != null) {
        System.out.println("Worker did not complete normally: " + error);
    } else {
        System.out.println("Worker completed: " + value);
    }
});
~~~

Đây là **đoạn caller minh họa**, giả định `reportWorker` được Spring inject, có imports `AtomicBoolean` và `CompletableFuture`. Không có stdout cố định: công việc có thể hoàn tất trước hoặc sau khi cờ được đặt.

**Tại sao không lập tức gọi `result.cancel(true)` trong ví dụ này?** Vì khi đó `result` có thể báo hủy ngay trong khi worker chưa kiểm tra token, làm mất một kênh theo dõi thời điểm worker thực sự thoát. Với hợp đồng cần biết đã dừng, nên giữ job status hoặc completion signal tách biệt.

### 8.3. Hủy công việc liên process hoặc nhiều replica

`AtomicBoolean` chỉ truyền tín hiệu trong một JVM. Nếu job chạy trên pod A mà request cancel đến pod B, cờ trong bộ nhớ B không thể tác động tới worker A. Thiết kế cần job id, nơi lưu trạng thái tập trung và kênh báo tín hiệu hoặc polling tới đúng worker. Với job có yêu cầu bền vững, thêm job store, retry/idempotency và cơ chế khôi phục sau restart.

Token lưu trong map cần có lifecycle cleanup để không rò bộ nhớ, nhưng cũng không nên xóa trước khi worker thực sự hoàn tất.

## 9. Interruption là tín hiệu hợp tác, không phải lệnh giết thread

Một số API Java như `Thread.sleep`, `Object.wait` hoặc `BlockingQueue.take` hỗ trợ ngắt bằng `InterruptedException`. Khi code bắt exception này nhưng không thể ném lên tầng trên, mẫu phổ biến là khôi phục cờ interrupt rồi thoát:

~~~java
try {
    blockingOperation();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
}
~~~

Đây là **pseudocode trong một method void**, giả định `blockingOperation()` có thể ném `InterruptedException`. Trong method có thể khai báo `throws InterruptedException`, việc ném tiếp cũng là cách xử lý hợp lệ.

Tránh `catch (InterruptedException ignored) {}` rồi chạy tiếp vô thời hạn. Nhưng cũng không nên tưởng mọi JDBC/HTTP/remote API đều chấm dứt lập tức khi thấy interrupt: một số I/O cần timeout/hủy riêng, và hệ thống từ xa có thể đã nhận công việc.

### 9.1. Side effect không bị hoàn tác bởi cancel

Một worker có thể đã ghi cơ sở dữ liệu, gửi email hoặc thực hiện HTTP POST trước khi caller yêu cầu cancel. Khi `future.cancel(true)` thành công, những tác động đã xảy ra **không được rollback**. Trong Spring, transaction context thường gắn với thread; transaction của caller không tự lan đầy đủ sang worker `@Async`. Hủy Future của caller không tự rollback transaction worker.

Với nghiệp vụ không thể mất hoặc không được phép xử lý trùng, cần idempotency key, trạng thái job, giao thức rollback/compensation và một nguồn công việc bền vững nếu phù hợp. Không hứa “hủy thành công” chỉ vì handle chuyển sang canceled.

## 10. Hủy task trước khi bắt đầu chạy

Nếu task còn chờ queue, hủy một Future trước khi nó start có thể khiến wrapper của executor **bỏ qua logic task** khi được worker lấy ra. Nhưng điều này phụ thuộc implementation, loại Future, cách tạo callback và cơ chế hủy. Không suy luận rằng mọi `CompletableFuture.cancel()` sẽ tự gỡ chính xác task khỏi queue của mọi `TaskExecutor`.

Đối với `FutureTask` từ `ExecutorService.submit(...)`, việc hủy thành công trước khi task chạy thông thường ngăn phần `Callable/Runnable` chạy. Với một CompletableFuture dùng `runAsync`, cần kiểm tra đường dispatch/wrapper thực tế nếu yêu cầu ngăn task tuyệt đối.

Tương tự, hủy một dependent stage không đồng nghĩa dừng stage nguồn. Một stage có thể đã khởi chạy HTTP call trước khi downstream bị hủy. Cần thiết kế cách truyền cancellation riêng nếu upstream cũng phải dừng.

## 11. Quy trình xác minh cancellation trong production

Để tránh dashboard báo “100% jobs canceled” trong khi worker vẫn chạy, nên ghi nhận riêng:

| Sự kiện/metric | Ý nghĩa |
| --- | --- |
| `submitted_total` | Số task được yêu cầu |
| `started_total` | Số worker thực sự bắt đầu |
| `cancel_requested_total` | Số lời yêu cầu dừng |
| `future_cancelled_total` | Số handle Future chuyển sang trạng thái hủy |
| `worker_stopped_total` | Số worker có tín hiệu đã thoát/cleanup |
| `completed_total` | Số tác vụ hoàn tất bình thường |
| `failed_total` | Số tác vụ thất bại |
| `cancel_latency` | Khoảng từ yêu cầu hủy tới worker xác nhận dừng |
| `active_threads` / `queue_depth` | Pool còn bận và backlog bao nhiêu |

Các tên trên là **gợi ý metrics**, không phải những tên Spring mặc định bắt buộc phải có. Dùng job id/correlation id để nối logs giữa caller, worker và downstream. Nếu Future bị cancel, `whenComplete` có thể chạy ngay và báo lỗi cancellation **trước khi worker thực tế dừng**; không lấy callback đó làm metric xác nhận stopped.

### 11.1. Ngôn ngữ trạng thái rõ ràng

- **`CANCEL_REQUESTED`:** hệ thống đã nhận lệnh yêu cầu hủy.
- **`CANCELLING`:** worker đang phối hợp thoát và cleanup, nếu cần phân biệt.
- **`CANCELLED`:** đã có xác nhận rằng job không còn thực hiện công việc theo giao thức của ứng dụng.
- **`SUCCEEDED`:** worker hoàn tất trước khi lệnh hủy có hiệu lực.
- **`FAILED`:** lỗi khác phát sinh; cần xem có retry/compensation không.

Không dùng `future.isCancelled()` để quyết định mọi trạng thái nghiệp vụ trên khi chưa có kênh xác nhận từ worker.

## 12. Cách kiểm thử đáng tin cậy

Một bài test liên quan hủy nên bảo đảm **worker đã bắt đầu** trước khi gọi cancel. Dùng latch/barrier hoặc fake client có thể dừng tại điểm xác định:

1. **Cancel CompletableFuture đang chạy:** dùng `CompletableFutureCancelDemo`; kiểm tra Future canceled trong khi worker vẫn chờ; sau đó thả latch để cleanup.
2. **FutureTask nhận interrupt:** dùng `InterruptibleFutureDemo`; worker chặn ở API có thể ngắt, cancel và xác minh worker quan sát `InterruptedException`.
3. **Timeout không dừng worker:** tạo task có latch, gọi `get(timeout)`/`orTimeout`, xác nhận callback chưa rời latch trước khi main mở nó.
4. **Spring @Async proxy:** gọi qua bean Spring thật, không gọi `this.method()`; inject đúng executor và token.
5. **Hủy tại ranh giới side effect:** dùng fake DB/API để kiểm tra worker không tạo side effect mới sau điểm kiểm tra token được thiết kế; đồng thời xác minh xử lý trường hợp side effect đã xảy ra.
6. **Đóng executor:** cleanup trong `finally`, dùng timeout hữu hạn để tránh test treo và không để worker vô hạn sau khi test.

Test dùng `Thread.sleep(100)` ở main để đoán task đã bắt đầu thường chập chờn trên CI. Latch khiến thứ tự trong ví dụ có bằng chứng.

## 13. Các nhầm lẫn thường gặp

**“`CompletableFuture.cancel(true)` chắc chắn gửi interrupt đến worker.”** Sai. Theo Java SE 17, tham số `mayInterruptIfRunning` không có tác dụng với implementation CompletableFuture.

**“`cancel(true)` trả true nghĩa là worker đã dừng.”** Sai. Đó là trạng thái Future, không phải acknowledgement của worker.

**“`future.isDone()==true` có nghĩa công việc đã xong.”** Sai. Hủy và lỗi đều là trạng thái hoàn tất của Future; worker có thể còn chạy.

**“`join()` ném CancellationException nghĩa là database chưa commit.”** Sai. Side effect có thể đã diễn ra hoặc tiếp tục.

**“`get(timeout)` ném TimeoutException nên task tự bị hủy.”** Sai. Caller chỉ thôi đợi kết quả.

**“`orTimeout` sẽ ngắt thread sau N giây.”** Sai. Nó cập nhật trạng thái CompletableFuture, không điều khiển I/O thực tế.

**“AtomicBoolean tự làm worker thức dậy.”** Sai. Cờ phải được kiểm tra; blocking I/O cần timeout/interrupt phù hợp.

**“Spring @Async trả Future thì Spring chắc chắn ngắt được thread của method khi cancel.”** Không có bảo đảm chung như vậy. Cần xác nhận implementation và thiết kế giao thức stop riêng.

**“Nếu future canceled thì mọi dependent và upstream cũng ngừng chạy.”** Sai. Dependent có thể hoàn tất đặc biệt, upstream không mặc nhiên nhận lệnh dừng.

**“Hủy một job đồng nghĩa có thể rollback mọi giao dịch đã commit.”** Sai. Cancellation là quyết định điều khiển, không phải transaction rollback toàn hệ thống.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** `CompletableFuture.cancel(true)` trả true, worker đang chờ `CountDownLatch.await()`. Worker có chắc nhận `InterruptedException` không?

**Đáp án:** không. CompletableFuture bỏ qua tham số true, nên worker có thể tiếp tục đợi cho tới khi latch được mở hoặc có nguồn interrupt khác.

**Câu 2.** `isCancelled()==true` và `isDone()==true` có chứng minh worker đã thoát không?

**Đáp án:** không. Hai hàm chỉ phản ánh trạng thái kết quả; phải có tín hiệu dừng từ worker.

**Câu 3.** `FutureTask.cancel(true)` khác gì `CompletableFuture.cancel(true)`?

**Đáp án:** FutureTask có thể yêu cầu interrupt thread đang chạy; CompletableFuture tiêu chuẩn không dùng interrupt để điều khiển processing. Cả hai đều không bảo đảm rollback hoặc kết thúc tức thì.

**Câu 4.** Nếu `get(2, SECONDS)` ném `TimeoutException`, task có bị hủy không?

**Đáp án:** không mặc nhiên. Chỉ lượt chờ của caller hết thời gian.

**Câu 5.** Trong ví dụ đầu tiên, vì sao in “Worker ended before release: false”?

**Đáp án:** worker đã bắt đầu nhưng vẫn chờ latch; main chưa mở latch và cancel không interrupt worker.

**Câu 6.** Thiết kế chức năng Cancel cho job Spring @Async chạy lâu thế nào?

**Đáp án:** cần job id, token/status riêng, worker kiểm tra ở điểm an toàn, timeout I/O, phân biệt CANCEL_REQUESTED với CANCELLED; khi có nhiều pod cần tín hiệu/điều phối phân tán.

**Câu 7.** Nếu caller cancel Future ngay sau HTTP POST tạo đơn hàng, có chắc đơn hàng chưa được tạo không?

**Đáp án:** không. Request có thể đã đến remote service; dùng idempotency key, tra cứu trạng thái và/hoặc cơ chế bù trừ phù hợp.

**Câu 8.** Để chứng minh cancel không interrupt worker, nên dùng sleep ngẫu nhiên ở test hay latch?

**Đáp án:** dùng latch để bảo đảm worker đã chạy và chưa thể kết thúc trước khi cancel, rồi quan sát interrupt và cleanup theo thứ tự kiểm soát.

## 15. Tài liệu tham khảo chính thức

- Java SE 17 — `CompletableFuture`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html
- Java SE 17 — `Future`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html
- Java SE 17 — `FutureTask`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/FutureTask.html
- Java SE 17 — `ExecutorService`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
- Java SE 17 — `Thread.interrupt()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
- Spring Framework — Task Execution and Scheduling / `@Async`: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework — `ThreadPoolTaskExecutor`: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="timeout-cancellation-006">Xử lý InterruptedException và bảo toàn tín hiệu ngắt phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý InterruptedException và bảo toàn tín hiệu ngắt phù hợp

## 1. Vì sao InterruptedException quan trọng trong ứng dụng Spring?

Khi một ứng dụng Spring chạy nhiều công việc nền bằng `TaskExecutor`, `ThreadPoolTaskExecutor`, `@Async`, `TaskScheduler` hoặc một `ExecutorService` do ứng dụng sở hữu, nó phải có cách yêu cầu công việc **ngừng sớm**: client không còn cần kết quả, job hết hạn, executor đang shutdown, hoặc một workflow cần hủy các nhánh còn lại.

Trong Java, **interruption** là cơ chế phối hợp: một thread có thể gửi **yêu cầu ngắt** cho thread khác qua `Thread.interrupt()`. Các thao tác blocking có hỗ trợ interruption như `Thread.sleep`, `Object.wait`, `BlockingQueue.take`, `CountDownLatch.await` hoặc `Future.get` có thể phản hồi bằng `InterruptedException`. Đây **không** phải lệnh cưỡng ép giết thread, và cũng không đồng nghĩa tất cả I/O, JDBC query hoặc tác vụ CPU lập tức dừng.

Cách xử lý đúng tùy **ai đang sở hữu vòng đời của công việc**:

- Nếu method **cho phép ném `InterruptedException`**, thường **truyền tiếp (propagate)** ngoại lệ để tầng trên quyết định dừng và cleanup.
- Nếu method **không thể truyền checked exception**, thường **khôi phục interrupt flag** bằng `Thread.currentThread().interrupt()`, rồi **thoát khỏi công việc hoặc chuyển sang hợp đồng hủy có ngữ nghĩa**.
- Nếu code **thực sự sở hữu** tín hiệu và có chính sách xử lý hoàn tất việc hủy, nó có thể tiêu thụ tín hiệu một cách chủ đích; nhưng phải hiểu rõ ranh giới, không được nuốt ngoại lệ rồi tiếp tục nghiệp vụ như chưa có gì xảy ra.

> Quy tắc thực hành: **đừng im lặng nuốt `InterruptedException`**. Truyền tiếp nếu được; nếu phải đổi sang unchecked exception hoặc trả về, khôi phục interrupt flag trước khi rời ranh giới, trừ trường hợp mã đã chủ động xử lý toàn bộ giao thức hủy và được thiết kế để tiêu thụ tín hiệu.

Bài thuộc nhóm **Hết hạn và hủy** của **Spring Framework – concurrency**. Trọng tâm là hiểu status bit, `InterruptedException` và cách viết worker/caller không vô tình phá tín hiệu hủy, đặc biệt với Spring `@Async` và Futures.

## 2. Interrupt là yêu cầu hợp tác, không phải dừng cưỡng bức

### 2.1. Thread có trạng thái interrupted

Mỗi Java thread có một **interrupted status**. API quan trọng:

| API | Ý nghĩa | Có xóa interrupt flag không? |
| --- | --- | --- |
| `someThread.interrupt()` | Yêu cầu ngắt thread được chỉ định, thường đặt trạng thái interrupted hoặc đánh thức một blocking call hỗ trợ ngắt | Không phải API đọc/xóa flag; cách một blocking call phản ứng còn phụ thuộc API |
| `Thread.currentThread().isInterrupted()` | Đọc flag của **thread hiện tại** | **Không** |
| `someThread.isInterrupted()` | Đọc flag của thread tương ứng | **Không** |
| `Thread.interrupted()` | Đọc flag của **thread hiện tại** và **xóa trạng thái đó** | **Có** |
| `Thread.sleep(...)`, `Object.wait(...)`, `CountDownLatch.await()` | Khi bị interrupt trong khi chờ, thường ném `InterruptedException` | **Có**, chúng xóa flag khi ném ngoại lệ |

Cần phân biệt chính xác hai lệnh dễ nhầm:

~~~java
boolean peek = Thread.currentThread().isInterrupted();
// Chỉ quan sát, vẫn giữ trạng thái.

boolean consume = Thread.interrupted();
// Quan sát rồi xóa trạng thái interrupted của current thread.
~~~

Đây là hai **mảnh code minh họa**. `Thread.interrupted()` là method **static**, dù nhìn giống `isInterrupted()`; gọi nó vô tình trong một phương thức trợ giúp có thể làm mất tín hiệu hủy mà những tầng phía sau cần quan sát.

### 2.2. Tại sao bắt InterruptedException lại thấy flag bằng false?

Ví dụ:

~~~java
try {
    Thread.sleep(10_000L);
} catch (InterruptedException ex) {
    System.out.println(Thread.currentThread().isInterrupted());
}
~~~

Khi `sleep` bị interrupt và ném `InterruptedException`, status của thread đã được **xóa theo hợp đồng của sleep**. Vì vậy trong tình huống thường gặp, lệnh `isInterrupted()` trong catch cho `false`, không có nghĩa trước đó không hề có yêu cầu ngắt.

Đây là điểm làm phát sinh lỗi:

~~~java
try {
    queue.take();
} catch (InterruptedException ex) {
    // Chỉ log và tiếp tục vòng lặp — tín hiệu đã bị xóa!
}
~~~

Mảnh code trên cần `queue` phù hợp và không nên được copy làm production pattern. Nếu worker tiếp tục vòng lặp mà không báo cho ai, một shutdown/cancel request có thể bị **bỏ qua**. Người vận hành thấy công việc vẫn chạy dù đã gửi yêu cầu ngắt.

## 3. Ba chiến lược xử lý InterruptedException

### 3.1. Chiến lược A: truyền tiếp ngoại lệ nếu hợp đồng cho phép

Nếu method khai báo `throws InterruptedException`, cách rõ nhất thường là **không bắt** để nó tự truyền lên:

~~~java
public JobResult waitForData(BlockingQueue<JobResult> queue)
        throws InterruptedException {
    return queue.take();
}
~~~

Đây là method hợp lệ khi đặt trong class với import `BlockingQueue` và `JobResult` đã khai báo. Nó không phải chương trình chạy độc lập. Tầng gọi, vốn biết có nên cancel workflow hay không, sẽ nhận được `InterruptedException`.

Nếu có resource cần dọn, dùng `finally` hoặc `try-with-resources`:

~~~java
public void runInterruptibly(BlockingQueue<String> queue)
        throws InterruptedException {
    openResource();
    try {
        String item = queue.take();
        process(item);
    } finally {
        closeResource();
    }
}
~~~

Mảnh code minh họa này cần các method `openResource`, `process`, `closeResource`. Việc `queue.take()` ném `InterruptedException` khiến control đi vào `finally` rồi truyền tiếp exception, trừ khi `closeResource()` ném lỗi khác. Production code cần quyết định cách ưu tiên/ghi nhận lỗi cleanup và có thể dùng `try-with-resources` để giữ suppressed exception.

**Khi truyền tiếp `InterruptedException` đúng như contract, thường không cần tự restore flag trong cùng tầng**. Exception là tín hiệu rõ ràng cho caller; tầng trên chịu trách nhiệm không bỏ mất nó. Nếu mọi tầng chỉ wrap thành một exception khác, cần cân nhắc phục hồi interrupt status trước khi đi qua boundary ấy.

### 3.2. Chiến lược B: không thể ném checked exception, restore rồi thoát

Một `Runnable.run()` không khai báo `throws InterruptedException`. Khi nó cần dùng `sleep`, `take` hoặc `await`, thường chọn:

~~~java
@Override
public void run() {
    try {
        processNextItem();
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return; // Dừng vòng đời Runnable này, không tiếp tục xử lý job.
    }
}
~~~

Đây là **mảnh code minh họa**: `processNextItem()` phải được định nghĩa có khả năng ném `InterruptedException`. Nếu cần log hoặc thông báo job cancellation, thực hiện mà không làm hỏng interrupt contract.

Nếu muốn chuyển thành lỗi unchecked để người giám sát/đối tượng gọi thấy thất bại:

~~~java
try {
    blockingOperation();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Job interrupted", ex);
}
~~~

Cần `blockingOperation()` được định nghĩa. **Cả hai thao tác đều có vai trò riêng**: restore flag để không làm mất tín hiệu của thread, và exception để biểu đạt kết quả tác vụ cho cơ chế giám sát. Chỉ ném `new RuntimeException(ex)` mà không restore có thể khiến tầng quản lý thread không thấy flag từng bị ngắt, dù cause vẫn còn.

### 3.3. Chiến lược C: chuyển thành trạng thái cancelled có chủ đích

Ở một ranh giới điều phối job, một contract có thể định nghĩa rõ `JobResult.cancelled(...)` thay cho exception. Khi đó vẫn phải **dừng việc xử lý tiếp** và cập nhật trạng thái một cách nhất quán:

~~~java
try {
    waitForExternalSignal();
    return JobResult.completed();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return JobResult.cancelled();
}
~~~

Đây là đoạn khái niệm cần `JobResult` và `waitForExternalSignal`. Không nên chỉ trả `JobResult.completed()` trong catch vì caller sẽ tưởng thao tác đã thành công. Nếu phương thức còn chạy các đoạn blocking sau khi restore, nó có thể lại gặp interrupt ngay; vì vậy **restore rồi kết thúc ranh giới** thường dễ hiểu hơn restore rồi tiếp tục chạy.

Trong những framework có **giao thức hủy riêng**, ranh giới sở hữu thread có thể lựa chọn tiêu thụ tín hiệu theo tài liệu, sau khi đã dừng task và ghi trạng thái. Người viết application service không nên tự ý giả định mình là tầng sở hữu toàn bộ vòng đời của thread do Spring quản lý.

## 4. Mẫu sai: nuốt, lặp lại vô hạn, hoặc khôi phục ở sai thời điểm

### 4.1. Nuốt exception rồi tiếp tục

~~~java
while (running) {
    try {
        String job = queue.take();
        process(job);
    } catch (InterruptedException ignored) {
        // Sai: bỏ qua tín hiệu và quay lại queue.take().
    }
}
~~~

Đây là ví dụ **sai có chủ đích**, cần biến `running`, `queue`, `process`. Với `BlockingQueue.take`, khi exception được ném, flag đã xóa; vòng lặp có thể chờ tiếp vô thời hạn, dù hệ thống cần shutdown.

Sửa:

~~~java
while (running) {
    try {
        String job = queue.take();
        process(job);
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        break; // Thoát khỏi vòng lặp worker.
    }
}
~~~

Nếu cleanup phải chạy, bao vòng lặp trong `try/finally`. Đừng đặt một vòng lặp khác phía dưới chỉ để worker bắt đầu nhận việc lại sau khi đã break.

### 4.2. Restore flag rồi tiếp tục sleep trong một vòng lặp

~~~java
while (true) {
    try {
        Thread.sleep(1000L);
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        // Sai nếu tiếp tục vòng lặp: sleep sau sẽ lại bị interrupt ngay.
    }
}
~~~

Đây là mảnh anti-pattern. Khôi phục flag **không phải giấy phép để tiếp tục công việc**. Với nhiều blocking API, status đã set làm lần chờ kế tiếp ném ngay `InterruptedException`, gây vòng lặp bận CPU hoặc log spam.

Sửa bằng `break`, `return` hoặc truyền tiếp, tùy hợp đồng. Muốn tiếp tục vì interruption không có ý nghĩa dừng trong một API cụ thể, phải có chính sách rõ ràng để xử lý/tiêu thụ trạng thái — không mặc định làm điều này trong business task.

### 4.3. Dùng Thread.interrupted() chỉ để kiểm tra

~~~java
if (Thread.interrupted()) {
    logCancellation();
}
// Tín hiệu vừa bị xóa! Một hàm phía sau có thể không còn thấy nữa.
~~~

Đây là đoạn minh họa. Nếu chỉ muốn **nhìn** trạng thái, dùng `Thread.currentThread().isInterrupted()`. Nếu muốn dùng `Thread.interrupted()` để chủ động tiêu thụ, chẳng hạn trong loop chuẩn hóa trạng thái của một thành phần quản lý thread, phải biết chính xác vì sao tiêu thụ an toàn.

### 4.4. Catch Exception quá rộng và mất ý nghĩa

~~~java
try {
    future.get();
} catch (Exception ex) {
    logFailure(ex);
    return null;
}
~~~

Đây là ví dụ sai có chủ đích. `Future.get()` có thể báo `InterruptedException`, `ExecutionException` hoặc `CancellationException` với ý nghĩa khác nhau. Bắt tất cả rồi trả `null` vừa nuốt interruption, vừa biến lỗi thành một kết quả dễ bị coi là thành công.

Hãy tách nhánh `InterruptedException` để giữ tín hiệu, phân loại `ExecutionException.getCause()` theo lỗi thực thi và xử lý cancellation/timeouts đúng contract.

## 5. Ví dụ Java 17 độc lập: flag bị xóa rồi được khôi phục

Demo dùng `CountDownLatch` để điều khiển thread, không dựa vào ngủ một thời gian ngẫu nhiên. Nó chứng minh:

1. Sau khi `await()` bị interrupt và ném `InterruptedException`, flag thường bị xóa.
2. `Thread.currentThread().interrupt()` trong catch thiết lập lại status.
3. `Thread.currentThread().isInterrupted()` chỉ quan sát.
4. `Thread.interrupted()` vừa đọc vừa **xóa** status của thread hiện tại.

Tạo tệp **`InterruptFlagDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class InterruptFlagDemo {

    public static void main(String[] args) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch keepBlocked = new CountDownLatch(1);

        AtomicBoolean flagInCatch = new AtomicBoolean(true);
        AtomicBoolean flagAfterRestore = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            entered.countDown();
            try {
                keepBlocked.await();
            } catch (InterruptedException ex) {
                // await đã xóa interrupted status trước khi throw.
                flagInCatch.set(Thread.currentThread().isInterrupted());
                Thread.currentThread().interrupt();
                flagAfterRestore.set(
                        Thread.currentThread().isInterrupted());
            }
        }, "interruptible-worker");

        worker.start();
        if (!entered.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Worker did not start");
        }

        // Yêu cầu ngắt dù worker vừa mới vào await hay chưa.
        worker.interrupt();
        worker.join(5000L);
        if (worker.isAlive()) {
            throw new IllegalStateException("Worker did not stop");
        }

        System.out.println("caught flag cleared: " +
                !flagInCatch.get());
        System.out.println("flag restored: " +
                flagAfterRestore.get());

        AtomicBoolean peek = new AtomicBoolean(false);
        AtomicBoolean consume = new AtomicBoolean(false);
        AtomicBoolean afterConsume = new AtomicBoolean(true);

        Thread probe = new Thread(() -> {
            Thread.currentThread().interrupt();
            peek.set(Thread.currentThread().isInterrupted());
            consume.set(Thread.interrupted());
            afterConsume.set(Thread.currentThread().isInterrupted());
        }, "flag-probe");

        probe.start();
        probe.join(5000L);
        if (probe.isAlive()) {
            throw new IllegalStateException("Probe did not stop");
        }

        System.out.println("isInterrupted observes: " + peek.get());
        System.out.println("Thread.interrupted observes: " +
                consume.get());
        System.out.println("Thread.interrupted clears: " +
                !afterConsume.get());
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac InterruptFlagDemo.java
java InterruptFlagDemo
~~~

**Năm dòng stdout do chương trình tự in khi chạy bình thường:**

~~~text
caught flag cleared: true
flag restored: true
isInterrupted observes: true
Thread.interrupted observes: true
Thread.interrupted clears: true
~~~

Lý do kết quả ổn định:

- `entered` báo thread đã bước vào method, sau đó main gửi interruption. Nếu interruption đến trước `keepBlocked.await()`, `await` vẫn phát hiện flag và ném `InterruptedException`. Nếu đến trong lúc chờ, nó đánh thức method. Cả hai trường hợp đều vào catch.
- Catch thấy status đã bị xóa bởi `await`. Gọi `interrupt()` trên **current thread** sau đó khôi phục status. Ví dụ chỉ quan sát lại flag, rồi worker kết thúc.
- Thread `probe` riêng tự bật flag, dùng `isInterrupted` để quan sát mà không xóa, rồi dùng `Thread.interrupted()` để tiêu thụ. Ta không can thiệp vào interrupt flag của main thread.
- Hai lần `join(5000)` có giới hạn chờ và kiểm tra `isAlive()` để tránh báo kết quả thành công nếu worker không thoát như dự kiến.

Đây là demo về **semantics**, không phải quy trình dùng `interrupt()` tùy tiện với thread production. Trong thực tế, caller thường dùng `Future.cancel(true)` hoặc lifecycle của executor thay vì giữ reference trực tiếp tới worker thread.

## 6. Future.cancel(true) khác gì với InterruptedException?

`Future.cancel(boolean mayInterruptIfRunning)` là **nỗ lực hủy** task:

- `cancel(false)`: cố hủy Future mà **không yêu cầu interrupt** thread đang chạy thông qua cờ tham số; task đã chạy có thể vẫn hoàn tất công việc thực tế tùy implementation.
- `cancel(true)`: nếu task đang chạy và implementation biết worker thread, có thể **yêu cầu interrupt worker** để nó thoát sớm. Đây **không phải bảo đảm** công việc đã dừng, cũng không bảo đảm downstream I/O bị rollback.
- `isCancelled()`: báo trạng thái hủy theo hợp đồng Future, **không phải bằng chứng** mọi side effect bên ngoài đã chấm dứt.
- `isDone()`: true khi Future đã thành công, thất bại hoặc bị hủy. Nó không đồng nghĩa task đã commit thành công.
- `get()` trên Future bị hủy có thể ném `CancellationException`, không phải `InterruptedException` của worker.

**Đừng nhầm hai thread**:

~~~text
Caller / request thread                      Worker thread
-----------------------                      ----------------------
future.cancel(true)  --------------------->  interrupt attempt
                                             |
                                             +-- sleep / await / take
                                                    |
                                                    +-- InterruptedException
                                                    +-- catch, cleanup,
                                                        propagate/restore

future.get() của caller có thể:
    - CancellationException nếu Future đã cancelled;
    - InterruptedException nếu CHÍNH caller bị interrupt trong lúc chờ;
    - ExecutionException nếu computation hoàn tất lỗi (không bị cancel).
~~~

`InterruptedException` trong method worker và `InterruptedException` của caller khi chờ `Future.get()` là **hai sự kiện ở hai thread khác nhau**. Khôi phục flag cần làm trên **thread vừa bắt exception**, không phải trên một thread khác.

### 6.1. CompletableFuture.cancel(boolean) không bảo đảm interrupt worker

`CompletableFuture` của JDK có semantics đặc biệt: tham số `mayInterruptIfRunning` của `CompletableFuture.cancel(boolean)` **không được dùng để điều khiển quá trình thực thi của worker**. Nó chủ yếu hoàn thành Future theo trạng thái cancelled và thông báo cho dependent stages. Vì vậy **không được suy ra** `someCompletableFuture.cancel(true)` sẽ ngắt I/O hoặc thread thực tế đang chạy.

Trong Spring `@Async`, loại Future được trả về và cách Spring/Executor xây dựng handle có ảnh hưởng tới cancellation. `@Async` trả `Future<T>` từ một executor hỗ trợ cancellation có thể cho phép yêu cầu interrupt; `CompletableFuture<T>` và các stage kết hợp không nên bị hiểu nhầm là một nút "kill task" có bảo đảm. **Kiểm tra API/runtime cụ thể và test cancellation**, thay vì chỉ dựa trên kiểu biến hoặc tên method.

Nếu phải có khả năng dừng một job dài, cân nhắc thiết kế **cancellation token**/flag nghiệp vụ riêng, có các điểm kiểm tra định kỳ, deadline rõ ràng và các I/O operations có timeout. Dùng interruption như một trong các tín hiệu hợp tác, không phải nguồn bảo đảm duy nhất.

### 6.2. Timeout chờ không phải lệnh hủy

~~~java
try {
    return future.get(3, TimeUnit.SECONDS);
} catch (TimeoutException ex) {
    // Caller đã chờ quá hạn; job có thể vẫn chạy.
    boolean requested = future.cancel(true);
    throw new TaskTimeoutException(requested, ex);
}
~~~

Đây là mảnh code minh họa, cần `future`, các import và `TaskTimeoutException` ứng dụng. `get(timeout)` ném `TimeoutException` chỉ nói **caller chờ quá thời hạn**; việc có muốn gọi `cancel(true)` hay không là **quyết định nghiệp vụ**. Không phải mọi timeout đều nên hủy job, ví dụ background report được phép tiếp tục và client sẽ poll trạng thái.

## 7. Worker loop: shutdown hợp tác và cleanup

Một worker dạng vòng lặp thường chờ trên `BlockingQueue`. Đây là một thiết kế đúng về interruption nếu **worker do ứng dụng sở hữu**:

~~~java
public void consume(BlockingQueue<Job> queue)
        throws InterruptedException {
    while (!Thread.currentThread().isInterrupted()) {
        Job job = queue.take();
        process(job);
    }
}
~~~

Đoạn code minh họa cần kiểu `Job` và `process(Job)`. Nếu `queue.take()` bị interrupt, nó ném `InterruptedException` và method truyền tiếp, thay vì nuốt. Nếu `process` không interruptible, yêu cầu dừng có thể chỉ được quan sát sau khi `process` trả về.

Một `Runnable` không thể khai báo `throws InterruptedException`, nên phiên bản tương đương:

~~~java
public final class QueueConsumer implements Runnable {
    private final BlockingQueue<String> queue;

    public QueueConsumer(BlockingQueue<String> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                String message = queue.take();
                handle(message);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            releaseOwnedResources();
        }
    }

    private void handle(String message) {
        // Xử lý một thông điệp. Bản thực tế cần timeout/retry phù hợp.
    }

    private void releaseOwnedResources() {
        // Đóng tài nguyên mà consumer thực sự sở hữu.
    }
}
~~~

Đây là **class minh họa**, cần import `BlockingQueue`; có thể biên dịch sau khi bổ sung import đó nhưng không có `main()` độc lập. Khi `take` ném exception, catch restore flag, finally cleanup, sau đó `run()` kết thúc. Đây là cách không tiếp tục lấy công việc sau khi đã yêu cầu dừng.

**Lưu ý với Spring-managed executor:** nếu một thread thuộc `ThreadPoolTaskExecutor` do Spring quản lý, application task **không sở hữu toàn bộ vòng đời worker thread**. Task nên kết thúc sớm khi nhận yêu cầu ngắt và để executor quyết định tái sử dụng/thay thế worker. Không dùng `Thread.stop()`, không tự thay đổi internal worker state hoặc cố shutdown cả executor bên trong một task không sở hữu nó.

## 8. Ví dụ đầy đủ Spring Framework: Future.cancel(true) và @Async worker

Demo này mô tả đường cancellation có thể quan sát với **`Future<T>` trả về từ một method `@Async`** và bean `ThreadPoolTaskExecutor` do Spring quản lý. Nó sử dụng `CountDownLatch` để xác định task thực sự đã bắt đầu, rồi caller yêu cầu hủy. Worker đang chờ một latch không bao giờ được thả nên có thể phản ứng qua `InterruptedException`.

**Môi trường:** Java 17+, Spring Framework 6.1.14, Gradle 8.5+. Đây là ứng dụng dòng lệnh, không cần Spring Boot.

### 8.1. Cấu trúc

~~~text
spring-interrupt-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/SpringInterruptDemo.java
~~~

**settings.gradle**

~~~groovy
rootProject.name = 'spring-interrupt-demo'
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
    mainClass = 'demo.SpringInterruptDemo'
}
~~~

**src/main/java/demo/SpringInterruptDemo.java**

~~~java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public final class SpringInterruptDemo {

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            CancelWorker worker = context.getBean(CancelWorker.class);
            CountDownLatch started = new CountDownLatch(1);
            CompletableFuture<Boolean> observed = new CompletableFuture<>();

            Future<String> handle = worker.runInterruptibly(
                    started, observed);

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker did not start");
            }

            // Với Future của ThreadPoolTaskExecutor trong cấu hình này,
            // cancel(true) cố gửi interruption tới worker.
            boolean cancellationAccepted = handle.cancel(true);

            // Future cancellation không phải bằng chứng mọi cleanup đã xong;
            // dùng observer riêng để biết worker đã vào catch.
            boolean workerRestored = observed.get(5, TimeUnit.SECONDS);

            System.out.println("cancel accepted: " +
                    cancellationAccepted);
            System.out.println("worker restored flag: " +
                    workerRestored);
            System.out.println("handle is cancelled: " +
                    handle.isCancelled());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class Config {

        @Bean("workerExecutor")
        public ThreadPoolTaskExecutor workerExecutor() {
            ThreadPoolTaskExecutor executor =
                    new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(4);
            executor.setThreadNamePrefix("cancel-worker-");
            return executor;
        }

        @Bean
        public CancelWorker cancelWorker() {
            return new CancelWorker();
        }
    }

    public static class CancelWorker {

        @Async("workerExecutor")
        public Future<String> runInterruptibly(
                CountDownLatch started,
                CompletableFuture<Boolean> observed) {

            CountDownLatch neverReleased = new CountDownLatch(1);
            started.countDown();

            try {
                neverReleased.await();
                return CompletableFuture.completedFuture("unexpected");
            } catch (InterruptedException ex) {
                // 'await' đã xóa flag.
                Thread.currentThread().interrupt();
                observed.complete(
                        Thread.currentThread().isInterrupted());

                // Không xử lý job tiếp sau khi bị ngắt.
                throw new IllegalStateException("Job interrupted", ex);
            }
        }
    }
}
~~~

**Chạy** từ thư mục `spring-interrupt-demo`:

~~~bash
gradle run
~~~

Nếu project có Gradle Wrapper, dùng `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows.

**Stdout của chương trình khi `cancel(true)` của Future được Spring/ThreadPoolTaskExecutor hỗ trợ như dự kiến và hai thao tác chờ không timeout:**

~~~text
cancel accepted: true
worker restored flag: true
handle is cancelled: true
~~~

Đây là ví dụ **kiểm tra một cấu hình cụ thể**, không phải bảo đảm rằng mọi `Future.cancel(true)` hay `CompletableFuture.cancel(true)` trong mọi runtime đều ngắt được worker. Nếu implementation không interrupt worker, `observed.get(timeout)` có thể timeout thay vì in các dòng thành công; trong thực tế phải có fallback và chính sách shutdown. Spring/Gradle có thể in thêm log ngoài ba dòng của chương trình.

### 8.2. Điều demo chứng minh và không chứng minh

- `@EnableAsync` bật xử lý annotation trong context. `CancelWorker` được đăng ký bằng `@Bean`; caller lấy nó từ `ApplicationContext`, nên `@Async("workerExecutor")` có thể qua proxy.
- `@Async` method trả `Future<String>`; phần worker chạy trên pool được đặt tên `cancel-worker-`.
- `started` giúp chắc chắn method đã vào worker trước khi caller hủy; không cần `Thread.sleep` để đoán thời điểm.
- `handle.cancel(true)` yêu cầu hủy Future. Với đường thực thi hỗ trợ ngắt của `ThreadPoolTaskExecutor`, nó gửi interrupt tới worker đang chờ `neverReleased.await()`.
- `InterruptedException` do `await()` ném **xóa flag**, worker gọi `interrupt()` để restore, ghi nhận `true` qua observer rồi thoát bằng exception.
- `handle.isCancelled()` mô tả **Future ở caller**, còn `observed` cho thấy worker đã thật sự xử lý interruption. Không dùng trạng thái Future thay thế cho bằng chứng đã cleanup toàn bộ side effect.
- Không hứa transaction rollback hoặc database driver đã ngừng I/O chỉ vì worker đã nhận interrupt; hủy vẫn là một protocol hợp tác.

Nếu mục tiêu chỉ là quan sát status bit, demo ở mục 5 độc lập với Spring và ít yếu tố runtime hơn. Demo ở mục 8 cho thấy khi đã có Spring proxy, **interrupt vẫn là quy tắc Java**, không có "interrupt flag riêng của Spring".

## 9. Spring @Async: phân biệt lỗi thực thi và lỗi chờ của caller

Khi một caller thực hiện:

~~~java
Future<Report> future = reportWorker.generateAsync();
Report report = future.get();
~~~

Đây là mảnh minh họa cần bean và kiểu `Report`. Có ít nhất ba đường báo lỗi:

1. **Worker bị interrupt:** method đang `sleep`/`await` có thể ném `InterruptedException` trong **worker**. Worker xử lý bằng cách propagate hoặc restore/translate theo contract.
2. **Caller bị interrupt trong lúc chờ `future.get()`:** chính `get()` trên **caller thread** ném `InterruptedException`. Catch ở caller nên restore flag nếu không truyền checked exception, và quyết định có yêu cầu hủy worker hay không.
3. **Worker ném một lỗi và Future hoàn thành lỗi:** `future.get()` thường ném `ExecutionException` bọc nguyên nhân. Nếu Future đã bị cancel, `get()` có thể ném `CancellationException` thay vì ExecutionException.

Đừng phân loại mọi `InterruptedException` thành "worker đã bị hủy"; cần xác định **thread nào** bị ngắt và **operation nào** đang đợi.

### 9.1. Caller chờ Future và bị interrupt

~~~java
try {
    return future.get(5, TimeUnit.SECONDS);
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();

    // Đây là quyết định nghiệp vụ, không phải hành vi bắt buộc:
    future.cancel(true);

    throw new JobWaitInterruptedException(ex);
} catch (TimeoutException ex) {
    throw new JobWaitTimeoutException(ex);
} catch (ExecutionException ex) {
    throw mapExecutionFailure(ex.getCause());
} catch (CancellationException ex) {
    throw new JobWasCancelledException(ex);
}
~~~

Đây là **đoạn code minh họa**, cần khai báo `future`, exception ứng dụng và mapping. Nếu muốn worker tiếp tục xử lý dù request caller không còn chờ, đừng tự động gọi `future.cancel(true)`. Nếu task bắt buộc có kết quả cho job store/broker, một caller bị interrupt không nhất thiết có quyền dừng job.

### 9.2. @Async void và exception handler

Method `@Async void` không trả Future cho caller. Nếu một `InterruptedException` bị bắt rồi chuyển thành exception trong worker, lỗi này không thể tự ném đồng bộ sang caller. Spring có `AsyncUncaughtExceptionHandler` để quan sát exception của asynchronous `void` methods.

~~~java
@Async("workerExecutor")
public void doWork() {
    try {
        blockingStep();
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Work interrupted", ex);
    }
}
~~~

Đây là method minh họa trong Spring-managed service; cần import và `blockingStep`. Với công việc có hợp đồng hủy rõ ràng, có thể ghi status cancelled và trả về; không nhất thiết phải ném error cho mọi lần hủy bình thường. Điều cần tránh là làm mất tín hiệu và tiếp tục tạo side effects.

## 10. Khi nào không thể dùng interrupt để dừng nhanh?

### 10.1. CPU-bound loop phải tự kiểm tra

Một vòng lặp tính toán không chứa blocking calls có thể tiếp tục chạy dù flag đã được set:

~~~java
public long calculate(long max) {
    long total = 0;
    for (long i = 0; i < max; i++) {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException(
                    "Calculation interrupted");
        }
        total += (i % 17);
    }
    return total;
}
~~~

Đây là method Java minh họa cần import `CancellationException`. `isInterrupted()` **không xóa flag**, nên lời gọi này phù hợp để kiểm tra hợp tác. Trong vòng lặp cực lớn, kiểm tra mỗi iteration có thể gây overhead; có thể kiểm tra định kỳ, ví dụ mỗi vài nghìn iterations, với mức trễ phản ứng chấp nhận được.

Một `CancellationException` do ứng dụng ném trong worker không đồng nghĩa Future đã chuyển trạng thái cancelled bằng `Future.cancel`; nó có thể được lưu như computation failure tùy loại Future. Nếu nghiệp vụ cần phân biệt cancellation với failure, thiết kế status/job contract rõ ràng, không chỉ dựa vào tên exception.

### 10.2. Một số I/O không đáp ứng interruption như mong đợi

- Một `BlockingQueue.take()`, `CountDownLatch.await()` hoặc `Thread.sleep()` thường phản hồi interrupt bằng `InterruptedException`.
- Một `InterruptibleChannel` có thể đóng channel và báo `ClosedByInterruptException`, chứ không bắt buộc ném `InterruptedException`.
- Các JDBC drivers, HTTP clients, socket APIs, filesystem I/O hoặc native calls khác nhau có **hợp đồng ngắt khác nhau**; có thể không dừng ngay, bỏ qua interrupt hoặc trả lỗi riêng.
- `synchronized` lock acquisition truyền thống không tương đương `Lock.lockInterruptibly()`; muốn chờ lock có thể hủy, cần chọn primitive tương ứng và handle `InterruptedException`.
- Không dùng `Thread.stop()` vì đây là API không an toàn và đã bị deprecated.

**Timeout ở tầng I/O vẫn cần thiết.** Cancellation signal không thay thế socket read timeout, connect timeout, SQL query timeout, deadline hoặc tài nguyên bị giới hạn. Nếu API chỉ có blocking I/O không interruptible, code có thể phải đợi I/O kết thúc mới thực hiện cleanup; hãy thiết kế timeout và shutdown có giới hạn thay vì giả định `cancel(true)` có thể hủy tức thì.

### 10.3. Interruption không tự rollback transaction

Với Spring `@Transactional` chạy trên worker thread, interruption có thể làm method dừng hoặc ném exception; **transaction behavior** còn tùy cách exception được truyền qua proxy, quy tắc rollback và trạng thái database operation. Nếu method bắt `InterruptedException` rồi trả về thành công, transaction có thể **commit** dù một phần công việc chưa hoàn tất.

Khi cần hủy giao dịch, phải xác định contract rollback rõ ràng, thường bằng exception phù hợp được truyền tới transaction interceptor hoặc transaction status được đánh dấu rollback-only. Không suy ra "thread bị interrupt" tự làm JDBC rollback. Nếu task có side effect ngoài database (gửi email, gọi API), interruption cũng không tự hoàn tác chúng; cần thiết kế compensation/idempotency khi cần.

## 11. Shutdown executor, cancel job và lifecycle Spring

Trong Spring, `ThreadPoolTaskExecutor` là bean có lifecycle. Khi context shutdown, Spring và executor có cấu hình điều phối các task đang chạy, task chờ và thời gian chờ kết thúc. Đừng giả định mọi loại shutdown đều lập tức gửi interrupt đến mọi task hoặc mọi task luôn được cho chạy tới khi xong: hành vi còn tùy cấu hình, trạng thái và phiên bản.

Các thao tác JDK liên quan:

| Operation | Mục tiêu chung | Lưu ý |
| --- | --- | --- |
| `ExecutorService.shutdown()` | Ngừng nhận task mới, thường để task đã nhận chạy tiếp | **Không** phải yêu cầu interrupt trực tiếp tất cả task đang chạy |
| `ExecutorService.shutdownNow()` | Cố dừng task đang chạy (thường bằng interrupt) và trả danh sách task chờ chưa bắt đầu | Best-effort; không hứa mọi worker đã kết thúc |
| `Future.cancel(true)` | Cố hủy một task, có thể interrupt worker nếu implementation hỗ trợ | Không mặc nhiên hủy downstream side effects |
| `Future.cancel(false)` | Cố chuyển Future sang cancelled, không yêu cầu interrupt running task qua tham số | Hành vi thực thi phụ thuộc loại Future |
| `awaitTermination(timeout, unit)` | Chờ các worker của executor kết thúc đến giới hạn | Có thể ném `InterruptedException` cho **thread chờ** |

Ví dụ một service sở hữu `ExecutorService` riêng:

~~~java
ExecutorService pool = Executors.newFixedThreadPool(2);
try {
    // Submit và quản lý các task thuộc service này.
} finally {
    pool.shutdown();
    try {
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            pool.shutdownNow();
        }
    } catch (InterruptedException ex) {
        pool.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
~~~

Đây là một **mảnh code minh họa**; cần import `ExecutorService`, `Executors`, `TimeUnit`, và xác định nghiệp vụ được phép hủy task nào. Trong nhánh catch, chính **thread đang chờ termination** bị interrupt: code yêu cầu shutdownNow rồi **restore interrupt flag** trước khi thoát ranh giới, thay vì nuốt và tiếp tục chờ vô hạn.

Với **Spring-managed executor bean**, thông thường để Spring quản lý shutdown thay vì service gọi `shutdownNow()` từ bên trong một task. Ứng dụng vẫn cần quyết định deadline, những job nào phải hoàn tất, cơ chế job recovery và điều phối shutdown toàn hệ thống.

## 12. Cancellation, CompletableFuture và điểm chờ

`CompletableFuture.join()` không khai báo `InterruptedException` như `Future.get()`. Nó có thể chặn cho tới khi stage hoàn thành và thường ném `CompletionException` khi stage thất bại. **Đừng kết luận `join()` là non-blocking** hay có cơ chế dừng worker vì caller bị ngắt.

Nếu một caller đang `get()` bị interrupt, chiến lược hợp lệ là:

- **Truyền tiếp `InterruptedException`** khi signature/hợp đồng cho phép.
- **Restore và ném unchecked cancellation** nếu API không cho truyền checked exception.
- **Restore và trả trạng thái cancelled** nếu nghiệp vụ cho phép, không trả thành công giả.
- **Quyết định riêng** có gọi `future.cancel(true)` để yêu cầu dừng worker hay không; caller interruption và worker cancellation không phải một điều.

Với `CompletableFuture`, một pipeline có thể có nhiều stage chạy trên nhiều executors. Hủy Future kết quả không đảm bảo tất cả upstream stages và external requests đều đã dừng. Nếu workflow đòi hủy xuyên nhiều công việc, cần tracking handle, cancellation token, timeout ở downstream và giao thức hoàn tất trạng thái rõ ràng.

### 12.1. Timeout và interruption là hai sự kiện khác nhau

| Sự kiện | Tín hiệu thường thấy | Có tự cancel worker không? |
| --- | --- | --- |
| Caller chờ `get(timeout)` quá lâu | `TimeoutException` | Không |
| Caller bị interrupt khi đang `get()` | `InterruptedException` trên caller | Không tự động theo nghĩa API phải interrupt worker |
| Worker bị interrupt trong `sleep/await/take` | `InterruptedException` trên worker, flag thường bị clear | Worker phải xử lý hợp tác |
| `Future.cancel(true)` thành công về trạng thái | Future cancelled; `get()` có thể ném `CancellationException` | Có thể có interrupt attempt, không phải bảo đảm mọi work đã dừng |
| `CompletableFuture.orTimeout(...)` hết hạn | Future exceptional với `TimeoutException` | Không tự đảm bảo ngắt computation |
| Thread chờ `awaitTermination` bị ngắt | `InterruptedException` trên thread chờ | Không tự quyết định shutdown policy |

Một API có timeout thường có nghĩa "ngừng **đợi** sau deadline", không có nghĩa "ngừng mọi xử lý và rollback". Contract phải thể hiện rõ khác biệt này.

## 13. Lưu ý đặc biệt khi dùng ThreadPoolTaskExecutor, @Scheduled và event listeners

### 13.1. Method @Async không nên lờ interruption

~~~java
@Service
public class ImportWorker {
    @Async("importExecutor")
    public CompletableFuture<String> importAsync() {
        try {
            return CompletableFuture.completedFuture(
                    fetchAndImportInterruptibly());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(ex);
        }
    }
}
~~~

Đây là một **class minh họa**, cần import Spring annotations, `CompletableFuture`, method `fetchAndImportInterruptibly()` và bean `importExecutor`. `@Async` chỉ có hiệu lực qua Spring-managed proxy khi bật `@EnableAsync`; self-invocation `this.importAsync()` thường bỏ qua proxy.

Việc trả `failedFuture(ex)` báo lỗi qua Future cho caller, còn khôi phục flag là để **không tiêu thụ im lặng tín hiệu trên thread**. Đừng dùng `failedFuture` để ngụ ý caller đã hủy Future; stage lỗi và stage cancelled có thể có semantics khác nhau.

### 13.2. Scheduled task và callback ở message/event processing

Một `@Scheduled` callback cần quyết định liệu một lần chạy bị interrupt có được đánh dấu failure, aborted, hay retry ở lần lịch kế tiếp. **Đừng đặt catch-all `catch (Exception)` rồi chạy tiếp vòng lặp vô hạn** trong scheduler thread khi cancellation đã được yêu cầu. Nếu scheduler dùng một thread, blocking quá lâu có thể ảnh hưởng các lần trigger khác.

Với message consumer, khi một task bị interrupt **trước khi xử lý thành công**, không được tự động ACK message nếu delivery contract yêu cầu ACK sau thành công. Tùy hệ thống, có thể cần NACK/requeue/durable retry hoặc trạng thái job riêng. Tín hiệu interruption không tự trả lại message đã ack, và code xử lý cleanup có thể cần bảo đảm idempotency.

Với Spring application event listener được đặt vào async executor, caller thường không nhận exception của listener theo cách đồng bộ. Cần quan sát lỗi, quyết định retry và tránh hứa exactly-once delivery khi chỉ có in-memory executor.

### 13.3. Spring bean singleton và ThreadLocal

Worker `@Service` mặc định là singleton; nhiều task có thể gọi chung instance. Interruption không làm mutable fields trở nên thread-safe. Ưu tiên stateless worker, immutable DTO và tránh giữ `currentJobId` hay request-specific state trên field dùng chung.

`ThreadLocal`/MDC/security context/transaction của caller không tự động được mang qua `@Async`. Nếu cần context propagation, dùng mechanism phù hợp hoặc truyền rõ ràng qua tham số, kèm cleanup. Không dùng interrupt flag như một dạng dữ liệu nghiệp vụ liên request; nó thuộc **thread hiện tại**.

## 14. Quan sát và kiểm thử hủy có kiểm soát

### 14.1. Đo các trạng thái riêng biệt

Trong hệ thống sản xuất nên phân biệt:

- **Cancellation requested**: ai yêu cầu hủy, lý do gì, deadline nào.
- **Future cancelled**: trạng thái handle mà caller thấy.
- **Worker observed cancellation**: worker đã thấy flag/ngoại lệ/token.
- **Worker stopped**: worker đã ngừng thực thi code.
- **Cleanup completed**: tài nguyên được đóng, job status được lưu.
- **Downstream side effects**: transaction, email, payment, message ACK thực sự xảy ra đến đâu.

Không gộp các trạng thái này thành `future.cancel(true) == true` rồi kết luận "mọi thứ đã dừng". Đặc biệt với công việc cần survive restart, có thể cần job store, outbox hoặc broker để lưu trạng thái hủy và retry một cách bền vững.

### 14.2. Kiểm thử không lệ thuộc Thread.sleep ngẫu nhiên

Dùng `CountDownLatch` hoặc `Phaser` để kiểm soát trạng thái:

1. Worker báo đã bắt đầu.
2. Caller gửi `interrupt` hoặc `Future.cancel(true)`.
3. Worker quan sát `InterruptedException` ở blocking point.
4. Test kiểm tra trong catch flag đã bị xóa, sau restore flag trở thành true.
5. Test kiểm tra worker **không tiếp tục** xử lý business steps tiếp theo.
6. Tách kiểm tra `Future.isCancelled()` và worker cleanup, không coi là cùng một sự kiện.
7. Luôn đặt timeout cho các bước chờ trong test và cleanup trong `finally` khi có thể.
8. Kiểm tra `cancel(false)` không được hiểu nhầm là interrupt running task.
9. Kiểm tra `CompletableFuture.cancel(true)` với contract đặc thù, tránh giả định như `FutureTask`.
10. Kiểm tra caller bị interrupt khi `get()` và logic khôi phục flag trên caller.
11. Kiểm tra exception mapping không nuốt `InterruptedException` khi bắt `Exception`.
12. Kiểm tra shutdown và retry/idempotency của job có side effects.

### 14.3. Dấu hiệu production code có vấn đề

- Log liên tục `InterruptedException` nhưng worker không kết thúc.
- Thread dump thấy worker vẫn sống sau yêu cầu shutdown, đặc biệt trong loop bắt và nuốt exception.
- Request timeout nhưng task background tiếp tục thực hiện side effect ngoài dự kiến.
- `Future.cancel(true)` trả true, song job status không chuyển hoặc downstream vẫn xử lý.
- Code dùng `Thread.interrupted()` trong helper "peek" nên làm mất flag.
- Method khôi phục flag rồi lại gọi blocking API và nhận exception ngay, tạo vòng lặp bận.
- Có `@Async` nhưng self-invocation khiến code chạy trên caller; `InterruptedException` xuất hiện ở thread không ngờ tới.
- Catch-all ghi log rồi `return null` làm workflow coi việc bị hủy là thành công.
- Bean worker đang shutdown nhưng một producer vẫn tiếp tục gửi task; rejection bị nhầm là interruption.

Khi thấy các triệu chứng này, điều tra **thread nào nhận tín hiệu, API nào ném exception, caller nào sở hữu Future, và job contract yêu cầu điều gì**.

## 15. Những lỗi suy luận thường gặp

| Nhầm lẫn | Điều đúng |
| --- | --- |
| "interrupt() luôn giết thread ngay." | Nó là yêu cầu hợp tác, không tự kết thúc mọi công việc |
| "Bắt InterruptedException thì flag vẫn true." | Nhiều blocking API **xóa flag trước khi ném**, nên cần quyết định propagate/restore |
| "Thread.interrupted() giống isInterrupted()." | `Thread.interrupted()` đọc rồi xóa **flag của thread hiện tại** |
| "Luôn restore rồi tiếp tục while(true)." | Có thể khiến blocking API ném liên tục; thường nên dừng hoặc propagate |
| "Chỉ throw new RuntimeException(ex) là đủ." | Nếu không thể propagate checked signal, thường còn phải restore flag |
| "Luôn cần restore dù đã throws InterruptedException." | Khi truyền tiếp checked exception đúng hợp đồng, không nhất thiết restore ở tầng đó |
| "future.cancel(true) bảo đảm worker dừng." | Best-effort, implementation và hoạt động blocking/CPU quyết định |
| "CompletableFuture.cancel(true) chắc chắn interrupt." | JDK CompletableFuture **không dùng** tham số để điều khiển worker |
| "get(timeout) tự cancel job khi timeout." | Nó chỉ giới hạn thời gian caller chờ |
| "Caller get() ném InterruptedException nghĩa là worker đã bị ngắt." | Đó có thể là **caller thread** bị interrupt trong lúc đợi |
| "Future cancelled nghĩa là transaction đã rollback." | Cancellation state và transaction/side effect là các hợp đồng riêng |
| "ThreadPoolTaskExecutor tự biến mọi I/O thành interruptible." | Không; phụ thuộc API, driver và timeout của từng tầng |
| "catch (Exception) rồi log là đủ." | Có thể nuốt interrupt signal, dẫn đến shutdown không hoạt động |
| "Thread.stop() giúp hủy chắc chắn." | API không an toàn, không dùng để giải quyết task cancellation |

## 16. Checklist code review cho Spring concurrency

- [ ] Method có thể **propagate `InterruptedException`** thì không nuốt tín hiệu.
- [ ] Method không thể ném checked exception thì **restore flag và thoát**, hoặc áp dụng giao thức hủy rõ ràng.
- [ ] Không dùng `Thread.interrupted()` để **peek** vô tình xóa status.
- [ ] Không tiếp tục vòng lặp vô hạn sau khi đã restore flag mà không có lý do được tài liệu hóa.
- [ ] `Future.cancel(true)` được hiểu là **nỗ lực hủy**, không phải xác nhận worker dừng/cleanup xong.
- [ ] Không kỳ vọng `CompletableFuture.cancel(true)` tự ngắt mọi work upstream.
- [ ] Dùng timeout ở I/O và downstream, không chỉ timeout khi caller chờ.
- [ ] Phân biệt caller bị interrupt lúc `Future.get()` với worker bị interrupt trong blocking task.
- [ ] Có xử lý riêng `TimeoutException`, `ExecutionException`, `CancellationException` và rejection.
- [ ] `@Async` được gọi qua Spring-managed proxy; executor phù hợp và có giới hạn tài nguyên.
- [ ] Shutdown executor thuộc đúng thành phần sở hữu; task không tự đóng cả pool khi không có quyền.
- [ ] Có cleanup và ghi trạng thái job/ACK phù hợp nếu thao tác đã dừng giữa chừng.
- [ ] Có test hủy bằng latch, bao gồm trường hợp race giữa hoàn thành và cancel.
- [ ] Các side effects có idempotency hoặc compensation khi nghiệp vụ yêu cầu.
- [ ] Không xem interruption là cơ chế security/authorization hay tự động rollback transaction.

## 17. Câu hỏi tự kiểm tra

1. **Interruption có giết thread ngay không?** Không; nó yêu cầu hợp tác và phụ thuộc code/operation quan sát.
2. **Sau khi sleep ném `InterruptedException`, flag thường còn true không?** Không. `Thread.sleep` xóa flag khi ném exception do interrupt.
3. **Nếu method khai báo `throws InterruptedException`, thường nên làm gì?** Truyền tiếp để tầng trên xử lý, đồng thời cleanup qua finally nếu cần.
4. **Nếu `Runnable.run()` bắt `InterruptedException`, nên làm gì?** Thường restore flag, dừng task và cleanup; hoặc chuyển thành trạng thái hủy đã được thiết kế.
5. **Vì sao chỉ `catch (InterruptedException e) { }` là nguy hiểm?** Nó mất tín hiệu và có thể tiếp tục chạy sau shutdown/cancel.
6. **`Thread.interrupted()` khác `isInterrupted()` thế nào?** Method static đầu tiên đọc và xóa flag current thread; method thứ hai chỉ đọc.
7. **Nên restore rồi tiếp tục sleep trong while(true) không?** Thường không. Lần sleep kế có thể ném ngay, tạo busy loop.
8. **`cancel(true)` trên Future có bảo đảm interrupt worker không?** Không; chỉ cố gửi yêu cầu nếu implementation hỗ trợ và worker còn chạy.
9. **`CompletableFuture.cancel(true)` có bảo đảm interrupt task không?** Không. Tham số không điều khiển computation theo contract JDK.
10. **`get(timeout)` bị timeout nghĩa là job đã dừng chưa?** Chưa. Đây là timeout **đợi** của caller.
11. **Caller đang get() ném InterruptedException: phải restore flag của thread nào?** Của **caller thread** đang catch, không phải worker.
12. **Đối với CPU loop không block, làm sao phản hồi hủy?** Kiểm tra `isInterrupted()` hoặc cancellation token ở các điểm hợp lý.
13. **Một `@Async` singleton worker bị interrupt có tự rollback database không?** Không; rollback phụ thuộc transaction boundary và lỗi được truyền qua interceptor.
14. **Vì sao cần quan sát worker cleanup riêng với Future.isCancelled?** Vì Future state không chứng minh side effects đã dừng.
15. **Có nên gọi `shutdownNow()` ngay trong một @Async task bình thường không?** Không nếu task không sở hữu executor; để owner/context quản lý lifecycle.
16. **Một message handler bị interrupt giữa chừng có nên luôn ACK không?** Không; quyết định ACK/NACK dựa trên delivery contract và trạng thái xử lý.
17. **Với `Thread.interrupted()`, nếu cần giữ tín hiệu cho tầng sau thì sao?** Không gọi method chỉ để nhìn; nếu đã tiêu thụ mà contract yêu cầu propagate, restore status phù hợp.
18. **Có trường hợp chủ động tiêu thụ interruption không?** Có, khi một thành phần sở hữu protocol hủy đã xử lý đầy đủ tín hiệu và contract cho phép; không nên làm ngầm trong business task.

**Kết luận:** `InterruptedException` là **tín hiệu hợp tác** của Java khi một thread bị yêu cầu ngừng lúc đang chờ. Trong Spring concurrency, nguyên tắc an toàn là **propagate nếu có thể; nếu không, restore flag bằng `Thread.currentThread().interrupt()` và dừng hoặc báo trạng thái hủy thích hợp**. Đồng thời phân biệt việc hủy `Future` với việc worker thực sự dừng, timeout chờ với timeout công việc, và các thao tác mà interrupt không đủ để dừng.

### Tài liệu chính thức

- [Java 17 — Thread (interrupt, interrupted, isInterrupted)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html)
- [Java 17 — InterruptedException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/InterruptedException.html)
- [Java 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Java 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework 6.1 — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework 6.1 — @Async](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/annotation/Async.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="timeout-cancellation-007">Đặt timeout riêng cho truy cập cơ sở dữ liệu hoặc lời gọi mạng</a>

<details>
<summary>Xem chi tiết</summary>

# Đặt timeout riêng cho truy cập cơ sở dữ liệu hoặc lời gọi mạng

> **Module:** Spring Framework – concurrency  
> **Chủ đề cha:** Hết hạn và hủy  
> **Mục tiêu:** Hiểu vì sao timeout của `Future`, `CompletableFuture`, `@Async` hoặc HTTP request đầu vào **không thay thế timeout của thao tác I/O**; thiết kế timeout riêng khi lấy connection, chạy SQL và gọi HTTP; phối hợp với deadline tổng, hủy tác vụ, transaction, retry và giám sát trong ứng dụng Spring.

## 1. Quy tắc quan trọng nhất: đặt timeout nơi đang chờ tài nguyên

Một endpoint hoặc `@Async` worker có thể đang chờ:

- Có worker trống trong thread pool, hoặc chờ job được lấy khỏi queue.
- Mượn `Connection` từ JDBC connection pool.
- Kết nối TCP/TLS đến database, gửi SQL, chờ query trả kết quả hoặc chờ đọc socket.
- Mở kết nối tới HTTP service, chờ phản hồi và đọc dữ liệu.
- Chờ một `CompletableFuture` hoàn thành ở phía caller.

Đây là **những điểm chờ khác nhau**. Đặt `future.orTimeout(2, TimeUnit.SECONDS)` chỉ làm thay đổi trạng thái quan sát của future khi hết hạn; nó không chứng minh JDBC statement hoặc HTTP request đang blocking đã bị dừng. Worker có thể vẫn giữ connection và tiếp tục thực hiện I/O trong khi caller đã nhận timeout.

**Nguyên tắc:** Đặt giới hạn ở lớp **thực thi thao tác thật** (JDBC driver/SQL statement, HTTP client, network stack), đồng thời đặt một **deadline end-to-end** ở lớp nghiệp vụ để không tiếp tục các bước vô ích sau hạn. Hai loại giới hạn bổ sung cho nhau, không thể dùng một cái thay mọi cái khác.

## 2. Sơ đồ các lớp timeout trong một công việc Spring

Ví dụ luồng lấy trạng thái đơn hàng từ DB rồi gọi một hệ thống khác:

~~~text
HTTP request / job handler -- [deadline nghiệp vụ tổng]
        |
        v
Spring @Async / ThreadPoolTaskExecutor
        |-- queue wait: task có thể chưa bắt đầu
        v
DataSource.getConnection()
        |-- connection-pool acquisition timeout
        |-- JDBC driver connect/login timeout khi phải tạo connection
        v
JdbcTemplate.query(...)
        |-- Statement / query timeout
        |-- JDBC socket read timeout
        |-- database-side statement/lock timeout (nếu cấu hình)
        v
HTTP client gọi service phụ thuộc
        |-- TCP connection timeout
        |-- response/request timeout
        |-- connection-pool acquisition, read/write/idle timeout tùy thư viện
        v
Xử lý kết quả và trả response
        |
        +-- Future/CompletableFuture timeout ở người chờ
~~~

**Một timeout không tự giới hạn tất cả các đoạn ở phía dưới.** Chẳng hạn `HikariCP.connectionTimeout=500ms` không có nghĩa một `SELECT` sau khi lấy được connection cũng chỉ chạy 500ms. Tương tự, `HttpClient.connectTimeout=400ms` chỉ giới hạn việc thiết lập **kết nối mới**, không phải toàn bộ thời gian server xử lý request và trả dữ liệu.

Đọc kết quả timeout cần xác định **cơ chế nào đã hết hạn**. Một lỗi đợi connection khác với SQL query timeout, và cả hai khác với remote HTTP timeout. Nếu không phân biệt, hệ thống có thể retry sai hoặc tăng pool size trong khi nguyên nhân thật nằm ở DB/server.

## 3. Chọn timeout theo ngân sách thời gian, không theo một con số duy nhất

Giả sử yêu cầu nghiệp vụ phải hoàn thành trong khoảng **3 giây**. Một cấu hình minh họa có thể dùng các ngân sách:

| Đoạn chờ | Ví dụ thời gian | Lớp cần áp |
| --- | ---: | --- |
| Mượn connection từ pool | 250 ms | HikariCP `connectionTimeout` |
| Thực thi SQL | 800 ms | JDBC `Statement.setQueryTimeout` (độ phân giải giây) hoặc database-side timeout phù hợp |
| Một HTTP call sau DB | 1.200 ms tổng | HTTP client request/response timeout; connect nằm **trong** budget HTTP |
| Phần còn lại | 750 ms | serialization, scheduling, logging, tải bất thường, sai số thời gian |

Các giá trị trên chỉ là **ví dụ tính ngân sách**, không phải cấu hình khuyến nghị áp dụng cứng. `Statement.setQueryTimeout` nhận **giây nguyên**, nên 800ms không thể truyền trực tiếp vào nó; nếu cần giới hạn dưới giây, phải dùng giải pháp DB/driver riêng hoặc thiết kế deadline phù hợp. Tài liệu từng driver quyết định độ phân giải và việc cancel thực tế.

Không cộng `connectTimeout` và request timeout như hai thời lượng độc lập nếu request timeout **đã bao trùm** giai đoạn thiết lập kết nối. Đồng thời, đừng đánh đồng tổng timeout cấu hình với một bảo đảm thời gian cứng: DNS, scheduler, rollback, driver và hệ thống bên ngoài có thể có hành vi khác.

Nên biểu diễn deadline tại mỗi yêu cầu bằng một **thời điểm hết hạn** (dùng monotonic clock khi đo duration trong cùng JVM). Trước mỗi bước còn lại, kiểm tra thời gian còn lại; không bắt đầu một lời gọi vốn không còn cơ hội hoàn tất hợp lệ.

## 4. JDBC: timeout lấy connection khác timeout thực thi SQL

Khi dùng Spring với `DataSource`/HikariCP, thường có nhiều giới hạn:

| Cơ chế | Kiểm soát | Không kiểm soát |
| --- | --- | --- |
| HikariCP `connectionTimeout` (ms) | Tối đa thời gian chờ **mượn** connection từ pool khi pool không thể cấp connection | Thời gian một câu SQL chạy sau khi mượn được |
| HikariCP `validationTimeout` (ms) | Thời gian kiểm tra connection còn sống trong quá trình validation | Timeout của câu query nghiệp vụ |
| JDBC driver connect/login timeout | Giai đoạn kết nối hoặc login đến DB, theo driver | Mọi SQL chạy trên connection đã thiết lập |
| JDBC `Statement.setQueryTimeout(seconds)` | Thời gian chờ statement thực thi theo JDBC và hỗ trợ driver | Thời gian đợi pool cấp connection trước đó |
| JDBC driver socket read timeout | Thời gian đọc socket khỏi DB theo driver (thường là khoảng chờ cho thao tác đọc) | Không nhất thiết tương đương query timeout hoặc transaction timeout |
| Database statement/lock timeout | Giới hạn do server DB áp dụng cho statement/lock theo engine | HTTP call xảy ra bên ngoài DB |
| Spring transaction timeout | Thời gian cho transaction khi transaction manager hỗ trợ và các thao tác tham gia được cấu hình đúng | Không phải công tắc tổng để hủy mọi HTTP/network operation |

Ví dụ: pool có 10 connection, 10 truy vấn dài chiếm hết. Task thứ 11 có thể chờ đến `connectionTimeout` rồi thất bại **chưa chạy một SQL nào**. Dù `JdbcTemplate.setQueryTimeout(2)`, giới hạn 2 giây của SQL không chữa thời gian chờ lấy connection.

Ngược lại, khi đã lấy được connection ngay nhưng một `SELECT` chạy lâu, timeout mượn pool không giới hạn truy vấn đó. Cần query/statement/server timeout.

## 5. HikariCP: đặt thời gian đợi connection trong Spring Boot

Nếu dự án dùng **Spring Boot + HikariCP**, có thể cấu hình pool acquisition timeout bằng thuộc tính Boot:

~~~yaml
spring:
  datasource:
    hikari:
      connection-timeout: 500
      validation-timeout: 400
      maximum-pool-size: 10
~~~

Các số theo **milliseconds**; đây là ví dụ dùng cho kiểm thử/ngữ cảnh latency thấp, không phải giá trị production nên áp dụng nguyên xi. Hikari yêu cầu `validationTimeout` thấp hơn `connectionTimeout` và cũng có ngưỡng tối thiểu. `maximum-pool-size` quyết định trần số connection đang được quản lý bởi pool; tăng mù quáng có thể chuyển nghẽn từ pool ứng dụng sang DB.

Khi một lời gọi `DataSource.getConnection()` không được phục vụ trong `connectionTimeout`, Hikari báo lỗi acquisition (thường là `SQLException`, được Spring dịch nếu đi qua abstraction tương ứng). Đây là **pool timeout**; không mặc định JDBC statement đã chạy hay DB vừa cancel query.

Cũng cần cấu hình **JDBC driver connect/login timeout** nếu việc tạo connection mới có thể treo lâu. `connectionTimeout` của pool và connect timeout của driver là hai chính sách khác nhau; tên tương tự không làm chúng đồng nhất.

Với Spring Framework không dùng Boot, các khóa `spring.datasource.hikari.*` không tự có hiệu lực. Bạn phải cấu hình `HikariDataSource` hoặc connection pool đang dùng qua cách cấu hình riêng của ứng dụng.

## 6. JDBC chuẩn: giới hạn chính truy vấn bằng Statement.setQueryTimeout

JDBC có `Statement.setQueryTimeout(int seconds)`, được `PreparedStatement` kế thừa. Nó áp thời gian chờ statement thực thi, tính theo **giây**. Driver được kỳ vọng nhận biết hết hạn và cố gắng hủy câu lệnh; nhưng thời điểm dừng thực tế, cách driver gửi cancel, trạng thái connection và kết quả side effect cần xem riêng theo driver/DB.

Đoạn JDBC minh họa (giả định có `DataSource dataSource` hợp lệ):

~~~java
String sql = "select status from orders where id = ?";

try (Connection connection = dataSource.getConnection();
     PreparedStatement statement = connection.prepareStatement(sql)) {

    statement.setQueryTimeout(2);  // 2 giây, chỉ cho statement này
    statement.setLong(1, orderId);

    try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
            throw new IllegalStateException("Order not found");
        }
        return rs.getString(1);
    }
}
~~~

Đây là **đoạn ghép vào phương thức Java**, không phải chương trình độc lập; cần import `java.sql.Connection`, `PreparedStatement`, `ResultSet`, `DataSource` và biến `orderId`. `try-with-resources` đóng result set, statement và connection theo đường thành công hoặc lỗi.

Cần phân biệt:

1. `setQueryTimeout(2)` không bao gồm thời gian `dataSource.getConnection()` đã mất ở phía trên.
2. Nếu SQL timeout, JDBC có thể ném `SQLTimeoutException`; Spring JDBC thường chuyển các `SQLException` theo hệ thống `DataAccessException`. Không ràng buộc cứng ứng dụng vào một message exception cụ thể giữa nhiều driver.
3. Timeout thường **yêu cầu** hủy statement, không đồng nghĩa có thể hoàn tác mọi lệnh đã được DB thực thi, commit hoặc gửi side effect.
4. Timeout JDBC không thay thế network socket timeout: driver có thể cần chờ giao thức hủy hoặc mất kết nối, tùy implementation.
5. SQL batching, stream kết quả lớn và các phần đọc `ResultSet` có thể có phạm vi timeout khác nhau giữa driver. Hãy kiểm tra tài liệu và thử nghiệm DB thực tế.

Không dùng `Thread.interrupt()` hoặc `CompletableFuture.orTimeout()` như cách duy nhất giới hạn SQL blocking. Cơ chế driver/server mới là điểm chính để phát tín hiệu cancel/tránh chờ không có hạn.

## 7. Spring JdbcTemplate: cấu hình query timeout

Spring JDBC cung cấp `JdbcTemplate.setQueryTimeout(int)`. Mặc định của `JdbcTemplate` là **-1**: không áp một giá trị timeout cụ thể lên statement và để driver sử dụng mặc định. Khi đặt giá trị dương, `JdbcTemplate` cố gắng áp giới hạn khi tạo statement.

Một cấu hình Spring Framework dùng chung một template:

~~~java
import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class DatabaseTimeoutConfig {
    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setQueryTimeout(2); // giây
        return template;
    }
}
~~~

Đây là **đoạn cấu hình tích hợp Spring**, cần dependency `spring-jdbc` và bean `DataSource` được tạo trước đó. Khi một service dùng `JdbcTemplate` này để thực thi SQL, các statement do template quản lý chịu query timeout áp dụng, trừ khi có ràng buộc khác (đặc biệt timeout transaction).

Ví dụ service sử dụng:

~~~java
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderStatusReader {
    private final JdbcTemplate jdbc;

    public OrderStatusReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String status(long orderId) {
        return jdbc.queryForObject(
            "select status from orders where id = ?",
            String.class,
            orderId);
    }
}
~~~

Đây là **ví dụ ghép vào project**, giả định DB có bảng `orders`, cột `id` và `status`; không thể chạy độc lập nếu không có datasource/schema. `queryForObject` cũng có thể báo lỗi không tìm thấy hoặc quá nhiều hàng, hoàn toàn khác với timeout; không gộp chúng thành cùng một loại retry.

**Điểm tinh tế:** Khi có transaction timeout đang hiệu lực, `JdbcTemplate` thông thường sử dụng **thời gian còn lại của transaction** để áp lên statement, thay thế giá trị query timeout riêng. Đừng viết quy tắc sai rằng hệ thống luôn tự chọn giá trị nhỏ nhất trong hai số do bạn khai báo. Hãy kiểm tra Spring transaction manager và hành vi thực tế.

## 8. Transaction timeout không phải một đồng hồ hủy mọi I/O

Spring có thuộc tính `@Transactional(timeout = n)` với **n tính bằng giây**, tùy transaction manager hỗ trợ. Trong JDBC với `DataSourceTransactionManager` (hoặc cấu hình tương đương), Spring có thể truyền thời gian còn lại của transaction vào statement do `JdbcTemplate` tạo.

~~~java
@Service
public class InventoryService {
    private final JdbcTemplate jdbc;

    public InventoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(timeout = 4, readOnly = true)
    public Integer available(long skuId) {
        return jdbc.queryForObject(
            "select quantity from inventory where sku_id = ?",
            Integer.class,
            skuId);
    }
}
~~~

Đoạn này cần import Spring JDBC, `@Transactional`, `@Service` và phải được gọi qua Spring transaction proxy; không phải project chạy một mình. Hành vi timeout chính xác phụ thuộc transaction manager, propagation hiện tại, JDBC driver và phạm vi transaction.

**Các hiểu nhầm cần tránh:**

- `@Transactional(timeout=4)` không bảo đảm một HTTP call thực hiện bên trong transaction cũng bị hủy sau bốn giây.
- Không phải mọi transaction manager đều áp deadline xuống mọi resource/driver giống nhau.
- Nếu method tham gia một transaction đã tồn tại, timeout của annotation mới không nhất thiết tạo đồng hồ mới; thuộc tính timeout được định nghĩa theo propagation và việc tạo transaction.
- Spring transaction timeout không thay thế thời gian đợi lấy connection từ pool hoặc socket timeout của driver trong mọi cấu hình.
- `@Transactional` ở method tự gọi bằng `this.method()` trong proxy mode có thể bị bỏ qua. Với `@Async`, transaction imperative cũng không tự đi từ caller thread sang async worker.

Nếu nghiệp vụ vừa dùng DB vừa gọi remote API, **không nên giữ transaction và DB connection mở suốt một lời gọi mạng chậm** trừ khi đã đánh giá kỹ. Làm như vậy có thể giữ lock/connection, đẩy pool vào nghẽn. Thường cần chia transaction boundary hoặc thiết kế outbox/saga phù hợp nghiệp vụ.

## 9. Driver và database cũng có timeout riêng

Cùng khái niệm JDBC nhưng driver triển khai chi tiết rất khác nhau. Với **PostgreSQL JDBC (pgJDBC)**, ví dụ các tham số URL thường gặp:

~~~text
jdbc:postgresql://db.example.invalid:5432/orders?connectTimeout=3&socketTimeout=8
~~~

Đây là **URL minh họa**, không phải địa chỉ DB thật và không kèm credentials. Trong pgJDBC, `connectTimeout` là giới hạn thiết lập socket tính bằng giây, còn `socketTimeout` là giới hạn socket read tính bằng giây. Driver cũng có `queryTimeout` dạng tham số mặc định khi statement không đặt timeout riêng.

Không lấy bảng đơn vị của pgJDBC rồi áp cho MySQL, SQL Server hoặc Oracle JDBC: một driver khác có thể dùng milliseconds, tên property khác hoặc cách hủy khác. Hãy chọn theo driver/version thực tế của dự án.

Phía **PostgreSQL server** còn có `statement_timeout` để giới hạn statement trực tiếp trên DB, và `lock_timeout` cho việc chờ lock. Ví dụ trong một transaction kiểm soát được:

~~~sql
BEGIN;
SET LOCAL statement_timeout = '2s';
SELECT status FROM orders WHERE id = 42;
COMMIT;
~~~

Đây là **SQL minh họa PostgreSQL**, cần bảng hợp lệ và quyền DB; không áp dụng nguyên xi trên database khác. `SET LOCAL` làm timeout có phạm vi transaction. Cần tránh thay đổi session-level setting rồi trả connection vào pool mà không quản lý reset đúng cách.

Timeout server mang lại một tầng bảo vệ khi caller/driver không còn chờ, nhưng không phải mọi thao tác đã có tác dụng phụ đều tự hoàn tác như ý định nghiệp vụ. Đối với ghi dữ liệu, thiết kế transaction và trạng thái commit là yếu tố quyết định.

## 10. HTTP: connect timeout không phải request/response timeout

Khi gọi dịch vụ qua mạng, cần phân biệt:

| Giới hạn | Ý nghĩa | Khi nào hữu ích |
| --- | --- | --- |
| Connect timeout | Thời gian thiết lập **kết nối mới** đến remote endpoint | Host không phản hồi TCP connect, firewall/drop |
| Connection pool acquisition timeout | Thời gian đợi một connection khả dụng từ **HTTP client connection pool**, nếu thư viện có pool | Client bị cạn connection dù remote còn hoạt động |
| Request/response timeout | Thời gian mong đợi remote trả response theo chính sách client | Server xử lý lâu hoặc response bị chậm |
| Socket read/idle timeout | Thời gian một thao tác đọc không có dữ liệu, tùy thư viện và transport | Đường truyền đứng hoặc streaming không có tiến triển |
| Total deadline | Thời điểm toàn nghiệp vụ phải hoàn tất | Tránh tiếp tục các bước sau khi kết quả đã hết giá trị |

Không phải HTTP client nào cũng có đầy đủ các thuộc tính trên hoặc cùng ý nghĩa. Đặc biệt, **read timeout không luôn là tổng thời gian của toàn response body**: nếu thư viện chỉ đo khoảng im lặng giữa các lần đọc, server gửi một byte định kỳ vẫn có thể làm tổng thời gian rất dài. Với streaming, cần thêm giới hạn tổng thời gian hoặc lượng dữ liệu nếu nghiệp vụ đòi hỏi.

Một kết nối đã được tái sử dụng thường **không trải qua connect mới**, nên chỉ đặt connect timeout không đủ bảo vệ khỏi remote xử lý chậm. Lệnh gọi có thể chờ response rất lâu dù kết nối đã tồn tại.

## 11. Java 17 HttpClient: cấu hình connect + request timeout riêng

JDK 17 hỗ trợ `java.net.http.HttpClient` và `HttpRequest`:

~~~java
HttpClient client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofMillis(500))
    .build();

HttpRequest request = HttpRequest.newBuilder(
        URI.create("https://api.example.invalid/orders/42"))
    .timeout(Duration.ofSeconds(2))
    .GET()
    .build();

HttpResponse<String> response =
    client.send(request, HttpResponse.BodyHandlers.ofString());
~~~

Đây là **đoạn tích hợp**, cần import `HttpClient`, `HttpRequest`, `HttpResponse`, `URI`, `Duration`, đồng thời xử lý `IOException` và `InterruptedException`; hostname `example.invalid` chỉ dùng minh họa, không phải endpoint thật.

Cơ chế:

- `HttpClient.Builder.connectTimeout(Duration)` áp cho việc **thiết lập kết nối mới**. Nếu kết nối được reuse, timeout này không phải đồng hồ độc lập để giới hạn toàn response.
- `HttpRequest.Builder.timeout(Duration)` đặt timeout của **request**; trong API JDK 17, nếu response chưa nhận trong hạn, `HttpTimeoutException` xuất hiện ở `send` hoặc `sendAsync` hoàn tất ngoại lệ. Đây không đơn giản là “TCP socket read timeout”.
- `HttpConnectTimeoutException` là một subtype của `HttpTimeoutException`, cho phép phân loại lỗi kết nối so với lỗi phản hồi khi cần.
- Tất cả timeout Duration dùng ở đây phải là **giá trị dương**; đừng dùng `Duration.ZERO` với giả định đồng nghĩa vô hạn.
- Một remote HTTP server **có thể vẫn xử lý request** sau khi client timeout. Đặc biệt khi gửi POST/PUT có side effect, retry phải có idempotency key hoặc cơ chế kiểm tra kết quả.

Cấu hình này tốt hơn chỉ gọi `CompletableFuture.orTimeout` ở bên ngoài bởi giao thức HTTP client biết đến timeout của request thật. Dù vậy, cancellation và đóng connection cụ thể vẫn phụ thuộc transport, body handler, version và tình huống.

## 12. Ví dụ Java 17+ chạy độc lập: HTTP server cố tình trả lời chậm

Để quan sát timeout mà **không cần internet hay một server bên ngoài**, dùng HTTP server local của JDK. Server chậm 700ms trước khi gửi response; client đặt request timeout 120ms. Chương trình in một dòng xác định khi client phát hiện timeout.

Tạo file **NetworkTimeoutDemo.java**:

~~~java
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class NetworkTimeoutDemo {
    private static void slowResponse(HttpExchange exchange) {
        try {
            Thread.sleep(700);
            byte[] body = "ready".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException disconnected) {
            // Client may have timed out before this response was written.
        } finally {
            exchange.close();
        }
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(
            new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService serverWorkers = Executors.newSingleThreadExecutor();
        server.setExecutor(serverWorkers);
        server.createContext("/slow", NetworkTimeoutDemo::slowResponse);
        server.start();

        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(300))
                .build();
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:"
                        + server.getAddress().getPort() + "/slow"))
                .timeout(Duration.ofMillis(120))
                .GET()
                .build();

            try {
                client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("Unexpected: request succeeded");
            } catch (HttpTimeoutException timeout) {
                System.out.println("HTTP request timed out");
            }
        } finally {
            server.stop(0);
            serverWorkers.shutdownNow();
        }
    }
}
~~~

Chạy trên **JDK 17+**:

~~~bash
javac NetworkTimeoutDemo.java
java NetworkTimeoutDemo
~~~

**Stdout khi máy cho phép khởi tạo server/client local bình thường**:

~~~text
HTTP request timed out
~~~

Demo đã được biên dịch và chạy thành công; không cần dependency Spring, database hoặc internet. Server chỉ bind loopback `127.0.0.1` trên một port trống tự chọn. Vì response chậm hơn request timeout, `HttpTimeoutException` được bắt và chương trình in thông báo. `finally` dừng server và giải phóng executor phục vụ server.

**Điểm học quan trọng:** hết hạn ở client **không có nghĩa server đã bị JVM khách ra lệnh dừng công việc**. Nếu server đang làm một thao tác có side effect, dù caller không còn chờ phản hồi, trạng thái ở phía server vẫn có thể đã thay đổi. Test này chỉ chứng minh client thực thi request-level timeout tại lớp I/O.

`com.sun.net.httpserver.HttpServer` thuộc module `jdk.httpserver` đi kèm **JDK**, phục vụ demo kiểm thử local; nó không phải Spring Boot embedded server và không dành để thay production web server.

## 13. Spring RestClient: chọn request factory và cấu hình HTTP timeout

Trong **Spring Framework 6.2**, một `RestClient` dùng `JdkClientHttpRequestFactory` có thể nhận `HttpClient` JDK đã cấu hình connect timeout, đồng thời đặt response/read timeout ở request factory:

~~~java
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class HttpClientTimeoutConfig {
    @Bean
    RestClient stockRestClient() {
        HttpClient jdkClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(500))
            .build();

        JdkClientHttpRequestFactory factory =
            new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(Duration.ofSeconds(2));

        return RestClient.builder()
            .requestFactory(factory)
            .baseUrl("https://stock.example.invalid")
            .build();
    }
}
~~~

Đây là **đoạn cấu hình Spring**, cần dependency `spring-web` và URL remote thật khi chạy; `example.invalid` cố ý không trỏ tới hệ thống thật. Cặp giá trị là ví dụ cho connect/response timeout, không phải lời khuyên chọn cố định cho mọi API.

Tên gọi `setReadTimeout` và cách mapping sang native HTTP client **phụ thuộc request factory/version**. Đừng sao chép nguyên giá trị cho Apache HttpComponents, Reactor Netty, Jetty hoặc OkHttp rồi giả định cùng semantics. Spring có nhiều `ClientHttpRequestFactory`; hãy kiểm tra runtime thực sự chọn factory nào.

Với **Spring WebClient** dựa trên Reactor Netty, các timeout thường cấu hình ở lớp Reactor Netty `HttpClient`: connect timeout và response timeout là những cơ chế khác nhau. Thay một property không được kết nối vào `WebClient` thật sẽ không có tác dụng. Trong hệ thống reactive, tránh `block()` trên event-loop và sử dụng timeout/cleanup phù hợp pipeline.

**Không đánh đồng** `spring.mvc.async.request-timeout` của Spring MVC với timeout của HTTP client gọi service bên ngoài. Timeout phía server inbound không thay thế timeout của `RestClient`/`WebClient` outbound.

## 14. @Async và CompletableFuture: timeout ngoài không dừng JDBC/HTTP bên trong

Đoạn mã hay gây hiểu nhầm:

~~~java
CompletableFuture<String> future =
    CompletableFuture.supplyAsync(
        () -> blockingJdbcCall(),
        ioExecutor);

future.orTimeout(2, TimeUnit.SECONDS);
~~~

Đây là **đoạn minh họa**, cần `blockingJdbcCall`/`ioExecutor` và imports tương ứng. `orTimeout` có thể làm future hoàn tất **exceptionally** sau hai giây. Nó **không bảo đảm** `blockingJdbcCall()` hoặc JDBC connection bị interrupt/đóng/rollback khi đã chạy. Worker có thể vẫn giữ JDBC connection và tiếp tục thực thi SQL sau khi caller tưởng đã “cancel”.

Một cách thiết kế tốt hơn là đặt timeout **ngay trong JDBC** ở lời gọi `blockingJdbcCall`: `JdbcTemplate.setQueryTimeout(...)`, JDBC `Statement.setQueryTimeout(...)`, driver/server timeout. Tương tự, nếu callback dùng HTTP client thì request timeout phải được cấu hình trên HTTP client/request, không chỉ trên returned future.

Ví dụ `CompletableFuture<HttpResponse<String>>` từ `HttpClient.sendAsync(...)` được hưởng timeout của `HttpRequest` vì native HTTP client thực thi policy đó. Nếu đặt thêm `future.orTimeout(...)`, đây là một **đồng hồ quan sát bổ sung**, không nên xem là thay thế request timeout đã cấu hình.

**Phân biệt ba hành vi:**

1. **Timed-out caller:** người chờ kết quả ngừng chờ hoặc future được hoàn tất timeout.
2. **Cancellation attempt:** hệ thống cố ngắt/hủy tác vụ bên dưới thông qua API driver/client nếu hỗ trợ.
3. **Remote work actually stopped:** DB/server đã thực sự ngừng xử lý và trả tài nguyên.

Ba điều này không đồng nghĩa. Với `CompletableFuture.cancel(true)` của JDK, cờ `mayInterruptIfRunning` không có tác dụng buộc worker đang chạy phải bị interrupt. Với JDK HTTP client, cancellation của future bất đồng bộ cũng không nên được hiểu là xác nhận remote đã dừng xử lý.

## 15. Deadline tổng, retry budget và propagation

Một tác vụ có các bước DB → HTTP cần có **deadline end-to-end**. Ví dụ inbound yêu cầu được cấp 3 giây, đến lúc bắt đầu HTTP chỉ còn 900ms. Nếu vẫn cấu hình HTTP request timeout 2 giây mà không kiểm tra thời gian còn lại, kết quả có thể đến sau khi response inbound đã hết hiệu lực.

Một cách tính ở **cùng tiến trình Java** bằng clock monotonic:

~~~java
long deadlineNanos =
    System.nanoTime() + TimeUnit.SECONDS.toNanos(3);

// Sau khi hoàn tất bước DB...
long remainingNanos = deadlineNanos - System.nanoTime();
if (remainingNanos <= 0) {
    throw new TimeoutException("Request deadline exceeded");
}
Duration requestBudget = Duration.ofNanos(remainingNanos);
~~~

Đây là **đoạn ghép vào phương thức**, cần import `TimeUnit`, `Duration`, `TimeoutException`; không phải class độc lập. `System.nanoTime()` phù hợp đo **elapsed time trong một JVM**, không dùng như timestamp có ý nghĩa qua máy khác. Phải xử lý overflow/budget quá nhỏ phù hợp hệ thống khi thiết kế API chung.

Truyền `requestBudget` vào builder HTTP request thực tế, có thể trừ thêm safety margin để đáp ứng serializer/logging. Với SQL, nếu API chỉ nhận timeout theo giây nguyên, không tự làm tròn thành 0 rồi vô tình vô hiệu hóa timeout; thay vào đó chọn policy làm tròn hợp lệ, driver/server setting có độ phân giải phù hợp hoặc từ chối bước không đủ thời gian.

**Retry:** mỗi retry phải nằm trong deadline tổng và còn đủ budget cho việc chờ + thực thi. Không retry ngay khi DB/HTTP đã timeout, vì có thể tăng quá tải và nhân side effect. Chỉ retry lỗi thật sự tạm thời, có backoff + jitter, max attempts và idempotency, tôn trọng `Retry-After` nếu remote trả tín hiệu phù hợp.

## 16. Khi timeout xảy ra: tài nguyên, transaction và kết quả không chắc chắn

Với DB:

- Timeout của statement có thể kích hoạt nỗ lực cancel, nhưng cần xác minh driver/DB thật có ngừng câu query hay chưa.
- Transaction có thể cần rollback; nếu Spring thấy một `DataAccessException` unchecked đi qua transaction interceptor, mặc định thường đánh dấu rollback theo rule của Spring. Nhưng nếu bạn **bắt và nuốt lỗi** trước khi interceptor biết, rollback không tự nhiên xảy ra.
- Một câu `UPDATE`/stored procedure có thể thực hiện một phần công việc trước khi timeout; trạng thái commit/rollback quyết định hệ quả cuối cùng, không thể kết luận chỉ bằng thông báo timeout.
- Đóng `ResultSet`/`Statement`/`Connection` bằng `try-with-resources`; khi dùng Spring JDBC, để template/transaction manager quản lý tài nguyên thay vì đóng connection bound vào transaction sai cách.
- Một connection bị lỗi network nghiêm trọng có thể không còn an toàn để reuse; để driver/pool kiểm tra và loại bỏ theo cơ chế của chúng.

Với HTTP:

- Client timeout không chứng minh remote **chưa nhận POST/PUT/PATCH**. Yêu cầu có thể được xử lý thành công trước khi response mất.
- Không tự retry mọi mutation bằng request mới. Dùng **idempotency key ổn định** qua các attempt hoặc API tra cứu trạng thái.
- Nếu dùng streaming lớn, giải phóng response body/subscriber/connection đúng abstraction; đừng để task timeout nhưng body stream vẫn giữ tài nguyên.
- Phân biệt connect error, request timeout, server trả `408`/`429`/`503` và lỗi deserialize; chúng có retry semantics khác nhau.

Khi task chạy trong `@Async` worker, timeout I/O cần được **chuyển thành trạng thái kết quả**: với method trả `CompletableFuture`, caller có thể theo dõi exceptional completion. Với `@Async void`, exception chưa bắt sẽ đi qua `AsyncUncaughtExceptionHandler` nếu lời gọi được Spring proxy xử lý, không quay về caller như một exception đồng bộ.

## 17. Thiết kế thứ tự timeout để tránh tự làm nghẽn hệ thống

Một policy tốt cần xét quan hệ với tài nguyên thực:

1. **Timeout lấy DB connection nhỏ hơn deadline tổng.** Nếu pool cạn, fail sớm thay vì giữ worker vô nghĩa.
2. **Query timeout phù hợp đặc tính SQL và SLO.** Truy vấn batch dài có thể cần pool/API riêng, không nên tăng timeout chung của tất cả request.
3. **Driver socket timeout bảo vệ đường truyền DB.** Tùy driver, cần đủ để cơ chế cancel/query timeout được trao đổi; đặt quá ngắn có thể làm hỏng kết nối trước khi DB xác nhận hủy statement.
4. **HTTP connect timeout ngắn nhưng hợp lý** cho mạng triển khai; request/response timeout giới hạn chính công việc remote. Có thêm pool acquisition timeout nếu HTTP client dùng connection pool.
5. **Tổng deadline dành buffer cho downstream và cleanup.** Đừng dành hết thời gian cho SQL rồi ngay sau đó mới bắt đầu HTTP khi caller đã hết hạn.
6. **Retry không được reset deadline tổng** thành một ngân sách mới vô hạn.
7. **Queue time cũng là latency.** Một `@Async` task nằm trong executor queue 5 giây rồi mới bắt đầu thì timeout 2 giây của SQL không bảo đảm job hoàn tất trong 2 giây kể từ lúc nộp.
8. **Đo concurrency và connection pool.** Một worker treo trong JDBC/HTTP đồng nghĩa một worker ít hơn cho các task khác; timeout I/O là một phần của cơ chế chống thread starvation.

Cần có ngoại lệ theo loại workload: một báo cáo chạy nền được phép 2 phút có thể cần worker pool, DB statement timeout và deadline khác với endpoint tương tác yêu cầu dưới 1 giây. Không có một cấu hình chung cho mọi nhiệm vụ.

## 18. Quan sát và kiểm thử timeout theo từng tầng

**Metric/tracing nên tách được:**

| Thành phần | Điều nên đo |
| --- | --- |
| ThreadPoolTaskExecutor | active threads, queue length, task waiting time, rejected submissions |
| Connection pool | active/idle/pending connections, acquisition latency, acquisition timeouts |
| SQL/JDBC | query duration, statement timeout, lock wait, SQLState/vendor error, transaction rollback |
| HTTP client | connection establishment, response latency, HTTP status, connect timeout, request timeout, pool wait |
| Nghiệp vụ | end-to-end latency, remaining deadline, retry attempts, late completions, duplicate prevention |

Các label metric có cardinality thấp; không đưa raw SQL chứa dữ liệu cá nhân, URL có token, request body hoặc user ID làm tag metric. Dùng tracing/log có cấu trúc và chính sách bảo mật khi cần drill-down.

**Bộ test gợi ý:**

1. **DB pool exhausted:** giữ hết connection trong test, yêu cầu một connection mới và xác nhận pool acquisition timeout hoạt động; không nhầm với query timeout.
2. **Long SQL:** chạy một query được DB test cố ý làm chậm (ví dụ `pg_sleep` trên PostgreSQL test environment) rồi xác nhận driver/server timeout và cách kết nối được trả về pool.
3. **Slow HTTP:** dùng một server local/mock server trả response chậm hơn request timeout; kiểm tra loại lỗi và tài nguyên cleanup như demo.
4. **Connect failure:** thử môi trường có network fault được kiểm soát; không giả định mọi địa chỉ sai đều gây connect timeout (có thể bị từ chối ngay).
5. **Late result:** làm cho caller/future hết hạn trước nhưng downstream xử lý xong muộn; xác nhận không ghi đè trạng thái cuối hoặc tạo tác dụng phụ trùng.
6. **Retry storm:** nhiều caller cùng gặp timeout; xác minh retry có giới hạn, backoff/jitter và không làm tăng tải vô hạn.
7. **Cancellation:** kiểm tra `Future.cancel` và thực tế JDBC/HTTP client/DB có giải phóng tài nguyên hay không; không chỉ assert trạng thái `isCancelled()`.
8. **Transaction boundary:** gây timeout bên trong `@Transactional`, kiểm tra rollback và trạng thái bảng sau lỗi theo DB thật.
9. **Queue wait:** giữ worker bằng latch để đo queue delay riêng với thời gian chạy SQL/network.

Dùng **đồng hồ có thể kiểm soát** hoặc latch trong unit test khi có thể, không viết test bằng `Thread.sleep` rồi kỳ vọng timing tuyệt đối. Đối với driver/network, phải có integration test thực tế vì các loại timeout chịu ảnh hưởng của OS, JDK và cấu hình hạ tầng.

## 19. Những hiểu nhầm thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “`CompletableFuture.orTimeout` dừng JDBC query.” | Nó làm future hết hạn; I/O bên dưới có thể còn chạy. |
| “`HikariCP.connectionTimeout` là SQL query timeout.” | Nó chủ yếu giới hạn thời gian mượn connection từ pool. |
| “`@Transactional(timeout=...)` tự cancel mọi HTTP request.” | Transaction timeout chỉ có hiệu lực lên các resource được transaction manager hỗ trợ và tích hợp. |
| “Có `connectTimeout` là remote API không bao giờ treo.” | Connect chỉ giới hạn lập kết nối mới; remote có thể chậm trả response. |
| “Mọi `readTimeout` đều giới hạn tổng thời gian request.” | Nhiều client chỉ giới hạn thời gian chờ giữa các lần đọc hoặc response theo policy riêng. |
| “`Statement.setQueryTimeout(2)` bao gồm thời gian lấy connection.” | Query timeout chỉ bắt đầu ở statement; acquisition nằm trước. |
| “`setQueryTimeout(500)` nghĩa là 500ms.” | JDBC nhận **giây**; đó là 500 giây nếu driver áp trực tiếp. |
| “Mọi driver dùng socketTimeout theo milliseconds.” | Tên và đơn vị là đặc thù driver; pgJDBC dùng giây. |
| “Timeout nghĩa là remote chưa tạo đơn/thanh toán.” | Remote có thể đã thực hiện, chỉ có response không tới kịp. |
| “Client timeout thì transaction chắc chắn rollback.” | Cần xác minh commit/rollback thực tế; hai hệ thống là những ranh giới riêng. |
| “Chỉ cần đặt tổng deadline 3 giây là mọi thread/connection được giải phóng.” | Deadline phải phối hợp timeout/hủy thực tế ở mỗi I/O boundary. |
| “Đặt timeout thật nhỏ luôn tốt hơn.” | Dễ tạo lỗi giả, retry storm, kết nối bị phá hủy và giảm throughput; cần theo SLO/tải. |

## 20. Checklist trước khi triển khai

- Xác định rõ từng chỗ đợi: executor queue, DB pool acquire, DB connect, SQL execution, DB socket, HTTP connect, HTTP response và caller wait.
- Chọn timeout ở **đúng lớp thực hiện I/O**; không chỉ đặt timeout trên future phía ngoài.
- Ghi rõ **đơn vị** của từng property: Hikari milliseconds, JDBC statement seconds, JDK HTTP `Duration`, pgJDBC driver properties theo tài liệu.
- Có deadline tổng và không bắt đầu bước mới khi không còn thời gian.
- Kiểm tra version/driver/request factory đang thực sự chạy; không áp giả định từ thư viện khác.
- Dùng query timeout, driver/network timeout và server timeout khi cần, hiểu điều kiện override bởi transaction timeout.
- Chọn đúng policy rollback, error propagation và cleanup khi timeout.
- Không giữ DB transaction/connection trong lúc remote call chậm nếu có thể tách boundary.
- Không auto-retry vô hạn; với tác vụ thay đổi trạng thái, có idempotency key và reconciliation.
- Quan sát connection pool pending, worker active, query/HTTP latency, timeout rate, retries và late completions.
- Kiểm thử cả slow query, slow HTTP, connection pool saturation, lỗi mạng, transaction rollback và side effect đến muộn.

**Kết luận:** Một `@Async` task có thể đã hết hạn trong mắt caller nhưng vẫn đang chờ JDBC/HTTP ở worker. Để tránh chiếm thread và connection vô thời hạn, phải **đặt timeout riêng tại DB driver/statement/server và HTTP client/request**, đồng thời quản lý deadline nghiệp vụ, retry, cancellation, cleanup và hậu quả của kết quả không chắc chắn.

## Tài liệu tham khảo chính thức

- [Java SE 17 – Statement.setQueryTimeout](https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/Statement.html): giới hạn thực thi JDBC statement và `SQLTimeoutException`.
- [Spring Framework – JdbcTemplate Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/jdbc/core/JdbcTemplate.html): `setQueryTimeout`, mặc định -1 và tương tác với timeout transaction.
- [Spring Framework – Using @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html): timeout và propagation của transaction.
- [Spring Framework – DataSourceTransactionManager](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/jdbc/datasource/DataSourceTransactionManager.html): cách truyền transaction timeout xuống JDBC statements khi tích hợp đúng.
- [HikariCP – README](https://github.com/brettwooldridge/HikariCP): `connectionTimeout`, `validationTimeout`, `maximumPoolSize`.
- [PostgreSQL JDBC – Connection parameters](https://jdbc.postgresql.org/documentation/use/): `connectTimeout`, `socketTimeout`, `queryTimeout` và đơn vị.
- [Java SE 17 – HttpClient](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html): connect timeout và HTTP client configuration.
- [Java SE 17 – HttpRequest.Builder](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpRequest.Builder.html): timeout cho một HTTP request.
- [Spring Framework – JdkClientHttpRequestFactory](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/client/JdkClientHttpRequestFactory.html): `setReadTimeout(Duration)`.
- [Java SE 17 – CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): `orTimeout`, `completeOnTimeout`, `cancel` và giới hạn hủy tác vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="timeout-cancellation-008">Ngăn công việc hết giá trị tiếp tục tạo tác dụng phụ</a>

<details>
<summary>Xem chi tiết</summary>

# Ngăn công việc hết giá trị tiếp tục tạo tác dụng phụ

## 1. Vấn đề: hết thời hạn không đồng nghĩa công việc đã dừng

Một ứng dụng Spring nhận yêu cầu tạo báo cáo, gửi email, cập nhật trạng thái đơn hàng hoặc gọi API thanh toán. Công việc chạy bất đồng bộ qua `@Async`, `Executor` hoặc `CompletableFuture`. Người dùng hủy, request hết timeout, hoặc yêu cầu mới thay thế yêu cầu cũ. **Worker cũ vẫn có thể đang chạy và thực hiện một tác dụng phụ**: ghi database, phát message, gửi email, gọi API ngoài, ghi file, thay đổi cache hoặc kích hoạt một thao tác không thể thu hồi.

Ví dụ:

1. 10:00:00, người dùng yêu cầu xuất báo cáo phiên bản 7. Worker A bắt đầu tính.
2. 10:00:02, người dùng đổi bộ lọc; phiên bản 8 thay thế phiên bản 7.
3. 10:00:03, controller đánh dấu request A đã hết thời hạn, hoặc future A hoàn tất với timeout.
4. 10:00:05, worker A hoàn tất tính toán và **ghi kết quả phiên bản 7 đè lên phiên bản 8**.

Việc caller không còn chờ A **không ngăn** A sửa trạng thái. Tương tự, nếu người dùng rời trang đặt hàng, điều đó không tự chứng minh đơn hàng hoặc thanh toán phải được hủy. Ứng dụng cần một **hợp đồng nghiệp vụ**: khi nào công việc được xem là hết giá trị, và tác dụng phụ nào phải được chặn.

**Mục tiêu sau bài học:**

- Phân biệt timeout khi chờ, trạng thái `CompletableFuture`, tín hiệu interrupt, lệnh hủy nghiệp vụ và việc đã thực sự chặn tác dụng phụ.
- Thiết kế `deadline`, cancellation token, revision/generation và quy tắc `latest-wins` khi phù hợp.
- Hiểu race condition giữa **kiểm tra còn hợp lệ** và **thực hiện side effect**.
- Bảo vệ ranh giới ghi bằng kiểm tra nguyên tử, optimistic locking, fencing token và idempotency.
- Biết giới hạn của `@Async`, `Future.cancel(true)`, `CompletableFuture.cancel(true)`, `orTimeout` và `get(timeout)`.
- Chọn cách xử lý side effect bên ngoài database: outbox, xác nhận token tại downstream, compensation hoặc báo trạng thái không chắc chắn.
- Viết và chạy các ví dụ Java 17+ có output xác định, kể cả tình huống sai do race.
- Test shutdown, retry, stale tasks và quan sát số lần tác dụng phụ bị chặn trong Spring.

Bài thuộc nhóm **Hết hạn và hủy** của chủ đề Spring Framework – concurrency. Trọng tâm không phải “cách làm future trả lỗi timeout”, mà là **cách bảo đảm công việc không còn quyền tạo tác dụng phụ sau khi mất hiệu lực theo chính sách đã định**.

## 2. Trước hết: phân biệt năm khái niệm

| Khái niệm | Ý nghĩa | Có tự ngăn tác dụng phụ về sau không? |
| --- | --- | --- |
| Caller hết thời gian đợi | `Future.get(timeout, unit)` không nhận kết quả trước deadline | **Không**; worker có thể tiếp tục |
| Future hoàn thành bất thường | `CompletableFuture.orTimeout` hoặc `completeExceptionally` thay đổi trạng thái future | **Không**; tác vụ nền có thể còn chạy |
| Yêu cầu hủy tính toán | `Future.cancel(true)` có thể cố interrupt; `CompletableFuture.cancel` có semantics riêng | **Không bảo đảm** dừng I/O, ngăn code tiếp tục hoặc rollback |
| Mất hiệu lực nghiệp vụ | Phiên bản cũ, yêu cầu mới thay thế, trạng thái job bị hủy, deadline nghiệp vụ đã qua | Cần **kiểm tra và thực thi quy tắc** tại đúng ranh giới |
| Tác dụng phụ đã commit/được gửi | DB commit, API bên ngoài chấp nhận lệnh, email được phát, message đã publish | Thường **không thể “unsend”**; cần chính sách, idempotency, compensation |

Một thao tác có thể **hết giá trị đối với giao diện** nhưng vẫn **phải hoàn tất về nghiệp vụ**. Ví dụ: HTTP timeout sau khi ngân hàng đã nhận yêu cầu chuyển tiền không cho phép tự phát lệnh chuyển tiền ngược hoặc gửi lại không có idempotency key. Định nghĩa “hết giá trị” phải theo domain, không chỉ theo deadline trình duyệt.

### 2.1. Những tác dụng phụ cần nhận diện

- **Trong JVM:** thay đổi bean singleton, cache, collection chia sẻ, ghi file tạm.
- **Trong database:** INSERT/UPDATE/DELETE, ghi trạng thái job, commit transaction.
- **Qua mạng:** gửi email, webhook, thông báo đẩy, gọi dịch vụ thanh toán, tạo đơn bên thứ ba.
- **Qua message broker:** publish event, gửi command hoặc xác nhận message.
- **Ngoài tiến trình:** chạy process, ghi lên object storage, tạo tài nguyên cloud.

Việc đặt một `if (cancelled) return;` trước mỗi thao tác có thể loại bỏ nhiều công việc vô ích. Nhưng nếu **hủy xảy ra sau lúc kiểm tra và trước lúc ghi**, tác dụng phụ vẫn có thể diễn ra. Đó là lỗ hổng check-then-act quan trọng nhất của bài này.

## 3. Mô hình trạng thái và định nghĩa “còn hợp lệ”

Không có một mô hình phù hợp mọi nghiệp vụ, nhưng nên ghi rõ một state machine. Ví dụ job tạo bản xem trước có thể là:

```text
PENDING ---> RUNNING ---> SUCCEEDED
   |            |   \----> FAILED
   |            |
   |            +-----> CANCEL_REQUESTED ---> CANCELLED
   |            |
   |            +-----> EXPIRED
   |
   +-----> CANCELLED / EXPIRED
```

Các mũi tên mang tính mô hình: trong hệ thống thật phải quyết định **cạnh tranh trạng thái** khi một thread đang commit thành công đúng lúc thread khác yêu cầu hủy. Không tự cho phép mọi trạng thái chuyển sang mọi trạng thái khác.

Một công việc chỉ được tạo tác dụng phụ khi tất cả điều kiện áp dụng đều đúng, ví dụ:

```text
job.state == RUNNING
AND job.generation == currentGeneration(resourceId)
AND job.deadline > authoritativeNow()
AND operationId chưa được áp dụng
AND business invariant vẫn đúng
```

Điều kiện `generation` thích hợp cho yêu cầu “chỉ kết quả mới nhất được phép ghi”. Điều kiện deadline thích hợp khi sau thời điểm đó kết quả không còn có ý nghĩa. Idempotency key áp dụng khi cùng một lệnh có thể được retry. Chúng giải quyết **các bài toán khác nhau**, không nên dùng thay nhau.

### 3.1. Ai quyết định hợp lệ?

- **Caller/UI:** có thể thông báo không còn cần kết quả, nhưng không nên tự là nguồn sự thật của giao dịch đã commit.
- **Service nghiệp vụ:** quyết định luật “job được commit khi nào”, quyền hủy và phản hồi khi conflict.
- **Database/authoritative store:** có thể giữ revision, state, deadline, idempotency key và đảm bảo thay đổi nguyên tử.
- **Downstream:** nếu tác dụng phụ xảy ra ngoài hệ thống, downstream cần một cách từ chối token cũ hoặc chấp nhận idempotency key khi có thể.
- **Worker:** kiểm tra sớm để tiết kiệm tài nguyên và kiểm tra lần cuối tại boundary commit; không tự dựa vào trạng thái cục bộ nếu hệ thống có nhiều replica.

Nếu nhiều request cập nhật cùng tài nguyên, một `AtomicBoolean` trong một worker **không đủ** để đồng bộ giữa các instance hay JVM restart. Cần lưu quyền thực thi và generation ở hệ thống dùng chung khi chính sách yêu cầu đồng bộ toàn cụm.

## 4. Timeout và hủy trong Java: hiểu đúng hợp đồng API

### 4.1. Future.get(timeout, unit)

`future.get(2, TimeUnit.SECONDS)` giới hạn **thời gian thread caller đợi**. Nếu quá hạn, nó ném `TimeoutException` cho caller; điều đó không đồng nghĩa task ngừng. Task vẫn có thể hoàn thành trong nền và gây tác dụng phụ.

Một ví dụ khái niệm:

```java
Future<?> task = executor.submit(() -> sendEmail());
try {
    task.get(2, TimeUnit.SECONDS);
} catch (TimeoutException ex) {
    // Chỉ biết caller đã đợi quá lâu.
    // sendEmail() có thể vẫn chạy hoặc đã gửi xong.
}
```

Đây là **fragment** cần định nghĩa executor/sendEmail, import Java và chính sách lỗi; không phải chương trình chạy độc lập. Để hủy task, bạn có thể **yêu cầu** `task.cancel(true)`, nhưng vẫn phải thiết kế kiểm tra nghiệp vụ, timeout của client và idempotency.

### 4.2. Future.cancel(true)

Hợp đồng `Future.cancel(boolean)` cho phép thử hủy task. Với một số implementation kiểu `FutureTask`, nếu task đang chạy và thread thực thi được biết, `cancel(true)` có thể gửi tín hiệu **interrupt**. Nó không phải cơ chế cưỡng chế giết thread: code có thể bỏ qua interrupt, I/O có thể không phản hồi, hoặc side effect đã xảy ra.

- Nếu task chưa chạy, việc cancel thành công có thể ngăn nó bắt đầu.
- Nếu task đang chạy, interrupt là tín hiệu **hợp tác**; không tự rollback transaction hay hủy một HTTP request đã được downstream xử lý.
- Sau khi một `Future` được đánh dấu cancelled, `get()` không còn là công cụ để chứng minh tác vụ vật lý đã dừng; với implementation chạy trong nền, cần theo dõi trạng thái dừng thực tế.
- Giá trị boolean trả về phụ thuộc implementation và race với completion; không dùng nó làm bằng chứng giao dịch đã bị đảo ngược.

### 4.3. CompletableFuture.cancel(true) có điểm đặc biệt

Theo Javadoc Java 17, `CompletableFuture.cancel(mayInterruptIfRunning)` hoàn thành future bằng `CancellationException` nếu chưa hoàn tất; **tham số `mayInterruptIfRunning` không có tác dụng với implementation này**, vì interrupt không được dùng để điều khiển computation.

Vì vậy:

```java
CompletableFuture<Result> stage =
        CompletableFuture.supplyAsync(() -> blockingCall(), executor);

stage.cancel(true);
```

là **fragment**, không bảo đảm `blockingCall()` bị interrupt, không bảo đảm HTTP/DB call được hủy, và không ngăn logic phía sau trong supplier nếu nó đã chạy. Muốn worker dừng hợp tác, cần tín hiệu riêng và kiểm tra tại các checkpoint; với effect quan trọng, cần **freshness guard ở commit**.

### 4.4. orTimeout và completeOnTimeout

`orTimeout(timeout, unit)` hoàn thành chính `CompletableFuture` bằng `TimeoutException` nếu nó chưa hoàn tất vào deadline. `completeOnTimeout(fallback, timeout, unit)` hoàn thành tương tự nhưng bằng một **giá trị fallback** nếu deadline thắng race. Cả hai không đồng nghĩa với việc worker nguồn bị dừng.

Cẩn thận với:

```java
CompletableFuture<Result> task = startAsync();
CompletableFuture<Result> timed = task.orTimeout(2, TimeUnit.SECONDS);
```

`orTimeout` trả về **chính** `CompletableFuture` đã gọi trong API Java thông thường; đừng suy ra nó tạo một wrapper độc lập để chỉ thay kết quả của caller. Future có thể bị đánh dấu timeout trong khi supplier đã được submit vẫn chạy.

`completeOnTimeout(defaultValue, ...)` còn có rủi ro khác: downstream callback dựa trên completion bình thường có thể chạy với **giá trị fallback**. Nếu callback dùng fallback để ghi business state, phải chắc chắn đó là hành vi được phép.

### 4.5. Bảng chọn hành vi

| Nhu cầu | Java API/cơ chế | Chú ý |
| --- | --- | --- |
| Caller không được chờ quá SLA | `get(timeout)` | Không dừng worker |
| Stage hoàn tất lỗi khi quá hạn | `orTimeout` | Không hủy tác dụng phụ |
| Stage trả fallback theo deadline | `completeOnTimeout` | Fallback có thể kích hoạt callback bình thường |
| Xin dừng tác vụ đã submit | `Future.cancel(true)` | Interrupt tùy implementation; cần hợp tác |
| Đánh dấu một `CompletableFuture` cancelled | `CompletableFuture.cancel(true)` | Không dùng interrupt điều khiển source |
| Dừng việc vô ích giữa các pha | Atomic cancellation flag / token + interrupt check | Chặn sớm, nhưng có check-then-act race |
| Ngăn ghi giá trị cũ trong DB | Conditional write/optimistic lock theo generation | Kiểm tra + thay đổi phải nguyên tử |
| Ngăn command cũ ở hệ ngoài | Downstream kiểm tra fencing token hoặc giao thức idempotent | Cần downstream hỗ trợ |
| Không được mất job khi restart | Durable queue/job store/outbox | Future trong RAM không đủ |

## 5. Ví dụ Java 17+: timeout đã xảy ra nhưng tác dụng phụ vẫn tiếp tục

Ví dụ cố ý biểu diễn **hành vi cần tránh**. Một worker chờ latch; main cho future timeout; sau đó main mở latch để worker thực hiện tác dụng phụ. Dùng latch giúp test có thứ tự rõ ràng, không dựa vào `Thread.sleep`.

Tệp `TimeoutStillRunsDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TimeoutStillRunsDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger effects = new AtomicInteger();

        try {
            CompletableFuture<String> result =
                    CompletableFuture.supplyAsync(() -> {
                        started.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(ex);
                        }
                        effects.incrementAndGet();
                        return "stored";
                    }, executor);

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker did not start");
            }

            result.orTimeout(0, TimeUnit.MILLISECONDS);
            try {
                result.join();
            } catch (CompletionException ex) {
                System.out.println("future-error="
                        + ex.getCause().getClass().getSimpleName());
            }

            release.countDown();
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker did not stop");
            }
            System.out.println("effects-after-timeout=" + effects.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac TimeoutStillRunsDemo.java
java TimeoutStillRunsDemo
```

Stdout khi hoàn tất bình thường:

```text
future-error=TimeoutException
effects-after-timeout=1
```

**Giải thích:** worker đã bắt đầu và chờ `release`, nên `orTimeout(0, ...)` có cơ hội hoàn thành future một cách bất thường trước khi worker tự chạy tiếp. Main quan sát `TimeoutException`; sau đó mới mở latch. Worker vẫn tăng `effects` vì không có guard nghiệp vụ nào trước tác dụng phụ. `executor.awaitTermination` giúp xác nhận side effect đã chạy xong trước lúc đọc counter.

Đây là chứng minh trực tiếp rằng **tình trạng future** không phải **quyền tạo tác dụng phụ**. Đừng chữa bằng cách chỉ thêm `exceptionally(...)` để future “không còn lỗi”; điều đó càng có thể che mất vấn đề.

## 6. Phòng tuyến thứ nhất: dừng hợp tác trước công việc không cần thiết

Một worker có thể thực hiện nhiều pha:

```text
received
  -> validate input
  -> load data
  -> compute result
  -> prepare change
  -> commit effect
```

Khi nhận thấy công việc hết giá trị, nên dừng càng sớm càng tốt:

1. Trước khi bắt đầu pha tốn CPU/I/O.
2. Sau khi một lời gọi chặn trở về.
3. Trước khi submit tác vụ phụ tiếp theo.
4. Sau khi nhận tín hiệu hủy từ caller/broker.
5. **Ngay tại ranh giới commit tác dụng phụ**, với cơ chế kiểm tra nguyên tử khi cần độ đúng cao.

Trong một JVM, token đơn giản có thể là `AtomicBoolean cancelled` hoặc `AtomicLong generation`. Worker có thể kiểm tra `Thread.currentThread().isInterrupted()` khi đang dùng interruption-based cancellation; khi bắt `InterruptedException`, thường cần thoát hợp tác và khôi phục trạng thái interrupt nếu code tiếp tục truyền tín hiệu.

### 6.1. Ví dụ Java 17+: token chặn công việc trước side effect

Tệp `CooperativeGuardDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CooperativeGuardDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean obsolete = new AtomicBoolean(false);
        AtomicInteger effects = new AtomicInteger();

        try {
            Future<Boolean> job = executor.submit(() -> {
                started.countDown();
                release.await();
                if (obsolete.get()) {
                    return false; // Không ghi khi yêu cầu đã bị thay thế.
                }
                effects.incrementAndGet();
                return true;
            });

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker not started");
            }

            obsolete.set(true);
            release.countDown();

            System.out.println("applied=" +
                    job.get(5, TimeUnit.SECONDS));
            System.out.println("effects=" + effects.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
```

```bash
javac CooperativeGuardDemo.java
java CooperativeGuardDemo
```

Output:

```text
applied=false
effects=0
```

Khi main đã đặt `obsolete=true` trước lúc mở latch, worker nhận tín hiệu và bỏ qua tác dụng phụ. `AtomicBoolean` bảo đảm visibility/atomicity của biến trạng thái trong một JVM.

**Giới hạn:** nếu main đặt `obsolete=true` **sau lúc worker kiểm tra** nhưng **trước lúc worker `incrementAndGet()`**, side effect vẫn có thể xảy ra. Một boolean guard làm giảm công việc thừa, nhưng chưa phải bảo đảm tuyệt đối cho điều kiện “không ghi sau khi đã hết giá trị”.

### 6.2. Ví dụ Java 17+: Future.cancel(true) và interruption hợp tác

Tệp `InterruptCooperationDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class InterruptCooperationDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean sawInterrupt = new AtomicBoolean(false);
        AtomicInteger effects = new AtomicInteger();

        try {
            Future<?> task = executor.submit(() -> {
                started.countDown();
                try {
                    neverReleased.await();
                    effects.incrementAndGet();
                } catch (InterruptedException ex) {
                    sawInterrupt.set(true);
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker not started");
            }

            boolean accepted = task.cancel(true);
            if (!finished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("interrupt not observed");
            }

            System.out.println("cancel-accepted=" + accepted);
            System.out.println("interrupt-seen=" + sawInterrupt.get());
            System.out.println("effects=" + effects.get());
        } finally {
            executor.shutdownNow();
        }
    }
}
```

```bash
javac InterruptCooperationDemo.java
java InterruptCooperationDemo
```

Output khi không có tác động interrupt ngoài chương trình:

```text
cancel-accepted=true
interrupt-seen=true
effects=0
```

Ở đây `Future` từ `ExecutorService.submit` có thể interrupt worker đang chờ `CountDownLatch.await()`; worker **chủ động** xử lý `InterruptedException` và không thực hiện effect. Nó minh họa interruption hợp tác, **không** chứng minh mọi JDBC/HTTP call đều có thể ngắt hoặc mọi `CompletableFuture.cancel(true)` đều gửi interrupt.

## 7. Lỗ hổng check-then-act: kiểm tra đúng nhưng ghi vẫn sai

Trường hợp nguy hiểm hơn:

```java
if (generation.get() == expectedGeneration) {
    // Task có thể bị thay thế ngay tại đây.
    writeResult();
}
```

Hai thao tác **đọc trạng thái** và **ghi tác dụng phụ** là hai sự kiện khác nhau. Thread khác có thể hủy/thay thế ở giữa. `AtomicLong` giúp đọc generation một cách atomic và có visibility, nhưng **không tự biến** cả hai thao tác thành một transaction.

### 7.1. Ví dụ Java 17+: tái hiện race bằng latch

Tệp `CheckThenActRaceDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CheckThenActRaceDemo {
    public static void main(String[] args) throws Exception {
        AtomicLong generation = new AtomicLong(1);
        AtomicInteger effects = new AtomicInteger();
        CountDownLatch checked = new CountDownLatch(1);
        CountDownLatch continueTask = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            long capturedGeneration = generation.get();

            Future<Boolean> stale = executor.submit(() -> {
                if (generation.get() != capturedGeneration) {
                    return false;
                }
                checked.countDown();
                continueTask.await();
                effects.incrementAndGet();
                return true;
            });

            if (!checked.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("not checked");
            }

            generation.incrementAndGet(); // Yêu cầu mới đã thay thế.
            continueTask.countDown();

            System.out.println("stale-applied=" +
                    stale.get(5, TimeUnit.SECONDS));
            System.out.println("effects=" + effects.get());
        } finally {
            continueTask.countDown();
            executor.shutdownNow();
        }
    }
}
```

```bash
javac CheckThenActRaceDemo.java
java CheckThenActRaceDemo
```

Output:

```text
stale-applied=true
effects=1
```

Lịch đã được điều phối: worker kiểm tra generation 1; main nâng lên 2; worker mới chạy tiếp và ghi. Worker “đã kiểm tra” nhưng **kết luận hợp lệ không còn đúng tại thời điểm effect**.

**Bài học:** các checkpoint trước pha tốn CPU/I/O tốt cho hiệu năng; đối với effect ảnh hưởng đúng/sai nghiệp vụ, phải có **quy tắc nguyên tử tại ranh giới tác dụng phụ**, hoặc một hợp đồng downstream có khả năng từ chối token cũ. Không coi nhiều `if` rời rạc là một transaction.

## 8. Fencing token, generation và kiểm tra nguyên tử tại commit

Một giải pháp thông dụng cho “chỉ công việc mới nhất được phép ghi” là **generation** (còn gọi revision/epoch/fencing token). Mỗi khi yêu cầu mới thay thế yêu cầu cũ, authoritative store tăng generation. Worker mang generation đã cấp lúc bắt đầu. Trước khi áp dụng effect, nơi **sở hữu dữ liệu** chỉ cho phép token trùng generation hiện tại.

### 8.1. Vì sao fencing token tốt hơn một cờ cancelled đơn giản?

| Cơ chế | Có tác dụng gì? | Giới hạn |
| --- | --- | --- |
| `AtomicBoolean cancelled` trong worker | Dừng sớm cùng JVM | Không tự đồng bộ nhiều process; check-then-act race |
| `AtomicLong generation` trong process | Phát hiện kết quả cũ khi cùng process quản lý tài nguyên | Restart/replica khác có trạng thái riêng; vẫn phải commit atomic |
| Generation lưu DB | Token dùng chung giữa replica/restart | Cần transaction/conditional update chuẩn |
| Fencing token downstream kiểm tra | Downstream từ chối command từ owner/lease cũ | Chỉ hiệu lực nếu downstream **thực sự kiểm tra** token |
| Idempotency key | Chặn tác động lặp của **cùng lệnh** | Không tự chặn **lệnh cũ nhưng khác ID** |
| Business deadline/state | Chặn tác động sau thời điểm/trạng thái quy định | Phải kiểm tra ở nguồn có thẩm quyền và cùng ranh giới commit |

Một token nên tăng đơn điệu hoặc tuân theo thứ tự phiên bản mà hệ thống có thể xác minh. Không dùng timestamp từ clock mỗi instance như một thứ tự tuyệt đối nếu các clock có thể lệch, và không reset token tùy tiện sau JVM restart khi worker cũ vẫn có thể đến muộn.

### 8.2. Ví dụ Java 17+: atomic gate mô phỏng bằng synchronized

Tệp `AtomicFencingDemo.java`:

```java
import java.util.HashSet;
import java.util.Set;

public class AtomicFencingDemo {
    static final class Store {
        private long generation = 1;
        private int effectCount;
        private final Set<String> completedIds = new HashSet<>();

        synchronized long current() {
            return generation;
        }

        synchronized long supersede() {
            return ++generation;
        }

        synchronized String commit(long token, String operationId) {
            if (token != generation) {
                return "STALE";
            }
            if (!completedIds.add(operationId)) {
                return "DUPLICATE";
            }
            effectCount++;
            return "APPLIED";
        }

        synchronized int effects() {
            return effectCount;
        }
    }

    public static void main(String[] args) {
        Store store = new Store();
        long oldToken = store.current();
        long currentToken = store.supersede();

        System.out.println("old=" +
                store.commit(oldToken, "old-command"));
        System.out.println("current=" +
                store.commit(currentToken, "new-command"));
        System.out.println("retry=" +
                store.commit(currentToken, "new-command"));
        System.out.println("effects=" + store.effects());
    }
}
```

```bash
javac AtomicFencingDemo.java
java AtomicFencingDemo
```

Output:

```text
old=STALE
current=APPLIED
retry=DUPLICATE
effects=1
```

Toàn bộ `current/supersede/commit` được đồng bộ hóa trên một `Store`; trong `commit`, **kiểm tra token + kiểm tra duplicate + áp dụng effect** xảy ra trong cùng một critical section. Một thread khác không thể chèn `supersede` vào khoảng giữa check và effect của `commit` trên instance đó.

**Giới hạn phải nhớ:** code này **chỉ** bảo đảm trong một JVM và với một `Store` duy nhất. Trong ứng dụng Spring có nhiều replica, database hoặc durable state store phải thực thi cơ chế tương đương. Nếu effect là gửi email bên ngoài, không thể giữ Java `synchronized` mà kết luận email sẽ bị từ chối sau hủy ở hệ bên kia.

### 8.3. Chọn điểm tuyến tính hóa (linearization point)

Khi commit và cancel xảy ra đồng thời, cần định nghĩa cái nào thắng:

- Nếu **cancel được commit trước**, mọi effect dùng token cũ phải bị từ chối.
- Nếu **effect được commit trước**, lệnh hủy đến muộn có thể báo “quá muộn để hủy”; không được tuyên bố rollback đã xảy ra.
- Nếu nghiệp vụ bắt buộc “hủy trước một mốc” chứ không phải trước commit, phải lưu/đánh giá deadline tại authoritative boundary.
- Nếu có công việc chạy nhiều phút, đừng khóa toàn bộ pipeline; chỉ bảo vệ **bước chuyển trạng thái/tác dụng phụ** bằng transaction có thời gian ngắn, rồi thiết kế những bước I/O bên ngoài.

Đây là chính sách về **thứ tự quyền lực của state transition**, không phải yêu cầu đảm bảo scheduler chạy theo thứ tự thời gian tường đồng hồ.

## 9. Cài đặt trong database: conditional write và optimistic locking

Với hệ nhiều instance, cơ chế mạnh hơn là một lệnh database làm **kiểm tra và thay đổi cùng lúc**. Ví dụ tư duy SQL (minh họa kiểu PostgreSQL; cần schema, index, transaction và mapping trạng thái cụ thể):

```sql
UPDATE report_jobs
SET state = 'READY',
    result_ref = :result_ref
WHERE job_id = :job_id
  AND generation = :expected_generation
  AND state = 'RUNNING'
  AND expires_at > CURRENT_TIMESTAMP;
```

Đọc số hàng được cập nhật:

- **1 hàng:** tại thời điểm lệnh UPDATE được database thực thi theo transaction semantics phù hợp, điều kiện hiện hợp lệ và effect trong DB được áp dụng.
- **0 hàng:** có thể job đã bị hủy/thay thế, hết hạn, hoàn tất ở nơi khác hoặc không tồn tại. Không retry vô điều kiện cùng dữ liệu cũ; phân loại nguyên nhân theo nghiệp vụ.
- **Lỗi database/transaction:** trạng thái có thể chưa được commit; theo dõi transaction outcome và idempotency trước khi retry.

Với ranh giới phức tạp, dùng `@Version`/optimistic locking của JPA hoặc custom repository conditional update. Không thực hiện `SELECT status`, đợi vài giây, rồi `UPDATE` không có điều kiện: đó là check-then-act race.

### 9.1. Khi cần ghi cả trạng thái và outbox

Nếu tác dụng phụ là gửi event cho dịch vụ khác, có thể ghi **state transition + outbox record** trong **cùng database transaction**:

```text
BEGIN TRANSACTION
    UPDATE job WHERE generation = expected AND state = RUNNING;
    nếu updatedRows != 1 -> ROLLBACK và báo STALE/EXPIRED;
    INSERT outbox(eventId, operationId, generation, payload);
COMMIT
```

Đây là **pseudocode**, không phải SQL chạy độc lập. Dùng unique constraint/idempotency key cho event hoặc operation ID để hạn chế duplicate. Worker phát event từ outbox có retry và trạng thái giao nhận rõ.

**Cực kỳ quan trọng:** transaction bảo đảm **ghi trạng thái + outbox cùng commit**; nó **không** bảo đảm downstream **không nhận event cũ** nếu job bị thay thế **sau khi outbox đã commit**. Nếu chính sách nói “lệnh cũ không được tác động bên ngoài sau khi một yêu cầu mới đã thắng”, consumer/downstream cần thêm generation/fencing kiểm tra tại điểm **chấp nhận effect**. Một lần kiểm tra ở publisher trước khi gửi vẫn có check-send race.

### 9.2. Fencing token tại downstream

Ví dụ dịch vụ nhận command cập nhật cache/tài nguyên:

```text
Command:
  resourceId = report-42
  generation = 18
  operationId = update-report-42-18

Downstream:
  nếu generation < currentGeneration(resourceId): REJECT_STALE
  nếu operationId đã được áp dụng: DEDUPLICATE
  ngược lại: áp dụng và lưu generation/operationId NGUYÊN TỬ
```

Downstream phải duy trì trạng thái authoritative mà nó có thể kiểm tra. Nếu nó chỉ tin vào một trường `generation` được truyền nhưng không lưu và so sánh, token **không hề ngăn** stale request.

**Điểm khác với idempotency:** command phiên bản 17 và command phiên bản 18 có **operationId khác nhau**. Idempotency giúp không chạy hai lần lệnh 17, nhưng chỉ **fencing** mới giúp không áp dụng lệnh 17 **sau** khi phiên bản 18 đã được công nhận là mới hơn.

## 10. Spring @Async: tách chuẩn bị kết quả và ranh giới side effect

Trong Spring, `@Async` thường được áp dụng qua proxy khi có `@EnableAsync` và method được gọi qua Spring-managed bean. Controller có thể nhận `CompletableFuture` rất sớm, trong khi worker vẫn đang làm việc.

Để tránh stale side effect, nên tách:

1. **Worker/compute:** đọc input immutable, tính kết quả hoặc tạo payload; kiểm tra cancel/deadline định kỳ.
2. **Commit service:** kiểm tra generation/state/deadline một cách **nguyên tử** và ghi business state/outbox trong transaction.
3. **Delivery worker/downstream:** xử lý external side effect với idempotency, fencing và retry phù hợp.
4. **Caller/status API:** phân biệt `SUCCEEDED`, `SKIPPED_STALE`, `CANCELLED`, `TIMED_OUT`, `FAILED`, `UNKNOWN` thay vì chỉ dùng boolean `done`.

### 10.1. Mẫu chia service (snippet Spring, không phải file javac độc lập)

```java
// File trong project Spring: ReportWorker.java
@Service
public class ReportWorker {
    private final ReportCommitService commitService;

    public ReportWorker(ReportCommitService commitService) {
        this.commitService = commitService;
    }

    @Async("reportExecutor")
    public CompletableFuture<CommitOutcome> generate(
            ReportCommand command) {
        // Input nên là immutable snapshot.
        // Tác vụ tính toán có thể kiểm tra token để dừng sớm.
        String preparedResult = calculate(command);

        // Service khác sở hữu transaction/conditional update.
        CommitOutcome outcome = commitService.applyIfCurrent(
                command.jobId(),
                command.generation(),
                preparedResult);

        return CompletableFuture.completedFuture(outcome);
    }
}
```

Đoạn trên là **fragment thiết kế**, cần định nghĩa `ReportCommand`, `CommitOutcome`, `calculate`, các imports, bean `reportExecutor` và repository. Không copy y nguyên rồi mong biên dịch.

Một phần của `ReportCommitService` có thể được thiết kế theo hướng:

```java
@Service
public class ReportCommitService {
    private final ReportRepository repository;
    private final OutboxRepository outbox;

    public ReportCommitService(
            ReportRepository repository,
            OutboxRepository outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    @Transactional
    public CommitOutcome applyIfCurrent(
            long jobId, long generation, String result) {

        int updated = repository.writeIfRunningAndCurrent(
                jobId, generation, result);
        if (updated == 0) {
            return CommitOutcome.SKIPPED_STALE;
        }

        outbox.addForCommittedResult(jobId, generation);
        return CommitOutcome.APPLIED;
    }
}
```

Các tên repository/enum ở đây được dùng để minh họa **hợp đồng**, không phải API Spring có sẵn. Repository phải thực hiện update có điều kiện nguyên tử; outbox phải dùng **cùng transaction** với update và có unique constraint thích hợp. `@Transactional` phải được intercept đúng, ví dụ method được gọi từ bean khác qua Spring-managed proxy. Nếu Spring transaction không thực sự áp dụng, “ghi job + outbox cùng transaction” có thể là lời hứa sai.

**Không đặt** `@Transactional` lên một private method rồi gọi bằng `this.applyIfCurrent(...)` từ chính bean và cho rằng transaction proxy đã được áp dụng trong cấu hình proxy mặc định.

### 10.2. Trong controller, timeout không phải lệnh hủy thanh toán

Đối với tạo báo cáo best-effort, request timeout có thể đánh dấu job `EXPIRED` và hủy worker nếu hợp đồng cho phép. Nhưng với thanh toán, nhận timeout từ API bên ngoài có thể là **UNKNOWN**: downstream đã commit nhưng response bị mất. Khi đó cần truy vấn trạng thái bằng operation ID, không tạo giao dịch trùng hoặc tự tuyên bố hủy thành công.

Một quy tắc an toàn:

- **Bước chuẩn bị** có thể bỏ khi không còn cần.
- **Bước đã commit** cần được công nhận, đối soát hoặc compensation theo luật nghiệp vụ.
- **Bước ngoài hệ thống đang “in flight”** có thể có kết quả không biết ngay; không làm lại vô điều kiện.

### 10.3. @Async, CompletableFuture và cancellation

Khi method Spring `@Async` trả `CompletableFuture<T>`, caller có một future đại diện cho invocation. Việc gọi `future.cancel(true)` không tự tạo một business cancellation token bền vững. Ngữ nghĩa interruption của một executor-specific `Future` cũng không được suy từ API `CompletableFuture.cancel`.

Thiết kế rõ ràng hơn là cho caller gọi một **cancel command** có hiệu lực nghiệp vụ, ví dụ thay `RUNNING` thành `CANCEL_REQUESTED` hoặc tăng generation trong database. Worker kiểm tra token để dừng sớm, còn commit service kiểm tra authoritative state tại ranh giới effect.

## 11. Deadline: đồng hồ nào và ai có quyền đánh giá?

Có hai loại deadline:

- **Local elapsed-time deadline:** dùng `System.nanoTime()` hoặc `Duration` để tính thời gian trôi qua **trong cùng JVM**, không phụ thuộc điều chỉnh wall clock. Ví dụ budget tính toán 200 ms.
- **Business expiration timestamp:** dùng một `Instant`/timestamp lưu bền vững và nguồn thời gian authoritative, thường đánh giá ở database hoặc service sở hữu state. Dùng cho job qua nhiều replica/restart.

Ví dụ một checkpoint local (Java fragment):

```java
long deadlineNanos =
        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(200);

if (System.nanoTime() - deadlineNanos >= 0) {
    // Đã hết budget local; nên dừng pha chuẩn bị.
}
```

Đây là minh họa với budget nhỏ hợp lý; chọn cách so sánh và xử lý overflow phù hợp với thời lượng thực tế. Giá trị `System.nanoTime()` chỉ dùng đo **khoảng cách thời gian**, không phải epoch timestamp để ghi DB hay so sánh giữa hai JVM.

Dùng `Instant.now()` trong từng worker của các replica để quyết định commit có thể tạo khác biệt khi clock lệch. Nếu yêu cầu tuyệt đối tại database boundary, dùng thời gian của database trong **cùng câu lệnh/transaction** bảo vệ effect và xác định quy tắc với commit gần ranh giới deadline.

### 11.1. Truyền deadline đến các bước tiếp theo

Nếu một request có deadline tổng 2 giây, không nên cho mỗi HTTP client retry thêm 2 giây độc lập và tạo tổng thời gian 10 giây. Truyền budget còn lại hoặc absolute deadline có nguồn thời gian rõ tới các tầng xử lý; client connect/read/total timeout phải nằm trong budget phù hợp.

Hết budget trước khi gọi API ngoài thì nên skip khi policy cho phép. Nếu API đã được gửi, timeout không đủ để biết nó có áp dụng side effect hay không; cần operation ID và reconciliation.

## 12. Không áp dụng “chỉ việc mới nhất” cho mọi nghiệp vụ

Latest-wins thích hợp cho **preview, search suggestions, render ảnh tạm, dự đoán thay đổi UI, kết quả tính toán cũ có thể bỏ**. Nó không mặc nhiên thích hợp cho các lệnh **ghi nhận trách nhiệm, đặt hàng, tính phí, gửi giao dịch**.

| Nghiệp vụ | Có thể hủy bỏ công việc cũ? | Chính sách thường cần |
| --- | --- | --- |
| Gợi ý tìm kiếm | Có, theo session/query version | Generation/sequence theo session; không render response cũ |
| Preview PDF chưa được xuất bản | Thường có | Skip nếu phiên bản cũ; commit theo revision |
| Tính toán chỉ để hiển thị dashboard | Có khi dữ liệu cũ không còn cần | Cancellation token + latest-wins cache update |
| Gửi thông báo marketing best-effort | Phụ thuộc opt-out và cửa sổ gửi | Kiểm tra consent/expiry ngay tại delivery; idempotency |
| Gửi email hóa đơn bắt buộc | Không thể tùy tiện bỏ nếu đã phát sinh hóa đơn | Durable job, audit trạng thái, idempotent delivery |
| Thanh toán/chuyển tiền | Không suy từ HTTP timeout | Command ID bền vững, truy vấn trạng thái, reconciliation |
| Cập nhật trạng thái đơn hàng | Chỉ khi invariant cho phép | Optimistic lock/version, state machine, transaction |
| Audit bắt buộc | Không thể bỏ vì caller đã rời trang | Persist event/outbox trước khi xác nhận |

**Nguyên tắc:** trước khi chặn stale side effect, phải xác định “stale” dựa trên luật domain. Một nhiệm vụ bị timeout ở UI không đồng nghĩa quyền thực hiện nghiệp vụ đã hết.

## 13. External side effect: tại sao không thể bảo đảm bằng if(cancelled)?

Giả sử worker gửi request HTTP tới dịch vụ ngoài:

```text
Worker          Local DB          External API
  |
  | check generation=8
  |-------------------------> (chưa có effect)
  |
  |                        hủy/thay thế lên generation=9
  |
  | HTTP POST(generation=8) ---------------------->
  |                                              ghi effect
```

Dù local database đã lưu generation 9 trước HTTP POST, một lần kiểm tra local trước POST không thể ngăn API bên ngoài xử lý phiên bản 8. **Ranh giới cuối cùng kiểm soát effect là nơi chấp nhận effect**.

Các chiến lược tùy khả năng downstream:

1. **Downstream có fencing/version check:** truyền resource ID và generation; bên nhận atomic check token và thay đổi state. Đây là cách mạnh khi cần từ chối stale command.
2. **Downstream có idempotency key:** bảo vệ khỏi duplicate retry; vẫn cần business ordering/version nếu lệnh cũ khác ID không được phép ghi.
3. **Transactional outbox + consumer:** bảo đảm giao message gắn transaction nội bộ; chưa tự loại bỏ stale delivery sau khi outbox commit.
4. **Reservation/prepare-commit protocol:** nếu API hỗ trợ đặt chỗ và xác nhận, quy định thời điểm lệnh có hiệu lực và cách hủy reservation.
5. **Compensation/reconciliation:** khi effect đã diễn ra hoặc không biết trạng thái, dùng nghiệp vụ hoàn tác/đối soát; không giả vờ có atomic rollback qua HTTP.
6. **Thiết kế không phát effect nếu quá hạn** bằng cách để nơi gửi/quyết định cuối cùng kiểm tra policy, với hiểu biết rằng một effect đã được phía ngoài chấp nhận có thể không thể thu hồi.

Không giữ DB lock trong thời gian dài quanh HTTP call chỉ để hy vọng tránh mọi race: lock có thể làm cạn connection, deadlock, và không tạo transaction phân tán với API ngoài. Muốn bảo đảm toàn cục, cần protocol toàn cục hoặc chấp nhận trạng thái có thể đối soát.

## 14. Trạng thái hủy: phản hồi trung thực với caller

Một API hỗ trợ hủy không nên luôn trả “đã hủy thành công” chỉ vì đã nhận request cancel. Có thể có các kết quả:

- `CANCEL_REQUESTED`: đã ghi nhận mong muốn dừng; worker/downstream chưa xác nhận.
- `CANCELLED`: quy tắc nghiệp vụ xác nhận không còn effect tương lai được phép, với phạm vi được bảo đảm.
- `TOO_LATE`: effect đã commit trước khi cancel.
- `NOT_FOUND` hoặc `ALREADY_TERMINAL`: không có job hoặc job đã kết thúc.
- `UNKNOWN`: request bên ngoài đã gửi nhưng chưa biết outcome.
- `REJECTED_STALE`: command cũ bị authoritative boundary từ chối.
- `EXPIRED`: hết hạn theo nguồn thời gian và chính sách đã định.

Ví dụ response JSON minh họa (schema do ứng dụng thiết kế):

```json
{
  "jobId": "report-42",
  "status": "CANCEL_REQUESTED",
  "generation": 9,
  "effectCommitted": false
}
```

Không suy từ `effectCommitted=false` tại một thời điểm rằng không có effect ở hệ ngoài đang in-flight. Với các hệ thống có side effect quan trọng, endpoint trạng thái cần dựa vào operation ID và state bền vững.

### 14.1. Idempotent cancel và retry

Lệnh cancel thường nên có tính idempotent: gửi lại cùng một yêu cầu cancel không tạo tác dụng phụ mới. Nhưng **cancel** và **commit** cạnh tranh nên kết quả có thể khác tùy thứ tự được chốt trong authoritative store. Client cần xử lý `TOO_LATE`/`ALREADY_TERMINAL` thay vì retry vô hạn.

## 15. Kết hợp với CompletableFuture pipeline và callbacks

Một lỗi dễ bỏ sót là source đã bị timeout nhưng callback độc lập còn chạy:

```java
CompletableFuture<Result> source = runAsync();
CompletableFuture<Void> audit =
        source.thenAcceptAsync(result -> persist(result), auditExecutor);
```

Đây là fragment. Nếu `source` được `orTimeout` **trước khi** complete bình thường, callback `thenAccept` phụ thuộc **normal completion** sẽ bị bỏ qua. Nhưng **nếu callback đã được kích hoạt/nộp trước khi timeout/hủy**, việc cancel một future liên quan không phải bảo đảm callback đã chạy sẽ bị interrupt hoặc không còn side effect.

Ngoài ra, callback có thể được gắn trên **future khác**, dùng manual `complete`, hoặc tạo một command riêng trước timeout. Vì vậy không được coi `thenAccept` normal-only là cơ chế bảo vệ commit duy nhất.

**Mẫu an toàn hơn:**

```java
CompletableFuture<Outcome> pipeline =
        source.thenApplyAsync(result ->
                commitService.applyIfCurrent(
                        jobId, expectedGeneration, result),
                commitExecutor);

return pipeline;
```

Fragment này chỉ đúng nếu `applyIfCurrent` **thực hiện kiểm tra nguyên tử tại boundary**; lời gọi method tên như vậy không tự đảm bảo điều kiện. Việc dùng `thenApplyAsync` chỉ giúp chọn executor cho bước commit, không biến một câu `if` thông thường thành transaction.

### 15.1. Cancellation của downstream và upstream không đối xứng

Hủy dependent future không luôn hủy computation upstream; hủy source không bảo đảm interrupt một callback đã chạy. Với pipeline có nhiều nhánh, mỗi nhánh có thể có trạng thái riêng. Cần giữ **operation context** chứa job ID, generation và deadline, đồng thời mỗi side-effect boundary kiểm tra quyền thực hiện của chính nó.

### 15.2. Reactor/WebFlux và timeout

Trong mô hình reactive, các operator `timeout`/cancellation phát tín hiệu dừng subscription phù hợp; nhưng **không bảo đảm rollback side effect** đã chạy hoặc khiến mọi thư viện ngoại vi lập tức dừng. Blocking client được offload lên worker có thể tiếp tục làm việc nếu cancellation chưa được API hỗ trợ. Bảo vệ bằng cùng nguyên tắc: idempotency, business state, fencing/conditional commit tại nơi effect được thực hiện.

## 16. Chiến lược triển khai trong Spring có nhiều replica

Một kiến trúc tham khảo:

```text
HTTP Controller
    |
    | create/cancel/replace command
    v
Job Application Service --transaction--> DB: state, generation, deadline
    |
    | enqueue job ID + expected generation
    v
Durable Queue ---------------------> Worker Instance A/B/C
                                         |
                                         | prepare/compute
                                         | cooperative stop checks
                                         v
                                   Commit Service
                                         |
                                         | conditional UPDATE + outbox
                                         v
                                 authoritative DB
                                         |
                                         v
                                   Outbox Delivery
                                         |
                                         | operationId + fencing token
                                         v
                                Downstream acceptance gate
```

Không phải mọi ứng dụng cần đầy đủ queue/outbox; bài toán preview đơn giản trong một JVM có thể dùng local token và versioned cache write. Nhưng **mức bảo đảm phải khớp rủi ro**. Khi có nhiều replica, dùng `AtomicBoolean` ở từng pod không đủ cho một global latest-wins invariant.

### 16.1. Quy tắc ownership

- Controller chỉ yêu cầu hủy; không tự nhận quyền ghi một trạng thái terminal sai.
- Job service/DB làm nguồn sự thật về state/generation.
- Worker có thể bỏ công việc sớm để tiết kiệm tài nguyên, nhưng không tự quyết định last-write-wins bằng timestamp cục bộ.
- Commit service kiểm tra state/revision cùng transaction.
- Downstream kiểm tra token hoặc idempotency theo hợp đồng; nếu không hỗ trợ, phải thiết kế reconciliation/compensation.
- Scheduler/retry controller không phát lại stale command vô điều kiện.

### 16.2. @Transactional và @Async

`@Async` thường đưa method sang worker thread; transaction trong caller thread không tự theo sang worker. `@Transactional` cần được đặt trên boundary chạy đúng thread và được Spring AOP intercept qua bean reference. Tránh `this.commit()` trong cùng bean nếu đang dựa vào proxy transaction mặc định.

Khi exception xảy ra, phải xác định transaction đã rollback hay đã commit trước khi retry; chỉ quan sát `CompletableFuture.isCompletedExceptionally()` không đủ để kết luận effect ngoài hệ thống chưa xảy ra.

### 16.3. Graceful shutdown

Khi ứng dụng nhận SIGTERM/đóng Spring context:

1. Ngừng nhận job mới, cập nhật readiness nếu thích hợp.
2. Đánh dấu worker lease/cancel intent theo hợp đồng nếu không thể hoàn thành trong grace period.
3. Yêu cầu task kiểm tra interruption/token ở checkpoint.
4. Đợi những commit quan trọng trong giới hạn thời gian cho phép.
5. Dựa vào job store/queue để phục hồi công việc chưa xác nhận, với idempotency và fencing để ngăn owner cũ ghi muộn.
6. Không hứa một in-memory `Future` sẽ tồn tại sau khi process chết.

Nếu task bắt buộc được xử lý sau restart, không tạo một công việc chỉ bằng `executor.execute(...)` rồi trả HTTP `202 Accepted` trước khi có ghi nhận bền vững.

## 17. Quan sát và cảnh báo: đo effect chứ không chỉ đo Future

Các metric cần phân biệt:

| Metric/log | Mục đích |
| --- | --- |
| `jobs_expired_total` | Bao nhiêu job hết business deadline |
| `cancel_requested_total` | Bao nhiêu lệnh hủy được tiếp nhận |
| `tasks_interrupt_observed_total` | Bao nhiêu task thực sự ghi nhận tín hiệu interrupt |
| `effects_skipped_stale_total` | Bao nhiêu commit bị từ chối do generation/state cũ |
| `effects_applied_total` | Bao nhiêu effect được authoritative boundary chấp nhận |
| `effects_duplicate_total` | Bao nhiêu command trùng operationId đã bị dedup |
| `effects_after_expiration_total` | Theo chính sách, có effect nào được phép chấp nhận sau expiry? Nếu không, phải cảnh báo |
| `inflight_external_unknown` | Bao nhiêu external operations đang chưa đối soát outcome |
| `queue_wait`, `active_workers`, `rejects` | Tác dụng của quá tải lên deadline/cancellation |

Log nên chứa **job ID, operation ID, resource ID dạng an toàn, generation, expected/current version, deadline, trạng thái trước/sau, lý do skip**. Không ghi thông tin cá nhân nhạy cảm hoặc payload thanh toán toàn phần vào log.

Cần kiểm soát cardinality trong metrics: không dùng mỗi job ID thành một metrics label. Giữ ID trong structured logs/traces, còn metrics labels nên là tập giá trị hữu hạn như `status`, `executor` và `operationType`.

## 18. Kiểm thử race, hết hạn và tác dụng phụ

Trước khi đưa vào production, phải test **chính trường hợp cạnh tranh**:

1. **Timeout trước khi worker bắt đầu:** task có được skip không? Queue đã có task cũ có bị discard hợp lý không?
2. **Hủy giữa pha tính toán:** worker phát hiện token và không gọi downstream.
3. **Hủy ngay sau check trước effect:** tái hiện bằng latch như `CheckThenActRaceDemo`; giải pháp thật phải chặn được ở commit.
4. **Commit thắng trước cancel:** API cancel phải trả trạng thái đúng (`TOO_LATE` hoặc equivalent), không giả vờ rollback.
5. **Cancel thắng trước commit:** conditional write trả 0 rows; không có outbox effect mới.
6. **Thử lại cùng operationId:** idempotency/dedup không tạo effect thứ hai.
7. **Lệnh cũ đến sau lệnh mới:** downstream fencing từ chối token cũ.
8. **Worker chết và restart:** job được phục hồi theo policy, lease/token của worker cũ không còn hiệu lực.
9. **Tác vụ I/O không phản hồi interrupt:** timeout chỉ tác động caller hay thật sự hủy remote operation? Ghi rõ kết quả UNKNOWN khi cần.
10. **Nhiều replica:** hai worker với cùng hoặc khác generation không ghi đè kết quả hợp lệ.
11. **Clock lệch:** DB authoritative time vẫn đánh giá expiry nhất quán.
12. **Mất response sau effect:** retry không nhân đôi effect nhờ idempotency/reconciliation.

### 18.1. Unit test không thay thế integration test

Các ví dụ với `AtomicLong`/`synchronized` kiểm thử ý tưởng trong **một JVM**. Cần integration test cho câu UPDATE có điều kiện (số hàng cập nhật), transaction rollback, unique constraint, outbox và hành vi database isolation. Có thể dùng Testcontainers hoặc database test environment để chạy đúng engine cần triển khai.

Đối với external API, cần contract test bằng fake/server có hỗ trợ idempotency/fencing, và fault injection ở điểm “request đã nhận nhưng response thất lạc”. Không dùng `Thread.sleep` làm phương pháp duy nhất để tạo race; dùng latch/barrier và deadline có timeout.

### 18.2. Tiêu chí đúng về mặt nghiệp vụ

Một hệ thống không được xem là đúng chỉ vì test cho thấy `cancelledFuture.isCancelled() == true`. Cần chứng minh invariant, ví dụ:

- Với tài nguyên `report-42`, một commit thành công phải mang **current generation tại linearization point**.
- Không có hai commit khác nhau cho cùng `operationId`.
- Khi cancel được chốt trước commit, worker cũ không tạo DB effect.
- Nếu external effect đã được chấp nhận trước cancel, hệ thống trả trạng thái đúng và không “hủy giả”.
- Khi có trạng thái UNKNOWN, có cơ chế đối soát mà không phát lại lệnh nguy hiểm.

## 19. Những sai lầm thường gặp

| Sai lầm | Vì sao nguy hiểm | Cách sửa |
| --- | --- | --- |
| “Future timeout là worker dừng” | Timeout thường chỉ tác động waiting/completion | Kiểm tra task và bảo vệ effect |
| “`CompletableFuture.cancel(true)` luôn interrupt” | Javadoc nói flag interrupt không điều khiển implementation này | Token hợp tác + authoritative gate |
| “Thêm `if (!cancelled)` trước ghi là đủ” | Check-then-act race | Atomic conditional commit / fencing |
| “`AtomicBoolean` đủ cho Kubernetes nhiều pod” | Mỗi pod có trạng thái riêng | Shared authoritative state/generation |
| “Idempotency key chặn mọi stale command” | Lệnh cũ khác ID vẫn có thể thắng | Kiểm tra version/fencing bên nhận |
| “Outbox bảo đảm event cũ không phát” | Outbox đảm bảo giao sau commit, không tự kiểm tra freshness tại downstream | Consumer/downstream gate hoặc policy chấp nhận event đã commit |
| “Đã gọi HTTP rồi vẫn rollback được bằng cancel” | API ngoài có thể đã áp dụng side effect | Idempotency, reconciliation, compensation |
| “Có @Transactional trên method nào cũng atomic” | Self-invocation/proxy boundary và transaction scope có thể sai | Bean riêng, verify transaction và SQL |
| “UI rời trang thì hủy luôn thanh toán” | UI timeout không nói gì về business commit | Phân biệt UNKNOWN, TOO_LATE, CANCELLED |
| “Retry đến khi thành công” | Nhân đôi tác dụng phụ, gửi lệnh cũ | Bounded retry, idempotency, generation |
| “Dùng deadline theo local clock cho toàn cụm” | Clock lệch và restart | Authoritative timestamp ở shared store |
| “Task bị reject thì bỏ qua lặng lẽ” | Mất công việc hoặc trạng thái sai | Return error, durable enqueue hoặc explicit policy |

## 20. Bài tập ứng dụng: tạo báo cáo chỉ cho phép phiên bản mới nhất

Giả sử người dùng liên tục thay đổi bộ lọc báo cáo. Hệ thống có nhiều replica Spring Boot, worker có thể chạy lâu và kết quả sẽ được ghi vào DB và phát event để cập nhật màn hình.

**Yêu cầu thiết kế:**

1. Đặt tên tài nguyên `reportId`, operation ID và generation. Khi request mới đến, tăng generation trong DB và đánh dấu generation cũ stale.
2. Worker nhận immutable command chứa `reportId` + `generation` + deadline. Trong pha tính toán, kiểm tra tín hiệu hủy để dừng sớm.
3. Khi tính xong, dùng conditional UPDATE trên `reportId` và `generation`, với trạng thái/expiry thích hợp. Nếu 0 rows, trả `SKIPPED_STALE` và **không** phát effect.
4. Nếu UPDATE thành công, ghi outbox trong cùng transaction. Tạo unique idempotency key cho event.
5. Consumer chỉ gửi event tới nơi có chính sách version/fencing rõ; nếu UI chỉ cần bản mới nhất, giao thức nhận có thể từ chối message version thấp hơn bản đã công nhận.
6. Khi caller timeout, không chỉ gọi `future.cancel(true)` rồi xóa job state; phải persist/sắp xếp policy hủy.
7. Khi hai worker cùng commit, integration test xác nhận chỉ bản được phép cập nhật và không có outbox trùng.
8. Khi cancel đến sau DB commit, phản hồi rõ `TOO_LATE` hoặc trạng thái equivalent; đừng xóa audit trail.
9. Khi upstream/downstream không đáp ứng, sử dụng timeout, bounded retry, metric và đối soát UNKNOWN.
10. Thực hiện load test burst, executor shutdown và rolling restart; đo stale skips, effects applied và queue wait.

### 20.1. Checklist tự đánh giá

- [ ] Tôi phân biệt được caller timeout và worker thực sự dừng.
- [ ] Tôi hiểu `CompletableFuture.cancel(true)` không cưỡng chế interrupt nguồn đang chạy.
- [ ] Tôi biết cách dùng cooperative token/interrupt tại các checkpoint để giảm công việc lãng phí.
- [ ] Tôi có thể tái hiện check-then-act race bằng latch.
- [ ] Tôi hiểu vì sao kiểm tra và commit phải atomic khi cần bảo đảm không có stale effect.
- [ ] Tôi biết generation/fencing khác idempotency key ở mục tiêu nào.
- [ ] Tôi biết dùng optimistic locking/conditional UPDATE khi nhiều replica cùng ghi.
- [ ] Tôi hiểu hạn chế của outbox và local pre-send check đối với external side effect.
- [ ] Tôi biết phân biệt CANCEL_REQUESTED, CANCELLED, TOO_LATE và UNKNOWN.
- [ ] Tôi không tự hủy giao dịch quan trọng chỉ vì HTTP/UI timeout.
- [ ] Tôi kiểm chứng Spring `@Async`, `@Transactional`, executor và context đúng nơi chạy.
- [ ] Tôi có test cho cancel-before-commit, commit-before-cancel, retry và restart.
- [ ] Tôi quan sát được tác dụng phụ thật, không chỉ trạng thái future.

**Kết luận:** để **ngăn công việc hết giá trị tiếp tục tạo tác dụng phụ**, cần nhiều lớp: dừng hợp tác để tiết kiệm tài nguyên, **định nghĩa hiệu lực nghiệp vụ**, và **thực thi kiểm tra nguyên tử ở ranh giới chấp nhận effect**. Timeout/cancel chỉ thay đổi quyền chờ hoặc gửi tín hiệu dừng; chúng không xóa tác dụng phụ đã commit. Đối với hệ phân tán, generation/fencing, idempotency, transaction/outbox và reconciliation phải được thiết kế theo **mức bảo đảm thực tế** của từng hệ thống.

## 21. Tài liệu chính thức

- Java SE 17 — [Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): `get(timeout)`, `cancel` và cancellation contract.
- Java SE 17 — [CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): `cancel`, `orTimeout`, `completeOnTimeout`, dependent stages và exceptional completion.
- Java SE 17 — [CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): đồng bộ các bước trong test concurrency.
- Spring Framework — [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@Async`, `TaskExecutor`, async method return type và exception handling.
- Spring Framework — [Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html): ranh giới `@Transactional` và nguyên tắc áp dụng transaction advice.
- Spring Framework — [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): lý do self-invocation có thể bỏ qua advice.

Thực hành trên Java 17+ và đối chiếu với phiên bản Spring, DB engine và downstream thực tế. Các đoạn SQL và Spring trong bài được ghi rõ là **mẫu thiết kế**, không phải API có sẵn hoặc giao dịch phân tán tự động.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="timeout-cancellation-009">Kiểm tra hủy trước khi bắt đầu và hủy khi tác vụ đang chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra hủy trước khi bắt đầu và hủy khi tác vụ đang chạy

Trong ứng dụng Spring Framework xử lý công việc đồng thời, một tác vụ có thể được **đưa vào hàng đợi nhưng chưa chạy**, hoặc **đã được một worker bắt đầu thực thi**. Gọi `Future.cancel(...)` ở hai thời điểm này có ý nghĩa rất khác nhau. Khi chưa chạy, một `FutureTask` bị hủy thành công sẽ không thực thi body tác vụ; khi đã chạy, hủy chỉ là **nỗ lực yêu cầu dừng**, thường thông qua interruption hoặc tín hiệu hợp tác. Việc một `Future` báo `isCancelled() == true` **không đồng nghĩa** rằng code nghiệp vụ đã dừng, một giao dịch đã rollback hay mọi side effect đã được đảo ngược.

Bài học thuộc nhóm **Hết hạn và hủy** trong module **Spring Framework - concurrency**. Trọng tâm là nhận diện ranh giới **trước lúc bắt đầu** và **trong lúc đang chạy**, cách kiểm tra trạng thái chính xác, xử lý race condition, kiểm chứng bằng Java 17+ và tích hợp thực tế với `ThreadPoolTaskExecutor`, `@Async` và `CompletableFuture`.

## 1. Kết quả học tập

Sau khi hoàn thành, bạn có thể:

- Mô tả các trạng thái của task: queued, running, completed, failed, cancellation requested và cancelled; không đồng nhất chúng thành một cờ `isDone`.
- Dùng `Future.cancel(false)` và `Future.cancel(true)` đúng thời điểm, hiểu kết quả boolean cũng như `isCancelled()`, `isDone()` và `get()`.
- Chứng minh bằng kiểm thử xác định rằng task chưa bắt đầu sẽ không chạy body sau khi một `FutureTask` bị hủy thành công.
- Hiểu vì sao task đã chạy vẫn có thể tiếp tục dù Future bị cancel, kể cả khi đã gọi `cancel(true)`.
- Sử dụng `InterruptedException`, `Thread.currentThread().isInterrupted()` và cancellation token cho cooperative cancellation.
- Phân biệt hủy `FutureTask`/`ExecutorService.submit` với hủy `CompletableFuture`, đặc biệt là việc `CompletableFuture.cancel(true)` không điều khiển interruption.
- Thiết kế API hủy trong Spring để tránh báo “đã dừng” khi mới chỉ “đã yêu cầu hủy”.
- Kiểm thử và đo lường queued cancellation, running cancellation, shutdown, timeout và việc xử lý side effect.

## 2. Đặt tên đúng các giai đoạn của công việc

Một `ExecutorService` có thể nhận task qua `submit`, nhưng “đã nhận” không có nghĩa “đã bắt đầu”. Với executor có queue, thời gian sống đơn giản là:

~~~text
SUBMITTED
   |
   v
QUEUED / chờ được worker chọn
   |
   +---- cancel thành công trước khi bắt đầu
   |           |
   |           v
   |       CANCELLED
   |       body không chạy (với FutureTask đã hủy)
   |
   v
RUNNING
   |
   +---- chạy xong --------------------> SUCCEEDED
   +---- ném exception ----------------> FAILED
   +---- nhận yêu cầu hủy --------------+
                                        |
                          có hợp tác dừng?
                            /        \
                          có          không
                           |           |
                           v           v
                      kết thúc sớm    vẫn chạy tiếp
~~~

Sơ đồ này nói về **trạng thái công việc nghiệp vụ**, không phải toàn bộ trạng thái nội bộ của `FutureTask`. Một Future có thể đã được đánh dấu CANCELLED trong khi method body vẫn đang hoàn tất cleanup hoặc tiếp tục chạy vì bỏ qua tín hiệu hủy. Chính vì vậy không thể suy từ `isDone()` sang “worker đã thoát”.

### Phân biệt ba loại sự kiện

| Sự kiện | Ý nghĩa | Có đảm bảo body đã dừng? |
| --- | --- | --- |
| Caller gửi yêu cầu cancel | Caller muốn hủy công việc | Không |
| `Future.cancel(...)` thành công | Handle Future chuyển sang trạng thái hủy theo implementation | Không nếu body đã chạy; có thể chỉ chặn việc bắt đầu |
| Worker xác nhận đã thoát khỏi vùng xử lý | Code thực thi đã dừng hoặc hoàn thành cleanup | Có đối với đoạn code được kiểm soát, nếu dùng tín hiệu hoàn tất đáng tin |

Trong hệ thống có job store, nên lưu ít nhất trạng thái **cancel_requested** tách biệt với **cancelled/stopped**. Nếu một request HTTP gọi cancel, response “đã tiếp nhận yêu cầu hủy” đáng tin hơn “công việc đã dừng” khi bạn chưa có xác nhận của worker.

## 3. Hợp đồng Future.cancel(boolean)

`java.util.concurrent.Future<V>` định nghĩa:

~~~java
boolean cancel(boolean mayInterruptIfRunning);
boolean isCancelled();
boolean isDone();
V get() throws InterruptedException, ExecutionException;
V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;
~~~

Ý nghĩa của `cancel(...)` theo API:

- Nếu task **chưa bắt đầu** và hủy thành công, task không được thực thi sau đó.
- Nếu task **đã bắt đầu**, tham số `mayInterruptIfRunning` quyết định có nên thử interrupt thread đang chạy **nếu implementation biết thread đó**.
- Nếu task **đã hoàn tất**, đã bị hủy, hoặc không thể hủy, lời gọi mới không làm thay đổi kết quả đã chốt; boolean có thể là `false`.
- Javadoc khuyên kiểm tra `isCancelled()` để xác định trạng thái sau cuộc đua hủy; đừng diễn giải mỗi boolean trả về thành xác nhận worker đã dừng.
- `isDone()` là `true` khi Future đã hoàn thành **bất kỳ cách nào**: thành công, thất bại hoặc hủy. Nó không có nghĩa là thành công.

Khi Future đã cancel, gọi `get()` thông thường ném `CancellationException` (unchecked). Nếu task hoàn thành lỗi không do cancel, `get()` ném `ExecutionException`. `get(timeout, unit)` có thể ném `TimeoutException` chỉ vì người chờ hết hạn; timeout **không tự gọi cancel**.

### Bảng kết quả thường gặp

| Thời điểm gọi | `cancel(false)` | `cancel(true)` |
| --- | --- | --- |
| Task còn chờ, chưa bắt đầu | Có thể ngăn body thực thi nếu hủy thành công | Cũng có thể ngăn body trước khi chạy |
| Task đang chạy với FutureTask | Có thể đánh dấu Future cancelled **mà không interrupt**; body vẫn tiếp tục | Có thể đánh dấu cancelled và thử interrupt worker |
| Task đã hoàn tất trước lời gọi | Không đổi kết quả; thường trả `false` | Không đổi kết quả; thường trả `false` |
| Task đang chạy nhưng bỏ qua interruption | Không yêu cầu interrupt | Interrupt có thể được gửi, nhưng body vẫn có thể tiếp tục |
| Cancellation từ nhiều caller đua nhau | Kết quả từng lời gọi phụ thuộc cuộc đua | Không được suy luận chỉ từ một lần trả boolean |

`cancel(true)` **không giống** `Thread.stop()`; Java hiện đại không có API an toàn để cưỡng bức dừng tùy ý một đoạn code Java. Interruption là tín hiệu hợp tác, không phải sự đảm bảo mọi thao tác I/O, lock hoặc native call sẽ ngừng ngay.

## 4. Hủy trước khi bắt đầu: ngăn body chạy

Giả sử executor chỉ có một worker. Worker đang xử lý job A, còn job B được submit và nằm trong queue:

~~~text
Worker 1:  [job A đang chờ latch]
Queue:     [job B / FutureTask]
Caller:    jobB.cancel(false)
Queue:     [job B đã bị đánh dấu cancelled, có thể vẫn hiện diện]
Worker 1:  khi lấy job B, FutureTask.run() không thực thi body B
~~~

Điều kiện **job B chưa bắt đầu** là cốt lõi. Nếu job B đã rời queue và bắt đầu giữa lúc bạn quan sát và lúc bạn gọi cancel, đó là **cuộc đua** và bạn đang hủy một task đang chạy, không còn tình huống trước khi bắt đầu.

Với `ExecutorService.submit(...)` dựa trên `ThreadPoolExecutor`, task thường được bao vào một `RunnableFuture` như `FutureTask`. `FutureTask.run()` sẽ kiểm tra trạng thái; khi Future đã cancel, body task không được gọi. Như vậy hủy trước khi chạy có thể là phương thức tránh một công việc không còn cần thiết.

### Nhưng job có tự biến mất khỏi queue không?

**Không nhất thiết.** `ThreadPoolExecutor` thường lưu một wrapper trong queue. Khi wrapper được cancel, nó có thể **còn nằm trong hàng đợi vật lý** cho tới khi worker lấy ra hoặc khi có một thao tác dọn dẹp thích hợp. Điều này gây hai hệ quả:

1. `Future.isCancelled() == true` và `getQueue().size() > 0` có thể đồng thời đúng.
2. Số phần tử đang queue không đồng nhất với số job còn cần chạy.

`ThreadPoolExecutor.purge()` cố gắng gỡ những task Future đã bị cancel khỏi queue; đây là biện pháp reclaim bộ nhớ/queue và có chi phí duyệt queue, không nên gọi liên tục như cách điều phối cancellation chính. `remove(Runnable)` có thể không gỡ được wrapper nếu thứ bạn giữ là task gốc chứ không phải Runnable thực tế trong queue.

**Không nhầm API:** `setRemoveOnCancelPolicy(true)` là cấu hình của `ScheduledThreadPoolExecutor`, không phải setter của `ThreadPoolExecutor` thông thường hoặc một tính năng chung của mọi Spring `TaskExecutor`. Kiểm tra loại executor cụ thể trước khi đề xuất cấu hình dọn queue tự động.

### Vì sao cần kiểm tra trước khi bắt đầu?

Nếu ứng dụng tự lưu trạng thái hủy trong token hoặc job store, worker nên kiểm tra **sát trước thao tác có side effect đầu tiên**:

~~~java
if (token.isCancellationRequested()) {
    return; // bỏ qua việc chưa bắt đầu có ích
}
performFirstSideEffect(jobId);
~~~

Đây là **mẫu minh họa** trong method với `token` và `performFirstSideEffect` do ứng dụng cung cấp. Check này tránh được yêu cầu hủy đã tồn tại trước khi worker nhận job, nhưng **không tạo tính nguyên tử** giữa “kiểm tra” và “ghi side effect”. Cancel có thể đến ngay giữa hai dòng. Nếu hợp đồng bắt buộc “cancel thành công đồng nghĩa không ghi gì”, phải dùng trạng thái giao dịch, lock hoặc một admission/state transition được đồng bộ với nơi thực thi side effect.

## 5. Hủy khi đang chạy: interruption chỉ là lời đề nghị

Một task có thể đang chạy trên worker:

~~~text
Caller               Worker
  |                     |
  |                task đang chạy
  |----cancel(true)----->|
  |  Future cancelled    | nhận interrupt (nếu có hỗ trợ)
  |                      | kiểm tra/đáp ứng
  |                      +-- dừng sớm và cleanup
  |                      hoặc
  |                      +-- bỏ qua, tiếp tục chạy
~~~

Với `FutureTask` thông thường:

- `cancel(false)` có thể đánh dấu Future là canceled **nhưng không interrupt**. Body đã bắt đầu vẫn có thể thực thi tới cuối, kể cả sinh side effect.
- `cancel(true)` thử interrupt thread chạy task. Nếu đang chờ ở `Thread.sleep`, `BlockingQueue.take`, `CountDownLatch.await` hoặc nhiều API interruptible khác, chúng thường ném `InterruptedException`.
- Nếu đang chạy một vòng tính toán CPU không kiểm tra interrupt hoặc token, task có thể bỏ qua yêu cầu và tiếp tục đến khi xong.
- Nếu đang chạy I/O không hỗ trợ interrupt theo kỳ vọng, cần timeout/cancellation API riêng của thư viện, ví dụ client HTTP, driver database hoặc giao thức RPC.
- `cancel(true)` có thể trả `true` **trước khi worker thực sự thoát**. Đừng dùng boolean này để xác nhận mọi tài nguyên đã được giải phóng.

### InterruptedException và cờ interrupt

Khi một API chờ interruptible ném `InterruptedException`, trạng thái interrupt thường đã được xóa khi exception được ném. Nếu method không thể truyền exception lên caller do chữ ký/khung làm việc, mẫu thường dùng là **khôi phục interrupt** rồi kết thúc có kiểm soát:

~~~java
try {
    blockingOperation();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Hủy việc tiếp theo, thu dọn tài nguyên và return/throw phù hợp.
    return;
}
~~~

Đây là **mã minh họa**, với `blockingOperation()` do ứng dụng định nghĩa. Chỉ gọi `Thread.currentThread().interrupt()` không tự chấm dứt method. Nếu bắt exception rồi tiếp tục vòng lặp như không có gì xảy ra, nhiệm vụ sẽ khó dừng và caller có thể nhầm rằng cancel đã thành công.

### Kiểm tra mà không xóa cờ

~~~java
if (Thread.currentThread().isInterrupted()) {
    // Dừng hợp tác hoặc chuyển sang logic cleanup.
}
~~~

`isInterrupted()` đọc trạng thái mà **không xóa** cờ. Ngược lại, static `Thread.interrupted()` đọc **và xóa** trạng thái interrupt của thread hiện tại; sử dụng tùy tiện trong vòng lặp hoặc thư viện có thể làm mất tín hiệu khiến tầng trên không nhìn thấy. Nếu dùng `Thread.interrupted()` có chủ đích để xử lý một lần, phải hiểu ý nghĩa việc clear flag.

## 6. Demo Java 17+ độc lập: cả ba tình huống hủy

Đây là chương trình hoàn chỉnh minh họa:

1. Hủy một task **đang chờ queue** trước khi body bắt đầu.
2. Hủy một task **đang chạy** bằng `cancel(true)` và xác minh nó quan sát interruption.
3. Hủy một task **đang chạy** bằng `cancel(false)`: Future bị hủy nhưng body vẫn hoàn thành một side effect được mô phỏng.
4. Hủy một `CompletableFuture`: handle hoàn thành ngoại lệ, không suy ra điều gì về interruption.

Tất cả các đường thực thi dùng `CountDownLatch` và timeout chống treo, **không phụ thuộc vào `Thread.sleep()`**.

Tệp **CancellationPhasesDemo.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class CancellationPhasesDemo {

    private static void cancelBeforeStart() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger queuedBodyRuns = new AtomicInteger();

        try {
            Future<?> blocker = executor.submit(() -> {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });

            if (!entered.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Blocker did not start");
            }

            Future<?> queued = executor.submit(() -> {
                queuedBodyRuns.incrementAndGet();
            });

            boolean cancelled = queued.cancel(false);
            release.countDown();

            blocker.get(5, TimeUnit.SECONDS);
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);

            System.out.println("queuedCancelled=" + cancelled);
            System.out.println("queuedBodyRuns=" + queuedBodyRuns.get());
            System.out.println("queuedIsDone=" + queued.isDone());
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void cancelRunningWithInterrupt() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        AtomicBoolean interruptSeen = new AtomicBoolean();

        try {
            Future<?> running = executor.submit(() -> {
                entered.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException ex) {
                    interruptSeen.set(true);
                    Thread.currentThread().interrupt();
                } finally {
                    exited.countDown();
                }
            });

            if (!entered.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Task did not start");
            }

            boolean cancelled = running.cancel(true);
            if (!exited.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Task did not exit");
            }

            System.out.println("runningCancelTrue=" + cancelled);
            System.out.println("interruptSeen=" + interruptSeen.get());

            try {
                running.get();
            } catch (CancellationException ex) {
                System.out.println("runningGet=CancellationException");
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void cancelRunningWithoutInterrupt() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        AtomicBoolean bodyContinued = new AtomicBoolean();

        try {
            Future<?> running = executor.submit(() -> {
                entered.countDown();
                try {
                    release.await();
                    bodyContinued.set(true);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    exited.countDown();
                }
            });

            if (!entered.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Task did not start");
            }

            boolean cancelled = running.cancel(false);
            release.countDown();

            if (!exited.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Task did not exit");
            }

            System.out.println("runningCancelFalse=" + cancelled);
            System.out.println("bodyContinued=" + bodyContinued.get());
            System.out.println("futureCancelled=" + running.isCancelled());
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void cancelCompletableFuture() {
        CompletableFuture<String> result = new CompletableFuture<>();
        boolean cancelled = result.cancel(true);
        System.out.println("cfCancel=" + cancelled);
        System.out.println("cfIsCancelled=" + result.isCancelled());

        try {
            result.join();
        } catch (CancellationException ex) {
            System.out.println("cfJoin=CancellationException");
        }
    }

    public static void main(String[] args) throws Exception {
        cancelBeforeStart();
        cancelRunningWithInterrupt();
        cancelRunningWithoutInterrupt();
        cancelCompletableFuture();
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac CancellationPhasesDemo.java
java CancellationPhasesDemo
~~~

Stdout của lần chạy thành công:

~~~text
queuedCancelled=true
queuedBodyRuns=0
queuedIsDone=true
runningCancelTrue=true
interruptSeen=true
runningGet=CancellationException
runningCancelFalse=true
bodyContinued=true
futureCancelled=true
cfCancel=true
cfIsCancelled=true
cfJoin=CancellationException
~~~

### Tại sao kiểm thử này xác định?

**Tình huống trước khi bắt đầu:** `entered.await()` đảm bảo worker đầu đã vào task blocker và đang chờ `release`. Vì executor chỉ có một worker, task thứ hai vẫn chưa thể chạy. `queued.cancel(false)` hủy wrapper này; sau đó giải phóng blocker. Lời gọi submit marker cuối và `get()` của marker bảo đảm worker đã đi qua vị trí của task bị hủy trong hàng đợi FIFO. Kết quả `queuedBodyRuns=0` chứng minh body thứ hai không chạy. `queuedIsDone=true` là trạng thái Future đã hủy, không phải bằng chứng body chạy thành công.

**Tình huống đã chạy + interrupt:** worker ghi `entered` trước khi đi vào một `await()` không được thả. `cancel(true)` thử interrupt thread đang chờ; task bắt `InterruptedException`, ghi `interruptSeen`, khôi phục cờ rồi thoát. `exited.await()` bảo đảm main chỉ in sau khi body đã đi qua cleanup. `running.get()` ném `CancellationException` vì **kết quả Future đã bị hủy**, không phải vì body ném lỗi nghiệp vụ.

**Tình huống đã chạy, không interrupt:** `cancel(false)` đánh dấu Future canceled nhưng không yêu cầu interrupt. Main thả `release`, task vẫn đặt `bodyContinued=true` và thoát. Bởi thế **`futureCancelled=true` và `bodyContinued=true` cùng đúng**. Đây là điểm cần nhớ khi thiết kế side effect.

**Tình huống CompletableFuture:** `cancel(true)` hoàn thành handle ở dạng cancellation; chương trình không tạo tác vụ tính toán nào cho handle đó. Ví dụ chỉ xác nhận semantics của handle, **không phải thí nghiệm gián đoạn worker**.

Nếu chạy trên môi trường có tài nguyên bất thường, timeout bảo vệ test khỏi treo vô hạn, nhưng sẽ khiến chương trình báo lỗi chứ không xuất stdout “thành công” giả.

## 7. Races ở ranh giới QUEUED -> RUNNING

Không có một thao tác “đọc `isDone` rồi chắc chắn task chưa bắt đầu” nào bảo đảm nguyên tử với scheduler. Các sự kiện có thể xen kẽ:

~~~text
Caller                              Worker
  |                                   |
  | đọc trạng thái đang chờ            |
  |                                   | lấy task khỏi queue
  |                                   | bắt đầu body
  |---- cancel(false) ---------------->|
  | Future có thể chuyển cancelled     | body vẫn chạy tiếp
~~~

Đây là **check-then-act race**. Ngay cả khi quan sát `getQueue().contains(task)` vừa trả true, đến lúc gọi `cancel` task có thể đã bị worker lấy ra. Queue size, active count và trạng thái “đang đợi” trong metrics chỉ là snapshot, không phải transaction với `cancel`.

Nếu yêu cầu nghiệp vụ chỉ là *best effort*, thường chấp nhận race và thiết kế task kiểm tra tín hiệu hủy. Nếu yêu cầu cứng rằng một job bị hủy trước mốc X **không được bắt đầu side effect**, cần một protocol đồng bộ:

1. Một **job state** được lưu bền, có ID và version.
2. Worker chuyển `QUEUED -> RUNNING` bằng một thao tác có điều kiện/atomic, ví dụ cập nhật bản ghi với điều kiện status vẫn QUEUED.
3. Người hủy chuyển `QUEUED -> CANCELLED` cũng bằng một thao tác có điều kiện.
4. Chỉ một chuyển trạng thái có thể thắng; nếu worker thắng, hủy trở thành **CANCEL_REQUESTED** và cần hợp tác dừng.
5. Worker kiểm tra trạng thái trước từng vùng side effect nhạy cảm và xác nhận `STOPPED` khi đã ra khỏi vùng xử lý.

Tùy hệ thống có thể dùng lock, CAS, database optimistic concurrency, message broker semantics hoặc lease; lựa chọn phải phù hợp cơ chế storage thật. `AtomicBoolean` chỉ đồng bộ trong **một JVM**, không quản lý các replica khác.

## 8. FutureTask khác CompletableFuture ở điểm cốt lõi

Cả `FutureTask` và `CompletableFuture` đều triển khai `Future`, nhưng hai cơ chế điều khiển khác nhau:

| Đặc tính | `FutureTask` (thường dùng bởi `submit`) | `CompletableFuture` |
| --- | --- | --- |
| Là đối tượng gắn với execution của `Callable/Runnable`? | Có, trực tiếp triển khai `RunnableFuture` | Không nhất thiết; chủ yếu là handle completion |
| `cancel(false)` trước khi chạy | Ngăn body của FutureTask chạy khi hủy thành công | Đánh dấu handle cancelled; không bảo đảm kiểm soát công việc nào phía sau |
| `cancel(true)` trong khi đang chạy | Thử interrupt runner thread nếu biết | Tham số `mayInterruptIfRunning` **không có tác dụng** trong implementation chuẩn |
| Kết quả `isCancelled` | Cho biết Future đã bị hủy | Cho biết CompletableFuture đã hoàn thành dạng cancellation |
| Tác động tới dependent stages | Không có composition chuẩn | Các stage phụ thuộc có thể hoàn thành exceptionally |
| Guarantee task ngừng/rollback side effect | Không | Không |

Theo Javadoc Java 17 của `CompletableFuture`, `cancel(boolean)` có cùng hiệu ứng với `completeExceptionally(new CancellationException())`, và `mayInterruptIfRunning` **không được dùng để điều khiển ngắt thực thi**. Do đó:

~~~java
CompletableFuture<String> future = new CompletableFuture<>();
future.cancel(true); // hủy completion handle, không tự ngắt worker
~~~

Đây là **đoạn Java minh họa**. Nếu một worker đang tính toán thứ sẽ gọi `future.complete(value)` về sau, worker đó có thể tiếp tục chạy; lời gọi `complete(value)` có thể thất bại vì future đã được kết thúc bằng cancellation.

### supplyAsync và cancel không tạo một đường interrupt đáng tin

~~~java
ExecutorService pool = Executors.newFixedThreadPool(2);
CompletableFuture<String> future =
        CompletableFuture.supplyAsync(() -> performWork(), pool);
future.cancel(true);
~~~

Đây là **mã minh họa**, `performWork` do ứng dụng cung cấp và phải shutdown pool trong code thực. `CompletableFuture.cancel(true)` **không cam kết interrupt lambda** đang chạy, kể cả khi bạn truyền một `ExecutorService`. Nếu cần dừng công việc thực sự, truyền cancellation token cho body hoặc sở hữu một `FutureTask` có đường interruption kiểm chứng được.

**Không nhầm:** một số framework có thể tạo wrapper riêng quanh CompletableFuture; semantics cancel/propagation có thể phụ thuộc implementation. Với `@Async` trong Spring, hãy xác nhận loại executor và Future trả về, đừng suy từ chữ ký `CompletableFuture<T>` rằng bạn có thể gián đoạn worker bằng `cancel(true)`.

## 9. Hợp tác hủy trong vòng lặp và đoạn blocking

Một task dài nên chia thành **đơn vị công việc nhỏ** và kiểm tra tín hiệu trước mỗi đơn vị:

~~~java
for (int chunk = 0; chunk < chunks.size(); chunk++) {
    if (token.isCancellationRequested()
            || Thread.currentThread().isInterrupted()) {
        return;
    }

    processChunk(chunks.get(chunk));
}
~~~

Đây là **mã minh họa**, các biến/hàm `token`, `chunks` và `processChunk` do ứng dụng cung cấp. Nếu `processChunk` bị chặn vô hạn, check ở đầu vòng lặp không có ích cho đến khi hàm trả về. Cần đặt timeout/cancel API ở chính I/O blocking.

### Cancellation token

Một token đơn giản trong một JVM:

~~~java
import java.util.concurrent.atomic.AtomicBoolean;

public final class CancellationToken {
    private final AtomicBoolean requested = new AtomicBoolean();

    public boolean request() {
        return requested.compareAndSet(false, true);
    }

    public boolean isCancellationRequested() {
        return requested.get();
    }
}
~~~

Code này **độc lập về mặt Java** nếu lưu trong tệp `CancellationToken.java` và biên dịch với JDK 17+. `AtomicBoolean` cung cấp visibility và atomicity cho trạng thái cancel. Nhưng `request()==true` chỉ có nghĩa **caller đầu tiên chuyển token sang requested**; nó không cho biết worker đã quan sát token hoặc đã dừng.

Nếu task hoạt động trên nhiều JVM, token trong RAM không đủ. Lưu yêu cầu hủy trong job store, gửi event hoặc polling đến các worker theo contract; trạng thái bền vững quan trọng hơn hình thức API.

### Khi task bắt InterruptedException

Một body có thể dùng:

~~~java
try {
    blockingClientCall();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    markCancellationObserved();
    return;
} finally {
    closeLocalResources();
}
~~~

Đây là **mẫu tích hợp** với các hàm do ứng dụng định nghĩa. Dọn tài nguyên trong `finally` nên idempotent và hạn chế block lâu. Không đánh dấu `CANCELLED` ở job store trước khi hoàn tất cleanup nếu hệ thống yêu cầu xác nhận thực sự ngừng; đồng thời không giữ transaction lớn trong lúc cleanup có thể kéo dài tùy ý.

## 10. Tích hợp trong Spring: chọn đúng handle để hủy

Spring `ThreadPoolTaskExecutor` dựa trên `ThreadPoolExecutor` và cũng hỗ trợ `submit(...)`. Nếu cần có quyền gọi `Future.cancel(...)` với các semantics của tác vụ executor, một cách dễ hiểu là **chủ động submit Runnable/Callable** và giữ lại Future. `TaskExecutor.execute(Runnable)` không trả Future nên không cho caller handle hủy qua lời gọi đó.

Tệp **CancellableExecutorConfig.java** của ứng dụng Spring:

~~~java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class CancellableExecutorConfig {
    @Bean(name = "cancellableExecutor")
    public ThreadPoolTaskExecutor cancellableExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("cancellable-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
~~~

Đây là **cấu hình Spring**, cần `spring-context`, không chạy một mình bằng lệnh `java`. Các con số thread/queue chỉ minh họa, không phải khuyến nghị sizing production. Bean do Spring quản lý được khởi tạo và đóng theo vòng đời context.

### Dispatcher giữ Future và token

Tệp **CancellableJobDispatcher.java**:

~~~java
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class CancellableJobDispatcher {
    private final ThreadPoolTaskExecutor executor;

    public CancellableJobDispatcher(
            @Qualifier("cancellableExecutor")
            ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    public JobHandle submit(String jobId) {
        CancellationToken token = new CancellationToken();
        Future<?> future = executor.submit(() -> run(jobId, token));
        return new JobHandle(future, token);
    }

    public boolean requestCancellation(JobHandle handle) {
        boolean firstRequest = handle.token().request();
        handle.future().cancel(true);
        return firstRequest; // KHÔNG đồng nghĩa worker đã dừng
    }

    private void run(String jobId, CancellationToken token) {
        for (int part = 0; part < 10; part++) {
            if (token.isCancellationRequested()
                    || Thread.currentThread().isInterrupted()) {
                return;
            }
            processPart(jobId, part);
        }
    }

    private void processPart(String jobId, int part) {
        System.out.println("Processing " + jobId + " part " + part);
    }

    public record JobHandle(
            Future<?> future, CancellationToken token) { }

    public static final class CancellationToken {
        private final AtomicBoolean requested = new AtomicBoolean();

        public boolean request() {
            return requested.compareAndSet(false, true);
        }

        public boolean isCancellationRequested() {
            return requested.get();
        }
    }
}
~~~

Đây là **một Spring-managed service**, cần Spring dependencies và Spring ApplicationContext. Lớp `CancellationToken` lồng trong `CancellableJobDispatcher` nên **không** dùng đồng thời với tệp `CancellationToken.java` độc lập ở phần trước nếu ứng dụng không chủ đích có hai loại token. Ví dụ tự chứa các class cần thiết để làm rõ API.

Quan trọng:

- `submit` trả handle liên kết với task được giao cho Spring executor. Khi đó có thể thử hủy cả trước và sau khi task bắt đầu.
- `requestCancellation` đặt token trước rồi gọi `future.cancel(true)`. Điều này vừa hỗ trợ cooperative checks vừa thử interrupt đối với underlying Future có hỗ trợ.
- Giá trị trả về của method là **“đây có phải yêu cầu hủy đầu tiên”**, không phải trạng thái `STOPPED`.
- Nếu thread đã bắt đầu `processPart` trước khi thấy token, một phần việc vẫn có thể chạy sau yêu cầu hủy. Với I/O cần timeout/cancel API riêng.
- Trả handle `Future` qua các ranh giới HTTP hoặc serialization không thích hợp; trong production nên lưu một **job ID** và metadata bền vững, còn Future/token chỉ là handle nội bộ trong một JVM.

### Không nhầm rejection và cancellation

Với `AbortPolicy`, nếu executor bão hòa hoặc shutdown khi `submit`, Spring `ThreadPoolTaskExecutor` có thể ném `TaskRejectedException`. **Đây là tác vụ không được chấp nhận**, không phải một Future đã chạy và sau đó bị hủy. Caller phải có đường xử lý overload: phản hồi HTTP phù hợp, queue bền vững hoặc retry có backoff và idempotency.

Nếu dùng `CallerRunsPolicy`, task có thể chạy ngay trên thread gọi khi pool bão hòa; khi đó `submit` có thể chờ đến lúc body hoàn tất rồi mới trả Future. Việc “hủy ngay sau khi submit” có thể đến quá muộn. Chính sách từ chối là một phần của hợp đồng hủy.

## 11. @Async và cancellation: đừng đồng nhất wrapper với worker

Một method `@Async` có thể trả `Future<T>` hoặc `CompletableFuture<T>`. Spring proxy tạo handle cho caller; target method thường tính giá trị trong worker rồi trả một `Future` tạm phù hợp chữ ký:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncReportService {
    @Async("cancellableExecutor")
    public CompletableFuture<String> render(
            String reportId, CancellableJobDispatcher.CancellationToken token) {
        for (int part = 0; part < 10; part++) {
            if (token.isCancellationRequested()
                    || Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Cancellation requested");
            }
            renderPart(reportId, part);
        }
        return CompletableFuture.completedFuture("done:" + reportId);
    }

    private void renderPart(String reportId, int part) {
        System.out.println("Rendering " + reportId + " part " + part);
    }
}
~~~

Đây là **Spring service** cần lớp token từ ví dụ dispatcher, executor bean và `@EnableAsync` ở cấu hình ứng dụng. Nó không phải một chương trình Java độc lập. Phần `completedFuture` bên trong method là Future tạm ở target; caller qua proxy nhận một handle biểu diễn việc xử lý async.

Nếu caller chỉ làm:

~~~java
CompletableFuture<String> future = reportService.render(id, token);
future.cancel(true);
~~~

thì `CompletableFuture.cancel(true)` tự nó **không phải cơ chế đáng tin để interrupt work**. Worker có thể vẫn chạy, tùy cơ chế proxy/wrapper và code bên trong. Vì thế nếu muốn dừng nghiệp vụ, gọi `token.request()` và đảm bảo method thường xuyên kiểm tra token. Exception kiểu `CancellationException` trong body có thể làm future của proxy hoàn thành exceptionally; **đừng khẳng định `future.isCancelled()==true` trong mọi biến thể chỉ vì body ném loại exception này**. Phải kiểm tra hợp đồng Future và cách Spring/Executor tạo wrapper.

### Tại sao @Async void khó hủy theo lời gọi?

Với `@Async void`, caller không có Future handle nên không thể gọi `cancel` trực tiếp trên kết quả method. Nếu cần hỗ trợ hủy, hãy cung cấp API riêng theo job ID/token, hoặc đổi thiết kế trả Future/công việc bền vững. `AsyncUncaughtExceptionHandler` là đường xử lý exception không bắt của `void`; nó không thay thế state machine cho hủy.

### Self-invocation và cancellation

Trong proxy mode mặc định, `this.render(...)` bên trong cùng bean bỏ qua Spring proxy. Khi đó body chạy trực tiếp trên thread caller, bất kể annotation. Một test cancellation có thể kết luận sai nếu không đảm bảo lời gọi đi qua Spring-managed proxy. Chọn executor và kiểm tra thread identity, đặc biệt khi có policy `CallerRunsPolicy`.

## 12. Cancellation và timeout: hai điều kiện riêng

`get(timeout, unit)` chỉ đặt giới hạn thời gian **cho thread đang chờ kết quả**. Khi `TimeoutException` xảy ra, tác vụ có thể vẫn chạy.

~~~java
Future<?> future = executor.submit(this::doWork);

try {
    future.get(2, TimeUnit.SECONDS);
} catch (TimeoutException ex) {
    boolean cancelRequested = future.cancel(true);
    recordTimeout(cancelRequested);
}
~~~

Đây là **fragment tích hợp**, cần imports `Future`, `TimeoutException`, `TimeUnit` và các method do ứng dụng định nghĩa; code thực cũng phải xử lý `InterruptedException` và `ExecutionException`. `cancel(true)` sau timeout là một **quyết định nghiệp vụ riêng**. Đôi khi kết quả đến quá muộn cho request nhưng tác vụ nền vẫn nên hoàn tất, ví dụ ghi audit; khi đó không nhất thiết cancel.

Với `CompletableFuture`:

- `orTimeout(duration, unit)` có thể hoàn thành **chính CompletableFuture** exceptionally bằng `TimeoutException` nếu chưa xong.
- `completeOnTimeout(fallback, duration, unit)` có thể hoàn thành Future đó bằng giá trị fallback.
- Cả hai **không tự ngắt công việc đang chạy**; background work vẫn có thể tiếp tục tác động lên database hoặc service ngoài.
- Nếu retry sau timeout, phải có idempotency vì lần thực thi đầu có thể chưa dừng.

Cần phân biệt ba deadline: deadline của request HTTP, timeout của lời gọi I/O trong worker, và deadline tổng của job. Chúng cần liên hệ qua một thiết kế chủ động; đơn giản gọi `future.get(2, ...)` không tạo tự động timeout ở mọi tầng.

## 13. Interrupt trong blocking API, I/O và critical section

### Blocking interruptible

Các API như `CountDownLatch.await`, `BlockingQueue.take`, `Thread.sleep` và nhiều thao tác chờ lock interruptible hỗ trợ `InterruptedException`. Khi nhận interrupt, thường nên:

1. Ghi nhận yêu cầu dừng ở mức cần thiết.
2. Giải phóng tài nguyên local trong `finally`.
3. Khôi phục cờ interrupt nếu không thể truyền `InterruptedException` cho tầng trên.
4. Thoát khỏi công việc càng sớm càng phù hợp với tính nhất quán dữ liệu.

### CPU-bound

Với phép tính dài:

~~~java
while (hasMoreWork()) {
    if (Thread.currentThread().isInterrupted()
            || token.isCancellationRequested()) {
        break;
    }
    computeNextChunk();
}
~~~

Các hàm/biến trong ví dụ là thành phần ứng dụng. Nếu `computeNextChunk()` là vòng tính toán 30 giây không kiểm tra token, độ trễ phản hồi yêu cầu cancel có thể 30 giây hoặc hơn. Chia workload theo checkpoint là cách kiểm soát cancellation latency.

### I/O và tài nguyên bên ngoài

Không phải mọi thao tác I/O đều dừng khi Java thread bị interrupt. Một HTTP client có thể có API cancel request riêng; JDBC driver có `Statement.cancel()` với hành vi tùy driver/database; socket có timeout hoặc cần đóng channel. Không được lấy `cancel(true)` làm bằng chứng một query DB đã rollback. Gắn timeout phù hợp lên HTTP/JDBC, kiểm tra ràng buộc transaction, và có bù trừ/idempotency cho side effect đã commit.

### Critical section

Nếu task đang giữ lock hoặc đang commit, hủy không có nghĩa được phép dừng ở bất kỳ instruction nào. Thiết kế điểm kiểm tra ở **ranh giới an toàn** giữa các bước. Nếu một đơn vị công việc đã ghi partial data, cần transaction, checkpoint hoặc compensation để trạng thái sau hủy không bị dở dang.

## 14. Shutdown và hủy là hai khái niệm khác

`ExecutorService.shutdown()` ngăn nhận tác vụ mới và cho phép các tác vụ đã được submit tiếp tục chạy theo hợp đồng shutdown thông thường. Nó **không đồng nghĩa hủy toàn bộ task đang chạy**.

`shutdownNow()` cố gắng dừng tác vụ đang thực thi qua interruption, ngăn xử lý tác vụ chờ và trả danh sách những `Runnable` chưa bắt đầu. Đây cũng là **best effort**: nếu task bỏ qua interrupt, nó có thể không dừng. Danh sách trả về là các wrapper Runnable, không nhất thiết là chính object nghiệp vụ gốc.

Một cấu trúc shutdown an toàn khi tự quản lý executor:

~~~java
executor.shutdown();
try {
    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
} catch (InterruptedException ex) {
    executor.shutdownNow();
    Thread.currentThread().interrupt();
}
~~~

Đây là **đoạn Java minh họa** trong method có `executor` kiểu `ExecutorService`, không phải class độc lập. Nếu Spring quản lý `ThreadPoolTaskExecutor` bean dùng chung, để ApplicationContext quản lý lifecycle theo cấu hình; không tự gọi shutdown ngay khi một request kết thúc.

Với job bắt buộc không mất, trước lúc shutdown cần drain hoặc lưu trạng thái bền vững, vì pending work trong queue RAM có thể mất khi JVM dừng.

## 15. Thiết kế job cancellation API đáng tin

Nếu client gửi `POST /jobs/{id}/cancel`, nên diễn đạt kết quả hủy một cách trung thực. Một sơ đồ trạng thái có thể dùng:

~~~text
QUEUED ----- claim/start -----> RUNNING ------> SUCCEEDED
   |                                |               ^
   | cancel trước claim             | cancel request |
   v                                v               |
CANCELLED_BEFORE_START       CANCEL_REQUESTED       |
                                   |               |
                               worker xác nhận      |
                                   |               |
                            STOPPED / CANCELLED     |
                                                   |
                                có thể hoàn thành trước
                                khi thấy yêu cầu hủy
~~~

Các trạng thái phải được thiết kế để tránh cả “CANCELLED và SUCCEEDED” cùng được ghi như kết quả cuối trong job store. Với yêu cầu hủy đến muộn, API có thể trả `ALREADY_COMPLETED` hoặc `CANCELLATION_NOT_GUARANTEED` thay vì cố ghi đè thành công thành cancelled.

### Gợi ý contract

| API / trạng thái | Ý nghĩa |
| --- | --- |
| `202 Accepted` khi yêu cầu cancel | Hệ thống đã ghi nhận yêu cầu hủy, chưa khẳng định worker dừng |
| `QUEUED -> CANCELLED` bằng CAS/transaction | Cancel thắng race với worker claim; chưa bắt đầu side effect theo protocol |
| `RUNNING -> CANCEL_REQUESTED` | Worker phải kiểm tra và hợp tác dừng |
| `RUNNING -> SUCCEEDED` | Worker hoàn thành trước hoặc trong cuộc đua với yêu cầu |
| `CANCEL_REQUESTED -> STOPPED` | Worker xác nhận đã dừng và cleanup |
| `CANCEL_REQUESTED -> FAILED` | Worker dừng do lỗi; cần phân loại nguyên nhân |
| `ALREADY_COMPLETED` | Không thể hủy công việc đã chốt kết quả |

`202` chỉ là ví dụ API cho asynchronous cancellation. Cách chọn HTTP status thật phụ thuộc hợp đồng sản phẩm, authentication và cơ chế lưu bền vững. Đừng lộ job ID của người khác hoặc cho một caller bất kỳ hủy job không thuộc quyền.

### Atomicity và idempotency

Một cancellation endpoint thường cần idempotent: gọi nhiều lần không tạo trạng thái trái ngược. Dùng version/CAS cho state transition và gắn request ID/correlation ID để quan sát. Nếu task có side effect không rollback được (email đã gửi, chuyển tiền đã thực hiện), cancellation chỉ có thể ngăn **bước tiếp theo**, không thể tự rút lại bước đã hoàn tất; cần bù trừ hoặc hợp đồng “không hủy được sau mốc X”.

## 16. Quan sát cancellation đúng cách

Không chỉ đếm `Future.isCancelled()`. Với workload lớn, nên quan sát:

| Metric | Giải thích |
| --- | --- |
| `cancel_requests_total` | Số yêu cầu người dùng/hệ thống muốn hủy |
| `cancel_before_start_total` | Số job hủy thắng trước khi worker claim |
| `cancel_running_requests_total` | Số yêu cầu hủy khi job đã chạy |
| `cancel_acknowledged_total` | Worker đã quan sát yêu cầu và bước vào cleanup |
| `cancel_stopped_total` | Job thực sự dừng sau cleanup |
| `cancel_too_late_total` | Job đã hoàn thành trước khi cancel thắng |
| `cancellation_latency` | Thời gian từ yêu cầu đến xác nhận dừng |
| `interrupted_tasks_total` | Số body thực sự quan sát interrupt, không phải số lần gọi cancel(true) |
| `queued_cancelled_task_count` | Số task bị hủy nhưng còn hiện diện vật lý trong queue nếu có thể đo |
| `partial_side_effects` | Số job bị hủy sau khi đã ghi một phần side effect; cần reconciliation |

Metric phải tránh đếm trùng: một request có thể gửi cancel nhiều lần, nhiều caller cùng gửi, hoặc Future bị cancel vì timeout. Dùng job ID, request ID và phân biệt **requested**, **handle cancelled**, **worker stopped**. Logging thread name hữu ích cho điều tra nhưng không thay thế một trạng thái bền vững.

### Những số đo dễ đánh lừa

- `getQueue().size()` là ảnh chụp tức thời; có thể bao gồm wrapper đã cancel nhưng chưa được dọn.
- `getActiveCount()` là ước lượng, không cho biết bao nhiêu worker đã hợp tác hủy.
- `Future.isDone()` là true cả khi canceled; không phải metric job success.
- `cancel(true)` trả true không phải “interrupted_tasks_total”.
- `CompletableFuture.isCancelled()` có thể true trong khi computation đứng sau handle vẫn chạy.

## 17. Những lỗi phổ biến

1. **Đọc `isDone()==true` và kết luận job thành công.** Future có thể hoàn thành lỗi hoặc hủy.
2. **Đọc `isCancelled()==true` và kết luận worker đã dừng.** Body có thể vẫn đang cleanup hoặc chạy tiếp.
3. **Dùng `cancel(false)` trên task đang chạy rồi kỳ vọng dừng body.** Nó không yêu cầu interruption.
4. **Dùng `cancel(true)` như lệnh cưỡng bức dừng thread.** Interruption cần hợp tác.
5. **Nuốt `InterruptedException` rồi chạy tiếp vô điều kiện.** Mất tín hiệu dừng.
6. **Quên khôi phục interrupt khi không thể truyền exception.** Thư viện/tầng trên có thể không biết đã bị ngắt.
7. **Gọi `Thread.interrupted()` để “kiểm tra” mà không biết nó xóa flag.** Tầng sau có thể bỏ lỡ tín hiệu.
8. **Chỉ check token một lần trước vòng xử lý dài.** Cancel sau check vẫn có thể tới và không được nhìn thấy.
9. **Tin vào `getQueue().contains(task)` như bằng chứng task chưa chạy.** Worker có thể lấy task ngay sau đó.
10. **Nhầm xóa wrapper khỏi queue với rollback side effect.** Hai quá trình độc lập.
11. **Giả định cancel luôn xóa entry queue ngay.** `ThreadPoolExecutor` có thể giữ FutureTask cancelled đến khi dequeue hoặc purge.
12. **Gọi `setRemoveOnCancelPolicy` trên ThreadPoolExecutor thường.** Setter đó thuộc ScheduledThreadPoolExecutor.
13. **Gọi `CompletableFuture.cancel(true)` rồi mặc định worker bị interrupt.** API Java chuẩn không dùng tham số đó để điều khiển interruption.
14. **Dùng `orTimeout` và tưởng nó ngắt CPU/I/O phía sau.** Nó thay đổi completion, không bảo đảm dừng computation.
15. **Dùng `@Async void` rồi tìm Future để cancel.** Cần giao diện job/token khác hoặc kiểu trả về có handle.
16. **Self-invocation bỏ qua proxy `@Async`.** Body chạy trên caller, làm test timing sai.
17. **Không xử lý rejection của executor.** Task bị từ chối không phải task được nhận rồi hủy.
18. **Dùng `CallerRunsPolicy` mà tưởng submit luôn trả về ngay.** Body có thể chạy inline khi bão hòa.
19. **Cancel task sau khi nó đã commit external side effect rồi báo đã rollback.** Cần compensation, không phải chỉ Future.
20. **Không có cleanup trong finally.** Tài nguyên/socket/lock có thể bị giữ khi interruption xảy ra.
21. **Chỉ dùng AtomicBoolean trong một service nhiều replica.** State cancel không được đồng bộ liên JVM.
22. **Đếm mọi Future cancelled là job cancelled thành công.** Có thể báo cáo sai mức độ thực thi.
23. **Test bằng `Thread.sleep()` để đoán task đã start.** Dùng latch/barrier và timeout.
24. **Không giới hạn thời gian shutdown.** Worker không hợp tác có thể giữ process hoặc tài nguyên.
25. **Gọi retry ngay khi cancel trên side effect không idempotent.** Công việc cũ có thể vẫn chạy.

## 18. Quy trình kiểm thử đáng tin cậy

### Kiểm thử trước khi bắt đầu

1. Dùng single-thread executor, giữ worker đầu bằng latch.
2. Đợi latch xác nhận worker đã bắt đầu và bị giữ.
3. Submit task thứ hai; lúc này task thứ hai chắc chắn chưa có worker rảnh.
4. Gọi `cancel(false)` trên Future thứ hai; assert trạng thái hủy.
5. Thả worker đầu, submit marker barrier và chờ marker hoàn thành.
6. Assert body của task thứ hai **chưa từng chạy**. Không dựa vào `Thread.sleep()`.

### Kiểm thử khi đang chạy

1. Task phát tín hiệu `entered` khi đã vào body.
2. Task chờ một latch không được thả, sử dụng API interruptible.
3. Main đợi `entered`, gọi `cancel(true)`.
4. Task bắt `InterruptedException`, ghi nhận và thoát trong `finally`.
5. Main chờ `exited`, assert interrupt thực sự đã được body quan sát và Future bị hủy.

### Kiểm thử running cancel(false)

1. Đưa task vào trạng thái đang chạy và đang chờ latch.
2. Gọi `cancel(false)`, xác minh Future canceled.
3. Thả latch thủ công, xác minh body **vẫn có thể chạy đến sau vùng chờ**.
4. Điều này giúp team hiểu rằng “future canceled” không đồng nhất với “worker stopped”.

### Kiểm thử Spring integration

- Lấy `ThreadPoolTaskExecutor` do Spring quản lý, không quên lifecycle của ApplicationContext.
- Test đường `submit` và đường `@Async` riêng nếu hệ thống có cả hai; wrapper semantics có thể khác.
- Test bằng handle/ID thật và thread context phù hợp, không chỉ gọi trực tiếp `new Service()`.
- Dựng queue bounded và latch để tái hiện rejection và queued cancellation.
- Kiểm tra timeout/cancellation state trong job table; assert side effect thực sự không xảy ra khi cancel thắng race theo contract.
- Trong mọi test có worker bị chặn, giải phóng latch trong `finally`, giới hạn thời gian chờ và đóng executor để tránh CI treo.
- Test kết hợp shutdown/rolling deployment để biết task đang chờ được drain, bị bỏ hay được ghi bền vững.

## 19. Khi nào cần cơ chế mạnh hơn Future.cancel?

| Bối cảnh | Cơ chế nên dùng |
| --- | --- |
| Task ngắn, nội bộ một JVM, có thể bỏ qua nếu chưa chạy | Future cancellation có thể đủ |
| Task dài, có thể dừng giữa các chunk | Future interrupt + cancellation token + checkpoint |
| Task dùng HTTP/DB blocking | Timeout/cancel API của thư viện + token; không chỉ interrupt |
| Task đang chạy có thể tạo side effect quan trọng | Transaction/state machine/idempotency và checkpoint an toàn |
| Nhiều replica, cần hủy qua node khác | Durable job store và trạng thái cancel có thể quan sát |
| Cần ngăn tuyệt đối một task bắt đầu sau hủy | Atomic state transition giữa worker claim và cancel |
| Job đã hoàn thành rồi client mới hủy | Trả trạng thái quá muộn/already completed |
| Nhiều worker chia sẻ một công việc | Cancellation protocol cho từng child task và tổng hợp completion |
| Chỉ cần UI ngừng chờ response | Có thể timeout request mà không hủy job nếu hợp đồng nghiệp vụ yêu cầu |
| Cần bảo đảm job không mất khi restart | Queue bền vững, retry và outbox/idempotency, độc lập với Future |

Đặt ưu tiên theo nghĩa của *cancel*: “không trả kết quả cho tôi nữa”, “không được bắt đầu job”, “dừng sớm nếu có thể”, hay “hủy toàn bộ hiệu ứng nghiệp vụ”. Bốn yêu cầu này khác nhau đáng kể và không thể giải quyết hết bằng một lời gọi `future.cancel(true)`.

## 20. Câu hỏi tự kiểm tra

1. Tại sao một task vừa được `submit` có thể chưa bắt đầu dù đã có Future?
2. Trong tình huống worker bận và task B còn nằm queue, hủy `FutureTask` của B thành công có làm body B chạy sau đó không?
3. Vì sao sau khi `cancel(false)` một task đang chạy, `future.isCancelled()==true` nhưng body vẫn có thể sinh side effect?
4. `cancel(true)` có chắc worker dừng ngay không? Khi nào interruption thực sự hữu ích?
5. Khi bắt `InterruptedException` mà không ném tiếp, vì sao thường nên gọi `Thread.currentThread().interrupt()`?
6. `Thread.interrupted()` khác `Thread.currentThread().isInterrupted()` ở điểm nào?
7. Tại sao đọc `getQueue().size()` hoặc `contains(task)` không thể làm hàng rào đảm bảo task chưa chạy?
8. `CompletableFuture.cancel(true)` có tự interrupt lambda chạy trong `supplyAsync` không?
9. `isDone()` có phân biệt success, failure và cancellation không?
10. `get(timeout)` timeout có tự cancel hoặc ngừng task không?
11. Với Spring `@Async CompletableFuture`, bạn cần thiết kế gì nếu người dùng yêu cầu dừng công việc thực sự?
12. Trong job store nhiều replica, làm sao phân biệt `QUEUED -> CANCELLED` với `RUNNING -> CANCEL_REQUESTED`?
13. Nếu thao tác chuyển tiền đã commit, `Future.cancel(true)` có rollback được không? Điều gì phải được thiết kế thêm?
14. Một cancellation endpoint trả “đã tiếp nhận yêu cầu” khác “worker đã ngừng” ở điểm nào?
15. Trong kiểm thử, làm thế nào chứng minh body chưa chạy mà không dùng sleep ngẫu nhiên?
16. Khi shutdown, `shutdownNow()` có đảm bảo mọi I/O đang chạy dừng tức thì không?

## 21. Tài liệu tham khảo chính thức

- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java SE 17 — FutureTask](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/FutureTask.html)
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java SE 17 — Thread interruption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html)
- [Java SE 17 — ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework — @Async](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/annotation/Async.html)

**Ghi nhớ:** hủy khi task **chưa bắt đầu** có thể ngăn body thực thi nếu FutureTask được cancel thành công. Hủy khi task **đang chạy** chủ yếu là yêu cầu dừng hợp tác: `cancel(false)` không gửi interrupt, `cancel(true)` chỉ thử interrupt, còn `CompletableFuture.cancel(true)` không tự điều khiển việc ngắt computation. Muốn bảo đảm nghiệp vụ, phải xác minh **worker thực sự dừng** và quản lý **job state, side effect, deadline** độc lập với trạng thái Future.

</details>

- [Quay lại đầu trang](#back-to-top)
