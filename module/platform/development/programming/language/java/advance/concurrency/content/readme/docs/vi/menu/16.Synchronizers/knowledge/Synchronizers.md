<a id="back-to-top"></a>

# Synchronizers

## Menu
- [CountDownLatch chờ một mốc hoàn tất](#synchronizers-001)
- [CyclicBarrier phối hợp các vòng làm việc](#synchronizers-002)
- [Phaser cho số lượng participant linh hoạt](#synchronizers-003)
- [Semaphore giới hạn truy cập tài nguyên hữu hạn](#synchronizers-004)
- [Exchanger&lt;T&gt; trao đổi dữ liệu giữa hai luồng](#synchronizers-005)
- [Các thao tác timeout và interruption](#synchronizers-006)
- [Barrier action, trạng thái broken và reset](#synchronizers-007)
- [Đếm permit và phòng tránh permit leak](#synchronizers-008)
- [Latch dùng một lần so với barrier tái sử dụng](#synchronizers-009)
- [Sử dụng synchronizer trong test concurrency](#synchronizers-010)
- [Khi nên dùng BlockingQueue thay vì tự phối hợp luồng](#synchronizers-011)
- [Tránh deadlock do thiếu arrive(), countDown() hoặc release()](#synchronizers-012)

## <a id="synchronizers-001">CountDownLatch chờ một mốc hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# CountDownLatch: chờ một mốc hoàn tất trong Java

## 1. CountDownLatch giải quyết bài toán gì?

Trong lập trình đa luồng, có nhiều lúc một thread phải **đợi một sự kiện hoặc một nhóm công việc đạt mốc hoàn tất** rồi mới tiếp tục. Ví dụ:

- Thread điều phối gửi ba công việc độc lập cho executor, chỉ tổng hợp kết quả sau khi cả ba đã kết thúc.
- Một bộ kiểm thử muốn đợi các worker chuẩn bị xong rồi đồng loạt phát tín hiệu bắt đầu.
- Một thread phải chờ một bước khởi tạo được báo hiệu thành công trước khi bắt đầu đọc dữ liệu.
- Nhiều thread cần chờ cùng một sự kiện “cấu hình sẵn sàng”.

`CountDownLatch` trong package `java.util.concurrent` là một **synchronizer dùng một lần** (one-shot). Nó quản lý một bộ đếm không âm được đặt khi xây dựng. Các thread có thể gọi `await()` để chờ cho đến khi bộ đếm về **0**; những thread báo hoàn tất gọi `countDown()` để giảm bộ đếm.

Một cách hình dung:

```text
CountDownLatch(3)

Worker A hoàn tất → countDown(): 3 → 2
Worker B hoàn tất → countDown(): 2 → 1
Worker C hoàn tất → countDown(): 1 → 0

Thread điều phối gọi await() ──đang chờ──► tiếp tục khi count = 0
```

Đây là **mốc đồng bộ**, không phải hàng đợi chứa dữ liệu, không tạo thread, không tự chạy task và cũng không tự đảm bảo task thành công. Việc bộ đếm về 0 chỉ cho biết các lần báo hiệu đã xảy ra theo hợp đồng do lập trình viên thiết kế.

Bài học tập trung vào **Java Concurrency thuần JDK**, không dùng các API chuyên biệt của Spring.

## 2. Những API cốt lõi

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

CountDownLatch latch = new CountDownLatch(3);

// Thread báo một sự kiện/một phần việc đã đến mốc:
latch.countDown();

// Thread cần đợi mốc:
latch.await();

// Thread cần giới hạn thời gian chờ:
boolean reached = latch.await(500, TimeUnit.MILLISECONDS);

// Quan sát bộ đếm, chủ yếu để chẩn đoán:
long count = latch.getCount();
```

Đoạn trên minh họa API; hai lời gọi `await` là hai cách sử dụng thay thế, không phải chương trình độc lập.

| API | Hành vi | Điểm cần nhớ |
| --- | --- | --- |
| `new CountDownLatch(n)` | Tạo latch với bộ đếm ban đầu bằng `n` | `n` không được âm; `n == 0` nghĩa latch đã mở sẵn |
| `countDown()` | Giảm bộ đếm 1 nếu còn dương | Không chặn; ở 0 rồi thì không giảm xuống âm |
| `await()` | Đợi cho tới khi bộ đếm bằng 0 | Có thể ném `InterruptedException` |
| `await(long, TimeUnit)` | Đợi tối đa thời gian quy định | Trả `true` nếu về 0, `false` nếu hết hạn; cũng có thể bị interrupt |
| `getCount()` | Trả số đếm hiện tại | Dùng monitoring/debugging, không thay cho phối hợp đồng bộ |

Constructor `new CountDownLatch(-1)` ném `IllegalArgumentException`. Giá trị `0` hợp lệ và làm `await()` trở về ngay, trừ tình huống thread bị interrupt theo hợp đồng của API.

**Không có `reset()`.** Khi về 0, latch mở vĩnh viễn. Những lần `await()` tiếp theo không còn chờ; những lần `countDown()` tiếp theo không có tác dụng. Muốn chờ một giai đoạn mới, tạo một `CountDownLatch` mới hoặc chọn synchronizer có thể tái sử dụng khi thích hợp.

## 3. Cơ chế đợi và báo hiệu

Giả sử có một latch đếm 2:

1. Thread M gọi `latch.await()` khi bộ đếm là 2. M sẽ chờ, không liên tục tự kiểm tra trong vòng lặp busy-spin.
2. Worker 1 gọi `countDown()`: bộ đếm chuyển 2 → 1, M **chưa** được mở khóa vì còn một tín hiệu chưa đến.
3. Worker 2 gọi `countDown()`: bộ đếm chuyển 1 → 0, M đủ điều kiện tiếp tục.
4. Thread N sau đó cũng gọi `await()` trên latch này thì có thể tiếp tục ngay vì mốc đã đạt.

Lưu ý:

- **`countDown()` không đợi `await()`**. Worker có thể báo xong ngay cả khi chưa có thread nào chờ.
- **Nhiều thread cùng `await()`** được phép. Khi count về 0, tất cả đều đủ điều kiện được đánh thức; thứ tự thực tế chạy tiếp do scheduler quyết định.
- `await()` không “nhận” hoặc “tiêu thụ” một permit. Một lần mốc đạt 0 mở đường cho mọi awaiter, khác `Semaphore.acquire()` vốn nhận permit.
- CountDownLatch không tự biết có bao nhiêu task thực tế được submit hoặc chạy. Nếu cấu hình count sai, latch có thể mở quá sớm hoặc không bao giờ mở.

Một mốc về 0 không tương đương “mọi công việc đều thành công”. Nếu worker ném exception nhưng vẫn gọi `countDown()`, latch vẫn mở; caller cần đọc `Future`, trạng thái lỗi hoặc cơ chế riêng để biết kết quả.

## 4. Ví dụ Java 17 chạy độc lập: đợi ba tác vụ rồi tổng hợp

Chương trình tạo ba `Callable<Integer>` chạy trong `ExecutorService`. Mỗi worker báo mốc hoàn tất bằng `countDown()` đặt trong `finally`. Main đợi latch về 0, sau đó dùng `Future.get()` để kiểm tra việc thực thi và lấy giá trị. Vì việc in diễn ra **sau mốc đồng bộ**, stdout không phụ thuộc thứ tự các worker được lập lịch.

Lưu vào tệp `CountDownLatchDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CountDownLatchDemo {
    public static void main(String[] args) throws Exception {
        int taskCount = 3;
        CountDownLatch completed = new CountDownLatch(taskCount);
        ExecutorService pool = Executors.newFixedThreadPool(taskCount);
        List<Future<Integer>> futures = new ArrayList<>();

        try {
            for (int i = 1; i <= taskCount; i++) {
                final int number = i;
                Callable<Integer> work = () -> {
                    try {
                        return number * 10;
                    } finally {
                        completed.countDown();
                    }
                };
                futures.add(pool.submit(work));
            }

            completed.await();
            System.out.println("All tasks signalled completion.");

            int sum = 0;
            for (Future<Integer> future : futures) {
                // Nếu một task lỗi, get() ném ExecutionException.
                sum += future.get();
            }

            System.out.println("Results count: " + futures.size());
            System.out.println("Total: " + sum);
            System.out.println("Latch count: " + completed.getCount());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
```

Chạy bằng Java 17 trở lên:

```bash
javac CountDownLatchDemo.java
java CountDownLatchDemo
```

Stdout:

```text
All tasks signalled completion.
Results count: 3
Total: 60
Latch count: 0
```

**Giải thích các điều kiện đúng:**

- Bộ đếm được đặt bằng đúng số công việc dự định báo hiệu.
- Mỗi `Callable` chỉ gọi `completed.countDown()` **một lần**, trong `finally`.
- `finally` chạy khi lời gọi `call()` của task đã bắt đầu đi tới đường kết thúc, kể cả khi phát sinh exception ở phần tính toán.
- Main chỉ in kết quả sau `completed.await()`. Tổng từ ba task là `10 + 20 + 30 = 60`.
- `Future.get()` vẫn quan trọng: latch không lưu kết quả và không mang exception của task. Nếu một task ném lỗi trước khi hoàn thành bình thường, `get()` sẽ ném `ExecutionException`; latch chỉ là tín hiệu mốc.
- `CountDownLatch` không bảo đảm thứ tự worker chạy; chương trình không in từ worker, nên kết quả hiển thị có thứ tự xác định.

**Ranh giới ví dụ:** Nếu `pool.submit(work)` thất bại trước khi nhận một task (chẳng hạn executor bị shutdown hoặc bị từ chối), worker tương ứng sẽ **không có cơ hội** gọi `countDown()`. Khi đó, chờ `await()` vô hạn sẽ không an toàn. Trong ứng dụng thật, cần cơ chế admission/rejection và timeout; phần tiếp theo giải thích cụ thể.

## 5. Vì sao countDown nên nằm trong finally?

Mã không an toàn:

```java
Runnable task = () -> {
    doWork();       // Nếu ném exception thì không chạy dòng tiếp.
    latch.countDown();
};
```

Đây là fragment: `doWork` và `latch` được khai báo ở nơi khác. Nếu `doWork()` ném `RuntimeException`, latch không giảm, khiến các thread đang đợi có nguy cơ chờ mãi.

Mẫu phù hợp hơn:

```java
Runnable task = () -> {
    try {
        doWork();
    } finally {
        latch.countDown();
    }
};
```

Đặt báo hiệu trong `finally` làm rõ hợp đồng: **“Task đã thoát khỏi phần công việc được bao”**, chứ **không nhất thiết là “task thành công”**. Chỉ cần bảo đảm mỗi task thuộc danh sách được tính vào latch thực sự bắt đầu thực thi. Nếu task bị hủy trước lúc bắt đầu, bị từ chối hoặc không được submit, `finally` trong task không chạy; phải xử lý việc báo hiệu/missing task ở tầng điều phối.

Một thiết kế khác là chỉ `countDown()` khi task thành công để mô hình hóa “đã có đủ N thành công”. Khi đó, nếu một task lỗi, việc đợi có thể không bao giờ kết thúc; bắt buộc phải có deadline hoặc đường lỗi riêng. Vì thế với nhu cầu **chờ mọi task kết thúc**, đặt `countDown()` trong `finally` thường rõ ràng và an toàn hơn.

## 6. Hai latch phối hợp thời điểm bắt đầu và kết thúc

Một kỹ thuật kiểm thử concurrency là dùng **hai hoặc ba latch riêng biệt**:

- `ready`: các worker xác nhận đã đến điểm chuẩn bị.
- `start`: main mở một cổng bắt đầu chung bằng một lần `countDown()`.
- `done`: các worker báo đã kết thúc công việc.

Cách này làm giảm sự phụ thuộc vào `Thread.sleep()` trong test. Nó không có nghĩa mọi worker chạy **đồng thời tuyệt đối**; scheduler vẫn quyết định worker nào nhận CPU trước.

Ví dụ minh họa độc lập, lưu tệp `StartTogetherDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class StartTogetherDemo {
    public static void main(String[] args) throws InterruptedException {
        int workers = 3;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        int[] results = new int[workers];

        try {
            for (int i = 0; i < workers; i++) {
                final int index = i;
                pool.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        results[index] = (index + 1) * 10;
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await();
            start.countDown();
            done.await();

            System.out.println("Results: "
                    + results[0] + "," + results[1] + "," + results[2]);
            System.out.println("All done: " + (done.getCount() == 0));
        } finally {
            start.countDown(); // Mở cổng nếu main bị interrupt.
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Workers did not terminate");
            }
        }
    }
}
```

Chạy:

```bash
javac StartTogetherDemo.java
java StartTogetherDemo
```

Stdout trong lần chạy bình thường:

```text
Results: 10,20,30
All done: true
```

Mỗi worker ghi một chỉ số mảng khác nhau, không tranh ghi cùng ô. `done.await()` chỉ trả khi cả ba đã báo mốc, nên main có thể đọc các giá trị đã ghi. Nếu task bị interrupt trước khi ghi, chương trình vẫn có thể mở `done` vì `finally` chạy; trong hệ thống cần phân biệt thành công và lỗi thì phải thêm `Future` hoặc trạng thái kết quả. Demo dùng `shutdownNow()` trong `finally` sau khi đã hoàn thành, không phải cách hủy một khối công việc đang chạy bình thường.

**Một deadlock test dễ gặp:** dùng `newFixedThreadPool(2)` nhưng có ba worker đều phải đợi `start.await()`, rồi main đợi `ready` với count 3. Hai worker đang chờ cổng start, worker thứ ba nằm trong queue nên không thể báo ready, và main không bao giờ mở cổng. Để tránh, không đặt số worker bị chặn ở cổng lớn hơn capacity thread, hoặc thiết kế giao thức không yêu cầu toàn bộ task đồng thời chiếm worker.

## 7. await(timeout): không nên chờ vô hạn khi có lỗi ngoài ý muốn

`await()` không có timeout có thể chờ mãi nếu số lần `countDown()` không đủ. Trong phần mềm có yêu cầu về độ trễ, cân nhắc:

```java
boolean reached = latch.await(2, TimeUnit.SECONDS);
if (!reached) {
    // Deadline đã hết: ghi nhận những công việc còn chưa báo hiệu,
    // yêu cầu hủy, trả lỗi hoặc chuyển sang phương án dự phòng.
    handleTimeout();
}
```

Đây là fragment, `latch` và `handleTimeout()` do ứng dụng định nghĩa.

| Kết quả | Diễn giải |
| --- | --- |
| `true` | Bộ đếm đã về 0 trong thời gian chờ |
| `false` | Hết hạn chờ mà bộ đếm vẫn chưa về 0 |
| `InterruptedException` | Thread đợi bị interrupt trong thao tác chờ |

Nếu thời gian chờ bằng 0 hoặc âm, `await(timeout, unit)` không đợi để bộ đếm thay đổi; nó kiểm tra trạng thái rồi trả kết quả tương ứng, trừ trường hợp thread đã bị interrupt.

**Timeout không tự giảm bộ đếm, không hủy task, không shutdown executor.** Các worker có thể tiếp tục sau khi `await(timeout)` trả `false`. Nếu muốn dừng, caller phải quản lý `Future.cancel(true)`, executor lifecycle, interrupt và cleanup theo hợp đồng riêng. Không giả định cancel tức thời sẽ làm task đang chạy ngừng mọi side effect.

Một ví dụ chắc chắn không phụ thuộc tốc độ máy:

```java
CountDownLatch neverOpened = new CountDownLatch(1);
boolean reached = neverOpened.await(0, TimeUnit.MILLISECONDS);
System.out.println(reached); // false
```

Ở đây không thread nào gọi `countDown()`, nên việc kiểm tra không chờ trả `false`. Đoạn này là fragment cần thêm các `import` phù hợp.

## 8. InterruptedException và hủy lúc đang chờ

`await()` và `await(timeout, unit)` đều là các thao tác **interruptible**. Nếu thread đang chờ bị interrupt, chúng ném `InterruptedException`. Nếu thread đã có interrupt status khi vào `await`, API cũng có thể ném ngay theo hợp đồng.

Trong một method có khả năng truyền exception, cách đơn giản là cho `InterruptedException` lan ra:

```java
void waitForReady(CountDownLatch ready) throws InterruptedException {
    ready.await();
}
```

Nếu cần bắt để xử lý trong method không khai báo checked exception, hãy xử lý interruption theo hợp đồng và thường **khôi phục interrupt flag**:

```java
try {
    ready.await();
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    return; // Ví dụ method void; dừng xử lý thay vì tiếp tục mù quáng.
}
```

Đây là fragment; `ready` phải được khai báo trước. Khi `await` ném `InterruptedException`, trạng thái interrupt có thể đã được xóa bởi cơ chế kiểm tra interrupt. Việc gọi `interrupt()` lại giúp tầng trên có thể quan sát yêu cầu dừng.

**Đừng tiếp tục dùng dữ liệu như thể mốc đã đạt** sau khi `await` bị interrupt. Tương tự, nếu `await(timeout)` trả `false`, code không nên đọc kết quả đang được worker ghi với giả định chúng đã hoàn tất, trừ khi có cơ chế đồng bộ khác.

## 9. Bảo đảm nhìn thấy dữ liệu: happens-before

Một đặc tính quan trọng của `CountDownLatch` là **memory consistency effects** của nó. Theo hợp đồng API:

> Các hành động trong một thread xảy ra trước lời gọi `countDown()` sẽ *happen-before* các hành động xảy ra sau khi một `await()` tương ứng trả về thành công ở thread khác.

Ví dụ:

```java
final int[] result = new int[1];
CountDownLatch ready = new CountDownLatch(1);

// Thread worker:
result[0] = 42;
ready.countDown();

// Thread đợi, chạy ở thread khác:
ready.await();
System.out.println(result[0]); // Có quan hệ visibility với ghi trước countDown.
```

Đây là ví dụ minh họa các đoạn chạy ở **hai thread khác nhau**, không phải một chương trình độc lập. Nếu worker thật sự ghi `result[0] = 42` rồi gọi `countDown()` và thread khác chờ `await()` thành công trước khi đọc, thay đổi đã được công bố theo quan hệ happens-before.

Lưu ý giới hạn:

- Những ghi dữ liệu **sau** `countDown()` không tự có bảo đảm tương tự từ latch.
- Nếu `await(timeout)` trả `false` hoặc `await` ném `InterruptedException`, không được giả định quan hệ đồng bộ của việc await **thành công**.
- Latch không biến mọi thao tác với một object mutable thành thread-safe. Nếu hai worker đồng thời cập nhật cùng một `HashMap` hoặc tăng một biến bằng phép đọc–sửa–ghi không được bảo vệ, vẫn có data race và lost update.
- Khi cần kết quả của nhiều worker, mỗi worker nên ghi vào vùng không tranh chấp, dùng collection concurrent hoặc synchronization thích hợp; latch chủ yếu đảm bảo **đã đến mốc và công bố các hành động trước mốc**.

Đây là điểm khiến latch hữu ích hơn một vòng `while (flag == false)` trên biến không `volatile`. Vòng lặp đó vừa tốn CPU vừa thiếu bảo đảm visibility khi không có quan hệ đồng bộ phù hợp.

## 10. CountDownLatch không phải Future và không mang lỗi

Một latch có thể về 0 ngay cả khi tất cả worker đã thất bại, miễn là mỗi worker vẫn `countDown()` trong `finally`. Nó **không** trả kết quả từng task, không lưu stack trace và không phân biệt success/failure.

So sánh:

| Công cụ | Chờ cái gì? | Kết quả/lỗi | Khả năng hủy |
| --- | --- | --- | --- |
| `CountDownLatch` | Đủ N tín hiệu | Chỉ bộ đếm và trạng thái mốc | Không có API cancel task |
| `Future<T>` | Một task có kết quả | `get()` trả `T` hoặc báo lỗi/hủy | `cancel(...)` cho task |
| `ExecutorService.invokeAll(...)` | Một nhóm Callable hoàn tất hoặc hết hạn | Danh sách Future từng task | API có thể hủy task chưa hoàn tất trong một số đường |
| `CompletableFuture.allOf(...)` | Nhiều CompletableFuture hoàn tất | Cần xem từng future để lấy kết quả | Semantics cancel cần thiết kế riêng |

Khi bài toán là “đợi N công việc rồi gom kết quả và lỗi”, `Future` hoặc `invokeAll` có thể ít code điều phối hơn. `CountDownLatch` phát huy tác dụng khi sự kiện đợi **không nhất thiết gắn với Future**, hoặc khi nhiều thread cần được giải phóng cùng một mốc.

Ví dụ, nếu dùng `ExecutorService.submit(Callable)` và `Future.get()` cho từng task, việc đợi Future lần lượt cuối cùng vẫn đảm bảo các task được xử lý kết quả; một latch riêng không bắt buộc phải thêm vào nếu không có yêu cầu coordination khác.

## 11. Những nguồn treo chương trình thường gặp

### 11.1. Khởi tạo count sai

```java
CountDownLatch done = new CountDownLatch(4);
// Nhưng chỉ có 3 task sẽ gọi done.countDown() mỗi task một lần.
done.await(); // Có thể chờ mãi.
```

Muốn đợi ba task, nên đặt count = 3. Nếu số task chưa xác định hoặc task sinh thêm task, một latch cố định có thể không phù hợp; cân nhắc `Phaser` hoặc cơ chế quản lý số công việc động.

### 11.2. Quên finally

Nếu task ném exception trước `countDown()`, latch bị thiếu tín hiệu. Dùng `try/finally` trên đường thực thi task và thiết kế đường lỗi rõ ràng.

### 11.3. Task bị từ chối hoặc hủy trước khi bắt đầu

Ví dụ executor gọi `RejectedExecutionHandler` và không nhận một task, hoặc `Future.cancel(...)` hủy task chưa chạy. `finally` bên trong task **không thực thi**. Nếu đã tính nó vào count, latch có thể không mở. Cần ràng buộc chính xác between admission và số lần báo hiệu; có thể chọn giao thức “chốt danh sách task được nhận rồi mới tạo latch”, hoặc dùng cấu trúc khác khi admission thay đổi động.

### 11.4. Thread pool không đủ worker để qua cổng

Dùng 5 task đều chờ cổng start nhưng pool chỉ có 2 worker, trong khi main đợi cả 5 task báo ready trước khi mở cổng. Hai worker giữ thread, ba task còn ở queue: hệ thống deadlock. Không sử dụng latch như một rào khởi động cho số task lớn hơn số worker có thể chạy đồng thời, trừ khi đã đổi giao thức để không bị circular wait.

### 11.5. Báo hiệu quá sớm

`countDown()` trước khi ghi dữ liệu hoặc trước khi kết thúc cập nhật quan trọng khiến awaiter có thể tiếp tục trong lúc trạng thái vẫn đang được sửa. Đặt countDown **sau** những hành động mà awaiter cần quan sát. Trong `finally`, nếu phải dọn tài nguyên và muốn mốc nghĩa là “cleanup xong”, phải sắp xếp sao cho cleanup xảy ra **trước** countDown.

### 11.6. Gọi countDown nhiều lần cho một nhiệm vụ

CountDownLatch không biết nguồn tín hiệu. Một worker gọi `countDown()` hai lần có thể làm latch mở sớm, dù worker khác chưa hoàn tất. Mỗi tín hiệu phải có ownership và nghĩa rõ ràng; count dư sau khi đã về 0 chỉ bị bỏ qua.

### 11.7. Dùng lại latch đã mở

Một latch đã về 0 không thể “đóng lại”. `getCount()` sẽ tiếp tục trả 0; `await()` trên nó không đợi giai đoạn thứ hai. Cần tạo một latch mới cho mốc tiếp theo hoặc chọn cơ chế nhiều pha.

## 12. So sánh với CyclicBarrier, Phaser, Semaphore và Condition

| Synchronizer | Dùng khi nào? | Điểm khác CountDownLatch |
| --- | --- | --- |
| `CountDownLatch` | Chờ một mốc gồm N tín hiệu, một hoặc nhiều thread chờ | One-shot; bên báo tín hiệu không bắt buộc chờ |
| `CyclicBarrier` | Một nhóm thread cùng tới barrier rồi cùng đi tiếp qua **nhiều lượt** | Barrier có thể tái sử dụng; các bên tham gia cùng gọi `await()`; có thể broken |
| `Phaser` | Nhiều pha đồng bộ, số bên tham gia có thể đăng ký/hủy đăng ký | Hỗ trợ tiến pha và số party thay đổi |
| `Semaphore` | Giới hạn số thao tác đồng thời bằng permits | `acquire` lấy permit và `release` trả lại; không phải mốc đếm xuống một lần |
| `Condition` | Chờ một điều kiện trạng thái dưới `Lock` và được báo qua `signal`/`signalAll` | Phải giữ lock tương ứng, thường đợi trong vòng lặp kiểm tra predicate |
| `Thread.join()` | Đợi một `Thread` cụ thể kết thúc | Gắn với vòng đời thread; latch có thể đại diện N sự kiện hoặc task, không cần thread riêng cho mỗi tín hiệu |

Lựa chọn dựa trên hợp đồng nghiệp vụ:

- “Đợi ba bước hoàn tất rồi đi tiếp một lần”: `CountDownLatch(3)`.
- “Mười worker phải gặp nhau ở cuối mỗi vòng tính và tái sử dụng rào”: `CyclicBarrier(10)` hoặc `Phaser`.
- “Chỉ cho phép năm request truy cập tài nguyên cùng lúc”: `Semaphore(5)`.
- “Đợi trạng thái queue chuyển từ rỗng sang có phần tử”: `BlockingQueue` hoặc `Condition` phù hợp hơn một latch chỉ dùng một lần.

`CountDownLatch` **không đếm số lượng công việc đang chạy theo kiểu tăng/giảm tùy ý**. Nó chỉ giảm tới 0, nên không phù hợp thay cho một bộ đếm outstanding tasks có thể tăng trở lại sau khi giai đoạn trước kết thúc.

## 13. Thiết kế đúng khi có timeout và cancellation

Trong hệ thống có deadline, một giao thức chờ có thể cần:

1. Chọn **mốc nào** phải xảy ra trước deadline (mọi task thoát, tối thiểu một số task thành công, hay một phần của pipeline sẵn sàng).
2. Lưu các `Future<?>` nếu cần yêu cầu hủy task thực sự.
3. Dùng `await(timeout, unit)` và xử lý rõ `false`.
4. Khi timeout, yêu cầu `cancel(true)` trên những công việc cần dừng, **nhưng không giả định mọi task dừng tức thì**.
5. Thiết kế tác vụ hỗ trợ interrupt và timeout I/O; sử dụng `try/finally` để cleanup.
6. Nếu cần chứng minh tài nguyên đã giải phóng, dùng một mốc completion/cleanup riêng; Future có thể bị cancelled trước khi task thực sự dừng.
7. Đảm bảo có log/metric về task chậm, số tín hiệu còn thiếu và deadline.

Mẫu fragment:

```java
if (!completed.await(2, TimeUnit.SECONDS)) {
    for (Future<?> future : futures) {
        future.cancel(true);
    }
    reportTimeout(completed.getCount());
}
```

`completed`, `futures` và `reportTimeout` là các thành phần do ứng dụng cung cấp. `getCount()` lúc timeout chỉ là trạng thái quan sát hiện tại; nó có thể thay đổi ngay sau đó do worker khác đang gọi `countDown()`. Không dùng số này làm một giao dịch atomic để tính toán chính xác những tác vụ còn chưa chạy.

**Thận trọng với lỗi phân phối task:** Nếu công việc bị từ chối trước khi tạo Future, không thể yêu cầu hủy nó thông qua Future list. Hợp đồng đếm phải được thiết kế sao cho task chưa được nhận không bị chờ vô hạn.

## 14. Kiểm thử đồng thời bằng CountDownLatch

CountDownLatch được dùng nhiều trong test vì có thể tạo mốc xác định thay vì dựa vào `Thread.sleep()`:

- `ready` bảo đảm worker đã chạy tới điểm chuẩn bị.
- `start` cho phép test điều khiển lúc mở cổng.
- `done` giúp test đợi công việc kết thúc.
- Các assertion được thực hiện **sau** `await()` thành công để tránh đọc kết quả giữa chừng.

Tuy nhiên, test cũng có thể treo nếu setup count sai hoặc pool thiếu worker. Nên dùng `await(timeout)` và fail với thông tin cụ thể:

```java
boolean allReady = ready.await(3, TimeUnit.SECONDS);
if (!allReady) {
    throw new AssertionError("Workers not ready: " + ready.getCount());
}
start.countDown();
```

Đây là fragment; `ready` và `start` được khởi tạo trong test. Khi test thất bại, cần bảo đảm `start.countDown()` trong `finally` hoặc cleanup phù hợp để tránh các worker vẫn mắc kẹt trong lúc bộ test cố shutdown.

CountDownLatch giúp tạo trật tự giữa **các mốc có quan hệ xác định**, nhưng không đảm bảo thứ tự mọi instruction giữa các worker sau khi được giải phóng. Nếu một test cần phát hiện race condition, vẫn cần chạy đủ scenario, kiểm tra invariant và dùng các công cụ hoặc kỹ thuật test concurrency khác.

## 15. Checklist sử dụng CountDownLatch

- [ ] Bộ đếm ban đầu phản ánh **đúng số tín hiệu** mà nghiệp vụ sẽ phát.
- [ ] Mỗi nguồn tín hiệu gọi `countDown()` đúng một lần tại **thời điểm có ý nghĩa**.
- [ ] Với completion của task, dùng `finally` để báo khi task đã bắt đầu và thoát, kể cả khi lỗi.
- [ ] Xử lý task **không được nhận** và task **bị hủy trước khi chạy**; đừng trông chờ `finally` của chúng.
- [ ] Dùng `await(timeout)` khi có deadline; phân biệt kết quả true/false.
- [ ] Xử lý `InterruptedException` và không coi thread bị interrupt là đã đạt mốc.
- [ ] Không đọc trạng thái do worker ghi sau một await thất bại mà không có synchronization khác.
- [ ] Nếu cần biết task thành công/lỗi, kết hợp `Future` hoặc result status.
- [ ] Nếu có cổng start, bảo đảm pool đủ capacity để các worker cần báo ready thực sự có thể bắt đầu.
- [ ] Không tái sử dụng latch đã về 0; tạo latch mới cho giai đoạn mới.
- [ ] Không giữ lock quan trọng trong lúc `await()` nếu worker cần lock đó để hoàn thành.
- [ ] Khi phải đo “đã cleanup hoàn toàn”, đặt countDown sau cleanup hoặc dùng completion signal riêng.

## 16. Câu hỏi tự kiểm tra

**1. `new CountDownLatch(3)` rồi gọi `countDown()` hai lần, `await()` có trả về bình thường không?**  
Chưa, vì bộ đếm còn 1. Lần `countDown()` thứ ba mới đưa nó về 0.

**2. Khi latch về 0, gọi `countDown()` tiếp có làm số đếm âm không?**  
Không, bộ đếm duy trì ở 0.

**3. Nhiều thread có thể cùng đợi một latch không?**  
Có. Khi mốc về 0, tất cả awaiter đều đủ điều kiện đi tiếp, nhưng không có thứ tự chạy tiếp được bảo đảm.

**4. `await(timeout)` trả `false` có hủy các worker không?**  
Không. Nó chỉ báo đã hết thời gian chờ mà count chưa về 0.

**5. Đặt `countDown()` trong `finally` có chứng minh task thành công không?**  
Không. Nó báo thân task đã đi qua đường kết thúc có finally; lỗi vẫn có thể xảy ra.

**6. Một task bị hủy trước khi được worker chạy có thực hiện finally bên trong task không?**  
Không. Vì body chưa bắt đầu, finally đó chưa được thực thi; phải xử lý thiếu tín hiệu ở bên điều phối.

**7. Tại sao đợi `await()` thành công giúp nhìn thấy dữ liệu được ghi trước `countDown()`?**  
Vì CountDownLatch có bảo đảm memory consistency: các hành động trước `countDown()` happen-before các hành động sau `await()` thành công tương ứng.

**8. Khi cần đồng bộ lặp lại theo nhiều vòng, có nên dùng lại CountDownLatch đã về 0 không?**  
Không. Dùng một latch mới cho mỗi lượt hoặc chọn `CyclicBarrier`/`Phaser`.

**9. Vì sao `CountDownLatch(5)` với fixed thread pool 2 và năm task đều chờ start có thể deadlock?**  
Nếu main chờ đủ năm tín hiệu ready trước khi mở start, chỉ hai task đầu giữ worker; ba task còn lại ở queue không thể báo ready, trong khi hai worker đầu không thoát để nhường chỗ.

## Tài liệu tham khảo

- [Java SE 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html)
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java SE 17 — CyclicBarrier](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CyclicBarrier.html)
- [Java SE 17 — Phaser](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Phaser.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronizers-002">CyclicBarrier phối hợp các vòng làm việc</a>

<details>
<summary>Xem chi tiết</summary>

# CyclicBarrier phối hợp các vòng làm việc trong Java

> **Trọng tâm:** `CyclicBarrier` là một **điểm hẹn đồng bộ có thể tái sử dụng** cho **một số lượng thread cố định**. Mỗi thread thực hiện phần việc của vòng hiện tại rồi gọi `await()`; khi **đủ số parties** đến, barrier có thể chạy một `barrierAction` duy nhất, mở cổng cho các thread tiếp tục và **tự sẵn sàng cho vòng tiếp theo**. Các mốc đồng bộ này giúp các thuật toán nhiều vòng (*iterative/phase-based computation*) không đọc dữ liệu vòng sau trước khi mọi thread hoàn thành phần việc vòng trước. Nhưng một thread vắng mặt, bị ngắt hoặc quá hạn có thể làm cả nhóm kẹt hoặc khiến barrier trở thành *broken*.

Bài thuộc nhóm **Synchronizers**, phạm vi **Java concurrency nói chung**. Ví dụ sử dụng Java SE 17+, không phụ thuộc Spring hay một framework nào khác.

## 1. Vì sao cần một điểm hẹn giữa nhiều thread?

Giả sử chia một mảng thành 4 vùng. 4 worker độc lập tính kết quả của **vòng k** trên vùng mình phụ trách. Vòng `k+1` cần nhìn thấy **toàn bộ** kết quả của vòng `k`; nếu worker nhanh được phép bước sang vòng mới ngay, nó có thể trộn dữ liệu cũ với dữ liệu mới.

~~~text
              VÒNG k                  VÒNG k+1
Worker 1    [tính A] ----┐          [tính A mới] -----┐
Worker 2    [tính B] --┐ |          [tính B mới] ---┐ |
Worker 3    [tính C] ----+--> BARRIER --> [tính C] --+--> BARRIER
Worker 4    [tính D] --┘ |          [tính D mới] ---┘ |
                        |                            |
              Tất cả đã hoàn thành       Tất cả đã hoàn thành
              trước khi sang vòng mới    trước khi sang vòng tiếp
~~~

Barrier là **đồng bộ theo giai đoạn**: những worker cùng một nhóm phải hoàn tất một mốc chung trước khi nhóm đi tiếp. Nó **không** phân phối các task, không tự chia mảng, không tự tạo thread và không sửa thuật toán nghiệp vụ. Lập trình viên vẫn phải viết công việc của từng thread, chọn số worker và quản lý vòng đời.

Ví dụ tình huống phù hợp:

- Tính toán lặp nhiều bước: mô phỏng vật lý, xử lý lưới, thuật toán relaxation, iterative numerical processing.
- Xử lý dữ liệu theo các vòng: mọi worker hoàn thành round hiện tại rồi chuyển state.
- Thử nghiệm concurrency: cần nhiều thread cùng đến một điểm đã định trước để điều phối các bước test.
- Pipeline có các **giai đoạn chung** mà tất cả thành viên nhóm phải hoàn thành trước khi mở giai đoạn mới.

Không dùng barrier khi công việc hoàn toàn độc lập và chỉ cần thu kết quả cuối; lúc ấy `Future.get()`, `invokeAll` hoặc `CountDownLatch` có thể đơn giản hơn.

## 2. API cốt lõi của CyclicBarrier

`java.util.concurrent.CyclicBarrier` có hai constructor chính:

~~~java
// Snippet API; cần import java.util.concurrent.CyclicBarrier.
CyclicBarrier simple = new CyclicBarrier(3);
CyclicBarrier withAction =
        new CyclicBarrier(3, () -> System.out.println("round done"));
~~~

Số `parties` là số **lượt đến** yêu cầu để mở barrier mỗi vòng; trong mô hình thông dụng, mỗi worker đến **một lần cho mỗi vòng**. `parties <= 0` không hợp lệ và constructor ném `IllegalArgumentException`.

Các phương thức:

| API | Ý nghĩa |
| --- | --- |
| `await()` | Worker đến điểm hẹn, **chờ** cho đến khi đủ parties hoặc barrier bị phá; trả arrival index |
| `await(timeout, unit)` | Cũng chờ đủ parties, nhưng có timeout; khi timeout có thể phá cả barrier |
| `getParties()` | Trả số parties cấu hình khi tạo; đây không phải current active-thread count |
| `getNumberWaiting()` | Số parties đang chờ ở barrier hiện tại; dùng chủ yếu cho monitoring/debug |
| `isBroken()` | Cho biết barrier đang ở trạng thái *broken* |
| `reset()` | Đưa barrier vào generation mới, đồng thời làm các waiter hiện hữu nhận `BrokenBarrierException` |

CyclicBarrier **không có** `arrive()` không chờ, `arriveAndDeregister()` hoặc `getPhase()` như `Phaser`. Nó cũng **không tự thay đổi parties** khi một worker chết. Nếu số workers có thể co giãn, hãy cân nhắc một synchronizer khác.

### Parties khác threads trên toàn JVM

`new CyclicBarrier(4)` không có nghĩa JVM chỉ có 4 threads, hay executor sẽ tự tạo 4 worker. Nó chỉ định một *rendezvous* phải có **bốn lần đến của nhóm** ở mỗi vòng. Các thread không tham gia vẫn chạy bình thường. Một barrier dùng chung với những thread không thuộc nhóm có thể làm protocol sai; mỗi thread phải hiểu rõ mình đang tham gia vòng nào.

## 3. Vòng đời một barrier: reusable và generation

Một barrier thành công theo quy trình:

1. Worker A đến và gọi `await` → chờ.
2. Worker B đến → tiếp tục chờ.
3. Worker C đến, đủ `parties=3`.
4. Nếu có `barrierAction`, **thread cuối cùng đến** chạy action.
5. Khi action hoàn tất bình thường, mọi waiter được phép ra khỏi `await`.
6. Barrier **tự reset cho vòng kế tiếp**; nhóm có thể lặp lại cùng quy trình.

~~~text
CyclicBarrier(parties=3)

Generation 0:
 A: await ──────────────┐
 B: await ────────────┐ │
 C: await ── last ────┤ │
                      ▼ ▼
                 [barrierAction]
                        |
                  release A,B,C
                        |
Generation 1:  A/B/C lại await
                        |
                  [barrierAction] ...
~~~

**Phân biệt tự mở lại và reset thủ công:** Sau **một vòng thành công**, CyclicBarrier tự dùng generation tiếp theo; không cần `reset()`. `reset()` chủ yếu phục vụ khi cần xử lý trạng thái broken theo protocol, và nếu đang có waiter, nó **phá vòng mà waiter ấy đang đợi**.

### Không là broadcast event độc lập

Barrier chỉ mở khi **đủ parties** đến. Một thread gọi `await()` trong khi thiếu thành viên không nhận được “event done” từ việc thread khác ở nơi xa gọi `countDown`; nó sẽ chờ. Đây là khác biệt lớn so với `CountDownLatch`.

## 4. Ví dụ 1 — ba worker phối hợp qua ba vòng

Ví dụ dùng 3 thread và 3 vòng. Trong mỗi vòng, worker `i` ghi một giá trị vào **ô riêng `values[i]`**, rồi chờ barrier. `barrierAction` đọc ba ô để tổng hợp và ghi vào tổng chung. Không dựa vào thứ tự OS schedule.

Tạo tệp `ThreeRoundsBarrier.java` (Java 17+):

~~~java
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreeRoundsBarrier {
    public static void main(String[] args) throws InterruptedException {
        int workers = 3;
        int rounds = 3;
        int[] values = new int[workers];
        AtomicInteger completedRounds = new AtomicInteger();
        AtomicInteger grandTotal = new AtomicInteger();

        CyclicBarrier barrier = new CyclicBarrier(workers, () -> {
            int roundSum = 0;
            for (int value : values) {
                roundSum += value;
            }
            grandTotal.addAndGet(roundSum);
            completedRounds.incrementAndGet();
        });

        Thread[] threads = new Thread[workers];
        for (int i = 0; i < workers; i++) {
            final int slot = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int round = 1; round <= rounds; round++) {
                        values[slot] = round;
                        barrier.await();
                    }
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }, "worker-" + i);
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("rounds completed = "
                + completedRounds.get());
        System.out.println("grand total = " + grandTotal.get());
        System.out.println("broken = " + barrier.isBroken());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac ThreeRoundsBarrier.java
java ThreeRoundsBarrier
~~~

Stdout trên đường chạy bình thường:

~~~text
rounds completed = 3
grand total = 18
broken = false
~~~

Diễn giải:

- Vòng 1: ba worker đều ghi `1`, action cộng `1+1+1=3`.
- Vòng 2: mỗi worker ghi `2`, action cộng `2+2+2=6`.
- Vòng 3: mỗi worker ghi `3`, action cộng `3+3+3=9`.
- `grandTotal = 3+6+9 = 18`; action được gọi **chính xác một lần cho mỗi vòng thành công**, tổng 3 lần.
- `barrier.await()` ở mỗi worker chặn sự chuyển sang vòng `round+1` cho tới khi mọi worker hoàn thành vòng hiện tại.
- Sau `join` cả ba threads, main đọc kết quả và in đúng một trình tự xác định.

Không đặt `System.out.println` cho từng worker ngay trước barrier rồi đòi thứ tự dòng log giống nhau mọi lần chạy: **thứ tự đến barrier không được scheduler cố định**.

**Vì sao dùng `int[]` không khóa ở đây?** Mỗi thread ghi **một slot khác nhau**; không có hai threads ghi cùng một slot. `barrierAction` đọc sau khi tất cả đã gọi `await`, phù hợp bảo đảm *happens-before* của barrier. Nếu worker khác ngoài nhóm sửa `values` đồng thời, hoặc các worker cùng ghi một slot, vẫn cần đồng bộ bổ sung.

## 5. Giá trị trả về của await(): arrival index

`await()` trả một `int` biểu diễn **vị trí đến trong generation hiện tại**, từ `getParties()-1` cho người đến đầu tới **0 cho người đến cuối**.

Với `parties=4`, bốn calls thành công trong một generation nhận bốn giá trị phân biệt `3,2,1,0`, không nhất thiết theo thứ tự thread IDs. `0` là **last arrival**; đây cũng là thread thực hiện barrier action nếu có.

### Ví dụ 2 — kiểm chứng arrival indices không phụ thuộc scheduling

Tạo `BarrierArrivalIndices.java` (Java 17+):

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

public class BarrierArrivalIndices {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger actions = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(
                4, actions::incrementAndGet);

        List<Integer> indices =
                Collections.synchronizedList(new ArrayList<>());

        Thread[] workers = new Thread[4];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                try {
                    indices.add(barrier.await());
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });
            workers[i].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }
        Collections.sort(indices);

        System.out.println("arrival indices = " + indices);
        System.out.println("action count = " + actions.get());
    }
}
~~~

Chạy:

~~~bash
javac BarrierArrivalIndices.java
java BarrierArrivalIndices
~~~

Stdout:

~~~text
arrival indices = [0, 1, 2, 3]
action count = 1
~~~

`indices` là synchronized list vì **nhiều threads cùng add** sau khi barrier mở. Main chỉ sort sau khi tất cả worker đã kết thúc. Không sử dụng `ArrayList` thường để nhiều worker `add` cùng lúc rồi nghĩ barrier làm mọi mutation sau `await` tuần tự: sau khi được mở, **các worker lại tiếp tục đồng thời**.

### Có nên chọn “leader” bằng index 0?

Có thể dùng `index==0` làm dấu hiệu last-arriving thread để thực hiện một thao tác **sau khi barrier đã mở**, nhưng nếu thao tác đó phải hoàn tất **trước khi các worker khác tiếp tục vòng mới**, dùng **`barrierAction`** mới đúng. Sau khi `await` trả về, tất cả có thể chạy đồng thời; những worker khác không phải chờ riêng thao tác của thread index 0.

Thí dụ logic:

~~~java
// Snippet minh họa bên trong một vòng có thể throw Exception.
int index = barrier.await();
if (index == 0) {
    logRoundCompleted();
}
// Không đảm bảo logRoundCompleted() xong trước code của workers khác.
~~~

Nếu cần phát một immutable snapshot vào đầu vòng kế tiếp, hãy xuất bản nó trong barrier action (hoặc thêm một điểm đồng bộ thứ hai sau thao tác leader).

## 6. Barrier action: chạy khi đủ parties, trước khi mở cổng

Constructor `new CyclicBarrier(parties, barrierAction)` cho phép đăng ký một `Runnable` được thực hiện **một lần trên mỗi generation thành công**, khi thread cuối đến và **trước khi các threads còn lại được giải phóng**.

Vai trò điển hình:

- Gộp kết quả tạm của các worker.
- Đổi buffer `next/current` cho vòng kế tiếp.
- Kiểm tra bất biến cuối vòng.
- Tính/ghi nhận thống kê của từng round.
- Reset những dữ liệu thuộc vòng vừa kết thúc, nếu việc reset thực sự diễn ra sau khi mọi worker ngừng dùng chúng.

**Không phải một task chạy trên “barrier thread” riêng.** Barrier action được chạy bởi **last-arriving worker**. Nếu action block lâu, chính worker đó và các waiter đều bị giữ, nên tổng latency vòng tăng. Không thực hiện network I/O vô hạn, lấy lock phức tạp hay chờ một worker khác đang ở cùng barrier trong action.

### Ví dụ 3 — đổi double buffer ở ranh giới vòng

Tạo `DoubleBufferBarrier.java` (Java 17+):

~~~java
import java.util.Arrays;
import java.util.concurrent.CyclicBarrier;

public class DoubleBufferBarrier {
    static final class State {
        int[] current = new int[4];
        int[] next = new int[4];
        int completedRounds;
    }

    public static void main(String[] args) throws InterruptedException {
        State state = new State();

        CyclicBarrier barrier = new CyclicBarrier(4, () -> {
            state.current = state.next;
            state.next = new int[4];
            state.completedRounds++;
        });

        Thread[] workers = new Thread[4];
        for (int i = 0; i < workers.length; i++) {
            final int slot = i;
            workers[i] = new Thread(() -> {
                try {
                    for (int round = 0; round < 3; round++) {
                        state.next[slot] = state.current[slot] + 1;
                        barrier.await();
                    }
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });
            workers[i].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("completed rounds = "
                + state.completedRounds);
        System.out.println("values = "
                + Arrays.toString(state.current));
    }
}
~~~

Chạy:

~~~bash
javac DoubleBufferBarrier.java
java DoubleBufferBarrier
~~~

Đầu ra xác định:

~~~text
completed rounds = 3
values = [3, 3, 3, 3]
~~~

Mỗi worker chỉ sửa `state.next[slot]` của mình trước `await`. Khi cả bốn đến, action đổi tham chiếu buffer và tạo buffer trống cho vòng mới. Các worker chỉ bắt đầu round tiếp sau khi action hoàn tất nên đọc được buffer mới. Sau ba rounds, tất cả giá trị bằng 3.

Đây là phép cập nhật một giá trị trên mỗi slot, không phải bài toán numerical stencil phức tạp. Với thuật toán mà mỗi ô mới phụ thuộc **hàng xóm**, các worker cũng phải đọc **chỉ từ current** và ghi **chỉ vào next** trong một vòng, tránh sửa current khi worker khác còn đọc.

### Không phải mọi mutation đều tự có tính nhất quán

Việc `state.current`/`state.next` là field thường vẫn phù hợp **trong phạm vi đồng bộ của barrier**: các hành động trước `await` được đồng bộ với barrier action và những hành động sau successful `await`. Nếu một thread ngoài barrier muốn đọc snapshot trong lúc workers còn đang tính, thread ấy không được bảo đảm đồng bộ chỉ nhờ barrier; cần safe publication/lock/volatile/immutable snapshot riêng.

## 7. Java Memory Model: happens-before qua barrier

Một điểm nhiều người bỏ qua: `CyclicBarrier` cung cấp quan hệ *memory consistency* trong Javadoc:

> Những hành động trong một thread **trước khi gọi `await()`** *happen-before* những hành động thuộc **barrier action**; những hành động của barrier action *happen-before* những hành động **sau khi `await()` trả về thành công** ở các thread khác.

Sơ đồ:

~~~text
Worker 1: write result[0] -----+
Worker 2: write result[1] -----+--> all await()
Worker 3: write result[2] -----+
                                     |
                                     v
                               barrierAction
                              đọc toàn bộ result
                              cập nhật next state
                                     |
                               happens-before
                                     v
                       workers tiếp tục sau await
                       đọc state đã công bố
~~~

Đây là lý do `ThreeRoundsBarrier` có thể dùng từng slot của một `int[]` mà không cần làm từng slot thành `AtomicInteger`, **nếu ownership của slot và giai đoạn được quản lý đúng**.

Không suy diễn:

- Barrier tự bảo vệ một shared counter khi nhiều worker `counter++` trước `await`. Các lần `++` vẫn cạnh tranh và có lost update.
- Những ghi **sau await** của hai thread tự được serialize. Barrier đã mở, threads lại chạy song song.
- Thread thứ ba không hề tham gia barrier được bảo vệ cho mọi lần đọc state.
- I/O ngoài JVM hoặc transaction database tự có commit/visibility chỉ vì Java thread đã đi qua barrier.
- Khi barrier **broken** và `await` ném exception, code có thể xử lý như một successful phase. Phải coi generation lỗi là không hoàn chỉnh.

Nếu một worker ghi nhiều ô, một thread khác đọc những ô đó **trước khi đủ parties** thông qua đường không được đồng bộ, data race vẫn có thể xảy ra. Barrier chỉ đúng khi mỗi giai đoạn thực sự tuân thủ giới hạn ownership của nó.

## 8. Barrier bị broken nghĩa là gì?

Trạng thái *broken* xuất hiện khi một generation không thể hoàn thành theo protocol. Các nguyên nhân thường gặp:

- Một thread đang chờ tại barrier bị `interrupt()`.
- Một thread gọi timed `await` và **hết thời gian** trước khi đủ parties.
- `barrierAction` ném unchecked exception hoặc error.
- Coordinator gọi `reset()` khi đang có các waiter.
- Một số tình huống lỗi đồng bộ khác theo hợp đồng API.

`CyclicBarrier` dùng mô hình **all-or-none breakage**: khi một waiter thất bại kiểu interrupt/timeout, các waiter còn lại trong generation ấy không nên tiếp tục như thể toàn bộ nhóm thành công; chúng thường nhận `BrokenBarrierException`.

Khi barrier broken:

- `isBroken() == true`.
- Các lời gọi `await` tiếp theo **ném `BrokenBarrierException`** thay vì chờ một generation bình thường (cho tới khi có reset).
- Những threads đang chờ được đánh thức bằng kết quả lỗi.
- Barrier **không tự hồi phục** chỉ vì một worker mới đến.
- Cần quyết định toàn round đã thất bại, thu hồi phần việc hoặc reset theo một coordinator rõ ràng.

### Phân biệt ba exception thường gặp

| Exception | Ai nhận, trong hoàn cảnh nào? |
| --- | --- |
| `InterruptedException` | Worker đang `await` bị interrupt (hoặc vào lúc còn interrupt flag) và call phản ứng với interrupt |
| `TimeoutException` | Worker gọi `await(timeout,unit)` hết thời hạn |
| `BrokenBarrierException` | Worker khác trong nhóm đã làm barrier broken, hoặc caller đến khi barrier đang broken |

CyclicBarrier còn có thể chuyển unchecked exception từ `barrierAction` qua thread thực hiện action. Không “chữa” exception của một worker bằng cách cho worker ấy tiếp tục vòng mới trong khi cả nhóm vẫn broken.

## 9. Ví dụ 4 — timeout phá barrier, reset rồi dùng lại

Tạo `BarrierTimeoutReset.java` (Java 17+):

~~~java
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class BarrierTimeoutReset {
    public static void main(String[] args) throws Exception {
        AtomicInteger actions = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(
                2, actions::incrementAndGet);

        boolean timedOut = false;
        try {
            // Chỉ main tới: không có party thứ hai.
            barrier.await(50, TimeUnit.MILLISECONDS);
        } catch (TimeoutException expected) {
            timedOut = true;
        }

        System.out.println("timed out = " + timedOut);
        System.out.println("broken before reset = "
                + barrier.isBroken());

        barrier.reset();
        System.out.println("broken after reset = "
                + barrier.isBroken());

        AtomicInteger successes = new AtomicInteger();
        Runnable workerTask = () -> {
            try {
                barrier.await();
                successes.incrementAndGet();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        };

        Thread a = new Thread(workerTask);
        Thread b = new Thread(workerTask);
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("successful await calls = "
                + successes.get());
        System.out.println("actions = " + actions.get());
    }
}
~~~

Chạy:

~~~bash
javac BarrierTimeoutReset.java
java BarrierTimeoutReset
~~~

Stdout:

~~~text
timed out = true
broken before reset = true
broken after reset = false
successful await calls = 2
actions = 1
~~~

Lần `await(50 ms)` đầu không có party thứ hai nên **chắc chắn** timeout; barrier broken. `reset()` tạo generation mới; hai threads của lượt sau cùng chờ và cùng thành công, action chạy một lần.

Đây là demo **reset có điều phối**, không chứng minh có thể reset bất kỳ lúc nào trong hệ thống thật. Nếu workers của generation cũ vẫn đang chạy/phục hồi, gọi reset quá sớm có thể khiến các threads ấy rơi vào generation khác, mất đồng bộ với công việc của nhóm.

### Timeout của một waiter ảnh hưởng cả nhóm

Nếu một worker gặp timeout và phá barrier, những worker khác đã đang đợi ở đó có thể nhận `BrokenBarrierException`. Vì vậy timed await là **timeout ở cấp protocol của nhóm**, không phải chỉ caller tự từ bỏ chờ mà tất cả những người khác sẽ tiếp tục thành công theo lịch cũ.

Đây là khác biệt quan trọng với `Future.get(timeout)`: timeout của một lần đợi Future **không tự hủy computation**; timeout của `CyclicBarrier.await` có thể **làm cả generation broken**.

## 10. Ví dụ 5 — interrupt một waiter làm waiter khác nhận BrokenBarrierException

Để minh họa tính *all-or-none*, dùng `parties=3` nhưng **chỉ tạo hai waiters**. Khi cả hai đang chờ, main ngắt thread A. A nhận `InterruptedException`; B thức dậy với `BrokenBarrierException`.

Tạo `BarrierInterruptDemo.java` (Java 17+):

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class BarrierInterruptDemo {
    public static void main(String[] args) throws InterruptedException {
        CyclicBarrier barrier = new CyclicBarrier(3);
        List<String> outcomes =
                Collections.synchronizedList(new ArrayList<>());

        Thread a = new Thread(() -> {
            try {
                barrier.await();
                outcomes.add("A-success");
            } catch (InterruptedException expected) {
                outcomes.add("A-interrupted");
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException ex) {
                outcomes.add("A-broken");
            }
        });

        Thread b = new Thread(() -> {
            try {
                barrier.await();
                outcomes.add("B-success");
            } catch (InterruptedException ex) {
                outcomes.add("B-interrupted");
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException expected) {
                outcomes.add("B-broken");
            }
        });

        a.start();
        b.start();

        // Chỉ có hai threads, nên barrier(3) chưa thể mở.
        while (barrier.getNumberWaiting() != 2) {
            Thread.yield();
        }
        a.interrupt();

        a.join();
        b.join();
        Collections.sort(outcomes);

        System.out.println("outcomes = " + outcomes);
        System.out.println("broken = " + barrier.isBroken());
        System.out.println("number waiting = "
                + barrier.getNumberWaiting());
    }
}
~~~

Chạy:

~~~bash
javac BarrierInterruptDemo.java
java BarrierInterruptDemo
~~~

Stdout khi không có interruption ngoài chương trình:

~~~text
outcomes = [A-interrupted, B-broken]
broken = true
number waiting = 0
~~~

Vòng `while(getNumberWaiting()!=2)` chỉ dùng để **điều phối demo**, không phải mẫu chờ tốt cho production: nó có thể spin CPU nếu worker không được scheduled. Test suite thực tế nên có **deadline** và nhánh cleanup/fail-fast, không đợi vô hạn dựa vào metric. `getNumberWaiting()` là thông tin hiện trạng, không tạo cơ chế đợi/notification tự thân.

A bị interrupt khi đang chờ → chính A nhận `InterruptedException` và restore flag; B nhận `BrokenBarrierException`. **Cả hai** đều không được coi là đã hoàn thành successful round. Main có thể quyết định bỏ vòng, báo lỗi hoặc tạo protocol reset phù hợp.

## 11. Barrier action có thể ném exception: generation bị phá

Barrier action là một phần của giao thức. Nếu action thất bại, các worker không được phép tiếp tục như đã hoàn thành barrier thành công.

Theo Javadoc:

- Barrier action chạy bởi **thread đến cuối**.
- Nếu action thất bại vì exception, barrier trở thành **broken**.
- Exception từ action được truyền trong thread chạy action.
- Những waiter khác được giải phóng bằng `BrokenBarrierException`.

### Ví dụ 6 — action lỗi và trạng thái broken

Tạo `BarrierActionFailure.java` (Java 17+):

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class BarrierActionFailure {
    public static void main(String[] args) throws InterruptedException {
        CyclicBarrier barrier = new CyclicBarrier(2, () -> {
            throw new IllegalStateException("action failed");
        });

        List<String> outcomes =
                Collections.synchronizedList(new ArrayList<>());

        Runnable waiter = () -> {
            try {
                barrier.await();
                outcomes.add("success");
            } catch (InterruptedException e) {
                outcomes.add("InterruptedException");
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException e) {
                outcomes.add("BrokenBarrierException");
            } catch (IllegalStateException e) {
                outcomes.add("IllegalStateException");
            }
        };

        Thread a = new Thread(waiter);
        Thread b = new Thread(waiter);
        a.start();
        b.start();
        a.join();
        b.join();

        Collections.sort(outcomes);
        System.out.println("outcomes = " + outcomes);
        System.out.println("broken = " + barrier.isBroken());
    }
}
~~~

Chạy:

~~~bash
javac BarrierActionFailure.java
java BarrierActionFailure
~~~

Stdout:

~~~text
outcomes = [BrokenBarrierException, IllegalStateException]
broken = true
~~~

Hai threads đến barrier. Last arriver chạy action và nhận `IllegalStateException`; thread còn lại thức dậy với `BrokenBarrierException`. Action ném exception trước khi hoàn tất thành công nên **generation bị phá**. Dữ liệu vòng đang tính cần được đánh dấu **failed**, không phát ra như một snapshot hợp lệ.

### Barrier action nên làm gì?

Nên là thao tác **ngắn, xác định, biết xử lý lỗi** như đổi buffer, gộp một mảng hoặc kiểm tra invariant. Với công việc tốn thời gian hoặc có I/O, cần cân nhắc thiết kế khác: action chạy dưới thread worker, toàn bộ nhóm đợi action xong. Nếu action đang giữ lock hoặc chờ một task do chính worker còn đang đợi barrier đảm nhiệm, có thể kẹt.

Đừng dùng `try { ... } catch (Exception ignored) { }` để giữ barrier chạy vô hạn khi dữ liệu đã lỗi. Có thể catch một lỗi **được định nghĩa là recoverable** và ghi trạng thái vòng, nhưng failure policy phải rõ ràng.

## 12. reset() không phải nút “chữa deadlock” tự động

`reset()` đưa barrier sang generation mới. Nhưng những worker **đang đợi** sẽ nhận `BrokenBarrierException`, vì generation mà họ tham gia đã bị phá.

Sai lầm:

~~~java
// Snippet về hành vi nguy hiểm.
if (barrier.getNumberWaiting() > 0) {
    barrier.reset(); // Mọi waiter hiện hữu có thể nhận lỗi.
}
~~~

Không được gọi reset định kỳ chỉ vì thấy còn waiters; bản chất barrier là **chờ các parties**. Nếu một worker chậm, reset gây hủy luôn phase; nếu caller không xử lý exception, cả nhóm có thể rơi vào trạng thái không đồng bộ.

Protocol hồi phục gợi ý:

1. Coordinator nhận biết **đã broken** hoặc round bị hủy.
2. Dừng admission vào round mới, yêu cầu tất cả worker xử lý lỗi và **rời wait**.
3. Chờ/ack các worker thuộc generation lỗi, phân loại dữ liệu dở dang.
4. Chỉ khi nhóm đã thống nhất trạng thái, cân nhắc `reset()` hoặc **tạo barrier mới** cho team mới.
5. Tiếp tục từ một state nhất quán, không “chắp” dữ liệu của hai rounds.
6. Nếu số participants thay đổi, `Phaser` hoặc tái cấu trúc worker team thường thích hợp hơn.

Việc đợi mọi worker rời một generation lỗi trước reset là một **quy tắc thiết kế an toàn của ứng dụng**, không phải điều kiện bắt buộc Java API để gọi reset. Ý chính: tránh trộn các nhóm/rounds khi có race giữa recovery và arrivals.

## 13. Một thread không đến đủ barrier: deadlock hoặc chờ vô hạn

`await()` bản thường **không có timeout**. Nếu cấu hình parties=4 nhưng chỉ có 3 workers thật sự gọi await ở một vòng, ba worker có thể chờ mãi.

Nguyên nhân:

- Một worker bị lỗi trước `await`.
- Một worker tự `return` sớm vì điều kiện nghiệp vụ, không báo cho nhóm.
- Số parties cấu hình không khớp số threads đang active.
- Một worker đợi lock mà worker khác giữ khi đang chờ barrier.
- Executor có **ít worker threads hơn parties**, khiến tasks chưa được chạy vì các worker đang chạy đều bị chặn ở barrier.
- Tác vụ bị cancel/interrupted, nhưng code bắt lỗi sai và không báo cho coordinator.
- Một worker gọi `await` hai lần trong một vòng hoặc bỏ qua một round, gây sai phase matching.

### Ví dụ anti-pattern: pool có hai worker nhưng barrier cần ba parties

~~~java
// SAI: demo cấu trúc, đừng chạy rồi chờ Future.get() vô hạn.
ExecutorService pool = Executors.newFixedThreadPool(2);
CyclicBarrier barrier = new CyclicBarrier(3);

for (int i = 0; i < 3; i++) {
    pool.submit(() -> {
        barrier.await();
        return null;
    });
}
~~~

Hai tasks đầu đang chạy và chờ barrier; task thứ ba nằm ở queue của executor vì pool chỉ có 2 workers. Không có worker rảnh để chạy task thứ ba, nên barrier không đủ parties. Đây là **thread-pool starvation deadlock** do thiết kế scheduling, không phải lỗi implementation `CyclicBarrier`.

**Cách sửa:** đảm bảo có **ít nhất số worker có thể đến barrier đồng thời** (hoặc thiết kế lại task scheduling để không block hết pool), và theo dõi exceptions/timeouts. Nếu tasks còn phụ thuộc locks/I/O, chỉ tăng pool size có thể chưa giải quyết nguyên nhân.

## 14. Ví dụ 7 — ExecutorService đủ worker để mở barrier

Tạo `BarrierExecutorPool.java` (Java 17+):

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BarrierExecutorPool {
    public static void main(String[] args) throws Exception {
        AtomicInteger actions = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(
                3, actions::incrementAndGet);

        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                futures.add(pool.submit(() -> barrier.await()));
            }

            List<Integer> indices = new ArrayList<>();
            for (Future<Integer> future : futures) {
                indices.add(future.get());
            }
            Collections.sort(indices);

            System.out.println("indices = " + indices);
            System.out.println("actions = " + actions.get());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac BarrierExecutorPool.java
java BarrierExecutorPool
~~~

Stdout:

~~~text
indices = [0, 1, 2]
actions = 1
~~~

Có đúng ba worker slots cho ba tasks, nên mỗi task có cơ hội đến barrier. Khi party thứ ba đến, action tăng counter, ba `await` trả về các arrival indices `0,1,2` (sau sort). `Future.get()` chờ cả ba tasks trước khi in kết quả.

**Phạm vi demo:** Không có task nào cố tình lỗi; đây không phải mẫu production có deadline toàn diện. Trong chương trình thật, dùng `get(timeout)`/timed `await` và định nghĩa nhánh cancellation, error propagation, shutdown. Nếu executor có pool size đúng 3 nhưng tasks đợi lẫn nhau ở lock hoặc external resource, barrier vẫn có thể kẹt.

## 15. CyclicBarrier khác CountDownLatch và Phaser

| Tiêu chí | `CyclicBarrier` | `CountDownLatch` | `Phaser` |
| --- | --- | --- | --- |
| Mục tiêu chính | Các parties **cùng đến một điểm hẹn** | Chờ một số events/countdown | Đồng bộ nhiều phases, participants linh hoạt |
| Có reusable tự động? | **Có**, sau một vòng thành công | **Không**, count về 0 rồi latch không reset | **Có**, phase tăng theo mỗi advance |
| Số participants | Cố định khi tạo | Count cố định (nhưng thread countdown có thể khác thread chờ) | Có thể register/deregister động |
| Thread đến có phải chờ? | `await()` chờ | `countDown()` **không chờ**; `await()` của observer mới chờ | `arrive()` không chờ; `arriveAndAwaitAdvance()` chờ |
| Hành động ở ranh giới | `barrierAction` | Không có callback nội tại | Có thể override `onAdvance(...)` |
| Failure khi chờ | Interrupted/timeout có thể làm **broken** toàn barrier | Thread đợi bị interrupt không tự “phá latch” | Có protocol termination/interruptible wait riêng |
| Hỗ trợ giảm parties khi worker rời | Không | Không áp dụng theo mô hình party | `arriveAndDeregister()` |
| Có phase number truy cập trực tiếp? | Không | Không | `getPhase()` |
| Dùng khi | Team ổn định, rounds đồng bộ | One-shot start gate hoặc completion latch | Team/phases linh hoạt, công việc phân đợt phức tạp |

### CountDownLatch: một lần, phân biệt thread báo và thread chờ

~~~java
// Snippet minh họa, không phải chương trình độc lập.
CountDownLatch done = new CountDownLatch(3);
worker1: done.countDown();
worker2: done.countDown();
worker3: done.countDown();
coordinator: done.await();
~~~

Các calls `countDown()` **không block**. Một coordinator có thể đợi ba events hoàn tất mà không yêu cầu workers đồng thời chờ cùng mốc. Latch khi đếm về 0 sẽ giữ trạng thái open; không trở lại 3. Muốn một vòng mới phải tạo latch mới.

### Phaser: parties linh hoạt và các phases

Với `Phaser`, threads có thể `register`, `arriveAndAwaitAdvance` và `arriveAndDeregister`. Nó phù hợp hơn nếu một worker cần rời team giữa các phases hoặc số participants thay đổi. `Phaser` không phải drop-in replacement: cần thiết kế registration/deregistration và failure policy riêng.

Ví dụ cấu trúc:

~~~java
// Snippet API, không phải chương trình độc lập.
Phaser phaser = new Phaser(2);
phaser.arriveAndAwaitAdvance();
phaser.arriveAndDeregister();
~~~

Không tự động gọi `reset` trên CyclicBarrier để giả lập `arriveAndDeregister`. `reset` thay generation nhưng **không đổi** `getParties()` và gây `BrokenBarrierException` cho waiters hiện hữu.

## 16. Khi nào barrier không cần thiết?

Không cần CyclicBarrier nếu:

- Tasks độc lập và có thể kết thúc bất kỳ thứ tự nào; caller chỉ cần `invokeAll` hoặc nhiều `Future.get`.
- Một thread đợi N operations trả kết quả một lần; `CountDownLatch` thường rõ hơn.
- Cần producer gửi work liên tục không theo nhịp chung; dùng `BlockingQueue`/completion protocol.
- Cần “nhiều threads cùng bắt đầu một lúc” ở test duy nhất; một start latch cũng có thể phù hợp hơn.
- Team co giãn liên tục; cố định parties khiến việc thêm/bớt worker khó quản lý.
- Chỉ có một thread thực hiện toàn bộ các bước; barrier không mang lại song song.
- Nhiệm vụ sử dụng async callbacks, không có một nhóm blocking workers ổn định; barrier có thể gây starvation nếu mỗi thread vừa chờ vừa giữ scarce executor worker.

Không phải cứ thấy “làm việc theo vòng” là CyclicBarrier luôn tối ưu. Synchronizer nên phản ánh **quan hệ dependency thật** giữa các bước.

## 17. Cần bảo đảm điều gì trước và sau await?

Một checklist cho mỗi worker:

**Trước await:**

- Hoàn thành việc tính cho **phần dữ liệu thuộc quyền sở hữu** trong round.
- Không giữ lock mà barrier action hoặc worker khác cần để đến barrier.
- Không bỏ cuộc âm thầm; nếu lỗi, báo coordinator để cả nhóm biết cần cancel/reset.
- Không cập nhật state mà worker khác vẫn đang đọc trong round nếu chưa có protocol.
- Kiểm tra cancellation/timeout nếu round có deadline.

**Tại await:**

- Gọi **đúng một lần** cho mỗi round đã thiết kế.
- Dùng timed `await` nếu cần deadline; hiểu timeout của một party sẽ ảnh hưởng cả generation.
- Xử lý riêng `InterruptedException`, `TimeoutException`, `BrokenBarrierException`.
- Không nuốt exception rồi tiếp tục round tiếp như thể barrier thành công.

**Sau successful await:**

- Chỉ khi barrier action đã hoàn tất bình thường, đọc state được publication cho vòng mới.
- Không mặc định tất cả workers sẽ tiếp tục đồng thời hoặc theo thứ tự cố định.
- Không dùng `await` return index để suy ra permanent worker ID.
- Chuẩn bị dữ liệu vòng tiếp và tham gia generation sau theo cùng protocol.

Một pattern có xử lý lỗi:

~~~java
// Snippet minh họa trong một worker loop.
try {
    computeRound();
    barrier.await(2, TimeUnit.SECONDS);
    usePublishedStateForNextRound();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    notifyCoordinatorOfFailure(e);
    return;
} catch (TimeoutException | BrokenBarrierException e) {
    notifyCoordinatorOfFailure(e);
    return;
}
~~~

Các method `computeRound`, `usePublishedStateForNextRound`, `notifyCoordinatorOfFailure` là nghiệp vụ giả định; snippet không biên dịch độc lập. Trong production, coordinator phải biết tất cả workers sẽ thoát và xử lý failed round, không chỉ nhận log rồi tiếp tục dùng shared state dở dang.

## 18. Lỗi thường gặp khi dùng CyclicBarrier

| Sai lầm | Hậu quả | Cách tiếp cận |
| --- | --- | --- |
| Đặt parties=4 nhưng chỉ chạy 3 workers | `await` có thể chờ vô hạn | Khớp số parties và active workers |
| Dùng fixed thread pool 2 cho barrier 3 | Worker pool bị starvation deadlock | Đảm bảo capacity đủ hoặc đổi thiết kế |
| Một worker `return` sớm trước await | Nhóm còn lại kẹt | Báo lỗi và cancel/break protocol |
| Gọi `await` khác số lần giữa workers | Lệch generation, ghép sai các rounds | Quy định vòng lặp, membership thống nhất |
| Gọi `reset` sau **mỗi successful round** | Không cần và có thể phá next generation | Để barrier tự tái sử dụng |
| Gọi reset khi còn waiter rồi coi là success | Waiters nhận `BrokenBarrierException` | Recovery coordinator + consistent state |
| Catch `BrokenBarrierException` rồi tiếp tục tính | Có thể dùng partial data của failed round | Thoát/báo lỗi/restart theo protocol |
| Đặt I/O/blocking dài vào barrier action | Cả nhóm chờ action, tăng latency | Action ngắn, rõ và được kiểm soát |
| Nghĩ `arrival index 0` là leader vĩnh viễn | Last arriver thay đổi theo round | Không gắn với worker identity |
| Leader làm việc **sau await** rồi nghĩ nhóm đã đợi | Workers khác có thể chạy trước khi leader xong | Dùng barrier action hoặc barrier thứ hai |
| Nhiều workers `counter++` trước await | Data race/lost update | Slot riêng/atomic/lock, rồi reduce |
| Shared mutable state đọc ở ngoài barrier | Không có JMM visibility tự động cho thread ngoài | Safe publication riêng |
| Coi timeout chỉ ảnh hưởng caller | Barrier generation có thể broken cho mọi waiter | Có group failure policy |
| Không kiểm tra task errors từ Future | Silent failure làm thiếu party | Thu `ExecutionException`, cancel nhóm |
| Barrier action ném exception không xử lý | Generation broken | Theo dõi/cancel/recovery |
| Dùng `getNumberWaiting()` làm gate chính xác | Snapshot thay đổi theo timing | Dùng protocol/latch/timeout khi test |
| Không join workers khi chương trình kết thúc | Main có thể đóng tài nguyên quá sớm | `join`/`Future.get` và shutdown phối hợp |
| Dùng reset để giảm parties | Parties cố định, reset không deregister | `Phaser` khi membership linh hoạt |

## 19. Test và quan sát barrier trong thực tế

### A. Thực hiện test có kiểm soát

Các ví dụ trên cố tình tránh test dựa vào `Thread.sleep(100)` rồi kết luận “đã chờ đủ”. Với dữ liệu thời gian thực tế, scheduling không có deadline hard real-time.

Đối với automated test production:

1. Dùng `CountDownLatch` để xác nhận task đã vào đoạn cần kiểm tra.
2. Dùng `await(timeout, unit)` để test không treo vô hạn.
3. Nếu phải thử trường hợp thiếu party, chuẩn bị timeout và cleanup rõ.
4. Đảm bảo gọi `join`/`Future.get` với timeout thích hợp.
5. Trong `finally`, hủy các tasks còn sống và đóng executor do test sở hữu.
6. Không giả định thứ tự threads; assert **counts, sets và state cuối**.
7. Với exception, assert **đường lỗi chính xác** của các waiter và trạng thái `isBroken`.
8. Không dùng một global/static CyclicBarrier chia sẻ giữa các test cases độc lập.

### B. Metrics đáng theo dõi

- Số rounds **hoàn tất thành công**, không chỉ số vòng được bắt đầu.
- Thời gian workers chờ ở barrier theo từng round.
- Slowest worker trong nhóm, và sự chênh lệch thời gian hoàn thành round.
- Số lần timeout/interruption/broken barrier.
- Barrier action latency và failures.
- Số members đang active, số parties cấu hình.
- Số tasks bị hủy hoặc còn đang đợi sau lỗi.
- Thread pool active/queued counts để phát hiện starvation.
- Tổng thời gian toàn workflow, data validity của mỗi round.
- Lần thành công cuối cùng, không chỉ “worker thread còn sống”.

### C. `getNumberWaiting` và `isBroken` không phải một transaction

`getNumberWaiting()` cho biết số parties đang đợi ở thời điểm gọi, nhưng các threads có thể đến/rời ngay sau đó. `isBroken()` cũng có thể thay đổi khi reset/recovery. Không tạo algorithm dựa vào:

~~~java
// Sai nếu dùng như protocol đồng bộ duy nhất.
if (barrier.getNumberWaiting() == barrier.getParties() - 1) {
    // Cho rằng chắc mình sẽ là người đến cuối.
    barrier.await();
}
~~~

Giữa check và await có thể xuất hiện arrivals, breakage hoặc reset. Nếu thật sự cần xử lý công việc ở điểm cuối, **`barrierAction`** có hợp đồng đúng hơn so với nhìn số waiter rồi tự chọn leader.

## 20. Thiết kế vòng làm việc có lỗi: policy nên quyết định trước

Trong một ứng dụng thật, một round thất bại có thể do data validation, timeout hay interruption. Trước khi dùng barrier, hãy định nghĩa:

- **Bỏ toàn bộ round hay retry?** Nếu chỉ một phần worker đã ghi side effects, retry có idempotent không?
- **State tạm thuộc round nằm ở đâu?** Có thể bỏ buffer `next` và giữ `current` hợp lệ trước đó không?
- **Ai làm coordinator?** Ai quyết định reset/tạo lại barrier và khởi động đội workers?
- **Workers có thể rời nhóm không?** Nếu có, fixed parties dễ thành vấn đề; cân nhắc `Phaser`.
- **Có giới hạn thời gian không?** Timed await giúp phát hiện nhóm không đủ, nhưng chỉ có nghĩa nếu code có xử lý group failure.
- **Ngoại lệ trong worker truyền đi đâu?** Nếu task được submit bằng `ExecutorService.submit`, caller cần `Future.get` để thu `ExecutionException`; log mặc định có thể không biểu thị đầy đủ lỗi nghiệp vụ.
- **Tài nguyên và cleanup?** Dừng/cancel workers trước khi phá shared buffers hoặc đóng connections đang dùng.
- **Giai đoạn tiếp theo cần điều gì?** Có thể publish immutable snapshot, đổi buffer, hoặc báo lỗi đủ rõ để downstream không đọc dữ liệu dở dang.

Một pattern tốt cho iterative numerical state là double buffering: mọi worker đọc `current`, ghi những ô riêng trong `next`; barrier action đổi buffer **chỉ khi round hoàn tất**. Khi lỗi, coordinator giữ `current` của round đã xác nhận và bỏ `next` chưa hoàn thiện thay vì công bố snapshot nửa chừng.

## 21. Câu hỏi tự kiểm tra

**Câu 1.** `CyclicBarrier(3)` có ba threads, mỗi thread gọi `await()` mỗi vòng. Barrier có cần `reset()` sau vòng thành công không?  
**Đáp án:** Không. Nó tự sẵn sàng cho generation sau khi ba parties đã đến và action (nếu có) hoàn tất.

**Câu 2.** `await()` có trả về giá trị không?  
**Đáp án:** Có, arrival index từ `parties-1` tới `0`. `0` là người đến cuối.

**Câu 3.** Một thread là index 0 ở round 1. Có chắc cũng là index 0 ở round 2 không?  
**Đáp án:** Không. Last arrival thay đổi theo scheduling và workload.

**Câu 4.** Barrier action chạy trên một thread riêng do JDK tạo ra?  
**Đáp án:** Không. Nó chạy trong thread đến cuối, trước khi các waiters được giải phóng.

**Câu 5.** Barrier action cập nhật một shared buffer; các worker sau successful await có được nhìn thấy thay đổi đó theo JMM không?  
**Đáp án:** Có, theo bảo đảm happens-before của barrier. Workers ngoài protocol vẫn cần đồng bộ phù hợp.

**Câu 6.** Ba workers cùng `counter++` trước await có an toàn vì barrier sau đó không?  
**Đáp án:** Không. Race trên `counter++` xảy ra trước barrier; cần atomic/lock hoặc mỗi worker ghi slot riêng.

**Câu 7.** `await(100, MILLISECONDS)` của một thread timeout trong lúc những threads khác đang đợi. Chỉ thread ấy gặp lỗi?  
**Đáp án:** Không. Barrier có thể broken; các waiters khác nhận `BrokenBarrierException`.

**Câu 8.** `barrier.reset()` có đổi số parties từ 4 thành 3 không?  
**Đáp án:** Không. Parties cố định; reset tạo generation mới và làm các waiters hiện tại nhận broken.

**Câu 9.** `ExecutorService.newFixedThreadPool(2)` chạy ba tasks, tất cả `await` trên barrier(3). Điều gì nguy hiểm?  
**Đáp án:** Hai tasks chạy và chờ; task thứ ba xếp hàng không có worker, gây thread-pool starvation deadlock.

**Câu 10.** Một worker ném exception trước await và tự return; hai workers khác chờ barrier(3). Có tự mở không?  
**Đáp án:** Không. Cần failure/cancellation protocol hoặc timeout; thiếu party có thể chờ vô hạn.

**Câu 11.** `barrierAction` ném `IllegalStateException`. Những waiter khác có tiếp tục như round thành công không?  
**Đáp án:** Không. Generation bị broken; last-arriving thread nhận exception, các waiters khác nhận `BrokenBarrierException`.

**Câu 12.** `CountDownLatch` và `CyclicBarrier` khác nhau điểm quan trọng nhất?  
**Đáp án:** Latch là countdown một lần, người `countDown` không chờ; CyclicBarrier là rendezvous của một nhóm cố định và tái sử dụng qua rounds.

**Câu 13.** Muốn worker có thể `arriveAndDeregister` khi hết việc, nên dùng gì?  
**Đáp án:** Cân nhắc `Phaser`; CyclicBarrier không có API deregister.

**Câu 14.** Một thread sau `await` có thể bắt đầu vòng kế trước khi thread khác hoàn tất logic sau `await` không?  
**Đáp án:** Có. Sau khi barrier mở, các workers tiếp tục đồng thời; chỉ logic **trước barrier** và barrier action được mốc ấy đồng bộ.

**Câu 15.** Có nên dùng `getNumberWaiting()` như một condition gate chính xác để quyết định leader của round không?  
**Đáp án:** Không. Đó là snapshot chịu concurrency; barrier action và arrival index theo hợp đồng phù hợp hơn.

## Tài liệu tham khảo Java SE 17

- [`CyclicBarrier` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CyclicBarrier.html): reusable barrier, barrier action, arrival index, all-or-none breakage, memory consistency.
- [`BrokenBarrierException` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BrokenBarrierException.html): lỗi khi barrier bị phá hoặc reset.
- [`CountDownLatch` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): đồng bộ một lần, không reusable.
- [`Phaser` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Phaser.html): đăng ký/rời parties, phases và advance.
- [`ExecutorService` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): pool workers, submit, Future và shutdown.
- [`Future` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): kết quả task, cancellation và exception.
- [`TimeUnit` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/TimeUnit.html): đơn vị thời gian khi dùng timed await.
- [Java Language Specification `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): happens-before và visibility.

**Ghi nhớ:** `CyclicBarrier` giúp **một nhóm worker cố định cùng hoàn thành từng vòng**, có thể lặp lại mà không reset sau mỗi vòng. Đặt barrier action tại ranh giới khi cần hợp nhất/đổi state, kiểm tra parties và executor capacity, rồi **xử lý timeout/interruption/broken barrier như thất bại của cả round** chứ không lặng lẽ cho các worker tiếp tục với dữ liệu chưa hoàn thiện.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronizers-003">Phaser cho số lượng participant linh hoạt</a>

<details>
<summary>Xem chi tiết</summary>

# Phaser cho số lượng participant linh hoạt trong Java Concurrency

> **Bối cảnh:** `java.util.concurrent.Phaser` là synchronizer của Java SE cho các công việc trải qua **nhiều giai đoạn (phases)**, trong đó **số lượng thread/task tham gia có thể thay đổi** bằng `register()`, `bulkRegister()` và `arriveAndDeregister()`. Bài thuộc **Synchronizers**, chỉ bàn Java concurrency nói chung; nội dung đặc thù về Spring được đặt ở chương khác.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Hiểu khái niệm **phase**, **registered party**, **arrived/unarrived parties** và **phase advance**.
- Giải thích lý do một barrier có số lượng participant cố định không phải lúc nào cũng phù hợp khi tasks ra/vào ở runtime.
- Chọn đúng `register()`, `bulkRegister(n)`, `arrive()`, `arriveAndAwaitAdvance()`, `arriveAndDeregister()`, `awaitAdvance(phase)` và `awaitAdvanceInterruptibly(...)`.
- Phân biệt **đánh dấu đã tới barrier** với **chờ barrier mở**, và phân biệt **deregister** với chỉ rời method hoặc kết thúc thread.
- Thiết kế protocol đăng ký trước khi cho task bắt đầu, deregister chính xác một lần, có coordinator và tránh phase bị treo.
- Giải thích **đăng ký participant trong lúc phase đang diễn ra** có thể ảnh hưởng phase hiện tại, không mặc nhiên là “đăng ký cho vòng kế tiếp”.
- Sử dụng `onAdvance` để kiểm soát chính sách kết thúc, đồng thời nhận biết mặc định Phaser kết thúc khi số registered parties về 0.
- Hiểu cách exception, interruption, timeout và `forceTermination()` ảnh hưởng liveness.
- Viết và chạy chương trình Java 17 có **hai participants ban đầu, một participant tham gia sau phase 0, rồi những participants lần lượt deregister**.
- Phân tích giới hạn của Phaser: không tự quản lý thread, không tự rollback side effects, không thay thế event queue hoặc transaction.

**Tư tưởng chính:** Phaser không “đếm thread đang sống”; nó quản lý **những bên đã đăng ký (parties)** cùng **lượt arrival của họ trong từng phase**. Phase chỉ tiến khi tất cả parties còn đăng ký cần tới phase ấy đã arrive hoặc deregister theo protocol.

## 2. Vấn đề: số lượng participants có thể thay đổi

Giả sử một chương trình xử lý dữ liệu qua ba giai đoạn:

1. Các workers đọc và parse input.
2. Một số workers đi tiếp để transform dữ liệu; một số rút vì hết việc.
3. Một worker mới được thêm để validate hoặc aggregate, rồi tất cả cùng chờ giai đoạn kết thúc.

Nếu có đúng 3 workers từ đầu đến cuối, một barrier với party count cố định có thể đủ. Nhưng ở pipeline động:

- Có worker chỉ tham gia những phase đầu.
- Có worker được khởi tạo sau phase đầu.
- Có phase cần nhiều workers hơn phase trước.
- Có worker hoàn tất công việc riêng và **phải rút** để không ngăn những phase còn lại.

`Phaser` hỗ trợ **dynamic registration và deregistration**. Đây là khác biệt quan trọng so với `CyclicBarrier`, nơi số parties được cung cấp từ constructor và không được tăng/giảm trực tiếp bằng một cặp `register/deregister`.

### Hình dung một pha

```text
PHASE = 3, registered = 4, unarrived = 4

Worker A: làm bước của phase 3 ---> arrive ----\
Worker B: làm bước của phase 3 ---> arrive -----+--> advance sang phase 4
Worker C: làm bước của phase 3 ---> arrive -----|
Coordinator: chuẩn bị barrier ------> arrive ----/

Nếu Worker C không còn tham gia phase sau:
Worker C: arriveAndDeregister() thay vì arrive()
=> tính arrival cho phase hiện tại VÀ giảm registered parties cho các phase sau
```

Bản chất của mô hình không phải “bốn callbacks cần chạy” mà là **bốn parties đã đăng ký có trách nhiệm arrive**. Một party đã registered nhưng quên báo arrive có thể khiến phase chờ vô hạn, bất kể CPU rảnh hay số task khác đã xử lý xong.

## 3. Đọc các khái niệm cơ bản

| Thuật ngữ | Ý nghĩa | API quan sát |
|---|---|---|
| **Phase** | Chỉ số giai đoạn hiện tại; thông thường bắt đầu từ 0 | `getPhase()` |
| **Registered parties** | Số registrations hiện tham gia vào Phaser | `getRegisteredParties()` |
| **Unarrived parties** | Số registrations chưa báo arrival ở phase hiện tại | `getUnarrivedParties()` |
| **Arrived parties** | Số registrations đã báo arrival ở phase hiện tại | `getArrivedParties()` |
| **Arrival** | Tín hiệu một party đã hoàn thành phần việc của phase | `arrive()` |
| **Advance** | Chuyển sang phase kế tiếp sau khi đủ arrivals | `arriveAndAwaitAdvance()`, arrival cuối |
| **Deregistration** | Arrival của phase hiện tại và rút một registration cho về sau | `arriveAndDeregister()` |
| **Termination** | Phaser không còn vận hành như barrier bình thường | `isTerminated()`, `forceTermination()`, `onAdvance` |

Một registration **không được tự động gắn với một `Thread` cụ thể**. Phaser không ghi nhận một Java thread ID và bắt buộc chính thread đó phải arrive. Đó là trách nhiệm của giao thức ứng dụng. Bạn có thể đăng ký trong coordinator và yêu cầu worker báo arrival, nhưng phải chắc chắn mỗi registration có đúng một arrival/deregistration cho từng phase cần thiết.

### Constructor

```java
Phaser phaser = new Phaser();    // Không có party đăng ký ban đầu.
Phaser withMain = new Phaser(1); // Main/coordinator là một party.
Phaser fixedInitially = new Phaser(3);
```

Đây là đoạn code trong một method với `import java.util.concurrent.Phaser;`. Có thể bắt đầu từ 0 và đăng ký sau, nhưng nhiều workflow dễ kiểm soát hơn nếu coordinator **giữ một registration** để phase không tự advance khi còn đang thêm participants.

**Lưu ý:** mặc định `Phaser.onAdvance` trả true khi `registeredParties == 0`, dẫn đến termination khi phase advance và không còn parties. Một Phaser chưa có party đăng ký ngay khi vừa tạo không đồng nghĩa mọi method của nó sẽ tự mang trạng thái terminated theo mọi lộ trình; không suy đoán từ `getRegisteredParties()==0` mà bỏ qua lifecycle contract. Để thấy termination chính xác, dùng `isTerminated()`.

## 4. Cấu trúc phase advance

Một phase được mở khi các registrations đang áp dụng cho phase đó **đều báo arrive hoặc deregister**. Một party báo tới barrier là một arrival.

Ví dụ coordinator + hai workers, tất cả đã registered:

```text
Phase 0: registered = 3
  A.arrive()       -> unarrived 3 -> 2
  B.arrive()       -> unarrived 2 -> 1
  Main.arrive()    -> unarrived 1 -> 0
                     advance: phase 0 -> phase 1
Phase 1: các bên còn registered có obligation arrival mới
```

Khi phase tiến, unarrived count được thiết lập lại theo số registered parties của phase mới, ngoại trừ termination policy có thể khiến Phaser ngừng.

**Không lấy `getArrivedParties()` rồi cộng số thủ công để điều phối**. Các getters trả **snapshot có thể thay đổi ngay lập tức**; caller khác có thể arrive, deregister hoặc register bất kỳ lúc nào theo giao thức của chương trình.

## 5. Các API quan trọng và hành vi

### `register()`

```java
int phaseAtRegistration = phaser.register();
```

Đăng ký thêm một party. Giá trị trả về là phase mà registration áp dụng theo contract; nếu phaser đã terminated, phương thức có thể trả chỉ số phase âm phù hợp với trạng thái termination. **Đăng ký không tạo thread hoặc khởi chạy task**.

Quan trọng: khi Phaser còn active, registration có thể tham gia **phase hiện tại**. Nếu register trong lúc những parties cũ đang chờ phase advance, participant mới có nghĩa vụ arrive để phase hoàn tất.

### `bulkRegister(int parties)`

```java
phaser.bulkRegister(3);
```

Tăng số registrations một lần, hữu ích khi biết cần N workers tham gia cùng phase. `parties` không âm; nếu `parties == 0`, method không thêm registration. Đăng ký quá giới hạn nội bộ có thể gây `IllegalStateException`.

Nếu cần đăng ký cả một batch workers **trước khi submit tasks**, `bulkRegister` thể hiện rõ invariant. Tránh mở cho workers thực thi khi chưa đăng ký đầy đủ nghĩa vụ của họ.

### `arrive()`

```java
int arrivedAtPhase = phaser.arrive();
```

Đánh dấu một registration đã tới barrier của phase hiện tại **nhưng không chờ** phase tiến. Phương thức trả về phase khi arrival được ghi nhận (không nhất thiết là phase sau đó).

Một thread có thể dùng `arrive` rồi làm việc khác, nhưng nếu cần chờ, nên dùng `awaitAdvance(arrivedAtPhase)`. **Không gọi `arrive` hai lần cho cùng registration trong cùng một phase**; có thể gây `IllegalStateException` hoặc làm lệch protocol.

### `arriveAndAwaitAdvance()`

```java
int nextPhase = phaser.arriveAndAwaitAdvance();
```

Vừa arrive, vừa chờ toàn bộ parties cần thiết của phase hoàn thành. Khi hoạt động bình thường, return phase sau advance (ví dụ 1 nếu rời phase 0). Đây là phương thức **không đáp ứng interruption theo cách ném InterruptedException**; nếu cần timeout hoặc interruptibility, xem `awaitAdvanceInterruptibly` kết hợp `arrive`.

### `arriveAndDeregister()`

```java
int oldPhase = phaser.arriveAndDeregister();
```

Báo hoàn tất phase hiện tại **và rút registration**. Đây là điều worker nên làm ở phase cuối cùng nó tham gia. Nó **không chờ** các parties còn lại, và giá trị trả về liên quan phase lúc deregister, không phải giấy chứng nhận mọi task khác đã hoàn tất.

Nếu vừa cần rút vừa cần đợi phase mở, có thể thực hiện `int p = phaser.arriveAndDeregister(); phaser.awaitAdvance(p);` trong protocol phù hợp; sau deregister thread trở thành **observer**, không còn party cần arrive.

### `awaitAdvance(int phase)`

```java
int advanced = phaser.awaitAdvance(phase);
```

Chờ đến khi current phase khác giá trị `phase` được truyền. API này **không tự arrive** và **không yêu cầu thread gọi phải là party**. Vì vậy hữu ích cho observer hoặc sau khi đã `arrive`.

Lưu ý: nếu truyền một phase đã cũ, method có thể return ngay; nếu truyền phase hiện tại nhưng không có bên nào báo arrive, có thể chờ mãi. Không nhầm nó với `arriveAndAwaitAdvance`.

### `awaitAdvanceInterruptibly(int phase, ...)`

Có hai overload:

```java
phaser.awaitAdvanceInterruptibly(phase);
phaser.awaitAdvanceInterruptibly(phase, 500, TimeUnit.MILLISECONDS);
```

Đây là code minh họa bên trong method, cần import `TimeUnit` và khai báo/xử lý `InterruptedException`; bản có timeout còn có thể ném `TimeoutException`.

**Điểm tinh tế:** gọi `awaitAdvanceInterruptibly` **không tự arrive hoặc deregister**. Thông thường party làm `int p = phaser.arrive();` trước, sau đó chờ interruptibly. Nếu thời hạn chờ hết hoặc thread bị interrupt, party đã arrived ở phase ấy vẫn registered cho phase tiếp theo. Phải có shutdown/cancellation protocol riêng để tránh mất registration hoặc workers bỏ đi trong khi phase mới cần họ.

### `forceTermination()`

Dùng để đưa Phaser vào trạng thái terminated, giải phóng những threads đang đợi advance theo contract. Nó **không hủy/interrupt tác vụ đang tính toán**, không tự rollback hay gọi `ExecutorService.shutdownNow()`.

Sau termination, các method về phase có thể trả phase âm để báo trạng thái; application phải kiểm tra và thoát, thay vì tiếp tục loop như thể phase vẫn tăng bình thường.

## 6. Điều làm Phaser linh hoạt: parties vào và ra giữa các phase

Giả sử hai workers A/B cùng coordinator tham gia phase 0. Sau phase 0:

- A và B còn một bước cuối ở phase 1 rồi sẽ rút.
- Coordinator đăng ký thêm C trước khi đóng phase 1.
- C chỉ làm phần việc của phase 1 và rút.
- Coordinator đến barrier phase 1, đợi đủ A/B/C.
- Sau phase 1, chỉ còn coordinator registered.
- Coordinator rút, dẫn tới termination mặc định.

```text
Phase 0:
  Main registered + A + B       = 3 parties
  A, B, Main all arrive         -> phase 1

Phase 1:
  Main + A + B, register C      = 4 parties
  A, B, C arriveAndDeregister  -> chỉ còn Main
  Main arriveAndAwaitAdvance   -> phase 2

After phase 1:
  Main only                     = 1 registered
  Main arriveAndDeregister      -> no parties; terminated
```

Đây là mô hình cho chương trình Java 17 dưới đây. **Số registrations đang được báo giữa các bước là trạng thái logic tại mốc được mô tả**, không khẳng định mọi getter lấy đồng thời đều có thể chụp một snapshot nguyên tử của toàn bộ hệ thống.

### “Đăng ký giữa phase” có nghĩa gì?

Nếu coordinator gọi `phaser.register()` khi phase 1 **đã mở và chưa advance**, C phải có một arrival cho phase 1. Nó không tự được “dời sang phase 2” chỉ vì C là người mới.

Với ứng dụng cần participant tham gia **chỉ từ phase kế tiếp**, tốt nhất dùng một coordination protocol: coordinator giữ registration, chờ phase hiện tại advance xong, sau đó register newcomers **trước khi coordinator cho phase kế tiếp hoàn tất**. Phải tránh race với những workers có thể đã chạy đến arrival của phase mới.

## 7. Chương trình Java 17 chạy được

Chương trình minh họa **dynamic registration/deregistration** với ba worker tasks và main coordinator. Những worker đầu tham gia phase 0, rồi phase 1; worker mới chỉ tham gia phase 1. `AtomicInteger` đếm công việc trong từng phase; `Future.get()` bảo đảm main đã đợi các tasks xong trước khi in counters.

Tạo file **`DynamicPhaserDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DynamicPhaserDemo {
    public static void main(String[] args) throws Exception {
        Phaser phaser = new Phaser(1); // Main owns one registration.
        ExecutorService pool = Executors.newFixedThreadPool(3);
        AtomicInteger phase0Work = new AtomicInteger();
        AtomicInteger phase1Work = new AtomicInteger();
        List<Future<?>> tasks = new ArrayList<>();

        try {
            phaser.bulkRegister(2);
            for (int i = 0; i < 2; i++) {
                tasks.add(pool.submit(() -> {
                    phase0Work.incrementAndGet();
                    phaser.arriveAndAwaitAdvance(); // End of phase 0
                    phase1Work.incrementAndGet();
                    phaser.arriveAndDeregister(); // Exit during phase 1
                }));
            }
            int afterFirstBarrier = phaser.arriveAndAwaitAdvance();
            System.out.println("afterPhase0=" + afterFirstBarrier);

            phaser.register(); // Join before main closes phase 1.
            tasks.add(pool.submit(() -> {
                phase1Work.incrementAndGet();
                phaser.arriveAndDeregister(); // Only participates in phase 1
            }));

            int afterSecondBarrier = phaser.arriveAndAwaitAdvance();
            for (Future<?> task : tasks) task.get();
            System.out.println("afterPhase1=" + afterSecondBarrier);
            System.out.println("phase0Workers=" + phase0Work.get());
            System.out.println("phase1Workers=" + phase1Work.get());
            System.out.println("remainingParties=" + phaser.getRegisteredParties());

            phaser.arriveAndDeregister(); // Main exits, zero registrations.
            System.out.println("terminated=" + phaser.isTerminated());
        } finally {
            phaser.forceTermination();
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool did not stop");
            }
        }
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 DynamicPhaserDemo.java
java DynamicPhaserDemo
```

**Stdout đã được biên dịch/chạy xác minh bằng Java 17:**

```text
afterPhase0=1
afterPhase1=2
phase0Workers=2
phase1Workers=3
remainingParties=1
terminated=true
```

### Giải thích từng đoạn

**`new Phaser(1)`:** main giữ một registration. Khi hai workers còn chưa đến barrier, main có thể gọi `arriveAndAwaitAdvance` để chờ. Khi workers đã tới phase 0, phase mới mở.

**`bulkRegister(2)` trước khi submit:** hai nghĩa vụ arrival được đăng ký **trước** khi bất kỳ worker task nào có thể chạy. Đây là cách tránh lỗi worker chạy đến arrival khi chưa được tính, hoặc coordinator advance trong lúc đang còn submit workers.

**`arriveAndAwaitAdvance()` trong hai workers:** mỗi worker hoàn thành công việc phase 0 rồi chờ toàn bộ participants (A, B, main) cùng tới barrier. Sau khi phase 0 advance, A/B làm phase 1.

**`phaser.register()` sau barrier đầu:** main đăng ký thêm C khi phase 1 đã bắt đầu nhưng main vẫn giữ một nghĩa vụ arrival, nên phase 1 chưa thể advance trước khi C được đăng ký.

**`arriveAndDeregister()` ở A/B/C:** họ hoàn thành phase 1 rồi rút khỏi các phase sau. Đó là lý do sau phase 1 chỉ còn main.

**`Future.get()`:** main chờ tất cả worker task hoàn thành. Nhờ đó counters ở output không phụ thuộc worker scheduling dù các tác vụ chạy đồng thời.

**`main.arriveAndDeregister()`:** sau khi mọi workers đã rút, main rút cuối cùng. Với default `onAdvance`, khi registered parties về 0, Phaser terminated; `isTerminated()` true.

**`finally`:** `forceTermination()` và `shutdownNow()` là cleanup cho demo ngay cả khi main gặp lỗi. Đây là chiến lược fail-fast cho bài ngắn; production nên chọn graceful shutdown, timeout và cancellation rõ, không coi forceTermination là cách “tự dọn mọi task”.

## 8. Tại sao chương trình trên có kết quả xác định?

Không có dòng print nào phụ thuộc vào thứ tự worker A hoặc B được scheduler chạy trước:

- `afterPhase0=1` đến từ ranh giới phase 0 của main, đảm bảo các obligations ban đầu đã arrived.
- Hai workers A/B mỗi worker increment `phase0Work` một lần trước arrival; `phase0Workers=2` sau khi đã await phase và Future completion.
- Trong phase 1, A/B mỗi người increment một lần, C cũng increment một lần. `phase1Workers=3` sau khi cả ba Future hoàn tất.
- A/B/C đều deregister, nên `remainingParties=1` chỉ còn main.
- Main rút party cuối khiến `terminated=true` theo termination policy mặc định.
- `AtomicInteger` giúp increments an toàn nếu dùng cùng một biến từ nhiều threads.
- `Future.get()` bảo đảm exception từ worker không bị nuốt và cung cấp memory visibility cho main đối với công việc đã hoàn thành.

Nếu thay `AtomicInteger` bằng `int counter++` từ nhiều workers, nguy cơ lost update có thể xuất hiện bất kể Phaser barriers có đúng, vì nhiều workers vẫn có thể tăng counter **cùng lúc trong một phase**.

## 9. Vì sao không dùng Thread.sleep để chờ phase?

Một cách sai:

```java
// Không dùng sleep làm protocol đồng bộ.
Thread.sleep(200);
System.out.println(phaser.getPhase());
```

Đây là snippet minh họa trong method `throws InterruptedException`. Ngủ 200 ms không đảm bảo workers đã hoàn tất, không làm nghĩa vụ arrival biến mất, và không cung cấp ranh giới happens-before thích hợp.

Thay vào đó:

- Party có công việc và cần đợi: `arriveAndAwaitAdvance()`.
- Party đã arrive rồi cần chờ với timeout: `awaitAdvanceInterruptibly(phase, timeout, unit)`.
- Observer không phải party chỉ cần biết phase thay đổi: `awaitAdvance(phase)` hoặc bản interruptible.
- Caller cần biết **task đã hoàn tất thật sự** (không chỉ đến barrier): `Future.get()`/`join()` với policy timeout.

Thời gian chỉ nên dùng cho timeout/deadline và mô phỏng workload trong test, không làm synchronization contract.

## 10. Khác biệt giữa arrive(), awaitAdvance() và arriveAndAwaitAdvance()

Ba lời gọi có quan hệ nhưng không tương đương:

```java
int phase = phaser.arrive();
doIndependentWork();
phaser.awaitAdvance(phase);
```

Đây là đoạn code minh họa trong method. Nó tương đương về ý tưởng “arrive rồi chờ”, nhưng có khoảng thời gian chạy công việc khác giữa hai lời gọi. Thread đã **đánh dấu arrived**, không có quyền arrive lần nữa trong cùng phase chỉ vì đã làm thêm việc.

Ngược lại:

```java
int nextPhase = phaser.arriveAndAwaitAdvance();
```

Vừa arrive vừa chờ barrier ở đúng một operation. Nếu không có công việc nào cần làm trong khoảng chờ, thường dễ hiểu hơn.

**Nguy cơ:** nếu thread gọi `awaitAdvance(currentPhase)` nhưng chưa arrive cho registration của mình và không có ai khác có thể thực hiện arrival thay nó, phase không thể advance. Đây là deadlock logic: nó đang đợi **chính arrival mà nó còn nợ**.

## 11. Đăng ký trước khi khởi chạy task: tránh “arrival without registration”

Nếu khởi chạy task trước:

```java
// Sai về thứ tự responsibility registration:
pool.submit(() -> {
    doWork();
    phaser.arriveAndDeregister();
});
phaser.register(); // Có thể xảy ra quá muộn.
```

Đây là snippet minh họa nguy hiểm, các biến/method được định nghĩa bên ngoài.

Một worker có thể chạy đến `arriveAndDeregister()` **trước khi main đăng ký nó**. Tùy state hiện tại, lời arrive có thể làm sai obligation của party khác hoặc ném lỗi; ý định “task này có một registration riêng” không được bảo vệ.

Cách đúng:

```java
phaser.register();
Future<?> task = pool.submit(() -> {
    try {
        doWork();
    } finally {
        phaser.arriveAndDeregister();
    }
});
```

Đây là đoạn code minh họa trong method với `pool` là `ExecutorService`, import `Future`; khi task chạy, registration đã tồn tại. **Nhưng đoạn này cần được bổ sung error policy:** nếu `submit` bị reject hoặc task **chưa từng bắt đầu** do pool shutdown, registration đã được thêm nhưng không có worker deregister; coordinator cần rollback registration theo protocol an toàn. Nếu task ném lỗi, finally deregister để tránh giữ barrier, nhưng không được coi task nghiệp vụ thành công chỉ vì barrier được mở.

### Đăng ký trong task hay coordinator?

Có thể đăng ký từ worker, nhưng nếu coordinator muốn chờ “tất cả N tasks mới submit” thì đăng ký khi worker bắt đầu có race: coordinator có thể tới barrier trước khi worker đăng ký. Một coordinator giữ một party và **bulkRegister(N) trước submit** giúp rõ ràng hơn.

Mọi đường như rejection, cancellation trước khi task chạy và pool failure đều phải có kế hoạch rút các registrations **chưa được worker nhận trách nhiệm**.

## 12. Đăng ký muộn và race khi phase đang tiến

Một lỗi khác:

```java
// Main không giữ phase mở, workers có thể advance sớm.
Phaser phaser = new Phaser(2);
startTwoWorkers(phaser);
// Sau một khoảng thời gian:
phaser.register();  // Không có nghĩa chắc chắn join phase mong muốn.
```

Đây là snippet minh họa, `startTwoWorkers` được định nghĩa bên ngoài. Nếu hai workers đã arrive và phase advance, registration mới có thể gắn với phase **khác** so với điều bạn kỳ vọng.

Một thiết kế tốt:

- Tạo **registration của coordinator** để chặn phase advance cho đến khi setup hoàn tất.
- Định nghĩa rõ “join current phase” hay “join next phase”.
- Đặt phase boundary và registration trong một **protocol có owner**; tránh nhiều components tự register tùy lúc.
- Trước khi báo “phase finished”, cần kiểm tra **không còn pending registration/submission nào** theo logic của caller, không chỉ nhìn current unarrived snapshot.

Không có một API “registerForExactlyPhaseNAtFutureTime” với cùng semantics như một task scheduler. Nếu cần join theo kế hoạch phức tạp, dùng coordinator quản lý khi nào registration xảy ra.

## 13. arriveAndDeregister(): rời giai đoạn mà không chặn các vòng sau

Một party chỉ cần tham gia hai phase nhưng coordinator sẽ còn chạy ba phase. Sau lần cuối, nó nên:

```java
// Phase cuối worker còn tham gia:
doLastStage();
phaser.arriveAndDeregister();
```

Đây là snippet minh họa trong method.

Nếu nó chỉ gọi `arriveAndAwaitAdvance()` ở phase cuối rồi return khỏi task **mà không deregister**, phaser vẫn coi registration tồn tại ở phase tiếp theo. Coordinator có thể bị treo vì đợi một party đã không còn code để arrive.

**Cần phân biệt:**

- `arrive()`: vẫn registered, còn obligation ở phase sau.
- `arriveAndAwaitAdvance()`: vẫn registered, đã arrive và đợi phase mới.
- `arriveAndDeregister()`: rút party cho các phase sau, không chờ phase hiện tại.
- `forceTermination()`: toàn bộ Phaser kết thúc, không phải “rút một party”.

### Nếu worker lỗi trước barrier

Một pattern:

```java
try {
    processStage();
} catch (RuntimeException e) {
    recordFailure(e);
    throw e;
} finally {
    phaser.arriveAndDeregister();
}
```

Đây là snippet minh họa, `recordFailure`, `processStage`, `phaser` ở ngữ cảnh ngoài.

Nó tránh một registration bị bỏ rơi khi task **đã bắt đầu** và vẫn có khả năng hoàn thành finally. Tuy nhiên, đây là lựa chọn policy: việc deregister có thể cho phép những parties khác **advance mặc dù stage không thành công**. Nếu stages phụ thuộc output của worker lỗi, cần chia sẻ failure state và fail-fast/termination protocol, không chỉ deregister và giả vờ thành công.

**Không gọi `arriveAndDeregister()` hai lần trên cùng logical registration.** Trách nhiệm deregister phải thuộc một đường rõ ràng, chẳng hạn owner task sau khi đã chạy, hoặc coordinator trong đường reject *trước khi worker bắt đầu*.

## 14. Interruption: Phaser không tự loại party bị interrupt

`arriveAndAwaitAdvance()` và `awaitAdvance()` là các phương thức **chờ không thể ngắt bằng checked InterruptedException theo contract**. Nếu application cần chờ đáp ứng interrupt, dùng:

```java
int phase = phaser.arrive();
try {
    phaser.awaitAdvanceInterruptibly(
            phase, 1, TimeUnit.SECONDS);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    handleInterruptedWait(e);
} catch (TimeoutException e) {
    handlePhaseTimeout(e);
}
```

Đây là snippet minh họa trong method; cần imports `TimeUnit`, `TimeoutException` và các handler method do application định nghĩa.

Điểm dễ sai: sau `arrive()`, party **vẫn registered**; timeout/interrupt của lời **chờ** không tự hủy arrival hoặc rút registration. Nếu worker bỏ task vĩnh viễn, coordinator phải quyết định:

- Terminate Phaser vì phase không còn an toàn.
- Dùng một giao thức deregistration phù hợp khi phase đổi, tránh arrive hai lần cùng phase.
- Chuyển trách nhiệm arrival ở phase sau cho một worker khác trong giao thức có kiểm soát.
- Ghi nhận failure và đảm bảo các parties còn lại không chờ vô hạn.

**Không tự gọi `arriveAndDeregister()` ngay sau `arrive()` trong cùng phase chỉ vì timed await thất bại.** Điều này sẽ là **một arrival thứ hai** cho cùng registration/phase, có thể làm lệch đếm hoặc ném `IllegalStateException`. Cần controller/termination protocol thay vì “sửa” bằng cách arrive thêm.

### Timeout khác termination

`awaitAdvanceInterruptibly(phase, timeout, unit)` ném `TimeoutException` chỉ nói thread chưa thấy phase advance trong thời hạn. Nó **không tự forceTermination**, và những parties khác có thể đang tiến triển hợp lệ hoặc thực sự đã kẹt.

`forceTermination()` là một hành động riêng, có tác động đến toàn bộ Phaser, không phải một timeout cục bộ vô hại. Chỉ owner/coordinator nên quyết định nếu contract cho phép.

## 15. Phase termination và onAdvance()

Theo mặc định, khi phase advance với **registered parties bằng 0**, Phaser trở thành terminated. Muốn xác định chính sách khác, có thể subclass và override `protected boolean onAdvance(int phase, int registeredParties)`.

Ví dụ chỉ cho chạy tối đa ba phase:

```java
import java.util.concurrent.Phaser;

public final class ThreeRoundsPhaser extends Phaser {
    public ThreeRoundsPhaser(int parties) {
        super(parties);
    }

    @Override
    protected boolean onAdvance(int phase, int registeredParties) {
        return phase >= 2 || registeredParties == 0;
    }
}
```

Đây là một **class Java độc lập, có thể biên dịch** trong file `ThreeRoundsPhaser.java`; nó không có `main`. `onAdvance` nhận phase vừa hoàn tất và số registered parties hiện tại. Với chuỗi phase bắt đầu 0, callback khi vừa kết thúc phase 2 trả true (hoặc trả true sớm hơn nếu không còn parties), nên Phaser sẽ terminate theo policy.

### Những chú ý về onAdvance

- Callback có thể được gọi bởi thread thực hiện **arrival cuối khiến phase tiến**; không được mặc định main thread là callback owner.
- Callback nên **nhanh và không block**. Nếu nó làm I/O dài hoặc đợi một party cần phase advance để tiến triển, hệ thống có thể kẹt.
- Return `true` là yêu cầu terminate, không phải “advance tiếp”.
- Return `false` cho phép phase mới bắt đầu, nếu đủ điều kiện.
- Đừng mutate registry của Phaser theo cách tạo self-dependency trong callback.
- Nếu override và luôn return false, cần **một chính sách terminate khác**, nhất là khi registered count về 0; không mặc định mỗi subclass vẫn dùng hành vi termination mặc định.

### Termination và giá trị phase âm

Sau termination, một số method như `getPhase()` có thể trả phase âm để báo trạng thái. Application loop cần kiểm tra `phaser.isTerminated()` hoặc phase âm sau wait để không tiếp tục làm việc không còn ý nghĩa.

Không so sánh phase tăng vô hạn với integer tràn mà không có chính sách lifecycle. Theo API, phase number có semantics riêng khi terminated và wraparound trong thời gian rất dài; ứng dụng thông thường nên dùng số giai đoạn hữu hạn hoặc kiểm soát vòng đời rõ.

## 16. Coordinator và worker: ai là owner của party?

Bản thân Phaser không gắn registration với thread ID. Vì thế nếu bạn có 1.000 jobs nhưng chỉ tạo phaser với 3 parties, có thể đang mô hình hóa **ba worker loops** chứ không phải 1.000 jobs. Điều này hợp lệ nếu mỗi worker có trách nhiệm arrive mỗi phase; nhưng nếu business muốn chờ **tất cả 1.000 jobs**, số party/registration phải phản ánh đúng obligations.

Có ba pattern thường gặp:

**Mỗi worker là một party:** phù hợp khi một worker cố định hoặc tạm thời tham gia mọi phase, deregister khi không còn việc.

**Mỗi job là một party:** coordinator register trước khi submit từng job, job hoàn thành stage gọi arrive/deregister. Cần làm rõ một job có kéo dài nhiều phases hay chỉ thuộc một phase.

**Một coordinator là party “giữ cửa”:** main có registration để không cho phase advance trước khi đăng ký xong những participants của một batch. Coordinator không cần làm work nặng, nhưng phải arrive chính xác khi quá trình đăng ký và chuẩn bị kết thúc.

Không dùng `getRegisteredParties()` như số thread sống; không dùng `Thread.activeCount()` làm party count; không xem `ExecutorService.getPoolSize()` là số nghĩa vụ arrival.

## 17. Registration và lỗi submit/rejection

Một pipeline thực tế có thể:

1. `phaser.register()` cho một task.
2. Gọi `executor.submit(task)`.
3. Task bắt đầu, chạy, đến barrier, deregister.

Nhưng nếu bước 2 ném `RejectedExecutionException` vì pool đã shutdown hoặc bão hòa, không có worker nào sẽ thực hiện bước 3. Registration bị bỏ lại, khiến phase có thể chờ mãi.

Cần xử lý:

```java
phaser.register();
try {
    executor.execute(() -> {
        try {
            doWork();
        } finally {
            phaser.arriveAndDeregister();
        }
    });
} catch (RejectedExecutionException e) {
    phaser.arriveAndDeregister();
    throw e;
}
```

Đây là đoạn **minh họa** trong method với `executor` là executor có rejection bằng exception, `doWork` được định nghĩa, import `RejectedExecutionException`. Nó phù hợp khi executor dùng policy **ném exception rõ ràng**, ví dụ `AbortPolicy`.

**Không áp dụng mù quáng cho mọi handler:** `CallerRunsPolicy` có thể chạy task ngay trên caller; `DiscardPolicy` có thể âm thầm bỏ task mà **không ném exception**, khiến registration vẫn còn. Ownership của executor và rejection semantics phải được xác định, nếu không không thể bảo đảm registration được dọn.

Ngoài ra, nếu task được enqueue thành công nhưng **bị hủy hoặc trả ra bởi shutdownNow() trước khi bắt đầu**, worker finally chưa chạy. Caller/coordinator cần biết task có được thực thi hay không, và quản lý registration tương ứng.

## 18. Một lỗi deadlock điển hình: số party không khớp worker thực tế

Giả sử dùng `Phaser(3)` với coordinator và dự kiến hai worker, nhưng executor chỉ có **một thread**:

```java
ExecutorService pool = Executors.newSingleThreadExecutor();
Phaser phaser = new Phaser(3); // main + 2 worker obligations

pool.submit(() -> phaser.arriveAndAwaitAdvance());
pool.submit(() -> phaser.arriveAndAwaitAdvance());
phaser.arriveAndAwaitAdvance(); // Có thể treo vô hạn.
```

Đây là đoạn **minh họa sai**, không dùng nguyên đoạn để thực hành chạy. Worker đầu tiên có thể block ở barrier; worker thứ hai **vẫn nằm trong work queue**, chưa có worker để chạy; main cũng chờ worker thứ hai. Đây là **thread starvation deadlock**.

Cách xử lý:

- Bảo đảm số worker có thể **cùng đạt barrier** phù hợp với số concurrently blocking parties cần thiết.
- Không dùng một executor nhỏ để chạy nhiều tasks mà các tasks phải đồng thời cùng arrive thì mới tiến triển.
- Có thể thiết kế nonblocking completion/async orchestration, hoặc để registrations đại diện worker loops thực tế chứ không đại diện toàn bộ queued jobs.
- Dùng timeout hoặc `forceTermination` cho đường lỗi, nhưng không xem timeout là lời giải cho kiến trúc không thể tiến triển.

Một `Phaser` linh hoạt không tự tạo thêm thread hay cấp thêm capacity cho executor.

## 19. So sánh với CountDownLatch và CyclicBarrier

| Tiêu chí | `CountDownLatch` | `CyclicBarrier` | `Phaser` |
|---|---|---|---|
| Mục tiêu | Chờ count giảm về 0, thường **one-shot** | Nhiều lần rendezvous với số parties cố định | Nhiều phases với số parties **thay đổi được** |
| Register participant động | Không có API register | Không có register/deregister động | `register`, `bulkRegister` |
| Rút một party đang tham gia | Không theo nghĩa participant riêng | Không có deregister động trực tiếp | `arriveAndDeregister` |
| Tái sử dụng | Không reset sau khi về 0 | Có thể reuse theo generations | Advance qua phases |
| Wait interruptible | `await` interruptible | `await` interruptible/broken-barrier semantics | `awaitAdvanceInterruptibly`; `arriveAndAwaitAdvance` không ném interrupt |
| Khi party thất bại | Caller phải quyết định countDown có xảy ra không | Có cơ chế broken barrier theo contract | Không tự broken vì party lỗi; cần deregister/forceTermination policy |
| Hoạt động khi parties = 0 | Latch count=0 → await trả ngay | Barrier parties phải hợp lệ theo constructor | Có thể khởi tạo zero parties; termination là lifecycle riêng |

### Khi nào chọn Phaser?

- Có **nhiều giai đoạn** cần barrier và **số participants tăng/giảm**.
- Muốn một coordinator quản lý batch workers mới và cho phép workers rời pipeline.
- Cần chính sách termination tại phase boundary qua `onAdvance`.
- Có thể quản lý đăng ký/arrival theo protocol rõ, và có tests cho failure/cancellation.

### Khi nào không cần Phaser?

- Chỉ chờ N tasks xong một lần: `CountDownLatch` hoặc `CompletableFuture.allOf` đơn giản hơn.
- Luôn có đúng N workers qua mọi vòng: `CyclicBarrier` có thể dễ hiểu hơn.
- Cần producer–consumer truyền message: dùng `BlockingQueue`, không dùng Phaser làm data transport.
- Cần locking để bảo vệ shared state trong critical section: `synchronized`, `Lock`, atomics hoặc ownership.
- Cần transaction nhiều resources hoặc durable job orchestration qua process crash: Phaser không cung cấp.

## 20. Phaser phân cấp (hierarchical Phaser)

Java `Phaser` hỗ trợ constructor nhận parent:

```java
Phaser parent = new Phaser(1);       // Coordinator.
Phaser childGroup = new Phaser(parent, 0);
childGroup.bulkRegister(3);
```

Đây là đoạn minh họa bên trong method. `childGroup` có parent `parent` và quản lý ba local parties. Trong một cấu trúc nhiều tầng, child Phaser có thể đại diện **một node party** với parent khi nó có registered parties, giảm áp lực contention ở root khi số participants rất lớn.

### Cần hiểu kỹ trước khi dùng

- Parent/child registration và deregistration có những quy tắc tự động khi child chuyển từ 0→nonzero hoặc ngược lại; không đơn giản cộng ba local parties vào parent như ba registrations độc lập.
- Child Phaser được liên kết với root phase; advanced phase và termination được phối hợp theo cấu trúc hierarchy.
- Quan sát `parent.getRegisteredParties()` không phải số **tổng leaf participants toàn hệ**.
- Khi số participant rất lớn hoặc contention tại một Phaser cao, phân cấp có thể cải thiện hiệu năng, nhưng tạo thêm độ phức tạp debug/lifecycle.
- Với đa số bài tập nhỏ, một Phaser đơn giản là đủ. Chỉ dùng hierarchy khi đã có nhu cầu và benchmark phù hợp.

Theo tài liệu Java, giới hạn số registrations trực tiếp trên một Phaser là **65.535 parties**; nếu cần nhiều hơn, cân nhắc hierarchy hoặc thiết kế synchronization khác. Không nhầm giới hạn này với số thread Java được phép tồn tại.

## 21. Memory consistency và happens-before

Phaser không chỉ đếm: nó còn cung cấp các ranh giới synchronization.

JavaDoc mô tả: **hành động trong thread trước một thao tác arrival** *happen-before* các hành động liên quan đến phase advance; những hành động sau khi phase advance được quan sát sẽ nằm **sau** phase boundary phù hợp về memory ordering.

Ví dụ hai workers xây dữ liệu trước barrier rồi main đọc:

```java
// Worker A:
resultsA[0] = 10;
phaser.arriveAndAwaitAdvance();

// Worker B:
resultsB[0] = 20;
phaser.arriveAndAwaitAdvance();

// Main, nếu cũng đăng ký và tham gia đúng phase:
phaser.arriveAndAwaitAdvance();
int sum = resultsA[0] + resultsB[0];
```

Đây là **pseudocode với các đoạn nằm ở nhiều threads**, không phải code độc lập. Bảo đảm chỉ áp dụng nếu cả ba cùng thuộc **một phase đúng** và không tiếp tục mutate dữ liệu không có synchronization.

### Không ngụ ý mọi state bên ngoài Phaser an toàn

Nếu A/B cùng thực hiện `sharedCounter++` trong phase trước, họ vẫn có thể race với nhau trước barrier. Phase advance giúp quan sát completion/visibility, **không biến phép tăng không nguyên tử thành atomic**.

Dùng `AtomicInteger`, mỗi worker ghi vào partition riêng, hoặc dùng lock/immutable state theo invariant. Trong runnable demo, `AtomicInteger` bảo vệ counters; Phaser điều phối phase; `Future.get()` bổ sung ranh giới hoàn tất task.

### Khi nào phase advance là đủ, khi nào cần Future?

- Nếu cần dữ liệu worker đã xây trước arrival và main đã qua đúng phase boundary, Phaser cung cấp synchronization cần thiết.
- Nếu cần biết worker đã **thoát method và chạy cleanup sau arrival/deregister** hay chưa, barrier không nhất thiết đủ; chờ `Future.get()`/`join()`.
- Nếu worker dùng `arriveAndDeregister()` rồi tiếp tục side effect sau đó, main qua phase boundary **không tự đợi** side effect ấy.
- Nếu có worker không nằm trong Phaser nhưng sửa shared data, barrier không bao phủ worker đó.

Đây là lý do demo gọi `task.get()` trước khi in số liệu và cleanup.

## 22. Quan sát metrics và debug phase bị treo

API quan sát:

```java
int phase = phaser.getPhase();
int registered = phaser.getRegisteredParties();
int arrived = phaser.getArrivedParties();
int unarrived = phaser.getUnarrivedParties();
boolean terminated = phaser.isTerminated();
```

Đây là code trong method với biến `phaser`.

Các giá trị này giúp:

- Biết đang ở phase nào.
- Có bao nhiêu registrations đang được giữ.
- Còn bao nhiêu obligations chưa arrive.
- Phân biệt Phaser active hay terminated.

**Cẩn thận:** getters có thể được đọc ở các thời điểm khác nhau. Không khẳng định `registered == arrived + unarrived` trong một snapshot ghép từ ba lần gọi concurrent nếu phase/registrations đang thay đổi giữa các reads.

### Điều tra khi treo

1. Xem phase, registered, unarrived, terminated.
2. Xem tasks có được submit thành công không; có rejection/cancel trước khi bắt đầu không.
3. Xem worker nào đã tới barrier và worker nào còn chạy/block trong tác vụ trước barrier.
4. Xem một worker có kết thúc mà quên `arriveAndDeregister` không.
5. Kiểm tra thread pool capacity: có task đang block ở barrier giữ toàn bộ workers, trong khi một party khác còn nằm trong executor queue không.
6. Kiểm tra có ai gọi `awaitAdvance` mà không arrive cho registration còn nợ không.
7. Kiểm tra registration bị thực hiện quá muộn hoặc double arrival.
8. Nếu cancellation policy cho phép, dùng `forceTermination` để giải phóng waiters rồi xử lý root cause.

### Thread dump

```bash
jcmd <PID> Thread.print -l
```

Thay `<PID>` bằng process ID thật. Thread dump có thể hiển thị trạng thái waiting/parking trong `Phaser`/`ForkJoinPool.managedBlock` hoặc các primitive liên quan. Một thread `WAITING (parking)` không mặc nhiên là deadlock: đợi barrier hợp lệ cũng có trạng thái chờ.

Nếu muốn chứng minh lỗi, kết hợp stack traces với **danh sách registrations/participants ở tầng nghiệp vụ**; bản thân Phaser không có API liệt kê party identity theo task id.

## 23. Những lỗi phổ biến

1. **Tạo Phaser với party count theo số task dự kiến nhưng không chạy đủ worker threads.** Có thể thread-starvation deadlock.
2. **Quên register một worker trước khi nó arrive.** Arrival có thể làm lệch nghĩa vụ của party khác hoặc ném exception.
3. **Register quá muộn khi phase hiện tại đã advance.** Worker mới có thể tham gia phase khác dự kiến.
4. **Đăng ký mới trong phase đang đợi nhưng không có ai arrive cho registration mới.** Phase bị treo.
5. **Quên deregister worker đã hết việc.** Phase tiếp theo vẫn đòi arrival của worker đó.
6. **Deregister hai lần cho cùng logical registration.** Lệch state hoặc `IllegalStateException`.
7. **Nhầm `arrive()` với `arriveAndAwaitAdvance()`.** Arrive không chờ, nên thread có thể chạy tiếp trước những parties khác.
8. **Gọi `awaitAdvance(current)` nhưng chưa arrive registration của mình.** Thread chờ điều kiện mà chính nó ngăn.
9. **Gọi `awaitAdvanceInterruptibly` rồi tưởng mình đã arrive.** Nó chỉ chờ; phải arrive riêng nếu là party.
10. **Timeout sau arrive rồi gọi arriveAndDeregister ngay trong cùng phase.** Có nguy cơ double arrival; cần failure/termination policy rõ.
11. **Cho rằng interrupt tự làm Phaser giảm registered parties.** Không, phải quản lý deregister/termination.
12. **Cho rằng forceTermination giết các worker và rollback mọi side effects.** Không; nó chỉ chấm dứt barrier coordination.
13. **Xem `getRegisteredParties` là số threads còn sống.** Registration là nghĩa vụ logic, không gắn thread identity.
14. **Đặt `onAdvance` làm I/O dài/lock đợi task khác.** Có thể block phase advance và gây deadlock.
15. **Override `onAdvance` luôn false mà không có shutdown policy.** Phaser có thể không auto terminate khi zero parties theo mong muốn.
16. **Dùng getters để quyết định register/arrive dựa trên snapshot không đồng bộ.** State có thể đổi ngay.
17. **Bỏ qua submission rejection sau khi register.** Không worker nào sẽ arrive và các parties khác treo.
18. **Bỏ qua task cancel trước khi bắt đầu.** Worker finally không chạy, registration bị bỏ lại.
19. **Xử lý lỗi worker bằng deregister rồi coi phase là thành công.** Barrier mở không xác nhận business stage đã thành công.
20. **Dùng Phaser để bảo vệ `counter++` giữa workers.** Barrier không làm phép update trong cùng phase trở nên nguyên tử.
21. **Dùng one-shot CountDownLatch semantics cho Phaser mà không quản lý phases sau.** Party còn registered phải arrive tiếp ở phase mới.
22. **Cho rằng mọi phase advances diễn ra trên main thread.** Arrival cuối có thể từ worker khác.
23. **Cho rằng phaser terminated nghĩa mọi Futures đã hoàn tất.** Worker có thể làm cleanup sau deregister; cần await completion riêng.
24. **Nhầm party hierarchy với số tổng leaf registrations.** Parent có thể chỉ thấy child Phaser như một node party.
25. **Dùng Thread.sleep để làm producer/worker “kịp đăng ký”.** Timing không thể thay protocol register-before-execute.
26. **Cho rằng awaitAdvance luôn chờ.** Nếu phase truyền vào đã khác current, nó có thể trả ngay.
27. **Cho rằng awaitAdvanceInterruptibly timeout là cancellation của toàn Phaser.** Không; timeout là của thread đợi.
28. **Không đặt deadline/alert cho phase chờ lâu.** Một party lỗi có thể làm tất cả chờ vô hạn.
29. **Sử dụng Phaser như data queue.** Phaser chỉ là barrier/phase coordination, không lưu và chuyển message.
30. **Dùng Phaser làm distributed synchronizer.** Nó quản lý synchronization nội bộ JVM, không xử lý nodes crash, network partition hoặc durability.

## 24. Bài tập thực hành

### Bài tập A — năm workers, hai người rút sau phase 0

Khởi tạo `Phaser(1)` cho coordinator, `bulkRegister(5)` trước khi submit 5 tasks. Trong phase 0, cả năm làm việc; hai tasks `arriveAndDeregister()`, ba tasks `arriveAndAwaitAdvance()` rồi làm phase 1. Coordinator qua barrier 0 rồi barrier 1. Kiểm tra số parties còn lại và đảm bảo main không tự advance trước khi đăng ký xong.

### Bài tập B — late join tại phase boundary

Bắt đầu với A/B + coordinator; sau phase 0, đăng ký C/D rồi submit. Cho A/B/C/D làm phase 1, tất cả deregister và main rút sau khi đợi phase 1. Dùng `Future.get()` để khẳng định task completion. Tránh in số thứ tự worker vì scheduler không bảo đảm.

### Bài tập C — timeout có kiểm soát

Một worker cố tình không tới barrier. Coordinator đã arrive bằng `arrive()`, rồi gọi `awaitAdvanceInterruptibly(phase, timeout, unit)`. Khi timeout, coordinator ghi cause và gọi `forceTermination()` theo policy fail-fast, hủy workers và shutdown pool. Giải thích vì sao không nên gọi `arriveAndDeregister()` lần nữa cho registration đã arrived trong cùng phase.

### Bài tập D — executor thiếu worker

Tạo một test có hai tasks chờ barrier nhưng pool chỉ một worker. Dùng test harness timeout để chứng minh không thể tiến triển, tránh chạy một ví dụ cố tình treo vô hạn. Sau đó tăng worker capacity hoặc sửa kiến trúc để hai parties có thể đồng thời tiến tới barrier.

### Bài tập E — custom onAdvance

Tạo subclass `ThreeRoundsPhaser` để terminate sau ba phase. Dùng hai parties thực hiện đúng ba vòng. Phân tích return value từ `arriveAndAwaitAdvance` ở lần cuối có thể báo termination bằng phase âm, và vì sao code cần kiểm tra `isTerminated()`.

### Bài tập F — hierarchy

Tạo root với coordinator, hai child phasers mỗi child có 3 local parties. So sánh registered count của root và tổng local parties, giải thích vì sao root không lưu trực tiếp sáu registrations. Viết test tất cả local parties qua một phase rồi shutdown sạch.

## 25. Câu hỏi ôn tập và đáp án

**Câu 1.** Vì sao Phaser phù hợp khi số participants thay đổi?  
**Đáp án:** Nó hỗ trợ `register`/`bulkRegister` để thêm parties và `arriveAndDeregister` để rút parties khi chạy qua nhiều phases.

**Câu 2.** `new Phaser(1)` có tạo một worker thread không?  
**Đáp án:** Không. Nó đăng ký một logical party ban đầu; caller phải tự chạy code và báo arrival cho party đó.

**Câu 3.** `arrive()` có đợi các parties còn lại không?  
**Đáp án:** Không. Nó đánh dấu arrived và trả về phase đang thực thi.

**Câu 4.** `arriveAndAwaitAdvance()` khác gì `arrive()`?  
**Đáp án:** Nó vừa báo arrival vừa chờ phase advance.

**Câu 5.** Một worker ngừng tham gia, tại sao phải gọi `arriveAndDeregister`?  
**Đáp án:** Nếu chỉ arrive rồi không quay lại, registration vẫn tồn tại ở phase sau, có thể làm barrier chờ mãi.

**Câu 6.** `awaitAdvance(phase)` có cần caller là một party không?  
**Đáp án:** Không. Non-party observer có thể chờ; method không tự arrive.

**Câu 7.** `awaitAdvanceInterruptibly` timeout có tự deregister party không?  
**Đáp án:** Không. Nó chỉ kết thúc việc chờ của caller; registration và arrival state cần protocol riêng.

**Câu 8.** Có được gọi `arriveAndDeregister` ngay sau `arrive()` trên cùng party và phase để “dọn” không?  
**Đáp án:** Không. Đây là double arrival theo cùng registration/phase; có thể làm sai nghĩa vụ. Cần owner-managed failure protocol/termination.

**Câu 9.** Nếu worker mới register khi phase 2 đang mở, có chắc nó chỉ phải arrive từ phase 3 không?  
**Đáp án:** Không. Registration đang active thường áp dụng cho current phase; cần coordinator quản lý phase boundary.

**Câu 10.** Vì sao executor một worker + hai tasks đang chờ barrier có thể deadlock?  
**Đáp án:** Task thứ nhất giữ worker rồi chờ task thứ hai arrive; task hai vẫn queue và không có worker để chạy.

**Câu 11.** `getRegisteredParties()` có cho biết số thread Java đang sống không?  
**Đáp án:** Không. Nó đếm logical registrations, không thread identities.

**Câu 12.** Khi registered parties về 0 trong default Phaser và phase advance, điều gì xảy ra?  
**Đáp án:** `onAdvance` mặc định trả true, khiến Phaser terminate.

**Câu 13.** `onAdvance` return true có nghĩa advance tiếp không?  
**Đáp án:** Không; true báo termination.

**Câu 14.** `forceTermination()` có dừng ngay mọi business task đang tính toán không?  
**Đáp án:** Không. Nó chấm dứt synchronization và giải phóng waiters, không kill threads hay rollback side effects.

**Câu 15.** Nếu cả hai workers cùng `counter++` trước barrier, main qua barrier có được tổng chính xác không?  
**Đáp án:** Không chắc. Barrier không sửa lost update xảy ra giữa workers trong phase; cần atomic/lock/partitioned writes.

**Câu 16.** Chương trình demo có phase0Workers=2 và phase1Workers=3, vì sao?  
**Đáp án:** A/B tham gia cả hai phases; C được register sau phase 0 và chỉ tham gia phase 1.

**Câu 17.** `arriveAndDeregister` có nghĩa “đã chạy xong toàn bộ finally/cleanup của worker” không?  
**Đáp án:** Không. Worker có thể tiếp tục code sau arrival; caller cần chờ Future/join nếu cần task hoàn tất.

**Câu 18.** Nếu pool rejected task sau khi register, phải làm gì?  
**Đáp án:** Xử lý rejection và rút registration theo policy nếu task chưa bắt đầu, hoặc terminate/cancel có kiểm soát. Không để party mồ côi chặn phase.

**Câu 19.** `getArrivedParties` và `getUnarrivedParties` có cung cấp một snapshot nguyên tử chung khi đọc lần lượt không?  
**Đáp án:** Không. Phases/registrations có thể đổi giữa các lần đọc.

**Câu 20.** Khi nào chọn CountDownLatch thay Phaser?  
**Đáp án:** Khi chỉ chờ một tập hoạt động hoàn tất **một lần** và không cần dynamic multi-phase participation.

## 26. Checklist thiết kế thực tế

Trước khi dùng Phaser trong dự án, kiểm tra:

- **Scope:** Phaser thuộc một job/pipeline nào, ai là owner lifecycle?
- **Party identity:** mỗi registration tương ứng worker, task hay coordinator?
- **Registration timing:** số parties đã được thêm **trước khi tasks có thể arrive**?
- **Phase protocol:** task nào cần arrive ở từng phase, task nào cần deregister?
- **Join semantics:** newcomer tham gia current hay next phase, ai giữ phase mở?
- **Failure handling:** task lỗi trước barrier có làm những parties khác chờ vô hạn không?
- **Submission/cancel:** rejection và cancellation trước task start có đường cleanup registration?
- **Interrupt:** cần interruptible/timed wait hay uninterruptible barrier?
- **Timeout:** nếu một phase treo, coordinator có deadline và forceTermination policy?
- **Termination:** default last-party deregistration hay custom `onAdvance`?
- **Executor capacity:** có đủ workers để những parties blocking cùng tới barrier?
- **State safety:** dữ liệu dùng qua phases có publication, atomicity và ownership đúng?
- **Completion:** cần chờ barrier, chờ Future, hay cả hai?
- **Observability:** phase, registered/unarrived, last progress, task failures và deadline?
- **Scalability:** participant count lớn có cần hierarchical Phaser hoặc mô hình khác?
- **Testing:** test một/zero/many parties, register/deregister động, phase timeout, worker failure và shutdown?
- **Version:** triển khai với đúng JDK hỗ trợ API cần dùng (ví dụ Java 17).

Một thiết kế Phaser tốt bắt đầu từ **bảng trách nhiệm arrival của mỗi party theo từng phase**, không bắt đầu từ số thread dự đoán.

## 27. Tài liệu tham khảo chính thức

- [Java SE 17 — Phaser](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Phaser.html): constructors, register/bulkRegister, arrive/await/deregister, termination, onAdvance, hierarchy và memory consistency.
- [Java SE 17 — CyclicBarrier](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CyclicBarrier.html): cyclic barrier có số parties cố định.
- [Java SE 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): đồng bộ one-shot count-down.
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): submit, Future, shutdown và lifecycle.
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): completion và memory-consistency effects.
- [Java SE 17 — AtomicInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html): atomic counters dùng trong ví dụ.
- [Java Language Specification `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): Java Memory Model, happens-before và thread communication.

**Tổng kết:** `Phaser` là barrier nhiều vòng dành cho **tập participants linh hoạt**. Sức mạnh đến từ `register/bulkRegister` và `arriveAndDeregister`, nhưng chính sự linh hoạt đó làm tăng yêu cầu quản lý ownership, phase timing và failure paths. Đăng ký đúng trước khi worker có thể arrive, cho mỗi party một obligation rõ ở mỗi phase, deregister khi rời, và dùng `Future.get` khi cần biết task thực sự hoàn tất. Nếu một participant mất tích, Phaser không thể tự đoán cách sửa: cần coordinator, timeout/interrupt policy và recovery/termination nhất quán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronizers-004">Semaphore giới hạn truy cập tài nguyên hữu hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Semaphore giới hạn truy cập tài nguyên hữu hạn trong Java

## 1. Vấn đề: nhiều thread, tài nguyên có số lượng giới hạn

Giả sử một ứng dụng có 100 tác vụ đồng thời nhưng một hệ thống downstream chỉ cho phép **5 yêu cầu đang xử lý**. Nếu mỗi task tự gọi downstream ngay khi được chạy, số kết nối hoặc luồng yêu cầu có thể vượt mức tài nguyên cho phép, làm tăng timeout, lock contention và nguy cơ sập dịch vụ.

**`java.util.concurrent.Semaphore`** là synchronizer dùng một số nguyên không âm biểu thị **số permit hiện có**. Trước khi bước vào phần công việc sử dụng tài nguyên hữu hạn, task phải **acquire một permit**; khi không còn cần giữ quyền truy cập, task **release permit**. Nếu tất cả permit đang được giữ, một caller muốn acquire theo kiểu blocking sẽ chờ cho tới khi có permit được release hoặc xảy ra interruption.

~~~text
              Semaphore(3)
           [permit] [permit] [permit]
               |        |        |
           worker A  worker B  worker C    -> được vào vùng giới hạn
                                         
           worker D, E, F                  -> đang chờ permit
               |
       khi A release một permit
               |
         một waiter có thể acquire
~~~

**Điểm cần nhớ:** Semaphore **không đại diện cho tài nguyên vật lý** (connection, socket, thiết bị...). Nó chỉ quản lý *quyền được bắt đầu sử dụng* theo quy ước. Ứng dụng vẫn phải đảm bảo tài nguyên thực sự có sẵn, giải phóng đúng lúc và các worker tuân thủ giao thức acquire/release.

Bài học tập trung vào Java Concurrency chuẩn, không phụ thuộc framework khác.

## 2. Permit, acquire, release và bất biến cốt lõi

Tạo semaphore:

~~~java
Semaphore slots = new Semaphore(3);
~~~

Đây là **đoạn minh họa** trong ngữ cảnh đã import `java.util.concurrent.Semaphore`, không phải class độc lập.

- **`new Semaphore(n)`** bắt đầu với n permit.
- **`acquire()`** cần một permit. Nếu còn permit, số permit hiện có giảm đi một và caller tiếp tục; nếu không còn, caller chờ và có thể bị interrupt.
- **`release()`** tăng số permit hiện có thêm một và có thể đánh thức một caller đang chờ.
- **`tryAcquire()`** thử ngay, trả `true` khi lấy được permit và `false` nếu không lấy được ngay.
- **`tryAcquire(timeout, unit)`** chờ có hạn; trả `false` nếu hết thời gian mà chưa lấy được permit; có thể ném `InterruptedException`.
- **`availablePermits()`** trả snapshot số permit đang có tại thời điểm gọi, **không đặt chỗ** cho bước acquire kế tiếp.

Với **n permit ban đầu**, nếu code tuân thủ một acquire thành công ↔ một release tương ứng, số tác vụ đang giữ permit không vượt n. Đây là **bất biến do ứng dụng duy trì**; Semaphore tự nó không theo dõi chủ sở hữu permit.

Ví dụ:

~~~text
Semaphore(2)
Ban đầu: available=2
Task A acquire thành công: available=1
Task B acquire thành công: available=0
Task C tryAcquire(): false
Task A release(): available=1
Task C acquire thành công: available=0
Task B release(), C release(): available=2
~~~

Đừng suy ra rằng một thread giữ permit thì chỉ thread đó mới được release: Java Semaphore **không có khái niệm ownership như ReentrantLock**. Điều này hữu dụng cho handoff giữa thread nhưng cũng khiến bug **release thừa** hoặc release mà chưa acquire dễ xuất hiện.

### Quan hệ bộ nhớ (happens-before)

Theo hợp đồng Java Semaphore, hành động trong một thread **trước khi gọi release** xảy ra *happens-before* hành động trong thread khác **sau khi acquire thành công** trên cùng semaphore. Đó là cơ chế đồng bộ hữu ích, nhưng **không biến mọi thao tác trên shared data thành thread-safe**. Với n lớn hơn 1, nhiều task có thể vẫn truy cập cùng dữ liệu trong vùng được giới hạn; nếu các task cùng sửa một biến/cấu trúc không thread-safe, vẫn cần đồng bộ hoặc phân chia dữ liệu thích hợp.

## 3. Bốn kiểu acquire và lựa chọn theo deadline

| API | Hành vi khi hết permit | Giá trị trả về | Interruption |
|---|---|---|---|
| `acquire()` | Chờ tới khi có permit | `void` | Có, `InterruptedException` |
| `acquireUninterruptibly()` | Chờ tới khi có permit | `void` | Không ném vì interrupt trong lúc chờ; giữ lại trạng thái interrupt khi kết thúc |
| `tryAcquire()` | Trả về ngay | `boolean` | Không chờ nên không phải API chờ interruptible |
| `tryAcquire(timeout, unit)` | Chờ tối đa timeout | `boolean` | Có, `InterruptedException` |

Ngoài ra có các overload cho nhiều permit:

~~~java
slots.acquire(2);
slots.release(2);
boolean accepted = slots.tryAcquire(2);
boolean timed = slots.tryAcquire(2, 100, TimeUnit.MILLISECONDS);
~~~

Các dòng trên là **mã minh họa** với `slots` là Semaphore và `TimeUnit` đã import; `acquire(2)` có thể ném InterruptedException. Acquire nhiều permit yêu cầu đủ toàn bộ số lượng permit theo hợp đồng, không nhận một phần rồi trả về. Tránh yêu cầu quá nhiều permit khiến task nhỏ khó được phục vụ.

### Chọn cách nào?

- **`acquire()`:** khi producer có thể chờ và task phải được xử lý, nhưng phải có kế hoạch shutdown/interrupt. Nếu caller là thread request có deadline ngắn, chờ vô hạn thường không thích hợp.
- **`tryAcquire()`:** dùng cho admission control **fail-fast**, ví dụ trả lỗi quá tải hoặc bỏ công việc ít quan trọng. Bắt buộc kiểm tra boolean.
- **`tryAcquire(timeout)`:** cho phép đợi ngắn để hấp thụ burst nhưng giữ **giới hạn latency**. Khi trả false, task chưa vào vùng cần permit; không được release.
- **`acquireUninterruptibly()`:** chỉ dùng khi có lý do mạnh để không bỏ cuộc khi nhận interrupt. Trong shutdown, nó có thể làm worker chờ rất lâu; không phải lựa chọn mặc định.

**Không dùng `availablePermits() > 0` làm bước kiểm tra trước rồi tự tin sẽ acquire ngay**: nhiều thread có thể đồng thời thấy cùng snapshot. Chỉ kết quả `tryAcquire()` hoặc acquire hoàn tất mới xác nhận đã giữ quyền truy cập.

## 4. Quy tắc an toàn: acquire thành công rồi mới release trong finally

Mẫu thực hành quan trọng nhất:

~~~java
boolean acquired = false;
try {
    acquired = slots.tryAcquire(200, TimeUnit.MILLISECONDS);
    if (!acquired) {
        // Quyết định reject/defer: không gọi downstream.
        return;
    }
    useLimitedResource();
} finally {
    if (acquired) {
        slots.release();
    }
}
~~~

Đây là **mã minh họa bên trong một phương thức**, với `slots` đã khai báo và `useLimitedResource()` đại diện cho xử lý nghiệp vụ; không phải file Java độc lập. Overload có timeout có thể ném `InterruptedException`.

Với blocking `acquire()`:

~~~java
slots.acquire(); // Có thể ném InterruptedException.
try {
    useLimitedResource();
} finally {
    slots.release();
}
~~~

**Đặt `acquire()` trước khối try/finally** giúp không release nếu acquire chưa thành công và ném InterruptedException. Sau khi đã acquire, finally chạy dù công việc bên trong ném exception unchecked.

Nếu cần acquire nhiều permit, **release đúng số đã lấy**. Một lỗi rất khó phát hiện là `release()` cả trong catch lẫn finally, khiến available permit tăng vượt capacity ban đầu: về sau hệ thống có thể chạy nhiều công việc đồng thời hơn giới hạn đặt ra.

**Semaphore không giới hạn số permit tối đa khi release.** Nó không tự bác bỏ một release thừa chỉ vì đã đạt số permit ban đầu. Có thể kiểm thử bất biến này ở cấp application bằng metric hoặc semaphore wrapper, nhưng không dùng snapshot `availablePermits()` như cơ chế khóa chính.

## 5. Ví dụ Java 17: chỉ hai tác vụ được xử lý đồng thời

Chương trình dùng **Semaphore(2)** và hai worker giữ permit trong khi đợi một latch. Thread main thử acquire permit thứ ba: chắc chắn nhận `false` vì hai permit đang được giữ. Khi cho hai worker hoàn tất, permit được trả về.

Lưu **SemaphoreLimitDemo.java**:

~~~java
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class SemaphoreLimitDemo {
    public static void main(String[] args) throws Exception {
        Semaphore slots = new Semaphore(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch finishWork = new CountDownLatch(1);

        try {
            Callable<String> first = () -> {
                slots.acquire();
                try {
                    bothEntered.countDown();
                    finishWork.await();
                    return "task-A";
                } finally {
                    slots.release();
                }
            };
            Callable<String> second = () -> {
                slots.acquire();
                try {
                    bothEntered.countDown();
                    finishWork.await();
                    return "task-B";
                } finally {
                    slots.release();
                }
            };

            Future<String> a = pool.submit(first);
            Future<String> b = pool.submit(second);

            if (!bothEntered.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Workers not ready");
            }

            boolean thirdAccepted = slots.tryAcquire();
            System.out.println("third accepted=" + thirdAccepted);
            if (thirdAccepted) {
                slots.release(); // Phòng vệ nếu điều kiện ví dụ thay đổi.
            }

            finishWork.countDown();
            System.out.println("results=" + a.get() + "," + b.get());
            System.out.println("available after=" + slots.availablePermits());
        } finally {
            finishWork.countDown();
            pool.shutdownNow();
        }
    }
}
~~~

Chạy bằng JDK 17+:

~~~bash
javac SemaphoreLimitDemo.java
java SemaphoreLimitDemo
~~~

Stdout dự kiến:

~~~text
third accepted=false
results=task-A,task-B
available after=2
~~~

**Vì sao kết quả ổn định?** `bothEntered.await(...)` chỉ tiếp tục khi cả hai worker đã acquire thành công và đi đến latch. Lúc đó mỗi worker đang chờ `finishWork`, nên cả hai permit vẫn được giữ. Main gọi `tryAcquire()` trước khi thả latch; tất yếu không có permit rảnh. Sau khi release latch và `Future.get()` của cả hai task hoàn tất, hai `finally` đã release permit. Thứ tự chạy của hai worker không ảnh hưởng thứ tự `a.get(), b.get()` được in từ main.

**Chú ý:** `pool.shutdownNow()` trong finally là bước phòng vệ khi ví dụ có lỗi; trong hệ thống thật cần quy trình graceful shutdown có thời hạn và kiểm tra kết quả dừng.

## 6. Ví dụ Java 17: giới hạn thời gian chờ permit

Với một tài nguyên chỉ cho phép một request đồng thời, main giữ permit trước. Worker khác không thể acquire trong thời hạn ngắn, nên `tryAcquire(timeout)` trả `false`. Sau khi main release, một lần thử mới thành công. Đây là mẫu admission control có timeout.

Lưu **SemaphoreTimeoutDemo.java**:

~~~java
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class SemaphoreTimeoutDemo {
    public static void main(String[] args) throws Exception {
        Semaphore slots = new Semaphore(1);

        slots.acquire(); // Giả lập tài nguyên đang bị chiếm.
        try {
            boolean accepted = slots.tryAcquire(
                    30, TimeUnit.MILLISECONDS);
            System.out.println("accepted while busy=" + accepted);
            if (accepted) {
                slots.release();
            }
        } finally {
            slots.release();
        }

        boolean acceptedAfterRelease = slots.tryAcquire(
                30, TimeUnit.MILLISECONDS);
        System.out.println("accepted after release=" + acceptedAfterRelease);
        if (acceptedAfterRelease) {
            slots.release();
        }
        System.out.println("available=" + slots.availablePermits());
    }
}
~~~

Chạy:

~~~bash
javac SemaphoreTimeoutDemo.java
java SemaphoreTimeoutDemo
~~~

Stdout:

~~~text
accepted while busy=false
accepted after release=true
available=1
~~~

**Timeout không phải auto-retry**: `tryAcquire(timeout)` trả false nghĩa là caller chưa có permit sau thời hạn chờ, nên chưa được bắt đầu công việc downstream. Caller phải có chính sách: trả lỗi quá tải, bỏ/defer nếu được phép hoặc retry có budget và backoff phù hợp.

Ở đây cũng không dùng sleep để ép kết quả: main giữ permit trong toàn bộ thời gian gọi timed tryAcquire, nên kết quả false là xác định.

## 7. Ví dụ Java 17: interrupt một worker đang chờ permit

`Semaphore.acquire()` là **interruptible**. Ví dụ này cho main giữ permit, worker bắt đầu chờ rồi bị yêu cầu dừng bằng `Future.cancel(true)`. CountDownLatch xác nhận worker đã vào logic chờ, còn main vẫn giữ permit, vì thế worker không thể đi qua acquire. Worker bắt `InterruptedException`, đánh dấu đã nhận ngắt và không release permit mà nó chưa acquire.

Lưu **SemaphoreInterruptDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SemaphoreInterruptDemo {
    public static void main(String[] args) throws Exception {
        Semaphore slots = new Semaphore(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();

        slots.acquire(); // Main giữ permit; worker không thể lấy.
        try {
            Future<?> waiter = pool.submit(() -> {
                boolean acquired = false;
                try {
                    entered.countDown();
                    slots.acquire();
                    acquired = true;
                } catch (InterruptedException ex) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                } finally {
                    if (acquired) {
                        slots.release();
                    }
                    finished.countDown();
                }
            });

            if (!entered.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Waiter not started");
            }
            boolean cancelled = waiter.cancel(true);
            if (!finished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Waiter did not stop");
            }

            System.out.println("cancel accepted=" + cancelled);
            System.out.println("waiter interrupted=" + interrupted.get());
            System.out.println("waiter cancelled=" + waiter.isCancelled());
        } finally {
            slots.release();
            pool.shutdownNow();
        }
        System.out.println("available=" + slots.availablePermits());
    }
}
~~~

Chạy:

~~~bash
javac SemaphoreInterruptDemo.java
java SemaphoreInterruptDemo
~~~

Stdout:

~~~text
cancel accepted=true
waiter interrupted=true
waiter cancelled=true
available=1
~~~

**Điểm phân biệt:** `Future.cancel(true)` gửi yêu cầu interrupt tới worker đang chạy của ExecutorService. Worker được chặn trong `Semaphore.acquire()` nên `InterruptedException` xảy ra; worker có thể thoát sạch. Tuy nhiên, không có quy tắc chung rằng mọi tác vụ Java lập tức dừng khi cancel(true): việc dừng các thao tác khác vẫn phụ thuộc chúng có hỗ trợ interrupt và code có hợp tác không.

Cờ `acquired` bảo đảm chỉ release nếu thực sự đã acquire. Main release permit **chỉ một lần** trong finally; tổng số permit trở về 1.

## 8. Fairness: Semaphore công bằng hay không?

Mặc định `new Semaphore(permits)` dùng chế độ **nonfair**. Có thể tạo semaphore có thứ tự phục vụ công bằng:

~~~java
Semaphore fair = new Semaphore(3, true);
~~~

Với chế độ fair, các lời gọi acquire **blocking** được phục vụ theo thứ tự đến tại **những điểm ordering nội bộ của cơ chế acquisition**. Điều này giúp hạn chế nguy cơ thread chờ lâu bị vượt mặt, nhưng **không cam kết thời điểm thread hoàn tất, thứ tự bắt đầu ngay khi gọi method, hay độ trễ cố định** do OS scheduler, thời gian thực hiện task và cuộc đua trước điểm ordering còn tác động.

Một chi tiết dễ bỏ sót: **`tryAcquire()` không có timeout có thể lấy permit ngay khi thấy rảnh, kể cả khi fairness=true và có thread đang chờ** (*barging*). Với mã cần tôn trọng fairness, overload **`tryAcquire(0, TimeUnit.SECONDS)`** là cách thử ngay có tham gia chính sách fairness (nhưng interruptible).

| Thuộc tính | Nonfair (mặc định) | Fair |
|---|---|---|
| Chiến lược acquire khi cạnh tranh | Có thể barging, thường thiên về throughput | Thường phục vụ theo thứ tự waiting tại điểm cấp permit |
| Mục tiêu | Giảm overhead, throughput cao | Giảm starvation cho waiter |
| Chi phí | Thường thấp hơn | Có thể tốn đồng bộ hơn, giảm throughput |
| Cam kết thứ tự log | Không | **Cũng không** |
| `tryAcquire()` không timeout | Có thể barging | **Vẫn có thể barging** |

Fairness không thay thế deadline, starvation monitoring hoặc quản lý workload. Nếu một thread giữ permit rất lâu, các thread khác vẫn chờ dù chế độ fair=true.

## 9. Binary semaphore khác gì ReentrantLock?

**Binary semaphore** (`new Semaphore(1)`) giới hạn vùng truy cập còn **một task tại một thời điểm**, nên nhìn giống mutex. Tuy nhiên:

| Tiêu chí | `Semaphore(1)` | `ReentrantLock` |
|---|---|---|
| Số holder logic | 1 khi tuân thủ giao thức | 1 owner thread |
| Ownership bắt buộc | **Không**: thread khác cũng có thể release | **Có**: chỉ owner được unlock |
| Reentrant | **Không** theo nghĩa lock: acquire lần hai cần permit khác | **Có**: owner có thể lock nhiều lần |
| `release` / `unlock` thừa | Có thể tăng permit không mong muốn | `unlock` không phải owner ném IllegalMonitorStateException |
| Chuyển quyền giữa thread | Có thể dùng để handoff/signal | Không có cùng mô hình release khác thread |
| Dùng chính | Giới hạn quyền truy cập tài nguyên đồng thời | Bảo vệ critical section có owner thread |

Nếu mục tiêu là **bảo vệ một biến chia sẻ** với một lock độc quyền và cần ownership, `ReentrantLock` (hoặc `synchronized`) thường biểu đạt rõ hơn. Nếu mục tiêu là **N thao tác được phép đồng thời**, Semaphore là primitive tự nhiên.

Binary semaphore còn có thể khởi tạo bằng **0 permit** để dùng như tín hiệu một lần/handoff, nhưng cần quản lý release tránh tích lũy permit nếu tín hiệu lặp lại. Với chờ đến một sự kiện hoặc nhiều worker cùng hoàn tất, có thể cân nhắc CountDownLatch, Phaser hoặc Condition tùy bài toán.

## 10. Semaphore và connection pool/resource pool

Giả sử có **10 kết nối** hoặc **10 thiết bị** vật lý:

- Semaphore(10) giới hạn **không quá 10 vùng xử lý có permit**.
- Nhưng bạn vẫn cần một **bộ quản lý 10 tài nguyên thực**: ai lấy connection nào, kiểm tra connection còn dùng được, khi nào trả lại và điều gì xảy ra nếu lỗi.
- Semaphore không tự tạo connection, không theo dõi connection đang hỏng và không làm thao tác quản lý tài nguyên thành thread-safe.

Ví dụ logic:

~~~text
acquire permit
    |
  borrow actual resource
    |
  use resource with timeout
    |
  return/close actual resource
    |
release permit
~~~

Thứ tự giải phóng nên đảm bảo **không trả permit trước khi tài nguyên thật sự sẵn sàng cho lượt mới**. Với `try-with-resources`, phải hiểu semantics của `close()`: có nơi close trả connection về pool, có nơi close đóng kết nối vật lý. Nếu một resource bị hỏng, cần xử lý thay thế hoặc giảm khả năng cấp phát thực tế thay vì vẫn cấp quyền cho 10 task mặc dù chỉ còn 9 resource hợp lệ.

**Không nên đặt hai cơ chế chờ cạnh nhau mà không rõ ràng**: chẳng hạn acquire Semaphore trước rồi chờ lâu trên một queue connection có capacity khác có thể giữ permit vô ích. Nếu thư viện resource pool đã có giới hạn concurrency/borrow timeout đáng tin, cân nhắc sử dụng chính nó thay vì thêm semaphore không cần thiết.

### Sizing permits

Một giới hạn hợp lý phải dựa trên **năng lực downstream** và yêu cầu độ trễ, không chỉ số thread của ứng dụng:

- Database cho phép bao nhiêu connection hoạt động và mỗi query tốn bao lâu?
- Dịch vụ ngoài áp giới hạn **in-flight**, rate limit hoặc cả hai?
- Một task có thể giữ đồng thời nhiều permit từ nhiều tài nguyên hay không?
- Có nhiều instance ứng dụng cùng dùng chung downstream không?

**Semaphore chỉ giới hạn concurrency, không trực tiếp giới hạn requests/giây.** Nếu mỗi request hoàn thành nhanh, 5 permit vẫn có thể cho rất nhiều requests/giây. Nếu downstream áp rate limit theo cửa sổ thời gian, cần rate limiter/token bucket hoặc cơ chế quota khác. Một giới hạn 10 permit **mỗi instance** cũng không tự tạo giới hạn toàn cụm 10 khi ứng dụng chạy trên 20 instance.

## 11. Semaphore và bounded queue/backpressure

Khi producer liên tục nhận công việc, chỉ đặt Semaphore ở đoạn gọi downstream có thể khiến **rất nhiều task đang chờ acquire** tích lũy ở bên trong executor hoặc tạo thread, dù số task đang dùng downstream được giới hạn.

Vì vậy cần phân biệt:

- **Semaphore:** giới hạn **số task được đi vào vùng dùng tài nguyên**.
- **Bounded BlockingQueue:** giới hạn **số task chờ** trong queue nếu có chính sách admission phù hợp.
- **ThreadPoolExecutor:** giới hạn số worker và điều phối queue/rejection khi saturated, tùy cấu hình.
- **Rate limiter:** giới hạn tốc độ bắt đầu công việc theo thời gian.
- **Deadline/cancellation:** giới hạn thời gian task chờ và ngừng những công việc không còn hữu ích.

~~~text
Incoming requests
       |
 admission / bounded queue / reject
       |
 worker thực thi
       |
 tryAcquire(timeout) trên Semaphore(N)
       |
   N thao tác downstream đang active
       |
 release đúng một permit
       |
 hoàn tất
~~~

**Nếu chỉ có Semaphore mà thiếu admission control**, nguy cơ backlog tăng và request timeout ở tầng trên vẫn còn. Backpressure thực sự cần **tác động ngược đến producer** khi hệ thống đầy, ví dụ trả lỗi quá tải, chờ ngắn hoặc trì hoãn có giới hạn.

Khi dùng Semaphore bên trong một worker pool nhỏ, nhiều worker có thể cùng bị chặn ở `acquire()\), làm worker không còn phục vụ tác vụ khác. Đôi khi nên đặt semaphore tại điểm admission hoặc thiết kế async queue để tránh dùng worker chỉ để chờ permit.

## 12. Deadlock, starvation và acquire nhiều tài nguyên

### Deadlock do giữ permit rồi chờ tài nguyên khác

Giả sử Task A đã giữ semaphore X nhưng đang chờ Y, Task B giữ Y nhưng chờ X. Cả hai có thể deadlock dù mỗi semaphore hoạt động đúng. Cách phòng tránh thường là **thứ tự acquire thống nhất**, deadline và không giữ permit lâu trong khi chờ các tài nguyên khác.

~~~text
Task A: acquire(X) -> wait(Y)
Task B: acquire(Y) -> wait(X)
=> vòng chờ
~~~

### Self-deadlock với một permit

Một method đang giữ `Semaphore(1)` rồi gọi method khác tiếp tục `acquire()` trên cùng semaphore có thể tự chờ mãi, vì Semaphore **không reentrant**. Không áp dụng mẫu reentrant lock một cách máy móc cho Semaphore.

### Hoạt động quá lâu trong vùng được bảo vệ

Một task giữ permit trong khi đợi I/O vô thời hạn, hoặc ghi log đồng bộ chậm, có thể khiến các waiter khác bị đói tài nguyên. Đặt timeout I/O, giới hạn critical section và phát hiện **permit hold duration** bất thường.

### Nhiều permit cùng lúc

`acquire(2)` thuận tiện khi một công việc thực sự dùng hai slot, nhưng nếu nhiều task nhỏ chỉ cần một permit, một tác vụ đòi nhiều permit có thể chờ lâu, đặc biệt trong chế độ nonfair. Cân nhắc fairness và scheduler workload khi yêu cầu permit rất không đồng đều.

**`reducePermits` và `drainPermits`:** các phương thức nâng cao để giảm hoặc rút permit hiện có, không phải API quản lý resource pool phổ thông; chúng có thể đưa semaphore vào trạng thái ít permit hoặc âm permit trong những trường hợp điều chỉnh chuyên biệt. Không dùng chúng tùy tiện như cách giải phóng tài nguyên hoặc xử lý shutdown, vì chúng làm khó bảo đảm số task được phép chạy.

## 13. Interruption, cancellation và graceful shutdown

Thread đang chờ `acquire()` hoặc `tryAcquire(timeout)` có thể bị interrupt, ném `InterruptedException`. Khi code bắt exception nhưng **không truyền tiếp**, thông thường nên khôi phục cờ interrupt bằng `Thread.currentThread().interrupt()` và thoát sớm để tầng trên biết có yêu cầu ngừng.

**Không release nếu chưa acquire thành công.** Đây là lỗi điển hình khi đặt release trong finally bao quanh cả lệnh acquire.

Nếu caller dùng `Future.cancel(true)`, việc dừng thực sự của task còn phụ thuộc vào nơi task đang chờ hoặc đang chạy. Một task đang chờ `Semaphore.acquire()` sẽ đáp ứng interrupt theo hợp đồng; một task đang xử lý thư viện I/O không interruptible có thể không dừng ngay.

**Semaphore không có thao tác `close()` tổng quát để đánh thức mọi waiter và chặn acquire mới.** Khi shutdown service, component cần có chính sách riêng:

1. Ngừng nhận task mới vào pipeline.
2. Hủy/interrupt công việc đang chờ theo deadline.
3. Cho công việc đang giữ permit kết thúc hoặc escalate có kiểm soát.
4. Bảo đảm mọi release cuối cùng hoàn tất, không tự tăng permit để “đánh thức” waiter nếu điều đó vô tình cho phép công việc mới vào vùng giới hạn.
5. Đảm bảo nguồn tài nguyên thực được đóng **sau** khi các task không còn dùng chúng, hoặc có kế hoạch cleanup cho trường hợp task không dừng.

Nếu cần giám sát shutdown, phân biệt số task đang chờ, số permit hiện có, số task đang giữ permit và trạng thái thực tế của connection/resource. `availablePermits()` một mình không cho biết đã an toàn để đóng mọi tài nguyên.

## 14. Fairness không có nghĩa không bao giờ starvation

Một semaphore fair giúp giảm việc một waiter bị vượt mặt lặp đi lặp lại, nhưng **không bảo đảm** từng task sẽ hoàn thành trong một thời gian cụ thể. Ví dụ một task giữ permit quá lâu, OS scheduler tạm ngừng worker, hay API `tryAcquire()` liên tục barging, vẫn có thể tạo độ trễ lớn.

Theo dõi:

- **Wait time** để acquire permit, ví dụ p50/p95/p99;
- số lượng task đang chờ (`getQueueLength()` là **ước lượng**, không phải số chính xác để quyết định admission);
- tỷ lệ timed `tryAcquire` thất bại và reject;
- **hold duration** từ sau acquire đến release;
- số permit tối đa, số đang dùng, tài nguyên downstream thực sự đang active;
- callback/exception khiến release bị bỏ sót;
- saturation ở queue/worker khác.

Nên tránh `if (sem.getQueueLength() == 0) ...` như điều kiện quyết định an toàn: nó chỉ là một ước lượng tại thời điểm quan sát. Nếu muốn fail-fast, dùng `tryAcquire()` và quyết định rõ khi false.

## 15. Những lỗi phổ biến

| Lỗi | Hậu quả | Cách xử lý |
|---|---|---|
| `release()` khi `acquire()` chưa thành công | Permit tăng thừa, phá giới hạn concurrency | Đặt acquire trước try/finally hoặc dùng cờ acquired |
| Quên release khi công việc ném exception | Rò permit; nhiều waiter chờ mãi | Release trong finally sau acquire thành công |
| Gọi `availablePermits() > 0` rồi tin đã giữ slot | Race, kiểm tra và dùng không nguyên tử | Dùng `tryAcquire()` |
| `tryAcquire()` trả false mà vẫn gọi downstream | Vượt giới hạn | Không vào vùng hạn chế nếu không có permit |
| Dùng `acquireUninterruptibly()` cho tác vụ cần dừng | Thread kẹt khi shutdown | Dùng acquire interruptible hoặc timed |
| Dùng Semaphore để bảo vệ biến dùng chung như lock có ownership | Side effect và lỗi release từ thread khác | Dùng `ReentrantLock` nếu cần ownership |
| Dùng `Semaphore(N)` mà mặc định mọi truy cập shared state an toàn | Có thể vẫn có data race giữa N task | Đồng bộ state hoặc chia dữ liệu |
| Giới hạn N permit mỗi instance mà tưởng là N toàn hệ thống | Nhiều instance cùng vượt capacity downstream | Phân chia ngân sách hoặc giới hạn tại downstream |
| Giữ permit trong lúc chờ queue/resource khác | Giảm hiệu suất hoặc deadlock | Rút ngắn vùng giữ permit, thống nhất thứ tự acquire |
| Nghĩ fairness=true bảo đảm thứ tự tuyệt đối mọi API | Không xét barging của `tryAcquire()` | Hiểu điểm ordering và overload timeout |
| Chỉ dùng Semaphore mà không giới hạn backlog | Thread/task đang đợi tăng không kiểm soát | Kết hợp bounded queue, rejection và deadline |
| Tin `release()` chỉ có thể do owner gọi | Không có ownership, dễ release sai | Kiểm soát bằng API wrapper và finally |

## 16. Bài toán thực tế: chọn permit và backpressure

Một dịch vụ chỉ cho tối đa **8 outbound call đồng thời trên một JVM**:

1. Tạo `Semaphore(8)` dùng chung cho **tất cả code path** gọi tài nguyên đó.
2. Ở boundary của request, áp **deadline toàn request** và dùng `tryAcquire(remainingTime, unit)`.
3. Nếu false, trả kết quả overload hoặc defer công việc theo policy rõ; **không** gọi downstream.
4. Khi acquire thành công, đo **thời gian giữ permit**, gọi downstream có timeout I/O, release trong finally.
5. Nếu ingress lớn hơn năng lực xử lý bền vững, đặt bounded queue/admission và backpressure ở đầu vào.
6. Nếu triển khai 4 instance, kiểm tra tổng budget downstream: 8 × 4 = 32 call có thể đồng thời. Nếu downstream chỉ cho 20, quota 8 mỗi instance là quá lớn.

**Giới hạn concurrency khác giới hạn rate.** Với 8 permit và mỗi call mất 10 ms, tốc độ có thể cao hơn nhiều so với 8 request/giây. Nếu API giới hạn 100 request/phút, một semaphore đơn thuần không đủ: cần rate limiter riêng.

**Nguyên tắc quan trọng:** permission boundary phải bao trùm phần thật sự sử dụng tài nguyên, không bao luôn preprocessing dài không cần thiết. Đồng thời nếu release quá sớm trước khi transaction/I/O thực sự hoàn tất, giới hạn không còn ý nghĩa.

## 17. Checklist để áp dụng đúng

Trước khi dùng Semaphore, hãy trả lời:

1. **Tài nguyên hữu hạn nào cần bảo vệ?** Connection, outbound request, thiết bị, task CPU-bound hay bộ nhớ?
2. Số permit có dựa trên năng lực tài nguyên thật không? Giới hạn theo JVM hay toàn hệ thống?
3. Người gọi có thể chờ bao lâu? Chọn acquire, tryAcquire hay timed tryAcquire?
4. Mọi đường đi đều có **đúng một release sau một acquire thành công** chưa?
5. Có khả năng nested acquire cùng semaphore, nhiều semaphore hoặc deadlock vòng chờ không?
6. Có cần fairness không? Có hiểu `tryAcquire()` không timeout có thể barging không?
7. Có bounded queue, policy overload, retry budget và deadline hay semaphore đang che giấu backlog?
8. Task đang giữ permit có timeout I/O và cleanup hợp lý khi exception/interrupt không?
9. Có đo wait time, hold time, rejection và downstream saturation không?
10. Đã test trường hợp mọi permit bị giữ, timeout, interrupt, double release và shutdown chưa?

**Tóm tắt:** Java `Semaphore` giới hạn **số tác vụ đồng thời được phép đi vào một vùng sử dụng tài nguyên** bằng cơ chế permit. Hãy **acquire trước – làm việc trong try – release trong finally**, chọn timeout theo deadline, hiểu fairness/ownership và kết hợp bounded queue/backpressure khi lưu lượng đến vượt khả năng xử lý. Semaphore giúp tránh quá tải đồng thời, nhưng không tự tạo tài nguyên, không tự bảo vệ shared state và không thay thế rate limiting hay quản lý lifecycle.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronizers-005">Exchanger&lt;T&gt; trao đổi dữ liệu giữa hai luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Exchanger<T> trao đổi dữ liệu giữa hai luồng trong Java

`Exchanger<T>` là một **điểm gặp gỡ đồng bộ (rendezvous)** trong `java.util.concurrent`: một thread mang một đối tượng tới `exchange()`, thread khác cũng tới điểm đó với một đối tượng của nó; khi **ghép thành công một cặp**, mỗi bên nhận **đối tượng của bên kia** và tiếp tục. Nếu chỉ một bên tới, bên đó **đợi** cho tới khi có partner, bị interrupt hoặc hết hạn timeout nếu dùng overload có thời hạn.

Ví dụ:

```text
Thread A                         Thread B
--------                         --------
có "APPLE"                       có "BANANA"
    |                                |
    +--> exchanger.exchange("APPLE") |
                                     +--> exchanger.exchange("BANANA")
                   GẶP NHAU
                  /         \
A nhận "BANANA" <-           -> B nhận "APPLE"
    |                                |
  tiếp tục                         tiếp tục
```

Đây **không phải queue tích lũy nhiều item**, không phải broadcast một item cho mọi consumer, cũng không phải "gửi trước rồi quên". Hai phía phải tham gia vào **cùng một lần trao đổi thành công** để cả hai nhận được object đối tác. Công cụ rất phù hợp khi **hai giai đoạn của pipeline cần bàn giao cho nhau hai buffer**: producer chuyển buffer đầy và nhận lại buffer trống của consumer, giúp tái sử dụng vùng nhớ thay vì cấp phát liên tục.

Bài Knowledge này thuộc **Synchronizers** trong **Java concurrency nói chung**. Ví dụ dùng Java SE 17+, không dựa vào Spring Framework.

## 1. Bản chất của `Exchanger<T>`

`Exchanger<V>` là một class đồng bộ hoá chứ không phải `Collection`, `Queue` hoặc `Future`.

- **Hai chiều:** hai thread trao đổi, mỗi bên đưa vào một giá trị và nhận giá trị từ bên kia.
- **Ghép cặp:** nếu nhiều thread cùng gọi trên một `Exchanger`, runtime có thể ghép chúng thành những **cặp**; không có danh tính partner cố định theo class.
- **Đồng bộ tại điểm trao đổi:** với một cặp thành công, mỗi bên chỉ đi tiếp sau khi phần trao đổi đã được phối hợp với phía kia.
- **Có blocking:** nếu không có partner, `exchange()` không timeout có thể chờ mãi; bản có timeout cho phép giới hạn thời gian.
- **Có interruption:** các overload khai báo `InterruptedException`; đoạn code gọi phải propagate hoặc xử lý hủy.
- **Không lưu lịch sử:** không có API `peek()`, `size()`, `take()` hay `drain()`. Sau một lần trao đổi, object đã thuộc luồng xử lý tiếp theo của phía nhận theo giao thức ứng dụng.
- **Không tự tạo thread:** ứng dụng vẫn phải dùng `Thread`, `ExecutorService` hoặc cơ chế thực thi phù hợp.

Tên generic `T` hay `V` chỉ là tên type parameter. `Exchanger<String>` trao đổi references tới `String`; `Exchanger<List<Integer>>` trao đổi references tới list. Nó không clone hoặc serialize object.

## 2. Chữ ký API và cách import

Dùng:

```java
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
```

Các member chính trong Java SE 17:

```java
public Exchanger();

public V exchange(V x) throws InterruptedException;

public V exchange(V x, long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException;
```

Đây là **chữ ký minh họa**, không phải mã chạy độc lập; `V` là type parameter của class.

| Phương thức | Hành vi |
| --- | --- |
| `new Exchanger<T>()` | Tạo một điểm trao đổi; chưa có thread nào chờ |
| `exchange(myObject)` | Chờ một thread khác tới để swap; không đặt giới hạn thời gian |
| `exchange(myObject, timeout, unit)` | Chờ tối đa khoảng timeout cho một partner; có thể ném `TimeoutException` |
| Giá trị trả về | Object mà **thread đối tác** đã cung cấp ở lần exchange thành công |

`Exchanger` không cung cấp `send` và `receive` tách rời. **Cả hai thread gọi cùng một method** để vừa gửi vừa nhận. Vì vậy cách tư duy phù hợp là "đổi" chứ không phải "đẩy item vào hàng đợi".

## 3. Trình tự của một lần trao đổi thành công

Giả sử A và B cùng chia sẻ đúng một instance `Exchanger<Message>`:

1. A tạo `messageA` và gọi `exchange(messageA)`.
2. Nếu chưa có partner, A chờ tại điểm trao đổi.
3. B tạo `messageB`, gọi `exchange(messageB)` trên **chính instance đó**.
4. Một cặp A/B được ghép; tham chiếu được swap theo hợp đồng.
5. A nhận object B đã đưa, B nhận object A đã đưa.
6. Hai thread tiếp tục độc lập, có thể ở những thời điểm khác nhau do scheduler.

Không có cam kết A chạy tiếp trước B hay ngược lại. Cũng không có cam kết hai bên thức dậy **đúng cùng một nanosecond**. Kết quả swap là điều được bảo đảm; thứ tự log và thời gian wake-up không phải phần của contract.

Sơ đồ trạng thái khái niệm:

```text
A arrives --------\
                   >--- successful pairing ---> both return peer item
B arrives --------/

A arrives --- waits --- timeout -------> TimeoutException
A arrives --- waits --- interrupt -----> InterruptedException
A arrives --- waits --- forever? ------> possible without partner or cancellation
```

Các đường timeout/interrupt không phải một giao dịch chuyển item thành công. Application phải quyết định xử lý value vẫn giữ ở bên gọi thế nào và có retry hay kết thúc.

## 4. Ví dụ Java 17+: hai thread trao đổi String

Lưu thành `BasicExchangerDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BasicExchangerDemo {
    public static void main(String[] args)
            throws InterruptedException {
        Exchanger<String> exchanger = new Exchanger<>();
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<String> aGot = new AtomicReference<>();
        AtomicReference<String> bGot = new AtomicReference<>();

        Thread a = new Thread(() -> {
            try {
                start.await();
                aGot.set(exchanger.exchange(
                        "APPLE", 5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.TimeoutException e) {
                throw new IllegalStateException(e);
            }
        }, "A");

        Thread b = new Thread(() -> {
            try {
                start.await();
                bGot.set(exchanger.exchange(
                        "BANANA", 5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.TimeoutException e) {
                throw new IllegalStateException(e);
            }
        }, "B");

        a.start();
        b.start();
        start.countDown();

        a.join();
        b.join();

        System.out.println("A received: " + aGot.get());
        System.out.println("B received: " + bGot.get());
    }
}
```

Chạy trên JDK 17+:

```bash
javac BasicExchangerDemo.java
java BasicExchangerDemo
```

**Stdout trên đường chạy thành công (hai thread gặp nhau trong thời hạn):**

```text
A received: BANANA
B received: APPLE
```

Tại sao không in từ trong worker? Nếu để A/B cùng `System.out.println`, thứ tự hai dòng phụ thuộc lịch thread. Code ghi kết quả vào `AtomicReference`, dùng `Thread.join()` chờ cả hai xong, rồi main in theo thứ tự cố định.

`CountDownLatch` cho hai thread khởi hành gần nhau, nhưng không bảo đảm *thời điểm đến exchange chính xác*. Timeout 5 giây là giới hạn tránh treo lâu nếu hệ thống bị lỗi; trên môi trường bị tạm dừng quá thời hạn, demo có thể đi vào nhánh timeout thay vì stdout thành công.

**Lưu ý kiểm thử:** bản demo dùng exception handler đơn giản để làm nổi bật happy path; code production nên thu thập lỗi từ cả hai thread bằng `Future`, completion result hoặc handler có quản lý thay vì chỉ ném unchecked exception trên worker.

## 5. Điều gì xảy ra khi không có partner?

Với:

```java
String peer = exchanger.exchange("HELLO");
```

nếu chỉ một thread duy nhất từng gọi `exchange` trên instance đó, thread có thể **đợi vô hạn**. Điều này thường gặp khi:

- Thread đối tác chưa được start.
- Thread đối tác đã kết thúc vì exception.
- Hai phía dùng **hai instance `Exchanger` khác nhau**.
- Một phía gọi lần thứ hai, phía kia chỉ gọi một lần rồi thoát.
- Một phía đang kẹt ở lock/I/O trước khi chạm `exchange`.
- Caller cho rằng `exchange` chỉ là `offer` vào queue và không cần receiver.

Đừng dùng `Thread.sleep` để đoán "đối tác chắc đã sẵn". Nếu việc chờ vô hạn là không chấp nhận được, dùng overload có timeout và thiết kế giao thức xử lý failure/interrupt rõ.

## 6. Overload có timeout

Chữ ký:

```java
T peer = exchanger.exchange(
        myValue, timeout, TimeUnit.MILLISECONDS);
```

Đây là snippet minh họa với biến kiểu `T`/`myValue` đã được định nghĩa trong method.

Các kết quả có thể xảy ra:

- Một đối tác được ghép trong thời hạn: trả object của partner.
- Chờ vượt thời hạn mà chưa ghép: ném `TimeoutException`.
- Thread nhận interrupt lúc bắt đầu hoặc đang chờ: ném `InterruptedException`; interrupt status được **xóa** khi ngoại lệ được ném theo hợp đồng.
- Nếu `timeout <= 0`, lời gọi **không chờ** để partner mới tới. Nó vẫn có thể ghép nếu có thread khác đã chờ sẵn; vì vậy **không** tự động kết luận mọi lần gọi zero-timeout đều ném TimeoutException.

Timeout của A không phải lệnh đóng `Exchanger`, không có tác dụng tự động interrupt B và không truyền failure cho những thread khác. Mỗi phía cần có chính sách nhất quán về việc dừng/khởi động lại vòng trao đổi.

## 7. Ví dụ Java 17+: một thread đơn độc bị timeout

Lưu thành `ExchangerTimeoutDemo.java`:

```java
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ExchangerTimeoutDemo {
    public static void main(String[] args)
            throws InterruptedException {
        Exchanger<String> exchanger = new Exchanger<>();
        boolean timedOut = false;

        try {
            exchanger.exchange("ALONE", 30, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            timedOut = true;
        }

        System.out.println("Timed out: " + timedOut);
    }
}
```

```bash
javac ExchangerTimeoutDemo.java
java ExchangerTimeoutDemo
```

Stdout chính xác nếu main không bị interrupt từ bên ngoài:

```text
Timed out: true
```

Chương trình không có thread partner nào, nên `exchange` không thể ghép thành công. Sau thời gian chờ, method ném `TimeoutException`; biến nhận true và ứng dụng vẫn tiếp tục.

**Timeout không có nghĩa object "ALONE" đã được gửi tới một queue và bị mất.** Nó chưa được swap thành công. Nếu ứng dụng muốn retry trao đổi cùng payload, phải xác định liệu partner tương lai sẽ dùng cùng phiên làm việc và có nguy cơ ghép nhầm lượt hay không.

## 8. `InterruptedException` và phục hồi interrupt flag

`Exchanger.exchange` là một lời gọi có thể bị interrupt. Nếu thread bị ngắt khi đang chờ, hoặc có interrupt status lúc vào method theo hợp đồng, nó ném `InterruptedException` và **xóa interrupt status**.

Nếu method cho phép `throws InterruptedException`, thường nên **truyền ngoại lệ** để tầng gọi thực hiện cancellation policy:

```java
static String swap(
        Exchanger<String> exchanger, String value)
        throws InterruptedException {
    return exchanger.exchange(value);
}
```

Đây là method minh họa.

Ở ranh giới không thể thêm checked exception như `Runnable.run()`, mẫu thông dụng:

```java
try {
    String received = exchanger.exchange(message);
    handle(received);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
```

Đây là snippet minh họa với `message` và `handle` thuộc ứng dụng. **Restore rồi dừng công việc phù hợp**, không chỉ đặt lại flag rồi lặp `exchange` vô hạn.

Đừng catch rỗng:

```java
catch (InterruptedException ignored) {
    // Sai: xóa tín hiệu hủy và tiếp tục bình thường.
}
```

Cách đó có thể khiến worker không dừng khi executor shutdown/cancel, nhất là khi phía đối tác đã biến mất.

## 9. Ví dụ Java 17+: interrupt khi đang tìm partner

Lưu thành `ExchangerInterruptDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Exchanger;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExchangerInterruptDemo {
    public static void main(String[] args)
            throws InterruptedException {
        Exchanger<String> exchanger = new Exchanger<>();
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean caught = new AtomicBoolean();
        AtomicBoolean clearedInCatch = new AtomicBoolean();
        AtomicBoolean restored = new AtomicBoolean();

        Thread worker = new Thread(() -> {
            try {
                entered.countDown();
                exchanger.exchange("NEVER_MATCHED");
            } catch (InterruptedException e) {
                caught.set(true);
                clearedInCatch.set(
                        !Thread.currentThread().isInterrupted());
                Thread.currentThread().interrupt();
                restored.set(Thread.currentThread().isInterrupted());
            }
        }, "waiting-worker");

        worker.start();
        entered.await();

        // Không có partner, worker chỉ thoát qua interruption.
        worker.interrupt();
        worker.join();

        System.out.println("InterruptedException: " + caught.get());
        System.out.println("Flag cleared in catch: "
                + clearedInCatch.get());
        System.out.println("Flag restored: " + restored.get());
    }
}
```

```bash
javac ExchangerInterruptDemo.java
java ExchangerInterruptDemo
```

Stdout chính xác:

```text
InterruptedException: true
Flag cleared in catch: true
Flag restored: true
```

Tại sao kết quả đáng tin:

1. Worker gửi `entered` ngay trước lời gọi `exchange`.
2. Nếu main interrupt khi worker đã chờ, `exchange` ném exception. Nếu interrupt tới **trước** khi worker thực sự vào exchange, method vẫn phát hiện interrupt flag ở điểm vào và ném.
3. Không có thread thứ hai nên không xảy ra ghép cặp bất ngờ.
4. Catch quan sát flag đã được xóa theo API, rồi gọi `Thread.currentThread().interrupt()`.
5. Main `join` chờ worker kết thúc trước khi đọc các `AtomicBoolean`.
6. Không dựa vào `worker.isInterrupted()` sau khi worker đã kết thúc để chứng minh restoration; code đo **ngay trong worker**.

Tính hợp tác hủy ở đây thuộc về **worker đang chờ**. Gọi `worker.interrupt()` không có nghĩa mọi thread đang dùng cùng exchanger đều bị interrupt.

## 10. Hai vòng trao đổi liên tiếp

Một `Exchanger` có thể được tái sử dụng nhiều lần:

```text
Round 1:
A gửi A1; B gửi B1; A nhận B1; B nhận A1

Round 2:
A gửi A2; B gửi B2; A nhận B2; B nhận A2
```

Nếu **đúng hai thread** tham gia, cả hai cùng gọi theo thứ tự lượt nhất quán, mỗi lời gọi có thể ghép với lời gọi tương ứng còn chờ của phía kia. Không cần tạo `new Exchanger` cho từng vòng.

Nhưng nếu một phía bỏ một lượt, retry không phối hợp hoặc xuất hiện thread thứ ba, **một cuộc gặp sau có thể không còn mang ý nghĩa nghiệp vụ cùng round**. Exchanger không tự mang "sequence number" hay kiểm tra rằng hai payload thuộc cùng phiên.

Giải pháp cho giao thức nhiều vòng:

- Đưa `roundId` hoặc correlation ID vào payload.
- Quy định mỗi phía trao đổi chính xác một lần mỗi round.
- Khi timeout/interrupt, xác định rõ **hủy cả phiên** hay retry có đồng bộ.
- Không gửi lại âm thầm nếu một phía có thể đã đi sang round kế.
- Nếu cần match theo request ID bất kỳ, Exchanger đơn không phải cấu trúc phù hợp; dùng map/future/queue theo correlation key.

## 11. Ví dụ Java 17+: hai lượt trao đổi có kết quả cố định

Lưu thành `ExchangerRoundsDemo.java`:

```java
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ExchangerRoundsDemo {
    public static void main(String[] args) throws InterruptedException {
        Exchanger<String> exchanger = new Exchanger<>();
        AtomicReference<String> aResults = new AtomicReference<>();
        AtomicReference<String> bResults = new AtomicReference<>();

        Thread a = new Thread(() -> {
            try {
                String first = exchanger.exchange(
                        "A1", 5, TimeUnit.SECONDS);
                String second = exchanger.exchange(
                        "A2", 5, TimeUnit.SECONDS);
                aResults.set(first + "," + second);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.TimeoutException e) {
                throw new IllegalStateException(e);
            }
        });

        Thread b = new Thread(() -> {
            try {
                String first = exchanger.exchange(
                        "B1", 5, TimeUnit.SECONDS);
                String second = exchanger.exchange(
                        "B2", 5, TimeUnit.SECONDS);
                bResults.set(first + "," + second);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.TimeoutException e) {
                throw new IllegalStateException(e);
            }
        });

        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("A got: " + aResults.get());
        System.out.println("B got: " + bResults.get());
    }
}
```

```bash
javac ExchangerRoundsDemo.java
java ExchangerRoundsDemo
```

Stdout khi hai thread phối hợp thành công trong timeout:

```text
A got: B1,B2
B got: A1,A2
```

Chỉ hai thread dùng chung instance, mỗi bên phải hoàn tất round 1 trước khi có thể gọi round 2. Không có thread thứ ba làm nhiễu pairing, nên các kết quả được xác định theo round. Nếu môi trường bị scheduler đình trệ quá 5 giây hoặc một worker thất bại, đường lỗi phải được xử lý bởi ứng dụng thực.

## 12. Bàn giao reference, không copy nội dung

Khi:

```java
List<Integer> received = exchanger.exchange(myBuffer);
```

thao tác trao đổi **tham chiếu** tới object. Nó không deep-copy list, không copy byte array, không biến object thành immutable. Nếu A đã gửi một `ArrayList` cho B nhưng A vẫn giữ reference cũ và tiếp tục `add` trong lúc B đọc, **vẫn có data race**.

Hãy dùng quy tắc **ownership transfer**:

1. A sở hữu buffer A trước exchange và được phép ghi vào nó.
2. A gọi `exchange(bufferA)`, không sửa buffer A sau khi đã đưa cho exchanger nếu phía kia có thể truy cập.
3. Khi exchange thành công, A sở hữu **buffer nhận từ B**; B sở hữu buffer A.
4. B chỉ đọc/ghi buffer được nhận theo giao thức đã thống nhất.
5. Những reference cũ không được dùng lại để sửa object đã bàn giao.

Nếu cần giữ ownership cùng lúc cho nhiều thread, phải dùng immutable data, snapshot copy hoặc synchronization riêng. `Exchanger` không phải "bộ khóa" cho mọi thao tác trên object được swap.

## 13. Java Memory Model: happens-before qua lần swap

Javadoc Java SE 17 ghi rõ hiệu ứng memory consistency:

> Với một cặp thread trao đổi thành công, **các hành động trước `exchange()` ở mỗi bên happens-before các hành động sau khi trở về từ `exchange()` tương ứng ở bên kia**.

Điều này rất quan trọng khi trao đổi **mutable buffer**:

```text
Thread A                               Thread B
--------                               --------
buffer.values[0] = 42
buffer.ready = true
    |
    |  Exchanger.exchange(buffer)
    +-------------------------------> receives buffer
                                        |
                                        v
                                  read buffer.ready
                                  read buffer.values[0]
```

Nếu A hoàn tất ghi **trước** exchange thành công và B đọc buffer sau exchange, JMM cung cấp quan hệ visibility cần thiết trên đường trao đổi đó. Không phải đặt mọi field trong buffer thành `volatile` chỉ để công bố những ghi *trước bàn giao* theo giao thức.

Tuy vậy:

- **Happens-before không tự khóa object sau trao đổi.** Nếu A tiếp tục sửa buffer sau khi đã đưa nó cho B, vẫn có rủi ro cạnh tranh.
- Quan hệ này áp dụng cho **cặp exchange thành công**; một lần timeout không thể dùng làm bằng chứng rằng peer đã nhận dữ liệu.
- Dữ liệu được ghi **sau** khi A đã thực hiện exchange không tự trở thành "đã có trước khi B đọc".
- Nếu buffer chứa reference tới những object mà thread thứ ba tiếp tục sửa, phải xem xét synchronization của những object đó riêng.
- Không suy ra một "global happens-before" giữa mọi thread từng dùng cùng exchanger ở các round khác nhau.

## 14. Ví dụ Java 17+: công bố payload mutable bằng exchange

Lưu thành `ExchangerVisibilityDemo.java`:

```java
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ExchangerVisibilityDemo {
    static final class Payload {
        int number;  // không cần volatile cho publication ở ví dụ này
        String text;
    }

    public static void main(String[] args) throws InterruptedException {
        Exchanger<Payload> exchanger = new Exchanger<>();
        AtomicInteger observed = new AtomicInteger(-1);

        Thread producer = new Thread(() -> {
            Payload payload = new Payload();
            payload.number = 42;
            payload.text = "ready";

            try {
                exchanger.exchange(payload, 5, TimeUnit.SECONDS);
                // Không chạm lại payload sau khi đã bàn giao.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.TimeoutException e) {
                throw new IllegalStateException(e);
            }
        });

        Thread consumer = new Thread(() -> {
            try {
                Payload received = exchanger.exchange(
                        new Payload(), 5, TimeUnit.SECONDS);

                if ("ready".equals(received.text)) {
                    observed.set(received.number);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.TimeoutException e) {
                throw new IllegalStateException(e);
            }
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        System.out.println("Observed: " + observed.get());
    }
}
```

```bash
javac ExchangerVisibilityDemo.java
java ExchangerVisibilityDemo
```

Stdout nếu exchange thành công:

```text
Observed: 42
```

Đọc `number` và `text` không tạo data race trong cấu hình demo: producer ghi trước bàn giao, consumer đọc sau khi nhận; producer không sửa lại object. `AtomicInteger` ở đây dùng làm kênh ghi kết quả cho main. Main đợi `join` để chắc hai thread đã rời công việc.

Nếu thêm `producerPayload.number++` **sau exchange** trong producer trong lúc consumer vẫn đọc, lập luận không còn đúng: object đã được chia sẻ để biến đổi mà không có ownership/synchronization phù hợp.

## 15. Use case kinh điển: producer đổi buffer đầy lấy buffer trống

Một pipeline có hai giai đoạn:

- **Producer:** lấp đầy buffer từ dữ liệu đầu vào.
- **Consumer:** xử lý buffer đầy, làm trống rồi trao đổi trở lại.

`Exchanger<Buffer>` giúp hai bên bàn giao theo cặp:

```text
Producer                              Consumer
--------                              --------
buffer P = empty                      buffer C = empty
fill P
  |                                      |
  +---------- exchange(P) ---------------+ exchange(C)
  |                                      |
gets C = empty                          gets P = full
fill C                                  process P; clear P
  |                                      |
  +---------- exchange(C) ---------------+ exchange(P)
  |                                      |
gets P = empty                          gets C = full
...
```

Lợi ích:

1. Mỗi phía có **một buffer riêng đang sở hữu** giữa hai lần swap.
2. Không cần cấp phát buffer mới cho mỗi batch nếu tái sử dụng phù hợp.
3. Producer bị **backpressure** tự nhiên khi chờ consumer sẵn sàng đổi buffer.
4. Consumer không đọc một buffer đang bị producer sửa, **nếu ownership protocol được giữ đúng**.
5. Không cần một shared mutable `ArrayList` với hai thread liên tục thêm/xóa trên cùng object.

Nhược điểm: hai giai đoạn bị **ghép nhịp với nhau**. Nếu consumer rất chậm hoặc thất bại, producer có thể đợi lâu. Nó không phải queue cho phép producer đẩy 100 batches rồi đi tiếp.

## 16. Ví dụ Java 17+: producer–consumer đổi hai `ArrayList`

Lưu thành `ExchangerBufferDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ExchangerBufferDemo {
    public static void main(String[] args) throws InterruptedException {
        Exchanger<List<Integer>> exchanger = new Exchanger<>();
        AtomicReference<List<Integer>> result = new AtomicReference<>();

        Thread producer = new Thread(() -> {
            List<Integer> myBuffer = new ArrayList<>();
            try {
                for (int n = 1; n <= 3; n++) {
                    myBuffer.add(n);

                    // Gửi buffer đã chứa một item, lấy buffer trống từ consumer.
                    myBuffer = exchanger.exchange(
                            myBuffer, 5, TimeUnit.SECONDS);

                    // Đây là buffer vừa được phía kia giao; nó đang trống
                    // theo giao thức của ví dụ.
                    myBuffer.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.TimeoutException e) {
                throw new IllegalStateException(e);
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            List<Integer> myBuffer = new ArrayList<>();
            List<Integer> collected = new ArrayList<>();

            try {
                for (int round = 0; round < 3; round++) {
                    // Gửi buffer trống, nhận buffer đầy.
                    myBuffer = exchanger.exchange(
                            myBuffer, 5, TimeUnit.SECONDS);

                    collected.addAll(myBuffer);
                    myBuffer.clear();
                }
                result.set(List.copyOf(collected));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.TimeoutException e) {
                throw new IllegalStateException(e);
            }
        }, "consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        System.out.println("Consumed: " + result.get());
    }
}
```

```bash
javac ExchangerBufferDemo.java
java ExchangerBufferDemo
```

Stdout nếu cả ba round thành công:

```text
Consumed: [1, 2, 3]
```

**Chứng minh dữ liệu và ownership:**

1. Hai phía khởi tạo hai `ArrayList` khác nhau.
2. Producer thêm đúng một số vào **buffer mà nó đang sở hữu**, rồi đổi cho consumer.
3. Consumer nhận buffer chứa số đó, copy vào `collected`, rồi `clear()` trước lần trao đổi kế tiếp.
4. Producer nhận buffer phía consumer đã đưa và chuẩn bị điền số tiếp theo.
5. Mỗi vòng dùng đúng một swap ở cả hai phía, nên order các số trong `collected` là 1, 2, 3, dù scheduler thay đổi.
6. `List.copyOf` tạo bản kết quả không cho sửa cấu trúc sau khi consumer hoàn tất.
7. Main dùng `join` để đợi cả hai worker và đọc kết quả.

**Điều kiện sử dụng thực tế:** ở nhánh timeout/interrupt, phải đặt policy xem buffer nào còn sở hữu ở bên nào, worker còn cần cleanup gì và có nên dừng cả pipeline. Ví dụ tập trung vào đường chạy bình thường và ghi nhận lỗi đơn giản. Đừng dùng vòng `while(true)` không deadline với Exchanger nếu peer có thể chết.

## 17. Backpressure có, nhưng không phải queue có capacity

Nếu producer có thể tạo 100 buffer trong 1 giây còn consumer chỉ xử lý 1 buffer/giây, dùng `Exchanger` khiến producer thường phải chờ consumer tại mỗi round. Cơ chế này hạn chế số buffer "đang bay" theo **giao thức hai buffer**.

Nhưng nó không tương đương `ArrayBlockingQueue<>(2)`:

- `BlockingQueue` có **storage slots** cho các item theo API `put`/`take`.
- `Exchanger` là **rendezvous**: mỗi thread đều phải cung cấp item và nhận item.
- Queue hỗ trợ một chiều producer gửi item, consumer nhận; Exchanger hỗ trợ đổi hai chiều trong cùng synchronization point.
- Queue có thể có nhiều producers/consumers và policy dung lượng cụ thể; Exchanger ghép cặp chờ, không quản lý một danh sách item đã gửi.
- Nếu muốn producer gửi mà không cần consumer có item riêng để đổi, queue thường dễ mô hình hóa hơn.
- Nếu cần gửi kèm trả lại buffer dùng lại, Exchanger có thể giảm chuyện copy/allocation và làm rõ ownership.

Chọn công cụ dựa trên **giao thức truyền dữ liệu**, không chỉ vì cả hai đều có blocking.

## 18. `Exchanger` so với `SynchronousQueue`

Javadoc mô tả Exchanger giống một biến thể **hai chiều** của `SynchronousQueue`.

| Tiêu chí | `Exchanger<T>` | `SynchronousQueue<T>` |
| --- | --- | --- |
| Phương thức chính | `exchange(item)` | `put(item)` / `take()` |
| Hướng dữ liệu | Cả hai thread gửi và nhận trong một lần | Một thread put, một thread take |
| Cặp đồng bộ | Hai callers của exchange được ghép | Producer và consumer rendezvous |
| Có lưu backlog không? | Không | Không, queue dung lượng 0 |
| Trả object của đối tác? | Có | Producer `put` không nhận payload ngược từ consumer |
| Có timeout/interrupt? | Có | Có ở các overload thích hợp |
| Có nên dùng cho request–response đa client? | Chỉ khi pairing/round protocol được kiểm soát | Thường cần thêm correlation/future/queue phù hợp |

Cả hai là công cụ synchronization, không tự tạo durability hay bảo đảm processing của remote service.

Nếu chỉ muốn **handoff một chiều**, `SynchronousQueue` thường làm contract dễ đọc hơn. Nếu muốn **swap buffer hai chiều**, `Exchanger` là lựa chọn trực tiếp.

## 19. `Exchanger` so với `CyclicBarrier`, `CountDownLatch`, `Phaser`

Các synchronizers khác nhau về mục đích:

- **`CountDownLatch`:** một hoặc nhiều thread chờ tới khi số đếm về 0. Không swap dữ liệu; không reset được.
- **`CyclicBarrier`:** N bên chờ nhau ở một mốc; có thể có barrier action. Không mặc định trao đổi payload hai chiều.
- **`Phaser`:** quản lý nhiều phase và đăng ký/rút bớt parties. Phù hợp khi số bên tham gia có thể thay đổi.
- **`Exchanger`:** **ghép hai thread** và trao đổi object ngay tại điểm gặp; không đếm đủ N participants cho một collective barrier.
- **`Semaphore`:** giới hạn số permit, không trực tiếp trao đổi hai object.

Nếu muốn **ba thread cùng gặp nhau rồi tiếp tục**, `CyclicBarrier(3)` hoặc `Phaser` thường đúng hơn Exchanger. Exchanger có thể ghép hai trong ba thread và để thread thứ ba chờ, dẫn đến treo nếu không có thread thứ tư.

## 20. Nhiều hơn hai thread dùng cùng một `Exchanger`

Mặc dù bài học thường có đúng hai thread, một `Exchanger` có thể được nhiều thread gọi. Runtime ghép **từng cặp** cho một lần swap thành công.

Ví dụ có bốn thread A, B, C, D cùng tới:

```text
Possible pairing 1: (A,B), (C,D)
Possible pairing 2: (A,C), (B,D)
Possible pairing 3: (A,D), (B,C)
```

Đây là **các khả năng logic**, không phải phân bố xác suất hay thứ tự được Javadoc cam kết.

Với ba thread đến và không có ai khác, một thread có thể chờ partner mãi nếu không timeout/interrupt. Khi muốn truyền tới **đúng B**, Exchanger dùng chung bởi A, B, C, D **không phải hệ thống định tuyến có địa chỉ**. Chọn instance riêng cho từng cặp cố định hoặc dùng một giao thức có correlation key.

Không nên xem Exchanger như một "broadcast channel" mà mọi bên đều nhận được payload của nhau: mỗi exchange ghép **hai** callers, không multicast.

## 21. Ví dụ Java 17+: bốn thread ghép thành hai cặp

Lưu thành `ExchangerMultiplePairsDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ExchangerMultiplePairsDemo {
    public static void main(String[] args) throws InterruptedException {
        Exchanger<String> exchanger = new Exchanger<>();
        CountDownLatch begin = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger differentPeer = new AtomicInteger();

        Thread[] workers = new Thread[4];

        for (int i = 0; i < workers.length; i++) {
            final String mine = "T" + i;

            workers[i] = new Thread(() -> {
                try {
                    begin.await();
                    String peer = exchanger.exchange(
                            mine, 5, TimeUnit.SECONDS);

                    succeeded.incrementAndGet();
                    if (!mine.equals(peer)) {
                        differentPeer.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.TimeoutException e) {
                    throw new IllegalStateException(e);
                }
            }, mine);
            workers[i].start();
        }

        begin.countDown();
        for (Thread thread : workers) {
            thread.join();
        }

        System.out.println("Successful threads: " + succeeded.get());
        System.out.println("Got other thread's item: "
                + differentPeer.get());
    }
}
```

```bash
javac ExchangerMultiplePairsDemo.java
java ExchangerMultiplePairsDemo
```

**Stdout trong trường hợp cả bốn thread ghép thành công trong deadline:**

```text
Successful threads: 4
Got other thread's item: 4
```

Không in ra danh tính partner cụ thể, vì pairing (T0/T1, T0/T2 hay T0/T3) **không xác định**. Kết quả logic ổn định: mỗi thread thành công nhận một item từ thread khác, không phải item chính nó gửi. Dữ liệu `mine` của từng worker là unique string; không có thread nào cố tình gửi item trùng nhau.

Nếu hệ thống bị đình trệ quá 5 giây, thread có thể timeout nên số lượng successful nhỏ hơn; bài không hứa hard scheduling deadline. Trong production, cần thu thập lỗi từ workers và phối hợp shutdown/cancellation để không bỏ sót task thất bại.

## 22. Pairing không phải FIFO, công bằng hay định tuyến

Không nên viết code dựa trên các giả định:

- Thread gọi `exchange` sớm nhất chắc ghép với thread gọi tiếp theo theo FIFO tuyệt đối.
- Thread A chắc chỉ ghép với B dù còn C, D cùng dùng exchanger.
- Sau khi một bên timeout, retry của nó luôn ghép đúng "lượt" với peer cũ.
- Một thread sẽ chắc được partner trong thời hạn cố định chỉ vì nhiều thread khác tồn tại.
- Pairing order tương ứng task ID, request ID hoặc timestamp.

`Exchanger` không cung cấp một fairness parameter như một số lock/queue. Lịch pairing và thời gian chờ có thể chịu scheduler, contention và các chi tiết runtime. Nếu cần **công bằng hoặc partner identity**, thiết kế tầng application hoặc chọn công cụ phù hợp.

Với hai threads cố định, việc mỗi thread gọi **một lần/round** dễ suy luận hơn nhiều so với một exchanger toàn cục nhận request của hàng trăm threads không có sequence protocol.

## 23. Timeout, cancel và hiện tượng lệch phiên

Tình huống:

```text
A: muốn đổi dữ liệu round 7
B: còn đang xử lý round 6

A.exchange(round7, 100ms) -> timeout
A quyết định retry round7

B sau đó mới tới exchange(round6)
A retry có thể gặp B round6, dù A tưởng B đã tới round7
```

Exchanger chỉ biết **có hai callers đang chờ**. Nó không kiểm tra các payload có cùng round ID. Do đó, **timeout của một round là vấn đề protocol**, không chỉ là một exception để log.

Một cách xử lý khi pair cố định:

1. Thỏa thuận rằng timeout/interrupt nghĩa là **hủy toàn bộ phiên**, không tự retry một phía.
2. Nếu cần retry, có handshake/sequence ID và cơ chế phục hồi trạng thái ở **cả hai bên**.
3. Không áp dụng side effect dựa trên payload vừa nhận nếu `roundId` không khớp.
4. Không dùng một exchanger chung cho các phiên độc lập nếu có nguy cơ ghép chéo.
5. Quy định timeout từng round và timeout tổng thể để worker không chờ vô hạn sau failure.
6. Dùng resource close/cancellation token phù hợp nếu worker đang block ở nơi khác trước exchange.

Đừng tưởng `exchange(x, timeout)` có "rollback protocol" giữa nhiều lần trao đổi. Nó chỉ quyết định kết quả **của một lời gọi**.

## 24. Khi một phía ném exception trước exchange

Giả sử producer gặp exception ở `fillBuffer()` và rời thread, còn consumer vẫn gọi:

```java
Buffer next = exchanger.exchange(emptyBuffer);
```

Đây là snippet minh họa với `Buffer`/`emptyBuffer` giả định. Consumer có thể **đợi mãi** vì không còn producer.

Giải pháp:

- Dùng timed `exchange` và kiểm tra timeout.
- Khi một phía thất bại, gửi **cancellation signal riêng** (token/interrupt) đến phía còn lại.
- Định nghĩa states `RUNNING`, `STOPPING`, `STOPPED` và cleanup ownership.
- Không dựa vào `finally` của worker lỗi để tự "mở exchanger": Exchanger **không có `countDown` hay `close`**.
- Nếu chỉ có hai worker, caller điều phối có thể dùng `Future` để quan sát thất bại của một bên rồi `cancel(true)` bên còn lại theo hợp đồng executor thích hợp.
- Nếu một buffer đã được chuyển quyền sở hữu, tránh hai bên cùng cleanup/mutate buffer đó khi failure xảy ra.

Mẫu callback lỗi cần chú ý: nếu một thread timeout và rời đi, thread khác có thể vẫn ở lượt exchange tương ứng và phải được wake bằng interrupt hoặc timeout riêng.

## 25. `Exchanger` có thể truyền null hay không?

Tham số generic `T` là một reference type. `Exchanger` không biến `null` thành một token có định danh nghiệp vụ; một lời gọi có thể cần truyền **không có object** theo protocol của hai bên.

Trong code muốn dễ đọc, nên ưu tiên một **message type tường minh**:

```java
record Envelope(long round, String status, String payload) {}

Exchanger<Envelope> exchanger = new Exchanger<>();
```

Đây là snippet minh họa Java 17, không có `main`. Nếu muốn biểu thị "không có payload", dùng `status` hoặc một sealed hierarchy/enum phù hợp. Tránh dùng null như tín hiệu **END** mà không document, vì receiver sẽ khó phân biệt kết thúc bình thường, thiếu dữ liệu hay lỗi logic.

Một object được swap có thể chứa null field theo hợp đồng của object đó; thread safety vẫn phụ thuộc ownership và publication. **Không dựa vào null để điều phối vòng đời**, nhất là khi có retry hoặc nhiều round.

## 26. Generics và type safety

`Exchanger<T>` xác định compile-time type của object trao đổi:

```java
Exchanger<String> strings = new Exchanger<>();
Exchanger<byte[]> byteBuffers = new Exchanger<>();
Exchanger<java.util.List<Integer>> batches = new Exchanger<>();
```

Những declaration này hợp lệ trong method hoặc field scope thích hợp. Java compiler ngăn lời gọi:

```java
strings.exchange(123); // Không biên dịch: Integer không phải String.
```

Đây là **anti-example cố ý không biên dịch**; method còn khai báo `InterruptedException`.

**Generics không deep-copy object:** `byte[]` hoặc `ArrayList` vẫn mutable. Generic type giúp giảm lỗi trộn loại payload, nhưng không thay ownership contract hay cơ chế matching theo round ID.

Nếu cần trao đổi hai **loại** dữ liệu khác nhau trong cùng cặp, đóng gói trong một common supertype/record/interface hoặc dùng wrapper:

```java
record SwapMessage(String sender, int round, byte[] payload) {}
```

Thiết kế loại message theo business contract để tránh ép cast không an toàn. Với record chứa `byte[]`, record là shallow immutable: array bên trong vẫn mutable; phải copy hoặc chuyển ownership an toàn.

## 27. Với executor, ai là người quản lý vòng đời?

`Exchanger` không tạo hay đóng thread. Nếu chạy hai `Runnable` qua `ExecutorService`, cần:

- Pool có đủ khả năng **cho cả hai phía tới exchange**. Một pool fixed single-thread chạy hai task, task thứ nhất chờ exchange và chiếm worker, task thứ hai không được chạy: có thể bị treo.
- **Quan sát lỗi của cả hai tasks**, ví dụ dùng `Future.get` hoặc result channel; ném exception trong worker không tự bảo đảm caller biết.
- Có deadline hoặc cancellation policy cho exchange.
- Nếu hủy một phía bằng `Future.cancel(true)`, phía kia vẫn cần được đánh thức/timeout nếu không có partner mới.
- `shutdownNow` thường yêu cầu interrupt các worker; không bảo đảm arbitrary blocking I/O hoặc user code dừng tức thì.
- Không chia sẻ một exchanger chung giữa nhiều application sessions mà không quản lý pairing rõ.

Ví dụ anti-pattern:

```java
ExecutorService pool = Executors.newSingleThreadExecutor();

pool.submit(() -> exchanger.exchange("A"));
pool.submit(() -> exchanger.exchange("B"));
```

Đây là snippet minh họa, cố ý bỏ phần handle checked exception. Hai task cần cùng chạy tới rendezvous, nhưng single worker bị task đầu chặn nên task thứ hai nằm trong queue. Dùng ít nhất hai worker phù hợp, hoặc thiết kế không yêu cầu hai tasks chiếm worker cùng lúc. Đừng coi việc tăng pool size là cách sửa mọi vấn đề: nếu một task không bao giờ đến exchange, thêm workers vẫn không tạo partner.

## 28. Tránh giữ khóa khi chờ `exchange()`

Một lỗi nguy hiểm:

```java
synchronized (lock) {
    exchanger.exchange(myBuffer);
}
```

Đây là snippet minh họa; thiếu xử lý `InterruptedException`.

Nếu partner phải **lấy cùng monitor `lock`** để chuẩn bị payload rồi mới exchange, sẽ có vòng phụ thuộc:

```text
Thread A: giữ lock -> chờ partner tại exchange
Thread B: chờ lock  -> không thể đến exchange
```

Đó là deadlock do tổ chức lock ordering, không phải lỗi riêng của Exchanger. Tương tự với `ReentrantLock`, semaphore permit hoặc transaction lock đang giữ qua blocking exchange.

**Quy tắc:** chuẩn bị payload và bảo vệ state cần thiết ở một vùng lock ngắn, **nhả lock trước khi chờ partner**, rồi áp dụng kết quả theo giao thức thích hợp. Nếu cần update atomic với kết quả swap, phải thiết kế protocol không tạo vòng chờ hoặc dùng một primitive khác.

## 29. Exchanger và virtual threads

Trong các phiên bản Java có virtual threads (từ Java 21 ở trạng thái ổn định), `Exchanger` vẫn là API phối hợp thread theo hợp đồng Java SE. Tuy nhiên, **Exchanger không tự giải quyết deadlock logic**: nếu chỉ một bên tới, nó vẫn cần partner, timeout hoặc interrupt.

Bài học này dùng **Java 17+** với platform threads để runnable examples hoạt động trên JDK 17. Không phụ thuộc virtual thread APIs hoặc những điều kiện implementation cụ thể của scheduler virtual threads.

Nếu dùng virtual threads ở Java mới hơn, vẫn phải xem xét ownership mutable buffer, exception propagation, multiple rounds và match identity. Đừng nghĩ virtual threads rẻ hơn nghĩa một task có thể đợi partner vô hạn mà không ảnh hưởng lifecycle của ứng dụng.

## 30. Các sai lầm thường gặp

**1. "`exchange` là gửi item vào queue."** Sai. Nó là rendezvous hai chiều; cần partner để thành công.

**2. "Một thread có thể gọi exchange một mình rồi nhận lại chính item."** Không có partner thì block/timeout/interrupt; không tự complete bằng item chính mình.

**3. "Exchanger luôn ghép hai thread có tên A và B."** Không. Khi nhiều callers dùng cùng instance, partner pairing không được cố định bởi tên thread.

**4. "`exchange` tạo thread còn thiếu."** Sai. Người dùng phải khởi tạo/submit những tasks thực sự tham gia.

**5. "Bản có timeout luôn ném khi timeout bằng 0."** Không tuyệt đối. Nếu partner đã chờ sẵn, có thể ghép ngay; không chờ partner mới khi hết budget.

**6. "Timeout tự interrupt phía đối tác."** Sai. Timeout thuộc lời gọi hiện tại; peer cần protocol hủy riêng.

**7. "Interrupt status vẫn bật trong catch InterruptedException."** Theo contract của `exchange`, status đã được clear khi ném; nếu không thể propagate và muốn bảo toàn tín hiệu hủy, restore.

**8. "Exchanger copy ArrayList/buffer."** Sai. Nó swap references; object vẫn mutable.

**9. "Swap xong producer vẫn tự do sửa buffer đã gửi."** Sai với ownership handoff; có thể tạo data race.

**10. "happens-before nghĩa không cần đồng bộ bất kỳ mutation nào về sau."** Sai. Nó bảo đảm ordering/visibility cho hành động trước exchange thành công và hành động sau tương ứng, không khóa những thao tác tương lai.

**11. "Có ba threads gọi exchange thì cả ba đều xong."** Không có collective barrier cho ba; một thread có thể còn chờ.

**12. "Exchanger có FIFO và fairness mặc định."** Không có guarantee fairness/partner ordering như vậy trong public API.

**13. "Hai round dùng chung exchanger luôn đúng dù một bên retry hoặc bỏ lượt."** Không. Cần sequence/cancellation protocol để không ghép lệch round.

**14. "Nếu producer crash, consumer sẽ tự nhận tín hiệu end."** Không. Exchanger không có built-in close/EOF; dùng timeout/interrupt và lifecycle signals.

**15. "Pool một worker có thể chạy hai blocking exchange tasks."** Dễ deadlock/starvation nếu task đầu chờ task thứ hai chưa được chạy.

**16. "Dùng `synchronized` bao quanh `exchange` luôn an toàn."** Không nếu peer cần monitor đó để tới exchange.

**17. "Exchanger thay thế toàn bộ queue, barrier, phaser."** Không. Mỗi loại có mục tiêu synchronization riêng.

**18. "Một exchange thành công nghĩa consumer đã xử lý xong payload."** Không. Nó chỉ xác nhận việc trao đổi references theo pair, không xác nhận nghiệp vụ phía nhận đã commit.

**19. "Có thể retry mọi exchange vô hạn khi TimeoutException."** Nguy cơ retry lệch phiên hoặc giữ resources vô hạn; thiết kế deadline chung và correlation ID.

**20. "Generic type T làm object được trao đổi immutable."** Không. Mutable payload vẫn cần ownership và synchronization.

## 31. Checklist triển khai `Exchanger<T>`

Trước khi đưa vào production:

1. **Có thật sự là hai chiều không?** Nếu chỉ gửi một chiều, cân nhắc `BlockingQueue`/`SynchronousQueue`.
2. **Một lần exchange có chính xác hai participants mong muốn không?** Nếu nhiều threads cùng dùng, xác định pairing policy.
3. **Có giữ cùng instance exchanger không?** Hai instances khác nhau không ghép với nhau.
4. **Đối tác có chắc sẽ tới exchange theo mọi đường execution không?** Kể cả exception/early return.
5. **Có timeout hoặc cancellation plan không?** Tránh chờ vô hạn khi peer chết.
6. **Có xử lý `InterruptedException` đúng không?** Propagate nếu có thể, nếu không restore rồi dừng.
7. **Timeout có hủy cả phiên hay retry không?** Không retry một phía theo cảm tính.
8. **Có sequence ID/correlation ID cho nhiều round không?** Chặn ghép lệch lượt.
9. **Có quy tắc ownership mutable payload không?** Đừng dùng lại reference đã gửi cho partner.
10. **Object nhận có đúng lifecycle không?** Buffer đã được clear, có thể sử dụng lại và không bị bên kia sửa?
11. **Có kiểm tra missing partner?** Test một thread bị cancel trước khi tới rendezvous.
12. **Có shared lock được giữ khi exchange không?** Tránh dependency deadlock.
13. **Executor có đủ worker để hai phía tiến tới exchange không?** Single-thread fixed pool thường không đủ.
14. **Có test nhiều callers và pairing ngẫu nhiên không?** Không hardcode rằng T0 luôn ghép T1.
15. **Có log success/failure/timeouts và thời gian chờ không?** Quan sát latency, nhưng không lẫn scheduling delay với bug.
16. **Có test JMM visibility bằng pattern đúng không?** Ghi trước swap, đọc sau swap; tránh concurrent mutation sau bàn giao.
17. **Ai chịu trách nhiệm shutdown?** Exchanger không có close; stop threads/tasks theo owner.
18. **Có cần exactly-once processing hoặc durability không?** Exchanger không cung cấp transaction log/ack business.
19. **Có hạn chế memory/resource không?** Không giữ buffer rất lớn vô hạn trong vòng exchange.
20. **Có fallback khi peer chậm không?** Chọn deadline, token, queue khác hoặc kết thúc pipeline có kiểm soát.

## 32. Câu hỏi tự kiểm tra

**Câu 1:** `Exchanger<String>` có phải queue chứa String không?

**Không.** Nó là điểm rendezvous: một caller đưa String vào và chờ peer đưa String khác để swap.

**Câu 2:** A gửi "X", B gửi "Y". A và B nhận gì khi thành công?

A nhận "Y", B nhận "X". Không có bảo đảm thứ tự hai thread tiếp tục sau khi swap.

**Câu 3:** Nếu chỉ A gọi `exchange("X")` và B không bao giờ tới?

A có thể chờ vô hạn nếu không bị interrupt; nếu dùng timed overload có thể nhận `TimeoutException`.

**Câu 4:** `exchange(x, timeout)` ném `TimeoutException` có gửi cancellation đến B?

Không. Timeout chỉ báo A không ghép được trong thời hạn; B phải có tín hiệu hoặc timeout riêng.

**Câu 5:** `exchange` với một mutable buffer có tạo deep copy không?

Không. Hai bên swap references. Quy tắc ownership phải ngăn sửa đồng thời sau bàn giao.

**Câu 6:** Ghi `payload.number=42` trước swap và partner đọc sau swap thành công có visibility phù hợp không?

Có, theo memory consistency effect được Javadoc quy định cho cặp exchange thành công, miễn không có mutation cạnh tranh không đồng bộ phá contract.

**Câu 7:** Có ba thread A/B/C dùng một exchanger; tất cả chắc chắn qua exchange không?

Không. Hai thread có thể ghép một cặp, thread còn lại chờ partner thứ tư hoặc timeout/interrupt.

**Câu 8:** Exchanger có tự xác định partner theo ID không?

Không. Khi nhiều callers dùng chung, cần instance theo cặp hoặc protocol định tuyến/correlation bên ngoài.

**Câu 9:** Producer gửi buffer đầy rồi ngay lập tức `clear()` buffer vừa gửi có an toàn không?

Không. Consumer có thể đang đọc cùng object. Producer chỉ được clear **buffer mới nhận** mà nó sở hữu.

**Câu 10:** Hai exchange tasks submit vào `Executors.newSingleThreadExecutor()` có thể bị gì?

Task thứ nhất block chờ partner, task thứ hai nằm trong queue và không thể bắt đầu; có thể dẫn đến không tiến triển.

**Câu 11:** `InterruptedException` từ `exchange` có phải signal phải luôn bỏ qua cho task chạy tiếp không?

Không. Thường nên propagate, hoặc restore flag rồi dừng theo cancellation contract nếu ở ranh giới `Runnable`.

**Câu 12:** Khi nào `Exchanger` hợp hơn `SynchronousQueue`?

Khi **hai phía đều có một object để trao đổi**, đặc biệt khi hoán đổi buffer đầy/trống. Với truyền một chiều, `SynchronousQueue` thường rõ hơn.

**Câu 13:** Có thể tạo một global exchanger rồi dùng cho tất cả client request–response không?

Chỉ khi pairing không quan trọng hoặc có protocol riêng bảo vệ nó. Nếu yêu cầu response đúng request, Exchanger chung không tự cung cấp routing theo ID.

**Câu 14:** `CountDownLatch` và Exchanger khác gì?

Latch đợi một đếm/sự kiện, không swap payload. Exchanger ghép hai callers để trao đổi object.

**Câu 15:** Có nên nắm `ReentrantLock` trong lúc chờ exchange nếu partner cần lock đó để chuẩn bị item?

Không. Dễ tạo vòng phụ thuộc dẫn tới deadlock.

**Câu 16:** Một cặp trao đổi thành công có chứng minh phía nhận đã làm xong nghiệp vụ trên payload không?

Không. Nó xác nhận swap, không xác nhận downstream processing success hay commit. Muốn ack cần giao thức bổ sung.

## 33. Tổng kết

- **`Exchanger<T>` trao đổi object theo từng cặp thread**, không lưu hàng đợi, không broadcast và không tự tạo thread.
- **`exchange(x)`** chờ một partner, swap và trả object của partner; **`exchange(x, timeout, unit)`** thêm giới hạn thời gian và `TimeoutException`.
- **`InterruptedException`** là đường hủy hợp tác; phương thức exchange clear status khi ném, nên không được catch rỗng nếu cần giữ yêu cầu dừng.
- **Happens-before** giữa hành động trước exchange thành công và hành động sau exchange ở thread đối tác giúp **công bố payload đã chuẩn bị xong**, nhưng không tự bảo vệ mutation sau bàn giao.
- Use case đặc trưng là **đổi buffer đầy/trống**, giảm allocation và làm rõ ownership trong producer–consumer hai giai đoạn.
- Với nhiều threads, Exchanger chỉ **ghép từng cặp**, không bảo đảm partner identity, fairness, FIFO hoặc collective synchronization cho N threads.
- **Timeout, exception hoặc missed round** có thể làm lệch protocol; cần sequence ID, deadline, cancellation và cleanup rõ khi dùng nhiều lượt.
- **`SynchronousQueue` thích hợp hơn cho handoff một chiều; `CountDownLatch`/`CyclicBarrier`/`Phaser` phù hợp các mẫu chờ khác.**
- Không chờ exchange trong lúc giữ monitor/lock mà partner cần; không chạy hai blocking tasks trên executor chỉ có một worker.
- Một lần swap thành công không phải transaction, acknowledgment xử lý nghiệp vụ hay bảo đảm durable delivery.

**Tự kiểm tra thiết kế:** *"Ai là partner, hai bên gửi object gì, mỗi bên đang sở hữu buffer nào, và khi một phía không bao giờ tới điểm exchange thì bên kia có thể thoát/cleanup thế nào?"* Nếu trả lời được bốn câu hỏi ấy, `Exchanger<T>` có thể là công cụ rất rõ ràng cho giao thức trao đổi dữ liệu hai chiều trong Java concurrency.

**Tham khảo chính thức:** [Java SE 17 — Exchanger Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Exchanger.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronizers-006">Các thao tác timeout và interruption</a>

<details>
<summary>Xem chi tiết</summary>

# Các thao tác timeout và interruption trong Java Synchronizers

## 1. Vì sao các synchronizer phải có timeout và interruption?

Khi nhiều thread phối hợp bằng `CountDownLatch`, `CyclicBarrier`, `Semaphore`, `Phaser` hoặc `Exchanger`, một thread thường cần **đợi** thread khác, chờ đủ participant, chờ tài nguyên hay chờ một phase hoàn tất. Vấn đề là: một partner có thể bị lỗi, không được schedule, không bao giờ count down hoặc không bao giờ trả lại permit.

Nếu chờ **vô thời hạn**, ứng dụng có thể bị treo. Nếu một request đã hết hiệu lực mà worker vẫn tiếp tục chờ, ta lãng phí thread và tài nguyên. Hai khả năng then chốt của Java concurrency là:

- **Timeout:** chờ tối đa một khoảng thời gian (hoặc tới deadline của một operation) và nhận kết quả/exception tương ứng khi chưa đạt điều kiện.
- **Interruption:** một thread khác gọi `Thread.interrupt()` để **yêu cầu** thread đang chờ hủy công việc. Đây là tín hiệu hợp tác, **không phải dừng cưỡng bức**.

**Điều rất quan trọng:** cùng là “hết thời gian chờ”, nhưng **mỗi synchronizer có hợp đồng khác nhau**. Ví dụ:
- `CountDownLatch.await(timeout, unit)` trả **false** nếu chưa đếm về 0.
- `Semaphore.tryAcquire(timeout, unit)` trả **false** nếu không lấy được permit.
- `CyclicBarrier.await(timeout, unit)` ném **TimeoutException** và **làm hỏng barrier của generation hiện tại**.
- `Phaser.awaitAdvanceInterruptibly(phase, timeout, unit)` ném **TimeoutException**, nhưng **không tự thay đổi trạng thái của Phaser**.
- `Exchanger.exchange(value, timeout, unit)` ném **TimeoutException** nếu chưa có partner.

Không thể dùng chung một catch/return policy cho cả năm API mà không xét những hậu quả này.

### Mục tiêu sau bài học

1. Nắm chính xác API timeout/interruption của các synchronizers Java SE.
2. Phân biệt **false/null**, **TimeoutException**, **InterruptedException** và **BrokenBarrierException**.
3. Biết khi nào timeout/interrupt chỉ ảnh hưởng thread đang chờ và khi nào **lan sang các thread khác**.
4. Dùng `finally` và biến sở hữu permit để tránh **release sai** khi acquire chưa thành công.
5. Hiểu `Thread.interrupted()` xóa cờ trong khi `isInterrupted()` không xóa; biết lúc nào restore bằng `Thread.currentThread().interrupt()`.
6. Xây cancellation và deadline nhất quán, không tạo một timeout mới đầy đủ cho từng bước.
7. Viết Java 17+ runnable demos có output dự đoán được, không dựa `sleep()` để “ép” scheduler.
8. Biết điều kiện khôi phục `CyclicBarrier`, xử lý party rời đi ở `Phaser` và tránh wait-for cycles.

**Phạm vi:** Java concurrency nói chung trong `java.util.concurrent`; không phụ thuộc framework.

## 2. Timeout, interruption và hoàn tất bình thường: ba con đường

Giả sử một thread cần chờ resource hoặc một nhóm workers:

~~~text
               bắt đầu chờ
                    |
          +---------+----------+
          |         |          |
    Điều kiện đạt  timeout   bị interrupt
          |         |          |
    thành công     false/      InterruptedException
                  Timeout...    (nếu API hỗ trợ)
          |         |          |
       dùng kết     fallback   propagate hoặc
       quả đúng     /cleanup   restore+thoát
~~~

**Completion:** điều kiện thực sự đã đạt, có thể tiếp tục phần việc kế tiếp.

**Timeout:** chưa đạt điều kiện trong thời gian cho phép. Tùy API, caller nhận boolean false hoặc một `TimeoutException`; timeout không chứng minh partner đã chết hoặc task đã bị hủy.

**Interruption:** thread đang chờ được yêu cầu hủy. Các API chờ **interruptible** có thể ném `InterruptedException` và thường **clear interrupted status khi ném** theo contract cụ thể. Caller cần có chính sách tiếp theo.

Không phải mọi API “chờ” đều interruptible. Ví dụ `Semaphore.acquireUninterruptibly()` chủ động bỏ qua interrupt trong pha chờ và sẽ bảo tồn trạng thái interrupt sau khi cuối cùng lấy được permit; `Phaser.awaitAdvance(phase)` là **non-interruptible**. Vì vậy nếu muốn hủy có deadline, phải chọn đúng overload ngay từ đầu.

## 3. Bảng nhanh: API nào trả false, API nào ném exception?

| Synchronizer | Chờ không timeout | Chờ có timeout | Khi timeout | Khi bị interrupt |
|---|---|---|---|---|
| `CountDownLatch` | `await()` | `await(long, TimeUnit)` | Trả `false` nếu count chưa về 0 | `InterruptedException` |
| `CyclicBarrier` | `await()` | `await(long, TimeUnit)` | `TimeoutException`, barrier bị broken | `InterruptedException`; có thể làm barrier broken và khiến waiter khác nhận `BrokenBarrierException` |
| `Semaphore` | `acquire()` | `tryAcquire(long, TimeUnit)` | Trả `false` nếu chưa có permit | `InterruptedException` |
| `Phaser` | `awaitAdvance(int)` (non-interruptible) | `awaitAdvanceInterruptibly(int, long, TimeUnit)` | `TimeoutException`; không tự break phaser | Bản interruptible: `InterruptedException`; phaser vẫn giữ state |
| `Exchanger<V>` | `exchange(V)` | `exchange(V, long, TimeUnit)` | `TimeoutException` nếu chưa ghép được với partner | `InterruptedException` |

**Các giá trị cần phân biệt:** `await(timeout)` của CountDownLatch trả boolean; `await(timeout)` của CyclicBarrier trả **int arrival index khi thành công**, và ném exception khi timeout. Không có một kiểu trả về timeout chung cho mọi synchronizer.

Các overload đã liệt kê thuộc Java SE 17. Một số class có thêm variants như `Semaphore.tryAcquire()` không chờ hoặc `Semaphore.acquireUninterruptibly()`, cần đọc contract từng method.

## 4. CountDownLatch: chờ một lần, timeout không “phá” latch

`CountDownLatch` có một count khởi tạo. Các thread gọi `countDown()` để giảm count; khi count về 0, tất cả waiters được giải phóng. **Count không reset được.**

Hai API:

~~~java
latch.await();
boolean ready = latch.await(500, TimeUnit.MILLISECONDS);
~~~

- `await()`: chờ cho tới count=0, hoặc ném `InterruptedException`.
- `await(timeout, unit)`: trả `true` nếu count về 0 trước khi hết thời gian; trả `false` nếu hết hạn khi count vẫn lớn hơn 0.
- Khi timeout, **count không tự giảm**, latch không bị broken và những waiter khác có thể tiếp tục đợi.
- Nếu lúc gọi count đã bằng 0, timed await trả true ngay.
- Nếu thời gian <= 0 và count chưa về 0, timed await trả false ngay; không chờ.
- Nếu thread đang chờ bị interrupt, method ném InterruptedException và clear flag theo contract; count **không đổi chỉ vì interruption**.
- `countDown()` khi count đã về 0 không làm count âm.

### Ví dụ thực hành: chờ công việc hoàn tất nhưng có thời hạn

~~~java
CountDownLatch completed = new CountDownLatch(3);
// Workers đảm bảo gọi completed.countDown() khi công việc tương ứng xong.
boolean allDone = completed.await(2, TimeUnit.SECONDS);
if (!allDone) {
    reportIncomplete(completed.getCount());
}
~~~

Đây là đoạn method minh họa; `reportIncomplete()` do ứng dụng định nghĩa. **Điều kiện quan trọng:** workers cần gọi `countDown()` ở đúng điểm hoàn tất, thường trong `finally` để không làm caller chờ mãi khi công việc lỗi. Tuy nhiên, countDown trong finally chứng minh **task đã rời vùng xử lý**, không đồng nghĩa kết quả thành công; lỗi cần được truyền bằng cơ chế riêng.

### Ví dụ runnable 1: timeout trả false và interrupt không giảm count

Lưu **CountDownLatchTimeoutInterruptDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CountDownLatchTimeoutInterruptDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch gate = new CountDownLatch(1);

        // Timeout bằng 0: không đo timing/scheduler.
        boolean completed = gate.await(0, TimeUnit.MILLISECONDS);

        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        Thread waiter = new Thread(() -> {
            started.countDown();
            try {
                gate.await();
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        }, "latch-waiter");

        waiter.start();
        started.await();
        waiter.interrupt();
        waiter.join();

        System.out.println("completed=" + completed);
        System.out.println("interrupted=" + interrupted.get());
        System.out.println("count=" + gate.getCount());

        gate.countDown(); // một lần phát tín hiệu thực sự, sau khi test
        System.out.println("afterCountDown=" + gate.getCount());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac CountDownLatchTimeoutInterruptDemo.java
java CountDownLatchTimeoutInterruptDemo
~~~

Output trong môi trường không có external interruption:

~~~text
completed=false
interrupted=true
count=1
afterCountDown=0
~~~

**Lý do kết quả ổn định:** gate có count=1 và không thread nào countDown trước khi main in. `await(0)` trả false ngay. Waiter có thể đã vào `await()` hoặc chưa khi main gọi interrupt; trong cả hai lịch chạy, `await` phát hiện flag và ném exception. `join()` làm main chờ waiter xong. Chỉ câu `gate.countDown()` cuối mới hạ count về 0.

**Không kết luận:** “Timeout nghĩa là tác vụ producer đã dừng”. Nó chỉ nói thread caller **chưa quan sát đủ countDown trong deadline**.

## 5. CyclicBarrier: timeout của một party ảnh hưởng cả nhóm

`CyclicBarrier` dùng khi có một số lượng cố định `parties` phải cùng tới điểm đồng bộ trước khi tất cả được tiếp tục. Barrier được tái sử dụng qua nhiều **generations** nếu các lần chờ thành công.

~~~java
CyclicBarrier barrier = new CyclicBarrier(3);
int index = barrier.await(1, TimeUnit.SECONDS);
~~~

Một lần `await` thành công trả **arrival index**, không phải boolean:
- Giá trị 0 thuộc thread đến sau cùng trong generation (thread này có thể thực thi barrier action nếu có).
- Khi tất cả parties đến, barrier trip, các waiters cùng được giải phóng và generation mới có thể bắt đầu.
- Nếu một party timeout trong khi chờ, nó ném `TimeoutException` và barrier **chuyển sang broken state**.
- Những thread khác đang chờ ở generation đó thường nhận `BrokenBarrierException`.
- Nếu một party bị interrupt khi chờ, nó ném `InterruptedException`, interrupted status được clear; barrier broken và có thể khiến các waiters khác nhận BrokenBarrierException.
- `reset()` có thể đưa barrier về trạng thái ban đầu, nhưng reset đồng thời với waiting có thể gây BrokenBarrierException cho những waiter còn lại; không nên reset tùy tiện trước khi cả nhóm có protocol đồng thuận.

Đó là **all-or-none breakage**: nếu một participant không thể tiếp tục vòng phối hợp, những participant khác không bị để lại chờ một generation đã không thể hoàn thành.

### Ví dụ runnable 2: một party timeout làm barrier broken

Lưu **CyclicBarrierTimeoutDemo.java**:

~~~java
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CyclicBarrierTimeoutDemo {
    public static void main(String[] args) throws InterruptedException {
        CyclicBarrier barrier = new CyclicBarrier(2);
        boolean timedOut = false;
        boolean otherBroken = false;

        // Chỉ có 1 party, timeout 0: chắc chắn chưa đủ 2 parties.
        try {
            barrier.await(0, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            timedOut = true;
        } catch (BrokenBarrierException e) {
            throw new AssertionError("Unexpected pre-broken barrier", e);
        }

        boolean broken = barrier.isBroken();

        // Party tiếp theo không thể hoàn thành generation đã broken.
        try {
            barrier.await();
        } catch (BrokenBarrierException e) {
            otherBroken = true;
        }

        System.out.println("timedOut=" + timedOut);
        System.out.println("broken=" + broken);
        System.out.println("otherBroken=" + otherBroken);
    }
}
~~~

Lệnh:

~~~bash
javac CyclicBarrierTimeoutDemo.java
java CyclicBarrierTimeoutDemo
~~~

Stdout:

~~~text
timedOut=true
broken=true
otherBroken=true
~~~

**Giải thích:** chưa có party thứ hai, timeout=0, `await` ném TimeoutException rồi barrier được đánh dấu broken. Lời `await()` sau đó không đợi vô hạn vì đã broken; nó ném BrokenBarrierException. Chương trình cố ý tránh dùng sleep hoặc tạo waiters không có điểm thoát.

### Phân biệt với CountDownLatch

- CountDownLatch timeout **không hỏng latch** và không thay đổi count.
- CyclicBarrier timeout **làm broken barrier**, một thay đổi có ảnh hưởng đến cả nhóm.
- CountDownLatch là **one-shot**; CyclicBarrier có thể **reuse generation** khi không bị broken (hoặc sau reset có giao thức phù hợp).
- CountDownLatch chờ những `countDown` được phát; CyclicBarrier chờ **đủ parties cùng gọi await ở một generation**.

### Nếu một worker lỗi trước khi tới barrier?

Nếu một party thoát khỏi computation **trước khi gọi await**, các party còn lại có thể chờ mãi. Timed await giúp giới hạn thời gian. Caller cần hiểu rằng việc worker không đến là một thất bại ở cấp group, không chỉ retry chính thread đã timeout. Cần propagate lỗi/cancellation và quyết định tạo barrier mới hoặc dừng generation thay vì reset không kiểm soát.

## 6. Semaphore: timeout là false, interrupt phải giữ đúng ownership

`Semaphore` quản lý một số permit. Thread cần permit để vào vùng giới hạn concurrency. Những API quan trọng:

~~~java
semaphore.acquire();
boolean acquired = semaphore.tryAcquire(500, TimeUnit.MILLISECONDS);
semaphore.acquireUninterruptibly();
boolean immediate = semaphore.tryAcquire();
~~~

- `acquire()` chờ nếu không có permit, **interruptible**.
- `tryAcquire(timeout, unit)` chờ có hạn và trả `true` nếu lấy được permit, `false` nếu hết hạn. Có thể ném `InterruptedException`.
- `tryAcquire()` không tham số thử ngay, không chờ và **có thể barge** khi fairness=true, vì không tôn trọng việc các waiter khác đã đứng chờ theo kiểu timed acquire.
- `acquireUninterruptibly()` tiếp tục cố lấy permit dù bị interrupt khi đang chờ, sau đó bảo tồn interrupted status; không dùng khi lifecycle phải phản hồi cancel ngay.
- `release()` thêm permit trở lại; **Semaphore không tự biết thread nào sở hữu permit** như mutex. Nếu release sai, availablePermits có thể tăng không đúng protocol.

### Quy tắc quan trọng: chỉ release khi thật sự acquire thành công

~~~java
boolean acquired = false;
try {
    acquired = semaphore.tryAcquire(300, TimeUnit.MILLISECONDS);
    if (!acquired) {
        handleOverload();
        return;
    }
    doWorkWithPermit();
} finally {
    if (acquired) {
        semaphore.release();
    }
}
~~~

Đây là đoạn method tích hợp; `handleOverload` và `doWorkWithPermit` do ứng dụng định nghĩa, method phải handle/throws InterruptedException.

Nếu `tryAcquire` ném InterruptedException **trước khi nhận permit**, `acquired` vẫn false; finally không release. Nếu timeout trả false, cũng không release. Nếu true, dù work lỗi, finally release đúng một permit.

**Không viết:**

~~~java
try {
    semaphore.acquire();
    process();
} finally {
    semaphore.release(); // SAI nếu acquire đã ném InterruptedException
}
~~~

Đoạn sai trên nằm trong method có `throws InterruptedException` và các biến do ứng dụng cung cấp. Khi acquire thất bại vì interrupt, release sẽ tạo **permit không thuộc lần acquire đó**, phá giới hạn concurrency. Đối với acquire() không trả boolean, có thể đặt `acquire()` **trước** try/finally để chỉ vào finally sau khi acquire thành công.

### Ví dụ runnable 3: interrupt trước khi lấy permit

Lưu **SemaphoreTimeoutInterruptDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SemaphoreTimeoutInterruptDemo {
    public static void main(String[] args) throws InterruptedException {
        Semaphore permits = new Semaphore(1, true);
        permits.acquire(); // main giữ permit duy nhất
        boolean timedAcquired =
                permits.tryAcquire(0, TimeUnit.MILLISECONDS);

        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        Thread waiter = new Thread(() -> {
            started.countDown();
            try {
                permits.acquire();
                try {
                    throw new AssertionError("No permit should be available");
                } finally {
                    permits.release();
                }
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        }, "semaphore-waiter");

        try {
            waiter.start();
            started.await();
            waiter.interrupt();
            waiter.join();

            System.out.println("timedAcquired=" + timedAcquired);
            System.out.println("interrupted=" + interrupted.get());
            System.out.println("permitsWhileOwned=" +
                    permits.availablePermits());
        } finally {
            permits.release(); // main trả lại permit mình đã lấy
        }

        System.out.println("permitsAfterRelease=" +
                permits.availablePermits());
    }
}
~~~

Chạy:

~~~bash
javac SemaphoreTimeoutInterruptDemo.java
java SemaphoreTimeoutInterruptDemo
~~~

Stdout (không có interruption từ bên ngoài):

~~~text
timedAcquired=false
interrupted=true
permitsWhileOwned=0
permitsAfterRelease=1
~~~

**Lập luận:** main giữ permit duy nhất, nên timed tryAcquire(0) không thể thành công. Thread waiter báo started rồi thử acquire, nhưng main chưa release permit khi gửi interrupt; dù signal đến trước hoặc trong lúc acquire chờ, method ném InterruptedException. Main join waiter trước khi in. Semaphore count vẫn 0 cho tới finally của main và bằng 1 sau release.

**Lưu ý:** Phần trong waiter có một `AssertionError` cố ý đánh dấu đường thành công không mong muốn. Nó không chạy trong điều kiện ví dụ đã mô tả. Trong production, thay bằng logic nghiệp vụ thật và quản lý ownership trong finally.

## 7. Phaser: timeout/interrupt không tự phá trạng thái phaser

`Phaser` phối hợp nhiều **registered parties** qua các **phases**, cho phép register/deregister linh hoạt hơn CyclicBarrier. Một party gọi `arrive()` để báo đã tới phase hiện tại; `arriveAndAwaitAdvance()` báo tới và đợi phase chuyển. Phaser có thể bị terminate chủ động bằng `forceTermination()` hoặc theo lifecycle.

Các phương thức cần phân biệt:

| API | Đã báo arrive? | Có interruptible? | Có timeout? |
|---|---|---|---|
| `arrive()` | Có | Không phải thao tác chờ | Không |
| `arriveAndDeregister()` | Có và rời khỏi group | Không phải thao tác chờ | Không |
| `arriveAndAwaitAdvance()` | Có rồi chờ advance | **Non-interruptible** | Không |
| `awaitAdvance(phase)` | **Không** tự arrive; chỉ đợi phase đổi | **Non-interruptible** | Không |
| `awaitAdvanceInterruptibly(phase)` | Không tự arrive | Có | Không |
| `awaitAdvanceInterruptibly(phase, timeout, unit)` | Không tự arrive | Có | Có |

**Hai lỗi đặc biệt nguy hiểm:**

1. Gọi `awaitAdvanceInterruptibly(phase)` mà tưởng nó tự báo arrive; nếu không ai gọi `arrive`, phase có thể không bao giờ chuyển.
2. Một party đã `arrive()` rồi timeout khi đợi phase tiếp theo, sau đó lập tức gọi `arriveAndDeregister()` cho **cùng phase** một cách tùy tiện. Việc arrive thêm lần nữa trong cùng phase không phải lối dọn dẹp chung an toàn. Phải xây protocol quản lý registration/termination theo vòng đời thực tế.

### Khác biệt lớn so với CyclicBarrier

Theo Javadoc `Phaser`, **interruption và timeout khi đợi bằng các phương thức interruptible/timed không tự làm thay đổi state của phaser**. Không có mô hình barrier broken tự động tương đương CyclicBarrier.

Hệ quả:
- Caller có thể nhận TimeoutException, nhưng `getPhase()` vẫn giữ nguyên.
- Các registered parties khác vẫn có thể đang chờ phase này.
- Nếu quyết định hủy cả group, cần một coordinator xử lý `forceTermination()` hoặc deregistration/protocol khác đúng thời điểm.
- `forceTermination()` có thể khiến các waiters được giải phóng với **phase âm** báo termination; code phải kiểm tra kết quả thay vì mặc định đã tới phase kế tiếp thành công.
- `awaitAdvance(phase)` và `arriveAndAwaitAdvance()` **không chấm dứt việc chờ chỉ vì interrupt**, nên không dùng khi yêu cầu phản hồi cancel ngay mà không có đường termination khác.

### Ví dụ runnable 4: Phaser timeout không bị broken

Lưu **PhaserTimeoutDemo.java**:

~~~java
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PhaserTimeoutDemo {
    public static void main(String[] args) throws InterruptedException {
        Phaser phaser = new Phaser(2);
        int originalPhase = phaser.getPhase();

        boolean timedOut = false;
        try {
            // Không party nào arrive, phase chưa thể advance.
            phaser.awaitAdvanceInterruptibly(
                    originalPhase, 0, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            timedOut = true;
        }

        System.out.println("timedOut=" + timedOut);
        System.out.println("samePhase=" +
                (phaser.getPhase() == originalPhase));
        System.out.println("terminated=" + phaser.isTerminated());

        phaser.forceTermination(); // chủ động đóng group trong ví dụ
        System.out.println("afterForceTermination=" +
                phaser.isTerminated());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac PhaserTimeoutDemo.java
java PhaserTimeoutDemo
~~~

Output:

~~~text
timedOut=true
samePhase=true
terminated=false
afterForceTermination=true
~~~

**Vì sao?** Phaser có 2 registered parties, chưa ai gọi arrive. Bản timed await với timeout=0 ném TimeoutException, **không sửa phase hay tự terminate**. Sau đó main gọi forceTermination theo quyết định chủ động. Đây là khác biệt cốt lõi với `CyclicBarrier.await(0, unit)` vốn làm broken barrier khi timeout.

### Timeout khi đang là một registered party

Khi một registered party timeout sau khi đã arrive, muốn cleanup cần biết:
- Party đó đã arrive ở phase nào?
- Có task nào đang chờ nó ở phase tiếp theo?
- Group sẽ **fail fast toàn bộ** hay có thể tiếp tục với ít parties hơn?
- Ai được phép deregister/forceTermination?
- Có thread đang chờ bản non-interruptible không?
- Có nguy cơ một thread đến phase mới trong khi party cũ chưa rời lifecycle?

`Phaser` linh hoạt nhưng dễ mắc lỗi registration-count nếu phần dừng/hủy không được thiết kế rõ ràng. Không “cứ catch TimeoutException rồi return” nếu vẫn để những registered parties khác đợi vĩnh viễn.

## 8. Exchanger: chờ một partner, timeout ném exception

`Exchanger<V>` ghép **hai threads** để trao đổi giá trị. Mỗi thread gọi `exchange(value)` và nhận lại giá trị của partner khi hai bên gặp nhau.

~~~java
Exchanger<String> exchanger = new Exchanger<>();

String partnerValue = exchanger.exchange(
        "my-value", 500, TimeUnit.MILLISECONDS);
~~~

- Nếu có partner tương ứng kịp thời, method trả về object từ partner.
- Nếu chưa có partner trong deadline, ném `TimeoutException`.
- Nếu bị interrupt khi đang chờ, ném `InterruptedException` và clear interrupted status theo contract.
- Không có khái niệm **broken state kéo dài** như CyclicBarrier. Một lần timeout không tự biến Exchanger thành một đối tượng không thể dùng lại.
- Dù một bên timeout, bên kia có thể đến sau và phải chờ một partner mới hoặc timeout theo contract của chính nó.
- Không mặc định dữ liệu đã chuyển cho partner hay chưa nếu ứng dụng bắt/điều phối các side effects ngoài exchange; xác định điểm thành công bằng **return bình thường** từ exchange.

### Ví dụ runnable 5: exchange timeout nếu không có partner

Lưu **ExchangerTimeoutDemo.java**:

~~~java
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ExchangerTimeoutDemo {
    public static void main(String[] args) throws InterruptedException {
        Exchanger<String> exchanger = new Exchanger<>();
        boolean timedOut = false;

        try {
            exchanger.exchange("A", 0, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            timedOut = true;
        }

        System.out.println("timedOut=" + timedOut);
    }
}
~~~

Lệnh:

~~~bash
javac ExchangerTimeoutDemo.java
java ExchangerTimeoutDemo
~~~

Stdout:

~~~text
timedOut=true
~~~

Chỉ có main thread gọi exchange, không có partner; timeout=0 khiến method không chờ và ném TimeoutException. Dùng thời hạn 0 ở ví dụ giúp kiểm chứng hành vi mà không cần tạo race dựa trên sleep.

### Khi partner có thể biến mất

Một thread chờ trao đổi có thể bị bỏ lại nếu partner throw exception hoặc branch return trước khi gọi exchange. Vì vậy:
- Timed exchange thường phù hợp hơn vô hạn trong workflow có failure.
- Caller cần xử lý TimeoutException và cancellation rõ ràng.
- Không dùng Exchanger như một message queue nhiều phần tử hay broadcast primitive: mỗi lần ghép chỉ trao đổi giữa **hai participants**.
- Nếu cần nhiều producers/consumers không hẹn nhau theo cặp, `BlockingQueue` hoặc coordination abstraction khác có thể phù hợp hơn.

## 9. Interruption: xử lý thế nào để không làm mất tín hiệu?

Các thao tác interruptible như `CountDownLatch.await`, `CyclicBarrier.await`, `Semaphore.acquire`, `Phaser.awaitAdvanceInterruptibly`, `Exchanger.exchange` có thể ném `InterruptedException` khi thread bị interrupt trong lúc chờ.

### Hai phương pháp tốt

**Cách 1: Propagate checked exception khi method cho phép**

~~~java
void waitForWorkers(CountDownLatch latch)
        throws InterruptedException {
    latch.await();
}
~~~

Method trên để caller tự quyết định cancellation/fallback.

**Cách 2: Restore interrupt flag khi không ném được**

~~~java
@Override
public void run() {
    try {
        semaphore.acquire();
        try {
            doWork();
        } finally {
            semaphore.release();
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
    }
}
~~~

Đây là fragment trong một class có `Semaphore semaphore` và method `doWork()`. `Runnable.run()` không thể khai báo `throws InterruptedException`, nên restore flag rồi return bảo tồn ý nghĩa yêu cầu hủy cho tầng trên.

**Tránh:**

~~~java
try {
    gate.await();
} catch (InterruptedException ignored) {
    // Sai khi yêu cầu hủy cần được tôn trọng:
    // bỏ qua rồi tiếp tục vòng chờ như không có gì xảy ra.
}
~~~

Với nhiều synchronizer, `InterruptedException` được ném và interrupted status bị **clear** khi ném. Nếu nuốt exception, yêu cầu hủy có thể hoàn toàn bị mất.

### isInterrupted() vs Thread.interrupted()

| API | Kiểm tra thread nào? | Có clear interrupted status? |
|---|---|---|
| `thread.isInterrupted()` | Thread được tham chiếu | Không |
| `Thread.currentThread().isInterrupted()` | Current thread | Không |
| `Thread.interrupted()` (static) | **Current thread** | **Có** |
| `thread.interrupt()` | Gửi tín hiệu interrupt tới thread | Không phải kiểm tra/clear flag |

Đừng gọi `Thread.interrupted()` trong polling rồi vô tình tiếp tục công việc mà không xử lý giá trị true: nó clear flag nên những lần kiểm tra sau có thể không còn thấy yêu cầu hủy.

### Interrupt khác timeout

- Timeout = điều kiện chưa đạt trong khoảng thời gian.
- Interruption = bên điều phối/caller khác yêu cầu hủy.
- Timeout không tự set interrupted status.
- Interruption không phải một countdown/timer và không có bảo đảm dừng ngay đối với code không kiểm tra.
- Với CyclicBarrier, **cả hai** có thể làm broken barrier; với Phaser, việc wait bị interrupt/timeout **không tự thay đổi phaser**.
- Trong chương trình thực tế, những trạng thái này cần metrics/logs khác nhau.

## 10. Quy tắc thời gian: timeout của từng bước không phải deadline tổng

Giả sử workflow có ba bước chờ:
1. Chờ một permit tối đa 300ms.
2. Chờ latch tối đa 300ms.
3. Chờ partner Exchanger tối đa 300ms.

Nếu mỗi bước được cấp **300ms mới tinh**, workflow có thể đợi gần **900ms** chưa kể thời gian xử lý. Nếu SLA tổng chỉ 300ms thì code không đúng.

Giải pháp: tạo **deadline bằng đồng hồ đơn điệu** (`System.nanoTime()`), rồi ở mỗi bước tính **remaining budget**.

~~~java
long budget = TimeUnit.MILLISECONDS.toNanos(300);
long deadline = System.nanoTime() + budget;

long remaining = deadline - System.nanoTime();
if (remaining <= 0L) {
    throw new TimeoutException("No time left");
}
boolean acquired = semaphore.tryAcquire(
        remaining, TimeUnit.NANOSECONDS);
if (!acquired) {
    throw new TimeoutException("Permit wait expired");
}
~~~

Đây là đoạn minh họa trong method có `semaphore`, imports và exception handling. Với deadline cực dài có thể cần xử lý overflow, nhưng với ngân sách ngắn hợp lệ thì phép trừ trên monotonic nanoTime là cách thường dùng. Khi đã acquire thành công, phải release trong `finally`.

**Lưu ý:** Nếu ba bước không thể bị hủy ngay khi timeout bởi một remote I/O blocking không hỗ trợ interrupt, timeout của synchronizer không biến thành hard deadline của toàn hệ thống. Phải cấu hình deadline ở từng dependency.

### Khác biệt giữa timeout và absolute timestamp

`System.currentTimeMillis()` là wall-clock và có thể đổi do điều chỉnh đồng hồ. `System.nanoTime()` dùng để đo **elapsed time** và so sánh các mốc tương đối trong cùng JVM; không dùng giá trị đó làm Unix timestamp. Các timed APIs dùng `TimeUnit` để biểu đạt đơn vị thời gian rõ ràng.

## 11. Hủy một member: ảnh hưởng “toàn nhóm” rất khác nhau

| Primitive | Nếu một thread timeout | Những thread khác |
|---|---|---|
| CountDownLatch | Waiter nhận false, latch count không đổi | Có thể tiếp tục chờ countDown; không bị đánh thức chỉ vì waiter khác timeout |
| Semaphore | Waiter không lấy permit, trả false | Các threads khác vẫn chờ/tìm permit theo chính sách |
| CyclicBarrier | Barrier bị broken | Những waiter cùng generation nhận BrokenBarrierException; các lần await sau cũng thất bại tới khi reset/đổi barrier |
| Phaser | Timed await thất bại, state phaser không tự đổi | Các parties khác có thể vẫn chờ; coordinator phải quyết định deregister/terminate |
| Exchanger | Một trao đổi chưa ghép được bị timeout | Partner tới sau có thể phải tiếp tục chờ/tự timeout; không có broken state lâu dài |

**Đừng coi timeout là “không còn ai cần cleanup”.** Nếu waiter thoát nhưng một resource hoặc một group đã được đăng ký, hãy xác định ownership và giao thức đóng.

### Lưu ý memory visibility

Các synchronizers cũng có memory consistency effects:
- `CountDownLatch`: actions trước `countDown` happen-before actions sau một `await` trả thành công ở thread khác.
- `CyclicBarrier`: actions trước `await` happen-before barrier action và actions sau `await` **thành công** ở party khác theo Javadoc.
- `Semaphore`: actions trước `release` happen-before actions sau một `acquire` thành công ở thread khác.
- `Exchanger`: các actions trước `exchange` của mỗi bên happen-before actions sau một cặp trao đổi thành công ở bên kia.

**Không tự áp dụng kết luận visibility của một lần đồng bộ thành công cho một lần timeout.** Ví dụ `latch.await(timeout)==false` không chứng minh tất cả workers đã publish kết quả; `BrokenBarrierException` không phải một completed generation bình thường. Nếu cần dữ liệu hoàn chỉnh, phải có protocol công bố kết quả hợp lệ và chỉ đọc sau điểm đồng bộ thích hợp.

## 12. Khôi phục sau timeout và tránh deadlock

Một lần timeout chỉ nói **điều kiện chưa đạt đúng thời hạn**. Sau đó bạn cần quyết định:
- Có tiếp tục với **partial result** không?
- Có retry không? Nếu có, còn bao nhiêu ngân sách và có làm tăng tải không?
- Có hủy toàn bộ nhóm không? Ai gửi tín hiệu?
- Có child/participant nào còn chạy không?
- Có permit/lock/resource nào phải release?
- Với Phaser, ai deregister hoặc forceTermination?
- Với CyclicBarrier, ai được phép reset/khởi tạo generation mới?
- Có nên log nguyên nhân timeout/interrupt riêng với task failure?

### Một vòng đợi gây deadlock điển hình

~~~text
Thread A: giữ permit P cuối cùng
          rồi đợi latch L về 0

Thread B: cần permit P trước khi countDown L

A đợi B countDown L.
B đợi A release P.
=> wait-for cycle; timeout chỉ giúp A thoát nếu A dùng timed await
   và release P trong finally.
~~~

Không sửa vấn đề bằng cách tăng timeout vô hạn. Cách đúng là **không giữ tài nguyên mà thread khác cần khi chờ completion của chính nó**, hoặc tổ chức lại lifecycle/ownership.

### Những lỗi phá invariant khi recover

- `Semaphore.release()` khi chưa acquire thành công tạo permit thừa.
- `Phaser.arriveAndDeregister()` sau một lần arrive đã được tính cho phase hiện tại mà không xét trạng thái phase có thể báo arrive hai lần.
- `CyclicBarrier.reset()` trong khi các waiters còn tham gia làm họ nhận BrokenBarrierException, không phải “cứu” generation đã lỗi một cách trong suốt.
- Nuốt InterruptedException khiến group tưởng cancellation đã được xử lý nhưng worker tiếp tục chờ.
- Retry sau timeout với full budget mới có thể vượt quá deadline chung.
- Một party nhận TimeoutException rồi tiếp tục đọc shared results như thể mọi bên đã đồng bộ xong.

## 13. Các phép chờ liên quan nhưng không đồng nghĩa

Các API Java SE liên quan:

| API | Khi hết hạn | Interruption | Liên hệ |
|---|---|---|---|
| `BlockingQueue.offer(e, timeout, unit)` | Trả false | InterruptedException | Chờ slot để đưa item vào queue |
| `BlockingQueue.poll(timeout, unit)` | Trả null | InterruptedException | Chờ item có sẵn |
| `Condition.await(timeout, unit)` | Trả boolean | InterruptedException | Có spurious wakeup; luôn kiểm tra predicate dưới lock |
| `ReentrantLock.tryLock(timeout, unit)` | Trả false | InterruptedException | Chờ lock, chỉ unlock nếu đã acquire |
| `Future.get(timeout, unit)` | TimeoutException | InterruptedException | Chờ Future; timeout không tự cancel task |
| `Thread.join(timeout)` | Trả bình thường kể cả do timeout | InterruptedException | Cần kiểm tra `thread.isAlive()` để biết thread còn sống |
| `LockSupport.parkNanos` | Trở về sau park/spurious wakeup/interrupt | Không ném InterruptedException | Phải kiểm tra predicate/flag, không là synchronizer completion |
| `Phaser.awaitAdvance(phase)` | Không có timeout | **Không ném** InterruptedException | Phải có đường termination/advance nếu cần dừng |

Đừng gộp “method trả bình thường” với “điều kiện đã đạt” nếu API không báo riêng kết quả. Ví dụ `Thread.join(timeout)` có thể trở về vì hết timeout; caller cần kiểm tra `isAlive()`. Với `LockSupport.parkNanos`, spurious return là hợp lệ nên luôn dùng một condition/predicate rõ ràng.

## 14. Lỗi thường gặp khi dùng timeout và interruption

**Lỗi 1 — Bắt TimeoutException cho CountDownLatch.await(timeout).** API trả **boolean false**, không ném TimeoutException.

**Lỗi 2 — Nghĩ CyclicBarrier.await(timeout) trả false.** Nó ném TimeoutException và làm barrier broken.

**Lỗi 3 — Nghĩ Phaser timeout tự làm mọi parties rời group.** State phaser không đổi; cần lifecycle/recovery rõ.

**Lỗi 4 — Đợi Phaser bằng awaitAdvance() rồi chờ interrupt phá đợi.** Đây là non-interruptible method.

**Lỗi 5 — Gọi awaitAdvanceInterruptibly() mà quên arrive() theo protocol.** Method chỉ đợi phase, không tự báo party đã tới.

**Lỗi 6 — Release semaphore permit trong finally dù chưa acquire.** Có thể tạo thêm permit và phá giới hạn concurrency.

**Lỗi 7 — Bắt InterruptedException rồi bỏ qua.** Nhiều API clear flag, tín hiệu cancellation bị mất.

**Lỗi 8 — Gọi Thread.interrupted() khi chỉ cần kiểm tra.** Static method clear cờ.

**Lỗi 9 — Tưởng timeout tự cancel các thread khác.** Thông thường timeout chỉ kết thúc phép chờ; riêng CyclicBarrier có cơ chế breakage của group, nhưng không giết threads.

**Lỗi 10 — Tự reset barrier khi có nhiều callers đang chờ mà thiếu protocol.** Có thể làm generation mới/old lẫn lộn, hoặc gây BrokenBarrierException ngoài dự kiến.

**Lỗi 11 — Dùng Exchanger không timeout khi partner có thể mất.** Một thread có thể chờ mãi.

**Lỗi 12 — Gộp timeout của mỗi bước thành deadline tổng mà không trừ thời gian đã tiêu.** Có thể vượt SLA.

**Lỗi 13 — Dùng wall clock để đo elapsed và không xử lý thay đổi thời gian hệ thống.** Ưu tiên System.nanoTime với budget hợp lý.

**Lỗi 14 — Coi await(timeout)==false là mọi worker thất bại.** Nó chỉ nói count chưa về zero trong thời hạn; outcome từng worker cần theo dõi riêng.

**Lỗi 15 — Nghĩ fair Semaphore.tryAcquire() luôn tôn trọng thứ tự chờ.** Bản không tham số có thể barging.

**Lỗi 16 — Nghĩ semaphore biết owner như ReentrantLock.** Semaphore permits không gắn ownership thread; release sai nguy hiểm.

**Lỗi 17 — Khi barrier broken vẫn đọc data của generation như success.** BrokenBarrierException báo phối hợp đã thất bại, không có completed generation hợp lệ.

**Lỗi 18 — Gọi Phaser.arriveAndDeregister hai lần trong cùng phase để “sửa” timeout.** Có thể làm lệch số party/arrivals.

**Lỗi 19 — Coi timeout=0 là luôn nhận kết quả thất bại kể cả khi điều kiện đã sẵn sàng.** Nếu latch count đã 0 hoặc semaphore có permit, API có thể thành công ngay; timeout=0 chỉ bỏ phần **chờ**.

**Lỗi 20 — Dùng Thread.sleep để điều phối test.** Sleep không bảo đảm thread đang đợi ở API mong muốn; dùng latch/timeout 0/điểm kiểm tra rõ.

## 15. Checklist code review và vận hành

- [ ] Đã xác định primitive đúng: latch, barrier, permits, phases hay exchange?
- [ ] Đã đọc return type và exception của **đúng overload**?
- [ ] Timeout trả false, null hay ném TimeoutException?
- [ ] Interruption có ném InterruptedException hay method non-interruptible?
- [ ] Timeout/interrupt có ảnh hưởng trạng thái **toàn nhóm** không?
- [ ] CyclicBarrier broken có được lan lỗi tới các parties khác?
- [ ] Phaser có registration/arrival/termination protocol nếu party hủy?
- [ ] Semaphore chỉ release đúng số permits đã acquire?
- [ ] Thread caller cần propagate hay restore interrupt flag?
- [ ] Các consumer/partners bị mất có làm thread khác chờ mãi?
- [ ] Có deadline tổng với remaining budget thay vì timeout mỗi bước?
- [ ] Sau timeout có task/worker nào tiếp tục chạy và dùng tài nguyên không?
- [ ] Có log metric phân biệt completed, timed out, interrupted, broken?
- [ ] Có tránh giữ lock/permit trong lúc đợi một task cần chính resource đó?
- [ ] Test có dùng CountDownLatch hoặc timeout=0 thay vì sleep và timing assumptions?
- [ ] Khi dùng shutdownNow/cancel, đã hiểu đây là **yêu cầu hủy**?
- [ ] Có bảo đảm memory visibility trước khi dùng shared results?
- [ ] Có xác định ai sở hữu executor, barrier/phaser và cleanup?

## 16. Bài tập luyện tập có đáp án

**Câu 1.** CountDownLatch count=2, gọi `await(0, MILLISECONDS)` và chưa có countDown. Kết quả?

**Đáp án:** false, không chờ; count vẫn là 2.

**Câu 2.** CyclicBarrier có 3 parties, chỉ một party chờ rồi timeout. Hai parties khác sẽ nhận gì nếu đang chờ generation đó?

**Đáp án:** Những waiters còn lại thường nhận BrokenBarrierException, barrier broken. Cần recovery protocol; reset không tự an toàn cho mọi tình huống.

**Câu 3.** Semaphore permit=0, `tryAcquire(100, MILLISECONDS)` không có ai release. Kết quả?

**Đáp án:** false sau khi không lấy được permit trong thời hạn (trừ khi thread bị interrupt, khi đó ném InterruptedException).

**Câu 4.** Semaphore.acquire() ném InterruptedException trước khi lấy permit. Có gọi release không?

**Đáp án:** Không. Chỉ release nếu đã acquire thành công.

**Câu 5.** Phaser có 2 parties, một thread timeout trong `awaitAdvanceInterruptibly(phase, timeout, unit)`. Phaser có tự broken như CyclicBarrier không?

**Đáp án:** Không. Timeout/interrupt của phép đợi không tự thay đổi phaser state; recovery/termination cần do ứng dụng quyết định.

**Câu 6.** Gọi `awaitAdvance(phase)`, bên khác interrupt waiter. Có bắt buộc ném InterruptedException không?

**Đáp án:** Không. Đây là phiên bản non-interruptible; nếu cần phản hồi cancel khi đợi, dùng awaitAdvanceInterruptibly và protocol lifecycle phù hợp.

**Câu 7.** Exchanger chỉ có một thread gọi `exchange("A", 0, MILLISECONDS)`. Kết quả?

**Đáp án:** TimeoutException nếu không có partner, không phải false hoặc null.

**Câu 8.** Một thread bị interrupt trong lúc `CountDownLatch.await()`. Count có tự giảm không?

**Đáp án:** Không. Interrupt chỉ kết thúc phép chờ của thread đó; countDown phải được phát bởi bên chịu trách nhiệm.

**Câu 9.** Workflow có ba bước mỗi bước timeout 300ms, SLA tổng 300ms. Vấn đề?

**Đáp án:** Tổng thời gian chờ có thể gần 900ms. Phải dùng deadline chung và remaining budget.

**Câu 10.** CyclicBarrier đã broken. Gọi await tiếp tục có thể thành công bình thường mà không reset không?

**Đáp án:** Không. Await sẽ ném BrokenBarrierException cho tới khi có chính sách reset/tạo barrier mới phù hợp.

**Câu 11.** Thread.interrupted() có giống currentThread().isInterrupted() không?

**Đáp án:** Đều kiểm tra current thread nếu viết đúng, nhưng Thread.interrupted() **clear flag**, còn isInterrupted() không clear.

**Câu 12.** Timeout của Future.get(timeout) có tự hủy Future task không?

**Đáp án:** Không. Đây là timeout của caller đang chờ kết quả. Muốn hủy phải quyết định cancel theo hợp đồng Future.

## 17. Tài liệu tham khảo chính thức

- **CountDownLatch (Java SE 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html
- **CyclicBarrier (Java SE 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CyclicBarrier.html
- **Semaphore (Java SE 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html
- **Phaser (Java SE 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Phaser.html
- **Exchanger (Java SE 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Exchanger.html
- **TimeUnit (Java SE 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/TimeUnit.html
- **Thread (Java SE 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
- **JLS 17, Threads and Locks:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html

**Tóm tắt:** Timeout và interruption cho phép **chờ có giới hạn và hủy hợp tác**, nhưng từng synchronizer có contract riêng: **latch/semaphore trả boolean false; barrier timeout ném exception và làm broken; phaser timeout ném exception nhưng không tự đổi state; exchanger timeout ném exception khi không có partner**. Xử lý đúng bao gồm deadline tổng, bảo toàn interrupted status, ownership/cleanup permit, khôi phục trạng thái nhóm và tránh vòng chờ tài nguyên.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronizers-007">Barrier action, trạng thái broken và reset</a>

<details>
<summary>Xem chi tiết</summary>

# Barrier action, trạng thái broken và reset trong Java CyclicBarrier

`java.util.concurrent.CyclicBarrier` là một **synchronizer** giúp một nhóm cố định các thread gặp nhau tại cùng một điểm đồng bộ: mỗi bên gọi `await()` sau khi hoàn thành phần việc của mình; **chỉ khi đủ số bên** (parties), barrier mới mở để tất cả tiếp tục sang pha kế tiếp. Sau khi mở thành công, cùng một barrier **tự tái sử dụng** cho nhiều pha, do đó mang tên *cyclic*.

Ba khái niệm cần hiểu sâu là:

1. **Barrier action**: một `Runnable` tùy chọn chạy **đúng một lần cho mỗi lần barrier mở thành công**, trên **thread đến cuối cùng**, sau khi tất cả parties đã đến nhưng **trước khi họ được giải phóng**. Nó thường tổng hợp kết quả của pha vừa xong.
2. **Broken state**: nếu một bên bị interrupt, timeout khi đang chờ, hoặc barrier action thất bại, `CyclicBarrier` có cơ chế **all-or-none breakage**: cả nhóm chờ cùng bị ảnh hưởng, thay vì các bên còn lại đứng chờ tưởng rằng pha vẫn hợp lệ.
3. **Reset**: `reset()` thiết lập **thế hệ barrier mới** sau sự cố. Đây không phải lệnh cứu sống một pha đang chạy; nếu reset khi có thread đang chờ, các thread đó sẽ nhận `BrokenBarrierException`. Phải phối hợp recovery ở cấp ứng dụng.

Bài này trình bày Java concurrency nói chung theo JDK 17+, không sử dụng cơ chế của Spring.

## 1. Bức tranh cơ bản: nhiều worker, một điểm hẹn

Giả sử có ba worker tính toán ba partition dữ liệu, đồng bộ ở cuối mỗi vòng:

```text
Pha 0
Worker A: compute A0 ------ await()
Worker B: compute B0 -- await()       [còn đợi]
Worker C: compute C0 --------- await()   <-- bên đến cuối
                                     |
                                     v
                            barrier action
                            combine A0+B0+C0
                                     |
                                     v
                         tất cả được phép đi tiếp
Pha 1
Worker A: compute A1 ---- await()
Worker B: compute B1 -------- await()
Worker C: compute C1 -- await()
                                     |
                                     v
                            barrier action lần 2
                                     |
                                     v
                         tất cả tiếp tục/hoàn tất
```

`CyclicBarrier(3)` có **3 parties**, không phải “3 lượt await tổng cộng trong cả vòng đời”. Mỗi **generation** cần đủ ba lời `await` tham gia. Khi tripped thành công, barrier chuyển sang generation mới tự động; không gọi `reset()` mỗi vòng.

### Thuật ngữ

| Thuật ngữ | Nghĩa |
| --- | --- |
| **Party** | Một bên tham gia; thường là worker/thread sẽ gọi `await` đúng một lần ở mỗi pha |
| **Generation** | Một lượt chờ của barrier, từ lúc chưa đủ parties tới khi tripped hoặc broken |
| **Arrival** | Một bên gọi `await()` cho generation hiện tại |
| **Trip** | Đủ parties và barrier action (nếu có) hoàn thành thành công; parties được mở |
| **Barrier action** | Runnable chạy một lần trên bên đến cuối trước khi parties được phép rời barrier |
| **Broken barrier** | Thế hệ đồng bộ bị hỏng; không thể coi pha hiện tại đã hoàn tất thành công |
| **Reset** | Kết thúc/loại bỏ generation cũ và tạo generation chờ mới |
| **Arrival index** | Giá trị từ `await()`, từ `parties-1` (đến sớm nhất) đến `0` (đến cuối cùng) |

Không cần mỗi party là một `Thread` cố định về mặt cú pháp, nhưng thuật toán thường cần **đúng N lượt đến cho mỗi pha**. Nếu chỉ có N−1 thread có khả năng gọi `await`, chương trình có thể chờ mãi nếu không dùng timeout/cancellation.

## 2. Các API quan trọng

```java
CyclicBarrier barrier = new CyclicBarrier(3);
CyclicBarrier withAction =
        new CyclicBarrier(3, () -> doPhaseSummary());
```

Đây là snippet, `doPhaseSummary()` là method ứng dụng; cần import `java.util.concurrent.CyclicBarrier`. Số `parties` phải **tối thiểu 1**, nếu không constructor ném `IllegalArgumentException`.

| API | Công dụng | Lưu ý |
| --- | --- | --- |
| `new CyclicBarrier(n)` | Tạo barrier cho n parties | Không có action |
| `new CyclicBarrier(n, action)` | Tạo barrier và action | Action là `Runnable`, không có result trả về |
| `int await()` | Đợi đủ parties | Có thể ném `InterruptedException` hoặc `BrokenBarrierException` |
| `int await(timeout, unit)` | Đợi có giới hạn | Có thể thêm `TimeoutException` |
| `int getParties()` | Số parties cấu hình | Không đổi do reset |
| `int getNumberWaiting()` | Số parties **đang chờ** trong generation hiện tại | Chủ yếu phục vụ debug/assertions, không phải business predicate |
| `boolean isBroken()` | Generation hiện tại đang broken? | Sau reset thường false vì là generation mới |
| `void reset()` | Đưa barrier về trạng thái ban đầu | Giải phóng waiters của generation cũ **bằng exception** |

**Không có** `arriveAndDeregister()` ở CyclicBarrier. Nếu số participants cần tăng/giảm linh hoạt, cân nhắc `Phaser`. Không dùng `reset()` để thay số parties vì nó giữ nguyên `getParties()`.

## 3. Barrier action chạy khi nào và trên thread nào?

Khi một `CyclicBarrier` có action:

```java
CyclicBarrier barrier = new CyclicBarrier(
        3,
        () -> aggregatePreviousPhase()
);
```

Đây là snippet; `aggregatePreviousPhase` là logic ứng dụng.

Trình tự **thành công** của một generation:

1. Các worker hoàn tất phần của mình và gọi `await()`.
2. Những bên đến chưa đủ số parties **chờ** ở barrier.
3. Thread đến **cuối cùng** chạy `barrierAction.run()` ngay trong đường `await` của nó.
4. Khi action kết thúc **bình thường**, generation được mở và các parties được giải phóng.
5. Barrier chuyển generation mới, sẵn sàng cho vòng tiếp theo.

**Không có một “thread action” riêng được tạo mặc định**: chính thread đến cuối thực thi callback. Action không chạy song song một lần trên mỗi party, không được tự schedule như một task riêng của executor.

Nếu số parties là 3, action **không chạy khi chỉ có 2 threads đến**. Điều này khác trigger “đã có ít nhất một kết quả”. Đừng thiết kế callback mà giả định luôn có thread thứ ba nếu task có thể fail/timeout trước khi `await`.

### Arrival index

`await()` trả **arrival index** trong khoảng `0..(parties-1)`:

- `parties - 1`: bên đến **đầu tiên**.
- `0`: bên đến **cuối cùng** (chạy barrier action nếu có).
- Các chỉ số khác: thứ tự đến trung gian.

Không dự đoán worker ID nào nhận index 0: điều đó phụ thuộc scheduling. Muốn chỉ định **một worker thực hiện thao tác sau barrier**, có thể viết:

```java
int index = barrier.await();
if (index == 0) {
    doOnceAfterTrip();
}
```

Đây là snippet trong method xử lý `InterruptedException`/`BrokenBarrierException`; `doOnceAfterTrip` do ứng dụng định nghĩa. Khác barrier action, `doOnceAfterTrip()` nằm **sau `await`**, khi các parties khác **đã có thể** bắt đầu pha tiếp theo. Không dùng mẫu `index==0` như một cách thay thế action nếu thao tác **phải hoàn tất trước khi tất cả các thread được giải phóng**.

### Hành vi khi barrier action thất bại

Nếu action ném unchecked exception, **thread đến cuối** nhìn thấy chính lỗi đó trên đường `await()`; barrier được đánh dấu broken, các bên đang chờ nhận `BrokenBarrierException`. Vì vậy exception ở callback không chỉ làm lỗi một worker; nó **phá tính hợp lệ của cả pha**.

**Không mặc định action luôn chạy:** nếu barrier bị timeout/interrupt/reset trước khi đủ parties, action của generation đó không được chạy trên đường trip thành công. Logic cleanup khi lỗi không nên chỉ đặt trong barrier action.

## 4. Memory consistency: barrier action có thấy dữ liệu của parties không?

Có bảo đảm theo **happens-before** của `CyclicBarrier`:

> Các thao tác của mỗi worker **trước khi `await()`** xảy ra-before các thao tác **trong barrier action**, và các thao tác **trong barrier action** xảy ra-before các thao tác **sau khi `await()` thành công** của các worker khác.

Nhờ đó, mẫu **mỗi worker ghi một vùng không chồng lấn, action đọc các vùng ấy** có thể đúng mà không cần bọc mọi phép gán bằng `synchronized`, miễn là cấu trúc dữ liệu và quyền sở hữu được thiết kế phù hợp:

```java
// Workers (mỗi worker có index riêng):
partialResults[workerIndex] = localCalculation();
barrier.await();

// Barrier action:
int sum = 0;
for (int value : partialResults) {
    sum += value;
}
phaseTotal = sum;
```

Đây là **snippet**, cần tạo `partialResults`, `phaseTotal` và các biến/method tương ứng ở nơi khác. Worker **không ghi cùng phần tử array với worker khác**, action đọc sau khi mọi bên tới barrier; kết quả `phaseTotal` được công bố trước khi các bên rời `await` thành công.

Bảo đảm này **không làm toàn bộ array thread-safe** nếu worker khác vẫn sửa cùng chỉ số đồng thời ngoài đúng protocol. Nếu action giữ reference tới một mutable object và trả về, worker khác có thể bắt đầu sửa object cho phase tiếp theo; code bên ngoài phải biết lúc nào dữ liệu còn ổn định.

### Vì sao action thường dùng để aggregate?

Nếu mỗi worker tự kiểm tra “tôi có phải người đến cuối không” rồi aggregate **sau `await`**, các worker khác đã được thả và có thể làm phase tiếp. Khi đó aggregate có thể đọc dữ liệu đã bị ghi đè bởi phase mới.

Đặt aggregate **trong barrier action** bảo đảm tất cả worker còn bị giữ lại tới khi aggregate hoàn thành. Đây là một lý do quan trọng để chọn action thay vì `if (await() == 0)`.

## 5. Java 17 runnable: action chạy một lần mỗi pha

Ví dụ ba worker chạy **hai pha**. Mỗi worker ghi một phần tử riêng của `partials` theo pha rồi gọi barrier. Action cộng ba phần tử, ghi kết quả pha, tăng action counter. Thread nào đến cuối mỗi pha không quan trọng; chương trình chỉ in thống kê sau khi tất cả futures hoàn tất.

Tạo tệp **`BarrierActionPhasesDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BarrierActionPhasesDemo {
    public static void main(String[] args) throws Exception {
        int parties = 3;
        int phases = 2;
        int[] partials = new int[parties];

        AtomicInteger actions = new AtomicInteger();
        AtomicInteger lastSum = new AtomicInteger();
        AtomicInteger successfulAwaits = new AtomicInteger();
        AtomicInteger lastArrivalReturns = new AtomicInteger();

        CyclicBarrier barrier = new CyclicBarrier(parties, () -> {
            int sum = 0;
            for (int p : partials) {
                sum += p;
            }
            lastSum.set(sum);
            actions.incrementAndGet();
        });

        ExecutorService pool = Executors.newFixedThreadPool(parties);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int id = 0; id < parties; id++) {
                final int workerId = id;
                futures.add(pool.submit(() -> {
                    for (int phase = 0; phase < phases; phase++) {
                        partials[workerId] = workerId + 1 + phase;
                        try {
                            int arrivalIndex =
                                    barrier.await(5, TimeUnit.SECONDS);
                            successfulAwaits.incrementAndGet();
                            if (arrivalIndex == 0) {
                                lastArrivalReturns.incrementAndGet();
                            }
                        } catch (Exception e) {
                            throw new IllegalStateException(
                                    "Barrier failed", e);
                        }
                    }
                }));
            }

            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }

            System.out.println("phases=" + phases);
            System.out.println("barrierActions=" + actions.get());
            System.out.println("lastSum=" + lastSum.get());
            System.out.println(
                    "successfulAwaits=" + successfulAwaits.get());
            System.out.println(
                    "indexZeroReturns=" + lastArrivalReturns.get());
            System.out.println("broken=" + barrier.isBroken());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac BarrierActionPhasesDemo.java
java BarrierActionPhasesDemo
```

Stdout khi tất cả workers hoàn tất trong các deadline:

```text
phases=2
barrierActions=2
lastSum=9
successfulAwaits=6
indexZeroReturns=2
broken=false
```

**Phân tích kết quả:**

- Phase 0: `partials` có `1, 2, 3` → action ghi `6`.
- Phase 1: `partials` có `2, 3, 4` → action ghi `9`; đây là `lastSum` cuối cùng.
- Mỗi phase có ba successful `await` → tổng cộng 6.
- Mỗi phase đúng một thread nhận arrival index 0 → 2 lượt.
- Action chạy đúng một lần ở mỗi phase thành công → 2 lượt; không cần reset.
- Không in worker IDs vì không thể dự đoán thread nào đến cuối.
- `Future.get` chờ tất cả workers hoàn tất rồi main mới in, nên kết quả không phụ thuộc thứ tự in của threads.

**Giới hạn:** mỗi worker ghi riêng phần tử `partials[workerId]` và không có writer khác sửa cùng index trong phase đó. Action chạy trước khi release nên aggregate không bị trộn dữ liệu phase kế tiếp. Ví dụ không cho thấy hành vi khi một task thất bại; các phần sau xử lý đường lỗi.

## 6. Broken barrier là gì?

`CyclicBarrier` dùng mô hình **all-or-none breakage**. Khi một bên không thể chờ tới đủ parties do một nguyên nhân làm hỏng generation, các bên đang chờ cùng bị buộc rời điểm hẹn bằng exception; barrier không cho phần còn lại đi tiếp như một lần trip thành công giả tạo.

Các nguyên nhân quan trọng:

- Một waiter bị **interrupt** trong lúc chờ.
- Một waiter hết **timeout** ở `await(timeout, unit)`.
- **Barrier action ném exception**.
- Ai đó gọi **`reset()`** khi đang có waiters.
- Một thread mới gọi `await` khi barrier hiện tại **đang broken**, nó sẽ nhận `BrokenBarrierException` (theo contract, với lưu ý precedence của interruption).

### Bảng trạng thái theo nguyên nhân

| Nguyên nhân | Thread trực tiếp gặp lỗi | Những thread khác đang đợi | `isBroken()` sau sự cố, trước reset |
| --- | --- | --- | --- |
| Một waiter hết timeout | `TimeoutException` | Thường `BrokenBarrierException` | `true` |
| Một waiter bị interrupt | `InterruptedException` (flag thường được xóa khi ném) | Thường `BrokenBarrierException` | `true` |
| Barrier action ném unchecked exception | Thread chạy action nhận exception gốc | `BrokenBarrierException` | `true` |
| Gọi `reset()` khi waiters đang chờ | Thread gọi reset return bình thường | Waiters nhận `BrokenBarrierException` | **`false` ngay sau reset** vì generation mới |
| Một caller mới vào barrier đã broken | `BrokenBarrierException` | Không mở generation hỏng | `true` |

**Phân biệt rõ:** `reset()` **làm hỏng generation cũ và tạo generation mới**. Do đó không có lý do `isBroken()` phải còn true **ngay sau reset**. Nhưng những waiters thuộc generation cũ vẫn nhận `BrokenBarrierException`.

Một waiter có thể đồng thời bị interrupt đúng lúc sự cố khác xảy ra và nhận `InterruptedException` thay vì `BrokenBarrierException`. JDK cho phép các interleavings như vậy; không viết test mà khẳng định mọi waiter luôn nhận đúng một loại exception khi nhiều nguyên nhân xảy ra đồng thời.

### `BrokenBarrierException` có nghĩa “lỗi ở worker này” không?

Không nhất thiết. Nó thường cho biết **một bên khác** hoặc action của cả barrier đã làm generation thất bại. Khi thấy exception này, hãy tìm **nguyên nhân đầu tiên**: worker nào timeout, ai bị interrupt, action nào ném exception, có reset đồng thời không. Không xử lý bằng cách catch rồi bỏ qua để worker tiếp tục phase như thể dữ liệu đã được tổng hợp hoàn chỉnh.

## 7. Java 17 runnable: exception trong barrier action

Ví dụ có hai parties. Barrier action luôn ném `IllegalStateException`. Thread đến cuối chạy action nên nhận exception gốc; thread còn lại nhận `BrokenBarrierException`. Chương trình thu thập counter để stdout không phụ thuộc worker nào đến cuối.

Tạo **`BarrierActionFailureDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BarrierActionFailureDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger actionErrors = new AtomicInteger();
        AtomicInteger brokenWaiters = new AtomicInteger();

        CyclicBarrier barrier = new CyclicBarrier(2, () -> {
            throw new IllegalStateException("action failed");
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        barrier.await();
                        throw new AssertionError(
                                "Action should not succeed");
                    } catch (IllegalStateException e) {
                        if (!"action failed".equals(e.getMessage())) {
                            throw e;
                        }
                        actionErrors.incrementAndGet();
                    } catch (BrokenBarrierException e) {
                        brokenWaiters.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }));
            }

            for (Future<?> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
            System.out.println("actionErrors=" + actionErrors.get());
            System.out.println("brokenWaiters=" + brokenWaiters.get());
            System.out.println("broken=" + barrier.isBroken());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

```bash
javac BarrierActionFailureDemo.java
java BarrierActionFailureDemo
```

Stdout trên đường chạy bình thường:

```text
actionErrors=1
brokenWaiters=1
broken=true
```

**Lập luận:** Barrier có hai parties, vì vậy một thread tới trước sẽ chờ và một thread tới cuối sẽ chạy callback. Callback ném unchecked exception → thread cuối nhận `IllegalStateException("action failed")`; waiter còn lại được giải phóng bằng `BrokenBarrierException`; barrier ở trạng thái broken.

Nếu một lần `await()` khác được gọi trên **barrier vẫn broken**, nó cũng không mở pha mới thành công mà ném `BrokenBarrierException`. Muốn tái sử dụng sau sự cố, cần recovery và `reset` hoặc tạo barrier mới.

**Điểm rất quan trọng:** `reset()` **không sửa lỗi trong barrier action**. Nếu reset rồi hai parties tới lại, callback vẫn ném lỗi đó và barrier lại broken. Cần sửa root cause, thay implementation action hay thay barrier nếu thích hợp, không chỉ reset lặp vô hạn.

## 8. Timeout: một bên thiếu làm hỏng cả generation

Dùng timeout khi một bên có thể không đến vì I/O, queue starvation, cancellation hoặc sự cố tài nguyên. Nếu `await(300, TimeUnit.MILLISECONDS)` hết hạn mà chưa đủ parties:

1. Thread hết hạn nhận `TimeoutException`.
2. Barrier được đánh dấu **broken**.
3. Các threads khác đang chờ cùng generation được đánh thức và thường nhận `BrokenBarrierException`.
4. Những arrivals sau đó cũng không thể dùng generation broken cho tới khi reset.

**Không nhầm timeout của barrier với timeout xử lý nghiệp vụ.** Một worker có thể mất 10 giây tính toán *trước khi gọi await*; nếu tất cả các waiter khác đặt deadline 1 giây tại barrier, họ có thể timeout trước khi worker chậm đến. Thời hạn phải phản ánh tính chất phase, deadline end-to-end và downstream.

### Java 17 runnable: timeout, broken, reset, reuse

Tạo **`BarrierTimeoutRecoveryDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class BarrierTimeoutRecoveryDemo {
    public static void main(String[] args) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger successfulNextGeneration = new AtomicInteger();

        try {
            Future<Boolean> lone = pool.submit(() -> {
                try {
                    barrier.await(200, TimeUnit.MILLISECONDS);
                    return false;
                } catch (TimeoutException expected) {
                    return true;
                }
            });

            boolean timedOut = lone.get(5, TimeUnit.SECONDS);
            boolean brokenAfterTimeout = barrier.isBroken();

            boolean laterWaiterRejected;
            try {
                barrier.await();
                laterWaiterRejected = false;
            } catch (BrokenBarrierException expected) {
                laterWaiterRejected = true;
            }

            barrier.reset();
            boolean brokenAfterReset = barrier.isBroken();

            List<Future<?>> reused = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                reused.add(pool.submit(() -> {
                    try {
                        barrier.await(5, TimeUnit.SECONDS);
                        successfulNextGeneration.incrementAndGet();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }));
            }
            for (Future<?> f : reused) {
                f.get(10, TimeUnit.SECONDS);
            }

            System.out.println("timedOut=" + timedOut);
            System.out.println("brokenAfterTimeout=" + brokenAfterTimeout);
            System.out.println(
                    "laterWaiterRejected=" + laterWaiterRejected);
            System.out.println("brokenAfterReset=" + brokenAfterReset);
            System.out.println("successfulNextGeneration="
                    + successfulNextGeneration.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

```bash
javac BarrierTimeoutRecoveryDemo.java
java BarrierTimeoutRecoveryDemo
```

Stdout khi chương trình hoàn thành bình thường:

```text
timedOut=true
brokenAfterTimeout=true
laterWaiterRejected=true
brokenAfterReset=false
successfulNextGeneration=2
```

**Vì sao kết quả không phụ thuộc sleep/scheduling?**

- Generation đầu có **2 parties nhưng chỉ 1 thread `await`**, do đó không thể trip thành công. Nó sẽ timeout sau thời gian được cấu hình.
- Main **chờ Future** hoàn tất rồi mới đọc `isBroken`, tránh race với thời điểm timeout.
- Một lời `await()` trên barrier broken ném `BrokenBarrierException` **ngay**, không chờ đủ parties.
- `reset()` tạo generation mới, `isBroken=false`.
- Hai tasks mới được gửi vào executor 2 workers, gọi `await` trong cùng generation mới nên có thể trip thành công. Main chờ cả hai futures trước khi in.
- Deadline 200 ms trong demo phục vụ **đường timeout có chủ đích**, không chứng minh một worker luôn được scheduler chạy chính xác ở mốc 200 ms.

Cần lưu ý `await()` trên main khi barrier broken sẽ ném theo hợp đồng; nếu main tình cờ bị interrupt sẵn, precedence exception có thể khác. Chương trình giả định main không bị interrupt từ nguồn ngoài trong lúc test bình thường.

## 9. Interrupt và chính sách all-or-none

Khi một waiter `await()` bị interrupt, nó nhận `InterruptedException`, và **trạng thái interrupt được xóa khi exception được ném** theo contract. Barrier thế hệ đó bị broken; các bên đang chờ khác thường nhận `BrokenBarrierException`.

Ví dụ **snippet**:

```java
try {
    barrier.await();
    useCompletedPhase();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    stopThisWorker();
} catch (BrokenBarrierException e) {
    abortThisPhase();
}
```

Các methods `useCompletedPhase`, `stopThisWorker`, `abortThisPhase` là nghiệp vụ của ứng dụng. Nếu method không có khả năng propagate `InterruptedException` ra ngoài, sau khi catch cần thường **restore interrupt flag** rồi **thoát đường xử lý**, thay vì nuốt cờ và chờ lại ngay.

Một người bị interrupt có thể khiến **cả thế hệ barrier hỏng**, vì vậy không thể để những parties khác cứ tính tiếp và giả định có kết quả đầy đủ. Nếu workflow cần partial results, phải thiết kế hợp đồng riêng cho việc chấp nhận thiếu worker.

### Không nuốt `BrokenBarrierException`

```java
try {
    barrier.await();
} catch (BrokenBarrierException ignored) {
    // SAI nếu tiếp tục coi phase đã hoàn thành.
}
continueUsingAllPartitions();
```

Đây là snippet **sai về business semantics**; `continueUsingAllPartitions` là method minh họa. Khi barrier broken, một hoặc nhiều participants chưa đến hoặc action thất bại. Những kết quả cần “đủ mọi partition” chưa được xác nhận. Người điều phối nên abort/retry/compensate theo generation, chứ không bỏ qua exception.

### Có nên auto-reset trong catch của mỗi waiter?

**Không.** Nếu tất cả parties đồng thời catch `BrokenBarrierException` rồi mỗi thread tự gọi `reset()`, sẽ xảy ra reset races: thread A vừa tham gia generation mới có thể bị thread B reset làm hỏng tiếp. Recovery cần **một coordinator/leader** theo protocol rõ, hoặc tạo barrier mới cho một nhóm workers được tạo/điều phối lại.

## 10. `reset()` thực sự làm gì?

Javadoc mô tả `reset()` đưa barrier về trạng thái ban đầu. Điều này có hai ý nghĩa riêng:

**A. Nếu barrier hiện đang broken:** reset kết thúc thế hệ cũ và mở một thế hệ mới **không broken** để một nhóm parties có thể chờ lại.

**B. Nếu barrier đang có waiters, chưa đủ parties:** reset **kết thúc thế hệ chờ ấy bất thường**. Những waiters đó nhận `BrokenBarrierException`; sau reset barrier đã sẵn sàng cho **một lượt đợi khác**. Điều này không giống `notifyAll` “cho qua thành công”.

```text
Generation G0, parties=3:
    Thread A -> await (WAITING)
    Thread B -> await (WAITING)
    Thread C -> chưa đến

Coordinator -> reset()

    A -> BrokenBarrierException (G0 invalid)
    B -> BrokenBarrierException (G0 invalid)
    G1 mới: isBroken() == false, waiting == 0

Sau khi coordinator xử lý/đồng bộ lại:
    A/B/C có thể cùng await() ở generation G1
```

**Không gọi reset ở mọi pha**: CyclicBarrier tự động chuyển generation sau trip thành công. Reset chủ yếu phục vụ **recovery/abandon** sau sự cố, và phải phối hợp để không có parties cũ lẫn parties mới.

### Cẩn thận với “reset nghĩa là phục hồi ngay mọi task”

Reset chỉ thay trạng thái **barrier**, không:

- Khởi động lại worker bị chết.
- Rollback side effects trước khi bị broken.
- Hoàn thành barrier action từng ném exception.
- Tự sinh parties còn thiếu.
- Hủy mọi future/task đã submit.
- Đặt lại state nghiệp vụ và dữ liệu của các worker về một snapshot đồng bộ.
- Chữa lỗi do executor có ít workers hơn số parties cần đợi.

Do đó nếu có lỗi nghiêm trọng, **tạo barrier mới** có thể dễ suy luận hơn reset tại chỗ, nhất là khi nhiều threads đã đi vào các nhánh recover khác nhau.

## 11. Java 17 runnable: reset khi có hai parties đang đợi

Ví dụ này có một barrier cần ba parties, nhưng chỉ submit hai waiters đầu. Main đợi tới khi **`getNumberWaiting()==2`** (với deadline) rồi gọi reset. Hai waiters nhận `BrokenBarrierException`. Sau khi cả hai Future đã hoàn thành, ta submit ba waiters mới để chứng minh barrier generation mới được dùng thành công.

Tạo **`BarrierResetWaitersDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BarrierResetWaitersDemo {
    public static void main(String[] args) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(3);
        AtomicInteger releasedByReset = new AtomicInteger();
        AtomicInteger nextGenerationSuccess = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(3);

        try {
            List<Future<?>> oldWaiters = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                oldWaiters.add(pool.submit(() -> {
                    try {
                        barrier.await();
                        throw new AssertionError(
                                "Old generation must not trip");
                    } catch (BrokenBarrierException expected) {
                        releasedByReset.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }));
            }

            long deadline =
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (barrier.getNumberWaiting() != 2) {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException(
                            "Two waiters not observed");
                }
                Thread.onSpinWait();
            }

            barrier.reset();
            for (Future<?> f : oldWaiters) {
                f.get(5, TimeUnit.SECONDS);
            }

            boolean brokenAfterReset = barrier.isBroken();

            List<Future<?>> nextWaiters = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                nextWaiters.add(pool.submit(() -> {
                    try {
                        barrier.await(5, TimeUnit.SECONDS);
                        nextGenerationSuccess.incrementAndGet();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }));
            }

            for (Future<?> f : nextWaiters) {
                f.get(10, TimeUnit.SECONDS);
            }

            System.out.println(
                    "releasedByReset=" + releasedByReset.get());
            System.out.println(
                    "brokenAfterReset=" + brokenAfterReset);
            System.out.println(
                    "nextGenerationSuccess="
                    + nextGenerationSuccess.get());
            System.out.println(
                    "waitingNow=" + barrier.getNumberWaiting());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

```bash
javac BarrierResetWaitersDemo.java
java BarrierResetWaitersDemo
```

Stdout nếu hoàn thành bình thường trong deadline:

```text
releasedByReset=2
brokenAfterReset=false
nextGenerationSuccess=3
waitingNow=0
```

**Phân tích:**

- G0 đòi 3 parties nhưng chỉ có 2 threads gọi `await` nên cả hai chờ.
- Main dùng `getNumberWaiting()==2` làm **test checkpoint có kiểm soát** và gọi `reset` sau khi biết cả hai đang đợi. Hàm getter chủ yếu phục vụ debug/assertions; **không dùng snapshot này làm giao thức điều phối production** giữa nhiều resetters.
- Hai waiters G0 không được coi là thành công: mỗi người nhận `BrokenBarrierException` rồi return.
- Main đợi hai Future cũ hoàn tất **trước khi** submit ba tasks mới vào G1; tránh trộn generation trong bài test.
- G1 có đủ ba workers và tripped thành công. `getNumberWaiting()==0` sau khi cả ba futures đã hoàn tất.
- `reset()` không làm parties đổi từ 3 thành 2; G1 vẫn cần 3 arrivals.

**Lưu ý:** trong production, khi một coordinator quyết định reset do lỗi, cần có quy tắc ai được reset và cách các workers tái đồng bộ. Một vòng `while (getNumberWaiting()<N)` dùng cho giám sát không thể thay thế một barrier/protocol an toàn khi các threads khác có thể thay generation đồng thời.

## 12. Khi nào `getNumberWaiting()` và `isBroken()` hữu ích?

Hai getters có ý nghĩa quan sát:

```java
int configured = barrier.getParties();
int waiting = barrier.getNumberWaiting();
boolean broken = barrier.isBroken();
```

Đây là snippet (cần biến `barrier` được định nghĩa). Chúng có thể giúp xác định:

- Barrier đang đợi bao nhiêu parties?
- Có bao nhiêu bên còn thiếu cho thế hệ hiện tại?
- Thế hệ hiện tại đã broken do timeout/interrupt/action failure chưa?
- Sau reset có đang ở generation mới hay không?

**Nhưng:** đọc ba getters lần lượt **không phải snapshot nhất quán nguyên tử**. Workers có thể vừa trip hoặc reset giữa các lần đọc. `getNumberWaiting()` được Javadoc định hướng **debugging và assertions**, không phải điều kiện business để quyết định reset khi nhiều actor có thể chạy đồng thời.

Một barrier đang chờ có thể hoàn toàn bình thường (pha tính toán chưa đủ parties). Đừng tuyên bố deadlock chỉ vì thấy `WAITING` trên stack. Cần theo dõi ai chưa đến, queue/executor có worker không, I/O có timeout không và flow có exit/cancellation không.

### Ví dụ pool quá nhỏ

Nếu cấu hình `newFixedThreadPool(2)` nhưng submit **3 tasks** mỗi task gọi `barrier.await()` trên barrier có `parties=3`, hai worker đầu có thể chiếm hết threads và đợi bên thứ ba đang nằm trong queue **không bao giờ được chạy**. Đây là **thread-starvation deadlock** do sizing/graph phụ thuộc, không phải lỗi của `reset`.

Giải pháp có thể là pool đủ worker đồng thời, mô hình non-blocking/phased khác, hoặc tổ chức execution sao cho parties không phải chặn toàn bộ khả năng chạy của parties còn lại. Timeout chỉ là cơ chế fail-fast chứ không sửa được thiết kế thiếu capacity.

## 13. Action nặng hoặc blocking có vấn đề gì?

Barrier action chạy trên **thread đến cuối cùng** và tất cả parties khác đang đợi nó hoàn tất. Nếu action:

- Gọi I/O rất lâu.
- Thực hiện DB transaction nặng.
- Chờ `Future.get` của task cần một trong các worker đang ở barrier.
- Lấy mutex đang bị giữ bởi thread ở ngoài hoặc trong một chu trình chờ.
- Gọi lại `barrier.await` theo một protocol không hợp lệ.
- Ném unchecked exception do bug.

thì toàn pha có thể bị trì hoãn hoặc thất bại. Callback nên **ngắn, xác định và có tài nguyên/timeout rõ**. Nếu aggregation nặng, cân nhắc phân vùng kết quả, gom từng phần trước barrier hoặc chuyển xử lý sang một phase có người điều phối riêng; nhưng phải giữ điều kiện visibility và phase ownership.

**Một điểm tinh vi:** các waiter có timeout không nhất thiết có thể “phá” ngay một barrier action đang chạy dài nếu action đang giữ cơ chế đồng bộ bên trong của barrier; hợp đồng không cho phép dùng `await(timeout)` như một nút cưỡng chế ngắt bất kỳ callback nào. Đừng đặt callback có I/O vô thời hạn rồi tin một timeout ở caller sẽ dừng được nó.

### Action failure và cleanup

Barrier action thất bại có thể làm generation broken, nhưng worker trong partition khác có thể đã nắm resource hoặc có side effects trước khi chờ. Hãy:

1. Đóng resource per-task trong `finally`/`try-with-resources`.
2. Không commit kết quả incomplete sau khi nhận `BrokenBarrierException`.
3. Ghi log nguyên nhân đầu tiên (timeout/interrupt/action failure).
4. Chọn retry/abort policy tại cấp coordinator.
5. Nếu retry, tạo state mới hoặc rollback/compensate partial writes theo business contract.

Một action có side effect không idempotent (gửi payment/email) cần đặc biệt cẩn trọng nếu ứng dụng retry phase: một generation bị lỗi giữa chừng không tạo exactly-once semantics.

## 14. Recovery tốt hơn reset vô điều kiện

Một quy trình phục hồi được cân nhắc:

**Bước 1: Stop accepting work vào generation cũ.** Đưa phase vào trạng thái failed/cancelled ở cấp ứng dụng, ví dụ một `AtomicReference<State>`/owner coordinator.

**Bước 2: Collect failures.** Xác định bên nào bị interrupt, timeout, action nào thất bại và tasks nào còn chạy. Không chỉ bắt `BrokenBarrierException` ở một worker rồi suy ra nguyên nhân.

**Bước 3: Drain/terminate old work.** Hủy hợp tác các workers còn đang xử lý generation cũ; đợi cleanup phù hợp, tránh state cũ và mới ghi chung dữ liệu.

**Bước 4: Reset hoặc replace.** Nếu group parties/contract không đổi và mọi actor đã tái đồng bộ, có thể `reset()`. Khi generation cũ đã phức tạp, tạo một `CyclicBarrier` mới và công bố an toàn cho các workers mới thường rõ ràng hơn.

**Bước 5: Prepare new state.** Phân vùng dữ liệu và biến tổng hợp cho generation mới, không tái sử dụng partial results hỏng.

**Bước 6: Restart only with full participant plan.** Đảm bảo đủ parties sẽ có cơ hội tới `await`; tránh submit các blocking workers vào pool không đủ capacity.

**Bước 7: Verify success.** Đếm các phase hoàn tất và business outputs; không chỉ nhìn `isBroken()==false`, vì một barrier chưa có ai tới cũng false.

### Đừng reset tự động mỗi khi thấy broken

Một vòng vô hạn kiểu:

```java
for (;;) {
    try {
        barrier.await();
        break;
    } catch (BrokenBarrierException e) {
        barrier.reset(); // SAI nếu mỗi worker tự reset.
    }
}
```

là **snippet sai**; các workers có thể liên tục reset generation mới của nhau, mất khả năng tiến triển. Nếu thực sự muốn retry, hãy có một coordinator độc quyền quyết định, stop/restart protocol và deadline.

## 15. Barrier action so với logic sau `await()`

Hai cách chỉ khác nhau vài dòng nhưng khác contract:

### Cách A — barrier action

```java
CyclicBarrier barrier = new CyclicBarrier(
        parties, () -> publishAggregate());
```

Đây là snippet; action **xong trước** khi workers được phép đi tiếp. Có happens-before từ writes trước await tới action rồi tới các operations sau successful await.

### Cách B — leader sau await

```java
int index = barrier.await();
if (index == 0) {
    publishAggregate();
}
```

Đây là snippet; **các workers khác đã được giải phóng** khi leader làm aggregate. Nếu các workers có thể viết đè partial state cho pha mới, leader có thể đọc sai. Cần thêm một barrier thứ hai sau aggregation, chuyển ownership/immutable snapshot, hoặc đặt aggregate trong barrier action.

**Không dùng cùng một object barrier để tạo “thêm barrier thứ hai” một cách ngẫu nhiên** nếu các parties có số lượt `await` không thống nhất: thế hệ thứ hai có thể bị trộn với thế hệ tiếp theo của một worker. Thiết kế phase protocol phải rõ cho mọi participant.

## 16. CyclicBarrier so với CountDownLatch và Phaser

| Thuộc tính | `CyclicBarrier` | `CountDownLatch` | `Phaser` |
| --- | --- | --- | --- |
| Mục đích | N parties cùng gặp ở điểm hẹn | Một hoặc nhiều threads đợi count về 0 | Đồng bộ nhiều pha, số parties có thể thay đổi |
| Tái sử dụng sau thành công | **Có**, tự mở generation mới | **Không**, latch một lần | **Có**, chuyển phase |
| Đăng ký/hủy participant | `parties` cố định | Initial count cố định | `register`/`arriveAndDeregister` |
| Action khi hoàn tất pha | Constructor `Runnable barrierAction` | Không có callback sẵn | Có thể override `onAdvance` |
| Failure do timeout/interrupt waiter | Generation broken, các waiter khác nhận lỗi | Một waiter bị interrupt không tự “break” latch | Semantics khác; cần thiết kế termination/error riêng |
| Reset | Có, **phá generation cũ nếu đang chờ** | Không có reset | Không phải cùng mô hình reset; `forceTermination` và phase controls |
| Cách chờ | `await` của các parties | `await` trên latch, `countDown` riêng | `arriveAndAwaitAdvance`/`awaitAdvance` |
| Nên dùng khi | Nhóm cố định tính toán theo vòng | Một lần khởi động/hoàn thành | Nhiều pha với participants động |

**CyclicBarrier khác CountDownLatch ở chỗ parties đi qua barrier cùng nhau**, còn latch có thể được `countDown()` trước khi bất kỳ thread nào `await()` và sau khi count về 0, latch luôn mở.

**Phaser khác CyclicBarrier** khi một số workers có thể đăng ký thêm hoặc rút khỏi vòng. Không cố “rút party” bằng cách reset CyclicBarrier: `getParties` vẫn giữ nguyên.

Nếu bài toán chỉ là đợi ba Futures hoàn thành **một lần**, `CompletableFuture.allOf(...)` hoặc `CountDownLatch` thường đơn giản hơn CyclicBarrier.

## 17. Có nên dùng barrier cho I/O-bound workload?

`CyclicBarrier` phù hợp hơn khi nhóm workers biết chắc cần **đồng bộ từng phase**. Với I/O-bound workload:

- Một API chậm có thể khiến mọi party chờ theo “slowest participant”.
- Nhiều platform threads chờ sẽ chiếm worker slots, dù CPU rảnh.
- Nếu một request timeout/hủy thì cả generation có thể broken.
- Nếu executor không đủ workers để cùng tham gia, có starvation.
- Một phần output đã được tạo trước barrier vẫn cần cleanup khi generation failed.

Trong hệ thống xử lý streams/independent requests, barrier có thể làm giảm throughput nếu ép các công việc không liên quan phải “đợi người chậm nhất”. Cân nhắc asynchronous composition, queues, futures hoặc phaser tùy mô hình phụ thuộc. **Đừng dùng barrier chỉ để tạo cảm giác “có synchronization”**.

### Virtual threads từ Java 21

Virtual threads vẫn tuân thủ Java Memory Model và có thể chờ trên synchronization primitives, nhưng `CyclicBarrier` vẫn có **số parties cố định và hợp đồng all-or-none**. Dùng virtual threads không làm một bên bị thiếu tự xuất hiện, không ngăn barrier action ném exception và không thay đổi semantics `reset()`.

Các demo trong bài chạy bằng Java 17, không yêu cầu virtual-thread API.

## 18. Debug một hệ thống đứng ở barrier

Khi thấy thread dump có nhiều tasks ở `CyclicBarrier.await`:

1. **Số parties được yêu cầu?** `getParties()` là bao nhiêu?
2. **Bao nhiêu thread đang đợi?** `getNumberWaiting()`, đọc như snapshot/debug.
3. **Party còn thiếu ở đâu?** Đang compute, chờ I/O, queue executor, bị exception hay đã shutdown?
4. **Executor đủ capacity?** Có N−1 workers chờ và party N nằm trong queue của same pool không?
5. **Có failure trước khi await?** Worker ném exception rồi chết mà không báo coordinator, các bên khác có thể chờ mãi nếu không timeout.
6. **Barrier action có đang chạy?** Callback nặng/lock/I/O hay ném exception?
7. **`isBroken()` là gì?** Nếu true, tìm root cause và không cứ await tiếp.
8. **Có reset race?** Nhiều threads tự reset khi catch broken hoặc manager reset khi workers đang lẫn pha?
9. **Có timeout/cancellation policy?** Nếu không, một bên thiếu có thể treo cả nhóm.
10. **Dữ liệu phase có ownership không?** Có worker sửa partition mới trước khi action đọc xong không?
11. **Lấy nhiều snapshot/JFR**: tránh kết luận từ một thread dump đơn lẻ.
12. **Đối chiếu epoch/generation** bằng state do ứng dụng quản lý; CyclicBarrier không public generation identifier.

Khi instrument, có thể gắn một `phaseId`/runId do ứng dụng tự tạo vào logging. Không lấy `arrivalIndex` làm ID generation hoặc worker identity; nó chỉ là thứ tự đến trong một generation.

## 19. Những lỗi thường gặp

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Nghĩ barrier action chạy trên thread riêng | Sizing và latency sai | Action do **last arrival** chạy |
| Nghĩ action chạy khi chỉ N−1 parties đến | Callback không bao giờ chạy | Đảm bảo đủ parties hoặc timeout |
| Gọi action sau `await` và tưởng mọi bên vẫn bị giữ | Worker khác có thể bắt đầu phase sau | Đặt aggregate vào barrier action |
| Quên `await` ở một branch | Parties còn lại chờ mãi | Cùng protocol mọi paths, timeout |
| Cấp pool size nhỏ hơn parties mà tất cả đều block | Thread-starvation deadlock | Đủ execution capacity hoặc đổi thiết kế |
| Bắt `BrokenBarrierException` rồi tiếp tục bình thường | Dùng incomplete phase results | Abort/recover rõ |
| Mỗi waiter tự gọi `reset` | Reset race, có thể liên tục break | Một coordinator/leader |
| Reset khi đang có waiters và mong họ thành công | Họ nhận `BrokenBarrierException` | Phân biệt abandon với trip |
| `isBroken()==false` là bằng chứng pha hoàn tất | Một barrier mới cũng false | Đo output và completion state |
| Nghĩ reset sửa callback lỗi | Action cũ vẫn ném | Sửa root cause hoặc thay barrier |
| Action ném exception nhưng chỉ log ở waiter khác | Mất lỗi gốc | Collect failure ở last arrival |
| Bỏ qua `InterruptedException` | Tác vụ không dừng/đi sai phase | Propagate hoặc restore + exit |
| Nghĩ timeout ở await có thể kill callback blocking | Action có thể chặn lâu | Timeouts ở resource/callback |
| Dùng `getNumberWaiting` làm transactional state | Getter chỉ snapshot | Ứng dụng có state/ownership riêng |
| Dùng `arrivalIndex==0` như worker cố định | Worker đến cuối thay theo lịch | Không gắn logic vào ID worker |
| `reset` để giảm `getParties` | Số parties không đổi | Phaser/thiết kế mới |
| Đặt side effects không idempotent trong action rồi retry bừa | Có thể phát tác dụng phụ trùng | State machine/idempotency |
| Suy ra barrier bảo vệ mọi mutable field | Chỉ có HB theo protocol | Quy định ownership, không race ngoài pha |

## 20. Kiểm thử các interleavings cần thiết

Một bộ test đủ tốt không chỉ in một phase thành công. Nên có:

1. Barrier 1 party: action có chạy khi party duy nhất `await`?
2. 2–3 parties, nhiều pha thành công, action count đúng mỗi pha.
3. Một party tới trễ nhưng **trước deadline**, không broken.
4. Một party **không tới**, timed await ném `TimeoutException`, `isBroken=true`.
5. Một waiter bị interrupt, các waiter khác rời bằng `BrokenBarrierException` theo contract.
6. Action ném unchecked exception; last arrival nhận lỗi gốc, các waiters còn lại nhận broken.
7. Reset khi đang có waiters; họ bị broken dù sau reset `isBroken=false`.
8. Reset sau lỗi rồi **đủ N parties mới** dùng lại thành công.
9. Nhiều phases với worker IDs khác nhau nhận arrival index 0; **không assert thứ tự thread**.
10. Dữ liệu phase được tổng hợp chính xác trong action trước release.
11. Executor thiếu worker: phát hiện deadlock/starvation bằng timeout test.
12. Race giữa cancel/reset/late arrival: không lẫn dữ liệu giữa các generations.
13. Callback chặn lâu: coordinator phát hiện và có recovery ngoài barrier.
14. Cleanup resource trong `finally` khi await ném exception.
15. Hệ thống nhiều generations: business output không bị ghi đè/trộn phase.

### Không dùng `Thread.sleep` làm bằng chứng scheduling

`Thread.sleep(10)` không bảo đảm thread A đã vào `await` trước thread B hay callback đã hoàn tất. Để kiểm thử:

- Dùng `CountDownLatch` cho checkpoints trước khi submit/await.
- Dùng `Future.get(timeout)` cho deadline tránh treo.
- Với reset test, kiểm tra `getNumberWaiting` **chỉ khi đã cô lập mọi actor** và có deadline.
- Tổng hợp counters rồi main in sau `Future.get`, không phụ thuộc thứ tự log từ nhiều workers.
- Không để các tests tạo thread pools không shutdown.

## 21. Câu hỏi tự kiểm tra

**1. Barrier action chạy trên thread nào?** Thread đến **cuối cùng** tại barrier của generation đó, trước khi các parties khác được release.

**2. Barrier action có chạy nếu một party timeout trước khi đủ N?** Không theo đường trip thành công; generation broken, các parties đang chờ nhận exception.

**3. `await()` trả `0` nghĩa gì?** Đây là party đến cuối cùng trong generation ấy, không phải worker có ID 0.

**4. Gọi `reset()` sau mỗi pha thành công có cần thiết không?** Không. CyclicBarrier tự tái sử dụng sau successful trip.

**5. Reset khi hai waiters đang chờ thì họ được đi tiếp bình thường không?** Không. Họ nhận `BrokenBarrierException`; reset tạo generation mới.

**6. Sau reset, `isBroken()==false` có nghĩa generation cũ thành công?** Không. Nó chỉ nói generation mới đang không broken.

**7. Action ném `IllegalStateException` thì thread nào nhận nó?** Thread đến cuối chạy action; các parties khác đang chờ nhận `BrokenBarrierException`.

**8. Nếu một waiter bị interrupt, chỉ nó bị ảnh hưởng?** Không. Generation bị broken và các waiters khác thường nhận broken exception.

**9. `await(timeout,unit)` timeout có tự reset barrier không?** Không. Barrier broken cho đến khi có recovery/reset hoặc thay mới.

**10. `getNumberWaiting()` có là điều kiện đồng bộ mạnh không?** Không; chủ yếu là debugging/assertions snapshot, có race khi nhiều actors thay đổi trạng thái.

**11. Nếu pool có 2 threads mà barrier đòi 3, submit 3 blocking tasks thì sao?** Hai tasks đầu có thể chiếm worker đợi task thứ ba mãi ở queue; phải thiết kế execution capacity/protocol khác.

**12. Khi nào cần barrier action thay `if (await()==0)`?** Khi aggregate/phân phối state **phải hoàn tất trước** khi bất kỳ worker nào chuyển sang pha sau.

**13. `reset` có thay đổi `getParties` không?** Không. Muốn số participants động hãy cân nhắc Phaser.

**14. Barrier action có nghĩa mọi field shared đều thread-safe?** Không. Chỉ có memory consistency theo happens-before của protocol; concurrent writes ngoài vùng/phase vẫn có thể race.

**15. Có nên cho mọi thread tự reset trong catch `BrokenBarrierException`?** Không. Cần một coordinator và tái đồng bộ rõ ràng để tránh reset races.

**16. `CyclicBarrier` có giống CountDownLatch không?** Không. Barrier chờ các parties cùng đến và tái sử dụng; latch có counter một chiều, không reset.

**17. `isBroken()` true nghĩa là barrier không dùng lại được mãi mãi?** Không. Có thể reset theo protocol hoặc tạo barrier mới; nhưng nếu lỗi nguyên nhân còn tồn tại, sẽ broken lần nữa.

**18. Có thể dùng reset để thay thế failure compensation không?** Không. Nó không rollback side effects hay cleanup I/O của các worker.

## 22. Checklist thiết kế production

- Đã định nghĩa **parties và generation** theo workflow chưa?
- Mọi worker có chắc gọi `await` đúng **một lần mỗi phase** không?
- Pool/executor có **đủ khả năng chạy các parties đồng thời** không?
- Barrier action có **ngắn, đúng dữ liệu và không blocking vô thời hạn** không?
- Shared data trước `await` có ownership rõ và được aggregate **trước release** không?
- Đã hiểu `arrivalIndex==0` là **last arrival**, không phải worker cố định?
- `InterruptedException`/`TimeoutException`/`BrokenBarrierException` có được xử lý riêng và đúng policy không?
- Có thể một task ném exception **trước await** khiến các parties khác mắc kẹt không? Có timeout/cancellation?
- Nếu action thất bại, coordinator có bắt được **exception gốc** không?
- Có **một owner duy nhất cho reset/recovery** không, hay mọi worker tự reset?
- Recovery có chờ old tasks/cleanup xong trước khi dùng generation mới không?
- `reset` có được dùng chỉ khi thực sự cần? Có tránh reset ở healthy phase?
- Có phân biệt `isBroken=false` với **business phase completed** không?
- Có xác định side effects của action có **idempotent** khi retry không?
- Có cần dynamic participants? Nếu có, Phaser có thích hợp hơn không?
- Có load/stress tests với timeout, interrupt, broken action, reset và nhiều pha không?
- Có đo worker starvation, queue, waiting time và callback duration không?

**Kết luận:** `CyclicBarrier` tạo một điểm hẹn lặp lại cho nhóm cố định. **Barrier action** chạy một lần trên thread đến cuối và tạo ranh giới visibility trước khi parties được mở. **Broken** biểu thị rằng cả generation đồng bộ không còn hợp lệ do timeout, interrupt hoặc action failure; waiters còn lại phải nhận lỗi thay vì tiếp tục với dữ liệu thiếu. **Reset** loại bỏ generation cũ, tạo generation mới và làm các waiters hiện có ném `BrokenBarrierException`; nó không tự khôi phục business state, worker hay callback. Thiết kế đúng phải quản lý đầy đủ participants, action, timeouts, cancellation, recovery owner và dữ liệu theo phase.

### Tài liệu tham khảo

- Oracle Java SE 17 API: [CyclicBarrier](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CyclicBarrier.html)
- Oracle Java SE 17 API: [CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html)
- Oracle Java SE 17 API: [Phaser](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Phaser.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronizers-008">Đếm permit và phòng tránh permit leak</a>

<details>
<summary>Xem chi tiết</summary>

# Đếm permit và phòng tránh permit leak trong Java Synchronizers

## 1. Bài toán: giới hạn số công việc đồng thời phải luôn đúng

Một ứng dụng có thể chỉ cho phép tối đa ba yêu cầu cùng gọi dịch vụ ngoài, năm tác vụ cùng dùng connection đắt tiền, hoặc hai phép chuyển đổi dữ liệu nặng cùng chạy. Nếu không đặt giới hạn, hàng trăm thread có thể cùng tranh chấp tài nguyên, làm quá tải hệ thống.

`java.util.concurrent.Semaphore` là một synchronizer quản lý **số permit**. Một thread muốn đi vào đoạn code bị giới hạn phải **acquire** đúng số permit cần thiết; khi kết thúc quyền sử dụng tài nguyên, nó phải **release** đúng số permit đã lấy.

Trọng tâm của bài này là một bất biến rất đơn giản nhưng dễ làm sai:

> **Mỗi lần acquire thành công phải có đúng một lần release tương ứng, với đúng số permit; acquire không thành công thì không được release.**

Nếu lấy permit nhưng quên trả, hệ thống bị **permit leak**: khả năng phục vụ giảm dần, cuối cùng những công việc mới có thể chờ vô hạn. Nếu release quá số đã lấy hoặc release khi acquire thất bại, đó là **over-release**: hệ thống có thể cho phép nhiều công việc đi vào hơn giới hạn ban đầu, làm mất ý nghĩa của semaphore.

Bài thuộc phạm vi **Java concurrency nói chung**, tập trung vào API chuẩn của JDK, không dựa vào bất cứ framework riêng nào.

### Mục tiêu

Sau bài này, bạn cần:

1. Hiểu `Semaphore` là gì, `availablePermits()` phản ánh gì, và vì sao permit **không có ownership** như lock.
2. Viết đúng giao thức `acquire()`/`release()` và `tryAcquire()`/`release()`, kể cả timeout và interrupt.
3. Dùng `try/finally` đúng vị trí để không làm thất thoát permit hoặc trả permit chưa từng sở hữu theo logic nghiệp vụ.
4. Phân biệt **permit leak**, **over-release**, **double release**, **starvation** và **deadlock**.
5. Làm việc với `acquire(n)`, `release(n)`, fairness, shutdown và retry mà không phá bộ đếm.
6. Thiết kế phép đo, test và review để phát hiện lỗi trả permit trong workload đồng thời.
7. Biết khi nào nên dùng `Semaphore`, `BlockingQueue`, `ThreadPoolExecutor` hoặc tài nguyên tự quản lý khác.

## 2. Semaphore đếm permit như thế nào?

Một semaphore có một trạng thái permit count. Với `new Semaphore(3)`:

~~~text
Ban đầu:                    available = 3
Task A acquire():           available = 2
Task B acquire():           available = 1
Task C acquire():           available = 0
Task D acquire():           phải chờ
Task A release():           available = 1
Task D có thể acquire():    available = 0
~~~

Các thao tác acquire/release thay đổi trạng thái theo contract concurrency. Nhiều thread có thể gọi chúng cùng lúc mà không phải tự bảo vệ bộ đếm nội bộ bằng `synchronized`.

### 2.1. Ý nghĩa API chính

| API | Ý nghĩa và điều cần nhớ |
| --- | --- |
| `new Semaphore(permits)` | Tạo synchronizer với số permit ban đầu |
| `new Semaphore(permits, true)` | Chọn chính sách fair khi có thread cạnh tranh |
| `acquire()` | Chờ lấy một permit; có thể ném `InterruptedException` |
| `acquire(int n)` | Chờ lấy **n permit** theo thao tác đồng bộ của semaphore |
| `acquireUninterruptibly()` | Chờ mà không ném `InterruptedException` theo kiểu `acquire()`; cần cân nhắc cancellation |
| `tryAcquire()` | Thử lấy ngay một permit, trả boolean, không đợi |
| `tryAcquire(timeout, unit)` | Chờ có giới hạn nếu chưa lấy được permit, có thể bị interrupt |
| `tryAcquire(n, timeout, unit)` | Thử lấy n permit trong khoảng thời gian chỉ định |
| `release()` / `release(n)` | Trả đúng số permit theo protocol; không xác minh caller từng acquire |
| `availablePermits()` | Giá trị permit hiện có tại thời điểm đọc, **không phải reservation** |
| `drainPermits()` | Lấy hết các permit đang khả dụng và trả số đã lấy; API quản trị cấp thấp |
| `getQueueLength()` | Ước lượng số thread đang chờ acquire |
| `hasQueuedThreads()` | Gợi ý đang có waiter, không phải sự thật bất biến |
| `isFair()` | Cho biết semaphore được cấu hình fairness |

Các phương thức `acquire(n)` và `release(n)` yêu cầu n dương; n không hợp lệ có thể ném `IllegalArgumentException`. Dùng API nhiều permit thay vì lặp nhiều `acquire()` riêng khi một tác vụ cần **đồng thời** lấy đủ n permit trước khi bắt đầu. Điều đó tránh trạng thái “đã giữ một phần permit nhưng còn chờ nốt phần còn lại” của cách acquire lặp thủ công.

### 2.2. Semaphore không phải một lock có owner

`ReentrantLock` có thread sở hữu khóa; thread khác cố `unlock()` không đúng ownership sẽ bị từ chối. `Semaphore` **không buộc thread gọi release phải là thread đã acquire**: một thread có thể release permit mà thread khác lấy trước đó.

Đó là khả năng hữu ích cho một số giao thức handoff, nhưng cũng là nguồn lỗi:

~~~java
// SAI: không có chuyện Semaphore tự kiểm tra acquire trước đó.
// Một thread bất kỳ có thể tăng số permit bằng dòng này.
semaphore.release();
~~~

Chỉ vì `release()` không ném exception không có nghĩa bạn đã trả đúng permit. **Ownership của permit là một quy tắc do chương trình phải duy trì**, không được semaphore ép buộc.

### 2.3. Permit count không luôn là “capacity cố định”

Semaphore có thể được tạo với số permit bằng 0 để đóng vai trò **tín hiệu/permit producer-consumer**, hoặc với giá trị khác tùy giao thức. Java `Semaphore` còn cho phép các thao tác điều chỉnh thấp cấp. Vì vậy `availablePermits()` không phải một số luôn bị chặn phía trên bởi constructor.

Với cách dùng giới hạn đồng thời thông thường, ta đặt một **capacity logic N** và tự duy trì:

~~~text
0 <= availablePermits <= N
inFlight + availablePermits = N
~~~

Bất biến này chỉ đúng nếu mọi acquire/release thực sự tuân thủ giao thức và không có thao tác `drainPermits`, điều chỉnh permit hoặc cấp quyền ngoài quy tắc. `inFlight` cần được định nghĩa chính xác (số permit đã acquire nhưng chưa release), không đơn giản là số task đã submit.

## 3. Permit leak và over-release khác nhau như thế nào?

### 3.1. Permit leak

Một task gọi `acquire()` thành công, sau đó gặp exception, return sớm hoặc bị hủy nhưng không gọi `release()`. Mỗi permit bị mất khiến semaphore còn ít quyền vào hơn dự kiến.

~~~text
Capacity ban đầu: 3
Task A lấy 1 permit rồi thoát mà không release
Available tối đa về sau: 2
Task B lại leak 1 permit
Available tối đa về sau: 1
Task C tiếp tục leak permit
Available = 0, không còn tiến triển nếu không ai release
~~~

Đây không phải “rò rỉ object” hay “GC không thu hồi bộ nhớ”. Permit là **trạng thái đồng bộ**. Garbage collector không tự biết một tác vụ đã quên trả permit và không tự gọi `release()`.

### 3.2. Over-release

Task chưa acquire được permit nhưng gọi `release()`, hoặc một lần acquire lại có hai đường cleanup cùng release. Permit count có thể **vượt capacity logic**.

~~~text
Capacity logic N = 2
Available ban đầu = 2
Một release() nhầm -> available = 3
Ba task acquire() có thể cùng đi vào
=> giới hạn tối đa 2 đã bị phá.
~~~

Đối với semaphore giới hạn kết nối, over-release nguy hiểm không kém leak: thay vì throughput giảm, bạn có thể gây **quá tải hạ tầng ngoài**, lỗi tài nguyên và timeout dây chuyền.

### 3.3. Double release

Double release là một trường hợp over-release:

- `release()` trong `catch` rồi `release()` lại trong `finally`.
- Method bên trong tự release, method gọi bên ngoài cũng release.
- Một task timeout và cleanup release, sau đó callback hoàn tất cũng release.
- Retry logic giữ biến `acquired` sai, trả permit lần nữa sau attempt khác.

Cách giải quyết không phải chỉ “nhớ đừng release hai lần”, mà là xác định **một nơi có trách nhiệm release**, theo một quyền sở hữu logic rõ ràng.

## 4. Mẫu acquire/release an toàn nhất

### 4.1. Acquire bên ngoài, finally bên trong

Mẫu chuẩn với `acquire()`:

~~~java
// Mẫu Java, không phải chương trình độc lập.
semaphore.acquire(); // Có thể chờ và ném InterruptedException.
try {
    performProtectedWork();
} finally {
    semaphore.release();
}
~~~

**Vì sao acquire nằm trước try?** Nếu `acquire()` ném `InterruptedException` do chưa lấy được permit, không được chạy `release()`. Đặt `acquire()` phía ngoài bảo đảm chỉ đi vào khối `try/finally` khi acquire đã trở về thành công.

**Vì sao release nằm trong finally?** Nếu `performProtectedWork()` ném exception hoặc return, `finally` vẫn được gọi trong luồng điều khiển Java bình thường. Điều đó ngăn leak ở những đường thoát thông thường, không phải lời hứa trước crash JVM hoặc `Runtime.halt()`.

### 4.2. Mẫu nguy hiểm: finally ôm luôn acquire

~~~java
// SAI: nếu acquire bị interrupt trước khi thành công, vẫn release.
try {
    semaphore.acquire();
    performProtectedWork();
} finally {
    semaphore.release();
}
~~~

Nếu `acquire()` thất bại, đoạn này over-release. Đây là lỗi đặc biệt dễ mắc khi lập trình viên có thói quen “cứ thấy resource là đặt finally”.

Một mẫu thay thế khi cần nhiều nhánh phức tạp là giữ boolean `acquired`, đặt true **chỉ sau khi acquire thành công** và chỉ release khi boolean true. Nhưng nếu không thật sự cần, mẫu acquire trước try thường ngắn và khó sai hơn.

### 4.3. Kiểm soát vòng đời của tài nguyên ngoài

Gọi `release()` đúng lúc **quyền sử dụng tài nguyên hạn chế thực sự kết thúc**. Nếu bạn có một kết nối đang phục vụ I/O bất đồng bộ, release ngay sau khi **submit I/O** có thể quá sớm: request thật vẫn còn dùng connection nhưng permit đã trả cho request khác.

Ngược lại, giữ permit xuyên một thao tác callback không liên quan sau khi công việc giới hạn đã hoàn tất có thể làm giảm throughput. Xác định ranh giới tài nguyên trước khi chọn vị trí `try/finally`.

## 5. Ví dụ 1 — kế toán permit cơ bản (Java 17+)

Tạo tệp `PermitAccountingDemo.java`:

~~~java
import java.util.concurrent.Semaphore;

public class PermitAccountingDemo {
    public static void main(String[] args)
            throws InterruptedException {
        Semaphore semaphore = new Semaphore(3);

        System.out.println("initial = "
            + semaphore.availablePermits());

        semaphore.acquire(2);
        System.out.println("after acquire(2) = "
            + semaphore.availablePermits());

        try {
            System.out.println("inside = true");
        } finally {
            semaphore.release(2);
        }

        System.out.println("after release(2) = "
            + semaphore.availablePermits());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac PermitAccountingDemo.java
java PermitAccountingDemo
~~~

Stdout:

~~~text
initial = 3
after acquire(2) = 1
inside = true
after release(2) = 3
~~~

Hai permit được lấy trong một lời gọi `acquire(2)`; finally trả lại **đúng hai** bằng `release(2)`. Nếu chỉ trả một permit, available cuối là 2, thể hiện leak một permit. Nếu trả ba, available cuối là 4, thể hiện over-release một permit.

Không có thread khác trong ví dụ này, nên `availablePermits()` phản ánh bộ đếm tại các điểm xác định. Trong hệ thống nhiều thread, giá trị có thể thay đổi ngay sau khi quan sát.

### 5.1. Tại sao không lặp acquire từng permit?

Giả sử task cần hai permit nhưng viết:

~~~java
// Chỉ minh họa rủi ro với hai lời acquire độc lập.
semaphore.acquire();
semaphore.acquire();
try {
    doWork();
} finally {
    semaphore.release(2);
}
~~~

Nếu lần acquire thứ hai bị interrupt, lần đầu đã thành công nhưng khối finally chưa được thiết lập: **leak một permit**. Bài toán còn có thể gặp chờ kiểu hold-and-wait khi nhiều task cùng giữ permit một phần.

Ưu tiên `acquire(2)` khi cần hai permit cùng lúc; nếu phải lấy nhiều nguồn lực khác nhau, thiết kế giao thức rollback/lock ordering chặt chẽ.

## 6. tryAcquire: chỉ release khi thực sự thành công

`tryAcquire()` hoặc `tryAcquire(timeout, unit)` trả boolean:

- `true`: lời gọi đã **acquire đủ permit yêu cầu**.
- `false`: không có permit được cấp từ lời gọi đó; **không được release**.
- Phương thức timeout có thể ném `InterruptedException` khi bị ngắt; không được release cho lần gọi không thành công.

Mẫu chuẩn:

~~~java
// Mẫu Java, không phải chương trình độc lập.
boolean acquired =
    semaphore.tryAcquire(200, TimeUnit.MILLISECONDS);

if (!acquired) {
    return false;
}

try {
    performProtectedWork();
    return true;
} finally {
    semaphore.release();
}
~~~

Mẫu lỗi thường gặp:

~~~java
// SAI: over-release khi tryAcquire trả false.
try {
    if (!semaphore.tryAcquire()) {
        return;
    }
    doWork();
} finally {
    semaphore.release();
}
~~~

Vì `return` trong try **vẫn chạy finally**, nhánh tryAcquire false trên cũng `release()`. Một sai lầm nhỏ có thể làm số permit vượt capacity mà không có exception cảnh báo.

### 6.1. Ví dụ 2 — timeout không tạo permit giả (Java 17+)

Tạo tệp `TimedAcquireDemo.java`:

~~~java
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class TimedAcquireDemo {
    public static void main(String[] args)
            throws InterruptedException {
        Semaphore permits = new Semaphore(1);
        permits.acquire();

        try {
            boolean gotAnother =
                permits.tryAcquire(0, TimeUnit.MILLISECONDS);

            System.out.println("second acquired = " + gotAnother);
            System.out.println("while held = "
                + permits.availablePermits());

            if (gotAnother) {
                permits.release();
            }
        } finally {
            permits.release(); // Trả permit ban đầu đúng một lần.
        }

        System.out.println("after = "
            + permits.availablePermits());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac TimedAcquireDemo.java
java TimedAcquireDemo
~~~

Stdout:

~~~text
second acquired = false
while held = 0
after = 1
~~~

Cùng một thread vẫn có thể gọi `tryAcquire` lần nữa vì semaphore **không có reentrancy/ownership** như một mutex; nhưng khi available bằng 0, lời thử với timeout 0 trả false. Vì thất bại không cấp permit nên không có release tương ứng.

Nhánh finally chỉ trả permit đã được `acquire()` thành công trước đó, do vậy kết quả cuối là 1 thay vì 2. Đây là một kiểm tra trực tiếp để phát hiện lỗi over-release.

## 7. InterruptedException: tại sao phải đặt finally đúng ranh giới?

`Semaphore.acquire()` là một phép chờ interruptible. Nếu thread bị interrupt trong lúc chờ, nó có thể ném `InterruptedException` mà **không có permit nào được cấp bởi lần acquire đó**.

Mẫu đúng:

~~~java
// Mẫu method trong code production; không độc lập.
void guardedJob() throws InterruptedException {
    semaphore.acquire();
    try {
        work();
    } finally {
        semaphore.release();
    }
}
~~~

Nếu `work()` cần phản hồi với interrupt ở giữa critical section, finally vẫn release khi method thoát do exception hoặc return. Nếu `acquire()` bị interrupt và không thành công, control không vào khối try nên không release.

### 7.1. Ví dụ 3 — worker bị interrupt khi chưa có permit (Java 17+)

Tạo tệp `InterruptedAcquireDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterruptedAcquireDemo {
    public static void main(String[] args)
            throws InterruptedException {
        Semaphore semaphore = new Semaphore(0);
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean noticed = new AtomicBoolean();

        Thread worker = new Thread(() -> {
            entered.countDown();
            try {
                semaphore.acquire();
                try {
                    throw new AssertionError(
                        "This acquisition should not succeed"
                    );
                } finally {
                    semaphore.release();
                }
            } catch (InterruptedException ex) {
                noticed.set(true);
                Thread.currentThread().interrupt();
            }
        }, "waiting-worker");

        worker.start();
        entered.await();
        worker.interrupt();
        worker.join();

        System.out.println("interrupted = " + noticed.get());
        System.out.println("available = "
            + semaphore.availablePermits());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac InterruptedAcquireDemo.java
java InterruptedAcquireDemo
~~~

Stdout khi không có tác động ngoài ví dụ:

~~~text
interrupted = true
available = 0
~~~

`entered` xác nhận worker đã tới vùng code trước khi main gửi interrupt, nhưng không cần bảo đảm nó đã chặn trong `acquire()`: nếu interrupt tới **trước** khi acquire, `acquire()` vẫn phản hồi bằng `InterruptedException`; nếu tới **trong lúc chờ**, kết quả cũng vậy. Không ai release một permit vào semaphore ban đầu bằng 0.

Dòng `semaphore.release()` chỉ nằm trong finally **sau** khi acquire đã trở về bình thường. Vì điều đó không thể xảy ra trong ví dụ có interrupt và không có producer permit, available cuối vẫn là 0. Việc khôi phục interrupt flag trong catch giúp tầng khác của worker biết tín hiệu đã tới, thay vì vô tình nuốt nó.

### 7.2. acquireUninterruptibly không có nghĩa “an toàn hủy hơn”

`acquireUninterruptibly()` đợi mà không truyền `InterruptedException` giống `acquire()`. Nó có thể tiếp tục chờ permit dù caller đã yêu cầu hủy. Trong hệ thống shutdown cần phản hồi nhanh, sử dụng nó bừa bãi có thể làm task mắc kẹt.

Khi không có lý do rõ ràng phải đợi không thể ngắt, ưu tiên `acquire()` hoặc `tryAcquire(timeout, unit)`, kết hợp deadline và chiến lược cancellation.

## 8. Giới hạn đồng thời thực tế với nhiều worker

Một semaphore giới hạn **số quyền vào đoạn code**, không giới hạn số task đã được gửi vào executor. Nếu 1.000 task được submit vào một pool và chỉ có hai permit, nhiều task có thể vẫn xếp hàng hoặc block trong acquire; việc đó có thể làm pool đầy, tăng latency và gây **thread pool starvation** khi các task trong pool lại phụ thuộc nhau.

### 8.1. Ví dụ 4 — ba task, tối đa hai permit (Java 17+)

Tạo tệp `BoundedWorkerDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BoundedWorkerDemo {
    public static void main(String[] args) throws Exception {
        Semaphore permits = new Semaphore(2);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch twoEntered = new CountDownLatch(2);
        CountDownLatch releaseWork = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();

        try {
            Future<?>[] tasks = new Future<?>[3];

            for (int i = 0; i < tasks.length; i++) {
                tasks[i] = pool.submit(() -> {
                    permits.acquire();
                    try {
                        twoEntered.countDown();
                        releaseWork.await();
                        completed.incrementAndGet();
                    } finally {
                        permits.release();
                    }
                    return null;
                });
            }

            twoEntered.await();
            System.out.println("available while held = "
                + permits.availablePermits());

            releaseWork.countDown();
            for (Future<?> task : tasks) {
                task.get();
            }

            System.out.println("completed = " + completed.get());
            System.out.println("available after = "
                + permits.availablePermits());
        } finally {
            releaseWork.countDown();
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac BoundedWorkerDemo.java
java BoundedWorkerDemo
~~~

Stdout khi các task hoàn tất bình thường:

~~~text
available while held = 0
completed = 3
available after = 2
~~~

**Vì sao có thể kết luận?**

- Hai task đầu tiên lấy được permit sẽ `twoEntered.countDown()` rồi chờ `releaseWork`; lúc latch về 0, **chắc chắn có hai permit đang được giữ**.
- Task thứ ba chưa thể vượt qua acquire khi cả hai permit vẫn bị giữ, bất kể worker nào được scheduler chọn trước.
- Main in available = 0 rồi mở `releaseWork`. Tất cả ba task sẽ hoàn thành và mỗi task trả đúng một permit qua finally.
- `Future.get()` đợi từng task và chuyển lỗi của task thành `ExecutionException` nếu có; vì vậy dòng kết quả cuối không được in sớm.
- Không có worker nào in trực tiếp, nên thứ tự scheduler không làm thứ tự stdout thay đổi.

**Điểm cần nhìn xa hơn:** Một semaphore không tự đảm bảo fairness. Chương trình đúng về số permit nhưng không yêu cầu task được cấp permit theo thứ tự `submit`. Nếu SLA cần thứ tự chờ, xem xét fairness, loại queue hoặc mô hình worker riêng.

### 8.2. Giữ permit trong lúc block quá lâu

Nếu mỗi task giữ permit rồi đợi I/O không có timeout, hai permit có thể bị chiếm rất lâu. Đây không phải nhất thiết là **permit leak**: permit vẫn sẽ được trả nếu I/O kết thúc. Nhưng biểu hiện vận hành “available=0 kéo dài, queue tăng” có thể tương tự.

Để chẩn đoán, cần đo thời gian **giữ permit**, phân biệt task còn sống với task đã thoát, và biết downstream đang chặn ở đâu.

### 8.3. Không dùng semaphore để thay mọi loại queue

Nếu công việc đã được nhận nhưng cần **phân phối đúng một lần** cho các worker, `BlockingQueue.take()`/`poll()` hoặc `ThreadPoolExecutor` có queue bounded có thể biểu diễn ownership rõ hơn. Semaphore không lưu chính task và không tự xác nhận đã xử lý xong một task.

## 9. Over-release có thể xảy ra mà không có exception

Khác `ReentrantLock.unlock()`, `Semaphore.release()` không tra cứu owner. Do đó một lời gọi release sai có thể **làm tăng permit count** mà không ném `IllegalMonitorStateException`.

### 9.1. Ví dụ 5 — cố ý tạo lỗi over-release (Java 17+)

Tạo tệp `OverReleaseDemo.java`:

~~~java
import java.util.concurrent.Semaphore;

public class OverReleaseDemo {
    public static void main(String[] args) {
        Semaphore semaphore = new Semaphore(1);

        // SAI CÓ CHỦ Ý: chưa ai acquire nhưng lại release.
        semaphore.release();

        System.out.println("available = "
            + semaphore.availablePermits());
        System.out.println("limit violated = "
            + (semaphore.availablePermits() > 1));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac OverReleaseDemo.java
java OverReleaseDemo
~~~

Stdout:

~~~text
available = 2
limit violated = true
~~~

Đây là **demo hành vi sai, không phải mẫu áp dụng**. Java không tự buộc available ≤ giá trị truyền vào constructor. Nếu coi constructor `Semaphore(1)` là mutex limit một tác vụ, việc gọi `release()` nhầm làm hai tác vụ khác có thể cùng acquire — phá ý nghĩa loại trừ.

### 9.2. Double release trong catch và finally

~~~java
// SAI: release hai lần khi doWork ném exception.
semaphore.acquire();
try {
    doWork();
} catch (RuntimeException ex) {
    semaphore.release();
    throw ex;
} finally {
    semaphore.release();
}
~~~

Cách sửa: **một finally duy nhất** chịu trách nhiệm release; catch chỉ ghi nhận/rethrow lỗi, không tự release nếu finally đã làm. Nếu một callback ngoài cũng release, phải xác định rõ việc bàn giao ownership; đừng để hai lớp cùng nghĩ mình có trách nhiệm cuối.

## 10. Dùng try-with-resources để giảm nguy cơ quên release

Khi một ứng dụng có nhiều method có thể return sớm, throw, hoặc gọi helper, một wrapper có ownership rõ ràng giúp review dễ hơn. Java cho phép dùng try-with-resources với một object `AutoCloseable` đại diện cho **permit lease**.

### 10.1. Thiết kế lease: acquire trước khi tạo token

Một lease nên:

- Được tạo **chỉ sau khi acquire thành công**.
- Biết số permit đã lấy và semaphore sẽ nhận release.
- Chỉ được close **một lần có tác dụng thực tế**, dù caller vô tình close lặp.
- Không chuyển quyền sở hữu sang nơi khác mà thiếu giao thức rõ.
- Không thực hiện I/O, logging hay logic nghiệp vụ bất định trong `close()`.

### 10.2. Ví dụ 6 — PermitLease AutoCloseable (Java 17+)

Tạo tệp `PermitLeaseDemo.java`:

~~~java
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class PermitLeaseDemo {
    static final class PermitLease implements AutoCloseable {
        private final Semaphore semaphore;
        private final int permits;
        private final AtomicBoolean closed = new AtomicBoolean();

        private PermitLease(Semaphore semaphore, int permits) {
            this.semaphore = semaphore;
            this.permits = permits;
        }

        static PermitLease acquire(
                Semaphore semaphore, int permits)
                throws InterruptedException {
            if (permits <= 0) {
                throw new IllegalArgumentException(
                    "permits must be positive"
                );
            }

            semaphore.acquire(permits);
            return new PermitLease(semaphore, permits);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                semaphore.release(permits);
            }
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Semaphore semaphore = new Semaphore(2);

        try (PermitLease lease =
                 PermitLease.acquire(semaphore, 2)) {
            System.out.println("inside = "
                + semaphore.availablePermits());
        }

        System.out.println("after close = "
            + semaphore.availablePermits());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac PermitLeaseDemo.java
java PermitLeaseDemo
~~~

Stdout:

~~~text
inside = 0
after close = 2
~~~

`PermitLease.acquire` không tạo object lease trước khi semaphore `acquire(permits)` thành công. Nếu bị interrupt khi chờ, method ném exception và không có lease nào để close; không over-release. Sau khi acquire thành công, try-with-resources gọi `close()` ở cuối scope kể cả khi code bên trong ném exception hoặc return.

`AtomicBoolean` làm `close()` có hiệu lực đúng một lần nếu bị gọi lại. Đây là **bảo vệ ở cấp object lease**, không thay thế việc thiết kế ownership toàn hệ thống: hai lease khác nhau không được đại diện cho cùng một quyền permit nếu chưa có protocol chuyển giao.

### 10.3. Những giới hạn của wrapper

- `AutoCloseable` không làm resource ngoài tự được trả khi process chết. Nó chỉ tự gọi `close()` khi rời scope try-with-resources theo control flow Java.
- Không để lease bị giữ qua một tác vụ bất đồng bộ **ngoài lifetime của try** nếu tác vụ đó vẫn cần permit.
- Nếu một API thực sự cần bàn giao quyền release cho thread khác, phải dùng transfer-of-ownership rõ ràng; wrapper trong scope ban đầu có thể release quá sớm.
- Khi gói nhiều permit, nên tạo lease duy nhất có số permit chính xác, thay vì tạo nhiều lease cho cùng những permit đã acquire.
- Việc dùng `AtomicBoolean` tạo một allocation phụ, nên không mặc định lease wrapper là tối ưu throughput cho mọi hot path. Với code ngắn, một `try/finally` đúng có thể đơn giản và nhanh hơn.

## 11. Bulk permits: acquire(n) và release(n) phải cân bằng

Nếu một tác vụ tiêu thụ hai đơn vị capacity, ví dụ hai slot GPU logic, dùng `acquire(2)` và `release(2)`.

~~~java
// Mẫu Java, không phải chương trình độc lập.
semaphore.acquire(2);
try {
    processUsingTwoSlots();
} finally {
    semaphore.release(2);
}
~~~

Cần chú ý:

- Kiểm tra n > 0 và đúng với số permit cần theo nghiệp vụ.
- Không bao giờ acquire 2 nhưng release 1 hoặc 3.
- Nếu dùng `tryAcquire(2, timeout, unit)`, chỉ release(2) khi lời thử trả true.
- Nếu một task cần tất cả N permit để chạy thao tác độc quyền, nó có thể phải chờ lâu khi các task nhỏ liên tục lấy permit; cân nhắc fairness và starvation.
- Không trộn giữa giữ một permit rồi lại chờ thêm nhiều permit theo các đường code khác nhau nếu không có kế hoạch tránh deadlock và rollback.

### 11.1. Ví dụ 7 — thành công và thất bại khi cần hai permit (Java 17+)

Tạo tệp `BulkPermitDemo.java`:

~~~java
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class BulkPermitDemo {
    public static void main(String[] args)
            throws InterruptedException {
        Semaphore semaphore = new Semaphore(3);
        semaphore.acquire(2);

        try {
            boolean anotherTwo =
                semaphore.tryAcquire(
                    2, 0, TimeUnit.MILLISECONDS
                );

            System.out.println("second two = " + anotherTwo);
            System.out.println("available inside = "
                + semaphore.availablePermits());

            if (anotherTwo) {
                semaphore.release(2);
            }
        } finally {
            semaphore.release(2);
        }

        System.out.println("available after = "
            + semaphore.availablePermits());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac BulkPermitDemo.java
java BulkPermitDemo
~~~

Stdout:

~~~text
second two = false
available inside = 1
available after = 3
~~~

Sau lần acquire(2) đầu, chỉ còn một permit. Lần tryAcquire(2, 0, ...) không lấy được đủ hai, trả false và không cấp permit nào. Chỉ có finally của lần acquire thành công release(2), nên tổng quay về 3.

**Không dùng giá trị `availablePermits()` để tự chia lấy permit:** giữa việc bạn đọc count và lời acquire, thread khác có thể thay đổi count. Nếu cần một tập n permit, hãy yêu cầu bằng `acquire(n)`/`tryAcquire(n, ...)` với atomic acquisition theo contract.

## 12. Fairness: ngăn barging không đồng nghĩa không leak

`new Semaphore(permits, true)` cho phép áp dụng chính sách **fair** tại các điểm acquisition theo API. Khi tranh chấp, nó ưu tiên thứ tự chờ theo các điểm xếp hàng nội bộ thay vì cho phép thread mới vượt lên liên tục như nonfair.

Nhưng fairness không giải quyết mọi vấn đề:

- **Permit leak**: một permit đã mất vẫn mất, bất kể fair.
- **Over-release**: `release()` sai vẫn tăng count; fair không kiểm tra ownership.
- **Owner giữ permit quá lâu**: những waiter vẫn phải chờ.
- **Deadlock đa tài nguyên**: hai task có thể giữ các permit/lock khác nhau rồi chờ nhau; fair không loại circular wait.
- **Scheduler fairness**: JVM/OS không đảm bảo mỗi task có CPU trong khoảng thời gian cố định.
- **Throughput**: fair có thể phải trả chi phí cấp quyền theo waiter cũ, nên có thể chậm hơn nonfair dưới contention.

### 12.1. Ngoại lệ của tryAcquire không timeout

`Semaphore.tryAcquire()` không tham số có thể lấy một permit **ngay lập tức nếu còn**, ngay cả khi semaphore fair và đã có waiter đang chờ. Tức phương thức này **cho phép barging**.

`tryAcquire(0, TimeUnit.SECONDS)` trên semaphore fair có thể dùng khi muốn thử không đợi mà vẫn tôn trọng fairness; dạng này có thể ném `InterruptedException`. Tương tự, `tryAcquire(n)` không timeout có chính sách barging của overload tương ứng.

Nếu yêu cầu ứng dụng là không cho task mới vượt hàng, phải xem **tất cả code path acquire**, không chỉ constructor `Semaphore(..., true)`.

### 12.2. Starvation không phải permit leak

Nếu một thread đợi rất lâu, chưa đủ chứng minh permit bị leak:

- Một thread khác đang giữ permit và thực sự làm việc quá lâu.
- Task chờ bị xếp lịch ít CPU.
- Nonfair barging làm waiter cũ chờ kéo dài.
- Task đang giữ một số permit trong lúc chờ tài nguyên khác.
- Một lỗi giao thức thật sự đã làm mất permit.

Cần đo owner/lifetime của permit và các trường hợp timeout thay vì chỉ nhìn queue length.

## 13. getQueueLength(), availablePermits() và quan sát

`availablePermits()` trả số permit khả dụng tại thời điểm đọc. `getQueueLength()` là **ước lượng** số thread chờ; `hasQueuedThreads()` chỉ là một quan sát tức thời. Ngay sau khi đọc, thread khác có thể acquire/release.

**Không dùng các giá trị này để tạo logic check-then-act như:**

~~~java
// SAI: có race giữa kiểm tra và acquire.
if (semaphore.availablePermits() > 0) {
    // Giả định chắc chắn acquire sẽ không chờ là sai.
    semaphore.acquire();
}
~~~

Thread khác có thể lấy permit ngay sau phép kiểm tra. Nếu muốn **không chặn**, dùng `tryAcquire()` và kiểm tra boolean; nếu có deadline, dùng overload timeout.

### 13.1. Số liệu hữu ích

| Metric | Ý nghĩa và giới hạn |
| --- | --- |
| Capacity logic | Số quyền vào tối đa được thiết kế |
| Available permits | Permit đang khả dụng; snapshot tức thời |
| In-flight permits | Số permit đã acquire chưa release theo protocol ứng dụng |
| Acquire latency p50/p95/p99 | Thời gian chờ để được cấp quyền |
| Hold duration p95/p99 | Thời gian permit được giữ |
| Timeout rate | Tỉ lệ task không lấy được permit trong deadline |
| Interrupt/cancellation count | Có bao nhiêu lần chờ bị ngắt |
| Estimated queue length | Gợi ý áp lực chờ, không phải số người đợi chính xác |
| Acquire/release event balance | Có event nào thiếu trả permit hoặc trả thừa không? |
| Work outcome | Task thành công/thất bại/hủy; không đồng nhất permit accounting |

Nếu ứng dụng dùng n-permit acquire, **đếm số lần acquire/release** không đủ; cần đếm **tổng số permit**. Một event acquire(3) phải được cân bằng với release(3), không phải với release() một lần.

### 13.2. Metric không làm thành một synchronization protocol

Nếu có nhiều thread cùng tăng metric “in-flight”, bản thân metric cũng phải thread-safe. Nhưng đừng đặt metric update ở vị trí khiến exception trong logger làm leak permit. Với code quan trọng, đơn giản hóa critical path, rồi quan sát bằng công cụ ít có thể thất bại.

Nếu muốn kiểm toán lịch sử, ghi nhận permit acquisition và release theo **một ID lease**, số permit và thời gian giữ. Không suy ra owner chỉ từ `availablePermits()` vì semaphore không biết owner của từng permit.

## 14. Các đường code dễ leak permit trong thực tế

### 14.1. Return sớm

~~~java
// SAI: return sớm khiến release bị bỏ qua.
semaphore.acquire();
if (!isValidRequest()) {
    return;
}
doWork();
semaphore.release();
~~~

Dùng finally:

~~~java
semaphore.acquire();
try {
    if (!isValidRequest()) {
        return;
    }
    doWork();
} finally {
    semaphore.release();
}
~~~

### 14.2. Exception ở giữa đường

~~~java
// SAI nếu process có thể ném exception.
semaphore.acquire();
process();
semaphore.release();
~~~

Ngay cả unchecked exception cũng làm bỏ qua dòng release nếu không có finally. Đừng chỉ catch một loại lỗi rồi release; hãy đặt release trong finally để bao phủ các đường thoát thông thường.

### 14.3. Timeout hoặc interrupt trước acquire

Nếu `tryAcquire(...)` trả false hoặc `acquire()` ném `InterruptedException`, không có permit được cấp bởi lời gọi đó. Một unconditional finally bên ngoài acquire khiến **over-release**, không phải leak. Cần xem mã trả permit và mã cấp permit là hai nửa của cùng giao thức.

### 14.4. Async callback

~~~java
// Sai thiết kế nếu externalCallAsync còn dùng tài nguyên
// sau khi hàm đã return.
semaphore.acquire();
try {
    externalCallAsync(); // Chỉ khởi động công việc.
} finally {
    semaphore.release(); // Quá sớm!
}
~~~

Cần xác định ai giữ lease cho tới khi callback/`CompletionStage` thực sự hoàn tất, và release đúng **một lần** trong đường success/failure/cancel. Nếu callback có thể được kích hoạt nhiều lần do bug hoặc API hứa at-most-once nhưng code có thêm fallback cleanup, phải có guard idempotent và ownership rõ ràng.

Cảnh giác với mẫu `whenComplete(... release)`: nếu bạn acquire trước khi tạo stage, nhưng bước tạo stage **ném exception đồng bộ trước khi đăng ký callback**, release sẽ bị bỏ qua. Hãy thiết kế việc chuyển ownership từ scope đồng bộ sang callback với trạng thái cụ thể, hoặc dùng wrapper lease được đóng đúng một lần.

### 14.5. Retry

Một task retry có thể acquire mới cho mỗi attempt. Mỗi attempt phải cân bằng permit của chính nó. Nếu muốn giữ permit xuyên mọi attempt, phải bảo đảm tổng thời gian giữ và deadline phù hợp; giữ permit khi ngủ backoff dài có thể làm nghẽn hệ thống dù không leak.

### 14.6. Nested acquire và dependency stall

Task A giữ permit rồi gọi task B cần **cùng semaphore**, trong khi capacity đã hết; nếu A block chờ B hoàn tất, có thể thành bế tắc phụ thuộc.

~~~text
Semaphore(1)
A: acquire -> available = 0
A: submit B và chờ kết quả
B: acquire -> chờ vì available = 0
A: không release cho đến khi B xong
=> B không thể vào; A không thể xong
~~~

Không có leak theo nghĩa bị mất permit, nhưng có **deadlock**. Thiết kế lại ownership: tránh giữ permit trong lúc chờ công việc phụ cũng cần permit, hoặc điều chỉnh phạm vi semaphore/luồng phụ thuộc.

## 15. Drain và giảm permit: API nâng cao cần cảnh giác

`Semaphore.drainPermits()` trả số permit khả dụng và đặt trạng thái khả dụng về 0 theo thao tác phù hợp. Nó có thể hữu ích cho vài giao thức quản trị, nhưng nếu không có ownership rõ ràng thì rất dễ “giữ” các permit đã drain rồi quên trả.

~~~java
// Minh họa API nâng cao; không phải chương trình độc lập.
int drained = semaphore.drainPermits();
try {
    reconfigureCapacity();
} finally {
    semaphore.release(drained);
}
~~~

Mẫu này chỉ phù hợp khi protocol của ứng dụng thực sự cần giữ số permit đã drain, và khi việc trả chúng đúng vào thời điểm cuối không phá giới hạn logic khác. Nếu có task đang giữ permit từ trước, drain **không thu hồi** những permit đang được sử dụng.

`reducePermits(int reduction)` là phương thức **protected** dành cho subclass và các giao thức đặc biệt; không coi nó là API công khai để arbitrary caller giảm capacity. Nếu triển khai dynamic capacity, phải thiết kế tương quan giữa available/in-flight/target capacity và các permit đã cấp, không chỉ gọi một method rồi suy ra số task được phép vào đã đổi ngay.

Không dùng `drainPermits()` như nút “hủy toàn bộ task”: nó chỉ lấy các permit còn khả dụng, không interrupt waiter và không thu hồi permit đang được task giữ.

## 16. Chọn Semaphore hay synchronizer khác?

| Nhu cầu | Lựa chọn thường phù hợp | Vì sao |
| --- | --- | --- |
| Giới hạn tối đa N đoạn code dùng cùng tài nguyên | `Semaphore(N)` | Đơn giản, có acquire/release và timeout |
| Giới hạn số task trong queue và số worker | `ThreadPoolExecutor` với bounded queue và rejection policy | Quản lý capacity task/pool, không chỉ quyền vào |
| Nơi có phần tử để producer/consumer trao đổi | `BlockingQueue` | Lưu item và cung cấp ownership tiêu thụ |
| Chờ một số hoạt động hoàn tất | `CountDownLatch` | Count-down một chiều, không cần trả permit |
| Đồng bộ nhiều thread theo pha | `CyclicBarrier` hoặc `Phaser` | Giao thức pha, không phải số slot |
| Bảo vệ invariant đa trường với một owner | `ReentrantLock` / `synchronized` | Lock ownership và mutual exclusion |
| Đồng thời nhiều reader nhưng writer độc quyền | `ReadWriteLock` | Semantics khác counting semaphore |
| Giới hạn số request/giây | Rate limiter/bucket có semantics thời gian | Semaphore giới hạn **concurrency**, không tự giới hạn rate |

**Ví dụ:** Capacity 10 permit và mỗi request xử lý 1 ms có thể cho throughput lớn; capacity 10 nhưng mỗi request giữ 10 s có thể throughput rất thấp. Semaphore không trực tiếp bảo đảm **10 request/giây**. Nếu cần rate limiting, phải đưa thời gian vào giao thức.

### 16.1. Semaphore(1) có thay mutex không?

`Semaphore(1)` có thể giới hạn một task cùng vào, nhưng **không có owner** và không reentrant theo kiểu `ReentrantLock`. Nếu bài toán cần bảo vệ state bằng một khóa, ownership và unlock nghiêm ngặt thường làm `ReentrantLock` hoặc `synchronized` dễ chứng minh đúng hơn.

Không gọi `Semaphore(1)` là mutex hoàn toàn tương đương khi bạn dựa vào hành vi của monitor hoặc lock ownership.

## 17. Kiểm thử permit leak và over-release

### 17.1. Không phụ thuộc lịch scheduler

Một test chỉ `Thread.sleep(50)` rồi đoán worker đã acquire không đáng tin trên máy CI bận. Dùng `CountDownLatch`, `Future.get()`, `join()` hoặc barrier để quan sát các điểm xác định.

Với code có thể block vô hạn do leak, test cần timeout khi chờ hoàn tất; nếu timeout, thu thread dump và làm cleanup có giới hạn, tránh làm cả test suite treo.

### 17.2. Những tình huống quan trọng

1. **Success path:** acquire thành công, doWork bình thường, release đủ.
2. **Unchecked exception:** doWork ném exception; finally vẫn release.
3. **Early return:** logic từ chối request; release vẫn xảy ra sau acquire.
4. **Acquire bị interrupt:** không release permit chưa lấy; khôi phục/propagate interrupt đúng.
5. **Timeout:** `tryAcquire` false; không gọi release.
6. **TryAcquire true:** có đúng một release cho lần thành công đó.
7. **Bulk permits:** acquire(n) được cân bằng bằng release(n).
8. **Nested ownership:** không có hai lớp cùng release một permit.
9. **Async completion:** release đúng một lần trên success/failure/cancel, không trả quá sớm.
10. **Cancellation:** task đang giữ permit bị interrupt; finally vẫn release.
11. **Contention:** số tác vụ đồng thời nằm trong vùng được bảo vệ không vượt capacity logic.
12. **Shutdown:** người đang chờ được hủy/đánh thức theo protocol; không giữ permit mãi vì task không chịu dừng.
13. **Over-release detector:** thử cố tình release trước acquire trên semaphore test riêng, xác nhận hệ thống test/linter/lease tracking có thể phát hiện hoặc ngăn lỗi ở tầng ứng dụng.
14. **Retries:** mỗi attempt tự cân bằng, không release lặp khi callback đến muộn.

### 17.3. Tự kiểm tra bất biến trong test

Nếu code đã bảo đảm không ai còn đang giữ permit và mọi task trong test đều đã hoàn tất, kiểm tra:

~~~java
// Mẫu assertion khi test đã chờ tất cả task xong;
// không phải chương trình độc lập.
if (semaphore.availablePermits() != initialCapacity) {
    throw new AssertionError("Permit imbalance");
}
~~~

**Cảnh giác:** Available quay về initial capacity chỉ chứng minh **net balance** tại thời điểm đó. Một leak và một over-release có thể triệt tiêu số học, nhưng hệ thống vẫn từng cho phép vượt capacity hoặc làm nghẽn. Test mạnh hơn cần theo dõi số permit đang thực sự giữ, max concurrent entries, một mã lease ID cho acquire/release và thời gian release.

Nếu có writer/caller khác vẫn thao tác semaphore, việc kiểm tra available bằng một giá trị cố định không còn có ý nghĩa; hãy đóng cửa nguồn task và chờ worker kết thúc trước khi assertion.

## 18. Checklist code review: mỗi permit có một đường trả rõ ràng

- Có capacity logic và số permit cần cho mỗi loại công việc được định nghĩa rõ?
- Mọi `acquire()` thành công đều vào **một finally** bảo đảm release?
- `acquire()` được đặt **trước** khối try/finally nếu không có flag acquired?
- Với `tryAcquire`, chỉ release khi boolean true?
- Với timeout/interrupt, có tránh release khi acquire chưa thành công?
- Với `acquire(n)`, có release **chính xác n** permit?
- Có đường return sớm, checked/unchecked exception, cancel, timeout, callback nào bỏ qua release?
- Có đường catch + finally hoặc wrapper + outer caller cùng release?
- Task có giữ permit lâu hơn vòng đời thực tế của tài nguyên không?
- Async operation có đảm bảo callback hoàn tất mới release, đồng thời không bỏ sót khi setup ném exception?
- Có giới hạn retry và xử lý idempotency, tránh leak giữa các attempt?
- Semaphore(1) có đang bị dùng như lock với yêu cầu ownership/reentrancy không?
- Có tác vụ giữ permit và đồng thời chờ task khác phải acquire cùng semaphore không?
- Fairness có được chọn trên số liệu throughput/starvation, không phải tin rằng fair sửa mọi leak?
- Có timeout và cancellation path để worker không chờ vô hạn nếu downstream bị treo?
- Có dùng `availablePermits()` làm metric thay vì “check rồi chắc chắn acquire được”?
- Có quan sát tổng permit in-flight và max concurrent entries trong test?
- Khi shutdown, những task giữ permit có chạy cleanup và chờ hoàn tất theo contract?
- Có tránh `drainPermits`/capacity adjustment mà không có protocol chính xác?
- Có phân biệt “tác vụ xử lý thất bại” với “quyền sử dụng slot phải được trả” trong mọi trường hợp?

## 19. Câu hỏi tự kiểm tra

**1.** Vì sao `try { semaphore.acquire(); work(); } finally { semaphore.release(); }` là mẫu sai?

Nếu `acquire()` bị interrupt trước khi thành công, finally vẫn `release()`; semaphore tăng permit dù caller chưa được cấp. Đặt acquire ngoài try, hoặc dùng flag acquired chặt chẽ.

**2.** `tryAcquire()` trả false. Có cần `release()` để “dọn dẹp” không?

Không. False nghĩa lời gọi đó chưa cấp permit; release sẽ là over-release.

**3.** Vì sao semaphore không tự phát hiện thread B trả permit mà thread A đã lấy?

Vì semaphore không ràng buộc permit với thread sở hữu như `ReentrantLock`. Đây là khả năng thiết kế, không phải lỗi JVM. Chương trình phải quản lý ownership logic.

**4.** `availablePermits() == 0` kéo dài có chắc là leak không?

Không. Có thể mọi permit đang được giữ bởi task chạy chậm, bị kẹt I/O, hoặc deadlock. Cần thêm thông tin holder, hold duration và lifecycle của worker.

**5.** Một task lấy ba permit mà trả hai permit là lỗi gì?

Permit leak **một permit**. Phải release đúng số đã acquire.

**6.** `release()` hai lần sau một acquire thành công có làm semaphore ném exception như `ReentrantLock.unlock()` không?

Không có owner check tương tự; available có thể tăng vượt capacity logic. Đây là over-release/double release.

**7.** Fair semaphore có ngăn mọi starvation và permit leak không?

Không. Fairness điều phối thứ tự waiter tại các điểm acquisition; không thu hồi permit bị mất, không ép owner trả, không sửa deadlock hay hủy sai.

**8.** Có nên giữ permit khi khởi chạy một việc bất đồng bộ rồi release ngay sau khi submit không?

Chỉ nếu quyền sử dụng tài nguyên **thực sự kết thúc** ngay sau submit. Nếu công việc bên trong còn dùng tài nguyên được giới hạn, release như vậy là quá sớm. Cần chuyển ownership release sang completion path.

**9.** Vì sao một latch là lựa chọn tốt để test giới hạn 2 permit?

Lập trình viên có thể giữ hai task đã acquire ở cùng một điểm chờ và xác nhận available = 0 trước khi cho chúng release, không phải đoán lịch scheduler bằng sleep.

**10.** Lúc shutdown, dùng `acquireUninterruptibly()` có nguy cơ gì?

Task đang chờ có thể không phản hồi yêu cầu interrupt để hủy nhanh; cần protocol riêng để đảm bảo liveness hoặc ưu tiên API interruptible.

**11.** `Semaphore(1)` có hoàn toàn tương đương với mutex không?

Không. Semaphore không ràng buộc owner/reentrancy như `ReentrantLock`. Chọn theo giao thức bạn cần bảo vệ.

**12.** Vì sao kiểm tra available bằng capacity sau khi test xong chưa đủ chứng minh không có lỗi?

Một leak và một over-release có thể bù trừ, hoặc hệ thống từng cho quá số task vào dù cuối cùng count cân bằng. Cần cả kiểm tra event balance và max concurrent entries.

## 20. Tài liệu JDK và kết luận

Để đối chiếu hành vi theo JDK:

- [Semaphore — JDK 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html)
- [ThreadPoolExecutor — JDK 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [BlockingQueue — JDK 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html)

**Quy tắc thực hành:**

~~~text
acquire() thành công:
    try { công việc bảo vệ } finally { release() }

tryAcquire() == true:
    try { công việc bảo vệ } finally { release() }

tryAcquire() == false:
    không được release

acquire() ném InterruptedException:
    không được release cho lời gọi đó
    propagate hoặc restore tín hiệu hủy theo hợp đồng

acquire(n) thành công:
    release(n) đúng một lần khi quyền sử dụng kết thúc
~~~

**Kết luận:** Semaphore chỉ giữ **bộ đếm permit**, không biết owner hay invariant nghiệp vụ. Để phòng permit leak và over-release, một acquire thành công phải chuyển thành một quyền sở hữu rõ ràng, có đúng một đường release ở cuối lifetime tài nguyên. Kết hợp `try/finally` hoặc một lease wrapper, xử lý timeout/interrupt chính xác, đo hold time và thiết kế test theo interleaving xác định để bảo toàn giới hạn đồng thời của ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronizers-009">Latch dùng một lần so với barrier tái sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Latch dùng một lần so với barrier tái sử dụng trong Java

## 1. Hai bài toán trông giống nhau nhưng có hợp đồng khác nhau

Trong lập trình đa luồng, nhiều thread có thể cần đợi nhau hoặc đợi một mốc chung. Hai công cụ quan trọng của `java.util.concurrent` là **`CountDownLatch`** và **`CyclicBarrier`**. Cả hai có thể khiến thread chờ, nhưng chúng mô hình hóa hai loại phối hợp khác nhau:

- **CountDownLatch:** một hoặc nhiều thread **đợi đủ N tín hiệu**. Thread báo hiệu gọi `countDown()` và **không phải đợi các bên khác**. Khi bộ đếm về 0, latch mở vĩnh viễn; không có thao tác reset.
- **CyclicBarrier:** một nhóm có **số bên tham gia cố định** cùng đi tới một điểm hẹn. Mỗi bên gọi `await()`; khi đủ số bên, tất cả cùng vượt qua điểm hẹn. Sau khi vượt thành công, barrier **tự sẵn sàng cho vòng kế tiếp**, không phải tạo lại.

Ví dụ thực tế:

- Khởi tạo ba module độc lập rồi mở dịch vụ khi cả ba đã báo sẵn sàng: `CountDownLatch(3)`.
- Bốn worker tính toán theo từng vòng; mọi worker phải hoàn tất vòng hiện tại trước khi bắt đầu vòng tiếp theo: `CyclicBarrier(4)`.
- Một thread điều phối đợi nhiều worker, nhưng các worker không cần đợi nhau: latch là mô hình gọn hơn.
- Một pipeline nhiều pha mà số lượng bên tham gia thay đổi: có thể cần `Phaser` thay vì barrier cố định.

**Không phải “CountDownLatch là CyclicBarrier kém tính năng hơn”.** Hai công cụ có semantics khác nhau về ai phải chờ, việc đếm, khả năng tái sử dụng và cách xử lý lỗi. Bài học giới hạn trong Java Concurrency của JDK, không liên quan đến Spring.

## 2. So sánh tổng quan

| Tiêu chí | `CountDownLatch` | `CyclicBarrier` |
| --- | --- | --- |
| Mục tiêu | Chờ một **mốc** đủ N tín hiệu | Đồng bộ nhiều thread tại một **điểm hẹn**, có thể lặp |
| Khởi tạo | `new CountDownLatch(count)`, `count >= 0` | `new CyclicBarrier(parties)`, `parties >= 1` |
| Ai báo đã đến? | `countDown()` | `await()` |
| Thread báo hiệu có phải chờ không? | **Không**; `countDown()` trả về ngay | **Có**; `await()` đợi đủ party |
| Ai đợi? | Một hoặc nhiều thread gọi `await()`; không bắt buộc chính là bên phát tín hiệu | Thông thường chính các party gọi `await()` |
| Khi đủ bên/mốc | Bộ đếm về 0; mọi awaiter được mở | Barrier tripped; mọi party của vòng đó được giải phóng |
| Tái sử dụng | **Không**; one-shot | **Có**; tự sang thế hệ/vòng tiếp theo sau khi tripped |
| Hết hạn đợi | `await(timeout,unit)` trả `false` | `await(timeout,unit)` ném `TimeoutException` và làm barrier **broken** |
| Một bên bị interrupt khi chờ | Awaiter đó ném `InterruptedException`; latch **không broken** | Một party ném `InterruptedException` và barrier thường bị **broken**, các bên chờ khác ném `BrokenBarrierException` |
| Hành động khi đủ bên | Không có callback tự động của latch | Có tùy chọn `barrierAction` chạy trước khi thả các party |
| Phục hồi sau lỗi | Kiểm soát tín hiệu/timeout ở tầng ứng dụng, hoặc tạo latch mới | Có `reset()`, nhưng cần phối hợp ngoài; nhiều tình huống nên tạo barrier mới |
| Quan sát | `getCount()` | `getParties()`, `getNumberWaiting()`, `isBroken()` |

Một điểm quan trọng về chữ *tái sử dụng*: `CyclicBarrier` tự tái sử dụng **sau một vòng thành công**. `reset()` không phải thao tác bắt buộc sau từng vòng; nó là cách đưa barrier về trạng thái ban đầu, thường phục vụ khôi phục lỗi và có nguy cơ phá vỡ những thread đang chờ.

## 3. Cơ chế của CountDownLatch: chỉ mở một lần

Tạo latch bằng bộ đếm ban đầu N:

```java
import java.util.concurrent.CountDownLatch;

CountDownLatch done = new CountDownLatch(3);
```

Mỗi khi một phần công việc hoàn tất, thread thực hiện:

```java
done.countDown();
```

Bộ đếm biến thiên:

```text
Ban đầu: 3
Sự kiện A: 2
Sự kiện B: 1
Sự kiện C: 0 → latch mở
Các countDown sau đó: vẫn 0
Mọi await sau mốc này: trở về ngay
```

Thread cần chờ có thể dùng:

```java
done.await(); // Chờ đến khi count về 0 hoặc bị interrupt.
```

Lời gọi `countDown()` không block đợi các bên khác; nó chỉ giảm số đếm nếu còn dương. `await()` có thể được gọi **trước hoặc sau** khi các tín hiệu đến. Nếu đã về 0, `await()` có thể trả về ngay mà không yêu cầu tín hiệu mới.

### Một tín hiệu không nhất thiết tương ứng một thread

`CountDownLatch(3)` không có danh sách đăng ký ba thread. Có thể ba worker cùng báo mỗi worker một lần, hoặc một worker báo ba mốc riêng biệt theo thiết kế. Latch chỉ biết **số lần đếm**, không biết danh tính hay trạng thái thành công của bên phát tín hiệu.

Nếu một worker nhầm lẫn gọi `countDown()` hai lần, latch có thể mở sớm dù một công việc khác chưa hoàn tất. Nếu một worker bị lỗi mà không gọi `countDown()`, bộ đếm có thể không bao giờ về 0. Cần thiết kế ownership của từng tín hiệu và dùng `try/finally` nếu hợp đồng là “đã thoát khỏi phần xử lý”:

```java
Runnable work = () -> {
    try {
        performWork();
    } finally {
        done.countDown();
    }
};
```

Đây là fragment; `performWork()` và `done` do ứng dụng khai báo. `finally` bảo đảm báo khi *body đã chạy rồi thoát*, không bảo đảm task thành công. Nếu task bị từ chối hoặc bị hủy trước khi bắt đầu, `finally` trong task **không chạy**; người điều phối phải xử lý trường hợp thiếu tín hiệu.

### Có thể tạo cổng mở bằng count = 1

`CountDownLatch start = new CountDownLatch(1)` cho phép nhiều thread chờ một lệnh mở cổng. Thread điều phối gọi `start.countDown()` một lần; tất cả waiter đủ điều kiện đi tiếp. Không có chuyện waiter phải tự gọi `countDown()` để được đi qua.

## 4. Cơ chế CyclicBarrier: các party tự đợi nhau

Tạo một barrier cho ba bên:

```java
import java.util.concurrent.CyclicBarrier;

CyclicBarrier barrier = new CyclicBarrier(3);
```

Mỗi party, sau khi hoàn tất phần việc của vòng hiện tại, gọi `barrier.await()`. Hai bên đầu tiên sẽ chờ; bên thứ ba đến làm barrier tripped và mở đường cho cả ba. Trong vòng kế tiếp, ba bên lại gọi `await()` trên **chính đối tượng barrier đó**.

```text
Vòng 1:
A đến await ──┐
B đến await ──┤ đợi đủ ba party
C đến await ──┘ → tripped → cả A, B, C tiếp tục

Vòng 2 (cùng CyclicBarrier):
B đến await ──┐
C đến await ──┤ đợi đủ ba party
A đến await ──┘ → tripped → cả A, B, C tiếp tục
```

Không cần gọi `reset()` giữa vòng 1 và vòng 2 thành công. Thứ tự party tới barrier không nhất thiết giống nhau qua các vòng.

### Arrival index và barrier action

`await()` trả về **arrival index**: bên đến cuối nhận `0`, bên đầu tiên nhận `parties - 1`. Index không phải ID thread hay thứ tự cố định qua các vòng.

Constructor tùy chọn:

```java
CyclicBarrier barrier = new CyclicBarrier(
        3,
        () -> System.out.println("Một vòng đã đủ ba party"));
```

`barrierAction` được thực thi bởi **party đến cuối cùng** sau khi tất cả party đã tới barrier nhưng **trước khi các bên được giải phóng**. Nó chạy một lần cho mỗi vòng thành công. Có thể dùng action để tổng hợp trạng thái của vòng hoặc cập nhật mốc pha, miễn hành động đủ ngắn và tránh chờ chính những party đang bị giữ ở barrier.

Nếu `barrierAction` ném exception, exception đó truyền ra trên thread thực thi action và barrier đi vào trạng thái **broken**. Các party khác đang chờ có thể nhận `BrokenBarrierException`. Không đặt logic dễ lỗi hoặc blocking dài trong action mà không thiết kế cách phục hồi.

## 5. Ví dụ Java 17 độc lập: latch một lần và barrier qua ba vòng

Chương trình gồm hai phép thử.

- Với latch: ba task tính ra các giá trị 2, 4, 6 và báo hoàn tất. Thread chính đợi latch về 0, tính tổng, rồi gọi `await()` thêm lần nữa; lần chờ thứ hai không cần tín hiệu nào mới.
- Với barrier: ba worker tính ba vòng trên mảng riêng theo chỉ số và cùng chờ tại barrier ở cuối mỗi vòng. `barrierAction` đếm số vòng đã tripped; sau khi worker hoàn tất, main tổng hợp các số nguyên và in ra theo thứ tự xác định.

Lưu thành **`LatchVsBarrierDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LatchVsBarrierDemo {
    public static void main(String[] args) throws Exception {
        latchOnce();
        barrierAcrossRounds();
    }

    private static void latchOnce() throws Exception {
        CountDownLatch completed = new CountDownLatch(3);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        int[] values = new int[3];

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 3; index++) {
                final int i = index;
                futures.add(pool.submit(() -> {
                    try {
                        values[i] = 2 * (i + 1);
                    } finally {
                        completed.countDown();
                    }
                }));
            }

            completed.await();
            for (Future<?> future : futures) {
                future.get(); // Ghi nhận lỗi task, nếu có.
            }

            int total = values[0] + values[1] + values[2];
            System.out.println("Latch: count="
                    + completed.getCount() + ", total=" + total);

            completed.await(); // Count vẫn là 0: không đợi vòng mới.
            System.out.println("Latch second await: returned");
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    private static void barrierAcrossRounds() throws Exception {
        final int parties = 3;
        final int rounds = 3;
        int[][] results = new int[rounds][parties];
        AtomicInteger completedRounds = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(
                parties,
                completedRounds::incrementAndGet);
        ExecutorService pool = Executors.newFixedThreadPool(parties);

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < parties; index++) {
                final int worker = index;
                futures.add(pool.submit(() -> {
                    for (int phase = 0; phase < rounds; phase++) {
                        results[phase][worker] =
                                10 * (phase + 1) + worker;
                        barrier.await(); // Điểm hẹn mỗi vòng.
                    }
                    return null;
                }));
            }

            for (Future<?> future : futures) {
                future.get(); // Đợi tất cả worker và phát hiện lỗi.
            }

            System.out.println("Barrier: phases="
                    + completedRounds.get());
            for (int phase = 0; phase < rounds; phase++) {
                int sum = 0;
                for (int value : results[phase]) {
                    sum += value;
                }
                System.out.println("Round " + (phase + 1)
                        + " total=" + sum);
            }
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Workers did not terminate");
            }
        }
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac LatchVsBarrierDemo.java
java LatchVsBarrierDemo
```

Stdout của lần chạy thành công:

```text
Latch: count=0, total=12
Latch second await: returned
Barrier: phases=3
Round 1 total=33
Round 2 total=63
Round 3 total=93
```

Trong phép thử barrier, ba worker ghi vào ba vị trí khác nhau của từng hàng mảng (ví dụ vòng 1 là 10, 11, 12). Chúng không tranh ghi cùng phần tử. `Future.get()` trước khi main đọc mảng cũng bảo đảm đã hoàn tất và có quan hệ visibility phù hợp. Trật tự worker đạt barrier có thể khác nhau, nhưng mọi vòng thành công chỉ tăng `completedRounds` một lần; không có lệnh in từ worker nên stdout xác định.

**Giới hạn của demo:** Nó giả định tất cả task được executor nhận và có thể tiến triển. Nếu pool thiếu worker hoặc một task bị kẹt trước `await()`, các party khác có thể chờ vô hạn. Trong ứng dụng thật nên có timeout, interruption/cancellation và chính sách xử lý barrier hỏng. Demo cố ý tách thành công bình thường và lỗi để dễ thấy semantics cơ bản.

## 6. Vì sao không thay barrier bằng một CountDownLatch dùng lại?

Sau khi bộ đếm của latch về 0, không có API tăng đếm hoặc reset về giá trị ban đầu:

```java
CountDownLatch latch = new CountDownLatch(2);
latch.countDown();
latch.countDown();
latch.await(); // Đủ điều kiện đi tiếp.
latch.await(); // Cũng đi tiếp ngay; KHÔNG phải vòng hai.
```

Vì vậy, nếu mỗi worker cần **đợi nhau cuối mỗi vòng**, một latch duy nhất không đủ. Có thể tạo một `CountDownLatch` mới cho mỗi vòng và truyền tới đúng bên báo/đợi, nhưng điều đó đòi hỏi giao thức công bố latch mới và chuyển pha đúng lúc. Trong trường hợp số bên cố định, `CyclicBarrier` thường đơn giản hơn.

Ngược lại, đừng ép `CyclicBarrier` làm nhiệm vụ “main đợi ba worker hoàn thành” theo cách để **main chỉ chờ** nhưng ba worker chỉ phát tín hiệu rồi đi tiếp mà không chờ. Với barrier, mỗi party tham gia phải gọi `await()` và có thể bị chặn. `CountDownLatch` diễn đạt ý định của bài toán một chiều rõ hơn.

## 7. Failure: timeout và interruption có hiệu ứng khác nhau

Đây là khác biệt quan trọng nhất khi xử lý lỗi.

### 7.1. CountDownLatch không có trạng thái broken

`CountDownLatch.await(timeout, unit)` trả:

- `true` nếu bộ đếm đạt 0 trong thời gian chờ.
- `false` nếu hết thời hạn mà bộ đếm chưa về 0.
- `InterruptedException` nếu thread chờ bị interrupt.

Timeout hoặc interruption **không tự thay đổi bộ đếm** và không hủy các worker. Một thread đợi khác vẫn có thể chờ tiếp, và các lần `countDown()` sau đó vẫn có thể đưa count về 0. Không cần gọi reset vì latch không có reset; cũng không có `isBroken()`.

```java
CountDownLatch neverOpened = new CountDownLatch(1);
boolean reached = neverOpened.await(0, TimeUnit.NANOSECONDS);
System.out.println(reached); // false
System.out.println(neverOpened.getCount()); // 1
```

Đây là fragment nếu đặt cùng import thích hợp. Với timeout bằng 0 và count còn dương, `await(timeout)` không chờ; latch vẫn chưa mở và có thể nhận `countDown()` sau đó.

### 7.2. CyclicBarrier có trạng thái broken

`CyclicBarrier.await(timeout, unit)` có thể ném:

- `TimeoutException` khi chính lời gọi hết thời hạn; việc timeout làm barrier đi vào trạng thái **broken**.
- `InterruptedException` khi thread đang chờ bị interrupt; barrier bị broken, trừ trường hợp cuộc đua với một lần trip/điểm ngắt có semantics theo API.
- `BrokenBarrierException` nếu party khác bị interrupt/timeout, barrier đã broken, `reset()` xảy ra khi đang chờ hoặc barrier action lỗi.

Mô hình này thường được gọi là **all-or-none breakage**: khi một party không thể cùng vượt qua barrier của vòng hiện tại, những party khác không tiếp tục chờ một vòng thành công không còn khả thi.

`barrier.isBroken()` báo trạng thái hỏng; `barrier.getNumberWaiting()` cho biết số party hiện đang bị chặn trong `await()` (phục vụ chẩn đoán, không phải dữ kiện atomic để quyết định tiến trình). `barrier.getParties()` trả số party yêu cầu ban đầu và không tự thay đổi giữa các vòng.

### 7.3. Ví dụ lỗi có kết quả xác định

Lưu `BrokenBarrierDemo.java`:

```java
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BrokenBarrierDemo {
    public static void main(String[] args) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);

        try {
            // Chỉ có main gọi await; timeout 0 khiến nó không chờ.
            barrier.await(0, TimeUnit.NANOSECONDS);
            throw new AssertionError("Expected timeout");
        } catch (TimeoutException expected) {
            System.out.println("Timed out");
        }

        System.out.println("Broken: " + barrier.isBroken());
        barrier.reset();
        System.out.println("Broken after reset: " + barrier.isBroken());
    }
}
```

Chạy:

```bash
javac BrokenBarrierDemo.java
java BrokenBarrierDemo
```

Stdout:

```text
Timed out
Broken: true
Broken after reset: false
```

Trong ví dụ này không có hai party tới cùng lúc nên cuộc hẹn không thể hoàn thành. Sau timeout, trạng thái broken là `true`. `reset()` đưa barrier về trạng thái ban đầu; **nó không có nghĩa những task cũ đã tự khôi phục**. Code ứng dụng phải bảo đảm mọi bên đang chờ và đang chạy đã nhận biết lỗi, sau đó thống nhất có tiếp tục hay không.

## 8. reset() không phải cơ chế phục hồi tự động

`CyclicBarrier.reset()` đưa barrier về trạng thái ban đầu, nhưng nếu đang có party đợi, những party đó nhận `BrokenBarrierException`. Nếu một số thread đang xử lý lỗi của vòng cũ còn thread khác bắt đầu vòng mới, việc reset thiếu phối hợp có thể gây race hoặc lẫn thế hệ.

Cần phân biệt:

- **Vòng kết thúc thành công:** không gọi `reset()`; barrier tự tái sử dụng.
- **Vòng bị broken:** mọi party cần biết lỗi, dừng/hủy/rollback phần việc liên quan theo nghiệp vụ; nếu muốn khôi phục, các thread phải phối hợp rõ việc tạo barrier mới hoặc reset.
- **Thêm hoặc bớt worker:** `CyclicBarrier` có số party cố định. `reset()` **không đổi** `getParties()`. Nếu tập tham gia thay đổi, tạo barrier mới với số party phù hợp hoặc dùng `Phaser`.

Một bên timeout không tự động hủy công việc của các bên khác đang xử lý ngoài barrier. Sau `BrokenBarrierException`, ứng dụng cần thiết kế đường thoát, hủy `Future`, interrupt và cleanup nếu không muốn một số worker tiếp tục tính toán mất kiểm soát.

## 9. Interruption và hủy task: phải xử lý như điều kiện điều phối

Hai phương thức `CountDownLatch.await()` và `CyclicBarrier.await()` đều có thể bị interrupt, nhưng hậu quả với synchronizer khác nhau:

| Sự kiện | Latch | Barrier |
| --- | --- | --- |
| Một thread chờ bị interrupt | Thread đó ném `InterruptedException`; các thread khác vẫn có thể đợi count về 0 | Có thể làm thế hệ hiện tại broken; bên khác nhận `BrokenBarrierException` |
| Caller timeout | Bộ đếm không đổi, caller nhận `false` | Thế hệ bị broken, caller nhận `TimeoutException` |
| Worker chưa từng bắt đầu | Có thể thiếu một `countDown()`, khiến latch không mở | Có thể thiếu một party, khiến các bên đã đến barrier chờ mãi |
| Hủy `Future` trước khi body chạy | `finally` bên trong body không chạy; cần accounting riêng | Party không đến barrier; các bên khác cần timeout/đường hủy |
| Worker giữ lock mà party khác cần | Có thể tạo circular wait | Có thể tạo circular wait |

Khi dùng executor, hãy lưu các `Future` nếu cần cancel; dùng timeout phù hợp và xử lý `InterruptedException`. Nếu một bên của barrier lỗi trước khi gọi `await()`, các bên khác **không tự biết** vì chưa có sự kiện gây broken ở barrier. Có thể cần `reset()` bởi thread điều phối, `Future.cancel(true)`, hoặc timeout tại các awaiter để tránh chờ vô hạn.

**Không nuốt `InterruptedException`.** Trong method cho phép, truyền ngoại lệ lên trên. Trong method không thể truyền, thường khôi phục interrupt flag bằng `Thread.currentThread().interrupt()` và thoát. Với barrier bị broken, không lặp `await()` mãi trên cùng barrier mà không có giao thức khôi phục.

## 10. Happens-before: điều gì được công bố khi vượt mốc?

### CountDownLatch

Hợp đồng memory consistency của latch: các hành động của một thread **trước khi `countDown()`** *happen-before* các hành động **sau khi `await()` tương ứng thành công** ở thread khác. Điều này giúp thread đợi thấy trạng thái được chuẩn bị trước khi các tín hiệu hoàn tất.

Ví dụ fragment:

```java
int[] shared = new int[1];
CountDownLatch ready = new CountDownLatch(1);

// Worker:
shared[0] = 42;
ready.countDown();

// Thread khác:
ready.await();
System.out.println(shared[0]); // Thấy ghi trước countDown.
```

Những dòng đánh dấu “Worker” và “Thread khác” là hai luồng thực thi giả định, không phải chương trình độc lập. Nếu `await(timeout)` trả `false` thì không được coi mốc đã đạt hoặc áp dụng bảo đảm của việc chờ **thành công**.

### CyclicBarrier

Hợp đồng memory consistency của barrier: các hành động trước khi một party gọi `await()` *happen-before* hành động trong `barrierAction`, và những hành động của action *happen-before* những hành động sau khi party khác **`await()` thành công** ở vòng đó. Vì thế barrier phù hợp cho thuật toán nhiều pha cần mỗi pha thấy kết quả của pha trước.

Dẫu vậy, latch và barrier **không biến thao tác read-modify-write trên shared mutable state thành nguyên tử**. Nếu hai worker cùng tăng một biến bằng `counter++` trước mốc mà không có đồng bộ hóa phù hợp, vẫn có thể xảy ra lost update. Dùng phần tử mảng tách biệt, `AtomicInteger`, lock hoặc cấu trúc dữ liệu concurrent khi các worker cùng sửa một trạng thái.

## 11. Deadlock do thread pool không đủ worker

Một bẫy nổi bật là **thread starvation deadlock**. Ví dụ:

- `ExecutorService pool = Executors.newFixedThreadPool(2)`.
- Submit ba task; mỗi task có nhiệm vụ gọi `barrier.await()` trên `CyclicBarrier(3)`.
- Hai task đầu giữ cả hai worker và đợi party thứ ba.
- Task thứ ba vẫn ở queue, không có worker trống để bắt đầu.
- Không party nào có thể hoàn tất: cả hai worker đang đợi lẫn nhau và task trong queue.

Bẫy tương tự có thể xuất hiện khi dùng latch với một cổng start: main đợi ba worker đều báo “ready”, nhưng chỉ hai worker của pool có thể bắt đầu, rồi cả hai bị giữ lại ở một latch chưa mở.

Cách tránh:

1. Bảo đảm số worker thực sự có thể chạy đồng thời ít nhất bằng số party **sẽ bị block ở barrier**, hoặc đổi thiết kế để không cần giữ worker suốt thời gian đợi.
2. Dùng `await(timeout, unit)` để không treo vô hạn, đồng thời xử lý `TimeoutException`/`BrokenBarrierException`.
3. Đừng giữ monitor/lock cần cho party khác trong khi chờ barrier hoặc latch.
4. Đừng đặt một `barrierAction` đợi kết quả từ worker khác đang bị chặn ở cùng barrier.
5. Nếu số party thay đổi động hoặc có sự kiện worker rời nhóm, cân nhắc `Phaser`.

Số thread lớn hơn số party cũng không giải quyết được mọi dạng deadlock nếu task giữ lock theo thứ tự vòng hoặc đang chờ I/O không có timeout.

## 12. Khi nào chọn Phaser?

`Phaser` là synchronizer có thể tái sử dụng qua nhiều **pha** và hỗ trợ số party thay đổi bằng `register()`, `bulkRegister(int)` và `arriveAndDeregister()`. Những thao tác như `arriveAndAwaitAdvance()` có vai trò tương tự đi tới một barrier rồi chờ pha tiếp theo.

| Nhu cầu | Công cụ phù hợp |
| --- | --- |
| Chờ đúng N sự kiện một lần | `CountDownLatch` |
| Mở một cổng cho nhiều waiter | `CountDownLatch(1)` |
| Nhóm N worker cố định gặp nhau ở cuối mỗi vòng | `CyclicBarrier(N)` |
| Nhóm worker có thể đăng ký/rút khỏi pha | `Phaser` |
| Muốn kiểm soát số tác vụ đồng thời bằng permit | `Semaphore` |
| Muốn chờ một `Future<T>` hoàn tất và đọc kết quả/lỗi | `Future.get()` |
| Muốn đợi một `Thread` cụ thể chấm dứt | `Thread.join()` |

Khác biệt đáng nhớ: `CyclicBarrier` có cơ chế **broken** khi một bên bị interrupt/timeout trong lúc chờ; `Phaser` có chính sách khác, các overload đợi pha với timeout/interruption **không tự phá trạng thái phaser** theo cách của CyclicBarrier. Cần đọc hợp đồng của `Phaser` trước khi chuyển qua vì cách xử lý lỗi không giống nhau.

Không chọn `Phaser` chỉ vì nó nhiều tính năng; cho bài toán fixed-party nhiều vòng, `CyclicBarrier` thường dễ đọc. Cho một mốc độc lập, `CountDownLatch` đơn giản và ít giao thức hơn.

## 13. Quy trình thiết kế synchronizer an toàn

Trước khi viết code, hãy tự trả lời:

1. **Mốc hay điểm hẹn?** Bên báo tín hiệu có cần chờ các bên còn lại không?
2. **Một lần hay nhiều pha?** Sau khi mở mốc, có thực sự cần đóng lại để chờ vòng mới không?
3. **Số party cố định?** Ai đăng ký party, ai được phép rời?
4. **Lỗi của một task ảnh hưởng ai?** Với barrier, khi một party thất bại, các party khác có tiếp tục một vòng khác không?
5. **Timeout là lỗi gì?** Latch trả boolean, barrier ném exception và có thể broken.
6. **Đủ worker?** Có task nào phải ở queue trong khi tất cả worker đều đang chờ synchronizer?
7. **Side effect và cleanup?** Tín hiệu đến có nghĩa thành công, đã rời task hay đã giải phóng tài nguyên?
8. **Kết quả được lưu ở đâu?** Latch/barrier không thay `Future` hoặc nơi thu thập exception.
9. **Memory visibility?** Có sử dụng đúng điểm đồng bộ, và có tránh data race giữa những worker cùng ghi dữ liệu không?
10. **Sau lỗi làm gì?** Hủy task, đóng tài nguyên, tạo barrier mới, hay dừng toàn bộ phép tính?

Nếu worker có thể bị hủy **trước khi bắt đầu**, `finally` bên trong task sẽ không chạy; điều phối viên phải xử lý task không được nhận. Với barrier, số party thực sự đến ít hơn kỳ vọng là nguyên nhân điển hình khiến chờ vô hạn.

## 14. Những lỗi phổ biến

| Sai lầm | Hệ quả | Cách khắc phục |
| --- | --- | --- |
| Dùng một `CountDownLatch` cho nhiều lượt rồi tưởng `await()` tự đợi lại | Sau khi count về 0, mọi `await()` tiếp theo đi qua ngay | Tạo latch mới đúng mỗi giai đoạn hoặc dùng `CyclicBarrier` |
| Dùng `CyclicBarrier` chỉ để một thread đợi N tín hiệu không chặn các worker | Các worker bị buộc phải đứng chờ ở `await()` | Dùng `CountDownLatch` |
| Cho rằng `countDown()` đợi bên khác | Hiểu sai độ trễ và luồng xử lý | Nhớ `countDown()` không block |
| Cho rằng barrier tự reset ngay cả khi bị broken | Các `await()` sau tiếp tục nhận lỗi | Phối hợp xử lý lỗi, reset có chủ đích hoặc tạo barrier mới |
| Gọi `reset()` sau mỗi vòng thành công | Có thể phá nhầm party đang tới vòng tiếp theo | Để barrier tự tái sử dụng |
| Dùng `await(timeout)` mà không phân biệt boolean và exception | Xử lý sai lỗi | Latch trả boolean; barrier ném `TimeoutException` |
| Nuốt `InterruptedException` ở một party | Hợp đồng hủy không rõ, barrier có thể broken | Truyền/khôi phục interrupt và điều phối lỗi |
| Cho ít worker hơn số party cần chờ | Thread starvation deadlock | Đủ capacity, timeout, thiết kế lại |
| Đọc `getNumberWaiting()` rồi dự đoán chắc chắn ai sẽ tới | Trạng thái thay đổi concurrent | Dùng để quan sát/debug, không để phối hợp chính |
| Dùng latch/barrier như cơ chế lưu exception hoặc rollback | Task có thể lỗi hoặc gây side effect dù mốc đã đạt | Kết hợp `Future`, result status và cleanup |
| Cho nhiều worker cùng tăng biến thường | Data race dù có barrier | Atomic/lock hoặc tách ô kết quả |
| Chạy I/O lâu trong barrier action | Giữ tất cả party ở barrier, tăng độ trễ | Action ngắn; I/O ở ngoài với timeout riêng |

## 15. Câu hỏi tự kiểm tra

**1. CountDownLatch đã về 0 có thể gọi reset để chờ vòng hai không?**  
Không. Nó là one-shot; muốn có mốc mới thì tạo latch mới hoặc chọn synchronizer khác.

**2. `CyclicBarrier(4)` cần bao nhiêu lần `await()` trong mỗi vòng?**  
Bốn party đến trong cùng một thế hệ. Khi đủ bốn, barrier tripped, rồi tự chuẩn bị vòng kế tiếp.

**3. Một worker chỉ báo hoàn tất và không cần chờ các worker khác nên dùng gì?**  
`CountDownLatch.countDown()` nếu mục tiêu là mở mốc cho các thread khác.

**4. `CountDownLatch.await(1, SECONDS)` bị timeout có ném `TimeoutException` không?**  
Không; overload này trả `false` khi count chưa về 0 và thời gian đã hết.

**5. Một party hết hạn chờ ở `CyclicBarrier.await(timeout,unit)` thì các party khác có tiếp tục chờ vòng đó đến khi đủ số không?**  
Không theo semantics thông thường; barrier trở thành broken và các bên chờ khác nhận `BrokenBarrierException`.

**6. Có cần gọi `barrier.reset()` sau vòng thành công?**  
Không. CyclicBarrier tự tái sử dụng. Reset có thể phá các party đang chờ.

**7. `barrierAction` chạy ở đâu?**  
Trên thread của party đến cuối cùng, trước khi giải phóng các party của vòng đó.

**8. Vì sao pool 2 worker và barrier 3 party có thể deadlock?**  
Hai worker có thể đang chờ party thứ ba, còn party thứ ba bị kẹt trong queue chưa có worker để chạy.

**9. Barrier có tự biến phép `sharedCounter++` của nhiều worker thành an toàn không?**  
Không. Barrier chỉ điều phối mốc pha và visibility; read-modify-write tranh chấp vẫn cần đồng bộ.

**10. Khi số party có thể thay đổi động qua các pha, nên cân nhắc gì?**  
`Phaser` vì nó hỗ trợ đăng ký và rút party, thay vì `CyclicBarrier` có số party cố định.

## Tài liệu tham khảo

- [Java SE 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html)
- [Java SE 17 — CyclicBarrier](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CyclicBarrier.html)
- [Java SE 17 — Phaser](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Phaser.html)
- [Java SE 17 — Semaphore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html)
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronizers-010">Sử dụng synchronizer trong test concurrency</a>

<details>
<summary>Xem chi tiết</summary>

# Sử dụng synchronizer trong test concurrency của Java

## 1. Vì sao kiểm thử đa luồng khó hơn kiểm thử tuần tự?

Trong một phép thử tuần tự, các dòng lệnh chạy theo thứ tự tương đối dễ suy luận. Khi nhiều thread cùng truy cập state, lịch thực thi phụ thuộc scheduler, CPU, lock, I/O, JIT và các quan hệ đồng bộ. Một bài test chỉ chạy thành công một lần **không chứng minh** chương trình luôn đúng dưới mọi interleaving.

Ví dụ có hai worker cùng thực hiện phép tăng `counter++` trên một trường thường. Phép tăng là tổ hợp **đọc → tính → ghi**, không phải một transaction nguyên tử. Nếu chạy thử 100 lần và lần nào cũng nhận kết quả đúng, lỗi **lost update** vẫn có thể tồn tại nhưng chưa xuất hiện do lịch chạy.

Mục tiêu của bài này là sử dụng các **synchronizer trong `java.util.concurrent`** để:

1. Cho nhiều worker tới cùng một **điểm bắt đầu** mà không phải đoán thời gian bằng `Thread.sleep()`.
2. Chủ động buộc một số **interleaving** xảy ra, qua đó tái hiện bug.
3. Chờ kết thúc bằng **tín hiệu có timeout**, tránh test treo vô hạn.
4. Ghi nhận exception từ worker và **làm test thất bại** ở thread kiểm thử.
5. Kiểm tra một số **bất biến** về dữ liệu, số lượt xử lý và giới hạn concurrent access.
6. Phân biệt **deterministic coordination test** với stress test và những công cụ chuyên sâu kiểm tra Java Memory Model.

Đây là kiến thức **Java concurrency nói chung**, dùng ví dụ **Java 17+** và API JDK; không phụ thuộc Spring hay framework kiểm thử cụ thể.

## 2. Synchronizer là gì trong một bài test?

Các công cụ đồng bộ không chỉ phục vụ production; chúng cũng giúp test biểu diễn chính xác mối quan hệ **“A phải xảy ra trước B”**.

| Công cụ | Vai trò hữu ích trong test | Điểm cần tránh |
| --- | --- | --- |
| `CountDownLatch` | Đợi N worker đến mốc; phát một tín hiệu bắt đầu chung; chờ N tác vụ xong | Latch chỉ đi từ count ban đầu về 0, **không reset** |
| `CyclicBarrier` | Buộc N participant gặp nhau ở **nhiều vòng/phases** | Một participant bị lỗi/timeout có thể làm barrier **broken** |
| `Phaser` | Điều phối nhiều phase, số participant có thể **register/deregister** động | Dùng `arriveAndAwaitAdvance()` vô hạn có thể làm test treo |
| `Semaphore` | Kiểm tra tối đa N tác vụ cùng dùng một resource | Chỉ release permit đã acquire; đừng suy chỉ số active từ số thread |
| `BlockingQueue` | Chuyển dữ liệu/sự kiện giữa worker và test thread, chờ có hạn bằng `poll` | Queue rỗng tại một thời điểm không chứng minh toàn pipeline hoàn tất |
| `Future` / `ExecutorService` | Thu kết quả và lỗi worker qua `get()`; giới hạn chờ và gửi cancellation | `get(timeout)` hết hạn **không tự hủy** task |
| `ReentrantLock` + `Condition` | Dàn dựng predicate/condition và kiểm tra wakeup | `await` cần kiểm tra predicate trong **while**, không chỉ `if` |

**Synchronizer không kiểm soát toàn bộ scheduler.** Một latch xác nhận worker đã tới vị trí được đánh dấu; nó không ép worker tiếp tục chạy ngay lập tức sau khi mở cổng và không cho phép suy ra một thứ tự tùy ý chưa được đồng bộ.

### Tên các mốc hữu ích trong test

- **Ready gate:** tất cả worker đã chuẩn bị xong, sắp đợi lệnh bắt đầu.
- **Start gate:** main/test thread mở để các worker được phép đi tiếp.
- **Done gate:** tất cả worker đã đi qua khối cần kiểm tra (kể cả đường lỗi, nếu thiết kế đúng).
- **Phase barrier:** chỉ cho các participant đi qua khi tất cả tới một mốc của **cùng vòng**.
- **Release gate:** giữ worker tại vị trí đã biết cho đến khi test quyết định giải phóng.

Bằng cách nêu rõ các mốc, ta kiểm tra đúng **hợp đồng đồng thời** thay vì so sánh một lượng mili-giây tình cờ.

## 3. Tại sao `Thread.sleep()` không phải cơ chế phối hợp test?

Mẫu dễ gặp:

```java
// Snippet có race: không bảo đảm worker đã thực hiện việc cần thiết.
worker.start();
Thread.sleep(100);
assertState();
```

Đây là snippet, `worker` và `assertState` chưa khai báo. Sleep 100 ms **không xác nhận** worker đã bắt đầu, tới critical section hoặc hoàn tất. Máy CI chậm, GC pause hoặc scheduler bị tải có thể khiến test **flaky**. Ngược lại, trên máy nhanh test có thể luôn pass mà không tạo interleaving cần bắt lỗi.

**Thay bằng một tín hiệu có nghĩa:** worker `ready.countDown()` tại vị trí muốn quan sát; main gọi `ready.await(timeout, unit)` và kiểm tra kết quả. Sau đó nếu muốn cho worker chạy, main mở `start.countDown()`. Tín hiệu thể hiện sự kiện cụ thể, không phải dự đoán thời gian.

Sleep vẫn có thể dùng khi chính **thời gian** là đối tượng kiểm thử, nhưng không nên dùng nó làm bằng chứng ngầm rằng một thread đã đạt một trạng thái logic.

## 4. Pattern cơ bản: ready → start → done với CountDownLatch

Để cho N worker chuẩn bị rồi cùng nhận lệnh bắt đầu:

```text
Main/test                            Worker 1 ... Worker N
---------                            ----------------------
Tạo ready(N), start(1), done(N)
                                     ready.countDown()
                                     start.await()
ready.await(timeout) ----------->    mọi worker đã tới start gate
start.countDown() -------------->    bắt đầu khối cần đo
                                     work()
                                     done.countDown() trong finally
done.await(timeout) ----------->     tất cả đã kết thúc khối
kiểm tra invariant + worker errors
```

**Đừng nhầm** “mở cổng cùng lúc” với “thực thi cùng một thời điểm CPU”. Sau `start.countDown()`, scheduling vẫn bất định. Pattern chỉ đảm bảo các worker **không đi qua start gate trước khi main cho phép**.

### Java 17+ hoàn chỉnh: phép kiểm thử tăng atomic

Tạo file `LatchStartTest.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class LatchStartTest {
    public static void main(String[] args) throws InterruptedException {
        int workers = 4;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        AtomicInteger counter = new AtomicInteger();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        Thread[] threads = new Thread[workers];

        for (int i = 0; i < workers; i++) {
            threads[i] = new Thread(() -> {
                try {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Start gate timed out");
                    }
                    counter.incrementAndGet();
                } catch (Throwable error) {
                    workerFailure.compareAndSet(null, error);
                    if (error instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                } finally {
                    done.countDown();
                }
            }, "worker-" + i);
            threads[i].start();
        }

        try {
            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Workers not ready");
            }
            start.countDown();
            if (!done.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Workers did not finish");
            }
            if (workerFailure.get() != null) {
                throw new AssertionError("Worker failed", workerFailure.get());
            }
            if (counter.get() != workers) {
                throw new AssertionError("Wrong counter");
            }
            System.out.println("Ready workers: " + workers);
            System.out.println("Atomic increments: " + counter.get());
        } finally {
            start.countDown(); // Không để worker kẹt do test thất bại.
            for (Thread thread : threads) {
                thread.interrupt();
            }
            for (Thread thread : threads) {
                thread.join();
            }
        }
    }
}
```

Biên dịch/chạy:

```bash
javac LatchStartTest.java
java LatchStartTest
```

Đầu ra khi không có interrupt từ bên ngoài và test pass:

```text
Ready workers: 4
Atomic increments: 4
```

**Các điểm quan trọng:**

1. Worker báo `ready` **trước** khi đợi `start`, nên main có thể xác nhận chúng đã đến cổng.
2. `AtomicInteger.incrementAndGet()` là một phép cập nhật nguyên tử. Kết quả cuối luôn là 4 nếu cả bốn phép tăng đã chạy thành công.
3. `done.countDown()` nằm trong `finally`, nên một worker ném exception vẫn có thể báo **đã kết thúc đường xử lý**, tránh main chờ vô hạn do thiếu tín hiệu.
4. Exception được ghi vào `AtomicReference<Throwable>` để main **đọc lại và làm test fail**. Exception ở thread phụ không tự biến thành failure của thread main.
5. Mọi `await` nhạy với tiến triển đều có timeout. `finally` mở start gate, gửi interrupt, rồi `join` để giảm nguy cơ bỏ lại worker.
6. Demo này kiểm tra một lần theo lịch đã phối hợp, **không chứng minh** toàn bộ thuật toán concurrent đúng với mọi interleaving.

Trong test production, khi worker có thể cố tình không phản hồi interrupt, `join()` không timeout cuối cùng cũng có nguy cơ treo. Có thể dùng timed join, executor shutdown có deadline và theo dõi thread còn sống; một timeout test không có nghĩa Java có thể cưỡng ép dừng thread một cách an toàn.

## 5. Dựng interleaving để tái hiện lost update một cách xác định

Một phương pháp học hiệu quả là **cố tình viết code sai**, rồi dùng synchronizer **ép hai thread cùng đọc một giá trị cũ trước khi bất kỳ thread nào được phép ghi**.

Giả sử giá trị ban đầu bằng 1. Hai thread đều làm:

```java
int snapshot = counter.value;
counter.value = snapshot + 1;
```

Đây là snippet, `counter` chưa khai báo. Nếu tách read và write bằng latch, cả hai thread có thể cùng đọc 1 rồi đều ghi 2. Kết quả 2 **thay vì 3** là lỗi lost update có thể tái hiện mà **không cần ngủ/đoán scheduling**.

### Java 17+ hoàn chỉnh: chứng minh volatile không biến RMW thành atomic

Tạo file `LostUpdateTest.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class LostUpdateTest {
    private static final class Counter {
        volatile int value = 1;
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch mayWrite = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Runnable nonAtomicIncrement = () -> {
            try {
                int old = counter.value;
                bothRead.countDown();
                if (!mayWrite.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Write gate timeout");
                }
                counter.value = old + 1;
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        Thread left = new Thread(nonAtomicIncrement, "left");
        Thread right = new Thread(nonAtomicIncrement, "right");
        left.start();
        right.start();

        try {
            if (!bothRead.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Both reads not reached");
            }
            mayWrite.countDown();
            left.join();
            right.join();

            if (failure.get() != null) {
                throw new AssertionError("Worker failed", failure.get());
            }
            System.out.println("Observed after two increments: "
                    + counter.value);
            System.out.println("Expected with atomic increments: 3");
        } finally {
            mayWrite.countDown();
            left.interrupt();
            right.interrupt();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac LostUpdateTest.java
java LostUpdateTest
```

Đầu ra:

```text
Observed after two increments: 2
Expected with atomic increments: 3
```

**Tại sao kết quả 2 là xác định?**

- Mỗi worker chụp `old = counter.value` **trước** `bothRead.countDown()`.
- Main chỉ mở `mayWrite` **sau khi cả hai worker đã countDown**, tức cả hai đã đọc giá trị cũ.
- Vì chưa có writer nào được đi qua `mayWrite.await()`, cả hai snapshot đều là **1**.
- Hai worker viết cùng giá trị 2 theo mọi thứ tự; final `value` là 2.
- `volatile` đảm bảo các tính chất visibility/ordering của từng truy cập nhưng **không làm cặp đọc–ghi thành một thao tác atomic**.

Đây là một **test chứng minh bug**: việc in `2` là hành vi có chủ đích, không phải kết quả đúng của thuật toán tăng. Khi sửa production code, dùng `AtomicInteger.incrementAndGet()`, `getAndAdd()`, khóa phù hợp hoặc CAS loop. Không cố “sửa” bằng cách chèn `Thread.sleep` giữa hai dòng.

**Một cảnh báo về test orchestration:** `mayWrite` là đồng bộ **chỉ dùng để dựng lịch**. Nó không đại diện cho cơ chế bảo vệ counter trong code production. Hãy tránh đưa các gate dành cho test vào public API của đối tượng nghiệp vụ nếu có thể dùng injection/hook nội bộ hoặc harness kiểm thử tách biệt.

## 6. CyclicBarrier: nhiều lượt phối hợp, không phải CountDownLatch dùng lại

`CountDownLatch` là một cổng **một lần**: count về 0 thì luôn mở. Nếu test cần worker gặp nhau qua nhiều vòng, `CyclicBarrier` thường phù hợp hơn.

`new CyclicBarrier(parties, barrierAction)` cho phép **N participant** cùng gọi `await()`. Khi participant cuối đến, barrier action chạy trước khi những waiter được thả; barrier được dùng lại cho **generation tiếp theo**.

- `await()` có thể ném `InterruptedException` hoặc `BrokenBarrierException`.
- `await(timeout, unit)` có thể ném thêm `TimeoutException`.
- Nếu một participant bị interrupt/timeout, barrier có thể chuyển sang **broken state**. Các participant khác nhận lỗi tương ứng; test phải có cleanup.
- `getNumberWaiting()` chỉ là snapshot để quan sát, không nên dùng làm cơ sở duy nhất để ra quyết định logic test.

### Java 17+: hai worker và main qua hai phase

Tạo file `BarrierRoundsTest.java`:

```java
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class BarrierRoundsTest {
    public static void main(String[] args) throws Exception {
        AtomicInteger completedRounds = new AtomicInteger();
        AtomicInteger workerSteps = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        CyclicBarrier barrier = new CyclicBarrier(
                3, completedRounds::incrementAndGet);

        Runnable worker = () -> {
            try {
                for (int round = 0; round < 2; round++) {
                    workerSteps.incrementAndGet();
                    barrier.await(5, TimeUnit.SECONDS);
                }
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        };

        Thread first = new Thread(worker, "first");
        Thread second = new Thread(worker, "second");
        first.start();
        second.start();

        try {
            for (int round = 0; round < 2; round++) {
                barrier.await(5, TimeUnit.SECONDS);
            }
            first.join();
            second.join();

            if (failure.get() != null) {
                throw new AssertionError("Worker failed", failure.get());
            }
            System.out.println("Barrier rounds: "
                    + completedRounds.get());
            System.out.println("Worker steps: " + workerSteps.get());
        } finally {
            barrier.reset();
            first.interrupt();
            second.interrupt();
            first.join();
            second.join();
        }
    }
}
```

Chạy:

```bash
javac BarrierRoundsTest.java
java BarrierRoundsTest
```

Đầu ra khi không có interrupt bên ngoài:

```text
Barrier rounds: 2
Worker steps: 4
```

**Phân tích:**

- Barrier có **3 parties**: main + hai worker.
- Mỗi worker tăng `workerSteps` trước khi chạm barrier ở mỗi phase; tổng 2 × 2 = 4.
- Barrier action tăng `completedRounds` **một lần cho mỗi generation thành công**; có hai generation nên tổng bằng 2.
- Không cần biết worker nào chạy trước.
- Nếu worker bỏ cuộc hoặc một lời gọi đợi quá hạn, main không được tiếp tục báo test pass. Phải làm test thất bại, reset/break barrier và dọn thread.

`barrier.reset()` trong `finally` chỉ là cleanup của demo sau khi main đã hoàn thành hoặc gặp lỗi; **không** thay thế cơ chế recovery đúng của một thuật toán production đang dùng barrier.

### Dùng barrier để phát hiện lỗi nào?

- Hai giai đoạn ghi/đọc dữ liệu phải hoàn thành trước khi bước sang round tiếp.
- Chặn worker ở các checkpoint được biết trước để kiểm tra trạng thái trung gian.
- Tạo áp lực chạy đồng thời mà không phụ thuộc `sleep`.
- Xác nhận số lần một phase hoàn tất; thử các đường exception/timeout của participant.

Tuy nhiên, barrier không kiểm tra tự động rằng dữ liệu chia sẻ được bảo vệ đúng. Sau mỗi phase, **vẫn cần assertion** về invariant theo Java Memory Model và hợp đồng của hệ thống.

## 7. Phaser: nhiều phase và số participant thay đổi

`Phaser` phù hợp khi số worker tham gia thay đổi theo thời điểm. Các API:

- `register()` / `bulkRegister(n)`: đăng ký participant.
- `arriveAndAwaitAdvance()`: báo đã tới phase hiện tại và chờ chuyển phase.
- `arriveAndDeregister()`: báo đã tới và **rút khỏi những phase tiếp theo**.
- `getPhase()`: phase hiện tại; khi terminated có thể trả giá trị âm theo hợp đồng.
- `awaitAdvanceInterruptibly(phase, timeout, unit)`: đợi một phase thay đổi bằng một lần chờ hỗ trợ interrupt/timeout.

**Điểm khác:** `arriveAndAwaitAdvance()` có đường chờ **uninterruptible** theo hợp đồng, nên không tự coi đây là wait có timeout. Nếu một participant biến mất mà không arrive/deregister, phase có thể không tiến triển.

### Ví dụ Java 17+ hoàn chỉnh: đăng ký worker động

Tạo file `PhaserRegistrationTest.java`:

```java
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;

public class PhaserRegistrationTest {
    public static void main(String[] args) throws InterruptedException {
        Phaser phaser = new Phaser(1); // Main là một party.
        AtomicInteger operations = new AtomicInteger();
        Thread[] workers = new Thread[2];

        for (int i = 0; i < workers.length; i++) {
            phaser.register();
            workers[i] = new Thread(() -> {
                try {
                    operations.incrementAndGet();
                    phaser.arriveAndAwaitAdvance();
                } finally {
                    phaser.arriveAndDeregister();
                }
            }, "phase-worker-" + i);
            workers[i].start();
        }

        // Đợi cả main và hai worker đều tới phase 0.
        phaser.arriveAndAwaitAdvance();
        phaser.arriveAndDeregister();

        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("Operations: " + operations.get());
        System.out.println("All parties deregistered: "
                + phaser.isTerminated());
    }
}
```

Chạy:

```bash
javac PhaserRegistrationTest.java
java PhaserRegistrationTest
```

Đầu ra khi cả hai worker không lỗi:

```text
Operations: 2
All parties deregistered: true
```

**Giải thích:** main bắt đầu là một party, rồi đăng ký hai worker. Mỗi worker thực hiện một operation rồi arrive phase 0. Main cũng arrive; khi cả ba đã tới thì phase có thể advance. Sau đó mỗi participant `arriveAndDeregister()`, cuối cùng không còn party đăng ký nên phaser kết thúc.

**Cảnh báo ứng dụng:** demo này ưu tiên cho thấy cơ chế register/deregister. Trong test production, nếu một worker ném exception trước `arriveAndAwaitAdvance` hoặc bị kẹt, main có thể chờ vô hạn. Khi cần deadline chắc chắn, dùng đường `arrive()` rồi `awaitAdvanceInterruptibly(phase, timeout, unit)`, kèm `forceTermination()` và cleanup khi test thất bại; đồng thời nhớ **ghi nhận lỗi worker** ở thread kiểm thử. Không dùng `Thread.sleep` để “chắc” worker đã tới phase.

## 8. Semaphore: kiểm tra trần mức đồng thời của tài nguyên

`Semaphore` đặc biệt hữu ích để test **không bao giờ có hơn N tác vụ cùng vào vùng sử dụng tài nguyên**. Một Semaphore với 2 permit giới hạn tối đa hai người giữ permit tại cùng thời điểm **nếu mọi đường vào đều thực hiện acquire và mọi đường ra release đúng quy tắc**.

Dùng hai worker chiếm hết permit, đồng thời đặt worker thứ ba đang chờ. Test kiểm tra worker thứ ba **không thể vào trước khi main giải phóng** hai worker đầu.

### Java 17+: mô phỏng tài nguyên có hai slot

Tạo file `SemaphoreLimitTest.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SemaphoreLimitTest {
    public static void main(String[] args) throws InterruptedException {
        Semaphore slots = new Semaphore(2);
        CountDownLatch twoInside = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch thirdAttempted = new CountDownLatch(1);
        CountDownLatch thirdEntered = new CountDownLatch(1);
        AtomicInteger current = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Runnable occupant = () -> {
            boolean acquired = false;
            try {
                slots.acquire();
                acquired = true;
                int now = current.incrementAndGet();
                peak.accumulateAndGet(now, Math::max);
                twoInside.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Release gate timed out");
                }
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                if (acquired) {
                    current.decrementAndGet();
                    slots.release();
                }
            }
        };

        Thread a = new Thread(occupant, "a");
        Thread b = new Thread(occupant, "b");
        Thread c = new Thread(() -> {
            boolean acquired = false;
            try {
                thirdAttempted.countDown();
                slots.acquire();
                acquired = true;
                thirdEntered.countDown();
            } catch (InterruptedException e) {
                failure.compareAndSet(null, e);
                Thread.currentThread().interrupt();
            } finally {
                if (acquired) {
                    slots.release();
                }
            }
        }, "c");

        a.start();
        b.start();

        try {
            if (!twoInside.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("First two did not enter");
            }
            c.start();
            if (!thirdAttempted.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Third did not attempt");
            }
            System.out.println("Third entered before release: "
                    + (thirdEntered.getCount() == 0));
            release.countDown();

            a.join();
            b.join();
            c.join();
            if (failure.get() != null) {
                throw new AssertionError("Worker failed", failure.get());
            }
            System.out.println("Peak occupied: " + peak.get());
            System.out.println("Available permits: "
                    + slots.availablePermits());
        } finally {
            release.countDown();
            a.interrupt();
            b.interrupt();
            c.interrupt();
            a.join();
            b.join();
            if (c.getState() != Thread.State.NEW) {
                c.join();
            }
        }
    }
}
```

Chạy:

```bash
javac SemaphoreLimitTest.java
java SemaphoreLimitTest
```

Đầu ra bình thường:

```text
Third entered before release: false
Peak occupied: 2
Available permits: 2
```

**Tại sao giá trị trước release phải là false?** Main chỉ tạo worker c sau khi `twoInside` đã về 0: hai worker a/b đã **acquire và chưa release** permit, vì còn chờ cổng `release`. Worker c có thể đã bắt đầu gọi `acquire` hoặc đang sắp gọi, nhưng không thể lấy permit khi `availablePermits == 0`. Do đó `thirdEntered` chưa được countDown trước khi main mở cổng.

**Tại sao peak bằng 2?** Chỉ hai worker a/b tăng bộ đếm `current` sau acquire; với việc release đúng sau sử dụng, không có lượt tăng thứ ba. Ví dụ kiểm tra giới hạn slot, không cố dùng scheduling để tăng peak.

Trong hệ thống thật, muốn theo dõi **toàn bộ** việc dùng resource, mọi caller (kể cả worker c) phải cập nhật metric theo cùng giao thức. Demo này chỉ đo occupancy của hai worker được thiết kế để giữ slot và quan sát worker thứ ba có được vào sớm hay không.

**Quy tắc permit:** chỉ `release()` nếu `acquire()` đã thành công. Nếu release khi chưa acquire, semaphore có thể tăng permit vượt mức dự định và test cho kết quả sai.

## 9. Future.get(timeout): quan sát lỗi worker và không để test chờ mãi

`Thread.start()` không tự mang exception trong worker về thread test. Nếu worker ném `AssertionError` trong một thread khác, test main **không nhất thiết fail** chỉ vì stack trace được in ra. Có hai cách phổ biến:

- Với `Thread` trực tiếp: catch `Throwable` trong worker, lưu vào `AtomicReference<Throwable>` rồi throw `AssertionError` ở main sau khi `join` hoặc `done.await`.
- Với `ExecutorService.submit(Callable/Runnable)`: giữ `Future`, gọi `get(timeout, unit)` để nhận giá trị hoặc exception. Nếu task thất bại, `get` ném `ExecutionException` và có `getCause()`. Nếu quá hạn, `get` ném `TimeoutException`.

**Lưu ý:** `get(timeout)` **không tự cancel** task. Caller phải xác định có nên `future.cancel(true)`, `executor.shutdownNow()` hoặc chờ/cleanup bằng cơ chế khác.

### Java 17+ hoàn chỉnh: timeout và hủy task chờ latch

Tạo file `FutureTimeoutTest.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureTimeoutTest {
    public static void main(String[] args) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<String> result = executor.submit(() -> {
                started.countDown();
                gate.await();
                return "released";
            });

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Task never started");
            }

            boolean timedOut = false;
            try {
                result.get(20, TimeUnit.MILLISECONDS);
            } catch (TimeoutException expected) {
                timedOut = true;
            }

            System.out.println("Timed out: " + timedOut);
            boolean cancelRequested = result.cancel(true);
            System.out.println("Cancellation accepted: "
                    + cancelRequested);
            System.out.println("Future cancelled: "
                    + result.isCancelled());
        } finally {
            gate.countDown();
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Executor did not stop");
            }
        }
    }
}
```

Biên dịch/chạy:

```bash
javac FutureTimeoutTest.java
java FutureTimeoutTest
```

Đầu ra trong lần chạy bình thường:

```text
Timed out: true
Cancellation accepted: true
Future cancelled: true
```

Cổng `gate` không mở trước lần `get(20ms)`, nên task không thể trả `released` trước timeout. Sau đó `cancel(true)` gửi yêu cầu hủy và chuyển Future sang trạng thái cancelled. `gate.countDown()` trong `finally` đảm bảo ví dụ không cố giữ worker chờ nếu tình huống thay đổi.

**Không kết luận:** `Future.isCancelled() == true` không chứng minh mã task đã dừng tại **đúng thời điểm đọc cờ** hay mọi side effect được hoàn tác. `awaitTermination()` cho executor một khoảng để kết thúc, còn code task phải hợp tác với interrupt nếu cần hủy nhanh.

### Ví dụ ngắn về exception từ Future

```java
// Snippet trong phương thức có executor và imports thích hợp.
Future<?> failed = executor.submit(() -> {
    throw new IllegalStateException("bad task");
});
try {
    failed.get(1, TimeUnit.SECONDS);
} catch (java.util.concurrent.ExecutionException e) {
    throw new AssertionError("Worker failed", e.getCause());
}
```

Đoạn này chưa là file độc lập. Nếu một task ném `IllegalStateException`, `Future.get` sẽ phản ánh nó bằng `ExecutionException`. Phương thức bao quanh cũng cần xử lý `InterruptedException` và `TimeoutException`; ví dụ chỉ tập trung vào việc **không bỏ qua lỗi worker**.

## 10. Latch, barrier và Phaser bảo đảm visibility gì?

Synchronizer không chỉ chặn/đánh thức; các API trong `java.util.concurrent` có các **memory-consistency effects** giúp tạo quan hệ happens-before.

### CountDownLatch

Các hành động **trước** `countDown()` của một thread happens-before các hành động **sau khi** `await()` tương ứng trở về thành công ở thread khác.

Ví dụ writer:

```java
data = 42;
ready.countDown();
```

Reader:

```java
ready.await();
int snapshot = data;
```

Đây là hai snippet không độc lập; giả sử `data` và `ready` có cùng ownership/giao thức. Khi reader đã chờ latch về 0, các ghi trước `countDown()` được công bố theo hợp đồng. Tuy nhiên, nếu writer **tiếp tục sửa data sau đó**, latch không đóng băng object thành snapshot; cần đồng bộ cho lần cập nhật tiếp theo.

### CyclicBarrier

Các hành động của participant trước `await()` được công bố theo hợp đồng của `CyclicBarrier` đối với barrier action, và từ barrier action tới các hành động sau `await()` thành công của các participant. Dùng barrier giúp tách các **phase công việc**; nhưng nếu một participant thoát trước barrier, phải xử lý broken barrier, không giả vờ dữ liệu đã ở phase mới.

### Phaser

`Phaser` có các bảo đảm về việc hoàn thành phase và những hành động được công bố khi phase advance. Khi dùng `arriveAndAwaitAdvance` hoặc `awaitAdvanceInterruptibly`, hãy bảo đảm participant **đúng phase**; việc gọi đợi với phase đã qua có thể trả về ngay. Không suy rằng một lần `arrive` tương đương chờ các party khác xong.

### Semaphore

Release và acquire của `Semaphore` có các quan hệ memory-consistency phù hợp, nhưng semaphore cũng cần **số permit đúng**. Không dùng `availablePermits()` như một phép quyết định nguyên tử cho việc sử dụng tài nguyên; hãy dùng `acquire`/`tryAcquire` thật.

**Điểm dạy cốt lõi:** chính **giao thức đồng bộ** tạo ra trật tự/visibility, không phải chỉ vì các thread “đợi đủ lâu”. Vì vậy test kiểm tra dữ liệu sau `await/join/get` có cơ sở mạnh hơn test ngủ một lượng thời gian rồi đọc state.

## 11. Thiết kế test để không treo và không làm nhiễm test khác

Một test concurrency nên có **chính sách lỗi và dọn dẹp** rõ ràng:

1. **Timeout cho mọi wait có thể chặn vô hạn:** `latch.await(timeout)`, `barrier.await(timeout)`, `future.get(timeout)`, `executor.awaitTermination(timeout)`, hoặc timed join.
2. **Mở các cổng quan trọng trong `finally`** nếu test thất bại trước khi release. Latch không phải resource được tự dọn; một gate bị quên có thể giữ worker mãi.
3. **Gửi interrupt/cancellation** khi cần, nhưng hiểu rằng đây là yêu cầu hợp tác, không phải kill thread.
4. **Không giữ lock trong khi đợi worker cần chính lock đó.** Một test có thể tự tạo deadlock không liên quan bug mục tiêu.
5. **Không dùng `Thread.sleep` để “đợi đủ lâu”.** Dùng mốc và điều kiện thực.
6. **Gom lỗi worker về test thread.** Đừng chỉ xem log/stack trace rồi cho test pass.
7. **Đặt giới hạn tối đa cho số worker**, đặc biệt khi thử tải; tránh làm cạn CPU/RAM của máy CI.
8. **Khôi phục interrupt status** khi bắt `InterruptedException` và không truyền tiếp, tùy tầng sở hữu lifecycle.
9. **Phân biệt timeout do lỗi thuật toán và timeout do môi trường CI quá tải.** Timeout là failure để điều tra, không phải kết luận ngay nguyên nhân.
10. **Đảm bảo test độc lập:** tạo executor/queue/state mới ở mỗi test, không để static mutable state từ test trước ảnh hưởng test sau.

### Pattern shutdown ExecutorService sau test

```java
// Snippet cho executor do test tạo và sở hữu.
executor.shutdown();
try {
    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Executor did not terminate");
        }
    }
} catch (InterruptedException e) {
    executor.shutdownNow();
    Thread.currentThread().interrupt();
    throw new AssertionError("Test interrupted", e);
}
```

Đây là snippet, `executor` chưa khai báo. Hai lần timeout là **hai giai đoạn chờ riêng**, không phải một deadline tổng 5 giây. Nếu test có deadline tổng, tính `remaining` từ `System.nanoTime()` để không cấp lại toàn bộ ngân sách mỗi lượt.

**Lưu ý:** worker không hợp tác interruption vẫn có thể tồn tại sau khi test báo lỗi. Với các test rủi ro cao (ví dụ deliberately deadlock/loop), nên cô lập test trong một process riêng để harness bên ngoài có thể kill **process** khi quá hạn; không dùng `Thread.stop()`.

## 12. Deterministic test, stress test và kiểm tra Java Memory Model

Không có một bộ synchronizer nào bao phủ **mọi lịch thread**. Cần phối hợp nhiều kỹ thuật:

| Loại test | Ý tưởng | Nên kiểm tra |
| --- | --- | --- |
| **Deterministic scenario** | Latch/barrier tạo ra interleaving đã biết | Một bug cụ thể, invariant tại checkpoint |
| **Repeated concurrency test** | Chạy nhiều lần với số worker và data seed khác nhau | Tính ổn định của invariant trên nhiều lịch |
| **Stress test** | Tăng tranh chấp, tạo nhiều thao tác xen kẽ | Race, lost update, deadlock, tài nguyên bị rò |
| **JMM litmus test** | Tập trung visibility, ordering, atomicity | Các outcome hợp lệ/không hợp lệ theo JMM |
| **Performance benchmark** | Đo throughput/latency với workload đại diện | Hiệu năng, không phải chứng minh absence of data race |

### jcstress và JMH: hai mục tiêu khác nhau

- **jcstress** là công cụ thuộc hệ sinh thái OpenJDK giúp thử nhiều interleaving và outcomes của các phép kiểm tra concurrency/JMM.
- **JMH** phục vụ microbenchmark hiệu năng có kiểm soát. Nó **không phải** công cụ chứng minh thuật toán concurrent luôn đúng.
- Các test phối hợp bằng latch giúp dựng **một số lịch cụ thể**, còn jcstress giúp khảo sát nhiều outcome; chúng bổ trợ nhau.
- Thread Sanitizer/hệ thống phân tích race có thể có mức hỗ trợ/phạm vi khác nhau; không suy rằng không có cảnh báo nghĩa mã an toàn trên mọi JVM.

Nếu bắt buộc chứng minh formal correctness, cần suy luận theo Java Memory Model/happens-before, linearizability hoặc kiểm chứng thuật toán thích hợp; stress test chỉ tăng khả năng phát hiện lỗi, không tạo chứng minh toán học về mọi interleaving.

### Tránh dùng `yield()` như một cơ chế test chắc chắn

`Thread.yield()` là **hint** cho scheduler, không ép thread khác được chạy. `Thread.sleep(1)` cũng không đặt được một thứ tự happens-before mong muốn. Chúng có thể tăng biến thiên lịch trong thử tải, nhưng không nên là assertion hoặc barrier.

## 13. Ví dụ kiểm tra không làm mất dữ liệu bằng BlockingQueue

`BlockingQueue` thường có semantics thread-safe, nhưng test vẫn cần kiểm tra đúng **số item đã gửi và nhận**, tránh dựa vào `queue.isEmpty()` đơn lẻ khi producer còn chạy.

Một pattern tốt:

```text
Producer đưa 100 item và báo done.
Consumer lấy item, ghi nhận ID nhận được.
Main chờ producer done + consumer done.
Main kiểm tra:
  số lượng = 100
  ID không trùng
  mọi ID nằm trong tập kỳ vọng
```

Nếu hệ thống cần đúng-once processing, phải xác định rõ retry, cancellation, failure, ack và thời điểm nào một item được xem “đã xử lý”. `BlockingQueue.take` chỉ bảo đảm lấy phần tử theo hợp đồng queue; nó không tự bảo đảm side effect nghiệp vụ là exactly once.

### Chờ bằng `poll(timeout)`

`queue.poll(timeout, unit)` trả `null` khi không nhận được phần tử trong khoảng chờ. `null` **không phải** bằng chứng producer đã kết thúc vĩnh viễn. Để nhận biết kết thúc stream, dùng một tín hiệu riêng (như latch, completion Future) hoặc một sentinel hợp lệ được đưa vào queue theo giao thức.

Tránh viết `while (!queue.isEmpty())` để quyết định consumer dừng khi producer còn đang hoạt động; khoảng trống tạm thời có thể làm mất cơ hội nhận item tới sau.

## 14. Những lỗi test concurrency phổ biến

1. **“Đã `sleep(100)` thì worker chắc chắn tới dòng mong muốn.”** Sai: scheduling không có cam kết như vậy.
2. **“Tạo nhiều thread là chắc có race.”** Sai: cần kiểm soát contention/interleaving; test một lần pass không chứng minh gì nhiều.
3. **“Một worker ném AssertionError thì test main tự fail.”** Sai: phải chuyển lỗi về main hoặc đọc Future.
4. **“Latch có thể reset cho test round tiếp theo.”** Sai: `CountDownLatch` một lần; dùng `CyclicBarrier` hoặc `Phaser` cho nhiều phase.
5. **“`CountDownLatch.await(timeout)` trả false nghĩa worker đã xong.”** Sai: false báo timeout khi chưa đủ tín hiệu.
6. **“Đặt `done.countDown()` ở cuối try là đủ.”** Không đủ nếu giữa chừng ném lỗi; đặt trong `finally`.
7. **“Barrier một bên hỏng vẫn có thể coi phase đã xong.”** Sai: broken barrier cần làm test fail/cleanup.
8. **“`CyclicBarrier` tự mở khi một thread bỏ cuộc.”** Sai: có thể treo nếu số participant không đủ và không có timeout.
9. **“Phaser.arriveAndAwaitAdvance() là interruptible timed wait.”** Sai: muốn interrupt/timeout, dùng API tương ứng.
10. **“Có permit nghĩa dữ liệu được bảo vệ mọi nơi.”** Sai: chỉ những đường tuân thủ acquire/release mới được giới hạn.
11. **“`availablePermits()` là một quyết định admission atomic.”** Sai: đây là trạng thái quan sát có thể đổi ngay.
12. **“Future.get(timeout) hết hạn thì task đã hủy.”** Sai: timeout chỉ giới hạn chờ caller.
13. **“Cancel(true) chứng minh worker đã dừng và cleanup xong.”** Sai: cần chờ/vòng đời phù hợp.
14. **“Nếu tất cả thread join xong thì shared int++ chắc chắn không mất lượt.”** Sai: join chỉ chờ hoàn tất; phép cập nhật có thể đã race.
15. **“Volatile làm tăng `x++` nguyên tử.”** Sai: volatile chỉ cho semantics của từng truy cập.
16. **“`isEmpty()` của queue sau một thời điểm là đủ xác nhận pipeline xong.”** Sai: producer có thể gửi thêm.
17. **“Dùng thật nhiều delay để giảm flaky test.”** Không bền vững: nên dùng điều kiện/synchronizer thật.
18. **“Stress test pass 1 triệu lượt chứng minh không có data race.”** Sai: không bao quát mọi lịch và mọi JVM/hardware.
19. **“Test phải giả định số CPU luôn bằng số worker.”** Sai: concurrency không đồng nghĩa chạy song song trên mọi core.
20. **“Tạo pool trong test nhưng không shutdown cũng được.”** Có thể để lại thread/tài nguyên, ảnh hưởng những test khác.

## 15. Checklist viết test concurrency đáng tin cậy

1. **Viết rõ invariant**: kết quả nào phải đúng với mọi lịch, kết quả nào chỉ là kỳ vọng của một lịch cụ thể?
2. **Chọn checkpoint có nghĩa** thay cho sleep: ready, start, done, phase, release hoặc future completed.
3. **Kiểm soát số participant**: latch count/barrier parties/phaser registration phải trùng với worker thực tế.
4. **Timeout mọi điểm chờ có nguy cơ treo**; xem đây là tín hiệu test failure cần điều tra.
5. **Chuyển exception từ worker về main** bằng Future hoặc AtomicReference rồi assert.
6. **Đưa countDown/deregister/permit release vào finally** khi đúng hợp đồng sở hữu, tránh thiếu tín hiệu.
7. **Không release semaphore khi chưa acquire**; không double countDown hoặc quên deregister khiến phase sai.
8. **Kiểm tra data invariant sau mốc đồng bộ thích hợp**; tránh đọc state khi writer vẫn đang sửa mà không có giao thức bảo vệ.
9. **Sửa lỗi timing ở kiến trúc test**, không chỉ tăng timeout hoặc thêm sleep.
10. **Đặt cleanup trong finally** và quản lý thread/executor do test tạo; timeout/cancel không phải kill cưỡng chế.
11. **Dựng đường lỗi riêng**: worker ném exception, participant thiếu, barrier broken, interrupt giữa lúc đợi, timeout và quá tải.
12. **Chạy lặp trên nhiều môi trường** nhưng không lấy số lần pass làm bằng chứng hình thức.
13. **Dùng jcstress khi kiểm tra JMM outcome**, JMH khi đo hiệu năng; đừng nhầm mục tiêu.
14. **Không đưa test-only gate làm thay đổi thuật toán production** theo cách che bug; sử dụng hook/injection hợp lý và review tác động.
15. **Ghi rõ test thành công nghĩa gì**: tái hiện lịch cố định, kiểm tra invariant, hay phát hiện race theo xác suất.

## 16. Tham khảo API JDK

- [CountDownLatch — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html)
- [CyclicBarrier — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CyclicBarrier.html)
- [Phaser — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Phaser.html)
- [Semaphore — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html)
- [Future — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [java.util.concurrent package — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html)

**Kết luận:** test concurrency tốt cần biến những mốc vô hình của nhiều thread thành **sự kiện có thể quan sát và phối hợp**. `CountDownLatch` dựng ready/start/done, `CyclicBarrier` chia nhiều vòng, `Phaser` quản lý participant thay đổi, `Semaphore` kiểm tra giới hạn truy cập, còn `Future` đưa kết quả/lỗi về main. Synchronizer giúp tái hiện một số interleaving và kiểm chứng invariant với dữ liệu được công bố đúng cách, nhưng không bao quát mọi lịch: cần kết hợp suy luận JMM, kiểm thử lặp/stress và quản lý timeout, interruption, cleanup để bài test vừa hữu ích vừa không gây treo chương trình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronizers-011">Khi nên dùng BlockingQueue thay vì tự phối hợp luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nên dùng BlockingQueue thay vì tự phối hợp luồng trong Java

## 1. Bài toán: producer–consumer tưởng đơn giản nhưng nhiều hợp đồng ẩn

Một thread tạo dữ liệu, thread khác xử lý dữ liệu. Ví dụ: thread đọc log đẩy các sự kiện vào một hàng đợi; nhiều worker chờ nhận để tính toán hoặc ghi kết quả. Bạn có thể tự viết `synchronized` + `wait()/notifyAll()` hoặc `ReentrantLock` + `Condition`. Nhưng khi làm như vậy, bạn cũng phải tự chịu trách nhiệm về tất cả những vấn đề sau:

- Hàng đợi rỗng thì consumer **chờ** như thế nào mà không đốt CPU?
- Producer thêm phần tử xong phải **đánh thức ai**, tại thời điểm nào?
- Nhiều producers/consumers cùng chạy có thể đánh thức nhầm nhóm thread không?
- Khi queue đầy, producer **đợi, từ chối hay bỏ dữ liệu**?
- Nếu thread bị interrupt trong khi đang chờ, trạng thái dữ liệu và interrupt phải xử lý ra sao?
- Có bị **lost notification**, kiểm tra điều kiện ngoài lock, hoặc dùng `if` thay `while` không?
- Khi shutdown, consumer đang chờ `take()` sẽ được kết thúc như thế nào?
- Tính **FIFO, fairness, ordering hoàn tất** và memory visibility được bảo đảm tới mức nào?

`java.util.concurrent.BlockingQueue<E>` có sẵn giao thức handoff có đồng bộ giữa producers và consumers, cùng các phương thức chặn, không chặn và chờ có thời hạn. Khi bài toán đúng là **truyền các phần tử qua hàng đợi giữa các thread**, việc dùng collection chuyên dụng này thường đơn giản và ít lỗi hơn viết protocol chờ–báo thức thủ công.

Bài học thuộc **Synchronizers** trong **Java Advance - concurrency**, chủ đề trực tiếp **“Khi nên dùng BlockingQueue thay vì tự phối hợp luồng”**. Phạm vi là Java SE 17+ nói chung; không đưa framework/API riêng của Spring vì phần liên quan Spring đã có chương riêng.

### Sau bài học, bạn có thể

1. Mô tả `BlockingQueue` cung cấp những hợp đồng gì: handoff, wait, timeout, boundedness, visibility và ordering.
2. Phân biệt `put/take`, `offer/poll`, các phiên bản timed, `add/remove/element` và giá trị sentinel `null`.
3. Giải thích vì sao tự viết `wait/notifyAll` cần vòng `while`, cùng monitor, state invariant và cleanup.
4. Chọn `ArrayBlockingQueue`, `LinkedBlockingQueue`, `SynchronousQueue`, `PriorityBlockingQueue` hoặc loại queue khác theo mục tiêu.
5. Nhận ra trường hợp **không nên dùng queue**, ví dụ chỉ đợi một sự kiện, cấp số permit, chờ nhiều thread tới barrier hoặc lấy kết quả của một computation.
6. Thiết kế bounded queue và backpressure thay vì dùng hàng đợi vô hạn như nơi chứa mọi backlog.
7. Xử lý interruption, shutdown, poison pill cho nhiều consumer, cancellation, lỗi producer và queue bị đầy.
8. Giải thích sự khác biệt giữa **thứ tự lấy khỏi queue** và **thứ tự hoàn tất công việc**.
9. Chạy năm chương trình Java 17+ độc lập có đầu ra xác định, kiểm chứng được.

## 2. BlockingQueue chính xác là gì?

`BlockingQueue<E>` là interface kế thừa `Queue<E>`. Ngoài thao tác thêm/lấy/xem phần tử, nó có các thao tác cho phép thread **đợi** tới khi trạng thái queue đáp ứng điều kiện.

~~~text
Producer A -----\
                 \                    put / offer
Producer B ------>  [ BlockingQueue ]  --------------+
                 /          |                        |
Producer C -----/      buffer hoặc rendezvous        v
                                             Consumer 1
                                             Consumer 2
                                             Consumer 3
~~~

Queue là **ranh giới trao đổi dữ liệu**, không phải một thread, không tự chạy `Runnable`, không tự tạo worker và không tự quản lý vòng đời nghiệp vụ. Caller vẫn cần `Thread`/`ExecutorService` để chạy producer/consumer.

Có hai điều kiện chờ cơ bản:
- **Queue rỗng:** consumer gọi `take()` hoặc timed `poll(timeout,unit)` có thể phải đợi tới khi có item.
- **Queue đầy (với queue bounded):** producer gọi `put()` hoặc timed `offer(e,timeout,unit)` có thể phải đợi tới khi có chỗ trống.

Những điều kiện chờ này được implementation của `BlockingQueue` đồng bộ và đánh thức theo contract. Bạn không phải tự duy trì biến `notEmpty`, `notFull` hay gọi `notifyAll` trên mỗi đường cập nhật.

### BlockingQueue khác Collection thông thường ở đâu?

`ArrayDeque`, `LinkedList` và `ArrayList` không tự thread-safe để nhiều thread cùng `add/remove`. `Collections.synchronizedList`/`synchronizedCollection` có thể bảo vệ các method riêng lẻ, nhưng **không cung cấp trực tiếp giao thức “chờ có dữ liệu/chờ có chỗ”** qua `put/take`. `ConcurrentLinkedQueue` hỗ trợ concurrent FIFO nonblocking nhưng không có `take()` để block khi rỗng.

`BlockingQueue` cung cấp **cả quyền truy cập đồng thời lẫn giao thức chờ** phù hợp producer–consumer. Đây là lý do chọn queue thay vì chỉ chọn một collection có phương thức thread-safe.

## 3. Bốn nhóm thao tác và cách xử lý khi không thể thực hiện ngay

Hợp đồng `BlockingQueue` có bốn kiểu phản ứng với hai hành động **insert** và **remove**:

| Cách xử lý | Thêm phần tử | Lấy phần tử | Khi không làm ngay được |
| --- | --- | --- | --- |
| Ném exception | `add(e)` | `remove()` | Ném exception khi queue full/empty tương ứng |
| Trả giá trị đặc biệt | `offer(e)` | `poll()` | `false` khi không thêm được, `null` khi rỗng |
| Chờ vô hạn nếu không bị interrupt | `put(e)` | `take()` | Thread có thể chờ tới khi điều kiện thay đổi |
| Chờ có thời hạn | `offer(e,t,u)` | `poll(t,u)` | Trả `false/null` khi hết thời hạn |

Đối với thao tác **đọc đầu queue mà không lấy**:
- `element()` ném `NoSuchElementException` khi rỗng.
- `peek()` trả `null` khi rỗng.

**Lưu ý:** `poll()==null` có ý nghĩa queue hiện không có item, bởi `BlockingQueue` **không cho phép thêm phần tử `null`**. Đừng thiết kế `null` vừa là dữ liệu hợp lệ vừa là tín hiệu kết thúc của chính BlockingQueue.

### Ví dụ chọn method theo chính sách nghiệp vụ

- **Không thể mất dữ liệu, chấp nhận đợi:** `put(event)`, đồng thời phải bảo đảm shutdown/cancel đánh thức producer khi cần.
- **Hệ thống cần reject khi quá tải:** `offer(event)` rồi xử lý `false` bằng log/metric/retry thích hợp.
- **Có deadline chặt:** `offer(event, remaining, unit)` hoặc `poll(remaining, unit)`.
- **Không muốn block thread consumer:** `poll()` và dùng một scheduler/event mechanism khác để quyết định lần kiểm tra tiếp theo.
- **Consumer phải chờ việc mới:** `take()` nếu có giao thức kết thúc rõ ràng.

Không thay `put()` bằng vòng lặp liên tục `while (!queue.offer(x)) {}`. Vòng quay nóng này có thể chiếm CPU, tăng contention và không có chiến lược backpressure hợp lý.

### Interrupt khi đang chờ

`put`, `take` và phiên bản timed có `InterruptedException`. Khi bị interrupt:
- Primitive có thể thoát khỏi chờ bằng exception.
- Bạn phải quyết định propagate exception hoặc restore interrupt status và **ngừng/đổi trạng thái công việc**.
- Việc thoát do interrupt không đồng nghĩa đã hoàn tất lưu dữ liệu hay remote transaction.
- Nếu producer phải bảo đảm không mất item, cần xác định item chưa gửi có thể retry/idempotent hoặc được chuyển sang đường xử lý nào.

Cẩn trọng với việc **nuốt `InterruptedException` rồi tiếp tục vòng lặp vô hạn**: request đã hủy nhưng worker lại tiếp tục chờ như chưa có gì.

## 4. Memory visibility: handoff cũng là một đồng bộ hóa

Theo contract memory consistency của `BlockingQueue`:

> Hành động của một thread trước khi đặt một object vào BlockingQueue happens-before các hành động sau khi thread khác truy cập hoặc lấy object đó từ queue.

Điều này giúp **publish dữ liệu trước handoff** cho consumer:

~~~java
record Message(int id, String payload) {}

Message message = new Message(7, "ready");
queue.put(message);

// Thread consumer:
Message received = queue.take();
use(received.payload());
~~~

Đây là snippet gồm code ở hai threads, với `queue` và `use` cần khai báo. Dữ liệu được tạo trước `put` sẽ được công bố tới thread nhận theo contract, không cần tự gọi `volatile` chỉ để truyền một object immutable qua queue.

**Nhưng** memory visibility không tự làm object mutable an toàn khi nhiều threads còn cùng sửa nó sau khi enqueue:
- Producer đặt một `ArrayList` vào queue rồi tiếp tục thêm phần tử trong khi consumer đang iterate list: vẫn có nguy cơ data race.
- Một object có nhiều field cần invariant chung không trở thành immutable vì được enqueue.
- Dùng immutable message, ownership transfer (producer giao xong không sửa nữa), hoặc locking thích hợp.

BlockingQueue giúp **handoff/visibility**, không thay thế nguyên tắc quản lý shared mutable state.

## 5. Vì sao tự viết wait/notify dễ sai?

Một bounded buffer tự xây cần ít nhất hai predicate:
- `notEmpty`: `size > 0`, cho phép consumer lấy.
- `notFull`: `size < capacity`, cho phép producer thêm.

Các predicate phải được kiểm tra **dưới cùng monitor/lock** được dùng để chờ và đánh thức. Mẫu monitor đúng về cơ bản:

~~~java
synchronized (monitor) {
    while (queue.isEmpty()) {
        monitor.wait();
    }

    E item = queue.removeFirst();
    monitor.notifyAll();
    return item;
}
~~~

Đây là snippet trong method `take`, giả định `monitor`, `queue` được khai báo và method cho phép ném `InterruptedException`. Vì `wait()` nhả monitor và lấy lại monitor trước khi trả, vòng `while` **kiểm tra lại điều kiện sau khi thức dậy**.

### Lỗi 1 — dùng if thay while

Sai:

~~~java
synchronized (monitor) {
    if (queue.isEmpty()) {
        monitor.wait();
    }
    return queue.removeFirst();
}
~~~

Một thread có thể tỉnh vì **spurious wakeup** hoặc vì thread khác đã lấy item trước khi nó giành lại lock. Nếu bỏ qua kiểm tra lại, consumer có thể xóa trên queue rỗng. `while` là yêu cầu correctness, không phải “biện pháp dự phòng hiếm gặp”.

### Lỗi 2 — kiểm tra predicate ngoài lock

Sai:

~~~java
if (queue.isEmpty()) {
    synchronized (monitor) {
        monitor.wait();
    }
}
~~~

Giữa lần kiểm tra `isEmpty` và lần thật sự wait, producer có thể thêm dữ liệu/notify. Consumer sau đó mới wait và có thể **bỏ lỡ tín hiệu**, dù dữ liệu đã có. Tên lỗi thường gọi là **lost notification** hoặc missed signal trong giao thức chờ sai.

### Lỗi 3 — notify một waiter không phù hợp

Khi cùng monitor có cả producers đang chờ `notFull` và consumers đang chờ `notEmpty`, `notify()` đánh thức một waiter bất kỳ; có thể đánh thức một nhóm không tiến triển, nhất là trong thiết kế phức tạp. `notifyAll()` thường đơn giản hơn cho correctness khi chỉ dùng một monitor, đổi lại có thể gây nhiều thread thức dậy và tranh lock.

Nếu dùng `ReentrantLock` và hai `Condition` như `notEmpty` và `notFull`, bạn có thể signal đúng hàng đợi điều kiện hơn, nhưng phải tự xử lý lock, predicate, interrupt và close. **BlockingQueue đã đóng gói phần cơ chế này** trong API.

### Lỗi 4 — quên trạng thái shutdown

Một consumer đang chờ ở `wait()` có thể không bao giờ tỉnh nếu producer đã chết trước khi gửi tín hiệu. `BlockingQueue.take()` cũng **không tự giải quyết vòng đời**, nhưng ít nhất đưa bạn tới giao thức chặn chuẩn với interruption, timed wait và khả năng gửi terminal signal.

### Lỗi 5 — tự viết queue mà quên backpressure

Nếu mọi producer cứ `add` vào một `ArrayDeque` tăng vô hạn, buffer có thể cạn bộ nhớ. Một bounded queue cần xử lý `notFull` và quyết định liệu producer chờ hay drop/reject. `ArrayBlockingQueue(capacity)` đã cung cấp việc quản lý capacity và chờ tương ứng.

**Kết luận phần này:** Bạn có thể tự xây monitor buffer đúng, nhưng để đạt tương đương một BlockingQueue production, phải thiết kế và test nhiều góc cạnh concurrency. Nếu không có invariant rất đặc biệt, ưu tiên collection đã chuẩn hóa.

## 6. Ví dụ 1 — Bounded producer–consumer bằng ArrayBlockingQueue

Tạo file **`BoundedHandoffDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class BoundedHandoffDemo {
    public static void main(String[] args) throws Exception {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<?> producer = pool.submit(() -> {
                try {
                    queue.put(10);
                    queue.put(20);
                    queue.put(30);
                    queue.put(-1); // Sentinel dành cho một consumer.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("producer interrupted", e);
                }
            });

            Future<List<Integer>> consumer = pool.submit(() -> {
                List<Integer> received = new ArrayList<>();
                while (true) {
                    int value = queue.take();
                    if (value == -1) {
                        return received;
                    }
                    received.add(value);
                }
            });

            producer.get();
            List<Integer> result = consumer.get();

            System.out.println("received=" + result);
            System.out.println("remaining=" + queue.size());
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Chạy Java 17+:

~~~bash
javac BoundedHandoffDemo.java
java BoundedHandoffDemo
~~~

Stdout khi không có interruption/lỗi bên ngoài:

~~~text
received=[10, 20, 30]
remaining=0
~~~

**Giải thích từng điểm:**
1. `ArrayBlockingQueue<>(2)` cho tối đa hai phần tử đang nằm trong buffer; đây là giới hạn backlog tại ranh giới queue.
2. Producer đưa `10,20,30,-1` theo thứ tự. Khi buffer đầy, `put` có thể chờ cho tới lúc consumer lấy bớt.
3. Consumer dùng `take()` để chờ item khi rỗng, không tự gọi `wait`, không cần busy polling.
4. Có **một producer và một consumer**, nên thứ tự nhận là FIFO `10,20,30`. `-1` được dành riêng để kết thúc.
5. Main gọi `Future.get()`, đảm bảo producer và consumer hoàn tất thành công trước khi đọc result và size.
6. Chương trình không dự đoán thread nào chạy trước hoặc có bao nhiêu lần producer thật sự phải chờ; điều đó phụ thuộc lịch chạy.

**Production caveat:** Nếu producer bị lỗi trước khi gửi sentinel, consumer có thể đợi mãi. `Future.get()` cũng có thể đợi vô hạn. Cần thiết kế deadline, cancellation, close protocol hoặc coordinator phát terminal signal khi producer thất bại; **BlockingQueue không có method close() chung trong interface**.

### Vì sao không dùng LinkedList + synchronized cho cùng bài toán?

Có thể làm vậy, nhưng bạn phải viết thêm:
- Kiểm tra buffer rỗng/đầy trong vòng while.
- Nhả lock khi chờ rồi lấy lại lock.
- Signal producer khi consumer lấy item và ngược lại.
- Xử lý interruption, shutdown, capacity và ordering.
- Kiểm thử race ở những thời điểm mất notify.

BlockingQueue cho đúng abstraction: **dữ liệu qua một hàng đợi có thể chặn**.

## 7. Ví dụ 2 — Chọn offer/poll và timed methods theo backpressure

Không phải ứng dụng nào cũng chấp nhận chờ vô thời hạn. Với UI request, message ingestion hoặc API có SLO, bạn có thể dùng `offer`/`poll`.

Tạo file **`TimedQueuePolicyDemo.java`**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TimedQueuePolicyDemo {
    public static void main(String[] args) throws Exception {
        ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);

        boolean accepted1 = queue.offer(1);
        boolean accepted2 = queue.offer(2);
        boolean accepted3 = queue.offer(3);

        boolean timedAccepted = queue.offer(
                4, 20, TimeUnit.MILLISECONDS);

        System.out.println("accepted=" +
                accepted1 + "," + accepted2 + "," + accepted3);
        System.out.println("timedAccepted=" + timedAccepted);
        System.out.println("poll1=" + queue.poll());
        System.out.println("poll2=" + queue.poll());
        System.out.println("pollEmpty=" + queue.poll());
        System.out.println("timedPollEmpty=" +
                queue.poll(20, TimeUnit.MILLISECONDS));
    }
}
~~~

Chạy:

~~~bash
javac TimedQueuePolicyDemo.java
java TimedQueuePolicyDemo
~~~

Stdout không có interruption bên ngoài:

~~~text
accepted=true,true,false
timedAccepted=false
poll1=1
poll2=2
pollEmpty=null
timedPollEmpty=null
~~~

Ở đây không có consumer khác, nên buffer capacity 2 chắc chắn full khi gọi `offer(4,20ms)`. Timed offer hết hạn rồi trả false. Hai lần `poll` lấy 1 rồi 2 theo FIFO; lần sau queue rỗng, cả poll thường và timed poll đều trả null.

**Chính sách ứng dụng phải quyết định khi `offer` trả false:**
- Trả 429/503 hoặc một loại “busy” phù hợp ở tầng API.
- Ghi metric rejected.
- Retry với exponential backoff và deadline tổng.
- Chuyển sang storage bền vững nếu nghiệp vụ không được phép mất sự kiện.
- Drop/coalesce nếu dữ liệu có thể bỏ (ví dụ sensor sampling).

Bản thân `offer(false)` không tự quyết định business semantics. Không retry mãi trong một thread pool nhỏ mà không có timeout — nó có thể làm hệ thống kiệt worker.

## 8. Chọn implementation BlockingQueue phù hợp

| Implementation | Thứ tự chính | Capacity | Blocking semantics | Nên cân nhắc khi |
| --- | --- | --- | --- | --- |
| `ArrayBlockingQueue<E>` | FIFO | **Cố định, bounded** | `put/take`, timed `offer/poll` | Pipeline có giới hạn backlog rõ |
| `LinkedBlockingQueue<E>` | FIFO | Có thể bounded khi truyền capacity; mặc định rất lớn | `put/take`, timed API | Producer–consumer FIFO với cấu trúc linked |
| `SynchronousQueue<E>` | Handoff trực tiếp | **0 item buffered** | Producer/consumer đợi partner | Rendezvous, giao việc trực tiếp |
| `LinkedBlockingDeque<E>` | Hai đầu, FIFO nếu dùng last/first | Capacity tùy chọn | Blocking ở hai đầu | Work deque, yêu cầu push/pop riêng |
| `PriorityBlockingQueue<E>` | Priority/comparator | **Không bounded theo constructor** | `take` khi rỗng; put không chờ vì capacity logic | Priority scheduler không yêu cầu FIFO |
| `DelayQueue<E extends Delayed>` | Theo delay expiry | Không bounded | `take` đợi item đến hạn | Xử lý sự kiện sau độ trễ |
| `LinkedTransferQueue<E>` | FIFO, có transfer semantics | Unbounded về cấu trúc | `transfer` có thể chờ consumer nhận | Handoff với lựa chọn transfer trực tiếp |

### ArrayBlockingQueue: baseline rõ cho bounded backlog

- Backing array cố định theo capacity.
- FIFO cho các phần tử đã enqueue theo thứ tự xác định.
- Tùy chọn `fair=true` để cân nhắc fairness giữa threads chờ truy cập; fairness **không** có nghĩa xử lý hoàn tất FIFO hoặc priority.
- Boundedness giúp giới hạn số item đang đợi, nhưng không thay thế chính sách upstream khi queue đầy.

Thường là điểm xuất phát dễ lý luận cho **producer–consumer có backpressure**.

### LinkedBlockingQueue: đừng nhầm mặc định là “vừa đủ”

`new LinkedBlockingQueue<>()` có capacity mặc định `Integer.MAX_VALUE`; nó không chờ producer do một ngưỡng nhỏ như 1.000 trừ khi bạn cấu hình:

~~~java
BlockingQueue<Event> queue = new LinkedBlockingQueue<>(1_000);
~~~

Đây là snippet giả định class `Event` đã tồn tại. Một backlog mặc định rất lớn có thể làm cạn memory nếu tốc độ producer vượt consumer kéo dài. “Unbounded về contract thực tế” không có nghĩa máy có bộ nhớ vô hạn.

### PriorityBlockingQueue: không phải FIFO

`PriorityBlockingQueue` trả phần tử theo comparator/natural order; nếu nhiều phần tử có priority bằng nhau, **không bảo đảm FIFO ổn định**. Nếu cần tie-break, thêm sequence vào comparator. Không dùng `iterator()` để giả định thứ tự priority: iterator không phải ordered drain.

Nó có thể block consumer lúc rỗng, nhưng producer `put` không tạo backpressure bounded kiểu `ArrayBlockingQueue`. Nếu cần vừa priority vừa bounded backlog, phải thiết kế admission control khác; **không thể đổi constructor capacity ban đầu rồi coi đó là hard limit**.

### DelayQueue: chờ đến khi item đến hạn

`DelayQueue` là BlockingQueue đặc biệt: phần tử phải implement `Delayed`; consumer `take` chỉ trả item khi delay đã hết. Dù queue có item vật lý, `poll()` có thể trả null khi chưa item nào hết hạn. Không phù hợp thay một FIFO work queue nếu bạn cần xử lý ngay.

### Deque: hành động nào quyết định ordering

`LinkedBlockingDeque` có thể lấy/thêm ở cả đầu và cuối. Nếu dùng `putLast` + `takeFirst` thì theo FIFO; nếu dùng `putFirst` + `takeFirst` thì theo LIFO cho các phần tử tương ứng. “Có ordering” chưa đủ: phải chỉ rõ **thao tác ở đầu nào**.

## 9. Ví dụ 3 — SynchronousQueue: trao đổi trực tiếp không buffer

`SynchronousQueue` có capacity bằng 0: một lần `put(x)` cần một consumer tương ứng nhận; nó không chứa item trong buffer. Vì vậy:
- `offer(x)` không chờ sẽ trả false nếu chưa có consumer chờ phù hợp.
- `put(x)` có thể block tới khi một `take()` nhận.
- `remainingCapacity()` luôn 0.
- Nó hữu ích khi muốn ràng buộc tốc độ producer vào consumer nhưng **không** tự tạo retry/durable storage.

Tạo file **`SynchronousHandoffDemo.java`**:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

public class SynchronousHandoffDemo {
    public static void main(String[] args) throws Exception {
        SynchronousQueue<String> queue = new SynchronousQueue<>();

        boolean offeredWithoutReceiver = queue.offer("early");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> sender = pool.submit(() -> {
                try {
                    queue.put("payload");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("sender interrupted", e);
                }
            });

            Future<String> receiver = pool.submit(queue::take);

            sender.get();
            String result = receiver.get();

            System.out.println("immediateOffer=" + offeredWithoutReceiver);
            System.out.println("received=" + result);
            System.out.println("remainingCapacity=" +
                    queue.remainingCapacity());
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Chạy:

~~~bash
javac SynchronousHandoffDemo.java
java SynchronousHandoffDemo
~~~

Stdout khi không có interruption/lỗi bên ngoài:

~~~text
immediateOffer=false
received=payload
remainingCapacity=0
~~~

**Vì sao tất định?** Lần `offer("early")` chạy trong main trước khi tạo task consumer nên chưa có thread đợi nhận, kết quả false. Sau khi hai tasks được submit, sender và receiver rendezvous qua `put/take`, dù thứ tự worker start khác nhau. `Future.get()` đợi cả hai hoàn tất; không có item lưu trữ trong queue.

**Liveness:** Một `SynchronousQueue.put()` có thể chờ mãi nếu không có consumer, và `take()` có thể chờ mãi nếu không có producer. Với thread pool chỉ có **một worker**, nếu producer chiếm worker và block ở put còn consumer cũng submit vào đúng pool ấy, có thể bị thread-starvation deadlock. Đừng coi handoff trực tiếp là giải pháp thần kỳ không cần đủ worker hoặc giao thức shutdown.

## 10. Lifecycle và shutdown: BlockingQueue không có close() chung

Một điểm dễ bỏ sót khi chuyển từ `wait/notify` sang queue: bạn không phải tự viết chờ rỗng/đầy, **nhưng vẫn phải thiết kế “không còn dữ liệu nữa”**.

`BlockingQueue` không định nghĩa một method `close()` để tự động làm mọi consumer đang chờ `take()` thoát. Nếu producer đã xong nhưng không thông báo, consumer vẫn có thể chờ vô hạn.

Các chiến lược phổ biến:
1. **Poison pill/sentinel:** gửi một giá trị đặc biệt cho mỗi consumer.
2. **Interruption:** coordinator giữ worker `Future` hoặc thread handles, gửi cancel/interrupt theo contract.
3. **Timed poll + closed flag:** consumer định kỳ kiểm tra `closed` (và drain các item còn lại), tránh chờ vô hạn.
4. **Wrapper abstraction có close:** tự định nghĩa trạng thái `closed`, giao thức đánh thức và bảo vệ atomicity của close so với enqueue.
5. **Executor lifecycle:** nếu không cần tự lập trình event-loop, có thể dùng `ExecutorService.submit` và `shutdown/awaitTermination` để quản lý task queue và worker ở tầng cao.

**Poison pill chỉ là một item**, không tự nhân bản cho N consumers. Nếu một consumer ăn pill rồi thoát, N-1 consumers khác vẫn có thể đang đợi.

### Ví dụ 4 — Kết thúc chính xác ba consumers bằng ba sentinels

Tạo file **`MultiConsumerStopDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiConsumerStopDemo {
    public static void main(String[] args) throws Exception {
        int consumerCount = 3;
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(4);
        ExecutorService pool = Executors.newFixedThreadPool(4);

        AtomicInteger processed = new AtomicInteger();
        AtomicInteger total = new AtomicInteger();

        try {
            List<Future<?>> consumers = new ArrayList<>();

            for (int i = 0; i < consumerCount; i++) {
                consumers.add(pool.submit(() -> {
                    try {
                        while (true) {
                            int value = queue.take();
                            if (value == -1) {
                                return;
                            }
                            processed.incrementAndGet();
                            total.addAndGet(value);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("consumer interrupted", e);
                    }
                }));
            }

            Future<?> producer = pool.submit(() -> {
                try {
                    for (int i = 1; i <= 9; i++) {
                        queue.put(i);
                    }
                    for (int i = 0; i < consumerCount; i++) {
                        queue.put(-1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("producer interrupted", e);
                }
            });

            producer.get();
            for (Future<?> consumer : consumers) {
                consumer.get();
            }

            System.out.println("processed=" + processed.get());
            System.out.println("sum=" + total.get());
            System.out.println("remaining=" + queue.size());
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Chạy:

~~~bash
javac MultiConsumerStopDemo.java
java MultiConsumerStopDemo
~~~

Stdout khi không bị interruption/lỗi ngoài ý muốn:

~~~text
processed=9
sum=45
remaining=0
~~~

**Lập luận:**
- Producer gửi 9 item thật `1..9`, sau đó **đúng ba sentinel** cho ba consumer.
- Queue FIFO và producer duy nhất bảo đảm các pills được enqueue **sau** các item thật.
- Ba consumers mỗi consumer thoát ngay khi nhận một pill, không có consumer “ăn hai pill” vì đã return sau pill đầu.
- Mọi item thật được một consumer lấy một lần; `AtomicInteger` bảo vệ tổng và số item processed khi các consumer ghi đồng thời.
- Main đợi producer và tất cả consumers qua `Future.get()` rồi mới đọc kết quả.
- `1+2+...+9=45`, nên output không phụ thuộc consumer nào lấy phần tử nào.
- Pool có bốn worker: ba consumer có thể chờ và producer vẫn có worker để gửi dữ liệu. Nếu dùng pool chỉ một worker và submit consumers trước, producer có thể không bao giờ có cơ hội chạy.

### Những điều poison pill không giải quyết

- Nếu producer bị lỗi **trước khi enqueue pills**, consumers có thể treo. Coordinator phải có đường hủy/wakeup độc lập.
- Với nhiều producer, **chỉ producer cuối cùng hoặc coordinator biết mọi producer đã kết thúc** mới có thể gửi terminal signal sau toàn bộ work; nếu pill đến quá sớm, consumer có thể bỏ lại work của producer khác.
- Nếu dữ liệu hợp lệ có thể bằng `-1`, sentinel int trùng dữ liệu là lỗi thiết kế. Dùng một marker/type phân biệt (sealed hierarchy, enum/record wrapper).
- Một queue bị full có thể khiến producer **block cả khi đang enqueue pills**. Do đó cần deadline/cancellation khi shutdown.
- Nếu một consumer đã chết vì lỗi trước khi nhận pill, pill có thể ở lại hoặc khiến các consumers còn lại xử lý sai termination count.
- Queue đảm bảo dequeue item một lần trong các thao tác bình thường, **không** đảm bảo business processing exactly-once nếu worker chết sau take mà trước commit. Cần ack/retry/durable queue và idempotency khi yêu cầu.

## 11. Ví dụ 5 — Tự phối hợp bằng monitor: có thể đúng nhưng phức tạp hơn

Để so sánh công bằng, sau đây là một **monitor bounded buffer đúng cho happy path**, không cố tình viết sai. Nó dùng `ArrayDeque`, `synchronized`, `while`, `wait()` và `notifyAll()`. Như vậy ta thấy chính xác phần code mà BlockingQueue giúp ẩn đi.

Tạo file **`ManualMonitorBufferDemo.java`**:

~~~java
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class ManualMonitorBufferDemo {
    static final class BoundedBuffer {
        private final ArrayDeque<Integer> items = new ArrayDeque<>();
        private final int capacity;

        BoundedBuffer(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity <= 0");
            }
            this.capacity = capacity;
        }

        synchronized void put(int value) throws InterruptedException {
            while (items.size() == capacity) {
                wait();
            }
            items.addLast(value);
            notifyAll();
        }

        synchronized int take() throws InterruptedException {
            while (items.isEmpty()) {
                wait();
            }
            int value = items.removeFirst();
            notifyAll();
            return value;
        }

        synchronized int size() {
            return items.size();
        }
    }

    public static void main(String[] args) throws Exception {
        BoundedBuffer buffer = new BoundedBuffer(2);
        AtomicInteger sum = new AtomicInteger();

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    buffer.put(i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    sum.addAndGet(buffer.take());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        System.out.println("sum=" + sum.get());
        System.out.println("remaining=" + buffer.size());
    }
}
~~~

Chạy:

~~~bash
javac ManualMonitorBufferDemo.java
java ManualMonitorBufferDemo
~~~

Stdout trên đường không bị interruption ngoài ý muốn:

~~~text
sum=15
remaining=0
~~~

**Tại sao nó đúng trong demo?**
- `put`/`take` synchronized trên cùng instance `BoundedBuffer`, nên `wait`/`notifyAll` sử dụng cùng monitor.
- Predicate `size==capacity` và `isEmpty` được đọc/ghi dưới lock, không có lost-notification race giữa check và wait.
- `wait()` nhả monitor khi chờ; lúc tỉnh lấy lại monitor rồi **kiểm tra lại bằng while**.
- `notifyAll()` đánh thức waiters để họ tự xác minh predicate, không dự đoán ai tỉnh trước.
- Buffer FIFO bằng `addLast` + `removeFirst`, capacity 2; 5 phần tử `1..5` cho tổng 15.
- Main `join` cả hai threads trước khi đọc biến cộng và buffer size.

**Vẫn chỉ là một ví dụ tối giản**: chưa có `offer` không chặn, `poll(timeout)`, `put(timeout)`, close, poison-pill protocol nhiều consumers, chính sách xử lý producer failure, fairness, structured diagnostics hoặc saturation management.

### Khi nào tự viết Condition/monitor vẫn hợp lý?

Có, nếu đồng bộ hóa gắn với **một invariant riêng không thể diễn đạt bằng queue**. Ví dụ:
- Một máy trạng thái cần đợi nhiều điều kiện liên quan `state == RUNNING && count >= N`.
- Cần atomic transition nhiều field dưới cùng lock.
- Cần đợi chính trạng thái thay đổi, **không phải một item được truyền**.
- Cần tín hiệu phát tới mọi listener mà không tiêu thụ từng message (broadcast).

Trong các trường hợp đó `ReentrantLock + Condition` hoặc `synchronized + wait/notifyAll` có thể là abstraction đúng. Nhưng nếu chỉ đang lặp lại buffer producer–consumer thông thường, hãy ưu tiên BlockingQueue chuẩn.

## 12. BlockingQueue hay synchronizer khác? Chọn theo đối tượng chờ

| Bài toán | Abstraction nên xem trước | Vì sao |
| --- | --- | --- |
| Chuyển các item từ producers sang consumers; consumer chờ khi rỗng | **`BlockingQueue`** | Dữ liệu và cơ chế wait cùng một abstraction |
| Giới hạn buffer và tạo backpressure cho producers | **Bounded BlockingQueue** | Có capacity + `put/offer(timeout)` |
| Một thread đợi sự kiện xảy ra đúng một lần | `CountDownLatch` | Chờ signal, không cần một item bị lấy khỏi queue |
| Một nhóm thread cùng chờ để qua nhiều pha | `CyclicBarrier` / `Phaser` | Phối hợp phase giữa nhiều participants |
| Giới hạn số công việc truy cập tài nguyên chung | `Semaphore` | Quản lý số permit, không phải FIFO job queue |
| Một thread đợi một kết quả tính toán | `Future` / `CompletableFuture` | Kết quả và failure/cancellation của computation |
| Một lock phối hợp condition tùy biến | `ReentrantLock + Condition` | Bảo vệ invariant state riêng |
| Muốn chạy nhiều `Runnable/Callable` bằng worker pool | `ExecutorService` | Quản lý scheduling, worker, Future và lifecycle |
| Event broadcast cho nhiều subscribers | Mô hình pub/sub/callback phù hợp | Queue dùng nhiều consumers thường chia item, không broadcast |
| Muốn luôn nhận **giá trị mới nhất** thay vì backlog | `AtomicReference`/`volatile` + signal | Queue giữ các item cũ, không tự coalesce |
| Cần xử lý message bền vững sau restart | Persistent broker/storage protocol | BlockingQueue thuần in-memory, không durable |

### Queue không thay semaphore

`Semaphore(3)` cho phép tối đa ba threads đồng thời giữ permit, bất kể “item công việc” có hay không. `ArrayBlockingQueue(3)` giới hạn **ba item trong buffer**, chứ **không** giới hạn ba task đang xử lý sau khi chúng đã `take` item ra khỏi queue. Nếu cần giới hạn số HTTP requests đang in-flight, có thể cần semaphore (hoặc bounded executor/admission control) chứ không phải chỉ một queue.

### Queue không thay CountDownLatch

`CountDownLatch.await()` đợi một tín hiệu **không tiêu thụ** cho tất cả waiters. Nếu thay bằng một queue chứa đúng một token, consumer đầu tiên `take` sẽ lấy mất token; các waiters còn lại không tự vượt qua. Điều này làm sai semantics broadcast/one-time release.

### Queue không thay Future

Một `Future<T>` đại diện một computation và cung cấp outcome với `get()`, `ExecutionException`, `CancellationException` và completion semantics. Một BlockingQueue có thể truyền một `Result` được wrapper tự thiết kế, nhưng phải tự làm failure/cancel/correlation. Nếu chỉ muốn chạy callable rồi lấy kết quả, `executor.submit(callable)` thường đơn giản hơn tự lập trình request/result queues.

## 13. Ordering: FIFO dequeue không bảo đảm FIFO completion

Giả sử producer enqueue A rồi B vào FIFO queue. Hai consumers có thể:
- Consumer 1 take A, xử lý 500 ms.
- Consumer 2 take B, xử lý 10 ms.
- B được commit trước A.

~~~text
FIFO enqueue/dequeue:   A -> B
Processing workers:     A ---------finish
                         B -finish
Commit order:           B -> A   (có thể)
~~~

Nếu business cần output đúng thứ tự, hãy thiết kế **sequence number, reorder buffer, serial commit**, hoặc một consumer xử lý tuần tự. Queue không tự đồng bộ “task A hoàn tất trước task B”.

Với nhiều producers thực hiện `put` đồng thời, thứ tự enqueue hiệu lực do scheduling và synchronization thực tế quyết định. Nó không tự là thứ tự “request xuất hiện trong logs” nếu các timestamps/logs được tạo ngoài điểm enqueue.

Với `PriorityBlockingQueue`, dequeue order dựa trên comparator, không phải FIFO. Với `SynchronousQueue`, không có buffer; một lần put được match với take tương ứng. Với `DelayQueue`, item chỉ sẵn sàng khi delay hết hạn.

**Đừng dùng `size()` hoặc `isEmpty()` như một lock:**

~~~java
if (!queue.isEmpty()) {
    E item = queue.remove();
}
~~~

Đây là snippet rủi ro khi nhiều consumers: giữa `isEmpty` và `remove`, thread khác có thể lấy phần tử cuối. Nếu chỉ muốn claim item khi có, dùng `poll()`; nếu muốn đợi, dùng `take()`.

## 14. Boundedness, backpressure và giới hạn bộ nhớ

Một câu hỏi quan trọng hơn “queue nào nhanh nhất” là **“điều gì xảy ra khi producer nhanh hơn consumer 10 phút?”**

Nếu mỗi giây nhận 10.000 items nhưng consumer xử lý 8.000 items, backlog tăng khoảng 2.000 items/s. Sau 10 phút là khoảng 1,2 triệu items, chưa tính bộ nhớ cho payload, node, object graph, metrics. Queue không bounded có thể tiêu thụ rất nhiều RAM trước khi bị lỗi.

Với bounded queue capacity `C`:
- Chỉ tối đa C items đang đợi trong queue (không bao gồm những items consumers đã take và đang xử lý).
- `put` có thể block producer lúc full.
- `offer` có thể reject ngay.
- `offer(timeout)` cho phép chờ có giới hạn.
- Caller có thể dùng queue depth, rejected count, producer wait time để điều tiết.

**Không đồng nhất capacity với toàn bộ in-flight requests.** Nếu có 100 consumers đang xử lý và queue capacity 100, có thể có tới khoảng 200 items vừa đang xử lý vừa đang đợi (chưa tính những request chưa vào queue và các stage khác).

### Backpressure nên lan ngược lên upstream

Một bounded queue chỉ tạo ra áp lực tại nơi producer gọi `put/offer`. Nếu upstream chấp nhận request vô hạn rồi tạo một task chờ đưa vào queue cho mỗi request, backlog vẫn tồn tại **ở thread/task layer thay vì trong queue**. Cần admission control tại đúng ranh giới, timeout và bounded executor hoặc cơ chế điều tiết đầu vào.

### Các loại lỗi và quyết định phải rõ

- **Full queue:** reject, retry, shed load, hoặc backpressure.
- **Empty queue:** wait, timed poll hoặc idle scheduler.
- **Shutdown:** không nhận item mới, drain hay discard theo policy.
- **Producer failure:** làm sao consumer nhận được trạng thái kết thúc?
- **Consumer failure:** item đã take nhưng chưa xử lý xong được retry không?
- **Timeout/cancellation:** caller không còn cần kết quả; có cần gỡ item khỏi queue không?
- **Duplicate:** retry có thể khiến cùng business item xuất hiện nhiều lần.
- **Poison-pill admission:** queue đầy có thể block lúc gửi terminal messages.
- **Durability:** in-memory queue mất dữ liệu khi process chết.

Nếu nghiệp vụ cần **at-least-once delivery, retry và durable acknowledgement**, BlockingQueue thuần không phải message broker có persistent log. Một worker `take` item rồi crash có thể làm item ấy mất khỏi queue dù chưa commit.

## 15. Shutdown an toàn cần giao thức ngoài queue

Với pipeline nhiều producer/consumer, hãy xem shutdown như một **state machine**:

~~~text
RUNNING
   |
   | stop accepting new items
   v
CLOSING
   |
   | producers finish / cancel
   | drain outstanding items nếu policy yêu cầu
   | notify consumers qua sentinel/close/interrupt
   v
STOPPED
~~~

Một chính sách graceful-drain thường thực hiện:
1. Không nhận thêm producers mới.
2. Đợi hoặc hủy producers đang chạy trong deadline.
3. Đảm bảo không có item mới được enqueue **sau** terminal markers.
4. Đưa terminal signal cho đúng số consumers hoặc dùng close protocol.
5. Đợi consumers thực sự thoát và cleanup, có deadline cho lỗi/treo.
6. Shutdown executor đúng ownership.

Không tùy tiện `shutdownNow()` ngay khi queue hết item nếu consumer vẫn phải commit công việc đã take. `ExecutorService.shutdown()` ngừng nhận task mới nhưng cho task đã submit chạy tiếp; `shutdownNow()` chỉ best-effort interrupt. Cả hai không tự tạo “closed state” cho `BlockingQueue` mà code của bạn giữ.

### Poison pill không nên bị nhầm với exception

Một pill là **dữ liệu theo một quy ước đặc biệt**. Nếu bạn cần phân biệt hết nguồn thành công, lỗi, cancel và retryable failure, hãy định nghĩa wrapper như:

~~~java
sealed interface Event permits Data, End, Failure {}
record Data(int id) implements Event {}
record End() implements Event {}
record Failure(String reason) implements Event {}
~~~

Đây là snippet định nghĩa sealed hierarchy Java 17; không phải file độc lập. Caller quyết định `End` có nghĩa graceful stop còn `Failure` có nghĩa ngừng với lỗi. Nếu nhiều consumers dùng một queue, nên thiết kế riêng routing/broadcast của terminal messages, không mặc định một marker cho cả nhóm.

## 16. Lưu ý về cancellation và interrupts

Khi một thread đang ở `take/put`, `Thread.interrupt()` có thể khiến blocking operation ném `InterruptedException`. Nhưng:
- Nếu task bắt exception rồi **tiếp tục loop vô điều kiện**, cancellation có thể bị vô hiệu.
- `Thread.interrupted()` (static) đọc và **xóa** interrupt status của thread hiện tại; `isInterrupted()` chỉ đọc.
- Nếu method có thể khai báo `throws InterruptedException`, thường propagate để coordinator quyết định.
- Nếu không thể propagate, restore flag bằng `Thread.currentThread().interrupt()` và **thoát theo policy**.
- `Future.cancel(true)` yêu cầu interrupt runner ở những implementation tương ứng, nhưng không bảo đảm mọi I/O/CPU loop dừng tức thì.
- Một task đã take item rồi bị hủy cần xác định **ownership của item**: retry, chuyển trả queue hay ghi failure. Tự `queue.offer(item)` trong finally có thể fail nếu queue full và cũng có nguy cơ duplicate nếu commit đã thành công.

Không dùng `Thread.stop()` để hủy cưỡng bức. Hủy an toàn phải giữ invariants và resource cleanup.

### Timeout khi đợi không đồng nghĩa task đã bị hủy

`queue.poll(timeout)` trả null khi hết thời gian chờ item, nhưng thread có thể tiếp tục xử lý việc khác. `future.get(timeout)` timeout chỉ giới hạn caller chờ một computation. Cần xác định **deadline toàn pipeline**, không tái cấp thời gian chờ ở mỗi stage và không suy remote side effect được rollback.

### Nếu một consumer đã nhận item nhưng xử lý thất bại?

Queue đã remove item ở thời điểm `take()` thành công. Không có `ack/nack` tích hợp trong interface `BlockingQueue`. Nếu cần retry/at-least-once:
- Gói item vào trạng thái task có ID.
- Có một owner/coordinator theo dõi in-flight.
- Định nghĩa retry policy và chống duplicate bằng idempotency.
- Với durability vượt process lifetime, dùng persistent storage/broker/giao thức phù hợp.

Một queue trong RAM chỉ là synchronizer/data structure; không tự giải quyết transaction delivery semantics.

## 17. Vì sao không dùng một queue cho mọi dạng tín hiệu?

Sự tiện lợi của `take` có thể khiến lập trình viên dùng queue như một “đường hầm truyền mọi trạng thái”. Điều này làm coupling và deadlock khó chẩn đoán.

**Trường hợp một sự kiện đã xảy ra:** dùng `CountDownLatch` để nhiều thread cùng vượt qua khi count về 0. Queue chỉ chuyển một token cho một taker.

**Trường hợp cần một hàng đợi công việc:** dùng `BlockingQueue` hoặc submit tasks cho `ExecutorService`. Nếu bạn đang tự tạo N worker threads, tự dequeue `Runnable` và tự quản lý shutdown, có thể bạn đang viết lại một executor.

**Trường hợp giới hạn truy cập DB connection:** dùng `Semaphore` hoặc connection pool có contract rõ. Queue capacity hạn chế item chờ, không tự giới hạn số consumer đang giữ DB connection.

**Trường hợp nhiều điều kiện state phức tạp:** dùng `ReentrantLock + Condition` để giữ invariant trong một critical section; queue chỉ biểu diễn một loại dữ liệu/ordering, không transaction nhiều field.

**Trường hợp broadcast:** queue multi-consumer thông thường **phân phối** mỗi item cho một consumer, không broadcast mỗi item cho tất cả; cần pub/sub với nhiều per-subscriber queues hoặc abstraction phù hợp.

**Trường hợp “luôn lấy giá trị mới nhất”:** một `AtomicReference` có publication và notification phù hợp có thể tốt hơn queue tích lũy giá trị cũ. Nếu cần coalescing, đừng để backlog chứa mọi phiên bản vô ích.

## 18. Kiểm thử producer–consumer không bị flaky

Đừng chỉ dùng `Thread.sleep(100)` để “đợi consumer bắt đầu”. Scheduler không bảo đảm thread đã tới vị trí mong muốn sau thời gian đó. Test nên dùng:
- `CountDownLatch` xác nhận producer/consumer đã vào một đoạn cụ thể.
- `Future.get(timeout)` để test có thời hạn thay vì treo toàn test suite.
- Bounded queue nhỏ (capacity 1 hoặc 2) để ép đường full/empty.
- `AtomicInteger` hoặc result reduction khi cần đếm multi-consumer.
- Assertion trên **tập item và tổng số lần nhận**, không đoán thread nào xử lý item nào.
- Test interrupt khi consumer đang `take` và producer đang `put`.
- Test producer thất bại trước terminal signal.
- Test multiple consumers và đúng số pills hoặc close protocol.
- Test shutdown khi queue đang full.
- Test producer thêm sau khi close hoặc một pill được enqueue quá sớm.
- Test cancellation sau khi item đã take nhưng trước commit.
- Test không có leak permits/locks, không còn blocked worker quá deadline.

### Những kịch bản bắt buộc

| Kịch bản | Expected behavior |
| --- | --- |
| Producer nhanh, consumer chậm, queue bounded | `put` có thể đợi; không vượt capacity |
| Queue rỗng, consumer dùng `take` | Consumer chờ, không busy spin |
| Queue full, producer dùng `offer` | Trả false, caller xử lý reject |
| Queue full, timed offer | Đợi tối đa thời hạn theo contract, trả false nếu không có chỗ |
| Có N consumers, chỉ 1 poison pill | Không bảo đảm cả N consumers thoát |
| Producer chết trước pill | Cần coordinator cancellation/close để tránh treo |
| Multi-consumer và FIFO queue | Dequeue theo queue order, completion order có thể khác |
| Producer handoff mutable object rồi sửa tiếp | Cần ownership/synchronization |
| Một consumer chết sau take | Item có thể mất nếu không có retry/ack ngoài queue |
| Shutdown executor khi workers vẫn chờ | Phải có protocol xử lý interruption và cleanup |

### Performance test đúng trọng tâm

So sánh `ArrayBlockingQueue`, `LinkedBlockingQueue(capacity)`, `SynchronousQueue` hoặc nonblocking queue **trên cùng workload thực**:
- Tốc độ producer/consumer, burst size và số cores.
- Throughput (items/s), p95/p99 queue wait và end-to-end latency.
- CPU utilization khi idle: busy-spin vs blocking.
- Allocation/GC và memory theo backlog.
- Count rejected/timed-out/cancelled.
- Time to graceful shutdown.
- Fairness/starvation nếu nhiều producers/consumers.

Không kết luận “ArrayBlockingQueue luôn nhanh nhất” hoặc “LinkedBlockingQueue luôn nhanh hơn” từ một thử nghiệm nhỏ. Dùng JMH hoặc benchmark harness thích hợp; luôn kiểm tra correctness và làm rõ môi trường JVM/hardware.

## 19. Những lỗi phổ biến khi áp dụng BlockingQueue

1. **Dùng `ArrayList`/`ArrayDeque` đồng thời mà không đồng bộ.** Có data race/corruption; dùng đúng concurrent/BlockingQueue hoặc lock.
2. **Tự viết `wait()` với `if`.** Spurious wakeup và cạnh tranh sau notify có thể làm predicate sai; dùng while dưới cùng monitor.
3. **Đọc condition ngoài lock rồi mới wait.** Dễ missed notification; bảo vệ check–wait cùng giao thức đồng bộ.
4. **Dùng `notify()` tùy tiện khi có nhiều loại waiter.** Có thể đánh thức nhóm không tiến triển; cân nhắc notifyAll/Conditions đúng.
5. **Cho rằng `BlockingQueue` có `close()`.** Không có method close chung; phải thiết kế lifecycle.
6. **Cho rằng `take()` trả null báo kết thúc.** `take()` chờ tới khi có item, không dùng null sentinel.
7. **Đặt null vào BlockingQueue.** Không được phép; null dành làm giá trị “không có item” của `poll/peek`.
8. **Dùng `put()` trên unbounded queue và mong có backpressure nhỏ.** Không có capacity đủ nhỏ để tạo áp lực theo yêu cầu; cấu hình bounded.
9. **Dùng `offer()` nhưng bỏ qua false.** Item có thể bị từ chối mà hệ thống vẫn báo thành công.
10. **Busy-poll bằng vòng lặp `poll()` liên tục.** Có thể đốt CPU; dùng take/timed poll hoặc scheduler.
11. **Dùng một pill cho nhiều consumers.** Chỉ một consumer nhận pill; những consumer khác có thể treo.
12. **Producer phát pill trước khi mọi producers hoàn tất.** Một consumer có thể kết thúc trước khi hết work.
13. **Dùng một sentinel trùng giá trị dữ liệu thật.** Consumer có thể bỏ nhầm dữ liệu; dùng type marker.
14. **Giả định FIFO queue bảo đảm FIFO processing completion.** Nhiều consumers có thể xử lý xong đảo thứ tự.
15. **Dùng `isEmpty` rồi `remove` như một operation atomic.** Check-then-act race; dùng poll/take.
16. **Dùng `PriorityBlockingQueue` làm bounded work queue.** Không có hard capacity do constructor cung cấp.
17. **Dùng `SynchronousQueue` với một worker producer block còn consumer cũng xếp sau trong cùng pool.** Có thể starvation deadlock.
18. **Ngừng producer nhưng không thông báo consumer.** Consumer tiếp tục chờ vô hạn.
19. **Nuốt `InterruptedException`.** Cancel/shutdown không được tôn trọng.
20. **Tưởng handoff tự deep-freeze payload.** Mutable object tiếp tục bị sửa vẫn không an toàn.
21. **Tưởng queue bảo đảm exactly-once business processing.** Item đã take có thể mất khi consumer chết trước commit.
22. **Không bounded backlog tại upstream.** Queue đầy nhưng vô hạn request threads đứng chờ put vẫn gây overload.
23. **Dùng queue để giải quyết barrier/broadcast.** Sai semantics; dùng đúng synchronizer.
24. **Thay executor bằng một hệ thống worker tự viết khi không cần.** Tăng trách nhiệm xử lý queue, failures, lifecycle và scheduling.
25. **Không đo peak writes/bursts và shutdown latency.** Thiết kế có thể hoạt động ở demo nhưng sụp dưới tải thực.

## 20. Checklist ra quyết định

Trước khi chọn BlockingQueue, trả lời:

1. Có thật sự **các item được chuyển từ producer sang consumer** không?
2. Consumer phải **đợi khi rỗng** hay có thể thăm dò nonblocking?
3. Producer phải **đợi khi đầy**, reject/drop hay retry?
4. Có cần **capacity cứng** cho backlog không?
5. Thứ tự thực sự cần: FIFO, priority, delayed, LIFO hay hoàn tất theo sequence?
6. Nhiều consumers có được chia các items hay mỗi item cần broadcast tới tất cả?
7. Payload có immutable hoặc chuyển ownership sau enqueue không?
8. Có cần snapshot/multi-field transaction thay vì chỉ queue item không?
9. Producer và consumer có được bảo đảm có đủ worker để tiến triển?
10. Có deadline/cancellation cho `put/take` đang chờ không?
11. Khi producer lỗi, consumer được thông báo thế nào?
12. Nếu nhiều producers, ai có quyền phát terminal marker cuối?
13. Với N consumers, có đủ pills hoặc close protocol không?
14. Nếu queue full lúc shutdown, có đường tiến triển không?
15. Item đã take nhưng xử lý fail có cần retry/durable ack không?
16. Có cần backpressure lên upstream để không phình backlog ở nơi khác?
17. Có phải một event signal cho tất cả waiters, không phải queue? Nếu vậy, dùng `CountDownLatch`.
18. Có phải giới hạn số resource in-use? Nếu vậy, xem `Semaphore`.
19. Có cần nhiều phases/barrier? Nếu vậy, xem `Phaser/CyclicBarrier`.
20. Có đơn giản chỉ là `Callable` chạy nền? Nếu vậy, xem `ExecutorService.submit`.
21. Đã test race và worker failure bằng latch/timed get chưa?
22. Đã đo throughput, wait latency, memory và shutdown dưới tải chưa?

## 21. Câu hỏi tự kiểm tra và đáp án

1. **BlockingQueue thay thế phần nào của monitor producer–consumer?** Queue data structure, đồng bộ thêm/lấy, chờ rỗng/đầy, wakeup và visibility.
2. **Khi queue rỗng, `take()` trả gì?** Nó chờ tới khi có item hoặc bị interrupt theo contract, không trả null để báo rỗng.
3. **Khi queue rỗng, `poll()` trả gì?** `null` ngay, không chờ.
4. **Với queue bounded đã đầy, `offer(e)` trả gì?** `false`.
5. **Với queue bounded đã đầy, `put(e)` làm gì?** Có thể block đợi có chỗ hoặc bị interrupt.
6. **`offer(e,t,u)` hết hạn trả gì?** `false` nếu không thêm được trước thời hạn.
7. **Có thể dùng `null` làm poison pill không?** Không; BlockingQueue không cho phần tử null.
8. **Vì sao `wait` phải nằm trong while?** Spurious wakeup và cạnh tranh có thể làm predicate sai khi thức dậy.
9. **Vì sao check ngoài lock rồi wait nguy hiểm?** Producer có thể thay đổi state/notify giữa check và wait, gây missed signal.
10. **Có thể gọi BlockingQueue rồi coi payload mutable tự thread-safe không?** Không; cần ownership/immutable hoặc synchronization riêng.
11. **Có memory happens-before từ producer put tới consumer take không?** Có, theo contract của BlockingQueue với object được truyền.
12. **ArrayBlockingQueue có capacity tùy chọn không giới hạn không?** Không; capacity cố định theo constructor.
13. **LinkedBlockingQueue không truyền capacity mặc định lớn bao nhiêu?** `Integer.MAX_VALUE`.
14. **SynchronousQueue có buffer không?** Không, capacity 0; yêu cầu rendezvous producer–consumer.
15. **PriorityBlockingQueue có FIFO như ArrayBlockingQueue không?** Không; theo comparator/natural priority.
16. **PriorityBlockingQueue có bounded backlog theo constructor capacity không?** Không.
17. **Queue FIFO có bảo đảm kết quả của các consumer được commit FIFO không?** Không; xử lý song song có thể đảo thứ tự hoàn tất.
18. **Một poison pill có dừng mọi N consumers không?** Không; một pill thường chỉ bị một consumer take.
19. **Nếu producer chết trước pill, cần gì?** Coordinator/close/cancel/timeout để consumer không treo.
20. **BlockingQueue có close() chung không?** Không.
21. **Item đã take rồi consumer chết có tự quay lại queue không?** Không; cần retry/ack policy.
22. **Queue capacity C có giới hạn C active tasks sau take không?** Không; capacity chỉ giới hạn items đang nằm trong queue.
23. **Chờ một tín hiệu one-shot cho nhiều threads nên dùng gì?** `CountDownLatch`.
24. **Giới hạn số connection dùng đồng thời nên cân nhắc gì?** `Semaphore`/resource pool có contract phù hợp.
25. **Chạy một Callable và lấy kết quả nên xem gì?** `ExecutorService.submit`/`Future`.
26. **Dùng queue thay broadcast event có đúng không?** Không mặc định; consumers chia items chứ không nhận tất cả.
27. **`poll()==null` có nghĩa producer đã đóng chưa?** Không; chỉ hiện tại không có item sẵn sàng.
28. **`future.get(timeout)` timeout có tự cancel producer không?** Không; cần quyết định cancel riêng.
29. **Vì sao unbounded queue có nguy cơ OOM?** Producers có thể tích lũy item nhanh hơn consumers xử lý.
30. **Khi nào nên tự viết Condition thay queue?** Khi bài toán là invariant state riêng hoặc nhiều predicates không biểu diễn đúng bằng item handoff.

## 22. Tổng kết

**Dùng `BlockingQueue` khi bạn thật sự cần một hàng đợi công việc hoặc dữ liệu giữa các threads**, đặc biệt khi consumer phải đợi rỗng, producer cần chờ hoặc nhận reject khi đầy, và bạn muốn tránh tự viết các giao thức `wait/notify/Condition` vốn dễ gây race, missed notification và spurious wakeup bugs.

- Chọn **`ArrayBlockingQueue`** khi muốn bounded FIFO và backpressure rõ ràng.
- Chọn **`LinkedBlockingQueue(capacity)`** nếu phù hợp với linked FIFO; đừng vô tình để capacity gần vô hạn khi có nguy cơ backlog.
- Chọn **`SynchronousQueue`** nếu cần handoff trực tiếp, không buffer.
- Chọn **`PriorityBlockingQueue`** hoặc **`DelayQueue`** khi thứ tự ưu tiên/thời gian đến hạn là yêu cầu, lưu ý boundedness và ordering khác FIFO.
- Dùng **`offer/poll`** khi không được phép chờ vô thời hạn; **`put/take`** khi muốn block và đã thiết kế được lifecycle.
- Không quên **interruption, poison pill/close protocol, capacity, backpressure, resource ownership và error handling**.

Nếu điều bạn cần **không phải item handoff**, hãy chọn đúng synchronizer: `CountDownLatch` cho one-time signal, `Phaser` cho phases, `Semaphore` cho permits, `Condition` cho invariant state, hoặc `Future/ExecutorService` cho kết quả một computation. BlockingQueue **giảm gánh nặng phối hợp luồng**, nhưng không tự giải quyết business transaction, retry, durable messaging, exactly-once processing hay graceful shutdown.

## Tài liệu tham khảo Java SE 17

- [BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html): bảng method, happens-before, không cho null, thiếu close protocol chung.
- [ArrayBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html): bounded FIFO và fairness.
- [LinkedBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingQueue.html): optional capacity và FIFO.
- [SynchronousQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/SynchronousQueue.html): rendezvous zero-capacity.
- [PriorityBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/PriorityBlockingQueue.html): priority, không bounded, tie ordering.
- [DelayQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/DelayQueue.html): delayed items.
- [LinkedBlockingDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingDeque.html): thao tác ở hai đầu.
- [CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): one-shot synchronization.
- [Semaphore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html): giới hạn permits.
- [Condition](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Condition.html): wait/signal tùy predicate.
- [ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): quản lý task/worker/lifecycle.
- [Object.wait](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#wait()): monitor wait, spurious wakeup và monitor ownership.
- [JMH — Java Microbenchmark Harness](https://openjdk.org/projects/code-tools/jmh/): benchmark concurrency có kiểm soát.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronizers-012">Tránh deadlock do thiếu arrive(), countDown() hoặc release()</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh deadlock do thiếu arrive(), countDown() hoặc release()

## 1. Mục tiêu học tập và phạm vi

Sau bài này, bạn có thể:

- Phân biệt **deadlock**, **chờ vô hạn vì thiếu tín hiệu**, **resource leak**, **starvation** và **broken barrier**. Không phải mọi thread đang WAITING đều chứng minh một vòng chờ deadlock theo nghĩa chặt.
- Hiểu vai trò của **CountDownLatch.countDown()**, **CyclicBarrier.await()**, **Phaser.arrive()/arriveAndDeregister()** và **Semaphore.release()** trong việc giúp thread khác tiến lên.
- Nhận diện các nhánh **return sớm**, **exception**, **interruption**, **timeout**, **task bị từ chối**, **task chưa start** khiến sự kiện hoàn tất không được báo.
- Dùng **try/finally**, đăng ký/bỏ đăng ký participant có kiểm soát, và **chỉ release permit khi acquire thành công**.
- Chọn **timeout, failure propagation, cancellation và abort policy** để hệ thống thoát khỏi chờ vô hạn, nhưng không nhầm timeout với việc sửa xong trạng thái hoặc rollback side effect.
- Phân biệt **phát tín hiệu task kết thúc** với **phát tín hiệu task thành công**; không gọi countDown hoặc arrive để giả mạo điều kiện nghiệp vụ chưa đạt.
- Chạy các chương trình Java 17+ minh họa liveness failure và cách tổ chức giao thức đúng, với stdout có thể kiểm chứng bằng latch, join và exception handling thay vì dựa vào Thread.sleep.

Đây là bài của **Synchronizers** trong **Java concurrency nói chung**. Các cơ chế liên quan riêng tới Spring đã có chương riêng và không được sử dụng ở đây.

## 2. Synchronizer không tự cứu một giao thức bị bỏ dở

Nhiều primitive chỉ hoạt động đúng khi **tất cả bên tham gia tuân thủ giao thức**. Chúng không có khả năng suy đoán rằng một thread đã thất bại, bị hủy, quên gọi completion hay làm mất permit.

~~~text
Thread A                              Thread B
--------                              --------
thực hiện công việc                   await tín hiệu
       |
       +-- thành công -> signal ----------> tiếp tục
       |
       +-- exception -> quên signal ------> vẫn chờ
       |
       +-- return sớm -> quên signal -----> vẫn chờ
~~~

Một trường hợp khác:

~~~text
Worker A: semaphore.acquire() -> giữ permit
          gặp lỗi rồi return, quên release()
Worker B: semaphore.acquire() -> không còn permit -> chờ
~~~

Cách xử lý phụ thuộc **ngữ nghĩa tín hiệu**:

- Nếu latch đại diện cho **“worker đã kết thúc, kể cả lỗi”**, hãy countDown trong finally và lưu lỗi ở một kênh riêng.
- Nếu latch đại diện cho **“tất cả bước đã đạt điều kiện thành công”**, không được countDown vô điều kiện trong finally rồi cho consumer tưởng đã thành công. Cần mô hình trạng thái FAILED, kênh báo lỗi hoặc thay đổi primitive/giao thức.
- Nếu Phaser có participant đã đăng ký nhưng worker không thể tiếp tục, việc deregister đúng lúc có thể ngăn kẹt pha; song phải chứng minh pha sau vẫn có ý nghĩa.
- Với semaphore, release chỉ khi permit thật sự đã được acquire theo hợp đồng; release dư tạo ra lỗi khác, có thể nghiêm trọng hơn việc chờ lâu.

**Mấu chốt:** xác định rõ ai phải phát tín hiệu, tín hiệu biểu diễn điều gì, và từng đường thoát thực thi làm gì.

## 3. “Deadlock” có đúng là tên của mọi ca treo?

Một chương trình chờ mãi có thể do nhiều dạng lỗi khác nhau:

| Hiện tượng | Ví dụ | Phân tích |
| --- | --- | --- |
| **Deadlock vòng chờ** | A giữ lock L, chờ B; B cần L mới có thể kết thúc | Có chu trình phụ thuộc tài nguyên |
| **Missing signal / liveness failure** | Latch count=1 nhưng không ai có khả năng countDown | Waiter mắc kẹt dù không thấy vòng khóa hai thread |
| **Broken barrier** | Một party timeout/interrupted trong CyclicBarrier.await() | Barrier bị broken; các waiter khác nhận BrokenBarrierException |
| **Permit leak** | Semaphore.acquire thành công nhưng không release | Permit khả dụng giảm, người chờ có thể bị kẹt |
| **Starvation** | Task giữ mọi worker rồi chờ task con trong cùng pool | Task con không được lịch chạy, dù không có hai monitor |
| **Live lock** | Nhiều thread liên tục nhường/đổi trạng thái nhưng không tiến triển | Có hoạt động, không có progress nghiệp vụ |

Các kiểu **WAITING**, **TIMED_WAITING** trong thread dump là dấu hiệu chẩn đoán, không phải chứng minh một loại lỗi. Một thread hoàn toàn có thể WAITING hợp lệ vì đang đợi sự kiện chưa đến.

### Wait-for graph

~~~text
A đợi B hoàn tất
B đợi khóa L do A đang giữ
     A --> B --> L --> A  : chu trình deadlock

C đợi latch count về 0
Không còn producer sở hữu trách nhiệm countDown
     C --> latch?        : chờ vĩnh viễn vì mất completion
~~~

Hai trường hợp đều ảnh hưởng liveness, nhưng khắc phục khác nhau: trường hợp một cần phá dependency/lock order; trường hợp hai cần sửa giao thức hoàn tất và báo lỗi.

## 4. CountDownLatch: thiếu countDown khiến await không kết thúc

**CountDownLatch(initialCount)** là synchronizer một lần. Mỗi countDown() làm counter giảm một (đến 0 thì dừng). **await()** chờ đến khi counter về 0; khi count đã về 0, các await tiếp theo đi qua.

~~~java
// Minh họa, không phải file độc lập:
CountDownLatch completed = new CountDownLatch(2);
workerA.start();
workerB.start();
completed.await();
~~~

Nếu workerA và workerB đều phải countDown một lần nhưng một bên return trước khi báo, completed.getCount() có thể mãi lớn hơn 0.

**Counter không tự giảm khi Thread kết thúc**, khi Runnable ném exception hoặc khi executor shutdown. Primitive không biết câu chuyện nghiệp vụ đằng sau biến completed.

### Nguyên nhân thường gặp

- countDown đặt cuối thân try, exception ném trước dòng đó.
- Một nhánh return ngay khi phát hiện lỗi đầu vào.
- Task chưa được submit do RejectedExecutionException nhưng coordinator vẫn cộng count của task ấy.
- Worker chết trước khi bắt đầu và coordinator không có chính sách bù/failure.
- Thread bị interrupt khi đang làm việc và handler thoát mà không báo completion.
- Đếm sai số lượng worker/giai đoạn: tạo latch count=5 nhưng thực tế chỉ có 4 người có trách nhiệm báo.
- Một bên tưởng bên kia sẽ countDown, nên không ai thực hiện.

**Bất biến cần xác định:** nếu latch đại diện cho N completion, phải có **N nghĩa vụ hoàn tất** và mỗi nghĩa vụ cần đi đến trạng thái kết thúc đúng một lần, kể cả đường lỗi đã được xử lý.

### finally không tự bảo đảm thành công

~~~java
// Mảnh code trong Runnable, không phải chương trình độc lập:
try {
    doWork();
} catch (RuntimeException failure) {
    recordFailure(failure);
} finally {
    completed.countDown();
}
~~~

Trong ví dụ này, countDown nghĩa **“đã kết thúc do thành công hoặc lỗi”**. Consumer sau await phải kiểm tra **recordFailure** hoặc trạng thái result. Nếu đặt tên latch là allSucceeded rồi countDown mọi đường lỗi, đó là giao thức sai.

## 5. Ví dụ 1: CountDownLatch báo hoàn tất, lỗi theo kênh riêng

Tệp **LatchFinallyDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class LatchFinallyDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(2);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        Runnable good = () -> {
            try {
                succeeded.incrementAndGet();
            } finally {
                completed.countDown();
            }
        };

        Runnable bad = () -> {
            try {
                throw new IllegalStateException("demo failure");
            } catch (IllegalStateException ex) {
                failed.incrementAndGet();
            } finally {
                completed.countDown();
            }
        };

        Thread first = new Thread(good, "good");
        Thread second = new Thread(bad, "bad");
        first.start();
        second.start();

        completed.await();
        first.join();
        second.join();

        System.out.println("remaining = " + completed.getCount());
        System.out.println("succeeded = " + succeeded.get());
        System.out.println("failed = " + failed.get());
        System.out.println("all succeeded = " + (failed.get() == 0));
    }
}
~~~

~~~bash
javac -encoding UTF-8 LatchFinallyDemo.java
java LatchFinallyDemo
~~~

Stdout:

~~~text
remaining = 0
succeeded = 1
failed = 1
all succeeded = false
~~~

**Giải thích:**

1. Coordinator tạo latch count=2 theo hai nghĩa vụ completion.
2. Cả good và bad đều đưa countDown vào finally.
3. Task bad ném exception có chủ đích; catch ghi thất bại, finally vẫn báo **kết thúc**.
4. await hoàn tất khi count=0, không có busy waiting.
5. Thống kê riêng cho biết **không phải tất cả đã thành công**.
6. join đợi cả hai thread kết thúc trước khi in stdout, tạo điểm đọc kết quả rõ ràng.

### Khi không nên countDown trong finally?

Nếu tín hiệu là **“giai đoạn đã đạt điều kiện an toàn để tiếp tục”**, thất bại không được phát như thành công. Thiết kế đúng thường dùng **result/failure state** độc lập và coordinator kiểm tra nó sau latch, hoặc dùng Future để lấy kết quả/exception. Không sửa bug treo bằng cách cho downstream đi vào trạng thái thiếu dữ liệu.

## 6. Ví dụ 2: mô phỏng thiếu countDown với timeout có kiểm soát

Ví dụ sau không spawn một worker bí ẩn để làm chương trình treo. Ta tạo latch mà **không thread nào countDown**, rồi dùng overload có timeout để quan sát ngữ nghĩa mà không bị mắc vĩnh viễn.

Tệp **MissingLatchSignalDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MissingLatchSignalDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch neverCompleted = new CountDownLatch(1);

        boolean completed =
                neverCompleted.await(15, TimeUnit.MILLISECONDS);

        System.out.println("completed = " + completed);
        System.out.println("remaining = " + neverCompleted.getCount());
    }
}
~~~

~~~bash
javac -encoding UTF-8 MissingLatchSignalDemo.java
java MissingLatchSignalDemo
~~~

Stdout:

~~~text
completed = false
remaining = 1
~~~

**Vì sao chính xác?** Không có đường code gọi countDown(), nên sau khoảng chờ có giới hạn, await(timeout) trả false và counter vẫn 1. **Timeout không làm count giảm**, không cấp phép tiếp tục logic thành công và không sửa lỗi mất completion.

Trong sản phẩm thật, timeout cần dẫn tới một chính sách rõ:

- Báo lỗi hoặc trả trạng thái **INCOMPLETE**.
- Cố cancel task còn tồn tại nếu task hỗ trợ hợp tác hủy.
- Thu thập diagnostics và tìm participant nào không báo.
- Retry khi nghiệp vụ cho phép, có backoff/idempotency.
- Không lặp timeout rồi tiếp tục chờ vô hạn nếu deadline của request đã hết.

**CountDownLatch không reset.** Nếu cần nhiều pha lặp hoặc số party động, xem CyclicBarrier/Phaser và các quy tắc khác bên dưới.

## 7. CountDownLatch và Future: cách xử lý exception tốt hơn

Nếu một nhóm task chạy trong ExecutorService và caller cần biết **kết quả riêng**, giữ danh sách **Future** thường tự nhiên hơn dùng một latch chỉ để biết completion.

~~~java
// Minh họa trong một method có ExecutorService pool:
Future<Integer> f = pool.submit(() -> calculate());
try {
    int result = f.get();
} catch (ExecutionException ex) {
    handleFailure(ex.getCause());
}
~~~

Đây không phải chương trình độc lập: calculate(), handleFailure(), pool và import được giả định. Future.get() có thể ném InterruptedException; nếu dùng timeout, còn có TimeoutException.

**Phân biệt:** Future có trạng thái **done** kể cả failure/cancellation. Caller phải kiểm tra get()/exception, không dựa vào isDone() để kết luận thành công.

### Bẫy: đếm nghĩa vụ trước khi task được nhận

~~~text
Coordinator tạo latch count=10
Executor chỉ nhận 8 task; 2 lần submit bị reject.
8 worker hoàn tất và countDown 8 lần.
Latch còn 2; không ai có trách nhiệm countDown.
~~~

Nếu vẫn muốn dùng latch, phải có contract rõ rằng **coordinator bù completion của task không được nhận** hoặc abort toàn bộ workflow. Không được phát tín hiệu thành công giả. Với các task bị reject, caller cần xử lý **RejectedExecutionException** ngay khi submit thay vì bỏ qua nó.

Một giải pháp khác là **chỉ tính các nghĩa vụ đã được chấp nhận** qua một cơ chế đăng ký động (Phaser) hoặc collection of Futures, nhưng phải giải quyết được race giữa register/start và đóng admission.

## 8. CyclicBarrier: thiếu một party có thể kẹt cả nhóm

**CyclicBarrier(parties)** tạo điểm gặp cho đúng số party cố định tại mỗi **generation**. Mỗi bên gọi **await()**; khi đủ parties, barrier trip, các thread được phép tiếp tục và barrier sẵn sàng dùng cho generation tiếp theo.

~~~text
CyclicBarrier(3)

Thread A: ... await() ---+
Thread B: ... await() ---+-- đủ 3 --> cùng tiếp tục
Thread C: ... await() ---+
~~~

Nếu C **không gọi await()** mà A/B đều gọi await() không timeout, các thread ấy có thể chờ mãi. Đó là lỗi giao thức participant. **CyclicBarrier không tự biết C đã chết** khi C chưa từng vào barrier.

### Khác CountDownLatch

| Thuộc tính | CountDownLatch | CyclicBarrier |
| --- | --- | --- |
| Số lượng | CountDown theo số completion | Số party cố định phải await trong một pha |
| Reuse | Không reset sau khi về 0 | Có thể dùng qua nhiều generation |
| Tín hiệu đến từ | Worker countDown | Mỗi party đến await |
| Lỗi/chờ thiếu party | Có thể chờ vô hạn | Có thể chờ vô hạn nếu không timeout/interruption |
| Timeout | await(timeout) trả boolean | await(timeout) có thể ném TimeoutException và làm barrier broken |
| Failure state | Không có built-in broken flag | có **isBroken()** |

**Không dùng countdown thay vì barrier chỉ vì đều có chữ “chờ”.** Một barrier đồng bộ các bên ở **cùng mốc pha**, còn latch cho phép một hoặc nhiều waiter quan sát một counter về 0.

### Vai trò của barrier action

Constructor CyclicBarrier có thể nhận một **Runnable barrierAction** chạy khi đủ parties, trước khi các party được giải phóng. Nếu action ném exception, có thể gây broken barrier. Không đặt side effect blocking dài trong barrier action rồi ngạc nhiên vì tất cả party đều bị kéo chậm.

## 9. Ví dụ 3: thiếu một party trong CyclicBarrier

Tệp **BarrierMissingPartyDemo.java**:

~~~java
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class BarrierMissingPartyDemo {
    public static void main(String[] args) throws InterruptedException {
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicBoolean timedOut = new AtomicBoolean();

        Thread onlyParticipant = new Thread(() -> {
            try {
                barrier.await(20, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ex) {
                timedOut.set(true);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException ex) {
                throw new AssertionError("unexpected broken", ex);
            }
        });

        onlyParticipant.start();
        // Không có party thứ hai nào gọi await().
        onlyParticipant.join();

        System.out.println("timed out = " + timedOut.get());
        System.out.println("broken = " + barrier.isBroken());
    }
}
~~~

~~~bash
javac -encoding UTF-8 BarrierMissingPartyDemo.java
java BarrierMissingPartyDemo
~~~

Stdout:

~~~text
timed out = true
broken = true
~~~

**Tại sao?**

- Barrier cần 2 party nhưng chỉ một thread tới await.
- Sau timeout, await ném TimeoutException và generation chuyển sang trạng thái broken.
- Main join thread rồi đọc flags.
- Không dùng Thread.sleep để “hy vọng” bắt kịp.
- Đầu ra minh họa đường lỗi, **không phải cách vận hành thành công** của barrier.

Nếu một thread đang await bị interrupt, barrier cũng có thể bị broken và các waiter khác nhận BrokenBarrierException. **Không được catch InterruptedException rồi im lặng bỏ qua** khi đồng đội đang trông chờ party đó.

### Timeout không tự làm các bên hoàn thành giai đoạn

Khi barrier broken, phải coi generation ấy thất bại. Có thể **reset()** trong một protocol tái khởi động có điều phối, nhưng reset tùy tiện trong lúc party khác còn dùng barrier gây lỗi logic. Thiết kế cần biết ai được quyền reset, ai hủy công việc cũ và lúc nào tất cả bên sẵn sàng bước vào generation mới.

## 10. Ví dụ 4: CyclicBarrier có đủ party, lặp hai pha

Tệp **BarrierTwoPhasesDemo.java**:

~~~java
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BarrierTwoPhasesDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger trips = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(
                2, trips::incrementAndGet
        );
        AtomicInteger completed = new AtomicInteger();

        Runnable worker = () -> {
            try {
                for (int phase = 0; phase < 2; phase++) {
                    // Thực hiện bước cục bộ rồi tới cùng barrier.
                    barrier.await(5, TimeUnit.SECONDS);
                    completed.incrementAndGet();
                }
            } catch (Exception ex) {
                throw new IllegalStateException("barrier failed", ex);
            }
        };

        Thread a = new Thread(worker, "a");
        Thread b = new Thread(worker, "b");
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("barrier trips = " + trips.get());
        System.out.println("completed steps = " + completed.get());
        System.out.println("broken = " + barrier.isBroken());
    }
}
~~~

~~~bash
javac -encoding UTF-8 BarrierTwoPhasesDemo.java
java BarrierTwoPhasesDemo
~~~

Stdout với môi trường xử lý bình thường và không bị interrupt từ bên ngoài:

~~~text
barrier trips = 2
completed steps = 4
broken = false
~~~

Cả a và b đều tới barrier đúng hai lần, nên action chạy hai lần; mỗi thread đi qua mỗi pha rồi tăng completed. **Thời lượng chờ không phải benchmark**; giới hạn 5 giây chỉ là đường thoát khi môi trường có vấn đề, không phải SLA thời gian cố định.

**Nếu một worker có thể rời workflow trước pha 2**, CyclicBarrier fixed parties có thể không phù hợp, trừ khi coordinator có protocol rõ để kết thúc hoặc khởi tạo nhóm mới. Dùng Phaser khi cần participant động.

## 11. Phaser: arrived, unarrived và registered parties

**Phaser** hỗ trợ đồng bộ hóa nhiều pha với **số party có thể thay đổi**. Một party có thể register, **arrive**, **arriveAndAwaitAdvance**, hoặc **arriveAndDeregister**.

| API | Ngữ nghĩa |
| --- | --- |
| register() | Đăng ký thêm một party |
| bulkRegister(n) | Đăng ký n party |
| arrive() | Báo party đã đến pha hiện tại, không chờ |
| arriveAndAwaitAdvance() | Báo đã đến và chờ pha chuyển |
| arriveAndDeregister() | Báo đến và rời khỏi nhóm party cho các pha sau |
| awaitAdvance(phase) | Chờ pha đổi, bản thân không thay arrive |
| awaitAdvanceInterruptibly(phase, timeout, unit) | Chờ có interrupt/timeout theo overload |
| forceTermination() | Buộc Phaser kết thúc, đánh thức bên chờ theo ngữ nghĩa termination |
| getRegisteredParties()/getUnarrivedParties() | Số party đăng ký/chưa arrive ở pha hiện tại |

**Khác biệt sống còn:** **awaitAdvance(phase) không tự gọi arrive()**. Nếu một party chỉ gọi awaitAdvance khi nó còn nghĩa vụ arrive mà không ai khác bù, phase có thể không tiến.

### Ví dụ các lỗi

~~~text
Phaser đăng ký 3 parties.
A gọi arrive().
B gọi arrive().
C exception trước arrive().
C không deregister.
=> unarrived vẫn còn 1, phase không advance.
~~~

Nếu một participant biến mất sau khi register và coordinator không có protocol phát hiện/bù, các waiter có thể mắc kẹt. **Phaser không tự đoán party đã chết**.

**Không register quá sớm:** nếu register trước khi có khả năng start worker và start thất bại, nghĩa vụ arrive vẫn tồn tại. Cần đặt nghĩa vụ register/start/deregister trong một scope có thể quản lý lỗi.

## 12. Phaser và finally: đúng nhưng không phải luôn cần thiết

Với worker chỉ tham gia một giai đoạn rồi rời, có thể:

~~~java
// Mảnh code minh họa; phaser đã được chia sẻ và đăng ký party.
try {
    doWork();
} finally {
    phaser.arriveAndDeregister();
}
~~~

Đây là mảnh code, cần doWork() và cơ chế start/exception phù hợp. finally đảm bảo worker không giữ party lâu hơn vòng đời dự kiến khi code đi qua đường thoát bình thường hoặc exception.

**Nhưng** nếu worker thất bại làm phase tiếp theo không còn an toàn, chỉ deregister rồi cho tất cả tiếp tục là sai về nghiệp vụ. Hãy lưu trạng thái FAILED, báo coordinator và hủy pha hoặc **forceTermination()** theo một protocol đã xác định.

### Lỗi gọi arrive hai lần

Một party đã gọi **arrive()** trong phase hiện tại **không được arrive thêm một lần cùng phase**. Đếm thừa có thể vi phạm giao thức của Phaser hoặc ném IllegalStateException khi số party/unarrived không hợp lệ. Nếu finally gọi arriveAndDeregister sau khi phần thân đã arrive(), phải kiểm soát trạng thái để **tránh double arrive**.

Dùng một **chỗ sở hữu trách nhiệm arrival** ở mỗi phase, thay vì nhiều nhánh cùng tự báo.

## 13. Ví dụ 5: Phaser với worker đăng ký trước khi start

Tệp **PhaserDeregisterDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;

public class PhaserDeregisterDemo {
    public static void main(String[] args) throws InterruptedException {
        Phaser phaser = new Phaser(1); // Main là coordinator.
        AtomicInteger completed = new AtomicInteger();
        List<Thread> workers = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            phaser.register(); // Trách nhiệm của worker mới.
            Thread worker = new Thread(() -> {
                try {
                    completed.incrementAndGet();
                } finally {
                    phaser.arriveAndDeregister();
                }
            }, "worker-" + i);
            workers.add(worker);
            worker.start();
        }

        phaser.arriveAndAwaitAdvance();
        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("completed = " + completed.get());
        System.out.println(
                "registered parties = " + phaser.getRegisteredParties()
        );

        phaser.arriveAndDeregister(); // Coordinator rời Phaser.
    }
}
~~~

~~~bash
javac -encoding UTF-8 PhaserDeregisterDemo.java
java PhaserDeregisterDemo
~~~

Stdout:

~~~text
completed = 3
registered parties = 1
~~~

**Giải thích:**

- Main được đăng ký ngay từ new Phaser(1).
- Trước khi start từng worker, coordinator register nghĩa vụ của worker đó.
- Mỗi worker, kể cả khi logic phát sinh unchecked exception sau khi vào try, vẫn arriveAndDeregister ở finally.
- Main tới arriveAndAwaitAdvance, đợi các worker đã đăng ký cho phase hiện tại hoàn tất/deregister.
- Sau khi phase tiến, chỉ còn main đã đăng ký: registered parties = 1.
- Main deregister ở cuối; không còn participant nào.
- completed phản ánh ba worker đã xử lý phần minh họa.

**Giới hạn:** Ví dụ giả định start() thành công và không có lỗi fatal trước khi vào try. Ứng dụng thật cần xử lý trường hợp **Thread.start() thất bại**, không để party đã register tồn tại mà không có worker, và có coordinator failure policy.

### Nếu thread thất bại giữa nhiều phase

Khi có nhiều vòng, cần quy định worker được phép rời ở phase nào, liệu số party có thể giảm hay không, và một lỗi có làm kết quả của phase đó vô nghĩa không. **arriveAndDeregister** chỉ quản lý số người tham gia; nó không tự bù dữ liệu nghiệp vụ mà người rời chưa sản xuất.

## 14. Timeout của Phaser khác CyclicBarrier

**CyclicBarrier.await(timeout)** khi một party timeout thường **break barrier** và các waiter khác có đường nhận BrokenBarrierException.

Với **Phaser.awaitAdvanceInterruptibly(phase, timeout, unit)**, một thread timeout/interrupted **không tự làm Phaser terminated**, và phương thức chờ này **không tự thay nghĩa vụ arrive** của một party. Nếu lỗi điều phối vẫn tồn tại, các bên khác có thể tiếp tục bị kẹt.

Một chiến lược khi không thể tiếp tục:

1. Coordinator nhận TimeoutException hoặc InterruptedException.
2. Ghi nhận phase thất bại và ngừng cấp tác vụ mới cho phase đó.
3. Ra quyết định **forceTermination()** nếu toàn nhóm phải dừng.
4. Yêu cầu mọi worker hợp tác với dừng/hủy và giải phóng tài nguyên.
5. Tách phần **dừng chờ** khỏi phần **rollback/compensation** cho side effect bên ngoài.

**forceTermination không giết thread và không rollback transaction.** Nó thay trạng thái của phaser và đánh thức waiter theo hợp đồng; worker vẫn có thể đang làm việc nếu không tự kiểm tra.

## 15. Semaphore: thiếu release khiến permit bị rò

**Semaphore(permits)** đại diện cho số quyền truy cập còn có thể cấp. **acquire()** có thể chờ cho đến khi lấy được permit; **release()** trả permit.

~~~java
// Anti-pattern minh họa:
sem.acquire();
doWork(); // Exception ở đây khiến sem.release không được gọi.
sem.release();
~~~

Mảnh code không độc lập; sem và doWork phải có sẵn, phương thức cần xử lý InterruptedException. Nếu worker throw hoặc return trước release, permit giảm vĩnh viễn theo góc nhìn semaphore cho đến khi có hành động bù đúng.

**Một semaphore bị leak hết permits có thể khiến mọi caller mới chờ vô hạn.** Đó là liveness failure; khi kết hợp nhiều resource/lock, nó còn có thể tạo deadlock vòng chờ.

### Pattern đúng với acquire interruptible

~~~java
// Mảnh code trong phương thức throws InterruptedException.
boolean acquired = false;
try {
    sem.acquire();
    acquired = true;
    doWork();
} finally {
    if (acquired) {
        sem.release();
    }
}
~~~

**Vì sao cần cờ acquired?** acquire() có thể ném InterruptedException **trước khi lấy được permit**. Nếu finally gọi release vô điều kiện, số permit tăng sai, tạo over-release.

### Cách khác: đặt try ngay sau acquire

~~~java
sem.acquire();
try {
    doWork();
} finally {
    sem.release();
}
~~~

Khi sem.acquire() ném exception, code chưa vào try/finally này, nên không release. Khi acquire thành công và đi vào try, finally trả permit. Đây là mẫu gọn đúng trong luồng bình thường, nhưng phải xét lỗi bất thường trong khoảng giữa acquire và try nếu có thao tác bổ sung; nên giữ khoảng này tối thiểu.

## 16. Ví dụ 6: bị interrupt trước khi acquire thì không release thừa

Ta dùng một holder giữ permit duy nhất. Waiter bị interrupt trong khi đang cố acquire nên **không có permit** để trả. Trong finally, waiter chỉ release khi acquired thành true.

Tệp **SemaphoreInterruptDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class SemaphoreInterruptDemo {
    public static void main(String[] args) throws InterruptedException {
        Semaphore permits = new Semaphore(1);
        CountDownLatch holderReady = new CountDownLatch(1);
        CountDownLatch allowRelease = new CountDownLatch(1);
        AtomicBoolean waiterInterrupted = new AtomicBoolean();

        Thread holder = new Thread(() -> {
            boolean acquired = false;
            try {
                permits.acquire();
                acquired = true;
                holderReady.countDown();
                allowRelease.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (acquired) {
                    permits.release();
                }
            }
        });

        holder.start();
        holderReady.await(); // Holder đang giữ permit duy nhất.

        Thread waiter = new Thread(() -> {
            boolean acquired = false;
            try {
                permits.acquire();
                acquired = true;
            } catch (InterruptedException ex) {
                waiterInterrupted.set(true);
                Thread.currentThread().interrupt();
            } finally {
                if (acquired) {
                    permits.release();
                }
            }
        });

        waiter.start();
        waiter.interrupt();
        waiter.join();

        allowRelease.countDown();
        holder.join();

        System.out.println(
                "waiter interrupted = " + waiterInterrupted.get()
        );
        System.out.println("permits = " + permits.availablePermits());
    }
}
~~~

~~~bash
javac -encoding UTF-8 SemaphoreInterruptDemo.java
java SemaphoreInterruptDemo
~~~

Stdout:

~~~text
waiter interrupted = true
permits = 1
~~~

**Lập luận:**

1. Holder acquire permit rồi báo holderReady; main mới cho waiter start.
2. Waiter không thể lấy permit đang được holder giữ.
3. Main interrupt waiter; nếu interrupt đến trước khi waiter thực sự block, acquire interruptible vẫn xử lý trạng thái interrupt khi bắt đầu.
4. Vì acquire không thành công, cờ acquired vẫn false; waiter không gọi release.
5. Holder sau đó được mở latch, trả đúng một permit.
6. Kết quả permits=1, không bị over-release.

**Lưu ý:** Semaphore không gắn ownership permit cứng với thread như ReentrantLock; API cho phép một thread khác release. Nhưng vì vậy **ứng dụng phải giữ kỷ luật ownership** của permit ở mức nghiệp vụ, không được coi release tùy tiện là an toàn.

## 17. Ví dụ 7: Semaphore giới hạn đồng thời và hoàn trả sau mọi đường

Tệp **SemaphoreCapacityDemo.java**:

~~~java
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class SemaphoreCapacityDemo {
    public static void main(String[] args) throws InterruptedException {
        Semaphore slots = new Semaphore(2);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();

        Runnable task = () -> {
            boolean acquired = false;
            try {
                slots.acquire();
                acquired = true;

                int now = active.incrementAndGet();
                peak.accumulateAndGet(now, Math::max);
                try {
                    if (6 * 7 == 42) {
                        completed.incrementAndGet();
                    }
                } finally {
                    active.decrementAndGet();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (acquired) {
                    slots.release();
                }
            }
        };

        Thread[] threads = new Thread[4];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(task);
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }

        System.out.println("completed = " + completed.get());
        System.out.println("peak <= 2 = " + (peak.get() <= 2));
        System.out.println("permits = " + slots.availablePermits());
    }
}
~~~

~~~bash
javac -encoding UTF-8 SemaphoreCapacityDemo.java
java SemaphoreCapacityDemo
~~~

Stdout:

~~~text
completed = 4
peak <= 2 = true
permits = 2
~~~

Giới hạn **2 permit** khiến không hơn 2 task có thể đi vào critical section cùng lúc. Vì tác vụ rất ngắn, peak thực tế có thể bằng 1 hoặc 2; chỉ assert bất biến **peak <= 2**, không in “peak = 2” khi scheduler không bảo đảm.

**Sửa thiếu release không chỉ để tránh treo**: nếu release thiếu, permits còn 0/1; nếu release thừa, permits có thể vượt 2 và làm mất giới hạn concurrency.

## 18. Semaphore: acquire nhiều permit phải release đúng số

Với **acquire(n)** và **release(n)**, việc trả số permit phải khớp với số đã lấy thực sự.

~~~java
// Mảnh code trong phương thức throws InterruptedException:
sem.acquire(2);
try {
    useTwoResources();
} finally {
    sem.release(2);
}
~~~

Nếu lấy 2 mà chỉ release 1, semaphore bị leak một permit. Nếu lấy 1 mà release 2, giới hạn bị phá.

**Không nên** làm:

~~~java
// Anti-pattern: lỗi vì chưa chắc acquire thành công.
try {
    sem.acquire(2);
    work();
} finally {
    sem.release(2);
}
~~~

Nếu acquire(2) bị interrupt trước khi trả, finally vẫn release 2 dù caller chưa được cấp chúng. Với tryAcquire(timeout) trả false cũng tương tự: chỉ release nếu kết quả thực sự là true.

### Tính fairness

Semaphore có constructor fairness. Fair mode ảnh hưởng thứ tự cấp permit cho các thread chờ theo hợp đồng, nhưng không bảo đảm mọi thread được CPU đúng lượt hoặc mọi tryAcquire không tham gia policy. **Fairness không sửa permit leak**.

## 19. Return, exception, interrupt và cancel: ma trận trách nhiệm

| Điểm thoát | CountDownLatch | Phaser/CyclicBarrier | Semaphore |
| --- | --- | --- | --- |
| Thành công | Báo completion theo hợp đồng | Arrive đúng phase / await đúng generation | Release permit đã acquire |
| Validation thất bại trước khi làm việc | Báo failure; completion tùy ngữ nghĩa | Abort/deregister nếu đã register | Không release nếu chưa acquire |
| Runtime exception giữa đường | finally cho completion; lưu lỗi | Deregister hoặc terminate theo policy, tránh double arrive | finally release permit sở hữu |
| InterruptedException | Propagate/restore và báo trạng thái | Hủy pha/điều phối bên còn lại theo contract | Nếu acquire chưa thành công: không release |
| Timeout chờ bên khác | Không coi false là thành công | Barrier broken hoặc Phaser timeout cần coordinator xử lý | tryAcquire false: không giữ permit |
| Executor từ chối task | Coordinator xử lý nghĩa vụ task chưa nhận | Không để registration mồ côi | Không giả định permit thuộc task đã reject |
| Task bị cancel trước khi chạy | Coordinator cần completion/failure protocol | Hủy hoặc deregister party đã đăng ký | Chỉ trả permit nếu thực sự đã lấy |
| Shutdown trong lúc pending | Không tự countDown các nghĩa vụ | Không tự unregister party còn thiếu | Không tự thu hồi permit mà task cũ đang giữ |

**Nguyên tắc:** mỗi primitive có **đơn vị trách nhiệm** khác nhau (count, party-phase, permit). Đừng sửa một đơn vị bằng cách phát thừa đơn vị khác.

## 20. Dùng timeout để phát hiện, không để che lỗi

Một lệnh await() vô hạn là phù hợp khi protocol đã chứng minh sẽ có tín hiệu và yêu cầu cho phép chờ. Trong hệ thống nhiều nguồn lỗi, overload có timeout thường hữu ích:

~~~java
// Minh họa với CountDownLatch:
if (!completed.await(5, TimeUnit.SECONDS)) {
    throw new IllegalStateException("completion deadline exceeded");
}
~~~

Không phải chương trình độc lập: completed cần có và method cần khai báo InterruptedException.

**Timeout chỉ cung cấp đường thoát cho waiter.** Nó không:

- Giảm CountDownLatch count.
- Khôi phục permit đã bị leak.
- Bắt thread khác tự gọi arrive.
- Dừng task đang chạy hoặc hoàn tác external I/O.
- Chứng minh worker nào gây lỗi nếu thiếu telemetry.
- Biến kết quả thiếu dữ liệu thành kết quả thành công.

Một chính sách timeout tốt gồm **thông báo lỗi**, **cancel hợp tác**, **thu thập diagnostic**, **cleanup**, **backoff/retry có giới hạn**, và cách trả kết quả chính xác cho caller.

### Deadline tổng thay vì cấp lại timeout

Nếu workflow gồm ba bước chờ, mỗi bước timeout 5 giây, tổng thời gian có thể gần 15 giây, trái với deadline toàn request 5 giây. Nên tính **remaining budget** từ một clock đơn điệu (**System.nanoTime()**) trước từng bước chờ. Không dùng wall-clock time dễ thay đổi để đo elapsed.

## 21. Không giữ lock rồi chờ tín hiệu do người cần lock tạo

Đây là deadlock thực sự:

~~~text
Thread A:
  synchronized(lock L) {
      latch.await();
  }

Thread B:
  synchronized(lock L) {
      prepareData();
      latch.countDown();
  }
~~~

A đang giữ L nhưng chờ latch; B phải lấy L mới countDown. Ta có vòng phụ thuộc:

~~~text
A giữ L -> chờ latch
B cần L -> mới countDown
      ^             |
      +-------------+
~~~

**Đổi await() sang await(timeout)** giúp A có đường thoát sau hạn, nhưng không làm thiết kế đúng. Khi có thể, hãy **đưa await ra ngoài critical section**; khi lấy lại lock, phải kiểm tra lại state vì có thể thay đổi. Với Condition.await(), chính await sẽ nhả **lock liên kết với Condition** trong thời gian chờ, nhưng không nhả tất cả các lock khác mà thread đang giữ.

### “Chờ khi giữ lock” cũng gặp với Future

Một task giữ lock rồi Future.get() đợi task con cần lock ấy cũng tạo chu trình tương tự. Phải kiểm tra **dependency graph**, không chỉ lệnh arrive/countDown/release.

## 22. Thread pool starvation: không đủ worker để phát tín hiệu

Một nguồn kẹt liveness khác:

~~~text
Single-thread executor:
  Task A đang chạy trên worker duy nhất.
    A submit Task B vào cùng executor.
    A gọi futureB.get() hoặc latch.await() chờ B.
  Task B đang nằm trong queue nhưng không có worker rảnh.

=> A chờ B, B chờ worker mà A đang chiếm.
~~~

Đây là **thread-starvation deadlock**. Tăng pool lên 2 có thể làm ví dụ chạy ở mức tải thấp, nhưng một pool có 2 worker vẫn có thể bị 2 task cha chiếm hết rồi cùng chờ 2 task con. **Thay số worker không sửa nguyên tắc phụ thuộc vòng**.

Giải pháp:

- Không block đợi một tác vụ **chỉ có thể chạy trên tài nguyên do chính mình giữ**.
- Dùng composition bất đồng bộ, workflow state machine, hoặc cơ chế hỗ trợ fork/join đúng contract khi phù hợp.
- Phân tách nguồn tài nguyên nếu dependency hợp lệ và được kiểm soát.
- Giới hạn backlog, theo dõi worker active và queue age.
- Không nhầm **executor đã nhận task** với **task đã được thực thi**.

## 23. Participant động và task không được start

Ví dụ Phaser với N worker phải xử lý các ca:

1. Coordinator register participant.
2. Tạo hoặc submit task.
3. Submit có thể **bị reject**, Thread.start có thể thất bại, hoặc cancel xảy ra trước start.
4. Nếu participant đã register nhưng không có worker nào sẽ arrive, **pha bị mắc kẹt**.

Một thiết kế an toàn cần **register/start/rollback** trong cùng protocol. Ví dụ đơn giản:

~~~text
register party
try:
    submit work (work có finally -> arriveAndDeregister)
catch rejection:
    arriveAndDeregister chính party vừa register
    báo workflow failure, không giả vờ bước thành công
~~~

Đây là pseudocode cho một giao thức trong đó party mới **chưa arrive phase hiện hành**. Nếu worker đã bắt đầu hoặc có thể vừa chạy đồng thời với đường hủy, không được coordinator và worker **cùng deregister một party hai lần**. Cần cơ chế ownership/once-token cho nghĩa vụ hoàn tất.

### Race giữa cancel và start

Task có thể nhận cancel ngay khi worker bắt đầu; coordinator chưa biết nó đã tới finally hay chưa. Không nên dựa vào một boolean thường để quyết định ai release/deregister, vì check–then–act cũng có race. Có thể dùng **AtomicBoolean.compareAndSet(false,true)** cho *once completion* của một nghĩa vụ, hoặc cấu trúc task lifecycle có trạng thái và quyền sở hữu rõ.

**Once-completion khác “signal success”**: kể cả khi báo completion exactly once trong JVM, nghiệp vụ vẫn có thể FAILED hoặc CANCELLED, và process crash không được xử lý bởi AtomicBoolean.

## 24. Idempotency và trách nhiệm của tín hiệu

**CountDownLatch.countDown()** sau khi count=0 không làm âm. Điều này không có nghĩa gọi thừa countDown là an toàn về mặt giao thức: nếu count ban đầu N mà một worker countDown hai lần, latch có thể về 0 **trước khi worker khác kết thúc**.

**Semaphore.release()** có thể làm permit tăng quá giới hạn thiết kế khi gọi thừa. Nó không tự gắn ownership với thread.

**Phaser.arrive()** có trách nhiệm phase/party; gọi thừa hoặc deregister sai có thể phá phối hợp.

**CyclicBarrier.await()** là nghĩa vụ đến điểm gặp đúng **một lần trong mỗi generation**; không thể một thread gọi await hai lần liên tiếp để thay cho party còn thiếu, vì nó sẽ tự block ở lần đầu.

### Sổ nghĩa vụ

Hữu ích khi code review:

| Primitive | Nghĩa vụ | Điều kiện giải phóng hợp lệ |
| --- | --- | --- |
| CountDownLatch | Một count theo worker/giai đoạn | Worker đã đạt định nghĩa completion |
| CyclicBarrier | Một await mỗi party trong một generation | Party thực sự tới rào cản của generation ấy |
| Phaser | Một arrival mỗi party mỗi phase | Party arrive hợp lệ; deregister khi rời |
| Semaphore | Một permit đã acquire | Chủ thể trả đúng số permit theo giao thức |

Trong code, nên ghi rõ **ai sở hữu nghĩa vụ**, ai được phép phát tín hiệu thay thế và khi nào trạng thái lỗi phải được công bố.

## 25. Metrics, diagnostics và thread dump

Khi ứng dụng treo:

1. Xác định loại primitive và **đơn vị chờ** còn thiếu.
2. Với CountDownLatch, log **getCount()** và danh sách nhiệm vụ dự kiến; đừng chỉ thấy getCount()>0 rồi suy ra worker nào lỗi.
3. Với CyclicBarrier, xem **getNumberWaiting()**, **isBroken()**, số parties và generation ở cấp giao thức.
4. Với Phaser, xem **getPhase()**, **getRegisteredParties()**, **getUnarrivedParties()**; đây là snapshot có thể thay đổi.
5. Với Semaphore, xem **availablePermits()**, queue length ước lượng, số permit từng task đã acquire; không dùng queue length làm logic correctness.
6. Thu **thread dump** để thấy thread chờ trên synchronizer, lock, Future hay executor queue.
7. Kiểm tra **exception logs**, task rejection, cancellation, timeout và interrupted flags.
8. Xác định **thread nào còn có khả năng phát tín hiệu** hay vòng dependency đã kín.
9. Có correlation id cho mỗi nghĩa vụ: registered, started, arrived/released/failed/cancelled.
10. Tránh instrument bằng shared atomic counter nóng trên mọi bước hiệu năng cao nếu nó tạo contention đáng kể; có thể batch metric khi cần.

**Thread dump chỉ là một ảnh chụp**, còn nguyên nhân thường nằm ở sự kiện trước đó: task throw, missed release, submit bị reject, hoặc phase participant không được deregister.

### Kiểm tra liveness khác correctness

Một test xác minh **không treo** chưa chứng minh output đúng. Ví dụ thêm countDown vô điều kiện có thể làm test hoàn tất nhanh nhưng đọc dữ liệu chưa được tạo. Mọi phép sửa liveness phải giữ invariant nghiệp vụ.

## 26. Kiểm thử đúng bằng bất biến

Thay vì chạy 1000 lần và hy vọng race xuất hiện, hãy **điều khiển lịch**:

- Dùng CountDownLatch để bắt buộc worker tới một điểm trước khi main thực hiện bước tiếp theo.
- Dùng **await(timeout)** cho đường lỗi, tránh test treo vô hạn khi protocol có bug.
- Dùng **join()** để chắc worker đã kết thúc trước khi kiểm tra final state.
- Dùng AtomicBoolean/AtomicInteger cho biến quan sát được nhiều thread cập nhật.
- Nếu muốn thử interrupted waiter, giữ permit/latch bằng một worker rồi interrupt waiter theo giao thức.
- Với CyclicBarrier thiếu party, chỉ start một party trong barrier 2 parties rồi dùng timed await để có kết quả xác định.
- Với Phaser, register số party rõ, mọi worker deregister trong finally, coordinator đợi phase transition.
- Với Semaphore, xác minh **availablePermits() trở lại mức ban đầu** và **peak concurrent tasks <= permits** sau khi join.

**Không assert** rằng hai thread nhất định chạy xen kẽ một cách ngẫu nhiên; không in tên thread hoặc timestamp giả định thứ tự scheduler. Test nên chứng minh invariant, không dựa vào thời gian sleep “đủ dài”.

## 27. Anti-pattern và cách khắc phục

| Anti-pattern | Vì sao sai | Sửa bằng |
| --- | --- | --- |
| countDown chỉ ở cuối try | Exception/return bỏ qua | finally nếu latch đại diện completion |
| countDown cuối finally nhưng không ghi lỗi | Waiter có thể tưởng task thành công | Result/error state riêng |
| Khởi tạo latch với count lớn hơn nghĩa vụ thực | Waiter có thể không bao giờ qua | Kiểm kê obligations và xử lý rejected task |
| Gọi countDown hai lần từ một worker | Latch có thể mở quá sớm | Exactly-once completion protocol |
| CyclicBarrier.await không timeout dù party có thể chết | Các waiter còn lại mắc kẹt | Timed await + abort/recovery policy |
| Thấy BrokenBarrierException rồi cứ tiếp tục nghiệp vụ | Dữ liệu pha có thể thiếu | Xem generation thất bại, restart có điều phối |
| Phaser register rồi task bị reject | Party không bao giờ arrive | Deregister có ownership rõ |
| Phaser awaitAdvance khi party chưa arrive | Phase không tiến | Phân biệt arrive và awaitAdvance |
| Phaser arrive hai lần cùng phase | Vi phạm protocol | Một owner/phase, tránh double arrival |
| forceTermination rồi coi đã cancel worker | Worker vẫn có thể chạy | Hủy hợp tác và cleanup |
| Semaphore.acquire rồi release không ở finally | Exception gây permit leak | finally release đúng số permit |
| release trong finally dù acquire thất bại | Over-release, phá giới hạn | Cờ acquired hoặc try sau acquire |
| release sai số permit | Leak hoặc over-release | Acquire/release cùng số |
| Giữ lock trong lúc await tín hiệu do người cần lock tạo | Vòng deadlock | Phá dependency; chờ ngoài critical section |
| Task trong single worker chờ task con cùng pool | Thread-starvation deadlock | Composition / tránh blocking cùng worker |
| Timeout rồi trả success | Che lỗi completion | Failure/cancel/partial-state theo contract |
| Dùng sleep để chữa missing signal | Không tạo sự kiện cần thiết | Sửa protocol và happens-before |
| Nuốt InterruptedException rồi tiếp tục | Không phản hồi yêu cầu hủy | Propagate hoặc restore interrupt rồi thoát phù hợp |

## 28. Checklist review giao thức

**CountDownLatch**

- [ ] Count đúng với số nghĩa vụ completion thực sự?
- [ ] Mỗi nghĩa vụ chỉ countDown một lần?
- [ ] Đường exception, return, interrupt và task rejection được xử lý?
- [ ] Latch có nghĩa “done” hay “success”? Có result/error riêng?
- [ ] Waiter có deadline và nhánh timeout phù hợp?

**CyclicBarrier / Phaser**

- [ ] Số parties của mỗi phase/generation đúng?
- [ ] Mọi party có điểm arrive/await hợp lệ?
- [ ] Party rời workflow có deregister hoặc abort theo policy?
- [ ] Không register party không có worker rồi bỏ quên?
- [ ] Không double arrive/deregister?
- [ ] Có xử lý broken barrier, timeout, interruption?
- [ ] Nếu phase thất bại, downstream có biết và dừng hợp lý?

**Semaphore**

- [ ] Acquire thật sự thành công trước khi release?
- [ ] Release đúng số permit đã acquire?
- [ ] Toàn bộ critical section được bảo vệ bằng try/finally?
- [ ] Không có đường early-return bỏ release?
- [ ] Không có over-release phá giới hạn concurrency?
- [ ] Có timeout hoặc đường hủy phù hợp khi resource không có sẵn?

**Hệ thống**

- [ ] Không block giữ lock hoặc worker mà producer tín hiệu đang cần?
- [ ] Có telemetry về nghĩa vụ đã đăng ký, hoàn tất, thất bại?
- [ ] Không cho rằng timeout/cancel tự rollback I/O/DB?
- [ ] Đã test cả lỗi và thành công, không chỉ happy path?
- [ ] Có bất biến nghiệp vụ để biết “đã tiếp tục” là an toàn?

## 29. Tự kiểm tra

**1. Vì sao CountDownLatch có thể treo khi một worker ném exception?**  
Vì countDown không được gọi nếu đặt ở cuối try; latch không biết Thread đã chết. Cần completion protocol với finally hoặc failure channel phù hợp.

**2. Đặt countDown trong finally có luôn đúng không?**  
Chỉ khi countDown đại diện “đã kết thúc”. Nếu nó đại diện “đã thành công”, phải tách status và bảo đảm downstream không hiểu sai.

**3. CountDownLatch.await(20ms) trả false có làm counter giảm không?**  
Không. Count vẫn giữ nguyên; timeout là đường thoát của waiter.

**4. CyclicBarrier cần 3 party nhưng chỉ có 2 đến await; điều gì xảy ra nếu không có timeout/interrupt?**  
Hai party có thể chờ vô hạn nếu party thứ ba không bao giờ đến.

**5. Timed await trong CyclicBarrier timeout thì barrier ra sao?**  
Barrier bị broken; các waiter khác có thể nhận BrokenBarrierException. Phải xử lý failure/recovery có điều phối.

**6. Phaser.awaitAdvance(phase) có tự tính caller đã arrive không?**  
Không. Caller phải hoàn thành nghĩa vụ arrive bằng API phù hợp; awaitAdvance chỉ chờ phase đổi.

**7. Một party đã register rồi không start; Phaser có tự bỏ nó không?**  
Không. Coordinator cần xử lý registration không có worker, hoặc abort toàn workflow.

**8. Khi worker chỉ tham gia một phase rồi rời, API nào phù hợp?**  
arriveAndDeregister() theo protocol, thường trong finally nếu báo kết thúc cả đường lỗi, kết hợp báo lỗi riêng.

**9. Phaser.forceTermination() có dừng tất cả worker đang thực thi không?**  
Không. Nó chuyển phaser sang trạng thái terminated; worker phải tự hợp tác với cancellation và cleanup.

**10. Semaphore.acquire bị InterruptedException trước khi lấy permit; có được release không?**  
Không. Release khi chưa acquire làm permit tăng sai. Chỉ release nếu acquire thành công.

**11. Nếu Semaphore(2) bị gọi release ba lần nhưng chỉ acquire hai lần, điều gì sai?**  
Có thể tăng permit vượt 2, phá giới hạn concurrency theo thiết kế.

**12. Vì sao lock + latch.await có thể tạo deadlock vòng?**  
Nếu người phát countDown cần khóa đang được waiter giữ, hai bên phụ thuộc lẫn nhau.

**13. Một single-thread executor task submit child vào chính executor và gọi get thì sao?**  
Worker đang đợi child, child chờ worker rảnh; có thể tạo thread-starvation deadlock.

**14. Tại sao timeout không đủ để chứng minh sửa liveness?**  
Nó chỉ dừng chờ của caller; nghĩa vụ arrival/countDown/permit vẫn sai và trạng thái nghiệp vụ có thể thiếu.

**15. Các chỉ số getCount/getUnarrivedParties/availablePermits chứng minh đúng đắn không?**  
Chúng hỗ trợ chẩn đoán; phải phân tích nghĩa vụ/ownership, các đường exception và invariant, không chỉ nhìn snapshot.

## 30. Tổng kết

Thiếu một lời gọi **countDown(), arrive()/arriveAndDeregister() hoặc release()** có thể khiến các thread khác **không bao giờ tiến triển**. Nhưng gọi thừa hoặc báo tín hiệu sai ngữ nghĩa cũng nguy hiểm: latch có thể mở sớm, phaser vi phạm phase protocol, semaphore mất giới hạn tài nguyên.

Cách thiết kế đáng tin:

1. Ghi rõ **nghĩa vụ** của mỗi participant/task/permit và ai sở hữu.
2. Cấu trúc code với **try/finally** để cleanup và báo kết thúc theo đúng contract.
3. **Không phát tín hiệu thành công giả**; truyền failure/result ở kênh riêng.
4. **Không release nếu acquire chưa thành công**; tránh double-arrive, double-countDown.
5. Dùng **timeout + failure propagation + cancellation** để có đường thoát kiểm soát khi bên kia không thể tiếp tục.
6. Kiểm tra dependency graph để không giữ lock/worker mà người phát tín hiệu cần.
7. Test các đường **exception, interrupt, rejection và shutdown** bằng synchronization xác định, sau đó kiểm tra invariant nghiệp vụ.

Điểm cốt lõi không chỉ là “cho thread hết chờ”, mà là bảo đảm **mỗi sự kiện hoàn tất được phát đúng một lần, đúng ý nghĩa, đúng chủ sở hữu, và mọi thread tiếp tục trên một trạng thái hợp lệ**.


</details>

- [Quay lại đầu trang](#back-to-top)
