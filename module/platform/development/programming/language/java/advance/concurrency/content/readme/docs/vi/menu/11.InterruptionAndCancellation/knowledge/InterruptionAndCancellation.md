<a id="back-to-top"></a>

# Interrupt &amp; Hủy

## Menu
- [Interrupt là tín hiệu hợp tác, không phải dừng cưỡng bức](#interruption-and-cancellation-001)
- [interrupt(), isInterrupted() và Thread.interrupted()](#interruption-and-cancellation-002)
- [InterruptedException và cách xử lý hợp lệ](#interruption-and-cancellation-003)
- [Khôi phục interrupt flag khi không thể truyền ngoại lệ](#interruption-and-cancellation-004)
- [Điểm chặn hỗ trợ và không hỗ trợ interrupt](#interruption-and-cancellation-005)
- [Thiết kế cancellation flag, volatile và cleanup](#interruption-and-cancellation-006)
- [Future.cancel(true) và các giới hạn thực tế](#interruption-and-cancellation-007)
- [Timeout khác cancellation thế nào?](#interruption-and-cancellation-008)
- [shutdown(), shutdownNow() và awaitTermination()](#interruption-and-cancellation-009)
- [Đảm bảo finally đóng tài nguyên khi dừng tác vụ](#interruption-and-cancellation-010)
- [Phân tầng tín hiệu hủy trong chuỗi xử lý](#interruption-and-cancellation-011)
- [Không nuốt InterruptedException hoặc làm mất tín hiệu hủy](#interruption-and-cancellation-012)
- [Vì sao tránh Thread.stop(), suspend() và resume(): rủi ro khóa và tính nhất quán](#interruption-and-cancellation-013)

## <a id="interruption-and-cancellation-001">Interrupt là tín hiệu hợp tác, không phải dừng cưỡng bức</a>

<details>
<summary>Xem chi tiết</summary>

# Interrupt là tín hiệu hợp tác, không phải dừng cưỡng bức trong Java

## 1. Mục tiêu và mô hình tư duy

Giả sử một thread đang tính toán lâu, một tác vụ đang chờ kết quả từ hàng đợi, hoặc một worker đang chờ khóa. Khi không cần công việc đó nữa, thread khác có thể gọi `interrupt()` để **yêu cầu** worker dừng hoặc hủy hoạt động đang thực hiện. Nhưng khác với một lệnh “kill process”, **`Thread.interrupt()` không cưỡng bức chấm dứt thread**.

Trong Java, interruption là một **giao thức hợp tác (cooperative cancellation)**: bên yêu cầu hủy phát tín hiệu; code chạy trong thread mục tiêu phải chủ động nhận diện tín hiệu và kết thúc theo một cách an toàn, hoặc API blocking mà nó gọi phải hỗ trợ interruption để giải phóng việc chờ.

Sau bài này, bạn sẽ:

- Phân biệt **gửi interrupt**, **trạng thái interrupt**, **phát sinh InterruptedException** và **thread thật sự kết thúc**.
- Dùng chính xác `interrupt()`, `isInterrupted()`, `Thread.interrupted()`, `Thread.currentThread()` và `join()`.
- Hiểu tại sao các API như `sleep()`, `wait()`, `join()`, `BlockingQueue.take()` hay `ReentrantLock.lockInterruptibly()` có thể phản ứng bằng `InterruptedException`.
- Chọn giữa **ném tiếp exception**, **khôi phục cờ bằng `Thread.currentThread().interrupt()`**, và **cleanup rồi thoát**.
- Thiết kế CPU-bound loops, blocking worker và executor tasks hỗ trợ hủy mà không mất dữ liệu hoặc tài nguyên.
- Phân biệt `Future.cancel(true)`, `shutdown()`, `shutdownNow()`, timeout và interrupt; không tuyên bố task đã dừng chỉ từ một yêu cầu.
- Biên dịch và chạy các ví dụ Java 17+ có thứ tự được kiểm soát bằng `CountDownLatch` và `join()`.

**Phạm vi:** Java SE/JVM concurrency nói chung; không phụ thuộc framework. Tất cả chương trình đầy đủ trong bài tương thích **JDK 17+**.

## 2. Interrupt là yêu cầu, không phải nút dừng cưỡng bức

Mỗi `Thread` có một **interrupted status** (trạng thái/cờ interrupt). Thread khác có thể gọi:

~~~java
worker.interrupt();
~~~

Lời gọi này **không tự bảo đảm** rằng `worker` chuyển ngay sang `TERMINATED`. Nó báo hiệu cho worker rằng có yêu cầu ngừng/hủy hoạt động hiện tại. Phản ứng thực tế phụ thuộc worker đang làm gì:

1. **Worker đang chạy CPU calculation không kiểm tra interrupt:** có thể tiếp tục chạy dù cờ đã được đặt.
2. **Worker chủ động kiểm tra `isInterrupted()`:** có thể thoát khỏi vòng lặp, rollback phần việc chưa commit hoặc cleanup rồi kết thúc.
3. **Worker đang chờ ở API interruptible:** API có thể thoát khỏi trạng thái chờ và ném `InterruptedException`; nhiều API clear cờ khi ném.
4. **Worker đang block ở API không hỗ trợ interruption:** có thể **không tỉnh dậy chỉ vì** interrupt; phải dùng timeout, đóng tài nguyên hoặc cơ chế cancel được API đó hỗ trợ.
5. **Worker đã hoàn thành:** gọi interrupt không đưa nó trở lại trạng thái chạy; không thể dùng interrupt để đảo ngược side effects đã xảy ra.

Bởi vậy, cần tách **ba sự kiện**:

~~~text
Thread điều phối             Thread worker
      │                           │
      ├─ worker.interrupt() ──────►│  1. nhận yêu cầu hủy (tín hiệu)
      │                           │
      │                           ├─ kiểm tra flag / API trả InterruptedException
      │                           │  2. phát hiện tín hiệu
      │                           │
      │                           ├─ cleanup / kết thúc an toàn
      │                           │  3. worker thật sự hoàn thành
      │                           ▼
      └─ worker.join() ◄───── TERMINATED
~~~

Lời gọi `interrupt()` là **request**; `join()` hoặc một cơ chế completion khác mới giúp caller biết thread đã thực sự kết thúc. Chúng không đồng nghĩa với nhau.

## 3. Ba API thường bị nhầm: interrupt, isInterrupted và interrupted

| API | Đối tượng tác động | Ý nghĩa | Có xóa cờ không? |
|---|---|---|---|
| `someThread.interrupt()` | Thread được tham chiếu bởi `someThread` | Gửi yêu cầu interrupt, tùy trạng thái có thể khiến API blocking phản ứng | Không phải phương thức kiểm tra/clear cờ; API blocking có thể clear trong lúc ném exception |
| `someThread.isInterrupted()` | Thread `someThread` | Kiểm tra trạng thái interrupt hiện tại | **Không** |
| `Thread.interrupted()` | **Thread đang gọi** phương thức static | Kiểm tra cờ interrupt của thread hiện tại | **Có**, clear cờ khi kiểm tra |

`Thread.interrupted()` là **static method** và **không** kiểm tra một thread bất kỳ. Kể cả khi viết `worker.interrupted()` (Java cho phép gọi static qua reference nhưng gây hiểu nhầm), method vẫn kiểm tra **current thread**, không phải worker. Luôn viết `Thread.interrupted()` khi thật sự muốn kiểm tra **và clear** interrupt flag của current thread.

Ví dụ ngắn:

~~~java
Thread me = Thread.currentThread();
me.interrupt();

System.out.println(me.isInterrupted());  // true
System.out.println(Thread.interrupted()); // true; đồng thời clear
System.out.println(me.isInterrupted());  // false
~~~

Đoạn mã trên là **minh họa chạy trong một method**. Nó giả định không có interrupt mới đến giữa các lời gọi. Đừng vô tình clear interrupt flag của thread thực hiện test trong framework hoặc worker pool trừ khi đó là một phần có chủ đích của giao thức xử lý.

### Câu hỏi quan trọng

**Nếu worker chỉ gọi isInterrupted() mà không clear, cờ có còn không?** Có, miễn không có hoạt động khác clear hoặc thay đổi trạng thái. Worker có thể nhận biết yêu cầu ở nhiều điểm kiểm tra.

**Nếu code gọi Thread.interrupted() trong một vòng, cờ có còn ở lần sau?** Lần gọi đầu có thể trả true rồi clear. Nếu không có interrupt tiếp theo, lần gọi sau trả false; nếu code không thoát ngay khi thấy true, nó có thể **vô tình tiếp tục** hoạt động như chưa bị yêu cầu hủy.

## 4. Interruption khi đang chạy CPU-bound work

Một vòng tính toán không đi qua API blocking sẽ **không tự dừng** khi caller gọi `interrupt()`. Tác vụ cần **định kỳ kiểm tra** cờ và dừng theo chính sách của nó.

~~~java
long compute(long items) throws InterruptedException {
    long sum = 0;
    for (long i = 0; i < items; i++) {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Calculation cancelled");
        }
        sum += i;
    }
    return sum;
}
~~~

Đây là một **method minh họa**, không phải chương trình độc lập. Điểm đáng chú ý:
- Worker không chờ JVM “giết” mình; nó tự kiểm tra điều kiện hủy.
- Dùng `isInterrupted()` để kiểm tra **không clear** cờ.
- Ném `new InterruptedException(...)` thủ công **không tự clear** interrupt flag. Việc clear cờ là đặc tính của những blocking operations cụ thể ném InterruptedException, không phải phép màu của constructor exception. Caller cần có protocol rõ ràng.
- Nếu vòng lặp chạy quá nhanh, có thể không cần kiểm tra từng iteration; kiểm tra theo từng batch có thể giảm overhead, nhưng tăng độ trễ phản ứng hủy. Chọn theo workload và deadline.
- Tác vụ phải tránh commit một phần kết quả chưa nhất quán. Nếu đã thay đổi tài nguyên bên ngoài, interrupt không rollback những thay đổi đó.

### Ví dụ độc lập 1: CPU-bound loop dừng khi tự kiểm tra interrupt

Lưu tệp **CooperativeCpuDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class CooperativeCpuDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean noticed = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            started.countDown();
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    noticed.set(true);
                    return;
                }
                Thread.onSpinWait();
            }
        }, "cpu-worker");

        worker.start();
        started.await();
        worker.interrupt();
        worker.join();

        System.out.println("noticed=" + noticed.get());
        System.out.println("alive=" + worker.isAlive());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac CooperativeCpuDemo.java
java CooperativeCpuDemo
~~~

Output trong điều kiện hoạt động bình thường:

~~~text
noticed=true
alive=false
~~~

**Phân tích:**

1. `started.await()` bảo đảm worker đã bắt đầu thực thi. Nó **không** bắt worker phải chạy đủ số vòng; đây chỉ là tín hiệu bắt đầu.
2. Main gọi `worker.interrupt()`. Nếu worker đang ở vòng lặp, `isInterrupted()` của nó cuối cùng thấy true; nếu interrupt đến ngay sau countDown và trước lượt kiểm tra đầu tiên, lượt đầu cũng thấy true.
3. Worker **tự return** khỏi Runnable, không bị Thread.interrupt cưỡng bức kết thúc.
4. `worker.join()` chờ thread hoàn tất rồi main in; vì vậy cả hai dòng không phụ thuộc thứ tự chạy giữa main và worker.
5. `Thread.onSpinWait()` chỉ là gợi ý cho vòng đợi ngắn, không phải cơ chế dừng hoặc đồng bộ. Trong production, tránh busy spin dài; ví dụ cố tình đơn giản để làm rõ interrupt flag.

**Điểm học tập:** Nếu bỏ câu `if (isInterrupted())` và không có điều kiện thoát nào khác, worker có thể tiếp tục chạy mãi dù main đã gọi interrupt. Đó là lý do gọi interrupt **không phải cưỡng bức dừng**.

## 5. InterruptedException: API blocking phản ứng như thế nào?

Nhiều API chờ theo hợp đồng Java phản ứng với interruption bằng `InterruptedException`. Các trường hợp quen thuộc:

| API | Khi bị interrupt trong pha chờ | Lưu ý |
|---|---|---|
| `Thread.sleep(...)` | Ném `InterruptedException` | Không nhả intrinsic monitor đang giữ; cờ được clear khi ném. |
| `Object.wait(...)` | Ném `InterruptedException` khi có interrupt theo hợp đồng | Wait sử dụng monitor và nhả monitor trong lúc chờ; phải lấy lại trước khi tiếp tục xử lý. |
| `Thread.join(...)` | Ném `InterruptedException` ở **thread đang join** | Không nhất thiết ngắt thread được join. |
| `CountDownLatch.await()` | Ném `InterruptedException` | Mục đích đợi latch, không đảm bảo tác vụ đích dừng. |
| `BlockingQueue.take()/put()` | Ném `InterruptedException` nếu bị hủy khi đang chờ | Có thể dùng vòng đời producer/consumer rõ ràng. |
| `ReentrantLock.lockInterruptibly()` | Ném `InterruptedException` trong lúc chờ lock | Khác `lock()` không ném checked exception trong pha acquire. |
| `ReentrantLock.tryLock(timeout, unit)` | Có thể ném `InterruptedException` | Phân biệt bị hủy với trả `false` do timeout. |
| `Future.get()` | Có thể ném `InterruptedException` ở **caller đang chờ** | Không đồng nghĩa Future task đã bị hủy. |

**Cần đọc hợp đồng từng API:** không phải mọi block đều interruptible. Một số kênh I/O từ `java.nio.channels.InterruptibleChannel` phản ứng bằng cách đóng channel và ném `ClosedByInterruptException` (thuộc `IOException`), chứ không nhất thiết bằng `InterruptedException`. Các socket/I/O, native calls, filesystem và thư viện bên ngoài có thể có hành vi khác nhau theo loại API và bản JDK. Không được hứa “interrupt luôn đánh thức mọi blocking call”.

### Khi InterruptedException được ném, cờ thường đã bị clear

Ví dụ với `Thread.sleep`:

~~~java
try {
    Thread.sleep(60_000);
} catch (InterruptedException e) {
    System.out.println(Thread.currentThread().isInterrupted());
    // false đối với trường hợp sleep đã clear cờ trước khi ném
}
~~~

Đây là đoạn method minh họa. Việc clear cờ cho phép caller **quyết định tiếp**: ném lại để truyền yêu cầu hủy lên trên; hoặc khôi phục cờ và kết thúc khi interface không cho phép ném checked exception.

Không phát biểu máy móc rằng “mọi API ném InterruptedException đều luôn clear flag đúng cùng một thời điểm” mà không đọc contract. Nhưng các API JDK phổ biến như `sleep`, `wait` và `join` có quy định rõ về việc clear khi ném.

## 6. Xử lý InterruptedException: ném tiếp, restore flag, hoặc thoát

### Cách A — Ném tiếp khi chữ ký cho phép

~~~java
void fetchWork(java.util.concurrent.BlockingQueue<String> queue)
        throws InterruptedException {
    String item = queue.take();
    process(item);
}
~~~

Đây là method minh họa; `process(item)` là logic do ứng dụng định nghĩa. Khi `take()` bị interrupt trong lúc chờ, exception truyền về caller. Caller có thể quyết định hủy tác vụ, log hoặc shutdown; không cần nuốt exception rồi âm thầm chạy tiếp.

### Cách B — Khôi phục cờ nếu không thể ném checked exception

Một `Runnable.run()` không khai báo `throws InterruptedException`:

~~~java
@Override
public void run() {
    try {
        workQueue.take();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // khôi phục tín hiệu
        return; // chấm dứt tác vụ hoặc thoát vòng xử lý
    }
}
~~~

Đây là đoạn trích trong một class có `BlockingQueue<?> workQueue`. Sau khi `take()` ném exception và clear cờ, gọi `currentThread().interrupt()` bảo tồn thông tin hủy cho tầng trên. `return` cho thấy tác vụ **chọn dừng** thay vì tiếp tục.

### Cách C — Cleanup tài nguyên trong finally

~~~java
void processOneTask() throws InterruptedException {
    Resource resource = acquireResource();
    try {
        performInterruptibleWork(resource);
    } finally {
        resource.close();
    }
}
~~~

Đoạn trên giả định `Resource`, `acquireResource` và `performInterruptibleWork` do ứng dụng định nghĩa và `close()` phù hợp với chữ ký. Ý tưởng là tài nguyên đã acquire phải được giải phóng kể cả khi `InterruptedException` thoát khỏi phương thức. Cần xác định hành vi nếu cleanup tự ném exception; có thể dùng `try-with-resources` với tài nguyên implements `AutoCloseable` để quản lý suppressed exceptions.

**Tại sao không “catch rồi bỏ qua”?**

~~~java
while (true) {
    try {
        taskQueue.take();
    } catch (InterruptedException ignored) {
        // Sai khi shutdown cần dừng: cứ quay lại đợi như không có tín hiệu.
    }
}
~~~

Nếu `take` clear flag, vòng lặp lặp lại và tiếp tục chờ; caller đã yêu cầu hủy nhưng worker có thể **bỏ qua hoàn toàn**. Đôi khi ứng dụng có lý do cụ thể để bỏ qua interruption và hoàn thành một thao tác bắt buộc, nhưng phải **document policy**, thường khôi phục interrupt flag trước khi thoát vùng không thể bị hủy. Mặc định, không nuốt tín hiệu.

## 7. Ví dụ độc lập 2: sleep ném exception và clear cờ

Chương trình chủ động bảo đảm worker đã đến sát điểm chờ bằng latch. Main interrupt worker. Dù interrupt đến **trước** khi worker thực hiện sleep hay khi nó đang sleep, lời gọi `sleep` sẽ ném `InterruptedException` trong điều kiện này.

Lưu tệp **InterruptedSleepDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterruptedSleepDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean caught = new AtomicBoolean();
        AtomicBoolean clearedInCatch = new AtomicBoolean();
        AtomicBoolean restoredInCatch = new AtomicBoolean();

        Thread worker = new Thread(() -> {
            started.countDown();
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                caught.set(true);
                clearedInCatch.set(!Thread.currentThread().isInterrupted());
                Thread.currentThread().interrupt();
                restoredInCatch.set(Thread.currentThread().isInterrupted());
            }
        }, "sleep-worker");

        worker.start();
        started.await();
        worker.interrupt();
        worker.join();

        System.out.println("caught=" + caught.get());
        System.out.println("cleared=" + clearedInCatch.get());
        System.out.println("restored=" + restoredInCatch.get());
        System.out.println("alive=" + worker.isAlive());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac InterruptedSleepDemo.java
java InterruptedSleepDemo
~~~

Output kỳ vọng, khi không có interrupt ngoài hoặc thao tác khác can thiệp:

~~~text
caught=true
cleared=true
restored=true
alive=false
~~~

Phân tích:

1. Worker phát `started.countDown()` ngay trước sleep, nên main biết worker đã vào Runnable. `started` không phải tín hiệu rằng worker chắc chắn đang ở trạng thái `TIMED_WAITING`.
2. Nếu interrupt đến trước sleep, cờ đã được set; khi sleep kiểm tra interrupted status, nó ném exception. Nếu worker đã sleep, interrupt đánh thức nó qua exception.
3. `Thread.sleep` clear interrupt status khi ném `InterruptedException`, vì vậy `isInterrupted()` ở đầu catch là false và biến `clearedInCatch` được ghi true.
4. Worker gọi `Thread.currentThread().interrupt()` để khôi phục tín hiệu, rồi `isInterrupted()` trả true.
5. Main dùng `join()`, nên các biến đã được worker ghi được quan sát sau khi kết thúc. `AtomicBoolean` là lựa chọn minh họa dễ đọc, không phải vì `join()` tự thiếu visibility.

**Giới hạn:** Trong môi trường sản xuất, không nên phụ thuộc vào một `sleep(60_000)` chỉ để mô phỏng synchronization. Ở đây thời lượng dài là cách minh họa API; chương trình dựa vào latch/interrupt/join, **không** dựa vào chờ hết 60 giây.

## 8. join(), interrupted status và hành động của thread đang đợi

Giả sử main gọi:

~~~java
worker.join();
~~~

Điều này yêu cầu **main đợi worker kết thúc**. Nếu thread khác interrupt **main** trong khi main chờ join, `join` có thể ném `InterruptedException` trong main; nó không có nghĩa worker tự động bị dừng.

Trong mã điều phối, cần suy nghĩ:
- Có muốn hủy worker khi main bị interrupt không? Nếu có, chủ động gọi `worker.interrupt()` hoặc hủy task theo protocol.
- Có cần chờ worker cleanup trước khi thoát? Có thể chờ theo deadline, nhưng phải xử lý trường hợp chính thread điều phối đã bị interrupt.
- Có thể rời bỏ quyền sở hữu task không? Một worker non-daemon không được quản lý có thể vẫn giữ process sống.
- Nếu caller quyết định ném tiếp InterruptedException từ join, tác vụ đích vẫn có thể tiếp tục nếu không có yêu cầu hủy riêng.

`join()` có memory consistency/happens-before cho trường hợp thread được join đã hoàn tất, nhưng **không phải cơ chế cưỡng bức hủy**.

## 9. Interrupt và thao tác chờ trong synchronized, Lock, Condition

### 9.1. synchronized không hỗ trợ hủy khi đang chờ lấy monitor

Một thread đang cố vào:

~~~java
synchronized (sharedLock) {
    updateState();
}
~~~

nếu monitor đang do thread khác giữ, việc gọi interrupt trên thread đang **tranh monitor** không làm JVM tự ném `InterruptedException` và rời bỏ yêu cầu lấy monitor. Interrupted status có thể được ghi nhận, nhưng thread vẫn cần acquire monitor trước khi thực thi block theo hợp đồng của intrinsic monitor.

**Đừng nhầm** với `Object.wait()`: `wait` được gọi **bên trong vùng synchronized** và là một API chờ interruptible; khi wait, thread nhả monitor trong lúc chờ và phải lấy lại trước khi tiếp tục. Hai pha “chờ để **vào** monitor” và “đang ở monitor rồi gọi `wait`” khác nhau.

### 9.2. ReentrantLock hỗ trợ pha acquire có thể hủy

~~~java
import java.util.concurrent.locks.ReentrantLock;

void work(ReentrantLock lock) throws InterruptedException {
    lock.lockInterruptibly();
    try {
        updateState();
    } finally {
        lock.unlock();
    }
}
~~~

Đây là method minh họa; `updateState()` thuộc ứng dụng. `lockInterruptibly()` cho phép thread dừng **pha đợi lock** bằng `InterruptedException`. Chỉ vào `try/finally` sau khi lock được acquire thành công, nên không gọi `unlock()` nếu acquisition thất bại do interrupt.

Đối với `tryLock(timeout, unit)`, cần phân biệt:
- Trả `true`: đã acquire lock, phải unlock sau khi xong.
- Trả `false`: hết thời gian chờ mà không acquire được.
- Ném `InterruptedException`: bị hủy trong pha chờ, **không phải** kết quả timeout thông thường.

### 9.3. Condition.await có thể bị interrupt

Với `Condition` tạo từ `Lock`, `await()` là cơ chế chờ có thể bị interrupt. Code cần đặt điều kiện trong vòng lặp `while` để xử lý spurious wakeup và thay đổi trạng thái cạnh tranh. Khi `await()` trả/throw, thread đã được yêu cầu reacquire lock theo hợp đồng của Condition trước khi rời await; các khía cạnh cụ thể của API cần được đọc khi xây dựng protocol. Đừng viết một vòng gọi `sleep` bên trong monitor để thay thế Condition, vì `sleep` không nhả monitor đang giữ.

## 10. Future.cancel(true): thử hủy task, không bảo đảm Thread đã chết

Trong `ExecutorService`, caller thường không nắm trực tiếp Thread chạy tác vụ. Thay vào đó, caller giữ `Future`:

~~~java
Future<?> future = executor.submit(task);
boolean requested = future.cancel(true);
~~~

Hợp đồng cơ bản:
- `cancel(true)` **cố gắng hủy** Future. Nếu task đã chạy, khi biết worker thread, executor có thể interrupt thread để yêu cầu dừng.
- `cancel(false)` không yêu cầu interrupt task đang chạy, nhưng có thể ngăn task chưa khởi động được thực thi.
- `Future.isCancelled()` phản ánh Future đã ở trạng thái cancelled theo contract, **không chứng minh** side effects của worker đã dừng hoặc cleanup xong tại đúng thời điểm đó.
- `future.get()` sau khi Future thực sự cancelled có thể ném `CancellationException`; không phải vì task “trả về” exception đó như kết quả bình thường.
- `future.get(timeout, unit)` hết thời gian chờ trả kết quả sẽ ném `TimeoutException`; **timeout khi đợi kết quả không tự động hủy task**. Nếu muốn hủy, caller phải quyết định gọi `cancel(...)`.
- Với những API không nhận biết interrupt, worker có thể không phản hồi kịp hoặc tiếp tục làm side effects sau khi Future bị đánh dấu cancelled.

### Ví dụ mô hình code

~~~java
java.util.concurrent.ExecutorService pool =
        java.util.concurrent.Executors.newSingleThreadExecutor();

java.util.concurrent.Future<?> future = pool.submit(() -> {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            performOneStep();
        }
    } finally {
        cleanupResources();
    }
});

try {
    future.get(500, java.util.concurrent.TimeUnit.MILLISECONDS);
} catch (java.util.concurrent.TimeoutException e) {
    future.cancel(true); // yêu cầu hủy, không phải cưỡng bức chấm dứt
} finally {
    pool.shutdown();
}
~~~

Đây là **đoạn tích hợp**: `performOneStep()` và `cleanupResources()` là hàm ứng dụng; cần xử lý `InterruptedException` và `ExecutionException` của `Future.get` theo contract ở phương thức bao quanh. Điều quan trọng là `performOneStep()` phải có chiến lược thoát khi bị interrupt hoặc có timeout riêng; vòng kiểm tra chỉ chạy **sau khi** nó trả về.

Trong production, sau `shutdown()` có thể cần `awaitTermination` có deadline và fallback `shutdownNow`, cùng báo cáo nếu còn tác vụ chưa kết thúc. Đừng dựa vào một Future cancelled để cam kết mọi file/connection đã được đóng.

## 11. Ví dụ độc lập 3: cancel Future, worker tự xử lý interrupt

Ví dụ này dùng `CountDownLatch` để biết task đã chạy, rồi gọi `cancel(true)`. Worker đang chờ bằng `Thread.sleep()`; khi bị interrupt, nó đi vào catch, ghi nhận yêu cầu hủy và kết thúc. Caller không dùng `Future.get()` để chứng minh cleanup; thay vào đó dùng một latch kết thúc riêng.

Lưu **FutureCancellationDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FutureCancellationDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean detected = new AtomicBoolean(false);

        try {
            Future<?> future = pool.submit(() -> {
                try {
                    started.countDown();
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    detected.set(true);
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });

            started.await();
            boolean cancelled = future.cancel(true);
            finished.await();

            System.out.println("cancelled=" + cancelled);
            System.out.println("futureCancelled=" + future.isCancelled());
            System.out.println("workerDetected=" + detected.get());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac FutureCancellationDemo.java
java FutureCancellationDemo
~~~

Output khi task chưa hoàn tất trước yêu cầu hủy và không có interruption bên ngoài:

~~~text
cancelled=true
futureCancelled=true
workerDetected=true
~~~

**Cách lập luận:**
1. `started.await()` cho main biết task đã chạy tới điểm báo hiệu. Worker chuẩn bị `sleep()`, có thể đã ngủ hoặc chưa.
2. `future.cancel(true)` yêu cầu interrupt worker khi task đang chạy. Trong tình huống này, `sleep` phát hiện interrupt dù tín hiệu đến trước hay trong lúc ngủ, rồi ném `InterruptedException`.
3. Worker bắt exception, ghi `detected=true`, khôi phục cờ và gọi `finished.countDown()` trong finally.
4. `finished.await()` bảo đảm caller **chờ logic cleanup trong Runnable đã đi qua finally**; điều này cung cấp sự quan sát ổn định cho biến detected. Cũng cần phân biệt `finished.countDown()` với thread pool worker thực sự kết thúc: pool có thể giữ worker để thực hiện task tiếp theo.
5. `pool.shutdown()` ngừng nhận task mới; `awaitTermination` giới hạn thời gian chờ executor đóng. `shutdownNow()` chỉ nỗ lực ngắt các worker còn chạy, không cưỡng bức kết thúc mọi task.

**Lưu ý về lịch chạy:** Nếu nguồn tác vụ cực ngắn đã hoàn tất **trước** khi caller `cancel(true)`, `cancel` có thể trả false. Ví dụ cố định lựa chọn tác vụ sleep dài và latch bắt đầu nên trong thực thi bình thường task chưa thể hoàn thành trước khi main yêu cầu hủy. Đây không phải benchmark hay cam kết định thời tuyệt đối trên mọi môi trường.

## 12. ExecutorService.shutdown và shutdownNow khác nhau

| API | Ý nghĩa | Có interrupt các task đang chạy? |
|---|---|---|
| `shutdown()` | Ngừng nhận tác vụ mới; các task đã submit tiếp tục theo chính sách executor | **Không** yêu cầu interrupt chỉ do shutdown |
| `shutdownNow()` | Nỗ lực ngừng các task đang chạy và ngăn/thu hồi task chưa khởi động | **Thường** dùng interrupt, nhưng không cam kết mọi task lập tức dừng |
| `awaitTermination(timeout, unit)` | Chờ executor kết thúc tối đa thời gian định trước | Không tự thực hiện cancellation của worker |
| `Future.cancel(true)` | Yêu cầu hủy **một task** và có thể interrupt thread đang chạy task đó | Có, khi khả thi và task còn chạy |
| `Future.cancel(false)` | Yêu cầu hủy nhưng không cố interrupt thread đang chạy | Không |

`shutdownNow()` trả về danh sách những task đang chờ chưa bắt đầu trong executor phù hợp. Nó **không phải danh sách toàn bộ tác vụ chưa hoàn tất** trong mọi ý nghĩa nghiệp vụ. Một số task đang chạy vẫn có thể tiếp tục nếu không phản hồi interrupt.

### Một mẫu shutdown có deadline

~~~java
pool.shutdown();
try {
    if (!pool.awaitTermination(3, TimeUnit.SECONDS)) {
        pool.shutdownNow();
        if (!pool.awaitTermination(3, TimeUnit.SECONDS)) {
            System.err.println("Some tasks did not terminate");
        }
    }
} catch (InterruptedException e) {
    pool.shutdownNow();
    Thread.currentThread().interrupt();
}
~~~

Đây là **đoạn trích** bên trong method có `ExecutorService pool` và import `TimeUnit`, phù hợp hướng dẫn shutdown executor thường gặp trong JDK. Việc giữ lại interrupt status của thread điều phối khi bắt InterruptedException là quan trọng; song cũng phải ghi nhận nếu worker vẫn không kết thúc. Một chính sách production có thể dùng deadline toàn cục thay vì hai khoảng chờ độc lập.

## 13. Timeout và interrupt không phải cùng một điều

Một thread có thể muốn dừng vì:
- **Interrupt:** một bên khác chủ động gửi yêu cầu hủy.
- **Timeout:** một phép chờ vượt quá thời gian giới hạn.
- **Business cancellation:** trạng thái nghiệp vụ không còn yêu cầu thực thi.
- **Shutdown:** lifecycle executor/application đang kết thúc.

Các trường hợp có thể liên quan nhau nhưng không giống nhau:
- `Future.get(100, MILLISECONDS)` ném `TimeoutException` chỉ cho biết **caller đã chờ quá thời hạn**, không nói task đã bị hủy.
- `Thread.sleep(100)` hoàn tất bình thường vì đủ thời gian ngủ không có nghĩa thread nhận yêu cầu hủy.
- `lock.tryLock(100, MILLISECONDS)` trả false nghĩa là **không lấy được lock trong hạn**, không phải do thread bị interrupt. Nếu bị interrupt, nó có thể ném `InterruptedException`.
- `Future.cancel(true)` có thể đánh dấu Future cancelled, nhưng các side effects đã thực hiện không tự rollback.
- `Thread.interrupt()` có thể phát tín hiệu ngay cả khi worker chưa tới điểm kiểm tra, và việc phát hiện/dừng có độ trễ.

Để yêu cầu deadline **toàn nghiệp vụ**, cần truyền ngân sách thời gian qua nhiều bước, đặt timeout tương ứng cho I/O, hàng đợi, khóa và phần xử lý cần thiết. Một interrupt flag riêng không cung cấp deadline thời gian thực.

## 14. Thread.stop không phải giải pháp thay thế

`Thread.stop()` là API không an toàn đã bị loại khỏi thực hành hiện đại và trong JDK hiện hành bị đánh dấu/triển khai theo hướng **không còn hỗ trợ**; với JDK 17+, không dùng nó để cưỡng bức thread ngừng. Lý do căn bản của việc dừng bất đồng bộ là có thể phá vỡ bất biến dữ liệu khi code đang giữ tài nguyên hoặc đang cập nhật một nửa trạng thái.

`Thread.suspend()`/`resume()` cũng không phải cách hủy đáng tin: có thể làm treo thread đang sở hữu monitor, gây deadlock. Không xây dựng cơ chế hủy bằng các API nguy hiểm/đã deprecated này.

Giải pháp chuẩn là **cooperative cancellation**:
1. Task có điều kiện hủy được kiểm tra tại những điểm an toàn.
2. Blocking operation phải có đường interrupt/timeout/cancel phù hợp.
3. Task có cleanup và giao thức hoàn tất rõ ràng.
4. Caller đợi completion hoặc xử lý tình huống task chưa dừng sau deadline.

## 15. Interrupt và trạng thái thread không phải một bản đồ một-một

Các trạng thái `NEW`, `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING` và `TERMINATED` mô tả trạng thái execution/thread-lifecycle ở mức JVM. **Interrupted status là một thuộc tính khác**. Một thread có thể:
- Ở `RUNNABLE` với interrupted status true mà vẫn đang tính toán.
- Bị interrupt khi đang `TIMED_WAITING` ở sleep và đi qua InterruptedException.
- Chờ lấy monitor intrinsic (`BLOCKED`), nhận interrupt request nhưng vẫn phải chờ monitor.
- Đã xử lý interrupt và thoát khỏi Runnable, chuyển `TERMINATED` sau khi cleanup.

`interrupt()` không phải một lệnh “setState(TERMINATED)”. Khi debug, nhìn cả stack trace, trạng thái interrupt, điều kiện chờ và nơi xử lý exception; chỉ thấy `isInterrupted()==true` không chứng minh nhiệm vụ đã kết thúc.

### Lưu ý với virtual threads trong Java 21+

Virtual threads cũng là `Thread` và có interrupt status, nên các quy tắc cooperative cancellation của Java vẫn áp dụng. Nhưng khả năng một hoạt động blocking cụ thể phản ứng/thoát nhanh phụ thuộc API và phiên bản JDK, không phải mọi I/O, native call hay thư viện đều có cùng cách xử lý.

Virtual thread là daemon theo đặc tính thiết kế; vì vậy nếu ứng dụng tạo nhiều virtual threads nhưng không có cơ chế chờ task hoàn tất, JVM có thể kết thúc trước khi các task hoàn thành. Interrupt không thay thế `Future.get`, `join`, structured lifecycle hoặc đóng executor có kiểm soát. Ví dụ trong bài dùng Java 17+ platform threads để tập trung vào ngữ nghĩa chung của Thread API.

## 16. Hủy ở ranh giới tài nguyên và tính đúng của nghiệp vụ

Cooperative cancellation không đơn giản là “bất cứ khi nào có interrupt thì return ngay”. Bạn cần xác định **safe cancellation point**:

- **Trước khi bắt đầu bước có side effect:** có thể kiểm tra cờ và bỏ qua công việc nếu chưa có gì commit.
- **Trong lúc chờ tài nguyên:** dùng API interruptible và cleanup ownership đúng (không unlock nếu chưa acquire; không release permit nếu chưa lấy).
- **Trong critical section:** phải giữ invariant; không return giữa việc cập nhật hai field mà để trạng thái nửa chừng.
- **Sau khi đã commit side effect ra ngoài:** interrupt không tự rollback email đã gửi, file đã ghi hay giao dịch đã commit. Cần idempotency/compensation protocol riêng nếu muốn hủy nghiệp vụ.
- **Khi giữ lock:** dùng `finally` để unlock kể cả khi exception; tránh hủy bỏ nửa chừng mà không dọn tài nguyên.

Ví dụ với semaphore:

~~~java
boolean acquired = false;
try {
    semaphore.acquire();       // có thể bị interrupt trước khi lấy permit
    acquired = true;
    performWork();             // logic ứng dụng
} finally {
    if (acquired) {
        semaphore.release();   // chỉ trả permit mình đã lấy
    }
}
~~~

Đây là **đoạn trích** trong method có `Semaphore semaphore` và `performWork()`, method cần khai báo/handle InterruptedException. Tại sao dùng biến `acquired`? Nếu `acquire` ném InterruptedException **trước khi được cấp permit**, code không được release permit của thread khác. Cùng nguyên tắc áp dụng cho `ReentrantLock.lockInterruptibly()`: chỉ unlock sau khi đã acquire.

### Không ép hard stop với tài nguyên chung

Trong một hệ thống nhiều threads, việc một thread bị buộc dừng giữa lúc giữ monitor hoặc thay đổi state có thể làm các threads khác quan sát trạng thái bất nhất hoặc chờ mãi. Thiết kế cancellation tốt luôn kèm **ownership + cleanup + completion protocol**, không chỉ flag.

## 17. Tránh các lỗi phổ biến

**Lỗi 1 — Gọi interrupt là coi worker đã dừng.** Không có bảo đảm. Cần worker hợp tác và caller quan sát completion bằng join/Future/latch thích hợp.

**Lỗi 2 — Catch InterruptedException rồi để trống.** Nhiều API clear cờ trước khi throw; nuốt exception có thể khiến worker tiếp tục vô hạn và bỏ qua yêu cầu hủy.

**Lỗi 3 — Gọi Thread.interrupted() thay isInterrupted() trong polling rồi tiếp tục.** Static method clear cờ. Nếu không thoát ngay, tín hiệu bị mất.

**Lỗi 4 — Nghĩ `Thread.interrupted()` kiểm tra object đứng trước dấu chấm.** Đây là static method kiểm tra current thread, không phải Thread tùy ý.

**Lỗi 5 — Nghĩ `Thread.sleep()` dừng được worker đang tranh synchronized monitor.** Sleep không nhả monitor đang giữ và không làm acquire intrinsic lock trở thành interruptible.

**Lỗi 6 — Quên finally khi một blocking method bị hủy.** Tài nguyên đã lấy trước đó có thể bị giữ, gây leak hoặc deadlock.

**Lỗi 7 — Gọi unlock/release khi acquire bị interrupt trước thành công.** Có thể ném IllegalMonitorStateException hoặc tăng permit sai; theo dõi ownership đúng.

**Lỗi 8 — Dùng Future.isCancelled như chứng cứ side effects đã chấm dứt.** Future cancellation và actual worker termination là hai mốc khác nhau.

**Lỗi 9 — Cho rằng get(timeout) timeout sẽ hủy task.** Timeout chỉ áp dụng cho caller đợi kết quả. Muốn hủy, cần cancel và protocol worker phù hợp.

**Lỗi 10 — Retry vô hạn sau interrupt.** Tác vụ bị yêu cầu hủy có thể không bao giờ dừng; cần kiểm tra cancellation/deadline và không retry theo cách bỏ tín hiệu.

**Lỗi 11 — Dùng interrupt để xử lý một API không hỗ trợ interrupt.** Đọc contract cụ thể; dùng timeout, close hoặc cancellation hook đúng loại tài nguyên.

**Lỗi 12 — Dùng Thread.stop hoặc suspend/resume.** Chúng không bảo đảm trạng thái/tài nguyên an toàn và không phải cách quản lý concurrency hiện đại.

**Lỗi 13 — Nghĩ sleep/yield tạo happens-before.** Không. Đồng bộ visibility cần Lock/volatile/latch/join/Future hoặc protocol JMM thích hợp.

**Lỗi 14 — Cho rằng khôi phục cờ và tiếp tục tính vô hạn là đã xử lý hủy.** Restore giúp bảo tồn tín hiệu, nhưng còn phải **thoát/chuyển quyết định**. Nếu code tiếp tục bỏ qua flag, cancellation vẫn không diễn ra.

**Lỗi 15 — Lẫn interrupt giữa caller và callee.** Nếu main bị interrupt trong `future.get`, task không mặc nhiên bị hủy; nếu worker bị interrupt thì main không nhất thiết nhận InterruptedException của chính main.

**Lỗi 16 — Hủy giữa nhiều cập nhật nghiệp vụ rồi mặc định rollback.** Interrupt không phải transaction. Các bất biến và cơ chế compensation cần thiết kế riêng.

## 18. Quy trình thiết kế cancellation an toàn

~~~text
Caller không cần kết quả nữa
       |
       v
Yêu cầu cancel: worker.interrupt() / future.cancel(true)
       |
       v
Worker có điểm kiểm tra hay API chờ interruptible?
       |                         \
      Có                          Không
       |                           |
       v                           v
Phát hiện tín hiệu           Có thể tiếp tục chạy
       |                    → cần timeout/close/cancel hook
       v
Dừng ở safe point, giữ invariant
       |
       v
finally/try-with-resources cleanup
       |
       v
Runnable/Callable thực sự kết thúc
       |
       v
Caller đợi/ghi nhận completion và kiểm tra outcome
~~~

Tại từng bước, hỏi thêm:

1. **Ai sở hữu task?** Caller có còn giữ Thread/Future để yêu cầu hủy và quan sát kết thúc không?
2. **Ai có quyền interrupt?** Có thể người điều phối chỉ được phát yêu cầu theo contract của executor, không nên tự interrupt thread pool workers tùy tiện.
3. **Task đang làm CPU hay chờ blocking?** CPU work cần polling định kỳ; blocking work cần API hỗ trợ interrupt/timeout.
4. **Khi bắt InterruptedException thì sao?** Ném tiếp, restore flag và return, hay xử lý chính sách cụ thể có lý do?
5. **Tài nguyên nào cần cleanup?** Lock, permit, stream, file, connection, temp state.
6. **Các side effects đã commit chưa?** Có cần idempotency, compensation hay kết quả “partial success” không?
7. **Ai chờ hoàn tất?** Có deadline và logic khi worker vẫn chạy sau khi yêu cầu hủy không?
8. **Có metric và log phân biệt timeout, cancellation, failure không?** Không gộp mọi kết quả vào “lỗi hệ thống”.

## 19. Bài tập tự kiểm tra

**Câu 1.** Thread A tính toán trong vòng `while(true)` không kiểm tra interrupt. B gọi `A.interrupt()`. A có bắt buộc dừng không?

**Đáp án:** Không. Interrupt không cưỡng bức kết thúc CPU code. A phải chủ động kiểm tra flag/điểm hủy hoặc gọi API phản ứng với interrupt.

**Câu 2.** Khi `Thread.sleep()` ném InterruptedException, `Thread.currentThread().isInterrupted()` trong catch thường cho gì?

**Đáp án:** False đối với `Thread.sleep` theo hợp đồng: cờ được clear trước khi ném. Nếu muốn bảo tồn tín hiệu khi không ném tiếp, gọi `Thread.currentThread().interrupt()`.

**Câu 3.** `Thread.interrupted()` và `Thread.currentThread().isInterrupted()` khác gì?

**Đáp án:** Cả hai kiểm tra current thread nếu gọi đúng như trên, nhưng `Thread.interrupted()` **clear flag**, còn `isInterrupted()` không clear.

**Câu 4.** Main bị interrupt khi đang `worker.join()`. Điều này chứng minh worker đã bị interrupt chưa?

**Đáp án:** Không. Interruption tác động thread đang chờ join là main. Muốn yêu cầu hủy worker, main phải dùng một hành động riêng phù hợp với ownership/protocol.

**Câu 5.** `Future.get(200, MILLISECONDS)` bị timeout. Task đã tự bị cancel chưa?

**Đáp án:** Chưa. Đây chỉ là timeout của thao tác chờ kết quả. Caller có thể chọn `future.cancel(true)` nếu muốn phát yêu cầu hủy.

**Câu 6.** Khi `lock.lockInterruptibly()` bị interrupt trước khi lock thành công, có gọi `lock.unlock()` trong finally không?

**Đáp án:** Không. Chỉ unlock nếu đã acquire. Viết try/finally **sau** lời acquire thành công hoặc dùng một biến ghi nhận ownership.

**Câu 7.** Vì sao `shutdownNow()` không bảo đảm mọi task của executor đã kết thúc?

**Đáp án:** Nó chỉ cố dừng/interrupt workers và trả về các task chưa bắt đầu trong phạm vi contract. Worker không phản hồi interruption có thể vẫn chạy.

**Câu 8.** Một thread bị interrupt sau khi ghi file nhưng trước khi gửi event. Có tự rollback file không?

**Đáp án:** Không. Interrupt chỉ là tín hiệu hủy; file write là side effect riêng. Phải có protocol nghiệp vụ cho partial completion, idempotency và recovery.

## 20. Tài liệu chính thức

- **Thread (Java 21), mục Thread Interruption:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html
- **Future (Java 21), mục cancel:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html
- **ExecutorService (Java 21), mục shutdown/shutdownNow/awaitTermination:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html
- **ReentrantLock (Java 21), lockInterruptibly:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html
- **BlockingQueue (Java 21):** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/BlockingQueue.html
- **JLS `17, Threads and Locks:** https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html

**Kết luận:** `interrupt()` chỉ **gửi yêu cầu hủy**, không “giết” thread. Worker phải **hợp tác nhận diện**, chọn điểm dừng an toàn, cleanup và thực sự kết thúc; caller cần quan sát completion. `InterruptedException` thường là cách các API blocking chuyển yêu cầu hủy thành luồng điều khiển, còn `Future.cancel(true)` và `shutdownNow()` cũng chỉ nỗ lực hủy. Tính đúng của hệ thống đến từ **giao thức cancellation, ownership, cleanup và invariant**, không đến từ một lời gọi interrupt đơn lẻ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-002">interrupt(), isInterrupted() và Thread.interrupted()</a>

<details>
<summary>Xem chi tiết</summary>

# interrupt(), isInterrupted() và Thread.interrupted() trong Java

## 1. Interrupt là tín hiệu phối hợp, không phải lệnh buộc thread dừng

Trong Java concurrency, một thread đôi khi cần yêu cầu thread khác **ngừng công việc đang làm**: người dùng hủy tác vụ, ứng dụng đóng, request vượt deadline, hoặc một nhóm worker không còn cần kết quả. Cơ chế căn bản của Java là **interrupt**.

Điểm dễ hiểu sai nhất: **`thread.interrupt()` không tương đương `Thread.stop()`**. Nó không ép mã Java đang chạy dừng tại một vị trí tùy ý, không rollback cập nhật dữ liệu, và không tự đóng mọi I/O. Interrupt là một **tín hiệu hợp tác**. Thread nhận tín hiệu phải đến điểm nhận biết phù hợp, sau đó thực hiện chính sách hủy, dọn dẹp tài nguyên và kết thúc hoặc tiếp tục theo hợp đồng.

Ba phương thức trọng tâm:

- `thread.interrupt()`: yêu cầu ngắt **thread đối tượng đích**. Thông thường đặt *interrupt status* của thread đích; nếu thread đang chờ trong một phương thức interruptible, có thể khiến phương thức đó kết thúc bằng `InterruptedException` hoặc cơ chế đặc thù được API định nghĩa.
- `thread.isInterrupted()`: **đọc trạng thái ngắt của thread đích** mà **không xóa cờ**.
- `Thread.interrupted()`: phương thức **static** kiểm tra trạng thái ngắt của **thread đang thực thi lời gọi** và **xóa trạng thái đó** khi đọc (test-and-clear). Nó không nhận đối tượng đích.

Bài này thuộc phạm vi **Java concurrency nói chung**, tập trung vào Thread API và phối hợp hủy trong JDK; không sử dụng cơ chế riêng của framework.

### Mục tiêu học tập

Sau bài, bạn cần có khả năng:

1. So sánh ý nghĩa, đối tượng tác động và tác dụng phụ của ba API.
2. Giải thích vì sao `InterruptedException` thường xóa cờ interrupt và khi nào nên khôi phục bằng `Thread.currentThread().interrupt()`.
3. Viết worker kiểm tra interrupt khi tính toán, xử lý hủy khi `sleep`/`wait`/`join`/`Condition.await` bị ngắt.
4. Phân biệt yêu cầu hủy với xác nhận hủy đã hoàn tất; dùng `join`, `Future` hoặc cơ chế phối hợp để chờ an toàn.
5. Nhận diện lỗi nuốt interrupt, vô tình xóa cờ, gọi sai phương thức static, và giả định API blocking nào cũng interruptible.
6. Hiểu những giới hạn của interrupt đối với intrinsic monitor, I/O, executor, cancellation và Java Memory Model.

## 2. Bảng so sánh chính xác ba API

| API | Ai thường gọi? | Trạng thái được xét là của ai? | Có xóa cờ interrupt khi kiểm tra không? | Mục đích |
| --- | --- | --- | --- | --- |
| `target.interrupt()` | Thread yêu cầu hủy hoặc chính thread đó | `target` | Không phải thao tác đọc/xóa cờ; gửi yêu cầu ngắt | Yêu cầu thread đích chú ý tín hiệu |
| `target.isInterrupted()` | Bất kỳ thread có reference đến `target` | `target` | **Không** | Quan sát cờ hiện có |
| `Thread.interrupted()` | Thread **đang chạy** | Thread **hiện tại** | **Có** | Kiểm tra và tiêu thụ cờ |
| `Thread.currentThread().isInterrupted()` | Thread đang chạy | Thread hiện tại | **Không** | Tự kiểm tra mà giữ nguyên cờ |

### 2.1. Cú pháp gây nhầm lẫn

~~~java
// Minh họa, không phải chương trình độc lập.
Thread worker = ...;

worker.interrupt();     // Gửi ngắt tới worker.
worker.isInterrupted(); // Đọc cờ của worker, không xóa.

Thread.interrupted();   // Đọc VÀ XÓA cờ của thread đang gọi dòng này.
~~~

**Không được suy luận từ đối tượng reference mà phương thức static kiểm tra đối tượng đó.** Dù Java cho phép gọi static method qua expression thuộc kiểu `Thread`, viết `worker.interrupted()` là cú pháp gây hiểu nhầm và vẫn gọi phương thức static cho **current thread**, không phải kiểm tra worker. Luôn viết `Thread.interrupted()` khi bạn chủ động muốn test-and-clear **chính mình**.

`interrupt()` trả về `void`, nên không có boolean “hủy thành công” được trả từ lời gọi. `isInterrupted()` và `interrupted()` trả boolean về **trạng thái cờ tại thời điểm quan sát**, không phải kết luận nghiệp vụ đã dừng đúng cách.

## 3. Cờ interrupt và quá trình được nhận biết

Một thread có trạng thái interrupt. Một câu chuyện thông thường:

~~~text
Thread A:                         Thread B:
  target.interrupt()  --------->     cờ interrupt được đặt
                                     |
                                     +--> polling isInterrupted()
                                     |      thấy true, tự dọn dẹp, thoát
                                     |
                                     +--> sleep/wait/join/await()
                                            bị ngắt bằng
                                            InterruptedException
                                            (trạng thái thường đã clear)
~~~

Điều gì xảy ra chính xác phụ thuộc vào **thời điểm thread ở đâu** và **API nó đang thực thi**:

- Nếu đang chạy vòng tính toán bình thường, thread không tự bị Java ném exception vào một lệnh tính toán tùy ý. Nó có thể tiếp tục chạy đến lúc tự kiểm tra cờ.
- Nếu đang chờ bằng `Thread.sleep`, `Object.wait`, `Thread.join` hoặc `Condition.await` bản interruptible, lời chờ thường phản hồi bằng `InterruptedException` và cờ interrupt bị **xóa theo hợp đồng**.
- Nếu dùng `ReentrantLock.lockInterruptibly()` hoặc `BlockingQueue.take()`, yêu cầu ngắt có thể làm kết thúc việc chờ bằng `InterruptedException`.
- Nếu đang chờ **lấy intrinsic monitor** để vào `synchronized`, interrupt không tự làm thread thoát khỏi trạng thái chờ monitor. Cờ có thể được đặt, nhưng thread chỉ phản ứng khi đến một điểm có thể xử lý nó.
- Nếu đang trong thao tác I/O, phản ứng có thể phụ thuộc API: một số blocking channel của NIO có quy tắc ngắt/đóng channel riêng; không được áp dụng một kết luận đồng loạt cho mọi nguồn I/O.

Vì thế **gửi interrupt** và **thread hoàn tất** là hai sự kiện khác nhau.

### 3.1. Interrupt đến trước lời gọi sleep

Nếu current thread đã có cờ interrupt khi gọi `Thread.sleep(...)`, lời gọi có thể ném `InterruptedException` ngay thay vì ngủ đủ thời lượng. Điều này giúp worker nhận biết yêu cầu hủy dù tín hiệu tới trước điểm chờ.

Không dùng `Thread.sleep(100)` để “đảm bảo” thread khác đã xử lý interrupt. Sleep chỉ ảnh hưởng lịch chạy của **thread đang gọi**, không xác nhận trạng thái của worker.

### 3.2. Interrupt là yêu cầu không phải phép ép hủy

Ví dụ một thread không bao giờ kiểm tra flag và chỉ chạy phép tính vô hạn:

~~~java
// Sai về khả năng hủy, không phải chương trình độc lập.
for (;;) {
    computeNextStep();
}
~~~

Nếu `computeNextStep` không dùng primitive interruptible hoặc tự kiểm tra cờ, việc gọi `interrupt()` từ ngoài có thể **không làm vòng lặp dừng**. Một giao thức hủy tốt phải thiết kế **cooperative cancellation points** ở những chỗ có thể dọn dẹp và thoát an toàn.

## 4. Ví dụ 1 — test-and-clear bằng Thread.interrupted() (Java 17+)

Để hiểu khác biệt mà không phụ thuộc scheduler, ta dùng **chính thread main** làm đối tượng thí nghiệm. Chương trình chủ động xóa cờ ban đầu, đặt lại nó, quan sát và kiểm tra việc xóa cờ.

Tạo tệp `InterruptFlagDemo.java`:

~~~java
public class InterruptFlagDemo {
    public static void main(String[] args) {
        Thread current = Thread.currentThread();

        Thread.interrupted(); // Đảm bảo trạng thái đầu thí nghiệm là clear.
        current.interrupt();

        System.out.println("isInterrupted #1 = "
            + current.isInterrupted());

        System.out.println("interrupted #1 = "
            + Thread.interrupted());

        System.out.println("isInterrupted #2 = "
            + current.isInterrupted());

        System.out.println("interrupted #2 = "
            + Thread.interrupted());
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac InterruptFlagDemo.java
java InterruptFlagDemo
~~~

Stdout khi không có thread bên ngoài can thiệp vào trạng thái của main:

~~~text
isInterrupted #1 = true
interrupted #1 = true
isInterrupted #2 = false
interrupted #2 = false
~~~

Giải thích theo thứ tự:

1. `current.interrupt()` đặt cờ interrupt của current thread.
2. `current.isInterrupted()` chỉ **đọc** trạng thái, nên kết quả lần đầu là true và cờ vẫn còn.
3. `Thread.interrupted()` trả về true **đồng thời xóa cờ**.
4. Lần `isInterrupted()` sau đó trả về false.
5. Lần `Thread.interrupted()` tiếp theo trả về false vì cờ đã bị tiêu thụ ở bước 3.

**Điểm thực hành:** Đừng chèn `Thread.interrupted()` vào logging hoặc một câu điều kiện chỉ cần quan sát. Vì nó xóa cờ, code ở tầng gọi sau đó có thể mất tín hiệu hủy.

## 5. Ví dụ 2 — gọi interrupt vào thread đang sleep (Java 17+)

Đây là ví dụ nhận tín hiệu qua `InterruptedException`. Dùng `CountDownLatch` để bảo đảm main chỉ gửi interrupt sau khi worker đã bắt đầu đoạn code liên quan; không dùng `Thread.sleep` để đoán thời điểm.

Tạo tệp `InterruptSleepDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterruptSleepDemo {
    public static void main(String[] args)
            throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean caught = new AtomicBoolean();
        AtomicBoolean restored = new AtomicBoolean();

        Thread worker = new Thread(() -> {
            entered.countDown();

            try {
                for (;;) {
                    Thread.sleep(1_000);
                }
            } catch (InterruptedException ex) {
                caught.set(true);

                // Vì sleep thường đã xóa cờ, ta khôi phục
                // để code tầng gọi/cleanup có thể quan sát.
                Thread.currentThread().interrupt();
                restored.set(
                    Thread.currentThread().isInterrupted()
                );
            }
        }, "interruptible-worker");

        worker.start();
        entered.await();

        worker.interrupt();
        worker.join();

        System.out.println("caught = " + caught.get());
        System.out.println("restored = " + restored.get());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac InterruptSleepDemo.java
java InterruptSleepDemo
~~~

Đầu ra khi worker không bị lỗi ngoài dự kiến:

~~~text
caught = true
restored = true
~~~

**Vì sao không có race làm thay đổi output?**

- Worker đếm latch trước khi đi vào vòng sleep; main đợi latch rồi mới gửi interrupt. Nếu interrupt đến **trước** lần sleep đầu tiên, `sleep` vẫn nhận cờ và ném `InterruptedException` khi bắt đầu; nếu đến trong lúc sleep, lời chờ được ngắt.
- Vòng sleep chạy mãi nếu không nhận interrupt, nên worker không tự hoàn thành trước yêu cầu hủy.
- Worker bắt exception, lưu `caught=true`, đặt lại interrupt flag và lưu `restored=true`.
- Main gọi `join()` để chờ worker kết thúc rồi mới đọc các kết quả `AtomicBoolean`. Output không phụ thuộc thứ tự log của các thread vì chỉ main in.

**Không nên lấy `worker.isInterrupted()` sau khi thread đã `TERMINATED` làm bằng chứng duy nhất rằng nó từng bị interrupt**. Khi thread đã kết thúc, quan sát cờ của đối tượng không thay thế được việc worker ghi nhận trạng thái xử lý hoặc báo kết quả qua kênh phối hợp.

### 5.1. Tại sao catch rồi gọi interrupt lại?

Một số API chờ như `Thread.sleep` ném `InterruptedException` và **clear interrupt status**. Nếu method hiện tại thuộc `Runnable.run()` không thể khai báo `throws InterruptedException`, một mẫu thực tế là:

~~~java
// Mẫu Runnable, không phải chương trình độc lập.
try {
    performInterruptibleWork();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt(); // Restore flag.
    return; // Dừng tác vụ hoặc bắt đầu cleanup theo hợp đồng.
}
~~~

Khôi phục cờ để tầng cao hơn hoặc code dọn dẹp phù hợp có thể quan sát yêu cầu hủy; **không** vì thế mà tiếp tục làm side effect như tác vụ chưa hề bị hủy. Nếu phương thức có thể khai báo `throws InterruptedException`, việc truyền exception lên thường rõ ràng hơn.

Một trường hợp khác là tầng hiện tại **xử lý hoàn toàn** interrupt theo giao thức của nó và cố ý tiếp tục; khi đó việc giữ hay xóa flag phải được **tài liệu hóa có chủ đích**, không để mất tín hiệu vì một catch rỗng.

## 6. Ví dụ 3 — vòng tính toán tự kiểm tra isInterrupted() (Java 17+)

Với công việc CPU-bound không gọi `sleep`, `wait` hoặc `take`, thread phải tự kiểm tra interrupt để thoát. Cách đơn giản là dùng `!Thread.currentThread().isInterrupted()`.

Tạo tệp `InterruptPollingDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterruptPollingDemo {
    public static void main(String[] args)
            throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean noticed = new AtomicBoolean();

        Thread worker = new Thread(() -> {
            started.countDown();

            while (!Thread.currentThread().isInterrupted()) {
                Thread.onSpinWait();
            }

            noticed.set(true);
        }, "polling-worker");

        worker.start();
        started.await();

        worker.interrupt();
        worker.join();

        System.out.println("noticed = " + noticed.get());
        System.out.println("finished = " + !worker.isAlive());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac InterruptPollingDemo.java
java InterruptPollingDemo
~~~

Đầu ra:

~~~text
noticed = true
finished = true
~~~

Worker không dựa vào API chờ ném exception mà chỉ kiểm tra cờ. Main gửi tín hiệu rồi `join()` để xác nhận vòng đã thoát.

**Không dùng đoạn spin vô hạn này như worker production điển hình.** `Thread.onSpinWait()` chỉ là gợi ý tối ưu cho vòng spin, không tạo trạng thái chờ tiết kiệm CPU, không thay thế blocking queue và không tự tạo bảo đảm đồng bộ. Trong ứng dụng có task thật, nên kiểm tra cờ giữa các đơn vị công việc, hoặc dùng primitive blocking phù hợp để tránh đốt CPU.

### 6.1. Nếu gọi Thread.interrupted() trong điều kiện vòng lặp?

Cũng có thể viết:

~~~java
// Mẫu minh họa, không phải chương trình độc lập.
while (!Thread.interrupted()) {
    doSmallUnit();
}
~~~

Khi phát hiện cờ true, `Thread.interrupted()` **đồng thời xóa cờ**. Vòng sẽ thoát, nhưng nếu code sau đó cần thấy cờ, nó có thể không thấy nữa. Thường thích hợp hơn để **tự kiểm tra không tiêu thụ cờ** bằng `isInterrupted()`. Chỉ dùng `Thread.interrupted()` khi có lý do rõ ràng muốn xử lý và xóa một tín hiệu đã nhận.

## 7. Một ví dụ về phương thức static dễ gọi sai

Một đoạn tưởng như hỏi “worker đã bị interrupt chưa”:

~~~java
// Minh họa sai ngữ nghĩa; không phải chương trình độc lập.
Thread worker = new Thread(() -> doWork());

boolean result = worker.interrupted(); // KHÔNG kiểm tra worker!
~~~

`interrupted()` là static method: nó kiểm tra/xóa cờ của **thread chạy đoạn mã trên**, không phải worker. Kết quả tùy trạng thái của caller.

Viết đúng:

~~~java
// Minh họa đúng; không phải chương trình độc lập.
boolean workerFlag = worker.isInterrupted();
boolean myFlagWithoutClear =
    Thread.currentThread().isInterrupted();
boolean myFlagWithClear = Thread.interrupted();
~~~

Khi debug, ghi chú cờ thuộc về **thread nào** và ở **thời điểm nào**. Một flag có thể bị xóa bởi chính thread nhận khi API interruptible ném `InterruptedException` hoặc khi gọi `Thread.interrupted()`. Một lần đọc `isInterrupted() == false` không chứng minh worker chưa từng bị gửi ngắt.

## 8. InterruptedException và hợp đồng xử lý đúng

### 8.1. Truyền lên hoặc khôi phục cờ

Hai mẫu nên nhớ:

**Mẫu A — truyền yêu cầu hủy lên caller có hợp đồng throws:**

~~~java
// Mẫu phương thức, không phải chương trình độc lập.
void doStep() throws InterruptedException {
    Thread.sleep(100);
    // Chỉ thực hiện phần tiếp nếu chưa bị ngắt tại sleep.
}
~~~

**Mẫu B — không thể khai báo throws trong Runnable:**

~~~java
// Mẫu Runnable, không phải chương trình độc lập.
Runnable task = () -> {
    try {
        doStep();
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        // Có thể cleanup, rồi return.
        return;
    }
};
~~~

Nếu method chứa code đã thực hiện một số side effect trước khi interrupt, caller cần biết trạng thái “hoàn thành một phần” theo hợp đồng. Interrupt không tự rollback file write, thay đổi bộ nhớ hoặc truy vấn remote.

### 8.2. Vì sao catch rỗng gây nguy hiểm?

~~~java
// Sai trong nhiều giao thức hủy; không phải chương trình độc lập.
try {
    blockingCall();
} catch (InterruptedException ignored) {
    // Bỏ qua; tiếp tục như chưa nhận yêu cầu hủy.
}
~~~

Lời chờ thường đã xóa cờ trước khi ném exception. Nếu catch rỗng, code bên ngoài không còn cờ để nhận biết, tạo **lost cancellation**. Ứng dụng có thể không dừng lúc shutdown hoặc task kéo dài vượt deadline.

Không phải lúc nào cũng buộc restore: một tầng quản lý chuyên biệt có thể chủ động xử lý ngắt và tiếp tục theo hợp đồng. Nhưng **nuốt không suy nghĩ** là lỗi phổ biến.

### 8.3. Không nhầm exception với một lỗi nghiệp vụ thông thường

`InterruptedException` nói rằng **một thao tác chờ bị ngắt**, không chứng minh kết quả trước đó của toàn task đã bị rollback và không phải lúc nào cũng biểu thị một bug. Thường nó là một phần của cancellation/control flow.

Khi worker bị `InterruptedException`, chọn chính sách rõ:

1. Đóng hoặc release tài nguyên theo ownership phù hợp.
2. Bảo toàn invariant trạng thái (không giữ khóa vô hạn, không để dữ liệu nửa chừng).
3. Trả kết quả hủy/thất bại qua API task nếu cần.
4. Kết thúc, truyền exception hoặc khôi phục cờ theo lớp abstraction đang xử lý.

Trong code chứa `Lock`, luôn dùng `finally` để unlock nếu acquire thành công, bất kể task bình thường hay bị interrupt.

## 9. API blocking nào phản ứng với interrupt?

| Tình huống | Cơ chế thường thấy trên Java 17+ | Lưu ý |
| --- | --- | --- |
| `Thread.sleep(...)` | Ném `InterruptedException` và clear flag | Không tự release monitor mà thread đang giữ |
| `Object.wait(...)` | Ném `InterruptedException`; phải reacquire monitor trước khi hoàn thành lời gọi | Cần giữ monitor đúng object; chờ trong vòng while |
| `Thread.join()` | Thread **gọi join** bị ngắt thì join ném `InterruptedException` | Ngắt waiter, không có nghĩa đã ngắt thread được join |
| `Condition.await()` | Có thể ném `InterruptedException` và trở lại với lock được reacquire | Predicate phải kiểm tra lại trong while |
| `BlockingQueue.take()` | Thường ném `InterruptedException` khi bị ngắt trong chờ | Queue vẫn cần giao thức producer/consumer hợp lệ |
| `CountDownLatch.await()` | Ném `InterruptedException` khi chờ bị ngắt | Không tự biến count về 0 |
| `Semaphore.acquire()` | Có thể ném `InterruptedException` | Không tự hủy những permit đã lấy ở các bước khác |
| `ReentrantLock.lockInterruptibly()` | Chờ lấy lock có thể bị interrupt | Phải unlock nếu acquire thành công |
| `ReentrantLock.lock()` | Không chấm dứt chờ chỉ vì interrupt | Cờ có thể được giữ để xử lý sau |
| Chờ **vào `synchronized`** | Interrupt không tự kết thúc việc chờ monitor | Khác với `Object.wait()` trong monitor |
| `CompletableFuture.cancel(true)` | Không dùng `mayInterruptIfRunning` để điều khiển execution theo hợp đồng API | Không suy ra task underlying đã bị interrupt |
| `Future.cancel(true)` | Yêu cầu hủy và có thể gửi interrupt cho task đang chạy | Chỉ best effort, không bảo đảm side effect dừng tức thì |

**Không suy rộng quá mức về I/O:** Ví dụ một số NIO interruptible channel ném `ClosedByInterruptException` và đóng channel khi thread chờ bị ngắt, trong khi các loại blocking I/O khác có hợp đồng riêng. Kiểm tra chính xác Javadoc của tài nguyên đang dùng.

Một phương thức có tên “blocking” không đồng nghĩa nó sẽ luôn ném `InterruptedException`; cả bộ phận API và thời điểm xử lý ngắt đều quan trọng.

### 9.1. Thread.join() khác interrupt(thread)

Nếu main đang `worker.join()` và một thread khác gọi `main.interrupt()`, **main** có thể nhận `InterruptedException` từ `join()`. Worker không bị main tự động interrupt, nên có thể tiếp tục chạy.

Ngược lại, nếu main gọi `worker.interrupt()` trong khi worker đang chờ, worker có thể nhận interrupt; main vẫn phải `join()` hoặc cơ chế khác nếu cần xác nhận hoàn thành.

Không dùng tên phương thức `join` như bằng chứng công việc thành công; nó chỉ chờ thread kết thúc. Lỗi của task cần được đưa ra kênh riêng nếu caller cần xử lý.

## 10. Ví dụ 4 — Future.cancel(true) yêu cầu hủy task (Java 17+)

Executor quản lý worker thread nên caller thông thường **không giữ reference để tự gọi worker.interrupt()**. Khi submit task, `Future.cancel(true)` là cơ chế yêu cầu hủy. Task vẫn phải hợp tác, ví dụ nhận `InterruptedException` khi sleep.

Tạo tệp `FutureCancellationDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FutureCancellationDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean noticedInterrupt = new AtomicBoolean();

        try {
            Future<?> future = pool.submit(() -> {
                entered.countDown();
                try {
                    for (;;) {
                        Thread.sleep(1_000);
                    }
                } catch (InterruptedException ex) {
                    noticedInterrupt.set(true);
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });

            entered.await();
            boolean requestAccepted = future.cancel(true);
            finished.await();

            System.out.println("cancel accepted = " + requestAccepted);
            System.out.println("future cancelled = " + future.isCancelled());
            System.out.println("worker noticed = " + noticedInterrupt.get());
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println("pool still shutting down");
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac FutureCancellationDemo.java
java FutureCancellationDemo
~~~

Đầu ra thông thường khi task không bị lỗi khác và executor dừng trong 5 giây:

~~~text
cancel accepted = true
future cancelled = true
worker noticed = true
~~~

Phân tích:

- `entered` bảo đảm task đã thực sự chạy và bước vào vùng code trước khi caller hủy.
- `cancel(true)` yêu cầu hủy và cho phép interrupt worker đang chạy. Đây không phải lời hứa chung rằng mọi task có thể bị ép dừng trong một thời gian giới hạn.
- Trong ví dụ, vòng `sleep` phản ứng với interrupt; catch đánh dấu trạng thái, restore flag và thoát.
- `finished.await()` là xác nhận **code cleanup của task đã tới cuối**, khác với riêng `future.isCancelled()`. Caller đọc `noticedInterrupt` sau latch để có kết quả quan sát ổn định.
- `shutdownNow()` là yêu cầu ngắt các worker đang hoạt động và trả danh sách task chưa bắt đầu, không đồng nghĩa đảm bảo mọi task kết thúc ngay; `awaitTermination` chờ thêm theo giới hạn.
- Nếu task bên trong không kiểm tra interrupt hoặc bị kẹt ở API không hủy được, `cancel(true)` có thể đánh dấu future đã hủy trong khi code thực sự vẫn còn chạy.

Đừng dùng `future.isCancelled()` làm bằng chứng một transaction đã rollback, file đã đóng hoặc thao tác mạng đã dừng. Phân biệt **trạng thái Future** và **trạng thái tài nguyên bên ngoài**.

## 11. Memory Model: interrupt và happens-before

Java Memory Model có quy tắc quan trọng: **một hành động interrupt thread đồng bộ theo quan hệ happens-before với điểm thread đích phát hiện ra interrupt** (ví dụ kiểm tra thông qua `isInterrupted()`/`Thread.interrupted()` hoặc nhận `InterruptedException` theo API thích hợp).

Điều này giúp mô hình công bố các cập nhật được thực hiện trước hành động interrupt tới điểm nhận biết trong thread đích. Tuy nhiên, không nên dùng interrupt như **giao thức giao dữ liệu tùy tiện** hoặc thay thế một primitive thông báo trạng thái nghiệp vụ.

Ví dụ cần phân biệt:

~~~java
// Minh họa, không phải chương trình độc lập.
payload = prepareData();
worker.interrupt(); // Tín hiệu hủy và có ràng buộc HB khi worker nhận biết.
~~~

Nếu worker thực sự nhận biết interrupt theo hợp đồng, những hành động trước interrupt của sender có quan hệ HB phù hợp. Nhưng:

- Nếu worker không bao giờ kiểm tra và không vào API interruptible, bạn không có điểm nhận biết để dựa vào.
- Nếu nhiều thread còn cập nhật `payload` đồng thời, cần bảo vệ bằng giao thức đồng bộ riêng.
- Nếu cần truyền công việc/kết quả theo nhiều phiên hoặc nhiều producer, nên dùng `BlockingQueue`, `Future` hoặc cấu trúc dữ liệu có memory-consistency contract rõ ràng.
- Nếu cần đợi worker hoàn tất, `join()`/latch/future vẫn là cơ chế chờ; `interrupt()` chỉ là yêu cầu.

`volatile boolean cancelled` hoặc `AtomicBoolean` cũng có thể là một giao thức cờ hủy khi worker chỉ làm CPU work. Tuy vậy, chỉ cập nhật cờ riêng có thể **không đánh thức** thread đang block trên queue/sleep/wait. Interrupt kết hợp điểm chờ interruptible thường phù hợp hơn trong trường hợp đó.

## 12. Thiết kế interruptible worker đúng cách

### 12.1. Chia task thành đơn vị nhỏ

Để hủy phản hồi nhanh mà không kiểm tra flag từng phép toán, chia công việc thành các bước hữu hạn:

~~~java
// Mẫu xử lý CPU work, không phải chương trình độc lập.
void runTask() {
    while (!Thread.currentThread().isInterrupted()) {
        if (!hasMoreWork()) {
            return;
        }
        processOneBoundedUnit();
    }
}
~~~

Điều kiện để mô hình này phản hồi tốt:

- `processOneBoundedUnit()` cần có thời lượng hữu hạn hoặc điểm kiểm tra bổ sung.
- Nếu method block trên I/O không interruptible, flag có thể được đặt mà thread vẫn kẹt.
- Những side effect đã xảy ra phải có quy tắc idempotency/compensation nếu cần retry.
- Khi thoát cần cleanup tài nguyên, có thể đặt trong `finally` hoặc try-with-resources.
- Nếu API cần báo lý do hủy, cung cấp kết quả phù hợp; không chỉ in log rồi biến mất.

### 12.2. Không tự dừng thread khác bằng Thread.stop()

`Thread.stop()` không an toàn vì có thể dừng một thread ở chỗ đang giữ lock hoặc đang phá vỡ bất biến, và API này đã deprecated. Lựa chọn đúng thường là **cooperative cancellation** bằng interrupt/flag, kết hợp điều kiện hoàn thành, deadline và giải phóng tài nguyên do worker sở hữu.

### 12.3. Dọn dẹp và interrupt flag

Nếu dọn dẹp có các phép chờ interruptible, việc **khôi phục cờ ngay** rồi gọi các phép chờ có thể khiến chúng ném `InterruptedException` ngay. Cách sắp xếp restore, cleanup và propagate cần theo hợp đồng và tính an toàn của tài nguyên.

Một mẫu đơn giản khi catch ở rìa Runnable:

~~~java
// Mẫu logic, không phải chương trình độc lập.
boolean interrupted = false;
try {
    doInterruptibleWork();
} catch (InterruptedException ex) {
    interrupted = true;
} finally {
    releaseResourcesWithoutUnboundedWait();
    if (interrupted) {
        Thread.currentThread().interrupt();
    }
}
~~~

Đây chỉ là mẫu nếu cleanup không phụ thuộc một phép chờ dài có thể bị ngắt và không có exception khác cần xử lý. Trong ứng dụng thật, phải cân nhắc thứ tự cleanup, exception bổ sung và truyền lại tín hiệu cho tầng sở hữu tác vụ. Điều cốt lõi là **không vô tình làm mất yêu cầu hủy**.

## 13. Các lỗi thường gặp

| Lỗi | Vì sao sai | Cách đúng |
| --- | --- | --- |
| Xem `interrupt()` là force-stop | Worker có thể không hợp tác | Thiết kế điểm kiểm tra và chờ interruptible |
| Dùng `target.interrupted()` để hỏi cờ target | Đây là static method của **current thread** | Dùng `target.isInterrupted()` |
| Gọi `Thread.interrupted()` để “log trạng thái” | Xóa cờ một cách vô ý | Dùng `Thread.currentThread().isInterrupted()` |
| Catch `InterruptedException` rỗng | API chờ thường đã clear flag; mất thông báo hủy | Propagate hoặc restore rồi thoát theo hợp đồng |
| Gọi `interrupt()` rồi giả định task đã dừng | Chỉ mới yêu cầu, task có thể còn chạy | Chờ `join`, latch hoặc lifecycle outcome |
| Cứ thấy cờ true là ném `InterruptedException` thủ công mọi nơi | Có thể làm lệch hợp đồng exception của API | Xử lý theo thiết kế và điểm cancellation phù hợp |
| Gọi `Thread.sleep` để “đợi worker hủy xong” | Sleep không xác nhận hoàn tất | Dùng `join`/Future/CountDownLatch |
| Tưởng interrupt mở được `synchronized` đang bị chặn | Entry vào intrinsic monitor không interruptible | Thiết kế lock ordering hoặc chọn `lockInterruptibly` nếu cần |
| Tưởng interrupt hủy mọi I/O | API I/O có hợp đồng khác nhau | Kiểm tra API, timeout, close tài nguyên |
| Dùng `Future.cancel(false)` rồi mong worker đang chạy nhận interrupt | `mayInterruptIfRunning=false` không yêu cầu interrupt worker đang chạy | Chọn true khi hợp lý; task vẫn phải hợp tác |
| Cho rằng `future.isCancelled()` = cleanup xong | Future state không đồng nhất lifecycle của side effect | Phối hợp xác nhận hoàn tất riêng |
| Sau khi worker đã kết thúc mới đọc flag để suy ra lịch sử | Cờ tại thời điểm sau không phải nhật ký mọi interrupt | Ghi nhận trạng thái trong task, dùng kênh kết quả |
| Tự retry task sau interrupt mà không xét idempotency | Có thể lặp side effect | Thiết kế retry và compensation theo nghiệp vụ |

## 14. Kiểm tra trong thực tế

### 14.1. Kiểm thử hủy có kiểm soát

Không nên viết test dựa trên `Thread.sleep(50)` của main rồi đoán worker chắc đã vào trạng thái chờ. Thay vào đó:

1. Dùng `CountDownLatch` hoặc primitive tương tự để nhận biết worker đã vào đoạn code cần kiểm tra.
2. Gửi `interrupt()` hoặc `Future.cancel(true)`.
3. Chờ xác nhận kết thúc bằng `join`, latch hay Future theo đúng hợp đồng.
4. Kiểm tra task có giữ invariant, release lock, đóng tài nguyên và không bỏ qua yêu cầu hủy.
5. Đặt timeout để test không treo vô hạn nếu code cố tình không hợp tác.
6. Phân biệt “future đã cancel” và “worker thật sự chạm đến cleanup”.
7. Test cả trường hợp interrupt đến trước khi worker vào `sleep` và đến trong lúc worker đang chờ.
8. Với CPU-bound worker, test việc tự kiểm tra flag; với blocking worker, test API thật mà worker dùng.

### 14.2. Quan sát và debug

- Thread dump giúp nhìn trạng thái `RUNNABLE`, `WAITING`, `TIMED_WAITING`, `BLOCKED`; nó không phải lịch sử đầy đủ các lần interrupt.
- Log nên ghi **event nhận interrupt**, lý do cancel và kết quả cleanup, tránh chỉ log boolean cờ có thể bị xóa.
- Với `ExecutorService`, theo dõi thời gian shutdown, task không phản hồi cancellation và tài nguyên còn mở.
- Nếu task không dừng, xác định nó có kiểm tra flag, đang chờ primitive nào, có giữ khóa ở chỗ chặn không và liệu API đó có phản ứng với interrupt hay không.
- Giữ thời gian hủy có thể dự đoán: task chạy I/O dài nên có timeout hoặc cơ chế đóng tài nguyên riêng phù hợp.

### 14.3. Giới hạn của test

Một stress test thành công không chứng minh mọi interleaving có thể xảy ra đều an toàn. Cần lập luận theo Java Memory Model và hợp đồng API. Các ví dụ trong bài cố tình dùng latch và output sau join để **không khẳng định thứ tự scheduler ngẫu nhiên là cố định**.

## 15. Câu hỏi tự kiểm tra

**1.** `worker.interrupt()` có đảm bảo worker kết thúc trước khi method trả về không?

Không. Nó gửi yêu cầu ngắt và có thể làm các lời chờ interruptible phản hồi; worker cần hợp tác và có thể cần dọn dẹp trước khi kết thúc. Dùng `join` hoặc cơ chế khác để chờ.

**2.** `worker.isInterrupted()` khác `Thread.interrupted()` ở đâu?

`worker.isInterrupted()` đọc cờ của worker mà không clear; `Thread.interrupted()` đọc **current thread** và clear cờ đó. Một hàm có receiver, một hàm static.

**3.** Sau khi `Thread.sleep(...)` ném `InterruptedException`, vì sao catch có thể cần `Thread.currentThread().interrupt()`?

Vì sleep thường đã xóa cờ khi ném exception; restore giúp tầng cao hơn nhận biết yêu cầu hủy nếu method không truyền exception lên.

**4.** Nếu worker không gọi bất kỳ API blocking nào và không kiểm tra cờ, interrupt có buộc nó dừng không?

Không. Interrupt là hợp tác; vòng tính toán phải tự kiểm tra cờ hoặc một cơ chế hủy hợp lệ khác.

**5.** Tại sao `Thread.interrupted()` gọi từ thread main không giúp kiểm tra cờ của worker?

Vì đó là static method, luôn tác động tới thread **đang chạy** lời gọi — ở đây là main — bất kể có biến worker nào trong scope.

**6.** Main đang chờ `worker.join()` và chính main bị interrupt. Điều gì xảy ra?

`join()` có thể ném `InterruptedException` cho **main**, không phải tự động gửi interrupt cho worker. Code caller cần xử lý tín hiệu hủy và quyết định có yêu cầu worker dừng riêng hay không.

**7.** `Future.cancel(true)` trả true có chứng minh mọi side effect của task đã dừng hoàn toàn không?

Không. Future có thể được đánh dấu hủy trong khi task chưa phản ứng hoặc cleanup chưa xong. Cần protocol xác nhận nếu business yêu cầu.

**8.** Khi nào nên chọn `Thread.interrupted()` thay vì `isInterrupted()`?

Khi **chủ ý** kiểm tra và tiêu thụ interrupt status của **chính thread hiện tại**, với giao thức cụ thể xử lý tín hiệu đó. Nếu chỉ polling hoặc quan sát không phá trạng thái, ưu tiên `isInterrupted()`.

**Ghi nhớ:** `interrupt()` là **yêu cầu** ngắt thread đích; `isInterrupted()` là **quan sát không xóa**; `Thread.interrupted()` là **kiểm tra và xóa cờ của current thread**. Xây dựng cancellation đúng cần xác định điểm nhận interrupt, xử lý `InterruptedException`, giữ nguyên tín hiệu khi phù hợp, bảo vệ bất biến và chờ task thật sự kết thúc nếu nghiệp vụ yêu cầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-003">InterruptedException và cách xử lý hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# InterruptedException và cách xử lý hợp lệ trong Java

Một thread có thể đang chờ dữ liệu, ngủ, chờ khóa hoặc đợi tác vụ khác. Khi ứng dụng muốn **yêu cầu thread dừng công việc đang làm**, Java thường dùng cơ chế **interrupt**. Đây là một **tín hiệu hợp tác**, không phải lệnh giết thread. Nhiều API chặn (*blocking APIs*) phản hồi tín hiệu ấy bằng cách ném `InterruptedException` để mã ứng dụng có cơ hội hủy công việc, đóng tài nguyên hoặc chuyển thông tin hủy lên tầng gọi.

Vấn đề không chỉ là biết cách `catch (InterruptedException e)`. Cần quyết định **ai sở hữu quyền hủy**, có nên tiếp tục tác vụ hay dừng, trạng thái interrupt của thread đã bị thay đổi thế nào, và bảo đảm dọn tài nguyên trong mọi đường thoát.

Bài này thuộc chủ đề **Interrupt & Hủy**, phạm vi **Java concurrency nói chung (Java 17+)**. Không xét cơ chế hủy của framework Spring.

Sau bài học, bạn sẽ:
- Giải thích sự khác nhau giữa `Thread.interrupt()`, interrupt status và `InterruptedException`.
- Hiểu vì sao một số phương thức chặn ném `InterruptedException` và **xóa trạng thái interrupt** khi ném.
- Biết hai chính sách thông dụng: **propagate** hoặc **re-interrupt rồi thoát**.
- Xử lý đúng ở `Runnable`, `Callable`, vòng lặp worker, `ExecutorService` và `Future.cancel(true)`.
- Phân biệt interrupt với timeout, cancellation, trạng thái hoàn tất và việc đóng tài nguyên.
- Nhận diện các anti-pattern như nuốt ngoại lệ, tiếp tục vô hạn, reset flag vô ý và dùng interrupt như phương thức dừng cưỡng bức.

## 1. Interrupt là tín hiệu hợp tác, không phải Thread.stop()

### 1.1. Interrupt status là gì?

Mỗi đối tượng `Thread` có một trạng thái **interrupted status**. Một thread khác có thể yêu cầu gián đoạn bằng:

~~~java
worker.interrupt();
~~~

Lời gọi này **không đồng nghĩa** worker dừng ngay. Tùy worker đang làm gì:

- Nếu đang chờ trong một API hỗ trợ interrupt như `Thread.sleep(...)`, `Object.wait(...)`, `Thread.join(...)`, `BlockingQueue.take()` hay `CountDownLatch.await()`, lời gọi chặn thường **ném `InterruptedException`** để worker quyết định cách xử lý.
- Nếu đang tính toán CPU và không kiểm tra trạng thái, nó có thể **tiếp tục chạy**.
- Nếu đang gọi API I/O hoặc thư viện bên ngoài, hành vi có thể khác: một số API đáp ứng interrupt theo hợp đồng riêng, một số phép chặn không giải phóng ngay. **Không mặc định mọi thao tác I/O đều bị cắt ngay**.
- Nếu worker đã hoàn tất, lời gọi interrupt không mang ý nghĩa “hủy lại công việc đã xong”.

Hợp đồng căn bản: **bên yêu cầu hủy gửi tín hiệu; bên thực thi hợp tác dừng**. Một giao thức dừng tốt thường kết hợp interrupt với kiểm tra trạng thái, giải phóng tài nguyên, timeout và chờ worker thực sự kết thúc.

### 1.2. Vì sao không dùng Thread.stop()?

`Thread.stop()` là API không an toàn và đã bị deprecated. Việc dừng cưỡng bức có thể làm thread nhả monitor trong lúc bất biến của object mới được cập nhật một phần, để những thread khác quan sát trạng thái không nhất quán. Cách hiện đại là **cooperative cancellation**: thread chủ động thoát khỏi đoạn xử lý ở thời điểm phù hợp.

`Thread.interrupt()` không tự động rollback thay đổi, không gọi `close()` thay mọi resource và không buộc thread thực thi `finally` ngay tại thời điểm signal.

## 2. Phân biệt Thread.interrupt(), isInterrupted() và Thread.interrupted()

Ba API dễ nhầm:

| API | Ai gọi / kiểm tra ai? | Có xóa interrupt status không? |
| --- | --- | --- |
| `someThread.interrupt()` | Gửi yêu cầu gián đoạn đến `someThread` | Không phải thao tác kiểm tra/xóa cờ theo nghĩa của người gọi |
| `someThread.isInterrupted()` | Đọc trạng thái interrupt của `someThread` | **Không** xóa |
| `Thread.interrupted()` | Static method kiểm tra **thread hiện tại** | **Có**, kiểm tra và **xóa** status |

**Điểm quan trọng:** Dù viết `t.interrupted()` vẫn gọi **static method** của `Thread`, nó kiểm tra thread **đang thực thi lời gọi**, không phải `t`. Nên viết đúng `Thread.interrupted()` để tránh hiểu lầm.

Ví dụ Java 17+ chạy độc lập:

~~~java
// InterruptFlagDemo.java
public class InterruptFlagDemo {
    public static void main(String[] args) {
        Thread current = Thread.currentThread();

        System.out.println("Initial: " + current.isInterrupted());
        current.interrupt();

        System.out.println("After interrupt: " + current.isInterrupted());
        System.out.println("Static check: " + Thread.interrupted());
        System.out.println("After static check: " +
            current.isInterrupted());
    }
}
~~~

~~~bash
javac InterruptFlagDemo.java
java InterruptFlagDemo
~~~

Stdout:

~~~text
Initial: false
After interrupt: true
Static check: true
After static check: false
~~~

Lần gọi `Thread.interrupted()` trả `true` vì status đang được đặt, **đồng thời xóa nó**. Sau đó `isInterrupted()` trả `false`.

**Không dùng `Thread.interrupted()` để chỉ “xem thử” một cờ mà vẫn mong caller khác thấy cờ ấy.** Nếu bạn vô ý xóa interrupt status trong một helper, tầng ngoài có thể không biết đã có yêu cầu hủy.

## 3. InterruptedException có ý nghĩa gì?

`InterruptedException` là **checked exception**. Những phương thức có khả năng chờ và phản hồi interrupt thường khai báo `throws InterruptedException`; caller phải **truyền tiếp** hoặc **bắt và xử lý**.

Ví dụ thường gặp trong Java 17:

| API | Vì sao có thể chờ? | Khi bị interrupt lúc chờ |
| --- | --- | --- |
| `Thread.sleep(...)` | Ngủ một khoảng thời gian | Ném `InterruptedException` |
| `Thread.join(...)` | Chờ thread khác kết thúc | Ném `InterruptedException` |
| `Object.wait(...)` | Chờ điều kiện trên monitor | Ném `InterruptedException` |
| `BlockingQueue.put(e)` | Chờ queue có chỗ | Ném `InterruptedException` |
| `BlockingQueue.take()` | Chờ queue có item | Ném `InterruptedException` |
| `CountDownLatch.await()` | Chờ count về 0 | Ném `InterruptedException` |
| `Semaphore.acquire()` | Chờ permit | Ném `InterruptedException` |
| `ReentrantLock.lockInterruptibly()` | Chờ lấy lock, có thể ngắt | Ném `InterruptedException` |
| `Condition.await()` | Chờ signal/predicate | Ném `InterruptedException` |
| `Future.get()` | Chờ tác vụ bất đồng bộ hoàn thành | Có thể ném `InterruptedException` cho **thread đang gọi get** |
| `ExecutorService.awaitTermination(...)` | Chờ executor kết thúc | Ném `InterruptedException` |

**Không phải mọi API đợi đều như vậy.** Ví dụ `ReentrantLock.lock()` không khai báo `InterruptedException` và không thoát chỉ vì caller bị interrupt, còn `lockInterruptibly()` được thiết kế cho trường hợp cần đáp ứng yêu cầu gián đoạn khi chờ khóa. Một số kiểu I/O phản hồi bằng exception khác hoặc cơ chế riêng.

### 3.1. Vì sao cờ thường bị xóa khi ném?

Với `Thread.sleep`, `Object.wait` và `Thread.join`, khi lời gọi phát hiện interrupt và ném `InterruptedException`, interrupt status bị **xóa theo hợp đồng API**. Nhiều phương thức chờ trong `java.util.concurrent` cũng ném ngoại lệ và tiêu thụ trạng thái theo hợp đồng riêng.

Hệ quả: bên nhận `catch (InterruptedException e)` **không được giả định** rằng `Thread.currentThread().isInterrupted()` vẫn là `true`. Đây là lý do mẫu **re-interrupt** tồn tại khi tầng hiện tại không thể truyền ngoại lệ lên.

### 3.2. Ví dụ độc lập kiểm chứng trạng thái trước và sau catch

~~~java
// InterruptedSleepDemo.java
import java.util.concurrent.CountDownLatch;

public class InterruptedSleepDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            try {
                System.out.println("Worker ready");
                ready.countDown();
                Thread.sleep(60_000);
                System.out.println("Unexpected completion");
            } catch (InterruptedException e) {
                System.out.println("Flag in catch: " +
                    Thread.currentThread().isInterrupted());
                Thread.currentThread().interrupt();
                System.out.println("Flag restored: " +
                    Thread.currentThread().isInterrupted());
            } finally {
                System.out.println("Cleanup executed");
            }
        }, "worker");

        worker.start();
        ready.await();
        worker.interrupt();
        worker.join();
        System.out.println("Main finished");
    }
}
~~~

~~~bash
javac InterruptedSleepDemo.java
java InterruptedSleepDemo
~~~

Stdout (không có interrupt bên ngoài lên thread main):

~~~text
Worker ready
Flag in catch: false
Flag restored: true
Cleanup executed
Main finished
~~~

`ready.countDown()` diễn ra trước khi main gọi `worker.interrupt()`. Worker có thể đang `sleep` hoặc chưa kịp vào `sleep`; ở cả hai trường hợp, phép `sleep` phát hiện interrupt và ném. Khi đã ném, status được xóa, nên dòng đầu trong catch in `false`. Mã chủ động gọi `interrupt()` trên thread hiện tại để khôi phục trạng thái. `finally` chạy khi worker thoát scope.

**Lưu ý:** ví dụ không có thao tác chờ thứ hai trong worker sau khi đặt lại flag, vì một blocking call tiếp theo có thể ném ngay nếu nó chú ý đến trạng thái interrupt. Trong thiết kế thực tế, hãy quyết định đường thoát và cleanup rõ ràng thay vì re-interrupt rồi tiếp tục xử lý vô hạn.

## 4. Chính sách hợp lệ thứ nhất: truyền tiếp InterruptedException

Nếu phương thức của bạn **có thể khai báo `throws InterruptedException`**, cách đơn giản và thường tốt nhất là **không nuốt tín hiệu**. Cho exception truyền lên tầng có trách nhiệm quyết định hủy.

~~~java
// Minh họa phương thức trong class ứng dụng:
static String receive(BlockingQueue<String> queue)
        throws InterruptedException {
    return queue.take();
}
~~~

Hàm `receive` ở trên chỉ là đoạn code minh họa, cần `import java.util.concurrent.BlockingQueue;` trong file thật.

### 4.1. Ví dụ chạy được: phương thức truyền tiếp, worker quyết định dừng

~~~java
// PropagateInterruptedDemo.java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

public class PropagateInterruptedDemo {
    private static String receive(BlockingQueue<String> queue)
            throws InterruptedException {
        return queue.take();
    }

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
        CountDownLatch started = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            started.countDown();
            try {
                receive(queue);
                System.out.println("Unexpected message");
            } catch (InterruptedException e) {
                System.out.println("Worker cancelled");
                Thread.currentThread().interrupt();
            } finally {
                System.out.println("Worker cleanup");
            }
        });

        worker.start();
        started.await();
        worker.interrupt();
        worker.join();
        System.out.println("Main done");
    }
}
~~~

~~~bash
javac PropagateInterruptedDemo.java
java PropagateInterruptedDemo
~~~

Stdout:

~~~text
Worker cancelled
Worker cleanup
Main done
~~~

Việc `started.countDown()` chỉ bảo đảm worker đã vào lambda, chưa chắc đang đứng trong `take()`. Nhưng vì queue rỗng và main đặt interrupt, worker sẽ nhận `InterruptedException` trong `take()` dù interrupt đến trước hay trong lúc chờ. Tầng `receive` không chặn thông tin hủy; chỉ tầng `Runnable` phải bắt vì `Runnable.run()` không khai báo checked exception.

### 4.2. Khi cleanup nhưng vẫn muốn truyền exception

~~~java
// Mã minh họa, resource đã mở và work là method có thể ném InterruptedException.
try {
    work();
} finally {
    releaseLocalState();
}
~~~

Nếu `work()` ném `InterruptedException`, khối `finally` vẫn chạy (trong điều kiện JVM tiếp tục thực thi thông thường), sau đó ngoại lệ truyền lên caller. Điều này thường **rõ ràng hơn** việc catch exception rồi âm thầm tiếp tục.

Với resource là `AutoCloseable`, ưu tiên `try-with-resources`, vì Java gọi `close()` lúc thoát scope. Việc đóng resource không tự đảm bảo task đã hoàn thành nghiệp vụ; nếu bị hủy giữa chừng, cần xem xét giao thức rollback/compensation phù hợp.

### 4.3. Có cần re-interrupt khi đã throw tiếp?

Nếu bạn **truyền tiếp chính `InterruptedException`**, thông tin gián đoạn đã được chuyển lên caller qua exception; thường **không cần** khôi phục flag ở tầng trung gian. Caller cuối cùng quyết định việc đặt lại status hay dừng.

Không biến mẫu re-interrupt thành công thức máy móc “catch ở đâu cũng interrupt lại rồi throw” vì điều đó có thể ảnh hưởng các blocking calls của tầng trên. Quan trọng là có **một hợp đồng xử lý nhất quán** và không làm mất yêu cầu hủy.

## 5. Chính sách hợp lệ thứ hai: restore interrupt rồi kết thúc

Một số interface không cho phương thức hiện tại ném checked `InterruptedException`. Ví dụ `Runnable.run()`, `Supplier.get()` hoặc callback không khai báo checked exception. Ở ranh giới ấy, mẫu thường gặp:

~~~java
// Mã minh họa trong Runnable:
try {
    doInterruptibleWork();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
~~~

`doInterruptibleWork()` là tên minh họa, không phải Java API có sẵn. Ý nghĩa:

1. Catch đúng loại `InterruptedException`.
2. **Khôi phục interrupt flag**, để tầng sở hữu thread hoặc code chạy sau có thể biết đã từng có yêu cầu gián đoạn.
3. **Thoát** khỏi công việc hiện tại (return, break vòng worker, hoặc chuyển sang kết thúc theo giao thức), tránh tiếp tục như thể mọi việc bình thường.

### 5.1. Vòng consumer chờ BlockingQueue

~~~java
// Mã minh họa trong Runnable; queue và handle đã khai báo:
while (!Thread.currentThread().isInterrupted()) {
    try {
        Message item = queue.take();
        handle(item);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
    }
}
~~~

Đoạn này không chạy độc lập vì `Message` và `handle` là API giả định. Nếu `handle(item)` thực hiện I/O có thể bị gián đoạn hoặc ném lỗi nghiệp vụ, phải thiết kế chính sách tiếp theo. Việc lặp kiểm tra `isInterrupted()` **không xóa** flag.

**Sai lầm đặc biệt nguy hiểm:**

~~~java
// Anti-pattern: nuốt interrupt rồi tiếp tục vòng lặp.
while (true) {
    try {
        queue.take();
    } catch (InterruptedException ignored) {
        // Không dừng, không thông báo, không restore status.
    }
}
~~~

Sau khi blocking call ném, flag thường đã bị xóa. Vòng tiếp tục chờ và **bỏ qua yêu cầu hủy**. Điều này có thể khiến `shutdownNow()` hoặc cơ chế hủy ứng dụng không đạt kết quả mong muốn.

Một biến thể tệ khác là catch, re-interrupt, rồi **tiếp tục ngay** chờ `take()` trong vòng vô hạn. Vì flag vẫn đặt, API chặn có thể liên tục ném lại, gây vòng lặp lỗi/CPU cao. Nếu lựa chọn restore status, phải đi kèm **đường thoát**.

### 5.2. Nếu phương thức buộc phải trả một giá trị?

Nếu contract không cho ném checked exception, bạn có thể:
- Trả một kết quả biểu diễn **cancelled** rõ ràng nếu API hỗ trợ.
- Khôi phục flag rồi chuyển thông tin lên bằng một kiểu kết quả/exception **đã được API chấp nhận**.
- Để tầng quản lý thread quyết định retry/hủy thay vì tự retry vô hạn.

Không tự tạo giá trị thành công giả (ví dụ trả `0` cho một giao dịch chưa hoàn thành) chỉ để “bắt cho xong ngoại lệ”. Cách chuyển sang unchecked exception có thể phù hợp một số abstraction, nhưng cần hợp đồng rõ ràng; việc wrap bừa `InterruptedException` thành `RuntimeException` và không restore có thể làm mất trạng thái hủy đối với tầng điều phối.

## 6. InterruptedException không phải lúc nào cũng có nghĩa “đã dừng xong”

Một thread bị interrupt **có thể**:
- Bắt exception và thoát đúng cách.
- Bắt exception rồi tiếp tục làm việc.
- Đang ở code không phản hồi interrupt.
- Đang cleanup hoặc thực hiện I/O/transaction chưa hoàn tất.

Vì thế `interrupt()` là **yêu cầu**, không phải **xác nhận đã hoàn thành việc hủy**. Muốn biết worker đã thật sự kết thúc, có thể `join()`, đợi `CountDownLatch`/`Future` hoặc một tín hiệu hoàn tất thích hợp, kèm timeout nếu cần.

Cần phân biệt các trạng thái:

| Sự kiện | Kết luận chắc chắn |
| --- | --- |
| `worker.interrupt()` được gọi | Đã gửi yêu cầu gián đoạn theo hợp đồng Thread |
| `worker.isInterrupted() == true` | Interrupt status đang được đặt tại thời điểm quan sát |
| Blocking call ném `InterruptedException` | Lời gọi đã thoát theo đường gián đoạn |
| `worker.join()` trả về bình thường | Worker đã kết thúc |
| `Future.cancel(true)` trả true | Future được đánh dấu hủy; có yêu cầu interrupt nếu task đang chạy và hỗ trợ |
| `Future.isCancelled() == true` | Future ở trạng thái canceled, **không chứng minh worker đã dừng tức thì** |
| `ExecutorService.shutdownNow()` trả về | Đã thử dừng bằng interrupt và trả danh sách task chưa bắt đầu; **không bảo đảm** worker đã kết thúc |

### 6.1. Interrupt không rollback

Nếu task thực hiện:
1. Ghi tệp hoặc cơ sở dữ liệu.
2. Gửi một request.
3. Chờ response và bị interrupt.

Không thể suy ra công đoạn 1 hoặc 2 đã được tự động đảo ngược. Cancellation là câu chuyện **lifecycle và hợp đồng nghiệp vụ**, không chỉ một exception của thread. Hãy thiết kế transactional boundary, idempotency, retry hoặc compensation theo hệ thống cụ thể.

## 7. Future.cancel(true) và interrupt trong ExecutorService

`ExecutorService.submit(...)` trả một `Future`. Với các implementation thông dụng dựa trên `FutureTask`, `cancel(true)` yêu cầu hủy và **cố gián đoạn thread đang chạy**; nhưng không đảm bảo task dừng ngay. `cancel(false)` không yêu cầu interrupt worker đang chạy.

Sau khi Future được hủy:
- `future.isCancelled()` có thể trả `true`.
- `future.get()` ném `CancellationException` (unchecked), không trả kết quả ngay cả khi mã task đã thực hiện một phần.
- Worker có thể vẫn đang cleanup hoặc chưa phản hồi interrupt.

### Ví dụ Java 17+ phân biệt Future đã hủy với worker hoàn tất

~~~java
// FutureCancellationDemo.java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class FutureCancellationDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);

        try {
            Future<String> future = pool.submit(() -> {
                entered.countDown();
                try {
                    neverReleased.await();
                    return "completed";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "stopped cooperatively";
                } finally {
                    finished.countDown();
                }
            });

            entered.await();
            boolean accepted = future.cancel(true);

            finished.await(); // Đợi task thực sự thoát scope.
            System.out.println("Cancel accepted: " + accepted);
            System.out.println("Future cancelled: " + future.isCancelled());

            try {
                future.get();
            } catch (CancellationException e) {
                System.out.println("get(): CancellationException");
            }
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Worker did not terminate");
            }
        }
    }
}
~~~

~~~bash
javac FutureCancellationDemo.java
java FutureCancellationDemo
~~~

Trong điều kiện chạy bình thường, không có interrupt bên ngoài, stdout:

~~~text
Cancel accepted: true
Future cancelled: true
get(): CancellationException
~~~

Lập luận:
- Main chỉ gọi `cancel(true)` sau khi task đã bắt đầu (`entered.await()`). Task đang hoặc sắp chờ một latch không bao giờ được mở.
- Việc cancel yêu cầu interrupt; task bắt `InterruptedException`, phục hồi flag, rồi rời scope. `finished.await()` xác nhận `finally` đã chạy.
- Future vẫn là **cancelled**, nên `get()` ném `CancellationException` dù callable đã trả chuỗi `"stopped cooperatively"` trong lúc xử lý hủy.
- `pool.shutdownNow()` là bước cleanup executor, không thay thế việc worker tự xử lý interrupt; ví dụ chờ pool kết thúc có giới hạn.

**Điểm cần kiểm tra với API khác:** Không phải mọi loại `Future` hoặc abstraction bất đồng bộ đều triển khai `cancel(true)` bằng cách interrupt thread xử lý. Cần đọc hợp đồng **loại Future cụ thể**; ví dụ `CompletableFuture.cancel(boolean)` có semantics khác về việc interrupt. Không suy từ tham số `true` thành một bảo đảm phổ quát rằng mọi loại async computation bị cắt ngay.

## 8. Shutdown executor đúng cách và xử lý interrupt của thread điều phối

Một service sở hữu `ExecutorService` cần có kế hoạch ngừng nhận task mới và đợi các task đã nhận kết thúc. `shutdown()` không tự dừng task đang đợi vô hạn trên queue hay I/O. `shutdownNow()` cố gián đoạn các task đang chạy, nhưng vẫn cần task hợp tác.

### 8.1. Mẫu graceful shutdown trong Java 17

~~~java
// ExecutorShutdownDemo.java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ExecutorShutdownDemo {
    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.execute(() -> System.out.println("Task finished"));
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                    if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                        System.err.println("Executor still running");
                    }
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
~~~

~~~bash
javac ExecutorShutdownDemo.java
java ExecutorShutdownDemo
~~~

Trong lần chạy bình thường, stdout:

~~~text
Task finished
~~~

Cách đọc:
1. `shutdown()` ngừng nhận task mới, **không** tự chờ.
2. `awaitTermination(...)` chờ có hạn cho executor kết thúc.
3. Khi timeout, `shutdownNow()` yêu cầu interrupt tác vụ đang chạy và trả các task chưa bắt đầu; không bảo đảm mọi worker đã dừng.
4. Nếu **thread đang điều phối shutdown** bị interrupt khi đang chờ, cần bảo toàn tín hiệu bằng `Thread.currentThread().interrupt()` và có chính sách xử lý tiếp. Mẫu này thử `shutdownNow()` trước khi restore flag.

**Lưu ý về JDK:** Trên Java 17, `ExecutorService` không cung cấp `AutoCloseable.close()` theo các phiên bản mới hơn. Quản lý shutdown chủ động như trên thay vì giả định `try (ExecutorService e = ...)` biên dịch được trên mọi JDK.

### 8.2. Điều gì xảy ra khi catch InterruptedException từ awaitTermination?

Đây là interrupt của **thread caller đang chờ executor**, không nhất thiết là interrupt của worker trong pool. Caller phải quyết định: tiếp tục đợi trong một giao thức đặc biệt hay yêu cầu shutdown mạnh hơn, rồi khôi phục status/propagate theo contract. Không được mặc định ngoại lệ cho caller có nghĩa các worker đã chết.

## 9. Interruptible lock, Condition và điểm chờ khác nhau

### 9.1. Lock.lock() so với lockInterruptibly()

`ReentrantLock.lock()` lấy khóa theo cơ chế không thoát bằng `InterruptedException`. Nếu caller đang chờ khóa và bị interrupt, lời gọi thường vẫn tiếp tục đợi cho tới khi nhận khóa (trạng thái interrupt được giữ/khôi phục theo implementation phù hợp). Ngược lại, `lockInterruptibly()` cho phép chờ khóa bị ngắt.

Ví dụ chạy độc lập Java 17+:

~~~java
// InterruptibleLockDemo.java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

public class InterruptibleLockDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch ready = new CountDownLatch(1);

        lock.lock(); // Main giữ khóa.
        try {
            Thread waiter = new Thread(() -> {
                ready.countDown();
                try {
                    lock.lockInterruptibly();
                    try {
                        System.out.println("Unexpected acquisition");
                    } finally {
                        lock.unlock();
                    }
                } catch (InterruptedException e) {
                    System.out.println("Waiter interrupted");
                    Thread.currentThread().interrupt();
                }
            });

            waiter.start();
            ready.await();
            waiter.interrupt();
            waiter.join();
        } finally {
            lock.unlock();
        }

        System.out.println("Main released lock");
    }
}
~~~

~~~bash
javac InterruptibleLockDemo.java
java InterruptibleLockDemo
~~~

Stdout:

~~~text
Waiter interrupted
Main released lock
~~~

Main giữ lock đến sau khi `waiter.join()`, nên waiter không thể lấy khóa. Nó nhận interrupt và `lockInterruptibly()` ném; waiter chỉ gọi `unlock()` khi thật sự lấy khóa thành công. `finally` ở main nhả khóa sau khi waiter kết thúc.

**Cảnh báo:** Không đặt `unlock()` ngoài khối chỉ chạy khi caller đã lấy được khóa. Nếu `lockInterruptibly()` ném trước khi thành công, việc `unlock()` sai ownership có thể ném `IllegalMonitorStateException`.

### 9.2. Condition.await() và Object.wait()

`Object.wait()` phải được gọi khi thread giữ monitor tương ứng; trong khi chờ, nó **nhả monitor** và khi trở về phải lấy lại monitor theo contract. `Condition.await()` thường nhả associated lock khi chờ và lấy lại trước khi trở về, theo hợp đồng condition/lock tương ứng. Cả hai có thể ném `InterruptedException`.

Nếu chờ một predicate, luôn dùng vòng lặp:

~~~java
// Mã minh họa trong class có lock, condition và boolean ready:
lock.lock();
try {
    while (!ready) {
        condition.await();
    }
    consumeReadyState();
} finally {
    lock.unlock();
}
~~~

`consumeReadyState()` là phương thức ứng dụng giả định. Tại đây `condition.await()` có thể ném, nên phương thức bao quanh cần khai báo `throws InterruptedException` hoặc catch đúng chính sách. `finally` nhả lock; không tự đoán tình trạng predicate sau khi bị interrupt.

**Vì sao dùng while?** Có thể có spurious wakeup hoặc state bị thread khác thay đổi trước lúc tiếp tục chạy, nên luôn kiểm tra lại điều kiện.

### 9.3. Thread.sleep không nhả monitor

`Thread.sleep(...)` **không nhả** bất kỳ monitor nào mà thread đang giữ. Nếu thread ngủ trong `synchronized`, các thread cần cùng monitor vẫn bị chặn. Vì vậy không dùng sleep như một cách “cho thread khác cơ hội vào khóa” trong giao thức điều kiện. Dùng `wait/notify` hoặc `Condition` theo hợp đồng khi cần nhả khóa lúc chờ.

## 10. Hợp tác dừng trong tác vụ CPU-bound

Một vòng tính toán không gọi API chặn có thể **không bao giờ ném `InterruptedException`** chỉ vì thread khác gọi `interrupt()`. Nó phải chủ động kiểm tra cờ gián đoạn ở những điểm hợp lý.

~~~java
// CooperativeCpuDemo.java
import java.util.concurrent.CountDownLatch;

public class CooperativeCpuDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            started.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                // Minh họa điểm kiểm tra; không thực hiện công việc thật.
                Thread.onSpinWait();
            }
            System.out.println("CPU worker stopped");
        });

        worker.start();
        started.await();
        worker.interrupt();
        worker.join();
        System.out.println("Main observed termination");
    }
}
~~~

~~~bash
javac CooperativeCpuDemo.java
java CooperativeCpuDemo
~~~

Stdout trong lần chạy bình thường:

~~~text
CPU worker stopped
Main observed termination
~~~

Thread kiểm tra bằng `isInterrupted()` nên không tự xóa cờ; main gửi interrupt rồi `join()` để biết thực sự kết thúc.

Ví dụ dùng spin để minh họa đơn giản, **không phải kỹ thuật xử lý CPU-bound hiệu quả**. Trong một thuật toán thật, kiểm tra flag sau mỗi chunk công việc phù hợp, tránh vòng lặp trống đốt CPU. Đối với thao tác blocking hỗ trợ interrupt, ưu tiên API đó thay vì tự spin.

### 10.1. Cờ volatile riêng so với interrupt

Một ứng dụng có thể dùng `volatile boolean stopRequested` để yêu cầu worker dừng vòng tính toán, nhất là khi không dùng blocking APIs. Nhưng nếu worker đang kẹt ở `BlockingQueue.take()`, chỉ đặt `stopRequested = true` **không tự đánh thức take**. Có thể cần `interrupt`, sentinel hoặc timed poll. Đừng giữ hai cơ chế dừng không nhất quán (ví dụ một nơi đặt cờ, nơi khác interrupt mà worker chỉ theo dõi một cái).

## 11. Cleanup, try-with-resources và tính đúng nghiệp vụ

### 11.1. Cleanup cần chạy khi code thật sự thoát scope

Một worker thường có:
- Resource như `InputStream`, `Reader`, `Socket` hoặc kết nối.
- Trạng thái context như `ThreadLocal`.
- Lock hoặc semaphore permit.
- Task đang cập nhật nhiều bước.

Khi có interrupt, thiết kế đường thoát qua `finally` hoặc `try-with-resources`:

~~~java
// Mã minh họa trong class; resource và process có hợp đồng phù hợp.
try (Resource resource = openResource()) {
    process(resource); // Có thể ném InterruptedException.
} finally {
    threadContext.remove();
}
~~~

`Resource`, `openResource()`, `process(...)` và `threadContext` là tên giả định để minh họa cấu trúc, không phải file độc lập. `try-with-resources` gọi `close()` khi rời scope và `finally` dọn context. Nhưng **cancellation không tự động đóng resource ngay lúc caller gửi interrupt**: chỉ khi luồng thực thi đi qua scope dọn dẹp, hoặc owner có giao thức đóng riêng.

### 11.2. Giữ interrupt flag có thể tác động lên cleanup

Sau khi catch và re-interrupt, các blocking calls tiếp theo của **cùng thread** có thể phát hiện cờ và ném nhanh. Nếu cleanup cần chờ interruptible API, hãy thiết kế rõ cách thực hiện:
- Nếu có thể, cho cleanup xảy ra trong `finally` trước khi tín hiệu hủy được chuyển lên caller.
- Nếu phải đợi một bước đóng tài nguyên có timeout, xác định có cần trì hoãn, khôi phục hoặc xử lý lại trạng thái interrupt theo contract, **không vô tình xóa vĩnh viễn tín hiệu**.
- Đừng chạy I/O blocking vô hạn trong `finally` rồi kỳ vọng cancellation luôn hoàn thành nhanh.
- Đối với permit/lock, chỉ nhả khi đã thực sự sở hữu; đối với `ThreadLocal` theo task, dọn mapping trong `finally`, nhất là worker pool tái sử dụng thread.

Không có một mẫu cleanup duy nhất phù hợp mọi API. Điều phải giữ là **không mất yêu cầu hủy** và **không bỏ lại trạng thái/handle** ngoài phạm vi sở hữu.

## 12. Phân biệt InterruptedException với TimeoutException và CancellationException

| Loại | Thường xuất hiện ở đâu? | Ý nghĩa chính |
| --- | --- | --- |
| `InterruptedException` | `sleep`, `join`, `take`, `await`, `Future.get`, `lockInterruptibly` | **Thread đang chờ** được yêu cầu gián đoạn |
| `TimeoutException` | `Future.get(timeout, unit)` | Caller đã chờ quá hạn; **task chưa chắc bị hủy** |
| `CancellationException` | `Future.get()` sau khi Future bị cancel | Future bị hủy, không cung cấp kết quả |
| `ExecutionException` | `Future.get()` khi Callable lỗi | Task đã kết thúc bằng exception (cause ở `getCause()`) |
| `RejectedExecutionException` | Submit khi executor không nhận thêm task hoặc quá tải tùy cấu hình | Task bị từ chối nhận, không đồng nghĩa interrupt |

Ví dụ: `future.get(100, MILLISECONDS)` ném `TimeoutException` **không tự gọi `future.cancel(true)`**. Caller phải quyết định có yêu cầu hủy hay không và xử lý việc task có thể vẫn chạy. Ngược lại, `Future.get()` có thể ném `InterruptedException` vì **thread caller** bị interrupt trong lúc chờ; điều đó **không có nghĩa task đang chạy nhất thiết bị interrupt hoặc cancel**.

**Không gộp các ngoại lệ này vào một catch tổng quát rồi bỏ qua**: chúng biểu diễn các trạng thái lifecycle khác nhau và cần phản ứng khác nhau.

## 13. Những anti-pattern phổ biến

| Sai lầm | Tại sao sai | Hướng xử lý |
| --- | --- | --- |
| `catch (InterruptedException ignored) {}` | Nuốt yêu cầu hủy | Truyền tiếp hoặc restore + thoát |
| Catch, restore flag, rồi lặp blocking ngay | Có thể ném liên tục, gây busy error loop | Thoát worker hoặc chuyển sang đường kết thúc |
| Catch và `return success` dù task dở dang | Báo sai kết quả nghiệp vụ | Báo cancelled/failure theo contract |
| Gọi `Thread.interrupted()` để chỉ kiểm tra | Nó xóa flag của current thread | Dùng `isInterrupted()` nếu không muốn xóa |
| Dùng `interrupt()` như “kill ngay” | Worker có thể không hợp tác | Cài cooperative cancellation + chờ termination |
| Giữ lock khi đang chờ vô hạn mà không có đường hủy | Có thể kéo dài contention/deadlock | Thiết kế lock/condition/timeout phù hợp |
| Gọi `unlock()` khi `lockInterruptibly()` chưa thành công | Sai ownership, exception khác | Chỉ unlock trong scope sau khi nhận lock |
| `shutdownNow()` rồi cho rằng mọi task đã dừng | Chỉ cố interrupt, task có thể chạy tiếp | Theo dõi termination và đường thoát worker |
| `Future.get(timeout)` timeout rồi coi task đã cancel | Timeout chỉ ngừng chờ caller | Cancel riêng nếu cần, có giám sát kết thúc |
| Dựa vào `finally` như cơ chế chống process kill | JVM crash/halt có thể không chạy finally | Cần recovery/durable consistency nếu yêu cầu |
| Tự reset interrupt status không có chủ đích | Mất dấu hiệu hủy cho caller | Chỉ xóa khi sở hữu giao thức |
| Đem `InterruptedException` ra làm exception nghiệp vụ tùy ý | Nhầm tín hiệu lifecycle với lỗi dữ liệu | Giữ ý nghĩa hủy/chờ bị gián đoạn |

### 13.1. Không nên “bắt Exception chung rồi retry”

~~~java
// Mã minh họa anti-pattern:
while (true) {
    try {
        doWork();
    } catch (Exception e) {
        log(e);
    }
}
~~~

Nếu `doWork()` chặn và ném `InterruptedException`, catch tổng quát có thể **nuốt** nó; thread không dừng. Còn `doWork`, `log` là placeholder, không phải chương trình Java độc lập.

Tách `InterruptedException` thành nhánh xử lý riêng ở những chỗ có thể ném, rồi mới phân loại các lỗi khác. Nếu bắt `Exception` ở tầng giám sát, hãy bảo đảm tín hiệu hủy không bị biến thành “error thông thường, thử lại ngay”.

### 13.2. Không phải mọi catch đều phải re-interrupt

Nếu caller thật sự yêu cầu **chờ dù bị interrupt** (một số cơ chế nội bộ đặc biệt), có thể tạm ghi nhận interrupt, tiếp tục chờ, rồi restore status khi rời phạm vi. Nhưng đây là **ngoại lệ cần chủ đích rõ**, không phải mặc định cho worker. Nếu mục tiêu là phản hồi yêu cầu dừng, tiếp tục chờ không giới hạn thường trái với hợp đồng hủy.

## 14. Checklist thiết kế và tự kiểm tra

### Checklist nhanh

1. **Ai sở hữu quyền dừng?** Main thread, service, executor hay thành phần giám sát?
2. **Worker đang chờ ở đâu?** `sleep`, `take`, `await`, lock, I/O hay vòng CPU?
3. **API có hỗ trợ interrupt không?** Phải đọc hợp đồng chính xác; không đoán mọi blocking call đều ném `InterruptedException`.
4. **Phương thức có thể throw tiếp không?** Nếu có, ưu tiên truyền `InterruptedException` tới tầng quyết định. Nếu không, restore flag và thoát theo giao thức.
5. **Mọi đường thoát có cleanup không?** `finally`, try-with-resources, unlock/permit/ThreadLocal.
6. **Có chờ xác nhận kết thúc không?** `join()`/`Future`/`awaitTermination` phù hợp, có timeout khi cần.
7. **Có nhầm timeout với hủy không?** Timed wait timeout không tự cancel task.
8. **Có producer/consumer bị kẹt vô hạn không?** Giao thức dừng phải đánh thức được các worker đang chờ.
9. **Có báo trạng thái nghiệp vụ đúng không?** Task bị hủy không được tự báo thành công hoặc làm mất công việc không có recovery.
10. **Có test race giữa “đã gửi interrupt” và “chưa vào blocking call” không?** Blocking API tương thích phải phát hiện status đã đặt; code của bạn không được dựa chỉ vào thứ tự sleep.

### Câu hỏi tự kiểm tra

**1.** `worker.interrupt()` có bảo đảm worker dừng ngay không?  
**Đáp án:** Không. Đây là yêu cầu hợp tác; worker có thể bắt exception rồi tiếp tục hoặc chưa kiểm tra flag.

**2.** Khác nhau giữa `Thread.interrupted()` và `Thread.currentThread().isInterrupted()`?  
**Đáp án:** Cả hai xem current thread khi gọi theo mẫu này, nhưng `Thread.interrupted()` **xóa status**, còn `isInterrupted()` **không** xóa.

**3.** Sau khi `Thread.sleep` ném `InterruptedException`, `isInterrupted()` thường là gì?  
**Đáp án:** `false` vì phép chờ đã xóa status khi ném. Nếu không thể throw tiếp và muốn giữ thông tin cho tầng ngoài, gọi `Thread.currentThread().interrupt()`.

**4.** Một phương thức `throws InterruptedException` nên làm gì khi `queue.take()` bị interrupt?  
**Đáp án:** Thường để ngoại lệ truyền lên caller, dọn resource trong `finally` nếu cần; không nuốt nó.

**5.** `Runnable.run()` không khai báo checked exception; worker bị interrupt nên làm gì?  
**Đáp án:** Một lựa chọn hợp lệ là catch `InterruptedException`, re-interrupt current thread, rồi thoát task; chính sách cụ thể tùy contract.

**6.** `Future.cancel(true)` trả true có chứng minh toàn bộ side effect của task đã dừng?  
**Đáp án:** Không. Future bị đánh dấu cancelled và có yêu cầu interrupt khi phù hợp; task có thể vẫn chạy hoặc cleanup.

**7.** Nếu `future.get(timeout)` ném `TimeoutException`, task tự bị cancel?  
**Đáp án:** Không. Caller cần quyết định cancel riêng và xử lý completion.

**8.** `lock.lock()` và `lock.lockInterruptibly()` có khác khi đang chờ không?  
**Đáp án:** Có. `lockInterruptibly()` có thể thoát chờ bằng `InterruptedException`; `lock()` không có cùng hợp đồng ném checked exception.

**9.** Trong worker loop, bắt `InterruptedException` rồi tiếp tục `take()` vô hạn gây gì?  
**Đáp án:** Nuốt tín hiệu dừng hoặc vòng lỗi liên tục nếu restore rồi lặp, làm shutdown không tiến triển.

**10.** Đã bắt `InterruptedException` rồi restore flag, có nên tiếp tục gọi `sleep()` như không có gì xảy ra?  
**Đáp án:** Thường không. `sleep()` có thể phát hiện flag và ném ngay; nếu muốn dừng, hãy thoát theo giao thức.

**11.** Có thể dùng `join()` để xác nhận worker đã kết thúc sau interrupt không?  
**Đáp án:** Có, nếu `join()` trả về bình thường. Caller nên cân nhắc timed join và xử lý việc thread đang chờ bị interrupt.

**12.** `ExecutorService.shutdown()` có giải phóng mọi task đang `BlockingQueue.take()` không?  
**Đáp án:** Không. Shutdown không tự interrupt hoặc cung cấp item; cần giao thức worker stop/cancel và chờ kết thúc.

**13.** Một worker cập nhật tệp xong mới bị interrupt. Việc catch `InterruptedException` có tự hoàn tác tệp không?  
**Đáp án:** Không. Phải thiết kế transaction/recovery/compensation tương ứng nếu nghiệp vụ cần.

**Ghi nhớ:** `InterruptedException` là tín hiệu quan trọng của **cooperative cancellation**. **Truyền tiếp** khi có thể; nếu tầng hiện tại không thể truyền checked exception thì thường **khôi phục interrupt flag và thoát**. Luôn phân biệt **yêu cầu hủy** với **đã kết thúc**, giữ cleanup đúng phạm vi và không biến interrupt thành lỗi thông thường bị nuốt mất.

### Tài liệu tham khảo chuẩn

- Java SE 17, `Thread.interrupt()`, `Thread.interrupted()`, `isInterrupted()` và `sleep()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
- Java SE 17, `InterruptedException`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/InterruptedException.html
- Java SE 17, `BlockingQueue`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html
- Java SE 17, `Future`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html
- Java SE 17, `ExecutorService`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
- Java SE 17, `ReentrantLock`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-004">Khôi phục interrupt flag khi không thể truyền ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Khôi phục interrupt flag khi không thể truyền ngoại lệ trong Java

Trong Java, **interruption** là một cơ chế yêu cầu thread hợp tác dừng hoặc thay đổi công việc đang thực hiện. Nó không cưỡng ép thread kết thúc. Một thread có thể đang chờ trong `Thread.sleep()`, `Object.wait()`, `Thread.join()`, `BlockingQueue.take()` hay một thao tác blocking có hỗ trợ interruption. Khi thread được interrupt, các phương thức này thường **ném `InterruptedException` và đồng thời xóa interrupt status** theo hợp đồng của chúng.

Nếu một phương thức đang xử lý có quyền khai báo `throws InterruptedException`, cách đơn giản nhất thường là **truyền ngoại lệ lên tầng gọi** để tầng đó quyết định việc hủy. Nhưng có những ranh giới API **không cho phép khai báo checked exception**, chẳng hạn `Runnable.run()`, `Consumer.accept()`, nhiều callback, phương thức override interface không có `throws InterruptedException` hoặc một public API cố ý không khai báo ngoại lệ đó.

Trong trường hợp **không thể truyền ngoại lệ**, quy tắc mặc định rất quan trọng là:

> **Sau khi bắt `InterruptedException`, nếu chưa xử lý hoàn toàn yêu cầu interrupt ở tầng hiện tại, hãy khôi phục trạng thái bằng `Thread.currentThread().interrupt()`, rồi dừng hoặc trả quyền điều khiển phù hợp.** Không được bắt rồi im lặng tiếp tục làm việc như thể chưa hề có yêu cầu hủy.

Bài Knowledge này thuộc nhóm **Interrupt & Hủy** trong **Java concurrency nói chung**, tập trung vào Java SE 17+, không dùng cơ chế riêng của Spring Framework.

## 1. Interrupt flag là gì?

Mỗi `Thread` có **interrupt status**: một trạng thái logic có thể được đặt khi ai đó gọi `thread.interrupt()`. Thread được yêu cầu ngắt thường tự kiểm tra trạng thái hoặc đang gọi một API có hợp đồng xử lý interruption.

Ba lời gọi cần phân biệt:

| Lời gọi | Đối tượng chịu tác động | Ngữ nghĩa |
| --- | --- | --- |
| `targetThread.interrupt()` | `targetThread` | Đặt/yêu cầu interrupt; nếu target đang đợi bằng API interruptible, API có thể đánh thức bằng exception |
| `targetThread.isInterrupted()` | `targetThread` | Đọc interrupt status; **không xóa** status |
| `Thread.interrupted()` | **Thread hiện tại** | Đọc và **xóa** interrupt status của current thread khi trả về true |

`Thread.interrupted()` là **static method**; không phải phương thức kiểm tra trạng thái của một thread bất kỳ. Tránh viết `otherThread.interrupted()` với hy vọng nó kiểm tra `otherThread`: cách gọi static đó vẫn thao tác trên **current thread**. Muốn xem status của một thread cụ thể, dùng `otherThread.isInterrupted()`.

Cần phân biệt **request** và **completion**:

- `interrupt()` đặt yêu cầu; không bảo đảm method `run()` đã kết thúc.
- `isInterrupted()` trả flag; không chứng minh thread đã dừng, đã cleanup hay task thành công.
- `Thread.join()`, `Future.get()` hoặc tín hiệu completion riêng là cơ chế phù hợp để biết công việc đã hoàn tất theo hợp đồng.
- Thread có thể bỏ qua interrupt, xử lý thành công hoặc kết thúc; kết quả phụ thuộc code và API blocking đang dùng.

## 2. Tại sao `InterruptedException` có thể làm mất interrupt flag?

Nhiều API blocking của Java hỗ trợ interruption theo kiểu **ném checked exception**. Khi ném `InterruptedException` do bị ngắt, các API điển hình như `Thread.sleep()` và `Object.wait()` **xóa interrupt status** trước khi control được chuyển vào `catch`.

Ví dụ logic:

```text
Trước yêu cầu:
  interrupt flag = false
  worker đang sleep

Thread khác:
  worker.interrupt()

Worker:
  sleep() phát hiện interruption
  sleep() ném InterruptedException
  interrupt status được xóa như một phần của hợp đồng
  catch (InterruptedException e) { ... }
```

Việc xóa status ở đây có chủ ý: **ngoại lệ chính là cách báo cho code rằng sự kiện interrupt đã xảy ra**. Nếu code bắt ngoại lệ rồi chỉ log và bỏ qua, tín hiệu có thể không còn đường truyền lên tầng điều phối.

Không nên suy rộng rằng **mọi** API hoặc **mọi** interruption đều luôn xóa flag. Một số thao tác có thể giữ trạng thái, phản hồi bằng giá trị khác, hoặc không chấp nhận interruption trong lúc block. Phải đọc hợp đồng của API cụ thể.

Đặc biệt, `Thread.interrupted()` **chủ động xóa** flag khi dùng để kiểm tra; `Thread.currentThread().isInterrupted()` thì không.

## 3. Khi nào nên truyền tiếp, khi nào nên khôi phục?

Ba tình huống quan trọng:

### Trường hợp A — phương thức được khai báo `throws InterruptedException`

Nếu contract của method cho phép, **truyền ngoại lệ** thường là cách đúng nhất:

```java
static String receive(
        java.util.concurrent.BlockingQueue<String> queue)
        throws InterruptedException {
    return queue.take();
}
```

Đây là phương thức minh họa, không phải chương trình có `main`. Nó không bắt interruption và không biến task bị hủy thành một lần nhận thành công.

Trong code cần cleanup:

```java
static String receiveWithCleanup(
        java.util.concurrent.BlockingQueue<String> queue)
        throws InterruptedException {
    try {
        return queue.take();
    } finally {
        // Giải phóng tài nguyên thuộc phạm vi phương thức, nếu có.
    }
}
```

Đây vẫn là đoạn minh họa. Nếu caller muốn biết và quyết định cách hủy, có thể để exception đi lên. **Không bắt buộc vừa gọi `interrupt()` vừa truyền tiếp `InterruptedException`**; ngoại lệ đã là tín hiệu cho tầng tiếp theo. Việc đồng thời khôi phục status có thể thích hợp theo contract đặc biệt, nhưng không phải quy tắc bắt buộc khi đã propagate.

### Trường hợp B — không thể khai báo `throws InterruptedException`

`Runnable.run()` không khai báo checked exception này. Một thread đang chạy `Runnable` phải bắt ngoại lệ nếu gọi API như `sleep()` hay `take()`. Nếu muốn nhường quyền quyết định cho tầng ngoài thông qua flag, dùng:

```java
@Override
public void run() {
    try {
        doInterruptibleWork();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
    }
}
```

Đây là đoạn minh họa trong class implement `Runnable`, với `doInterruptibleWork()` là phương thức giả định. `Thread.currentThread().interrupt()` **khôi phục status của thread đang xử lý**, sau đó `return` kết thúc `run`. Nếu tiếp tục một vòng xử lý dài sau catch, flag có thể đã được bật nhưng task vẫn làm việc sai ý định hủy.

### Trường hợp C — tầng hiện tại đã xử lý trọn vẹn interruption theo contract

Một API đặc biệt có thể **cố ý tiêu thụ** interrupt nếu trách nhiệm của nó là chấm dứt hoạt động và công bố kết quả hủy bằng cơ chế khác, và contract xác định rõ điều này. Khi đó không có quy tắc tuyệt đối phải restore trong mọi catch.

Tuy nhiên, **đừng tự nhận "đã xử lý" chỉ vì đã log exception**. Nếu việc hủy vẫn cần được người gọi hoặc framework executor nhận biết, restore là lựa chọn an toàn hơn khi không thể propagate. Cần phân biệt việc xử lý tín hiệu có chủ ý với việc vô tình làm mất tín hiệu.

## 4. Vì sao `catch (InterruptedException e) { }` là anti-pattern?

Sai:

```java
while (true) {
    try {
        queue.take();
    } catch (InterruptedException ignored) {
        // Bỏ qua yêu cầu hủy và tiếp tục chờ.
    }
}
```

Đây là anti-pattern với biến `queue` giả định. Hậu quả:

1. `take()` có thể đã xóa interrupt flag trước khi ném.
2. Catch rỗng loại bỏ thông tin về yêu cầu hủy.
3. Vòng lặp quay lại `take()` và chờ vô thời hạn, dù caller mong worker thoát.
4. `Future.cancel(true)` hoặc shutdown hợp tác có thể trở nên vô hiệu về mặt dừng task.
5. Hệ thống khó phân biệt worker bận, worker kẹt và worker cố tình bỏ qua hủy.

Cũng không đủ nếu chỉ viết:

```java
catch (InterruptedException e) {
    System.err.println("Interrupted: " + e);
}
```

Logging không thay thế propagation hoặc status restoration. Nếu sau catch code còn tiếp tục bình thường, người dùng API có thể tưởng tác vụ chưa bị hủy. **Đúng hơn:** quyết định rõ "truyền tiếp", "khôi phục và kết thúc", hay "chuyển sang cancellation state theo contract".

## 5. Ví dụ Java 17+: khôi phục interrupt flag trong `Runnable`

Lưu thành `InterruptRestoreDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterruptRestoreDemo {
    public static void main(String[] args)
            throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean clearedWhenCaught = new AtomicBoolean();
        AtomicBoolean restoredInsideCatch = new AtomicBoolean();

        Thread worker = new Thread(() -> {
            try {
                entered.countDown();
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                // sleep() đã xử lý interruption bằng exception
                // và xóa flag theo hợp đồng.
                clearedWhenCaught.set(
                        !Thread.currentThread().isInterrupted());

                // Không thể throws InterruptedException từ Runnable.run().
                Thread.currentThread().interrupt();
                restoredInsideCatch.set(
                        Thread.currentThread().isInterrupted());
                return;
            }
        }, "interrupt-restore-worker");

        worker.start();
        entered.await();

        worker.interrupt();
        worker.join();

        System.out.println("Flag cleared in catch: "
                + clearedWhenCaught.get());
        System.out.println("Flag restored in catch: "
                + restoredInsideCatch.get());
        System.out.println("Worker terminated: "
                + !worker.isAlive());
    }
}
```

Biên dịch/chạy:

```bash
javac InterruptRestoreDemo.java
java InterruptRestoreDemo
```

Stdout chính xác trên đường chạy bình thường:

```text
Flag cleared in catch: true
Flag restored in catch: true
Worker terminated: true
```

Tại sao đáng tin?

- `entered` chỉ thông báo worker đã đến trước lời gọi `sleep()`. Nếu main interrupt trước lúc worker thật sự sleep, sleep vẫn phản hồi với interrupt status; nếu worker đang sleep, nó được đánh thức bằng `InterruptedException`. Không phụ thuộc một lần `Thread.sleep(100)` ở main để đoán lịch chạy.
- Khi `InterruptedException` được ném từ `sleep`, interrupt flag đã được xóa; `clearedWhenCaught` nhận true.
- Worker đặt lại flag bằng `Thread.currentThread().interrupt()`, đo được true trong catch rồi **kết thúc `run()`**.
- Main gọi `join()` để đợi worker kết thúc trước khi đọc hai `AtomicBoolean` và trạng thái thread.
- **Không lấy `worker.isInterrupted()` từ main sau `join()` để làm kiểm chứng bắt buộc**. Khi thread đã kết thúc, không nên dựa vào trạng thái interrupt hậu vòng đời để chuyển ý nghĩa business. Ví dụ ghi nhận flag ngay trong worker để kiểm tra ý nghĩa ở đúng thời điểm.

Đây là ví dụ cho cách **bảo toàn yêu cầu hủy** qua ranh giới API không cho phép throws; nó không ép thread phải bị kill.

## 6. `Thread.interrupted()` xóa flag, còn `isInterrupted()` thì không

Lưu thành `InterruptFlagMethodsDemo.java`:

```java
public class InterruptFlagMethodsDemo {
    public static void main(String[] args) {
        Thread current = Thread.currentThread();

        current.interrupt();

        System.out.println("Before check: "
                + current.isInterrupted());
        System.out.println("Static check: "
                + Thread.interrupted());
        System.out.println("After static check: "
                + current.isInterrupted());

        current.interrupt(); // Khôi phục một lần nữa.
        System.out.println("Restored: "
                + current.isInterrupted());

        // Dọn interrupt status để kết thúc bài demo.
        Thread.interrupted();
    }
}
```

```bash
javac InterruptFlagMethodsDemo.java
java InterruptFlagMethodsDemo
```

Stdout chính xác:

```text
Before check: true
Static check: true
After static check: false
Restored: true
```

`Thread.interrupted()` là lời gọi **static** và kiểm tra **current thread**, sau đó xóa flag khi đang bật. Ngược lại `current.isInterrupted()` không xóa flag. Cuối chương trình có một lần `Thread.interrupted()` chỉ để cleanup trong ví dụ **có chủ đích**; không được dùng cách "kiểm tra rồi vô tình xóa" trong code nghiệp vụ đang muốn chuyển tiếp yêu cầu hủy.

Đối với vòng xử lý hợp tác, có thể kiểm tra:

```java
while (!Thread.currentThread().isInterrupted()) {
    processOneUnit();
}
```

Đây là mẫu minh họa; `processOneUnit` không xác định. Nếu hàm bên trong có thể block/throw `InterruptedException`, phải kết hợp xử lý ngoại lệ để thoát vòng đúng lúc. Kiểm tra flag không tự đánh thức lời gọi blocking.

## 7. Vì sao restore xong thường phải thoát, không chỉ tiếp tục?

Một lỗi tinh vi:

```java
while (true) {
    try {
        performBlockingOperation();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        // Nhưng lại chạy vòng lặp tiếp.
    }
}
```

Nếu `performBlockingOperation` phản hồi ngay khi thấy interrupt flag đang bật, vòng sau có thể liên tục ném `InterruptedException`, tạo **busy loop** tiêu thụ CPU. Nếu API khác không kiểm tra flag, code lại có thể tiếp tục làm việc dù đã nhận yêu cầu hủy.

Mẫu thường tốt hơn:

```java
while (!Thread.currentThread().isInterrupted()) {
    try {
        performBlockingOperation();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
    }
}
```

Hai đoạn trên là minh họa, `performBlockingOperation` giả định.

**Tách hai quyết định:** phục hồi flag để giữ thông tin interruption, và **dừng/từ chối công việc mới** để thực sự hợp tác hủy. Chỉ có thao tác đầu mà thiếu thao tác thứ hai có thể làm trạng thái "interrupted" tồn tại trên một thread vẫn đang xử lý nghiệp vụ.

Nếu method bắt exception nằm sâu trong một stack, có thể khôi phục và `return` trạng thái hủy cho caller để caller không tiếp tục; nếu API có kiểu trả về, phải định nghĩa rõ sentinel hoặc result type đại diện việc hủy.

## 8. Ví dụ Java 17+: worker chờ queue và dừng hợp tác

Lưu thành `CooperativeQueueWorkerDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class CooperativeQueueWorkerDemo {
    public static void main(String[] args)
            throws InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(1);
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean stoppedOnInterrupt = new AtomicBoolean();
        AtomicBoolean restored = new AtomicBoolean();

        Thread worker = new Thread(() -> {
            started.countDown();

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    queue.take(); // Có thể block khi queue rỗng.
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                restored.set(Thread.currentThread().isInterrupted());
                stoppedOnInterrupt.set(true);
            }
        }, "queue-worker");

        worker.start();
        started.await();

        worker.interrupt();
        worker.join();

        System.out.println("Stopped: " + stoppedOnInterrupt.get());
        System.out.println("Flag at exit: " + restored.get());
    }
}
```

```bash
javac CooperativeQueueWorkerDemo.java
java CooperativeQueueWorkerDemo
```

Stdout chính xác:

```text
Stopped: true
Flag at exit: true
```

Lịch chạy có thể khác nhau:

- Nếu worker đã vào `queue.take()`, interruption thường làm `take` ném `InterruptedException`, catch đặt lại flag, rồi control đi vào `finally`.
- Nếu main interrupt trước lúc worker vào `take()`, vòng lặp có thể nhìn thấy flag và thoát trực tiếp; hoặc lần `take` có thể phát hiện interrupt và ném. Cả hai đều dẫn đến kiểm tra trạng thái true tại điểm kết thúc.
- Queue luôn rỗng, nên không có trường hợp worker thực hiện một tác vụ mới rồi thoát mà làm stdout thay đổi.
- `join` bảo đảm main đợi worker hoàn tất trước khi đọc kết quả.

`stoppedOnInterrupt` trong ví dụ ghi true khi worker rời khối xử lý; nó là flag quan sát cho bài demo, **không phải contract completion đầy đủ** cho ứng dụng phức tạp. Nếu cần lấy lỗi/kết quả task, `Future` hoặc một kiểu kết quả phù hợp thường rõ hơn.

Trong production, cần phân biệt lỗi khác với interruption. Một task kết thúc bình thường hoặc gặp exception khác cũng sẽ chạy `finally`; không nên dùng duy nhất "đã vào finally" để kết luận lý do kết thúc nếu code có nhiều nhánh.

## 9. Ranh giới thường gặp: `Runnable.run()`

`Runnable.run()` có chữ ký:

```java
void run();
```

Đây là chữ ký của interface, không khai báo checked `InterruptedException`. Code trong `run` phải xử lý ngoại lệ nếu nó gọi các API interruptible.

Mẫu thông dụng:

```java
final class Worker implements Runnable {
    private final java.util.concurrent.BlockingQueue<String> queue;

    Worker(java.util.concurrent.BlockingQueue<String> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        try {
            while (true) {
                String message = queue.take();
                handle(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Trả về từ run() để dừng thread/task.
        }
    }

    private void handle(String message) {
        // Xử lý không block hoặc có chính sách lỗi riêng.
    }
}
```

Class này không có `main`, nhưng dùng Java SE thuần. Nếu `handle` có thể block, ném exception, hoặc cập nhật state ngoài process, nó cần thêm hợp đồng hủy và xử lý partial side effect.

Vì API `Runnable` là một ranh giới không cho phép khai báo checked exception, **restore rồi return** là lựa chọn thường gặp. Nếu framework executor có chính sách quan sát exception theo future, có thể cân nhắc wrapping thành unchecked exception **khi contract yêu cầu**; vẫn phải xác định trạng thái interrupt và cách báo lỗi. Không tự động biến mọi interruption thành lỗi "business failure" mà không kiểm tra contract.

## 10. Ranh giới thường gặp: `Consumer<T>`, listener và callback

Ví dụ callback:

```java
import java.util.function.Consumer;

Consumer<String> listener = event -> {
    try {
        Thread.sleep(1_000);
        System.out.println(event);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
    }
};
```

Đây là đoạn minh họa trong một context hợp lệ có import; không phải file có `main`. `Consumer.accept` không khai báo checked `InterruptedException`, nên callback phải xử lý.

**Hai rủi ro:**

1. Callback bị interrupt có thể dừng riêng callback nhưng **publisher vẫn tiếp tục gọi callback khác**, tùy contract của publisher. Vì thế cần định nghĩa caller có quan tâm status của thread đang chạy callback không và callback nên chuyển kết quả hủy bằng cách nào.
2. Nếu callback chạy trên **thread dùng chung**, interrupt status được khôi phục trên thread đó. Điều này là thông tin chính xác tại ranh giới callback, nhưng caller/dispatcher cần có chính sách không tiếp tục chạy tác vụ bình thường bất chấp status.

Không giả định callback chắc chắn chạy trên thread riêng. Một listener có thể được gọi **đồng bộ trên thread gọi**, hoặc bởi executor; cơ chế tương tác quyết định ai sở hữu thread đang được interrupt. Trong public API, document threading model, cancellation behavior và failure propagation.

### Phương thức override không cho thêm checked exception

Nếu interface quy định:

```java
interface Listener {
    void onMessage(String message);
}
```

implementation không thể tùy ý thêm `throws InterruptedException` vào chữ ký override. Nếu việc xử lý phải block, có thể:

- Catch, restore flag rồi **return/abort** theo contract.
- Đổi API do mình kiểm soát để cho phép checked interruption hoặc trả trạng thái hủy.
- Dùng một callback riêng có chữ ký `throws InterruptedException` nếu thực sự có quyền thay đổi interface.
- Tách công việc blocking sang một layer có thể truyền exception theo `Callable`/future phù hợp.

Đừng biến một hạn chế chữ ký thành cớ để **nuốt interruption**.

## 11. `Callable<V>` và sự khác nhau khi có thể propagate

`Callable.call()` có chữ ký:

```java
V call() throws Exception;
```

Nó cho phép truyền `InterruptedException` (là subclass của `Exception`), khác với `Runnable.run()`.

Ví dụ:

```java
Callable<String> task = () -> queue.take();
```

Đây là đoạn minh họa, giả định `queue` là `BlockingQueue<String>` và đã import `java.util.concurrent.Callable`.

Khi task được gửi tới `ExecutorService.submit`, ngoại lệ từ `call` được thu nhận trong `Future`; caller dùng `Future.get()` có thể gặp `ExecutionException` bao nguyên nhân. Cần phân biệt:

- `InterruptedException` ném **trong worker task**.
- `InterruptedException` ném **trong caller đang chờ `future.get()`** vì **caller thread** bị interrupt.
- `CancellationException` khi future đã được cancel theo contract.
- `ExecutionException` khi task kết thúc bằng exception.

Đây là các đường diễn tiến khác nhau. Caller không nên bắt một `InterruptedException` từ `future.get()` rồi mặc định worker cũng đã bị interrupt; nó báo **thread gọi `get` đang bị yêu cầu ngắt trong lúc chờ**.

Nếu chính `Callable.call` có thể truyền ngoại lệ lên tầng quản lý, **không cần mặc định bắt lại để restore**. Nếu `call` bắt chỉ để cleanup rồi propagate, phải giữ nguyên thông tin hủy, không biến thành successful result.

## 12. Ví dụ Java 17+: Future và hai loại interruption

Chương trình sau kiểm chứng **caller của `Future.get()` bị interrupt**, còn task chỉ được giải phóng sau đó bằng một tín hiệu riêng. Không suy luận sai rằng interrupt caller đồng nghĩa interrupt worker.

Lưu thành `FutureWaitInterruptionDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class FutureWaitInterruptionDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch allowTaskToFinish = new CountDownLatch(1);
        CountDownLatch aboutToGet = new CountDownLatch(1);
        AtomicBoolean callerRestored = new AtomicBoolean();

        try {
            Future<Integer> future = pool.submit(() -> {
                allowTaskToFinish.await();
                return 42;
            });

            Thread caller = new Thread(() -> {
                try {
                    aboutToGet.countDown();
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    callerRestored.set(
                            Thread.currentThread().isInterrupted());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }, "future-caller");

            caller.start();
            aboutToGet.await();
            caller.interrupt();
            caller.join();

            // Task chưa bị cancel hoặc interrupt bởi thao tác trên caller.
            allowTaskToFinish.countDown();
            System.out.println("Caller flag restored: "
                    + callerRestored.get());
            System.out.println("Task result: " + future.get());
        } finally {
            allowTaskToFinish.countDown();
            pool.shutdownNow();
        }
    }
}
```

```bash
javac FutureWaitInterruptionDemo.java
java FutureWaitInterruptionDemo
```

Stdout chính xác trên đường chạy bình thường:

```text
Caller flag restored: true
Task result: 42
```

**Lý do deterministic:**

- Task worker đợi `allowTaskToFinish`, chưa thể hoàn tất trong lúc thread caller chờ `future.get()`.
- Caller thông báo đã tới điểm trước `get()`; main có thể interrupt trước hoặc trong `get()`. Nếu interrupt đến trước lời gọi chờ thực tế, `get()` vẫn thấy interrupt status khi phải chờ trên future chưa hoàn thành.
- `future.get()` ném `InterruptedException` trên **caller**, catch khôi phục flag của caller.
- `caller.join()` đợi thread caller thoát, rồi main giải phóng task. Chỉ lúc này `future.get()` trong main lấy kết quả 42.
- `finally` bảo đảm main mở latch nếu có lỗi trên đường đi, sau đó gửi yêu cầu shutdown.

Không dùng ví dụ này để kết luận `Future.get()` luôn phản hồi interruption theo đúng một thời điểm: nó là API chờ có thể bị ngắt, còn nếu future đã hoàn tất trước khi caller bắt đầu chờ, các điều kiện có thể khác. Việc giữ task chưa hoàn tất trong demo giúp kết quả ổn định.

## 13. `Future.cancel(true)` không phải là "đã xử lý xong"

`Future.cancel(true)` là một yêu cầu hủy có thể cho phép gửi interrupt đến worker nếu task đang chạy và có thể bị interrupt theo implementation. Giá trị trả về cho biết việc cancel request có được chấp nhận theo contract của future; nó **không cam kết mọi side effect đã dừng ngay** hay resource đã cleanup hoàn tất.

Nếu worker:

```java
try {
    blockingOperation();
} catch (InterruptedException e) {
    // Nuốt ngoại lệ rồi tiếp tục.
    continueWorking();
}
```

thì cancellation có thể không khiến code dừng như caller mong muốn. Mẫu trên là anti-pattern minh họa với phương thức giả định.

Nếu worker restore rồi thoát, đó là **cooperative cancellation** đúng mục tiêu hơn. Tuy vậy, cần phân biệt:

- **Future cancelled** không có nghĩa executor đã hoàn toàn giải phóng mọi tài nguyên của task.
- **Thread interrupted** không có nghĩa task chưa để lại partial side effect.
- **Thread terminated** không có nghĩa operation đã thành công.
- Khi dùng `CompletableFuture.cancel(...)`, theo hợp đồng của `CompletableFuture`, tham số `mayInterruptIfRunning` không dùng để điều khiển underlying processing; đừng mặc định cancellation sẽ interrupt một tác vụ đang chạy như một số Future do executor quản lý.

Để đợi lifecycle thực sự hoàn tất, cần một contract completion/cleanup rõ ràng, future hoặc latch riêng nếu trạng thái canceled không đủ biểu diễn điểm cleanup.

## 14. Khôi phục flag trong utility method không khai báo throws

Một helper đôi khi có chữ ký không cho đổi, như:

```java
boolean awaitQuietly(CountDownLatch latch);
```

Từ "quietly" dễ khiến người viết chọn bỏ qua interruption. Nếu không thể truyền checked exception, có thể trả `false` và restore flag, **nhưng chỉ khi contract định nghĩa `false` là bị hủy/chưa hoàn thành**.

Mẫu Java:

```java
static boolean awaitOrCancel(
        java.util.concurrent.CountDownLatch latch) {
    try {
        latch.await();
        return true;
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
    }
}
```

Đây là phương thức minh họa, không phải chương trình có `main`.

**Quan trọng:** caller phải kiểm tra kết quả:

```java
if (!awaitOrCancel(latch)) {
    return; // Không tiếp tục vào nhánh yêu cầu latch đã hoàn tất.
}
performAfterCompletion();
```

Đây là đoạn minh họa khác với `latch` và `performAfterCompletion` giả định. Nếu caller bỏ qua `false` rồi vẫn làm việc, lời hứa "return false khi chưa sẵn sàng" không giúp duy trì invariant.

API rõ hơn có thể dùng enum kết quả `COMPLETED`/`INTERRUPTED`, hoặc tách method interruptible với wrapper no-throws có chính sách cụ thể.

## 15. Ví dụ Java 17+: trả kết quả hủy và giữ interrupt flag

Lưu thành `NoThrowsBoundaryDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class NoThrowsBoundaryDemo {
    static boolean awaitOrCancel(CountDownLatch gate) {
        try {
            gate.await();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean(true);
        AtomicBoolean restored = new AtomicBoolean();

        Thread worker = new Thread(() -> {
            ready.countDown();
            result.set(awaitOrCancel(gate));
            restored.set(Thread.currentThread().isInterrupted());
        });

        worker.start();
        ready.await();
        worker.interrupt();
        worker.join();

        System.out.println("Gate passed: " + result.get());
        System.out.println("Flag restored: " + restored.get());
    }
}
```

```bash
javac NoThrowsBoundaryDemo.java
java NoThrowsBoundaryDemo
```

Stdout chính xác:

```text
Gate passed: false
Flag restored: true
```

Gate **không bao giờ được mở**, nên nếu không bị interrupt, worker sẽ tiếp tục chờ. Main yêu cầu interrupt; helper catch, restore rồi trả `false`. Cả hai AtomicBoolean cho phép main đọc sau `join`, dù chính `join` đã đủ thiết lập happens-before cho việc đọc các kết quả worker ghi.

Ví dụ minh họa cách định nghĩa **contract của wrapper**: `false` không có nghĩa gate đã hoàn tất; nó có nghĩa method không thể đợi thành công. Tầng gọi cần quyết định chấm dứt công việc khi nhận false.

## 16. Có nên rethrow bằng unchecked exception?

Khi API không cho `throws InterruptedException`, một số hệ thống chọn:

```java
try {
    doInterruptibleWork();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Interrupted while working", e);
}
```

Đây là đoạn minh họa, không phải công thức dùng chung cho mọi tầng.

Ưu điểm: **không giả vờ thành công**; thông tin lỗi có thể đi tới exception handler hoặc future. Rủi ro: unchecked exception có thể được hệ thống coi là application failure thay vì cancellation; nó có thể làm thay đổi contract public API và khiến listener dispatch dừng đột ngột.

Nếu chọn cách này, tài liệu phải nói rõ:

- Exception nào đại diện interruption/cancellation?
- Caller có dựa vào interrupt flag đã restore không?
- Có resource cleanup cần làm trước khi throw không?
- Task có bị retry vì runtime exception không; retry có thể nhân đôi side effect không?

Cách đơn giản cho `Runnable.run()` thường là **restore rồi return** khi nhiệm vụ chỉ cần dừng. Cách dùng unchecked exception phù hợp khi tầng điều phối cần nhận failure bằng exception và contract đã định nghĩa rõ.

## 17. Cleanup, `finally` và thời điểm khôi phục flag

Khi bị interrupt, code vẫn có thể phải giải phóng khóa, permit, file hoặc resource đang sở hữu. Quy tắc **không bỏ cleanup** và **không làm mất interruption** đều quan trọng.

Nếu cleanup đơn giản và không có blocking operation:

```java
try {
    doInterruptibleWork();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
} finally {
    releaseOwnedResource();
}
```

Đây là ví dụ minh họa. `finally` chạy khi control rời `try/catch`, kể cả khi catch `return`; nhưng **không thể bảo đảm mọi cleanup sẽ thành công** nếu chính cleanup ném exception.

Nếu cleanup **có thể block và hỗ trợ interruption**, khôi phục flag **trước** cleanup có thể làm lời gọi cleanup ném `InterruptedException` ngay, cản trở việc dọn tài nguyên. Trong một số thiết kế, cần tạm **ghi nhớ việc đã bị interrupt**, làm cleanup có giới hạn theo contract, rồi **khôi phục flag trong `finally` sau cleanup**:

```java
boolean interrupted = false;

try {
    doInterruptibleWork();
} catch (InterruptedException e) {
    interrupted = true;
    // Không tiếp tục công việc nghiệp vụ bình thường.
} finally {
    try {
        cleanupWithDefinedPolicy();
    } finally {
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
```

Đây là pseudo-flow minh họa; `doInterruptibleWork` và `cleanupWithDefinedPolicy` là phương thức giả định. Nó chỉ thích hợp khi tầng hiện tại **thực sự sở hữu cleanup** và việc trì hoãn restore là chủ ý, không phải nuốt tín hiệu. Nếu cleanup cũng bị interrupt, policy phải quyết định timeout/retry/propagate như thế nào. Không được thực hiện **retry vô hạn không interruptible** chỉ để "cleanup cho bằng được".

Đặc biệt, `synchronized` tự nhả monitor khi rời block; `Lock` thủ công phải `unlock()` trong `finally`. Restoration không thay thế việc nhả lock.

## 18. Khi cleanup cũng bị interrupt, phải xử lý ra sao?

Xem tình huống utility có hai pha:

1. Chờ công việc hoàn tất.
2. Khi bị interrupt, cố ghi nhận/trả tài nguyên và thoát.

Nếu cả pha cleanup cũng gọi API blocking, một số cách ứng xử tùy contract:

- **Best-effort cleanup có deadline:** thử trong khoảng thời gian hữu hạn, rồi ghi nhận thất bại hoặc trả về state lỗi; khôi phục flag khi kết thúc.
- **Cleanup không interruptible nhưng ngắn:** thực thi thao tác giải phóng nội bộ bảo đảm rồi restore.
- **Propagate một ngoại lệ cao hơn:** nếu tầng hiện tại được phép, truyền interruption hoặc failure kèm nguyên nhân thích hợp; tránh làm mất original cause.
- **Đóng resource để đánh thức waiter:** tùy hợp đồng cụ thể của queue/channel/resource, không tự giả định `Thread.interrupt()` đã dừng mọi I/O.

Một anti-pattern khác là lặp:

```java
while (true) {
    try {
        cleanupBlocking();
        break;
    } catch (InterruptedException ignored) {
        // Cố chờ vô hạn, không lưu lại trạng thái và không timeout.
    }
}
```

Đây là đoạn minh họa sai: nó có thể bỏ qua yêu cầu hủy lâu dài, không còn giới hạn tiến độ. Nếu buộc phải hoàn tất cleanup, hãy thiết kế protocol có deadline và ghi lại điều kiện interrupt; không mặc nhiên "cleanup quan trọng nên bỏ qua mọi interrupt".

## 19. Một cờ `AtomicBoolean` không tương đương interrupt flag

Nhiều ứng dụng có:

```java
AtomicBoolean stopRequested = new AtomicBoolean();
stopRequested.set(true);
```

Một cờ atomic/volatile giúp worker thấy yêu cầu khi chủ động kiểm tra. Nhưng cờ không tự đánh thức thread đang `BlockingQueue.take()` hoặc `Object.wait()`. Interruption là cơ chế riêng mà nhiều API blocking hiểu để **thoát khỏi lời gọi chờ**.

Ngược lại, chỉ gọi `thread.interrupt()` không tự định nghĩa kết quả business: task có thể dừng một phần, bỏ qua request hoặc hoàn tất đúng. Thường cần kết hợp:

- Một cancellation policy của task.
- Một đường signaling (interrupt, sentinel, close, timeout hoặc cancellation token).
- Một completion signal để caller biết cleanup đã xong.
- Một cách báo lỗi hoặc trạng thái hủy cho người gọi.

Khi restore flag, bạn **bảo toàn thông tin yêu cầu interrupt**, không tự xây toàn bộ lifecycle protocol.

## 20. Không phải mọi blocking I/O đều phản hồi interruption như `sleep()`

Một số API trong `java.nio.channels` có semantics đóng channel khi thread bị interrupt, hoặc phản hồi qua exception khác. Một số thao tác legacy I/O có thể không dừng kịp khi chỉ gọi `interrupt()`. Các thư viện network, driver hoặc native code có hợp đồng riêng.

Vì vậy:

- Không áp dụng máy móc "mọi blocking I/O đều ném `InterruptedException`".
- Nếu một API trả exception khác do interruption, đọc hợp đồng để quyết định preserve status/cancellation signal đúng.
- Thiết kế timeout, resource close và cancellation path thích hợp.
- Nếu không thể hủy một thao tác, document giới hạn và giám sát thread/resource bị kẹt.
- Tránh giữ monitor trong lúc thực hiện I/O có thể block lâu, vì việc dừng/cleanup có thể cần một thread khác vào monitor đó.

Bài học về **restore interrupt flag** chủ yếu áp dụng tình huống **một interruptible call đã ném `InterruptedException` và làm clear status**, rồi code của bạn không thể truyền checked exception lên. Với API khác, không suy đoán semantics khi chưa đọc contract.

## 21. Interrupt flag và Java Memory Model

Interruption có các bảo đảm liên quan happens-before trong JMM: một lời gọi `interrupt()` trên thread target **synchronizes-with** điểm một thread phát hiện nó đã bị interrupt theo hợp đồng (ví dụ nhận `InterruptedException`, hoặc kiểm tra trạng thái bằng API thích hợp).

Nhưng một interrupt request **không thay thế khóa bảo vệ shared mutable state**. Nếu bạn có:

```java
sharedList.add(item);
worker.interrupt();
```

thì không được chỉ dựa vào `interrupt()` để kết luận mọi thao tác concurrent của `ArrayList` đã an toàn. Quan hệ ordering của một interrupt signal không biến list thành concurrent collection hoặc tạo transaction giữa nhiều thao tác.

Tương tự, interrupt status không cung cấp:

- Atomicity cho `count++`.
- Mutual exclusion cho nhiều writer.
- Guaranteed fairness khi có nhiều task.
- Một thông báo kết quả task thành công.
- Khả năng rollback side effect bên ngoài JVM.

**Restoration là phép bảo toàn tín hiệu**, không phải cách sửa một thuật toán đang có data race.

## 22. Tương tác với executor và thread pool

Khi task chạy trong executor, thread thực thi có thể được **tái sử dụng**. Interruption là trạng thái của **thread**, trong khi cancellation/failure/completion của một task là **trạng thái nghiệp vụ khác**.

Cần chú ý:

1. Một `Runnable` bị interrupt nên **thoát có kiểm soát**, không tiếp tục tiêu thụ task tiếp theo như thể bình thường.
2. Nếu task bắt `InterruptedException` rồi bỏ qua, `shutdownNow()` hoặc `cancel(true)` có thể không giúp task dừng nhanh như mong đợi.
3. Khôi phục flag giúp giữ yêu cầu hủy tại ranh giới task, nhưng **không bảo đảm executor sẽ công bố flag đó ra caller**; hãy dùng future/result hoặc cancellation state để báo lifecycle.
4. Không dựa vào trạng thái interrupt còn lại trên pooled thread cho **task kế tiếp**; thread-pool implementation có thể xử lý/reset status theo logic của nó. Mỗi task phải có contract độc lập.
5. Nếu task phải tự dừng do interruption, đừng thực hiện I/O, callback hoặc side effect mới sau khi restore rồi mới nhận ra đã bị hủy.
6. Nếu API quản lý task có policy riêng về interruption, tuân thủ policy đó thay vì tự quyết định "nuốt cho đỡ log".

Với thread pool, lỗi "nuốt interrupt" đặc biệt khó tìm vì pool vẫn sống, future có thể đã bị cancel, nhưng worker có thể tiếp tục nắm tài nguyên hoặc làm việc không cần thiết.

## 23. Các lỗi thường gặp

| Mẫu lỗi | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| `catch (InterruptedException ignored) {}` | Xóa tín hiệu, tiếp tục bình thường | Propagate nếu có thể; nếu không, restore rồi dừng theo contract |
| Catch chỉ log | Log không chuyển yêu cầu hủy tới tầng trên | Restore/propagate, xác định exit path |
| Restore rồi tiếp tục vòng while vô hạn | Có thể busy-loop hoặc làm thêm việc sau khi bị hủy | `break`/`return`, hoặc logic kiểm tra cờ rõ |
| Gọi `Thread.interrupted()` để "kiểm tra" nhưng không muốn clear | Static method xóa flag | Dùng `isInterrupted()` |
| Gọi `otherThread.interrupted()` | Static method vẫn tác động current thread | Dùng `otherThread.isInterrupted()` |
| Tự động restore kể cả khi propagate | Có thể tạo hai kênh tín hiệu không cần thiết và làm caller khó xử lý | Nếu truyền `InterruptedException` lên được, thường cứ truyền |
| Dùng `AtomicBoolean` thay interrupt của blocking call | Flag không đánh thức API đang block | Thêm interrupt/timeout/close hoặc giao thức signaling |
| Gọi interrupt rồi giả định worker đã chết | Interrupt chỉ là yêu cầu | Đợi completion bằng `join`/future/latch theo contract |
| Dùng `Future.cancel(true)` như bảo đảm side effect đã rollback | Cancellation không là transaction | Quản lý idempotency, cleanup và completion |
| Bọc thành RuntimeException nhưng bỏ cause | Làm mất nguồn gốc interruption | Giữ cause, document failure/cancellation policy |
| Restore trước blocking cleanup rồi lặp vô hạn | Cleanup có thể bị interrupt liên tục | Có chính sách cleanup và deadline, restore đúng thời điểm |
| Bỏ qua interruption vì "chỉ là test" | Test có thể treo và che race | Bảo toàn/propagate đúng, giới hạn thời gian chờ |

## 24. Khi nào **không** nên dùng `Thread.currentThread().interrupt()`?

Không phải mọi tình huống liên quan concurrency đều cần restore:

- **Không bắt `InterruptedException`:** nếu phương thức khai báo `throws InterruptedException`, cứ để exception đi lên.
- **Một cancellation contract đã được xử lý hoàn toàn** ở đúng tầng chịu trách nhiệm, có outcome rõ, và chính sách xác định signal không cần chuyển tiếp qua interrupt status; đây là ngoại lệ có chủ đích, không phải im lặng.
- **Đang xử lý một exception không liên quan tới interruption:** đừng tùy ý bật interrupt flag chỉ để "báo lỗi".
- **Chỉ muốn dừng một task khác:** không gọi `Thread.currentThread().interrupt()` để ngắt worker ở thread khác. Gọi `targetThread.interrupt()` hoặc cancellation API của task cần hủy.
- **Đang truyền một kết quả hủy qua cơ chế khác mà caller cần phân biệt:** có thể chọn một giao thức trả kết quả cụ thể, nhưng cần document rõ điều gì xảy ra với interrupt status ở ranh giới hiện tại.

Tuy nhiên, khi **đã catch `InterruptedException` do một lời gọi interruptible và không thể rethrow**, restore thường là **lựa chọn mặc định an toàn nhất** trừ khi contract nói rõ đã xử lý hoàn toàn.

## 25. Checklist review một catch `InterruptedException`

Trước khi chấp nhận code, tự hỏi:

1. **Câu lệnh nào ném `InterruptedException`?** API đó có clear interrupt flag khi ném không?
2. **Phương thức hiện tại có thể khai báo `throws InterruptedException` không?** Nếu có, ưu tiên propagate.
3. **Nếu không thể throws, đã gọi `Thread.currentThread().interrupt()` chưa?**
4. **Sau khi restore, execution path có kết thúc hoặc abort công việc đúng cách không?** Hay lại tiếp tục vòng chờ?
5. **Có cleanup cần thiết không?** Cleanup có block/interruptible không; restore trước hay sau được chọn có chủ đích không?
6. **Caller biết task bị hủy bằng cách nào?** Result/exception/future/state cần được document.
7. **Có side effect đã xảy ra trước interrupt không?** Retry hoặc rollback có yêu cầu idempotency không?
8. **Có nhầm caller interrupt với worker interrupt không?** `Future.get()` có thể ném trên thread đang đợi.
9. **Có dùng `Thread.interrupted()` làm mất flag không?** Nếu chỉ muốn kiểm tra, dùng `isInterrupted()`.
10. **Có blocking operation không hỗ trợ interrupt không?** Cần timeout/close riêng thay vì chỉ restore.
11. **Có thread pool tái sử dụng worker không?** Không dựa vào interrupt flag làm kênh completion cho task kế tiếp.
12. **Có kiểm thử cả interrupt đến sớm và đến trong lúc block không?** Dùng latch/condition điều phối, không dựa vào sleep phỏng đoán.
13. **Có tests chặn treo vô hạn không?** Với đường lỗi, dùng timeout ở test harness và shutdown cleanup rõ ràng.
14. **Status có được đọc ở đúng thời điểm không?** Để kiểm chứng restoration, đo trong chính worker trước khi task kết thúc, đừng dựa vào state hậu termination.

## 26. Câu hỏi tự kiểm tra

**1. Vì sao `Thread.sleep()` có thể khiến `Thread.currentThread().isInterrupted()` trả false trong `catch (InterruptedException e)`?**

Vì `sleep` xử lý interruption bằng cách ném `InterruptedException` và clear interrupt status theo hợp đồng. Nếu chưa xử lý hoàn toàn yêu cầu hủy, code catch nên restore trước khi nhường quyền.

**2. `Runnable.run()` có thể khai báo `throws InterruptedException` không?**

Không thể thêm checked exception đó khi override `Runnable.run()`. Trong `run` phải catch, restore rồi dừng hoặc dùng failure policy thích hợp.

**3. Tại sao `catch (...) { Thread.currentThread().interrupt(); }` chưa chắc đủ?**

Vì code vẫn có thể tiếp tục làm nghiệp vụ sau catch hoặc vào lại một blocking call. Cần thêm một đường `return`, `break` hoặc cancellation handling rõ.

**4. `Thread.interrupted()` và `isInterrupted()` khác nhau thế nào?**

`Thread.interrupted()` kiểm tra **current thread** và xóa status nếu đang bật. `isInterrupted()` đọc status của receiver mà không xóa.

**5. Nếu method đã `throws InterruptedException`, có cần catch để restore rồi throw tiếp không?**

Không cần mặc định. Truyền exception lên caller thường đủ để giữ tín hiệu cancellation. Restore thêm có thể là yêu cầu của contract cụ thể nhưng không phải quy tắc chung.

**6. Tại sao `Future.get()` ném `InterruptedException` không chứng minh worker đã bị interrupt?**

Vì ngoại lệ có thể xảy ra ở **caller đang chờ future**, trong khi task worker vẫn chạy. Cần phân biệt thread nào nhận interrupt.

**7. Restore interrupt flag có tự đánh thức một lời gọi I/O không hỗ trợ interruption không?**

Không. Cần hiểu hợp đồng I/O cụ thể và có thể cần timeout, close/cancel hoặc cơ chế khác.

**8. Cờ `AtomicBoolean stopRequested` có thay toàn bộ interruption được không?**

Không. Cờ hỗ trợ visibility của trạng thái do ứng dụng tự kiểm tra, không tự làm `BlockingQueue.take()` hay `Thread.sleep()` thức dậy.

**9. Sau khi restore flag, có nên tiếp tục retry cùng blocking operation vô hạn không?**

Thông thường không. Có thể tạo busy loop hoặc làm việc trái với yêu cầu hủy. Nên thoát hoặc chuyển sang policy cancellation đã document.

**10. Vì sao có thể cần restore **sau cleanup** thay vì ngay đầu catch?**

Vì một số cleanup có thể block và phản hồi ngay với interrupt status đang bật. Nếu có chủ đích, giữ lại thông tin interruption, thực hiện cleanup theo deadline/chính sách rồi restore trong finally.

## 27. Tổng kết

- **Interrupt là yêu cầu hợp tác**, không phải lệnh cưỡng ép dừng thread hay rollback công việc.
- Khi một API blocking ném `InterruptedException`, nhiều API chuẩn đã **clear interrupt flag**; nếu catch rồi bỏ qua, có thể đánh mất yêu cầu hủy.
- **Có thể propagate thì thường propagate.** Với `Runnable`, `Consumer`, callback hoặc method không cho thêm checked exception, **restore bằng `Thread.currentThread().interrupt()` rồi dừng/abort đúng contract**.
- **`isInterrupted()` đọc mà không clear; `Thread.interrupted()` đọc và clear current thread.** Không dùng nhầm.
- Restore flag **không tự kết thúc task**. Đường control flow phải hợp tác hủy; riêng blocking I/O cần policy thích hợp.
- Cleanup, timeout, exception propagation, trạng thái future và side effect đã xảy ra phải được thiết kế riêng; restore signal không giải quyết hết lifecycle.
- Không dựa vào lịch chạy hoặc `Thread.sleep` để test interruption. Dùng latch và timeout phù hợp; kiểm tra restoration trong worker và completion bằng cơ chế được bảo đảm.

Khi gặp `catch (InterruptedException e)` trong Java, câu hỏi trọng tâm là **"Ai chịu trách nhiệm xử lý yêu cầu hủy tiếp theo, và thông tin interrupt có được bảo toàn đến ranh giới đó không?"**. Nếu có thể truyền ngoại lệ, hãy truyền. Nếu không thể, **đặt lại interrupt flag trên current thread và thoát công việc theo giao thức rõ ràng**, thay vì vô tình nuốt tín hiệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-005">Điểm chặn hỗ trợ và không hỗ trợ interrupt</a>

<details>
<summary>Xem chi tiết</summary>

# Điểm chặn hỗ trợ và không hỗ trợ interrupt trong Java

## 1. Vì sao phân biệt các điểm chặn lại quan trọng?

Một thread có thể **bị chặn (blocked/waiting)** khi đợi monitor, lock, dữ liệu queue, tín hiệu từ thread khác, I/O hoặc thời gian trôi qua. Khi hệ thống muốn dừng công việc—chẳng hạn hủy task hoặc đóng ứng dụng—thường dùng `Thread.interrupt()`. Nhưng **không phải điểm chặn nào cũng phản ứng giống nhau với interrupt**.

Có ba nhóm chính:

1. **Điểm chặn interruptible:** thao tác đang chờ có thể thoát bằng `InterruptedException`, ví dụ `Thread.sleep()`, `Object.wait()`, `Thread.join()`, `BlockingQueue.take()`, `CountDownLatch.await()`, `ReentrantLock.lockInterruptibly()`.
2. **Điểm chặn không hủy việc đợi chỉ vì interrupt:** ví dụ thread chờ vào `synchronized` monitor, hoặc `ReentrantLock.lock()`. Interrupt **không cưỡng ép** nhả lock đang do thread khác giữ. Thread có thể tiếp tục phải đợi cho đến khi lock khả dụng.
3. **Điểm chặn có cách phản ứng riêng:** `LockSupport.park()` có thể trở về do interrupt nhưng **không ném `InterruptedException` và không tự xóa cờ**; I/O qua `InterruptibleChannel` có thể đóng channel và ném `ClosedByInterruptException`.

**Thông điệp cốt lõi:** `interrupt()` là một **yêu cầu hợp tác**, không phải `Thread.stop()`, không ép thread khác chấm dứt và không tự động giải phóng mọi tài nguyên mà thread đang giữ. Việc hủy có hiệu quả hay không phụ thuộc thao tác đang chờ, trạng thái interrupt và code của ứng dụng.

Bài này chỉ bàn **Java concurrency nói chung** trên JDK/Java 17+, không phụ thuộc framework.

## 2. Interrupt flag và các API cơ bản

Mỗi thread có **interrupt status** (cờ ngắt). Thread khác có thể gọi:

```java
worker.interrupt();
```

Đây là snippet: `worker` là tham chiếu `Thread` đã được khai báo. Hành động này yêu cầu thread worker chú ý đến interrupt; **không bảo đảm worker dừng ngay**, không làm nó tự phát sinh một exception tại mọi điểm thực thi.

Các API quan trọng:

| API | Tác dụng |
| --- | --- |
| `target.interrupt()` | Gửi tín hiệu interrupt tới thread đích |
| `target.isInterrupted()` | Đọc interrupt status của thread đích, **không xóa cờ** |
| `Thread.currentThread().isInterrupted()` | Đọc cờ của thread hiện tại, **không xóa cờ** |
| `Thread.interrupted()` | Static method đọc **và xóa** interrupt status của **thread hiện tại** |
| `InterruptedException` | Tín hiệu do nhiều thao tác chờ interruptible ném; **thông thường cờ đã được xóa khi ngoại lệ được ném** |

**Không nhầm hai cách kiểm tra:**

```java
boolean keepFlag = Thread.currentThread().isInterrupted();
boolean consumeFlag = Thread.interrupted();
```

Đây là snippet minh họa. `isInterrupted()` giữ nguyên cờ; `Thread.interrupted()` xóa cờ, nên gọi nó vô tình trong vòng lặp có thể khiến lần kiểm tra kế tiếp không còn thấy yêu cầu hủy.

Đối với các thao tác chờ ném `InterruptedException` như `sleep`/`wait`/`join`, khi bắt exception thường cần **truyền tiếp** hoặc **khôi phục cờ** bằng `Thread.currentThread().interrupt()` nếu không thể truyền tiếp. Không phục hồi cờ một cách máy móc trong mọi tình huống: tầng gọi có thể đã chuyển tín hiệu thành trạng thái hủy cuối cùng và không tiếp tục dùng thread. Quan trọng là phải có **hợp đồng xử lý interrupt rõ ràng** và không âm thầm nuốt yêu cầu hủy.

## 3. Bảng phân loại các điểm chặn thường gặp trong JDK

| Điểm chặn / API | Interrupt khi đang đợi | Cần chú ý |
| --- | --- | --- |
| `Thread.sleep(...)` | **Có**: ném `InterruptedException` | Không nhả monitor đang giữ; cờ thường bị xóa khi ném |
| `Object.wait(...)` | **Có**: ném `InterruptedException` | Nhả monitor được wait trong lúc đợi, lấy lại monitor trước khi trở về/ném |
| `Thread.join(...)` | **Có**: ném `InterruptedException` | Ngắt **thread đang join**, không tự ngắt worker được join |
| `BlockingQueue.put()/take()` | **Có** | Chờ dữ liệu/dung lượng; các timed `offer/poll` cũng interruptible |
| `CountDownLatch.await(...)` | **Có** | Timeout `await(timeout)` trả boolean, interrupt là ngoại lệ khác |
| `CyclicBarrier.await(...)` | **Có** | Barrier có thể chuyển sang broken state khi một waiter bị interrupt |
| `Semaphore.acquire(...)` | **Có** | `tryAcquire(timeout, unit)` cũng interruptible |
| `Semaphore.acquireUninterruptibly()` | **Không thoát do interrupt** | Tiếp tục đợi permit; khôi phục interrupt status theo hợp đồng khi hoàn tất |
| `ReentrantLock.lockInterruptibly()` | **Có** | Có thể dừng chờ **trước khi sở hữu khóa** |
| `ReentrantLock.tryLock(timeout, unit)` | **Có** | Trả false khi timeout; ném `InterruptedException` khi bị ngắt |
| `ReentrantLock.lock()` | **Không hủy việc đợi bằng interrupt** | Vẫn đợi lấy khóa; bảo toàn trạng thái interrupt theo hợp đồng |
| `synchronized (monitor)` khi monitor do thread khác giữ | **Không**: không có `InterruptedException` | Thread vẫn phải đợi monitor; cờ interrupt có thể vẫn được đặt |
| `Condition.await()/awaitNanos(...)` | **Có** | Nhả lock liên kết khi chờ; lấy lại lock trước khi tiếp tục |
| `Condition.awaitUninterruptibly()` | **Không thoát vì interrupt** | Vẫn đợi predicate/signal, bảo toàn dấu hiệu interrupt |
| `Future.get()/get(timeout, unit)` | **Có** | TimeoutException là kết quả khác với InterruptedException |
| `CompletableFuture.get(...)` | **Có** | `get()` có thể ném `InterruptedException` |
| `CompletableFuture.join()` | **Không ném `InterruptedException`** | Có thể chờ bất chấp interrupt và bảo toàn cờ; không nhầm với `Thread.join()` |
| `ExecutorService.awaitTermination(...)` | **Có** | Hết hạn trả false; interrupt là đường khác |
| `LockSupport.park()/parkNanos(...)` | **Phản ứng riêng**: có thể trở về khi interrupt | Không tự xóa cờ, không ném `InterruptedException`; có thể trở về không có signal |
| `InterruptibleChannel` đang thực hiện blocking I/O | **Có theo hợp đồng channel** | Có thể đóng channel và ném `ClosedByInterruptException` |
| `Selector.select(...)` | **Có thể đánh thức việc select** | Không phải InterruptedException; interrupt có tác dụng đánh thức selector và trạng thái cờ vẫn cần xử lý |
| Vòng tính toán CPU không kiểm tra cờ | **Không tự dừng** | Phải kiểm tra interrupt hợp tác hoặc dùng cơ chế hủy khác |
| `InputStream.read()` / legacy socket I/O nói chung | **Phụ thuộc implementation và đối tượng I/O** | Không hứa mọi blocking I/O đều interruptible; xem hợp đồng của lớp cụ thể |

**Quan trọng:** cùng một tên “blocking” không đồng nghĩa cùng ngữ nghĩa interrupt. Đọc **chữ ký API và hợp đồng**; một số thao tác thoát bằng `InterruptedException`, một số chặn không ngắt được, một số trả về hoặc ném ngoại lệ I/O riêng.

## 4. Java 17+: `Thread.sleep` phản hồi interrupt và cờ bị xóa

Tạo tệp `InterruptSleepDemo.java`:

```java
import java.util.concurrent.CountDownLatch;

public class InterruptSleepDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            try {
                started.countDown();
                Thread.sleep(60_000);
                System.out.println("Normal sleep completion");
            } catch (InterruptedException e) {
                System.out.println("Flag in catch: "
                        + Thread.currentThread().isInterrupted());
                Thread.currentThread().interrupt();
                System.out.println("Flag restored: "
                        + Thread.currentThread().isInterrupted());
            }
        }, "sleeper");

        worker.start();
        started.await();
        worker.interrupt();
        worker.join();

        System.out.println("Main done");
    }
}
```

Biên dịch và chạy:

```bash
javac InterruptSleepDemo.java
java InterruptSleepDemo
```

Đầu ra trong lần chạy bình thường, không có ngắt ngoài:

```text
Flag in catch: false
Flag restored: true
Main done
```

Lời gọi `started.countDown()` diễn ra trước khi worker vào `sleep`, và `main` gửi `interrupt` sau khi latch mở. Nếu interrupt đến **trước** khi worker thật sự gọi `sleep`, lần sleep kế tiếp vẫn thấy cờ đã đặt và ném `InterruptedException`. Nếu worker đã ngủ, interrupt đánh thức nó. Cả hai trường hợp đều dẫn đến catch; sleep dài chỉ để worker không tự hoàn tất trước khi main gửi interrupt.

`Thread.sleep` khi bị interrupt ném `InterruptedException` và xóa interrupt status theo hợp đồng. Ví dụ **chủ động khôi phục cờ**, để tầng gọi có thể thấy thread đã nhận yêu cầu hủy. Trong ứng dụng thật, worker thường nên thoát một cách có kiểm soát thay vì tiếp tục công việc lớn sau khi bắt interrupt.

**Đừng đặt `sleep` trong `synchronized` rồi nghĩ nó nhả monitor.** Sleep không giải phóng khóa đã giữ, dù nó có thể bị interrupt.

## 5. Điểm chặn không hỗ trợ hủy việc chờ: lấy intrinsic monitor

Điều khác biệt dễ mắc lỗi là **chờ đi vào `synchronized` không interruptible**. Nếu monitor đang bị thread A giữ, thread B cố vào khối `synchronized (monitor)` có thể ở trạng thái JVM `BLOCKED`. Gọi `B.interrupt()` không làm B tự ném `InterruptedException` để bỏ việc lấy monitor.

### Java 17+: interrupt không làm thread bỏ chờ monitor

Tạo file `MonitorWaitInterruptDemo.java`:

```java
import java.util.concurrent.CountDownLatch;

public class MonitorWaitInterruptDemo {
    public static void main(String[] args) throws InterruptedException {
        Object monitor = new Object();
        CountDownLatch attempted = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            attempted.countDown();
            synchronized (monitor) {
                System.out.println("Entered after interrupt: "
                        + Thread.currentThread().isInterrupted());
            }
        }, "monitor-worker");

        synchronized (monitor) {
            worker.start();
            attempted.await();
            worker.interrupt();
            // Main vẫn giữ monitor đến khi ra khỏi synchronized.
        }

        worker.join();
        System.out.println("Main done");
    }
}
```

Chạy:

```bash
javac MonitorWaitInterruptDemo.java
java MonitorWaitInterruptDemo
```

Đầu ra:

```text
Entered after interrupt: true
Main done
```

**Giải thích:**

1. Main giữ `monitor` trước khi khởi động worker.
2. Worker báo đã tới bước sắp yêu cầu monitor bằng `attempted.countDown()`.
3. Main gửi interrupt trong khi **vẫn đang giữ monitor**, nên worker không thể vào khối `synchronized` ngay cả nếu đã chạy đến dòng đó.
4. Khi main nhả monitor, worker lấy được nó và chạy vào khối. Cờ interrupt đã đặt vẫn còn để worker kiểm tra bằng `isInterrupted()`.
5. Worker không ném `InterruptedException` chỉ vì đang chờ monitor; chương trình không có lệnh catch quanh acquisition.

Có thể kiểm tra `Thread.getState()` khi thread thực sự đang chờ monitor để thấy `BLOCKED`, nhưng `getState()` chỉ là **snapshot**, không nên dùng để điều phối hoặc kiểm thử dựa vào timing.

### Phân biệt với `Object.wait()`

Cùng là intrinsic monitor nhưng **`Object.wait()` có thể bị interrupt**:

- Để gọi `monitor.wait()`, thread phải **đang giữ** monitor đó.
- Khi bắt đầu wait, thread **nhả monitor của object được wait**, cho phép thread khác vào sửa điều kiện và `notify/notifyAll`.
- Khi được đánh thức hoặc bị interrupt, thread phải **lấy lại monitor** theo hợp đồng wait trước khi trở về hoặc ném `InterruptedException`.
- Nếu một thread khác giữ monitor quá lâu, thread đang bị interrupt vẫn có thể phải **chờ lấy lại monitor** trước khi catch chạy. Vì thế interrupt không tạo cam kết về độ trễ đáp ứng tức thời.

Khi đợi một điều kiện, luôn dùng `while (!condition) monitor.wait()`, không chỉ `if`, vì có **spurious wakeup** và trạng thái có thể thay đổi trước khi thread giành lại monitor.

## 6. Điểm chặn hỗ trợ interrupt khi lấy khóa: `lockInterruptibly()`

`ReentrantLock.lock()` là thao tác chờ khóa nhưng **không phải interruptible acquisition**. Nếu khóa đang bị giữ, interrupt không khiến `lock()` tự thất bại để thoát. Nếu yêu cầu **có thể hủy trong lúc chờ khóa**, dùng `lockInterruptibly()` hoặc `tryLock(timeout, unit)`.

### Java 17+: dừng chờ trước khi chiếm ReentrantLock

Tạo file `InterruptibleLockDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

public class InterruptibleLockDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch attempted = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            boolean acquired = false;
            try {
                attempted.countDown();
                lock.lockInterruptibly();
                acquired = true;
                System.out.println("Unexpected lock acquisition");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Interrupted while waiting for lock");
            } finally {
                if (acquired) {
                    lock.unlock();
                }
            }
        }, "lock-worker");

        lock.lock();
        try {
            worker.start();
            attempted.await();
            worker.interrupt();
            worker.join(); // Worker có đường thoát do interrupt.
        } finally {
            lock.unlock();
        }

        System.out.println("Main released lock");
    }
}
```

Chạy:

```bash
javac InterruptibleLockDemo.java
java InterruptibleLockDemo
```

Đầu ra nếu không có interrupt ngoài:

```text
Interrupted while waiting for lock
Main released lock
```

Main giữ `lock` cho đến khi worker kết thúc. Worker không thể lấy lock, nhưng `lockInterruptibly()` kiểm tra interrupt và ném `InterruptedException`. Nếu interrupt đến trước khi worker gọi `lockInterruptibly()`, API cũng nhận biết interrupt status. Vì worker thoát việc đợi, main có thể `join` dù vẫn giữ khóa.

Mẫu này được sắp xếp để chứng minh **khả năng hủy chờ khóa**. Nó không phải khuyến nghị thường xuyên `join` worker trong khi giữ lock. Với `lock()` thường, cấu trúc main giữ lock rồi join worker đợi lock có thể deadlock.

**Điểm phải giữ:** chỉ gọi `unlock()` nếu worker thực sự acquire thành công. `lockInterruptibly()` thất bại bằng exception thì worker **không có quyền unlock**. Nếu code muốn thử trong một khoảng giới hạn, dùng `tryLock(timeout, unit)` và kiểm tra `boolean acquired`.

### `ReentrantLock.lock()` không interruptible thì cờ có mất không?

`ReentrantLock.lock()` vẫn chờ đến khi lấy được khóa, kể cả nếu bị interrupt trong lúc chờ; API duy trì interrupt status theo hợp đồng. Sau khi lấy khóa, code có thể chủ động kiểm tra `Thread.currentThread().isInterrupted()` và quyết định không tiếp tục một thao tác dài.

**Không** dùng `lock()` rồi tưởng có thể catch `InterruptedException` ở chính lời gọi đó. API `lock()` không khai báo ngoại lệ ấy.

## 7. `LockSupport.park`: thức dậy khi interrupt nhưng không ném checked exception

`LockSupport` là primitive cấp thấp cho park/unpark. `park()` có thể trở về vì:

- Một permit được cấp qua `unpark(thread)`;
- Thread đã bị interrupt;
- Một **spurious return** (trở về không do sự kiện mà code dự kiến).

`park()` **không** tự xóa interrupt status và **không** ném `InterruptedException`. Vì vậy, code phải kiểm tra điều kiện thực và cờ ngắt, thường trong vòng lặp có predicate rõ ràng.

### Java 17+: quan sát cờ sau park

Tạo file `ParkInterruptDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;

public class ParkInterruptDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            ready.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                LockSupport.park();
            }

            System.out.println("Flag after park: "
                    + Thread.currentThread().isInterrupted());
            System.out.println("Clear returned: "
                    + Thread.interrupted());
            System.out.println("Flag after clear: "
                    + Thread.currentThread().isInterrupted());
        }, "park-worker");

        worker.start();
        ready.await();
        worker.interrupt();
        worker.join();
    }
}
```

Chạy:

```bash
javac ParkInterruptDemo.java
java ParkInterruptDemo
```

Đầu ra:

```text
Flag after park: true
Clear returned: true
Flag after clear: false
```

Vòng `while` xử lý được **spurious return**: dù park trở về sớm, worker không ra khỏi vòng cho đến khi cờ được đặt. Khi interrupt đến, `park()` trở về hoặc vòng lặp thấy cờ đã có. `Thread.interrupted()` đọc và **xóa** cờ; đó là lý do dòng cuối là false.

**Lưu ý:** đây là ví dụ về interrupt flag. Nếu triển khai queue/lock bằng `LockSupport` thật sự, predicate “có thể tiến triển” phải được kiểm tra bằng giao thức đồng bộ phù hợp; việc `park/unpark` đơn độc không làm mọi shared mutable state an toàn.

## 8. Đợi mà “không ngắt được” có phải là luôn xấu?

Không hẳn. Có những thao tác cần giữ invariant ngay cả nếu người dùng đã hủy yêu cầu. Ví dụ một đoạn cập nhật ngắn có thể chờ lấy lock, hoàn thành một commit trong JVM rồi mới phản hồi tín hiệu interrupt. Tuy nhiên, **không interruptible** nghĩa là không thể dùng riêng `interrupt()` để đặt một giới hạn thời gian chờ chắc chắn tại điểm đó.

Một số API được thiết kế để **tiếp tục chờ dù bị interrupt**, đồng thời ghi nhớ cờ:

- `Semaphore.acquireUninterruptibly()`;
- `Condition.awaitUninterruptibly()` (chờ predicate/condition theo giao thức);
- `ReentrantLock.lock()`;
- `CompletableFuture.join()` (khác với `Thread.join()`).

Với các API này, phải có cơ chế **tiến triển**, timeout/giới hạn khác khi cần, hoặc bảo đảm tài nguyên cuối cùng sẽ được giải phóng. `Condition.awaitUninterruptibly()` có thể chờ mãi nếu không có signal/điều kiện thích hợp; interrupt đơn lẻ không phải cách thoát.

**Đừng chọn uninterruptible chỉ để tránh phải xử lý `InterruptedException`.** Làm vậy có thể khiến ứng dụng khó shutdown, các request bị hủy vẫn giữ thread lâu và hệ thống tích tụ việc chờ.

### `CompletableFuture.join()` không giống `Thread.join()`

- `Thread.join()` (bao gồm timed join) có thể ném `InterruptedException` khi **thread đang chờ** bị interrupt.
- `CompletableFuture.join()` trả kết quả hoặc ném `CompletionException`/`CancellationException` tương ứng, không khai báo `InterruptedException`; interrupt không phải đường thoát bình thường khỏi việc chờ của `join()`.
- `CompletableFuture.get()` lại là API chờ **interruptible**, có `InterruptedException`/`ExecutionException`; overload có timeout còn có `TimeoutException`.
- Timeout của caller **không** tự hủy task đang chạy; cần chính sách cancellation riêng.

Vì vậy cần chọn **API cụ thể**, không suy theo từ “join” trong tên phương thức.

## 9. Interrupt đối với Java I/O: luôn xem hợp đồng từng loại

Không thể nói “mọi I/O trong Java đều bị interrupt” hoặc “không I/O nào bị interrupt”. Cách xử lý khác nhau giữa stream truyền thống, socket, NIO channel và selector.

### `java.nio.channels.InterruptibleChannel`

Theo hợp đồng của `InterruptibleChannel`, khi một thread đang bị chặn trong I/O trên channel triển khai giao diện này mà bị interrupt, channel có thể bị đóng và thao tác ném `ClosedByInterruptException`, đồng thời interrupt status được đặt. Nếu một thread khác đóng channel trong khi thao tác đang chặn, nó có thể ném `AsynchronousCloseException`. Đây **không** phải `InterruptedException`.

Điều rất quan trọng: **channel bị đóng** không thể mặc nhiên dùng lại. Code phải xử lý vòng đời và mở lại tài nguyên nếu cần; không chỉ catch exception rồi retry trên cùng channel đã đóng.

### `Selector.select()`

Với selector NIO, interrupt một thread đang trong `select()` có tác dụng tương tự `wakeup()` đối với việc chọn kênh, nhưng không có nghĩa thao tác phải ném `InterruptedException`. Code phải xem kết quả select, trạng thái interrupt và vòng đời selector. Không được tưởng interrupt một selector tự hủy tất cả I/O của mọi channel đăng ký.

### `InputStream.read()` và socket truyền thống

Khả năng phản hồi interrupt **phụ thuộc class và cách tạo tài nguyên**. Một `InputStream` có thể không có hợp đồng hủy blocking read bằng `Thread.interrupt()`. Một số loại socket/channel có cơ chế đóng hoặc interrupt riêng. Ở Java 17 không nên áp đặt hành vi của virtual thread (xuất hiện ở các phiên bản Java mới hơn) cho platform thread chạy JDK 17.

Nếu dùng I/O, hãy đọc hợp đồng của lớp đang dùng, đặt timeout của socket/channel khi có hỗ trợ, hoặc quản lý việc đóng tài nguyên phù hợp. Với socket blocking, cần phân biệt timeout I/O và interrupt status của thread; chúng là các cơ chế khác nhau.

## 10. Interrupt, timeout và cancellation có mối quan hệ thế nào?

Một số thao tác cung cấp cả **interruptible wait** lẫn **timeout**:

| API | Timeout | Interrupt | Kết quả tiêu biểu |
| --- | --- | --- | --- |
| `BlockingQueue.poll(timeout, unit)` | Có | Có | Phần tử / `null` / `InterruptedException` |
| `ReentrantLock.tryLock(timeout, unit)` | Có | Có | `true` / `false` / `InterruptedException` |
| `CountDownLatch.await(timeout, unit)` | Có | Có | `true` / `false` / `InterruptedException` |
| `Future.get(timeout, unit)` | Có | Có | Giá trị / `TimeoutException` / `InterruptedException` / lỗi task |
| `Thread.join(timeout)` | Có | Có | Trả `void`; cần kiểm tra `isAlive()` sau đó |
| `Object.wait(timeout)` | Có | Có | Trở về; phải kiểm tra lại predicate |
| `Condition.awaitNanos(remaining)` | Có | Có | Ước lượng nanos còn lại; kiểm tra predicate |
| `Semaphore.tryAcquire(timeout, unit)` | Có | Có | `true` / `false` / `InterruptedException` |

**Timeout** nghĩa là khoảng chờ của caller đã hết; task hoặc producer **có thể vẫn đang chạy**. **Interrupt** là tín hiệu được gửi tới một thread. **Cancellation** là quyết định ở cấp task; thường có thể dùng interrupt như cơ chế gửi yêu cầu, nhưng không đồng nghĩa “đã hủy thành công mọi side effect”.

Ví dụ `Future.cancel(true)` yêu cầu hủy bằng cách cố interrupt thread chạy task nếu phù hợp. Nó không thể bảo đảm task tuân thủ interrupt hoặc dừng tại I/O/blocking point không hỗ trợ. Trạng thái `Future` có thể đã là cancelled trong khi mã của task vẫn đang thực thi một đoạn chưa phản hồi interrupt. Không được dùng cancellation flag của Future để suy ra tài nguyên ngoài ứng dụng chắc chắn đã rollback.

## 11. Thiết kế worker hợp tác với interrupt

Một vòng xử lý có thể kết hợp **điểm chặn interruptible** và **kiểm tra cờ tại các phần CPU**:

```java
// Snippet về cấu trúc một worker, chưa là file Java độc lập.
while (!Thread.currentThread().isInterrupted()) {
    try {
        Task task = queue.take(); // Interruptible.
        process(task);            // Có thể là CPU/I/O; cần hợp đồng riêng.
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
    }
}
cleanup();
```

Ở đây `Task`, `queue`, `process` và `cleanup` là các thành phần phải được khai báo trong ứng dụng thật. Khi `take()` bị interrupt, worker phục hồi cờ và thoát vòng; các thao tác cleanup thực hiện sau đó. Nếu `process(task)` chạy CPU lâu và không bao giờ kiểm tra interrupt, việc shutdown **vẫn có thể chậm** dù `queue.take()` là interruptible.

Đối với code CPU dài, kiểm tra `Thread.currentThread().isInterrupted()` định kỳ ở các điểm an toàn; xác định **sau bước nào** có thể dừng mà không phá dữ liệu. Đối với I/O, đặt timeout/đóng tài nguyên theo API; đối với lock, cân nhắc `lockInterruptibly`; đối với waiting loops, không nuốt interrupt và tiếp tục đợi vô hạn.

### Khi cần cleanup

Không được để interrupt làm bỏ qua `finally` giải phóng lock, permit, connection, file hoặc các tài nguyên đã acquire. Ngược lại, cũng không được **release khi chưa acquire thành công**:

```java
// Snippet: lock là ReentrantLock đã khai báo.
boolean acquired = false;
try {
    lock.lockInterruptibly();
    acquired = true;
    // Thực hiện công việc dưới lock.
} finally {
    if (acquired) {
        lock.unlock();
    }
}
```

Snippet này thuộc một phương thức khai báo `throws InterruptedException`. Cách viết phân biệt đường thành công và bị ngắt **trước khi lấy khóa**, tránh gọi `unlock()` sai owner.

## 12. Những lỗi phổ biến

1. **“`interrupt()` tương đương kill thread.”** Sai: nó chỉ gửi yêu cầu, code và API quyết định phản hồi.
2. **“Một thread chờ `synchronized` sẽ ném `InterruptedException`.”** Sai: monitor acquisition không interruptible theo cách đó.
3. **“`Object.wait()` và chờ vào monitor là một thứ.”** Sai: wait interruptible và nhả monitor trong lúc đợi; chờ lấy monitor thì không interruptible.
4. **“`ReentrantLock.lock()` sẽ dừng chờ khi bị interrupt.”** Sai: phải dùng `lockInterruptibly()` hoặc timed `tryLock` nếu cần đường thoát.
5. **“Sau khi catch `InterruptedException` cờ chắc chắn vẫn true.”** Sai: nhiều blocking API xóa cờ khi ném; có thể cần restore.
6. **“`Thread.interrupted()` chỉ đọc cờ.”** Sai: nó đọc **và xóa** cờ của thread hiện tại.
7. **“`park()` ném `InterruptedException`.”** Sai: nó trở về với cờ được giữ; còn có thể thức dậy không vì interrupt.
8. **“`Thread.sleep()` nhả monitor.”** Sai: sleep không nhả monitor hoặc lock đã giữ.
9. **“`CompletableFuture.join()` phản hồi interrupt giống `Thread.join()`.”** Sai: semantics khác nhau.
10. **“`Future.cancel(true)` bảo đảm tác vụ đã dừng.”** Sai: đó là yêu cầu hợp tác, có thể chưa được xử lý.
11. **“Mọi `InputStream.read()` đều bị `Thread.interrupt()` đánh thức.”** Không có bảo đảm chung; phải xem loại I/O cụ thể.
12. **“Ngắt một thread đang trong `InterruptibleChannel` chỉ tạo `InterruptedException` và channel không sao.”** Sai: có thể là `ClosedByInterruptException` và channel bị đóng.
13. **“Dùng uninterruptible API tránh được mọi phức tạp.”** Sai: chỉ chuyển vấn đề sang khả năng shutdown, backpressure hoặc deadlock.
14. **“Một khi đã nhận interrupt, không cần dọn dẹp.”** Sai: cleanup, rollback/ack và giải phóng tài nguyên vẫn phải đúng giao thức.
15. **“Gửi interrupt cho thread đang chờ worker sẽ tự interrupt worker.”** Sai: thread bị ngắt và thread được chờ là hai đối tượng khác nhau.

## 13. Checklist chọn blocking point trong Java

1. **Thao tác đang chờ điều gì?** Lock, monitor, dữ liệu queue, permit, tín hiệu, task, CPU hay I/O?
2. **API cụ thể có hỗ trợ interrupt không?** Đọc hợp đồng; không suy từ tên “blocking”.
3. **Phản hồi là gì?** `InterruptedException`, boolean/null, trả từ `park()`, hay ngoại lệ I/O như `ClosedByInterruptException`?
4. **Interrupt status còn hay đã bị xóa?** Kiểm tra và restore/propagate phù hợp; tránh gọi `Thread.interrupted()` vô tình.
5. **Có nên chờ không giới hạn không?** Nếu không, chọn API có timeout và dùng deadline chung nếu nhiều bước chờ.
6. **Có giữ lock/permit/tài nguyên không?** Sắp xếp `try/finally`; chỉ nhả những gì đã acquire.
7. **Công việc CPU/I/O có điểm kiểm tra không?** Nếu không, interrupt chỉ được phản hồi ở điểm chặn tiếp theo hoặc không phản hồi.
8. **Làm sao hủy tác vụ thật sự?** Xem xét `Future.cancel`, interrupt hợp tác, cờ trạng thái, timeout I/O và đóng tài nguyên khi cần.
9. **Hủy giữa chừng có phá invariant không?** Xác định điểm dừng an toàn và xử lý side effect một phần.
10. **Có kiểm thử được các đường ngắt không?** Dùng latch/khóa để đặt thread vào trạng thái chờ có kiểm soát, rồi gửi interrupt và xác minh thời điểm thoát, cờ và cleanup.
11. **Có nhiều loại lock/wait lồng nhau không?** Interrupt ở một điểm chặn không tự tháo vòng deadlock hay làm thread khác nhả khóa.
12. **Ứng dụng shutdown có tiến triển không?** Xem thời hạn kết thúc, số thread còn chờ và khả năng xử lý interrupt dưới tải thật.

**Ghi nhớ:** một số điểm chặn Java **ném `InterruptedException` và thoát chờ**, một số **vẫn đợi dù bị interrupt**, và một số có **cơ chế trả về/ngoại lệ riêng**. Muốn hủy an toàn, phải biết chính xác API đang chờ, xử lý interrupt status đúng cách, thiết kế cleanup trong `finally` và không đánh đồng interrupt với khả năng cưỡng ép chấm dứt task.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-006">Thiết kế cancellation flag, volatile và cleanup</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế cancellation flag, volatile và cleanup trong Java Concurrency

Trong một ứng dụng đa luồng Java, **hủy tác vụ (cancellation)** không đơn giản là “giết một thread”. Khi thread đang xử lý một vòng lặp, chờ dữ liệu từ queue, thực hiện I/O hoặc giữ tài nguyên, việc chấm dứt phải được **phối hợp**: bên yêu cầu hủy công bố ý định, worker nhận biết ý định ấy tại điểm an toàn, dừng nhận việc mới, rồi **giải phóng tài nguyên** trước khi kết thúc.

Ba khái niệm có vai trò khác nhau:

- **Cancellation flag:** biến trạng thái biểu thị đã yêu cầu dừng, thường dùng `volatile boolean` hoặc `AtomicBoolean` để các thread chia sẻ nhận biết thay đổi.
- **Interruption:** `Thread.interrupt()` gửi tín hiệu hợp tác; đặc biệt hữu ích để đánh thức các thao tác chờ có thể bị ngắt như `sleep()`, `wait()`, `BlockingQueue.take()`, `Condition.await()`.
- **Cleanup:** code `finally`, `try-with-resources` hoặc một lifecycle rõ ràng bảo đảm dọn dẹp **khi worker thực sự rời khỏi phần việc**, không phải ngay tại thời điểm bên ngoài gọi `cancel()`.

**Nguyên tắc:** yêu cầu hủy không đồng nghĩa đã hủy xong. Bên gọi cần phân biệt trạng thái “đã gửi yêu cầu”, “worker đã xác nhận”, “worker đã kết thúc” và “tài nguyên đã được dọn”.

## 1. Vì sao Java dùng cooperative cancellation?

Java hiện đại khuyến khích **cooperative cancellation**: thread tự quyết định khi nào có thể dừng mà không làm hỏng invariant của dữ liệu. Một yêu cầu hủy hợp lý cần xác định:

1. **Ai có quyền yêu cầu dừng?** Owner của tác vụ, dịch vụ quản lý worker hoặc thread gọi.
2. **Yêu cầu được lưu ở đâu?** Biến cờ dễ quan sát hoặc trạng thái lifecycle được phối hợp.
3. **Worker kiểm tra khi nào?** Đầu/cuối mỗi vòng, trước khi nhận việc mới, sau khi hoàn tất bước không thể chia nhỏ, hoặc khi một blocking call bị interrupt.
4. **Làm sao đánh thức worker đang chờ?** `interrupt()`, queue/sentinel, condition signal, đóng nguồn dữ liệu… phù hợp với loại thao tác chờ.
5. **Cleanup do ai thực hiện?** Thread sở hữu tài nguyên, trong `finally` hay `try-with-resources`, hoặc thành phần lifecycle có contract rõ ràng.
6. **Làm sao biết đã dừng thật?** `Thread.join()`, `Future.get()`, `ExecutorService.awaitTermination()` hoặc cơ chế hoàn thành tương đương.

Tránh dùng `Thread.stop()`: đây là API cũ không an toàn, có thể làm thread dừng khi đang giữ invariant chưa hoàn tất hoặc đang thao tác với tài nguyên chung. `interrupt()` **không phải** phiên bản an toàn của ép dừng: nó là một yêu cầu hợp tác, và worker vẫn cần thực thi logic chấm dứt.

## 2. Cancellation flag và volatile: công bố ý định dừng

Một cờ đơn hướng tương đối đơn giản:

```java
private volatile boolean cancelRequested;

public void requestCancel() {
    cancelRequested = true;
}
```

Đây là trích đoạn trong class, không phải chương trình độc lập. `volatile` phù hợp nếu chỉ cần **một thread ghi true, nhiều thread đọc**, và không có quy trình chuyển trạng thái phức tạp hơn.

Theo Java Memory Model (JMM), thao tác **ghi volatile** có quan hệ đồng bộ hóa với những lần đọc volatile tương ứng về sau theo thứ tự đồng bộ hóa. Thread đọc cờ trong vòng lặp có cơ sở nhìn thấy thay đổi của thread yêu cầu hủy; compiler/JIT không được coi cờ là hằng bất biến rồi bỏ qua việc đọc cần thiết.

Không có `volatile`, một thread đọc shared boolean không được đồng bộ hóa thích hợp có thể gặp lỗi **visibility**: worker không có bảo đảm thấy thay đổi từ thread kia trong thời gian mong đợi. Chạy thử trên máy cá nhân và “lúc nào cũng dừng” không chứng minh code đúng theo JMM.

Tuy nhiên, `volatile` **không cung cấp** các đặc tính sau:

- Không làm `if (!cancelRequested) performWork()` thành một transaction nguyên tử: cờ có thể chuyển sang true **sau khi kiểm tra** nhưng trước khi hành động bắt đầu.
- Không đánh thức thread đang chặn vô thời hạn trong `queue.take()`, `wait()` hoặc `Condition.await()`. Worker phải có đường thức dậy độc lập.
- Không làm cập nhật `x++`, chuyển trạng thái nhiều field hoặc cleanup đồng thời trở nên nguyên tử.
- Không chứng minh worker **đã kết thúc** hay cleanup hoàn tất chỉ vì `cancelRequested == true`.

Do đó cần xác định nghĩa của cờ: **“đã yêu cầu dừng”**, chứ không phải **“đã dừng xong”**.

## 3. Ví dụ 1 — Volatile flag cho vòng xử lý không chặn

Đây là ví dụ nhỏ tập trung vào visibility. Worker chủ động đọc cờ mỗi vòng; `Thread.onSpinWait()` là một gợi ý cho runtime trong vòng spin, **không** phải primitive chờ có thể bị interrupt và không làm tiết kiệm CPU như blocking queue. Không dùng vòng spin này làm mặc định cho tác vụ production.

Tạo file **`VolatileCancellationDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class VolatileCancellationDemo {
    static final class Worker extends Thread {
        private volatile boolean cancelRequested;
        private final CountDownLatch started = new CountDownLatch(1);
        private final AtomicInteger cleanupCount = new AtomicInteger();

        void requestCancel() {
            cancelRequested = true;
        }

        void awaitStart() throws InterruptedException {
            started.await();
        }

        int cleanupCount() {
            return cleanupCount.get();
        }

        boolean cancellationRequested() {
            return cancelRequested;
        }

        @Override
        public void run() {
            started.countDown();
            try {
                while (!cancelRequested) {
                    // Chỉ để học cách quan sát volatile; tránh busy-spin thực tế.
                    Thread.onSpinWait();
                }
            } finally {
                cleanupCount.incrementAndGet();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Worker worker = new Worker();
        worker.start();
        worker.awaitStart();

        worker.requestCancel();
        worker.join();

        System.out.println("Cancellation requested: "
                + worker.cancellationRequested());
        System.out.println("Cleanup calls: " + worker.cleanupCount());
        System.out.println("Worker alive: " + worker.isAlive());
    }
}
```

```bash
javac VolatileCancellationDemo.java
java VolatileCancellationDemo
```

Kết quả khi không có interrupt từ bên ngoài:

```text
Cancellation requested: true
Cleanup calls: 1
Worker alive: false
```

**Phân tích:** main chờ worker bắt đầu qua `CountDownLatch`, ghi cờ `volatile`, sau đó `join()` đợi worker hoàn tất. Worker thấy yêu cầu dừng trong vòng lặp và đi qua `finally`; cleanup chạy một lần. `join()` mang lại quan hệ *happens-before* từ hành động trong worker tới thread chờ sau khi phát hiện kết thúc, nên main quan sát được kết quả hoàn tất.

Với một vòng có công việc thực, hãy kiểm tra cờ **giữa những đơn vị công việc có thể dừng** (batch, item, bước tính). Không nên bỏ dở một phép cập nhật đang giữ lock hoặc giữa chừng một transaction chỉ vì cờ chuyển sang true; xác định **điểm hủy an toàn** rồi dừng ở đó.

## 4. Vì sao flag không đủ khi worker bị chặn?

Xét:

```java
while (!cancelRequested) {
    String item = queue.take();
    process(item);
}
```

Đây là trích đoạn minh họa: `queue` là `BlockingQueue<String>`, `process()` do ứng dụng định nghĩa, `take()` ném `InterruptedException`. Nếu queue rỗng, worker kẹt ở `take()`; việc thread khác đặt `cancelRequested = true` **không buộc `take()` trả về**. Nó chỉ kiểm tra lại cờ nếu chờ kết thúc theo một đường nào đó.

Các phương án tùy contract:

- Gửi **`worker.interrupt()`** để yêu cầu blocking API hỗ trợ interruption chấm dứt việc chờ.
- Dùng `poll(timeout, unit)` để worker định kỳ trở về kiểm tra cờ, nếu timeout có ý nghĩa với thiết kế.
- Đưa một **sentinel / poison pill** vào queue để báo kết thúc, nếu số lượng consumer và việc đóng queue được kiểm soát đúng.
- Với `Condition.await()`, cập nhật predicate đóng/hủy dưới lock rồi `signalAll()` cho các condition queues bị ảnh hưởng.
- Nếu đợi I/O không hỗ trợ interruption, có thể cần đóng tài nguyên qua cơ chế API riêng hoặc thiết kế timeout cấp I/O.

**Không mặc định mọi lời gọi blocking đều phản ứng với interrupt**. `Thread.interrupt()` không cưỡng bức dừng socket, native call, thư viện ngoài hoặc đoạn tính toán không kiểm tra cờ interrupt. Phải đọc hợp đồng API được sử dụng.

## 5. Interruption: một kênh tín hiệu hợp tác

Mỗi thread có một **interrupt status**. `thread.interrupt()` yêu cầu đặt trạng thái này (và có thể làm các chờ có hỗ trợ interrupt ném `InterruptedException`). Một worker có thể quan sát nó:

```java
if (Thread.currentThread().isInterrupted()) {
    // Thoát tại điểm dừng an toàn.
}
```

Hoặc trong vòng lặp:

```java
while (!Thread.currentThread().isInterrupted()) {
    doUnitOfWork();
}
```

Hai trích đoạn trên không độc lập và cần định nghĩa `doUnitOfWork()`. `isInterrupted()` **không xóa** trạng thái interrupt, còn phương thức static `Thread.interrupted()` **đọc rồi xóa** interrupt status của thread hiện tại. Dùng nhầm `Thread.interrupted()` trong điều kiện vòng lặp có thể làm mất thông tin hủy mà tầng trên cần xử lý.

Các thao tác như `Thread.sleep()`, `Object.wait()`, `BlockingQueue.take()`, `Condition.await()` hoặc `Thread.join()` có thể ném `InterruptedException`. Theo hợp đồng thông thường, khi exception được ném vì interrupt, trạng thái interrupt **bị xóa**, nên nếu không ném exception tiếp mà cần giữ tín hiệu, hãy khôi phục:

```java
try {
    queue.take();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // Thoát hoặc giao trách nhiệm hủy cho tầng trên.
}
```

Đây là trích đoạn, giả định queue đã khai báo. Khôi phục cờ **không** có nghĩa cứ lặp lại blocking call vô hạn: nếu cờ đang được đặt, lần chờ tiếp có thể lập tức ném exception và tạo vòng lặp nóng. Thường nên thoát vòng xử lý hoặc ném tiếp để tầng trên quyết định.

Khi worker dùng nhiều primitive hủy, nên định nghĩa rõ interrupt mang nghĩa gì: yêu cầu **shutdown**, hủy một đơn vị công việc hay đánh thức tạm thời? Không dùng interruption cho mục đích điều khiển tùy tiện nếu nơi khác của chương trình đã sở hữu hợp đồng hủy thread đó.

## 6. Ví dụ 2 — Interrupt đánh thức BlockingQueue.take(), cleanup trong finally

Ví dụ sau tạo worker đang chờ item trên queue rỗng. Main đợi worker bước vào vùng chuẩn bị chờ, sau đó interrupt; worker bắt `InterruptedException`, khôi phục cờ và đi qua `finally` để đóng tài nguyên mô phỏng.

Tạo file **`InterruptibleCleanupDemo.java`**:

```java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterruptibleCleanupDemo {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean closed = new AtomicBoolean();

        Thread worker = new Thread(() -> {
            try {
                entered.countDown();
                queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Worker interrupted");
            } finally {
                closed.set(true);
            }
        }, "queue-worker");

        worker.start();
        entered.await();
        worker.interrupt();
        worker.join();

        System.out.println("Resource closed: " + closed.get());
        System.out.println("Worker alive: " + worker.isAlive());
    }
}
```

```bash
javac InterruptibleCleanupDemo.java
java InterruptibleCleanupDemo
```

Kết quả:

```text
Worker interrupted
Resource closed: true
Worker alive: false
```

`entered.countDown()` xảy ra trước lời gọi `take()`, nên main có thể interrupt **trước khi worker chạm đến take** hoặc **khi nó đang đợi**. Cả hai đều được `take()` xử lý như một đường interruption theo hợp đồng. Thread không dùng `System.exit()` hoặc `stop()`; nó chạy qua `finally`. Sau `join()`, main xác nhận worker đã kết thúc và cleanup flag được đặt.

Trong thực tế, cleanup có thể là đóng stream, trả kết nối về pool hoặc dọn một tài nguyên đã được thread sở hữu. Ví dụ đặt `AtomicBoolean` chỉ để chứng minh **đã đi qua nhánh finally**; nó không đại diện cho một resource bên ngoài.

## 7. Kết hợp volatile flag và interrupt: yêu cầu hủy + đánh thức

Một pattern phổ biến là: `requestCancel()` ghi cờ **trước**, rồi gọi `interrupt()` lên worker. Cờ giữ **ý định dừng**, interrupt đánh thức lời chờ có hỗ trợ. Worker kiểm tra cờ khi thức dậy, hoàn tất cleanup và chấm dứt.

```java
// Trích đoạn trong class quản lý một Thread worker.
private volatile boolean cancelRequested;

public void requestCancel() {
    cancelRequested = true;
    worker.interrupt();
}
```

Trình tự cờ trước → interrupt làm ý định hủy đã được công bố khi worker kiểm tra sau khi thức dậy. Nếu worker chưa vào blocking call, interrupt status cũng có thể được blocking API quan sát. Nhưng nó không bảo đảm task dừng tức thời: worker vẫn có thể đang ở điểm xử lý không thể ngắt, hoặc có đường làm việc không kiểm tra cờ.

### Ví dụ 3 — Service nhỏ tự sở hữu vòng đời worker

Tạo file **`CooperativeServiceDemo.java`**:

```java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class CooperativeServiceDemo {
    static final class Service {
        private final BlockingQueue<Integer> queue =
                new LinkedBlockingQueue<>();
        private final CountDownLatch started = new CountDownLatch(1);
        private final AtomicInteger cleanups = new AtomicInteger();
        private volatile boolean cancelRequested;
        private final Thread worker = new Thread(this::runLoop, "service-worker");

        void start() {
            worker.start();
        }

        void awaitStarted() throws InterruptedException {
            started.await();
        }

        void requestCancel() {
            cancelRequested = true;
            worker.interrupt();
        }

        void awaitTermination() throws InterruptedException {
            worker.join();
        }

        int cleanupCalls() {
            return cleanups.get();
        }

        boolean cancelRequested() {
            return cancelRequested;
        }

        boolean isAlive() {
            return worker.isAlive();
        }

        private void runLoop() {
            started.countDown();
            try {
                while (!cancelRequested) {
                    try {
                        Integer item = queue.take();
                        // Nếu hủy đã được yêu cầu trong lúc chờ:
                        if (cancelRequested) {
                            break;
                        }
                        process(item);
                    } catch (InterruptedException e) {
                        if (cancelRequested) {
                            break;
                        }
                        // Contract demo: interrupt bất ngờ cũng kết thúc service.
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                cleanups.incrementAndGet();
            }
        }

        private void process(int item) {
            // Mô phỏng xử lý nhanh; chương trình không cần có item để hủy.
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Service service = new Service();
        service.start();
        service.awaitStarted();
        service.requestCancel();
        service.awaitTermination();

        System.out.println("Cancel requested: " + service.cancelRequested());
        System.out.println("Cleanup calls: " + service.cleanupCalls());
        System.out.println("Service alive: " + service.isAlive());
    }
}
```

```bash
javac CooperativeServiceDemo.java
java CooperativeServiceDemo
```

Kết quả:

```text
Cancel requested: true
Cleanup calls: 1
Service alive: false
```

Giao thức service:

1. `Service` là chủ sở hữu thread; API bên ngoài chỉ gọi `requestCancel()` và `awaitTermination()`, không tự truy cập trực tiếp queue/worker.
2. `cancelRequested` là `volatile`, nên thread khác quan sát được ý định dừng theo JMM; `interrupt()` giúp kết thúc `take()` đang chặn.
3. Worker kiểm tra cờ ở đầu vòng và sau khi `take()` trở về, không tiếp tục xử lý item mới sau khi đã phát hiện yêu cầu hủy.
4. Bất kể dừng vì flag hay vì `InterruptedException`, khối `finally` chạy và cập nhật `cleanups`.
5. `join()` cung cấp mốc **đã kết thúc**; chỉ sau đó main kết luận cleanup trong worker đã thực hiện.

**Ranh giới:** worker có thể đã bắt đầu xử lý một item **ngay trước** khi nhận lệnh hủy. Một phép kiểm tra cờ không khóa được toàn bộ thao tác nghiệp vụ tiếp theo; nếu cần đảm bảo “không có tác dụng phụ sau khi API cancel trả về”, phải thiết kế giao thức mạnh hơn như trạng thái atomically guarded, khóa, transaction hoặc đợi hủy hoàn tất. Mẫu này đảm bảo **dừng hợp tác tại điểm kiểm tra**, không phải cắt ngang ngay lập tức.

Với service thực tế, cần quy định `start()` chỉ gọi một lần, `requestCancel()` trước start có hợp lệ hay không, các item còn trong queue được xử lý hay bỏ, và xử lý item quá lâu hoặc I/O không ngắt được. Demo gọi đúng thứ tự `start → awaitStarted → requestCancel → awaitTermination`.

## 8. Cleanup bằng finally, try-with-resources và ownership

Một worker thường sở hữu resource trong khoảng vòng đời xử lý. Quy tắc dễ kiểm chứng:

```java
Resource resource = acquireResource();
try {
    while (!cancelRequested) {
        useResource(resource);
    }
} finally {
    resource.close();
}
```

Đây là mã ý tưởng, không phải chương trình độc lập: `Resource`/`acquireResource()`/`useResource()` cần tự định nghĩa; xử lý checked exception của `close()` tùy API.

Nếu resource triển khai `AutoCloseable`, ưu tiên **try-with-resources** cho ownership từ lúc tạo tới lúc hủy:

```java
try (Resource resource = acquireResource()) {
    while (!cancelRequested) {
        useResource(resource);
    }
}
```

`try-with-resources` gọi `close()` khi ra khỏi khối do hoàn tất, `break`, `return` hoặc exception. Một ngoại lệ ở `close()` có thể được ghi dưới dạng **suppressed exception** nếu đã có exception chính từ thân khối. Việc cleanup cần được thiết kế để không che mất nguyên nhân lỗi và không tạo deadlock khi đang giữ lock.

**Sở hữu rõ ràng:** worker không nên tùy tiện đóng resource đang được thread khác dùng nếu đó là resource chia sẻ. Bên quản lý phải xác định ai có quyền đóng, khi nào đóng, xử lý close lặp ra sao và liệu close có thể chặn không.

### Ví dụ 4 — Try-with-resources vẫn đóng khi worker bị interrupt

Tạo file **`ResourceCleanupDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class ResourceCleanupDemo {
    static final class Resource implements AutoCloseable {
        private final AtomicInteger closeCount;

        Resource(AtomicInteger closeCount) {
            this.closeCount = closeCount;
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicInteger closeCount = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            try (Resource resource = new Resource(closeCount)) {
                entered.countDown();
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "resource-worker");

        worker.start();
        entered.await();
        worker.interrupt();
        worker.join();

        System.out.println("Close calls: " + closeCount.get());
        System.out.println("Worker alive: " + worker.isAlive());
    }
}
```

```bash
javac ResourceCleanupDemo.java
java ResourceCleanupDemo
```

Kết quả:

```text
Close calls: 1
Worker alive: false
```

Nếu interrupt đến khi worker đang ngủ thì `sleep()` ném `InterruptedException`. Nếu đến trước `sleep()`, trạng thái interrupt khiến lời ngủ xử lý tín hiệu theo hợp đồng. Dù đường nào, khi rời khỏi khối try-with-resources, `close()` được gọi một lần. Main chờ `join()` rồi mới in kết quả; `60_000` chỉ là thời gian ngủ dự phòng để demo thao tác chặn, không phải thời gian chương trình sẽ phải chờ khi đã interrupt thành công.

**Điểm giới hạn:** `finally`/try-with-resources không bảo vệ trước mọi kiểu sự cố (ví dụ JVM bị dừng đột ngột, process bị kill hoặc mất điện). Cleanup tài nguyên bên ngoài có thể cần timeout, recovery hoặc giao thức đóng riêng.

## 9. Cancellation nhiều lần: dùng AtomicBoolean cho chuyển trạng thái một lần

Nếu nhiều caller có thể đồng thời hủy một service, thao tác `cancelRequested = true` lặp lại thường không nguy hiểm **nếu chỉ là ghi cờ**. Nhưng nếu “lần đầu cancel” còn có tác dụng phụ **phải chạy tối đa một lần** (phát một tín hiệu đóng, cập nhật lifecycle, gửi lời nhắc), kiểm tra rồi đặt cờ bằng hai bước không đủ.

```java
// KHÔNG nguyên tử nếu hai caller gọi đồng thời:
if (!cancelRequested) {
    cancelRequested = true;
    performCancelHook();
}
```

`volatile` không tự làm nguyên tử chuỗi check-then-act này. Dùng `AtomicBoolean.compareAndSet(false, true)` để bảo vệ quyền trở thành caller đầu tiên:

```java
if (cancelRequested.compareAndSet(false, true)) {
    performCancelHook();
}
```

Đây là trích đoạn, với `cancelRequested` là `AtomicBoolean`. Nếu hook có thể ném exception hoặc bị ngắt giữa chừng, **CAS chỉ đảm bảo quyền chạy hook được cấp một lần, không đảm bảo hook thực sự hoàn tất**. Cần chính sách retry/recovery khác khi nghiệp vụ yêu cầu thực hiện đúng một lần và chịu lỗi.

### Ví dụ 5 — Nhiều caller cancel, chỉ một lượt chấp nhận chuyển trạng thái

Tạo file **`IdempotentCancelDemo.java`**:

```java
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class IdempotentCancelDemo {
    static final class CancellationController {
        private final AtomicBoolean cancelRequested = new AtomicBoolean();
        private final AtomicInteger firstRequests = new AtomicInteger();

        boolean requestCancel() {
            if (cancelRequested.compareAndSet(false, true)) {
                firstRequests.incrementAndGet();
                return true;
            }
            return false;
        }

        boolean isCancellationRequested() {
            return cancelRequested.get();
        }

        int firstRequestCount() {
            return firstRequests.get();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        CancellationController controller = new CancellationController();

        Thread first = new Thread(controller::requestCancel);
        Thread second = new Thread(controller::requestCancel);
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("Cancel requested: "
                + controller.isCancellationRequested());
        System.out.println("First requests: "
                + controller.firstRequestCount());
    }
}
```

```bash
javac IdempotentCancelDemo.java
java IdempotentCancelDemo
```

Kết quả:

```text
Cancel requested: true
First requests: 1
```

CAS chỉ cho một thread chuyển trạng thái từ false sang true. Bộ đếm minh họa “số lần nhận quyền lần đầu”, **không phải bộ đếm cleanup**. Cleanup phải gắn với lifecycle worker và được chờ hoàn tất riêng nếu caller cần xác nhận.

`AtomicBoolean` cũng có semantics hiển thị bộ nhớ tương đương với các thao tác atomic phù hợp; với yêu cầu chỉ đặt một cờ true đơn hướng và không có hook, `volatile boolean` thường đã đủ và đơn giản hơn.

## 10. Vòng đời hủy: requested, stopping, terminated

Một boolean có thể đủ cho yêu cầu “hãy dừng khi có thể”, nhưng nhiều dịch vụ cần phân biệt:

```text
NEW -> RUNNING -> STOP_REQUESTED -> STOPPING -> TERMINATED
                                  \-> FAILED
```

Đây là sơ đồ **lifecycle logic của dịch vụ**, không phải tên các giá trị trong enum `Thread.State` của JVM. Một thiết kế rõ ràng có thể định nghĩa:

- **RUNNING:** nhận việc và thực hiện bình thường.
- **STOP_REQUESTED:** đã công bố yêu cầu, worker có thể vẫn đang bận.
- **STOPPING:** worker đã vào đường thoát, ngừng nhận việc mới và làm cleanup.
- **TERMINATED:** mọi phần việc thuộc quyền sở hữu worker đã dừng; cleanup có thể xác nhận theo contract.
- **FAILED:** worker thoát do exception; không tự suy thành “hủy thành công”.

Với state machine có nhiều nhánh, `AtomicReference<Lifecycle>` và CAS, monitor hoặc khóa phù hợp dễ bảo đảm chuyển trạng thái hợp lệ hơn việc tạo nhiều boolean rời rạc. Cần tránh các trạng thái vô nghĩa như `terminated=true` trong khi cleanup chưa được hoàn tất, trừ khi contract định nghĩa điều đó rõ ràng.

**Lưu ý:** `Thread.isAlive() == false` sau khi thread từng được start cho biết thread đã kết thúc, nhưng không cho biết nó thành công, bị cancel hay thất bại. Nếu cần phân biệt, lưu kết quả/lỗi và chỉ đọc sau một cơ chế đồng bộ hoàn thành phù hợp.

## 11. Chờ kết thúc: join(), timeout và hủy không hoàn tất

Sau khi gọi `requestCancel()`, bên quản lý có thể đợi worker:

```java
worker.interrupt();
worker.join();
```

Đây là trích đoạn bên ngoài worker; phương thức chứa phải xử lý hoặc khai báo `InterruptedException`. `join()` **không tự yêu cầu dừng** mà chỉ đợi thread đích kết thúc. Nếu worker không bao giờ hợp tác hoặc kẹt trong I/O không ngắt được, `join()` không thời hạn có thể chờ mãi.

Nếu dùng `join(timeoutMillis)`, việc trả về **không đảm bảo đã kết thúc**: có thể là worker hoàn tất hoặc timeout. Phải kiểm tra:

```java
worker.join(1_000);
if (worker.isAlive()) {
    // Chưa thể khẳng định cleanup hoàn tất;
    // báo timeout/chuyển sang chính sách phục hồi.
}
```

Đây là trích đoạn minh họa, cần xử lý `InterruptedException`. Đối với overload `join(long)`, giá trị 0 có nghĩa **chờ vô thời hạn**, không phải “không chờ”. Một hạn chờ có ý nghĩa phải lớn hơn 0.

`join()` sau khi quan sát thread kết thúc còn tạo quan hệ **happens-before** từ các hành động trong worker đến các hành động sau join của thread đợi. Nhờ vậy, bên quản lý có thể đọc dữ liệu hoàn tất/cleanup mà worker đã ghi trước khi kết thúc, miễn không có truy cập tranh chấp ngoài giao thức.

**Không gọi `join()` trên chính thread hiện tại** như một cách “chờ cleanup”; nó sẽ tự chờ chính mình và có thể bị treo. Worker nên thực thi cleanup trong đường thoát; thread quản lý mới chịu trách nhiệm chờ.

## 12. Future.cancel(true) và ExecutorService: cùng bản chất hợp tác

Khi công việc được gửi qua `ExecutorService`, caller thường không giữ trực tiếp `Thread`:

```java
Future<?> future = executor.submit(task);
boolean accepted = future.cancel(true);
```

Đây là trích đoạn, `executor` và `task` đã có. `cancel(true)` yêu cầu hủy `Future`; nếu task đang chạy, implementation có thể cố interrupt thread thực thi. **Nó không bảo đảm code task đã kết thúc hoặc cleanup đã hoàn tất** vào đúng thời điểm `cancel(true)` trả về. `Future.isDone()` cũng có thể true khi trạng thái Future đã canceled mà worker vẫn đang thực hiện cleanup.

Với executor, cần phân biệt:

- `shutdown()`: ngừng nhận task mới nhưng cho task đã nộp được tiếp tục theo contract.
- `shutdownNow()`: cố dừng các task đang chạy (thường bằng interrupt) và trả các task còn trong hàng chờ; không bảo đảm mọi task dừng ngay.
- `awaitTermination(timeout, unit)`: đợi executor thực sự terminated, có kết quả boolean để phân biệt timeout.

Nếu task mở resource, hãy dùng try-with-resources/finally bên trong task; không coi việc Future được đánh dấu canceled là bằng chứng resource đã đóng. Tùy kiến trúc, có thể cần một lifecycle riêng để xác nhận cleanup.

## 13. Ranh giới đúng: không bắt đầu tác dụng phụ sau khi đã request cancel?

Một cờ `volatile` chỉ phản ánh **điều kiện tại thời điểm đọc**. Mã:

```java
if (!cancelRequested) {
    sendOrder();
}
```

Đây là đoạn minh họa, với `sendOrder()` là một tác dụng phụ. Thread cancel có thể đặt cờ true **sau kiểm tra** nhưng **trước `sendOrder()`**. Vì thế không thể tuyên bố “một khi `requestCancel()` trả về, tuyệt đối không có tác dụng phụ mới bắt đầu” nếu chỉ dùng cờ đơn giản.

Nếu hợp đồng cần ranh giới mạnh, có thể phải:

- Bảo vệ đoạn “cho phép bắt đầu tác vụ” và “yêu cầu đóng cửa” bằng cùng lock hoặc một state machine với phép chuyển trạng thái nguyên tử.
- Ngăn nhận thêm việc mới, chờ các việc **đã bắt đầu** hoàn tất rồi mới báo TERMINATED.
- Với hệ thống ngoài JVM, dùng transaction, idempotency key hoặc cơ chế thực thi có đảm bảo của hệ thống đích; khóa JVM không tạo atomicity cho lời gửi mạng.

**Cancellation không phải rollback.** Hủy một thread sau khi đã gửi dữ liệu không tự thu hồi tác dụng phụ bên ngoài.

## 14. Cleanup đúng và những lỗi dễ mắc

| Lỗi | Hậu quả | Cách xử lý |
|---|---|---|
| Cờ hủy là boolean bình thường, bị đọc/ghi cạnh tranh | Không có bảo đảm visibility theo JMM. | Dùng `volatile`, `AtomicBoolean` hoặc một lock nhất quán. |
| Đặt flag rồi cho rằng `BlockingQueue.take()` sẽ tự thức | Worker có thể đợi vô hạn. | Interrupt, timeout, sentinel hoặc cơ chế đóng phù hợp. |
| Chỉ gọi `interrupt()` mà worker không kiểm tra/không chờ API interruptible | Có thể không dừng. | Xác định điểm hợp tác; kiểm tra flag/interrupt ở vòng xử lý. |
| Catch `InterruptedException` rồi tiếp tục như bình thường | Bỏ mất yêu cầu hủy; có thể lặp nóng. | Ném tiếp hoặc khôi phục cờ rồi thoát theo contract. |
| Dùng `Thread.interrupted()` tưởng không xóa cờ | Cờ interrupt của thread hiện tại bị xóa ngoài ý muốn. | Dùng `isInterrupted()` để đọc không xóa khi thích hợp. |
| Gọi cleanup ngay tại `requestCancel()` khi worker còn dùng resource | Đóng resource đang sử dụng, race và lỗi nghiệp vụ. | Xác định ownership và cleanup tại điểm worker thoát, đợi hoàn tất. |
| Giữ lock rồi đợi worker `join()` trong khi worker cần lock đó để cleanup | Deadlock. | Không đợi thread trong lúc giữ khóa mà thread đó cần. |
| Không có `finally` hoặc try-with-resources | Resource có thể rò rỉ khi return/exception/interrupt. | Bọc ownership trong cấu trúc cleanup thích hợp. |
| Nhiều caller chạy cancel hook theo `if (!flag) flag=true` | Side effect cancel có thể chạy nhiều lần. | `AtomicBoolean.compareAndSet(false,true)` hoặc lock. |
| Tưởng `Future.cancel(true)` là đã đóng hết resource | Cleanup có thể còn đang chạy. | Có cơ chế xác nhận termination/cleanup riêng. |
| Không quy định tác vụ còn trong queue khi hủy | Có thể mất dữ liệu hoặc xử lý thừa. | Thiết kế drain, discard, retry và báo lỗi rõ theo nghiệp vụ. |
| Dùng `Thread.stop()` | Dừng không an toàn tại trạng thái giữa chừng. | Cooperative cancellation; không sử dụng stop. |

Một số lỗi khó phát hiện hơn:

- **Cleanup ném exception:** `finally` chạy nhưng `close()` có thể thất bại; cần ghi nhận và truyền lỗi phù hợp, không tự tuyên bố “đã đóng chắc chắn”.
- **Cleanup chặn quá lâu:** cần timeout của resource và khả năng quan sát tiến độ; `finally` không bảo đảm kết thúc trong thời gian hữu hạn.
- **Hủy khi task chưa start:** nếu lifecycle cho phép, cancellation state phải được kiểm tra trước khi tạo/chạy worker; không gọi `interrupt()` trên một thread chưa start rồi giả định trạng thái dừng đã hoàn tất.
- **Tài nguyên dùng chung:** không đóng từ một worker nếu những worker khác vẫn cần nó, trừ khi ownership/ref-counting/hợp đồng đóng đã quy định.

## 15. Phương án thay thế theo kiểu tác vụ

| Loại công việc | Cơ chế hủy/đóng thường hợp lý |
|---|---|
| Tính toán CPU có các bước nhỏ | `volatile`/`AtomicBoolean` hoặc kiểm tra interrupt giữa các đơn vị công việc. |
| Chờ queue có thể interrupt | Cờ hủy + `interrupt()`, hoặc sentinel và hợp đồng đóng queue. |
| Chờ `Condition.await()` | Đổi predicate đóng dưới lock, `signalAll()`, xử lý `InterruptedException`. |
| Chờ `CountDownLatch.await()`/`sleep()` | `interrupt()` và xử lý exception theo lifecycle. |
| Task qua ExecutorService | `Future.cancel(true)`, shutdown/awaitTermination, cleanup bên trong task. |
| I/O có API timeout/hủy riêng | Dùng API cụ thể hoặc đóng resource theo ownership; đừng giả định interrupt đủ. |
| Tác vụ cần đóng nhiều resource | `try-with-resources`, `finally`, thứ tự giải phóng và chính sách lỗi. |
| Cập nhật dữ liệu ngoài JVM | Giao thức idempotency/transaction/recovery độc lập với thread cancellation. |

Đối với công việc chờ, một primitive cấp cao có hợp đồng rõ ràng thường dễ dùng đúng hơn busy polling. Đối với công việc CPU, tránh kiểm tra flag ở mỗi phép toán cực nhỏ nếu gây overhead không cần thiết; lựa chọn điểm kiểm tra theo granularity công việc và mục tiêu latency hủy.

## 16. Quy trình thiết kế cancellation an toàn

1. **Định nghĩa hợp đồng:** hủy là best-effort hay phải hoàn tất? Khi requestCancel trả về, đảm bảo điều gì? Có timeout không?
2. **Xác định owner:** ai tạo worker, ai có quyền interrupt, ai đóng resource và ai đợi completion?
3. **Lựa chọn state:** `volatile boolean` cho cờ đơn hướng đơn giản; `AtomicBoolean` nếu cần CAS hoặc nhiều caller; state machine khi có nhiều pha.
4. **Đánh dấu cancellation point:** trước nhận việc mới, giữa đơn vị xử lý, khi chờ interruptible, sau chờ trước khi thực thi item.
5. **Bảo vệ bất biến:** không hủy giữa lúc cập nhật dữ liệu nhiều field nếu không có rollback/transaction hoặc điểm nhất quán.
6. **Lựa chọn wake-up:** interrupt cho API có hỗ trợ, timeout hoặc signal/sentinel nếu hợp đồng đó phù hợp.
7. **Đảm bảo cleanup:** `try/finally` hoặc `try-with-resources`; tránh resource bị đóng đồng thời ngoài ownership.
8. **Truyền kết quả/lỗi:** lưu thất bại và trạng thái dừng theo một giao thức đồng bộ; không tự đánh đồng “thread chết” với “task thành công”.
9. **Chờ completion:** `join()`, `awaitTermination()` hoặc cơ chế hoàn thành riêng; nếu timeout, báo còn chạy thay vì khẳng định đã dừng.
10. **Kiểm thử nhiều lịch xen kẽ:** yêu cầu hủy trước start, ngay trước khi blocking, khi đang blocking, trong lúc xử lý, khi cleanup và khi nhiều caller cùng cancel.
11. **Giám sát liveness:** worker có thể không hợp tác, bị deadlock hoặc mắc I/O không ngắt được. Thiết kế fallback vận hành ở tầng hệ thống nếu cần.
12. **Tách khỏi transaction ngoài tiến trình:** hủy thread không tự rollback một giao dịch đã commit ở nơi khác.

## 17. Tự kiểm tra kiến thức

1. **Vì sao flag hủy thường dùng `volatile`?** Để có bảo đảm hiển thị/thứ tự theo JMM khi nhiều thread đọc/ghi cờ.
2. **`volatile boolean` có thể bảo đảm `if (!cancelled) doWork()` nguyên tử không?** Không; trạng thái có thể đổi sau khi check.
3. **Tại sao đặt cờ không đánh thức `BlockingQueue.take()`?** Vì việc đặt cờ không tác động trực tiếp lên giao thức blocking của queue.
4. **`interrupt()` có giết thread ngay không?** Không; đó là tín hiệu hợp tác, chỉ một số blocking call phản ứng bằng exception.
5. **`Thread.interrupted()` khác `isInterrupted()` thế nào?** Phương thức static đọc và xóa cờ của thread hiện tại; `isInterrupted()` đọc cờ thread cụ thể mà không xóa.
6. **Tại sao cleanup đặt trong `finally`?** Để đi qua đường dọn khi vòng xử lý thoát vì return, break hoặc exception.
7. **Sau `requestCancel()`, có thể báo “worker đã chết” ngay không?** Không; cần chờ một cơ chế termination thực sự.
8. **Vì sao `AtomicBoolean.compareAndSet(false,true)` hữu ích khi nhiều caller cancel?** Vì chỉ một caller được quyền chuyển trạng thái lần đầu một cách nguyên tử.
9. **`Future.cancel(true)` có đảm bảo resource đã đóng không?** Không; trạng thái Future bị cancel không đồng nghĩa cleanup của task đã hoàn tất.
10. **Nếu interrupt đến trước một lần `sleep()` thì sao?** Blocking call có thể phát hiện interrupt status và ném `InterruptedException` ngay theo hợp đồng.
11. **Nếu cleanup cần lock mà caller giữ lock đó trong khi gọi `join()` thì sao?** Có thể deadlock; không chờ worker khi giữ lock mà worker cần.
12. **Nếu tác vụ đã gửi request ra hệ thống khác, cancel có hoàn tác không?** Không; cần transaction, idempotency hoặc bù trừ theo hợp đồng hệ thống ngoài JVM.

**Ghi nhớ:** `volatile`/`AtomicBoolean` **công bố ý định hủy**, `interrupt()` **giúp thoát khỏi những thao tác chờ hỗ trợ interruption**, còn `finally`/`try-with-resources` **dọn tài nguyên khi worker thực sự rời khỏi công việc**. Chỉ kết luận “đã dừng và cleanup xong” sau một cơ chế xác nhận hoàn tất đúng hợp đồng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-007">Future.cancel(true) và các giới hạn thực tế</a>

<details>
<summary>Xem chi tiết</summary>

# Future.cancel(true) và các giới hạn thực tế trong Java Concurrency

## 1. Mục tiêu và ý nghĩa của hủy tác vụ

Trong ứng dụng đa luồng, đôi lúc một công việc không còn cần thiết: người gọi đã hết hạn chờ, yêu cầu bị đóng, worker phải nhường tài nguyên, hoặc ứng dụng đang shutdown. Với một tác vụ đã gửi vào `ExecutorService`, đối tượng `Future<?>` giúp người gọi truy xuất kết quả hoặc **yêu cầu hủy** thông qua `cancel(boolean mayInterruptIfRunning)`.

Lời gọi thường thấy:

~~~java
boolean accepted = future.cancel(true);
~~~

Có một điểm cực kỳ quan trọng:

> **`Future.cancel(true)` báo yêu cầu hủy được chấp nhận và, nếu phù hợp với implementation, cố ngắt thread đang chạy; nó không bảo đảm business logic đã dừng tại thời điểm `cancel` trả về, không tự rollback side effect và không bắt buộc mọi API I/O thoát ngay.**

Sau bài, bạn sẽ biết:
- Đọc đúng ý nghĩa `cancel(true)`, `cancel(false)`, giá trị trả về, `isCancelled()`, `isDone()`, `get()`.
- Phân biệt **trạng thái của Future** với **vòng đời thực của mã trong Callable/Runnable**.
- Viết task **hợp tác với interruption** và quản lý cleanup trong `finally`.
- Hiểu tại sao task bỏ qua interrupt có thể tiếp tục làm việc **sau khi Future đã cancelled**.
- Phân biệt `Future.get(timeout, unit)` chỉ hết hạn chờ với hành động **cancel task**.
- Hủy tác vụ **trước khi bắt đầu**, trong khi đang chạy và sau khi đã hoàn thành; xử lý các race ở ranh giới.
- Tránh kỳ vọng sai về blocking I/O, synchronized/locks, CPU-bound loops, `CompletableFuture.cancel(true)` và shutdown của executor.
- Thiết kế timeout, cancellation, idempotency, resource cleanup và kiểm chứng bằng ví dụ **Java 17+**.

**Phạm vi:** Java SE, `java.util.concurrent` và concurrency của Java nói chung. Không dùng các cơ chế riêng của Spring vì phần liên quan Spring đã có chương riêng.

## 2. Future đại diện cho kết quả, không phải công tắc dừng cưỡng bức

`Future<V>` đại diện cho **kết quả cuối của một phép tính bất đồng bộ**, cung cấp:
- `get()`: đợi và lấy kết quả nếu task hoàn thành bình thường; có thể ném `ExecutionException` nếu task thất bại.
- `get(timeout, unit)`: đợi **tối đa một khoảng thời gian** rồi ném `TimeoutException` nếu chưa có kết quả.
- `cancel(boolean)`: yêu cầu hủy theo hợp đồng của implementation.
- `isCancelled()`: task đã được đánh dấu hủy **trước khi hoàn thành bình thường** hay chưa.
- `isDone()`: phép tính đã **hoàn thành, thất bại hoặc được đánh dấu hủy** hay chưa.

Các trạng thái thường được quan sát:

~~~text
         submit
            |
            v
       Chưa bắt đầu --------------+
            |                    |
            v                    |
         Đang chạy                |
          /   \                   |
         /     \                  |
 Hoàn tất     Exception           |
         \     /                  |
          \   /                   |
           v v                    |
        Done bình thường/         |
        Done do lỗi               |
                                 |
       cancel(...) thành công ---+
                   |
                   v
           Future cancelled
          isDone == true
          isCancelled == true
          get() -> CancellationException

Thread có thể vẫn tiếp tục chạy sau khi Future đã cancelled.
~~~

Đây là sơ đồ **trạng thái quan sát của Future**, không phải sơ đồ đầy đủ của mọi thread/JVM. Một task có thể nhận tín hiệu interrupt rồi cần thêm thời gian cleanup. Một task **bỏ qua** interrupt còn có thể chạy tiếp tới khi tự kết thúc, dù kết quả của nó không còn được Future công bố.

**`isDone() == true` không đồng nghĩa “thread đã dừng”**. Nó chỉ cho biết phép tính đại diện bởi Future đã ở trạng thái hoàn tất theo contract, và **cancellation là một kiểu hoàn tất**.

## 3. cancel(true) và cancel(false) chính xác có nghĩa gì?

Signature của interface:

~~~java
boolean cancel(boolean mayInterruptIfRunning);
~~~

| Tình huống | `cancel(true)` | `cancel(false)` |
| --- | --- | --- |
| Task còn nằm trong queue, chưa chạy | Nếu hủy thành công, task không được thực hiện | Tương tự: không thực hiện task đã hủy |
| Task đã bắt đầu nhưng chưa hoàn tất | Yêu cầu hủy; implementation như `FutureTask` có thể/cố interrupt worker | Yêu cầu hủy **không yêu cầu interrupt** worker đang chạy |
| Task đã hoàn tất trước lời gọi | Thường trả `false`; kết quả đã cố định | Tương tự |
| Hủy đã được chấp nhận | `isCancelled()==true` và `isDone()==true` | Tương tự |
| Task vẫn có thể thực thi code sau cancel? | **Có**, nếu task không hợp tác hoặc chưa quan sát interrupt | **Có** nếu task đang chạy, vì không yêu cầu interrupt |
| `get()` sau khi Future bị hủy | Ném `CancellationException` | Tương tự |

Lưu ý `cancel(true)` **không thay thế `Thread.stop()`**: đây không phải lệnh giết thread không điều kiện. Nó chỉ thiết lập cơ chế hủy của Future và yêu cầu ngắt khi implementation hỗ trợ; code tác vụ cần **hợp tác** để dừng đúng. Không nên dùng `Thread.stop()` vì khả năng để object ở trạng thái không nhất quán.

### Giá trị trả về true/false

- `true`: lời gọi đã **thành công trong việc chuyển Future sang trạng thái cancelled** theo hợp đồng; **không chứng minh worker đã kết thúc**.
- `false`: không thể thực hiện hủy, ví dụ phép tính đã hoàn thành hoặc đã ở trạng thái không thể hủy theo implementation. Không dùng `false` đơn lẻ để kết luận “task đã hoàn thành thành công”: nó có thể thất bại hoặc bị hủy trước đó.
- `cancel(true)` có thể cạnh tranh với việc task tự kết thúc. Nếu một trong hai thắng trước, kết quả của lời gọi hủy tùy thời điểm; code phải chấp nhận cả hai đường đi.
- Nếu trạng thái là cancelled, các lần `get()` sau đó ném `CancellationException`, không trả kết quả do task vừa tính được, ngay cả khi phần mã trong worker vẫn tiếp tục chạy.

Với `ExecutorService.submit(...)` ở các executor thông dụng của Java, Future thường được triển khai dựa trên `FutureTask` và hỗ trợ việc interrupt worker khi `cancel(true)` thành công lúc task đang chạy. Nhưng **Future là một interface**; các implementation khác có thể có chính sách hủy khác, nên phải đọc contract cụ thể.

### CancellationException không phải InterruptedException

`Future.get()` có thể ném các exception khác nhau:
- `CancellationException` (*unchecked*): **Future đang ở trạng thái cancelled**.
- `InterruptedException` (*checked*): **thread đang gọi get** bị interrupt trong lúc chờ, không có nghĩa tác vụ chắc chắn bị hủy.
- `ExecutionException` (*checked*): computation hoàn thành bằng lỗi được lưu trong Future.
- `TimeoutException` (*checked*, với timed get): caller chưa nhận được kết quả trong khoảng chờ; task **có thể vẫn chạy và Future chưa bị hủy**.

Đây là bốn tình huống khác nhau. Không bắt `TimeoutException` rồi báo “task cancelled” nếu chưa thực sự gọi `cancel` và kiểm tra kết quả.

## 4. Interruption hoạt động như một yêu cầu hợp tác

Mỗi Java `Thread` có **interrupt status**. `Thread.interrupt()` thiết lập trạng thái đó và có tác động tới một số thao tác chờ; các API khác nhau phản ứng khác nhau.

Một task CPU-bound nên tự kiểm tra status:

~~~java
while (!Thread.currentThread().isInterrupted()) {
    doSmallUnitOfWork();
}
// Ra khỏi vòng lặp và cleanup.
~~~

Snippet giả định `doSmallUnitOfWork()` đã khai báo, không phải chương trình độc lập. Phải chia công việc thành đơn vị đủ nhỏ để kiểm tra status; nếu một vòng xử lý kéo dài hàng phút mà không kiểm tra, cancellation sẽ chậm.

Một task đang dùng API interruptible như `Thread.sleep()`, `Object.wait()`, `BlockingQueue.take()`, `CountDownLatch.await()` hoặc `Condition.await()` có thể nhận `InterruptedException`. Theo hợp đồng của các thao tác thường gặp này, khi exception được ném, **interrupt status bị xóa**.

Ví dụ hàm `Callable` có thể để exception truyền lên:

~~~java
Callable<String> task = () -> {
    while (true) {
        Thread.sleep(1_000);
        doSmallUnitOfWork();
    }
};
~~~

Đây là snippet, cần import `java.util.concurrent.Callable` và khai báo method giả định. Khi `sleep` bị interrupt, lambda có thể thoát bằng `InterruptedException`; Future đã cancelled vẫn không chuyển trở lại trạng thái hoàn thành bình thường.

Nếu phải bắt `InterruptedException` nhưng tầng hiện tại không thể truyền tiếp, một mẫu xử lý:

~~~java
try {
    doInterruptibleWork();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt(); // Khôi phục trạng thái.
    return;
}
~~~

Đây là đoạn minh họa trong method `void`. Quan trọng nhất là **thoát hoặc xử lý chính sách hủy rõ ràng**, không nuốt exception rồi lại bắt đầu chờ vô hạn.

### Hai cách kiểm tra interrupt status

- `Thread.currentThread().isInterrupted()`: kiểm tra **không xóa** status.
- `Thread.interrupted()`: phương thức static kiểm tra **và xóa** status của **thread hiện tại**.

Nếu vô tình dùng `Thread.interrupted()` để kiểm tra nhiều lần mà không ghi nhớ kết quả, có thể xóa mất yêu cầu hủy. Dùng `isInterrupted()` khi chỉ muốn biết có nên dừng hay không.

## 5. Ví dụ Java 17: task hợp tác với cancel(true)

Chương trình tạo một task có vòng `Thread.sleep()` interruptible. Main đợi task bắt đầu, gọi `cancel(true)`, đợi worker thực sự rời tác vụ qua latch, rồi kiểm tra `Future`. Không dựa vào ngủ ngẫu nhiên để đo timing.

Tạo file **`CooperativeCancellationDemo.java`**:

~~~java
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CooperativeCancellationDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);

        try {
            Callable<String> work = () -> {
                started.countDown();
                try {
                    while (true) {
                        Thread.sleep(60_000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "stopped-by-interrupt";
                } finally {
                    exited.countDown();
                }
            };

            Future<String> future = pool.submit(work);
            started.await();

            boolean accepted = future.cancel(true);
            exited.await(); // Khác với Future.isDone(): chờ task thực sự thoát.

            System.out.println("cancelAccepted=" + accepted);
            System.out.println("isCancelled=" + future.isCancelled());
            System.out.println("isDone=" + future.isDone());
            System.out.println("workerExited=" + (exited.getCount() == 0));

            try {
                System.out.println("result=" + future.get());
            } catch (CancellationException e) {
                System.out.println("get=CancellationException");
            }
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                System.out.println("executor-not-terminated");
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac CooperativeCancellationDemo.java
java CooperativeCancellationDemo
~~~

Stdout trên đường thực thi bình thường, khi worker hợp tác và executor kết thúc:

~~~text
cancelAccepted=true
isCancelled=true
isDone=true
workerExited=true
get=CancellationException
~~~

**Phân tích:**
1. `started.await()` bảo đảm worker đã bước vào callable; task chưa tự hoàn tất vì nó liên tục chờ trong `sleep`.
2. `cancel(true)` trên Future do executor trả về đánh dấu hủy và yêu cầu interrupt.
3. `Thread.sleep` quan sát interrupt bằng `InterruptedException`, worker đi tới `finally` và hạ latch `exited`.
4. `exited.await()` kiểm tra worker **đã thực sự rời callable**, điều mà `future.isDone()` sau hủy **không tự chứng minh**.
5. Dù callable có nhánh `return "stopped-by-interrupt"`, Future đã cancelled nên `future.get()` ném `CancellationException` chứ **không** trả chuỗi đó.
6. `shutdownNow` ở `finally` giúp tránh giữ worker nếu có lỗi giữa chừng; đây vẫn là cơ chế hủy best-effort, không phải bảo đảm mọi tác vụ bất hợp tác dừng.

Lưu ý demo dùng `exited.await()` không timeout để minh họa tín hiệu xác nhận worker kết thúc. Trong code production nên dùng **deadline** cho thời gian chờ shutdown và có chiến lược xử lý khi task không phản hồi interruption.

## 6. Vì sao Future cancelled không có nghĩa worker đã dừng?

Đây là giới hạn quan trọng nhất của `cancel(true)`. Một task có thể:
- Không gọi API interruptible và không kiểm tra `isInterrupted()`.
- Bắt `InterruptedException` rồi bỏ qua, tiếp tục công việc.
- Đang ở một thao tác native hoặc blocking I/O không đáp ứng interruption ngay.
- Đang giữ lock hoặc thực hiện một callback dài trước khi tới điểm kiểm tra.
- Đang trong `finally` cleanup, có thể còn thực hiện side effect sau thời điểm Future được đánh dấu cancelled.

### Ví dụ Java 17: task cố tình bỏ qua interrupt

Chương trình sau **cố ý thiết kế không hợp tác**: worker chờ một latch, nhưng nếu bị interrupt, nó bắt exception và **tiếp tục chờ**. Main hủy Future rồi mới cho phép worker hoàn tất. Demo chứng minh có thể có code chạy sau `cancel(true)`, không tạo một vòng CPU nóng.

Tạo file **`UncooperativeCancellationDemo.java`**:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class UncooperativeCancellationDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        AtomicBoolean sideEffectAfterCancel = new AtomicBoolean(false);

        try {
            Future<String> future = pool.submit(() -> {
                started.countDown();
                try {
                    while (true) {
                        try {
                            release.await();
                            break;
                        } catch (InterruptedException ignored) {
                            // CỐ Ý SAI: bỏ qua cancellation để minh họa.
                        }
                    }
                    sideEffectAfterCancel.set(true);
                    return "completed-despite-cancel";
                } finally {
                    exited.countDown();
                }
            });

            started.await();
            boolean accepted = future.cancel(true);

            System.out.println("cancelAccepted=" + accepted);
            System.out.println("futureDone=" + future.isDone());
            System.out.println("workerExitedBeforeRelease="
                    + (exited.getCount() == 0));

            release.countDown();
            exited.await();

            System.out.println("sideEffectAfterCancel="
                    + sideEffectAfterCancel.get());
            System.out.println("futureStillCancelled=" + future.isCancelled());

            try {
                future.get();
            } catch (CancellationException e) {
                System.out.println("get=CancellationException");
            }
        } finally {
            release.countDown();
            pool.shutdownNow();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Chạy:

~~~bash
javac UncooperativeCancellationDemo.java
java UncooperativeCancellationDemo
~~~

Stdout trong đường thực thi bình thường:

~~~text
cancelAccepted=true
futureDone=true
workerExitedBeforeRelease=false
sideEffectAfterCancel=true
futureStillCancelled=true
get=CancellationException
~~~

**Điều gì xảy ra?**
- `cancel(true)` có thể interrupt worker đang đợi `release.await()`; nhưng code **cố tình nuốt** `InterruptedException` và chờ lại.
- Ngay sau cancel, `Future.isDone()==true`, trong khi `exited` chưa được count down: task chưa kết thúc.
- Main cho `release` về 0. Worker tiếp tục ghi một side effect minh họa, kết thúc callable và hạ `exited`.
- Future vẫn cancelled; `get()` ném `CancellationException` dù logic worker đã return bình thường.

Đây là ví dụ **anti-pattern**, không phải khuyến nghị bỏ qua interrupt. Trong thực tế, side effect có thể là ghi DB, gửi HTTP, cập nhật cache hoặc làm thay đổi dữ liệu nghiệp vụ mà caller đã tưởng không còn xảy ra.

**Hệ quả thiết kế:** nếu tính đúng yêu cầu “sau khi hủy thì tuyệt đối không ghi kết quả”, chỉ `Future.cancel(true)` không đủ. Cần phối hợp bằng state machine, token/generation, idempotency, quyền commit hoặc giao thức xử lý ở tài nguyên cuối. Worker cần kiểm tra cancellation **trước điểm commit**; nếu commit thuộc hệ thống bên ngoài, phải thiết kế tương ứng tại hệ thống đó.

## 7. cancel(false): hủy kết quả mà không yêu cầu ngắt worker

`future.cancel(false)` không phải “hủy nhẹ hơn nhưng chắc chắn dừng”:
- Nếu task **chưa chạy** và hủy thành công, worker không thực hiện công việc đó.
- Nếu task **đã chạy**, `FutureTask` có thể chuyển Future sang cancelled mà **không interrupt** worker. Task đang chạy có thể tiếp tục tính toán hoặc làm I/O cho đến khi tự dừng.
- Caller thấy `get()` ném `CancellationException`, nhưng không thể suy rằng hoạt động phía trong đã dừng.
- `cancel(false)` có thể phù hợp khi không muốn gửi interrupt tới một task đã chạy nhưng không còn cần kết quả; điều này **không giải phóng ngay** slot trong thread pool.

Khi task được triển khai bằng `FutureTask`, quyết định cancel khác với tín hiệu interrupt: một việc là thay **state của Future**, một việc là cố tác động vào **thread thực thi**. Trong chương trình có nhiều loại Future, luôn xem Javadoc của implementation thay vì áp đặt chi tiết `FutureTask` cho tất cả.

## 8. Hủy trước khi task bắt đầu: tác vụ không được thực thi

Khi dùng một executor có một worker, ta có thể cố tình giữ worker bận bằng tác vụ đầu tiên và đưa tác vụ thứ hai vào queue. Nếu hủy tác vụ thứ hai **trước khi nó được chạy**, runnable của nó không được gọi.

Tạo file **`CancelQueuedTaskDemo.java`**:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CancelQueuedTaskDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean queuedWorkRan = new AtomicBoolean(false);

        try {
            Future<?> blocker = pool.submit(() -> {
                blockerStarted.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            blockerStarted.await();

            Future<?> queued = pool.submit(() ->
                    queuedWorkRan.set(true));

            boolean accepted = queued.cancel(true);
            release.countDown();
            blocker.get();

            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.MINUTES);

            System.out.println("cancelAccepted=" + accepted);
            System.out.println("queuedWasExecuted=" + queuedWorkRan.get());
            System.out.println("queuedCancelled=" + queued.isCancelled());

            try {
                queued.get();
            } catch (CancellationException e) {
                System.out.println("queuedGet=CancellationException");
            }
        } finally {
            release.countDown();
            pool.shutdownNow();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Chạy:

~~~bash
javac CancelQueuedTaskDemo.java
java CancelQueuedTaskDemo
~~~

Stdout khi executor hoạt động bình thường:

~~~text
cancelAccepted=true
queuedWasExecuted=false
queuedCancelled=true
queuedGet=CancellationException
~~~

**Lý do kết quả không phụ thuộc thứ tự chạy:**
1. `newSingleThreadExecutor` chỉ thực hiện một task tại một thời điểm.
2. Task `blocker` đã bắt đầu và đang chờ `release`, nên task thứ hai chỉ có thể xếp hàng.
3. `queued.cancel(true)` được gọi trước khi mở `release`, do đó queued task chưa thể chạy.
4. Khi worker xử lý Future đã canceled, nội dung runnable không được thực thi; `queuedWorkRan` vẫn false.
5. `queued.get()` ném `CancellationException`.

**Giới hạn:** việc hủy một Future đang đợi trong queue không nhất thiết làm nó **biến mất ngay lập tức khỏi mọi cấu trúc queue nội bộ**. Cơ chế loại task hủy khỏi work queue và quản lý memory có thể phụ thuộc executor/implementation. Không suy “cancel thành công” đồng nghĩa kích thước queue được giảm ngay.

## 9. Future.get(timeout) không tự hủy task

`Future.get(timeout, TimeUnit.MILLISECONDS)` chỉ giới hạn **thời gian thread gọi get chờ kết quả**. Khi ném `TimeoutException`:
- Future **có thể vẫn running** hoặc đang đợi trong queue.
- `isCancelled()` thường vẫn false, trừ khi có bên khác đã hủy.
- Task **không tự nhận interrupt** chỉ vì caller hết hạn `get`.
- Caller cần quyết định có gọi `future.cancel(true)` hay không, hoặc để task tiếp tục làm việc.

### Ví dụ Java 17: timeout chỉ thuộc về caller

Tạo file **`TimedGetDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TimedGetDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<Integer> future = pool.submit(() -> {
                started.countDown();
                release.await();
                return 42;
            });

            started.await();

            try {
                future.get(20, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                System.out.println("get=TimeoutException");
            }

            System.out.println("cancelled=" + future.isCancelled());
            System.out.println("doneBeforeRelease=" + future.isDone());

            release.countDown();
            System.out.println("resultAfterRelease=" + future.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Chạy:

~~~bash
javac TimedGetDemo.java
java TimedGetDemo
~~~

Stdout khi main không bị interrupt và task hoạt động bình thường:

~~~text
get=TimeoutException
cancelled=false
doneBeforeRelease=false
resultAfterRelease=42
~~~

Task bị chặn tại `release.await()` trong khi main gọi `get(20ms)`. Hết 20ms, main nhận `TimeoutException` nhưng Future chưa canceled; sau khi `release.countDown()`, task hoàn tất và `get()` trả 42.

**Bẫy thường gặp:** caller timeout rồi retry một nghiệp vụ khác trong khi task cũ vẫn chạy. Hai tác vụ có thể cùng gây side effect (đặt hàng hai lần, ghi dữ liệu hai lần...). Giới hạn thời gian chờ kết quả **không thay thế** deadline của chính thao tác từ xa, không thay thế cancellation, và không thay thế idempotency.

### Mẫu deadline và cancel khi timeout

~~~java
try {
    return future.get(timeout, TimeUnit.MILLISECONDS);
} catch (TimeoutException e) {
    boolean cancelAccepted = future.cancel(true);
    // Log/cập nhật trạng thái theo yêu cầu.
    // cancelAccepted không bảo đảm worker đã thực sự dừng.
    throw e;
}
~~~

Đây là snippet **bên trong method có future, timeout và kiểu trả về phù hợp**; cần import `TimeoutException` và `TimeUnit`. Nếu timeout của caller là một phần của deadline đầu-cuối, tổng thời gian ở queue, I/O, retry và cleanup đều phải được tính; chỉ thêm `get(timeout)` chưa tạo deadline cho toàn bộ business operation.

## 10. Một hạn chế dễ nhầm: CompletableFuture.cancel(true)

`CompletableFuture<T>` triển khai `Future<T>` nhưng có chính sách cancel riêng. Theo Javadoc Java 17, **tham số `mayInterruptIfRunning` không có tác dụng trong implementation của `CompletableFuture` vì interruption không được sử dụng để điều khiển quá trình xử lý**.

Vì vậy, nếu `CompletableFuture.supplyAsync(...)` đã chạy:
- `cf.cancel(true)` có thể chuyển CompletableFuture sang trạng thái cancelled.
- Caller của `cf.get()`/`cf.join()` quan sát cancellation theo contract của CompletableFuture.
- **Không được suy** từ `cancel(true)` rằng thread đang thực thi `supplyAsync` đã bị interrupt.
- Worker có thể chạy tiếp, làm side effect hoặc hoàn tất calculation, nhưng không làm CompletableFuture đã canceled công bố lại kết quả bình thường.
- Các dependent stages có thể hoàn thành exceptional/cancellation tùy đồ thị phụ thuộc; không có bảo đảm ngắt thread tương ứng.

### Ví dụ Java 17: cancel CompletableFuture không interrupt worker

Tạo file **`CompletableFutureCancelDemo.java`**:

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
        CountDownLatch exited = new CountDownLatch(1);
        AtomicBoolean sawInterrupt = new AtomicBoolean(false);

        try {
            CompletableFuture<String> cf =
                    CompletableFuture.supplyAsync(() -> {
                        started.countDown();
                        try {
                            release.await();
                            return "work-finished";
                        } catch (InterruptedException e) {
                            sawInterrupt.set(true);
                            Thread.currentThread().interrupt();
                            return "interrupted";
                        } finally {
                            exited.countDown();
                        }
                    }, pool);

            started.await();
            boolean accepted = cf.cancel(true);

            System.out.println("cancelAccepted=" + accepted);
            System.out.println("futureCancelled=" + cf.isCancelled());
            System.out.println("workerExitedBeforeRelease="
                    + (exited.getCount() == 0));

            release.countDown();
            exited.await();

            System.out.println("interruptObserved=" + sawInterrupt.get());

            try {
                cf.join();
            } catch (CancellationException e) {
                System.out.println("join=CancellationException");
            }
        } finally {
            release.countDown();
            pool.shutdownNow();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Chạy:

~~~bash
javac CompletableFutureCancelDemo.java
java CompletableFutureCancelDemo
~~~

Stdout trên đường thực thi bình thường với worker không nhận interruption bên ngoài:

~~~text
cancelAccepted=true
futureCancelled=true
workerExitedBeforeRelease=false
interruptObserved=false
join=CancellationException
~~~

Đây là khác biệt rất đáng nhớ giữa **Future do executor trả về** và **CompletableFuture của một pipeline bất đồng bộ**. Không mặc định tất cả implementation của `Future.cancel(true)` đều interrupt task runner giống nhau. Nếu cần một kênh hủy có interrupt cụ thể, phải quản lý task handle/`FutureTask` tương ứng, timeout và cancellation token ở abstraction của bạn.

## 11. Những thao tác thường không phản hồi interruption như mong đợi

### 11.1. CPU-bound loop không kiểm tra cờ

Ví dụ sai:

~~~java
while (true) {
    performCpuWork(); // Không kiểm tra interrupt hoặc cancellation token.
}
~~~

`cancel(true)` có thể đặt interrupt status nhưng **không tự chèn một nhánh break** vào vòng lặp. Task có thể tiếp tục mãi. Tách CPU work thành chunk nhỏ và kiểm tra `Thread.currentThread().isInterrupted()` tại các checkpoint.

### 11.2. Chờ monitor lock bằng synchronized

Một thread đang cố **vào `synchronized(lock)`** có thể ở `Thread.State.BLOCKED`. Việc interrupt thông thường **không khiến thao tác tranh monitor của `synchronized` ném `InterruptedException` hoặc tự bỏ cuộc**. Khi cuối cùng lấy được monitor, code phải kiểm tra interrupt/cancellation nếu muốn dừng.

Nếu cần chờ lock với khả năng interrupt, cân nhắc `ReentrantLock.lockInterruptibly()` hoặc `tryLock(timeout, unit)`, bảo đảm `unlock()` trong `finally`. Các API có semantics riêng; đừng đổi lock chỉ vì hy vọng cancel thành công mà không xét giao thức nghiệp vụ.

### 11.3. Blocking I/O

Không có bảo đảm chung rằng `Thread.interrupt()` ngắt tức thì **mọi** `InputStream.read()`, socket/file I/O hay native blocking call trên mọi JDK. Ví dụ:
- Với một số `InterruptibleChannel`, interruption khi block có thể làm channel đóng và ném `ClosedByInterruptException`.
- Một số socket/stream truyền thống phụ thuộc timeout, `close()` hoặc triển khai I/O cụ thể; `Socket#setSoTimeout` đặt thời gian tối đa chờ đọc với những API tương ứng.
- Một lời gọi từ thư viện/driver có thể xử lý interruption theo chính hợp đồng của nó, không theo ý caller.

Nếu task đang chờ một dependency bên ngoài, **timeout của đúng API I/O** và **cleanup/đóng resource** thường quan trọng không kém interrupt. Không mặc định đóng một resource từ thread khác lúc nào cũng an toàn: phải theo contract, lifecycle và ownership của tài nguyên ấy.

### 11.4. Callback hoặc cleanup dài

Ngay cả task hợp tác có thể vẫn chạy `finally` sau khi bị interrupt:
- Đóng stream, hoàn trả connection, giải phóng lock.
- Ghi metric hoặc trạng thái kết thúc.
- Hoàn tất một operation không thể bỏ dở mà không vi phạm invariant.

Do đó, khoảng thời gian từ **cancel accepted** đến **resource fully released** có thể không bằng 0. Với hệ thống cần shutdown đúng, nên theo dõi kết thúc bằng latch/termination của executor hoặc một tín hiệu cleanup riêng, không chỉ `Future.isDone()` của task đã hủy.

## 12. Hủy task và hủy executor là hai cấp độ khác nhau

### shutdown()

`ExecutorService.shutdown()` bắt đầu đóng nhận task mới, nhưng **các task đã submit được phép hoàn thành** theo hợp đồng. `shutdown()` không đồng nghĩa gửi interrupt để hủy mọi task đang chạy.

### shutdownNow()

`ExecutorService.shutdownNow()` **cố gắng** dừng task đang hoạt động và ngừng xử lý task đang chờ. Implementation thông dụng dùng `Thread.interrupt()` để yêu cầu task đang chạy dừng, nhưng đó là **best-effort**; nếu task không phản hồi interruption, nó có thể tiếp tục. Phương thức trả danh sách các task chưa bắt đầu được thực thi; cách thể hiện task/Future phải xét đúng implementation.

### awaitTermination()

`awaitTermination(timeout, unit)` đợi executor đạt trạng thái terminated hoặc hết timeout (hoặc caller bị interrupt). Đây là **cách quan sát kết thúc executor** sau `shutdown()/shutdownNow()`, không tự động yêu cầu dừng task nếu bạn chưa khởi động shutdown.

Mẫu thường dùng:

~~~java
pool.shutdown();
try {
    if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
        pool.shutdownNow();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            // Báo task không hợp tác hoặc cleanup kéo dài.
        }
    }
} catch (InterruptedException e) {
    pool.shutdownNow();
    Thread.currentThread().interrupt();
}
~~~

Đây là snippet, giả định `pool` là `ExecutorService` đã được khởi tạo. Các timeout minh họa không thay thế SLO thực; với một service phức tạp có thể cần deadline tổng, kiểm soát task chặn I/O và theo dõi những tài nguyên không được giải phóng.

**Đừng mặc định gọi `cancel(true)` trên từng Future là đủ để thread pool shutdown.** Một task bỏ qua interrupt có thể khiến executor không terminate và giữ cả tiến trình sống nếu sử dụng platform threads non-daemon.

## 13. Bảo vệ business invariant và side effect khi cancellation

Cancellation có thể đến tại nhiều điểm:

~~~text
queued -> started -> validated -> remote call -> commit -> cleanup -> done
   |         |          |            |          |         |
   +---- cancel yêu cầu ở bất kỳ thời điểm nào -------+
~~~

Những rủi ro:
- **Đã commit trước khi cancel đến:** caller có thể thấy Future cancelled nhưng side effect đã xảy ra; không thể rollback bằng interrupt.
- **Đang gọi remote API khi cancel đến:** lệnh từ xa có thể đã hoàn tất dù caller không nhận response; retry có thể tạo duplicate.
- **Đang giữ lock khi cancel đến:** task phải đảm bảo `finally` nhả lock; interruption không tự rollback trạng thái.
- **Một phần state đã cập nhật:** task không được bỏ ngang để lại invariant sai, ví dụ giảm tồn kho mà không ghi đơn hàng tương ứng.
- **Task đã bị canceled nhưng vẫn chạy:** nó có thể publish kết quả muộn, ghi đè trạng thái của tác vụ thay thế.

Các thiết kế phòng vệ:
1. **Idempotency key:** cùng một yêu cầu nghiệp vụ chỉ có một hiệu ứng được chấp nhận tại hệ thống thực hiện side effect.
2. **Generation/version/token:** chỉ lần chạy đang hợp lệ được quyền publish/commit kết quả; task cũ bị hủy không được ghi đè phiên bản mới.
3. **Trạng thái rõ ràng:** `NEW -> RUNNING -> COMMITTING -> SUCCEEDED/FAILED/CANCELLED` theo giao thức có atomic transition ở phạm vi cần thiết.
4. **Transaction/compensation:** với cập nhật nhiều tài nguyên, định nghĩa rollback hoặc bù trừ nếu việc ngắt giữa chừng gây state dở dang.
5. **Cancellation checkpoints:** kiểm tra interrupt/token trước bước nặng và ngay trước commit khi có thể; vẫn cần giao thức nguyên tử để tránh race giữa “đã kiểm tra” và “commit”.
6. **Thời gian chờ hữu hạn:** timeout cho I/O, lock wait và cleanup; đừng dựa duy nhất vào timed get.
7. **Cleanup đúng ownership:** try-with-resources và `finally`, trả connection, nhả semaphore, unlock; không để task hủy giữ tài nguyên vô hạn.

### Điều quan trọng về “kiểm tra trước commit”

Đoạn sau **chưa đủ** nếu phải bảo đảm tuyệt đối không có commit sau cancellation:

~~~java
if (!Thread.currentThread().isInterrupted()) {
    commitToExternalSystem();
}
~~~

Một cancel có thể đến **sau lần kiểm tra nhưng trước commit**. Nếu contract nghiệp vụ cần loại trừ side effect từ task bị hủy, phải có một giao thức xác nhận quyền commit tại hệ thống đích hoặc một atomic state transition thống nhất; đơn thuần xem interrupt flag không đủ.

## 14. Future.cancel(true) và các mô hình Future khác

### FutureTask

`FutureTask<V>` là implementation có thể chạy một `Callable`/`Runnable`. Khi hủy một task đang chạy bằng `cancel(true)` và thành công, nó cố interrupt thread đang chạy. Dù task tiếp tục chạy, trạng thái kết quả của FutureTask vẫn là cancelled. Trong các executor phổ biến, `submit` tạo task theo mẫu này hoặc tương đương.

### ScheduledFuture

`ScheduledExecutorService.schedule(...)` và các phương thức periodic trả `ScheduledFuture`; hủy có thể ngăn các lần thực thi được lập lịch tiếp theo. Với một lần thực thi **đang chạy**, mayInterruptIfRunning vẫn chỉ mang semantics tương ứng của implementation. Với periodic task, cancellation không làm những side effect trước đó biến mất. Chính sách loại bỏ task đã hủy khỏi delay queue cũng phụ thuộc executor (ví dụ `ScheduledThreadPoolExecutor#setRemoveOnCancelPolicy`), không mặc định hủy làm memory queue giảm tức khắc.

### CompletableFuture

`CompletableFuture.cancel(true)` **không sử dụng interrupt để điều khiển task** trong implementation Java 17. Nó là một cách **hoàn thành exceptional** của future và ảnh hưởng tới các stage phụ thuộc, nhưng không phải tương đương `FutureTask.cancel(true)`. Muốn thực sự yêu cầu task dừng, cần một kênh cancellation được chính công việc hỗ trợ.

### Thread và ForkJoinTask

Nếu quản lý `Thread` trực tiếp, `thread.interrupt()` chỉ là yêu cầu hủy; bạn không tự có một Future kết quả. Với `ForkJoinTask`, semantics cancellation/interrupt cũng không nên suy từ FutureTask; hãy đọc tài liệu của implementation cụ thể. **Không xây abstraction chung “mọi Future đều interrupt worker khi cancel(true)”**.

## 15. Những lỗi thường gặp

| Nhầm lẫn hoặc lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| `cancel(true) == true` được hiểu là thread đã dừng | Side effect/cleanup vẫn có thể chạy | Theo dõi task exited hoặc giao thức hoàn tất riêng |
| `isDone() == true` được hiểu là công việc thành công | Cancel và exception cũng là done | Kiểm tra `isCancelled()`, xử lý `get()` đúng |
| `get()` sau cancel được kỳ vọng trả kết quả task đã tính | Thực tế ném `CancellationException` | Tách trạng thái Future và hành vi worker |
| Bắt `InterruptedException` rồi tiếp tục vô hạn | Task không hợp tác với cancellation | Propagate hoặc restore status và thoát |
| Dùng `Thread.interrupted()` để kiểm tra nhưng bỏ qua giá trị | Xóa interrupt status | Dùng `isInterrupted()` khi muốn đọc không xóa |
| CPU loop không kiểm tra cờ | Không phản hồi cancel | Thêm checkpoint và công việc theo chunk |
| Chờ `synchronized` bị cạnh tranh và mong interrupt giải phóng ngay | Không có hợp đồng như lockInterruptibly | Dùng lock API phù hợp hoặc xem xét cấu trúc khóa |
| Giả định mọi I/O dừng vì interrupt | Task tiếp tục block | Timeout/close theo API I/O |
| `get(timeout)` được hiểu là task đã bị cancel | Task vẫn chạy, dễ retry trùng | Gọi cancel riêng nếu cần và bảo vệ idempotency |
| `cancel(false)` được hiểu là task sẽ dừng | Task đang chạy có thể tiếp tục | Hiểu semantics “không yêu cầu interrupt” |
| `CompletableFuture.cancel(true)` được hiểu là interrupt worker | Không đúng với Java 17 | Thêm cancellation token/task handle riêng |
| Dùng `shutdownNow()` và tin executor chắc chắn terminated | Noncooperative workers có thể tiếp tục | `awaitTermination` có deadline và cảnh báo |
| Dừng giữa hai lần sửa shared state | Invariant nghiệp vụ sai | Bảo vệ commit/rollback, cleanup trong finally |
| Cancel task cũ rồi lập tức submit task mới mà không có version guard | Kết quả muộn của task cũ ghi đè dữ liệu mới | Generation/version/idempotent commit |
| Không thiết lập timeout cho downstream I/O | Cancel có thể không giải phóng worker | Timeout ở đúng API và giới hạn concurrency |
| Không kiểm tra task đang chạy có được release resource không | Leak connection, semaphore, lock | Try-with-resources, finally, completion metrics |

## 16. Kiểm thử cancellation đáng tin

Một test hợp lý phải đo **hai việc khác nhau**:
1. Trạng thái **Future**: `cancel` trả gì, `isCancelled/isDone` thế nào, `get` ném gì.
2. Trạng thái **thực thi**: callable đã chạy đến đâu, đã thoát chưa, cleanup và side effect cuối có xảy ra không.

Cách kiểm thử:
- **Sử dụng latch/barrier** để buộc task thực sự bắt đầu hoặc thực sự nằm trong queue trước khi cancel; không dùng `Thread.sleep(100)` làm bằng chứng về thứ tự chạy.
- Test hủy **trước khi bắt đầu**, khi đang `sleep/take/wait`, khi làm CPU và khi task không hợp tác.
- Test `cancel(true)` và `cancel(false)` trên implementation thực sự sử dụng.
- Test riêng `CompletableFuture.cancel(true)`: kết quả cancelled không phải bằng chứng interruption.
- Test `get(timeout)`: xác nhận Future vẫn có thể hoàn tất sau khi caller timeout.
- Dùng `CountDownLatch exited` hoặc `awaitTermination` để xác nhận worker/executor đã dừng thực sự; **không dùng Future.isDone() sau cancel như assertion thay thế**.
- Đảm bảo mọi nhánh test đều **release latch trong `finally`** và đóng executor để không khiến test treo.
- Khi side effect quan trọng, dùng fake resource/gateway và xác nhận không bị duplicate hoặc ghi đè sau cancel; đừng chỉ kiểm tra một biến boolean “cancel requested”.
- Kiểm tra interruption bị catch/clear hoặc callback native I/O không hỗ trợ hủy; không suy luận rằng một test sleep thành công nghĩa mọi công việc đều interruptible.
- Theo dõi metric: cancel requests, cancel accepted, worker exits, cleanup duration, pool active count, queued count, timeout và tác vụ vẫn chạy quá deadline.

Nếu đo latency, phân biệt:
- **Cancellation request latency:** thời gian gọi `cancel`.
- **Task stop latency:** từ yêu cầu hủy tới lúc worker không thực thi task nữa.
- **Resource release latency:** tới lúc tất cả connection/lock/permit được trả.
- **Business finality latency:** tới lúc hệ thống biết rõ side effect thành công, thất bại hay cần bù trừ.

Các khoảng này **không nhất thiết giống nhau**.

## 17. Câu hỏi tự kiểm tra

1. `Future.cancel(true)` trả `true` có chứng minh worker đã thoát không? **Không.** Nó chỉ chứng minh Future đã chấp nhận cancellation theo contract.
2. Một Future đã cancelled có thể `isDone() == true` trong khi runnable còn chạy không? **Có.**
3. Sau cancellation thành công, `Future.get()` ném exception gì? **`CancellationException`**.
4. `Future.get(timeout)` ném `TimeoutException` có tự interrupt/cancel task không? **Không.**
5. Nếu một `Callable` bắt `InterruptedException` rồi bỏ qua và tiếp tục chờ, `cancel(true)` có buộc nó dừng không? **Không.**
6. Với `cancel(false)` trên một `FutureTask` đã chạy, task có thể tiếp tục không? **Có, vì không yêu cầu interrupt.**
7. Hủy một task đang xếp hàng trước khi worker nhận nó có thể làm runnable không được chạy không? **Có, nếu cancellation thành công theo executor/Future đó.**
8. `Thread.currentThread().isInterrupted()` và `Thread.interrupted()` khác nhau ở đâu? **Hàm thứ nhất không xóa cờ; hàm static thứ hai xóa cờ của thread hiện tại.**
9. Tại sao một task đang cố vào synchronized có thể không dừng ngay khi bị interrupt? **Chờ monitor qua synchronized không phải thao tác lock interruptible như `lockInterruptibly()`.**
10. Interrupt có bảo đảm đóng mọi socket hoặc làm mọi `InputStream.read()` trở về ngay không? **Không**, semantics phụ thuộc API và implementation.
11. Với Java 17, `CompletableFuture.cancel(true)` có interrupt thread đang xử lý `supplyAsync` không? **Không phải cơ chế của implementation CompletableFuture**; cờ mayInterruptIfRunning không có tác dụng.
12. `shutdownNow()` có bảo đảm mọi task đã dừng không? **Không, chỉ nỗ lực best-effort.**
13. Tại sao Future bị hủy vẫn có thể tạo một bản ghi database? **Worker có thể đã commit trước cancel, đang ở giữa I/O, hoặc tiếp tục chạy sau khi Future đã cancelled.**
14. Tại sao check interrupt ngay trước khi gửi request ra ngoài vẫn chưa bảo đảm không có side effect sau cancellation? **Có race giữa check và commit; cần idempotency/giao thức commit.**
15. Khi nào nên restore interrupted flag trong catch? **Khi code không thể propagate `InterruptedException` và cần để tầng gọi biết yêu cầu interrupt, đồng thời thoát/tuân thủ chính sách hủy.**
16. `CancellationException` và `InterruptedException` của `get()` khác nhau thế nào? **Exception đầu cho biết Future bị hủy; exception sau cho biết thread gọi `get` bị interrupt.**
17. Khi cancel một task xếp hàng, có thể khẳng định nó lập tức bị loại khỏi mọi queue executor không? **Không; cách quản lý queued/cancelled tasks phụ thuộc implementation.**
18. Nếu task đã cancel nhưng bạn vẫn cần bảo đảm resource được đóng, phải theo dõi gì? **Kết thúc thực thi và cleanup theo giao thức riêng, không chỉ `isDone`.**

## 18. Kết luận

`Future.cancel(true)` là **yêu cầu hủy có khả năng gửi interrupt**, không phải cơ chế cưỡng bức dừng code Java. Khi cancel được chấp nhận, Future có thể lập tức báo `isCancelled()==true` và `isDone()==true`; `get()` ném `CancellationException`, nhưng worker **vẫn có thể đang chạy, cleanup hoặc tạo side effect**.

Task cần hợp tác với interruption, chọn API I/O có timeout/hủy thích hợp và nhả resource trong `finally`. `get(timeout)` chỉ giới hạn thời gian chờ của caller, còn `CompletableFuture.cancel(true)` trong Java 17 **không dùng interrupt** để điều khiển worker. Đối với nghiệp vụ có side effect không thể trùng lặp, thêm **idempotency, quyền commit, trạng thái kết thúc rõ ràng và giám sát stop latency**; không lấy giá trị trả về của `cancel` làm bằng chứng mọi hậu quả đã biến mất.

## Tài liệu tham khảo Java SE 17

- [Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): `cancel`, `isCancelled`, `isDone`, `get` và timeout.
- [FutureTask](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/FutureTask.html): implementation kết quả task và cancellation.
- [ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): `submit`, `shutdown`, `shutdownNow`, `awaitTermination`.
- [Thread](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): `interrupt`, `interrupted` và `isInterrupted`.
- [CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): `cancel` và ghi chú `mayInterruptIfRunning`.
- [ReentrantLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html): `lockInterruptibly`, `tryLock`.
- [InterruptibleChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/InterruptibleChannel.html): đóng channel khi interrupt block và `ClosedByInterruptException`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-008">Timeout khác cancellation thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

# Timeout khác cancellation thế nào? — Java concurrency

Một lời gọi Java có thể chạy quá lâu vì thread đang chờ khóa, chờ phần tử trong queue, chờ I/O, hoặc tính toán mất nhiều thời gian. Khi đó ứng dụng thường cần hai cơ chế nghe có vẻ giống nhau nhưng **không đồng nghĩa**:

- **Timeout:** đặt **giới hạn thời gian chờ** hoặc ngân sách thời gian cho một hoạt động. Khi vượt giới hạn, *người chờ* có thể nhận kết quả "hết hạn", `false`, `null` hoặc một exception. Điều đó **không tự khẳng định công việc được chờ đã dừng**.
- **Cancellation:** **yêu cầu chấm dứt công việc** hoặc ngăn nó bắt đầu, theo một giao thức hủy. Yêu cầu có thể được tiếp nhận nhưng task còn phải hợp tác dừng, xử lý side effect và giải phóng tài nguyên. Không phải mọi API cancellation đều làm việc đó tức thời hoặc bằng interruption.

Ví dụ đơn giản: thread A gọi `future.get(100, MILLISECONDS)` để đợi một task trên thread B. Nếu quá 100 ms, A nhận `TimeoutException` và tiếp tục. **Task của B vẫn có thể đang chạy**. Nếu A muốn yêu cầu B dừng, cần một bước riêng, chẳng hạn `future.cancel(true)` theo hợp đồng của loại future/producer đang dùng. Ngay cả khi gọi cancel, không được xem task chắc chắn đã dừng hoặc dữ liệu bên ngoài đã được rollback.

Bài thuộc phần **Interrupt & Hủy** của **Java concurrency nói chung**, tập trung vào JDK 17+ và `java.util.concurrent`, không dùng thành phần concurrency riêng của Spring Framework.

## 1. Một bảng phân biệt nhanh

| Khía cạnh | Timeout | Cancellation |
| --- | --- | --- |
| Câu hỏi chính | "Tôi có chờ quá thời hạn không?" | "Tôi có yêu cầu công việc ngừng/không chạy nữa không?" |
| Trọng tâm | Thời gian chờ, deadline hoặc timeout của operation | Vòng đời và trạng thái của task/operation |
| Ví dụ Java | `Future.get(timeout, unit)`, `BlockingQueue.poll(timeout, unit)`, `Lock.tryLock(timeout, unit)` | `Future.cancel(...)`, `Thread.interrupt()`, cancellation token, đóng resource |
| Tác động mặc định tới worker khác | **Không nhất thiết** | Có thể gửi tín hiệu/yêu cầu, nhưng **không bảo đảm dừng ngay** |
| Kết quả thường gặp | `TimeoutException`, `false`, `null` hoặc exception I/O riêng | `CancellationException` khi lấy kết quả của future đã hủy; flag/trạng thái hủy; interruption tùy API |
| Task có thể vẫn chạy? | Có | Cũng có, nếu không hợp tác hoặc API không hỗ trợ dừng |
| Resource bên ngoài tự rollback? | Không | Không |
| Hợp đồng cần thiết | Deadline, đơn vị thời gian, điều kiện hết hạn | Ai sở hữu task, cách truyền yêu cầu, cleanup, trạng thái hoàn tất |

**Timeout có thể dẫn đến cancellation**, nhưng đó là **chính sách được code chủ động lựa chọn**, không phải một quy tắc Java áp dụng cho mọi method.

## 2. Phân biệt bốn sự kiện trong một request

Trong một hệ thống concurrent, ít nhất bốn mốc có thể khác nhau:

1. **Hết hạn chờ của caller:** caller không muốn đợi thêm.
2. **Yêu cầu hủy được phát ra:** caller hoặc coordinator gọi cancel/interrupt/close.
3. **Worker quan sát yêu cầu hủy:** worker thoát khỏi blocking call hoặc kiểm tra cancellation token.
4. **Worker thật sự hoàn tất cleanup:** không còn làm việc, khóa/permit/resource đã được giải phóng theo giao thức.

Sơ đồ:

```text
Caller                           Task/worker
------                           -----------
submit task  ------------------> start work
wait up to 100 ms                 |
       |                          | doing work
timeout (caller stops waiting)    |
       |                          | STILL RUNNING possible
optional cancel(true) ----------> interrupt request / cancel flag
       |                          | detects request when/if supported
caller returns                    | stops useful work, runs cleanup
                                  +--> terminated/cleaned up
```

Các mốc này có thể cách nhau rất xa. Một API trả `true` từ `cancel()` **không mặc nhiên chứng minh** bước cuối đã hoàn tất. Người gọi muốn chờ cleanup cần một giao thức completion riêng nếu contract cancellation không bao gồm nó.

## 3. "Timeout" có thể nằm ở những tầng nào?

Từ "timeout" không xác định một hành vi duy nhất:

- **Timeout khi chờ kết quả:** người gọi ngừng chờ; task có thể tiếp tục. Ví dụ `Future.get(100, MILLISECONDS)`.
- **Timeout khi lấy tài nguyên:** nếu không lấy được lock/permit/queue item trong thời hạn, operation trả thất bại; chưa bắt đầu phần công việc cần tài nguyên. Ví dụ `ReentrantLock.tryLock(...)`.
- **Timeout của một thao tác I/O:** network read/connection attempt có thể báo hết thời gian theo hợp đồng I/O; điều này không tự rollback remote request hoặc đóng mọi resource.
- **Timeout được áp đặt lên trạng thái future:** một API có thể **hoàn thành future với kết quả lỗi/fallback**, nhưng underlying work có thể vẫn đang làm. Ví dụ `CompletableFuture.orTimeout(...)`.
- **Deadline cho cả workflow:** một thời điểm kết thúc dùng chung để hạn chế nhiều bước gọi liên tiếp; mỗi bước nhận phần thời gian còn lại.

Đọc **Javadoc của đúng phương thức** trước khi suy ra hậu quả. Một `TimeoutException` từ `Future.get` không mang cùng nghĩa vòng đời với `SocketTimeoutException` từ một thao tác network read.

## 4. Ví dụ Java 17+: `Future.get(timeout)` chỉ giới hạn thời gian đợi

Lưu thành `FutureTimeoutDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureTimeoutDemo {
    public static void main(String[] args) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            Future<Integer> future = pool.submit(() -> {
                release.await();
                return 42;
            });

            boolean timedOut = false;
            try {
                future.get(30, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                timedOut = true;
            }

            System.out.println("Wait timed out: " + timedOut);
            System.out.println("Task done after timeout: "
                    + future.isDone());

            release.countDown();
            System.out.println("Later result: " + future.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
```

Biên dịch/chạy trên JDK 17+:

```bash
javac FutureTimeoutDemo.java
java FutureTimeoutDemo
```

Stdout trên đường chạy bình thường:

```text
Wait timed out: true
Task done after timeout: false
Later result: 42
```

**Vì sao kết quả xác định?**

- Task đợi `release` chưa được mở. Vì vậy ngay cả khi worker được scheduler chạy sớm, task vẫn **không thể hoàn tất** trước thời điểm main mở latch.
- `get(30, MILLISECONDS)` hết hạn chờ trên thread main và ném `TimeoutException`; đoạn code **không hề gọi `cancel`**.
- `future.isDone()` vẫn false vì task chưa hoàn tất và chưa bị cancel.
- Sau `release.countDown()`, worker mới có thể trả 42; main gọi `future.get()` lần nữa lấy kết quả.
- `finally` giải phóng latch ngay cả khi lỗi để tránh bỏ mặc worker đang đợi, rồi yêu cầu executor shutdown.

Một lần `get` bị timeout **không làm future biến thành timed-out state vĩnh viễn**. Cùng future có thể được chờ lại, trả kết quả hoặc bị cancel theo chính sách riêng.

## 5. Nếu timeout thì task có thể làm gì tiếp?

Sau `future.get(timeout)` ném `TimeoutException`:

- Task có thể **chưa bắt đầu** vì nằm trong hàng đợi executor.
- Task có thể **đang chạy** và vẫn ghi dữ liệu, gọi callback hoặc gửi network request.
- Task có thể **hoàn thành ngay sau khi caller hết hạn**.
- Task có thể sau đó thất bại với exception khác mà caller không còn lấy kết quả.
- Một job khác có thể bị phụ thuộc vào side effect của task, dù request bên ngoài đã trả "timeout".

Do đó, "caller đã hết hạn" và "task đã dừng" không phải hai trạng thái tương đương. Nếu timeout là lý do không muốn sử dụng kết quả đến muộn, cần quy định **stale-result handling**, idempotency hoặc generation/version token để kết quả đến sau không ghi đè trạng thái mới.

Không nên dùng `Thread.sleep(...)` để kết luận task chắc chắn đã dừng; chỉ một completion primitive hoặc lifecycle protocol xác định mới cho biết điều đó.

## 6. `Future.cancel(boolean)` thực sự cam kết điều gì?

`Future.cancel(mayInterruptIfRunning)` cố gắng hủy computation liên quan. Tham số:

- `false`: nếu computation đã bắt đầu, không yêu cầu interrupt thread đang chạy qua tham số này; implementation có thể không dừng một task đang thực thi.
- `true`: nếu task đang chạy trên một thread được quản lý phù hợp, implementation có thể **yêu cầu interrupt** thread đó nhằm tạo cơ hội dừng hợp tác.

Khi cancellation được thực hiện thành công theo `Future` contract, các lần gọi `isCancelled()` và `isDone()` có thể phản ánh trạng thái canceled/done của **future**. `get()` trên future đã cancel ném `CancellationException`. Nhưng **trạng thái future đã cancel không phải giấy chứng nhận worker đã cleanup xong**.

Đặc biệt:

- `cancel(true)` **không cưỡng bức giết** thread.
- Nhiều blocking calls trong Java phản hồi interruption; một số I/O không làm vậy ngay, hoặc có exception/semantics khác.
- Code có thể bắt `InterruptedException` rồi nuốt nó, khiến task tiếp tục.
- Task có thể đang ở giữa side effect bên ngoài; không thể tự hoàn tác HTTP request hoặc file write đã xảy ra.
- Một task đã hoàn thành trước lời gọi cancel có thể không được cancel; phải xét race giữa completion và cancellation.

## 7. Ví dụ Java 17+: yêu cầu hủy và worker hợp tác dừng

Lưu thành `FutureCancelDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

public class FutureCancelDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicBoolean sawInterrupt = new AtomicBoolean();

        try {
            Future<Integer> future = pool.submit(() -> {
                try {
                    entered.countDown();
                    Thread.sleep(60_000);
                    return 42;
                } catch (InterruptedException e) {
                    sawInterrupt.set(true);
                    Thread.currentThread().interrupt();
                    return -1;
                } finally {
                    stopped.countDown();
                }
            });

            entered.await();
            boolean accepted = future.cancel(true);
            stopped.await();

            boolean canceledOnGet = false;
            try {
                future.get();
            } catch (CancellationException e) {
                canceledOnGet = true;
            }

            System.out.println("Cancellation accepted: " + accepted);
            System.out.println("Worker saw interrupt: "
                    + sawInterrupt.get());
            System.out.println("Future canceled: " + future.isCancelled());
            System.out.println("Get canceled: " + canceledOnGet);
        } finally {
            pool.shutdownNow();
        }
    }
}
```

```bash
javac FutureCancelDemo.java
java FutureCancelDemo
```

Stdout trên đường chạy thành công của chương trình:

```text
Cancellation accepted: true
Worker saw interrupt: true
Future canceled: true
Get canceled: true
```

Tại sao chương trình không dựa vào lịch chạy ngẫu nhiên?

1. `entered.countDown()` cho biết task đã bước vào vùng `try`; main không cancel một task chưa chạy.
2. Task cố ngủ 60 giây. Nếu main interrupt **trước** khi worker gọi sleep, sleep nhận flag và ném `InterruptedException`; nếu interrupt **trong** lúc sleep, lời gọi được đánh thức bằng exception.
3. Catch đánh dấu `sawInterrupt` và khôi phục flag bằng `Thread.currentThread().interrupt()` để không làm mất thông tin yêu cầu hủy tại ranh giới không thể throw.
4. `finally` luôn đếm `stopped` khi task rời khối đó; main `await()` trước khi kiểm tra kết quả, xác định cleanup demo đã chạy.
5. Future bị cancel nên `get()` ném `CancellationException`, **không trả -1** dù thân task có nhánh trả -1 khi bị ngắt.

Điểm quan trọng: ví dụ dùng một task hợp tác với interruption để minh họa. **Đừng suy rộng rằng mọi `Future.cancel(true)` ở mọi implementation đều cam kết worker đã dừng ngay**. `stopped` là một completion signal riêng của demo; ứng dụng thực cũng nên có cách quan sát cleanup nếu cần.

## 8. `cancel(false)` và `cancel(true)` không phải chế độ "nhẹ/nặng" tuyệt đối

Tham số chỉ thể hiện **có cho phép thử interrupt worker đang chạy hay không**, không quy định mức ưu tiên của request hoặc thời gian hoàn thành.

Với `FutureTask`/task từ `ExecutorService`, `cancel(false)` có thể khiến future chuyển sang trạng thái canceled nhưng worker đang chạy vẫn tiếp tục nếu không có một signaling mechanism khác. `cancel(true)` có thể giúp một worker đang chờ bằng API interruptible thoát nhanh hơn, song worker vẫn có thể bỏ qua interrupt hoặc mất thời gian cleanup.

Tình huống chọn thường gặp:

- Một task đã bắt đầu và công việc không nên tiếp tục: cân nhắc `cancel(true)` khi việc interrupt được document là an toàn và task có cleanup phù hợp.
- Một operation không được phép ngắt giữa bước critical, hoặc có cancellation token riêng: có thể dùng cách khác, nhưng phải định nghĩa nhiệm vụ còn chạy thế nào sau khi future canceled.
- Chỉ không muốn dùng kết quả đến muộn, nhưng muốn công việc nền hoàn tất phục vụ cache: **chỉ timeout khi chờ** rồi để task tiếp tục có thể là chính sách hợp lệ.

Không chọn `cancel(true)` theo phản xạ nếu tác vụ đang giữ một resource có giao thức không an toàn khi dừng giữa chừng. **Cooperative cancellation** đòi hỏi code xác định các điểm hủy và bất biến sau hủy.

## 9. Timeout trên `BlockingQueue` là chờ có giới hạn, không "hủy producer"

`BlockingQueue.poll(timeout, unit)` chờ tối đa khoảng thời gian tương ứng để lấy một phần tử. Nếu không có phần tử khi thời hạn hết, nó trả `null`. Nó không phát lệnh dừng đến producer khác.

Lưu thành `QueueTimeoutDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class QueueTimeoutDemo {
    public static void main(String[] args)
            throws InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);

        Integer first = queue.poll(20, TimeUnit.MILLISECONDS);
        System.out.println("First poll timed out: " + (first == null));

        queue.offer(7);
        Integer second = queue.poll(1, TimeUnit.SECONDS);
        System.out.println("Next item: " + second);
    }
}
```

```bash
javac QueueTimeoutDemo.java
java QueueTimeoutDemo
```

Stdout chính xác:

```text
First poll timed out: true
Next item: 7
```

Không có producer nào thêm phần tử trước lần `poll` đầu, nên kết quả là `null` sau thời gian chờ. Việc không lấy được item **không thay đổi trạng thái hủy của queue hoặc một thread**. Sau đó chương trình thêm `7` và lấy được bình thường.

Một `poll` bị timeout khác với `take()` bị interrupt: `poll` trả `null` theo hợp đồng timeout, còn `take` có thể ném `InterruptedException` khi người chờ bị yêu cầu ngắt. Chúng cần được xử lý bằng hai nhánh logic khác nhau.

## 10. Timeout khi chờ lock không có nghĩa thread giữ lock bị cancel

`ReentrantLock.tryLock(timeout, unit)` thử lấy lock trong giới hạn thời gian, có thể trả `false` nếu không lấy được. Caller thất bại trong việc lấy lock, **không gửi yêu cầu hủy thread đang giữ lock**.

Ví dụ có điều phối rõ, lưu thành `TimedLockDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class TimedLockDemo {
    public static void main(String[] args) throws Exception {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch holderAcquired = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                holderAcquired.countDown();
                releaseHolder.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }, "lock-holder");

        holder.start();
        holderAcquired.await();

        boolean acquiredWhileHeld = false;
        try {
            acquiredWhileHeld =
                    lock.tryLock(30, TimeUnit.MILLISECONDS);
        } finally {
            if (acquiredWhileHeld) {
                lock.unlock();
            }
        }

        releaseHolder.countDown();
        holder.join();

        boolean acquiredAfterRelease =
                lock.tryLock(1, TimeUnit.SECONDS);
        try {
            System.out.println("Acquired while held: "
                    + acquiredWhileHeld);
            System.out.println("Acquired after release: "
                    + acquiredAfterRelease);
        } finally {
            if (acquiredAfterRelease) {
                lock.unlock();
            }
        }
    }
}
```

```bash
javac TimedLockDemo.java
java TimedLockDemo
```

Stdout trên đường chạy bình thường:

```text
Acquired while held: false
Acquired after release: true
```

Trong lần thử đầu, holder chắc chắn đang sở hữu lock vì main đã đợi `holderAcquired` và chưa mở `releaseHolder`. Vì thế caller không thể lấy được lock trong khoảng timeout, trừ khi holder bị interrupt ngoài dự kiến hoặc một bên chủ động mở latch. Sau khi main mở latch và `join` hoàn tất, lock đã được nhả, nên lần thử sau thành công.

**Cần biết:** `tryLock(timeout, unit)` là phép chờ **có thể bị interrupt** và có `throws InterruptedException`. Nếu thread caller bị interrupt trong lúc đợi, exception không có nghĩa holder bị cancel. Trong code thật, luôn nhả `ReentrantLock` trong `finally` khi đã lấy được.

## 11. `CompletableFuture.orTimeout` và `completeOnTimeout`

Java 9+ cung cấp hai API quan trọng:

- `future.orTimeout(timeout, unit)`: nếu chưa complete đúng hạn, **hoàn thành chính CompletableFuture đó exceptionally** với `TimeoutException`.
- `future.completeOnTimeout(value, timeout, unit)`: nếu chưa complete đúng hạn, **hoàn thành chính CompletableFuture đó bình thường** với một giá trị fallback.

Các method này **thay đổi trạng thái của CompletableFuture được gọi**, khác với `Future.get(timeout)` chỉ làm **lần chờ của caller** hết hạn. Tuy nhiên, **chúng không tự cam kết interrupt hoặc dừng underlying computation** do một worker khác đang làm.

Lưu thành `CompletableFutureTimeoutDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CompletableFutureTimeoutDemo {
    public static void main(String[] args) {
        CompletableFuture<Integer> timeoutFuture =
                new CompletableFuture<>();

        timeoutFuture.orTimeout(20, TimeUnit.MILLISECONDS);

        boolean sawTimeout = false;
        try {
            timeoutFuture.join();
        } catch (CompletionException e) {
            sawTimeout = e.getCause() instanceof TimeoutException;
        }

        boolean laterCompletionAccepted = timeoutFuture.complete(7);

        CompletableFuture<Integer> fallbackFuture =
                new CompletableFuture<>();

        fallbackFuture.completeOnTimeout(
                99, 20, TimeUnit.MILLISECONDS);

        System.out.println("Exceptional timeout: " + sawTimeout);
        System.out.println("Later completion accepted: "
                + laterCompletionAccepted);
        System.out.println("Fallback: " + fallbackFuture.join());
    }
}
```

```bash
javac CompletableFutureTimeoutDemo.java
java CompletableFutureTimeoutDemo
```

Stdout chính xác:

```text
Exceptional timeout: true
Later completion accepted: false
Fallback: 99
```

Phân tích:

1. `timeoutFuture` ban đầu chưa được complete bởi bất kỳ producer nào. Sau deadline, nó complete exceptionally.
2. `join()` ném `CompletionException`, trong đó cause là `TimeoutException`.
3. `complete(7)` sau khi future đã complete exceptionally trả `false`: không thể thay kết quả đã hoàn tất bằng normal completion thông thường.
4. `fallbackFuture` cũng không có producer; nó được complete với 99 sau deadline.
5. **Không có worker tính toán thực sự** trong ví dụ này. Đây là cách chủ động tách *future result state* khỏi *task execution state*. Khi có underlying asynchronous task, một timeout của future **không tự chứng minh** task nền đã dừng.

**Lưu ý:** `orTimeout` và `completeOnTimeout` trả về **chính CompletableFuture đó**. Nếu caller khác cũng giữ reference, họ sẽ nhìn thấy trạng thái completion do timeout; đây không đơn thuần là một phép "đợi tạm thời" như `get(timeout)`.

## 12. `CompletableFuture.cancel(true)` khác `FutureTask.cancel(true)`

Dù `CompletableFuture` implement `Future`, cancellation semantics của nó có điểm quan trọng: **tham số `mayInterruptIfRunning` không được dùng để điều khiển underlying computation** theo hợp đồng của `CompletableFuture`.

Ví dụ một `CompletableFuture.supplyAsync(...)` chạy trên executor: việc hủy future có thể đặt future vào trạng thái canceled và các dependent stage có thể nhận exceptional completion, nhưng **không được mặc định** worker đã bị interrupt. Nếu tác vụ cần hợp tác dừng, thiết kế phải có token, handle hoặc cơ chế hủy nguồn công việc tương ứng.

So sánh:

| Cấu trúc | Timeout | Cancellation |
| --- | --- | --- |
| `Future` từ `ExecutorService.submit` | `get(timeout)` hết thời gian đợi, task có thể tiếp tục | `cancel(true)` cố gắng cancel và có thể yêu cầu interrupt running worker |
| `CompletableFuture` | `orTimeout`/`completeOnTimeout` có thể complete chính future | `cancel(...)` chuyển future sang canceled nhưng không dùng boolean để interrupt computation |
| `BlockingQueue` | `poll(timeout)` hoặc `offer(timeout)` giới hạn chờ | Không có khái niệm cancel mọi producer/consumer chỉ từ timeout; điều phối bằng interrupt, close protocol hoặc token |
| `ReentrantLock` | `tryLock(timeout)` giới hạn chờ lock | Không cancel thread khác đang sở hữu lock |

Không dựa vào tên `cancel` chung để suy ra mọi implementation đều tác động lên thread giống nhau.

## 13. `ExecutorService.invokeAll(timeout)` là một ví dụ kết hợp cả hai

Không phải mọi API timeout đều "chỉ ngừng chờ". `ExecutorService.invokeAll(tasks, timeout, unit)` có hợp đồng riêng: thực thi tập task, chờ chúng hoàn tất trong giới hạn thời gian; **khi method trả về, những task chưa hoàn tất sẽ bị cancel**.

Ở đây timeout của **operation tổng thể** được gắn với **chính sách cancellation** mà API quy định. Đừng áp đặt kết luận của `Future.get(timeout)` lên `invokeAll(timeout)`, hoặc ngược lại.

Tương tự, `invokeAny(tasks, timeout, unit)` trả một kết quả thành công theo hợp đồng hoặc có thể ném `TimeoutException` khi hết hạn; những task chưa hoàn tất có chính sách cancellation theo API. Đọc Javadoc về việc hủy các task còn lại và exception trước khi dùng với side effect không idempotent.

**Bài học:** ý nghĩa của timeout luôn phải được giải thích **theo method**, không chỉ theo tên exception.

## 14. Deadlines: tại sao dùng một ngân sách thời gian tổng thể?

Giả sử một request có ba bước:

```text
A: lấy lock       timeout 200 ms
B: đọc queue      timeout 200 ms
C: chờ future     timeout 200 ms
```

Nếu mỗi bước có *một timeout riêng 200 ms*, tổng thời gian có thể gần **600 ms** chưa kể overhead. Điều đó vi phạm yêu cầu "request tổng thể tối đa 200 ms" nếu đó mới là SLA thực sự.

**Deadline** là một mốc thời gian chung, còn **remaining budget** là thời lượng còn lại tại mỗi bước:

```text
start request
    |
    +--> deadline = start + 200 ms
    |
    +--> step A uses remaining time
    |
    +--> step B recomputes remaining time
    |
    +--> step C recomputes remaining time
    |
    +--> timeout/cancellation policy if budget exhausted
```

Với Java SE, `System.nanoTime()` phù hợp để tính **thời lượng đã trôi qua** vì không dựa vào giờ hệ thống có thể bị điều chỉnh. Đừng dùng `System.currentTimeMillis()` để làm đồng hồ đo chính cho duration nhạy với chỉnh giờ.

### Tránh tính deadline ngây thơ khi timeout cực lớn

Một cách viết phổ biến:

```java
long deadline = System.nanoTime()
        + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
```

Đây là mẫu minh họa, không phải class có `main`. Với timeout hữu hạn bình thường, dễ hiểu. Nhưng với khoảng cực lớn, arithmetic trên `long` có thể wrap. Một thiết kế library cần giới hạn ngân sách, dùng **elapsed time** hoặc tính toán an toàn với số nguyên, và định nghĩa rõ timeout 0/âm.

Không thể chỉ tạo deadline rồi bỏ qua nó: **mỗi blocking operation phải được cấp remaining time phù hợp**. Những API chỉ có chờ vô hạn thì không tự trở nên deadline-aware khi caller giữ một biến deadline.

## 15. Mẫu deadline theo thời gian đã trôi qua

Một helper tính ngân sách còn lại mà không lệ thuộc giờ thực:

```java
static long remainingNanos(
        long startNanos, long budgetNanos) {
    long elapsed = System.nanoTime() - startNanos;
    if (elapsed >= budgetNanos) {
        return 0L;
    }
    return budgetNanos - elapsed;
}
```

Đây là phương thức minh họa trong class Java, **chỉ đúng trong phạm vi các duration đủ nhỏ** để phép trừ elapsed theo mô hình `System.nanoTime()` không bị vấn đề wrap dài hạn và `budgetNanos` là số không âm hợp lệ. Với timeout vài mili-giây, giây hay phút trong ứng dụng thông thường, mô hình có thể sử dụng được.

Khi bước A kết thúc, tính lại `remainingNanos` để bước B không được cấp lại toàn bộ ngân sách. Nếu remaining bằng 0, caller có thể báo deadline exceeded thay vì tiếp tục chờ.

Nhưng nhớ rằng **kiểm tra budget** và **hủy task đang chạy** là hai hành động khác nhau. Sau khi budget hết, caller phải **chọn một policy**: chỉ không chờ nữa, trả fallback, yêu cầu cancel, hoặc tiếp tục để phục vụ cache.

## 16. Timeout trong blocking I/O: hãy đọc hợp đồng cụ thể

Với Java network I/O, có nhiều mức timeout:

- **Connection timeout:** giới hạn thời gian thiết lập kết nối; tùy API dùng để connect.
- **Read timeout:** giới hạn một lần chờ đọc dữ liệu; ví dụ socket có thể ném `SocketTimeoutException` theo hợp đồng cấu hình.
- **Write timeout:** không có một thiết lập thống nhất cho mọi kiểu stream/socket truyền thống; phải xem API/channel/client tương ứng.
- **Overall operation deadline:** giới hạn toàn bộ request qua nhiều lần connect, write, read, decode, retry và xử lý; có thể cần policy riêng.

Ví dụ minh họa với `java.net.Socket`:

```java
import java.net.InetSocketAddress;
import java.net.Socket;

Socket socket = new Socket();
socket.connect(new InetSocketAddress("example.org", 80), 2_000);
socket.setSoTimeout(3_000);
// Khi read từ socket input stream theo hợp đồng,
// thời gian chờ đọc có thể kết thúc bằng SocketTimeoutException.
```

Đây là **đoạn minh họa**, không phải chương trình chạy hoàn chỉnh; không thực hiện network request trong bài học. `setSoTimeout` đặt **read timeout**; nó không phải deadline tổng thể cho toàn bộ kết nối, càng không phải lệnh hủy request đã tới server.

Khi client timeout, server có thể **đã nhận và xử lý yêu cầu** nhưng phản hồi bị chậm hoặc mất. Do đó:

- Timeout không chứng minh external side effect **chưa xảy ra**.
- Retry một operation không idempotent có thể tạo giao dịch trùng.
- Cần request ID/idempotency key, transaction status, deduplication hoặc chính sách bù trừ phù hợp với nghiệp vụ.
- Một timeout thường chỉ nói caller **không nhận được kết quả đúng hạn**.

Không dùng `Thread.interrupt()` như cơ chế hủy I/O chung không cần đọc hợp đồng; một số API hỗ trợ interruption rõ, một số không hoặc cần close channel/socket.

## 17. Timeout và cancellation khi có side effect

Giả sử task thanh toán:

```text
Client gửi lệnh thanh toán -> Service đang xử lý
Client hết timeout -> không thấy response
Client retry ngay -> có thể gửi lệnh lần thứ hai
```

Có ba trạng thái phải phân biệt:

1. **Chưa bắt đầu:** có thể hủy an toàn nếu có giao thức chắc chắn công việc chưa chạy.
2. **Đang thực hiện:** có thể tồn tại partial side effect; interrupt/cancel không tự rollback.
3. **Đã commit nhưng response đến muộn:** caller timeout, nhưng nghiệp vụ bên kia đã thành công.

Nếu có khả năng trạng thái thứ ba, một API không nên trả "thanh toán thất bại" chỉ vì client timeout, mà thường trả **"không xác định/đã hết hạn chờ xác nhận"** rồi cho phép truy vấn trạng thái bằng request ID. Điều này là ví dụ nguyên tắc chung, không phụ thuộc framework.

Các giải pháp thường cần:

- **Idempotency token** để nhiều lần retry cùng yêu cầu không tạo nhiều side effect.
- **Deadline propagation** xuống tầng thực thi khi có hỗ trợ.
- **Cooperative cancellation** cho phần công việc có thể dừng an toàn.
- **Result reconciliation** khi status outcome là unknown.
- **Cleanup** và resource release dù caller đã bỏ cuộc.

Cơ chế concurrency trong một JVM không thay được transaction semantics của hệ thống bên ngoài.

## 18. Cancellation token và cooperative checkpoints

Java có `Thread.interrupt()` như một tín hiệu cấp thread. Một số tác vụ cũng dùng **cancellation token** thuộc cấp operation. Ví dụ với `AtomicBoolean`:

```java
import java.util.concurrent.atomic.AtomicBoolean;

final class CancellationToken {
    private final AtomicBoolean canceled = new AtomicBoolean();

    void cancel() {
        canceled.set(true);
    }

    boolean isCanceled() {
        return canceled.get();
    }
}
```

Đây là class minh họa không có `main`. Task hợp tác kiểm tra:

```java
while (!token.isCanceled()) {
    processOneSmallStep();
}
```

Đoạn minh họa giả định `token` và `processOneSmallStep` được khai báo. Token cho phép nhiều bước xử lý nhìn thấy yêu cầu hủy mà không gắn semantics với một thread cụ thể. Tuy nhiên:

- Nếu task đang block trong `BlockingQueue.take()`, token **không tự đánh thức** nó.
- Worker có thể chỉ kiểm tra token giữa các bước, nên **độ trễ hủy** phụ thuộc mỗi bước.
- Một task đã hoàn thành rồi thì token bị set muộn không hoàn tác kết quả.
- Token cần **ownership** và đường completion rõ; caller không nên đồng nhất `isCanceled()` với cleanup done.
- Một token được chia sẻ giữa nhiều task sẽ yêu cầu **tất cả task** hợp tác ở checkpoint riêng.

Khi công việc có blocking call, có thể kết hợp token với interruption, timeout ngắn, close resource hoặc sentinel để worker có cơ hội kiểm tra lại.

## 19. Interruption không phải forced stop

`Thread.interrupt()` là một cơ chế yêu cầu thread hợp tác. Nếu worker đang gọi `Thread.sleep()`, `Object.wait()`, `BlockingQueue.take()` hoặc nhiều API interruptible, nó có thể được đánh thức bằng `InterruptedException`; khi API ném exception, **interrupt flag thường đã được xóa** theo contract của lời gọi đó.

Nếu method không thể truyền checked exception, mẫu tốt thường là:

```java
try {
    performBlockingWork();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
```

Đây là đoạn minh họa với `performBlockingWork` giả định. Khôi phục flag giúp không nuốt yêu cầu interrupt, còn `return` giúp task hợp tác dừng. **Chỉ khôi phục flag mà sau đó tiếp tục vòng xử lý vô hạn có thể tạo busy loop hoặc làm việc ngoài ý muốn**.

Nếu method được phép `throws InterruptedException`, thường có thể **truyền ngoại lệ lên caller** thay vì catch/restore. Không tự động biến interruption thành lỗi business hoặc bỏ qua chính sách cleanup.

### Cancellation có thể xảy ra trước, trong hoặc sau operation

Một task có thể kiểm tra token rồi bắt đầu side effect; đúng lúc đó caller yêu cầu cancel. Không thể suy ra side effect chắc chắn chưa bắt đầu chỉ vì một flag được set. Nếu cần "đảm bảo không chạy khi đã cancel trước commit", phải đặt **điểm commit/ownership** được đồng bộ đúng, có thể cần lock, compare-and-set trạng thái hoặc transaction protocol.

## 20. Race giữa completion, timeout và cancellation

Ba mốc có thể cạnh tranh:

```text
Task hoàn tất ----+
                  +--> Outcome mà caller quan sát
Timeout xảy ra ---+
                  |
Cancel request ---+
```

Ví dụ `future.get(100ms)`:

- Task hoàn tất trước khi timeout được quan sát: `get` có thể trả kết quả hoặc ném exception từ task.
- Thời gian chờ hết trong khi task còn incomplete: `get` ném `TimeoutException`.
- Task bị cancel trước khi caller lấy kết quả: `get` có thể ném `CancellationException`.
- Caller thread bị interrupt trong lúc đang chờ: `get` có thể ném `InterruptedException`.

**Không hứa thứ tự tuyệt đối khi các sự kiện xảy ra sát nhau** ngoài contract của API. Ví dụ tác vụ hoàn thành gần deadline có thể khiến các lần thử khác nhau quan sát kết quả khác nhau; chương trình đúng phải xử lý cả hai outcome mà không làm hỏng invariant.

Trong thiết kế hệ thống, thường cần một state machine rõ:

```text
NEW -> RUNNING -> SUCCEEDED
              \-> FAILED
              \-> CANCEL_REQUESTED -> STOPPED
              \-> TIMED_OUT (caller outcome, task may still RUNNING)
```

Đây là một mô hình khái niệm. `TIMED_OUT` thường là **trạng thái góc nhìn caller**, không nên tự biến thành trạng thái task đã terminal trừ khi API thực sự áp dụng cancellation/timeout completion contract.

## 21. Timeout là kết quả hay exception?

Không phải timeout nào cũng ném `TimeoutException`:

| API | Khi hết thời gian chờ |
| --- | --- |
| `Future.get(timeout, unit)` | `TimeoutException` nếu chưa có kết quả theo contract |
| `BlockingQueue.poll(timeout, unit)` | `null` khi không lấy được item |
| `BlockingQueue.offer(item, timeout, unit)` | `false` khi không thêm được |
| `CountDownLatch.await(timeout, unit)` | `false` nếu count chưa về 0 |
| `ReentrantLock.tryLock(timeout, unit)` | `false` nếu không lấy được lock |
| `Thread.join(timeout)` | Trả về sau khi đợi, nhưng caller phải kiểm tra `isAlive()` để biết thread còn sống không |
| `Object.wait(timeout)` | Trở lại sau notify, timeout hoặc spurious wakeup; phải kiểm tra lại guarded predicate |
| `CompletableFuture.orTimeout(...)` | Hoàn thành CompletableFuture exceptionally với `TimeoutException` |
| `CompletableFuture.completeOnTimeout(...)` | Hoàn thành CompletableFuture với fallback |
| Socket read với read timeout | Có thể ném `SocketTimeoutException` theo hợp đồng I/O |

Chưa tính **interruption**: các phương thức chờ có thể ném `InterruptedException` khi caller thread bị yêu cầu ngắt. Cần xử lý timeout và interruption là **hai trường hợp khác nhau**, không bắt cả hai rồi luôn coi như "đã cancel task".

## 22. Timeout của `Thread.join(timeout)` không phải kill thread

`Thread.join(timeoutMillis)` chỉ giới hạn việc **caller đợi thread kết thúc**. Khi trở về do timeout, target thread có thể vẫn chạy. Nếu cần biết worker còn sống, kiểm tra `thread.isAlive()` và chấp nhận rằng trạng thái có thể đổi ngay sau lần kiểm tra.

Ví dụ anti-pattern:

```java
worker.join(100);
System.out.println("Task finished successfully");
```

Đây là đoạn minh họa, `worker` giả định. Dòng in không có căn cứ: `join` có thể trở về do timeout, và ngay cả khi thread kết thúc, điều đó không chứng minh công việc thành công về mặt nghiệp vụ.

Cách suy nghĩ đúng:

```java
worker.join(100);
if (worker.isAlive()) {
    // Caller đã chờ đủ, nhưng worker vẫn chạy.
    // Chọn policy: tiếp tục chờ, request interrupt, hoặc return timeout.
} else {
    // Worker đã kết thúc; success/failure vẫn cần kênh kết quả riêng.
}
```

Đây là đoạn minh họa, không phải chương trình độc lập. Nếu muốn báo kết quả và exception, `Future`/`CompletableFuture` thường phù hợp hơn việc tự đọc field sau một khoảng join.

**Không dùng `Thread.stop()`** làm "cancellation cứng"; nó không bảo vệ invariant và đã bị loại khỏi cách lập trình Java an toàn.

## 23. `shutdown()`, `shutdownNow()` và `awaitTermination`

Khi quản lý `ExecutorService`, ba phương thức có vai trò khác nhau:

- `shutdown()`: bắt đầu **orderly shutdown**, không nhận task mới, cho phép các task đã gửi tiếp tục theo hợp đồng executor.
- `shutdownNow()`: **cố gắng** dừng các task đang chạy (thường bằng interruption), không chạy task chưa bắt đầu và trả danh sách task chưa bắt đầu theo contract; không bảo đảm mọi active task dừng ngay.
- `awaitTermination(timeout, unit)`: **đợi có giới hạn** để executor đạt trạng thái terminated; trả `true` nếu terminated, `false` nếu timeout, có thể ném `InterruptedException` nếu thread caller bị ngắt.

Ví dụ:

```java
pool.shutdown();
if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
    pool.shutdownNow();
}
```

Đây là đoạn minh họa với `pool` là `ExecutorService`, trong method có thể truyền/handle `InterruptedException`.

Điểm cần hiểu: `awaitTermination(5s)` trả `false` **không tự tạo shutdownNow**; code đã chủ động chọn bước `shutdownNow()` sau đó. Đây là ví dụ kết hợp **timeout → cancellation attempt** thành **policy của ứng dụng**.

Nếu caller bị interrupt trong lúc `awaitTermination`, cần có nhánh cleanup phù hợp, thường request `shutdownNow` khi chính tầng đó sở hữu executor, rồi khôi phục flag nếu không thể propagate. Không phải mọi tầng gọi đều có quyền tắt executor được chia sẻ.

## 24. Chọn policy khi hết thời gian chờ

Không có một hành động đúng cho mọi timeout:

| Mục tiêu | Policy thường phù hợp |
| --- | --- |
| Caller chỉ muốn UI phản hồi nhanh | Trả timeout/fallback; tác vụ nền có thể tiếp tục nếu có chủ đích |
| Tính toán CPU-only không còn ai cần | Request cancellation; task kiểm tra token/interrupt ở checkpoint |
| Task đọc queue đang block | Interrupt nếu API hỗ trợ; hoặc close/sentinel/timeout theo lifecycle protocol |
| Giao dịch remote có thể đã commit | Trả "outcome unknown", truy vấn trạng thái và dùng idempotency thay vì retry mù |
| Task có dữ liệu mutable đang commit | Không hủy giữa invariant; dùng atomic commit/rollback protocol |
| Task giúp làm nóng cache | Có thể để chạy tiếp sau caller timeout, nhưng giới hạn tài nguyên và stale writes |
| Executor đang shutdown | Đợi trong ngân sách, sau đó request shutdownNow nếu tầng gọi sở hữu executor và policy cho phép |

Luôn xác định **ai sở hữu quyền hủy**. Một tầng helper xử lý timeout của một request không nhất thiết được phép cancel một shared task đang phục vụ nhiều request khác.

Ví dụ cùng một `CompletableFuture` được nhiều caller dùng: caller A hết hạn chờ không nên mặc định cancel future nếu caller B vẫn cần kết quả. Cancel shared future có thể ảnh hưởng tất cả subscribers; thay vào đó mỗi caller có thể giữ một deadline chờ riêng.

## 25. Timeout lồng nhau, retry và nguy cơ khuếch đại tải

Một hệ thống có timeout 200 ms nhưng tự động retry ba lần liên tiếp có thể tiêu thụ 600 ms hoặc nhiều hơn, cộng thêm thời gian queue/network và cleanup. Nếu mỗi retry còn tạo task mới trong khi task cũ chưa dừng, tải bên dưới có thể **tăng** dù caller tưởng đang giới hạn bằng timeout.

Một policy an toàn hơn cần xét:

1. **Tổng ngân sách deadline** cho cả workflow, không tự reset vô hạn.
2. **Số lần retry tối đa** và khoảng backoff phù hợp.
3. **Idempotency** của operation (đặc biệt với I/O hoặc side effect).
4. **Cancellation/cleanup** của những attempt cũ khi không còn cần.
5. **Backpressure** của executor/queue và giới hạn in-flight tasks.
6. **Phân biệt lỗi tạm thời với cancellation:** không retry vì bị interrupt nếu policy yêu cầu dừng.
7. **Outcome unknown:** với remote side effect có thể đã commit, truy vấn tình trạng thay vì gửi thêm operation không idempotent.

Timeout không phải backpressure tự động. Một caller trả nhanh do timeout vẫn có thể để lại nhiều task chạy dài nếu không quản lý lifecycle.

## 26. Khi nào cần trạng thái "canceled", "timed out", "failed"?

Một API kết quả thường nên phân biệt:

- **SUCCEEDED:** có kết quả hợp lệ theo contract.
- **FAILED:** task chạy nhưng gặp lỗi, có exception/cause.
- **TIMED_OUT:** caller hoặc operation không nhận được kết quả đúng hạn; không tự kết luận task đã dừng.
- **CANCEL_REQUESTED:** đã yêu cầu dừng; worker chưa chắc đã xử lý.
- **CANCELED / STOPPED:** một trạng thái terminal phải được **định nghĩa chính xác**: chỉ future canceled hay cả cleanup done?
- **UNKNOWN / PENDING_RECONCILIATION:** tác động bên ngoài chưa thể xác định sau timeout.

Với library Java, `Future` có semantics state riêng của nó. Với API nghiệp vụ, tránh mượn chữ `CANCELED` nếu muốn hứa worker không còn thực thi trong khi implementation chỉ gọi `future.cancel(true)` rồi trả ngay.

Không nên gộp tất cả `TimeoutException`, `InterruptedException`, `ExecutionException` và `CancellationException` vào một nhánh "task thất bại" nếu caller cần policy khác nhau.

## 27. Các lỗi phổ biến

**1. "`Future.get(timeout)` ném `TimeoutException` là task bị hủy."** Sai. Đây là timeout của lần chờ; task có thể còn chạy.

**2. "`cancel(true)` trả `true` thì worker đã dừng."** Sai. Nó có thể chỉ báo yêu cầu cancel được chấp nhận và future chuyển state; worker cần hợp tác.

**3. "`cancel(false)` an toàn vì không động tới task."** Không nên suy như vậy. Nó có thể chuyển future sang canceled dù task vẫn chạy và side effect vẫn xảy ra; cần contract rõ.

**4. "`CompletableFuture.orTimeout` interrupt task nền."** Không có cam kết đó. Nó hoàn thành future exceptionally, underlying computation có thể tiếp tục.

**5. "`CompletableFuture.cancel(true)` giống `FutureTask.cancel(true)`."** Sai. `CompletableFuture` không dùng tham số mayInterruptIfRunning để điều khiển underlying processing.

**6. "Mọi timeout đều ném `TimeoutException`."** Sai. `poll` trả null, `tryLock` trả false, `awaitTermination` trả boolean, socket read có exception riêng.

**7. "Task đã hết thời gian nên server chắc chắn chưa xử lý."** Sai. Remote side effect có thể đã commit nhưng response đến muộn.

**8. "`Thread.interrupt()` luôn dừng network I/O ngay."** Không; phải đọc hợp đồng channel/socket/client cụ thể.

**9. "Để toàn bộ retry dùng timeout như lần đầu là vẫn đúng SLA tổng thể."** Sai; phải tính remaining budget từ deadline chung.

**10. "Task bị cancel rồi có thể bỏ cleanup."** Sai; cần giữ invariant và giải phóng resource theo policy.

**11. "Cờ cancellation tự thức thread đang block."** Sai. Token cần kết hợp signaling/timeout/interruption hoặc close.

**12. "Có `Future.isDone()` là task thành công."** Sai. Done gồm normal completion, exceptional completion hoặc cancellation tùy future.

**13. "`Thread.join(timeout)` trả về nghĩa worker đã chết."** Sai. Phải kiểm tra `isAlive` nếu cần.

**14. "Một request timeout cho phép hủy shared future của mọi caller."** Sai nếu task đang phục vụ người khác; cần quản lý quyền sở hữu và reference/lifecycle.

**15. "Timeout kết thúc một network transaction."** Không. Transaction, idempotency và outcome reconciliation phải được thiết kế riêng.

## 28. Cách kiểm thử timeout và cancellation một cách đáng tin

Đừng thử bằng cách đợi may rủi `Thread.sleep(100)` rồi giả định worker đã sẵn sàng. Dùng **CountDownLatch** hoặc primitive phối hợp để tạo mốc xác định:

- Để kiểm tra timeout của `get`, giữ task chưa thể hoàn tất bằng một latch chưa mở; sau timeout kiểm tra future còn incomplete, rồi mở latch và lấy kết quả.
- Để kiểm tra cancellation, cho worker báo đã vào vùng chờ, gọi `cancel(true)`, rồi quan sát chính worker đã nhận interrupt và gửi completion riêng.
- Để kiểm tra queue timeout, giữ queue chắc chắn trống trong lần poll đầu, sau đó tự đưa item để chứng minh queue còn dùng bình thường.
- Để kiểm tra lock timeout, giữ lock trong holder có latch kiểm soát, rồi thử `tryLock` từ main; chỉ mở latch sau khi lần thử đầu kết thúc.
- Để kiểm tra late completion, dùng version/generation token bảo đảm kết quả từ attempt cũ không ghi đè attempt mới.

Ở test automation, có thể thêm **timeout của test harness** để tránh treo khi code sai; tuy nhiên timeout test không tự động cung cấp cancellation/cleanup cho task bị kẹt. Cần `finally` để giải phóng latch, close resource, shutdown executor hoặc interrupt worker theo quyền sở hữu, với thời gian dọn dẹp hợp lý.

**Stress test không chứng minh tính đúng tuyệt đối**; quan trọng là hợp đồng API, quan hệ happens-before, state transitions và invariant phù hợp.

## 29. Checklist lựa chọn API

Trước khi viết `timeout` hoặc `cancel`, trả lời:

1. **Cái gì cần hết hạn?** Lần chờ của caller, một operation blocking hay toàn bộ workflow?
2. **Khi hết hạn, task có được chạy tiếp không?** Nếu có, tại sao và ai còn cần kết quả?
3. **Ai được quyền cancel?** Caller hiện tại có sở hữu task, executor hoặc shared future không?
4. **Task nhận yêu cầu bằng cách nào?** Interruption, cancellation token, close resource, sentinel hay API đặc thù?
5. **Task có điểm kiểm tra và cleanup không?** Có tiếp tục side effect sau yêu cầu hủy không?
6. **Future state có phản ánh worker lifecycle không?** Đừng nhầm `isCancelled` với resource đã giải phóng.
7. **Có remote side effect không?** Timeout có thể dẫn đến unknown outcome; cần idempotency/truy vấn trạng thái?
8. **Có nhiều bước hoặc retry không?** Truyền remaining deadline thay vì mỗi bước reset budget.
9. **Cần báo kết quả bằng gì?** Return value, `TimeoutException`, `CancellationException`, custom outcome hay future?
10. **Có race completion-vs-cancel không?** Cần chấp nhận các trạng thái hợp lệ và transition atomic?
11. **Có thể mất interruption không?** Catch `InterruptedException` cần propagate, hoặc restore và thoát khi không thể throw.
12. **Có blocking I/O không hỗ trợ interruption?** Cần timeout/close/cancellation policy riêng.
13. **Có đảm bảo bounded concurrency không?** Timeout không thay queue capacity, semaphore hoặc limit in-flight.
14. **Bạn sẽ test ra sao?** Tạo điều kiện bằng latch, kiểm chứng cleanup và late completion chứ không đoán timing.

## 30. Câu hỏi tự kiểm tra

**Câu 1.** `Future.get(100, MILLISECONDS)` ném `TimeoutException`. Task có chắc bị hủy không?

**Không.** Chỉ lần chờ của caller bị timeout. Task có thể vẫn đang chạy, chưa bắt đầu, hoặc hoàn tất muộn. Nếu muốn yêu cầu dừng, cần cancellation policy và thao tác riêng.

**Câu 2.** `Future.cancel(true)` trả `true`. Có thể trả "worker đã cleanup xong" cho người dùng không?

**Không mặc định.** Future có thể canceled nhưng worker còn cần nhận interrupt và cleanup. Nếu cần bảo đảm cleanup hoàn tất, phải có completion signal/contract riêng.

**Câu 3.** `BlockingQueue.poll(timeout)` trả `null` thì producer đã bị cancel chưa?

**Chưa.** Nó chỉ nói poll không nhận được item đúng hạn. Queue và producer khác vẫn có thể hoạt động.

**Câu 4.** `CompletableFuture.orTimeout` khác `Future.get(timeout)` ở điểm nào?

`orTimeout` có thể **hoàn thành chính CompletableFuture exceptionally** sau deadline nếu chưa complete; `get(timeout)` chỉ làm lần chờ của caller kết thúc bằng `TimeoutException`. Không API nào mặc nhiên chứng minh underlying task đã dừng.

**Câu 5.** Vì sao `CompletableFuture.cancel(true)` không nên dùng như cam kết sẽ interrupt worker?

Vì `CompletableFuture` không dùng tham số `mayInterruptIfRunning` để điều khiển underlying computation theo contract của nó. Muốn hủy worker, cần signaling và quản lý task thực thi thích hợp.

**Câu 6.** Nếu một HTTP request timeout, có nên retry lập tức bằng request mới không?

Chỉ khi semantics của operation cho phép. Server có thể đã commit side effect; cần idempotency key hoặc truy vấn/reconciliation để tránh thực thi trùng.

**Câu 7.** Ba bước mỗi bước timeout 100 ms có bảo đảm workflow hoàn tất trong 100 ms không?

**Không.** Tổng thời gian chờ có thể gần 300 ms cộng overhead. Nếu SLA là 100 ms tổng thể, phải dùng deadline chung và remaining budget.

**Câu 8.** `awaitTermination(5s)` trả `false` có tự gọi `shutdownNow()` không?

**Không.** Nó chỉ trả kết quả chờ. Caller phải chủ động chọn policy escalation, và chỉ làm khi sở hữu executor.

**Câu 9.** `Thread.interrupt()` có phải là cancellation với đảm bảo dừng tức thì không?

**Không.** Đây là yêu cầu hợp tác; worker có thể phản hồi ở interruptible blocking call hoặc điểm kiểm tra. Một số I/O/side effect không thể dừng ngay.

**Câu 10.** Nếu caller A timeout nhưng caller B vẫn đợi cùng shared future, A có nên luôn `cancel` future đó không?

**Không.** A có thể chỉ ngừng chờ; hủy shared task sẽ ảnh hưởng B. Quyền hủy phải dựa vào ownership và contract của hệ thống.

## 31. Tổng kết

- **Timeout** chủ yếu là giới hạn thời gian **chờ** hoặc hạn chót của một operation. Nó **không đồng nghĩa** task đang chạy đã dừng.
- **Cancellation** là **yêu cầu hủy công việc**; ngay cả `cancel(true)` cũng không cưỡng bức worker hoàn tất cleanup hoặc rollback side effect.
- `Future.get(timeout)` chỉ timeout **caller wait**; `CompletableFuture.orTimeout` và `completeOnTimeout` có thể complete **future state**; `ExecutorService.invokeAll(timeout)` lại có policy cancel unfinished tasks. **Đọc contract của đúng API.**
- `BlockingQueue.poll(timeout)`, `Lock.tryLock(timeout)`, `awaitTermination(timeout)` và `Thread.join(timeout)` có semantics timeout khác nhau và không tự cancel worker khác.
- Dùng **deadline chung** cho nhiều bước/retry, truyền **remaining budget** và quản lý **backpressure** để tránh các task chạy dài chất chồng sau timeout.
- Khi timeout liên quan I/O hoặc side effect bên ngoài, kết quả có thể **chưa xác định**; phải dùng idempotency, truy vấn trạng thái và quy tắc xử lý late result khi cần.
- Một hệ thống đúng cần tách rõ **caller stopped waiting**, **cancel requested**, **worker observed cancel**, và **cleanup completed**.

Câu hỏi nên đặt ra khi gặp timeout là: **"Ai đã hết thời gian — người chờ hay công việc đang chạy? Nếu tôi muốn dừng công việc, API nào bảo đảm điều đó, theo giao thức nào và khi nào cleanup thật sự hoàn tất?"**. Trả lời được các câu này sẽ tránh phần lớn nhầm lẫn giữa timeout và cancellation trong Java concurrency.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-009">shutdown(), shutdownNow() và awaitTermination()</a>

<details>
<summary>Xem chi tiết</summary>

# `shutdown()`, `shutdownNow()` và `awaitTermination()` trong Java

## 1. Vì sao cần quản lý vòng đời ExecutorService?

Trong Java Concurrency, `ExecutorService` giúp ứng dụng nộp tác vụ thay vì tự tạo và quản lý mọi `Thread`. Ví dụ:

```java
ExecutorService executor = Executors.newFixedThreadPool(4);
executor.submit(() -> process());
```

Đây là đoạn minh họa, cần import các lớp trong `java.util.concurrent` và định nghĩa `process()`. Một executor thường **giữ các worker thread tồn tại** để xử lý nhiều tác vụ. Nếu nộp xong rồi bỏ mặc, ứng dụng có thể không thoát như mong đợi, không biết công việc đã xong chưa, hoặc bị mất dữ liệu trong lúc dừng.

Ba API cốt lõi cho vòng đời là:

- **`shutdown()`:** ngừng nhận tác vụ mới, cho những tác vụ **đã nộp** cơ hội hoàn thành.
- **`shutdownNow()`:** cố gắng dừng các tác vụ đang thực thi (thường bằng interrupt), không bắt đầu xử lý những tác vụ còn chờ, và trả về danh sách tác vụ đang chờ chưa được thực thi.
- **`awaitTermination(timeout, unit)`:** **chờ** executor kết thúc trong thời hạn cho phép; tự nó **không gửi yêu cầu shutdown**.

Điểm dễ nhầm nhất: **“đã gọi shutdown” không có nghĩa “mọi thread đã dừng”**. Phải phân biệt yêu cầu đóng, yêu cầu hủy và sự kiện đã kết thúc thật.

Bài này tập trung **Java Concurrency nói chung**, dùng `ExecutorService` và các primitive thuộc JDK. Không phụ thuộc framework.

## 2. Mô hình vòng đời dễ hiểu

Với một executor đang chạy:

```text
                  nhận submit/execute
                         |
                         v
                    RUNNING
                       |
         shutdown()    |    shutdownNow()
             |         |         |
             v                   v
    không nhận task mới     cố ngắt task đang chạy,
    đã nộp thì xử lý        ngừng xử lý task đang chờ
             |                   |
             +---------+---------+
                       |
      các worker kết thúc, không còn task phải chạy
                       |
                       v
                   TERMINATED
```

Đây là mô hình khái niệm. `ThreadPoolExecutor` có các trạng thái nội bộ chi tiết hơn, nhưng **không nên dựa vào state machine nội bộ** để dùng API. Với `ExecutorService`, các mốc quan sát qua giao diện là:

- `isShutdown()`: đã có yêu cầu shutdown. Nó có thể trả `true` ngay khi executor vẫn có worker đang chạy.
- `isTerminated()`: executor đã hoàn toàn kết thúc sau shutdown.
- `awaitTermination(...)`: đợi mốc terminated hoặc hết thời gian, có thể bị interrupt.

Tính chất cần nhớ:

```text
isShutdown() == true
       không suy ra
isTerminated() == true
```

Một task mất hàng phút hoặc không hợp tác với interrupt vẫn có thể giữ executor ở trạng thái chưa terminated rất lâu sau khi yêu cầu shutdown.

## 3. So sánh trực tiếp ba API

| API | Nhận task mới? | Task đã chạy | Task đang chờ trong queue | Có chờ trong caller? | Giá trị trả về |
| --- | --- | --- | --- | --- | --- |
| `shutdown()` | Dừng nhận mới | Được tiếp tục thực hiện bình thường | Được xử lý theo chính sách executor | Không | `void` |
| `shutdownNow()` | Dừng nhận mới | Cố gắng hủy bằng interrupt hoặc cơ chế thích hợp; **không bảo đảm dừng** | Không tiếp tục xử lý tác vụ chưa bắt đầu; được trả về trong danh sách pending theo hợp đồng | Không đợi toàn bộ task dừng | `List<Runnable>` |
| `awaitTermination(t, unit)` | Không thay đổi việc nhận task | Không tự hủy | Không tự dọn queue | **Có**, chờ tối đa thời hạn | `boolean`, hoặc `InterruptedException` |

**Lưu ý về phạm vi:** một `ExecutorService` có nhiều implementation. Với `ThreadPoolExecutor` thông thường, `shutdownNow()` cố interrupt worker và lấy các tác vụ chưa chạy ra khỏi queue. Việc interrupt có phát huy tác dụng hay không phụ thuộc vào **task có hợp tác hay không**, và một task đang nằm ở ranh giới “queued/running” có thể cạnh tranh với yêu cầu shutdown.

**`shutdownNow()` không có nghĩa “kill thread”.** Java không bảo đảm cưỡng bức kết thúc một task tùy ý khi gọi phương thức này.

## 4. `shutdown()`: đóng có trật tự (graceful shutdown)

Hãy dùng `shutdown()` khi muốn:

1. Không cho caller nộp thêm tác vụ.
2. Để các tác vụ đã nhận vào executor tiếp tục được hoàn thành.
3. Có thể đợi sự kiện hoàn tất ở bước sau bằng `awaitTermination()`.

`shutdown()` trả về **ngay sau khi khởi tạo quy trình đóng**, không đợi mọi worker hoàn thành.

Nếu caller tiếp tục gọi `execute()` hoặc `submit()` sau khi executor đã shutdown, executor sẽ **từ chối** tác vụ mới, thường bằng `RejectedExecutionException` tùy implementation và rejection policy. Đừng nghĩ `shutdown()` chỉ là một cờ “sau này sẽ dừng nhận”, còn có thể tiếp tục submit bình thường.

### Ví dụ 1: xử lý hết hai task đã nộp

Tạo tệp `GracefulShutdownDemo.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GracefulShutdownDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> System.out.println("task A"));
        executor.execute(() -> System.out.println("task B"));

        executor.shutdown();
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("shutdown=" + executor.isShutdown());
        System.out.println("terminated=" + terminated);
    }
}
```

Biên dịch/chạy trên JDK 17+:

```bash
javac GracefulShutdownDemo.java
java GracefulShutdownDemo
```

Kết quả điển hình khi hai task hoàn thành trước thời hạn:

```text
task A
task B
shutdown=true
terminated=true
```

Vì executor có một worker, hai task được nộp lần lượt và chạy theo thứ tự trong FIFO queue của single-thread executor thông thường. `shutdown()` không hủy task A hoặc B. `awaitTermination()` chờ tối đa mười giây rồi trả về `true` nếu đã terminated.

Nếu máy bị trì hoãn bất thường đến mức hết thời hạn, dòng cuối có thể là `terminated=false`, **không có nghĩa task chắc chắn thất bại**: nó chỉ nói executor chưa đạt trạng thái terminated trong thời gian chờ. Ví dụ thời hạn mười giây là giá trị minh họa, không phải SLA cho mọi môi trường.

### Vì sao `shutdown()` không đủ để biết kết quả nghiệp vụ?

`shutdown()` chỉ yêu cầu lifecycle transition. Một `Runnable` đã được chạy có thể thất bại; một `Callable` trả giá trị có thể cần kiểm tra qua `Future.get()`. **Đợi executor terminated** và **kiểm tra outcome của từng task** là hai nhu cầu khác nhau.

Nếu ứng dụng phải biết từng task thành công hay thất bại, hãy lưu `Future`, truyền outcome qua một kênh, hoặc dùng cơ chế kết quả phù hợp. Đừng coi `isTerminated()==true` là tất cả nghiệp vụ đã thành công.

## 5. `awaitTermination()` chỉ quan sát, không tự shutdown

Chữ ký:

```java
boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException;
```

Đây là chữ ký API rút gọn. Phương thức đợi cho đến khi **executor đã terminated**, **hết thời gian** hoặc **thread đang chờ bị interrupt**:

- Trả `true`: executor đã đạt trạng thái terminated.
- Trả `false`: hết thời hạn mà trạng thái terminated chưa đạt.
- Ném `InterruptedException`: thread gọi phương thức bị interrupt trong lúc đợi; cần propagate hoặc xử lý theo chính sách hủy.

Gọi `awaitTermination()` khi executor còn nhận task và không có ai gọi shutdown thường chỉ khiến caller đợi đến timeout: bản thân phương thức **không đóng executor**. Một thread khác vẫn có thể gọi shutdown trong lúc caller đang đợi, và khi executor kết thúc thì việc đợi mới có thể trả `true`.

### Ví dụ 2: timeout là false, sau khi nhả task là true

Dùng latch để giữ task chưa thể kết thúc trong lúc main thử timeout. Điều này chứng minh ngữ nghĩa thời hạn mà không dựa vào việc ngủ ngẫu nhiên.

Tạo tệp `AwaitTerminationDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AwaitTerminationDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        executor.execute(() -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        entered.await();
        executor.shutdown();

        boolean first = executor.awaitTermination(
                50, TimeUnit.MILLISECONDS);
        System.out.println("before release=" + first);

        release.countDown();

        boolean second = executor.awaitTermination(
                10, TimeUnit.SECONDS);
        System.out.println("after release=" + second);
    }
}
```

```bash
javac AwaitTerminationDemo.java
java AwaitTerminationDemo
```

Kết quả khi không có interrupt ngoài ý muốn và task được lập lịch hoàn tất trong thời hạn thứ hai:

```text
before release=false
after release=true
```

`entered` đảm bảo task đã vào runnable. Vì `release` chưa được mở, task chưa thể hoàn tất trong lần chờ thứ nhất: `false` có ý nghĩa **đã hết thời gian chờ**. Main mở `release`, task kết thúc và executor có thể chuyển tới terminated; lần chờ thứ hai thường trả `true`.

Không nên xem một lần `awaitTermination(10, SECONDS)` là đảm bảo thời gian tuyệt đối: hệ điều hành có thể trì hoãn runnable, nên code production phải xử lý cả `false`.

### Phải làm gì khi `awaitTermination` trả `false`?

Tùy chính sách:

- Chờ thêm nếu độ trễ cho phép.
- Gọi `shutdownNow()` để yêu cầu cancel task còn chạy và lấy lại những task chưa bắt đầu.
- Ghi log/đánh dấu shutdown chưa hoàn tất và kích hoạt cảnh báo hoặc quá trình cleanup khác.
- Không được giả định rằng `false` có nghĩa thread đã kết thúc hoặc resource đã được trả lại.

Nếu một worker không thể hủy hợp tác, việc chờ lại có thể tiếp tục timeout. Không có phương thức nào trong ba API bảo đảm cưỡng bức hoàn tất một task bất kỳ.

## 6. `shutdownNow()`: cố hủy đang chạy và trả về pending tasks

Phương thức:

```java
List<Runnable> notStarted = executor.shutdownNow();
```

Nó **cố gắng**:

1. Ngừng chấp nhận task mới.
2. Đưa executor vào quy trình dừng ngay.
3. Cố gửi `interrupt()` đến các worker đang thực thi task (với thread pool thông thường).
4. Không bắt đầu những tác vụ còn ở queue; trả về danh sách `Runnable` của các task đang chờ chưa thực thi.

Từ **“Now”** ở đây là *yêu cầu ngay*, **không phải kết thúc hoàn toàn ngay**.

Cần hiểu danh sách trả về:

- Nó đại diện cho các task **đang chờ chưa bắt đầu** mà executor trả về tại lúc dừng.
- Nó **không chứa toàn bộ task đang chạy**; task đang chạy có thể cần tự phản hồi interrupt.
- Với `submit()`, executor có thể bọc `Callable`/`Runnable` trong `FutureTask` hoặc một wrapper khác. `List<Runnable>` không nhất thiết chứa đúng object task ban đầu bạn đã truyền vào.
- Không thể chỉ dựa vào danh sách đó để suy ra đã xử lý thành công bao nhiêu task trong toàn hệ thống. Task có thể đang ở ranh giới lấy khỏi queue, bắt đầu chạy hoặc đang cleanup.

Nếu ứng dụng muốn retry pending tasks, phải xác định **task có idempotent không, có còn hợp lệ không và cách tránh chạy hai lần**. Đừng gửi lại mọi `Runnable` một cách mù quáng.

### Ví dụ 3: một task đang chạy bị interrupt, một task pending được trả về

Tạo tệp `ShutdownNowDemo.java`:

```java
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShutdownNowDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1));

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        AtomicBoolean sawInterrupt = new AtomicBoolean(false);

        executor.execute(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await(); // Chờ đến khi bị interrupt.
            } catch (InterruptedException e) {
                sawInterrupt.set(true);
                Thread.currentThread().interrupt();
            } finally {
                exited.countDown();
            }
        });

        started.await();
        executor.execute(() ->
                System.out.println("queued task should not run"));

        List<Runnable> pending = executor.shutdownNow();
        exited.await();

        boolean terminated =
                executor.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("pending=" + pending.size());
        System.out.println("interrupt observed=" + sawInterrupt.get());
        System.out.println("terminated=" + terminated);
    }
}
```

```bash
javac ShutdownNowDemo.java
java ShutdownNowDemo
```

Kết quả khi worker phản hồi interrupt và hoàn tất trong thời hạn:

```text
pending=1
interrupt observed=true
terminated=true
```

**Tại sao kết quả có thể dự đoán:**

- Pool có đúng một worker và queue capacity 1.
- Main đợi `started` nên biết task thứ nhất đã chạy; task này block ở một latch **không bao giờ được mở**.
- Task thứ hai chỉ có thể nằm ở queue vì worker duy nhất chưa thoát.
- `shutdownNow()` yêu cầu interrupt worker. `await()` của task thứ nhất có thể bị ngắt, nên catch ghi nhận interrupt rồi rời.
- Task thứ hai bị lấy ra khỏi queue và được trả trong `pending`, không chạy.
- Main đợi `exited` rồi chờ executor terminated trước khi in kết quả.

Ví dụ này dùng latch không bao giờ được mở **chỉ để minh họa shutdownNow và interrupt**. Task thực tế nên có cả đường hủy, timeout và cleanup; đừng dùng đợi vô hạn mà không có phương án khôi phục.

## 7. `shutdownNow()` không bảo đảm dừng được task bỏ qua interrupt

Interrupt trong Java là một **tín hiệu hợp tác**. Nhiều thao tác chặn như `Thread.sleep()`, `BlockingQueue.take()`, `Object.wait()`, `Condition.await()` hay `Lock.lockInterruptibly()` có thể ném `InterruptedException`. Nhưng nếu một task chỉ làm việc CPU và không kiểm tra trạng thái interrupt, nó có thể tiếp tục chạy sau `shutdownNow()`.

Ví dụ sau **cố tình viết task không hợp tác** để minh họa giới hạn này. Không dùng kiểu busy-spin đó trong ứng dụng production.

Tạo `UncooperativeTaskDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class UncooperativeTaskDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicBoolean keepWorking = new AtomicBoolean(true);
        CountDownLatch started = new CountDownLatch(1);

        executor.execute(() -> {
            started.countDown();
            while (keepWorking.get()) {
                Thread.onSpinWait(); // Cố tình không kiểm tra interrupt.
            }
        });

        started.await();
        executor.shutdownNow();

        boolean first = executor.awaitTermination(
                50, TimeUnit.MILLISECONDS);
        System.out.println("terminated while task ignores interrupt=" + first);

        keepWorking.set(false);

        boolean second = executor.awaitTermination(
                10, TimeUnit.SECONDS);
        System.out.println("terminated after cooperative stop=" + second);
    }
}
```

```bash
javac UncooperativeTaskDemo.java
java UncooperativeTaskDemo
```

Kết quả nếu task được lập lịch kết thúc trong thời hạn thứ hai:

```text
terminated while task ignores interrupt=false
terminated after cooperative stop=true
```

Task không đọc cờ interrupt. Do đó `shutdownNow()` **không thể tự chấm dứt** vòng lặp. Chỉ khi main đặt `keepWorking=false`, worker mới thoát và executor có thể terminated.

Trong code thật, sử dụng cơ chế chờ/block phù hợp hoặc kiểm tra interrupt/token hủy tại những điểm an toàn. `Thread.onSpinWait()` chỉ là gợi ý tối ưu cho vòng spin; nó **không kiểm tra hủy, không ngủ và không nhả khóa**.

Nếu task bị kẹt trong I/O hoặc thư viện không hỗ trợ interrupt, `shutdownNow()` cũng có thể không kết thúc tác vụ sớm. Một số tài nguyên cần biện pháp khác như đóng socket/stream, deadline, timeout hoặc API cancellation phù hợp.

## 8. Mẫu đóng hai giai đoạn: graceful rồi fallback

Một chiến lược phổ biến:

1. **Gửi `shutdown()`** để ngừng nhận việc mới.
2. **Đợi có giới hạn** bằng `awaitTermination()`.
3. Nếu vẫn chưa kết thúc, **gọi `shutdownNow()`** để yêu cầu hủy các tác vụ còn chạy và lấy danh sách pending nếu cần.
4. **Đợi có giới hạn lần nữa**.
5. Nếu vẫn không kết thúc, báo trạng thái **shutdown chưa hoàn tất** thay vì giả định mọi thread đã dừng.
6. Nếu thread gọi phương thức shutdown bị interrupt trong lúc chờ, gửi yêu cầu `shutdownNow()` và **khôi phục trạng thái interrupt** hoặc propagate exception theo hợp đồng.

Mẫu Java 17+:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class ExecutorShutdown {
    private ExecutorShutdown() {}

    public static void stop(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

Đây là **lớp tiện ích hoàn chỉnh có thể biên dịch**, nhưng không có `main`, nên không chạy trực tiếp bằng `java ExecutorShutdown`. Nó minh họa mẫu shutdown hai giai đoạn; hai thời hạn năm giây chỉ là **giá trị ví dụ**, cần chọn phù hợp với SLA và yêu cầu cleanup của hệ thống.

**Không nhầm:** nếu `awaitTermination` thứ hai trả `false`, executor vẫn có thể còn thread sống. Hàm log lỗi để caller biết tình trạng, không thể ép thread dừng. Trong ứng dụng production nên truyền trạng thái kết thúc lên caller để nó đưa ra quyết định tiếp theo.

### Mẫu hai giai đoạn có cần xem danh sách pending không?

Nếu pending task là những tác vụ quan trọng (ghi hóa đơn, thanh toán, lưu dữ liệu), không được mặc định rằng có thể **bỏ chúng** sau `shutdownNow()`. Có thể phải persist, retry có kiểm soát hoặc báo lỗi cho chủ sở hữu. Ngược lại, với task thăm dò đã lỗi thời, có thể chấp nhận hủy.

Chính sách xử lý phần việc chưa chạy là **quyết định nghiệp vụ**, không phải một lựa chọn `ExecutorService` tự làm giúp.

## 9. `InterruptedException` trong lúc đợi termination

`awaitTermination(timeout, unit)` là thao tác có thể bị interrupt. Khi thread đang chờ bị ngắt, phương thức ném `InterruptedException`.

Có hai chiến lược hợp lệ tùy tầng API:

**Truyền tiếp:**

```java
void stopAndWait(ExecutorService executor)
        throws InterruptedException {
    executor.shutdown();
    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
    }
}
```

Đây là đoạn minh họa. Việc propagate cho tầng gọi quyết định có thử thêm, đóng tài nguyên khác hay báo lỗi.

**Không thể propagate thì khôi phục cờ:**

```java
try {
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);
} catch (InterruptedException e) {
    executor.shutdownNow();
    Thread.currentThread().interrupt();
}
```

Đây cũng là đoạn minh họa. `Thread.currentThread().interrupt()` khôi phục trạng thái ngắt của thread **đang gọi shutdown**, vì một thao tác chặn thường xóa cờ khi ném `InterruptedException`.

Không “nuốt” interrupt bằng `catch (InterruptedException ignored) {}` rồi tiếp tục đợi vô hạn, nếu hệ thống cần khả năng hủy hoặc shutdown responsive.

Một lưu ý: `Thread.currentThread().isInterrupted()` đọc mà không xóa cờ; `Thread.interrupted()` (static) đọc và **xóa** cờ. Sử dụng có chủ đích, tránh làm mất tín hiệu hủy.

## 10. Task phải hợp tác với interrupt ra sao?

Trong code chạy bởi executor, điều quan trọng không chỉ là caller gọi `shutdownNow()` mà **task có đáp ứng yêu cầu hủy**.

### Task chặn ở API interruptible

Ví dụ `BlockingQueue.take()`:

```java
try {
    while (!Thread.currentThread().isInterrupted()) {
        Work item = queue.take();
        process(item);
    }
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
} finally {
    cleanup();
}
```

Đây là đoạn minh họa, với `Work`, `queue`, `process` và `cleanup` do ứng dụng định nghĩa. Khi shutdownNow gửi interrupt, `take()` có thể ném `InterruptedException`; task thoát vòng, cleanup trong finally và khôi phục cờ.

**Không nhất thiết phải khôi phục rồi lại lặp tiếp.** Trong runnable, một đường catch interrupt nên kết thúc tác vụ hoặc chuyển thành kết quả hủy theo hợp đồng. Nếu chỉ khôi phục rồi trở về một vòng tiếp tục gọi các API chặn, có thể tạo vòng exception vô ích.

### Task CPU-bound

Nếu task tính toán lâu, không gọi API chặn, cần kiểm tra tín hiệu trong vòng lặp:

```java
for (int i = 0; i < batches.size(); i++) {
    if (Thread.currentThread().isInterrupted()) {
        return;
    }
    processBatch(batches.get(i));
}
```

Đây là đoạn minh họa. Kiểm tra interrupt ở điểm phù hợp; phải chắc rằng việc thoát giữa chừng **không phá invariant** hoặc để resource đang giữ bị rò rỉ.

### Task đã bắt đầu thay đổi nhiều field

Interrupt không rollback phép gán đã thực hiện. Nếu task dừng giữa một giao dịch trong bộ nhớ hoặc I/O nhiều bước, dữ liệu có thể không nhất quán. Cần chia công việc thành **các đơn vị commit hợp lệ**, hoàn thành/rollback một đơn vị trước khi phản hồi hủy.

## 11. `Future.cancel(true)` khác `shutdownNow()` ở đâu?

Cả hai có thể sử dụng interrupt, nhưng phạm vi khác nhau:

- `Future.cancel(true)` yêu cầu hủy **một task cụ thể**; khi task đang chạy, executor có thể cố interrupt thread thực hiện task đó theo hợp đồng tương ứng.
- `shutdownNow()` yêu cầu dừng **cả executor**, từ chối nộp mới, xử lý các worker đang chạy và trả về pending tasks.
- `Future.cancel(false)` không yêu cầu interrupt task đang chạy. Một Future bị đánh dấu canceled không chứng minh worker đã xong cleanup.
- `shutdown()` khác cả hai: không yêu cầu hủy những task đã nhận.

Ví dụ nếu có ba request độc lập trên cùng pool, chỉ request B hết hạn thì `futureB.cancel(true)` có thể phù hợp hơn shutdown toàn bộ pool. Nếu ứng dụng đóng và không còn chấp nhận công việc, cần xử lý lifecycle ở cấp executor.

Sau khi `Future` bị cancel, gọi `future.get()` thường ném `CancellationException`. Điều đó không phải một cơ chế đợi chắc chắn cho cleanup của task đã chạy. Nếu cần biết tài nguyên thật sự được nhả, dùng một tín hiệu hoàn tất riêng hoặc quản lý vòng đời ở tầng task/executor.

## 12. Nộp task sau shutdown và RejectedExecutionException

Ngay sau khi executor đã shutdown, yêu cầu nộp mới không còn được chấp nhận.

Ví dụ dùng `execute()`:

```java
ExecutorService executor = Executors.newSingleThreadExecutor();
executor.shutdown();

try {
    executor.execute(() -> System.out.println("not accepted"));
} catch (RejectedExecutionException e) {
    System.out.println("rejected");
}
```

Đây là **đoạn minh họa** cần import `ExecutorService`, `Executors` và `RejectedExecutionException`; với executor tiêu chuẩn, output là:

```text
rejected
```

Nếu dùng `ThreadPoolExecutor` với một `RejectedExecutionHandler` tùy biến, tác động của rejection policy có thể khác, ví dụ chạy task trên caller, loại bỏ task hoặc ném exception. Đừng viết code phụ thuộc mù quáng vào việc **mọi implementation đều ném exception** nếu bạn cho phép cấu hình handler khác.

Lỗi concurrency thường gặp khi một thread bắt đầu shutdown trong lúc thread khác tiếp tục submit: không có gì bảo đảm “kiểm tra `!isShutdown()` rồi submit” là atomic, bởi executor có thể shutdown giữa hai thao tác. Caller cần xử lý rejection như một khả năng bình thường của lifecycle transition.

## 13. `isShutdown()` và `isTerminated()` là quan sát trạng thái, không là barrier cho nghiệp vụ

`isShutdown()` cho biết một yêu cầu shutdown đã xảy ra. Nó không nói rằng queue trống, không nói rằng mọi worker đã exit, và không nói rằng file/network/database đã được flush hoàn tất.

`isTerminated()` cho biết executor đã terminated; nhưng vẫn không nói **mọi task đã thành công**. Một worker có thể ném exception rồi kết thúc, executor vẫn hoàn tất lifecycle.

Một mẫu sai:

```java
executor.shutdown();
if (executor.isShutdown()) {
    saveResults(); // Sai nếu assume task đã hoàn tất.
}
```

Đây là đoạn phản ví dụ. Khi cần kết quả chắc chắn, lưu `Future` và chờ `get()`, dùng `awaitTermination()` theo chính sách, hoặc cơ chế kết quả/bàn giao khác.

**Memory visibility:** quan hệ happens-before được đặc tả rõ cho việc nộp task vào executor và việc nhận kết quả qua `Future.get()`. Nếu task ghi một kết quả cho caller cần đọc, `Future.get()` là cách rõ ràng để vừa biết outcome vừa có hợp đồng bộ nhớ phù hợp. Không nên dùng một cờ trạng thái lifecycle thay cho mọi giao thức chia sẻ mutable state.

## 14. Timeout, deadline và shutdown budget

Một timeout trong `awaitTermination` là **thời hạn chờ tối đa của lời gọi đó**, không phải lời hứa task sẽ thực hiện xong đúng lúc. Nếu gọi:

```java
executor.shutdown();
if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
    executor.shutdownNow();
    executor.awaitTermination(5, TimeUnit.SECONDS);
}
```

thì tổng thời gian chờ theo hai lời gọi có thể tới **khoảng mười giây** (cộng overhead). Nếu hệ thống có deadline shutdown tổng thể, cần lập ngân sách thời gian cho các pha và các tài nguyên khác; đừng lặng lẽ cộng thời hạn nhiều lần vượt yêu cầu.

Ví dụ ý tưởng:

```text
Shutdown deadline tổng: 8 giây
0s         5s               8s
|----------|----------------|
 graceful      cố hủy và cleanup
```

Code production có thể tính thời gian còn lại bằng `System.nanoTime()` (để đo khoảng thời gian đơn điệu) rồi truyền phần còn lại cho các lệnh chờ. Không dựa vào `System.currentTimeMillis()` để đo duration nếu hệ thống có thể chỉnh đồng hồ.

Ngay cả khi deadline hết, **task có thể vẫn chạy**. Phải quyết định chính sách ứng dụng: báo lỗi, ghi log/metric, tiếp tục cleanup không chặn, hoặc yêu cầu hệ thống quản lý process xử lý theo giới hạn. Ba phương thức `ExecutorService` không cung cấp lệnh “kill bất kỳ Java thread nào ngay lập tức”.

## 15. Xử lý các task pending sau shutdownNow

Danh sách `List<Runnable>` từ `shutdownNow()` không phải là một kết quả nghiệp vụ đầy đủ. Với `execute(runnable)`, danh sách có thể chứa chính task chưa bắt đầu; với `submit(...)`, các tác vụ có thể được bọc trong `RunnableFuture`/`FutureTask`.

Cần quyết định:

- **Discard:** chấp nhận hủy những task không còn cần.
- **Retry:** chỉ khi task còn hợp lệ và logic an toàn khi thử lại.
- **Persist:** lưu yêu cầu chưa thực thi ra kho bền vững nếu không được phép mất.
- **Fail request:** báo rõ cho caller thay vì để Future/chờ phản hồi treo vô hạn.
- **Reconcile:** nếu một task có thể đã bắt đầu, cần kiểm tra kết quả đã commit trước khi retry để tránh xử lý trùng.

Một queue trong bộ nhớ **không phải durable job queue**. Nếu process crash, danh sách pending có thể không còn. Nếu công việc quan trọng như thanh toán hoặc ghi sổ, cần idempotency, transaction, durable queue hoặc một giao thức recovery ở tầng tương ứng.

## 16. Khi nào `shutdown()` là đủ, khi nào cần `shutdownNow()`?

Chọn `shutdown()` nếu:

- Muốn **xử lý hết công việc đã nhận**.
- Task có thời lượng dự kiến phù hợp với thời hạn dừng.
- Hệ thống muốn graceful drain hơn là hủy ngay.
- Công việc không nên bị bỏ giữa chừng.

Chọn `shutdownNow()` khi:

- Không còn thời gian chờ graceful.
- Task còn chạy không còn cần thiết hoặc được thiết kế hỗ trợ cancel.
- Cần lấy lại những task còn queued để xử lý theo chính sách khác.
- Hệ thống ưu tiên giải phóng tài nguyên sớm nhưng chấp nhận bản chất best-effort.

Dùng `awaitTermination()` khi:

- Caller cần **biết executor đã dừng thật chưa**.
- Muốn triển khai deadline/graceful fallback.
- Cần đồng bộ vòng đời trước khi đóng các resource phụ thuộc vào worker.

Không nhất thiết lúc nào cũng gọi `shutdownNow()` ngay sau `shutdown()`. Nếu shutdownNow được gọi quá sớm, các task đang hoạt động có thể bị interrupt trước khi hoàn thành; điều đó đi ngược mục tiêu graceful.

## 17. Resource cleanup và thứ tự đóng

Executor thường không đứng một mình. Task có thể dùng database connection, file, socket, queue hoặc service khác. Thứ tự đóng sai có thể làm task thất bại ngay cả khi executor được dừng “đúng” về mặt thread lifecycle.

Một chuỗi thường hợp lý:

1. Ngừng **nhận yêu cầu mới** tại đầu vào của hệ thống.
2. Đảm bảo không còn producer mới nộp task vào executor.
3. Yêu cầu executor `shutdown()`.
4. Đợi graceful trong thời hạn.
5. Nếu cần, gọi `shutdownNow()` và đợi lại.
6. Sau khi worker ngừng dùng resource, đóng các resource liên quan.
7. Nếu một worker không dừng được, xử lý trạng thái không chắc chắn thay vì đóng bừa mọi thứ rồi tuyên bố đã hoàn tất.

Ví dụ, **đóng database connection pool trước khi task cuối kết thúc** có thể khiến task đang commit thất bại. Ngược lại, nếu task đang kẹt trong I/O, việc đóng resource có thể là một phần cần thiết của quy trình hủy — nhưng phải căn cứ vào hợp đồng của resource đó.

Không có thứ tự đóng duy nhất cho mọi hệ thống. Điều quan trọng là biết dependency và quyền sở hữu tài nguyên.

## 18. ScheduledExecutorService và các implementation đặc thù

`ScheduledExecutorService` mở rộng `ExecutorService` nên cũng có `shutdown()`, `shutdownNow()` và `awaitTermination()`. Nhưng task có lịch chạy trì hoãn hoặc lặp lại có thể chịu **chính sách shutdown riêng của implementation**.

Với `ScheduledThreadPoolExecutor`, có các lựa chọn như chính sách tiếp tục thực thi delayed tasks đã tồn tại hoặc không tiếp tục periodic tasks sau shutdown. Đừng giả định toàn bộ delayed/periodic task luôn có cùng hành vi như một queue FIFO của `ThreadPoolExecutor` thông thường.

Khi dùng scheduled executor, hãy xem rõ:

- Task đã lên lịch nhưng chưa đến hạn có được chạy sau `shutdown()` không?
- Periodic task có tiếp tục không?
- Future tương ứng sẽ ở trạng thái gì khi shutdown?
- Task chưa chạy được trả về dưới dạng nào khi `shutdownNow()`?

Lời giải tổng quát vẫn là **đọc hợp đồng của implementation và kiểm tra cancellation/termination theo yêu cầu**. `shutdown()` không có nghĩa mọi loại công việc đều lập tức ngừng.

## 19. Các lỗi thường gặp

### 19.1. Chỉ gọi `shutdown()` rồi nghĩ mọi worker đã dừng

Sai. `shutdown()` không chờ. Dùng `awaitTermination` nếu cần xác nhận vòng đời đã kết thúc.

### 19.2. Gọi `awaitTermination()` mà không shutdown

Nếu không có thread khác gọi shutdown, executor có thể tiếp tục hoạt động và lời gọi chỉ hết timeout. `awaitTermination` không chủ động ngừng nhận công việc.

### 19.3. Nghĩ `shutdownNow()` ép mọi task chết ngay

Sai. Nó thường gửi interrupt; task không hợp tác vẫn có thể chạy. Có thể phải xử lý I/O, interrupt và cleanup theo hợp đồng riêng.

### 19.4. Bỏ qua danh sách pending và làm mất công việc quan trọng

`shutdownNow()` trả các task chưa bắt đầu theo hợp đồng; phải quyết định discard/retry/persist, không mặc nhiên giả định chúng đã hoàn thành.

### 19.5. Gửi shutdownNow rồi không đợi hoặc không kiểm tra hậu quả

Có thể còn task đang cleanup và resource chưa được trả. Khi cần xác nhận, đợi termination có giới hạn và báo cáo nếu vẫn chưa xong.

### 19.6. Nuốt `InterruptedException` trong worker

Worker bị ngắt ở `sleep` hoặc `take` rồi catch rỗng và tiếp tục vòng lặp có thể làm `shutdownNow()` không có hiệu lực. Propagate hoặc khôi phục cờ và dừng tại điểm an toàn.

### 19.7. Chỉ kiểm tra `isShutdown()` trước khi submit

Có race: executor có thể shutdown giữa kiểm tra và submit. Cần xử lý rejection.

### 19.8. Cho rằng `isTerminated()` chứng minh mọi tác vụ thành công

Task có thể kết thúc bằng exception hoặc cancellation. Kiểm tra `Future`/outcome nghiệp vụ riêng.

### 19.9. Gọi shutdown bên trong một task rồi tự đợi executor terminated

Nếu chính task đang chạy đợi executor terminated, còn executor phải chờ task đó kết thúc mới terminated, có thể tạo **self-wait/deadlock** hoặc timeout. Việc shutdown và chờ nên do một coordinator bên ngoài vòng công việc cần kết thúc điều phối.

### 19.10. Đóng tài nguyên trước khi worker hoàn tất

Các task đã được nộp nhưng đang chạy có thể còn sử dụng resource. Thiết kế thứ tự đóng dựa trên dependency, không chỉ dựa trên thứ tự dòng code thuận tiện.

### 19.11. Dùng `Thread.sleep` để đoán task đã kết thúc

Lịch chạy thread không xác định. Sử dụng `Future.get`, `CountDownLatch`, `awaitTermination`, hoặc tín hiệu hoàn tất được thiết kế cho mục tiêu đó.

## 20. Câu hỏi tự kiểm tra

**Câu 1.** Đã gọi `shutdown()` thì một task đang chạy có bị interrupt ngay không?

**Trả lời:** Không. `shutdown()` yêu cầu đóng có trật tự; task đã nhận vào được phép tiếp tục.

**Câu 2.** `shutdown()` có đợi tất cả worker kết thúc không?

**Trả lời:** Không. Nó trả về sau khi khởi tạo shutdown. Dùng `awaitTermination` hoặc một cơ chế chờ kết quả phù hợp.

**Câu 3.** `awaitTermination(2, SECONDS)` trả `false` nghĩa là gì?

**Trả lời:** Executor chưa terminated trong thời hạn hai giây. Không suy ra mọi task thất bại hoặc executor đã dừng.

**Câu 4.** `shutdownNow()` trả `List<Runnable>` chứa các task đang chạy hay pending?

**Trả lời:** Các task đang chờ chưa được thực thi theo hợp đồng; task đang chạy được yêu cầu dừng best-effort chứ không phải tất cả nằm trong danh sách.

**Câu 5.** Task CPU-bound không đọc interrupt có chắc chắn dừng khi `shutdownNow()` không?

**Trả lời:** Không. Interrupt là tín hiệu hợp tác; task có thể tiếp tục chạy.

**Câu 6.** Khi một task bị hủy qua `Future.cancel(true)`, có thể dùng `future.get()` để chờ chắc chắn cleanup xong không?

**Trả lời:** Không. Future bị hủy có thể ném `CancellationException` trong khi code cleanup còn đang chạy. Cần cơ chế xác nhận cleanup riêng nếu bắt buộc.

**Câu 7.** Executor đã `isShutdown()==true` nhưng `isTerminated()==false` có mâu thuẫn không?

**Trả lời:** Không. Nó đã bắt đầu quy trình đóng nhưng còn task hoặc worker chưa kết thúc.

**Câu 8.** Tại sao chiến lược hai giai đoạn gọi `shutdown()` trước và chỉ dùng `shutdownNow()` nếu chờ quá lâu?

**Trả lời:** Để ưu tiên hoàn tất task đã nhận, nhưng vẫn có fallback yêu cầu hủy nếu vượt thời hạn graceful.

**Câu 9.** Hai producer cạnh tranh: thread A kiểm tra `!executor.isShutdown()`, thread B shutdown, thread A submit. Điều gì có thể xảy ra?

**Trả lời:** Submit có thể bị từ chối. Kiểm tra và submit không phải thao tác nguyên tử; xử lý rejection.

**Câu 10.** Nếu cần giữ các job chưa chạy cho lần khởi động sau, `shutdownNow()` và `List<Runnable>` đã đủ chưa?

**Trả lời:** Không. List tồn tại trong bộ nhớ của process, có thể chứa wrapper; cần xác định danh tính, trạng thái, idempotency và lưu bền vững nếu cần phục hồi qua crash.

**Câu 11.** Một task đã bắt đầu ghi hai trường quan hệ với nhau rồi nhận interrupt. Có được dừng giữa chừng không?

**Trả lời:** Không nếu làm vậy khiến invariant sai. Cần commit/rollback ở điểm an toàn trước khi thoát và giải phóng resource.

## 21. Tổng kết

**`shutdown()` = ngừng nhận mới, để các tác vụ đã nhận hoàn thành. `shutdownNow()` = cố ngắt tác vụ đang chạy, không tiếp tục xử lý pending và trả về danh sách pending. `awaitTermination()` = chờ executor thật sự kết thúc hoặc hết thời hạn; bản thân nó không hủy hay shutdown.**

Một ứng dụng Java cần lifecycle rõ ràng thường dùng **shutdown hai giai đoạn**: graceful trước, đợi có hạn, fallback hủy bằng interrupt nếu cần, rồi đợi lần nữa và báo cáo khi chưa terminated. Nhưng kết thúc worker **không đồng nghĩa** mọi task thành công, và cancel **không đồng nghĩa** cleanup đã xong. Luôn xem xét outcome qua Future hoặc giao thức nghiệp vụ, tính nhất quán khi hủy, xử lý pending tasks, thời hạn shutdown và thứ tự đóng các resource phụ thuộc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-010">Đảm bảo finally đóng tài nguyên khi dừng tác vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Đảm bảo finally đóng tài nguyên khi dừng tác vụ trong Java

Một tác vụ Java có thể kết thúc bình thường, ném exception, nhận yêu cầu hủy, bị interrupt giữa lúc chờ hoặc bị timeout ở thread gọi. Trong mọi trường hợp **tác vụ còn có cơ hội thực thi cleanup**, programmer cần đóng các tài nguyên mà nó sở hữu: stream, socket, file handle, lock, transaction/session tự quản, worker phụ và các đối tượng có vòng đời hữu hạn. Công cụ căn bản là khối **`try/finally`** và **`try-with-resources`**.

Tuy nhiên, **`Future.cancel(true)` không phải thao tác giết thread**, và `finally` **không hứa sẽ chạy trong mọi kiểu sự cố** như process bị kill, JVM halt, máy mất điện hoặc thread bị mắc kẹt không thể tiến tới điểm thoát. Muốn cleanup đáng tin cậy, phải thiết kế **cancellation có hợp tác**, điểm chờ có khả năng phản hồi, quyền sở hữu tài nguyên rõ ràng, giới hạn thời gian, báo lỗi và kiểm thử các đường kết thúc.

Bài này nói về **Java concurrency nói chung**, sử dụng các API trong JDK và Java 17+. Các cơ chế concurrency riêng của Spring được dành cho chương khác.

## 1. Ba việc khác nhau: yêu cầu hủy, dừng tác vụ, đóng tài nguyên

| Khái niệm | Định nghĩa | Ví dụ |
| --- | --- | --- |
| **Cancellation request** | Một bên đề nghị tác vụ dừng | `future.cancel(true)`, `thread.interrupt()` |
| **Task termination** | Mã tác vụ thực sự không còn chạy | Method `run()`/`call()` đã return hoặc ném exception |
| **Resource cleanup** | Thả resource mà tác vụ sở hữu | `close()`, `unlock()`, đóng socket, thu hồi handle |
| **Executor termination** | Executor không còn task chạy và hoàn tất vòng đời | `awaitTermination(...)` trả true |

Ba sự kiện **không đồng nhất**. `cancel(true)` có thể làm `Future` chuyển sang trạng thái cancelled trước khi worker hoàn thành cleanup. Một task không phản hồi interruption vẫn có thể chạy. `shutdownNow()` cũng chỉ **cố** interrupt tasks đang chạy và trả danh sách tasks chưa được bắt đầu; nó không bảo đảm mọi resource đã đóng.

Mục tiêu thiết kế là: sau khi request hủy được gửi, worker **nhận tín hiệu**, rời khỏi vòng nghiệp vụ, đi qua `finally` hoặc `try-with-resources`, đóng tài nguyên rồi thông báo **đã hoàn tất cleanup** cho bên điều phối.

### Quy tắc phân quyền sở hữu

**Ai sở hữu resource thì chịu trách nhiệm đóng resource đúng lúc.** Nếu worker mở file stream riêng trong method, thường worker phải đóng. Nếu worker chỉ **mượn** một shared client/queue do ứng dụng quản lý, worker **không** được tự ý đóng khi bị hủy, vì các task khác còn sử dụng nó.

## 2. `try/finally`: cleanup khi rời vùng bảo vệ

Mẫu cơ bản:

```java
Resource resource = acquire();
try {
    use(resource);
} finally {
    release(resource);
}
```

Đây là **snippet**; `Resource`, `acquire`, `use` và `release` là các tên minh họa. Nếu `acquire()` hoàn tất thành công và chương trình đã vào `try`, khối `finally` được thực thi khi dòng điều khiển rời `try` theo những đường Java thông thường: hoàn thành, `return`, exception, hoặc `InterruptedException` được ném ra từ một lời gọi blocking.

**Không viết theo kiểu sau:**

```java
Resource resource = acquire();
use(resource);
release(resource); // Bị bỏ qua nếu use() ném exception.
```

Trong code có cancellation, khả năng gặp exception thường tăng vì `sleep`/`wait`/`join`/`BlockingQueue.take` có thể ném `InterruptedException`. Nếu đặt `close` ở cuối code mà không có `finally`, đường hủy có thể bỏ qua cleanup.

### Nhưng `finally` không phải phép màu

`finally` không được bảo đảm hoàn thành khi:

- JVM/process bị kill cứng, `Runtime.halt`, crash, mất điện.
- Code bị mắc kẹt mãi bên trong vùng `try` vì vòng lặp không có điểm thoát hoặc API chặn vô hạn không phản hồi interrupt.
- Thread bị ngừng thô bạo bằng cơ chế không an toàn, hoặc ứng dụng không còn cơ hội chạy mã cleanup.
- Chính `close()` bị treo, hỏng hoặc ném exception; mã cleanup tiếp theo có thể không chạy nếu không tổ chức phù hợp.

**Phạm vi “đảm bảo” thực tế:** khi **luồng điều khiển Java trở về qua stack bình thường** và JVM còn thực thi, `finally` được chạy theo semantics ngôn ngữ. Để cleanup trước một deadline hoặc trước khi process kết thúc, cần thêm hợp đồng/điều phối lifecycle ngoài `finally`.

## 3. `try-with-resources` là lựa chọn mặc định cho `AutoCloseable`

Java `try-with-resources` tự gọi `close()` cho resource khai báo trong header khi rời khối, kể cả khi body ném exception:

```java
try (InputStream input = Files.newInputStream(path)) {
    process(input);
}
```

Đây là snippet, cần import `java.io.InputStream` và `java.nio.file.Files`, `path` là một `Path` hợp lệ, `process` là method của chương trình. `InputStream` implements `AutoCloseable`. `try-with-resources` giúp tránh quên close và có xử lý **suppressed exceptions** khi cả body lẫn close đều lỗi.

Một số quy tắc:

- Resource cần implements `AutoCloseable` (hoặc `Closeable`).
- Các resource trong header được đóng theo **thứ tự ngược** lúc khai báo.
- Nếu khai báo resource thứ hai bị lỗi, các resource đã tạo trước đó vẫn được đóng theo semantics `try-with-resources`.
- `close()` vẫn có thể ném exception hoặc chặn; ngôn ngữ không cam kết close luôn thành công hay đáp ứng timeout.
- Tạo resource **trước** rồi dùng try-with-resources với biến final/effectively final có thể đúng cú pháp từ Java 9, nhưng cần xác định rõ **ai thực sự sở hữu** nó. Đừng tự đóng shared resource chỉ vì biến có thể đưa vào header.

**Ưu tiên `try-with-resources`** khi resource có phạm vi lexical rõ: file stream, reader/writer, socket do task tạo, tài nguyên tùy chỉnh implements `AutoCloseable`.

## 4. `InterruptedException`: tín hiệu hủy, không phải yêu cầu nuốt lỗi

Nhiều blocking methods của JDK phản hồi interrupt bằng `InterruptedException`, ví dụ `Thread.sleep()`, `Object.wait()`, `Thread.join()`, `BlockingQueue.take()` và `CountDownLatch.await()`.

Nếu một worker đã có cơ hội dừng, mẫu phù hợp:

```java
try {
    while (true) {
        WorkItem item = queue.take();
        process(item);
    }
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // Thoát method; finally/try-with-resources sẽ cleanup.
} finally {
    cleanupOwnedResources();
}
```

Đây là **snippet**, với `WorkItem`, `queue`, `process` và `cleanupOwnedResources` được định nghĩa ở nơi khác. Vì `InterruptedException` thường **xóa interrupt flag khi được ném**, ta gọi `Thread.currentThread().interrupt()` nếu bắt exception mà **không ném tiếp**, để caller/lifecycle code còn biết thread đã bị yêu cầu ngắt. Sau đó phải **rời vòng công việc** để cleanup, không tiếp tục như thể không có tín hiệu.

**Nếu method có thể khai báo `throws InterruptedException`**, thường đơn giản nhất là **cho exception lan ra** và đặt cleanup trong `finally`/`try-with-resources`. Không cần restore flag ngay trước khi rethrow nếu policy là để caller xử lý interruption; điều quan trọng là **không nuốt tín hiệu**.

### Phản ví dụ: bắt rồi chạy tiếp

```java
while (true) {
    try {
        doBlockingWork();
    } catch (InterruptedException ignored) {
        // BUG: nuốt tín hiệu, vòng lặp tiếp tục.
    }
}
```

Đây là snippet sai. Một `Future.cancel(true)` có thể được gửi, nhưng worker vẫn chạy mãi, làm `finally` bên ngoài vòng không có cơ hội thực thi. Nếu tác vụ cần hỗ trợ hủy, hãy thoát vòng hoặc chuyển trạng thái một cách có chủ đích.

## 5. Ví dụ Java 17 chạy được: interrupt dẫn tới đóng resource

Chương trình dùng một resource `AutoCloseable` nhỏ, một thread đợi trên `CountDownLatch` chưa bao giờ được mở và main gửi interrupt sau khi thread đã bắt đầu. Worker bắt `InterruptedException`, restore interrupt flag và tự thoát; Java gọi `close()` khi rời try-with-resources. Main chỉ in sau khi `join` hoàn tất nên stdout xác định.

Tạo tệp **`FinallyOnInterrupt.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FinallyOnInterrupt {
    static final class TrackedResource implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void close() {
            closed.set(true);
        }

        boolean isClosed() {
            return closed.get();
        }
    }

    public static void main(String[] args) throws Exception {
        TrackedResource resource = new TrackedResource();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch neverOpened = new CountDownLatch(1);
        AtomicBoolean interruptRestored = new AtomicBoolean();

        Thread worker = new Thread(() -> {
            try (resource) {
                started.countDown();
                neverOpened.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                interruptRestored.set(
                        Thread.currentThread().isInterrupted());
            }
        }, "worker");

        worker.start();
        if (!started.await(5, TimeUnit.SECONDS)) {
            worker.interrupt();
            throw new IllegalStateException("Worker not started");
        }

        worker.interrupt();
        worker.join(5_000);
        if (worker.isAlive()) {
            throw new IllegalStateException("Worker did not finish");
        }

        System.out.println("resourceClosed=" + resource.isClosed());
        System.out.println("interruptRestored=" + interruptRestored.get());
    }
}
```

Chạy bằng JDK 17+:

```bash
javac FinallyOnInterrupt.java
java FinallyOnInterrupt
```

Stdout **khi thread chạy và kết thúc trong deadline**:

```text
resourceClosed=true
interruptRestored=true
```

**Giải thích từng bước:**

1. `resource` được gắn với try-with-resources của worker; object này theo contract bài học thuộc quyền sở hữu của worker, dù main giữ reference chỉ để kiểm tra sau `join`.
2. Worker gọi `started.countDown()` rồi chờ ở `neverOpened.await()`.
3. Main chỉ interrupt sau khi biết worker đã đi vào phần `try`. Nếu interrupt đến trước khi `await` thực sự chờ, `await` vẫn phản hồi bằng exception khi kiểm tra interrupt; nếu interrupt đến trong lúc chờ, nó được đánh thức.
4. Worker bắt `InterruptedException`, khôi phục cờ interrupt và ghi nhận kết quả.
5. **Try-with-resources đóng resource khi thoát vùng `try`, trước khi vào nhánh catch của chính `try`.**
6. `worker.join()` bảo đảm main đọc kết quả sau khi worker đã hoàn thành.

Tác vụ trên không dùng `Thread.stop()`; nó dừng bằng **cooperative interruption**, cho phép cleanup chạy trên đường Java thông thường.

**Giới hạn:** ví dụ chỉ chứng minh resource mẫu được đóng trên đường hủy này. Nó không chứng minh rằng mọi loại I/O hay native call đều phản hồi interrupt; khi thay `neverOpened.await()` bằng I/O khác, phải đọc đúng contract.

## 6. `finally` với mutex: luôn nhả khóa đã lấy thành công

`ReentrantLock` không tự được JVM nhả theo scope. Vì vậy sau khi `lock()` hoặc `lockInterruptibly()` thành công, phải đặt `unlock()` trong `finally`:

```java
lock.lock();
try {
    updateState();
} finally {
    lock.unlock();
}
```

Đây là snippet; `lock` là `ReentrantLock` hoặc `Lock` và `updateState` là logic được bảo vệ.

### Chú ý vị trí của `lockInterruptibly()`

```java
lock.lockInterruptibly(); // Có thể throw trước khi lấy khóa.
try {
    updateState();
} finally {
    lock.unlock(); // Chỉ chạy khi acquisition đã thành công.
}
```

Snippet này nằm trong method `throws InterruptedException`. **Đừng** đặt `unlock()` vào finally chạy ngay cả khi `lockInterruptibly()` ném exception trước khi lấy khóa. Với `ReentrantLock`, unlock khi không phải owner ném `IllegalMonitorStateException` và có thể che lỗi gốc.

Với intrinsic monitor, `synchronized(lock)` tự nhả monitor khi thoát block, kể cả exception. Nhưng **nhả mutex không đồng nghĩa đóng file/socket** đã được tạo bên trong; resource có vòng đời riêng vẫn cần try-with-resources hoặc finally.

### `Semaphore`, permit và ownership

Nếu đã `semaphore.acquire()` thành công, cần `release()` trong finally. Nhưng nếu `acquire()` bị interrupt **trước khi thành công**, không được release như thể đã sở hữu permit, nếu không sẽ làm tăng số permit sai:

```java
semaphore.acquire();
try {
    useLimitedResource();
} finally {
    semaphore.release();
}
```

Đây là snippet trong method có `throws InterruptedException`. `semaphore`/`useLimitedResource` được định nghĩa ngoài đoạn. Đặc biệt không dùng một finally bao trùm cả acquisition thất bại để luôn release vô điều kiện.

## 7. Hủy `Future`: cancelled không đồng nghĩa cleanup đã xong

`Future.cancel(true)` **yêu cầu** hủy. Với tác vụ đang chạy, implementation như `FutureTask` có thể gọi interrupt lên thread đang chạy. Nhưng:

- Nếu task chưa bắt đầu và cancellation được chấp nhận, task có thể **không chạy**, nên `finally` bên trong task cũng **không chạy**. Tài nguyên cấp phát bên ngoài task vẫn cần owner ngoài xử lý.
- Nếu task đang chạy, cờ cancelled của `Future` có thể được công bố **trước khi code worker thoát khỏi finally**. Đừng dùng `future.isDone()` hay `isCancelled()` làm bằng chứng rằng close hoàn tất.
- Nếu worker bỏ qua interrupt hoặc bị chặn trong API không hỗ trợ hủy, cancellation có thể không làm worker dừng sớm.
- `Future.get()` trên Future đã cancelled thường ném `CancellationException`; nó **không chờ** resource cleanup theo cách bạn có thể mặc định. Cần handshake riêng hoặc chờ executor thực sự terminate.
- `cancel(false)` không gửi interrupt tới task đang chạy; việc dừng còn tùy task và trạng thái scheduling.
- Khi `cancel(true)` trả false, task có thể đã hoàn tất hoặc cancellation không được chấp nhận; phải phân tích kết quả theo contract, không tự đoán.

### Mẫu điều phối hai giai đoạn

**Giai đoạn 1:** gửi request hủy (cancel/interrupt). **Giai đoạn 2:** chờ tín hiệu **worker rời khỏi finally** hoặc chờ executor termination, rồi mới tuyên bố các resource thuộc worker đã đóng.

Nếu chỉ đợi `future.get()` và bắt `CancellationException`, bạn đang quan sát **trạng thái Future**, không nhất thiết là **trạng thái cleanup hoàn tất**.

## 8. Java 17: phân biệt `Future.cancel(true)` và worker đã cleanup

Ví dụ sau có `CountDownLatch finished` được đếm xuống trong **finally ngoài cùng**, sau khi try-with-resources đã đóng resource. Main chỉ in khi đã thấy `finished`, nên không chạy đua với cleanup.

Tạo tệp **`FutureCancelCleanup.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FutureCancelCleanup {
    static final class TrackedResource implements AutoCloseable {
        private final AtomicBoolean closed;

        TrackedResource(AtomicBoolean closed) {
            this.closed = closed;
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    public static void main(String[] args) throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        CountDownLatch neverOpened = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            Future<?> future = pool.submit(() -> {
                try {
                    try (TrackedResource resource =
                                 new TrackedResource(closed)) {
                        started.countDown();
                        neverOpened.await();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Task not started");
            }

            boolean accepted = future.cancel(true);
            if (!finished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Cleanup not completed");
            }

            System.out.println("cancelAccepted=" + accepted);
            System.out.println("futureCancelled=" + future.isCancelled());
            System.out.println("resourceClosed=" + closed.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac FutureCancelCleanup.java
java FutureCancelCleanup
```

Stdout khi chương trình hoàn thành bình thường:

```text
cancelAccepted=true
futureCancelled=true
resourceClosed=true
```

**Vì sao test không dựa vào timing may rủi?**

- Worker được submit và chạy trong executor một thread.
- Main chờ `started`, chứng minh worker đã tạo resource và đến checkpoint trong task.
- Worker sau đó chờ `neverOpened.await()`; interruption làm `await` ném `InterruptedException`, kể cả khi interrupt đến ngay trước khi nó thực sự đợi.
- `future.cancel(true)` yêu cầu interrupt worker. Khi worker thoát, try-with-resources đóng resource trước, rồi finally ngoài cùng đếm `finished`.
- Main **chờ `finished` trước khi in**, thay vì thấy `future.isCancelled()` rồi suy luận close đã kết thúc.

**Có một giới hạn đặc biệt:** nếu worker lỗi trước khi đi vào try-with-resources hoặc bị hủy trước khi chạy, không thể dựa vào `finished` trong body sẽ chạy. Trong chương trình này `started` đã được xác nhận nên ta biết task đang hoạt động và đã cấp phát resource. Trong hệ thống thực tế, thiết kế lifecycle phải quản lý cả trạng thái **queued / never started / running / completed**.

**Timeout không cưỡng chế close:** nếu `finished.await(5s)` trả false, main báo lỗi; worker có thể vẫn đang chặn trong một operation không phản hồi interrupt. `shutdownNow()` cố gửi interrupt nhưng không thể bảo đảm giải phóng I/O native hoặc resource ngoại vi.

## 9. `ExecutorService.shutdown()` và `shutdownNow()` không thay cleanup của task

Một executor được shutdown đúng cách giúp không bỏ mặc workers:

```java
pool.shutdown();
try {
    if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
        pool.shutdownNow();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            reportStuckWorkers();
        }
    }
} catch (InterruptedException e) {
    pool.shutdownNow();
    Thread.currentThread().interrupt();
}
```

Đây là **snippet**, `pool` là `ExecutorService` và `reportStuckWorkers()` là method ứng dụng. Ý nghĩa:

1. `shutdown()` không nhận tasks mới, nhưng cho tasks đã nhận cơ hội hoàn thành bình thường.
2. `awaitTermination` đợi một thời lượng giới hạn để executor thực sự kết thúc.
3. Nếu quá thời hạn, `shutdownNow()` cố interrupt tasks đang chạy và trả danh sách tasks chưa bắt đầu.
4. Đợi lần hai để biết worker có thật sự thoát hay không.
5. Nếu chính thread điều phối bị interrupt, nó cố yêu cầu hủy, **khôi phục cờ interrupt** rồi kết thúc đường chờ.

**Điều bắt buộc phải phân biệt:** `shutdownNow()` không đóng hộ mọi resource của task và cũng không cam kết tất cả thread dừng ngay. Nếu có tasks chưa bao giờ chạy, `finally` trong task không được thực thi; những resource chuẩn bị từ trước phải có một owner khác đóng. Với resource chia sẻ, lifetime thường gắn với application/executor owner, không gắn từng task.

## 10. Tài nguyên được mở ở đâu quyết định ai phải đóng

### 10.1. Task mở resource riêng

```java
void runTask(Path path) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
        readFully(in);
    }
}
```

Snippet cần import `IOException`, `InputStream`, `Files`, `Path` và method `readFully`. Nếu worker tạo input stream cho riêng job đó thì nên đóng ngay trong phạm vi job.

### 10.2. Resource được ứng dụng chia sẻ

```java
void runTask(SharedClient client) {
    client.sendOneRequest();
    // Không đóng client; owner ứng dụng quản lý vòng đời.
}
```

`SharedClient` là type minh họa, không phải class có sẵn của JDK. Task **không sở hữu** resource, nên cancellation của một job không được làm gián đoạn các job khác bằng cách đóng shared client.

### 10.3. Resource được tạo trước khi submit

```java
Resource resource = openResource();
Future<?> f = pool.submit(() -> use(resource));
```

Hai dòng này là snippet có tên minh họa. Nếu `f.cancel(true)` xảy ra **trước khi task bắt đầu**, lambda có thể không chạy, và `finally` đặt **bên trong lambda** không bao giờ có cơ hội đóng `resource`. Hãy thiết kế **ownership transfer rõ ràng**:

- Tốt nhất: mở resource **bên trong task** và dùng try-with-resources, nếu không cần mở trước.
- Nếu buộc mở trước: người tạo giữ trách nhiệm đóng khi task không được nhận/chưa bắt đầu, hoặc dùng một owner object quản lý trạng thái, việc bàn giao quyền sở hữu và đóng đúng một lần.
- Khi nhiều actor có thể cùng cố close, cần chính sách close idempotent/ownership protocol; đừng dựa vào may mắn để tránh double-close.

### 10.4. Task có nhiều resource

```java
try (Reader reader = Files.newBufferedReader(source);
     Writer writer = Files.newBufferedWriter(target)) {
    copy(reader, writer);
}
```

Đây là snippet, cần import `Reader`, `Writer`, `Files`, `Path` và có `source`, `target`, `copy`. Resource được đóng **ngược thứ tự khai báo**: `writer` rồi `reader`. Nếu một close ném lỗi, Java vẫn cố close các resource còn lại theo semantics try-with-resources.

**Không mở resource đắt hoặc có side effect trong constructor rồi giao cho worker mà không nghĩ tới task bị từ chối/hủy trước khi chạy.**

## 11. `close()` có thể thất bại: đừng che exception gốc

Một lỗi tinh vi xảy ra khi body ném exception và `finally` cũng ném exception trong lúc close:

```java
try {
    doWork();
} finally {
    resource.close(); // Nếu close ném, exception gốc có thể bị che.
}
```

Đây là snippet, giả định `doWork`/`resource` đã được định nghĩa. Với `try-with-resources`, nếu **body** ném exception và `close` cũng ném exception, Java giữ exception chính của body và thêm lỗi close vào danh sách **suppressed exceptions**.

### Java 17: minh họa suppressed exception

Tạo **`SuppressedCleanupDemo.java`**:

```java
import java.io.IOException;

public class SuppressedCleanupDemo {
    static final class FailingResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    public static void main(String[] args) {
        try (FailingResource r = new FailingResource()) {
            throw new IOException("work failed");
        } catch (IOException e) {
            System.out.println("primary=" + e.getMessage());
            System.out.println(
                    "suppressed=" + e.getSuppressed()[0].getMessage());
        }
    }
}
```

```bash
javac SuppressedCleanupDemo.java
java SuppressedCleanupDemo
```

Stdout chính xác:

```text
primary=work failed
suppressed=close failed
```

**Bài học:** nếu phần việc thất bại và cả thao tác đóng cũng thất bại, cả hai lỗi đều quan trọng. Khi logging, ghi nhận suppressed exceptions (`Throwable.getSuppressed()`) thay vì chỉ in message exception chính.

Đối với cleanup nhiều bước thủ công, nếu bước đầu `close` ném exception, các bước sau có thể bị bỏ qua. Cần thiết kế `finally` lồng, try-with-resources, hoặc thu thập các lỗi close sao cho **mọi resource có cơ hội được giải phóng**. Không ném `return` trong finally vì nó có thể **che exception hoặc kết quả** của phần thân.

## 12. Đóng tài nguyên không phải lúc nào cũng làm I/O dừng ngay

Một số blocking operations phản hồi interruption (ví dụ `BlockingQueue.take`, `CountDownLatch.await`). Nhưng không được suy diễn rằng **mọi blocking I/O** cũng phản hồi `Thread.interrupt()` theo cùng cách.

- **NIO interruptible channels**: hành vi với interrupt/close được mô tả bởi contract của loại channel; một thao tác đang chặn có thể bị kết thúc bằng exception phù hợp khi channel bị interrupt/đóng.
- **`java.net.Socket`/stream I/O**: behavior phụ thuộc API, runtime, timeout và platform. Nên đọc contract và đặt `Socket.setSoTimeout(...)` ở những nơi có ý nghĩa; `Thread.interrupt` không phải nút universal để chấm dứt mọi loại socket read.
- **Một lời gọi native hoặc thư viện ngoài** có thể không kiểm tra interrupt và tiếp tục chặn.
- **`AutoCloseable.close()` có thể chặn** trong quá trình flush, handshake, ghi data hoặc chờ trạng thái khác; không mặc nhiên có deadline.
- **Shutdown hook** cũng không phải nơi bảo đảm cleanup nếu process bị kill cứng hoặc hook bị treo; tránh dựa vào hook làm giao thức duy nhất bảo vệ dữ liệu quan trọng.

Nếu cần hủy I/O đáng tin, thiết kế **timeout ở đúng API**, khả năng đóng channel/socket từ bên quản lý phù hợp với contract, hoặc dùng API có cancellation rõ ràng. Không dùng `Thread.stop()` để buộc thoát — dừng bất ngờ có thể phá invariants và gây corruption.

## 13. Resource cleanup có thể tự cần interrupt policy

Sau khi worker bị interrupt, thread vẫn có interrupt flag bật (nếu đã restore). Nếu `close()` của resource lại dùng một blocking operation phản hồi interrupt, **cleanup có thể bị cắt ngắn**.

Có ba nguyên tắc cần cân nhắc:

1. **Ưu tiên cleanup không blocking và idempotent** khi thiết kế resource wrapper tự quản.
2. Nếu cleanup buộc chặn, định nghĩa **deadline**, fallback, logging và nghĩa vụ của owner; không chỉ gọi close rồi hy vọng hoàn tất.
3. Nếu một implementation cần **tạm hoãn interruption để cleanup**, chỉ điều khiển interrupt flag một cách có chủ đích và **khôi phục trạng thái** sau đó; không xóa interrupt flag vô tình.

Không có một pattern duy nhất đúng cho mọi `close()`. Với file stream đóng nhanh và không ném checked exception, try-with-resources rất rõ. Với network connection có shutdown handshake phức tạp, phải đọc contract và thiết kế vòng đời ngoài task; thậm chí lựa chọn “abort ngay” thay vì “flush/commit mọi thứ” có thể là một quyết định nghiệp vụ.

### Vì sao không swallow exception trong close?

```java
finally {
    try {
        resource.close();
    } catch (Exception ignored) {
        // Sai nếu cần biết resource đóng thất bại.
    }
}
```

Đoạn snippet này bỏ hết thông tin lỗi cleanup. Nếu lỗi close không thể propagate vì cần giữ exception nghiệp vụ chính, ít nhất phải **ghi nhận/attach suppressed exception** hoặc báo trạng thái cleanup không thành công. Tránh log spam khi shutdown nhưng cũng không làm hệ thống báo “đã đóng” khi resource vẫn còn.

## 14. Đảm bảo cleanup cả khi task lỗi không do interrupt

Một worker không chỉ chết vì `InterruptedException`. Có thể ném `IOException`, `RuntimeException`, timeout riêng, validation exception, hoặc trả sớm do điều kiện nghiệp vụ. Vì thế **đặt cleanup tại vùng có cùng ownership** là nguyên tắc vững hơn việc thêm `close()` vào từng nhánh `catch`.

Ví dụ tốt:

```java
try (Resource r = open()) {
    if (skipThisTask()) {
        return;
    }
    doWork(r);
}
```

Đây là snippet; resource được đóng ngay cả trên đường `return` bình thường. Nếu phải đóng nhiều resource thủ công mà chúng không implements `AutoCloseable`, dùng `try/finally` quanh từng acquisition thành công, và sắp xếp thứ tự giải phóng rõ ràng.

**Tránh gọi một callback không tin cậy khi giữ mutex hoặc đang close resource** nếu callback có thể block, reenter, lấy khóa theo thứ tự ngược hoặc ném exception làm ngắt cleanup. Khi có thể, chụp immutable event/snapshot trước rồi callback ngoài critical section theo một giao thức thông báo riêng.

## 15. Cấp phát và đóng tài nguyên đúng thứ tự

Một chu trình an toàn thường là:

1. **Acquire**: lấy resource; nếu acquisition thất bại, chỉ cleanup những phần đã lấy được thành công.
2. **Register ownership**: đảm bảo từ lúc acquisition thành công đã có một scope/owner có trách nhiệm close.
3. **Execute**: thực hiện công việc; có cancellation points, timeout và state kiểm soát.
4. **Cancel**: khi có request hủy, dừng việc nhận thêm công việc và giúp task thoát khỏi các điểm chờ hỗ trợ interrupt.
5. **Cleanup**: chạy `finally`/`try-with-resources`; xử lý lỗi close, cố giải phóng mọi resource liên quan.
6. **Signal completion**: sau khi cleanup thực sự xong, phát tín hiệu `CountDownLatch`/state completion theo happens-before phù hợp.
7. **Coordinator verifies**: chỉ báo "đã đóng" khi có bằng chứng đã hoàn tất, hoặc công khai timeout/cleanup failure.

### Lưu ý về quyền sở hữu của `ThreadLocal`

Một `ThreadLocal` có thể giữ mutable resource trên thread pool worker. Nếu mỗi task `set` nhưng không `remove` ở finally, state có thể còn tồn tại trên **thread tái sử dụng** và rò rỉ sang task khác. Mẫu:

```java
try {
    context.set(taskContext);
    doWork();
} finally {
    context.remove();
}
```

Đây là snippet, `context` là `ThreadLocal<?>` thích hợp và `taskContext` thuộc task. `remove` giải phóng binding của thread đối với value; **không tự gọi `close()` cho object value** nếu nó implements AutoCloseable. Nếu value sở hữu tài nguyên thật, phải có close riêng theo ownership.

## 16. Kiểm thử tình huống hủy: cần có chứng cứ cleanup

### Nhóm tình huống tối thiểu

| Tình huống | Điều cần kiểm tra |
| --- | --- |
| Tác vụ return bình thường | `close()` được gọi theo scope |
| Body ném exception | `close()` vẫn được gọi, lỗi gốc không bị bỏ qua |
| Blocking wait ném `InterruptedException` | Worker thoát, cleanup được thực thi |
| `future.cancel(true)` khi task đang chạy | Không coi cancelled Future là bằng chứng cleanup đã xong |
| Hủy task **trước khi bắt đầu** | Resource ngoài task có owner đóng thay |
| Close ném exception | Lỗi cleanup được báo/giữ suppressed phù hợp |
| Task không phản hồi interrupt | Coordinator timeout, không khẳng định resource đã đóng |
| Executor shutdown | Không còn task running trước khi đóng resource dùng chung |
| Nhiều resource, một close lỗi | Các resource còn lại vẫn có cơ hội đóng |
| Shared resource được task mượn | Hủy một task không đóng resource của cả ứng dụng |

### Dùng handshake thay vì `Thread.sleep`

Để test chắc chắn hủy **khi worker đã bắt đầu**, dùng `CountDownLatch started`; để biết đã cleanup, dùng `CountDownLatch finished` trong `finally` sau các bước close. Đừng `sleep(20)` rồi giả định worker đang chờ đúng vị trí; scheduling không có cam kết đó.

### Chỉ assert sau khi cleanup thực sự hoàn tất

Một test chỉ kiểm tra:

```java
future.cancel(true);
assert future.isCancelled();
```

là **chưa đủ** để chứng minh resource đã đóng. `assert` của Java còn có thể bị vô hiệu nếu không bật `-ea`. Nên dùng assertion của test framework hoặc explicit `AssertionError` và một sự kiện `finished`/executor termination có nghĩa đúng với đời sống resource.

## 17. Những lỗi phổ biến

| Ngộ nhận hoặc sai lầm | Hậu quả | Cách xử lý |
| --- | --- | --- |
| “`cancel(true)` giết ngay worker” | Worker có thể tiếp tục chạy | Cooperative cancellation + timeout |
| “`isCancelled()` nghĩa là close xong” | Cleanup có thể còn đang chạy | Chờ `finished` hoặc termination thực |
| “`finally` luôn chạy kể cả JVM bị kill” | Không có bảo đảm như vậy | Recovery/idempotency ngoài process |
| Bắt `InterruptedException` rồi bỏ qua | Worker không bao giờ rời vòng, finally chưa chạy | Propagate hoặc restore + exit |
| Đặt close sau body không có finally | Exception/return bỏ qua close | Try-with-resources/finally |
| Đóng shared client khi một task bị hủy | Phá các task khác | Xác định resource owner |
| Cấp phát resource trước submit, chỉ close trong task | Hủy trước start làm resource rò rỉ | Owner bên ngoài hoặc tạo trong task |
| `unlock()` khi chưa acquire thành công | Exception mới, sai lock ownership | Chỉ finally sau successful acquire |
| `semaphore.release()` khi acquire thất bại | Tăng sai số permit | Release chỉ sau successful acquire |
| `close()` ném làm che exception gốc | Mất nguyên nhân failure | Try-with-resources, suppressed exceptions |
| Nhiều close trong một finally, close đầu ném | Resource sau có thể không được đóng | TWR hoặc tách catch/lồng finally |
| Tuyên bố mọi I/O đều bị interrupt | Worker có thể vẫn block | API-specific cancel/timeout/close |
| Dùng `Thread.stop()` để ép cleanup | Nguy cơ phá invariant/corruption | Hủy hợp tác |
| Dùng `sleep` để test thời điểm cleanup | Flaky test | Latch/handshake |
| Quên `ThreadLocal.remove()` | State task rò trên thread pool | Cleanup per-task trong finally |
| Suppress hoàn toàn close errors | Báo sai rằng shutdown đã thành công | Ghi nhận và báo trạng thái |

## 18. Checklist thiết kế production

- **Tài nguyên nào task sở hữu độc quyền?** Cái nào chỉ được mượn từ owner ứng dụng?
- Resource được cấp phát **trước hay sau khi task được lên lịch**? Nếu không được chạy thì ai đóng?
- Có **try-with-resources** với `AutoCloseable`? Nếu không, mỗi successful acquisition có đúng finally không?
- Blocking operations có **hỗ trợ interrupt**? Nếu không, có API close/cancel/timeout riêng không?
- Khi bắt `InterruptedException`, code có **thoát** hoặc propagate? Có restore flag khi catch và không rethrow?
- Nếu `close()` ném exception hoặc chặn, có kế hoạch giữ lỗi gốc, log và deadline?
- Sau `future.cancel(true)`, coordinator có **đợi cleanup thực sự** trước khi giải phóng resource dùng chung?
- `ExecutorService.shutdownNow()` có được xem là **request** hủy chứ không phải completion guarantee?
- Các resource mở trước khi task bắt đầu có được owner khác quản lý khi task bị cancelled/never started?
- Nếu có nhiều resource, có đóng theo thứ tự thích hợp và xử lý lỗi của từng close không?
- Trong task có `ThreadLocal` hoặc callback/reference cần giải phóng không?
- Test đã bao phủ **normal return, exception, interrupt, cancel-before-start, cancel-while-running, close failure và shutdown timeout**?
- Nếu process crash trước cleanup, trạng thái nghiệp vụ có cơ chế **recovery/idempotency/lease/transaction** phù hợp không?

## 19. Câu hỏi tự kiểm tra

**1. `Future.cancel(true)` trả true có đảm bảo file đã đóng không?** Không. Nó cho biết cancellation được chấp nhận theo contract, không chứng minh worker đã đi qua finally.

**2. Worker catch `InterruptedException` rồi tiếp tục vòng vô hạn, finally bên ngoài có chạy không?** Chỉ khi worker thật sự rời vùng try; nuốt interrupt có thể khiến nó không thoát, nên cleanup không được thực hiện đúng lúc.

**3. Khi nào dùng try-with-resources thay cho try/finally?** Khi tài nguyên implements `AutoCloseable` và ownership có thể diễn đạt bằng scope rõ ràng; TWR còn xử lý suppressed exception khi cả body và close lỗi.

**4. Một task chưa từng được chạy có thể thực hiện finally trong task không?** Không. Resource mở trước submit phải có owner khác khi task bị hủy trước lúc bắt đầu.

**5. Có nên gọi `unlock` nếu `lockInterruptibly` bị interrupt trước khi acquire?** Không. Lúc đó caller không sở hữu lock.

**6. `shutdownNow()` có cam kết mọi worker dừng không?** Không. Nó cố interrupt task đang chạy; task không phản hồi có thể vẫn hoạt động.

**7. Có thể rely vào `System.exit`/kill để finally chạy không?** Không. Phải thiết kế recovery khi process dừng bất thường.

**8. Tại sao exception của `close()` không nên che exception của body?** Vì lỗi nghiệp vụ gốc cần được giữ; try-with-resources giữ nó làm primary và lỗi close ở suppressed list.

**9. Tại sao đóng shared client trong finally của từng task là sai?** Resource thuộc owner khác và có thể còn được sử dụng bởi các task cùng ứng dụng.

**10. Nếu interrupt flag đang bật khi cleanup dùng blocking API, có nguy cơ gì?** Cleanup có thể bị interrupt ngay hoặc thất bại sớm theo contract; cần policy cleanup và bảo toàn trạng thái hủy.

**11. Muốn test task đã đóng tài nguyên, có đủ nếu `future.isDone()` là true?** Không, nhất là với cancelled Future. Cần xác minh dấu hiệu hoàn tất cleanup hoặc executor termination thật.

**12. Vì sao `ThreadLocal.remove()` nên nằm trong finally?** Thread pool tái sử dụng thread; binding cũ có thể tồn tại qua nhiều task nếu không remove, gây rò trạng thái và tài nguyên gián tiếp.

## 20. Kết luận

**`finally` và `try-with-resources` là hàng rào cleanup trên đường thực thi Java bình thường, không phải lệnh cưỡng chế kết thúc tác vụ.** Muốn đóng tài nguyên đáng tin khi hủy, phải kết hợp:

1. **Ownership rõ ràng** (ai mở, ai đóng, tài nguyên per-task hay shared).
2. **Cooperative interruption** (tác vụ kiểm tra tín hiệu, thoát khỏi blocking points và không nuốt `InterruptedException`).
3. **Cleanup theo scope** (try-with-resources hoặc finally quanh mọi resource đã cấp phát thành công).
4. **Completion handshake** (không nhầm Future cancelled với worker đã close xong).
5. **Failure handling** (suppressed close errors, timeout, API I/O-specific cancellation và recovery sau crash).

Đặt đúng các yếu tố này sẽ khiến đường dừng tác vụ **dự đoán được, dễ kiểm thử và ít rò rỉ resource**, mà không phải dùng cơ chế dừng thread thô bạo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-011">Phân tầng tín hiệu hủy trong chuỗi xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Phân tầng tín hiệu hủy trong chuỗi xử lý Java Concurrency

## 1. Bài toán và kết quả học tập

Một request không nhất thiết được xử lý bởi một thread duy nhất. Caller có thể gửi một `Callable` vào `ExecutorService`; parent task lại tạo các task con; child gọi `BlockingQueue`, chờ I/O, chạy CPU hoặc ghi kết quả vào hệ thống khác. Khi caller timeout hoặc người dùng hủy, câu hỏi không chỉ là **“đã gọi `cancel(true)` chưa?”**, mà là **“tín hiệu hủy có đi tới đúng từng tầng và tác vụ đã thực sự ngừng/cleanup/commit theo chính sách chưa?”**

~~~text
Caller / Service shutdown
          |
          v
Coordinator (Parent Future) ---> Child A (CPU / queue)
          |                   \
          |                    +-- checkpoint / interrupt
          v
     Child B (I/O) ----------> timeout / close / remote request
          |
          v
     Publish / commit -------> state generation, idempotency
          |
          v
        Cleanup -------------> locks, permits, connections
~~~

Có hai dòng thông tin cần phân biệt:
- **Từ trên xuống:** cancellation request, deadline, interrupt, cancellation token và chính sách ngừng từng child.
- **Từ dưới lên:** kết quả, exception, trạng thái worker thực sự rời task, cleanup đã xong và nghiệp vụ đã/không commit.

Sau bài, bạn sẽ biết:
1. Phân biệt **ý định hủy**, **interrupt status**, **Future cancelled**, **deadline**, **worker exited** và **business finality**.
2. Truyền cancellation từ parent xuống các task con **mà parent sở hữu**, thay vì tưởng hủy parent tự lan ra toàn cây.
3. Dùng đúng `InterruptedException`, `Thread.currentThread().isInterrupted()`, `Future.cancel(true)`, token và timeout.
4. Xác định ranh giới mà interruption không thể tự ngắt, như một số I/O, `synchronized` monitor acquisition hay side effect ở hệ thống khác.
5. Tránh lost cancellation, orphan tasks, resource leaks, result đến muộn và commit trùng khi retry.
6. Chạy ví dụ độc lập trên **Java 17+** với stdout và giải thích vì sao kết quả là như vậy.

**Phạm vi:** Java SE và concurrency của Java nói chung. Không đưa cơ chế riêng của Spring vào bài vì phần liên quan Spring có chương riêng.

## 2. Phân biệt các tầng của việc hủy

| Tầng hoặc trạng thái | Ý nghĩa | Điều không được suy ra |
| --- | --- | --- |
| Caller requested cancellation | Người dùng/caller muốn bỏ công việc | Task đã ngừng |
| Future.cancel(true) accepted | Future đã chấp nhận chuyển sang trạng thái hủy, và implementation có thể yêu cầu interrupt runner | Mọi child đã ngừng hoặc side effect đã rollback |
| Thread interrupt status | Một thread được báo có yêu cầu interrupt | CPU loop và mọi loại I/O sẽ tự thoát |
| Cancellation token | Ý định hủy được chia sẻ tới những stage kiểm tra token | Thread block sẽ tự thức dậy |
| Deadline expired | Thời gian ngân sách đã hết | Remote server chắc chắn dừng |
| Worker exited | Callable/Runnable đã hoàn tất thực thi | Tất cả remote side effect đã bị hủy |
| Resource cleanup finished | Lock/permit/stream/connection đã được giải phóng theo giao thức | Dữ liệu nghiệp vụ đã được rollback |
| Business commit finalized | Nơi ghi kết quả quyết định trạng thái cuối | Có thể suy từ một cờ local cancelled |

**`Future.isDone() == true` bao gồm cả completed, failed hoặc cancelled.** Một Future đã cancelled có thể báo isDone ngay cả khi callable còn thực hiện cleanup. `Future.get()` sau cancellation ném `CancellationException`, nhưng không phải chứng chỉ rằng mọi nhánh trong xử lý đã dừng.

Một yêu cầu có thể đang ở chuỗi trạng thái khác nhau:

~~~text
Future:   RUNNING -> CANCELLED -> isDone = true
Worker:   RUNNING -> nhận interrupt -> cleanup -> EXITED
Remote:   REQUEST_RECEIVED -> COMMITTED (dù caller đã cancel)
Resource: ACQUIRED -> CLEANUP_PENDING -> RELEASED
~~~

Do đó hãy đo/kiểm tra từng trạng thái ở đúng tầng, không gộp tất cả thành một boolean `cancelled`.

## 3. Tầng caller: nguồn tín hiệu hủy và thời hạn

Hủy có thể xuất phát từ:
- Người dùng nhấn hủy hoặc client disconnect.
- `Future.get(timeout, unit)` hết thời gian chờ.
- Một bước downstream vượt deadline.
- Một child gặp lỗi không thể phục hồi khiến các nhánh còn lại không cần.
- Service đang shutdown, cần dừng nhận request mới và giải phóng tài nguyên.
- Đã có một kết quả đủ tốt từ nhiều nhánh tìm kiếm, những nhánh còn lại không cần thiết.

Những nguyên nhân này có thể có **chính sách khác nhau**. Ví dụ một báo cáo tạm thời có thể bỏ phần tính toán còn lại, nhưng một thao tác đã gửi giao dịch ra hệ thống ngoài cần biết kết quả cuối hoặc bù trừ trước khi coi là hoàn tất.

### `Future.get(timeout)` chỉ giới hạn thời gian caller chờ

~~~java
try {
    return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
} catch (TimeoutException e) {
    future.cancel(true); // Tín hiệu hủy riêng; get(timeout) không tự làm.
    throw e;
}
~~~

Đây là snippet trong method trả kiểu của Future, đã có `future`, `timeoutMillis` và import tương ứng. Nếu `get(timeout)` ném `TimeoutException`, task **có thể vẫn nằm trong queue hoặc đang chạy**, và `isCancelled()` không tự chuyển true. Gọi `cancel(true)` chỉ mới gửi yêu cầu hủy; phải xử lý tiếp theo contract.

### Deadline tổng khác timeout theo từng stage

Giả sử request có ngân sách 500 ms:

~~~text
Stage A mất 410 ms.
Stage B không được tự cấp lại 500 ms nếu deadline là toàn request.
Stage B chỉ còn khoảng 90 ms (trừ overhead/cleanup).
~~~

Nếu mỗi stage được phép dùng nguyên 500 ms, một chuỗi năm stage có thể khiến request chờ nhiều lần quá deadline. Mang **deadline chung** hoặc **ngân sách còn lại** qua các tầng.

Với duration trong một JVM, có thể dùng `System.nanoTime()` để tính elapsed/remaining; nó không phải timestamp UTC và không được chuyển nguyên giá trị sang JVM khác. Khi truyền deadline qua mạng cần giao thức thời gian giữa các máy và biên an toàn thích hợp.

## 4. Tầng Future và executor: ai thực sự bị hủy?

`Future<V>.cancel(boolean mayInterruptIfRunning)` có hai khía cạnh:
- **Hủy kết quả** đại diện bởi Future.
- Nếu `mayInterruptIfRunning=true` và implementation hỗ trợ, **cố interrupt thread** đang chạy task.

Bảng quan sát thông dụng:

| Tình huống | `cancel(true)` | `cancel(false)` |
| --- | --- | --- |
| Task chưa chạy | Hủy thành công ngăn task được chạy | Tương tự |
| Task đang chạy | Đánh dấu cancelled, implementation có thể interrupt runner | Không yêu cầu interrupt runner |
| Task đã hoàn thành | Không hủy kết quả đã hoàn tất | Tương tự |
| Future đã được đánh dấu cancelled | `get()` ném `CancellationException` | Tương tự |

`cancel(true)` không phải `Thread.stop()`. Interrupt là một yêu cầu **hợp tác**, không phải kill không điều kiện. `FutureTask` và các `Future` từ executor thông dụng hỗ trợ cancel bằng cách cố interrupt runner; nhưng `Future` là **interface**, nên phải xem implementation cụ thể.

Đặc biệt, theo Javadoc **Java 17**, `CompletableFuture.cancel(true)` **không dùng interrupt để điều khiển thực thi**, và tham số `mayInterruptIfRunning` không có tác dụng. Cancel `CompletableFuture` có thể khiến `join()/get()` quan sát cancellation, nhưng không chứng minh một `supplyAsync` đang chạy đã bị ngắt.

### Parent Future không tự quản lý toàn bộ child

Giả sử:

~~~java
Future<String> child = childPool.submit(childTask);
Future<String> parent = parentPool.submit(() -> child.get());
~~~

Đây là snippet với các executor và task đã được định nghĩa. Khi `parent.cancel(true)`:
- Thread đang đợi `child.get()` có thể bị interrupt, khiến `get()` ném `InterruptedException`.
- **Child Future không tự nhận `cancel(true)`** chỉ vì parent ngừng chờ.
- Nếu child vẫn block I/O hoặc chờ queue, nó có thể giữ resource và chạy sau khi request đã hủy.
- **Coordinator/owner** phải quyết định có hủy child, đợi cleanup, hoặc cố ý chuyển child thành job độc lập.

## 5. Tầng thread: interrupt và việc hợp tác

`Thread.interrupt()` đặt interrupt status và một số primitive blocking sẽ phản hồi:
- `Thread.sleep`, `Object.wait`, `CountDownLatch.await`, `BlockingQueue.take`, `Condition.await` và `Future.get` thường ném `InterruptedException` khi chờ bị ngắt.
- Thread đang chạy CPU thường **không tự ném exception**; phải kiểm tra cờ interrupt.
- Việc chờ lấy intrinsic monitor `synchronized` không tự thành thao tác interruptible như `ReentrantLock.lockInterruptibly()`.
- Blocking I/O có semantics khác nhau tùy API/driver/native implementation; không thể tuyên bố mọi `InputStream.read` dừng ngay.

Mẫu CPU loop hợp tác:

~~~java
while (!Thread.currentThread().isInterrupted()) {
    processSmallBatch();
}
cleanupAfterCancellation();
~~~

Đây là snippet với các method được khai báo. Chia công việc thành batch nhỏ để latency phản hồi cancel nằm trong giới hạn chấp nhận được. Một lệnh CPU chạy 10 phút không kiểm tra status có thể tiếp tục đủ 10 phút dù Future đã cancelled.

### Hai phương thức kiểm tra cờ khác nhau

- `Thread.currentThread().isInterrupted()`: kiểm tra nhưng **không xóa** cờ.
- `Thread.interrupted()`: static method đọc **và xóa** cờ của thread hiện tại.

Đừng gọi `Thread.interrupted()` rồi bỏ qua giá trị chỉ để “thăm dò”: bạn có thể xóa yêu cầu hủy trước khi tầng sau đọc được.

### Propagate, restore hay xử lý?

Khi một method có thể ném `InterruptedException`, thường nên truyền tiếp nếu nó không có lý do nghiệp vụ để nuốt:

~~~java
String takeItem(BlockingQueue<String> queue)
        throws InterruptedException {
    return queue.take();
}
~~~

Nếu không thể truyền tiếp và phải thoát:

~~~java
try {
    performInterruptibleOperation();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
~~~

Snippet thứ hai nằm trong method `void`. Khôi phục cờ có ích để tầng bao ngoài tiếp tục thấy yêu cầu; nhưng **cũng phải dừng/đổi trạng thái**, không khôi phục cờ rồi tiếp tục xử lý như chưa có gì.

## 6. Ví dụ Java 17: Future cancelled và worker exited là hai việc

Tạo file **`InterruptBoundaryDemo.java`**:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class InterruptBoundaryDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);

        try {
            Future<String> future = pool.submit(() -> {
                started.countDown();
                try {
                    Thread.sleep(60_000);
                    return "completed";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "stopped";
                } finally {
                    exited.countDown();
                }
            });

            started.await();
            boolean accepted = future.cancel(true);
            exited.await();

            System.out.println("cancelAccepted=" + accepted);
            System.out.println("futureCancelled=" + future.isCancelled());
            System.out.println("workerExited=" + (exited.getCount() == 0));

            try {
                future.get();
            } catch (CancellationException e) {
                System.out.println("get=CancellationException");
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Chạy:

~~~bash
javac InterruptBoundaryDemo.java
java InterruptBoundaryDemo
~~~

Stdout trên đường thực thi không có interruption bên ngoài:

~~~text
cancelAccepted=true
futureCancelled=true
workerExited=true
get=CancellationException
~~~

Lập luận:
1. `started` xác nhận Callable đã bắt đầu. `Thread.sleep(60_000)` giữ nó chưa hoàn thành trước khi main hủy.
2. `cancel(true)` yêu cầu interrupt runner. Dù interrupt tới trước hay trong sleep, `sleep` sẽ phản hồi và thoát.
3. Worker đi tới `finally`, hạ latch `exited`. Main **đợi exited**, không dùng `future.isDone()` để suy worker đã dừng.
4. Dù Callable return `"stopped"` trong catch, Future đã cancelled nên `get()` ném `CancellationException`.

Lỗi thực tế là chỉ kiểm tra `future.isDone()` sau cancel rồi coi đã hoàn tất cleanup. Một Future bị hủy có thể done trước khi Callable rời stack, trả connection hoặc unlock. Khi yêu cầu xác nhận thực thi đã dừng, dùng lifecycle signal hoặc theo dõi termination của worker/executor với deadline.

## 7. Ví dụ Java 17: hủy parent không tự hủy child

Tạo file **`DetachedChildDemo.java`**:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class DetachedChildDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService parents = Executors.newSingleThreadExecutor();
        ExecutorService children = Executors.newSingleThreadExecutor();

        CountDownLatch childStarted = new CountDownLatch(1);
        CountDownLatch releaseChild = new CountDownLatch(1);
        CountDownLatch childExited = new CountDownLatch(1);
        CountDownLatch parentWaiting = new CountDownLatch(1);
        CountDownLatch parentExited = new CountDownLatch(1);

        try {
            Future<Integer> child = children.submit(() -> {
                childStarted.countDown();
                try {
                    releaseChild.await();
                    return 7;
                } finally {
                    childExited.countDown();
                }
            });

            childStarted.await();

            Future<Integer> parent = parents.submit(() -> {
                parentWaiting.countDown();
                try {
                    return child.get() + 1;
                } finally {
                    parentExited.countDown();
                }
            });

            parentWaiting.await();
            boolean accepted = parent.cancel(true);
            parentExited.await();

            System.out.println("parentCancelAccepted=" + accepted);
            System.out.println("parentCancelled=" + parent.isCancelled());
            System.out.println("childCancelled=" + child.isCancelled());
            System.out.println("childExitedBeforeOwnCancel="
                    + (childExited.getCount() == 0));

            child.cancel(true);
            childExited.await();

            System.out.println("childCancelledNow=" + child.isCancelled());
            System.out.println("childExitedNow="
                    + (childExited.getCount() == 0));

            try {
                parent.get();
            } catch (CancellationException e) {
                System.out.println("parentGet=CancellationException");
            }
        } finally {
            releaseChild.countDown();
            parents.shutdownNow();
            children.shutdownNow();
            parents.awaitTermination(1, TimeUnit.MINUTES);
            children.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac DetachedChildDemo.java
java DetachedChildDemo
~~~

Stdout khi thực thi bình thường:

~~~text
parentCancelAccepted=true
parentCancelled=true
childCancelled=false
childExitedBeforeOwnCancel=false
childCancelledNow=true
childExitedNow=true
parentGet=CancellationException
~~~

**Vì sao?** Parent đang chờ `child.get()`, child bị giữ ở `releaseChild.await()`. Hủy parent có thể làm thread parent rời `get` qua `InterruptedException`, nhưng không tự cancel child. Child chỉ nhận yêu cầu dừng ở lời gọi `child.cancel(true)` riêng, rồi thoát và hạ latch.

Ví dụ cho thấy **cancellation của kết quả parent không có propagation tự động qua mọi Future trong chương trình**. Nếu child thuộc một request mà parent đang điều phối, việc bỏ quên child sau hủy là **orphan work**, có thể tiếp tục giữ thread, I/O hoặc sinh side effect. Ngược lại, nếu child thực sự là công việc nền độc lập/durable, nó có thể được cố ý tách khỏi parent: cần mô tả ownership và lifecycle rõ ràng.

## 8. Ví dụ Java 17: coordinator lan truyền hủy xuống child

Coordinator muốn **hủy các child do chính nó sở hữu** khi không còn tiếp tục được. Ví dụ tiếp theo tạo child rồi parent đợi kết quả. Nếu parent bị interrupt trong `child.get()`, nó chủ động gọi `child.cancel(true)`.

Tạo file **`CascadedCancelDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CascadedCancelDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService parents = Executors.newSingleThreadExecutor();
        ExecutorService children = Executors.newSingleThreadExecutor();

        CountDownLatch childStarted = new CountDownLatch(1);
        CountDownLatch releaseChild = new CountDownLatch(1);
        CountDownLatch childExited = new CountDownLatch(1);
        CountDownLatch parentReady = new CountDownLatch(1);
        CountDownLatch parentExited = new CountDownLatch(1);

        AtomicReference<Future<String>> childHandle =
                new AtomicReference<>();

        try {
            Future<String> parent = parents.submit(() -> {
                Future<String> child = children.submit(() -> {
                    childStarted.countDown();
                    try {
                        releaseChild.await();
                        return "child-result";
                    } finally {
                        childExited.countDown();
                    }
                });

                childHandle.set(child);
                try {
                    childStarted.await();
                    parentReady.countDown();
                    return child.get();
                } catch (InterruptedException e) {
                    child.cancel(true);
                    Thread.currentThread().interrupt();
                    throw e;
                } finally {
                    parentExited.countDown();
                }
            });

            parentReady.await();
            boolean accepted = parent.cancel(true);

            parentExited.await();
            childExited.await();

            System.out.println("parentCancelAccepted=" + accepted);
            System.out.println("parentCancelled=" + parent.isCancelled());
            System.out.println("childCancelled="
                    + childHandle.get().isCancelled());
            System.out.println("childExited="
                    + (childExited.getCount() == 0));
        } finally {
            releaseChild.countDown();
            parents.shutdownNow();
            children.shutdownNow();
            parents.awaitTermination(1, TimeUnit.MINUTES);
            children.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Chạy:

~~~bash
javac CascadedCancelDemo.java
java CascadedCancelDemo
~~~

Stdout trên đường thực thi bình thường:

~~~text
parentCancelAccepted=true
parentCancelled=true
childCancelled=true
childExited=true
~~~

**Phân tích:** Parent tự giữ quyền sở hữu child Future. Khi `Future.get()` bị interrupt, catch của parent không nuốt exception: nó **hủy child**, restore interrupt status, rồi truyền tiếp. Child đang ở một thao tác chờ interruptible nên thoát và giảm `childExited`; main có tín hiệu riêng để quan sát thực thi đã kết thúc.

**Giới hạn:** Nếu parent có **nhiều** child Futures, phải quản lý tất cả. Nếu child không interruptible, `cancel(true)` không đủ để xác nhận dừng. Ví dụ minh họa đường **interrupted while awaiting**; trong ứng dụng thật cần thêm cleanup khi parent exception ở chỗ khác, cancellation trước khi child được submit, partial child creation và shutdown races.

### Mẫu quản lý nhiều child

~~~java
List<Future<?>> ownedChildren = new ArrayList<>();
try {
    ownedChildren.add(childPool.submit(stageA));
    ownedChildren.add(childPool.submit(stageB));
    // Chờ và tổng hợp.
} finally {
    if (requestNoLongerNeedsChildren) {
        for (Future<?> child : ownedChildren) {
            child.cancel(true);
        }
    }
}
~~~

Đây là **pseudocode Java** trong method đã có `childPool`, `stageA`, `stageB`, `requestNoLongerNeedsChildren`; không phải class độc lập. Không dùng `finally` để cancel **mọi** child khi nghiệp vụ yêu cầu một job đã tách ownership tiếp tục chạy. Mục tiêu là làm rõ quan hệ sở hữu: ai submit, ai chờ, ai cancel, ai cleanup và ai trả kết quả.

## 9. Cancellation token: truyền ý định hủy qua cây tác vụ

Không phải helper nào cũng có Future. Một **cancellation token** là một object thể hiện ý định dừng để các stage kiểm tra tại checkpoint.

Nhu cầu phổ biến:
- Request chạy qua nhiều method/helper.
- Một parent điều phối nhiều child task trên các executor khác nhau.
- CPU loop cần kiểm tra ý định hủy giữa các bước.
- Một child có thể bị hủy riêng mà **không** hủy các sibling hoặc parent.
- Parent hủy cần khiến mọi child còn phụ thuộc cùng request quan sát trạng thái cancelled.

### Ví dụ Java 17: token cha–con

Tạo file **`HierarchicalTokenDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class HierarchicalTokenDemo {
    static final class CancelToken {
        private final CancelToken parent;
        private final AtomicBoolean localCancelled = new AtomicBoolean();

        CancelToken(CancelToken parent) {
            this.parent = parent;
        }

        void cancel() {
            localCancelled.set(true);
        }

        boolean isCancelled() {
            return localCancelled.get()
                    || (parent != null && parent.isCancelled());
        }
    }

    public static void main(String[] args) throws Exception {
        CancelToken root = new CancelToken(null);
        CancelToken child = new CancelToken(root);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);

        try {
            Future<String> task = pool.submit(() -> {
                started.countDown();
                while (!child.isCancelled()) {
                    Thread.sleep(10); // Chỉ giảm CPU trong demo.
                }
                return "stopped-by-token";
            });

            started.await();
            root.cancel();

            System.out.println("rootCancelled=" + root.isCancelled());
            System.out.println("childCancelled=" + child.isCancelled());
            System.out.println("result=" + task.get());
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Chạy:

~~~bash
javac HierarchicalTokenDemo.java
java HierarchicalTokenDemo
~~~

Stdout khi không có interruption ngoài ý muốn:

~~~text
rootCancelled=true
childCancelled=true
result=stopped-by-token
~~~

`AtomicBoolean` cung cấp visibility cho trạng thái token. Child đọc `isCancelled()` của nó và ancestor, nên parent hủy được quan sát ở child. Token giúp truyền **ý định hủy** từ scope cha; nó không tự gọi `Thread.interrupt()` hoặc đóng connection.

Trong demo, `Thread.sleep(10)` chỉ giúp tránh vòng CPU nóng; nó không phải phương tiện bảo đảm phản hồi tức thì. Nếu task bị kẹt ở một I/O không có timeout, nó không thể check token cho đến khi I/O trả về. Thiết kế thực thường phối hợp **token + interrupt/timed API/close**.

### Cancellation độc lập theo scope

Một token con được phép tự cancel mà không bắt buộc cha canceled; sibling khác có thể tiếp tục. Tuy nhiên nếu **mọi child** đều phụ thuộc kết quả chung all-or-nothing, coordinator có thể quyết định cancel root khi một child thất bại. Chính sách đó phải được viết ở **tầng orchestration**, không ngầm đoán từ token.

### Token và memory semantics

Một shared `boolean cancelled` thường không đủ nếu được truy cập đa luồng mà thiếu synchronized/volatile/atomic. Dùng `AtomicBoolean`, `volatile` hoặc giao thức memory visibility tương đương. Nếu token còn chứa reason/deadline, hãy công bố các field nhất quán (immutable cancellation state/atomic reference nếu cần). Không xây một tập cờ mutable rồi để reader thấy reason của lần hủy này và status của lần khác.

## 10. Deadline và ngân sách còn lại

Một request deadline có thể dùng một mốc nano-time nội bộ và truyền object Deadline qua các stage. Tất cả stage dùng **remaining time**, không tạo một timeout mới đầy đủ mỗi lần.

Tạo file **`DeadlineBudgetDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DeadlineBudgetDemo {
    static final class Deadline {
        private final long deadlineNanos;

        Deadline(long timeout, TimeUnit unit) {
            deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        }

        long remainingNanos() {
            return Math.max(0L, deadlineNanos - System.nanoTime());
        }
    }

    static void awaitWithinBudget(
            CountDownLatch event, Deadline deadline)
            throws InterruptedException, TimeoutException {

        long remaining = deadline.remainingNanos();
        if (remaining == 0L
                || !event.await(remaining, TimeUnit.NANOSECONDS)) {
            throw new TimeoutException("overall deadline reached");
        }
    }

    public static void main(String[] args) throws Exception {
        Deadline deadline = new Deadline(20, TimeUnit.MILLISECONDS);
        CountDownLatch neverCompleted = new CountDownLatch(1);

        try {
            awaitWithinBudget(neverCompleted, deadline);
            System.out.println("eventArrived");
        } catch (TimeoutException e) {
            System.out.println("budgetTimedOut=true");
        }
    }
}
~~~

Chạy:

~~~bash
javac DeadlineBudgetDemo.java
java DeadlineBudgetDemo
~~~

Stdout nếu thread không bị interrupt:

~~~text
budgetTimedOut=true
~~~

Vì latch **không bao giờ count down**, nó không thể hoàn tất bình thường. Nếu deadline đã hết trước khi stage được chạy, method lập tức báo timeout; nếu chưa, nó chờ **chỉ phần còn lại** rồi báo timeout. Không in elapsed milliseconds do thời gian scheduling không cố định.

**Giới hạn rõ ràng:** ví dụ dùng deadline ngắn; code production cần validate timeout âm/cực lớn, tránh overflow khi cộng nano duration. `System.nanoTime()` phù hợp cho elapsed time **trong một JVM**, không là timestamp có thể gửi trực tiếp qua nhiều máy.

**Deadline object không tự thức tỉnh thread**: nó chỉ cho phép tính ngân sách. Khi stage block ở socket, lock, queue, remote call, phải truyền remaining budget vào API có timeout phù hợp hoặc dùng kênh hủy/close riêng. Đặt `Future.get(timeout)` ở tầng caller mà không đặt timeout cho I/O bên trong không giới hạn worker stop latency.

## 11. Blocking operations: mỗi tầng có cơ chế ngắt khác nhau

| Loại công việc | Phản hồi với interrupt | Cần thiết kế thêm |
| --- | --- | --- |
| `Thread.sleep` | Thường ném `InterruptedException` | Exit/restore/cleanup |
| `BlockingQueue.take` | Chờ interruptible | `poll(timeout)` nếu có deadline |
| `CountDownLatch.await` | Chờ interruptible | Bảo đảm latch release/shutdown |
| `Future.get` | Thread chờ có thể nhận `InterruptedException` | Cancel child Future riêng |
| `Condition.await` | Chờ interruptible, lấy lại lock trước khi thoát | while predicate, finally unlock |
| `ReentrantLock.lockInterruptibly` | Chờ lấy lock có thể bị interrupt | Chọn timeout khi cần |
| Tranh intrinsic monitor `synchronized` | Không tự ném `InterruptedException` khi đợi lock | Critical section ngắn, kiểm tra cờ sau khi vào |
| CPU loop | Không dừng nếu không có checkpoint | `isInterrupted()`/token giữa các batch |
| Blocking I/O | Khác nhau giữa stream, socket, channel, driver | Deadline đúng API, close theo contract |
| Remote commit | Local interrupt không đồng nghĩa remote rollback | Idempotency, trạng thái giao dịch |

**I/O không thể suy đoán:** Với một số `InterruptibleChannel`, interruption lúc blocking có thể làm channel đóng và ném `ClosedByInterruptException`; với nhiều stream/socket/driver khác, semantics phụ thuộc triển khai, timeout hoặc đóng tài nguyên. Đừng hứa mọi `InputStream.read()` trả về tức thì khi `Future.cancel(true)`.

### Timeout không hủy tác dụng phụ của remote service

Caller có thể mất kết nối hoặc timeout **sau khi server đã nhận và commit** request. Nếu retry mà không có idempotency key, có nguy cơ tạo hai bản ghi/thanh toán. Do đó propagation deadline không thay thế giao thức chống duplicate và reconciliation ở nơi thực sự tạo side effect.

## 12. Cleanup: hủy không được làm rò resource

Một task có thể nắm giữ `Semaphore` permit, lock, file, stream hoặc connection khi nhận interrupt. `Future.cancel(true)` không tự thay task chạy code cleanup; phải dùng `finally`/try-with-resources và đảm bảo state không bị bỏ ở trạng thái nửa cập nhật.

### Ví dụ Java 17: trả permit sau cancellation

Tạo file **`ResourceReleaseDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ResourceReleaseDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Semaphore permits = new Semaphore(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);

        try {
            Future<?> task = pool.submit(() -> {
                boolean acquired = false;
                try {
                    permits.acquire();
                    acquired = true;
                    started.countDown();
                    neverReleased.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    if (acquired) {
                        permits.release();
                    }
                    exited.countDown();
                }
            });

            started.await();
            boolean accepted = task.cancel(true);
            exited.await();

            System.out.println("cancelAccepted=" + accepted);
            System.out.println("futureCancelled=" + task.isCancelled());
            System.out.println("availablePermits="
                    + permits.availablePermits());
            System.out.println("workerExited="
                    + (exited.getCount() == 0));
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Chạy:

~~~bash
javac ResourceReleaseDemo.java
java ResourceReleaseDemo
~~~

Stdout bình thường:

~~~text
cancelAccepted=true
futureCancelled=true
availablePermits=1
workerExited=true
~~~

`started` được hạ **sau khi acquire permit**, nên main chỉ hủy task khi nó đã vào vùng sở hữu permit. Interrupt làm `neverReleased.await()` kết thúc bằng `InterruptedException`; `finally` release permit và hạ `exited`. Main đợi `exited` trước khi kiểm tra số permit. Do đó kết quả in chứng minh tài nguyên local trong demo đã trả, không chỉ Future đã bị hủy.

**Production:** cleanup có thể ném exception, chậm hoặc block, vì thế việc đợi worker exited cũng cần deadline và cảnh báo. Nếu một permit được release hai lần do hai nhánh hủy cùng chạy cleanup, invariant tài nguyên cũng bị sai; đặt cleanup tại một owner và viết logic idempotent khi có nhiều đường kết thúc.

## 13. Ranh giới commit: tại sao kiểm tra interrupt chưa đủ?

Một task đã bắt đầu gọi external API, ghi database hoặc publish vào shared state có thể tiếp tục gây side effect sau khi caller hủy. Xét snippet:

~~~java
if (!Thread.currentThread().isInterrupted()) {
    commitToExternalSystem(result);
}
~~~

Đây là đoạn minh họa với `result` và `commitToExternalSystem` do ứng dụng định nghĩa. Có race:

~~~text
Worker                           Caller
------                           ------
check isInterrupted() -> false
                                 cancel(true)
commitToExternalSystem(result)
                                 Caller nghĩ tác vụ đã bỏ
~~~

Check và commit không phải một transaction. Ngay cả nếu worker được interrupt trước khi gửi, một remote request trước đó có thể đã commit tại server.

Các hướng xử lý khi kết quả muộn không được phép ghi:
- **Generation/version:** mỗi request/retry có version; kết quả cũ không thể ghi đè state của generation mới nếu publish kiểm tra version nguyên tử.
- **Commit ownership:** một component duy nhất được quyền chấp nhận kết quả và quyết định liệu cancellation đã thắng chưa.
- **Atomic state transition:** chuyển `RUNNING -> COMMITTING` dưới lock/CAS theo invariant cần giữ; định nghĩa nếu cancellation và commit cạnh tranh thì bên nào được chấp nhận.
- **Idempotency key:** hệ thống đích nhận diện các lần retry của cùng một business operation.
- **Transaction hoặc compensation:** nếu commit một phần không thể rollback nguyên tử, có cơ chế bù trừ và durable status.
- **Reconciliation:** khi caller timeout nhưng không biết remote đã làm gì, tra cứu trạng thái bằng request ID thay vì đoán “thất bại”.

**Cancellation token và interrupt flag không tự đảm bảo atomicity với commit ngoài JVM.** Nếu cần tính đúng mạnh, phải có giao thức ở chính nơi có side effect.

## 14. CompletableFuture và đồ thị phụ thuộc

Java 17 `CompletableFuture` tạo pipeline bằng `thenApply`, `thenCompose`, `allOf`, `anyOf`. Nhưng **cancel một CompletableFuture** không đồng nghĩa cancel mọi công việc nằm dưới stage:
- `cancel(true)` trên `CompletableFuture` không dùng interrupt để điều khiển worker trong Java 17.
- Một `supplyAsync` đang chạy có thể tiếp tục tạo side effect.
- Các dependent stages có thể nhận completion exceptional/cancellation theo hợp đồng CompletableFuture, nhưng không tự quản lý FutureTask/HTTP request mà callback đã tạo.
- Nếu cần hủy thực thi, phải chuyển token/deadline xuống callback hoặc giữ riêng task/I/O handles có chính sách cancel thích hợp.

### Snippet ứng dụng token trong pipeline

~~~java
CancelToken requestToken = new CancelToken(null);

CompletableFuture<String> result = CompletableFuture.supplyAsync(() -> {
    if (requestToken.isCancelled()) {
        return "cancelled-before-work";
    }

    String answer = computeWithCheckpoints(requestToken);

    if (requestToken.isCancelled()) {
        return "discarded";
    }
    return answer;
}, executor);

// Ở phía caller:
requestToken.cancel();
result.cancel(true);
~~~

Đây là **snippet**, dùng class `CancelToken` trong ví dụ trước cùng method `computeWithCheckpoints` và executor do ứng dụng định nghĩa. `result.cancel(true)` chỉ là cancellation ở tầng CompletableFuture; `requestToken.cancel()` làm ý định hủy có thể được callback tự quan sát. Bước publish cuối vẫn cần kiểm soát generation/commit, bởi cancellation có thể đến sau lần check cuối.

Không mặc định các API structured concurrency được giới thiệu trong phiên bản Java mới hơn có sẵn trên Java 17. Với API chuẩn Java 17, **ownership của Futures/child tasks** phải được quản lý tường minh.

## 15. Chính sách lỗi/hủy của coordinator

Khi một child thất bại, có phải hủy tất cả child còn lại? Không có một quy tắc cho mọi nghiệp vụ:

| Chính sách | Khi nào áp dụng | Tác vụ khác |
| --- | --- | --- |
| All-or-nothing | Mọi child cần thành công mới có kết quả hợp lệ | Hủy phần còn lại khi lỗi bắt buộc |
| Best-effort | Có thể trả kết quả không đầy đủ trong deadline | Tiếp tục child còn có giá trị |
| First-success | Một trong nhiều child cho kết quả hợp lệ là đủ | Hủy child thừa nếu an toàn |
| Durable background | Job phải tiếp tục dù caller disconnect | Tách quyền sở hữu và giám sát |
| Shared computation | Nhiều caller cần cùng một child | Một caller hủy không được hủy shared work của người khác bừa bãi |

Tầng coordinator cần giữ **danh sách những child do nó thực sự sở hữu**. Nếu một child được nhiều callers dùng chung, có thể cần lease/ref count hoặc một owner độc lập. Không được tùy tiện hủy task chung chỉ vì một caller mất hứng nhận kết quả.

### Đừng biến interruption thành “success mặc định”

Sai:

~~~java
try {
    return child.get();
} catch (InterruptedException ignored) {
    return defaultResult;
}
~~~

Đây là snippet bên trong method trả kiểu phù hợp. Nó có thể che yêu cầu hủy, khiến caller tưởng pipeline thành công và commit một giá trị sai. Hướng xử lý tốt hơn là xác định child ownership, cancel child nếu không còn cần, propagate `InterruptedException` hoặc restore status rồi trả trạng thái canceled rõ ràng.

### Khi worker đã cancelled nhưng vẫn chạy

Nếu một task bỏ qua interrupt hoặc đang ở I/O chưa kết thúc, nó có thể trở thành **orphan work**. Dù Future isCancelled true, không có bằng chứng resource đã được giải phóng. Cần log/metric phân biệt:
- Cancellation requested.
- Cancellation accepted by Future.
- Child cancellation dispatched.
- Worker actually exited.
- Cleanup completed.
- Business commit accepted/rejected.

## 16. Executor shutdown là tầng hệ thống khác với request cancellation

**`ExecutorService.shutdown()`**: từ chối task mới, cho phép task đã submit chạy theo hợp đồng.

**`shutdownNow()`**: cố dừng task đang chạy bằng interrupt và trả task chưa được bắt đầu, nhưng đây là hành động **best-effort**.

**`awaitTermination(timeout, unit)`**: chờ executor terminated sau shutdown, không tự khởi động shutdown.

Snippet:

~~~java
pool.shutdown();
try {
    if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
        pool.shutdownNow();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            // Ghi nhận task không hợp tác hoặc cleanup quá hạn.
        }
    }
} catch (InterruptedException e) {
    pool.shutdownNow();
    Thread.currentThread().interrupt();
}
~~~

Đây là snippet với `pool` là `ExecutorService` và `TimeUnit` đã import. Một yêu cầu bị cancel **không có quyền shutdown executor dùng chung** của cả ứng dụng; hãy cancel Futures/tokens thuộc request. Shutdown application mới có trách nhiệm phối hợp tất cả request scopes, drain/cancel, đợi cleanup và theo dõi noncooperative worker.

## 17. Checklist thiết kế phân tầng tín hiệu hủy

1. **Nguồn hủy là gì?** Người dùng, deadline, upstream error hay shutdown? Chính sách mỗi loại có khác nhau?
2. **Ai sở hữu các child?** Có danh sách Future/handle đầy đủ khi parent tạo nhiều nhánh không?
3. **Hủy có cần lan xuống không?** All-or-nothing hay job được phép tiếp tục độc lập?
4. **Mỗi stage nhận gì?** Interrupt, token, deadline, close, timeout API hoặc kết hợp?
5. **CPU stage có checkpoint không?** Một vòng tính toán quá dài có kéo cancel latency lên không?
6. **Blocking I/O có timeout/hủy thực sự không?** Không nhầm Future.get(timeout) với socket/DB timeout.
7. **Tầng trung gian có nuốt InterruptedException không?** Nếu không thể propagate, có restore flag và thoát không?
8. **Mỗi child có cleanup không?** Lock, permit, connection, stream trả trong finally/try-with-resources?
9. **Có trường hợp cleanup fail/treo không?** Có deadline giám sát, không đợi vô hạn khi shutdown?
10. **Có thể cancel khi đang commit không?** Cần quyền commit/state machine và idempotency.
11. **Kết quả muộn có thể ghi đè generation mới không?** Có CAS/version check ở nơi publish?
12. **Có child chia sẻ giữa nhiều callers không?** Một caller không tự hủy global work.
13. **Có cấu trúc token có memory visibility đúng không?** Dùng atomic/volatile hoặc immutable state.
14. **Có truyền ngân sách thời gian còn lại không?** Không khởi động lại toàn timeout mỗi stage.
15. **Metrics có phân biệt accept cancel và worker exit không?** Không báo “cancel hoàn tất” chỉ từ Future.isDone().
16. **Test có điều phối chính xác không?** Dùng latch/barrier thay vì sleep để ép điểm thực thi.
17. **Shutdown song song cancel có idempotent không?** Tránh double-release hoặc double-compensate.
18. **Đã đo liveness và throughput sau khi thêm cancellation chưa?** Một đường hủy không được gây deadlock mới.

## 18. Bài tập và câu hỏi tự kiểm tra

1. `parent.cancel(true)` có tự gọi `child.cancel(true)` không? **Không**, trừ khi abstraction/coordinator thiết kế lan truyền.
2. `Future.isDone()==true` sau cancel có chứng minh Callable đã thoát không? **Không**, cần lifecycle observation riêng.
3. `Future.get(timeout)` ném `TimeoutException` có hủy task không? **Không**.
4. `Thread.currentThread().isInterrupted()` khác `Thread.interrupted()` thế nào? **Hàm sau là static và xóa flag của thread hiện tại**.
5. Nếu CPU loop không check interrupt hay token, cancel có buộc nó dừng không? **Không**.
6. Token cha bị cancel thì token con như ví dụ có biết không? **Có**, vì đọc trạng thái ancestor.
7. Token đổi true có tự ngắt một lần `BlockingQueue.take()` đang block không? **Không**, cần interrupt/timed/wakeup protocol.
8. Tại sao parent đang đợi `child.get()` bị interrupt mà child vẫn chạy? **InterruptedException của parent không hủy child Future**.
9. Ai có trách nhiệm cancel child khi không cần nữa? **Owner/coordinator quản lý child**, trừ khi child đã tách ownership rõ ràng.
10. Có nên hủy task chia sẻ nếu một caller bỏ kết quả không? **Không mặc định**, phải xem ownership và yêu cầu của callers khác.
11. Trong Java 17, `CompletableFuture.cancel(true)` có luôn interrupt thread `supplyAsync` không? **Không; mayInterruptIfRunning không có tác dụng**.
12. `shutdownNow()` có buộc mọi task dừng tức thì không? **Không, chỉ best-effort**.
13. Tại sao cần deadline tổng xuyên nhiều stage? **Để không tái cấp nguyên timeout và vượt thời gian yêu cầu**.
14. `System.nanoTime()` có phải timestamp UTC có thể gửi sang server khác không? **Không**, là cơ sở đo duration tương đối trong process.
15. `Future.get(timeout)` có đủ để ngắt mọi socket read không? **Không**, phải dùng API I/O có timeout/close/cancel tương ứng.
16. Check interrupt ngay trước commit đã ngăn mọi commit sau cancel chưa? **Chưa**, còn race giữa check và commit.
17. Nếu server đã commit nhưng caller timeout, nên làm gì để tránh duplicate? **Idempotency key và tra cứu/reconcile trạng thái**.
18. Cancellation token có thay được cơ chế memory visibility không? **Không**, token phải dùng atomic/volatile/lock đúng cách.
19. Vì sao cleanup phải diễn ra trong `finally`? **Để exception/interruption vẫn dẫn tới giải phóng tài nguyên tại owner**.
20. Trong `DetachedChildDemo`, hủy parent có khiến child isCancelled true không? **Không**, chỉ lần hủy child riêng.
21. Trong `CascadedCancelDemo`, vì sao child isCancelled true? **Parent catch InterruptedException và chủ động gọi `child.cancel(true)`**.
22. Trong `ResourceReleaseDemo`, vì sao permit trở lại 1? **Worker chạy finally và release permit sau interruption, main đợi exited**.
23. Nếu cancellation và shutdown cùng đến, có thể release resource hai lần không? **Có nếu giao thức cleanup sai**, phải có owner/transition idempotent.
24. Có thể coi số lần `cancel(true)==true` là số lần business operation bị rollback không? **Không**, đây là hai metric khác.
25. Một child có thể cố ý chạy tiếp sau request cancel không? **Có**, nếu nó là job nền durable, nhưng phải tách ownership, quản lý tài nguyên và finality rõ ràng.

## 19. Kết luận

**Phân tầng tín hiệu hủy** là thiết kế đường truyền yêu cầu ngừng từ caller xuống coordinator, children, thread, API blocking và nơi commit; đồng thời xác định kết quả và trạng thái cleanup đi ngược lên. `Future.cancel(true)`, interrupt status, cancellation token và deadline **là những cơ chế bổ sung, không thay thế lẫn nhau**.

Một protocol đúng phải xác định:
- **Phạm vi sở hữu:** child nào thuộc parent và có cần hủy theo parent không.
- **Điểm kiểm tra:** stage nào hợp tác với interrupt/token, I/O nào có timeout/close.
- **Tài nguyên:** ai nhả lock, permit, connection và xác nhận worker exited.
- **Nghiệp vụ:** cancellation có thể ngăn commit nào, xử lý side effect đã hoàn tất và kết quả đến muộn ra sao.
- **Quan sát:** không nhầm Future cancelled với worker stopped và business finalized.

Hãy đo thành công của hủy bằng **tác vụ thật sự dừng, resource thật sự được trả và kết quả nghiệp vụ được xử lý đúng**, chứ không chỉ bằng giá trị trả về của `cancel(true)`.

## Tài liệu tham khảo Java SE 17

- [Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): `cancel`, `isCancelled`, `get(timeout)`.
- [ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): `submit`, `shutdown`, `shutdownNow`, `awaitTermination`.
- [Thread](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): `interrupt`, `interrupted`, `isInterrupted`.
- [CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): điều phối giữa các stage.
- [CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): cancellation semantics.
- [BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html): `take` và `poll(timeout)`.
- [ReentrantLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html): `lockInterruptibly` và `tryLock`.
- [InterruptibleChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/InterruptibleChannel.html): interruption với blocking channel.
- [Java Language Specification `17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): happens-before và Java Memory Model.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-012">Không nuốt InterruptedException hoặc làm mất tín hiệu hủy</a>

<details>
<summary>Xem chi tiết</summary>

# Không nuốt InterruptedException hoặc làm mất tín hiệu hủy trong Java

## 1. Vì sao việc xử lý InterruptedException là một phần của tính đúng đắn?

Một tác vụ chạy đồng thời có thể phải dừng trước khi hoàn thành: người dùng hủy, quá thời hạn xử lý, ứng dụng shutdown, hoặc một task khác không còn cần kết quả. Java sử dụng **interrupt** như một yêu cầu hủy hợp tác. Những phép chờ có thể ngắt, chẳng hạn `Thread.sleep()`, `Object.wait()`, `Thread.join()`, `BlockingQueue.take()`, `CountDownLatch.await()` và `Condition.await()`, thông báo cho caller bằng `InterruptedException`.

Sai lầm nguy hiểm:

~~~java
// SAI: đây chỉ là minh họa, không phải chương trình độc lập.
try {
    blockingOperation();
} catch (InterruptedException ignored) {
    // Bỏ qua rồi tiếp tục làm việc.
}
~~~

Một phương thức chờ thông thường **xóa interrupt status** khi ném `InterruptedException`. Nếu catch rỗng, tín hiệu hủy có thể biến mất trong mắt các tầng phía trên. Tác vụ tiếp tục hoạt động như chưa có gì xảy ra, khiến shutdown treo, request vượt deadline, hoặc side effect được thực hiện sau khi bên gọi đã yêu cầu hủy.

**Nguyên tắc:** Một tầng code không có quyền tự ý làm biến mất yêu cầu hủy mà tầng khác có thể cần biết. Khi nhận `InterruptedException`, hãy **truyền lên**, hoặc **khôi phục interrupt status và thoát theo hợp đồng** nếu không thể truyền checked exception; trường hợp chủ động xử lý hoàn toàn tín hiệu phải được thiết kế và tài liệu hóa rõ.

Bài này chỉ xét cơ chế interrupt và hủy của **Java concurrency nói chung**, không phụ thuộc framework.

### Mục tiêu sau bài

Bạn sẽ có khả năng:

1. Giải thích vì sao `InterruptedException` thường đi kèm việc clear cờ ngắt và điều gì xảy ra nếu catch rỗng.
2. Phân biệt **propagate**, **restore flag**, **handle hoàn toàn** và **nuốt tín hiệu**.
3. Viết ví dụ Java 17+ minh họa mất interrupt, truyền ngoại lệ, khôi phục cờ và cleanup trong worker.
4. Phối hợp `Future.cancel(true)`, executor shutdown, latch và xác nhận task thật sự dừng mà không suy diễn sai từ trạng thái Future.
5. Tránh mất yêu cầu hủy trong catch rộng, retry loop, xử lý timeout, khóa và I/O.
6. Thiết kế test không phụ thuộc `Thread.sleep` của thread điều phối để đoán lịch chạy.

## 2. Interrupt status và ý nghĩa của InterruptedException

Mỗi thread có một trạng thái interrupt. `target.interrupt()` là yêu cầu ngắt thread đích; không phải lệnh ép thread đó dừng ngay lập tức. Nếu thread đang chờ trong phương thức interruptible, phương thức có thể ném `InterruptedException` và thread cần phản ứng.

Ba API dễ nhầm:

| API | Tác động | Có xóa flag không? |
| --- | --- | --- |
| `target.interrupt()` | Yêu cầu ngắt thread đích | Đây không phải thao tác kiểm tra/clear |
| `target.isInterrupted()` | Quan sát cờ của thread đích | Không |
| `Thread.interrupted()` | Đọc cờ của **thread đang chạy** | **Có**, test-and-clear |

**`Thread.interrupted()` là phương thức static.** Không nên viết `worker.interrupted()` để hỏi worker: lời gọi static đó vẫn kiểm tra và xóa flag của **current thread**. Hãy dùng `worker.isInterrupted()` nếu cần quan sát trạng thái worker từ nơi khác.

Khi một phép chờ `sleep`, `wait`, `join` hoặc `await` nhận interrupt theo hợp đồng và ném `InterruptedException`, **flag thường đã bị clear** tại thời điểm catch chạy. Ngoại lệ chứa thông tin “vừa có yêu cầu ngắt” ngay cả khi `Thread.currentThread().isInterrupted()` trong catch trả false.

### Interrupt là yêu cầu, không phải kết quả hoàn thành

~~~text
Caller                     Worker
  |                           |
  +-- interrupt() ----------->|  yêu cầu hủy
  |                           |
  |                           +-- nhận biết qua polling hoặc wait
  |                           +-- cleanup / bảo toàn invariant
  |                           +-- kết thúc
  |
  +-- join() / latch -------->|  xác nhận hoàn tất
~~~

Gửi interrupt không bảo đảm tài nguyên được đóng, transaction được rollback hoặc worker đã rời khỏi critical section. Nếu caller cần xác nhận, hãy dùng `Thread.join()`, `CountDownLatch`, một kênh kết quả hoặc cơ chế lifecycle được thiết kế riêng.

### Happens-before

Java Memory Model có quy tắc đồng bộ liên quan interrupt: hành động ngắt một thread **happens-before** điểm thread đích phát hiện ra interrupt theo cơ chế JMM thích hợp. Điều này không biến interrupt thành message queue hay transaction; các cập nhật trạng thái dùng chung khác vẫn cần giao thức phù hợp, và một thread không bao giờ kiểm tra tín hiệu có thể không tiến triển đến điểm nhận biết.

## 3. Catch rỗng phá hợp đồng hủy như thế nào?

Xét một worker:

~~~java
// SAI: code minh họa trong Runnable.
while (hasMoreWork()) {
    try {
        Thread.sleep(100);
    } catch (InterruptedException ignored) {
        // Flag đã bị clear, vòng lặp tiếp tục.
    }

    performSideEffect();
}
~~~

Caller gọi `worker.interrupt()` để yêu cầu ngừng xử lý. Khi `sleep()` ném `InterruptedException`, catch rỗng khiến vòng lặp vẫn chạy và `performSideEffect()` vẫn có thể được gọi sau yêu cầu hủy. Nếu vòng này chạy vô hạn, worker có thể không bao giờ dừng.

Cũng nguy hiểm khi catch rồi chỉ ghi log:

~~~java
// SAI nếu không có chính sách xử lý hủy khác.
catch (InterruptedException ex) {
    logger.warn("Interrupted", ex);
}
// Tiếp tục làm việc sau catch.
~~~

**Logging không phải xử lý hủy.** Một log line không khôi phục interrupt status, không truyền exception, không ngừng side effect và không báo caller rằng task đã kết thúc.

### Vì sao cờ bị mất?

Trình tự phổ biến:

~~~text
Ban đầu: interrupt flag = false
Caller: worker.interrupt()       -> flag = true
Worker: Thread.sleep(...)        -> InterruptedException
                                   flag được clear trước khi catch
Worker: catch rỗng              -> flag vẫn false
Worker: poll isInterrupted()     -> false
Worker: tiếp tục công việc       -> yêu cầu hủy bị mất
~~~

Nếu một tầng code **chủ động tiêu thụ** yêu cầu hủy theo hợp đồng riêng, việc không restore có thể hợp lý. Nhưng “catch vì compiler bắt buộc” rồi bỏ qua không phải xử lý có chủ ý.

## 4. Ba lựa chọn xử lý InterruptedException

### 4.1. Lựa chọn A: truyền exception lên caller

Ưu tiên khi method có thể khai báo `throws InterruptedException`:

~~~java
// Mã minh họa, không phải chương trình độc lập.
void waitForMoreWork() throws InterruptedException {
    queue.take();
}
~~~

Caller nhận rõ tín hiệu rằng việc chờ bị ngắt, có thể dừng task, hủy workflow hoặc xử lý theo tầng sở hữu công việc. Thường **không cần** tự restore flag trước khi propagate, vì bản thân exception đã mang thông tin điều khiển.

Nếu cần cleanup tại tầng hiện tại, dùng `finally`:

~~~java
// Mã minh họa trong method có hợp đồng throws; không độc lập.
void process() throws InterruptedException {
    acquireResource();
    try {
        doInterruptibleStep();
    } finally {
        releaseResource();
    }
}
~~~

`finally` chạy khi exception được truyền lên (trừ các tình huống chấm dứt JVM bất thường). Cleanup cũng có thể ném exception khác; cần thiết kế để không vô tình che mất lỗi/ngắt quan trọng.

### 4.2. Lựa chọn B: không thể throws, restore flag và thoát

`Runnable.run()` không khai báo checked `InterruptedException`. Mẫu thường dùng:

~~~java
// Mẫu Runnable, không phải chương trình độc lập.
Runnable task = () -> {
    try {
        doInterruptibleWork();
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
    }
};
~~~

Khôi phục cờ bằng `Thread.currentThread().interrupt()` cho phép code khác trong thread có cơ hội nhận biết. **`return` rất quan trọng** khi ý định là hủy tác vụ; nếu chỉ restore rồi tiếp tục vòng lặp hoặc làm side effect thì task chưa thực sự tôn trọng yêu cầu hủy.

Một cách khác là ném một unchecked exception có semantics cancellation được API của bạn định nghĩa, nhưng phải chú ý cleanup, logging và hợp đồng báo lỗi. Không tùy tiện biến mọi interrupt thành một lỗi nghiệp vụ thông thường.

### 4.3. Lựa chọn C: xử lý hoàn toàn theo giao thức cụ thể

Đôi khi một tầng *có trách nhiệm sở hữu quyết định hủy* và chủ động thực hiện một hành động nhất quán rồi tiếp tục, chẳng hạn một vòng quản lý dịch vụ cần đóng một phiên làm việc, báo trạng thái và chuyển sang pha khác. Khi đó:

- Phải xác định rõ **ai sở hữu interrupt policy**.
- Không trả kết quả như thể công việc cũ đã thành công.
- Tài liệu hóa vì sao clear flag là cố ý và vì sao không phải propagate/restore.
- Đảm bảo code tầng trên không còn cần nhận tín hiệu đó.
- Tránh tạo vòng lặp bỏ qua vô hạn mọi interrupt.

Đây là lựa chọn có điều kiện, **không** phải lý do để dùng catch rỗng mặc định.

## 5. Ví dụ 1 — so sánh nuốt và khôi phục flag (Java 17+)

Ví dụ chạy ngay trong main để không phụ thuộc lịch scheduler. Chương trình cố tình minh họa **một hành vi sai** rồi cách restore đúng.

Tạo tệp `SwallowVsRestoreDemo.java`:

~~~java
public class SwallowVsRestoreDemo {
    public static void main(String[] args) {
        Thread.currentThread().interrupt();

        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
            // SAI: minh họa tín hiệu bị nuốt.
        }

        System.out.println(
            "after swallow = "
                + Thread.currentThread().isInterrupted()
        );

        Thread.currentThread().interrupt();

        try {
            Thread.sleep(10);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        System.out.println(
            "after restore = "
                + Thread.currentThread().isInterrupted()
        );
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac SwallowVsRestoreDemo.java
java SwallowVsRestoreDemo
~~~

Stdout chính xác trên luồng main trong hoàn cảnh ví dụ:

~~~text
after swallow = false
after restore = true
~~~

Lần `sleep(10)` đầu gặp cờ interrupt đã đặt và ném `InterruptedException`; JVM clear flag. Catch rỗng khiến lần `isInterrupted()` sau đó trả false. Lần thứ hai, catch khôi phục flag nên lần đọc sau trả true.

**Lưu ý:** Ví dụ cố ý để flag của main ở true lúc kết thúc để dễ quan sát; trong chương trình thực cần policy rõ ràng của thread sở hữu thực thi. Dòng `Thread.sleep(10)` không dùng làm cơ chế đồng bộ thời gian, mà chỉ là một điểm chờ interruptible có thể thử trực tiếp.

## 6. Ví dụ 2 — propagate InterruptedException lên tầng gọi (Java 17+)

Tạo tệp `PropagateInterruptDemo.java`:

~~~java
public class PropagateInterruptDemo {
    static void waitForStep() throws InterruptedException {
        Thread.sleep(1000);
    }

    static void runStep() throws InterruptedException {
        waitForStep(); // Không che mất InterruptedException.
    }

    public static void main(String[] args) {
        Thread.currentThread().interrupt();

        try {
            runStep();
            System.out.println("unexpected completion");
        } catch (InterruptedException ex) {
            System.out.println("caller received interruption");
            System.out.println(
                "flag in catch = "
                    + Thread.currentThread().isInterrupted()
            );
            Thread.currentThread().interrupt();
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac PropagateInterruptDemo.java
java PropagateInterruptDemo
~~~

Stdout chính xác:

~~~text
caller received interruption
flag in catch = false
~~~

`runStep()` không sở hữu quyết định hủy nên chỉ khai báo `throws InterruptedException` và cho exception đi lên. `main` nhận ngoại lệ, thấy cờ đã clear khi được sleep thông báo, rồi restore vì đây là rìa xử lý của ví dụ.

Một lưu ý thiết kế: nếu code cần cleanup và cleanup có thể bị interrupt, hãy suy nghĩ kỹ **thứ tự restore flag**. Nếu restore quá sớm rồi gọi một phép chờ interruptible trong cleanup, phép chờ đó có thể ném ngay. Đừng chỉ copy-paste công thức mà không xem việc giải phóng tài nguyên cụ thể.

## 7. Ví dụ 3 — worker phản hồi hủy và chạy cleanup (Java 17+)

Ta sẽ dùng `CountDownLatch` để xác nhận worker đi vào vùng chờ và cleanup được thực hiện, không dùng `sleep` trong main để đoán thời điểm worker đã chạy.

Tạo tệp `CooperativeCleanupDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class CooperativeCleanupDemo {
    public static void main(String[] args)
            throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch cleaned = new CountDownLatch(1);
        AtomicBoolean cancellationObserved = new AtomicBoolean();
        AtomicBoolean flagRestored = new AtomicBoolean();

        Thread worker = new Thread(() -> {
            try {
                entered.countDown();
                for (;;) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException ex) {
                cancellationObserved.set(true);
                Thread.currentThread().interrupt();
                flagRestored.set(
                    Thread.currentThread().isInterrupted()
                );
            } finally {
                cleaned.countDown();
            }
        }, "cleanup-worker");

        worker.start();
        entered.await();

        worker.interrupt();
        worker.join();
        cleaned.await();

        System.out.println(
            "cancellation observed = " + cancellationObserved.get()
        );
        System.out.println("flag restored = " + flagRestored.get());
        System.out.println("cleanup reached = true");
        System.out.println("worker stopped = " + !worker.isAlive());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac CooperativeCleanupDemo.java
java CooperativeCleanupDemo
~~~

Stdout khi không có lỗi ngoại lai:

~~~text
cancellation observed = true
flag restored = true
cleanup reached = true
worker stopped = true
~~~

Phân tích:

1. Worker đếm `entered` trước khi tới vòng sleep. Caller chờ latch rồi mới gửi interrupt.
2. Nếu interrupt đến **trước** lần sleep đầu tiên, sleep nhận cờ đã đặt và ném `InterruptedException`; nếu đến **trong khi** sleep, lời chờ bị ngắt.
3. Worker ghi nhận đã quan sát cancellation, restore flag rồi đi vào `finally`, báo đã tới cleanup.
4. Main `join()` để chờ worker kết thúc rồi mới in các biến kết quả.
5. `AtomicBoolean` cho phép lưu các cờ kết quả an toàn trong ví dụ; `join()` còn cung cấp quan hệ happens-before đối với những hành động trước khi worker kết thúc.

**Không hiểu sai “cleanup reached”:** ví dụ chỉ chứng minh `finally` đã chạy và đếm latch, không giả vờ rằng file/network/database nào đó đã được đóng. Trong ứng dụng thực, đặt các thao tác giải phóng tài nguyên tại `finally` hoặc try-with-resources và xác định cách báo lỗi cleanup riêng.

### Cleanup khi có cả InterruptedException và exception khác

Nếu `close()` hoặc cleanup có thể ném exception khác, lỗi cleanup có thể **che** ngoại lệ ban đầu nếu không quản lý cẩn thận. Try-with-resources giúp giữ các exception bị suppress theo ngữ nghĩa Java phù hợp. Với giao thức hủy, cần bảo đảm hai thông tin không bị bỏ mất: yêu cầu cancel và lỗi dọn dẹp thật sự quan trọng.

## 8. Ví dụ 4 — BlockingQueue.take() và xử lý interrupt (Java 17+)

`BlockingQueue.take()` là primitive chờ có thể ngắt. Khi chưa có item, consumer nên chờ thay vì bận spin; khi bị hủy, consumer phải kết thúc hoặc trả cancellation theo hợp đồng.

Tạo tệp `InterruptibleQueueDemo.java`:

~~~java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterruptibleQueueDemo {
    public static void main(String[] args)
            throws InterruptedException {
        BlockingQueue<Integer> queue =
            new LinkedBlockingQueue<>();
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean noticed = new AtomicBoolean();
        AtomicBoolean restored = new AtomicBoolean();

        Thread consumer = new Thread(() -> {
            entered.countDown();

            try {
                Integer item = queue.take();
                System.out.println("item = " + item);
            } catch (InterruptedException ex) {
                noticed.set(true);
                Thread.currentThread().interrupt();
                restored.set(Thread.currentThread().isInterrupted());
            }
        }, "queue-consumer");

        consumer.start();
        entered.await();

        consumer.interrupt();
        consumer.join();

        System.out.println("noticed = " + noticed.get());
        System.out.println("restored = " + restored.get());
        System.out.println("queue empty = " + queue.isEmpty());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac InterruptibleQueueDemo.java
java InterruptibleQueueDemo
~~~

Stdout khi không có tác động ngoài ví dụ:

~~~text
noticed = true
restored = true
queue empty = true
~~~

Queue luôn trống và không có producer nào thêm phần tử; consumer không thể lấy item, nên câu lệnh in `item = ...` không chạy trong thực thi hợp lệ của ví dụ. Caller gửi interrupt sau khi consumer đã vào vùng xử lý. Nếu interrupt đến trước `take()`, `take()` nhận trạng thái đó và phản hồi bằng `InterruptedException`; nếu đến khi đang chờ, chờ bị ngắt.

Worker catch exception, ghi nhận yêu cầu hủy, khôi phục flag rồi rời khỏi task. Caller `join()` trước khi đọc kết quả. Đừng nhầm `Thread.interrupted()` (test-and-clear) với cách kiểm tra cờ trong catch; `Thread.currentThread().isInterrupted()` chỉ đọc và không xóa.

**Nếu queue có item và task được phép kết thúc bình thường trước yêu cầu cancel**, output có thể khác. Ví dụ cố ý không có producer để tập trung vào cơ chế interrupt của `take()`.

## 9. Không phải mọi phương thức blocking phản ứng giống nhau

| API/chỗ chờ | Phản ứng với interrupt điển hình | Điều cần nhớ |
| --- | --- | --- |
| `Thread.sleep()` | Ném `InterruptedException`, clear flag | Không nhả intrinsic monitor thread đang giữ |
| `Object.wait()` | Ném `InterruptedException`, clear flag và reacquire monitor trước khi thoát | Phải giữ đúng monitor; chờ trong `while` |
| `Thread.join()` | Thread **gọi join** có thể nhận `InterruptedException` | Không tự interrupt thread đang được join |
| `CountDownLatch.await()` | Ném `InterruptedException` | Không tự đặt count về 0 |
| `BlockingQueue.take()/put()` | Có thể ném `InterruptedException` khi chờ | Task xử lý state queue đúng contract |
| `Condition.await()` | Có thể ném `InterruptedException`, reacquire associated lock | Vẫn kiểm tra predicate trong while |
| `Semaphore.acquire()` | Có thể ném `InterruptedException` | Giải phóng permit đã acquire riêng |
| `ReentrantLock.lockInterruptibly()` | Chờ lấy lock có thể bị ngắt | Chỉ unlock nếu đã acquire |
| `ReentrantLock.lock()` | Không dừng chờ chỉ vì interrupt | Flag có thể còn để xử lý sau |
| Chờ **vào `synchronized`** | Không có `InterruptedException` chỉ vì chờ monitor | Interrupt không ép lấy hoặc release monitor |
| `Future.cancel(true)` | Yêu cầu hủy, có thể interrupt worker | Best effort, không bảo đảm task sạch ngay |
| `CompletableFuture.cancel(true)` | Theo API, tham số `mayInterruptIfRunning` không dùng để điều khiển thực thi | Không xem nó là yêu cầu interrupt tới underlying computation |

Các I/O API có ngữ nghĩa khác nhau. Một số interruptible channel trong NIO có thể bị đóng và phát sinh `ClosedByInterruptException`; một số loại socket, stream hoặc native blocking I/O khác có giới hạn riêng. Không hứa “mọi I/O đều thoát khi interrupt” nếu chưa kiểm tra chính xác Javadoc của tài nguyên đó.

### Vì sao sleep không phù hợp làm đồng bộ?

Caller ngủ 100ms rồi kiểm tra worker có còn chạy hay không không xác nhận worker đã đi vào điểm interruptible. Với test, dùng `CountDownLatch`, `CyclicBarrier`, `Future` hoặc kênh trạng thái rõ ràng. Ngược lại, `Thread.sleep` trong *worker* là một điểm chờ interruptible hợp lệ khi thật sự cần chờ thời gian.

## 10. Future.cancel(true): trạng thái hủy và lifecycle worker khác nhau

Khi dùng executor, caller thường không trực tiếp giữ `Thread` instance của worker. `Future.cancel(true)` yêu cầu hủy task, và nếu task đang chạy, có thể interrupt thread thực thi nó.

**Không đồng nhất:**

- `future.cancel(true) == true`: request hủy được Future chấp nhận theo hợp đồng API.
- `future.isCancelled() == true`: Future được đánh dấu hủy.
- **Code worker đã vào catch/cleanup**: một sự kiện thực thi khác.
- **Tài nguyên đã được giải phóng**: cần xác nhận theo API tài nguyên.
- **Một nghiệp vụ ngoài JVM đã rollback**: không được suy ra từ interrupt.

### Ví dụ 5 — Future cancel và xác nhận cleanup riêng (Java 17+)

Tạo tệp `FutureCancelCleanupDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FutureCancelCleanupDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean sawCancellation = new AtomicBoolean();

        try {
            Future<?> future = executor.submit(() -> {
                entered.countDown();
                try {
                    for (;;) {
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException ex) {
                    sawCancellation.set(true);
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });

            entered.await();
            boolean cancelAccepted = future.cancel(true);
            finished.await();

            System.out.println("cancel accepted = " + cancelAccepted);
            System.out.println("future cancelled = " + future.isCancelled());
            System.out.println("task caught interrupt = "
                + sawCancellation.get());
            System.out.println("cleanup reached = true");
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println("executor still shutting down");
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac FutureCancelCleanupDemo.java
java FutureCancelCleanupDemo
~~~

Stdout khi task và executor hoàn tất bình thường theo thiết kế:

~~~text
cancel accepted = true
future cancelled = true
task caught interrupt = true
cleanup reached = true
~~~

`entered` khiến main yêu cầu hủy khi task đã bắt đầu chạy. Task không tự hoàn thành nếu không có interrupt, vì vòng sleep là vô hạn. `cancel(true)` gửi yêu cầu hủy tới task đang chạy; `sleep` phản hồi bằng ngoại lệ. `finished.await()` quan sát đường code cuối của task, tách khỏi trạng thái Future.

Không dùng `future.get()` sau cancel để mong nhận kết quả tính toán bình thường: nó có thể ném `CancellationException` theo contract. `Future.isDone()` và `isCancelled()` không tự bảo đảm mọi side effect bên ngoài đã kết thúc.

### Executor shutdown

- `shutdown()` không nhận task mới và đợi các task cũ được xử lý theo cơ chế shutdown; **không yêu cầu interrupt tất cả task đang chạy**.
- `shutdownNow()` cố ngắt task đang chạy và trả về các task chưa bắt đầu; đó là **best effort**.
- `awaitTermination(timeout, unit)` cho caller đợi kết thúc executor trong một khoảng thời gian; timeout không bảo đảm mọi task đã dừng.

Một task nuốt `InterruptedException` có thể làm cả shutdown trở nên rất khó dự đoán, ngay cả khi caller đã gọi `shutdownNow()`.

## 11. Mẫu cleanup: khôi phục cờ ở đâu cho an toàn?

Khuyến nghị “catch rồi `Thread.currentThread().interrupt()`” rất hữu ích, nhưng thứ tự cleanup quan trọng.

### 11.1. Cleanup không cần chờ interruptible

~~~java
// Mẫu minh họa trong Runnable, không phải chương trình độc lập.
try {
    doBlockingWork();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
} finally {
    closeOwnedNonBlockingResources();
}
~~~

Khi cleanup chắc chắn không phụ thuộc một lời chờ interruptible và luôn xử lý được tài nguyên, mẫu này tương đối đơn giản. Nhưng nếu việc đóng tài nguyên có thể chặn hoặc ném exception, cần thiết kế thêm.

### 11.2. Cleanup cần một phép chờ interruptible

Nếu restore flag ngay, một `Thread.sleep`, `BlockingQueue.put` hoặc `Condition.await` trong cleanup có thể ném ngay vì flag đang set. Đôi khi cần **hoãn restore đến sau cleanup**:

~~~java
// Mẫu minh họa, không phải chương trình độc lập.
// Giả sử cleanup() không che lỗi nghiêm trọng và có giới hạn thời gian.
boolean interrupted = false;
try {
    doBlockingWork();
} catch (InterruptedException ex) {
    interrupted = true;
} finally {
    try {
        cleanup();
    } finally {
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
~~~

Mẫu trên đặt restore trong `finally` lồng nhau, nên kể cả khi `cleanup()` ném exception, flag vẫn được khôi phục. Tuy nhiên, **exception của cleanup có thể trở thành exception cuối cùng**: trong code thực cần báo/ghép lỗi theo API đang sử dụng. Nếu cleanup cũng bị interrupt, phải xét **cả hai tín hiệu** và chính sách retry/timeout thích hợp. Không cố chờ vô hạn chỉ để cleanup “cho xong”, đặc biệt khi ứng dụng đang shutdown.

### 11.3. Try-with-resources

Nếu thao tác mở tài nguyên được biểu diễn bằng `AutoCloseable`, try-with-resources giúp bảo đảm `close()` được gọi khi rời scope, kể cả lúc có exception. Exception từ `close()` có thể được ghi dưới dạng **suppressed exception** nếu đã có exception chính.

Dù vậy, try-with-resources **không tự restore interrupt status** sau `InterruptedException`; caller vẫn cần propagate hoặc xử lý cờ theo hợp đồng. Đối với tài nguyên bên ngoài, việc gọi `close()` cũng không phải lúc nào là hành động tức thời hoặc không thể lỗi.

## 12. Những bẫy nâng cao thường làm mất tín hiệu hủy

### 12.1. Catch Exception rộng và retry sai

~~~java
// SAI: mẫu minh họa, không phải chương trình độc lập.
while (true) {
    try {
        doBlockingWork();
        break;
    } catch (Exception ex) {
        log(ex);
        // Retry mọi lỗi, kể cả InterruptedException.
    }
}
~~~

`InterruptedException` là subclass của `Exception`, nên một catch rộng có thể vô tình nuốt tín hiệu. Nếu cần retry những lỗi tạm thời, **tách catch InterruptedException trước**:

~~~java
// Mẫu minh họa, không phải chương trình độc lập.
try {
    doBlockingWork();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
} catch (TransientFailure ex) {
    scheduleRetryIfAllowed(ex);
}
~~~

`TransientFailure` ở đây là loại exception nghiệp vụ giả định. Đừng coi ngắt là lỗi mạng để retry tự động, vì chính chủ sở hữu task có thể đang yêu cầu dừng.

### 12.2. Utility method trả default value sau interrupt

~~~java
// SAI nếu caller xem giá trị mặc định là kết quả hợp lệ.
String readValue() {
    try {
        return blockingRead();
    } catch (InterruptedException ignored) {
        return "";
    }
}
~~~

Trả chuỗi rỗng làm tầng gọi tưởng đọc thành công và có thể ghi dữ liệu sai. Nếu không thể throws, cần API diễn đạt rõ “đã hủy” như kết quả có trạng thái, callback hủy, unchecked cancellation phù hợp hoặc restore flag và dừng theo thiết kế.

### 12.3. Gọi Thread.interrupted() trong logging

~~~java
// Nguy hiểm nếu chỉ định quan sát:
boolean interrupted = Thread.interrupted();
~~~

Phương thức trên **xóa cờ current thread**. Nếu chỉ muốn hỏi có bị interrupt hay không, dùng `Thread.currentThread().isInterrupted()`. Không dùng `worker.interrupted()` để quan sát worker, vì đây vẫn là static method tác động đến current thread.

### 12.4. Catch InterruptedException rồi tiếp tục khóa

Khi một thread đợi `lockInterruptibly()` và bị interrupt, nó **không lấy được lock bằng lần chờ đó**. Không được gọi `unlock()` nếu chưa acquire. Mẫu đúng:

~~~java
// Mẫu minh họa, lock là ReentrantLock.
try {
    lock.lockInterruptibly();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
}
try {
    mutateSharedState();
} finally {
    lock.unlock();
}
~~~

Trong khi đó `ReentrantLock.lock()` và chờ **vào `synchronized`** không tự thoát chỉ vì có interrupt; việc restore flag không “phá khóa”. Cần lựa chọn primitive và thứ tự lock phù hợp từ đầu.

### 12.5. Interrupt khi đang giữ intrinsic monitor

`Thread.sleep(...)` **không** nhả monitor thread đang giữ. Một worker ngủ trong `synchronized(lock)` có thể làm thread khác chờ lock, dù bản thân nó bị interrupt và đi vào catch. Không thiết kế chờ I/O hoặc sleep dài trong critical section nếu có thể tránh.

`Object.wait()` thì nhả monitor của object dùng để wait, sau đó reacquire trước khi thoát. Khi `wait()` bị ngắt và ném `InterruptedException`, bạn vẫn phải kiểm tra và bảo vệ invariant trong vùng monitor tương ứng. Không suy luận mọi phương thức chờ đều có hành vi nhả khóa giống nhau.

### 12.6. Nuốt interrupt trong thư viện không thuộc quyền sở hữu

Một utility được gọi từ executor có thể không biết toàn bộ lifecycle của task. Nếu utility bắt `InterruptedException` rồi bỏ qua, caller có thể không còn cơ hội áp dụng chính sách hủy. Với thư viện dùng chung, mặc định nên **propagate** khi có thể; nếu API không cho, khôi phục cờ và báo status rõ ràng thay vì áp đặt tiếp tục.

### 12.7. Multiple interrupts và cờ boolean

Interrupt status là **một trạng thái**, không phải hàng đợi tích lũy số lần interrupt. Gọi `interrupt()` nhiều lần không tạo ra nhiều “token” tương ứng để tiêu thụ từng cái. `Thread.interrupted()` clear cờ một lần và không chứng minh chỉ có duy nhất một request từng xảy ra.

Nếu cần đếm yêu cầu, lưu lý do hủy, phân biệt deadline và user cancel, hoặc truyền nhiều sự kiện, hãy sử dụng **giao thức trạng thái riêng** như message queue, atomic state machine hoặc cancellation token chuyên biệt, không phụ thuộc vào số lần flag đổi.

## 13. Khi nào không restore flag?

Không nên biến nguyên tắc “restore flag” thành công thức máy móc trong mọi tầng:

1. **Method khai báo `throws InterruptedException`:** truyền exception lên thường đủ; không phải luôn restore trước khi throws.
2. **Tầng sở hữu quyết định hủy đã xử lý đầy đủ:** có thể chủ ý tiêu thụ tín hiệu nếu hợp đồng cho phép, nhưng phải giữ correctness và thông báo trạng thái phù hợp.
3. **Đã quyết định kết thúc task bằng exception/hủy rõ ràng:** việc restore thêm có thể hữu ích hoặc không tùy contract executor/tầng gọi; không dựa vào flag như kênh duy nhất.
4. **Cleanup cần chờ interruptible:** cân nhắc trì hoãn restore đến sau cleanup, tránh gây lỗi ngay tại lời chờ.
5. **Một exception khác đang xảy ra đồng thời:** quản lý thứ tự ưu tiên lỗi, suppressed exceptions và tính chất cancellation mà ứng dụng yêu cầu.

Mấu chốt là **đừng âm thầm đánh mất yêu cầu hủy**; truyền bằng exception, cờ, kết quả có trạng thái hoặc kênh phối hợp rõ ràng đều có thể là một phần thiết kế đúng nếu ownership nhất quán.

## 14. Kiểm thử để phát hiện mất tín hiệu hủy

### 14.1. Những kịch bản cần có

| Kịch bản | Kết quả mong đợi |
| --- | --- |
| Interrupt đến **trước** khi gọi sleep/take/await | Phép chờ phản hồi theo hợp đồng; task không tiếp tục như thường |
| Interrupt đến **trong lúc** chờ | Task nhận exception hoặc tín hiệu theo API |
| Catch không thể throws | Restore flag hoặc truyền trạng thái hủy rõ, không tiếp tục side effect |
| Utility method có `throws InterruptedException` | Caller nhận exception, không bị che bởi catch rộng |
| Executor `cancel(true)` | Task có cơ hội hợp tác và đạt cleanup, không chỉ Future state |
| Cleanup có exception | Không che mất thông tin quan trọng về cancel và lỗi cleanup |
| CPU-bound worker | Poll `isInterrupted()` giữa đơn vị công việc hữu hạn |
| Worker không chịu dừng | Test timeout, thu thập thread dump; không treo cả suite |
| Dữ liệu dùng chung | Invariant vẫn đúng sau hủy một phần công việc |
| I/O không phản ứng ngắt | Chính sách timeout/close theo API cụ thể |

### 14.2. Không dùng sleep để phỏng đoán lịch chạy

Sai lầm phổ biến là main tạo worker, ngủ 50ms rồi gửi interrupt, sau đó giả định worker chắc chắn đang ở `queue.take()`. Trên máy chậm hoặc CI bận, thứ tự có thể khác.

Dùng `CountDownLatch` hoặc primitive phối hợp để đánh dấu worker đã tới một điểm được xác định, rồi hủy. Tuy nhiên, một latch đặt ngay **trước** blocking operation chỉ bảo đảm worker đến vùng code đó, không chứng minh nó đã thực sự block; ví dụ trong bài được thiết kế để cả hai thời điểm interrupt trước và trong lời chờ đều dẫn tới một kết quả nhất quán.

### 14.3. Tách “future cancelled” khỏi “worker cleanup done”

Dùng hai điểm quan sát nếu nghiệp vụ cần cả hai:

- `future.isCancelled()` cho biết trạng thái hủy của Future theo contract.
- Một latch/kênh kết quả do worker phát ra sau cleanup cho biết code đã đạt mốc hoàn thành.

Không nhầm `Future.get()` bị `CancellationException` với bằng chứng tài nguyên bên ngoài đã được đóng. Nếu worker bỏ qua interrupt, nó có thể còn chạy dù Future đã bị hủy.

### 14.4. Quan sát production

- Log **lý do cancel**, thời điểm gửi yêu cầu, thời điểm worker nhận, và kết quả cleanup, thay vì chỉ log một cờ có thể clear.
- Đo thời gian từ khi yêu cầu hủy tới khi task hoàn tất; thống kê tỉ lệ task vượt deadline.
- Kiểm tra pool có shutdown đúng thời hạn, thread nào vẫn `RUNNABLE`/`WAITING`/`BLOCKED` và chúng chờ API nào.
- Với I/O, theo dõi timeout, đóng connection/channel và xử lý side effect bất định, thay vì giả định interrupt luôn có tác dụng ngay.
- Không cố sửa bằng `Thread.stop()`: ép dừng thread ở điểm bất kỳ có thể phá bất biến; API này không an toàn.

## 15. Checklist khi code review

- Code nào bắt `InterruptedException`? **Mỗi catch có một quyết định hủy rõ ràng** chưa?
- Method nào có thể `throws InterruptedException`? Có truyền lên thay vì tự ý nuốt không?
- Trong `Runnable`/callback không thể throws, có restore flag hoặc kênh trạng thái hủy phù hợp và **thoát** không?
- Có catch `Exception`/`Throwable` rộng nuốt tín hiệu của API blocking không?
- Có utility “đọc lỗi thì trả default” che mất `InterruptedException` không?
- Có lời gọi `Thread.interrupted()` ở chỗ chỉ định quan sát, làm clear flag ngoài ý muốn không?
- Có nhầm `worker.interrupted()` là truy vấn worker thay vì current thread không?
- Có retry loop tiếp tục chạy sau khi bị hủy không? Có kiểm tra `isInterrupted()` ở điểm phù hợp không?
- Cleanup có giữ lock quá lâu, thực hiện I/O vô hạn hoặc yêu cầu chờ interruptible sau khi đã restore flag không?
- `lockInterruptibly()` thất bại có thể dẫn tới `unlock()` sai không?
- Với Future, có phân biệt request cancel, trạng thái Future và hoàn tất side effect/cleanup không?
- Có timeout, interrupt, shutdown path và test để không mắc kẹt vô thời hạn không?
- Có xử lý trạng thái nghiệp vụ bị cập nhật dở khi hủy giữa một workflow không?
- Đã chọn API blocking có contract interrupt phù hợp hoặc một lối hủy riêng cho I/O chưa?

## 16. Câu hỏi tự kiểm tra

**1.** Vì sao đoạn `catch (InterruptedException ignored) {}` thường khiến worker không dừng?

Phép chờ thường đã clear interrupt flag khi ném exception. Catch rỗng làm mất tín hiệu và worker tiếp tục chạy nếu không có một cơ chế hủy khác.

**2.** Một helper có thể `throws InterruptedException`. Nên khôi phục cờ rồi mới ném lại không?

Thông thường helper chỉ cần **propagate** exception. Tầng caller nhận tín hiệu và có trách nhiệm quyết định. Restore trước khi throws không phải quy tắc bắt buộc, và có thể ảnh hưởng cleanup ở tầng trên.

**3.** Vì sao trong `Runnable.run()` thường dùng `Thread.currentThread().interrupt()` khi bắt `InterruptedException`?

`run()` không khai báo checked exception đó. Restore flag cho tầng khác có cơ hội quan sát yêu cầu, và task thường phải thoát/cancel theo hợp đồng.

**4.** Gọi `Thread.interrupted()` trong một câu log có nguy cơ gì?

Nó đọc **và xóa** interrupt flag của current thread. Nếu chỉ muốn quan sát mà không sửa trạng thái, dùng `Thread.currentThread().isInterrupted()`.

**5.** `Future.cancel(true)` trả true. Có chắc worker đã chạy cleanup và đóng file không?

Không. Trạng thái Future không đồng nghĩa toàn bộ code worker và side effect đã kết thúc. Dùng kênh xác nhận riêng nếu cần.

**6.** Sau khi restore flag, code bắt đầu gọi `BlockingQueue.take()` để đẩy thông điệp cleanup. Điều gì cần chú ý?

`take()` là interruptible; khi flag đã set, nó có thể ném `InterruptedException` ngay. Cần lập kế hoạch cleanup và chọn cách thông báo/đợi phù hợp trước khi restore hoặc tránh chờ dài trong cleanup.

**7.** Main bị interrupt khi đang chờ `worker.join()`. Thread nào nhận `InterruptedException`?

**Main**, tức thread đang gọi join. Worker không tự động nhận interrupt chỉ vì caller bị ngắt. Caller phải quyết định có gửi yêu cầu hủy riêng tới worker hay không.

**8.** Một thread chờ để vào `synchronized(lock)`. Gọi interrupt có ép nó dừng chờ monitor không?

Không. Việc chờ intrinsic monitor không phải một thao tác chờ interruptible như `ReentrantLock.lockInterruptibly()`. Interrupt có thể đặt cờ nhưng không tự lấy hoặc nhả monitor.

**9.** Có tình huống nào không restore flag là đúng không?

Có, nếu một tầng sở hữu hủy đã **chủ động xử lý hoàn toàn tín hiệu** theo hợp đồng rõ ràng hoặc đang propagate exception. Điều cấm kỵ là vô tình đánh mất yêu cầu mà bên khác vẫn dựa vào.

**10.** Cần chứng minh điều gì để nói một tác vụ đã được hủy an toàn?

Task đã nhận hoặc xử lý yêu cầu hủy theo contract, giữ được invariant, giải phóng tài nguyên cần thiết, có kết quả/trạng thái hủy rõ ràng và không còn thực hiện side effect trái phép sau khi hoàn tất. Chỉ có cờ Future hoặc lời gọi `interrupt()` chưa đủ.

**Ghi nhớ:** `InterruptedException` không phải một ngoại lệ phiền phức để catch rồi bỏ qua. Nó là tín hiệu điều khiển về yêu cầu hủy. Hãy **propagate** khi có thể; **restore interrupt status và thoát** khi không thể truyền tiếp; chỉ **tiêu thụ có chủ ý** nếu tầng hiện tại thật sự sở hữu chính sách hủy. Luôn phối hợp cleanup, invariant và xác nhận hoàn thành bằng cơ chế phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-and-cancellation-013">Vì sao tránh Thread.stop(), suspend() và resume(): rủi ro khóa và tính nhất quán</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao tránh Thread.stop(), suspend() và resume(): rủi ro khóa và tính nhất quán

Trong ứng dụng Java đa luồng, một yêu cầu “dừng công việc ngay”, “tạm dừng luồng” hoặc “cho luồng chạy tiếp” có thể làm thay đổi tính đúng đắn của cả hệ thống. Ba API cũ `Thread.stop()`, `Thread.suspend()` và `Thread.resume()` từng cho phép thread khác can thiệp trực tiếp vào luồng đang chạy mà không cần luồng mục tiêu tự chọn **điểm an toàn (safe point)**. Điều này nguy hiểm khi luồng đang giữ monitor, cập nhật nhiều trường trong cùng invariant, thao tác với tài nguyên hoặc đợi một tác vụ khác.

Giải pháp hiện đại không phải tìm API “kill thread nhanh hơn” mà là thiết kế **cooperative cancellation**, **pause/resume theo trạng thái**, **commit/rollback hợp lệ** và **xác nhận hoàn tất**. `Thread.interrupt()` và `Future.cancel(true)` là tín hiệu yêu cầu hủy chứ không phải công cụ tự động ép mọi dòng mã đang chạy dừng tức thời.

Bài này thuộc chủ đề **Interrupt & Hủy, Java Concurrency nói chung**. Nó trình bày semantics chính xác theo phiên bản JDK, nguyên nhân rủi ro khóa/tính nhất quán, thay thế an toàn, ví dụ Java 17+ chạy được và cách kiểm thử. Các cơ chế chuyên biệt của Spring nằm ngoài phạm vi.

## 1. Phân biệt dừng, tạm dừng và tiếp tục

| Yêu cầu | Ý nghĩa nghiệp vụ | Hợp đồng nên thiết kế |
| --- | --- | --- |
| **Stop/cancel** | Không tiếp tục công việc, giải phóng tài nguyên, cho caller nhận biết đã kết thúc. | Controller yêu cầu → worker nhận biết → kết thúc/rollback operation → cleanup → hoàn tất. |
| **Pause** | Giữ worker còn sống nhưng chưa bắt đầu bước mới trong khi đang tạm dừng. | Worker tự chờ tại safe point, không giữ lock nghiệp vụ mà controller hoặc thread khác cần. |
| **Resume** | Cho worker tiếp tục sau pause. | Cập nhật một predicate như `paused=false` rồi signal; worker kiểm tra trạng thái trước khi đợi. |
| **Timeout** | Caller không được chờ quá một khoảng xác định. | Hết deadline → trả trạng thái timeout/cancel request; không tự đồng nghĩa worker đã chết. |
| **Shutdown** | Hệ thống không nhận thêm việc, giải phóng và chờ các task đang chạy. | Chính sách owner cho executor, thread, queue, file/socket và data lifecycle. |

Sự khác nhau quan trọng là **một thread không chỉ chứa CPU instructions**: nó có thể sở hữu lock, tài nguyên, object graph chưa nhất quán và một transaction đang diễn ra. Controller ở bên ngoài thường không biết ranh giới nào có thể dừng an toàn.

## 2. Các API cũ đã thay đổi theo phiên bản JDK

Không thể viết rằng “`Thread.stop` luôn gây `ThreadDeath`” mà không nêu phiên bản Java. Tình trạng chính xác:

| API | JDK 17 (hành vi lịch sử) | Từ JDK 20 trở đi |
| --- | --- | --- |
| `Thread.stop()` | Deprecated; từng ép thread mục tiêu nhận `ThreadDeath` bất đồng bộ. Khi stack unwind, các monitor được nhả. | **Luôn ném `UnsupportedOperationException`** tại lời gọi; khả năng ép stop đã bị loại bỏ. |
| `Thread.suspend()` | Deprecated, for removal; từng đình chỉ một thread, kể cả khi nó đang giữ monitor. | **Luôn ném `UnsupportedOperationException`**. |
| `Thread.resume()` | Deprecated, for removal; từng tiếp tục một thread đã suspend. | **Luôn ném `UnsupportedOperationException`**. |

Chúng đã bị deprecated từ JDK 1.2 (1998). Đến JDK 20, khả năng stop/suspend/resume kiểu cũ bị loại bỏ; các method còn trong API để tương thích về ký hiệu nhưng ném exception. Vì vậy **không chạy thử các lời gọi này** như một chiến lược kiểm soát luồng. Trên JVM cũ, chúng có thể phá tính đúng đắn; trên JVM mới, chúng không còn thực hiện tác dụng mà code cũ mong muốn.

Tài liệu chính thức: [Thread JDK 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html), [Thread JDK 20](https://docs.oracle.com/en/java/javase/20/docs/api/java.base/java/lang/Thread.html), [Oracle JDK 20 Release Notes](https://www.oracle.com/java/technologies/javase/20-relnote-issues.html), [Java Thread Primitive Deprecation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/doc-files/threadPrimitiveDeprecation.html).

Tất cả ví dụ runnable ở các phần sau dùng Java 17+ **không gọi** ba API cũ. Các đoạn mô tả sai chỉ để phân tích failure mode, không phải code nên đưa vào sản phẩm.

## 3. Nguyên nhân Thread.stop() lịch sử làm hỏng dữ liệu

Giả sử hai trường đại diện cho hai tài khoản nội bộ, invariant là `accountA + accountB == 100`. Một operation chuyển 30 đơn vị:

~~~java
synchronized (lock) {
    accountA -= 30;
    accountB += 30;
}
~~~

Đây là mảnh code minh họa, giả định các field và `lock` đã được khai báo. Khi operation chạy đầy đủ và mọi reader/writer tuân thủ cùng monitor, hai bước có thể được quan sát như một thay đổi logic.

Nhưng trên JDK 17, `Thread.stop()` lịch sử có thể ép thread đang chạy ném `ThreadDeath` tại một điểm bất đồng bộ:

~~~text
Worker sở hữu monitor L              Controller / Observer
------------------------              ----------------------
lock(L)
accountA = 70
                                      stop(worker) theo semantics cũ
ThreadDeath khiến stack unwind
monitor L được nhả
accountB vẫn = 0
                                      observer lấy được L
                                      thấy A=70, B=0, tổng=70
~~~

Khi exception unwind stack, monitor bị nhả như quy tắc của `synchronized`. Đây là một đặc điểm tốt cho exception thông thường, nhưng cực kỳ nguy hiểm khi việc chấm dứt bị áp đặt giữa những phép ghi thuộc một invariant: **object bị công bố trong trạng thái dở dang**.

Điểm mấu chốt:

- **Mutual exclusion không tự tạo rollback.** Monitor ngăn hai thread chen ngang khi đang được giữ, nhưng không hoàn tác các assignment khi thread sở hữu thoát bất thường.
- **Happens-before không sửa dữ liệu sai.** Unlock → lock sau đó có thể giúp reader thấy những phép ghi đã xảy ra, kể cả một cặp trạng thái đã bị hỏng.
- **Lỗi có thể xuất hiện muộn.** Một object dở dang có thể tiếp tục được sử dụng và chỉ gây lỗi ở operation khác rất lâu sau lần stop.
- **Ngoại lệ bị chèn không đúng điểm nghiệp vụ.** Code không thể dựa vào giả định “sau validation thì các phép gán primitive không ném exception” khi cơ chế bên ngoài có thể ngắt tại bất kỳ chỗ không được thiết kế.

### 3.1 Vì sao finally không phải phép chữa tự động?

~~~java
synchronized (lock) {
    try {
        accountA -= 30;
        accountB += 30;
    } finally {
        closeTemporaryResource();
    }
}
~~~

Đây là mảnh giả định `closeTemporaryResource()` tồn tại. `finally` có thể chạy khi stack unwind, nhưng nó **không tự biết** bước nào của nghiệp vụ đã commit và không có cách chung để đảo:

- Các phép ghi vào nhiều object;
- Một message đã phát hoặc email đã gửi;
- Lệnh gọi remote đã thành công nhưng caller chỉ thấy timeout;
- Một file đã ghi được một phần;
- Callback hoặc state machine khác đã quan sát dữ liệu trung gian.

Kể cả tự viết rollback, rollback có thể gặp lỗi hoặc đua với các thread khác. Trong JVM cũ, việc cố bắt `ThreadDeath` cũng không tạo chiến lược chung an toàn; `ThreadDeath` kế thừa `Error`, thường không bị bắt bởi `catch (Exception)` và có thể bị code `catch (Throwable)` xử lý sai.

**Kết luận:** không có phương pháp tổng quát để ép thread dừng tại bất kỳ instruction nào nhưng vẫn bảo đảm mọi invariant và tài nguyên còn đúng.

## 4. Vì sao Thread.suspend() và resume() lịch sử dễ deadlock?

Trái với `stop()`, cơ chế `suspend()` trước đây **không nhả những monitor mà thread đang sở hữu**. Một thread bị đình chỉ có thể đang giữ lock của queue, cache, bộ đếm, class initialization hoặc cấu trúc dữ liệu chia sẻ. Tất cả contender của monitor ấy phải đợi.

~~~text
Thread A / worker                     Thread B / controller
-----------------                     ---------------------
synchronized (L) {
    ... giữ monitor L ...
}
A bị suspend khi vẫn giữ L
                                      synchronized (L) {
                                          // muốn gọi resume(A)
                                      }
                                      B BLOCKED chờ L

A không tiến triển vì bị suspend.
B không lấy được L để gửi resume.
=> Vòng phụ thuộc; deadlock / ứng dụng "đóng băng".
~~~

Đây là **sơ đồ phân tích lịch sử**, không phải code cần thực thi.

Rủi ro không chỉ có hai thread trong sơ đồ. Worker có thể giữ nhiều monitor của thư viện mà controller không biết; pause một thread vào thời điểm không kiểm soát có thể chặn cả thread đang cố thu hồi tài nguyên.

### 4.1 Resume sớm không phải tín hiệu được lưu

Nếu controller gửi một lệnh “resume” khi worker chưa suspended, điều đó không đồng nghĩa cấp một permit bền vững cho lần suspend tương lai. Một lần suspend xảy ra sau đó vẫn có thể khiến worker đứng im, không có lần resume tiếp theo.

Một giao thức pause/resume an toàn phải **lưu trạng thái mong muốn** vào predicate như `paused`, rồi thread tự kiểm tra và chờ. Tín hiệu signal chỉ để đánh thức thread **đang chờ**; **state** mới là nguồn sự thật cho thread đến sau.

### 4.2 Không dùng busy waiting thay suspend

~~~java
while (paused) {
    // Poll liên tục, không làm việc hữu ích.
}
~~~

Đây là mảnh mã không nên dùng cho pause lâu. Nếu `paused` là field thường, có thể thiếu visibility; nếu volatile, thread vẫn chiếm CPU khi spin. Chọn `Condition.await()` hoặc `Object.wait()` để worker nhả đúng lock điều khiển và block trong lúc chờ.

## 5. Cooperative cancellation: controller yêu cầu, worker tự kết thúc

Thay vì cưỡng ép dừng thread, hãy thiết kế một vòng đời hợp tác:

~~~text
Controller                        Worker
----------                        -------------------------
request stop / interrupt()
       |                           làm việc ở current unit
       |-------------------------> kiểm tra yêu cầu ở safe point
                                   hoàn tất/rollback operation
                                   đóng resource đang sở hữu
                                   thoát run()/call()
caller join()/awaitTermination()   TERMINATED / task completed
~~~

Một **safe point ở cấp ứng dụng** là ranh giới nơi task có thể quyết định không làm thêm việc mà không phá invariant. Nó không phải “JVM safepoint” nội bộ cho GC.

Cách triển khai:

1. Controller ghi nhận yêu cầu dừng bằng `interrupt()`, flag `volatile`/`AtomicBoolean` hoặc state được bảo vệ bởi lock.
2. Worker kiểm tra yêu cầu ở những ranh giới đủ thường xuyên (giữa các unit of work).
3. Nếu đang chờ API interruptible, controller dùng `interrupt()` để đánh thức theo hợp đồng API ấy.
4. Worker hoàn tất/rollback transaction đang dở và đóng resource trong `finally`/`try-with-resources`.
5. Worker thoát; controller `join()` hoặc kiểm tra completion của task.
6. Nếu worker không hợp tác, có deadline, log chẩn đoán và cách cô lập task, **không gọi stop cũ**.

Cơ chế này có thể không dừng tức thì, nhưng **cho phép chọn ranh giới an toàn**. Một hệ thống yêu cầu “kill ngay code không tin cậy” thường cần **process isolation** và policy thu hồi process/resource bên ngoài thread.

## 6. Thread.interrupt(): tín hiệu, không phải thao tác kill

`Thread.interrupt()` đặt trạng thái interruption hoặc làm một số blocking API phản ứng bằng exception. Nó **không trực tiếp kết thúc Java thread** đang tính toán.

| Trạng thái của worker | Hành vi khi interrupt | Cần xử lý |
| --- | --- | --- |
| CPU-bound loop | Interrupted status được đặt; worker chỉ dừng khi tự kiểm tra. | `isInterrupted()`/flag ở safe point. |
| `Thread.sleep`, `Object.wait`, `Thread.join` | Có thể ném `InterruptedException`, thường xóa interrupted status lúc ném. | Propagate hoặc restore rồi thoát. |
| `CountDownLatch.await`, `BlockingQueue.take` | Có thể ném `InterruptedException`. | Cleanup theo contract, không nuốt lỗi. |
| `ReentrantLock.lockInterruptibly()` | Có thể bỏ chờ lấy lock khi bị interrupt. | Không unlock nếu chưa acquire. |
| `synchronized` đang `BLOCKED` chờ monitor | Không có interruptible acquisition tương đương. | Không kỳ vọng thoát chờ monitor ngay. |
| I/O | Tùy channel, stream, driver và platform. | Dùng timeout hoặc đóng resource theo hợp đồng API cụ thể. |

Không hứa rằng `interrupt()` sẽ ngắt mọi thao tác I/O ngay. Một số API hỗ trợ cancellation bằng interruption, một số cần đóng socket/channel, và một số có hành vi phức tạp.

### 6.1 isInterrupted() so với Thread.interrupted()

~~~java
boolean flag1 = Thread.currentThread().isInterrupted();
boolean flag2 = Thread.interrupted();
~~~

Mảnh code trên không độc lập. `isInterrupted()` đọc status **không xóa flag**. `Thread.interrupted()` là static method, đọc rồi **xóa flag của current thread**. Việc dùng `Thread.interrupted()` trong loop mà không lưu kết quả có thể vô tình xóa thông tin hủy.

Để hủy CPU loop:

~~~java
while (!Thread.currentThread().isInterrupted()) {
    doOneBoundedStep();
}
~~~

Đây là mảnh code giả định `doOneBoundedStep()` có thời gian hữu hạn và không tự phá invariant. Nếu mỗi bước có thể chạy hàng giờ, cancellation latency vẫn cao.

## 7. Ví dụ Java 17+: interrupt worker đang chờ

Tạo file `CooperativeCancelDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;

public final class CooperativeCancelDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            try {
                entered.countDown();
                neverReleased.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Cancel observed");
            } finally {
                System.out.println("Cleanup executed");
            }
        }, "worker");

        worker.start();
        entered.await();
        worker.interrupt();
        worker.join();

        System.out.println("Alive: " + worker.isAlive());
    }
}
~~~

Lệnh JDK 17+:

~~~bash
javac CooperativeCancelDemo.java
java CooperativeCancelDemo
~~~

Stdout nếu không có tác nhân ngoài can thiệp:

~~~text
Cancel observed
Cleanup executed
Alive: false
~~~

Giải thích:

- `entered.countDown()` báo mốc worker đã bắt đầu; main không cần sleep để đoán thời gian.
- Main có thể interrupt trước **hoặc sau** khi worker thực sự vào `neverReleased.await()`. Cả hai trường hợp đều được blocking wait nhận biết.
- `InterruptedException` là đường thoát hợp tác; worker restore flag bằng `Thread.currentThread().interrupt()` và ra khỏi task.
- `finally` hoàn tất trước khi thread kết thúc; main `join()` nên in trạng thái `Alive: false` sau các dòng worker.
- Trong sản phẩm, thay `System.out.println("Cleanup executed")` bằng cleanup resource thật: đóng file/socket/transaction handle theo hợp đồng. In log không tự là cleanup.

Không dùng thử `Thread.stop()` để “so sánh” trong chương trình: nó không có lợi ích học tập nào đáng với nguy cơ trên JDK cũ và ném exception ở JDK mới.

## 8. Cách xử lý InterruptedException đúng

Có hai lựa chọn thường gặp:

**Truyền tiếp exception** nếu method có thể khai báo:

~~~java
void runBlockingTask() throws InterruptedException {
    queue.take();
}
~~~

Mảnh minh họa giả định `queue` đã khai báo. Caller sở hữu task có thể quyết định shutdown/cancel tiếp.

**Khôi phục interrupted status rồi thoát** khi đang trong `Runnable.run()` không khai báo checked exception:

~~~java
try {
    blockingOperation();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
~~~

Mảnh này nằm trong method trả `void`, giả định `blockingOperation()` được định nghĩa. Nhiều blocking API xóa interrupted flag lúc ném `InterruptedException`, nên restore flag cho phép tầng cleanup/caller biết yêu cầu hủy từng xuất hiện.

Sai lầm phổ biến:

~~~java
try {
    blockingOperation();
} catch (InterruptedException ignored) {
    // Bỏ qua rồi tiếp tục chạy.
}
~~~

Đoạn này làm cancellation mất hiệu lực nếu không có protocol khác. Nếu task đã quyết định **thật sự xử lý xong** interruption và tiếp tục, phải có lý do nghiệp vụ và trạng thái rõ ràng; đừng âm thầm swallow.

## 9. Không kiểm tra cờ hủy giữa một operation phải atomic

Giả sử chuyển tiền A→B:

~~~java
synchronized (lock) {
    accountA -= amount;
    if (Thread.currentThread().isInterrupted()) {
        return; // Sai: đã trừ A nhưng chưa cộng B.
    }
    accountB += amount;
}
~~~

Đây là **mảnh lỗi**, giả định method trả `void` và các biến có sẵn. Mặc dù `synchronized` tự nhả monitor khi return, state sau đó đã sai invariant. **Cooperative cancellation cũng có thể sai** nếu chọn safe point giữa hai bước thuộc cùng một transaction.

Mẫu thiết kế tốt hơn:

~~~text
kiểm tra cancel
      |
acquire monitor
validate input và state
tính nextA, nextB
commit A và B trong một critical section ngắn
release monitor
      |
kiểm tra cancel trước unit tiếp theo
~~~

Một critical section không chứa blocking operation và có thời gian nhỏ thường không cần ngắt giữa chừng. Nếu phải gọi API có thể throw hoặc block bên trong, cần chuẩn bị rollback hoặc cấu trúc lại work boundary. `finally` nhả lock **không tự rollback** các phép ghi đã thực hiện.

## 10. Ví dụ Java 17+: dừng ở ranh giới giao dịch an toàn

Tạo `SafeCommitCancelDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;

public final class SafeCommitCancelDemo {
    private record Snapshot(long a, long b) {
        long total() {
            return Math.addExact(a, b);
        }
    }

    private static final class Ledger {
        private final Object lock = new Object();
        private long a = 100;
        private long b;

        void transfer(long amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount");
            }
            synchronized (lock) {
                if (a < amount) {
                    throw new IllegalArgumentException("insufficient");
                }
                long nextA = a - amount;
                long nextB = Math.addExact(b, amount);
                a = nextA;
                b = nextB;
            }
        }

        Snapshot snapshot() {
            synchronized (lock) {
                return new Snapshot(a, b);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Ledger ledger = new Ledger();
        CountDownLatch committed = new CountDownLatch(1);
        CountDownLatch afterCommitWait = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            try {
                ledger.transfer(30);
                committed.countDown();
                afterCommitWait.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "transfer-worker");

        worker.start();
        committed.await();
        worker.interrupt();
        worker.join();

        Snapshot s = ledger.snapshot();
        System.out.println("A: " + s.a());
        System.out.println("B: " + s.b());
        System.out.println("Total: " + s.total());
        System.out.println("Alive: " + worker.isAlive());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac SafeCommitCancelDemo.java
java SafeCommitCancelDemo
~~~

Stdout:

~~~text
A: 70
B: 30
Total: 100
Alive: false
~~~

**Vì sao an toàn trong phạm vi ví dụ?**

- `ledger.transfer(30)` tính số dư mới, kiểm tra `Math.addExact` trước khi sửa state, rồi commit cả hai field dưới cùng monitor.
- `committed.countDown()` chỉ được phát sau khi method transfer kết thúc. Main đợi mốc này rồi mới gửi interruption.
- Worker xử lý interruption khi đang hoặc sắp chờ `afterCommitWait.await()`, bên ngoài critical section. Không rời đi giữa debit và credit.
- Main gọi `join()` trước khi đọc `snapshot()`. Snapshot cũng lấy cùng lock bảo vệ state.
- Dữ liệu cuối thỏa invariant `A+B=100`, không phụ thuộc thứ tự scheduling giữa yêu cầu interrupt và lời gọi await.

Ví dụ này là **in-memory transaction**, không phải database transaction. Nếu debit/credit là các external service calls, cần giao thức dữ liệu/transaction và idempotency tương ứng.

## 11. Pause/resume hợp tác bằng Condition và predicate

Một thread nên tự kiểm tra **pause flag** tại safe point, rồi chờ nếu controller yêu cầu. Cơ chế `ReentrantLock` + `Condition` phù hợp vì:

- Predicate `paused/stopped` được bảo vệ bởi lock.
- `Condition.await()` nhả **lock của Condition** trong lúc chờ, rồi reacquire trước khi trả về.
- `signalAll()` thức những waiter đang chờ, nhưng **không lưu signal** cho waiter đến sau; predicate lưu trạng thái mới.
- Vòng `while` kiểm tra lại điều kiện sau spurious wakeup hoặc khi có thay đổi trạng thái.
- `stopRequested` có ưu tiên hơn pause, tránh worker bị kẹt ở paused khi cần shutdown.

Mẫu state machine:

~~~text
RUNNING --requestPause--> PAUSE_REQUESTED
PAUSE_REQUESTED --worker reaches safe point--> PAUSED
PAUSED --requestResume--> RUNNING
RUNNING/PAUSED --requestStop--> STOP_REQUESTED
STOP_REQUESTED --worker cleanup--> TERMINATED
~~~

`pause()` trả về không có nghĩa worker đã **acknowledge** rằng nó đang PAUSED. Nếu application cần mạnh hơn (“worker đã ra khỏi tất cả critical section”), phải thiết kế acknowledgement riêng.

## 12. Ví dụ Java 17+: SafePauseResumeDemo

Tạo file `SafePauseResumeDemo.java`:

~~~java
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class SafePauseResumeDemo {
    private static final class Gate {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition changed = lock.newCondition();
        private boolean paused;
        private boolean stopped;

        void pause() {
            lock.lock();
            try {
                if (!stopped) {
                    paused = true;
                }
            } finally {
                lock.unlock();
            }
        }

        void resume() {
            lock.lock();
            try {
                paused = false;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }

        void stop() {
            lock.lock();
            try {
                stopped = true;
                paused = false;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }

        boolean awaitRunning() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (paused && !stopped) {
                    changed.await();
                }
                return !stopped;
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Gate first = new Gate();
        first.pause();
        first.resume(); // Signal trước khi worker đến.

        Thread early = new Thread(() -> {
            try {
                if (first.awaitRunning()) {
                    System.out.println("Early resume preserved");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        early.start();
        early.join();

        Gate second = new Gate();
        second.pause();
        Thread cancelled = new Thread(() -> {
            try {
                if (!second.awaitRunning()) {
                    System.out.println("Stop while paused");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        cancelled.start();
        second.stop(); // Có thể trước hoặc sau await.
        cancelled.join();

        System.out.println("Done");
    }
}
~~~

Chạy JDK 17+:

~~~bash
javac SafePauseResumeDemo.java
java SafePauseResumeDemo
~~~

Stdout:

~~~text
Early resume preserved
Stop while paused
Done
~~~

**Giải thích:**

1. Với gate thứ nhất, `resume()` chạy **trước** khi worker tới. State `paused=false` được lưu; worker đọc predicate và không chờ. Không có “lost resume”.
2. Với gate thứ hai, worker bắt đầu trong trạng thái `paused=true`. Dù controller `stop()` được thực thi trước hay sau khi worker gọi await, `stopped=true` vẫn được lưu và `signalAll` đánh thức đúng khi cần. Worker trả `false`.
3. `awaitRunning()` dùng `while (paused && !stopped)` để chống spurious wakeup và race khi nhiều controller thay state.
4. Gate dùng `lockInterruptibly()` khi acquire lock; nếu worker bị interrupt, nó có thể thoát khỏi chờ acquisition theo contract.
5. `stop()` ở đây là **method của ứng dụng Gate**, không phải `Thread.stop()`. Nó chỉ thay trạng thái, hoàn toàn không cưỡng bức dừng thread.

**Giới hạn:** ví dụ mô tả một gate cho một task, chưa có acknowledgement thực sự đã pause, chưa có job queue, và không có resource cleanup phức tạp. Trong một worker loop, hãy đặt `gate.awaitRunning()` ở ranh giới **trước unit of work**, không gọi khi đang giữ một monitor nghiệp vụ khác.

## 13. Object.wait()/notifyAll() là một lựa chọn khác

Không nhất thiết phải dùng `ReentrantLock`. Một controller dựa trên intrinsic monitor cũng hợp lệ:

~~~java
private final Object controlLock = new Object();
private boolean paused;
private boolean stopped;

void requestPause() {
    synchronized (controlLock) {
        if (!stopped) {
            paused = true;
        }
    }
}

void requestResume() {
    synchronized (controlLock) {
        paused = false;
        controlLock.notifyAll();
    }
}

void requestStop() {
    synchronized (controlLock) {
        stopped = true;
        paused = false;
        controlLock.notifyAll();
    }
}

boolean awaitPermission() throws InterruptedException {
    synchronized (controlLock) {
        while (paused && !stopped) {
            controlLock.wait();
        }
        return !stopped;
    }
}
~~~

Đây là **mảnh code để đặt trong một class**, không có `main`. Cùng một monitor bảo vệ tất cả predicate và tín hiệu, nên “resume trước wait” không mất state. `Object.wait()` chỉ nhả **monitor controlLock**, không nhả bất kỳ monitor khác mà worker đang sở hữu. Vì vậy tuyệt đối tránh:

~~~java
synchronized (businessLock) {
    gate.awaitPermission(); // Có thể pause nhưng vẫn giữ businessLock!
}
~~~

Đây là mảnh code **nguy hiểm**: worker chờ ở gate nhưng vẫn giữ lock nghiệp vụ, khiến các thread khác có thể bị block vô hạn. Đặt pause gate **ngoài** critical section nghiệp vụ:

~~~java
while (gate.awaitPermission()) {
    doOneCompleteBusinessOperation();
}
~~~

Hai mảnh giả định `gate` và các object đã được khai báo; method xử lý `InterruptedException`.

## 14. Future.cancel(true) quản lý task, không giết worker

Trong `ExecutorService`, worker thread thuộc executor; task được biểu diễn bởi `Future`. Nên yêu cầu hủy task thay vì điều khiển Thread của pool trực tiếp.

~~~java
Future<?> future = executor.submit(task);
boolean cancelled = future.cancel(true);
~~~

Đây là mảnh mã minh họa. `cancel(true)` yêu cầu hủy, có thể interrupt worker đang thực thi; `Future` chuyển sang trạng thái cancelled nếu lệnh hủy thành công theo contract. **Future cancelled không chứng minh mọi dòng user code đã dừng ngay.** Task bỏ qua interruption có thể tiếp tục chạy.

### 14.1 Ví dụ Java 17+: task nhận cancellation

Tạo `FutureCancelDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class FutureCancelDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch blocker = new CountDownLatch(1);

        try {
            Future<?> future = executor.submit(() -> {
                try {
                    entered.countDown();
                    blocker.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            entered.await();
            boolean requested = future.cancel(true);
            System.out.println("Cancel accepted: " + requested);
            System.out.println("Future cancelled: " + future.isCancelled());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
~~~

Chạy:

~~~bash
javac FutureCancelDemo.java
java FutureCancelDemo
~~~

Stdout khi không có external interruption:

~~~text
Cancel accepted: true
Future cancelled: true
~~~

Task đã vào runnable và chờ trên latch, nên trước lúc hủy nó chưa tự hoàn tất. `cancel(true)` đánh dấu hủy và yêu cầu interruption. Task nhận `InterruptedException`, restore flag rồi thoát. `shutdownNow()` trong finally vẫn là **best effort**; `awaitTermination` có deadline 5 giây nhưng ví dụ không giả vờ rằng mọi task không hợp tác đều phải dừng.

Không can thiệp trực tiếp `Thread.stop()` vào worker của executor: có thể phá state của task hoặc pool trên JVM cũ, và trên JDK 20+ lời gọi không còn có chức năng đó.

## 15. Executor shutdown: lifecycle riêng của tập hợp task

Một ứng dụng sở hữu executor cần quy trình shutdown:

- `shutdown()` ngừng nhận task mới, cho các task đã chấp nhận tiếp tục.
- `awaitTermination(timeout, unit)` đợi tới khi executor terminated; trả `false` nếu hết thời gian mà chưa hoàn tất.
- `shutdownNow()` cố gắng hủy task đang chạy (thường qua interruption), trả các task chưa được thực thi trong queue; **không bảo đảm dừng mọi task**.
- `Future.cancel(true)` nhắm tới một task, không tương đương tắt executor.
- `Thread.interrupt()` nhắm tới một Thread do application sở hữu, không thay shutdown của pool.

Mẫu điển hình:

~~~java
executor.shutdown();
try {
    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            System.err.println("Executor did not terminate");
        }
    }
} catch (InterruptedException e) {
    executor.shutdownNow();
    Thread.currentThread().interrupt();
}
~~~

Đây là mảnh code minh họa, giả định `executor` là `ExecutorService` và đã import `TimeUnit`. Timeout là ví dụ API, không phải SLA chung. Nếu một task kẹt trong I/O không có timeout hoặc không hợp tác với interruption, executor vẫn có thể không dừng ngay.

## 16. Vì sao timeout không đồng nghĩa terminate?

Các API có timeout **giới hạn thời gian caller đợi**, không tự ép thread mục tiêu biến mất:

| API | Khi hết thời gian chờ | Task/Thread mục tiêu |
| --- | --- | --- |
| `Thread.join(timeout)` | Trả về, cần kiểm tra `isAlive()`. | Có thể vẫn đang chạy. |
| `Future.get(timeout, unit)` | Ném `TimeoutException`. | Có thể vẫn đang chạy, chưa bị cancel. |
| `Condition.await(timeout, unit)` | Báo trạng thái hết hạn theo overload. | Producer/worker khác không bị dừng. |
| `ReentrantLock.tryLock(timeout, unit)` | Trả false nếu không lấy được lock. | Holder tiếp tục giữ lock nếu chưa nhả. |
| `ExecutorService.awaitTermination(timeout, unit)` | Trả false nếu executor chưa terminated. | Worker có thể vẫn hoạt động. |

Nếu contract yêu cầu deadline, có thể dùng các bước **timeout → cancel request → chờ cleanup thêm có giới hạn → báo trạng thái**. Không tuyên bố “đã dừng” chỉ vì đã gửi interrupt hoặc cancel.

## 17. Khi interrupt chưa có hiệu lực: tìm đúng nguyên nhân

### 17.1 CPU loop không kiểm tra trạng thái

~~~java
while (true) {
    computeNext();
}
~~~

Nếu `computeNext()` không tự kiểm tra cancellation và không ném exception, `interrupt()` chỉ đặt flag. Thay bằng bounded units và check:

~~~java
while (!Thread.currentThread().isInterrupted()) {
    computeOneBoundedUnit();
}
~~~

Đây là mảnh code minh họa.

### 17.2 Đợi vào intrinsic monitor

Thread đang `BLOCKED` để vào `synchronized(lock)` **không có interruptible acquisition** như `ReentrantLock.lockInterruptibly()`. Nếu cancellation khi đợi lock là yêu cầu thật, cân nhắc đổi primitive, giữ nguyên invariants/lock ordering.

### 17.3 I/O không phản hồi interruption

Socket, channel, file I/O, native library và các driver khác nhau có semantics riêng. Có API phản hồi bằng exception, có API cần đóng resource để đánh thức, có API chỉ hỗ trợ timeout. Dựa vào tài liệu của **đúng I/O API**. Không hứa “mọi I/O bị interrupt là dừng ngay”.

### 17.4 Nuốt InterruptedException

~~~java
try {
    queue.take();
} catch (InterruptedException ignored) {
    // Bỏ qua rồi tiếp tục làm việc.
}
~~~

Đây là mảnh lỗi. Nếu task lặp lại vô hạn, cancellation request có thể bị bỏ mất. Propagate hoặc restore flag và thoát ở ranh giới hợp lệ.

### 17.5 Pause trong khi đang giữ lock khác

`Condition.await()` chỉ nhả lock **liên kết với Condition**, không nhả một `businessLock` khác. Nếu worker gọi gate khi giữ businessLock, mọi waiter của businessLock có thể bị chặn tới lúc resume. Đưa gate ra ngoài critical section.

## 18. Invariant, exception safety và tính nhất quán

Để dừng an toàn, phải xác định những state nào được phép publish:

| State | Invariant cần giữ | Điểm hủy an toàn |
| --- | --- | --- |
| Hai tài khoản A/B | `A + B == total`, không âm | Trước transfer hoặc sau khi hai số dư đã commit. |
| Đơn hàng và thanh toán | `PAID` chỉ khi có kết quả thanh toán hợp lệ | Sau transaction/state-machine transition đã được chứng minh. |
| Queue và bộ đếm | Phần tử trong queue/counter khớp contract | Sau operation đồng bộ trên queue/counter. |
| File đích | Không công bố file mới chỉ được ghi một phần | Ghi file tạm, validate, publish qua thao tác phù hợp filesystem. |
| Tài nguyên lock | Không giữ lock khi task đã dừng | Nhả trong `finally` khi đã acquire; không tự dừng giữa bất kỳ instruction. |
| Callback ngoài | Không gọi code không kiểm soát khi đang giữ lock quan trọng | Chụp snapshot dưới lock rồi callback bên ngoài với protocol ordering rõ. |

Một exception đồng bộ có thể được phòng tránh bằng **validate/calculate trước commit**, ví dụ gọi `Math.addExact` trước khi gán hai field. `Thread.stop()` lịch sử phá giả định đó vì có thể xen vào giữa các phép gán mà tác giả không dự đoán.

Trong hệ thống nhiều process/database, lock JVM không tạo giao dịch bền vững; cần transaction, versioning, idempotency/compensation theo failure model riêng. Đừng suy từ `synchronized` rằng external side effect đã rollback.

## 19. Visibility và happens-before của cờ hủy

Một thread có thể đặt flag:

~~~java
private volatile boolean stopRequested;

void requestStop() {
    stopRequested = true;
}

void runLoop() {
    while (!stopRequested) {
        doOneBoundedUnit();
    }
}
~~~

Đây là mảnh class minh họa. `volatile` thiết lập semantics memory visibility/ordering của Java Memory Model; worker có thể quan sát thay đổi flag đúng theo quan hệ **happens-before** phù hợp khi đọc volatile. Nếu chỉ dùng field thường không đồng bộ, có thể không có bảo đảm visibility cần thiết.

Tuy nhiên `volatile` **không đánh thức** một thread đang block trong `CountDownLatch.await()` hoặc `BlockingQueue.take()`. Nếu cần cancellation trong lúc chờ:

~~~java
stopRequested = true;
worker.interrupt();
~~~

Đây là mảnh code minh họa; phải có policy rõ để một nguồn stop request và interrupt không mâu thuẫn. Nếu đang paused trên Condition, controller phải cập nhật `stopped=true` và `signalAll()` trong đúng lock để worker tỉnh dậy/thoát.

Phân biệt ba tầng:

1. **Visibility:** worker thấy có yêu cầu dừng.
2. **Wakeup:** worker thoát khỏi wait để kiểm tra yêu cầu.
3. **Safe completion:** worker giữ invariant và giải phóng resource.

Mỗi tầng có thể cần một cơ chế khác nhau.

## 20. Race giữa pause, resume và stop

Khi nhiều controller có thể ra lệnh cùng lúc, phải xác định thứ tự và ưu tiên:

- `pause()` trong lúc worker đang commit chỉ **yêu cầu pause ở safe point tiếp theo**; không cắt ngang operation.
- `resume()` đến trước `await()` phải được nhớ bằng predicate `paused=false`, không dựa vào một signal không lưu.
- `stop()` trong lúc worker đang paused phải đặt `stopped=true` và `signalAll()` để worker thoát.
- `resume()` đến **sau stop** không được xóa `stopped=true`; task không được “hồi sinh” do tín hiệu resume.
- Nếu hai controller cùng pause/resume, lock điều khiển xác định thứ tự các phép thay state. Muốn guarantee thêm fairness/acknowledgement phải thiết kế riêng.

Nếu API cần “pause xong mọi worker đã thực sự đứng yên”, không thể chỉ dùng request flag. Cần worker gửi **acknowledgement** khi đã tới safe point và không giữ business lock. Có thể dùng latch/Condition riêng để controller chờ xác nhận với timeout.

## 21. Deadlock, starvation và task không hợp tác

Không nên dùng stop cũ để xử lý một thread bị kẹt. Hãy chẩn đoán:

- **Deadlock:** A giữ L1 chờ L2, B giữ L2 chờ L1; giải pháp là lock ordering, ownership hoặc cấu trúc đồng bộ khác.
- **Thread pool starvation:** tất cả worker chờ những task đang nằm trong queue của chính pool; phải sửa đồ thị phụ thuộc và capacity.
- **Blocking I/O:** dùng timeout và resource-specific cancellation/close.
- **Missed signal:** waiter ngủ sau khi signal đã xảy ra; phải lưu predicate và kiểm tra dưới cùng lock/Condition.
- **Busy loop:** kiểm tra interrupt hoặc flag tại safe points.
- **Long critical section:** đưa tính toán thuần/I/O ra ngoài lock nhưng giữ validation/commit nguyên tử.
- **Không tin cậy hoặc cố ý không hợp tác:** dùng process/container isolation và quản lý process cấp cao khi cần cưỡng chế.

Một thread `WAITING` trong dump không tự chứng minh lỗi; có thể nó đang chờ đúng điều kiện. Một thread `BLOCKED` chờ monitor cũng không tự là deadlock. Cần biết **lock owner, predicate, ai có thể signal, và dependency graph**.

## 22. Testing an toàn thay vì gọi API cũ

Kiểm thử cancellation nên tạo **interleaving xác định** chứ không dựa vào một số millisecond ngẫu nhiên:

1. **Interrupt trước khi worker vào await.** Dùng latch cho biết worker đã đến mốc; gửi interrupt và assert worker thoát.
2. **Interrupt sau khi worker vào await.** Giữ worker chờ trên interruptible API, gửi interrupt rồi join.
3. **Pause trước resume sớm.** Gọi `pause` rồi `resume` trước khi worker start; worker phải không bị kẹt.
4. **Stop khi paused.** Worker phải thoát kể cả khi stop tới trước/đúng lúc chờ Condition.
5. **Invariant sau cancellation.** Chèn cancellation request ngay sau một transaction logic và kiểm tra snapshot; không ngắt bằng `Thread.stop`.
6. **Exception safety.** Gây overflow/validation failure trước commit; assert không có partial mutation.
7. **Future cancellation.** Assert `Future.isCancelled()` và kiểm tra task cooperation/lifecycle nếu cần.
8. **Timeout.** Dùng `join(timeout)` hoặc Future with timeout, kiểm tra kết quả và cleanup trong `finally` để không leak non-daemon threads.
9. **Không nuốt interrupt.** Test đường catch và verify task rời loop đúng policy.
10. **JDK compatibility.** Dò code cũ gọi `stop/suspend/resume` và test nâng cấp runtime; không dùng chúng trong application logic.

Một test pass không chứng minh mọi interleaving đều đúng. Review theo invariant, lock ownership, happens-before, interruption contract và safe points vẫn cần thiết.

## 23. Những ngộ nhận thường gặp

| Ngộ nhận | Sự thật |
| --- | --- |
| “`Thread.stop()` chỉ yêu cầu thread tự dừng.” | Trên JDK 17, nó từng ép `ThreadDeath`; từ JDK 20 luôn ném UOE. |
| “Đã `synchronized` thì stop không thể phá dữ liệu.” | Stop lịch sử có thể unwind và nhả monitor khi dữ liệu còn dở dang. |
| “`finally` chắc chắn rollback hết.” | Finally giúp cleanup theo code đã viết, không tự khôi phục mọi invariant/external side effect. |
| “`suspend()` nhả lock khi pause.” | Không theo hành vi cũ; đó chính là rủi ro deadlock. |
| “`resume()` được ghi nhớ cho một lần suspend sau.” | Không có predicate/state bảo đảm như protocol pause hợp tác. |
| “`interrupt()` là stop ngay.” | Không; đó là tín hiệu. Worker/API blocking cần hợp tác. |
| “Một vòng CPU bất tận tự thoát khi interrupt.” | Không nếu không kiểm tra status/cancel flag. |
| “Chờ `synchronized` sẽ ném InterruptedException.” | Intrinsic monitor acquisition không interruptible kiểu `lockInterruptibly`. |
| “Future cancelled nghĩa task đã cleanup xong.” | Không nhất thiết; future state và user code lifecycle khác nhau. |
| “Gọi `pause()` trả về tức worker đã đứng yên.” | Không; cần acknowledgement ở safe point khi hợp đồng yêu cầu. |
| “`Condition.await()` nhả mọi lock của thread.” | Chỉ nhả lock liên kết, không nhả monitor/lock khác. |
| “Timeout trong `join()` sẽ terminate worker.” | Không; nó chỉ giới hạn thời gian caller chờ. |
| “Dùng virtual threads thì có thể dùng stop/suspend an toàn.” | Không; vẫn cần cooperative lifecycle và invariant. |

## 24. Checklist thiết kế task có thể hủy

- [ ] Thread/task **owner** được xác định; không tự can thiệp worker của executor.
- [ ] Yêu cầu hủy được truyền bằng interrupt, flag được publish đúng hoặc Future cancel.
- [ ] Worker có **safe points** trước/sau mỗi đơn vị công việc.
- [ ] Không check cancel giữa hai bước thuộc cùng invariant mà không rollback.
- [ ] Các phép tính có thể throw được thực hiện trước commit khi có thể.
- [ ] `InterruptedException` được propagate hoặc restore và rời task an toàn.
- [ ] Blocking operations có timeout/cancellation/close policy phù hợp.
- [ ] Pause/resume lưu predicate dưới lock; `await` dùng while.
- [ ] Worker không pause hoặc chờ completion khi còn giữ lock nghiệp vụ cần cho tiến triển.
- [ ] Stop khi paused làm signalAll và không cho resume hồi sinh task.
- [ ] Nếu cần pause acknowledgement, có tín hiệu xác nhận worker đã tới safe point.
- [ ] Caller phân biệt cancellation requested, task completed và executor terminated.
- [ ] Tests kiểm tra interrupt trước/sau await, cancelled while paused và invariant.
- [ ] Code không còn dùng `Thread.stop()`, `suspend()`, `resume()` để điều khiển thread.
- [ ] Phiên bản JDK được ghi rõ khi giải thích hành vi deprecated/UOE.

## 25. Câu hỏi tự kiểm tra

**1.** Vì sao `Thread.stop()` trên JDK 17 có thể làm hỏng một object dùng synchronized?  
**Đáp:** `ThreadDeath` bất đồng bộ có thể khiến thread nhả monitor khi mới ghi một nửa state; thread khác sau đó đọc state sai.

**2.** Từ JDK 20, ba method stop, suspend và resume còn thực hiện chức năng cũ không?  
**Đáp:** Không. Chúng ném `UnsupportedOperationException`.

**3.** Vì sao suspend một thread đang giữ monitor dẫn tới deadlock?  
**Đáp:** Holder đứng yên nhưng không nhả monitor; controller có thể cần chính monitor đó trước khi resume, tạo vòng chờ.

**4.** `Thread.interrupt()` có trực tiếp giết thread đang tính CPU không?  
**Đáp:** Không. Worker cần kiểm tra status/flag hoặc gặp API interruptible rồi chủ động kết thúc.

**5.** Khi catch InterruptedException trong Runnable mà không thể ném tiếp, hướng thường dùng?  
**Đáp:** Restore interrupted status, cleanup và thoát theo chính sách task.

**6.** `Thread.interrupted()` khác `isInterrupted()` như thế nào?  
**Đáp:** Static method kiểm tra rồi xóa cờ current thread; instance method chỉ đọc.

**7.** Vì sao không đặt `if (interrupted) return` giữa debit và credit?  
**Đáp:** Nó có thể để invariant tổng tiền bị phá và nhả lock với state sai.

**8.** Một `pause()` request có bảo đảm worker đã PAUSED không?  
**Đáp:** Không. Worker còn có thể đang làm unit trước; cần acknowledgement khi yêu cầu xác nhận.

**9.** Resume đến trước Condition.await có thể bị bỏ lỡ không?  
**Đáp:** Không nếu thiết kế kiểm tra predicate `paused` dưới cùng lock; state đã lưu là nguồn sự thật.

**10.** Nếu stop xảy ra khi worker paused, cần làm gì?  
**Đáp:** Ghi stopped=true, signalAll dưới lock để worker thức và thoát, không chờ resume.

**11.** Future.cancel(true) trả true có nghĩa task đã hoàn tất cleanup không?  
**Đáp:** Không. Cancellation flag của future và completion của user code là hai điều khác nhau.

**12.** Khi worker bị kẹt ở I/O không phản ứng interrupt, giải pháp tổng quát là gì?  
**Đáp:** Không có mẹo Thread chung; dùng timeout/đóng resource/cô lập process theo API và failure model.

**13.** Dùng `Thread.sleep` trong `synchronized` có nhả monitor không?  
**Đáp:** Không. Sleep vẫn giữ monitor, có thể chặn contender.

**14.** Vì sao một test chỉ dựa vào sleep để “đợi cancellation” không đáng tin?  
**Đáp:** Sleep không bảo đảm trạng thái worker hay happens-before; cần latch, join/Future và assert completion.

**15.** Khi nào nên thay code dùng stop bằng một state machine?  
**Đáp:** Khi vòng đời có pause/resume/stop, nhiều worker hoặc cleanup phức tạp; state machine giúp chỉ rõ các transition/safe points.

**16.** Điều quan trọng nhất khi thay `Thread.stop()` là gì?  
**Đáp:** Bảo vệ invariant và chọn ranh giới commit/cleanup hợp lệ, không chỉ đổi tên API.

**Ghi nhớ:** Cưỡng bức dừng hoặc đóng băng một thread ở vị trí bất kỳ không cho phép bảo đảm các monitor và trạng thái nghiệp vụ còn an toàn. `Thread.stop()` lịch sử có thể nhả monitor khi state chưa nhất quán; `suspend()/resume()` lịch sử có thể deadlock vì lock không được nhả. Từ JDK 20, cả ba ném `UnsupportedOperationException`. Hướng thiết kế đúng là **cooperative cancellation bằng interrupt/flag/Future**, **pause/resume theo predicate tại safe point**, **transaction commit/cleanup rõ ràng** và **xác nhận kết thúc**.

</details>

- [Quay lại đầu trang](#back-to-top)
