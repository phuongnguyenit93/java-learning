<a id="back-to-top"></a>

# Vòng đời Thread

## Menu
- [Các trạng thái NEW, RUNNABLE và TERMINATED](#thread-lifecycle-001)
- [Các trạng thái BLOCKED, WAITING, TIMED_WAITING](#thread-lifecycle-002)
- [Chuyển trạng thái khi vào monitor hoặc chờ lock](#thread-lifecycle-003)
- [Chuyển trạng thái bởi sleep(), join(), wait()](#thread-lifecycle-004)
- [Quan sát trạng thái bằng Thread.getState()](#thread-lifecycle-005)
- [Scheduling và sự bất định của thứ tự thực thi](#thread-lifecycle-006)
- [Ảnh hưởng của blocking I/O và chờ điều kiện](#thread-lifecycle-007)
- [Khởi chạy một lần và lỗi khởi chạy lại thread](#thread-lifecycle-008)
- [Chờ thread hoàn tất mà không busy waiting](#thread-lifecycle-009)
- [Vòng đời tài nguyên gắn với thread](#thread-lifecycle-010)
- [Phân biệt trạng thái thread và trạng thái nghiệp vụ](#thread-lifecycle-011)

## <a id="thread-lifecycle-001">Các trạng thái NEW, RUNNABLE và TERMINATED</a>

<details>
<summary>Xem chi tiết</summary>

# Các trạng thái NEW, RUNNABLE và TERMINATED trong vòng đời Thread

Trong Java, `Thread` biểu diễn một luồng thực thi. Luồng trải qua các trạng thái vòng đời từ lúc được tạo, bắt đầu thực thi cho đến khi kết thúc. `Thread.State` là enum mô tả trạng thái **quan sát được** của một luồng tại một thời điểm. Bài này tập trung vào ba trạng thái quan trọng:

- **`NEW`**: đối tượng `Thread` đã được tạo nhưng luồng chưa được khởi động bởi `start()`.
- **`RUNNABLE`**: luồng đã khởi động, có thể đang chạy trong JVM **hoặc sẵn sàng để được lập lịch chạy**. Đây không đồng nghĩa đang chiếm CPU ở đúng thời điểm kiểm tra.
- **`TERMINATED`**: luồng đã hoàn tất thực thi, dù kết thúc bình thường hay do ngoại lệ/lỗi chưa được xử lý thoát khỏi `run()`.

Ba trạng thái này giúp hiểu vì sao `new Thread(...)` chưa chạy tác vụ, `start()` khác `run()`, và vì sao một `Thread` đã `TERMINATED` không thể khởi động lại.

## 1. Đối tượng Thread và luồng thực thi không phải một

Việc xây dựng `new Thread(runnable)` tạo đối tượng Java lưu các thông tin cần cho một luồng, **chưa bắt đầu chạy tác vụ trên luồng mới**. Đối tượng ban đầu có trạng thái `NEW`.

```java
Thread worker = new Thread(() -> {
    System.out.println("Worker started");
}, "my-worker");

// worker.getState() == Thread.State.NEW
worker.start();
```

Đây là trích đoạn minh họa cần đặt trong phương thức hoặc lớp hợp lệ, chưa phải chương trình độc lập. Lời gọi `start()` yêu cầu JVM bắt đầu thực thi `run()` trên luồng mới. Thời điểm CPU thực sự chạy tác vụ được bộ lập lịch quyết định; `start()` không có nghĩa tác vụ đã hoàn thành.

Nếu gọi trực tiếp `worker.run()`, Java thực hiện phương thức đó **ngay trên luồng gọi** (ví dụ `main`) thay vì khởi tạo luồng mới. Nếu trước đó chưa `start()`, đối tượng `worker` vẫn ở `NEW` sau khi gọi `run()` trực tiếp.

## 2. Bảng ý nghĩa và chuyển trạng thái

| Trạng thái | Điều kiện | `isAlive()` khi quan sát ổn định | Điều gì xảy ra tiếp theo? |
|---|---|---|---|
| `NEW` | Tạo đối tượng, chưa `start()`. | `false` | `start()` thành công → `RUNNABLE`. |
| `RUNNABLE` | Đủ điều kiện chạy hoặc đang thực thi trong JVM. | `true` | Có thể tiếp tục RUNNABLE, đi qua các trạng thái chờ, rồi TERMINATED. |
| `TERMINATED` | `run()` đã kết thúc. | `false` | Trạng thái cuối; không thể `start()` lại. |

Java 17 định nghĩa **sáu** giá trị của enum `Thread.State`: `NEW`, `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING`, `TERMINATED`. Ba trạng thái còn lại xuất hiện khi luồng còn sống nhưng đang chờ khóa hoặc chờ điều kiện/thời hạn. Chúng là ngữ cảnh để hiểu vòng đời, không phải trọng tâm của bài.

### NEW: chưa có hoạt động thực thi của luồng mới

Đối tượng ở `NEW` có thể được cấu hình (đặt tên, gắn uncaught exception handler, và với platform thread có thể đặt chế độ daemon) trước `start()`. Nếu không bao giờ gọi `start()`, tác vụ không tự động chạy. `run()` được gọi như phương thức thông thường không chuyển `Thread` sang RUNNABLE.

### RUNNABLE: sẵn sàng hoặc đang chạy

Tên `RUNNABLE` dễ gây hiểu nhầm. **`Thread.State` không có trạng thái `RUNNING` riêng.** JVM gộp việc “đang chạy” và “sẵn sàng chạy” vào RUNNABLE; thread đang tranh CPU có thể vẫn báo RUNNABLE dù chưa được hệ điều hành cấp CPU ngay lúc quan sát.

Đừng dùng `getState() == RUNNABLE` để kết luận thread đang xử lý hữu ích hay dùng bao nhiêu CPU. Trạng thái này cũng không đưa ra cam kết thứ tự thực thi giữa các luồng.

### TERMINATED: đã kết thúc

`TERMINATED` xuất hiện khi phương thức `run()` đã hoàn tất:

- Chạy hết thân phương thức hoặc `return` bình thường.
- Một ngoại lệ/lỗi chưa được xử lý khiến luồng kết thúc.

Không thể “reset” về NEW hoặc RUNNABLE. Gọi lại `start()` trên một thread đã bắt đầu, kể cả đã `TERMINATED`, gây `IllegalThreadStateException`. Muốn thực hiện tác vụ mới, hãy tạo đối tượng `Thread` mới hoặc giao việc cho cơ chế thực thi được quản lý.

## 3. Sơ đồ vòng đời

```text
new Thread(task)
      |
      v
     NEW
      |
      | start() thành công, chỉ một lần
      v
   RUNNABLE <------> BLOCKED / WAITING / TIMED_WAITING
      |                (khi luồng tạm chờ)
      |
      | run() trở về hoặc kết thúc bởi lỗi chưa xử lý
      v
  TERMINATED
      |
      +-- start() lần nữa: IllegalThreadStateException
```

Một thread có thể từ RUNNABLE chuyển sang `BLOCKED` khi chờ monitor `synchronized`, `WAITING` khi chờ điều kiện không thời hạn (ví dụ `CountDownLatch.await()`), hoặc `TIMED_WAITING` khi `Thread.sleep(...)`. Khi hết chờ, nó có thể quay về RUNNABLE rồi tiếp tục công việc. Không phải luồng nào cũng trải qua mọi trạng thái; một tác vụ ngắn có thể đi từ NEW qua RUNNABLE tới TERMINATED rất nhanh.

Không có API an toàn để đặt trạng thái theo ý muốn như `setState(TERMINATED)`. Muốn dừng, cần phối hợp để `run()` tự trả về (tín hiệu dừng, interrupt được xử lý đúng, hoặc hủy tác vụ có kiểm soát). `Thread.stop()` đã lỗi thời và không an toàn.

## 4. Ví dụ 1: thấy rõ NEW → RUNNABLE → TERMINATED

Để quan sát trạng thái, cần ngăn worker kết thúc quá nhanh trước khi `main` kịp kiểm tra. Ví dụ sau dùng `CountDownLatch` làm tín hiệu đã bắt đầu và `AtomicBoolean` làm tín hiệu dừng. Worker tạm chạy vòng lặp chủ động, vì vậy không phải đợi bằng `sleep()` với thời gian ước đoán.

Tạo file **`ThreadStateLifecycle.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThreadStateLifecycle {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            started.countDown();
            while (!stop.get()) {
                Thread.onSpinWait();
            }
        }, "lifecycle-worker");

        System.out.println("Before start: "
                + worker.getState() + ", alive=" + worker.isAlive());

        worker.start();
        started.await();

        System.out.println("While worker is spinning: "
                + worker.getState() + ", alive=" + worker.isAlive());

        stop.set(true);
        worker.join();

        System.out.println("After join: "
                + worker.getState() + ", alive=" + worker.isAlive());
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac ThreadStateLifecycle.java
java ThreadStateLifecycle
```

Kết quả dự kiến trong lần chạy bình thường:

```text
Before start: NEW, alive=false
While worker is spinning: RUNNABLE, alive=true
After join: TERMINATED, alive=false
```

**Giải thích theo thứ tự:**

1. `new Thread(...)` tạo đối tượng nhưng chưa thực thi: `NEW`, `isAlive() == false`.
2. `worker.start()` bắt đầu luồng. Worker phát tín hiệu `started.countDown()` và tiếp tục quay trong `while (!stop.get())`. `started.await()` giúp `main` chỉ kiểm tra sau khi worker báo bắt đầu.
3. Worker không chờ monitor, ngủ hay đợi latch ở trong vòng lặp, nên khi chưa nhận tín hiệu dừng, trạng thái quan sát là RUNNABLE. Có thể nó đang chiếm CPU hoặc đang được chờ cấp CPU.
4. `stop.set(true)` phát tín hiệu với bảo đảm hiển thị bộ nhớ của `AtomicBoolean`. Worker thoát vòng lặp và kết thúc `run()`.
5. `worker.join()` đợi việc kết thúc hoàn tất. Sau khi `join()` trả về bình thường, `getState()` là `TERMINATED`, `isAlive()` là `false`.

**Chú ý:** vòng lặp dùng `Thread.onSpinWait()` là cách cố ý tạo RUNNABLE để học, có thể tiêu tốn CPU. Không sử dụng vòng lặp chờ bận không giới hạn trong chương trình thực tế. Nếu thay bằng `Thread.sleep()` hoặc `await()`, trạng thái có thể là `TIMED_WAITING` hoặc `WAITING` và kết quả kiểm tra sẽ khác.

## 5. Ví dụ 2: gọi run() không thay thế start()

Ví dụ này chỉ dùng một `Thread`, nhưng chạy nội dung `Runnable` hai cách khác nhau để thấy sự khác biệt.

Tạo file **`StartVersusRun.java`**:

```java
public class StartVersusRun {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            System.out.println("Current thread: "
                    + Thread.currentThread().getName());
        }, "demo-worker");

        System.out.println("Initial state: " + worker.getState());

        worker.run();
        System.out.println("After direct run(): " + worker.getState());

        worker.start();
        worker.join();
        System.out.println("After start() and join(): " + worker.getState());

        try {
            worker.start();
        } catch (IllegalThreadStateException e) {
            System.out.println("Restart rejected: "
                    + e.getClass().getSimpleName());
        }
    }
}
```

```bash
javac StartVersusRun.java
java StartVersusRun
```

Đầu ra khi luồng khởi chạy thông thường có tên `main`:

```text
Initial state: NEW
Current thread: main
After direct run(): NEW
Current thread: demo-worker
After start() and join(): TERMINATED
Restart rejected: IllegalThreadStateException
```

Lần gọi `worker.run()` đầu tiên chỉ thực thi `Runnable` trên luồng `main`, nên `worker` vẫn NEW. Lần gọi `worker.start()` mới thực sự bắt đầu `demo-worker`. `join()` giúp dòng kiểm tra cuối được chạy khi worker đã xong. Cố gắng `start()` lần hai bị từ chối vì một đối tượng `Thread` chỉ có một vòng đời thực thi.

## 6. Ví dụ 3: ngoại lệ cũng dẫn đến TERMINATED

`TERMINATED` không có nghĩa tác vụ đã thành công; nó chỉ nói **luồng đã kết thúc**. Đoạn sau chủ động ném ngoại lệ, ghi nhận qua `UncaughtExceptionHandler`, rồi chờ hoàn tất bằng `join()`.

Tạo file **`FailedThreadLifecycle.java`**:

```java
public class FailedThreadLifecycle {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            throw new IllegalStateException("intentional");
        }, "failing-worker");

        worker.setUncaughtExceptionHandler((thread, error) -> {
            System.out.println(thread.getName()
                    + " failed: " + error.getMessage());
        });

        System.out.println("Before start: " + worker.getState());
        worker.start();
        worker.join();
        System.out.println("After failure: " + worker.getState());
    }
}
```

```bash
javac FailedThreadLifecycle.java
java FailedThreadLifecycle
```

Kết quả:

```text
Before start: NEW
failing-worker failed: intentional
After failure: TERMINATED
```

Handler ghi lại nguyên nhân; nó không làm thread trở lại RUNNABLE hay tự động chạy lại công việc. Trong ứng dụng thực tế, cần ghi nhận kết quả/lỗi riêng và quyết định có thử lại bằng **một tác vụ hoặc Thread mới** hay không.

## 7. getState(), isAlive() và join(): dùng đúng mục đích

### getState() là ảnh chụp, không phải khóa đồng bộ

`thread.getState()` trả về trạng thái tại thời điểm gọi. Đến lệnh kế tiếp trạng thái đã có thể khác. Ví dụ, một worker chạy rất nhanh có thể đã `TERMINATED` ngay sau `start()`, dù bạn vừa dự đoán nó RUNNABLE. Đây là lý do không nên viết logic kiểu:

```java
// Trích đoạn minh họa; worker được giả định đã khai báo.
while (worker.getState() != Thread.State.TERMINATED) {
    // Thăm dò liên tục: tốn CPU, không phải chiến lược đợi tốt.
}
```

Dùng `getState()` để ghi log hoặc hỗ trợ điều tra, không dùng làm cơ chế phối hợp luồng. Với nhu cầu chờ kết thúc, `join()` phù hợp hơn.

### isAlive() không có nghĩa là đang RUNNABLE

`isAlive()` cho biết thread **đã bắt đầu và chưa kết thúc**. Ở thời điểm ổn định, NEW và TERMINATED cho `false`; RUNNABLE cho `true`. Tuy nhiên, `isAlive() == true` không có nghĩa trạng thái là RUNNABLE: thread đang `WAITING`, `TIMED_WAITING` hay `BLOCKED` cũng còn sống.

`isAlive()` và `getState()` là hai phép đọc độc lập; nếu trạng thái chuyển giữa hai lần gọi, hai giá trị lấy được không nhất thiết là cùng một ảnh chụp nguyên tử. Ví dụ 1 chủ động giữ worker chưa kết thúc để phép quan sát dễ hiểu.

### join() là điểm đợi hoàn thành

`worker.join()` khiến **luồng gọi** chờ đến khi worker kết thúc, trừ trường hợp việc đợi bị interrupt. Nó hoạt động với cả daemon và user thread. `join()` có ý nghĩa về đồng bộ hóa: Java Memory Model bảo đảm các hành động của worker trước khi kết thúc *happens-before* với các hành động sau khi luồng khác phát hiện việc kết thúc qua `join()`. Tương tự, hành động trước `Thread.start()` trên luồng khởi tạo có quan hệ *happens-before* với các hành động trong thread mới.

Do đó, khi cần xử lý kết quả sau tác vụ, hãy dùng cơ chế hoàn thành/đồng bộ phù hợp (`join()`, `Future`, `CountDownLatch`…), tránh dựa vào việc thăm dò trạng thái và hy vọng dữ liệu đã sẵn sàng.

## 8. Những lỗi thiết kế thường gặp

| Hiểu sai hoặc thao tác sai | Hậu quả | Cách làm tốt hơn |
|---|---|---|
| `new Thread(task)` đã bắt đầu chạy | `task` chưa được thực thi, vẫn NEW. | Gọi `start()` khi thực sự muốn khởi chạy. |
| `thread.run()` tương đương `thread.start()` | Tác vụ chạy trên luồng gọi, không tạo concurrency. | Gọi `start()` đúng một lần. |
| RUNNABLE có nghĩa đang chiếm CPU | Đoán sai hiệu năng và nguyên nhân tắc nghẽn. | Quan sát CPU bằng profiler/thread dump theo thời gian. |
| Ngay sau `start()` nhất định thấy RUNNABLE | Task có thể chạy xong hoặc chuyển sang WAITING rất nhanh. | Dùng tín hiệu đồng bộ nếu muốn tạo thí nghiệm ổn định. |
| `TERMINATED` là một thread có thể dùng lại | `start()` lần nữa ném `IllegalThreadStateException`. | Tạo Thread mới cho lần chạy khác. |
| TERMINATED nghĩa là kết quả thành công | Ngoại lệ chưa xử lý cũng kết thúc thread. | Quản lý lỗi/kết quả riêng khỏi trạng thái. |
| `getState()` là công cụ chờ task | Race condition, vòng đợi bận và mã khó bảo trì. | Dùng `join()` hoặc công cụ đồng bộ thích hợp. |
| `isAlive()` có nghĩa worker đang RUNNABLE | Worker có thể đang bị BLOCKED/WAITING. | Kiểm tra mục đích quan sát và hiểu sáu trạng thái enum. |

**Không nhầm với daemon/user thread:** `isDaemon()` cho biết một thuộc tính khác về việc luồng có giữ JVM sống khi JVM kết thúc thông thường hay không. Daemon thread và user thread đều có thể có trạng thái NEW, RUNNABLE, TERMINATED; chế độ daemon **không** nằm trong enum `Thread.State`.

Nếu cần chẩn đoán, có thể đọc trạng thái bằng `getState()`, đặt tên thread dễ nhận diện, và dùng thread dump (chẳng hạn `jcmd <pid> Thread.print` trên JDK thích hợp). Một lần đo không đủ chứng minh deadlock, tiêu thụ CPU hay độ tiến triển; cần quan sát diễn biến và các mối quan hệ khóa/tín hiệu.

## 9. Tự kiểm tra

1. **Tạo `new Thread(...)` nhưng không gọi `start()` thì trạng thái là gì?** NEW.
2. **Gọi trực tiếp `run()` có làm đối tượng NEW chuyển thành RUNNABLE không?** Không; đó chỉ là lời gọi phương thức trên luồng hiện tại.
3. **Vì sao Java không có `Thread.State.RUNNING`?** JVM dùng RUNNABLE cho cả đã sẵn sàng và đang thực thi theo mô hình trạng thái của nó.
4. **Một thread đang `WAITING` có `isAlive() == true` không?** Có, nếu đã bắt đầu và chưa kết thúc.
5. **Sau `worker.join()` hoàn tất bình thường, trạng thái worker là gì?** TERMINATED.
6. **Một ngoại lệ chưa xử lý có thể đưa thread đến TERMINATED không?** Có; TERMINATED chỉ nói thread đã kết thúc, không xác nhận thành công.
7. **Vì sao không dùng vòng lặp kiểm tra `getState()` để đợi kết quả?** Vì trạng thái là ảnh chụp thay đổi liên tục, thăm dò dễ tốn CPU và không thay thế cơ chế đồng bộ.
8. **Gọi lại `start()` trên thread đã TERMINATED được không?** Không; tạo Thread mới.

**Ghi nhớ:** `NEW` = chưa bắt đầu; `RUNNABLE` = đang hoặc sẵn sàng chạy; `TERMINATED` = đã kết thúc và không thể khởi động lại cùng đối tượng. Dùng `start()` để khởi chạy, `join()` để chờ, `getState()` chủ yếu để quan sát.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-lifecycle-002">Các trạng thái BLOCKED, WAITING, TIMED_WAITING</a>

<details>
<summary>Xem chi tiết</summary>

# Các trạng thái BLOCKED, WAITING và TIMED_WAITING trong vòng đời Thread của Java

Ba trạng thái `BLOCKED`, `WAITING` và `TIMED_WAITING` đều cho thấy một thread hiện không tiến hành công việc Java thông thường như một thread `RUNNABLE`. Tuy nhiên, **nguyên nhân chờ, tài nguyên liên quan và cách đánh thức hoàn toàn khác nhau**. Phân biệt chính xác ba trạng thái giúp đọc thread dump, nhận diện lock contention, deadlock và thiết kế phối hợp thread đúng. Nội dung dưới đây nói về Java concurrency nói chung, không phụ thuộc framework.

## 1. Đặt ba trạng thái vào toàn bộ vòng đời

`java.lang.Thread.State` là một `enum` gồm sáu giá trị:

| Trạng thái | Ý nghĩa tóm tắt |
| --- | --- |
| `NEW` | Đã tạo đối tượng `Thread` nhưng chưa gọi `start()`. |
| `RUNNABLE` | Thread sẵn sàng hoặc đang thực thi trong JVM; **không đồng nghĩa** đang chiếm CPU ở mọi thời điểm. |
| `BLOCKED` | Đang chờ lấy **intrinsic monitor lock** để vào hoặc quay lại một vùng đồng bộ bằng `synchronized`. |
| `WAITING` | Đang chờ vô thời hạn theo một cơ chế chờ cụ thể, cho tới khi có sự kiện cho phép tiến triển. |
| `TIMED_WAITING` | Đang chờ theo cơ chế có thời hạn, có thể kết thúc vì sự kiện đánh thức hoặc hết thời gian. |
| `TERMINATED` | Phương thức `run()` đã kết thúc, bình thường hoặc bởi exception không được xử lý. |

Không có giá trị `RUNNING`, `SLEEPING` hoặc `DEADLOCKED` trong `Thread.State`. Chẳng hạn “sleeping” thể hiện qua `TIMED_WAITING`; deadlock là một tình trạng hệ thống có thể biểu hiện bằng `BLOCKED` hoặc những trạng thái chờ khác. Một thread có thể đi qua nhiều trạng thái trước khi kết thúc; **đây là snapshot tại một thời điểm, không phải lịch sử đầy đủ của thread**.

Ba trạng thái trọng tâm:

| Tiêu chí | `BLOCKED` | `WAITING` | `TIMED_WAITING` |
| --- | --- | --- | --- |
| Đợi điều gì? | Được quyền sở hữu monitor lock | Tín hiệu/sự kiện, không đặt thời hạn ở thao tác chờ | Tín hiệu/sự kiện **hoặc** mốc thời gian |
| Ví dụ trực tiếp | Vào `synchronized(lock)` khi thread khác đang giữ `lock` | `Object.wait()`, `Thread.join()`, `LockSupport.park()` | `Thread.sleep(ms)`, `Object.wait(ms)`, `Thread.join(ms)`, `LockSupport.parkNanos(ns)` |
| Có timeout tự kết thúc? | Không có timeout trong thao tác lấy monitor bằng `synchronized` | Không có timeout được chỉ định | Có, nhưng không đảm bảo được chạy ngay đúng mốc |
| Có thể giữ khóa khác không? | Có | Có, tùy API; riêng `Object.wait()` nhả monitor của đối tượng đang đợi | Có, ví dụ `sleep` không nhả monitor đang giữ |
| Điều nên điều tra | Ai giữ monitor? Giữ bao lâu? Có vòng chờ deadlock? | Đang chờ điều kiện nào? Ai phát tín hiệu? | Hạn chờ gì? Có timeout hoặc signal đúng không? |

## 2. BLOCKED: chờ lấy intrinsic monitor

Ví dụ:

```java
Object lock = new Object();

Thread first = new Thread(() -> {
    synchronized (lock) {
        // Tác vụ A giữ monitor của lock.
    }
});
Thread second = new Thread(() -> {
    synchronized (lock) {
        // Tác vụ B phải chờ nếu A đang giữ monitor.
    }
});
```

Đây là **mảnh minh họa**, không phải chương trình hoàn chỉnh. Nếu `first` đang trong `synchronized (lock)` khi `second` cố vào cùng monitor, `second` có thể ở `Thread.State.BLOCKED` cho tới khi lấy được khóa. Chỉ khai báo hai thread như trên **chưa làm chúng chạy**: cần `start()`; và không được khẳng định chúng chắc chắn tranh chấp nếu không có phối hợp/thời điểm phù hợp.

### Monitor là gì?

Mỗi object Java có thể đóng vai trò monitor nội tại. `synchronized (object)` yêu cầu thread sở hữu monitor trước khi vào critical section; `synchronized` trên instance method dùng monitor của `this`; `static synchronized` dùng monitor của đối tượng `Class` tương ứng. Khi monitor do thread khác nắm giữ, thread chưa lấy được monitor sẽ chờ ở `BLOCKED`.

- Monitor nội tại có tính **reentrant**: cùng một thread có thể vào lại `synchronized` của cùng monitor mà không tự khóa chính mình.
- Rời `synchronized` bình thường hay qua exception đều nhả monitor theo cơ chế Java.
- Nếu thread giữ monitor để làm I/O dài, `sleep`, gọi service chậm hoặc tính toán lâu, các thread khác có thể xếp hàng ở `BLOCKED`.
- `BLOCKED` **không có nghĩa** đang đợi mạng/đĩa. Với `ReentrantLock`, `Condition` hoặc nhiều cấu trúc dựa trên `AbstractQueuedSynchronizer`, việc chờ thường được triển khai bằng `LockSupport.park` và có thể hiện `WAITING` hoặc `TIMED_WAITING`; không nhất thiết hiện `BLOCKED`.
- Không có `synchronized.tryLock(timeout)`; nếu cần thời gian chờ hữu hạn để lấy lock, cân nhắc API `Lock.tryLock(timeout, unit)` và xử lý thất bại rõ ràng.

### BLOCKED có phải deadlock?

Không. Tranh chấp ngắn hạn là bình thường. **Deadlock** xảy ra khi có quan hệ chờ vòng, ví dụ A giữ `lock1`, đợi `lock2` còn B giữ `lock2`, đợi `lock1`. Khi đó hai thread có thể đều `BLOCKED` mãi. Nhưng một thread ở `BLOCKED` trong 2 ms vẫn có thể chỉ đang chờ critical section hợp lệ. Để điều tra, cần xem **monitor đang chờ, thread hiện sở hữu monitor đó và chuỗi phụ thuộc**, thay vì chỉ đếm số thread `BLOCKED`.

## 3. WAITING: chờ vô thời hạn theo điều kiện hoặc tín hiệu

`WAITING` nghĩa là phương thức chờ hiện tại không quy định hạn kết thúc. Các nguồn phổ biến:

- `Object.wait()`: thread phải đang giữ monitor của object, sau đó **nhả monitor của chính object đó** và vào wait set để chờ `notify`/`notifyAll`, interrupt hoặc thức dậy không có tín hiệu (spurious wakeup). Trước khi `wait()` trả về bình thường hoặc ném `InterruptedException`, thread phải lấy lại monitor đó.
- `Thread.join()` không timeout: đợi thread đích kết thúc nếu nó còn sống; thread gọi `join` mới là thread đang chờ. Không nhầm thread “bị join” với thread đang ở `WAITING`.
- `LockSupport.park()`: park thread cho tới khi có `unpark`, interrupt hoặc trở lại vì lý do được API cho phép. `park` **không tự động nhả monitor lock** mà caller đang giữ.
- Một số thao tác chờ không timeout như `CountDownLatch.await()`, `Future.get()`, `Condition.await()`: khi thực sự phải chờ, chúng thường dùng cơ chế đỗ thread và có thể hiện `WAITING`. Trạng thái thực tế là snapshot; đừng dựa vào tên API để khẳng định trạng thái tại mọi nanogiây.

**Vô thời hạn không có nghĩa là không thể thoát.** Có thể có tín hiệu, điều kiện được đáp ứng, thread bị interrupt, kết quả async hoàn tất, hoặc API cho phép spurious wakeup. Ngược lại, nếu sự kiện dự kiến không bao giờ xảy ra, thread có thể chờ vô hạn.

### `Object.wait()` khác `Thread.sleep()` ở việc nhả khóa

Đây là một điểm rất dễ nhầm:

- Khi gọi `lock.wait()` trong `synchronized(lock)`, thread **nhả monitor của `lock`** để thread khác có thể vào `synchronized(lock)`, thay đổi điều kiện và gọi `notifyAll()`.
- Khi gọi `Thread.sleep(...)` trong `synchronized(lock)`, thread **vẫn giữ monitor**. Thread khác muốn vào cùng monitor sẽ `BLOCKED` trong lúc nó ngủ.
- `wait()` chỉ nhả monitor của đối tượng được gọi `wait`; nếu thread đang giữ các monitor khác, chúng **không tự được nhả**.
- `notify()` đánh thức một waiter đủ điều kiện trong wait set, nhưng không đảm bảo chọn đúng thread mà lập trình viên muốn. `notifyAll()` thông báo mọi waiter trên monitor đó; các thread sau đó **vẫn phải tranh quyền lấy lại monitor**.

### Mẫu chuẩn: chờ một điều kiện bằng vòng `while`

`Object.wait()` chỉ là cơ chế chờ; điều kiện nghiệp vụ mới quyết định thread được tiếp tục. Do có thể thức dậy không mong đợi, điều kiện phải luôn được kiểm tra trong **`while`** thay vì giả định một lần `notify` đồng nghĩa có dữ liệu.

Ví dụ Java 17 độc lập, lưu thành `WaitNotifyExample.java`:

```java
public class WaitNotifyExample {
    public static void main(String[] args) throws InterruptedException {
        Object lock = new Object();
        boolean[] ready = {false};

        Thread consumer = new Thread(() -> {
            synchronized (lock) {
                while (!ready[0]) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                System.out.println("Da nhan tin hieu");
            }
        });

        consumer.start();

        synchronized (lock) {
            ready[0] = true;
            lock.notifyAll();
        }

        consumer.join();
        System.out.println("Hoan tat");
    }
}
```

Chạy bằng Java 17+:

```bash
javac WaitNotifyExample.java
java WaitNotifyExample
```

Stdout xác định:

```text
Da nhan tin hieu
Hoan tat
```

Dữ liệu `ready` chỉ được đọc/ghi khi giữ `lock` nên có bảo đảm đồng bộ và visibility. Nếu producer vào monitor trước consumer, consumer thấy `ready=true` và không cần `wait()`; nếu consumer vào trước, nó gọi `wait()` để **nhả** monitor, rồi producer có thể thay đổi điều kiện và `notifyAll()`. Hai đường đi đều đúng, không bị mất tín hiệu vì kiểm tra điều kiện và chờ diễn ra trong cùng critical section. Ví dụ này minh họa giao thức `wait/notifyAll`, **không đảm bảo** consumer đi qua `WAITING` ở mọi lần chạy.

**Đừng gọi `wait()/notify()/notifyAll()` nếu không sở hữu monitor của object**: thao tác sẽ ném `IllegalMonitorStateException`. Nên tránh dùng `wait` trực tiếp khi `BlockingQueue`, `CountDownLatch`, `Condition` hoặc primitive đồng bộ cấp cao diễn đạt đúng yêu cầu và ít rủi ro hơn.

## 4. TIMED_WAITING: chờ với thời gian giới hạn

Các thao tác điển hình:

| API | Ý nghĩa | Tác động lên monitor |
| --- | --- | --- |
| `Thread.sleep(1000)` | Tạm dừng ít nhất khoảng 1.000 ms theo cơ chế lập lịch, nếu không bị interrupt | **Không** nhả monitor đang giữ |
| `lock.wait(1000)` | Đợi trên wait set của `lock` tối đa khoảng thời gian tương ứng, có thể được báo thức sớm | Nhả monitor của `lock` trong thời gian đợi và lấy lại trước khi trả về |
| `otherThread.join(1000)` | Chờ thread đích kết thúc trong tối đa thời hạn | Không phải thao tác nhả tất cả monitor đang giữ của caller |
| `LockSupport.parkNanos(nanos)` | Park tối đa khoảng nanos dương | Không tự nhả monitor |
| `future.get(timeout, unit)` hoặc `latch.await(timeout, unit)` | Đợi kết quả/điều kiện có thời hạn | Không mặc định nhả intrinsic monitor caller đang giữ |

`TIMED_WAITING` chỉ cho thấy caller hiện đang đợi bằng thao tác có thời hạn, **không chứng minh** nó đang bị “treo” hoặc đã vi phạm SLA. Thời gian chờ thực tế bị ảnh hưởng bởi scheduling; hết timeout không có nghĩa caller lập tức giành được CPU. Một số API có thể kết thúc trước hạn vì tín hiệu, hoàn thành, interrupt hoặc spurious wakeup. `Thread.sleep` đặc biệt có thể bị ngắt bởi interrupt, nhưng không dùng nó làm cơ chế phối hợp điều kiện.

**Lưu ý Java API:** `Object.wait(0)` là đợi *vô thời hạn*, không phải chờ 0 ms rồi kết thúc ngay; `Thread.join(0)` cũng là chờ vô thời hạn. Trong ngữ cảnh thời hạn dương, dùng phiên bản có timeout và biết rõ quy ước đơn vị (milliseconds/nanoseconds). `Thread.sleep(0)` không tạo một khoảng `TIMED_WAITING` hữu ích để chờ điều kiện.

## 5. Thí nghiệm chạy được: quan sát cả ba trạng thái trên Java 17

Chạy một chương trình có phối hợp để tạo ba trạng thái mục tiêu. Đối với `BLOCKED`, main giữ monitor rồi tạo contender. Với `WAITING`, thread dùng `LockSupport.park()`. Với `TIMED_WAITING`, thread dùng `sleep` có thời hạn. Chương trình kiểm tra `getState()` ở thời điểm biết lý do chờ; nó không suy luận từ tốc độ máy hay thứ tự ngẫu nhiên.

Tạo `ThreadStatesExample.java`:

```java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class ThreadStatesExample {
    private static void awaitState(Thread thread, Thread.State expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != expected) {
            if (!thread.isAlive() || System.nanoTime() >= deadline) {
                throw new IllegalStateException(
                        "Khong quan sat duoc: " + expected);
            }
            Thread.sleep(1);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Object lock = new Object();

        Thread contender = new Thread(() -> {
            synchronized (lock) {
                // Vào được sau khi main nhả monitor.
            }
        }, "monitor-contender");

        synchronized (lock) {
            contender.start();
            awaitState(contender, Thread.State.BLOCKED);
            System.out.println("Da quan sat: BLOCKED");
        }
        contender.join();

        AtomicBoolean done = new AtomicBoolean(false);
        Thread parked = new Thread(() -> {
            while (!done.get()) {
                LockSupport.park();
            }
        }, "parked-worker");

        parked.start();
        awaitState(parked, Thread.State.WAITING);
        System.out.println("Da quan sat: WAITING");
        done.set(true);
        LockSupport.unpark(parked);
        parked.join();

        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "sleeping-worker");

        sleeper.start();
        awaitState(sleeper, Thread.State.TIMED_WAITING);
        System.out.println("Da quan sat: TIMED_WAITING");
        sleeper.interrupt();
        sleeper.join();
    }
}
```

```bash
javac ThreadStatesExample.java
java ThreadStatesExample
```

Stdout khi các trạng thái được quan sát trong thời hạn kiểm tra:

```text
Da quan sat: BLOCKED
Da quan sat: WAITING
Da quan sat: TIMED_WAITING
```

**Giải thích thí nghiệm:**

1. Main giữ `lock`. Contender gọi `synchronized(lock)` nhưng chưa lấy được monitor nên `BLOCKED`. Sau khi main rời khối đồng bộ, contender có thể vào monitor và hoàn thành.
2. Worker gọi `LockSupport.park()` khi chưa có permit, nên có thể đi vào `WAITING`. Main đặt cờ `done` rồi gọi `unpark`; vòng lặp quanh `park` xử lý trường hợp `park` tự thức dậy không do `unpark`. `AtomicBoolean` đảm bảo visibility của cờ.
3. Worker ngủ tối đa 10 giây. Main quan sát `TIMED_WAITING`, rồi `interrupt()` để kết thúc sớm; không cần chờ đủ 10 giây.

`awaitState` chỉ dùng cho **thí nghiệm**. Nó polling snapshot có deadline và có thể ném exception nếu máy cực kỳ bận hoặc thread bị tác động ngoài dự kiến. Trong production, **không viết logic điều khiển ứng dụng bằng cách đoán thread state với `getState()`**; dùng `join`, `CountDownLatch`, `Future`, `Condition` hoặc các cơ chế phối hợp theo điều kiện thực tế. Các trạng thái có thể đổi rất nhanh giữa hai lần đọc.

## 6. Chuyển trạng thái thường gặp

### Tranh chấp monitor

```text
Thread A: RUNNABLE --vào synchronized(lock) thành công--> RUNNABLE
Thread B: RUNNABLE --không lấy được monitor lock--> BLOCKED
Thread A: ... ra khỏi synchronized(lock), nhả monitor ...
Thread B: BLOCKED --lấy được monitor--> RUNNABLE
```

Đây là luồng diễn giải logic; việc lấy monitor và đổi trạng thái có thể diễn ra rất nhanh, nên sampling không bảo đảm bắt được mọi trạng thái.

### `wait()/notifyAll()`

```text
Consumer giữ monitor M và thấy condition=false
Consumer gọi M.wait(): nhả M, có thể vào WAITING
Producer lấy M, cập nhật condition=true, gọi M.notifyAll()
Consumer được đánh thức nhưng phải lấy lại M
Consumer có thể BLOCKED trong lúc producer còn giữ M
Producer nhả M; consumer lấy lại M, kiểm tra while rồi tiếp tục
```

**`notifyAll()` không trao monitor ngay** cho waiter. Nếu producer tiếp tục giữ `M` lâu, consumer chưa thể chạy phần sau `wait()` dù đã được thông báo. Sau khi nhận thông báo, waiter cũng có thể không phải là thread được scheduler chọn ngay lập tức. Dùng `while` vì tín hiệu chỉ bảo thread kiểm tra lại điều kiện; không thay thế điều kiện.

### Chờ có hạn

```text
RUNNABLE --Thread.sleep(200)--> TIMED_WAITING
TIMED_WAITING --hết thời hạn hoặc interrupt--> RUNNABLE
```

Với `wait(timeout)` hay `join(timeout)`, kết thúc thời hạn **không bảo đảm** điều kiện đã thỏa mãn. Với `wait(timeout)`, thread còn phải lấy lại monitor; với `join(timeout)`, cần kiểm tra `isAlive()` nếu phải biết thread đích có thực sự kết thúc.

## 7. Điều gì xảy ra khi interrupt?

`Thread.interrupt()` là **tín hiệu yêu cầu phối hợp dừng/chuyển hướng**, không phải lệnh giết thread ngay lập tức.

| Hoạt động | Phản ứng điển hình |
| --- | --- |
| `Object.wait()`, `Thread.sleep()`, `Thread.join()` | Ném `InterruptedException` nếu bị interrupt khi chờ và trạng thái interrupt thường bị xóa khi exception được ném |
| `CountDownLatch.await()`, `Future.get()` | Có thể ném `InterruptedException` theo hợp đồng API |
| `LockSupport.park()` | Có thể trở về và giữ cờ interrupt; không ném `InterruptedException` |
| Đang đợi monitor của `synchronized` (`BLOCKED`) | Không thể dùng interrupt để hủy việc lấy intrinsic monitor bằng cú pháp `synchronized` |
| `ReentrantLock.lockInterruptibly()` | Có thể phản hồi interrupt khi chờ lock; khác với `synchronized` |

Khi bắt `InterruptedException` mà không ném tiếp, mã thường nên khôi phục cờ bằng `Thread.currentThread().interrupt()` rồi thoát hoặc chuyển lỗi phù hợp. Không tạo vòng `while(true)` tiếp tục đợi và nuốt interrupt vô hạn. Khi dùng `LockSupport.park()`, chú ý nếu cờ interrupt vẫn đang bật, lần `park()` sau có thể trở về ngay; cần có điều kiện thoát rõ ràng.

## 8. Đọc thread dump đúng cách

Trên HotSpot/OpenJDK, có thể lấy thread dump của JVM bằng JDK diagnostic tools, ví dụ (thay PID thực tế):

```bash
jcmd <PID> Thread.print -l
```

Các đoạn sau **minh họa cấu trúc thông tin thường gặp**, không phải stdout cố định của JDK ở mọi phiên bản:

```text
"worker-1" ... java.lang.Thread.State: BLOCKED (on object monitor)
    - waiting to lock <...> (a java.lang.Object)
"worker-2" ... java.lang.Thread.State: WAITING (parking)
    at jdk.internal.misc.Unsafe.park(...)
"worker-3" ... java.lang.Thread.State: TIMED_WAITING (sleeping)
    at java.lang.Thread.sleep(...)
```

Cần đọc kèm stack trace, monitor/synchronizer đang chờ, thread sở hữu lock, và dấu vết từ các lần dump khác nhau:

- **Nhiều `BLOCKED` trên cùng monitor:** tìm thread đang giữ monitor, vị trí khóa được lấy và thời gian critical section. Nếu nhiều thread tranh cùng một lock lâu, cân nhắc giảm phạm vi `synchronized` hoặc loại bỏ blocking I/O trong critical section.
- **`WAITING` tại `Object.wait`:** xác định condition, đường đi `notify/notifyAll`, liệu producer có thể chạy, có kiểm tra lại condition không.
- **`WAITING` tại `LockSupport.park`:** xem thư viện đồng bộ ở stack trace (`Future`, `ReentrantLock`, queue, pool worker idle...). Thread pool worker rảnh và đang park có thể **hoàn toàn bình thường**.
- **`TIMED_WAITING` tại `sleep`:** tìm lý do ngủ (retry, backoff, lịch, hay code vô tình chặn). `TIMED_WAITING` tại timed queue poll hoặc connection wait có ý nghĩa khác; đọc frame gọi và deadline.
- **Deadlock:** xác định chu trình giữa các thread và lock. `ThreadMXBean.findDeadlockedThreads()` giúp phát hiện một số deadlock liên quan object monitors và ownable synchronizers, nhưng không thay thế phân tích các chu trình chờ logic, mạng, DB hoặc resource bên ngoài.

`Thread.getState()`, thread dump và metrics là công cụ **quan sát**, không dùng để đảm bảo giao thức đồng bộ. Thread state ở cấp Java cũng không tương ứng 1:1 với trạng thái scheduler của hệ điều hành: ví dụ thread ở `RUNNABLE` vẫn có thể đang chờ xử lý tài nguyên hệ điều hành. Sự hiện diện của `WAITING` hoặc `TIMED_WAITING` không tự chứng minh một lỗi hiệu năng.

Trong Java 21+, **virtual thread** cũng có các trạng thái Java ở cấp API, nhưng cơ chế mount/unmount trên carrier và cách các công cụ JVM hiển thị virtual threads có thể khác platform threads. Không suy ra số OS threads chỉ bằng cách đếm virtual threads đang chờ; đọc diagnostics phù hợp với phiên bản JDK. Bài này không dùng API/framework riêng ngoài Java core và Java concurrent utilities.

## 9. Phân biệt chờ monitor với chờ điều kiện

Một tình huống dễ gây khó hiểu:

```java
synchronized (lock) {
    lock.wait();
}
```

Trước lúc gọi `wait`, thread đã **sở hữu monitor**. Khi vào `wait`, nó nhả monitor rồi ở `WAITING`; sau `notifyAll`, nó phải quay lại lấy monitor. Trong khoảng nó cố lấy lại monitor nhưng chưa thành công, trạng thái có thể là `BLOCKED`. Vì vậy, **một thread có thể xuất hiện ở `WAITING` rồi chuyển sang `BLOCKED` trong cùng một chu trình phối hợp**, không có mâu thuẫn.

Ngược lại:

```java
synchronized (lock) {
    Thread.sleep(500);
}
```

Thread đang ngủ có thể ở `TIMED_WAITING` **nhưng vẫn giữ `lock`**. Những thread khác muốn vào `synchronized(lock)` sẽ `BLOCKED`. Đây thường là lý do cần tránh ngủ, gọi HTTP, đọc file hay chờ `Future` dài hạn trong critical section.

Với `ReentrantLock` và `Condition`, `condition.await()` nhả **lock tương ứng với condition** khi đợi và lấy lại trước khi tiếp tục, tương tự mục đích phối hợp của `Object.wait` nhưng API khác. Không suy luận “không thấy `BLOCKED` tức là không có tranh chấp lock”: nhiều lock hiện đại park thread và thể hiện trong `WAITING`.

## 10. Những lỗi thường gặp và cách sửa

| Lỗi | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Xem mọi `BLOCKED` là deadlock | Tranh chấp monitor ngắn có thể bình thường | Tìm owner và chu trình chờ; so nhiều thread dump |
| Xem mọi `WAITING` là thread treo | Worker rảnh, chờ queue/signal là trạng thái hợp lệ | Xem stack, điều kiện thoát, thời gian, tải hệ thống |
| Cho rằng `WAITING` chỉ đến từ `Object.wait` | `join`, `park` và nhiều synchronizer cũng chờ | Đọc frame thực tế và tài nguyên liên quan |
| Cho rằng `TIMED_WAITING` là timeout đã xảy ra | Đây là trạng thái **trong lúc** đang chờ có hạn | Kiểm tra kết quả trả về/exception và deadline |
| Gọi `notify` rồi nghĩ waiter có monitor ngay | Waiter còn phải tranh lấy lại monitor | Giữ critical section ngắn và dùng condition-loop |
| Dùng `if (conditionFalse) wait()` | Spurious wakeup, nhiều consumer, condition thay đổi | Dùng `while` kiểm tra điều kiện sau khi thức |
| Gọi `wait` ngoài `synchronized` | `IllegalMonitorStateException` | Giữ đúng monitor trước khi `wait` |
| Cho rằng `sleep` hoặc `park` nhả monitor | Chúng không tự nhả intrinsic monitor | Tránh sleep/I/O/long waits khi giữ monitor |
| Nuốt `InterruptedException` | Bỏ tín hiệu hủy, shutdown khó | Propagate hoặc restore interrupt và thoát hợp lý |
| Dựa vào `getState()` để điều phối production | Race giữa thời điểm đọc và hành động | Dùng `Future`/`CountDownLatch`/queue/condition |
| Dùng `wait(0)` như timeout zero | `wait(0)` có nghĩa đợi vô thời hạn | Dùng timeout dương, kiểm tra đơn vị và deadline |
| Nhầm `ReentrantLock` contention luôn là `BLOCKED` | Có thể dùng park/`WAITING` | Đọc stack và lock metadata |

## 11. Một vài câu hỏi kiểm tra hiểu

**1. Hai thread đều `BLOCKED` có chắc deadlock?** Không. Chúng có thể chờ một thread thứ ba nhả monitor. Chỉ xác nhận deadlock khi có chu trình chờ không thể tiến triển hoặc phát hiện phù hợp qua diagnostics.

**2. Thread gọi `join()` sẽ ở trạng thái gì?** Nếu thread đích còn chạy và caller thực sự phải đợi không timeout, caller có thể ở `WAITING`. `join(timeout dương)` có thể dẫn đến `TIMED_WAITING`. Nếu target đã kết thúc, caller không cần vào trạng thái chờ.

**3. `notifyAll()` có đưa thread từ `WAITING` vào `RUNNABLE` ngay không?** Không nhất thiết. Thread được đánh thức phải lấy lại monitor của `wait`, nên có thể thấy `BLOCKED` trước khi chạy tiếp. Sau khi chạy tiếp vẫn phải kiểm tra condition trong `while`.

**4. `Thread.sleep(1000)` trong `synchronized` giúp thread khác vào monitor không?** Không. Thread ngủ không tự nhả monitor. Thread khác có thể `BLOCKED` trong lúc nó `TIMED_WAITING`.

**5. Thread ở `TIMED_WAITING` có chắc sẽ kết thúc tác vụ sau 1 giây không?** Không. Mốc thời gian chỉ giới hạn một thao tác chờ cụ thể; scheduler, việc lấy lại lock và các bước còn lại của tác vụ có thể làm thời gian tổng dài hơn.

**6. Nhiều `WAITING` trong thread pool có phải đang quá tải?** Không thể kết luận. Worker đang rảnh và đợi việc mới là bình thường. Cần xem active workers, queue depth, throughput, latency, lỗi và stack.

## 12. Checklist áp dụng khi debug Java concurrency

1. Ghi lại **state, stack trace, lock identity và lock owner** của thread liên quan tại nhiều thời điểm.
2. Nếu `BLOCKED`, xác định monitor nào đang bị tranh và vì sao thread giữ monitor lâu.
3. Nếu `WAITING`, xác định cơ chế chờ (monitor, park, join, Future, latch, queue) và điều kiện/tác nhân giải phóng.
4. Nếu `TIMED_WAITING`, xác định timeout được cấu hình, đơn vị, điều kiện kết thúc sớm và hành động khi timeout.
5. Xác minh có giữ monitor khi `sleep`, chờ I/O hoặc park hay không; kiểm tra deadlock lẫn logical stall.
6. Đánh giá interrupt, cancellation, condition-loop, timeout và việc giải phóng tài nguyên.
7. Kết luận từ **nhiều bằng chứng** (thread dumps, JFR, logs, CPU/lock metrics), không từ một giá trị `getState()` đơn lẻ.

**Ghi nhớ:** `BLOCKED` là tranh lấy **intrinsic monitor**; `WAITING` là chờ không đặt hạn trên một cơ chế phối hợp; `TIMED_WAITING` là chờ có thời hạn. Điều quan trọng nhất khi xử lý sự cố là **thread đang đợi điều gì, ai/cái gì làm nó tiến triển, và nó có đang giữ tài nguyên gây nghẽn thread khác không**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-lifecycle-003">Chuyển trạng thái khi vào monitor hoặc chờ lock</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển trạng thái Thread khi vào monitor hoặc chờ lock trong Java

> **Ngữ cảnh:** vòng đời `Thread` và các trạng thái khi thread tranh chấp monitor hoặc chờ khóa. Bài tập trung vào cơ chế **Java SE/JVM**, bao gồm `synchronized`, `Object.wait/notify`, `ReentrantLock`, `Condition` và `LockSupport`; không đi vào concurrency riêng của Spring.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Diễn giải chính xác lúc nào một Java thread thường chuyển giữa `RUNNABLE`, `BLOCKED`, `WAITING` và `TIMED_WAITING`.
- Phân biệt **đợi lấy intrinsic monitor**, **đang đợi một điều kiện trên monitor**, và **đợi khóa tường minh** như `ReentrantLock`.
- Giải thích vì sao `notify()`, `notifyAll()` hoặc `signal()` **chưa có nghĩa** thread được đánh thức đã có thể chạy qua critical section.
- Viết đúng cấu trúc chờ điều kiện, xử lý `InterruptedException`, giải phóng khóa và tránh lost notification.
- Đọc thread dump/JFR theo cơ chế đồng bộ đang dùng, thay vì suy nguyên nhân từ riêng một nhãn `Thread.State`.

Điểm then chốt: **`BLOCKED` là một trạng thái cụ thể của `Thread.State` dành cho việc đợi *intrinsic monitor*; không phải mọi thread “đang bị chặn” bởi một khóa đều mang `BLOCKED`.**

## 2. Thread.State biểu diễn điều gì?

`Thread.getState()` trả về một trong sáu trạng thái của Java. Đây là trạng thái quan sát tại thời điểm lấy mẫu, **không** phải sơ đồ đầy đủ mọi trạng thái lập lịch nội bộ của hệ điều hành, và cũng không mô tả toàn bộ lý do một task chưa hoàn thành.

| Trạng thái | Định nghĩa đáng chú ý | Liên quan đến monitor/lock |
|---|---|---|
| `NEW` | Thread được tạo nhưng chưa `start()`. | Chưa tranh khóa. |
| `RUNNABLE` | Đang chạy hoặc sẵn sàng được chạy trong JVM; có thể còn chờ OS cấp CPU. | Thread có thể đang ở bên trong critical section hoặc chuẩn bị gọi lock. |
| `BLOCKED` | Đợi vào hoặc **vào lại** một intrinsic monitor. | Thường thấy ở `synchronized` và khi `Object.wait()` đã được đánh thức nhưng chưa lấy lại monitor. |
| `WAITING` | Đợi một hành động từ thread khác, không chỉ định timeout. | `Object.wait()` không timeout, `Condition.await()`, `LockSupport.park()`, một số đường đi của `ReentrantLock.lock()`. |
| `TIMED_WAITING` | Đợi trong thời hạn được chỉ định. | `Object.wait(timeout)`, `Condition.awaitNanos()`, `LockSupport.parkNanos()`; cả `Thread.sleep()` cũng ở đây. |
| `TERMINATED` | Hàm `run()` đã kết thúc. | Không còn tranh khóa. |

Một số lưu ý để không đọc sai:

- `RUNNABLE` **không chứng minh** thread đang chạy trên CPU vào thời điểm lấy mẫu.
- `WAITING` **không chứng minh** deadlock: executor worker có thể đang nhàn rỗi đợi queue, hoặc thread đang đợi một điều kiện hợp lệ.
- `BLOCKED` trong API này chỉ nói tới **monitor**, không gộp mọi kiểu I/O, thread pool, database connection hoặc khóa do thư viện tự xây.
- Thread có thể chuyển trạng thái nhanh hơn chu kỳ thu thập dữ liệu. Hai ảnh chụp liên tiếp có thể khác nhau mà không có lỗi.
- Java `Thread.State` không phải `Process State` của Linux/Windows; không suy trực tiếp số lần context switch, CPU time hay thời gian lock chờ từ một ảnh `getState()`.

## 3. Vào `synchronized`: `RUNNABLE → BLOCKED → RUNNABLE`

Một **intrinsic monitor** gắn với từng object Java, và cũng được dùng cho `static synchronized` thông qua đối tượng `Class` tương ứng. Khi một thread vào `synchronized (lock) { ... }`, nó cần **sở hữu monitor** của `lock`.

- Nếu monitor đang rảnh, thread có thể lấy nó và đi tiếp vào critical section mà không phải đi qua một khoảng `BLOCKED` quan sát được.
- Nếu monitor do thread khác nắm giữ, thread đang muốn vào sẽ ở `BLOCKED` trong mô hình trạng thái Java.
- Khi monitor được giải phóng, thread tranh chấp có **cơ hội** lấy lại monitor và tiếp tục `RUNNABLE`; không có bảo đảm thread nào giành được trước, cũng không có bảo đảm lập tức được CPU.
- `synchronized` là **reentrant**: thread đã sở hữu monitor đó có thể vào lồng nhau với cùng monitor mà không tự block chính nó.

Ví dụ:

```java
Object guard = new Object();

// Thread A
synchronized (guard) {
    doWork(); // A giữ guard trong lúc thực hiện
}

// Thread B chạy đồng thời:
synchronized (guard) {
    updateState(); // B phải đợi nếu A chưa nhả guard
}
```

Đây là **đoạn minh họa**, không phải một chương trình độc lập vì `doWork()` và `updateState()` chỉ là tên giả định. Khi A còn giữ `guard`, B có thể ở `BLOCKED`. A thoát khỏi khối `synchronized` (kể cả khi exception làm stack unwinding) thì monitor được nhả. Thread B chỉ tiếp tục cập nhật sau khi nó thực sự vào được monitor.

**Monitor entrance không interruptible:** gọi `contender.interrupt()` không buộc một thread đang `BLOCKED` ở lối vào `synchronized` thoát khỏi chờ khóa và không khiến lệnh `synchronized` ném `InterruptedException`. Cờ interrupt vẫn có thể được đặt để code phía sau xử lý. Muốn một **lock acquisition** có thể hủy bằng interrupt, cân nhắc `ReentrantLock.lockInterruptibly()` khi yêu cầu thiết kế cho phép.

Về hiệu ứng bộ nhớ, việc **unlock** một monitor *happens-before* lần **lock** tiếp theo của cùng monitor. Nhờ đó, thread vào sau quan sát các ghi trước khi thread trước nhả monitor theo quy tắc Java Memory Model. Tuy nhiên, điều này không tự động bảo vệ dữ liệu nếu một luồng đọc/ghi bỏ qua cùng cơ chế đồng bộ.

## 4. `Object.wait()`: từ sở hữu monitor sang WAITING

`Object.wait()` khác hoàn toàn với việc chờ **để vào** `synchronized`.

1. Thread phải **đang sở hữu monitor** của đúng object mà nó gọi `wait()`. Nếu không, Java ném `IllegalMonitorStateException`.
2. `wait()` đưa thread vào wait set của monitor đó và **nhả monitor đó** (bao gồm các lần nắm monitor reentrant tương ứng) trong lúc đợi.
3. Với `wait()` không timeout, thread thường được quan sát là `WAITING`. Với `wait(long timeoutMillis)` (timeout dương), là `TIMED_WAITING`.
4. Một lý do giúp rời wait set có thể là `notify()`, `notifyAll()`, interrupt, timeout (nếu có), hoặc **spurious wakeup**.
5. Trước khi `wait()` thực sự trả về hoặc ném `InterruptedException`, thread phải **lấy lại monitor** của object đó. Nếu monitor còn do thread khác giữ, thread có thể chuyển sang `BLOCKED` khi chờ vào lại.
6. Chỉ sau khi vào lại thành công, thread mới tiếp tục kiểm tra điều kiện và chạy code trong khối `synchronized`.

Luồng thường gặp:

```text
RUNNABLE (đang giữ monitor)
    |
    | gọi Object.wait(), nhả monitor của object đó
    v
WAITING hoặc TIMED_WAITING
    |
    | notify/notifyAll, interrupt, timeout hoặc spurious wakeup
    v
BLOCKED (nếu đang chờ lấy lại monitor)
    |
    | lấy được monitor
    v
RUNNABLE (tiếp tục trong synchronized)
```

Sơ đồ là **một chuỗi chuyển trạng thái điển hình**, không phải khẳng định mọi lần chạy đều quan sát đủ từng trạng thái. Nếu monitor có thể lấy lại ngay, pha `BLOCKED` rất ngắn hoặc không nhìn thấy ở ảnh chụp.

### Tại sao notify chưa phải là tiếp tục chạy?

Xét:

```java
synchronized (guard) {
    available = true;
    guard.notifyAll();
    // Thread vừa được notify vẫn chưa thể thực thi
    // bên trong synchronized(guard) cho tới khi monitor được nhả.
    performMoreWork();
}
```

Đây là **đoạn minh họa đặt bên trong method**; `guard`, `available` và `performMoreWork()` là các thành phần đã được khai báo trong ngữ cảnh giả định. `notifyAll()` làm các waiter đủ điều kiện tranh lấy monitor, nhưng **không tự nhả monitor**. Thread gọi `notifyAll()` vẫn giữ monitor cho tới khi thoát vùng đồng bộ. Vì vậy, kéo dài `performMoreWork()` ngay sau thông báo có thể tăng thời gian các waiter chờ lấy lại khóa.

- `notify()` chọn **một** waiter từ wait set tương ứng, không cam kết chọn “cũ nhất” hay bất kỳ thứ tự công bằng nào.
- `notifyAll()` đánh thức các waiter trong wait set, nhưng họ vẫn phải lần lượt tranh monitor.
- Không phải waiter nào cũng đủ điều kiện xử lý dữ liệu sau khi tỉnh dậy: thông báo không biến điều kiện nghiệp vụ thành đúng cho mọi thread.

### Luôn đợi theo điều kiện (predicate) trong vòng lặp

Mẫu đúng:

```java
private final Object guard = new Object();
private boolean available;

// Producer:
void publish() {
    synchronized (guard) {
        available = true;  // thay đổi predicate trước
        guard.notifyAll(); // thông báo khi đang giữ monitor
    }
}

// Consumer:
void awaitAvailable() throws InterruptedException {
    synchronized (guard) {
        while (!available) {
            guard.wait();
        }
        // available == true và hiện đang giữ guard
    }
}
```

Đây là các method minh họa của **cùng một class**, không phải tệp Java độc lập. Cả ghi `available`, kiểm tra `available` và `wait/notify` đều diễn ra dưới **cùng monitor**, nên không cần `volatile` riêng cho biến này trong mô hình đó.

**Vì sao `while` chứ không phải `if`?** Có thể tỉnh dậy giả (spurious wakeup), nhiều consumer cùng được đánh thức nhưng chỉ một consumer giành được dữ liệu, hoặc điều kiện đã thay đổi trong lúc chờ lấy lại monitor. Thread phải **kiểm tra lại predicate** sau mỗi lần thức dậy.

**Tránh lost notification:** đừng tách thao tác kiểm tra điều kiện khỏi thao tác vào wait set. Nếu một thread kiểm tra điều kiện không đồng bộ rồi mới gọi `wait()`, một `notify` có thể xảy ra ở giữa và bị bỏ lỡ. Predicate được cập nhật và kiểm tra dưới cùng khóa giải quyết trường hợp này: nếu điều kiện đã đúng, thread không gọi `wait`.

`wait()` **chỉ nhả monitor của object được gọi**. Nếu thread đang giữ thêm một monitor khác khi gọi `guard.wait()`, các monitor khác **không tự được nhả**; điều này có thể tạo deadlock hoặc giữ khóa quá lâu. `Thread.sleep()` cũng **không nhả các monitor đang giữ**; ngủ không phải thay thế cho `wait()`.

### Interrupt trong Object.wait

Khi một thread bị interrupt trong lúc `Object.wait()`, `wait()` sẽ ném `InterruptedException` sau khi đã lấy lại monitor theo hợp đồng API; **interrupt status được xóa** khi exception được ném. Việc xử lý nên hoặc truyền exception lên caller, hoặc thoát có kiểm soát và khôi phục interrupt flag:

```java
try {
    guard.wait();  // chỉ gọi khi đã sở hữu monitor guard
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return; // nếu đang ở method void và quyết định hủy công việc
}
```

Đây là **đoạn minh họa trong một method `void` nằm trong `synchronized(guard)`**, không phải chương trình độc lập. Không nuốt interrupt bằng một `catch` trống rồi tiếp tục đợi vô hạn.

## 5. `ReentrantLock`: chờ lock không đồng nghĩa Java BLOCKED

`ReentrantLock` thuộc `java.util.concurrent.locks`. Đây là **explicit lock**, không phải intrinsic monitor do `synchronized` dùng. Khi gọi `lock()` mà khóa chưa khả dụng, triển khai đồng bộ thường dựa trên AQS và có thể dùng `LockSupport.park()` để cho thread chờ. Khi đang được park, thread thường thể hiện `WAITING` (hoặc `TIMED_WAITING` cho cơ chế chờ có timeout). Trong những đoạn thử giành khóa ngắn, nó cũng có thể là `RUNNABLE`. **Không mặc định trạng thái `BLOCKED` cho `ReentrantLock.lock()`.**

Ví dụ mẫu:

```java
ReentrantLock lock = new ReentrantLock();

lock.lock();
try {
    // Đọc/ghi dữ liệu được bảo vệ bởi lock.
} finally {
    lock.unlock();
}
```

Đây là **đoạn mã trong một method** cần import `java.util.concurrent.locks.ReentrantLock`. `unlock()` phải nằm trong `finally`; nếu công việc ném exception mà không unlock, các thread khác có thể kẹt. Chỉ thread sở hữu mới được `unlock()`; gọi sai gây `IllegalMonitorStateException`.

Khác biệt quan trọng về chờ hủy được:

| Cơ chế | Có thể dừng **đang chờ lấy khóa** bằng interrupt? | Ghi chú |
|---|---|---|
| `synchronized` enter | **Không** | `interrupt()` không làm `synchronized` tự ném exception. |
| `ReentrantLock.lock()` | **Không theo API này** | Giành khóa theo cách mặc định; không phải acquisition có thể hủy. |
| `ReentrantLock.lockInterruptibly()` | **Có** | Có thể ném `InterruptedException` trong lúc đợi lock; caller phải xử lý. |
| `ReentrantLock.tryLock()` | Trả kết quả ngay | Không phải đường chờ; trả `false` nếu khóa chưa lấy được. |
| `ReentrantLock.tryLock(timeout, unit)` | **Có** | Có thể timeout hoặc ném `InterruptedException`. |

Khi viết `lockInterruptibly()`, chỉ vào `finally { unlock(); }` **sau khi khóa được lấy thành công**:

```java
lock.lockInterruptibly(); // có thể ném trước khi sở hữu lock
try {
    // critical section
} finally {
    lock.unlock();
}
```

Đây là **đoạn minh họa trong một method khai báo `throws InterruptedException`**. Một exception xuất phát từ lúc đợi sẽ không đi vào `try/finally` trên, nên không vô tình unlock khóa mình chưa giữ.

### `Condition.await()`: đợi điều kiện trên explicit lock

`Condition` đi cùng một `Lock`, về ý tưởng tương tự việc chờ một predicate dưới monitor nhưng **không dùng** `Object.wait()`:

```java
// Các trường của một class:
private final ReentrantLock lock = new ReentrantLock();
private final Condition changed = lock.newCondition();
private boolean available;

void publish() {
    lock.lock();
    try {
        available = true;
        changed.signalAll();
    } finally {
        lock.unlock();
    }
}

void awaitAvailable() throws InterruptedException {
    lock.lock();
    try {
        while (!available) {
            changed.await();
        }
    } finally {
        lock.unlock();
    }
}
```

Đây là **đoạn mã thành viên của một class**; cần import `ReentrantLock` và `Condition` từ `java.util.concurrent.locks`. `await()` **nhả explicit lock tương ứng** trong khi đợi, rồi **lấy lại khóa trước khi trả về**; nó không nhả những khóa khác mà thread đồng thời giữ. `signal()/signalAll()` yêu cầu sở hữu khóa với `Condition` của `ReentrantLock` này. Cũng như monitor, signal **không tự chuyển quyền sở hữu**: waiter phải tranh lấy lại khóa, và luôn lặp `while` để kiểm tra predicate.

`Condition.await()`/`awaitNanos()` có thể bị interrupt; hàm chờ trả hoặc ném sau khi lấy lại lock theo hợp đồng của `Condition`/`ReentrantLock`. Tùy API chờ và tình huống, signal, timeout, interrupt hoặc thức dậy giả có thể làm vòng đợi kết thúc, nên predicate mới là nguồn chân lý của công việc.

### `LockSupport.park()/unpark()`: chờ ở tầng thấp hơn

`LockSupport.park()` có thể đặt thread vào `WAITING`, `parkNanos()` vào `TIMED_WAITING`. `unpark(thread)` cung cấp một permit để thread đó có thể đi tiếp; permit **không cộng dồn thành hàng đợi vô hạn**. `park()` có thể trả về khi bị interrupt hoặc vì spurious return; vì vậy bộ đồng bộ viết trên `LockSupport` cũng cần kiểm tra điều kiện trong vòng lặp.

**`park()` tự nó không nhả monitor hay `ReentrantLock` nào thread đang giữ.** Đây là điểm khác với `Object.wait()` và `Condition.await()`: các API await cao cấp chỉ nhả đúng lock tương ứng theo quy ước của chúng. Dùng park tùy tiện khi vẫn giữ khóa người khác cần có thể gây deadlock.

`Thread.interrupt()` có thể làm `park()` trở về, nhưng `park()` **không ném `InterruptedException`** và cũng không tự xóa interrupt status. Muốn kiểm tra mà không xóa cờ, dùng `Thread.currentThread().isInterrupted()`; `Thread.interrupted()` là phương thức static kiểm tra **và xóa** cờ của thread đang chạy.

## 6. Thực hành chạy được trên Java 17+

Ví dụ **`MonitorLockTransitions.java`** có ba phần. Hai phần đầu dùng `CountDownLatch` để sắp xếp thời điểm trước khi quan sát trạng thái: thread tranh intrinsic monitor được giữ ở `BLOCKED`; thread chờ `Object.wait()` được quan sát `WAITING` rồi `BLOCKED` khi được thông báo nhưng chưa lấy lại monitor. Phần ba cho thấy giành `ReentrantLock` và giải phóng bằng `finally`, **không giả định một trạng thái cố định** cho explicit lock.

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class MonitorLockTransitions {
    private static Thread.State awaitState(
            Thread thread, Thread.State expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.State current = thread.getState();
            if (current == expected) return current;
            Thread.sleep(1);
        }
        throw new IllegalStateException("Cannot observe " + expected);
    }

    private static void monitorEntry() throws InterruptedException {
        Object monitor = new Object();
        CountDownLatch aboutToEnter = new CountDownLatch(1);
        Thread contender = new Thread(() -> {
            aboutToEnter.countDown();
            synchronized (monitor) {
                System.out.println("2. contender entered monitor");
            }
        }, "monitor-contender");

        synchronized (monitor) {
            contender.start();
            aboutToEnter.await();
            System.out.println("1. contender state: " +
                    awaitState(contender, Thread.State.BLOCKED));
        }
        contender.join();
    }

    private static void monitorWait() throws InterruptedException {
        Object monitor = new Object();
        boolean[] ready = {false};
        CountDownLatch entered = new CountDownLatch(1);
        Thread waiter = new Thread(() -> {
            synchronized (monitor) {
                entered.countDown();
                while (!ready[0]) {
                    try {
                        monitor.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                System.out.println("5. waiter resumed with monitor");
            }
        }, "monitor-waiter");

        waiter.start();
        entered.await();
        System.out.println("3. waiter state before notify: " +
                awaitState(waiter, Thread.State.WAITING));
        synchronized (monitor) {
            ready[0] = true;
            monitor.notifyAll();
            System.out.println("4. waiter state after notify, before unlock: " +
                    awaitState(waiter, Thread.State.BLOCKED));
        }
        waiter.join();
    }

    private static void explicitLock() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch aboutToLock = new CountDownLatch(1);
        Thread contender = new Thread(() -> {
            aboutToLock.countDown();
            lock.lock();
            try {
                System.out.println("7. explicit lock acquired");
            } finally {
                lock.unlock();
            }
        }, "lock-contender");

        lock.lock();
        try {
            contender.start();
            aboutToLock.await();
            System.out.println("6. main still holds explicit lock");
        } finally {
            lock.unlock();
        }
        contender.join();
    }

    public static void main(String[] args) throws InterruptedException {
        monitorEntry();
        monitorWait();
        explicitLock();
    }
}
```

Biên dịch và chạy từ thư mục chứa file:

```bash
javac --release 17 MonitorLockTransitions.java
java MonitorLockTransitions
```

Kết quả trong lần chạy đã xác minh trên OpenJDK (và là thứ tự được điều khiển bởi monitor/latch trong chương trình này):

```text
1. contender state: BLOCKED
2. contender entered monitor
3. waiter state before notify: WAITING
4. waiter state after notify, before unlock: BLOCKED
5. waiter resumed with monitor
6. main still holds explicit lock
7. explicit lock acquired
```

**Giải thích từng điểm quan sát:**

1. `main` giữ monitor và thread `contender` định vào: trạng thái `BLOCKED` được giữ ổn định cho đến khi `main` rời `synchronized`.
2. Khi `main` nhả monitor, `contender` lấy được nó rồi in dòng 2; `join()` bảo đảm phần này kết thúc trước phần tiếp theo.
3. `waiter` có được monitor rồi gọi `wait()`, nhả monitor và ở `WAITING`.
4. `main` giành monitor, đặt `ready=true` và gọi `notifyAll()` nhưng vẫn giữ monitor. `waiter` đã được thông báo song còn đợi **reacquire**, nên quan sát được `BLOCKED`.
5. Sau khi `main` rời monitor, `waiter` vào lại, kiểm tra `ready` và tiếp tục.
6. Với explicit lock, `main` giữ `ReentrantLock` trong khi contender cố lấy khóa. Dòng 6 không suy đoán state là `BLOCKED`.
7. Sau `unlock()`, contender lấy explicit lock và in dòng 7.

`awaitState()` chỉ dùng để **lấy mẫu trạng thái trong bài thực hành**, không phải giao thức đồng bộ cho nghiệp vụ. Nó polling mỗi 1 ms và có deadline 5 giây để tránh treo vô hạn khi quan sát trạng thái gặp vấn đề. Trong môi trường cực chậm hoặc không được cấp CPU, ví dụ có thể ném `IllegalStateException` vì không quan sát trạng thái kịp thời; không có cam kết về deadline của scheduler. Không dùng polling `getState()` để quyết định khi nào được vào critical section trong sản phẩm thật.

**Lưu ý về sự đúng đắn:** `ready` ở ví dụ là mảng `boolean[]` chỉ để biến local được lambda truy cập; nó được đọc/ghi **dưới cùng monitor** nên không cần `volatile`. Mốc `entered.countDown()` nằm bên trong `synchronized`, còn `main` đọc trạng thái `WAITING` trước khi thông báo. Đây là phối hợp thí nghiệm; ứng dụng thực thường dùng `BlockingQueue`, `CountDownLatch` hoặc `Condition` thay vì tự viết vòng chờ mức thấp khi primitive có sẵn đáp ứng đúng ý định.

## 7. So sánh nhanh các hành vi chờ

| Thao tác | Phải giữ monitor/lock trước khi gọi? | Có nhả khóa trong lúc đợi? | Trạng thái thường quan sát |
|---|---|---|---|
| Vào `synchronized(obj)` khi obj bị giữ | Không, đang cố lấy | Chưa sở hữu khóa này | `BLOCKED` |
| `obj.wait()` | Phải giữ monitor `obj` | **Có**, monitor `obj`; sau đó reacquire | `WAITING` → có thể `BLOCKED` |
| `obj.wait(t>0)` | Phải giữ monitor `obj` | **Có**, monitor `obj` | `TIMED_WAITING` → có thể `BLOCKED` |
| `obj.notify()/notifyAll()` | Phải giữ monitor `obj` | **Không** | Caller tiếp tục chạy; waiter phải reacquire |
| `Thread.sleep(t)` | Không | **Không** nhả khóa đang giữ | `TIMED_WAITING` |
| `ReentrantLock.lock()` khi đang bận | Không | Chưa sở hữu khóa mới | Thường `WAITING` khi park; có thể `RUNNABLE` |
| `Condition.await()` | Phải sở hữu lock liên quan | **Có**, lock liên quan; sau đó reacquire | Thường `WAITING` |
| `Condition.awaitNanos(t)` | Phải sở hữu lock liên quan | **Có**, lock liên quan | Thường `TIMED_WAITING` |
| `LockSupport.park()` | Không | **Không** tự nhả khóa | `WAITING` |
| `LockSupport.parkNanos(t)` | Không | **Không** tự nhả khóa | `TIMED_WAITING` |

Các ô “thường quan sát” mô tả kết quả điển hình ở một thời điểm. **Không** coi bảng là lịch trình bắt buộc hoặc số lần chuyển trạng thái phải xuất hiện. Đặc biệt, sau khi một waiter rời hàng đợi điều kiện, nó vẫn phải đợi scheduler cấp CPU và có thể phải cạnh tranh để lấy lại khóa.

## 8. Deadlock, starvation và blocked không cùng nghĩa

### Deadlock

Giả sử thread A giữ monitor `left` và đợi monitor `right`, trong khi thread B giữ `right` và đợi `left`. Cả hai có thể ở `BLOCKED` vĩnh viễn vì vòng phụ thuộc. Với `ReentrantLock` và các tài nguyên khác, vòng đợi có thể biểu hiện dưới `WAITING` thay vì `BLOCKED`. Deadlock là **vòng phụ thuộc không thể tiến triển**, không phải một nhãn trạng thái.

Biện pháp hữu ích: quy định thứ tự lấy nhiều khóa nhất quán; giảm khóa lồng nhau; dùng timeout khi được phép và có chiến lược rollback; không gọi I/O chậm khi đang giữ monitor nếu có thể bố trí an toàn bên ngoài.

### Starvation

Một thread lâu không được lấy khóa dù các thread khác vẫn tiến triển, do phân phối CPU, cạnh tranh quá cao, hoặc chính sách chọn waiter. Nó có thể đổi trạng thái liên tục, không nhất thiết deadlock. `synchronized` không bảo đảm FIFO. `ReentrantLock(true)` cung cấp chính sách fairness cho acquisition dưới cạnh tranh ở mức API, nhưng không bảo đảm lịch CPU tuyệt đối và thường có đánh đổi throughput; `tryLock()` không thời hạn còn có hành vi barging cần lưu ý. Dùng số liệu chờ và SLA để quyết định, không chỉ bật fairness.

### Waiting hợp lệ

Một thread đang `WAITING` trong `BlockingQueue.take()` khi queue trống có thể chỉ đang nhàn rỗi đúng thiết kế. `Object.wait()` trong producer-consumer cũng có thể là hành vi chính xác. Muốn đánh giá lỗi, cần biết predicate nào chưa đúng, ai chịu trách nhiệm thay đổi predicate, có lệnh signal hay không và công việc có thực sự tiến triển.

## 9. Cách chẩn đoán trong chương trình thực

**Bước 1 — Nhìn đúng thời điểm và đúng thread.** Khi process đang sống, lấy một vài thread dump cách nhau:

```bash
jcmd <PID> Thread.print -l
```

Thay `<PID>` bằng process ID thực tế. Tùy JDK/OS, công cụ có thể cần quyền truy cập hoặc môi trường hỗ trợ attach.

**Bước 2 — Đọc trạng thái cùng stack:**

- `BLOCKED (on object monitor)` và dòng “waiting to lock” trỏ về monitor là tín hiệu cần tìm owner, critical section và đường lấy monitor.
- `WAITING (on object monitor)` đi với `Object.wait` gợi ý đợi predicate trên monitor; tìm logic `notify/notifyAll` và predicate.
- `WAITING (parking)` với `LockSupport.park`/AQS gợi ý explicit lock, condition, queue hoặc synchronizer khác; phải đọc stack để phân biệt.
- `TIMED_WAITING (sleeping)` là ngủ có thời hạn; **không** kết luận monitor đang được nhả.

**Bước 3 — Đối chiếu với dữ liệu theo thời gian.** Thread dump có thể bỏ lỡ lock contention rất ngắn. Dùng Java Flight Recorder để xem sự kiện `jdk.JavaMonitorEnter`, `jdk.JavaMonitorWait`, `jdk.ThreadPark`, thời lượng, ngưỡng sự kiện, thread và stack trace khi môi trường hỗ trợ:

```bash
jcmd <PID> JFR.start name=lock-check settings=profile duration=60s filename=locks.jfr
jfr summary locks.jfr
jfr print --events jdk.JavaMonitorEnter,jdk.JavaMonitorWait,jdk.ThreadPark locks.jfr
```

Môi trường phải có hỗ trợ JFR; các event có thể không được bật hoặc bị lọc theo duration/threshold, nên **không thấy event không chứng minh không có contention**. Một file recording chỉ có ý nghĩa khi workload đang tái hiện vấn đề trong khoảng ghi. Với chương trình demo chạy cực nhanh, có thể không kịp ghi lại sự kiện hữu ích.

**Bước 4 — Đo nguyên nhân, không đoán.** Xem lock owner, wait duration, số thread cùng tranh, độ dài critical section, thời gian làm I/O và các công việc đang đợi trong queue. Phân biệt CPU starvation, lock contention, chờ I/O và thread pool saturation; hướng tối ưu sẽ khác nhau.

### Java 21+ và virtual threads (lưu ý phạm vi)

Virtual threads không phải mỗi thread Java đều gắn vĩnh viễn với một OS thread. Các thao tác blocking được hỗ trợ có thể **unmount** virtual thread khỏi carrier; vì vậy không nên lấy số lượng platform threads và quy luật OS scheduling để áp nguyên xi vào mọi phép đo virtual thread. Tuy nhiên, monitor, điều kiện, lock ownership, interruption và invariants vẫn quan trọng. Ví dụ thực hành bên trên chủ đích dùng API phổ biến từ **Java 17+** và tạo **platform threads** để nghiên cứu `Thread.State` dễ quan sát.

## 10. Sai lầm thường gặp và cách sửa

1. **“Thread đợi `ReentrantLock` chắc chắn là `BLOCKED`.”** Nhầm intrinsic monitor với explicit lock. Xem call stack; AQS/park thường hiện `WAITING` hoặc `TIMED_WAITING`.
2. **“`notify()` khiến waiter chạy ngay.”** Nó chỉ chuyển waiter tới cơ hội tranh lại monitor; caller vẫn giữ monitor cho đến khi unlock.
3. **“Gọi `wait()` ở ngoài `synchronized` vẫn được.”** Sẽ ném `IllegalMonitorStateException` nếu không sở hữu đúng monitor.
4. **“`wait()` nhả hết mọi khóa.”** Chỉ nhả monitor của object được gọi; giữ những monitor khác có thể tạo vòng đợi.
5. **“`sleep()` giải phóng lock cho thread khác.”** Không. Nếu đang giữ monitor/explicit lock, thời gian ngủ vẫn giữ khóa đó.
6. **“Có notify rồi thì dùng `if` là đủ.”** Có thể spurious wakeup hoặc điều kiện bị thread khác đổi; dùng `while (!predicate) wait()`.
7. **“Chỉ cần thấy `WAITING` là tìm được deadlock.”** Cần wait graph, owner, stack và bằng chứng không tiến triển; chờ hợp lệ rất phổ biến.
8. **“`interrupt()` sẽ hủy mọi lần lấy khóa.”** Không áp dụng cho monitor entrance hay `ReentrantLock.lock()`; cần `lockInterruptibly()` hoặc `tryLock(timeout, unit)` để kiểm soát việc chờ bằng API phù hợp.
9. **“Lock được nhả khi `notifyAll()`/`signalAll()`.”** Không. Nhả khóa khi thoát `synchronized` hoặc `unlock()` đúng cách.
10. **“`Thread.getState()` có thể thay điều kiện đồng bộ.”** Không. Đây là công cụ quan sát; dùng lock, condition, latch, queue và predicate cho correctness.

## 11. Tự kiểm tra kiến thức

**Câu 1.** A giữ monitor `m`, B đang cố vào `synchronized(m)`. B thường ở trạng thái nào?  
**Đáp án:** `BLOCKED`. B chưa vào critical section, phải đợi monitor `m`.

**Câu 2.** B đang `m.wait()` (không timeout), A lấy được `m` và gọi `m.notifyAll()` nhưng vẫn giữ `m` thêm vài giây. B có thể quan sát trạng thái nào trong thời gian đó?  
**Đáp án:** B rời `WAITING`; khi chờ lấy lại monitor `m` sẽ ở `BLOCKED`. B không thể tiếp tục bên trong synchronized trước khi giành lại `m`.

**Câu 3.** Một thread chờ `ReentrantLock.lock()` xuất hiện `WAITING (parking)` trong dump. Điều này có mâu thuẫn với khái niệm “chờ khóa” không?  
**Đáp án:** Không. `Thread.State.BLOCKED` mô tả chờ intrinsic monitor; explicit lock có đường chờ dựa trên `park`.

**Câu 4.** Vì sao dùng `while (!ready) guard.wait()` tốt hơn `if (!ready) guard.wait()`?  
**Đáp án:** Phải kiểm tra lại predicate sau spurious wakeup, sau cạnh tranh với waiter khác, và khi trạng thái nghiệp vụ có thể thay đổi trong lúc thread reacquire monitor.

**Câu 5.** Thread đang giữ `a` và `b`, rồi gọi `a.wait()`. Khóa nào được nhả trong lúc chờ?  
**Đáp án:** Monitor `a` được nhả trong `wait()`; monitor `b` **không tự nhả**.

**Câu 6.** Đang `BLOCKED` vì monitor thì `interrupt()` có làm `synchronized` ném `InterruptedException` không?  
**Đáp án:** Không. Lối vào monitor không interruptible; nếu cần acquisition có khả năng hủy, thiết kế với `lockInterruptibly()` hoặc timed `tryLock` có kiểm soát.

## Tài liệu tham khảo

- [Java SE 17 — Thread.State](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.State.html): định nghĩa chính thức sáu trạng thái.
- [Java SE 17 — Object.wait/notify](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): sở hữu monitor, wait set, notify và reacquire.
- [Java SE 17 — ReentrantLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html): các kiểu acquisition, fairness, unlock.
- [Java SE 17 — Condition](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Condition.html): semantics của await/signal và predicate.
- [Java SE 17 — LockSupport](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/LockSupport.html): park/unpark và permit.
- [Java SE 17 — ThreadMXBean](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ThreadMXBean.html): hỗ trợ quan sát thread và contention.

**Ghi nhớ:** khi nhìn một thread “đang chờ khóa”, câu hỏi đầu tiên là **nó đang chờ *monitor entry*, đang đợi một *điều kiện*, hay đang chờ một *explicit lock*?** Chỉ sau khi biết cơ chế đó mới giải thích đúng trạng thái, việc nhả/lấy lại khóa và cách xử lý an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-lifecycle-004">Chuyển trạng thái bởi sleep(), join(), wait()</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển trạng thái Thread bởi `sleep()`, `join()` và `wait()` trong Java

## 1. Vì sao cần hiểu chuyển trạng thái?

Khi một ứng dụng Java thực hiện nhiều công việc đồng thời, có lúc một luồng **đang chạy hoặc sẵn sàng chạy**, có lúc luồng phải **chờ thời gian**, **chờ luồng khác kết thúc**, hoặc **chờ một điều kiện của dữ liệu dùng chung**. Ba API dễ gặp là:

- `Thread.sleep(...)`: **luồng đang thực thi** tạm dừng trong khoảng thời gian yêu cầu.
- `someThread.join(...)`: **luồng gọi `join`** chờ **luồng `someThread`** kết thúc, có thể kèm timeout.
- `lock.wait(...)`: **luồng đang giữ monitor của `lock`** tạm nhả monitor đó và chờ được đánh thức, có thể kèm timeout.

Cả ba có thể khiến một thread không tiếp tục chạy ngay, nhưng khác nhau về **ai chờ, chờ điều gì, có nhả monitor hay không, và điều kiện tiếp tục**. Phân biệt chúng giúp tránh khóa dữ liệu không cần thiết, nhầm lẫn vòng đời Thread, treo chương trình và lỗi mất tín hiệu.

Bài học xét concurrency của **Java nói chung**, tập trung vào API chuẩn của `java.lang.Thread` và `java.lang.Object`. Các trường hợp sử dụng framework không thuộc phạm vi bài này.

## 2. Sáu trạng thái quan sát bằng `Thread.State`

API `Thread.getState()` trả về một trong sáu giá trị dưới đây. Đây là **ảnh chụp trạng thái tại một thời điểm**, không phải lịch sử chuyển trạng thái hay cam kết thứ tự thực thi của bộ lập lịch.

| `Thread.State` | Ý nghĩa trong Java | Ví dụ |
| --- | --- | --- |
| `NEW` | Đối tượng Thread đã tạo nhưng chưa `start()` | `new Thread(task)` |
| `RUNNABLE` | Thread đang chạy hoặc sẵn sàng chạy ở mức JVM | Sau `start()`, khi không phải chờ theo các trạng thái khác |
| `BLOCKED` | Đang chờ chiếm monitor nội tại để đi vào `synchronized` (hoặc chiếm lại monitor) | Thread khác đang giữ monitor cần vào |
| `WAITING` | Đang chờ vô thời hạn một hành động của thread khác | `Object.wait()`, `Thread.join()` không timeout |
| `TIMED_WAITING` | Đang chờ với giới hạn thời gian | `Thread.sleep(500)`, `Thread.join(500)`, `Object.wait(500)` |
| `TERMINATED` | Phương thức `run()` đã kết thúc, kể cả kết thúc vì exception không được xử lý | Sau khi worker hoàn tất |

Không có trạng thái `SLEEPING` hay `JOINED` trong enum `Thread.State`. “Ngủ” qua `sleep` thể hiện bằng `TIMED_WAITING`; “đang join” thường là `WAITING` hoặc `TIMED_WAITING` của **luồng gọi `join`**, chứ không phải một trạng thái mới của worker.

Sơ đồ khái niệm (chỉ minh họa các đường thường gặp, không bao phủ mọi chuyển đổi):

```text
                  start()
     NEW -------------------------> RUNNABLE
                                      |
             sleep(duration)          | chờ monitor synchronized
             join(timeout)            +---------------------> BLOCKED
             wait(timeout)            |                           |
                   |                  |       lấy được monitor    |
                   v                  |<--------------------------+
              TIMED_WAITING <---------+
                   |        thời hạn hết / đánh thức / gián đoạn
                   +------------------> RUNNABLE (khi sẵn sàng)
                                      |
             join() / wait()          |   không có timeout
                   |                  |
                   v                  |
                WAITING --------------+
                  kết thúc chờ
                                      |
                                      | run() kết thúc
                                      v
                                  TERMINATED
```

Đường quay lại sau `wait` **có thể phải qua `BLOCKED`** để giành lại monitor trước khi tiếp tục. Sau khi một thread hoàn thành, không thể gọi lại `start()` trên cùng đối tượng. `RUNNABLE` không nhất thiết có nghĩa thread đang chiếm CPU tại thời điểm quan sát; trạng thái thực tế thay đổi rất nhanh.

## 3. `Thread.sleep()`: tạm dừng luồng hiện tại theo thời gian

### 3.1. API và điều kiện tiếp tục

`Thread.sleep(long millis)` và `Thread.sleep(long millis, int nanos)` là **phương thức static**. Chúng tác động tới **luồng hiện tại đang gọi**, không phải một đối tượng Thread được chỉ định. Không nên viết `worker.sleep(100)` để diễn đạt ý định “cho worker ngủ”: Java cho phép gọi static qua biến tham chiếu ở mức cú pháp nhưng thao tác vẫn diễn ra trên **luồng gọi**, gây hiểu lầm. Viết rõ `Thread.sleep(100)`.

Trong thời gian ngủ do `Thread.sleep(500)`, trạng thái thường quan sát được của luồng ngủ là `TIMED_WAITING`. Khi khoảng thời gian chờ hoàn tất, luồng trở thành ứng viên được tiếp tục chạy; **không có bảo đảm nó chạy đúng sau 500 ms**, bởi độ chính xác đồng hồ và lịch trình CPU phụ thuộc JVM/hệ điều hành.

`sleep` không phải công cụ đợi một điều kiện nghiệp vụ trở thành đúng. Sau khoảng thời gian đó, điều kiện bên ngoài có thể vẫn sai. Nếu cần phản ứng với sự kiện hoặc dữ liệu thay đổi, dùng điều phối phù hợp (monitor với `wait/notifyAll`, `BlockingQueue`, latch, condition, future...).

### 3.2. `sleep` **không nhả monitor** đang giữ

Đây là điểm khác biệt then chốt với `Object.wait()`. Một thread đang trong `synchronized(lock)` vẫn **sở hữu monitor của `lock`** nếu gọi `Thread.sleep` bên trong khối. Thread khác muốn vào `synchronized(lock)` có thể chuyển sang `BLOCKED` cho đến khi monitor được nhả.

Ví dụ hoàn chỉnh, tạo tệp `SleepKeepsMonitorDemo.java`:

```java
import java.util.concurrent.CountDownLatch;

public class SleepKeepsMonitorDemo {
    public static void main(String[] args) throws InterruptedException {
        Object lock = new Object();
        CountDownLatch entered = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            synchronized (lock) {
                entered.countDown();
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                System.out.println("worker: vẫn giữ lock");
            }
        }, "sleep-worker");

        worker.start();
        entered.await();

        synchronized (lock) {
            System.out.println("main: đã lấy lock");
        }
        worker.join();
    }
}
```

Chạy với Java 17 trở lên:

```bash
javac SleepKeepsMonitorDemo.java
java SleepKeepsMonitorDemo
```

Kết quả khi worker không bị interrupt:

```text
worker: vẫn giữ lock
main: đã lấy lock
```

`entered.countDown()` được gọi khi worker đang ở bên trong `synchronized(lock)`. `main` chỉ tiếp tục sau `entered.await()` rồi cố gắng lấy cùng monitor. Trong lúc `worker` ngủ, nó **vẫn giữ lock**, vì vậy main không thể in trước dòng của worker. Nếu đo trạng thái main đúng khi đang chờ chiếm monitor, có thể thấy `BLOCKED`; worker trong khoảng ngủ thường là `TIMED_WAITING`. Không dùng `getState()` để khẳng định sẽ bắt gặp hai trạng thái này tại một thời điểm bất kỳ.

Trong thực tế, tránh ngủ lâu khi giữ monitor chung: việc này có thể làm mọi luồng khác cần monitor phải chờ theo, giảm throughput và tăng latency.

### 3.3. Khi bị ngắt

`Thread.sleep` khai báo `throws InterruptedException`. Nếu thread bị interrupt trước hoặc trong khi ngủ, lời gọi ném `InterruptedException`; khi exception được ném theo cơ chế này, **cờ interrupt đã được xóa**. Mã nhận exception thường truyền tiếp hoặc khôi phục cờ ngắt bằng `Thread.currentThread().interrupt()` rồi thoát một cách có kiểm soát. Bắt exception và tiếp tục vòng lặp vô hạn có thể làm yêu cầu hủy không còn tác dụng.

## 4. `Thread.join()`: caller chờ một Thread khác kết thúc

### 4.1. Ai chuyển sang trạng thái chờ?

Cho hai thread: `main` và `worker`. Khi main gọi `worker.join()`:

- **main** là luồng chờ: nó thường vào `WAITING` nếu `join()` không timeout, hoặc `TIMED_WAITING` nếu dùng `join(timeout)`.
- **worker** vẫn tiếp tục vòng đời của riêng nó. Worker có thể `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING` hoặc đã `TERMINATED` tùy lúc quan sát; **`join` không làm worker tự ngủ hoặc dừng**.
- Khi worker kết thúc, main có thể tiếp tục sau `join()`. Trong trường hợp `join` có timeout, main cũng có thể tiếp tục vì hết thời gian dù worker vẫn sống.

Đây là lỗi dễ mắc: “`worker.join()` khiến worker chuyển sang `WAITING`” là **sai đối tượng**. Người đợi là thread gọi `join`.

### 4.2. Ví dụ thứ tự hoàn thành xác định

Tạo tệp `JoinOrderDemo.java`:

```java
public class JoinOrderDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            System.out.println("worker: hoàn thành");
        }, "worker");

        worker.start();
        worker.join();

        System.out.println("main: tiếp tục sau join");
    }
}
```

```bash
javac JoinOrderDemo.java
java JoinOrderDemo
```

Kết quả:

```text
worker: hoàn thành
main: tiếp tục sau join
```

`start()` khởi động worker, còn `join()` khiến main đợi worker kết thúc. Vì vậy thứ tự hai dòng được bảo đảm bởi cấu trúc chương trình; nó không phụ thuộc việc main hay worker được CPU chạy trước. Nếu bỏ `join()`, thứ tự hai dòng không còn được bảo đảm.

### 4.3. `join(timeout)` có thể trở về khi worker chưa hoàn thành

`worker.join(200)` yêu cầu chờ **tối đa khoảng 200 ms** để worker kết thúc. Sau khi lời gọi trả về bình thường, cần kiểm tra `worker.isAlive()` nếu nghiệp vụ phải biết worker thực sự đã xong:

```java
worker.join(200);
if (worker.isAlive()) {
    System.out.println("Worker chưa hoàn thành ở thời điểm kiểm tra");
}
```

Đây là đoạn minh họa đặt trong ngữ cảnh đã có biến `worker` và xử lý `InterruptedException`. Dòng thông báo có thể xuất hiện hoặc không, tùy thời gian thực thi. Không được coi việc `join(timeout)` trả về là bằng chứng chắc chắn worker đã `TERMINATED`. Tham số `millis = 0` của `join(0)` có nghĩa là **chờ vô thời hạn**, không phải “không chờ”.

`Thread.join()` có thể bị interrupt khi caller đang chờ. Khi đó **caller** nhận `InterruptedException`; điều này tự nó không hủy hoặc ngắt worker. Nếu muốn yêu cầu worker dừng, cần gửi tín hiệu riêng, ví dụ `worker.interrupt()` với hợp đồng hủy phù hợp.

### 4.4. Quan hệ happens-before

Khi một thread kết thúc, các hành động nó đã thực hiện có quan hệ *happens-before* với hành động của thread khác phát hiện được việc kết thúc đó qua `join()` thành công. Nhờ vậy, mã sau `worker.join()` có thể quan sát các ghi của worker đã kết thúc theo bảo đảm của Java Memory Model.

Tuy nhiên, `join` là cơ chế chờ **kết thúc thread**, không phải cơ chế bảo vệ một cấu trúc dữ liệu khi nhiều thread vẫn đang sửa nó đồng thời. Nếu các thread khác vẫn cập nhật dữ liệu chung, vẫn cần đồng bộ phù hợp.

## 5. `Object.wait()`: nhả monitor và chờ điều kiện

### 5.1. `wait` là phương thức của `Object`, không phải `Thread`

Các overload phổ biến là `lock.wait()`, `lock.wait(long timeout)` và `lock.wait(long timeout, int nanos)`. Mỗi đối tượng Java có monitor nội tại để dùng với `synchronized`, `wait`, `notify`, `notifyAll`.

Muốn gọi `lock.wait()`, **luồng hiện tại phải đang giữ monitor của chính `lock`**, thông thường bằng:

```java
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
}
```

Đây là đoạn minh họa với `lock` và `ready` đã được khai báo. Nếu gọi `lock.wait()` khi không sở hữu monitor của `lock`, Java ném `IllegalMonitorStateException`. Giữ monitor của một đối tượng **khác** không đáp ứng điều kiện.

Khác `sleep`, khi gọi `lock.wait()` hợp lệ, luồng đang chờ sẽ **nhả monitor của chính `lock`** và chuyển sang chờ. Nhờ đó một luồng khác có thể vào `synchronized(lock)`, thay đổi trạng thái được bảo vệ và gửi thông báo.

### 5.2. `wait`, `notify` và `notifyAll` phối hợp ra sao?

Giả sử consumer cần đợi producer đặt `ready = true`:

1. Consumer vào `synchronized(lock)`, kiểm tra điều kiện `ready`.
2. Nếu chưa sẵn sàng, consumer gọi `lock.wait()`. Nó nhả monitor `lock` và chờ (thường thấy `WAITING`).
3. Producer lấy monitor `lock`, cập nhật `ready = true` rồi gọi `lock.notifyAll()`.
4. `notifyAll()` **không lập tức nhả monitor** của producer và cũng không bảo đảm consumer chạy ngay.
5. Producer rời `synchronized(lock)` để nhả monitor; consumer phải **lấy lại monitor `lock`** trước khi `wait()` trả về. Trong lúc giành lại monitor, consumer có thể `BLOCKED`.
6. Consumer kiểm tra lại `ready` bằng `while` rồi xử lý điều kiện đã thỏa.

Luồng chờ không được bảo đảm chạy ngay khi nhận thông báo: nó còn phải tranh quyền chiếm monitor với các thread khác. `wait()` có thể trở về bởi thông báo, timeout (với overload có thời hạn), ngắt, hoặc **spurious wakeup**. Vì vậy luôn kiểm tra điều kiện trong **vòng `while`**, không dùng một câu `if` duy nhất.

### 5.3. Ví dụ producer–consumer đầy đủ, không bị mất tín hiệu

Tạo tệp `WaitNotifyDemo.java`:

```java
import java.util.concurrent.CountDownLatch;

public class WaitNotifyDemo {
    public static void main(String[] args) throws InterruptedException {
        Object lock = new Object();
        boolean[] ready = {false};
        CountDownLatch consumerEntered = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            synchronized (lock) {
                while (!ready[0]) {
                    System.out.println("consumer: đang chờ");
                    consumerEntered.countDown();
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                System.out.println("consumer: đã nhận điều kiện");
            }
        }, "consumer");

        consumer.start();
        consumerEntered.await();

        synchronized (lock) {
            ready[0] = true;
            lock.notifyAll();
            System.out.println("producer: đã phát tín hiệu");
        }

        consumer.join();
        System.out.println("main: hoàn thành");
    }
}
```

```bash
javac WaitNotifyDemo.java
java WaitNotifyDemo
```

Kết quả khi không có interrupt:

```text
consumer: đang chờ
producer: đã phát tín hiệu
consumer: đã nhận điều kiện
main: hoàn thành
```

**Vì sao kết quả xác định?** Consumer in thông báo và gọi `consumerEntered.countDown()` khi đang giữ `lock`. Main đợi latch rồi mới thử lấy `lock`. Consumer phải đi đến `lock.wait()` và nhả monitor trước khi main có thể đặt `ready[0] = true`, vì vậy không có khoảng trống giữa kiểm tra điều kiện và nhả monitor để mất thông báo. Producer in trong lúc giữ monitor nên consumer không thể in “đã nhận điều kiện” trước nó. Cuối cùng, `join()` đặt dòng “main: hoàn thành” sau khi consumer kết thúc.

Biến `ready` được đọc/ghi **chỉ trong `synchronized(lock)`**, tạo quan hệ nhìn thấy dữ liệu qua việc nhả/chiếm monitor; dùng mảng một phần tử ở đây chỉ để có một holder có thể được lambda tham chiếu, không phải cơ chế đồng bộ. Trong ứng dụng thật, nên dùng đối tượng trạng thái có tên rõ ràng.

**Vì sao vẫn dùng `while`?** Java cho phép wakeup không có `notify/notifyAll` tương ứng, hoặc một thread khác có thể làm điều kiện trở lại sai trước lúc consumer lấy monitor. Vòng `while` buộc kiểm tra lại mệnh đề thật sự cần đáp ứng.

### 5.4. `wait(timeout)` và kết quả đánh thức

Trong `synchronized(lock)`, `lock.wait(500)` đặt thời hạn chờ. Thread thường là `TIMED_WAITING` trong lúc đợi, nhưng **trở về không chứng minh điều kiện đã đúng**. Cần kiểm tra điều kiện một lần nữa. `wait(0)` có nghĩa là chờ vô thời hạn.

Tương tự, `notify()` thường đánh thức **một** thread chờ trên monitor đó, nhưng không chọn theo thứ tự bạn mong muốn. `notifyAll()` đánh thức tất cả thread đang chờ trên monitor đó; chúng tiếp tục cạnh tranh để lấy lại monitor và tự kiểm tra điều kiện. Với nhiều loại điều kiện cùng dùng một monitor, `notifyAll()` thường giảm rủi ro đánh thức nhầm thread không thể tiến triển, dù có thể làm nhiều thread thức dậy và tranh chấp hơn. Chọn chiến lược phù hợp với bất biến thực tế.

### 5.5. `wait` chỉ nhả monitor đang được đợi

Nếu một thread đang giữ cả `lockA` và `lockB`, rồi gọi `lockA.wait()`, nó nhả monitor `lockA` nhưng **vẫn giữ `lockB`**. Do đó đặt `wait` trong cấu trúc khóa lồng nhau dễ gây bế tắc nếu producer cần `lockB` để tạo điều kiện đánh thức. `wait` cũng không tự nhả các loại khóa khác như `ReentrantLock`. Khi dùng `ReentrantLock`, hãy dùng `Condition.await/signal` tương ứng thay vì pha trộn với `Object.wait/notify` trên một đối tượng không liên quan.

## 6. So sánh trực tiếp ba API

| Câu hỏi | `Thread.sleep(100)` | `worker.join()` | `lock.wait()` |
| --- | --- | --- | --- |
| API nằm ở đâu? | Phương thức static của `Thread` | Phương thức instance của `Thread` | Phương thức instance của `Object` |
| Ai chờ? | Thread hiện tại | Thread gọi `join` | Thread đang giữ monitor `lock` và gọi `wait` |
| Chờ điều gì? | Khoảng thời gian được yêu cầu | Một thread đích kết thúc; hoặc timeout | Thông báo/interrupt/spurious wakeup; hoặc timeout |
| Trạng thái thường quan sát | `TIMED_WAITING` | `WAITING` hoặc `TIMED_WAITING` | `WAITING` hoặc `TIMED_WAITING` |
| Cần sở hữu monitor? | Không | Không | Có, phải giữ monitor của chính đối tượng gọi `wait` |
| Có nhả monitor đã giữ? | Không | Không tự nhả các monitor caller đang giữ | Có, nhả monitor của đối tượng gọi `wait` |
| Cần kiểm tra điều kiện sau khi trở về? | Nếu nghiệp vụ dựa điều kiện: có | Với timeout: kiểm tra `isAlive()` | Có, dùng `while` cho điều kiện được bảo vệ |
| Khi caller bị interrupt trong lúc chờ? | `InterruptedException` | `InterruptedException` | `InterruptedException` |
| Mục đích chính | Trì hoãn thời gian, mô phỏng chờ khi học | Điều phối kết thúc vòng đời thread | Phối hợp điều kiện giữa các thread qua monitor |

**Đặc biệt với `join`:** đối tượng `worker` là **đích được chờ**, không phải “thread bị đưa vào WAITING”. Trạng thái chờ thuộc về caller. **Đặc biệt với `wait`:** thread nhả monitor của đối tượng chờ, nhưng khi `wait` trả về thì thread đã lấy lại monitor đó. **Đặc biệt với `sleep`:** thread có thể ngủ trong `synchronized` mà vẫn giữ monitor.

## 7. Bị interrupt khi đang `sleep`, `join` hoặc `wait`

Cả ba API đều có thể ném `InterruptedException` khi thread thực hiện lời gọi bị ngắt. Ngắt là **tín hiệu hợp tác**: nó không phải thao tác cưỡng chế dừng JVM thread một cách tức thì. Lời gọi đang chờ có thể ném exception và thông thường xóa trạng thái interrupt trước khi chuyển quyền cho `catch`.

Tạo tệp `SleepInterruptDemo.java` để thấy rõ tác động lên **luồng đang ngủ**:

```java
import java.util.concurrent.CountDownLatch;

public class SleepInterruptDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            ready.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                System.out.println("catch: interrupted=" +
                        Thread.currentThread().isInterrupted());
                Thread.currentThread().interrupt();
                System.out.println("restored: interrupted=" +
                        Thread.currentThread().isInterrupted());
            }
        }, "worker");

        worker.start();
        ready.await();
        worker.interrupt();
        worker.join();
        System.out.println("main: worker đã kết thúc");
    }
}
```

```bash
javac SleepInterruptDemo.java
java SleepInterruptDemo
```

Kết quả:

```text
catch: interrupted=false
restored: interrupted=true
main: worker đã kết thúc
```

Ngay cả khi main ngắt worker trước khi worker thực sự bước vào `sleep`, `sleep` vẫn phát hiện interrupt và ném exception; vì vậy ví dụ không cần trông cậy vào việc main “canh đúng 10 giây”. `isInterrupted()` trong `catch` là `false` vì cờ đã bị xóa khi `InterruptedException` được ném. Mã khôi phục cờ để tầng gọi có thể nhận biết yêu cầu ngắt.

Đối với `join`, nếu main bị interrupt lúc đang chờ `worker.join()`, **main** mới là thread nhận `InterruptedException`; worker không tự bị interrupt. Đối với `wait`, thread đang đợi monitor nhận `InterruptedException`; trước khi ném exception, Java phải hoàn tất việc lấy lại monitor theo hợp đồng `wait`.

Không nuốt `InterruptedException` rồi tiếp tục chờ vô hạn nếu chức năng cần hỗ trợ hủy. Nếu không thể khai báo `throws InterruptedException`, mẫu xử lý phổ biến là khôi phục cờ ngắt và kết thúc task hoặc chuyển trạng thái hủy lên tầng điều phối. Cũng không gọi `Thread.stop()` để giải quyết một thread đang chờ; đây là API đã lỗi thời và không an toàn.

## 8. Trạng thái nào xuất hiện sau khi được đánh thức?

Không thể áp dụng máy móc quy tắc “`wait` xong là `RUNNABLE` ngay”:

- `sleep`: hết thời gian hoặc bị interrupt, thread có thể sẵn sàng chạy trở lại; các monitor mà nó đang giữ vẫn được giữ cho đến lúc rời khối `synchronized`.
- `join`: worker kết thúc hoặc khoảng chờ hết hạn, caller được phép tiếp tục; caller vẫn phải được lên lịch CPU.
- `wait`: sau `notify/notifyAll`, hết thời hạn, spurious wakeup hoặc interrupt, thread còn phải **giành lại monitor của đối tượng `wait`**. Nếu monitor đang do người khác giữ, nó có thể được quan sát ở `BLOCKED` trước khi quay lại thực thi.
- Nếu không được báo thức, không hết thời gian, không bị interrupt và không có spurious wakeup, `wait()` vô thời hạn có thể tiếp tục chờ mãi. Đây là lý do chương trình cần đảm bảo một đường thay đổi điều kiện và báo thức rõ ràng.

`Thread.getState()` là công cụ chẩn đoán. Ví dụ, `System.out.println(worker.getState())` có thể giúp quan sát, nhưng kết quả là một snapshot và có thể thay đổi ngay sau đó. **Không sử dụng vòng lặp canh `getState() == WAITING` để thay thế giao thức điều phối đúng**. Khi cần kiểm chứng trình tự, dùng latch, monitor hoặc `Future` như trong các ví dụ thay vì phụ thuộc lịch biểu CPU.

## 9. Lỗi phổ biến và nguyên nhân

### 9.1. Nhầm đối tượng chờ khi gọi `join`

**Nhận định sai:** “Gọi `worker.join()` làm worker ngủ.”

**Đúng:** caller của `worker.join()` chờ worker hoàn thành; worker vẫn chạy bình thường. Nếu worker lại chờ caller bằng một cơ chế khác, có thể tạo vòng chờ (deadlock). Hãy vẽ quan hệ ai chờ ai trước khi thêm lời gọi `join`.

### 9.2. Ngủ khi giữ monitor để đợi thread khác thay đổi dữ liệu

```java
synchronized (lock) {
    while (!ready) {
        Thread.sleep(100);
    }
}
```

Đoạn mã minh họa trên có lỗi thiết kế: nếu luồng producer cũng cần `synchronized(lock)` để đổi `ready`, consumer vẫn giữ lock trong các lần `sleep` và kiểm tra nên producer không thể vào để làm điều kiện đúng. **Sửa bằng `lock.wait()` trong vòng `while`** để nhả monitor lúc đợi, hoặc dùng cơ chế đồng bộ cấp cao phù hợp.

### 9.3. Gọi `wait()` ở ngoài `synchronized`

```java
lock.wait(); // IllegalMonitorStateException nếu caller không giữ monitor của lock
```

Phải gọi bên trong `synchronized(lock)` (hoặc khi thực sự sở hữu monitor đó). Việc khai báo biến `lock` là `final` không làm thread sở hữu monitor.

### 9.4. Dùng `if` thay `while` để đợi điều kiện

```java
synchronized (lock) {
    if (!ready) {
        lock.wait();
    }
    useReadyData();
}
```

Đây là đoạn minh họa có rủi ro. `wait` có thể trở về mà điều kiện vẫn sai; cần thay `if` bằng `while` và giữ việc kiểm tra điều kiện cùng dữ liệu dùng chung trong vùng được bảo vệ. `useReadyData()` là công việc do ứng dụng định nghĩa.

### 9.5. Gọi `notify` quá sớm hoặc không giữ cùng monitor

Nếu producer gọi `notifyAll` trên một đối tượng khác với đối tượng consumer chờ, consumer không được đánh thức. Nếu không lưu điều kiện trong dữ liệu và báo hiệu trước khi consumer bắt đầu đợi, thông báo có thể bị mất. Giao thức “kiểm tra điều kiện + `wait`” dưới **cùng monitor**, và producer “đổi điều kiện + `notifyAll`” dưới **cùng monitor**, giúp tránh lỗ hổng này.

### 9.6. Quên rằng timeout không đồng nghĩa với hoàn thành

`worker.join(100)` có thể trả về dù worker chưa kết thúc. `lock.wait(100)` có thể trả về dù producer chưa báo hiệu. `Thread.sleep(100)` chỉ làm thread đợi khoảng thời gian, không đảm bảo một sự kiện cụ thể đã xảy ra. Sau timeout, hãy kiểm tra mệnh đề thực sự cần thiết, không mặc định điều kiện đã đúng.

### 9.7. Quên vòng đời và khả năng bị ngắt

Không xử lý `InterruptedException` có thể làm ứng dụng hủy task không sạch hoặc chờ không dứt. Với `join` trên thread không bao giờ kết thúc, caller có thể bị chặn mãi. Với `wait`, nếu không còn luồng nào có thể thay đổi điều kiện và đánh thức, có nguy cơ treo nghiệp vụ. Cần thiết kế đường thoát, timeout và cleanup phù hợp.

## 10. Thực tế nên dùng API nào?

**`sleep`** phù hợp khi bài toán thật sự cần trì hoãn thời gian hoặc mô phỏng tác vụ chờ trong môi trường học tập. Không dùng nó làm cách “đồng bộ đại khái” giữa các thread.

**`join`** phù hợp khi một thread quản lý cần chắc chắn rằng một thread khác đã kết thúc, chẳng hạn đóng một worker trước khi kết thúc chương trình. Nếu task do `ExecutorService` quản lý và cần kết quả hoặc lỗi, `Future.get()` thường diễn đạt ý định tốt hơn.

**`wait/notifyAll`** thích hợp để hiểu cơ chế monitor nguyên thủy và khi bạn thật sự cần tự quản lý một điều kiện được bảo vệ bởi `synchronized`. Trong mã ứng dụng thực tế, các cấu trúc như `BlockingQueue`, `CountDownLatch`, `Semaphore`, `Condition` hay `CompletableFuture` thường giúp diễn đạt mục đích rõ và giảm lỗi giao thức. Chúng có cơ chế chờ riêng, không nên gọi chéo `Object.wait` trên một đối tượng tùy ý rồi cho rằng sẽ nhận tín hiệu từ các cấu trúc đó.

Với **virtual thread** (Java 21+), các phương thức `sleep` và `join` vẫn có ý nghĩa đợi tương ứng, và các trạng thái `Thread.State` vẫn là trạng thái của thread Java. Cơ chế JVM có thể tháo virtual thread khỏi carrier trong nhiều tình huống chờ để tiết kiệm OS thread; điều đó **không thay đổi quy tắc monitor**: `sleep` không nhả monitor, `wait` phải giữ rồi nhả monitor của đối tượng, `notifyAll` không nhả monitor ngay. Trong Java 21, thao tác chặn khi đang ở vùng `synchronized` có thể gây pinning carrier; các phiên bản Java mới có cải tiến, nhưng cần đo trên JDK và thư viện thực tế. Đừng dùng virtual thread để thay thế quy tắc đồng bộ dữ liệu.

## 11. Tự kiểm tra

**Câu 1.** Main gọi `Thread.sleep(300)` khi đang trong `synchronized(lock)`, và một worker khác cố vào `synchronized(lock)`. Hai thread có thể ở trạng thái gì?

**Trả lời:** Main thường `TIMED_WAITING` do `sleep` nhưng vẫn giữ monitor của `lock`. Worker có thể `BLOCKED` vì chờ monitor. Trạng thái quan sát là snapshot và tùy thời điểm.

**Câu 2.** Main gọi `worker.join()` trong khi worker đang chạy. Thread nào đang chờ và điều gì xảy ra khi worker kết thúc?

**Trả lời:** Main là caller nên thường `WAITING`; worker tiếp tục công việc. Khi worker kết thúc, main có thể tiếp tục sau `join()`.

**Câu 3.** Consumer gọi `lock.wait()` trong `synchronized(lock)`. Tại sao producer có thể thay đổi dữ liệu và gọi `notifyAll()` trên cùng lock?

**Trả lời:** `wait` nhả monitor của `lock`, cho producer cơ hội chiếm nó. Producer cập nhật dữ liệu và phát tín hiệu; consumer phải chiếm lại monitor trước khi trở về từ `wait`, rồi kiểm tra điều kiện bằng `while`.

**Câu 4.** `lock.wait(100)` trả về bình thường. Có thể khẳng định producer đã báo thức và điều kiện đã đúng chưa?

**Trả lời:** Không. Có thể hết thời gian, thông báo hoặc spurious wakeup; cần kiểm tra lại điều kiện.

**Câu 5.** `worker.join(100)` trả về, worker chắc chắn `TERMINATED` chưa?

**Trả lời:** Chưa. `join(timeout)` có thể hết thời hạn. Kiểm tra `worker.isAlive()` để biết worker còn sống ở thời điểm kiểm tra.

**Câu 6.** Vì sao `Thread.sleep` không giúp tránh mất thông báo như mô hình `while (!ready) lock.wait()`?

**Trả lời:** `sleep` không gắn kiểm tra điều kiện với việc nhả monitor, cũng không được `notify` đánh thức như protocol monitor. Nếu ngủ trong `synchronized`, thread còn giữ monitor làm producer không thể cập nhật điều kiện.

## 12. Kết luận

Ba lời gọi cùng có thể khiến luồng tạm dừng nhưng phục vụ **ba nhu cầu khác nhau**:

- `sleep(duration)`: **luồng hiện tại chờ thời gian**, thường `TIMED_WAITING`, không nhả monitor đang giữ.
- `other.join()`: **luồng gọi chờ `other` kết thúc**, thường `WAITING` hoặc `TIMED_WAITING`, không làm `other` chuyển sang trạng thái chờ.
- `lock.wait()`: **luồng đang sở hữu monitor `lock` nhả chính monitor đó và chờ điều kiện được đánh thức**, thường `WAITING`; sau khi được đánh thức còn phải lấy lại monitor và kiểm tra điều kiện.

Nắm được **ai chờ, chờ cái gì, monitor nào được nhả và điều kiện nào cho phép tiếp tục** là nền tảng để đọc đúng vòng đời Thread và thiết kế chương trình concurrency an toàn trong Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-lifecycle-005">Quan sát trạng thái bằng Thread.getState()</a>

<details>
<summary>Xem chi tiết</summary>

# Quan sát trạng thái Thread bằng `Thread.getState()` trong Java

Khi một chương trình Java chạy đồng thời nhiều công việc, ta thường muốn trả lời: thread đã khởi động chưa, đang có cơ hội chạy, đang chờ khóa, đang chờ tín hiệu, đang chờ có giới hạn thời gian hay đã kết thúc? **`Thread.getState()`** là API quan sát nhanh vòng đời của một `Thread`. Tuy nhiên, trạng thái được trả về là **ảnh chụp tại một thời điểm**, không phải nhật ký đầy đủ hay cam kết thread vẫn ở trạng thái đó khi ta xử lý kết quả.

Bài học tập trung vào concurrency của **Java nói chung**, qua `java.lang.Thread` và các tiện ích đồng bộ trong `java.util.concurrent`. Trọng tâm là biết **đọc đúng sáu giá trị `Thread.State`**, tạo được tình huống quan sát, hiểu giới hạn và dùng trạng thái để hỗ trợ chẩn đoán. Không dùng cơ chế riêng của Spring.

## 1. `Thread.getState()` trả về gì?

Chữ ký:

```java
public Thread.State getState()
```

Đây là **chữ ký API**, không phải một chương trình Java độc lập. `getState()` trả về một phần tử của enum lồng `Thread.State`, mô tả trạng thái của **thread được gọi** tại thời điểm quan sát:

```java
Thread worker = new Thread(() -> doWork());
Thread.State snapshot = worker.getState();
```

Đoạn trên là minh họa ý tưởng; `doWork()` là tên phương thức giả định. Có thể gọi `getState()` trước hoặc sau `start()`, trong khi thread chạy, hoặc khi nó đã kết thúc. Không cần `synchronized` chỉ để gọi phương thức quan sát này.

Có **sáu** giá trị:

| `Thread.State` | Cách đọc | Tình huống tiêu biểu |
| --- | --- | --- |
| `NEW` | Đã tạo đối tượng `Thread` nhưng chưa `start()` | `new Thread(task)` vừa được gọi |
| `RUNNABLE` | Đang có khả năng thực thi trong JVM, có thể đang chạy hoặc chờ được cấp CPU/tài nguyên hệ điều hành | Thread đã `start()` và đang thực hiện công việc hoặc sẵn sàng chạy |
| `BLOCKED` | Đang chờ **monitor lock** để vào hoặc trở lại một vùng `synchronized` | Hai thread tranh chấp cùng monitor |
| `WAITING` | Đang chờ vô thời hạn theo một cơ chế chờ, tới khi được đánh thức/hoàn tất/interrupt tùy API | `Object.wait()` không timeout, `Thread.join()` không timeout, `LockSupport.park()` |
| `TIMED_WAITING` | Đang chờ với thời hạn hoặc khoảng thời gian được chỉ định | `Thread.sleep(...)`, `Object.wait(timeout)`, `Thread.join(timeout)`, `LockSupport.parkNanos(...)` |
| `TERMINATED` | Hàm `run()` đã kết thúc, dù kết thúc bình thường hay bởi uncaught exception | Thread đã hoàn tất |

**Không có `RUNNING`, `SLEEPING`, `PARKED` hay `DEAD` trong enum này.** Những cách gọi đó đôi khi xuất hiện trong sơ đồ dạy học hoặc công cụ chẩn đoán, nhưng không phải giá trị trả về của `Thread.getState()`.

`RUNNABLE` là trường hợp dễ hiểu sai nhất: nó **không chứng minh thread thực sự chiếm CPU tại lúc quan sát**. Tài liệu Java mô tả một thread ở trạng thái này có thể đang thực thi trong JVM nhưng vẫn chờ những tài nguyên ở mức hệ điều hành, chẳng hạn bộ xử lý. Từ một ảnh chụp `RUNNABLE` không suy ra CPU usage.

## 2. Sơ đồ vòng đời: trạng thái có thể chuyển đổi ra sao?

Sơ đồ khái quát:

```text
new Thread(...) 
      |
      v
     NEW
      |
      | start()
      v
  RUNNABLE <----------------------------------------------+
      |                                                  |
      | tranh monitor / synchronized                      |
      +-------> BLOCKED ----- lấy được monitor -----------+
      |                                                  |
      | wait/park/join không timeout                      |
      +-------> WAITING ----- được phép tiếp tục ---------+
      |                                                  |
      | sleep/wait/park/join có timeout                   |
      +-------> TIMED_WAITING -- timeout/tín hiệu --------+
      |
      | run() trả về hoặc kết thúc do uncaught exception
      v
 TERMINATED
```

Đây là **sơ đồ tư duy**, không phải một máy trạng thái mô tả đầy đủ mọi chuyển tiếp của JVM. Ví dụ thread được `notify()` sau `Object.wait()` có thể phải **chờ lấy lại monitor** trước khi tiếp tục, nên đôi lúc được quan sát là `BLOCKED`. Sau `interrupt()`, thread có thể trở về `RUNNABLE` để xử lý ngắt, chứ không tự động thành `TERMINATED`.

Những nguyên tắc cơ bản:

1. Tạo bằng constructor chỉ cho trạng thái `NEW`; **gọi `run()` trực tiếp** là gọi phương thức bình thường trên caller thread, không `start()` một thread mới.
2. `start()` yêu cầu JVM bắt đầu vòng đời thread, chỉ được gọi **một lần trên cùng đối tượng `Thread`**. Gọi lại sau khi đã start, kể cả khi thread kết thúc, dẫn tới `IllegalThreadStateException`.
3. Thread có thể đi từ `RUNNABLE` sang chờ rồi quay lại `RUNNABLE` nhiều lần; **không cần đi qua tất cả sáu trạng thái**.
4. Khi `run()` kết thúc, trạng thái cuối là `TERMINATED`. Muốn thực hiện thêm công việc trên thread khác thì tạo thread mới hoặc gửi task tới executor; không tái khởi động cùng `Thread`.

## 3. Ví dụ Java 17+: quan sát `NEW`, `RUNNABLE`, `TERMINATED`

Ví dụ đầu tiên dùng một worker chạy vòng lặp có cờ dừng `volatile`. Nhờ không đặt điểm chờ blocking trong vòng lặp, ta có thể kiểm tra `RUNNABLE` một cách có kiểm soát. Đoạn spin này **chỉ để học và chẩn đoán**, không nên biến thành cách chờ công việc trong ứng dụng thực.

Lưu thành `BasicThreadStateDemo.java`:

```java
import java.util.concurrent.TimeUnit;

public class BasicThreadStateDemo {
    private static volatile boolean stopRequested;

    private static void awaitState(Thread thread, Thread.State expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);

        while (System.nanoTime() - deadline < 0) {
            if (thread.getState() == expected) {
                return;
            }
            Thread.onSpinWait();
        }

        throw new IllegalStateException(
                "Did not observe " + expected + "; last=" + thread.getState());
    }

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            while (!stopRequested) {
                Thread.onSpinWait();
            }
        }, "demo-worker");

        System.out.println("Before start: " + worker.getState());

        worker.start();
        try {
            awaitState(worker, Thread.State.RUNNABLE);
            System.out.println("While active: " + worker.getState());
        } finally {
            stopRequested = true;
            worker.join();
        }

        System.out.println("After join: " + worker.getState());
    }
}
```

Lệnh chạy trên JDK 17 trở lên:

```bash
javac BasicThreadStateDemo.java
java BasicThreadStateDemo
```

Với lần chạy bình thường, khi `RUNNABLE` được quan sát trước thời hạn, stdout:

```text
Before start: NEW
While active: RUNNABLE
After join: TERMINATED
```

**Giải thích:**

- Trước `start()`, đối tượng worker là `NEW`.
- `worker.start()` bắt đầu thread mới; worker đọc cờ `volatile stopRequested` trong vòng lặp. Cờ volatile giúp worker nhìn thấy yêu cầu dừng từ main.
- `awaitState` **lấy mẫu lặp có giới hạn ba giây**, tránh giả định cứ gọi `getState()` ngay sau `start()` sẽ nhìn thấy trạng thái mong muốn. Thời điểm scheduler chạy worker vẫn không được cam kết; nếu máy bị treo lâu hoặc JVM không lập lịch kịp, ví dụ báo lỗi thay vì khẳng định sai.
- Trong ví dụ này worker không chờ monitor, queue hay sleep và cờ chưa được bật, nên khi đã bắt đầu hoạt động nó nằm ở `RUNNABLE`. Trạng thái không đồng nghĩa có cả một lõi CPU dành riêng.
- `stopRequested = true` yêu cầu worker thoát, `worker.join()` **đợi kết thúc thật sự**. Chỉ sau `join()` thành công, việc in `TERMINATED` mới có căn cứ mạnh. `getState()` một mình không phải phép đợi hoàn thành.

Vì `Thread.onSpinWait()` có thể tiêu tốn CPU trong lúc chờ, đây chỉ là đoạn kiểm chứng ngắn phục vụ việc học. Trong code thực tế, dùng `CountDownLatch`, `BlockingQueue`, `Future` hoặc cơ chế signaling tương ứng khi cần chờ sự kiện.

## 4. Phân biệt ba kiểu chờ: `BLOCKED`, `WAITING` và `TIMED_WAITING`

Ba trạng thái trông giống nhau vì thread chưa tiếp tục công việc, nhưng **nguyên nhân chờ khác nhau**.

### `BLOCKED`: chờ intrinsic monitor lock

Giả sử thread A đang ở:

```java
synchronized (monitor) {
    // Giữ monitor trong critical section.
}
```

Nếu thread B cũng muốn vào vùng `synchronized (monitor)` khi A chưa nhả monitor, B có thể ở `BLOCKED`. Cả hai phải tranh **cùng một đối tượng monitor** thì mới có tranh chấp đó.

`BLOCKED` gắn với **monitor entry/re-entry**. Không nên hiểu mọi thao tác "đang block" theo tiếng Anh là `Thread.State.BLOCKED`:

- `Thread.sleep(...)` thường cho `TIMED_WAITING`.
- `Object.wait()` nhả monitor khi chờ và thường cho `WAITING`.
- `BlockingQueue.take()` thường đỗ thread qua cơ chế trong `java.util.concurrent.locks`, do đó thường cho `WAITING` nếu không timeout.
- `ReentrantLock.lock()` dưới tranh chấp có thể chờ thông qua `LockSupport.park()`, thường quan sát là `WAITING`, **không phải `BLOCKED`** theo nghĩa monitor.

Không suy luận trực tiếp loại tài nguyên đang chờ từ tên "blocked" trong tài liệu nghiệp vụ. Phải đối chiếu API và stack trace.

### `WAITING`: đợi tín hiệu hoặc điều kiện vô thời hạn

Thread có thể ở `WAITING` khi gọi `Object.wait()`, `Thread.join()`, `LockSupport.park()` hoặc các API đồng bộ dùng parking mà chưa đặt timeout.

"Vô thời hạn" chỉ có nghĩa **không thiết lập thời hạn tự đánh thức ngay trong lời gọi**. Không có nghĩa thread sẽ không bao giờ được đánh thức; nó có thể được `notify/notifyAll`, unpark, điều kiện đồng bộ hoàn thành hoặc interruption tùy API. `Object.wait()` và `Condition.await()` còn có thể thức dậy giả (spurious wakeup), nên phải kiểm tra lại điều kiện theo hợp đồng của API.

### `TIMED_WAITING`: chờ có khoảng thời gian/thời hạn

`Thread.sleep(1_000)` đưa thread hiện tại vào trạng thái `TIMED_WAITING` trong phần thời gian ngủ. Các ví dụ khác là `wait(timeout)`, `join(timeout)` và `LockSupport.parkNanos(...)`.

**Có timeout không có nghĩa thread chắc chắn trở lại `RUNNABLE` vào đúng mốc thời gian.** Thời gian hết hạn, scheduling, việc tranh khóa và interrupt đều có thể ảnh hưởng lúc thread thực sự tiếp tục. `Thread.sleep(...)` cũng **không giải phóng monitor** mà thread đang giữ.

## 5. Ví dụ Java 17+: tạo tình huống quan sát ba trạng thái chờ

Ví dụ tiếp theo dùng nguyên nhân chờ có kiểm soát: một monitor đang bị giữ cho `BLOCKED`, một latch chưa mở cho `WAITING`, và `Thread.sleep` cho `TIMED_WAITING`. Thay vì gọi `sleep` ở thread main để **đoán** lúc worker chuyển trạng thái, chương trình lấy mẫu giới hạn thời gian rồi giải phóng từng worker.

Lưu thành `WaitingStatesDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WaitingStatesDemo {
    private static Thread.State observe(
            Thread thread, Thread.State expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);

        while (System.nanoTime() - deadline < 0) {
            Thread.State current = thread.getState();
            if (current == expected) {
                return current;
            }
            Thread.onSpinWait();
        }

        throw new IllegalStateException(
                "Expected " + expected + ", last=" + thread.getState());
    }

    public static void main(String[] args) throws InterruptedException {
        // 1. Một thread khác đang giữ intrinsic monitor.
        Object monitor = new Object();
        CountDownLatch aboutToContend = new CountDownLatch(1);
        Thread contender = new Thread(() -> {
            aboutToContend.countDown();
            synchronized (monitor) {
                // Chỉ vào được sau khi main nhả monitor.
            }
        }, "contender");

        synchronized (monitor) {
            contender.start();
            aboutToContend.await();
            System.out.println("Monitor: "
                    + observe(contender, Thread.State.BLOCKED));
        }
        contender.join();

        // 2. Thread chờ latch mở, không đặt timeout.
        CountDownLatch gate = new CountDownLatch(1);
        Thread waiter = new Thread(() -> {
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "waiter");

        waiter.start();
        try {
            System.out.println("Latch: "
                    + observe(waiter, Thread.State.WAITING));
        } finally {
            gate.countDown();
            waiter.join();
        }

        // 3. Thread đang sleep với khoảng thời gian cho trước.
        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "sleeper");

        sleeper.start();
        try {
            System.out.println("Sleep: "
                    + observe(sleeper, Thread.State.TIMED_WAITING));
        } finally {
            sleeper.interrupt();
            sleeper.join();
        }
    }
}
```

Chạy:

```bash
javac WaitingStatesDemo.java
java WaitingStatesDemo
```

Khi ba trạng thái được quan sát thành công trước thời hạn, stdout chính xác:

```text
Monitor: BLOCKED
Latch: WAITING
Sleep: TIMED_WAITING
```

**Vì sao ví dụ cố tình giữ trạng thái ổn định?**

- Main giữ `monitor` trong khi thread `contender` cố vào cùng monitor. `aboutToContend` xác nhận worker đã chạy đến trước đoạn tranh khóa; vòng `observe` chờ nhìn thấy `BLOCKED` thay vì đặt một lần `sleep` và hy vọng.
- `gate` chưa được `countDown()` trong lúc quan sát, nên worker gọi `gate.await()` sẽ chờ. Trên JDK thông dụng, latch sử dụng cơ chế parking nên ta quan sát `WAITING`. Sau đó main gọi `countDown()` để worker có thể thoát.
- `sleeper` bắt đầu khoảng `sleep` dài để main có cơ hội lấy mẫu `TIMED_WAITING`. `interrupt()` trong `finally` giúp kết thúc nhanh; nó **không cưỡng bức giết thread**, mà làm `sleep` phản hồi bằng `InterruptedException`.
- `join()` dùng để thu dọn worker sau từng trường hợp. Không cần dùng `getState()` để quyết định lúc giải phóng tài nguyên.

Đây vẫn là một ví dụ đo trạng thái theo lịch chạy thực tế: nếu JVM hoặc hệ điều hành chậm bất thường, vòng `observe` có thể hết thời gian và ném `IllegalStateException`. Timeout là **giới hạn chờ để bài không treo vô tận**, không phải lời cam kết scheduler luôn đặt worker vào đúng trạng thái trong ba giây.

## 6. Vì sao `getState()` chỉ là ảnh chụp, không phải công cụ đồng bộ?

Xét đoạn code:

```java
if (worker.getState() == Thread.State.WAITING) {
    // ...
}
```

Ngay sau khi `getState()` trả về, một thread khác có thể đánh thức worker; hoặc worker bị interrupt, timeout hay thay đổi trạng thái vì một sự kiện khác. Câu `if` chỉ nói **đã quan sát `WAITING`**, không chứng minh điều kiện vẫn đúng ở câu lệnh tiếp theo.

Đây là một dạng **time-of-check to time-of-use race**: trạng thái có thể thay đổi giữa lúc kiểm tra và lúc sử dụng thông tin. Không nên viết logic nghiệp vụ dựa vào kết luận "nếu thread đang WAITING thì chắc chắn có thể thao tác X an toàn".

So sánh hai cách chờ kết thúc:

```java
// Không nên dùng vòng đọc trạng thái như cơ chế chờ hoàn tất.
while (worker.getState() != Thread.State.TERMINATED) {
    Thread.onSpinWait();
}
```

và:

```java
// Cách chuẩn để đợi Thread kết thúc.
worker.join();
```

`join()` có ngữ nghĩa **đợi thread hoàn tất**, hỗ trợ interruption và cung cấp quan hệ happens-before: mọi hành động trong worker xảy ra trước các hành động của thread gọi sau khi `join()` thành công. `getState()` là API quan sát; không được sử dụng như một primitive đồng bộ thay thế `join()`.

Tương tự, nếu muốn đợi một nhiệm vụ trong executor, dùng `Future.get()` hoặc `CompletableFuture.join()` theo ngữ nghĩa cần thiết. Muốn biết nhiều worker đã tới mốc xử lý, dùng `CountDownLatch` hoặc barrier phù hợp. Muốn chờ dữ liệu, dùng queue/condition. Các API đó biểu diễn **điều kiện công việc**, trong khi `Thread.State` chỉ mô tả trạng thái thread.

### `getState()` và `isAlive()` khác nhau thế nào?

`Thread.isAlive()` trả về `true` sau khi thread đã được start và **chưa** chết. Nó là câu hỏi hai trạng thái tổng quát hơn:

| Quan sát | `getState()` | `isAlive()` |
| --- | --- | --- |
| Trước `start()` | `NEW` | `false` |
| Sau `start()`, chưa kết thúc | Một trong `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING` | `true` |
| Sau `join()` thành công | `TERMINATED` | `false` |

Vì hai lệnh đọc là hai thời điểm khác nhau, code không được dựa vào việc chúng luôn phản ánh **cùng một thời điểm**. `isAlive()` cũng không chứng minh worker đang tạo tiến độ hữu ích: thread có thể đang chờ hoặc mắc kẹt.

### `getState()` không biểu diễn interrupt flag

`Thread.interrupt()` là cơ chế yêu cầu ngắt. Trạng thái `WAITING` hoặc `TIMED_WAITING` có thể thay đổi khi một lời gọi chờ hỗ trợ interruption ném `InterruptedException`; bản thân **interrupt flag không phải giá trị `Thread.State`**. Dùng `isInterrupted()` hoặc `Thread.interrupted()` đúng ngữ nghĩa nếu cần kiểm tra ngắt; lưu ý `Thread.interrupted()` là phương thức static kiểm tra **thread hiện tại** và xóa interrupt flag khi trả về `true`.

## 7. Cùng là chờ nhưng nguyên nhân khác: đọc state và stack trace cùng nhau

Không thể xác định chính xác "đang chờ gì" chỉ từ enum:

- `WAITING` có thể là worker đang chờ task mới trong pool, thread đang `join()` thread khác, đang `Object.wait()`, hoặc đang bị `park()` bởi một công cụ đồng bộ.
- `TIMED_WAITING` có thể là sleep chủ động, chờ tín hiệu có timeout hoặc chờ một thao tác lập lịch theo khoảng thời gian.
- `BLOCKED` chỉ ra chờ monitor, nhưng **một lần quan sát `BLOCKED` không chứng minh deadlock**. Đây có thể là tranh khóa rất ngắn, hoàn toàn hợp lệ.
- `RUNNABLE` có thể đang thực thi mã CPU-bound hoặc chờ tài nguyên hệ điều hành; cần thêm thông tin để kết luận vì sao hệ thống chậm.

Nếu cần điều tra, kết hợp **thread name**, **stack trace**, **thời điểm lấy mẫu**, **monitor/lock liên quan** và chỉ số tiến độ của công việc.

Ví dụ chụp stack bằng Java:

```java
Thread.State state = worker.getState();
StackTraceElement[] frames = worker.getStackTrace();
```

Đây là đoạn minh họa sử dụng biến `worker` đã có, không phải chương trình độc lập. Stack trace cũng là một ảnh chụp có thể không hoàn toàn trùng thời điểm với lần đọc state. Nếu thread thay đổi trong khoảng giữa hai lời gọi, stack có thể phản ánh một vị trí thực thi khác; không nên diễn giải đây là một snapshot đồng bộ tuyệt đối.

Với chẩn đoán JVM, Java có `java.lang.management.ThreadMXBean` để lấy thông tin về thread, stack, một số chỉ số/owner của lock tùy khả năng nền tảng. Khi kiểm tra deadlock, `findDeadlockedThreads()` có thể phát hiện chu trình chờ liên quan monitor và ownable synchronizers được JVM hỗ trợ. Kết quả `null` trong một lần kiểm tra **không bảo đảm chương trình không thể deadlock trong tương lai**. Thread dump và công cụ như `jcmd Thread.print` cũng hữu ích khi xác định vị trí chờ, nhưng cần đối chiếu nhiều mẫu và bối cảnh tải thực tế.

## 8. Khi nào nên lấy mẫu trạng thái theo chu kỳ?

`getState()` hữu ích trong **chẩn đoán**, ví dụ:

1. Xác nhận một thread mới tạo chưa được start (`NEW`).
2. Thấy một thread đã hoàn tất sau khi điều phối thành công (`TERMINATED`).
3. Nhận diện một worker đang tranh monitor (`BLOCKED`) để xem có critical section quá dài hay không.
4. Kiểm tra worker đang chờ tín hiệu hay chờ timeout (`WAITING`/`TIMED_WAITING`), từ đó xem điều kiện và nguồn đánh thức.
5. Xác định các thread cùng thay đổi trạng thái qua nhiều lần quan sát, rồi đối chiếu với log và thread dump.

Lấy **một** trạng thái của **một** thread chỉ đủ cho kết luận hẹp. Khi cần hiểu xu hướng, nên lấy nhiều mẫu có mốc thời gian rồi so với số task hoàn thành, thời gian queue, CPU, lock contention và stack.

Ví dụ nếu 20 worker đều `WAITING` và hàng đợi rỗng, đây có thể là một pool nhàn rỗi khỏe mạnh. Nếu hàng đợi còn hàng nghìn task nhưng worker `WAITING` lâu và không có task hoàn thành, cần xem điều kiện đồng bộ, tín hiệu và cách đẩy công việc. Chỉ đếm số thread `WAITING` sẽ bỏ qua bối cảnh.

**Đừng dùng polling trạng thái liên tục trong production** để quyết định vòng đời task. Nó tốn chi phí, dễ đua trạng thái và không cung cấp causal semantics. Đặt metric cho tiến độ công việc và sử dụng các primitive đồng bộ để điều khiển thay cho polling trạng thái.

## 9. Quan sát current thread và worker thread

`Thread.currentThread()` trả về thread đang thực thi câu lệnh hiện tại. Vì vậy:

```java
Thread current = Thread.currentThread();
System.out.println(current.getName());
System.out.println(current.getState());
```

Nếu gọi trong `main` và thread main đang thực thi lệnh in, thông thường `getState()` tại thời điểm đọc sẽ là `RUNNABLE`, nhưng **tên thread** phụ thuộc cách chạy chương trình, và việc in/đọc stdout có thể khiến trạng thái thay đổi sau đó. Không nên ghi cứng tên hay các số đo thời gian trong tài liệu như cam kết toàn cục.

Phải phân biệt:

```java
Thread worker = new Thread(() -> {
    // Tại đây currentThread() là worker sau khi start().
});
System.out.println(worker.getState()); // Có thể vẫn là NEW.
worker.start();
```

Trong khi phương thức `run()` chạy trên worker mới sau `start()`, thread main tiếp tục độc lập. Một lệnh `getState()` ngay sau `start()` ở main **không đảm bảo** sẽ thấy `RUNNABLE`; nếu worker kết thúc cực nhanh, main có thể thấy `TERMINATED`.

Nếu thay `worker.start()` bằng `worker.run()`, Java chỉ thực thi `run()` như phương thức bình thường trên main; `worker` vẫn là `NEW`, dù code bên trong đã được chạy một lần trên thread khác. Đây là lỗi đặc biệt dễ làm người học hiểu sai vòng đời.

## 10. Những tình huống dễ chẩn đoán sai

### Trường hợp A: "Một thread ở `BLOCKED` nên ứng dụng deadlock"

Không đủ dữ kiện. `BLOCKED` nghĩa là đang chờ monitor; nếu thread giữ monitor sớm giải phóng, đó chỉ là contention. Deadlock cần quan hệ chờ vòng tròn hoặc bằng chứng hệ thống không thể tiến triển. Cần đối chiếu lock owner, stack trace và tiến độ qua thời gian.

### Trường hợp B: "Một worker trong pool ở `WAITING` là bị treo"

Không nhất thiết. Worker rảnh có thể đợi job mới bằng queue, và điều đó là bình thường. Phải xem tình trạng queue, số worker hoạt động, số task hoàn thành và chính sách pool.

### Trường hợp C: "`RUNNABLE` là đang chạy 100% CPU"

Sai. `RUNNABLE` không phải CPU meter. Thread có thể chưa được cấp CPU hoặc đang chờ tài nguyên hệ điều hành. Muốn kiểm tra CPU dùng CPU profiling/CPU time từ công cụ chẩn đoán phù hợp, không suy ra từ state.

### Trường hợp D: "`TIMED_WAITING` nghĩa là thread sắp kết thúc"

Sai. Nó chỉ cho biết thread chờ có giới hạn; sau khi hết hạn hoặc bị interrupt, nó có thể quay lại làm việc và lặp lại chu kỳ nhiều lần.

### Trường hợp E: "Thread đã `TERMINATED` có thể gọi `start()` lại"

Sai. Đối tượng `Thread` đã start không thể start lần hai. Hãy tạo thread mới hoặc gửi task vào executor.

### Trường hợp F: "Gọi `getState()` liên tục là cách chuẩn để đợi thread xong"

Sai. Sử dụng `join()` hoặc kết quả từ future/latch. `getState()` dùng cho quan sát; việc tự spin trên state gây lãng phí CPU và không thay thế bảo đảm đồng bộ mong muốn.

### Trường hợp G: "Gọi `interrupt()` sẽ làm thread thành `TERMINATED`"

Không nhất thiết. Interruption chỉ là yêu cầu. Khi thread đang `sleep()` hoặc chờ hỗ trợ interruption, nó có thể ném `InterruptedException`, thực thi nhánh catch rồi tiếp tục. Trạng thái cuối phụ thuộc logic `run()`.

### Trường hợp H: "Chờ bằng `ReentrantLock.lock()` chắc chắn là `BLOCKED`"

Không chính xác. `BLOCKED` trong `Thread.State` gắn với **intrinsic monitor**, không bao trùm mọi loại lock. Khóa tường minh có thể dùng parking nên thường được thấy ở `WAITING`. Đọc stack để hiểu đúng API và cơ chế thực tế.

## 11. Virtual threads có làm thay đổi cách hiểu không?

Trên Java 21+, virtual threads cũng là các đối tượng `Thread` và có thể được quan sát bằng `getState()`. Tuy nhiên, virtual thread được JVM lên lịch lên carrier platform thread; vì vậy **không được đồng nhất trạng thái của virtual thread với một thread hệ điều hành đang chạy trên một CPU cụ thể**. Các thời điểm parking, waiting và scheduling có thể có chi tiết khác với platform threads.

Hai chương trình Java 17+ trong bài dùng platform threads để minh họa rõ vòng đời nền tảng. Nếu ứng dụng dùng virtual threads trên Java 21+, ý nghĩa quan sát vẫn theo `Thread.State`, còn việc đánh giá hiệu năng hoặc scheduler phải xét riêng. Không suy luận số lượng carrier threads từ số virtual threads đang `RUNNABLE`.

## 12. Câu hỏi tự kiểm tra

**1. Vì sao gọi `worker.getState()` ngay sau `worker.start()` có thể cho kết quả khác nhau giữa các lần chạy?**

Vì scheduler và thời lượng công việc quyết định thời điểm chuyển trạng thái, trong khi `getState()` chỉ chụp một thời điểm. Với worker chạy rất ngắn, main có thể quan sát `RUNNABLE` hoặc thậm chí `TERMINATED` khi đọc.

**2. `BLOCKED` và `WAITING` khác nhau ở điểm nào quan trọng nhất?**

`BLOCKED` liên quan trực tiếp đến chờ **intrinsic monitor**. `WAITING` là chờ không timeout theo cơ chế như `wait/park/join`, không đồng nghĩa chờ monitor entry. Một API dùng `ReentrantLock` có thể park và hiện là `WAITING`.

**3. `Thread.sleep(1000)` có nhả khóa `synchronized` mà thread đang giữ không?**

Không. `sleep` không giải phóng monitor. Vì thế một thread khác có thể bị `BLOCKED` trong khi chủ monitor ở `TIMED_WAITING`.

**4. Nếu thread ở `WAITING` thì có chắc ứng dụng lỗi?**

Không. Thread có thể đang chờ công việc mới hoặc chờ tín hiệu hợp lệ. Cần xem điều kiện chờ, nguồn tín hiệu, số task trong queue và các mẫu trạng thái/stack theo thời gian.

**5. Tại sao `join()` được dùng sau khi lấy mẫu ở các ví dụ?**

Vì `join()` thực sự đợi worker kết thúc và thiết lập quan hệ đồng bộ sau khi hoàn tất. `getState()` chỉ giúp quan sát, không đợi và không giữ trạng thái cố định.

**6. Cơ chế nào nên dùng nếu muốn biết ba task đã xong, và `getState()` đóng vai trò gì?**

Dùng `CountDownLatch`, future hoặc cơ chế đợi hoàn tất phù hợp để điều phối ba task. `getState()` chỉ hỗ trợ chẩn đoán thread nào đang ở trạng thái gì trong quá trình đó.

## 13. Nguyên tắc ghi nhớ

- `Thread.getState()` trả về **một** trong sáu trạng thái `NEW`, `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING`, `TERMINATED`.
- `RUNNABLE` **không đồng nghĩa đang tiêu thụ CPU**; `BLOCKED` chỉ chờ monitor; `WAITING` và `TIMED_WAITING` mô tả chờ không timeout hoặc có giới hạn.
- `getState()` là **ảnh chụp có thể lỗi thời ngay lập tức**. Đừng dựa vào một lần quan sát để xác định điều kiện nghiệp vụ hoặc chứng minh deadlock.
- Khi cần **đợi hoàn thành**, dùng `join()`, `Future` hay primitive đồng bộ khác. Khi cần **tìm nguyên nhân chờ**, kết hợp trạng thái với stack trace, monitor/lock và tiến độ công việc.
- Hãy thiết kế ví dụ quan sát bằng điều kiện có kiểm soát, xác nhận trạng thái bằng polling **có timeout**, và luôn có đường cleanup cho thread được khởi động.

Hiểu các giới hạn này giúp `getState()` trở thành công cụ quan sát hữu ích khi học, kiểm thử và xử lý sự cố Java concurrency, thay vì một nguồn suy luận sai về scheduler hoặc về sự hoàn tất của công việc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-lifecycle-006">Scheduling và sự bất định của thứ tự thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Scheduling và sự bất định của thứ tự thực thi trong Java

## 1. Scheduling là gì và vì sao thứ tự thực thi không cố định?

Trong Java, **thread scheduling** là quá trình chọn thread nào có cơ hội tiến triển tại một thời điểm. JVM và hệ điều hành cùng tham gia quản lý khả năng chạy của các thread; quyết định thực tế phụ thuộc vào hệ điều hành, số lõi CPU, độ ưu tiên, tải máy, thao tác chờ, khóa và nhiều yếu tố khác. Lập trình viên không được dựa vào một lịch chạy cụ thể chỉ vì chương trình thường chạy theo lịch đó trên máy của mình.

Ví dụ, hai thread độc lập A và B đều in một dòng. Dù gọi `a.start()` trước `b.start()`, hệ thống vẫn có thể cho B in trước A. `start()` khởi động quá trình thực thi bất đồng bộ; nó **không** đợi thread chạy tới một điểm nào đó và không bảo đảm thread được gọi `start()` trước sẽ thực hiện câu lệnh đầu tiên trước.

Cần phân biệt ba khái niệm:

- **Concurrency (đồng thời):** nhiều thread có thể tiến triển trong cùng một khoảng thời gian; JVM/OS có thể xen kẽ chúng trên một lõi.
- **Parallelism (song song):** các thread thực sự thực thi đồng thời trên nhiều lõi.
- **Scheduling (lập lịch):** quyết định khi nào một thread có cơ hội chạy, chờ, bị tạm dừng hoặc tiếp tục. Lập lịch không tạo ra bảo đảm về thứ tự giữa các tác vụ độc lập.

**Thông điệp quan trọng:** kết quả đúng của chương trình đa luồng phải dựa vào quan hệ phụ thuộc và cơ chế đồng bộ xác định, không dựa vào việc “thread này có lẽ sẽ chạy trước”.

## 2. Trạng thái thread liên quan đến scheduler

`Thread.State` trong Java cho biết một số trạng thái JVM quan sát được của thread. Nó không phải bản mô tả đầy đủ mọi trạng thái do hệ điều hành quản lý và chỉ là ảnh chụp tại thời điểm gọi `getState()`.

| `Thread.State` | Ý nghĩa thực hành | Liên hệ với scheduling |
| --- | --- | --- |
| `NEW` | Đã tạo đối tượng nhưng chưa `start()` | Chưa được đăng ký để thực thi như thread mới |
| `RUNNABLE` | Đang thực thi trong JVM hoặc sẵn sàng thực thi | **Không đồng nghĩa đang chiếm CPU đúng thời điểm quan sát** |
| `BLOCKED` | Đang chờ lấy monitor của `synchronized` | Phải đợi monitor khả dụng; không phải một trạng thái chung cho mọi I/O |
| `WAITING` | Đang đợi vô thời hạn theo cơ chế chờ, ví dụ `Object.wait()`, `Thread.join()` hoặc `LockSupport.park()` | Không thể tiếp tục nếu điều kiện/chỉ báo đánh thức tương ứng chưa xảy ra |
| `TIMED_WAITING` | Đang đợi có thời hạn, ví dụ `Thread.sleep()`, timed `join()`, timed `wait()` | Có thể tiếp tục khi điều kiện thỏa hoặc hết thời gian, sau đó còn phụ thuộc lập lịch |
| `TERMINATED` | `run()` đã kết thúc hoặc thread chấm dứt do lỗi không xử lý | Không được khởi động lại |

Một thread có thể đi qua nhiều trạng thái rất nhanh. Ví dụ quan sát `RUNNABLE` rồi gọi `getState()` lại có thể thấy `WAITING`. Vì vậy không viết logic phối hợp dựa vào việc liên tục kiểm tra `getState()` rồi giả định thread đang ở nguyên trạng thái đó.

**Đặc biệt:** gọi `Thread.sleep()` không có nghĩa nhường quyền sở hữu monitor đã giữ; nếu đang ở trong `synchronized`, thread vẫn giữ monitor trong lúc ngủ.

## 3. Ví dụ Java 17+: hai thread bắt đầu cùng mốc nhưng thứ tự không bảo đảm

Tạo tệp `SchedulingObservation.java`:

```java
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

public class SchedulingObservation {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> order = new ConcurrentLinkedQueue<>();

        Runnable work = () -> {
            ready.countDown();
            try {
                go.await();
                order.add(Thread.currentThread().getName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread a = new Thread(work, "A");
        Thread b = new Thread(work, "B");

        a.start();
        b.start();
        ready.await();    // Cả hai worker đã tới mốc sẵn sàng
        go.countDown();   // Mở cùng một rào chờ
        a.join();
        b.join();

        System.out.println("Observed order: " + order);
        System.out.println("Recorded two events: " + (order.size() == 2));
    }
}
```

Biên dịch và chạy:

```bash
javac SchedulingObservation.java
java SchedulingObservation
```

**Hai dạng đầu ra có thể gặp** (thứ tự không được bảo đảm):

```text
Observed order: [A, B]
Recorded two events: true
```

hoặc:

```text
Observed order: [B, A]
Recorded two events: true
```

Không có cam kết sẽ quan sát được cả hai thứ tự dù chạy nhiều lần trên cùng máy. Lập lịch đôi khi tình cờ lặp lại một thứ tự rất ổn định. **Đừng dùng sự ổn định đó làm chứng cứ bảo đảm.**

Giải thích các thành phần:

1. `CountDownLatch ready` giúp `main` biết cả hai worker đã đi tới cổng chờ.
2. `go.await()` khiến hai worker đợi đến khi `go.countDown()`; đây là đồng bộ tại một mốc, **không** bảo đảm chúng được chạy cùng một nano-giây hoặc chọn thứ tự A/B nào.
3. `ConcurrentLinkedQueue` cho phép ghi sự kiện từ nhiều thread an toàn, tránh lỗi dữ liệu do collection không hỗ trợ đồng thời.
4. Hai `join()` làm `main` chờ cả hai worker hoàn tất trước khi in kết quả.
5. Nếu chương trình không bị interrupt từ bên ngoài thì cả hai worker ghi một tên vào queue. Dòng `Recorded two events: true` xác định; dòng thứ tự quan sát phụ thuộc lịch chạy.

Đây là ví dụ về **thứ tự của các side effect** do nhiều thread thực hiện. Ngay cả khi mỗi thread chỉ có một thao tác rất ngắn, Java cũng không hứa sẽ chạy các thao tác theo thứ tự gọi `start()`.

## 4. Các nguyên nhân khiến lịch thực thi thay đổi

### 4.1. Khả năng chạy và thời điểm thực sự được cấp CPU

Một thread ở trạng thái `RUNNABLE` có thể đã sẵn sàng nhưng chưa được CPU thực thi ngay. Trên máy có nhiều lõi, các thread có thể chạy song song; trên máy ít lõi hơn số thread sẵn sàng, chúng phải chia sẻ thời gian xử lý. Kết quả phụ thuộc hệ điều hành và tình trạng máy tại thời điểm chạy.

### 4.2. Preemption và chuyển ngữ cảnh

Hệ điều hành có thể chuyển CPU từ thread này sang thread khác theo cơ chế lập lịch của nó. Việc chuyển ngữ cảnh khiến các lệnh của nhiều thread có thể xen kẽ với nhau. Java không cung cấp cam kết rằng mỗi đoạn mã giữa hai dấu chấm phẩy sẽ luôn chạy liền một mạch không bị xen kẽ.

Cần phân biệt **tác vụ ngắn về mặt mã nguồn** với **thao tác nguyên tử**. Ví dụ `counter++` gồm các bước đọc, cộng, ghi; việc viết trên một dòng không làm ba bước trở nên không thể xen kẽ với thao tác của thread khác.

### 4.3. Chờ lock, I/O và các primitive đồng bộ

Một thread có thể phải chờ monitor của `synchronized`, điều kiện trong `wait()`/`notify()`, `CountDownLatch`, `Semaphore`, `BlockingQueue`, `LockSupport`, `Future` hoặc I/O. Khi sự kiện chờ kết thúc, thread trở thành ứng viên để chạy; điều đó **không** cam kết nó sẽ được CPU chạy tức thì hoặc chạy trước mọi thread khác.

### 4.4. Độ ưu tiên thread

Java có `Thread.MIN_PRIORITY`, `Thread.NORM_PRIORITY`, `Thread.MAX_PRIORITY` và `setPriority(int)` cho platform threads truyền thống. Priority có thể tác động đến scheduling tùy JVM và hệ điều hành, nhưng **không phải công cụ bảo đảm thứ tự, tính công bằng hay hạn chót**. Không xây dựng tính đúng đắn dựa vào giả thuyết rằng thread priority cao sẽ luôn chạy hoặc hoàn thành trước.

### 4.5. Tối ưu hóa và bộ nhớ

Ngoài sự bất định về lịch chạy, chương trình có **data race** còn có vấn đề visibility và ordering theo Java Memory Model. Compiler, JIT và CPU có thể thực hiện tối ưu hóa hợp lệ mà một thread khác không quan sát được các thao tác như thứ tự mã nguồn đơn giản nếu không có quan hệ đồng bộ thích hợp.

Vì thế câu “chỉ cần thêm `sleep()` để thread kia có thời gian cập nhật biến” có hai lỗi: thời gian ngủ không bảo đảm thread kia đã chạy xong và `sleep()` không tự tạo quan hệ happens-before để xuất bản dữ liệu an toàn.

## 5. `sleep()`, `yield()`, `join()` có làm lịch chạy trở nên xác định không?

| API | Tác dụng | Điều **không** được bảo đảm |
| --- | --- | --- |
| `Thread.sleep(millis)` | Đề nghị thread hiện tại tạm ngừng trong khoảng thời gian chỉ định | Thread khác sẽ hoàn tất; chính xác thời điểm được chạy lại; visibility của dữ liệu |
| `Thread.yield()` | Gợi ý scheduler rằng thread hiện tại sẵn sàng nhường phần thời gian chạy | Scheduler sẽ nghe theo; thread khác sẽ chạy trước; cách sắp lịch cố định |
| `Thread.join()` | Thread gọi chờ thread đích chấm dứt | Thứ tự thực thi giữa các worker độc lập; worker hoàn thành thành công |
| `Thread.start()` | Khởi chạy một thread mới | Thread vừa start sẽ chạy trước thread start sau |
| `Thread.setPriority(...)` | Điều chỉnh gợi ý ưu tiên khi triển khai hỗ trợ | Thứ tự, độ công bằng, thời hạn phản hồi |

**Về `sleep()`:** thời gian ngủ còn phụ thuộc độ chính xác timer, scheduler và tải máy. `sleep(100)` không phải lời hứa “chạy lại sau đúng 100 ms”. Có thể đo thời gian trôi qua để quản lý timeout, nhưng phải tính việc thức dậy muộn hơn dự kiến.

**Về `yield()`:** nó là một hint không có hiệu lực đồng bộ. Thêm `yield()` trong một vòng lặp chờ flag thông thường không thay `volatile`, lock hoặc các cơ chế chờ đúng chuẩn. Busy-wait có thể gây lãng phí CPU và vẫn sai về visibility.

**Về `join()`:** sau khi `join()` trả về bình thường không có timeout, thread đích đã kết thúc; tuy nhiên thứ tự từng câu lệnh giữa các worker trước đó vẫn có thể khác nhau. `join(timeout)` chỉ chờ có giới hạn, nên phải kiểm tra `isAlive()` nếu cần biết worker thực sự đã hoàn tất.

## 6. Đừng nhầm bất định thứ tự với bất định kết quả đúng

Một ứng dụng concurrent tốt **có thể có lịch chạy khác nhau nhưng vẫn có kết quả đúng giống nhau**. Ví dụ hai thread tính hai phần độc lập rồi main lấy tổng sau khi cả hai đã kết thúc: từng phần có thể hoàn tất theo thứ tự nào cũng được nếu cách lưu và thu thập kết quả an toàn.

Ngược lại, nếu nhiều thread cùng cập nhật một biến mutable không đồng bộ, kết quả tính toán có thể sai tùy thứ tự xen kẽ.

### Vì sao `counter++` không an toàn?

Giả sử hai thread cùng đọc `counter = 0`, mỗi thread tính `1`, sau đó đều ghi `1`. Ta mong đợi `2` nhưng nhận được `1`: một lần tăng bị mất. Đây gọi là **lost update**, một dạng race condition. `volatile int counter` bảo đảm visibility của phép đọc/ghi riêng lẻ nhưng **không** biến thao tác đọc-sửa-ghi `counter++` thành nguyên tử.

Để ví dụ có đầu ra đúng, sử dụng `AtomicInteger.incrementAndGet()` hoặc `getAndIncrement()`, hoặc bảo vệ thao tác bằng khóa phù hợp.

Tạo tệp `AtomicScheduling.java`:

```java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicScheduling {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();

        Runnable increment = () -> {
            for (int i = 0; i < 1_000; i++) {
                counter.incrementAndGet();
            }
        };

        Thread a = new Thread(increment, "A");
        Thread b = new Thread(increment, "B");
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("Final count: " + counter.get());
    }
}
```

Biên dịch và chạy:

```bash
javac AtomicScheduling.java
java AtomicScheduling
```

Đầu ra:

```text
Final count: 2000
```

Scheduler có thể xen kẽ A và B theo bất kỳ lịch hợp lệ nào; kết quả vẫn là 2.000 vì hai thread thực hiện đủ số lần tăng nguyên tử và `main` chờ cả hai bằng `join()`. **Không cần** ép A chạy hết rồi mới B. Đây là hướng thiết kế tốt: dùng primitive có bảo đảm phù hợp để kết quả không phụ thuộc thứ tự scheduling.

## 7. Muốn có thứ tự thì phải biểu diễn quan hệ phụ thuộc

Có những công việc cần thứ tự nghiệp vụ thật: chẳng hạn bước “nạp cấu hình” phải xong trước bước “dùng cấu hình”. Khi đó hãy đồng bộ **đúng sự kiện**, thay vì kỳ vọng một thread được `start()` trước sẽ luôn hoàn thành trước.

### Ví dụ Java 17+: bảo đảm bước 1 trước bước 2 bằng `CountDownLatch`

Tạo tệp `OrderedSteps.java`:

```java
import java.util.concurrent.CountDownLatch;

public class OrderedSteps {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch firstDone = new CountDownLatch(1);

        Thread second = new Thread(() -> {
            try {
                firstDone.await();
                System.out.println("Step 2: use data");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        Thread first = new Thread(() -> {
            System.out.println("Step 1: prepare data");
            firstDone.countDown();
        }, "producer");

        second.start(); // Cố ý start consumer trước
        first.start();

        first.join();
        second.join();
        System.out.println("All done");
    }
}
```

Chạy:

```bash
javac OrderedSteps.java
java OrderedSteps
```

Đầu ra trong lần chạy hoàn tất bình thường:

```text
Step 1: prepare data
Step 2: use data
All done
```

Dù `second.start()` được gọi trước, `second` chỉ in bước 2 sau khi `firstDone` được giảm về 0. Đây là thứ tự **do điều kiện đồng bộ tạo ra**, không phải nhờ scheduler ưu tiên thread nào.

`CountDownLatch.countDown()` có quan hệ happens-before với các hành động sau khi thread khác vượt qua `await()` thành công. Vì vậy nếu producer ghi dữ liệu trước `countDown()` và consumer chỉ đọc sau `await()`, đây là một ranh giới công bố dữ liệu hợp lệ. Lưu ý rằng latch không tự truyền ngoại lệ hoặc chứng minh producer thành công: bài toán production phải định nghĩa cách xử lý lỗi và tránh chờ vô hạn nếu producer không bao giờ gọi `countDown()`.

### Trường hợp chỉ cần kết quả sau khi thread kết thúc

`worker.join()` đơn giản hơn khi **toàn bộ** thread worker phải kết thúc trước khi main dùng kết quả. Nhưng nếu consumer cần chờ một **mốc** trong lúc producer còn tiếp tục làm việc, `CountDownLatch`, `BlockingQueue`, `Condition` hoặc một abstraction tương tự diễn đạt yêu cầu chính xác hơn.

## 8. Java Memory Model: thứ tự logic và visibility

Scheduler quyết định thời điểm thread có cơ hội chạy, còn **Java Memory Model (JMM)** xác định những quan sát dữ liệu được bảo đảm giữa thread khi các điều kiện đồng bộ được thỏa mãn.

Một số quan hệ **happens-before** cần nhớ:

- Các hành động trong cùng một thread tuân theo quy tắc thứ tự chương trình (`program order`) theo ngữ nghĩa JMM; điều này không có nghĩa mọi lệnh máy đều được phát ra theo đúng thứ tự mã nguồn.
- Lời gọi `thread.start()` happens-before các hành động của thread vừa được khởi động.
- Các hành động của một thread happens-before việc thread khác xác nhận thread đó đã kết thúc qua `join()` thành công.
- Một lần unlock monitor happens-before lần lock tiếp theo trên **cùng monitor**.
- Một lần ghi vào trường `volatile` happens-before lần đọc tiếp theo của chính trường đó trong thứ tự đồng bộ.
- Với `CountDownLatch`, các hành động trước `countDown()` happens-before hành động sau `await()` thành công khi latch mở.

Quan hệ happens-before là nền tảng về visibility và ordering, **không có nghĩa scheduler buộc thread có quan hệ trước được CPU chọn chạy đầu tiên ở mọi thời điểm**. Ví dụ, main gọi `a.start()` rồi `b.start()`: thao tác start có quan hệ với các hành động trong từng worker, nhưng **không tạo ra** thứ tự toàn phần giữa mọi hành động của A và B.

Không dùng `System.out.println()` hay `Thread.sleep()` như giao thức điều phối. Một vài triển khai I/O có đồng bộ nội bộ, nhưng dựa vào tác dụng phụ của logging để sửa race hoặc định thứ tự là thiết kế mong manh.

## 9. Các lỗi thường gặp liên quan scheduling

### 9.1. Giả định thứ tự gọi `start()` quyết định thứ tự kết quả

Sai: `a.start(); b.start();` không tạo cam kết A in trước B. Giải pháp: nếu cần thứ tự, dùng `join()` tại đúng ranh giới hoặc primitive đồng bộ sự kiện. Nếu các task độc lập, viết code chấp nhận mọi thứ tự hợp lệ.

### 9.2. Dùng `sleep()` để “đợi đủ lâu”

Sai: ngủ 500 ms không chứng minh một task khác đã hoàn tất hoặc dữ liệu đã được công bố đúng. Hãy chờ điều kiện thật: `join()`, `Future.get()`, latch, queue hoặc lock/condition; khi cần thời hạn, dùng các API có timeout và kiểm tra trạng thái kết quả.

### 9.3. Dùng `yield()`/priority để giải quyết race

Sai: `yield()` chỉ gợi ý lập lịch; priority cũng không tạo thứ tự và không bảo đảm fairness. Lỗi truy cập dữ liệu phải được xử lý bằng quan hệ đồng bộ hoặc thiết kế lại dữ liệu.

### 9.4. Nghĩ kiểm thử vài nghìn lần không lỗi thì không có race

Sai: cùng máy, cùng tải và cùng một lịch chạy phổ biến có thể che giấu race. Thread scheduling biến thiên theo hệ điều hành và tải; chạy nhiều lần, stress tests và công cụ quan sát chỉ tăng khả năng phát hiện, không chứng minh không có mọi race.

### 9.5. Busy-wait không điều kiện đồng bộ

Ví dụ lỗi về mặt ý tưởng:

```java
while (!finished) {
    Thread.yield();
}
```

Nếu `finished` là boolean thường do thread khác ghi và không có đồng bộ phù hợp, thread chờ có thể không quan sát được thay đổi; ngoài ra vòng lặp lãng phí CPU. Đây là **snippet minh họa lỗi**, không phải chương trình Java hoàn chỉnh. Nên dùng `volatile` cho tín hiệu đơn giản theo thiết kế hợp lệ, hoặc `CountDownLatch`, `BlockingQueue`, `Condition` nếu cần đợi sự kiện. Với chuỗi trạng thái phức tạp, `volatile` đơn lẻ không giải quyết mọi vấn đề atomicity.

### 9.6. Deadlock vì đồ thị chờ có vòng

Nếu A giữ một lock và chờ B, trong khi B cần lock đó để hoàn thành, scheduler không thể tự “sửa” quan hệ chờ. Chẳng hạn A `join()` B khi đang giữ monitor mà B phải lấy, cả hai có thể kẹt. Hãy thiết kế thứ tự lock ổn định, hạn chế giữ lock khi chờ và tránh các vòng phụ thuộc.

### 9.7. Nhầm starvation với deadlock

**Starvation** xảy ra khi một thread ít hoặc không có cơ hội tiến triển do cạnh tranh tài nguyên, lock hoặc chính sách ưu tiên không công bằng; hệ thống có thể vẫn đang có các thread khác tiến triển. **Deadlock** là vòng phụ thuộc khiến một nhóm thread không thể tiến triển. **Livelock** là các thread vẫn hoạt động, đổi trạng thái nhưng không đạt mục tiêu. Ba vấn đề đều có thể xuất hiện trong chương trình concurrent, và việc “tăng priority” không phải lời giải tổng quát.

## 10. Quan sát và đo lường đúng cách

- Dùng tên thread rõ ràng (`new Thread(task, "worker-A")`) để log, stack trace và thread dump dễ hiểu.
- Khi quan sát trạng thái, xem `Thread.getState()` như **snapshot**, không phải giao thức đồng bộ hoặc bảo đảm thứ tự thực thi.
- Để phân tích chương trình thực, kết hợp thread dump (`jcmd` và công cụ JVM), JFR, log có timestamp và thread name, CPU profiling, thông tin lock contention và metrics latency/throughput. Quan sát có thể làm thay đổi thời điểm thực thi; không suy từ một lần lấy log rằng mọi lần chạy đều giống nhau.
- Muốn kiểm tra tính đúng đắn, đặt bất biến cụ thể: không mất cập nhật, không trùng xử lý, thứ tự nghiệp vụ được bảo vệ, không chờ vô hạn. Kiểm thử nhiều lịch và tải khác nhau, đồng thời xem xét happens-before và các điểm chia sẻ trạng thái trong code review.
- Với benchmark, dùng môi trường và workload đại diện, warm-up JVM, nhiều lần đo. Những lần chạy có kết quả biến thiên không tự chứng minh một vấn đề scheduling; cần xác định nút thắt và đo đồng bộ/lock/I/O/GC.

## 11. Checklist trước khi phụ thuộc vào thứ tự giữa các thread

1. **Có yêu cầu thứ tự thật không?** Nếu task độc lập, ưu tiên thiết kế không phụ thuộc thứ tự hoàn tất.
2. **Sự kiện nào tạo quan hệ trước/sau?** `start()`, `join()`, latch, lock, queue, `Future` hay một giao thức cụ thể phải được dùng đúng.
3. **Dữ liệu có được công bố an toàn không?** Chứng minh happens-before hoặc sử dụng primitive atomic/synchronized/volatile phù hợp; không chỉ dựa vào việc “nó thường chạy trước”.
4. **Có khả năng treo không?** Kiểm tra vòng chờ, lock contention, timeout, interrupt và đường lỗi khiến tín hiệu kết thúc không bao giờ phát ra.
5. **Kết quả có đúng trên mọi lịch hợp lệ không?** Tránh giả định thứ tự `println`, thời điểm `sleep` kết thúc, hiệu lực `yield` hoặc thứ tự thread priority.
6. **Quan sát có đủ tin cậy không?** Dựa vào bất biến, bằng chứng đồng bộ và đo workload; log hoặc một lần chạy chỉ là ảnh chụp.

**Ghi nhớ:** scheduling trong Java mang tính bất định ở chỗ chương trình không thể dựa vào lịch thực thi cụ thể của các thread độc lập. Tính đúng đắn được xây dựng bằng quan hệ đồng bộ và dữ liệu an toàn, để chương trình vẫn cho kết quả hợp lệ dù JVM/OS chọn thứ tự chạy khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-lifecycle-007">Ảnh hưởng của blocking I/O và chờ điều kiện</a>

<details>
<summary>Xem chi tiết</summary>

# Ảnh hưởng của blocking I/O và chờ điều kiện đến vòng đời Thread trong Java

## Mục tiêu của bài học

Một `Thread` trong Java không nhất thiết đang tính toán mỗi khi nó còn sống. Nó có thể **chờ dữ liệu từ I/O**, **chờ một điều kiện được thỏa mãn**, **chờ lấy monitor lock**, hoặc **ngủ trong một khoảng thời gian**. Những kiểu chờ này tạo ra biểu hiện khác nhau trong `Thread.State`, ảnh hưởng tới thời gian phản hồi, mức sử dụng tài nguyên và khả năng phục vụ tác vụ mới.

Sau bài này, bạn có thể:
- Phân biệt **I/O blocking** với **chờ điều kiện** và với **tranh chấp monitor**.
- Đọc đúng sáu trạng thái Java `NEW`, `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING`, `TERMINATED`; giải thích vì sao một thread **đang đợi I/O vẫn có thể hiện `RUNNABLE`**.
- Hiểu vì sao `Object.wait()`/`notifyAll()`, `Condition.await()`/`signalAll()` và `BlockingQueue.take()` cho phép thread chờ công việc mà không cần quay vòng tiêu CPU.
- Nhận ra tác động của chờ dài đối với thread pool, độ dài queue, throughput và tail latency.
- Chạy ví dụ Java 17+ để quan sát thread chờ điều kiện và thread đợi dữ liệu trên stream; biết giới hạn của phép chụp trạng thái bằng `getState()`.
- Chọn timeout, interruption, giới hạn concurrency và các công cụ chẩn đoán phù hợp với từng loại chờ.

**Phạm vi:** cơ chế thread và concurrency của Java/JVM nói chung. Các cơ chế, annotation hay cấu hình asynchronous riêng của Spring thuộc chương khác.

## 1. Một thread sống không có nghĩa là nó luôn thực thi CPU

Giả sử một worker đang xử lý yêu cầu đọc dữ liệu:

~~~text
NEW
 |
 | start()
 v
RUNNABLE ----> gọi read() mà chưa có dữ liệu ----> chờ I/O
   ^                                               |
   |                        dữ liệu đến / I/O xong  |
   +-----------------------------------------------+
   |
   +---- chờ predicate qua wait() ---> WAITING hoặc TIMED_WAITING
   |                                     |
   |        signal/notify/timeout        |
   +-------------------------------------+
   |
   +---- cố lấy monitor bị giữ --------> BLOCKED
   |                                     |
   |         lấy được monitor           |
   +-------------------------------------+
   |
   +---- kết thúc run() ----------------> TERMINATED
~~~

Sơ đồ trên mô tả **nguyên nhân một thread tạm ngừng công việc và cách nó tiếp tục**, không khẳng định chờ I/O là một trạng thái enum riêng. Java không có `Thread.State.IO_BLOCKED`. Ngay cả khi đang đợi thao tác I/O ở hệ điều hành, Java có thể vẫn ghi nhận thread là `RUNNABLE`. Không có trạng thái enum `RUNNING` riêng: `RUNNABLE` bao gồm thread đủ điều kiện được chạy, đang chạy hoặc có thể chờ tài nguyên ngoài JVM tùy tình huống.

**Cần phân biệt hai tầng:**
1. **Trạng thái JVM** do `Thread.getState()` hoặc thread dump báo: một ảnh chụp thô phục vụ giám sát.
2. **Nguyên nhân thực tế của sự chờ**: system call đọc socket, `Object.wait()`, một `Lock`, chờ dữ liệu từ queue, chờ CPU được lập lịch... Nguyên nhân này thường cần kết hợp stack trace, thông tin lock, metric và log để xác định.

Một thread có thể thay đổi trạng thái nhiều lần trong một mili giây. `getState()` chỉ là snapshot ở thời điểm đọc; dùng nó để hiểu hiện tượng, **không** để điều khiển logic phối hợp các thread.

## 2. Sáu trạng thái Thread.State cần nắm

| `Thread.State` | Ý nghĩa trong Java | Ví dụ liên quan |
| --- | --- | --- |
| `NEW` | Tạo đối tượng `Thread` nhưng chưa gọi `start()` | `new Thread(task)` |
| `RUNNABLE` | Thread đủ điều kiện chạy hoặc đang thực thi theo góc nhìn JVM; có thể đang chờ tài nguyên OS | CPU work, một số dạng socket/file I/O blocking |
| `BLOCKED` | Đang **chờ monitor lock** để vào/re-enter `synchronized` | Thread A giữ monitor, B cố vào `synchronized(monitor)` |
| `WAITING` | Chờ một hành động khác, không đặt hạn thời gian | `Object.wait()`, `Thread.join()`, `LockSupport.park()` |
| `TIMED_WAITING` | Chờ trong tối đa một khoảng thời gian | `Thread.sleep(500)`, `Object.wait(500)`, `Thread.join(500)`, `LockSupport.parkNanos(...)` |
| `TERMINATED` | `run()` đã kết thúc bình thường hoặc do exception không được xử lý | Sau khi worker chấm dứt |

Lưu ý quan trọng:

- Tên **`BLOCKED` trong enum Java có nghĩa hẹp**: chờ *monitor* của `synchronized`. “Blocking I/O” trong thuật ngữ lập trình **không tự động tương ứng `Thread.State.BLOCKED`**.
- Chờ `ReentrantLock.lock()` hoặc `Condition.await()` có thể được triển khai bằng parking và được quan sát ở `WAITING`/`TIMED_WAITING`; không tự quy chúng về `BLOCKED` chỉ vì “đang đợi lock”.
- Trạng thái Java là trạng thái của JVM; không phải bản sao trực tiếp trạng thái runnable/sleeping của hệ điều hành.
- Thread chuyển trạng thái do điều kiện thực thi, không phải vì tên hay ID của nó. `notifyAll()` chỉ là tín hiệu, không có nghĩa thread được chạy ngay lập tức.

## 3. Blocking I/O: phải đợi dữ liệu hoặc thiết bị

### 3.1. Blocking xảy ra ở đâu?

Một lời gọi I/O **blocking** chỉ trả về sau khi thao tác có kết quả, đạt EOF, gặp lỗi, bị đóng/hủy theo hợp đồng API hoặc đạt timeout nếu API hỗ trợ. Ví dụ:

~~~java
int firstByte = inputStream.read();
~~~

Nếu `InputStream` chưa có byte nào, `read()` có thể đợi. Bên trong phương thức đọc, thread gọi chưa thể tiếp tục thực thi phần code sau `read()`. Trường hợp khác, lời gọi có thể trả về ngay khi đã có dữ liệu, đã đến EOF hoặc luồng đã bị lỗi; **gọi API blocking không đồng nghĩa lần nào cũng thực sự chờ**.

Ví dụ thực tế:
- Đọc socket TCP bằng stream truyền thống khi dữ liệu chưa tới.
- Chờ nhận kết quả từ thiết bị hoặc file system; mức độ block phụ thuộc API, bộ đệm và hệ điều hành.
- Chờ `accept()` của server socket truyền thống khi chưa có kết nối mới.
- Đọc một pipe chưa có byte từ phía ghi.

**Không nhầm elapsed time với CPU time.** Một lời gọi `read()` kéo dài 700 ms có thể tiêu rất ít CPU trong phần lớn thời gian đó, bởi thread đang chờ dữ liệu. Tuy nhiên nó vẫn chiếm **một công việc đang mở**, có thể giữ socket, bộ nhớ, stack, chỗ trong thread pool hoặc quota của tài nguyên khác.

### 3.2. Tác động lên vòng đời và hiệu năng

Với pool có **4 platform worker threads**, nếu cả bốn đang chờ I/O nhiều giây, một task thứ năm có thể đợi trong queue dù CPU của máy còn rảnh. Quan sát thường gặp:

~~~text
4 workers đều đang chờ remote I/O
             |
             +--> CPU có thể còn thấp
             |
             +--> worker pool bận hết
                        |
                        +--> task mới xếp hàng
                        +--> queue wait tăng
                        +--> end-to-end latency tăng
~~~

Tăng số worker **có thể** giúp chạy đồng thời nhiều công việc chờ I/O hơn, nhưng chỉ đến giới hạn thật: số connection, memory, file descriptor, throughput phía dịch vụ phụ thuộc và mức tải được nhận. Nếu mỗi request chờ downstream mất 2 giây, mở thêm nhiều thread không làm downstream hoàn tất nhanh hơn; còn có nguy cơ tăng backlog và làm hệ thống phụ thuộc quá tải.

Thực tế nên đo:
- Thời gian vào queue và thời gian từ lúc worker bắt đầu đến khi hoàn thành.
- Số tác vụ/worker đang chờ I/O, số kết nối đang dùng và số kết nối còn chờ.
- Timeout, retry, kết quả lỗi, số yêu cầu được nhận và số được xử lý thành công.
- p95/p99 latency; CPU thấp **không** đủ kết luận hệ thống có năng lực dư.

### 3.3. Vì sao getState() có thể vẫn là RUNNABLE?

Với một số thao tác blocking I/O cổ điển, JVM quan sát thread ở `RUNNABLE` vì nó đang ở trong lời gọi native/hệ điều hành; hệ điều hành thực tế có thể đang đợi network hay disk. Những API/triển khai khác có thể dùng monitor hoặc công cụ đồng bộ nội bộ khiến bạn nhìn thấy `WAITING` hoặc `TIMED_WAITING`.

Vì vậy:

~~~java
Thread.State snapshot = readerThread.getState();
System.out.println(snapshot);
~~~

Dòng này cho biết **snapshot Java state**, không đủ bằng chứng để khẳng định reader đang tính toán, đã nhận dữ liệu hoặc đang bị block ở socket. Muốn biết chính xác hơn, cần stack trace, thông tin I/O, metrics và một phép đo trước–sau lời gọi.

**Không suy luận ngược:** thấy `RUNNABLE` không chứng minh CPU bận; thấy `WAITING` không chứng minh nguồn chờ là `Object.wait()` của chính code bạn.

## 4. Chờ điều kiện: đợi một predicate trở thành đúng

Khác với chờ I/O từ nguồn dữ liệu bên ngoài, **condition waiting** xuất hiện khi thread cần một trạng thái phối hợp giữa các thread, ví dụ:

- Buffer vẫn trống → consumer chờ producer thêm phần tử.
- Công việc chưa được đánh dấu hoàn tất → thread khác chờ tín hiệu hoàn thành.
- Chưa đủ tài nguyên/số giấy phép → tác vụ chờ quyền tiếp tục.
- Một cờ `ready` do thread khác cập nhật chưa thành `true`.

Mấu chốt là **predicate của điều kiện**, ví dụ `ready == true` hoặc `!queue.isEmpty()`. Tín hiệu `notify/``signal` chỉ thông báo “có thể điều kiện đã thay đổi”; thread được đánh thức vẫn phải kiểm tra predicate. Không dùng tín hiệu đơn thuần làm nguồn sự thật.

### 4.1. busy-wait so với blocking wait

Cách sai về hiệu năng nếu dùng vòng lặp nóng:

~~~java
while (!ready) {
    // Liên tục kiểm tra điều kiện; có thể tiêu CPU cao.
}
~~~

Vòng lặp như vậy còn có rủi ro **visibility** nếu biến `ready` được cập nhật bởi thread khác nhưng không có quan hệ happens-before (chẳng hạn qua `volatile`, lock hoặc atomic). Thêm `volatile` giúp thấy giá trị, nhưng **không** ngăn vòng lặp nóng dùng CPU. `Thread.yield()` cũng không phải giải pháp phối hợp đáng tin.

Cách có cấu trúc là cho thread **ngủ chờ tín hiệu** bằng `wait()`, `Condition.await()` hoặc primitive cấp cao như `BlockingQueue.take()`, `CountDownLatch.await()`. Những cách này tránh đốt CPU trong khi không có việc làm và đặt ra quy tắc đồng bộ rõ ràng.

### 4.2. Object.wait() giải phóng monitor của chính đối tượng

Mẫu chuẩn:

~~~java
final Object monitor = new Object();
boolean ready = false; // Trong code thực: biến nằm trong object chia sẻ được bảo vệ bởi monitor.

// Thread chờ (minh họa, không phải một chương trình độc lập):
synchronized (monitor) {
    while (!ready) {
        monitor.wait(); // Giải phóng monitor; vào wait set.
    }
    // Khi trở lại đây, thread đã lấy lại monitor và ready == true.
}
~~~

Để thread khác cập nhật `ready`, nó cũng phải vào `synchronized (monitor)` và gọi `monitor.notifyAll()` **trong monitor đó**. `wait()`:
1. Yêu cầu thread hiện tại đang giữ đúng monitor, nếu không ném `IllegalMonitorStateException`.
2. Tạm nhả monitor **của đối tượng gọi `wait()`** trong lúc chờ; không tự nhả các monitor khác thread đang giữ.
3. Có thể kết thúc do `notify`/`notifyAll`, interruption hoặc **spurious wakeup**; `wait()` không timeout mặc định.
4. Trước khi trả về bình thường hoặc ném `InterruptedException`, thread phải lấy lại monitor đó.

**Luôn kiểm tra điều kiện bằng `while`, không dùng `if`**: sau khi được đánh thức, điều kiện có thể vẫn sai vì spurious wakeup hoặc thread khác đã tiêu thụ tài nguyên trước.

Sự kiện `notifyAll()` **không chuyển quyền sở hữu monitor ngay**. Thread phát tín hiệu vẫn giữ monitor cho đến khi rời khối synchronized. Các thread được đánh thức phải cạnh tranh để lấy lại monitor, có thể tạm xuất hiện ở `BLOCKED`.

### 4.3. Chờ có timeout

Các API `wait(long timeoutMillis)` hoặc `Condition.await(long, TimeUnit)` giúp đặt giới hạn. Với `Object.wait(0)`, giá trị 0 nghĩa là **chờ không thời hạn**, không phải trả về ngay.

Khi timeout, chỉ có nghĩa “đã hết thời gian chờ theo hợp đồng API”; **không** có nghĩa predicate đã thành đúng. Ví dụ ý tưởng:

~~~java
long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
synchronized (monitor) {
    while (!ready) {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new java.util.concurrent.TimeoutException("ready timeout");
        }
        java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(monitor, remainingNanos);
    }
}
~~~

Đây là **đoạn mã minh họa**, giả định `monitor` và `ready` cùng tồn tại trong phạm vi hợp lệ; khi dùng trong method cần khai báo/xử lý `InterruptedException` và `TimeoutException`. `timedWait` ở đây là phương thức tiện ích của `TimeUnit`, vẫn dựa vào monitor wait. Sử dụng đồng hồ `System.nanoTime()` cho khoảng thời gian, không dùng đồng hồ lịch để tính deadline nội bộ. Trong code production, xử lý deadline cộng số lớn cần cân nhắc overflow, và có thể ưu tiên primitive concurrency có timeout sẵn.

## 5. Phân biệt wait(), sleep(), join(), BLOCKED và Condition.await()

| Tình huống | Tài nguyên/tín hiệu đang chờ | Java state thường thấy | Có giải phóng monitor/lock? |
| --- | --- | --- | --- |
| `Thread.sleep(500)` | Đồng hồ trôi qua | `TIMED_WAITING` | **Không tự nhả** monitor đang giữ |
| `Object.wait()` | Notify/interrupt/spurious wakeup | `WAITING` | Nhả monitor của object dùng `wait()` và lấy lại trước khi trở về |
| `Object.wait(500)` | Tín hiệu hoặc timeout | `TIMED_WAITING` | Nhả monitor tương ứng |
| Cố vào `synchronized` đang bị thread khác giữ | Monitor lock | `BLOCKED` | Chưa lấy được monitor đó |
| `Thread.join()` | Thread mục tiêu kết thúc | `WAITING` | Không có quy tắc “nhả mọi lock” |
| `Thread.join(500)` | Thread mục tiêu kết thúc hoặc timeout | `TIMED_WAITING` | Không có quy tắc “nhả mọi lock” |
| `Condition.await()` với `ReentrantLock` | `signal`/`signalAll`/interrupt/spurious wakeup | Thường `WAITING` | Nhả **lock gắn với Condition**, lấy lại trước khi trở về |
| `BlockingQueue.take()` trên queue trống | Có phần tử mới | Thường `WAITING` | Primitive tự quản lý khóa/điều kiện bên trong |
| I/O blocking đọc socket | Dữ liệu/EOF/lỗi/timeout | Phụ thuộc API và JVM; có thể `RUNNABLE` | **Không tự nhả** những monitor hoặc `Lock` ngoài lời gọi I/O |

Hai lỗi phổ biến:

**Lỗi A — giữ monitor trong khi ngủ**

~~~java
synchronized (monitor) {
    Thread.sleep(1000);
}
~~~

Trong giây đó, thread vẫn giữ monitor. Thread khác cần vào `synchronized(monitor)` phải đợi `BLOCKED`. `sleep()` chỉ tạm ngừng chạy; nó không phải cơ chế chờ predicate và không giải phóng monitor.

**Lỗi B — giữ lock trong khi gọi I/O rất chậm**

~~~java
synchronized (monitor) {
    byte[] data = inputStream.readNBytes(1024);
    // Nếu nguồn dữ liệu chậm, các thread khác có thể mắc kẹt
    // khi chờ cùng monitor, dù CPU không bận.
}
~~~

Đoạn trên chỉ minh họa tác động giữ lock; để dùng cần biến `inputStream` được khai báo và xử lý `IOException`. Nên giới hạn critical section tới cập nhật trạng thái dùng chung, tránh đưa thao tác I/O có thời gian khó dự đoán vào vùng khóa khi không thực sự cần.

## 6. Ví dụ Java 17+: thread chờ điều kiện rồi được đánh thức

Chương trình này minh họa bốn mốc: thread đã khởi chạy, đi vào `wait()` khi `ready=false`, được thread khác `notifyAll()`, và kết thúc. Biến `ready` chỉ được đọc/ghi khi giữ cùng monitor để bảo đảm tính đúng đắn và visibility.

Tạo tệp **`ConditionLifecycleDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;

public class ConditionLifecycleDemo {
    private static final Object MONITOR = new Object();
    private static boolean ready;

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            synchronized (MONITOR) {
                entered.countDown();

                while (!ready) {
                    try {
                        MONITOR.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.out.println("worker-interrupted");
                        return;
                    }
                }
            }
            System.out.println("worker-finished");
        }, "condition-worker");

        System.out.println("before-start=" + worker.getState());
        worker.start();
        entered.await(); // Chắc chắn worker đã vào synchronized ít nhất một lần.

        // Snapshot: có thể là RUNNABLE hoặc WAITING tùy thời điểm.
        System.out.println("during-wait=" + worker.getState());

        synchronized (MONITOR) {
            ready = true;
            MONITOR.notifyAll();
        }

        worker.join(); // Đợi worker hoàn thành.
        System.out.println("after-join=" + worker.getState());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ConditionLifecycleDemo.java
java ConditionLifecycleDemo
~~~

Một đầu ra **có thể** quan sát:

~~~text
before-start=NEW
during-wait=WAITING
worker-finished
after-join=TERMINATED
~~~

**`during-wait` không được cam kết luôn là `WAITING`**: ngay sau `entered.countDown()`, worker vẫn có thể chưa gọi `wait()`. Nếu main lấy `MONITOR` trước, worker có thể chưa chờ, rồi sau đó nhìn thấy `ready=true` và bỏ qua `wait()`. Trên những lượt khác, snapshot có thể là `RUNNABLE`, `BLOCKED` hoặc trạng thái phù hợp với lịch chạy. Điểm đáng tin là `before-start=NEW`, việc cuối cùng in `worker-finished` (khi không bị ngắt), rồi `after-join=TERMINATED`.

Tại sao chương trình không bị “lost notification”?
- `ready` được kiểm tra **dưới cùng monitor** với thao tác cập nhật/`notifyAll()`.
- Nếu main đặt `ready=true` trước khi worker kịp `wait()`, worker sẽ thấy predicate đúng và **không chờ**. Không phụ thuộc vào việc có “ghi nhớ” tín hiệu notify hay không.
- Nếu worker đã `wait()`, thao tác wait nhả monitor và main có thể vào để thay đổi predicate rồi phát tín hiệu.
- `while` bảo vệ khỏi wakeup giả hoặc thay đổi predicate trong chương trình phức tạp hơn.

Lưu ý `CountDownLatch` trong demo chỉ điều phối điểm quan sát `entered.await()`; nó **không bảo đảm** worker đã chuyển vào WAITING. Nếu cần viết test về trạng thái, hãy dùng công cụ đồng bộ chủ động và timeout; đừng kiểm tra một `getState()` duy nhất như một assertion chắc chắn.

## 7. Ví dụ Java 17+: một reader chờ dữ liệu I/O

Để quan sát blocking I/O mà không cần server/mạng bên ngoài, dùng một cặp `PipedInputStream` / `PipedOutputStream`. Reader gọi `read()` khi chưa có byte nào; writer ở thread main gửi ký tự `A` sau một khoảng chờ.

Đây là **một pipe trong bộ nhớ của Java**, thích hợp cho thí nghiệm, không đại diện chính xác cho mọi loại socket hoặc native file I/O. Phần `Thread.State` quan sát được phụ thuộc cách `PipedInputStream` triển khai chờ.

Tạo file **`BlockingIoLifecycleDemo.java`**:

~~~java
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;

public class BlockingIoLifecycleDemo {
    public static void main(String[] args) throws Exception {
        try (PipedInputStream input = new PipedInputStream();
             PipedOutputStream output = new PipedOutputStream(input)) {

            CountDownLatch startingRead = new CountDownLatch(1);
            Thread reader = new Thread(() -> {
                try {
                    startingRead.countDown();
                    int value = input.read(); // Có thể chờ cho tới khi có byte.
                    System.out.println("received=" + (char) value);
                } catch (IOException e) {
                    System.out.println("io-error=" + e.getClass().getSimpleName());
                }
            }, "pipe-reader");

            reader.start();
            startingRead.await();

            Thread.sleep(100); // Chỉ tạo khoảng trống để quan sát.
            System.out.println("while-reading=" + reader.getState());

            output.write('A');
            output.flush();

            reader.join();
            System.out.println("after-join=" + reader.getState());
        }
    }
}
~~~

Chạy:

~~~bash
javac BlockingIoLifecycleDemo.java
java BlockingIoLifecycleDemo
~~~

Trên lượt chạy bình thường, ví dụ đầu ra:

~~~text
while-reading=TIMED_WAITING
received=A
after-join=TERMINATED
~~~

Chỉ hai dòng **`received=A`** và **`after-join=TERMINATED`** được kỳ vọng ổn định khi I/O thành công và thread đã hoàn thành. **`while-reading` là snapshot, không có giá trị cố định**: có thể là `TIMED_WAITING`, `WAITING`, `RUNNABLE` hoặc trạng thái khác hợp lệ theo thời điểm/implementation. Nếu stream lỗi, nhánh `io-error=...` sẽ xuất hiện thay cho dữ liệu thành công.

Quan sát quan trọng: điều kiện để reader tiến lên là **có byte từ phía writer**, chứ không phải việc main đánh thức monitor của reader bằng `notifyAll()`. Cũng không nên gọi `reader.interrupt()` rồi mặc định mọi `InputStream.read()` sẽ thoát ngay: hỗ trợ interruption khác nhau theo API và đường I/O.

Trong ví dụ, main gọi `reader.join()` để đợi kết quả; thao tác `join()` là **một kiểu chờ khác** của main thread, không làm reader tự chuyển sang RUNNABLE.

## 8. Condition.await() và các primitive thay cho wait/notify thủ công

`Object.wait()/notifyAll()` giúp hiểu cơ chế monitor, nhưng trong code nghiệp vụ, primitive concurrency cấp cao thường đơn giản và ít lỗi hơn.

### 8.1. ReentrantLock + Condition

Với `java.util.concurrent.locks.ReentrantLock`, điều kiện gắn với lock được tạo bởi `newCondition()`:

~~~java
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

// Đoạn minh họa trong một class có trạng thái chia sẻ:
private final ReentrantLock lock = new ReentrantLock();
private final Condition canProceed = lock.newCondition();
private boolean ready = false;

public void awaitReady() throws InterruptedException {
    lock.lockInterruptibly();
    try {
        while (!ready) {
            canProceed.await(); // Nhả lock; chờ; lấy lại lock trước khi trả về.
        }
    } finally {
        lock.unlock();
    }
}

public void markReady() {
    lock.lock();
    try {
        ready = true;
        canProceed.signalAll();
    } finally {
        lock.unlock();
    }
}
~~~

Đây là **đoạn thành viên class**, không phải chương trình độc lập. Khi ghép vào class hợp lệ, các method minh họa sự khác nhau của `await` và `signalAll` với `wait` và `notifyAll`:
- `Condition.await()` phải được gọi khi thread giữ **lock tương ứng**. Nó nhả lock khi chờ và lấy lại trước khi trả về.
- `signalAll()` không mang predicate; thread được đánh thức vẫn kiểm tra lại `ready` trong `while`.
- Luôn `unlock()` trong `finally`; khi interruption xảy ra trong `await()`, phép thu hồi lock được xử lý theo hợp đồng `Condition` trước khi unwinding.
- `lockInterruptibly()` có thể bị gián đoạn trong lúc đợi lấy lock. `lock()` có semantics khác, vì thế cần chọn theo yêu cầu hủy tác vụ.

### 8.2. BlockingQueue.take() diễn đạt điều kiện “có phần tử”

Nếu nhu cầu thật sự là **consumer chờ producer đưa công việc**, `BlockingQueue` giúp bỏ thao tác `wait/notify` thủ công:

~~~java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class QueueWaitDemo {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        Thread consumer = new Thread(() -> {
            try {
                String value = queue.take(); // Đợi cho đến khi có phần tử.
                System.out.println("consumed=" + value);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("consumer-interrupted");
            }
        }, "consumer");

        consumer.start();
        queue.put("work");
        consumer.join();
    }
}
~~~

Lưu file **`QueueWaitDemo.java`**, chạy:

~~~bash
javac QueueWaitDemo.java
java QueueWaitDemo
~~~

Khi không có interruption:

~~~text
consumed=work
~~~

Nếu main `put` trước khi consumer `take`, consumer lấy phần tử ngay và **không cần vào WAITING**. Nếu consumer `take` trước khi có phần tử, nó chờ theo cơ chế nội bộ của queue. Trong cả hai trường hợp chương trình vẫn đúng: **dữ liệu trong queue là trạng thái cần thiết**, không phụ thuộc vào một tín hiệu bị bỏ lỡ.

Với `LinkedBlockingQueue<>()` không truyền capacity, mặc định capacity lớn (tối đa `Integer.MAX_VALUE`); trong hệ thống nhận tải thực, nên cân nhắc queue hữu hạn, timeout và chính sách từ chối phù hợp thay vì tích backlog không giới hạn theo mục đích thiết kế.

### 8.3. CountDownLatch và join có mục đích khác

- `CountDownLatch.await()`: chờ một bộ đếm về 0; dùng cho tín hiệu một lần, sau khi về 0 thì không reset.
- `Thread.join()`: chờ **thread mục tiêu kết thúc**; không phải chờ dữ liệu mạng và không có ý nghĩa rằng “tác vụ logic” đã hoàn thành nếu nó tiếp tục ở worker khác.
- `Future.get()`: chờ kết quả của task hoặc nhận exception; có biến thể có timeout.
- `Semaphore.acquire()`: chờ giấy phép; thích hợp giới hạn số tác vụ cùng dùng tài nguyên khan hiếm.

Dùng primitive phù hợp sẽ khiến intent rõ hơn và giảm nguy cơ deadlock, missed signal và race condition.

## 9. Interrupt, timeout, đóng tài nguyên và cancellation

Một thread có thể chờ vô hạn nếu nguồn dữ liệu, điều kiện hoặc tín hiệu hoàn thành không bao giờ đến. Vì thế, thiết kế concurrency phải trả lời: **khi nào ngừng chờ và ai chịu trách nhiệm giải phóng tài nguyên?**

### 9.1. Interruption là yêu cầu hợp tác, không phải dừng cưỡng bức

`Thread.interrupt()` đặt interrupt status và có hành vi riêng cho một số thao tác chờ.

Ví dụ đối với `Object.wait()`, `Thread.sleep()`, `Thread.join()` và nhiều primitive `java.util.concurrent`, interrupt có thể làm ném `InterruptedException`, đồng thời cờ interrupt bị xóa khi exception được phát sinh. Mẫu xử lý khi code không thể ném tiếp exception:

~~~java
try {
    queue.take();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt(); // Khôi phục cờ cho lớp gọi ngoài.
    return;
}
~~~

Đây là **đoạn minh họa** nằm trong method nhận biết biến `queue`. Nếu API của bạn có thể khai báo `throws InterruptedException`, thường nên để exception truyền lên thay vì nuốt. Không tiếp tục vòng lặp chờ vô hạn sau khi bắt và bỏ qua interrupt nếu task cần khả năng hủy.

**Với I/O: quy tắc khác nhau theo API.** Ví dụ thao tác đang block trên `InterruptibleChannel` có thể khiến channel đóng và ném `ClosedByInterruptException`; `Selector` có xử lý interrupt riêng. Không có bảo đảm chung rằng `interrupt()` sẽ ngắt mọi `InputStream.read()` hoặc mọi native call trong Java 17. Hãy dùng timeout của đúng API, cancellation và/hoặc đóng tài nguyên theo hợp đồng được tài liệu hóa. Cẩn thận để thread vẫn có đường thoát trong khi ứng dụng shutdown.

### 9.2. Timeout phải có ranh giới và hậu quả rõ ràng

Ví dụ khi chờ queue, thay `take()` bằng `poll(2, TimeUnit.SECONDS)`: nếu hết hạn, kết quả `null` nghĩa là **không lấy được phần tử trước deadline**, không phải queue đã hỏng. Với socket truyền thống, có thể cấu hình read timeout bằng API tương ứng (`Socket#setSoTimeout`); timeout đọc khác với timeout kết nối và không thay thế deadline đầu-cuối của yêu cầu.

Một chính sách hoàn chỉnh cần phân biệt:
- **Timeout**: thời gian chờ tối đa cho một operation.
- **Cancellation**: người dùng/hệ thống yêu cầu không làm tiếp công việc.
- **Backpressure/admission control**: giảm tải được nhận khi tài nguyên/hàng đợi đầy.
- **Resource cleanup**: đóng stream/socket, trả connection, nhả lock trong `finally` hoặc try-with-resources.

Nếu chỉ tăng timeout nhưng không giải quyết nguồn chờ, số task bị giữ lại có thể tăng và làm các request sau chậm hơn.

## 10. Bài toán thread pool và virtual threads

### 10.1. Platform threads là tài nguyên hữu hạn

Một platform thread thường tương ứng với một thread của hệ điều hành. Trong mô hình **thread-per-request**, nếu mỗi request chủ yếu block ở I/O, số thread cần để giữ nhiều request đồng thời có thể lớn dù CPU còn rảnh. Điều này gây:
- Chi phí stack, scheduling và thread management.
- Queue chờ worker rảnh trong `ExecutorService` hữu hạn.
- Gia tăng connection, open files và áp lực lên dịch vụ phụ thuộc.

Tác vụ chờ I/O có thể hưởng lợi từ concurrency cao hơn so với tác vụ CPU-bound, nhưng giới hạn hợp lý phụ thuộc **tài nguyên thực tế và SLO**. Cần đo latency, successful throughput, số request chờ, lỗi và pool utilization, không chọn thread count chỉ từ số core.

### 10.2. Virtual threads (Java 21+) thay đổi chi phí của việc chờ

Virtual threads là một cơ chế thread nhẹ hơn cho workload có nhiều tác vụ độc lập chờ I/O. Với những hoạt động blocking được JVM hỗ trợ, virtual thread có thể **nhường carrier platform thread** trong thời gian chờ, cho phép carrier phục vụ virtual thread khác. Điều này giúp các mô hình có nhiều tác vụ chờ thuận tiện hơn.

Nhưng cần nhớ:
- Virtual threads **không tăng số CPU core** và không làm remote service trả lời nhanh hơn.
- Chúng vẫn tiêu memory, file descriptor, connection và capacity của tài nguyên phụ thuộc.
- Không suy ra trạng thái chính xác của carrier từ `Thread.currentThread().getState()` của virtual thread.
- Một số blocking hoặc native/foreign operations, cơ chế đồng bộ và hành vi pinning phụ thuộc phiên bản JDK cụ thể; cần dùng tài liệu/quan sát đúng JDK khi phân tích vấn đề này.
- `Thread.State` và các API `wait/interrupt` vẫn phải được hiểu theo hợp đồng Java; virtual thread không biến chờ không có timeout thành an toàn vô điều kiện.

Các ví dụ chạy được trong bài dùng Java 17 và platform threads để cô lập nguyên nhân chờ. Phần virtual threads chỉ cung cấp bối cảnh Java 21+ chứ không yêu cầu dùng API mới để chạy demo.

## 11. Chẩn đoán “thread bị kẹt” bằng thông tin nào?

### 11.1. Đọc stack và Java state cùng nhau

Dùng `Thread.getState()` để nhìn trạng thái hiện tại, kết hợp `Thread.getStackTrace()` hoặc thread dump (ví dụ `jcmd <pid> Thread.print` trên JDK hỗ trợ). Đối với CPU, lock và thread hoạt động trong Java process, có thể dùng Java Flight Recorder (JFR), Java Mission Control (JMC) hoặc `ThreadMXBean` nếu hỗ trợ các phép đo liên quan.

| Dấu hiệu | Có thể đang xảy ra | Cần kiểm chứng tiếp |
| --- | --- | --- |
| Thread dump có `BLOCKED`, stack tại `synchronized` | Cạnh tranh monitor | Ai giữ monitor, giữ trong bao lâu, có lock-order cycle không |
| `WAITING` ở `Object.wait()` hoặc `Condition.await()` | Đang đợi predicate/tín hiệu | Predicate được cập nhật ở đâu, ai signal, có đường timeout/hủy không |
| `TIMED_WAITING` ở `Thread.sleep` | Đang ngủ theo thời gian | Có đang giữ lock, sleep có thay thế waiting logic một cách sai lầm không |
| `RUNNABLE` với stack trong `SocketInputStream.read` hoặc native read | Có thể đang đợi I/O | Remote latency, socket timeout, connection count, log trước/sau read |
| Nhiều pool workers active, CPU thấp, queue dài | Nhiều worker có thể đang chờ I/O/lock | Đo wait duration, queue size, pool metrics và downstream |
| `TERMINATED` nhưng task nghiệp vụ chưa có kết quả cuối | Đường xử lý có thể đã chuyển sang thread khác hoặc thất bại | Correlation ID, Future/status của task, luồng dữ liệu |

**Không kết luận deadlock chỉ vì thread ở `WAITING` lâu.** Một consumer không có công việc có thể chờ hoàn toàn hợp lệ. Deadlock cần bằng chứng có chu trình phụ thuộc khóa/tài nguyên khiến các bên không thể tiến triển; `ThreadMXBean.findDeadlockedThreads()` có thể giúp phát hiện một số trường hợp, nhưng không thay thế phân tích nguyên nhân chờ I/O hay dependency bên ngoài.

### 11.2. Đo thời gian ở đúng ranh giới

Đo các quãng riêng biệt bằng `System.nanoTime()`:

~~~text
submit/enqueue ---- queue wait ---- start worker
                                         |
                                         +-- waiting for lock
                                         +-- blocking I/O
                                         +-- condition wait
                                         +-- CPU work
                                         |
                                       complete
~~~

**Task latency = queue wait + elapsed time trong worker**, còn **CPU time** chỉ tính thời gian CPU thực sự được sử dụng. Nếu một worker chờ I/O 1 giây rồi xử lý CPU 5 ms, elapsed khoảng 1 giây không có nghĩa CPU đã chạy 1 giây.

Dùng histogram/p95/p99 và các chỉ số đối ứng (timeout/rejection/connection/CPU). Thread state snapshot cho biết **đang ở trạng thái nào lúc lấy mẫu**; số đo thời gian mới cho biết **chờ bao lâu**.

## 12. Những lỗi thường gặp và cách khắc phục

1. **Thấy `RUNNABLE` rồi khẳng định thread đang tiêu CPU.** Có thể thread đang ở native/blocking I/O hoặc chờ lập lịch. Cần kết hợp stack, CPU time và I/O metrics.
2. **Gọi mọi kiểu chờ là `BLOCKED`.** Trong `Thread.State`, `BLOCKED` là chờ monitor; `WAITING` và `TIMED_WAITING` có nghĩa khác.
3. **Dùng `Thread.sleep()` để “đợi khi có dữ liệu”.** Sleep không gắn với predicate hoặc tín hiệu, có thể trễ vô ích hoặc polling liên tục. Ưu tiên primitive concurrency có điều kiện.
4. **Gọi `wait()` bên ngoài `synchronized`.** Có thể bị `IllegalMonitorStateException`. Phải giữ đúng monitor.
5. **Dùng `if` thay cho `while` quanh `wait/await`.** Không chống được spurious wakeup hoặc cạnh tranh điều kiện sau khi được đánh thức.
6. **Chỉ gọi `notify()` để sửa mọi lỗi không tiến triển.** Tín hiệu không thay predicate; cũng có thể đánh thức sai waiter nếu nhiều điều kiện chung monitor. Dùng predicate đúng và `notifyAll()`/`signalAll()` khi thích hợp, hoặc abstraction cấp cao.
7. **Nghĩ `notifyAll()` khiến thread chạy ngay.** Waiter còn cần lấy lại monitor/lock; thứ tự đánh thức không chứng minh thứ tự chạy.
8. **Giữ lock trong lúc I/O chậm.** Một thread chờ network vẫn có thể giữ monitor, làm thread khác chuyển thành `BLOCKED`.
9. **Nuốt `InterruptedException` và tiếp tục vòng lặp.** Có thể làm cancellation/shutdown bị kẹt. Truyền exception lên hoặc khôi phục interrupt status và thoát đúng đường.
10. **Tăng vô hạn số worker để giải quyết I/O chậm.** Có thể chuyển bottleneck sang connection, database, memory, file descriptor hoặc downstream; phải đo và giới hạn concurrent work.
11. **Không đặt timeout cho chờ phụ thuộc bên ngoài.** Một request lỗi có thể giữ tài nguyên vô hạn. Cần timeout và cleanup theo đúng API.
12. **Kiểm tra `getState() == WAITING` để quyết định có gọi notify không.** Snapshot có race và không thể thay thế predicate dưới cùng cơ chế đồng bộ.
13. **Cho rằng virtual threads xóa mọi giới hạn.** Chúng giảm chi phí thread trong nhiều tình huống chờ, nhưng không xóa giới hạn tài nguyên hoặc yêu cầu timeout/cancellation.

## 13. Câu hỏi tự kiểm tra

1. Một thread đang chờ byte qua socket và `getState() == RUNNABLE`. Điều đó có chứng minh nó đang chạy CPU không? **Không.** Java state có thể không thể hiện chi tiết chờ I/O ở OS.
2. Một thread cố vào `synchronized(lock)` khi thread khác đang giữ monitor. Java state có thể là gì? **`BLOCKED`.**
3. Tại sao `Object.wait()` cho phép producer vào cùng monitor còn `Thread.sleep()` trong `synchronized` thì không? **`wait()` nhả monitor liên quan; `sleep()` không nhả monitor.**
4. Tại sao phải viết `while (!ready) monitor.wait()` thay vì `if`? **Predicate có thể vẫn sai do spurious wakeup hoặc thread khác thay đổi tình trạng trước khi waiter lấy lại monitor.**
5. `notifyAll()` có bảo đảm waiter bắt đầu chạy ngay không? **Không.** Waiter còn phải cạnh tranh lấy lại monitor.
6. Pool bốn worker đều đợi database 2 giây, CPU tiến trình chỉ 10%, queue đang tăng. Vì sao? **Worker slot đã bị chiếm bởi task đang chờ; CPU thấp không đồng nghĩa worker còn rảnh.**
7. `interrupt()` có bảo đảm ngắt mọi lời gọi I/O trong Java 17 không? **Không.** Hợp đồng tùy loại stream/channel; cần timeout/cancellation/close phù hợp.
8. Khi quan sát trạng thái `WAITING` trong `BlockingQueue.take()`, đó có phải dấu hiệu deadlock? **Chưa chắc.** Queue trống và consumer chờ dữ liệu là tình trạng bình thường.
9. Virtual threads giúp vấn đề nào và không giúp điều gì? **Giảm chi phí giữ nhiều tác vụ đồng thời đang chờ ở các blocking operations được hỗ trợ; không tăng CPU hay tốc độ nguồn dữ liệu.**

## Kết luận

**Blocking I/O** làm tiến trình thực thi của một công việc phải đợi dữ liệu hoặc tài nguyên bên ngoài; **condition waiting** làm thread đợi một predicate do các thread phối hợp thay đổi. Cả hai đều có thể ngăn task hoàn tất dù CPU rảnh. Tuy vậy, **`Thread.State.BLOCKED` chỉ nói về chờ monitor lock**, trong khi I/O có thể quan sát là `RUNNABLE` và condition waiting thường xuất hiện là `WAITING` hoặc `TIMED_WAITING`.

Khi thiết kế Java concurrency, hãy biểu diễn điều kiện bằng predicate và primitive phù hợp, tránh giữ lock lúc chờ I/O, có timeout/cancellation/cleanup, và đo cả queue wait lẫn thời gian chờ thực tế. **Không dùng snapshot `getState()` làm cơ chế đồng bộ hoặc làm kết luận duy nhất về hiệu năng.**

### Tham khảo Java API

- [Java SE 17 — Thread.State](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.State.html): định nghĩa sáu trạng thái và giới hạn khi suy ra OS state.
- [Java SE 17 — Object.wait/notify](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): nhả monitor, reacquire, interruption và spurious wakeup.
- [Java SE 17 — Thread](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): `sleep()`, `join()`, `interrupt()`.
- [Java SE 17 — Condition](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Condition.html): `await`, `signal` và quy tắc lock.
- [Java SE 17 — BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html): `take`, `poll`, `put`.
- [Java SE 17 — PipedInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/PipedInputStream.html): đọc pipe khi chưa có byte.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-lifecycle-008">Khởi chạy một lần và lỗi khởi chạy lại thread</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi chạy một lần và lỗi khởi chạy lại thread trong Java

## 1. Quy tắc cốt lõi: một đối tượng Thread chỉ được start một lần

Trong Java, một đối tượng `java.lang.Thread` biểu diễn **một lần thực thi của một luồng**. Sau khi tạo:

~~~java
// Đoạn minh họa, không phải chương trình độc lập.
Thread worker = new Thread(() -> doWork(), "worker-1");
worker.start();
~~~

lời gọi `start()` yêu cầu JVM bắt đầu một luồng thực thi mới. Đến lượt được lập lịch, luồng đó chạy `run()`; khi `run()` kết thúc bình thường hoặc thoát vì một lỗi không được bắt, luồng đi tới trạng thái kết thúc.

**Cùng đối tượng `Thread` không thể được `start()` lần thứ hai**, kể cả khi công việc lần đầu đã hoàn tất, luồng đã được `join()`, hoặc người viết mã cố gắng “reset” trạng thái. Java ném `IllegalThreadStateException` khi cố khởi chạy lại đối tượng đã start. Muốn thực hiện công việc lần nữa, hãy **tạo một đối tượng `Thread` mới** hoặc gửi task mới cho một `ExecutorService` phù hợp.

Ba đối tượng khác nhau cần phân biệt:

- **Task**: công việc được mô tả bởi `Runnable` hoặc `Callable`; có thể được gọi hoặc gửi thực hiện nhiều lần nếu logic cho phép.
- **Thread object**: một phiên thực thi có vòng đời riêng, từ chưa chạy đến đã kết thúc; chỉ `start()` một lần.
- **Worker trong thread pool**: thread mà executor quản lý, có thể thực thi **nhiều task lần lượt** bằng vòng lặp nội bộ; điều này không đồng nghĩa với việc gọi `start()` lặp lại trên worker đó.

Quy tắc một lần áp dụng cho **đối tượng luồng**, không cấm tái sử dụng logic của task.

## 2. Đặt trong vòng đời Thread

`Thread.State` trong Java phân biệt sáu trạng thái quan sát được:

| Trạng thái | Ý nghĩa | Có thể gọi `start()`? |
| --- | --- | --- |
| `NEW` | Đối tượng thread đã được tạo nhưng chưa khởi chạy | Có, **một lần**, nếu chưa có lời gọi `start()` cạnh tranh |
| `RUNNABLE` | Luồng đang chạy hoặc sẵn sàng được CPU lập lịch | Không |
| `BLOCKED` | Luồng đợi để lấy monitor lock | Không |
| `WAITING` | Luồng chờ vô thời hạn một điều kiện/sự kiện phù hợp | Không |
| `TIMED_WAITING` | Luồng chờ với thời hạn, ví dụ `sleep` hoặc `join` có timeout | Không |
| `TERMINATED` | Luồng đã hoàn thành thực thi | Không |

Một đường đi đơn giản:

~~~text
new Thread(...)
      |
      v
     NEW
      |
    start()  [duy nhất một lần]
      |
      v
  RUNNABLE <----> BLOCKED / WAITING / TIMED_WAITING
      |
   run() kết thúc hoặc lỗi không được bắt
      |
      v
  TERMINATED
      |
  start() lần nữa -> IllegalThreadStateException
~~~

Bảng chỉ mô tả khả năng quan sát, không phải sơ đồ bắt buộc mỗi thread phải đi qua tất cả các trạng thái. Ví dụ thread có thể đi từ `RUNNABLE` tới `TERMINATED` mà không từng `WAITING`.

**Điểm rất dễ nhầm:** `TERMINATED` không tương đương `NEW`. Việc kết thúc chỉ nói rằng luồng thực thi cũ đã dừng; nó không đưa đối tượng trở lại trạng thái chưa được start. API không cung cấp thao tác hợp lệ nào để “reset” một `Thread` đã kết thúc.

Giá trị `getState()` là **ảnh chụp tại một thời điểm** và có thể thay đổi ngay sau khi đọc. Điều kiện `thread.getState() == Thread.State.NEW` không phải cơ chế phối hợp an toàn để cho nhiều thread cạnh tranh khởi chạy cùng một đối tượng.

## 3. Ví dụ 1 — start, join rồi start lại (Java 17+)

Tạo tệp `StartOnceDemo.java`:

~~~java
public class StartOnceDemo {
    public static void main(String[] args)
            throws InterruptedException {

        Thread worker = new Thread(() -> {
            System.out.println(
                "Running on " + Thread.currentThread().getName()
            );
        }, "report-worker-1");

        System.out.println("Before start: " + worker.getState());

        worker.start();
        worker.join();

        System.out.println("After join: " + worker.getState());

        try {
            worker.start(); // Cùng đối tượng: không được phép.
        } catch (IllegalThreadStateException ex) {
            System.out.println(
                "Second start failed: " + ex.getClass().getSimpleName()
            );
        }

        System.out.println("Finished");
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac StartOnceDemo.java
java StartOnceDemo
~~~

Đầu ra chính xác:

~~~text
Before start: NEW
Running on report-worker-1
After join: TERMINATED
Second start failed: IllegalThreadStateException
Finished
~~~

Phân tích từng bước:

1. `new Thread(...)` chỉ tạo đối tượng, nên `getState()` trước khi gọi `start()` là `NEW`.
2. `start()` khởi tạo quá trình thực thi và trả về cho thread gọi; JVM quyết định thời điểm worker thực sự được chạy. Vì vậy không nên suy ra worker đã hoàn tất chỉ từ việc `start()` trả về.
3. `join()` trên `worker` khiến thread `main` chờ tới khi worker kết thúc; sau khi `join()` trả về bình thường, thread đã `TERMINATED`.
4. Gọi `worker.start()` lần nữa bị từ chối với `IllegalThreadStateException` dù `run()` trước đó không gặp lỗi.
5. `catch` minh họa cách phát hiện thao tác sai. Trong code thực tế, thiết kế nên **tránh gọi `start()` lần hai** thay vì thường xuyên dựa vào exception để điều khiển luồng.

`IllegalThreadStateException` là unchecked exception, nên compiler không bắt buộc khai báo `throws` cho nó. Phần `throws InterruptedException` trên `main` là vì `join()` có thể bị interrupt, **không phải** do `start()` lần hai.

### Vì sao `join()` không giúp khởi chạy lại?

`join()` chỉ chờ một thread kết thúc, không tạo thêm thread và không thay đổi lịch sử đã start của đối tượng. Gọi `join()` lần nữa trên một thread đã kết thúc chỉ trở về; điều này vẫn không cấp quyền `start()` lần nữa.

Ngược lại, gọi `join()` trên một thread **chưa từng start** có thể trở về ngay vì thread chưa sống. Vì thế không dùng `join()` như tín hiệu xác nhận công việc đã từng chạy thành công; phải biết rõ vòng đời và kênh kết quả của task.

## 4. `start()` và `run()` là hai thao tác khác nhau

Tên `run()` dễ khiến người học nghĩ có thể “chạy lại thread” bằng cách gọi trực tiếp. Thực tế:

- `thread.start()` yêu cầu tạo luồng thực thi độc lập; JVM sẽ gọi `run()` trong luồng đó. Chỉ được start một lần.
- `thread.run()` là **lời gọi phương thức Java bình thường**, chạy trên **thread đang gọi**; nó không tạo thread mới và tự nó không chuyển đối tượng từ `NEW` sang `RUNNABLE`.
- Gọi trực tiếp `run()` nhiều lần có thể lặp lại công việc ở thread gọi, nhưng **không phải** chạy lại phiên luồng đã start.

### Ví dụ 2 — so sánh bằng đúng thread thực thi (Java 17+)

Tạo tệp `RunVsStartDemo.java`:

~~~java
public class RunVsStartDemo {
    public static void main(String[] args)
            throws InterruptedException {

        Thread caller = Thread.currentThread();

        Thread worker = new Thread(() -> {
            System.out.println(
                "Runs on caller: "
                    + (Thread.currentThread() == caller)
            );
        }, "actual-worker");

        worker.run();
        System.out.println(
            "State after direct run: " + worker.getState()
        );

        worker.start();
        worker.join();

        System.out.println(
            "State after start and join: " + worker.getState()
        );
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac RunVsStartDemo.java
java RunVsStartDemo
~~~

Đầu ra chính xác:

~~~text
Runs on caller: true
State after direct run: NEW
Runs on caller: false
State after start and join: TERMINATED
~~~

Lần đầu, `worker.run()` được gọi như một phương thức và chạy trên thread `main`; sau lời gọi, đối tượng `worker` vẫn `NEW`. Lần sau, `worker.start()` khởi chạy thread thực sự mang tên `actual-worker`, nên phép so sánh với `caller` trả về `false`. Hai lần in được sắp theo thứ tự ổn định nhờ lời gọi trực tiếp đồng bộ và `join()`.

Một ví dụ `Runnable` đơn giản có thể thực thi nhiều lần nếu nội dung tác vụ phù hợp, nhưng không có nghĩa mỗi lần thực thi là một thread mới. Với tác vụ có side effect (ghi file, trừ số dư, gửi request), lặp lại `run()` còn có thể gây lặp xử lý nghiệp vụ.

## 5. Cách đúng để chạy cùng công việc lần nữa

### 5.1. Tạo một Thread mới cho mỗi lần thực thi

Bạn có thể tái sử dụng **đối tượng `Runnable`** khi nó không có trạng thái gây lỗi trong các lần chạy:

~~~java
// Minh họa ý tưởng; Runnable chỉ đọc, không sửa trạng thái dùng chung.
Runnable task = () -> System.out.println("A unit of work");

Thread first = new Thread(task, "job-1");
Thread second = new Thread(task, "job-2");

first.start();
second.start();
~~~

Đây là hai đối tượng `Thread` khác nhau. Mỗi đối tượng có quyền `start()` đúng một lần. Nếu muốn chạy **tuần tự** và tránh sự phụ thuộc vào lịch chạy, chờ `first.join()` trước khi `second.start()`.

### Ví dụ 3 — tạo thread mới ở mỗi lượt (Java 17+)

Tạo tệp `NewThreadPerRunDemo.java`:

~~~java
public class NewThreadPerRunDemo {
    public static void main(String[] args)
            throws InterruptedException {

        Runnable task = () -> {
            System.out.println(
                "Executed by " + Thread.currentThread().getName()
            );
        };

        for (int i = 1; i <= 3; i++) {
            Thread worker = new Thread(task, "task-worker-" + i);
            worker.start();
            worker.join();
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac NewThreadPerRunDemo.java
java NewThreadPerRunDemo
~~~

Đầu ra chính xác:

~~~text
Executed by task-worker-1
Executed by task-worker-2
Executed by task-worker-3
~~~

Vòng lặp tạo **ba đối tượng thread mới**, mỗi đối tượng start một lần. `join()` trong vòng lặp buộc từng lượt hoàn tất rồi mới tới lượt kế tiếp, giúp thứ tự output xác định. Khi muốn concurrency thật sự giữa ba task, có thể khởi chạy cả ba trước rồi mới `join()` cả ba, nhưng thứ tự in lúc đó không cố định.

**Chi phí:** Tạo platform thread mới cho lượng lớn công việc ngắn có thể tốn thời gian, bộ nhớ ngăn xếp và chi phí lập lịch. Đừng dùng “tạo thread mới mỗi lần” làm chiến lược mặc định cho số lượng task rất lớn.

### 5.2. Dùng ExecutorService cho chuỗi task dài

`ExecutorService` nhận nhiều task và tự quản lý worker thread. Người gọi gửi task bằng `execute(...)` hoặc `submit(...)`; **không** tự gọi `start()` trên worker do executor nắm giữ.

Tạo tệp `RepeatedTasksDemo.java`:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class RepeatedTasksDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor =
            Executors.newSingleThreadExecutor(
                task -> new Thread(task, "queue-worker-1")
            );

        try {
            for (int i = 1; i <= 3; i++) {
                int taskId = i;

                Future<String> result = executor.submit(() ->
                    "Task " + taskId + " on "
                        + Thread.currentThread().getName()
                );

                System.out.println(result.get());
            }
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac RepeatedTasksDemo.java
java RepeatedTasksDemo
~~~

Đầu ra chính xác khi task hoàn tất bình thường:

~~~text
Task 1 on queue-worker-1
Task 2 on queue-worker-1
Task 3 on queue-worker-1
~~~

Với `newSingleThreadExecutor`, các task thực thi tuần tự trên một worker nếu worker còn hoạt động bình thường. Worker có thể nhận **nhiều task qua vòng lặp của executor**, nhưng bản thân worker vẫn chỉ được `start()` một lần khi executor tạo ra nó.

- `submit(...)` trả về `Future` để lấy kết quả hoặc exception của task. `get()` chờ hoàn tất; code mẫu dùng `get()` để có thứ tự output xác định.
- Worker có thể được thay thế khi chết bất thường, vì vậy **không nên lấy một tên thread hoặc identity worker làm bảo đảm bất biến cho toàn bộ vòng đời ứng dụng**.
- `shutdown()` ngừng nhận task mới nhưng cho task đã nộp tiếp tục; `awaitTermination(...)` chờ có giới hạn; `shutdownNow()` chỉ yêu cầu ngắt các task chưa kết thúc.
- Sau khi executor bị shutdown, muốn gửi công việc mới cần một executor khác; gửi task vào executor đã shutdown thường bị `RejectedExecutionException`. Đây là **quy tắc vòng đời executor**, khác với `IllegalThreadStateException` của việc gọi `Thread.start()` lần hai.

### 5.3. Chọn cách nào?

| Nhu cầu | Cách chọn | Ghi chú |
| --- | --- | --- |
| Một thao tác đơn lẻ, muốn thấy cơ chế Thread | `new Thread(task)` rồi `start()` | Đơn giản để học, phải quản lý `join`/lỗi |
| Chạy lại một logic cụ thể | Tạo **Thread mới** dùng cùng `Runnable` an toàn | Không reuse đối tượng Thread cũ |
| Nhiều task liên tục, cần kiểm soát số worker | `ExecutorService` | Giới hạn concurrency, queue và shutdown |
| Một task thất bại nhưng cần biết nguyên nhân | `Future.get()`, quản lý lỗi task | Tránh suy ra thành công chỉ từ `join()` |
| Java 21+, nhiều tác vụ chờ I/O độc lập | Cân nhắc virtual thread per task | Vẫn chỉ start từng virtual thread một lần |

## 6. Nếu cố gọi start hai lần từ nhiều thread thì sao?

Một lỗi thực tế là có hai nơi cùng muốn khởi chạy một worker:

~~~java
// Minh họa không an toàn: hai caller cùng thao tác trên một đối tượng.
if (worker.getState() == Thread.State.NEW) {
    worker.start();
}
~~~

Ngay cả khi cả hai caller đều thấy trạng thái `NEW`, điều kiện `if` và lời gọi `start()` không tạo thành một phép kiểm tra–hành động nguyên tử. Caller thứ nhất có thể khởi chạy; caller thứ hai tiếp tục tới `start()` và nhận `IllegalThreadStateException`.

Các kiểm tra `!worker.isAlive()` cũng không xác định “có thể start” vì `isAlive() == false` có thể xảy ra khi thread **chưa từng start** hoặc **đã kết thúc**. Tương tự, `getState() == TERMINATED` chứng minh đã kết thúc nhưng **không** có nghĩa sẵn sàng chạy lại.

Thiết kế an toàn hơn:

1. Xác định thành phần **sở hữu** quyền tạo/khởi chạy thread (ví dụ đối tượng quản lý worker).
2. Đảm bảo quy trình khởi chạy được gọi tại một điểm được kiểm soát; không để nhiều caller tự kiểm tra trạng thái rồi cùng `start()`.
3. Nếu có yêu cầu “start nếu chưa start”, dùng đồng bộ hóa/`AtomicBoolean` để điều phối **quyền gọi start một lần**; giải pháp này không làm đối tượng thread trở nên có thể restart.
4. Nếu nghiệp vụ đòi hỏi “bấm Start nhiều lần sau mỗi lần Stop”, mỗi chu kỳ phải tạo **đối tượng thread mới**, khởi tạo lại trạng thái của job đúng cách và quản lý việc chờ chu kỳ cũ kết thúc.

Ví dụ mô hình vòng đời của một worker có thể nhận nhiều phiên chạy: một controller giữ trạng thái “đang chạy/đã dừng”, mỗi lần khởi động **tạo một Thread mới**, và khi dừng thì gửi tín hiệu hợp tác (như interrupt hoặc cờ dừng có đồng bộ) rồi đợi hoàn tất. Không dùng `Thread.stop()`: API này đã bị deprecated và vốn không an toàn vì có thể dừng thread tại điểm đang giữ bất biến/khóa.

## 7. Vì sao thread kết thúc không thể hồi sinh?

Một đối tượng `Thread` đã start gắn với một phiên thực thi, bao gồm ngăn xếp lời gọi, trạng thái tiến triển, thông tin lập lịch và quá trình hoàn tất. Java không định nghĩa một cách hợp lệ để tái tạo lại phiên thực thi ấy bằng cách gọi `start()` lần nữa.

Đừng nhầm việc **giữ lại đối tượng `Thread` trong một biến** với việc giữ lại năng lực thực thi của thread:

~~~java
// Minh họa: reference vẫn tồn tại nhưng phiên thực thi đã kết thúc.
Thread t = new Thread(() -> System.out.println("done"));
t.start();
t.join();

// t vẫn được tham chiếu, nhưng không thể t.start() lần nữa.
~~~

Tham chiếu `t` vẫn có ích cho việc quan sát tên, ID, trạng thái cuối, hoặc xác định thread đã hoàn tất. Nó không giữ một “nút chạy lại”. Mỗi lần chạy lại logic nhiệm vụ là một phiên thực thi mới.

Đặc biệt, nếu `run()` kết thúc do exception không được bắt, thread vẫn chấm dứt và vẫn không thể start lại. Cài `UncaughtExceptionHandler` chỉ giúp nhận biết/báo lỗi; handler **không** chuyển thread về `NEW`, không tự động sửa lỗi, không khởi chạy lại cùng đối tượng.

## 8. Thứ tự bộ nhớ, trạng thái và quan sát

Quy tắc một lần của `start()` còn liên hệ tới quan hệ **happens-before** trong Java Memory Model:

- Những hành động của một thread thực hiện **trước lời gọi `otherThread.start()`** happens-before các hành động do thread mới đó thực hiện.
- Khi một thread khác chờ `join()` hoàn tất thành công, những hành động của thread đã kết thúc happens-before các hành động tiếp theo của thread đã join. Nhờ vậy, sau khi `join()` bình thường, các cập nhật phù hợp từ worker đã được công bố để thread chờ quan sát.

Những bảo đảm đó giúp phối hợp giữa các phiên thực thi, nhưng **không** làm cho tất cả trạng thái chia sẻ trở thành thread-safe trong lúc nhiều thread đang chạy. Nếu hai worker cùng cập nhật một biến mutable, vẫn cần thiết kế đồng bộ phù hợp (khóa, atomic, bất biến, phân quyền sở hữu dữ liệu).

`getState()`, `isAlive()` và tên thread hữu ích cho debugging và kiểm tra vòng đời, nhưng không nên được dùng làm giao thức đồng bộ duy nhất. Trạng thái có thể thay đổi ngay sau khi quan sát. Với sản phẩm thực tế, một cấu trúc quản lý task/worker thường cần giao thức khởi động, hoàn thành, dừng và xử lý lỗi rõ ràng.

## 9. Phân biệt những lỗi liên quan

| Hành động | Điều xảy ra | Cách xử lý đúng |
| --- | --- | --- |
| Gọi `start()` lần đầu trên Thread `NEW` | Khởi động phiên thực thi | Chờ hoặc phối hợp với task khi cần |
| Gọi `start()` lần hai trên cùng Thread | `IllegalThreadStateException` | Tạo Thread mới hoặc dùng executor |
| Gọi `run()` trực tiếp trên đối tượng Thread | Chạy đồng bộ trong **caller** | Dùng `start()` nếu cần luồng mới |
| Gọi `start()` trên Thread đã `TERMINATED` | `IllegalThreadStateException` | Không reset thread; tạo phiên mới |
| Gọi `join()` nhiều lần sau khi kết thúc | Trả về, không khởi chạy lại | Dùng `join()` để chờ, không để restart |
| Gửi task vào executor đã shutdown | Thường `RejectedExecutionException` | Tạo hoặc giữ executor đang hoạt động |
| Gọi `Thread.start()` từ hai caller cạnh tranh | Chỉ một lần start hợp lệ; caller khác có thể nhận lỗi | Điều phối ownership và thao tác khởi động |
| Worker bị interrupt | Tín hiệu phối hợp; thread có thể tiếp tục hoặc kết thúc tùy code | Xử lý interrupt đúng hợp đồng, không xem là reset |
| Worker chết vì uncaught exception | Thread chấm dứt, không thể restart cùng đối tượng | Ghi lỗi, quyết định retry bằng task/thread mới |

### Lỗi thường gặp khi chữa `IllegalThreadStateException`

- **Đổi `start()` thành `run()`** để hết lỗi: code có thể chạy được nhưng sẽ chạy trong thread gọi, dễ làm mất tính đồng thời hoặc chặn đường xử lý chính.
- **Gọi `join()` trước lần start thứ hai**: `join()` không thay đổi quy tắc một lần.
- **Chỉ kiểm tra `isAlive()` trước khi start**: `false` không phân biệt `NEW` với `TERMINATED`.
- **Giữ một Thread trong field và gọi start mỗi khi nhận request**: mỗi request sau request đầu có nguy cơ ném lỗi; nên giữ task factory hoặc executor.
- **Tự retry `start()` trên cùng đối tượng trong vòng lặp catch**: ngoại lệ sẽ lặp lại vì đối tượng đã được start trước đó.
- **Quên kết thúc executor**: chuyển sang executor mà không có shutdown, giới hạn tài nguyên hay xử lý lỗi chỉ thay thế một lỗi vòng đời bằng vấn đề quản lý worker khác.

## 10. Java 21+ và virtual threads

Trong Java 21, virtual threads được chuẩn hóa thành tính năng chính thức. Dù rẻ hơn platform threads trong nhiều trường hợp chờ I/O, một virtual thread **vẫn là một `Thread` và vẫn chỉ start một lần**.

Ví dụ API Java 21+ (minh họa, không biên dịch trên JDK 17):

~~~java
Thread virtual = Thread.ofVirtual()
    .name("fetch-1")
    .unstarted(() -> System.out.println("fetching"));

virtual.start();
virtual.join();

// virtual.start(); // Sai: IllegalThreadStateException.
~~~

Nếu muốn thực hiện nhiều task độc lập, thông thường nên tạo virtual thread mới cho mỗi task, hoặc dùng cơ chế `Executors.newVirtualThreadPerTaskExecutor()` để giao việc; không tái sử dụng đối tượng virtual thread đã kết thúc.

Quy tắc này hoàn toàn thuộc Thread API của Java; không cần một framework nào để áp dụng.

## 11. Bài tập tự kiểm tra

**Bài 1 — nhận diện lỗi:**

~~~java
// Minh họa, không phải chương trình độc lập.
Thread t = new Thread(() -> System.out.println("hello"));
t.start();
t.join();
t.start();
~~~

Câu hỏi: lỗi xuất hiện ở đâu? **Đáp án:** ở `t.start()` thứ hai với `IllegalThreadStateException`; `join()` không đặt lại trạng thái.

**Bài 2 — lý giải trạng thái:** Một đối tượng `Thread` chưa từng được `start()` nhưng đã được gọi `run()` trực tiếp hai lần. Lúc này `getState()` của đối tượng đó là gì nếu không có thread nào khác gọi `start()`? **Đáp án:** `NEW`; hai lời gọi `run()` không tạo thread mới.

**Bài 3 — thiết kế retry:** Một tác vụ gửi dữ liệu thất bại và cần thử lại nếu an toàn về nghiệp vụ. Có nên giữ `Thread` cũ rồi `start()` lần nữa? **Đáp án:** không. Phải quyết định retry theo hợp đồng của tác vụ (idempotency, giới hạn lần thử, backoff, xử lý side effect), sau đó gửi **task** vào executor hoặc tạo **thread mới**. Lỗi nghiệp vụ và vòng đời thread là hai vấn đề riêng.

**Bài 4 — đọc mã:** Một ứng dụng kiểm tra `!thread.isAlive()` trước khi `start()`. Điều kiện đó có đủ bảo đảm không ném `IllegalThreadStateException`? **Đáp án:** không. `isAlive()` có thể `false` khi thread chưa start *hoặc* đã chấm dứt, và phép kiểm tra có thể bị race.

## 12. Checklist khi triển khai

- Phân biệt đúng **task có thể thực thi lại** và **Thread object chỉ được start một lần**.
- Chỉ gọi `start()` cho một Thread `NEW`, tại nơi sở hữu trách nhiệm khởi động.
- Dùng `join()` hoặc cơ chế phối hợp khác khi cần biết task đã kết thúc; quản lý lỗi qua kênh kết quả phù hợp.
- Không dùng `run()` như cách sửa lỗi `start()` lần hai.
- Muốn chạy lại, tạo một đối tượng Thread mới hoặc gửi task mới cho executor còn hoạt động.
- Không dựa riêng vào `getState()` hoặc `isAlive()` để điều phối nhiều caller.
- Thiết kế quản lý dừng, interrupt, thất bại và retry theo logic nghiệp vụ; không dùng `Thread.stop()`.
- Trong ứng dụng thực, đo concurrency, giới hạn tài nguyên và đóng executor đúng cách.

**Ghi nhớ:** Một `Thread` không phải đối tượng “chạy lại được”. `start()` chỉ hợp lệ một lần trong suốt vòng đời của **chính đối tượng đó**. Muốn lặp lại công việc, hãy quản lý và thực thi lại **task** qua một phiên thread mới hoặc qua executor.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-lifecycle-009">Chờ thread hoàn tất mà không busy waiting</a>

<details>
<summary>Xem chi tiết</summary>

# Chờ thread hoàn tất mà không busy waiting trong Java

Khi một thread khởi động công việc ở thread khác, đôi khi thread gọi phải **đợi kết quả hoặc đợi công việc kết thúc** trước khi thực hiện bước tiếp theo. Cách thường thấy ở người mới là liên tục kiểm tra `isAlive()`, một biến cờ, hoặc trạng thái tác vụ trong vòng lặp. Kiểu chờ đó có thể tiêu tốn CPU, khó kiểm soát độ trễ và dễ mắc lỗi đồng bộ.

Java cung cấp các cơ chế **chờ có phối hợp (blocking wait)** như `Thread.join()`, `CountDownLatch.await()`, `Future.get()` và `ExecutorService.awaitTermination()`. Chúng cho phép thread đang đợi nhường quyền thực thi trong lúc điều kiện chưa đạt, thay vì liên tục chạy vòng lặp thăm dò.

Bài này nằm trong **vòng đời Thread của Java Concurrency nói chung**. Trọng tâm là biết **đợi một thread kết thúc đúng cách**, hiểu visibility, timeout, interruption và biết khi nào nên dùng một primitive khác. Các cơ chế đặc thù của Spring nằm ngoài phạm vi bài.

## 1. Busy waiting là gì và vì sao cần tránh?

*Busy waiting* (spin waiting) là khi một thread liên tục thực hiện phép kiểm tra trong lúc đợi điều kiện thay đổi, thường không nhường CPU một cách có chủ đích:

```java
Thread worker = new Thread(() -> doWork());
worker.start();

while (worker.isAlive()) {
    // Liên tục hỏi trạng thái: không làm việc hữu ích.
}
consumeResult();
```

Đây là **mã minh họa**, giả định `doWork()` và `consumeResult()` tồn tại. Vòng lặp có thể chạy hàng triệu lần trong thời gian worker làm việc. `Thread.isAlive()` cho biết thread đã được start và chưa kết thúc; nó không phải API chờ tối ưu.

Các hệ quả:

- **Tốn CPU:** thread đợi vẫn chạy, cạnh tranh thời gian xử lý với worker và các task khác.
- **Tốn năng lượng:** đặc biệt bất lợi khi chờ I/O, công việc dài hoặc chờ nhiều thread.
- **Dễ tạo độ trễ khó đoán:** `Thread.yield()` không bảo đảm nhường CPU; nhét `Thread.sleep(10)` vào vòng lặp chỉ biến nó thành *polling có khoảng nghỉ*, có thể thêm gần một chu kỳ đợi và vẫn cần xử lý cờ/interruption.
- **Khó xác định đúng điều kiện kết thúc:** một biến boolean bình thường được chia sẻ giữa thread có thể thiếu quan hệ *happens-before*, dẫn đến lỗi visibility. Dùng `volatile` làm cờ có thể giải quyết một phần visibility, nhưng không khiến việc polling trở thành lựa chọn phù hợp cho thời gian chờ dài.
- **Khó xử lý lỗi:** nếu worker ném exception và kết thúc, vòng lặp có thể dừng nhưng không cho biết công việc thành công hay thất bại.

Với nhu cầu “đợi thread A kết thúc”, thay đoạn `while (worker.isAlive())` bằng:

```java
worker.join();
```

`join()` có thể block **thread đang gọi**, không block worker. Khi worker kết thúc, lời gọi hoàn thành. Thread gọi phải sẵn sàng xử lý `InterruptedException` trong lúc chờ.

> Có những thuật toán đồng bộ cấp thấp cố ý dùng spin rất ngắn khi xác suất đợi thấp và chi phí chuyển ngữ cảnh lớn. `Thread.onSpinWait()` (Java 9+) là một gợi ý hiệu năng cho trường hợp đó, **không** thay thế `join()` khi công việc có thể kéo dài. Bài này chọn blocking wait cho việc theo dõi hoàn tất thread thông thường.

## 2. `Thread.join()`: đợi đúng sự kiện thread kết thúc

`join()` là instance method của `Thread`. Khi thread `main` gọi `worker.join()`, main sẽ đợi đến khi worker hoàn thành. Quan hệ cơ bản:

```text
Thread main                                Thread worker
    |                                           |
    |-- worker.start() ------------------------>|
    |                                           | run() thực thi
    |-- worker.join() ---- đợi ..................|
    |                                           | run() kết thúc
    |<-------------- join() trả về -------------|
    | main xử lý kết quả                         |
```

Các overload thường dùng:

| Lời gọi | Ý nghĩa | Giá trị trả về |
| --- | --- | --- |
| `worker.join()` | Đợi đến khi worker kết thúc, trừ khi thread gọi bị interrupt. | `void` |
| `worker.join(long millis)` | Đợi tối đa khoảng thời gian tính bằng millisecond; **`0` nghĩa là chờ không giới hạn**. | `void` |
| `worker.join(long millis, int nanos)` | Đợi với tham số millisecond/nanosecond hợp lệ theo hợp đồng API; có điều chỉnh độ chính xác theo implementation. | `void` |

Các overload trên có thể ném `InterruptedException` nếu **thread đang đợi** bị interrupt. Tham số thời gian không hợp lệ có thể gây `IllegalArgumentException`. Với phiên bản có timeout, việc trả về **không tự nói** thread đã hoàn thành hay hết thời gian. Hãy kiểm tra `worker.isAlive()` nếu cần phân biệt.

**Thứ tự sử dụng thông thường:**

1. Tạo `Thread` với đúng `Runnable`.
2. Gọi `start()` để khởi chạy thread (chỉ một lần).
3. Khi logic thực sự cần đợi, gọi `join()` hoặc `join(timeout)`.
4. Sau khi xác nhận thread đã kết thúc, đọc trạng thái/kết quả đã được worker công bố và xử lý lỗi theo cơ chế đã chọn.

Không gọi `worker.run()` để “chạy thread”: đó chỉ là lời gọi phương thức bình thường trên thread hiện tại. Không gọi `join()` trước khi `start()` rồi coi đó là đã đợi tác vụ: thread chưa từng start không có gì để chờ, nên `join()` có thể trả ngay.

## 3. Ví dụ Java 17+: đợi kết quả bằng `join()`

Tạo tệp `JoinResultDemo.java`:

```java
public final class JoinResultDemo {
    public static void main(String[] args) throws InterruptedException {
        int[] result = {0};

        Thread worker = new Thread(() -> {
            int sum = 0;
            for (int i = 1; i <= 5; i++) {
                sum += i;
            }
            result[0] = sum;
        }, "sum-worker");

        worker.start();
        worker.join();

        System.out.println("Thread finished: " + !worker.isAlive());
        System.out.println("Sum: " + result[0]);
    }
}
```

Chạy:

```bash
javac JoinResultDemo.java
java JoinResultDemo
```

Stdout chính xác:

```text
Thread finished: true
Sum: 15
```

**Phân tích:**

- `worker.start()` bắt đầu thực thi `Runnable` trên thread mới.
- Main gọi `join()` và chờ đến khi worker kết thúc. `join()` không yêu cầu vòng lặp kiểm tra thủ công.
- Worker ghi `result[0] = 15`. Sau khi `join()` hoàn thành vì worker đã kết thúc, main có thể đọc kết quả đó.
- `!worker.isAlive()` là `true` vì thread đã kết thúc và không thể start lần nữa.

`int[] result` chỉ dùng để minh họa **công bố kết quả sau khi join**. Nó không phải cấu trúc thread-safe để nhiều worker cùng cập nhật tùy ý. Nếu có nhiều worker ghi chung một kết quả, cần thêm quy tắc phối hợp, atomic operation, hoặc dùng `Future`/kiểu kết quả phù hợp.

Việc khai báo `main throws InterruptedException` giúp ví dụ ngắn và minh bạch: nếu main bị interrupt khi đang chờ, exception được truyền ra caller. Trong ứng dụng thực tế, luồng điều khiển cần có chính sách hủy và dọn dẹp rõ ràng.

## 4. Vì sao đọc kết quả sau `join()` là an toàn về visibility?

Java Memory Model cung cấp quan hệ *happens-before* liên quan đến vòng đời thread:

- Những hành động xảy ra trước `worker.start()` trong thread gọi **happens-before** các hành động do worker thực hiện sau khi được start.
- Tất cả hành động của worker **happens-before** việc thread khác phát hiện worker đã kết thúc thành công, chẳng hạn qua `worker.join()` hoặc `worker.isAlive()` trả `false`.

Vì thế trong ví dụ:

```text
worker ghi result[0] = 15
              |
              | worker kết thúc
              v
main gọi join() và nhận biết kết thúc
              |
              v
main đọc result[0] == 15
```

Nếu chỉ dùng `Thread.sleep(100)` trên main, **không có** bảo đảm rằng worker đã hoàn tất hoặc rằng lần đọc dựa trên một quan hệ đồng bộ đủ rõ. Máy nhanh/chậm, độ dài công việc và lịch chạy đều có thể thay đổi. Sleep là tạm dừng một khoảng thời gian; join là đồng bộ theo **sự kiện kết thúc**.

**Lưu ý quan trọng:** `join(timeout)` trả về vì hết thời gian khi worker vẫn sống **không** cung cấp bảo đảm “mọi phép ghi của worker đã xuất hiện”. Nếu sau đó `isAlive()` trả về `false`, bạn đã nhận biết thread kết thúc, và quan hệ happens-before của việc kết thúc được thiết lập. Nếu worker còn sống, không được giả định rằng tất cả kết quả đã sẵn sàng; muốn trao đổi kết quả từng phần cần synchronization, `volatile`, lock hoặc cơ chế concurrent phù hợp.

Happens-before bảo đảm **thứ tự quan sát và visibility theo mô hình bộ nhớ**, không bảo đảm thuật toán nghiệp vụ có logic đúng. Ví dụ worker hoàn thành phép tính sai thì join chỉ giúp main quan sát *kết quả sai ấy* một cách nhất quán.

## 5. Timed join: chờ có giới hạn nhưng vẫn kiểm tra kết thúc

Một tác vụ có thể treo do I/O, deadlock hoặc chờ sự kiện không đến. `join(timeout)` giúp thread gọi ngừng **chờ thêm** sau khoảng thời gian quy định, nhưng **không tự dừng worker**.

Ví dụ muốn có kết quả xác định mà không phụ thuộc `sleep()`: dùng `CountDownLatch` giữ worker ở trạng thái chưa kết thúc trong thời gian thử timed join.

Tạo tệp `TimedJoinDemo.java`:

```java
import java.util.concurrent.CountDownLatch;

public final class TimedJoinDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch finishGate = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            try {
                finishGate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "gated-worker");

        worker.start();
        try {
            worker.join(100);
            System.out.println("Still alive after timeout: "
                    + worker.isAlive());
        } finally {
            finishGate.countDown();
        }

        worker.join();
        System.out.println("Finished after gate opened: "
                + !worker.isAlive());
    }
}
```

Biên dịch/chạy bằng Java 17 trở lên:

```bash
javac TimedJoinDemo.java
java TimedJoinDemo
```

Stdout trong điều kiện chương trình được thực thi bình thường, không có external interruption:

```text
Still alive after timeout: true
Finished after gate opened: true
```

**Tại sao không bị phụ thuộc lịch ngủ của worker?** Worker không thể kết thúc trước khi `finishGate.countDown()` (trừ trường hợp bị external interruption), vì đang chờ latch. Main chỉ mở cổng trong `finally`, sau khi thử `join(100)`. Lần `join()` thứ hai đợi công việc thực sự kết thúc. Không sử dụng vòng lặp busy waiting.

**Cẩn thận với timeout:**

- `join(100)` có đơn vị millisecond và chỉ đưa ra giới hạn thời gian đợi theo hợp đồng API; thời gian tường đo được còn chịu độ trễ scheduling. Đừng diễn giải như một deadline thời gian thực tuyệt đối.
- `join(0)` là chờ vô hạn, **không** có nghĩa là “không chờ”.
- Sau `join(timeout)`, `worker.isAlive()` giúp xác định hiện tại worker còn sống hay không. Một lần kiểm tra `true` có thể trở nên lỗi thời ngay sau đó khi worker hoàn tất; nếu cần kết quả hoàn thành cuối cùng, vẫn phải phối hợp/chờ tiếp.
- Thời gian hết hạn không làm worker chết. Tự quyết định tiếp tục chạy background, yêu cầu hủy bằng interruption, ghi trạng thái “timeout”, hoặc từ bỏ kết quả.
- Nếu main bị interrupt trong timed join, `InterruptedException` được ném; `finally` vẫn mở cổng, tránh giữ worker mãi mãi ở latch do nhánh xử lý lỗi.

Ở các tác vụ không hợp tác với interruption, gọi `worker.interrupt()` cũng **không** bảo đảm dừng tức thì. Đừng dùng `Thread.stop()`; phương thức này không an toàn và đã bị loại khỏi những cách dừng thread được khuyến nghị.

## 6. Interruption: ai bị interrupt khi đang `join()`?

`join()` ném `InterruptedException` khi **thread đang thực hiện join** bị interrupt trong quá trình chờ. Ví dụ thread A gọi `worker.join()`; thread B interrupt A. Kết quả là A rời khỏi lời gọi chờ với exception; worker **không tự bị hủy** chỉ vì A bị interrupt.

Khi `InterruptedException` được ném từ các blocking method thường dùng, **interrupted flag của thread bắt exception bị xóa**. Bạn cần chọn chính sách:

- Ở tầng có thể khai báo `throws InterruptedException`, thường **truyền exception lên trên** để caller xử lý cancellation.
- Ở `Runnable.run()` không khai báo checked exception, thường bắt exception, gọi `Thread.currentThread().interrupt()` để **phục hồi cờ interruption**, rồi thoát tác vụ hoặc làm cleanup.
- Nếu đã được yêu cầu hủy cả worker, quyết định gọi `worker.interrupt()` theo hợp đồng task; không mặc nhiên cho rằng interrupt đồng nghĩa kill.

Ví dụ mảnh mã xử lý trong `Runnable`:

```java
try {
    worker.join();
} catch (InterruptedException e) {
    worker.interrupt();                     // Chỉ khi muốn yêu cầu worker hủy.
    Thread.currentThread().interrupt();     // Phục hồi cờ ở thread đang chờ.
    return;                                 // Rời khỏi task đang chạy.
}
```

Đây là mảnh mã **không chạy độc lập**, giả định `worker` đã khai báo, đang được start và `return` nằm trong phương thức `void`. Lỗi phổ biến là bắt `InterruptedException` rồi tiếp tục một vòng lặp join vô hạn, khiến cancellation không còn hiệu lực.

Nếu ứng dụng cần **đợi worker dọn dẹp sau khi bị hủy**, hãy đưa ra deadline độc lập và quy trình ownership rõ ràng. Không cố chờ vô hạn trong thread đã bị interrupt chỉ để “bảo đảm” tác vụ hoàn tất; worker có thể kẹt hoặc không hợp tác.

## 7. `join()` khác `sleep()`, `wait()` và `yield()`

| API | Đợi điều gì? | Có phải thay thế trực tiếp cho `join()`? |
| --- | --- | --- |
| `Thread.join()` | Một thread cụ thể kết thúc. | Có — là API trực tiếp cho nhu cầu này. |
| `Thread.sleep(duration)` | Khoảng thời gian trôi qua. | **Không**: không biết worker đã kết thúc hay chưa. |
| `Object.wait()` | Notification hoặc sự kiện chờ/timeout trên monitor, kiểm tra predicate bằng giao thức `synchronized`. | Chỉ phù hợp nếu đang tự thiết kế điều kiện chia sẻ; dễ sai hơn `join()` cho nhu cầu chờ kết thúc thread. |
| `Thread.yield()` | Gợi ý scheduler cho thread khác cơ hội chạy. | **Không**: không có bảo đảm nhường CPU, kết thúc task hay visibility. |
| `Thread.onSpinWait()` | Gợi ý tối ưu processor cho vòng spin. | **Không** với tác vụ dài; vẫn là busy waiting nếu gọi trong vòng poll. |
| `LockSupport.park()` | Đợi permit / unparking hoặc interruption/spurious return. | Primitive cấp thấp, cần protocol riêng; không nên thay `join()` khi chỉ chờ thread kết thúc. |

`Object.wait()` khác `join()` về điểm gắn với **điều kiện** và monitor. Nó phải được gọi khi đang giữ monitor tương ứng; thường cần vòng `while (!predicate) wait()` để chịu được *spurious wakeup*. Dùng `wait()/notify()` để tự viết lại cơ chế chờ thread kết thúc có thể mắc lost notification nếu thiết kế sai.

`Thread.join()` trong một số JDK có thể được hiện thực dựa trên wait/notification nội bộ của đối tượng Thread. Đó là **chi tiết triển khai**; code của bạn nên dựa vào hợp đồng công khai `join()`, không tự gọi `notify()` trên đối tượng `Thread` để mong tác vụ kết thúc nhanh hơn.

## 8. Đợi nhiều thread: join từng thread hay CountDownLatch?

Nếu phải đợi một nhóm thread kết thúc, có thể gọi join từng thread:

```java
for (Thread worker : workers) {
    worker.join();
}
```

Đây là mảnh mã giả định `workers` là collection các thread đã start. Các worker vẫn **chạy song song**; join tuần tự chỉ quyết định thứ tự mà main chờ nhận completion. Nếu worker cuối kết thúc trước worker đầu thì main vẫn đợi worker đầu trước, sau đó các join khác có thể trả ngay.

Cần phân biệt hai điều:

- **Tất cả thread đã kết thúc**: join tất cả thread đúng mục tiêu.
- **Một số thao tác logic đã hoàn tất** nhưng thread vẫn tồn tại/chờ tác vụ khác: dùng tín hiệu completion (latch, Future, queue, condition) phù hợp hơn.

### 8.1 CountDownLatch cho nhiều công việc hoàn tất

`CountDownLatch` được khởi tạo với số sự kiện cần chờ. Mỗi công việc gọi `countDown()` khi đã làm xong; phía đợi gọi `await()`. Thread đang đợi **không** cần polling và không bận CPU. Latch là cơ chế **một lần**; bộ đếm giảm đến 0 và không reset để dùng vòng khác.

Tạo tệp `CompletionLatchDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class CompletionLatchDemo {
    public static void main(String[] args) throws InterruptedException {
        int taskCount = 3;
        CountDownLatch completed = new CountDownLatch(taskCount);
        AtomicInteger successful = new AtomicInteger();

        Thread[] threads = new Thread[taskCount];
        for (int i = 0; i < taskCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    successful.incrementAndGet();
                } finally {
                    completed.countDown();
                }
            }, "task-" + i);
            threads[i].start();
        }

        completed.await();
        System.out.println("Completed signals: " + taskCount);
        System.out.println("Successful tasks: " + successful.get());

        // Nếu cần bảo đảm chính các Thread đã TERMINATED,
        // có thể join từng thread sau khi nhận tín hiệu hoàn thành.
        for (Thread thread : threads) {
            thread.join();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac CompletionLatchDemo.java
java CompletionLatchDemo
```

Stdout:

```text
Completed signals: 3
Successful tasks: 3
```

**Vì sao dùng `finally`?** Nếu phần thân có exception, latch vẫn giảm, tránh main chờ vô hạn do thiếu tín hiệu. Tuy nhiên `await()` hoàn tất **không chứng minh mọi task thành công**; hãy lưu và đọc lỗi riêng. Trong chương trình này, thao tác tăng một atomic integer không có exception dự kiến, nên `Successful tasks: 3` có thể dự đoán.

Lời gọi `countDown()` diễn ra ngay trước phần cuối của `run()`. `await()` trả về khi bộ đếm đạt 0, nhưng một thread có thể **chưa chuyển hoàn toàn sang TERMINATED** tại thời điểm đó; nếu thực sự phải đợi kết thúc vòng đời thread, join như ví dụ.

Theo Java Memory Model, các hành động trong thread trước `countDown()` happen-before những hành động sau khi `await()` tương ứng trả về thành công. Vì thế main có thể đọc những cập nhật được công bố trước `countDown()` theo đúng quan hệ đồng bộ. Trong ví dụ, `AtomicInteger` còn cho phép tăng đồng thời chính xác.

### 8.2 Timed await

```java
boolean finished = completed.await(2, TimeUnit.SECONDS);
if (!finished) {
    // Hết thời gian chờ; xác định chính sách retry/hủy/báo timeout.
}
```

Đây là mảnh mã minh họa, giả định `completed` và `TimeUnit` đã có. Khác `Thread.join(timeout)` trả `void`, `CountDownLatch.await(timeout, unit)` trả `boolean`: `true` nếu bộ đếm đạt 0, `false` nếu hết hạn. `await()` cũng có thể bị interrupt.

**Không** gọi `countDown()` thay cho việc thực sự hoàn tất công việc. Nếu gọi trước khi ghi kết quả hoặc đóng tài nguyên, bên đợi có thể tiếp tục quá sớm. Ngược lại, nếu một task không bao giờ được start, bộ đếm bị thiếu tín hiệu; phải thiết kế lỗi submit/start và đường cancellation.

## 9. Chờ kết quả: Future thay vì quản lý Thread trực tiếp

Trong phần lớn chương trình xử lý nhiều task, bạn cần **giá trị trả về và exception** của công việc, không cần tên/đối tượng Thread của worker. `ExecutorService.submit()` trả `Future`; `Future.get()` đợi completion **mà không busy waiting** và có cơ chế báo lỗi.

Tạo tệp `FutureCompletionDemo.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class FutureCompletionDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> answer = executor.submit(() -> 6 * 7);

            int value = answer.get();
            System.out.println("Answer: " + value);
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac FutureCompletionDemo.java
java FutureCompletionDemo
```

Stdout:

```text
Answer: 42
```

Sự khác nhau cần nắm:

- `Thread.join()` cho biết **thread đã kết thúc**. Nó không trực tiếp trả giá trị, cũng không tự ném lại lỗi mà worker gặp trong `run()`.
- `Future.get()` cho biết **task đã hoàn tất**, trả kết quả hoặc ném `ExecutionException` chứa nguyên nhân lỗi; task có thể hoàn tất trên worker tiếp tục sống và chạy task khác.
- `Future.get(timeout, unit)` có thể ném `TimeoutException`. Hết hạn đợi không tự hủy task; nếu cần hủy, cân nhắc `future.cancel(true)` với chính sách interruption hợp tác.
- `Future.get()` có thể ném `InterruptedException` nếu thread đang đợi bị interrupt và `CancellationException` nếu task bị hủy. Bắt lỗi thích hợp khi nhiệm vụ thật cần phân biệt trạng thái.

Trong ví dụ, `awaitTermination(5, ...) ` chờ **executor kết thúc tất cả worker sau `shutdown()`**, không phải một task bất kỳ. Giá trị `boolean` của `awaitTermination` cho biết executor đã chấm dứt hay hết thời hạn. `shutdownNow()` cố gắng interrupt công việc đang chạy; nó không buộc task không hợp tác dừng ngay.

### 9.1 CompletableFuture và tên `join()` khác nhau

`CompletableFuture<T>.join()` **không phải** `Thread.join()`:

```java
CompletableFuture<Integer> value =
        CompletableFuture.supplyAsync(() -> 21 * 2);
System.out.println(value.join());
```

Mảnh mã này cần import `java.util.concurrent.CompletableFuture`. `CompletableFuture.join()` đợi **completion của future**, trả `T`, và khi tác vụ hoàn thành lỗi sẽ ném unchecked `CompletionException` (hoặc trường hợp hủy là `CancellationException`); nó không khai báo checked `InterruptedException`. Đừng nhầm việc không khai báo checked interruption với cơ chế hủy task hay deadline tự động.

Nếu phải đợi nhiều `CompletableFuture`, `CompletableFuture.allOf(...)` tạo một future hoàn thành khi tất cả future nguồn hoàn thành. Cần kiểm tra lỗi/kết quả của từng future theo mục tiêu nghiệp vụ; không mặc định “allOf hoàn thành” nghĩa mọi công việc thành công.

## 10. Đợi kết thúc toàn bộ executor

Khi đã gọi `shutdown()`, executor ngừng nhận thêm task, song những task đã nhận có thể vẫn hoạt động. Dùng `awaitTermination()` để đợi trạng thái **terminated** của executor:

```java
executor.shutdown();
try {
    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        executor.shutdownNow();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            System.err.println("Executor did not terminate");
        }
    }
} catch (InterruptedException e) {
    executor.shutdownNow();
    Thread.currentThread().interrupt();
}
```

Mảnh minh họa giả định `executor` là `ExecutorService` và đã import `TimeUnit`. Nó **không bảo đảm** rằng task bị mắc kẹt sẽ dừng: cancellation/interruption là cơ chế hợp tác. Bài toán shutdown còn phải xét worker đang thực hiện I/O không có timeout, giữ lock hoặc chờ task khác.

**Không** gọi `awaitTermination()` trước `shutdown()` rồi mong executor tự dừng: pool vẫn có thể tiếp tục nhận việc và duy trì worker. `Thread.join()` không thay thế quy trình shutdown của executor; worker thread có thể sống lâu hoặc được thay thế, còn lifecycle thuộc executor.

Nếu đang quản lý một vài `Thread` trực tiếp (không dùng executor), bạn tự chịu trách nhiệm yêu cầu chúng kết thúc, truyền tín hiệu hủy phù hợp và join các thread cần thiết.

## 11. Khi chờ làm chương trình bị kẹt

`join()` không tốn CPU theo kiểu polling, nhưng nó **vẫn là blocking** và có thể chờ vô hạn nếu thiết kế phụ thuộc sai.

### 11.1 Chờ chính mình

```java
Thread.currentThread().join();
```

Thread đang chạy không thể tự kết thúc trước khi `join()` trả về, nên nếu không bị interrupt, nó chờ vô hạn. Không gọi join trên current thread.

### 11.2 Vòng phụ thuộc giữa hai thread

Thread A join B, trong khi B lại join A: cả hai có thể chờ mãi nếu không có timeout/interruption. Lỗi này là một dạng deadlock ở mức phụ thuộc completion, không nhất thiết được bộ phát hiện deadlock monitor ghi nhận.

### 11.3 Gọi join khi đang giữ lock mà worker cần

Đây là **mã lỗi minh họa**:

```java
Object lock = new Object();
Thread worker = new Thread(() -> {
    synchronized (lock) {
        // Làm việc cần lock.
    }
});

synchronized (lock) {
    worker.start();
    worker.join(); // main giữ lock, worker không vào được synchronized.
}
```

Giả định `InterruptedException` đã được khai báo/xử lý ở method chứa đoạn này. Main giữ `lock` khi đợi kết thúc; worker cần chính `lock` để hoàn tất. Nếu không có interruption ngoài, cả hai kẹt. `join()` **không giải phóng mọi lock mà thread đang đợi đã giữ**. Cách sửa là không join trong critical section:

```java
Thread worker = new Thread(() -> {
    synchronized (lock) {
        // Làm việc ngắn với dữ liệu chia sẻ.
    }
});

worker.start();
worker.join(); // Không giữ lock cần cho worker.
```

Mảnh sửa giả định `lock` đã khai báo và method xử lý `InterruptedException`. Nếu buộc phải giữ lock để kiểm tra trạng thái, hãy thiết kế protocol với `Condition`/thông báo hoàn tất phù hợp, thay vì tạo vòng chờ lock.

### 11.4 Pool starvation

Một task chạy trong pool nhỏ gọi `future.get()` hoặc `join()` để đợi task khác **cũng đang nằm trong queue của chính pool**, trong khi không còn worker rảnh để chạy task đó. Kết quả là mọi worker bận chờ, còn tác vụ được chờ không bao giờ bắt đầu. Giải pháp nằm ở đồ thị phụ thuộc task, executor capacity, tránh blocking tùy tiện trong worker và cấu trúc phối hợp, không phải đổi một lời gọi busy loop sang `join()`.

### 11.5 Chờ vô hạn khi worker không bao giờ kết thúc

Worker có thể kẹt vì I/O, bug, deadlock hoặc thiếu tín hiệu. `join()` cũng sẽ chờ vô hạn nếu không bị interrupt. Dùng timeout và chính sách xử lý lỗi/hủy khi yêu cầu hệ thống có giới hạn thời gian. Cần quản lý resource và shutdown riêng; timeout của join chỉ giới hạn thời gian **caller chờ**, không phải runtime của worker.

## 12. Chọn primitive chờ theo điều kiện thật

| Bài toán | API ưu tiên | Điều kiện thực sự được chờ |
| --- | --- | --- |
| Đợi một `Thread` do mình tạo kết thúc. | `Thread.join()` / `join(timeout)` | Kết thúc vòng đời thread. |
| Đợi toàn bộ nhóm `Thread` kết thúc. | Join từng thread đã start. | Tất cả thread đã terminated. |
| Đợi N tác vụ/cột mốc phát tín hiệu hoàn tất. | `CountDownLatch.await()` | Bộ đếm tín hiệu bằng 0; thread có thể vẫn sống. |
| Đợi một tác vụ có giá trị trả về/lỗi. | `Future.get()` / `get(timeout)` | Task completion, kết quả hoặc lỗi. |
| Phối hợp kết quả của nhiều tác vụ bất đồng bộ. | `CompletableFuture.allOf(...)` hoặc các combinator. | Completion của các future; kiểm tra lỗi riêng. |
| Đợi executor shutdown xong. | `ExecutorService.awaitTermination()` | Executor chuyển sang terminated. |
| Đợi điều kiện dữ liệu trong vùng lock. | `Condition.await()` cùng vòng kiểm tra predicate, hoặc cấu trúc concurrent phù hợp. | Predicate được thỏa dưới đồng bộ đúng. |
| Đợi phần tử từ queue. | `BlockingQueue.take()` / `poll(timeout)` | Có phần tử (hoặc hết thời hạn), không nhất thiết thread producer kết thúc. |

**Nguyên tắc chọn:** xác định bạn cần đợi **thread kết thúc**, **task xong**, **kết quả sẵn sàng**, **đủ N tín hiệu** hay **executor dừng**. Dùng API gắn với điều kiện đó. Tránh quản lý Thread thủ công chỉ để lấy giá trị của một task mà executor/Future đã hỗ trợ.

## 13. Kiểm thử và quan sát việc chờ

Để kiểm tra code không bận CPU và xử lý completion đúng:

1. **Định nghĩa sự kiện hoàn tất.** Ví dụ worker đã kết thúc, latch về 0, future completed, hay executor terminated. Không lấy “đã ngủ đủ 200 ms” làm tiêu chí.
2. **Kiểm tra đường thành công.** Tạo worker làm việc xác định, start, join, rồi assert kết quả. Ví dụ `JoinResultDemo` cho stdout cố định.
3. **Kiểm tra đường timeout.** Dùng latch giữ worker để tạo điều kiện chờ chắc chắn thay vì phụ thuộc `Thread.sleep()` và may rủi scheduler; sau đó giải phóng mọi thread trong `finally`.
4. **Kiểm tra interruption.** Tạo một thread đang chờ một worker bị chặn có kiểm soát; interrupt thread chờ, xác nhận nó rời blocking wait và vẫn thực hiện cleanup.
5. **Kiểm tra lỗi của task.** `Thread.join()` không ném lại exception từ `Runnable`; nếu muốn thu thập lỗi, kiểm thử `Future.get()` và handler/lưu lỗi riêng.
6. **Quan sát thread dump khi treo.** Thread đang đợi join thường thể hiện trạng thái chờ (`WAITING` hoặc `TIMED_WAITING` tùy kiểu join/JDK). Truy stack trace và dependency của thread mục tiêu; một thread đang `WAITING` không đủ kết luận deadlock.
7. **Đo CPU và thời gian đợi.** Busy loop có thể chiếm một core; blocking wait thường nhường CPU. Tuy vậy nhiều task bị block có thể vẫn gây nghẽn tài nguyên hoặc exhaustion pool.
8. **Luôn thu hồi tài nguyên test.** Worker được giữ bằng latch phải được mở cổng; executor phải shutdown; không để non-daemon thread treo khiến test/JVM không thoát.

Ví dụ anti-pattern trong test:

```java
Thread.sleep(100);
assertEquals(42, result[0]);
```

Đoạn này *giả định* worker hoàn thành trong 100 ms. Trên CI chậm hoặc khi máy bận, test có thể thất bại dù code đúng; trên máy nhanh, test pass cũng không thiết lập quan hệ synchronization rõ. Đổi thành `worker.join(timeout)` rồi xác nhận `!worker.isAlive()` trước khi kiểm tra kết quả, hoặc dùng latch/future cho đúng sự kiện completion.

## 14. Câu hỏi tự kiểm tra

**1.** Vì sao `while(worker.isAlive()) {}` kém hơn `worker.join()` khi chờ tác vụ dài?  
Vòng while liên tục tiêu thụ CPU; join block thread gọi cho đến khi worker kết thúc (hoặc caller bị interrupt).

**2.** `worker.join(500)` trả về có đồng nghĩa worker đã kết thúc?  
Không. Nó có thể trả về vì timeout. Kiểm tra `worker.isAlive()` nếu cần phân biệt.

**3.** Nếu `join()` ném `InterruptedException`, worker có tự dừng không?  
Không. Thread **đang chờ** bị interrupt; worker có thể tiếp tục chạy. Chính sách hủy worker là quyết định riêng.

**4.** Main đọc một giá trị worker đã ghi sau khi `join()` hoàn tất thì có quan hệ happens-before không?  
Có, các hành động của worker happens-before việc thread khác nhận biết worker kết thúc qua join thành công.

**5.** Vì sao không gọi `worker.join()` trong `synchronized(lock)` nếu worker cần lock đó?  
Main giữ lock trong lúc chờ worker, còn worker phải lấy lock mới kết thúc; có thể deadlock.

**6.** Muốn đợi 10 tác vụ executor trả về kết quả/lỗi, nên giữ 10 `Thread` để join không?  
Thông thường không. Giữ `Future` rồi `get()` hoặc dùng CompletableFuture combinator; pool worker có thể xử lý nhiều task.

**7.** `CountDownLatch.await()` trả về có chứng minh tất cả thread phát tín hiệu đã terminated không?  
Không. Nó chỉ chứng minh đủ `countDown()` đã xảy ra; thread có thể còn chạy đoạn mã sau tín hiệu.

**8.** `Thread.sleep(10)` trong vòng kiểm tra trạng thái có biến vòng đó thành phương án đồng bộ đúng không?  
Không. Đó là polling với khoảng nghỉ, vẫn có độ trễ và phải giải quyết visibility, timeout, interruption; nên dùng primitive chờ đúng điều kiện.

**9.** `CompletableFuture.join()` và `Thread.join()` có giống nhau không?  
Không. Một bên đợi completion của Future và có thể trả kết quả/unchecked exception, bên còn lại đợi vòng đời Thread và trả `void`.

**10.** `awaitTermination(timeout)` khi executor chưa shutdown có bảo đảm pool dừng không?  
Không. Cần gọi `shutdown()`/`shutdownNow()` theo chính sách; awaitTermination chỉ chờ điều kiện terminated.

**Ghi nhớ:** Khi cần đợi một thread hoàn tất, **`Thread.join()` là lựa chọn trực tiếp**. Chọn timed join kèm kiểm tra trạng thái khi có deadline; xử lý `InterruptedException` rõ ràng; không chờ khi đang giữ lock worker cần. Khi bài toán đòi **task result, N tín hiệu hoặc shutdown**, hãy chuyển sang Future, CountDownLatch hoặc ExecutorService tương ứng. Blocking wait giúp tránh lãng phí CPU, còn an toàn dữ liệu và khả năng hoàn thành vẫn phải được thiết kế riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-lifecycle-010">Vòng đời tài nguyên gắn với thread</a>

<details>
<summary>Xem chi tiết</summary>

# Vòng đời tài nguyên gắn với thread trong Java

Khi một `Thread` thực thi công việc, nó sử dụng nhiều loại tài nguyên: stack và thông tin điều phối do JVM quản lý, dữ liệu riêng của luồng (`ThreadLocal`), ngữ cảnh gắn với luồng, và các tài nguyên do mã ứng dụng mở như file, stream, socket hoặc kết nối. **Các tài nguyên này không nhất thiết sống và kết thúc cùng thời điểm.** Đặc biệt, một thread trong pool có thể phục vụ hàng nghìn tác vụ: tác vụ kết thúc không có nghĩa thread kết thúc, và thread kết thúc không tự động đóng mọi tài nguyên ứng dụng từng được mở.

Bài này tập trung vào **Java concurrency nói chung (Java 17+)**, thuộc chủ đề vòng đời `Thread`; không xét cơ chế quản lý luồng hoặc context riêng của Spring.

Sau bài học, bạn cần trả lời được:
- Tài nguyên nào thực sự thuộc JVM/Thread, tài nguyên nào thuộc một **tác vụ** hay một **phạm vi xử lý**?
- Dữ liệu trong `ThreadLocal` tồn tại bao lâu, tại sao tái sử dụng thread dẫn đến giữ dữ liệu cũ và cách dọn đúng?
- Khi có ngoại lệ, hủy, timeout, interrupt, ai chịu trách nhiệm đóng tài nguyên?
- Quản lý lifecycle của thread thủ công và `ExecutorService` khác nhau thế nào?
- Nhận diện và phòng tránh giữ bộ nhớ, rò rỉ handle, context sai và thread không kết thúc.

## 1. Phân biệt vòng đời thread, tác vụ và tài nguyên

Một **thread** là một luồng thực thi. Một **task** là phần việc được giao cho thread thực hiện. **Resource** là đối tượng hay khả năng truy cập cần được thu hồi, đóng hoặc khôi phục tại ranh giới sở hữu thích hợp.

~~~text
Vòng đời thread pool worker:
  Thread tạo ────────────────────────────────────────────────> Thread kết thúc
                 | Task A |  | Task B |  | Task C | ...
                   mở/đóng    mở/đóng    mở/đóng
                    resource   resource   resource

ThreadLocal của worker:
                   set(A) -> remove()
                                set(B) -> remove()
                                            set(C) -> remove()
~~~

Đối với `new Thread(task)` tự tạo, một thread thường chỉ thực thi một lần `run()` rồi kết thúc. Nhưng với `ExecutorService`, **worker thread tồn tại lâu hơn mỗi task** để được tái sử dụng. Vì vậy không được gắn sự dọn dẹp theo “khi nào thread chết” nếu resource chỉ dùng trong một tác vụ.

| Loại | Ví dụ Java | Thời điểm nên kết thúc/phục hồi | Ai chịu trách nhiệm |
| --- | --- | --- | --- |
| Nội bộ JVM phục vụ thread | Stack thực thi, trạng thái/metadata của thread | Khi thread kết thúc và JVM thu hồi phần tương ứng | JVM/hệ điều hành, theo cơ chế triển khai |
| Giá trị liên kết với một thread | `ThreadLocal<T>` | Hết phạm vi logic sử dụng (thường là cuối task), đặc biệt ở pool | Mã đặt giá trị phải `remove()` hoặc phục hồi giá trị trước đó |
| Ngữ cảnh thay đổi trên thread | Context class loader, thread name trong một scope | Khi thoát scope, kể cả lỗi | Mã thay đổi phải khôi phục |
| Tài nguyên của task | `InputStream`, `Reader`, `Socket`, JDBC `Connection` | Khi kết thúc phạm vi sở hữu; không phụ thuộc worker còn chạy | Mã sở hữu, thường bằng `try-with-resources` |
| Khóa phải giải phóng | `ReentrantLock` | Kết thúc đoạn tới hạn | `unlock()` trong `finally` sau khi lấy khóa thành công |
| Bộ thực thi | `ExecutorService` | Khi chủ sở hữu không nhận thêm công việc và muốn hoàn tất/huỷ các task còn lại | Thành phần tạo và sở hữu executor |

Một biến Java trở nên **không còn tham chiếu** không đồng nghĩa tài nguyên ngoài heap được đóng ngay. Garbage collector quản lý khả năng truy cập đối tượng; nó **không phải** cơ chế xác định thời điểm đóng kết nối, file hoặc socket. Đóng đúng lúc giúp tránh cạn tài nguyên và hành vi không dự đoán được.

## 2. Vòng đời của Thread không đồng nghĩa với vòng đời của resource

### 2.1. Những trạng thái quan trọng

`Thread.State` gồm `NEW`, `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING` và `TERMINATED`.

- `NEW`: đối tượng thread đã tạo nhưng chưa `start()`.
- `RUNNABLE`: đang chạy hoặc sẵn sàng chạy theo cách JVM biểu diễn; không nhất thiết đang dùng CPU ngay thời điểm quan sát.
- `BLOCKED`: đang chờ lấy monitor của `synchronized`.
- `WAITING` / `TIMED_WAITING`: đang chờ một điều kiện khác vô hạn hoặc có giới hạn thời gian, ví dụ `join()`/`sleep()`/một số cơ chế đồng bộ.
- `TERMINATED`: `run()` đã kết thúc, bình thường hoặc vì ngoại lệ chưa bắt.

Một `Thread` chỉ có thể `start()` **một lần**. Sau khi `run()` kết thúc, thread không thể khởi động lại. `join()` là cách người khác chờ thread kết thúc; `join()` không tự trả giá trị của tác vụ và không phải phương thức “đóng toàn bộ tài nguyên mà thread từng sử dụng”.

### 2.2. Điều gì xảy ra khi thread kết thúc?

Với platform thread thông thường trên Java 17, JVM/hệ điều hành có thể thu hồi tài nguyên phục vụ thực thi (ví dụ stack/native thread tương ứng) khi thread kết thúc. **Đối tượng `Thread` có thể vẫn được tham chiếu và còn tồn tại trên heap** sau đó; thread ở trạng thái `TERMINATED` không có nghĩa mọi object có liên quan đã lập tức được GC.

Giá trị trong `ThreadLocal` gắn với dữ liệu lưu trên thread, nên có thể được thu gom sau khi thread và dữ liệu của nó thực sự không còn tham chiếu phù hợp. Đây không phải lời hứa “ngay lúc `run()` kết thúc thì `close()` của ứng dụng tự động chạy”.

Ví dụ worker mở `FileInputStream` hoặc `Socket` rồi quên đóng: sự kết thúc thread **không cung cấp hợp đồng đóng** cho tài nguyên đó. Cần dùng `try-with-resources`/`finally` theo API tương ứng, ngay cả khi tác vụ chỉ chạy trên một thread ngắn hạn.

### 2.3. Daemon thread và liveness

`setDaemon(true)` phải được đặt trước khi thread được khởi động. Khi JVM chỉ còn daemon threads hoạt động (và không còn lý do khác để tiếp tục chạy theo cơ chế runtime), JVM có thể kết thúc mà không đợi những daemon thread hoàn tất tác vụ. Do đó không dùng daemon thread như một cách “tự dọn dẹp”; nó có thể bỏ dở thao tác ghi dữ liệu và `finally` cũng không được bảo đảm sẽ chạy khi JVM chấm dứt đột ngột.

Ngược lại, một thread không phải daemon bị kẹt có thể khiến ứng dụng không kết thúc. Vòng đời đúng cần kế hoạch dừng hợp tác, không chỉ thay `daemon`.

## 3. ThreadLocal: dữ liệu riêng của thread được giữ bao lâu?

`ThreadLocal<T>` giúp mỗi thread có một giá trị độc lập được tra cứu qua cùng một biến `ThreadLocal`. Nó thích hợp cho trạng thái **thực sự thuộc thread** hoặc dữ liệu ngữ cảnh **phải giới hạn thời gian sử dụng rất rõ**. Nó không tự đồng bộ hóa một đối tượng dùng chung và không chuyển dữ liệu giữa các thread.

Các thao tác:
- `set(value)`: ghi giá trị thuộc thread đang gọi.
- `get()`: lấy giá trị của **thread đang gọi**, hoặc giá trị khởi tạo nếu dùng `withInitial(...)`.
- `remove()`: xóa mapping của thread hiện tại với `ThreadLocal` này; lần `get()` tiếp theo có thể khởi tạo lại nếu cấu hình initializer.

~~~java
ThreadLocal<String> currentTask = new ThreadLocal<>();
currentTask.set("task-A");
try {
    // Mã chạy trong cùng thread có thể đọc currentTask.get().
} finally {
    currentTask.remove();
}
~~~

Đây là **đoạn mã minh họa**, dùng trong một phạm vi đã biết. `remove()` chỉ xóa liên kết trong `ThreadLocal`; **nó không gọi `close()` trên đối tượng từng chứa trong đó**. Nếu giá trị là tài nguyên cần đóng, cần đóng tài nguyên riêng và đồng thời dọn mapping.

### 3.1. Lỗi kinh điển: thread pool tái sử dụng dữ liệu của task trước

Vì pool giữ worker sống giữa các task, `ThreadLocal` sẽ giữ giá trị qua ranh giới task nếu quên dọn. Đó là lỗi logic (task B thấy context của task A) và cũng có thể kéo dài thời gian sống của một object lớn.

Ví dụ đầy đủ cho Java 17+ sử dụng executor **một worker** để bảo đảm hai task lần lượt chạy trên cùng một thread. Đây là thí nghiệm có chủ đích, không nên giữ nhánh thiếu cleanup trong mã sản phẩm.

~~~java
// ThreadLocalLifecycleDemo.java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadLocalLifecycleDemo {
    private static void scenario(String label, boolean cleanup) throws Exception {
        ThreadLocal<String> context = new ThreadLocal<>();
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            pool.submit(() -> {
                context.set("request-A");
                try {
                    System.out.println(label + " first: " + context.get());
                } finally {
                    if (cleanup) {
                        context.remove();
                    }
                }
            }).get();

            pool.submit(() -> {
                try {
                    System.out.println(label + " second: " + context.get());
                } finally {
                    context.remove();
                }
            }).get();
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        scenario("unsafe", false);
        scenario("safe", true);
    }
}
~~~

Chạy:

~~~bash
javac ThreadLocalLifecycleDemo.java
java ThreadLocalLifecycleDemo
~~~

Stdout:

~~~text
unsafe first: request-A
unsafe second: request-A
safe first: request-A
safe second: null
~~~

**Giải thích:** Tác vụ đầu đặt `"request-A"`. Ở tình huống `unsafe`, task hoàn thành nhưng worker không kết thúc, nên task thứ hai đọc lại giá trị cũ. Ở `safe`, `remove()` chạy trong `finally` trước khi thread được tái sử dụng; lần `get()` ở task kế tiếp trả `null`. Hai tình huống sử dụng hai executor riêng; mỗi executor có chính xác một worker, tránh phụ thuộc vào lựa chọn worker của một pool nhiều luồng. Các lệnh `Future.get()` đảm bảo hai tác vụ theo thứ tự và lỗi trong chúng sẽ được chuyển tới caller.

**Nguyên tắc:** Mã đã `set()` dữ liệu mang ý nghĩa **một request/một task** phải có ranh giới dọn ở **cuối request/task**, không chờ pool đóng.

### 3.2. Giá trị tồn tại trong bộ nhớ như thế nào?

Một thread có cấu trúc lưu giá trị thread-local (triển khai của JDK dùng `ThreadLocalMap`). Trong triển khai thông dụng, khóa của entry tham chiếu yếu tới đối tượng `ThreadLocal`, còn value được giữ mạnh. Có hai tình huống đáng lưu ý:

1. Nếu `ThreadLocal` còn tồn tại, ví dụ biến `static final`, mỗi worker từng `set` có thể giữ giá trị đến khi được `remove`/thay hoặc worker được thu hồi.
2. Nếu chính đối tượng `ThreadLocal` không còn được tham chiếu mạnh ở nơi khác, khóa yếu có thể được thu gom, nhưng entry chứa value chưa chắc được dọn ngay; triển khai loại entry cũ trong các hoạt động phù hợp, nên không được trông chờ GC như chiến lược cleanup có thời hạn.

Vì vậy một pool sống lâu có thể giữ một đồ thị đối tượng lớn, thậm chí cả cấu trúc tham chiếu liên quan đến class loader. Không gọi mọi việc giữ bộ nhớ là “memory leak vĩnh viễn”: chính xác hơn là **giữ tham chiếu lâu hơn phạm vi cần thiết**, gây tăng sử dụng heap hoặc kéo dài vòng đời đối tượng.

### 3.3. Khi nào ThreadLocal không phù hợp?

- Kết quả cần chuyển từ worker sang caller: dùng `Future`, `Callable` hoặc cơ chế truyền dữ liệu rõ ràng.
- Context phải đi theo một task qua **nhiều thread**: truyền tham số hoặc sao chép context có kiểm soát; một `ThreadLocal` thông thường chỉ đọc giá trị của **thread hiện tại**.
- Có thể hoàn thành bằng biến cục bộ/phương thức tham số: phương án đó thường dễ hiểu hơn và có vòng đời ngắn hơn.
- Dữ liệu là tài nguyên cần `close()`: không coi `ThreadLocal.remove()` như thay thế `close()`.

## 4. Tài nguyên thuộc một task: đóng trong phạm vi sở hữu

### 4.1. Dùng try-with-resources, kể cả khi chạy qua ExecutorService

Nếu một tác vụ mở stream/reader/socket/kết nối thì task hoặc abstraction được chỉ định sở hữu phải đóng chúng sau khi hoàn thành. `try-with-resources` tự gọi `close()` khi thoát khối, kể cả khi có exception. Nếu có nhiều resource, chúng được đóng theo thứ tự ngược với thứ tự khai báo.

Ví dụ dưới đây vừa trả kết quả qua `Future`, vừa đóng resource và xóa `ThreadLocal` theo vòng đời task. `StringReader` chỉ dùng để tạo ví dụ chạy độc lập không cần file ngoài; với file/socket thật, quy tắc sở hữu tài nguyên vẫn như vậy.

~~~java
// TaskScopedResourceDemo.java
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TaskScopedResourceDemo {
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Callable<String> task = () -> {
                CONTEXT.set("request-7");
                try (BufferedReader reader =
                         new BufferedReader(new StringReader("alpha\nbeta"))) {
                    return CONTEXT.get() + ": " + reader.readLine();
                } finally {
                    CONTEXT.remove();
                }
            };

            System.out.println(pool.submit(task).get());
            System.out.println("next sees: " +
                pool.submit(() -> CONTEXT.get()).get());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac TaskScopedResourceDemo.java
java TaskScopedResourceDemo
~~~

Stdout:

~~~text
request-7: alpha
next sees: null
~~~

- `BufferedReader.close()` được gọi ở cuối khối `try-with-resources`, **trước khi task trả kết quả**; caller chỉ nhận chuỗi qua `Future.get()`.
- `finally { CONTEXT.remove(); }` bảo đảm context được xóa khi task kết thúc bình thường hoặc ném exception.
- Task kế tiếp được submit vào cùng executor một worker, nên thấy `null`. Đây là kết quả mong muốn.
- `shutdown()` quản lý vòng đời **executor**, không thay thế việc đóng reader và xóa context trong từng task.

Khi dùng connection, lưu ý “ai sở hữu?”: một connection do task tự mở có thể phải đóng ở cuối scope; connection được một thành phần khác cho mượn có thể có hợp đồng trả về riêng. Đừng đóng tài nguyên đang thuộc sở hữu của thành phần khác chỉ vì bạn đã sử dụng nó.

### 4.2. Các cơ chế cleanup không thể thay thế nhau

| Cơ chế | Làm gì? | Không làm gì? |
| --- | --- | --- |
| `ThreadLocal.remove()` | Xóa value liên kết với thread hiện tại | Không đóng resource được value trỏ tới |
| `AutoCloseable.close()` | Gọi phương thức đóng theo hợp đồng resource | Không tự xóa một mapping `ThreadLocal` bất kỳ |
| `finally` | Bảo đảm khối dọn chạy khi luồng điều khiển thoát bình thường/exception trong điều kiện JVM tiếp tục thực thi | Không bảo đảm chạy sau `Runtime.halt()`, JVM crash hoặc process bị kill |
| `Thread.join()` | Đợi một thread cụ thể kết thúc | Không đóng handle/tài nguyên của task |
| `Future.get()` | Đợi và quan sát kết quả/ngoại lệ của task | Không tự đóng resource bị task bỏ quên |
| `ExecutorService.shutdown()` | Ngừng chấp nhận task mới, cho task đã nhận hoàn tất | Không chờ hoàn tất, không tự dọn mọi resource do task mở |

`java.util.concurrent.locks.ReentrantLock` cũng cần dọn rõ ràng, nhưng **không phải `AutoCloseable`**: lấy khóa thành công rồi phải `unlock()` trong `finally`.

~~~java
// Ví dụ minh họa: giả sử lock là ReentrantLock đã khai báo.
lock.lock();
try {
    // Cập nhật trạng thái chia sẻ cần được bảo vệ.
} finally {
    lock.unlock();
}
~~~

Phạm vi `finally` phải bắt đầu sau khi thực sự lấy được khóa; không `unlock` một khóa mà thread chưa giữ.

## 5. Context gắn với thread: đừng mang giá trị sai sang task sau

### 5.1. Thread name và context class loader

Tên thread thường giúp chẩn đoán. Nếu bạn tạm thay đổi `Thread.currentThread().setName(...)` cho một task trên worker, hãy khôi phục tên cũ trong `finally` để task sau không nhận nhãn của task trước.

Context class loader cũng là trạng thái gắn với thread, truy cập qua `getContextClassLoader()` / `setContextClassLoader(...)`. Một số API tra cứu provider/service theo loader này. Thay đổi mà quên phục hồi có thể khiến tác vụ về sau dùng sai loader và giữ tham chiếu không cần thiết tới class loader.

~~~java
// Ví dụ minh họa: customLoader đã được xác định từ trước.
Thread current = Thread.currentThread();
String oldName = current.getName();
ClassLoader oldLoader = current.getContextClassLoader();
try {
    current.setName("worker-for-task-A");
    current.setContextClassLoader(customLoader);
    // Thực thi công việc cần context tạm thời.
} finally {
    current.setContextClassLoader(oldLoader);
    current.setName(oldName);
}
~~~

Đoạn trên **không phải file Java chạy độc lập**, vì `customLoader` cần được cung cấp. Điểm quan trọng là **save → change → use → restore**, kể cả khi thao tác thất bại. Nếu context có thể lồng nhau, việc phục hồi giá trị cũ đúng hơn là đơn giản gọi `ThreadLocal.remove()` lên một context đã có trước scope; xác định rõ ai là chủ sở hữu ngữ cảnh.

### 5.2. InheritableThreadLocal không tự truyền context theo task

`InheritableThreadLocal<T>` cho phép child thread nhận giá trị kế thừa khi thread được tạo, với cách kế thừa theo hợp đồng API. Nó **không bảo đảm** rằng mỗi task được gửi vào thread pool sẽ nhận context hiện tại của thread submit: worker có thể đã được tạo từ trước và tái sử dụng. Với value mutable, việc kế thừa tham chiếu cũng có thể gây chia sẻ trạng thái ngoài ý muốn.

Không dùng `InheritableThreadLocal` để giả định có cơ chế truyền “request context” xuyên mọi executor. Nếu cần context theo task, **truyền rõ ràng dữ liệu**, và chỉ đặt/khôi phục trong phạm vi task ở đúng thread thực thi khi thật sự cần.

## 6. Interrupt, timeout, cancel và ngoại lệ ảnh hưởng đến cleanup

### 6.1. Interrupt là tín hiệu hợp tác, không phải nút kill

Gọi `Thread.interrupt()` yêu cầu thread chú ý đến trạng thái gián đoạn. Các thao tác chặn có hỗ trợ interrupt như `Thread.sleep`, `Object.wait`, `Thread.join` và một số phép chờ trong concurrency API có thể ném `InterruptedException`. Khi ném ngoại lệ này, trạng thái interrupt thường bị xóa theo hợp đồng phương thức; nếu catch và không truyền tiếp, cần cân nhắc khôi phục bằng `Thread.currentThread().interrupt()`, sau đó kết thúc công việc/đưa tín hiệu lên tầng gọi.

~~~java
// Đoạn mã minh họa bên trong task; resource là AutoCloseable.
try (var resource = openResource()) {
    doInterruptibleWork(resource);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // Thoát khỏi task hoặc báo hủy theo hợp đồng.
}
~~~

`openResource()` và `doInterruptibleWork(...)` là **placeholder giải thích cấu trúc**, không phải API Java có sẵn; đoạn này không phải chương trình độc lập. Điểm cần nhìn là resource được đóng khi thoát khỏi `try-with-resources` do interrupt gây ra. Dù vậy, không phải mọi thao tác I/O hoặc thư viện bên ngoài đều phản hồi interrupt ngay lập tức.

### 6.2. Future.cancel và ExecutorService.shutdownNow

- `Future.cancel(true)` thử hủy task, cho phép gián đoạn thread đang chạy nếu task đang chạy và implementation hỗ trợ. Đây **không phải bảo đảm** mã tác vụ dừng tức thì.
- `Future.get(timeout, unit)` chỉ giới hạn **thời gian chờ của caller**. `TimeoutException` không tự dừng task hoặc đóng tài nguyên của nó.
- `ExecutorService.shutdown()` dừng nhận việc mới; task đã được nhận vẫn có thể chạy.
- `ExecutorService.shutdownNow()` cố dừng task đang chạy bằng interrupt và trả về danh sách task đang đợi chưa chạy; nó không bảo đảm tất cả worker đã thực sự dừng.
- `awaitTermination(...)` cho caller khả năng đợi executor kết thúc trong một khoảng thời gian; nếu quá thời hạn, cần chính sách tiếp theo.

**Hệ quả với resource:** `finally`/`try-with-resources` trong task chỉ chạy khi luồng điều khiển của task thực sự thoát khỏi scope. Nếu task phớt lờ interrupt và tiếp tục chạy, caller `cancel` hoặc `shutdownNow` không có phép màu đóng thay những resource đang nằm trong task đó. Hãy thiết kế task có đường thoát hợp tác và deadline thích hợp.

### 6.3. Mẫu quản lý executor trên Java 17

~~~java
// ExecutorShutdownDemo.java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ExecutorShutdownDemo {
    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.execute(() -> System.out.println("task complete"));
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                    if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                        System.err.println("executor still running");
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

Stdout trong lần chạy bình thường:

~~~text
task complete
~~~

Giải thích: `shutdown()` ngăn nhận task mới; `awaitTermination` chờ tối đa thời gian chỉ định; nếu chưa kết thúc, `shutdownNow()` yêu cầu hủy theo mức implementation cho phép. Trong ví dụ bình thường, một tác vụ in thông báo rồi kết thúc rất nhanh. Với hệ thống thực tế, thiết kế timeout/chính sách báo lỗi phải tương ứng với nhu cầu ứng dụng, và người dùng cần biết khi dừng không thành công.

**Lưu ý phiên bản:** Trên Java 17, `ExecutorService` chưa có cơ chế `AutoCloseable.close()` theo API mới hơn, nên hãy quản lý `shutdown`/`awaitTermination` chủ động như ví dụ. Không lấy ví dụ `try (ExecutorService executor = ...)` từ tài liệu JDK mới hơn rồi kỳ vọng biên dịch dưới Java 17.

### 6.4. Uncaught exception và cleanup

Một exception không được bắt có thể khiến `Thread.run()` kết thúc; với `Thread` thủ công, JVM có cơ chế uncaught-exception handler để báo lỗi. Nhưng đây **không phải** cách tự động dọn các resource mà code mở sai phạm vi. `finally` và `try-with-resources` trong những frame đang unwinding vẫn chạy trong điều kiện thực thi Java thông thường. Với task do `ExecutorService.submit`, lỗi thường được giữ trong `Future` và quan sát khi gọi `get()`. Không chỉ dựa vào log handler để biết task có hoàn tất thành công hay không.

## 7. Khác nhau giữa ThreadLocal và tài nguyên được truyền qua tham số

Giả sử một worker xử lý lần lượt task A và task B:
- Dùng **tham số** `process(order, requestId)`: giá trị thuộc lời gọi, dễ thấy ranh giới và không tự lưu trên worker.
- Dùng **ThreadLocal** cho `requestId`: nhiều phương thức sâu trong call stack có thể đọc mà không nhận tham số, nhưng mọi đoạn code cùng thread có thể đọc cùng mapping và phải bảo đảm dọn. Nếu code giao một bước cho thread khác, thread mới không mặc nhiên có giá trị.
- Dùng **một object mutable chia sẻ** qua nhiều worker: cần thiết kế thread safety của object độc lập với lifecycle; `ThreadLocal` không biến object đó thành bất biến hay ngăn race nếu nhiều thread trỏ tới cùng instance.

Quy tắc thực hành: **ưu tiên trạng thái theo phạm vi xử lý**, chỉ lưu trạng thái theo thread khi có lý do rõ ràng về API, hiệu năng hoặc ngữ cảnh thực thi. Một thread là công cụ chạy công việc, không nhất thiết là đơn vị nghiệp vụ “request”.

## 8. Chẩn đoán giữ tài nguyên quá lâu

### Dấu hiệu thường gặp

| Triệu chứng | Khả năng cần kiểm tra |
| --- | --- |
| Request sau thấy ID/context của request trước | `ThreadLocal` hoặc thread metadata không được dọn/khôi phục trên worker tái sử dụng |
| Heap tăng theo thời gian dù số request đã hoàn tất | Giá trị `ThreadLocal`, cache/task queue, hoặc object graph còn được giữ |
| Tăng số file descriptor/socket/connection | Resource ngoài heap chưa `close` đúng thời điểm |
| Ứng dụng không chịu thoát sau khi ngừng nhận việc | Executor chưa `shutdown`, worker không kết thúc, task chặn hoặc bỏ qua interrupt |
| Task hủy rồi nhưng vẫn giữ kết nối lâu | Timeout/cancel chỉ dừng chờ caller, task không thoát scope hoặc API không hỗ trợ ngắt |
| Task chạy trên thread khác mất context | `ThreadLocal` không truyền theo task; thread pool tái sử dụng không bảo đảm `InheritableThreadLocal` |

### Cách tìm nguyên nhân

1. **Xác định chủ sở hữu**: ai mở file/kết nối, ai `set` `ThreadLocal`, ai tạo executor? Ghi ranh giới chính xác: method call, task, request, worker hay toàn ứng dụng.
2. **Đo thời gian sống thật**: khi công việc xong, resource đã đóng chưa? Hãy đo số connection/descriptor đang mở, heap hoặc queue đang giữ task, số worker đang sống.
3. **Tìm đường thoát**: exception, return sớm, cancel, timeout và interrupt có đi qua `finally`/`try-with-resources` không? Có nhánh nào quên cleanup?
4. **Kiểm tra sử dụng thread pool**: chạy nhiều task liên tiếp trên cùng worker để phát hiện dữ liệu cũ trong `ThreadLocal`; đừng kiểm thử bằng hai thread mới rồi kết luận không có lỗi.
5. **Quan sát thread dump nếu nghi worker kẹt**: `jcmd <PID> Thread.print -l` có thể giúp xem trạng thái/chồng lời gọi của các thread đang hoạt động, nhưng **không tự cho biết mọi stream hoặc ThreadLocal đã được dọn**. Kết hợp log, heap dump hoặc công cụ đo tài nguyên thích hợp khi cần.
6. **Sửa theo hợp đồng sở hữu**: resource mở ở đâu thì đóng đúng chỗ; `ThreadLocal` theo task thì `remove` cuối task; trạng thái context tạm thời thì restore; executor do ứng dụng sở hữu thì shutdown đúng lifecycle.

Đừng mặc định “rò rỉ bộ nhớ” khi thấy heap lớn: cache hợp lệ, GC chưa thu hồi, task queue bị tồn đọng, thread-local giữ value hoặc reference ngoài phạm vi đều có thể gây biểu hiện tương tự. Cần lần theo đối tượng và thời điểm chúng còn được tham chiếu.

## 9. Checklist thiết kế và lỗi cần tránh

- **Task mở resource** → dùng `try-with-resources` nếu là `AutoCloseable`; bảo đảm close kể cả khi có lỗi.
- **Task đặt ThreadLocal** → `remove()` trong `finally`, hoặc lưu/khôi phục giá trị cũ nếu xử lý ngữ cảnh lồng nhau.
- **Task đổi thread name/context loader** → save giá trị trước và restore trong `finally`.
- **Thread dùng lock** → lấy khóa thành công rồi `unlock()` trong `finally`.
- **Executor do thành phần hiện tại sở hữu** → `shutdown()` và đợi hoàn tất có giới hạn, xử lý interrupt/cancel phù hợp.
- **Task cần dừng** → phản hồi interrupt hoặc có cơ chế hủy hợp tác; đừng phụ thuộc vào `Thread.stop()` (unsafe/deprecated).
- **Có nhiều worker** → không dùng `ThreadLocal` để truyền giá trị trả về hoặc mặc định chia sẻ context giữa thread.
- **Dùng daemon thread** → không xem daemon là cơ chế flush/close resource khi JVM thoát.
- **Có giá trị được cache theo thread** → cân nhắc chi phí bộ nhớ nhân với số worker và nhu cầu cleanup/eviction cụ thể.
- **Có ngoại lệ ở executor task** → quan sát kết quả `Future`, vì lỗi có thể không hiện ra theo cách mong đợi nếu chỉ `submit` rồi bỏ quên future.

### Sai lầm hay gặp

**“Đã xong request nên ThreadLocal sẽ tự trống.”** Sai nếu worker tiếp tục sống; mapping theo **thread**, không theo request.

**“Gọi ThreadLocal.remove() sẽ đóng Connection.”** Sai: remove chỉ tháo liên kết. `Connection.close()` tuân theo hợp đồng sở hữu riêng.

**“Thread kết thúc thì JVM đóng tất cả socket/file do nó mở.”** Không có bảo đảm như vậy. Thread và I/O resource có vòng đời khác nhau.

**“Future.get(timeout) timeout nghĩa là task đã bị hủy.”** Sai: caller ngừng chờ, task có thể đang chạy.

**“Dùng InheritableThreadLocal là context tự đi theo submit().”** Sai với pool: kế thừa xảy ra theo việc tạo thread, không theo mỗi lần submit task.

**“shutdownNow() chắc chắn dừng hết ngay.”** Sai: nó cố gián đoạn task đang chạy; task cần hợp tác và có thể không thoát.

**“Đợi join() là thay được close().”** Sai: join đợi thực thi kết thúc; resource của task cần chính task hoặc owner đóng đúng phạm vi.

## 10. Câu hỏi tự kiểm tra

1. **Một `ExecutorService` chỉ có một worker. Task A đặt `ThreadLocal` rồi kết thúc mà không remove. Task B đọc gì?** Nếu không có ghi đè/cleanup nào khác, B có thể đọc lại giá trị A, vì cả hai chạy trên cùng thread.
2. **Task A dùng `BufferedReader` trong `try-with-resources`, còn worker vẫn sống. Reader đã đóng chưa?** Đã đóng khi rời scope, không cần đợi worker chết.
3. **Bắt `InterruptedException` và tiếp tục xử lý như không có gì xảy ra có thể gây vấn đề gì?** Làm mất tín hiệu hủy/dừng, task kéo dài và resource được giữ lâu hơn; cân nhắc truyền tiếp hoặc khôi phục interrupt rồi thoát.
4. **Nếu `ThreadLocal` chứa một `Socket`, gọi remove có đóng socket không?** Không; owner phải đóng socket theo hợp đồng.
5. **Thay context class loader trong task có cần khôi phục không?** Có, để worker được tái sử dụng không mang loader của task trước.
6. **`shutdown()` rồi thoát `main` có chắc pool đã kết thúc không?** Không; cần chờ/cấu trúc lifecycle phù hợp nếu yêu cầu xác định thời điểm kết thúc.
7. **Dùng `InheritableThreadLocal` cho pool tạo từ đầu ứng dụng có đảm bảo task mới thấy context của thread submit không?** Không, vì giá trị kế thừa xảy ra khi tạo worker, không ở mỗi lần gửi task.

**Kết luận cần nhớ:** Xác định **resource thuộc phạm vi nào**, rồi đặt **cleanup ở cuối chính phạm vi đó**. Thread có thể sống lâu hơn task; vì vậy việc gỡ `ThreadLocal`, khôi phục context, giải phóng lock và đóng I/O phải do mã sở hữu thực hiện chủ động. `Thread.join()`, `Future.get()` và `ExecutorService.shutdown()` hỗ trợ điều phối vòng đời thực thi, nhưng không thay thế các thao tác cleanup của tài nguyên ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-lifecycle-011">Phân biệt trạng thái thread và trạng thái nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt trạng thái thread và trạng thái nghiệp vụ trong Java

## 1. Vì sao phải phân biệt?

Khi xây dựng một tác vụ bất đồng bộ, ta thường muốn biết cả hai điều:

1. **Thread hiện đang ở trạng thái nào?** Nó chưa khởi động, có thể chạy, đang chờ khóa/điều kiện, hay đã kết thúc?
2. **Công việc nghiệp vụ đang ở trạng thái nào?** Tác vụ đã được tiếp nhận, đang xử lý, thành công, thất bại, bị hủy hay cần chạy lại?

Hai câu hỏi này **không tương đương**.

**Thread state** là trạng thái của luồng thực thi trong Java, được JVM biểu diễn bằng **Thread.State**. Nó phục vụ quan sát vòng đời và chẩn đoán tại thời điểm đọc.

**Business state** (còn gọi là *task/job/workflow status*) là trạng thái do ứng dụng **tự định nghĩa** để biểu diễn tiến độ và kết quả có ý nghĩa với người dùng hoặc hệ thống nghiệp vụ. Ví dụ **PENDING → RUNNING → SUCCEEDED/FAILED**.

Điểm quan trọng nhất: **Thread.State.TERMINATED không đồng nghĩa công việc thành công; Thread.State.WAITING không đồng nghĩa công việc nghiệp vụ đang chờ xử lý; Thread.State.RUNNABLE không đồng nghĩa nghiệp vụ đang RUNNING.**

Bài này nói về **Java concurrency nói chung** và các API trong JDK. Các cơ chế quản lý task, cấu hình scheduler hoặc sự kiện thuộc Spring nằm ở chương riêng.

## 2. Thread.State: sáu trạng thái của JVM

Java cung cấp enum **java.lang.Thread.State** với sáu giá trị:

| Thread.State | JVM mô tả điều gì? | Ví dụ |
| --- | --- | --- |
| **NEW** | Đối tượng Thread đã tạo nhưng chưa gọi start(). | new Thread(task) |
| **RUNNABLE** | Đang thực thi hoặc sẵn sàng được scheduler cấp thời gian CPU. | Mã Java đang chạy hoặc chờ lượt CPU |
| **BLOCKED** | Chờ lấy **monitor lock** để vào synchronized. | Một thread khác đang giữ cùng monitor |
| **WAITING** | Đợi vô thời hạn cho một hành động khác. | Object.wait(), Thread.join(), CountDownLatch.await() không timeout |
| **TIMED_WAITING** | Đợi một khoảng thời gian có hạn. | Thread.sleep(...), join(timeout), await(timeout) |
| **TERMINATED** | Hàm run() của Thread đã kết thúc. | Chạy xong, return, hoặc exception không được xử lý |

Lưu ý chính xác:

- **RUNNABLE** không có nghĩa thread đang dùng CPU ngay lúc đo.
- **BLOCKED** là trạng thái chờ **monitor của synchronized**. Thread đợi **ReentrantLock** hoặc một thao tác I/O có thể được quan sát ở trạng thái khác, tùy API và triển khai; không suy luận rằng mọi kiểu “bị chặn” đều là Thread.State.BLOCKED.
- **WAITING/TIMED_WAITING** không cho biết vì sao task nghiệp vụ chờ; đó chỉ là kiểu chờ trong luồng thực thi.
- **getState()** trả về một **snapshot**, có thể thay đổi ngay sau khi đọc.
- **TERMINATED** nói thread đã dừng, **không cho biết** nó hoàn thành thành công, ném lỗi hay dừng vì yêu cầu hủy.
- Một đối tượng Thread đã TERMINATED **không thể gọi start() lại**; muốn thực thi tiếp phải có Thread mới hoặc cơ chế thực thi tác vụ khác.

Vòng đời phổ biến:

~~~text
NEW --start()--> RUNNABLE --run() kết thúc--> TERMINATED
                    │   ^
                    v   │
           BLOCKED / WAITING / TIMED_WAITING
~~~

Đây là sơ đồ khái quát, không phải máy trạng thái nghiệp vụ. Chẳng hạn, khi một thread trong **run()** đang gọi **sleep()**, trạng thái là TIMED_WAITING dù tác vụ của nó vẫn mang trạng thái nghiệp vụ **RUNNING**.

### Không sử dụng getState() làm giao thức điều phối

Ví dụ sau là **anti-pattern**:

~~~java
// Mã minh họa lỗi: worker là Thread đã được tạo ở nơi khác.
if (worker.getState() == Thread.State.TERMINATED) {
    markJobSucceeded();
}
~~~

Thứ nhất, **markJobSucceeded()** là phương thức giả định của ứng dụng, không phải API JDK. Thứ hai, thread chết có thể do lỗi. Thứ ba, snapshot không thay thế được **join()**, **Future.get()**, latch hoặc sự kiện kết thúc có cơ chế đồng bộ. Nếu muốn biết một thread đã hoàn thành, dùng **join()**; nếu muốn biết **kết quả công việc**, cần ghi nhận và kiểm tra trạng thái/kết quả nghiệp vụ.

## 3. Business state: mô hình do ứng dụng thiết kế

JDK không có enum chung mang tên **BusinessState** hay **JobStatus** phù hợp cho mọi bài toán. Ứng dụng tự đặt trạng thái theo ý nghĩa thực tế.

Một mô hình tối giản cho một job:

~~~text
PENDING ──nhận xử lý──> RUNNING ──hoàn thành──> SUCCEEDED
                          │
                          ├── lỗi ─────────> FAILED
                          └── hủy thành công -> CANCELLED
~~~

Có thể thêm **QUEUED**, **RETRYING**, **TIMED_OUT**, **CANCEL_REQUESTED**, **PAUSED** tùy yêu cầu; đừng tự thêm chỉ vì Thread.State cũng có các trạng thái chờ. Trong ứng dụng thực, cần định nghĩa chính xác khi nào một transition được phép xảy ra.

Ví dụ Java enum để mô hình hóa:

~~~java
enum JobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCEL_REQUESTED,
    CANCELLED
}
~~~

Đây là **đoạn mã minh họa**; enum trên chưa có **main()**. Ý nghĩa mỗi trạng thái cần được thống nhất: ví dụ **CANCEL_REQUESTED** chỉ nói có yêu cầu hủy, còn **CANCELLED** nói ứng dụng đã xác nhận công việc dừng theo chính sách hủy.

Điều khác biệt với Thread.State:

- Business state có thể được **lưu xuống cơ sở dữ liệu** để dùng sau khi JVM thoát.
- Business state có thể **tồn tại trước khi tạo thread**, hoặc sau khi một thread đã hoàn tất.
- Một job có thể đi qua nhiều thread trong các giai đoạn khác nhau; một worker thread có thể xử lý nhiều job.
- Business state đại diện cho **đơn vị công việc** và kết quả theo hợp đồng ứng dụng, không phải trạng thái nội bộ của worker.
- **SUCCEEDED**, **FAILED**, **CANCELLED** chỉ đáng tin khi có quy tắc ghi nhận rõ ràng; không suy luận từ trạng thái thread.

## 4. So sánh trực tiếp

| Câu hỏi | Thread state | Business state |
| --- | --- | --- |
| Đối tượng được mô tả | Một **Thread** cụ thể | Một task, job, order, workflow hoặc operation |
| Ai định nghĩa | JDK: enum Thread.State | Ứng dụng: enum, bản ghi, state machine |
| Nguồn quan sát | Thread.getState() | Trường status, event, Future/result hoặc kho lưu trữ |
| Tính ổn định | Snapshot tức thời | Theo transition và chính sách nghiệp vụ |
| Có nghĩa là thành công/thất bại? | Không có trạng thái SUCCESS/FAILED | Có thể có SUCCEEDED/FAILED |
| Có thể tồn tại lâu hơn một thread? | Không, gắn với đối tượng Thread | Có thể |
| Có bắt buộc được lưu bền vững? | Không | Tùy yêu cầu; job lâu dài thường cần persistence |
| Có thay thế synchronization không? | Không | Không; trạng thái chia sẻ vẫn phải an toàn |
| Trạng thái kết thúc | TERMINATED | SUCCEEDED, FAILED, CANCELLED hoặc trạng thái cuối khác |

Một số cặp dễ gây hiểu nhầm:

| Quan sát | Trạng thái nghiệp vụ vẫn có thể là | Vì sao |
| --- | --- | --- |
| Thread **NEW** | PENDING, QUEUED, hoặc chưa gắn job | Business status có thể được tạo từ trước |
| Thread **RUNNABLE** | RUNNING, RETRYING, CANCEL_REQUESTED | Thread có thể đang thực thi logic của nhiều giai đoạn |
| Thread **WAITING** | RUNNING | Task đang chờ phản hồi/điều kiện để tiếp tục bước đang xử lý |
| Thread **TIMED_WAITING** | RUNNING, RETRYING, CANCEL_REQUESTED | Thread đang sleep/backoff/chờ timeout |
| Thread **BLOCKED** | RUNNING | Task đã bắt đầu nhưng đang đợi monitor |
| Thread **TERMINATED** | SUCCEEDED, FAILED, CANCELLED | Worker đã kết thúc bằng nhiều cách khác nhau |

Bảng chỉ nêu **khả năng hợp lệ** theo thiết kế của từng ứng dụng, không có ánh xạ tự động được JDK bảo đảm.

## 5. Ví dụ chạy được: thread đang chờ nhưng job đang RUNNING

Tệp **ThreadAndJobStateDemo.java** (Java 17+):

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class ThreadAndJobStateDemo {
    enum JobStatus { PENDING, RUNNING, SUCCEEDED }

    public static void main(String[] args) throws InterruptedException {
        AtomicReference<JobStatus> status =
                new AtomicReference<>(JobStatus.PENDING);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            status.set(JobStatus.RUNNING);
            started.countDown();
            try {
                proceed.await();
                status.set(JobStatus.SUCCEEDED);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "job-worker");

        System.out.println("before start: " + status.get());
        worker.start();

        started.await();
        System.out.println("after started signal: " + status.get());

        proceed.countDown();
        worker.join();
        System.out.println("after join: " + status.get());
        System.out.println("thread: " + worker.getState());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 ThreadAndJobStateDemo.java
java ThreadAndJobStateDemo
~~~

Stdout (khi không có interrupt từ bên ngoài):

~~~text
before start: PENDING
after started signal: RUNNING
after join: SUCCEEDED
thread: TERMINATED
~~~

**Giải thích:**

1. **status** là nghiệp vụ của job, được ứng dụng lưu độc lập trong **AtomicReference**.
2. Worker chuyển trạng thái job sang RUNNING rồi báo **started.countDown()**. **started.await()** của main đảm bảo main quan sát tín hiệu và cập nhật đã xảy ra trước countDown.
3. Worker đợi **proceed.await()**; trong khoảng chờ nó có thể được quan sát là **WAITING**, nhưng **status vẫn RUNNING**. Ví dụ cố ý không in **worker.getState()** khi đang chạy vì snapshot không có kết quả cố định và có race với việc release latch.
4. Main phát tín hiệu **proceed.countDown()** rồi **join()**. Worker đổi job thành SUCCEEDED trước khi kết thúc; main quan sát kết quả ổn định sau join().
5. Sau khi **join()** không timeout trả về bình thường vì worker đã kết thúc, **getState()** là TERMINATED. Thành công được biểu diễn bởi cập nhật **status**, chứ không được suy ra từ TERMINATED.

Ví dụ dùng latch để thiết lập các mốc rõ ràng, không dùng **Thread.sleep()** để phỏng đoán thread đã chạy đến đâu.

**Giới hạn của ví dụ:** Worker có nhánh bắt InterruptedException để kết thúc hợp tác, nhưng mô hình enum tối giản không có trạng thái CANCELLED/FAILED cho trường hợp đó. Khi thiết kế thực tế, mọi nhánh lỗi và hủy phải cập nhật trạng thái tương ứng. Phần tiếp theo xử lý trường hợp thất bại.

## 6. TERMINATED không có nghĩa SUCCEEDED

Thread có thể kết thúc vì phương thức run() return bình thường, vì exception không được bắt, hoặc vì code chủ động xử lý yêu cầu hủy rồi return. **Thread.State.TERMINATED** giống nhau trong tất cả các tình huống này.

Nếu không bắt exception ở worker, một exception chưa xử lý sẽ được báo cho uncaught exception handler theo cấu hình, nhưng **thread gọi join() không tự nhận lại exception đó**. Vì vậy, nếu chỉ làm:

~~~java
worker.start();
worker.join();
System.out.println("Success");
~~~

Đây là **đoạn minh họa logic sai** nếu dòng Success có nghĩa tác vụ nghiệp vụ thành công. **worker** phải là đối tượng Thread đã được tạo, và đoạn này cần xử lý hoặc khai báo **InterruptedException**. **join()** chỉ xác nhận hoàn tất vòng đời thread (khi trả về do kết thúc), không xác nhận thành công.

### Ví dụ có ghi nhận FAILED riêng

Tệp **BusinessFailureDemo.java**:

~~~java
import java.util.concurrent.atomic.AtomicReference;

public class BusinessFailureDemo {
    enum JobStatus { PENDING, RUNNING, SUCCEEDED, FAILED }

    static void process(String input) {
        if (input.isBlank()) {
            throw new IllegalArgumentException("empty input");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicReference<JobStatus> status =
                new AtomicReference<>(JobStatus.PENDING);
        AtomicReference<String> error = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            status.set(JobStatus.RUNNING);
            try {
                process("");
                status.set(JobStatus.SUCCEEDED);
            } catch (RuntimeException ex) {
                error.set(ex.getMessage());
                status.set(JobStatus.FAILED);
            }
        }, "validation-worker");

        worker.start();
        worker.join();

        System.out.println("thread = " + worker.getState());
        System.out.println("job = " + status.get());
        System.out.println("error = " + error.get());
    }
}
~~~

~~~bash
javac -encoding UTF-8 BusinessFailureDemo.java
java BusinessFailureDemo
~~~

Stdout:

~~~text
thread = TERMINATED
job = FAILED
error = empty input
~~~

**Tại sao kết quả đúng?** Mã nghiệp vụ cố ý ném lỗi khi chuỗi rỗng. Worker bắt lỗi, lưu mô tả và cập nhật FAILED trước khi thoát. **join()** bảo đảm main chờ hết worker và nhìn thấy các ghi trước khi worker kết thúc; **AtomicReference** giúp định nghĩa và chia sẻ trạng thái rõ ràng.

Trong ứng dụng thật, đừng lưu cả thông báo chứa dữ liệu nhạy cảm vào status log công khai. Đặt chính sách rõ ràng cho loại lỗi nào là retryable và có cần phân biệt **FAILED** với **CANCELLED**, **TIMED_OUT** hoặc **PARTIALLY_COMPLETED**.

## 7. Vì sao không ánh xạ 1 thread = 1 job?

Ví dụ đơn giản khi học **new Thread(runnable)** có thể cho cảm giác mỗi công việc có một thread riêng. Điều đó không đúng nói chung:

- **Một thread thực thi nhiều task tuần tự:** executor có pool worker tái sử dụng thread sau từng job. Worker vẫn sống khi job trước đã SUCCEEDED hoặc FAILED.
- **Một job gồm nhiều bước chạy trên nhiều thread:** khi fan-out, callback hoặc pipeline chuyển giai đoạn, không có một Thread.State đơn lẻ đại diện cho toàn bộ job.
- **Job đang QUEUED nhưng chưa có worker chạy:** business state là QUEUED, dù không tồn tại thread riêng của job.
- **Task đang chờ I/O trên một worker:** worker có thể được quan sát ở các trạng thái Java khác nhau, nhưng nghiệp vụ vẫn RUNNING.
- **Job được lưu bền vững và chờ retry:** một worker đã TERMINATED, nhưng job có thể chuyển RETRYING và tiếp tục trên worker khác.

Đừng dùng thread name, thread ID hay Thread.State như khóa nhận dạng duy nhất cho job. Hãy dùng **jobId/correlationId** riêng, liên kết các lần thực thi vào một công việc nghiệp vụ.

### Future là trạng thái tác vụ ở tầng JDK, không phải Thread.State

Trong **java.util.concurrent**, **Future<T>** biểu diễn kết quả có thể đến sau khi gửi tác vụ:

- **isDone()** là true khi computation đã hoàn tất **bình thường, thất bại hoặc bị hủy**.
- **isCancelled()** báo việc hủy Future thành công, nhưng không hứa side effect đã được rollback.
- **get()** trả kết quả khi hoàn thành bình thường; có thể ném **ExecutionException** khi code task lỗi, **CancellationException** khi bị hủy, hoặc **InterruptedException** khi thread đang chờ bị ngắt.
- Nếu dùng **cancel(true)**, cơ chế có thể gửi interrupt đến thread đang thực thi, nhưng task cần hợp tác; **cancel()** không đảm bảo undo trạng thái đã tác động ra ngoài.

Do đó **Future.isDone() = true** chưa đủ để đánh dấu **SUCCEEDED**.

### Ví dụ Future: kết quả công việc khác vòng đời worker

Tệp **FutureOutcomeDemo.java**:

~~~java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class FutureOutcomeDemo {
    public static void main(String[] args)
            throws InterruptedException, ExecutionException {

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> future = executor.submit(() -> 6 * 7);
            int result = future.get();

            System.out.println("result = " + result);
            System.out.println("future done = " + future.isDone());
            System.out.println("future cancelled = " + future.isCancelled());
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 FutureOutcomeDemo.java
java FutureOutcomeDemo
~~~

Stdout:

~~~text
result = 42
future done = true
future cancelled = false
~~~

**future.get()** xác nhận tác vụ đã trả giá trị 42 hoặc sẽ ném ngoại lệ tương ứng. Kết quả **không suy ra** worker thread đã chết: với **newSingleThreadExecutor()**, worker có thể sống để nhận job tiếp theo cho đến khi executor được shutdown. Ví dụ gọi shutdown để kết thúc tài nguyên một cách có kiểm soát.

Trong hệ thống nghiệp vụ, bạn vẫn cần quy định **khi nào** ghi SUCCEEDED: sau xử lý tính toán, sau commit cơ sở dữ liệu, sau gửi sự kiện, hay sau cả chuỗi bước. Future completion là tín hiệu tầng thực thi, không tự định nghĩa giao dịch nghiệp vụ.

## 8. Trạng thái nghiệp vụ chia sẻ cần đồng bộ hóa

Vì status có thể được đọc/ghi từ nhiều thread, một biến thông thường như:

~~~java
// Mã minh họa thiếu bảo đảm visibility giữa các thread:
JobStatus status = JobStatus.PENDING;
~~~

chưa đủ nếu nhiều thread đọc/ghi không có quan hệ happens-before. **volatile** bảo đảm visibility và thứ tự phù hợp cho đọc/ghi riêng lẻ, nhưng **không làm chuỗi check-then-set nguyên tử**:

~~~java
// Mã minh họa lỗi khi hai thread cùng chạy:
if (status == JobStatus.PENDING) {
    status = JobStatus.RUNNING;
}
~~~

Hai thread đều có thể đọc PENDING và cùng xử lý một job. Một số cách đúng tùy nhu cầu là:

- **AtomicReference.compareAndSet(expected, updated)** để cập nhật có điều kiện nguyên tử trên một biến trong bộ nhớ.
- **synchronized / Lock** khi cần bảo vệ nhiều trường hoặc một bất biến phức tạp.
- **BlockingQueue, CountDownLatch, Future** để trao tác vụ/tín hiệu/kết quả theo hợp đồng của chúng.
- **Transaction và cập nhật có điều kiện ở kho lưu trữ** khi nhiều process hoặc instance cùng xử lý một job; **AtomicReference chỉ bảo vệ đối tượng trong một JVM**, không thay thế đồng bộ hóa giữa các tiến trình.

### Ví dụ: ngăn hai thread cùng chuyển PENDING → RUNNING

Tệp **JobTransitionDemo.java**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class JobTransitionDemo {
    enum JobStatus { PENDING, RUNNING, SUCCEEDED }

    public static void main(String[] args) throws InterruptedException {
        AtomicReference<JobStatus> status =
                new AtomicReference<>(JobStatus.PENDING);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        Runnable requestStart = () -> {
            if (status.compareAndSet(
                    JobStatus.PENDING, JobStatus.RUNNING)) {
                accepted.incrementAndGet();
            } else {
                rejected.incrementAndGet();
            }
        };

        Thread a = new Thread(requestStart, "request-a");
        Thread b = new Thread(requestStart, "request-b");
        a.start();
        b.start();
        a.join();
        b.join();

        if (!status.compareAndSet(
                JobStatus.RUNNING, JobStatus.SUCCEEDED)) {
            throw new IllegalStateException("Unexpected job state");
        }

        System.out.println("accepted = " + accepted.get());
        System.out.println("rejected = " + rejected.get());
        System.out.println("job = " + status.get());
    }
}
~~~

~~~bash
javac -encoding UTF-8 JobTransitionDemo.java
java JobTransitionDemo
~~~

Stdout:

~~~text
accepted = 1
rejected = 1
job = SUCCEEDED
~~~

**Một CAS thành công**, còn CAS kia thấy trạng thái đã khác PENDING và từ chối. Vì chỉ in kết quả tổng hợp sau khi đã join cả hai thread nên stdout không phụ thuộc thread a hay b thắng.

Đây là ví dụ về **chuyển trạng thái đúng luật**; chưa thực thi nội dung business thực tế. Trong job thật, chỉ request được chấp nhận mới được tiến hành xử lý; việc chuyển RUNNING → SUCCEEDED phải xảy ra **sau khi** xử lý thành công. Nếu lỗi, cập nhật FAILED; nếu hủy, áp dụng luật hủy riêng.

### Thiết kế luật chuyển trạng thái

Một state machine nên trả lời được:

| Chuyển trạng thái | Khi nào hợp lệ? |
| --- | --- |
| PENDING → RUNNING | Một worker nhận quyền bắt đầu xử lý |
| RUNNING → SUCCEEDED | Các điều kiện thành công thực sự đã hoàn tất |
| RUNNING → FAILED | Có lỗi không thể hoàn tất theo hợp đồng hiện tại |
| RUNNING → CANCEL_REQUESTED | Một tác nhân yêu cầu hủy công việc đang chạy |
| CANCEL_REQUESTED → CANCELLED | Worker/giao thức xác nhận đã dừng theo chính sách |
| FAILED → RETRYING | Có chính sách retry và hạn mức lần thử |
| RETRYING → RUNNING | Một attempt mới được cấp quyền thực thi |

Đây là các ví dụ quy tắc **do ứng dụng đặt ra**, không phải quy tắc bắt buộc của JDK. Nếu retry, cần phân biệt **jobId** (danh tính công việc xuyên nhiều lần thử) với **attemptId** (danh tính từng lượt chạy). Tránh việc completion đến muộn từ một attempt cũ ghi đè trạng thái của attempt mới: dùng version, attempt token hoặc update có điều kiện phù hợp.

## 9. Hủy và timeout: trạng thái kỹ thuật khác quyết định nghiệp vụ

Khi gọi **worker.interrupt()**, ta gửi một yêu cầu ngắt. Điều đó **không tự chứng minh** business state phải là CANCELLED. Worker có thể:

- Chưa quan sát cờ interrupt, hoặc đang ở bước không thể bị interrupt.
- Bắt InterruptedException rồi cleanup và thoát.
- Hoàn thành công việc gần như đồng thời với yêu cầu hủy.
- Đã tạo side effect trước khi hủy, cần rollback/bù trừ theo luật nghiệp vụ.

Một mô hình rõ ràng có thể dùng **CANCEL_REQUESTED** trước, rồi chỉ chuyển **CANCELLED** khi có xác nhận phù hợp. Nếu công việc đã thành công trước yêu cầu hủy, luật chuyển trạng thái có thể từ chối hủy hoặc chỉ ghi nhận yêu cầu đến muộn.

**TIMED_WAITING** không phải **TIMED_OUT**. Thread đang **Thread.sleep(1000)** ở TIMED_WAITING nhưng task chưa chắc quá hạn. Ngược lại, business job có thể đã quá deadline theo policy trong khi worker vẫn RUNNABLE, BLOCKED hoặc đang I/O.

Nếu dùng **Future.get(timeout, unit)**, khi timeout nó ném **TimeoutException** cho **lời gọi chờ**; điều này **không tự dừng** computation và **không tự hủy Future**. Cần chính sách rõ ràng nếu muốn cancel, retry hoặc đánh dấu TIMED_OUT.

### Sơ đồ hai trục trạng thái

~~~text
Trục thực thi (JVM):
Thread NEW -> RUNNABLE <-> WAITING/TIMED_WAITING/BLOCKED -> TERMINATED

Trục nghiệp vụ (do ứng dụng định nghĩa):
Job PENDING -> RUNNING -> SUCCEEDED
                      \-> FAILED
                      \-> CANCEL_REQUESTED -> CANCELLED

Một job có thể đổi thread/attempt; một thread có thể đổi job.
Không có phép biến đổi tự động từ trục JVM sang trục nghiệp vụ.
~~~

Khi cần trạng thái timeout, cancellation hoặc retry có độ tin cậy, hãy cập nhật business state thông qua các bước nhất quán, có guard điều kiện; đừng kiểm tra Thread.State rồi tự động phỏng đoán job outcome.

## 10. Nhớ đúng về happens-before và công bố kết quả

Nếu một worker cập nhật các trường chia sẻ rồi kết thúc, **Thread.join()** trả về do thread kết thúc cung cấp quan hệ *happens-before* từ các hành động của worker đến hành động tiếp theo của thread đã join. Điều này giúp thread gọi đọc kết quả sau khi job hoàn thành.

Ngoài ra:

- **Thread.start()** thiết lập quan hệ happens-before từ các hành động của thread gọi trước start() đến hành động trong thread được khởi động.
- **CountDownLatch.countDown()** cùng **await()** thành công cho phép công bố kết quả trước tín hiệu.
- **AtomicReference** cung cấp các thao tác đọc/ghi/CAS an toàn tương ứng với hợp đồng bộ nhớ của lớp atomic.
- **volatile** và **synchronized** có hợp đồng visibility/happens-before, nhưng cần thiết kế đúng phạm vi dữ liệu được chia sẻ.

Không nên nghĩ **Thread.sleep()**, **Thread.yield()**, hoặc chỉ kiểm tra **getState()** tạo ra quan hệ happens-before cho dữ liệu nghiệp vụ. Quan sát “dường như chạy đúng” qua nhiều lần thử không chứng minh một giao thức race-free.

Mặt khác, **join()** chỉ chờ **thread**. Nếu worker chỉ đặt một task vào queue hoặc kích hoạt bước bất đồng bộ rồi kết thúc, join() của worker ấy không nói toàn bộ job nghiệp vụ đã xong.

## 11. Phân biệt trạng thái quan sát với nguồn sự thật

Một hệ thống thường có ít nhất ba lớp thông tin:

1. **Thread.State**: snapshot kỹ thuật, phù hợp thread dump, chẩn đoán BLOCKED/WAITING và phân tích nghẽn.
2. **Task execution outcome**: giá trị hoặc exception của Future, latch completion, sự kiện từ worker; liên quan một lần chạy cụ thể.
3. **Business state**: kết quả nghiệp vụ chính thức theo hợp đồng (có thể được lưu bền vững), bao gồm retry, cancellation, timeout, xử lý bù trừ.

Khi cần hiển thị cho người dùng, API nên trả về **business state** với **jobId** và thông tin lỗi an toàn; không đưa Thread.State làm trạng thái đơn hàng, xử lý dữ liệu hoặc transaction.

Đối với job chạy lâu hoặc cần sống sót qua restart, **status chỉ nằm trong AtomicReference sẽ mất khi process thoát**. Cần lưu trạng thái theo transaction/định dạng phù hợp và giải quyết tình huống crash giữa “đã làm side effect” và “đã cập nhật trạng thái”. Thiết kế idempotency, transaction boundary và retry có kiểm soát để tránh thực hiện nghiệp vụ trùng lặp. Những vấn đề này độc lập với việc JVM đang báo Thread.State nào.

### Một bảng log hữu ích

Thay vì chỉ log:

~~~text
thread=worker-1 state=RUNNABLE
~~~

hãy cân nhắc log sự kiện ở tầng task, chẳng hạn:

~~~text
jobId=import-842 attempt=2 status=RUNNING step=VALIDATING
jobId=import-842 attempt=2 status=FAILED reason=INVALID_INPUT
~~~

Các dòng trên là **ví dụ cấu trúc log**, không phải kết quả của một chương trình cụ thể. Một log vận hành có thể bổ sung timestamp, worker/thread name và correlationId. Không dùng thứ tự log từ các thread như một chứng cứ tuyệt đối về happens-before khi không có đồng bộ hóa.

## 12. Các lỗi tư duy thường gặp

| Suy luận sai | Cách hiểu đúng |
| --- | --- |
| **Thread.State.RUNNABLE** tức là job đang RUNNING | RUNNABLE chỉ là trạng thái lịch chạy của thread; một thread có thể đang làm nhiều nhiệm vụ khác nhau |
| **Thread.State.WAITING** tức là job nghiệp vụ đang PENDING | Job RUNNING vẫn có thể đợi latch, condition hoặc kết quả I/O |
| **Thread.State.TERMINATED** tức là SUCCEEDED | Thread có thể kết thúc vì exception, hủy hoặc chỉ hoàn tất việc gửi task khác |
| **Thread.sleep()** chứng minh job đã đợi đủ, nên mark SUCCESS | Sleep chỉ tạm dừng thread; không chứng minh side effect hay điều kiện nghiệp vụ |
| **Future.isDone()** tức là SUCCEEDED | Future done bao gồm cả thất bại và hủy |
| **Future.get(timeout)** timeout nghĩa là worker đã dừng | Timeout của caller không tự cancel task đang chạy |
| Interrupt đã gửi nghĩa là job CANCELLED | Interrupt là tín hiệu hợp tác, cancellation cần luật xác nhận |
| Một worker thread tương đương một job | Thread pool tái sử dụng worker; job có thể có nhiều attempt |
| **volatile status** đủ ngăn hai worker cùng nhận job | volatile bảo đảm visibility, không nguyên tử hóa check-then-set |
| **AtomicReference** đủ bảo vệ job trong nhiều JVM | AtomicReference chỉ đồng bộ hóa trong một process; cần cơ chế ở kho dữ liệu |
| Kiểm tra **getState()** trong vòng lặp là một cách chờ chắc chắn | getState() là snapshot; dùng join, Future, latch hoặc giao thức trạng thái có đồng bộ hóa |
| **FAILED** rồi retry nên có thể ghi SUCCESS bất kỳ lúc nào | Cần quản lý version/attempt và transition hợp lệ để tránh kết quả cũ ghi đè |

## 13. Tự kiểm tra

**Câu 1.** Một thread đang chờ **CountDownLatch.await()**. Vì sao công việc vẫn có thể là RUNNING?  
**Trả lời:** Thread đang ở một điểm chờ kỹ thuật để tiếp tục giai đoạn đang chạy. RUNNING là nhãn nghiệp vụ cho toàn bộ giai đoạn, không phải ảnh chụp sử dụng CPU.

**Câu 2.** Worker đã TERMINATED, vậy kết quả nghiệp vụ chắc chắn là gì?  
**Trả lời:** Không xác định chỉ từ Thread.State. Cần biết worker đã trả kết quả, thất bại, bị hủy hay đã giao việc cho giai đoạn khác.

**Câu 3.** Hai thread cùng nhận một job PENDING bằng check-then-set thường có rủi ro gì?  
**Trả lời:** Cả hai có thể đọc PENDING và đều thực thi. Dùng cập nhật có điều kiện nguyên tử như CAS trong một JVM; nếu có nhiều tiến trình phải có điều kiện và transaction ở hệ thống lưu trữ.

**Câu 4.** **Future.isDone() == true** có nghĩa công việc SUCCEEDED không?  
**Trả lời:** Không. Future cũng hoàn tất khi computation lỗi hoặc bị hủy. Kiểm tra kết quả của get() và áp dụng luật nghiệp vụ.

**Câu 5.** **Future.get(1, TimeUnit.SECONDS)** ném TimeoutException có chứng minh worker đã bị dừng?  
**Trả lời:** Không. Đó là việc người chờ đã hết thời gian; computation có thể vẫn chạy.

**Câu 6.** Khi nào nên dùng Thread.getState(), khi nào nên đọc JobStatus?  
**Trả lời:** getState() dùng để quan sát/chẩn đoán vòng đời thread tại một thời điểm; JobStatus dùng để biết tiến độ và kết quả công việc theo hợp đồng ứng dụng.

**Câu 7.** Vì sao trạng thái nghiệp vụ lưu trong bộ nhớ chưa đủ cho job cần chạy lại sau crash?  
**Trả lời:** Trạng thái biến mất cùng JVM, không tự đảm bảo transaction hay tránh xử lý trùng. Cần nơi lưu bền vững, quy tắc transition và idempotency.

## 14. Kết luận

**Thread.State trả lời “luồng thực thi đang thế nào?”; business state trả lời “công việc đã đi đến đâu và kết quả ra sao?”** Hai trạng thái có thể diễn tiến độc lập. Hãy dùng **Thread.getState()** để quan sát/chẩn đoán, dùng **join()/Future/latch** để chờ và thu kết quả thực thi, và dùng **state machine nghiệp vụ** có đồng bộ hóa/transaction tương ứng để xác định trạng thái thực sự của công việc.


</details>

- [Quay lại đầu trang](#back-to-top)
