<a id="back-to-top"></a>

# Ngắt và hủy

## Menu
- [InterruptedException là tín hiệu gì](#interruption-and-cancellation-001)
- [Quan hệ giữa interrupt flag và phương thức chờ có thể bị ngắt](#interruption-and-cancellation-002)
- [Phân biệt Thread.interrupted với isInterrupted](#interruption-and-cancellation-003)
- [Lan truyền InterruptedException khi hợp đồng cho phép](#interruption-and-cancellation-004)
- [Khôi phục interrupt flag khi không thể tiếp tục lan truyền](#interruption-and-cancellation-005)
- [Dừng xử lý và cleanup sau khi nhận tín hiệu ngắt](#interruption-and-cancellation-006)
- [Vì sao không nên nuốt InterruptedException rồi tiếp tục như bình thường](#interruption-and-cancellation-007)
- [Phân biệt ngắt luồng, hủy Future và kết thúc cưỡng bức](#interruption-and-cancellation-008)
- [Giới hạn của timeout và cancellation đối với tác vụ bên dưới](#interruption-and-cancellation-009)

## <a id="interruption-and-cancellation-001">InterruptedException là tín hiệu gì</a>

<details>
<summary>Xem chi tiết</summary>

# `InterruptedException` là tín hiệu gì trong Java?

`InterruptedException` là **ngoại lệ có kiểm tra (checked exception)** thông báo rằng **luồng đang thực hiện một thao tác chờ có thể ngắt đã nhận yêu cầu interrupt**. Trong Java, interrupt là cơ chế **báo hiệu để luồng hợp tác dừng việc chờ hoặc thay đổi hướng xử lý**. Nó không phải lệnh cưỡng bức giết luồng, không mặc nhiên là lỗi nghiệp vụ, và cũng không tự bảo đảm tác vụ đã hủy xong.

Bài viết thuộc nhóm **Ngắt và hủy** của Java Core Exception, sử dụng Java 17+. Mục tiêu là hiểu tín hiệu interrupt, trạng thái cờ interrupt, xử lý `InterruptedException` đúng cách, sự khác nhau giữa ngắt luồng chờ và ngắt luồng làm việc, cũng như cách dùng trong ứng dụng đồng thời thực tế.

## 1. Hiểu đúng tín hiệu interrupt

Mỗi đối tượng `Thread` có **trạng thái interrupt** (thường gọi là *interrupt status* hoặc *interrupt flag*).

Khi một luồng A gọi:

```java
worker.interrupt();
```

thì về mặt khái niệm A gửi tín hiệu tới `worker`: **“hãy chú ý, có yêu cầu ngắt”**. Tùy thời điểm và thao tác `worker` đang làm, Java sẽ phản ứng khác nhau:

1. Nếu `worker` đang chạy mã tính toán bình thường, `interrupt()` thường đặt cờ interrupt của `worker` thành `true`; luồng vẫn có thể tiếp tục chạy cho đến khi chính mã của nó quan sát cờ và quyết định dừng.
2. Nếu `worker` đang trong một thao tác chờ **có thể ngắt** như `Thread.sleep(...)`, `Object.wait(...)`, `Thread.join(...)`, `BlockingQueue.take()` hoặc `CountDownLatch.await()`, thao tác có thể kết thúc sớm bằng `InterruptedException`.
3. Với các thao tác chờ thông dụng như `sleep`, `wait`, `join`, khi `InterruptedException` được ném, **trạng thái interrupt bị xóa**. Nếu mã bắt ngoại lệ mà không truyền tiếp hoặc khôi phục tín hiệu, tầng gọi cao hơn có thể không còn nhận ra yêu cầu ngắt.
4. Nếu luồng bị kẹt trong thao tác không hỗ trợ interrupt, hoặc cố tình phớt lờ tín hiệu, gọi `interrupt()` **không bảo đảm** luồng dừng. Một số API I/O có quy tắc riêng, nên phải xem hợp đồng của thao tác cụ thể.

Cần phân biệt ba khái niệm:

| Khái niệm | Ý nghĩa |
|---|---|
| `Thread.interrupt()` | Gửi yêu cầu interrupt đến một luồng; không ép nó chấm dứt |
| `Thread.isInterrupted()` | Kiểm tra trạng thái interrupt của **đối tượng luồng**, không tự xóa cờ |
| `Thread.interrupted()` | Phương thức `static` kiểm tra **luồng hiện tại** và **xóa cờ** interrupt của luồng đó |

`InterruptedException` không tương đương `ThreadDeath`, không tự đồng nghĩa với `CancellationException` và không chứng minh chương trình đang gặp sự cố. Trong nhiều hệ thống, ngắt là tín hiệu điều phối vòng đời: đóng ứng dụng, hủy tác vụ, ngừng consumer, dừng chờ khi request hết hiệu lực.

## 2. Luồng phát tín hiệu và luồng nhận tín hiệu

Giả sử luồng `main` khởi tạo một worker đang chờ dữ liệu:

```text
main                                     worker
----                                     ------
new Thread(...) ──────────────────────►  bắt đầu chạy
start()                                  gọi await()/sleep()/take()
                                           │
worker.interrupt() ─── tín hiệu ───────►  thao tác chờ nhận interrupt
                                           │
                                           ▼
                                       InterruptedException
                                           │
                                       thoát hoặc xử lý tiếp
worker.join() ─── chờ worker kết thúc ──►  kết thúc
```

`InterruptedException` được ném **tại luồng đang gọi thao tác chờ**. Nó không tự động được “ném ngược” sang luồng đã gọi `interrupt()`.

Nếu luồng A gọi `worker.interrupt()` trong khi luồng B đang `worker.join()`, **worker là đối tượng bị yêu cầu ngắt**. Lời gọi `join()` của B chỉ tự ném `InterruptedException` nếu **chính B bị interrupt** trong lúc đợi. Việc worker bị interrupt không mặc nhiên làm B bị interrupt.

Đối với thao tác ngủ:

```java
try {
    Thread.sleep(10_000);
} catch (InterruptedException e) {
    // Chính luồng đang ngủ nhận tín hiệu ngắt.
}
```

Đây là **đoạn minh họa trong thân phương thức**, không phải một tệp Java độc lập. `sleep(...)` dừng chờ sớm khi nhận interrupt; câu lệnh `catch` chạy trong cùng luồng vừa bị ngắt.

## 3. Ví dụ chạy được: bắt tín hiệu và khôi phục cờ interrupt

Tạo tệp `InterruptSignalDemo.java`:

```java
import java.util.concurrent.CountDownLatch;

public class InterruptSignalDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            System.out.println("worker: sẵn sàng");
            ready.countDown();

            try {
                new CountDownLatch(1).await();
                System.out.println("worker: hoàn thành bình thường");
            } catch (InterruptedException e) {
                System.out.println("worker: bắt ngoại lệ, flag="
                        + Thread.currentThread().isInterrupted());

                Thread.currentThread().interrupt();

                System.out.println("worker: khôi phục, flag="
                        + Thread.currentThread().isInterrupted());
            }
        }, "demo-worker");

        worker.start();
        ready.await();
        worker.interrupt();
        worker.join();

        System.out.println("main: worker alive=" + worker.isAlive());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac InterruptSignalDemo.java
java InterruptSignalDemo
```

Kết quả chuẩn:

```text
worker: sẵn sàng
worker: bắt ngoại lệ, flag=false
worker: khôi phục, flag=true
main: worker alive=false
```

**Vì sao kết quả ổn định?**

- `ready.await()` buộc `main` chờ đến lúc worker phát tín hiệu đã sẵn sàng. Worker có thể vào `await()` trước hoặc sau lời gọi `interrupt()` của `main`, nhưng thao tác `await()` sẽ phản ứng với trạng thái interrupt trong cả hai trường hợp.
- Trong `catch`, `flag=false` vì việc ném `InterruptedException` từ `CountDownLatch.await()` đã xóa cờ ngắt. Đây là hành vi của API chờ có thể ngắt đó.
- `Thread.currentThread().interrupt()` trong nhánh `catch` **khôi phục** cờ cho worker. Vì worker sắp kết thúc nên không có vòng lặp tiếp tục tiêu thụ tín hiệu.
- `worker.join()` bảo đảm main chỉ in dòng cuối **sau khi worker đã chấm dứt**. `Thread.isAlive()` do đó là `false`.

Ví dụ này cố tình dùng `CountDownLatch` thay cho việc đoán thứ tự chạy bằng `Thread.sleep(...)`, vì lịch thực thi các luồng không được bảo đảm.

Lưu ý: cờ interrupt được khôi phục là một **trạng thái trên Thread**, không phải bằng chứng worker sẽ chấm dứt. Worker ở đây chấm dứt vì đoạn mã chạy đến cuối.

## 4. Vì sao `InterruptedException` là checked exception?

`InterruptedException` kế thừa `Exception`, không kế thừa `RuntimeException`. Vì vậy các phương thức có thể ném nó thường khai báo `throws InterruptedException`; người gọi phải **bắt** hoặc **khai báo truyền tiếp** ngoại lệ.

Ví dụ dưới đây là một phương thức hoàn chỉnh có thể đặt trong một class Java:

```java
static void pause() throws InterruptedException {
    Thread.sleep(1_000);
}
```

Khi caller cũng có khả năng phản ứng đúng với interrupt, **truyền tiếp** thường là lựa chọn đơn giản và rõ ràng nhất:

```java
static String waitForResult(java.util.concurrent.Future<String> future)
        throws InterruptedException, java.util.concurrent.ExecutionException {
    return future.get();
}
```

Nếu không thể truyền checked exception qua API hiện tại, có thể bắt, **khôi phục cờ**, rồi thoát theo chiến lược của tầng ứng dụng:

```java
try {
    Thread.sleep(1_000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Công việc đang chờ bị yêu cầu ngắt", e);
}
```

Đoạn `try/catch` này là **mã minh họa trong thân phương thức**. Trong ứng dụng thực tế, kiểu exception được chuyển đổi phải phù hợp hợp đồng của hàm. Việc `throw` còn giúp tránh chạy tiếp như thể không có tín hiệu ngắt.

Quy tắc ra quyết định:

- **Có thể khai báo `throws InterruptedException`**: thường truyền ngoại lệ lên caller, để caller quyết định hủy hoặc kết thúc. Việc khôi phục cờ trước khi truyền không mặc nhiên cần thiết.
- **Đã bắt ngoại lệ để kết thúc tác vụ tại đây**: thoát vòng lặp hoặc trả về; khôi phục cờ nếu cần báo trạng thái interrupt cho tầng phía trên.
- **Phải đổi sang ngoại lệ khác**: thường gọi `Thread.currentThread().interrupt()` trước khi ném ngoại lệ mới và **giữ cause** là `e`.
- **Chủ đích xử lý và tiếp tục**: chỉ làm nếu hợp đồng ứng dụng thực sự cho phép, và đã quyết định rõ có giữ hay tiêu thụ tín hiệu. Đây là lựa chọn đặc biệt, không phải mặc định.

Một anti-pattern phổ biến:

```java
try {
    Thread.sleep(1_000);
} catch (InterruptedException ignored) {
    // Bỏ qua: bên yêu cầu ngắt mất tín hiệu mà không biết.
}
```

Nếu sau đó chương trình tiếp tục xử lý dài hoặc chờ vô hạn, tác vụ có thể không đáp ứng yêu cầu dừng. Dòng `ignored` không tự giải quyết ngắt; thông thường cần truyền tiếp, khôi phục cờ và thoát, hoặc có chính sách xử lý riêng được giải thích rõ.

## 5. `isInterrupted()` khác `Thread.interrupted()` thế nào?

`Thread.currentThread().isInterrupted()` đọc cờ của luồng hiện tại mà không xóa. `Thread.interrupted()` là phương thức tĩnh của `Thread`: đọc rồi **xóa cờ của luồng đang chạy lệnh này**.

Ví dụ độc lập `InterruptFlagDemo.java`:

```java
public class InterruptFlagDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread thread = new Thread(() -> {
            Thread.currentThread().interrupt();

            System.out.println("isInterrupted #1: "
                    + Thread.currentThread().isInterrupted());
            System.out.println("Thread.interrupted(): "
                    + Thread.interrupted());
            System.out.println("isInterrupted #2: "
                    + Thread.currentThread().isInterrupted());
        });

        thread.start();
        thread.join();
    }
}
```

Chạy:

```bash
javac InterruptFlagDemo.java
java InterruptFlagDemo
```

Kết quả chính xác:

```text
isInterrupted #1: true
Thread.interrupted(): true
isInterrupted #2: false
```

Giải thích:

1. Worker tự gọi `interrupt()`, nên cờ được đặt `true`.
2. `isInterrupted()` chỉ kiểm tra, nên vẫn là `true`.
3. `Thread.interrupted()` vừa trả `true` vừa **xóa cờ**.
4. Lần `isInterrupted()` cuối vì thế trả `false`.

Đừng viết `otherThread.interrupted()` với ý định kiểm tra trạng thái ngắt của `otherThread`: cú pháp đó gọi **phương thức tĩnh** và kiểm tra **luồng đang chạy**, dù viết qua tên biến đối tượng. Muốn hỏi trạng thái của luồng khác, dùng `otherThread.isInterrupted()`.

Vì `Thread.interrupted()` tiêu thụ tín hiệu, hãy cân nhắc kỹ trước khi dùng trong logging, tiện ích kiểm tra hoặc điều kiện vòng lặp. Nếu chỉ cần quan sát, ưu tiên `isInterrupted()`.

## 6. Những thao tác nào thường ném `InterruptedException`?

| API tiêu biểu | Luồng nào nhận `InterruptedException`? | Điều đang bị ngắt |
|---|---|---|
| `Thread.sleep(...)` | Luồng đang ngủ | Chờ hết thời gian ngủ |
| `Object.wait(...)` | Luồng đang đợi trên monitor | Chờ notify hoặc thời điểm timeout |
| `Thread.join(...)` | Luồng đang **gọi** `join()` | Chờ luồng khác kết thúc |
| `CountDownLatch.await()` | Luồng đang gọi `await()` | Chờ latch về 0 |
| `BlockingQueue.take()` / `put(...)` | Luồng lấy hoặc thêm phần tử | Chờ queue có phần tử hoặc còn chỗ |
| `Future.get()` | Luồng đang lấy kết quả | Chờ tác vụ kết thúc |
| `Lock.lockInterruptibly()` | Luồng đang xin khóa | Chờ lấy khóa |
| `Semaphore.acquire()` | Luồng đang xin permit | Chờ permit |
| `ExecutorService.awaitTermination(...)` | Luồng đang chờ executor đóng | Chờ các tác vụ đã gửi kết thúc |

Điều quan trọng không nằm ở việc có tạo thread hay không, mà nằm ở **thao tác chờ cụ thể**. Một phương thức thuần CPU như tính tổng vòng lặp sẽ không tự sinh `InterruptedException` khi cờ interrupt được đặt. Mã tính toán muốn hỗ trợ hủy hợp tác thường phải tự quan sát cờ hoặc gọi thao tác có khả năng ngắt.

Ví dụ vòng lặp CPU:

```java
static long countUntilInterrupted() {
    long counter = 0;
    while (!Thread.currentThread().isInterrupted()) {
        counter++;
    }
    return counter;
}
```

Đây là **phương thức minh họa**, không phải ví dụ chạy đầy đủ: số vòng đếm tùy thời điểm và môi trường. Nếu vòng lặp chứa thao tác chờ ném `InterruptedException`, cần bổ sung xử lý/truyền ngoại lệ ở vị trí thao tác ấy.

Một số API khác có phản ứng interrupt khác nhau. Chẳng hạn các thao tác I/O kênh có thể đóng channel hoặc ném ngoại lệ chuyên biệt thay vì `InterruptedException`. Vì vậy đừng áp quy tắc “mọi chỗ bị ngắt đều ném `InterruptedException`” cho toàn bộ Java.

## 7. Vòng đời worker: dừng hợp tác với `BlockingQueue.take()`

Giả sử một worker đang đợi công việc từ hàng đợi. Một chính sách shutdown hợp lý là yêu cầu interrupt, worker thoát khỏi `take()`, chạy `finally` để dọn dẹp, rồi kết thúc.

Tạo tệp `QueueInterruptDemo.java`:

```java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

public class QueueInterruptDemo {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        CountDownLatch ready = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            try {
                System.out.println("consumer: đang chờ");
                ready.countDown();
                String item = queue.take();
                System.out.println("consumer: nhận " + item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("consumer: đã nhận tín hiệu dừng");
            } finally {
                System.out.println("consumer: dọn dẹp");
            }
        }, "queue-consumer");

        consumer.start();
        ready.await();
        consumer.interrupt();
        consumer.join();

        System.out.println("main: hoàn tất");
    }
}
```

Chạy:

```bash
javac QueueInterruptDemo.java
java QueueInterruptDemo
```

Kết quả:

```text
consumer: đang chờ
consumer: đã nhận tín hiệu dừng
consumer: dọn dẹp
main: hoàn tất
```

`BlockingQueue.take()` chờ đến khi có phần tử hoặc bị ngắt. Hàng đợi trong ví dụ không được thêm phần tử; sau tín hiệu `interrupt()`, worker thoát `take()` bằng `InterruptedException`. `finally` chạy để xử lý tài nguyên. `join()` bảo đảm main chỉ in sau khi worker kết thúc.

Trong dịch vụ thực, `finally` phải dọn đúng tài nguyên mà worker sở hữu: khóa, stream, kết nối, transaction hoặc trạng thái nghiệp vụ tùy trường hợp. Cần thiết kế dọn dẹp **an toàn khi bị yêu cầu hủy**; không giả định mọi tác vụ đang dở đều có thể bỏ mà không cần rollback hoặc xử lý bù.

### Vòng lặp consumer nên bắt ở đâu?

Với một worker đọc queue liên tục, mẫu thường gặp:

```java
static void consume(java.util.concurrent.BlockingQueue<String> queue) {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            String message = queue.take();
            process(message);
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        releaseResources();
    }
}
```

Đây là **mẫu phác thảo**, giả định ứng dụng đã định nghĩa `process(String)` và `releaseResources()`; không phải chương trình Java độc lập. Mã sử dụng `take()` để ngừng chờ khi không còn dữ liệu. Sau `catch`, phương thức kết thúc. Nếu `process` tự chặn hoặc ném lỗi, cần thêm xử lý phù hợp với hợp đồng thực tế của nó.

Sai lầm điển hình là đặt `catch` **bên trong** vòng lặp nhưng vẫn tiếp tục:

```java
// Minh họa lỗi thiết kế, không dùng như vòng lặp production.
while (true) {
    try {
        queue.take();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        // Sau đó lặp lại: take() lại bị ngắt ngay, có thể tạo vòng lặp nóng.
    }
}
```

Khi cờ đã được khôi phục, lần chờ có thể ngắt tiếp theo thường lập tức ném `InterruptedException`; vòng lặp có nguy cơ chiếm CPU và không thể shutdown. **Khôi phục cờ phải đi cùng hành động thích hợp**, chẳng hạn thoát vòng lặp hoặc truyền lỗi lên trên.

## 8. Liên hệ với `ExecutorService`, `Future` và hủy tác vụ

Trong executor có **hai luồng cần phân biệt**:

- **Worker** đang chạy `Callable.call()` hoặc `Runnable.run()`.
- **Caller** đang gọi `Future.get()` để chờ worker hoàn thành.

Khi caller đang ở `get()` và **caller bị interrupt**, caller nhận `InterruptedException` trực tiếp. Tác vụ worker **không mặc nhiên bị hủy**. Ngược lại, nếu worker nhận interrupt trong một thao tác chờ và `Callable` để `InterruptedException` thoát ra, tác vụ sẽ được đánh dấu kết thúc thất bại và `Future.get()` của caller thường ném `ExecutionException` có cause là `InterruptedException` — **nếu Future chưa bị chuyển sang trạng thái hủy**.

Ví dụ độc lập `FutureWorkerInterruptDemo.java`:

```java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FutureWorkerInterruptDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<String> future = executor.submit(() -> {
                Thread.currentThread().interrupt();
                Thread.sleep(1_000);
                return "thành công";
            });

            try {
                System.out.println(future.get());
            } catch (ExecutionException e) {
                System.out.println("cause = "
                        + e.getCause().getClass().getSimpleName());
            }
        } finally {
            executor.shutdown();
        }
    }
}
```

Chạy:

```bash
javac FutureWorkerInterruptDemo.java
java FutureWorkerInterruptDemo
```

Kết quả:

```text
cause = InterruptedException
```

`Thread.sleep(...)` thấy cờ interrupt và ném `InterruptedException` trong worker. `Callable` không bắt ngoại lệ đó; executor lưu thất bại của tác vụ. Caller gọi `get()` nhận `ExecutionException`, trong đó `getCause()` là `InterruptedException` của worker. Caller **không nhận `InterruptedException` trực tiếp** trong tình huống này vì chính caller không bị interrupt.

Đối với hủy Future:

```java
boolean accepted = future.cancel(true);
```

Đây là đoạn mã minh họa, cần có biến `future` thuộc loại `Future<?>` trong phạm vi. Nếu hủy thành công, Future được đánh dấu canceled và `get()` ném **`CancellationException`**; `cancel(true)` cho phép yêu cầu interrupt worker đang chạy, nhưng không bảo đảm worker sẽ ngừng ngay. Khi `cancel(false)`, không yêu cầu ngắt worker đang thực thi. Việc ngắt worker và trạng thái Future **có quan hệ nhưng không phải cùng một khái niệm**.

Với executor:

- `shutdown()` dừng nhận công việc mới và để các công việc đã nhận tiếp tục thực thi; không tự động interrupt tất cả worker và không tự đợi chúng kết thúc.
- `shutdownNow()` thử dừng những tác vụ đang hoạt động (thường bằng interrupt), ngừng xử lý những tác vụ đang đợi và trả danh sách tác vụ chưa bắt đầu. Đây là **best effort**, không bảo đảm tác vụ phớt lờ interrupt sẽ dừng.
- `awaitTermination(...)` giúp caller đợi executor đóng và cũng có thể ném `InterruptedException` nếu caller bị ngắt.

Thiết kế lifecycle phải tách **yêu cầu dừng** khỏi **xác nhận đã kết thúc** (`join()`, `awaitTermination()`, hoặc cơ chế trạng thái hoàn tất).

## 9. Cờ interrupt bị xóa: nguyên tắc và ranh giới

Nhiều thao tác chờ được định nghĩa là **ném `InterruptedException` và xóa trạng thái interrupt**. Điều này giải thích vì sao `isInterrupted()` có thể là `false` trong nhánh `catch`.

Tuy nhiên, quy tắc “thấy interrupt là luôn tự xóa” là sai. Cờ thường vẫn được đặt khi luồng chỉ thực thi mã tính toán; `isInterrupted()` không xóa; `Thread.interrupted()` xóa theo chủ đích; các API I/O và đồng bộ khác có thể xử lý riêng. Đọc tài liệu của API đang dùng là cần thiết nếu việc duy trì cờ ảnh hưởng logic.

Mẫu xử lý an toàn ở lớp trung gian:

```java
static boolean waitUntilReady(java.util.concurrent.CountDownLatch latch) {
    try {
        latch.await();
        return true;
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
    }
}
```

Đây là một **phương thức hợp lệ**: khi bị interrupt, trả `false` để caller biết chờ không thành công **và giữ cờ interrupt**. Dù vậy, nếu thiết kế API cho phép `throws InterruptedException`, truyền tiếp thường minh bạch hơn so với mã hóa kết quả trong `boolean`.

Không nên gọi `Thread.interrupted()` chỉ để kiểm tra xem worker bị ngắt chưa rồi quên rằng nó đã xóa cờ. Nếu API cần chủ động tiêu thụ tín hiệu interrupt, phải ghi rõ quy tắc đó trong hợp đồng phương thức.

## 10. `InterruptedException` và `CancellationException`, `TimeoutException`

Ba loại sự kiện dễ bị nhầm:

| Tín hiệu | Tác nhân/tình huống | Hành vi cần nghĩ tới |
|---|---|---|
| `InterruptedException` | Luồng hiện tại bị interrupt khi thao tác có thể ngắt | Tôn trọng yêu cầu ngắt: truyền tiếp, khôi phục cờ rồi kết thúc, hoặc xử lý theo hợp đồng |
| `CancellationException` | Future/operation đã được đánh dấu hủy theo API của nó | Xử lý trạng thái canceled; không giả định mọi worker đã hoàn tất dọn dẹp |
| `TimeoutException` | Thao tác chờ có timeout không có kết quả trong thời hạn | Quyết định thử lại, báo quá hạn hay chủ động gọi cancel; timeout không tự đồng nghĩa với interrupt |

Ví dụ, `future.get(500, TimeUnit.MILLISECONDS)` có thể ném `TimeoutException` vì tác vụ còn chạy; lúc này caller có thể chọn `future.cancel(true)`. Còn `future.get()` có thể ném `InterruptedException` nếu caller bị ngắt khi chờ, dù tác vụ sau đó vẫn hoàn thành bình thường.

Khi thiết kế API, cần làm rõ **ngắt là yêu cầu đóng toàn bộ tác vụ**, **chỉ yêu cầu dừng chờ**, hay **một lựa chọn hủy trong luồng điều phối**. Không có một chính sách duy nhất đúng cho mọi hệ thống.

## 11. Các lỗi phổ biến và cách sửa

### 11.1. Nuốt interrupt rồi tiếp tục như bình thường

Sai:

```java
try {
    queue.take();
} catch (InterruptedException e) {
    System.out.println("Có ngắt nhưng bỏ qua");
}
```

Hậu quả: caller yêu cầu dừng nhưng tín hiệu bị mất; tác vụ vẫn có thể tiếp tục chờ hoặc xử lý.

Tùy thiết kế, sửa bằng cách truyền `InterruptedException` lên ngoài, hoặc khôi phục cờ và **thoát**:

```java
try {
    queue.take();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return; // Ví dụ bên trong một phương thức void.
}
```

Hai đoạn là **mảnh mã đặt trong phương thức**, với `queue` là một `BlockingQueue` đã tồn tại.

### 11.2. Gọi `Thread.interrupted()` khi chỉ định kiểm tra

```java
boolean wasInterrupted = Thread.interrupted();
```

Lệnh trên **xóa cờ của luồng hiện tại**. Nếu chỉ định quan sát, dùng:

```java
boolean interrupted = Thread.currentThread().isInterrupted();
```

Đừng trông đợi mã khác trong cùng luồng thấy cờ như trước sau khi bạn đã gọi `Thread.interrupted()`.

### 11.3. Khôi phục cờ nhưng không dừng, tạo vòng lặp bận

Khôi phục cờ rồi lặp lại một thao tác ném `InterruptedException` ngay có thể làm CPU tăng và worker không chịu kết thúc. Sửa bằng cách thoát vòng lặp, propagate, hoặc thiết kế quy tắc khôi phục và xử lý trạng thái rõ ràng.

### 11.4. Tưởng `interrupt()` giết worker ngay tức thì

Một tác vụ CPU không quan sát cờ, thao tác chặn không hỗ trợ ngắt, hoặc worker bắt rồi nuốt ngoại lệ có thể tiếp tục chạy. Muốn dừng có kiểm soát, phải thiết kế điểm kiểm tra cờ, các thao tác chờ có thể ngắt và tài nguyên cần dọn.

### 11.5. Nhầm luồng bị ngắt khi gọi `join()` hoặc `get()`

Nếu main bị interrupt khi gọi `worker.join()` hoặc `future.get()`, `InterruptedException` xuất hiện **trong main**. Nó không có nghĩa worker cũng đã bị interrupt. Khi cần hủy worker, phải gửi tín hiệu/hủy một cách riêng biệt và cân nhắc hậu quả.

### 11.6. Bắt `Exception` hoặc `Throwable` quá rộng

Trong vòng lặp worker, viết `catch (Exception e)` rồi tiếp tục thường vô tình bắt luôn `InterruptedException`, gây mất tín hiệu dừng. Nên bắt `InterruptedException` riêng và xử lý vòng đời có chủ đích; những lỗi nghiệp vụ khác có chính sách log/retry độc lập.

### 11.7. Dùng `Thread.stop()` để ép dừng

`Thread.stop()` là API lỗi thời, nguy hiểm vì có thể chấm dứt luồng khi trạng thái chia sẻ đang không nhất quán. Không dùng thay cho interrupt; thiết kế dừng hợp tác, đóng tài nguyên và cơ chế timeout phù hợp.

### 11.8. Trộn tín hiệu ngắt với lỗi nghiệp vụ

Một tác vụ bị ngắt khi chờ thường nên thoát sớm hoặc báo hủy; không nên mặc định ghi log mức lỗi nghiêm trọng như thể ứng dụng gặp bug. Tuy nhiên, nếu ngắt xuất hiện ngoài dự kiến trong một luồng không được phép dừng, cần điều tra nguyên nhân gửi interrupt và tác động đến tính đúng đắn của nghiệp vụ.

## 12. Quy trình chọn cách xử lý

```text
Thao tác chờ ném InterruptedException
                    │
                    ▼
       Phương thức có thể truyền tiếp?
              ┌─────┴─────┐
             Có          Không
              │            │
      khai báo throws       ▼
      InterruptedException  Có xử lý để thoát tại đây?
                           ┌┴─────────┐
                          Có         Không
                           │           │
                    restore flag       ▼
                    khi phù hợp     Chuyển lỗi theo hợp đồng,
                    và kết thúc     restore flag, giữ cause
```

Thực hành tốt là **xem interrupt như yêu cầu hợp tác**: nhận tín hiệu tại điểm chờ hoặc điểm kiểm tra, bảo toàn thông tin để tầng có trách nhiệm quyết định, kết thúc công việc an toàn, rồi xác nhận trạng thái đã hoàn tất nếu cần.

Checklist khi review code đồng thời:

- Có biết **luồng nào** được `interrupt()` và **luồng nào** đang đợi?
- Các thao tác dài hạn có cơ chế chờ có thể ngắt, timeout hoặc kiểm tra cờ?
- `InterruptedException` được truyền tiếp hay khôi phục cờ và thoát? Có nhánh bắt rộng vô tình nuốt nó không?
- `Thread.interrupted()` có được dùng với chủ đích xóa cờ?
- Sau `cancel(true)`/`shutdownNow()`, ứng dụng có tiếp tục theo dõi hoàn tất và dọn dẹp?
- Có phân biệt trạng thái hủy Future với ngoại lệ phát sinh bên trong worker?
- Các ví dụ/test có điều phối bằng latch, join hoặc đồng bộ thích hợp thay vì dựa vào thời gian sleep may rủi?

**Ghi nhớ:** `InterruptedException` chủ yếu là **tín hiệu rằng luồng hiện tại phải xem lại việc chờ/tiếp tục làm việc**. Phản ứng phù hợp thường là truyền tín hiệu lên caller hoặc khôi phục cờ rồi kết thúc an toàn. Chỉ gọi `interrupt()` chưa chứng minh worker đã dừng; chỉ bắt `InterruptedException` chưa có nghĩa đã xử lý xong yêu cầu hủy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-002">Quan hệ giữa interrupt flag và phương thức chờ có thể bị ngắt</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa interrupt flag và phương thức chờ có thể bị ngắt trong Java

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Hiểu interrupt là **tín hiệu yêu cầu hợp tác** với luồng đang chạy, không phải lệnh cưỡng bức kết thúc luồng.
- Phân biệt <code>thread.interrupt()</code>, <code>thread.isInterrupted()</code> và <code>Thread.interrupted()</code>, đặc biệt tác dụng **xóa cờ ngắt** của phương thức cuối.
- Giải thích điều gì xảy ra khi một luồng đang chờ ở <code>Thread.sleep()</code>, <code>Object.wait()</code>, <code>Thread.join()</code>, <code>BlockingQueue.take()</code>, <code>Condition.await()</code> hoặc <code>Lock.lockInterruptibly()</code> nhận interrupt.
- Biết rằng các phương thức chờ ném <code>InterruptedException</code> thường đã **xóa interrupt flag khi ném lỗi**; lựa chọn truyền ngoại lệ lên hoặc khôi phục cờ phải phù hợp hợp đồng phương thức.
- Viết code chờ/hủy an toàn, không để luồng quay vòng vô hạn, không nhả khóa chưa sở hữu và không vô tình nuốt yêu cầu hủy.
- Tự kiểm tra hành vi interrupt qua các chương trình Java 17+ có kết quả xác định.

## 2. Interrupt flag là gì?

Mỗi <code>Thread</code> trong Java có trạng thái ngắt (thường được gọi là **interrupt flag** hoặc *interrupted status*). Khi một luồng gọi <code>target.interrupt()</code>, nó gửi tín hiệu ngắt tới <code>target</code>.

Thông thường, nếu <code>target</code> đang chạy code tính toán không kiểm tra cờ, code **không tự động dừng**. Nếu luồng đang chờ trong một phương thức có hợp đồng phản ứng với interrupt, lời gọi chờ có thể kết thúc bằng <code>InterruptedException</code>, hoặc một hình thức phản ứng khác theo API đó.

Ví dụ trực giác:

~~~text
Luồng điều phối                     Luồng worker
       |                                  |
       |                          đang làm việc/chờ
       |                                  |
       +---- worker.interrupt() --------->|
                                          |
                            API kiểm tra trạng thái ngắt
                                          |
                         +----------------+----------------+
                         |                                 |
                  chờ interruptible                 tác vụ tính toán
                         |                                 |
             InterruptedException                  cần chủ động kiểm tra
                và thường xóa cờ                     isInterrupted()
                         |                                 |
             bắt/truyền/khôi phục                    quyết định dừng
                theo hợp đồng                        theo hợp đồng
~~~

Không nên coi cờ ngắt như một biến <code>boolean</code> do ứng dụng tùy tiện đặt/xóa. Java cung cấp API để gửi và quan sát trạng thái đó. Cũng không nên diễn giải rằng interrupt là ngoại lệ được “ném từ luồng A sang luồng B” một cách trực tiếp: <code>interrupt()</code> là **tín hiệu**, còn ngoại lệ có được ném hay không là quyết định của API đang chạy trong luồng nhận tín hiệu.

### Một luồng đang RUNNABLE có thể phớt lờ interrupt

~~~java
while (true) {
    doCpuWork();
}
~~~

Đây là **mã minh họa**; <code>doCpuWork()</code> chỉ đại diện cho tính toán. Nếu vòng lặp không kiểm tra trạng thái ngắt và công việc không gọi API có phản ứng với interrupt, lời gọi <code>thread.interrupt()</code> từ luồng khác không buộc vòng lặp phải dừng.

Mẫu hợp tác:

~~~java
while (!Thread.currentThread().isInterrupted()) {
    doCpuWork();
}
~~~

Đây cũng là **mã minh họa** trong phương thức có sẵn <code>doCpuWork()</code>. Kiểm tra cờ giúp vòng lặp chọn dừng ở điểm phù hợp. Nếu công việc thực tế có thao tác ghi dữ liệu, trước khi kết thúc vẫn phải giải phóng tài nguyên và bảo vệ bất biến trạng thái.

## 3. Ba API quan trọng: gửi tín hiệu, kiểm tra và tiêu thụ cờ

| Biểu thức | Tác dụng | Có xóa interrupt flag không? |
| --- | --- | --- |
| <code>target.interrupt()</code> | Yêu cầu ngắt luồng <code>target</code>; hệ quả phụ thuộc trạng thái/API chờ | Không phải API dùng để xóa cờ |
| <code>target.isInterrupted()</code> | Đọc trạng thái ngắt của đối tượng luồng được chỉ định | **Không** |
| <code>Thread.interrupted()</code> | Đọc trạng thái ngắt của **luồng đang gọi** | **Có**: trả về trạng thái cũ rồi xóa |
| <code>Thread.currentThread().interrupt()</code> | Gửi tín hiệu ngắt tới **chính luồng hiện tại**; thường dùng để khôi phục cờ sau khi catch | Thiết lập tín hiệu ngắt, không xóa |

**Điểm dễ nhầm:** <code>Thread.interrupted()</code> là phương thức **static**. Nó luôn kiểm tra cờ của **luồng hiện tại**, bất kể bạn định dùng nó như một lời gọi trên đối tượng luồng nào. Để quan sát trạng thái của một đối tượng <code>Thread</code> cụ thể mà không xóa cờ, dùng <code>target.isInterrupted()</code>.

Một phép đọc không phá hủy trạng thái:

~~~java
boolean requested = Thread.currentThread().isInterrupted();
~~~

Một phép đọc đồng thời **tiêu thụ** trạng thái:

~~~java
boolean wasInterrupted = Thread.interrupted();
~~~

Nếu <code>Thread.interrupted()</code> trả về <code>true</code>, một lần đọc ngay sau đó thường trả về <code>false</code> nếu chưa nhận interrupt mới. Điều này ảnh hưởng trực tiếp tới những API chờ sau đó: chúng có thể không còn nhìn thấy yêu cầu ngắt vừa bị xóa.

### Ví dụ Java 17+: quan sát cách cờ được thay đổi

Lưu tệp **InterruptFlagDemo.java**:

~~~java
public class InterruptFlagDemo {
    public static void main(String[] args) {
        Thread current = Thread.currentThread();

        current.interrupt();
        System.out.println("Sau interrupt(): " + current.isInterrupted());

        try {
            Thread.sleep(1_000);
            System.out.println("Sleep hoàn tất");
        } catch (InterruptedException e) {
            System.out.println("Sleep ném InterruptedException");
            System.out.println("Cờ trong catch: " + current.isInterrupted());
        }

        current.interrupt();
        System.out.println("Trước interrupted(): " + current.isInterrupted());
        System.out.println("Thread.interrupted(): " + Thread.interrupted());
        System.out.println("Sau interrupted(): " + current.isInterrupted());
    }
}
~~~

Biên dịch và chạy bằng Java 17 hoặc mới hơn:

~~~bash
javac -encoding UTF-8 InterruptFlagDemo.java
java InterruptFlagDemo
~~~

Kết quả stdout trong lần chạy thông thường:

~~~text
Sau interrupt(): true
Sleep ném InterruptedException
Cờ trong catch: false
Trước interrupted(): true
Thread.interrupted(): true
Sau interrupted(): false
~~~

**Giải thích từng bước:**

1. <code>current.interrupt()</code> đặt cờ ngắt cho chính luồng <code>main</code>, vì <code>current</code> tham chiếu đến <code>Thread.currentThread()</code>.
2. <code>Thread.sleep(1_000)</code> thấy luồng hiện tại đã bị ngắt và ném <code>InterruptedException</code> thay vì ngủ đủ thời gian yêu cầu.
3. Theo hợp đồng của <code>Thread.sleep()</code>, **interrupted status được xóa khi ngoại lệ đó được ném**. Vì vậy trong <code>catch</code>, <code>current.isInterrupted()</code> trả về <code>false</code> nếu không có interrupt mới.
4. Chương trình chủ động đặt cờ lại một lần nữa để minh họa <code>Thread.interrupted()</code>.
5. <code>Thread.interrupted()</code> trả về <code>true</code> và đồng thời xóa cờ, nên phép <code>isInterrupted()</code> cuối trả về <code>false</code>.

Ví dụ cố tình không khôi phục cờ trong khối <code>catch</code> đầu tiên để hiển thị rõ hợp đồng của <code>sleep()</code>. Trong code nghiệp vụ, quyết định khôi phục hay truyền <code>InterruptedException</code> phải theo các quy tắc ở phần sau.

## 4. InterruptException và trạng thái ngắt là hai kênh báo hiệu khác nhau

<code>InterruptedException</code> là **checked exception**. Một số phương thức chờ sử dụng nó để thông báo rằng yêu cầu ngắt đã cắt ngang thao tác chờ.

Một diễn biến điển hình:

~~~text
Cờ = false
   |
   | Luồng khác gọi interrupt()
   v
Cờ = true
   |
   | Luồng đang gọi phương thức chờ interruptible
   v
Phương thức phát hiện interrupt
   |
   | ném InterruptedException
   v
Cờ = false (với hợp đồng các phương thức chờ chuẩn đang xét)
   |
   +--> catch: truyền ngoại lệ lên
   |
   +--> catch: khôi phục cờ và rời thao tác
   |
   +--> catch: xử lý theo hợp đồng đặc biệt
~~~

Điểm quan trọng là **một ngoại lệ đã có thể mang thông tin “tác vụ bị ngắt” dù interrupt flag tại thời điểm bắt là <code>false</code>**. Nếu code chỉ nhìn cờ sau khi catch mà bỏ qua đối tượng <code>InterruptedException</code>, nó có thể kết luận sai rằng không có yêu cầu hủy.

Theo tài liệu Java, <code>InterruptedException</code> thường đi kèm việc trạng thái ngắt bị xóa khi ném, nhưng hãy xét hợp đồng của API cụ thể. Không áp dụng quy tắc này cho **mọi API có chữ “interrupt”**: ví dụ <code>LockSupport.park()</code> phản ứng bằng cách trở về và không tự xóa cờ theo cách <code>Thread.sleep()</code> làm.

Nếu có một interrupt mới tới **sau khi** phương thức đã ném ngoại lệ nhưng trước lúc code kiểm tra cờ, trạng thái quan sát được cũng có thể thay đổi. Các ví dụ độc lập dưới đây kiểm soát số lượng tín hiệu để kết quả dễ hiểu; code đa luồng thực tế cần xét khả năng có nhiều yêu cầu ngắt.

## 5. Các phương thức chờ thường gặp và phản ứng với interrupt

| API | Khi nhận interrupt trong lúc chờ | Điều cần nhớ |
| --- | --- | --- |
| <code>Thread.sleep(...)</code> | Ném <code>InterruptedException</code>, xóa cờ theo hợp đồng | Không tự nhả lock/monitor mà luồng đang giữ |
| <code>Object.wait(...)</code> | Ném <code>InterruptedException</code>, xóa cờ theo hợp đồng | Chỉ gọi khi sở hữu monitor; tạm nhả monitor rồi lấy lại trước khi thoát |
| <code>Thread.join(...)</code> | Luồng **đang join** có thể nhận <code>InterruptedException</code> và cờ của nó bị xóa | Không có nghĩa luồng được join đã bị ngắt hoặc kết thúc |
| <code>BlockingQueue.take()</code> | Có thể ném <code>InterruptedException</code> khi đang chờ phần tử | Không biến thành một kết quả phần tử hợp lệ; cần kết thúc/xử lý đúng |
| <code>BlockingQueue.put(...)</code> | Có thể ném <code>InterruptedException</code> khi chờ chỗ trống | Với queue có dung lượng hữu hạn, kiểm tra tính nhất quán của luồng sản xuất |
| <code>Condition.await()</code> | Có thể ném <code>InterruptedException</code> | Nhả rồi lấy lại lock gắn với Condition trước khi phương thức kết thúc theo hợp đồng |
| <code>Lock.lockInterruptibly()</code> | Có thể ném <code>InterruptedException</code> trước khi lấy được khóa | Nếu không lấy được khóa thì **không gọi <code>unlock()</code>** |
| <code>Lock.tryLock(timeout, unit)</code> | Có thể ném <code>InterruptedException</code> trong khi đợi | Chỉ <code>unlock()</code> sau khi nhận được kết quả lấy khóa thành công |
| <code>CountDownLatch.await()</code>, <code>Semaphore.acquire()</code> | Là các lời gọi chờ có thể bị ngắt | Cần duy trì quy ước về kết quả/permit và hủy tác vụ |

Bảng mô tả hành vi thông thường của các API Java chuẩn. Một phương thức chờ có thể hoàn tất vì điều kiện bình thường, timeout, có phần tử mới, hoặc interrupt; loại kết quả cụ thể do hợp đồng của từng phương thức quy định. Không suy từ tên phương thức rằng mọi lời gọi chờ đều phản ứng giống nhau.

### Các phương thức chờ không cùng một kiểu

- <code>ReentrantLock.lock()</code> dùng cách lấy khóa **không thể hủy bằng <code>InterruptedException</code> trong lúc đợi**; nếu nhận interrupt trong khi đợi, nó tiếp tục chờ và duy trì/khôi phục thông tin interrupt theo hợp đồng.
- <code>ReentrantLock.lockInterruptibly()</code> cho phép hủy việc đợi bằng <code>InterruptedException</code>.
- <code>Condition.awaitUninterruptibly()</code> không ném <code>InterruptedException</code> từ interrupt đang đợi; nó tiếp tục chờ cho tới khi thỏa điều kiện thức dậy theo hợp đồng, đồng thời bảo toàn trạng thái ngắt để người gọi có thể nhận biết sau khi trở về.
- <code>LockSupport.park()</code> có thể trở về vì interrupt **mà không xóa interrupt flag** và không ném <code>InterruptedException</code> theo cách <code>sleep()</code> làm.

Vì vậy, quan hệ giữa flag và hành vi chờ là **hợp đồng của API chờ**, không phải một quy tắc “mọi lời gọi blocking đều ném ngoại lệ” của JVM.

## 6. Thread.sleep(): nhận interrupt nhưng không giải phóng khóa

<code>Thread.sleep</code> là phương thức static làm **luồng hiện tại** tạm ngừng thực thi trong khoảng thời gian yêu cầu, trừ khi bị ngắt hoặc gặp điều kiện khác được API quy định.

Hai điểm rất quan trọng:

1. Khi bị ngắt trong lúc sleep, phương thức ném <code>InterruptedException</code> và xóa cờ ngắt theo hợp đồng.
2. <code>sleep()</code> **không tự giải phóng monitor hoặc Lock** mà luồng đang sở hữu.

Ví dụ minh họa trong một lớp có <code>monitor</code>:

~~~java
synchronized (monitor) {
    Thread.sleep(1_000);
}
~~~

Đoạn trên **chưa phải chương trình Java độc lập** và phương thức chứa nó phải xử lý/khai báo <code>InterruptedException</code>. Luồng giữ monitor trong thời gian sleep. Nếu sleep bị ngắt, trước khi ra khỏi khối <code>synchronized</code>, control flow sẽ đi theo ngữ nghĩa Java của việc thoát khối; monitor được nhả **do rời khỏi khối synchronized**, không phải do <code>sleep()</code> tự nhả khi bắt đầu chờ.

Tương tự:

~~~java
lock.lock();
try {
    Thread.sleep(1_000);
} finally {
    lock.unlock();
}
~~~

Đây là **mã minh họa** với biến <code>lock</code> kiểu <code>Lock</code> và phương thức có hợp đồng checked exception phù hợp. Nếu <code>sleep()</code> ném lỗi, <code>finally</code> nhả khóa. Giữ khóa khi ngủ thường làm tăng thời gian các luồng khác phải đợi và cần được xem xét lại trong thiết kế.

## 7. Object.wait(): tạm nhả monitor rồi lấy lại trước khi kết thúc

<code>Object.wait()</code> chỉ được gọi khi luồng hiện tại **sở hữu monitor** của đối tượng đang đợi, thường bên trong <code>synchronized (monitor)</code>. Nếu không sở hữu monitor, lời gọi có thể ném <code>IllegalMonitorStateException</code>.

Đoạn minh họa:

~~~java
synchronized (monitor) {
    while (!ready) {
        monitor.wait();
    }
    consume();
}
~~~

Các biến <code>monitor</code>, <code>ready</code> và phương thức <code>consume()</code> là thành phần minh họa. Phương thức bao ngoài phải xử lý hoặc khai báo <code>InterruptedException</code>.

Khác với <code>sleep()</code>, <code>wait()</code> **tạm nhả monitor của đối tượng được đợi**, cho phép luồng khác vào vùng đồng bộ để cập nhật điều kiện và gọi <code>notify()</code>/<code>notifyAll()</code>. Trước khi <code>wait()</code> trả về hoặc ném <code>InterruptedException</code>, luồng phải lấy lại monitor đó theo ngữ nghĩa của API.

Một luồng có thể thức dậy vì tín hiệu, timeout (nếu dùng overload có thời hạn), interrupt hoặc *spurious wakeup*. Vì thế, điều kiện phải được kiểm tra trong **vòng <code>while</code>**, không mặc định dùng <code>if</code> một lần rồi tiếp tục.

Khi nhận interrupt trong lúc chờ:

- <code>wait()</code> ném <code>InterruptedException</code> sau bước lấy lại monitor cần thiết.
- Interrupted status bị xóa khi ném ngoại lệ.
- Nếu code để exception thoát khỏi khối <code>synchronized</code>, monitor được giải phóng theo quy tắc thoát khỏi vùng synchronized.
- Nếu code bắt ngoại lệ **ngay bên trong** vùng synchronized, tại thời điểm catch nó vẫn đang sở hữu monitor; cần quyết định rời khối hoặc xử lý sao cho không giữ monitor quá lâu.

**Không đồng nhất** “wait đã nhả monitor khi chờ” với “sau khi catch không còn giữ monitor”. Hai thời điểm này khác nhau.

## 8. Thread.join(): ngắt luồng đang chờ, không ngắt luồng được chờ

Xét:

~~~java
Thread worker = new Thread(() -> compute());
worker.start();
worker.join();
~~~

Đây là **mã minh họa**, giả định có <code>compute()</code> và phương thức gọi xử lý hoặc khai báo <code>InterruptedException</code>.

Lời gọi <code>worker.join()</code> khiến **luồng gọi join** chờ <code>worker</code> kết thúc. Nếu luồng gọi join bị interrupt, chính lời gọi <code>join()</code> có thể ném <code>InterruptedException</code>; điều đó **không chứng minh** <code>worker</code> đã kết thúc, cũng không tự động gọi <code>worker.interrupt()</code>.

Do đó, handler của luồng quản lý cần quyết định chính sách:

- Dừng chờ và báo rằng tác vụ quản lý bị hủy.
- Truyền <code>InterruptedException</code> cho tầng điều phối nếu chữ ký cho phép.
- Nếu trách nhiệm dọn dẹp yêu cầu đợi worker kết thúc, thiết kế một quy trình hủy/đợi riêng, không tự động nuốt interrupt hoặc chờ vô thời hạn.

Không viết một vòng lặp <code>while (worker.isAlive()) worker.join()</code> rồi bỏ qua mọi <code>InterruptedException</code> nếu hệ thống muốn hỗ trợ hủy. Điều đó biến yêu cầu ngắt thành tín hiệu bị bỏ quên.

## 9. BlockingQueue.take(): chương trình Java 17+ với interrupt từ luồng khác

<code>BlockingQueue.take()</code> chờ một phần tử có sẵn. Khi queue rỗng, worker có thể block; nếu nhận interrupt theo hợp đồng, lời gọi chờ ném <code>InterruptedException</code> và worker phải quyết định xử lý yêu cầu hủy.

Ví dụ dưới đây dùng <code>LinkedBlockingQueue</code> rỗng và một <code>CountDownLatch</code> để điều phối thứ tự. Điều kiện đồng bộ cố ý **không phụ thuộc** vào việc worker đã kịp park/block tại chính xác thời điểm nào: nếu interrupt đến ngay trước khi <code>take()</code> bắt đầu, thao tác interruptible này vẫn nhận tín hiệu.

Tạo tệp **InterruptibleQueueDemo.java**:

~~~java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

public class InterruptibleQueueDemo {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        CountDownLatch ready = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            ready.countDown();

            try {
                String item = queue.take();
                System.out.println("Worker nhận: " + item);
            } catch (InterruptedException original) {
                System.out.println("Worker nhận InterruptedException");
                System.out.println("Cờ sau khi catch: "
                        + Thread.currentThread().isInterrupted());

                Thread.currentThread().interrupt();
                System.out.println("Cờ sau khi restore: "
                        + Thread.currentThread().isInterrupted());
            }
        }, "queue-worker");

        worker.start();
        ready.await();

        worker.interrupt();
        worker.join();

        System.out.println("Worker đã kết thúc: " + !worker.isAlive());
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 InterruptibleQueueDemo.java
java InterruptibleQueueDemo
~~~

Stdout của ví dụ (khi không có nguồn gửi interrupt khác):

~~~text
Worker nhận InterruptedException
Cờ sau khi catch: false
Cờ sau khi restore: true
Worker đã kết thúc: true
~~~

Phân tích:

1. <code>queue</code> bắt đầu rỗng và không có code nào thêm phần tử. Vì thế <code>take()</code> không thể hoàn thành bằng cách nhận một phần tử.
2. <code>ready.countDown()</code> cho phép <code>main</code> gửi interrupt sau khi worker đã khởi chạy. Nó **không chứng minh** worker đã hoàn toàn đi vào trạng thái chờ; ví dụ không yêu cầu điều đó để có kết quả đúng.
3. <code>worker.interrupt()</code> làm lời gọi <code>take()</code> đang chờ, hoặc chuẩn bị đi vào chờ, phản ứng bằng <code>InterruptedException</code>.
4. Khi vào <code>catch</code>, trạng thái ngắt của worker đã bị xóa theo cách xử lý interrupt của <code>take()</code>, vì vậy in <code>false</code>.
5. Worker khôi phục cờ bằng <code>Thread.currentThread().interrupt()</code> để bảo toàn tín hiệu cho code quản lý phía trên nếu có; in <code>true</code>.
6. Lambda kết thúc, <code>main</code> dùng <code>join()</code> để chờ worker thực sự dừng rồi mới in dòng cuối.

**Không diễn giải nhầm:** cờ được khôi phục thành <code>true</code> không khiến worker “sống tiếp” hay quay trở lại chờ. Trong ví dụ, worker kết thúc vì code đã thoát khỏi lambda. Quyết định kết thúc là do logic ứng dụng.

### Vì sao không bắt rồi lặp lại ngay?

Một dạng dễ gây lỗi:

~~~java
while (true) {
    try {
        process(queue.take());
    } catch (InterruptedException ignored) {
        // Lặp lại ngay, coi như chưa có yêu cầu hủy.
    }
}
~~~

Đoạn này là **mã minh họa cố ý sai**, giả định biến <code>queue</code> và <code>process()</code> có sẵn. Nó âm thầm bỏ yêu cầu ngắt. Với một worker phục vụ hệ thống có chính sách shutdown bằng interrupt, vòng lặp có thể làm tác vụ tiếp tục sau khi đã có tín hiệu dừng.

Nếu bắt rồi **khôi phục cờ nhưng vẫn lặp lại**, một số API chờ interruptible sẽ lập tức ném <code>InterruptedException</code> ở lần tiếp theo, có thể gây vòng quay nhanh tiêu thụ CPU. Vì vậy khi muốn tôn trọng yêu cầu hủy, thường cần **rời vòng lặp hoặc truyền ngoại lệ**, không chỉ restore rồi tiếp tục vô hạn.

## 10. Hai chiến lược đúng trong catch: truyền tiếp hoặc khôi phục

### Cách A — truyền InterruptedException lên tầng gọi

Nếu method được phép khai báo checked exception, cách đơn giản thường là **để <code>InterruptedException</code> lan truyền**:

~~~java
String awaitNextItem(BlockingQueue<String> queue)
        throws InterruptedException {
    return queue.take();
}
~~~

Đoạn này là **mã minh họa trong một lớp có import phù hợp**, không phải chương trình độc lập. Tầng gọi sẽ nhận trực tiếp nguyên nhân yêu cầu chờ bị ngắt và chịu trách nhiệm quyết định hủy hoặc tiếp tục.

Trong trường hợp chỉ truyền ngoại lệ lên ngay, không bắt rồi gọi <code>Thread.currentThread().interrupt()</code> một cách máy móc nếu không có lý do rõ ràng. Hợp đồng checked exception đã cung cấp tín hiệu cho bên gọi; code tầng trên có thể thiết kế chính sách riêng.

### Cách B — không thể ném checked exception: restore và kết thúc

Giao diện <code>Runnable.run()</code> không cho phép khai báo <code>throws InterruptedException</code>. Khi worker nhận interrupt trong một thao tác chờ, một mẫu hợp lý là:

~~~java
@Override
public void run() {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            String task = queue.take();
            process(task);
        }
    } catch (InterruptedException original) {
        Thread.currentThread().interrupt();
    } finally {
        releaseOwnedResources();
    }
}
~~~

Đây là **mã minh họa trong một lớp implements Runnable** có <code>queue</code>, <code>process(...)</code> và <code>releaseOwnedResources()</code> phù hợp.

- Việc <code>take()</code> ném ngoại lệ khiến control flow ra ngoài vòng lặp.
- <code>catch</code> khôi phục tín hiệu vì phương thức không thể truyền checked exception lên trên.
- <code>finally</code> giải phóng tài nguyên do worker sở hữu.
- Sau <code>catch/finally</code>, <code>run()</code> kết thúc. Code không lặp lại thao tác chờ như thể tín hiệu chưa từng tồn tại.

Có tình huống hợp đồng ứng dụng yêu cầu **chuyển lỗi thành unchecked exception**. Khi ấy nên truyền ngoại lệ gốc vào cause, ví dụ:

~~~java
catch (InterruptedException original) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Tác vụ bị ngắt", original);
}
~~~

Đây chỉ là **khối catch minh họa** trong ngữ cảnh phù hợp. Cần quyết định rõ ngoại lệ loại nào mô tả hủy tác vụ; <code>IllegalStateException</code> ở đây minh họa cơ chế, không phải mẫu tên bắt buộc. Việc đặt cờ lại giữ thông tin cho code kiểm tra trạng thái ngắt, còn cause bảo toàn dấu vết <code>InterruptedException</code> đã ném.

### Khi nào không nên restore?

Nếu một API của bạn **thực sự tiêu thụ yêu cầu interrupt** theo hợp đồng được công bố, thực hiện xong quy trình phục hồi và quyết định tiếp tục công việc, nó có thể xử lý theo chính sách riêng. Nhưng đó phải là quyết định có lý do, không phải một <code>catch (InterruptedException ignored)</code> chung.

Một trường hợp phổ biến không cần tự restore ở tầng trung gian là khi **truyền <code>InterruptedException</code> lên ngay** cho tầng đã nhận trách nhiệm hủy. Không có một quy tắc yêu cầu mọi catch đều vừa restore vừa throw checked exception.

## 11. Khác nhau giữa Thread.interrupted() và isInterrupted() trong vòng lặp

Hãy xem:

~~~java
while (!Thread.interrupted()) {
    doWork();
}
~~~

Đây là **mã minh họa**. Nó dùng lời gọi static để tiêu thụ interrupt flag. Khi vòng lặp thoát vì <code>Thread.interrupted()</code> đã trả về <code>true</code>, cờ đã bị **xóa**. Đây có thể là thiết kế có chủ đích của một thành phần tiêu thụ tín hiệu; nhưng dễ làm mất thông tin mà những tầng sau cần quan sát.

Trong một worker muốn để các đoạn cleanup hoặc caller biết yêu cầu ngắt đã xảy ra, thường rõ ràng hơn:

~~~java
while (!Thread.currentThread().isInterrupted()) {
    doWork();
}
~~~

<code>isInterrupted()</code> không xóa cờ. Việc rời vòng lặp không tự xóa yêu cầu dừng.

Nếu cần truy vấn trạng thái của **luồng khác** từ bộ quản lý, dùng <code>worker.isInterrupted()</code>. Tuy nhiên, đọc cờ không chứng minh công việc đã ngừng, tài nguyên đã được nhả, hay luồng đã chết. Để chờ kết thúc, phải dùng cơ chế đồng bộ phù hợp như <code>join()</code> hoặc completion API.

Một số thư viện/framework có thể tự xử lý interrupt trong các phương thức chờ. Vì vậy, **một giá trị <code>false</code> đọc sau này không chứng minh chưa từng có interrupt**. Đối với kiểm thử và quan sát, cần biết API nào đã nhận và có thể đã tiêu thụ tín hiệu.

## 12. Lock.lockInterruptibly(): interrupt trước khi lấy khóa

<code>java.util.concurrent.locks.Lock</code> cung cấp cách lấy khóa có thể bị ngắt qua <code>lockInterruptibly()</code>. Với <code>ReentrantLock</code>, nếu luồng bị interrupt khi đang đợi khóa hoặc có trạng thái ngắt tại thời điểm vào lời gọi, phương thức ném <code>InterruptedException</code> và xóa trạng thái ngắt theo hợp đồng.

Mẫu đúng:

~~~java
lock.lockInterruptibly();
try {
    updateSharedState();
} finally {
    lock.unlock();
}
~~~

Đây là **mã minh họa** trong một phương thức khai báo/xử lý <code>InterruptedException</code>, có <code>lock</code> và <code>updateSharedState()</code>. <code>lockInterruptibly()</code> đặt **trước** <code>try</code> để nếu lời gọi bị ngắt trước khi lấy khóa, <code>finally</code> không chạy và không nhả một khóa chưa sở hữu.

### Ví dụ Java 17+ độc lập: hủy lúc đang tranh khóa

Tạo tệp **InterruptibleLockDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

public class InterruptibleLockDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch ready = new CountDownLatch(1);

        lock.lock();
        try {
            Thread contender = new Thread(() -> {
                ready.countDown();
                try {
                    lock.lockInterruptibly();
                    try {
                        System.out.println("Contender đã lấy khóa");
                    } finally {
                        lock.unlock();
                    }
                } catch (InterruptedException original) {
                    System.out.println("Contender bị ngắt khi đợi khóa");
                    System.out.println("Cờ khi catch: "
                            + Thread.currentThread().isInterrupted());
                    Thread.currentThread().interrupt();
                    System.out.println("Cờ sau restore: "
                            + Thread.currentThread().isInterrupted());
                }
            }, "contender");

            contender.start();
            ready.await();

            contender.interrupt();
            contender.join();

            System.out.println("Main vẫn giữ khóa: "
                    + lock.isHeldByCurrentThread());
        } finally {
            lock.unlock();
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 InterruptibleLockDemo.java
java InterruptibleLockDemo
~~~

Stdout:

~~~text
Contender bị ngắt khi đợi khóa
Cờ khi catch: false
Cờ sau restore: true
Main vẫn giữ khóa: true
~~~

**Vì sao kết quả xác định?**

- Luồng <code>main</code> lấy khóa trước khi tạo luồng contender, nên contender không thể lấy khóa khi <code>main</code> còn giữ.
- <code>ready</code> bảo đảm contender đã được khởi chạy; interrupt có thể xảy ra ngay trước hoặc trong <code>lockInterruptibly()</code>, và cả hai thời điểm đều dẫn tới <code>InterruptedException</code> với cách triển khai này.
- Contender chưa lấy được khóa, nên nhánh <code>catch</code> không gọi <code>unlock()</code>. Nếu gọi nhả khóa sai, <code>ReentrantLock</code> sẽ ném <code>IllegalMonitorStateException</code>.
- Sau khi <code>contender.join()</code> hoàn tất, <code>main</code> vẫn sở hữu khóa và in <code>true</code>, sau đó mới nhả trong <code>finally</code>.

Nếu thay lời gọi bằng <code>lock.lock()</code>, việc đợi sẽ **không ném InterruptedException theo cùng cơ chế**. Trong ví dụ này, luồng main đang giữ khóa; nếu main đợi contender bằng <code>join()</code> trong khi contender chờ khóa không thể hủy, chương trình có thể bị kẹt do quan hệ chờ vòng. Đây là lý do phải lựa chọn hợp đồng lấy khóa phù hợp với chính sách hủy.

## 13. Condition.await(): khi bị ngắt, phải lấy lại khóa trước khi thoát

<code>Condition</code> thường gắn với một <code>ReentrantLock</code>. Khi một luồng gọi <code>condition.await()</code> trong khi sở hữu khóa, lời gọi tạm nhả khóa đó để luồng khác có thể thay đổi điều kiện và đánh thức nó.

Một mẫu thường dùng:

~~~java
lock.lock();
try {
    while (!ready) {
        condition.await();
    }
    consume();
} finally {
    lock.unlock();
}
~~~

Đây là **mã minh họa** với <code>lock</code>, <code>condition</code>, <code>ready</code> và <code>consume()</code> được cung cấp bởi lớp bao ngoài; phương thức phải xử lý/khai báo <code>InterruptedException</code>.

Lưu ý:

- <code>Condition.await()</code> có thể bị ngắt bằng <code>InterruptedException</code>.
- <code>ReentrantLock</code> sẽ được lấy lại trước khi lời gọi <code>await()</code> hoàn tất bằng cách ném lỗi theo hợp đồng của Condition; vì vậy khi đi vào <code>finally</code>, code vẫn có quyền nhả lần giữ khóa đã lấy trước đó.
- Điều kiện phải được kiểm tra trong <code>while</code> vì tín hiệu đánh thức không bảo đảm bất biến vẫn còn đúng tại thời điểm luồng lấy lại khóa, và có thể có *spurious wakeup*.
- Nếu bắt ngoại lệ ngay bên trong vùng khóa để khôi phục hoặc chuyển lỗi, đừng giữ khóa quá lâu sau khi đã quyết định hủy.

Khác với <code>lockInterruptibly()</code>, một luồng gọi <code>await()</code> **đã sở hữu khóa trước khi chờ**. Do đó việc <code>await()</code> ném <code>InterruptedException</code> không có nghĩa không cần <code>unlock()</code>: trong cấu trúc chuẩn, <code>finally</code> vẫn phải giải phóng khóa sau khi nó được lấy lại.

Với <code>awaitUninterruptibly()</code>, interrupt không làm phát sinh <code>InterruptedException</code> để chấm dứt việc chờ theo cơ chế này; cờ ngắt vẫn được bảo toàn/khôi phục theo hợp đồng của implementation. Không dùng biến thể này cho một tác vụ mà chính sách yêu cầu hủy chờ kịp thời.

## 14. LockSupport.park(): trường hợp không ném InterruptedException

<code>java.util.concurrent.locks.LockSupport.park()</code> là primitive cấp thấp thường được các thư viện đồng bộ hóa dùng để đỗ luồng. Nó có thể trả về khi luồng bị interrupt, nhưng **không ném <code>InterruptedException</code>** và **không xóa interrupt flag** chỉ vì trở về do interrupt.

Minh họa trong một phương thức:

~~~java
java.util.concurrent.locks.LockSupport.park();
boolean interrupted = Thread.currentThread().isInterrupted();
~~~

Đoạn này là **mã minh họa**, không phải ví dụ chạy độc lập vì <code>park()</code> có thể chờ vô thời hạn nếu không có nguyên nhân thức dậy. Nếu <code>park()</code> trả về do cờ ngắt đang bật, <code>interrupted</code> thường là <code>true</code> cho tới khi code chủ động tiêu thụ cờ hoặc một cơ chế khác thay đổi trạng thái.

Vì cờ vẫn bật, việc gọi <code>park()</code> lặp lại mà không xử lý interrupt có thể trả về ngay, tạo vòng quay không mong muốn. Các thuật toán dùng <code>LockSupport</code> phải tự quản lý điều kiện chờ, permit, trạng thái ngắt và các lần thức dậy bất thường.

Sự khác biệt này nhấn mạnh rằng **interruptible wait** có nhiều kiểu: ném checked exception và xóa flag, trở về bình thường với flag được giữ, hoặc tiếp tục chờ trong biến thể uninterruptible.

## 15. Tính hủy hợp tác: kết thúc có trật tự

Một yêu cầu ngắt thường xuất hiện khi hệ thống muốn dừng worker, đóng ứng dụng hoặc hủy một task:

~~~text
Bộ điều phối muốn dừng
          |
          | worker.interrupt()
          v
Worker đang tính toán hoặc chờ
          |
          +--- isInterrupted() thấy true ------+
          |                                    |
          +--- wait ném InterruptedException --+
                                               |
                                               v
                                   Đặt chính sách kết thúc
                                               |
                                    finally/close/cleanup
                                               |
                                               v
                                        Worker kết thúc
~~~

Tín hiệu interrupt **không tự động rollback dữ liệu hoặc đóng mọi tài nguyên**. Nếu tác vụ đang thực hiện nhiều bước, cần thiết kế trạng thái trung gian sao cho ngừng ở điểm hợp lệ, hoàn tất cleanup, hoặc ghi nhận một kết quả hủy rõ ràng.

Một số nguyên tắc thực hành:

- Không dùng <code>Thread.stop()</code> để ép dừng một luồng: hành vi ngắt cưỡng bức không an toàn đối với bất biến dữ liệu.
- Đừng giữ khóa quá lâu khi chờ I/O hoặc một điều kiện mà chính sách cần hủy sớm.
- Với thao tác có tác dụng phụ, không tự động thử lại chỉ vì nhận interrupt: công việc có thể đã hoàn thành một phần.
- Nếu API async của bạn có trạng thái cancelled riêng, hãy phân biệt tín hiệu interrupt với kết quả hủy đã được xác nhận.
- Khi muốn dừng worker, có thể kết hợp một cờ điều khiển ứng dụng và interrupt để đánh thức thao tác chờ; tránh giả định mọi API blocking đều được ngắt bằng cùng cơ chế.

### Future.cancel(true) không đồng nghĩa worker chắc chắn đã dừng

Khi sử dụng <code>Future</code>, <code>cancel(true)</code> có thể yêu cầu interrupt luồng đang thực thi task nếu implementation hỗ trợ. Nó **không cưỡng bức** mọi đoạn code chấp nhận hủy; một task cố tình bỏ qua interrupt hoặc đang chờ trong API không phản ứng tương ứng có thể còn chạy. Đừng dùng trạng thái <code>Future.isCancelled()</code> như bằng chứng duy nhất rằng các tác dụng phụ đang chạy đã chấm dứt tại đúng thời điểm.

Ngược lại, một task tự phát sinh <code>InterruptedException</code> rồi được lưu như lỗi trong cơ chế future chưa chắc đã trở thành trạng thái “cancelled” trừ khi hợp đồng của framework định nghĩa như vậy.

## 16. Những lỗi thường gặp với interrupt flag và API chờ

### Lỗi 1 — catch InterruptedException rồi im lặng

~~~java
try {
    Thread.sleep(500);
} catch (InterruptedException ignored) {
    // Không thông báo và tiếp tục công việc như bình thường.
}
~~~

Đây là **mã minh họa cố ý không khuyến nghị**. Sau khi <code>sleep()</code> ném ngoại lệ, cờ đã bị xóa. Lỗi bị nuốt, và các tầng sau không nhận được yêu cầu dừng. Nếu việc tiếp tục thật sự được phép theo hợp đồng, cần ghi rõ logic xử lý riêng; trong phần lớn worker có chính sách hủy, nên truyền lỗi hoặc restore và kết thúc.

### Lỗi 2 — tưởng isInterrupted() xóa cờ

~~~java
boolean first = Thread.currentThread().isInterrupted();
boolean second = Thread.currentThread().isInterrupted();
~~~

Hai lần đọc không tự thay đổi trạng thái; nếu không có lời gọi/API khác tác động, chúng quan sát cùng một giá trị. Khác với <code>Thread.interrupted()</code> vốn xóa cờ.

### Lỗi 3 — dùng Thread.interrupted() chỉ để ghi log

~~~java
System.out.println("Interrupted: " + Thread.interrupted());
~~~

Đây là **mã minh họa**. Dòng log có tác dụng phụ: nếu cờ đang bật, nó bị xóa. Nếu chỉ muốn quan sát, dùng <code>Thread.currentThread().isInterrupted()</code> để giữ tín hiệu.

### Lỗi 4 — restore nhưng tiếp tục chờ ngay

~~~java
while (true) {
    try {
        queue.take();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}
~~~

Đây là **mã cố ý sai**, với <code>queue</code> có sẵn. Sau restore, lần <code>take()</code> tiếp theo có thể ném ngay, khiến vòng lặp quay nhanh. Nếu interrupt có nghĩa hủy, nên rời vòng lặp; nếu thiết kế cần tiếp tục, phải có chính sách tiêu thụ tín hiệu rõ ràng, không dùng cách trên.

### Lỗi 5 — cho rằng sleep() hoặc join() nhả mọi khóa

<code>sleep()</code> không nhả monitor/lock. <code>join()</code> không tự nhả những khóa tùy ý mà luồng gọi đang giữ. Chờ khi đang giữ khóa mà worker cần có thể dẫn tới deadlock. <code>Object.wait()</code> chỉ tạm nhả monitor tương ứng của đối tượng được wait; <code>Condition.await()</code> làm tương tự với lock gắn với condition theo hợp đồng.

### Lỗi 6 — unlock() sau khi lockInterruptibly thất bại

~~~java
try {
    lock.lockInterruptibly();
    work();
} finally {
    lock.unlock();
}
~~~

Đây là **mã minh họa cố ý sai** khi <code>lock</code> là <code>ReentrantLock</code>: nếu <code>lockInterruptibly()</code> ném trước khi lấy được khóa, <code>finally</code> vẫn chạy và có thể ném <code>IllegalMonitorStateException</code>. Mẫu đúng là lấy khóa trước, rồi mới vào <code>try/finally</code> để nhả khóa.

### Lỗi 7 — xem interrupt là ngoại lệ luôn được ném

Một vòng lặp CPU không kiểm tra cờ có thể tiếp tục chạy. <code>LockSupport.park()</code> có thể trở về mà không ném <code>InterruptedException</code>. <code>ReentrantLock.lock()</code> có thể tiếp tục chờ. Cần tra đúng hợp đồng API.

### Lỗi 8 — đánh đồng tín hiệu ngắt với hoàn tất hủy

<code>worker.interrupt()</code> chỉ gửi tín hiệu. Sau đó cần đợi hoặc quan sát bằng <code>join()</code>, <code>Future</code>, latch hoặc cơ chế quản lý phù hợp nếu muốn biết worker đã kết thúc. Không kiểm tra cờ rồi khẳng định cleanup đã xong.

### Lỗi 9 — bắt Exception quá rộng và xử lý như lỗi nghiệp vụ

~~~java
try {
    performTask();
} catch (Exception error) {
    reportFailure(error);
}
~~~

Đây là **mã minh họa**. Nếu bên trong có <code>InterruptedException</code> bị bắt qua <code>Exception</code>, tín hiệu hủy có thể bị mất. Đặc biệt khi dùng các wrapper hoặc framework xử lý exception, cần phân biệt thất bại nghiệp vụ thông thường với yêu cầu ngắt của luồng.

## 17. Kiểm thử những gì?

Một bộ kiểm thử có giá trị nên phân biệt **trạng thái cờ**, **ngoại lệ được ném**, **quyền sở hữu khóa** và **kết quả hủy**:

| Tình huống | Điều cần xác nhận |
| --- | --- |
| Gọi <code>interrupt()</code> rồi <code>isInterrupted()</code> | Cờ bật; phép đọc không xóa |
| Gọi <code>Thread.interrupted()</code> khi cờ đang bật | Trả về <code>true</code> và xóa cờ |
| Cờ bật trước <code>sleep()</code> | <code>InterruptedException</code> và cờ bị xóa |
| Worker bị ngắt trong <code>BlockingQueue.take()</code> | Lời gọi chờ báo ngắt, worker theo chính sách kết thúc |
| Catch và restore | Cờ được đặt lại nếu code chủ động gọi <code>interrupt()</code> |
| Ngắt khi <code>lockInterruptibly()</code> chưa lấy khóa | Không nhả khóa chưa sở hữu |
| Ngắt trong <code>Condition.await()</code> | Khóa được lấy lại theo hợp đồng rồi giải phóng đúng từ <code>finally</code> |
| Ngắt luồng đang gọi <code>join()</code> | Không kết luận luồng đích đã dừng |
| Tác vụ thực thi không kiểm tra cờ | Không giả định nó tự dừng chỉ vì nhận interrupt |
| <code>park()</code> trở về vì interrupt | Không nhầm với đường ném <code>InterruptedException</code> của sleep |

Với kiểm thử đa luồng, nên dùng <code>CountDownLatch</code>, queue hoặc cơ chế đồng bộ tương đương để đặt thứ tự kiểm chứng thay vì chỉ gọi <code>Thread.sleep()</code> để “chờ cho đủ lâu”. Nếu một lỗi có thể khiến test bị kẹt, cần giới hạn thời gian chờ phù hợp và dọn tài nguyên sau phép thử.

Đối với kiểm tra trạng thái cờ ngay sau khi nhận <code>InterruptedException</code>, xác định rõ có luồng nào khác đang gửi interrupt tiếp hay không. Nếu có tín hiệu ngắt mới, việc quan sát <code>true</code> không phủ nhận hợp đồng rằng phương thức chờ đã xóa trạng thái cho lần interrupt nó xử lý.

## 18. Checklist áp dụng trong dự án

Khi một phương thức gọi API blocking, hãy tự hỏi:

1. **API này có thể bị ngắt không?** Nó ném <code>InterruptedException</code>, trả về sớm nhưng giữ cờ, hay tiếp tục chờ?
2. **Cờ ở đâu sau khi bị ngắt?** Kiểm tra hợp đồng API, đừng dự đoán từ tên phương thức.
3. **Hợp đồng tầng hiện tại là gì?** Có thể khai báo <code>throws InterruptedException</code> và chuyển lên không?
4. **Nếu phải catch**, nên khôi phục cờ và rời thao tác, hay có quy tắc xử lý tín hiệu riêng thực sự hợp lệ?
5. **Có tránh mất cờ do gọi <code>Thread.interrupted()</code> không chủ đích** không?
6. **Có đang giữ khóa hoặc tài nguyên khi chờ** không? API nào nhả tạm, API nào không?
7. **Khi chờ khóa bị ngắt**, code đã tránh gọi <code>unlock()</code> nếu chưa sở hữu khóa chưa?
8. **Việc hủy có để lại dữ liệu cập nhật dở dang không?** Tài nguyên và bất biến có được bảo vệ bởi <code>finally</code>/giao thức cập nhật phù hợp không?
9. **Có cơ chế xác nhận hoàn tất** như <code>join()</code> hay completion state, thay vì chỉ dựa vào tín hiệu <code>interrupt()</code> không?
10. **Kiểm thử có phân biệt** ngoại lệ, cờ ngắt và sự hoàn tất thực tế của task không?

**Điều cốt lõi:** interrupt flag là **trạng thái tín hiệu hợp tác của luồng**. Một phương thức chờ có thể phản ứng với tín hiệu bằng <code>InterruptedException</code> rồi xóa cờ, hoặc bằng cách khác theo hợp đồng. Khi bắt <code>InterruptedException</code>, ứng dụng phải quyết định rõ **truyền tiếp**, **khôi phục tín hiệu rồi thoát**, hoặc **xử lý có chủ đích**. Quản lý đúng quan hệ đó giúp hệ thống ngừng công việc có trật tự mà không âm thầm bỏ qua yêu cầu hủy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-003">Phân biệt Thread.interrupted với isInterrupted</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `Thread.interrupted()` với `isInterrupted()` trong Java

Khi cần dừng một công việc đang chạy trên luồng (thread), Java thường dùng cơ chế **ngắt hợp tác** (*cooperative interruption*): một luồng gửi yêu cầu ngắt bằng `interrupt()`, còn luồng nhận tự kiểm tra tín hiệu và quyết định cách kết thúc an toàn. Để kiểm tra tín hiệu ấy, Java có hai phương thức trông rất giống nhau: `Thread.interrupted()` và `thread.isInterrupted()`. Cả hai đều trả `boolean`, nhưng **chúng kiểm tra luồng nào** và **có xóa trạng thái ngắt hay không** là hai khác biệt quyết định.

Hiểu sai hai phương thức có thể khiến tín hiệu hủy bị “tiêu thụ” quá sớm, luồng không dừng khi được yêu cầu, hoặc một công việc cấp trên không còn biết rằng luồng đang bị ngắt.

## 1. Hiểu trạng thái ngắt trước khi so sánh

Mỗi đối tượng `Thread` có trạng thái ngắt (*interrupt status*, thường gọi là cờ ngắt). Đây là tín hiệu cho luồng phối hợp với việc dừng, không phải lệnh cưỡng bức chấm dứt lập tức.

- `targetThread.interrupt()` gửi yêu cầu ngắt đến `targetThread`. Trong tình huống thông thường, nếu luồng đích đang chạy, trạng thái ngắt của nó được đặt thành `true`.
- Mã của luồng đích có thể đọc cờ ngắt, kết thúc vòng lặp, dọn dẹp tài nguyên và trả quyền điều khiển về cho người gọi.
- Một số thao tác chờ có khả năng ngắt, như `Thread.sleep()`, `Object.wait()`, `Thread.join()` hoặc `BlockingQueue.take()`, có thể ném `InterruptedException`. Những thao tác này thường **xóa trạng thái ngắt khi ném ngoại lệ**.
- Không phải mọi thao tác đều phản ứng với `interrupt()`. Ví dụ, một phép tính CPU không kiểm tra cờ sẽ không tự dừng; chờ giành khóa `synchronized` thông thường cũng không tự động trở thành phép chờ có thể ngắt.

Vì thế, cần phân biệt **gửi tín hiệu** (`interrupt()`), **quan sát tín hiệu** (`isInterrupted()` hoặc `Thread.interrupted()`), và **chính sách đáp ứng** (dừng, truyền lỗi hoặc dọn dẹp).

## 2. Hai API: cùng đọc cờ, khác đối tượng và tác dụng phụ

| Đặc điểm | `Thread.interrupted()` | `someThread.isInterrupted()` |
| --- | --- | --- |
| Kiểu phương thức | `static` | Phương thức thể hiện (instance) |
| Luồng được kiểm tra | **Luồng hiện tại**, tức luồng đang thực thi lời gọi | **Đối tượng `Thread` được gọi** (`someThread`); có thể là luồng hiện tại hoặc luồng khác |
| Giá trị trả về | `true` nếu trạng thái ngắt đang được đặt, ngược lại là `false` | `true` nếu trạng thái ngắt của đối tượng luồng đang được đặt, ngược lại là `false` |
| Trạng thái sau khi gọi | **Xóa cờ ngắt của luồng hiện tại** | **Không thay đổi cờ ngắt** |
| Đọc nhiều lần liên tiếp khi không có tín hiệu mới | Lần đầu có thể `true`, lần sau `false` vì lần đầu đã xóa | Có thể vẫn `true` ở cả hai lần |
| Trường hợp sử dụng | Luồng hiện tại muốn kiểm tra **và chủ động tiêu thụ** tín hiệu | Kiểm tra trạng thái mà **không làm mất** tín hiệu; cũng có thể quan sát một `Thread` khác |

Ví dụ phân biệt ngay trong luồng đang chạy:

```java
Thread current = Thread.currentThread();

boolean a = current.isInterrupted(); // Chỉ đọc, giữ nguyên cờ.
boolean b = Thread.interrupted();     // Đọc rồi xóa cờ của current.
boolean c = current.isInterrupted(); // Có thể false vì b đã xóa.
```

Ba dòng trên là đoạn minh họa, không phải chương trình Java độc lập. Nếu trạng thái ngắt trước dòng đầu là `true` và không có yêu cầu ngắt mới xen vào thì `a == true`, `b == true`, `c == false`.

**Bẫy về cú pháp:** `interrupted()` là phương thức `static`, vì thế dù Java cho phép cú pháp `someThread.interrupted()`, lời gọi ấy vẫn kiểm tra **luồng hiện tại**, không kiểm tra `someThread`. Tránh viết kiểu này. Muốn hỏi trạng thái của luồng khác, gọi `someThread.isInterrupted()`.

Có thể viết `Thread.currentThread().isInterrupted()` để đọc cờ của chính mình nhưng không xóa. Đây không phải một phương thức mới: chỉ là cách lấy đối tượng luồng hiện tại rồi gọi API instance.

## 3. Ví dụ có thể chạy: quan sát thay đổi của cờ ngắt

Tạo tệp **`InterruptFlagComparison.java`**:

```java
public class InterruptFlagComparison {
    public static void main(String[] args) {
        Thread current = Thread.currentThread();

        System.out.println("initial: " + current.isInterrupted());

        current.interrupt();
        System.out.println("peek: " + current.isInterrupted());
        System.out.println("readAndClear: " + Thread.interrupted());
        System.out.println("afterClear: " + current.isInterrupted());

        current.interrupt();
        try {
            Thread.sleep(10);
            throw new AssertionError("sleep should have been interrupted");
        } catch (InterruptedException exception) {
            System.out.println("afterSleepThrows: " + current.isInterrupted());
            current.interrupt();
            System.out.println("restored: " + current.isInterrupted());
        }

        System.out.println("finalReadAndClear: " + Thread.interrupted());
        System.out.println("finalStatus: " + current.isInterrupted());
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac InterruptFlagComparison.java
java InterruptFlagComparison
```

Kết quả trên một lần chạy độc lập bình thường:

```text
initial: false
peek: true
readAndClear: true
afterClear: false
afterSleepThrows: false
restored: true
finalReadAndClear: true
finalStatus: false
```

Đọc kết quả theo từng nhóm:

1. Khi chương trình mới bắt đầu, luồng `main` chưa được ngắt, nên `initial: false`.
2. `current.interrupt()` đặt cờ thành `true`. `current.isInterrupted()` chỉ đọc nên `peek: true`, cờ vẫn còn.
3. `Thread.interrupted()` trả `true` và xóa cờ. Vì thế `afterClear: false`.
4. Chương trình đặt lại cờ rồi gọi `Thread.sleep(10)`. Vì luồng đã mang trạng thái ngắt, `sleep()` ném `InterruptedException`; khi vào `catch`, trạng thái đã bị xóa, tạo ra `afterSleepThrows: false`.
5. Lệnh `current.interrupt()` bên trong `catch` **khôi phục tín hiệu ngắt**. Vì thế `restored: true`.
6. Lệnh `Thread.interrupted()` cuối cùng lại nhận `true` và xóa cờ. `finalStatus: false` xác nhận trạng thái sau lần xóa cuối.

Giá trị `10` trong `sleep(10)` là thời gian chờ tối đa dự định (mili giây), không phải khoảng trễ cần đợi để phát sinh lỗi: việc cờ đã được đặt làm thao tác ngủ bị ngắt.

Ví dụ này cố tình gọi `Thread.interrupted()` để chứng minh tác dụng xóa cờ. Trong chương trình nghiệp vụ, không nên xóa tín hiệu ngắt nếu mã phía sau hoặc tầng gọi cần biết luồng đã được yêu cầu dừng.

## 4. Trạng thái nào đổi khi đọc?

Hãy xem một chuỗi thao tác **trên cùng luồng**, không có luồng nào gửi tín hiệu mới ở giữa:

| Thao tác | Giá trị trả về | Cờ sau thao tác |
| --- | --- | --- |
| Ban đầu chưa bị ngắt | — | `false` |
| `Thread.currentThread().interrupt()` | `void` | `true` |
| `Thread.currentThread().isInterrupted()` | `true` | `true` |
| `Thread.currentThread().isInterrupted()` lần nữa | `true` | `true` |
| `Thread.interrupted()` | `true` | `false` |
| `Thread.interrupted()` lần nữa | `false` | `false` |

Bảng mô tả một chuỗi có kiểm soát. Trong ứng dụng chạy đồng thời, một luồng khác có thể gọi `interrupt()` bất kỳ lúc nào; vì vậy kết quả của hai lần đọc tách rời có thể khác nhau do tín hiệu mới, chứ không chỉ do cách API xử lý cờ.

**Quy tắc cần nhớ:** `isInterrupted()` = *nhìn mà giữ*; `Thread.interrupted()` = *nhìn rồi xóa* (trên luồng hiện tại).

## 5. Kết hợp với `InterruptedException` và truyền tín hiệu hủy

Có ba tình huống quan trọng khi xử lý `InterruptedException`.

### 5.1. Phương thức có thể khai báo `throws InterruptedException`

Nếu phương thức chỉ là tầng trung gian và có thể truyền yêu cầu ngắt lên tầng gọi, thường nên **truyền tiếp ngoại lệ**:

```java
void waitForItem(java.util.concurrent.BlockingQueue<String> queue)
        throws InterruptedException {
    String item = queue.take();
    System.out.println(item);
}
```

Đây là đoạn phương thức minh họa đặt trong một lớp Java. Khi `take()` ném `InterruptedException`, phương thức trả quyền xử lý cho tầng gọi. Không cần bắt ngoại lệ chỉ để in log rồi tiếp tục vô điều kiện.

### 5.2. Không thể truyền `InterruptedException`: bắt và khôi phục cờ

`Runnable.run()` không khai báo checked exception. Nếu cần xử lý ngắt khi đang chờ trong `run()`, một mẫu phù hợp là **rời công việc** sau khi khôi phục trạng thái:

```java
@Override
public void run() {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            String item = queue.take();
            process(item);
        }
    } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
    } finally {
        closeOwnedResources();
    }
}
```

Đây là **mã minh họa bên trong một lớp triển khai `Runnable`**, với `queue`, `process(...)` và `closeOwnedResources()` do ứng dụng cung cấp. Không thể biên dịch độc lập nếu thiếu các thành phần đó.

Vì `queue.take()` đã xóa cờ khi ném `InterruptedException`, việc gọi `Thread.currentThread().interrupt()` trong `catch` khôi phục tín hiệu cho mã gọi hoặc cơ chế quản lý luồng có thể nhận biết. Sau đó `run()` kết thúc thay vì quay lại một vòng chờ khác. Việc dọn dẹp tài nguyên được thực hiện qua `finally`.

**Không nên** bắt `InterruptedException` rồi lặp lại ngay vô hạn như thể không có yêu cầu hủy. Nếu mã của bạn hoàn toàn chịu trách nhiệm xử lý và kết thúc sau ngắt, có thể có chiến lược riêng, nhưng phải nêu rõ ai sở hữu tín hiệu và khi nào được xóa.

### 5.3. Vì sao sau khi bắt ngoại lệ, `isInterrupted()` có thể là `false`?

Những API ném `InterruptedException` như `sleep()`, `wait()`, `join()` thường xóa trạng thái ngắt khi ném. Vì thế phép kiểm tra `Thread.currentThread().isInterrupted()` ngay đầu nhánh `catch` có thể trả `false` dù chính tác vụ đã bị ngắt. Đây là hành vi hợp lệ, không có nghĩa tín hiệu ngắt chưa từng xảy ra.

Đặc biệt, **đừng gọi thêm `Thread.interrupted()` trong `catch` với hy vọng xác minh lại ngoại lệ**: nếu trạng thái mới được khôi phục hoặc có tín hiệu ngắt khác đến, lời gọi này có thể xóa nó lần nữa.

## 6. Ví dụ thực tế: dừng worker chờ trên hàng đợi

Tạo tệp **`InterruptibleQueueWorker.java`**:

```java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

public class InterruptibleQueueWorker {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        CountDownLatch started = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            try {
                started.countDown();
                while (!Thread.currentThread().isInterrupted()) {
                    String item = queue.take();
                    System.out.println("Processed: " + item);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                System.out.println(
                        "Worker ended; interrupted="
                                + Thread.currentThread().isInterrupted());
            }
        }, "queue-worker");

        worker.start();
        started.await();
        worker.interrupt();
        worker.join();

        System.out.println("Main observed worker termination");
    }
}
```

Biên dịch và chạy:

```bash
javac InterruptibleQueueWorker.java
java InterruptibleQueueWorker
```

Khi chạy độc lập, không có dữ liệu được thêm vào hàng đợi, kết quả là:

```text
Worker ended; interrupted=true
Main observed worker termination
```

Diễn giải:

- `started` giúp `main` đợi worker bắt đầu trước khi phát yêu cầu ngắt; nếu lệnh ngắt đến trước lúc worker gọi `queue.take()`, `take()` vẫn phát hiện cờ và ném `InterruptedException`.
- Trong lúc không có phần tử, `take()` chờ. Khi bị ngắt, nó thoát bằng `InterruptedException` và xóa cờ.
- Worker gọi lại `interrupt()` trong nhánh `catch` để khôi phục cờ, sau đó đi vào `finally`; dòng đầu vì vậy hiển thị `true`.
- `worker.join()` chỉ giúp `main` đợi luồng worker kết thúc. Nó không gửi yêu cầu ngắt cho worker. Vì `main` in sau khi `join()` trả về, thứ tự hai dòng được bảo đảm.
- `worker.isInterrupted()` là phép đọc trạng thái tại thời điểm kiểm tra; đối với một luồng đã kết thúc, **không nên dùng cờ ngắt như hồ sơ lịch sử để kết luận vì sao nó dừng**. Ví dụ ghi trạng thái ngay trong `finally` của worker trước khi kết thúc.

Về mặt thiết kế, đây là **hủy hợp tác**: `main` đề nghị dừng, worker đáp ứng tại điểm chờ có hỗ trợ ngắt, dọn dẹp rồi kết thúc. `interrupt()` không cưỡng chế đóng tài nguyên hoặc bảo đảm mọi tác vụ tùy ý sẽ dừng.

## 7. Khi nào nên dùng phương thức nào?

**Dùng `thread.isInterrupted()`** khi muốn xem một luồng đã nhận yêu cầu ngắt mà **giữ nguyên tín hiệu**. Từ bên trong worker, `Thread.currentThread().isInterrupted()` là lựa chọn dễ đọc cho vòng lặp công việc:

```java
while (!Thread.currentThread().isInterrupted()) {
    doOneSmallNonBlockingStep();
}
```

Đây là đoạn minh họa: `doOneSmallNonBlockingStep()` do ứng dụng cung cấp và phải có khả năng kết thúc mỗi lượt; nếu chặn quá lâu ở thao tác không hỗ trợ ngắt thì kiểm tra đầu vòng không giúp dừng kịp thời.

**Dùng `Thread.interrupted()`** khi mã của **chính luồng hiện tại** chủ đích nhận và tiêu thụ tín hiệu ngắt, đã quyết định rõ việc xóa cờ phù hợp với hợp đồng của mình. Ví dụ, một đoạn kiểm tra trạng thái rồi rời hàm ngay có thể chấp nhận tiêu thụ tín hiệu nếu tầng đó thực sự sở hữu toàn bộ việc xử lý hủy. Tuy nhiên, nếu tầng gọi vẫn cần nhận biết ngắt, hãy bảo toàn/khôi phục trạng thái hoặc truyền `InterruptedException`.

Một vòng lặp `while (!Thread.interrupted())` có thể dùng để phát hiện rồi rời vòng lặp, nhưng lưu ý rằng cờ đã bị xóa lúc điều kiện trả `true`. Nếu mã phía sau đợi cờ vẫn còn hoặc muốn chuyển tín hiệu hủy lên tầng cao, hành vi này dễ gây lỗi. Với vòng lặp không cần tiêu thụ cờ, `isInterrupted()` thường diễn đạt ý định rõ hơn.

### 7.1. Quan sát luồng khác

Có thể dùng `worker.isInterrupted()` từ một luồng giám sát để đọc trạng thái ngắt của `worker` mà không xóa cờ của nó. Tuy nhiên, trạng thái cờ có thể thay đổi giữa lúc kiểm tra và lúc hành động; đây không phải một cơ chế đồng bộ hoặc kết quả nghiệp vụ hoàn chỉnh. Cần phối hợp với `join()`, `Future`, latch hoặc cơ chế kết thúc phù hợp nếu muốn biết công việc đã thật sự kết thúc.

Viết `worker.interrupted()` là sai ý định: vì `interrupted()` là `static`, nó đọc và xóa cờ của **luồng giám sát đang gọi**, không đọc cờ của `worker`. Dù một trình biên dịch cho phép cú pháp qua biến, hãy luôn gọi `Thread.interrupted()` để thể hiện chính xác rằng phương thức là static.

### 7.2. Quan hệ với `Future.cancel(true)`

Trong công việc do `ExecutorService` quản lý, `Future.cancel(true)` yêu cầu hủy và **có thể gửi interrupt tới luồng đang chạy tác vụ**; `cancel(false)` không yêu cầu ngắt tác vụ đang chạy. Trạng thái của `Future` và cờ ngắt của worker là hai khái niệm khác nhau. Không suy ra rằng `cancel(true)` sẽ buộc một tác vụ phớt lờ ngắt phải dừng ngay.

Một tác vụ chạy trong pool nên có chiến lược phản hồi khi bị ngắt và dọn dẹp an toàn. Tuy nhiên, không nên tự ý gọi `Thread.interrupted()` chỉ để “làm sạch” cờ của worker; việc xóa tín hiệu có thể khiến logic hủy phía ngoài khó hiểu. Khi hợp đồng thư viện hoặc framework yêu cầu quản lý tín hiệu khác, cần tuân theo hợp đồng đó một cách nhất quán.

## 8. Các lỗi thường gặp

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| Gọi `Thread.interrupted()` chỉ để “kiểm tra xem có bị ngắt không” | Xóa mất cờ, mã phía sau có thể không thấy yêu cầu ngắt | Dùng `Thread.currentThread().isInterrupted()` nếu chỉ muốn xem |
| Gọi `otherThread.interrupted()` để hỏi về luồng khác | Thực tế lại kiểm tra/xóa cờ của luồng gọi | Gọi `otherThread.isInterrupted()` |
| Bắt `InterruptedException` rồi bỏ qua | Tác vụ có thể tiếp tục mặc dù đã được yêu cầu dừng | Truyền ngoại lệ lên hoặc khôi phục cờ và kết thúc phù hợp |
| Cho rằng `isInterrupted() == false` trong `catch` chứng minh chưa bị ngắt | Bỏ qua việc API chờ có thể đã xóa cờ khi ném ngoại lệ | Dựa vào `InterruptedException` vừa bắt, xử lý và khôi phục nếu cần |
| Dùng `interrupt()` như lệnh dừng cứng | Công việc không hợp tác vẫn chạy | Chọn điểm chờ có thể ngắt và kiểm tra cờ trong các vòng tính toán |
| Kết luận `isInterrupted() == true` nghĩa là luồng đã dừng | Ngắt là yêu cầu; luồng có thể vẫn đang chạy | Dùng `join()` hoặc cơ chế hoàn tất phù hợp |
| Dùng `Thread.interrupted()` rồi chạy tiếp, không xem xét tín hiệu đã tiêu thụ | Làm mất ý nghĩa của yêu cầu hủy đối với tầng gọi | Xác định rõ trách nhiệm xử lý; khôi phục nếu cần chuyển tiếp |

Một lưu ý thêm về luồng chạy đồng thời: không dùng cờ ngắt làm biến trạng thái nghiệp vụ để thay thế cho `volatile`, khóa, hàng đợi hay các primitive đồng bộ khác. Cờ ngắt có vai trò riêng là phối hợp hủy/ngắt công việc.

## 9. Câu hỏi tự kiểm tra

1. Luồng `main` gọi `worker.interrupt()`, sau đó gọi `Thread.interrupted()` trong chính `main`. Phương thức đó kiểm tra cờ của ai? **Của `main`**, vì `interrupted()` là static và luôn tác động lên luồng hiện tại; nó không đọc cờ của `worker`.
2. Luồng đang có cờ ngắt `true`. Gọi `Thread.currentThread().isInterrupted()` hai lần liên tiếp mà không có thay đổi khác sẽ trả gì? **`true` và `true`**, bởi phương thức này không xóa cờ.
3. Cùng điều kiện trên, gọi `Thread.interrupted()` hai lần liên tiếp trả gì? **`true` và `false`**, vì lần đầu đã xóa cờ.
4. Vì sao `isInterrupted()` ngay trong `catch (InterruptedException e)` thường có thể trả `false`? Vì API chờ đã **xóa cờ khi ném `InterruptedException`**.
5. Trong `Runnable.run()`, nên làm gì nếu `BlockingQueue.take()` bị ngắt và chương trình cần ngừng worker? **Khôi phục cờ bằng `Thread.currentThread().interrupt()` rồi rời công việc và dọn dẹp**, hoặc thực hiện chính sách ngắt đã được ứng dụng quy định.
6. Có thể dùng `isInterrupted()` để kiểm tra luồng khác không? **Có**, qua `otherThread.isInterrupted()`. Điều đó chỉ đọc cờ tại thời điểm quan sát, không bảo đảm tác vụ đã hoàn tất.

**Ghi nhớ:** `Thread.interrupted()` hỏi *“luồng hiện tại có bị ngắt không?”* và **xóa** trạng thái; `thread.isInterrupted()` hỏi *“luồng này có bị ngắt không?”* và **giữ nguyên** trạng thái. Chọn đúng API dựa trên việc bạn cần **chỉ quan sát** hay **chủ động tiêu thụ** tín hiệu ngắt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-004">Lan truyền InterruptedException khi hợp đồng cho phép</a>

<details>
<summary>Xem chi tiết</summary>

# Lan truyền InterruptedException khi hợp đồng cho phép

## 1. Vì sao cần lan truyền tín hiệu ngắt?

Trong Java, **interruption** là một cơ chế để một thread thông báo cho thread khác rằng công việc hiện tại nên được dừng, hủy hoặc xem xét lại. Đây là **yêu cầu hợp tác**: gọi **Thread.interrupt()** không buộc JVM giết thread và không tự đảo ngược dữ liệu đã thay đổi.

Khi một thread đang chờ tại một phương thức có thể bị ngắt, ví dụ **Thread.sleep()**, **CountDownLatch.await()**, **BlockingQueue.take()**, **Future.get()** hoặc **Thread.join()**, phương thức đó có thể ném **InterruptedException**. Ngoại lệ này giúp code nhận biết rằng yêu cầu ngắt cần được xử lý thay vì tiếp tục đợi đến khi công việc hoàn tất.

Đối với một phương thức được phép khai báo **throws InterruptedException**, lựa chọn thường phù hợp nhất là **để exception lan truyền lên caller**. Caller ở cấp cao hơn có nhiều thông tin hơn để quyết định nên dừng cả nghiệp vụ, hủy công việc liên quan, thông báo trạng thái hay chuyển quyền xử lý cho tầng điều phối.

Ví dụ một phương thức service gọi repository có thao tác chờ:

~~~java
// Hai phương thức minh họa; không phải chương trình độc lập.
static String fetch() throws InterruptedException {
    return queue.take();
}

static String load() throws InterruptedException {
    return fetch();
}
~~~

Giả sử **queue** là một **BlockingQueue<String>** đã khai báo. Nếu thread đang đợi phần tử ở **queue.take()** bị interrupt, InterruptedException đi qua **fetch()** rồi đến **load()**; hai tầng không cần bắt rồi tiếp tục chạy như thể không có gì xảy ra.

Ý nghĩa của **lan truyền** ở đây là giữ nguyên tín hiệu kiểm soát vòng đời công việc. Đây không phải exception đại diện cho lỗi nghiệp vụ thông thường và cũng không mặc nhiên đồng nghĩa một task khác trong hệ thống đã bị hủy.

## 2. Hợp đồng throws InterruptedException

**InterruptedException** là checked exception thuộc **java.lang** và kế thừa **Exception**. Một phương thức gọi API có thể ném InterruptedException phải:

1. Khai báo **throws InterruptedException** và để lỗi lan truyền; hoặc
2. Bắt exception để xử lý theo một chính sách rõ ràng.

Nếu tầng hiện tại chỉ là một bước trung gian và có quyền khai báo checked exception, cách thứ nhất thường đơn giản, an toàn và thể hiện đúng hợp đồng.

~~~java
// Minh họa một chuỗi gọi hợp lệ theo cấu trúc Java.
static void lowLevel() throws InterruptedException {
    Thread.sleep(1_000);
}

static void middleLevel() throws InterruptedException {
    lowLevel();
}

static void higherLevel() throws InterruptedException {
    middleLevel();
}
~~~

Đoạn này là các phương thức độc lập về logic nhưng cần đặt trong một class để biên dịch. **higherLevel()** không cần tạo một exception mới; nó cho caller biết hoạt động có thể bị gián đoạn.

Một chữ ký **throws InterruptedException** là thông tin thiết kế API quan trọng:

- Caller biết đây là lời gọi có thể bị ngắt trong lúc chờ.
- Caller có thể chủ động quyết định việc thoát, cleanup hay tiếp tục ở ranh giới sở hữu công việc.
- Code review dễ phát hiện một tầng trung gian vô tình nuốt hoặc đổi nghĩa interrupt.
- Test có thể xác minh interruption đi xuyên qua các tầng.

Tuy nhiên, không nên khai báo throws một cách tùy tiện ở API không thật sự cần thiết. Chỉ chuyển tín hiệu này thành một phần của hợp đồng khi có thao tác interruptible hoặc có lý do giao tiếp phù hợp với caller.

## 3. Interrupt flag và exception liên quan nhau như thế nào?

Mỗi đối tượng **Thread** có trạng thái interrupt riêng. Một thread khác có thể gọi **target.interrupt()** để thiết lập yêu cầu ngắt. Điều gì xảy ra tiếp theo phụ thuộc mã và thao tác thread đang thực hiện:

- Nếu thread đang chờ ở một API interruptible, thao tác chờ có thể dừng và ném InterruptedException.
- Nếu thread đang chạy code tính toán thông thường, lời gọi interrupt chủ yếu thiết lập cờ; code phải kiểm tra cờ hoặc gặp API interruptible mới có phản ứng thích hợp.
- Nếu API không hỗ trợ interruption, interrupt có thể không dừng thao tác đó.

Hai cách kiểm tra cờ thường bị nhầm:

| API | Chức năng | Có xóa interrupt flag không? |
| --- | --- | --- |
| **Thread.currentThread().isInterrupted()** | Đọc trạng thái interrupt của thread hiện tại | Không |
| **someThread.isInterrupted()** | Đọc trạng thái interrupt của thread được tham chiếu | Không |
| **Thread.interrupted()** | Đọc trạng thái interrupt của **thread đang gọi** | **Có**, xóa cờ khi kiểm tra |
| **Thread.currentThread().interrupt()** | Đặt lại/yêu cầu interrupt trên thread hiện tại | Thiết lập cờ |

Nhiều API blocking theo hợp đồng JDK, bao gồm **CountDownLatch.await()**, khi phát hiện interruption và ném InterruptedException sẽ **xóa trạng thái interrupted** của thread trước khi ném. Bởi vậy, sau khi bắt InterruptedException, **isInterrupted()** có thể là false dù đã có yêu cầu ngắt hợp lệ.

Điểm quyết định chính sách:

- **Nếu có thể ném InterruptedException lên tầng gọi**, chỉ cần **throw** hoặc **throw e** là thường đủ để thông báo tín hiệu ngắt. Tầng gọi nhận exception có ý nghĩa; không bắt buộc phải tự đặt lại interrupt flag trong từng tầng trung gian.
- **Nếu không thể ném checked exception**, ví dụ trong **Runnable.run()**, code thường nên khôi phục interrupt flag bằng **Thread.currentThread().interrupt()** rồi kết thúc hoặc để ranh giới điều phối quyết định.
- **Nếu đang chuyển sang một kiểu exception khác**, phải xác định tầng gọi có còn nhận biết interruption không. Thường nên bảo tồn interrupt flag và cause khi không thể truyền trực tiếp InterruptedException.

Không có một quy tắc “luôn re-interrupt rồi luôn throw” cho mọi API. Nếu vừa khôi phục interrupt flag vừa tiếp tục gọi các phương thức blocking, chúng có thể ném InterruptedException ngay sau đó. Chính sách phải phù hợp với hợp đồng hiện tại và ranh giới sở hữu thread.

## 4. Ví dụ Java 17+: lan truyền qua các tầng, xử lý ở Runnable boundary

Chương trình sau mô phỏng ba tầng:

1. **repositoryWait** là tầng thấp dùng **CountDownLatch.await()**, có thể ném InterruptedException.
2. **serviceWait** cần một bước quan sát nhưng vẫn **throw failure** để truyền đúng exception lên trên.
3. **Runnable** là ranh giới cuối cùng; **run()** không khai báo checked InterruptedException nên nó bắt, khôi phục interrupt flag và kết thúc công việc.

Dùng hai latch để phối hợp giúp kết quả không phụ thuộc vào giả định thread chạy nhanh hay chậm. **gate** không bao giờ được mở; việc chờ chỉ có thể kết thúc ở ví dụ này bằng interruption.

Lưu vào file **InterruptPropagationDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;

public class InterruptPropagationDemo {
    static void repositoryWait(CountDownLatch gate)
            throws InterruptedException {
        try {
            gate.await();
        } finally {
            System.out.println("Repository cleanup");
        }
    }

    static void serviceWait(CountDownLatch gate)
            throws InterruptedException {
        try {
            repositoryWait(gate);
        } catch (InterruptedException failure) {
            System.out.println("Service propagates interruption");
            throw failure;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch gate = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            try {
                ready.countDown();
                serviceWait(gate);
                System.out.println("Unexpected completion");
            } catch (InterruptedException failure) {
                System.out.println("Boundary caught: InterruptedException");
                System.out.println("Flag in catch: "
                        + Thread.currentThread().isInterrupted());
                Thread.currentThread().interrupt();
                System.out.println("Flag restored: "
                        + Thread.currentThread().isInterrupted());
            }
        }, "worker");

        worker.start();
        ready.await();
        worker.interrupt();
        worker.join();
        System.out.println("Worker finished: " + !worker.isAlive());
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac --release 17 InterruptPropagationDemo.java
java InterruptPropagationDemo
~~~

Kết quả stdout đã kiểm tra bằng cách biên dịch và chạy:

~~~text
Repository cleanup
Service propagates interruption
Boundary caught: InterruptedException
Flag in catch: false
Flag restored: true
Worker finished: true
~~~

### Diễn giải trình tự

**ready.countDown()** xác nhận worker đã bắt đầu. Sau khi main đi qua **ready.await()**, main gọi **worker.interrupt()**. Lúc này worker có thể đã ở trong **gate.await()** hoặc sắp gọi await; cả hai trường hợp đều khiến chờ trên gate phát hiện trạng thái interrupt và ném InterruptedException.

Trong **repositoryWait**, **finally** vẫn chạy và in **Repository cleanup**. Exception đang được ném tiếp, vì finally không tự thay thế nó bằng một exception khác.

Trong **serviceWait**, catch chỉ ghi dấu mốc và **throw failure**: cùng một instance InterruptedException tiếp tục truyền lên. Vì tầng service được khai báo **throws InterruptedException**, nó không cần che exception bằng RuntimeException và không cần khôi phục cờ tại đây.

**CountDownLatch.await()** xóa trạng thái interrupt khi ném exception, nên ở **Runnable** giá trị **isInterrupted()** được in là false. Vì **Runnable.run()** không được phép khai báo throws InterruptedException, worker khôi phục cờ rồi kết thúc nhánh catch. Main dùng **join()** để xác nhận thread đã kết thúc trước khi in dòng cuối.

Ví dụ minh họa hợp đồng này trong một đường chạy có điều khiển. Nó không có nghĩa mọi API đều xóa cờ khi ném mọi loại exception; phải dựa vào hợp đồng của từng phương thức.

## 5. Lan truyền nguyên trạng tốt hơn khi nào?

Trong một hệ thống nhiều lớp, tầng thấp thường không phải chủ sở hữu vòng đời toàn bộ công việc. Chẳng hạn:

~~~text
Controller / job coordinator
    |
    v
Application service
    |
    v
Repository / integration adapter
    |
    v
BlockingQueue.take() hoặc CountDownLatch.await()
~~~

Nếu tầng repository bắt InterruptedException rồi tiếp tục lặp vô hạn, tầng controller có thể không bao giờ xử lý được yêu cầu hủy request. Nếu tầng service tự chuyển mọi interruption thành lỗi dữ liệu, tầng coordinator dễ retry sai và gây tải không cần thiết.

Khi các chữ ký phương thức cho phép, hãy để **InterruptedException** truyền lên đến nơi có thể đưa ra quyết định về vòng đời tác vụ. Một tầng trung gian vẫn có thể catch để:

- Đảm bảo cleanup cục bộ.
- Ghi thông tin chẩn đoán thật sự cần thiết.
- Điều chỉnh dữ liệu tạm chưa công bố theo hợp đồng.
- Đính kèm ngữ cảnh mà không làm thay đổi bản chất tín hiệu ngắt.

Sau đó **ném lại đúng exception** nếu tầng trên vẫn được phép nhận InterruptedException.

Không nhất thiết phải catch chỉ để log rồi throw ở tất cả các tầng. Cách này có thể tạo log trùng lặp và không bổ sung ích lợi thực tế. Một phương thức chỉ cần:

~~~java
// Mã minh họa bên trong một class có biến queue thích hợp.
static String load(BlockingQueue<String> queue)
        throws InterruptedException {
    return queue.take();
}
~~~

Chữ ký thể hiện đầy đủ hợp đồng gián đoạn; không cần catch thừa.

## 6. Không nuốt InterruptedException

Một lỗi thường gặp:

~~~java
// Phản mẫu minh họa.
try {
    blockingCall();
} catch (InterruptedException ignored) {
    // Tiếp tục xử lý như chưa từng có yêu cầu hủy.
}
doNextStep();
~~~

Đây là ví dụ minh họa trong phương thức lớn hơn. Nếu blockingCall dừng do interrupt, mã tiếp tục **doNextStep()** có thể làm các side effect mà bên điều phối đang yêu cầu dừng lại.

Một phản mẫu khác:

~~~java
// Phản mẫu minh họa: vòng lặp không có đường thoát.
while (true) {
    try {
        process(queue.take());
    } catch (InterruptedException ignored) {
        // Nuốt tín hiệu rồi quay lại take().
    }
}
~~~

Một API blocking thường xóa flag khi ném InterruptedException. Sau khi exception bị nuốt, vòng lặp có thể quay lại chờ vô thời hạn với cờ đã xóa. **Interrupt không còn khả năng điều phối việc thoát** nếu code liên tục bỏ qua nó.

Nếu API gọi cho phép:

~~~java
// Phương thức minh họa; queue là BlockingQueue<Task>.
static void runOne(BlockingQueue<Task> queue)
        throws InterruptedException {
    Task task = queue.take();
    task.execute();
}
~~~

Chỉ cần truyền InterruptedException từ **take()**. Đoạn code theo ngữ cảnh này giả sử đã có kiểu Task với phương thức execute; đây không phải chương trình chạy độc lập.

Nếu code không được phép khai báo checked exception và không có chính sách xử lý đầy đủ tại đây, cách thường dùng là khôi phục flag rồi ngừng:

~~~java
// Trong một Runnable hoặc callback không khai báo InterruptedException.
try {
    blockingCall();
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    return;
}
~~~

Việc **return** ở đây là kết thúc tác vụ hiện tại sau khi đã khôi phục cờ, khác với **return trong finally** có thể che lỗi. Nếu còn tài nguyên cần cleanup, cleanup phải được tổ chức để vẫn chạy trước khi tác vụ kết thúc.

## 7. Vì sao không cần re-interrupt ở từng tầng khi đang throw?

Xét chuỗi sau:

~~~java
// Minh họa phương thức có hợp đồng throws InterruptedException.
static void service() throws InterruptedException {
    try {
        Thread.sleep(10_000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw e;
    }
}
~~~

Đoạn này không nhất thiết sai trong mọi bối cảnh, nhưng bước **interrupt()** ngay trước **throw e** thường **không cần thiết** nếu mục tiêu duy nhất là báo interruption cho caller. Bản thân exception đã truyền tín hiệu. Khi tầng trên nhận lỗi và dự định thực hiện cleanup có thao tác blocking, interrupt flag được đặt lại sớm có thể khiến thao tác cleanup đó bị ngắt ngay. Hợp đồng tầng trên mới quyết định thời điểm xử lý flag.

Một cách đơn giản hơn:

~~~java
// Minh họa phương thức có hợp đồng throws InterruptedException.
static void service() throws InterruptedException {
    Thread.sleep(10_000);
}
~~~

Nếu cần catch để dọn dẹp, vẫn có thể giữ nguyên exception:

~~~java
// Minh họa; releaseLocalState() phải theo hợp đồng riêng.
try {
    blockingOperation();
} catch (InterruptedException e) {
    releaseLocalState();
    throw e;
}
~~~

Lưu ý: nếu **releaseLocalState()** cũng ném exception, nó có thể che khuất InterruptedException. Với cleanup có thể ném lỗi, cần dùng cơ chế bảo toàn primary exception phù hợp, ví dụ **try-with-resources** cho AutoCloseable hoặc suppressed exceptions khi xử lý thủ công. Không chỉ quan tâm việc ghi **throw e** mà bỏ qua ảnh hưởng của đường cleanup.

## 8. Giữ đúng kiểu InterruptedException và stack trace

Khi lan truyền:

- **throw e** ném lại chính đối tượng vừa bắt và giữ những thông tin có trên exception đó.
- **throws InterruptedException** trên chữ ký cho caller biết loại checked exception có thể xuất hiện.
- Không tạo **new InterruptedException(e.getMessage())** nếu không có lý do đặc biệt: cách này tạo đối tượng mới và không tự bảo toàn đầy đủ cause/suppressed/stack trace của exception ban đầu.
- Không bao bọc thành một RuntimeException chung khi chữ ký có thể truyền nguyên trạng; caller có thể không còn catch được InterruptedException như mong đợi.

Trong một service interface mà nhiều implementation cần hỗ trợ gián đoạn, việc đưa InterruptedException vào chữ ký có thể là quyết định hợp lý. Nếu đó là API công khai không cho phép checked exception, cần chính sách chuyển đổi đã được tài liệu hóa; tránh che interruption bằng thông báo nghiệp vụ không liên quan.

### Nếu muốn thêm ngữ cảnh

Không phải lúc nào cũng cần tạo một exception mới. Có thể ghi context ở một ranh giới điều phối, giữ Throwable nguyên vẹn và để tầng trên quyết định. Nếu API bắt buộc wrap, cần:

1. Khôi phục interrupt flag nếu không còn truyền trực tiếp InterruptedException theo hợp đồng.
2. Bảo tồn original dưới dạng **cause** khi kiểu wrapper hỗ trợ.
3. Bảo đảm tầng gọi hiểu wrapper đó biểu diễn sự gián đoạn, không phải lỗi nghiệp vụ thông thường.
4. Tránh retry tự động các thao tác không idempotent.

Ví dụ chuyển đổi trong callback không khai báo checked exception:

~~~java
// Mã minh họa trong callback không khai báo checked exceptions.
try {
    blockingOperation();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Operation interrupted", e);
}
~~~

Đây là một chính sách API có thể dùng trong một số trường hợp, nhưng không có nghĩa **IllegalStateException** là kiểu cancellation tiêu chuẩn hay mọi callback đều nên xử lý như vậy. Nếu API cung cấp một kiểu exception/cancellation thích hợp hơn, ưu tiên hợp đồng của API đó. Có callback nên kết thúc bình thường sau khi ghi trạng thái hủy thay vì ném unchecked exception.

## 9. Runnable khác Callable về hợp đồng exception

**Runnable.run()** không khai báo checked exception. Vì thế một method reference/lambda Runnable không thể đơn giản gọi API throws InterruptedException rồi để exception đó đi xuyên qua **run()** nếu không có catch hoặc chuyển đổi phù hợp.

~~~java
// Minh họa Runnable trong một phương thức.
Runnable task = () -> {
    try {
        Thread.sleep(10_000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
    }
};
~~~

Trong khi đó, **Callable<V>.call()** được khai báo **throws Exception**, nên một Callable có thể để InterruptedException truyền ra:

~~~java
// Minh họa Callable theo ngữ cảnh.
Callable<String> task = () -> {
    Thread.sleep(10_000);
    return "done";
};
~~~

Tuy nhiên, khi gửi Callable qua **ExecutorService.submit()**, caller bên ngoài thường không nhận InterruptedException do *worker ném* theo kiểu trực tiếp khi gọi **Future.get()**. Nếu task thất bại với InterruptedException và Future chưa canceled, **Future.get()** thường ném **ExecutionException** có **getCause()** là InterruptedException.

Cần phân biệt:

| Tình huống | Tín hiệu người gọi Future.get() nhận |
| --- | --- |
| **Chính thread đang gọi get()** bị interrupt trong lúc chờ | **InterruptedException** trực tiếp |
| **Callable của worker** ném InterruptedException và Future hoàn tất bất thường | **ExecutionException**, cause là InterruptedException |
| Future đã chuyển sang trạng thái canceled | **CancellationException** |
| Một lần gọi get(timeout) hết hạn chờ | **TimeoutException** |

Vì vậy, không được kết luận mọi InterruptedException quan sát ở code bất đồng bộ đều liên quan cùng một thread. Có hai chủ thể khác nhau: **caller đang đợi** và **worker đang thực thi**.

### CompletableFuture

**CompletableFuture.supplyAsync** dùng **Supplier<T>**, mà phương thức **get()** của Supplier không khai báo throws InterruptedException. Do đó lambda supplier phải catch hoặc dùng một adapter theo chính sách hủy. **CompletableFuture.get()** và **join()** cũng có hợp đồng chờ/exception khác nhau: get khai báo InterruptedException, còn join không khai báo checked InterruptedException.

Đừng giả định rằng **CompletableFuture.cancel(true)** bắt buộc interrupt supplier đang chạy. CompletableFuture sử dụng trạng thái completion riêng và tham số **mayInterruptIfRunning** không điều khiển computation theo cùng cách FutureTask thông thường. Nếu cần ngắt công việc thật sự, quản lý riêng Future/task bên dưới hoặc token hủy phù hợp.

## 10. Không đồng nhất InterruptedException với CancellationException

**InterruptedException** nói rằng thao tác chờ có thể ngắt của thread hiện tại đã bị gián đoạn. **CancellationException** mô tả trạng thái hủy của đối tượng Future hoặc kết quả bất đồng bộ. Một yêu cầu cancel(true) đối với Future có thể khiến cơ chế executor gửi interrupt cho worker, nhưng hai ngoại lệ vẫn xuất hiện ở những vị trí và ý nghĩa khác nhau.

Một Future có thể đã canceled và **get()** ném CancellationException dù worker chưa xử lý interrupt xong. Cũng có thể thread bị interrupt khi gọi Future.get() trong lúc task vẫn tiếp tục làm việc, khiến caller nhận InterruptedException mà Future chưa canceled.

**TimeoutException** lại mô tả một lần chờ có timeout đã hết hạn; nó không tự interrupt caller hoặc cancel Future. Chỉ ứng dụng mới quyết định tiếp tục chờ, trả fallback, gửi yêu cầu hủy hay kết thúc request.

Khi truyền InterruptedException lên trên, caller cần đánh giá có phải hủy những task con liên quan không. Đây là chính sách của nhóm công việc, không phải một tác dụng phụ tự động của từ khóa **throws**.

## 11. Cleanup khi lan truyền interruption

Khi interruption xảy ra giữa một thao tác có tài nguyên, code vẫn phải bảo đảm cleanup thích hợp. Cấu trúc **try/finally** cho phép cleanup chạy khi exception truyền lên:

~~~java
// Minh họa: lock được khai báo và có thể lấy bằng lockInterruptibly().
lock.lockInterruptibly();
try {
    blockingOperation();
} finally {
    lock.unlock();
}
~~~

Điểm quan trọng là **lock.lockInterruptibly()** nằm trước khối try. Nếu acquire bị interrupt và ném InterruptedException thì thread chưa sở hữu lock theo đường thành công, và code không được gọi unlock như thể đã giữ lock. Sau khi acquire thành công, finally chịu trách nhiệm unlock khi thân try kết thúc hoặc ném lỗi.

Một phản mẫu dễ sai:

~~~java
// Phản mẫu minh họa, không dùng trong chương trình thật.
try {
    lock.lockInterruptibly();
    blockingOperation();
} finally {
    lock.unlock(); // Có thể unlock khi chưa hề lock thành công.
}
~~~

Nếu lockInterruptibly thất bại trước khi lấy khóa, unlock có thể ném **IllegalMonitorStateException**, che lỗi InterruptedException ban đầu. Hãy đặt phạm vi cleanup sau khi tài nguyên/khóa đã được nhận thành công, hoặc theo dõi rõ việc acquire có thành công không.

### Cleanup có thể tự thất bại

Nếu xử lý chính ném InterruptedException và close/rollback trong finally lại ném exception mới, exception cleanup có thể che khuất tín hiệu interrupt. Với tài nguyên **AutoCloseable**, ưu tiên **try-with-resources**, vì lỗi close sẽ được lưu dưới dạng **suppressed** khi thân try đã ném primary exception.

~~~java
// Minh họa: resource là AutoCloseable.
try (var resource = openResource()) {
    resource.blockingAction(); // Có thể throws InterruptedException.
}
~~~

Đoạn này cần nằm trong bối cảnh API thích hợp; nó chỉ là mã minh họa. Để giữ InterruptedException truyền nguyên trạng, caller hoặc phương thức bao cần chấp nhận loại checked exception tương ứng, cùng những checked exceptions khác mà close có thể ném theo hợp đồng của resource.

**Cleanup không phải rollback tự động.** Giải phóng khóa không đảo ngược object mutable đã sửa; đóng stream không xóa byte đã ghi; hủy chờ không chứng minh request từ xa chưa tạo side effect. Khi interruption xảy ra, các quy tắc bảo toàn trạng thái và giao dịch vẫn cần được thiết kế riêng.

## 12. Giữ trạng thái nhất quán khi ngắt giữa nhiều bước

Việc truyền InterruptedException đúng loại chỉ giải quyết luồng điều khiển và thông tin hủy. Nếu code đã sửa dữ liệu trước khi gọi một blocking operation, trạng thái có thể thay đổi một phần:

~~~java
// Phản mẫu minh họa: entries là mutable list đã được công bố.
entries.add(item);
remoteGate.await(); // Có thể ném InterruptedException.
commitOtherFields();
~~~

Nếu remoteGate.await bị ngắt, danh sách đã thêm item nhưng phần cập nhật khác chưa hoàn tất. Để bảo toàn trạng thái mạnh, có thể:

1. Kiểm tra điều kiện và chuẩn bị dữ liệu mới trên biến cục bộ/snapshot riêng.
2. Thực hiện các thao tác chờ hoặc tính toán có thể ném InterruptedException trước khi công bố.
3. Chỉ commit khi mọi điều kiện đã thỏa và vẫn được phép công bố kết quả.
4. Nếu thao tác đã gửi side effect bên ngoài, áp dụng transaction, idempotency hoặc quy trình bù trừ phù hợp.

Mã minh họa:

~~~java
// Minh họa phương thức theo ngữ cảnh; không phải chương trình độc lập.
Snapshot candidate = prepareCandidate(current);
remoteGate.await();  // Nếu bị ngắt, current vẫn giữ nguyên.
current = candidate;  // Commit sau khi chuẩn bị thành công.
~~~

Điều này chỉ cung cấp đảm bảo trong phạm vi trạng thái mà **current** thật sự sở hữu và kiểm soát. Nếu chuẩn bị candidate còn sửa object mutable dùng chung hoặc remoteGate tạo side effect bên ngoài, phép gán cuối không biến toàn bộ hệ thống thành một giao dịch nguyên tử.

Ngoài ra, giữa lần kiểm tra cờ interrupt và commit có thể có race: yêu cầu interrupt có thể đến ngay sau khi kiểm tra. Hệ thống cần xác định điểm nào được xem là đã commit và hứa hẹn cancellation đến mức nào; không thể tùy tiện cam kết rằng *mọi yêu cầu interrupt đến bất kỳ thời điểm nào* đều ngăn mọi side effect.

## 13. Gián đoạn trong vòng lặp làm việc

Một worker chạy nhiều đơn vị công việc nên có đường thoát rõ:

~~~java
// Minh họa logic của worker, không phải chương trình độc lập.
static void consume(BlockingQueue<Job> queue)
        throws InterruptedException {
    while (true) {
        Job job = queue.take();
        job.process();
    }
}
~~~

Ở đây **queue.take()** có thể ném InterruptedException. Nếu tầng gọi cho phép, exception sẽ thoát khỏi **consume()**, từ đó vòng lặp chấm dứt. Không cần bắt InterruptedException rồi **continue**: làm vậy có thể khiến worker phớt lờ yêu cầu ngắt.

Nhưng nếu **job.process()** là CPU-bound kéo dài hoặc dùng I/O không nhận interrupt, tín hiệu ngắt có thể không được xử lý trong khoảng thời gian đó. Có thể thiết kế các điểm kiểm tra:

~~~java
// Minh họa theo ngữ cảnh; không phải chương trình độc lập.
while (!Thread.currentThread().isInterrupted()) {
    doSmallUnitOfWork();
}
~~~

Điều kiện **isInterrupted()** chỉ kiểm tra flag; nó không tự ném InterruptedException. Nếu hợp đồng worker muốn thông báo ngắt qua checked exception, cần lựa chọn một điểm chuyển tín hiệu phù hợp hoặc sử dụng các API interruptible; đừng tự tạo InterruptedException tùy tiện khi chưa xác định semantics.

### Khi cần dừng vòng lặp nhưng cleanup có tính bắt buộc

Một worker có thể có finally để trả connection/lock sau khi vòng lặp kết thúc. Khi interruption ném qua các tầng, finally chạy trên đường thoát trong điều kiện thực thi thông thường. Nếu cleanup cần thực hiện thao tác blocking mới, phải xem xét flag đã được xóa hay chưa và cleanup có chính sách timeout riêng không. Tránh chặn vô hạn trong cleanup của đường hủy.

## 14. Interruption trong ExecutorService

Khi một task được submit vào ExecutorService:

- **Future.cancel(true)** có thể đề nghị interrupt task đang chạy nếu executor và task cho phép.
- **ExecutorService.shutdownNow()** cố ngắt các worker đang thực thi, nhưng không cam kết mọi task dừng ngay lập tức.
- **ExecutorService.shutdown()** không tự gửi interrupt tới tất cả task đã được nhận như shutdownNow.
- **awaitTermination(timeout, unit)** là lời gọi có thể ném InterruptedException trên thread đang đợi executor kết thúc.

Nếu thread quản lý executor bị interrupt trong khi đợi shutdown, policy phù hợp có thể là ngừng đợi, yêu cầu shutdownNow khi thật sự sở hữu executor, sau đó khôi phục flag hoặc ném tiếp InterruptedException. Không được tắt một executor dùng chung chỉ vì caller của một request bị interrupt khi tầng đó không sở hữu vòng đời executor.

Ví dụ minh họa về một ranh giới quản lý executor đã sở hữu:

~~~java
// Mã minh họa, executor thuộc quyền sở hữu của phạm vi này.
executor.shutdown();
try {
    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
    }
} catch (InterruptedException e) {
    executor.shutdownNow();
    Thread.currentThread().interrupt();
}
~~~

Đây là chính sách dành cho một ngữ cảnh **không tiếp tục ném InterruptedException** và có quyền yêu cầu dừng executor. Nếu phương thức cho phép **throws InterruptedException**, có thể thay ranh giới bằng cách ném tiếp sau cleanup phù hợp. Không áp dụng máy móc vào thread pool dùng chung của server.

## 15. API không cho phép checked exception: chuyển đổi có trách nhiệm

Một số interface không khai báo checked exception, ví dụ **Runnable**, **Supplier**, một số callback hoặc phương thức override bị ràng buộc chữ ký. Không thể thêm **throws InterruptedException** vào một phương thức override nếu interface không cho phép.

Khi đó các lựa chọn thường gặp gồm:

- **Khôi phục interrupt flag và kết thúc công việc** khi interruption mang nghĩa yêu cầu dừng.
- **Chuyển thành exception unchecked có cause rõ ràng** khi framework cần đường báo lỗi bất thường và hợp đồng cho phép.
- **Đưa trạng thái canceled vào kết quả nghiệp vụ** nếu API được thiết kế để đại diện cancellation bằng một trạng thái riêng.
- **Dùng wrapper/adaptor bên ngoài** để đặt thao tác blocking vào giao diện có thể khai báo checked exception, ví dụ Callable khi thích hợp.

Không có một lựa chọn đúng tuyệt đối cho mọi callback. Tuy nhiên, **catch và bỏ qua** thường là sai vì làm tín hiệu ngắt biến mất.

Khi chuyển thành unchecked exception, không dùng một exception mơ hồ rồi để hệ thống retry vô điều kiện. Caller hoặc framework cần nhận biết lý do là interrupt/cancellation để quyết định vận hành phù hợp. Và việc đặt lại interrupt flag phải xét hợp đồng thread pool; không thực hiện hoạt động blocking tiếp theo như thể cờ đã được xóa.

## 16. Cách kiểm thử việc lan truyền InterruptedException

Một kiểm thử tốt cần kiểm tra cả **đường truyền exception** và **hành vi của thread sở hữu tác vụ**. Không nên chỉ gọi Thread.sleep với con số đoán trước rồi hy vọng một thread đã vào đúng vị trí blocking.

Ưu tiên dùng **CountDownLatch**, barrier hoặc công cụ đồng bộ để kiểm soát thời điểm:

1. Tạo latch **ready** cho biết thread đã bắt đầu.
2. Giữ một latch **gate** chưa mở để bảo đảm thao tác chờ chưa thể thành công.
3. Khi ready được phát tín hiệu, gọi **interrupt()** lên đúng thread đang kiểm thử.
4. Cho exception truyền qua phương thức tầng thấp và service bằng **throws InterruptedException**.
5. Tại boundary không khai báo checked exception, ghi nhận exception, khôi phục flag theo hợp đồng và thoát.
6. **join()** thread để xác minh không còn chạy; nếu là test production, cần timeout an toàn để tránh treo bộ test khi mã có lỗi.
7. Assert không có side effect sau interruption, cleanup bắt buộc đã chạy và dữ liệu chỉ thay đổi trong phạm vi đã cam kết.

Ở chương trình runnable, latch ready/gate loại bỏ sự phụ thuộc vào scheduler: interrupt xảy ra trước hay trong lời gọi await đều dẫn đến InterruptedException trên worker. Dòng **Repository cleanup** xác nhận finally đã chạy; dòng **Service propagates interruption** cho thấy tầng service ném tiếp; hai dòng **Flag** thể hiện trạng thái khi bắt và sau khi khôi phục.

### Các test case cần có

| Trường hợp | Kỳ vọng |
| --- | --- |
| Tầng thấp bị interrupt khi await | InterruptedException được ném |
| Tầng giữa khai báo throws và không nuốt | Caller nhận được InterruptedException |
| Tầng giữa catch rồi throw e | Caller vẫn nhận chính exception gốc |
| Tầng giữa catch rồi continue | Test phát hiện task không dừng theo hợp đồng |
| Runnable boundary không thể throws | Flag được khôi phục nếu policy chọn re-interrupt; task kết thúc |
| Callable worker ném InterruptedException | Future.get thường ném ExecutionException có cause là InterruptedException |
| Caller bị interrupt khi Future.get | Caller nhận InterruptedException trực tiếp |
| Cleanup trong finally chạy | Resource được dọn theo hợp đồng |
| Cleanup cũng ném lỗi | Primary không bị che khuất; suppressed phù hợp |
| Interrupt gần điểm commit | Ứng dụng giữ đúng semantics về thời điểm commit và side effect |

Nếu đang dùng unit test framework, có thể kiểm tra exception identity để phát hiện một tầng vô ý tạo exception mới. Trong bài test tích hợp, cần kiểm tra trạng thái tài nguyên và những tác dụng phụ thực sự, không chỉ assert kiểu Throwable.

## 17. Lỗi phổ biến và cách sửa

**Nuốt InterruptedException rồi tiếp tục.** Đây là lỗi cốt lõi: yêu cầu hủy không còn được phản ánh và task có thể thực hiện thêm side effect. Nếu được phép, khai báo throws và ném tiếp; nếu không được phép, dùng chính sách khôi phục flag/kết thúc phù hợp.

**Tự đặt lại interrupt flag ở mọi tầng dù đều throw.** Thường không cần khi caller đã nhận đúng InterruptedException. Hãy để ranh giới sở hữu công việc quyết định cách xử lý flag.

**Nhầm isInterrupted với Thread.interrupted.** Phương thức static Thread.interrupted kiểm tra và xóa cờ của thread hiện tại; dùng nó để “chỉ xem thử” có thể vô tình tiêu thụ tín hiệu.

**Chuyển interruption thành lỗi nghiệp vụ chung không có cause.** Caller mất khả năng phân biệt dừng tác vụ với lỗi xử lý dữ liệu. Nếu phải wrap, giữ cause và policy cờ ngắt.

**Tưởng Future.get ném InterruptedException nghĩa worker bị interrupt.** Đó có thể chỉ là thread caller đang chờ bị interrupt. Lỗi worker thường được chuyển qua ExecutionException khi quan sát Future chưa canceled.

**Dùng cancellation của CompletableFuture để khẳng định supplier dừng.** Trạng thái future và việc worker thực thi dừng là hai khái niệm khác nhau; cần cơ chế hủy cụ thể.

**Gọi unlock khi lockInterruptibly chưa acquire thành công.** Nếu acquire bị interrupt, unlock sai tạo IllegalMonitorStateException và có thể che lỗi gốc. Đặt try/finally sau khi lấy lock thành công.

**Để cleanup ném lỗi làm mất InterruptedException.** Nếu tài nguyên AutoCloseable, ưu tiên try-with-resources; nếu cleanup thủ công, giữ primary và suppressed.

**Cho rằng interruption rollback dữ liệu.** Interrupt chỉ là tín hiệu; các side effect trong RAM, file, database hay network cần chiến lược bảo toàn trạng thái riêng.

**Dựa vào Thread.sleep để tạo test race.** Scheduler có thể thay đổi. Dùng latch/barrier để kiểm soát điều kiện chờ và kiểm tra các kết quả được phép bởi hợp đồng.

## 18. Bảng kiểm khi viết phương thức có thể bị ngắt

Trước khi hoàn thiện một đoạn Java blocking hoặc chuỗi API có cancellation, tự hỏi:

1. Phương thức này có được phép khai báo **throws InterruptedException** không?
2. Tầng hiện tại có đủ quyền quyết định hủy tác vụ, hay nên để exception truyền lên?
3. Nếu bắt exception, có thật sự bổ sung xử lý hợp lệ trước khi **throw e** không?
4. Có catch nào nuốt interrupt, tiếp tục vòng lặp hoặc retry tự động không?
5. Khi một API blocking ném InterruptedException, nó có xóa cờ theo hợp đồng không?
6. Nếu không thể ném checked exception, boundary sẽ khôi phục flag, trả trạng thái hủy hay chuyển thành một exception khác như thế nào?
7. Đã phân biệt worker bị interrupt với thread caller của Future.get bị interrupt chưa?
8. Có cleanup nào ném exception khác và che mất lỗi interrupt không?
9. Nếu interruption xảy ra sau side effect, trạng thái có còn nhất quán và đã có cơ chế rollback/compensation cần thiết chưa?
10. Các test có kích hoạt interrupt tại đúng vị trí chờ, xác minh đường truyền exception và việc worker thoát không?

**Kết luận:** Khi chữ ký API cho phép, **lan truyền InterruptedException nguyên trạng** là một cách giữ đúng tín hiệu yêu cầu dừng và chuyển quyết định vòng đời công việc đến tầng có trách nhiệm. Không cần bắt rồi khôi phục cờ ở mọi tầng trung gian. Ở những ranh giới không được ném checked exception, phải có chính sách rõ ràng để bảo toàn ý nghĩa interruption, dọn dẹp tài nguyên và kết thúc an toàn. Interruption là tín hiệu điều phối, còn việc bảo toàn trạng thái và side effect luôn cần được thiết kế, kiểm chứng riêng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-005">Khôi phục interrupt flag khi không thể tiếp tục lan truyền</a>

<details>
<summary>Xem chi tiết</summary>

# Khôi phục interrupt flag khi không thể tiếp tục lan truyền trong Java

> **Chủ đề:** Java Core → Exception → Ngắt và hủy. Bài học giải thích cách giữ tín hiệu hủy hợp tác khi một phương thức Java bắt `InterruptedException` nhưng **không thể khai báo hoặc tiếp tục ném** ngoại lệ đó, chẳng hạn khi triển khai `Runnable.run()`. Trọng tâm là sự khác biệt giữa **ngoại lệ báo ngắt** và **cờ interrupt** gắn với luồng, cách khôi phục cờ chính xác, và cách thiết kế đường thoát để không tiếp tục công việc trái ý muốn.

## 1. Interrupt trong Java là gì?

`Thread.interrupt()` là cơ chế **báo hiệu hợp tác** tới một thread. Nó không cưỡng bức dừng luồng tại một dòng mã tùy ý, không tự rollback trạng thái và không mặc nhiên ném `InterruptedException` vào stack của thread khác. Tác vụ phải phản ứng với tín hiệu theo hợp đồng của API đang chạy.

Một thread có **interrupt status** (thường gọi là *interrupt flag*). Khi thread A gọi `worker.interrupt()`, tín hiệu được đặt cho `worker`; một số thao tác chặn ở worker sẽ phản ứng bằng cách ném `InterruptedException`. Với các thao tác chặn chuẩn như `Thread.sleep`, `Object.wait` hoặc `Thread.join`, nếu bị interrupt và ném `InterruptedException`, **trạng thái interrupt được xóa khi exception được ném**. Điều này khiến mã đã bắt exception cần chủ động quyết định có truyền tín hiệu lên hay không.

~~~text
Thread A                                 Thread B (worker)
--------                                 -----------------
worker.interrupt()
     |                                   đang Thread.sleep(...)
     |                                   -> InterruptedException
     |                                   -> interrupt status được xóa
     |                                   -> catch (InterruptedException e)
     |                                      ├─ truyền exception tiếp
     |                                      └─ không truyền được:
     |                                           interrupt() để đặt lại flag
     |                                           thoát/ủy quyền xử lý hủy
~~~

Từ khóa quan trọng là **hợp tác**: việc đặt cờ cho phép những lớp phía ngoài nhận ra yêu cầu dừng, nhưng chỉ có tác dụng nếu các lớp đó kiểm tra và tôn trọng tín hiệu.

## 2. Vì sao bắt InterruptedException có thể làm mất tín hiệu?

Ví dụ một đoạn mã trong worker:

~~~java
// Đoạn minh họa nằm bên trong một phương thức.
try {
    Thread.sleep(10_000);
} catch (InterruptedException ex) {
    System.out.println("Interrupted");
}
continueProcessing();
~~~

Với `Thread.sleep`, khi exception được ném do interrupt, interrupt status được xóa. Sau khối `catch` trên, `continueProcessing()` vẫn chạy và tầng ngoài kiểm tra `Thread.currentThread().isInterrupted()` có thể thấy `false`. Ứng dụng đã *nuốt tín hiệu* hủy.

Điều này có thể dẫn tới:

- Tác vụ vẫn lấy message mới hoặc tiếp tục lặp dù hệ thống yêu cầu dừng.
- Shutdown của `ExecutorService` kéo dài vì worker không hợp tác.
- Caller tưởng yêu cầu cancellation đã được tôn trọng, trong khi một phần side effect còn tiếp tục.
- Các đoạn mã phía ngoài không nhìn thấy interrupt status để dừng hợp tác hoặc khôi phục tài nguyên.

**Nguyên tắc:** nếu không thể truyền `InterruptedException` lên theo đúng hợp đồng phương thức, thường phải **khôi phục trạng thái interrupt bằng `Thread.currentThread().interrupt()`** rồi **kết thúc công việc hiện tại hoặc đi tới đường thoát phù hợp**.

Việc chỉ đặt lại flag nhưng tiếp tục xử lý vô hạn chưa giải quyết vấn đề hủy.

## 3. Hai cách chuẩn khi gặp InterruptedException

### Cách A — truyền exception lên nếu chữ ký cho phép

Nếu phương thức có thể khai báo `throws InterruptedException`, cách đơn giản nhất là để ngoại lệ truyền lên:

~~~java
// Ví dụ phương thức có thể khai báo checked exception.
static String receive(java.util.concurrent.BlockingQueue<String> queue)
        throws InterruptedException {
    return queue.take();
}
~~~

Nếu cần dọn dẹp trước khi truyền, bắt rồi `throw ex`:

~~~java
static String receiveWithCleanup(
        java.util.concurrent.BlockingQueue<String> queue)
        throws InterruptedException {
    try {
        return queue.take();
    } catch (InterruptedException ex) {
        logCancellation();
        throw ex;
    }
}
~~~

`logCancellation()` ở trên là hàm minh họa do ứng dụng cung cấp; đoạn này không phải chương trình Java độc lập. Khi truyền chính `InterruptedException` lên, tầng gọi nhận tín hiệu qua **đường exception**; thông thường **không cần đặt lại interrupt flag chỉ để rethrow cùng ngoại lệ**. Caller sẽ quyết định hủy/khôi phục nếu nó không thể truyền tiếp.

### Cách B — không truyền được thì khôi phục flag

Một `Runnable.run()` không khai báo `throws InterruptedException`. Khi code trong `run()` gọi thao tác chặn có thể ném checked exception, thường cần:

~~~java
@Override
public void run() {
    try {
        blockingOperation();
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt(); // Đặt lại flag đã bị xóa.
        return; // Thoát công việc theo chính sách hủy.
    }
}
~~~

`blockingOperation()` ở đây là hàm minh họa được định nghĩa trong lớp thật, có `throws InterruptedException`.

Dòng `Thread.currentThread().interrupt()` không ngắt một thread xa lạ: nó báo lại tín hiệu cho **chính luồng đang chạy catch**, để mã bao ngoài/đơn vị quản lý tác vụ có thể quan sát. `return` ngăn worker tiếp tục xử lý như chưa có yêu cầu dừng.

**Không viết `throw new InterruptedException()` trong `Runnable.run()`**, vì chữ ký override không cho khai báo một checked exception mới. Nếu hợp đồng đòi một lỗi unchecked, có thể bọc nguyên nhân bằng `RuntimeException` thích hợp và khôi phục flag trước khi ném, nhưng cần tài liệu hóa hành vi này; không phải mọi ngắt hợp tác đều nên trở thành một lỗi ứng dụng.

## 4. Chương trình Java 17 chạy được: quan sát flag bị xóa rồi được phục hồi

Ví dụ sử dụng interrupt do chính thread đặt trước lời gọi `Thread.sleep` để tạo tình huống **xác định, không phụ thuộc thời gian scheduler**. Trong ứng dụng thực, tín hiệu thường được gửi từ thread khác qua `interrupt()` hoặc `Future.cancel(true)`.

Lưu toàn bộ mã trong **`InterruptFlagRestoreDemo.java`**:

~~~java
public class InterruptFlagRestoreDemo {
    static final class Worker implements Runnable {
        @Override
        public void run() {
            // Mô phỏng thread đã được yêu cầu hủy trước khi chờ.
            Thread.currentThread().interrupt();

            try {
                Thread.sleep(1_000);
                System.out.println("unexpected success");
            } catch (InterruptedException ex) {
                System.out.println("after InterruptedException: "
                        + Thread.currentThread().isInterrupted());

                // Không thể throws InterruptedException qua Runnable.run().
                Thread.currentThread().interrupt();

                System.out.println("after restore: "
                        + Thread.currentThread().isInterrupted());
                return;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(new Worker(), "worker");
        worker.start();
        worker.join();

        // Interrupt status là trạng thái theo từng thread.
        System.out.println("main interrupted: "
                + Thread.currentThread().isInterrupted());
    }
}
~~~

Chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac InterruptFlagRestoreDemo.java
java InterruptFlagRestoreDemo
~~~

**Stdout chính xác:**

~~~text
after InterruptedException: false
after restore: true
main interrupted: false
~~~

Giải thích:

1. Worker đặt cờ của chính nó bằng `Thread.currentThread().interrupt()`.
2. `Thread.sleep(1_000)` nhận thấy tình trạng đã bị interrupt, ném `InterruptedException` và xóa interrupt status. Vì vậy dòng đầu là `false`; lệnh sau `sleep` không chạy.
3. Khối `catch` gọi `Thread.currentThread().interrupt()` để phục hồi trạng thái, nên dòng thứ hai là `true`.
4. Worker dừng bằng `return`. Main gọi `join()` rồi kiểm tra **cờ của main**, không phải cờ của worker, nên in `false`. Một luồng bị interrupt không tự biến mọi luồng khác thành interrupted.

Dấu hiệu quan trọng là **trạng thái sau khi thao tác chặn ném lỗi**; không nên diễn giải rằng mọi API ném exception bất kỳ đều tự xóa interrupt flag.

## 5. Mã ứng dụng thực tế: nhận việc từ BlockingQueue

Một vòng lặp tiêu thụ tác vụ có thể chờ trên `BlockingQueue.take()`; đây là phương thức có thể ném `InterruptedException`. Ví dụ sau là **đoạn minh họa trong một worker**, giả sử `queue` chứa công việc, `process` xử lý công việc và các API khác đã được khai báo:

~~~java
@Override
public void run() {
    while (!Thread.currentThread().isInterrupted()) {
        try {
            Work work = queue.take();
            process(work);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            break;
        }
    }
    // Thực hiện cleanup phù hợp trước khi run() kết thúc.
}
~~~

Ở đây:

- Vòng `while` kiểm tra tín hiệu giữa các lần xử lý; `queue.take()` phản ứng với interrupt khi chờ.
- Nếu `take()` bị ngắt, khối `catch` khôi phục cờ vì `run()` không thể tiếp tục ném checked exception.
- `break` thoát vòng lặp để tiến tới cleanup. Nếu chỉ reinterrupt rồi vòng lại `queue.take()`, tác vụ có thể bị ngắt ngay lần nữa và lặp không hiệu quả.
- Nếu `process(work)` có thể ném `InterruptedException`, cùng nhánh xử lý cần bảo đảm tác vụ đang làm có trạng thái kết thúc rõ ràng. Nếu `process` gây side effect, không thể giả định interrupt tự rollback.

**Lưu ý concurrency:** nếu dùng `ExecutorService`, callback có thể chạy trên một thread dùng chung lâu dài. Thực hành tốt là tôn trọng chính sách hủy của executor/framework và thoát đúng ranh giới task. Đừng tự ý quyết định cả thread pool phải chết chỉ vì một task nhận yêu cầu interrupt.

## 6. Cách kiểm tra interrupt flag: isInterrupted và interrupted

Java có hai phương thức dễ bị nhầm:

| API | Đối tượng được kiểm tra | Có xóa interrupt status? | Dùng khi nào? |
|---|---|---|---|
| `Thread.currentThread().isInterrupted()` | Thread hiện tại, gọi qua object | **Không** | Điều kiện dừng ở vòng lặp, quan sát trạng thái trước khi thoát |
| `someThread.isInterrupted()` | Thread object được chỉ định | **Không** | Giám sát một thread khác khi thích hợp |
| `Thread.interrupted()` | **Luồng hiện tại** (static) | **Có**: đọc rồi xóa flag | Chỉ khi chủ đích tiêu thụ tín hiệu ngắt |
| `Thread.currentThread().interrupt()` | Thread hiện tại | **Đặt** cờ (hoặc kích hoạt ngữ nghĩa interrupt tương ứng) | Khôi phục sau khi đã bắt `InterruptedException` và không truyền tiếp |

Ví dụ dễ sai:

~~~java
// Không phù hợp nếu muốn giữ tín hiệu cho tầng ngoài.
while (!Thread.interrupted()) {
    doNonBlockingWork();
}
~~~

Khi gặp cờ interrupt, `Thread.interrupted()` trả về `true` **đồng thời xóa cờ**. Vòng lặp có thể dừng, nhưng tầng ngoài không còn thấy yêu cầu hủy qua interrupt status. Nếu hợp đồng yêu cầu bảo toàn flag, dùng `Thread.currentThread().isInterrupted()` hoặc đặt lại cờ khi đã tiêu thụ tín hiệu.

Ví dụ chủ ý xóa flag có thể phù hợp khi một tầng đã **nhận và xử lý hoàn toàn** tín hiệu rồi quyết định tiếp tục theo hợp đồng riêng. Điều này cần được thực hiện có chủ đích, không vô tình bởi tên phương thức tương tự nhau.

## 7. Nếu chỉ ghi log mà không reinterrupt thì sao?

Mẫu thường gây lỗi:

~~~java
// Sai khi ứng dụng cần bảo toàn yêu cầu hủy.
try {
    Thread.sleep(5_000);
} catch (InterruptedException ex) {
    logger.warn("interrupted", ex);
}
doMoreWork();
~~~

Tại đây, logger được giả định là đối tượng ghi log đã được định nghĩa ở bên ngoài đoạn mã. `Thread.sleep` đã xóa cờ khi ném exception; code chỉ log rồi chạy tiếp, nên caller không nhận được tín hiệu hủy. Đó là hành vi **swallow interruption**.

Một sửa đổi phù hợp trong tác vụ không thể `throws InterruptedException`:

~~~java
try {
    Thread.sleep(5_000);
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    logger.warn("operation cancelled by interrupt", ex);
    return;
}
doMoreWork();
~~~

Đây vẫn là **trích đoạn trong phương thức có kiểu trả về `void`**. Dòng `return` ngăn công việc sau thao tác chặn tiếp tục. Nếu việc ghi log tự ném ngoại lệ, cờ đã được khôi phục trước lời gọi logging; tuy nhiên code ứng dụng vẫn cần cân nhắc trách nhiệm cleanup và kênh báo lỗi.

**Quy tắc không nên áp dụng máy móc:** không phải *mọi* khối `catch` đều phải gọi `interrupt()`. Hãy xác định chính xác exception được bắt và ngữ nghĩa của API sinh lỗi. `IOException` do socket timeout, ví dụ, không tự đồng nghĩa với yêu cầu interrupt của thread.

## 8. Khôi phục flag rồi ném unchecked exception: khi nào phù hợp?

Đôi khi interface không cho phép checked exception nhưng caller/framework muốn nhận một thất bại thay vì chỉ thấy tác vụ trả về bình thường. Có thể chuyển đổi lỗi có chủ đích:

~~~java
@Override
public void run() {
    try {
        blockingOperation();
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("background task interrupted", ex);
    }
}
~~~

Hai hành động ở đây phục vụ **hai kênh khác nhau**:

- `Thread.currentThread().interrupt()` bảo toàn tín hiệu hủy ở trạng thái thread.
- `throw new IllegalStateException(..., ex)` truyền thông tin thất bại theo kênh exception mà framework có thể ghi nhận, ví dụ executor có `Future`.

Việc bọc lỗi không có nghĩa trạng thái chia sẻ đã rollback. Nếu hệ thống kỳ vọng cancellation là kết quả bình thường, trả về hoặc đưa trạng thái `CANCELLED` có thể rõ hơn biến interrupt thành một lỗi nghiệp vụ. Hãy thống nhất với hợp đồng API: một framework có thể sử dụng interrupt để hủy thay vì muốn nhận `IllegalStateException`.

Ngoài ra, một task chỉ có thể báo cho caller về việc bị ngắt **nếu kết quả hoặc kênh lỗi thật sự được người cần xử lý quan sát**. Ví dụ `ExecutorService.submit` lưu lỗi trong `Future`; bỏ qua Future có thể đồng nghĩa bỏ luôn thông tin thất bại ở cấp ứng dụng.

## 9. Cẩn thận khi gọi thao tác chặn khác sau khi reinterrupt

Nếu khôi phục interrupt flag rồi tiếp tục chạy:

~~~java
// Trích đoạn cảnh báo trong một phương thức.
catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    Thread.sleep(1_000); // Không hợp lệ nếu không xử lý throws; có thể ném ngay.
}
~~~

Một số lệnh chặn sẽ nhận thấy flag đang bật và phản ứng bằng một `InterruptedException` mới; nếu mỗi lần bắt lại khôi phục rồi lặp, chương trình có thể tạo **vòng lặp chờ/interrupt** không có tiến triển. Đừng dùng interrupt như một thông báo để *bỏ qua một bước rồi tiếp tục làm mọi việc bình thường*.

Sau reinterrupt, nên:

1. Ra khỏi vòng xử lý hoặc trả về tầng quản lý.
2. Hoàn tất cleanup ngắn, có khả năng thành công trong trạng thái đã bị interrupt.
3. Nếu cleanup cần chờ, xác định rõ chiến lược xử lý riêng và tránh mất tín hiệu hủy.
4. Ghi nhận phần trạng thái đã thay đổi trước khi lỗi, không nhầm “thread sắp dừng” với “transaction đã rollback”.

Một số thao tác cleanup không chặn vẫn có thể thực hiện an toàn sau khi cờ được khôi phục, nhưng không nên phát sinh hoạt động dài hoặc gửi thêm tác vụ mà caller đã yêu cầu hủy.

## 10. InterruptedException trong các API thường gặp

| Thao tác | Khi bị interrupt trong lúc chờ | Gợi ý xử lý |
|---|---|---|
| `Thread.sleep(...)` | Ném `InterruptedException`, xóa interrupt status | Propagate hoặc restore+exit |
| `Object.wait(...)` | Ném `InterruptedException`, xóa interrupt status; thread lấy lại monitor thích hợp trước khi trở về | Bảo vệ invariant trạng thái được chờ, propagate hoặc restore+exit |
| `Thread.join(...)` | Thread **đang join** nhận `InterruptedException` nếu bị ngắt; đây không phải lỗi của thread được chờ | Caller quyết định có chờ tiếp hay hủy; giữ tín hiệu nếu không truyền tiếp |
| `BlockingQueue.take()` / `put()` | Có thể ném `InterruptedException` theo hợp đồng | Worker loop nên hợp tác kết thúc |
| `CountDownLatch.await()` | Ném `InterruptedException` theo hợp đồng | Propagate hoặc restore tại boundary |
| `Future.get()` | Luồng đang chờ có thể nhận `InterruptedException` | Phân biệt với `ExecutionException` do task lỗi |
| `Lock.lockInterruptibly()` | Có thể ném `InterruptedException` khi chờ lock | Bảo đảm không gọi `unlock()` nếu chưa lấy được lock |
| `ExecutorService.awaitTermination(...)` | Có thể ném `InterruptedException` trong lúc chờ shutdown | Yêu cầu hủy theo chính sách, restore flag |

Với các API như `CompletableFuture.join()`, lời gọi **không khai báo `InterruptedException`**, nên cách chờ/ngắt khác với `Future.get()`. Không áp dụng quy tắc “cứ catch rồi reinterrupt” ở nơi không có checked exception đó. Hãy đọc hợp đồng của chính API đang dùng.

Một số API I/O liên quan interrupt bằng những lớp exception khác, như `ClosedByInterruptException` của các channel có thể ngắt, hoặc `InterruptedIOException`. Trạng thái cờ và side effect phụ thuộc từng API. Đừng suy diễn rằng mọi exception có chữ “interrupted” đều đã xóa flag giống `Thread.sleep`; đọc Javadoc khi thiết kế chuyển đổi.

## 11. Shutdown ExecutorService: mẫu phục hồi interrupt đúng ngữ cảnh

Ví dụ chính sách shutdown một executor: ngừng nhận công việc mới, chờ công việc đang chạy, yêu cầu hủy nếu quá hạn; nếu thread điều phối bị interrupt thì yêu cầu hủy và **khôi phục interrupt flag**.

~~~java
// Trích đoạn dùng ExecutorService pool đã được khởi tạo.
// TimeUnit được dùng với tên đầy đủ.
pool.shutdown();
try {
    if (!pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
        pool.shutdownNow();
    }
} catch (InterruptedException ex) {
    pool.shutdownNow();
    Thread.currentThread().interrupt();
}
~~~

Đây là một đoạn mã minh họa từ cách xử lý được tài liệu hóa cho executor. Vì đoạn code ở ranh giới shutdown không chọn `throws InterruptedException`, nó bảo toàn tín hiệu với `Thread.currentThread().interrupt()`.

**Không đánh đồng các thao tác:**

- `shutdown()` ngừng chấp nhận công việc mới, nhưng không yêu cầu mọi task đang chạy dừng tức thì.
- `shutdownNow()` cố gắng dừng các task đang chạy, thường thông qua interrupt và trả về danh sách tác vụ chờ chưa được chạy; không bảo đảm mọi task tuân theo yêu cầu.
- `awaitTermination()` chờ và có thể bị interrupt ở **thread điều phối**, khác với exception của một task đang chạy.
- `Thread.currentThread().interrupt()` trong `catch` trả lại tín hiệu cho người gọi đoạn shutdown này. Nó không tự hủy những task chưa hoàn tất nếu không có chính sách như `shutdownNow()`.

Nếu code cần cleanup thêm, nên định nghĩa rõ giới hạn chờ, các trường hợp không thể hoàn tất và khả năng đặt interrupt flag lại sau khi một đoạn cleanup khác đã tiêu thụ nó.

## 12. Giữ tính toàn vẹn trạng thái khi bị ngắt

Khôi phục interrupt status không bảo đảm **bất biến dữ liệu**. Giả sử worker đã trừ tồn kho rồi mới đi vào một thao tác chặn. Nếu thao tác chặn ném `InterruptedException`, việc reinterrupt chỉ giữ yêu cầu hủy; hàng tồn đã giảm vẫn có thể tồn tại.

~~~java
// Minh họa tình huống cần thiết kế commit/rollback.
synchronized (lock) {
    inventory.decrement(item);
    queue.put(event); // Có thể bị interrupt hoặc chờ.
}
~~~

Đoạn trên là **mã minh họa** có `lock`, `inventory`, `queue`, `item`, `event` được định nghĩa trong ứng dụng. Nếu `put` thất bại vì interrupt, monitor được nhả khi rời khối `synchronized` (nếu exception thoát), nhưng `decrement` không tự bị hoàn tác.

Muốn giữ dữ liệu nhất quán cần thêm:

- Validate và chuẩn bị trạng thái mới trước tác dụng phụ.
- Quy định rõ điểm commit; khi có nhiều tài nguyên, dùng transaction/compensation/outbox nếu phù hợp.
- Dùng cùng cơ chế đồng bộ cho các đường đọc/ghi liên quan, tránh TOCTOU.
- Quyết định task đang bị hủy có thể bỏ dở phần việc nào và cần cleanup gì.
- Giữ nguyên `cause`, thông tin tiến độ và trạng thái đã commit trong kết quả nếu caller cần phục hồi.

Việc cố “rollback” trong `catch` cũng có thể thất bại hoặc không thể đảo ngược email/HTTP request đã gửi. Tách riêng **bảo toàn tín hiệu interrupt** và **bảo toàn dữ liệu nghiệp vụ**.

## 13. Những điều không nên làm

1. **Nuốt interrupt:** `catch (InterruptedException ex) { }` rồi làm tiếp.
2. **Chỉ log rồi tiếp tục:** sau `sleep`, cờ đã bị xóa; tầng khác không thấy cancellation.
3. **Luôn reinterrupt rồi vòng lặp lại thao tác chặn:** có thể tạo vòng lỗi lặp mãi mà không tiến triển.
4. **Dùng `Thread.interrupted()` để chỉ “kiểm tra” trong khi cần bảo toàn flag:** phương thức này đồng thời xóa flag.
5. **Ném unchecked wrapper mà quên cause:** làm mất nguồn gốc lỗi và thông tin ngắt.
6. **Coi `interrupt()` như `Thread.stop()`:** interrupt là tín hiệu hợp tác, không phải dừng cưỡng bức.
7. **Ném hoặc bắt `InterruptedException` cho mọi lỗi I/O:** interrupt không đồng nghĩa mọi timeout/mất kết nối.
8. **Bắt `Exception` quá rộng rồi xử lý như lỗi thường:** có thể giấu mất `InterruptedException` và phá chính sách shutdown.
9. **Reinterrupt ở tầng đã truyền `InterruptedException` nguyên vẹn mà không có lý do:** làm trùng kênh thông báo và có thể gây tác động ngoài hợp đồng.
10. **Cho rằng shutdownNow/cancel(true) chắc chắn dừng task:** tác vụ phải hỗ trợ hủy; side effect đã thực hiện vẫn có thể tồn tại.

## 14. Kiểm thử đúng hành vi của interrupt flag

Kiểm thử nên tạo tình huống interrupt một cách có kiểm soát thay vì đoán thời điểm scheduler:

| Kịch bản | Điều cần xác nhận |
|---|---|
| Đặt flag trước `Thread.sleep` | `InterruptedException` xảy ra và flag được xóa trước khi catch xử lý |
| Catch rồi reinterrupt | `Thread.currentThread().isInterrupted()` trả `true` |
| Caller khác thread | Interrupt status của caller không tự bị thay đổi |
| Propagate `InterruptedException` | Lỗi đến đúng tầng nhận; không bị wrap/nuốt ngoài ý muốn |
| `Runnable.run()` nhận interrupt khi chặn | Worker thoát, không tiếp tục xử lý các bước không nên chạy |
| Vòng lặp dùng `Thread.interrupted()` | Chứng minh cờ bị xóa; quyết định có đặt lại hay đổi sang `isInterrupted()` |
| `Future.cancel(true)` | Task hợp tác dừng; không giả định tức thì, kiểm tra thời gian/cleanup |
| Shutdown manager bị interrupt trong `awaitTermination` | Chính sách shutdown và restored interrupt status đúng |
| Task có side effect rồi bị interrupt | Bất biến và trạng thái commit được kiểm tra độc lập |

Đối với kiểm thử trong một thread khác, có thể dùng `CountDownLatch` để đánh dấu tác vụ đã vào điểm chờ, rồi gọi `interrupt()` và `join()`; cần timeout để tránh test treo. Không dùng một lần `Thread.sleep(100)` như bằng chứng chắc chắn rằng worker đã đến đúng vị trí chờ.

Ngoài kiểm thử, nên đọc kỹ hợp đồng của API có thể ném `InterruptedException`: chính API xác định điều kiện ném, thao tác với interrupt status và trạng thái tài nguyên trước/sau ngắt.

## 15. Quy trình xử lý khi viết code thật

Khi nhìn thấy một `catch (InterruptedException ex)`, hãy trả lời các câu hỏi theo thứ tự:

1. **Luồng nào bị interrupt?** Đó là current thread đang thực thi thao tác chặn; không nhầm với một worker mà ta đang chờ.
2. **API đang dùng làm gì với flag?** Với `sleep`/`wait`/`join` và nhiều phương thức blocking chuẩn, flag được xóa khi exception được ném; API khác có thể có quy tắc riêng.
3. **Phương thức có thể khai báo `throws InterruptedException` không?** Nếu được và hợp đồng cho phép, truyền tiếp thường là cách rõ ràng.
4. **Nếu không truyền được, có khôi phục flag không?** Dùng `Thread.currentThread().interrupt()` tại ranh giới bắt lỗi.
5. **Sau đó có thoát đúng cách không?** `return`/`break`/chuyển trạng thái cancel thay vì tiếp tục công việc bình thường.
6. **Có cleanup hoặc thông báo kết quả không?** Giữ nguyên nhân lỗi và trạng thái công việc cần thiết, tránh chặn vô hạn sau reinterrupt.
7. **Dữ liệu có thể đã đổi trước ngắt chưa?** Xác nhận invariant, transaction và khả năng idempotency/compensation.
8. **Đã test flag và hành vi hủy chưa?** Đo cả đường exception lẫn điều kiện dừng, tránh chỉ assert có exception.

## 16. Ghi nhớ

**`InterruptedException` và interrupt flag là hai cách biểu diễn tín hiệu ngắt hợp tác.** Nhiều thao tác chặn chuẩn xóa flag khi ném `InterruptedException`. Nếu có thể truyền checked exception lên theo hợp đồng, hãy truyền; nếu **không thể truyền tiếp**, thường phải gọi **`Thread.currentThread().interrupt()`** để khôi phục tín hiệu, rồi thoát hoặc bàn giao cho cơ chế hủy phù hợp.

Khôi phục cờ **không tự dừng luồng**, không khiến exception lan sang thread khác và không tự rollback dữ liệu. Nó có ý nghĩa khi những tầng liên quan thực sự tôn trọng giao thức interrupt và tác vụ ngừng thực hiện công việc không còn được yêu cầu.

### Tham khảo

- [Java SE 17 — Thread.interrupt, interrupted, isInterrupted](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html)
- [Java SE 17 — InterruptedException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/InterruptedException.html)
- [Java SE 17 — BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html)
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java SE 17 — Lock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Lock.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-006">Dừng xử lý và cleanup sau khi nhận tín hiệu ngắt</a>

<details>
<summary>Xem chi tiết</summary>

# Dừng xử lý và cleanup sau khi nhận tín hiệu ngắt

Trong Java, **interrupt** là tín hiệu hợp tác để yêu cầu một luồng ngừng hoặc thay đổi công việc đang làm. Nó không phải lệnh cưỡng chế giết luồng, không tự rollback giao dịch và không tự đóng tất cả tài nguyên. Khi mã nhận tín hiệu ngắt, trách nhiệm của nó thường gồm **ngừng tiếp nhận hoặc thực hiện công việc theo chính sách**, **dọn dẹp những tài nguyên mình sở hữu**, **bảo toàn trạng thái và thông tin ngắt**, rồi **kết thúc hoặc truyền tín hiệu lên tầng điều phối**.

Đây là phần quan trọng của thiết kế exception: `InterruptedException` có thể làm luồng điều khiển rời khỏi thao tác blocking bất kỳ lúc nào. Một chương trình chỉ xử lý đường thành công mà không cleanup trên đường interruption có thể rò rỉ stream/kết nối, giữ lock hoặc registration, bỏ dở cập nhật trạng thái, hay làm ứng dụng không thể shutdown đúng cách.

Bài tập trung vào Java 17+ với thread, tác vụ trong executor và các API blocking. Cách dừng cụ thể phụ thuộc hợp đồng của mỗi thư viện; không có một câu lệnh `catch (InterruptedException)` duy nhất giải quyết mọi tình huống.

## 1. Hiểu tín hiệu ngắt trước khi cleanup

Mỗi `Thread` có một **interrupted status** (cờ ngắt). Một luồng khác có thể gọi `worker.interrupt()` để yêu cầu nó lưu ý đến việc hủy/dừng. Khi đó:

- Nếu worker đang chạy mã tính toán bình thường, Java **không tự ném `InterruptedException` ở mỗi câu lệnh**. Worker phải chủ động kiểm tra trạng thái hoặc đi vào thao tác interruptible.
- Nếu worker đang chờ bằng API interruptible như `Thread.sleep`, `Object.wait`, `Thread.join`, `BlockingQueue.take`, `CountDownLatch.await`, `Future.get` hoặc `Lock.lockInterruptibly`, thao tác có thể thoát ra bằng `InterruptedException`.
- Khi những phương thức chờ thông dụng này ném `InterruptedException` vì interrupt, trạng thái interrupt của thread thường đã được **xóa** theo hợp đồng API. Muốn tầng cao hơn tiếp tục biết yêu cầu ngắt, cần truyền tiếp checked exception hoặc **phục hồi cờ** khi chuyển nó thành dạng khác.
- Với I/O hoặc thao tác thư viện không bảo đảm phản hồi interrupt, `interrupt()` có thể không khiến worker dừng ngay. Cần xét timeout, `close()`, cancellation API riêng hoặc cơ chế dừng hợp tác của thư viện đó.

`Thread.interrupt()` được gọi từ phía khác chỉ gửi yêu cầu. Quyền và nghĩa vụ xử lý yêu cầu nằm ở **mã đang thực thi và quản lý vòng đời** của worker. Khi nhận interrupt, worker có thể rời vòng lặp, cleanup và kết thúc; hoặc một API cụ thể có thể chọn tiếp tục nếu việc ngắt được tài liệu hóa là không áp dụng tại đó.

### Ba thao tác với cờ interrupt

| API | Tác dụng | Lưu ý |
| --- | --- | --- |
| `thread.interrupt()` | Đặt/yêu cầu trạng thái ngắt của thread đích, có thể khiến thao tác chờ interruptible thức dậy bằng exception | Không bảo đảm thread dừng ngay. |
| `thread.isInterrupted()` | Đọc cờ của thread đích, **không xóa** | Dùng kiểm tra trong vòng lặp hợp tác. |
| `Thread.interrupted()` | Đọc **và xóa** cờ của thread **hiện tại** | Không dùng như phép kiểm tra vô hại nếu code phía sau vẫn cần tín hiệu. |

`Thread.currentThread().interrupt()` thường dùng trong `catch (InterruptedException ex)` để **phục hồi cờ của chính luồng hiện tại** sau khi không thể truyền tiếp checked exception.

## 2. Tín hiệu ngắt và exception xảy ra ở những nơi khác nhau

Xét một worker đang chờ công việc:

```java
while (!Thread.currentThread().isInterrupted()) {
    Job job = queue.take();
    process(job);
}
```

Đây là đoạn **minh họa**, giả định `Job`, `queue` và `process` đã được định nghĩa. `queue.take()` có thể ném `InterruptedException` nên đoạn này chưa đầy đủ để biên dịch riêng. Dù điều kiện `while` kiểm tra cờ interrupt, một yêu cầu ngắt vẫn có thể đến **trong lúc `take()` đang chờ**. Vì thế vòng lặp cần xử lý đường exception:

```java
try {
    while (!Thread.currentThread().isInterrupted()) {
        Job job = queue.take();
        process(job);
    }
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Rời vòng lặp để cleanup tài nguyên thuộc quyền sở hữu.
} finally {
    releaseOwnedResources();
}
```

Đây cũng là **mảnh minh họa**, không phải chương trình Java độc lập. `releaseOwnedResources()` cần có hợp đồng riêng về exception và quyền sở hữu.

Nếu `process(job)` có side effect và không thể dừng giữa chừng an toàn, chính sách có thể yêu cầu hoàn tất đơn vị công việc hiện tại rồi mới ngừng nhận job tiếp theo. Đừng tự ý chen một phép `interrupt` vào giữa thao tác cần tính nhất quán mà không có thiết kế rollback/idempotency.

## 3. Quy trình xử lý khi nhận interrupt

Một luồng xử lý hợp tác thường làm các bước sau:

1. **Nhận biết tín hiệu** qua `InterruptedException` hoặc kiểm tra cờ `isInterrupted()`; nhận diện đúng loại lỗi để không nhầm với exception nghiệp vụ.
2. **Chuyển sang trạng thái stopping** nếu worker có lifecycle (ví dụ ngừng lấy job mới, báo cho coordinator); tránh tiếp tục vòng lặp vô thời hạn.
3. **Dừng hoặc hoàn tất phần việc đang dang dở** theo hợp đồng. Nếu tác vụ có điểm commit, cần bảo đảm không ghi một phần không mong muốn.
4. **Cleanup tài nguyên thuộc quyền sở hữu** trong `finally`/`try-with-resources`: đóng stream/connection riêng, trả permit, unlock đúng lock, unsubscribe listener, đóng registration handle...
5. **Bảo toàn thông tin**: truyền tiếp `InterruptedException` nếu chữ ký cho phép, hoặc khôi phục interrupt flag khi chuyển nghĩa/thoát.
6. **Báo hoàn tất shutdown** cho chủ sở hữu nếu cần: latch, trạng thái lifecycle hoặc completion stage. Điều này khác với thông báo “đã gửi yêu cầu cancel”.

Các bước trên là trách nhiệm logic, không nhất thiết theo một thứ tự cứng cho mọi API. Đặc biệt, **thời điểm khôi phục cờ interrupt so với cleanup blocking** cần cân nhắc như mục 7.

Không dùng `Thread.stop()` để ép luồng dừng: API cũ này không an toàn vì có thể phá invariant và làm giải phóng monitor khi trạng thái đang bị cập nhật dở.

## 4. Try-with-resources: cleanup ngay cả khi await bị ngắt

Tài nguyên của worker nên được đóng theo một cấu trúc có bảo đảm cleanup trong đường thoát bình thường và đường exception.

Với `AutoCloseable`:

```java
try (InputStream input = Files.newInputStream(path)) {
    consume(input);
}
```

Đây là snippet minh họa, giả định `path` và `consume` đã có và `consume` phù hợp kiểu exception. `InputStream` mở bởi phạm vi này được đóng khi khối `try` thoát, bao gồm khi phần thân ném exception. Nhưng việc một tài nguyên được đưa vào `try-with-resources` có nghĩa là **phạm vi đó sẽ gọi `close()` lên nó**: chỉ làm khi có quyền sở hữu.

Trường hợp body ném `InterruptedException`:

```java
try (OwnedResource resource = openOwnedResource()) {
    awaitInterruptibly();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Đã đi qua resource.close() trước khi chạy catch này.
}
```

Đây là **mã minh họa chưa độc lập**, với `OwnedResource`, `openOwnedResource` và `awaitInterruptibly` giả định. Cấu trúc đặt `catch` **bên ngoài** khối try-with-resources có điểm hay: trình tự đóng tài nguyên của khối `try` diễn ra trước khi bắt và phục hồi interrupt. Nếu `close()` cần thực hiện một bước cleanup interruptible, nó không bị cờ interrupt vừa khôi phục ngay lập tức gây gián đoạn một cách vô ý.

Nếu `close()` tự ném exception, phải xét ngoại lệ chính và các suppressed exceptions. Đừng giả định rằng cleanup chắc chắn thành công chỉ vì đã gọi `close()`.

### Đóng theo đúng quyền sở hữu

Một parser nhận `InputStream` do caller truyền vào có thể chỉ **mượn** stream. Khi bị ngắt, parser không nên tự đóng stream của caller nếu hợp đồng không chuyển giao quyền sở hữu. Caller có thể có một `try-with-resources` ở phạm vi bao ngoài chịu trách nhiệm đóng.

Tương tự, một request sử dụng shared `HttpClient` hoặc shared database connection pool không được tùy tiện đóng toàn bộ client/pool của ứng dụng khi request bị hủy. Chỉ giải phóng những handle/tài nguyên **thuộc request đó**, theo hợp đồng API.

## 5. Ví dụ chạy độc lập Java 17: dừng worker và cleanup trước khi kết thúc

Ví dụ dùng `CountDownLatch` để kiểm soát thứ tự giữa main và worker, tránh phải dựa vào `Thread.sleep` để hy vọng worker đã bắt đầu. Worker mở resource của riêng mình, chờ trên một latch không được giải phóng và sau đó nhận interrupt từ main. Khi `await()` ném `InterruptedException`, try-with-resources đóng resource, `catch` phục hồi interrupt flag và cuối cùng worker kết thúc.

Tạo tệp `InterruptCleanupDemo.java`:

```java
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterruptCleanupDemo {

    private static final class OwnedResource implements AutoCloseable {
        private final List<String> events;
        private boolean closed;

        OwnedResource(List<String> events) {
            this.events = events;
            events.add("resource: opened");
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                events.add("resource: closed");
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        List<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch enteredWait = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        AtomicBoolean flagRestored = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            try (OwnedResource resource = new OwnedResource(events)) {
                events.add("task: waiting");
                enteredWait.countDown();
                neverReleased.await();
                events.add("task: completed"); // Không chạy trong ví dụ.
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                flagRestored.set(Thread.currentThread().isInterrupted());
                events.add("task: interrupted");
            } finally {
                events.add("task: finished");
            }
        }, "resource-owner");

        worker.start();
        enteredWait.await();
        worker.interrupt();
        worker.join();

        events.forEach(System.out::println);
        System.out.println("main: flag restored = " + flagRestored.get());
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 InterruptCleanupDemo.java
java InterruptCleanupDemo
```

Stdout:

```text
resource: opened
task: waiting
resource: closed
task: interrupted
task: finished
main: flag restored = true
```

Giải thích hành vi:

- `enteredWait.countDown()` chỉ báo worker đã tạo resource và đến vùng chờ. Main có thể gọi `interrupt()` ngay trước hoặc trong `neverReleased.await()`; cả hai trường hợp đều dẫn đến `InterruptedException` ở `await()` theo hợp đồng của latch.
- Java rời khối try-with-resources và gọi `OwnedResource.close()` **trước** khi vào `catch` bên ngoài. Dòng `resource: closed` vì thế đứng trước `task: interrupted`.
- `catch` gọi `Thread.currentThread().interrupt()` để bảo toàn yêu cầu hủy cho code phía trên trong cùng worker (nếu có), sau đó ghi nhận trạng thái interrupt đã được phục hồi. `finally` ghi nhận thoát lifecycle.
- Main `join()` để đợi worker kết thúc mới in toàn bộ nhật ký. Danh sách `CopyOnWriteArrayList` bảo đảm chia sẻ dữ liệu an toàn giữa hai thread; vì worker là tác giả duy nhất của các dòng sự kiện nên thứ tự được giữ nguyên.
- `task: completed` không xuất hiện vì latch không được giải phóng. Đó là dấu hiệu worker **dừng theo ngắt**, không phải hoàn thành tác vụ như bình thường.
- `OwnedResource.close()` của ví dụ không blocking và không ném checked exception. Trong ứng dụng thực, cleanup có thể bị lỗi hoặc mất thời gian; cần chính sách riêng.

Ví dụ sử dụng `throws InterruptedException` trong `main` vì chính main có các thao tác `await()/join()` interruptible. Đây là lỗi có thể xảy ra ở **luồng main**, tách biệt với exception mà worker vừa nhận.

## 6. Vì sao không nên nuốt InterruptedException?

Một lỗi phổ biến:

```java
try {
    queue.take();
} catch (InterruptedException ex) {
    // Bỏ qua và tiếp tục làm việc.
}
```

Đây là snippet minh họa với `queue` giả định. Sau khi `take()` ném `InterruptedException`, cờ interrupt có thể đã bị xóa. Nếu không truyền tiếp, không phục hồi và không rời công việc, caller hoặc bộ phận shutdown có thể **mất tín hiệu hủy**, khiến worker tiếp tục lấy job và hệ thống không thể dừng đúng.

Những cách xử lý thông thường:

**Truyền tiếp checked exception** khi API cho phép:

```java
public static String waitForMessage(BlockingQueue<String> queue)
        throws InterruptedException {
    return queue.take();
}
```

Đây là phương thức có thể đặt trong class với import `java.util.concurrent.BlockingQueue`. Quyết định dừng và cleanup được giao cho caller có thông tin về lifecycle cao hơn.

**Phục hồi cờ và kết thúc** khi ở `Runnable.run()` (không khai báo checked exception):

```java
try {
    waitForMessage(queue);
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return; // finally/try-with-resources bao ngoài vẫn cleanup.
}
```

Mảnh minh họa này giả định `queue` và `waitForMessage` đã có. Trả về khỏi `run()` không thể bỏ qua `finally` của các phạm vi đang hoạt động, nhưng tài nguyên phải được khai báo trong `try-with-resources` hoặc cleanup có cấu trúc.

**Chuyển thành exception của API nhưng giữ interrupt**:

```java
try {
    blockingOperation();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    throw new OperationInterruptedException("Đã ngắt xử lý", ex);
}
```

`OperationInterruptedException` và `blockingOperation` là thành phần giả định của ứng dụng. Khi chuyển nghĩa, giữ `cause` để chẩn đoán; không biến tín hiệu ngắt thành `IllegalArgumentException` hay “input invalid”.

Một số framework/caller **trực tiếp trông đợi checked `InterruptedException`**. Nếu có thể truyền tiếp, không cần bọc nó chỉ vì muốn “chuẩn hóa” exception.

## 7. Cleanup có thể blocking: đặt thời điểm phục hồi cờ cho đúng

Khôi phục interrupt sớm thường là đúng sau khi bắt `InterruptedException`, nhưng hãy để ý cleanup tiếp theo có thao tác **cũng phản ứng với interrupt**.

Ví dụ nguy cơ:

```java
try {
    blockingOperation();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
} finally {
    // Nếu cleanup sử dụng phương thức interruptible, cờ đã được đặt.
    waitForCleanupAcknowledgement();
}
```

Đây là snippet minh họa, `waitForCleanupAcknowledgement` có thể ném exception hoặc nhận cờ interrupt. Nếu chờ cleanup dùng `CountDownLatch.await()`, cờ interrupt đã được phục hồi có thể khiến lệnh `await()` lập tức ném `InterruptedException` thay vì đợi thực hiện cleanup. Nếu cleanup là điều bắt buộc để bảo toàn trạng thái, thứ tự này có thể sai.

Một cách là tổ chức cleanup vào **try-with-resources nằm bên trong** và catch ở ngoài như ví dụ đầy đủ tại mục 5. Một cách khác là giữ thông tin yêu cầu ngắt trong biến cục bộ khi cần chạy cleanup blocking có giới hạn, rồi phục hồi cờ sau cleanup:

```java
boolean interrupted = false;
try {
    blockingOperation();
} catch (InterruptedException ex) {
    interrupted = true;
} finally {
    try {
        cleanupWithinDeadline();
    } catch (InterruptedException duringCleanup) {
        interrupted = true;
        // Ghi nhận việc cleanup bị ngắt; xem xét trạng thái còn lại.
    } finally {
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
```

Đây là đoạn **minh họa**, với `blockingOperation` và `cleanupWithinDeadline` chưa định nghĩa. Biến `interrupted` chỉ ghi nhận tín hiệu; việc không tái ném exception có phù hợp hay không phụ thuộc contract của phương thức bao ngoài. `cleanupWithinDeadline` phải thật sự có giới hạn chờ/hủy rõ ràng; không chờ vô hạn trong shutdown.

**Không được tùy tiện gọi `Thread.interrupted()` để xóa cờ** chỉ nhằm ép cleanup chạy mà không lưu và phục hồi trạng thái. Nếu dùng cơ chế xóa có chủ đích, phải hiểu ai là owner của tín hiệu hủy và không làm tầng gọi mất yêu cầu dừng.

Lưu ý thêm: nếu cleanup ném `IOException`, `RuntimeException` hoặc một exception khác, đoạn `finally` có thể tác động đến exception trước đó. Cần đọc hợp đồng và dùng cấu trúc bảo toàn lỗi (mục 9), không chỉ phục hồi interrupt flag.

## 8. Dừng vòng lặp mà vẫn giữ bất biến trạng thái

Một vòng lặp xử lý job nên có **ranh giới an toàn** để ngừng:

```java
while (!Thread.currentThread().isInterrupted()) {
    Job job = acquireNextJob();
    processOneJob(job);
}
```

Đây là mảnh minh họa thiếu định nghĩa `Job`, `acquireNextJob` và `processOneJob`. `acquireNextJob` có thể interruptible; cần catch/cleanup cho exception. Nhưng ngay cả khi không có exception, interrupt flag có thể được đặt **sau điều kiện `while`**. Nếu yêu cầu ngừng ngay trước khi lấy job mới, có thể cần kiểm tra thêm tại biên thích hợp.

Với tác vụ có trạng thái:

- Trước khi bắt đầu job mới, kiểm tra tín hiệu dừng và không nhận thêm công việc.
- Khi đang xử lý một đơn vị cần nguyên tử, chọn rollback, hoàn tất đến checkpoint an toàn hoặc ghi nhận trạng thái có thể resume.
- Trong `finally`, cleanup những tài nguyên đã thực sự được acquire. Không cleanup một tài nguyên chưa tạo thành công hoặc do thành phần khác sở hữu.
- Sau khi cleanup, báo kết quả **stopped/cancelled** riêng với **completed successfully**, nếu API cho phép caller phân biệt.

Nếu dùng lock, lưu ý:

```java
lock.lockInterruptibly();
try {
    updateSharedState();
} finally {
    lock.unlock();
}
```

Đoạn này minh họa với `lock` và `updateSharedState` giả định. `lock.lockInterruptibly()` nằm **trước** `try` vì nó có thể ném `InterruptedException` khi chưa acquire lock. Chỉ gọi `unlock()` khi đã acquire thành công. Nếu bao `lockInterruptibly()` vào `try` rồi `finally` luôn `unlock()`, có thể gây `IllegalMonitorStateException` khi nhận interrupt trước lúc nắm lock.

Bảo toàn trạng thái không đồng nghĩa mọi interrupt đều rollback tự động. Nếu đã gửi một request mạng hoặc đã ghi dữ liệu, ngắt việc chờ không chứng minh remote service chưa xử lý yêu cầu. Với thao tác có side effect, cần idempotency, transaction hoặc bước đối soát riêng.

## 9. Nếu vừa bị ngắt vừa lỗi trong close thì sao?

Một thao tác blocking có thể ném `InterruptedException` đúng lúc resource `close()` cũng gặp lỗi. Với try-with-resources:

- Exception của phần thân `try` là lỗi **chính**.
- Exception trong `close()` được gắn dưới dạng **suppressed exception**.
- Caller có thể truy cập qua `Throwable.getSuppressed()`.
- Nếu phần thân hoàn thành bình thường nhưng `close()` ném exception, exception lúc đóng có thể trở thành lỗi chính.

Ví dụ Java 17 chạy độc lập, cố ý ném exception trong `close()`:

Tạo `InterruptedAndCloseFailureDemo.java`:

```java
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class InterruptedAndCloseFailureDemo {

    private static final class FailingClose implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            try (FailingClose resource = new FailingClose()) {
                entered.countDown();
                neverReleased.await();
            } catch (InterruptedException ex) {
                System.out.println("primary: " + ex.getClass().getSimpleName());
                System.out.println(
                    "suppressed: " + ex.getSuppressed().length
                );
                System.out.println(
                    "cleanup: " + ex.getSuppressed()[0].getMessage()
                );
                Thread.currentThread().interrupt();
            } catch (IOException ex) {
                System.out.println("close-only: " + ex.getMessage());
            }
        }, "close-failure-worker");

        worker.start();
        entered.await();
        worker.interrupt();
        worker.join();
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 InterruptedAndCloseFailureDemo.java
java InterruptedAndCloseFailureDemo
```

Stdout:

```text
primary: InterruptedException
suppressed: 1
cleanup: close failed
```

Vì `neverReleased` không bao giờ được mở, worker rời `await()` qua `InterruptedException` sau khi main gọi `interrupt()`. `FailingClose.close()` tiếp tục ném `IOException("close failed")`. Try-with-resources bảo toàn `InterruptedException` làm exception chính, còn `IOException` trở thành một suppressed exception.

Không phải mọi exception khi đóng đều vô hại. Ví dụ với stream ghi được buffer, lỗi lúc `flush/close` có thể báo dữ liệu chưa thực sự được ghi. Cần ghi nhận và đánh giá hậu quả cleanup, không chỉ gọi `close()` rồi xem việc giải phóng là thành công.

Trong logging, giữ cả primary, cause và suppressed exceptions nếu chúng có ý nghĩa chẩn đoán. Tránh in thông điệp có thông tin nhạy cảm ra client cuối nếu lỗi bên dưới chứa đường dẫn nội bộ, token hoặc dữ liệu riêng tư.

## 10. Future.cancel(true): tín hiệu hủy và thời điểm cleanup hoàn thành

Trong `ExecutorService`, caller thường nhận `Future`. Nếu caller gọi `future.cancel(true)`, implementation có thể yêu cầu interrupt worker đang thực hiện task. Nhưng **`Future.isCancelled() == true` không chứng minh `finally` trong task đã chạy xong**.

Đây là hai mốc khác nhau:

1. **Yêu cầu/trạng thái cancellation:** Future được đánh dấu canceled; caller không còn nhận kết quả bình thường qua `get()`.
2. **Task đã ngừng và cleanup hoàn tất:** Worker thực sự đi qua các nhánh dừng, đóng tài nguyên và thoát.

Nếu caller cần đợi cleanup, phải có cơ chế chờ completion/acknowledgement riêng, hoặc hợp đồng của một abstraction cao hơn bảo đảm điều đó. `Future.get()` sau khi Future đã canceled sẽ ném `CancellationException`; nó không thay thế việc chờ tín hiệu “đã cleanup” từ task.

### Ví dụ Java 17 hoàn chỉnh: hủy rồi đợi cleanup

Tạo `CancelAndCleanupDemo.java`:

```java
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CancelAndCleanupDemo {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        List<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        CountDownLatch cleaned = new CountDownLatch(1);

        try {
            Future<?> future = executor.submit(() -> {
                try {
                    events.add("task: started");
                    started.countDown();
                    neverReleased.await();
                    events.add("task: completed");
                } catch (InterruptedException ex) {
                    events.add("task: interrupt received");
                    Thread.currentThread().interrupt();
                } finally {
                    events.add("task: cleanup");
                    cleaned.countDown();
                }
            });

            started.await();
            boolean accepted = future.cancel(true);

            // Chờ task xác nhận đã đi qua finally;
            // chỉ kiểm tra isCancelled() là chưa đủ.
            cleaned.await();

            events.forEach(System.out::println);
            System.out.println("cancel accepted: " + accepted);
            System.out.println("future cancelled: " + future.isCancelled());
            System.out.println("cleanup confirmed: true");
        } finally {
            executor.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 CancelAndCleanupDemo.java
java CancelAndCleanupDemo
```

Stdout:

```text
task: started
task: interrupt received
task: cleanup
cancel accepted: true
future cancelled: true
cleanup confirmed: true
```

Tại sao đầu ra xác định?

- `started.await()` bảo đảm worker đã khởi chạy trước khi main yêu cầu hủy.
- Worker đang ở trước/đang chờ `neverReleased.await()`. `cancel(true)` đối với `FutureTask` do executor chuẩn tạo sẽ yêu cầu interrupt thread của task đang thực hiện.
- `CountDownLatch.await()` phản hồi interrupt bằng `InterruptedException`; worker ghi lại, phục hồi cờ và đi vào `finally`.
- `cleaned.countDown()` là tín hiệu xác nhận cleanup của **ví dụ này** đã chạy. Main đợi `cleaned.await()` trước khi in kết quả; vì thế có thể xác nhận độc lập trạng thái Future và trạng thái cleanup.
- `neverReleased` không được giải phóng nên không có dòng `task: completed`.
- `shutdownNow()` trong `finally` là yêu cầu shutdown executor khi rời demo; trong đường thành công, task đã cleanup trước đó.

**Giới hạn cần nhớ:** một task thực dùng I/O hoặc thư viện không phản hồi interrupt có thể không bao giờ đạt đến `cleaned.countDown()`; việc đợi vô hạn là không phù hợp với nhiều đường shutdown production. Thiết kế thực nên dùng deadline cho chờ cleanup và cơ chế giám sát/escalation phù hợp. Ví dụ chỉ dùng latch interruptible của JDK nên có đường dừng được xác định rõ.

`CompletableFuture.cancel(true)` có semantics khác với `FutureTask`: `mayInterruptIfRunning` không được dùng để điều khiển interruption của một task chạy nền theo cách trên. Không lấy ví dụ `FutureTask` áp dụng máy móc cho mọi implementation `Future`.

## 11. Cleanup không bảo đảm rollback

Một nguồn nhầm lẫn khác: “Đã catch interrupt và close xong” không có nghĩa trạng thái nghiệp vụ được khôi phục như trước tác vụ.

| Việc cần làm | Mục tiêu | Ví dụ |
| --- | --- | --- |
| Dừng nhận công việc | Không tạo thêm side effect sau yêu cầu ngừng | Ngừng poll queue, ngăn đăng ký request mới. |
| Cleanup tài nguyên | Không giữ tài nguyên/registration thuộc owner | `close` stream riêng, `removeListener`, trả permit. |
| Bảo toàn invariant | Đối tượng không ở trạng thái nửa khởi tạo hoặc nửa đóng | Đặt trạng thái rõ ràng `STOPPING`/`STOPPED`, đồng bộ cập nhật. |
| Rollback/compensation | Khôi phục hoặc bù những thay đổi nghiệp vụ đã thực hiện | Transaction rollback, xóa dữ liệu tạm, thao tác bù. |
| Báo kết quả | Caller biết canceled khác với failed và completed | Future/callback/event/trạng thái job. |

Với một tác vụ gửi thanh toán, interrupt khi chờ response HTTP **không chứng minh** server chưa xử lý thanh toán. Không tự retry hay rollback giả nếu thiếu thông tin idempotency hoặc khả năng kiểm tra trạng thái.

Với ghi file, `close()` có thể flush dữ liệu đã đẩy vào buffer nhưng không làm thao tác ghi trở lại trạng thái ban đầu. Nếu cần cập nhật nguyên tử file, có thể ghi vào tệp tạm, flush/sync phù hợp và chuyển đổi có bảo đảm phù hợp với filesystem; quyết định này thuộc thiết kế tính nhất quán, không do interrupt tự mang lại.

## 12. Cleanup đúng thứ tự và không đóng tài nguyên của người khác

Một worker có thể đồng thời giữ:
- File stream do chính nó mở.
- Permit của semaphore đã acquire thành công.
- Connection đã checkout từ pool.
- Listener registration handle đã tạo.
- Reference đến shared executor hoặc shared HTTP client.

Tại đường interruption:

1. Dọn resources thuộc phạm vi của worker theo thứ tự phù hợp với dependency. Try-with-resources đóng nhiều tài nguyên theo thứ tự **ngược thứ tự khai báo**.
2. Chỉ giải phóng permit/lock sau khi **acquire thành công**. Ví dụ `Semaphore.acquire()` có thể ném `InterruptedException` trước khi nhận permit; `finally { semaphore.release(); }` vô điều kiện sẽ làm sai số permit.
3. Chỉ `unlock()` khi thread thực sự nắm lock. Với `lock.lockInterruptibly()`, một interrupt trước acquisition không cho phép gọi `unlock()`.
4. Chỉ hủy subscription mà worker sở hữu. Hủy registration chung có thể làm subscriber khác mất sự kiện.
5. Không shutdown shared executor/pool chỉ vì một request/caller bị ngắt, trừ khi worker đó thực sự quản lý lifecycle của thành phần chia sẻ.

Ví dụ minh họa xử lý permit:

```java
boolean acquired = false;
try {
    semaphore.acquire();
    acquired = true;
    doWork();
} finally {
    if (acquired) {
        semaphore.release();
    }
}
```

Đây là snippet chưa độc lập; `semaphore` và `doWork` giả định được khai báo, và phương thức phải xử lý/khai báo `InterruptedException`. Biến `acquired` bảo đảm không release permit chưa sở hữu nếu bị ngắt trong lúc chờ `acquire()`.

Với `Lock`:

```java
lock.lockInterruptibly();
try {
    updateSharedState();
} finally {
    lock.unlock();
}
```

Snippet này minh họa rằng `unlock()` chỉ được cài sau điểm acquire thành công, không gọi bừa khi `lockInterruptibly()` bị ngắt trước khi nắm lock.

## 13. Khởi tạo một phần và nhiều thao tác cleanup

Nếu quá trình khởi tạo acquires A, rồi B, sau đó gặp interrupt trước khi acquire C, chỉ A/B cần cleanup. Sai lầm phổ biến là viết code tạo tài nguyên ở ngoài `try` rồi chỉ cleanup khi mọi bước tạo hoàn tất.

Với `AutoCloseable`, cấu trúc:

```java
try (Resource first = openFirst();
     Resource second = openSecond(first)) {
    runInterruptibleOperation(second);
}
```

là snippet minh họa. Nếu `openSecond` ném exception hoặc nhận interrupt, Java đóng `first` đã tạo thành công. Nếu cả hai được tạo, Java đóng `second` rồi `first` khi thoát.

Nhưng nếu `openSecond` **tạo được một phần tài nguyên bên trong rồi ném trước khi trả object**, caller chưa nhận object để đóng. Chính implementation của `openSecond` phải cleanup tài nguyên nội bộ đã acquire.

Với nhiều registration/listener, tương tự: lưu handle ngay khi subscribe thành công; nếu subscribe tiếp theo thất bại, hủy các handle đã tạo. Lúc gặp interrupt, cleanup không được bỏ sót những registration đã thành công trước đó.

Nếu cleanup nhiều tài nguyên thủ công, đừng để lỗi từ resource A ngăn việc đóng resource B còn độc lập; dùng try-with-resources khi phù hợp hoặc cấu trúc thu thập lỗi cleanup để vẫn cố đóng các tài nguyên còn lại.

## 14. Interrupt trong ExecutorService.shutdown và awaitTermination

Một owner của executor thường có các bước:
- Dừng nhận job mới bằng `shutdown()`.
- Đợi job đang thực hiện kết thúc bằng `awaitTermination(timeout, unit)`.
- Nếu quá thời hạn và chính sách cho phép, yêu cầu hủy công việc còn lại qua `shutdownNow()` hoặc cancel handles.
- Đảm bảo task tự cleanup và ghi nhận job nào hoàn thành/chưa hoàn thành.

`shutdown()` **không chờ** các task kết thúc; `shutdownNow()` là nỗ lực dừng task đang chạy bằng interrupt và trả danh sách job chưa bắt đầu, không bảo đảm task đã dừng. `awaitTermination(...)` có thể ném `InterruptedException` nếu **luồng điều phối shutdown** bị ngắt khi chờ. Điều đó không đồng nghĩa mọi worker đã lỗi.

Mẫu sau là **mảnh minh họa** cho một component thật sự sở hữu executor:

```java
executor.shutdown();
try {
    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
    }
} catch (InterruptedException ex) {
    executor.shutdownNow();
    Thread.currentThread().interrupt();
}
```

Ở đây owner chọn chính sách: nếu người điều phối shutdown bị ngắt, yêu cầu workers dừng và phục hồi interrupt flag của chính coordinator. Không sao chép chính sách này vào code chỉ **mượn** executor do framework quản lý.

Tài nguyên của task vẫn phải cleanup gần task. Owner executor có thể giám sát/quy định deadline, nhưng không nên dựa vào `shutdownNow()` như bảo đảm mọi `finally` đã thực thi. Với công việc quan trọng, cần một kênh xác nhận tác vụ thực sự đã dừng hoặc cơ chế shutdown có timeout và xử lý tình trạng không đáp ứng.

## 15. Khi interrupt đến trong lúc chính cleanup đang diễn ra

Cleanup có thể bao gồm close socket, flush buffer, chờ callback dừng, dừng producer hoặc trả kết nối. Một số thao tác blocking sẽ nhận thêm interrupt. Chính sách cần trả lời:

- Cleanup tối thiểu nào **phải cố hoàn tất** để không rò rỉ tài nguyên hoặc giữ invariant sai?
- Cleanup nào có thể bỏ qua hoặc thực hiện bất đồng bộ sau khi yêu cầu dừng?
- Có deadline không? Nếu cleanup vẫn không xong, ai nhận cảnh báo/escalation?
- Có cần bảo lưu cả exception của công việc chính lẫn exception trong cleanup?
- Sau khi cleanup thất bại, thành phần nên là `FAILED` hay `STOPPED_WITH_ERRORS` thay vì tuyên bố dừng sạch?

Đừng dùng vòng lặp:

```java
while (true) {
    try {
        waitUntilClean();
        break;
    } catch (InterruptedException ignored) {
        // Bỏ qua vô hạn.
    }
}
```

Đây là snippet minh họa cách làm **không nên áp dụng**: nó có thể chờ mãi, che tín hiệu shutdown và gây deadlock khi component khác chờ thread kết thúc. Nếu bắt buộc cleanup có chờ, dùng deadline/giới hạn, giữ thông tin interrupt và có đường thoát có ý nghĩa.

Không có bảo đảm JVM luôn chạy `finally` trong mọi tình huống: tiến trình có thể bị cưỡng chế dừng, JVM crash hoặc tài nguyên ở tầng hệ điều hành bị mất. Phần mềm yêu cầu độ bền cao cần thêm recovery khi khởi động lại, transaction hoặc lease có hạn.

## 16. Phân biệt ngắt ở worker và ngắt ở caller

Đối với `ExecutorService.submit`, worker có thể ném `InterruptedException` ra khỏi `Callable.call()`; task hoàn thành exceptionally và `Future.get()` ở caller nhận **`ExecutionException` với cause là `InterruptedException`**. Caller không vì thế tự bị ngắt.

Ngược lại, nếu **caller** bị ngắt khi chờ `future.get()`, nó nhận `InterruptedException` trực tiếp. Task có thể tiếp tục chạy bình thường.

| Vị trí ngắt | Cách quan sát | Nghĩa vụ cleanup |
| --- | --- | --- |
| Worker bị ngắt khi xử lý | Worker catch/truyền exception, hoặc Future chứa thất bại | Worker cleanup tài nguyên do worker sở hữu. |
| Caller bị ngắt khi `Future.get()` | `InterruptedException` ở caller | Caller cleanup tài nguyên do caller sở hữu; có thể quyết định cancel task nếu là owner. |
| `get(timeout)` chỉ hết hạn chờ | `TimeoutException` của waiter | Không mặc nhiên bắt worker cleanup; policy có thể tiếp tục chờ hoặc yêu cầu cancel. |
| `Future.cancel(true)` được chấp nhận | Future có trạng thái canceled, worker có thể bị interrupt | Worker vẫn phải hợp tác và cleanup; caller muốn biết cleanup xong cần cơ chế riêng. |

Không gọi `Thread.currentThread().interrupt()` trên caller **chỉ vì** `ExecutionException.getCause() instanceof InterruptedException`. Interrupt status thuộc **thread hiện tại**, không phải một “biến toàn cục đại diện cho lỗi task”.

## 17. Checklist kiểm thử interruption và cleanup

Test dùng `CountDownLatch` để điều khiển trạng thái thay vì chỉ `Thread.sleep(10)` rồi hy vọng worker đang chờ.

| Kịch bản | Điều cần xác minh |
| --- | --- |
| Thread được interrupt trước khi vào thao tác blocking | Blocking API nhận cờ interrupt và worker vẫn đi qua cleanup đúng. |
| Thread bị interrupt trong khi `await/take/sleep` | `InterruptedException` được xử lý, worker ngừng theo contract. |
| Tài nguyên mở thành công trước khi bị ngắt | `close()` của tài nguyên thuộc owner được gọi. |
| Interrupt trong khi acquisition thứ hai | Resource đầu tiên đã mở được đóng; tài nguyên chưa acquire không bị release sai. |
| Cleanup cũng ném exception | Exception chính và suppressed exception được giữ để chẩn đoán. |
| Task sở hữu permit/lock | Chỉ release/unlock khi acquisition thành công. |
| `Future.cancel(true)` trả kết quả | Không nhầm Future canceled với task cleanup complete. |
| Caller đợi `get()` bị ngắt | Kiểm tra chính caller có thể truyền tiếp/khôi phục cờ; task không tự động bị gán thất bại. |
| Task xử lý dữ liệu có side effect | Invariant, transaction/compensation và idempotency đáp ứng hợp đồng. |
| Cleanup không phản hồi interrupt | Hệ thống có deadline, metrics và chính sách escalation, không treo vô hạn ngoài ý muốn. |

Các test nên xác nhận **trạng thái quan sát được**: resource closed, subscription removed, permit không rò rỉ, worker không nhận job mới, acknowledgment đã phát. Không cần so khớp chính xác stack trace hoặc phụ thuộc vào timing tình cờ nếu hợp đồng không hứa thứ tự đó.

## 18. Những lỗi thường gặp và cách sửa

**Nuốt `InterruptedException` rồi tiếp tục vòng lặp.** Cờ có thể đã bị xóa, tín hiệu ngắt bị mất. Truyền tiếp, hoặc phục hồi cờ và kết thúc có chủ đích.

**Gọi `Thread.interrupted()` để kiểm tra rồi vô tình xóa cờ.** Dùng `isInterrupted()` khi chỉ cần đọc; chỉ clear khi có lý do và phải giữ/khôi phục tín hiệu nếu contract yêu cầu.

**Phục hồi cờ trước cleanup blocking mà không xem xét hậu quả.** Lệnh chờ trong cleanup có thể nhận interrupt ngay lập tức. Tổ chức cleanup trước catch ở ngoài try-with-resources, hoặc hoãn restoration có kiểm soát rồi phục hồi đúng lúc.

**Đặt `close()` chỉ ở cuối happy path.** Exception hoặc `return` sớm khiến cleanup bị bỏ qua. Dùng try-with-resources hoặc `finally` có cấu trúc.

**Đóng tài nguyên được mượn.** Quyền sở hữu không phải tên biến; không đóng shared pool/client/stream của caller ngoài hợp đồng.

**Release permit hoặc unlock khi acquire chưa thành công.** Nếu `Semaphore.acquire` hoặc `Lock.lockInterruptibly` bị ngắt trước acquisition, thao tác release/unlock vô điều kiện sẽ gây sai trạng thái.

**Coi cancel thành công là cleanup hoàn tất.** `cancel(true)` chỉ yêu cầu/đánh dấu cancellation theo implementation. Dùng completion/ack riêng nếu cần đợi xác nhận.

**Retry ngay khi bị ngắt.** Lý do interruption có thể là shutdown; retry có thể chống lại chính sách hủy, tạo side effect trùng và trì hoãn dừng.

**Nuốt lỗi `close()` hoặc để nó che exception chính.** Cần giữ primary/cause/suppressed và có chính sách khi dọn dẹp thất bại.

**Dùng `Thread.stop()` hay cưỡng bức thread theo cách làm hỏng invariant.** Thiết kế cooperative cancellation, timeout và điểm dừng an toàn.

**Dùng một kiểu trạng thái cho completed, failed và cancelled.** Caller cần phân biệt kết quả thành công, lỗi nghiệp vụ và kết thúc do hủy để quyết định đúng.

## 19. Bài tập tự kiểm tra

**Câu 1.** Worker chạy `queue.take()`, được gọi `interrupt()` rồi bắt `InterruptedException`. Nếu method không thể khai báo checked exception, có nên để catch rỗng?

**Trả lời:** Không. Thông thường cần phục hồi `Thread.currentThread().interrupt()` và kết thúc/đi vào nhánh dừng; cleanup tài nguyên thuộc owner theo cấu trúc phù hợp.

**Câu 2.** Code nhận `InterruptedException` rồi gọi `Thread.currentThread().interrupt()` trước khi cleanup bằng `latch.await()`. Điều gì có thể xảy ra?

**Trả lời:** `await()` có thể ném `InterruptedException` ngay vì cờ interrupt đã đặt. Nếu cần cleanup blocking có giới hạn, cân nhắc tổ chức cleanup trước điểm restoration hoặc lưu tín hiệu rồi khôi phục sau.

**Câu 3.** Worker được cấp connection bởi pool, và caller dùng shared executor. Khi bị ngắt, worker có nên `executor.shutdownNow()` và `pool.close()` không?

**Trả lời:** Không nếu worker chỉ mượn cả executor và pool. Worker trả connection/đóng handle do mình checkout theo hợp đồng; lifecycle executor/pool thuộc owner chung.

**Câu 4.** `Future.cancel(true)` trả `true` và `future.isCancelled()` trả `true`. Có khẳng định được `finally` của task đã chạy xong không?

**Trả lời:** Không. Task có thể còn đang phản hồi interrupt hoặc làm cleanup. Muốn biết xong phải dựa vào một tín hiệu/abstraction hoàn tất riêng.

**Câu 5.** Worker đang giữ một permit semaphore khi bị ngắt; một worker khác bị ngắt ngay tại `semaphore.acquire()` trước khi có permit. Hai worker có giống nhau về cleanup không?

**Trả lời:** Không. Chỉ worker đã acquire mới được release permit. Dùng cấu trúc đảm bảo biến trạng thái acquisition hoặc đặt `try/finally` sau điểm acquire thành công.

**Câu 6.** Body ném `InterruptedException`, `AutoCloseable.close` ném `IOException`. Try-with-resources làm gì?

**Trả lời:** `InterruptedException` từ body là primary; `IOException` từ close bị suppressed. Caller có thể đọc `getSuppressed()` để chẩn đoán cả hai, rồi phục hồi/lan truyền tín hiệu ngắt theo contract.

## 20. Ghi nhớ để áp dụng

Nhận interrupt nghĩa là ứng dụng được yêu cầu **hợp tác chuyển sang trạng thái dừng phù hợp**. Đối với code sở hữu tài nguyên, hãy đặt cleanup vào phạm vi được bảo đảm bởi `try-with-resources` hoặc `finally`, rời vòng lặp ở điểm an toàn, giữ thông tin interruption và phân biệt yêu cầu cancel với việc thực sự hoàn tất cleanup.

Quy tắc kiểm tra nhanh: **Ai sở hữu tài nguyên? Khi interrupt đến, tác vụ dừng ở đâu? Cleanup có chạy cả đường exception không? Cờ interrupt và lỗi cleanup có được bảo toàn không? Caller biết khi nào dừng xong bằng cách nào?** Trả lời rõ năm câu hỏi ấy giúp giảm rò rỉ tài nguyên, giữ invariant và làm shutdown có thể kiểm chứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-007">Vì sao không nên nuốt InterruptedException rồi tiếp tục như bình thường</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao không nên nuốt InterruptedException rồi tiếp tục như bình thường

## 1. Vấn đề: một tín hiệu dừng có thể biến mất

Trong Java, một luồng có thể bị yêu cầu **ngắt** (*interrupt*) khi một thành phần khác muốn nó ngừng chờ, hủy công việc, giải phóng tài nguyên hoặc kết thúc có kiểm soát. Việc gọi `thread.interrupt()` gửi tín hiệu tới **luồng đích**, thường qua trạng thái interrupt của luồng; nó không cưỡng chế giết luồng hay tự động hoàn tác công việc đã làm.

Nếu luồng đang ở thao tác chờ có hỗ trợ interruption như `Thread.sleep()`, `Thread.join()` hoặc `BlockingQueue.take()`, nó có thể nhận `InterruptedException`. Nhiều phương thức chờ theo cơ chế này **xóa trạng thái interrupt khi ném ngoại lệ**. Do đó, một đoạn mã như sau có thể vô tình làm mất yêu cầu hủy:

```java
try {
    Thread.sleep(10_000);
} catch (InterruptedException ex) {
    // Bỏ qua rồi tiếp tục công việc.
}
doMoreWork();
```

Đây là **mảnh mã trong một phương thức** có `doMoreWork()` đã được định nghĩa, không phải chương trình độc lập. Nếu có luồng khác yêu cầu ngắt trong lúc `sleep`, phương thức thoát `sleep` qua exception, nhưng handler không chuyển tiếp tín hiệu, không khôi phục cờ, cũng không thay đổi đường điều khiển. `doMoreWork()` vẫn được gọi. Với một vòng lặp worker dài hạn, tác vụ có thể tiếp tục hoạt động dù hệ thống đã yêu cầu dừng.

**“Nuốt `InterruptedException`”** nghĩa là bắt tín hiệu ngắt rồi không chuyển tiếp cho tầng có trách nhiệm, không giữ trạng thái, và vẫn vận hành như chưa xảy ra yêu cầu hủy. Đây là vấn đề về **giao thức phối hợp giữa các luồng**, không chỉ là mất một thông báo lỗi.

## 2. Interrupt là yêu cầu hợp tác, không phải lệnh dừng cưỡng bức

Khi A gọi `worker.interrupt()`, các hành vi chính cần phân biệt:

1. JVM đặt trạng thái interrupted cho **worker**, trừ những trường hợp API chờ phản ứng bằng cách ném `InterruptedException` và xóa trạng thái đó.
2. Nếu worker đang chạy tính toán không kiểm tra interrupt, lời gọi `interrupt()` **không tự dừng** các lệnh tính toán.
3. Nếu worker đang chờ ở một phương thức có hợp đồng interruptible, thao tác chờ có thể ném `InterruptedException` để worker quyết định thoát, trả lỗi hoặc dọn dẹp.
4. Worker có thể chủ động kiểm tra `Thread.currentThread().isInterrupted()` trong vòng lặp không có điểm chờ.
5. Interruption không có chức năng tự rollback thay đổi đối tượng, database, file hoặc request mạng.

Không nên đồng nhất `InterruptedException` với lỗi I/O bình thường hoặc lỗi đầu vào người dùng. Trong nhiều thư viện concurrency, nó thể hiện **một yêu cầu hủy hoặc một thay đổi về chính sách điều khiển luồng**. Nuốt tín hiệu này có thể phá hợp đồng dừng của thành phần cấp trên.

Tuy nhiên, `interrupt()` không luôn có nghĩa “hủy toàn bộ” trong mọi ứng dụng. Ý nghĩa cụ thể do giao thức giữa các thành phần quyết định; nếu một tầng có trách nhiệm xử lý interruption hoàn toàn, nó có thể tiêu thụ tín hiệu một cách có chủ đích. Vấn đề là **bỏ qua mà không đưa ra quyết định điều khiển phù hợp**.

## 3. Cơ chế cờ interrupt và hành vi của các phương thức chờ

Mỗi `Thread` có trạng thái interrupted. Những API quan trọng:

| API | Tác động |
| --- | --- |
| `someThread.interrupt()` | Gửi tín hiệu ngắt tới luồng `someThread`; không chờ nó kết thúc |
| `Thread.currentThread().isInterrupted()` | Kiểm tra cờ của luồng hiện tại; **không xóa cờ** |
| `someThread.isInterrupted()` | Kiểm tra cờ của một luồng được tham chiếu; **không xóa cờ** |
| `Thread.interrupted()` | Phương thức `static` kiểm tra **luồng hiện tại** và **xóa cờ** |
| `Thread.currentThread().interrupt()` | Đặt/khôi phục cờ interrupted cho luồng hiện tại |
| `Thread.sleep(...)` | Nếu bị interrupt khi chờ (hoặc có cờ ngắt khi vào), ném `InterruptedException` và xóa cờ theo hợp đồng |
| `Thread.join(...)`, `Object.wait(...)` | Có thể ném `InterruptedException` khi luồng chờ bị ngắt và xóa cờ |

Hai phương thức dễ nhầm nhất là:

```java
boolean flagStillThere = Thread.currentThread().isInterrupted();
boolean flagAndClearIt = Thread.interrupted();
```

Đây là hai câu lệnh Java hợp lệ bên trong một phương thức. `isInterrupted()` chỉ đọc cờ, còn `Thread.interrupted()` có hiệu ứng **clear-on-read**. Trong loop điều khiển hủy, vô tình dùng `Thread.interrupted()` rồi bỏ qua giá trị trả về có thể xóa mất yêu cầu dừng ngay cả khi không hề catch một `InterruptedException`.

Các API trong `java.util.concurrent` như `BlockingQueue.take()`, `CountDownLatch.await()`, `Future.get()`, `Semaphore.acquire()`, `Lock.lockInterruptibly()` và `Condition.await()` cũng hỗ trợ interruption theo hợp đồng riêng. Nhiều API blocking xóa trạng thái khi ném `InterruptedException`; cần đọc đúng API cụ thể thay vì giả định mọi lời gọi Java đều có cùng hành vi.

Một số API như `LockSupport.park()` có semantics khác: có thể **trở về** khi bị interrupt mà không ném `InterruptedException` và không xóa cờ. Lệnh I/O hoặc thao tác native cũng không nhất thiết phản ứng nhanh với interruption. Vì vậy việc một luồng còn đang chạy sau `interrupt()` không tự chứng minh chương trình sai; cần xem **điểm hợp tác hủy** và trách nhiệm của tác vụ.

## 4. Tại sao `catch (InterruptedException) {}` nguy hiểm?

Nuốt interruption có thể gây nhiều hệ quả:

- **Hủy không hiệu lực:** `Future.cancel(true)` hoặc `ExecutorService.shutdownNow()` đã cố interrupt worker, nhưng task tiếp tục như bình thường.
- **Dừng ứng dụng chậm hoặc không dừng:** các shutdown hook hoặc controller đang đợi worker thoát trong khi worker cứ gọi lại thao tác chờ.
- **Xóa dấu vết hủy:** vì một số phương thức chờ xóa cờ khi ném, tầng trên gọi `isInterrupted()` có thể thấy `false` và hiểu nhầm không có yêu cầu dừng.
- **Chờ thêm lần nữa:** handler bỏ qua exception rồi quay lại `sleep()`, `take()` hoặc `await()`; vì cờ đã bị xóa, lần chờ kế tiếp có thể lại block đến khi có một tín hiệu mới.
- **Tác dụng phụ sau hủy:** worker tiếp tục ghi database, gửi thông báo, xử lý job hoặc thực hiện giao dịch mà caller tưởng đã yêu cầu hủy.
- **Sai contract giữa các tầng:** thư viện đã được yêu cầu kết thúc nhưng che tín hiệu, khiến lớp service hoặc scheduler không biết chính sách dừng đã bị xâm phạm.
- **Khó chẩn đoán:** log có thể chỉ ghi “sleep interrupted” như một cảnh báo không quan trọng, trong khi vấn đề thực là vòng đời không đi theo lệnh hủy.

Một câu lệnh `catch` trống đặc biệt khó review vì người đọc không biết đó là quyết định có chủ đích hay sai sót. Nếu thật sự muốn tiêu thụ interruption, cần mô tả rõ **ai sở hữu yêu cầu hủy** và vì sao trạng thái đã được xử lý đầy đủ.

## 5. Ví dụ Java 17+ chạy được: nuốt tín hiệu và khôi phục tín hiệu

Chương trình sau tạo hai worker tuần tự. Mỗi worker báo đã bắt đầu qua `CountDownLatch` rồi chờ bằng `Thread.sleep(...)`. Luồng main yêu cầu ngắt worker.

- `SWALLOW`: bắt `InterruptedException` rồi vẫn chạy bước tiếp theo; do `sleep` xóa cờ, `isInterrupted()` trong handler là `false`.
- `RESTORE`: bắt lỗi, gọi `Thread.currentThread().interrupt()` và `return` để ngừng task; cờ có giá trị `true` ở điểm kiểm tra sau khôi phục.

Lưu thành **`InterruptedExceptionHandlingDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;

public class InterruptedExceptionHandlingDemo {
    private static void runCase(String name, boolean restore)
            throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                started.countDown();
                Thread.sleep(60_000);
            } catch (InterruptedException ex) {
                System.out.println(name + " caught; flag="
                        + Thread.currentThread().isInterrupted());
                if (restore) {
                    Thread.currentThread().interrupt();
                    System.out.println(name + " stopped; flag="
                            + Thread.currentThread().isInterrupted());
                    return;
                }
                System.out.println(name + " continued despite cancellation");
            }
        }, name);
        worker.start();
        started.await();
        worker.interrupt();
        worker.join();
    }

    public static void main(String[] args) throws InterruptedException {
        runCase("SWALLOW", false);
        runCase("RESTORE", true);
        System.out.println("DONE");
    }
}
```

Biên dịch và chạy trên JDK 17 trở lên:

```bash
javac -encoding UTF-8 InterruptedExceptionHandlingDemo.java
java InterruptedExceptionHandlingDemo
```

Standard output:

```text
SWALLOW caught; flag=false
SWALLOW continued despite cancellation
RESTORE caught; flag=false
RESTORE stopped; flag=true
DONE
```

**Vì sao output này đáng chú ý?**

1. `started.await()` chỉ xác nhận worker đã chạy đến điểm báo hiệu; main có thể interrupt ngay trước hoặc trong `Thread.sleep`. Cả hai đều khiến thao tác `sleep` phản ứng bằng `InterruptedException`.
2. `Thread.sleep` xóa cờ khi ném, vì vậy cả hai handler in `flag=false` ngay lúc vào `catch`.
3. Trong trường hợp `SWALLOW`, chương trình cố tình thực hiện bước “continued despite cancellation”. Trong worker thực tế có loop, một lần bỏ qua như vậy có thể gây xử lý tiếp nhiều lượt.
4. Trong trường hợp `RESTORE`, handler khôi phục cờ `true` rồi **kết thúc Runnable**. Việc restore đơn thuần mà không thoát đường điều khiển có thể vẫn dẫn đến loop tiếp tục chạy và các lời gọi blocking sau lại lập tức bị interrupt.
5. `worker.join()` chỉ chờ worker kết thúc; nó không chuyển `InterruptedException` xảy ra **ở worker** về main. `runCase` khai báo `throws InterruptedException` vì chính luồng main khi chờ `await` hoặc `join` cũng có thể bị interrupt.

Ví dụ cố tình nhỏ và không có tác dụng phụ thật, không cho thấy mọi dạng I/O đều interruptible. `Thread.sleep(60_000)` là thời gian chờ dự kiến, nhưng trong demo main yêu cầu ngắt ngay nên không đợi đến một phút. Nếu môi trường chạy rất đặc biệt làm main bị interrupt ngoài chương trình, nó có thể ném `InterruptedException` ở `await` hoặc `join`; output liệt kê giả định chương trình chạy bình thường và chỉ có các lệnh interrupt được chỉ ra trong mã.

## 6. Mẫu tốt nhất khi hợp đồng cho phép: truyền tiếp `InterruptedException`

Nếu phương thức đang viết **có thể khai báo `throws InterruptedException`**, cách dễ bảo toàn tín hiệu nhất thường là không bắt nó hoặc bắt chỉ để dọn dẹp rồi ném tiếp:

```java
void waitForItem(BlockingQueue<String> queue)
        throws InterruptedException {
    String item = queue.take();
    process(item);
}
```

Đây là mảnh phương thức trong một lớp có `process(String)` đã được định nghĩa; cần import `java.util.concurrent.BlockingQueue`. `queue.take()` có thể chờ và ném `InterruptedException`. Khi exception thoát từ `waitForItem`, tầng gọi biết thao tác đã dừng vì bị yêu cầu ngắt và có thể quyết định hủy cả quy trình.

Nếu phải dọn dẹp:

```java
void runOneStep() throws InterruptedException {
    Resource resource = acquireResource();
    try {
        blockingOperation();
    } finally {
        resource.close();
    }
}
```

Đây là mẫu cấu trúc, với `Resource`, `acquireResource()` và `blockingOperation()` do ứng dụng định nghĩa. `finally` chạy trên đường rời phương thức khi `InterruptedException` được truyền tiếp. Cần thiết kế `close()` sao cho xử lý được lỗi cleanup, không vô tình che exception ngắt ban đầu.

**Không nhất thiết khôi phục cờ trong mọi đoạn bắt rồi `throw ex`**: với phương thức khai báo `throws InterruptedException`, caller đã nhận được tín hiệu rõ ràng qua exception. Nhiều hợp đồng chờ chủ động xóa interrupt flag khi ném; việc truyền tiếp exception giữ đúng cơ chế đó. Nếu caller cần khôi phục cờ để truyền tín hiệu tới mã cao hơn không thể khai báo ngoại lệ, hãy thực hiện tại ranh giới thích hợp.

## 7. Khi không thể ném checked exception: khôi phục rồi kết thúc

`Runnable.run()` không khai báo `throws InterruptedException`. Với một worker muốn kết thúc khi bị interrupt, mẫu phổ biến:

```java
@Override
public void run() {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            WorkItem item = queue.take();
            process(item);
        }
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
    } finally {
        releaseResources();
    }
}
```

Đây là đoạn trong một lớp đã định nghĩa `queue` kiểu `BlockingQueue<WorkItem>` cùng `process`, `releaseResources`; không phải chương trình độc lập. Nó có ba điểm:

- Điều kiện vòng lặp chủ động kiểm tra cờ interrupted.
- `queue.take()` bị interrupt thì ném exception; handler **khôi phục cờ** để đường xử lý phía trên còn thấy tín hiệu.
- `return` kết thúc `run()`, còn `finally` vẫn thực hiện cleanup.

Mẫu này chỉ phù hợp khi interruption được định nghĩa là **yêu cầu kết thúc worker**. Nếu logic nghiệp vụ có transaction phải rollback hoặc checkpoint trước khi dừng, cần tổ chức cleanup có kiểm soát, không chỉ thêm `return`.

Cần tránh mẫu nửa vời:

```java
while (true) {
    try {
        Thread.sleep(1000);
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
    }
}
```

Đây là **ví dụ sai có chủ đích**. Sau khi handler khôi phục cờ, vòng lặp lại gọi `sleep`. Vì cờ đã đặt, `sleep` có thể lập tức ném `InterruptedException` và xóa cờ; handler lại đặt cờ. Kết quả có thể là **vòng lặp nóng**, tiêu tốn CPU, không làm việc hữu ích và không kết thúc. Vì vậy **khôi phục cờ phải đi cùng đường điều khiển đúng**, thường là `break`, `return` hoặc truyền một kết quả hủy rõ ràng.

## 8. Vì sao phải cân nhắc cả `catch (Exception)` và các wrapper?

Một phương thức có thể vô tình nuốt interruption dù không viết `catch (InterruptedException)` riêng:

```java
try {
    queue.take();
} catch (Exception ex) {
    logger.warn("Cannot receive next item", ex);
    // Tiếp tục vòng lặp — đã nuốt InterruptedException.
}
```

Đây là **mảnh minh họa lỗi**, với `queue`, `logger` do ứng dụng định nghĩa. `InterruptedException` kế thừa `Exception`, nên khối `catch (Exception)` bắt được nó. Nếu mã cứ tiếp tục, yêu cầu shutdown có thể mất.

Cũng cần kiểm tra code bắt các ngoại lệ cha khác và các adapter đổi checked exception sang unchecked:

- `catch (Throwable)` còn bao gồm `Error`, có thể che tình trạng JVM không an toàn; không phải giải pháp để worker “không bao giờ chết”.
- `throw new RuntimeException(ex)` giữ `cause` nhưng **chưa chắc bảo toàn interrupt status**. Nếu tầng ngoài chỉ kiểm tra cờ, thông tin có thể bị mất.
- Ghi log `ex.getMessage()` rồi bỏ qua không báo đủ ý nghĩa điều khiển của interruption.
- Gói tất cả lỗi thành “request timeout” hoặc “unknown failure” khiến hệ thống không phân biệt bị hủy với phụ thuộc thất bại.

Nếu đang ở một ranh giới API bắt buộc phải chuyển `InterruptedException` sang unchecked và không còn chữ ký truyền tiếp, một mẫu thường dùng là:

```java
try {
    blockingOperation();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Operation interrupted", ex);
}
```

Đây là **mảnh triển khai**, với `blockingOperation()` là hàm interruptible. Nó vừa giữ `cause` vừa khôi phục trạng thái. Tuy nhiên, loại wrapper và quyết định ném phải theo hợp đồng API. Đừng đổi interruption thành lỗi trạng thái khi caller cần phân biệt hủy riêng biệt; một ngoại lệ chuyên biệt có thể phù hợp hơn. Nếu task đã đáp ứng yêu cầu hủy bằng cách kết thúc bình thường và chỉ cần báo “cancelled”, việc ném wrapper cũng không phải lựa chọn bắt buộc.

## 9. Khi nào được phép “tiêu thụ” interruption?

Có những thành phần **sở hữu toàn bộ giao thức hủy** và có trách nhiệm xử lý việc ngắt ngay tại chỗ. Khi đó, không truyền tiếp hoặc không khôi phục cờ có thể là quyết định có chủ đích. Ví dụ một controller đang quản lý dừng hệ thống có thể bắt tín hiệu, chuyển state sang STOPPING, thực hiện cleanup đến trạng thái đã xác nhận, rồi trả kết quả theo hợp đồng riêng.

Để một trường hợp được xem là xử lý đúng, cần chứng minh:

1. Thành phần bắt exception chính là bên chịu trách nhiệm **hoàn tất xử lý tín hiệu hủy**.
2. Tác vụ không tiếp tục thực hiện side effect sai hợp đồng sau khi yêu cầu hủy đã được chấp nhận.
3. Caller hoặc thành phần quản lý vòng đời có cách nhận biết tác vụ đã dừng/hủy đúng, ví dụ Future, trạng thái hoặc callback.
4. Cleanup không che lỗi quan trọng và không đợi vô hạn vì cờ đã bị mất.
5. Quyết định được mô tả và kiểm thử rõ, không phải `catch` trống do quên xử lý.

Một số thư viện có API chỉ chấp nhận trả về bình thường hoặc thông báo cancellation qua kết quả riêng. Trong trường hợp ấy có thể tiêu thụ exception nếu quy trình hủy được hoàn tất và trạng thái phía ngoài vẫn chính xác.

Câu “phải luôn gọi `Thread.currentThread().interrupt()` ngay khi bắt `InterruptedException`” là **quá tuyệt đối**. Với phương thức khai báo checked exception, truyền tiếp `InterruptedException` đã là cách bảo toàn tín hiệu. Với nơi thật sự xử lý xong interruption, giữ cờ có thể làm các thao tác sau bị ngắt ngoài ý muốn. Điều cần tránh là **nuốt vô thức rồi tiếp tục như bình thường**.

## 10. Các API thường phát tín hiệu ngắt như thế nào?

| API | Thao tác | Khi bị interrupt |
| --- | --- | --- |
| `Thread.sleep` | Tạm dừng luồng | Ném `InterruptedException` và xóa trạng thái interrupt |
| `Object.wait` | Chờ monitor theo giao thức wait/notify | Ném `InterruptedException`, xóa trạng thái; phải sở hữu monitor hợp lệ |
| `Thread.join` | Chờ một thread kết thúc | Luồng **đang đợi** nhận `InterruptedException`, không phải lỗi của thread được join |
| `BlockingQueue.take` | Chờ phần tử có sẵn | Có thể ném `InterruptedException`; tác vụ nên dừng/chuyển tiếp đúng hợp đồng |
| `CountDownLatch.await` | Chờ counter về 0 | Có thể ném `InterruptedException` |
| `Future.get` | Chờ kết quả tác vụ | Có thể ném `InterruptedException` ở **thread đang chờ**, khác với `ExecutionException` của task |
| `Semaphore.acquire` | Chờ permit | Có thể ném `InterruptedException` |
| `Lock.lockInterruptibly` | Đợi lock theo cách hỗ trợ ngắt | Có thể ném `InterruptedException` |
| `ExecutorService.awaitTermination` | Chờ executor kết thúc | Có thể ném `InterruptedException` ở thread đang chờ |
| `LockSupport.park` | Dừng chờ theo permit/interrupt | Có thể trở về khi bị ngắt, không phải đường `InterruptedException` thông thường |

Không phải mọi thao tác chờ đều hỗ trợ interrupt. Ví dụ `ReentrantLock.lock()` bình thường không có hợp đồng ném `InterruptedException` trong lúc chờ lock; hãy dùng `lockInterruptibly()` khi muốn hủy theo interrupt. Một số thao tác I/O có cách phản ứng khác nhau tùy kênh, provider và OS. Nếu cần bảo đảm dừng đúng hạn, phải có timeout hoặc cơ chế đóng/hủy tài nguyên thích hợp, không chỉ dựa vào `interrupt()`.

## 11. `Future.cancel(true)` và `shutdownNow()` không thể đảm bảo task dừng

Khi tác vụ chạy trong `ExecutorService`, code quản lý thường dùng:

```java
Future<?> future = executor.submit(task);
future.cancel(true);
```

Đây là mảnh code có `executor` và `task` đã định nghĩa. `cancel(true)` **yêu cầu** hủy công việc và có thể interrupt luồng thực thi nếu tác vụ đang chạy. Điều này không đồng nghĩa JVM đã cưỡng chế hủy mọi tính toán hoặc đảo ngược side effect.

Nếu task:

- đang gọi API interruptible và **tuân thủ tín hiệu**, nó có thể thoát sớm;
- bắt `InterruptedException` rồi bỏ qua, nó **có thể tiếp tục công việc** dù future đã báo cancelled;
- đang tính CPU dài nhưng không kiểm tra cờ, nó có thể tiếp tục cho tới khi kết thúc;
- đang ở thao tác I/O không đáp ứng interruption, nó có thể chưa dừng ngay;
- đã commit DB hoặc gửi request mạng, những hiệu ứng đó không tự bị rollback.

`ExecutorService.shutdownNow()` cố gắng dừng tác vụ đang thực thi bằng interruption, ngừng nhận công việc mới và trả về danh sách tác vụ chưa bắt đầu. Nó là *best effort*; nếu task bỏ qua interrupt, executor có thể không kết thúc trong thời gian mong đợi. `shutdown()` là yêu cầu ngừng nhận task mới nhưng cho công việc đã nộp tiếp tục theo chính sách của executor.

Với `CompletableFuture`, nên nhớ `cancel(boolean mayInterruptIfRunning)` không dùng tham số này để điều khiển interruption theo cách tương đương `FutureTask`; trong triển khai `CompletableFuture`, tham số đó không có hiệu lực vì interrupt không được dùng để kiểm soát xử lý. Đừng xây giao thức dừng task trên giả định mọi API future đều có cùng cơ chế interrupt.

## 12. Hủy khi đang chờ và lỗi của công việc là hai loại thông tin khác nhau

Trong code concurrency, một luồng A có thể chờ kết quả do B thực hiện:

```java
try {
    String value = future.get();
    use(value);
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
} catch (ExecutionException ex) {
    handleTaskFailure(ex.getCause());
}
```

Đây là **mảnh code trong phương thức trả về `void`**, với `future`, `use`, `handleTaskFailure` do ứng dụng cung cấp và import `ExecutionException`.

Phân biệt:

- `InterruptedException`: **A đang chờ** bị yêu cầu ngắt. Việc chờ bị gián đoạn; B có thể **vẫn đang chạy**. Muốn hủy cả B cần chính sách riêng, ví dụ `future.cancel(true)` nếu thích hợp.
- `ExecutionException`: task B hoàn tất do lỗi; `getCause()` giữ nguyên nhân thất bại của B. Đây là thông tin **kết quả tác vụ**, không phải tín hiệu interrupt của A.
- `CancellationException`: Future ở trạng thái hủy; điều đó không nhất thiết chứng minh mọi side effect của task đã dừng.
- `TimeoutException` của `future.get(timeout, unit)`: A đợi quá hạn; B có thể tiếp tục chạy trừ khi được hủy theo chính sách.

Nuốt `InterruptedException` trong A rồi gọi `get()` lại có thể khiến A tiếp tục chờ không giới hạn, phá deadline hoặc yêu cầu shutdown. Đó là ví dụ thường gặp của việc “đã yêu cầu ngừng chờ nhưng lại chờ tiếp”.

## 13. Xử lý interruption trong vòng lặp worker

Một worker dài hạn thường có ba loại bước: nhận yêu cầu, xử lý dữ liệu, ghi nhận kết quả. Nếu dùng `BlockingQueue.take()`, thiết kế hợp tác hủy có thể là:

```java
void workerLoop(BlockingQueue<Job> queue)
        throws InterruptedException {
    while (!Thread.currentThread().isInterrupted()) {
        Job job = queue.take();
        execute(job);
    }
}
```

Đây là **mảnh phương thức**, với `Job` và `execute(Job)` do ứng dụng định nghĩa. `take()` nhận tín hiệu interrupt đúng quy tắc, nhưng `execute(job)` cũng cần có hợp đồng xử lý hủy và trạng thái. Nếu `execute` chặn trong I/O hoặc làm việc CPU nặng không có điểm kiểm tra, worker sẽ chưa dừng ngay.

Nếu thay bằng một loop luôn bắt rồi bỏ qua:

```java
while (true) {
    try {
        Job job = queue.take();
        execute(job);
    } catch (InterruptedException ex) {
        // Không làm gì, lặp lại take().
    }
}
```

Đây là **ví dụ sai có chủ đích**. Sau interrupt, `take()` ném, cờ bị xóa, vòng lặp tiếp tục chờ job mới. Điều đó có thể khiến shutdown không thể hoàn tất, dù các thành phần quản lý đã gửi yêu cầu hủy.

Trong thiết kế production còn phải quyết định:

- Nếu worker bị hủy giữa khi xử lý job, job được đánh dấu **cancelled**, **retryable** hay **unknown**?
- Nếu job đã ghi một phần, có transaction/rollback, checkpoint hoặc bù trừ nào được phép?
- Nếu nhiều worker cùng dùng queue, interruption có thể xảy ra sau khi đã lấy một job; job đó cần được hoàn trả/ghi tiến độ theo hợp đồng nào?
- Nếu cleanup xảy ra khi cờ interrupt đang đặt, các thao tác blocking tiếp theo có thể lại ném exception ngay; cần lập kế hoạch cleanup có giới hạn và xử lý lỗi riêng.

Không nên suy ra “đã nhận `InterruptedException`” đồng nghĩa “mọi trạng thái đã được rollback”. Đó là hai mối quan tâm riêng.

## 14. Cleanup, trạng thái và tính nhất quán khi ngắt

Khi nhận yêu cầu hủy, ứng dụng có thể đang giữ lock, file handle, transaction hoặc trạng thái đang thay đổi. Hai nguyên tắc phối hợp:

**Đầu tiên, không che yêu cầu hủy.** Thông thường truyền tiếp `InterruptedException` hoặc khôi phục interrupt flag trước khi kết thúc task, để cấp trên nhận diện được trạng thái.

**Tiếp theo, đảm bảo cleanup theo phạm vi thực tế.** Dùng `finally`, `try-with-resources`, rollback transaction, release lock hoặc checkpoint đúng hợp đồng. Các tác vụ cleanup có thể ném exception hoặc block; không thể giả định quá trình khôi phục luôn thành công.

Ví dụ về cấu trúc có ý nghĩa (mảnh code, các phương thức liên quan phải được định nghĩa):

```java
try {
    startTransaction();
    doInterruptibleWork();
    commitTransaction();
} catch (InterruptedException ex) {
    try {
        rollbackTransaction();
    } catch (RuntimeException rollbackFailure) {
        ex.addSuppressed(rollbackFailure);
    }
    throw ex;
} finally {
    releaseResources();
}
```

Đoạn này giả định phương thức ngoài **khai báo `throws InterruptedException`** và các thao tác DB/rollback là những hàm ứng dụng với kiểu exception tương thích đã định nghĩa. `addSuppressed` giữ thông tin rollback lỗi nhưng **không làm rollback thành công**. Đây chỉ là cấu trúc phân tích; ứng dụng thật phải xử lý mọi ngoại lệ DB, tính không chắc chắn của commit, việc rollback cũng có thể bị interrupt và thông tin liên quan đến giao dịch.

Nếu `commitTransaction()` đã thành công rồi mới có một yêu cầu hủy, dữ liệu commit vẫn có thể tồn tại. Không có bảo đảm “interrupt tức rollback”. Đừng tự động retry một job có side effect sau khi hủy mà không dùng idempotency hoặc đối chiếu.

## 15. Gắn interruption với chính sách timeout và deadline

Yêu cầu ngắt và timeout liên quan nhưng không đồng nhất:

- **Deadline/timeout:** giới hạn thời gian hoàn tất hoặc chờ; `get(timeout)` có thể ném `TimeoutException` cho luồng gọi.
- **Interrupt:** yêu cầu luồng cụ thể ngừng chờ hoặc hợp tác dừng; nhiều API trả `InterruptedException`.
- **Cancellation:** trạng thái vòng đời của task/request, có thể kích hoạt interrupt nhưng còn có semantics về kết quả và side effect.

Một controller có thể hủy future do hết thời gian, nhưng nếu worker nuốt interruption rồi tiếp tục, thời gian thực thi có thể vượt deadline và còn gửi tác dụng phụ sau khi caller đã bỏ cuộc.

Một chiến lược có thể kết hợp timeout, `cancel(true)` khi API hỗ trợ, check cờ interrupt tại các điểm hợp lý, và deadline truyền xuống dependency. Đừng coi `interrupt()` là cơ chế timeout tổng quát cho mọi socket, file, JDBC driver hay dịch vụ bên ngoài.

## 16. Đừng dùng interruption như lỗi nghiệp vụ để retry vô hạn

Ví dụ worker gửi request tới backend. Nếu lỗi mạng tạm thời, có thể retry với số lần giới hạn, backoff và jitter. Nhưng nếu thread bị interrupt, thường đó là tín hiệu từ hệ thống quản lý rằng **không nên tiếp tục retry**.

Sai lầm thường thấy:

```java
while (true) {
    try {
        requestRemote();
        break;
    } catch (Exception ex) {
        Thread.sleep(1000);
    }
}
```

Đây là **mảnh code sai có chủ đích**, với `requestRemote()` đã định nghĩa; để biên dịch phải xử lý hoặc khai báo checked `InterruptedException` từ `sleep`. Nếu vòng retry bắt `Exception` mà không phân biệt lỗi hủy, hoặc catch interrupt ở tầng trên rồi tiếp tục, nó có thể chống lại shutdown.

Mẫu chính sách tốt hơn:

1. Nếu lỗi là **InterruptedException**, truyền tiếp hoặc khôi phục cờ rồi rời task.
2. Nếu là lỗi phụ thuộc có thể retry, kiểm tra deadline và trạng thái interrupt trước vòng kế.
3. Backoff bằng thao tác chờ interruptible để có thể dừng nhanh khi bị hủy.
4. Nếu retry có side effect, dùng idempotency key và kiểm tra kết quả chưa rõ.
5. Khi vượt ngân sách retry, báo lỗi có ngữ cảnh và kết thúc theo hợp đồng.

Không tự động convert interruption thành `IllegalStateException` rồi đưa nó vào nhánh “retry lỗi tạm thời”. Chính sách retry phải nhận biết cancellation là trường hợp riêng.

## 17. Virtual threads cũng cần tôn trọng interruption

Virtual thread (ổn định từ Java 21) vẫn là một `Thread` với trạng thái interrupt và các quy tắc `InterruptedException` ở những API hỗ trợ. Nó giúp chạy nhiều tác vụ blocking nhẹ hơn, **không** làm cancellation tự động hay rollback.

Một virtual thread ngủ trong `Thread.sleep(...)` cũng có thể bị interrupt, ném `InterruptedException` và yêu cầu code quyết định thoát/khôi phục tín hiệu. Nếu handler bỏ qua rồi tiếp tục, virtual thread vẫn có thể thực hiện công việc sai hợp đồng.

Nếu dùng các phiên bản Java mới có **structured concurrency** hoặc framework quản lý nhóm task, hủy các sibling task thường dựa vào interrupt hoặc cơ chế tương tự. Một tác vụ con nuốt tín hiệu có thể làm nhóm khó hoàn tất đúng thời hạn. API structured concurrency và trạng thái ổn định thay đổi theo phiên bản JDK; cần xem hợp đồng JDK thực tế, không áp đặt một đoạn demo của Java 17 cho API không có ở Java 17.

## 18. Cách kiểm thử việc không nuốt interruption

Kiểm thử phải xác nhận **tín hiệu và hành vi sau tín hiệu**, không chỉ assert rằng log có chữ “InterruptedException”.

| Trường hợp | Điều cần xác minh |
| --- | --- |
| Thread đang `sleep` bị interrupt | `InterruptedException` xuất hiện trong chính thread bị ngắt |
| Handler nuốt rồi tiếp tục | Có thể nhìn thấy bước xử lý sau yêu cầu hủy (để phát hiện lỗi) |
| Handler restore và return | Cờ được khôi phục tại điểm cần thiết; task không làm bước nghiệp vụ tiếp theo |
| Phương thức `throws InterruptedException` | Exception được truyền lên caller thay vì bị che |
| Worker dùng `BlockingQueue.take()` | Hủy làm worker rời vòng lặp, không quay lại chờ job vô hạn |
| `Future.cancel(true)` | Task có phản ứng phù hợp; không suy ra mọi side effect đã bị rollback |
| Bị interrupt lúc đang cleanup | Tài nguyên được xử lý theo chính sách; không làm mất thông tin lỗi chính |
| Timeout chờ `Future.get(timeout)` | Caller phân biệt timeout với interrupt và lỗi thật của task |
| Luồng khác đang làm việc | Interrupt một worker không tự “lan exception” sang call stack của worker khác |
| Một loop dùng `Thread.interrupted()` | Xác nhận hành vi clear-on-read không làm tín hiệu hủy bị mất |

Để test ổn định, dùng `CountDownLatch`, `Future`, `AtomicBoolean` hoặc các synchronization primitive để kiểm soát thứ tự, kèm timeout đủ rộng. Tránh test chỉ dựa trên `Thread.sleep(100)` và suy luận scheduler chắc chắn đã chạy. Luôn bảo đảm thread/executor được yêu cầu kết thúc trong `finally`, kể cả khi assertion lỗi.

Nếu một tác vụ không hợp tác và tiếp tục mãi, test cần có giới hạn thời gian và cơ chế cách ly thích hợp; không để cả bộ test treo vô hạn.

## 19. Các lỗi phổ biến và cách sửa

| Cách viết có vấn đề | Hậu quả | Hướng sửa |
| --- | --- | --- |
| `catch (InterruptedException ignored) {}` | Xóa dấu vết hủy, task tiếp tục | Truyền tiếp, hoặc restore cờ và thoát task |
| Bắt `Exception` rồi log và loop tiếp | Nuốt cả interruption của các API blocking | Catch riêng `InterruptedException` trước và tuân thủ hợp đồng |
| `Thread.currentThread().interrupt()` rồi tiếp tục `while(true)` | Lặp nóng khi API chờ ném ngay lần kế | `break`/`return` hoặc phối hợp trạng thái hủy |
| Dùng `Thread.interrupted()` chỉ để kiểm tra mà bỏ kết quả | Vô tình xóa cờ | Dùng `isInterrupted()` nếu cần kiểm tra không phá cờ |
| Coi `cancel(true)` là dừng cưỡng chế | Task có thể còn chạy và tạo side effect | Thiết kế cancellation hợp tác và idempotency |
| Nuốt interrupt để “bảo đảm hoàn tất job” | Có thể phá deadline/shutdown của caller | Tách yêu cầu bắt buộc hoàn tất với giao thức hủy, tài liệu hóa rõ |
| Đổi `InterruptedException` thành lỗi retry thông thường | Worker tiếp tục thử khi đã bị hủy | Phân loại cancellation riêng, giữ cause/flag khi cần |
| Cho rằng interruption rollback DB/file | Dữ liệu có thể đã commit/ghi | Transaction, cleanup, checkpoint và đối soát độc lập |
| Đoán mọi API I/O phản ứng interrupt ngay | Task không dừng đúng hạn | Kiểm tra contract, timeout, đóng tài nguyên khi phù hợp |
| Chỉ ghi log một lần rồi không ai quan sát kết quả | Caller vẫn tưởng task còn chạy | Trả future/trạng thái/callback lỗi rõ ràng |

## 20. Checklist thiết kế API và review code

1. Xác định **ai có quyền interrupt**, interrupt mang nghĩa **cancel**, shutdown, hủy một lần chờ hay tín hiệu khác.
2. Liệt kê các điểm chờ interruptible: `sleep`, `wait`, `join`, `take`, `get`, lock, latch và các API tích hợp khác.
3. Nếu phương thức có thể khai báo `throws InterruptedException`, ưu tiên **truyền tiếp** tín hiệu cho tầng sở hữu chính sách.
4. Nếu không thể ném checked exception, chọn chính sách xử lý rõ; trường hợp dừng task thường **restore flag + return/break**, và dọn dẹp trong `finally`.
5. Tránh `catch (Exception)` hoặc `catch (Throwable)` khiến yêu cầu hủy bị lẫn với lỗi nghiệp vụ.
6. Đừng restore flag rồi quay lại một blocking call trong loop không kiểm tra điều kiện thoát.
7. Khi task được hủy, xác định trạng thái công việc đã làm, transaction, checkpoint và những side effect cần đối chiếu.
8. Với `Future.cancel(true)`, `shutdownNow()` hay executor, không giả định tác vụ chắc chắn dừng nếu code không hợp tác.
9. Phân biệt **interrupted**, **cancelled**, **timed out** và **failed** trong kết quả, logging và monitoring.
10. Kiểm thử đường hủy ngay cả khi task đang chờ, đang xử lý, đang cleanup và khi dependency không phản ứng interrupt.

**Quy tắc ghi nhớ:** `InterruptedException` thường là tín hiệu điều phối có chủ đích. Bắt rồi bỏ qua có thể xóa tín hiệu và khiến tác vụ tiếp tục trái yêu cầu. Hãy **truyền tiếp**, hoặc **khôi phục cờ và kết thúc đúng đường**, trừ khi tầng hiện tại thực sự sở hữu và đã xử lý xong toàn bộ giao thức hủy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-008">Phân biệt ngắt luồng, hủy Future và kết thúc cưỡng bức</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt ngắt luồng, hủy `Future` và kết thúc cưỡng bức trong Java

Khi một công việc chạy quá lâu hoặc ứng dụng cần dừng, ba thao tác thường bị đánh đồng là **ngắt luồng** (`Thread.interrupt()`), **hủy tác vụ** (`Future.cancel(...)`) và **kết thúc cưỡng bức** (chẳng hạn `Thread.stop()` hoặc chấm dứt tiến trình). Chúng có **đối tượng tác động, hợp đồng kết quả và rủi ro hoàn toàn khác nhau**. Không có lời gọi nào trong hai cách đầu bảo đảm rằng một đoạn mã tùy ý sẽ ngừng thực thi ngay lập tức.

Bài thuộc phần **Ngắt và hủy** của Java Core Exception, dùng API Java 17+. Sau khi học, bạn cần giải thích được: ai nhận interrupt, tại sao `Future` có thể báo đã hủy trong khi worker còn chạy, cách phân biệt các ngoại lệ khi lấy kết quả, và khi nào phải dùng ranh giới tiến trình thay vì cố ép dừng một luồng.

## 1. Ba cấp độ điều khiển khác nhau

| Cơ chế | Đối tượng chính | Tác động trực tiếp | Có bảo đảm worker dừng ngay? |
|---|---|---|---|
| `thread.interrupt()` | Một `Thread` cụ thể | Đặt trạng thái interrupt hoặc đánh thức thao tác chờ có hỗ trợ interrupt | **Không** |
| `future.cancel(false)` | Trạng thái một `Future` | Yêu cầu hủy Future, không yêu cầu ngắt worker đang chạy | **Không** |
| `future.cancel(true)` | Trạng thái `Future` và khả năng yêu cầu ngắt worker | Nếu hủy được, Future chuyển sang canceled; implementation có thể interrupt worker đang chạy | **Không** |
| `executor.shutdown()` | Vòng đời executor | Ngừng nhận tác vụ mới, tiếp tục xử lý tác vụ đã nhận | **Không** |
| `executor.shutdownNow()` | Executor và những task đang thực thi/đợi | Cố gắng interrupt task đang chạy, ngừng nhận/chạy task đang chờ và trả về danh sách task chưa bắt đầu | **Không** |
| `Thread.stop()` | Một luồng | Cơ chế dừng bất đồng bộ nguy hiểm, đã bị deprecated | **Không nên dùng** |
| Kết thúc cả tiến trình JVM/OS | Tiến trình | Chấm dứt môi trường chạy của nhiều luồng cùng lúc | Mất cả tiến trình; không phải cách hủy một task Java an toàn |

**Phân biệt đối tượng:** `Thread` là luồng thực thi, `Future` là tay nắm theo dõi kết quả/trạng thái một tác vụ, `ExecutorService` là bộ quản lý thực thi nhiều tác vụ. Thực hiện thao tác trên một đối tượng không đồng nghĩa những đối tượng còn lại tự chuyển cùng một trạng thái.

Một sơ đồ ngắn:

```text
Caller giữ Future<T>                           Worker Thread
         │                                           │
         ├─ thread.interrupt() ──► cờ interrupt ────► │
         │                                      worker tự xử lý
         │
         ├─ future.cancel(false) ─► Future=canceled   │ có thể tiếp tục
         │
         └─ future.cancel(true) ──► Future=canceled   │
                                   + thử interrupt ─►│ có thể dừng hợp tác

Đã canceled ──► future.get() ném CancellationException
Worker lỗi mà Future chưa bị canceled ──► get() ném ExecutionException
```

Sơ đồ mô tả cách `FutureTask` được dùng phổ biến bởi `ExecutorService.submit(...)`. **Không phải mọi triển khai `Future` dùng interrupt để điều khiển tính toán.** Ví dụ, `CompletableFuture.cancel(boolean)` bỏ qua tham số `mayInterruptIfRunning` theo hợp đồng của chính nó; hủy `CompletableFuture` không mặc nhiên ngắt thread chạy công việc.

## 2. Ngắt luồng: `Thread.interrupt()` là yêu cầu hợp tác

Mỗi `Thread` có một trạng thái interrupt. Khi luồng A gọi `worker.interrupt()`, Java báo hiệu cho `worker` rằng có yêu cầu ngắt:

- Nếu worker đang tính toán và không gọi thao tác chờ có thể ngắt, cờ interrupt được đặt; worker thường tiếp tục chạy cho tới khi tự kiểm tra và phản ứng.
- Nếu worker đang chờ trong `Thread.sleep`, `Object.wait`, `Thread.join`, `BlockingQueue.take`, `CountDownLatch.await` hoặc nhiều thao tác chờ interruptible khác, lời gọi chờ có thể kết thúc sớm bằng `InterruptedException`.
- Những thao tác chờ nêu trên thường **xóa cờ interrupt khi ném `InterruptedException`**. Mã bắt ngoại lệ cần quyết định truyền tiếp tín hiệu hoặc khôi phục cờ và kết thúc công việc.
- `Thread.currentThread().isInterrupted()` đọc cờ của luồng hiện tại mà không xóa; phương thức tĩnh `Thread.interrupted()` đọc **và xóa cờ của luồng hiện tại**.

`interrupt()` **không tự tạo `CancellationException`**, không biến task đã submit thành canceled, và không có nghĩa caller cũng bị interrupt. Nếu worker chủ động bắt `InterruptedException`, dọn tài nguyên và trả kết quả bình thường, `Future` có thể hoàn thành **thành công**.

### Ví dụ Java 17 độc lập: interrupt một Thread, không có Future

Tạo tệp `ThreadInterruptDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThreadInterruptDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        AtomicBoolean observed = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            ready.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                observed.set(true);
                Thread.currentThread().interrupt();
            }
        }, "worker");

        worker.start();
        ready.await();
        worker.interrupt();
        worker.join();

        System.out.println("received interrupt = " + observed.get());
        System.out.println("worker alive = " + worker.isAlive());
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac ThreadInterruptDemo.java
java ThreadInterruptDemo
```

Đầu ra:

```text
received interrupt = true
worker alive = false
```

`ready` bảo đảm worker đã đi đến đoạn xử lý trước khi main phát tín hiệu; dù worker chưa kịp gọi `await()`, trạng thái interrupt vẫn khiến lần chờ ấy ném `InterruptedException`. Worker tự thoát khỏi `run()` nên kết thúc, chứ không phải `interrupt()` cưỡng ép dừng. `join()` đợi worker kết thúc trước khi in kết quả; `AtomicBoolean` ghi lại việc worker nhận tín hiệu.

Trong `catch`, ta khôi phục cờ với `Thread.currentThread().interrupt()` vì `await()` đã xóa cờ khi ném ngoại lệ. Nếu phương thức cho phép `throws InterruptedException`, truyền tiếp ngoại lệ thường minh bạch hơn; khi bắt ở ranh giới vòng đời, có thể khôi phục cờ rồi thoát.

### Công việc CPU phải hợp tác kiểm tra cờ

Đây là **phương thức minh họa** có thể đặt bên trong một class, không phải tệp chạy độc lập:

```java
static long calculateUntilStopped() {
    long count = 0;
    while (!Thread.currentThread().isInterrupted()) {
        count++;
    }
    return count;
}
```

Nếu thay điều kiện bằng `while (true)` và không có thao tác chờ interruptible, gọi `interrupt()` sẽ không tự ném `InterruptedException` ở vòng lặp CPU đó. Cũng đừng dùng `Thread.interrupted()` như phép kiểm tra vô hại: nó xóa cờ, có thể làm mất tín hiệu cho tầng khác.

## 3. `Future.cancel(false)`: hủy kết quả quan sát được, không ngắt worker

`Future.cancel(boolean mayInterruptIfRunning)` thử hủy Future:

```java
boolean accepted = future.cancel(false);
```

Đây là **mảnh mã minh họa** có biến `future` kiểu `Future<?>`. Khi `cancel(false)` thành công với `FutureTask` đang chạy, Future chuyển sang trạng thái **canceled**, nhưng việc thực thi worker **không bị yêu cầu interrupt**; worker có thể tiếp tục và thậm chí hoàn thành tính toán. Kết quả cuối cùng không còn nhận được qua `get()` của Future đã hủy.

Sau khi Future đã hủy thành công:

- `future.isCancelled()` trả `true`.
- `future.isDone()` trả `true` vì Future có trạng thái kết thúc theo hợp đồng của nó.
- `future.get()` ném `CancellationException` trực tiếp.
- `isDone() == true` **không chứng minh** worker đã thoát và dọn dẹp xong.

Nếu task đã hoàn thành hoặc đã bị hủy trước đó, lời gọi `cancel(...)` tiếp theo thường trả `false` theo hợp đồng `Future`. Kết quả trả về phụ thuộc trạng thái tại thời điểm race, vì vậy **phải dùng giá trị thực trả về** thay vì mặc định cancel luôn thành công.

Về vận hành, `cancel(false)` phù hợp khi không còn quan tâm kết quả, muốn tránh tác vụ chưa bắt đầu chạy tiếp theo khả năng của implementation, hoặc không muốn yêu cầu interrupt một worker đang làm công việc khó hủy an toàn. Nó **không phải** một cơ chế dừng worker đang chạy.

## 4. `Future.cancel(true)`: hủy Future và cho phép yêu cầu interrupt

```java
boolean accepted = future.cancel(true);
```

Với `FutureTask` thông thường được trả bởi `ExecutorService.submit(...)`, nếu việc hủy thành công và tác vụ đang chạy, implementation sẽ cố gắng gửi interrupt tới thread chạy nó. Tuy nhiên:

1. Future có thể chuyển sang canceled **trước khi** worker phản ứng hoặc thoát; caller thấy `CancellationException` từ `get()` ngay cả khi worker vẫn đang dọn dẹp.
2. Worker có thể đang thực hiện mã không hỗ trợ interrupt hoặc cố tình nuốt interrupt; nó vẫn có thể chạy.
3. Nếu worker đã hoàn tất trước yêu cầu hủy, `cancel(true)` có thể trả `false` và Future giữ kết quả đã hoàn tất.
4. Một API khác triển khai `Future` có thể có quy tắc hủy riêng. Đặc biệt `CompletableFuture.cancel(true)` không dùng tham số `true` để interrupt tác vụ nền.

**`cancel(true)` có nghĩa là “cho phép yêu cầu interrupt nếu phù hợp”, không phải “kill thread ngay”.** Worker cần code xử lý cooperative cancellation để dừng an toàn.

### Ví dụ Java 17 độc lập: `cancel(false)` và `cancel(true)`

Tạo tệp `FutureCancelModesDemo.java`:

```java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class FutureCancelModesDemo {
    public static void main(String[] args) throws Exception {
        withoutInterrupt();
        withInterrupt();
    }

    private static void withoutInterrupt() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);

        try {
            Future<Integer> future = executor.submit(() -> {
                started.countDown();
                try {
                    release.await();
                    return 42;
                } finally {
                    finished.countDown();
                }
            });

            started.await();
            boolean accepted = future.cancel(false);

            System.out.println("cancel(false) = " + accepted);
            System.out.println("cancelled = " + future.isCancelled());
            System.out.println("worker still waiting = "
                    + (finished.getCount() == 1));

            try {
                future.get();
            } catch (CancellationException e) {
                System.out.println("get = CancellationException");
            }

            release.countDown();
            finished.await();
            System.out.println("worker finished after release = true");
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    private static void withInterrupt() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean sawInterrupt = new AtomicBoolean(false);

        try {
            Future<String> future = executor.submit(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                    return "normal";
                } catch (InterruptedException e) {
                    sawInterrupt.set(true);
                    Thread.currentThread().interrupt();
                    return "worker handled interrupt";
                } finally {
                    finished.countDown();
                }
            });

            started.await();
            boolean accepted = future.cancel(true);
            finished.await();

            System.out.println("cancel(true) = " + accepted);
            System.out.println("worker saw interrupt = "
                    + sawInterrupt.get());
            try {
                future.get();
            } catch (CancellationException e) {
                System.out.println("get = CancellationException");
            }
        } finally {
            executor.shutdown();
        }
    }
}
```

Chạy:

```bash
javac FutureCancelModesDemo.java
java FutureCancelModesDemo
```

Đầu ra:

```text
cancel(false) = true
cancelled = true
worker still waiting = true
get = CancellationException
worker finished after release = true
cancel(true) = true
worker saw interrupt = true
get = CancellationException
```

Trong nhánh thứ nhất, `started.await()` bảo đảm task đã chạy; `release` còn đóng, nên worker chưa hoàn tất khi `cancel(false)` được gọi. Future báo canceled và `get()` ném `CancellationException`, nhưng worker tiếp tục chờ đến khi main mở `release`.

Trong nhánh thứ hai, worker chờ một latch không bao giờ được mở. `cancel(true)` yêu cầu interrupt, `await()` ném `InterruptedException`, worker ghi nhận tín hiệu, khôi phục cờ rồi thoát. Dù worker tự trả chuỗi `"worker handled interrupt"`, bên nhận **không lấy được chuỗi đó** vì Future đã bị hủy. `finished.await()` chỉ dùng để đợi phần `finally` của worker chạy xong trước khi đọc `sawInterrupt`; đối với thao tác thật, muốn xác nhận toàn bộ worker đã kết thúc có thể cần cơ chế lifecycle như `awaitTermination(...)`.

**Đừng suy từ kết quả của ví dụ thành cam kết cho mọi tác vụ:** ví dụ chủ động dùng `CountDownLatch`, mã worker hợp tác với interrupt và executor chỉ có một worker. Một tác vụ chặn I/O không interruptible hoặc bị kẹt trong thư viện bên ngoài có thể không dừng khi gọi `cancel(true)`.

## 5. Ngắt worker trực tiếp không nhất thiết hủy `Future`

Nếu worker nhận interrupt qua `worker.interrupt()` hoặc do executor gửi interrupt mà **không có lời gọi `Future.cancel(...)`**, Future có thể kết thúc theo hai đường:

- Worker **để `InterruptedException` thoát khỏi `Callable`**: Future hoàn thành **thất bại**; `future.get()` ném `ExecutionException`, `getCause()` là `InterruptedException`.
- Worker **bắt `InterruptedException`, dọn dẹp và return giá trị**: Future có thể hoàn thành **thành công** và `get()` trả giá trị đó.

Hai tình huống khác với một Future đã hủy, khi `get()` ném `CancellationException` dù worker còn xử lý dở.

### Ví dụ độc lập: interrupt worker tạo `ExecutionException`, không phải cancellation

Tạo tệp `WorkerInterruptedFailureDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class WorkerInterruptedFailureDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);

        try {
            Future<String> future = executor.submit(() -> {
                started.countDown();
                new CountDownLatch(1).await();
                return "not reached";
            });

            started.await();
            executor.shutdownNow();

            try {
                future.get();
            } catch (ExecutionException e) {
                System.out.println("cause = "
                        + e.getCause().getClass().getSimpleName());
            }

            System.out.println("cancelled = " + future.isCancelled());
        } finally {
            executor.shutdownNow();
        }
    }
}
```

Chạy:

```bash
javac WorkerInterruptedFailureDemo.java
java WorkerInterruptedFailureDemo
```

Đầu ra:

```text
cause = InterruptedException
cancelled = false
```

`shutdownNow()` yêu cầu interrupt worker đang thực thi. Khi bị ngắt, lời gọi `await()` ném `InterruptedException` và `Callable` không bắt ngoại lệ đó, vì thế `FutureTask` ghi nhận **thất bại của task**. Caller đọc bằng `get()` thấy `ExecutionException`, không phải `CancellationException`, vì không có lời gọi `future.cancel(...)`.

Điểm quan trọng: `executor.shutdownNow()` **không đồng nghĩa** với `future.cancel(true)`. Nó cố ngắt các task đang chạy, nhưng có thể trả về các task chưa từng bắt đầu; Future tương ứng với task nằm trong danh sách chờ có thể vẫn chưa được tự động chuyển sang canceled. Ứng dụng phải quản lý lifecycle và Future của các tác vụ đó theo đúng hợp đồng executor cụ thể.

## 6. Người gọi `Future.get()` bị ngắt là một chuyện khác

Hai luồng có thể tham gia cùng một Future:

```text
Caller/main                               Executor worker
    │                                          │
    ├─ future.get() ───── chờ ───────────────► chạy Callable
    │
    └─ caller bị interrupt
             │
             ▼
      get() ném InterruptedException
             │
      Future/worker có thể vẫn chạy
```

Nếu **caller** bị interrupt trong khi đợi `get()`, `get()` có thể ném trực tiếp **`InterruptedException`**; không có suy luận tự động rằng worker cũng bị ngắt hay Future đã bị cancel. Ngược lại, khi **worker** ném `InterruptedException` thoát khỏi `Callable` mà Future chưa canceled, bên gọi `get()` thấy **`ExecutionException`** chứa `InterruptedException` ở `getCause()`.

| Điều xảy ra | Bên gọi `Future.get()` thường thấy |
|---|---|
| Callable trả về bình thường | Giá trị `T` |
| Callable ném ngoại lệ, Future chưa canceled | `ExecutionException` có cause là lỗi worker |
| Chính caller đang chờ bị interrupt | `InterruptedException` trực tiếp |
| Future bị cancel thành công | `CancellationException` trực tiếp |
| `get(timeout, unit)` hết hạn trong khi vẫn chưa có kết quả | `TimeoutException` |

`InterruptedException`, `ExecutionException` và `TimeoutException` là checked exception; `CancellationException` là unchecked exception. Timeout của `get(timeout, unit)` **không tự hủy** Future; nếu cần dừng, caller phải chủ động quyết định có gọi `cancel(...)` hay không.

Mẫu xử lý (mảnh mã trong một phương thức có `future` hợp lệ, có kiểu trả về phù hợp):

```java
try {
    return future.get(2, java.util.concurrent.TimeUnit.SECONDS);
} catch (java.util.concurrent.TimeoutException e) {
    future.cancel(true);           // Chính sách chủ động yêu cầu hủy.
    throw e;
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    future.cancel(true);           // Chỉ khi yêu cầu ngắt caller cũng phải hủy task.
    throw e;
}
```

Nếu phương thức chỉ có nhiệm vụ **đợi** và tác vụ phải được phép tiếp tục dù caller ngừng đợi, hãy bỏ `future.cancel(true)` khỏi nhánh `InterruptedException`. Không có chính sách duy nhất phù hợp mọi trường hợp.

## 7. Vì sao `Thread.stop()` không phải giải pháp?

Trước đây Java có `Thread.stop()` để dừng một thread bất đồng bộ. API này đã bị **deprecated** vì nguy hiểm. Nó có thể làm thread dừng ở vị trí không thể dự đoán, giải phóng các monitor khi trạng thái đối tượng đang dang dở, phá vỡ invariant dữ liệu dùng chung và khiến những thread khác nhìn thấy trạng thái không nhất quán.

Ví dụ tình huống cần tránh:

```java
synchronized (account) {
    account.debit(100);
    // Nếu bị ép dừng tại đây, chưa chắc thao tác cân đối liên quan đã xong.
    ledger.recordDebit(100);
}
```

Đây là **mảnh mã minh họa**, các đối tượng `account` và `ledger` là giả định. Một cơ chế cưỡng bức dừng giữa hai bước có thể để hệ thống ở trạng thái dở dang; `synchronized` chỉ bảo đảm khóa được quản lý theo quy tắc JVM, không bảo đảm chuỗi cập nhật nghiệp vụ trở thành transaction nguyên tử.

Các cách liên quan cũng cần hiểu đúng:

- `Thread.suspend()`/`resume()` là API lỗi thời nguy hiểm, có thể tạo deadlock; không dùng để quản lý vòng đời.
- `Thread.stop()` không nên dùng để xử lý task treo; Java không cung cấp API an toàn tổng quát để giết **một luồng bất kỳ** và vẫn bảo toàn mọi invariant/tài nguyên.
- `System.exit(...)` chấm dứt **cả JVM**, không phải riêng Future hoặc worker. Dữ liệu chưa ghi, request đang xử lý và nhiều tài nguyên khác cùng chịu ảnh hưởng.
- Kết thúc tiến trình bằng công cụ hệ điều hành hoặc `Process.destroyForcibly()` tác động ở **ranh giới process**, không có cùng ý nghĩa với `Thread.interrupt()`. Nếu tác vụ không tin cậy cần có khả năng dừng cưỡng bức, hãy cân nhắc chạy **trong tiến trình riêng**, giao tiếp qua IPC và quy định rõ timeout, dữ liệu có thể mất, dọn tài nguyên, retry và giám sát.

**Lựa chọn kiến trúc:** với công việc Java trong cùng JVM, ưu tiên điểm dừng hợp tác. Với tác vụ không thể tin cậy sẽ phản ứng với interrupt (ví dụ đoạn mã third-party có nguy cơ treo), cô lập sang process/container riêng có thể tạo ranh giới cắt bỏ mạnh hơn, nhưng việc kết thúc cưỡng bức vẫn có hậu quả nghiệp vụ và tài nguyên.

## 8. `ExecutorService`: dừng nhận việc, yêu cầu dừng và chờ xác nhận

Ba thao tác phổ biến có ý nghĩa khác nhau:

1. `shutdown()` báo executor ngừng nhận task mới, còn task đã nhận được phép tiếp tục; **không tự đợi** chúng hoàn tất.
2. `shutdownNow()` cố gắng interrupt các tác vụ đang chạy, ngừng xử lý task chờ, trả về danh sách các task chưa bắt đầu; đây là yêu cầu **best effort**.
3. `awaitTermination(timeout, unit)` đợi trạng thái executor đã kết thúc trong khoảng thời gian giới hạn và trả `boolean`; luồng **đang đợi** có thể nhận `InterruptedException`.

Một mẫu shutdown có thời hạn (đoạn mã đặt trong phương thức, biến `executor` đã tồn tại):

```java
executor.shutdown();

try {
    if (!executor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
        executor.shutdownNow();

        if (!executor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
            System.err.println("Executor chưa dừng sau khi yêu cầu ngắt");
        }
    }
} catch (InterruptedException e) {
    executor.shutdownNow();
    Thread.currentThread().interrupt();
}
```

Hai khoảng 3 giây **chỉ là ví dụ**: hãy chọn theo thời gian đáp ứng, độ nguy hiểm của việc ngắt, khả năng rollback và các thỏa thuận về dữ liệu. Đoạn mã không chứng minh mọi worker đã dừng nếu `awaitTermination` trả `false`. Khi có dữ liệu cần bảo toàn, ứng dụng phải có kế hoạch dọn dẹp, checkpoint hoặc xử lý bù.

Nếu một worker đang giữ lock hoặc kết nối, cần `try/finally` ở chính worker để dọn tài nguyên. Không trông chờ một lời gọi `shutdownNow()` từ caller tự làm thay trách nhiệm đó.

## 9. Chọn cách ngắt hay hủy theo tình huống

| Tình huống | Hướng xử lý phù hợp | Điều cần xác nhận |
|---|---|---|
| Thread tự quản đang chờ queue/latch | `thread.interrupt()`, worker bắt/truyền tín hiệu và thoát | `join()` có timeout hoặc cơ chế hoàn tất |
| Future chưa bắt đầu, không còn cần kết quả | `future.cancel(false)` hoặc chính sách hủy task đợi | `cancel(...)` có thành công; không đọc kết quả từ Future đã hủy |
| Callable đang chạy, có hỗ trợ interrupt | `future.cancel(true)` nếu hợp đồng nghiệp vụ cho phép | Tài nguyên/side effect đã dọn, worker đã ngừng nếu cần |
| Caller hết hạn chờ kết quả nhưng worker vẫn hữu ích | `get(timeout, unit)`, trả trạng thái timeout, không bắt buộc cancel | Tránh tự suy timeout nghĩa là task đã dừng |
| Caller bị interrupt khi gọi `get()` | Propagate hoặc khôi phục cờ; quyết định riêng có cancel task hay không | Caller và worker là hai luồng độc lập |
| Dừng cả executor có thời hạn | `shutdown()`, `awaitTermination`, sau đó cân nhắc `shutdownNow()` | Task chưa bắt đầu, task còn treo, tài nguyên |
| Code không phản ứng interrupt, nguy cơ treo không kiểm soát | Cô lập sang process riêng với timeout và giám sát | Hệ quả khi giết process, retry/idempotency, dữ liệu dang dở |

Nếu task thay đổi dữ liệu bên ngoài (ghi DB, gửi HTTP, chuyển tiền...), hủy Future **không hoàn tác** mọi tác dụng phụ đã xảy ra. Cần nghĩ đến tính idempotent, transaction, timeout phía dịch vụ, compensation và việc tránh submit trùng khi retry.

### Điểm dừng hợp tác trong worker

Một vòng lặp consumer thường có dạng (mẫu minh họa, `process` và `cleanup` do ứng dụng định nghĩa):

```java
static void runConsumer(java.util.concurrent.BlockingQueue<String> queue) {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            String item = queue.take();
            process(item);
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        cleanup();
    }
}
```

Nếu `process` cũng có thao tác blocking hoặc thay đổi trạng thái chung, nó phải có hợp đồng lỗi/hủy phù hợp. Bắt `InterruptedException` rồi khôi phục cờ nhưng **tiếp tục một vòng `while(true)`** thường dẫn tới lặp nóng: lần chờ interruptible tiếp theo có thể ném ngoại lệ ngay lập tức.

Đôi khi task phải hoàn tất một phần **không thể ngắt an toàn** trước khi kết thúc. Thiết kế cần nói rõ ranh giới đó, dùng vùng thao tác ngắn, rollback khi phù hợp và giữ tín hiệu ngắt cho tầng có trách nhiệm. Không tùy tiện nuốt interrupt để che lỗi vòng đời.

## 10. Những ngộ nhận cần tránh

### “`isDone()` nghĩa là worker đã chạy xong”

Sai. Một Future bị cancel có `isDone() == true`, `isCancelled() == true` mặc dù worker có thể còn chạy hoặc đang dọn dẹp. `isDone()` chỉ là trạng thái terminal của **Future**, không phải lời xác nhận toàn bộ worker đã thoát.

### “`cancel(true)` luôn ném `InterruptedException` trong caller”

Sai. `cancel(true)` trả `boolean` cho caller. Khi Future đã bị hủy, `get()` ném `CancellationException`. `InterruptedException` có thể xuất hiện **trong worker** tại thao tác chờ được ngắt, hoặc **trong caller** nếu chính caller bị interrupt khi chờ một API interruptible.

### “`cancel(false)` chỉ hủy task chưa bắt đầu”

Chưa đầy đủ. Với `FutureTask` đang chạy, `cancel(false)` vẫn có thể hủy **Future** mà không interrupt worker; worker còn làm tiếp nhưng kết quả không còn lấy qua Future ấy. Chi tiết có thể khác giữa các implementation, nên đọc hợp đồng cụ thể.

### “Worker ném `InterruptedException` thì Future chắc chắn canceled”

Sai. Nếu Callable để ngoại lệ thoát và không có thao tác cancel thắng race, Future có thể báo **thất bại** bằng `ExecutionException` có cause là `InterruptedException`. Nếu worker bắt ngoại lệ và return, Future có thể **thành công**.

### “`shutdownNow()` hủy hết Future”

Sai. `shutdownNow()` là nỗ lực dừng executor; nó trả task chưa bắt đầu và không mặc nhiên tương đương gọi `cancel(true)` trên từng Future. Quản lý những Future còn giữ bên ngoài là trách nhiệm tầng điều phối.

### “Có interrupt là có rollback”

Sai. Interrupt tác động lên điều khiển luồng chứ không phải cơ chế transaction. Một task đã ghi dữ liệu, gửi thông báo hay tạo tệp có thể đã tạo side effect trước khi tín hiệu đến.

### “Cưỡng bức dừng một Thread giúp hệ thống an toàn hơn”

Sai. Dừng tại vị trí tùy ý có thể phá invariant và để tài nguyên dang dở. Cách an toàn hơn là dừng hợp tác; khi không kiểm soát được mã, thay đổi ranh giới thực thi (ví dụ process riêng) và chấp nhận bài toán recovery của process.

## 11. Checklist xử lý ngắt và hủy trong ứng dụng thật

Trước khi quyết định hủy một công việc, hãy trả lời:

1. **Ai điều khiển?** Caller, worker, Future hay executor? Luồng nào thực sự nhận interrupt?
2. **Muốn dừng chờ hay dừng công việc?** Timeout của caller có nghĩa task hết giá trị không?
3. **Task có hợp tác không?** Nó có điểm `isInterrupted()`, blocking API interruptible hoặc cờ dừng riêng không?
4. **Kết quả gì được báo?** Future returned value, `ExecutionException`, `CancellationException`, hay caller thấy `InterruptedException`?
5. **Đã dọn xong chưa?** Có `finally`, `join`, `awaitTermination`, latch hoặc một tín hiệu hoàn tất đáng tin không?
6. **Side effect nào đã xảy ra?** Có cần rollback, idempotency, compensation hoặc chống retry trùng không?
7. **Nếu task treo vĩnh viễn?** Có deadline và ranh giới process thích hợp không, thay vì dựa vào `Thread.stop()`?

**Kết luận kỹ thuật:** `Thread.interrupt()` là **tín hiệu hợp tác gửi đến luồng**; `Future.cancel(false/true)` là **yêu cầu đổi trạng thái Future**, trong đó `true` có thể cho phép ngắt worker tùy implementation; `shutdownNow()` chỉ là nỗ lực dừng executor; kết thúc cưỡng bức cần ranh giới riêng và có rủi ro. Luôn phân biệt **tín hiệu yêu cầu dừng**, **trạng thái kết quả của Future** và **bằng chứng công việc đã thật sự kết thúc**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-009">Giới hạn của timeout và cancellation đối với tác vụ bên dưới</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của timeout và cancellation đối với tác vụ bên dưới

## 1. Vấn đề: hết thời gian chờ không đồng nghĩa công việc đã dừng

Một chương trình Java thường gửi công việc bất đồng bộ tới `ExecutorService` hoặc `CompletableFuture` rồi giới hạn thời gian chờ kết quả. Nếu quá hạn, caller có thể nhận `TimeoutException` hoặc yêu cầu hủy bằng `Future.cancel(true)`. Tuy nhiên hai hành động này nói về **khả năng quan sát và điều phối công việc**, không phải một cam kết mọi phép tính, I/O hoặc side effect bên dưới đều đã dừng.

Cần tách rõ bốn sự kiện:

1. **Caller ngừng chờ**: luồng gọi thoát khỏi `get(timeout, unit)` vì chưa có kết quả đúng hạn.
2. **Handle được đánh dấu hủy hoặc hoàn thành lỗi**: trạng thái `Future`/`CompletableFuture` có thể chuyển thành terminal.
3. **Worker thực sự ngừng chạy**: thread thực thi đã kết thúc, thường cần tác vụ hợp tác với interruption, deadline hoặc một cơ chế hủy riêng.
4. **Tác động bên ngoài đã dừng/được phục hồi**: cơ sở dữ liệu, file, HTTP service hoặc thanh toán không còn thay đổi; mức bảo đảm này cần cơ chế riêng.

Những sự kiện trên **không thể hoán đổi ý nghĩa**. Ví dụ: `future.get(100, MILLISECONDS)` ném `TimeoutException`, nhưng worker vẫn gửi request và nhận phản hồi sau đó. Hoặc `future.cancel(true)` trả `true`, trong khi worker bỏ qua interrupt và tiếp tục ghi file. Hoặc `CompletableFuture.orTimeout` đã làm stage hoàn thành bằng `TimeoutException`, nhưng supplier đang chạy trong executor vẫn hoàn tất tác động phụ.

Sau bài này, bạn sẽ:
- Hiểu chính xác hợp đồng `Future.get(timeout)`, `Future.cancel`, `CompletableFuture.orTimeout` và `completeOnTimeout` trong Java 17+.
- Phân biệt timeout của caller, timeout của API I/O, interruption, cancellation và trạng thái kết quả phía remote.
- Thiết kế task hỗ trợ hủy hợp tác và dọn tài nguyên đúng quyền sở hữu.
- Đánh giá race condition, kết quả chưa xác định và nguy cơ side effect lặp lại khi retry.
- Viết test xác nhận **worker dừng thật** và trạng thái hậu xử lý, thay vì chỉ xác nhận Future có cờ `isCancelled`.

## 2. Mô hình thời gian: caller và worker không dùng chung một điểm dừng

Giả sử caller cần kết quả trong 200 ms, còn worker đang tải dữ liệu:

~~~text
Thời điểm       Caller                              Worker
t0              submit -> Future                    được lên lịch
t1              get(200 ms)                         đang gửi HTTP
t2              TimeoutException                    vẫn chờ máy chủ
t3              caller trả "quá hạn"                 máy chủ đã xử lý xong
t4              caller có thể retry                  worker nhận response
t5              ...                                  cập nhật trạng thái/file
~~~

Dòng thời gian chỉ minh họa, không phải lời hứa về thời điểm thực tế. Lệnh `get(timeout)` giới hạn **thời gian caller đợi lời gọi đó**. Nó không cài một đồng hồ bắt buộc dừng CPU, Java thread, socket, database query hay remote service.

Cùng một thao tác có thể có nhiều deadline khác nhau:
- **Request timeout** tại tầng HTTP đầu vào.
- **Timeout chờ kết quả** trong `Future.get(timeout, unit)`.
- **Connect timeout**, **read timeout** hoặc **request timeout** trong HTTP/JDBC client.
- **Deadline của toàn tác vụ** do ứng dụng truyền xuống các bước con.
- **Timeout của hệ thống xa** hoặc transaction tại database.
- **Thời hạn retry** của một policy bên ngoài.

Muốn người dùng nhận một kết quả đúng hạn **và** hạn chế tác vụ chạy quá lâu, phải phối hợp các cơ chế tương ứng. Chỉ bọc `get` bằng timeout là chưa đủ.

## 3. `Future.get(timeout, unit)` chỉ giới hạn thời gian chờ

Ví dụ:

~~~java
Future<String> future = executor.submit(this::loadData);
String value = future.get(500, TimeUnit.MILLISECONDS);
~~~

Đây là fragment, giả định `executor`, `loadData` và các import đã tồn tại; phương thức chứa nó cần xử lý `InterruptedException`, `ExecutionException`, `TimeoutException`. Nếu quá 500 ms mà Future chưa có kết quả, `get` có thể ném `TimeoutException`. **Future có thể vẫn đang chạy và chưa bị hủy**.

### Các kết cục của `get(timeout, unit)`

| Kết cục quan sát | Ý nghĩa | Có chứng minh worker đã dừng? |
| --- | --- | --- |
| Trả `T` | Future hoàn thành bình thường, có kết quả | Tác vụ mà Future đại diện đã hoàn thành; không bảo đảm side effect bên ngoài đã được đảo ngược |
| `ExecutionException` | Task hoàn thành ngoại lệ; nguyên nhân ở `getCause()` | Task được Future quản lý đã hoàn tất bằng lỗi; công việc phụ nó tạo ra có thể còn chạy |
| `TimeoutException` | Không nhận được kết quả trong khoảng chờ | **Không** |
| `InterruptedException` | Thread đang chờ bị interrupt | **Không** |
| `CancellationException` | Future đã hủy khi caller lấy kết quả | **Không nhất thiết**; task bên dưới có thể chưa thực sự dừng |

Gọi `future.isDone()` sau timeout cũng cần hiểu đúng: `true` bao gồm thành công, thất bại và hủy. Không suy ra thành công chỉ từ cờ đó. `isCancelled()` chỉ nói Future ở trạng thái hủy theo hợp đồng, không kiểm chứng các tài nguyên bên ngoài.

**Không được viết logic** “`get` timeout nên chắc chắn chưa có đơn hàng” hoặc “timeout nên retry ngay POST tạo đơn mới” khi công việc cũ có thể vẫn đang xử lý.

## 4. `Future.cancel(boolean)` là một yêu cầu hủy, không phải kill an toàn

`Future.cancel(mayInterruptIfRunning)` thử chuyển công việc sang trạng thái hủy. Nó có thể trả `false` khi không thể hủy, chẳng hạn task đã hoàn thành; `true` nói yêu cầu hủy Future thành công theo hợp đồng. Sự thành công này không có nghĩa thread đang chạy đã ngừng tức thì.

### 4.1. `cancel(false)` so với `cancel(true)`

| Lời gọi | Ý nghĩa quan trọng | Điều không bảo đảm |
| --- | --- | --- |
| `cancel(false)` | Cố hủy nhưng **không yêu cầu interrupt** nếu task đã chạy | Không bắt worker đang chạy dừng ngay |
| `cancel(true)` | Cố hủy và cho phép yêu cầu interrupt thread đang chạy | Không ép tác vụ không hợp tác ngừng; không rollback side effect |
| `get()` sau Future đã hủy | Ném `CancellationException` | Không nói worker/remote có còn đang chạy không |
| `isCancelled()` | Kiểm tra trạng thái Future đã hủy | Không xác nhận I/O hay hiệu ứng bên ngoài đã dừng |

`cancel(true)` thường tác động qua **interruption** nếu triển khai Future/Executor hỗ trợ điều đó. Nhưng interruption của Java là cơ chế hợp tác: nó đặt cờ interrupt hoặc khiến một số API blocking ném `InterruptedException`. Nó không phải một lệnh cưỡng chế dừng mọi loại tác vụ.

Trong một số tác vụ đang chạy, kết quả Future có thể được đánh dấu hủy trong khi worker vẫn tiếp tục một đoạn, thậm chí chạy tới cuối vì không kiểm tra interrupt. Sau khi Future bị hủy, `get()` không còn trả giá trị tính toán bình thường của worker, cho dù worker có làm xong sau đó.

### 4.2. Vì sao `Thread.stop()` không phải lời giải?

Không nên dùng `Thread.stop()` để cố cưỡng chế hủy vì API này bị deprecated vì không an toàn: nó có thể làm thread dừng tại thời điểm bất kỳ, để lại đối tượng chia sẻ/invariant trong trạng thái không nhất quán và gây hậu quả khó kiểm soát. Ngắt hợp tác, chính sách timeout của I/O và thiết kế task có điểm dừng an toàn mới là hướng cần học.

## 5. Interruption chỉ có tác dụng khi code bên dưới hợp tác

Trong Java, `Thread.interrupt()` thể hiện **một yêu cầu**. Tác vụ phải biết quan sát và phản hồi. Hai loại phản hồi phổ biến:

1. Với API interruptible blocking như `Thread.sleep`, `Object.wait`, `Thread.join` hoặc các thao tác `BlockingQueue` phù hợp, thread có thể bị đánh thức bằng `InterruptedException`.
2. Với vòng lặp tính toán hoặc thao tác không tự ném `InterruptedException`, code cần kiểm tra `isInterrupted()` hoặc một cancellation token/flag có quan hệ bộ nhớ đúng.

Mẫu task hợp tác:

~~~java
Runnable worker = () -> {
    while (!Thread.currentThread().isInterrupted()) {
        try {
            processOneBatch();
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
    }
};
~~~

Đây là fragment minh họa; `processOneBatch()` chưa định nghĩa, và cần có chính sách cho exception khác của chính tác vụ. Nếu `processOneBatch()` mất hàng phút và không hỗ trợ hủy, vòng lặp chỉ kiểm tra interrupt **sau khi hàm đó trở về**. Do vậy phải đặt điểm kiểm tra ở các vùng xử lý dài hoặc truyền deadline/cancellation vào API con có hỗ trợ.

### 5.1. Cờ interrupt và `InterruptedException`

Các thao tác blocking ném `InterruptedException` thường xóa interrupt status trong quá trình ném. Nếu code bắt exception này mà không thể khai báo `throws InterruptedException`, mẫu thường đúng là **khôi phục cờ** rồi kết thúc tác vụ:

~~~java
try {
    queue.put(item);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
~~~

Đây là fragment minh họa nằm trong phương thức trả `void`, có `queue` và `item` được khai báo. Nếu nuốt exception rồi tiếp tục, hệ thống shutdown/cancel có thể không dừng được công việc. Nếu chữ ký phương thức cho phép `throws InterruptedException`, có thể truyền tiếp lỗi thay vì tự xử lý.

Cần phân biệt:
- `isInterrupted()` kiểm tra trạng thái của thread, **không xóa cờ**.
- `Thread.interrupted()` (static) kiểm tra trạng thái interrupt của **thread hiện tại** và **xóa cờ**.
- `Thread.currentThread().interrupt()` đặt lại cờ cho thread hiện tại.

Không gọi `Thread.interrupted()` trong điều kiện lặp một cách vô tình rồi đánh mất yêu cầu hủy nếu logic sau đó còn cần biết.

### 5.2. Những thao tác không nhất thiết ngắt ngay

Nhiều tác vụ không phản hồi hoặc chỉ phản hồi gián tiếp với interruption:
- Vòng lặp CPU-bound không kiểm tra cờ.
- Một đoạn native code, thư viện bên ngoài hoặc API blocking không công bố hành vi interrupt.
- Chờ giành intrinsic monitor của khối `synchronized` theo cơ chế truyền thống: một yêu cầu interrupt không tự phá việc chờ monitor.
- Gọi I/O có timeout/semantics riêng: không suy luận mọi socket, JDBC call, file operation đều lập tức ném `InterruptedException`.
- Remote HTTP request đã tới server: ngắt client không tự khiến server đảo thao tác.
- Đoạn code bắt `InterruptedException` nhưng bỏ qua rồi tiếp tục.

Một số API có cách phản hồi interrupt khác nhau, ví dụ có thể ném một unchecked exception/đóng channel/cập nhật trạng thái; hãy dựa vào hợp đồng của **API cụ thể**. Không hứa rằng mọi thư viện I/O đều phản hồi như `Thread.sleep`.

### 5.3. Hủy hợp tác và cleanup

Khi worker nhận yêu cầu hủy, nó thường cần dọn **tài nguyên do nó sở hữu**: đóng stream, giải phóng lock, xóa file tạm, rollback transaction chưa commit. Dùng `try/finally` và `try-with-resources` ở phạm vi phù hợp.

Tuy nhiên `finally` không phải rollback tự động. Nếu worker đã gửi email hoặc DB đã commit, cleanup sau interrupt không thu hồi hành động đó. Hơn nữa, nếu cleanup bản thân bị blocking không interruptible thì việc phản hồi hủy vẫn có thể chậm; nếu dọn dẹp ném exception, cần bảo toàn nguyên nhân chính theo chính sách ghi nhận lỗi.

## 6. Ví dụ Java 17 hoàn chỉnh: nhìn thấy ba giới hạn

Chương trình sau cố ý dùng `CountDownLatch` để kiểm soát thời điểm worker được phép tiếp tục, thay vì dựa vào thứ tự scheduling ngẫu nhiên.

1. `Future.get(timeout)` quá hạn nhưng Future vẫn chưa xong; mở latch, worker hoàn thành và caller lấy được kết quả.
2. `Future.cancel(true)` hủy một task đang ngủ; trong trường hợp này, `Thread.sleep` hợp tác với interrupt nên worker nhận `InterruptedException`. Dù worker trả về một chuỗi sau catch, `Future.get()` của Future đã hủy vẫn ném `CancellationException`.
3. `CompletableFuture.orTimeout` hoàn thành future bằng `TimeoutException` trong khi supplier vẫn chờ latch. Sau khi thả latch, worker hoàn tất, dù future đã ở trạng thái lỗi.

Lưu thành **`TimeoutCancellationLimitsDemo.java`**:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class TimeoutCancellationLimitsDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Future<String> task = pool.submit(() -> {
                entered.countDown();
                release.await();
                return "work result";
            });
            entered.await();
            try {
                task.get(30, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                System.out.println("get timed out");
            }
            System.out.println("after timeout, done=" + task.isDone());
            release.countDown();
            System.out.println("later result=" + task.get());

            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch stopped = new CountDownLatch(1);
            AtomicBoolean noticedInterrupt = new AtomicBoolean();
            Future<String> cancellable = pool.submit(() -> {
                started.countDown();
                try {
                    Thread.sleep(Long.MAX_VALUE);
                    return "normal";
                } catch (InterruptedException e) {
                    noticedInterrupt.set(true);
                    Thread.currentThread().interrupt();
                    return "stopped";
                } finally {
                    stopped.countDown();
                }
            });
            started.await();
            boolean accepted = cancellable.cancel(true);
            stopped.await();
            System.out.println("cancel returned=" + accepted);
            System.out.println("worker noticed interrupt=" + noticedInterrupt.get());
            try {
                cancellable.get();
            } catch (CancellationException e) {
                System.out.println("cancelled future has no result");
            }

            CountDownLatch stageStarted = new CountDownLatch(1);
            CountDownLatch stageRelease = new CountDownLatch(1);
            CountDownLatch stageFinished = new CountDownLatch(1);
            CompletableFuture<String> stage = CompletableFuture.supplyAsync(() -> {
                stageStarted.countDown();
                try {
                    stageRelease.await();
                    return "stage result";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "interrupted";
                } finally {
                    stageFinished.countDown();
                }
            }, pool);
            stageStarted.await();
            stage.orTimeout(30, TimeUnit.MILLISECONDS);
            try {
                stage.join();
            } catch (CompletionException e) {
                System.out.println("stage result=" + e.getCause().getClass().getSimpleName());
            }
            stageRelease.countDown();
            stageFinished.await();
            System.out.println("stage worker completed=" + (stageFinished.getCount() == 0));
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Biên dịch và chạy với JDK 17 hoặc mới hơn từ thư mục chứa file:

~~~bash
javac --release 17 TimeoutCancellationLimitsDemo.java
java TimeoutCancellationLimitsDemo
~~~

Stdout dự kiến khi chương trình được chạy bình thường, không bị môi trường ngắt đột ngột:

~~~text
get timed out
after timeout, done=false
later result=work result
cancel returned=true
worker noticed interrupt=true
cancelled future has no result
stage result=TimeoutException
stage worker completed=true
~~~

### 6.1. Vì sao các dòng kết quả có ý nghĩa?

**Phần Future chờ timeout:** `entered.await()` bảo đảm task đã vào vùng chờ trên `release`. Do latch chưa được mở, `get(30 ms)` không thể nhận kết quả thành công, vì vậy ném `TimeoutException`. Lúc này Future chưa xong; khi main gọi `release.countDown()`, task tiếp tục và trả `work result`. Mốc timeout đã trôi qua nhưng worker vẫn có thể hoàn tất thành công.

**Phần cancel:** `started.await()` bảo đảm worker thực sự đã vào task. `cancel(true)` yêu cầu hủy; vì worker đang `Thread.sleep(Long.MAX_VALUE)`, interrupt được phản hồi qua `InterruptedException`. `stopped.await()` chờ worker chạy xong khối `finally` trước khi in cờ; do đó dòng `worker noticed interrupt=true` không dựa vào race đọc sớm. Future đã hủy không trả `"stopped"` qua `get()`, dù worker có thể kết thúc logic catch và trả giá trị đó nội bộ.

**Phần CompletableFuture:** supplier đợi `stageRelease`. `stage.orTimeout(30 ms)` làm chính future hoàn thành ngoại lệ nếu chưa xong trong khoảng thời gian đó; `join()` quan sát `CompletionException` chứa `TimeoutException`. Main chỉ sau đó mới mở `stageRelease`. `stageFinished.await()` chứng minh supplier đi hết `finally`. Dòng cuối không nói là giá trị `stage result` đã được xuất bản vào future: future đã hoàn tất bằng lỗi trước đó.

Ví dụ là một **thí nghiệm có điều phối bằng latch**, không phải một chiến lược timeout cho production. `await()` không timeout trong những chỗ có chủ đích vì main đã biết điểm cấp tín hiệu trong thí nghiệm; test và ứng dụng thực nên dùng thời hạn tổng, có lối thoát và cleanup phù hợp khi gặp lỗi bất thường.

## 7. `CompletableFuture.orTimeout` và `completeOnTimeout`

Java 9+ có hai API tiện dụng, đều dùng được trên Java 17:

~~~java
CompletableFuture<T> limited = original.orTimeout(2, TimeUnit.SECONDS);
CompletableFuture<T> defaulted = original.completeOnTimeout(fallback, 2, TimeUnit.SECONDS);
~~~

Đây là **cú pháp minh họa**, giả định `T`, `original`, `fallback` đã khai báo. Trong hai ví dụ, phương thức được gọi trên `original` **trả chính CompletableFuture đó**; nếu viết liên tiếp trên cùng một object thì có thể có sự cạnh tranh giữa hai chính sách, không có hai handle độc lập như tên biến gợi ý. Khi deadline tới và future chưa hoàn thành:

- `orTimeout` cố hoàn thành chính CompletableFuture **ngoại lệ** với `TimeoutException`.
- `completeOnTimeout` cố hoàn thành chính CompletableFuture **bình thường với giá trị dự phòng**.

Chúng **không tự động hủy hoặc interrupt supplier đang chạy** chỉ vì future bị hoàn thành do timeout. Đối với `CompletableFuture.supplyAsync`, một phép tính đang thực thi có thể tiếp tục trên executor ngay cả khi CompletableFuture đã có kết quả timeout. Nếu muốn ngắt nguồn, cần thiết kế một handle/cơ chế hủy thật sự cho nguồn đó và hiểu tính hợp tác của nguồn.

### 7.1. Timeout làm thay đổi handle chia sẻ

Khi nhiều caller cùng giữ reference đến **một** `CompletableFuture`, lời gọi `orTimeout` trên shared future có thể làm **cả những caller khác** nhìn thấy future đó hoàn thành bằng timeout. Đây là thay đổi trạng thái của đối tượng Future, không chỉ “tôi không muốn chờ nữa”.

Nếu cần timeout riêng cho từng caller mà không làm thay đổi kết quả chung, nên dùng một cơ chế giới hạn **việc chờ của caller** (ví dụ `get(timeout)`) hoặc tạo một dependent stage thích hợp rồi áp dụng timeout lên stage riêng. Cần chú ý vòng đời và việc lan truyền cancellation từ stage phụ về nguồn **không tự động được bảo đảm** chỉ vì hai stage liên kết với nhau.

### 7.2. `completeOnTimeout` có thể che trạng thái công việc vẫn đang xử lý

Ví dụ:

~~~java
CompletableFuture<String> displayed =
        CompletableFuture.supplyAsync(() -> remoteSave())
                .completeOnTimeout("pending", 1, TimeUnit.SECONDS);
~~~

Đây là fragment với `remoteSave()` chưa định nghĩa; cần import `CompletableFuture` và `TimeUnit`. Nếu sau 1 giây supplier vẫn đang chạy, `displayed.join()` có thể trả `pending`. Điều đó chỉ nói rằng **một giá trị mặc định đã được dùng làm kết quả cho future**. Không chứng minh remote write đã dừng, chưa bắt đầu, không tạo side effect hay sẽ thành công về sau.

Không nên đặt fallback `"saved"` cho một tác vụ lưu dữ liệu chưa biết kết quả, vì sẽ tạo thông báo sai. Một trạng thái như `PENDING`/`UNKNOWN` có thể phù hợp hơn nếu hệ thống có job ID và đường tra cứu hậu xử lý.

### 7.3. `CompletableFuture.cancel(true)` khác `FutureTask.cancel(true)`

`CompletableFuture` triển khai `Future` nhưng không trực tiếp kiểm soát thread đã khởi chạy phép tính. Theo hợp đồng Java, tham số `mayInterruptIfRunning` trong `CompletableFuture.cancel(boolean)` **không có tác dụng điều khiển interrupt đối với chính implementation này**. Lời gọi làm future hoàn thành ngoại lệ bằng `CancellationException` nếu còn có thể hủy; nó không bảo đảm interrupt supplier chạy bởi `supplyAsync`.

Không được suy từ `CompletableFuture.cancel(true)` rằng mã bên trong `supplyAsync` đã nhận interrupt như ví dụ `Future` được trả từ `ExecutorService.submit()`. Đây là khác biệt rất thực tế giữa hai cơ chế có chung interface `Future`.

## 8. Cancellation có race condition với hoàn tất bình thường

Một tác vụ có thể hoàn thành gần đúng lúc caller yêu cầu hủy:

~~~text
Tình huống A:
worker hoàn thành -> Future nhận result -> cancel() trả false

Tình huống B:
cancel() đánh dấu canceled -> worker có thể vẫn chạy ->
get() của Future đã hủy ném CancellationException

Tình huống C:
caller get(timeout) hết hạn -> worker hoàn thành ngay sau đó ->
caller thấy timeout ở lần chờ cũ, kết quả có thể đã sẵn sàng
~~~

Cả ba đều có thể xảy ra. Không có một phép kiểm tra `isDone()` rồi `cancel()` tách biệt nào biến hai thao tác thành một giao thức atomic “nếu chưa xong thì đảm bảo dừng”. Sau timeout, task có thể hoàn thành trước khi caller gọi hủy; lúc đó cancel thất bại nhưng kết quả có thể đã được ghi vào Future.

**Đừng viết logic nghiệp vụ dựa trên suy diễn từ thứ tự ở độ phân giải milli giây.** Hãy xây các nhánh rõ:
- `cancel` trả `false` thì task có thể đã hoàn tất hoặc hủy không thành công; đọc lại trạng thái có quản lý phù hợp.
- `cancel` trả `true` thì trạng thái Future đã hủy, nhưng công việc/side effect bên dưới vẫn cần một kênh xác minh riêng nếu yêu cầu.
- `get` timeout thì caller chưa nhận được kết quả trong thời hạn, không phải kết luận cuối của task.

Nếu một request có tiền thật hoặc tài nguyên quan trọng, trạng thái `UNKNOWN` thường an toàn hơn khẳng định thất bại tuyệt đối.

## 9. Timeout của I/O không đồng nghĩa rollback ở phía server

Một HTTP client có thể timeout khi:
- Không kết nối được đến server trong khoảng quy định.
- Đang đợi nhận dữ liệu từ server.
- Request tổng thể vượt quá deadline.
- Một tầng proxy/load balancer ngắt kết nối.
- Client bị interrupt hoặc hủy theo cơ chế riêng của thư viện.

Từ góc nhìn caller, timeout ở tầng nhận phản hồi **không phân biệt chắc**:
1. Request chưa rời khỏi client.
2. Request đã tới server nhưng chưa được thực thi.
3. Server đã thực thi và commit, nhưng response bị thất lạc.
4. Server đang tiếp tục thực thi sau khi client ngừng chờ.

Tương tự, một JDBC query timeout hoặc việc cancel statement là **yêu cầu điều khiển** được chuyển tới driver/database theo khả năng hỗ trợ, không phải chứng cứ mọi thao tác liên quan đã rollback. Cần kiểm tra transaction, isolation, auto-commit, loại lệnh và trạng thái commit theo hợp đồng của driver/database. Lỗi xảy ra đúng lúc `commit` có thể tạo kết quả **không chắc chắn**: server đã commit nhưng client chưa nhận được xác nhận.

### 9.1. Rủi ro double side effect khi retry sau timeout

Ví dụ quy trình thanh toán:

~~~text
Client gửi POST /charge, requestId=abc
    -> service nhận request và ghi thanh toán
Client hết timeout, chưa nhận response
Client retry POST /charge với requestId mới
    -> service tạo thêm một giao dịch
~~~

Đây là lỗi thiết kế. Khi một thao tác có side effect, nên dùng **idempotency key ổn định cho cùng yêu cầu logic**, kết hợp với quy tắc server hỗ trợ và truy vấn trạng thái để tránh tạo hiệu ứng trùng. Chỉ gắn header có tên `Idempotency-Key` mà server không lưu/tránh trùng ở mức atomic sẽ không tạo bảo đảm.

Một chiến lược thực tế:
1. Tạo `operationId` một lần trước yêu cầu đầu.
2. Gửi lại **cùng ID và payload hợp lệ** khi retry theo hợp đồng remote API.
3. Nếu timeout, tra cứu trạng thái bằng ID trước hoặc theo quy định của server.
4. Trả một trạng thái có cấu trúc như `PENDING`/`UNKNOWN` nếu chưa thể xác định.
5. Ghi nhận để reconciliation/đối soát khi kết quả cuối không thể xác nhận ngay.

Không retry vô hạn hoặc retry đồng thời nhiều request khi không biết cơ chế chống lặp của phía nhận. Nếu tác vụ thuộc job nền có tính bền vững qua restart, handle `Future` trong bộ nhớ là chưa đủ; cần kho trạng thái job/queue bền vững hoặc hệ thống điều phối phù hợp.

## 10. Truyền deadline xuống các bước bên dưới

Đối với một request có ngân sách tổng là 2 giây, cho ba lời gọi con mỗi lời chờ 2 giây có thể làm tổng thời gian vượt xa giới hạn. Cần phân bổ một **deadline chung** rồi truyền thời gian còn lại xuống từng bước.

Ví dụ ý tưởng Java:

~~~java
long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);

// Trước mỗi bước:
long remaining = deadline - System.nanoTime();
if (remaining <= 0) {
    throw new TimeoutException("overall deadline exceeded");
}
String value = future.get(remaining, TimeUnit.NANOSECONDS);
~~~

Đây là fragment, cần import `TimeUnit`, `TimeoutException` và xử lý các checked exception khác từ `get`. `System.nanoTime()` thích hợp để **đo khoảng thời gian trôi qua** trong một tiến trình, không phải timestamp để ghi nhật ký hoặc đồng bộ với service ngoài. Với deadline cực lớn có thể cần xử lý vấn đề tràn số; với timeout nghiệp vụ thông thường và khoảng giới hạn hợp lý, mẫu này minh họa được nguyên tắc dùng ngân sách còn lại.

Nếu tầng HTTP client hỗ trợ request timeout riêng, hãy truyền `remaining` (hoặc mức nhỏ hơn phù hợp) vào client. Nếu database driver có query timeout hoặc API cancellation riêng, cấu hình đúng theo hợp đồng. Tuy vậy, ngay cả việc lan truyền deadline đầy đủ cũng chỉ **giới hạn thời gian chờ/cố gắng kết thúc**, không tạo bảo đảm remote side effect sẽ bị hoàn tác.

### 10.1. Có phải luôn phải cancel khi timeout?

Không. Caller có thể:
- **Chỉ thôi chờ** và để một job dùng chung tiếp tục chạy, ví dụ tác vụ cache warm-up.
- **Yêu cầu hủy** nếu công việc không còn giá trị, caller sở hữu quyền hủy và task hỗ trợ dừng.
- **Chuyển sang chế độ nền** với job ID và trạng thái theo dõi khi không thể dừng an toàn.
- **Đối soát trạng thái bên ngoài** nếu tác vụ đã gửi yêu cầu có side effect và chưa biết kết quả.

Hủy tự động một Future đang được **nhiều request sử dụng chung** có thể làm request khác mất kết quả. Tầng điều phối phải xác định quyền sở hữu handle trước khi hủy. Không gắn `cancel(true)` vào mọi khối `catch (TimeoutException)` mà không biết hậu quả.

## 11. `ExecutorService.shutdownNow()` không cưỡng chế dừng an toàn

Trong Java 17:

| API | Ý nghĩa | Giới hạn |
| --- | --- | --- |
| `shutdown()` | Không nhận task mới; các task đã gửi tiếp tục theo quy tắc executor | Không tự hủy task đang chạy |
| `shutdownNow()` | Cố ngừng task đang chạy, thường bằng interrupt, và thu hồi task còn chờ | Không bảo đảm tất cả task đang chạy đều ngừng |
| `awaitTermination(timeout, unit)` | Đợi executor kết thúc trong thời hạn | Trả `false` nếu chưa chấm dứt, không nói mọi task thành công |

Một code shutdown điển hình cần có deadline chờ, chính sách gửi signal, xử lý interruption và ghi nhận task chưa kết thúc. Nhưng gọi `shutdownNow()` hai lần hoặc đóng pool không thể sửa một task chặn không interruptible; cần sửa **task hoặc API bên dưới** để có lối thoát.

Nếu worker giữ file lock, connection hoặc một resource chỉ do nó sở hữu, việc cho phép worker dùng `finally` giải phóng thường an toàn hơn hành động từ ngoài thread đóng ngẫu nhiên một tài nguyên chung. Một số API hỗ trợ hủy bằng cách đóng resource do tác vụ sở hữu, nhưng phải đọc hợp đồng thread-safety và phạm vi sở hữu thật sự trước khi áp dụng.

## 12. Những lựa chọn thiết kế cho task dài

### 12.1. Chia công việc thành đơn vị nhỏ có điểm hủy

Nếu một task tính toán nhiều phần tử, thay vì một vòng lặp dài không kiểm tra, hãy:
- Giữ trạng thái ở các điểm có thể quan sát tiến độ.
- Kiểm tra deadline hoặc cờ interrupt theo chu kỳ phù hợp.
- Dừng ở điểm nhất quán, không để invariant hỏng.
- Dọn tài nguyên trong `finally` hoặc try-with-resources.
- Trả trạng thái hủy/partial result nếu hợp đồng cho phép.

Việc kiểm tra quá dày có thể làm tăng chi phí, còn quá thưa làm hủy chậm; chọn tần suất theo thời gian mỗi đơn vị công việc và yêu cầu về độ trễ dừng. Một task đang giữ lock lâu cần có chính sách rõ để tránh làm các luồng khác bị kẹt khi bị hủy.

### 12.2. Kết quả “partial success” cần được biểu đạt tường minh

Nếu đã xử lý 70/100 file rồi timeout, hệ thống không nên chỉ trả `false`. Có thể dùng result như `Completed(100)`, `Partial(70, failures)`, `Cancelled(70)` hoặc `Unknown(operationId)` tùy nghiệp vụ. Nếu caller cần tiếp tục hoặc bù trừ, phải biết:
- Những đơn vị nào đã hoàn tất.
- Những đơn vị nào chưa được xử lý hoặc trạng thái không xác định.
- Có thể chạy lại phần nào an toàn.
- Kết quả nào đã commit/được công bố.

Chỉ có `CancellationException` của Future không mang thông tin đó. Kênh tiến độ/định danh ngoài Future có thể cần thiết, nhất là sau crash hoặc restart.

### 12.3. Dừng an toàn không đồng nghĩa hoàn tác

Một worker nhận interrupt sau khi đã ghi 70 bản ghi có thể dừng rất nhanh, nhưng 70 bản ghi vẫn tồn tại nếu chúng đã commit. Nếu yêu cầu “tất cả hoặc không có gì”, phải dùng transaction/atomic publish trong phạm vi tài nguyên hỗ trợ. Nếu hành động đã xảy ra trên nhiều hệ thống, cần compensation, saga, outbox hoặc đối soát thích hợp; chúng đều có giới hạn và bản thân compensation cũng có thể thất bại.

## 13. Phản ví dụ phổ biến

### Sai lầm 1: Timeout đồng nghĩa task thất bại hoàn toàn

~~~java
try {
    return future.get(1, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    return "nothing happened";
}
~~~

Đây là fragment để phân tích anti-pattern; các kiểu/checked exception khác phải được xử lý trong code thật. `TimeoutException` chỉ nói caller chưa có kết quả trong một giây. Trả `"nothing happened"` có thể sai nếu server đã ghi dữ liệu hoặc worker sẽ còn xử lý.

### Sai lầm 2: `cancel(true)` có nghĩa worker chắc chắn chết

~~~java
future.cancel(true);
System.out.println("work was stopped");
~~~

Đây là fragment minh họa, không có cách kiểm chứng worker đã dừng. Hãy phân biệt **yêu cầu hủy Future** và **xác nhận completion của worker** nếu caller cần sự xác nhận đó. Có thể dùng một kênh completion riêng hoặc mô hình job state, đồng thời giữ timeout để không đợi mãi.

### Sai lầm 3: Nuốt `InterruptedException`

~~~java
try {
    Thread.sleep(1000);
} catch (InterruptedException ignored) {
    // Tiếp tục chạy như không có lệnh hủy.
}
~~~

Đây là fragment phản ví dụ. Nếu muốn tôn trọng yêu cầu hủy, nên truyền tiếp hoặc khôi phục cờ interrupt rồi thoát khỏi task theo đúng hợp đồng. Không biến interrupt thành giá trị thất bại của worker khi thực ra chính luồng điều phối mới bị interrupt.

### Sai lầm 4: `orTimeout` tự ngắt tất cả các stage nguồn

Nếu stage A gọi remote, stage B nối từ A và B được đặt timeout, điều này không tạo lệnh hủy chắc chắn tới socket/remote service do A dùng. Các dependent stage chỉ diễn đạt phụ thuộc kết quả; muốn dừng nguồn cần cơ chế hủy do API nguồn hỗ trợ.

### Sai lầm 5: Retry một tác vụ có side effect bằng operationId mới

Một lỗi mạng hoặc timeout có thể tới sau commit. Nếu retry tạo operationId khác, phía nhận có thể thực hiện tác động hai lần. Dùng cùng operationId và hợp đồng idempotency/tra cứu trạng thái đúng phía server.

### Sai lầm 6: Có `Future.isDone()` nên mọi tài nguyên đều đã được dọn

Future có thể xong do hủy, timeout stage, hoặc hoàn tất bất thường. Với `CompletableFuture`, supplier bên dưới vẫn có thể hoạt động sau khi future đã hoàn thành do timeout. Kiểm tra tài nguyên/worker theo kênh riêng nếu cần bảo đảm dọn xong.

### Sai lầm 7: Đóng bất kỳ tài nguyên nào từ thread điều phối

Đóng connection/stream đang được task khác hoặc nhiều request chia sẻ có thể phá tác vụ khác. Chỉ đóng resource có quyền sở hữu hợp lệ hoặc dùng API hủy được nhà cung cấp hỗ trợ, với quy tắc thread-safety rõ ràng.

## 14. Kiểm thử giới hạn thật của timeout và cancellation

Một bộ test tốt phải tạo được các trạng thái đúng thời điểm, thay vì dựa vào `Thread.sleep(100)` và đoán scheduler. `CountDownLatch` giúp biết task đã bắt đầu, chặn nó ở điểm xác định và thả nó ở thời điểm mong muốn.

| Kịch bản | Hành vi cần kiểm chứng |
| --- | --- |
| `get(timeout)` hết hạn | Caller nhận `TimeoutException` nhưng Future chưa bị tự động hủy |
| Thả latch sau timeout | Worker vẫn có thể hoàn thành và trả kết quả |
| `cancel(true)` khi task đang `sleep` | Worker nhận interrupt, chạy cleanup; Future báo canceled |
| `cancel(true)` khi task CPU bỏ qua interrupt | Future có thể canceled nhưng worker tiếp tục cho đến khi logic dừng |
| `cancel(false)` khi task đã chạy | Không có lời hứa gửi interrupt tới worker |
| `CompletableFuture.orTimeout` trên supplier bị chặn | Future hoàn thành lỗi timeout, supplier có thể tiếp tục |
| `completeOnTimeout` | Future trả fallback, nhưng nguồn có thể còn chạy |
| `cancel(true)` trên CompletableFuture | Không giả định supplier nhận interrupt |
| Timeout ngay gần lúc completion | Chấp nhận race giữa kết quả, timeout và hủy theo hợp đồng |
| Caller bị interrupt trong `get` | Xử lý `InterruptedException`, không suy ra task lỗi |
| Thao tác remote timeout sau khi commit | Tra operationId để xác minh, không nhân đôi side effect |
| Nhiều người dùng chung một future | Timeout/hủy của một caller không phá kết quả của caller khác ngoài ý muốn |

Để mô phỏng task **bỏ qua interrupt** trong một test, chỉ làm trong môi trường có deadline và lối thoát chắc chắn; ví dụ dùng một latch được giải phóng trong `finally` của test. Tránh tạo vòng lặp vô hạn không thể thu hồi. Kiểm thử cleanup cần xác nhận worker đã thật sự ra khỏi đoạn xử lý, chứ không chỉ kiểm tra Future `isCancelled()`.

Không khẳng định một thứ tự log từ hai thread nếu test không tạo quan hệ đồng bộ. Khi kiểm thử giới hạn mili giây, nên chọn latch/điểm sự kiện quyết định kết quả thay vì đặt timeout sát tốc độ máy đang chạy để tránh test chập chờn.

## 15. Cây quyết định sau khi caller bị timeout

~~~text
Caller chưa có kết quả trước deadline
|
+-- Công việc chỉ đọc, không có side effect?
|      +-- Có: có thể trả timeout/để tác vụ chạy hoặc yêu cầu hủy
|      +-- Không chắc: kiểm tra tác vụ con và ảnh hưởng ngoài hệ thống
|
+-- Caller có quyền sở hữu việc hủy?
|      +-- Không: không hủy task dùng chung chỉ vì caller timeout
|      +-- Có: cân nhắc cancel theo khả năng hỗ trợ
|
+-- Task có hỗ trợ interruption/deadline?
|      +-- Có: phát yêu cầu, xác nhận tiến độ dừng theo kênh quản lý
|      +-- Không: cần thay thiết kế I/O/task hoặc dùng trạng thái pending
|
+-- Có hiệu ứng bên ngoài chưa xác định?
       +-- Có: dùng operationId, idempotency, tra trạng thái/đối soát
       +-- Không: báo kết quả hủy/chưa hoàn tất theo hợp đồng
~~~

Không có một nhánh chung “timeout → rollback hết”. Cách đúng phụ thuộc vào phạm vi công việc, quyền sở hữu, cơ chế hợp tác với hủy và giao thức của từng tài nguyên.

## 16. Checklist dành cho Java API bất đồng bộ

1. **Đặt tên đúng trạng thái:** timeout là “chưa nhận kết quả đúng hạn”; cancel của Future là “handle đã hủy”; không đồng nhất với worker đã dừng hay side effect đã undo.
2. **Truyền deadline có mục đích:** giới hạn thời gian chờ của caller và cấu hình timeout cho network/database theo API cụ thể.
3. **Hủy đúng chủ sở hữu:** xác định Future dùng riêng hay dùng chung; tránh hủy một job vẫn cần cho caller khác.
4. **Tác vụ hợp tác:** hỗ trợ kiểm tra interrupt/deadline, không nuốt `InterruptedException`, đặt điểm dừng nhất quán và cleanup tài nguyên đúng phạm vi.
5. **Hiểu API:** `Future.get(timeout)` không hủy; `Future.cancel(true)` chỉ cố hủy/interrupt; `CompletableFuture.orTimeout` làm future hoàn thành lỗi; `CompletableFuture.cancel(true)` không đảm bảo interrupt supplier.
6. **Chấp nhận race:** task có thể hoàn tất khi timeout/cancel diễn ra; đọc trạng thái một cách có kiểm soát, không dựa vào thứ tự không có đồng bộ.
7. **Tôn trọng side effect:** hủy Java task không làm remote service rollback. Với request có tác dụng phụ, dùng idempotency key và tra trạng thái khi cần.
8. **Thiết kế vòng đời:** quản lý executor, chờ shutdown trong hạn, ghi nhận những task chưa thể kết thúc và tránh rò resource.
9. **Báo kết quả có ý nghĩa:** phân biệt completed, failed, canceled, pending và unknown; cung cấp job ID/progress nếu kết quả có thể tới sau khi caller đã timeout.
10. **Test đúng trạng thái bên dưới:** kiểm tra worker/remote đã dừng thật hay chưa, thay vì chỉ assert exception/cờ của handle.

### Bài tập vận dụng

Thiết kế một Java service nhận yêu cầu xuất báo cáo rồi tải lên object storage. API phải phản hồi trong 3 giây, trong khi công việc có thể kéo dài 30 giây. Hãy trả lời:

- Khi `future.get(3, SECONDS)` timeout, nên trả “thất bại”, “đã tiếp nhận”, hay một job ID để tiếp tục tra cứu? Tại sao?
- Nếu người dùng hủy báo cáo, nên gọi `cancel(true)` ở đâu? Task đang upload có hỗ trợ dừng không? Nếu upload đã hoàn tất ở storage nhưng phản hồi thất lạc, trạng thái nào là đúng?
- Có thể dùng một `CompletableFuture.orTimeout(3, SECONDS)` cho nhiều caller cùng tham gia không? Điều gì xảy ra với supplier?
- Nếu retry upload, làm thế nào tránh tạo ra hai file mang cùng nội dung nhưng định danh khác nhau hoặc công bố hai phiên bản nhầm lẫn?
- Viết test bằng latch chứng minh task còn chạy sau timeout; thêm test rằng worker nhận interrupt và dọn file tạm nhưng file đã công bố không bị xóa tùy tiện.

**Điểm cần nhớ:** Timeout giới hạn **sự chờ đợi hoặc trạng thái của future**, cancellation là **yêu cầu hủy**. Việc dừng được một tác vụ thực thi và việc kiểm soát ảnh hưởng của nó lên file, database hay dịch vụ ngoài phụ thuộc vào thiết kế tác vụ và giao thức của hệ thống bên dưới.

</details>

- [Quay lại đầu trang](#back-to-top)
