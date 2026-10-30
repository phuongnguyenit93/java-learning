<a id="back-to-top"></a>

# Quá tải và từ chối

## Menu
- [Những tình huống bộ thực thi từ chối tác vụ](#overload-rejection-001)
- [RejectedExecutionHandler và TaskRejectedException](#overload-rejection-002)
- [AbortPolicy báo lỗi tại điểm gửi tác vụ](#overload-rejection-003)
- [CallerRunsPolicy chuyển công việc sang luồng gọi và thay đổi độ trễ](#overload-rejection-004)
- [DiscardPolicy, DiscardOldestPolicy và nguy cơ mất công việc](#overload-rejection-005)
- [Không để Future chờ vô hạn do chính sách loại bỏ không hoàn tất tác vụ](#overload-rejection-006)
- [Chọn trả lỗi, hạn chế nhận thêm hoặc lưu công việc bền vững](#overload-rejection-007)
- [Không lặp gửi lại tức thời làm quá tải nghiêm trọng hơn](#overload-rejection-008)
- [Thực nghiệm nhóm luồng và hàng đợi cùng đạt giới hạn](#overload-rejection-009)

## <a id="overload-rejection-001">Những tình huống bộ thực thi từ chối tác vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Những tình huống bộ thực thi từ chối tác vụ trong Spring Framework

Trong ứng dụng Spring, công việc bất đồng bộ thường được đưa vào `TaskExecutor` hoặc một `ThreadPoolTaskExecutor`. Việc gọi `execute(...)`, `submit(...)` hoặc phương thức `@Async` **không tự bảo đảm task được nhận, được chạy hay hoàn tất thành công**. Khi executor quá tải hoặc đang đóng, một lời gọi giao việc mới có thể bị **từ chối (task rejection)**. Hiểu nguyên nhân từ chối giúp tránh mất việc âm thầm, backlog kéo dài và retry làm hệ thống càng quá tải.

Bài học này tập trung trả lời: **khi nào executor từ chối task, nhận biết ở đâu, Spring biểu diễn lỗi thế nào, và cần phản ứng ra sao theo yêu cầu nghiệp vụ**. Các ví dụ dùng Java 17+ và Spring Framework 6.x/Spring Boot 3.x. Những thuộc tính cụ thể cần đối chiếu với phiên bản framework ứng dụng đang chạy.

## 1. “Từ chối tác vụ” chính xác là gì?

Một executor **từ chối** khi có yêu cầu giao một task nhưng executor **không chấp nhận task đó để thực thi theo hợp đồng hiện tại**. Ở mức Java, `ThreadPoolExecutor.execute(Runnable)` chuyển yêu cầu không thể tiếp nhận tới `RejectedExecutionHandler`. Chính handler quyết định ném `RejectedExecutionException`, chạy ngay trên caller hoặc bỏ task theo chính sách.

Ba mốc thường bị nhầm lẫn:

```text
Producer / Caller
       |
       | submit(task)
       v
 [Executor xét nhận task] -- KHÔNG NHẬN --> Rejection policy
       |
       | ĐÃ NHẬN
       v
 [Đợi queue hoặc worker chạy]
       |
       +---> Thành công
       +---> Exception khi đang chạy
       +---> Bị hủy/gián đoạn
       +---> Chưa chạy trước lúc tiến trình dừng/crash
```

- **Accepted / đã nhận:** pool đã tiếp nhận để xếp hàng hoặc chạy. Chưa đồng nghĩa task đã hoàn thành.
- **Rejected / bị từ chối:** task không được nhận theo chính sách tiếp nhận thông thường. Với `AbortPolicy`, caller nhận exception ngay tại lúc giao việc.
- **Execution failure / lỗi lúc chạy:** task đã được nhận, nhưng code nghiệp vụ ném lỗi sau đó. Với `submit`, lỗi thường nằm trong `Future`; với `CompletableFuture`, lỗi đi qua cơ chế hoàn tất ngoại lệ.
- **Cancellation, timeout và shutdown:** là các trạng thái/vòng đời khác; có thể liên quan tới từ chối, nhưng không phải mọi timeout hay interrupt đều là rejection.
- **Discard policy:** vẫn là tình huống handler quyết định **bỏ task** sau khi không nhận được, nhưng caller có thể không thấy exception. Vì vậy “không có lỗi” không đồng nghĩa “đã nhận và sẽ chạy”.

Trong Spring, `ThreadPoolTaskExecutor` triển khai `TaskExecutor`; với chính sách ném lỗi, lời gọi có thể nhận **`TaskRejectedException`** của Spring, một subtype của `RejectedExecutionException`. Cách lỗi nổi lên khi dùng `@Async` phụ thuộc đường gọi và handler; không thể suy đoán từ việc method có annotation.

## 2. Tình huống 1: pool và hàng đợi đều bão hòa

Đây là nguyên nhân phổ biến khi chạy nền dưới tải cao. Pool có giới hạn `corePoolSize`, `maxPoolSize` và `queueCapacity`. Với `ThreadPoolTaskExecutor` bao quanh JDK `ThreadPoolExecutor`, thuật toán tổng quát khi task mới đến là:

1. Nếu số worker đang tồn tại thấp hơn **core**, tạo thêm worker cho task.
2. Khi đã đạt core, thử đưa task vào **queue**.
3. Nếu queue không còn chỗ, thử tăng worker tới **max**.
4. Nếu queue không nhận thêm và đã đạt max, chuyển task sang **rejection handler**.

Ví dụ:

```java
executor.setCorePoolSize(2);
executor.setMaxPoolSize(4);
executor.setQueueCapacity(2);
```

Giả định **mọi task đang chạy đều bị giữ lại**, không có worker rảnh hoặc task nào kịp kết thúc:

| Task đến | Pool/queue tại lúc xét nhận | Hành động |
| --- | --- | --- |
| Task 1 | Worker chưa đạt core | Tạo worker 1 |
| Task 2 | Worker chưa đạt core | Tạo worker 2 |
| Task 3 | Đã đủ core, queue còn chỗ | Xếp vào queue (1/2) |
| Task 4 | Đã đủ core, queue còn chỗ | Xếp vào queue (2/2) |
| Task 5 | Queue đầy, worker chưa đạt max | Tạo worker 3 |
| Task 6 | Queue đầy, worker chưa đạt max | Tạo worker 4 |
| Task 7 | Queue đầy, đã đủ 4 worker | **Rejection handler** |

Sơ đồ:

```text
core=2, max=4, queueCapacity=2

            2 core workers           queue=2          2 extra workers
Task 1,2 -> [W1][W2]    Task 3,4 -> [Q3][Q4]   Task 5,6 -> [W3][W4]
Task 7  --------------------------------------------------> REJECT
```

**Điều kiện về thời gian rất quan trọng:** đây là trường hợp **cả sáu task đã nhận đều chưa hoàn thành**. Nếu worker xử lý xong một việc và lấy task khỏi queue trước khi task 7 đến, task 7 có thể được nhận. Không tính mức từ chối đơn giản bằng số lượng request trong cả một phút; phải nhìn **mức độ đồng thời và queue tại thời điểm submit**.

### 2.1. Vì sao chỉ đặt `maxPoolSize` lớn chưa đủ?

Nếu `core=2, max=20, queueCapacity=10_000`, sau khi đủ core, các task mới thường chờ trong queue. Pool **không tăng vượt core chỉ vì có nhiều task đang đợi**; nó thử queue trước khi tạo worker vượt core. Khi queue lớn, hệ thống có thể chậm khủng khiếp nhưng chưa hề phát sinh rejection do capacity.

Với queue gần như không giới hạn, nếu tốc độ nạp việc luôn cao hơn tốc độ xử lý, backlog tăng và có thể dẫn tới `OutOfMemoryError`. Đây là **lỗi cạn bộ nhớ**, không nên mô tả như một rejection bình thường có thể xử lý trơn tru.

### 2.2. Tình huống bão hòa do blocking I/O

Một thread pool có thể đầy ngay cả khi CPU rất thấp. Ví dụ có 8 worker cùng chờ database connection hoặc HTTP API chậm; các task mới nằm trong queue cho tới khi đầy. Tăng thêm thread lúc đó có thể làm DB/API càng quá tải.

Chẩn đoán phải xem **active worker, queue size, queue wait, downstream latency, connection pool** và số lần từ chối, không chỉ CPU.

## 3. Tình huống 2: executor đã shutdown hoặc đang chuyển sang trạng thái đóng

JDK `ThreadPoolExecutor` từ chối task mới sau khi `shutdown()` hoặc `shutdownNow()`. `shutdown()` bắt đầu quá trình đóng có trật tự: có thể tiếp tục xử lý task đã nhận, nhưng **không nhận task mới**. `shutdownNow()` cố ngừng task đang chạy bằng interrupt và rút các task chờ khỏi queue theo cơ chế best effort; không thể ép mọi tác vụ đang chặn I/O dừng ngay.

Ví dụ Java:

```java
ExecutorService pool = Executors.newFixedThreadPool(2);
pool.shutdown();
pool.execute(() -> System.out.println("work")); // Thường ném RejectedExecutionException
```

Đây là **fragment** minh họa với default `AbortPolicy` của executor JDK. Khi dùng custom rejection handler như `CallerRunsPolicy` hoặc `DiscardPolicy`, task gửi sau shutdown có thể bị **bỏ mà không ném exception**. Đừng khẳng định mọi policy đều ném cùng một lỗi.

Trong Spring, tình huống này có thể xảy ra khi:

- Application context đang đóng nhưng một HTTP request, listener hoặc scheduler vẫn cố giao việc mới.
- Một bean đang trong `@PreDestroy` lại gọi service `@Async`, trong khi executor đã vào giai đoạn shutdown.
- Một background callback giữ tham chiếu tới executor và submit sau khi context dừng.
- Ứng dụng chủ động gọi `shutdown()` trên một executor dùng chung quá sớm.
- Có race giữa thời điểm kiểm tra trạng thái `isShutdown()` và lúc submit: kiểm tra thấy chưa shutdown **không bảo đảm** một thread khác không đóng executor ngay sau đó.

**Cách xử lý:** trước hết phân biệt đây là shutdown hợp lệ hay lỗi thứ tự lifecycle. Không retry liên tục vào executor đã đóng; cần stop producer/ingress, phối hợp graceful shutdown, hoặc chuyển giao công việc chưa xử lý sang hệ thống bền vững nếu nghiệp vụ yêu cầu.

## 4. Tình huống 3: `queueCapacity=0` và không còn worker nhận việc

`ThreadPoolTaskExecutor#setQueueCapacity(0)` dùng `SynchronousQueue`, một cơ chế **bàn giao trực tiếp** thay vì lưu task vào hàng chờ có sức chứa dương.

Ví dụ:

```java
executor.setCorePoolSize(1);
executor.setMaxPoolSize(3);
executor.setQueueCapacity(0);
```

Nếu ba worker đang thực thi các task chưa hoàn thành, task thứ tư **không có chỗ để chờ** và pool không thể mở rộng quá max: xảy ra rejection theo handler.

```text
queueCapacity = 0
Task A -> Worker 1 bận
Task B -> Worker 2 bận
Task C -> Worker 3 bận
Task D -> Không có worker nhận trực tiếp -> REJECT
```

Khi có worker đang rảnh và sẵn sàng nhận, task mới vẫn có thể được nhận. `queueCapacity=0` không có nghĩa executor không làm việc; nó định nghĩa **không tích lũy backlog trong queue**. Nếu max đặt quá lớn, burst traffic có thể tạo rất nhiều platform threads và gây áp lực bộ nhớ/CPU; cần giới hạn rõ ràng.

## 5. Tình huống 4: burst, việc chậm và starvation tạo áp lực dồn

Capacity nhìn có vẻ đủ ở điều kiện bình thường vẫn có thể bão hòa bởi:

- **Burst traffic:** hàng nghìn task đến trong thời gian ngắn.
- **Downstream chậm:** mọi worker chờ JDBC, HTTP, tệp hoặc lock thay vì xử lý xong.
- **Tác vụ chạy rất lâu hoặc kẹt vô hạn:** worker không quay lại queue để lấy task khác.
- **Một pool dùng chung cho nhiều loại tải:** tác vụ gửi email chậm chiếm hết worker của báo cáo cần SLA ngắn.
- **Task phụ thuộc nhau:** task A chiếm worker và chờ Future của task B, nhưng B lại nằm trong cùng queue của pool đã đầy. Có thể xảy ra starvation deadlock mà không cần rejection ngay từ đầu.
- **Quá nhiều lần retry không giới hạn:** vừa gặp lỗi đã submit lại vào chính pool đang quá tải, làm queue và tốc độ đến tăng thêm.

Ví dụ producer tạo 200 task/giây, hệ thống chỉ xử lý ổn định 100 task/giây. Nếu vẫn nhận tất cả, backlog tăng xấp xỉ 100 task/giây trong mô hình đơn giản hóa. Queue 1.000 task có thể che giấu vấn đề khoảng 10 giây trước khi đầy; độ trễ của task nằm cuối queue đã tăng đáng kể từ trước khi bắt đầu reject.

**Tín hiệu rejection là hậu quả của áp lực tải**, không nhất thiết nguyên nhân gốc. Cần xác định bottleneck: CPU quota, thread, lock, DB pool, network timeout, downstream quota hay thiết kế producer.

## 6. Tình huống không nên nhầm thành rejection

| Hiện tượng | Có phải executor rejection? | Giải thích |
| --- | --- | --- |
| Task đã chạy nhưng ném `IllegalStateException` | Không | Lỗi nghiệp vụ/execution; thường quan sát qua Future/callback |
| Caller gọi `Future.get(1, SECONDS)` rồi timeout | Không tự động | Caller chờ quá hạn; task có thể vẫn chạy |
| Task bị `cancel(true)` | Không tự động | Yêu cầu hủy/interruption khác với từ chối lúc submit |
| Thread pool rất chậm do queue dài | Chưa chắc | Queue có thể chưa đầy, task vẫn được nhận |
| Queue không giới hạn dẫn đến hết bộ nhớ | Không phải tình huống reject thông thường | Cạn bộ nhớ cần xử lý capacity và admission trước đó |
| Đặt `core > max` và bean không khởi tạo được | Không | Lỗi cấu hình khi tạo pool, không phải task rejection |
| Gọi `@Async` nội bộ qua `this.method()` | Không | Self-invocation thường bỏ qua Spring proxy; method có thể chạy trực tiếp |
| Task gửi sau `shutdown()`, default `AbortPolicy` ném | Có | Executor đã đóng, không nhận task mới |
| Pool max đã bận, queue đầy, default `AbortPolicy` ném | Có | Executor bão hòa |
| `CallerRunsPolicy` chạy task trên caller | Là xử lý overload | Handler chọn thực thi trên caller thay vì ném |

Một custom `ThreadFactory` không thể tạo worker cũng có thể làm executor không tiến triển đúng cách; đây là một trường hợp tài nguyên/cấu hình bất thường, không nên đánh đồng với quy tắc bão hòa chuẩn. Phân biệt `OutOfMemoryError`, timeout, interruption và rejection để tránh retry sai.

## 7. Chính sách xử lý khi từ chối

JDK cung cấp bốn `RejectedExecutionHandler` thường gặp. Spring `ThreadPoolTaskExecutor` có thể nhận handler thông qua `setRejectedExecutionHandler(...)`.

| Handler | Nếu pool không thể nhận task | Ý nghĩa/rủi ro |
| --- | --- | --- |
| `ThreadPoolExecutor.AbortPolicy` | Ném `RejectedExecutionException` (Spring adapter thường chuyển thành `TaskRejectedException`) | Caller thấy rõ quá tải, có cơ hội trả lỗi hoặc retry có kiểm soát |
| `ThreadPoolExecutor.CallerRunsPolicy` | Nếu chưa shutdown, **thread submit chạy task ngay** | Tạo backpressure, nhưng có thể chặn HTTP request thread hoặc scheduler thread |
| `ThreadPoolExecutor.DiscardPolicy` | Bỏ task không có thông báo | Chỉ hợp với tác vụ thực sự chấp nhận mất; nguy cơ mất việc âm thầm |
| `ThreadPoolExecutor.DiscardOldestPolicy` | Nếu chưa shutdown, bỏ task đầu queue rồi thử submit lại | Có thể làm rơi việc cũ, phá thứ tự; rất khó bảo đảm completion |

**Đặc biệt khi shutdown:** `CallerRunsPolicy` và `DiscardOldestPolicy` có thể **bỏ task** thay vì chạy lại hoặc ném lỗi. Không dùng policy này rồi mặc nhiên tin rằng mọi lời gọi không ném lỗi đã hoàn tất.

Với `DiscardPolicy` hoặc `DiscardOldestPolicy`, một `Future` do `submit(...)` trả về có thể không bao giờ chuyển sang trạng thái completed nếu task bị bỏ nhưng Future không được cancel. Khi đó `get()` không timeout có thể chờ mãi. Vì vậy **chỉ dùng các policy bỏ task khi thiết kế đã chấp nhận mất việc và hiểu cách thông báo completion/cancellation**.

### 7.1. Khi nào cân nhắc `CallerRunsPolicy`?

Nếu producer nhanh hơn consumer, chuyển task quá tải về caller khiến caller bị chặn và giảm tốc độ nạp task. Nó là cơ chế backpressure đơn giản:

```text
Caller submit -> pool còn chỗ -> trả về nhanh
Caller submit -> pool bão hòa -> CallerRunsPolicy chạy ngay trên caller
Caller bận chạy task -> chưa tiếp tục submit các task khác
```

Tuy nhiên, nếu caller là HTTP request thread, response có thể chậm bất ngờ và timeout. Nếu caller là thread lập lịch, lần trigger tiếp theo có thể bị trễ. Với xử lý cần latency thấp và quy tắc SLA rõ ràng, **AbortPolicy + phản hồi overload** thường dễ kiểm soát hơn.

### 7.2. Nên dùng policy nào?

Không có policy phù hợp mọi nghiệp vụ. Có thể bắt đầu bằng default **AbortPolicy** để không che giấu overload, sau đó chọn hành vi theo khả năng mất việc, độ ưu tiên, giới hạn nguồn lực và giao thức gọi. Hệ thống cần đảm bảo thực thi sau restart phải dùng **persistent queue/job store** chứ không chỉ đổi rejection policy.

## 8. Java 17+ runnable: tái hiện saturation, shutdown và CallerRuns

Ví dụ JDK thuần dùng `CountDownLatch` để **giữ task đang chạy**, tạo điều kiện bão hòa xác định. Không cần Spring và không dựa vào `Thread.sleep(...)` để đoán lịch CPU.

Lưu toàn bộ chương trình vào **RejectionCasesDemo.java**:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RejectionCasesDemo {
    private static ThreadPoolExecutor newPool(
            RejectedExecutionHandler handler) {
        return new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), handler);
    }

    private static Runnable heldTask(
            CountDownLatch started, CountDownLatch release) {
        return () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor abort =
                newPool(new ThreadPoolExecutor.AbortPolicy());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<?> running =
                    abort.submit(heldTask(started, release));
            started.await();                 // Worker đang bận
            Future<?> queued = abort.submit(() -> {});

            try {
                abort.execute(() -> {});
                System.out.println("unexpected acceptance");
            } catch (RejectedExecutionException e) {
                System.out.println("rejected: saturated");
            }

            release.countDown();
            running.get();
            queued.get();
            System.out.println("accepted completed: 2");

            abort.shutdown();
            try {
                abort.execute(() -> {});
                System.out.println("unexpected after shutdown");
            } catch (RejectedExecutionException e) {
                System.out.println("rejected: shutdown");
            }
        } finally {
            release.countDown();
            abort.shutdownNow();
        }

        ThreadPoolExecutor caller =
                newPool(new ThreadPoolExecutor.CallerRunsPolicy());
        CountDownLatch started2 = new CountDownLatch(1);
        CountDownLatch release2 = new CountDownLatch(1);

        try {
            Future<?> running =
                    caller.submit(heldTask(started2, release2));
            started2.await();                // Worker đang bận
            Future<?> queued = caller.submit(() -> {});

            Thread submittingThread = Thread.currentThread();
            AtomicBoolean onCaller = new AtomicBoolean(false);
            caller.execute(() -> onCaller.set(
                    Thread.currentThread() == submittingThread));
            System.out.println("caller ran: " + onCaller.get());

            release2.countDown();
            running.get();
            queued.get();
        } finally {
            release2.countDown();
            caller.shutdownNow();
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac RejectionCasesDemo.java
java RejectionCasesDemo
```

**Stdout xác định với chương trình chạy bình thường:**

```text
rejected: saturated
accepted completed: 2
rejected: shutdown
caller ran: true
```

**Vì sao kết quả này xác định?**

1. `abort` chỉ có **một worker** và **một vị trí queue**. `started.await()` bảo đảm worker đã bắt đầu `heldTask` và đang chờ `release`.
2. `queued` chiếm vị trí hàng chờ; task thứ ba không còn worker/queue nên `AbortPolicy` ném `RejectedExecutionException` **ngay lúc `execute`**.
3. Sau `release.countDown()`, worker xử lý hết hai task đã nhận. `Future.get()` đợi hoàn tất trước khi in dòng `accepted completed: 2`.
4. `abort.shutdown()` ngừng nhận task mới; lệnh `execute` tiếp theo bị từ chối dù không còn task đang chạy.
5. Với `caller`, worker đang bị giữ và queue đã đầy; `CallerRunsPolicy` chạy task thứ ba ngay trên **main thread đang submit**, nên `caller ran: true`.
6. `finally` luôn giải phóng latch và yêu cầu đóng pool để tránh treo tiến trình nếu có lỗi trước khi cleanup.

Ví dụ này minh họa chính xác cơ chế của **JDK `ThreadPoolExecutor`**. Spring `ThreadPoolTaskExecutor` dùng pool JDK bên dưới nhưng còn có lifecycle và loại exception theo hợp đồng Spring.

## 9. Spring Boot: bắt rejection tại điểm giao việc `@Async`

Trong Spring Framework 6.x, có thể tạo `ThreadPoolTaskExecutor` với queue hữu hạn và handler tường minh, rồi dùng `@Async("notificationExecutor")`. Ví dụ dưới đây dùng **Spring Boot 3.x, Java 17+**, dependency `spring-boot-starter-web` và một application class.

Đặt toàn bộ mã vào **src/main/java/com/example/demo/RejectionApplication.java**:

```java
package com.example.demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableAsync
public class RejectionApplication {
    public static void main(String[] args) {
        SpringApplication.run(RejectionApplication.class, args);
    }

    @Bean("notificationExecutor")
    ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(2);
        executor.setThreadNamePrefix("notify-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}

@Service
class NotificationService {
    @Async("notificationExecutor")
    public CompletableFuture<String> sendAsync(String id) {
        try {
            Thread.sleep(500); // Chỉ giả lập blocking I/O
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Task interrupted", e);
        }
        return CompletableFuture.completedFuture("sent:" + id);
    }
}

@RestController
class NotificationController {
    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping("/notifications/{id}")
    public ResponseEntity<String> submit(@PathVariable String id) {
        try {
            CompletableFuture<String> future = service.sendAsync(id);

            future.whenComplete((value, error) -> {
                if (error != null) {
                    System.err.println("Failed " + id + ": " + error);
                } else {
                    System.out.println(value);
                }
            });

            return ResponseEntity.accepted().body("accepted:" + id);
        } catch (TaskRejectedException rejected) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("executor overloaded");
        }
    }
}
```

Chạy bằng plugin Spring Boot trong dự án, ví dụ `./gradlew bootRun` (trên Windows: `gradlew.bat bootRun`), rồi thử:

```bash
curl -i -X POST http://localhost:8080/notifications/N1
```

**Trường hợp nhận việc thành công**, response có HTTP **202 Accepted**, body:

```text
accepted:N1
```

**Trường hợp executor ném `TaskRejectedException` tại lúc submit**, controller đáp HTTP **503 Service Unavailable** với body:

```text
executor overloaded
```

Kết quả nào xảy ra tùy tải, thứ tự thread và thời điểm submit. **Một request đơn lẻ khi pool rảnh thường được chấp nhận**, không có cách suy ra chắc chắn 503 chỉ từ ví dụ một lệnh `curl`. Để tái hiện rejection ổn định, cần dùng latch/load-test hoặc gửi đồng thời đủ task sao cho worker và queue đều bận; ví dụ JDK ở mục 8 là test xác định.

### 9.1. Những điểm quan trọng trong ví dụ Spring

- `@EnableAsync` bật xử lý async qua proxy; controller gọi **bean khác** là `NotificationService` nên method `@Async` đi qua proxy.
- `@Async("notificationExecutor")` chọn đúng bean pool, tránh vô tình dùng executor mặc định khác.
- `AbortPolicy` cho phép submit thất bại **có tín hiệu**; Spring adapter phát `TaskRejectedException` theo hợp đồng.
- Lỗi **tại submit** được bắt bởi `catch` quanh `service.sendAsync(id)`; lỗi **trong quá trình chạy** phải quan sát qua `future.whenComplete(...)` hoặc cơ chế Future khác. Đây là hai đường lỗi khác nhau.
- HTTP 202 ở ví dụ **chỉ xác nhận được giao cho executor trong bộ nhớ**. Nó **không cam kết đã gửi thông báo**, không có job status bền vững, không tự lưu sau crash. Trong nghiệp vụ thật cần job ID, lưu trạng thái, idempotency và cơ chế lấy kết quả/retry phù hợp.
- `Thread.sleep` ở đây là mô phỏng I/O blocking; worker bị chiếm khi chờ. `@Async` chuyển việc chờ sang pool, **không biến JDBC/HTTP blocking thành non-blocking I/O**.
- `this.sendAsync(...)` gọi từ chính service sẽ bỏ qua proxy theo chế độ proxy mặc định, nên không nên dùng để kiểm thử hành vi rejection của `@Async`.

### 9.2. Thực thi trực tiếp bằng `TaskExecutor`

Nếu không dùng `@Async`, có thể inject bean `ThreadPoolTaskExecutor` hoặc `TaskExecutor` và submit trực tiếp. Fragment:

```java
try {
    notificationExecutor.execute(task);
} catch (TaskRejectedException ex) {
    // Chỉ xử lý việc CHƯA được executor nhận.
    // Ví dụ: ghi metric rejection và phản hồi overload.
}
```

Đây là fragment, không phải Java class độc lập: `notificationExecutor` và `task` phải được inject/tạo trước. Với một execution path chỉ sử dụng JDK `ThreadPoolExecutor`, loại exception thường là `RejectedExecutionException`; với Spring `TaskExecutor` nên xét `TaskRejectedException`.

Không ép mọi exception vào một `catch (Exception e)` rồi retry: exception nghiệp vụ, timeout và rejection có căn nguyên khác nhau.

## 10. Dấu hiệu vận hành và cách chẩn đoán

Khi có `TaskRejectedException`, hãy kiểm tra **đúng executor được chọn**, vì ứng dụng thường có nhiều pool cho notification, report, scheduler hoặc request handling. Các getter hữu ích của `ThreadPoolTaskExecutor`:

| Thông tin | Câu hỏi chẩn đoán |
| --- | --- |
| `getCorePoolSize()` | Core cấu hình là bao nhiêu? |
| `getMaxPoolSize()` | Có giới hạn max rõ ràng không? |
| `getPoolSize()` | Pool đã tăng tới bao nhiêu worker? |
| `getActiveCount()` | Bao nhiêu worker đang bận? |
| `getThreadPoolExecutor().getQueue().size()` | Có bao nhiêu task đang chờ? |
| Queue remaining capacity | Hàng đợi còn chỗ không? |
| `getThreadPoolExecutor().isShutdown()` | Executor đã bắt đầu shutdown chưa? |
| Rejection count | Bao nhiêu lần submit thất bại? |
| Queue wait / task duration | Task chậm do xếp hàng hay do xử lý? |

Các getter về pool/queue là **snapshot**, có thể thay đổi ngay sau mỗi lần đọc. Không đưa quyết định nghiệp vụ “có chỗ, chắc chắn nhận được” chỉ vì vừa thấy queue chưa đầy; giữa lúc kiểm tra và submit có thể có thêm task hoặc shutdown.

### Phân tích theo triệu chứng

| Quan sát | Nguyên nhân cần kiểm tra |
| --- | --- |
| Active gần max, queue đầy, reject tăng | Capacity saturation; workload chậm; burst |
| CPU thấp, active cao, queue lớn | Worker chờ DB/HTTP/lock, cần kiểm tra downstream |
| CPU cao, active cao, throughput giảm | CPU contention, quá nhiều worker, task CPU-bound |
| Reject đột ngột đúng lúc deploy/shutdown | Producer vẫn submit vào executor đang đóng |
| Không thấy reject nhưng queue wait tăng mãi | Queue quá lớn hoặc chính sách discard đang che mất việc |
| Request đột ngột mất nhiều giây, không exception | Có thể `CallerRunsPolicy` đang chạy task trên caller |
| Future chờ mãi nhưng không có worker làm | Có thể task bị discard hoặc starvation; xem policy và queue |

Khi có Micrometer/Actuator, nên ghi metric về active worker, queue depth, task accepted/completed/failed/rejected, thời gian chờ queue và thời gian chạy. Bổ sung correlation ID và tên executor để phân biệt tải; chỉ có log “thread pool full” thường chưa đủ để tìm nguyên nhân.

## 11. Ứng xử đúng khi task bị từ chối

### 11.1. Không retry vô hạn ngay trong caller

Giả sử queue đã đầy vì downstream chậm. Nếu 100 request cùng retry không giới hạn liên tục, chúng tiếp tục tranh chấp CPU, RAM và khiến hệ thống khó phục hồi.

Chiến lược tốt hơn tùy mục tiêu:

1. **Giới hạn đầu vào:** rate limit, bulkhead/semaphore, quota theo người dùng/loại việc hoặc giảm số producer.
2. **Báo lỗi có nghĩa:** API có thể trả HTTP 503 khi hạ tầng tạm quá tải; HTTP 429 khi chính sách giới hạn tốc độ của client bị vượt. Cân nhắc `Retry-After` khi có lịch retry xác định.
3. **Retry có giới hạn:** exponential backoff, jitter, timeout tổng và giới hạn số lần; không retry nếu operation không idempotent và chưa có cơ chế chống trùng.
4. **Giữ job quan trọng bền vững:** ghi vào database/job store hoặc message broker có ack và cơ chế xử lý lại; worker pool chỉ là một consumer.
5. **Tách nhóm công việc:** dùng pool riêng cho tải ưu tiên cao và tải chậm để tránh starvation.
6. **Giảm kích thước/batch load:** nếu 1.000 task rất nhỏ tạo overhead quá cao, có thể gom batch khi nghiệp vụ cho phép.

**Retry không tự tăng capacity.** Nếu dependency đang chết hoặc pool đã shutdown, retry liên tục vào cùng nơi không cải thiện khả năng xử lý.

### 11.2. Phải định nghĩa ý nghĩa “accepted” theo hợp đồng API

Một endpoint trả 202 ngay sau khi executor nhận task chỉ biết **đã giao vào pool trong RAM**. Nếu process crash trước khi worker chạy, tác vụ có thể mất. Với tác vụ quan trọng, cần quyết định:

- Chỉ cần **best effort**: có thể chấp nhận rơi task khi quá tải, nhưng phải ghi metric và hiển thị trạng thái.
- Cần **at least once**: đưa job vào nguồn lưu bền vững trước khi xác nhận, consumer ack sau khi xử lý, và hỗ trợ retry.
- Cần **không gây tác động trùng**: dùng idempotency key, deduplication hoặc transaction/outbox phù hợp. “Exactly once” end-to-end không tự có chỉ vì dùng executor.
- Cần **hoàn thành trong thời hạn**: admission control và deadline rõ ràng; queue dài có thể khiến task hết hạn trước khi bắt đầu.

### 11.3. Đừng quên request đã được nhận nhưng chưa chạy

Cấu hình graceful shutdown có thể cho task đang chạy và task trong queue hoàn tất khi context đóng có trật tự. Tuy nhiên timeout shutdown, kill signal hoặc crash vẫn có thể làm mất in-memory tasks. Vì vậy cần đánh giá cả **accepted-but-pending** và **rejected-before-acceptance** thay vì chỉ đếm các exception lúc submit.

## 12. Spring @Scheduled, @Async và vòng đời liên quan

Một method `@Scheduled` có thể gửi việc vào `@Async` executor:

```java
@Scheduled(fixedDelay = 5_000)
public void scheduleBatch() {
    notificationService.sendAsync("daily");
}
```

Đây là fragment và giả định `notificationService` là **bean khác** được inject. Khi scheduler kích hoạt đúng lúc executor đã đầy, method `sendAsync(...)` có thể bị từ chối. Nếu không bắt và quan sát rejection, lần kích hoạt này có thể **không gửi được việc**. Nếu dùng `CallerRunsPolicy`, thread scheduler có thể tự xử lý task và làm chậm các lượt lịch khác.

Quan trọng:

- **Scheduler quyết định khi nào kích hoạt**, executor quyết định task được xử lý trên worker/queue nào.
- `fixedDelay` tính độ trễ sau khi **method scheduled kết thúc**, không tự đợi background task `@Async` hoàn tất nếu method chỉ submit rồi return.
- Khi shutdown, scheduler có thể còn trigger trong lúc executor đã bắt đầu đóng nếu lifecycle không phối hợp; cần tránh producer tiếp tục gửi việc vào pool đã dừng.
- Nếu job phải chạy đúng một lần trên nhiều replica, `@Scheduled` và `ThreadPoolTaskExecutor` riêng lẻ **không cung cấp bảo đảm phân tán**. Phải dùng cơ chế coordination riêng.

Không xử lý overload bằng cách tăng `maxPoolSize` tới vô hạn: giảm rejection trước mắt có thể gây cạn thread hoặc làm hệ thống downstream sập nhanh hơn.

## 13. Những lỗi phổ biến

**Lỗi 1 — Nghĩ một task không ném exception tức là đã chạy xong.** Lời gọi `execute` thành công chỉ là accepted. Task có thể đang đợi queue, còn chạy hoặc lỗi sau đó.

**Lỗi 2 — Chỉ cấu hình `maxPoolSize` mà không để ý queue.** `ThreadPoolExecutor` ưu tiên queue sau khi đủ core; max lớn không tự tăng worker nếu queue chưa đầy.

**Lỗi 3 — Dùng queue không giới hạn để “tránh từ chối”.** Điều này chuyển áp lực sang RAM và queue wait; có thể OOM, không phải khả năng xử lý vô hạn.

**Lỗi 4 — Nhầm lỗi nghiệp vụ với TaskRejectedException.** Exception từ code task sau khi chạy cần xử lý qua Future/callback; không retry chỉ vì một exception chung chung.

**Lỗi 5 — Dùng `DiscardPolicy` cho công việc phải hoàn thành.** Task có thể mất âm thầm. Một Future có thể chờ mãi nếu task bị loại mà không được cancel.

**Lỗi 6 — Dùng `CallerRunsPolicy` mà không chấp nhận caller chậm.** Khi overload, HTTP/scheduler thread có thể bị giữ lại; phía client thấy latency tăng đột ngột.

**Lỗi 7 — Retry ngay không giới hạn vào pool đã đầy.** Gây retry storm và khiến backlog trầm trọng hơn.

**Lỗi 8 — Chạy tác vụ blocking trên ít worker nhưng chỉ nhìn CPU.** CPU thấp không có nghĩa pool còn năng lực; worker có thể đang chờ I/O.

**Lỗi 9 — Submit trong `@PreDestroy` sau khi executor đóng.** Không phối hợp lifecycle làm task bị từ chối đúng lúc shutdown.

**Lỗi 10 — Thấy `@Async` nên mặc định mọi self-invocation cũng chạy nền.** `this.method()` thường bỏ qua proxy. Không thể dựa vào test self-invocation để đánh giá executor.

**Lỗi 11 — Bắt rejection xong trả 202 “đã nhận”.** Khi `AbortPolicy` từ chối, phải phản ánh trạng thái thất bại hoặc lưu việc vào cơ chế khác trước khi xác nhận.

**Lỗi 12 — Coi rejection policy là giải pháp bền vững dữ liệu.** Mọi policy xử lý overload chỉ nằm ở đường submit trong tiến trình. Muốn khôi phục sau crash cần lưu trạng thái/persistent queue.

## 14. Checklist xử lý sự cố ngoài thực tế

Khi phát hiện executor từ chối tác vụ, có thể đi theo trình tự:

1. **Xác định đúng executor và thời điểm:** tên pool, tên task, caller, shutdown hay runtime bình thường.
2. **Đọc cấu hình thực tế:** `corePoolSize`, `maxPoolSize`, `queueCapacity`, rejection handler, thời gian shutdown.
3. **Đo trạng thái:** active/pool size, queue depth/remaining capacity, queue wait, task run time, số lần reject.
4. **Xác định bottleneck:** CPU, DB connection pool, HTTP timeout, lock, I/O, burst traffic hoặc starvation.
5. **Phân loại tác động:** task chưa nhận, task đang chờ, task bị lỗi sau khi nhận, task có thể mất sau restart.
6. **Áp chính sách theo nghiệp vụ:** 503/429 khi phù hợp, retry có backoff và idempotency, rate limit, admission control hoặc job store bền vững.
7. **Kiểm tra tình huống shutdown:** không còn producer submit khi pool đóng, có thời hạn graceful shutdown và phương án khôi phục.
8. **Load test lại:** dùng latch/benchmark có kiểm soát để tái hiện saturation, xác nhận số task được chấp nhận, bị từ chối và hoàn tất.

Không dùng vài lần chạy `Thread.sleep(...)` rồi suy ra chắc chắn queue đầy; lịch chạy thread không cố định. Khi cần minh chứng, hãy giữ worker bằng `CountDownLatch` để đảm bảo điều kiện saturation như ví dụ Java ở trên.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** Pool có core=2, max=4, queue=2. Sáu task đã nhận và chưa task nào hoàn thành. Task thứ bảy có chắc được nhận không?

**Đáp án:** Trong điều kiện cả bốn worker đang bận, queue chứa đủ hai task và không worker rảnh, task thứ bảy **không có capacity** nên đi vào rejection handler. Handler có thể ném, chạy ở caller hoặc bỏ task tùy cấu hình.

**Câu 2.** Vì sao `maxPoolSize=100` không giúp tăng worker nếu `queueCapacity` cực lớn?

**Đáp án:** Sau khi đạt core, executor ưu tiên đưa task vào queue; nó chỉ tăng worker vượt core khi queue không còn nhận được task.

**Câu 3.** Task mới gửi sau `shutdown()` có được tự động chờ để chạy sau khi restart không?

**Đáp án:** Không. Executor đã shutdown không nhận task mới theo cơ chế thông thường. Không có persistence/restart queue tự động.

**Câu 4.** `CallerRunsPolicy` có ném `RejectedExecutionException` khi pool bão hòa không?

**Đáp án:** Thường nó chạy task trên chính caller nếu executor chưa shutdown. Caller có thể bị chặn và đó là backpressure. Nếu executor đã shutdown, policy có thể bỏ task.

**Câu 5.** Một task được submit thành công nhưng ném exception sau 2 giây. Đây có phải rejection không?

**Đáp án:** Không. Task đã được nhận rồi **failed during execution**; cần quan sát `Future`/`CompletableFuture` hoặc handler của kiểu task tương ứng.

**Câu 6.** Có thể giải quyết overload chỉ bằng retry ngay trong vòng lặp vô hạn không?

**Đáp án:** Không. Retry storm tăng tải. Cần backoff, giới hạn, jitter, idempotency và admission control hoặc queue bền vững khi nghiệp vụ cần.

**Câu 7.** `queueCapacity=0` có nghĩa executor luôn từ chối?

**Đáp án:** Không. Nó dùng handoff trực tiếp; task được nhận khi worker có thể nhận hoặc pool còn mở rộng được. Khi max workers đều bận thì mới đi vào handler.

**Câu 8.** Vì sao HTTP 202 ngay sau `@Async` submit không chắc công việc sẽ hoàn thành?

**Đáp án:** Nó chỉ phản ánh việc giao thành công vào executor trong bộ nhớ; task có thể lỗi sau đó, bị ngắt hoặc mất khi process crash. Cần persistent job tracking nếu muốn cam kết thực thi.

## Tài liệu tham khảo

- Spring Framework Reference — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html
- Spring Framework Javadoc — ThreadPoolTaskExecutor: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Spring Framework Javadoc — TaskRejectedException: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/task/TaskRejectedException.html
- Java SE 17 API — ThreadPoolExecutor, phần Rejected Tasks: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
- Java SE 17 API — RejectedExecutionHandler: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RejectedExecutionHandler.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overload-rejection-002">RejectedExecutionHandler và TaskRejectedException</a>

<details>
<summary>Xem chi tiết</summary>

# RejectedExecutionHandler và TaskRejectedException: xử lý khi executor không nhận thêm tác vụ

Trong ứng dụng Spring chạy đồng thời, **gửi một tác vụ tới executor không bảo đảm tác vụ luôn được chấp nhận**. Khi thread pool hết khả năng nhận công việc, hoặc đã dừng nhận tác vụ mới, lời gọi gửi việc có thể bị **rejected**. Ở tầng Java, **RejectedExecutionHandler** quyết định hành động khi **ThreadPoolExecutor** từ chối. Ở tầng Spring, **TaskRejectedException** là ngoại lệ thể hiện việc một **TaskExecutor** không chấp nhận một task theo hợp đồng Spring.

Chủ đề này thuộc nhóm **Quá tải và từ chối** của **Spring Framework - concurrency**. Sau bài học, bạn có thể xác định thời điểm rejection xảy ra, chọn chính sách phù hợp, phân biệt lỗi khi submit với lỗi trong task, tích hợp với **@Async**, kiểm thử overload một cách xác định và bảo vệ ứng dụng khỏi việc mất công việc âm thầm.

## 1. Vì sao một tác vụ bị từ chối?

Một thread pool có khả năng xử lý hữu hạn. Ví dụ, một service nhận nhiều yêu cầu tạo báo cáo, mỗi báo cáo chiếm worker và truy cập database trong vài giây. Nếu tốc độ gửi task lớn hơn tốc độ xử lý, hệ thống phải quyết định **xếp chờ, tạo thêm worker, chạy trên luồng gửi hay từ chối**.

**ThreadPoolExecutor** thường xem xét các bước chính:

1. Khi số worker đang có nhỏ hơn **corePoolSize**, cố tạo worker mới để thực thi task.
2. Khi đã có đủ core workers, cố đưa task vào **workQueue**.
3. Nếu không đưa được vào queue, cố tạo thêm worker đến **maximumPoolSize**.
4. Nếu vẫn không nhận được task, gọi **RejectedExecutionHandler**.
5. Executor đã **shutdown** cũng không còn chấp nhận task mới; lúc này rejection có thể xảy ra dù pool không bận.

~~~text
Caller -> execute(task)
               |
        Pool còn dưới core?
          | Có        | Không
          v           v
     Tạo worker    Thử queue.offer(task)
                      | Có chỗ       | Không nhận
                      v              v
                    Queue      Còn chỗ dưới max?
                                  | Có     | Không
                                  v        v
                             Tạo worker  REJECT
                                           |
                                 RejectedExecutionHandler
~~~

Đây là sơ đồ đơn giản của **đường nhận việc**, không mô tả tất cả kiểm tra cạnh tranh và shutdown trong triển khai JDK. Quan hệ giữa **corePoolSize**, **maxPoolSize**, **queueCapacity** rất quan trọng:

| Cấu hình | Hành vi dưới tải |
| --- | --- |
| Queue có giới hạn dương | Sau khi đủ core workers, task thường được xếp hàng; khi hàng đợi đầy mới mở rộng đến max. |
| Queue rất lớn/gần như vô hạn | Task tiếp tục nằm chờ, vì thế **maxPoolSize** có thể hiếm khi phát huy tác dụng. |
| **queueCapacity = 0** trong ThreadPoolTaskExecutor | Dùng **SynchronousQueue**, chuyển giao trực tiếp; không có vùng lưu task chờ. Khi không handoff được, thử tăng worker rồi reject. |
| Executor đã shutdown | Từ chối gửi mới theo chính sách hiện tại, bất kể nhìn thấy queue còn chỗ hay không. |

**Rejected không đồng nghĩa với task chạy thất bại.** Rejection xảy ra ở **ranh giới nhận công việc**: có thể task chưa từng được chạy. Lỗi nghiệp vụ bên trong worker là một sự kiện khác; hai tình huống cần xử lý và giám sát riêng.

## 2. RejectedExecutionHandler là hợp đồng của Java

Trong gói **java.util.concurrent**, giao diện **RejectedExecutionHandler** có một phương thức trừu tượng:

~~~java
void rejectedExecution(Runnable task, ThreadPoolExecutor executor);
~~~

Đây là **chữ ký rút gọn** dùng để giải thích API; kiểu thật được khai báo trong **java.util.concurrent.RejectedExecutionHandler** với tham số **Runnable r** và **ThreadPoolExecutor executor**.

Handler được **ThreadPoolExecutor** gọi khi nó không chấp nhận một task qua đường **execute**. Tùy handler, kết quả có thể là:

- **Ném ngoại lệ** để caller biết việc gửi bị từ chối.
- **Chạy task ngay trên luồng gọi** nhằm tạo áp lực ngược lên bên gửi.
- **Bỏ task**, có hoặc không loại một task khác, tùy chính sách.
- Thực thi **custom policy** do ứng dụng viết, nhưng policy phải quy định rõ hành vi, trách nhiệm với task/future và khả năng quan sát.

Đối tượng **Runnable** được đưa cho handler có thể là task gốc hoặc một wrapper như **FutureTask**, đặc biệt khi người gọi dùng **submit**. Vì vậy, tự viết handler mà âm thầm bỏ task có thể để lại một **Future không bao giờ hoàn tất**.

**Không phải mọi TaskExecutor của Spring đều là ThreadPoolExecutor.** Ví dụ **SimpleAsyncTaskExecutor** có cơ chế giới hạn đồng thời riêng, không tự nhiên biến thành một pool dùng bốn rejection policy của JDK. Trong bài này, **RejectedExecutionHandler** chủ yếu áp dụng cho **ThreadPoolExecutor** và **ThreadPoolTaskExecutor** bọc nó; **TaskRejectedException** là khái niệm rộng hơn ở phía Spring.

## 3. Bốn chính sách rejection có sẵn trong ThreadPoolExecutor

JDK cung cấp bốn triển khai trực tiếp của **RejectedExecutionHandler**:

| Chính sách | Khi không chấp nhận task | Hệ quả cần cân nhắc |
| --- | --- | --- |
| **AbortPolicy** | Ném **RejectedExecutionException** | Rejection tường minh, caller có thể ghi nhận và trả lỗi/phản hồi phù hợp. Đây là mặc định của ThreadPoolExecutor. |
| **CallerRunsPolicy** | Nếu pool chưa shutdown, **luồng gọi execute tự chạy task** | Gây áp lực ngược bằng cách làm caller chậm lại; phá giả định phần việc luôn chạy trên worker khác. Nếu đã shutdown, task bị bỏ. |
| **DiscardPolicy** | Bỏ task và **không ném ngoại lệ** | Có thể mất việc âm thầm; **Future** của task bị bỏ có thể không bao giờ hoàn tất. |
| **DiscardOldestPolicy** | Nếu pool còn hoạt động, bỏ task đang chờ ở đầu queue rồi thử gửi task mới lần nữa | Có thể làm mất task cũ và để future của nó không hoàn tất; khi không có task chờ thích hợp, việc thử lại có thể tiếp tục thất bại. |

**AbortPolicy** thường là lựa chọn dễ kiểm soát nhất cho những tác vụ mà người gọi cần biết đã được nhận hay chưa. Người gọi có thể báo quá tải hoặc quyết định retry có giới hạn.

**CallerRunsPolicy** có ích khi chủ đích là cho bên gửi trả giá bằng thời gian thực thi, từ đó giảm tốc độ gửi task mới. Tuy nhiên, nó **có thể làm một HTTP request thread phải thực thi tác vụ dài**, ảnh hưởng latency hoặc làm sai giả định về MDC, transaction và ngữ cảnh luồng.

**DiscardPolicy** chỉ nên được cân nhắc khi task thực sự có thể bị bỏ **mà không ai chờ kết quả hay cần ghi nhận thành công**. Chẳng hạn, một số tín hiệu đo lường không quan trọng có thể bỏ có kiểm soát, nhưng task nghiệp vụ phải bảo đảm hoàn tất thì không phù hợp.

**DiscardOldestPolicy** không bảo đảm công bằng hay an toàn cho các task có Future. Bỏ task ở đầu hàng đợi có thể khiến Future tương ứng vẫn pending nếu không chủ động cancel. Với **SynchronousQueue** không có buffer, đừng giả định luôn có task cũ trong queue để loại; chính sách này đặc biệt khó lý giải ở cấu hình chuyển giao trực tiếp.

### Chính sách nào là mặc định?

Khi dùng **ThreadPoolExecutor** mà không chỉ định handler khác, JDK dùng **AbortPolicy**. **ThreadPoolTaskExecutor** của Spring cũng dựa trên cấu hình này ở tầng pool nếu ứng dụng không thay đổi handler. Khi tầng Java ném **RejectedExecutionException**, lớp Spring có thể chuyển thành **TaskRejectedException** theo hợp đồng **TaskExecutor**.

Chính sách có thể khác nếu dự án **tự cấu hình setRejectedExecutionHandler**, framework tích hợp cung cấp executor riêng hoặc có lớp wrapper khác. Khi phân tích lỗi thật, luôn xem cấu hình executor thực tế.

## 4. Ví dụ Java 17 chạy được: AbortPolicy và CallerRunsPolicy

Để không phụ thuộc may rủi của scheduler, ta dùng **CountDownLatch** giữ worker đang bận. Pool có **một worker**, **một chỗ queue**; task thứ ba sẽ bị từ chối. Sau đó đổi handler sang **CallerRunsPolicy** để kiểm tra task được thực hiện ngay ở thread gửi.

Lưu thành **RejectionPoliciesDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RejectionPoliciesDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(1),
                new ThreadPoolExecutor.AbortPolicy()
        );

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        Thread submittingThread = Thread.currentThread();

        try {
            pool.execute(() -> {
                workerStarted.countDown();
                try {
                    releaseWorker.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            if (!workerStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker không khởi động");
            }

            // Worker thứ nhất đang bị giữ; task tiếp theo nằm trong queue.
            pool.execute(() -> { });

            System.out.println("Queue đã đầy: "
                    + (pool.getQueue().size() == 1));

            boolean aborted = false;
            try {
                pool.execute(() -> { });
            } catch (RejectedExecutionException e) {
                aborted = true;
            }
            System.out.println("AbortPolicy từ chối: " + aborted);

            pool.setRejectedExecutionHandler(
                    new ThreadPoolExecutor.CallerRunsPolicy());

            AtomicBoolean ranOnCaller = new AtomicBoolean(false);
            pool.execute(() -> ranOnCaller.set(
                    Thread.currentThread() == submittingThread));

            System.out.println("CallerRuns trên luồng gửi: "
                    + ranOnCaller.get());
        } finally {
            releaseWorker.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Chạy bằng **JDK 17+**:

~~~bash
javac -encoding UTF-8 RejectionPoliciesDemo.java
java RejectionPoliciesDemo
~~~

**Stdout** trong điều kiện bình thường (worker khởi động trong 5 giây):

~~~text
Queue đã đầy: true
AbortPolicy từ chối: true
CallerRuns trên luồng gửi: true
~~~

**Vì sao kết quả xác định?**

1. Task đầu tiên gọi **workerStarted.countDown()**, rồi chờ **releaseWorker**. Main đợi đến khi task đã chạy thực sự.
2. Pool chỉ có một worker và một vị trí queue. Khi task thứ hai được gửi, worker vẫn bị giữ, nên task đó chiếm vị trí queue.
3. Task thứ ba không thể tạo thêm worker vì **core=max=1** và không vào được queue. **AbortPolicy** ném ngoại lệ.
4. Ta thay handler thành **CallerRunsPolicy**. Một task tiếp theo gặp cùng trạng thái bão hòa; handler chạy lambda **ngay trên submittingThread**. So sánh danh tính Thread trả true.
5. **finally** mở latch, shutdown pool rồi chờ kết thúc để tránh giữ luồng nền sau ví dụ.

Chương trình kiểm tra **điều kiện cấu trúc và danh tính luồng**, không dựa vào in tên thread có thể thay đổi. Nếu test bị timeout trước khi worker khởi động, chương trình ném **IllegalStateException** thay vì khẳng định một stdout không đúng.

Đây là **ThreadPoolExecutor thuần Java** nên có thể dùng javac/java. Đối với **ThreadPoolTaskExecutor** của Spring, đường nhận task nền tương tự, nhưng ngoại lệ từ chối được biểu diễn theo hợp đồng Spring.

## 5. Tình huống khi submit Future: tránh chính sách bỏ tác vụ im lặng

Cần phân biệt **execute** và **submit**:

- **execute(Runnable)** chỉ gửi việc; nếu dùng AbortPolicy, rejection thường được ném ngay cho caller.
- **submit(Runnable/Callable)** tạo một **Future** đại diện cho kết quả, rồi gửi wrapper task tới executor. Rejection tại thời điểm gửi có thể xảy ra **trước khi submit trả về Future**.
- Nếu handler **im lặng bỏ task**, đường submit có thể vẫn trả về một Future đã được tạo nhưng **không có worker nào hoàn tất nó**.

Ví dụ minh họa trong một tình huống **pool đã bão hòa**:

~~~java
pool.setRejectedExecutionHandler(
        new ThreadPoolExecutor.DiscardPolicy());

java.util.concurrent.Future<?> future =
        pool.submit(() -> System.out.println("Công việc quan trọng"));
~~~

Đây là **đoạn mã minh họa**, giả sử **pool** là ThreadPoolExecutor đang chạy và đã đạt cả worker/queue limit; nó **không phải chương trình độc lập**. Ở tình huống này, DiscardPolicy có thể khiến submit trả về Future chưa hoàn tất; **future.get() có thể chờ vô hạn** do tác vụ không bao giờ được chạy. Nếu điều này đã xảy ra, cần chính sách cancel/timeout và xử lý xác thực trạng thái thực tế.

**Đừng dùng DiscardPolicy chỉ để “hết thấy lỗi rejection”**. Mất ngoại lệ không có nghĩa hệ thống đã xử lý công việc. Với tác vụ liên quan thanh toán, lưu dữ liệu hoặc thông báo phải bảo đảm, tính toàn vẹn nghiệp vụ đòi hỏi cơ chế tiếp nhận, trả lời, retry hoặc queue bền vững phù hợp.

Tương tự, **DiscardOldestPolicy** có thể làm task đã vào queue bị loại. Nếu task đó là wrapper của Future, code đợi nó có thể mắc kẹt trừ khi có logic cancel đúng cách. Các Javadoc mới cũng khuyến nghị cân nhắc hủy Future và ghi nhận việc loại task trong custom policy.

## 6. TaskRejectedException: ngoại lệ của hợp đồng Spring TaskExecutor

Spring cung cấp **org.springframework.core.task.TaskRejectedException**. Nó **kế thừa java.util.concurrent.RejectedExecutionException**, nên có thể được bắt cụ thể ở ứng dụng Spring hoặc bắt theo kiểu cha trong tích hợp chung với JDK executors.

~~~text
java.lang.RuntimeException
    |
    +-- RejectedExecutionException  (java.util.concurrent)
           |
           +-- TaskRejectedException  (org.springframework.core.task)
~~~

**Ý nghĩa:** một **TaskExecutor** của Spring đã không chấp nhận task được gửi. Đó là tín hiệu về **khả năng tiếp nhận công việc**, không phải một ngoại lệ nghiệp vụ xuất hiện trong task đã thực thi.

Các trường hợp thường dẫn tới lỗi:

- Pool worker đã đạt **maxPoolSize** và queue không nhận được thêm task.
- Executor đã **shutdown** hoặc không còn cho phép submit.
- Một implementation TaskExecutor khác có quy tắc quá tải riêng và biểu diễn việc từ chối qua TaskRejectedException.
- Chính sách rejection ở dưới ném **RejectedExecutionException**, được **ThreadPoolTaskExecutor** bọc theo hợp đồng Spring.

**Lưu ý về custom handler:** nếu handler được cài để **im lặng bỏ task** hoặc **thực thi task ở caller** và trả về bình thường, có thể **không có TaskRejectedException được ném**, dù đường rejection đã được kích hoạt. Nếu handler ném một runtime exception khác với RejectedExecutionException, không nên giả định Spring luôn chuyển mọi loại ngoại lệ đó thành TaskRejectedException.

### Java và Spring nhìn cùng một tình huống

| Tầng | Loại dùng | Kết quả khi AbortPolicy từ chối |
| --- | --- | --- |
| JDK ThreadPoolExecutor | RejectedExecutionHandler, RejectedExecutionException | **RejectedExecutionException** tại chỗ submit/execute. |
| Spring ThreadPoolTaskExecutor | setRejectedExecutionHandler, TaskRejectedException | **TaskRejectedException**, có thể mang nguyên nhân là RejectedExecutionException. |
| Spring @Async gọi qua proxy | Future/CompletableFuture + executor | Lời gọi submit qua proxy có thể **ném TaskRejectedException ngay**; nếu được nhận, lỗi trong worker có thể đến qua future. |

Bắt ngoại lệ này có ý nghĩa thực tế khi đặt giới hạn pool. Ví dụ API cần phân biệt “hệ thống quá bận để nhận việc mới” với “công việc được nhận rồi nhưng tính toán thất bại”.

## 7. Cấu hình ThreadPoolTaskExecutor và chính sách từ chối

Trong Spring, **ThreadPoolTaskExecutor** hỗ trợ cấu hình **corePoolSize**, **maxPoolSize**, **queueCapacity** và **setRejectedExecutionHandler(...)**. Phương thức đặt handler được kế thừa từ lớp hỗ trợ cấu hình executor của Spring.

Ví dụ đây là **các lớp trong ứng dụng có Spring Context**, không phải file độc lập chạy bằng javac trần.

**ReportExecutorConfig.java**:

~~~java
package example.concurrent;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class ReportExecutorConfig {

    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("report-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());

        // Spring khởi tạo bean và quản lý lifecycle của executor.
        return executor;
    }
}
~~~

Cấu hình này cho phép tối đa **4 worker** và một queue chờ tối đa **8 task**. Khi **2 core workers** bận, pool thường ưu tiên nhận task vào queue; khi queue đầy, pool mới thử tăng worker đến 4. Nếu toàn bộ khả năng nhận việc đã hết, **AbortPolicy** từ chối và Spring báo **TaskRejectedException** cho nơi submit.

**Đừng suy luận chỉ từ maxPoolSize=4 rằng pool luôn chạy đúng bốn worker.** Trong trạng thái tải bình thường, nó có thể chỉ có hai worker hoặc ít hơn; các thông số là ngưỡng, không phải cam kết số thread hiện diện mọi thời điểm.

**Điều kiện khai thác:** các con số **2/4/8** chỉ để minh họa. Với workload thật, cần cân nhắc CPU-bound/I/O-bound, thời gian task, queue wait, giới hạn connection pool và chính sách overload.

### Handler nhận task nào?

Handler nhận **Runnable** mà executor cố gửi. Nếu task đã được đóng gói bằng FutureTask, việc log mỗi task bằng **toString()** hoặc giữ task trong một tập riêng có thể tạo overhead/rò rỉ dữ liệu. Một custom handler nên làm công việc thật ngắn gọn, ghi nhận counter và đưa ra lựa chọn có thể kiểm chứng.

Ví dụ **đoạn mã cấu hình** để đếm và ném tiếp ngoại lệ:

~~~java
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

AtomicLong rejectedCount = new AtomicLong();

executor.setRejectedExecutionHandler((task, pool) -> {
    rejectedCount.incrementAndGet();
    throw new RejectedExecutionException("Executor đã đầy hoặc dừng");
});
~~~

Đây là **đoạn minh họa bên trong lúc cấu hình executor** với biến **executor** đã có. Vì handler ném **RejectedExecutionException**, caller nhận được tín hiệu lỗi; lớp **ThreadPoolTaskExecutor** có thể bọc nó thành **TaskRejectedException**. Một counter đơn giản chỉ đo số lần handler bị gọi **trên instance đó** và không thay thế metrics có nhãn/mức tổng hợp phù hợp ở production.

## 8. Tích hợp với @Async: hai thời điểm thất bại khác nhau

Giả sử một bean thực thi báo cáo trên executor ở mục 7:

**ReportService.java**:

~~~java
package example.concurrent;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    @Async("reportExecutor")
    public CompletableFuture<String> generate(String reportId) {
        if (reportId.isBlank()) {
            throw new IllegalArgumentException("reportId phải có dữ liệu");
        }
        return CompletableFuture.completedFuture("report:" + reportId);
    }
}
~~~

Một bean khác được Spring quản lý gọi phương thức này **qua proxy**:

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.core.task.TaskRejectedException;

try {
    CompletableFuture<String> future = reportService.generate("r-17");

    future.whenComplete((value, error) -> {
        if (error == null) {
            System.out.println("Đã xong: " + value);
        } else {
            System.err.println("Task chạy lỗi: " + error.getMessage());
        }
    });
} catch (TaskRejectedException rejected) {
    System.err.println("Không thể nhận việc: "
            + rejected.getMessage());
}
~~~

Đây là **đoạn minh họa trong phương thức caller** đã inject ReportService; phần code Spring cần dependency và cấu hình bean phù hợp. Ví dụ này cho thấy:

**Trường hợp A — từ chối tại submit:** pool đầy hoặc đã dừng; lời gọi qua **@Async** có thể ném **TaskRejectedException** ngay trong luồng gọi. Chưa chắc có một **CompletableFuture** được trả về để gắn **exceptionally()** hay **whenComplete()**.

**Trường hợp B — task đã được nhận nhưng logic lỗi:** ví dụ reportId rỗng khiến worker ném **IllegalArgumentException**. Với return type **CompletableFuture**, caller quan sát lỗi qua future (như whenComplete, exceptionally hoặc join). Đây **không phải** rejection.

**Trường hợp C — thành công:** task được nhận, thực thi và future hoàn tất với giá trị **report:r-17**. Thứ tự in log giữa nhiều request/worker không được bảo đảm.

Phân biệt ba trường hợp này rất quan trọng khi báo lỗi cho caller hoặc tính retry. **Không nên xem mọi CompletionException/ExecutionException như overload**.

**@Async không tự xử lý overload thay bạn.** Trong mô hình proxy thông thường, lời gọi từ cùng object qua **this.generate(...)** bỏ qua interceptor, khiến hành vi chuyển luồng không như mong đợi. Muốn dùng @Async, phải bật **@EnableAsync** hoặc cấu hình tương đương và gọi qua Spring proxy. Nếu cấu hình **CallerRunsPolicy**, phương thức @Async thậm chí có thể chạy trong thread của caller khi pool bão hòa.

### Async void và xử lý lỗi

Với **@Async void**, caller không có Future để lấy ngoại lệ bên trong worker. Spring cung cấp cơ chế **AsyncUncaughtExceptionHandler** cho trường hợp này. Tuy nhiên, ngoại lệ từ chối **trước khi task được nhận** vẫn thuộc đường gửi việc; đừng chỉ trông vào handler xử lý lỗi từ worker.

Nếu nghiệp vụ cần biết kết quả, dùng **Future/CompletableFuture** có giám sát lỗi hoặc một cơ chế job bền vững phù hợp, thay vì gửi một void task rồi xem lời gọi trả về là cam kết thành công.

## 9. CallerRunsPolicy: backpressure nhưng có chi phí ngữ nghĩa

**CallerRunsPolicy** được dùng vì nó tương đối đơn giản: khi pool bão hòa và chưa shutdown, chính **luồng gọi execute** chạy task. Khi caller là HTTP request thread, điều đó có nghĩa lời gọi **execute** hoặc phương thức proxy **@Async** có thể kéo dài cho tới khi phần công việc hoàn thành.

~~~text
Trước khi bão hòa:
HTTP thread -> submit -> worker xử lý -> HTTP thread rảnh

Khi bão hòa với CallerRunsPolicy:
HTTP thread -> submit
             -> chạy cả task ngay trên HTTP thread
             -> submit trả về sau khi task hoàn tất
~~~

**Ưu điểm:** làm bên gửi chậm lại, giúp giảm tốc độ đưa thêm task mới và có thể tránh bỏ task ngay khi pool còn đang hoạt động.

**Đánh đổi:**

- Response latency của HTTP request có thể tăng mạnh; thậm chí dẫn tới cạn server request threads.
- Đoạn code dự kiến chạy ở background lại chạy cùng luồng với caller. Các giả định về **ThreadLocal**, MDC, transaction hoặc tracing phải được xem xét.
- Nếu task có lock hoặc phụ thuộc đồng bộ vào một tác vụ khác, việc chạy trực tiếp ở caller có thể gây nghẽn khó nhận biết.
- **Khi executor đã shutdown, CallerRunsPolicy không chạy task mà bỏ qua nó**. Vì vậy không được dùng policy này như bảo đảm mọi task sẽ được thực hiện.

**Không nên chọn CallerRunsPolicy chỉ vì muốn “không bao giờ reject”**. Nó tạo backpressure bằng chặn caller trong khi làm việc, có thể hợp lý cho worker producer nội bộ nhưng gây tác động lớn khi người gửi chính là request thread phục vụ người dùng.

## 10. Viết custom RejectedExecutionHandler có trách nhiệm

Handler tùy biến thường cần cho quan sát và điều phối overload. Một mẫu an toàn, dễ dự đoán là **ghi nhận reject rồi ném exception rõ ràng**, như ví dụ AtomicLong ở trên. Nếu muốn đưa task sang cơ chế khác, phải thiết kế hợp đồng tiếp nhận.

Các nguyên tắc quan trọng:

1. **Xác định task đã được tiếp nhận hay chưa.** Handler không được trả về bình thường và đồng thời âm thầm mất một job mà caller tin đã được nhận.
2. **Không chặn quá lâu trong handler.** Handler chạy trên luồng đang submit; đợi vô hạn để queue có chỗ hoặc retry liên tục có thể làm nghẽn request threads và gây deadlock.
3. **Tránh retry đệ quy vô giới hạn ngay trong handler.** Nếu pool vẫn đầy hoặc đã shutdown, lặp execute(task) liên tục có thể tạo vòng lặp không tiến triển.
4. **Giữ thao tác quan sát nhẹ.** Ghi counter, trạng thái pool và correlation ID phù hợp; tránh in dữ liệu nhạy cảm hoặc serialize cả Runnable/FutureTask vào log.
5. **Phân biệt shutdown với saturation.** Cùng một API rejection có thể được gọi trong hai nguyên nhân vận hành khác nhau; nên đọc trạng thái executor và lifecycle khi xử lý.
6. **Tính đến Future đã tồn tại.** Nếu một task bị loại khỏi queue, caller đang chờ Future cần nhận trạng thái cancel/failure tương ứng nếu policy cho phép.
7. **Giữ đúng chính sách tài nguyên.** Tự tạo thread vô hạn trong handler để tránh reject chỉ di chuyển điểm nghẽn ra khỏi pool.

Một custom handler cũng có thể chuyển việc sang một hàng đợi bền vững, nhưng điều đó yêu cầu hợp đồng lưu trữ, xác nhận nhận việc, idempotency, retry và lỗi phù hợp. Đừng coi `execute()` trả về là bằng chứng task đã được ghi an toàn nếu việc ghi có thể thất bại.

## 11. Ứng dụng nên làm gì khi nhận TaskRejectedException?

Phản ứng phù hợp phụ thuộc mục tiêu của lời gọi.

| Loại tác vụ | Lựa chọn có thể phù hợp | Điều cần tránh |
| --- | --- | --- |
| Yêu cầu HTTP đồng bộ cần kết quả ngay | Trả lỗi quá tải có chủ đích, chẳng hạn **HTTP 503** khi service tạm không tiếp nhận được; có thể kèm Retry-After nếu thật sự biết thời điểm thử lại. | Giả vờ trả thành công khi task chưa được nhận. |
| API bị giới hạn theo quota hoặc rate limit | **HTTP 429** khi lý do là giới hạn tốc độ của caller, kèm chính sách retry rõ. | Dùng 429 thay cho mọi lỗi cạn tài nguyên không phân biệt nguyên nhân. |
| Job không được mất | Tiếp nhận qua queue/job store bền vững, có idempotency và trạng thái rõ. | Chỉ dùng DiscardPolicy hoặc in-memory executor như kho lưu trữ lâu dài. |
| Tác vụ best-effort | Có thể drop sau khi đo và giải thích chính sách mất dữ liệu chấp nhận được. | Để caller hiểu nhầm drop là hoàn tất. |
| Xử lý nội bộ với producer có thể chậm lại | Backpressure hoặc bounded retry, cân nhắc CallerRunsPolicy khi hợp đồng thread cho phép. | Vòng retry vô hạn, không có deadline. |

**HTTP 503/429 là lựa chọn ở tầng giao thức và nghiệp vụ**, không phải mã HTTP tự động được Spring gán cho mọi TaskRejectedException. Một ứng dụng có thể dùng **@ControllerAdvice**, exception mapping hoặc tầng API gateway để diễn dịch overload theo hợp đồng của mình. Không nên hứa một thời điểm retry chính xác nếu hệ thống không tính được.

Nếu request tạo job trả **202 Accepted**, chỉ trả 202 khi hệ thống thực sự **đã tiếp nhận công việc theo hợp đồng đã hứa**. Một lời gọi executor trong RAM bị reject không đủ điều kiện để xem là accepted.

## 12. Retry khi overload: phải hữu hạn và có chủ đích

Retry không phải lúc nào cũng đúng. Một task có thể bị reject do shutdown (retry vào cùng executor vô ích), do downstream quá tải, do cấu hình pool quá nhỏ hoặc do burst ngắn. **Retry đồng loạt và tức thì** khi toàn bộ caller cùng gặp reject thường làm hệ thống bận hơn.

Một chính sách retry có kiểm soát cần:

- **Giới hạn số lần** và tổng deadline.
- **Exponential backoff có jitter** khi thích hợp để tránh đám đông retry đồng thời.
- **Idempotency** cho thao tác có side effect: tạo đơn hàng, ghi hóa đơn, gửi thông báo không được nhân đôi sau retry.
- **Phân biệt lý do**: saturated tạm thời, executor đang shutdown, yêu cầu không hợp lệ và lỗi nghiệp vụ; không retry tất cả như nhau.
- **Nhìn tải toàn hệ thống**: retry thêm có thể chỉ làm nghẽn DB/external API, dù executor có nhận task ở lần sau.

Nếu chính handler tự sleep hoặc block để retry, thời gian ấy diễn ra trên **thread submit**, có thể là HTTP request thread. Thường nên đặt retry ở lớp orchestration/caller với deadline rõ, thay vì giấu nó trong RejectedExecutionHandler.

**Điểm mấu chốt:** retry chỉ là chiến lược gửi lại công việc, không bảo đảm xử lý đúng hoặc bền vững. Một hệ thống cần bảo đảm không mất job phải có cơ chế lưu công việc và xác nhận tiếp nhận đáng tin.

## 13. Giám sát overload và không nhầm các chỉ số

Chỉ số cần quan sát cho **ThreadPoolTaskExecutor**:

| Chỉ số | Ý nghĩa |
| --- | --- |
| **getActiveCount()** | Số worker đang thực thi task tại thời điểm quan sát. |
| **getPoolSize()** | Số worker hiện có, không nhất thiết luôn bằng max. |
| **getCorePoolSize() / getMaxPoolSize()** | Các ngưỡng cấu hình để hiểu khả năng mở rộng. |
| **getQueueSize()** | Số task chờ trong queue, chỉ là snapshot tại thời điểm đọc. |
| **Rejected task count** | Cần tự đo hoặc dùng instrumentation phù hợp; tăng cho thấy submit không được chấp nhận theo chính sách ném lỗi. |
| **Task wait/run duration** | Phân biệt nghẽn do queue wait, do CPU hay I/O. |
| **HTTP latency, error rate, downstream saturation** | Kiểm tra tác động tới người dùng và dịch vụ phụ thuộc. |

**Queue size bằng 0 không có nghĩa hệ thống khỏe.** Với **SynchronousQueue** được chọn bởi queueCapacity=0, hàng đợi **không tích trữ task**, trong khi toàn bộ worker vẫn có thể bận và handler reject các task mới.

**Count rejection phải hiểu theo policy.** Với CallerRunsPolicy, handler có thể được gọi nhưng task vẫn được chạy trên caller; với DiscardPolicy, task bị bỏ mà không có ngoại lệ. Vì vậy, nên tách các chỉ số **submission attempts, accepted, caller-runs, rejected-with-error, discarded** nếu ứng dụng cần đo chính xác.

**Không dùng riêng thông điệp exception để phân biệt nguyên nhân.** Kiểm tra trạng thái **isShutdown()** của executor nền khi cần, nhưng một snapshot trạng thái là quan sát thời điểm, không phải sự chứng minh tuyệt đối về nguyên nhân giữa các luồng cạnh tranh.

### Quan sát qua JDK underlying executor

Đoạn Spring sau giả sử một **ThreadPoolTaskExecutor đã initialize**:

~~~java
java.util.concurrent.ThreadPoolExecutor delegate =
        executor.getThreadPoolExecutor();

System.out.println("shutdown=" + delegate.isShutdown());
System.out.println("active=" + executor.getActiveCount());
System.out.println("pool=" + executor.getPoolSize());
System.out.println("queue=" + executor.getQueueSize());
~~~

Đây là **đoạn minh họa trong phương thức có biến executor hợp lệ**, không phải chương trình độc lập. Những số đo có thể thay đổi ngay sau khi đọc; chỉ dùng để định hướng kiểm tra cùng log/metrics liên quan.

## 14. Kiểm thử rejection đáng tin

Để kiểm tra handler, không nên chạy vài task ngẫu nhiên rồi dựa vào **Thread.sleep()** với hy vọng chúng tình cờ bão hòa pool. Có thể tạo tình huống điều phối bằng **CountDownLatch**:

1. Cấu hình **corePoolSize=1, maxPoolSize=1, queueCapacity=1** và AbortPolicy.
2. Gửi task A; dùng latch đợi A đã bắt đầu, rồi giữ A không kết thúc.
3. Gửi task B vào queue; xác nhận queue đã nhận B.
4. Gửi task C; khẳng định caller nhận **TaskRejectedException** tại Spring hoặc **RejectedExecutionException** ở JDK.
5. Đổi policy có chủ đích sang CallerRunsPolicy trong test riêng; assert Runnable được thực thi trên **đúng thread đang gửi task**.
6. Nếu thử shutdown, gọi shutdown trước khi gửi thêm task; assert hành vi theo handler, không suy luận từ tình trạng queue.
7. Trong **finally**, giải phóng latch và đóng executor, tránh treo suite hoặc rò rỉ worker threads.

**Cần test cả Future** khi handler có thể bỏ task: nếu cố tình dùng policy drop, phải kiểm chứng có cơ chế cancel/fail cho Future hoặc caller không chờ Future đó. Test chỉ assert “execute không ném ngoại lệ” là chưa chứng minh công việc đã được thực hiện.

**Các tình huống biên**: task lỗi sau khi được chấp nhận, ứng dụng đóng context trong lúc submit, pool full nhưng một worker vừa rảnh, và một request thread bị kéo sang CallerRuns. Mỗi tình huống kiểm tra một hành vi khác nhau.

## 15. Những nhầm lẫn phổ biến

**“TaskRejectedException có nghĩa task đã chạy và phát sinh exception.”** Sai. Nó thường biểu thị **tác vụ không được chấp nhận để thực thi**.

**“RejectedExecutionHandler luôn ném ngoại lệ.”** Sai. Chỉ handler như AbortPolicy ném; CallerRunsPolicy có thể chạy task, DiscardPolicy có thể bỏ task im lặng.

**“Thêm maxPoolSize là chắc chắn không bị từ chối.”** Sai. Nếu queue/pool vẫn bão hòa hoặc executor đã shutdown thì vẫn reject; max quá lớn còn có rủi ro tài nguyên.

**“queueCapacity lớn khiến maxPoolSize luôn tăng khi bận.”** Sai. Pool thường xếp queue sau khi đủ core, và chỉ thử tạo thêm worker khi queue không nhận được nữa.

**“CallerRunsPolicy vẫn là async.”** Không bảo đảm. Khi bão hòa, task có thể chạy đồng bộ trên thread đang submit.

**“Nếu executor đã shutdown, CallerRunsPolicy vẫn chạy để cứu việc.”** Sai. Theo JDK, nó bỏ task khi executor đã shutdown.

**“Dùng DiscardPolicy để hệ thống không còn lỗi.”** Sai. Chỉ mất tín hiệu từ chối, và có nguy cơ làm Future chờ mãi hoặc mất dữ liệu.

**“TaskRejectedException là con trực tiếp của RuntimeException, không liên quan ngoại lệ JDK.”** Sai. Nó **kế thừa RejectedExecutionException** và có thể được bắt qua kiểu cha.

**“Chỉ bắt lỗi trong CompletableFuture.exceptionally() là đã xử lý rejection.”** Sai. Rejection ngay tại thời điểm gọi @Async có thể xảy ra **trước khi có future để gắn handler**.

**“Queue rỗng tức là executor còn khỏe.”** Sai. Với direct handoff queue bằng 0, pool có thể bão hòa và reject dù queueSize luôn 0.

**“Task submit trả về nghĩa là đã lưu job bền vững.”** Sai. Executor in-memory không phải transactional/durable job queue.

**“Mọi TaskExecutor đều hỗ trợ bốn handler chuẩn của ThreadPoolExecutor.”** Sai. API handler này thuộc JDK ThreadPoolExecutor; từng implementation Spring có cơ chế riêng.

## 16. Bài tập tự kiểm tra

**Câu 1.** Với pool core=2, max=4, queueCapacity=2, tất cả bốn worker đều bận và queue có hai task. Gửi thêm một task với AbortPolicy. Kết quả?

**Đáp án:** Không vào queue và không tạo worker được, nên task bị reject. Dưới JDK là **RejectedExecutionException**, dưới **ThreadPoolTaskExecutor** thường là **TaskRejectedException**.

**Câu 2.** Pool còn nhiều chỗ nhưng đã shutdown. Gửi task mới có được chấp nhận không?

**Đáp án:** Không. Shutdown là một lý do rejection độc lập với saturation.

**Câu 3.** AbortPolicy so với CallerRunsPolicy khác nhau ở đâu?

**Đáp án:** AbortPolicy **ném lỗi** khi không nhận task; CallerRunsPolicy **chạy task trên caller nếu executor chưa shutdown**, từ đó có thể làm caller chậm và phá ranh giới thread.

**Câu 4.** Nếu handler dùng DiscardPolicy và caller nhận một Future, có thể gọi get() vô hạn không?

**Đáp án:** Có thể, khi wrapper task bị bỏ mà Future không được hoàn tất hoặc cancel. Không dùng policy này cho công việc mà caller cần biết kết quả.

**Câu 5.** Lỗi trong phương thức @Async sau khi task đã được nhận có phải TaskRejectedException không?

**Đáp án:** Thông thường không. Đó là lỗi chạy task, được đưa vào Future/CompletableFuture theo kiểu trả về. TaskRejectedException thuộc đường không tiếp nhận task.

**Câu 6.** QueueSize=0 và số lần rejection tăng có mâu thuẫn không?

**Đáp án:** Không, nhất là với SynchronousQueue có queueCapacity=0.

**Câu 7.** Một service trả 202 Accepted sau khi executor ném TaskRejectedException. Có hợp lý không?

**Đáp án:** Không nếu 202 được hiểu là job đã được tiếp nhận, vì task thực tế chưa được nhận. Phải sửa hợp đồng tiếp nhận hoặc cơ chế lưu công việc.

**Câu 8.** Có thể bắt TaskRejectedException bằng catch(RejectedExecutionException e) không?

**Đáp án:** Có, vì TaskRejectedException kế thừa RejectedExecutionException. Trong Spring, bắt kiểu cụ thể thường giúp truyền đạt rõ mục đích.

## 17. Checklist vận hành

- [ ] Xác định đúng executor được dùng ở từng điểm submit và bean @Async.
- [ ] Biết **corePoolSize**, **maxPoolSize**, **queueCapacity** và rejection handler thực tế.
- [ ] Tính đến **shutdown** như một nguyên nhân rejection.
- [ ] Quyết định rõ hành vi **Abort**, **CallerRuns** hoặc chính sách khác trước khi đưa production.
- [ ] Nếu có Future, bảo đảm không mất task âm thầm khiến caller chờ không giới hạn.
- [ ] Ở điểm @Async, xử lý **TaskRejectedException tại submit** khác với **exceptional completion của future**.
- [ ] Giữ retry hữu hạn, có deadline, idempotency và backoff thích hợp.
- [ ] Chọn phản hồi quá tải/API status code đúng ngữ nghĩa (ví dụ 503 hoặc 429 theo nguyên nhân).
- [ ] Theo dõi pool/queue saturation, rejection, task duration và downstream resources.
- [ ] Kiểm thử bằng latch, kiểm tra cả trường hợp worker bận và executor shutdown.
- [ ] Xác định rõ những task **phải được lưu bền vững** trước khi thông báo tiếp nhận thành công.

## Kết luận

**RejectedExecutionHandler** là nơi **ThreadPoolExecutor quyết định hành động khi không nhận thêm task**; lựa chọn này có thể ném lỗi, chạy task trên caller hoặc bỏ task. **TaskRejectedException** là cách Spring báo rằng **TaskExecutor không tiếp nhận tác vụ**, và nó kế thừa **RejectedExecutionException** của Java. Với **ThreadPoolTaskExecutor**, **AbortPolicy** cùng pool/queue hữu hạn và quan sát overload là điểm khởi đầu dễ kiểm soát. Khi kết hợp **@Async**, luôn tách **lỗi ở thời điểm submit** khỏi **lỗi khi task đang chạy**, đồng thời thiết kế chính sách phản hồi/retry không làm mất công việc hoặc che giấu quá tải.

**Tài liệu API tham khảo:** [Java 17 ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html), [RejectedExecutionHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RejectedExecutionHandler.html), [Spring ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html), [Spring TaskRejectedException](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/TaskRejectedException.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overload-rejection-003">AbortPolicy báo lỗi tại điểm gửi tác vụ</a>

<details>
<summary>Xem chi tiết</summary>

# AbortPolicy báo lỗi tại điểm gửi tác vụ

## Mục tiêu và phạm vi

Khi một ứng dụng Spring đẩy công việc sang thread pool, có hai câu hỏi tách biệt: **pool đã nhận công việc chưa?** và **công việc đã thực hiện thành công chưa?** Chính sách `ThreadPoolExecutor.AbortPolicy` giải quyết câu hỏi đầu tiên: nếu pool không thể nhận task mới, nó **ném exception ngay tại điểm gửi**. Đây là tín hiệu quá tải hoặc executor đã đóng, cần được xử lý ngay ở caller.

Sau bài này, bạn có thể:

- Giải thích điều kiện `ThreadPoolExecutor` từ chối task: **pool/queue bão hòa** hoặc **shutdown**.
- Phân biệt `RejectedExecutionException` của JDK với `TaskRejectedException` khi đi qua `ThreadPoolTaskExecutor` của Spring.
- Dự đoán sự tương tác giữa `corePoolSize`, `maxPoolSize`, `queueCapacity` và rejection handler.
- Viết ví dụ Java 17+ và Spring có tình huống từ chối **xác định bằng latch**, không dựa vào `sleep()` may rủi.
- Phân biệt exception **ngay lúc `execute/submit`** với exception xảy ra **bên trong task**; lựa chọn xử lý phù hợp cho HTTP, queue và job.
- Chẩn đoán rejection, giám sát backlog và tránh các cách xử lý gây mất dữ liệu hoặc retry storm.

**Phạm vi:** `java.util.concurrent.ThreadPoolExecutor` và `org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor`. Ví dụ JDK chạy với Java 17+, ví dụ Spring dùng **Spring Framework 6.1.3** và Maven. Nội dung mô tả mô hình platform thread pool thông thường, không mặc định áp dụng cho executor tạo virtual thread cho mỗi task.

## 1. Hiểu đúng hai giai đoạn: nhận task và chạy task

Khi caller gửi một `Runnable`, executor trước hết cần **tiếp nhận (admission)** rồi mới lên lịch **thực thi (execution)**. Việc `execute(...)` trả về bình thường chỉ nói rằng task đã được nhận, **không nói task đã hoàn thành**.

| Giai đoạn và kết quả | Điều xảy ra | Caller thấy gì? |
| --- | --- | --- |
| `execute(task)` được nhận | Task được worker chạy hoặc vào queue | Phương thức gửi trả về bình thường |
| `execute(task)` bị từ chối bởi AbortPolicy | Task **không được nhận** | Ném unchecked exception ngay trong lời gọi gửi |
| `submit(callable)` được nhận | Caller có `Future`; task sẽ chạy sau | Có thể chờ bằng `Future.get()` |
| `submit(callable)` bị từ chối | Không có `Future` hợp lệ | Exception ném **trước khi `submit` trả về** |
| Task đã nhận nhưng `run()/call()` ném lỗi | Lỗi xảy ra khi chạy task | Quan sát qua `Future.get()`, callback hoặc log theo API |
| Executor đã shutdown | Không nhận task mới dù queue rỗng | AbortPolicy cũng ném exception tại điểm gửi |

`AbortPolicy` chỉ từ chối **task mới**. Nó không tự động xóa task đã nằm trong queue, không dừng task đang chạy và không rollback những side effect đã xảy ra từ các task được nhận trước đó.

### Tại sao phải bắt lỗi ở caller?

Cơ chế rejection chạy trong **luồng đang gọi `execute/submit`**, ví dụ request thread của Spring MVC hoặc thread của một scheduler. Nếu task không được chấp nhận, **phần thân Runnable chưa chạy**, nên `try/catch` đặt *bên trong* Runnable đó không thể bắt lỗi rejection của chính lời gọi gửi task.

~~~java
// Fragment trong một service có biến executor kiểu TaskExecutor.
try {
    executor.execute(() -> performWork());
    // Đã gửi thành công; chưa có nghĩa performWork() hoàn thành.
} catch (org.springframework.core.task.TaskRejectedException ex) {
    // Pool không tiếp nhận task: xử lý ngay trong caller.
    recordAdmissionFailure(ex);
}
~~~

Đây là **đoạn minh họa**, không phải Java class độc lập; giả sử ứng dụng đã có `executor`, `performWork()` và `recordAdmissionFailure(...)`. Nếu không bắt ở ranh giới phù hợp, exception sẽ tiếp tục lan lên caller/framework.

## 2. Khi nào ThreadPoolExecutor gọi RejectedExecutionHandler?

### 2.1. Quy trình nhận việc

Với `ThreadPoolExecutor` thông dụng, `execute(Runnable)` hoạt động theo các bước khái quát:

1. Khi số worker còn dưới `corePoolSize`, thử tạo worker mới để chạy task.
2. Khi đã có đủ core workers, thử đưa task vào **work queue**.
3. Nếu queue **không nhận được** task, thử tạo worker vượt core, nhưng không vượt `maximumPoolSize`.
4. Nếu việc tạo worker cũng thất bại do đã đạt giới hạn, chuyển task sang `RejectedExecutionHandler`.
5. Nếu executor đã `shutdown()`, task mới cũng bị từ chối; mã JDK có các kiểm tra trạng thái lại để xử lý race với shutdown.

~~~text
                  task mới
                     |
           executor đang nhận việc?
              /             \
            không            có
              |               |
          REJECTION       workers < core?
                           /       \
                         có         không
                         |            |
                    tạo worker     queue nhận?
                                     /     \
                                   có       không
                                   |           |
                                xếp hàng    workers < max?
                                               /     \
                                             có       không
                                              |          |
                                         tạo worker   REJECTION
~~~

Sơ đồ là mô hình để học; mã thực tế có nhiều kiểm tra lại do concurrency. Đừng dùng snapshot `getQueue().size()` để cam kết task tiếp theo sẽ được nhận.

### 2.2. Ví dụ pool có giới hạn

Giả sử `corePoolSize=1`, `maxPoolSize=2` và `queueCapacity=1`, đồng thời các task trước vẫn đang bận/chờ trong suốt lúc gửi:

| Task | Diễn biến khi chưa task nào hoàn tất |
| --- | --- |
| A | Tạo core worker thứ nhất |
| B | Vào queue một slot |
| C | Queue đã đầy, tạo worker thứ hai |
| D | Queue đầy và worker đạt max=2; bị từ chối |

Nếu A hoặc C hoàn thành trước khi D đến, D **có thể được nhận**. Đó là lý do bài thực hành sẽ giữ A bằng `CountDownLatch` thay vì hy vọng task chạy đủ chậm.

### 2.3. Queue và maximumPoolSize ảnh hưởng rejection ra sao?

`ThreadPoolTaskExecutor` mặc định có `corePoolSize=1`, `maxPoolSize=Integer.MAX_VALUE` và `queueCapacity=Integer.MAX_VALUE`. Khi queue rất lớn, những task vượt năng lực core thường bị **xếp hàng**; pool không có lý do tạo nhiều worker vượt core cho đến khi queue không nhận thêm. Kết quả là ứng dụng có thể chậm nghiêm trọng hoặc tốn tài nguyên trước khi thấy rejection do **đầy queue**.

Để đặt giới hạn có chủ đích, cấu hình **core/max/queue hữu hạn** sau khi hiểu tốc độ task đến, thời gian chạy, giới hạn CPU, kết nối database và độ trễ được chấp nhận. Với `queueCapacity=0`, Spring dùng `SynchronousQueue`: task phải được trao ngay cho một worker, không có vị trí để xếp hàng. Với `queueCapacity>0`, Spring dùng queue có dung lượng tương ứng.

## 3. AbortPolicy: chính sách fail-fast mặc định

`java.util.concurrent.RejectedExecutionHandler` là contract cho việc xử lý tác vụ không được nhận. `ThreadPoolExecutor.AbortPolicy` là handler mặc định của `ThreadPoolExecutor`; nó **luôn ném `RejectedExecutionException`** khi `rejectedExecution(...)` được gọi.

~~~java
new ThreadPoolExecutor.AbortPolicy()
~~~

Đây chỉ là **biểu thức khởi tạo handler**, không phải chương trình độc lập. `RejectedExecutionException` kế thừa `RuntimeException`, nên caller không bị trình biên dịch bắt buộc khai báo `throws`, nhưng vẫn cần chính sách xử lý rõ ràng.

Ví dụ, khi task C bị từ chối:

~~~text
Caller thread                   Thread pool
     |                              |
     | execute(task C)              | worker đang chạy A
     | ---------------------------> | queue đang chứa B
     |                              |
     | <--- RejectedExecutionException
     |
     +-- catch trong caller
          C chưa được nhận
          A và B không tự bị hủy
~~~

**“Abort” ở đây không có nghĩa hủy toàn bộ pool**: nó ném lỗi cho lời gọi gửi **task đang bị từ chối**. Các task được nhận từ trước vẫn hoạt động theo vòng đời riêng, trừ khi có thao tác shutdown/cancellation khác.

Có hai nhóm nguyên nhân chính:

- **Bão hòa:** queue không nhận thêm và worker đã đến giới hạn; task mới bị từ chối.
- **Executor ngừng nhận việc:** `shutdown()`, quá trình đóng Spring context hoặc vòng đời executor khiến task mới không được nhận, ngay cả khi queue còn trống.

Không thể kết luận cứ gặp `RejectedExecutionException` là phải tăng `maxPoolSize`: trước hết phải xác minh executor đang shutdown hay bão hòa, hàng đợi có tăng hay không và downstream có phải nút thắt thực hay không.

## 4. Ví dụ Java 17+ độc lập: rejection xảy ra ngay tại execute()

Ví dụ sau dùng một worker và một slot queue. Một `CountDownLatch` xác nhận task A đã bắt đầu, latch còn lại giữ A đang chờ. Sau đó task B chiếm queue. Do đó task C chắc chắn bị từ chối tại thời điểm nộp.

Lưu thành `AbortPolicyDemo.java`:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class AbortPolicyDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy()
        );

        try {
            pool.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });

            started.await(); // A đang giữ worker duy nhất

            pool.execute(() -> { }); // B vào queue

            try {
                pool.execute(() -> { }); // C bị từ chối
                System.out.println("unexpected-accepted");
            } catch (RejectedExecutionException ex) {
                System.out.println("jdk-rejected="
                        + ex.getClass().getSimpleName());
            }

            System.out.println("queue=" + pool.getQueue().size());
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

Chạy bằng JDK 17+:

~~~bash
javac AbortPolicyDemo.java
java AbortPolicyDemo
~~~

Đầu ra xác định:

~~~text
jdk-rejected=RejectedExecutionException
queue=1
~~~

**Vì sao kết quả xác định?**

1. `started.await()` bảo đảm A đã chạy và báo đã bắt đầu.
2. A đang chờ `release`, nên worker duy nhất chưa thể xử lý task mới.
3. B chiếm đúng slot queue duy nhất.
4. C không vào được queue và không được tạo thêm worker (core=max=1), khiến `AbortPolicy` ném exception ngay trong **thread main**.
5. C không làm mất B; khi main in `queue=1`, B vẫn chờ.
6. `finally` mở latch, shutdown pool, đợi kết thúc để không để lại worker bị kẹt.

Nếu bỏ latch và chỉ cho A gọi `Thread.sleep(100)`, kết quả sẽ phụ thuộc lịch chạy và tốc độ máy, không phù hợp làm kiểm thử ổn định.

## 5. Trong Spring, TaskRejectedException là lỗi cần bắt

`ThreadPoolTaskExecutor` quản lý một `ThreadPoolExecutor` nội bộ và thực thi `TaskExecutor` của Spring. Handler mặc định vẫn là **AbortPolicy**. Spring chuyển exception không nhận task theo contract thành `org.springframework.core.task.TaskRejectedException`. `TaskRejectedException` là một lớp con của `RejectedExecutionException`.

| Lời gọi | Khi bị từ chối với AbortPolicy |
| --- | --- |
| `ThreadPoolExecutor.execute(...)` từ JDK | `RejectedExecutionException` |
| `ThreadPoolTaskExecutor.execute(...)` từ Spring | `TaskRejectedException` |
| `ThreadPoolTaskExecutor.submit(...)` | `TaskRejectedException` trước khi trả `Future` |
| `AsyncTaskExecutor.submitCompletable(...)` (Spring 6.0+) | `TaskRejectedException` trước khi trả `CompletableFuture` |

Với API Spring, nên catch `TaskRejectedException` vì nó thể hiện hợp đồng Spring và giúp code phân biệt rejection từ Spring với lỗi được ném ở tầng khác. Catch lớp cha `RejectedExecutionException` vẫn bắt được, nhưng có thể làm mờ nguồn lỗi.

### 5.1. Dự án Maven Spring chạy độc lập

Để kiểm chứng chính exception của Spring, tạo một dự án nhỏ với **Java 17+** và **Spring Framework 6.1.3**. Chương trình chạy bằng `main`, không cần mở Spring Boot server và không cần `@EnableAsync` vì gọi trực tiếp `executor.execute(...)`.

Cấu trúc:

~~~text
spring-abort-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── SpringAbortDemo.java
~~~

Tệp `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-abort-demo</artifactId>
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
                    <mainClass>demo.SpringAbortDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Tệp `src/main/java/demo/SpringAbortDemo.java`:

~~~java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public final class SpringAbortDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setQueueCapacity(1);
        pool.setThreadNamePrefix("abort-demo-");
        pool.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        pool.initialize();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            pool.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });

            started.await(); // A đang giữ worker
            pool.execute(() -> { }); // B nằm trong queue

            try {
                pool.execute(() -> { }); // C bị từ chối
                System.out.println("unexpected-accepted");
            } catch (TaskRejectedException ex) {
                System.out.println("spring-rejected="
                        + ex.getClass().getSimpleName());
            }

            System.out.println("queue=" + pool.getQueueSize());
        } finally {
            release.countDown();
            ThreadPoolExecutor nativePool =
                    pool.getThreadPoolExecutor();
            pool.shutdown();
            if (!nativePool.awaitTermination(5, TimeUnit.SECONDS)) {
                nativePool.shutdownNow();
            }
        }
    }
}
~~~

Chạy trong thư mục `spring-abort-demo`:

~~~bash
mvn -q compile exec:java
~~~

Đầu ra do chương trình Java in, không tính log riêng từ Maven nếu có:

~~~text
spring-rejected=TaskRejectedException
queue=1
~~~

Spring nhận cùng tình huống worker bận và queue đầy, nhưng báo `TaskRejectedException` ở lời gọi `ThreadPoolTaskExecutor.execute(...)`. Ta in **tên lớp exception** thay vì toàn bộ `getMessage()`, vì nội dung thông báo có thể thay đổi giữa phiên bản. Việc in `queue=1` xác nhận task B vẫn nằm trong queue khi C bị từ chối.

`setRejectedExecutionHandler(new AbortPolicy())` ở đây chỉ làm rõ chính sách; đó đã là mặc định nếu bạn không thay handler. `initialize()` cần thiết vì pool được tạo thủ công. Trong ứng dụng Spring, hãy dùng **bean singleton** và để container quản lý vòng đời.

### 5.2. Cấu hình làm bean Spring

Trong một ứng dụng Spring đã bật component scanning, có thể cấu hình executor dùng chung như sau (đây là **fragment tích hợp**, không phải dự án độc lập):

~~~java
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BoundedPoolConfig {
    @Bean("boundedExecutor")
    public ThreadPoolTaskExecutor boundedExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(4);
        pool.setMaxPoolSize(8);
        pool.setQueueCapacity(100);
        pool.setThreadNamePrefix("bounded-");
        pool.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return pool;
    }
}
~~~

Các số `4/8/100` là ví dụ về giới hạn hữu hạn, **không phải cấu hình tối ưu mặc định**. Nếu dùng `@Async("boundedExecutor")`, Spring cần async method processing (ví dụ `@EnableAsync`) và lời gọi phải đi qua proxy; tự gọi method bên trong cùng object có thể bỏ qua proxy. Khi executor không nhận được task tại thời điểm proxy gửi, rejection có thể nổi lên từ **lời gọi method của caller**, nên tầng gọi cần có chiến lược xử lý.

## 6. Rejection khi submit và lỗi chạy task là hai luồng khác nhau

`ThreadPoolTaskExecutor.submit(...)` trả `Future` chỉ **khi task đã được nhận**. Nếu AbortPolicy từ chối, `submit` ném `TaskRejectedException` **ngay lúc gọi**. Sau khi task được chấp nhận, lỗi do `Callable.call()` mới được quan sát qua `Future.get()`.

~~~java
// Fragment trong service có asyncExecutor kiểu AsyncTaskExecutor
// và các phương thức reportSubmissionFailure, reportTaskFailure.
Future<Integer> future;
try {
    future = asyncExecutor.submit(() -> calculate());
} catch (TaskRejectedException ex) {
    reportSubmissionFailure(ex);
    return;
}

try {
    Integer result = future.get();
    System.out.println(result);
} catch (ExecutionException ex) {
    reportTaskFailure(ex.getCause());
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
}
~~~

Đây là **đoạn minh họa bên trong một method trả `void`**; cần import `Future`, `ExecutionException`, `InterruptedException`, `TaskRejectedException` và có biến/hàm của service. `future.get()` có thể chặn thread caller; không gọi nó tùy tiện trong worker đang phụ thuộc vào cùng pool nhỏ vì có thể tạo starvation/deadlock. Lỗi được ném trong task **không quay lại catch tại điểm `submit`**.

Với Spring 6.0+ có `AsyncTaskExecutor.submitCompletable(...)` trả `CompletableFuture`. Các stage `exceptionally(...)` hoặc `handle(...)` xử lý **future đã trả về**; nếu việc nộp bị từ chối trước khi trả future, callback **chưa tồn tại**. Vì vậy vẫn phải bọc lời gọi `submitCompletable` bằng `try/catch(TaskRejectedException)`, rồi mới nối callback cho lỗi thực thi.

### Khi bị từ chối, task có “biến mất” không?

Với AbortPolicy, task bị từ chối **không nằm trong queue** và không được chạy. Nếu ứng dụng nuốt exception, đó là **mất công việc ở tầng nghiệp vụ** do code bỏ qua lỗi. Nếu công việc phải được xử lý chắc chắn, bạn cần giao thức nhận việc có lưu bền vững (ví dụ durable queue hoặc transactional outbox), acknowledgement rõ ràng và cơ chế retry hợp lệ. Executor trong bộ nhớ không thay thế một hệ thống đảm bảo giao hàng.

Không báo HTTP “đã nhận để xử lý” khi việc gửi executor đã thất bại, trừ khi một thành phần bền vững khác thực sự đã ghi nhận công việc.

## 7. Xử lý quá tải đúng vị trí

Tùy trách nhiệm của API, ứng dụng có thể:

- **Fail fast** với phản hồi rõ cho caller. HTTP 503 thường phù hợp với tình trạng service tạm thiếu năng lực; HTTP 429 thường phù hợp hơn với hạn mức theo client. Không tự động chuyển mọi `TaskRejectedException` thành một mã cố định khi chưa xét hợp đồng API.
- **Retry hữu hạn** với exponential backoff/jitter khi task có thể retry an toàn và dữ liệu đầu vào còn hiệu lực. Tránh retry tức thì liên tục ngay trong catch vì có thể tạo retry storm.
- **Giảm tốc đầu vào** bằng semaphore, rate limiter, admission control hoặc backpressure ở upstream trước khi hàng đợi phình lớn.
- **Dời công việc sang durable queue** nếu không được phép mất task và caller có thể chờ xử lý sau; cần thiết kế idempotency và xác nhận enqueue.
- **Sửa lifecycle** nếu pool đã shutdown, chẳng hạn task scheduler vẫn nộp việc trong khi Spring context đang đóng.

Không phải cứ tăng `maxPoolSize` là xử lý được quá tải: task có thể đang chờ DB connection hoặc external API, nên tăng số worker chỉ làm nhiều tác vụ cạnh tranh cùng tài nguyên hơn.

### So sánh các chính sách từ chối

| Chính sách | Hành vi khi bị từ chối | Đánh đổi |
| --- | --- | --- |
| **AbortPolicy** | Ném `RejectedExecutionException` ngay tại caller | Rõ ràng, dễ fail fast; caller phải có kế hoạch xử lý |
| `CallerRunsPolicy` | Chạy task trên **caller thread** nếu executor chưa shutdown | Tạo backpressure gián tiếp nhưng có thể chặn HTTP/event-loop thread; khi shutdown có thể bỏ task |
| `DiscardPolicy` | Bỏ task mà không ném lỗi | Dễ gây mất tác vụ âm thầm |
| `DiscardOldestPolicy` | Loại task cũ nhất trong queue rồi thử nhận task mới | Có nguy cơ mất task và phá vỡ kỳ vọng thứ tự, đặc biệt nguy hiểm khi có Future |

Đừng thay `AbortPolicy` bằng `DiscardPolicy` chỉ để “không còn exception”: điều đó biến một sự cố quan sát được thành mất công việc không dễ phát hiện. Với `CallerRunsPolicy`, phải kiểm tra chi phí chạy task trên caller và cách xử lý khi executor đã shutdown.

Nếu dùng custom `RejectedExecutionHandler` để tăng counter, hãy **vẫn ném rejection** khi muốn giữ nghĩa fail fast; handler chỉ ghi log rồi return bình thường có thể khiến phía gọi hiểu nhầm rằng task được nhận.

## 8. Giám sát và kiểm thử

### 8.1. Các số liệu nên theo dõi

- **Tỉ lệ rejected task:** ghi tại catch `TaskRejectedException` hoặc handler có metric; phân biệt rejection do quá tải và shutdown khi có đủ ngữ cảnh.
- **Active workers / pool size:** `getActiveCount()`, `getPoolSize()` chỉ là snapshot.
- **Queue depth / wait duration:** `getQueueSize()` và thời gian chờ hàng đợi; queue tăng liên tục là tín hiệu backlog.
- **Task duration / throughput / p95-p99 latency:** cho biết bottleneck do task chậm hoặc lưu lượng vượt khả năng xử lý.
- **Tình trạng downstream:** DB connection pool, socket, API quota, CPU, memory và GC.

Không viết logic kiểu “đọc queue đang 0 rồi chắc chắn submit sẽ thành công”, vì các thread khác có thể đổi trạng thái ngay sau lần đọc. Chính lời gọi `execute/submit` và handler là cơ chế quyết định việc nhận task.

### 8.2. Các trường hợp kiểm thử cần có

1. **Saturation:** core=max=1, queue=1; dùng latch giữ task đầu, gửi task thứ hai vào queue và xác nhận task thứ ba bị từ chối ngay khi submit.
2. **Normal acceptance:** không đầy queue, gọi `execute` thành công; theo dõi task hoàn tất riêng.
3. **Shutdown:** gọi `shutdown()` rồi gửi task mới; kiểm tra lỗi rejection ngay dù queue rỗng.
4. **Task failure:** nộp `Callable` được nhận nhưng ném exception; kiểm tra `Future.get()` trả `ExecutionException`, không nhầm với rejection.
5. **Đường Spring:** xác nhận catch `TaskRejectedException` ở chỗ gọi `ThreadPoolTaskExecutor.execute` thay vì chỉ đợi log từ worker.
6. **Recovery:** quá tải giảm thì task gửi mới có thể tiếp tục được nhận, miễn pool chưa shutdown; không retry vô hạn khi điều kiện chưa thay đổi.

Dùng latch/barrier và giới hạn rõ ràng cho kiểm thử thay vì `Thread.sleep(100)`; việc phụ thuộc tốc độ CPU có thể làm test thỉnh thoảng thất bại trên CI.

## 9. Lỗi thường gặp và cách sửa

1. **Catch exception bên trong task bị từ chối.** Không hiệu quả vì task không chạy; bắt quanh `execute/submit` tại caller.
2. **Cho rằng submit luôn trả Future kể cả khi quá tải.** Không đúng: rejection có thể ném trước khi Future được trả.
3. **Chỉ xử lý `CompletableFuture.exceptionally`.** Không bắt được lời gọi `submitCompletable` đã ném exception đồng bộ.
4. **Nghĩ AbortPolicy dừng cả executor.** Nó từ chối task hiện tại; các task đã nhận vẫn tiếp tục theo lifecycle.
5. **Cấu hình queue gần vô hạn và mong `maxPoolSize` tăng worker nhanh.** Pool ưu tiên queue sau khi đạt core, nên max có thể không được dùng như kỳ vọng.
6. **Tăng pool vô hạn để tránh rejection.** Có thể đẩy giới hạn sang DB, memory hoặc network; cần điều tiết tải và đo năng lực thực.
7. **Nuốt TaskRejectedException rồi trả “đã nhận”.** Caller sẽ mất thông tin task chưa được xử lý.
8. **Đổi sang DiscardPolicy để loại bỏ lỗi.** Task bị mất mà không có tín hiệu; chỉ dùng khi nghiệp vụ thực sự cho phép bỏ task.
9. **Retry ngay trong vòng lặp không giới hạn.** Có thể khiến tình trạng quá tải tự khuếch đại.
10. **Đồng nhất rejection do shutdown với bão hòa.** Cần kiểm tra lifecycle và thông tin tại thời điểm xảy ra sự cố.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** Pool `core=1,max=1,queue=1` đang chạy A và xếp hàng B. Task C gửi vào sẽ ra sao?  
**Trả lời:** C không vào queue và không tạo thêm worker được, vì vậy AbortPolicy ném exception ngay tại lời gọi gửi, nếu A vẫn đang chiếm worker.

**Câu 2.** Tại sao `try/catch` trong thân của C không thể xử lý rejection?  
**Trả lời:** C chưa được nhận nên thân C chưa hề chạy.

**Câu 3.** Spring `ThreadPoolTaskExecutor` sử dụng AbortPolicy. Caller nên bắt exception nào?  
**Trả lời:** `TaskRejectedException` theo hợp đồng Spring; lớp này kế thừa `RejectedExecutionException` của Java.

**Câu 4.** `submit` đã trả `Future`, nhưng task ném `IllegalStateException` khi chạy. Đây là rejection?  
**Trả lời:** Không. Executor đã nhận task; lỗi thực thi được quan sát qua `Future.get()` dưới dạng `ExecutionException` có cause là lỗi gốc.

**Câu 5.** Executor đã shutdown nhưng queue không đầy. AbortPolicy có thể từ chối task mới không?  
**Trả lời:** Có. Shutdown là một nguyên nhân rejection độc lập với saturation.

**Câu 6.** Vì sao thay AbortPolicy bằng DiscardPolicy có thể gây rủi ro nghiệp vụ?  
**Trả lời:** Task mới có thể bị bỏ mà caller không được báo lỗi; hệ thống dễ ghi nhận nhầm công việc đã nhận.

## Tham khảo chính thức

- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html): quy tắc core/queue/max, shutdown, rejected tasks và các handler.
- [Java SE 17 — ThreadPoolExecutor.AbortPolicy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.AbortPolicy.html): handler mặc định luôn ném `RejectedExecutionException`.
- [Spring Framework 6.1.3 — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): cấu hình executor, mặc định core/max/queue, exception theo hợp đồng Spring.
- [Spring Framework 6.1.3 — TaskExecutor](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/core/task/TaskExecutor.html): phương thức `execute(Runnable)` và `TaskRejectedException`.
- [Spring Framework 6.1.3 — AsyncTaskExecutor](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/core/task/AsyncTaskExecutor.html): cách `submit` và `submitCompletable` trả kết quả và báo lỗi khi bị từ chối.
- [Spring Framework 6.1.3 — TaskRejectedException](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/core/task/TaskRejectedException.html): exception Spring khi không nhận task.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overload-rejection-004">CallerRunsPolicy chuyển công việc sang luồng gọi và thay đổi độ trễ</a>

<details>
<summary>Xem chi tiết</summary>

# CallerRunsPolicy chuyển công việc sang luồng gọi và thay đổi độ trễ

Trong ứng dụng Spring, `ThreadPoolTaskExecutor` thường dùng một nhóm worker thread để xử lý công việc nền. Tuy nhiên, khi pool đã bận và hàng đợi đầy, một tác vụ mới có thể bị từ chối. `java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy` là một chiến lược xử lý tình huống đó: **nếu executor chưa shutdown, thread đang gọi `execute()` sẽ tự thực thi tác vụ bị từ chối**.

Kết quả rất đáng chú ý: lời gọi vốn được kỳ vọng chỉ “gửi việc” có thể trở thành **lời gọi đồng bộ, chờ đến khi công việc kết thúc**. Nếu caller là thread xử lý HTTP request, thời gian phản hồi của chính request đó tăng theo thời gian chạy tác vụ. Nếu caller là thread chuyên sinh việc, tốc độ sinh việc tự giảm lại. Cơ chế sau được gọi là **backpressure bằng caller**, nhưng chỉ phù hợp khi chấp nhận những hệ quả đối với latency, thread context và hợp đồng xử lý task.

Một giới hạn phải ghi nhớ ngay: **nếu executor đã shutdown, `CallerRunsPolicy` bỏ tác vụ và không ném lỗi**. Vì vậy không được xem mọi lần `execute()` trả về bình thường như một bằng chứng tác vụ đã được nhận để xử lý sau này.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích lúc nào `ThreadPoolExecutor` chuyển sang `RejectedExecutionHandler` và khi nào `CallerRunsPolicy` thực thi task trên caller thread.
- Phân biệt **worker thread**, **caller thread**, **queue** và **task đang chạy**; hiểu vì sao `maximumPoolSize` không hạn chế tổng số task có thể đồng thời chạy trên các caller.
- Phân tích tác động lên độ trễ `execute()`, thời gian đáp ứng HTTP, throughput, p95/p99 và chu kỳ sinh việc.
- Cấu hình `CallerRunsPolicy` với Spring `ThreadPoolTaskExecutor` và hiểu tác động khi dùng `@Async`, `Future` hoặc `CompletableFuture`.
- Nhận diện các rủi ro: chạy trong transaction/request context, blocking event-loop, deadlock/reentrancy, lỗi bị đẩy lên caller và task bị bỏ im lặng lúc shutdown.
- Viết kiểm thử Java 17+ không phụ thuộc timing để chứng minh nhiệm vụ bị đẩy sang caller.
- Chọn chính sách overload và cách quan sát phù hợp với yêu cầu nghiệp vụ.

## 2. Vì sao một executor cần chính sách từ chối?

`ThreadPoolExecutor` của Java có ít nhất ba yếu tố liên quan đến sức chứa:

1. `corePoolSize`: mức worker cốt lõi.
2. `maximumPoolSize`: số worker tối đa **thuộc về pool**.
3. `BlockingQueue<Runnable>`: nơi lưu công việc đã nhận nhưng chưa được worker xử lý.

Với cấu hình thông thường, trình tự xử lý `execute(task)` như sau:

1. Nếu số worker còn dưới `corePoolSize`, thử tạo worker để thực hiện task.
2. Nếu đã đạt core, thử thêm task vào queue.
3. Nếu queue không nhận thêm và số worker còn dưới `maximumPoolSize`, thử tạo thêm worker.
4. Nếu cả các phương án đó không thể nhận task, hoặc executor đang shutdown, gọi `RejectedExecutionHandler`.

Đây là mô tả đường quyết định của `ThreadPoolExecutor`, không phải hợp đồng cho mọi loại `Executor`. Trong khi xử lý một lời gọi, worker khác vẫn có thể hoàn thành task hoặc lấy việc khỏi queue, nên tình trạng bão hòa là một điều kiện có thể thay đổi rất nhanh.

Ví dụ `core=2`, `max=4`, queue chứa 3 phần tử: khi hai worker đều bận, ba job mới có thể vào queue; sau đó pool có thể tạo thêm hai worker; nếu bốn worker bận và ba chỗ queue đều đã dùng, một task mới sẽ phải đi vào rejection handler. Không phải cứ `getActiveCount()==max` là chắc chắn từ chối, vì queue có thể vẫn còn sức chứa.

### Hàng đợi cần được giới hạn có chủ đích

`ThreadPoolTaskExecutor` trong Spring có mặc định `corePoolSize=1`, `maxPoolSize=Integer.MAX_VALUE` và `queueCapacity=Integer.MAX_VALUE`. Với queue rất lớn, công việc thường xếp hàng trước khi pool cần mở rộng vượt core; độ trễ có thể ngày càng tăng trong khi chính sách từ chối chưa hề được kích hoạt.

Muốn đánh giá `CallerRunsPolicy` khi quá tải, trước hết phải xác định **capacity hữu hạn**, tốc độ producer, tốc độ consumer, chi phí của task và thời gian đáp ứng tối đa được phép. Không tăng queue tùy tiện chỉ để tránh thấy tín hiệu quá tải.

## 3. Hợp đồng chính xác của CallerRunsPolicy

`CallerRunsPolicy` là lớp lồng trong `ThreadPoolExecutor`, triển khai `RejectedExecutionHandler`. Khi handler được gọi:

| Trạng thái executor | Hành vi |
| --- | --- |
| Executor chưa shutdown và đang từ chối task do bão hòa | Gọi `run()` của tác vụ ngay trong thread đang gọi `execute()` |
| Executor đã shutdown | **Bỏ tác vụ**; không tự chạy và không ném `RejectedExecutionException` |
| Pool còn chỗ và `execute()` nhận task bình thường | Handler không được gọi; task chạy theo cơ chế worker/queue |

Có thể hình dung nhánh xử lý đặc trưng:

~~~java
// Mô hình khái niệm của rejectedExecution(), KHÔNG phải
// lời khuyên tự thay thế mã thư viện JDK.
if (!executor.isShutdown()) {
    runnable.run(); // chạy trong chính thread đã gọi execute()
}
// Khi shutdown: không làm gì.
~~~

Điểm cần nhấn mạnh:

- `CallerRunsPolicy` không tạo một worker thread mới.
- Nó không đặt tác vụ bị từ chối lại vào queue.
- Nó không gửi tác vụ sang một executor khác.
- Nó không hứa luôn thực thi tác vụ: **shutdown là ngoại lệ quan trọng**.
- Khi task chạy trực tiếp, lời gọi `execute()` thường chỉ trả về **sau khi `run()` kết thúc**. Nếu `run()` ném unchecked exception thì exception có thể thoát ra trên caller thread.
- Chính sách này không tự retry task lỗi, không cung cấp lưu trữ bền vững và không cam kết thứ tự thực thi với những task đã có trong queue.

Vì sao lại gọi là **caller-runs**? “Caller” là thread thực sự thực hiện lời gọi `execute()` trong tình huống đó; không nhất thiết là HTTP request thread. Trong batch producer, đó có thể là một producer thread; trong một worker đang tiếp tục gửi việc, đó có thể là worker của pool khác hoặc chính pool hiện tại.

## 4. Backpressure: producer bị buộc làm việc thay cho consumer

Giả sử một producer gọi:

~~~java
executor.execute(job);
createNextJob();
~~~

Khi executor còn sức chứa, `execute(job)` thường trả về tương đối nhanh để producer tiếp tục tạo việc. Khi pool bão hòa và handler là `CallerRunsPolicy`, `job.run()` chạy ngay trên producer thread. Producer **không thể đi tới `createNextJob()` trước khi job hoàn thành**.

Đây là cơ chế phản hồi bằng thời gian: tốc độ gửi việc giảm tự nhiên khi hệ thống thiếu năng lực xử lý. Trong khi producer bận làm job, worker có thể lấy bớt phần tử khỏi queue, tạo chỗ cho những lần submit tiếp theo. JDK gọi đây là một cơ chế feedback control đơn giản.

Nhưng cần phân biệt **giảm tốc producer** với **giới hạn concurrency toàn hệ thống**:

- `maximumPoolSize=4` chỉ giới hạn số worker mà **pool** có thể tạo; nó không cấm nhiều request thread cùng chạy task riêng do CallerRuns.
- Nếu có 30 request thread đồng thời submit vào một pool 4 worker đang bão hòa, một số request thread có thể đồng thời tự chạy các task bị từ chối. **Tổng số task đang thực thi có thể vượt quá bốn**.
- Với task dùng nhiều CPU hoặc kết nối database, cơ chế này có thể chuyển tình trạng quá tải sang web thread pool và dependency bên ngoài. Nó không phải một semaphore kiểm soát tổng số task toàn ứng dụng.

Do đó CallerRuns là một cách tạo áp lực ngược lên **nguồn gửi** chứ không phải bộ giới hạn tài nguyên tuyệt đối. Khi cần concurrency cap chung, cân nhắc bounded admission, semaphore hoặc hàng đợi bền vững có số worker được kiểm soát.

## 5. Ví dụ Java 17+ chứng minh việc chạy trên caller

Ta tạo pool chỉ có **một worker** và queue sức chứa **một task**. Task thứ nhất giữ worker bằng `CountDownLatch`; task thứ hai chiếm chỗ queue. Khi main thread gửi task thứ ba, `CallerRunsPolicy` phải chạy task đó ngay trên main thread.

Ví dụ không dùng `Thread.sleep()` để hy vọng pool bão hòa: các latch xác định chính xác trạng thái. Đồng thời, ví dụ kiểm tra trường hợp executor đã shutdown.

**Tệp `CallerRunsDemo.java`:**

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class CallerRunsDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch queuedFinished = new CountDownLatch(1);

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.CallerRunsPolicy());

        try {
            pool.execute(() -> {
                workerStarted.countDown();
                try {
                    releaseWorker.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });

            if (!workerStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker did not start");
            }

            // Worker vẫn chờ releaseWorker nên job này phải nằm trong queue.
            pool.execute(queuedFinished::countDown);

            Thread submittingThread = Thread.currentThread();
            AtomicReference<Thread> executedBy = new AtomicReference<>();
            List<String> order = new ArrayList<>();

            order.add("before");
            pool.execute(() -> {
                executedBy.set(Thread.currentThread());
                order.add("inCaller");
            });
            order.add("after");

            System.out.println("onCaller="
                    + (executedBy.get() == submittingThread));
            System.out.println("order=" + String.join(">", order));

            releaseWorker.countDown();
            if (!queuedFinished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Queued job did not finish");
            }
            System.out.println("queuedFinished=true");
        } finally {
            releaseWorker.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }

        // Chính sách này im lặng bỏ task sau khi executor đã shutdown.
        ThreadPoolExecutor stopped = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.CallerRunsPolicy());

        stopped.shutdown();
        AtomicBoolean ran = new AtomicBoolean(false);
        stopped.execute(() -> ran.set(true));
        Future<Integer> future = stopped.submit(() -> 42);

        System.out.println("shutdownTaskRan=" + ran.get());
        System.out.println("futureDoneAfterDiscard=" + future.isDone());
        future.cancel(false);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac CallerRunsDemo.java
java CallerRunsDemo
~~~

**Stdout của lần chạy thành công:**

~~~text
onCaller=true
order=before>inCaller>after
queuedFinished=true
shutdownTaskRan=false
futureDoneAfterDiscard=false
~~~

Giải thích:

1. `workerStarted.await()` chỉ tiếp tục khi worker thứ nhất đã bắt đầu và đang chờ `releaseWorker`.
2. Task thứ hai chiếm slot duy nhất của queue, vì worker chưa thể lấy task đó.
3. Task thứ ba không thể vào pool/queue, nên `CallerRunsPolicy` chạy nó trên chính `submittingThread`. Vì vậy `onCaller=true`.
4. Chuỗi `before>inCaller>after` chứng minh **`execute()` của task thứ ba chưa trả về trước khi phần thân task chạy xong**.
5. Sau `releaseWorker.countDown()`, worker xử lý task thứ hai và `queuedFinished.await()` chứng minh nó hoàn tất.
6. Với executor khác đã shutdown, `execute()` bỏ task; `shutdownTaskRan=false`. `submit()` vẫn có thể trả một `Future` chưa hoàn thành bởi vì task chứa trong `FutureTask` đã bị bỏ. Ta gọi `cancel(false)` để không để lại một future có thể bị chờ vô hạn.

**Vì sao đoạn này không race ở phần `List<String> order`?** Chỉ main thread chỉnh sửa danh sách trong ba vị trí `before`, `inCaller`, `after`: trong tình huống đang kiểm thử, phần thân task thứ ba cũng chạy trên main thread. Không nên suy luận rằng `ArrayList` an toàn với mọi trường hợp executor; trong bài toán thực tế phải dùng đồng bộ hoặc collection phù hợp nếu task có thể chạy trên worker.

Một điều kiện thiết yếu của bài kiểm thử là **không thả `releaseWorker` trước khi gửi task thứ ba**. Nếu worker có thời gian lấy task thứ hai ra khỏi queue, policy có thể không được kích hoạt và test không còn chứng minh nhánh caller-runs.

## 6. CallerRunsPolicy thay đổi độ trễ như thế nào?

### Độ trễ của lời gọi submit

Gọi `T_submit` là thời gian từ lúc bắt đầu tới lúc `execute(task)` trả về. Khi pool còn chỗ, `T_submit` thường gần với chi phí kiểm tra/đưa việc vào queue hoặc tạo worker; **không bao gồm toàn bộ thời gian chạy task**. Khi `CallerRunsPolicy` kích hoạt, công việc xảy ra trên caller nên:

~~~text
T_submit ≈ T_kiểm_tra_pool + T_task_trực_tiếp
~~~

Đây chỉ là mô hình gần đúng. `T_task_trực_tiếp` còn chứa thời gian CPU, I/O, chờ lock, GC, scheduling và các lệnh gọi phụ thuộc. Nếu task nặng hoặc bị block, `execute()` có thể block rất lâu, kể cả quá deadline của request.

### Tác động tới request HTTP

Giả sử một API mất khoảng 20 ms để validate và chuẩn bị response, sau đó gửi một công việc tạo báo cáo có thời gian xử lý khoảng 400 ms vào executor.

- Khi executor còn chỗ, request có thể trả kết quả về việc **đã tiếp nhận theo hợp đồng hiện tại** mà không chờ báo cáo chạy xong.
- Khi pool bão hòa và caller-runs kích hoạt, request thread có thể làm việc thêm khoảng 400 ms trước khi hoàn tất lời gọi submit. Độ trễ phản hồi có thể từ mức vài chục ms lên hàng trăm ms hoặc cao hơn.
- Nếu task gọi DB hay HTTP bên ngoài, thời gian có thể biến động rất lớn, khiến p95/p99 tăng mạnh dưới tải.

Các số 20 ms và 400 ms chỉ là **ví dụ minh họa cơ chế**, không phải cam kết đo đạc hoặc định luật cộng thời gian. Nếu deadline HTTP đã hết nhưng caller vẫn đang làm việc, server vẫn có thể tiêu tốn tài nguyên sau khi client ngừng chờ.

**Tính chất quan trọng:** cơ chế thường xuất hiện **chỉ ở giai đoạn bão hòa**, nên hệ thống có thể trông rất nhanh trong kiểm thử tải thấp rồi đột ngột có đuôi độ trễ dài trong production. Đừng đo mỗi p50.

### Hai loại latency cần phân biệt

| Chỉ số | Khi pool còn chỗ | Khi CallerRuns kích hoạt |
| --- | --- | --- |
| Thời gian gọi `execute()` | Thường ngắn | Bao gồm thời gian thực thi task trên caller |
| Thời gian đợi trong queue của task bị reject | Task khác có thể phải đợi | Task caller-runs không phải xếp vào queue đó |
| Request response latency | Ít chịu ảnh hưởng trực tiếp từ body task nếu async thật | Có thể tăng theo toàn bộ thời gian task |
| Tốc độ producer gửi việc mới | Có thể nhanh | Chậm lại vì producer thực hiện task |
| Số task chạy bên ngoài worker của pool | Thường bằng 0 | Có thể tăng theo số caller bị điều hướng |
| p95/p99 và khả năng đáp ứng deadline | Phụ thuộc queue và task | Có nguy cơ tăng đột ngột đúng lúc quá tải |

## 7. Ví dụ phân tích tải và backpressure

Giả sử một dịch vụ có `core=2`, `max=4`, queue capacity 8 và mỗi job mất trung bình 250 ms khi giữ một worker. Nếu bốn worker đều bận và hàng đợi đầy, task tiếp theo chuyển sang caller; producer phải tiêu tốn khoảng thời gian tương ứng để xử lý nó.

Trong điều kiện lý tưởng đơn giản, bốn worker mỗi worker xử lý một tác vụ trong 250 ms có thể hoàn tất khoảng 16 job/giây; đây **không phải dự báo production** vì còn contention, I/O, jitter và thay đổi kích thước worker. Nếu upstream liên tục đẩy 80 job/giây, backlog có thể đầy nhanh, khiến nhiều caller cùng làm việc.

Vì vậy có hai hiệu ứng đồng thời:

1. **Phản hồi âm lên tốc độ gửi của từng caller:** request/producer đó không gửi tiếp cho đến khi task nó đang làm xong.
2. **Gia tăng tổng thread tham gia thực thi task:** khi có nhiều caller, nhiều task chạy ngoài worker pool; quá tải có thể lan sang HTTP threads, scheduler hoặc database.

Một producer chạy vòng lặp tuần tự có thể được điều tiết tốt bởi CallerRuns. Một HTTP server có hàng trăm request thread độc lập có thể biến các request thread thành nguồn parallelism bổ sung không mong muốn. Phải đánh giá **topology của caller**, chứ không chỉ cấu hình pool.

Nếu thực sự cần tổng concurrency cố định, hãy dùng giới hạn admission riêng như semaphore/queue bounded, hoặc chuyển sang hàng đợi và worker độc lập với số lượng consumer cố định.

## 8. So sánh CallerRunsPolicy với các chính sách còn lại

`ThreadPoolExecutor` cung cấp một số `RejectedExecutionHandler` chuẩn:

| Chính sách | Khi bão hòa và chưa shutdown | Ảnh hưởng nổi bật |
| --- | --- | --- |
| `AbortPolicy` (mặc định) | Ném `RejectedExecutionException` | Caller biết không được nhận; có thể trả lỗi hoặc fallback |
| `CallerRunsPolicy` | Thực thi task trên caller | Không mất task do bão hòa trong nhánh này, nhưng làm caller chờ |
| `DiscardPolicy` | Bỏ task im lặng | Nhanh nhưng có nguy cơ mất việc mà hệ thống không nhận biết |
| `DiscardOldestPolicy` | Bỏ task đầu queue rồi thử submit lại | Có thể làm mất tác vụ đã queue và phá kỳ vọng thứ tự |

`CallerRunsPolicy` có vẻ tránh được việc bỏ job do **bão hòa** nhưng có hai ngoại lệ cần xét: task có thể lỗi ngay trên caller, và khi **shutdown** policy **bỏ job im lặng**. Vì vậy không thể dùng nó để bảo đảm “mỗi job đều thực thi thành công”.

Với dữ liệu bắt buộc xử lý, `AbortPolicy` và một durable queue/job table thường tạo hợp đồng rõ ràng hơn. Với producer batch tuần tự có thể chấp nhận chạy tác vụ trực tiếp, `CallerRunsPolicy` là công cụ hữu ích và ít phức tạp.

## 9. Cấu hình CallerRunsPolicy trong Spring

`ThreadPoolTaskExecutor` là Spring adapter trên nền `ThreadPoolExecutor`. Có thể cấp `RejectedExecutionHandler` cho executor bằng `setRejectedExecutionHandler(...)`.

**Tệp `ReportExecutorConfig.java`**, dùng trong dự án Spring Framework có `spring-context`:

~~~java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ReportExecutorConfig {

    @Bean(name = "reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("report-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy());

        return executor;
    }
}
~~~

Đây là **Spring configuration class**, không phải chương trình `javac` độc lập: nó cần dependencies của Spring và không có `main`. Khi bean do Spring quản lý, container khởi tạo và shutdown executor theo vòng đời ứng dụng. Nếu tự `new ThreadPoolTaskExecutor()` ngoài container, cần gọi `initialize()` trước khi sử dụng và `shutdown()` sau khi xong.

Với cấu hình trên, queue bounded là điều kiện quan trọng để có thể xuất hiện overload theo ý nghĩa mong muốn. Nếu đặt queue capacity cực lớn, `CallerRunsPolicy` có thể hiếm khi kích hoạt trong khi task đang đợi lâu.

### Service gửi việc

**Tệp `ReportDispatchService.java`:**

~~~java
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class ReportDispatchService {
    private final TaskExecutor executor;

    public ReportDispatchService(
            @Qualifier("reportExecutor") TaskExecutor executor) {
        this.executor = executor;
    }

    public void dispatch(String reportId) {
        executor.execute(() -> generate(reportId));
    }

    private void generate(String reportId) {
        System.out.println("Generating report " + reportId);
    }
}
~~~

`dispatch` ở đây không hứa luôn trả về nhanh. Khi pool còn capacity, task thường chạy trên worker; khi CallerRuns kích hoạt, **`generate(reportId)` chạy ngay trên thread gọi `dispatch`**, và `dispatch` chỉ trở về sau khi việc này hoàn thành. Khi pool đã shutdown, task có thể bị bỏ mà `dispatch` vẫn trả về bình thường.

Không dùng ví dụ in log trên làm cơ chế lưu/khôi phục job thật. Trong ứng dụng nghiệp vụ, cần job ID, trạng thái lưu bền, idempotency và xử lý lỗi phù hợp nếu việc không được phép mất.

### Dùng chung với @Async

Nếu phương thức được đánh dấu `@Async("reportExecutor")`, Spring gửi lời gọi qua một executor được chọn theo tên. Tuy nhiên, khi handler là `CallerRunsPolicy` và pool bão hòa, **wrapper của công việc bất đồng bộ có thể được chạy đồng bộ trên caller thread**. Do đó:

- Cần xem `@Async` là yêu cầu gửi công việc qua executor, không phải lời cam kết cứng rằng body **luôn** chạy trên thread khác.
- Nếu caller là request thread, request có thể chờ body thực thi, kéo dài response time.
- `@Async` theo proxy mặc định không intercept self-invocation trong cùng bean.
- Với method trả `Future`/`CompletableFuture`, phải xét cả completion/exception; nếu task bị discard lúc shutdown, một future của JDK submit có thể không bao giờ complete nếu không được hủy/timeout.
- `@Async void` có đường xử lý lỗi nội bộ riêng qua `AsyncUncaughtExceptionHandler`; không dùng handler này làm giải pháp chung cho tất cả lỗi tại thời điểm submit.

Trong ứng dụng Spring Boot, chỉ gắn handler vào đúng executor được yêu cầu. Đừng mặc định rằng mọi `@Async` hay mọi xử lý HTTP async đều chọn bean này; khi cần, chỉ định bean bằng qualifier và kiểm tra đường gọi qua proxy.

## 10. Exception của task và lỗi bị từ chối khác nhau

### Khi dùng execute(Runnable)

Nếu pool bão hòa và dùng `AbortPolicy`, handler ném `RejectedExecutionException`; qua `ThreadPoolTaskExecutor` của Spring, lỗi này thường được chuyển thành `TaskRejectedException` ở ranh giới submit. Caller có thể bắt và trả lỗi quá tải.

Nếu pool bão hòa và dùng `CallerRunsPolicy`, handler tự gọi `Runnable.run()` trên caller. Nếu body ném `RuntimeException`, exception đó có thể thoát ra ngay từ `execute()` trên thread gọi.

~~~java
// Mã minh họa trong một phương thức có executor phù hợp.
// Nếu handler chạy Runnable trực tiếp trên caller, exception có
// thể tới chính lời gọi execute() này.
executor.execute(() -> {
    throw new IllegalStateException("Report generation failed");
});
~~~

Vì vậy phải phân biệt:

- **Không nhận được task:** handler phản ứng với bão hòa hoặc shutdown.
- **Task được thực thi nhưng lỗi:** body `run()` ném exception trong môi trường thực thi của nó.

Với `CallerRunsPolicy`, hai tình huống đó có thể trộn vào cùng một call stack, nhưng ý nghĩa nghiệp vụ vẫn khác nhau. Đừng bắt mọi `RuntimeException` rồi kết luận “pool đang đầy”.

### Khi dùng submit(Callable/Runnable) và Future

Trong JDK `ExecutorService.submit` thường đóng gói công việc thành `FutureTask`. Nếu CallerRuns kích hoạt khi pool bão hòa, chính `FutureTask.run()` chạy trên caller: `submit` có thể mất nhiều thời gian mới trả `Future`, và Future thường đã hoàn tất khi công việc đã chạy xong. Lỗi từ `Callable` được ghi trong Future để `get()` thông báo bằng `ExecutionException`; không nhất thiết được ném trực tiếp từ `submit`.

Nhưng khi executor **shutdown**, `CallerRunsPolicy` **bỏ `FutureTask`**, không hủy Future. Bởi vậy `submit()` có thể trả một Future vẫn ở trạng thái pending; nếu người gọi `get()` không timeout, có thể chờ vô hạn.

Cách sử dụng an toàn hơn khi môi trường có thể shutdown:

- Không dựa vào `CallerRunsPolicy` để thông báo chắc chắn về việc nhận job khi shutdown.
- Khi cần hợp đồng “không nhận thì báo lỗi”, ưu tiên `AbortPolicy` hoặc custom handler ném exception rõ ràng.
- Khi có Future, dùng deadline/timeout và cơ chế cancel phù hợp; timeout chỉ giúp người chờ không treo, không tự chứng minh job chưa từng được thực thi.
- Không lập tức retry tác vụ có side effect mà chưa có job ID, idempotency key hay trạng thái xác nhận.

`CompletableFuture.runAsync(task, executor)` cũng cần đánh giá theo executor được truyền vào: nếu handler chạy inline, quá trình tạo/sắp lịch future có thể thực thi body ngay; nếu bỏ task khi shutdown mà không báo lỗi, completion có thể không đến. Đây không phải một API thay thế cho durable queue.

## 11. Thread context thay đổi: vấn đề quan trọng trong Spring

Khi task chạy trong worker thread, nhiều giá trị được gắn với thread gọi **không tự chuyển theo**. Khi chạy bằng `CallerRunsPolicy`, task lại dùng **chính thread gọi**. Sự khác biệt này có thể làm lộ lỗi chỉ xuất hiện lúc quá tải.

### Transaction và tài nguyên gắn với thread

Trong Spring, nhiều transaction context được gắn với thread đang thực thi. Giả sử controller/service đang ở trong một transaction và gọi `execute()`:

- Lúc còn capacity, task trên worker thường không tự nhận transaction của thread gửi.
- Lúc CallerRuns kích hoạt, task có thể chạy **trong thread đang mang transaction hiện tại**. Nó có thể thấy thread-bound state hoặc tham gia thao tác trên tài nguyên gắn thread theo cách khác với worker execution.
- Cùng một đoạn code có thể có hai hành vi khác nhau phụ thuộc tải, điều rất khó kiểm thử và vận hành.

Không nên giả định một tác vụ chạy qua executor luôn độc lập với request/transaction. Với cập nhật dữ liệu nghiệp vụ, cần xác định ranh giới transaction và nơi ghi bền vững rõ ràng. Dùng transactional outbox hoặc cơ chế sau-commit khi phù hợp với yêu cầu consistency.

### SecurityContext, MDC và ThreadLocal

Các giá trị như security principal, trace ID hoặc MDC logging có thể được giữ trong `ThreadLocal`. Worker pool thread có thể cần `TaskDecorator` hoặc cơ chế context propagation phù hợp để nhận đúng thông tin. Nhưng khi CallerRuns xảy ra, task có thể nhìn thấy trực tiếp context của caller.

Điều này dẫn tới hai kiểu vấn đề:

1. Task chạy trên worker thiếu context mà nó cần.
2. Task chạy trên caller bỗng thấy/ghi vào context của request, trong khi thiết kế ban đầu không dự kiến.

Tránh dựa vào implicit ThreadLocal cho logic nghiệp vụ hoặc quyền truy cập. Truyền dữ liệu cần thiết qua tham số, xác định scope của security context, và kiểm thử cả hai đường worker/caller.

### Thread name, blocking và event-loop

`setThreadNamePrefix("report-")` chỉ đặt tên cho **worker của pool**. Khi CallerRuns kích hoạt, log có thể ghi tên như `http-nio-8080-exec-...` hay tên thread khác do hệ thống gọi, không phải `report-...`. Không sử dụng prefix để suy ra chắc chắn một job chưa chạy.

Trong Spring MVC (Servlet), caller có thể là request processing thread: chạy job dài sẽ chiếm một thread phục vụ request. Trong ứng dụng reactive dùng event-loop, việc gọi một executor với CallerRuns từ event-loop để chạy task blocking có thể gây ảnh hưởng nghiêm trọng đến nhiều request. Kể cả với virtual thread, chạy trực tiếp vẫn làm thời gian hoàn tất của caller tăng và cần hiểu tác động lên tài nguyên.

## 12. Reentrancy, khóa và nguy cơ deadlock

CallerRuns làm việc bị từ chối trở thành **một lời gọi hàm lồng trong `execute()`**, vì vậy bất kỳ trạng thái/khóa nào caller đang giữ sẽ tiếp tục tồn tại trong khi body chạy.

Rủi ro có thể gặp:

- Caller đang giữ một lock; task chạy inline rồi đợi một worker khác cần lock đó → deadlock hoặc tắc nghẽn.
- Caller tạo một latch/future rồi dự định release/chờ ở những dòng **sau `execute()`**, nhưng task inline lại đang chờ release từ caller → cùng một thread tự chặn chính mình.
- Task tự submit thêm task vào cùng executor khi bão hòa; những task lồng nhau cũng chạy trên caller, có thể tạo chuỗi gọi đệ quy rất sâu hoặc thay đổi thứ tự side effect.
- Code vốn dựa vào giả định “ngay sau `execute` sẽ chạy cleanup/commit/giải phóng permit” có thể không thực hiện sớm được, vì `execute` đang chạy task nặng.

Ví dụ **mẫu lỗi khái niệm**:

~~~java
CountDownLatch ready = new CountDownLatch(1);
executor.execute(() -> {
    try {
        ready.await();  // nguy hiểm nếu task bị CallerRuns
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
    }
});
ready.countDown();      // không tới được nếu execute đang chờ vô hạn
~~~

Đây là **đoạn minh họa bên trong method**, không phải chương trình độc lập. Trong trạng thái overload, task có thể chạy inline rồi chờ mã `countDown()` sau lời gọi `execute()`, tạo deadlock. Với worker execution, đường này có thể hoạt động, khiến lỗi không xuất hiện trong test tải thấp.

Nếu dùng CallerRuns, thiết kế task **độc lập với caller sau thời điểm submit**, tránh block chờ caller, tránh giữ lock lâu và kiểm soát đệ quy.

## 13. Shutdown là góc khuất có thể làm mất công việc

Theo hợp đồng JDK, `CallerRunsPolicy` chỉ thực thi khi executor **chưa shutdown**. Khi shutdown, handler bỏ task mà không ném exception. Điều này đặc biệt nguy hiểm trong các tình huống:

- Rolling deployment hoặc ApplicationContext đang đóng.
- Request tới ngay sát thời điểm ứng dụng ngừng nhận việc.
- Một producer vẫn gửi việc sau khi executor bị đóng.
- Job bắt buộc được xử lý nhưng chỉ được lưu trong RAM/queue nội bộ.

Ví dụ Java ở trên kiểm tra cả `shutdownTaskRan=false` và `futureDoneAfterDiscard=false`, cho thấy `execute`/`submit` **trả về bình thường không đủ chứng minh công việc đã chạy hay sẽ chạy**.

Với job phải bảo đảm thực thi:

1. Lưu yêu cầu ở nơi bền vững, có mã job và trạng thái.
2. Chỉ trả “đã nhận” theo hợp đồng khi thao tác ghi bền vững thật sự thành công.
3. Để worker lấy việc từ kho đó khi có capacity; có retry giới hạn, backoff, dead-letter/failure state và idempotency.
4. Nếu không thể tiếp nhận yêu cầu, trả phản hồi rõ ràng (ví dụ HTTP 503 hoặc 429 tùy nguyên nhân).
5. Thiết kế drain/shutdown để không làm mất job đã nhận nhưng chưa chạy.

Trong hệ thống nhiều replica, một `ThreadPoolExecutor` chỉ quản lý thread của **một tiến trình JVM**, không thể đại diện cho một hàng đợi bền vững dùng chung.

## 14. Quan sát và đo đạc CallerRuns trong production

Nếu chỉ đo số thread active và số task hoàn thành của pool, có thể **không nhìn thấy những tác vụ chạy trực tiếp trên caller**. Những tác vụ này không phải worker task thông thường nên số đếm nội bộ của pool như `getCompletedTaskCount()` hoặc các hook `beforeExecute/afterExecute` không nhất thiết bao phủ chúng.

Các số đo nên cân nhắc:

| Tín hiệu | Vì sao quan trọng |
| --- | --- |
| Số lần handler đi vào nhánh caller-runs | Biết tần suất thực sự chuyển công việc sang caller |
| Tổng thời gian caller chạy task và phân phối p95/p99 | Xác định chi phí áp lên request/producer |
| Active worker, current pool size, queue size/capacity | Phân biệt đang đầy queue với cấu hình khác |
| Request latency, timeout, số request đang xử lý | Phát hiện overload lan sang HTTP |
| Job wait time, execution duration, job failure | Không nhầm độ trễ queue với độ trễ body |
| Số tác vụ bị bỏ khi shutdown | Phát hiện tình trạng im lặng mất job |
| CPU, GC, database connections, downstream saturation | Đánh giá tổng áp lực tài nguyên khi nhiều caller tự chạy |

Một handler tùy biến có thể đếm và đo thời gian caller-runs, nhưng phải **giữ đúng semantics shutdown** hoặc cố ý thay bằng semantics báo lỗi. Ví dụ dưới đây là **đoạn minh họa trong phần cấu hình executor**, với `callerRunCount` là `LongAdder` đã được khởi tạo và đăng ký làm metric theo thiết kế ứng dụng:

~~~java
executor.setRejectedExecutionHandler((task, pool) -> {
    if (pool.isShutdown()) {
        throw new java.util.concurrent.RejectedExecutionException(
                "Executor is shutting down");
    }
    callerRunCount.increment();
    task.run();
});
~~~

Đoạn này **không phải `CallerRunsPolicy` thuần**: nó thay đổi hợp đồng shutdown bằng cách ném exception để tránh mất việc im lặng. Với Spring `ThreadPoolTaskExecutor`, exception từ rejection handler có thể được chuyển thành `TaskRejectedException` khi caller gọi API của Spring. Nếu muốn đo thời gian, đặt `long start = System.nanoTime()` ngay trước `task.run()` rồi ghi metric trong `finally`. Chỉ thêm logging/metric nhẹ ở handler; I/O nặng trong handler sẽ khiến caller chậm hơn nữa.

### Không kết luận từ số pool worker

Ví dụ `pool.getActiveCount()==4`, `maximumPoolSize==4` không đồng nghĩa chỉ có bốn task đang chạy; năm request thread có thể đang đồng thời thực thi năm caller-runs task khác. Nếu cần thống kê tổng mức song song, instrument ngay ở ranh giới thực thi task và sử dụng các phép đếm thread-safe phù hợp.

## 15. Kiểm thử cần bao phủ đường worker lẫn đường caller

Test chỉ chạy trong điều kiện queue còn rỗng sẽ bỏ sót nhiều vấn đề. Một bộ kịch bản có ích:

| Kịch bản | Điều cần xác minh |
| --- | --- |
| Pool còn capacity | Tác vụ được worker nhận, không chạy inline chỉ vì có `CallerRunsPolicy` |
| Một worker đang chờ latch, queue đã đầy | Task tiếp theo chạy trên chính thread gọi `execute()` |
| Task caller-runs cần thời gian dài | Lời gọi submit chỉ trở về sau khi body hoàn thành; latency của caller tăng |
| Task caller-runs ném unchecked exception với `execute(Runnable)` | Lỗi có thể xuất hiện tại call stack của caller |
| `submit(Callable)` khi bão hòa | Future có thể hoàn tất sau khi body chạy inline; exception được lưu theo semantics Future |
| Executor đã shutdown | Task mới bị bỏ im lặng theo JDK `CallerRunsPolicy`; không coi return bình thường là accepted |
| Nhiều request threads cùng submit | Có thể có nhiều caller-runs đồng thời ngoài giới hạn worker pool |
| Gửi task khi đang giữ lock/transaction | Không phát sinh self-deadlock hoặc side effect khác giữa worker/caller |
| `@Async("reportExecutor")` khi overload | Xác nhận thời gian phản hồi và đúng executor; không giả định luôn chạy thread khác |
| Graceful shutdown/restart | Các job đã nhận theo hợp đồng bền vững không bị mất hoặc bị xử lý trùng thiếu kiểm soát |

Cách dựng pool bão hòa **không dùng `Thread.sleep()`**: giữ worker bằng latch, chờ `workerStarted`, gửi một job lấp queue, sau đó gửi job cần kiểm thử. Dùng timeout chỉ như chốt để test không treo vô hạn nếu lỗi. Test phải giải phóng latch và đóng executor trong `finally` để không làm CI giữ lại worker non-daemon.

Với Spring, nếu tự khởi tạo `ThreadPoolTaskExecutor` trong integration test, phải gọi `initialize()` rồi `shutdown()`. Nếu dùng bean Spring, để container sở hữu lifecycle. Khi test web response, assert đúng status và deadline ở cả tải thường lẫn overload.

## 16. Khi nào nên dùng CallerRunsPolicy?

**Tình huống phù hợp:** một tiến trình batch có **producer tuần tự**, công việc ngắn/giới hạn thời gian, task an toàn khi chạy trên chính producer, không giữ lock phụ thuộc producer, không cần phân tách thread context, và có chiến lược shutdown rõ ràng. CallerRuns có thể giảm tốc độ đưa việc vào queue mà không cần cơ chế phức tạp.

**Tình huống cần cân nhắc kỹ:** request thread trong Spring MVC giao một job nặng. CallerRuns sẽ làm request mất khả năng trả lời nhanh khi bão hòa; người dùng có thể gặp timeout ngay trong lúc API đang chịu tải nhất.

**Tình huống nên tránh:** event-loop dùng cho reactive request gửi task blocking, công việc có side effect phải thực thi đúng một lần, job cần bảo đảm qua restart, logic phụ thuộc transaction context của thread, hoặc tác vụ có thể chờ tín hiệu chỉ caller mới cung cấp.

### Ma trận lựa chọn

| Mục tiêu hệ thống | Lựa chọn thường thích hợp hơn |
| --- | --- |
| Producer batch tuần tự phải tự giảm tốc khi downstream chậm | CallerRuns có thể phù hợp nếu task chạy trên caller an toàn |
| API phải giữ deadline, không được block để làm job dài | Bounded executor + `AbortPolicy` + phản hồi quá tải rõ ràng |
| Bảo đảm job không mất khi shutdown hoặc restart | Durable queue/job table và worker tiêu thụ, với idempotency |
| Giới hạn tổng số task chạy đồng thời kể cả caller | Admission control, semaphore hoặc consumer pool cố định |
| Dữ liệu metrics best-effort có thể bỏ | Chính sách drop có quan sát, nếu đã chấp nhận mất |
| Tác vụ cần transaction/context rõ ràng | Thiết kế ranh giới thực thi và context propagation độc lập với chính sách tải |

Chính sách overload nên là **một phần hợp đồng ứng dụng**, được review cùng yêu cầu latency và durability. Không nên chỉ đổi handler vì benchmark thấy throughput tăng.

## 17. Các lỗi thường gặp

1. **Cho rằng đặt `CallerRunsPolicy` vẫn luôn bất đồng bộ.** Nhánh quá tải gọi `run()` trực tiếp trên thread gửi.
2. **Cho rằng không ném exception tức task chắc chắn được nhận.** Khi shutdown, chính sách này bỏ task mà có thể không báo lỗi.
3. **Đặt queue quá lớn rồi mong policy điều tiết.** Task có thể tích hàng nghìn phần tử thay vì kích hoạt CallerRuns.
4. **Tưởng `maximumPoolSize` là trần tổng số task đang chạy.** Caller threads có thể làm việc đồng thời ngoài worker pool.
5. **Dùng trong request handler nhạy latency nhưng chỉ đo tải thấp.** p95/p99 có thể tăng khi queue đầy.
6. **Chạy task blocking trên reactive event-loop.** Task inline có thể chặn event-loop, làm ảnh hưởng nhiều request.
7. **Dùng `ThreadLocal`/transaction context mà không kiểm tra cả hai nhánh.** Worker và caller có môi trường thực thi khác nhau.
8. **Phát hành tín hiệu cần thiết sau `execute()` trong khi task có thể chờ tín hiệu đó.** Có nguy cơ tự deadlock khi caller-runs.
9. **Không dọn exception trong tác vụ.** `run()` có thể ném trực tiếp trên caller, khiến đường submit thất bại ngoài dự kiến.
10. **Chỉ dựa vào `getCompletedTaskCount()` để báo tổng công việc.** Tác vụ chạy trên caller không thuộc thống kê worker thông thường.
11. **Dùng `Future.get()` vô hạn sau khi submit vào executor đã shutdown.** Với CallerRuns, Future bị bỏ có thể không hoàn tất.
12. **Tưởng policy tạo backpressure cho toàn bộ hệ thống.** Nó giảm tốc từng submitter, nhưng nhiều submitter vẫn có thể làm việc song song.
13. **Gắn policy vào nhầm Spring executor.** Dùng qualifier và xác minh `@Async`/TaskExecutor chọn đúng bean.
14. **Retry trực tiếp tác vụ có side effect không idempotent.** Rủi ro lặp hành động khi caller hoặc client không biết kết quả cuối.
15. **Test bằng thời gian ngủ ngẫu nhiên.** Dùng latch để kiểm soát lúc worker bận và queue đầy.

## 18. Câu hỏi tự kiểm tra

1. Điều kiện nào khiến `ThreadPoolExecutor` gọi `RejectedExecutionHandler`, thay vì xếp việc vào queue?
2. Vì sao trong demo `onCaller=true` và `order=before>inCaller>after` cho thấy `execute()` đã bị đồng bộ hóa theo thời gian chạy task?
3. Với pool tối đa 4 worker và 20 request threads cùng submit lúc bão hòa, có thể có hơn 4 task đang chạy không? Vì sao?
4. Khi executor đã shutdown, `CallerRunsPolicy` xử lý task mới thế nào? Điều này ảnh hưởng `Future.get()` ra sao?
5. Vì sao p95/p99 của một API có thể tăng mạnh khi dùng CallerRuns dù p50 gần như không đổi?
6. Nếu một task cần transaction của request, sự khác nhau giữa đường worker và caller-runs dẫn tới rủi ro gì?
7. Làm sao kiểm thử cả nhánh worker lẫn caller mà không dùng `Thread.sleep()`?
8. Với một yêu cầu xuất báo cáo bắt buộc không mất, vì sao một queue in-memory cùng CallerRuns chưa đủ?
9. Vì sao `getActiveCount()` không phản ánh tất cả caller-runs task đang chạy?
10. Nếu cần vừa bounded concurrency vừa phản hồi quá tải rõ ràng, bạn sẽ chọn CallerRuns hay admission control + AbortPolicy? Giải thích theo deadline và SLA.

## 19. Tài liệu tham khảo chính thức

- [Java SE 17 — ThreadPoolExecutor.CallerRunsPolicy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.CallerRunsPolicy.html)
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Java SE 17 — RejectedExecutionHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RejectedExecutionHandler.html)
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Spring Framework — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)

**Ghi nhớ:** `CallerRunsPolicy` chuyển chi phí thực thi một tác vụ bị từ chối sang **thread gửi việc**, qua đó làm producer chậm lại nhưng cũng có thể tăng mạnh thời gian phản hồi request và lan tải sang các caller khác. Cần xem **deadline, context, shutdown, observability và bảo đảm không mất việc** trước khi chọn chính sách này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overload-rejection-005">DiscardPolicy, DiscardOldestPolicy và nguy cơ mất công việc</a>

<details>
<summary>Xem chi tiết</summary>

# DiscardPolicy, DiscardOldestPolicy và nguy cơ mất công việc

## 1. Vấn đề thực tế: task đã submit nhưng không được thực hiện

Một ứng dụng Spring có thể gửi nhiều công việc vào `ThreadPoolTaskExecutor`: xử lý email, tính báo cáo, làm mới cache, ghi nhận sự kiện hoặc xử lý việc nền từ `@Async`. Khi số task đến nhanh hơn tốc độ thực thi, thread pool cùng hàng đợi có thể **bão hòa**. Lúc đó bộ thực thi cần quyết định điều gì sẽ xảy ra với công việc vừa gửi.

Java `ThreadPoolExecutor` định nghĩa các chính sách từ chối qua `RejectedExecutionHandler`. Hai chính sách rất dễ gây mất dữ liệu nếu không hiểu rõ là:

- **`DiscardPolicy`**: bỏ công việc **mới bị từ chối** một cách âm thầm.
- **`DiscardOldestPolicy`**: khi executor còn hoạt động, loại phần tử ở **đầu hàng đợi đang chờ**, sau đó **thử gửi lại** công việc mới; nếu executor đã shutdown thì bỏ công việc mới.

Cả hai đều có thể khiến code phía gửi **không nhận được exception**, trong khi một task không bao giờ chạy. Đặc biệt, nếu task được gửi bằng `submit(...)`, một `Future` tương ứng có thể **mãi không hoàn thành và cũng không được hủy**. Vì vậy việc lời gọi `execute` hoặc `submit` trả về bình thường **không phải xác nhận nghiệp vụ đã được xử lý**.

> Với công việc phải hoàn tất hoặc phải có dấu vết thất bại (thanh toán, đơn hàng, cập nhật tồn kho, thông báo bắt buộc, tác vụ có người đang đợi Future), **không chọn chính sách bỏ việc âm thầm**. Hãy áp dụng phản ứng quá tải rõ ràng, giới hạn nguồn gửi, theo dõi lỗi và cơ chế lưu việc bền vững khi cần.

Bài học tập trung vào hai policy nêu trên, trong ngữ cảnh **Quá tải và từ chối** của Spring Framework, đồng thời giải thích cách kiểm chứng bằng Java 17 và vận dụng an toàn với `ThreadPoolTaskExecutor`.

## 2. Task bị từ chối khi nào?

### 2.1. Mối quan hệ giữa thread, queue và giới hạn

Một `ThreadPoolExecutor` dùng các tham số quan trọng:

- `corePoolSize`: số worker cốt lõi mà pool hướng tới khi phát sinh task.
- `maximumPoolSize`: ngưỡng tối đa worker.
- `workQueue`: hàng đợi giữ công việc **chưa bắt đầu**.
- `RejectedExecutionHandler`: chính sách xử lý khi executor không thể nhận thêm task.
- Trạng thái vòng đời: đang chạy, shutdown, đang kết thúc, đã kết thúc.

Đường đi khái quát của `execute(task)`:

~~~text
Có task mới
    |
    v
Số worker đang chạy < corePoolSize?
    | có -> cố tạo worker để thực thi task
    | không
    v
Thử đưa task vào workQueue
    | thành công -> task chờ worker
    | thất bại
    v
Còn có thể tạo worker đến maximumPoolSize?
    | có -> thử tạo worker thực thi task
    | không / không thể nhận
    v
RejectedExecutionHandler.rejectedExecution(task, executor)
~~~

Đây là sơ đồ logic giải thích, không thay cho toàn bộ xử lý trạng thái và race trong source code JDK. Chẳng hạn, pool có thể từ chối task vì **đã shutdown**, ngay cả khi hàng đợi chưa đầy. Cần phân biệt tình huống executor bão hòa với tình huống nó không còn chấp nhận công việc do vòng đời.

Với `corePoolSize = maximumPoolSize = 1` và `ArrayBlockingQueue` dung lượng 1:

~~~text
Worker duy nhất             Queue dung lượng 1          Task mới đến
┌──────────────────┐        ┌────────────────┐          ┌───────────┐
│ A đang thực thi  │        │ B đang chờ     │          │ C         │
└──────────────────┘        └────────────────┘          └───────────┘

A đang bận, không tạo thêm worker, queue đang đầy.
=> C bị từ chối và policy được gọi.
~~~

Chúng ta sẽ dùng chính cấu hình này trong ví dụ để tái hiện kết quả **xác định được**, không phụ thuộc may rủi của lập lịch thread.

### 2.2. Hàng đợi quyết định cách bão hòa

| Queue | Ý nghĩa đối với từ chối |
| --- | --- |
| `ArrayBlockingQueue<>(n)` | Bounded FIFO queue, giúp đặt giới hạn pending tasks rõ ràng; có thể đầy |
| `LinkedBlockingQueue<>(n)` | Bounded FIFO khi truyền capacity `n`; có thể đầy |
| `LinkedBlockingQueue<>()` không giới hạn thực tế nhỏ | Có thể giữ số task rất lớn, gây độ trễ/bộ nhớ tăng; `maximumPoolSize` thường ít có cơ hội phát huy |
| `SynchronousQueue` | Không lưu task đang chờ; cần giao ngay cho worker hoặc tạo worker; với pool đã đạt max, dễ từ chối khi tất cả bận |
| `PriorityBlockingQueue` | Thứ tự đầu hàng đợi theo ưu tiên, không nhất thiết theo thời điểm đến; thông thường không có bounded capacity cố định |

Nói `DiscardOldestPolicy` loại công việc "cũ nhất" là **cách gọi theo policy**; thao tác thực tế lấy **phần tử ở đầu queue** bằng `poll()`. Với FIFO queue, đó thường là task chờ lâu nhất. Với queue sắp xếp theo ưu tiên hoặc đặc tính khác, đầu queue **không nhất thiết** là công việc đến đầu tiên.

## 3. DiscardPolicy: bỏ công việc mới mà không báo lỗi

`ThreadPoolExecutor.DiscardPolicy` triển khai `RejectedExecutionHandler` bằng hành vi **không làm gì** khi bị gọi.

~~~java
RejectedExecutionHandler policy =
        new ThreadPoolExecutor.DiscardPolicy();
~~~

Đây là mảnh khai báo (cần import các kiểu từ `java.util.concurrent`). Khi một task bị từ chối:

1. Executor gọi handler.
2. Handler không chạy, không enqueue, không chuyển task đi nơi khác và không ném exception.
3. Lời gọi gửi task có thể trả về bình thường, mặc dù task vừa gửi **không được thực thi**.

Với sơ đồ A/B/C:

~~~text
Worker chạy A
Queue chứa B
submit C -> DiscardPolicy -> C bị bỏ
Sau khi A kết thúc: B được chạy, C không chạy.
~~~

**Các hệ quả nghiêm trọng:**

- **Không có tín hiệu thành công/thất bại rõ ràng tại điểm submit.** Không thể xem việc không bắt được exception là dấu hiệu đã nhận việc.
- **Mất side effect nghiệp vụ.** Nếu task đáng lẽ ghi database, gửi mail hoặc phát event, hiệu ứng đó có thể không xảy ra.
- **Không có log/metric loss mặc định cho mỗi task bị bỏ.** Người vận hành có thể chỉ thấy hệ thống chậm hoặc thiếu kết quả.
- **Với `Future` từ `submit`**, `FutureTask` của công việc bị bỏ thường vẫn ở trạng thái **chưa hoàn thành**: không ai gọi `run()`, `cancel()` hoặc `complete` cho nó.
- **`get()` không timeout có thể chờ vô hạn** nếu không có cơ chế khác hủy Future hoặc luồng chờ không bị interrupt.

`DiscardPolicy` chỉ phù hợp trong trường hợp **được thiết kế rõ ràng để có thể mất task**, không ai chờ task kết thúc, và có phương án quan sát/giới hạn mức bỏ việc. Ví dụ một lần **làm mới dữ liệu cache không quan trọng** mà lần tiếp theo sẽ được đồng bộ lại, với điều kiện nhiệm vụ và hợp đồng dịch vụ thực sự cho phép bỏ qua. Ngay cả khi đó, nên có metric về việc bị bỏ và cần bảo đảm sự nhất quán của cache.

## 4. DiscardOldestPolicy: cứu task mới bằng cách bỏ task đang chờ

`ThreadPoolExecutor.DiscardOldestPolicy` có hành vi về mặt ý tưởng:

~~~java
// Minh họa logic chính; không phải bản sao đầy đủ source JDK.
if (!executor.isShutdown()) {
    executor.getQueue().poll();  // loại đầu hàng đợi, không báo cho chủ Future
    executor.execute(newTask);   // thử thực thi hoặc enqueue lại task mới
}
// Nếu đã shutdown: task mới bị bỏ.
~~~

Với worker chạy A, queue chứa B, task C đến:

~~~text
Trước:
  RUNNING: A
  QUEUED : B
  NEW    : C

C bị từ chối.
DiscardOldestPolicy:
  1. Queue.poll() loại B.
  2. Retry execute(C).

Sau (giả định retry thành công):
  RUNNING: A
  QUEUED : C
  DROPPED: B
~~~

Khi A hoàn thành, C chạy. **B không còn trong queue và sẽ không chạy**, dù B đã được nhận vào hàng đợi ở một thời điểm trước đó.

Cần hiểu chính xác:

- Policy không hủy task đang chạy A; nó **chỉ lấy phần tử đầu trong queue đang đợi**, nếu có.
- Policy **không bảo đảm retry thành công**. Race với các producer khác hoặc trạng thái shutdown có thể làm lần retry lại bị từ chối. JDK cảnh báo việc thử lại có thể tiếp tục lặp.
- Khi executor đã shutdown, policy **bỏ task vừa bị từ chối**, không nhất thiết lấy task nào khỏi queue.
- Nếu dùng một queue **không có phần tử đang chờ**, ví dụ `SynchronousQueue`, không có "oldest waiting task" để lấy. Việc retry có thể tiếp tục thất bại; không dùng policy này với giả định nó luôn tạo được chỗ trống.
- Nếu queue **không theo FIFO** thì `poll()` lấy head theo quy tắc của queue, không phải lúc nào cũng là task có timestamp cũ nhất.
- Nếu task B là `FutureTask` do `submit` tạo, **việc poll khỏi queue không tự động `cancel` Future đó**. Caller giữ Future B có thể chờ mãi.

Đặc biệt nguy hiểm nếu business coi "công việc đã được xếp hàng" là một bảo đảm sẽ được xử lý. Khi task mới đến, policy có thể **hủy ngầm bảo đảm đó** đối với task cũ, mà không gửi notification cho người đã submit.

### 4.1. Tại sao "ưu tiên việc mới hơn" không phải lúc nào cũng đúng?

Giả sử:

- B là lệnh cập nhật trạng thái một đơn hàng.
- C là yêu cầu xuất báo cáo tạm thời.
- Pool đang bão hòa khi C đến.

Nếu dùng `DiscardOldestPolicy`, task B có thể biến mất khỏi queue, dù quan trọng hơn C. Policy **không biết giá trị nghiệp vụ, deadline, loại công việc hay số lần retry**. Nó chỉ thao tác với cấu trúc queue.

Muốn ưu tiên một việc mới, phải thiết kế **quy tắc admission/priority/cancellation có kiểm soát** ở tầng nghiệp vụ. Chẳng hạn, phân loại workload thành pool hoặc hàng đợi riêng, theo dõi trạng thái task và báo rõ khi một job bị thay thế.

## 5. So sánh bốn policy JDK thường gặp

| Policy | Khi pool bão hòa và vẫn đang chạy | Bên submit biết bị từ chối? | Rủi ro chính |
| --- | --- | --- | --- |
| `AbortPolicy` (mặc định của JDK `ThreadPoolExecutor`) | Ném `RejectedExecutionException` | **Có**, nếu caller xử lý exception | Nếu caller bỏ qua lỗi hoặc không retry/lưu việc, nghiệp vụ vẫn mất việc |
| `CallerRunsPolicy` | Caller thực thi task trên chính thread gửi | Thường chạy đồng bộ thay vì bị bỏ | Tăng latency, chặn request/event thread; khi executor đã shutdown chính policy này cũng bỏ task |
| `DiscardPolicy` | Bỏ task **mới** âm thầm | **Không** | Task mới biến mất, Future có thể treo |
| `DiscardOldestPolicy` | Loại phần tử **đầu queue** rồi thử gửi lại task mới | **Không có bảo đảm phản hồi** về task bị loại | Task **đã chờ** biến mất, Future của task cũ có thể treo |

Không có policy nào tự làm công việc bền vững qua process crash. `AbortPolicy` chỉ làm việc từ chối **hiện rõ tại caller**; phía caller còn phải quyết định retry, trả lỗi HTTP, lưu vào queue bền vững hoặc áp dụng backpressure. `CallerRunsPolicy` làm chậm producer trong một số trường hợp nhưng có thể gây vấn đề mới nếu caller là thread nhạy cảm với latency.

## 6. Chương trình Java 17 tái hiện mất việc một cách xác định

Ví dụ này **chạy độc lập bằng Java 17**, không cần Spring/Gradle. Nó khống chế trạng thái pool bằng `CountDownLatch` để một worker đang bận, queue chứa chính xác một task, rồi submit task thứ ba. Nhờ vậy quan sát rõ sự khác nhau của hai policy.

Tạo tệp `RejectionLossDemo.java`:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class RejectionLossDemo {

    public static void main(String[] args) throws Exception {
        runCase("DiscardPolicy",
                new ThreadPoolExecutor.DiscardPolicy());
        runCase("DiscardOldestPolicy",
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    private static void runCase(
            String label,
            RejectedExecutionHandler handler) throws Exception {

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1,                              // corePoolSize
                1,                              // maximumPoolSize
                0L, TimeUnit.MILLISECONDS,       // keepAlive
                new ArrayBlockingQueue<>(1),    // chỉ một task chờ
                handler);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            // Task A bắt đầu chạy và bị chặn có chủ đích.
            Future<?> running = pool.submit(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker did not start");
            }

            // Task B chiếm chỗ duy nhất trong queue.
            Future<String> queued = pool.submit(() -> "queued");

            // Task C đến khi 1 worker bận và queue đầy.
            Future<String> incoming = pool.submit(() -> "incoming");

            // Khi chưa release A, không có worker nào rút B/C khỏi queue.
            System.out.println(label + ": queued in queue=" +
                    pool.getQueue().contains(queued));
            System.out.println(label + ": incoming in queue=" +
                    pool.getQueue().contains(incoming));

            release.countDown();
            running.get(5, TimeUnit.SECONDS);

            Future<String> kept = label.equals("DiscardPolicy")
                    ? queued : incoming;
            Future<String> dropped = label.equals("DiscardPolicy")
                    ? incoming : queued;

            System.out.println(label + ": result=" +
                    kept.get(5, TimeUnit.SECONDS));
            System.out.println(label + ": dropped done=" +
                    dropped.isDone());

            // Cleanup trong demo; hai policy KHÔNG tự hủy Future đã bỏ.
            dropped.cancel(false);
            System.out.println(label + ": dropped cancelled=" +
                    dropped.isCancelled());
        } finally {
            release.countDown();
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac RejectionLossDemo.java
java RejectionLossDemo
~~~

**Stdout chính xác** của chương trình khi hai lần chạy được hoàn tất bình thường:

~~~text
DiscardPolicy: queued in queue=true
DiscardPolicy: incoming in queue=false
DiscardPolicy: result=queued
DiscardPolicy: dropped done=false
DiscardPolicy: dropped cancelled=true
DiscardOldestPolicy: queued in queue=false
DiscardOldestPolicy: incoming in queue=true
DiscardOldestPolicy: result=incoming
DiscardOldestPolicy: dropped done=false
DiscardOldestPolicy: dropped cancelled=true
~~~

Vì sao có thể dự đoán các dòng này?

1. `started.await(...)` giữ main cho đến khi worker bắt đầu A; A chờ `release`.
2. Chỉ có **một** worker và **một** slot queue, vì vậy B ở queue khi C được submit.
3. Với `DiscardPolicy`, B vẫn nằm trong queue; C không vào queue.
4. Với `DiscardOldestPolicy`, B bị `poll()` khỏi queue; C được thêm vào slot trống.
5. Sau khi thả A, task được giữ lại chạy và `Future.get(...)` trả đúng chuỗi `queued` hoặc `incoming`.
6. `dropped.isDone()` là `false` dù task được giữ lại đã hoàn thành: policy không gọi `run` hay `cancel` cho `FutureTask` bị loại.
7. Ví dụ gọi `cancel(false)` **thủ công** để cleanup; đây là thao tác chứng minh rằng Future chỉ chuyển trạng thái cancelled nhờ code ứng dụng, **không nhờ policy**.

Không dùng `dropped.get()` không có timeout trong chương trình này: nó có thể chờ mãi. Với task bất đồng bộ thực, có thể áp dụng `get(timeout, unit)` để giới hạn thời gian chờ, nhưng **timeout không tự khôi phục công việc đã mất**.

### 6.1. Nếu gửi Runnable bằng execute(...) thay vì submit(...)

`execute(Runnable)` không tạo Future trả về cho caller. Với `DiscardPolicy`, lời gọi có thể trả bình thường dù Runnable không chạy; caller không có Future để phát hiện tình trạng ấy. `submit(...)` lại trả một Future nhưng Future có thể không bao giờ được hoàn tất. Vì vậy **không có exception** và **có Future** đều không tương đương với "task chắc chắn được xử lý".

Lưu ý rằng `Future.isDone() == false` ở một thời điểm bất kỳ **không tự chứng minh task bị bỏ**; nó có thể chỉ đang chờ hoặc đang chạy. Ví dụ ở trên tạo điều kiện xác định để biết task đã bị loại nhờ trạng thái worker/queue và policy được dùng.

## 7. Áp dụng vào Spring ThreadPoolTaskExecutor

Spring `ThreadPoolTaskExecutor` bao bọc `ThreadPoolExecutor`, cho phép cấu hình core/max pool, capacity, threadNamePrefix và `RejectedExecutionHandler`. Về giao diện Spring, khi underlying executor **ném** `RejectedExecutionException`, `ThreadPoolTaskExecutor` thường chuyển sang `TaskRejectedException` theo hợp đồng `TaskExecutor`.

Nhưng nếu **handler âm thầm bỏ task và không ném exception**, lớp Spring không thể mặc nhiên biết đã mất công việc chỉ bằng giá trị trả về của handler. Một ứng dụng dùng `@Async` hoặc `submit` có thể không nhận được tín hiệu từ chối ở điểm gọi, trong khi Future/CompletableFuture liên quan không được hoàn thành nếu việc chạy bị bỏ.

Đây là hai cấu hình **chỉ để minh họa rủi ro**, không khuyến nghị dùng cho nghiệp vụ bắt buộc:

~~~java
// Bên trong class @Configuration, có import @Bean,
// ThreadPoolTaskExecutor và ThreadPoolExecutor.
@Bean("lossyExecutor")
public ThreadPoolTaskExecutor lossyExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(20);
    executor.setThreadNamePrefix("lossy-");
    executor.setRejectedExecutionHandler(
            new ThreadPoolExecutor.DiscardPolicy());
    return executor;
}
~~~

Đổi `DiscardPolicy` thành `DiscardOldestPolicy` sẽ khiến executor có thể bỏ công việc **đang chờ** để nhận công việc mới. Dù đặt bean tên `lossyExecutor`, chỉ những đường gọi thực sự chọn bean ấy mới chịu ảnh hưởng. Ví dụ:

~~~java
// Minh họa trong một Spring bean khác, khi đã bật @EnableAsync.
@Async("lossyExecutor")
public CompletableFuture<String> generateReport() {
    return CompletableFuture.completedFuture("ready");
}
~~~

Chỉ khi lời gọi được Spring proxy chặn và dispatch vào đúng executor thì cấu hình trên mới áp dụng. Tự gọi method trong cùng bean (`this.generateReport()`) có thể bỏ qua cơ chế `@Async` ở chế độ proxy mặc định. Cũng không nên suy ra controller, scheduler, event listener, Spring Batch hay Integration tự động dùng `lossyExecutor`: mỗi điểm tích hợp có cấu hình chọn executor riêng.

### 7.1. Một cấu hình mặc định an toàn hơn

Với phần lớn công việc không chấp nhận mất âm thầm, chọn chiến lược **báo lỗi rõ ràng**:

~~~java
@Bean("importantExecutor")
public ThreadPoolTaskExecutor importantExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("important-");
    executor.setRejectedExecutionHandler(
            new ThreadPoolExecutor.AbortPolicy());
    return executor;
}
~~~

Đây cũng là mảnh code cấu hình Spring, chưa đủ để chạy độc lập. `AbortPolicy` khiến đường submit biết khi pool không chấp nhận task; **không tự thực hiện retry hoặc đưa task vào storage**. Ứng dụng phải có xử lý tương ứng. Đặc biệt khi dùng `@Async`/Future, cần kiểm thử cách lỗi được thể hiện tại ranh giới proxy và caller trong phiên bản/framework đang dùng.

Trong ứng dụng Spring Boot, có thể có TaskExecutor do auto-configuration tạo. Việc tự khai báo nhiều executor có thể thay đổi quy tắc lựa chọn bean mặc định. Nếu công việc quan trọng, **chỉ định qualifier hoặc wiring rõ ràng** thay vì tin một executor bất kỳ sẽ được chọn.

## 8. Ranh giới "accepted", "running", "done" và độ bền công việc

Một lỗi thiết kế phổ biến là gom bốn trạng thái khác nhau thành "đã gửi":

| Trạng thái | Định nghĩa | Có bảo đảm khi execute/submit trả về không? |
| --- | --- | --- |
| **Accepted** | Executor thực sự giữ được task hoặc đang cho chạy | Với policy bỏ âm thầm: **không thể suy ra** từ việc không có exception |
| **Running** | Một worker đã bắt đầu xử lý | Không; task có thể còn trong queue |
| **Completed** | Task kết thúc, kết quả hoặc lỗi đã được ghi nhận | Không; cần kết quả/Future/log/ack phù hợp |
| **Durably recorded** | Công việc đã lưu ở nơi tồn tại qua restart/crash | Không; thread pool memory không phải hàng đợi bền vững |

Hai policy này đặc biệt nguy hiểm khi caller nhận HTTP `202 Accepted` hoặc lưu trạng thái "PROCESSING" ngay sau khi submit nhưng không có ghi nhận đáng tin rằng task đã được tiếp nhận. Một executor có thể im lặng bỏ task hoặc loại task cũ sau đó, khiến người dùng thấy job "đang chạy" mãi.

Nếu ứng dụng thật sự hứa sẽ xử lý công việc, nên **ghi nhận job trong database/outbox hoặc broker có durability**, rồi dùng consumer/worker lấy và xử lý với chiến lược retry, deduplication và trạng thái lỗi rõ ràng. ThreadPoolTaskExecutor có thể là tài nguyên chạy worker, nhưng **không đóng vai trò kho lưu bền vững**.

### 8.1. Vì sao Future không tự giải quyết mất task?

Khi gọi `ExecutorService.submit`, một `RunnableFuture` (thường là `FutureTask`) được tạo và gửi vào executor. Chỉ khi có thread thực thi task, hoặc có người gọi cancel, Future mới đi tới trạng thái kết thúc tương ứng. Nếu policy bỏ task mà không chạy/hủy nó, **Future có thể tồn tại mãi trong trạng thái pending**.

Các hệ quả thực tế:

- `future.get()` không timeout có thể chờ không giới hạn.
- `future.get(3, TimeUnit.SECONDS)` có thể ném `TimeoutException`, nhưng đó là **timeout chờ**, chưa khẳng định nguyên nhân là bị bỏ hay chạy quá lâu.
- `CompletableFuture.join()` cũng có thể chờ vô hạn nếu future không bao giờ hoàn thành.
- `CompletableFuture.orTimeout(...)` làm kết quả tương lai có timeout, nhưng không tự chạy lại hoặc hoàn thành side effect nghiệp vụ.
- Với `DiscardOldestPolicy`, Future của task **đã được queue nhận từ trước** có thể mắc kẹt sau khi task bị thay thế. Nguy cơ này khó quan sát hơn việc từ chối ngay tại lúc submit mới.

Không coi `shutdown()` hay `shutdownNow()` là thao tác sẽ **tự hoàn tất mọi Future** đã bị một handler vứt bỏ. Future bị bỏ khỏi queue trước đó có thể không còn được executor nhìn thấy để xử lý.

## 9. Lựa chọn thay thế và cách thiết kế để không mất việc ngoài ý muốn

### 9.1. Thể hiện lỗi từ chối ngay tại producer

Đối với task không thể bỏ, dùng handler ném lỗi (thường `AbortPolicy`), bắt lỗi tại biên gửi việc và quyết định:

- Trả **429** khi áp dụng hạn mức cho client, hoặc **503** khi dịch vụ đang quá tải, cùng thông tin retry phù hợp nếu là HTTP.
- Retry **có giới hạn** với exponential backoff và jitter, chỉ khi operation có thể retry an toàn.
- Lưu task vào hệ thống bền vững để xử lý lại.
- Áp dụng bounded admission queue, semaphore hoặc rate limit **trước khi** gọi executor để tránh tạo quá nhiều việc.

Đừng retry vô hạn ngay trong request thread khi pool đã bão hòa. Retry không có giới hạn có thể làm tắc chính những thread đang cần để giải tỏa quá tải. Với tác vụ có side effect, cần định nghĩa **idempotency** để tránh thực thi trùng khi caller không biết lần trước đã xử lý đến đâu.

### 9.2. CallerRunsPolicy: backpressure nhưng có hệ quả

`CallerRunsPolicy` cho phép thread đang gọi `execute` tự chạy công việc khi pool bão hòa (nếu executor chưa shutdown). Điều này có thể làm giảm tốc độ producer vì producer bị chặn cho đến khi task xong, là một dạng **backpressure**.

Nhưng nếu caller là HTTP request thread, message listener hoặc scheduler thread, việc làm nặng có thể làm tăng latency, block I/O hoặc khiến lịch trễ. Khi executor đã shutdown, `CallerRunsPolicy` cũng có thể **bỏ** task mới theo hợp đồng của nó. Vì vậy cần đánh giá đường gọi thay vì coi CallerRuns là biện pháp bảo đảm không mất việc trong mọi trạng thái.

### 9.3. Nếu công việc được phép bỏ, phải định nghĩa điều kiện bỏ

Một số workload có thể chấp nhận **lossy execution** như:

- Sự kiện telemetry chỉ cần lấy mẫu và đã được đánh dấu có thể mất.
- Yêu cầu làm mới một snapshot tạm thời mà phiên bản mới nhất sẽ thay thế bản cũ.
- Cập nhật UI preview không quan trọng, khi người dùng chỉ cần kết quả gần nhất.

Ngay cả trường hợp này, **DiscardOldestPolicy không hiểu key hoặc phiên bản dữ liệu**. Bỏ phần tử đầu queue có thể loại một task quan trọng hơn task mới. Nếu muốn "giữ phiên bản mới nhất theo từng key", hãy thiết kế một **coalescing queue/last-value-wins mechanism** có chủ đích, không giả định DiscardOldest cung cấp đúng hợp đồng đó.

Nên định nghĩa mức loss tối đa, cách ghi metric, cách giao tiếp với caller và khả năng phục hồi. Nếu task có người đang chờ Future, khi bỏ phải **hoàn tất Future bằng hủy/lỗi có quan sát được**; chỉ xóa khỏi queue là chưa đủ.

### 9.4. Custom handler khi phải ghi nhận từng lần từ chối

Một ví dụ nhỏ dễ kiểm soát là custom handler **ghi metric và ném lỗi**, không im lặng mất việc. Đoạn sau đặt trong một class cấu hình có bean executor và một `AtomicLong` dùng làm bộ đếm; không phải một ứng dụng Spring độc lập:

~~~java
AtomicLong rejected = new AtomicLong();

ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(2);
executor.setMaxPoolSize(2);
executor.setQueueCapacity(50);

executor.setRejectedExecutionHandler((task, pool) -> {
    rejected.incrementAndGet();
    throw new RejectedExecutionException(
            "Important task rejected: executor overloaded or stopped");
});
~~~

Cần import `AtomicLong`, `RejectedExecutionException` và `ThreadPoolTaskExecutor`. Trong ứng dụng thật, dùng metrics registry phù hợp, có phân biệt `shutdown` và `saturation`, logger với tần suất được giới hạn và mã lỗi rõ ràng; không log payload/token/user data nhạy cảm.

Nếu nghiệp vụ thật sự cần loại task chờ và ưu tiên task mới, handler cần **ghi nhận task bị loại, hủy Future nếu có, quyết định retry hợp lý, xử lý trường hợp queue rỗng/shutdown và truyền trạng thái tới caller**. Javadoc của `DiscardOldestPolicy` còn đưa ví dụ kiểm tra task bị poll có phải `Future` để gọi `cancel(false)`. Dù vậy, một handler tự gọi lại `execute` vẫn phải cân nhắc race và khả năng từ chối lặp; đừng sao chép logic thay thế queue vào sản phẩm mà không kiểm thử concurrency và hợp đồng nghiệp vụ.

## 10. Quan sát mất công việc và kiểm thử

### 10.1. Chỉ số vận hành nên có

Bên cạnh số thread active, size queue, execution time và wait time, nên đo:

- **tasks_submitted**: số công việc producer yêu cầu gửi.
- **tasks_accepted**: số công việc thực sự có bằng chứng được tiếp nhận theo hợp đồng của hệ thống.
- **tasks_started/completed/failed**: được ghi ở nơi thực thi hoặc ở hệ thống quản lý job.
- **tasks_rejected/dropped/replaced**: ghi chính xác ở chính sách admission/rejection và khi chủ động thay thế task.
- **task_age / oldest_queue_wait**: giúp nhận ra backlog trước khi queue đầy.
- **Future timeout, cancelled, still pending**: điều tra những công việc không bao giờ có kết quả.

`ThreadPoolExecutor.getCompletedTaskCount()` không thay thế một bộ đếm loss đáng tin cậy; nó thống kê những task worker đã hoàn tất, không tự thông báo rõ mọi Runnable mà policy âm thầm bỏ. Số task "submitted" tính tại producer cũng không đủ để phân biệt accepted với dropped. Thực hiện đo theo đúng ranh giới nghiệp vụ.

### 10.2. Kiểm thử có kiểm soát

Thử nghiệm nên khống chế trạng thái (như `CountDownLatch` ở mục 6), rồi kiểm tra:

1. Task nào ở worker, task nào ở queue, task nào mới đến.
2. Policy đã áp dụng trong trạng thái bão hòa hay shutdown.
3. Task mới hay task cũ bị bỏ.
4. Bên gọi có nhận được exception, Future hủy hay trạng thái thất bại không.
5. Sau shutdown và restart, công việc bắt buộc có được phục hồi không.
6. Dưới nhiều producer đồng thời, có race khi handler tự poll/retry không.

Khi test với `DiscardPolicy` hoặc `DiscardOldestPolicy`, **không dùng `Future.get()` không timeout** cho task có thể bị loại. Hãy giới hạn thời gian, assert rõ trạng thái, cleanup bằng cancel và shutdown để test không treo. Test một lần qua không chứng minh thiết kế an toàn dưới mọi interleaving; kết hợp phân tích semantic và stress test có giới hạn.

## 11. Những lỗi suy luận cần tránh

| Phát biểu dễ nhầm | Điều đúng |
| --- | --- |
| "Không ném RejectedExecutionException thì task đã được nhận." | Handler có thể bỏ task âm thầm; không có exception không phải ACK |
| "DiscardPolicy chỉ giảm tốc độ xử lý khi quá tải." | Nó **bỏ công việc mới**, không tự chờ hoặc retry |
| "DiscardOldestPolicy chỉ bỏ task đang chạy lâu nhất." | Nó `poll()` **đầu queue đang chờ**, không hủy worker đang chạy |
| "DiscardOldestPolicy chắc chắn nhận được task mới." | Nó **retry**, vẫn có thể thất bại/reject lại hoặc executor shutdown |
| "submit trả Future nghĩa là sẽ có kết quả." | Future có thể pending vô thời hạn nếu task bị bỏ mà không hủy |
| "Future.get(timeout) tự chạy lại task đã mất." | Timeout chỉ giới hạn chờ; không phục hồi side effect |
| "Spring @Async luôn báo lỗi nếu pool bỏ việc." | Nếu handler không ném exception, các tích hợp không có tín hiệu rejection rõ ràng |
| "Queue nhỏ cộng max thread lớn thì chắc không bị quá tải." | Nguồn gửi vẫn có thể vượt năng lực xử lý và gây bão hòa |
| "DiscardOldest giữ công việc quan trọng hơn." | Policy không biết ý nghĩa nghiệp vụ, chỉ biết queue head |
| "Một pool trong JVM bảo đảm xử lý qua restart." | In-memory executor không phải job store bền vững |

## 12. Câu hỏi tự kiểm tra

1. Pool có một worker đang chạy A, queue dung lượng một chứa B, task C đến. `DiscardPolicy` bỏ task nào? **C**, tức task mới bị từ chối.
2. Cùng tình huống trên, `DiscardOldestPolicy` thường bỏ task nào? **B** ở đầu queue; sau đó thử gửi lại C.
3. Với `DiscardOldestPolicy`, A đang chạy có bị hủy không? **Không**, policy chỉ thao tác ở queue đang chờ.
4. Vì sao `Future` của B có thể treo sau khi B bị poll? **Vì B không được chạy và policy không gọi `cancel` hay hoàn thành Future.**
5. Nếu queue là `SynchronousQueue` đang không có phần tử chờ, có thể tin `DiscardOldestPolicy` sẽ tạo chỗ trống không? **Không**; không có queued task để poll và retry có thể tiếp tục bị từ chối.
6. `AbortPolicy` có bảo đảm task được xử lý khi bão hòa không? **Không**; nó chỉ làm từ chối hiện thành exception để caller xử lý.
7. Task được submit bằng `@Async` vào một `ThreadPoolTaskExecutor` dùng `DiscardPolicy`. Có thể kết luận Future chắc chắn hoàn tất không? **Không**; một task bị bỏ có thể khiến quá trình chờ kết quả không bao giờ kết thúc.
8. Khi nào có thể chấp nhận bỏ task? **Chỉ khi hợp đồng nghiệp vụ rõ ràng cho phép loss, có quan sát và cơ chế phục hồi/hoàn tất trạng thái người chờ khi cần.**
9. Nếu phải xử lý một payment job dù ứng dụng restart, liệu đổi DiscardPolicy sang CallerRunsPolicy là đủ? **Không**; cần lưu và điều phối công việc bền vững và thiết kế idempotency.
10. Nếu queue là priority queue, task được DiscardOldest lấy ra có chắc đến sớm nhất không? **Không**; head do thứ tự của queue quyết định.

**Kết luận:** `DiscardPolicy` có thể làm biến mất **công việc mới**; `DiscardOldestPolicy` có thể làm biến mất **công việc đã chờ**. Với cả hai, nguy cơ nằm ở việc mất công việc **không có tín hiệu đúng mức**, khiến kết quả thiếu, Future treo và hệ thống tưởng đã nhận việc. Với nghiệp vụ không cho phép mất task, chọn **admission rõ ràng, rejection có thể quan sát, backpressure và lưu công việc bền vững** tùy yêu cầu.

### Tài liệu chính thức

- [Java 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Java 17 — ThreadPoolExecutor.DiscardPolicy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.DiscardPolicy.html)
- [Java 17 — ThreadPoolExecutor.DiscardOldestPolicy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.DiscardOldestPolicy.html)
- [Java 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Spring Framework 6.1 — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overload-rejection-006">Không để Future chờ vô hạn do chính sách loại bỏ không hoàn tất tác vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Không để Future chờ vô hạn do chính sách loại bỏ không hoàn tất tác vụ

## 1. Bài toán: executor bỏ việc, Future vẫn đang đợi

Giả sử một API Spring nhận yêu cầu tạo báo cáo, đưa tác vụ vào `ThreadPoolTaskExecutor` và trả một `Future`/`CompletableFuture` để các thành phần khác chờ kết quả. Khi hệ thống quá tải, một cấu hình như `DiscardPolicy` có thể **âm thầm bỏ tác vụ**. Caller vẫn nhận được đối tượng `Future` từ `submit` nhưng tác vụ chưa từng chạy, không có ai gọi logic hoàn tất và cũng không có exception/cancellation được ghi trên Future.

Khi đó:

- `future.isDone() == false` có thể kéo dài mãi nếu không có tác nhân khác hoàn tất hoặc hủy Future.
- `future.get()` **không có timeout** có thể chặn vô hạn.
- `future.get(2, TimeUnit.SECONDS)` giúp người gọi dừng chờ sau khoảng thời gian giới hạn bằng `TimeoutException`, nhưng **không tự biến Future thành DONE/CANCELLED**.
- `CompletableFuture.join()` và các stage phụ thuộc cũng có thể chờ vô hạn; nếu task dùng để hoàn thành Future bị loại bỏ, lời gọi `thenApply`/`allOf` có thể không bao giờ chạy xong.

Lỗi thường khó thấy trong môi trường phát triển vì queue hiếm khi đầy. Trên production, chỉ cần một worker chậm, hàng đợi hữu hạn và nhiều tác vụ đến cùng lúc là chính sách từ chối được kích hoạt.

**Nguyên tắc thiết kế:** nếu bên ngoài đang đợi một kết quả, **mọi nhánh kết thúc** phải được quan sát: thành công, lỗi nghiệp vụ, từ chối nhận việc, hủy, timeout và dừng ứng dụng. Tránh chính sách bỏ việc không thông báo khi đầu ra là `Future` mà người khác sẽ đợi.

## 2. Phân biệt task, Future và việc “đã submit”

### 2.1. `execute` khác `submit`

Với Java `ThreadPoolExecutor`:

- `execute(Runnable)` đề nghị executor chạy một `Runnable` và có kiểu trả về `void`.
- `submit(Callable<T>)`/`submit(Runnable)` tạo một đối tượng kiểu `RunnableFuture` (thường là `FutureTask`), gửi wrapper đó qua `execute` và trả `Future` cho caller.
- Khi wrapper thực sự chạy, nó đánh dấu thành công hoặc lỗi. Khi bị `Future.cancel(...)`, nó chuyển sang trạng thái canceled. Nếu wrapper **chưa từng chạy và không ai cancel**, Future thường vẫn ở trạng thái ban đầu.
- `submit` trả về được **không bảo đảm** executor đã thật sự nhận việc: một `RejectedExecutionHandler` được cấu hình để im lặng có thể khiến `execute` trả về bình thường dù tác vụ vừa bị bỏ.

Vì vậy, “đã có `Future`” không đồng nghĩa “job sẽ hoàn tất”. Khi tác vụ được tạo bởi `CompletableFuture.supplyAsync(supplier, executor)`, một wrapper cũng phải được chạy để hoàn thành stage; executor im lặng loại wrapper thì CompletableFuture có thể tiếp tục `isDone() == false`.

### 2.2. Các trạng thái cần phân biệt

| Tình huống | `isDone()` | `get()` | Ý nghĩa |
| --- | --- | --- | --- |
| Chưa bắt đầu nhưng **đang nằm hợp lệ trong queue** | `false` | Chờ | Có thể được thực thi sau |
| Đang thực thi | `false` | Chờ | Tiến độ phụ thuộc task |
| Thành công | `true` | Trả kết quả | Terminal |
| Task ném exception qua `FutureTask` | `true` | `ExecutionException` | Terminal lỗi |
| Được cancel thành công | `true` và `isCancelled() == true` | `CancellationException` | Terminal hủy |
| Bị `DiscardPolicy` bỏ im lặng | Thường `false` mãi | Chờ mãi nếu không timeout | **Mất tín hiệu hoàn tất** |
| Bị `DiscardOldestPolicy` đẩy khỏi queue mà không cancel | Thường `false` mãi | Chờ mãi nếu không timeout | **Future cũ bị mồ côi** |

Một Future chưa hoàn tất không tự cho biết “đang xếp hàng”, “đang chạy” hay “đã thất lạc”. Cần kết hợp metric của pool, nhật ký rejection, trạng thái job và thời điểm gửi việc.

### 2.3. Vì sao executor từ chối?

`ThreadPoolExecutor` gọi `RejectedExecutionHandler` khi không thể nhận thêm việc (thường vì đã đạt giới hạn số worker và queue đầy) hoặc executor đã shutdown. Với Spring `ThreadPoolTaskExecutor`, `corePoolSize`, `maxPoolSize`, `queueCapacity` và handler quyết định hành vi thực tế.

Ví dụ pool có `core=1`, `max=1`, queue chứa tối đa một tác vụ: tác vụ đầu đang chạy; tác vụ thứ hai xếp hàng; tác vụ thứ ba bị từ chối. Nếu queue không giới hạn, tác vụ mới có thể xếp hàng rất lâu trước khi có rejection; không nên lấy việc “chưa từng rejected” làm bằng chứng là hệ thống có đủ năng lực.

## 3. Bốn chính sách từ chối của ThreadPoolExecutor

| Chính sách JDK | Khi pool bão hòa | Nguy cơ với Future |
| --- | --- | --- |
| `AbortPolicy` (mặc định của `ThreadPoolExecutor`) | Ném `RejectedExecutionException` ngay tại chỗ gửi | Caller nhận biết được việc từ chối, có thể trả lỗi/hoàn tất Future ngoại lệ |
| `CallerRunsPolicy` | Chạy tác vụ trên **luồng gửi** nếu executor chưa shutdown | Không mất tác vụ do bão hòa, nhưng có thể chặn HTTP caller; **khi đã shutdown thì bỏ im lặng** |
| `DiscardPolicy` | Bỏ tác vụ đang bị từ chối, **không ném lỗi** | `Future` của tác vụ vừa gửi có thể không bao giờ completed |
| `DiscardOldestPolicy` | Bỏ tác vụ **đầu queue** rồi thử gửi lại tác vụ mới nếu chưa shutdown | Future **của task cũ** có thể chờ vô hạn; khi shutdown, tác vụ mới cũng bị bỏ |

`DiscardOldestPolicy` không phải “đổi thứ tự ưu tiên” theo nghĩa an toàn. Nó thường lấy `Runnable` đầu queue ra bằng `poll()` mà **không hủy Future tương ứng**. Nếu queue có `FutureTask`, Future của người đã gửi task cũ có thể không bao giờ chuyển trạng thái terminal. Ngay cả khi tác vụ mới được chấp nhận, hệ thống đã làm mất một lời hứa kết quả của caller khác.

**Chú ý shutdown:** `CallerRunsPolicy` và `DiscardOldestPolicy` trong JDK cũng có nhánh bỏ tác vụ khi executor đã shutdown. Vì thế `CallerRunsPolicy` không phải bằng chứng rằng *mọi task submit sẽ luôn hoàn thành*. Với yêu cầu kết quả chắc chắn, ưu tiên cơ chế **từ chối minh bạch** và ghi nhận trạng thái terminal.

## 4. Java 17: tái hiện lỗi một cách xác định

Ví dụ độc lập dưới đây dùng `CountDownLatch` để giữ worker duy nhất đang bận, đặt task thứ hai vào queue một phần tử, rồi gửi task thứ ba. Không dựa vào `Thread.sleep` để “hy vọng” pool bão hòa. Hai chính sách được kiểm thử lần lượt:

1. `DiscardPolicy` bỏ task **mới**, Future của task mới không hoàn tất.
2. `DiscardOldestPolicy` loại task **cũ đang đợi**, Future cũ không hoàn tất, task mới chạy.

Lưu thành file **`DroppedFutureDemo.java`**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class DroppedFutureDemo {
    public static void main(String[] args) throws Exception {
        demonstrate("DiscardPolicy",
                new ThreadPoolExecutor.DiscardPolicy(), false);
        demonstrate("DiscardOldestPolicy",
                new ThreadPoolExecutor.DiscardOldestPolicy(), true);
    }

    private static void demonstrate(
            String name, RejectedExecutionHandler handler,
            boolean dropsQueuedTask) throws Exception {

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                handler);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<String> running = pool.submit(() -> {
                started.countDown();
                release.await();
                return "running";
            });

            // Chắc chắn worker đã nhận task đầu và đang chờ release.
            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker did not start");
            }

            // Queue capacity = 1: queued nằm trong hàng đợi.
            Future<String> queued = pool.submit(() -> "queued");

            // Worker bận và queue đầy: chính sách rejection chạy.
            Future<String> newest = pool.submit(() -> "newest");

            release.countDown();

            System.out.println(name + ": running="
                    + running.get(5, TimeUnit.SECONDS));

            Future<String> kept = dropsQueuedTask ? newest : queued;
            Future<String> dropped = dropsQueuedTask ? queued : newest;

            System.out.println(name + ": kept="
                    + kept.get(5, TimeUnit.SECONDS));

            // Không dùng dropped.get() vô hạn. Đây là hạn chờ của caller.
            try {
                dropped.get(100, TimeUnit.MILLISECONDS);
                System.out.println(name + ": UNEXPECTED COMPLETION");
            } catch (TimeoutException expected) {
                System.out.println(name + ": dropped TIMEOUT, done="
                        + dropped.isDone());
            }

            // Chủ động chuyển Future thất lạc sang trạng thái canceled.
            dropped.cancel(false);
            System.out.println(name + ": canceled="
                    + dropped.isCancelled());
        } finally {
            release.countDown();
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Pool did not stop");
            }
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac -encoding UTF-8 DroppedFutureDemo.java
java DroppedFutureDemo
~~~

Stdout của chương trình (không gồm dòng từ môi trường chạy):

~~~text
DiscardPolicy: running=running
DiscardPolicy: kept=queued
DiscardPolicy: dropped TIMEOUT, done=false
DiscardPolicy: canceled=true
DiscardOldestPolicy: running=running
DiscardOldestPolicy: kept=newest
DiscardOldestPolicy: dropped TIMEOUT, done=false
DiscardOldestPolicy: canceled=true
~~~

**Giải thích vì sao output xác định:** latch `started` bảo đảm worker đã vào `running` trước khi gửi `queued`. Latch `release` giữ worker không lấy task khác trước khi tác vụ thứ ba bị từ chối. Vì pool chỉ có một worker và một ô queue, điều kiện rejection là xác định. Chính sách giữ/bỏ task theo luật đã nêu; hai `get(5, SECONDS)` là hạn bảo vệ phép thử, còn `get(100, MILLISECONDS)` chứng minh Future bị bỏ vẫn `isDone() == false`. Cuối cùng `cancel(false)` làm Future chuyển trạng thái canceled; nó không “khôi phục” công việc đã bị thất lạc.

Sau `cancel(false)` thành công, nếu gọi `dropped.get()` thì sẽ có `CancellationException` thay cho chờ mãi. Phương thức `cancel` trả boolean phản ánh nỗ lực hủy; muốn biết trạng thái cuối thì đọc `isCancelled()`. Ví dụ không cần network, DB hoặc Spring Boot để tái hiện nguyên nhân gốc; Spring executor bọc JDK pool có cùng loại rủi ro nếu dùng các rejection handler này.

## 5. Cách sửa ưu tiên: từ chối có tín hiệu

### 5.1. Dùng AbortPolicy khi caller cần biết việc có được nhận hay không

Thay `DiscardPolicy` bằng `AbortPolicy`, `ThreadPoolExecutor` sẽ ném `RejectedExecutionException` khi pool đã bão hòa. Với `submit`, exception có thể ném **ngay khi gửi**, trước khi có Future trả về:

~~~java
// Trích đoạn minh họa khi pool đang bão hòa; không phải class đầy đủ.
try {
    Future<String> future = pool.submit(() -> "report");
    // Chỉ lưu future sau khi submit trả về bình thường.
} catch (java.util.concurrent.RejectedExecutionException ex) {
    // Báo quá tải và đánh dấu request/job bị từ chối.
}
~~~

Không dùng `try { future.get() }` để phát hiện rejection sau khi đã bật `DiscardPolicy`: Future có thể không bao giờ có kết quả hay lỗi. Cần đổi **chính sách acceptance** chứ không chỉ đặt một `catch` sau `get()`.

Trong Spring Framework, `ThreadPoolTaskExecutor` có thể ném `org.springframework.core.task.TaskRejectedException` khi từ chối bằng chính sách mặc định kiểu abort. Ở lớp API, trả HTTP `503 Service Unavailable` hoặc `429 Too Many Requests` tùy semantics và chính sách rate limit; không trả `202 Accepted` như thể hệ thống đã tiếp nhận công việc bền vững.

### 5.2. Không bỏ quên Future khi chủ động loại bỏ task

Nếu nghiệp vụ cho phép **thả bớt công việc ít quan trọng**, phải có cơ chế để người đã gửi biết task không còn được chạy:

- Với `FutureTask` bị loại, gọi `cancel(false)` hoặc hoàn tất bằng một exception thông qua một wrapper/gateway quản lý kết quả.
- Với `CompletableFuture` do ứng dụng sở hữu, dùng `completeExceptionally(new RejectedExecutionException(...))` hoặc một trạng thái lỗi/hủy rõ ràng; không phải mọi `Runnable` do `CompletableFuture` tạo đều là `Future<?>` có thể cast để hủy.
- Ghi nguyên nhân `REJECTED`, `DROPPED` hoặc `CANCELLED` vào trạng thái job, metric và log để upstream có thể retry hoặc thông báo cho người dùng.
- Với `DiscardOldestPolicy`, cần xử lý **task vừa bị đẩy khỏi queue**, không chỉ task mới bị từ chối. Do nhiều race (shutdown, polling của worker, nhiều submitters), custom handler cần được kiểm thử kỹ; nếu không cần ưu tiên task mới, nên dùng `AbortPolicy` đơn giản hơn.

Javadoc JDK 17 của `DiscardOldestPolicy` cũng minh họa handler tự lấy task cũ khỏi queue và hủy nó nếu là `Future<?>`. Tuy nhiên chỉ cast/cancel `Runnable` lấy từ queue có thể **chưa đủ** cho wrapper của `CompletableFuture`, callback hoặc một hệ thống job có trạng thái riêng. Phải chọn thiết kế gắn được identity tác vụ với Future/status cần hoàn tất.

## 6. Lớp phòng vệ thứ hai: hạn chờ và terminal state

### 6.1. Future.get(timeout) chỉ giới hạn thời gian chờ

~~~java
// Trích đoạn minh họa bên trong phương thức có thể throws InterruptedException.
try {
    String result = future.get(3, TimeUnit.SECONDS);
    consume(result);
} catch (TimeoutException ex) {
    boolean canceled = future.cancel(true);
    recordTimeout(canceled);
} catch (java.util.concurrent.ExecutionException ex) {
    recordFailure(ex.getCause());
} catch (java.util.concurrent.CancellationException ex) {
    recordCancellation();
}
~~~

`consume`/`recordTimeout`/`recordFailure`/`recordCancellation` là phương thức nghiệp vụ giả định: snippet này **không độc lập**. Hạn `3 giây` chỉ có nghĩa caller chờ tối đa khoảng đó cho `get`. `cancel(true)` gửi yêu cầu hủy và có thể interrupt worker, **không bảo đảm** task đang chạy dừng được, rollback DB hoặc không tiếp tục ghi file. `Future.get(timeout)` tự nó không hủy task.

Khi cần SLA thật, phải có cả **queue-wait budget**, **execution timeout**, timeout I/O/DB và idempotency để tránh một job bị retry khi bản gốc vẫn tiếp tục chạy sau khi caller hết hạn chờ.

### 6.2. CompletableFuture.orTimeout() chuyển Future sang trạng thái terminal

~~~java
// Trích đoạn minh họa: future được ứng dụng tạo và sở hữu.
CompletableFuture<String> future = new CompletableFuture<>();
future.orTimeout(3, TimeUnit.SECONDS);
~~~

Nếu sau 3 giây chưa hoàn tất, chính `CompletableFuture` đó sẽ hoàn tất **exceptionally** với `TimeoutException`; `join()` có thể ném `CompletionException` bọc nguyên nhân. `orTimeout` trả về **chính future đang được cấu hình**, không tạo một bản sao độc lập. Đây là cơ chế tránh một `CompletableFuture` trong bộ nhớ ở trạng thái pending mãi.

Nhưng `orTimeout` **không dừng task thực sự**, không gỡ queued task khỏi executor, không tự cancel `FutureTask` khác và không hoàn tất một job đã lưu DB. Task có thể vẫn tiếp tục side effects sau khi future timeout; một lời gọi `complete(...)` đến muộn sẽ không ghi đè kết quả terminal đã có.

`completeOnTimeout(value, timeout, unit)` hoàn tất bình thường bằng giá trị thay thế, thích hợp cho một số cache/fallback **chỉ khi nghiệp vụ cho phép**. Không nên biến báo cáo tài chính bị mất thành một kết quả thành công giả để che giấu quá tải.

### 6.3. Hạn chờ không thay thế rejection handling

Một phương án vững hơn gồm ba bước:

1. **Submission:** dùng handler ném lỗi và ghi nhận từ chối ngay khi `execute/submit` không nhận việc.
2. **Execution:** task hoàn tất kết quả hoặc exception bằng `try/catch/finally`, không để đường lỗi bị bỏ quên.
3. **Deadline:** đặt hạn chờ ở client và/hoặc timeout trên CompletableFuture, ghi lại trạng thái timeout; quản lý task đang chạy/đang xếp hàng khi hết hạn.

Nếu chỉ triển khai bước 3, hệ thống sẽ **thoát tình trạng chờ vô hạn cho caller** nhưng vẫn âm thầm làm mất tác vụ và có thể để rò rỉ trạng thái nghiệp vụ.

## 7. Kết hợp đúng với Spring ThreadPoolTaskExecutor

### 7.1. Bean executor có giới hạn và AbortPolicy

Ví dụ Spring Framework 6.x chạy trên Java 17, đặt trong một class `@Configuration` (đây là snippet cấu hình, không phải chương trình khởi động độc lập):

~~~java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ReportPoolConfig {
    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(4);
        pool.setQueueCapacity(20);
        pool.setThreadNamePrefix("report-");
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(15);
        return pool;
    }
}
~~~

Với một `@Bean` Spring quản lý, container xử lý lifecycle/initialization của `ThreadPoolTaskExecutor`. Nếu tự khởi tạo ngoài Spring thì phải `initialize()` trước khi chạy. Pool này sẽ dùng tối đa bốn worker và queue 20 theo logic của `ThreadPoolExecutor`; khi queue đầy và toàn bộ worker ở giới hạn, việc nhận task mới thất bại theo `AbortPolicy`.

**Không đánh đồng `maxPoolSize=4` với việc luôn có bốn luồng:** pool ban đầu tăng tới core=2; sau đó ưu tiên queue, và chỉ cân nhắc thêm worker khi queue đã đầy. Khi quá tải phải xử lý exception tại nơi submit, vì nếu không API có thể trả 500 không kiểm soát hoặc đánh dấu sai một job là đã được chấp nhận.

### 7.2. Gateway trả CompletableFuture có đường kết thúc đầy đủ

Ví dụ này là **một class Spring service hoàn chỉnh** để ghép với `ReportPoolConfig` trong cùng ứng dụng Spring đã cấu hình component scanning:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class ReportGateway {
    private final ThreadPoolTaskExecutor executor;

    public ReportGateway(
            @Qualifier("reportExecutor") ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    public CompletableFuture<String> submit(String reportId) {
        CompletableFuture<String> outcome = new CompletableFuture<>();

        try {
            executor.execute(() -> {
                try {
                    // Thay bằng nghiệp vụ thật, có timeout DB/HTTP riêng.
                    String location = "reports/" + reportId + ".csv";
                    outcome.complete(location);
                } catch (Throwable ex) {
                    // Đảm bảo nhánh lỗi có kết quả quan sát được.
                    outcome.completeExceptionally(ex);
                }
            });
        } catch (TaskRejectedException ex) {
            // AbortPolicy: lỗi phát sinh ngay lúc gửi task.
            outcome.completeExceptionally(ex);
        }

        // Phòng vệ cuối: Future không pending quá 30s trong bộ nhớ.
        return outcome.orTimeout(30, TimeUnit.SECONDS);
    }
}
~~~

`execute` có thể ném `TaskRejectedException` lúc pool bão hòa; trong nhánh ấy `outcome` được hoàn tất exceptionally, cho phép client kiểm tra và trả 503. Nếu xử lý nghiệp vụ trong worker bị lỗi, `completeExceptionally` giữ lỗi để downstream quan sát. `orTimeout` bảo vệ lời gọi đợi nếu bị kẹt vì lỗi ngoài ý muốn hoặc ai đó đổi handler thành kiểu bỏ im lặng.

**Giới hạn của gateway:** đoạn worker minh họa tạo chuỗi nhanh, chưa lưu job bền vững, chưa thể bảo đảm exactly-once. Nếu future timeout sau khi worker đã bắt đầu, worker vẫn có thể tiếp tục thao tác và ghi side effect. Trong hệ thống có nhiều bước hoặc ghi DB, cần trạng thái job ở kho bền vững, quản lý deadline và tránh job thành công muộn bị client retry gây trùng.

### 7.3. Nếu dùng @Async

~~~java
// Trích đoạn trong Spring-managed service, gọi qua proxy từ bean khác.
@Async("reportExecutor")
public CompletableFuture<String> build(String id) {
    String result = createReport(id);
    return CompletableFuture.completedFuture(result);
}
~~~

Đoạn trên là snippet; `createReport` do ứng dụng định nghĩa. `@EnableAsync` phải được bật, method phải được gọi qua Spring proxy để áp dụng `@Async` trong proxy mode. Executor được chỉ định bằng tên bean, nên chính sách reject/queue của bean đó có ý nghĩa trực tiếp.

Nếu executor ném `TaskRejectedException` khi submit, exception có thể xuất hiện **ngay ở lời gọi qua proxy**; lỗi phát sinh trong worker được truyền theo semantics của `Future/CompletableFuture`. Không giả định rằng mọi lỗi đều được phát sinh khi `future.join()` hoặc mọi lỗi luôn được ném đồng bộ. Với handler bỏ im lặng, lời gọi qua proxy có thể trả một Future không có ai hoàn tất.

## 8. Chọn kiểu kết quả theo yêu cầu thực tế

### 8.1. Không cần kết quả thì có thể bỏ việc, nhưng phải công khai

Một tác vụ telemetry không quan trọng có thể được phép rơi khi quá tải. Khi đó `execute(Runnable)` kiểu fire-and-forget và metric `dropped_total` có thể là lựa chọn chính sách. Nhưng nếu caller cần xác nhận “đã lưu audit log”, “đã gửi email”, “đã tạo báo cáo”, **không được** coi silently dropped như thành công.

Nếu dùng `DiscardOldestPolicy` cho hoạt động như refresh cache (chỉ cần bản mới nhất), vẫn phải giải quyết callback/awaiters của bản cũ nếu chúng tồn tại. Có thể thay đổi kiến trúc để chỉ giữ một trạng thái “latest” với cơ chế coalescing và một CompletableFuture được hoàn tất có kiểm soát thay vì dùng chính sách queue eviction mù.

### 8.2. Muốn bảo đảm xử lý sau restart cần hàng đợi bền vững

`ThreadPoolTaskExecutor` và `Future` là cấu trúc bộ nhớ của **một tiến trình**. Khi pod chết, mọi Future trong JVM đó cũng biến mất. Nếu nghiệp vụ cần không mất job, ghi yêu cầu vào DB/message broker có transaction/ack và cơ chế retry + idempotency.

Một trình tự thường gặp:

1. HTTP POST kiểm tra idempotency key rồi lưu job `PENDING` vào DB.
2. Transaction commit xong mới dispatch qua broker/outbox hoặc worker đáng tin cậy.
3. Worker chuyển job sang `RUNNING` bằng claim có điều kiện, ghi kết quả `DONE` hoặc `FAILED`, có retry/backoff.
4. API GET tra trạng thái job; khi quá hạn có reconciliation để tìm việc bị treo và chuyển sang trạng thái cần retry/failed theo chính sách.

Tương tác với broker/DB không tự cho exactly-once; cần thiết kế idempotent ở side effects và trạng thái chuyển có điều kiện. Dù dùng queue nào, vẫn phải theo dõi việc quá hạn chờ.

## 9. Shutdown và những Future bị bỏ lại

`shutdown()` cho executor có cơ hội xử lý task đã nhận và đang chờ; `shutdownNow()` cố interrupt task đang chạy và trả về danh sách `Runnable` chưa bắt đầu. JDK không tự cam kết hủy tất cả Future ứng với những Runnable bị trả về. Nếu ứng dụng sở hữu một `ThreadPoolExecutor` và chỉ submit `FutureTask`, có thể hủy những Future nằm trong danh sách này:

~~~java
// Trích đoạn minh họa trong code quản lý ThreadPoolExecutor.
for (Runnable queued : pool.shutdownNow()) {
    if (queued instanceof java.util.concurrent.Future<?> future) {
        future.cancel(false);
    }
}
~~~

Việc này giúp `get()` trên các `FutureTask` vừa bị drain thấy `CancellationException`, nhưng **không hủy được task đã bị discard từ trước** và không áp dụng phổ quát cho mọi wrapper của Spring, `CompletableFuture` hoặc broker. Nếu dùng `ThreadPoolTaskExecutor` do Spring quản lý, cấu hình lifecycle/shutdown thích hợp và ghi nhận job chưa hoàn tất ở tầng ứng dụng; đừng tự gọi `shutdownNow()` lên tài nguyên do framework sở hữu mà không có chiến lược.

Một số chính sách xử lý rejection có thể vứt task khi executor đã shutdown. Vì thế kiểm thử đường dừng/deploy lại là quan trọng tương đương thử đầy queue.

## 10. Kiểm thử để bảo đảm không còn Future “mồ côi”

Một test có ích phải **ép pool bão hòa một cách xác định** bằng latch thay vì chỉ submit nhiều task rồi hy vọng race. Với pool core=max=1 và queue=1:

1. Cho task A vào worker và chặn bằng `CountDownLatch`.
2. Gửi task B vào queue, xác minh queue có một phần tử nếu cần.
3. Gửi task C khiến handler rejection chạy.
4. Với `AbortPolicy`, assert caller nhận `RejectedExecutionException` (JDK) hoặc `TaskRejectedException` (Spring) và đã ghi nhận job `REJECTED`.
5. Với chính sách chủ động drop, xác nhận đúng Future bị loại được `cancel` hoặc `completeExceptionally` trong hạn định; không dùng `get()` vô hạn trong test.
6. Nhả latch rồi xác minh task được nhận hoàn tất, task bị loại mang trạng thái terminal, và không có ID nào mắc ở `PENDING` mãi.

Ngoài unit test, theo dõi runtime: số worker active, `getQueueSize()`, rejection rate, queue wait time, task execution latency, số `PENDING` quá hạn, `Future timeout`, tỷ lệ retry, việc thất lạc callback và số job chuyển trạng thái terminal. Đừng chỉ đếm số task hoàn thành vì những task bị bỏ **không đi tới đường hoàn thành** để được tính.

### Ma trận nguyên nhân thường gặp

| Triệu chứng | Nguyên nhân cần kiểm tra | Hướng khắc phục |
| --- | --- | --- |
| `submit` trả Future nhưng `get()` mãi không về | `DiscardPolicy` loại tác vụ mới | Dùng AbortPolicy; timeout và terminalize kết quả |
| Task mới chạy nhưng Future task cũ pending | `DiscardOldestPolicy` đẩy task cũ khỏi queue | Tránh policy này hoặc cancel/complete job bị đẩy |
| `CompletableFuture.allOf(...)` không hoàn tất | Một future con không bao giờ terminal | Bound timeout và xử lý từng future; tìm upstream task thất lạc |
| Không thấy `RejectedExecutionException` | Rejection handler im lặng | Audit `setRejectedExecutionHandler` và bean runtime |
| `get(timeout)` đã timeout nhưng `isDone()==false` | Timeout chỉ giới hạn thời gian đợi | Quy định việc cancel/terminal state và xử lý retry |
| `orTimeout` đã bắn nhưng DB vẫn được ghi | Timeout của Future không ngừng worker | Cơ chế cancellation, transaction, idempotency |
| Sau shutdown vẫn có waiter treo | Queued task bị drain/drop mà không cancel Future | Ghi nhận/hủy outstanding tasks, xử lý shutdown |
| Spring trả 202 dù job bị từ chối | Không chuyển rejection thành HTTP/job state | Catch `TaskRejectedException` tại gateway, trả 429/503 |
| Queue liên tục đầy và nhiều task bị loại | Mất cân đối tốc độ đến và năng lực xử lý | Bounded queue, backpressure, rate limit, tối ưu tác vụ |

## 11. Câu hỏi tự kiểm tra

1. Vì sao `ExecutorService.submit(...)` trả về bình thường không bảo đảm task sẽ được chạy khi có `DiscardPolicy`?
2. Trong pool một worker và queue một chỗ, task A đang chạy, B xếp hàng, C mới đến: Future nào có thể bị “mồ côi” với `DiscardPolicy` và với `DiscardOldestPolicy`?
3. Sự khác biệt giữa `future.get(3, SECONDS)` bị `TimeoutException` và `CompletableFuture.orTimeout(3, SECONDS)` là gì?
4. `Future.cancel(true)` có bảo đảm giao dịch DB rollback hoặc tác vụ ngừng ngay không?
5. Tại sao `CallerRunsPolicy` cũng không phải cam kết hoàn tất Future trong trường hợp executor đã shutdown?
6. Spring `ThreadPoolTaskExecutor` dùng `AbortPolicy`: rejection nên được bắt ở vị trí nào và phản hồi HTTP/job status ra sao?
7. Nếu ứng dụng bị kill khi job mới ở queue, một `Future` trong RAM có thay thế được kho lưu job bền vững không?
8. Tại sao metric “completed tasks” không đủ để phát hiện task bị discard?

**Quy tắc thực hành:** khi một API hứa trả kết quả bằng `Future`, hãy coi **silent discard** là một lỗi thiết kế cho tới khi đã có đường thông báo terminal tương ứng. Dùng rejection có tín hiệu, bounded wait, completion/cancellation rõ ràng, và theo dõi trạng thái job qua mọi nhánh để không có người gọi nào bị chặn vô hạn.

## Tài liệu tham khảo

- [Java SE 17 — ThreadPoolExecutor và các rejection policy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Java SE 17 — DiscardOldestPolicy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.DiscardOldestPolicy.html)
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overload-rejection-007">Chọn trả lỗi, hạn chế nhận thêm hoặc lưu công việc bền vững</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn trả lỗi, hạn chế nhận thêm hoặc lưu công việc bền vững

**Module:** Spring Framework - concurrency  
**Nhóm:** Quá tải và từ chối  
**Mục tiêu:** Khi hệ thống không thể xử lý ngay tất cả yêu cầu, biết lựa chọn và kết hợp ba chiến lược: **trả lỗi sớm**, **kiểm soát tốc độ/số việc được nhận**, hoặc **ghi nhận công việc bền vững để xử lý sau**. Hiểu chính xác hợp đồng HTTP, ranh giới bảo đảm, giới hạn executor của Spring, phục hồi sau lỗi và cách đo quá tải.

## 1. Bài toán: nhiều yêu cầu đến hơn năng lực xử lý

Giả sử API tạo báo cáo được gọi **100 lần/giây**, nhưng dịch vụ chỉ có thể hoàn tất **40 báo cáo/giây** trong một khoảng thời gian dài. Nếu mỗi lời gọi được xếp vào hàng chờ vô hạn, backlog tăng khoảng 60 việc mỗi giây. Sau 10 phút, riêng lượng việc tồn đọng lý thuyết là khoảng 36.000, chưa kể tác vụ thất bại và retry.

Tăng `queueCapacity` hoặc `maxPoolSize` không tự tạo thêm CPU, kết nối DB hay công suất xử lý của dịch vụ bên ngoài. Khi tốc độ đến vượt tốc độ hoàn tất đủ lâu, **bất kỳ hàng đợi hữu hạn nào cũng sẽ đầy**; hàng đợi vô hạn chỉ chuyển lỗi thành trễ lớn và nguy cơ hết bộ nhớ.

Phân biệt ba khái niệm:

- **Nhận yêu cầu HTTP (received):** server đã thấy request trên kết nối mạng. Chưa đồng nghĩa đã nhận trách nhiệm xử lý nghiệp vụ.
- **Chấp nhận công việc (accepted):** server đã nhận công việc theo hợp đồng cụ thể: có thể chỉ giao vào bộ nhớ, hoặc đã ghi vào kho bền vững.
- **Hoàn thành (completed):** nghiệp vụ tạo ra kết quả cuối cùng, thành công hoặc thất bại.

Vì thế, khi backend bận, phản hồi nào đúng phụ thuộc vào **mức cam kết**:

```text
Client gửi yêu cầu
        |
        v
Xác thực + xác minh payload + quyền + quota
        |
        v
Có thể phục vụ ngay / trong deadline?
        |
       Có ----------------------> Xử lý đồng bộ -> 2xx/4xx/5xx theo kết quả
        |
       Không
        |
        +-- Không thể/không nên nhận --> 429 hoặc 503 có chủ đích
        |
        +-- Chỉ cho phép N việc đồng thời -> admission control/bulkhead
        |                                  |
        |                                  +-- Còn chỗ -> nhận/xử lý
        |                                  +-- Hết chỗ -> từ chối sớm
        |
        +-- Có yêu cầu xử lý sau và đủ khả năng lưu bền vững?
                                           |
                                           +-- Ghi nhận thành công -> 202 + jobId
                                           +-- Không ghi được -> trả lỗi, không hứa đã nhận
```

Ba chiến lược **có thể phối hợp**. Chẳng hạn, API giới hạn mỗi khách hàng 20 job/phút, ghi job vào DB khi còn quota và trả `202`; khi hàng chờ đạt mức an toàn thì trả `503`, dù DB còn dung lượng. Không phải cứ có queue là phải nhận mọi request.

## 2. Bảng quyết định: từ chối, hạn chế nhận, hay lưu bền vững?

| Câu hỏi/điều kiện | Trả lỗi sớm | Hạn chế nhận thêm | Lưu việc bền vững |
| --- | --- | --- | --- |
| Client có thể thử lại mà không gây side effect lặp? | Hợp lý nếu có thông tin retry | Hợp lý để kiểm soát burst | Vẫn cần idempotency khi retry |
| Công việc rất ngắn, cần kết quả trong cùng HTTP response? | Từ chối khi quá tải | **Ưu tiên** giới hạn concurrency, hàng chờ rất nhỏ | Thường không đáng chi phí chuyển sang job |
| Công việc dài, có thể chạy sau và client không cần kết quả tức thì? | Nếu chưa có năng lực nhận việc | Dùng để bảo vệ tốc độ đưa vào queue | **Phù hợp** với job + trạng thái |
| Không được mất yêu cầu sau restart? | Client giữ trách nhiệm retry | Chỉ đủ nếu có cơ chế nhận bền vững | **Cần** DB/queue bền vững + recovery |
| Dịch vụ phụ thuộc DB/API bên ngoài đang nghẽn? | Trả lỗi sớm để ngắt dây chuyền | Bulkhead/rate limit theo downstream | Chỉ queue khi deadline, retry và backlog có kiểm soát |
| Hạn mức khác nhau theo người dùng/tenant? | `429` theo quota | Token bucket + quota per tenant | Kết hợp quota và số job đang chờ per tenant |
| Hệ thống đang tự quá tải hoặc tạm bảo trì? | Thường `503` | Giảm admission, load shedding | Có thể tiếp tục ghi nếu kho job và quota còn khỏe |

**Thứ tự suy nghĩ thực tế:**

1. Xác định **SLO/deadline** của client: cần kết quả trước thời điểm nào?
2. Xác định **bất biến nghiệp vụ**: được phép xử lý trùng không, mất việc có chấp nhận không?
3. Xác định **nguồn nghẽn**: CPU, thread, queue, DB connections, remote quota, RAM, disk hay broker.
4. Xác định **khả năng nhận việc an toàn**: bao nhiêu đang chạy, bao nhiêu được chờ, backlog tối đa và thời hạn sống?
5. Chọn phản hồi và bảo đảm đúng với thực tế; sau đó đặt giới hạn, metrics và kiểm thử lỗi.

## 3. Trả lỗi sớm: giữ server và downstream còn phục vụ được

Khi không có cách hoàn thành trong deadline và không thể nhận job an toàn, **từ chối rõ ràng** tốt hơn treo request rồi timeout. Hai HTTP status hay dùng:

### `429 Too Many Requests`: client/tenant chạm quota hoặc rate limit

Theo RFC 6585, `429` diễn tả **quá nhiều yêu cầu theo một giới hạn tốc độ**. Ví dụ một khách hàng dùng hết 60 lượt xuất báo cáo/phút:

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/problem+json
Retry-After: 30

{
  "type": "https://example.invalid/problems/rate-limit",
  "title": "Rate limit exceeded",
  "status": 429,
  "detail": "Quota for this client is exhausted."
}
```

Đây là **ví dụ hợp đồng HTTP**, không phải response thật của một máy chủ đang chạy. `Retry-After` có thể là số giây hoặc ngày giờ HTTP; chỉ đưa giá trị khi service có căn cứ ước lượng. Không tự trả cùng một số giây cố định cho mọi tình huống nếu không có logic reset quota.

### `503 Service Unavailable`: service tạm không đủ năng lực nhận thêm

Theo RFC 9110, `503` phù hợp với overload tạm thời hoặc bảo trì:

```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/problem+json
Retry-After: 5

{
  "type": "https://example.invalid/problems/overloaded",
  "title": "Service temporarily overloaded",
  "status": 503,
  "detail": "No execution capacity is available."
}
```

Một executor đầy, DB không còn connection hoặc broker không nhận message có thể dẫn tới `503` tùy hợp đồng. Không phải mọi lỗi DB đều là quá tải: lỗi dữ liệu, lỗi cấu hình hay lỗi lập trình cần được phân loại riêng.

**`429` hay `503`?** Một giới hạn áp lên **lượng yêu cầu của caller** thường diễn đạt bằng `429`; **năng lực chung của server** tạm không đáp ứng thường là `503`. Trên thực tế có trường hợp hai nguyên nhân cùng xảy ra; thiết kế mã lỗi nghiệp vụ ổn định để client biết lý do và cách retry. Không mặc định rằng việc đầy thread pool luôn là lỗi của client.

### Chính sách retry ở client

- Chỉ retry thao tác khi **idempotent** hoặc có `Idempotency-Key`/deduplication bảo vệ.
- Dùng exponential backoff + jitter; tôn trọng `Retry-After` khi có. Không cho hàng nghìn client retry lại cùng thời điểm.
- Có **retry budget**, deadline tổng và circuit breaker/bulkhead nếu downstream liên tục lỗi.
- Giới hạn retry tại tất cả các tầng: API gateway, SDK, service nội bộ và worker; nhiều tầng retry tự phát có thể nhân request theo cấp số nhân.
- Trả thông tin có ích: mã lỗi, status, correlation ID, gợi ý thử lại; không lộ exception nội bộ, SQL hoặc token nhạy cảm.

Một request bị từ chối **trước khi thực thi** khác request đã thực hiện một phần rồi bị timeout. Với trường hợp thứ hai, client có thể không biết side effect đã xảy ra. Khi đó idempotency và API tra cứu trạng thái càng quan trọng.

## 4. Hạn chế nhận thêm: kiểm soát *rate*, *concurrency* và *backlog*

**Admission control** quyết định *có nhận một công việc mới vào hệ thống hay không* dựa trên ngân sách thực tế. Đây là lớp phòng vệ ở trước hoặc ngay sát executor/downstream, không chỉ xử lý exception sau khi quá tải.

Ba loại giới hạn khác nhau:

| Giới hạn | Điều gì được hạn chế? | Ví dụ | Không bảo đảm điều gì? |
| --- | --- | --- | --- |
| **Rate limit** (token bucket, sliding window) | Số lời gọi trong một khoảng thời gian | Tối đa 100 POST/phút/tenant | Không giới hạn in-flight nếu task chạy rất lâu |
| **Concurrency limit** (semaphore/bulkhead) | Số thao tác đang hoạt động cùng lúc | Tối đa 8 query tới database | Không giới hạn tổng số request/phút khi mỗi thao tác nhanh |
| **Queue/backlog limit** | Số job chờ thực thi, cùng tuổi tối đa | Tối đa 200 job đang `QUEUED` | Không bảo đảm throughput nếu worker quá chậm |

Có thể cần cả ba: rate limit ngăn một tenant gửi ồ ạt; semaphore giới hạn DB calls; hàng chờ hữu hạn giới hạn độ trễ và RAM.

### Semaphore / bulkhead

`Semaphore` là cách đơn giản giới hạn số việc **đồng thời** trong một JVM:

```java
private final java.util.concurrent.Semaphore dbSlots =
        new java.util.concurrent.Semaphore(8);

public Result queryWithLimit(Request request) {
    if (!dbSlots.tryAcquire()) {
        throw new BackendBusyException();
    }
    try {
        return queryDatabase(request);
    } finally {
        dbSlots.release();
    }
}
```

Đây là **đoạn thành viên minh họa**, cần định nghĩa `Result`, `Request`, `BackendBusyException`, `queryDatabase` trong ứng dụng. Lấy permit trước khi dùng downstream, và **luôn** release trong `finally`. `tryAcquire()` không chờ, giúp fail fast; `tryAcquire(timeout, unit)` chỉ phù hợp nếu ngân sách chờ của request còn đủ.

Lưu ý:

- Nếu service có 5 replica, mỗi replica có semaphore 8 permit thì giới hạn lý thuyết **toàn cụm có thể lên tới 40**, không phải 8. Muốn quota toàn cụm, cần cơ chế điều phối chung hoặc phân bổ ngân sách per replica.
- Nếu đã dùng connection pool có tối đa 8 kết nối, semaphore thêm có thể vẫn hữu ích để từ chối sớm thay vì để hàng chục worker chờ lấy connection, nhưng phải đặt số hợp lý so với connection thực và workload khác.
- Đừng giữ permit qua một thao tác không cần thiết như chờ client download, sleep kéo dài hoặc gửi job đã chuyển sang queue riêng; permit phải bảo vệ đúng tài nguyên đang bị giới hạn.

### Vì sao không chỉ tăng hàng chờ?

Giả sử executor hoàn tất 25 job/giây, bạn cho queue 1.000 job. Một job ở cuối queue có thể chờ cỡ **40 giây** nếu pool chạy đều và không có ưu tiên. Nếu deadline client là 2 giây, hàng chờ đó tạo ra kết quả đến quá muộn. Sizing queue cần dựa trên **thời gian chờ chấp nhận được**, lượng burst và throughput thực, không chỉ số chỗ trống trong RAM.

Little's Law, khi hệ thống tương đối ổn định:

\[
L = \lambda \times T
\]

`L` là số công việc trong hệ thống, `λ` là throughput, `T` là thời gian trung bình lưu lại (bao gồm queue nếu đo toàn hệ thống). Công thức không làm overload biến mất; nó nhắc rằng backlog và latency có quan hệ trực tiếp.

### Công bằng và tách nguồn tải

Một tenant gửi rất nhiều job có thể chiếm queue của mọi tenant khác. Cân nhắc quota theo tenant, weighted fair queue hoặc phân pool/hàng đợi theo loại tác vụ. Job khẩn cấp và batch dài ngày thường không nên dùng chung một queue không ưu tiên. Tách tài nguyên phải dựa trên SLO và dự báo tải; nhiều pool nhỏ tạo thêm chi phí thread và giám sát.

## 5. Spring `ThreadPoolTaskExecutor` từ chối lúc nào?

`ThreadPoolTaskExecutor` dùng `ThreadPoolExecutor` JDK ở dưới và đưa ra hợp đồng `TaskExecutor` của Spring. Tham số trọng tâm:

```java
executor.setCorePoolSize(4);
executor.setMaxPoolSize(8);
executor.setQueueCapacity(16);
executor.setRejectedExecutionHandler(
        new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
```

Đây chỉ là đoạn cấu hình minh họa. Trình tự nhận việc thông thường của `ThreadPoolExecutor`:

1. Khi số worker đang có chưa đạt `corePoolSize`, tạo worker cho task mới.
2. Khi đã đạt core, **đưa task vào queue nếu còn chỗ**.
3. Khi queue không nhận thêm, tạo worker vượt core đến `maxPoolSize`.
4. Khi không thể tạo worker và queue đầy, áp dụng `RejectedExecutionHandler`.

Vì vậy `maxPoolSize=8` **không có nghĩa** executor dùng ngay 8 thread trong khi queue còn rộng. Với queue không giới hạn, pool có thể gần như chỉ chạy bằng core workers, còn backlog tiếp tục phình ra. Một giới hạn **queue hữu hạn** là phần thiết yếu của thiết kế overload.

### Chính sách từ chối

- **`AbortPolicy`** (JDK): ném `RejectedExecutionException`; `ThreadPoolTaskExecutor` trình bày rejection theo hợp đồng Spring `TaskRejectedException`.
- **`CallerRunsPolicy`**: nếu executor chưa shutdown và không nhận task, luồng gửi có thể **tự chạy** task. Với Servlet request thread, đây có thể là block rất lâu; không phù hợp nếu cần trả lỗi nhanh và chắc chắn.
- **`DiscardPolicy`/`DiscardOldestPolicy`**: nguy hiểm cho nghiệp vụ vì task có thể bị bỏ mà caller không biết kết quả; nhất là nếu đã hứa nhận việc.
- Có thể viết policy riêng, nhưng phải định nghĩa rõ khi nào chấp nhận, khi nào báo lỗi và ai chịu trách nhiệm retry.

`ThreadPoolTaskExecutor` mặc định không phải durable queue. Một tác vụ đã vào queue trong JVM có thể **mất khi tiến trình chết**. Khả năng tự từ chối khi đầy là một tầng phòng vệ, không phải bảo đảm xử lý cuối cùng.

### Ví dụ bean Spring có giới hạn rõ

Đây là file cấu hình dùng trong ứng dụng Spring Boot 3.x/Java 17+ có `spring-boot-starter-web` (hoặc Spring Context tương ứng).

**`src/main/java/com/example/overload/ExecutorConfig.java`**:

```java
package com.example.overload;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfig {

    @Bean("shortTaskExecutor")
    public ThreadPoolTaskExecutor shortTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("short-task-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
```

**`src/main/java/com/example/overload/AdmissionService.java`**:

```java
package com.example.overload;

import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class AdmissionService {
    // Giới hạn tổng số việc đã nhận và chưa xong tại instance này.
    private final Semaphore capacity = new Semaphore(24);
    private final ThreadPoolTaskExecutor executor;

    public AdmissionService(
            @Qualifier("shortTaskExecutor") ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    public boolean trySubmit(Runnable action) {
        if (!capacity.tryAcquire()) {
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    action.run();
                } finally {
                    capacity.release();
                }
            });
            return true;
        } catch (TaskRejectedException rejected) {
            capacity.release();
            return false;
        }
    }
}
```

Trong ví dụ, concurrency admission thực chất giới hạn **tổng việc đã nhận (đang chạy + đang chờ)** tối đa 24 per instance, tương ứng với trần 8 worker và 16 queue ở trường hợp đầy tải. Đây là **lớp bảo vệ trong RAM**, không phải kho job.

- Controller có thể gọi `trySubmit` và trả `503` khi nhận `false` nếu overload chung; `429` nếu bị từ chối vì hạn mức riêng của caller. Cần một hợp đồng thành công hợp lệ với loại việc và cách theo dõi của ứng dụng.
- **Không** báo `202` với cam kết durable chỉ vì `trySubmit()` trả `true`: task nằm trong JVM, có thể biến mất sau crash.
- Nếu `action.run()` lỗi, permit vẫn được trả về nhưng lỗi nghiệp vụ phải được log/ghi trạng thái theo cơ chế riêng. Không có job-status store trong ví dụ tối giản này.
- `Semaphore` có phạm vi một bean instance. Nhiều replica cần phối hợp với giới hạn tài nguyên thực hoặc giới hạn toàn cụm.
- Đừng bao một tác vụ ở `@Async` bằng một semaphore rồi giữ permit trên **request thread** qua vòng đời không xác định. Hãy chọn rõ thời điểm acquire/release, tránh leak permit khi reject/hủy/timeout.

### Spring exception → HTTP có chủ đích

Nếu giao việc bị từ chối, controller hoặc `@RestControllerAdvice` có thể ánh xạ `TaskRejectedException` sang `503` cho overload chung; nhưng **không** tự map mọi `IllegalStateException` hay `Exception` thành `503`. Kỹ thuật này nên đi kèm `Retry-After` có căn cứ, log/metric và mã lỗi ổn định. Những task đã nhận rồi nhưng thất bại về sau **không thể** sửa lại HTTP response đã trả.

## 6. Khi nào phải lưu công việc bền vững?

Chọn durable job khi **công việc có giá trị và phải tồn tại sau vòng đời request/JVM**, ví dụ xuất báo cáo dài, đối soát, gửi tài liệu theo yêu cầu, xử lý batch, tạo hợp đồng hoặc workflow nhiều bước. Khi client có thể tra cứu kết quả sau, API có thể trả `202 Accepted` kèm mã job.

Mẫu hợp đồng:

```http
POST /reports
Idempotency-Key: request-2026-0001
Content-Type: application/json

{"period":"2026-09"}
```

```http
HTTP/1.1 202 Accepted
Location: /jobs/8b4f
Content-Type: application/json

{
  "jobId": "8b4f",
  "status": "QUEUED",
  "statusUrl": "/jobs/8b4f"
}
```

```http
GET /jobs/8b4f

HTTP/1.1 200 OK
Content-Type: application/json

{
  "jobId": "8b4f",
  "status": "RUNNING",
  "attempts": 1
}
```

Các giá trị ID và payload ở trên là **minh họa**, không phải phản hồi của chương trình demo. `202 Accepted` theo RFC 9110 diễn tả đã nhận để xử lý nhưng **chưa hoàn thành**, và kết quả sau cùng có thể thành công hoặc không. Nên có resource trạng thái để client tra cứu; `200` của `GET` chỉ chứng minh việc **đọc trạng thái** thành công, không chứng minh job `SUCCEEDED`.

### “Đã nhận” là ranh giới nào?

Bốn tình huống phải tách bạch:

1. **Đã đặt vào `ThreadPoolTaskExecutor` trong RAM:** chỉ bảo đảm delegate tạm nhận tại thời điểm gọi; không an toàn sau crash.
2. **Đã ghi record job trong DB và commit:** job được lưu bền vững theo mức cam kết của database và có thể khôi phục/claim sau restart.
3. **Broker đã xác nhận message được lưu theo cấu hình durable thực tế:** có thể được tiêu thụ sau; phải phân biệt publish thành công với chỉ gọi hàm send. Độ bền phụ thuộc broker/quorum/replication/ack policy.
4. **Đã xử lý side effect xong:** mới là hoàn thành nghiệp vụ; có thể xảy ra nhiều lần thử và xử lý trùng nếu thiết kế không idempotent.

Nếu hợp đồng yêu cầu không mất việc, **chỉ trả `202` sau khi ghi nhận bền vững thành công**. Nếu DB commit thất bại hoặc message chưa được broker xác nhận theo bảo đảm cần thiết, phải báo thất bại/tình trạng không xác định phù hợp; không tạo `jobId` trên RAM rồi hứa chắc chắn xử lý.

### Mô hình lưu job đơn giản

```text
Client -> API (auth/validate/quota)
              |
              | BEGIN TRANSACTION
              v
       jobs(status=QUEUED) ----+----> outbox(event=PROCESS_JOB)
              |               |
              +--- COMMIT -----+
                       |
                       +----> HTTP 202 + jobId
                       |
               Outbox publisher
                       |
                       v
                  Message broker
                       |
                       v
              Worker (claim/dedupe)
                       |
                       v
            jobs: RUNNING -> SUCCEEDED/FAILED
                       |
                       v
                GET /jobs/{jobId}
```

Một worker có thể dùng DB trực tiếp làm queue nếu quy mô thích hợp, với cơ chế **claim/lease** và phục hồi job mồ côi. Broker giúp tách producer/consumer và điều tiết phân phối, nhưng không xóa nhu cầu kiểm soát backlog, idempotency và ghi trạng thái.

## 7. Transactional outbox để không mất công việc giữa DB và broker

Một lỗi thường gặp:

```text
1. INSERT job vào DB và COMMIT.
2. Publish message sang broker.
3. Trả HTTP 202.
```

Nếu tiến trình chết giữa bước 1 và 2, DB có job `QUEUED` nhưng broker không biết để chạy. Đảo thứ tự publish trước commit cũng nguy hiểm: worker có thể chạy cho một transaction sau đó rollback.

**Transactional outbox** ghi job và sự kiện cần phát **trong cùng DB transaction**:

```sql
-- PostgreSQL: cấu trúc minh họa, lược giản để tập trung vào bất biến.
CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_job_idem UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE job_outbox (
    id BIGSERIAL PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES jobs(id),
    event_type VARCHAR(40) NOT NULL,
    payload JSONB NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Trong một transaction, khi nhận yêu cầu mới:

```sql
BEGIN;

WITH inserted AS (
    INSERT INTO jobs (
        id, tenant_id, idempotency_key, request_hash, status
    )
    VALUES (
        :job_id, :tenant_id, :idempotency_key, :request_hash, 'QUEUED'
    )
    ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
    RETURNING id
)
INSERT INTO job_outbox (job_id, event_type, payload)
SELECT id, 'PROCESS_JOB', jsonb_build_object('jobId', id)
FROM inserted;

COMMIT;
```

Đây là **SQL mẫu PostgreSQL dùng named parameters** (`:job_id`, ...) do framework/JDBC client bind; không chạy nguyên văn trong `psql` nếu chưa thay/bind tham số. Logic nhận yêu cầu phải phân biệt **insert mới** với **duplicate idempotency key**:

- Nếu insert mới, outbox được ghi cùng giao dịch; chỉ sau `COMMIT` mới trả `202` + job ID.
- Nếu conflict trên `(tenant_id, idempotency_key)`, tìm job cũ **cùng tenant**, so sánh request hash. Nếu payload giống, trả cùng job ID; nếu khác, trả `409 Conflict` hoặc mã lỗi hợp đồng vì reuse key cho nội dung khác.
- Cần bảo đảm kiểm tra lại sau transaction race, giữ phân quyền và scope tenant; không chỉ nhìn `affectedRows=0` rồi tạo job khác.
- Worker/publisher phải hỗ trợ event trùng do retry/crash. Outbox giúp tránh **mất thông điệp do khoảng trống DB-then-publish**, nhưng không mang lại exactly-once side effect.

Một publisher thường: đọc các outbox record chưa phát, claim bằng lock/lease, publish với broker confirmation, đánh dấu `published_at` sau khi xác nhận. Nếu publish thành công nhưng publisher chết trước khi ghi `published_at`, event có thể **được phát lại**. Đó là lý do consumer bắt buộc idempotent.

**Không đồng nhất “có DB row” với “job sẽ chắc chắn chạy xong”**. DB vẫn có thể hỏng hoặc đầy; worker có thể xử lý lỗi vĩnh viễn; payload có thể không còn hợp lệ. “Bền vững” nói về khả năng lưu/khôi phục trách nhiệm nhận việc, không phải bảo đảm nghiệp vụ luôn thành công.

## 8. Worker, retry, idempotency và khôi phục sau crash

Một job thường đi qua các trạng thái:

```text
QUEUED -> RUNNING -> SUCCEEDED
              |
              +-> RETRY_WAIT -> QUEUED
              |
              +-> FAILED
              |
              +-> CANCELLED (nếu nghiệp vụ hỗ trợ hủy)
```

Khi cần xử lý chắc chắn:

- **Claim một lần tại một thời điểm:** một worker atomically chuyển job đủ điều kiện từ `QUEUED` sang `RUNNING` (hoặc dùng queue consumer ack contract). DB có thể dùng row locking/lease; tránh hai worker cùng claim một job trong thời điểm bình thường.
- **Lease/heartbeat:** nếu worker chết khi job `RUNNING`, job có thể được tái giao sau khi lease hết hạn; worker cũ sống lại cần fencing/điều kiện version nếu có khả năng ghi muộn.
- **Ack đúng thời điểm:** không ack một broker message trước khi nghiệp vụ và trạng thái đã được ghi theo hợp đồng cần thiết, vì crash giữa hai bước có thể mất trách nhiệm xử lý. Nếu ack sau commit, message có thể được giao lặp và consumer phải chịu được.
- **Retry có phân loại:** lỗi tạm thời (network timeout, quá tải downstream) có thể retry với exponential backoff + jitter; lỗi vĩnh viễn (payload sai, không đủ quyền, dữ liệu không tồn tại) cần `FAILED` có giải thích.
- **Hạn mức retry:** `maxAttempts`, retry deadline, dead-letter queue hoặc trạng thái `FAILED`; job không được lặp vô hạn làm nghẽn queue.
- **Idempotent side effect:** chuyển trạng thái bằng điều kiện version; dùng khóa nghiệp vụ duy nhất, bảng dedupe, hoặc request ID được downstream hỗ trợ. Gửi email/thu tiền/gọi webhook có thể lặp nếu sự cố xảy ra ngay sau side effect nhưng trước ack.
- **Out-of-order & cancellation:** nếu có update trạng thái muộn, dùng điều kiện transition hợp lệ và version. `CANCELLED` không được phép bị worker cũ vô tình ghi thành `SUCCEEDED`.
- **Result retention:** quy định bao lâu được `GET /jobs/{id}`, lưu tệp kết quả ở đâu, bảo vệ tenant data, thời hạn tải và dọn rác có kiểm soát.

### Không có “exactly once” miễn phí

Giả sử worker gọi đối tác để thu tiền; đối tác đã thu tiền thành công, nhưng response mất vì network timeout. Worker không biết giao dịch đã thành công nên retry có thể thu lần hai. Cách khắc phục là dùng **idempotency key tại ranh giới side effect**, hoặc cơ chế reconciliation của đối tác; chỉ đặt message vào queue bền vững không tự giải quyết bài toán này.

Điều tương tự áp dụng cho gửi email, chuyển tiền, ghi dữ liệu và webhook. Phân biệt:

- **At-most-once attempt:** có thể mất việc, ít trùng hơn.
- **At-least-once delivery:** giảm nguy cơ mất event bằng retry/ack, nhưng event có thể lặp.
- **Exactly-once business effect:** đòi hỏi idempotency/transaction/fencing hoặc thiết kế nghiệp vụ thích hợp qua mọi hệ thống liên quan; không suy ra chỉ vì broker quảng cáo một tính năng giao nhận.

## 9. Java 17 chạy độc lập: hai kiểu phòng vệ nhanh

Để quan sát **từ chối khi executor đầy** và **giới hạn số việc đang chạy**, ví dụ này chỉ dùng JDK, không phụ thuộc Spring. Nó không giả lập durable storage: lưu bền vững phải được chứng minh qua DB/broker và thử restart/crash, không thể chỉ thay tên một `List` trong RAM thành “queue bền vững”.

Lưu toàn bộ vào file **`OverloadDecisionDemo.java`**:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class OverloadDecisionDemo {

    static boolean rejectWhenExecutorFull() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);

        try {
            Future<?> running = executor.submit(() -> {
                workerStarted.countDown();
                try {
                    releaseWorker.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            if (!workerStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker was not started");
            }

            // Worker duy nhất đang chờ; task này chiếm 1 vị trí queue.
            Future<?> queued = executor.submit(() -> { });

            boolean rejected = false;
            try {
                executor.execute(() -> { });
            } catch (RejectedExecutionException ex) {
                rejected = true;
            }

            releaseWorker.countDown();
            running.get();
            queued.get();
            return rejected;
        } finally {
            releaseWorker.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    static void limitConcurrentAdmissions() {
        Semaphore gate = new Semaphore(2);

        boolean first = gate.tryAcquire();
        boolean second = gate.tryAcquire();
        boolean third = gate.tryAcquire();

        System.out.println(
                "admit=" + first + "," + second + "," + third);

        if (first) {
            gate.release();
        }
        boolean retry = gate.tryAcquire();
        System.out.println("admitAfterRelease=" + retry);

        if (second) {
            gate.release();
        }
        if (retry) {
            gate.release();
        }
        System.out.println("availablePermits=" + gate.availablePermits());
    }

    public static void main(String[] args) throws Exception {
        System.out.println(
                "executorRejected=" + rejectWhenExecutorFull());
        limitConcurrentAdmissions();
    }
}
```

Chạy bằng JDK 17+:

```bash
javac OverloadDecisionDemo.java
java OverloadDecisionDemo
```

**Stdout xác định khi chương trình kết thúc bình thường:**

```text
executorRejected=true
admit=true,true,false
admitAfterRelease=true
availablePermits=2
```

Giải thích:

1. Worker duy nhất bị giữ ở latch, vị trí queue duy nhất đã bị chiếm. Task thứ ba không được nhận, `AbortPolicy` phát `RejectedExecutionException`: `executorRejected=true`. Trong `ThreadPoolTaskExecutor` của Spring, lỗi tương ứng theo contract Spring thường là `TaskRejectedException`.
2. Semaphore có 2 permit; hai lời gọi đầu lấy được, lời gọi thứ ba bị từ chối ngay: `true,true,false`.
3. Sau khi trả một permit, lần thử mới được nhận; cuối cùng trả hết permit đã lấy nên `availablePermits=2`.
4. Trình tự được điều khiển bằng latch nên kết quả **không phụ thuộc** lịch chạy thread khi chạy bình thường; không dùng thời gian sleep để dự đoán kết quả.

**Không nên lấy throughput từ chương trình nhỏ này** để chọn pool production: nó là thử nghiệm tính chất admission/rejection. Khi load test thực tế, bổ sung request traffic, downstream, thời gian chờ, queue age, timeout và trường hợp crash.

## 10. Chọn phản hồi HTTP cho từng tình huống

Một quy tắc thiết kế tiện dụng:

| Tình huống | Phản hồi thường hợp lý | Công việc đã được nhận chưa? |
| --- | --- | --- |
| Tenant hết hạn mức 100 request/phút, chưa tạo job | `429` + mã quota; có thể có `Retry-After` | Chưa |
| CPU/worker pool bão hòa, deadline không thể đáp ứng | `503`; có thể có `Retry-After` | Chưa |
| Semaphore bảo vệ DB đã hết permit | `503` khi lỗi công suất chung, hoặc `429` khi giới hạn riêng caller | Chưa |
| Gửi vào executor RAM thành công, API chỉ cam kết best-effort | Có thể `202` nếu hợp đồng mô tả rõ mức bảo đảm và cách theo dõi | Đã nhận theo **RAM**, có thể mất khi crash |
| DB job + outbox đã commit, công việc sẽ được worker xử lý sau | `202 Accepted` + `Location`/job ID | Đã nhận **bền vững**, chưa hoàn tất |
| DB commit thất bại trước khi xác nhận nhận job | `5xx` phù hợp, không được khẳng định `202` durable | Có thể chưa nhận; cần xử lý trường hợp response không chắc chắn |
| Client retry cùng idempotency key và cùng payload | Trả về cùng job và trạng thái hiện có theo hợp đồng | Không tạo job trùng |
| Client dùng lại cùng key nhưng payload khác | `409 Conflict` hoặc lỗi theo hợp đồng | Không nhận job mới |
| Job xử lý xong nhưng thất bại nghiệp vụ | `GET /jobs/{id}` trả `200` với `FAILED`, hoặc API trạng thái chuyên biệt | Đã nhận trước đó, nay có kết quả thất bại |

**Lưu ý về lỗi không chắc chắn:** đôi khi DB **đã commit** nhưng kết nối giữa DB và API bị lỗi trước khi API thấy kết quả, hoặc response `202` đã gửi nhưng client timeout. Trong trường hợp đó không thể kết luận chắc chắn “chưa nhận”. Cần **idempotency key + tra cứu trạng thái** để client retry an toàn, tránh tạo job lặp.

## 11. Các tình huống lựa chọn chiến lược

### Tình huống A — API gợi ý sản phẩm phải trả trong 200 ms

Nếu tính toán CPU hoặc downstream kéo dài hơn deadline, queue dài sẽ biến câu trả lời thành vô dụng. Chọn **giới hạn số request đang xử lý**, queue rất ngắn hoặc không queue, từ chối khi quá tải. Client có thể dùng cached/fallback response theo hợp đồng; không cần durable job nếu kết quả không còn giá trị khi quay lại sau một phút.

### Tình huống B — Xuất báo cáo kế toán mất 5 phút

Người dùng không cần giữ kết nối HTTP 5 phút; mất yêu cầu sau restart là không chấp nhận. Chọn **lưu job vào DB + outbox/worker**, trả `202` + trạng thái. Vẫn đặt quota theo tenant và backlog limit; khi DB không thể lưu hoặc backlog vượt giới hạn cam kết, trả `429`/`503`.

### Tình huống C — Gửi thông báo marketing số lượng lớn

Các task có thể chạy sau, nhưng rate limit của nhà cung cấp email rất thấp. Chọn durable queue, worker xử lý có tốc độ giới hạn (token bucket/concurrency cap), retry và idempotency. Nếu nhà cung cấp gặp sự cố, không tăng vô hạn worker/queue; dùng circuit breaker, lùi thời gian retry và quản lý TTL của chiến dịch.

### Tình huống D — Đặt hàng còn một sản phẩm

Nếu quá tải dẫn tới hai request cùng đặt thành công, queue không tự bảo vệ bất biến tồn kho. Cần **transaction/conditional update trong DB**, idempotency cho lệnh tạo đơn; chọn synchronous hoặc durable job tùy hợp đồng giao dịch. Không hứa thành công khi mới ghi job: `202` chỉ là accepted, chưa phải xác nhận đã có hàng.

### Tình huống E — Hệ thống nhiều tenant, một khách hàng gây burst

Chọn rate limit per tenant (`429`), bulkhead bảo vệ tài nguyên chung, queue fairness/priority nếu có job bền vững. Giới hạn `Semaphore` cục bộ ở 10 replica phải được tính theo **tổng mức tiếp nhận**; nếu muốn quota 200 job/minute trên toàn hệ thống, cần nguồn đếm và thuật toán phân tán tương ứng.

## 12. Vận hành: lấy tín hiệu quá tải từ đâu?

Đo đồng thời **lưu lượng nhận** và **khả năng hoàn tất**:

- **Request ingress:** rate theo endpoint/tenant, status `429`/`503`/`202`, API p95/p99, timeout và tỷ lệ retry.
- **Executor:** `activeCount`, pool size, queue depth, **tuổi job lâu nhất**, số lần reject, thời gian chờ trước khi chạy, runtime theo loại việc.
- **Downstream:** connection pool active/pending, p95 latency, timeout, rate limit, circuit breaker open count.
- **Durable job:** số `QUEUED`, `RUNNING`, `RETRY_WAIT`, tỷ lệ `SUCCEEDED`/`FAILED`, số lần thử, backlog age, dead-letter, mất heartbeat, outbox lag, thời gian từ `202` đến hoàn tất.
- **Hạ tầng:** CPU throttling, memory, GC pause, file descriptor/socket exhaustion, disk và DB storage.
- **Công bằng:** backlog và rejection theo tenant/priority, tránh một nhóm dùng hết tài nguyên nhóm khác.

Một vài tín hiệu và phản ứng:

| Quan sát | Nguyên nhân có thể | Hành động cần cân nhắc |
| --- | --- | --- |
| Active threads cao, queue không ngừng tăng | Arrival > completion hoặc downstream chậm | Admission/rate limit, giảm work, scale thực sự |
| CPU còn dư, đa số worker chờ JDBC connection | DB pool hoặc query nghẽn | Giới hạn concurrency, tối ưu query, điều chỉnh pool theo DB |
| `503` giảm nhưng p99 tăng mạnh sau khi tăng queue | Đẩy áp lực vào thời gian chờ | Giảm queue theo deadline, báo overload sớm |
| Outbox tăng, broker/worker không nhận | Publisher lỗi, broker không sẵn sàng | Khôi phục publisher, giám sát lag, ngừng nhận vượt quota |
| `FAILED`/retry tăng sau outage | Retry storm hoặc downstream còn yếu | Backoff + jitter, giới hạn retry, circuit breaker |
| Queue gần như trống nhưng CPU quá tải | Worker làm việc CPU-heavy | Giới hạn concurrency CPU, profile code, tránh oversubscription |

**SLO theo từng chiến lược cũng khác:**

- Trả lỗi sớm: xác suất từ chối có chủ đích, thời gian phản hồi từ chối, không để server suy sụp.
- Admission control: tỷ lệ accepted/rejected, thời gian chờ permit và tác động giữa nhóm tải.
- Durable job: tỷ lệ job được nhận vẫn còn sau restart, độ trễ đến khi hoàn tất, độ trễ publish outbox, retry và job mắc kẹt.

Không tối ưu chỉ bằng “tỷ lệ HTTP 2xx cao”. Một API trả hàng nghìn `202` trong khi job không bao giờ chạy có thể tệ hơn việc từ chối sớm đúng lúc.

## 13. Những lỗi thường gặp

| Hiểu sai / triển khai sai | Hậu quả | Cách sửa |
| --- | --- | --- |
| “Queue càng dài càng tốt” | OOM, request quá hạn, độ trễ hàng phút | Hữu hạn, tính theo deadline và throughput |
| `maxPoolSize` rất lớn, nhưng queue không giới hạn | Pool không mở rộng như mong đợi, backlog vẫn tăng | Hiểu queue-before-grow, cấu hình core/max/queue đồng bộ |
| Trả `202` ngay sau `executor.execute()` và gọi là *durable* | Crash làm mất job đã hứa xử lý | Commit job/outbox hoặc durable broker trước `202` |
| Trả `429` cho mọi lỗi backend | Client bị quy lỗi dù server/DB bão hòa | Phân biệt quota caller và overload server |
| `CallerRunsPolicy` cho request CPU-heavy | Request thread chạy task, latency đột biến | Chọn policy theo SLO, có thể dùng `AbortPolicy` + từ chối |
| Bắt mọi exception rồi trả `503` | Che giấu lỗi lập trình/vĩnh viễn | Chỉ map đúng lỗi capacity/overload |
| Retry vô hạn, không jitter | Retry storm, amplifies outage | Retry budget, backoff, deadline, dead-letter |
| Chỉ token bucket mà không giới hạn in-flight | Request ít nhưng xử lý rất lâu vẫn giữ hết worker | Kết hợp concurrency limit và timeout |
| Chỉ semaphore ở mỗi instance khi quota là toàn cụm | Tổng số được nhận vượt quota mong muốn | Phân phối ngân sách hoặc cơ chế rate limit chung |
| Publish message ngoài transaction sau DB commit | Crash giữa bước gây job không được tiêu thụ | Transactional outbox/DB queue |
| Coi message broker mặc định là exactly-once | Side effect bị lặp khi redelivery | Consumer idempotent, deduplication, transaction/fencing |
| Ack trước khi cập nhật trạng thái/xử lý | Crash làm mất trách nhiệm xử lý | Ack theo hợp đồng sau ghi bền vững |
| Không có cơ chế reclaim `RUNNING` sau worker crash | Job kẹt vĩnh viễn | Lease/heartbeat/reconciliation |
| Không phân quyền `GET /jobs/{id}` | Lộ kết quả/metadata của tenant khác | Kiểm tra quyền và scope tenant trên mọi thao tác |
| Dùng test sleep làm sizing production | Ước lượng sai CPU/DB/network | Load test với tải và dependency thực |

## 14. Checklist thiết kế trước khi triển khai

1. **Ghi hợp đồng:** client cần kết quả trong response hiện tại hay có thể nhận `jobId`? Khi nào `202` được phát? Được mất job sau restart không?
2. **Xác định ngân sách:** throughput thực, latency SLO, queue wait, RAM, CPU, DB connections, broker throughput và hạn mức đối tác.
3. **Phân loại overload:** quota per caller → `429`; công suất chung tạm hết → `503`; chỉ `202` khi thực sự nhận được việc.
4. **Đặt rate/concurrency/queue cap:** giới hạn tại nơi tiêu thụ tài nguyên; kiểm soát tổng qua các instance.
5. **Đặt chính sách executor:** core, max, queue, rejection handler, timeout; kiểm tra `CallerRunsPolicy` có chặn request thread không.
6. **Nếu durable:** lưu job và outbox atomically, trả cùng job cho retry có cùng idempotency key, publish với xác nhận, consumer idempotent.
7. **Xử lý crash window:** sau commit trước response, sau publish trước ack, sau side effect trước ghi `SUCCEEDED`, worker chết khi `RUNNING`.
8. **Đặt TTL, retry và hủy:** giữ trạng thái lỗi có thể chẩn đoán, dead-letter, quyền truy cập kết quả và dọn dữ liệu cũ.
9. **Đo và kiểm thử:** tải vượt công suất, burst, DB/broker downtime, restart, nhiều replica, client retry, downstream latency và quota per tenant.
10. **Rà soát bảo mật:** xác thực, phân quyền, payload limit, quota fairness, dữ liệu cá nhân trong job/outbox, secret handling.

## 15. Câu hỏi tự kiểm tra

**1.** API nhận 200 request/giây nhưng chỉ xử lý được 80 request/giây trong hàng giờ. Có nên chỉ tăng queue lên 1 triệu không?  
**Đáp:** Không. Backlog tích lũy và thời gian chờ tăng liên tục. Cần tăng công suất thực, giảm lượng công việc hoặc giới hạn/từ chối nhận.

**2.** Một tenant vượt 20 POST/phút, còn server vẫn khỏe. Trả mã gì?  
**Đáp:** Thường `429 Too Many Requests` kèm mã quota, có thể có `Retry-After` nếu biết mốc thử lại.

**3.** Pool worker bị đầy vì downstream timeout, không còn khả năng nhận tác vụ. Trả mã gì?  
**Đáp:** Thường `503 Service Unavailable` cho overload tạm thời; bảo đảm request bị từ chối chưa bị ghi nhận thành accepted.

**4.** Vì sao một `Semaphore(10)` trong mỗi replica của cụm 6 replica không bảo đảm tổng cùng lúc chỉ có 10 task?  
**Đáp:** Mỗi replica có 10 permit riêng; lý thuyết toàn cụm có thể đạt 60.

**5.** `ThreadPoolTaskExecutor(core=4,max=12,queue=200)` có tăng lên 12 worker trước khi queue đầy không?  
**Đáp:** Thông thường không. Khi đạt core, queue được ưu tiên trước; vượt core khi queue không nhận thêm.

**6.** API trả `202` ngay khi đưa việc vào một queue RAM, sau đó JVM chết. Có đáp ứng yêu cầu “job không được mất” không?  
**Đáp:** Không. Cần durable store hoặc broker với xác nhận phù hợp trước khi nhận trách nhiệm.

**7.** DB ghi job thành công nhưng publish broker thất bại giữa hai bước. Cách tránh “job bị bỏ quên” là gì?  
**Đáp:** Lưu job + outbox trong một DB transaction, dùng publisher phát lại và consumer idempotent; hoặc dùng một durable DB queue/thiết kế khác có recovery rõ ràng.

**8.** Broker gửi cùng message hai lần do crash sau side effect và trước ack. Có thể tránh side effect lặp chỉ bằng cấu hình queue không?  
**Đáp:** Không đủ; phải dùng idempotency key, deduplication và/hoặc transaction/fencing ở ranh giới side effect.

**9.** Có nên gửi `Retry-After: 5` khi không biết service sẽ phục hồi sau 5 giây?  
**Đáp:** Chỉ nên công bố khi có căn cứ; nếu không biết, không bịa thời điểm phục hồi. Client vẫn cần backoff/jitter theo chính sách.

**10.** Job `RUNNING` từ hôm qua, worker đã chết. Vấn đề là gì?  
**Đáp:** Thiếu lease/heartbeat và quy trình reclaim/reconciliation. Durable job không đồng nghĩa tự phục hồi nếu trạng thái bị kẹt.

## 16. Ghi nhớ và tài liệu tham khảo

Ba lựa chọn trả lời ba câu hỏi khác nhau:

- **Trả lỗi sớm:** hệ thống **không nhận** công việc này; client biết có thể retry khi nào/với điều kiện gì. Dùng `429` cho quota/rate limit của caller, `503` cho overload tạm thời của service theo hợp đồng.
- **Hạn chế nhận thêm:** hệ thống chỉ nhận trong giới hạn **rate, concurrency, queue, downstream** có thể phục vụ; khi hết ngân sách phải từ chối rõ ràng.
- **Lưu công việc bền vững:** hệ thống **nhận trách nhiệm xử lý về sau** sau khi ghi nhận an toàn; trả `202` và job ID, có cơ chế trạng thái, retry, dedupe và recovery. Không hứa nghiệp vụ đã hoàn tất.

Các nguồn chính thức:

- [RFC 9110 — 202 Accepted](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.3.3), [503 Service Unavailable](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.6.4) và [Retry-After](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3).
- [RFC 6585 — 429 Too Many Requests](https://www.rfc-editor.org/rfc/rfc6585.html#section-4).
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html).
- [Spring Framework — `ThreadPoolTaskExecutor` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html).
- [Java SE 17 — `ThreadPoolExecutor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html).
- [Java SE 17 — `Semaphore`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overload-rejection-008">Không lặp gửi lại tức thời làm quá tải nghiêm trọng hơn</a>

<details>
<summary>Xem chi tiết</summary>

# Không lặp gửi lại tức thời làm quá tải nghiêm trọng hơn

> **Module:** Spring Framework – concurrency  
> **Chủ đề cha:** Quá tải và từ chối  
> **Mục tiêu:** Hiểu vì sao việc gửi lại ngay sau khi bị từ chối hoặc gặp lỗi tạm thời có thể biến tình trạng quá tải thành sự cố dây chuyền; biết thiết kế giới hạn lần thử, thời hạn, backoff, jitter và cơ chế điều phối retry không làm nghẽn thêm executor.

## 1. Vấn đề: hệ thống đang bận lại bị ép làm nhiều hơn

Giả sử một `ThreadPoolTaskExecutor` của Spring đang chạy hết số worker và queue cũng đầy. Lời gọi `execute()` hoặc `submit()` bị từ chối theo chính sách của pool (ví dụ `AbortPolicy` dẫn tới `TaskRejectedException`). Người viết mã thấy lỗi và lập tức gọi lại:

~~~java
for (;;) {
    try {
        executor.execute(task);
        break;
    } catch (TaskRejectedException rejected) {
        // Thử ngay lần nữa, không có giới hạn.
    }
}
~~~

Đây là **phản ví dụ**, không phải đoạn mã nên áp dụng: biến `executor`, `task` và import Spring được lược bỏ. Vòng lặp có thể chiếm CPU của luồng gọi, nộp lại cùng công việc hàng nghìn lần mỗi giây mà không tạo ra một worker hay chỗ trống trong queue. Nếu hàng trăm request cùng thực hiện, chính cơ chế “cố chữa lỗi” làm nghẽn thêm ứng dụng.

Khi sự cố nằm ở một dịch vụ bên ngoài, tình huống cũng tương tự: nếu backend trả `429 Too Many Requests`, `503 Service Unavailable` hoặc timeout do quá tải, tất cả caller lập tức gọi lại sẽ bổ sung lượng yêu cầu ngay lúc backend cần giảm tải để phục hồi. Các yêu cầu retry sử dụng cùng connection pool, CPU, thread và network, kéo dài thời gian xử lý của cả yêu cầu mới.

**Nguyên tắc:** Thất bại do quá tải là tín hiệu để **giảm tốc hoặc dừng nhận thêm việc**, không phải tín hiệu cho một vòng lặp thử lại tức thời.

## 2. Vòng phản hồi dương tạo ra retry storm

Một luồng khuếch đại tải điển hình:

~~~text
Nhiều yêu cầu mới
       |
       v
Pool/DB/downstream đã gần quá tải
       |
       v
Queue chờ dài, deadline vượt ngưỡng, reject/timeout tăng
       |
       v
Caller retry ngay -> tăng số lượt nộp/call
       |
       v
Thêm CPU + thread + connection + queue contention
       |
       v
Latency và lỗi tăng -> càng nhiều retry -> vòng lặp lặp lại
~~~

**Retry storm** là làn sóng yêu cầu thử lại tự duy trì hoặc làm trầm trọng tình trạng quá tải. Khi nhiều client retry cùng một nhịp, có thêm hiện tượng *thundering herd*: nhiều yêu cầu cùng đổ vào backend đúng thời điểm nó vừa mở lại khả năng nhận tải.

Thử lại có thể hữu ích khi lỗi ngắn, không thường xuyên và hệ thống đã có thời gian hồi phục. Nhưng khi nguyên nhân là **thiếu công suất kéo dài**, mỗi lần thử lại là một yêu cầu mới tiêu tốn tài nguyên, thường có khả năng tiếp tục thất bại.

## 3. Nhìn bằng số: một yêu cầu có thể biến thành bao nhiêu lần gọi?

Giả sử có 1.000 yêu cầu nghiệp vụ/s. Nếu mỗi yêu cầu bị lỗi đều được phép thử tối đa **bốn lần thực thi** (lần đầu + ba lần retry) và backend liên tục thất bại, số *attempt* có thể đạt **4.000/s**. Backend chỉ chịu được 800/s thì 4.000 lần thử không làm tăng số kết quả thành công; nó làm việc xếp hàng và phục hồi khó hơn.

Với xác suất một attempt thất bại là `p`, giả định các attempt độc lập và cùng xác suất lỗi, số attempt kỳ vọng khi có tối đa `k` lần thực thi là:

`E[attempts] = 1 + p + p² + ... + p^(k-1)`

Ví dụ `p=0,8`, `k=4`: kỳ vọng `1 + 0,8 + 0,64 + 0,512 = 2,952` lượt cho mỗi yêu cầu. Đây chỉ là mô hình giải thích; trong thực tế, những lỗi quá tải thường **không độc lập**: các attempt liên tiếp đúng giai đoạn nghẽn có thể đều thất bại.

Một nguy cơ khác là **retry tại nhiều tầng**. Nếu gateway, service trung gian và client đến DB đều tự cho phép ba lần thực thi, một hành động ở trên có thể tạo đến `3 × 3 × 3 = 27` lượt truy cập tầng thấp nhất khi các tầng đều phải thử đủ số lần. Cần phân định rõ tầng nào sở hữu retry để tránh nhân bội không chủ ý.

## 4. Phân biệt ba trường hợp thất bại trước khi retry

| Trường hợp | Ý nghĩa | Cách phản ứng |
| --- | --- | --- |
| `TaskRejectedException` ngay khi nộp vào Spring executor | Pool không nhận thêm công việc theo chính sách lúc đó, hoặc đang đóng | Không nộp lại ngay; phản hồi backpressure, lên lịch trễ có giới hạn nếu được phép, hoặc chuyển việc vào queue bền vững |
| Task đã được nhận nhưng gọi dịch vụ khác bị timeout/`429`/`503` | Công việc có thể đã chạy một phần; kết quả ở hệ thống đích có thể chưa rõ | Xác định lỗi tạm thời, idempotency, deadline; có thể retry với backoff và tôn trọng hướng dẫn từ backend |
| Lỗi vĩnh viễn: dữ liệu không hợp lệ, vi phạm bất biến, lỗi lập trình, quyền không hợp lệ | Lặp lại không làm yêu cầu hợp lệ hơn | Fail fast; trả lỗi đúng nguồn gốc và sửa input/code/quyền |

**Lưu ý quan trọng:** `TaskRejectedException` có thể phát sinh **đồng bộ từ lời gọi nộp**, trong khi exception bên trong `Runnable` đã được nhận không nhất thiết quay về luồng caller. Với `submit()`, lỗi của task có thể được giữ trong `Future`; cần `Future.get()` hoặc cơ chế quan sát phù hợp. Không được bắt lỗi từ lời gọi `submit` rồi nghĩ đã bao quát mọi lỗi xảy ra khi chạy.

Ngoài ra, timeout không chứng minh thao tác từ xa **chưa thực hiện**. Nếu một yêu cầu thanh toán đã được ghi nhận nhưng phản hồi mạng bị mất, retry không có idempotency key có thể dẫn đến thanh toán lặp.

## 5. Backoff: cho hệ thống thời gian lấy lại công suất

**Backoff** là khoảng dừng có chủ ý trước lần thử lại. Thay vì thử ngay, client giãn cách các lượt gọi và dừng sau một ngân sách hữu hạn.

Một công thức dễ hiểu là **exponential backoff có trần**:

`delay(n) = min(maxDelay, baseDelay × 2^(n-1))`

Trong đó `n = 1` là **lần retry đầu tiên sau khi attempt ban đầu thất bại**, `baseDelay` là thời gian chờ cơ bản, còn `maxDelay` tránh để khoảng chờ tăng vô tận.

Với `baseDelay = 200 ms` và `maxDelay = 1.600 ms`:

| Lần retry n | Thời gian trước retry khi chưa jitter |
| --- | ---: |
| 1 | 200 ms |
| 2 | 400 ms |
| 3 | 800 ms |
| 4 | 1.600 ms |
| 5 trở đi | 1.600 ms (chạm trần) |

Đây chỉ là **cách tính thời gian**, không phải chỉ dẫn “cứ retry mãi”. Một chính sách hoàn chỉnh còn giới hạn tổng lần thực thi, thời hạn nghiệp vụ và mức độ retry của toàn dịch vụ.

Không cần dùng `Thread.sleep()` trên worker của pool đang quá tải để thực hiện backoff. Cách đó giữ một worker vô ích, khiến năng lực xử lý vốn thiếu càng nhỏ hơn.

## 6. Vì sao chỉ exponential backoff vẫn chưa đủ?

Nếu 10.000 client đồng thời gặp lỗi và cùng dùng dãy `200 ms → 400 ms → 800 ms`, chúng vẫn có thể phát hàng nghìn request **cùng một khoảnh khắc** sau mỗi mốc. Làn sóng retry được trì hoãn nhưng vẫn đồng bộ.

**Jitter** thêm biến thiên ngẫu nhiên vào khoảng đợi để phân tán lượt thử lại theo thời gian. Một số chính sách thường dùng:

| Chính sách | Cách chọn thời gian | Đặc điểm |
| --- | --- | --- |
| Không jitter | `delay(n)` cố định cho mọi client | Dễ thực hiện nhưng dễ đồng bộ retry |
| Full jitter | Ngẫu nhiên trong `[0, delay(n)]` | Phân tán mạnh; có thể nhận giá trị 0, nên cần cân nhắc yêu cầu không gửi ngay |
| Equal jitter | Ngẫu nhiên trong `[delay(n)/2, delay(n)]` | Có một mức chờ tối thiểu, vẫn phân tán được lượt retry |
| Jitter theo tín hiệu server | Tôn trọng mốc `Retry-After`, thêm phân tán nếu hợp đồng và deadline cho phép | Giảm tranh chấp khi server đã biết thời gian phục hồi tối thiểu |

Với mục tiêu **không gửi lại tức thời**, equal jitter là một lựa chọn minh họa: lần retry đầu tiên có khoảng đợi `100–200 ms` khi delay danh định là `200 ms`; lần thứ hai `200–400 ms`, v.v. Đây **không phải** công thức tối ưu cho mọi tải; mức đợi phải dựa trên SLO, thời gian hồi phục, số caller và hạn xử lý.

Mỗi instance nên có nguồn ngẫu nhiên phù hợp để tránh các client cùng tạo chuỗi delay giống nhau. Không cần tự triển khai bộ sinh số ngẫu nhiên mới khi JDK đã có `ThreadLocalRandom` hoặc cơ chế phù hợp.

## 7. Giới hạn số lần thử, deadline và retry budget

Ba giới hạn giải quyết ba dạng thất bại:

1. **maxAttempts:** ví dụ tổng cộng bốn attempt (một ban đầu, tối đa ba retry), rồi kết thúc với trạng thái thất bại rõ ràng. Đừng nhầm `maxRetries=3` với `maxAttempts=3`.
2. **Deadline end-to-end:** nếu nghiệp vụ chỉ cho phép ba giây, mọi kết nối, chờ pool, backoff và các retry đều phải nằm trong hạn này. Không bắt đầu retry nếu delay cộng thời gian xử lý dự kiến đã vượt deadline.
3. **Retry budget toàn dịch vụ:** dù mỗi yêu cầu được tối đa ba retry, tổng số retry trên tất cả yêu cầu vẫn phải bị khống chế. Ví dụ cấp một số token retry hữu hạn cho mỗi giây hoặc mỗi cửa sổ thời gian để một đợt lỗi không tự nhân tải.

Một deadline có thể làm cho yêu cầu không được thử lại dù chưa chạm `maxAttempts`; retry budget cũng có thể yêu cầu bỏ một retry nếu toàn dịch vụ đang nghẽn. Đây là hành vi đúng khi hệ thống cần bảo vệ khả năng phục vụ các request mới.

**Cần có số đo:** tách số lần gọi ban đầu và số lần thử lại; theo dõi `retry_attempts_total`, `retry_exhausted_total`, `rejected_tasks_total`, độ trễ chờ retry và số yêu cầu hết deadline. Nếu không phân biệt, retry có thể bị ngụy trang thành “lưu lượng tự nhiên”.

## 8. Ví dụ Java 17+ chạy độc lập: không retry ngay, không chiếm thread để ngủ

Ví dụ sau dùng `ScheduledExecutorService` để minh họa ba attempt: hai lần đầu gặp lỗi tạm thời, mỗi lần đều được **lên lịch sau một khoảng đợi**; lần thứ ba thành công. Đây là mô phỏng local có kết quả xác định; không cần thư viện Spring.

Tạo tệp **ScheduledRetryDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ScheduledRetryDemo {
    private static final int MAX_ATTEMPTS = 3;
    private static final ScheduledExecutorService TIMER =
            Executors.newSingleThreadScheduledExecutor();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final CountDownLatch FINISHED = new CountDownLatch(1);

    private static long delayMillis(int failedAttempt) {
        return Math.min(800L, 100L << (failedAttempt - 1));
    }

    private static void attempt(int number) {
        int call = CALLS.incrementAndGet();

        if (call <= 2) {
            System.out.println("Attempt " + call + ": transient failure");

            if (number >= MAX_ATTEMPTS) {
                System.out.println("Retry budget exhausted");
                FINISHED.countDown();
                return;
            }

            long delay = delayMillis(number);
            System.out.println("Retry after " + delay + " ms");
            TIMER.schedule(() -> attempt(number + 1),
                    delay, TimeUnit.MILLISECONDS);
            return;
        }

        System.out.println("Attempt " + call + ": success");
        FINISHED.countDown();
    }

    public static void main(String[] args) throws InterruptedException {
        try {
            TIMER.execute(() -> attempt(1));
            if (!FINISHED.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Retry demo timed out");
            }
        } finally {
            TIMER.shutdown();
            if (!TIMER.awaitTermination(5, TimeUnit.SECONDS)) {
                TIMER.shutdownNow();
            }
        }
    }
}
~~~

Chạy với Java 17 trở lên:

~~~bash
javac ScheduledRetryDemo.java
java ScheduledRetryDemo
~~~

**Stdout do chương trình in** khi máy có thể thực thi ba task trong thời hạn chờ năm giây:

~~~text
Attempt 1: transient failure
Retry after 100 ms
Attempt 2: transient failure
Retry after 200 ms
Attempt 3: success
~~~

Giải thích:

- `TIMER.schedule()` chỉ đăng ký lần chạy sau, không vòng lặp thử ngay và không dùng `Thread.sleep()` để giữ một worker trong thời gian backoff.
- `MAX_ATTEMPTS=3` nghĩa là tối đa **ba lần gọi tổng cộng**, không phải ba lần retry sau lần đầu.
- Ví dụ cố ý dùng thời gian cố định `100 ms` và `200 ms` để stdout dễ kiểm tra. Trong hệ thống thật nên bổ sung jitter và deadline.
- Scheduler này thực thi công việc giả lập cực ngắn ngay trên timer thread; **không** đặt một HTTP call hoặc tác vụ DB chậm ngay trong thread scheduler khi thiết kế ứng dụng lớn. Scheduler nên đến thời điểm thích hợp rồi **gửi** việc sang executor có giới hạn, hoặc đưa message trở lại queue/job store.
- Ví dụ chưa có lưu bền hay nhiều JVM: JVM chết thì lịch chờ trong bộ nhớ cũng mất. Nếu công việc bắt buộc phải được hoàn thành sau restart, cần durable queue/job store.

### Tính equal jitter trong Java

Hàm sau minh họa riêng cách chọn khoảng đợi có jitter, có thể dùng trong service sản phẩm với `retryNumber >= 1` và chính sách giới hạn số lần thử:

~~~java
import java.util.concurrent.ThreadLocalRandom;

static long retryDelayMs(int retryNumber) {
    if (retryNumber < 1) {
        throw new IllegalArgumentException("retryNumber starts at 1");
    }
    long cap = 200;
    for (int i = 1; i < retryNumber && cap < 1600; i++) {
        cap = Math.min(1600, cap * 2);
    }
    long minimum = cap / 2;
    return ThreadLocalRandom.current().nextLong(minimum, cap + 1);
}
~~~

Đây là **phương thức minh họa**, không phải tệp Java biên dịch độc lập vì không có khai báo class bao ngoài. Với `retryNumber=1` kết quả nằm trong `[100,200]` ms; với `retryNumber=2` trong `[200,400]` ms. `nextLong(origin, bound)` có biên trên loại trừ, vì vậy dùng `cap + 1` để bao gồm `cap`. Tính trần trước khi cộng 1 để tránh overflow nếu thay đổi thông số; ví dụ này có trần nhỏ và an toàn.

## 9. Áp dụng trong Spring: tách scheduler và executor xử lý việc

Spring Framework cung cấp `TaskExecutor` để thực thi task và `TaskScheduler` để lên lịch chạy sau. `TaskScheduler.schedule(Runnable, Instant)` đặt một tác vụ chạy một lần vào thời điểm chỉ định. Với `ThreadPoolTaskScheduler`, timer quản lý các lượt kích hoạt; `ThreadPoolTaskExecutor` quản lý worker có giới hạn.

Ví dụ cấu hình tích hợp Spring:

~~~java
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class RetryInfrastructure {
    @Bean("workExecutor")
    ThreadPoolTaskExecutor workExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(4);
        pool.setMaxPoolSize(8);
        pool.setQueueCapacity(32);
        pool.setThreadNamePrefix("work-");
        pool.setRejectedExecutionHandler(
            new ThreadPoolExecutor.AbortPolicy());
        return pool;
    }

    @Bean("retryScheduler")
    ThreadPoolTaskScheduler retryScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("retry-");
        return scheduler;
    }
}
~~~

Đây là **đoạn cấu hình cho một project Spring**, cần ApplicationContext nạp class này và có dependency Spring thích hợp. Giá trị `4/8/32` là ví dụ minh họa, không phải mặc định hay khuyến nghị áp dụng nguyên xi. Giữ executor hữu hạn để quá tải được thể hiện rõ; một queue cực dài thường chỉ chuyển lỗi reject thành backlog và thời gian chờ không kiểm soát.

Một đường xử lý retry khi **lệnh nộp bị từ chối** có thể tổ chức theo luồng:

~~~text
Nhận command (operationId, deadline)
             |
             v
Thử workExecutor.execute(task)
   | được nhận                 | bị TaskRejectedException
   v                           v
Worker xử lý và             Kiểm tra retry count,
ghi nhận kết quả            deadline, retry budget
                               |
                         còn ngân sách?
                         /         \
                       có         hết
                       |           |
             delay + jitter     báo thất bại/
                       |        lưu trạng thái
                       v
             retryScheduler.schedule(
                 () -> nộp lại command,
                 Instant.now() + delay
             )
                       |
               đến hạn mới thử
~~~

Sơ đồ trên chỉ mô tả retry khi **nộp vào executor bị từ chối**. Nếu worker đã nhận task nhưng task thất bại, cần một đường xử lý khác bắt được kết quả/exception của worker và chỉ retry các lỗi thật sự tạm thời. `Future.get()` hoặc một completion handler thích hợp giúp quan sát; không được tin rằng `execute` trả về bình thường có nghĩa là việc nghiệp vụ đã thành công.

**Không gọi đệ quy tức thời** từ `catch(TaskRejectedException)`: nếu `submitAgain()` tự gọi lại chính nó trong catch thì vẫn là retry nóng. Scheduling một callback sau delay khác về bản chất. Tuy nhiên timer cũng có thể quá tải/từ chối, và lịch trong bộ nhớ không bền; production phải có trạng thái kết thúc, metric và fallback rõ ràng.

## 10. Đặt retry ở đúng tầng và tránh giữ tài nguyên vô ích

Một đoạn mã có vẻ “đơn giản” nhưng gây hại:

~~~java
try {
    doRemoteCall();
} catch (TransientException ex) {
    Thread.sleep(1000);
    doRemoteCall();
}
~~~

Đây là **phản ví dụ không độc lập**, có exception/dependency giả định và thiếu xử lý `InterruptedException`. Dù đã có delay, việc ngủ ngay **trong worker đang khan hiếm** vẫn chiếm thread trong lúc không làm gì. Nếu nhiều worker cùng ngủ, pool không còn slot để xử lý yêu cầu mới. Trường hợp gọi đồng bộ nhỏ, có kiểm soát và không nằm trong pool khan hiếm có thể chấp nhận blocking backoff; cần đánh giá tài nguyên thay vì cấm `sleep` trong mọi ngữ cảnh.

Với công việc dài hạn hoặc số lượng retry lớn, thích hợp hơn là: trả quyền điều khiển cho caller, ghi thời điểm retry dự kiến vào job store, hoặc đăng ký timer rồi nhả worker. Đến hạn, task được đưa lại vào executor/consumer có backpressure. Không để vô hạn delayed jobs tích trong bộ nhớ scheduler, vì chỉ chuyển backlog từ queue này sang queue khác cũng chưa giải quyết công suất.

## 11. Khi nào cần từ bỏ retry và fail fast?

Không phải mọi lỗi đều đáng thử lại:

- **Input sai, lỗi validation, `400`, phần lớn lỗi `401/403` do thiếu quyền:** cần sửa input/quyền; retry ngay hoặc backoff không giải quyết bản chất.
- **Kết quả conflict do bất biến nghiệp vụ:** `409` hay optimistic-lock conflict đôi khi cho phép load lại dữ liệu và thực hiện lại một thao tác nghiệp vụ **được thiết kế an toàn**, nhưng không được retry mù mọi conflict.
- **`429` và `503`:** thường báo throttling/không sẵn sàng; xem `Retry-After` nếu có, tôn trọng giới hạn server, deadline và jitter phù hợp.
- **Timeout và đứt kết nối:** không biết chắc remote có thực hiện thao tác hay chưa; chỉ retry nếu operation idempotent hoặc có idempotency key/kiểm tra trạng thái.
- **Lỗi lập trình, dữ liệu đã bị hủy, thao tác không còn có ý nghĩa:** ghi lỗi/đưa vào kênh xử lý thích hợp thay vì tiêu thêm tài nguyên.
- **Deadline hết, retry budget cạn, circuit breaker mở, hệ thống đang load shedding:** dừng retry, ưu tiên bảo vệ năng lực hệ thống.

Lỗi nhận được ở API client khác với nguyên nhân gốc. Một `503` có thể đến từ gateway hoặc dependency; cần logging/tracing để tìm tầng phát sinh và thống nhất tầng sở hữu retry.

## 12. Idempotency: tránh nhân tác dụng phụ khi đã nhận nhưng chưa phản hồi

Giả sử client gọi API tạo đơn, server đã commit thành công nhưng response bị mất. Client timeout và retry ngay hoặc retry sau một giây vẫn có thể tạo đơn lần hai **nếu nghiệp vụ không chống trùng**.

Một idempotency key ổn định gắn với ý định nghiệp vụ giúp server phát hiện các attempt trùng. Khi xử lý nhiều lần cùng key, server có thể trả kết quả của lần đầu hoặc giữ một trạng thái duy nhất, tùy hợp đồng. Unique constraint/transaction, bảng kết quả theo key, consumer dedupe và outbox có thể là thành phần triển khai.

Quy tắc quan trọng:

1. **Giữ nguyên cùng idempotency key giữa các lần retry** của cùng một ý định; không tạo key mới ở mỗi attempt.
2. Phân biệt key của một nghiệp vụ mới với key của một attempt kỹ thuật.
3. Xác định cách xử lý attempt đang xử lý, thất bại không rõ kết quả và quá hạn lưu key.
4. Không mặc định `Future.cancel(true)` hay timeout có thể hoàn tác tác dụng phụ đã xảy ra.
5. Kiểm thử duplicate và lost response trước khi bật auto-retry cho thao tác ghi dữ liệu/thu tiền.

Backoff hạn chế **áp lực tài nguyên**; idempotency hạn chế **tác dụng phụ lặp**. Hai cơ chế giải quyết hai khía cạnh khác nhau và thường cần đi cùng nhau.

## 13. Phối hợp với backpressure, rate limit và circuit breaker

| Cơ chế | Giải quyết | Quan hệ với retry |
| --- | --- | --- |
| Bounded pool/queue | Giới hạn số tác vụ đồng thời/chờ trong tiến trình | Khi bão hòa phải báo reject thay vì nhận backlog vô hạn |
| Rate limiting | Giới hạn lưu lượng vào theo client/tenant/service | Giảm tải gốc, bao gồm cả retry |
| Backpressure/load shedding | Yêu cầu caller chậm lại hoặc từ chối một phần tải | Retry phải chấp hành tín hiệu này |
| Circuit breaker | Ngừng gọi tạm thời một dependency đang lỗi liên tục | Giảm số attempt vô ích; chỉ thăm dò phục hồi theo chính sách |
| Timeout/deadline | Ngăn một thao tác dùng tài nguyên quá lâu | Không lên lịch retry khi hết thời hạn |
| Exponential backoff + jitter | Trải retry theo thời gian, tránh các đợt đồng bộ | Chỉ dùng khi lỗi có khả năng hồi phục và operation có thể retry |
| Retry budget | Giới hạn retry toàn hệ thống | Ngăn hàng loạt request cùng nhân đôi/nhân ba tải |
| Durable queue/job store | Lưu công việc qua restart, kiểm soát retry ở cấp hệ thống | Cần idempotency, giám sát và quy tắc xử lý lỗi độc hại |

**CallerRunsPolicy** của `ThreadPoolExecutor` có thể khiến luồng nộp phải tự chạy task để tạo backpressure khi pool đang hoạt động và bão hòa. Đây là **một lựa chọn thiết kế**, không phải lý do để thêm vòng lặp retry nóng; nó làm tăng độ trễ caller và cần kiểm soát deadline. Policy này cũng có hành vi đặc biệt khi executor đã shutdown, vì vậy không thể coi là cơ chế đảm bảo task luôn được nhận.

Spring Framework cung cấp abstraction executor/scheduler và cho phép tích hợp các cơ chế bảo vệ. Việc chọn thư viện retry/circuit breaker cụ thể (chẳng hạn Spring Retry hoặc giải pháp resilience riêng) là quyết định kiến trúc bổ sung; **`ThreadPoolTaskExecutor` không tự cấp retry có backoff và jitter cho mọi task**.

## 14. Hai tình huống thực tế

### A. Endpoint tạo báo cáo bị từ chối ở executor

**Trước:** HTTP handler gọi `reportExecutor.submit(job)`; khi gặp `TaskRejectedException`, chạy `while(true)` thử lại. Khi queue đầy, các request thread mắc trong vòng lặp, CPU tăng mà báo cáo không hoàn thành.

**Sau:** xác định API đồng bộ hay nhận job. Với API đồng bộ, có thể trả phản hồi quá tải thích hợp (ví dụ `429` hoặc `503` tùy ý nghĩa dịch vụ) cùng hướng dẫn retry để client không thử dồn dập. Với API job quan trọng, ghi job/idempotency key vào kho bền vững rồi worker tiêu thụ theo giới hạn. Nếu có đường retry trong service, đặt maxAttempts/deadline/budget và scheduler có giới hạn; không chặn request thread vô thời hạn.

### B. Dịch vụ thanh toán downstream chậm

**Trước:** ba tầng đều retry tức thời khi timeout. Một yêu cầu thanh toán có thể bùng thành nhiều cuộc gọi trong lúc payment gateway chậm, rủi ro lặp giao dịch và làm downstream thêm nghẽn.

**Sau:** thống nhất tầng sở hữu retry; dùng cùng idempotency key, giới hạn tổng thời gian, số attempt và retry budget. Tôn trọng rate limit/`Retry-After`, áp backoff jitter, kiểm tra trạng thái thanh toán khi kết quả không rõ ràng. Với lỗi không thể hồi phục hoặc hết deadline, kết thúc có ghi nhận thay vì tạo thêm tải.

## 15. Cách kiểm thử một chính sách retry an toàn

1. **Rejection có kiểm soát:** dùng `CountDownLatch` giữ worker của `ThreadPoolTaskExecutor`, làm đầy queue rồi xác nhận task mới bị reject đúng policy. Không viết test chỉ dựa trên `Thread.sleep()` để hy vọng pool bão hòa.
2. **Không retry nóng:** dùng fake clock/virtual time hoặc scheduler có thể kiểm soát; đo timestamp giữa các attempt và khẳng định lần retry kế tiếp không xảy ra ngay.
3. **Max attempts và deadline:** với dependency luôn lỗi, kiểm tra tổng lượt gọi không vượt trần và kết thúc sau deadline.
4. **Jitter:** kiểm tra delay nằm trong khoảng hợp lệ; kiểm thử giới hạn và seed cố định tại tầng tính toán nếu cần độ lặp lại. Tránh assert một thời điểm ngẫu nhiên chính xác trong test tích hợp.
5. **Retryable vs nonretryable:** xác nhận lỗi input/permission không bị retry; timeout có kết quả không rõ chỉ retry khi idempotent.
6. **Dồn tải đồng thời:** cho nhiều caller nhận lỗi cùng lúc; đo tổng attempt/s và phân bố retry để phát hiện thundering herd.
7. **Lỗi một phần:** giả lập remote xử lý thành công nhưng mất phản hồi; xác nhận idempotency key giúp tránh tạo bản ghi/tác dụng phụ trùng.
8. **Restart/shutdown:** với công việc quan trọng, kiểm tra khả năng phục hồi trong job store; không giả định timer trong bộ nhớ sẽ tồn tại qua process crash.

Theo dõi song song tỷ lệ request gốc, tỷ lệ retry, số bị từ chối, chiều dài queue, active worker, thời gian chờ, tỷ lệ lỗi và p95/p99 latency. Nếu **retry tăng đồng thời với lỗi và backlog**, hệ thống có thể đang tự khuếch đại sự cố.

## 16. Checklist khi thiết kế để tránh retry storm

- Tôi đã phân biệt **reject lúc nộp task** với **lỗi khi task đã chạy**.
- Tôi chỉ retry lỗi thật sự tạm thời và thao tác có khả năng retry an toàn.
- Tôi có `maxAttempts`, deadline tổng, retry budget toàn dịch vụ.
- Tôi dùng delay/backoff và jitter, không có vòng lặp retry tức thời.
- Tôi không giữ worker hiếm hoi chỉ để ngủ trong thời gian backoff.
- Tôi xử lý riêng timeout, cancellation, shutdown và scheduler rejection.
- Tôi tôn trọng tín hiệu `429`, `503`, `Retry-After` khi phù hợp hợp đồng.
- Tôi bảo đảm idempotency cho tác dụng phụ nếu có thể xuất hiện duplicate.
- Tôi quan sát riêng retry rate và load gốc; có kiểm thử saturation và lost response.
- Tôi biết đường xử lý cuối cùng sau khi retry hết ngân sách: trả lỗi, ghi thất bại, chuyển xử lý thủ công hoặc dead-letter theo yêu cầu nghiệp vụ.

**Ghi nhớ:** Khi pool hoặc downstream quá tải, **retry tức thời lấy thêm chính tài nguyên đang thiếu** và tạo vòng phản hồi khiến sự cố kéo dài. Retry đúng phải có **điều kiện**, **giới hạn**, **đợi có jitter**, **ngữ cảnh idempotency**, và một điểm dừng rõ ràng.

## Tài liệu tham khảo

- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `TaskExecutor`, `TaskScheduler`, queue, từ chối và scheduling.
- [Spring Framework – TaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/TaskScheduler.html): `schedule(Runnable, Instant)`.
- [Spring Framework – TaskRejectedException Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/TaskRejectedException.html).
- [Google SRE Book – Addressing Cascading Failures](https://sre.google/sre-book/addressing-cascading-failures/): retry storms, randomized backoff, ngân sách retry và tránh retry nhiều tầng.
- [Java SE 17 – ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html): cách lên lịch task mà không giữ worker trong lúc chờ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overload-rejection-009">Thực nghiệm nhóm luồng và hàng đợi cùng đạt giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Thực nghiệm nhóm luồng và hàng đợi cùng đạt giới hạn

## 1. Mục tiêu: chủ động tạo trạng thái quá tải

Trong Spring Framework, `ThreadPoolTaskExecutor` thường chạy các công việc được nộp qua `@Async` hoặc các lời gọi `TaskExecutor.execute(...)`. Khi hệ thống nhiều yêu cầu, một pool có thể có **tất cả worker đang bận** và **hàng đợi chưa chạy cũng đầy**. Nếu lại có một task mới, executor không thể nhận thêm theo cách thông thường: nó áp dụng **chính sách từ chối** (*rejection policy*).

Bài thực nghiệm này không chỉ nói “executor có giới hạn” mà tạo một tình huống có thể lặp lại để kiểm chứng:

- Task nào chạy trên worker, task nào nằm trong queue?
- Khi `corePoolSize` đã đạt, task mới được xếp hàng hay mở thêm thread?
- Khi queue đã đầy, pool có tiếp tục tăng tới `maxPoolSize` không?
- Khi **cả pool và queue cùng đầy**, task tiếp theo được xử lý thế nào?
- Làm sao chứng minh hiện tượng này mà không phụ thuộc vào sự ngẫu nhiên của `Thread.sleep(...)`?
- Trong Spring, `TaskRejectedException` và `RejectedExecutionHandler` liên quan ra sao?

Trọng tâm là **quá tải và từ chối**. Không nhầm một task **chưa được nhận** với một task **đã chạy nhưng thất bại trong logic nghiệp vụ**.

## 2. Quy tắc nhận việc của ThreadPoolExecutor

`ThreadPoolTaskExecutor` của Spring bọc một `java.util.concurrent.ThreadPoolExecutor`. Khi dùng queue hữu hạn, ba thông số phải xem cùng lúc:

| Thuộc tính | Ý nghĩa |
| --- | --- |
| `corePoolSize` | Số thread lõi executor ưu tiên tạo khi nhận các task đầu tiên |
| `maxPoolSize` | Trần số worker thread của pool |
| `queueCapacity` | Số task chưa chạy có thể chờ trong queue |
| `RejectedExecutionHandler` | Cách xử lý khi không còn khả năng nhận thêm task, hoặc pool đã shutdown |

Quy tắc của `execute(Runnable)` trong `ThreadPoolExecutor`, giản lược theo cấu hình thông thường:

1. **Số worker hiện có chưa đạt core:** tạo worker để chạy task mới.
2. **Đã đạt core:** thử đưa task mới vào queue.
3. **Queue không nhận được task:** thử tạo worker khác nếu chưa chạm max.
4. **Queue đầy và worker đã đạt max:** gọi rejection handler.
5. **Pool đã shutdown:** cũng có thể reject, **dù không bão hòa**. Vì vậy phải phân biệt hai nguyên nhân rejection.

Điều dễ nhầm: **max thread không nhất thiết được dùng trước queue**. Nếu core=1, max=10 và queue còn chỗ, các task nộp sau worker đầu có thể tiếp tục vào queue; executor chỉ mở thêm worker vượt core khi queue không nhận được việc.

Một queue gần như vô hạn khiến pool thường không cần mở rộng vượt core. Ngược lại, `queueCapacity=0` trong `ThreadPoolTaskExecutor` tương ứng chiến lược chuyển giao trực tiếp (`SynchronousQueue`), chứ không phải queue có thể chứa vô hạn task.

## 3. Dự đoán với core=1, max=2, queue=1

Dùng cấu hình nhỏ để hiểu từng bước:

```text
corePoolSize  = 1
maxPoolSize   = 2
queueCapacity = 1
handler       = AbortPolicy
```

Giả sử task #1 và #3 được **giữ chưa hoàn thành**, nên hai worker mà chúng chiếm vẫn bận. Task #2 ngắn nhưng phải xếp hàng.

| Lần submit | Hành vi | Số worker bận | Số task chờ |
| --- | --- | ---: | ---: |
| #1 | Tạo worker lõi, chạy task #1 | 1 | 0 |
| #2 | Worker lõi bận, queue nhận task #2 | 1 | 1/1 |
| #3 | Queue đầy, tạo worker thứ hai chạy task #3 | 2/2 | 1/1 |
| #4 | Worker và queue đều đầy, `AbortPolicy` từ chối | 2/2 | 1/1 |

Sau khi giải phóng hai worker, task #2 mới được lấy ra khỏi queue và chạy. **Tổng cộng 3 task được chấp nhận**, còn task #4 không được chạy.

```text
Sau khi submit task #3, trước khi submit task #4:

+-----------------------+  +-----------------------+
| worker 1: task #1     |  | worker 2: task #3     |
| đang chờ latch        |  | đang chờ latch        |
+-----------------------+  +-----------------------+
         active workers = 2/2

queue (capacity 1): [ task #2 ]
         queue size = 1/1

submit task #4
  -> không còn suất worker
  -> không còn chỗ trong queue
  -> AbortPolicy: RejectedExecutionException
```

Đây là **ảnh chụp tại một thời điểm** chứ không phải trạng thái tồn tại mãi: nếu task #1 kết thúc trước khi nộp #4 thì worker có thể lấy task #2, queue mở chỗ và task #4 không nhất thiết bị từ chối. Vì vậy phải tạo một thí nghiệm có kiểm soát.

## 4. Java 17 standalone: tái hiện rejection với CountDownLatch

Ví dụ sử dụng duy nhất thư viện chuẩn JDK 17. Nó không đòi hỏi Spring, Maven hoặc server. Các latch giúp xác nhận hai task thực sự đang chạy rồi **giữ chúng tại điểm chặn** cho tới sau lần submit cần thử.

Tạo tệp **`PoolQueueLimitDemo.java`**:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class PoolQueueLimitDemo {
    private static void awaitOrFail(CountDownLatch latch, String stage)
            throws InterruptedException {
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timeout at " + stage);
        }
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();

        Runnable heldTask = () -> {
            firstStarted.countDown();
            bothStarted.countDown();
            try {
                release.await();
                completed.incrementAndGet();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };
        Runnable shortTask = () -> {
            completed.incrementAndGet();
        };

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1,                             // corePoolSize
                2,                             // maxPoolSize
                30, TimeUnit.SECONDS,          // keepAlive ngoài core
                new ArrayBlockingQueue<>(1),   // queueCapacity = 1
                new ThreadPoolExecutor.AbortPolicy()
        );

        try {
            // Task #1: tạo worker lõi và giữ nó chưa hoàn thành.
            pool.execute(heldTask);
            awaitOrFail(firstStarted, "first started");

            // Task #2: worker lõi bận nên queue nhận 1 task.
            pool.execute(shortTask);

            // Task #3: queue đã đầy, pool phải tạo worker thứ hai.
            pool.execute(heldTask);
            awaitOrFail(bothStarted, "both started");

            // Cả hai worker còn chờ release, nên task #2 vẫn ở queue.
            int active = pool.getActiveCount();
            int queued = pool.getQueue().size();
            if (active != 2 || queued != 1) {
                throw new IllegalStateException(
                        "Unexpected state: " + active + "/" + queued);
            }
            System.out.println("active=" + active + ", queued=" + queued);

            boolean fourthRejected = false;
            try {
                // Task #4: max=2 và queue=1 đều đã hết chỗ.
                pool.execute(shortTask);
            } catch (RejectedExecutionException ex) {
                fourthRejected = true;
            }
            System.out.println("fourthRejected=" + fourthRejected);
        } finally {
            // Luôn trả quyền chạy cho worker, kể cả test thất bại.
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                throw new IllegalStateException("Pool did not terminate");
            }
        }

        System.out.println("completed=" + completed.get());
    }
}
```

Chạy trong thư mục có tệp nguồn:

```bash
javac PoolQueueLimitDemo.java
java PoolQueueLimitDemo
```

**Standard output khi chương trình hoàn tất bình thường:**

```text
active=2, queued=1
fourthRejected=true
completed=3
```

### Tại sao kết quả ổn định?

- Main không nộp task #2 cho tới khi `firstStarted` xác nhận task #1 đã chạy. Task #1 còn đợi `release` nên worker thứ nhất chưa thể lấy task #2.
- Khi nộp task #2, hàng đợi 1 chỗ được lấp đầy. Nộp task #3 buộc pool tạo worker thứ hai, vì max còn cho phép.
- Main đợi `bothStarted` để biết **cả hai worker đã bắt đầu**. Cả hai đang đợi cùng `release`, nên trước khi mở latch, active giữ ở 2 và queue giữ ở 1.
- Nộp task #4 lúc đó chắc chắn đi qua nhánh rejection của `AbortPolicy`. Exception được ném trong **thread submit** (main) và bị bắt ở đó, không phải bên trong task #4.
- `finally` mở latch, hai held task kết thúc, task #2 đang chờ được worker xử lý. `awaitTermination(...)` bảo đảm ba task đã hoàn tất trước khi in `completed=3`.
- Task #4 chưa được chấp nhận nên không được tính vào `completed`.

Timeout 10 giây dùng để phát hiện môi trường thực thi quá chậm/bị treo, không phải timeout nghiệp vụ của executor. Nếu test timeout, nó báo lỗi thay vì giả vờ đạt kết quả. Trong đường chạy bình thường ở trên, dòng stdout xác định không phụ thuộc thứ tự worker.

### Vì sao chỉ dùng sleep là không đủ?

Ví dụ kém tin cậy nếu người viết chỉ cho mỗi task ngủ vài mili-giây rồi gửi bốn task liên tiếp: task đầu có thể kết thúc trước lần nộp #4. Khi đó pool có chỗ trống và không reject, dẫn tới kết quả chạy lần được lần không. Latch hoặc barrier phải dùng để **giữ tình huống đầy**, không chỉ tạo chậm trễ ước lượng.

## 5. Cấu hình cùng kịch bản trong Spring ThreadPoolTaskExecutor

`ThreadPoolTaskExecutor` là adapter/lớp cấu hình tiện dụng của Spring cho JDK thread pool. Đoạn dưới đây là **các class tích hợp vào ứng dụng Spring Framework/Spring Boot**, không phải chương trình `javac` chỉ dùng JDK. Chúng cần Spring context, scheduling và cấu hình application phù hợp.

**`AsyncConfiguration.java`**:

```java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfiguration {

    @Bean("boundedExecutor")
    public ThreadPoolTaskExecutor boundedExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("bounded-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
```

Spring sẽ khởi tạo/đóng executor bean theo lifecycle. Ví dụ một service dùng đúng bean đó:

**`JobService.java`**:

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class JobService {

    @Async("boundedExecutor")
    public CompletableFuture<String> process(String id) {
        // Đây là công việc mẫu; thay bằng xử lý thực tế khi tích hợp.
        String result = "processed:" + id;
        return CompletableFuture.completedFuture(result);
    }
}
```

**Lưu ý khi chạy thực tế:**

1. `@EnableAsync` bật cơ chế gọi phương thức bất đồng bộ. `@Async("boundedExecutor")` chọn đúng executor, tránh nhầm với executor auto-configuration khác của Spring Boot.
2. Phải gọi `process(...)` **qua Spring-managed proxy**, ví dụ từ bean khác đã inject `JobService`. Tự gọi `this.process(...)` trong cùng bean ở proxy mode thường bỏ qua `@Async`.
3. Phương thức xử lý mẫu chạy rất nhanh, nên gửi bốn lời gọi liên tiếp **không bảo đảm** pool bão hòa. Để kiểm thử ở tầng Spring, cần giữ task bằng latch/dependency giả tương tự mục 4.
4. Với `ThreadPoolTaskExecutor`, Spring thường báo lỗi không nhận được task bằng `org.springframework.core.task.TaskRejectedException`, kế thừa `RejectedExecutionException`. Đây là lỗi **submit** có thể đi ra ngay ở lời gọi `@Async`, trước khi caller nhận `CompletableFuture`.
5. Nếu task đã được chấp nhận rồi gặp lỗi, caller quan sát qua Future/callback của công việc đã nhận; đó **không** phải rejection.
6. Nếu dùng `@Async void`, lỗi thực thi bên trong không có Future cho caller theo dõi và cần cấu hình cơ chế xử lý lỗi tương ứng.

Ví dụ **snippet tại biên caller**, với biến `jobService` đã được inject:

```java
try {
    CompletableFuture<String> future = jobService.process("A-100");
    future.whenComplete((value, error) -> {
        if (error != null) {
            // Task được nhận nhưng đã gặp lỗi lúc thực thi.
        }
    });
} catch (org.springframework.core.task.TaskRejectedException ex) {
    // Executor không nhận task: trả lỗi quá tải hoặc lưu/retry công việc.
}
```

Không nên catch rejection rồi im lặng bỏ qua việc nghiệp vụ quan trọng. Với công việc phải chạy ngay cả sau restart, cần hệ queue/job store bền vững và cơ chế idempotency, không chỉ executor queue ở RAM.

## 6. Rejection trong Java và Spring: bốn chính sách quan trọng

| Handler | Khi pool/queue đầy | Cần chú ý |
| --- | --- | --- |
| `AbortPolicy` | Ném `RejectedExecutionException`; Spring wrapper thường trả `TaskRejectedException` | Caller biết task chưa được nhận, phải xử lý |
| `CallerRunsPolicy` | Caller tự chạy task (nếu executor chưa shutdown) | Không reject kiểu exception, nhưng **chặn caller**; task chạy ngoài worker pool |
| `DiscardPolicy` | Bỏ task âm thầm | Dễ mất việc; Future có thể không hoàn thành như người đợi mong đợi |
| `DiscardOldestPolicy` | Bỏ task cũ ở đầu queue rồi thử nộp lại | Có thể mất việc cũ, sai thứ tự, cần xem kỹ semantics retry |

**Cảnh báo với CallerRunsPolicy:** nếu HTTP request thread gọi `execute(...)`, khi pool bão hòa request thread có thể tự chạy công việc chậm. Đây là một kiểu backpressure nhưng có thể làm độ trễ request tăng mạnh. Nếu caller là event-loop hoặc công việc nested phụ thuộc latch do chính caller mở, có thể gặp tình huống chặn hoặc deadlock.

Nếu thay `AbortPolicy` trong ví dụ Java bằng `CallerRunsPolicy`, task #4 sẽ chạy trong main (vì `shortTask` không bị chặn), nên **stdout phải thay đổi**. Không thể giữ `fourthRejected=true` trong kỳ vọng. Với DiscardPolicy, task #4 có thể bị bỏ mà không ném exception; phải đếm task thực thi để phát hiện mất việc.

Đừng diễn giải “không có exception” là “đã được nhận và xử lý thành công”.

## 7. Thay đổi tham số để suy luận kết quả

Muốn làm thực nghiệm có giá trị, hãy **dự đoán trạng thái trước khi chạy**, sau đó sửa đúng một thông số và kiểm tra. Giữ các worker ở latch để queue chưa được rút.

| Cấu hình | Thứ tự nộp việc khi worker giữ chỗ | Task đầu tiên bị AbortPolicy reject |
| --- | --- | --- |
| core=1, max=2, queue=1 | #1 chạy, #2 queue, #3 chạy, #4 reject | #4 |
| core=2, max=2, queue=1 | #1/#2 chạy, #3 queue, #4 reject | #4 |
| core=1, max=3, queue=1 | #1 chạy, #2 queue, #3/#4 chạy, #5 reject | #5 |
| core=2, max=4, queue=2 | #1/#2 chạy, #3/#4 queue, #5/#6 chạy, #7 reject | #7 |
| core=1, max=2, queue=0 | #1/#2 chạy, không có queue, #3 reject | #3 |

Bảng dự đoán giả định các task chạy đều **chưa hoàn thành**, không có worker rảnh, executor chưa shutdown và dùng AbortPolicy. Nếu task rảnh/kết thúc giữa các lần nộp, lần reject có thể khác.

### Trường hợp queue quá lớn

Giả sử core=1, max=8 và queueCapacity rất lớn. Task #1 chạy trên core, nhiều task tiếp theo sẽ được queue nhận. Pool có thể vẫn chỉ có một worker vì queue chưa đầy; max=8 **không buộc** pool tạo thêm bảy worker ngay.

Hậu quả: backlog dài, bộ nhớ tăng, p95/p99 latency có thể xấu đi, nhưng rejection diễn ra rất muộn hoặc không xảy ra cho tới khi chạm giới hạn khác. Vì vậy không đặt queue unbounded chỉ để “tránh lỗi quá tải”.

### Trường hợp queueCapacity = 0

Trong `ThreadPoolTaskExecutor`, giá trị 0 dùng `SynchronousQueue`. Nó không giữ task chờ. Task phải được chuyển cho worker có thể nhận hoặc mở worker mới nếu dưới max. Khi tất cả worker đã bận và max cũng đầy, submission sẽ reject theo handler.

Một pool như vậy phản ứng nhanh với tải tăng nhưng có thể mở nhiều thread nhanh; cần giới hạn max thích hợp và kiểm soát downstream.

## 8. Cần quan sát những chỉ số nào?

Tại thời điểm test được giữ bằng latch, quan sát:

```text
poolSize              số worker hiện tồn tại trong pool
activeCount           số worker đang chạy task
queueSize             số task chưa chạy đang chờ
queueRemaining        số chỗ queue còn lại
rejectedSubmissions   số task bị từ chối tại biên submit
completedTasks        số task đã chạy xong
```

Ở Spring `ThreadPoolTaskExecutor`, có thể xem `getPoolSize()`, `getActiveCount()`, `getQueueSize()`. Sau khi bean được khởi tạo, JDK executor nền qua `getThreadPoolExecutor()` cung cấp `getQueue()` và `getCompletedTaskCount()`. Các metric này phản ánh **trạng thái có thể biến động**; nhiều getter gọi liên tiếp không đảm bảo snapshot nguyên tử trong hệ đang chạy tự do.

Đặc biệt:

- `getPoolSize() == maxPoolSize` **chưa** chứng minh queue đầy.
- `getQueueSize() == queueCapacity` **chưa** chứng minh tất cả worker đã bận.
- Cả hai cùng đúng vẫn cần xem pool có đang shutdown, worker có sắp giải phóng và chính sách rejection.
- `getCompletedTaskCount()` là giá trị ước lượng của pool; không nhất thiết đếm task do `CallerRunsPolicy` thực thi ngoài pool.
- Muốn biết số reject chính xác, nên đếm tại chính rejection handler hoặc nơi bắt `TaskRejectedException`/`RejectedExecutionException`, có metric và log phù hợp.

Trong ứng dụng Spring Boot có Micrometer/Actuator, tùy cấu hình và cách binder nhận executor, có thể theo dõi active thread, queue depth, completed task, utilization, rejection và thời gian chờ. Đừng giả định mọi metric luôn tự xuất hiện; xác minh instrumentation thực tế.

## 9. Quy trình kiểm thử đáng tin cậy

1. **Cố định cấu hình và handler:** khai báo core, max, queueCapacity, AbortPolicy. Ghi rõ phiên bản JDK/Spring.
2. **Giữ worker bằng latch:** task thông báo “đã chạy” rồi đợi tín hiệu được thả. Kiểm thử không được phụ thuộc chủ yếu vào `Thread.sleep(100)`.
3. **Nạp đủ queue:** sau khi core worker bắt đầu, submit task ngắn vào queue trong khi core vẫn bị giữ; tiếp tục submit để mở rộng tới max.
4. **Xác nhận saturation:** đợi tất cả worker cần thiết bắt đầu; đo active worker và số phần tử queue trước khi nộp task bị thử reject.
5. **Kiểm tra exception đúng nơi:** catch lỗi từ `execute/submit` hoặc caller của Spring proxy. Đừng đặt catch bên trong task chưa được nhận.
6. **Giải phóng và kết thúc đúng:** mở latch trong `finally`, shutdown và đợi tất cả task hợp lệ hoàn thành. Một bài test có thể treo mãi nếu thiếu nhánh dọn dẹp.
7. **Đếm việc hoàn thành:** kiểm tra tổng task thực thi, không chỉ nhìn một dòng “rejected”. Với ba task được nhận và một task bị reject, không được đếm bốn thành công.
8. **Kiểm thử hồi phục:** sau khi worker giải phóng và queue rỗng, thử thêm task mới **trước khi shutdown** để chứng minh saturation là tạm thời. Nếu nộp task sau shutdown, rejection lúc đó không chứng minh pool vẫn đầy.
9. **Đổi handler và dự đoán lại:** CallerRuns, Discard và DiscardOldest có hành vi khác Abort; không tái sử dụng kết luận của AbortPolicy.
10. **Làm load test tích hợp:** thử với datasource, HTTP client và số pod thật. Saturation ở executor chỉ là một phần của hành vi hệ thống.

Nếu dùng JUnit, có thể viết các assertion tương ứng `assertEquals(2, active)`, `assertEquals(1, queueSize)` và `assertThrows(RejectedExecutionException.class, ...)` khi test JDK executor. Với Spring executor, catch/assert `TaskRejectedException`. Test runner cũng phải dùng timeout và teardown để tránh leak thread khi assertion fail.

## 10. Tác động khi đưa cấu hình lên production

Cấu hình nhỏ core=1, max=2, queue=1 dành cho minh họa, không phải giá trị mặc định nên dùng cho mọi ứng dụng. Chọn thông số dựa trên loại task:

- **CPU-bound:** quá nhiều worker tranh CPU, tăng context switch; cần cân nhắc số core máy và mức tải.
- **I/O-bound:** worker có thể chờ JDBC/HTTP rất lâu; tăng worker không tự tăng số DB connections hay quota downstream.
- **Tác vụ có thời hạn:** nếu nằm trong queue lâu hơn deadline, dù cuối cùng chạy thành công cũng có thể vô ích; nên có queue wait budget/admission policy.
- **Tác vụ quan trọng:** nếu bị reject và không được lưu lại ở tầng bền vững, công việc có thể bị mất. Cần retry có giới hạn, backoff, idempotency hoặc job queue.
- **Nhiều nguồn công việc:** background batch, HTTP request và event consumer có thể cần pool hoặc bulkhead riêng để không tranh hết suất lẫn nhau.
- **Nhiều instance:** giới hạn worker và queue là **mỗi executor trên mỗi JVM**; bốn pod cùng max=2 có thể có tám worker trên toàn dịch vụ, chưa kể executor khác.
- **Tải bùng nổ:** queue có thể giúp hấp thụ burst ngắn, nhưng không giải quyết được khi tốc độ đến liên tục lớn hơn tốc độ xử lý.

Ở thời điểm trạng thái hoàn toàn đầy và không có worker đang rảnh, pool có thể giữ tối đa **`maxPoolSize` task đang chạy + `queueCapacity` task đang đợi**. Đây là số task cùng tồn tại trong **một executor**, không phải tổng số task xử lý suốt vòng đời. Với CallerRunsPolicy, caller còn có thể thực thi task bên ngoài pool nên không được lấy công thức ấy làm trần mọi hoạt động của ứng dụng.

**Rejection sớm có thể là hành vi đúng:** thà trả phản hồi quá tải rõ ràng còn hơn nhận vô hạn công việc rồi timeout đồng loạt. Với API có thể trả HTTP 429 hoặc 503 tùy nguyên nhân/hợp đồng; với message consumer cần xử lý ack/nack và khả năng redelivery phù hợp.

## 11. Các lỗi thường gặp

**Lỗi: “Em tăng max nhưng active vẫn bằng core.”** Nếu queue còn nhận việc, pool ưu tiên xếp hàng trước khi mở rộng vượt core. Kiểm tra queueCapacity và queue depth.

**Lỗi: “Queue đầy nhưng task vẫn nhận, chắc Java sai.”** Nếu worker còn có thể mở rộng tới max, executor tạo thêm worker. Queue đầy **một mình** chưa phải lý do reject.

**Lỗi: “Pool chạm max nhưng task vẫn nhận.”** Queue còn chỗ thì task có thể vào queue. Phải xác định **cả hai** đều hết dung lượng.

**Lỗi: “Task #4 được reject nhưng chẳng thấy log bên trong run().”** Task không được chạy nên code trong `run()` không thể log. Bắt exception ở thread submit.

**Lỗi: “Chỉ dùng Thread.sleep nên test khi xanh khi đỏ.”** Khoảng ngủ không xác nhận worker đã đạt trạng thái. Sử dụng CountDownLatch/barrier và đợi các mốc cụ thể.

**Lỗi: “Tất cả request không bị reject nghĩa là hệ thống khỏe.”** Với queue lớn, backlog và latency có thể tăng nguy hiểm mà không reject. Đo queue waiting, heap, p95/p99 latency và throughput.

**Lỗi: “@Async luôn trả về ngay.”** Submit có thể ném `TaskRejectedException`; CallerRunsPolicy có thể chạy task ở caller. `@Async` không phải bảo đảm tuyệt đối về latency.

**Lỗi: “DiscardPolicy không báo exception nên đã xử lý xong.”** Không có exception có thể chỉ nghĩa là task đã bị bỏ. Không dùng policy bỏ việc âm thầm khi nghiệp vụ cần hoàn thành.

**Lỗi: “Task đang chạy ném lỗi là saturation.”** Lỗi trong worker khác với rejection tại cổng submit. Với `submit(...)`, lỗi worker thường được chứa trong `Future` và cần `get()`/callback để quan sát.

**Lỗi: “Test treo sau khi assert fail.”** Nếu không mở latch và shutdown trong `finally`, worker có thể đợi vô hạn. Dọn dẹp và timeout là một phần của bài kiểm thử.

**Lỗi: “Đã giới hạn executor là đủ để bảo vệ DB.”** Task có thể dùng nhiều connection, hoặc các thread khác cũng dùng DB. Phải phối hợp giới hạn của datasource, downstream, transaction duration và service quota.

## 12. Ghi nhớ và tài liệu chính thức

**Quy tắc cốt lõi:**

1. Chưa tới core → tạo worker.
2. Đã tới core, queue còn chỗ → đưa task vào queue.
3. Queue đầy, chưa tới max → tạo worker mới.
4. Queue đầy **và** max worker đã bận → áp dụng rejection handler.
5. `AbortPolicy` giúp quan sát sự kiện từ chối bằng exception ở phía submit.
6. Dùng `CountDownLatch` để thực nghiệm giữ worker chưa hoàn thành, tạo điều kiện bão hòa kiểm chứng được.
7. Spring `ThreadPoolTaskExecutor` cần được cấu hình hữu hạn; `@Async` phải đi qua đúng proxy và đúng executor.
8. Sau mỗi bài test phải release worker, shutdown pool, đợi task hợp lệ hoàn thành và đối chiếu số task thực thi.

**Tài liệu đối chiếu:**

- [Java SE 17 – ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html): cơ chế core → queue → max → rejection và các policy.
- [Java SE 17 – CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): đồng bộ điểm bắt đầu/kết thúc để test ổn định.
- [Java SE 17 – ArrayBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html): queue hữu hạn.
- [Spring Framework 6.2 – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): thread pool, queue và rejection trong Spring.
- [Spring Framework 6.2 – ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): cấu hình và API giám sát.
- [Spring Framework 6.2 – TaskRejectedException Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/core/task/TaskRejectedException.html): exception của Spring TaskExecutor.

**Kết luận:** muốn chứng minh một pool từ chối vì quá tải, phải tạo được **đồng thời** hai điều kiện: worker đã dùng hết `maxPoolSize` và queue cũng không còn chỗ. Khi giữ trạng thái này bằng latch rồi nộp task mới, ta thấy rõ chính sách từ chối và có thể thiết kế hành vi quá tải phù hợp cho ứng dụng Spring.


</details>

- [Quay lại đầu trang](#back-to-top)
