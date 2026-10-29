<a id="back-to-top"></a>

# Thread API

## Menu
- [Tạo thread bằng Thread và Runnable](#thread-api-001)
- [Phân biệt gọi start() và run()](#thread-api-002)
- [Đọc thông tin currentThread(), name và ID](#thread-api-003)
- [Thread priority: ý nghĩa và giới hạn bảo đảm](#thread-api-004)
- [Platform thread và chi phí tạo tài nguyên](#thread-api-005)
- [Daemon thread và user thread khi JVM kết thúc](#thread-api-006)
- [Đặt tên luồng và thiết lập UncaughtExceptionHandler](#thread-api-007)
- [ThreadFactory để chuẩn hóa cách tạo luồng](#thread-api-008)
- [Runnable, Callable và cách truyền kết quả tác vụ](#thread-api-009)
- [sleep(), yield() và các giới hạn khi điều phối](#thread-api-010)
- [join() để chờ kết thúc một thread](#thread-api-011)
- [Phân biệt công việc của thread và vòng đời task](#thread-api-012)
- [Thread.Builder: tạo platform thread, virtual thread và ThreadFactory theo cấu hình](#thread-api-013)

## <a id="thread-api-001">Tạo thread bằng Thread và Runnable</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo thread bằng Thread và Runnable trong Java

## 1. Mục tiêu và bối cảnh

Sau bài học, bạn có thể:

- Tạo, đặt tên, khởi chạy và chờ kết thúc một thread bằng lớp **Thread** và giao diện **Runnable**.
- Phân biệt **gọi run()** với **gọi start()**, hiểu khi nào mã thực thi trên thread hiện tại hoặc thread mới.
- Chọn giữa kế thừa Thread, triển khai Runnable và lambda; hiểu quan hệ giữa **công việc** và **thread thực thi công việc**.
- Quản lý vòng đời cơ bản, xử lý ngắt bằng **interrupt()**, và nhận diện rủi ro khi nhiều thread chia sẻ dữ liệu.
- Đọc đúng kết quả chương trình có thread: thứ tự thực thi thường không được bảo đảm và kết quả phụ thuộc vào đồng bộ hóa.

Phạm vi là **Java concurrency nói chung** qua API nền tảng của JDK; không bàn đến cơ chế quản lý thread, tác vụ bất đồng bộ hay cấu hình của Spring.

Một **thread** là luồng thực thi độc lập trong cùng tiến trình Java. Các thread chia sẻ heap và các đối tượng dùng chung, nhưng mỗi thread có call stack riêng. Khi nhiều thread cùng tồn tại, các công việc có thể *xen kẽ* (concurrency); chúng có thể thực sự chạy *song song* (parallelism) nếu tài nguyên CPU và bộ lập lịch cho phép. Không thể suy ra thứ tự chạy từ thứ tự gọi **start()**.

## 2. Hai API cốt lõi: Thread và Runnable

**java.lang.Thread** biểu diễn một thread, cung cấp các thao tác như **start()**, **join()**, **interrupt()**, **isAlive()**, **getState()** và **getName()**. Khi gọi **start()** trên đối tượng Thread ở trạng thái NEW, JVM sắp xếp cho phương thức **run()** của thread đó chạy trên luồng thực thi mới. **start()** trở về mà không đợi công việc hoàn thành.

**java.lang.Runnable** là functional interface có đúng một phương thức trừu tượng:

~~~java
@FunctionalInterface
public interface Runnable {
    void run();
}
~~~

Đây là hình thức rút gọn để diễn giải hợp đồng API; **java.lang.Runnable** đã có sẵn trong JDK, không cần khai báo lại. Một **Runnable** mô tả việc cần làm, nhưng tự nó **không tạo hoặc khởi động thread**. Ta đưa Runnable vào một Thread, rồi gọi **start()** trên Thread đó.

Sơ đồ quan hệ:

~~~text
Runnable task ──(truyền vào constructor)──> Thread worker
                                            │
                                         start()
                                            │
                                  thread mới gọi task.run()
~~~

Điểm cần nhớ: **Thread là phương tiện thực thi; Runnable là phần công việc**. Thread còn triển khai Runnable, vì thế có thể override **Thread.run()** để mô tả công việc trực tiếp.

## 3. Ba cách tạo và chạy thread

Ví dụ sau chạy độc lập trên **Java 17+** và minh họa cả ba cách: kế thừa Thread, triển khai Runnable, dùng lambda làm Runnable.

Tệp **ThreadCreationDemo.java**:

~~~java
public class ThreadCreationDemo {

    static class ByThread extends Thread {
        ByThread() {
            super("class-thread");
        }

        @Override
        public void run() {
            System.out.println(getName() + ": chạy bằng Thread");
        }
    }

    static class ByRunnable implements Runnable {
        @Override
        public void run() {
            System.out.println(
                Thread.currentThread().getName() + ": chạy bằng Runnable"
            );
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread first = new ByThread();
        first.start();
        first.join();

        Thread second = new Thread(new ByRunnable(), "runnable-thread");
        second.start();
        second.join();

        Runnable task = () -> System.out.println(
            Thread.currentThread().getName() + ": chạy bằng lambda"
        );
        Thread third = new Thread(task, "lambda-thread");
        third.start();
        third.join();

        System.out.println("main: hoàn tất");
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 ThreadCreationDemo.java
java ThreadCreationDemo
~~~

Kết quả khi terminal hiển thị Unicode đúng:

~~~text
class-thread: chạy bằng Thread
runnable-thread: chạy bằng Runnable
lambda-thread: chạy bằng lambda
main: hoàn tất
~~~

Ví dụ này có thứ tự kết quả xác định **vì mỗi lần start() đều được theo sau bởi join() trước khi bắt đầu thread tiếp theo**. Nếu khởi động cả ba thread trước rồi mới join, các dòng từ ba thread có thể xuất hiện theo nhiều thứ tự.

### 3.1. Kế thừa Thread

~~~java
class Worker extends Thread {
    @Override
    public void run() {
        // Công việc chạy khi gọi start() trên Worker.
    }
}

Thread worker = new Worker();
worker.start();
~~~

Đoạn trên minh họa cách dùng; lớp Worker chưa có phương thức **main()** để tự chạy như một ứng dụng độc lập. Cách này đơn giản khi cần một loại thread có hành vi riêng, nhưng gắn mã công việc với chính đối tượng Thread. Java chỉ hỗ trợ kế thừa một lớp, nên lớp đã **extends Thread** không thể extends một lớp khác.

### 3.2. Triển khai Runnable

~~~java
class ReportTask implements Runnable {
    @Override
    public void run() {
        System.out.println("Building report");
    }
}

Runnable task = new ReportTask();
Thread worker = new Thread(task, "report-worker");
worker.start();
~~~

Đây cũng là đoạn minh họa cần được đặt trong chương trình hợp lệ. Cách **implements Runnable** tách việc cần làm khỏi cách chạy nó; cùng một loại task có thể được dùng để tạo nhiều Thread. Một đối tượng Runnable cụ thể cũng có thể được truyền cho nhiều Thread, nhưng khi đó **trạng thái của chính đối tượng Runnable sẽ được chia sẻ**.

### 3.3. Lambda

Vì Runnable là functional interface, có thể viết ngắn gọn:

~~~java
Runnable task = () -> System.out.println(
    "Running on " + Thread.currentThread().getName()
);
Thread worker = new Thread(task, "lambda-worker");
worker.start();
~~~

Lambda phù hợp với công việc ngắn. Biến cục bộ được lambda capture phải là **final hoặc effectively final**; quy tắc này không làm đối tượng mutable được capture trở nên thread-safe.

### Chọn cách nào?

| Tiêu chí | extends Thread | implements Runnable / lambda |
| --- | --- | --- |
| Vai trò | Định nghĩa hành vi ngay trong lớp thread | Định nghĩa task độc lập |
| Khả năng tái sử dụng công việc | Gắn với Thread cụ thể | Có thể tạo nhiều Thread hoặc chuyển cho executor |
| Kế thừa lớp khác | Không thể extends thêm lớp khác | Có thể extends lớp khác |
| Dễ tách trách nhiệm | Thấp hơn | Cao hơn |
| Khuyến nghị mặc định | Khi thật sự cần tùy biến Thread | **Ưu tiên Runnable** cho tác vụ thông thường |

Cả hai con đường cuối cùng vẫn cần một **Thread.start()** để có thread mới khi dùng trực tiếp API cổ điển.

## 4. start() khác run() như thế nào?

**run() là lời gọi phương thức Java thông thường**. Khi gọi **worker.run()** trực tiếp, code chạy đồng bộ trên thread đang gọi; không có thread mới. **start()** mới yêu cầu JVM bắt đầu một thread khác và sau đó chạy **run()** trên thread đó.

Tệp **StartVsRunDemo.java**:

~~~java
public class StartVsRunDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(
            () -> System.out.println(
                "task: " + Thread.currentThread().getName()
            ),
            "worker-1"
        );

        worker.run();   // Chạy trực tiếp trên main.
        worker.start(); // Bắt đầu thread mới.
        worker.join();  // Chờ worker kết thúc.
    }
}
~~~

~~~bash
javac -encoding UTF-8 StartVsRunDemo.java
java StartVsRunDemo
~~~

Stdout:

~~~text
task: main
task: worker-1
~~~

Có thể gọi **run()** trực tiếp nhiều lần giống một phương thức bình thường, nhưng **một đối tượng Thread chỉ được gọi start() một lần**. Gọi lại **start()** khi nó đã được khởi động, kể cả đã kết thúc, sẽ ném **IllegalThreadStateException**. Muốn chạy lại task, tạo **Thread mới**; không thể reset Thread cũ về NEW.

## 5. Vòng đời và điều phối thread

Một số trạng thái trả về bởi **Thread.getState()**:

| Trạng thái | Ý nghĩa thực tế |
| --- | --- |
| **NEW** | Đã tạo đối tượng Thread nhưng chưa gọi start(). |
| **RUNNABLE** | Đủ điều kiện chạy hoặc đang chạy trên JVM; không đồng nghĩa chắc chắn đang chiếm CPU. |
| **BLOCKED** | Chờ giành monitor lock khi đi vào khối/phương thức synchronized. |
| **WAITING** | Đang chờ không có giới hạn thời gian, ví dụ join() không timeout hoặc Object.wait() không timeout. |
| **TIMED_WAITING** | Đang chờ có thời hạn, ví dụ Thread.sleep(...) hoặc join(timeout). |
| **TERMINATED** | Phương thức run() đã kết thúc, do hoàn thành hoặc lỗi không được bắt. |

Luồng phổ biến:

~~~text
NEW --start()--> RUNNABLE --run() kết thúc--> TERMINATED
                     │
           có thể chuyển qua
           BLOCKED / WAITING / TIMED_WAITING
                     │
                 RUNNABLE
~~~

**getState() chỉ là ảnh chụp tức thời**, phù hợp cho quan sát hoặc chẩn đoán, không dùng để xây dựng giao thức đồng bộ. Bộ lập lịch không hứa thread nào sẽ chạy trước, chạy bao lâu, hoặc chạy song song.

**Thread.currentThread()** trả về đối tượng Thread đang thực thi mã, còn **getName()** hỗ trợ phân biệt thread trong log. Có thể đặt tên qua constructor hoặc **setName(...)**, tốt nhất trước **start()**. Tên giúp quan sát chứ không quyết định thứ tự lập lịch.

### Chờ kết thúc với join()

**join()** khiến thread gọi chờ thread đích hoàn thành. Với mã:

~~~java
Thread worker = new Thread(() -> doWork());
worker.start();
worker.join();
consumeResult();
~~~

Đây là ví dụ luồng điều khiển, trong đó **doWork()** và **consumeResult()** là phương thức giả định cần được định nghĩa. Sau khi **join() trả về bình thường do worker kết thúc**, các hiệu ứng bộ nhớ của worker xảy ra-before (happens-before) các thao tác tiếp theo của thread đã join. Không có nghĩa là mọi việc truy cập cùng một biến đều an toàn *trong khi cả hai thread đang chạy*.

**join()** có thể ném **InterruptedException** khi thread đang đợi bị ngắt. Bản có timeout, như **join(1000)**, có thể trở về khi worker vẫn còn sống: nếu cần chắc chắn đã kết thúc, kiểm tra **isAlive()** hoặc sử dụng giao thức chờ phù hợp.

## 6. Thread chia sẻ dữ liệu: nguy cơ race condition

Tạo nhiều thread không tự làm chương trình thread-safe. Các thread dùng chung heap, vì vậy phép tăng **count++** trên một biến thường gồm đọc, cộng, ghi; đây không phải một thao tác nguyên tử. Nếu hai thread cập nhật đồng thời mà không có đồng bộ hóa, có thể xảy ra **lost update** và vấn đề hiển thị bộ nhớ (*visibility*). Đánh dấu biến là **volatile** chỉ giải quyết một số yêu cầu hiển thị/thứ tự, **không biến count++ thành atomic**.

Ví dụ đúng với **AtomicInteger**, dùng chung một đối tượng Runnable:

Tệp **SharedCounterDemo.java**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class SharedCounterDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();

        Runnable increment = () -> {
            for (int i = 0; i < 10_000; i++) {
                total.incrementAndGet();
            }
        };

        Thread first = new Thread(increment, "counter-1");
        Thread second = new Thread(increment, "counter-2");

        first.start();
        second.start();

        first.join();
        second.join();

        System.out.println("total=" + total.get());
    }
}
~~~

~~~bash
javac -encoding UTF-8 SharedCounterDemo.java
java SharedCounterDemo
~~~

Stdout luôn là:

~~~text
total=20000
~~~

**Vì sao đúng?** Hai Thread chạy cùng một Runnable, cả hai capture cùng **AtomicInteger**. **incrementAndGet()** thực hiện tăng nguyên tử; **join()** bảo đảm main đợi cả hai thread kết thúc rồi mới đọc. Không được thay bằng **int[] total = {0}; total[0]++** và kỳ vọng luôn được 20.000: đó là data race.

Các cách bảo vệ trạng thái chia sẻ trong Java gồm **synchronized**, lock, atomic classes và các cấu trúc concurrent. Chọn cơ chế phù hợp với thao tác cần bảo vệ; **join()** chỉ giải quyết chờ hoàn thành và công bố kết quả khi thread kết thúc, không bảo vệ một chuỗi cập nhật đồng thời đang diễn ra.

## 7. Ngắt thread đúng cách với interrupt()

**interrupt()** gửi tín hiệu yêu cầu ngắt, **không ép buộc dừng thread ngay lập tức** và không thay thế cơ chế hủy an toàn. Mã đang chạy cần tự hợp tác bằng cách kiểm tra **isInterrupted()** hoặc phản ứng với **InterruptedException** ở các thao tác chờ có thể bị ngắt (như sleep, wait, join).

Tệp **InterruptDemo.java**:

~~~java
public class InterruptDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1_000);
                }
            } catch (InterruptedException e) {
                // sleep() xóa trạng thái interrupt khi ném exception.
                Thread.currentThread().interrupt();
            }

            System.out.println(
                "worker interrupted: "
                    + Thread.currentThread().isInterrupted()
            );
        }, "interruptible-worker");

        worker.start();
        worker.interrupt();
        worker.join();
    }
}
~~~

~~~bash
javac -encoding UTF-8 InterruptDemo.java
java InterruptDemo
~~~

Stdout:

~~~text
worker interrupted: true
~~~

Kết quả này vẫn đúng nếu interrupt đến trước khi worker bắt đầu ngủ hoặc trong lúc ngủ. Nếu được ngắt trong **sleep()**, phương thức ném **InterruptedException** và xóa interrupt status; nhánh **catch** đặt lại cờ để không làm mất tín hiệu. Nếu cờ đã được đặt trước lần kiểm tra điều kiện, vòng lặp sẽ thoát. Trong thực tế, sau khi nhận yêu cầu ngắt, task nên giải phóng tài nguyên rồi kết thúc hợp tác; không nên tiếp tục vô hạn với cờ interrupt bị bỏ qua.

Lưu ý:

- **isInterrupted()** kiểm tra cờ của một Thread mà không xóa cờ.
- **Thread.interrupted()** là phương thức static: kiểm tra **và xóa** cờ của thread hiện tại; không dùng thay thế một cách vô ý.
- **InterruptedException** là checked exception. Phương thức **Runnable.run()** không khai báo checked exception, nên phải bắt trong run(), chuyển hóa phù hợp hoặc tổ chức lại phương thức.
- Khi bắt InterruptedException mà không thể xử lý đầy đủ, thực hành phổ biến là khôi phục cờ bằng **Thread.currentThread().interrupt()** rồi thoát hoặc chuyển quyền xử lý lên tầng phù hợp. Ở phương thức có **throws InterruptedException**, có thể để exception truyền lên thay vì nuốt lỗi.

## 8. Lỗi trong thread và vấn đề kết thúc chương trình

Một exception không được bắt trong **run()** kết thúc thread đó, và mặc định được báo qua uncaught-exception handler của thread/thread group. **join() không tự ném lại lỗi của worker trên main**. Nếu cần ghi nhận lỗi có kiểm soát, thiết lập handler trước khi start:

~~~java
Thread worker = new Thread(() -> {
    throw new IllegalStateException("task failed");
}, "failing-worker");

worker.setUncaughtExceptionHandler((thread, error) ->
    System.err.println(thread.getName() + ": " + error.getMessage())
);
worker.start();
~~~

Đoạn này minh họa callback xử lý lỗi và không có main() riêng. Handler phù hợp để quan sát, nhưng không có cơ chế mặc định chuyển kết quả/lỗi về người tạo thread. Với tác vụ cần **giá trị trả về** hoặc truyền exception có cấu trúc, xem **Callable/Future/ExecutorService** ở phần học tiếp theo: **Runnable.run() trả void** và không cho phép checked exception thoát trực tiếp.

Java có **user thread** (mặc định với thread thông thường) và **daemon thread**. JVM có thể kết thúc khi chỉ còn daemon threads; vì vậy không dùng daemon cho công việc mà bắt buộc phải hoàn thành như ghi dữ liệu quan trọng. Có thể gọi **setDaemon(true)** trước **start()**, nhưng gọi sau khi thread đã bắt đầu sẽ gây **IllegalThreadStateException**.

Không nên gọi **Thread.stop()**, **suspend()** hoặc **resume()** để điều khiển vòng đời: các API cũ đó không an toàn/đã bị loại bỏ hoặc deprecated tùy phiên bản JDK. Dùng cờ dừng có bảo đảm bộ nhớ, **interrupt()**, và quản lý tài nguyên có kiểm soát.

## 9. Khi nào nên tạo Thread trực tiếp?

Tạo Thread trực tiếp hữu ích khi học nguyên lý, chạy một tác vụ ngắn và số lượng thread nhỏ, có vòng đời rõ ràng. Trong ứng dụng có nhiều tác vụ, **một Thread cho mỗi request/tác vụ** có thể tạo quá nhiều platform threads, tăng chi phí stack, context switching và khó quản lý shutdown.

API cấp cao hơn của Java có **ExecutorService** để quản lý việc gửi và thực thi tác vụ, **Future** để quan sát kết quả, và từ **Java 21** có **virtual threads** (ví dụ **Thread.ofVirtual().start(task)**). Đây là các bước phát triển trên kiến thức nền của **Thread** và **Runnable**, không thay đổi quy tắc rằng task cần được lên lịch thực thi và dữ liệu chia sẻ phải được bảo vệ. Bài hiện tại tập trung vào Thread truyền thống cùng Runnable.

## 10. Những nhầm lẫn thường gặp

| Nhầm lẫn | Điều đúng |
| --- | --- |
| **new Thread(task)** đã tạo luồng chạy ngay | Mới tạo đối tượng ở trạng thái NEW; cần **start()**. |
| Gọi **run()** tạo thread mới | **run()** chạy trên thread gọi; **start()** mới khởi động thread mới. |
| Mỗi Runnable luôn tương ứng một thread | Runnable chỉ là task; có thể truyền một Runnable cho nhiều Thread, hoặc dùng cách thực thi khác. |
| **start()** chạy ngay lập tức và theo đúng thứ tự được gọi | JVM/OS quyết định lịch; không có cam kết thứ tự thực thi. |
| Sau **sleep(...)** thì thread khác chắc chắn chạy hoặc hoàn thành | sleep chỉ tạm dừng thread hiện tại, không cấp quyền/thứ tự cho thread khác. |
| **join()** tự làm mọi thao tác trên dữ liệu chia sẻ an toàn | join đợi kết thúc và cung cấp quan hệ happens-before khi hoàn thành; thao tác đồng thời vẫn cần đồng bộ hóa. |
| **volatile int count** làm **count++** atomic | volatile không biến chuỗi read-modify-write thành một thao tác nguyên tử. |
| **interrupt()** giết thread | interrupt chỉ yêu cầu ngắt; task phải xử lý tín hiệu hợp tác. |
| Exception trong worker tự được ném ra ở main | Lỗi không tự truyền qua **join()**. |
| Thread đã TERMINATED có thể **start()** lại | Mỗi đối tượng Thread chỉ start một lần. |

## 11. Checklist thực hành

1. Viết task trong **Runnable** (class hoặc lambda) khi không cần một lớp Thread chuyên biệt.
2. Tạo **new Thread(task, "tên-ý-nghĩa")**, rồi gọi **start()**; không gọi **run()** nếu muốn chạy trên thread khác.
3. Quyết định rõ ai chịu trách nhiệm chờ, hủy, theo dõi lỗi và giải phóng tài nguyên; dùng **join()** khi thật sự cần chờ hoàn thành.
4. Xác định đối tượng mutable nào bị nhiều thread chia sẻ. Dùng **synchronized**, **Lock**, **AtomicInteger** hoặc thiết kế không chia sẻ trạng thái tùy nhu cầu.
5. Khi công việc có thể bị hủy, xử lý **interrupt()** và **InterruptedException** hợp tác; tránh vòng lặp chờ bận, tránh bỏ qua ngắt.
6. Không viết chương trình dựa vào thứ tự các dòng log từ nhiều thread, thời gian **sleep()**, hoặc ảnh chụp **getState()**.

### Tự kiểm tra

**Câu 1.** Nếu viết **Thread t = new Thread(task); t.run();**, có thread mới không?  
**Trả lời:** Không. **task.run()** được thực hiện trên thread gọi **t.run()**.

**Câu 2.** Hai Thread nhận cùng một instance Runnable thì có cùng call stack không?  
**Trả lời:** Không. Mỗi thread có stack riêng, nhưng hai thread tham chiếu đến **cùng đối tượng Runnable** và các đối tượng chung mà task sử dụng.

**Câu 3.** Có được gọi **start()** trên cùng Thread sau khi **join()** xong không?  
**Trả lời:** Không. Tạo một Thread mới nếu cần chạy lại công việc.

**Câu 4.** Vì sao kết quả **count++** từ hai thread có thể sai ngay cả khi main gọi **join()** cả hai?  
**Trả lời:** Phép tăng không atomic nên hai thread có thể ghi đè cập nhật của nhau trước khi kết thúc. **join()** không sửa các cập nhật đã mất; dùng đồng bộ hóa hoặc phép tăng nguyên tử.

**Câu 5.** Khi worker đang **sleep()** bị ngắt, nên lưu ý điều gì?  
**Trả lời:** **sleep()** ném **InterruptedException** và xóa interrupt status; mã cần xử lý ngắt hợp tác, thường khôi phục cờ nếu không chuyển exception lên được, rồi thoát phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-api-002">Phân biệt gọi start() và run()</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt gọi `Thread.start()` và `Thread.run()` trong Java

## 1. Cùng một phương thức `run`, nhưng hai cách gọi có ý nghĩa khác nhau

Một `Thread` đại diện cho một luồng thực thi Java. Công việc mà luồng sẽ thực hiện thường nằm trong `Runnable.run()` hoặc phương thức `Thread.run()` được ghi đè. Điều dễ nhầm lẫn là **gọi `run()` trực tiếp không tạo hoặc khởi động luồng mới**.

- **`thread.start()`**: yêu cầu JVM khởi động luồng được biểu diễn bởi đối tượng `thread`. Khi luồng được lên lịch, JVM thực hiện `run()` trên **luồng mới**. Phương thức `start()` thường trả về trước khi công việc hoàn tất; người gọi tiếp tục thực thi độc lập với luồng mới.
- **`thread.run()`**: gọi một phương thức Java thông thường trên **luồng đang gọi**. Nội dung `run()` chạy đồng bộ; người gọi chờ lời gọi phương thức trả về trước khi thực hiện câu lệnh tiếp theo. Không có luồng Java mới nào được tạo chỉ vì gọi `run()`.

Sự khác biệt nằm ở **ngữ cảnh thực thi**, không phải ở bản thân nội dung của `run()`. Cùng một `Runnable` có thể chạy trên luồng `main` khi gọi trực tiếp hoặc trên luồng được tạo khi gọi `start()`.

```text
Luồng main
  |
  +-- thread.run() ------------------------+
  |     công việc chạy ngay trên main     |
  |     main đợi công việc hoàn thành      |
  |<--------------------------------------+
  |
  +-- thread.start() -- yêu cầu tạo luồng mới
  |       |
  |       +---- luồng worker chạy run()
  |       |       (được JVM/OS lên lịch)
  |       +---- kết thúc sau đó
  |
  +-- main tiếp tục (có thể chạy xen kẽ với worker)
```

Với platform thread, `start()` khởi động luồng Java gắn với OS thread. Với virtual thread (Java 21+), `start()` khởi động virtual thread do JVM lên lịch lên carrier platform thread. **Cả hai đều là luồng Java riêng** với luồng gọi; còn gọi `run()` trực tiếp vẫn chạy ở luồng hiện tại.

## 2. Ví dụ đầu tiên: xác định chính xác luồng nào thực thi

Tạo tệp `StartVsRunDemo.java`:

```java
public class StartVsRunDemo {
    public static void main(String[] args) throws InterruptedException {
        Runnable job = () -> System.out.println(
                "Job đang chạy trên: " + Thread.currentThread().getName());

        Thread direct = new Thread(job, "worker-direct");
        direct.run();
        System.out.println("Trạng thái direct: " + direct.getState());

        Thread started = new Thread(job, "worker-started");
        started.start();
        started.join();
        System.out.println("Trạng thái started: " + started.getState());
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac StartVsRunDemo.java
java StartVsRunDemo
```

Kết quả khi dùng trình khởi chạy Java thông thường (tên luồng chính là `main`):

```text
Job đang chạy trên: main
Trạng thái direct: NEW
Job đang chạy trên: worker-started
Trạng thái started: TERMINATED
```

Đọc kết quả theo từng bước:

1. `direct` được tạo với tên `worker-direct` nhưng chỉ được gọi `direct.run()`. Lúc thực thi, `Thread.currentThread()` trả về **main**, không phải `direct`. Tên đã đặt cho đối tượng `Thread` không đổi tên luồng thực thi.
2. Vì chưa từng gọi `direct.start()`, trạng thái `direct.getState()` vẫn là `NEW` ngay cả sau khi gọi `direct.run()`.
3. `started.start()` thực sự khởi động một luồng mang tên `worker-started` để chạy `job`. `Thread.currentThread()` lúc đó trả về luồng mới.
4. `started.join()` chờ luồng kết thúc, vì vậy dòng kiểm tra trạng thái sau đó in `TERMINATED`. Không có `join()`, luồng có thể còn đang chạy khi `main` kiểm tra.

Hai dòng đầu ra của ví dụ có thứ tự xác định nhờ `run()` đồng bộ, còn hai dòng cuối có thứ tự xác định nhờ `join()`. Trong một chương trình khác, các câu `println` từ `main` và worker chạy song song có thể xuất hiện theo thứ tự khác nhau; **không dựa vào thứ tự in ngẫu nhiên để kết luận về lập lịch**.

## 3. `run()` trực tiếp là lời gọi đồng bộ

`Thread` thực hiện `Runnable`; khi tạo `new Thread(job)`, công việc `job` được đặt làm mục tiêu của thread. Trong trường hợp thông thường, khi thực thi `Thread.run()`, thread gọi `job.run()`. Nếu bạn gọi `thread.run()` từ `main`, chuỗi lời gọi được thực thi trên stack của `main`:

```text
main()
  -> Thread.run()
       -> Runnable.run()
            -> công việc
  <- return
main() tiếp tục
```

Do đó:

- `run()` trực tiếp **không khởi tạo concurrency** từ đối tượng `Thread` đó.
- Nếu `run()` mất hai giây, luồng gọi bị giữ trong lời gọi khoảng hai giây (trừ khi tác vụ tự giao việc cho luồng khác). Đây là thực thi đồng bộ.
- `Thread.currentThread()` vẫn trả về luồng gọi.
- `run()` là phương thức Java có thể gọi nhiều lần, vì việc gọi nó **không tiêu thụ quyền `start()` một lần duy nhất** của đối tượng `Thread`.
- Exception không được xử lý bên trong lời gọi `run()` sẽ lan theo stack của luồng gọi, như lời gọi phương thức bình thường.

Không nên dùng tên biến `thread`, tên thread đặt trong constructor hoặc sự hiện diện của đối tượng `Runnable` để suy ra chương trình thực sự chạy đa luồng. Quan sát `Thread.currentThread()` và cách khởi động mới cho biết ngữ cảnh thực thi.

## 4. `start()` là thao tác khởi động luồng mới

Với một đối tượng `Thread` đang ở trạng thái `NEW`, gọi `start()` đăng ký yêu cầu khởi động để JVM sắp lịch thực thi `run()`. JVM gọi `run()` trên luồng mới; người lập trình **không cần và không nên gọi `run()` thủ công sau `start()` để khởi động task**.

Điều `start()` **không** cam kết:

- Nó không bảo đảm worker đã thực hiện câu lệnh đầu tiên khi `start()` trả về.
- Nó không bảo đảm worker hoặc `main` luôn chạy trước.
- Nó không chờ công việc hoàn tất. Muốn chờ, dùng `join()` hoặc cơ chế như `Future.get()`.
- Nó không tự bảo đảm việc truy cập dữ liệu chung là an toàn trước race condition.
- Nó không bảo đảm công việc chạy trên một lõi CPU khác; mức song song phụ thuộc bộ lập lịch và phần cứng.

`start()` tạo một nhánh tiến triển đồng thời về mặt thực thi. Trên CPU một lõi, hai luồng vẫn có thể chạy xen kẽ; với nhiều lõi, chúng có thể thực sự chạy song song.

### `start()` chỉ được gọi thành công một lần cho mỗi đối tượng `Thread`

Sau khi một thread đã được khởi động, dù đang chạy hay đã kết thúc, **không được khởi động lại chính đối tượng đó**. Gọi `start()` lần nữa ném `IllegalThreadStateException` (một `RuntimeException`). Nếu muốn chạy lại công việc, tạo đối tượng `Thread` mới hoặc gửi task mới vào executor.

Ví dụ `StartOnceDemo.java`:

```java
public class StartOnceDemo {
    public static void main(String[] args) throws InterruptedException {
        Runnable job = () -> System.out.println("Thực thi công việc");

        Thread direct = new Thread(job);
        direct.run();
        direct.run();
        System.out.println("direct state: " + direct.getState());

        Thread worker = new Thread(job);
        worker.start();
        worker.join();

        try {
            worker.start();
        } catch (IllegalThreadStateException e) {
            System.out.println("Không thể start lại cùng Thread");
        }
    }
}
```

```bash
javac StartOnceDemo.java
java StartOnceDemo
```

Kết quả:

```text
Thực thi công việc
Thực thi công việc
direct state: NEW
Thực thi công việc
Không thể start lại cùng Thread
```

Hai lần `direct.run()` khiến cùng một `Runnable` chạy hai lần trên `main`, nhưng `direct` vẫn `NEW`. `worker.start()` thực thi công việc trên thread mới đúng một lần; lần `start()` thứ hai ném exception. `join()` giúp các dòng của worker hoàn tất trước đoạn bắt exception, nên thứ tự đầu ra xác định.

Đặc biệt, **“có thể gọi `run()` nhiều lần” không có nghĩa “có thể khởi động cùng `Thread` nhiều lần”**. `run` là phương thức, còn `start` kích hoạt vòng đời của đối tượng luồng.

## 5. Phân biệt `Runnable.run()`, `Thread.run()` và ghi đè `run()`

Có hai cách thường gặp để định nghĩa công việc chạy trong thread.

### 5.1. Truyền `Runnable` vào `Thread`

```java
Runnable job = () -> System.out.println(Thread.currentThread().getName());
Thread worker = new Thread(job, "report-worker");
worker.start();
```

Đây là **đoạn mã minh họa** (chưa phải một lớp có `main`). `Runnable` mô tả công việc; `Thread` cung cấp ngữ cảnh thực thi. Khi `start()` khởi động worker, phương thức `Thread.run()` mặc định thực thi `job.run()` trên worker. Cách này tách trách nhiệm mô tả công việc khỏi quản lý vòng đời luồng.

### 5.2. Kế thừa `Thread` và ghi đè `run()`

```java
class ReportThread extends Thread {
    @Override
    public void run() {
        System.out.println(Thread.currentThread().getName());
    }
}
```

Đây là **định nghĩa lớp minh họa**. `new ReportThread().start()` thực thi phương thức `run()` đã ghi đè trên luồng mới. `new ReportThread().run()` chỉ gọi phương thức đã ghi đè trên luồng hiện tại.

Khi một lớp con ghi đè `run()`, việc có truyền thêm `Runnable` vào constructor hay không **không tự bảo đảm mục tiêu `Runnable` đó sẽ được gọi**: phương thức `run()` của lớp con quyết định có gọi `super.run()` hoặc gọi target hay không. Khi không cần tùy biến cơ chế quản lý thread, thiết kế dựa trên `Runnable`/`Callable` và executor thường đơn giản, dễ tái sử dụng hơn.

### 5.3. `Thread.currentThread()` không phải biến `this`

Trong phương thức `run()` của lớp `ReportThread`:

- `this` chỉ đối tượng `ReportThread` đang chứa phương thức.
- `Thread.currentThread()` chỉ thread **đang thực thi câu lệnh**.

Sau `start()`, thông thường hai tham chiếu đó cùng chỉ worker. Khi `main` gọi `worker.run()` trực tiếp, `this` vẫn là `worker` nhưng `Thread.currentThread()` là `main`. Đây là cách kiểm tra chính xác nhất khi học sự khác biệt giữa hai phương thức.

## 6. Đồng bộ trình tự bằng `join()`, không bằng `run()`

Người mới đôi khi chọn `run()` vì muốn “đợi cho xong”. Cách đó biến công việc thành cuộc gọi tuần tự, bỏ đi khả năng đồng thời. Nếu cần worker chạy riêng nhưng phải chờ kết quả ở một điểm xác định, dùng `start()` kết hợp `join()`:

```java
Thread worker = new Thread(() -> performWork());
worker.start();

// main có thể làm việc độc lập tại đây.
worker.join(); // Chờ worker kết thúc trước khi tiếp tục.
```

Đây là **đoạn mã minh họa**, trong đó `performWork()` là phương thức do ứng dụng định nghĩa. `join()` có thể ném `InterruptedException`; chương trình thực tế cần truyền tiếp exception hoặc xử lý tín hiệu ngắt đúng cách.

Về bản chất, `join()` làm cho main chờ sau khi đã cho phép worker thực thi độc lập một khoảng thời gian. Với `run()`, toàn bộ công việc đã nằm ngay trong lời gọi trên main; không có worker mới để `join()` đợi.

## 7. `start()` tạo quan hệ happens-before; `run()` không tạo cạnh khởi động luồng

Java Memory Model định nghĩa quan hệ **happens-before** để mô tả các bảo đảm về thứ tự và khả năng nhìn thấy thay đổi bộ nhớ giữa các thread:

- Các hành động của luồng gọi **trước lời gọi `thread.start()` thành công** xảy ra trước những hành động được luồng mới thực hiện.
- Mọi hành động của một thread xảy ra trước khi một thread khác **hoàn tất `join()` thành công trên thread đó**.
- Các bảo đảm riêng khác vẫn cần dùng khi truy cập chung: `synchronized`, `volatile`, các kiểu atomic hoặc collection concurrent tùy trường hợp.

Ví dụ `StartVisibilityDemo.java`:

```java
public class StartVisibilityDemo {
    private static int input;
    private static int result;

    public static void main(String[] args) throws InterruptedException {
        input = 21;

        Thread worker = new Thread(() -> result = input * 2);
        worker.start();
        worker.join();

        System.out.println("result=" + result);
    }
}
```

```bash
javac StartVisibilityDemo.java
java StartVisibilityDemo
```

Kết quả:

```text
result=42
```

`input = 21` xảy ra trước `start()`, vì vậy worker được bảo đảm nhìn thấy cập nhật đó theo quan hệ happens-before. Ghi `result` của worker được bảo đảm nhìn thấy sau `join()` hoàn tất. Ví dụ không cần `volatile` bởi vì hai cạnh đồng bộ nêu trên đã thiết lập thứ tự cần thiết.

Nhưng thay `worker.join()` bằng vòng lặp đọc `result` không có đồng bộ có thể khiến mã mắc lỗi về visibility; chỉ `start()` không bảo đảm mọi lần ghi trong tương lai sẽ được quan sát an toàn bởi main. Và gọi `worker.run()` trực tiếp thực hiện các câu lệnh theo thứ tự chương trình trên main, **không tạo một quan hệ khởi động giữa hai thread**, vì chẳng có thread thứ hai nào được khởi động.

## 8. Exception: chạy trên caller hay trên worker?

Khi `run()` được gọi trực tiếp, một exception không được bắt trong `run()` lan về luồng gọi. Khi `run()` được JVM thực thi sau `start()`, một exception không được xử lý xảy ra **trong worker**; `try/catch` quanh `thread.start()` ở main không bắt được exception ném sau đó trong worker.

Tạo tệp `ThreadExceptionDemo.java`:

```java
public class ThreadExceptionDemo {
    public static void main(String[] args) throws InterruptedException {
        Runnable failure = () -> {
            throw new IllegalStateException("boom");
        };

        Thread direct = new Thread(failure, "direct");
        try {
            direct.run();
        } catch (IllegalStateException e) {
            System.out.println("main bắt được: " + e.getMessage());
        }

        Thread worker = new Thread(failure, "worker");
        worker.setUncaughtExceptionHandler((thread, error) ->
                System.out.println("handler: " + thread.getName()
                        + " -> " + error.getMessage()));
        worker.start();
        worker.join();
    }
}
```

```bash
javac ThreadExceptionDemo.java
java ThreadExceptionDemo
```

Kết quả:

```text
main bắt được: boom
handler: worker -> boom
```

Lần thứ nhất, `direct.run()` ném lỗi trên stack của main nên `catch` ở main xử lý được. Lần thứ hai, worker ném exception trên stack của nó; `UncaughtExceptionHandler` được gắn cho worker xử lý thông tin lỗi. `join()` chờ worker chấm dứt nhưng không tự ném lại exception của worker sang main.

Trong ứng dụng cần thu thập kết quả hoặc lỗi của task, `ExecutorService.submit` cùng `Future.get` có thể phù hợp hơn: `get` báo lỗi task thông qua `ExecutionException` và giúp caller chủ động xử lý. Việc báo lỗi của executor là cơ chế quản lý task, không thay đổi ngữ nghĩa của `Thread.start()` và `Thread.run()`.

## 9. Trạng thái của đối tượng `Thread` và những hiểu lầm thường gặp

Các trạng thái quan sát qua `Thread.getState()` gồm `NEW`, `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING` và `TERMINATED`. Việc quan sát tức thời bị ảnh hưởng bởi lập lịch; không nên viết chương trình phụ thuộc vào kết quả kiểm tra trạng thái giữa hai câu lệnh đang cạnh tranh.

| Thao tác | Thread mới được khởi động? | Ngữ cảnh thực thi `run()` | Tác động lên vòng đời đối tượng `thread` |
| --- | --- | --- | --- |
| `new Thread(job)` | Chưa | Chưa chạy task | `NEW` |
| `thread.run()` khi thread chưa start | Không | Luồng gọi, ví dụ `main` | Vẫn `NEW` |
| `thread.start()` thành công | Có | Luồng đại diện bởi `thread` | Rời `NEW`, về sau `TERMINATED` |
| `thread.join()` sau `start()` | Không tạo thêm | Không tự chạy lại task | Caller đợi thread kết thúc |
| Gọi `start()` lần thứ hai | Không | Không chạy task lần mới | Ném `IllegalThreadStateException` |

**Một điểm tinh tế:** `run()` là phương thức có thể gọi trực tiếp theo cú pháp Java, nhưng đối tượng `Thread` còn có trạng thái nội bộ do JVM quản lý. Không nên lạm dụng `thread.run()` làm API chạy công việc ở nhiều thời điểm. Nếu muốn gọi công việc tuần tự, gọi trực tiếp `job.run()` hoặc tách logic thành phương thức dịch vụ bình thường để ý định rõ ràng.

## 10. Virtual thread cũng có sự khác biệt `start`/`run`

Từ Java 21, virtual thread là một `Thread` do JVM quản lý, tối ưu cho nhiều công việc I/O-bound đang chờ. Nó vẫn tuân theo nguyên tắc căn bản:

- `Thread.ofVirtual().unstarted(job)` tạo virtual thread ở trạng thái chưa khởi động.
- Gọi `virtual.start()` khởi động **virtual thread mới** và cho JVM lập lịch nó lên carrier.
- Gọi `virtual.run()` trực tiếp trên đối tượng chưa start **không khởi động virtual thread**; công việc (nếu được thực thi theo phương thức được gọi) chạy trên caller. Để chạy công việc ảo đúng mục đích, luôn dùng API `start()` hoặc executor tạo virtual thread.
- `Thread.ofVirtual().start(job)` kết hợp tạo và khởi động, tương đương mục đích với tạo thread chưa start rồi gọi `start()`.
- `Executors.newVirtualThreadPerTaskExecutor()` tạo virtual thread mới cho mỗi task được gửi vào executor; đây không phải lời gọi trực tiếp `run()` trên main.

Ví dụ `VirtualStartDemo.java` (JDK 21+):

```java
public class VirtualStartDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread virtual = Thread.ofVirtual().unstarted(() ->
                System.out.println("isVirtual="
                        + Thread.currentThread().isVirtual()));

        virtual.start();
        virtual.join();
        System.out.println("done=" + !virtual.isAlive());
    }
}
```

```bash
javac VirtualStartDemo.java
java VirtualStartDemo
```

Kết quả:

```text
isVirtual=true
done=true
```

`Thread.currentThread().isVirtual()` được thực thi **bên trong** virtual thread sau khi `start()`, vì vậy là `true`. Dòng `done=true` chỉ in sau `join()`. Virtual thread không phải “giả lập” đồng thời: nó có danh tính và vòng đời luồng Java riêng, dù JVM có thể gắn/tháo nó khỏi các carrier thread khi chờ I/O.

## 11. Khi nào dùng `start()`, khi nào dùng `run()`, khi nào dùng executor?

### Muốn một công việc chạy độc lập với luồng gọi

Dùng `thread.start()`. Quản lý rõ vòng đời, ngắt và dọn dẹp tài nguyên; nếu cần kết quả thì kết hợp cơ chế chờ thích hợp. Với vài luồng cố định có vai trò rõ, thao tác trực tiếp với `Thread` có thể đủ đơn giản.

### Muốn chạy đồng bộ một công việc đơn lẻ

Gọi **chính phương thức thực hiện công việc**, chẳng hạn `job.run()` hoặc `service.perform()`. Gọi `thread.run()` trực tiếp về mặt kỹ thuật có thể thực thi cùng công việc nhưng gây hiểu nhầm rằng bạn đang khởi động thread.

### Muốn quản lý nhiều task, kết quả, hàng đợi và hủy

Dùng `ExecutorService`. Một pool cố định dùng lại platform thread cho nhiều task; `newVirtualThreadPerTaskExecutor()` tạo virtual thread mới cho từng task khi phù hợp với I/O-bound. Khác nhau ở chính sách thực thi, nhưng cả hai giúp tách **task** khỏi chi tiết trực tiếp gọi `Thread.start()`. Khi dùng executor, chờ kết quả bằng `Future.get()` hoặc cơ chế phù hợp; đừng gọi `run()` thủ công trên đối tượng `Thread` do executor quản lý.

Số thread nhiều không tự làm hệ thống nhanh hơn. Nếu tác vụ bị giới hạn bởi CPU, số lõi là nút thắt; nếu chờ kết nối mạng hoặc cơ sở dữ liệu, còn có connection pool, giới hạn hệ thống đích, timeout và an toàn dữ liệu. Virtual thread không làm các tài nguyên bên ngoài trở nên vô hạn.

## 12. Lỗi phổ biến và cách sửa

**Lỗi 1 — tưởng đã chạy đa luồng:**

```java
Thread t = new Thread(() -> doWork());
t.run(); // Sai nếu mục tiêu là khởi động luồng mới.
```

Đoạn mã minh họa, với `doWork()` do ứng dụng định nghĩa. Sửa thành `t.start()`, rồi quyết định có cần `t.join()` hay không.

**Lỗi 2 — gọi cả `start()` và `run()` vì muốn “bắt đầu chắc chắn”:**

```java
Thread t = new Thread(() -> doWork());
t.start();
t.run(); // Có thể thực thi thêm công việc trên caller!
```

Đây không phải cách đợi worker. Có nguy cơ logic của `doWork()` chạy hai lần hoặc cạnh tranh dữ liệu với chính worker. Nếu muốn chờ thread đã khởi động, gọi `t.join()`; nếu chỉ muốn gọi lại task một cách tuần tự thì dùng API công việc rõ ràng.

**Lỗi 3 — gọi `start()` lại sau khi worker xong:** một `Thread` không thể restart. Tạo thread mới hoặc dùng executor nhận task mới.

**Lỗi 4 — cho rằng `start()` chờ xong rồi mới trả về:** `start()` chỉ khởi động. Muốn quan sát kết quả sau worker, dùng `join()`/`Future.get()` và thiết lập đồng bộ thích hợp.

**Lỗi 5 — nghĩ `start()` tự bảo vệ biến dùng chung:** quan hệ happens-before tại điểm khởi động và join không thay thế mọi đồng bộ trong lúc hai thread cùng sửa dữ liệu. Với bộ đếm dùng chung, `counter++` vẫn có thể mất cập nhật; cân nhắc `AtomicInteger`, `synchronized` hoặc cấu trúc concurrent.

**Lỗi 6 — bắt exception quanh `start()` để chặn lỗi của task:** exception ném sau đó trong worker không tự quay về stack của caller. Dùng xử lý lỗi bên trong task, `UncaughtExceptionHandler` hoặc cơ chế thu thập lỗi của executor.

## 13. Kiểm tra hiểu bài

1. **Đoạn sau có chạy trên luồng mới không?**
   `Thread t = new Thread(() -> System.out.println(Thread.currentThread().getName())); t.run();`
   
   **Trả lời:** Không. Lời gọi `run()` thực thi trên luồng gọi, thường là `main`. Đối tượng `t` vẫn `NEW` nếu chưa được `start()`.
2. **Sau `t.start()`, có được gọi `t.start()` lần nữa khi t đã `TERMINATED` không?**
   
   **Trả lời:** Không. Một đối tượng `Thread` chỉ được khởi động thành công một lần. Lần sau ném `IllegalThreadStateException`.
3. **Nếu `main` cần làm tác vụ A trong lúc worker làm B rồi mới sử dụng kết quả B, nên dùng `run()` hay `start()`?**
   
   **Trả lời:** `start()` để B tiến triển độc lập; main làm A; sau đó chờ bằng `join()` hoặc lấy kết quả qua `Future`. Dùng `run()` sẽ chạy B đồng bộ trước A nếu đặt lời gọi B trước.
4. **Vì sao `Thread.currentThread() == t` có thể sai bên trong `t.run()`?**
   
   **Trả lời:** Khi `t.run()` được gọi trực tiếp trên main, `this` vẫn là `t` nhưng thread đang chạy là main; chỉ `start()` mới khiến JVM thực thi `run()` trong luồng t.
5. **Có thể thay `t.start()` bằng `t.run()` để tránh race condition không?**
   
   **Trả lời:** Điều đó chỉ làm lời gọi cụ thể trở thành tuần tự và có thể che dấu tranh chấp trong bài thử. Nó đánh mất concurrency, không sửa cơ chế truy cập chung nếu các thread khác vẫn hoạt động.
6. **`t.start()` có bảo đảm main nhìn thấy mọi biến worker sửa ngay lập tức không?**
   
   **Trả lời:** Không. `start()` bảo đảm các hành động trước start của caller được worker nhìn thấy theo happens-before; để main thấy kết quả sau khi worker hoàn tất có thể dùng `join()` hoặc cơ chế đồng bộ khác.

## 14. Ghi nhớ cốt lõi

**`start()` khởi động luồng Java mới; `run()` trực tiếp là lời gọi phương thức bình thường trên luồng hiện tại.** Một đối tượng `Thread` chỉ được `start()` thành công một lần, còn phương thức `run()` có thể được gọi như phương thức thông thường nhưng không mang ý nghĩa khởi động. Khi cần đồng thời, hãy khởi động rồi quản lý kết thúc bằng `join()` hoặc executor; khi cần đồng bộ, gọi trực tiếp logic công việc. Phân biệt hai cách gọi giúp đọc đúng trạng thái `Thread`, thứ tự thực thi, propagation của exception và các bảo đảm bộ nhớ trong chương trình Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-api-003">Đọc thông tin currentThread(), name và ID</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc thông tin Thread hiện tại: currentThread(), name và ID trong Java

## Bạn sẽ học được gì?

Khi một đoạn mã chạy đồng thời với những đoạn mã khác, câu hỏi đầu tiên để hiểu hành vi của nó thường là **“Đoạn mã này đang chạy trên thread nào?”**. Java cung cấp đối tượng `Thread` mô tả thread đang thực thi. Từ đối tượng đó, ta có thể đọc **tên** (`name`) và **mã định danh thread** (`ID`), đưa vào log, kiểm tra khi debug hoặc phân biệt các luồng trong thí nghiệm concurrency.

Sau bài học, bạn có thể:
- Gọi đúng `Thread.currentThread()` để lấy thread thực sự đang chạy đoạn mã hiện tại.
- Đọc `getName()` và `getId()` trong Java 17; hiểu `threadId()` được bổ sung từ Java 19.
- Phân biệt **đối tượng Thread**, **tên có thể đổi**, **ID gắn với một thread**, và **một task/Runnable** có thể chạy trên nhiều thread vào các thời điểm khác nhau.
- Giải thích vì sao gọi trực tiếp `run()` khác với `start()` và vì sao code chạy trong thread pool phải tự xem `currentThread()`.
- Dùng các thông tin này an toàn cho logging và troubleshooting, không nhầm thread ID với process ID, ID thread của hệ điều hành hoặc ID của yêu cầu nghiệp vụ.

**Phạm vi:** Thread API và concurrency trong Java nói chung. Bài không đề cập đến thread pool hay asynchronous execution riêng của Spring vì các nội dung Spring đã có chương riêng.

## 1. “Thread hiện tại” là gì?

Một chương trình Java có thể có nhiều thread thực thi cùng lúc hoặc xen kẽ. Mỗi thread là một luồng thực thi riêng; nhưng **một phương thức không tự sở hữu một thread cố định**. Phương thức đó chạy trên thread đã gọi nó.

Ví dụ:

~~~text
main thread
   |
   +-- main() ------> call helper() ------> helper() chạy trong main thread
   |
   +-- start(worker)
          |
          +-- worker thread ------> call helper() --> helper() chạy trong worker thread
~~~

Cùng một phương thức `helper()`, nhưng kết quả `Thread.currentThread()` tùy vào **ngữ cảnh chạy tại đúng thời điểm gọi**.

Chữ “current” được xác định tại nơi lệnh được *thực thi*:
- Trong `main()` khi chương trình chạy bằng Java launcher thông thường, đó thường là thread tên `main`.
- Trong `Runnable` do một `Thread` mới khởi chạy bằng `start()`, đó là thread mới.
- Trong một task được thực thi bởi `ExecutorService`, đó là worker của executor đang chạy task.
- Trong đoạn mã được gọi trực tiếp bằng một lời gọi phương thức thông thường, đó vẫn là thread của người gọi.

Điều này rất quan trọng khi phân tích bug concurrency: nhìn vào tên class/method hoặc đối tượng `Runnable` không đủ để biết nó đang chạy trên thread nào.

## 2. Ba API quan trọng

| API | Kiểu trả về | Đọc thông tin gì? | Ghi nhớ |
| --- | --- | --- | --- |
| `Thread.currentThread()` | `Thread` | Tham chiếu đến thread đang thực thi lệnh | Phương thức **static**, không cần có sẵn đối tượng Thread |
| `Thread.currentThread().getName()` | `String` | Tên của thread hiện tại | Tên **không bảo đảm duy nhất** và có thể thay đổi |
| `Thread.currentThread().getId()` | `long` | ID của thread hiện tại trong Java 17 | ID được cấp lúc tạo thread, ổn định trong đời sống thread; có thể được tái sử dụng sau khi thread kết thúc |
| `Thread.currentThread().threadId()` | `long` | ID của thread hiện tại từ Java 19+ | Ưu tiên dùng trên JDK 19+; không khả dụng khi biên dịch với Java 17 |

Cú pháp cơ bản:

~~~java
Thread current = Thread.currentThread();
String name = current.getName();
long id = current.getId(); // Java 17: hợp lệ
~~~

- `current` là **tham chiếu tới đối tượng Thread hiện tại**, không tạo thread mới.
- `getName()` là phương thức instance: đọc tên của chính đối tượng `current`.
- `getId()` là phương thức instance: đọc một giá trị `long`, không phải `String`.
- Có thể viết gộp thành `Thread.currentThread().getName()`, nhưng lưu đối tượng vào biến giúp tránh lặp lời gọi và tiện đọc nhiều thuộc tính.

Một phân biệt rất dễ nhầm:

~~~java
Thread worker = new Thread(() -> doSomething(), "worker-A");

String configuredName = worker.getName();          // Đọc đối tượng worker
String executingName = Thread.currentThread().getName(); // Đọc thread đang chạy lệnh này
~~~

Khi hai dòng trên thực thi ở `main()` trước khi gọi `worker.start()`, `configuredName` là `"worker-A"`, còn `executingName` thường là `"main"`. Việc *giữ tham chiếu tới* `worker` không khiến lệnh của người gọi chuyển sang chạy trong worker.

## 3. currentThread(): lấy đúng đối tượng đang thực thi

Khai báo API:

~~~java
public static native Thread currentThread();
~~~

Đây là chữ ký mang tính tham khảo ở mức Java API/JDK implementation; trong chương trình bạn chỉ cần **`Thread.currentThread()`**. Giá trị trả về mô tả thread đang thực thi. Kể cả khi gọi từ một phương thức `static`, một lambda, callback hoặc phương thức của object, thread hiện tại vẫn được xác định theo lời gọi lúc chạy.

### Ví dụ chạy được trên Java 17: main và worker

Tạo file **`ThreadIdentityDemo.java`**:

~~~java
public class ThreadIdentityDemo {

    private static void describe(String stage) {
        Thread current = Thread.currentThread();
        System.out.printf(
                "%s | name=%s | id=%d%n",
                stage, current.getName(), current.getId());
    }

    public static void main(String[] args) throws InterruptedException {
        Thread mainThread = Thread.currentThread();

        // Đặt tên rõ ràng để kết quả demo không phụ thuộc tên mặc định.
        String previousName = mainThread.getName();
        mainThread.setName("lesson-main");

        try {
            describe("main");

            Thread worker = new Thread(() -> {
                Thread executing = Thread.currentThread();

                describe("worker");
                System.out.println(
                        "worker-is-main=" + (executing == mainThread));
            }, "lesson-worker");

            // ID được cấp khi tạo Thread; chưa cần gọi start().
            System.out.println("before-start-id=" + worker.getId());

            worker.start();
            worker.join(); // Chờ worker kết thúc trước khi kiểm tra tiếp.

            System.out.println(
                    "ids-differ=" + (mainThread.getId() != worker.getId()));
        } finally {
            mainThread.setName(previousName);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ThreadIdentityDemo.java
java ThreadIdentityDemo
~~~

Đầu ra có **đúng thứ tự dòng** như sau, nhưng giá trị ID thực tế phụ thuộc lần chạy/JVM:

~~~text
main | name=lesson-main | id=<MAIN_ID>
before-start-id=<WORKER_ID>
worker | name=lesson-worker | id=<WORKER_ID>
worker-is-main=false
ids-differ=true
~~~

Ở đây `<MAIN_ID>` và `<WORKER_ID>` là **ký hiệu thay cho số `long` được in thật**, không phải văn bản Java in ra nguyên xi. Hai nơi hiển thị `<WORKER_ID>` là **cùng một ID**: Java cấp ID khi đối tượng worker được tạo, và ID ấy không đổi trong suốt vòng đời thread. Tại thời điểm chương trình chạy, `mainThread` và `worker` là hai thread khác nhau đang tồn tại, nên có ID khác nhau.

Vì sao ví dụ đáng tin cậy?
1. `mainThread.setName("lesson-main")` cho tên đã biết trước, rồi khôi phục tên trong `finally`.
2. Worker được tạo với tên `"lesson-worker"`; trong lambda, `currentThread()` trỏ đến worker thực thi.
3. `worker.start()` khởi chạy worker. `join()` khiến thread main chờ worker hoàn thành, nhờ đó hai dòng cuối của worker xuất hiện trước `ids-differ=true`.
4. Không dự đoán hoặc hard-code số ID; ID được JVM cấp và có thể khác giữa các lần chạy.

**Lưu ý:** trong ví dụ này không có hai thread cùng lúc ghi vào một biến dữ liệu chung không được đồng bộ. Kết quả từ worker được ghi ra console, còn `join()` giúp main đợi kết thúc. Trong ứng dụng thật, nếu chia sẻ dữ liệu giữa các thread, chỉ biết tên/ID không thay thế các cơ chế đồng bộ và bảo đảm visibility.

## 4. Thread name: nhãn dễ đọc, không phải định danh duy nhất

### 4.1. Tên có thể đến từ đâu?

Với platform thread truyền thống, bạn có thể:
- Đặt tên lúc tạo: `new Thread(task, "worker-orders")`.
- Đổi tên về sau: `thread.setName("worker-payments")`.
- Đọc tên: `thread.getName()`.
- Để JVM tự tạo một tên khi không chỉ định; **không nên phụ thuộc vào mẫu tên sinh tự động trong logic chương trình**.

Ví dụ:

~~~java
Thread thread = new Thread(() -> {
    System.out.println(Thread.currentThread().getName());
}, "report-worker");
thread.start();
thread.join();
~~~

Khi đặt đoạn này trong phương thức có `throws InterruptedException` hoặc bắt exception tương ứng, kết quả là:

~~~text
report-worker
~~~

Tên là nhãn hỗ trợ quan sát; Java **không yêu cầu tên khác nhau**. Hai thread khác nhau hoàn toàn có thể cùng tên `"report-worker"`. Vì vậy không được dùng `getName()` như khóa unique trong `Map` lưu trạng thái thread, càng không dùng tên để chứng minh “hai công việc chạy chung một thread”.

### 4.2. Đổi tên không đổi thread hoặc ID

~~~java
Thread current = Thread.currentThread();
long idBefore = current.getId();
String oldName = current.getName();

try {
    current.setName("diagnostic-worker");
    System.out.println(current.getName());           // diagnostic-worker
    System.out.println(idBefore == current.getId()); // true
} finally {
    current.setName(oldName);
}
~~~

Đổi tên **không** tạo thread mới và **không** thay ID. Một task chạy trong pool có thể tạm đổi tên để dễ debug, nhưng việc đổi tên worker dùng chung dễ làm tên sai ngữ cảnh ở task kế tiếp nếu không khôi phục. Trong ứng dụng lớn, hãy đặt tên worker bằng `ThreadFactory` khi tạo pool; ID nghiệp vụ của task/request nên được ghi riêng trong log.

Tên một thread có thể thay đổi đồng thời với lúc thread khác đọc nó; đừng dựa vào tên làm cơ chế đồng bộ hoặc để ra quyết định correctness.

### 4.3. Phân biệt tên thread và tên công việc

Giả sử một worker tên `"pool-worker-2"` lần lượt xử lý request A, B và C:

~~~text
Thread: pool-worker-2 (ID cố định khi thread còn sống)
  |
  +-- request A
  +-- request B
  +-- request C
~~~

Tên/ID trong log có thể giống nhau qua cả ba request; điều đó **không** có nghĩa các request là một. Ngược lại, một thao tác nghiệp vụ có thể chuyển qua nhiều thread ở các giai đoạn khác nhau. Muốn theo dấu thao tác, hãy thêm **requestId/taskId/correlationId** độc lập với tên/ID thread.

## 5. Thread ID: giá trị nào, độ duy nhất đến đâu?

Trong Java 17, phương thức chính là:

~~~java
long id = Thread.currentThread().getId();
~~~

Theo đặc tả Java 17, ID:
- Là số nguyên dương kiểu `long` do JVM cấp khi một `Thread` được tạo.
- **Không đổi trong đời sống của chính Thread đó.**
- **Duy nhất giữa các thread trong suốt thời gian chúng còn sống đồng thời** theo bảo đảm ID thread.
- Có thể được **tái sử dụng** sau khi một thread kết thúc.

Vì có thể tái sử dụng, **không dùng riêng ID làm khóa lâu dài** cho bản ghi giám sát qua nhiều lần chạy chương trình, hay làm ID nghiệp vụ trong database.

Cần phân biệt rõ:

| Giá trị | Ví dụ cách lấy | Ý nghĩa |
| --- | --- | --- |
| Java thread ID | `Thread.currentThread().getId()` / `threadId()` | Định danh thread trong JVM |
| Java thread name | `Thread.currentThread().getName()` | Nhãn có thể sửa; không unique |
| Process ID (PID) | `ProcessHandle.current().pid()` | Định danh **tiến trình** JVM ở cấp hệ điều hành |
| Native/OS thread ID | API/công cụ hệ điều hành hoặc profiler | Định danh thread phía OS, **không bảo đảm bằng** Java thread ID |
| Request/task ID | Định danh do ứng dụng cấp | Định danh *công việc nghiệp vụ*, độc lập thread |

Khi đọc thread dump hay JFR, các công cụ có thể dùng nhiều loại ID hoặc cách biểu diễn khác nhau. Hãy đọc nhãn cột thay vì mặc định tất cả ID đều là kết quả `Thread.getId()`. ID là công cụ nhận diện thời điểm quan sát, **không tiết lộ** thread đang chờ, đang chạy, daemon hay mức ưu tiên; muốn kiểm tra thuộc tính đó phải dùng API và phép quan sát tương ứng.

## 6. Quan trọng: gọi run() không đồng nghĩa tạo thread mới

`worker.run()` gọi phương thức ngay trên caller, nên `Thread.currentThread()` trong task vẫn là **thread đang gọi**; `worker.getName()` chỉ là tên của đối tượng được tạo. Chỉ `worker.start()` mới khởi động thread mới. Đoạn sau minh họa bên trong một phương thức đã khai báo `throws InterruptedException`:

~~~java
Thread worker = new Thread(
        () -> System.out.println(Thread.currentThread().getName()),
        "new-worker");
worker.start();
worker.join();
~~~

Kết quả là `new-worker`; nếu gọi `run()` trực tiếp từ main thay vì `start()`, output sẽ là `main`. Vì bài này tập trung vào **nhận diện thread**, phần giải thích sâu về trạng thái, happens-before và exception được trình bày tại bài **Phân biệt gọi start() và run()**.

## 7. Đọc tên và ID khi chạy task qua ExecutorService

Trong chương trình thực tế, một `Runnable` hay `Callable` có thể được gửi đến pool và được worker thực thi sau đó. **Task không phải thread**. Vì vậy, giá trị tên/ID mà bạn đọc từ thread gọi `submit()` không chỉ ra worker nào sẽ chạy task.

Ví dụ Java 17 với pool hai worker, được đặt tên khi tạo bằng `ThreadFactory`:

Tạo file **`PoolIdentityDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PoolIdentityDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger sequence = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(
                2,
                runnable -> new Thread(
                        runnable,
                        "lesson-pool-" + sequence.incrementAndGet())
        );

        try {
            List<Future<String>> results = new ArrayList<>();

            for (int taskNo = 1; taskNo <= 4; taskNo++) {
                final int taskId = taskNo;
                results.add(pool.submit(() -> {
                    Thread worker = Thread.currentThread();

                    return "task=" + taskId
                            + ", worker=" + worker.getName()
                            + ", javaThreadId=" + worker.getId();
                }));
            }

            // Đọc kết quả theo thứ tự submit, dù worker xử lý xen kẽ.
            for (Future<String> result : results) {
                System.out.println(result.get());
            }
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac PoolIdentityDemo.java
java PoolIdentityDemo
~~~

Chương trình in bốn dòng tương ứng `task=1`, `task=2`, `task=3`, `task=4` theo **thứ tự lấy `Future`**, ví dụ một lượt chạy có thể trông như sau:

~~~text
task=1, worker=lesson-pool-1, javaThreadId=21
task=2, worker=lesson-pool-2, javaThreadId=22
task=3, worker=lesson-pool-1, javaThreadId=21
task=4, worker=lesson-pool-2, javaThreadId=22
~~~

**Đây là đầu ra minh họa, không phải stdout cố định.** JVM có thể chọn worker khác cho từng task và cấp ID khác `21`, `22`; thời điểm worker được tạo/lịch thực thi không được cố định. Ngay cả khi chỉ có một worker chạy đủ nhanh, kết quả từ cả bốn task cũng có thể mang cùng tên/ID worker.

Những điều chắc chắn trong ví dụ:
- Có **bốn kết quả** và thứ tự hiển thị theo `Future` trong list, nhờ vòng lặp đọc `result.get()`.
- Mỗi kết quả được tạo **bên trong task**, nên `Thread.currentThread()` trả về worker thật đang thực thi task đó.
- Với hai worker trong pool, tối đa hai worker chạy task đồng thời trong tình huống này; không bảo đảm chính xác hai worker sẽ được tạo.
- `ThreadFactory` đặt tên giúp log dễ đọc; `AtomicInteger` cấp suffix tăng dần một cách an toàn khi tạo worker. Số suffix không phải thread ID JVM.
- Pool được shutdown; `Future.get()` có thể ném exception nếu task thất bại. Ví dụ cố ý chờ hoàn tất để thí nghiệm dễ kiểm tra.

**Quy tắc:** muốn biết *task đang chạy ở đâu*, hãy gọi `Thread.currentThread()` **bên trong task**, không phải tại nơi tạo `Runnable` hoặc gửi `submit()`.

## 8. Khác biệt phiên bản Java: getId(), threadId() và virtual threads

Bài mẫu dùng Java 17 để biên dịch dễ dàng. Trên các JDK hiện đại, cần để ý:

| Phiên bản | API ID có thể dùng | Diễn giải |
| --- | --- | --- |
| Java 17 | `getId()` | Hợp lệ, chưa bị đánh dấu deprecated ở Java 17 |
| Java 19+ | `threadId()` (khuyến nghị), `getId()` (deprecated từ 19) | `threadId()` là `final` và phản ánh ID thread; `getId()` không phải `final`, có thể bị subclass override |
| Java 21+ | `threadId()` áp dụng cho cả platform và virtual thread | `currentThread()` trong virtual thread trả về **đối tượng virtual thread đang chạy**, không phải carrier platform thread |

Trong code chỉ nhắm Java 21+, cách đọc ID rõ ràng là:

~~~java
Thread current = Thread.currentThread();
long id = current.threadId(); // Java 19+
System.out.println(current.getName() + " #" + id);
~~~

**Không dán đoạn `threadId()` này vào chương trình biên dịch bằng `--release 17`:** phương thức chưa tồn tại trong Java 17.

Với virtual threads (ổn định từ Java 21), tên mặc định có thể là chuỗi rỗng `""` nếu bạn không đặt; `getName()` không tự nhiên cho nhãn kiểu `"Thread-..."`. Có thể đặt tên bằng builder dành cho virtual thread. Dù virtual thread được JVM lên lịch qua carrier platform threads, `Thread.currentThread()` trong mã virtual thread vẫn là virtual thread. Java thread ID là identity Java; đừng tự suy ra carrier hoặc OS thread ID từ nó.

Nếu phải hỗ trợ cả Java 17 lẫn Java 21 bằng **cùng một binary biên dịch cho 17**, dùng `getId()` với hiểu biết về deprecation ở các JDK mới; không dùng phương thức `threadId()` chưa có trong classpath API Java 17.

## 9. Cách áp dụng trong log và điều tra vấn đề

Một dòng log tối thiểu hữu ích có thể chứa:

~~~text
2026-10-28T10:15:30Z level=INFO threadName=lesson-pool-1 javaThreadId=21 taskId=job-42 action=parse-start
~~~

Các giá trị trên **chỉ là ví dụ định dạng**, không phải log được tạo từ chương trình phía trên. Khi quan sát nhiều thread:

1. **Thread name** cho biết nhãn dễ đọc, thường hữu ích để tìm nhóm worker.
2. **Java thread ID** giúp phân biệt các thread trùng tên khi chúng còn sống, nhưng không nên lưu làm identity xuyên nhiều tiến trình hoặc lâu dài sau khi thread đã kết thúc.
3. **Task ID** gắn các dòng log của cùng một công việc, kể cả công việc chuyển từ worker này sang worker khác.
4. **Timestamp và sự kiện** cho biết thứ tự quan sát; ID không cho biết thời gian tạo hoặc thứ tự hoàn tất.
5. **Exception/stack trace và trạng thái thread** mới là bằng chứng để điều tra block, deadlock hoặc lỗi; tên và ID một mình không xác định được nguyên nhân.

Trong code đa luồng, `System.out.println` từ các thread có thể xuất hiện theo thứ tự khác nhau; không giả định thứ tự từ tốc độ CPU hay số ID. Ví dụ trên đã dùng `join()` hoặc vòng lặp `Future.get()` để kiểm soát điểm chờ và thứ tự **hiển thị kết quả**, không giả định thứ tự **thực thi** của worker.

### Khi đặt tên thread

Một tên hữu ích nên mô tả vai trò ổn định của worker (ví dụ `"file-reader-1"`, `"metrics-poller-2"`), có suffix nếu cần đọc log. Tránh nhét mật khẩu, token hoặc dữ liệu cá nhân vào tên thread vì tên có thể xuất hiện trong log, thread dump và giao diện chẩn đoán. Đối với ID yêu cầu có thể thay đổi theo task, dùng field log riêng.

## 10. Các lỗi thường gặp

| Nhầm lẫn | Vì sao sai | Cách đúng |
| --- | --- | --- |
| `Thread.currentThread()` tạo thread mới | Đây là static accessor cho thread **đang chạy** | Muốn chạy thread mới phải tạo và `start()` (hoặc dùng executor) |
| `worker.getName()` luôn cho biết thread đang chạy đoạn mã | Nó chỉ đọc đối tượng `worker` | Gọi `Thread.currentThread().getName()` tại chỗ code thực thi |
| Gọi `worker.run()` là chạy song song | `run()` trực tiếp là lời gọi phương thức bình thường | `start()` mới yêu cầu một thread thực thi riêng |
| Tên thread là duy nhất | Java cho phép tên trùng và đổi tên | Dùng đối tượng hoặc ID khi nhận diện thread đang sống; dùng ID nghiệp vụ cho task |
| Java thread ID = OS thread ID = PID | Ba loại định danh khác nhau | Gắn nhãn rõ `javaThreadId`, `pid`, `nativeThreadId` |
| Thread ID tăng đều theo thứ tự xử lý | ID được cấp khi tạo, lịch thực thi độc lập | Không suy luận thứ tự chạy/hoàn tất từ ID |
| Tăng thread ID nghĩa là thread chạy nhanh hơn | ID không phải phép đo hiệu năng | Muốn đo latency/throughput phải có đồng hồ và metric riêng |
| Task chạy ở một thread cố định | Executor có thể dùng nhiều worker qua các lần submit | Đọc identity ngay trong task và gắn correlation ID riêng |
| Gọi `threadId()` khi dùng Java 17 | API mới có từ Java 19 | Java 17 dùng `getId()` |
| Dùng tên/ID để điều khiển synchronization | Metadata không thay thế quan hệ happens-before | Dùng `join()`, lock, atomic/concurrent utilities đúng mục đích |

## 11. Bài tập tự kiểm tra

**Bài 1 — Nhận diện caller:** Tạo một `Runnable` in `Thread.currentThread().getName()`, gọi `run()` trực tiếp rồi truyền cùng `Runnable` vào `new Thread(r, "demo-worker")` và `start()`. Vì sao hai lần có thể in hai tên khác nhau? (`run()` trực tiếp chạy trên caller; `start()` tạo đường thực thi của worker.)

**Bài 2 — Tên trùng nhau:** Tạo hai đối tượng `Thread` đều có tên `"reader"`; mỗi thread in tên và ID của chính nó. Kết luận được gì? (Tên có thể trùng, ID khác trong thời gian cùng sống; muốn đảm bảo hai thread đang cùng sống trong thí nghiệm, dùng đồng bộ thích hợp.)

**Bài 3 — ID trước và sau start:** Đọc `getId()` sau khi tạo nhưng trước `start()`, rồi đọc tiếp sau `join()` trên chính đối tượng. Giá trị có thay đổi không? (Không; ID được cấp lúc tạo và không đổi trong vòng đời đối tượng.)

**Bài 4 — Task khác worker:** Submit bốn task vào pool hai worker, log `taskId`, `getName()`, `getId()` **bên trong từng task**. Nếu hai task in cùng Java thread ID, có thể kết luận chúng là cùng một task không? (Không; worker có thể được tái sử dụng để chạy nhiều task.)

**Bài 5 — Tương thích JDK:** Code dùng `Thread.currentThread().threadId()` chạy khi build với Java 21 nhưng lỗi biên dịch khi build với `javac --release 17`. Hãy giải thích. (Phương thức `threadId()` xuất hiện từ Java 19; Java 17 dùng `getId()`.)

## Ghi nhớ

- `Thread.currentThread()` xác định **thread đang thực thi đoạn mã tại thời điểm gọi**.
- `getName()` cung cấp **nhãn dễ đọc**, có thể trùng hoặc đổi tên.
- `getId()` trên Java 17 / `threadId()` trên Java 19+ cung cấp **ID do JVM cấp**, ổn định trong đời sống của thread và không phải ID công việc hay OS/PID.
- Trong executor, cần lấy current thread **bên trong task**, đồng thời dùng task/request ID riêng để theo dõi nghiệp vụ.
- Các giá trị identity hỗ trợ debug; **không chứng minh thứ tự chạy, khả năng đồng bộ hoặc hiệu năng**.

### Tài liệu API

- [Java SE 17 — java.lang.Thread](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): `currentThread()`, `getName()`, `getId()`, `start()`, `join()`.
- [Java SE 21 — java.lang.Thread](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html): `threadId()`, `getId()` deprecation, virtual thread behavior.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-api-004">Thread priority: ý nghĩa và giới hạn bảo đảm</a>

<details>
<summary>Xem chi tiết</summary>

# Thread priority trong Java: ý nghĩa và giới hạn bảo đảm

> **Trọng tâm:** `Thread.setPriority(int)` chỉ đặt mức ưu tiên *đề nghị* cho bộ lập lịch của platform thread. Giá trị cao hơn **không cam kết** chạy trước, chạy nhanh hơn, được cấp một tỉ lệ CPU nhất định hoặc hoàn thành sớm hơn. Vì vậy, không dùng priority làm điều kiện đúng/sai của chương trình đồng thời.

## 1. Thread priority giải quyết điều gì?

Khi nhiều thread sẵn sàng chạy nhưng số lõi CPU hoặc tài nguyên xử lý có hạn, hệ điều hành/JVM phải quyết định thread nào được thực thi. `Thread` của Java có thuộc tính `priority` để cung cấp một tín hiệu về mức độ ưu tiên tương đối của **platform thread**. Priority không phải thứ tự công việc trong hàng đợi, cũng không phải deadline, cơ chế cấp quyền truy cập tài nguyên hay mức độ quan trọng của dữ liệu.

Phân biệt bốn khái niệm:

| Khái niệm | Trả lời câu hỏi | Priority có bảo đảm không? |
| --- | --- | --- |
| **Scheduling** (lập lịch) | Thread nào có cơ hội chạy khi CPU khan hiếm? | Chỉ là một gợi ý, tùy JVM/OS. |
| **Ordering** (thứ tự) | Tác vụ A bắt buộc được xử lý trước B? | **Không.** Dùng hàng đợi/phối hợp rõ ràng. |
| **Synchronization** (đồng bộ) | Dữ liệu chung có đúng và nhất quán? | **Không.** Dùng lock, volatile, atomic, concurrent collections... |
| **Deadline / latency** | Công việc kết thúc trước thời hạn nào? | **Không.** Cần thiết kế tài nguyên, timeout, đo đạc, kiểm soát tải. |

Ngay cả khi thread có priority cao hơn, nó có thể đang `BLOCKED` để lấy khóa, `WAITING` để nhận tín hiệu, `TIMED_WAITING` vì ngủ/chờ thời hạn, hoặc chờ I/O. Priority không làm các điều kiện chờ đó biến mất.

## 2. Các mức priority và Java API

`java.lang.Thread` định nghĩa ba hằng số và khoảng giá trị hợp lệ:

| Hằng số | Giá trị | Ý nghĩa |
| --- | ---: | --- |
| `Thread.MIN_PRIORITY` | `1` | Mức nhỏ nhất |
| `Thread.NORM_PRIORITY` | `5` | Mức mặc định thông dụng |
| `Thread.MAX_PRIORITY` | `10` | Mức lớn nhất |

Các số nguyên từ `1` đến `10` đều có thể truyền vào `setPriority(int)`. Đây là **thang priority do API Java biểu diễn**, không đồng nghĩa với các cấp priority của hệ điều hành. Cách JVM ánh xạ, số mức được phân biệt thực sự và ảnh hưởng của nó có thể khác nhau theo nền tảng và cấu hình.

Các thao tác chính:

~~~java
thread.setPriority(Thread.MIN_PRIORITY);  // Yêu cầu mức 1.
thread.setPriority(Thread.NORM_PRIORITY); // Yêu cầu mức 5.
thread.setPriority(Thread.MAX_PRIORITY);  // Yêu cầu mức 10.
int configuredPriority = thread.getPriority();
~~~

- `setPriority` với giá trị ngoài `1..10` ném `IllegalArgumentException`; không có cơ chế tự kẹp giá trị đầu vào không hợp lệ vào khoảng này.
- Đối với **platform thread**, mức thực tế được ghi nhận trong Java không vượt quá mức tối đa mà `ThreadGroup` của thread cho phép. Vì vậy, yêu cầu `10` có thể dẫn tới giá trị `getPriority()` thấp hơn `10` trong một group có trần thấp.
- `getPriority()` trả về thuộc tính priority của thread trong Java. **Không** hiểu đó là bằng chứng hệ điều hành đã ưu tiên chạy thread, hoặc là phép đo CPU share.
- Có thể cấu hình priority cho platform thread trước `start()`, hoặc thay đổi khi thread đang sống; thay đổi sau khi chạy không đồng nghĩa tác động tức thời, cố định hoặc có thể dự báo đến scheduler.
- Platform thread mới thường **kế thừa priority** của thread tạo nó, đồng thời chịu giới hạn `ThreadGroup`. Không nên dựa vào tính kế thừa ngầm khi cần một cấu hình cụ thể; hãy đặt rõ ràng cho thread được tạo ra.

`ThreadGroup` là API cũ và hiếm khi cần cho mã Java mới; hiểu giới hạn priority của nó giúp giải thích vì sao `setPriority(MAX_PRIORITY)` không phải lúc nào cũng khiến `getPriority() == 10`.

## 3. Ví dụ Java 17+ có đầu ra xác định

Tạo tệp `PriorityBasics.java`:

~~~java
public class PriorityBasics {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(
                () -> System.out.println(
                        "inside = " + Thread.currentThread().getPriority()),
                "demo-worker");

        // MIN_PRIORITY luôn nằm trong phạm vi hợp lệ của platform thread.
        worker.setPriority(Thread.MIN_PRIORITY);
        System.out.println("before start = " + worker.getPriority());

        worker.start();
        worker.join(); // Bảo đảm worker in xong trước dòng cuối.

        try {
            worker.setPriority(Thread.MAX_PRIORITY + 1);
        } catch (IllegalArgumentException expected) {
            System.out.println("out of range");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac PriorityBasics.java
java PriorityBasics
~~~

Đầu ra:

~~~text
before start = 1
inside = 1
out of range
~~~

Điều ví dụ chứng minh: ta có thể thiết lập và đọc **giá trị priority trong Java**; `join()` tạo thứ tự chờ hoàn thành nên vị trí các dòng in được xác định. Ví dụ **không** chứng minh rằng priority `1` chạy chậm hay priority `10` chạy nhanh. Hai dòng đầu phản ánh trạng thái cấu hình, không phải kết quả lập lịch CPU.

Nếu thay `MIN_PRIORITY` bằng `MAX_PRIORITY`, giá trị đọc thường là `10` trong group mặc định, nhưng có thể bị giới hạn bởi group. Không được viết bài test bắt buộc mọi môi trường đều quan sát một mức CPU/tốc độ giống nhau.

## 4. Vì sao không có bảo đảm “priority cao chạy trước”?

Một số nguyên nhân có thể cùng xuất hiện:

1. **Lập lịch phụ thuộc nền tảng.** JVM phối hợp với hệ điều hành để chạy platform thread; chính sách scheduler, độ ưu tiên native, permission, cgroup/container và tải máy ảnh hưởng đến kết quả. Java API không hứa lịch thực thi toàn cục theo priority.
2. **Nhiều lõi có thể chạy song song.** Thread priority thấp vẫn có thể chạy đồng thời với thread priority cao; khái niệm “trước” thường không có nghĩa khi cả hai được cấp CPU.
3. **Trạng thái sẵn sàng khác trạng thái chạy.** Thread ưu tiên cao có thể đang chờ lock, I/O, tín hiệu hoặc bị tạm dừng bởi cơ chế khác; thread thấp hơn vẫn có thể tiến triển.
4. **Hiệu năng không chỉ phụ thuộc thời gian CPU.** Khóa, bộ nhớ, cache CPU, JIT, garbage collection, đồng bộ, I/O và thuật toán có thể chi phối thời gian hoàn thành.
5. **Không có fairness hay starvation-freedom từ API.** Không có cam kết mỗi thread sẽ nhận CPU sau một khoảng tối đa, hoặc thread thấp sẽ không bị “đói” tài nguyên dưới tải.

Không suy ra các mệnh đề sau từ priority:

- `high.start()` rồi `low.start()` ⇒ `high` in ra trước `low`.
- `high.getPriority() > low.getPriority()` ⇒ `high` luôn lấy lock trước.
- `high` có priority `10` ⇒ luôn hoàn thành trước deadline.
- `low` có priority `1` ⇒ sẽ không bao giờ được chạy.
- Hai thread cùng priority ⇒ chia CPU 50/50 hoặc thực thi theo FIFO.

**Một lần chạy thử** tình cờ cho kết quả “đúng thứ tự” không biến hành vi đó thành hợp đồng của Java.

## 5. Thí nghiệm quan sát: đo, nhưng không suy ra hợp đồng

Ví dụ sau cố ý tạo hai platform thread tính toán cạnh tranh CPU. Lưu thành `PriorityObservation.java`:

~~~java
import java.util.concurrent.atomic.AtomicBoolean;

public class PriorityObservation {
    public static void main(String[] args) throws InterruptedException {
        AtomicBoolean running = new AtomicBoolean(true);
        long[] counts = new long[2];

        Thread low = new Thread(() -> {
            long n = 0;
            while (running.get()) {
                n++;
            }
            counts[0] = n;
        }, "low");

        Thread high = new Thread(() -> {
            long n = 0;
            while (running.get()) {
                n++;
            }
            counts[1] = n;
        }, "high");

        low.setPriority(Thread.MIN_PRIORITY);
        high.setPriority(Thread.MAX_PRIORITY);

        low.start();
        high.start();

        Thread.sleep(1_500);
        running.set(false);

        low.join();
        high.join();

        System.out.println("low = " + counts[0]);
        System.out.println("high = " + counts[1]);
    }
}
~~~

Chạy với:

~~~bash
javac PriorityObservation.java
java PriorityObservation
~~~

Chương trình in hai **số đếm tùy lần chạy**; không có stdout số học cố định. Tùy hệ điều hành, CPU, số lõi, tải hệ thống, warm-up JIT và cách ánh xạ priority, `high` có thể đếm nhiều hơn, gần bằng, thậm chí ít hơn `low`. Không nên dùng so sánh số đếm này làm unit test hay cam kết sản phẩm.

Điểm đúng về concurrency trong ví dụ: `running.get()`/`set(false)` của `AtomicBoolean` cung cấp cơ chế quan sát tín hiệu dừng giữa các thread; `join()` chờ cả hai kết thúc và cung cấp quan hệ *happens-before* để main đọc `counts` sau khi worker ghi. **Priority không chịu trách nhiệm cho tính an toàn bộ nhớ này.**

Vòng lặp bận ở đây chỉ dùng để minh họa scheduler trong môi trường thử nghiệm ngắn. Trong ứng dụng thật, tránh đốt CPU bằng busy-wait chỉ để “tranh quyền ưu tiên”; chọn blocking queue, wait/notify, lock/condition, semaphore hoặc API concurrent phù hợp.

Muốn thực nghiệm có ý nghĩa hơn, hãy ghi lại JDK/JVM, OS, giới hạn CPU/container, số core, các priority được `getPriority()` trả về, và đo nhiều lần sau warm-up. Kết quả đo vẫn chỉ mô tả **môi trường đã đo**.

## 6. Priority không thay thế đồng bộ, tính đúng đắn hay hủy tác vụ

### 6.1. Không thể dùng priority làm tín hiệu “xong việc”

Sai về thiết kế:

~~~java
// Minh họa lỗi logic; không phải chương trình độc lập.
producer.setPriority(Thread.MAX_PRIORITY);
producer.start();
consumer.start(); // Không thể suy ra producer đã tạo dữ liệu xong.
~~~

Nếu consumer cần kết quả của producer, hãy dùng một quan hệ phối hợp có hợp đồng, ví dụ:

~~~java
// Minh họa API phối hợp, không phải chương trình độc lập.
producer.start();
producer.join();   // Chỉ sau khi producer kết thúc mới tiếp tục.
consumer.start();
~~~

Trong bài toán thực tế không nên `join` mọi tác vụ theo chuỗi nếu cần throughput song song; có thể dùng `BlockingQueue`, `CountDownLatch`, `Future`, `CompletableFuture` hoặc cơ chế bảo vệ dữ liệu phù hợp. Điều cốt lõi là **thứ tự phụ thuộc dữ liệu phải được mã hóa trực tiếp**.

### 6.2. Priority không tạo *happens-before*

Java Memory Model quy định cách thread quan sát ghi/đọc giữa các thread. Việc gọi `setPriority`, hoặc thấy một thread có priority cao hơn, **không tự tạo** quan hệ *happens-before* giữa các thao tác trên dữ liệu dùng chung. Phải dùng các cơ chế có ngữ nghĩa đồng bộ, chẳng hạn `volatile`, `synchronized`, `Lock`, atomic variable, `Thread.start()`, `Thread.join()`, hay các lớp `java.util.concurrent` theo hợp đồng riêng.

### 6.3. Priority không chữa deadlock, starvation hay priority inversion

- **Deadlock:** các thread giữ khóa rồi chờ lẫn nhau; tăng priority không phá được vòng chờ.
- **Starvation:** một thread có thể đợi rất lâu để lấy CPU/lock/tác vụ tiếp theo; Java priority không cung cấp cam kết công bằng.
- **Priority inversion:** một thread ưu tiên cao đợi khóa do thread ưu tiên thấp giữ; thread khác có thể khiến thread giữ khóa chậm được chạy. Một số hệ điều hành có kỹ thuật như *priority inheritance* ở một số loại khóa, nhưng **không được mặc định Java monitor (synchronized) có cam kết priority inheritance di động giữa mọi JVM/OS**.

Biện pháp thiết kế thường thiết thực hơn: giữ critical section ngắn, tránh giữ khóa trong lúc I/O, giảm cạnh tranh tài nguyên, xác lập thứ tự lấy khóa, dùng timeout khi phù hợp và quan sát thread dump.

### 6.4. Priority không thay thế interruption

`interrupt()` là tín hiệu hợp tác để dừng/hủy công việc hoặc đánh thức một số thao tác chờ có khả năng bị ngắt. `setPriority(MAX_PRIORITY)` không buộc thread đang chờ hoàn thành, không ép thread thoát khỏi `sleep()`, `wait()` hay thao tác blocking; xử lý `InterruptedException` và chính sách hủy cần được thiết kế độc lập.

## 7. Cần “tác vụ quan trọng được chọn trước”? Dùng hàng đợi ưu tiên

Nếu yêu cầu là: *“Khi worker lấy tác vụ tiếp theo, tác vụ khẩn phải được chọn trước tác vụ thông thường đang chờ”*, thì hãy biểu diễn **ưu tiên tác vụ** trong cấu trúc dữ liệu, thay vì nâng priority của worker thread.

Ví dụ Java 17+ sử dụng `PriorityBlockingQueue`. Lưu thành `PriorityQueueExample.java`:

~~~java
import java.util.concurrent.PriorityBlockingQueue;

public class PriorityQueueExample {
    record Task(int importance, long arrival, String name)
            implements Comparable<Task> {
        @Override
        public int compareTo(Task other) {
            int byImportance = Integer.compare(
                    other.importance, this.importance); // Cao trước
            if (byImportance != 0) {
                return byImportance;
            }
            return Long.compare(this.arrival, other.arrival); // FIFO khi bằng
        }
    }

    public static void main(String[] args) {
        PriorityBlockingQueue<Task> queue = new PriorityBlockingQueue<>();

        // Nạp hết trước khi lấy để minh họa thứ tự lựa chọn.
        queue.add(new Task(1, 0, "background"));
        queue.add(new Task(10, 1, "urgent"));
        queue.add(new Task(5, 2, "normal"));
        queue.add(new Task(10, 3, "urgent-later"));

        while (!queue.isEmpty()) {
            System.out.println(queue.remove().name());
        }
    }
}
~~~

Chạy:

~~~bash
javac PriorityQueueExample.java
java PriorityQueueExample
~~~

Kết quả xác định:

~~~text
urgent
urgent-later
normal
background
~~~

Ở đây `compareTo` đặt quy tắc nghiệp vụ rõ ràng: `importance` lớn hơn được **lấy khỏi queue trước**; khi bằng nhau, `arrival` nhỏ hơn được lấy trước. Sự sắp xếp này là hợp đồng của cấu trúc dữ liệu theo comparator, khác với gợi ý CPU scheduling.

Giới hạn phải hiểu đúng:

- Priority queue ưu tiên **phần tử đang chờ được lấy ra**, không thể giành lại (preempt) công việc worker đã lấy và đang chạy.
- Nếu nhiều worker lấy và chạy cùng lúc, **thứ tự hoàn thành** có thể khác thứ tự lấy việc.
- `PriorityBlockingQueue` là queue **không giới hạn dung lượng theo capacity cố định**; dưới tải liên tục phải thiết kế giới hạn, admission control/backpressure và cơ chế chống starvation cho tác vụ ưu tiên thấp. `put` không tạo chờ vì queue đầy như `ArrayBlockingQueue`.
- Trong demo, `arrival` được cấp tuần tự; hệ thống đa producer cần một số thứ tự duy nhất/an toàn thread hoặc định nghĩa rõ cách xử lý trường hợp bằng điểm.
- Nếu một luồng tác vụ ưu tiên cao liên tục đến, tác vụ thấp có thể nằm chờ lâu; cân nhắc quota, age-based priority hoặc các queue riêng có phân bổ tài nguyên.

## 8. Platform thread và virtual thread khác nhau ở điểm nào?

Từ **Java 21**, Java hỗ trợ virtual thread như một lựa chọn ổn định. Cần phân biệt:

| | Platform thread | Virtual thread (Java 21+) |
| --- | --- | --- |
| Scheduling | Thông thường JVM dựa trên thread của hệ điều hành | Được runtime Java lập lịch trên các carrier platform thread |
| `getPriority()` | Thuộc tính `1..10` trong giới hạn group | Luôn là `Thread.NORM_PRIORITY` (`5`) |
| `setPriority(int)` | Thay đổi giá trị Java-level trong giới hạn group; hiệu ứng OS phụ thuộc nền tảng | **Bỏ qua yêu cầu thay đổi** (giá trị đầu vào vẫn phải nằm trong khoảng hợp lệ) |
| Nên dùng priority để kiểm soát thứ tự công việc? | Không | Không |

Đoạn độc lập sau **chỉ chạy trên JDK 21+**. Lưu thành `VirtualPriority.java`:

~~~java
public class VirtualPriority {
    public static void main(String[] args) throws InterruptedException {
        Thread virtual = Thread.ofVirtual().unstarted(
                () -> System.out.println(
                        "inside = " + Thread.currentThread().getPriority()));

        virtual.setPriority(Thread.MAX_PRIORITY);
        System.out.println("configured = " + virtual.getPriority());

        virtual.start();
        virtual.join();
    }
}
~~~

Chạy `javac VirtualPriority.java` rồi `java VirtualPriority` trên JDK 21+. Kết quả:

~~~text
configured = 5
inside = 5
~~~

Không đem kỳ vọng về platform-thread priority áp dụng lên virtual thread. Nếu cần chọn công việc nào xử lý trước, hãy quyết định ở mức queue, điều phối và giới hạn tải.

## 9. Thiết kế thực tế khi có yêu cầu ưu tiên

| Nhu cầu thật | Cơ chế nên xem xét | Điều cần xác minh |
| --- | --- | --- |
| Tác vụ khẩn được **lấy trước** trong danh sách chờ | `PriorityBlockingQueue`/queue có comparator, tie-break và aging | Có chống starvation? Đa worker có làm đảo thứ tự hoàn thành? |
| Tác vụ quan trọng cần **độ trễ thấp** khi tải tăng | Tách executor/queue, dự trữ năng lực, backpressure, giới hạn concurrency | p95/p99 latency dưới tải, timeout, saturation |
| Chạy công việc **sau một khoảng trễ** hoặc lặp theo lịch | `ScheduledExecutorService` | Lịch chạy chỉ là mục tiêu, không phải real-time deadline chắc chắn |
| Bảo đảm **A xong mới đến B** | `join`, `Future`, `CompletableFuture`, synchronization primitive | Quan hệ phụ thuộc, xử lý lỗi/hủy |
| Chia tài nguyên **công bằng** | Semaphore/lock có chính sách fairness khi có hỗ trợ, quota, weighted queue | Fairness cũng có chi phí và giới hạn riêng |
| Công việc có **hạn xử lý bắt buộc** | Kiến trúc kiểm soát thời gian thực, phân tích worst-case, admission control | JVM/GC/OS thông thường không tự cấp hard real-time guarantee |

`Thread.setPriority` có thể được thử như một **tinh chỉnh phụ thuộc môi trường** khi đã đo được vấn đề, hiểu chính sách scheduler và chấp nhận kết quả không di động. Nó không nên là cơ chế duy nhất để bảo vệ correctness, fairness hay deadline.

### Checklist khi đánh giá một thay đổi priority

1. Mục tiêu là **thứ tự lấy việc**, **độ trễ**, **CPU share**, hay **tính đúng đắn**? Viết thành yêu cầu kiểm chứng được.
2. Đây là platform thread hay virtual thread? Có trần `ThreadGroup` hoặc chính sách OS/container liên quan không?
3. Sau khi `setPriority`, `getPriority()` trả gì? Có cơ sở nào chứng minh scheduler hưởng ứng?
4. Benchmark có warm-up, nhiều lần chạy, tải đại diện, số lõi và metrics p95/p99/CPU đúng không?
5. Thiết kế có còn chạy **đúng** khi scheduler hoàn toàn bỏ qua priority không?
6. Có phương án queue, executor, giới hạn tải hay đồng bộ rõ ràng dễ bảo trì hơn không?

Nếu câu trả lời cho mục 5 là **không**, thiết kế đang phụ thuộc vào một giả định mà Java không bảo đảm; cần sửa cơ chế phối hợp.

## 10. Lỗi hiểu thường gặp và tự kiểm tra

**Lỗi 1: “`MAX_PRIORITY` là ưu tiên tuyệt đối.”** Không: `10` chỉ là giá trị lớn nhất của thang Java, có thể bị group giới hạn và không suy ra mức native/CPU chắc chắn.

**Lỗi 2: “`getPriority()==10` chứng minh OS ưu tiên đúng.”** Không: getter đọc thuộc tính Java, không đo lịch CPU thực thi.

**Lỗi 3: “Priority cao nên không cần lock/volatile/join.”** Không: dữ liệu chia sẻ và quan hệ phụ thuộc có quy tắc Java Memory Model độc lập với priority.

**Lỗi 4: “Priority queue và thread priority là một.”** Không: queue xác định phần tử nào được lấy trước; thread priority chỉ gợi ý scheduler cho platform thread.

**Lỗi 5: “Mọi thread Java đều chỉnh được priority.”** Không: virtual thread của Java 21+ có priority cố định `5`, yêu cầu thay đổi bị bỏ qua.

**Lỗi 6: “Benchmark một lần là bằng chứng.”** Không: scheduler, tải máy, JIT và khác biệt JVM/OS khiến kết quả có thể thay đổi.

Câu hỏi tự kiểm tra:

1. Nếu thread `T1` priority `10` đang đợi monitor của `T2` priority `1`, priority có bảo đảm `T1` giành được monitor trong 10 ms không? **Không.**
2. Nếu `T1` in trước `T2` ở 100 lượt thử, có thể dùng priority để bảo đảm thứ tự in ở production không? **Không.**
3. Khi cần tác vụ `urgent` được chọn từ các tác vụ *đang xếp hàng* trước `normal`, cơ chế nào diễn đạt trực tiếp quy tắc này? **Hàng đợi ưu tiên có comparator.**
4. Trong Java 21, `setPriority(10)` trên virtual thread khiến `getPriority()` trả bao nhiêu? **`5`.**

## Tham khảo API chính thức

- [`Thread` — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html): `MIN_PRIORITY`, `NORM_PRIORITY`, `MAX_PRIORITY`, `setPriority`, `getPriority`, platform/virtual thread.
- [`PriorityBlockingQueue` — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/PriorityBlockingQueue.html): cơ chế xếp thứ tự và đặc tính không giới hạn capacity.
- [Java Language Specification `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html): memory model, *happens-before*, synchronization.

**Ghi nhớ:** Priority là thuộc tính scheduling có giới hạn; mọi điều kiện về thứ tự, bộ nhớ, độ trễ và hủy tác vụ quan trọng đều cần một cơ chế có hợp đồng rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-api-005">Platform thread và chi phí tạo tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Platform thread và chi phí tạo tài nguyên trong Java

## 1. Mục tiêu và phạm vi

Sau bài này, bạn có thể giải thích platform thread gắn với luồng thực thi của hệ điều hành như thế nào; phân biệt đối tượng Thread, luồng native và CPU core; xác định các nguồn chi phí khi tạo, duy trì, lập lịch, chặn và hủy thread; lựa chọn cách giới hạn concurrency cho ứng dụng Java; đo đạc trước khi tối ưu. Nội dung tập trung vào **Java SE và JVM**, với ví dụ tương thích **Java 17+**.

Câu hỏi cốt lõi: vì sao tạo một platform thread cho mỗi công việc có thể hoạt động tốt với vài chục công việc nhưng trở thành gánh nặng khi hàng nghìn yêu cầu cùng chờ I/O?

## 2. Platform thread là gì?

Trong các JVM phổ biến như HotSpot, **platform thread** là Java thread được JVM ánh xạ gần với mô hình **một Java platform thread ↔ một native OS thread**. Hệ điều hành cấp tài nguyên và lập lịch native thread lên các CPU logic. JVM quản lý ngữ nghĩa Java: thực thi mã, trạng thái Thread, interrupt, Java Memory Model, stack trace và tương tác với garbage collector.

Ba khái niệm cần tách biệt:

| Thành phần | Vai trò |
|---|---|
| Đối tượng java.lang.Thread | Đối tượng Java trên heap dùng để định danh và điều khiển thread (start, join, interrupt, getState...). |
| Native/platform execution thread | Ngữ cảnh chạy do JVM/hệ điều hành hỗ trợ, có stack và trạng thái lập lịch riêng; được tạo khi khởi động thread thực sự. |
| CPU core / logical processor | Tài nguyên xử lý có thể thực thi mã tại một thời điểm. Số thread có thể lớn hơn số core rất nhiều; chỉ một phần thread đang chạy đồng thời trên CPU. |

Với đối tượng Thread mới, lệnh new Thread(runnable) **chưa** tạo một luồng thực thi đang chạy. Lệnh start() yêu cầu JVM khởi động thread mới và gọi run() trên thread đó. Gọi trực tiếp run() chỉ là lời gọi phương thức bình thường trên thread hiện tại.

Các trạng thái Java như NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED mô tả vòng đời quan sát từ Java. **RUNNABLE không bảo đảm đang sử dụng CPU**: nó có thể đang chạy hoặc sẵn sàng chờ hệ điều hành xếp lịch. Java Thread.State không phải bản sao một-một của mọi trạng thái scheduler native.

## 3. Những tài nguyên một platform thread tiêu thụ

Tạo platform thread không chỉ cấp phát một đối tượng Java. Chi phí thực tế thay đổi theo JVM, hệ điều hành, kiến trúc CPU, tùy chọn chạy và cách ứng dụng sử dụng thread.

| Nguồn tài nguyên | Khi nào phát sinh? | Ý nghĩa |
|---|---|---|
| Đối tượng Thread và dữ liệu liên quan trên Java heap | Khởi tạo đối tượng và liên kết các cấu trúc quản lý | Tăng số đối tượng cần theo dõi/thu gom; bản thân đối tượng không phản ánh toàn bộ chi phí. |
| Native thread và cấu trúc quản lý của OS/JVM | Khi start() tạo thành công luồng thực thi | Có chi phí tạo, hủy và giới hạn số native thread/process của môi trường chạy. |
| Stack riêng của thread | Khi native thread được khởi tạo | Giữ local variables, lời gọi hàm, địa chỉ trở về, frame Java/native; tiêu tốn vùng địa chỉ và một phần bộ nhớ vật lý khi sử dụng. |
| Dữ liệu TLS/ThreadLocal và ngữ cảnh thực thi | Khi thread hoạt động hoặc ứng dụng gán dữ liệu | Có thể giữ tham chiếu lâu hơn dự kiến, đặc biệt trong thread pool. |
| Thời gian CPU lập lịch | Khi có nhiều thread sẵn sàng chạy | OS phải phân phối thời gian chạy; nhiều context switch có thể giảm locality của cache. |
| Tương tác runtime (GC, safepoint, quan sát stack...) | Khi JVM cần duyệt hoặc phối hợp với các thread | Số thread và số frame đang sống có thể tăng chi phí quản lý/quan sát. |

### Stack: kích thước đặt trước khác bộ nhớ RAM thực dùng

Mỗi platform thread có stack riêng, không chia sẻ stack của thread khác. JVM/OS thường **dành sẵn vùng không gian địa chỉ** cho stack; số byte đã chạm tới hoặc thực sự được commit có thể nhỏ hơn phần reserved. Không được đồng nhất **reserved virtual memory** với **resident physical memory (RSS)**.

Tùy JVM/nền tảng, tùy chọn -Xss có thể điều chỉnh kích thước stack của Java platform thread. Ví dụ **giả định** mỗi stack được dành 1 MiB, 10.000 thread có thể tương ứng khoảng 10.000 MiB (xấp xỉ 9,77 GiB) **không gian địa chỉ dành cho stack**; đây **không phải khẳng định** tiến trình đã tiêu thụ 9,77 GiB RAM. Kích thước mặc định, guard pages, cách reserve/commit và giới hạn bộ nhớ đều phụ thuộc môi trường.

Stack quá nhỏ làm tăng nguy cơ StackOverflowError khi call stack sâu. Stack quá lớn khiến chi phí bảo lưu tài nguyên tăng, hạn chế số thread có thể tạo. Vì thế không chọn -Xss chỉ dựa trên phép nhân lý thuyết.

### Ngay cả thread đang chờ cũng chiếm tài nguyên

Một platform thread đợi socket, file, khóa, Condition hoặc sleep thường **không liên tục đốt CPU trong khi đợi**; tuy nhiên nó vẫn sở hữu ngữ cảnh thread, stack và các cấu trúc liên quan. Tỷ lệ thời gian chờ lớn khiến mô hình “mỗi request một platform thread” dễ cần số lượng thread lớn mà throughput CPU không tăng tương ứng.

Thread đang BLOCKED để vào synchronized khác với thread TIMED_WAITING do Thread.sleep(...) hoặc Object.wait(timeout). Cả ba trường hợp đều giữ tài nguyên thread trong lúc không làm công việc CPU hữu ích. Trạng thái cụ thể giúp chẩn đoán nguyên nhân chờ; không dùng mỗi số lượng thread để kết luận ứng dụng tắc nghẽn.

## 4. Tạo thread và chuyển ngữ cảnh tốn gì?

Một chu trình có thể bao gồm:

1. Tạo Thread object và liên kết Runnable hoặc logic run().
2. start() yêu cầu JVM/OS khởi tạo native thread, stack và dữ liệu điều khiển.
3. OS đưa thread vào hàng lập lịch; thread thực thi run() khi được cấp CPU.
4. Thread có thể chạy, chờ I/O/khóa, được đánh thức và bị hoán đổi CPU nhiều lần.
5. Khi run() kết thúc, thread đi đến TERMINATED; native resources được giải phóng theo cơ chế của runtime/OS, còn đối tượng Java chỉ được GC khi không còn tham chiếu tới nó.

Không có **một con số mili giây cố định** cho start() hoặc context switch trên mọi máy. Tạo thread có latency, bộ nhớ và hoạt động kernel; context switch còn phải lưu/khôi phục ngữ cảnh, có thể làm mất cache locality và gây cạnh tranh scheduler. Cường độ ảnh hưởng tăng khi có quá nhiều thread RUNNABLE so với CPU thực sự sẵn có.

Phân biệt hai vấn đề:

- **Tạo quá nhiều thread**: tốn native resource, stack, thời gian khởi tạo/hủy, dễ đụng giới hạn môi trường.
- **Có quá nhiều thread cùng tranh CPU**: tăng scheduling và context switching, có thể giảm throughput dù công việc mỗi thread không đổi.

Thread đang đợi I/O không nhất thiết tạo nhiều context switch liên tục; ngược lại hàng nghìn thread chạy vòng lặp CPU cùng lúc có thể làm scheduler quá tải. Dựa vào profile và workload thật để phân biệt.

## 5. Ví dụ Java 17+: 12 tác vụ, 3 platform worker

Ví dụ này tạo **12 công việc** mô phỏng thời gian chờ nhưng chỉ dùng tối đa **3 platform thread** trong pool. ThreadFactory đặt tên và đếm đúng số worker được tạo. Hàng đợi hữu hạn (ArrayBlockingQueue) khiến năng lực tiếp nhận công việc được xác định rõ.

Lưu tệp **PlatformThreadCostDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PlatformThreadCostDemo {
    public static void main(String[] args) throws InterruptedException {
        int taskCount = 12;
        int workerCount = 3;
        AtomicInteger createdWorkers = new AtomicInteger();

        ThreadFactory platformFactory = job ->
                new Thread(job, "platform-worker-" +
                        createdWorkers.incrementAndGet());

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(taskCount),
                platformFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );

        CountDownLatch finished = new CountDownLatch(taskCount);

        for (int i = 0; i < taskCount; i++) {
            pool.execute(() -> {
                try {
                    // Mô phỏng công việc phải chờ, không đo năng suất.
                    Thread.sleep(40);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }

        finished.await();
        pool.shutdown();
        boolean terminated = pool.awaitTermination(1, TimeUnit.MINUTES);

        System.out.println("tasksCompleted=" +
                (taskCount - finished.getCount()));
        System.out.println("platformWorkersCreated=" +
                createdWorkers.get());
        System.out.println("executorTerminated=" + terminated);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac PlatformThreadCostDemo.java
java PlatformThreadCostDemo
~~~

Kết quả trong điều kiện thực thi bình thường (không có lỗi cấp phát native thread hay gián đoạn bên ngoài):

~~~text
tasksCompleted=12
platformWorkersCreated=3
executorTerminated=true
~~~

**Giải thích cơ chế:**

- Ba lần execute(...) đầu tiên khiến ThreadPoolExecutor khởi tạo đủ ba core worker; các tác vụ tiếp theo vào hàng đợi có sức chứa 12, đủ cho ví dụ này. Không tạo một thread cho mỗi tác vụ.
- Mỗi worker lấy và xử lý nhiều tác vụ nối tiếp nhau. Lệnh sleep(40) giải phóng CPU để scheduler chạy công việc khác trong thời gian chờ nhưng **worker vẫn là một native thread** đang tồn tại.
- CountDownLatch làm kết quả số công việc hoàn tất độc lập với thứ tự thực thi; mã không suy diễn thời gian chạy hay hiệu năng từ sleep.
- finally bảo đảm đếm xuống ngay cả khi tác vụ bị interrupt. Khi thử nghiệm thực tế, tác vụ cần có chính sách xử lý hủy/interrupt đúng theo yêu cầu nghiệp vụ.
- shutdown() dừng nhận tác vụ mới và để tác vụ đã nhận hoàn tất; awaitTermination(...) đợi pool kết thúc. Trong ứng dụng thật, phải xử lý tình huống timeout và cơ chế shutdown có kiểm soát.

Ví dụ **minh họa reuse và giới hạn số worker**; nó không đo chính xác chi phí tạo OS thread, bộ nhớ stack hay hiệu suất xử lý tác vụ. Benchmark nghiêm túc cần tải đủ lớn, warmup, lặp nhiều lần, cùng giới hạn concurrency và quan sát CPU, RSS, GC, latency, throughput.

## 6. Chiến lược quản lý platform thread

### Tạo mới theo tác vụ hay dùng thread pool?

**Một thread cho mỗi tác vụ** đơn giản về mặt mô hình, nhưng số thread có thể tăng theo số tác vụ đồng thời. Với một lượng nhỏ tác vụ độc lập và sống lâu, thiết kế này có thể phù hợp; với lượng request lớn, khó kiểm soát tài nguyên.

**Thread pool** tái sử dụng một nhóm platform thread để thực hiện nhiều tác vụ. Nó giúp giới hạn số luồng native được tạo và giảm overhead tạo/hủy liên tục. Tuy nhiên, pool **không tự giải quyết quá tải** nếu queue tăng không giới hạn hoặc task giữ khóa/chờ vô thời hạn.

Các tham số phải được hiểu cùng nhau:

| Thành phần | Quyết định |
|---|---|
| corePoolSize / maximumPoolSize | Giới hạn số platform worker tùy cấu hình và trạng thái pool. |
| Queue có giới hạn | Giới hạn số tác vụ đang chờ; nếu đầy phải thực thi chính sách từ chối hoặc điều tiết đầu vào. |
| RejectedExecutionHandler | Xử lý khi task không thể nhận: AbortPolicy ném RejectedExecutionException; CallerRunsPolicy có thể chạy task trên luồng gửi yêu cầu, tạo áp lực ngược. |
| Task timeout, cancellation, interrupt | Tránh giữ worker vô thời hạn và cho phép dừng có kiểm soát. |
| shutdown / awaitTermination / shutdownNow | Quản lý vòng đời pool và xử lý công việc đang chạy/chưa chạy. |

**Cảnh báo về chính sách từ chối:** CallerRunsPolicy làm tác vụ chạy trên caller khi pool còn hoạt động và bão hòa; có thể làm chậm hoặc chặn chính thread tiếp nhận request, nên phải cân nhắc bối cảnh. AbortPolicy yêu cầu caller xử lý RejectedExecutionException. Với hàng đợi không giới hạn như LinkedBlockingQueue mặc định trong Executors.newFixedThreadPool(...), số worker cố định nhưng số task đợi vẫn có thể tăng rất lớn. Ngay cả với bounded queue, phải xác định cách phản hồi và phục hồi khi hệ thống quá tải.

**Ví dụ chọn số worker theo loại tải:**

- **CPU-bound:** Khi tác vụ chủ yếu tính toán và ít chờ, điểm xuất phát hợp lý thường gần số CPU logic thực sự cấp cho tiến trình; sau đó benchmark. Dùng thêm nhiều thread không tạo ra thêm core CPU.
- **I/O-bound:** Tác vụ chờ lâu có thể đạt throughput tốt hơn với số worker lớn hơn số CPU, nhưng native thread vẫn có chi phí. Đo tỉ lệ waiting, độ trễ dịch vụ ngoài, số connection và giới hạn tài nguyên; không tăng worker vô hạn.
- **Tải hỗn hợp:** Tách pool cho công việc CPU và công việc blocking khi cần cô lập độ trễ; theo dõi queue, rejection và saturation thay vì áp cùng một kích thước pool cho mọi việc.

Có thể dùng công thức ước lượng sơ bộ cho workload ổn định: **N ≈ P × (1 + W/C)**, trong đó P là số CPU hữu dụng, W là thời gian chờ trung bình và C là thời gian dùng CPU trung bình của mỗi tác vụ. Đây chỉ là **heuristic khởi điểm**, không phải bảo đảm tối ưu: giới hạn external service, memory, lock contention, burst traffic và SLO thường quan trọng ngang hoặc hơn công thức. Nếu C gần 0, công thức mất ý nghĩa thực tế; hãy đo và đặt trần rõ ràng.

### Backpressure khác với chỉ tăng số thread

Giả sử 200 request/giây đến nhưng backend chỉ xử lý ổn định 100 request/giây. Tăng pool có thể làm backend quá tải nhanh hơn; queue không giới hạn sẽ tích lũy việc chờ, tăng latency và cuối cùng có nguy cơ hết bộ nhớ. Một giải pháp có trách nhiệm cần giới hạn nhận việc, thời gian chờ, timeout, và phản hồi khi quá tải (đợi có giới hạn, giảm tốc phía gửi hoặc từ chối có kiểm soát). Pool chỉ là một thành phần của cơ chế điều tiết.

## 7. Chẩn đoán và đo đạc đúng chi phí

Các tín hiệu đáng quan sát cùng một lúc:

1. **Số thread:** thread đang sống, peak thread count, daemon, số worker theo pool và số native thread của tiến trình.
2. **Trạng thái chờ:** tỉ lệ RUNNABLE/BLOCKED/WAITING/TIMED_WAITING, thread dump lặp lại để thấy trạng thái dai dẳng.
3. **CPU và scheduler:** CPU usage, load, số context switch, số thread sẵn sàng chạy, mức độ cạnh tranh khóa.
4. **Bộ nhớ:** heap, native memory, RSS, virtual address space, stack reservation/commit và các giới hạn OS/container.
5. **Tải nghiệp vụ:** throughput, p50/p95/p99 latency, queue depth, task wait time, rejected tasks, số connection và timeout.

**Trong Java** có thể lấy số liệu cơ bản qua java.lang.management.ThreadMXBean:

~~~java
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public class ThreadMetricsDemo {
    public static void main(String[] args) {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        System.out.println("live=" + threads.getThreadCount());
        System.out.println("peak=" + threads.getPeakThreadCount());
        System.out.println("daemon=" + threads.getDaemonThreadCount());
    }
}
~~~

Lưu thành **ThreadMetricsDemo.java**, chạy javac ThreadMetricsDemo.java rồi java ThreadMetricsDemo. Các con số thay đổi theo JVM và thời điểm nên **không có stdout cố định**. ThreadMXBean đếm live Java threads theo định nghĩa của API, không thay thế bộ đếm native threads của OS hoặc một phép đo memory.

**Công cụ thực tế:** Với JDK có jcmd, lệnh jcmd <pid> Thread.print giúp lấy thread dump; Java Flight Recorder (JFR) hỗ trợ phân tích profile, allocation và chờ/khóa khi cấu hình sự kiện phù hợp. HotSpot Native Memory Tracking (NMT), nếu bật từ lúc khởi động bằng -XX:NativeMemoryTracking=summary, có thể quan sát phân loại bộ nhớ JVM qua jcmd <pid> VM.native_memory summary; tính năng này có overhead và không đồng nghĩa toàn bộ RSS. Trên Linux, công cụ OS như top -H -p <pid> hoặc ps -L -p <pid> hỗ trợ nhìn native threads. Cú pháp và độ sẵn có phụ thuộc JDK/OS.

Để tránh hiểu nhầm các chỉ số:

- **Nhiều WAITING threads:** có thể là pool rảnh và không phải lỗi; xét queue, demand và latency.
- **Nhiều BLOCKED threads:** xem điểm khóa/monitor và độ dài critical section.
- **RUNNABLE đông nhưng CPU bị bão hòa:** xem oversubscription và CPU contention.
- **RSS tăng khi tạo thêm threads:** không tự quy toàn bộ cho stack; đối chiếu heap, native memory và thread count.
- **Lỗi OutOfMemoryError: unable to create native thread** (hoặc thông điệp tương đương): kiểm tra cả thread count, giới hạn OS/container, stack size, vùng nhớ native và chính sách cấp phát; tăng heap đơn thuần thường không giải quyết đúng nguyên nhân.

Nếu benchmark chi phí tạo thread, tách riêng ít nhất ba kịch bản: (a) tạo và join một số lượng thread hữu hạn theo đợt; (b) nhiều task qua pool cố định; (c) tải blocking có số concurrency và timeout kiểm soát. Warmup JVM, đo nhiều lần, báo cấu hình JDK/OS/CPU, số lượng tác vụ, throughput và tail latency; không so sánh dựa trên một lần chạy hoặc số sleep giả lập.

## 8. Những lỗi thiết kế thường gặp

**1. Đồng nhất Thread object với native thread đang chạy.** new Thread(...) chỉ tạo đối tượng; start() mới yêu cầu chạy song song. start() lần hai trên cùng Thread ném IllegalThreadStateException. Thread hoàn thành không thể khởi động lại; muốn reuse **worker** thì dùng executor.

**2. “Một request = một platform thread” trong mọi tình huống.** Khi nhiều request cùng đợi I/O, native resources tăng mạnh. Thiết kế nên đặt giới hạn concurrency, queue và timeout theo khả năng hệ thống, hoặc cân nhắc mô hình concurrency khác khi phù hợp.

**3. Xem thread pool là tài nguyên miễn phí.** Pool cố định chặn số worker, nhưng hàng đợi không giới hạn và task bị kẹt vẫn gây memory/latency problem. Cần đo cả queue wait và rejection.

**4. Bỏ qua interrupt và shutdown.** Thread.interrupt() là tín hiệu hợp tác, không “giết” thread tức thì. sleep/wait/join có thể ném InterruptedException; khi không xử lý đầy đủ, thường cần khôi phục interrupt flag bằng Thread.currentThread().interrupt() rồi thoát hoặc trả quyền quyết định cho tầng gọi.

**5. Đặt biến dữ liệu lớn trong ThreadLocal của pool.** Worker sống lâu có thể giữ dữ liệu giữa các task; phải remove() trong finally khi kết thúc scope sử dụng. Tránh rò rỉ thông tin request hoặc classloader references qua thread reuse.

**6. Dựa vào Thread.setPriority(...) để sửa lỗi hiệu năng.** Mức ưu tiên có ý nghĩa phụ thuộc OS/JVM và không phải cơ chế đảm bảo fairness, deadline hay throughput. Đo scheduler và thiết kế tải trước.

**7. Lạm dụng daemon threads cho công việc quan trọng.** JVM có thể kết thúc khi chỉ còn daemon threads; không dựa vào daemon để hoàn tất ghi dữ liệu, transaction hoặc shutdown có thứ tự.

**8. Tối ưu -Xss tùy tiện.** Giảm stack có thể làm hỏng workload gọi sâu; tăng stack có thể giảm số native threads khả dụng. Phải kiểm tra stack depth, giới hạn bộ nhớ và lỗi thực tế trên môi trường triển khai.

## 9. Platform thread và virtual thread: liên hệ cần biết

Từ **Java 21**, virtual threads là tính năng chính thức, cho phép nhiều Java thread nhẹ được JVM lập lịch trên số platform carrier threads nhỏ hơn. Với nhiều công việc chủ yếu **chờ I/O**, virtual thread có thể giảm đáng kể chi phí dành một native thread cho mỗi tác vụ; tuy vậy **không biến CPU core thành tài nguyên vô hạn** và không tự giải quyết việc backend hoặc connection pool bị giới hạn.

Platform thread vẫn quan trọng để chạy mã CPU-bound, làm carrier cho virtual threads và thực thi các executor có kiểm soát. Không so sánh “virtual luôn nhanh hơn platform”: đo throughput/latency trong workload thật, phân biệt concurrency lớn do thời gian chờ với song song thực sự trên CPU. Khi làm việc với JDK 17, virtual threads chưa có dưới dạng tính năng chuẩn; ví dụ ở bài này dùng platform threads.

## 10. Checklist áp dụng

Trước khi quyết định tạo nhiều platform threads, tự trả lời:

- Công việc chiếm CPU hay chờ I/O/khóa? Thời gian chờ chiếm bao nhiêu?
- Cần tối đa bao nhiêu công việc đang chạy, bao nhiêu công việc được xếp hàng, và khi đầy thì hệ thống phản hồi thế nào?
- Có timeout, interrupt, hủy và shutdown rõ ràng không?
- Queue, thread count, stack/native memory, CPU, context switches, p95/p99 latency đang được đo ra sao?
- Số worker có được thử dưới tải mục tiêu, spike traffic và giới hạn container/OS thật không?

**Ghi nhớ:** Platform thread là đơn vị thực thi gắn với native OS thread nên có giá tài nguyên riêng. Thread đang chờ có thể ít dùng CPU nhưng không miễn phí; tăng thread count vượt nhu cầu không đồng nghĩa tăng tốc. Tái sử dụng worker, giới hạn concurrency và backpressure, đo bằng số liệu thực tế là nền tảng để vận hành Java concurrency ổn định.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-api-006">Daemon thread và user thread khi JVM kết thúc</a>

<details>
<summary>Xem chi tiết</summary>

# Daemon thread và user thread khi JVM kết thúc

Trong Java, một luồng (`Thread`) có thể là **user thread** (luồng thông thường, không phải daemon) hoặc **daemon thread** (luồng nền). Sự khác biệt quan trọng nhất là **JVM có chờ luồng đó trước khi tự kết thúc hay không**. Đây là quy tắc về vòng đời tiến trình Java, không phải cách phân loại mức độ ưu tiên CPU hay độ quan trọng của công việc.

## 1. Quy tắc JVM kết thúc

Trong trường hợp chương trình kết thúc bình thường, JVM bắt đầu quá trình shutdown khi **không còn luồng non-daemon nào đang sống**. JVM không cần đợi các daemon thread hoàn thành. Có thể hình dung:

| Trạng thái | JVM có thể tự kết thúc? | Giải thích |
|---|---|---|
| `main` đã trả về, còn một user thread đang chạy/chờ | Chưa | User thread vẫn giữ JVM sống. |
| `main` đã trả về, chỉ còn daemon thread | Có | Daemon không giữ JVM sống; có thể bị dừng giữa chừng. |
| Tất cả user thread đều đã kết thúc | Có | JVM bắt đầu shutdown thông thường. |
| Có lời gọi `System.exit(status)` | Có, bắt đầu shutdown | Lời gọi này chủ động khởi động shutdown ngay cả khi vẫn có user thread. |
| Có lời gọi `Runtime.getRuntime().halt(status)` | Kết thúc cưỡng bức | Bỏ qua quá trình shutdown thông thường, kể cả shutdown hooks. |

**Phân biệt hai thời điểm:** phương thức `main()` kết thúc **không đồng nghĩa** tiến trình JVM kết thúc. `main` thường chạy trên một user thread. Khi `main` trả về, các user thread khác vẫn có thể tiếp tục chạy; khi chỉ còn daemon, JVM có thể đóng tiến trình mà không chờ chúng.

Quy tắc trên nói về điều kiện JVM duy trì sự sống của tiến trình; nó **không** bảo đảm mọi công việc trên user thread hoàn thành trong mọi hoàn cảnh. `System.exit`, `halt`, lỗi nghiêm trọng của tiến trình hay việc hệ điều hành chấm dứt tiến trình có thể khiến công việc chưa xong.

## 2. Daemon được tạo và kiểm tra như thế nào?

Với **platform thread** thông thường của Java:

- `thread.isDaemon()` trả về trạng thái daemon của một luồng. `Thread.currentThread().isDaemon()` kiểm tra luồng đang thực thi.
- `new Thread(...)` mặc định **kế thừa trạng thái daemon của luồng tạo nó**, trừ khi gọi `setDaemon(...)` để đổi trước khi chạy. Vì `main` thông thường là user thread, luồng được tạo trực tiếp từ `main` thường cũng là user thread.
- `thread.setDaemon(true)` đánh dấu luồng là daemon; `thread.setDaemon(false)` đánh dấu là user thread. Phải thiết lập **trước `start()`**; đổi trên một luồng đang chạy có thể ném `IllegalThreadStateException`.
- `thread.start()` tạo hoạt động chạy bất đồng bộ; gọi trực tiếp `thread.run()` chỉ chạy phương thức như một lời gọi bình thường trên luồng hiện tại.
- `isAlive()` phản ánh luồng đã bắt đầu và chưa kết thúc, **độc lập** với `isDaemon()`. Daemon vẫn có thể ở trạng thái `RUNNABLE`, `WAITING` hoặc `TIMED_WAITING`.

Tên gọi *daemon* **không** có nghĩa JVM ưu tiên chạy nó ít hơn, tự ngắt (interrupt) nó, hoặc gọi `finally` để dọn dẹp trước khi thoát. Daemon vẫn chạy theo cơ chế lập lịch luồng thông thường khi tiến trình còn sống.

> Phạm vi phiên bản: các ví dụ bên dưới dùng Java 17+ với platform threads. Từ **Java 21**, virtual threads luôn là daemon và không giữ JVM sống; API tạo virtual thread không có trong Java 17.

## 3. Ví dụ 1 — User thread có thể giữ JVM sống sau `main`

Tạo tệp `UserThreadExample.java`:

```java
import java.util.concurrent.CountDownLatch;

public class UserThreadExample {
    public static void main(String[] args) {
        CountDownLatch release = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            try {
                release.await();
                System.out.println("User worker finished");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "user-worker");

        System.out.println("worker.isDaemon() = " + worker.isDaemon());
        worker.start();
        System.out.println("Main is finishing");
        release.countDown();
    }
}
```

Biên dịch và chạy:

```bash
javac UserThreadExample.java
java UserThreadExample
```

Kết quả:

```text
worker.isDaemon() = false
Main is finishing
User worker finished
```

`worker` mặc định không phải daemon do được tạo từ `main`. `CountDownLatch` giữ worker chờ cho tới khi dòng `Main is finishing` được in, vì vậy thứ tự thông báo là xác định trong lần chạy bình thường. Phương thức `main` **không gọi `join()`**; JVM vẫn phải tính đến worker nếu worker còn sống sau khi `main` trả về. Lưu ý: một lịch chạy cụ thể cũng có thể cho worker hoàn thành trước khi `main` thực sự trả về; điều đó không thay đổi quy tắc JVM.

Hãy thử thay `release.countDown();` bằng một thao tác không bao giờ mở khóa latch: worker non-daemon sẽ chờ mãi và tiến trình có thể không tự thoát. Đó là **minh họa để suy luận**, không nên để chương trình treo khi thực hành.

## 4. Ví dụ 2 — Daemon không giữ JVM sống

Tạo tệp `DaemonThreadExample.java`:

```java
import java.util.concurrent.CountDownLatch;

public class DaemonThreadExample {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);

        Thread background = new Thread(() -> {
            System.out.println("Daemon started");
            started.countDown();

            try {
                neverReleased.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                System.out.println("Daemon cleanup");
            }
        }, "background");

        background.setDaemon(true); // Phải trước start().
        background.start();
        started.await();
        System.out.println("Main is finishing");
    }
}
```

```bash
javac DaemonThreadExample.java
java DaemonThreadExample
```

Đầu ra khi chạy bình thường:

```text
Daemon started
Main is finishing
```

Latch `started` giúp bảo đảm daemon đã in dòng đầu trước khi `main` tiếp tục. `neverReleased` không bao giờ được mở, nên daemon vẫn chờ. Khi `main` kết thúc, không còn user thread do ứng dụng tạo giữ JVM sống và JVM có thể đóng tiến trình. Dòng `Daemon cleanup` **không được bảo đảm xuất hiện**: JVM không cần đánh thức daemon và không bảo đảm thực thi `finally` của luồng đang chờ.

Điểm cần nhớ: `setDaemon(true)` giải quyết chuyện **luồng có giữ tiến trình sống hay không**; nó **không phải** cơ chế đóng luồng sạch sẽ. Nếu daemon đang ghi tệp, phát sự kiện, gửi dữ liệu hoặc cập nhật trạng thái, công việc có thể bị bỏ dở.

## 5. Ví dụ 3 — Chủ động dừng luồng rồi đợi hoàn thành

Nếu muốn dọn dẹp chắc chắn theo luồng điều khiển bình thường, ứng dụng phải tự phối hợp dừng và đợi worker hoàn tất. Đây là ví dụ dùng một tín hiệu dừng và `join()`, dù worker được đánh dấu daemon.

Tạo tệp `GracefulDaemonExample.java`:

```java
import java.util.concurrent.CountDownLatch;

public class GracefulDaemonExample {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch stop = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            try {
                System.out.println("Worker started");
                started.countDown();
                stop.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                System.out.println("Worker cleaned up");
            }
        }, "cleanup-worker");

        worker.setDaemon(true);
        worker.start();

        started.await();
        System.out.println("Requesting stop");
        stop.countDown();
        worker.join();
        System.out.println("Main finished after join");
    }
}
```

```bash
javac GracefulDaemonExample.java
java GracefulDaemonExample
```

Kết quả:

```text
Worker started
Requesting stop
Worker cleaned up
Main finished after join
```

`stop.countDown()` là tín hiệu để worker thoát chờ; `worker.join()` khiến `main` đợi worker kết thúc, do đó dòng dọn dẹp được in trước thông báo cuối. `join()` dùng được với **cả daemon và user thread**. Trường hợp thực tế cũng cần chiến lược xử lý khi worker không chịu dừng (ví dụ thời hạn chờ bằng `join(timeout)`, ghi log lỗi và quyết định mức độ an toàn của việc thoát).

**Không dùng `Thread.stop()`** để cưỡng ép dừng: API này đã lỗi thời và không an toàn. Với công việc thực tế, dùng cơ chế hủy hợp tác như cờ trạng thái có bảo đảm hiển thị bộ nhớ (`volatile`/`AtomicBoolean`), `interrupt()`, hàng đợi/tín hiệu hoặc API quản lý vòng đời phù hợp. Nếu xử lý `InterruptedException` mà không tiếp tục ném lỗi, thường nên khôi phục cờ interrupt bằng `Thread.currentThread().interrupt()`.

## 6. Những trường hợp dễ nhầm

### `main` trả về nhưng ứng dụng vẫn chạy

Nguyên nhân thường là một user thread đang chờ khóa, chờ I/O hoặc chạy vòng lặp không có điều kiện dừng. Đặt tên thread và quan sát trạng thái bằng thread dump (`jcmd <pid> Thread.print` trên JDK phù hợp), rồi xác định thành phần nào tạo ra luồng đó. Việc đánh dấu toàn bộ luồng thành daemon chỉ để ứng dụng thoát có thể che giấu lỗi quản lý tài nguyên.

### Executor và thread pool

Khi tạo `ExecutorService`, cần quản lý thời điểm không nhận thêm việc và thời điểm dừng worker, ví dụ `shutdown()`, `awaitTermination(...)` và khi thật sự cần thiết mới xem xét `shutdownNow()`. Với các thread factory thường gặp, worker của pool có thể là non-daemon và giữ JVM sống. Không nên dựa vào việc `main` kết thúc để tự dọn pool. `shutdownNow()` chỉ **cố gắng** hủy tác vụ đang chạy, thường thông qua interrupt; tác vụ phớt lờ interrupt có thể tiếp tục.

### Shutdown hook không làm daemon trở nên an toàn tuyệt đối

`Runtime.getRuntime().addShutdownHook(thread)` đăng ký một luồng **chưa được `start()`** để JVM khởi chạy trong quá trình shutdown thông thường. Hook hữu ích để phát tín hiệu ngừng và thu hồi tài nguyên có kiểm soát, nhưng các hook có thể chạy đồng thời, có thể bị treo, và không chạy khi `Runtime.halt()` hoặc khi tiến trình bị kết thúc cưỡng bức. Không giả định daemon sẽ được đợi hay toàn bộ `finally` của daemon sẽ chạy chỉ vì có shutdown hook.

### `System.exit(0)` và `return` là hai việc khác nhau

- `return` trong `main`: chỉ kết thúc lời gọi `main`; JVM tiếp tục nếu còn user thread.
- `System.exit(0)`: yêu cầu JVM bắt đầu quá trình shutdown thông thường dù có user thread đang chạy; các shutdown hook đã đăng ký có cơ hội thực thi.
- `Runtime.getRuntime().halt(0)`: dừng cưỡng bức, bỏ qua shutdown hooks; không dùng như giải pháp quản lý luồng hằng ngày.

### Daemon khác với độ ưu tiên và tính đúng đắn của dữ liệu

`setDaemon(true)` không thay `setPriority(...)`, không bảo đảm chạy theo lịch biểu cụ thể và không bảo đảm hoàn thành task. Một daemon giữ khóa hoặc đang cập nhật dữ liệu vẫn có thể ảnh hưởng các user thread trong lúc JVM còn chạy. Với nhiệm vụ bắt buộc hoàn thành, cần có điểm chờ hoàn tất rõ ràng, cơ chế lưu bền và xử lý lỗi phù hợp.

## 7. Cách chọn trong chương trình Java

| Loại công việc | Lựa chọn thường hợp lý | Điều kiện quan trọng |
|---|---|---|
| Tính toán hoặc ghi dữ liệu bắt buộc hoàn thành trước khi thoát | User thread, hoặc executor được quản lý rõ vòng đời | Dừng nhận việc, chờ hoàn thành, xử lý lỗi. |
| Giám sát nền mang tính phụ trợ, có thể bỏ dở khi tiến trình đóng | Có thể dùng daemon | Không đặt tính đúng đắn của dữ liệu vào việc daemon chạy đến cuối. |
| Nền định kỳ nhưng phải flush dữ liệu/báo cáo | Chọn theo yêu cầu hoàn thành và quản lý shutdown riêng | Có tín hiệu dừng và chờ hoàn thành phần quan trọng. |
| Thread pool xử lý nhiều tác vụ | Quản lý executor tường minh | Gọi shutdown và đợi theo thời hạn; hiểu hành vi thread factory. |

Câu hỏi quyết định là: **“Nếu JVM đóng ngay sau khi tất cả user thread kết thúc, công việc của luồng này có thể bị bỏ dở mà vẫn chấp nhận được không?”** Nếu câu trả lời là không, không được chỉ dựa vào daemon. Ngược lại, một luồng non-daemon cũng cần cơ chế dừng để tránh giữ ứng dụng sống vô hạn.

## 8. Tự kiểm tra kiến thức

1. **Vì sao in xong dòng cuối của `main` mà tiến trình chưa thoát?** Vì vẫn có thể còn user thread đang sống; kiểm tra thread dump và vòng đời executor/worker.
2. **`setDaemon(true)` có khiến `finally` của worker luôn chạy?** Không. JVM không đợi daemon và có thể dừng tiến trình khi daemon đang chờ hoặc đang xử lý.
3. **Đặt `setDaemon(true)` sau `start()` được không?** Không được dùng cách đó; phải cấu hình trước khi start, nếu cố thay khi thread đang sống sẽ gặp `IllegalThreadStateException`.
4. **Có thể dùng `join()` với daemon không?** Có. Daemon mô tả điều kiện giữ JVM sống; `join()` là lời gọi đợi một thread kết thúc.
5. **Tại sao `System.exit(0)` khác `return` ở `main`?** `System.exit` chủ động khởi động shutdown; `return` chỉ trả về từ `main`.

**Ghi nhớ:** user thread có thể giữ JVM sống; daemon thread không giữ JVM sống; **muốn kết thúc công việc an toàn phải điều phối dừng và chờ hoàn thành một cách tường minh**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-api-007">Đặt tên luồng và thiết lập UncaughtExceptionHandler</a>

<details>
<summary>Xem chi tiết</summary>

# Đặt tên luồng và thiết lập UncaughtExceptionHandler trong Java

## 1. Mục tiêu bài học

Khi nhiều luồng cùng hoạt động, tên luồng giúp người phát triển xác định **luồng nào đang thực hiện công việc gì** trong log, debugger, thread dump và công cụ giám sát. `Thread.UncaughtExceptionHandler` cung cấp một điểm **ghi nhận lỗi cuối cùng** khi một `Throwable` thoát khỏi phương thức chạy của luồng mà không được bắt.

Sau bài này, bạn cần có khả năng:

- Tạo và đổi tên một `Thread`; chọn tên có ý nghĩa khi xử lý đồng thời.
- Đọc tên của luồng hiện tại bằng `Thread.currentThread().getName()` và phân biệt tên luồng với định danh của một tác vụ.
- Cài handler cho một luồng riêng lẻ hoặc handler mặc định của JVM, hiểu thứ tự lựa chọn handler.
- Hiểu khi nào handler được gọi, khi nào lỗi nằm trong `Future` hoặc `CompletableFuture` và không đi qua handler.
- Viết ví dụ Java 17+ có thể chạy, rồi xác minh tên luồng và đường đi của exception.

Bài tập trung vào **Thread API và concurrency Java nói chung**, không đi vào cơ chế bất đồng bộ hoặc cấu hình executor của Spring.

## 2. Tại sao tên luồng quan trọng?

JVM có thể chạy nhiều thread với các nhiệm vụ khác nhau: nhận dữ liệu, xử lý tệp, tính toán, đọc hàng đợi, dọn dẹp hoặc thực hiện công việc nền. Nếu log chỉ hiển thị `Thread-0`, `Thread-1`, người đọc khó biết nguồn gốc và vai trò của một luồng khi lỗi xảy ra.

Ví dụ một thông báo như:

~~~text
[import-parser-2] Invalid input at row 18
~~~

thường dễ truy vết hơn:

~~~text
[Thread-7] Invalid input at row 18
~~~

Tên luồng đặc biệt hữu ích khi:

1. **Đọc thread dump:** phân biệt luồng đang chờ I/O, giữ lock, chờ queue hoặc bị kẹt.
2. **Tìm log theo thành phần:** nhóm các log của worker theo tiền tố tên.
3. **Gỡ lỗi trong IDE/JFR:** liên hệ một thread với nhóm tác vụ, pool hoặc chức năng.
4. **Điều tra lỗi bất ngờ:** handler có thể ghi cả tên thread và stack trace của exception.

Tên luồng là **thông tin mô tả và hỗ trợ quan sát**, không phải định danh toàn cục có bảo đảm duy nhất, không phải cơ chế đồng bộ, bảo mật hay kiểm soát quyền. Hai thread vẫn có thể có cùng tên; đổi tên không thay đổi thứ tự thực thi hoặc tính thread-safe của code.

## 3. API đặt tên và đọc tên thread

### 3.1. Đặt tên ngay khi tạo thread

Hai cách cơ bản:

~~~java
// Minh họa: Runnable task đã được khai báo.
Thread first = new Thread(task, "file-reader-1");
Thread second = new Thread(task);
second.setName("file-reader-2");
~~~

`new Thread(Runnable, String)` đặt tên tại thời điểm tạo; `setName(String)` đổi tên sau khi tạo. Nên đặt tên **trước `start()`** để mọi sự kiện từ khi luồng bắt đầu đều mang tên mong muốn. Cả hai cách đều không tự chạy task; gọi `start()` mới khởi động một luồng thực thi mới.

`Thread.getName()` đọc tên của một đối tượng `Thread`. Bên trong task đang chạy, `Thread.currentThread().getName()` trả về tên của **luồng thực thi hiện tại**. Không nên dùng biến `thread` từ bên ngoài để thay cho `currentThread()` khi task có thể chạy trên nhiều thread.

### 3.2. Đổi tên khi luồng đang chạy

Java cho phép gọi `setName` trên một thread đã khởi động. Tuy nhiên, tên có thể đổi giữa các thời điểm ghi log, khiến việc ghép dấu vết khó hiểu. Với worker dùng lại trong pool, đổi tên theo từng yêu cầu mà quên khôi phục còn làm log của tác vụ sau mang tên tác vụ trước.

Nếu thật sự cần đổi tên tạm thời, phải khôi phục bằng `finally`:

~~~java
// Minh họa nằm trong một task đang chạy, không phải chương trình độc lập.
Thread current = Thread.currentThread();
String previousName = current.getName();
try {
    current.setName("image-transform-temporary");
    processImage(); // Phương thức do ứng dụng định nghĩa.
} finally {
    current.setName(previousName);
}
~~~

Trong phần lớn hệ thống, cách ổn định hơn là giữ **tên worker** cố định và đưa request ID, job ID hoặc correlation ID vào cấu trúc log/context riêng. Không đặt dữ liệu nhạy cảm, token hoặc thông tin cá nhân vào tên luồng vì tên có thể xuất hiện trong thread dump và hệ thống quan sát.

### 3.3. Quy ước đặt tên

Một quy ước dễ đọc là `<thành-phần>-<vai-trò>-<số-thứ-tự>`. Ví dụ:

| Tên | Vai trò gợi ý |
| --- | --- |
| `import-parser-1` | Worker thứ nhất phân tích dữ liệu nhập |
| `report-writer-2` | Worker thứ hai tạo báo cáo |
| `socket-reader-1` | Worker đọc dữ liệu mạng |
| `batch-cpu-3` | Worker xử lý tính toán |

Đặt tên ngắn, ổn định, có tiền tố đặc trưng và không trộn tên **thread** với mã **task**. Thứ tự các số trong tên không nói lên thứ tự chạy hay hoàn tất.

## 4. Ví dụ 1 — đặt tên và bắt lỗi không được xử lý (Java 17+)

Tạo tệp `NamedThreadDemo.java`:

~~~java
public class NamedThreadDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            System.out.println(
                "Running: " + Thread.currentThread().getName()
            );
            throw new IllegalStateException("invalid payload");
        }, "import-validator-1");

        worker.setUncaughtExceptionHandler((thread, error) -> {
            System.out.println(
                "Uncaught in " + thread.getName() + ": "
                + error.getClass().getSimpleName()
                + " (" + error.getMessage() + ")"
            );
        });

        worker.start();
        worker.join();
        System.out.println(
            "Main finished; worker isAlive=" + worker.isAlive()
        );
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac NamedThreadDemo.java
java NamedThreadDemo
~~~

Đầu ra tiêu chuẩn (stdout) chính xác:

~~~text
Running: import-validator-1
Uncaught in import-validator-1: IllegalStateException (invalid payload)
Main finished; worker isAlive=false
~~~

Diễn giải:

1. Tên `import-validator-1` được gán trước khi `start()`, nên `currentThread().getName()` bên trong `Runnable` trả về tên đó.
2. `IllegalStateException` thoát khỏi phương thức chạy mà không có `catch`. JVM gọi handler đã đăng ký với `worker`.
3. Handler nhận đúng đối tượng `Thread` và `Throwable`, sau đó in tên, loại lỗi và thông điệp.
4. `join()` khiến luồng `main` đợi `worker` kết thúc, vì vậy dòng cuối được in sau khi worker xử lý xong đường thoát bất thường. `isAlive()` lúc đó là `false`.
5. Handler tùy chỉnh đã xử lý việc báo lỗi, nên không có stack trace mặc định do handler mặc định in ra. Trong hệ thống thật, nên ghi **đầy đủ exception/stack trace** vào bộ ghi log có khả năng chịu lỗi; chỉ in `getMessage()` là chưa đủ để chẩn đoán.

**Lưu ý:** Handler không làm cho task thành công và không khởi động lại thread. Sau khi exception không được bắt thoát ra ngoài, worker chấm dứt. `join()` xác nhận thread kết thúc nhưng không tự báo task thành công; cần một kênh kết quả/trạng thái rõ ràng nếu nghiệp vụ phải biết thất bại.

## 5. UncaughtExceptionHandler hoạt động như thế nào?

### 5.1. Giao diện và thời điểm kích hoạt

`Thread.UncaughtExceptionHandler` là một functional interface với phương thức:

~~~java
// Chữ ký API; không phải một chương trình độc lập.
void uncaughtException(Thread thread, Throwable error);
~~~

JVM thông báo tới handler khi một thread sắp kết thúc do một **uncaught exception** — một `Throwable` đã thoát khỏi đường thực thi của luồng. `Throwable` bao gồm `RuntimeException` và cả `Error`; handler có thể thấy những lỗi nghiêm trọng như `OutOfMemoryError`, nhưng lúc đó không được giả định còn đủ tài nguyên để ghi log hay khôi phục.

Handler **không được gọi** chỉ vì:

- Một phương thức ném exception rồi được `try/catch` xử lý trong cùng luồng.
- Task kết thúc bình thường, trả về `run()` hoặc bị yêu cầu dừng bằng cơ chế hợp lệ.
- Task báo lỗi qua một kênh đã bắt/giữ exception, ví dụ `Future` của `submit()`.
- Có lời gọi `Thread.interrupt()` đơn thuần. Interrupt là tín hiệu phối hợp; nó không tự biến thành uncaught exception.

Nếu gọi `worker.run()` trực tiếp thay vì `worker.start()`, code chạy trên luồng gọi, không tạo luồng mới. Exception thoát khỏi lời gọi `run()` sẽ truyền lên caller theo quy tắc Java thông thường; không nên mong handler của đối tượng `worker` được sử dụng như lúc một thread thực sự chết do exception.

### 5.2. Handler theo từng thread và handler mặc định

| API | Phạm vi | Cách dùng |
| --- | --- | --- |
| `thread.setUncaughtExceptionHandler(handler)` | Một thread cụ thể | Ghi lỗi của worker có vai trò riêng |
| `thread.getUncaughtExceptionHandler()` | Xem handler áp dụng trực tiếp cho thread | Trả handler đã gán riêng; nếu không, trả `ThreadGroup` của thread |
| `Thread.setDefaultUncaughtExceptionHandler(handler)` | Handler mặc định ở mức tiến trình JVM | Điểm ghi nhận cuối cùng cho thread không có xử lý chuyên biệt |
| `Thread.getDefaultUncaughtExceptionHandler()` | Xem handler mặc định toàn cục | Có thể trả `null` nếu chưa được cấu hình |

**Thứ tự quan trọng:**

1. Nếu thread có handler riêng đã đặt bằng `setUncaughtExceptionHandler`, handler đó được chọn.
2. Nếu không, `Thread.getUncaughtExceptionHandler()` chuyển việc xử lý tới `ThreadGroup` của thread. Cơ chế `ThreadGroup.uncaughtException` mặc định có thể ủy quyền cho nhóm cha, rồi handler mặc định của JVM (nếu có); nếu vẫn không có, nó có thể in lỗi ra `System.err`.
3. Một `ThreadGroup` tùy biến có thể ghi đè `uncaughtException` và xử lý theo cách khác. Vì vậy không nên giả định handler mặc định sẽ luôn chạy cho mọi thread.

`ThreadGroup` là cơ chế cũ của Thread API; với code mới, nên ưu tiên cấu hình handler rõ ràng trên thread hoặc qua `ThreadFactory`, và quản lý vòng đời bằng executor hoặc cấu trúc phù hợp.

### 5.3. Có nên đặt handler mặc định?

Nếu ứng dụng muốn có nơi báo lỗi cuối cùng cho các thread chưa có handler riêng, có thể cài:

~~~java
// Minh họa cấu hình khi khởi động ứng dụng, không phải chương trình độc lập.
Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
    System.err.println(
        "Unhandled failure in thread: " + thread.getName()
    );
    error.printStackTrace(System.err);
});
~~~

Đây là **thay đổi trạng thái toàn cục của JVM**. Chỉ thành phần sở hữu vòng đời ứng dụng nên quyết định cài handler mặc định, thường ở lúc khởi động, sau khi xem xét cấu hình và logging sẵn có. Thư viện tái sử dụng không nên tự thay handler mặc định của toàn bộ ứng dụng; nhiều thành phần cùng ghi đè sẽ khiến lỗi đi vào handler không mong muốn.

Không phụ thuộc vào việc `getUncaughtExceptionHandler()` của từng thread sẽ trả trực tiếp handler mặc định: khi không có handler riêng, nó trả `ThreadGroup` như mô tả ở trên.

## 6. Đặt tên và cài handler cho worker tạo bởi ExecutorService

Trong chương trình có nhiều task, thường không tạo một `Thread` thủ công cho mỗi task. `ExecutorService` quản lý worker; muốn đặt tên và handler cho **worker thread**, sử dụng `ThreadFactory` khi tạo executor.

Một factory có thể làm ba việc: tạo `Thread` mới, sinh tên theo vai trò + thứ tự, và đăng ký `UncaughtExceptionHandler` trước khi thread chạy.

### 6.1. Phân biệt `execute()` và `submit()` khi task ném lỗi

Đây là điểm thường gây hiểu nhầm:

- Với `execute(Runnable)` trên `ThreadPoolExecutor` chuẩn, nếu `Runnable.run()` ném `RuntimeException` ra ngoài, worker có thể kết thúc bất thường và handler của worker được gọi. Executor có thể thay worker để tiếp tục duy trì pool.
- Với `submit(Runnable)` hoặc `submit(Callable)`, executor thường bọc task trong `FutureTask`. Exception được **giữ làm trạng thái thất bại của `Future`**. `future.get()` ném `ExecutionException` có `getCause()` là lỗi gốc; handler của worker **không được gọi chỉ vì task đó thất bại**.
- Với `CompletableFuture`, lỗi có thể được lưu trong trạng thái hoàn thành thất bại và quan sát qua `join()`, `get()`, `handle()`, `exceptionally()` hoặc nhánh xử lý lỗi phù hợp; không mặc định trông chờ vào uncaught handler.

Sự khác nhau ở trên mô tả cách hoạt động của các executor chuẩn thường dùng trong JDK, nhất là `ThreadPoolExecutor`. Một `Executor` tự cài đặt có thể chọn cách điều phối/xử lý lỗi riêng. Luôn hiểu hợp đồng của executor cụ thể.

### 6.2. Ví dụ 2 — ThreadFactory, execute và submit (Java 17+)

Tạo tệp `ExecutorFailureDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecutorFailureDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger sequence = new AtomicInteger(1);
        AtomicInteger handlerCalls = new AtomicInteger();
        CountDownLatch handlerCalled = new CountDownLatch(1);

        ThreadFactory factory = task -> {
            Thread worker = new Thread(
                task,
                "report-worker-" + sequence.getAndIncrement()
            );
            worker.setUncaughtExceptionHandler((thread, error) -> {
                handlerCalls.incrementAndGet();
                System.out.println(
                    "Handler: " + thread.getName() + " -> "
                    + error.getClass().getSimpleName()
                );
                handlerCalled.countDown();
            });
            return worker;
        };

        ExecutorService executor =
            Executors.newSingleThreadExecutor(factory);

        try {
            Future<?> failed = executor.submit(() -> {
                throw new IllegalArgumentException("submit failure");
            });

            try {
                failed.get();
            } catch (ExecutionException error) {
                System.out.println(
                    "Future cause: "
                    + error.getCause().getClass().getSimpleName()
                );
            }
            System.out.println(
                "Handler calls after submit: " + handlerCalls.get()
            );

            executor.execute(() -> {
                throw new IllegalStateException("execute failure");
            });

            if (!handlerCalled.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                    "Timed out waiting for uncaught handler"
                );
            }
            System.out.println(
                "Handler calls after execute: " + handlerCalls.get()
            );
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
javac ExecutorFailureDemo.java
java ExecutorFailureDemo
~~~

Khi hai task được chạy theo thứ tự như trong chương trình và handler được gọi trong giới hạn 5 giây, stdout là:

~~~text
Future cause: IllegalArgumentException
Handler calls after submit: 0
Handler: report-worker-1 -> IllegalStateException
Handler calls after execute: 1
~~~

Vì sao kết quả như vậy?

- `submit` tạo `Future` giữ `IllegalArgumentException`; `get()` phát hiện lỗi, nhưng thread xử lý không kết thúc bất thường nên bộ đếm handler vẫn là 0.
- `execute` chạy `Runnable` trực tiếp theo cơ chế của `ThreadPoolExecutor`. `IllegalStateException` thoát khỏi worker, làm worker kết thúc bất thường; handler cài trong `ThreadFactory` được gọi.
- `CountDownLatch` cho phép `main` chờ handler thật sự chạy. Không dùng `Thread.sleep(...)` để đoán handler đã chạy.
- `AtomicInteger` giúp tăng số thứ tự tên hoặc bộ đếm handler an toàn nếu factory/handler được gọi đồng thời. Thread được tạo thay thế sau một lỗi có thể có tên số thứ tự kế tiếp.
- `shutdown()` và `awaitTermination(...)` giải phóng executor có kiểm soát. `shutdownNow()` chỉ **yêu cầu** ngắt công việc còn chạy; không bảo đảm chấm dứt tức thời.

**Phạm vi output:** Đoạn mẫu chạy bằng executor chuẩn của JDK và giả định máy không bị treo/scheduler không trì hoãn quá 5 giây. Khi môi trường không đáp ứng deadline, chương trình sẽ ném lỗi timeout; nó không in giả rằng handler đã chạy.

Quan trọng hơn, ngay cả khi có handler, **ứng dụng vẫn phải quan sát lỗi từ `Future`**. Nếu gọi `submit` rồi bỏ qua `Future`, lỗi của task rất dễ bị bỏ sót.

## 7. Viết handler an toàn và có ích cho vận hành

### 7.1. Handler nên làm gì?

Một handler hữu ích thường thu thập **tên thread, lớp exception, message, stack trace**, ngữ cảnh an toàn đã có sẵn và gửi đến nơi ghi nhận lỗi phù hợp. Nếu cần cảnh báo sức khỏe tiến trình hoặc metrics, chỉ dùng đường xử lý ngắn, có khả năng thất bại an toàn.

Mẫu xử lý về ý tưởng:

~~~java
// Minh họa, logger được cung cấp bởi ứng dụng.
thread.setUncaughtExceptionHandler((failedThread, failure) -> {
    logger.error(
        "Uncaught error in thread " + failedThread.getName(),
        failure
    );
});
~~~

Đoạn này **không độc lập** vì `thread` và `logger` phải được ứng dụng khởi tạo. Truyền nguyên đối tượng `Throwable` giúp logger giữ stack trace, thay vì chỉ ghi một dòng `getMessage()`.

Handler phải ưu tiên **không ném exception mới, không block lâu và không chờ khóa có thể đang được giữ bởi luồng lỗi**. Nếu chính `uncaughtException` ném exception, JVM **bỏ qua exception phát sinh từ handler**, khiến thông tin lỗi gốc có thể bị mất. Khi heap đã cạn hoặc tiến trình ở trạng thái nguy hiểm, việc tạo chuỗi dài, gửi mạng đồng bộ hoặc khởi tạo nhiều đối tượng để ghi log có thể thất bại.

### 7.2. Handler không thay thế kiểm soát lỗi nghiệp vụ

| Vị trí xử lý | Mục đích |
| --- | --- |
| `try/catch` tại nơi hiểu được lỗi | Phục hồi hoặc chuyển lỗi thành kết quả nghiệp vụ |
| `Future.get()` / `CompletableFuture` | Biết task bất đồng bộ thành công hay thất bại |
| `UncaughtExceptionHandler` | Ghi nhận lỗi thoát ra khỏi đường thực thi của thread |
| Quản lý vòng đời executor/task | Quyết định retry, hủy, giới hạn tài nguyên và shutdown |

Không tự động retry một tác vụ từ uncaught handler: trạng thái đã có thể thay đổi một phần, dữ liệu có thể không nhất quán hoặc task có thể không idempotent. Việc khởi chạy lại phải thuộc chính sách nghiệp vụ có kiểm soát.

Một lỗi nghiêm trọng ở thread không nhất thiết chấm dứt JVM; các thread khác có thể tiếp tục chạy. Điều đó **không đồng nghĩa ứng dụng vẫn khỏe mạnh**. Hệ thống cần cơ chế quan sát và quyết định tình trạng phục vụ dựa trên mức độ ảnh hưởng của lỗi.

### 7.3. Phối hợp với interrupt và cancellation

`Thread.interrupt()` gửi tín hiệu interrupt, không tự gọi `UncaughtExceptionHandler`. Ví dụ một task bắt `InterruptedException` rồi thoát bình thường sẽ không kích hoạt handler. Khi không thể ném lại `InterruptedException`, thường nên khôi phục cờ bằng `Thread.currentThread().interrupt()` và kết thúc task hợp lý.

Nếu task ném một `RuntimeException` từ logic xử lý interrupt và exception đó thật sự thoát khỏi worker, handler mới có thể được gọi. Vì vậy không nên dùng handler để theo dõi mọi lần hủy hoặc dừng task.

## 8. Các tình huống dễ mắc lỗi

### Sai lầm 1 — Gọi `run()` thay vì `start()`

~~~java
// Minh họa sự khác biệt, không phải chương trình độc lập.
Thread t = new Thread(task, "background-1");
t.run();    // Chạy ngay trên thread đang gọi.
t.start();  // Mới thực sự khởi động thread riêng (nếu chưa start lần nào).
~~~

Đoạn trên dùng để so sánh hai phương thức; trong chương trình thực tế, **chọn một cách gọi phù hợp**. `start()` không được gọi lần thứ hai trên cùng đối tượng thread, nếu không sẽ ném `IllegalThreadStateException`.

### Sai lầm 2 — Cài handler rồi tưởng mọi exception đều đi qua đó

`try/catch`, `FutureTask` và các cơ chế completion có thể đã giữ lỗi. Khi xử lý task qua executor, hãy dùng `Future.get()` hoặc pipeline xử lý kết quả, đồng thời có cơ chế nhận diện task thất bại từ tầng nghiệp vụ.

### Sai lầm 3 — Cài handler mặc định từ thư viện

`Thread.setDefaultUncaughtExceptionHandler` thay đổi chính sách của **mọi thread trong JVM** theo tuyến xử lý mặc định. Code thư viện không sở hữu toàn bộ vòng đời ứng dụng nên không nên ghi đè handler toàn cục.

### Sai lầm 4 — Ghi lỗi nhưng không ghi stack trace

Chỉ in `exception.getMessage()` làm mất vị trí phát sinh, call stack và chuỗi nguyên nhân `getCause()`. Trong sản phẩm thực, truyền nguyên `Throwable` cho hệ thống logging hoặc dùng `printStackTrace` khi thích hợp.

### Sai lầm 5 — Đặt tên theo request rồi để worker pool tái sử dụng

Worker của pool phục vụ nhiều task theo thời gian. Tên nên mô tả **vai trò của worker**; request/correlation ID cần đi trong log context có vòng đời rõ ràng. Nếu đổi tên tạm thời, khôi phục trong `finally`.

### Sai lầm 6 — Dùng handler như cơ chế khôi phục hoặc cô lập lỗi

Handler là tầng báo cáo cuối, không bảo đảm task có thể chạy lại, không rollback side effect, không bảo vệ shared mutable state và không bảo đảm tiếp tục phục vụ an toàn sau một `Error` nghiêm trọng.

## 9. Liên hệ với Java 21+ và virtual threads

Java 21 đưa virtual threads thành tính năng chính thức. Virtual thread vẫn là đối tượng `Thread`, có thể có tên và `UncaughtExceptionHandler`, nhưng cách tổ chức chúng thường là **một virtual thread cho mỗi task** thay vì một nhóm worker nền tảng tái sử dụng.

Một minh họa API **chỉ dành cho Java 21+**, không chạy trên Java 17:

~~~java
ThreadFactory factory = Thread.ofVirtual()
    .name("io-task-", 0)
    .uncaughtExceptionHandler((thread, error) -> {
        error.printStackTrace(System.err);
    })
    .factory();
~~~

Đoạn mã chỉ khai báo `ThreadFactory`; nó chưa tạo hoặc khởi động task. Trong hệ thống dùng virtual threads, cần phân biệt tên task/virtual thread với tên carrier thread, đồng thời vẫn phải quan sát lỗi trong `Future` nếu executor đóng gói kết quả. Đặt tên hữu ích cho chẩn đoán nhưng không thay thế việc quản lý task và giới hạn tài nguyên.

## 10. Checklist áp dụng và tự kiểm tra

Trước khi triển khai:

1. Mỗi nhóm thread có **tên theo vai trò** dễ nhận biết trong log/thread dump hay chưa?
2. Tên worker có ổn định khi thread được tái sử dụng không? Có tránh dữ liệu cá nhân/secret trong tên không?
3. Handler riêng được cài **trước khi `start()`** hoặc thông qua `ThreadFactory` trước khi executor sử dụng worker chưa?
4. Ứng dụng có hiểu handler mặc định khác handler theo thread và tuyến `ThreadGroup` không?
5. Với `submit` và `CompletableFuture`, lỗi có được lấy và xử lý qua kênh kết quả không?
6. Handler có ghi stack trace mà không gây thêm lỗi, deadlock hoặc blocking kéo dài không?
7. Khi thread chết bất thường, có cơ chế giám sát và quyết định retry/fail job/ảnh hưởng sức khỏe tiến trình không?
8. Executor có kế hoạch shutdown và thời gian chờ hữu hạn không?

**Tình huống kiểm tra kiến thức:**

- Một task do `new Thread(task, "reader-1")` tạo ném `IllegalStateException` chưa được bắt và đã gán handler riêng. **Kết quả:** handler riêng được gọi, nhận thread `reader-1` và exception; thread chấm dứt.
- Một task do `executor.submit(...)` tạo ném `IllegalArgumentException`. **Kết quả:** với executor chuẩn của JDK, `Future.get()` báo `ExecutionException` với nguyên nhân là `IllegalArgumentException`; handler của worker không được gọi chỉ vì task đó thất bại.
- Một thread pool phục vụ nhiều request, mỗi request muốn có ID riêng. **Lựa chọn phù hợp:** đặt tên worker theo vai trò, đưa request ID vào log context với phạm vi/cơ chế dọn dẹp rõ ràng.

Tên luồng giúp quan sát *ai đang thực thi*; `UncaughtExceptionHandler` giúp ghi nhận *lỗi nào đã thoát khỏi thread*. Hai công cụ hỗ trợ chẩn đoán concurrency, nhưng tính đúng đắn và độ tin cậy của hệ thống vẫn cần quản lý kết quả task, shared state, giới hạn tài nguyên và vòng đời thread/executor.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-api-008">ThreadFactory để chuẩn hóa cách tạo luồng</a>

<details>
<summary>Xem chi tiết</summary>

# ThreadFactory để chuẩn hóa cách tạo luồng trong Java

Một ứng dụng Java có nhiều thread cần cách tạo thread nhất quán: tên dễ đọc trong thread dump, chế độ daemon phù hợp, mức ưu tiên hợp lý, xử lý exception, và quy tắc gắn thread với executor. Nếu các nơi trong chương trình trực tiếp gọi `new Thread(...)` với các thiết lập khác nhau, việc vận hành và truy vết sự cố rất khó kiểm soát.

`java.util.concurrent.ThreadFactory` là một **functional interface** cung cấp một điểm tập trung để tạo `Thread`. Nó tách **chính sách tạo thread** khỏi **công việc mà thread sẽ chạy**. Một `ExecutorService` có thể gọi factory mỗi khi cần worker mới; mã gọi executor không cần biết cách đặt tên hoặc cấu hình worker đó.

Bài học này chỉ xét **Java Concurrency và Thread API nói chung**, không triển khai cơ chế quản lý tác vụ riêng của Spring.

## 1. Mục tiêu và mô hình tư duy

Sau bài học, bạn cần phân biệt được các trách nhiệm:

| Thành phần | Trách nhiệm | Không chịu trách nhiệm |
| --- | --- | --- |
| `Runnable` / `Callable` | Mô tả việc cần thực hiện; `Callable` có thể trả kết quả/ném checked exception. | Quyết định cách đặt tên, daemon, số worker. |
| `ThreadFactory` | Tạo một đối tượng `Thread` để chạy `Runnable` được truyền vào và áp dụng chính sách thread. | Lập lịch tác vụ, giới hạn queue, tự khởi chạy thread, thu thập kết quả. |
| `Thread` | Đơn vị thực thi theo Thread API; `start()` khiến JVM bắt đầu thực thi `run()`. | Quản lý một hàng đợi nhiều tác vụ theo chính sách pool. |
| `Executor` / `ExecutorService` | Nhận tác vụ, lập lịch/phân phối chúng; `ExecutorService` thêm `submit`, shutdown và quản lý `Future`. | Bắt buộc tạo một thread mới cho mọi task; hành vi tùy implementation. |
| `ThreadPoolExecutor` | Quy định số worker, queue, timeout, rejection và vòng đời worker của một thread pool. | Dùng `ThreadFactory` để thay thế mọi cấu hình của pool. |

Luồng trách nhiệm thường là:

```text
Ứng dụng submit Runnable/Callable
             |
             v
     ExecutorService
         |  cần worker mới
         v
      ThreadFactory.newThread(Runnable worker)
         |
         v
       Thread (NEW, đã cấu hình)
         |
         v
     Executor gọi start()
         |
         v
   Worker xử lý task/queue
```

Factory **tạo** thread; executor thường **quyết định khi nào cần tạo, khi nào chạy và tái sử dụng**. Nếu gọi factory trực tiếp, chính người gọi phải `start()`, `join()`/chờ có kiểm soát và quản lý vòng đời.

## 2. Hợp đồng của ThreadFactory

Khai báo trong Java:

```java
@FunctionalInterface
public interface ThreadFactory {
    Thread newThread(Runnable r);
}
```

Những điểm cần hiểu chính xác:

1. `newThread(r)` nhận **chính Runnable do bên gọi cung cấp**. Factory phải gắn runnable này vào thread; bỏ qua runnable khiến executor không thể chạy worker theo thiết kế.
2. Thread được trả về thông thường ở trạng thái **`NEW`** (chưa gọi `start()`). Factory không tự gọi `start()`; nếu tự khởi chạy, executor có thể gặp `IllegalThreadStateException` khi cố start lại hoặc gặp race khó đoán.
3. Mỗi lần cần worker mới, factory phải trả về một thread **mới, dùng được**, không tái trả cùng một thread đã từng chạy. `Thread` chỉ được `start()` một lần.
4. Interface về mặt hợp đồng có thể trả `null` khi không thể tạo thread. Tuy nhiên, làm vậy có thể khiến executor không khởi tạo được worker hoặc không tiến triển theo kỳ vọng; trong ứng dụng thông thường nên tạo thread hợp lệ hoặc để lỗi tạo thread lộ rõ và được xử lý ở tầng phù hợp.
5. Không có nghĩa mọi lời gọi `submit()` dẫn tới một lần gọi `newThread()`: thread pool **tái sử dụng** worker.
6. Factory có thể được gọi đồng thời; trạng thái dùng để sinh số thứ tự/tên nên thread-safe. `AtomicInteger` là lựa chọn đơn giản. Không cần khóa quanh toàn bộ phần tạo thread chỉ để tăng bộ đếm.
7. Factory **không bảo đảm** `Runnable` hoàn thành, không bảo đảm thứ tự chạy, fairness, timeout hay số lượng thread hoạt động.

Tên thread là metadata giúp vận hành, không phải khóa duy nhất để nhận diện tác vụ. Một tên có thể trùng giữa hai factory instance; thread ID cũng không nên được coi là business ID lâu dài.

## 3. Ví dụ Java 17+: một factory tùy chỉnh chạy được

Tạo tệp `NamedThreadFactoryDemo.java`:

```java
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class NamedThreadFactoryDemo {

    public static void main(String[] args) throws InterruptedException {
        ThreadFactory factory = new NamedThreadFactory("demo-worker");

        Thread first = factory.newThread(() ->
                System.out.println("Task 1: "
                        + Thread.currentThread().getName()));
        first.start();
        first.join();

        Thread second = factory.newThread(() -> {
            System.out.println("Task 2: "
                    + Thread.currentThread().getName());
            throw new IllegalStateException("demo failure");
        });
        second.start();
        second.join();

        System.out.println("Main completed");
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger sequence = new AtomicInteger();

        private NamedThreadFactory(String prefix) {
            this.prefix = Objects.requireNonNull(prefix, "prefix");
        }

        @Override
        public Thread newThread(Runnable task) {
            Objects.requireNonNull(task, "task");

            String name = prefix + "-" + sequence.incrementAndGet();
            Thread thread = new Thread(task, name);

            // Cấu hình trước khi executor hoặc caller start().
            thread.setDaemon(false);
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.setUncaughtExceptionHandler((failedThread, error) ->
                    System.out.println("Uncaught in "
                            + failedThread.getName() + ": "
                            + error.getClass().getSimpleName()));

            return thread;
        }
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac NamedThreadFactoryDemo.java
java NamedThreadFactoryDemo
```

Kết quả chuẩn (hai lần `join()` khiến thứ tự của ví dụ xác định):

```text
Task 1: demo-worker-1
Task 2: demo-worker-2
Uncaught in demo-worker-2: IllegalStateException
Main completed
```

**Giải thích:**

- `AtomicInteger.incrementAndGet()` cấp số thứ tự không bị mất cập nhật khi nhiều nơi yêu cầu tạo thread đồng thời. Trong phạm vi *cùng một factory instance*, tên được đánh số tăng.
- `new Thread(task, name)` liên kết `Runnable` với thread mới và đặt tên ngay từ khi khởi tạo. Không có task nào chạy bên trong `newThread()`.
- `setDaemon(false)` và `setPriority(Thread.NORM_PRIORITY)` tạo chính sách rõ ràng cho **platform thread**. Phải đặt daemon trước `start()`. Mức ưu tiên chỉ là gợi ý đối với bộ lập lịch, không phải SLA.
- `setUncaughtExceptionHandler` nhận lỗi thoát khỏi `run()` mà không được bắt. Ví dụ chủ động ném `IllegalStateException` để kiểm tra handler; trên chương trình thật nên ghi log/metric với ngữ cảnh phù hợp.
- `join()` chờ thread kết thúc trước khi chuyển bước; ví dụ này dùng chạy tuần tự để stdout xác định, còn trên ứng dụng thật hai thread thường có thể chạy đồng thời.

**Không nên** sửa class mẫu bằng cách thêm `thread.start()` trong `newThread()`: factory phải trả thread chưa start. Nếu chỉ cần chạy một vài thao tác độc lập, factory có thể được dùng trực tiếp như trên, nhưng với luồng tác vụ dài hạn, executor giúp quản lý lifecycle và tài nguyên rõ hơn.

## 4. Dùng ThreadFactory với ExecutorService

Ứng dụng phổ biến nhất là truyền factory vào constructor của executor. Nhiều API `Executors` nhận factory, ví dụ:

```java
Executors.newSingleThreadExecutor(threadFactory);
Executors.newFixedThreadPool(poolSize, threadFactory);
Executors.newCachedThreadPool(threadFactory);
Executors.newScheduledThreadPool(corePoolSize, threadFactory);
```

Chúng khác nhau về chính sách tạo/reuse thread, queue và khả năng tăng số worker. Factory chỉ ảnh hưởng **thread mới mà executor yêu cầu tạo**.

### 4.1 Chương trình chạy độc lập bằng Java 17+

Tạo tệp `ExecutorThreadFactoryDemo.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ExecutorThreadFactoryDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger nextId = new AtomicInteger();

        ThreadFactory factory = task ->
                new Thread(task, "report-worker-" + nextId.incrementAndGet());

        ExecutorService executor = Executors.newSingleThreadExecutor(factory);
        try {
            Future<String> result =
                    executor.submit(() -> Thread.currentThread().getName());
            System.out.println(result.get());
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
```

Chạy:

```bash
javac ExecutorThreadFactoryDemo.java
java ExecutorThreadFactoryDemo
```

Stdout:

```text
report-worker-1
```

Ở đây `executor.submit(...)` nhận **tác vụ**, còn factory nhận `Runnable` nội bộ mà executor tạo ra để phục vụ worker. Một worker có thể thực hiện nhiều tác vụ nối tiếp. Vì vậy không nên lấy giá trị bộ đếm trong factory làm “task ID”: đó là **số lần tạo thread**, không phải số lần submit.

`Future.get()` đợi kết quả tác vụ; trong trường hợp này task trả về tên thread. Nếu tác vụ ném exception, `Future.get()` sẽ ném `ExecutionException` chứa nguyên nhân; nó không chuyển exception đó thành kết quả string. `shutdown()` ngừng nhận task mới và cho các task đã nhận cơ hội hoàn thành. `awaitTermination` có timeout chỉ giới hạn **thời gian đợi**, không tự chứng minh mọi task đã dừng. `shutdownNow()` cố gắng dừng bằng interruption và trả lại tác vụ chưa chạy; code tác vụ phải hợp tác với interruption nếu muốn dừng kịp thời.

### 4.2 ThreadFactory không thay thế cấu hình pool

Xét `newFixedThreadPool(4, factory)`: đây là policy tối đa bốn worker chạy tác vụ đồng thời của pool cố định. Khi bốn worker đã tồn tại và còn hoạt động, các submit mới thường được đưa vào queue, **không** khiến factory tạo thread thứ năm. Ngược lại, `newCachedThreadPool(factory)` có thể tăng số worker lớn khi có nhiều tác vụ ngắn và chưa có worker rảnh, không thích hợp nếu số thread cần được chặn cứng mà chưa thiết kế kiểm soát tải.

Đối với workload cần **queue hữu hạn** và chính sách khi quá tải, có thể dùng `ThreadPoolExecutor` cấu hình tường minh:

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
        2,                                  // corePoolSize
        4,                                  // maximumPoolSize
        30, TimeUnit.SECONDS,               // thời gian giữ worker dư
        new ArrayBlockingQueue<>(100),      // queue hữu hạn
        factory,                            // thread policy
        new ThreadPoolExecutor.AbortPolicy() // reject khi quá tải
);
```

Đây là **mảnh mã minh họa**, giả định đã import `ThreadPoolExecutor`, `TimeUnit`, `ArrayBlockingQueue` và có biến `factory` hợp lệ. Cần shutdown pool ở vòng đời ứng dụng. Khi core worker đã bận, `ThreadPoolExecutor` thường **ưu tiên đưa task vào queue** trước khi tăng số worker vượt core; chỉ khi queue không nhận thêm thì mới xét tăng worker tới maximum. `AbortPolicy` báo `RejectedExecutionException` khi không thể nhận task. Những hành vi đó thuộc executor, không thuộc factory.

## 5. Thiết kế chính sách factory nhất quán

Một factory tốt cung cấp cấu hình có ý nghĩa vận hành và giữ trách nhiệm nhỏ. Các chính sách thường gặp:

| Chính sách | Cách thực hiện | Lưu ý |
| --- | --- | --- |
| **Tên có ý nghĩa** | Prefix theo mục đích cộng số thứ tự: `event-dispatch-1`, `file-reader-2` | Tránh tên chứa dữ liệu người dùng/bí mật; prefix ngắn, ổn định. |
| **Daemon/platform thread** | `thread.setDaemon(boolean)` trước start | Daemon không giữ JVM sống; có thể bị dừng khi JVM kết thúc. |
| **Priority** | `thread.setPriority(Thread.NORM_PRIORITY)` hoặc giá trị 1–10 cho platform thread | Không bảo đảm CPU share, fairness, deadline hoặc tốc độ. |
| **Uncaught exception handler** | `setUncaughtExceptionHandler` | Chỉ nhận throwable thoát khỏi thread; không tự bao phủ lỗi được `Future` giữ lại. |
| **ThreadGroup** | Constructor `Thread(ThreadGroup, Runnable, String)` khi thật sự cần | Group không phải cơ chế cách ly tài nguyên hay quản lý pool hiện đại. |
| **ContextClassLoader** | `thread.setContextClassLoader(loader)` nếu có yêu cầu rõ | Có thể ảnh hưởng class loading và giữ classloader sống; phải quản lý vòng đời. |
| **Cấu hình dùng lại** | Inject `ThreadFactory` qua constructor của thành phần sử dụng | Dễ kiểm thử và tránh nhiều nơi viết lại quy tắc tên/handler. |

Nên ưu tiên chính sách tối thiểu, có căn cứ. Không nên tự ý tăng priority toàn bộ worker vì có thể làm thread khác ít được CPU. Không nên đặt daemon `true` cho mọi worker chỉ để chương trình thoát mà không xử lý shutdown; công việc đang chạy có thể bị cắt khi chỉ còn daemon thread.

Nếu một factory gắn `UncaughtExceptionHandler`, xử lý lỗi trong handler cũng cần an toàn. Handler không nên thực hiện tác vụ blocking không giới hạn, gọi lại tài nguyên lỗi đang giữ lock hoặc ném exception mới: handler ném exception sẽ được JVM bỏ qua ở tầng dispatch lỗi, khiến thông tin hỏng khó quan sát.

## 6. defaultThreadFactory và lựa chọn có sẵn

Java cung cấp:

```java
ThreadFactory factory = Executors.defaultThreadFactory();
```

Factory mặc định của `Executors` tạo **platform thread** (JDK 17), đặt tên dạng `pool-N-thread-M`, không daemon, ưu tiên `NORM_PRIORITY` (nếu phù hợp), và sử dụng `ThreadGroup` thích hợp theo thực thi của JDK. Thông thường đây là điểm khởi đầu đủ tốt nếu chưa cần quy tắc riêng.

**So sánh lựa chọn:**

- `new Thread(runnable)`: tạo trực tiếp; lập trình viên phải tự cấu hình và start.
- `Executors.defaultThreadFactory()`: có thể dùng lại khi cấu hình executor và muốn chính sách mặc định nhất quán.
- `Executors.privilegedThreadFactory()`: API cũ gắn với security context lịch sử; không phải hướng ưu tiên cho code Java mới và có thể bị deprecate/remove trong các phiên bản JDK. Đừng dùng như công cụ bảo mật chung.
- **Custom `ThreadFactory`**: cần tên rõ ràng, handler riêng, policy daemon hoặc khả năng inject/test. Chỉ tạo thread và cấu hình nó, đừng nhúng xử lý nghiệp vụ.
- **Java 21+ `Thread.Builder`**: tạo factory cho platform/virtual thread theo cú pháp mới, giải thích ở phần 9.

Factory thường nên là **một instance dùng chung** trong phạm vi executor, để bộ đếm tên không reset mỗi lần submit. Nếu tạo `new NamedThreadFactory("worker")` trong từng lời gọi, nhiều thread có thể đều mang tên `worker-1`, làm thread dump gây nhầm lẫn.

## 7. Exception: khác nhau giữa thread trực tiếp, execute và submit

Cấu hình `UncaughtExceptionHandler` thường bị hiểu nhầm là cơ chế tập trung bắt **mọi** exception của task.

### 7.1 Thread trực tiếp

Khi `new Thread(runnable).start()` chạy và `runnable.run()` ném exception không được bắt, exception thoát khỏi thread và handler tương ứng được gọi. Bài demo đầu đã minh họa chính xác trường hợp này.

### 7.2 Executor.execute(Runnable)

Với các executor dùng platform thread, một unchecked exception thoát khỏi `run()` của tác vụ do `execute` cung cấp có thể thoát tới worker và dẫn đến gọi uncaught handler; `ThreadPoolExecutor` có thể phải thay worker đã chết. Tuy vậy không nên dùng việc một task phá hủy worker làm chính sách lỗi bình thường. Hãy xử lý/retry task tại lớp nghiệp vụ với giới hạn phù hợp.

### 7.3 ExecutorService.submit(...)

`submit()` thường bọc tác vụ trong `FutureTask`. Exception từ task được **ghi nhận trong Future**, vì thế **không thoát khỏi `run()` của worker** để handler của thread bắt. Ví dụ:

```java
Future<?> job = executor.submit(() -> {
    throw new IllegalArgumentException("invalid input");
});

try {
    job.get();
} catch (ExecutionException e) {
    Throwable original = e.getCause();
    // Ghi nhận hoặc xử lý lỗi tác vụ, theo chính sách ứng dụng.
}
```

Mảnh này giả định có `ExecutorService executor` và import `Future`, `ExecutionException`. `get()` còn có thể ném `InterruptedException`; khi task bị hủy, nó có thể ném `CancellationException`. Trong ứng dụng, cần xử lý cả vòng đời Future và hủy tác vụ theo hợp đồng API.

**Nguyên tắc:** dùng `UncaughtExceptionHandler` cho lỗi không được bắt ở mức thread, còn lỗi của tác vụ asynchronous phải được xử lý ở mức `Future`/kết quả, chính sách retry và giám sát tác vụ. Một factory không biến `submit` thành fire-and-forget an toàn.

## 8. ThreadLocal, context và việc tái sử dụng worker

Tác vụ chạy trên worker của pool, **không nhất thiết** là thread đã gọi `submit()`. `ThreadLocal` đặt ở thread submit không tự trở thành `ThreadLocal` trong worker khác. Với `InheritableThreadLocal`, giá trị có thể được kế thừa **khi thread được tạo**, nhưng pool tạo worker trước hoặc tái sử dụng worker, nên kết quả không phải “context của từng task” và có nguy cơ lẫn dữ liệu giữa tác vụ.

Ví dụ mảnh mã dễ gây lỗi:

```java
static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

void process(String requestId) {
    REQUEST_ID.set(requestId);
    // ... business logic ...
    REQUEST_ID.remove();
}
```

Mảnh trên là phương pháp **thiết lập và dọn dẹp trong cùng thread**, nhưng bản thân nó chưa xử lý trường hợp `business logic` ném exception. Trong code thật, đặt cleanup trong `finally`:

```java
String previous = REQUEST_ID.get();
try {
    REQUEST_ID.set(requestId);
    // Chạy tác vụ ở đúng worker cần context.
} finally {
    if (previous == null) {
        REQUEST_ID.remove();
    } else {
        REQUEST_ID.set(previous);
    }
}
```

Đây là mảnh mã minh họa, giả định `REQUEST_ID` và `requestId` đã có. Nếu cần chuyển context giữa thread, capture **giá trị context phù hợp** tại lúc submit rồi gắn, sau đó cleanup ở **thời điểm thực thi trên worker**. Tránh đọc `ThreadLocal` của thread submit ngay trong factory rồi áp dụng mặc định cho mọi task của một worker: worker sống lâu và chạy nhiều request, context của lần tạo thread sẽ lỗi thời.

Không truyền dữ liệu bí mật, classloader hoặc context không cần thiết cho thread lâu sống. Factory chỉ nên quản lý thuộc tính thread; việc truyền context **theo từng task** cần một wrapper/decorator ở lớp thực thi task.

## 9. Platform thread và virtual thread từ Java 21

JDK 17 dùng `ThreadFactory` để tạo platform thread thông thường; với Java 21, `Thread.Builder` thêm cách khai báo factory cho cả platform và virtual thread.

Ví dụ Java 21+ tạo virtual thread factory:

```java
ThreadFactory factory = Thread.ofVirtual()
        .name("v-worker-", 0)
        .factory();
```

`Thread.ofVirtual()` tạo builder cho **virtual thread**; `name(prefix, start)` đặt tiền tố và số thứ tự; `factory()` tạo factory áp dụng cấu hình đó cho mỗi virtual thread mới.

### Ví dụ Java 21+ chạy độc lập

Tạo `VirtualFactoryDemo.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.Future;

public final class VirtualFactoryDemo {
    public static void main(String[] args) throws Exception {
        ThreadFactory factory = Thread.ofVirtual()
                .name("v-worker-", 0)
                .factory();

        try (ExecutorService executor =
                Executors.newThreadPerTaskExecutor(factory)) {
            Future<String> future = executor.submit(() ->
                    Thread.currentThread().getName()
                    + ", virtual=" + Thread.currentThread().isVirtual());
            System.out.println(future.get());
        }
    }
}
```

Chạy bằng **JDK 21 trở lên**:

```bash
javac VirtualFactoryDemo.java
java VirtualFactoryDemo
```

Stdout:

```text
v-worker-0, virtual=true
```

`Executors.newThreadPerTaskExecutor(factory)` tạo **một thread mới cho mỗi task**, thay vì tái sử dụng worker của fixed pool. Với virtual thread, mô hình một thread cho mỗi tác vụ blocking thường khả thi hơn rất nhiều so với một platform thread cho mỗi task; tuy nhiên vẫn cần giới hạn truy cập DB, network, connection pool và tài nguyên downstream bằng cơ chế riêng. Factory **không** tự áp đặt backpressure.

Virtual thread trong Java 21 là daemon thread; không thể đặt priority/daemon theo cách platform thread tùy biến được. Đừng sao chép nguyên xi factory gọi `thread.setDaemon(false)` hoặc `setPriority` sang virtual thread. Nếu cần đặt cấu hình cho virtual thread, dùng builder hỗ trợ, ví dụ `Thread.ofVirtual().name(...).uncaughtExceptionHandler(...).factory()`.

Virtual thread vẫn dùng được `ThreadFactory`, `Runnable` và `ExecutorService`, nhưng mục tiêu dùng virtual thread không phải tạo một “virtual fixed pool” nhỏ để hạn chế số thread. Điều tiết tải bằng semaphore, queue hay giới hạn tại tài nguyên thực sự khan hiếm thường rõ ràng hơn. Nếu cần hỗ trợ Java 17, **không** đưa `Thread.ofVirtual()` vào code biên dịch với JDK 17: API này chưa có tại đó.

Với platform thread trên JDK 21, cũng có thể viết `Thread.ofPlatform().name("platform-", 0).factory()` thay cho custom factory đơn giản. Cần phân biệt rõ runtime/JDK mục tiêu trước khi chọn API.

## 10. Giới hạn hiệu năng, safety và liveness

Không nên nhầm `ThreadFactory` với một giải pháp concurrency hoàn chỉnh:

- **Safety:** factory không làm dữ liệu chia sẻ trở thành thread-safe; vẫn cần `synchronized`, lock, atomic operation hoặc immutable design phù hợp.
- **Liveness:** factory không ngăn deadlock, livelock, starvation hay chờ vô hạn trong task; cần thiết kế thứ tự khóa, timeout, cancellation và quản lý phụ thuộc giữa tác vụ.
- **Số worker:** factory chỉ tạo thread khi được hỏi; giới hạn tài nguyên nằm ở executor/pool. Một factory tự tạo worker bổ sung trái yêu cầu sẽ phá vỡ policy của executor.
- **Thread reuse:** task có thể chạy trên cùng worker nhiều lần; ThreadLocal và tên thread không phải ID riêng cho task.
- **Throughput/latency:** đặt tên và handler giúp quan sát; không bảo đảm pool nhanh hơn. Cấu hình queue không giới hạn có thể tích tụ backlog, trong khi unbounded thread creation có thể gây áp lực CPU/memory.
- **Thời gian shutdown:** `daemon` không phải chiến lược graceful shutdown. Cần xử lý interruption và deadline hợp lý trong task.
- **Quyền ưu tiên:** `setPriority` không phải cơ chế fairness và hành vi có thể khác giữa nền tảng JVM/hệ điều hành.

Một factory được gọi từ nhiều thread nên nhanh, không blocking lâu và tránh side effect ngoài việc khởi tạo/configure thread. Không gọi callback nghiệp vụ hay I/O trong `newThread()`. Nếu có giới hạn tài nguyên đặc thù, giao cho executor/admission control quyết định có tạo thêm worker hay từ chối task.

## 11. Kiểm thử ThreadFactory

Nên kiểm tra các thuộc tính **có thể quan sát được**, đồng thời không dựa vào thứ tự schedule khó đoán:

1. `newThread(runnable)` trả thread khác `null`, ở trạng thái `NEW` và chưa chạy `Runnable` trước khi gọi `start()`.
2. Thread thực sự chạy đúng `Runnable` đã truyền và hoàn thành khi `join()`.
3. Tên thread có prefix hợp lệ; trong cùng factory, các tên không trùng nhau dưới tranh chấp.
4. Với **platform thread**, daemon/priority/handler đúng chính sách; không dùng test này cho virtual thread khi API không hỗ trợ cùng thuộc tính.
5. Handler nhận exception không được bắt khi thread chạy `run()` sau `start()`; lỗi thoát khỏi lời gọi `run()` trực tiếp lan tới caller như lời gọi phương thức thường, còn lỗi task `submit()` nằm trong `Future`.
6. Khi tích hợp executor, kiểm tra worker được factory tạo thật sự được sử dụng, task hoàn tất, shutdown và tình huống quá tải theo cấu hình pool.

Một harness ngắn xác nhận `newThread()` chưa start:

```java
ThreadFactory factory = r -> new Thread(r, "test-worker");
Thread thread = factory.newThread(() -> { /* không cần in */ });

if (thread.getState() != Thread.State.NEW) {
    throw new AssertionError("Factory started the thread");
}
thread.start();
thread.join();
```

Đây là **mảnh mã minh họa** đặt trong method có thể `throws InterruptedException`, với import `ThreadFactory`. Với thread đã chạy, trạng thái có thể thay đổi rất nhanh; chỉ kiểm tra trạng thái `NEW` **trước khi start** là ổn định. Test concurrent uniqueness nên dùng tập hợp thread-safe, barrier để tăng tranh chấp, rồi kiểm tra tất cả tên sau khi tạo xong; không suy ra correctness chỉ từ một lần chạy.

Trong môi trường thực tế, tên thread có prefix nhất quán giúp tìm thread dump, nhưng việc điều tra còn cần task ID/correlation ID riêng, queue depth, saturation và thời gian chờ. Đừng dùng tên worker để suy ngược nguyên nhân lỗi của một request cụ thể khi worker chạy nhiều request.

## 12. Những lỗi thường gặp

| Lỗi | Hệ quả | Cách đúng |
| --- | --- | --- |
| Gọi `thread.start()` trong factory | Executor có thể start lại và ném `IllegalThreadStateException`; race ngoài kiểm soát. | Chỉ tạo/configure và trả thread `NEW`. |
| Bỏ qua `Runnable` đầu vào | Executor tạo worker nhưng không chạy được nhiệm vụ worker đã định. | Truyền chính xác runnable vào constructor/builder thread. |
| Mỗi lần `newThread()` lại tạo factory/bộ đếm từ 0 | Tên trùng nhau và chính sách phân tán. | Dùng factory instance sống cùng phạm vi executor. |
| Dùng `int counter++` trong factory bị gọi đồng thời | Mất cập nhật, tên trùng. | `AtomicInteger` hoặc đồng bộ phù hợp. |
| Tự đặt `daemon=true` cho mọi pool | JVM có thể kết thúc khi công việc chưa hoàn tất. | Chọn policy daemon dựa trên yêu cầu, shutdown đúng cách. |
| Tin uncaught handler sẽ bắt lỗi của `submit()` | Lỗi nằm trong `Future` nên dễ bị bỏ sót. | Thu thập `Future` và xử lý `get()`/completion. |
| Dùng priority thay cho kiểm soát tải | Scheduling khó dự đoán và không có SLA. | Cấu hình queue, pool, timeout, giới hạn downstream. |
| Dùng `ThreadLocal` tạo bởi caller để “tự truyền context” | Worker tái sử dụng có context thiếu/sai hoặc lưu dữ liệu cũ. | Bọc từng task, gắn context trong worker và dọn trong `finally`. |
| Gán “mỗi submit = một lần tạo thread” | Đếm task sai, giả định sai về worker reuse. | Phân biệt task, worker thread, executor policy. |
| Gọi `Thread.ofVirtual()` trong Java 17 | Không biên dịch vì API Java 21 chưa tồn tại. | Dùng Java 17 factory hoặc nâng minimum JDK lên 21. |

## 13. Quy trình áp dụng trong dự án Java

1. **Xác định workload và executor.** Tác vụ CPU-bound, I/O-blocking, lịch định kỳ, số lượng request và tốc độ đến khác nhau; chọn executor/pool trước theo yêu cầu tải.
2. **Định nghĩa quy tắc thread.** Prefix tên cho từng vai trò, daemon hay không, handler và chính sách logging; chỉ đặt thuộc tính phục vụ mục tiêu cụ thể.
3. **Tạo một `ThreadFactory` thread-safe.** Mỗi `newThread(r)` trả thread mới ở trạng thái `NEW`, gắn đúng `r`, cấu hình xong rồi trả về.
4. **Truyền factory vào executor.** Tách setup executor khỏi code `submit`; giới hạn core/max, queue, rejection và timeout tại lớp executor.
5. **Quan sát task và lỗi.** `Future`/result ghi nhận exception nghiệp vụ; handler xử lý uncaught exception; theo dõi pool saturation và thread dump.
6. **Định nghĩa shutdown.** Ngừng nhận task, đợi có giới hạn, yêu cầu hủy nếu cần, dọn tài nguyên; phối hợp interruption trong runnable.
7. **Kiểm tra độ đúng và tải.** Test contract factory, thread naming, lỗi task, concurrent creation, lifecycle; stress test theo workload thực.

### Tự kiểm tra

**Câu 1.** `ThreadFactory.newThread(r)` nên trả về thread đã gọi `start()` không?  
**Trả lời:** Không. Caller/executor chịu trách nhiệm start; thread trả về cần mới và chưa chạy.

**Câu 2.** Nếu gửi 100 task vào fixed thread pool có 4 worker, factory có nhất thiết được gọi 100 lần không?  
**Trả lời:** Không. Factory chỉ được gọi khi executor cần worker mới; worker có thể chạy nhiều task.

**Câu 3.** Vì sao `AtomicInteger` phù hợp cho bộ đếm tên trong factory?  
**Trả lời:** Khi factory được gọi đồng thời, mỗi lần tăng là atomic, tránh các thread nhận cùng số do mất cập nhật.

**Câu 4.** Vì sao handler trên worker không thấy exception của `executor.submit(() -> { throw ...; })`?  
**Trả lời:** `submit` bọc task và giữ lỗi trong `Future`; phải quan sát qua `Future.get()` hoặc cơ chế completion phù hợp.

**Câu 5.** Muốn chỉ có tối đa 8 task cùng truy cập database thì nên đặt giới hạn ở ThreadFactory không?  
**Trả lời:** Không. Dùng admission control, semaphore, giới hạn pool/connection pool hoặc chính sách queue phù hợp; factory chỉ tạo thread.

**Câu 6.** Khác biệt chính giữa `Executors.newFixedThreadPool(4, factory)` và `Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory())` là gì?  
**Trả lời:** Cái đầu tái sử dụng số lượng worker giới hạn; cái sau tạo một thread (thường virtual) mới cho mỗi task và là API Java 21+. Vẫn phải quản lý tài nguyên downstream.

**Ghi nhớ:** `ThreadFactory` là nơi chuẩn hóa **cách tạo và cấu hình thread**, giúp mã nhất quán và quan sát dễ hơn. Executor quyết định **khi nào tạo, dùng lại, xếp hàng và dừng worker**; ứng dụng quyết định **logic task, dữ liệu an toàn, hủy bỏ và xử lý lỗi**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-api-009">Runnable, Callable và cách truyền kết quả tác vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Runnable, Callable và cách truyền kết quả tác vụ trong Java

Khi viết chương trình Java đồng thời (concurrency), ta cần tách hai câu hỏi: **tác vụ sẽ thực hiện việc gì?** và **người gọi lấy kết quả, biết lỗi hoặc chờ tác vụ hoàn tất bằng cách nào?** Hai functional interface `Runnable` và `Callable<V>` biểu diễn công việc; những công cụ như `Thread.join()`, `Future<V>`, `FutureTask<V>` và `ExecutorService` giúp quản lý quá trình thực thi và nhận kết quả. Bài này nói về **Java nói chung**, tập trung vào Thread API và Java concurrency tiêu chuẩn (Java 17+); không bàn về cơ chế riêng của Spring.

Sau bài này, bạn có thể:
- Phân biệt `Runnable.run()`, `Callable.call()`, giá trị trả về và checked exception.
- Chạy một tác vụ qua `Thread` hoặc gửi cho `ExecutorService`; biết lúc nào dùng `execute`, `submit`.
- Lấy kết quả từ luồng khác bằng `join` + đối tượng chia sẻ, `FutureTask` hoặc `Future`.
- Xử lý thành công, lỗi, chờ, timeout, hủy tác vụ và interrupt mà không làm mất lỗi.
- Nhận ra những sai lầm phổ biến: gọi `run()` thay vì `start()`, gọi `get()` quá sớm, coi `shutdown()` là đã đợi xong, hoặc bỏ qua `Future` khi tác vụ lỗi.

## 1. Runnable và Callable biểu diễn công việc như thế nào?

### 1.1. Runnable: thực thi hành động, không có giá trị trả về

`java.lang.Runnable` khai báo một phương thức trừu tượng:

~~~java
@FunctionalInterface
public interface Runnable {
    void run();
}
~~~

Một `Runnable` mô tả công việc có thể chạy; phương thức `run()` **trả về `void`** và **không khai báo checked exception**. Có thể viết bằng class, anonymous class, method reference hoặc lambda:

~~~java
Runnable logTask = () -> System.out.println("Running task");
~~~

`Runnable` không mang một kênh kết quả trả về trực tiếp qua `run()`, nhưng tác vụ vẫn có thể làm thay đổi một đối tượng được chia sẻ, phát thông điệp hoặc ghi dữ liệu. Việc chia sẻ đó cần thiết kế an toàn luồng khi có truy cập đồng thời.

### 1.2. Callable<V>: thực thi và trả về V

`java.util.concurrent.Callable<V>` khai báo:

~~~java
@FunctionalInterface
public interface Callable<V> {
    V call() throws Exception;
}
~~~

Khác biệt chính:
- `call()` trả về **một giá trị có kiểu `V`**; chẳng hạn `Callable<Integer>` trả về `Integer`.
- `call()` được phép khai báo `throws Exception`, nên tác vụ có thể ném checked exception.
- `Callable` không được truyền trực tiếp vào constructor `Thread(Runnable)`; muốn chạy với `Thread` có thể bọc nó trong `FutureTask`, hoặc thường gặp hơn là đưa cho `ExecutorService.submit`.

~~~java
Callable<Integer> calculate = () -> 6 * 7;
Callable<String> loadText = () -> "ready";
Callable<Void> withoutValue = () -> {
    System.out.println("Work completed");
    return null;
};
~~~

**Lưu ý:** `Callable<Void>` vẫn phải trả về một giá trị tương thích kiểu `Void` (trên thực tế thường là `null`). Nếu bạn chỉ cần diễn tả một hành động không có giá trị trả về, `Runnable` thường gọn hơn.

| Tiêu chí | `Runnable` | `Callable<V>` |
| --- | --- | --- |
| Phương thức trừu tượng | `void run()` | `V call() throws Exception` |
| Giá trị trả về trực tiếp | Không | Có, kiểu `V` |
| Checked exception khai báo trong phương thức | Không | Có |
| Có thể đưa trực tiếp vào `new Thread(...)` | Có | Không |
| Có thể đưa vào `ExecutorService.submit(...)` | Có | Có |
| Khi nào chọn | Hành động, tác vụ không cần trả giá trị | Tính toán, truy xuất hoặc xử lý cần trả giá trị / checked exception |

Cả hai là **functional interface** và đều biểu diễn *việc cần làm*; tự tạo `Runnable` hay `Callable` không khiến tác vụ chạy bất đồng bộ. Phải có cơ chế thực thi như `Thread.start()` hoặc executor.

## 2. Chạy Runnable bằng Thread và chờ bằng join()

### 2.1. Ví dụ Java 17+ có thể chạy độc lập

~~~java
// RunnableThreadDemo.java
import java.util.concurrent.atomic.AtomicInteger;

public class RunnableThreadDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger result = new AtomicInteger();

        Runnable task = () -> {
            int value = 6 * 7;
            result.set(value);
        };

        Thread worker = new Thread(task, "calculation-worker");
        worker.start(); // Tạo đường thực thi mới rồi gọi task.run() trong luồng đó.
        worker.join();  // Đợi worker kết thúc.

        System.out.println("Result: " + result.get());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac RunnableThreadDemo.java
java RunnableThreadDemo
~~~

Stdout:

~~~text
Result: 42
~~~

Ở đây `Runnable.run()` không trả về `42`. Worker **ghi** vào `AtomicInteger`, còn luồng `main` **đọc** sau khi `join()`. `join()` chỉ chờ kết thúc; giá trị xuất hiện vì chúng ta chủ động lưu trong đối tượng dùng chung. Mọi hành động của worker trước khi kết thúc được bảo đảm *happens-before* luồng khác quay về thành công từ `join()`, nên dữ liệu được công bố đúng cách qua việc chờ này. Dùng `AtomicInteger` ở ví dụ nhằm làm rõ trạng thái được chia sẻ; nếu chỉ một worker ghi và main chỉ đọc sau `join()` thì một holder không biến đổi đồng thời khác cũng có thể phù hợp.

### 2.2. Không nhầm run() với start()

~~~java
worker.run();   // Gọi phương thức như một lời gọi thông thường trên luồng hiện tại.
worker.start(); // Yêu cầu JVM bắt đầu luồng mới; luồng đó sẽ chạy run().
~~~

Hai dòng trên là **minh họa hai lựa chọn**, không phải chuỗi lệnh cần chạy nối tiếp trên cùng `worker`. Một `Thread` chỉ được `start()` **một lần**; gọi lại sẽ ném `IllegalThreadStateException`. `Thread.join()` chờ luồng kết thúc, nhưng **không trả về kết quả tác vụ**. Khi có nhiều nhiệm vụ và cần kết quả/ngoại lệ, `Future` thường là mô hình tiện hơn tự quản lý nhiều `Thread` cùng các holder.

## 3. Future<V>: đại diện cho kết quả sẽ có trong tương lai

`java.util.concurrent.Future<V>` là một *handle* theo dõi tác vụ đã được đăng ký/chạy và nhận kết quả khi hoàn tất. `Future` không tự tạo luồng; một executor hoặc `FutureTask` phải thực thi công việc phía sau.

Các phương thức quan trọng:

| Phương thức | Ý nghĩa |
| --- | --- |
| `V get()` | Đợi đến khi hoàn tất rồi trả kết quả, hoặc ném ngoại lệ nếu thất bại/bị hủy/người đợi bị interrupt |
| `V get(long timeout, TimeUnit unit)` | Đợi tối đa thời gian chỉ định, có thể ném `TimeoutException` |
| `boolean isDone()` | `true` nếu tác vụ đã kết thúc theo một cách nào đó: thành công, lỗi hoặc bị hủy |
| `boolean isCancelled()` | `true` nếu tác vụ bị hủy |
| `boolean cancel(boolean mayInterruptIfRunning)` | Thử hủy tác vụ; kết quả phụ thuộc trạng thái và cơ chế thực thi |

`get()` có thể **chặn luồng gọi**. Nếu đã hoàn tất thành công, nó trả về giá trị; nếu tác vụ lỗi, nó ném `ExecutionException`, trong đó `getCause()` giữ nguyên nhân. Nếu bị hủy, `get()` ném `CancellationException` (unchecked). Nếu người chờ bị interrupt, `get()` ném `InterruptedException`.

`isDone() == true` **không đồng nghĩa với thành công**; sau khi trả về `true`, vẫn phải xử lý các trường hợp lỗi/hủy nếu cần kết quả.

Mô hình thời gian:

~~~text
Luồng gọi                     Luồng tác vụ
   | submit(callable)  -------> | bắt đầu làm việc
   | nhận Future<V>            | tính toán...
   | làm việc khác             | ...
   | future.get()              | ...
   | [có thể chờ]              | return value
   | <------------------------ | hoàn tất
   | nhận V                    |
~~~

Việc chờ `Future.get()` trả về thành công đồng thời tạo bảo đảm bộ nhớ cần thiết để nhìn thấy các hiệu ứng do tác vụ thực hiện trước khi hoàn tất. Tuy vậy, **giá trị trả về có thể là đối tượng mutable**: sau thời điểm hoàn tất, các truy cập đồng thời tiếp theo vào đối tượng đó vẫn cần quy tắc đồng bộ riêng.

## 4. ExecutorService: gửi tác vụ rồi nhận Future

`ExecutorService` là abstraction quản lý thực thi tác vụ, có thể tái sử dụng một pool luồng. Khác với tạo `new Thread(...)` cho từng lần, nó tách tác vụ khỏi quyết định sử dụng bao nhiêu luồng.

Các overload thường dùng:

| Cách gọi | Kết quả |
| --- | --- |
| `execute(Runnable)` | Trả về `void`; không nhận một `Future` để chờ/đọc kết quả |
| `submit(Runnable)` | `Future<?>`; khi hoàn tất thành công, `get()` trả về `null` |
| `submit(Runnable, T result)` | `Future<T>`; khi hoàn tất thành công, `get()` trả lại **giá trị `result` được cung cấp trước** |
| `submit(Callable<T>)` | `Future<T>`; `get()` trả lại giá trị do `call()` tính và trả về |

### 4.1. Runnable có Future để biết khi nào hoàn thành

~~~java
// RunnableFutureDemo.java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class RunnableFutureDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger counter = new AtomicInteger();

        try {
            Future<?> first = pool.submit(() -> counter.addAndGet(3));
            Object noReturnValue = first.get();

            Future<String> second = pool.submit(
                () -> counter.addAndGet(2),
                "completed"
            );
            String status = second.get();

            System.out.println("First result: " + noReturnValue);
            System.out.println("Second result: " + status);
            System.out.println("Counter: " + counter.get());
        } finally {
            pool.shutdown();
        }
    }
}
~~~

~~~bash
javac RunnableFutureDemo.java
java RunnableFutureDemo
~~~

Stdout xác định:

~~~text
First result: null
Second result: completed
Counter: 5
~~~

- `first.get()` đợi tác vụ cộng 3 kết thúc; vì tác vụ là `Runnable` nên `get()` trả về `null`.
- `submit(runnable, "completed")` **không biến `run()` thành hàm trả về String**. Chuỗi `"completed"` là giá trị caller cung cấp trước và chỉ được trả về sau khi tác vụ hoàn tất thành công.
- Hai phép cộng an toàn nhờ `AtomicInteger`. `get()` được gọi lần lượt nên kết quả in luôn là 5. Không nên suy luận thứ tự bắt đầu giữa các task nếu không có quan hệ đồng bộ rõ ràng.
- `shutdown()` từ chối tác vụ mới nhưng **không tự chờ** các tác vụ trước đó hoàn thành. Ví dụ này đã gọi `get()` cho cả hai trước khi shutdown.

### 4.2. Callable: lấy kết quả thực sự do tác vụ tính

~~~java
// CallableResultDemo.java
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CallableResultDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            Callable<Integer> sum = () -> {
                int total = 0;
                for (int i = 1; i <= 5; i++) {
                    total += i;
                }
                return total;
            };

            Future<Integer> future = pool.submit(sum);
            System.out.println("Sum: " + future.get());
        } finally {
            pool.shutdown();
        }
    }
}
~~~

~~~bash
javac CallableResultDemo.java
java CallableResultDemo
~~~

Stdout:

~~~text
Sum: 15
~~~

`submit` trả về `Future<Integer>` ngay khi executor **chấp nhận** tác vụ, không đợi tính xong. `future.get()` là điểm có thể chờ và nhận `Integer` do `call()` trả về. `Callable` có thể được định nghĩa thành class riêng nếu logic dài; lambda phù hợp khi đoạn code ngắn, dễ đọc.

**Lựa chọn thực tế:** Nếu người gọi cần giá trị từ tác vụ, ưu tiên `Callable<T> + Future<T>`. Với logic không trả giá trị, `Runnable` phù hợp, kể cả khi bạn dùng `Future<?>` chỉ để theo dõi hoàn thành và lỗi.

## 5. FutureTask<V>: kết nối Callable với một Thread cụ thể

`FutureTask<V>` là một lớp hiện thực cả `Runnable` và `Future<V>`. Nó có thể **nhận một `Callable<V>` để thực thi** và **cho phép luồng khác gọi `get()` để lấy giá trị**. Vì `FutureTask` cũng là `Runnable`, bạn có thể truyền nó trực tiếp cho `new Thread(...)`.

~~~java
// FutureTaskThreadDemo.java
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class FutureTaskThreadDemo {
    public static void main(String[] args) throws Exception {
        Callable<String> work = () ->
            "calculated by " + Thread.currentThread().getName();

        FutureTask<String> task = new FutureTask<>(work);
        Thread thread = new Thread(task, "worker-1");

        thread.start();
        System.out.println(task.get());
    }
}
~~~

~~~bash
javac FutureTaskThreadDemo.java
java FutureTaskThreadDemo
~~~

Stdout:

~~~text
calculated by worker-1
~~~

Dòng `task.get()` có thể chờ đến khi thread hoàn thành. `FutureTask` chỉ hoàn tất một lần: gọi `run()` lại không tính lại kết quả của một instance đã hoàn thành. Với nhu cầu chạy nhiều tác vụ, điều phối tải và tái sử dụng luồng, `ExecutorService` thường thuận tiện hơn tự tạo hàng loạt `Thread`; `FutureTask` hữu ích khi cần kết nối mô hình `Thread` cơ bản với một kênh kết quả.

## 6. Truyền lỗi, hủy tác vụ và xử lý gián đoạn

### 6.1. Ngoại lệ trong task được quan sát như thế nào?

Khi một tác vụ được gửi bằng `submit` rồi ném exception, `Future.get()` ném `ExecutionException` và giữ ngoại lệ gốc ở `getCause()`. Điều này đúng cả với `Runnable` chạy qua `submit`; khác biệt ở khả năng **khai báo checked exception trong thân phương thức**, không phải ở việc có thể lỗi hay không.

Ví dụ Java 17+:

~~~java
// CallableFailureDemo.java
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CallableFailureDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            Callable<Integer> work = () -> {
                throw new IllegalArgumentException("invalid input");
            };
            Future<Integer> future = pool.submit(work);

            try {
                System.out.println(future.get());
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                System.out.println(
                    cause.getClass().getSimpleName() + ": " + cause.getMessage()
                );
            }
        } finally {
            pool.shutdown();
        }
    }
}
~~~

~~~bash
javac CallableFailureDemo.java
java CallableFailureDemo
~~~

Stdout:

~~~text
IllegalArgumentException: invalid input
~~~

Nếu dùng `execute(Runnable)`, caller không nhận `Future` để thu lỗi. Lỗi unchecked thoát khỏi `run()` được xử lý theo cơ chế uncaught-exception của luồng/executor, tùy cách quản lý luồng. Nếu dùng `submit` mà **bỏ qua `Future`**, người gọi có thể không nhìn thấy lỗi qua đường truyền kết quả. Vì thế hãy chủ động thu nhận/quan sát `Future` hoặc thiết lập cơ chế báo lỗi phù hợp.

### 6.2. Timeout và cancel

Ví dụ **đoạn mã minh họa**, giả sử đã có `Future<String> future` và phương thức bao quanh xử lý hoặc khai báo những exception cần thiết:

~~~java
String value = future.get(2, java.util.concurrent.TimeUnit.SECONDS);
~~~

Nếu quá 2 giây mà tác vụ chưa hoàn tất, lời gọi này ném `TimeoutException`. **Timeout của `get` chỉ giới hạn thời gian chờ của caller; nó không tự dừng task.** Caller có thể quyết định gọi `future.cancel(true)`, nhưng hủy có thành công hoặc khiến task dừng sớm hay không còn phụ thuộc trạng thái và khả năng hợp tác với interrupt.

~~~java
boolean cancellationRequested = future.cancel(true);
~~~

Với `mayInterruptIfRunning = true`, hệ thống có thể gửi interrupt tới luồng đang chạy; interrupt **không phải lệnh dừng cưỡng bức**. Tác vụ nên phản hồi khi thao tác chặn ném `InterruptedException`, hoặc kiểm tra trạng thái interrupt trong vòng lặp dài. Không được hiểu `isDone() == true` sau cancel là “công việc đã chạy đến cuối”; kết quả thành công, lỗi và hủy là các trạng thái khác nhau.

### 6.3. Đừng nuốt InterruptedException

Khi thread gọi `get()` hoặc `join()` đang chờ và bị interrupt, lời gọi có thể ném `InterruptedException`. Nếu tầng hiện tại có thể khai báo `throws InterruptedException`, thường nên truyền tiếp. Nếu phải xử lý tại chỗ, có thể khôi phục cờ interrupt để tầng trên biết:

~~~java
try {
    future.get();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // Dừng/thoát công việc của caller theo chính sách ứng dụng.
} catch (java.util.concurrent.ExecutionException e) {
    // Xử lý nguyên nhân từ e.getCause().
}
~~~

Đây là **đoạn minh họa xử lý**, giả sử đã tồn tại biến `future`. Nếu cần quản lý timeout, thêm nhánh `TimeoutException`; nếu có thể cancel, cân nhắc `CancellationException`. Không gộp tất cả vào một `catch (Exception ignored)` vì làm mất thông tin lỗi và tín hiệu interrupt.

## 7. CompletableFuture: kết quả bất đồng bộ có thể kết hợp

Ngoài `Future` cơ bản, Java còn có `CompletableFuture<T>`. Nó hữu ích khi muốn **ghép các bước tính toán, biến đổi kết quả, kết hợp nhiều task hoặc xử lý lỗi** mà không phải ngay lập tức chặn bằng `get()`. Đây là công cụ của Java tiêu chuẩn; phần này chỉ giới thiệu điểm nối với chủ đề truyền kết quả, không đi sâu vào toàn bộ API.

~~~java
// CompletableFutureDemo.java
import java.util.concurrent.CompletableFuture;

public class CompletableFutureDemo {
    public static void main(String[] args) {
        CompletableFuture<Integer> future =
            CompletableFuture.supplyAsync(() -> 21)
                .thenApply(value -> value * 2);

        System.out.println("Answer: " + future.join());
    }
}
~~~

~~~bash
javac CompletableFutureDemo.java
java CompletableFutureDemo
~~~

Stdout:

~~~text
Answer: 42
~~~

- `supplyAsync` nhận logic có giá trị trả về, gần với ý nghĩa `Callable`, nhưng dùng `Supplier<T>`: phương thức `get()` của `Supplier` không khai báo checked exception.
- `thenApply` mô tả bước biến đổi từ `Integer` sang `Integer` khi có kết quả; không cần caller tự lấy rồi gửi kết quả thủ công.
- `join()` của `CompletableFuture` chờ và trả kết quả giống ý tưởng `get()` nhưng dùng ngoại lệ unchecked cho lỗi tính toán (thường là `CompletionException`); nó không khai báo `InterruptedException`. Không nhầm `CompletableFuture.join()` với `Thread.join()`.
- Mặc định `supplyAsync` không truyền executor sẽ dùng cơ chế executor mặc định của `CompletableFuture` (thường là common ForkJoinPool; có thể dùng luồng khác khi common pool không đủ parallelism). Với ứng dụng cần kiểm soát tài nguyên, có thể truyền một `Executor` phù hợp vào overload của `supplyAsync`.

## 8. Chọn cách truyền kết quả theo tình huống

| Nhu cầu | Cách thường phù hợp | Điều cần nhớ |
| --- | --- | --- |
| Chạy một hành động đơn giản trên một luồng thủ công | `Runnable` + `Thread.start()` | `run()` không tạo thread mới |
| Chỉ cần main đợi worker xong và đọc state đã ghi | Shared holder + `Thread.join()` | Đồng bộ trạng thái truy cập khi cần; `join()` không trả giá trị |
| Dùng `Thread` nhưng muốn nhận kết quả/ngoại lệ kiểu rõ ràng | `Callable<T>` + `FutureTask<T>` + `Thread` | `FutureTask` vừa là `Runnable` vừa là `Future<T>` |
| Chạy nhiều tác vụ độc lập, cần trả kết quả | `ExecutorService.submit(Callable<T>)` | Nhận `Future<T>` rồi `get()` ở điểm thích hợp |
| Không trả kết quả nhưng cần đợi/biết task có lỗi | `ExecutorService.submit(Runnable)` | `Future<?>.get()` trả `null` nếu thành công |
| Runnable và muốn có thông báo hoàn tất dạng một giá trị đã biết | `submit(Runnable, T result)` | `result` do caller truyền vào, không được tính bằng giá trị trả về của `run()` |
| Cần xâu chuỗi và biến đổi kết quả bất đồng bộ | `CompletableFuture<T>` | Xử lý exception, executor và luồng chạy continuation đúng cách |

### 8.1. Những lỗi thường gặp

1. **Gọi `task.run()` rồi nghĩ đã chạy đồng thời.** Đó là lời gọi trên thread hiện tại; cần `start()` hoặc executor.
2. **Truyền `Callable` trực tiếp cho `new Thread(callable)`.** Constructor nhận `Runnable`; bọc bằng `FutureTask` hoặc dùng `submit`.
3. **Thấy `Future<?>` nên nghĩ task trả kết quả hữu ích.** `submit(Runnable)` hoàn tất thành công cho giá trị `null`; muốn giá trị tính toán thì dùng `Callable<T>`.
4. **Ngay sau `submit` gọi `get()` cho từng task trong một vòng lặp và mong toàn bộ quy trình không chặn.** Tác vụ vẫn chạy trên executor nhưng caller có thể chờ từng phần. Có thể gửi các task trước rồi thu kết quả sau, hoặc dùng công cụ phối hợp bất đồng bộ phù hợp.
5. **Bỏ `Future`, quên exception trong task.** Với `submit`, lỗi tác vụ được lưu vào future và thường quan sát khi `get()`.
6. **Coi `shutdown()` là “mọi task đã hoàn thành”.** Nó khởi động quá trình đóng, từ chối task mới nhưng không chờ hoàn tất; khi cần có thể đợi future hoặc dùng `awaitTermination` với chiến lược timeout.
7. **Dùng `future.get()` vô hạn trong đường xử lý có deadline.** Xem xét `get(timeout, unit)`, cancellation và quản lý tài nguyên.
8. **Nghĩ `cancel(true)` luôn giết được tác vụ.** Tác vụ phải phản hồi hợp tác với interrupt; một số công việc chặn hoặc thư viện có quy tắc hủy khác nhau.
9. **Dùng chung một kết quả mutable rồi coi `Future.get()` xử lý mọi đồng bộ về sau.** Lời gọi hoàn thành tạo quan hệ bộ nhớ cho công việc đã hoàn tất, không bảo vệ các lần sửa đồng thời sau đó.

## 9. Câu hỏi tự kiểm tra

**1. Một tác vụ chỉ ghi log, không trả giá trị.** Dùng `Runnable`. Nếu cần đợi nó xong qua executor, `submit(runnable)` và `Future<?>.get()` thích hợp.

**2. Một tác vụ tính ra danh sách kết quả.** Dùng `Callable<List<T>>` với `ExecutorService.submit`; kiểu của `Future` là `Future<List<T>>`. Nếu muốn trả collection để nhiều luồng đọc tiếp, nên thiết kế khả năng bất biến hoặc đồng bộ phù hợp.

**3. Vì sao `new Thread(() -> 42)` không diễn tả kết quả kiểu `Callable<Integer>`?** Lambda cho `Thread(Runnable)` phải tương thích với `void run()`; expression body dạng phép tính thuần `42` không phải statement expression hợp lệ cho void-compatible lambda. Để trả một số nguyên, dùng `Callable<Integer>` và `FutureTask` hoặc `submit`.

**4. Vì sao `future.isDone()` là `true` nhưng `future.get()` vẫn ném exception?** Vì `isDone` bao gồm thành công, lỗi và hủy, không chỉ thành công.

**5. Người gọi timeout ở `future.get(1, SECONDS)`; tác vụ có chắc đã dừng?** Không. Timeout chỉ dừng việc đợi của caller. Cần quyết định có yêu cầu cancel hay không và thiết kế task hỗ trợ interrupt/timeout từ chính nó.

**6. Trong hai cách `submit(runnable, "OK")` và `submit(callableReturningOK)`, ai tạo ra chuỗi trả về?** Ở cách đầu, caller cấp chuỗi `"OK"` từ trước; ở cách sau, `Callable.call()` tính/trả giá trị, và `get()` nhận lại kết quả đó.

**Ghi nhớ:** `Runnable` và `Callable` định nghĩa công việc; `Thread` hoặc `ExecutorService` đảm nhận thực thi; `Future`/`FutureTask` biểu diễn điểm đồng bộ và kênh nhận **kết quả hoặc lỗi**. Chọn kiểu tác vụ theo nhu cầu trả giá trị, rồi chọn cách lấy kết quả theo nhu cầu điều phối luồng, timeout và xử lý thất bại.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-api-010">sleep(), yield() và các giới hạn khi điều phối</a>

<details>
<summary>Xem chi tiết</summary>

# sleep(), yield() và các giới hạn khi điều phối thread trong Java

## 1. Mục tiêu bài học

Sau bài này, bạn có thể:

- Giải thích chính xác **Thread.sleep(...)** và **Thread.yield()** tác động đến **thread đang gọi** như thế nào.
- Phân biệt tạm ngừng theo thời gian, nhường cơ hội chạy và chờ một điều kiện/sự kiện.
- Xử lý **InterruptedException** khi thread đang ngủ, hiểu việc cờ interrupt bị xóa và cách khôi phục khi cần.
- Hiểu tại sao **sleep()** và **yield()** không đảm bảo thread khác chạy, không tạo đồng bộ hóa, không giải phóng monitor lock, không bảo đảm công bằng hay thứ tự chạy.
- Thay các cách điều phối dựa trên thời gian bằng cơ chế phù hợp của Java khi cần xác nhận hoàn thành, trao tín hiệu, quản lý tài nguyên hoặc chờ điều kiện.

Bài học thuộc **Java concurrency nói chung**, sử dụng API nền tảng của JDK; các cơ chế bất đồng bộ và quản lý task thuộc Spring là phạm vi riêng.

## 2. Bản chất việc điều phối thread

Trong một tiến trình Java có nhiều thread, mỗi thread có luồng lệnh và stack riêng; các thread có thể chạy xen kẽ trên một hoặc nhiều lõi CPU. JVM và hệ điều hành tham gia quyết định lúc nào thread được chạy. **Thread.State.RUNNABLE** bao gồm cả thread đang chạy và thread có thể được lập lịch chạy; trạng thái này không xác nhận thread đang chiếm CPU ở thời điểm quan sát.

Không có API Thread thông thường nào hứa rằng sau khi thread A tạm ngừng, thread B nhất định sẽ chạy ngay hoặc kết thúc trước A. Kết quả phụ thuộc vào scheduler, tải hệ thống, số CPU, ưu tiên, khóa, I/O và các thread khác. Hai chương trình giống nhau có thể cho thứ tự log khác nhau qua các lần chạy.

**sleep()** và **yield()** cùng thuộc lớp **java.lang.Thread**, đều là phương thức **static** tác động tới **thread hiện tại**:

- **Thread.sleep(...)**: đề nghị thread hiện tại ngừng thực thi ít nhất trong khoảng thời gian được yêu cầu theo khả năng đo thời gian của hệ thống, trừ khi bị ngắt. Khi hết thời gian, thread trở lại đủ điều kiện chạy; không có cam kết chạy lại ngay.
- **Thread.yield()**: đưa ra một **gợi ý** cho scheduler rằng thread hiện tại sẵn sàng nhường lượt sử dụng CPU. Scheduler có quyền bỏ qua hoàn toàn.

Cả hai phương thức đều **không phải primitive đồng bộ hóa** hay cơ chế chuyển điều khiển trực tiếp cho một thread cụ thể.

## 3. Thread.sleep(...): tạm dừng thread hiện tại

Các overload quan trọng trên Java 17:

~~~java
Thread.sleep(long millis);
Thread.sleep(long millis, int nanos);
~~~

Đây là chữ ký mô tả API, không phải các lời gọi đầy đủ; cả hai khai báo **throws InterruptedException**.

- **millis** biểu diễn số mili giây được yêu cầu. Giá trị âm gây **IllegalArgumentException**.
- Trong overload hai tham số, **nanos** là phần nano giây bổ sung, hợp lệ từ **0 đến 999_999**; giá trị ngoài phạm vi cũng gây **IllegalArgumentException**.
- Không nên suy luận độ chính xác nano giây từ overload có tham số nanos. Thời lượng thực tế chịu giới hạn của clock, độ phân giải timer và scheduler.
- **sleep(...)** không trả giá trị, không báo thread khác đã chạy bao lâu và không cam kết chính xác thời điểm thread tiếp tục.
- Viết **worker.sleep(100)** vẫn gọi phương thức static thông qua một tham chiếu (không nên làm): thread bị ngủ là **thread đang thực thi lời gọi**, không nhất thiết là đối tượng worker. Viết **Thread.sleep(100)** cho rõ ý định.

### Ví dụ 1: ngủ, khởi chạy và chờ kết thúc

Tệp **SleepJoinDemo.java**:

~~~java
public class SleepJoinDemo {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("main: before start");

        Thread worker = new Thread(() -> {
            System.out.println("worker: before sleep");
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                System.out.println("worker: interrupted");
                return;
            }
            System.out.println("worker: after sleep");
        }, "sleeper");

        worker.start();
        worker.join();
        System.out.println("main: after join");
    }
}
~~~

Chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 SleepJoinDemo.java
java SleepJoinDemo
~~~

Khi không có ngắt bên ngoài, stdout:

~~~text
main: before start
worker: before sleep
worker: after sleep
main: after join
~~~

**Giải thích:**

1. **main** in dòng đầu trước khi gọi **start()**.
2. **worker** in dòng trước khi ngủ rồi gọi **Thread.sleep(100)** trên chính thread worker.
3. Khi khoảng ngủ kết thúc, worker được phép tiếp tục; nó có thể phải đợi scheduler cấp CPU.
4. **main** đang ở **worker.join()**, chỉ in dòng cuối sau khi worker hoàn thành.

Thứ tự ở ví dụ này ổn định nhờ chương trình và **join()**; **sleep(100)** không tạo ra thứ tự đó. Nếu có thread khác làm việc song song, thứ tự log của những thread đó không được bảo đảm.

### sleep() không phải đồng hồ chính xác

**sleep(100)** không có nghĩa là câu lệnh kế tiếp chắc chắn bắt đầu đúng sau 100 ms. Timer có độ phân giải nhất định, scheduler có thể cho thread tiếp tục trễ và lệnh ngủ có thể bị ngắt sớm. Đặt **sleep(10)** trong vòng lặp 100 lần cũng không bảo đảm tổng thời gian đúng một giây, bởi còn thời gian chạy logic mỗi vòng và độ trễ lập lịch.

Để **đo thời gian đã trôi qua**, dùng **System.nanoTime()** rồi lấy hiệu hai giá trị; không dùng trực tiếp thời gian đồng hồ lịch **System.currentTimeMillis()** làm thước đo đơn điệu cho độ trễ ngắn. Dù dùng nanoTime(), việc đo chính xác không làm cho scheduler chạy chính xác theo từng nano giây.

~~~java
long start = System.nanoTime();
Thread.sleep(100);
long elapsedNanos = System.nanoTime() - start;
System.out.println("elapsed ms = " + elapsedNanos / 1_000_000.0);
~~~

Đoạn trên là **mã minh họa**, cần đặt trong phương thức có xử lý/khai báo **InterruptedException**. Giá trị in ra thay đổi theo môi trường; không có stdout số cố định.

## 4. sleep() và interruption: dừng có hợp tác

**Thread.interrupt()** là tín hiệu yêu cầu thread hợp tác dừng hoặc hủy thao tác đang chờ. Nếu một thread bị interrupt trong lúc **sleep()**, lời gọi **sleep()** kết thúc bằng **InterruptedException**. Đồng thời, trạng thái interrupt của thread **được xóa khi exception được ném**. Nếu cờ đã được đặt trước khi gọi sleep(), lời gọi cũng có thể ném exception ngay và xóa cờ.

Vì **Runnable.run()** không cho checked exception thoát trực tiếp, mã trong run() thường cần bắt **InterruptedException**. Nếu task không thể xử lý yêu cầu dừng tại tầng hiện tại, cách phổ biến là **khôi phục cờ interrupt** bằng **Thread.currentThread().interrupt()** và kết thúc hợp tác.

### Ví dụ 2: interrupt một worker đang ngủ

Tệp **SleepInterruptDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;

public class SleepInterruptDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            ready.countDown();
            try {
                while (true) {
                    Thread.sleep(60_000);
                }
            } catch (InterruptedException ex) {
                System.out.println("worker: interrupted");
                Thread.currentThread().interrupt();
                System.out.println(
                    "worker: flag restored = "
                        + Thread.currentThread().isInterrupted()
                );
            }
        }, "sleeping-worker");

        worker.start();
        ready.await();
        worker.interrupt();
        worker.join();

        System.out.println("main: worker done");
    }
}
~~~

~~~bash
javac -encoding UTF-8 SleepInterruptDemo.java
java SleepInterruptDemo
~~~

Stdout:

~~~text
worker: interrupted
worker: flag restored = true
main: worker done
~~~

**CountDownLatch** ở đây chỉ báo rằng worker đã tới điểm chuẩn bị ngủ; không khẳng định worker đã vào trạng thái TIMED_WAITING. Dù **interrupt()** đến trước hoặc trong khi ngủ, một lần sleep tiếp theo sẽ phát hiện yêu cầu ngắt; vòng lặp thoát qua catch. Dùng vòng lặp để kết quả không phụ thuộc vào việc main phải gửi tín hiệu trong đúng một lần ngủ. **join()** giữ main chờ đến khi worker đã kết thúc.

Không nên bắt InterruptedException rồi im lặng tiếp tục chạy vòng lặp vô hạn; thao tác đó có thể làm mất tín hiệu hủy. Nếu tầng gọi đã khai báo **throws InterruptedException** và phù hợp để xử lý, có thể để exception truyền lên. Nếu cần thoát sớm, dọn dẹp tài nguyên trong **finally** khi thích hợp.

Khác biệt của hai API đọc cờ:

| API | Đọc cờ của thread nào? | Có xóa cờ không? |
| --- | --- | --- |
| **thread.isInterrupted()** | Đối tượng thread được gọi | Không |
| **Thread.interrupted()** | Thread **hiện tại** | **Có** |

Không sử dụng **Thread.interrupted()** như một phép kiểm tra vô hại trong vòng lặp nếu không có chủ đích tiêu thụ tín hiệu ngắt.

## 5. sleep() không giải phóng monitor lock

Giả sử một thread đang ở trong **synchronized(lock)**. Khi nó gọi **Thread.sleep(...)**, thread ngủ nhưng **vẫn sở hữu monitor của lock** trong suốt phần synchronized cho đến khi rời khỏi phần đó. Thread khác muốn vào **synchronized(lock)** phải chờ, ngay cả khi CPU rảnh.

### Ví dụ 3: đối tượng lock vẫn được giữ

Tệp **SleepHoldsLockDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;

public class SleepHoldsLockDemo {
    public static void main(String[] args) throws InterruptedException {
        Object lock = new Object();
        CountDownLatch hasLock = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            synchronized (lock) {
                System.out.println("holder: acquired");
                hasLock.countDown();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("holder: leaving");
            }
        }, "lock-holder");

        holder.start();
        hasLock.await();

        synchronized (lock) {
            System.out.println("main: acquired");
        }

        holder.join();
    }
}
~~~

~~~bash
javac -encoding UTF-8 SleepHoldsLockDemo.java
java SleepHoldsLockDemo
~~~

Stdout (không có interrupt từ bên ngoài):

~~~text
holder: acquired
holder: leaving
main: acquired
~~~

Main chỉ tìm cách lấy lock sau khi latch báo holder đã giữ nó, và chỉ vào được synchronized sau khi holder rời khỏi synchronized. **sleep()** bên trong vùng khóa không tự mở khóa.

Điều này khác **Object.wait()**: phải gọi wait() khi đang sở hữu monitor của đối tượng, và thao tác wait() sẽ **nhả monitor của đối tượng đó trong khi chờ**, rồi lấy lại monitor trước khi wait() trả về bình thường. Với **ReentrantLock**, thao tác **Condition.await()** tương ứng sẽ nhả lock liên quan trong lúc chờ và lấy lại trước khi trở về. **sleep()** không làm những việc đó.

Hạn chế giữ lock trong khi thực hiện sleep, I/O hay chờ dài, vì các thread khác có thể bị chặn không cần thiết.

## 6. Thread.yield(): gợi ý nhường CPU, không phải lệnh chuyển lượt

**Thread.yield()** là phương thức **static void** không có tham số và không khai báo InterruptedException. Nó nói với scheduler rằng thread hiện tại sẵn lòng nhường quyền sử dụng CPU lúc này, nhưng **scheduler có thể bỏ qua gợi ý**.

Không có cam kết nào rằng:

- Thread hiện tại sẽ thực sự dừng, chuyển trạng thái hay mất CPU.
- Một thread cụ thể sẽ được chạy tiếp.
- Thread vừa yield sẽ bị xếp cuối hàng đợi.
- Thread có cùng ưu tiên sẽ được chạy công bằng kiểu round-robin.
- Một vòng lặp chứa yield() sẽ giúp task khác luôn có tiến độ.
- Yield sẽ giúp dữ liệu ghi từ thread khác trở nên nhìn thấy hoặc một điều kiện đúng.

**yield()** không ném InterruptedException, không đảm bảo delay tối thiểu và không có vai trò thay thế **sleep()**.

### Ví dụ 4: yield() không quyết định thứ tự thực thi

Tệp **YieldDemo.java**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class YieldDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger completed = new AtomicInteger();

        Runnable task = () -> {
            for (int i = 0; i < 10_000; i++) {
                Thread.yield();
            }
            completed.incrementAndGet();
        };

        Thread first = new Thread(task, "first");
        Thread second = new Thread(task, "second");
        first.start();
        second.start();

        first.join();
        second.join();

        System.out.println("completed = " + completed.get());
    }
}
~~~

~~~bash
javac -encoding UTF-8 YieldDemo.java
java YieldDemo
~~~

Stdout:

~~~text
completed = 2
~~~

Kết quả luôn là 2 khi chương trình chạy bình thường, vì mỗi thread hoàn tất một lần tăng **AtomicInteger** và main đã **join()** cả hai. Không thể kết luận mỗi lệnh **yield()** đã nhường CPU hay hai thread luân phiên chạy. Không lấy ví dụ này làm benchmark: 10.000 lời gọi yield có thể tốn chi phí đáng kể mà không đem lại điều phối có ích.

Nếu chèn lệnh in tên thread bên trong vòng lặp, thứ tự các dòng của first và second có thể khác nhau trong mỗi lần chạy; không có stdout theo thứ tự cố định để kiểm thử.

### yield() vẫn giữ lock

Nếu gọi **Thread.yield()** trong **synchronized(lock)**, thread vẫn giữ monitor, giống như khi gọi sleep(). Các thread đang BLOCKED trên lock đó không vào được synchronized chỉ nhờ yield. Với lock khác như **ReentrantLock**, yield cũng không tự gọi unlock().

Do đó, thêm yield vào vùng khóa có thể tăng thời gian một thread giữ lock mà không tăng cơ hội cho thread đang chờ chính lock ấy.

## 7. Giới hạn sâu hơn: scheduling khác synchronization

Một chương trình concurrent cần giải quyết **hai vấn đề riêng**:

1. **Scheduling/progress:** thread nào được chạy, khi nào và trong bao lâu?
2. **Synchronization/memory visibility:** khi một thread ghi dữ liệu, thread khác có được thấy giá trị mới và được phép đọc/ghi đồng thời ra sao?

**sleep()** và **yield()** chỉ có thể ảnh hưởng đến lịch chạy; chúng **không tạo quan hệ happens-before**, không làm thao tác **count++** trở nên nguyên tử, không thay thế **volatile**, **synchronized**, lock, atomic types, blocking queues hoặc latch.

### Ví dụ phản tác dụng: chờ cờ bằng yield()

~~~java
class SharedState {
    boolean ready; // Không volatile.
}

// Mã minh họa lỗi, KHÔNG phải chương trình độc lập an toàn:
// Thread đọc cờ có thể không bao giờ quan sát được thay đổi.
SharedState shared = new SharedState();

Thread observer = new Thread(() -> {
    while (!shared.ready) {
        Thread.yield(); // Không đảm bảo visibility.
    }
});
observer.start();

shared.ready = true;
~~~

Đoạn trên **cố tình thiếu đồng bộ hóa** để minh họa lỗi. Việc cập nhật **ready** từ thread khác có data race; JMM không bảo đảm observer thấy thay đổi trong thời gian hữu hạn, kể cả có gọi **yield()** hoặc **sleep()** trong vòng lặp.

Đặt **ready** là **volatile** hoặc dùng **AtomicBoolean** giúp bảo đảm visibility phù hợp, nhưng vẫn còn một vòng lặp polling chiếm CPU hoặc tạo độ trễ. Khi cần **đợi sự kiện**, cách thích hợp hơn là **CountDownLatch.await()**, **BlockingQueue.take()**, **Object.wait()/notify()** khi có giao thức monitor đúng, hay **Condition.await()/signal()** đi kèm lock.

### Ví dụ 5: chờ tín hiệu thay cho sleep/yield polling

Tệp **SignalDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;

public class SignalDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch finished = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            System.out.println("worker: finished work");
            finished.countDown();
        }, "worker");

        worker.start();
        finished.await();
        System.out.println("main: observed completion");
        worker.join();
    }
}
~~~

~~~bash
javac -encoding UTF-8 SignalDemo.java
java SignalDemo
~~~

Stdout:

~~~text
worker: finished work
main: observed completion
~~~

Không có thời gian ngủ đoán trước. **await()** chờ cho đến khi đếm về 0 hoặc bị interrupt; **countDown()** tạo tín hiệu và các bảo đảm đồng bộ hóa tương ứng. Với trường hợp đơn thuần cần đợi một thread kết thúc, **join()** đã đủ; latch hữu ích khi tín hiệu cần biểu diễn một mốc công việc riêng, không nhất thiết là toàn bộ vòng đời của thread.

### Khi nhiều thread đang chờ

- **Thread.sleep(...)** chỉ đánh thức chính thread hiện tại sau khoảng thời gian dự kiến hoặc interruption; không có đối tượng điều kiện dùng chung.
- **Thread.yield()** không đánh thức một thread đang WAITING vì latch/monitor/condition.
- **Object.notify()/notifyAll()**, **CountDownLatch.countDown()**, **BlockingQueue.put(...)** và cơ chế condition phù hợp mới phát tín hiệu theo hợp đồng riêng của chúng.
- **join()** chờ một thread khác kết thúc; bản **join(timeout)** có thể trở về vì hết hạn khi thread đích còn sống, nên phải kiểm tra điều kiện sau khi chờ nếu cần chắc chắn.

## 8. Đọc Thread.State đúng cách

Trong các bản triển khai JVM thông thường, một thread đang ngủ sẽ được quan sát ở trạng thái **TIMED_WAITING**; sau khi khoảng ngủ hoàn thành, thread trở lại đủ điều kiện chạy. **yield()** không có một trạng thái Thread.State riêng tên là YIELDED. Đừng dùng getState() như một công cụ chứng minh một lệnh yield đã được scheduler tôn trọng.

| Tình huống | Trạng thái có thể quan sát | Điểm cần hiểu |
| --- | --- | --- |
| Thread vừa new, chưa start() | NEW | Chưa chạy |
| Thread đang chạy hoặc sẵn sàng chạy | RUNNABLE | Không bảo đảm đang chiếm CPU |
| Thread trong Thread.sleep(...) | TIMED_WAITING | Có deadline, vẫn giữ các lock đã sở hữu |
| Thread chờ monitor vào synchronized | BLOCKED | Đang chờ giành monitor |
| Thread đang join() không timeout | WAITING | Đang chờ thread khác kết thúc |
| Thread đã kết thúc run() | TERMINATED | Không thể start lại |

**getState()** là ảnh chụp tức thời; trạng thái có thể đổi ngay sau lần đọc. Không viết logic kiểu “đợi đến khi thread có state TIMED_WAITING rồi mới gửi interrupt” để đồng bộ: đây là race do quan sát trạng thái. Hãy dùng latch hoặc giao thức tín hiệu rõ ràng khi cần xác nhận một mốc.

## 9. Bảng so sánh: chọn đúng công cụ

| API | Mục đích chính | Điều gì làm việc chờ kết thúc? | Có tự nhả monitor đang giữ? | Bảo đảm về thread khác |
| --- | --- | --- | --- | --- |
| **Thread.sleep(duration)** | Tạm dừng thread hiện tại theo thời gian | Hết thời hạn hoặc InterruptedException | Không | Không bảo đảm thread nào chạy |
| **Thread.yield()** | Đưa hint nhường lượt CPU | Trả về theo quyết định triển khai/scheduler | Không | Không bảo đảm hint được chấp nhận |
| **Thread.join()** | Chờ thread đích kết thúc | Thread đích kết thúc; hoặc interruption/timeout | Không tự nhả monitor tùy ý của caller | Đợi đúng thread đích, có quy tắc memory ordering |
| **Object.wait()** | Đợi điều kiện qua monitor | notify/notifyAll, interrupt, timeout hoặc wakeup giả | Nhả monitor **của đối tượng đang wait** trong lúc đợi | Phải kiểm tra điều kiện trong vòng lặp |
| **CountDownLatch.await()** | Đợi số lần báo sự kiện đạt 0 | countDown đủ, interrupt hoặc timeout tương ứng overload | Không tự nhả monitor tùy ý | Giao thức đồng bộ hóa với countDown |
| **BlockingQueue.take()** | Đợi một phần tử | Có phần tử hoặc interrupt | Không tự nhả monitor tùy ý | Trao đổi dữ liệu thread-safe |

Lưu ý: “không tự nhả monitor” chỉ nói về lock **đã được caller sở hữu**; các công cụ concurrent có thể dùng lock nội bộ của chúng theo cách riêng. Với **Object.wait()**, thread phải sở hữu đúng monitor và sau khi được đánh thức cần tái giành monitor trước khi tiếp tục.

## 10. Các anti-pattern cần tránh

### Dùng sleep() thay cho join()

~~~java
worker.start();
Thread.sleep(100);
System.out.println("worker chắc đã xong"); // Giả định sai.
~~~

Worker có thể mất hơn 100 ms, bị thiếu CPU hoặc chờ I/O/lock. Khi cần đợi thực sự, gọi **worker.join()** (và xử lý InterruptedException) hoặc chờ tín hiệu hoàn tất tương ứng.

### Dùng sleep() làm hàng rào bộ nhớ

~~~java
shared.ready = true;
Thread.sleep(10);
// Không suy ra thread khác đã thấy shared.ready.
~~~

Đây là minh họa lỗi; giả định **shared** đã được khai báo. **sleep()** không thay thế cơ chế công bố giá trị chia sẻ an toàn.

### Dùng yield() để sửa race condition

~~~java
while (!state.ready) {
    Thread.yield();
}
~~~

Đoạn này cũng là minh họa lỗi nếu **state.ready** không được đồng bộ hóa. Yield không sửa data race, có thể gây busy waiting và có thể bị scheduler bỏ qua. Nếu bắt buộc polling, dùng visibility đúng và giới hạn/pacing phù hợp; ưu tiên chờ có tín hiệu.

### Ngủ trong synchronized rồi kỳ vọng thread khác vào được

~~~java
synchronized (lock) {
    Thread.sleep(1000);
}
~~~

Đây là đoạn minh họa trong phương thức đã khai báo/đã xử lý InterruptedException; **lock** là đối tượng đã có. Thread khác muốn vào synchronized(lock) phải đợi cho đến khi đoạn synchronized kết thúc.

### Dùng yield() làm cơ chế fairness

Lặp đi lặp lại yield() không thiết lập công bằng, giới hạn starvation hay thứ tự cố định. Thiết kế dựa vào **giao thức trao quyền** rõ ràng nếu có yêu cầu lần lượt; ví dụ Semaphore/BlockingQueue/lock theo nhu cầu, sau đó vẫn phải xem bảo đảm fairness cụ thể của từng API. Scheduler không có nghĩa vụ đáp ứng công bằng chỉ vì thread gọi yield.

### Đo “hiệu năng” bằng một lần sleep/yield

Một lần thử chịu nhiễu do JIT, GC, system load, CPU frequency, scheduler và I/O; không thể lấy thời gian chờ ngủ làm CPU-time của công việc. Khi nghiên cứu latency, phân biệt **wall-clock elapsed time**, CPU time và thời gian thread bị BLOCKED/WAITING; đo lặp lại bằng công cụ phù hợp và nêu rõ môi trường.

## 11. Khi nào có thể dùng sleep() hoặc yield()?

**sleep() có ích** khi thật sự muốn trì hoãn một công việc, thử nghiệm có chủ đích độ trễ, hoặc điều tiết polling đơn giản trong điều kiện chấp nhận độ trễ và có đường hủy đúng. Đối với lịch chạy định kỳ, tác vụ nhiều thread hoặc deadline quan trọng, dùng **ScheduledExecutorService**, timeout có điều kiện, hoặc bộ lập lịch phù hợp; các công cụ này cũng không thể hứa timing tuyệt đối, nhưng cho API quản lý tác vụ và hủy rõ ràng hơn.

**yield()** thường hiếm cần trong ứng dụng nghiệp vụ. Nó đôi khi hữu ích trong thử nghiệm, quan sát ứng xử khi lập lịch, hoặc một số kỹ thuật tối ưu hóa rất đặc biệt đã được đo kiểm; ngay cả khi đó phải chấp nhận scheduler có thể bỏ qua. Không dùng yield làm giải pháp mặc định cho chờ trạng thái, giảm CPU hoặc tránh deadlock.

Java 21 còn có **virtual threads** và các đặc điểm lập lịch khác so với platform threads; nhưng nguyên tắc nền tảng vẫn đúng: không lấy **sleep()** và **yield()** làm hợp đồng đồng bộ hóa, thứ tự thực thi hoặc fairness. Các ví dụ thực hành ở bài này dùng API Java 17+.

## 12. Câu hỏi tự kiểm tra

**1. Nếu gọi Thread.sleep(200) từ main, một đối tượng Thread tên worker có bị ngủ không?**  
Không. Sleep là static; **main** bị ngủ vì nó thực thi lời gọi. Muốn worker tự ngủ, mã chạy trong worker phải gọi sleep.

**2. yield() có đảm bảo thread khác cùng độ ưu tiên được chạy không?**  
Không. Chỉ là một hint có thể bị bỏ qua. Thread vừa yield cũng có thể chạy tiếp.

**3. Thread đang giữ monitor rồi sleep() có nhả monitor không?**  
Không. Thread khác cần monitor đó vẫn phải chờ. **Object.wait()** trên cùng monitor có cơ chế nhả/reacquire khác.

**4. Sau sleep(1000), công việc chắc chắn chạy lại đúng 1 giây không?**  
Không. Scheduler có thể cho chạy muộn; interrupt có thể khiến sleep kết thúc sớm bằng exception.

**5. Có thể dùng while (!ready) { Thread.yield(); } để bảo đảm đọc được cập nhật từ thread khác không?**  
Không. Yield không tạo happens-before và không giải quyết data race. Cần visibility/synchronization đúng và nên chờ qua tín hiệu khi có thể.

**6. Nếu ngủ bị interrupt, vì sao nhiều ví dụ gọi Thread.currentThread().interrupt() trong catch?**  
Vì khi **sleep()** ném **InterruptedException**, cờ đã bị xóa. Khôi phục cờ giúp tầng xử lý sau còn nhận biết yêu cầu ngắt, nếu đó là chính sách xử lý phù hợp.

**7. Nếu chỉ muốn main đợi worker hoàn tất, chọn sleep(100) hay worker.join()?**  
Chọn **join()**: nó đợi sự kiện hoàn tất thật sự của worker thay vì đoán một độ trễ.

## 13. Ghi nhớ

**Thread.sleep(...)** yêu cầu thread hiện tại tạm dừng theo thời gian; có thể bị interrupt và **không nhả lock**. **Thread.yield()** là một gợi ý cho scheduler, có thể bị bỏ qua và **không có bảo đảm tiến độ**. Cả hai đều **không tạo đồng bộ hóa hay memory visibility**. Khi bài toán thực chất là “đợi công việc xong”, “đợi trạng thái thay đổi”, “trao tín hiệu” hoặc “bảo đảm thread-safe”, hãy dùng API tương ứng như **join**, **CountDownLatch**, **BlockingQueue**, **synchronized/Condition** hoặc atomic/volatile theo đúng yêu cầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-api-011">join() để chờ kết thúc một thread</a>

<details>
<summary>Xem chi tiết</summary>

# `Thread.join()`: chờ một thread kết thúc trong Java

## 1. `join()` giải quyết bài toán gì?

Trong Java, sau khi gọi `Thread.start()`, thread mới có thể chạy đồng thời với thread đang gọi. `start()` **không chờ** công việc trong thread đó hoàn tất. Nếu thread hiện tại cần kết quả hoặc cần bảo đảm công việc kia đã kết thúc trước khi tiếp tục, có thể gọi `join()` trên chính đối tượng `Thread` đại diện cho thread cần chờ.

```java
Thread worker = new Thread(() -> doWork());
worker.start();  // Yêu cầu JVM bắt đầu thread worker
worker.join();   // Thread hiện tại chờ worker kết thúc
useResult();     // Chỉ chạy sau khi join() trở về bình thường
```

Đoạn trên **chỉ là mã minh họa** vì `doWork()` và `useResult()` chưa được định nghĩa; ví dụ hoàn chỉnh ở phần tiếp theo.

**Cú pháp cốt lõi:** `worker.join()` có nghĩa “thread **đang gọi** sẽ chờ cho `worker` kết thúc”. Nó không mang nghĩa worker chờ thread đang gọi, cũng không tự khởi động worker.

- Nếu gọi từ `main`, `main` là thread bị chặn trong lúc chờ.
- Nếu gọi từ một worker khác, worker gọi `join()` là thread bị chặn.
- Thread đích vẫn tiếp tục thực thi theo lịch của JVM; `join()` không “chạy hộ”, không khiến thread đích kết thúc sớm và không bảo đảm nó thành công.
- Thread đích có thể hoàn tất trước khi gọi `join()`; lúc đó `join()` có thể trở về ngay.

### `join()` và `sleep()` khác nhau

`Thread.sleep(100)` yêu cầu **thread hiện tại** tạm nghỉ khoảng thời gian được chỉ định, không quan tâm worker đã xong chưa. `worker.join()` chờ một **điều kiện cụ thể là worker kết thúc**. Vì thế, dùng `sleep()` để “đợi worker đủ lâu rồi lấy kết quả” là sai về mặt đồng bộ: worker có thể nhanh hơn hoặc chậm hơn dự tính.

## 2. Ví dụ Java 17+: bảo đảm thứ tự và đọc kết quả sau `join()`

Tạo tệp `JoinBasic.java`:

```java
public class JoinBasic {
    private static int result;

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            int sum = 0;
            for (int number = 1; number <= 5; number++) {
                sum += number;
            }

            result = sum;
            System.out.println("Worker finished");
        }, "calculator");

        worker.start();
        worker.join();

        System.out.println("Result: " + result);
        System.out.println("Worker alive: " + worker.isAlive());
    }
}
```

Biên dịch và chạy:

```bash
javac JoinBasic.java
java JoinBasic
```

Đầu ra khi thực thi thành công:

```text
Worker finished
Result: 15
Worker alive: false
```

**Diễn giải chính xác:**

1. `new Thread(...)` tạo đối tượng mô tả luồng; chưa có tác vụ nào được chạy bởi thread mới.
2. `worker.start()` khởi động thread, sau đó `main` và worker có thể tiến triển độc lập.
3. Worker cộng các số từ 1 đến 5, gán `result = 15` rồi in thông báo.
4. `worker.join()` không trả về bình thường trước khi worker thực sự kết thúc.
5. Sau `join()`, `main` đọc `result` và quan sát `isAlive() == false`. Vì thông báo được in trước khi worker kết thúc, thứ tự ba dòng trên là xác định trong chương trình ví dụ.

**Điểm liên quan tới Java Memory Model:** mọi hành động của worker **happens-before** việc thread khác phát hiện worker đã kết thúc thành công qua `join()`. Vì vậy, với ví dụ trên, sau `join()` trả về bình thường, `main` thấy giá trị `result = 15`; trường `result` không cần đánh dấu `volatile` chỉ để truyền kết quả ở ranh giới kết thúc này. Điều đó **không** biến mọi truy cập đồng thời vào biến chia sẻ trước khi worker kết thúc thành an toàn, cũng không bảo đảm đúng nếu `join()` bị ngắt hoặc chỉ chờ có thời hạn mà worker vẫn chạy.

## 3. Các overload của `join()`

`Thread` trong Java 17 cung cấp các overload phổ biến:

| Phương thức | Ý nghĩa |
| --- | --- |
| `void join() throws InterruptedException` | Chờ thread đích kết thúc, không đặt thời hạn chờ |
| `void join(long millis) throws InterruptedException` | Chờ tối đa khoảng `millis` mili-giây nếu `millis > 0`; `millis == 0` là chờ vô hạn |
| `void join(long millis, int nanos) throws InterruptedException` | Chờ có giới hạn thời gian với phần mili-giây và nano-giây; phần nano được xử lý theo độ phân giải của triển khai |

Đối số thời gian không hợp lệ ném `IllegalArgumentException`: `millis < 0`, hoặc `nanos < 0` hay `nanos > 999_999`.

**Bẫy:** `join(0)` **không** phải “kiểm tra ngay, không chờ”. Nó có nghĩa chờ cho tới khi thread kết thúc, tương tự `join()`. Nếu cần biết thread còn sống mà không chặn, dùng `isAlive()` nhưng nhớ rằng trạng thái có thể thay đổi ngay sau lúc kiểm tra.

**Bẫy thời gian:** `join(100)` không cam kết trở về đúng sau 100 ms. Việc đánh thức và lập lịch tùy JVM/OS, nên thực tế có thể muộn hơn. Không sử dụng `join(long)` như bộ đếm thời gian chính xác. Với `join(long, int)`, độ phân giải và việc làm tròn phần nano phụ thuộc triển khai.

**Giá trị trả về:** cả ba overload đều trả về `void`. Với timed join, việc quay lại **không cho biết** worker đã xong hay hết thời gian chờ. Cần kiểm tra `worker.isAlive()` sau khi gọi, rồi quyết định tiếp tục chờ, hủy tác vụ hoặc ghi nhận trạng thái chưa hoàn tất.

## 4. Ví dụ Java 17+: `join(timeout)` và xử lý worker chưa hoàn tất

Tạo tệp `JoinTimeout.java`:

```java
public class JoinTimeout {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(200); // Mô phỏng một công việc mất thời gian
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "slow-worker");

        worker.start();
        worker.join(25); // Chờ nhiều nhất xấp xỉ 25 ms

        if (worker.isAlive()) {
            System.out.println("Worker may still be running");
            worker.join(); // Đợi kết thúc thật để dọn dẹp ví dụ
        }

        System.out.println("Worker terminated: " + !worker.isAlive());
    }
}
```

Chạy:

```bash
javac JoinTimeout.java
java JoinTimeout
```

Dòng cuối **luôn là** `Worker terminated: true` nếu chương trình hoàn tất bình thường. Dòng `Worker may still be running` có thể có hoặc không, vì kết quả phụ thuộc việc lập lịch: worker có thể đã kết thúc trước khi `main` kiểm tra `isAlive()`. Không dùng thời gian sleep trong ví dụ này để kết luận một ngưỡng timeout thực tế cho production.

Mã trên chỉ cho thấy kỹ thuật phát hiện trạng thái sau timed join. Với task không có giới hạn hoàn thành, `join()` lần thứ hai **có thể chờ vô hạn**. Trong ứng dụng thật, cần có chính sách rõ ràng: trả kết quả “đang xử lý”, đặt hạn chót, gửi tín hiệu hủy hợp tác, hoặc tiếp tục theo dõi từ thành phần quản lý vòng đời. `join(timeout)` **không tự hủy** thread đích khi hết hạn.

## 5. InterruptedException: người đang chờ bị ngắt

Các overload của `join()` khai báo `throws InterruptedException`. Nếu thread **đang chờ trong `join()`** bị interrupt, lệnh chờ có thể ném `InterruptedException`. Đây là tín hiệu yêu cầu ngừng chờ hoặc dừng công việc theo giao thức của chương trình; nó **không đồng nghĩa** thread đích đã kết thúc, cũng không tự truyền interrupt sang worker.

Hai hướng xử lý thường gặp:

- **Cho phép truyền lỗi ra ngoài:** phương thức khai báo `throws InterruptedException` để caller có quyền quyết định cách kết thúc, dọn dẹp và khôi phục.
- **Bắt lỗi ở tầng không thể truyền tiếp:** dọn dẹp cần thiết và thường gọi lại `Thread.currentThread().interrupt()` để giữ tín hiệu interrupt cho tầng trên. Khi `InterruptedException` được ném từ thao tác chờ, interrupt status của thread hiện tại thường đã bị xóa; vì vậy khôi phục là bước quan trọng nếu bạn không truyền ngoại lệ tiếp.

Ví dụ về cách khôi phục cờ interrupt trong một phương thức không khai báo `throws`:

```java
static boolean waitFor(Thread worker) {
    try {
        worker.join();
        return true;
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
    }
}
```

Đoạn này là **phương thức minh họa** dùng trong một class khác; biến `worker` là thread cần chờ. Giá trị `false` có nghĩa **không hoàn tất việc chờ**; không được suy ra worker đã thất bại hoặc đã kết thúc. Caller phải quyết định ứng xử tiếp theo theo chính sách dừng/hủy của ứng dụng.

**Phân biệt hai hành động:**

- `waitingThread.interrupt()`: yêu cầu ngắt thread đang nằm trong `join()`; có thể làm phát sinh `InterruptedException` ở thread đó.
- `worker.interrupt()`: gửi tín hiệu interrupt tới worker; worker phải tự phản ứng thích hợp, chẳng hạn xử lý `InterruptedException` hoặc kiểm tra cờ interrupt. Không bảo đảm worker sẽ kết thúc ngay, đặc biệt với tác vụ bỏ qua interrupt.

Không dùng `Thread.stop()` để cưỡng ép kết thúc worker: API này đã lỗi thời và không an toàn.

## 6. `join()` có bảo đảm tác vụ thành công không?

**Không.** `join()` chỉ xác nhận thread đã chấm dứt khi trả về bình thường không có timeout. Worker có thể kết thúc vì chạy xong hoặc vì lỗi không được xử lý. Ngoại lệ phát sinh trong worker **không tự được ném sang** thread đang gọi `join()`. Do đó, `join()` không thay thế cơ chế thông báo kết quả hoặc lỗi.

Nếu dùng `Thread` trực tiếp, có thể ghi kết quả và lỗi trong nơi lưu trữ được thiết kế rõ ràng. Ví dụ sau bắt lỗi nghiệp vụ trong worker và đọc sau `join()`:

Tạo tệp `JoinFailure.java`:

```java
import java.util.concurrent.atomic.AtomicReference;

public class JoinFailure {
    public static void main(String[] args) throws InterruptedException {
        AtomicReference<String> error = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            try {
                throw new IllegalArgumentException("invalid input");
            } catch (IllegalArgumentException e) {
                error.set(e.getMessage());
            }
        });

        worker.start();
        worker.join();

        if (error.get() != null) {
            System.out.println("Task failed: " + error.get());
        }
    }
}
```

Chạy:

```bash
javac JoinFailure.java
java JoinFailure
```

Đầu ra:

```text
Task failed: invalid input
```

`AtomicReference` ở đây cung cấp nơi lưu trạng thái lỗi rõ nghĩa và an toàn khi dùng giữa các thread. Với truyền dữ liệu **chỉ sau khi `join()` thành công**, quan hệ happens-before của `join()` đã đủ cho visibility; tuy nhiên trong bài toán phức tạp hơn, `Future`/`CompletableFuture` thường diễn đạt kết quả, ngoại lệ, timeout và hủy task trực tiếp hơn.

## 7. Các trường hợp dễ gây treo hoặc chờ sai

### 7.1. Thread tự `join()` chính mình

```java
Thread.currentThread().join(); // Nguy hiểm: đợi chính thread đang chạy kết thúc
```

Thread chỉ kết thúc khi mã của chính nó kết thúc, nhưng nó lại chờ chính mình: có thể mắc kẹt vô hạn trừ khi có interrupt phù hợp. Không dùng self-join.

### 7.2. Hai thread chờ nhau

Nếu thread A gọi `b.join()` còn B gọi `a.join()` khi cả hai đang hoạt động, cả hai có thể chờ vô thời hạn. Đây là chu trình phụ thuộc tương tự deadlock, dù không nhất thiết xảy ra trên monitor do lập trình viên tự khóa.

Quy tắc thiết kế: xây dựng **đồ thị chờ không có vòng**. Thường một thread điều phối chờ các worker; worker không nên chờ ngược thread điều phối trong lúc thread điều phối đang chờ worker đó.

### 7.3. Giữ một lock mà worker cũng cần

Nếu thread gọi giữ một monitor/lock rồi `join()` worker, trong khi worker cần đúng lock đó để hoàn tất, chương trình có thể bế tắc: caller giữ lock để chờ worker, worker chờ lock để kết thúc. Hãy tách vùng giữ lock khỏi thao tác chờ và kiểm tra thứ tự phụ thuộc khóa.

Ví dụ cần tránh về mặt ý tưởng:

```java
synchronized (sharedLock) {
    worker.join(); // Nguy hiểm nếu worker phải lấy sharedLock để thoát
}
```

Đây là mã minh họa khi `worker` và `sharedLock` đã được khai báo. Không nên kết luận mọi `join()` nằm trong `synchronized` đều deadlock; lỗi xuất hiện khi có đường phụ thuộc ngược.

### 7.4. Gọi `join()` trên thread chưa `start()`

Một đối tượng `Thread` còn ở trạng thái `NEW` chưa thực thi `run()`; gọi `join()` không khiến tác vụ bắt đầu. Vì thread đó chưa `isAlive()`, `join()` có thể trả về ngay. Vì vậy phải gọi `start()` đúng lúc trước khi chờ, trừ khi chủ ý kiểm tra một thread đã kết thúc.

### 7.5. Coi timed join trả về là hoàn tất

`worker.join(100)` không cam kết worker đã xong. Nếu cần kết quả hoàn chỉnh, kiểm tra `!worker.isAlive()` sau lúc chờ và chỉ đọc kết quả phụ thuộc hoàn tất khi điều kiện đó được thỏa mãn. Nếu thread vẫn chạy, không dùng dữ liệu mutable chưa được đồng bộ như thể nó đã hoàn thành.

### 7.6. Chờ vô hạn trên một công việc có thể treo

`join()` không có timeout khiến thread gọi chờ cho đến khi thread đích kết thúc hoặc bị interrupt. Nếu worker kẹt I/O, deadlock hoặc vòng lặp không dừng, người chờ cũng có thể kẹt. Hãy xem xét timeout/cancellation ở cấp kiến trúc, đặc biệt trên đường xử lý cần đáp ứng hạn chót.

## 8. `join()` và các cách chờ khác trong Java

| Cách chờ | Khi nào phù hợp | Điều cần nhớ |
| --- | --- | --- |
| `Thread.join()` | Cần đợi **một thread cụ thể** kết thúc | Chỉ cho biết hoàn tất vòng đời thread, không trả kết quả hoặc lỗi task |
| `Thread.join(timeout)` | Muốn dành thời gian có giới hạn để đợi thread kết thúc | Trả về `void`; phải kiểm tra còn sống hay không |
| `Future.get()` | Task được submit qua `ExecutorService`, cần kết quả và lỗi task | Có thể ném `ExecutionException`; có overload timeout |
| `CompletableFuture.join()` | Pipeline bất đồng bộ có `CompletableFuture` | Có thể ném `CompletionException` unchecked; tên `join` nhưng ngữ nghĩa khác `Thread.join` |
| `CountDownLatch.await()` | Một hoặc nhiều thread chờ **mốc công việc/sự kiện** | Không gắn trực tiếp với việc thread nào kết thúc; cần countdown đúng |
| `Thread.sleep()` | Cần tạm nghỉ thread hiện tại | Không xác nhận một công việc khác đã hoàn tất |

Các lựa chọn này là API Java tiêu chuẩn; không cần đưa nội dung framework vào bài học về `Thread.join()`.

## 9. Ghi nhớ và checklist thực hành

1. Tạo `Thread`, gọi `start()`, rồi gọi `join()` từ thread **cần chờ**.
2. `join()` đợi vòng đời thread, **không** bắt đầu thread và **không** chuyển tự động kết quả/ngoại lệ.
3. Một `join()` thành công sau khi thread đích kết thúc tạo quan hệ **happens-before**, giúp thấy các ghi của worker trước khi kết thúc.
4. `join(0)` là chờ vô hạn; với `join(timeout)` luôn kiểm tra `isAlive()` trước khi giả định công việc đã xong.
5. Có thể bị `InterruptedException`; truyền lỗi lên trên hoặc khôi phục interrupt status nếu bắt và không truyền.
6. Tránh self-join, vòng A → B → A và chờ khi đang giữ lock mà worker cần.
7. Nếu bài toán đã có thread pool, kết quả, lỗi, timeout hoặc nhiều task cần phối hợp, cân nhắc `Future` và các abstraction concurrent phù hợp thay vì tạo/chờ thread thủ công.

**Kết luận:** `Thread.join()` là primitive đơn giản và quan trọng để một thread chờ thread khác hoàn tất, đồng thời thiết lập quan hệ đồng bộ cần thiết với các ghi trước khi thread đích kết thúc. Sử dụng nó đúng thời điểm, xử lý interrupt và xác minh trạng thái khi có timeout sẽ tránh phần lớn lỗi thường gặp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-api-012">Phân biệt công việc của thread và vòng đời task</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt công việc của thread và vòng đời task trong Java

> **Thông điệp chính:** **Thread** là luồng thực thi; **task** là một đơn vị công việc. Thread có vòng đời và trạng thái riêng của luồng thực thi. Task có tiến trình tiếp nhận, chạy, hoàn thành, thất bại hoặc hủy riêng. Hai vòng đời có thể liên quan nhưng không đồng nhất, đặc biệt khi dùng executor, thread pool và virtual thread.

Bài này chỉ xét **Java concurrency nói chung** với `Thread`, `Runnable`, `Callable` và `java.util.concurrent`; không bàn đến framework Spring hoặc vòng đời component do Spring quản lý.

## 1. Vì sao cần phân biệt?

Một thread giống **người thực hiện công việc**; một task giống **phiếu công việc**. Một người có thể làm nhiều phiếu lần lượt; phiếu chưa được nhận vẫn nằm trong hàng đợi; hoàn thành một phiếu không có nghĩa người đó rời khỏi hệ thống. Một phiếu bị hủy trước khi nhận không nhất thiết từng có ai thực thi nó.

| Khái niệm | Câu hỏi chính | Đại diện trong Java |
| --- | --- | --- |
| **Thread** | Mã đang chạy trên luồng nào? Luồng đã khởi động, đang chờ hay đã kết thúc? | `Thread`, `Thread.currentThread()`, `Thread.State` |
| **Task** | Công việc gì cần làm? Một lần xử lý đã bắt đầu, thành công, lỗi hoặc bị hủy chưa? | `Runnable`, `Callable<V>` |
| **Task handle** | Làm sao đợi và kiểm tra kết quả của một công việc bất đồng bộ? | `Future<V>`, `FutureTask<V>`, `CompletableFuture<V>` |
| **Executor** | Ai tiếp nhận, xếp hàng và bố trí thực thi các task? | `Executor`, `ExecutorService`, thread pool |

Không có quy tắc **mỗi task nhất thiết có một platform thread riêng**. Trong thread pool, một worker thread có thể chạy nhiều task nối tiếp. Một task nằm trong queue có thể chưa có thread nào bắt đầu thực thi. Một đối tượng `Runnable` có thể được dùng ở nhiều lần chạy, kể cả chạy đồng thời trên các thread khác nhau; vì vậy nếu task giữ trạng thái mutable chung phải đồng bộ đúng cách.

## 2. `Runnable` và `Callable` mô tả công việc

`Runnable` có phương thức `void run()`, không trả giá trị, không khai báo checked exception từ chữ ký `run()`:

~~~java
Runnable print = () -> System.out.println("Hello from task");
~~~

Dòng này chỉ tạo **mô tả công việc**. Nó chưa tạo thread và chưa thực thi `print.run()`. Gọi trực tiếp `print.run()` đồng nghĩa gọi phương thức bình thường trên **thread hiện tại**.

`Callable<V>` có phương thức `V call() throws Exception` nên thích hợp khi task có kết quả hoặc cần chuyển lỗi cho bên nhận:

~~~java
import java.util.concurrent.Callable;

Callable<Integer> calculate = () -> 6 * 7;
~~~

`Runnable` và `Callable` không tự quyết định thread nào chạy mình. Chúng có thể được thực thi bằng thread trực tiếp hoặc gửi tới một executor. Một lần gửi task thường là một **lần thực thi công việc**, không đồng nhất với identity của đối tượng lambda dùng làm task.

Ngược lại, đối tượng `Thread` biểu diễn một luồng thực thi:

~~~java
Runnable work = () -> System.out.println("working");
Thread worker = new Thread(work, "worker");
worker.start();
~~~

`new Thread(work)` tạo thread ở trạng thái `NEW`. `start()` yêu cầu JVM bắt đầu luồng thực thi riêng và sau đó thực thi `work.run()` trên luồng ấy. Một **đối tượng Thread chỉ được `start()` một lần**; start lần thứ hai ném `IllegalThreadStateException`. Muốn làm lại việc đó, tạo một `Thread` mới hoặc gửi một task mới đến executor phù hợp.

## 3. Ví dụ 1: `run()` trực tiếp khác `start()`

Tạo tệp `RunVsStart.java` (Java 17+):

~~~java
public class RunVsStart {
    public static void main(String[] args) throws InterruptedException {
        Thread mainThread = Thread.currentThread();
        Runnable work = () -> System.out.println(
                "on main = " + (Thread.currentThread() == mainThread));

        Thread worker = new Thread(work, "worker");

        System.out.println("initial = " + worker.getState());
        worker.run(); // Gọi trực tiếp trên main.
        System.out.println("after direct run = " + worker.getState());

        worker.start(); // Khởi động một thread mới.
        worker.join();  // Chờ worker kết thúc.
        System.out.println("after join = " + worker.getState());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac RunVsStart.java
java RunVsStart
~~~

Đầu ra:

~~~text
initial = NEW
on main = true
after direct run = NEW
on main = false
after join = TERMINATED
~~~

Vì `worker.run()` chỉ là lời gọi phương thức, body `work` lần đầu chạy ngay trong main và `worker` vẫn `NEW`. Chỉ `worker.start()` mới cho phép body chạy trên một thread khác. `join()` khiến main đợi thread mới kết thúc nên dòng cuối quan sát `TERMINATED`. Bài học: **một lần thực thi code trong `run()` không đồng nghĩa khởi động đối tượng `Thread`**.

Trong cách dùng `new Thread(runnable)` này, một vòng đời thread gắn với một lần gọi `run()` khi `start()`. Đó là quan hệ của cách tạo thread trực tiếp, **không phải quy tắc cho mọi task**.

## 4. Vòng đời của thread: sáu `Thread.State`

`Thread.getState()` trả về một trạng thái tại thời điểm đọc. Các trạng thái do Java định nghĩa:

| Trạng thái | Ý nghĩa |
| --- | --- |
| `NEW` | Chưa khởi động bằng `start()` |
| `RUNNABLE` | Đang thực thi trong JVM hoặc ở trạng thái có thể được lập lịch |
| `BLOCKED` | Chờ giành **monitor lock** để vào/re-enter khối `synchronized` |
| `WAITING` | Chờ vô thời hạn cho hành động từ thread khác, ví dụ `Object.wait()`, `join()` không timeout hoặc `LockSupport.park()` |
| `TIMED_WAITING` | Chờ với giới hạn thời gian, ví dụ `sleep`, `wait(timeout)`, `join(timeout)` |
| `TERMINATED` | Luồng đã kết thúc vì `run()` trả về hoặc thoát bởi lỗi không được bắt |

Sơ đồ đơn giản:

~~~text
NEW -- start() --> RUNNABLE <----> BLOCKED / WAITING / TIMED_WAITING
                         |
                         +-- run() kết thúc --> TERMINATED
~~~

Sơ đồ trên chỉ gợi nhớ; trạng thái chờ có thể chuyển qua lại nhiều lần và chi tiết phụ thuộc hoạt động của thread.

- `RUNNABLE` **không** chứng minh thread đang sử dụng CPU ngay lúc đó; nó có thể đang chờ được hệ điều hành lập lịch.
- `BLOCKED` là trạng thái **chờ monitor**, không phải tên chung cho mọi thao tác blocking như I/O hay queue.
- `WAITING` không mặc định là lỗi; worker trong pool có thể đang đợi task tiếp theo một cách hợp lệ.
- `getState()` chỉ là snapshot; kiểm tra state rồi hành động dựa trên giả định state không đổi là lỗi race condition.
- `TERMINATED` thuộc về **thread cụ thể ấy**. Task hoàn tất trong pool không đòi hỏi thread pool worker phải kết thúc.

Không dùng `Thread.sleep()` hoặc vòng lặp polling `getState()` làm công cụ điều phối thứ tự hai tác vụ. Sử dụng các primitive có hợp đồng đồng bộ như `join`, `Future.get`, `CountDownLatch`, `BlockingQueue` hoặc lock/condition khi cần thiết.

## 5. Vòng đời task: mô hình công việc, không phải `Thread.State`

Task có thể trải qua các giai đoạn logic:

~~~text
CREATED -> SUBMITTED -> QUEUED -> RUNNING -> SUCCEEDED
                            |         \----> FAILED
                            +---------------> CANCELLED
                                      \-----> CANCELLED (yêu cầu hủy lúc chạy)
~~~

Đây là **mô hình khái niệm**, không phải enum bắt buộc có sẵn trên mọi `Runnable`/`Callable`. Task chưa được gửi thì chưa có executor nào nhận; task có thể bị `RejectedExecutionException` khi executor không nhận công việc; một số task được thực thi trực tiếp mà không xếp queue; hủy trong lúc chạy không đồng nghĩa body đã dừng ngay.

Bảng đối chiếu hai trục:

| Tình huống | Thread | Task |
| --- | --- | --- |
| Vừa `new Thread(work)` | `NEW` | `work` chưa chạy trên thread ấy |
| Task nằm trong pool queue | Worker đang chạy task khác hoặc đợi việc | Task này **chưa bắt đầu** |
| Task đang gọi I/O hoặc đợi lock | Thread có thể `RUNNABLE`/`WAITING`/`BLOCKED`... tùy API | Task **đang thực thi**, nhưng chưa xong |
| Task vừa kết thúc trong thread pool | Worker có thể vẫn sống để nhận task khác | Task **completed** |
| Task ném exception trong `submit(Callable)` | Worker có thể tiếp tục phục vụ công việc | Future của task **done do failure** |
| Task hủy trước khi bắt đầu | Không có worker nào cần chạy body | Future có thể **done do cancellation** |

Một task đang đợi trong **executor queue** không có `Thread.State.WAITING` riêng, vì task chưa phải là thread. Cũng không thể nhìn worker ở `WAITING` rồi kết luận task nào đã hoàn thành. Cần theo dõi kết quả của task trực tiếp.

## 6. Executor tách nơi gửi công việc khỏi nơi chạy

`ExecutorService` có thể nhận `Runnable` hoặc `Callable`:

~~~java
// Minh họa API, chưa phải chương trình độc lập.
ExecutorService executor = Executors.newFixedThreadPool(2);
Future<Integer> answer = executor.submit(() -> 42);
int value = answer.get();
executor.shutdown();
~~~

Với thread pool hai worker, nhiều task có thể cùng xếp hàng; executor chủ động bố trí công việc lên worker có sẵn. Không đồng nghĩa có một thread riêng cho mỗi lần `submit`. `Future<Integer>` theo dõi kết quả **task**, không theo dõi trạng thái sống/chết của một worker.

`execute(Runnable)` gửi công việc mà không trả `Future`. `submit(...)` trả một `Future`; điều này hữu ích để đợi kết quả, phát hiện lỗi, hoặc yêu cầu hủy. Nếu `submit(Runnable)` mà không cung cấp result riêng, `Future.get()` sẽ trả `null` nếu thành công.

### Ví dụ 2: hai task, một worker vẫn sống sau khi task hoàn thành

Tạo `TwoTasksOneWorker.java` (Java 17+):

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TwoTasksOneWorker {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Thread> first =
                    executor.submit(() -> Thread.currentThread());
            Future<Thread> second =
                    executor.submit(() -> Thread.currentThread());

            Thread firstWorker = first.get();
            Thread secondWorker = second.get();

            System.out.println("same worker = "
                    + (firstWorker == secondWorker));
            System.out.println("first done = " + first.isDone());
            System.out.println("second done = " + second.isDone());
            System.out.println("worker alive = " + secondWorker.isAlive());
        } finally {
            executor.shutdown();
        }
    }
}
~~~

Chạy:

~~~bash
javac TwoTasksOneWorker.java
java TwoTasksOneWorker
~~~

Đầu ra trong điều kiện worker hoạt động bình thường của ví dụ:

~~~text
same worker = true
first done = true
second done = true
worker alive = true
~~~

Hai task được xử lý tuần tự trên cùng một worker của `newSingleThreadExecutor()`. Các `get()` đã xác nhận **cả hai task xong**, trong khi worker vẫn còn sống do `shutdown()` chỉ được gọi về sau trong `finally`. Nếu worker gặp lỗi hỏng bất thường, executor có thể tạo worker thay thế; ví dụ này không phải bảo đảm identity worker vĩnh viễn.

Đây là khác biệt thực dụng: một worker thread có thể sống lâu hơn rất nhiều lần thực thi task mà nó nhận.

### Ví dụ 3: task chưa chạy vì đang xếp hàng

Tạo `QueuedTask.java` (Java 17+):

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class QueuedTask {
    public static void main(String[] args) throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<String> first = executor.submit(() -> {
                gate.await();
                return "first finished";
            });
            Future<String> second =
                    executor.submit(() -> "second finished");

            System.out.println("second done before release = "
                    + second.isDone());

            gate.countDown();
            System.out.println(first.get());
            System.out.println(second.get());
        } finally {
            gate.countDown();
            executor.shutdown();
        }
    }
}
~~~

Chạy:

~~~bash
javac QueuedTask.java
java QueuedTask
~~~

Đầu ra:

~~~text
second done before release = false
first finished
second finished
~~~

Trong executor một worker, task thứ nhất phải đợi `gate` nên task thứ hai không thể được worker thực thi trước khi main phát tín hiệu. `second.isDone()` chắc chắn là `false` ở phép kiểm tra đầu tiên. Không có thread khác đang chạy body của task thứ hai lúc ấy. Sau `countDown()`, hai `Future.get()` đợi lần lượt và lấy kết quả.

`CountDownLatch` ở đây cho quan hệ điều phối rõ ràng; dùng `Thread.sleep` để tạo một timing tình cờ sẽ khiến ví dụ phụ thuộc máy/tải.

## 7. `Future` theo dõi hoàn thành task ra sao?

`Future<V>` mô tả kết quả của một phép tính bất đồng bộ. Trong Java 17:

| API | Ý nghĩa chính |
| --- | --- |
| `get()` | Đợi nếu cần, rồi trả result; hoặc ném `ExecutionException`/`CancellationException`, hay `InterruptedException` nếu bên đợi bị ngắt |
| `get(timeout, unit)` | Đợi tối đa một khoảng; có thể ném `TimeoutException`, **không tự hủy task** |
| `isDone()` | `true` khi phép tính đã thành công, lỗi **hoặc bị hủy** |
| `isCancelled()` | `true` khi task handle đã bị hủy trước khi hoàn tất |
| `cancel(false)` | Yêu cầu hủy, không yêu cầu interrupt công việc đang chạy |
| `cancel(true)` | Yêu cầu hủy và thử interrupt thread đang thực thi nếu có thể |

**`isDone() == true` không có nghĩa “đã thành công”**. Một task có exception vẫn done. Một task bị hủy trước khi bắt đầu cũng done. Với `get()`, caller phân biệt kết quả thường, `ExecutionException` có cause từ task, và `CancellationException`.

Từ Java 19, `Future` có API bổ sung như `state()` với các trạng thái `RUNNING`, `SUCCESS`, `FAILED`, `CANCELLED`. Trong ngữ cảnh `Future.State`, `RUNNING` có thể bao gồm phép tính **chưa hoàn tất nhưng vẫn đang xếp hàng**; đừng đọc tên trạng thái đó thành “hiện đang chiếm CPU”. Ví dụ Java 17 nên dùng `get/isDone/isCancelled` nếu muốn chạy với baseline Java 17.

### Ví dụ 4: task thất bại, Future vẫn done

Tạo `TaskFailure.java`:

~~~java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TaskFailure {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> future = executor.submit(() -> {
                throw new IllegalStateException("failed");
            });

            try {
                future.get();
            } catch (ExecutionException ex) {
                System.out.println("cause = "
                        + ex.getCause().getClass().getSimpleName());
            }

            System.out.println("done = " + future.isDone());
            System.out.println("cancelled = " + future.isCancelled());
        } finally {
            executor.shutdown();
        }
    }
}
~~~

Chạy `javac TaskFailure.java` và `java TaskFailure`:

~~~text
cause = IllegalStateException
done = true
cancelled = false
~~~

`Future.get()` cho biết task bị lỗi và cung cấp nguyên nhân. Khi chạy `Runnable` bằng `Thread` tạo trực tiếp, `Thread.join()` **chỉ đợi thread kết thúc**, không tự trả giá trị hay ném lại exception từ thread khác. Nếu cần theo dõi uncaught exception của raw thread, dùng `Thread.UncaughtExceptionHandler` hoặc thiết kế kênh thông báo lỗi phù hợp. `submit(Callable)` thường tiện hơn cho tác vụ cần result/failure.

### Ví dụ 5: hủy một task chưa từng chạy

Tạo `CancelBeforeRun.java`:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.FutureTask;

public class CancelBeforeRun {
    public static void main(String[] args) throws Exception {
        FutureTask<Integer> task = new FutureTask<>(() -> 42);

        System.out.println("cancel accepted = " + task.cancel(false));
        System.out.println("done = " + task.isDone());
        System.out.println("cancelled = " + task.isCancelled());

        try {
            task.get();
        } catch (CancellationException expected) {
            System.out.println("result unavailable");
        }
    }
}
~~~

Chạy `javac CancelBeforeRun.java` và `java CancelBeforeRun`:

~~~text
cancel accepted = true
done = true
cancelled = true
result unavailable
~~~

Trong ví dụ không hề có thread thực thi body `() -> 42`. `FutureTask.cancel(false)` thành công vẫn khiến task handle `isDone()` trả `true`. Một phép tính được đánh dấu hoàn tất vì **hủy**, khác xa “thread hoàn tất phần việc”.

## 8. Hủy task là hợp tác; không đồng nghĩa kết thúc thread

Nếu task đã bắt đầu, `future.cancel(true)` có thể thử interrupt thread đang thực thi. Cần nhớ:

1. `cancel(true)` báo một **yêu cầu**, không bảo đảm buộc code dừng ngay.
2. Task có thể bỏ qua cờ interrupt, đang xử lý I/O không phản hồi interrupt, hoặc chủ động hoàn tất theo cách riêng.
3. `Future` có thể đã báo canceled/done **trước khi body task thực sự thoát**.
4. Thread trong pool **không nên bị hiểu là tài sản của một task**; sau task kết thúc, worker có thể phục vụ task tiếp theo.
5. Tránh `Thread.stop()`: đây là API cũ nguy hiểm. Tác vụ dài cần cơ chế hủy hợp tác và giải phóng tài nguyên trong `finally`.

Ví dụ xử lý interruption cho một task có thể chờ:

~~~java
// Chỉ minh họa body task; không phải chương trình độc lập.
try {
    while (!Thread.currentThread().isInterrupted()) {
        doOneUnitOfWork();
    }
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    // Trả quyền quyết định cho tầng bên ngoài và thoát tác vụ.
} finally {
    releaseOwnedResources();
}
~~~

Trong code thực tế, tên các hàm `doOneUnitOfWork` và `releaseOwnedResources` phải được triển khai; đoạn trên minh họa **ngữ nghĩa interruption**. Nếu body dùng phương thức ném `InterruptedException` thì có thể thoát, xử lý dọn dẹp hoặc khôi phục cờ interrupt tùy hợp đồng. Không nuốt ngắt rồi tiếp tục vòng lặp vô hạn ngoài ý muốn.

## 9. `Thread.join()`, `Future.get()`, `awaitTermination()` khác nhau

| API | Đối tượng được chờ | Kết quả và lỗi |
| --- | --- | --- |
| `Thread.join()` | **Một thread** kết thúc | Không trả giá trị task; không chuyển exception trong thread sang caller |
| `Future.get()` | **Một phép tính** kết thúc/có kết quả | Có result hoặc lỗi/hủy tương ứng |
| `ExecutorService.awaitTermination(...)` | **Executor** dừng sau shutdown | Boolean về việc executor đã kết thúc trong thời hạn hay chưa |

`join()` và `Future.get()` đều có hợp đồng memory visibility: các hành động của thread kết thúc xảy ra trước các hành động sau `join()` thành công; hành động của phép tính bất đồng bộ xảy ra trước hành động theo sau `Future.get()` tương ứng ở thread khác. Vì vậy việc nhận kết quả sau các cơ chế này an toàn hơn việc polling thread state hoặc chờ một thời gian đoán trước.

`CompletableFuture.join()` cũng có tên `join` nhưng **không phải** `Thread.join()`: nó chờ và lấy kết quả của một completion stage, với ngữ nghĩa exception riêng. Phải xác định đúng đối tượng trước khi nói “join là đợi luồng”.

## 10. Vòng đời executor là trục thứ ba

Bên cạnh thread và task, executor còn có trạng thái hoạt động riêng:

- `shutdown()` bắt đầu đóng theo cách trật tự; không nhận task mới, nhưng tiếp tục xử lý task đã được nhận và **trả về ngay** (không tự chờ hoàn tất).
- `awaitTermination(timeout, unit)` đợi executor kết thúc sau khi có yêu cầu đóng; trả boolean, không cung cấp kết quả của từng task.
- `shutdownNow()` cố dừng task đang chạy bằng interruption và trả các task trong queue chưa bắt đầu. Đây là **best effort**: task có thể không phản hồi ngắt ngay.
- `isShutdown()` nói đã yêu cầu đóng, còn `isTerminated()` nói executor đã kết thúc sau shutdown. Một `Future` hoàn thành không tự làm executor chuyển sang terminated.

Mẫu đóng executor Java 17:

~~~java
// Minh họa với biến executor đã được tạo trước đó.
executor.shutdown();
try {
    if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
        executor.shutdownNow();
    }
} catch (InterruptedException ex) {
    executor.shutdownNow();
    Thread.currentThread().interrupt();
}
~~~

Timeout 5 giây là thời gian chờ của caller, **không bảo đảm** mọi task dừng trong 5 giây. Thiết kế production cần xem xét timeout, backpressure, queue capacity, số worker, failure reporting và shutdown policy riêng.

## 11. Virtual thread trong Java 21+ làm rõ thêm sự khác biệt

Virtual thread vẫn là đối tượng `Thread` nhưng được JVM lập lịch trên carrier platform thread, không tương đương một OS thread mới cho từng task. Java 21 có executor tạo **một virtual thread cho mỗi task**:

~~~java
// VirtualPerTask.java — chỉ dành cho Java 21+
import java.util.concurrent.Executors;

public class VirtualPerTask {
    public static void main(String[] args) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(
                    () -> Thread.currentThread().isVirtual());
            System.out.println(future.get());
        }
    }
}
~~~

Chạy:

~~~bash
javac VirtualPerTask.java
java VirtualPerTask
~~~

Kết quả:

~~~text
true
~~~

Ở đây mỗi task được gán một **virtual thread mới**; các carrier platform thread có thể được runtime tái sử dụng. Virtual thread không xóa bỏ khái niệm task lifecycle và `Future`: task vẫn có thể hoàn thành, lỗi hay bị hủy; thread vẫn có identity và trạng thái riêng. `ExecutorService.close()`/try-with-resources cần JDK 19+; ví dụ đã ghi rõ Java 21, **không áp dụng cú pháp này cho Java 17**.

Điểm khác nhau giữa “mỗi task một virtual thread” và “mỗi task một platform thread” là cực kỳ quan trọng khi xét tài nguyên, lập lịch và khả năng mở rộng.

## 12. Chọn API theo câu hỏi cần trả lời

| Câu hỏi | Công cụ phù hợp | Sai lầm cần tránh |
| --- | --- | --- |
| Công việc có logic gì? | `Runnable`/`Callable` | Trộn logic công việc với thread identity |
| Muốn khởi động một raw thread? | `new Thread(work).start()` | Gọi `run()` rồi tưởng có concurrency |
| Task đã thành công hay thất bại? | `Future.get()`/completion-specific API | Chỉ nhìn `isDone()` hay `Thread.State` |
| Task nào đang chờ trong queue? | Executor/queue metrics, task ID | Đọc `Thread.State.WAITING` rồi gán cho task |
| Hai việc phải có thứ tự? | `join`, `Future.get`, `CountDownLatch`, `BlockingQueue` | `sleep()`, priority, suy đoán scheduling |
| Cần yêu cầu dừng task? | `cancel(...)`, `interrupt()`, logic dừng hợp tác | Cho rằng cancel cưỡng bức đã kết thúc code |
| Muốn executor nhận thêm công việc? | Kiểm tra shutdown policy, handle rejection | Nhìn một worker còn `RUNNABLE` |
| Cần đợi executor dừng? | `shutdown` + `awaitTermination` | Chỉ đợi một task Future |
| Muốn debug thread đang chờ gì? | Thread dump, stack, `Thread.getState()` | Đồng nhất thread state với business task state |

### Ba tình huống để tự phân tích

**Tình huống A:** Một pool có 4 worker và 1000 task đã được nhận. Có 1000 thread không? **Không.** Nhiều task có thể đang queue, chỉ một số được worker thực thi. Tùy chính sách executor, các task có thể còn chờ lâu.

**Tình huống B:** `Future.isDone() == true`, nhưng task bị hủy trước khi chạy. Có nhất thiết thread nào đã thực thi body task không? **Không.** Cờ done phản ánh trạng thái task handle, gồm cả cancellation.

**Tình huống C:** Một worker đang `WAITING`, còn `Future` của task trước đó đã thành công. Mâu thuẫn không? **Không.** Worker có thể chờ việc tiếp theo sau khi task trước hoàn tất.

## 13. Lỗi hiểu thường gặp

1. **“Task tạo rồi nghĩa là đang chạy.”** Sai: tạo object công việc khác với gửi, được nhận, được xếp lịch và thực thi.
2. **“`run()` và `start()` tương đương.”** Sai: gọi `run()` trực tiếp không tạo thread mới.
3. **“Một task hoàn thành thì thread của nó bị hủy.”** Sai với thread pool: worker thường được tái sử dụng.
4. **“`Thread.State.TERMINATED` cho biết Future thành công.”** Không: thread kết thúc không chuyển trạng thái thành công của một task tương ứng, đặc biệt nếu nhiều task được quản lý.
5. **“`isDone()` có nghĩa result thành công.”** Không: lỗi và hủy cũng done.
6. **“`cancel(true)` bảo đảm kill task ngay.”** Không: đây là nỗ lực phối hợp bằng interruption.
7. **“`Future.get(timeout)` tự hủy công việc khi timeout.”** Không: nó chỉ ngừng chờ và ném `TimeoutException`.
8. **“`shutdown()` xong là mọi worker/task đã dừng.”** Không: shutdown khởi phát đóng, còn termination là giai đoạn sau.
9. **“RUNNABLE chắc chắn đang dùng CPU.”** Không: trạng thái JVM không chứng minh lịch OS cấp CPU ở khoảnh khắc quan sát.
10. **“Virtual-thread-per-task nghĩa là một OS thread cho từng task.”** Không: virtual thread được JVM lập lịch trên các carrier thread.

## Nguồn API chính thức

- [Java SE 21 — `Thread`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html), [`Thread.State`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.State.html).
- [Java SE 21 — `Runnable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Runnable.html), [`Callable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Callable.html).
- [Java SE 21 — `Future`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html), [`FutureTask`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/FutureTask.html).
- [Java SE 21 — `ExecutorService`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html), [`Executors`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html).
- [Java Language Specification `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html): nền tảng đồng bộ và *happens-before*.

**Ghi nhớ:** Khi hỏi **ai đang chạy/đang đợi**, quan sát thread. Khi hỏi **công việc đã xử lý đến đâu, thành công hay thất bại**, quan sát task và kết quả của nó. Khi hỏi **hệ thống có nhận/điều phối việc tiếp không**, quan sát executor.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-api-013">Thread.Builder: tạo platform thread, virtual thread và ThreadFactory theo cấu hình</a>

<details>
<summary>Xem chi tiết</summary>

# Thread.Builder: tạo platform thread, virtual thread và ThreadFactory theo cấu hình

## 1. Mục tiêu học tập và yêu cầu phiên bản

Thread.Builder là API chuẩn trong **Java 21+** giúp cấu hình và khởi tạo thread theo một cách thống nhất, dễ đọc: chọn **platform thread** hoặc **virtual thread**, đặt tên, quyết định có khởi động ngay không, và tạo ThreadFactory dùng lại cấu hình trong các executor.

Sau bài học, bạn có thể:

- Phân biệt Thread.ofPlatform() và Thread.ofVirtual(), cùng chi phí và trường hợp sử dụng của chúng.
- Dùng start(Runnable), unstarted(Runnable), factory() đúng vòng đời; biết chính xác lúc nào thread được khởi động.
- Cấu hình tên, kế thừa InheritableThreadLocal, uncaught exception handler và các thuộc tính riêng của platform thread.
- Cung cấp ThreadFactory cho ExecutorService mà không nhầm thread factory với chính sách lập lịch, giới hạn concurrency hoặc queue.
- Viết ứng dụng Java 21+ chạy được, chờ thread kết thúc đúng cách và xác định các giới hạn khi vận hành.

**Phạm vi:** Java SE/JVM concurrency, không bàn tới framework. **Điều kiện:** dùng JDK 21 hoặc mới hơn cho toàn bộ ví dụ; JDK 17 không hỗ trợ các API này. Virtual threads từng trải qua giai đoạn preview ở các bản JDK trước, nhưng phiên bản chính thức là Java 21.

## 2. Mô hình tư duy: builder → thread hoặc factory

Thread.Builder là một **interface**, có hai nhánh chuyên biệt:

~~~text
Thread.ofPlatform() → Thread.Builder.OfPlatform
                     ├─ start(task)     → platform thread đã start
                     ├─ unstarted(task) → platform thread ở NEW
                     └─ factory()      → ThreadFactory tạo platform thread

Thread.ofVirtual()  → Thread.Builder.OfVirtual
                     ├─ start(task)     → virtual thread đã start
                     ├─ unstarted(task) → virtual thread ở NEW
                     └─ factory()      → ThreadFactory tạo virtual thread
~~~

Thread.Builder không tự chạy một Runnable và không đại diện cho một thread đang sống. Nó là **bộ cấu hình để tạo ra các Thread riêng biệt**. Mỗi Thread có nhiệm vụ và vòng đời độc lập. Builder có thể cấu hình tên, handler và một số thuộc tính trước khi tạo Thread.

Ba cách kết thúc quá trình xây dựng:

| Lời gọi | Kết quả | Thread bắt đầu chạy chưa? | Sử dụng khi |
|---|---|---|---|
| builder.start(task) | Thread mới | Đã gọi start() để yêu cầu lịch chạy | Cần khởi tạo và chạy ngay. |
| builder.unstarted(task) | Thread mới | Chưa; trạng thái NEW | Muốn giữ tham chiếu, sắp xếp thứ tự khởi động rồi tự gọi start(). |
| builder.factory() | ThreadFactory | Chưa tạo thread nào chỉ bởi factory() | Muốn tái sử dụng cấu hình khi tạo nhiều thread hoặc đưa cho executor. |
| factory.newThread(task) | Thread mới chưa start | Chưa; caller hoặc executor sẽ start | Dùng factory theo hợp đồng java.util.concurrent.ThreadFactory. |

Ví dụ ngắn:

~~~java
Thread platform = Thread.ofPlatform()
        .name("report-worker")
        .unstarted(() -> System.out.println("work"));

System.out.println(platform.getState()); // NEW
platform.start();                         // mới bắt đầu chạy
platform.join();                          // đợi kết thúc
~~~

Trong ví dụ, chỉ lệnh xuất NEW được thực thi trước khi start() được gọi. Với code chạy trên nhiều thread, thứ tự các lệnh in giữa các thread có thể thay đổi: start() **không đồng nghĩa** Runnable đã hoàn tất trước khi lời gọi start() trả về. Để phụ thuộc vào kết quả, dùng join(), Future.get() hoặc cơ chế đồng bộ phù hợp.

## 3. Platform thread và virtual thread: cùng API, khác mô hình thực thi

**Platform thread** thường được JVM ánh xạ tới một native thread của hệ điều hành. Nó dùng native stack và scheduler hệ điều hành; số thread lớn có chi phí bộ nhớ, tạo/hủy và lập lịch.

**Virtual thread** được JVM lập lịch trên các platform carrier threads. Trong nhiều thao tác I/O chặn mà JDK hỗ trợ, virtual thread có thể tạm ngừng và nhường carrier cho việc khác. Nó thích hợp khi cần rất nhiều tác vụ đồng thời dành phần lớn thời gian **chờ I/O**. Virtual thread vẫn dùng Java Thread object, có stack logic và dữ liệu liên quan; không phải hoàn toàn miễn phí.

| Tiêu chí | Thread.ofPlatform() | Thread.ofVirtual() |
|---|---|---|
| Loại thread | Platform | Virtual |
| Gắn với native OS thread | Thông thường một platform thread có native thread tương ứng | Nhiều virtual threads chia sẻ các carrier platform threads theo lịch JVM |
| Chi phí số lượng lớn | Cao hơn do native thread/stack/scheduler | Thường thấp hơn cho nhiều tác vụ chờ, vẫn có giới hạn tài nguyên |
| Daemon | Tùy cấu hình hoặc thừa kế khi tạo | Luôn là daemon; không cấu hình được thành non-daemon |
| Priority tùy chỉnh | Có thể cấu hình, nhưng hiệu lực còn phụ thuộc JVM/OS | Priority cố định, không có API builder để chỉnh |
| ThreadGroup tùy chỉnh | Có thể cấu hình | Không có tùy chọn builder để gán group |
| Stack size gợi ý | Có thể dùng stackSize(long), phụ thuộc nền tảng | Không có stackSize trên OfVirtual |
| Trường hợp nổi bật | Công việc CPU có concurrency hữu hạn; pool worker có kiểm soát | Nhiều công việc blocking I/O cần mô hình mã tuần tự dễ đọc |

**Đừng đồng nhất virtual thread với chạy song song trên CPU.** Trên máy có P CPU hiệu dụng, hàng nghìn virtual threads không biến P thành hàng nghìn lõi. Với tác vụ CPU-bound dài, nhu cầu CPU vẫn là giới hạn và không nên mặc định virtual threads sẽ làm tác vụ nhanh hơn.

Thread.currentThread() trong mã chạy trên virtual thread trả về **virtual thread hiện tại**, không phải đối tượng carrier. Vì thế tên, ThreadLocal và trạng thái nên gắn với virtual thread logic của tác vụ, không suy đoán về một carrier cụ thể.

## 4. Cấu hình dùng chung trên hai builder

Cả OfPlatform và OfVirtual đều cung cấp các phương thức chung:

### 4.1. name(String) và name(String prefix, long start)

- name("job-runner") gán cùng một tên cố định cho mỗi Thread được builder tạo; tên **không bắt buộc duy nhất**.
- name("job-", 0) sinh tên lần lượt job-0, job-1, ... theo bộ đếm bắt đầu từ số không âm. Mỗi lần builder tạo Thread sẽ dùng và tăng bộ đếm.
- Khi gọi factory(), factory nhận trạng thái cấu hình và bản sao bộ đếm tại thời điểm tạo; các thread tạo bằng factory có chuỗi tên của factory đó. Không mặc định mọi factory có chung bộ đếm toàn ứng dụng.

Tên giúp phân biệt vai trò khi log, debug và theo dõi thread dump; nó không phải cơ chế tạo định danh bảo mật hoặc bảo đảm thứ tự thực thi.

### 4.2. inheritInheritableThreadLocals(boolean)

Điều khiển việc một thread mới **thừa hưởng giá trị ban đầu của InheritableThreadLocal** từ thread đang tạo nó. Giá trị mặc định là true; cấu hình false thường hữu ích khi muốn tránh vô tình mang ngữ cảnh của tác vụ cha sang thread con:

~~~java
ThreadFactory factory = Thread.ofVirtual()
        .name("isolated-", 0)
        .inheritInheritableThreadLocals(false)
        .factory();
~~~

Đây **không phải** công tắc tắt toàn bộ ThreadLocal. Tác vụ vẫn có thể tự đặt ThreadLocal; dữ liệu nên được xóa trong finally khi hết phạm vi sử dụng. Cũng không nên dùng InheritableThreadLocal làm cơ chế truyền ngữ cảnh nghiệp vụ tùy tiện qua các executor: thời điểm tạo thread, caller của factory và cơ chế tái sử dụng worker ảnh hưởng đến giá trị thừa kế. Khi cần dữ liệu cho một task, truyền qua tham số/đối tượng ngữ cảnh rõ ràng sẽ dễ kiểm soát hơn.

### 4.3. uncaughtExceptionHandler(handler)

Cấu hình cách tiếp nhận Throwable **không được xử lý** khiến Thread kết thúc bất thường:

~~~java
Thread t = Thread.ofPlatform()
        .name("reporter")
        .uncaughtExceptionHandler((thread, failure) ->
                System.err.println(thread.getName() + ": " + failure))
        .start(() -> {
            throw new IllegalStateException("failed");
        });
t.join();
~~~

Đoạn này là **mã minh họa đặt trong một phương thức có thể ném InterruptedException**, không phải chương trình Java độc lập. Handler không biến tác vụ lỗi thành thành công; nó hỗ trợ logging, cảnh báo và xử lý lỗi cuối cùng. Lưu ý khác biệt với ExecutorService.submit(...): nhiều executor **bắt ngoại lệ vào Future**, vì vậy lỗi thường xuất hiện khi gọi Future.get() dưới dạng ExecutionException thay vì truyền vào uncaught exception handler của Thread. Với tác vụ quan trọng, caller vẫn cần xử lý kết quả và lỗi của Future.

## 5. Cấu hình dành riêng cho platform thread

Chỉ Thread.Builder.OfPlatform có các tùy chọn sau:

| Tùy chọn | Ý nghĩa | Cảnh báo thực tế |
|---|---|---|
| daemon(boolean), daemon() | Quyết định trạng thái daemon; daemon() tương đương true | Daemon không giữ JVM sống khi mọi non-daemon threads đã kết thúc. |
| priority(int) | Chỉ định mức ưu tiên từ Thread.MIN_PRIORITY tới Thread.MAX_PRIORITY | Không bảo đảm thời gian chạy hay fairness; hiệu lực phụ thuộc hệ điều hành và giới hạn ThreadGroup. |
| group(ThreadGroup) | Gắn platform thread vào ThreadGroup | ThreadGroup là cơ chế cũ; không dùng làm giới hạn tài nguyên hoặc ranh giới bảo mật. |
| stackSize(long) | Gợi ý kích thước stack native khi tạo platform thread | JVM/OS có thể bỏ qua hoặc hiệu chỉnh; giá trị âm không hợp lệ, 0 có nghĩa bỏ qua gợi ý. |

Nếu không đặt daemon/priority/group, platform thread có thể thừa kế giá trị từ thread tạo ra nó theo quy tắc của Thread API. Vì vậy, để xác định rõ hành vi cho worker đặc biệt, hãy thiết lập thuộc tính quan trọng thay vì dựa vào ngữ cảnh không kiểm soát.

Ví dụ:

~~~java
Thread.Builder.OfPlatform builder = Thread.ofPlatform()
        .name("file-worker-", 0)
        .daemon(false)
        .priority(Thread.NORM_PRIORITY)
        .inheritInheritableThreadLocals(false);

Thread first = builder.unstarted(() -> performWork());
~~~

Đây là **đoạn trích minh họa**: performWork() là hàm giả định của ứng dụng, không phải ví dụ biên dịch độc lập. Cần gọi first.start() để chạy.

**stackSize không phải bảo đảm tài nguyên.** Đó là gợi ý về cỡ stack, không hứa JVM sẽ cấp đúng số byte và cũng không trực tiếp cho biết mức RAM vật lý đang dùng. Kích thước quá nhỏ có thể dẫn đến StackOverflowError khi stack sâu; quá lớn có thể giảm số native threads khả dụng. Không đặt giá trị chỉ dựa vào cảm tính.

### Virtual thread không có daemon(false), priority(...), group(...), stackSize(...)

Dùng các phương thức này trên kết quả Thread.ofVirtual() sẽ **không biên dịch**, vì OfVirtual không công bố chúng. Virtual threads luôn daemon và có mức ưu tiên cố định. Nếu muốn thiết lập số lượng tác vụ, deadline, giới hạn kết nối hoặc điều kiện thoát ứng dụng, hãy dùng cơ chế quản lý tác vụ, executor và đồng bộ, thay vì cố thay đổi các thuộc tính platform-only.

## 6. Ví dụ độc lập 1: tạo platform/virtual thread từ hai factory

Đây là chương trình Java 21+ đầy đủ, thể hiện cấu hình, name counter, newThread chưa start, start, join và cách nhận diện thread.

Lưu tệp **ThreadBuilderBasics.java**:

~~~java
import java.util.concurrent.ThreadFactory;

public class ThreadBuilderBasics {
    public static void main(String[] args) throws InterruptedException {
        ThreadFactory platformFactory = Thread.ofPlatform()
                .name("platform-worker-", 1)
                .daemon(false)
                .priority(Thread.NORM_PRIORITY)
                .inheritInheritableThreadLocals(false)
                .factory();

        ThreadFactory virtualFactory = Thread.ofVirtual()
                .name("virtual-task-", 1)
                .inheritInheritableThreadLocals(false)
                .factory();

        Thread p = platformFactory.newThread(() -> {});
        Thread v = virtualFactory.newThread(() -> {});
        System.out.println("before=" + p.getState() + "," + v.getState());

        p.start();
        v.start();
        p.join();
        v.join();

        System.out.println("platform=" + p.getName()
                + ", virtual=" + p.isVirtual()
                + ", daemon=" + p.isDaemon());
        System.out.println("virtual=" + v.getName()
                + ", virtual=" + v.isVirtual()
                + ", daemon=" + v.isDaemon());
        System.out.println("after=" + p.getState() + "," + v.getState());
    }
}
~~~

Biên dịch và chạy bằng JDK 21+:

~~~bash
javac --release 21 ThreadBuilderBasics.java
java ThreadBuilderBasics
~~~

Kết quả chính xác cho ví dụ:

~~~text
before=NEW,NEW
platform=platform-worker-1, virtual=false, daemon=false
virtual=virtual-task-1, virtual=true, daemon=true
after=TERMINATED,TERMINATED
~~~

**Phân tích kết quả:**

1. factory.newThread(...) tạo hai Thread ở trạng thái NEW; Runnable rỗng chưa chạy.
2. Tên được tạo từ tiền tố và bộ đếm bắt đầu từ 1, riêng biệt cho mỗi factory.
3. p.start() và v.start() bắt đầu hai đường thực thi. Không giả định thứ tự hai task vì việc lập lịch có thể thay đổi.
4. join() đợi cả hai hoàn tất rồi mới đọc trạng thái sau cùng; kết quả TERMINATED là ổn định.
5. isVirtual() xác định loại thread; isDaemon() cho thấy sự khác biệt platform/virtual. Platform được đặt daemon(false); virtual vốn luôn là daemon.

Lệnh javac --release 21 yêu cầu compiler hỗ trợ release 21 (JDK 21 trở lên); với JDK 17 sẽ báo không nhận API hoặc release. Bạn có thể thay Runnable rỗng bằng tác vụ thật nhưng cần tránh phụ thuộc vào thứ tự in console giữa các thread.

## 7. ThreadFactory làm gì, và không làm gì?

ThreadFactory là functional interface với một phương thức chính:

~~~java
Thread newThread(Runnable runnable);
~~~

Theo hợp đồng, newThread trả về **Thread chưa start**; đối tượng gọi factory (hoặc executor) chịu trách nhiệm khởi động. factory() từ Thread.Builder tạo một ThreadFactory phù hợp với cấu hình hiện tại. Factory do builder tạo **an toàn khi nhiều thread cùng gọi newThread**; bản thân Thread.Builder **không thread-safe**, không nên chia sẻ để các luồng khác nhau vừa chỉnh cấu hình vừa tạo thread.

Ví dụ dùng với một platform thread pool cố định:

~~~java
ThreadFactory workerFactory = Thread.ofPlatform()
        .name("cpu-worker-", 0)
        .daemon(false)
        .factory();

java.util.concurrent.ExecutorService pool =
        java.util.concurrent.Executors.newFixedThreadPool(4, workerFactory);

// Tác vụ thật do ứng dụng submit/execute; cần shutdown và chờ kết thúc.
~~~

Đây là **đoạn tích hợp vào phương thức**, không phải chương trình độc lập. Executor quyết định **bao nhiêu worker**, cách lấy việc và khi nào start worker; factory quyết định **cách tạo từng Thread**. newFixedThreadPool(4, ...) giới hạn số worker nhưng mặc định dùng **queue không giới hạn**; nó không tự bảo vệ ứng dụng khỏi vô số tác vụ chờ. Với tải cần backpressure, cân nhắc ThreadPoolExecutor có bounded queue, timeout và rejection policy rõ ràng.

Điểm dễ nhầm: tạo virtual ThreadFactory **không tự biến một fixed thread pool thành executor với một virtual thread cho từng task**. Fixed pool vẫn dùng một tập worker hữu hạn theo cơ chế executor đó; mỗi worker lấy nhiều task nối tiếp. Nếu muốn một virtual thread riêng cho mỗi task, dùng Executors.newVirtualThreadPerTaskExecutor() hoặc Executors.newThreadPerTaskExecutor(virtualFactory).

### Lựa chọn executor phù hợp

| Mục tiêu | Kết hợp hợp lý | Điều cần tự kiểm soát |
|---|---|---|
| Tối đa vài platform workers tái sử dụng | newFixedThreadPool(n, platformFactory) hoặc ThreadPoolExecutor | Queue, chính sách quá tải, timeout, shutdown. |
| Một virtual thread trên mỗi task, mặc định | Executors.newVirtualThreadPerTaskExecutor() | Số task nhận vào, tài nguyên downstream và hủy. |
| Một virtual thread trên mỗi task, tên/handler tùy chỉnh | Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("task-", 0).factory()) | Thread-per-task **không có trần số thread** riêng; cần giới hạn tài nguyên. |
| Tự kiểm soát vòng đời một ít Thread | builder.start(...) / unstarted(...), join() | Bắt đầu, kết thúc và xử lý lỗi thủ công. |

**Không sử dụng Executors.newThreadPerTaskExecutor(platformFactory) vô hạn** cho tải lớn khi chưa hiểu chi phí native thread. API này tạo thread mới cho mỗi task, không tái sử dụng như fixed pool và không có giới hạn thread nội tại.

## 8. Ví dụ độc lập 2: virtual ThreadFactory + executor + Semaphore

Một virtual thread cho mỗi tác vụ không có nghĩa hệ thống bên dưới chịu được vô hạn tác vụ đồng thời. Ví dụ dùng Semaphore cho phép nhiều virtual threads được tạo nhưng chỉ **hai tác vụ** vào một đoạn mô phỏng tài nguyên giới hạn cùng lúc.

Lưu tệp **VirtualFactoryExecutor.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

public class VirtualFactoryExecutor {
    public static void main(String[] args) throws Exception {
        ThreadFactory factory = Thread.ofVirtual()
                .name("io-task-", 0)
                .inheritInheritableThreadLocals(false)
                .factory();
        Semaphore limit = new Semaphore(2);

        try (ExecutorService executor = Executors.newThreadPerTaskExecutor(factory)) {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                final int id = i;
                results.add(executor.submit(() -> {
                    limit.acquire();
                    try {
                        Thread.sleep(20);
                        return "completed-" + id;
                    } finally {
                        limit.release();
                    }
                }));
            }
            for (Future<String> result : results) {
                System.out.println(result.get());
            }
        }
    }
}
~~~

Biên dịch, chạy:

~~~bash
javac --release 21 VirtualFactoryExecutor.java
java VirtualFactoryExecutor
~~~

Kết quả:

~~~text
completed-0
completed-1
completed-2
completed-3
~~~

**Vì sao output có thứ tự cố định?** Công việc thực thi có thể hoàn thành không theo thứ tự, nhưng chương trình lưu Future theo thứ tự submit và in kết quả bằng vòng lặp qua danh sách đó. Future.get() chờ task tương ứng hoàn tất, không dựa vào thời điểm kết quả được xử lý.

**Vì sao finally đặt sau acquire()?** Thread chỉ release permit sau khi acquire() thành công. Nếu bị interrupt trong lúc đợi acquire(), ngoại lệ được ném trước khi vào try/finally nên không giải phóng permit của người khác. Sau khi đã lấy permit, sleep() có thể bị interrupt; finally vẫn chạy để trả permit.

**Vì sao try-with-resources?** Từ Java 21, ExecutorService có thể đóng bằng try-with-resources; close() thực hiện shutdown có trật tự và chờ tác vụ hoàn tất. Đối với ứng dụng có thời hạn nghiêm ngặt, có thể cần chính sách timeout, cancel và shutdown phù hợp thay vì để chờ vô hạn.

**Giới hạn của ví dụ:** Semaphore đặt trần truy cập tài nguyên mô phỏng, **không** giới hạn số virtual threads được executor tạo hay số task nhận vào. Một ứng dụng thật vẫn cần cơ chế bounded admission/backpressure, deadline, giới hạn connection, memory và queue upstream. Thread.sleep(20) dùng để minh họa chờ; không phải benchmark tốc độ hoặc chứng minh lợi thế hiệu năng.

## 9. Cấu hình và quản lý lỗi trong ứng dụng thật

### 9.1. Tạo task và quản lý vòng đời

- Nếu tự dùng builder.start(...), luôn giữ tham chiếu cần thiết để join() hoặc dùng cơ chế phối hợp nếu tiến trình phải chờ kết quả.
- Virtual thread là daemon: JVM có thể bắt đầu kết thúc khi không còn non-daemon threads; việc tạo virtual threads **không đủ** để bảo đảm tác vụ quan trọng hoàn thành trước khi process thoát.
- Nếu dùng executor, bảo đảm shutdown/close, đợi task hoặc áp chính sách cancellation khi quá thời hạn.
- Không start() một Thread lần thứ hai: Thread đã khởi động, dù hoàn thành, không thể được tái khởi động và sẽ ném IllegalThreadStateException. Có thể tạo **Thread mới từ cùng factory**.

### 9.2. Hủy và interrupt

Thread.interrupt() không cưỡng bức giết task tại mọi thời điểm. Nhiều API blocking phản ứng bằng InterruptedException, nhưng tác vụ phải hợp tác kiểm tra và thoát đúng cách. Trong đoạn bắt InterruptedException mà chưa thể ném tiếp, thường nên gọi Thread.currentThread().interrupt() để khôi phục cờ rồi kết thúc tác vụ hoặc trao quyền quyết định cho caller.

Với ExecutorService.submit(...), lỗi tác vụ được đóng gói trong Future; Future.get() có thể ném ExecutionException. Future.cancel(true) yêu cầu interrupt khi có thể, không chứng minh resource đã được giải phóng hoặc tác vụ đã đạt trạng thái nghiệp vụ thành công.

### 9.3. Handler không thay thế xử lý lỗi nghiệp vụ

uncaughtExceptionHandler dùng cho lỗi không được xử lý ở Thread top-level. Nó không phải cơ chế retry, không tự ghi rollback và không thay thế Future.get(). Nếu handler tự ném lỗi, runtime không bảo đảm cung cấp xử lý bổ sung theo ý bạn. Giữ handler đơn giản, tránh thêm tác vụ rủi ro trong handler.

### 9.4. Ngữ cảnh thread và dữ liệu

InheritableThreadLocal mặc định có thể truyền dữ liệu vào thread con; trong pool, worker có thể được tạo tại thời điểm khác xa lúc submit. Với virtual thread-per-task, số thread lớn khiến dữ liệu trong ThreadLocal có thể tăng chi phí bộ nhớ. Không dùng ThreadLocal để cache đối tượng lớn cho mỗi virtual thread; tránh mang dữ liệu nhạy cảm hoặc mutable state không kiểm soát giữa các task. Truyền tham số rõ ràng, giới hạn vòng đời và cleanup trong finally khi cần.

## 10. Những hiểu nhầm thường gặp

**“Thread.Builder tương tự một ExecutorService.”** Sai. Builder mô tả **một cách tạo Thread**; executor quyết định **nhận task, queue, lập lịch giao việc, shutdown**. ThreadFactory cũng chỉ có trách nhiệm tạo Thread, không tự quản lý hàng đợi.

**“factory.newThread(task) sẽ chạy task ngay.”** Sai. Nó trả Thread chưa start. Nếu tự sử dụng, phải gọi start(); nếu giao cho executor, chính executor quản lý việc start.

**“Có thể đổi daemon, priority hay stackSize của virtual thread qua builder.”** Sai. Các API đó chỉ có trên OfPlatform. Virtual thread luôn daemon, có priority cố định, và không cung cấp tuỳ chỉnh native stack qua builder.

**“name("worker") tạo tên duy nhất cho mọi thread.”** Sai. Nó cố định tên. Muốn có chuỗi tên, dùng name("worker-", start).

**“Builder có thể dùng từ nhiều luồng một cách tự do.”** Sai. Thread.Builder không thread-safe; ThreadFactory do builder.factory() tạo là thread-safe. Hãy hoàn thiện cấu hình và chia sẻ factory, tránh concurrent mutation của builder.

**“Tạo hàng triệu virtual threads luôn an toàn.”** Sai. Mỗi task vẫn mang bộ nhớ, đối tượng, tham chiếu và có thể giữ connection, mutex, file descriptor hay gọi service khác. Cần giới hạn tải vào và tài nguyên đắt tiền theo mức thật sự.

**“Virtual threads tạo nhiều CPU hơn.”** Sai. Chúng giúp khai thác thời gian chờ blocking I/O, không tăng năng lực phần cứng. Với tác vụ tính toán nặng, giới hạn song song có kiểm soát vẫn quan trọng.

**“Không cần biết hiện tượng pinning hoặc blocking.”** Không chính xác. Khả năng JVM nhường carrier khi virtual thread chặn phụ thuộc loại thao tác và phiên bản JDK; native calls, native/foreign code hoặc cơ chế đồng bộ trong một số JDK có thể cản trở unmount. Ví dụ, vấn đề pinning liên quan synchronized đã được cải thiện đáng kể ở JDK mới hơn so với JDK 21. Phải quan sát và thử đúng bản runtime đang triển khai, không áp kết luận cho mọi version.

**“ThreadMXBean.getThreadCount() là tổng số platform + virtual threads.”** Sai trong Java 21: java.lang.management.ThreadMXBean theo dõi **platform threads**, không đếm virtual threads. Nếu số thread trong hệ thống tăng chủ yếu vì virtual threads, số liệu từ MXBean sẽ không phản ánh hết số tác vụ đang chạy.

**“Tăng thread priority sửa được vấn đề throughput.”** Không có bảo đảm. Lập lịch còn phụ thuộc tải, OS, CPU, waiting, locks và quota container. Priority không phải công cụ thay thế đo đạc.

## 11. Quan sát và gỡ lỗi

Kiểm tra vấn đề theo thứ tự từ biểu hiện đến nguyên nhân:

| Triệu chứng | Cần quan sát | Hướng xử lý |
|---|---|---|
| Không thấy tác vụ chạy | Thread ở NEW? Có gọi start() không? Executor đã shutdown chưa? | Phân biệt unstarted/newThread với start; kiểm tra lifecycle. |
| Thread tên trùng nhau | Dùng name(String) hay name(prefix, start)? | Đặt tên theo chuỗi tăng và phân loại vai trò tác vụ. |
| Process thoát khi task chưa xong | Chỉ còn daemon/virtual threads? | Join/Future.get/close executor, thiết kế shutdown đúng. |
| Số platform threads hoặc native memory tăng | Có tạo OS thread per-task? Pool/queue cấu hình thế nào? | Giới hạn worker, reusing pool, đo native stack/OS limits. |
| Virtual tasks tạo nhiều nhưng latency tăng | Backend, connection pool, semaphore, queue, timeout | Đặt giới hạn tài nguyên downstream và backpressure. |
| Lỗi bị “mất” | Dùng submit() nhưng không đọc Future? | Đọc Future.get(), ghi nhận lỗi từ task; handler không thay thế. |
| ThreadLocal giữ dữ liệu ngoài ý muốn | Pool reuse, inheritable values, cleanup | Truyền context tường minh, remove() đúng scope. |

Để chẩn đoán thực tế, đặt tên worker/virtual task có ý nghĩa, dùng Java Flight Recorder (JFR) và các công cụ thread dump phù hợp với **phiên bản JDK**. Quan sát song song CPU, memory, context switches, task count, queue depth, downstream latency và cancellation. Với platform threads, ThreadMXBean hoặc OS thread tools có ích; với virtual threads, dùng thêm công cụ quan sát hỗ trợ virtual-thread events/dumps vì nhiều phép đếm thread cổ điển chỉ phản ánh platform threads.

Không chạy phép đo hàng triệu thread trên máy không có giới hạn tài nguyên thử nghiệm: hiệu năng bị chi phối bởi cả workload, CPU, native memory, heap, kết nối và thời gian chờ. Ví dụ chạy được trong bài chứng minh ngữ nghĩa API, **không** tự nó so sánh tốc độ platform và virtual threads.

## 12. Sơ đồ quyết định và checklist

~~~text
Cần tạo Thread Java 21+?
  │
  ├─ Một ít công việc, tự quản lý vòng đời
  │      └─ Thread.ofPlatform()/ofVirtual()
  │           → cấu hình → start(...) hoặc unstarted(...).start()
  │           → join()/xử lý lỗi
  │
  └─ Nhiều tác vụ và cần quản lý kết quả/đóng tài nguyên
         │
         ├─ CPU-bound hoặc cần giới hạn số worker native
         │      └─ platform ThreadFactory + executor có số worker/queue hợp lý
         │
         └─ Chủ yếu blocking I/O, nhiều tác vụ đồng thời
                └─ virtual ThreadFactory + thread-per-task executor
                     + giới hạn tài nguyên downstream, timeout, backpressure
~~~

Trước khi áp dụng vào ứng dụng thật, xác nhận:

1. Đã dùng JDK 21+ và hiểu sự khác nhau giữa builder.start, builder.unstarted, factory.newThread.
2. Đã chọn đúng kiểu thread cho workload và xác định rõ ai start, ai join, ai đóng executor.
3. Đã đặt tên mô tả vai trò và không coi name là ID duy nhất.
4. Đã dùng đúng tùy chọn: daemon/priority/group/stackSize dành cho platform; virtual không tùy biến các thuộc tính đó.
5. Đã cân nhắc thừa kế InheritableThreadLocal, xử lý uncaught exception, Future lỗi và interrupt.
6. Đã giới hạn kết nối/tài nguyên đắt tiền, quyết định mức nhận task và chính sách khi quá tải.
7. Đã đo đúng runtime/OS đang dùng, tránh đánh giá virtual concurrency bằng riêng MXBean platform-thread metrics.

## 13. Tài liệu API gốc để tra cứu

- Thread.Builder (Java 21): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.Builder.html
- Thread.Builder.OfPlatform: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.Builder.OfPlatform.html
- Thread.Builder.OfVirtual: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.Builder.OfVirtual.html
- ThreadFactory: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadFactory.html
- Executors: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html
- ThreadMXBean: https://docs.oracle.com/en/java/javase/21/docs/api/java.management/java/lang/management/ThreadMXBean.html

**Điểm cần nhớ:** Thread.Builder làm rõ **loại thread và cấu hình tạo thread**; start()/unstarted()/factory() làm rõ **thời điểm và cách khởi tạo**. ThreadFactory chuyển cấu hình vào hạ tầng quản lý task nhưng không thay thế executor, giới hạn concurrency, xử lý lỗi hay shutdown. Platform thread và virtual thread chia sẻ nhiều API Thread, song có đặc tính tài nguyên và lựa chọn vận hành khác nhau.


</details>

- [Quay lại đầu trang](#back-to-top)
