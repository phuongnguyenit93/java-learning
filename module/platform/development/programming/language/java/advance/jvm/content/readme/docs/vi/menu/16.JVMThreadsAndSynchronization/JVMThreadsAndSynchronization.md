<a id="back-to-top"></a>

# Luồng và đồng bộ

## Menu
- [Luồng Java, luồng nền tảng và luồng ảo trong mô hình thực thi](#jvm-threads-and-synchronization-001)
- [Ngăn xếp của luồng ảo và giới hạn của sơ đồ mỗi luồng có một native stack](#jvm-threads-and-synchronization-002)
- [Monitor của đối tượng và tính tái nhập của synchronized](#jvm-threads-and-synchronization-003)
- [monitorenter, monitorexit và phương thức có cờ ACC_SYNCHRONIZED](#jvm-threads-and-synchronization-004)
- [wait, notify, notifyAll và quan hệ giữa wait set với quyền giữ monitor](#jvm-threads-and-synchronization-005)
- [Java Memory Model khác với sơ đồ heap, stack và bộ nhớ vật lý](#jvm-threads-and-synchronization-006)
- [Tính nguyên tử, khả năng nhìn thấy và thứ tự thao tác giữa các luồng](#jvm-threads-and-synchronization-007)
- [Quan hệ happens-before qua khóa, volatile, start và join](#jvm-threads-and-synchronization-008)
- [Quy tắc trường final, hoàn thành khởi tạo và công bố đối tượng an toàn](#jvm-threads-and-synchronization-009)
- [Sắp xếp lại thao tác, data race và những bảo đảm mà sleep không cung cấp](#jvm-threads-and-synchronization-010)
- [Phân biệt deadlock, chờ khóa và JVM đang thực hiện công việc nền](#jvm-threads-and-synchronization-011)

## <a id="jvm-threads-and-synchronization-001">Luồng Java, luồng nền tảng và luồng ảo trong mô hình thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Luồng Java, luồng nền tảng và luồng ảo trong mô hình thực thi JVM

## 1. Mục tiêu và bản đồ khái niệm

Trong Java, `java.lang.Thread` biểu diễn **một luồng thực thi Java** với call stack, trạng thái, danh tính và quy tắc tương tác với Java Memory Model. Tuy nhiên, từ **JDK 21**, đối tượng `Thread` không nhất thiết gắn 1:1 với **một luồng hệ điều hành (OS thread)**: Java có **platform threads** và **virtual threads**. Virtual thread là một luồng Java thực sự, không phải một task callback trá hình, nhưng được HotSpot/JDK **lập lịch lên các platform threads làm carrier** khi cần chạy.

Phân biệt ba mức:

1. **Java thread**: khái niệm lập trình trong `java.lang.Thread`; có `Thread.currentThread()`, stack trace, interrupt, `ThreadLocal`, quyền sở hữu monitor và semantics happens-before.
2. **Platform thread**: một loại `Thread` Java thông thường được JVM nối với **OS thread** theo mô hình 1:1 trong HotSpot; OS scheduler quản lý việc chạy nó trên CPU.
3. **Virtual thread**: một loại `Thread` Java nhẹ, được **JDK scheduler** ánh xạ linh hoạt lên carrier platform threads theo mô hình nhiều virtual threads trên ít platform threads, nhờ cơ chế mount/unmount khi chờ thích hợp.

Sau bài học, bạn sẽ biết cách:
- Tạo, `start`, `join`, `interrupt` platform và virtual threads đúng phiên bản JDK.
- Diễn giải sự khác biệt giữa Java thread, OS thread, carrier, thread pool, executor và task.
- Giải thích vì sao virtual threads phù hợp **nhiều công việc I/O-bound đồng thời**, nhưng không tự làm CPU tính toán nhanh hơn.
- Hiểu sự liên quan giữa blocking APIs, mount/unmount, scheduler, parking, monitor, pinning và thay đổi trong **JDK 24 (JEP 491)**.
- Dùng `ExecutorService` với **một virtual thread mỗi task**, giới hạn kết nối DB/downstream bằng `Semaphore` thay vì pool virtual threads.
- Tránh các lỗi thread safety, shared-state race, `ThreadLocal` footprint, interrupt bị nuốt, blocking native code và resource exhaustion.
- Đọc diagnostics/JFR/thread dumps có ý thức về sự khác nhau giữa Java 17, Java 21 và Java 24+.
- Chạy các chương trình Java 17+ và Java 21+ có đầu ra xác định mà không suy diễn OS-thread mapping không được API cam kết.

## 2. Lịch sử ngắn: Java 17 khác Java 21 và 24 như thế nào?

| JDK | Virtual-thread API và trạng thái | Điểm cần nhớ |
| --- | --- | --- |
| **8–17** | Không có virtual threads chuẩn trong `java.lang.Thread` | `new Thread(...)` tạo platform thread theo HotSpot thông dụng; sử dụng executors/pools để quản lý chi phí |
| **19** | Virtual threads **preview** — **JEP 425** | Yêu cầu cấu hình preview tương ứng khi biên dịch/chạy code dùng preview APIs |
| **20** | Preview lần hai — **JEP 436** | API/chẩn đoán tiếp tục được điều chỉnh; không xem là bản ổn định |
| **21 (LTS)** | Virtual threads **chính thức** — **JEP 444** | Có `Thread.ofVirtual()`, `Thread.startVirtualThread(...)` và `Executors.newVirtualThreadPerTaskExecutor()`; không cần preview |
| **22–23** | Tiếp tục API chính thức | Pinning do `synchronized` + blocking vẫn là điều cần khảo sát trên các bản này |
| **24** | **JEP 491**: đồng bộ monitor không còn gây loại pinning cũ trong trường hợp thông thường | Virtual thread có thể unmount khi blocking trong nhiều vùng `synchronized`; native/foreign constraints vẫn cần lưu ý |
| **25+** | Tiếp tục cải tiến runtime/diagnostics | Kiểm tra đúng vendor/build; không áp quy tắc pinning của JDK 21 cho mọi JDK tương lai |

**Không nhầm** virtual threads (JEP 444) với structured concurrency. `StructuredTaskScope` trải qua các giai đoạn preview/API change trong nhiều release; bài này dùng **Thread và ExecutorService chuẩn ổn định của JDK 21+**, không yêu cầu bật preview để chạy ví dụ.

**Nguồn chính thức:** [JEP 425](https://openjdk.org/jeps/425), [JEP 436](https://openjdk.org/jeps/436), [JEP 444](https://openjdk.org/jeps/444), [JEP 491](https://openjdk.org/jeps/491).

## 3. Platform thread: Java Thread gắn với OS thread

Trên HotSpot, platform thread thường có quan hệ:

~~~text
Java platform Thread object
             |
             | được runtime gắn tới
             v
Operating-system thread
             |
             | OS scheduler
             v
CPU core(s), time slicing, kernel scheduling
~~~

Đặc điểm:
- OS thread có native stack và các cấu trúc quản lý ở hệ điều hành.
- Việc tạo/hủy rất nhiều platform threads có chi phí đáng kể; số lượng đồng thời thường phải hạn chế theo CPU/RAM/OS limits.
- Nếu một platform thread **blocking I/O** hoặc chờ lock, OS thread của nó thường không thể phục vụ request Java khác trong thời gian đó.
- JDK cung cấp `ThreadPoolExecutor` và các factories trong `Executors` để **tái sử dụng platform threads** giữa nhiều tasks.
- Platform thread có thể là **daemon** hoặc **non-daemon**, có priority thuộc phạm vi mà runtime/OS có thể diễn giải; không dùng priority làm phương pháp kiểm soát công bằng hoặc correctness.

Ví dụ mô hình thread pool:

~~~text
10.000 requests chờ I/O
          |
          v
  Blocking request queue
          |
     200 worker platform threads
          |
          v
  Tối đa khoảng 200 tasks được chạy/chờ
  trong các worker ở một thời điểm;
  phần còn lại xếp hàng
~~~

Số 200 chỉ minh họa. Thread pool giúp tránh chi phí tạo OS thread mỗi request, nhưng **không tự giải quyết** giới hạn số request đồng thời khi các worker bị chiếm bởi hoạt động chờ. Nếu I/O latency cao, queue có thể phình và gây tail latency.

### Một JVM luôn chạy nhiều loại threads

Ngoài application platform/virtual threads, HotSpot có thể có GC threads, JIT compiler threads, signal/reference processing, scheduler/carrier threads và các service threads khác. Số OS threads của process không bằng số lần ứng dụng gọi `new Thread`. Các GC/VM internal threads cũng có chi phí CPU/RSS.

## 4. Virtual thread: Java thread không gắn cố định với OS thread

JEP 444 mô tả virtual thread là `java.lang.Thread` **không buộc chặt với một OS thread riêng**. HotSpot/JDK có scheduler riêng, sử dụng các platform threads gọi là **carriers** để chạy virtual threads.

~~~text
Virtual Thread V1  --mount--> Carrier P1 --OS scheduler--> CPU
Virtual Thread V2  --mount--> Carrier P2 --OS scheduler--> CPU
Virtual Thread V3  --WAITING (unmounted, không giữ carrier)
Virtual Thread V4  --WAITING (unmounted, không giữ carrier)
Virtual Thread V5  --RUNNABLE/queued

Một lúc sau:
V1 chờ socket -> unmount khỏi P1
V5 mount lên P1 -> tiếp tục xử lý
V1 I/O sẵn sàng -> được enqueue, có thể mount lên P2
~~~

Sơ đồ khái niệm, **không phải lịch chạy bắt buộc**. Virtual thread có thể sử dụng nhiều carriers khác nhau qua thời gian. Không có API tiêu chuẩn cho phép virtual thread xác định một carrier nhất định để dùng làm identity công việc.

### Mount và unmount

- **Mount:** virtual thread được đặt vào carrier platform thread để thực thi Java code.
- **Unmount:** runtime tạm lưu trạng thái cần thiết để virtual thread dừng ở một thao tác chờ phù hợp, giải phóng carrier cho virtual threads khác.
- **Resume:** khi event/operation hoàn tất, virtual thread được đưa vào scheduler và có thể mount trên carrier khác.

Nhiều I/O operations trong JDK được thiết kế để **đợi theo semantics blocking của Java source**, nhưng nội bộ chuyển sang cơ chế cho phép virtual thread unmount. Người lập trình vẫn viết code tuần tự:

~~~java
String response = httpClient.send(request, responseHandler).body();
~~~

Đây là **snippet minh họa** cần `httpClient`, `request` và `responseHandler` phù hợp. Việc API/blocking có thực sự nhường carrier hay không còn tùy JDK operation, native paths, implementation và phiên bản.

### Virtual threads không phải OS kernel scheduling

JDK scheduler ánh xạ virtual threads tới carriers; **hệ điều hành vẫn lập lịch OS threads lên các CPU cores**. Vì vậy virtual threads không mang lại nhiều CPU cores hơn, không làm network/database xử lý nhanh hơn và không bỏ được giới hạn file descriptors hay connection pools.

### Carrier không phải danh tính thread mà application quan sát

Khi code chạy trong virtual thread:
- `Thread.currentThread()` trả về **virtual thread đang chạy**, không phải carrier.
- `Thread.currentThread().isVirtual()` là **true** (JDK 21+).
- `ThreadLocal` và stack trace thuộc về **logical virtual thread**, không chia sẻ tự động với carrier.
- Virtual thread có thể resume trên carrier khác mà Java logic vẫn là cùng một Thread.

Không nên cache một OS thread ID từ native code rồi giả định nó cố định cho suốt một virtual thread.

## 5. CPU-bound khác I/O-bound: virtual threads không thay định luật tài nguyên

Một service có 1000 requests, mỗi request dành 2 ms để tính toán CPU và 200 ms chờ I/O. Khi có nhiều requests đồng thời, phần chờ có thể được thực hiện hiệu quả bằng virtual threads mà không cần một OS thread bị giữ riêng cho mỗi request. Điều này làm **thread-per-request style** dễ mở rộng hơn.

Nhưng nếu mỗi request chạy 200 ms **thuần CPU**, số công việc CPU đang tiến hành thực tế vẫn bị giới hạn bởi số cores/CPU quota. Một triệu virtual threads có thể chỉ là một triệu tasks cạnh tranh CPU, có thêm scheduling overhead.

Mô hình:

~~~text
I/O-bound requests:
  CPU vài ms -> WAIT I/O lâu -> CPU vài ms
                  |
                  +-- virtual thread unmount, carrier xử lý task khác

CPU-bound requests:
  CPU -> CPU -> CPU -> CPU
  Không có carrier được giải phóng chỉ vì thread là virtual
~~~

**Virtual-thread scheduler không được hứa preempt từng virtual thread sau một timeslice công khai.** Không dựa vào một “1ms quantum” hoặc thứ tự fairness cố định. Thiết kế ứng dụng theo hợp tác với scheduler và blocking APIs thích hợp, tránh các tác vụ CPU rất dài chiếm carriers khi không cần thiết.

### Little's Law là công cụ tư duy, không phải cam kết throughput

Trong một hệ thống ổn định, số tác vụ trung bình đồng thời xấp xỉ **L = λ × W** (throughput × thời gian ở hệ thống). Nếu target 1000 requests/s và mỗi request có latency trung bình 0,2 s, có thể cần khoảng 200 requests cùng hiện diện. Điều này không đồng nghĩa cần đúng 200 OS threads, và cũng không nghĩa chỉ cần 200 connections DB: **thời gian và tài nguyên thực tế** của từng request mới quyết định.

## 6. Java 17+ chạy độc lập: platform threads và join

Trước JDK 21, dùng `new Thread` hoặc `Thread.ofPlatform()`? Phương thức `Thread.ofPlatform()` là API của thế hệ mới, **không được dùng trong bài chạy với Java 17**. Ví dụ tương thích Java 17 dưới đây chỉ dùng `new Thread`.

Tạo file **`PlatformThreads17Demo.java`**:

~~~java
public class PlatformThreads17Demo {
    static final class Counter {
        private int value;

        synchronized void increment() {
            value++;
        }

        synchronized int get() {
            return value;
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Counter counter = new Counter();
        Thread[] workers = new Thread[4];

        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < 1_000; j++) {
                    counter.increment();
                }
            }, "worker-" + i);
            workers[i].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("threads=" + workers.length);
        System.out.println("total=" + counter.get());
    }
}
~~~

Biên dịch/chạy JDK 17+:

~~~bash
javac --release 17 PlatformThreads17Demo.java
java PlatformThreads17Demo
~~~

**Stdout chính xác** nếu quá trình hoàn tất bình thường:

~~~text
threads=4
total=4000
~~~

Giải thích:
- `start()` yêu cầu một luồng mới chạy `Runnable`; **gọi trực tiếp `run()` không tạo thread mới**.
- Bốn threads đều tăng Counter 1000 lần; `synchronized` ngăn data race trên `value` trong chương trình này.
- `join()` khiến main chờ worker hoàn tất. Thành công từ `join` cũng tạo ra quan hệ happens-before cho những hành động của worker so với thread sau khi join theo Java Memory Model.
- Không dùng `Thread.sleep(...)` để “đợi đủ lâu”: thời gian scheduler không xác định.
- Thread execution order khác nhau giữa runs nhưng **kết quả cuối** vẫn là 4000 nhờ synchronization.

Nếu bỏ `synchronized` và để nhiều threads cùng thực thi `value++`, biểu thức increment gồm nhiều thao tác read/modify/write, có thể làm mất cập nhật. Vấn đề thread safety **không liên quan** việc thread là platform hay virtual.

## 7. Java 21+: tạo platform và virtual thread bằng builder

JDK 21 có cả:
- `Thread.ofPlatform()`: tạo platform thread theo builder.
- `Thread.ofVirtual()`: tạo virtual thread.
- `Thread.startVirtualThread(Runnable)`: tạo và chạy virtual thread trong một lời gọi.
- `Thread.currentThread().isVirtual()`: kiểm tra thread hiện tại.

Tạo file **`ThreadKinds21Demo.java`**:

~~~java
public class ThreadKinds21Demo {
    static String describe(String label) {
        Thread t = Thread.currentThread();
        return label + ": " + t.getName()
                + ", virtual=" + t.isVirtual()
                + ", daemon=" + t.isDaemon();
    }

    public static void main(String[] args)
            throws InterruptedException {
        String[] results = new String[2];

        Thread platform = Thread.ofPlatform()
                .daemon(false)
                .name("platform-1")
                .start(() -> results[0] = describe("platform"));

        Thread virtual = Thread.ofVirtual()
                .name("virtual-1")
                .start(() -> results[1] = describe("virtual"));

        platform.join();
        virtual.join();

        System.out.println(results[0]);
        System.out.println(results[1]);
    }
}
~~~

Biên dịch/chạy bằng JDK **21+**, không phải `javac --release 17`:

~~~bash
javac --release 21 ThreadKinds21Demo.java
java ThreadKinds21Demo
~~~

**Stdout chính xác:**

~~~text
platform: platform-1, virtual=false, daemon=false
virtual: virtual-1, virtual=true, daemon=true
~~~

Các đặc điểm:
- Platform thread trong ví dụ được thiết lập non-daemon tường minh.
- **Virtual threads luôn là daemon threads** theo API JDK 21. Không thể biến chúng thành non-daemon bằng cách gọi `setDaemon(false)`.
- Virtual thread có priority cố định `Thread.NORM_PRIORITY` theo API JDK 21, không thể dùng priority để sắp lịch tác vụ quan trọng hơn.
- Mỗi thread có **tên riêng** trong ví dụ nên output không phụ thuộc default virtual-thread name.
- Thu thập chuỗi vào hai phần tử riêng và chỉ in **sau khi join** loại bỏ tình huống hai threads in xen kẽ, nhờ đó stdout xác định.
- Không in carrier name: nó là implementation detail, có thể thay đổi khi virtual thread unmount/remount.

**Lưu ý:** virtual thread là daemon, nên một ứng dụng chỉ còn các virtual threads đang chạy nhưng main platform thread đã kết thúc có thể **không được giữ sống** chỉ bởi virtual threads. Sử dụng `join()` hoặc lifetime-managed `ExecutorService` trước khi main kết thúc.

## 8. Thread là công việc hay tài nguyên?

Một thread là **nơi thực thi** code; một task là **đơn vị công việc** có thể nộp vào executor.

Trong mô hình truyền thống:

~~~text
Many tasks
   |
   v
ExecutorService thread pool
   |
   +--> reusable platform worker #1
   +--> reusable platform worker #2
   +--> reusable platform worker #3
   ...
~~~

Mục tiêu pooling: reuse **OS/platform threads** tương đối đắt. Với virtual threads, mô hình khuyến nghị là **mỗi task một virtual thread**, không pool các virtual threads:

~~~text
Task 1 --> Virtual Thread 1 ----+
Task 2 --> Virtual Thread 2 ----+--> JDK scheduler --> carrier pool
Task 3 --> Virtual Thread 3 ----+
Task N --> Virtual Thread N ----+
~~~

`Executors.newVirtualThreadPerTaskExecutor()` tạo **một virtual thread mới cho mỗi task được submit**. Nó không giống fixed thread pool với N virtual workers tái sử dụng. Task hoàn tất thì virtual thread cũng hoàn tất.

### Vì sao không pool virtual threads?

Virtual thread thường rẻ hơn platform thread đáng kể về chi phí tạo và stack tài nguyên lúc chờ. Pool virtual threads sẽ đặt **một giới hạn nhân tạo** lên số tasks, làm mất phần lớn lợi ích cho request-per-thread style mà không giúp quản lý tài nguyên downstream một cách tách bạch.

Thay vì giới hạn **số virtual threads**, giới hạn **số đồng thời được sử dụng tài nguyên khan hiếm**:
- Connection pool của database.
- API downstream quotas.
- Số file descriptors/sockets.
- Concurrent writes đến một service.
- Heap/request buffers.
- CPU-bound parallelism.

Sử dụng `Semaphore` hoặc queue/backpressure theo tài nguyên, không tạo fixed virtual-thread pool như biện pháp mặc định.

## 9. Java 21+: ExecutorService một virtual thread mỗi task

Tạo file **`VirtualExecutor21Demo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VirtualExecutor21Demo {
    public static void main(String[] args) throws Exception {
        try (ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor()) {

            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                final int id = i;
                futures.add(executor.submit(() -> {
                    Thread.sleep(10);
                    return "task=" + id
                            + ", virtual="
                            + Thread.currentThread().isVirtual();
                }));
            }

            for (Future<String> future : futures) {
                System.out.println(future.get());
            }
        }
    }
}
~~~

Biên dịch/chạy JDK 21+:

~~~bash
javac --release 21 VirtualExecutor21Demo.java
java VirtualExecutor21Demo
~~~

**Stdout chính xác:**

~~~text
task=0, virtual=true
task=1, virtual=true
task=2, virtual=true
task=3, virtual=true
task=4, virtual=true
~~~

Mặc dù tasks có thể hoàn tất **không theo thứ tự 0→4**, vòng in duyệt `Future` trong danh sách theo thứ tự submit; vì vậy output ổn định. `Thread.sleep(10)` có thể khiến virtual thread unmount trong runtime hỗ trợ, nhưng **mỗi task không được hứa mất đúng 10 ms**, và demo không phải benchmark thông lượng.

Các lưu ý:
- `Future.get()` có thể ném `ExecutionException` nếu task fail; cần xử lý trong ứng dụng thật.
- `try-with-resources` gọi `ExecutorService.close()` để chờ quản lý kết thúc tasks theo contract.
- Việc dùng executor không tự áp timeout/cancellation cho I/O bất tận. Dùng deadlines, `Future.cancel(true)`, interrupt-aware APIs hoặc cơ chế scope thích hợp.
- Với request server, một executor-per-request thường **không cần thiết**; nên quản lý executor lifecycle ở mức phù hợp với application.

## 10. Java 21+: giới hạn downstream bằng Semaphore

Giả sử ứng dụng muốn chạy nhiều virtual threads để đợi I/O, nhưng dịch vụ downstream chỉ cho **hai** hoạt động đồng thời. Không phải giới hạn scheduler của JVM xuống hai carriers; hãy bảo vệ chính tài nguyên đó.

Tạo file **`VirtualSemaphore21Demo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualSemaphore21Demo {
    public static void main(String[] args) throws Exception {
        Semaphore permits = new Semaphore(2);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        try (ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = new ArrayList<>();

            for (int i = 0; i < 6; i++) {
                futures.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        int current = active.incrementAndGet();
                        peak.accumulateAndGet(current, Math::max);
                        Thread.sleep(20);
                        return Thread.currentThread().isVirtual();
                    } finally {
                        active.decrementAndGet();
                        permits.release();
                    }
                }));
            }

            int completed = 0;
            boolean allVirtual = true;
            for (Future<Boolean> future : futures) {
                allVirtual &= future.get();
                completed++;
            }

            System.out.println("completed=" + completed);
            System.out.println("allVirtual=" + allVirtual);
            System.out.println("bounded=" + (peak.get() <= 2));
            System.out.println("activeAfter=" + active.get());
        }
    }
}
~~~

Lệnh:

~~~bash
javac --release 21 VirtualSemaphore21Demo.java
java VirtualSemaphore21Demo
~~~

**Stdout chính xác:**

~~~text
completed=6
allVirtual=true
bounded=true
activeAfter=0
~~~

Giải thích:
- Có 6 virtual tasks nhưng **tối đa hai** tasks được vào protected section cùng lúc.
- `Semaphore.acquire()` chờ permit; khi virtual thread có thể park/unmount, carrier được dùng cho task khác.
- `try/finally` đảm bảo permit được release dù phần xử lý trong protected section thất bại sau khi acquire thành công.
- `peak` có thể bằng 1 hoặc 2 tùy scheduling; bài chỉ in **bất biến đúng** `peak <= 2`, không đoán nó sẽ bằng chính xác 2.
- Demo giới hạn concurrency, **không làm database tự đáp ứng vô hạn requests**. Trong thực tế nên có timeout/backpressure thay vì để hàng trăm nghìn virtual tasks đợi permit và giữ buffers mãi mãi.

**Điểm tổng quát:** “virtual threads rẻ” khác với “mọi tài nguyên được chúng truy cập đều vô hạn”.

## 11. Thread lifecycle: start, wait, join, interrupt

Các platform/virtual threads đều là Java threads và tuân những semantics cơ bản của `Thread`.

Một sơ đồ khái niệm:

~~~text
NEW
 |
 | start()
 v
RUNNABLE ---------------------+
 |                           |
 +--> WAITING / TIMED_WAITING|
 |       (join, sleep, park, |
 |        blocking operations)|
 |                           |
 +--> BLOCKED (monitor) -----+
 |
 | run() hoàn tất hoặc ném exception uncaught
 v
TERMINATED
~~~

**Thread.State** là trạng thái ở mức Java, không phải một bản sao từng trạng thái scheduler/kernel. `RUNNABLE` có thể bao gồm thread **đang chạy hoặc chờ CPU**; `WAITING` không nói chính xác carrier nào đang bị giữ. Không suy ra pinning chỉ từ `getState()`.

### `start()` khác `run()`

~~~java
Thread t = new Thread(() -> System.out.println("running"));
t.run();   // thực hiện Runnable trên thread hiện tại
// t.start(); // dòng khác: yêu cầu thread riêng
~~~

Đây là snippet minh họa. Nếu gọi `run()` trực tiếp, object Thread mới không chuyển sang started state. Nếu sau đó gọi `start()` một lần, nó sẽ chạy Runnable trong thread mới **lần nữa**. Nếu gọi `start()` hai lần trên cùng một Thread object, lần thứ hai ném `IllegalThreadStateException`.

### `join()` và happens-before

Các writes hoàn tất trong worker trước khi nó kết thúc được thread gọi `join()` quan sát theo happens-before rules sau khi join thành công. Điều này giúp lấy kết quả từ `String[] results` của ví dụ ThreadKinds mà không cần biến từng element thành `volatile`, với điều kiện main thực sự join trước khi đọc và không còn writer khác chạy đồng thời.

### `interrupt()` là tín hiệu hợp tác

Nó không phải “kill thread lập tức”. Một virtual hoặc platform thread bị interrupt:
- Nếu đang chờ qua một số interruptible APIs, API có thể ném `InterruptedException`.
- Code thường nên cleanup, dừng task, hoặc khôi phục interrupt flag nếu không thể truyền exception lên.
- Thread làm tính toán CPU không tự dừng chỉ vì interrupt flag bật nếu code không kiểm tra/cancellation không được propagate.

### Java 21+ demo interrupt

Tạo file **`VirtualInterrupt21Demo.java`**:

~~~java
import java.util.concurrent.atomic.AtomicReference;

public class VirtualInterrupt21Demo {
    public static void main(String[] args) throws Exception {
        AtomicReference<String> result =
                new AtomicReference<>("not-completed");

        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(100_000);
                result.set("finished");
            } catch (InterruptedException ex) {
                result.set("interrupted");
                Thread.currentThread().interrupt();
            }
        });

        worker.interrupt();
        worker.join();

        System.out.println("result=" + result.get());
    }
}
~~~

Chạy:

~~~bash
javac --release 21 VirtualInterrupt21Demo.java
java VirtualInterrupt21Demo
~~~

**Stdout chính xác:**

~~~text
result=interrupted
~~~

Trong điều kiện chương trình bình thường, `interrupt()` được gửi gần ngay sau `start()`. Dù worker chưa vào `sleep` hoặc đã vào rồi, khi `sleep` kiểm tra interrupt nó sẽ bị đánh thức/throw, nên branch `catch` gán "interrupted". Không dùng timer để đo việc interrupt “nhanh” bao nhiêu.

## 12. Blocking I/O và pinning: cần phân biệt JDK 21 với JDK 24+

### JDK 21–23: blocking dưới monitor có thể pin carrier

Một tình huống đơn giản:

~~~java
synchronized (lock) {
    socketReadBlocking();
}
~~~

Đây là snippet minh họa cần `lock` và `socketReadBlocking()` thích hợp. Trên JDK 21–23, một virtual thread chạy blocking operation trong vùng `synchronized` có thể **bị pin** vào carrier platform thread. Nếu virtual thread chờ I/O lâu trong khi pinned, **carrier cũng bị chiếm**, giảm khả năng chạy những virtual threads khác.

~~~text
JDK 21-23:
Virtual thread giữ monitor
     |
Blocking I/O trong synchronized
     |
Virtual thread có thể PINNED
     |
Carrier/OS thread bị giữ trong lúc chờ
     |
Giảm khả năng mở rộng khi có nhiều tác vụ như vậy
~~~

Pinning không làm kết quả chương trình Java sai; nó chủ yếu gây vấn đề **throughput/CPU carrier availability**. Không phải mọi `synchronized` block ngắn là vấn đề. Nếu chỉ bảo vệ vài instructions trong memory, thay toàn bộ locks một cách máy móc có thể làm code phức tạp mà không tăng hiệu năng.

### JDK 24+: JEP 491 cải thiện synchronized

**JEP 491: Synchronize Virtual Threads without Pinning** được phát hành trong JDK 24. HotSpot có thể cho virtual threads **unmount ngay cả khi đang giữ monitor hoặc chờ vào monitor**, trong những trường hợp supported, nên **không còn cần đổi `synchronized` sang `ReentrantLock` chỉ để tránh loại pinning monitor cũ**.

Tuy nhiên:
- Pinning hoặc capture của carrier vẫn có thể xảy ra với **native methods/foreign function calls** và một số đường tương tác VM/OS.
- Những I/O operations không phải tất cả đều có cùng khả năng unmount; hành vi phụ thuộc API, OS và JDK.
- Đo pinning trên build thực tế, không kết luận từ cú pháp `synchronized` mà không biết runtime version.

**Một bài học về migration:** lời khuyên “phải thay synchronized bằng ReentrantLock trên virtual threads” có thể đúng khi xử lý blocking hotspots trên **JDK 21**, nhưng đã lỗi thời nếu tuyên bố đó là quy tắc bắt buộc cho **JDK 24+**.

### Native/foreign pinning

Nếu virtual thread gọi native code/JNI/FFM và bị blocking theo con đường không thể unmount, carrier có thể bị pin. JNI critical operations còn có các quy tắc riêng về object references/critical sections. Không giải quyết bằng cách đổi một monitor Java rồi cho rằng native path cũng đã không pin.

### ReentrantLock có vai trò gì?

`java.util.concurrent.locks.ReentrantLock` là một primitive lock có API tryLock, timed acquisition, fairness options, condition variables và thường hoạt động với virtual-thread parking thích hợp. Nó có lợi khi chính semantics đó cần thiết; không tự động nhanh hơn `synchronized` cho mọi workload.

## 13. Carrier scheduler và parallelism

JEP 444 mô tả implementation scheduler dựa trên **work-stealing ForkJoinPool**, phân biệt với `ForkJoinPool.commonPool()`. Scheduler sử dụng platform threads để chạy virtual threads.

Một số system properties HotSpot/JDK có liên quan:

| Property | Vai trò | Lưu ý |
| --- | --- | --- |
| `jdk.virtualThreadScheduler.parallelism` | Điều chỉnh target parallelism của scheduler | Mặc định thường gắn với số available processors được JVM nhận diện |
| `jdk.virtualThreadScheduler.maxPoolSize` | Giới hạn max số platform threads scheduler dùng trong một số tình huống compensation | Không dùng nó làm số virtual threads có thể tạo |
| `jdk.tracePinnedThreads` | Hỗ trợ trace pinning trên JDK 21–23 | Hành vi đã thay đổi/không còn hữu ích như cũ sau JEP 491; ưu tiên JFR trên JDK mới |

Ví dụ tùy chỉnh (chỉ khi đã đo):

~~~bash
java -Djdk.virtualThreadScheduler.parallelism=4 MyApp
~~~

Đây là **mẫu lệnh**, cần có class `MyApp` hợp lệ. Nó không tạo ra thêm bốn CPU cores và không đảm bảo chỉ có đúng bốn platform threads trong toàn JVM. GC threads/JIT threads và các đường compensation có thể thêm OS threads.

**Thông thường không nên chỉnh scheduler properties sớm.** Nếu service tắc nghẽn ở DB pool, tăng carrier parallelism sẽ không làm DB có thêm kết nối; còn có thể gây CPU contention. Nếu workload rất CPU-bound, tăng virtual-thread count không thay thế việc quản lý số tác vụ có thể chạy hữu ích cùng lúc.

### Thread.yield() không phải phương pháp chia scheduler công bằng

`Thread.yield()` chỉ là một **gợi ý**; không hứa thread sẽ unmount/remount, nhường một timeslice cụ thể hay một thread khác sẽ chạy ngay. Không dựa vào nó để giải quyết deadlock, starvation hoặc scheduling fairness.

## 14. Stack, heap và chi phí tài nguyên

Platform thread có native stack gắn với OS thread; việc tạo rất nhiều platform threads có thể tiêu hao **virtual address space, native memory và OS scheduling resources**.

Virtual thread có **stack state được JVM quản lý linh hoạt**, khi unmount trạng thái có thể nằm trong các **stack chunk objects** của heap. Nhờ đó nhiều virtual threads đang chờ không yêu cầu mỗi thread giữ một native stack độc lập tương đương platform thread. Tuy nhiên “virtual thread rẻ” **không nghĩa miễn phí**.

Mỗi virtual thread vẫn có:
- Java Thread object và các cấu trúc trạng thái.
- Những stack frames/stack chunks còn sống.
- Captured lambdas, task objects, Futures/executor metadata.
- References tới buffers, request payloads, transactions hoặc connections.
- `ThreadLocal` entries/values của chính virtual thread khi được sử dụng.
- Queuing/timeouts/cancellation bookkeeping.

Một triệu virtual threads chờ mà mỗi task giữ 10 KiB payload có thể giữ **rất nhiều GiB** cho payloads, chưa kể stack/task overhead. Kinh phí memory và số file descriptors/connections vẫn phải được tính.

### ThreadLocal: API vẫn hoạt động, mô hình dùng có thể không phù hợp

JEP 444 bảo đảm virtual threads trong JDK 21 có thể dùng `ThreadLocal`. Tuy nhiên:
- Một `ThreadLocal` có value lớn trên **mỗi** virtual thread sẽ nhân footprint theo số threads.
- Pattern “mỗi worker platform thread có một expensive cache/reusable connection” không còn hợp lý nếu mỗi task được chạy trên **virtual thread ngắn hạn**.
- `ThreadLocal` thuộc **logical virtual thread**, không theo carrier khi thread chuyển mount.
- Dọn thread-local references khi task kết thúc hoặc khi scope không còn cần. Không dùng ThreadLocal như global request cache vô giới hạn.

Cần phân biệt **context propagation** với **resource pooling**. Request ID có thể gắn theo task/thread context phù hợp, nhưng JDBC connection vẫn nên lấy từ **connection pool riêng** có giới hạn.

## 15. Synchronization và Java Memory Model không đổi

Virtual threads **không tự loại bỏ data races**. Những vấn đề vốn có vẫn tồn tại:
- `x++` không nguyên tử.
- `HashMap`/`ArrayList` thông thường không tự thread-safe cho concurrent mutations.
- `volatile` bảo đảm một số quan hệ ordering/visibility, không biến compound increments thành atomic.
- `synchronized` và `Lock` bảo vệ critical sections theo semantics.
- `AtomicInteger`/`LongAdder` có những trade-offs phù hợp update patterns.
- `ExecutorService` không bảo đảm toàn bộ tasks sẽ hoàn thành trước main nếu application bỏ executor lifecycle/cancellation.

Ví dụ:

~~~java
// Snippet: counter dùng chung cần atomicity.
AtomicInteger count = new AtomicInteger();
Runnable job = count::incrementAndGet;
~~~

Đây là code minh họa, cần `import java.util.concurrent.atomic.AtomicInteger;`. Cho dù job chạy trên 100 platform threads hay 100 virtual threads, `AtomicInteger` giúp bảo đảm atomic increments.

### Happens-before tiêu biểu

- Những actions trước khi gọi `Thread.start()` **happens-before** các actions trong thread được start.
- Các actions trong thread **happens-before** một thread khác phát hiện nó đã terminate bằng `join()` thành công.
- Một `volatile write` happens-before một `volatile read` tương ứng sau đó trong synchronization order.
- Monitor unlock happens-before lock tiếp theo trên cùng monitor.
- `ExecutorService/Future` còn có các memory consistency effects theo contract API.

Các quy tắc này áp dụng ở mức **Java Memory Model**, không tùy thuộc thread chạy trên carrier P1 hay P2.

## 16. Deadlock, starvation và quá tải

Virtual threads không chữa deadlock:
- Task A giữ Lock1 rồi chờ Lock2.
- Task B giữ Lock2 rồi chờ Lock1.
- Số virtual threads lớn đến đâu cũng không phá được circular wait.

Virtual threads cũng không tự:
- Tăng số database connections tối đa.
- Tăng throughput của API downstream.
- Tránh transactional locks hoặc DB deadlocks.
- Tránh backpressure/queues không giới hạn.
- Giúp blocking native/unsupported I/O paths nhường OS carrier.
- Tăng CPU cores cho compute-intensive tasks.

**Rủi ro mới:** vì tạo virtual threads rất dễ, ứng dụng có thể nhận **quá nhiều requests/tasks cùng một lúc**, làm exhaustion ở DB, socket limits, heap buffer, external rate limit hoặc semaphore wait queue. Luôn gắn concurrency policy với **downstream capacity và latency SLO**.

### Một ví dụ sai về giới hạn concurrency

~~~java
// Anti-pattern minh họa:
// Executors.newFixedThreadPool(20) vô tình áp trần
// số request chờ I/O xuống 20 worker platform threads.
//
// Không nên tạo "pool 20 virtual threads" để giả lập
// chính giới hạn đó nếu mục tiêu là tối đa hóa khả năng chờ I/O.
~~~

Trong ứng dụng production, có thể cần **request admission control** và bounded queue để giới hạn tổng số request đang được xử lý, kể cả khi sử dụng virtual threads. Semaphore chỉ giải quyết từng tài nguyên cụ thể, không phải chính sách bảo vệ mọi loại overload.

## 17. I/O nào thường nhường carrier, I/O nào cần đo?

| Operation / tình huống | Kỳ vọng chung | Caveat |
| --- | --- | --- |
| `Thread.sleep(...)` trong virtual thread | Có thể unmount/nhường carrier | Không hứa thời gian wake chính xác |
| `BlockingQueue.take()` | Thường park virtual thread, nhường carrier | Kiểm tra đường API/JDK cụ thể |
| `LockSupport.park()` | Có virtual-thread support | Không đồng nghĩa OS thread bị park |
| `Socket`/một số `java.net` blocking I/O | JDK triển khai để virtual thread có thể chờ mà không giữ carrier | Driver/native paths khác cần kiểm chứng |
| `Future.get()` chờ task | Có thể dùng parking phù hợp | Nếu task deadlock, chờ vẫn có thể vô hạn |
| `synchronized` + blocking, JDK 21–23 | Có nguy cơ pin carrier | Di chuyển long blocking khỏi monitor nếu là hotspot |
| `synchronized` + blocking, JDK 24+ | Monitor-related pinning được cải thiện theo JEP 491 | Native/OS limitations khác vẫn còn |
| JNI/FFM native operation chờ | Có thể giữ/pin carrier | Phụ thuộc native call path, không thay chỉ bằng `ReentrantLock` |
| CPU loop dài | Dùng carrier khi tính toán | Không tự tạo thêm CPU capacity |

**Không áp dụng công thức “mọi blocking operation đều unmount”**. Các APIs/versions/hardware/OS implementation khác nhau. Nếu đổi JDK hoặc JDBC driver, đo lại I/O/thread behavior trên target runtime.

## 18. Quan sát virtual threads bằng JFR và jcmd

### JFR events

Java Flight Recorder có các events liên quan virtual threads, tùy version và recording configuration:
- `jdk.VirtualThreadStart` và `jdk.VirtualThreadEnd`: lifecycle events; thường **không bật mặc định** vì số lượng virtual threads có thể rất lớn.
- `jdk.VirtualThreadPinned`: phát hiện trường hợp virtual thread pin carrier lâu theo threshold được cấu hình.
- `jdk.VirtualThreadSubmitFailed`: vấn đề khi runtime submit/start/unpark virtual thread vào scheduler.
- Các events CPU, allocations, thread park và GC có thể hỗ trợ diễn giải nếu được bật/thu đủ dữ liệu.

Một ứng dụng có virtual threads rất ngắn hạn có thể tạo hàng triệu lifecycle events; bật hết `Start/End` trên production có thể gây overhead. Dùng JFR profiles và chọn events theo câu hỏi điều tra.

### Lệnh chẩn đoán

Từ JDK hỗ trợ, chạy với **PID thật** của Java process:

~~~bash
jcmd PID Thread.dump_to_file -format=json virtual-threads.json
jcmd PID JFR.start name=VThreads settings=profile duration=60s filename=vthreads.jfr
jcmd PID JFR.check
~~~

Cú pháp `PID` ở trên là **placeholder**, phải thay bằng process ID. Command availability, options và shape file có thể khác nhau theo JDK build; dùng `jcmd PID help` để xác nhận. Thread dumps truyền thống có thể không liệt kê mọi virtual thread theo cách bạn quen từ platform threads; format JSON qua lệnh chuyên dụng giúp quan sát virtual threads tốt hơn trên JDK hỗ trợ.

### JDK 21–23 pinning diagnostics

Có thể thử:

~~~bash
java -Djdk.tracePinnedThreads=full MyApp
~~~

Đây là ví dụ có class `MyApp` hợp lệ, chủ yếu hữu ích trên JDK 21–23 cho monitor/native pinning. Với JDK 24+, JEP 491 đã giải quyết loại monitor pinning cũ và thuộc tính này không còn là công cụ đáng dựa vào như trước; ưu tiên **JFR jdk.VirtualThreadPinned** và logging/profiling thích hợp.

**Đừng đọc JFR event count = số requests bị ảnh hưởng.** Một request có thể có nhiều blocking operations; event sampling/threshold làm một số tình huống không được ghi, và pinning không tự đồng nghĩa SLO violation.

## 19. Thread dumps và metrics: số thread đo được chưa chắc đúng mục tiêu

Platform-thread-oriented management APIs, OS tools và thread counts thường **không phản ánh trực tiếp số virtual threads**.

Ví dụ:
- OS utility đếm Linux threads/Windows threads của process chủ yếu nhìn **OS/platform threads**.
- Virtual thread count có thể lớn hơn rất nhiều mà OS thread count không tăng tương ứng.
- `ThreadMXBean` truyền thống tập trung platform threads; không suy ra JVM chỉ có từng ấy logical Java threads.
- Virtual-thread dumps/observability APIs giúp nhìn logical stacks, nhưng xuất file rất lớn có thể tạo overhead.

**Mục tiêu vận hành** không nên là “số virtual threads càng lớn càng tốt” mà là:
- completed requests/s;
- p95/p99 end-to-end latency;
- CPU quota, throttling, RSS, heap occupancy;
- DB connection usage và wait times;
- runnable/blocked/parked task profile;
- pinned events/carrier availability trên JDK liên quan;
- error/timeout/cancellation rate.

Nếu p99 tăng, kết hợp event timeline với tracing, downstream pool metrics và GC/JIT/OS signals. Không mặc định mọi latency spike là pinning.

## 20. So sánh platform thread pools và virtual-thread-per-task

| Thuộc tính | Platform thread / pool | Virtual thread per task |
| --- | --- | --- |
| Quan hệ với OS | 1:1 theo HotSpot thông dụng | N:M qua carriers theo JDK scheduler |
| Tạo thread | Tương đối tốn, thường dùng pool | Nhẹ hơn, tạo mới cho mỗi task |
| Stack | Native thread stack là tài nguyên đáng kể | Stack state được JVM quản lý linh hoạt, có stack chunks trong heap khi unmount |
| Blocking I/O | Worker OS thread thường bị giữ | Nhiều JDK blocking APIs có thể unmount và nhường carrier |
| CPU-bound | Chạy trên OS cores | Vẫn chạy trên OS cores/carriers, không tăng CPU vật lý |
| Tác dụng với thread-per-request | Pool có thể giới hạn số requests đồng thời | Cho phép nhiều tasks chờ mà không cần OS thread riêng |
| Synchronization | `synchronized`, `Lock`, atomic APIs | **Cùng Java Memory Model/locking semantics** |
| ThreadLocal | Thường tái sử dụng theo pool worker | Mỗi virtual thread có context riêng, có thể rất nhiều values |
| Tuning chủ yếu | Pool size, queue, rejection, native stacks | Downstream capacity, task admission, heap footprint, pinning/GC |
| Tình huống phù hợp | CPU-bound fixed workers, legacy libraries hoặc resource control cụ thể | High-concurrency blocking I/O, request-per-task, dễ giữ call stacks/try-catch |
| Nguy cơ | Worker starvation, OS thread exhaustion | Resource overload, pinning/native paths, memory giữ bởi tasks |

**Không chọn virtual threads chỉ vì “số threads nhiều hơn”**. Nếu đang dùng asynchronous non-blocking pipeline hoạt động tốt, chuyển sang virtual threads có thể đơn giản hóa code, nhưng lợi ích CPU/latency/throughput cần được đo. Đừng thay một reactive framework production chỉ từ kết quả toy benchmark.

## 21. Mô hình request-per-thread và mã dễ hiểu hơn callback

Một controller có thể thực hiện:

~~~java
Response handle(Request request) throws Exception {
    User user = users.load(request.userId());
    Price price = pricingService.quote(user);
    auditService.record(request, price);
    return new Response(user, price);
}
~~~

Đây là **pseudo-code minh họa**, cần các class/services phù hợp, không phải program độc lập. Nếu `users.load` và `pricingService.quote` là các blocking operations virtual-thread-friendly, **một virtual thread per request** có thể chờ tự nhiên qua các lời gọi tuần tự mà không giữ carrier suốt thời gian I/O. Bạn vẫn nhận được:
- try/catch/finally truyền thống;
- Java call stack thể hiện chuỗi công việc;
- structured control flow bằng loops/conditions;
- debugger/stack traces gắn với logical thread.

Nhưng lưu ý:
- Một request tuần tự có ba I/O calls vẫn phải đợi thời gian thực hiện của chúng. Virtual threads **không tự song song hóa** những thao tác phụ thuộc nhau.
- Nếu muốn chạy đồng thời nhiều I/O calls độc lập, vẫn cần API orchestration như futures hoặc cấu trúc concurrent khác.
- JDBC driver/HTTP client/file I/O library phải được kiểm tra tương thích và behavior phù hợp.
- Transactions/connections/locks giữ quá lâu vẫn là vấn đề nghiệp vụ.

## 22. Interrupt, timeout, cancellation và shutdown

Trong server có hàng nghìn virtual threads, quản lý lifetime quan trọng hơn chi phí gọi `Thread.ofVirtual()`.

### Sử dụng deadline

Một request không nên block vô hạn khi downstream chết. Sử dụng:
- timeouts của network client và database driver;
- `Future.get(timeout, unit)` khi thích hợp;
- cancellation/`interrupt()` theo contract từng API;
- admission control/backpressure;
- executor shutdown và chờ tasks kết thúc có kiểm soát.

### Ưu tiên không nuốt interrupt

Anti-pattern:

~~~java
try {
    Thread.sleep(1_000);
} catch (InterruptedException ignored) {
    // Không làm gì: mất cancellation signal.
}
~~~

Đây là snippet minh họa. Nếu không thể khai báo `throws InterruptedException` cho caller, thường nên cleanup rồi **khôi phục interrupt status** qua `Thread.currentThread().interrupt()`, hoặc kết thúc task đúng ngữ cảnh.

### Không dùng Thread.stop()

`Thread.stop()` là thao tác không an toàn/đã bị loại bỏ khả năng sử dụng ở nhiều API modes hiện đại, không phải cách hủy task đáng tin cậy. Hãy dùng cancellation hợp tác và resource cleanup trong `finally`.

### Virtual threads là daemon, main lifecycle rất quan trọng

Nếu main chỉ gọi `Thread.startVirtualThread(...)` rồi return, chương trình có thể chấm dứt trước khi virtual thread hoàn thành, vì virtual threads luôn daemon. Cần `join()`, `Future.get()` hoặc executor lifecycle để bảo đảm hoàn tất công việc mong muốn.

## 23. Common pitfalls khi chuyển từ thread pool sang virtual threads

| Sai lầm | Hệ quả và cách sửa |
| --- | --- |
| Đổi fixed pool sang triệu virtual threads nhưng DB pool chỉ có 20 connections | Chuyển nút thắt sang connection acquisition/queue; giới hạn bằng semaphore/pool và timeout |
| Pool virtual threads để “tiết kiệm thread” | Giảm concurrency không liên quan tài nguyên thật; dùng virtual-thread-per-task |
| Cho rằng virtual threads làm compute-heavy task nhanh gấp N | CPU cores/CPU quota không tăng; đo workload CPU |
| Dùng `ThreadLocal` để giữ buffer lớn cho mọi request | Tăng per-task heap footprint; xem lại resource sharing/pooling |
| Coi `Thread.currentThread()` là carrier | Đó là logical virtual Thread, không phải OS thread identity |
| Lưu OS thread ID rồi coi cố định sau blocking | Virtual thread có thể resume trên carrier khác |
| Thấy `synchronized` trên JDK 21–23 và bỏ qua pinning | Long blocking trong monitor có thể giữ carrier, cần profiling |
| Luôn thay `synchronized` bằng `ReentrantLock` trên JDK 24+ | JEP 491 xử lý monitor pinning thông thường; thay khóa phải dựa semantics |
| Tin rằng mọi blocking I/O nhường carrier | Native/foreign và một số OS/filesystem operations có exceptions |
| Không join các virtual threads daemon trước khi main kết thúc | Tasks có thể chưa hoàn tất |
| Dùng `Thread.sleep` để đợi result đã “chắc xong” | Race; dùng `join`/`Future.get`/latch |
| Không xử lý `InterruptedException` | Mất cancellation, tasks sống quá lâu |
| Chia sẻ HashMap/ArrayList và tăng `++` không đồng bộ | Data races/lost updates dù thread là virtual |
| Xem OS thread count là số virtual threads | Metrics chủ yếu phản ánh platform/OS threads |
| Xem benchmark “10.000 sleep tasks nhanh” là bằng chứng DB chịu được 10.000 queries | Downstream limits hoàn toàn khác |
| Phát triển code virtual thread rồi build `--release 17` | API chỉ chính thức từ JDK 21; build JDK 21+ |

## 24. Checklist triển khai production

1. **Xác định workload:** CPU-bound, I/O-bound, tỷ lệ waiting và peak concurrency.
2. **Chọn JDK:** ưu tiên release có virtual threads chuẩn (JDK 21+); nếu `synchronized`-related pinning quan trọng, thử **JDK 24+** trên target vendor/build.
3. **Giữ API tuần tự rõ ràng:** dùng virtual thread per task cho logic blocking, không rewrite thành callback chỉ để tiết kiệm OS threads.
4. **Đo tài nguyên khan hiếm:** DB connections, sockets, downstream QPS, heap buffers, connection wait times.
5. **Có admission control:** semaphore, bounded queue, rate limit và per-request deadline khi cần.
6. **Kiểm tra cancellation:** interrupt được propagate/restore, cleanup resources, executor shutdown.
7. **Audit ThreadLocal:** tránh expensive values per virtual thread; context không phụ thuộc carrier affinity.
8. **Audit blocking/native calls:** tìm pinning/long carrier occupancy trên đúng JDK.
9. **Quan sát bằng JFR/thread dumps:** bật event có chủ đích, tránh thu vô hạn mỗi task start/end.
10. **Load test có giới hạn thực:** throughput, p95/p99, CPU quota, RSS, GC, connection pool wait, errors.
11. **Canary rollout:** theo dõi regression và có rollback plan.
12. **Không bỏ qua security/context:** audit request identity, thread-scoped data và lifetime để tránh leak giữa tasks.

## 25. Câu hỏi tự kiểm tra

**Câu 1.** Java virtual thread có phải một OS thread mới cho mỗi `Thread.ofVirtual().start(...)` không?

**Đáp án:** Không. Virtual thread là Java Thread được JVM/JDK scheduler mount lên carrier platform thread khi chạy, có thể unmount khi chờ.

**Câu 2.** `Thread.currentThread()` trả gì khi đang chạy trên virtual thread?

**Đáp án:** Virtual thread hiện tại, không trả carrier.

**Câu 3.** Platform thread có thể resume trên OS thread khác bất kỳ như virtual thread không?

**Đáp án:** Theo HotSpot thông thường, platform thread gắn 1:1 với một OS thread, dù OS thread có thể chạy trên nhiều CPU cores qua OS scheduler.

**Câu 4.** Virtual thread có làm một vòng tính toán CPU 10 giây thành một giây trên cùng số cores không?

**Đáp án:** Không. Nó có ích cho concurrency khi blocking, không làm tăng số CPU cores.

**Câu 5.** Virtual threads có chính thức trong Java 17 không?

**Đáp án:** Không. JDK 19/20 có preview, còn JDK 21 mới final theo JEP 444.

**Câu 6.** Virtual threads có phải daemon không?

**Đáp án:** Có, theo Thread API JDK 21, nên cần join/executor lifecycle để không mất task khi main kết thúc.

**Câu 7.** `Executors.newVirtualThreadPerTaskExecutor()` tái sử dụng 10 virtual workers phải không?

**Đáp án:** Không. Nó tạo một virtual thread mới cho mỗi task được submit.

**Câu 8.** Nếu DB chỉ có 10 connections thì nên giới hạn số virtual threads còn đúng 10 không?

**Đáp án:** Không nhất thiết. Giới hạn số operations **dùng DB** qua pool/semaphore; các tasks khác vẫn có thể làm việc/chờ tài nguyên khác.

**Câu 9.** `ThreadLocal` của virtual thread có bị đổi khi chuyển carrier không?

**Đáp án:** Không. Nó thuộc logical virtual thread; carrier's ThreadLocal không phải của virtual thread.

**Câu 10.** `value++` được nhiều virtual threads thực hiện đồng thời có an toàn không?

**Đáp án:** Không. Cần synchronized/atomic/locking theo yêu cầu.

**Câu 11.** Tại sao JDK 21 pinning trong `synchronized` đáng chú ý?

**Đáp án:** Blocking trong monitor có thể giữ carrier OS thread, giảm khả năng mở rộng.

**Câu 12.** JDK 24 có còn yêu cầu thay mọi `synchronized` bằng `ReentrantLock` không?

**Đáp án:** Không. JEP 491 giải quyết monitor-related pinning thông thường; vẫn phải xét native/foreign và tính đúng của synchronization.

**Câu 13.** OS thread count trong task manager có tương đương số virtual threads không?

**Đáp án:** Không. Nhiều virtual threads dùng số carriers giới hạn, nên count của OS khác logical threads.

**Câu 14.** `Thread.interrupt()` có “giết” một virtual thread ngay lập tức không?

**Đáp án:** Không. Nó là tín hiệu hợp tác, có thể làm interruptible blocking API throw hoặc được code kiểm tra.

**Câu 15.** Có được coi `Thread.sleep(10)` là đúng 10 ms và chứng minh scheduler fairness không?

**Đáp án:** Không. Thời gian wake/OS scheduling không được đảm bảo chính xác.

**Câu 16.** Một request thread giữ 1 MiB buffer, có 50.000 requests chờ: có “free” vì dùng virtual threads không?

**Đáp án:** Không. Riêng payloads đã có thể chiếm khoảng 50.000 MiB nếu chúng thực sự còn reachable, chưa tính stacks/metadata.

**Câu 17.** Tool nào giúp điều tra virtual thread pinning?

**Đáp án:** JFR event `jdk.VirtualThreadPinned` và diagnostics phù hợp JDK; trên JDK 21–23 có thể dùng `-Djdk.tracePinnedThreads`. Phải gắn events với latency/CPU thực tế.

**Câu 18.** Vì sao một chương trình in result sau `join()` ổn định dù thread scheduling không cố định?

**Đáp án:** Main chỉ đọc sau khi workers hoàn tất; happens-before từ thread termination/join bảo đảm visibility của kết quả được ghi đúng, nếu không có concurrent conflicting writer khác.

## 26. Sơ đồ tổng kết

~~~text
           JAVA CONCURRENCY PROGRAMMING
                      |
               java.lang.Thread
                      |
              +-------+-------+
              |               |
        PLATFORM THREAD    VIRTUAL THREAD
        HotSpot ~1:1 OS    JDK scheduler M:N
              |               |
          OS Thread       Mount trên CARRIER
              |               |
         OS scheduler     OS platform thread
              |               |
              +-------+-------+
                      |
                    CPU
                      |
           BLOCKING / WAITING?
              |               |
       Platform worker     Virtual thread có thể
       thường bị chiếm     unmount và nhường carrier
                              |
                      JDK 21-23: chú ý
                      pinning dưới synchronized
                              |
                      JDK 24+: JEP 491
                      cải thiện monitor pinning
                              |
                    NATIVE / OS EXCEPTIONS
                              |
                  CAPACITY / CORRECTNESS
              DB pools + Semaphores + timeouts
              JMM + interrupts + joins
              JFR + diagnostics + load testing

Virtual threads giúp tăng khả năng đồng thời cho I/O-bound
nhưng không tạo CPU, RAM hay downstream capacity mới.
~~~

**Kết luận:** cả platform và virtual threads đều là **Java threads thực sự**, chịu cùng các nguyên tắc thread safety và Java Memory Model. Platform threads thường nối trực tiếp với OS threads; virtual threads cho phép nhiều Java task đang chờ I/O dùng chung lượng carriers nhỏ hơn và tiếp tục code tuần tự. Virtual threads phù hợp **thread-per-request cho I/O-bound workloads**; hiệu năng và độ tin cậy vẫn phụ thuộc CPU, heap, native I/O, connection limits, monitoring và cách xử lý cancellation. Đặc biệt, **JDK 21 pinning dưới monitor** và **JDK 24+ JEP 491** phải được diễn giải theo **đúng phiên bản**, không trộn thành một quy tắc bất biến.

## Tài liệu tham khảo chính thức

- [JEP 425 — Virtual Threads (Preview), JDK 19](https://openjdk.org/jeps/425).
- [JEP 436 — Virtual Threads (Second Preview), JDK 20](https://openjdk.org/jeps/436).
- [JEP 444 — Virtual Threads, JDK 21](https://openjdk.org/jeps/444).
- [JEP 491 — Synchronize Virtual Threads without Pinning, JDK 24](https://openjdk.org/jeps/491).
- [Java SE 21 — Thread API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html).
- [Java SE 21 — Executors API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html).
- [Java SE 21 — ExecutorService API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html).
- [Java SE 21 — Semaphore API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Semaphore.html).
- [Java SE 21 — Java Core Libraries: Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html).
- [JLS Java SE 21 — §17 Threads and Locks / Java Memory Model](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html).
- [JDK 21 — jcmd](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html).
- [Oracle JDK 24 — Significant Changes (JEP 491)](https://docs.oracle.com/en/java/javase/24/migrate/significant-changes-jdk-24.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-threads-and-synchronization-002">Ngăn xếp của luồng ảo và giới hạn của sơ đồ mỗi luồng có một native stack</a>

<details>
<summary>Xem chi tiết</summary>

# Ngăn xếp của luồng ảo và giới hạn của sơ đồ mỗi luồng có một native stack

## 1. Mô hình cũ đúng với platform thread, không đúng với mọi Java thread

Một sơ đồ JVM thường gặp là:

~~~text
Java Thread A ── OS thread A ── native stack A
Java Thread B ── OS thread B ── native stack B
Java Thread C ── OS thread C ── native stack C
~~~

Đó là mô hình gần đúng cho **platform threads** trên HotSpot: mỗi platform thread gắn với một OS thread trong lifetime của nó, dùng native stack/context của OS thread. Từ **Java 21**, có thêm **virtual threads**: chúng vẫn là `java.lang.Thread` với danh tính, locals, call stack và exception handling, nhưng **không nắm riêng một OS thread/native stack trong toàn lifetime**.

~~~text
             VIRTUAL THREADS (logical concurrent tasks)
               VT-1       VT-2       VT-3 ... VT-10000
                 |          |          |
                 +----------+----------+-------+
                            |
                       JVM scheduler
                            |
           mount VT đang chạy lên một carrier
                            |
                  +---------+---------+
                  |                   |
              carrier-1            carrier-2
                  |                   |
             OS thread 1          OS thread 2
                  |                   |
           native stack 1      native stack 2

VT bị park/unmount: giữ execution state/call stack logic,
có thể ở các stack chunk objects trên Java heap.
Không cần 10000 OS/native stacks riêng cho 10000 VTs.
~~~

**Kết luận phải phân biệt hai mệnh đề:**

- Một virtual thread **có Java stack logic** chứa frames, variables, method calls và trạng thái exception handling.
- Virtual thread **không sở hữu một native stack riêng cố định**; nó dùng một carrier platform thread khi đang chạy và có thể **unmount** khi chờ một blocking operation được JDK hỗ trợ.

Không suy ra “VT luôn chạy trong heap mà chẳng dùng OS thread”. Khi thực thi CPU instructions, một VT vẫn **được mount trên một platform/OS carrier thread**.

## 2. Mốc Java 17, 19, 20, 21 và 24

| JDK | Tình trạng virtual threads | Hệ quả |
| --- | --- | --- |
| **17** | Không có API chuẩn virtual threads | Không thể compile `Thread.ofVirtual()` bằng `--release 17` |
| **19** | JEP 425: preview lần đầu | Phải dùng tính năng preview của đúng runtime |
| **20** | JEP 436: preview lần hai | Vẫn là preview |
| **21** | **JEP 444** chính thức đưa virtual threads vào Java | `Thread.ofVirtual()`, `isVirtual()`, `newVirtualThreadPerTaskExecutor()` |
| **24** | **JEP 491** cải thiện monitor/synchronized pinning | Kết luận về pinning JDK 21 không còn áp nguyên xi |

**Các ví dụ runnable trong bài yêu cầu Java 21+**, dù tên module là Java Advance - jvm. Nếu triển khai thực tế vẫn dùng JDK 17 thì học phần này dùng để hiểu sự thay đổi trong thiết kế JVM; muốn dùng API virtual threads ổn định cần JDK 21 trở lên.

Lệnh:

~~~bash
javac --release 21 VirtualThreadParkingDemo.java
java VirtualThreadParkingDemo
~~~

`--release 21` là **API/language target tại thời điểm biên dịch**. Chạy bytecode ấy trên JDK 24 vẫn sử dụng scheduler, HotSpot và GC của **JDK 24**, không phải tự động mô phỏng mọi chi tiết của JDK 21.

## 3. Ba ý nghĩa của “stack” cần tách bạch

### 3.1. Java call stack logic

Một luồng thực thi `handleRequest() → authenticate() → fetch() → read()` có các Java frames về mặt ngữ nghĩa JVM. Frames chứa trạng thái của method, locals/operand stack ở cấp bytecode và thông tin return/exception.

~~~text
VT logical call stack:

handleRequest()
    └── authenticate()
        └── fetch()
            └── readFromSocket()  ← tạm dừng chờ I/O
~~~

Khi hoạt động I/O kết thúc, chương trình phải **tiếp tục đúng call chain và locals**, không buộc tác giả viết lại bằng callback state machine.

### 3.2. Native stack của platform/carrier thread

Platform thread gắn với OS thread; OS thread dùng native stack và context do OS/JVM quản lý. Với kiểu “platform thread per request”, một request blocking giữ OS thread của nó suốt quá trình chờ. Native stack có thể reserved/committed/resident khác nhau, vì vậy không phải cứ `-Xss1m` thì mọi stack đã dùng đúng 1 MiB RAM vật lý.

**Carrier** là platform thread được scheduler sử dụng để thực thi virtual thread. Native stack của carrier là tài nguyên thuộc carrier, **không phải tài nguyên gắn cứng với một VT suốt lifetime**.

### 3.3. Continuation và stack chunks trên heap

HotSpot triển khai virtual-thread continuation và lưu stack trạng thái suspend qua các **stack chunk objects nằm trên Java heap do GC quản lý**, theo JEP 444. Stack có thể grow/shrink theo độ sâu thực thi. Một VT có call stack nông và chỉ chờ network không cần cùng footprint với VT có nhiều frames sâu.

Không mô tả rằng **mọi frame luôn nằm 100% trên heap ngay cả khi VT đang chạy**: execution vẫn chạy trên carrier OS stack và VM có thể chuyển/bảo toàn frames khi mount/unmount. Cách biểu diễn memory chính xác là chi tiết implementation HotSpot/JDK.

Quan trọng: JVM Specification mô tả abstract JVM stacks; không bắt buộc mọi JVM vendor phải tổ chức virtual-thread stack chunks y hệt HotSpot.

## 4. Mount, park, unmount và resume

~~~text
Time →
Carrier OS thread:
  | VT-A runs | VT-B runs ........ | VT-C runs | VT-A resumes |

VT-A:
  xử lý → blocking socket read → PARK/UNMOUNT → I/O ready → RESUME
                                     |
                              continuation state
                              heap stack chunks
~~~

Một chu kỳ khái niệm:

1. `Thread.ofVirtual().start(task)` tạo VT, lên lịch task.
2. Scheduler **mount** VT trên carrier khả dụng.
3. VT chạy Java code trên OS thread của carrier.
4. VT gặp một blocking operation được JDK hỗ trợ cho việc unmount (ví dụ một số socket I/O, `BlockingQueue.take()` hoặc `CountDownLatch.await()` trong điều kiện thích hợp).
5. JVM giữ execution state/stack để có thể resume, **unmount** VT, trả carrier về scheduler.
6. Khi hoạt động có thể tiếp tục, VT được đánh thức/schedule, có thể **mount lên carrier khác**.
7. Code chạy tiếp theo Java stack logic cũ, giữ `try/catch/finally` và locals theo contract.

**Không có bảo đảm VT luôn dùng một carrier cụ thể**, cũng không có API portable `getMyCarrierThread()`. `Thread.currentThread()` trong code VT trả về **chính VT**, không phải carrier.

### Điểm unmount không đồng nghĩa mọi method call

Virtual thread không tự unmount khi gọi một method ngắn, đọc một field hay tính toán CPU. Nó vẫn sử dụng carrier trong khi làm việc; nếu code CPU-bound không block, virtual thread vẫn chiếm khả năng thực thi của một OS thread như bình thường.

Một số đoạn native/JNI hoặc đường blocking không hỗ trợ unmount có thể **pin carrier**. Vì vậy câu “mọi blocking call đều giải phóng carrier” là sai.

## 5. So sánh platform stack và virtual stack

| Câu hỏi | Platform thread | Virtual thread |
| --- | --- | --- |
| Là Java `Thread` object? | Có | Có |
| Có Java frames/stack trace logic? | Có | Có |
| Có OS thread dành riêng suốt lifetime? | Có theo cơ chế platform threads | **Không** |
| Có native OS stack dành riêng suốt lifetime? | Thường có | **Không** |
| Có cần carrier OS thread khi chạy CPU? | Tự là OS thread | Có, sử dụng carrier |
| Khi chờ I/O được hỗ trợ, có thể nhả OS thread? | Thông thường không trong synchronous platform-thread I/O | **Có thể unmount** |
| Có thể tiếp tục trên carrier khác? | Không áp dụng | **Có** |
| Execution state khi unmount có thể nằm ở heap chunks? | Không theo cùng mô hình | **Có** |
| Số lượng có thể vượt xa OS threads? | Bị OS thread resources giới hạn | **Có**, tùy heap và các tài nguyên khác |
| CPU-bound method có tự chạy nhanh hơn? | Không có cam kết | Không có cam kết |

**“Virtual thread không có stack”** là phát biểu sai. Nếu chúng không có stack logic thì code đồng bộ Java với nested calls, recursion, exceptions và debugger stack traces sẽ không có semantics quen thuộc.

## 6. Vì sao stack chunks trên heap làm virtual threads rẻ hơn?

Platform-thread-per-request có thể cần một OS thread, native stack, OS context và scheduler resources cho mỗi request đang chờ. Với VT, carrier được tái sử dụng khi task blocked/unmounted; nhiều call stacks logic được giữ trên heap theo nhu cầu.

Ví dụ kiến trúc:

~~~text
10000 synchronous requests đang chờ network:

Platform-thread-per-request:
  gần 10000 platform/OS threads nếu không bị pool giới hạn
  rất nhiều native stacks và OS scheduling state

Virtual-thread-per-request:
  10000 Java VT objects và waiting continuations có thể tồn tại
  một số carriers được scheduler tái sử dụng khi VTs unmount
  stack chunks/locals được GC quản lý trên heap
~~~

Đây chỉ là **mô hình khả năng mở rộng**, không phải công thức memory hay lời hứa rằng số carriers luôn bằng `Runtime.availableProcessors()` hoặc VT chỉ tốn 1 KiB. JVM còn tạo GC/JIT/service threads, còn scheduler/native code có thể dùng additional platform threads.

### VT vẫn tiêu thụ heap và giữ object sống

Một call stack parked có thể tham chiếu tới object lớn ở local:

~~~java
static void handle() throws InterruptedException {
    byte[] payload = new byte[2_000_000];
    Thread.sleep(100);
    consume(payload);
}
~~~

Đây là **mảnh code minh họa** với `consume` do application định nghĩa. Vì `payload` cần được dùng **sau khi sleep**, object 2 MB vẫn phải sống qua khoảng chờ, dù carrier có thể đã được giải phóng. Với 10.000 VT giữ mỗi VT một mảng 2 MB, riêng payload lên đến **khoảng 20 GB**, chưa tính overhead và stacks.

Tạo nhiều VT chỉ rẻ khi mỗi task thực sự có footprint vừa phải. Nên theo dõi **heap live set, request buffering, ThreadLocal state, số tasks đang chờ, scheduler queues** chứ không chỉ đếm native OS threads.

### Virtual stack chunks và GC roots

Theo JEP 444, virtual-thread stacks trên heap **không được đối xử giống native platform stacks như GC roots**. Những object mà frames của một continuation reachable đang tham chiếu tới vẫn có thể reachable qua **heap object graph**. Không suy luận “VT đã unmount nên object trong local được tự động GC”.

### StackOverflowError vẫn có thể xảy ra

VT có khả năng stack grow/shrink nhưng không có **stack vô hạn**. Đệ quy sâu vẫn có thể ném `StackOverflowError`.

JEP 444 còn mô tả một **giới hạn JDK 21/G1**: implementation khi ấy không hỗ trợ humongous stack chunk objects; nếu chunk trở nên lớn cỡ nửa G1 region (có thể nhỏ như 512 KiB), có thể bị `StackOverflowError`. Đây là **chi tiết version/collector-specific**, không được áp thành luật bất biến của mọi JDK/JVM.

`-Xss` có liên quan đến stack size/limits theo HotSpot, nhưng **không có nghĩa** `-Xss1m` đặt riêng 1 MiB native stack cho mỗi VT như OS platform thread. Phải phân biệt limit cấu hình với cách representation/cấp phát thực.

## 7. Ví dụ Java 21 hoàn chỉnh: 1.200 virtual threads cùng chờ latch

Tạo file **`VirtualThreadParkingDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class VirtualThreadParkingDemo {
    public static void main(String[] args) throws Exception {
        final int tasks = 1200;
        CountDownLatch allWaiting = new CountDownLatch(tasks);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < tasks; i++) {
                futures.add(executor.submit(() -> {
                    boolean virtual = Thread.currentThread().isVirtual();
                    allWaiting.countDown();
                    release.await();
                    return virtual;
                }));
            }
            allWaiting.await();
            System.out.println("tasksReady=" + tasks);
            release.countDown();

            int completed = 0;
            boolean allVirtual = true;
            for (Future<Boolean> future : futures) {
                allVirtual &= future.get();
                completed++;
            }
            System.out.println("completed=" + completed);
            System.out.println("allVirtual=" + allVirtual);
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 21 VirtualThreadParkingDemo.java
java VirtualThreadParkingDemo
~~~

**Stdout dự đoán theo API Java 21 (hãy biên dịch bằng `javac --release 21` và chạy trên JDK 21+ để tự kiểm chứng):**

~~~text
tasksReady=1200
completed=1200
allVirtual=true
~~~

Ý nghĩa:

- `newVirtualThreadPerTaskExecutor()` tạo **một virtual thread cho từng task**, chứ không tạo pool 1.200 OS threads.
- Mỗi task báo đã tới latch qua `allWaiting.countDown()` rồi chờ `release.await()`.
- Chờ bằng cơ chế `CountDownLatch` được JDK hỗ trợ cho VT có thể làm VT **unmount**, nhường carrier cho task khác.
- Main chỉ in `tasksReady=1200` sau khi tất cả tasks đã tới điểm đó.
- Sau `release.countDown()`, `Future.get()` đợi từng task hoàn tất và nhận kết quả `Thread.currentThread().isVirtual()`.
- `try-with-resources` bảo đảm executor được đóng đúng lifecycle.

**Không suy ra từ stdout rằng JVM dùng chính xác bao nhiêu carriers/OS threads**: ví dụ không đo carrier count. OS/JVM scheduler có thể có parallelism, compensation và thread services khác nhau. Muốn đưa số liệu performance phải đo bằng OS/JFR tools ở workload cụ thể.

Nếu chỉ có JDK 17, source sẽ **không compile với `javac --release 17`** vì API virtual threads chưa tồn tại trong Java SE 17.

## 8. Ví dụ Java 21 hoàn chỉnh: VT vẫn có call stack logic

Tạo file **`VirtualStackTraceDemo.java`**:

~~~java
import java.util.Arrays;

public final class VirtualStackTraceDemo {
    static long descend(int remaining) {
        if (remaining == 0) {
            return Arrays.stream(Thread.currentThread().getStackTrace())
                    .filter(frame ->
                            frame.getMethodName().equals("descend"))
                    .count();
        }
        return descend(remaining - 1);
    }

    public static void main(String[] args) throws Exception {
        Thread worker = Thread.ofVirtual()
                .name("logical-task")
                .start(() -> {
                    System.out.println("virtual=" +
                            Thread.currentThread().isVirtual());
                    System.out.println("logicalFrames=" + descend(5));
                });

        worker.join();
        System.out.println("terminated=" + !worker.isAlive());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 21 VirtualStackTraceDemo.java
java VirtualStackTraceDemo
~~~

**Stdout quan sát trên OpenJDK 24:**

~~~text
virtual=true
logicalFrames=6
terminated=true
~~~

Gọi `descend(5)` lồng sáu lần, tạo call-chain logic ở các giá trị 5, 4, 3, 2, 1, 0. `Thread.currentThread().getStackTrace()` chạy **trong chính VT** và ở JVM thử nghiệm đếm được 6 frames tên `descend`.

`getStackTrace()` có các giới hạn implementation khi stack walking/virtual machine frames; đừng coi mọi JDK bắt buộc hiển thị cùng số frame trong mọi trường hợp. Kết quả **nói rõ một lần chạy**; mục tiêu bài là chứng minh concept VT vẫn có logical call stack, không phải thứ gì đó “không stack”.

`worker.join()` đợi VT kết thúc; `terminated=true` không có nghĩa carrier platform thread của nó bị shutdown.

## 9. Pinning là gì? Tại sao cần phân biệt JDK 21 với JDK 24?

**Pinning** xảy ra khi VT **không thể unmount** khỏi carrier trong một đoạn có blocking operation. Carrier OS thread có thể bị block cùng VT, nên những VTs khác không tận dụng được carrier ấy. Pinning **không nhất thiết làm sai correctness**, nhưng pinning nhiều và lâu có thể làm ứng dụng mất scalability.

### JDK 21–23: synchronized + blocking có thể pin

Ở JDK 21, virtual thread thực hiện blocking operation **trong `synchronized` method hoặc block** có thể giữ carrier bị pin. Ví dụ minh họa:

~~~java
synchronized (lock) {
    readFromRemoteService();  // có thể block lâu
}
~~~

Đoạn này **không độc lập để chạy**, vì `lock` và `readFromRemoteService()` cần do ứng dụng định nghĩa.

Không nên kết luận “mọi synchronized đều xấu”: vùng synchronized rất ngắn để cập nhật state trong memory thường không phải bottleneck. Rủi ro là **thường xuyên và lâu dài** khi block I/O trong monitor. Ở JDK 21, với những chỗ đó, một thiết kế sử dụng `ReentrantLock` có thể tránh pinning monitor-related:

~~~java
lock.lock();
try {
    readFromRemoteService();
} finally {
    lock.unlock();
}
~~~

Ở đây `lock` là `ReentrantLock` do ứng dụng tạo. **Không thay tất cả synchronized một cách máy móc**: phải xét semantics monitor, `wait/notify`, reentrancy, ordering và code exception paths.

### JDK 24+: JEP 491 cải thiện monitor behavior

**JEP 491 — Synchronize Virtual Threads without Pinning** được triển khai trong JDK 24: virtual threads có thể unmount khi block theo các trường hợp Java monitor/synchronization đã được cải thiện. Do đó phát biểu “JDK 24 luôn pin mọi blocking I/O nằm trong synchronized” là **không còn đúng**.

Nhưng **native frames/JNI/foreign calls và một số platform-specific operations** vẫn có thể giữ carrier trong những tình huống khác. Không hứa JDK 24 “xóa sạch mọi pinning”.

| Trường hợp | JDK 21–23 | JDK 24+ |
| --- | --- | --- |
| Blocking dưới Java `synchronized` | Rủi ro monitor pinning | JEP 491 giải quyết các trường hợp monitor tương ứng |
| JNI/native/foreign operation | Có thể giữ carrier | Vẫn có những trường hợp giữ carrier |
| JDK blocking API hỗ trợ unmount, không pinned | Thường nhả carrier | Thường nhả carrier |
| CPU-bound code không block | Tiếp tục dùng carrier | Tiếp tục dùng carrier |

Khi phân tích production, ghi **JDK vendor/build, JVM implementation, OS, thư viện native và JFR settings**. Không sao chép bảng pinning từ tài liệu JDK 21 như định luật vĩnh viễn.

## 10. Virtual threads không thay cho CPU cores hoặc resource limits

Virtual threads được thiết kế để tăng **concurrency của các task thường xuyên chờ I/O**, không làm một phép tính CPU-bound chạy nhanh hơn tự động.

Một hệ thống có 8 CPU cores không thể cùng thực thi một triệu Java CPU-bound tasks ở đúng một thời điểm chỉ vì tạo một triệu virtual threads. Chúng vẫn phải chia sẻ carriers, scheduler và 8 cores đó.

Ví dụ một request làm 2 ms CPU rồi chờ database 100 ms: platform-thread-per-request có thể giữ OS thread lúc chờ; VT có thể nhường carrier khi I/O hỗ trợ unmount, giúp OS thread phục vụ task khác. Nhưng request ấy **vẫn cần thời gian database**; throughput có thể tăng khi nút thắt trước đây là OS threads, còn single-request latency không nhất thiết giảm.

**Downstream vẫn là bottleneck**: 10 DB connections không trở thành 10.000 connections. Nếu 10.000 VTs gọi database cùng lúc thì connection pool, rate limiting, file descriptors, socket buffers và heap retention vẫn giới hạn khả năng phục vụ.

### Bounded concurrency thay vì pooling VTs

JEP 444 khuyên **virtual thread per task**, không pool virtual threads vì chúng là đơn vị task nhẹ, không phải expensive OS worker để tái sử dụng.

~~~java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    // Mỗi submit tạo một VT mới cho task.
    var future = executor.submit(() -> performOperation());
    var result = future.get();
    process(result);
}
~~~

Đoạn này là **mảnh minh họa**, phải có `performOperation()`/`process()` thật. Với những downstreams có giới hạn, dùng `Semaphore`, connection pool hoặc backpressure:

~~~java
semaphore.acquire();
try {
    return callDatabase();
} finally {
    semaphore.release();
}
~~~

`semaphore` và `callDatabase()` ở đây thuộc application và code cần xử lý interruption đúng. VT chờ semaphore có thể nhả carrier, nhưng giới hạn concurrency của downstream vẫn phải được duy trì.

## 11. ThreadLocal, object retention và memory budget

Virtual threads hỗ trợ `ThreadLocal` giống platform threads, nhưng một “cache per thread” trở thành vấn đề khi **mỗi task có thread riêng**.

~~~java
private static final ThreadLocal<byte[]> SCRATCH =
        ThreadLocal.withInitial(() -> new byte[1_000_000]);
~~~

Đây là **field minh họa**, không phải chương trình chạy. Một platform thread pool 32 workers có thể tạo khoảng 32 buffers nếu mỗi worker truy cập một lần. Với 20.000 VTs cùng sử dụng ThreadLocal này, có thể có 20.000 buffers tương đương **khoảng 20 GB bytes**, chưa tính objects/stack chunks.

Thay vì cache tài nguyên lớn theo thread, cân nhắc **immutable shared objects, bounded pools, request-scoped data và explicit cleanup**. Thread-local state trong carrier **không** được mặc nhiên xem là ThreadLocal của VT. `Thread.currentThread()` trong VT trả VT, và thread-local namespaces không trộn với carrier.

Tính toán ngân sách:

~~~text
Java heap:
    request payloads, business objects
  + virtual Thread objects
  + stack chunks / continuation state
  + ThreadLocals và Futures/scheduler metadata

Native/process memory:
    carrier OS thread stacks
  + GC/JIT/VM service threads và internal structures
  + JNI/native allocations
  + mapped/direct buffers và OS resources
~~~

Không cộng các số reserved/committed/RSS bừa bãi; chúng có ý nghĩa khác nhau. **Không lấy `N_virtual × -Xss` làm công thức bắt buộc của native stack usage.**

## 12. Quan sát virtual threads: vì sao `ps` và ThreadMXBean là chưa đủ?

OS biết **native threads** (carriers, GC/JIT workers, native library threads...), không biết tất cả virtual threads như một OS thread riêng. Vì vậy:

~~~text
OS native thread count  != logical virtual-thread count
ThreadMXBean count       != số virtual threads đang tồn tại
~~~

JEP 444 ghi rằng trong JDK 21 `ThreadMXBean` tập trung quản lý platform threads và không liệt kê mọi virtual threads như thread manager truyền thống. Nhiều API `ThreadGroup` cũng có giới hạn vì virtual threads không được xem là active group members theo kiểu cũ.

### `jcmd` virtual-thread-aware dumps

Trên JDK 21+, có thể dùng `Thread.dump_to_file`:

~~~text
jcmd PID Thread.dump_to_file -format=text threads.txt
jcmd PID Thread.dump_to_file -format=json threads.json
~~~

Chữ `PID` là chỗ **thay bằng process ID thật**, không phải câu lệnh shell có biến PID mặc định. Cần JDK tools/quyền attach phù hợp. Dump này có thể biểu diễn logical virtual threads/stack traces tốt hơn nhiều dạng legacy dumps.

Với rất nhiều VTs, dump có thể **rất lớn** và ảnh hưởng CPU/memory/disk. Stack traces còn có thể tiết lộ class names/methods/metadata nhạy cảm; kiểm soát quyền truy cập.

### JFR virtual-thread events

Các events JDK 21 có thể dùng:

- `jdk.VirtualThreadStart`/`jdk.VirtualThreadEnd`: theo dõi vòng đời; thường **không bật mặc định** do event volume.
- `jdk.VirtualThreadPinned`: quan sát blocking khi pinned, tùy thresholds/settings (tài liệu JDK 21 mô tả threshold mặc định khoảng 20 ms).
- `jdk.VirtualThreadSubmitFailed`: lỗi khi schedule/start/unpark một VT.

Đọc từ recording đã có:

~~~bash
jfr print --events jdk.VirtualThreadPinned,jdk.VirtualThreadSubmitFailed recording.jfr
~~~

File `recording.jfr` phải được tạo trước. Event availability, settings và thresholds phụ thuộc JDK version; nếu event không xuất hiện, không khẳng định tuyệt đối “không pinning”.

Trên JDK 21, property:

~~~bash
java -Djdk.tracePinnedThreads=full MyMain
~~~

có thể giúp phát hiện một số pinning call stacks (`MyMain` cần là main class có thật). Với JDK 24+, JEP 491 đã thay đổi monitor behavior; không dựa vào absence of trace làm bằng chứng scheduler hoàn hảo.

## 13. Một bảng thiết kế cho tình huống 10.000 requests I/O

Giả sử có 10.000 requests chờ network trong khi mỗi request chỉ cần ít CPU.

| Tiêu chí | Platform-thread-per-request | Virtual-thread-per-request |
| --- | --- | --- |
| Thread cho mỗi request logic | Platform thread | Virtual thread |
| Native OS stack per request | Thường phải giữ | Không yêu cầu độc lập per VT |
| Blocking I/O | OS thread bị chiếm trong kiểu truyền thống | Có thể unmount VT và reuse carrier |
| Code tuần tự với `try/finally` | Tự nhiên | Cũng tự nhiên |
| Memory cho request context | Heap objects và platform stack | Heap objects và VT stack chunks |
| Downstream connection limit | Vẫn phải quản lý | **Vẫn phải quản lý** |
| CPU-bound throughput | Bị giới hạn CPU | **Vẫn bị giới hạn CPU** |
| JVM pinning pitfalls | Không theo virtual scheduler | Cần xét JDK/native/synchronized version |
| Profiling | Native+Java thread tools | Virtual-thread-aware dumps/JFR cần thiết |

**Không thể chọn VT chỉ từ số native stacks ít hơn**. Phải đo end-to-end throughput, p99/p99.9, RSS/heap live set, CPU/carriers, executor task backlog, connection pools và lỗi timeout/OOM trong workload thật.

### Khi nào VT có ích nhất?

- Nhiều concurrent tasks chủ yếu **chờ I/O** có APIs hỗ trợ suspension.
- Application cần code đồng bộ dễ đọc, một task một thread, cần mở rộng vượt giới hạn OS threads truyền thống.
- Resource limits (DB, sockets, heap) có backpressure rõ ràng.

### Khi nào VT không cứu được?

- CPU-bound hot loops đã saturate cores.
- Database đã đầy tải và connection pool chờ dài.
- Mỗi VT giữ payload lớn quá lâu, khiến heap/GC pressure cao.
- Native code pin carriers lâu ở nơi blocking thường xuyên.
- Hệ thống dùng async framework tối ưu sẵn với rất ít OS threads và không có vấn đề thread scarcity.

## 14. Những hiểu sai điển hình

1. **“Mỗi instance `Thread` đều có native stack riêng.”** Sai với VTs.
2. **“Virtual thread không có call stack.”** Sai; vẫn có logical Java frames và stack traces.
3. **“VT không cần OS thread để chạy CPU.”** Sai; cần carrier.
4. **“Carrier của VT cố định.”** Sai; có thể đổi khi resume.
5. **“Thread.currentThread() trả carrier.”** Sai; trả virtual thread.
6. **“VT đang park luôn giữ OS thread.”** Không khi operation được hỗ trợ unmount và không pinned.
7. **“Mọi blocking call đều unmount.”** Sai; native/platform-specific/pinning.
8. **“Virtual-thread stack là native OS stack bằng `-Xss`.”** Sai.
9. **“VT stack chunk không nằm trên heap.”** Sai với HotSpot/JEP 444.
10. **“Heap không tăng khi tạo hàng nghìn parked VTs.”** Sai; chunks, locals, payloads, metadata có footprint.
11. **“VT không bao giờ StackOverflowError.”** Sai.
12. **“VT chạy CPU-bound nhanh hơn platform thread.”** Không phải mục tiêu.
13. **“Java 17 hỗ trợ stable `Thread.ofVirtual()`.”** Sai; Java 21.
14. **“JDK 24 vẫn luôn pin trong synchronized.”** Sai nói chung do JEP 491.
15. **“JDK 24 không còn bất kỳ pinning nào.”** Sai; native cases vẫn tồn tại.
16. **“Cần pool các VT giống OS threads.”** Không phù hợp mô hình virtual-thread-per-task.
17. **“ThreadLocal của carrier là ThreadLocal của VT.”** Sai.
18. **“Tăng số VTs làm tăng số DB connections.”** Sai.
19. **“ThreadMXBean đếm tất cả virtual threads trong JDK 21.”** Sai.
20. **“OS thread count thấp chứng minh heap memory thấp.”** Sai.
21. **“Một VT có thể chạy CPU đồng thời trên hai carriers.”** Sai; một VT tại một thời điểm chỉ có một execution flow.
22. **“Stack chunks được GC quản lý nên mọi object local lúc VT park đều được GC.”** Sai.
23. **“Thấy `logicalFrames=6` tức VT đã unmount sáu lần.”** Sai; đó là call frames logic, không phải scheduler transitions.
24. **“Java API hứa số carrier threads cố định.”** Sai.

## 15. Bài tập tự kiểm tra

1. Một JVM có 10.000 VTs và 32 carriers, có cần 10.000 native stack reservations per VT không? **Không**.
2. VT đang chạy CPU thực sự chạy ở đâu? **Trên carrier platform/OS thread**.
3. VT bị unmount có mất locals/Java frames không? **Không**, runtime giữ continuation state để resume.
4. Heap stack chunks có nghĩa VT không thể stack overflow không? **Không**.
5. Tính năng stable virtual threads được phát hành ở Java nào? **Java 21, JEP 444**.
6. `--release 21` trên JDK 24 làm runtime thành JDK 21 không? **Không**.
7. `newVirtualThreadPerTaskExecutor()` tạo 1 OS thread cho mỗi submit không? **Không**.
8. `Thread.currentThread().isVirtual()` trong VT trả gì? **True**.
9. Một socket I/O có hỗ trợ unmount thì carrier có thể phục vụ VT khác trong lúc chờ không? **Có**.
10. Mọi JNI operation có đảm bảo unmount không? **Không**.
11. JDK 21 blocking trong synchronized có thể pin carrier không? **Có**.
12. JDK 24 cải thiện điều đó bằng JEP nào? **JEP 491**.
13. JEP 491 đã loại bỏ mọi native-related pinning chưa? **Chưa**.
14. Chạy 1.200 tasks trong ví dụ có chứng minh đúng 2 carriers không? **Không**, không đo carrier count.
15. Vì sao không dùng `ThreadMXBean.getThreadCount()` làm toàn bộ VT count ở JDK 21? **MXBean chủ yếu quản lý platform threads**.
16. Để xem stacks của VTs bằng JDK tools dùng lệnh gì? **`jcmd PID Thread.dump_to_file -format=json ...`**.
17. 10.000 VT mỗi VT giữ `byte[2_000_000]` qua sleep tạo memory pressure cỡ nào? **Khoảng 20 GB bytes payload trước overhead**.
18. Khi DB chỉ có 20 connections, tạo 10.000 VTs có tự tăng DB throughput vô hạn không? **Không**.
19. VTs có lợi chủ yếu với CPU-bound hay I/O-blocking concurrency? **I/O-blocking concurrency**.
20. Câu đúng hơn là “VT không có stack” hay “VT có Java stack logic nhưng không có native OS stack riêng cố định”? **Câu thứ hai**.

## 16. Tài liệu tham khảo chính thống

- **OpenJDK JEP 444 — Virtual Threads** (final in JDK 21, stack chunks, GC interactions): https://openjdk.org/jeps/444
- **Oracle Java SE 21 — Virtual Threads**, carrier scheduling, debugging, pinning và hướng thiết kế: https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html
- **Java SE 21 `Thread` API**, `Thread.ofVirtual()` và `isVirtual()`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html
- **Java SE 21 `Executors` API**, virtual-thread-per-task executor: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html
- **OpenJDK JEP 425/436**, preview JDK 19/20: https://openjdk.org/jeps/425 và https://openjdk.org/jeps/436
- **OpenJDK JEP 491 — Synchronize Virtual Threads without Pinning (JDK 24):** https://openjdk.org/jeps/491
- **Oracle Java SE 24 migration changes** ghi nhận JEP 491: https://docs.oracle.com/en/java/javase/24/migrate/significant-changes-jdk-24.html
- **Oracle JDK 21 `jcmd`**, Thread.dump_to_file: https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html
- **JVMS 21 §2.5 Runtime Data Areas**, abstract JVM stacks: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.5

**Kết luận:** Virtual thread là **Java thread thật ở cấp ngữ nghĩa**: có locals, call stack, stack traces và exception handling. Nhưng từ JDK 21, nó **không cần một OS thread/native stack dành riêng cho cả lifetime**; JVM mount VT lên **carrier** khi chạy, có thể lưu continuation/stack chunks trên **Java heap** và trả carrier lại scheduler khi VT chờ hỗ trợ unmount. Sơ đồ “mỗi Java thread = một native stack” chỉ nên dùng cho platform threads. Sự mở rộng concurrency không loại bỏ giới hạn CPU, heap, native pinning, downstream connections và cần đọc đúng khác biệt **JDK 21 so với JDK 24+**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-threads-and-synchronization-003">Monitor của đối tượng và tính tái nhập của synchronized</a>

<details>
<summary>Xem chi tiết</summary>

# Monitor của đối tượng và tính tái nhập của synchronized

## 1. Tổng quan và mục tiêu học tập

Trong Java, **synchronized** giúp nhiều thread phối hợp quyền truy cập vào dữ liệu dùng chung thông qua một **monitor** gắn với **một object cụ thể**. Một thread phải có quyền sở hữu monitor tương ứng mới được vào vùng mã đồng bộ hóa do monitor đó bảo vệ. Khi một thread đã sở hữu monitor, nó có thể **tái nhập (reenter)** cùng monitor nhiều lần, thay vì tự chặn chính mình. Khả năng này gọi là **reentrant synchronization**.

Ba ý tưởng quan trọng nhất:

1. **Monitor gắn với object identity, không gắn với tên phương thức.** Hai khối synchronized chỉ loại trừ lẫn nhau nếu đang dùng **chính cùng một monitor object**.
2. **Synchronized là reentrant.** Thread đang giữ monitor có thể vào lại synchronized method/block sử dụng cùng object, ngay cả qua chuỗi gọi methods hoặc đệ quy. JVM theo dõi số lần vào chưa được cân bằng bằng số lần thoát.
3. **Khóa monitor không chỉ là mutual exclusion.** Theo Java Memory Model (JMM), thao tác **unlock monitor M happens-before mọi lần lock M tiếp theo**; đây là một cơ chế visibility/ordering quan trọng đối với shared mutable state.

Sau bài này, bạn sẽ biết:

- Monitor là gì và tại sao mọi Java object đều có thể dùng làm monitor.
- **Owner thread**, **reentrancy/entry count**, cách acquire và release monitor.
- Phân biệt **synchronized instance method**, **static synchronized method** và **synchronized(lock)**.
- Vì sao một synchronized method có thể gọi một synchronized method khác của cùng object mà không deadlock.
- Cách Java compiler/JVM biểu diễn **monitorenter/monitorexit** và cờ **ACC_SYNCHRONIZED**.
- Sự khác biệt giữa **BLOCKED** khi chờ vào monitor và **WAITING/TIMED_WAITING** trong Object.wait().
- Tại sao **wait()** nhả toàn bộ số lần vào monitor đích rồi **reacquire** trước khi trả về, còn notify()/notifyAll() **không tự nhả** monitor của thread đang phát tín hiệu.
- Hậu quả của IllegalMonitorStateException, NullPointerException, exception exits và những hiểu nhầm thường gặp.
- Mô hình happens-before, visibility, atomicity, lock identity, deadlock và nguyên tắc thiết kế lock scope.
- Biết dùng Java 17+, Thread.holdsLock, jcmd, jstack và JFR để quan sát, phân tích sự cố monitor contention.

**Phạm vi:** ngữ nghĩa Java SE/JLS/JVMS 17. Những chi tiết tối ưu hóa monitor của **HotSpot** như object header, lock records, monitor inflation, spin hay biased locking là implementation details và có thể đổi giữa các JDK versions. Không xem chúng là giao diện ổn định được Java language bảo đảm.

## 2. Monitor là gì?

Một object Java có **một monitor liên kết với chính object identity ấy**. Monitor có cơ chế khái niệm:

- **Owner:** thread hiện đang giữ monitor, hoặc chưa có owner.
- **Entry/hold count:** số lần owner đã acquire monitor nhưng chưa release tương ứng.
- **Entry contention:** các threads khác muốn acquire cùng monitor có thể phải chờ.
- **Wait set:** tập các threads đã gọi Object.wait() hợp lệ trên object đó và đang chờ notification/interrupt/timeout/spurious wakeup theo quy tắc Java.

Mô hình:

~~~text
                Monitor liên kết với object LOCK
              +--------------------------------------+
 Thread T1 -->| Owner: T1                            |
              | Entry count: 2                       |
              | Wait set: {...}                      |
              +--------------------------------------+
                   ^
                   |
 Thread T2 --------+-----> BLOCKED nếu T2 muốn lock LOCK

T1 gọi synchronized(LOCK) tiếp:
   T1 đã là owner -> entry count 2 -> 3, không tự block.

T1 thoát một synchronized:
   entry count 3 -> 2, vẫn giữ monitor.

T1 thoát đủ số lần:
   entry count 1 -> 0, không còn owner.
   Các thread khác mới có thể tranh quyền vào.
~~~

**Entry count là mô hình ngữ nghĩa**, không phải một trường int có public getter trên java.lang.Object. Các JVM có thể biểu diễn monitor ownership/reentrancy khác nhau ở native data structures; Java chỉ cung cấp behavior đúng.

### 2.1. Monitor gắn với object, không tự động gắn với “dữ liệu quan trọng”

~~~java
// Mã minh họa trong method:
Object a = new Object();
Object b = new Object();
synchronized (a) {
    // Sở hữu monitor của a; chưa hề sở hữu monitor b.
}
~~~

Cùng loại **Object**, nhưng **a != b** nên là hai monitor độc lập. Nếu một thread đồng bộ trên a còn thread khác đồng bộ trên b, chúng không chặn lẫn nhau chỉ vì dùng keyword synchronized.

Điều này rất quan trọng khi thiết kế private lock fields và khi đánh giá synchronized instance/static methods.

### 2.2. Mỗi monitor chỉ có một owner tại một thời điểm

Khi T1 đang sở hữu monitor M với hold count dương, T2 không thể đồng thời trở thành owner của M. Nhưng T1 vẫn có thể reenter M. Quy tắc này tạo **mutual exclusion cho critical sections dùng cùng M**.

Không có nghĩa mọi dữ liệu của object sẽ được tự động bảo vệ. Nếu thread khác truy cập một mutable field **không qua cùng monitor hoặc cơ chế synchronization tương đương**, có thể có data race.

## 3. Ba dạng synchronized trong Java

### 3.1. Instance synchronized method

~~~java
final class Counter {
    private int value;

    public synchronized void increment() {
        value++;
    }

    public synchronized int get() {
        return value;
    }
}
~~~

Monitor được sử dụng là **this**, tức instance Counter cụ thể. Với:

~~~java
Counter first = new Counter();
Counter second = new Counter();
~~~

Thread khóa first và thread khác khóa second có thể chạy đồng thời vì đây là hai object monitors khác nhau. Hai threads cùng gọi first.increment() thì phải tranh monitor của first.

**Synchronized instance method không tự bảo vệ static field bằng một global class lock.** Nó dùng monitor **this**.

### 3.2. Static synchronized method

~~~java
final class GlobalCounter {
    private static int value;

    public static synchronized void increment() {
        value++;
    }
}
~~~

Monitor được sử dụng là **GlobalCounter.class**, tức Class mirror object biểu diễn runtime class **GlobalCounter** được định nghĩa bởi class loader tương ứng.

Vì vậy **static synchronized** không tranh chính monitor của một instance GlobalCounter thông thường. Một instance synchronized method và một static synchronized method của cùng class sử dụng **hai monitor khác nhau**; chúng không bảo vệ lẫn nhau chỉ do cùng có synchronized.

**Lưu ý class loaders:** hai runtime classes có cùng binary name nhưng do hai defining class loaders khác nhau định nghĩa có thể có hai Class objects, hai static states và hai monitors riêng biệt.

### 3.3. Synchronized statement/block

~~~java
final class Ledger {
    private final Object lock = new Object();
    private long balance;

    void add(long amount) {
        synchronized (lock) {
            balance += amount;
        }
    }

    long balance() {
        synchronized (lock) {
            return balance;
        }
    }
}
~~~

Ở đây lock object là **private final lock**, không phải Ledger.this. Đây thường là cách tốt để hạn chế code bên ngoài vô tình acquire monitor bảo vệ internal state. Tất cả truy cập balance liên quan phải tuân cùng synchronization policy.

**final chỉ cố định tham chiếu lock field**, không biến những fields khác thành thread-safe. Mọi reads/writes của state được bảo vệ cần phối hợp đúng monitor.

### 3.4. Bảng so sánh

| Cú pháp | Monitor thực sự acquire | Loại code | Ghi nhớ |
| --- | --- | --- | --- |
| **synchronized void m()** | this | Instance method | Mỗi instance có monitor riêng |
| **static synchronized void m()** | DefiningClass.class | Static method | Không phải monitor của instance |
| **synchronized (this) { ... }** | this | Statement/block | Có thể reenter với instance synchronized method |
| **synchronized (lock) { ... }** | object được biểu thức lock tham chiếu | Statement/block | Phải dùng cùng object identity |
| **synchronized (SomeClass.class) { ... }** | Class mirror object | Statement/block | Có thể phối hợp với static synchronized của class ấy |

## 4. Synchronized là reentrant: quy tắc vào/ra monitor

Giả sử T1 đang ở:

~~~java
synchronized (lock) {
    // Lần acquire đầu tiên.
    synchronized (lock) {
        // T1 lại acquire đúng cùng monitor.
    }
}
~~~

T1 không cần chờ chính nó release lock lần đầu. Một mô hình entry count:

~~~text
Ban đầu:                owner = none; count = 0

T1 vào outer block:      owner = T1;   count = 1
T1 vào inner block:      owner = T1;   count = 2
T1 thoát inner block:    owner = T1;   count = 1
T1 thoát outer block:    owner = none; count = 0
~~~

Chỉ khi **count về 0**, T1 mới nhả quyền sở hữu hoàn toàn. Không có semantic guarantee về fairness hoặc thứ tự thread tiếp theo nhận monitor.

### 4.1. Reentrancy qua chuỗi lời gọi phương thức

~~~java
final class BankAccount {
    private long balance;

    public synchronized void deposit(long amount) {
        validate(amount);
        applyDeposit(amount);
    }

    private synchronized void applyDeposit(long amount) {
        balance += amount;
    }

    private synchronized void validate(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("negative amount");
        }
    }

    public synchronized long getBalance() {
        return balance;
    }
}
~~~

**deposit**, **validate** và **applyDeposit** cùng lock **this**. Khi deposit đang sở hữu monitor, nó có thể gọi validate rồi applyDeposit vì **chính thread ấy là owner**. Mỗi lần vào một synchronized method có accounting entry/exit theo ngữ nghĩa, nhưng không deadlock chỉ vì gọi nested synchronized methods trên cùng receiver.

**Không hiểu nhầm:** reentrancy chỉ miễn block cho **chính owner thread trên cùng monitor**. Thread T2 vẫn phải chờ.

### 4.2. Reentrancy khác recursion

**Recursion** nghĩa là method tự gọi lại; **reentrancy của lock** nghĩa là cùng thread có thể acquire lại cùng lock. Có thể xảy ra một cái mà không có cái kia:

- Hai synchronized methods khác nhau trên cùng object gọi nhau: **lock reentrancy**, không phải recursion.
- Recursive pure function không dùng synchronized: recursion nhưng không dùng monitor.
- Recursive synchronized method: cả recursion và lock reentrancy.
- Hai lock objects khác nhau trong nested blocks: nested locking nhưng không reenter **cùng** monitor.

## 5. Java 17 runnable demo #1: chứng minh tái nhập

Tạo file **ReentrantMonitorDemo.java**:

~~~java
public class ReentrantMonitorDemo {
    static final class Service {
        private int count;

        synchronized void outer() {
            inner(); // Reenter this monitor.
            synchronized (this) {
                inner(); // Another nested reentrance.
            }
        }

        private synchronized void inner() {
            if (!Thread.holdsLock(this)) {
                throw new AssertionError("monitor not held");
            }
            count++;
        }

        synchronized int getCount() {
            return count;
        }
    }

    public static void main(String[] args) {
        Service service = new Service();

        System.out.println("before="
                + Thread.holdsLock(service));

        synchronized (service) {
            System.out.println("inside="
                    + Thread.holdsLock(service));
            service.outer();
        }

        System.out.println("after="
                + Thread.holdsLock(service));
        System.out.println("count=" + service.getCount());
    }
}
~~~

Biên dịch/chạy với JDK 17+:

~~~bash
javac --release 17 -encoding UTF-8 ReentrantMonitorDemo.java
java ReentrantMonitorDemo
~~~

**Stdout chính xác:**

~~~text
before=false
inside=true
after=false
count=2
~~~

Đường đi:

1. Ở main trước block, thread chưa sở hữu Service monitor.
2. Vào synchronized(service), owner main, count khái niệm = 1.
3. Gọi **service.outer()** là synchronized instance method, reenter cùng service monitor.
4. **outer()** gọi **inner()**, tiếp tục reenter monitor; Thread.holdsLock(this) trả true.
5. Vào synchronized(this) ngay trong outer, sau đó lại gọi inner(): vẫn là owner thread, không bị tự khóa.
6. Những scopes/methods thoát theo thứ tự ngược; ngoài block main không còn giữ monitor.
7. inner được gọi đúng hai lần, count=2.

**Thread.holdsLock(Object obj)** chỉ trả **boolean** thread hiện tại có giữ monitor obj không; nó không trả hold count, không kiểm tra monitor owner của thread khác và không phải tool đo lock contention.

## 6. Một monitor dùng cho nhiều phương thức, nhưng không tự khóa toàn bộ object

Một bug điển hình:

~~~java
final class Box {
    private int value;

    synchronized void increment() {
        value++;
    }

    int unsafeRead() {
        return value;
    }
}
~~~

**increment** khóa this nhưng **unsafeRead** không khóa hoặc dùng visibility mechanism khác. Nếu một thread đọc trong khi thread khác ghi, đây có thể là **data race**. Synchronized không tự áp dụng bảo vệ cho mọi access tới fields của object.

Sửa một cách đơn giản:

~~~java
synchronized int safeRead() {
    return value;
}
~~~

Đây là **một đoạn method để thêm vào Box**, không phải file Java độc lập. Cả increment và safeRead đều acquire monitor của cùng Box instance.

### 6.1. Một field protected bởi hai locks khác nhau không đủ

~~~java
private final Object writeLock = new Object();
private final Object readLock = new Object();
private int value;

void update() {
    synchronized (writeLock) {
        value++;
    }
}

int read() {
    synchronized (readLock) {
        return value;
    }
}
~~~

Dù cả hai methods đều có synchronized blocks, **writeLock != readLock**. Chúng không thiết lập mutual exclusion/happens-before qua cùng monitor. Đây là code **minh họa bug**, không phải một class độc lập hoàn chỉnh. Cần sử dụng **cùng lock** hoặc một chiến lược Java concurrency đúng khác.

## 7. Java 17 runnable demo #2: an toàn tăng đếm với nhiều threads

Tạo file **SynchronizedCounterDemo.java**:

~~~java
public class SynchronizedCounterDemo {
    static final class Counter {
        private int value;

        synchronized void increment() {
            doIncrement(); // Reentrant synchronized call.
        }

        private synchronized void doIncrement() {
            value++;
        }

        synchronized int get() {
            return value;
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Counter counter = new Counter();
        Thread[] threads = new Thread[4];

        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 5_000; i++) {
                    counter.increment();
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("count=" + counter.get());
        System.out.println("expected=" + (4 * 5_000));
        System.out.println("correct="
                + (counter.get() == 4 * 5_000));
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 SynchronizedCounterDemo.java
java SynchronizedCounterDemo
~~~

**Stdout chính xác nếu chương trình hoàn thành bình thường:**

~~~text
count=20000
expected=20000
correct=true
~~~

Giải thích:

- Bốn threads, mỗi thread gọi 5.000 lần increment.
- **increment()** acquire Counter.this và reenter khi gọi **doIncrement()**; cùng owner thread được phép thực thi.
- Phép **value++** được bảo vệ bởi một monitor duy nhất nên không có hai threads cùng sửa biến theo hai critical sections chồng nhau.
- **Thread.join()** chờ tất cả worker kết thúc; theo JMM, hoạt động trong thread được join happens-before thread khác phát hiện việc join hoàn thành thành công.
- **counter.get()** còn acquire monitor tương ứng, nên đọc sau completion an toàn.
- Thứ tự chạy threads không xác định, nhưng tổng kết quả sau join là xác định.

**Không dùng code này để chứng minh monitor scheduling công bằng.** Thread order, số lần bị BLOCKED và elapsed runtime thay đổi qua các lần chạy.

## 8. Synchronized method vs monitorenter/monitorexit trong class file

Có hai cách mã hóa đồng bộ hóa quan trọng trong JVMS.

### 8.1. Synchronized statement

~~~java
void work(Object lock) {
    synchronized (lock) {
        System.out.println("work");
    }
}
~~~

Java compiler thông thường sinh bytecode có **monitorenter** và **monitorexit**, cùng **exception handler** để đảm bảo thoát monitor khi exception đi qua block. Conceptually:

~~~text
aload lock
monitorenter
  ...
monitorexit
goto end

exceptionHandler:
  monitorexit
  athrow

end:
  return
~~~

Sơ đồ lược bỏ stack/locals chính xác, offsets và exception tables; không phải javap output cố định. Điều đúng theo JVMS là **monitorenter** tăng entry count nếu thread đã own monitor, còn **monitorexit** giảm count; khi về 0 owner được nhả.

### 8.2. Synchronized method

~~~java
synchronized void task() {
    System.out.println("task");
}
~~~

Một synchronized method trong class file mang flag **ACC_SYNCHRONIZED**. JVM xử lý monitor acquire/release **implicitly** khi invoke/return/abrupt completion của method; không nhất thiết sinh monitorenter và monitorexit trong bytecode method body.

**Điểm rất hay bị dạy sai:** “Mọi synchronized method sẽ có cặp monitorenter/monitorexit trong javap -c”. Với synchronized method thông thường, hãy nhìn **ACC_SYNCHRONIZED**, không đòi phải thấy hai opcodes trong method instructions.

### 8.3. Cách quan sát bytecode

Sau khi biên dịch file ReentrantMonitorDemo.java:

~~~bash
javap -c -v 'ReentrantMonitorDemo$Service'
~~~

Cú pháp quotes phù hợp terminal Unix-like. Trên PowerShell, single quote quanh class name có dấu $ cũng giúp tránh variable expansion. Xem phần **flags: ACC_SYNCHRONIZED** cho methods outer/inner/getCount và **monitorenter/monitorexit** quanh explicit synchronized(this) block.

**Lưu ý:** javap chỉ cho thấy Java bytecode/class-file metadata, không tiết lộ nguyên trạng HotSpot native lightweight lock records hay CPU-level locking instructions do JIT sinh.

## 9. Java Memory Model: monitor không chỉ loại trừ lẫn nhau

Theo JLS §17.4.5, một **unlock action trên monitor M synchronizes-with mỗi lock action tiếp theo trên M**, tạo quan hệ **happens-before**.

Ví dụ:

~~~java
final class SharedState {
    private final Object lock = new Object();
    private int data;

    void publish() {
        synchronized (lock) {
            data = 42;
        } // unlock lock
    }

    int observe() {
        synchronized (lock) { // lock cùng object
            return data;
        }
    }
}
~~~

Nếu thread T1 hoàn thành **publish()** trước khi thread T2 acquire cùng lock để chạy **observe()**, T2 có quan hệ happens-before thích hợp để nhìn cập nhật 42, giả định không có mutation khác và object được sử dụng/publication phù hợp.

### 9.1. Acquire và release semantics

Trực giác:

- **Unlock/release**: publish những writes đã xảy ra trước đó qua synchronization ordering.
- **Subsequent lock/acquire trên cùng M**: sau khi acquire, thread tiếp theo có visibility phù hợp của các writes trước unlock.
- Những accesses phải thực sự được tổ chức theo **cùng monitor object**.
- Synchronization còn đặt constraints lên compiler/CPU reordering để giữ đúng JMM.

**Happens-before không đồng nghĩa “mọi writes trong mọi threads tức thời visible khi synchronized bất kỳ object nào”.** Nếu writer dùng lockA, reader dùng lockB, chỉ hai locks khác nhau chưa cho quan hệ monitor happens-before giữa hai thao tác.

### 9.2. Synchronized có tính chất atomicity ở critical section

Khi **value++** diễn ra trong synchronized(lock) và mọi threads khác cũng theo cùng lock để truy cập state, mutual exclusion làm cho read-modify-write không bị xen kẽ với critical section khác trên cùng monitor.

Nhưng một synchronized method bảo vệ một thao tác **không tự biến chuỗi hai lần gọi riêng rẽ thành transaction**:

~~~java
if (account.getBalance() > 0) {
    account.withdraw(1);
}
~~~

Ngay cả khi getBalance và withdraw từng là synchronized, một thread khác có thể xen giữa hai lời gọi. Nếu cần atomic check-then-act, gộp cả hai trong **một vùng synchronized dùng cùng monitor**, hoặc định nghĩa method nghiệp vụ atomic.

### 9.3. Reentrant monitor không phải distributed lock

Monitor của Java process chỉ điều phối threads trong cùng runtime object model. Nó không tự bảo vệ một bản ghi trong DB khỏi process khác, không thay transaction isolation và không phải hệ distributed locking.

## 10. Khi nào một thread bị BLOCKED?

Giả sử T1 đã acquire monitor lock:

~~~text
T1: synchronized(lock) {
        // xử lý kéo dài
    }

T2: synchronized(lock) {
        // phải chờ T1 release hoàn toàn
    }
~~~

T2 muốn vào critical section sử dụng cùng monitor có thể được quan sát ở trạng thái Java **Thread.State.BLOCKED**. Một thread đang **BLOCKED** chờ monitor entry **không phải** thread đã acquire monitor.

Các điểm khác:

- T1 có thể reenter trong khi count dương.
- T2 chỉ có thể acquire khi monitor hoàn toàn free, không phải khi T1 chỉ thoát một nested block.
- JVM không bảo đảm FIFO fairness.
- Java intrinsic monitor entry không cung cấp cùng public **tryLock(timeout)**/**lockInterruptibly()** như ReentrantLock.
- **Thread.interrupt()** không bảo đảm khiến một thread đang chờ enter intrinsic monitor ngay lập tức ném InterruptedException rồi bỏ việc acquire.

### 10.1. Object.wait() khác BLOCKED-to-enter

**BLOCKED:** muốn vào synchronized region nhưng monitor hiện do thread khác sở hữu.

**WAITING:** sau khi đã sở hữu monitor, thread có thể gọi **wait()** hợp lệ, **nhả monitor ấy** rồi ở wait set cho đến notification/interrupt/spurious wakeup.

**TIMED_WAITING:** có thể xảy ra khi gọi **wait(timeout)** hoặc **sleep(timeout)**; nhưng sleep **không nhả monitor** mà thread đang giữ.

Thread state là **snapshot**; nó có thể thay đổi rất nhanh. Không định nghĩa thuật toán correctness bằng việc lặp getState() và chờ đúng tên state.

## 11. wait()/notify()/notifyAll(): phối hợp trên cùng monitor

Java cung cấp các instance methods trong **Object**:

- **wait()**, **wait(long)**, **wait(long,int)**.
- **notify()**.
- **notifyAll()**.

Thread gọi các phương thức này phải **đang sở hữu monitor của chính receiver object**. Nếu không, thường ném **IllegalMonitorStateException**.

Ví dụ:

~~~java
final Object lock = new Object();

synchronized (lock) {
    lock.wait();
}
~~~

Đây là mã **minh họa**, không phải ví dụ nên chạy nguyên xi: nếu không có tín hiệu/interrupt và không spurious wakeup, có thể chờ vô hạn. Phương thức cũng cần xử lý **InterruptedException**.

### 11.1. wait() thật sự làm gì?

Theo JLS §17.2:

1. Nếu thread không sở hữu monitor receiver, ném IllegalMonitorStateException.
2. Thread được đưa vào **wait set** của receiver object.
3. JVM thực hiện số lần **unlock phù hợp với mọi lần acquire chưa cân bằng trên chính monitor receiver**.
4. Thread chờ đến notification/interrupt/timeout/spurious wakeup theo ngữ nghĩa.
5. Khi được remove khỏi wait set, thread phải **reacquire cùng monitor với số lần sở hữu trước đó** trước khi việc wait hoàn tất/trả về hoặc ném InterruptedException thích hợp.

**Đặc biệt:** nếu thread sở hữu monitor m hai lần do nested synchronized(m), gọi **m.wait()** giải phóng **cả hai levels** cho m. Nó không chỉ giảm count 2→1, nếu thế notifier thread sẽ không thể acquire m và mô hình wait/notify dễ deadlock.

### 11.2. wait() không nhả những monitor khác

~~~java
synchronized (lockA) {
    synchronized (lockB) {
        lockB.wait();
    }
}
~~~

Thread có thể tạm nhả các acquisition của **lockB**, nhưng vẫn giữ **lockA** khi wait. Do đó có thể gây deadlock nếu notifier phải acquire lockA trước khi có thể notify lockB.

**Quy tắc thiết kế:** tránh giữ một lock không liên quan qua lời gọi wait trên monitor khác, trừ khi đã phân tích lock ordering rất kỹ.

### 11.3. notify()/notifyAll() không nhả monitor ngay

~~~text
T1 owns monitor M:
  synchronized(M) {
      condition = true;
      M.notifyAll();        // đưa waiters khỏi wait set
      doMoreWork();         // T1 vẫn là owner
  }                        // T1 unlock ở đây

T2 đang chờ M:
  M.wait() không thể hoàn tất return
  cho đến khi T2 reacquire M sau khi T1 release.
~~~

Notify là **tín hiệu**, không phải thao tác chuyển giao monitor tức thời. Sau notifyAll(), các waiters vẫn phải **tranh lại quyền acquire** cùng monitor. Java không đảm bảo thread cụ thể nào nhận lock trước.

### 11.4. Luôn wait trong vòng while kiểm tra điều kiện

~~~java
synchronized (lock) {
    while (!condition) {
        lock.wait();
    }
    // Chỉ tiếp tục nếu condition thực sự đúng.
}
~~~

Vì:

- **Spurious wakeups** có thể xảy ra.
- notifyAll có thể đánh thức nhiều threads nhưng chỉ một thread lấy resource.
- Condition có thể đã thay đổi trước khi waiter reacquire lock.
- notify() chỉ chọn một waiter bất kỳ, không hứa waiter mong muốn.
- InterruptedException cần được xử lý đúng theo contract ứng dụng.

**Không** dùng if thay while khi correctness phụ thuộc predicate. Cũng không dùng Thread.sleep() thay wait() trong critical section: sleep không release monitor.

## 12. Java 17 runnable demo #3: wait giải phóng hết reentrant holds

Tạo file **ReentrantWaitDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;

public class ReentrantWaitDemo {
    public static void main(String[] args)
            throws InterruptedException {
        Object lock = new Object();
        CountDownLatch entered = new CountDownLatch(1);
        boolean[] ready = {false};
        boolean[] reacquired = {false};

        Thread waiter = new Thread(() -> {
            synchronized (lock) {
                synchronized (lock) {
                    entered.countDown();
                    while (!ready[0]) {
                        try {
                            lock.wait();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    reacquired[0] = Thread.holdsLock(lock);
                }
            }
        });

        waiter.start();
        entered.await();

        synchronized (lock) {
            ready[0] = true;
            lock.notifyAll();
        }

        waiter.join();
        System.out.println("condition=" + ready[0]);
        System.out.println("reacquired=" + reacquired[0]);
        System.out.println("completed=" + !waiter.isAlive());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 -encoding UTF-8 ReentrantWaitDemo.java
java ReentrantWaitDemo
~~~

**Stdout chính xác trong lần chạy bình thường, không external interrupt:**

~~~text
condition=true
reacquired=true
completed=true
~~~

Lý do:

1. Waiter acquire cùng **lock** hai lần bằng nested synchronized blocks.
2. Waiter gọi entered.countDown() khi đang sở hữu lock, rồi kiểm tra ready=false và gọi lock.wait().
3. **wait() tạm release toàn bộ 2 reentrant holds** của lock. Main có thể acquire lock, thiết lập ready=true và notifyAll().
4. Main phải thoát synchronized(lock) để thực sự release; notifier **không trao monitor ngay** ở notifyAll().
5. Waiter reacquire monitor đến đúng depth cần thiết, kiểm tra vòng while; ready=true nên ghi nhận Thread.holdsLock(lock)==true.
6. Waiter thoát nested blocks, rồi main join chờ waiter hoàn tất.
7. Không cần dùng Thread.sleep để phối hợp timing; CountDownLatch chỉ giúp main biết waiter đã đến vị trí cần thiết.

**Nếu có spurious wakeup:** waiter kiểm tra lại while, tiếp tục wait khi ready=false nên không sai. Nếu bị thread khác interrupt bất thường, demo có nhánh return; stdout được mô tả cho môi trường thông thường không external interruption.

**Lưu ý:** CountDownLatch không thay thế monitor protocol trong demo. Chính wait() nhả monitor để main có thể acquire và phát điều kiện; latch chỉ là điểm đồng bộ bảo đảm thứ tự bắt đầu thí nghiệm.

## 13. IllegalMonitorStateException và null monitor

### 13.1. wait/notify không giữ đúng monitor

~~~java
Object lock = new Object();
lock.notifyAll(); // Không sở hữu lock monitor -> IllegalMonitorStateException.
~~~

Nếu code chỉ đang giữ **monitor khác**:

~~~java
Object a = new Object();
Object b = new Object();

synchronized (a) {
    b.notifyAll(); // Vẫn sai: chưa sở hữu monitor của b.
}
~~~

Phải gọi wait/notify trên **chính object mà thread đã acquire**. Giữ monitor a không cho quyền gửi notification trên b.

### 13.2. synchronized(null)

~~~java
Object lock = null;

synchronized (lock) {
    // Không được vào: NullPointerException.
}
~~~

Theo JVMS, **monitorenter** với null object reference ném NullPointerException. Đây không phải tình huống “lock rỗng thì cứ chạy không đồng bộ”.

### 13.3. Exception trong synchronized có làm rò monitor?

Java bảo đảm synchronized statement/method thực hiện monitor release phù hợp khi kết thúc **bình thường hoặc abrupt**, bao gồm exception propagation. Compiler tạo exception path cho synchronized block; method có ACC_SYNCHRONIZED được VM xử lý implicit release.

~~~java
void fail(Object lock) {
    synchronized (lock) {
        throw new IllegalStateException("boom");
    } // monitor được thoát trong quá trình exception propagation.
}
~~~

Mã minh họa; một runtime exception không tự “khoá monitor vĩnh viễn”. Tuy nhiên một thread bị treo vô hạn **bên trong** synchronized (không thoát block/method) vẫn có thể giữ lock rất lâu.

### 13.4. Thread.stop() và các unsafe threading APIs

Không dùng các API lịch sử như **Thread.stop()** để “ép thread đang giữ monitor nhả lock”. Cơ chế dừng thô bạo có thể bỏ qua object invariants và làm shared state hỏng. Dùng cancellation/cooperative interruption với lifecycle rõ ràng.

## 14. Java 17 runnable demo #4: static lock và instance lock là khác nhau

Tạo **MonitorIdentityDemo.java**:

~~~java
public class MonitorIdentityDemo {
    static synchronized boolean classOwned() {
        return Thread.holdsLock(MonitorIdentityDemo.class);
    }

    synchronized boolean instanceOwned() {
        return Thread.holdsLock(this);
    }

    public static void main(String[] args) {
        MonitorIdentityDemo instance =
                new MonitorIdentityDemo();

        System.out.println("classMethod="
                + classOwned());
        System.out.println("instanceMethod="
                + instance.instanceOwned());

        synchronized (MonitorIdentityDemo.class) {
            System.out.println("classBlock="
                    + Thread.holdsLock(MonitorIdentityDemo.class));
            System.out.println("instanceInClassBlock="
                    + Thread.holdsLock(instance));
        }
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 MonitorIdentityDemo.java
java MonitorIdentityDemo
~~~

**Stdout:**

~~~text
classMethod=true
instanceMethod=true
classBlock=true
instanceInClassBlock=false
~~~

Giải thích:

- classOwned() là **static synchronized**, nên method giữ **MonitorIdentityDemo.class** khi gọi holdsLock.
- instanceOwned() giữ **this**, là instance monitor.
- Main acquire Class monitor; điều đó không tự acquire instance monitor.
- Thí nghiệm chỉ dùng một thread để chứng minh **object identity** của monitor; nó không chứng minh thứ tự scheduling giữa threads.

## 15. Những vấn đề về fairness, scheduling và interruption

**Intrinsic monitors không có hợp đồng công bằng FIFO** cho tất cả waiters. Khi monitor được release, JVM/OS có thể chọn thread nào acquire tiếp theo dựa trên scheduling/implementation.

Những điều không được giả định:

- Thread T2 chờ trước T3 thì chắc chắn T2 sẽ nhận lock trước.
- notify() sẽ chọn thread đã wait lâu nhất.
- Thread.interrupt() sẽ luôn làm một thread đang BLOCKED chờ monitor entry dừng chờ và ném InterruptedException.
- Thread.yield() sẽ làm monitor bị release.
- Thread.sleep() bên trong synchronized sẽ nhả monitor.
- Synchronized block có built-in timeout parameter tương đương tryLock(timeout).

### 15.1. ReentrantLock khác intrinsic synchronized

**java.util.concurrent.locks.ReentrantLock** cũng tái nhập được nhưng có các API bổ sung:

- **lock()** và **unlock()**.
- **tryLock()** và **tryLock(timeout)**.
- **lockInterruptibly()**.
- Có thể tạo fair-mode lock theo API, dù fairness policy có trade-offs.
- Condition objects hỗ trợ nhiều wait sets logic.
- **getHoldCount()** và một số introspection capabilities.

Ngược lại intrinsic synchronized có cú pháp ngắn, release tự động theo lexical scope và monitor wait/notify tích hợp. **Không coi ReentrantLock luôn hiệu năng hơn synchronized**; JVM có nhiều tối ưu intrinsic monitors.

Ví dụ dùng ReentrantLock đúng cách:

~~~java
// Mã minh họa, đã khai báo Lock lock:
lock.lock();
try {
    // Critical section.
} finally {
    lock.unlock();
}
~~~

Với ReentrantLock, **unlock() là thao tác lập trình viên phải gọi**, nên nếu quên finally có thể gây leak lock ownership. Intrinsic synchronized tự exit khi block kết thúc.

## 16. Deadlock: reentrancy không chữa được khóa chéo

Reentrancy ngăn **self-deadlock khi cùng owner thread acquire lại cùng monitor**, nhưng không ngăn **deadlock giữa nhiều monitors**.

Ví dụ kịch bản:

~~~text
T1:
  acquire monitor A
  attempt acquire B -> BLOCKED vì T2 giữ B

T2:
  acquire monitor B
  attempt acquire A -> BLOCKED vì T1 giữ A

Kết quả:
  T1 chờ B do T2 giữ
  T2 chờ A do T1 giữ
  -> Circular wait / deadlock
~~~

Đây là ví dụ conceptual, không nên cố chạy một chương trình deadlock vô hạn trên môi trường dùng chung.

### 16.1. Nested synchronized khác lock reentrancy

~~~java
synchronized (a) {
    synchronized (b) {
        // Giữ hai monitors a và b.
    }
}
~~~

Nếu a và b là hai objects khác nhau, đây là **nested multi-locking**, không phải **reenter cùng lock**. T2 khóa b rồi a có thể tạo lock-order inversion.

### 16.2. Cách phòng tránh deadlock

1. Thiết lập **thứ tự khóa toàn cục** và luôn acquire nhiều monitors cùng thứ tự.
2. Giữ critical sections ngắn, tránh I/O/blocking operations kéo dài trong synchronized.
3. Không gọi code bên ngoài không kiểm soát trong khi giữ monitor nếu callback có thể acquire các locks khác.
4. Giảm số monitors phải giữ đồng thời.
5. Nếu yêu cầu có timeout hoặc interruptible acquisition, cân nhắc ReentrantLock.tryLock/lockInterruptibly thay vì intrinsic monitor.
6. Dùng thread dump/JFR để xác định actual ownership, không đoán từ một source snippet.

### 16.3. Wait() trên monitor B khi vẫn giữ A có thể gây deadlock

~~~java
synchronized (a) {
    synchronized (b) {
        b.wait();
    }
}
~~~

Thread sẽ release B khi wait nhưng **vẫn giữ A**. Nếu notifier cần acquire A trước mới có thể notify B, hai threads có thể kẹt. Đây là một failure mode khác với việc owner reenter cùng monitor.

## 17. Khả năng visibility không mở rộng qua lock khác

Ví dụ thiết kế sai:

~~~java
final class WrongLockExample {
    private final Object writeLock = new Object();
    private final Object readLock = new Object();
    private int value;

    void set(int newValue) {
        synchronized (writeLock) {
            value = newValue;
        }
    }

    int get() {
        synchronized (readLock) {
            return value;
        }
    }
}
~~~

Dù cả hai methods dùng synchronized, chúng khóa **khác objects**. Java Memory Model không có monitor happens-before edge giữa unlock writeLock và lock readLock chỉ do cả hai là synchronized.

Sửa:

~~~java
final class RightLockExample {
    private final Object lock = new Object();
    private int value;

    void set(int newValue) {
        synchronized (lock) {
            value = newValue;
        }
    }

    int get() {
        synchronized (lock) {
            return value;
        }
    }
}
~~~

Với mọi thread tuân cùng lock discipline, get() đọc state được đồng bộ đúng. Nếu có code khác đọc value trực tiếp không đồng bộ, bảo đảm thread-safety tổng thể vẫn có thể bị phá.

### 17.1. synchronized trên String literal, boxed Integer, public lock

~~~java
synchronized ("LOCK_NAME") {
    // Không nên dùng interned public String làm private lock.
}
~~~

String literals có thể được interned/chia sẻ giữa code không liên quan; các thành phần khác có thể vô tình lock cùng monitor. Tương tự boxed values như Integer.valueOf(1) có thể được cache; các locks công khai tạo nguy cơ lock coupling khó kiểm soát.

Ưu tiên **private final Object lock = new Object()** cho dữ liệu nội bộ. Không dùng một object tạo mới **ngay trong mỗi lần method call** như synchronized(new Object()) nếu mục tiêu là phối hợp giữa các callers; mỗi object identity là monitor khác nhau.

### 17.2. Lock trên this: tiện nhưng public exposure

Instance synchronized method dùng **this**, nên bất kỳ code nào giữ được reference tới instance cũng có thể cố tình hoặc vô tình synchronized(instance). Khi thiết kế library/service internals, private lock object cho phép tránh lock bị chia sẻ bất ngờ giữa thành phần nội bộ và bên ngoài.

Tuy nhiên private lock chỉ có giá trị nếu mọi reads/writes relevant đều sử dụng đúng cùng lock; không phải một dấu hiệu magic thread-safe.

## 18. Hiệu năng của synchronized và JVM implementation

Trên HotSpot, monitor có thể được tối ưu hóa để uncontended locking rẻ. Những terms có thể gặp trong diagnostics/historical explanations:

- **Thin/lightweight locking**: cơ chế tối ưu uncontended monitor entry/exit tùy phiên bản.
- **Inflated monitor/ObjectMonitor**: implementation có thể sử dụng cấu trúc monitor nặng hơn khi contention/waiting hoặc conditions khác.
- **Lock coarsening/elision**: compiler có thể tối ưu synchronized operations khi chứng minh hợp lệ.
- **Biased locking**: tối ưu lịch sử của HotSpot, đã bị tắt mặc định và deprecate từ JDK 15 theo JEP 374; không dạy nó là default behavior cho JDK 17.

**Không cố định** độ rộng object header bits, Monitor owner pointer hoặc thin-lock encoding theo một sơ đồ duy nhất cho JDK 17 và JDK 25. Những nội bộ này có thể thay đổi do cải tiến lightweight locking hoặc virtual-thread monitors.

### 18.1. Contention có thể tốn CPU và làm giảm scalability

Nếu 32 worker threads liên tục gọi synchronized trên **một global lock**, chỉ một thread có thể thực thi critical section tại thời điểm nhất định. Thời gian chờ có thể lớn ngay cả khi operation là một phép cộng nhỏ. Mục tiêu tuning hợp lý:

- Giảm contention bằng partition/sharding locks theo dữ liệu độc lập.
- Rút ngắn critical section.
- Tránh I/O/DB calls trong lock.
- Cân nhắc concurrent collections, atomics hoặc LongAdder cho counters theo semantics phù hợp.
- Benchmark throughput/tail latency trước và sau.

**Không thể tối ưu bằng cách bỏ synchronized** nếu yêu cầu mutual exclusion/visibility vẫn tồn tại. Hãy thay bằng một chiến lược đồng bộ hóa tương đương, không tạo data races.

### 18.2. Monitor reentrancy không có nghĩa mọi nested synchronized là miễn phí

Khi reenter cùng monitor trong code đã được JIT tối ưu, VM có thể tối ưu nhiều thao tác; nhưng vẫn có cost semantic/safepoint/deoptimization/monitor bookkeeping ở những điều kiện khác. Đừng giả định reentrant entry luôn có zero CPU cost hoặc luôn đắt như contended acquisition.

## 19. Quan sát monitor bằng Thread.holdsLock, jcmd, jstack và JFR

### 19.1. Thread.holdsLock

~~~java
Object lock = new Object();
System.out.println(Thread.holdsLock(lock)); // false ở thread chưa lock.
synchronized (lock) {
    System.out.println(Thread.holdsLock(lock)); // true.
}
~~~

Đoạn này chạy trong một method, đúng với thread hiện tại. API chỉ cho biết current thread owns object monitor hay không, **không trả recursion depth** và không trả thông tin về thread khác.

### 19.2. jcmd Thread.print

Trên HotSpot JDK 17, khi process đang chạy:

~~~bash
jcmd
jcmd <PID> help
jcmd <PID> Thread.print
~~~

Thay **<PID>** bằng PID thực; output có thể liệt kê Java thread states, monitor ownership/blocked-on details và thông tin deadlock theo tool/build. Dùng quyền phù hợp; output chứa stack traces và thông tin ứng dụng cần xử lý an toàn.

### 19.3. jstack

~~~bash
jstack <PID>
jstack -l <PID>
~~~

**-l** yêu cầu thêm lock-related information theo jstack implementation. Tùy JDK và cơ chế attach, jstack có thể không dùng được; trên JDK mới hơn, **jcmd Thread.print** thường là điểm bắt đầu thuận tiện.

Một thread dump có thể mô tả:

~~~text
Thread-1: BLOCKED (on object monitor)
    - waiting to lock <...> (a java.lang.Object)

Thread-2: RUNNABLE
    - locked <...> (a java.lang.Object)
~~~

Đây là **format minh họa**, không phải output cố định của một đoạn code. Khi thấy BLOCKED, tìm đúng object monitor và owner thread, rồi đối chiếu call stacks của cả hai.

### 19.4. Java Flight Recorder

JFR có những events phù hợp để quan sát monitor contention và wait durations khi được bật, ví dụ **jdk.JavaMonitorEnter**, **jdk.JavaMonitorWait** tùy JDK/recording profile.

Khởi chạy một ứng dụng thực có main:

~~~bash
java -XX:StartFlightRecording=filename=monitors.jfr,duration=30s,settings=profile MyMain
~~~

Dùng jfr CLI:

~~~bash
jfr summary monitors.jfr
jfr print --events jdk.JavaMonitorEnter,jdk.JavaMonitorWait monitors.jfr
~~~

**MyMain** và file name là ví dụ; ứng dụng có thể kết thúc trước duration. Event có thể vắng do recording settings/thresholds, không có contention đáng kể hoặc JVM configuration. Không nói “không có JavaMonitorEnter event” đồng nghĩa chương trình không bao giờ synchronized.

### 19.5. ThreadMXBean

Java management API **java.lang.management.ThreadMXBean** có phương thức lấy thread information, deadlock detection và monitor information ở những VM hỗ trợ. Ví dụ đọc toàn bộ stack traces có thể có overhead; nhớ rằng trạng thái thread/monitor là snapshots thay đổi liên tục.

### 19.6. Đo latency: chỗ nghẽn có phải monitor?

Nếu request latency tăng, hãy hỏi:

1. Có thread dump/JFR cho thấy nhiều threads **BLOCKED trên cùng object monitor** không?
2. Owner thread đang thực hiện công việc gì trong critical section?
3. Có owner đang chờ I/O, lock khác, hay gọi callback không kiểm soát?
4. Có monitor contention nhiều vì dùng một global singleton lock?
5. Có deadlock thực (circular waits) hay chỉ contention ngắn?
6. Có nhiều WAITING vì condition không được signal đúng?
7. Liệu vấn đề là GC/safepoint/CPU throttle chứ không phải monitor?
8. Có cần throughput optimization hay correctness bug fix?

Không chẩn đoán thread-safety chỉ từ việc đọc một file có keyword synchronized. Cần biết **object identity** và paths runtime.

## 20. Monitor và virtual threads: ghi chú phiên bản

**Java 17 chưa có virtual threads là tính năng chính thức**. Java 21 đưa virtual threads thành production feature theo JEP 444. Trong các JDK 21–23, virtual thread thực thi synchronized code có thể dẫn tới **carrier-thread pinning** khi blocking, giảm khả năng scale trong những tình huống nhất định.

**JDK 24 / JEP 491** cải thiện cơ chế monitor để virtual threads có thể **unmount** trong nhiều tình huống synchronized/monitor waiting thay vì bị pin như trước. Vì vậy:

- Không suy rằng mọi JDK dùng virtual threads đều có cùng synchronized pinning behavior.
- Không áp advice JDK 21–23 “tránh mọi synchronized để tránh pinning” nguyên xi cho JDK 24+.
- Vẫn cần giữ critical sections ngắn và tránh lock contention dù có virtual threads.
- Ngữ nghĩa **monitor ownership/reentrancy/JMM** dành cho Java code vẫn phải được bảo toàn qua những thay đổi implementation.

Thông tin này nhằm tránh diễn giải sai khi học tài liệu cũ; nó **không thay thế** nguyên lý synchronized của Java 17.

## 21. Những hiểu lầm phổ biến

1. **“synchronized khóa method name.”** Sai: nó acquire monitor cụ thể, thường là this hoặc Class object.
2. **“Mọi method synchronized trong cùng class dùng một lock duy nhất.”** Sai nếu instance methods trên các instances khác nhau hoặc static vs instance.
3. **“Thread gọi synchronized method rồi gọi synchronized method khác trên cùng object sẽ deadlock.”** Sai: monitor reentrant.
4. **“Monitor vào lại không cần track số lần sở hữu.”** Sai: phải cân bằng reentrant acquisitions.
5. **“Thoát inner synchronized nhả toàn bộ monitor.”** Sai nếu còn outer hold count.
6. **“Một thread khác có thể chen vào giữa hai nested synchronized của cùng owner.”** Không khi owner vẫn giữ monitor.
7. **“Thread.holdsLock trả recursion count.”** Sai: chỉ boolean current thread.
8. **“synchronized instance bảo vệ static field với global class monitor.”** Sai: khóa this.
9. **“static synchronized khóa mọi instances cùng class.”** Nó khóa Class monitor, không tự acquire instance monitors.
10. **“synchronized trên lockA bảo vệ reads dùng lockB.”** Sai: thiếu cùng-monitor synchronization.
11. **“Synchronized dùng một Object mới mỗi invocation sẽ làm các callers chặn nhau.”** Sai: object identities khác nhau.
12. **“Có synchronized keyword thì mọi field trong class luôn thread-safe.”** Sai: các accesses khác có thể data race.
13. **“unlock monitor A happens-before lock monitor B.”** Không chỉ vì hai monitors đều có synchronized; cần matching monitor hoặc HB edges khác.
14. **“Synchronized bảo đảm fairness FIFO.”** Sai: intrinsic monitor không có fairness guarantee.
15. **“Thread.sleep trong synchronized sẽ nhả monitor.”** Sai: sleep không nhả monitor.
16. **“Object.wait không nhả monitor.”** Sai: nhả **toàn bộ acquisitions của receiver monitor**.
17. **“wait trên B nhả tất cả monitors A/B mà thread đang giữ.”** Sai: chỉ receiver B.
18. **“notifyAll chuyển lock ngay cho waiter.”** Sai: notifier vẫn giữ monitor tới khi unlock.
19. **“notify() luôn chọn waiter chờ lâu nhất.”** Sai: không có lựa chọn thứ tự được bảo đảm.
20. **“if(condition) wait là đủ.”** Sai: dùng while predicate vì spurious wakeup/state race.
21. **“wait/notify có thể gọi khi không giữ monitor receiver.”** Sai: IllegalMonitorStateException.
22. **“synchronized(null) là no-op.”** Sai: NullPointerException.
23. **“Exception ném khỏi synchronized block làm monitor bị leak.”** Sai: release trên abrupt exit theo ngữ nghĩa.
24. **“Một synchronized method luôn sinh monitorenter/exit trong bytecode.”** Sai: dùng ACC_SYNCHRONIZED method flag.
25. **“monitorenter chỉ dùng cho non-reentrant locking.”** Sai: reentrant count là phần của instruction semantics.
26. **“Reentrancy tự ngăn deadlock trên hai monitors.”** Sai: A→B/B→A vẫn có thể deadlock.
27. **“Thread.interrupt sẽ bỏ chờ synchronized monitor entry ngay.”** Không theo quy tắc intrinsic lock.
28. **“Thread.State.WAITING và BLOCKED là một.”** Sai: waiting on condition vs monitor acquisition.
29. **“JIT implementation lock encoding là một phần Java SE API.”** Sai: VM implementation details.
30. **“Biased locking luôn mặc định bật trên Java 17.”** Sai: đã disabled-by-default từ JDK15.
31. **“GC/HotSpot cải tiến lock làm happens-before không cần nữa.”** Sai: JMM semantics giữ nguyên.
32. **“Bỏ synchronized sẽ luôn làm chương trình nhanh hơn.”** Sai nếu gây data races/corruption hoặc phải dùng cơ chế đồng bộ hóa khác tốn hơn.
33. **“Monitor là distributed lock bảo vệ DB qua nhiều JVMs.”** Sai.
34. **“Class loaders định nghĩa cùng binary name chia sẻ một Class monitor.”** Không nhất thiết; runtime class identity phụ thuộc defining loader.
35. **“wait sẽ trả về ngay khi nhận notify kể cả monitor còn bị notifier giữ.”** Sai: phải reacquire monitor trước.
36. **“Virtual threads ở Java 21 và Java 25 luôn có monitor pinning giống nhau.”** Sai: JDK24/JEP491 thay đổi đáng kể.

## 22. Bài tập có lời giải

**Câu 1.** Hai thread cùng gọi synchronized instance method trên **hai instances khác nhau**. Có tự chặn nhau không?

**Đáp án:** Không chỉ vì method cùng tên. Mỗi instance có monitor riêng.

**Câu 2.** Thread T1 vào synchronized(lock) rồi nested synchronized(lock). Entry count logic biến đổi thế nào?

**Đáp án:** 0→1→2, rồi thoát inner còn 1, thoát outer về 0.

**Câu 3.** synchronized static method lock gì?

**Đáp án:** DefiningClass.class, tức Class mirror object của runtime class.

**Câu 4.** synchronized instance method lock gì?

**Đáp án:** this, tức monitor của receiver instance.

**Câu 5.** Một static synchronized và một instance synchronized method cùng class có thể chạy song song không?

**Đáp án:** Có thể, vì Class monitor khác instance monitor, trừ khi code dùng thêm locks khác.

**Câu 6.** Khi đang sở hữu monitor lock, gọi another synchronized method của cùng object có bị tự deadlock không?

**Đáp án:** Không; monitor tái nhập được với owner thread ấy.

**Câu 7.** Thread.holdsLock(lock) trả gì nếu current thread đã reenter lock 3 lần?

**Đáp án:** true, không trả 3.

**Câu 8.** Synchronized block nào được biểu diễn bằng monitorenter/monitorexit? Synchronized method dùng gì?

**Đáp án:** Synchronized statement dùng monitorenter/exit; synchronized method dùng ACC_SYNCHRONIZED và VM implicit monitor entry/exit.

**Câu 9.** T1 giữ M hai lần rồi gọi M.wait(). T1 tạm nhả mấy lần?

**Đáp án:** Tạm release toàn bộ hai acquisitions trên M; khi returning, reacquire tới cùng hold depth.

**Câu 10.** T1 giữ A và B rồi gọi B.wait(). Monitor A có được nhả không?

**Đáp án:** Không; chỉ B được release bởi wait trên B.

**Câu 11.** T1 gọi notifyAll() trong synchronized(lock). T2 có lập tức chạy trong critical section trước khi T1 thoát không?

**Đáp án:** Không; T2 phải reacquire monitor khi T1 release đủ holds.

**Câu 12.** Tại sao while (!condition) lock.wait() tốt hơn if?

**Đáp án:** Vì spurious wakeups hoặc condition thay đổi trước khi waiter reacquire; cần kiểm tra lại predicate.

**Câu 13.** Gọi Object.notify() khi không giữ monitor của receiver dẫn tới gì?

**Đáp án:** IllegalMonitorStateException.

**Câu 14.** synchronized(null) dẫn tới gì?

**Đáp án:** NullPointerException trên monitor entry.

**Câu 15.** synchronized block ném RuntimeException có luôn giữ monitor mãi không?

**Đáp án:** Không. Ngữ nghĩa synchronized bảo đảm unlock trên normal/abrupt completion.

**Câu 16.** Thread đang BLOCKED chờ monitor khác gì WAITING sau wait()?

**Đáp án:** BLOCKED chưa acquire monitor; WAITING sau wait() đã release receiver monitor và chờ event/notification.

**Câu 17.** Dùng synchronized(lockA) viết state, synchronized(lockB) đọc state. Có HB monitor edge trực tiếp không?

**Đáp án:** Không nếu lockA và lockB khác identity, và không có synchronization khác bảo đảm.

**Câu 18.** Một luồng giữ A chờ B và luồng khác giữ B chờ A. Reentrancy giải được không?

**Đáp án:** Không; deadlock do hai owners và circular wait trên hai monitors khác nhau.

**Câu 19.** Vì sao synchronized("shared") không phải lock private tốt?

**Đáp án:** String literal có thể interned/share; các phần code khác có thể lock cùng object ngoài ý muốn.

**Câu 20.** Khi nào nên dùng private final Object lock?

**Đáp án:** Khi muốn che giấu lock identity để chỉ code nội bộ sử dụng cùng monitor bảo vệ một state.

**Câu 21.** JDK17 biased locking có còn mặc định bật không?

**Đáp án:** Không, JEP374 đã disable-by-default và deprecate từ JDK15.

**Câu 22.** Java 21–23 virtual threads có khả năng pin carrier với synchronized. JDK24 thay đổi gì?

**Đáp án:** JEP491 cải thiện khả năng unmount virtual threads khi monitor blocked/waiting, giảm pinning.

**Câu 23.** Tại sao Thread.join() giúp demo counter có output xác định?

**Đáp án:** Main chờ bốn threads thực hiện đủ operations; thread completion/join cung cấp HB phù hợp.

**Câu 24.** Có thể dùng một synchronized method để bảo vệ DB transaction giữa hai JVM processes không?

**Đáp án:** Không; monitor chỉ điều phối threads theo một JVM runtime, cần database/distributed coordination riêng.

**Câu 25.** ReentrantWaitDemo chứng minh điều gì vượt quá Thread.holdsLock?

**Đáp án:** Cho thấy thread giữ cùng monitor hai lần vẫn có thể wait, notifier acquire monitor rồi phát tín hiệu, waiter reacquire trước khi tiếp tục. holdsLock chỉ xác nhận owner, không đo được depth.

## 23. Quy trình thiết kế và debug monitor

1. **Xác định state cần bảo vệ** và những threads nào có thể truy cập đồng thời.
2. **Chọn đúng lock identity**: this, Class monitor hoặc private final Object, và dùng nhất quán.
3. **Thiết kế critical sections nhỏ**: bảo vệ bất biến, tránh I/O/blocking không cần thiết khi giữ monitor.
4. **Xét reentrancy** cho nested calls/recursion: same owner thread có thể acquire lại, nhưng cần release cân bằng.
5. **Đảm bảo visibility qua cùng lock** hoặc một synchronization scheme khác đúng JMM.
6. **Với wait/notify**, có explicit condition predicate, dùng while, xử lý interruption, và không nhầm notify với immediate unlock.
7. **Hạn chế multi-lock nested ordering**; nếu phải dùng nhiều monitors, đặt global order.
8. **Không expose lock object tùy tiện** cho code ngoài class; tránh interned Strings/boxed caches.
9. **Kiểm thử correctness** với nhiều threads và assertions; đừng viết tests dựa vào Thread.sleep/scheduling trùng hợp.
10. **Quan sát thực tế** bằng jcmd Thread.print, jstack/JFR ThreadMXBean khi contention/deadlock xuất hiện.
11. **Tách bug và performance**: mutex contention cao khác race condition, deadlock khác wait-set chờ condition hợp lệ.
12. **Khi dùng virtual threads**, xác nhận JDK version và các cải tiến monitor.
13. **Khi tối ưu**, xem data structures/lock granularity trước khi thay implementation; giữ semantics chính xác.
14. **Phân biệt source vs JVM bytecode**: ACC_SYNCHRONIZED của method khác monitorenter/exit của statement.
15. **Viết tài liệu rõ lock owner/state lifecycle**, giúp maintainers không vô tình thêm method truy cập state ngoài lock discipline.

### Tài liệu chính thức

- **Java Language Specification SE 17 — §17.1 Synchronization:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.1
- **Java Language Specification SE 17 — §17.2 Wait Sets and Notification:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.2
- **JLS SE 17 — §17.4.5 Happens-before Order:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5
- **JVMS SE 17 — §2.11.10 Synchronization:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.11.10
- **JVMS SE 17 — monitorenter/monitorexit instructions:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5.monitorenter
- **Java SE 17 API — Object.wait/notify:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html
- **Java SE 17 API — Thread.holdsLock:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#holdsLock(java.lang.Object)
- **Java SE 17 API — Thread.State:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.State.html
- **Java SE 17 API — ReentrantLock:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html
- **Oracle Java SE 17 — jcmd:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- **OpenJDK JEP 374 — Disable and Deprecate Biased Locking:** https://openjdk.org/jeps/374
- **OpenJDK JEP 491 — Synchronize Virtual Threads without Pinning:** https://openjdk.org/jeps/491

**Kết luận:** Monitor của Java gắn với **object identity**. Một thời điểm chỉ một thread sở hữu monitor, nhưng **chính owner đó có thể reenter bao nhiêu lần cần thiết**, và lock thực sự được nhả khi số lần thoát cân bằng số lần vào. Instance synchronized lock **this**, static synchronized lock **Class object**, explicit synchronized lock object được chỉ định. **wait()** tạm nhả toàn bộ reentrant holds của receiver rồi reacquire trước khi hoàn tất; **notify() không chuyển giao lock ngay**. Để code concurrent đúng, phải nhất quán lock identity, dùng while-predicate cho condition waits và tận dụng happens-before trên **cùng monitor**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-threads-and-synchronization-004">monitorenter, monitorexit và phương thức có cờ ACC_SYNCHRONIZED</a>

<details>
<summary>Xem chi tiết</summary>

# `monitorenter`, `monitorexit` và phương thức có cờ `ACC_SYNCHRONIZED`

## 1. Bức tranh tổng quan: Java `synchronized` được JVM thực thi thế nào?

Java cung cấp hai dạng cú pháp đồng bộ nội tại (intrinsic synchronization):

- **Khối lệnh:** `synchronized (lock) { ... }` — khóa monitor gắn với **object reference do biểu thức `lock` tạo ra** trong thời gian chạy block.
- **Phương thức:** `synchronized void method() { ... }` hoặc `static synchronized void method() { ... }` — khóa monitor được xác định ngầm bằng **receiver `this`** (instance method) hoặc **đối tượng `Class`** của class khai báo (static method).

Cả hai đều tuân theo một **monitor model**: một thread sở hữu monitor tại một thời điểm, có khả năng **tái nhập (reentrant)**, và unlock–lock trên **cùng monitor** có hiệu ứng memory-ordering theo Java Memory Model (JMM).

Nhưng chúng **khác cách thể hiện trong class file**:

| Java source | Biểu diễn bytecode/class file điển hình | Ai lo enter/exit? |
| --- | --- | --- |
| `synchronized (obj) { body(); }` | `monitorenter`, `monitorexit` và exception handler thích hợp trong `Code` | Instructions và control-flow do `javac` phát sinh |
| `public synchronized void action()` | `method_info.access_flags` chứa `ACC_SYNCHRONIZED`; thường **không** có `monitorenter`/`monitorexit` vì riêng việc khai báo method | JVM tự enter khi invoke, exit khi method complete |
| `public static synchronized void action()` | Cũng là `ACC_SYNCHRONIZED` | JVM dùng monitor của đối tượng `Class` đại diện class khai báo |
| `new ReentrantLock()` và `lock()/unlock()` | Thông thường các lời gọi method thư viện; **không** có `monitorenter` chỉ vì là lock | Thư viện `java.util.concurrent.locks` và các nguyên thủy JVM/OS mà nó dùng |

Đây là điểm chính để đọc `javap`: **không thấy opcode `monitorenter` trong một `synchronized` method không có nghĩa method ấy không được đồng bộ**. Bản thân cờ `ACC_SYNCHRONIZED` là cách JVM nhận biết và áp dụng khóa ngầm.

Bài theo **JVMS Java SE 17**, cùng những ghi chú riêng về HotSpot Java 17. Java/JVMS định nghĩa hiệu ứng và cấu trúc class file; việc HotSpot tối ưu monitor bằng fast path, spinning, inflation, JIT lock elimination… là chi tiết triển khai có thể đổi theo phiên bản.

## 2. Monitor là gì?

Một monitor là một cơ chế đồng bộ gắn với một object, có thể được mô hình hóa qua:

- **Owner:** thread đang sở hữu monitor hoặc không có owner.
- **Entry count (reentrancy depth):** số lần owner đã enter trừ số lần exit phù hợp.
- **Entry contention:** những thread khác đang cố acquire monitor phải chờ monitor có thể được lấy.
- **Wait set:** những thread đã gọi `Object.wait()` hợp lệ trên monitor và đang chờ được đánh thức/tái giành lock theo điều kiện của `wait`.

```text
                 Object L / monitor(L)
                 +--------------------+
Thread A ------> | owner = A          |
                 | entry count = 2    |
Thread B --X-->  | B đang chờ enter   |
                 | wait set: ...      |
                 +--------------------+

A đã enter hai lần => phải exit hai lần mới thả ownership.
B không thể enter trong lúc monitor vẫn do A sở hữu.
```

Các trường ở sơ đồ là **mô hình ngữ nghĩa**, không phải một Java object literal có các fields `owner` và `entryCount` mà ứng dụng được phép đọc. JVM có thể quản lý monitor object/metadata theo nhiều cách.

### Sở hữu monitor là theo object identity, không theo tên biến

```java
Object first = new Object();
Object alias = first;
synchronized (first) {
    synchronized (alias) {
        // Tái nhập cùng một monitor.
    }
}
```

Đây là một **fragment Java**, có thể đặt trong một method. Hai reference khác tên nhưng `first == alias`, nên hai synchronized statements là reentry vào **cùng monitor**.

Ngược lại, hai object khác nhau dù `equals` trả `true` vẫn có **monitor riêng**, vì khóa nội tại dựa trên **reference identity**, không dựa vào `equals` hoặc `hashCode`. `System.identityHashCode` cũng không phải địa chỉ monitor/raw memory address.

## 3. Cơ chế `monitorenter`

Trong JVMS 17, `monitorenter` là một opcode JVM instruction:

| Đặc tính | Nội dung |
| --- | --- |
| Mnemonic | `monitorenter` |
| Opcode | `194`, hexadecimal `0xC2` |
| Operand stack trước | `..., objectref` |
| Operand stack sau | `...` |
| Tác dụng | Thử acquire monitor của `objectref`; pop reference khỏi operand stack |

Mô hình hoạt động:

1. Nếu `objectref == null`, instruction ném `NullPointerException`.
2. Nếu monitor đang không có owner, thread hiện tại acquire; entry count thành 1.
3. Nếu thread hiện tại **đã** sở hữu monitor ấy, đây là reentrant enter; entry count tăng.
4. Nếu một thread khác đang là owner, thread hiện tại phải chờ để có thể acquire monitor theo cơ chế VM; không có đảm bảo FIFO.

```text
Operand stack trước:
  [..., objectref L]
                  ^
                  top

monitorenter

Operand stack sau:
  [...]

Monitor(L):
  entry count 0 -> owner T, count 1
  hoặc T đã owner -> count tăng
  hoặc thread khác owner -> T chờ acquire
```

Không nên hiểu “bytecode pop `objectref`” nghĩa GC không còn giữ reference tới lock trong thời gian synchronized block. Compiler/JVM phải quản lý đúng **root/lifetime** và exception exit path; `javac` thường lưu lock reference vào một **local variable ẩn** trước `monitorenter` để dùng lại khi `monitorexit`.

## 4. Cơ chế `monitorexit`

`monitorexit` là opcode đối ứng:

| Đặc tính | Nội dung |
| --- | --- |
| Mnemonic | `monitorexit` |
| Opcode | `195`, hexadecimal `0xC3` |
| Operand stack trước | `..., objectref` |
| Operand stack sau | `...` |
| Tác dụng | Pop reference, decrement entry count của monitor đang thuộc sở hữu thread |

Nếu decrement làm entry count về 0, thread **không còn là owner** và thread khác có thể thử acquire monitor.

Các exception quan trọng theo JVMS:

- `monitorexit` với `objectref == null` → `NullPointerException`.
- Nếu thread đang chạy **không sở hữu monitor** của object ấy → `IllegalMonitorStateException`.
- Một JVM thực thi kiểm tra **structured locking** có thể ném `IllegalMonitorStateException` khi các quy tắc structured locking bị vi phạm trong trường hợp mà specification mô tả.

```text
Thread T đã enter L hai lần:
  owner = T, entryCount = 2

monitorexit(L):
  owner = T, entryCount = 1
  (chưa thả monitor cho thread khác)

monitorexit(L):
  entryCount = 0
  owner = none
  (thread khác có thể acquire)
```

**Không được exit “một lần cho đủ”** khi đã nested synchronized vào cùng monitor nhiều lần. Compiler và VM phải bảo toàn độ sâu tái nhập ở những execution paths hợp lệ.

### Tính structured locking

JVMS `2.11.10` mô tả **structured locking**: trong một invocation, các lần monitor exit phải tương ứng với các lần enter, không exit nhiều hơn enter; trạng thái phải cân bằng khi invocation hoàn thành bình thường hoặc bất thường. JVM **được phép nhưng không bắt buộc** cưỡng chế mọi quy tắc này theo cách mà specification nêu đối với bytecode tổng quát.

Trong Java source hợp lệ, `synchronized` block/method có sự đảm bảo enter/exit tương ứng do compiler và JVM. **Không tự viết bytecode làm `monitorexit` trên monitor chưa sở hữu**; điều đó có thể ném `IllegalMonitorStateException`, không phải cách quản lý lock “chuyên sâu” hợp lệ.

## 5. Vì sao compiler phải tạo `try/finally` dạng bytecode?

Giả sử:

```java
void work(Object lock) {
    synchronized (lock) {
        doWork();
    }
}
```

Đây là **method fragment**, phụ thuộc `doWork()` được định nghĩa bên ngoài. Ngữ nghĩa Java yêu cầu monitor được thả **dù `doWork()` kết thúc bình thường hay ném exception**. Vì vậy bytecode của synchronized block có:

1. Đọc và giữ reference của object dùng làm lock.
2. `monitorenter` trước body.
3. `monitorexit` trên normal path.
4. **Exception handler** để thực hiện `monitorexit` rồi ném lại throwable trên abrupt path.

Sơ đồ mô phỏng (các BCIs không phải offset `javap` thật của một file cụ thể):

```text
aload lock
dup
astore monitorLocal
monitorenter

  [protected region]
  invoke doWork

aload monitorLocal
monitorexit
goto NORMAL_END

CATCH_ALL:
  astore thrown
  aload monitorLocal
  monitorexit
  aload thrown
  athrow

NORMAL_END:
  return
```

Exception table trong class file định tuyến throwable từ body tới handler phù hợp. `javac` cũng có thể phát sinh một handler bổ sung cho các trường hợp ngoại lệ xảy ra trong cleanup path tùy bytecode version/control flow.

### Vì sao không “nhét một monitorexit vào cuối method” là đủ?

Bởi vì body có thể:

- `return` sớm.
- `break` hoặc `continue` ra khỏi block.
- `throw` trực tiếp.
- Gặp exception từ method call, array access, field access, cast…
- Hoàn thành qua một nhánh điều kiện khác.

Đường thoát hợp lệ nào cũng phải giữ semantics monitor exit. Ở class-file level, compiler giải quyết bằng các branches, handlers và exit instructions phù hợp; không thể chỉ nhìn một dòng `return` ở Java source và suy lock bị kẹt.

## 6. Phương thức `synchronized`: cờ `ACC_SYNCHRONIZED`, không cần opcode tường minh

Một method được khai báo:

```java
public synchronized int read() {
    return count;
}
```

Đây là **fragment** trong một class có field `count`.

Thông thường `javac` không chèn `monitorenter`, `monitorexit` vào phần `Code` chỉ do modifier `synchronized` của method. Thay vào đó, `method_info.access_flags` có bit:

```text
ACC_SYNCHRONIZED = 0x0020
```

Khi thực hiện lời gọi method mang cờ này, JVM **tự** enter monitor trước khi thực thi method và **tự** exit khi method hoàn thành **bình thường hoặc bất thường**. Nếu method ném exception ra ngoài, monitor vẫn được thả khi invocation kết thúc.

### Method flag khác field hoặc class flag

- `ACC_SYNCHRONIZED` là một **method access flag** trong `method_info` (JVMS §4.6), không phải annotation Java.
- `ACC_SYNCHRONIZED` không có nghĩa “mọi method trong class dùng chung một global monitor”.
- Flag này không biến nested object fields thành thread-safe.
- JVM có một số cơ chế flags/locking ngoài class file; chúng không thay đổi ý nghĩa public bytecode method flag.

### Dùng monitor nào?

| Khai báo | Monitor gắn với | Ví dụ tương đương về ý nghĩa |
| --- | --- | --- |
| `synchronized void inc()` | `this` receiver object | `synchronized (this) { body }` |
| `static synchronized void reset()` | `Class` object đại diện cho class khai báo | `synchronized (Counter.class) { body }` |

**Khác nhau:** hai instances `Counter a` và `Counter b` có monitor riêng cho `a.inc()` và `b.inc()`. Trong khi đó `Counter.reset()` sử dụng monitor của `Counter.class`, không tự giành monitor của `a` hay `b`.

Nếu một class không `final` được kế thừa, hãy chú ý khi tự viết `getClass()`: đối tượng class runtime có thể là subclass khác so với class **khai báo static synchronized method**. Đừng tùy tiện thay `Counter.class` bằng `getClass()` khi diễn giải static monitor semantics.

## 7. Java 17 chạy được: xem block, instance method và static method

Tạo file **`MonitorBytecodeDemo.java`**:

```java
public class MonitorBytecodeDemo {
    private int count;

    public synchronized void incrementMethod() {
        count++;
    }

    public void incrementBlock() {
        synchronized (this) {
            count++;
        }
    }

    public synchronized int getCount() {
        return count;
    }

    public static synchronized String staticMarker() {
        return "class-monitor";
    }

    public static void main(String[] args) {
        MonitorBytecodeDemo counter = new MonitorBytecodeDemo();

        counter.incrementMethod();
        counter.incrementBlock();

        boolean holds;
        synchronized (counter) {
            holds = Thread.holdsLock(counter);
            counter.incrementMethod(); // Reentrant: cùng thread đã giữ monitor.
        }

        System.out.println("count=" + counter.getCount());
        System.out.println("holdsInside=" + holds);
        System.out.println("holdsOutside=" + Thread.holdsLock(counter));
        System.out.println("static=" + staticMarker());
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 MonitorBytecodeDemo.java
java MonitorBytecodeDemo
```

**Stdout chính xác:**

```text
count=3
holdsInside=true
holdsOutside=false
static=class-monitor
```

Giải thích:

- `incrementMethod` và `incrementBlock` đều bảo vệ cùng field `count` bằng monitor của receiver.
- Khi vào `synchronized(counter)`, thread giữ monitor của `counter`; gọi `counter.incrementMethod()` vẫn hợp lệ vì intrinsic locks **reentrant**.
- `Thread.holdsLock(counter)` trả `true` trong vùng do chính thread hiện tại giữ monitor, và `false` sau khi thoát vùng (không có khóa nào khác ở thread ấy).
- `staticMarker` là static synchronized method nên dùng `MonitorBytecodeDemo.class`; nó không tham gia khóa riêng của `counter`.

### Xem bằng `javap`

```bash
javap -c -v -p MonitorBytecodeDemo
```

Ở `incrementMethod`, `getCount` và `staticMarker`, hãy tìm dòng method flags có **`ACC_SYNCHRONIZED`**. Trong `incrementBlock`, hãy tìm các instruction **`monitorenter`**, **`monitorexit`** và phần **Exception table**.

Một trích đoạn **mang tính cấu trúc, không phải output nguyên văn với offsets/indices đã đo**:

```text
public synchronized void incrementMethod();
  flags: (0x0021) ACC_PUBLIC, ACC_SYNCHRONIZED
  Code:
    ... getfield count ...
    ... putfield count ...
    return

public void incrementBlock();
  flags: (0x0001) ACC_PUBLIC
  Code:
    ... aload_0 ...
    ... monitorenter ...
    ... getfield count ...
    ... putfield count ...
    ... monitorexit ...
    ... monitorexit ...
    ... athrow ...
  Exception table:
    ... any ...
```

Các `...` là **chú thích lược bỏ**, không phải opcode Java hợp lệ hoặc dump đầy đủ. Class-file constant pool indexes, BCI offsets, stack map frames và exception-table boundaries phải xem trực tiếp bằng `javap`. Điều đáng tin là **mẫu phân biệt flag versus opcodes**.

## 8. Đọc method_info và Code attribute đúng thứ tự

Một method trong class file có thông tin như:

```text
method_info:
  access_flags: ACC_PUBLIC | ACC_SYNCHRONIZED
  name_index
  descriptor_index
  attributes:
    Code {
      max_stack, max_locals
      code[]
      exception_table
      ...
    }
```

Đây là sơ đồ, không phải dữ liệu binary class file được giải mã thực. Những điều cần kiểm:

1. `ACC_SYNCHRONIZED` nằm ở **method_info.access_flags**.
2. `Code` chứa bytecode nghiệp vụ, nhưng không bắt buộc chứa `monitorenter`/`monitorexit` tương ứng method-level lock.
3. Với `synchronized` block, compiler phát sinh instructions rõ ràng **trong `code[]`**.
4. Các exception handlers trong `Code.exception_table` của block bảo vệ việc thả monitor khi body ném exception.
5. Nếu một method vừa mang `ACC_SYNCHRONIZED` vừa chứa block `synchronized(other)`, có thể có **hai monitor khác nhau** đang được giữ (method monitor + block monitor).

### Opcode và flags không giống nhau

- `monitorenter = 0xC2`, `monitorexit = 0xC3` là **1-byte opcodes** trong JVM instruction stream.
- `ACC_SYNCHRONIZED = 0x0020` là **bitmask trong cấu trúc metadata của method**.
- Không tìm bit `0x0020` trong `Code` rồi kết luận đó là lệnh lock.
- Không tìm `monitorenter` trong mọi method marked synchronized rồi báo bytecode bị lỗi.

## 9. Exception trong synchronized block: vì sao lock vẫn được thả?

Tạo file **`MonitorExceptionDemo.java`**:

```java
public class MonitorExceptionDemo {
    private final Object lock = new Object();

    public void failInBlock() {
        synchronized (lock) {
            throw new IllegalStateException("block");
        }
    }

    public synchronized void failInMethod() {
        throw new IllegalStateException("method");
    }

    public synchronized String recover() {
        return "recovered";
    }

    public static void main(String[] args) {
        MonitorExceptionDemo demo = new MonitorExceptionDemo();

        try {
            demo.failInBlock();
        } catch (IllegalStateException e) {
            System.out.println("caughtBlock=" + e.getMessage());
        }

        synchronized (demo.lock) {
            System.out.println("blockReentered="
                    + Thread.holdsLock(demo.lock));
        }

        try {
            demo.failInMethod();
        } catch (IllegalStateException e) {
            System.out.println("caughtMethod=" + e.getMessage());
        }

        System.out.println("methodReentered=" + demo.recover());
    }
}
```

```bash
javac --release 17 MonitorExceptionDemo.java
java MonitorExceptionDemo
```

**Stdout xác định:**

```text
caughtBlock=block
blockReentered=true
caughtMethod=method
methodReentered=recovered
```

Mã trình bày hai cơ chế:

- `failInBlock` có monitor được acquire qua block; compiler tạo đường release kể cả khi `IllegalStateException` thoát ra.
- `failInMethod` mang method flag `ACC_SYNCHRONIZED`; VM đảm bảo implicit exit khi method kết thúc bất thường.
- Có thể acquire lại monitor sau exception; không bị kẹt vĩnh viễn vì exception ấy.

**Giới hạn thử nghiệm:** ví dụ chạy một thread, nên `blockReentered=true` tự nó chưa chứng minh một thread khác sẽ không bao giờ bị block nếu có lỗi. Bằng chứng chính về release trên mọi control path là **quy tắc ngôn ngữ/JVMS và bytecode cleanup logic**. Không dùng “chạy được một lần” để khẳng định code concurrent luôn không có race/deadlock.

## 10. Lệnh `athrow`, return và release ngầm khác gì?

Với method có `ACC_SYNCHRONIZED`:

- Trước khi method body chạy, VM enter monitor tương ứng.
- Khi method **normal return**, VM đảm bảo exit monitor của invocation.
- Khi method **abrupt completion** do exception chưa được xử lý trong method, VM đảm bảo exit trước khi truyền exception ra caller.
- Bytecode body có thể chứa `ireturn`, `areturn`, `return` hoặc `athrow`, nhưng **method lock không cần được thể hiện bằng opcode `monitorexit` tường minh ở mỗi điểm**.

Với synchronized block:

- Có `monitorenter` trước body.
- `monitorexit` trên normal exit.
- `monitorexit` qua bytecode exception-handling path trên abrupt exit.
- Java compiler phải lo **đúng lock object** được thả; nếu biểu thức `lock` bị đổi giá trị ở Java variable ngoài block, monitor object đã lấy vẫn là object được lưu tại thời điểm enter.

Ví dụ:

```java
Object lock = new Object();
Object original = lock;
synchronized (lock) {
    lock = new Object();
    System.out.println(Thread.holdsLock(original)); // true
}
```

Đây là **fragment** trong method. Biến `lock` đã trỏ object khác nhưng block vẫn đang sở hữu monitor của **`original`** cho tới khi kết thúc. Không đồng nghĩa “reassign reference mở khóa ngay”.

## 11. Reentrancy chi tiết: một thread vào cùng monitor nhiều lần

Intrinsic locks của Java là **reentrant**. Giả sử:

```java
class Nested {
    synchronized void outer() {
        inner();
    }

    synchronized void inner() {
        // Cùng receiver this, nên tái nhập cùng monitor.
    }
}
```

Đây là class minh họa, không có `main`.

Timeline:

```text
T gọi outer() trên obj
  enter monitor(obj): depth 0 -> 1

outer() gọi inner() cùng obj
  enter monitor(obj): depth 1 -> 2

inner() return
  implicit exit: depth 2 -> 1

outer() return
  implicit exit: depth 1 -> 0

Monitor của obj sẵn sàng cho thread khác.
```

**Reentrancy không phải fairness.** Java intrinsic monitor không cam kết thread nào chờ sẽ được chọn kế tiếp, không cho `tryLock(timeout)` kiểu `ReentrantLock`, và việc enter monitor theo cú pháp `synchronized` không cung cấp cơ chế interruptible lock acquisition giống `lockInterruptibly()`.

Tránh code giả định một thread đang `BLOCKED` chờ intrinsic monitor sẽ tự động ngừng chờ vì thread bị `interrupt()`. **Interrupt của `Object.wait` là chuyện khác**: waiting thread có thể ném `InterruptedException` sau khi reacquire monitor.

## 12. Instance synchronized vs static synchronized: không khóa nhau mặc định

Xét:

```java
class Ledger {
    private int instanceCount;
    private static int globalCount;

    synchronized void incrementInstance() {
        instanceCount++;
    }

    static synchronized void incrementGlobal() {
        globalCount++;
    }
}
```

Đây là class fragment. Monitor của `ledger.incrementInstance()` là object `ledger`; monitor của `Ledger.incrementGlobal()` là **`Ledger.class`**.

Hai thread có thể:

- Thread A sở hữu monitor của `ledger`.
- Thread B đồng thời sở hữu monitor của `Ledger.class`.
- Cả hai thực thi những method tương ứng mà không chặn nhau **vì khác monitor**, nếu không có synchronization khác.

Đừng dùng một instance synchronized method để bảo vệ static shared mutable field mà các thread khác có thể sửa qua static synchronized method — chúng **không tự dùng cùng lock**.

### Hai instance khác nhau cũng là hai monitors

```text
Ledger a   -> monitor(a)
Ledger b   -> monitor(b)
Ledger.class -> monitor(Ledger.class)

a.incrementInstance() và b.incrementInstance() có thể song song.
a.incrementInstance() và Ledger.incrementGlobal() có thể song song.
```

Để bảo vệ trạng thái chung, chọn **một lock strategy nhất quán** và đảm bảo mọi reads/writes liên quan đều tuân theo strategy đó.

## 13. Java Memory Model: monitor exit có nghĩa về visibility

`synchronized` không chỉ bảo vệ **mutual exclusion**. Nó còn tạo quan hệ thứ tự bộ nhớ theo JMM.

Với **cùng monitor M**, một **unlock của M** trong thread A **synchronizes-with** một **lock của M** tiếp theo trong thread B; từ các quy tắc happens-before, những writes trong A trước unlock có thể trở nên visible đúng cho reads của B sau lock khi truy cập dữ liệu được đồng bộ phù hợp.

```text
Thread A                          Thread B

lock(M)
sharedValue = 42
unlock(M) -------- sync-with ----> lock(M)
                                  read sharedValue => thấy writes
                                  unlock(M)

Điều kiện: cùng M và có thứ tự phù hợp.
```

**Không phải “mọi unlock ở A synchronizes-with mọi lock ở B”**. Monitor identity phải giống nhau, và thứ tự các synchronization actions phải phù hợp. Khóa không sửa **một data race** nếu có thread khác đọc/ghi cùng mutable state bên ngoài cơ chế đồng bộ ấy.

### `volatile` khác `synchronized`

- `volatile` cung cấp visibility/order semantics trên một biến cụ thể; một biểu thức như `count++` không được tự biến thành atomic chỉ vì `count` volatile.
- `synchronized` cung cấp mutual exclusion **và** monitor-based happens-before nếu threads dùng cùng lock.
- `AtomicInteger` thực hiện các operations atomic qua API riêng.
- `ReentrantLock` có memory synchronization effects được Java concurrency API định nghĩa, nhưng không triển khai bằng hai bytecode opcodes `monitorenter`/`monitorexit` cho lock API ấy.

Đừng đồng nhất GC write barriers/JIT memory barriers với Java source `synchronized` statements: chúng có mục đích/semantics khác nhau.

## 14. Java 17: hai threads tăng một counter an toàn

Tạo file **`MonitorCounterDemo.java`**:

```java
public class MonitorCounterDemo {
    private int counter;

    public synchronized void increment() {
        counter++;
    }

    public synchronized int value() {
        return counter;
    }

    public static void main(String[] args)
            throws InterruptedException {
        MonitorCounterDemo shared = new MonitorCounterDemo();

        Runnable job = () -> {
            for (int i = 0; i < 10_000; i++) {
                shared.increment();
            }
        };

        Thread a = new Thread(job, "worker-A");
        Thread b = new Thread(job, "worker-B");

        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("expected=20000");
        System.out.println("actual=" + shared.value());
    }
}
```

```bash
javac --release 17 MonitorCounterDemo.java
java MonitorCounterDemo
```

**Stdout chính xác nếu threads hoàn thành bình thường:**

```text
expected=20000
actual=20000
```

Mỗi call `increment` cần acquire/release monitor của **cùng object `shared`**; vì vậy `counter++` (đọc, cộng, ghi) nằm trong critical section mutually exclusive. `value` cũng dùng monitor ấy, dù ở ví dụ `join()` đã đảm bảo các workers kết thúc trước khi main đọc.

Nếu bỏ `synchronized` khỏi `increment`, `counter++` không còn atomic và có thể **lost updates**; nhưng **không đảm bảo** mỗi lần chạy sẽ in dưới 20.000 — race có thể không lộ ra ở một lần thử. Điều đúng là chương trình khi ấy không có synchronization thích hợp để bảo đảm luôn được 20.000.

### Một số yêu cầu code review

- Guard **cả reads lẫn writes** của shared mutable state bằng cùng monitor hoặc một synchronization strategy hợp lệ.
- Không gọi `counter++` “atomic” chỉ vì Java có một opcode cộng; bytecode thường tách load/compute/store.
- Nếu lock lâu, throughput có thể giảm vì contention; chọn granularity phù hợp.
- Không tối ưu xóa synchronized một cách thủ công trên shared state.

## 15. `Object.wait`, `notify` và `notifyAll` liên hệ với monitor thế nào?

Ngoài việc enter/exit, Java monitor liên quan **wait sets**. Khi thread đang sở hữu monitor của object `lock`, nó có thể gọi:

- `lock.wait()`: tạm **nhả ownership của monitor của lock**, chờ notification/interruption/timeout theo overload/semantics; trước khi return hoặc throw phù hợp, thread **tái acquire monitor**.
- `lock.notify()`: chọn một thread đang chờ trên wait set của **cùng lock** để đánh thức theo quy tắc VM; **không nhả monitor ngay**.
- `lock.notifyAll()`: đánh thức toàn bộ threads trong wait set của object ấy để chúng tranh quyền reacquire.
- Các operations này yêu cầu thread hiện tại đang sở hữu monitor phù hợp; nếu không → `IllegalMonitorStateException`.

**Không có riêng một opcode JVM `wait` hoặc `notify` tương ứng**. Chúng là methods của `java.lang.Object` với implementation phối hợp monitor support của JVM.

### Vì sao luôn dùng `while` chứ không dùng `if` quanh `wait`?

Một thread được đánh thức không có nghĩa điều kiện nghiệp vụ đã trở thành đúng: có thể là **spurious wakeup**, thread khác đã thay đổi state, hoặc `notifyAll` đánh thức nhiều threads cùng lúc. Luôn kiểm tra điều kiện ở trong vòng lặp **khi đang giữ monitor**.

```java
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
    // ready đúng tại thời điểm đang giữ lock
}
```

Đây là fragment nằm trong method khai báo/propagate `InterruptedException`, có field `ready` và object `lock` thích hợp.

### `Thread.sleep` khác `Object.wait`

`Thread.sleep(...)` **không tự thả monitors** đang được thread sở hữu. Ngủ 1 giây trong `synchronized` block có thể giữ thread khác chờ vào cùng monitor suốt thời gian ấy. `Object.wait()` thì release **monitor được wait**, rồi reacquire trước khi tiếp tục; nó không tự thả mọi monitor khác mà thread cũng đang giữ.

## 16. Java 17: ví dụ wait/notifyAll không phụ thuộc thứ tự khởi chạy

Tạo file **`MonitorWaitDemo.java`**:

```java
public class MonitorWaitDemo {
    static final class Gate {
        private boolean open;

        synchronized void await() throws InterruptedException {
            while (!open) {
                wait();
            }
        }

        synchronized void signal() {
            open = true;
            notifyAll();
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Gate gate = new Gate();
        Thread worker = new Thread(() -> {
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            System.out.println("worker=passed");
        }, "waiter");

        worker.start();
        gate.signal();
        worker.join();

        System.out.println("main=done");
    }
}
```

```bash
javac --release 17 MonitorWaitDemo.java
java MonitorWaitDemo
```

**Stdout trong trường hợp không có interruption bên ngoài:**

```text
worker=passed
main=done
```

- Nếu worker chưa vào `await()` khi main gọi `signal()`, field `open` đã được đặt true; worker vào sau vẫn vượt qua, **không bị mất tín hiệu**.
- Nếu worker đã `wait()`, main có thể acquire monitor của Gate sau khi waiter release, đặt `open=true` rồi `notifyAll()`; worker phải reacquire monitor mới return từ `wait()`.
- `signal()` và `await()` là instance synchronized methods; monitor là `gate` (receiver).
- `join()` khiến dòng main in **sau** dòng worker ở đường chạy bình thường.

**Chú ý:** đây là demo synchronization correctness; không dùng nó để lập luận một sự kiện `notifyAll` chính xác tương ứng với một JVM opcode. Thread scheduling có thể thay đổi mà hai dòng output vẫn có thứ tự xác định nhờ `join()`.

## 17. `IllegalMonitorStateException` và `NullPointerException`

Hai lỗi hay bị nhầm:

### Không giữ monitor mà gọi wait/notify

```java
Object lock = new Object();
try {
    lock.notify();
} catch (IllegalMonitorStateException expected) {
    System.out.println("not-owner");
}
```

Fragment này chạy được khi đặt trong `main`. Kết quả là `not-owner` vì caller chưa giữ monitor của `lock`. Để đúng, gọi:

```java
synchronized (lock) {
    lock.notifyAll();
}
```

### Biểu thức lock là null

```java
Object lock = null;
synchronized (lock) {
    System.out.println("never");
}
```

Đây là fragment. Lúc monitor enter trên null, runtime ném `NullPointerException`; body không chạy. Không có một monitor đặc biệt đại diện cho `null`.

### `IllegalMonitorStateException` khác lock contention

Một thread hợp lệ đang chờ acquire một monitor do thread khác giữ **không mặc định ném `IllegalMonitorStateException`** chỉ vì chờ. Exception này liên quan các operations/monitor ownership không hợp lệ, như `notify()` ngoài synchronized và `monitorexit` khi không phải owner.

## 18. Synchronized method không tương đương khóa mọi state bên trong object graph

Giả sử class:

```java
class Basket {
    private final java.util.List<String> items =
            new java.util.ArrayList<>();

    synchronized void add(String item) {
        items.add(item);
    }

    java.util.List<String> rawItems() {
        return items;
    }
}
```

Đây là class fragment. Dù `add` synchronized, method `rawItems` trả trực tiếp một mutable list. Caller có thể sửa list mà **không giữ monitor Basket**, phá dữ liệu hoặc tạo race.

**Khóa monitor chỉ bảo vệ các hành động phối hợp dùng chính monitor đó.** Nó không tự đổi tính thread-safe của mọi object tham chiếu bên trong, không ngăn code bên ngoài giữ reference mutable và sử dụng sai.

Một API tốt có thể:

- Không phơi trực tiếp mutable internal representation.
- Trả immutable snapshot/copy phù hợp.
- Yêu cầu mọi thao tác vào state được đồng bộ đúng.
- Dùng collections hoặc concurrent abstractions phù hợp thay vì phức tạp hóa monitor protocol.

## 19. Deadlock: hai monitors khóa ngược thứ tự

Mỗi `synchronized` có thể đúng cục bộ nhưng hệ thống vẫn bị **deadlock** nếu hai threads giữ lock theo thứ tự ngược:

```text
Thread A:
  synchronized(L1) {
    synchronized(L2) { ... }
  }

Thread B:
  synchronized(L2) {
    synchronized(L1) { ... }
  }

A giữ L1, chờ L2.
B giữ L2, chờ L1.
=> cycle of wait-for dependencies.
```

Đây là ví dụ ý niệm, **không phải chương trình được tạo để cố tình treo JVM**.

Biện pháp:

- Định nghĩa và tuân thủ **global lock ordering** nhất quán.
- Giữ critical sections ngắn; tránh gọi vào callbacks, external code hoặc I/O lâu khi đang giữ locks.
- Không lấy lock trên objects public như string interned/class literals trừ khi chính sách ownership rõ ràng.
- Dùng `ReentrantLock.tryLock` hoặc abstractions cấp cao nếu bài toán cần timeouts/interruptible acquisition.
- Dùng thread dumps để xác minh owner và blocked dependencies.

**Reentrant không có nghĩa “không thể deadlock”**. Reentrancy chỉ cho phép cùng thread vào lại **cùng monitor**, không xử lý cycles giữa **hai monitors khác nhau**.

## 20. Thread dumps: BLOCKED khác WAITING như thế nào?

Khi một Java thread cố enter một intrinsic monitor đã do thread khác giữ, nó có thể xuất hiện trong trạng thái **`BLOCKED`**. Khi nó đã gọi `Object.wait()` và đang chờ trên wait set, thường thấy trạng thái **`WAITING`** (hoặc **`TIMED_WAITING`** với timeout), tùy thực tế.

Bảng khái quát:

| Tình huống | State thường thấy | Vai trò monitor |
| --- | --- | --- |
| Chờ vào `synchronized(lock)` vì thread khác giữ lock | `BLOCKED` | Đang chờ acquire ownership |
| Gọi `lock.wait()` không timeout | `WAITING` | Đã release monitor của lock và chờ được tái acquire theo quy tắc |
| Gọi `lock.wait(timeout)` | `TIMED_WAITING` | Timed wait |
| Gọi `Thread.sleep(timeout)` | `TIMED_WAITING` | **Không release** locks đang giữ |
| `LockSupport.park` | `WAITING` | Parking khác monitor wait set |
| Chạy code không chờ monitor | Có thể `RUNNABLE` | Không kết luận không có contention chỉ từ một snapshot |

Thread state là **ảnh chụp**; thread có thể đổi trạng thái rất nhanh. Dùng nhiều thread dumps và timeline nếu điều tra production.

### Công cụ Java 17

```bash
jcmd <PID> Thread.print -l
jstack -l <PID>
```

`<PID>` là placeholder của một Java process thực đang chạy và có quyền attach. `jcmd`/`jstack` có thể in monitor ownership, blocked/waiting threads và deadlock information tùy build/options. Một thread dump thường hiển thị `- locked <...>` hoặc `- waiting to lock <...>`; các object IDs trong diagnostic output **không phải Java API identity** ổn định để làm logic application.

Nếu gặp lỗi **Java-level deadlock**, dùng nhiều snapshots, xem threads đang giữ và chờ monitors nào, rồi tìm thứ tự acquire trái ngược trong source. Không kết luận mọi thread `WAITING` đều deadlock; thread pool idle, `Object.wait` và `LockSupport.park` hợp lệ cũng có WAITING.

## 21. Tại sao `synchronized` có thể nhanh hơn kỳ vọng?

HotSpot có các optimizations và implementation techniques khác nhau tùy release:

- Fast paths cho uncontended monitor acquisition.
- JIT inlining và compiler optimizations quanh monitor regions.
- **Escape analysis / lock elimination** khi compiler chứng minh một lock object không thể được thread khác quan sát trong compiled graph.
- Cơ chế quản lý monitor metadata, inflation/deflation và đường slow path khi contention.
- Tối ưu các trường hợp nested/reentrant locking hoặc code motion khi vẫn bảo toàn Java semantics.

Trên **HotSpot Java 17**, không được bê nguyên mô tả locking của JDK 8 hoặc JDK 25 áp như layout chắc chắn: biased locking đã **bị tắt mặc định trong các release trước Java 17**, các thuật toán monitor và object header có thể thay đổi giữa các versions.

### Lock elimination khác bỏ synchronized khỏi source

```java
static int localOnly(int a) {
    Object privateLock = new Object();
    synchronized (privateLock) {
        return a + 1;
    }
}
```

Method fragment này có private lock không thoát theo source; C2 **có thể** loại bỏ monitor operations và thậm chí allocation nếu đủ điều kiện. Nhưng compiler vẫn phải bảo toàn semantics; **không có guarantee** lock sẽ được eliminate trên mọi path/JDK build.

Điều này không trao quyền xóa `synchronized` thủ công trong application shared-state logic. Với object thật sự được chia sẻ, bỏ monitor có thể gây data race.

## 22. `monitorenter`/`monitorexit` trong bytecode có nghĩa JIT luôn thực hiện lock/unlock vật lý?

Không. Bytecode xác định **ngữ nghĩa JVM**; HotSpot interpreter/JIT được phép tối ưu implementation:

- Interpreter có thể thực hiện entry/exit qua VM/runtime mechanisms thích hợp.
- C1/C2 sinh machine code tương đương về hành vi, không phải lúc nào cũng một call runtime cho mỗi monitorenter.
- C2 có thể eliminate monitor nếu chứng minh thread-confined/no observable synchronization effect, hoặc dùng fast path.
- Một số monitors có thể được inflated khi contention/special operations; trạng thái lock bên trong không phải điều Java source được phép phụ thuộc.
- Deoptimization phải bảo tồn logical monitor ownership/state khi chuyển execution representation.

Vì vậy `javap -c` có `monitorenter` không chứng minh native instructions thực tế luôn gồm một lệnh hardware lock đúng tại BCI ấy. Để xem máy mã cần công cụ JIT diagnostics, profiling và build phù hợp, không dùng `javap` như native disassembler.

### Đừng nhầm `monitorexit` với memory reclamation

`monitorexit` chỉ liên quan monitor ownership/synchronization. Nó **không xóa object, không gọi GC, không close file/socket**, và không tự release mọi resources bên trong critical section. `synchronized` không thay thế `try-with-resources` cho I/O resources.

## 23. Tại sao giữ lock khi gọi method khác có thể nguy hiểm?

Ví dụ:

```java
synchronized (lock) {
    callback.run();
}
```

Đây là fragment. Bạn vẫn sở hữu monitor `lock` trong thời gian `callback.run()`. Nếu callback:

- Chờ một lock khác theo thứ tự nguy hiểm.
- Thực hiện blocking I/O hoặc network call.
- Gọi code có thể quay lại cùng object theo một protocol phức tạp.
- Gọi `wait` trên một monitor khác khi vẫn đang giữ `lock`.
- Giữ lock trong một tác vụ kéo dài.

thì tăng contention hoặc deadlock risk. **Callback re-enter cùng lock** tự nó hợp lệ do reentrancy, nhưng một protocol có nhiều locks vẫn có thể deadlock.

Thiết kế tốt thường:

1. Thu thập snapshot dưới lock.
2. Thả lock.
3. Gọi external callback ngoài vùng bảo vệ nếu business invariant cho phép.
4. Dùng immutable messages/queues cho async processing khi phù hợp.

Không tự động “đưa mọi callback ra ngoài synchronized” nếu state invariants yêu cầu thao tác nguyên tử; hãy xác định đúng atomicity contract rồi refactor.

## 24. Java 17: static monitor và instance monitor thật sự độc lập

Tạo file **`ClassMonitorDemo.java`**:

```java
public class ClassMonitorDemo {
    private int instanceValue;
    private static int classValue;

    public synchronized void updateInstance() {
        instanceValue++;
    }

    public static synchronized void updateClass() {
        classValue++;
    }

    public static void main(String[] args) {
        ClassMonitorDemo obj = new ClassMonitorDemo();

        boolean instanceHeld;
        boolean classHeld;
        synchronized (obj) {
            instanceHeld = Thread.holdsLock(obj);
            classHeld = Thread.holdsLock(ClassMonitorDemo.class);
        }

        obj.updateInstance();
        updateClass();

        System.out.println("instanceHeld=" + instanceHeld);
        System.out.println("classHeld=" + classHeld);
        System.out.println("instanceValue=" + obj.instanceValue);
        System.out.println("classValue=" + classValue);
    }
}
```

```bash
javac --release 17 ClassMonitorDemo.java
java ClassMonitorDemo
```

**Stdout:**

```text
instanceHeld=true
classHeld=false
instanceValue=1
classValue=1
```

Trong `synchronized(obj)`, thread giữ monitor của `obj` nhưng **không** vì thế sở hữu `ClassMonitorDemo.class`. Hai `update...` methods sau đó lần lượt cập nhật những fields riêng. Ví dụ giúp phân biệt monitors; nó **không phải stress test concurrency**, không chứng minh bản thân tất cả fields/static state đã thread-safe khi có nhiều methods không đồng bộ cùng truy cập.

## 25. Không phải mọi method đều có thể khai báo synchronized tùy ý

Java language có các quy tắc về modifier:

- **Constructors không được khai báo `synchronized`** trong Java source. Có thể dùng synchronized block bên trong constructor khi thật sự phù hợp, nhưng constructor escape/this publication cần được xem xét kỹ.
- Abstract methods không có body để thực thi monitor theo cách synchronized method thông thường; khai báo `abstract synchronized` không hợp lệ ở Java source.
- Interface default/static methods với body có các giới hạn về modifier `synchronized` trong Java source, dù có thể chứa synchronized blocks bên trong.
- Một method có `synchronized` modifier là một class-file method với `ACC_SYNCHRONIZED`; không xem nó như wrapper tự động sinh `synchronized(this)` ở source-level metadata.

**Không tự viết/mutate class file flags không hợp lệ** rồi suy rằng JVM phải chấp nhận. Các ràng buộc flags theo JVMS §4.6, language rules và verifier liên quan đến version class file cụ thể.

## 26. So sánh `synchronized`, `ReentrantLock` và atomic operations

| Nhu cầu | `synchronized` | `ReentrantLock` | `AtomicInteger` |
| --- | --- | --- | --- |
| Mutual exclusion cho critical section | Có, bằng intrinsic monitor | Có, qua explicit lock API | Chỉ operation atomic trên giá trị/kiểu của nó |
| Reentrancy | Có | Có theo class implementation | Không áp dụng như monitor lock |
| Auto unlock khi block/method hoàn thành | Có | Cần `unlock()` trong `finally` | Không cần unlock |
| Timeout khi acquire | Không có cú pháp `synchronized` timeout | Có `tryLock(timeout, unit)` | Không áp dụng |
| Interruptible acquisition API | Không có tương đương `lockInterruptibly` | Có | Không áp dụng |
| Condition waiting | `wait/notify/notifyAll` | `Condition.await/signal` | Không tự cung cấp Condition |
| Fairness policy cấu hình | Không | Có constructor fair mode (có giới hạn semantics) | Không áp dụng |
| JVM `monitorenter`/`monitorexit` bytecode | Block có; method dùng method flag | Không phải biểu diễn chủ yếu của API | Không |
| Bảo vệ nhiều invariants một cách nguyên tử | Được nếu dùng cùng monitor đúng | Được nếu dùng cùng lock đúng | Cần thiết kế khác hoặc CAS protocol |

Đừng chọn API chỉ vì nghĩ “bytecode monitorenter chậm hơn lock API”. Performance phụ thuộc contention, JDK, fast paths, operating system, workload và độ dài critical section. Chọn contract đúng (timeout, fairness, interruptibility, composability) rồi đo.

## 27. Các lỗi thường gặp khi đọc `javap`

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “Không có `monitorenter` trong synchronized method nên nó không khóa” | `ACC_SYNCHRONIZED` buộc JVM xử lý implicit enter/exit |
| “Một block phải chỉ có một `monitorexit`” | Compiler có thể có normal và exception cleanup paths, nên nhiều instruction sites |
| “Mỗi `monitorexit` trong file chạy mỗi lần vào block” | Chỉ control-flow path thực tế được thực thi |
| “`ACC_SYNCHRONIZED` = opcode 0x20” | Đây là method access flag; opcodes là 0xC2/0xC3 |
| “Static synchronized dùng monitor của một instance bất kỳ” | Dùng `Class` object tương ứng |
| “`javap` không thấy lock object local nên không có reference” | Compiler có thể lưu monitor reference ở local ẩn |
| “Block exception handler nghĩa exception bị nuốt” | Handler thường cleanup monitor rồi `athrow` lại |
| “`return` trong block bỏ qua release” | Compiler phải đưa mọi normal exit qua cleanup hợp lệ |
| “Nested synchronized cùng object gây tự deadlock” | Intrinsic monitors reentrant |
| “Synchronized bảo vệ mọi field dù code ngoài bỏ qua lock” | Các truy cập phải đồng bộ nhất quán |
| “Monitor exit chỉ giúp thread khác vào, không ảnh hưởng visibility” | Unlock/lock trên cùng monitor tạo JMM happens-before phù hợp |
| “`notify()` thả monitor ngay” | Thread gọi notify vẫn giữ monitor tới khi exit |
| “`wait()` không thả lock” | Wait nhả monitor của object được wait và reacquire trước khi tiếp tục |
| “`sleep()` thả tất cả locks” | Sleep không release intrinsic monitors |
| “`IllegalMonitorStateException` xảy ra chỉ vì lock đang bận” | Nó báo ownership/monitor operation không hợp lệ |
| “`Thread.State.BLOCKED` giống `WAITING`” | Blocked for monitor entry khác waiting on wait set/park |
| “Bật GC sẽ giải phóng monitor đang có owner” | GC không được phá synchronization semantics của live objects |
| “Lock elimination nghĩa source synchronized không cần thiết” | JIT chỉ bỏ khi chứng minh semantics an toàn trong một context cụ thể |

## 28. Quy trình thực hành giải thích một đoạn synchronized trên HotSpot

**Bước 1 — Chốt mục tiêu.** Bạn đang hỏi compiler phát sinh opcode nào, thread vì sao BLOCKED, hay vì sao synchronized code chậm? Mỗi câu dùng công cụ khác nhau.

**Bước 2 — Xem source form.** Đây là `synchronized(lock)` block hay `synchronized` modifier của method? Instance hay static?

**Bước 3 — Xác định lock identity.** Lock có thể là `this`, `SomeClass.class`, field `private final Object lock` hoặc một object được truyền vào. Tên variable không quan trọng bằng **reference identity**.

**Bước 4 — Biên dịch Java 17:**

```bash
javac --release 17 MonitorBytecodeDemo.java
javap -v -c -p MonitorBytecodeDemo
```

**Bước 5 — Với block:** tìm `monitorenter`, `monitorexit` trên normal path và exception handler. Đừng nhầm số opcode sites với số lần runtime lock/unlock.

**Bước 6 — Với method:** xem method flags có `ACC_SYNCHRONIZED`. JVM xử lý implicit monitor enter/exit tại invocation/return, không đòi `Code` có opcode monitor tường minh.

**Bước 7 — Đọc Java semantics:** mọi control flow phải release monitor; reentry cùng thread được phép; null lock ném NPE; caller phải sở hữu monitor nếu gọi `wait/notify`.

**Bước 8 — Xác nhận JMM:** những threads đọc/ghi shared state có thực sự dùng **cùng monitor**? Có code đọc ngoài synchronized làm mất happens-before không?

**Bước 9 — Điều tra runtime contention:** xem `jcmd <PID> Thread.print -l`, JFR Java Monitor Blocked events khi có trên build/settings, CPU/latency timeline. Một thread dump là snapshot, không phải thống kê dài hạn.

**Bước 10 — Nếu cần tuning:** giảm độ dài critical section, tránh blocking I/O và external callbacks trong lock, xem lock ordering. Không xóa synchronized chỉ vì profiler báo contention.

**Bước 11 — Chỉ khi nghiên cứu codegen:** dùng compiler diagnostics/assembly tools phù hợp đúng JDK/CPU và biết rằng JIT có thể thay bytecode monitor operations bằng fast paths hoặc lock elimination, trong khi semantics vẫn giữ nguyên.

## 29. Câu hỏi tự kiểm tra

**Câu 1.** Khối `synchronized(obj)` xuất hiện trong bytecode bằng những instruction nào?

**Đáp:** `monitorenter` và `monitorexit`, với exception-handling path đảm bảo exit.

**Câu 2.** Một instance method có `ACC_SYNCHRONIZED` mà `javap -c` không in `monitorenter`. Có lỗi không?

**Không.** Method monitor enter/exit được JVM xử lý ngầm qua invocation/return semantics.

**Câu 3.** Static synchronized method dùng monitor của ai?

**Đối tượng `Class` đại diện class khai báo method**, ví dụ `Counter.class`, không phải monitor của một instance.

**Câu 4.** `monitorenter` nhận operand gì và để lại gì?

Pop `objectref` từ operand stack rồi thực hiện acquire; các phần tử stack trước đó vẫn còn.

**Câu 5.** Thread đã enter cùng object hai lần; sau một `monitorexit`, thread khác có thể vào chưa?

**Chưa** nếu entry count vẫn còn 1; thread hiện tại vẫn sở hữu monitor.

**Câu 6.** Khi body trong synchronized block ném exception, điều gì xảy ra?

Compiler/JVM thực hiện cleanup để exit monitor trước khi exception được truyền ra (trừ những exceptional VM termination semantics ngoài luồng ứng dụng thông thường).

**Câu 7.** Vì sao `lock.notifyAll()` cần ở trong synchronized?

Caller phải sở hữu monitor của `lock`; nếu không có thể ném `IllegalMonitorStateException`.

**Câu 8.** `lock.wait()` và `Thread.sleep()` khác gì về monitor?

`wait` release monitor của `lock` và reacquire trước khi tiếp tục; `sleep` không tự thả monitors.

**Câu 9.** Tại sao `if (!ready) wait()` chưa đủ an toàn?

Có spurious wakeups và condition có thể thay đổi trước khi reacquire; dùng `while (!ready) wait()`.

**Câu 10.** Một thread vào `synchronized(a)` rồi gọi instance synchronized method của `b`, khi `a != b`. Có phải reentry cùng lock?

**Không.** Hai monitors khác nhau; thread có thể giữ **đồng thời** hai locks, làm phát sinh nguy cơ deadlock nếu lock order không nhất quán.

**Câu 11.** `monitorexit` có tự xóa object khỏi heap không?

**Không.** Nó thả ownership/reentrancy depth và tạo synchronization effects, không trực tiếp reclaim memory.

**Câu 12.** `synchronized` dùng cùng monitor trong hai threads mang lại điều gì ngoài mutual exclusion?

Quan hệ memory ordering **unlock synchronizes-with later lock**, tạo happens-before phù hợp cho reads/writes bảo vệ đúng.

**Câu 13.** Một phương thức được JIT tối ưu loại monitor operation, vậy class file `ACC_SYNCHRONIZED` có biến mất không?

**Không nhất thiết và không được suy vậy.** JIT có thể loại runtime work khi chứng minh an toàn mà không sửa Java-level method declaration/class-file flag.

**Câu 14.** Lệnh nào giúp xem cờ method và bytecode trên Java 17?

`javap -v -c -p ClassName`. `-v` hiển thị flags, `-c` hiển thị bytecode; compiler/native machine code là phạm vi công cụ khác.

## 30. Nguồn tài liệu chuẩn

- [JVMS Java SE 17 2.11.10 — Synchronization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.11.10): implicit monitor handling, structured locking và quyền của JVM implementation.
- [JVMS Java SE 17 3.14 — Synchronization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-3.html#jvms-3.14): cách Java source synchronized block được biên dịch, exception handler và phân biệt synchronized method.
- [JVMS Java SE 17 4.6 — Methods](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.6): `method_info` và `ACC_SYNCHRONIZED` (0x0020).
- [JVMS Java SE 17 6.5 — monitorenter](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5.monitorenter): operand stack, owner/entry count, NPE và reentrancy.
- [JVMS Java SE 17 6.5 — monitorexit](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5.monitorexit): operand stack, exit count, IllegalMonitorStateException, exception path.
- [JLS Java SE 17 17.1 — Synchronization](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.1) và [17.4.5 — Happens-before Order](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5): monitor, unlock/lock và memory ordering.
- [Java SE 17 `Object.wait`, `notify`, `notifyAll`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): ownership, wait set, re-acquisition và spurious wakeups.
- [Java SE 17 `Thread.holdsLock`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#holdsLock(java.lang.Object)): kiểm tra current thread đang sở hữu monitor.
- [Java SE 17 `javap` tool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): xem class-file flags, Code attribute, exception tables.
- [Java SE 17 `jcmd` tool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): `Thread.print` và VM diagnostics.
- [Java SE 17 `ReentrantLock`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html): so sánh intrinsic lock với explicit lock APIs.
- [OpenJDK JEP 374 — Disable and Deprecate Biased Locking](https://openjdk.org/jeps/374): bối cảnh khác biệt giữa các bản HotSpot trong monitor implementations.

**Ghi nhớ:** **synchronized block** được `javac` biểu diễn bằng **`monitorenter`/`monitorexit`** cộng với cleanup paths; **synchronized method** được đánh dấu bằng **`ACC_SYNCHRONIZED`** để JVM enter/exit ngầm. Cả hai dùng **monitor theo object identity**, hỗ trợ **reentrancy**, **mutual exclusion** và **happens-before trên cùng monitor**, nhưng không tự bảo đảm fairness, không xóa deadlock và không tự làm dữ liệu an toàn nếu có đường đọc/ghi bỏ qua lock. Muốn hiểu đúng mã Java 17, hãy nối **source → class-file flags/bytecode → exception handling → monitor ownership → JMM** rồi mới nghiên cứu những tối ưu nội bộ HotSpot.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-threads-and-synchronization-005">wait, notify, notifyAll và quan hệ giữa wait set với quyền giữ monitor</a>

<details>
<summary>Xem chi tiết</summary>

# `wait`, `notify`, `notifyAll` và mối quan hệ giữa wait set với quyền sở hữu monitor trong Java

Trong lập trình đa luồng Java, `Object.wait()`, `Object.notify()` và `Object.notifyAll()` là bộ ba nguyên thủy để **phối hợp trạng thái giữa các threads bằng intrinsic monitor**. Những phương thức này không phải các lệnh “tạm dừng/tiếp tục một thread theo ý muốn” và cũng không tương đương `Thread.sleep()`. Muốn dùng đúng, cần hiểu đồng thời **ba thứ khác nhau**:

1. **Monitor ownership:** thread nào hiện đang sở hữu khóa nội tại của object?
2. **Wait set:** những threads nào đã tự nguyện nhường monitor bằng `wait()` trên chính object ấy và đang chờ một sự kiện để được xem xét tiếp tục?
3. **Điều kiện nghiệp vụ (guarded predicate):** dữ liệu nào cho biết thao tác có thể thực hiện, ví dụ `ready == true`, hàng đợi không rỗng hoặc buffer còn chỗ?

Nguyên tắc cốt lõi: **`wait()` chỉ được gọi khi thread đang sở hữu monitor của đúng object; nó đưa thread vào wait set và nhường toàn bộ quyền sở hữu monitor ấy. `notify()`/`notifyAll()` cũng đòi quyền sở hữu cùng monitor, nhưng không nhả monitor ngay. Thread đã nhận tín hiệu chỉ được trở lại từ `wait()` sau khi giành lại monitor.**

Vì một thread có thể thức dậy do notification, interruption, timeout hoặc *spurious wakeup*, **luôn kiểm tra điều kiện bằng `while` trong cùng một vùng đồng bộ**:

```java
synchronized (lock) {
    while (!condition) {
        lock.wait();
    }
    // Thực hiện công việc khi condition đúng dưới cùng monitor.
}
```

Đoạn trên là **mẫu ý tưởng**, chưa phải một file chạy độc lập; `lock` và `condition` phải có khai báo và logic cập nhật. Bài học thuộc mục **Luồng và đồng bộ**, dựa trên **Java SE 17 – JLS §17.1–17.4** và `java.lang.Object` Javadoc. Các chương trình độc lập bên dưới sử dụng Java 17, có hướng dẫn biên dịch, stdout xác định khi chạy bình thường và giải thích phần scheduling không thể dự đoán.

## 1. Mỗi Java object có một monitor và một wait set

Theo Java Language Specification (`17.1, `17.2), một object gắn với:

- **Monitor:** cơ chế mutual exclusion mà `synchronized` và synchronized methods sử dụng.
- **Wait set:** tập các threads đã gọi `wait` trên object trong các điều kiện hợp lệ và chưa hoàn tất giai đoạn chờ theo quy tắc JLS.

Khi object mới được tạo, wait set của nó **rỗng**. Việc thêm/xóa một thread khỏi wait set được mô hình hóa như những hành động nguyên tử. Mỗi object có wait set **riêng biệt**: gọi `notifyAll` trên object A không đánh thức các threads đang `wait` trên object B.

```text
                       OBJECT lock
        ┌────────────────────────────────────┐
        │ Monitor: tại một thời điểm chỉ     │
        │ một thread sở hữu monitor          │
        │                                    │
        │ Wait set: {T2, T3, ...}            │
        │ thread đã gọi lock.wait()          │
        └────────────────────────────────────┘
                  ▲               │
     vào synchronized(lock)      lock.wait()
                  │               ▼
     T1 giữ monitor        T2 nhường monitor và chờ
                  │               │
       lock.notifyAll()           │ được lấy khỏi wait set
          khi T1 còn khóa          ▼
                  │         T2 tranh quyền giành lại monitor
                  │               │
       T1 rời synchronized        │
          → unlock               ▼
                       T2 giành lại lock, wait() trả về
                       và chỉ khi đó tiếp tục kiểm tra condition
```

**Đừng hiểu hình này là scheduler có một FIFO entry queue được Java specification cam kết.** “Entry set/entry list”, “monitor owner”, “wait set” thường được vẽ trong tài liệu JVM để giải thích scheduling. **Wait set** và quyền sở hữu monitor là ngữ nghĩa được JLS mô tả; thứ tự tranh monitor, cấu trúc các hàng đợi native và fairness không được bảo đảm theo những sơ đồ giáo khoa.

### 1.1. Quyền sở hữu monitor (ownership) có nghĩa gì?

Một thread lấy monitor bằng một trong những cách phổ biến:

```java
synchronized (lock) {
    // Thread hiện sở hữu monitor của lock.
}

synchronized void instanceMethod() {
    // Sở hữu monitor của this.
}

static synchronized void classMethod() {
    // Sở hữu monitor của Class object tương ứng.
}
```

Đây là các **snippets** minh họa; hai method thuộc một class thích hợp. Lưu ý:

- `synchronized(lock)` khóa **đúng instance mà `lock` trỏ tới**.
- `synchronized instanceMethod()` khóa **`this`**, không khóa một field `lock` bất kỳ.
- `static synchronized` khóa **Class object**, ví dụ `Sample.class`, không khóa một instance của `Sample`.
- `new Object()` mỗi lần tạo một monitor khác nhau. Không thể dùng hai monitor không liên quan rồi mong `wait/notify` giao tiếp.
- Một thread đang `synchronized(other)` không tự động có quyền gọi `lock.wait()`.

### 1.2. Monitor reentrant

Intrinsic monitors trong Java có tính **reentrant**: thread đã giữ monitor có thể đi vào synchronized block/method trên cùng object thêm lần nữa mà không tự deadlock.

```java
synchronized (lock) {
    synchronized (lock) {
        // Vẫn chỉ một thread sở hữu lock; ownership reentrant.
    }
}
```

Trong mô hình JLS, nếu thread giữ monitor của object `lock` qua **n lần lock actions chưa được ghép unlock**, lời gọi `lock.wait()` hợp lệ phải **nhường toàn bộ n mức sở hữu trên chính monitor đó**. Khi đủ điều kiện trở lại, thread phải **giành lại cùng số mức** để tiếp tục.

Điều này khác với chỉ “giảm lock count một lần”. Nếu `wait()` không nhường hết các mức sở hữu lock, thread khác sẽ không thể vào monitor để thay đổi trạng thái và gửi notification trong nhiều trường hợp.

## 2. Chữ ký ba nhóm API `Object`

Java 17 định nghĩa:

```java
public final void wait() throws InterruptedException;
public final void wait(long timeoutMillis) throws InterruptedException;
public final void wait(long timeoutMillis, int nanos)
        throws InterruptedException;

public final native void notify();
public final native void notifyAll();
```

Các signature này mô tả API; không phải file chạy. Bản `wait()` không giới hạn thời gian tương đương `wait(0L, 0)`; bản `wait(long)` với tham số `0` là đợi vô hạn cho tới khi có sự kiện đánh thức/interrupt/spurious wakeup, không phải “đợi 0 ms rồi trả về”.

| Phương thức | Cần sở hữu monitor của receiver? | Tác động chính |
| --- | --- | --- |
| `lock.wait()` | **Có** | Thêm current thread vào wait set của `lock`; nhường monitor ấy và đợi sự kiện |
| `lock.wait(millis)` | **Có** | Như trên nhưng cho phép timeout khi thời gian tương ứng trôi qua |
| `lock.wait(millis, nanos)` | **Có** | Timed wait với đơn vị millisecond + nanosecond component hợp lệ |
| `lock.notify()` | **Có** | Chọn **một thread tùy ý** trong wait set của `lock` để cho phép nó tranh lock trở lại |
| `lock.notifyAll()` | **Có** | Loại **tất cả threads** đang ở wait set của `lock` để chúng có thể tranh lock trở lại |
| `Thread.sleep(...)` | **Không** cần monitor | Tạm ngủ theo thời gian, **không tự nhả monitor** đã giữ |
| `Thread.join()` | Caller không bắt buộc sở hữu target monitor | Đợi target thread kết thúc theo API, không phải cơ chế signaling nghiệp vụ do caller tự viết |

Cả `wait`, `notify` và `notifyAll` đều là **instance methods của `Object`**, không phải static methods của `Thread`. Muốn điều phối bằng monitor `queueLock` thì gọi `queueLock.wait()`/`queueLock.notifyAll()`.

## 3. Một lời gọi `wait()` hợp lệ đi qua những bước nào?

Theo JLS 17.2.1, một thread T gọi `m.wait()` sau khi đã giữ monitor của object `m`:

1. Xác minh T **đang sở hữu monitor `m`**. Nếu không, ném `IllegalMonitorStateException`.
2. Xử lý điều kiện argument/tình trạng interrupted theo đặc tả.
3. T được **đưa vào wait set của `m`**, và nhường **toàn bộ n lần lock** đang giữ trên `m`.
4. T ở trạng thái chờ cho tới khi được loại khỏi wait set bởi notification, interrupt, timed wait expiry hoặc spurious wakeup.
5. **T cố giành lại monitor `m`**, cùng độ sâu reentrant phù hợp.
6. Chỉ **sau khi đã giành lại monitor**, `wait()` mới trả về bình thường hoặc ném `InterruptedException` tương ứng.

```text
T đang chạy trong synchronized(m)
    │ sở hữu monitor m
    ▼
m.wait()
    │ add T to wait set(m), release monitor m
    ▼
T đợi (không thực thi code sau wait)
    │ notify / notifyAll / interrupt / timeout / spurious
    ▼
T rời wait set(m)
    │
    ▼
T PHẢI tranh lại monitor m
    │ nếu thread khác đang giữ m → vẫn không vào được
    ▼
T giành lại monitor m (restored reentrancy)
    │
    ├─ interrupted theo trường hợp áp dụng → InterruptedException
    └─ bình thường → quay lại kiểm tra predicate trong while
```

**Điểm cốt lõi:** Rời wait set ≠ đang sở hữu monitor. “Đã được `notify`” chỉ là một bước cho phép thread tiếp tục cạnh tranh quyền sở hữu monitor; không phải một lệnh cưỡng chế nó chạy ngay lập tức.

### 3.1. `wait()` nhả monitor nào?

Nó chỉ nhả monitor của **object được gọi `wait()`**, không tự nhả tất cả các monitor mà thread đang giữ.

```java
synchronized (outer) {
    synchronized (inner) {
        inner.wait();
        // Vẫn đang giữ outer trong lúc chờ inner!
    }
}
```

Đây là snippet cần khai báo `outer`/`inner` và xử lý `InterruptedException`. Nếu thread khác cần monitor `outer` trước khi có thể thay đổi dữ liệu rồi gọi `inner.notifyAll()`, bạn có thể tạo **deadlock**. Không dựa vào `wait()` để tự giải phóng toàn bộ các locks đang nắm giữ.

### 3.2. Thread bị đánh thức có chắc chạy tiếp ngay không?

Không. `notifyAll()` có thể làm nhiều threads rời wait set, nhưng chúng chỉ có thể giành monitor **mỗi lần một thread**. Thread vừa gọi `notifyAll()` vẫn giữ monitor cho đến khi thoát khối `synchronized` hoặc trả về từ synchronized method.

Trong các ví dụ, một notifier có thể cập nhật `ready=true` và gọi `notifyAll()`, nhưng nếu còn thực hiện nhiều phép tính tốn thời gian **trong cùng synchronized block**, waiters phải chờ lock được nhả. Đây là lý do nên làm critical sections ngắn, không làm I/O blocking không cần thiết trong monitor.

## 4. `notify()` và `notifyAll()` thay đổi wait set, không nhả monitor

### 4.1. `notify()` đánh thức một thread nào?

Nếu wait set của `m` không rỗng, `m.notify()` **chọn một thread tùy ý** từ wait set để loại khỏi set. Java **không bảo đảm**:

- Chọn thread vào wait set sớm nhất (FIFO).
- Chọn thread có priority cao nhất.
- Chọn thread mà predicate hiện đang đúng.
- Chọn theo thứ tự tên/id.
- Đối xử công bằng theo số lần đợi.
- Thread được chọn sẽ giành monitor trước những threads khác đang cạnh tranh.

Nếu wait set **rỗng**, notification **không được lưu lại như một token để dành cho thread đợi sau**. Đây là nguồn của lỗi **lost notification** nếu code chỉ dựa vào sự kiện gọi notify mà không lưu trạng thái điều kiện.

### 4.2. `notifyAll()` đánh thức tất cả rồi chúng làm gì?

`notifyAll` đưa mọi threads hiện có trong wait set của **object nhận lời gọi** sang tình huống có thể tranh monitor trở lại. Sau notifier unlock, một thread giành được monitor, kiểm tra predicate, rồi:

- Nếu điều kiện đúng, thực hiện công việc và có thể cập nhật state.
- Nếu điều kiện sai, quay lại `wait()` theo vòng `while`, nhường monitor tiếp.
- Những threads khác đợi lượt giành monitor, không được phép cùng lúc vào critical section.

Gọi `notifyAll` **không đồng nghĩa** “tất cả threads được làm công việc ngay” và **không bảo đảm** tất cả sẽ thấy predicate đúng sau đó. Khi nhiều waiters cần cùng một tài nguyên, chỉ một số thread có thể xử lý, các thread còn lại tiếp tục đợi.

### 4.3. Nên chọn `notify` hay `notifyAll`?

- **`notifyAll`** thường là lựa chọn an toàn hơn khi nhiều loại predicates (producers chờ buffer trống, consumers chờ buffer có hàng) chia sẻ **cùng một wait set**, hoặc khi khó chứng minh thread được notify luôn có thể tiến triển.
- **`notify`** có thể dùng trong thiết kế đơn giản có **một loại điều kiện và chứng minh được một notification đủ cho tiến triển**, nhưng việc chỉ chọn một thread tùy ý khiến thuật toán dễ bị trì trệ nếu chọn “nhầm waiter”.
- `notifyAll` có thể gây **thundering herd**: nhiều threads được đánh thức rồi cùng cạnh tranh lock dù chỉ một thread đủ điều kiện, tốn context switching.
- Nếu cần phân biệt cụ thể producer-condition và consumer-condition, `java.util.concurrent.locks.Condition` có thể cung cấp **nhiều condition queues trên một Lock**.

**Không có quy tắc** “`notifyAll` luôn nhanh hơn” hoặc “`notify` luôn đúng nếu chỉ sửa một biến”. Phải kiểm tra protocol và states thực.

## 5. `IllegalMonitorStateException`: giữ đúng monitor, không phải bất kỳ lock nào

Những đoạn sau là **sai**:

```java
Object lock = new Object();
lock.wait();      // IllegalMonitorStateException nếu không đang sở hữu lock.
lock.notify();    // Tương tự.
lock.notifyAll(); // Tương tự.
```

Phép gọi `wait()` còn có checked `InterruptedException`, nên đoạn là **phản ví dụ** chưa hoàn chỉnh để biên dịch, mục đích chỉ ra lỗi sở hữu monitor.

Đúng:

```java
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
}

synchronized (lock) {
    ready = true;
    lock.notifyAll();
}
```

Đây là snippet cần khai báo `ready`/`lock` và xử lý interruption. Dữ liệu `ready` phải được bảo vệ bằng **cùng monitor** để tránh check-then-wait race.

### 5.1. Giữ lock A rồi gọi `B.wait()` có hợp lệ không?

Không, trừ khi thread cũng đang giữ monitor B.

```java
Object a = new Object();
Object b = new Object();
synchronized (a) {
    b.notifyAll(); // Sai: sở hữu a không phải sở hữu b.
}
```

Gọi notify trên `b` trong khối giữ `a` sẽ ném `IllegalMonitorStateException` nếu thread không sở hữu monitor `b`. Cần nhất quán identity của **object dùng làm monitor** trên cả hai phía.

### 5.2. Một chương trình Java 17 độc lập cho lỗi ownership

Lưu file **`MonitorOwnershipDemo.java`**:

```java
public class MonitorOwnershipDemo {
    public static void main(String[] args) throws InterruptedException {
        Object lock = new Object();

        try {
            lock.notify();
        } catch (IllegalMonitorStateException ex) {
            System.out.println("notifyWithoutOwner=true");
        }

        try {
            lock.wait(1);
        } catch (IllegalMonitorStateException ex) {
            System.out.println("waitWithoutOwner=true");
        }

        synchronized (lock) {
            lock.notifyAll(); // Hợp lệ kể cả khi không có waiter.
            System.out.println("notifyAllWithOwner=true");
        }
    }
}
```

Chạy:

```bash
javac --release 17 MonitorOwnershipDemo.java
java MonitorOwnershipDemo
```

**Stdout chính xác:**

```text
notifyWithoutOwner=true
waitWithoutOwner=true
notifyAllWithOwner=true
```

Không phải mỗi `notifyAll()` cần có waiter để được phép gọi; nó chỉ cần **current thread đang sở hữu receiver monitor**. Khi wait set rỗng, không có thread để đánh thức.

## 6. Guarded block: vòng `while` quan trọng hơn bản thân notification

**Sai:**

```java
synchronized (lock) {
    if (!ready) {
        lock.wait();
    }
    useSharedState();
}
```

Đây là snippet minh họa với `ready` và `useSharedState()` chưa khai báo. Nguy cơ:

1. Thread có thể tỉnh do *spurious wakeup*.
2. Thread có thể được notify vì **điều kiện khác**.
3. Thread khác có thể giành monitor **trước** thread vừa tỉnh, tiêu thụ resource hoặc thay state.
4. Timed wait có thể hết hạn dù predicate vẫn sai.

**Đúng:**

```java
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
    useSharedState();
}
```

Vòng `while` kiểm tra **predicate thực tế**, không kiểm tra đơn giản “đã nhận tín hiệu hay chưa”. Khi waiter giành lại monitor, nó đánh giá lại trạng thái mới nhất được bảo vệ bởi cùng monitor.

### 6.1. Tại sao không kiểm tra predicate ngoài `synchronized`?

**Phản ví dụ lost-notification race:**

```java
if (!ready) {             // Đọc ngoài synchronized(lock): có thể race.
    synchronized (lock) {
        lock.wait();
    }
}
```

Timeline:

```text
T1 waiter: đọc ready == false
T2 notifier: synchronized(lock) {
                 ready = true;
                 lock.notifyAll();    // wait set còn rỗng
             }
T1 waiter: vào synchronized(lock), gọi lock.wait()
           → rơi vào wait set, nhưng notification trước đó đã mất
           → có thể chờ mãi dù ready đã true
```

Chỉ dùng một `volatile boolean ready` **không sửa được check-then-wait không nguyên tử** nếu thiếu protocol chung. Điều cần có là **kiểm tra predicate và quyết định wait dưới cùng monitor**, trong khi phía notifier **cập nhật predicate và gửi notify trong cùng monitor**.

### 6.2. Notification không lưu trạng thái

`notify/notifyAll` không phải message queue, semaphore permit hoặc persistent event. Nếu notifier phát tín hiệu khi không có waiter, tín hiệu **không được lưu**; nhưng nếu notifier cập nhật `ready=true` dưới monitor, thread đến sau **thấy trạng thái đã đúng** và không cần wait.

Vì vậy, trong thiết kế tốt:

- **Predicate** là nguồn sự thật.
- **Notification** chỉ là gợi ý đánh thức những threads có thể cần **đọc lại predicate**.
- **Monitor** kết nối phép đọc/ghi predicate với việc vào/rời wait set một cách an toàn theo giao thức Java.

## 7. Java 17 demo 1 — một Gate có điều kiện không bị mất tín hiệu

Tạo file **`MonitorGateDemo.java`**:

```java
public class MonitorGateDemo {
    static final class Gate {
        private boolean open;

        synchronized void awaitOpen() throws InterruptedException {
            while (!open) {
                wait(); // Receiver là this, monitor đang giữ cũng là this.
            }
        }

        synchronized void open() {
            open = true;
            notifyAll(); // Thức dậy để kiểm tra lại open.
        }

        synchronized boolean isOpen() {
            return open;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Gate gate = new Gate();

        Thread worker = new Thread(() -> {
            try {
                gate.awaitOpen();
                System.out.println("workerPassed=true");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                System.out.println("workerInterrupted=true");
            }
        });

        worker.start();
        gate.open();
        worker.join();

        System.out.println("gateOpen=" + gate.isOpen());
    }
}
```

Chạy:

```bash
javac --release 17 MonitorGateDemo.java
java MonitorGateDemo
```

**Stdout xác định trong lần chạy thông thường không bị interrupt từ bên ngoài:**

```text
workerPassed=true
gateOpen=true
```

Giải thích:

- Nếu worker vào `awaitOpen()` **trước** khi main gọi `gate.open()`, worker thấy `open=false` rồi gọi `wait()`, nhường monitor. Main sau đó vào `open()`, ghi `open=true` và `notifyAll()`.
- Nếu main gọi `gate.open()` **trước** khi worker vào `awaitOpen()`, worker thấy `open=true` và **không gọi `wait()`**. Tín hiệu không cần được lưu vì **predicate đã được lưu**.
- Sau `worker.join()`, main mới in `gateOpen=true`. `join()` đảm bảo worker kết thúc trước dòng in cuối, nên **thứ tự hai dòng ổn định**.
- Ví dụ **không chứng minh** worker thực sự đã đi vào wait set ở mọi run; nó chứng minh **protocol dùng state + monitor vẫn đúng dưới cả hai interleavings**.

Không thêm `Thread.sleep(100)` để “ép worker chờ” rồi đưa ra kết luận chắc chắn. Sleep chỉ ảnh hưởng xác suất scheduling, không tạo một synchronization handshake đáng tin.

## 8. Thread States: WAITING, BLOCKED, TIMED_WAITING

Java API `Thread.State` có các trạng thái quan sát được:

| Trạng thái | Ví dụ tình huống | Ghi chú |
| --- | --- | --- |
| **RUNNABLE** | Đang thực thi hoặc đủ điều kiện được JVM/OS schedule | Không bảo đảm đang chiếm CPU thật sự |
| **BLOCKED** | Đang đợi vào một intrinsic monitor vì thread khác sở hữu | Đợi **quyền lock**, khác đợi predicate trong wait set |
| **WAITING** | `Object.wait()` không timeout, `Thread.join()` không timeout, `LockSupport.park()` | Đợi vô hạn tới khi có sự kiện phù hợp |
| **TIMED_WAITING** | `Object.wait(timeout)`, `Thread.sleep(timeout)`, `Thread.join(timeout)` | Đợi có hạn; không phải thời gian thực cứng |
| **TERMINATED** | Thread đã hoàn tất | Không nằm trong monitor wait set |
| **NEW** | Thread object chưa `start()` | Chưa tham gia thực thi |

Đặc biệt:

```text
T1 gọi lock.wait() khi giữ lock
    │
    └─ WAITING (đã nhả monitor và ở wait set)

T2 gọi lock.notify() nhưng còn giữ lock
    │
    └─ T1 ra khỏi wait set; T1 phải đợi giành monitor
       → có thể quan sát trạng thái BLOCKED

T2 rời synchronized(lock)
    │
    └─ T1 có thể giành lock và tiếp tục (RUNNABLE)
```

**Đây là mô hình khái niệm về trạng thái**, không phải một lịch trạng thái API phải chuyển đúng tại từng nanosecond. Polling `getState()` là một snapshot có race; trạng thái có thể thay đổi ngay sau khi đọc và không nên dùng để điều khiển thuật toán signaling.

### 8.1. `Thread.sleep` không giống `Object.wait`

Trong:

```java
synchronized (lock) {
    Thread.sleep(1000);
}
```

thread **vẫn giữ monitor `lock`** trong khi sleep. Không có thread nào khác có thể vào `synchronized(lock)` cho tới khi thread ngủ thoát critical section (trừ chính thread reenter). `Thread.sleep` không đưa thread vào wait set của `lock` và không tự nhả monitor.

Ngược lại, `lock.wait(1000)` khi sở hữu lock **nhường monitor `lock`**, cho phép thread khác giành lock và cập nhật predicate. Nhưng bản timed wait vẫn đòi kiểm tra predicate trong vòng `while`.

## 9. `wait()` có thể trở lại bởi những nguyên nhân nào?

JLS 17.2 mô tả một thread có thể rời wait set do:

1. Được chọn bởi `notify()` trên đúng object.
2. `notifyAll()` trên đúng object.
3. Thread bị `interrupt()`.
4. **Timed wait** đến thời điểm hết hạn theo quy tắc thời gian.
5. **Spurious wakeup:** JVM có thể loại thread khỏi wait set dù không có explicit notification/interrupt/timeout phù hợp.

Mọi trường hợp này đều dẫn đến **bước giành lại monitor trước khi hoàn tất wait action**.

### 9.1. Spurious wakeup là gì?

Một thread gọi `wait()`, sau đó có thể được cho phép trở lại dù chưa có notifier nào báo trạng thái nghiệp vụ đã thay đổi. Java specification cho phép điều này. Một chương trình đúng **không được** giả định “`wait()` trả bình thường ⇒ điều kiện đã đúng”.

Do đó:

```java
synchronized (lock) {
    while (queue.isEmpty()) {
        lock.wait();
    }
    Item item = queue.remove();
}
```

Đây là snippet, cần `queue`, `Item` và xử lý interrupt. Khi tỉnh dậy vì bất cứ lý do nào mà queue vẫn rỗng, thread lại vào `wait()`. Không phải bug của JVM mà là **ngữ nghĩa được phép**.

### 9.2. Interrupt là yêu cầu hủy/đánh thức có cơ chế riêng

Nếu thread đang đợi bằng `wait()` và bị interrupt theo tình huống JLS mô tả, nó rời wait set, phải giành lại monitor, rồi `wait()` ném `InterruptedException`, đồng thời **xóa interrupt status** khi ném exception.

Điểm quan trọng:

- `interrupt()` không “giết” thread một cách cưỡng bức.
- Không mặc định xem mọi interruption là signal `ready=true`.
- Nếu method của bạn muốn cho caller xử lý cancellation, hãy **propagate `InterruptedException`** khi API cho phép.
- Nếu thread worker bắt exception ở tầng không ném được checked exception, thường nên **khôi phục status bằng `Thread.currentThread().interrupt()`** và dừng/hủy công việc theo policy.
- Không catch `InterruptedException` rồi bỏ qua và tiếp tục vòng chờ vô hạn nếu hệ thống cần cancellation.

### 9.3. Notification và interruption đến gần nhau

JLS 17.2.4 giải thích: nếu một waiter đồng thời có thể được notify và interrupt, JVM phải hành xử **tương ứng một thứ tự hợp lệ** giữa các sự kiện. Thread có thể:

- Trở lại bình thường từ `wait()` với interruption **vẫn pending**, hoặc
- Ném `InterruptedException` theo tình huống interrupt được xử lý trước và xóa status.

Không được dạy “khi notify và interrupt cùng lúc thì exception chắc chắn thắng”. Java còn đặt quy tắc để notification không bị đánh mất do cuộc đua với interrupt trên một wait set hiện có.

### 9.4. Java 17 demo 2 — interrupt một waiter mà không lệ thuộc sleep

Lưu file **`InterruptWaitDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;

public class InterruptWaitDemo {
    public static void main(String[] args) throws InterruptedException {
        Object lock = new Object();
        CountDownLatch entered = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            synchronized (lock) {
                entered.countDown();
                try {
                    // Vòng lặp chống spurious wakeups:
                    for (;;) {
                        lock.wait();
                    }
                } catch (InterruptedException ex) {
                    System.out.println("interruptCaught=true");
                    System.out.println("interruptFlagCleared=" +
                            !Thread.currentThread().isInterrupted());
                }
            }
        });

        worker.start();
        entered.await();
        worker.interrupt();
        worker.join();
        System.out.println("done");
    }
}
```

Chạy:

```bash
javac --release 17 InterruptWaitDemo.java
java InterruptWaitDemo
```

**Stdout xác định khi chương trình chạy bình thường:**

```text
interruptCaught=true
interruptFlagCleared=true
done
```

**Vì sao chắc hơn dùng `sleep`?** `CountDownLatch` bảo đảm worker đã vào vùng đồng bộ và báo sẵn sàng trước khi main gọi interrupt. Main không cần chờ một số milliseconds được đoán. Nếu interrupt tới **trước** lời gọi `wait()`, `wait()` thấy interrupt status rồi ném exception; nếu tới **trong lúc** worker đang wait, waiter rời wait set theo quy tắc. Nếu có spurious wakeup, vòng `for (;;)` vẫn quay lại `wait()`, không in nhầm kết quả.

Đây là một demo học **interruption semantics**, không phải mẫu business loop. Trong code thật, vòng `while(!condition)` tốt hơn một vòng vô tận trừ khi đang minh họa chờ interrupt như trong bài.

## 10. Timed wait: tránh tưởng `wait(timeout)` là một deadline cứng

`wait(long timeoutMillis)` và `wait(long timeoutMillis, int nanos)` cho phép chờ **tối đa theo ý định timeout của wait set**, nhưng thời điểm thread hoàn tất method còn cần **giành lại monitor** và chịu ảnh hưởng scheduling/OS.

- `timeoutMillis` phải **không âm**.
- `nanos` ở overload hai tham số phải trong **0..999999**.
- `wait(0)` và `wait(0, 0)` là **đợi vô hạn** theo quy tắc API.
- `wait(0, 1)` là **timed wait**, không phải vô hạn.
- Thời gian chờ thực và lúc thread được schedule có thể vượt giá trị chỉ định.
- Timed wait có thể kết thúc **trước timeout** bởi notify, interrupt hoặc spurious wakeup.
- Hết timeout **không tự khiến predicate đúng**.

### 10.1. Kiểu đợi có deadline dùng `System.nanoTime`

Nếu yêu cầu “đợi tối đa khoảng X về mặt logic”, phải kiểm tra **thời gian còn lại sau mỗi wakeup**, không gọi lại `wait(timeout)` với toàn bộ timeout sau mỗi spurious wakeup (có thể làm kéo dài liên tục).

Một mô hình minh họa Java 17:

```java
long deadline = System.nanoTime() + timeoutNanos;
synchronized (lock) {
    while (!ready) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) {
            break;  // timeout theo logic; ready vẫn có thể false.
        }

        long millis = remaining / 1_000_000L;
        int nanos = (int) (remaining % 1_000_000L);
        lock.wait(millis, nanos);
    }
}
```

Đây là **snippet** cần khai báo `timeoutNanos` (nên là duration dương có giới hạn hợp lý), `lock` và `ready`; caller phải xử lý `InterruptedException`. `System.nanoTime` phù hợp đo khoảng thời gian trôi qua hơn wall-clock. Phép cộng deadline cần tránh overflow với các khoảng thời gian cực lớn; với timeout thông thường hợp lý, đây là mẫu cách kiểm tra **remaining duration**, không bảo đảm thread sẽ được schedule đúng deadline.

### 10.2. Tại sao không dùng `System.currentTimeMillis` làm đồng hồ deadline?

Wall-clock có thể được đồng bộ lại/chỉnh bởi hệ điều hành hoặc quản trị viên, gây những thay đổi không tương ứng elapsed duration. `System.nanoTime` đo thời gian tương đối tốt hơn cho các vòng chờ. Dù vậy, scheduler/lock contention vẫn có thể khiến việc thực tế tiếp tục code vượt deadline.

Với code hiện đại, thường nên ưu tiên API đã thiết kế timeout như `BlockingQueue.poll(timeout, unit)`, `Condition.awaitNanos` hoặc `CountDownLatch.await(timeout, unit)` thay vì tự viết nhiều phép toán thời gian.

## 11. Java Memory Model: notify không thay thế quy tắc happens-before

Dữ liệu dùng làm điều kiện phải được đọc/ghi **có đồng bộ phù hợp**. Với intrinsic monitor, mỗi lần thoát `synchronized(lock)` thực hiện unlock và lần vào `synchronized(lock)` sau đó thực hiện lock. Theo JLS §17.4.4–17.4.5:

> **Unlock trên monitor `m` happens-before một lần lock sau đó trên cùng monitor `m`** theo synchronization order.

Vì thế:

```text
T2 notifier, giữ lock:
   ready = true;
   payload = value;
   lock.notifyAll();
   [unlock lock] ────── happens-before ─────► [lock lock] T1 waiter
                                               đọc ready, payload
```

Điều bảo đảm visibility trong mẫu này dựa trên **monitor unlock/lock và guarded state**, không phải một sự thần kỳ rằng bản thân `notify` “copy mọi biến sang thread khác” mà không cần cùng lock.

### 11.1. Có cần `volatile` cho predicate đã guard bằng synchronized không?

Nếu **mọi truy cập** đến `ready`/`payload` đều nằm trong đúng `synchronized(lock)`, cơ chế unlock/lock đã thiết lập visibility và ordering cần thiết. `volatile` không phải điều kiện bắt buộc chỉ vì nhiều threads dùng biến.

Ngược lại, nếu một phía viết `ready` ngoài monitor và phía còn lại đọc trong monitor, protocol có thể hỏng. Đặt `volatile` cho riêng `ready` không thay thế yêu cầu **atomic check-then-wait** của guarded block.

### 11.2. Tại sao `notify()` không đủ khi thiếu predicate?

Một thread được notify **không được cấp dữ liệu** chỉ bởi tín hiệu. Khi giành monitor, nó phải đọc shared state để biết có job nào, buffer còn chỗ không, hoặc task đã hủy chưa. Nếu không có state invariant rõ ràng, notification là một sự kiện dễ bị miss và khó debug.

### 11.3. `Thread.start()` và `Thread.join()` cũng có happens-before

JLS §17.4.4 nói start action synchronizes-with hành động đầu tiên của thread mới; việc nhận biết thread đã kết thúc qua `join()` tạo quan hệ synchronization tương ứng. Đây là lý do các ví dụ dùng `join()` để **sắp xếp stdout cuối cùng**, nhưng `join` không thay lock nội tại để quản lý trạng thái được chia sẻ **khi các threads còn đang thực thi song song**.

## 12. Producer–consumer: wait set và monitor cùng một object

Bài toán **one-slot buffer**:

- Producer chỉ được `put` khi slot rỗng.
- Consumer chỉ được `take` khi slot có giá trị.
- Nếu không thỏa điều kiện, thread phải nhường monitor và chờ.
- Sau thay đổi trạng thái, thread thông báo để các waiters kiểm tra lại điều kiện.

Một implementation cơ bản:

```java
final class OneSlot {
    private Integer value;  // null = empty

    synchronized void put(int number) throws InterruptedException {
        while (value != null) {
            wait();          // Nhường monitor của this.
        }
        value = number;
        notifyAll();
    }

    synchronized int take() throws InterruptedException {
        while (value == null) {
            wait();
        }
        int result = value;
        value = null;
        notifyAll();
        return result;
    }
}
```

Đây là snippet class; file độc lập ở ngay phần sau. Đọc/ghi `value` và `wait/notifyAll` đều diễn ra dưới **monitor của cùng instance OneSlot**. Điều kiện `value==null` là dữ liệu điều khiển, còn `notifyAll` là tín hiệu giúp threads đã chờ thức dậy để kiểm tra lại.

### 12.1. Vì sao gọi `notifyAll` cả sau `put` lẫn `take`?

Sau `put`, slot chuyển **empty → full**, nên consumer có thể tiếp tục. Sau `take`, slot chuyển **full → empty**, nên producer đang chờ có thể tiếp tục.

Nếu dùng chỉ một wait set và có nhiều producers/consumers, `notify()` có thể chọn thread **không có predicate phù hợp**. Ví dụ buffer đầy nhưng notifier đánh thức một producer khác thay vì consumer cần tiến triển, rồi producer thấy đầy và lại wait. Không có bằng chứng tiến triển trọn vẹn nếu cứ lựa chọn một thread tùy ý. `notifyAll` đánh thức cả hai nhóm, rồi `while` lọc lại đúng điều kiện.

### 12.2. Java 17 demo 3 — chương trình one-slot hoàn chỉnh

Lưu file **`OneSlotDemo.java`**:

```java
import java.util.concurrent.atomic.AtomicInteger;

public class OneSlotDemo {
    static final class OneSlot {
        private Integer value;

        synchronized void put(int number) throws InterruptedException {
            while (value != null) {
                wait();
            }
            value = number;
            notifyAll();
        }

        synchronized int take() throws InterruptedException {
            while (value == null) {
                wait();
            }
            int result = value;
            value = null;
            notifyAll();
            return result;
        }

        synchronized boolean isEmpty() {
            return value == null;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        OneSlot slot = new OneSlot();
        AtomicInteger sum = new AtomicInteger();

        Thread producer = new Thread(() -> {
            try {
                for (int n = 1; n <= 5; n++) {
                    slot.put(n);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        Thread consumer = new Thread(() -> {
            try {
                for (int n = 1; n <= 5; n++) {
                    sum.addAndGet(slot.take());
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();

        System.out.println("sum=" + sum.get());
        System.out.println("empty=" + slot.isEmpty());
        System.out.println("done");
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 OneSlotDemo.java
java OneSlotDemo
```

**Stdout xác định khi không có interrupt ngoài ý muốn:**

```text
sum=15
empty=true
done
```

Giải thích:

1. Producer đặt lần lượt `1, 2, 3, 4, 5`. Slot dung lượng một, nên có lúc producer phải chờ consumer lấy đi.
2. Consumer lấy đúng năm giá trị, cộng thành `1+2+3+4+5=15`. Có thể consumer hoặc producer bắt đầu trước — protocol vẫn đúng.
3. Mỗi lần `wait()` nhường monitor **của cùng object `slot`**, cho thread bên kia vào để cập nhật `value`.
4. `notifyAll` đánh thức waiters nếu có; các thread kiểm tra lại `while` khi giành monitor.
5. `producer.join()` và `consumer.join()` đảm bảo main chỉ in kết quả sau khi hai threads đã xong. Không cần `Thread.sleep` để ép lịch chạy.
6. `AtomicInteger` giúp mô tả kết quả được publish an toàn; vì main đọc sau `join`, visibility cũng được thiết lập qua thread termination. Dùng `AtomicInteger` không thay thế monitor guarding `value`.

**Giới hạn:** Đây là ví dụ học cách dùng monitor, không có close/cancel protocol đầy đủ khi một producer/consumer bị interrupt. Trong hệ thống thực, nếu một worker bất ngờ chết mà phía kia cứ đợi vô hạn, cần thêm **shutdown state** và signaling để tránh treo.

## 13. Vì sao check điều kiện và notify phải trong cùng synchronization protocol?

### 13.1. Tình huống thread A check, thread B update

Gọi `lock.wait()` bên trong synchronized nhưng kiểm tra predicate **ngoài** synchronized tạo khoảng race:

```text
A: if (queueEmpty)              // không guard
B: synchronized(lock) {
     put(item);
     lock.notifyAll();           // A chưa wait; notification mất
   }
A: synchronized(lock) { lock.wait(); }
```

Lúc này queue đã có item nhưng A có thể chờ vô hạn. Sửa bằng việc đặt cả **check và wait** trong cùng critical section, còn producer đặt **update và notify** dưới cùng monitor.

### 13.2. Có cần notify khi predicate không thay đổi?

Notification nên gắn với những **state transitions có thể làm điều kiện của waiter thành true**, thay vì tùy tiện gọi notify mỗi khi chạy qua một function. Tín hiệu quá thừa gây wakeups/cạnh tranh, còn thiếu tín hiệu sau transition quan trọng gây mất tiến triển. Nếu nhiều predicates cùng chia monitor, dùng `notifyAll` khi chưa có chứng minh protocol mạnh hơn.

### 13.3. Không được giữ monitor trong lúc chờ một thứ sẽ cần chính monitor đó

Một deadlock pattern:

```java
synchronized (lock) {
    while (!ready) {
        Thread.sleep(100);
    }
}
```

Đây là phản ví dụ: `sleep` **không nhả `lock`**, nên nếu thread khác cần `synchronized(lock)` để đặt `ready=true`, nó không thể vào, và waiter có thể kẹt mãi. Đúng hơn là `lock.wait()` trong vòng while theo predicate để **nhường monitor**.

## 14. Đối chiếu `Thread.sleep`, `Object.wait`, `Thread.join`, `Condition.await`

| API | Đợi vì điều gì? | Nhả intrinsic monitor? | Cơ chế đánh thức/hoàn tất |
| --- | --- | --- | --- |
| `Thread.sleep(ms)` | Thời gian | **Không** nhả monitor đang giữ | Timeout hoặc interrupt |
| `obj.wait()` | Tín hiệu/sự kiện trên `obj` | **Có, chỉ monitor `obj`** | Notify/notifyAll, interrupt, spurious wakeup |
| `obj.wait(ms)` | Tín hiệu hoặc timeout | **Có, chỉ monitor `obj`** | Notify, interrupt, timeout, spurious |
| `thread.join()` | Target thread kết thúc | Không có contract “nhả mọi monitor của caller” | Target thread termination, interruption, timeout với overload |
| `condition.await()` | Điều kiện qua explicit Lock | **Nhả associated Lock theo contract và lấy lại trước khi return** | `signal`/`signalAll`, interrupt, spurious |
| `LockSupport.park()` | Permit/`unpark`/interrupt/spurious | **Không tự nhả intrinsic monitor** | Permit, interrupt hoặc spurious; caller tự kiểm tra condition |
| `CountDownLatch.await()` | Count về 0 | Không phải intrinsic-monitor wait do caller tự signal | `countDown`, interrupt hoặc timeout overload |
| `BlockingQueue.take()` | Có item trong queue | API tự quản synchronization | Producer `put/offer` hoặc interrupt |

**Cảnh báo:** `join()` trong HotSpot/Java platform có thể sử dụng monitor-wait nội bộ theo phiên bản và implementation, nhưng người sử dụng nên dựa vào **contract API**, không gọi `thread.notifyAll()` như một cách “tự triển khai join”. `Thread` object không nên được dùng làm monitor nghiệp vụ tùy tiện, vì API/runtime còn dùng những cơ chế đồng bộ liên quan thread lifecycle.

## 15. `Condition` trên `ReentrantLock`: nhiều wait sets nghiệp vụ

Intrinsic monitor mỗi object cung cấp **một wait set**, vì vậy producer và consumer cùng chia sẻ waiters trong một set nếu cùng synchronized object.

`ReentrantLock` hỗ trợ nhiều `Condition` objects, mỗi condition có **condition wait queue riêng** liên kết với một explicit Lock. Dùng khi cần quản lý các predicate khác nhau rõ hơn:

```java
final ReentrantLock lock = new ReentrantLock();
final Condition notEmpty = lock.newCondition();
final Condition notFull = lock.newCondition();

void put(Item item) throws InterruptedException {
    lock.lock();
    try {
        while (queue.isFull()) {
            notFull.await();
        }
        queue.add(item);
        notEmpty.signal();
    } finally {
        lock.unlock();
    }
}

Item take() throws InterruptedException {
    lock.lock();
    try {
        while (queue.isEmpty()) {
            notEmpty.await();
        }
        Item result = queue.remove();
        notFull.signal();
        return result;
    } finally {
        lock.unlock();
    }
}
```

Đây là **snippet minh họa**, cần import `java.util.concurrent.locks.*`, định nghĩa `Item`/`queue`, và đặt các fields/methods trong class. Nó không phải file runnable độc lập.

- `await()` nhường associated `ReentrantLock` rồi đợi condition.
- `signal()`/`signalAll()` phải được gọi với lock ownership phù hợp theo Condition implementation thông thường.
- Sau notification, waiter vẫn phải **giành lại Lock**, rồi kiểm tra predicate trong `while`.
- `Condition` không tự mang dữ liệu. Guarded predicate trong `queue` mới quyết định có thể `take/put`.
- Multiple conditions cho phép đánh thức đúng nhóm waiters hơn so với một intrinsic wait set trên `this`, giảm wakeups không cần thiết.

### 15.1. Không được trộn hai hệ đồng bộ không tương thích

**Sai:**

```java
synchronized (lock) {
    condition.await();  // Không phải cách sở hữu ReentrantLock.
}
```

Ở đây biến `lock` được giả định là một `ReentrantLock` object. `synchronized(lock)` chỉ sở hữu **intrinsic monitor của instance ReentrantLock**, **không** gọi `lock.lock()` và không thỏa ownership rule của `Condition.await`. Với `ReentrantLock`/`Condition`, phải dùng `lock.lock()/unlock()` theo protocol.

Ngược lại, sở hữu `ReentrantLock` bằng `lock.lock()` không tự cấp quyền gọi `lock.wait()` trên intrinsic monitor của chính `ReentrantLock` object. Hai cơ chế có ownership khác nhau.

## 16. Khi nào `notify()` có thể gây trì trệ với nhiều conditions?

Giả sử một hàng đợi dung lượng 1:

- Producers P1/P2 chờ “slot **trống**”.
- Consumers C1/C2 chờ “slot **có item**”.
- Cả hai nhóm đang dùng **wait set của cùng monitor `queue`**.

Nếu thread gọi `notify()` sau một transition cần đánh thức consumer mà nó lại chọn producer không đủ điều kiện, producer thức dậy nhưng thấy slot vẫn đầy và trở lại wait. Nếu không có future notifications từ threads khác, hệ thống có thể **không tiến triển** mặc dù một consumer đủ điều kiện cũng đang chờ.

```text
Wait set(queue) = {P1, P2, C1, C2}
state: buffer FULL

notify() → chọn P1 (arbitrary)
P1 giành monitor → thấy FULL → lại wait

C1/C2 vẫn trong wait set, dù có item để lấy.
Không có bảo đảm sẽ có tín hiệu tiếp theo.
```

Ví dụ trên mô tả **rủi ro chọn sai loại waiter trong các giao thức đa điều kiện**; để kết luận deadlock cụ thể cần xem interleavings và invariant của toàn bộ producer-consumer implementation. Nhưng nó đủ chứng minh rằng `notify()` không tự biết predicate nghiệp vụ của từng thread.

Giải pháp:

- Dùng `notifyAll()` với `while` để mọi waiters được kiểm tra lại, hoặc
- Dùng các `Condition` queues riêng `notEmpty/notFull` rồi `signal` nhóm thích hợp, hoặc
- Dùng sẵn `BlockingQueue` nếu không cần tự viết đồng bộ cấp thấp.

### 16.1. Thundering herd của `notifyAll` là trade-off

Nếu có 1.000 waiters và chỉ một token/tài nguyên, notifyAll có thể gây nhiều thread transitions và lock contention; phần lớn thức dậy chỉ để lại wait. Trong high-load production, có thể chọn abstraction như `Semaphore`, `Condition`, `BlockingQueue` hoặc work-queue pool thay vì intrinsic wait set chung.

Không hy sinh correctness chỉ vì giảm số lần wakeup: trước tiên chứng minh state machine không mất tín hiệu, không starvation, không deadlock trong các interleavings hợp lệ.

## 17. Interrupt và shutdown trong các hệ thống thực tế

Trong `OneSlotDemo`, worker catch interruption, khôi phục flag rồi kết thúc. Đó là đủ cho ví dụ không hủy giữa chừng, nhưng một dịch vụ production cần **lifecycle protocol** rõ hơn:

- Biến `closed` hoặc `stopping` được guard bằng cùng monitor.
- Khi đóng, đặt `closed=true` dưới monitor và gọi `notifyAll()`.
- Trong các loops `while`, kiểm tra đồng thời điều kiện nghiệp vụ và `closed` để quyết định dừng/throw exception.
- Quy định xử lý dữ liệu đã nằm trong queue: drain trước khi đóng hay từ chối hết.
- Xác định liệu `interrupt()` là yêu cầu cancel một operation hay đóng cả pipeline.
- Với nhiều workers, đảm bảo không thread nào chờ vô hạn sau khi đối tác thất bại.

Mẫu ý tưởng:

```java
synchronized (lock) {
    while (queue.isEmpty() && !closed) {
        lock.wait();
    }
    if (queue.isEmpty() && closed) {
        return; // hoặc tín hiệu end-of-stream tùy API.
    }
    // xử lý item
}
```

Đoạn này là **snippet** cần biến/kiểu/method phù hợp. `closed` cần được cập nhật cùng lock rồi notifyAll để waiters đang đợi thức dậy nhìn thấy thay đổi trạng thái và không quay lại wait mãi.

### 17.1. Đừng nuốt `InterruptedException` vô điều kiện

**Phản mẫu:**

```java
while (!ready) {
    try {
        lock.wait();
    } catch (InterruptedException ignored) {
        // Bỏ qua mọi yêu cầu dừng.
    }
}
```

Đây là snippet bên trong `synchronized(lock)` phù hợp. Nếu một thread bị cancel và `ready` không bao giờ true, nó có thể chờ lại mãi.

**Tốt hơn:** propagate `InterruptedException` từ method nếu API cho phép; nếu không, khôi phục flag và thoát tác vụ theo quyết định rõ ràng.

### 17.2. Không notify bằng cách interrupt bừa một thread khác

`Thread.interrupt()` là một **cơ chế yêu cầu interruption**, không phải replacement của predicate `ready`. Nếu thread đợi queue item và bạn chỉ interrupt mà không sửa state, nó có thể ném exception và hủy thao tác, không phải xử lý dữ liệu mong muốn.

Dùng đúng primitive đúng semantics: `notify/notifyAll` để gợi ý thay đổi guarded state; `interrupt` để báo hủy/ngắt; `Semaphore` cho permits; `BlockingQueue` cho trao đổi items.

## 18. Deadlock: giữ lock khác trong khi `wait()`

Một tình huống khó:

```text
T1:
  synchronized(A) {
      synchronized(B) {
          B.wait();    // Nhả B, VẪN GIỮ A
      }
  }

T2:
  synchronized(A) {    // Muốn vào để cập nhật predicate
      synchronized(B) {
          B.notifyAll();
      }
  }

T1 chờ T2 notify trên B.
T2 chờ T1 nhả A.
⇒ Deadlock theo protocol này.
```

**Quan trọng:** `B.wait()` là hợp lệ khi T1 sở hữu B; vấn đề không nằm ở `IllegalMonitorStateException` mà ở việc **không nhả monitor A**. Để sửa, giảm nested locks, không wait khi đang giữ lock khác mà notifier cần, và thống nhất lock order.

### 18.1. Monitor ordering và lock ordering

Nếu một workflow bắt buộc giữ nhiều locks, phải quy định thứ tự lấy chúng trên mọi code paths, giảm phạm vi critical sections và không gọi I/O chặn lâu khi còn giữ lock. Intrinsic monitor không tự phát hiện và giải quyết vòng chờ.

Java `ThreadMXBean.findDeadlockedThreads()` có thể giúp chẩn đoán một số deadlocks liên quan monitors/ownable synchronizers, nhưng **không thay thế reasoning về protocol**. Một chương trình chờ predicate không bao giờ trở thành true cũng có thể “treo” mà không xuất hiện trong deadlock detector vì nó không phải chu trình monitor ownership đơn thuần.

## 19. Phân tích thread dump và thread state

Với HotSpot JDK 17, có thể lấy thread dump qua:

```bash
jcmd <pid> Thread.print
```

Khi quan sát một thread, các trạng thái như `WAITING (on object monitor)`, `TIMED_WAITING (on object monitor)` hoặc `BLOCKED (on object monitor)` có thể giúp phân biệt:

- **WAITING on object monitor:** thread đã gọi `Object.wait` và đang đợi điều kiện/sự kiện.
- **BLOCKED on object monitor:** thread muốn giành monitor nhưng thread khác đang sở hữu.
- **TIMED_WAITING:** một thao tác chờ có timeout (nhưng state chi tiết còn phụ thuộc API và JVM).
- **Lock owner:** thông tin về monitor mà một thread đang giữ (nếu dump có thể suy ra).
- **Stack trace:** vị trí Java-level, ví dụ `Object.wait`, method của queue và synchronized code.

### 19.1. Có đo được trực tiếp wait set bằng public Java API không?

Java standard API không cung cấp `lock.getWaitSet()` trả về toàn bộ danh sách threads đang `wait` trên object. `Thread.getState()` chỉ trả trạng thái snapshot của một thread, không phải một danh sách phân biệt chính xác “wait set của object A”.

Có thể dùng thread dump/JFR/monitor instrumentation để chẩn đoán, nhưng results phụ thuộc implementation/timepoint. Không xây protocol dựa trên việc polling thread states để quyết định có cần gọi notify.

### 19.2. Vì sao một thread đã notify vẫn bị chậm?

Thread nhận notify có thể:

1. Chờ notifier rời synchronized block.
2. Cạnh tranh monitor với nhiều threads khác.
3. Chờ CPU scheduling nếu hệ thống quá tải.
4. Vào lại critical section rồi thấy predicate sai và wait lần nữa.

Vì vậy “đã gọi notifyAll nhưng consumer chưa chạy” không tự chứng minh notifyAll không hoạt động. Phải xem thread dumps, lock owner, guarded state và scheduling.

### 19.3. Log khởi đầu cho monitor contention

Trong ứng dụng có vấn đề hiệu năng, đo **monitor enter contention**, time spent blocked/waiting và số lượng notification/wakeups qua JFR hoặc profiler thích hợp, rồi correlate với throughput/p99 latency. Không giả định `Thread.State.WAITING` đồng nghĩa CPU đang tốn 100%; một thread đang wait thường không thực thi active work.

## 20. Một số thiết kế không nên tự viết bằng `wait/notify`

Các primitives hiện đại giải quyết nhiều loại coordination phổ biến:

| Nhu cầu | API thường phù hợp | Tránh tự viết |
| --- | --- | --- |
| Hàng đợi producer-consumer | `ArrayBlockingQueue`, `LinkedBlockingQueue` | Dễ thiếu shutdown/interrupt/fairness |
| Một thread chờ công việc hoàn tất | `CountDownLatch`, `Future.get`, `CompletableFuture` | Dễ lost notification nếu tự lưu cờ sai |
| Giới hạn số tác vụ đồng thời | `Semaphore` | Dễ xử lý permits sai |
| Nhiều worker gặp nhau tại pha | `CyclicBarrier`, `Phaser` | Dễ lỗi theo generation/phase |
| Cần nhiều condition trên cùng lock | `ReentrantLock` + `Condition` | Một wait set khó chọn đúng nhóm |
| Atomic state đơn giản | `AtomicBoolean`, `AtomicInteger`, `VarHandle` | Không cần wait set nếu chỉ cập nhật atomic không chặn |
| Chờ kết quả bất đồng bộ | `CompletableFuture` hoặc futures | Không nên tự wait trên `Thread` object |

Các API này không “đánh bại mọi deadlock” hay “tự thay thế hoàn toàn review concurrency correctness”; chúng cung cấp **abstractions có contract rõ hơn** so với tự thiết kế nhiều vòng wait/notify và lifecycle state.

## 21. Những hiểu lầm phổ biến

1. **“`Object.wait()` có thể gọi khi không giữ lock.”** Sai; cần monitor ownership của chính receiver.
2. **“Giữ `synchronized(a)` là đủ để gọi `b.wait()`.”** Sai nếu không giữ monitor `b`.
3. **“`wait()` chỉ nhả một mức reentrant lock.”** Sai; nhả toàn bộ ownership trên chính monitor receiver và giành lại tương ứng.
4. **“`wait()` nhả mọi locks của thread.”** Sai; chỉ monitor object nhận lời gọi.
5. **“`notify()` tự nhả monitor ngay.”** Sai; notifier tiếp tục giữ tới khi unlock theo protocol.
6. **“Thread được notify lập tức sở hữu monitor.”** Sai; phải cạnh tranh giành lại lock.
7. **“`notify()` luôn chọn thread đợi lâu nhất.”** Sai; không có FIFO/fairness guarantee.
8. **“`notifyAll()` cho phép mọi waiters vào critical section cùng lúc.”** Sai; monitor vẫn mutual exclusion.
9. **“`notify()` trước `wait()` được lưu lại như một permit.”** Sai; nếu không có waiter, notification không được tích lũy.
10. **“Chỉ cần `volatile ready` là check-then-wait ngoài synchronized an toàn.”** Sai; cần atomic guarded check/wait protocol.
11. **“`if (!ready) wait()` luôn đúng.”** Sai; spurious wakeups, state changes và nhiều conditions yêu cầu `while`.
12. **“`wait()` chỉ trả về khi có `notify()`.”** Sai; notifyAll, interrupt, timeout và spurious wakeup.
13. **“Sau timeout thì predicate đã true.”** Sai; timeout chỉ là sự kiện cho waiter tiếp tục nếu giành được lock.
14. **“`wait(0)` nghĩa không đợi.”** Sai; zero timeout tương đương indefinite wait.
15. **“`Thread.sleep()` nhả monitor như `wait()`.”** Sai; sleep không giải phóng monitor.
16. **“`Thread.State.WAITING` và `BLOCKED` giống nhau.”** Sai; một bên đợi sự kiện, một bên đợi lock.
17. **“`notifyAll` luôn hiệu quả nhất.”** Sai; có thể wake nhiều waiters không cần thiết.
18. **“`notify` luôn nhanh hơn và đúng hơn `notifyAll`.”** Sai; chọn sai loại waiter có thể khiến hệ thống không tiến triển.
19. **“Interrupt lúc wait luôn giết thread.”** Sai; thường báo `InterruptedException`, caller quyết định.
20. **“Khi wait ném `InterruptedException`, interrupt flag luôn còn true.”** Sai; status bị clear khi ném exception theo API.
21. **“Notification đồng thời với interrupt luôn ưu tiên interrupt.”** Sai; JLS mô tả nhiều thứ tự hợp lệ.
22. **“Thêm một `notifyAll` đủ thay việc cập nhật guarded state.”** Sai; predicate vẫn là nguồn sự thật.
23. **“Đang synchronized static method là đang khóa mọi instances.”** Sai; khóa `Class` object tương ứng.
24. **“Java có `lock.getWaitSet()` chuẩn trả chính xác danh sách waiters.”** Sai; không có API như vậy.
25. **“`ReentrantLock.lock()` cấp quyền gọi `reentrantLock.wait()`.”** Sai; explicit Lock ownership khác intrinsic monitor ownership.
26. **“`Condition.await()` không cần giữ associated Lock.”** Sai; Condition của ReentrantLock đòi lock ownership phù hợp.
27. **“Thread dump `BLOCKED` luôn nghĩa deadlock.”** Sai; có thể chỉ monitor contention tạm thời.
28. **“`join` và `notifyAll` là hai phương thức hoán đổi cho nhau.”** Sai; contract khác.
29. **“Không dùng `sleep` thì concurrent code chắc chắn không có race.”** Sai; tính đúng phụ thuộc protocol và synchronization.
30. **“`wait/notify` là giải pháp tốt nhất cho mọi concurrent queue.”** Sai; blocking queues và latches thường an toàn, rõ ràng hơn.
31. **“Thấy waiter đã rời wait set là đủ kết luận đã đọc shared data mới.”** Sai; còn phải giành monitor, thực thi dưới đồng bộ và kiểm tra predicate.
32. **“Synchronized chỉ ngăn chạy đồng thời, không có visibility guarantee.”** Sai; unlock/lock tạo happens-before theo JMM.
33. **“`notifyAll` làm Java tự kiểm tra mọi predicate.”** Sai; code của mỗi waiter phải có `while` để tự kiểm tra.
34. **“Giữ lock B trong `B.wait` sẽ khiến thread A không thể cập nhật B.”** Sai: `wait` nhả B để notifier có thể giành nó; vấn đề thường ở locks **khác** vẫn bị giữ.
35. **“Dùng `wait/notify` đúng thì hệ thống đảm bảo hard real-time deadline.”** Sai; scheduling/fairness/timing không có cam kết như thế.

## 22. Câu hỏi tự kiểm tra (có đáp án)

**Câu 1.** Tại sao `wait()` không thể được gọi bên ngoài synchronized block trên cùng object?

**Đáp án:** Vì phải sở hữu monitor receiver. Nếu không, `IllegalMonitorStateException`. Monitor phối hợp thao tác kiểm tra predicate với việc vào wait set an toàn.

**Câu 2.** Một thread giữ monitor `m` hai mức reentrant rồi gọi `m.wait()`. Nó nhả bao nhiêu mức?

**Đáp án:** Nhả **toàn bộ** số mức sở hữu trên monitor `m`, rồi giành lại cùng số mức trước khi tiếp tục; không nhả locks của những objects khác.

**Câu 3.** Notifier gọi `m.notifyAll()` khi còn ở giữa `synchronized(m)`. Waiters có chạy tiếp code sau `wait()` ngay không?

**Đáp án:** Không. Chúng phải giành lại `m` sau khi notifier unlock và scheduling cho phép.

**Câu 4.** `m.notify()` có FIFO guarantee không?

**Đáp án:** Không, chọn một thread tùy ý từ wait set.

**Câu 5.** Vì sao kiểm tra `ready` ngoài `synchronized` dễ lost notification?

**Đáp án:** Notifier có thể đặt `ready=true` và notify **sau check nhưng trước wait**; notification không được lưu và waiter có thể ngủ mãi.

**Câu 6.** Vì sao dùng `while` thay `if`?

**Đáp án:** Để kiểm tra lại predicate sau notification, spurious wakeup, timeout hoặc khi thread khác đã thay state trước lúc waiter giành lại monitor.

**Câu 7.** `Thread.sleep(100)` dưới monitor có giải phóng monitor không?

**Đáp án:** Không. `sleep` không release intrinsic monitors đã sở hữu.

**Câu 8.** `wait(0)` là nonblocking call hay indefinite wait?

**Đáp án:** Indefinite wait theo API, có thể kết thúc bởi notification, interrupt hoặc spurious wakeup.

**Câu 9.** `InterruptedException` từ `wait` xảy ra khi nào và lúc đó thread có giữ monitor không?

**Đáp án:** Sau quá trình thoát wait set do interruption hợp lệ và **giành lại monitor**; thread ở trong synchronized block khi catch chạy nếu catch nằm trong block.

**Câu 10.** `notify()` gọi khi wait set rỗng có gây lỗi không?

**Đáp án:** Không, nếu đã có monitor ownership. Nhưng notification không được tích lũy cho waiter tương lai.

**Câu 11.** Cơ chế visibility chính của guarded state dưới `synchronized(lock)` là gì?

**Đáp án:** Unlock-lock happens-before của **cùng monitor**, không phải notify tự thay thế đồng bộ dữ liệu.

**Câu 12.** Trong `OneSlotDemo`, vì sao sum chắc chắn là 15 trong lần chạy không bị interrupt?

**Đáp án:** Producer đặt đủ năm số 1..5, consumer lấy đủ năm số bằng protocol guarded buffer; tổng `1+2+3+4+5=15`.

**Câu 13.** `Condition.await()` có nhả intrinsic monitor của object `ReentrantLock` không?

**Đáp án:** Nó nhả **associated explicit Lock** theo contract Condition, không phải “intrinsic monitor của object ReentrantLock”; hai ownership models khác nhau.

**Câu 14.** Vì sao `notifyAll` có thể tốn CPU hơn `notify`?

**Đáp án:** Nhiều waiters thức dậy tranh cùng một monitor dù phần lớn predicates vẫn sai; phải trở lại wait.

**Câu 15.** Khi một thread còn giữ monitor A, rồi `wait()` trên B, rủi ro nào?

**Đáp án:** Nó nhả B nhưng **vẫn giữ A**; nếu notifier cần A để chuyển predicate/gửi tín hiệu B, có thể tạo deadlock.

**Câu 16.** Muốn nhiều producer/consumer dùng condition queues riêng, API nào có thể phù hợp?

**Đáp án:** `ReentrantLock` với hai `Condition` `notFull` và `notEmpty`; hoặc tốt hơn cho queue phổ thông, `BlockingQueue` đã xây sẵn protocol.

**Câu 17.** `getState() == WAITING` có chứng minh chính xác thread đang ở wait set của object cụ thể không?

**Đáp án:** Không. WAITING cũng xuất hiện từ `join`/`park`, và snapshot có race; cần thread dump/diagnostics cùng stack/monitor nếu muốn điều tra.

**Câu 18.** Tại sao interruption và notification gần nhau không thể áp quy tắc “interrupt luôn thắng”?

**Đáp án:** JLS cho phép những thứ tự sự kiện khác nhau và quy định kết quả tương ứng; notification/interruption interactions có semantics riêng bảo vệ trạng thái/notification.

## 23. Tổng kết: hình dung đúng wait set và monitor

```text
             MỖI OBJECT M CÓ:
   ┌────────────────────────────────┐
   │ intrinsic MONITOR             │
   │ current owner (hoặc không ai)  │
   │                                │
   │ WAIT SET của M                 │
   │ {T2, T3, ...}                  │
   └────────────────────────────────┘

T1 notifier:
  synchronized(m) {
      predicate = true;   // state
      m.notifyAll();      // lấy waiters ra khỏi wait set
      ...                 // T1 vẫn giữ monitor lúc này
  }                       // T1 unlock

T2 waiter:
  synchronized(m) {
      while (!predicate) {
          m.wait();       // vào wait set, nhả monitor M
      }                   // được đánh thức không có nghĩa chạy ngay
      doWork();           // chỉ sau khi giành lại M và predicate đúng
  }

Thread States (quan sát khái niệm):
  WAITING       chờ event/predicate sau wait()
  BLOCKED       chờ chiếm monitor M
  RUNNABLE      đủ điều kiện thực thi khi đã giành lại M

Correctness:
  shared predicate + SAME monitor + while + state-change notify
  + interruption/shutdown policy rõ ràng.
```

**Ghi nhớ:** `wait` **nhả toàn bộ ownership của monitor receiver, đưa current thread vào wait set, rồi bắt buộc giành lại monitor trước khi tiếp tục**. `notify` chọn một waiter tùy ý; `notifyAll` đánh thức tất cả waiters trên **đúng object**, nhưng **không giải phóng monitor ngay** và không đảm bảo fairness. Notification là tín hiệu nhất thời; **predicate được bảo vệ bằng synchronized mới là sự thật**. `while` cần thiết vì spurious wakeups, timeouts và cạnh tranh state. Với bài toán phức tạp, ưu tiên những abstractions trong `java.util.concurrent` có protocol rõ ràng hơn.

### Tài liệu tham khảo chính thức (Java SE 17)

- [JLS 17.1 — Synchronization](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.1): monitor ownership và synchronized.
- [JLS 17.2 — Wait Sets and Notification](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.2): wait set, notify, notifyAll, spurious wakeups.
- [JLS 17.2.1 — Wait](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.2.1): nhường rồi giành lại monitor và interrupt semantics.
- [JLS 17.2.2 — Notification](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.2.2): chọn một/tất cả waiters, không ưu tiên FIFO.
- [JLS 17.2.4 — Interactions of Waits, Notification, and Interruption](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.2.4).
- [JLS 17.4.4–17.4.5 — Synchronization Order/Happens-Before](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5).
- [Java SE 17 — `Object.wait()/notify()/notifyAll()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html).
- [Java SE 17 — `Thread.State`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.State.html).
- [Java SE 17 — `Condition`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Condition.html).
- [Java SE 17 — `BlockingQueue`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html).
- [Java SE 17 — `CountDownLatch`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html).
- [JDK 17 — `jcmd`, `Thread.print`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-threads-and-synchronization-006">Java Memory Model khác với sơ đồ heap, stack và bộ nhớ vật lý</a>

<details>
<summary>Xem chi tiết</summary>

# Java Memory Model khác với sơ đồ heap, stack và bộ nhớ vật lý

## 1. Trả lời ngắn: JMM là tập quy tắc về hành vi, không phải sơ đồ nơi dữ liệu được đặt

**Java Memory Model (JMM)** là mô hình ngữ nghĩa trong **Java Language Specification (JLS), Chương 17**, xác định **những kết quả quan sát được nào là hợp lệ** khi các threads cùng đọc, ghi và đồng bộ dữ liệu. Nó mô tả **hành vi đọc/ghi, thứ tự được phép quan sát, quan hệ happens-before, data races, volatile, monitors và final fields**.

Ngược lại, sơ đồ **JVM heap/stack** trong **Java Virtual Machine Specification (JVMS) §2.5** mô tả các **vùng dữ liệu runtime** của máy ảo: heap chia sẻ, mỗi thread có JVM stack, frames/local variables/operand stacks, method area, runtime constant pool, PC register và native method stacks.

Còn **bộ nhớ vật lý** là cách CPU, OS, máy ảo và HotSpot triển khai chương trình: registers, CPU caches, RAM, virtual memory, memory pages, allocation buffers, GC barriers, compiler optimizations...

Ba lớp giải thích **những câu hỏi khác nhau**:

| Lớp | Câu hỏi chính | Nguồn mô tả |
| --- | --- | --- |
| **JMM** | Thread B được phép nhìn giá trị nào từ thread A? Khi nào write được publish an toàn? Có data race không? | JLS `17.4–`17.5 |
| **JVM runtime data areas** | Heap, JVM stacks, frames, local-variable slots và method area có vai trò gì? | JVMS `2.5–`2.6 |
| **Implementation/hardware** | Bit nằm trong CPU register/cache/RAM ở thời điểm nào? JIT dùng instructions/barriers gì? Object bố trí thế nào? | HotSpot, OS và CPU architecture |

**Không được suy ra Java Memory Model chỉ bằng hình “biến local ở stack, object ở heap”.** Một thread có thể có biến local riêng **trỏ tới object chung**. Hơn nữa, even if một Java field nằm trong heap theo mô hình ngôn ngữ, JIT có thể giữ giá trị trong register, loại allocation bằng scalar replacement hoặc sắp xếp instructions lại theo cách vẫn tuân JMM.

### 1.1. Sơ đồ ba tầng

```text
JAVA PROGRAM                    JVM MODEL                    IMPLEMENTATION
--------------                  ----------------            ----------------------
Thread A, Thread B              Heap/shared fields          CPU registers
read/write shared state         Per-thread frames           L1/L2/L3 caches
synchronized, volatile          JVM stacks                  DRAM / virtual memory
final fields, Thread.start      Class metadata              HotSpot JIT, GC, barriers
        |                              |                            |
        v                              v                            v
        JMM: quan sát nào             JVMS: vùng dữ liệu            CPU/OS: hiện thực
        được phép?                    nào tồn tại?                 bằng cách nào?
        |                                                           |
        +--------------- RÀNG BUỘC NGỮ NGHĨA -----------------------+
```

Sơ đồ này **không đồng nhất** JMM với một khối RAM/caches có sẵn. Một implementation được tự do tối ưu miễn chương trình thực hiện **một execution hợp lệ theo JMM**.

## 2. Heap và stack trong JVMS: đúng ở cấp kiến trúc nhưng không đủ cho concurrency

Theo **JVMS §2.5.3**, JVM có **heap chia sẻ giữa tất cả JVM threads** để cấp phát class instances và arrays. Heap storage được quản lý bởi garbage collector; đặc tả không ép một phương pháp quản lý bộ nhớ cụ thể.

Theo **JVMS `2.5.2**, **mỗi JVM thread có JVM stack riêng**, được dùng để chứa frames cho method invocations. Theo `2.6, mỗi frame chứa **local variables array**, **operand stack** và thông tin liên quan.

```text
              THREAD A                      THREAD B
        +--------------------+        +--------------------+
        | JVM stack A        |        | JVM stack B        |
        | frame: local ref p |        | frame: local ref q |
        +---------+----------+        +----------+---------+
                  |                              |
                  +---------------+--------------+
                                  v
                        SHARED JVM HEAP
                        +-------------------+
                        | Payload object    |
                        | value = 42        |
                        +-------------------+
```

Biến `p` và `q` là **hai local variables khác nhau**, mỗi biến thuộc method frame riêng. Nhưng cả hai có thể chứa **reference tới cùng object** trên heap. Nếu hai threads cùng đọc/ghi `Payload.value`, có khả năng xảy ra **conflicting accesses** trên một shared field.

### 2.1. Stack riêng không làm object được trỏ tới trở nên riêng

Ví dụ khái niệm:

```java
class Box {
    int value;
}

Box shared = new Box();
Thread t1 = new Thread(() -> shared.value++);
Thread t2 = new Thread(() -> shared.value++);
```

Đây là **snippet**, chưa tạo/start/join đầy đủ. Dù tham số/local references trong hai threads không phải cùng local variable, cả hai có thể truy cập **field `value` của cùng một `Box`**. `value++` không phải phép increment atomic theo JMM; nếu không có synchronization phù hợp sẽ có data race và có thể mất cập nhật.

**Điều quyết định:** có đang truy cập **cùng shared variable** hay không, không phải reference nằm trong frame nào.

### 2.2. Local variables không phải shared variables của JMM

**JLS §17.4.1** nói rõ local variables, formal method parameters và exception handler parameters **không được chia sẻ trực tiếp giữa các threads** và không thuộc cơ chế shared-variable memory model.

Nhưng **giá trị của local variable có thể là reference tới shared object**. Chính **instance fields, static fields và array elements** mới thuộc các shared variables đang được JLS §17.4 nghiên cứu.

Do vậy câu “mọi biến local đều thread-safe” **quá rộng và sai**: local reference riêng có thể dẫn tới shared mutable state không an toàn.

### 2.3. JVM stack không nhất thiết khớp tuyệt đối với stack frame vật lý bạn tưởng tượng

JVMS mô tả các khái niệm runtime như frames/local-variable slots. Trên HotSpot thực tế, JIT có thể:

- Đặt một số values trong CPU registers.
- Inline methods khiến biên giới source frames không còn hiện hữu vật lý như suy đoán.
- Eliminate allocations hoặc scalar-replace object fields.
- Reuse/rearrange slots theo tối ưu.
- Lưu thêm metadata hỗ trợ safepoints, deoptimization và GC roots.

**Đừng gắn mọi local Java vào một địa chỉ stack vật lý cố định** hoặc coi mỗi Java object bắt buộc được materialize trong heap mọi thời điểm.

### 2.4. Heap chia sẻ không có nghĩa mọi heap object đều thực tế bị chia sẻ

Một Java object có thể chỉ được thread A truy cập, dù Java heap về mặt kiến trúc là vùng dùng chung. Compiler có thể chứng minh object không escape và tối ưu, nhưng **đây là kết luận từ luồng tham chiếu**, không phải do heap biến thành private memory.

## 3. Tại sao cần Java Memory Model?

Bởi một chương trình đa luồng muốn biết **thread B được phép nhìn kết quả nào** khi thread A ghi dữ liệu.

Ví dụ:

```java
class Message {
    int data;
    boolean ready;
}
```

Giả sử A làm:

```java
message.data = 42;
message.ready = true;
```

và B làm:

```java
if (message.ready) {
    System.out.println(message.data);
}
```

Các snippets trên **chưa có synchronization**. Dù A viết `data` trước `ready` theo source program order, nếu có data race thì **không thể kết luận B khi thấy `ready == true` chắc chắn đọc `data == 42`**. Compiler, runtime hoặc processor được phép thực hiện những transformations phù hợp với JMM mà trực giác đơn luồng không lường trước.

### 3.1. Điều JMM mô tả là các execution hợp lệ

Theo **JLS §17.4**, memory model xem một execution trace và kiểm tra read action có thể quan sát write action nào theo các quy tắc hợp lệ hay không.

Nó không cung cấp một sơ đồ như:

```text
volatile write == copy bytes immediately to RAM
volatile read  == fetch bytes directly from RAM
```

Hai đẳng thức trên **không phải định nghĩa của JMM**. Compiler/hardware có nhiều cách duy trì visibility/ordering mà không cần thực hiện các “RAM flushes” như mô tả giản lược.

### 3.2. Vấn đề nằm ở visibility, ordering và atomicity

- **Visibility:** một thread có thể được bảo đảm quan sát các writes của thread khác trong điều kiện nào.
- **Ordering:** những actions có thể được thấy theo quan hệ thứ tự ra sao.
- **Atomicity:** thao tác được coi như một bước nguyên tử với khả năng xen kẽ giữa các threads thế nào.

Ba thuộc tính này liên quan, nhưng **không tương đương**. `volatile` cung cấp những bảo đảm visibility/ordering cho biến thích hợp, **không làm `counter++` atomic**.

## 4. Các thành phần khái niệm chính của JMM

JMM sử dụng các khái niệm:

1. **Actions:** reads/writes và các synchronization actions của threads.
2. **Program order:** thứ tự actions của **một thread** theo thực thi ngôn ngữ.
3. **Synchronization order:** tổng thứ tự các synchronization actions trong một execution, nhất quán với program order cho mỗi thread.
4. **Synchronizes-with:** các cạnh đồng bộ được JLS định nghĩa, ví dụ volatile write → subsequent read của cùng volatile variable, unlock → subsequent lock cùng monitor.
5. **Happens-before (HB):** quan hệ thứ tự từng phần được xây từ program order, synchronizes-with và tính bắc cầu.
6. **Conflicting accesses:** hai accesses tới **cùng shared variable**, trong đó ít nhất một access là write.
7. **Data race:** hai conflicting accesses không được sắp thứ tự bằng happens-before.
8. **Well-formed execution/casuality rules:** quy tắc đảm bảo những values mà reads quan sát hợp lệ, giới hạn suy luận từ chương trình racy.

**Không phải mọi sự kiện trong JMM là một CPU instruction đơn lẻ.** Đó là **semantic actions**, implementation có thể hiện thực bằng nhiều instructions hoặc tối ưu không tạo instruction tương ứng nếu observable behavior vẫn hợp lệ.

### 4.1. Shared variables cụ thể gồm gì?

Theo §17.4.1:

- **Instance fields**.
- **Static fields**.
- **Array elements**.

Đừng loại array elements khỏi phân tích data races chỉ vì không có tên field như `count`. Hai threads cùng ghi `array[0]` có thể race; còn ghi vào **những array elements khác nhau** thì đó là những shared variables khác, dù vẫn có các vấn đề concurrency cấp cao cần xét.

### 4.2. Local primitive khác reference tới object

```java
int local = 7;
Box box = sharedBox;
local++;
box.value++;
```

Đây là **snippet**, `sharedBox` cần định nghĩa.

- `local`: local primitive trong frame, không được trực tiếp chia sẻ theo JMM.
- `box`: local reference riêng; giá trị reference có thể là địa chỉ/handle JVM hiện thực.
- `box.value`: field của object có thể shared; nếu được nhiều threads cập nhật không đồng bộ, có thể race.

Không được kết luận “`box` local nên `box.value` local”.

## 5. Program order không phải lời hứa về thứ tự physical instructions

Một thread theo source:

```java
a = 1;
b = 2;
```

có **program order** giữa hai actions theo JMM khi chúng xuất hiện trong execution tương ứng. Nhưng compiler và CPU có thể reorder hoặc loại bỏ operations khi điều đó không thay đổi behaviors được phép của chương trình.

Trong một chương trình đa luồng thiếu synchronization, một thread khác **không có quyền suy ra** từ source order đơn thuần rằng thấy `b == 2` đồng nghĩa phải thấy `a == 1`.

### 5.1. As-if-serial không đủ cho các shared-variable races

Trên một thread riêng lẻ, tối ưu thường phải bảo toàn intra-thread semantics. Nhưng các threads khác có thể quan sát những thứ tự tưởng như bất ngờ nếu không có HB edges.

JLS §17.4 có những ví dụ về **reordering** hợp lệ dù tưởng trái với source code.

### 5.2. CPU cache không là căn cứ để “chứng minh” một read hợp lệ

Các CPUs có cache coherence protocols, store buffers và memory-ordering rules khác nhau. Nhưng cách hiểu chính xác Java program phải dựa vào **JMM**, không phải phỏng đoán `L1 cache` của thread A đã flush hay chưa.

Hai CPUs cùng chạy một chương trình Java có thể có memory ordering hardware khác nhau; **JVM/JIT phải hiện thực synchronization semantics của Java trên cả hai**.

### 5.3. Wall-clock order khác happens-before

Một write “thật sự xảy ra sớm hơn” theo đồng hồ người quan sát **không tự tạo HB edge** với một read của thread khác. HB là **quan hệ trong execution theo JMM** được xây từ program order/synchronization rules.

Ngược lại, JMM cho phép implementation thực hiện một số reorderings vật lý nếu kết quả vẫn nhất quán với executions hợp lệ; **HB không có nghĩa mọi memory instruction phải hoàn tất vật lý theo cùng một lịch thời gian tuyệt đối**.

## 6. Happens-before: công cụ suy luận quan trọng nhất

Viết `hb(A, B)` khi action A **happens-before** action B.

JLS §17.4.5 xây HB từ:

- **Program order:** action trước trong cùng thread happens-before action sau.
- **Synchronizes-with:** action đồng bộ nguồn happens-before action đồng bộ đích.
- **Transitivity:** nếu `hb(A,B)` và `hb(B,C)` thì `hb(A,C)`.

Một số quy tắc quan trọng:

| Nguồn | Đích | Ý nghĩa |
| --- | --- | --- |
| Unlock monitor `M` | Subsequent lock monitor `M` theo synchronization order | Publish writes qua cùng monitor |
| Write volatile `v` | Subsequent read volatile `v` theo synchronization order | Publish các writes trước tới thread đọc flag/reference |
| `Thread.start()` | Hành động của thread được start | Công bố state đã chuẩn bị trước khi start |
| Actions trong thread `T` | Thread khác trở về thành công từ `T.join()` | Thread joiner nhìn thấy effects trước khi T kết thúc |
| Default initialization của object | Những actions về sau theo quy tắc JLS | Các fields có giá trị mặc định trước usage thích hợp |
| `Thread.interrupt()` | Thread khác phát hiện interruption theo quy tắc | Có cạnh synchronization tương ứng |

Những APIs trong `java.util.concurrent` còn định nghĩa **memory consistency effects** của riêng chúng. Hãy đọc tài liệu API để biết vì sao việc enqueue/dequeue, CountDownLatch, Future.get hoặc atomic operations tạo ra quan hệ quan sát phù hợp.

### 6.1. Ví dụ tuyến tính bắc cầu

```text
Thread A:                   Thread B:
  payload.value = 84          local = published  [volatile read]
  published = payload         print(local.value)
      [volatile write]

Program order: A.value write -> A.volatile write
Synchronizes-with: A.volatile write -> B.volatile read
Program order: B.volatile read -> B.value read

=> A.value write happens-before B.value read
   (khi B đọc reference đã được publish theo volatile)
```

Đây là **semantic proof**, không phải sơ đồ “copy 84 từ L1 sang DRAM”.

### 6.2. `happens-before` không đồng nghĩa mọi read sẽ thấy “giá trị mới nhất theo đồng hồ”

Một HB edge giúp ràng buộc các writes mà read có thể quan sát. Nhưng muốn suy read thấy **giá trị cụ thể**, phải xét **các writes khác tới cùng variable**, thứ tự liên quan và semantics đầy đủ của program.

Đừng nói “có HB là đọc ngay lập tức mọi giá trị mới nhất ngoài hệ thống” — JMM không cung cấp một global memory snapshot bằng timestamp vật lý.

## 7. `volatile`: publication và ordering, không phải “bắt buộc ghi thẳng RAM”

Một field khai báo:

```java
private static volatile boolean done;
```

là **volatile variable**. Theo JMM, **write volatile** và **read volatile** là **synchronization actions**. Một write vào `done` **synchronizes-with** những subsequent reads của cùng volatile variable theo synchronization order.

### 7.1. Ví dụ cờ dừng

```java
class Flag {
    volatile boolean done;
}

static void worker(Flag flag) {
    while (!flag.done) {
        Thread.onSpinWait();
    }
}
```

Đây là **đoạn mã minh họa**, chưa phải chương trình độc lập. `Thread.onSpinWait()` là **hint hiệu năng**, không thay thế `volatile` hay tạo một HB edge đặc biệt.

Nếu `done` là **non-volatile** và không có synchronization khác, JVM có thể tối ưu phép đọc, hoặc chương trình có thể mãi không quan sát thay đổi từ thread khác. Đừng dùng `Thread.sleep`/`onSpinWait` như giải pháp thay volatile.

### 7.2. Safe publication bằng volatile reference

```java
class Data {
    int value;
}

static volatile Data published;

static void producer() {
    Data data = new Data();
    data.value = 84;
    published = data;
}
```

Khi consumer quan sát **reference được publish** thông qua volatile read, những writes đi trước volatile write có HB path tới reads sau. Vì vậy consumer có thể dựa vào state đã được khởi tạo an toàn theo thứ tự đó.

### 7.3. Volatile reference không làm toàn object graph trở thành synchronized

Sau publication:

```java
Data snapshot = published;
snapshot.value++;
```

Nếu nhiều threads đồng thời tăng `value` mà không synchronization, **field `value` vẫn có thể race**. `volatile Data published` làm reference accesses volatile, **không biến mọi field của Data thành volatile/atomic**.

### 7.4. Volatile increment không atomic

```java
volatile int counter;

void increment() {
    counter++;
}
```

Đây là **snippet minh họa**; `counter++` gồm đọc, tính toán và ghi. Hai threads có thể đọc cùng giá trị rồi cùng ghi kết quả, gây **lost update**, dù access từng lần có volatile semantics.

Dùng `AtomicInteger.incrementAndGet()` hoặc `synchronized` khi cần atomic increment.

### 7.5. Volatile không nhất thiết luôn sinh full hardware fence

JIT có thể hiện thực volatile bằng instructions/ordering/barriers phù hợp từng CPU architecture. Không có cam kết rằng **mỗi volatile read gây một memory fence instruction cụ thể** hay **ép CPU cache flush toàn bộ lên main memory**.

**JMM cam kết hiệu ứng quan sát được**, không quy định instruction set implementation.

## 8. `synchronized`: vừa mutual exclusion vừa publication

```java
private static final Object LOCK = new Object();
private static int count;

static void increment() {
    synchronized (LOCK) {
        count++;
    }
}
```

Nếu mọi threads sửa `count` đều khóa **cùng monitor `LOCK`**, phép read-modify-write được bảo vệ bằng mutual exclusion.

### 8.1. Unlock → lock HB

Thread A:

```java
synchronized (LOCK) {
    count = 42;
}
```

Thread B:

```java
synchronized (LOCK) {
    System.out.println(count);
}
```

Khi B acquire cùng monitor sau A release, JMM có đồng bộ phù hợp để B nhìn thấy writes đi trước. Nhưng **không được chỉ nhìn hai snippets rồi tuyên bố output của B luôn 42** nếu B có thể chạy trước A; thứ tự acquiring monitor rất quan trọng.

### 8.2. Hai khóa khác nhau không tạo cùng quan hệ

```java
synchronized (new Object()) {
    count++;
}
```

Nếu mỗi thread tạo một khóa mới, họ **không dùng chung monitor**. Khối synchronized ấy không bảo vệ `count` khỏi các threads khác cùng làm tương tự. JIT còn có thể loại khóa nếu chứng minh monitor không escape.

**Monitor identity** mới quyết định việc đồng bộ, không phải hình dạng source code giống nhau.

### 8.3. Synchronized method khóa ai?

- `synchronized` instance method khóa **`this`**.
- `static synchronized` method khóa **`Class` object** đại diện class.
- `synchronized(lock)` block khóa **đúng object reference mà biểu thức trả về**.

Không được hiểu “khóa cả heap” hoặc “đóng băng RAM” khi chạy synchronized.

### 8.4. Synchronized và hardware instructions

HotSpot có thể triển khai các đường khóa không tranh chấp/tranh chấp bằng những cơ chế khác nhau theo phiên bản. JIT có thể tối ưu lock elision khi an toàn. JMM chỉ yêu cầu **hành vi monitor và memory ordering hợp lệ**; không quy định chính xác byte trong object header hoặc một instruction cố định.

## 9. Một ví dụ Java 17+ độc lập: volatile publication và synchronized counter

Tạo tệp **`MemoryModelDemo.java`**:

```java
public final class MemoryModelDemo {
    private static final class Payload {
        final int id;
        int value;

        Payload(int id, int value) {
            this.id = id;
            this.value = value;
        }
    }

    private static volatile Payload published;
    private static final Object LOCK = new Object();
    private static int count;

    private static void increment() {
        synchronized (LOCK) {
            count++;
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Thread producer = new Thread(() -> {
            Payload localReference = new Payload(42, 84);
            published = localReference;
        });

        Thread consumer = new Thread(() -> {
            Payload localReference;
            do {
                localReference = published;
            } while (localReference == null);

            System.out.println("published id=" + localReference.id);
            System.out.println("published value=" + localReference.value);
        });

        consumer.start();
        producer.start();
        producer.join();
        consumer.join();

        Thread first = new Thread(() -> {
            for (int i = 0; i < 20_000; i++) {
                increment();
            }
        });

        Thread second = new Thread(() -> {
            for (int i = 0; i < 20_000; i++) {
                increment();
            }
        });

        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("synchronized count=" + count);
        System.out.println("expected count=40000");
    }
}
```

### 9.1. Chạy chương trình

```bash
javac --release 17 MemoryModelDemo.java
java MemoryModelDemo
```

**Stdout chính xác khi chương trình hoàn tất bình thường:**

```text
published id=42
published value=84
synchronized count=40000
expected count=40000
```

Ví dụ đã được **biên dịch bằng `javac --release 17`** và chạy trên HotSpot ở ba chế độ: mặc định, `-Xint` và `-Xmixed`. Cả ba cho cùng kết quả logic này.

### 9.2. Vì sao `published id` và `published value` ổn định?

Producer tạo `Payload(42,84)` và **ghi volatile reference `published` sau khi constructor hoàn tất**.

Consumer đọc `published` **qua volatile read** cho tới khi nhận reference khác null. Từ volatile write đến subsequent volatile read có **synchronizes-with edge**; nhờ tính bắc cầu của HB, các fields được producer khởi tạo trước publication có thể được consumer đọc an toàn.

**Không phải vì object “nằm trên heap nên cả hai thread tự nhìn cùng state tức thời”**. Một field trên shared heap nhưng thiếu publication có thể không có HB guarantee tương ứng.

### 9.3. Vì sao dùng một biến local trong mỗi thread vẫn có shared object?

Producer có `localReference` trong frame riêng. Consumer cũng có một **biến local tên giống vậy** trong frame riêng. Chúng không phải cùng biến local. Producer đưa **reference tới Payload object** vào `published`, và consumer đọc reference đó.

Chính object được refer tới là shared; **JMM phân tích volatile field và object fields**, không một phép “chuyển biến local từ stack A sang stack B”.

### 9.4. Vì sao bộ đếm luôn 40.000?

Hai threads chạy 20.000 calls `increment()`, và mỗi call khóa **cùng `LOCK`**. Nhờ mutual exclusion, các increments không bị lost update. `join()` của main tạo quan hệ completion/happens-before cho main đọc `count` sau khi workers kết thúc.

`count` không cần khai báo volatile vì mọi updates được bảo vệ bằng monitor và main chỉ đọc sau successful joins. Nếu code mở rộng có thread thứ ba đọc count trong lúc workers chạy, phải xác định cơ chế synchronization phù hợp cho reader.

### 9.5. Vì sao spin-loop không phải mẫu production được khuyến nghị?

Consumer của demo chờ một volatile reference bằng vòng đọc liên tục. Nó giúp cô lập ví dụ về volatile, nhưng có thể **tốn CPU** và không có timeout/cancellation. Trong production, ưu tiên `CountDownLatch`, `BlockingQueue`, `Future`, `CompletableFuture` hoặc các APIs thích hợp để phối hợp thread lifecycle.

### 9.6. Vì sao `-Xint` và `-Xmixed` cho cùng logic?

JMM thuộc **ngữ nghĩa Java**; JIT có thể sắp xếp instructions/barriers tùy kiến trúc nhưng không được làm chương trình đồng bộ đúng quan sát kết quả không hợp lệ. Execution mode có thể làm khác timing, không được phá HB guarantees.

## 10. Data race: một thuộc tính của execution, không phải “CPU cache bị lỗi”

Một **data race** theo JLS §17.4.5 xảy ra khi có hai **conflicting accesses** đến cùng shared variable mà không được sắp thứ tự bằng happens-before. Ví dụ:

```java
static int total;

static void add() {
    total++;
}
```

Nếu hai threads gọi `add()` đồng thời mà không có synchronization, accesses có thể race. Mỗi phép `total++` gồm đọc, cộng, ghi, nên số cuối có thể **nhỏ hơn** số increments dự kiến.

### 10.1. Không thể chứng minh an toàn bằng “chạy thử 100 lần vẫn đúng”

Thread scheduling và optimizer decisions không bắt buộc tạo lost updates trong mọi lần chạy. Một chương trình có race vẫn là chương trình thiếu ràng buộc JMM, dù máy thử thường cho output mong muốn.

### 10.2. Data race trong Java không giống “undefined behavior” vô giới hạn của C/C++

Java Memory Model **vẫn đặt những ràng buộc** lên executions hợp lệ của chương trình racy (read-from legality và causality constraints). Không nên nói “data race khiến JVM được làm bất cứ điều gì, kể cả bịa giá trị tùy ý” theo kiểu đơn giản hóa từ một ngôn ngữ khác.

Tuy nhiên, **hành vi racy có thể rất bất ngờ** và không thích hợp để xây tính đúng của phần mềm.

### 10.3. “Data-race-free ⇒ sequential consistency” với điều kiện thích hợp

JLS §17.4 nêu rằng **chương trình được đồng bộ đúng**, tức mọi sequentially consistent execution của nó không có data races, sẽ có những behaviors xuất hiện **sequentially consistent (SC)**. Đây là **DRF–SC guarantee** của JMM.

Trực giác của sequential consistency: execution có thể được hiểu như một tổng thứ tự các actions nhất quán với program order của mỗi thread và reads quan sát write phù hợp trong thứ tự ấy.

**Cẩn trọng:** “DRF–SC” **không có nghĩa** mọi action thật sự được thực thi vật lý từng instruction một theo một global clock. Nó là **bảo đảm về behaviors được phép quan sát**.

## 11. Reordering litmus test: tại sao hai reads có thể cùng bằng 0?

Xét hai shared ints ban đầu bằng 0:

```java
int x = 0;
int y = 0;
```

Hai threads chạy các operations:

```text
Thread A               Thread B
--------               --------
x = 1;                 y = 1;
r1 = y;                r2 = x;
```

Nếu `x`/`y` là non-volatile fields chia sẻ và không có synchronization đặt thứ tự phù hợp, một số executions của chương trình có thể cho **`r1 == 0` và `r2 == 0`**, dù source code của từng thread ghi trước khi đọc.

### 11.1. Không suy “hai writes đã chạy trước hai reads” từ cấu trúc hai cột

Program order chỉ cho:

```text
A: write x -> read y
B: write y -> read x
```

Không có HB edges giữa threads để bắt buộc mỗi read nhìn thấy write của thread kia. Một execution hợp lệ có thể tương ứng reads quan sát giá trị khởi tạo. Đây là ví dụ loại *store buffering* thường dùng để thảo luận memory models.

### 11.2. Chuyển x và y sang volatile có thay đổi kết quả được phép không?

Với các volatile reads/writes thuộc synchronization order và semantics tương ứng, **`r1 == 0 && r2 == 0` bị loại khỏi những kết quả hợp lệ trong litmus test này**: để cả hai read thấy zero theo thứ tự toàn cục sẽ cần sắp hai writes/reads mâu thuẫn với program order và synchronization order.

**Không nên khái quát rằng volatile khiến mọi chương trình concurrent hoàn toàn tuần tự**. Đây là kết luận cho một mẫu thao tác cụ thể; compound actions, nhiều variables và các objects khác vẫn cần phân tích.

### 11.3. Vì sao không viết unit test “thử 1 triệu vòng phải thấy 0,0”?

Những outcomes hợp lệ không có nghĩa chúng phải được quan sát trong một số lần chạy hữu hạn. CPU, VM, JIT, OS scheduling có thể khiến kết quả đó hiếm hoặc không xuất hiện trong thử nghiệm. Để nghiên cứu concurrency litmus tests, dùng frameworks như **JCStress** thay vì tự viết vòng lặp rồi đưa ra khẳng định sai.

## 12. `final` fields: initialization safety không phải “mọi thứ bất biến”

JLS §17.5 định nghĩa **final field semantics** đặc biệt. Khi object được xây dựng **đúng cách**, không để `this` thoát khỏi constructor sớm, các final fields có bảo đảm khởi tạo đặc biệt giúp threads khác có thể nhìn thấy những giá trị final fields được thiết lập trong constructor dù cách publish không có synchronization thông thường.

Ví dụ:

```java
final class Configuration {
    final int port;
    final String name;

    Configuration(int port, String name) {
        this.port = port;
        this.name = name;
    }
}
```

Nếu constructor không leak `this`, final fields `port` và `name` hưởng các bảo đảm initialization safety theo JLS. Nhưng có các giới hạn quan trọng.

### 12.1. `final` không biến reference tới mutable object thành immutable deep graph

```java
final class Holder {
    final int[] values = new int[1];
}
```

`values` là final reference: reference không thể bị gán lại sau construction bình thường. Nhưng **`values[0]` vẫn mutable**.

Nếu nhiều threads cùng ghi/đọc `values[0]` mà thiếu synchronization, array element có thể có **data race**. Final field semantics không biến mọi mutation sau constructor thành safely synchronized.

### 12.2. Constructor escape phá những giả định an toàn

```java
final class BadPublication {
    final int value;

    BadPublication(java.util.List<BadPublication> registry) {
        registry.add(this); // publish this trước khi constructor hoàn tất
        value = 42;
    }
}
```

Đây là **anti-pattern minh họa**. Nếu một thread khác truy cập object qua registry trong khi constructor còn chạy, bảo đảm final field initialization safety có thể không áp dụng như trường hợp object được xây dựng đúng rồi mới publish.

### 12.3. `final` không thay publish protocol cho mọi trường hợp

Final fields có bảo đảm riêng, nhưng đối với state mutable thông thường, an toàn thread và visibility sau construction vẫn nên dựa vào **safe publication** hoặc cơ chế synchronization phù hợp.

## 13. Safe publication: làm sao đưa một object từ thread A sang thread B?

**Safe publication** nghĩa là có cơ sở JMM để thread B quan sát state đã được chuẩn bị bởi A, thay vì chỉ “tình cờ thấy reference khác null”.

Các cơ chế thường dùng:

- Ghi reference qua **volatile field**, đọc lại qua volatile read.
- Ghi/đọc state bên trong **`synchronized`** trên cùng monitor.
- Publish trước `Thread.start()` phù hợp với happens-before của start.
- Chờ hoàn thành bằng `Thread.join()` hoặc các cơ chế `Future.get()` có memory-consistency effects.
- Sử dụng `BlockingQueue`/concurrent collections/atomic APIs theo hợp đồng riêng của thư viện.
- Sử dụng class initialization an toàn khi áp dụng đúng.

### 13.1. Cẩn trọng với static field bình thường

```java
static Config config;

static void init() {
    config = new Config();
}
```

Đây là **snippet**, `Config` chưa định nghĩa. Nếu thread khác đọc `config` không synchronization, **không được tự khẳng định** mọi fields bình thường của Config đã được publish an toàn.

Nếu initialization diễn ra trong **class initialization** được JVM phối hợp theo JLS §12.4.2, tình hình khác: JVM cung cấp synchronization/happens-before phù hợp của class initialization. Nhưng một static field được **gán động về sau** không mặc nhiên được an toàn chỉ vì có từ khóa `static`.

### 13.2. Publication và mutation là hai giai đoạn riêng

Một object được publish an toàn tại thời điểm T chưa có nghĩa mọi writes sau T cũng được publish an toàn. Nếu thread A tiếp tục sửa normal fields sau publication còn thread B đọc chúng, cần synchronization tiếp theo.

### 13.3. Concurrent containers hỗ trợ HB theo hợp đồng API

Các `java.util.concurrent` containers như `BlockingQueue`, `ConcurrentHashMap` và các concurrency utilities có memory-consistency guarantees. Tùy API, hành động trước khi enqueue/put một object có thể happens-before những hành động sau khi thread khác thành công nhận truy cập object theo cách được quy định.

Đọc **API docs**, không suy bảo đảm chỉ từ tên “Concurrent”.

## 14. `Thread.start()` và `Thread.join()`: các HB edges không cần volatile field

### 14.1. Start

```java
class State {
    int value;
}

State state = new State();
state.value = 123;
Thread worker = new Thread(() -> System.out.println(state.value));
worker.start();
```

Đây là **snippet**, không phải chương trình độc lập. Những actions trước `worker.start()` trong thread gọi start happens-before actions trong thread mới. Vì vậy, nếu không có những concurrent writes khác tới `state.value`, thread worker có cơ sở JMM để đọc giá trị đã được chuẩn bị.

### 14.2. Join

```java
Thread worker = new Thread(() -> result[0] = 42);
worker.start();
worker.join();
System.out.println(result[0]);
```

Đây là snippet cần `result` và xử lý `InterruptedException`. Khi thread gọi join **trở về thành công và worker đã hoàn tất**, effects của worker được publish với thread joiner theo JMM.

Lưu ý: đọc array element **trong lúc worker đang ghi mà không có HB khác** vẫn là câu hỏi khác. `join()` không retroactively biến mọi race giữa các threads thành đúng.

### 14.3. Sleep không thay start/join/synchronization

```java
Thread.sleep(500);
System.out.println(sharedValue);
```

`Thread.sleep` không tạo ra HB edge tới write của thread khác chỉ vì đã trôi qua 500 ms. Một phương pháp polling dựa vào timing mà không có volatile/locks/concurrent APIs không được JMM bảo đảm.

## 15. Atomicity: loại biến và loại thao tác quan trọng hơn “nằm ở RAM”

```java
volatile int count;
count++;
```

Volatile read và write từng lần có atomicity/ordering phù hợp, nhưng **chuỗi đọc → cộng → ghi không atomic**.

`AtomicInteger` cung cấp các operations như:

```java
java.util.concurrent.atomic.AtomicInteger counter =
        new java.util.concurrent.atomic.AtomicInteger();
int updated = counter.incrementAndGet();
```

`AtomicInteger.incrementAndGet` đảm bảo atomic read-modify-write theo API, thích hợp cho counter độc lập.

### 15.1. `long` và `double` non-volatile theo JLS §17.7

JLS §17.7 lịch sử cho phép **non-volatile `long` và `double`** accesses được xem như hai thao tác 32-bit trong một số implementations; vì thế **không nên coi atomic 64-bit access non-volatile là bảo đảm ngôn ngữ tuyệt đối trên mọi JVM**.

Trong nhiều HotSpot/JDK hiện đại, thực tế xử lý 64-bit aligned values có thể atomic do implementation/architecture. Nhưng **đó không thay đổi tính chất của JLS guarantee**. Reads/writes volatile `long` và `double` là atomic theo đặc tả.

### 15.2. Không đồng nhất atomicity với visibility

Một phép đọc/ghi atomic một field không đủ để bảo đảm thread khác nhìn thấy cùng giá trị vào thời điểm mong muốn nếu thiếu ordering/visibility phù hợp.

### 15.3. Cùng dữ liệu, nhiều fields

Nếu một invariant gồm `balance` và `version` phải thay đổi atomically cùng nhau, chỉ dùng hai volatile fields không tự biến **cặp cập nhật** thành một transaction atomic. Cần cùng lock, immutable snapshot được publish, hoặc cơ chế thiết kế phù hợp.

## 16. JMM không cho phép suy đoán CPU cache line theo thread

Một số bài viết giản lược:

```text
Thread A -> CPU core A -> L1 cache A
Thread B -> CPU core B -> L1 cache B
volatile -> flush RAM
```

Đây là **sơ đồ học phần cứng**, không là định nghĩa JMM. Nó bỏ qua:

- Hai Java threads có thể **chạy luân phiên trên cùng core**, hoặc di chuyển core.
- CPUs có nhiều mức cache, coherence protocols, store buffers và các quy tắc memory ordering riêng.
- OS có thể preempt threads; JVM có thể execute compiled/interpreted code khác nhau.
- Object có thể nằm trong heap, registers hoặc đã được scalar replace trong optimized path.
- Volatile/barriers có thể được implement khác giữa x86-64, AArch64 và platforms khác.

**Cách giải đúng:** dựa vào JMM để xác định legal outcomes; nếu tối ưu hiệu năng ở cấp CPU, phân tích **implementation** sau khi correctness đã đúng.

### 16.1. “Main memory” trong JMM không đồng nghĩa mỗi thao tác vào DRAM thật

Những mô hình giáo khoa có thể nói “working memory của thread” và “main memory” để xây trực giác. Nhưng JLS §17.4 hiện hành định nghĩa JMM thông qua **actions/executions/happens-before**, không hứa mỗi biến có một “bản chính” đặt cố định tại DRAM rồi volatile buộc flush toàn bộ.

### 16.2. Cache coherence không thay thế happens-before

Ngay cả khi CPU cache coherence đảm bảo một số properties cho single memory locations, **ordering across multiple locations** và compiler transformations vẫn là vấn đề. Một chương trình Java có data race không trở thành hợp lệ chỉ vì chạy trên “CPU có cache nhất quán”.

## 17. False sharing: liên quan hiệu năng phần cứng, không cùng khái niệm với data race

**False sharing** xảy ra khi những threads sửa các variables/fields khác nhau nhưng chúng nằm gần nhau trên cùng cache line, gây cache-line traffic không mong muốn.

Ví dụ ý tưởng:

```text
Cache line:
+-------------------+-------------------+
| counter của A     | counter của B     |
+-------------------+-------------------+
       A ghi                B ghi
   (hai biến khác nhau, có thể không data race)
       |
       v
  Cache coherence traffic ảnh hưởng throughput
```

Có thể có **false sharing mà không có data race** vì hai threads sửa **các variables khác nhau**. Ngược lại, data race có thể tồn tại dù hai fields nằm ở hai cache lines khác nhau.

- **JMM correctness:** có conflicting accesses thiếu HB hay không?
- **Hardware performance:** các memory locations cùng cache line gây tranh chấp coherence không?

**Đừng giải một vấn đề correctness bằng cách thêm padding cache line**. Padding có thể giúp throughput trong một số tình huống, không tạo HB edges.

## 18. Reordering và compiler optimizations trên HotSpot

Một JIT compiler có thể:

- Hoist loads ra khỏi loop nếu không có synchronization buộc phải thấy updates từ threads khác.
- Inline methods và scalar-replace objects.
- Remove redundant writes/reads nếu observable behavior cho phép.
- Coalesce/canonicalize computations.
- Chèn barriers để hiện thực volatile/locks/atomic APIs theo platform.
- Deoptimize/recompile khi một giả định tối ưu không còn phù hợp.

Ví dụ:

```java
boolean done;

void waitUntilDone() {
    while (!done) {
        // không volatile, không synchronization
    }
}
```

Nếu thread khác ghi `done = true` thiếu synchronization, loop này **không có guarantee sẽ kết thúc**. Không nên kết luận “chắc sau vài milliseconds cache coherence tự làm nó thấy true”.

### 18.1. JIT optimization không được làm chương trình đồng bộ đúng thành sai

Nếu dùng volatile, monitor hay concurrent API đúng cách, implementation phải giữ những ordering/visibility constraints mà JMM quy định. C2 có thể loại một lock chỉ khi chứng minh việc loại đó không đổi behavior hợp lệ — không được “bỏ khóa vì thấy tốn CPU”.

### 18.2. Interpreter-only vẫn tuân JMM

Chạy:

```bash
java -Xint MemoryModelDemo
java -Xmixed MemoryModelDemo
```

Hai execution modes phải duy trì JMM semantics. Chạy `-Xint` **không phải cách chữa data race**: chương trình racy vẫn thiếu bảo đảm, dù interpreter vô tình ít reorder hơn optimizing compiler.

### 18.3. Bytecode không cố định CPU memory barriers

Trong class file, `synchronized` block có monitor bytecodes, volatile access có field access instructions và metadata flags, nhưng JIT/hardware lowering có thể rất khác theo architecture.

`javap -c` cho biết bytecode operations, **không phải một bản dump của memory instructions thực tế** sau C2 optimization.

## 19. GC reachability và JMM là hai vấn đề riêng

GC giải bài toán: **object nào còn reachable và khi nào heap storage có thể được thu hồi?** JMM giải bài toán: **read/write observations giữa threads nào là hợp lệ?**

Ví dụ:

```java
static Object shared;

static void publish() {
    shared = new Object();
}
```

Đây là snippet minh họa. Việc object được gán cho static field có thể giữ nó reachable; nhưng nếu thread khác đọc `shared` thiếu synchronization, **không có nghĩa việc publish state đã tuân JMM** chỉ vì object “sống trên heap”.

### 19.1. Object reachable không phải safely published

Một static list giữ reference không tự chứng minh reads/writes vào các mutable fields của object là thread-safe. Reachability và happens-before cần phân tích riêng.

### 19.2. GC không phải cơ chế visibility của business state

Một GC pause/safepoint có thể ảnh hưởng thực thi của VM, nhưng **không thay thế** synchronized/volatile/concurrent APIs theo JMM. Đừng thiết kế concurrency trên giả định “GC sẽ đồng bộ mọi cache nên state sẽ đúng”.

### 19.3. Cleaner và `Reference.reachabilityFence` không thay locks

Các APIs liên quan reachability hỗ trợ lifecycle của objects/native resources, không tự cung cấp atomicity cho shared mutable counters hoặc transaction state.

## 20. Những sai lầm kinh điển

| Sai lầm | Cách hiểu đúng |
| --- | --- |
| “JMM là hình heap ở giữa, mỗi thread một stack.” | Hình đó mô tả runtime data areas; JMM định nghĩa legal inter-thread observations. |
| “Biến local luôn thread-safe nên mọi object local đều safe.” | Local reference có thể trỏ tới shared mutable object. |
| “Field nằm ở heap nên mọi thread nhìn thấy giá trị mới ngay.” | Heap shared không tự tạo happens-before. |
| “Volatile luôn flush hết CPU caches ra RAM.” | JMM chỉ quy định synchronization/visibility/ordering semantics. |
| “Volatile counter++ là atomic.” | Volatile không tự làm compound read-modify-write atomic. |
| “Thread.sleep đảm bảo thấy write của thread khác.” | Sleep không tạo HB edge với write thông thường. |
| “Dùng synchronized với hai object khác nhau vẫn bảo vệ cùng static counter.” | Cần đồng bộ theo cùng monitor hoặc API thích hợp. |
| “Nếu code chạy đúng 100 lần thì không có data race.” | Một race có thể rất hiếm hoặc không lộ trong thử nghiệm. |
| “Mọi chương trình Java luôn sequentially consistent.” | DRF-SC đòi hỏi đồng bộ đúng; chương trình racy có behaviors khác. |
| “Data race Java cho phép bất kỳ giá trị tùy ý.” | JMM vẫn có well-formed execution/causality constraints. |
| “Final reference làm cả mutable graph bất biến.” | Final field không ngăn mutations của referenced object. |
| “Thấy reference khác null là object đã safely published.” | Cần HB path hoặc dựa đúng final-field semantics. |
| “Mỗi volatile read bắt buộc một full CPU fence instruction.” | Machine implementation phụ thuộc architecture/optimizer. |
| “CPU cache coherence tự thay JMM.” | Coherence không thay synchronization semantics của Java. |
| “False sharing là data race.” | Cache-line contention có thể xảy ra trên những variables khác nhau mà không có data race. |
| “Heap object luôn materialized ở DRAM.” | JIT có thể scalar-replace hoặc giữ data trong registers khi hợp lệ. |
| “-Xint cho phép bỏ volatile vì interpreter ít tối ưu.” | JMM không đổi theo interpreter/JIT mode. |
| “Class static field luôn safely published dù gán động ở bất kỳ thread nào.” | Class initialization có rules riêng; gán động thiếu sync vẫn có thể race. |
| “Thread.join chỉ đợi, không có memory effect.” | Join thành công có HB semantics với actions của thread đã kết thúc. |
| “GC là memory barrier cho business threads.” | GC scheduling không thay explicit concurrency guarantees. |
| “long/double thường atomic trên x64 nên Java Specification bảo đảm mọi nonvolatile 64-bit.” | JLS §17.7 cho phép non-atomic treatment với non-volatile long/double. |

## 21. Các tình huống thiết kế áp dụng JMM

### Tình huống A — Một request xây DTO rồi đưa sang worker thread

Nếu toàn bộ DTO được tạo trước `thread.start()` và không có concurrent mutations sai quy tắc, start HB có thể publish state cho worker. Nếu dùng executor pool, cần dựa vào **memory consistency effects của executor/queue APIs**, không giả định worker thread được tạo mới cho mỗi request.

### Tình huống B — Service khởi tạo singleton một lần

Có thể dùng **class initialization idiom** hoặc safe publication qua volatile/locks. Không nên viết lazy initialization với normal shared reference rồi giả định “chỉ có hai threads nên chắc ổn”.

### Tình huống C — Shared counter

Nếu cần atomic increment, dùng `AtomicInteger` hoặc monitor. Nếu throughput contention cao và chỉ cần tổng thống kê có semantics phù hợp, `LongAdder` đáng cân nhắc; nhưng phải đọc consistency behavior của `sum()` dưới concurrent updates.

### Tình huống D — Producer đưa message tới consumer

Sử dụng `BlockingQueue` để có ordering/coordination và memory effects theo API thay vì tự viết một non-volatile field + sleep-loop.

### Tình huống E — Immutable value object

Dùng final fields và constructor không leak `this`, tránh exposing mutable internal arrays trực tiếp. Nếu object chứa mutable fields, vẫn cần safe publication và mutation discipline theo nhu cầu.

### Tình huống F — Debug trên một CPU khác

Một code “đúng trên x86” nhưng thiếu HB có thể lộ lỗi trên architecture khác hoặc HotSpot/JDK khác. Sửa correctness bằng đúng memory-model rules, **không bằng thêm sleeps hay phụ thuộc cache layout**.

## 22. Công cụ và cách xác minh concurrency

### 22.1. `javap`

```bash
javap -c -p MemoryModelDemo
```

Dùng để đọc bytecode và cấu trúc methods, nhưng nó **không nói chính xác CPU instructions/barriers** mà HotSpot sử dụng sau JIT.

### 22.2. Java Flight Recorder và profiler

JFR có các sự kiện về thread activity, monitor contention và một số runtime events theo configuration. Nó giúp trả lời “threads có đang chờ monitor nhiều không?” nhưng không chứng minh một chương trình không có data race.

### 22.3. Thread dumps

Thread dumps cho thấy trạng thái tại một thời điểm như RUNNABLE/BLOCKED/WAITING, stacks/locks tùy tool. **Một thread dump không phải bằng chứng an toàn JMM**: data races là thuộc tính của accesses/orderings, không phải chỉ số threads đang blocked.

### 22.4. JCStress

**JCStress** là bộ công cụ OpenJDK để thử nghiệm các outcomes của concurrency litmus tests dưới nhiều interleavings/JIT/hardware conditions.

- Dùng JCStress để nghiên cứu những kết quả JMM cho phép hoặc cấm.
- Một outcome hợp lệ có thể không xuất hiện sau nhiều runs.
- JCStress không thay thế **logic proof** dựa trên JLS §17.4.
- Tránh biến một kết quả “chưa quan sát được” thành guarantee.

### 22.5. JMH

**JMH** tập trung vào microbenchmark performance, phù hợp đo overhead của volatile/locks/atomics trong các tình huống được kiểm soát. **JCStress là correctness/stress**, **JMH là performance benchmark**: hai công cụ trả lời hai câu hỏi khác nhau.

### 22.6. Static analyzers

IDE/static analysis tools có thể tìm những dấu hiệu concurrency bug: non-volatile flags, unsafe publication, lock misuse. Nhưng việc absence of warnings **không chứng minh toàn chương trình data-race-free**.

## 23. Bài tập tự kiểm tra kèm đáp án

**Câu 1.** JMM và JVM heap là một khái niệm không?

**Đáp án:** Không. JMM định nghĩa allowed observations giữa threads; JVM heap là runtime data area dùng cho class instances/arrays theo JVMS.

**Câu 2.** Biến local `Box b` ở thread A có thể tham chiếu tới cùng Box với local `Box c` ở thread B không?

**Đáp án:** Có. Local variables riêng nhưng references có thể trỏ tới một shared object.

**Câu 3.** Tại sao `ready = true` sau `data = 42` không đủ publish nếu cả hai là normal fields?

**Đáp án:** Không có HB edge giữa writes của producer và reads của consumer; JMM có thể cho phép quan sát thiếu ordering/visibility mong muốn.

**Câu 4.** Volatile write → volatile read cùng variable tạo quan hệ gì?

**Đáp án:** Có synchronizes-with edge khi read là subsequent theo synchronization order, do đó đóng góp vào happens-before.

**Câu 5.** `volatile int counter; counter++;` có atomic không?

**Đáp án:** Không. `++` là compound read-modify-write, có thể lost updates khi nhiều threads.

**Câu 6.** `Thread.start()` có HB effect không?

**Đáp án:** Có. Những actions xảy ra trước start trong thread gọi start happens-before actions của thread được start theo quy tắc JLS.

**Câu 7.** `Thread.join()` thành công có effect nào?

**Đáp án:** Actions của thread đã kết thúc happens-before actions tiếp theo của thread quan sát termination/join thành công.

**Câu 8.** Cùng source synchronized nhưng dùng hai lock objects khác nhau có bảo đảm atomic shared counter không?

**Đáp án:** Không. Mutual exclusion yêu cầu các threads phối hợp qua cùng monitor hoặc cơ chế đồng bộ tương đương.

**Câu 9.** Thread.sleep có tạo HB với việc thread khác ghi normal field không?

**Đáp án:** Không chỉ vì ngủ một khoảng thời gian.

**Câu 10.** Khác biệt false sharing và data race?

**Đáp án:** False sharing là cache-line contention ảnh hưởng performance; data race là conflicting shared-variable accesses không được HB order.

**Câu 11.** Tại sao `final int[] values` không biến array immutable?

**Đáp án:** `final` áp dụng reference field; array elements vẫn mutable.

**Câu 12.** Việc publish `this` từ constructor nguy hiểm ở đâu?

**Đáp án:** Threads khác có thể quan sát object trước khi initialization hoàn tất; final-field initialization safety có thể không áp dụng như construction đúng.

**Câu 13.** Một object vẫn reachable từ static field có nghĩa mọi normal fields được safely published không?

**Đáp án:** Không. Reachability khác với HB/safe publication.

**Câu 14.** `-Xint` có thay JMM semantics không?

**Đáp án:** Không. Interpreter/JIT đều phải tuân legal Java executions.

**Câu 15.** Volatile có bắt buộc write-thẳng-DRAM trên mọi platform không?

**Đáp án:** Không. JMM quy định ordering/visibility semantics, JVM chọn implementation thích hợp.

**Câu 16.** Long/double non-volatile reads/writes có 64-bit atomicity portable theo JLS không?

**Đáp án:** Không thể cam kết cho mọi VM. JLS §17.7 cho phép non-atomic treatment; volatile long/double có atomicity guarantee.

**Câu 17.** Nếu code không có data races theo điều kiện JMM, nó được bảo đảm gì về behaviors?

**Đáp án:** Properly synchronized/data-race-free programs có **sequential consistency guarantee** về observable behaviors theo JMM.

**Câu 18.** Chỉ thấy giá trị đúng 1.000 lần có chứng minh code data-race-free không?

**Đáp án:** Không. Outcomes phụ thuộc scheduling, JIT và hardware; cần phân tích HB và synchronization.

**Câu 19.** Hai threads đọc/ghi `array[0]` khác với mỗi thread chỉ ghi `array[0]` và `array[1]` ở đâu?

**Đáp án:** Truy cập cùng element có thể conflicting; elements khác nhau là những variables khác, dù còn phải xét data sharing ở cấp thuật toán.

**Câu 20.** JCStress và JMH khác nhau thế nào?

**Đáp án:** JCStress khảo sát concurrency correctness/allowed outcomes; JMH đo performance theo phương pháp microbenchmark.

**Câu 21.** “Happens-before” có nghĩa action A chắc chạy trước B theo đồng hồ vật lý trong mọi optimization không?

**Đáp án:** Không. Nó là quan hệ semantics/ràng buộc outcomes; implementation có thể reorder vật lý nếu không gây execution bất hợp lệ.

**Câu 22.** Vì sao synchronized count trong demo là 40.000 mà không cần count volatile?

**Đáp án:** Hai worker threads cùng dùng monitor cho increments; main chỉ đọc sau successful joins tạo HB phù hợp.

## 24. Checklist khi phân tích lỗi đa luồng thực tế

1. Xác định **shared variables**: instance fields, static fields, array elements.
2. Với từng variable, liệt kê threads có thể read/write nó.
3. Tìm **conflicting accesses**, xem có ít nhất một write không.
4. Vẽ **happens-before graph** dựa trên start/join, volatile, monitor và concurrent APIs.
5. Không dùng “local variable ở stack” làm chứng minh data không shared.
6. Phân biệt **ownership** của object với vị trí vật lý object.
7. Nếu nhiều threads cùng increment, kiểm tra atomicity của **compound operations**.
8. Nếu publish object, kiểm tra constructor có leak `this` không, final field semantics và publication path.
9. Với immutable objects, kiểm tra internal mutable arrays/lists có lộ reference hay không.
10. Với `synchronized`, đảm bảo các threads khóa **cùng object monitor**.
11. Với volatile, nhớ chỉ volatile variable có ordering semantics; post-publication mutations cần xử lý riêng.
12. Không dùng sleep/spin timing làm cách tạo HB.
13. Đừng đồng nhất GC safepoint hoặc heap scan với business synchronization.
14. Không kết luận lỗi do CPU cache trước khi xác định legal JMM behaviors.
15. Chạy concurrency litmus tests bằng JCStress khi cần, nhưng vẫn phân tích đúng JLS.
16. Dùng JMH nếu đang hỏi về performance của volatile/locks, không dùng nó chứng minh không có races.
17. Khi nâng JDK/đổi architecture, kiểm thử lại nhưng giữ logic đúng **không phụ thuộc compiler timing**.
18. Đọc nguồn chuẩn **JLS §17.4/§17.5** và **JVMS §2.5** thay vì chỉ dựa vào sơ đồ heap/stack.

### Kết luận

```text
SƠ ĐỒ JVM:                 JAVA MEMORY MODEL:
  Thread A stack             Actions: reads/writes/sync
  Thread B stack             Program order
  Shared heap                Synchronization order
  Frames/locals              Synchronizes-with
                             Happens-before
          |                        |
          +-----------+------------+
                      |
          Correct Java program behavior
                      |
          Runtime hiện thực trên CPU:
          registers, caches, virtual memory,
          HotSpot JIT, barriers, GC...

Heap shared KHÔNG tự bảo đảm safe publication.
Stack riêng KHÔNG làm object được trỏ tới private.
Volatile/synchronized KHÔNG được định nghĩa bằng “flush RAM”.
```

**Thông điệp cần nhớ:** **Java Memory Model không phải bản đồ heap–stack hay mô hình CPU cache.** JMM quy định **threads được phép quan sát những reads/writes nào**, còn JVM runtime areas mô tả nơi lưu dữ liệu ở cấp trừu tượng, và phần cứng/JIT quyết định cách thực thi. Khi đánh giá correctness của code đa luồng, hãy **chứng minh bằng happens-before, atomicity và safe publication**, không suy luận từ nơi đặt biến hoặc thời điểm “flush cache”.

### Tài liệu chuẩn và chuyên sâu

- [JLS 17.4 — Memory Model, Java SE 17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4): actions, shared variables, program order, synchronization order, happens-before và data races.
- [JLS 17.5 — final Field Semantics, Java SE 17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.5): initialization safety, freeze action và không publish `this` trước khi constructor hoàn tất.
- [JLS 17.7 — Non-Atomic Treatment of double and long, Java SE 17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.7): non-volatile 64-bit access và volatile atomicity.
- [JVMS 2.5 — Run-Time Data Areas, Java SE 17](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5): PC, JVM stacks, heap, method area và native method stacks.
- [JVMS 2.6 — Frames, Java SE 17](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6): local variables, operand stack, frames.
- [JLS 12.4.2 — Detailed Initialization Procedure, Java SE 17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2): class initialization synchronization.
- [java.util.concurrent package specification, Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html): memory consistency effects của concurrent utilities.
- [AtomicInteger API, Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html): atomic operations.
- [OpenJDK JCStress](https://openjdk.org/projects/code-tools/jcstress/): kiểm thử các allowed outcomes của concurrency litmus tests.
- [OpenJDK JMH](https://openjdk.org/projects/code-tools/jmh/): microbenchmark performance trong Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-threads-and-synchronization-007">Tính nguyên tử, khả năng nhìn thấy và thứ tự thao tác giữa các luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Tính nguyên tử, khả năng nhìn thấy và thứ tự thao tác giữa các luồng

## 1. Cùng truy cập bộ nhớ không có nghĩa cùng nhìn thấy một thứ

Trong Java đa luồng, nhiều threads có thể truy cập cùng một object/field trên heap, nhưng điều đó **không tự động** bảo đảm chương trình đúng. Một thread có thể cập nhật một field; thread khác có thể thấy giá trị cũ, hoặc các bước tạo thành một phép cập nhật có thể bị xen kẽ với bước của thread khác.

Ba nhóm tính chất cần phân biệt:

- **Atomicity (tính nguyên tử):** một **thao tác hoặc tổ hợp thao tác được đặc tả** diễn ra như một đơn vị không thể bị những thao tác cạnh tranh quan sát ở trạng thái trung gian theo atomicity contract.
- **Visibility (khả năng nhìn thấy):** các ghi từ thread A trở nên quan sát được trong thread B theo quy tắc **Java Memory Model (JMM)** khi có quan hệ đồng bộ thích hợp.
- **Ordering (thứ tự):** compiler/CPU/runtime có thể sắp xếp/tối ưu lệnh miễn hành vi hợp lệ theo JMM được bảo toàn; những quan hệ như **happens-before** đặt ràng buộc cho thứ tự quan sát liên luồng.

**Tóm tắt quan trọng:** một thao tác **atomic** chưa chắc cung cấp thứ tự/visibility cần thiết cho **những thao tác khác**; một biến `volatile` có visibility/ordering guarantees nhưng `count++` vẫn **không atomic**; một `synchronized` block có thể đồng thời bảo vệ atomicity của critical section và cung cấp happens-before giữa các lần lock/unlock **cùng monitor**.

### Ví dụ tối giản

~~~java
class Counter {
    int count;

    void increment() {
        count++;
    }
}
~~~

Đây là class minh họa, chưa có `main`. Nếu hai threads cùng gọi `increment()` trên **cùng instance** mà không đồng bộ, bạn không được bảo đảm kết quả bằng tổng số lần gọi. Câu lệnh `count++` gồm đọc giá trị, cộng 1, ghi lại — không phải một thao tác read-modify-write atomic theo JMM.

Ba vấn đề có thể song hành:

1. Hai threads có thể cùng đọc 5, cùng tính 6, cùng ghi 6: **lost update** (thiếu atomicity).
2. Một thread đọc giá trị không được đồng bộ từ thread kia: **visibility/data race**.
3. Compiler/CPU có thể cho phép các thao tác không được đồng bộ có thứ tự quan sát khiến code tưởng hợp lý theo source nhưng sai trong đa luồng: **ordering**.

Bài này giải thích chính xác ba thuộc tính, quan hệ giữa chúng và cách viết Java 17+ an toàn.

## 2. Mô hình Java Memory Model (JMM)

JMM được định nghĩa chủ yếu tại **Java Language Specification, Chapter 17 — Threads and Locks, §17.4**. Nó nói **những hành vi đọc/ghi nào hợp lệ** của một chương trình nhiều threads, không hứa mỗi Java source line tương ứng một machine instruction hay một lần “đồng bộ cache” vật lý.

Các thuật ngữ cốt lõi:

| Thuật ngữ | Diễn giải |
| --- | --- |
| **Action** | Read/write variables, lock/unlock, volatile accesses, thread-start/join-related synchronization actions… |
| **Program order** | Thứ tự các actions trong một thread theo ngữ nghĩa của thread ấy |
| **Synchronization order** | Một thứ tự toàn phần của synchronization actions trong một execution phù hợp JMM |
| **Synchronizes-with** | Quan hệ giữa những actions đồng bộ, ví dụ unlock monitor với lock sau trên cùng monitor |
| **Happens-before (HB)** | Bao đóng bắc cầu của **program order** và **synchronizes-with** |
| **Data race** | Hai conflicting accesses tới cùng variable từ các threads khác nhau, ít nhất một ghi, không có happens-before sắp thứ tự chúng |
| **Correctly synchronized** | Chương trình không có data races trong những sequentially consistent executions theo JLS; JMM bảo đảm các execution có vẻ sequentially consistent |

**Happens-before không chỉ có nghĩa A “xảy ra trước theo đồng hồ”.** Đây là **quan hệ quy tắc** khiến kết quả ghi của A được ràng buộc về visibility/ordering ở B; hai threads chạy trên cùng một CPU hoặc nhìn timestamps theo thời gian thực chưa đủ chứng minh HB.

### Cách suy luận bằng đồ thị HB

~~~text
Thread A                           Thread B
---------                          ---------
data = 42
    |
    v (program order)
volatile ready = true
    |
    | synchronizes-with (khi reader quan sát write phù hợp)
    +---------------------------------------+
                                            |
                                    if (ready)  // volatile read
                                            |
                                            v (program order)
                                     print(data)
~~~

Nếu `ready` là `volatile`, writer ghi `data=42` trước volatile write, và reader đọc `ready=true` theo synchronization order phù hợp, quy tắc HB bắc cầu buộc reader thấy `data=42` (giả sử không có cạnh tranh ghi `data` khác).

**Không suy** rằng mọi đọc `data` từ bất kỳ thread nào sẽ thấy giá trị mới **chỉ vì có volatile field khác trong cùng class**. Reader phải đi qua đúng synchronization edge hoặc một edge tương đương.

## 3. Atomicity: một read/write khác một read-modify-write

### 3.1. Những thao tác đơn lẻ

JLS quy định các read/write của **references** và primitive variables có kích thước **không quá 32 bit** là atomic ở cấp truy cập biến phù hợp. Với `long`/`double` **không volatile**, JLS `17.7` cho phép triển khai một read/write thành hai thao tác 32-bit theo quy tắc, nên không được dựa vào atomicity portable của plain 64-bit access trong trường hợp này. **Volatile `long`/`double` reads/writes là atomic.**

Điều này **không** biến chuỗi thao tác thành atomic. Một phép assignment atomic của `int` không có nghĩa hai lệnh `x = x + 1` trên hai threads được kết hợp đúng.

| Truy cập | Atomic theo semantics portable? | Đồng bộ liên luồng khác? |
| --- | --- | --- |
| Plain read/write `int` | Có, ở cấp read/write đơn lẻ | Không |
| Plain read/write reference | Có, ở cấp read/write reference đơn lẻ | Không |
| Plain read/write `long`/`double` | Không được bảo đảm 64-bit nguyên khối bởi JLS | Không |
| `volatile long` read/write | Có | Có volatile ordering/visibility |
| `volatile int` read/write | Có | Có volatile ordering/visibility |
| `volatile int++` | **Không**, vì nhiều bước | Có volatile accesses riêng lẻ nhưng không bảo vệ toàn bộ RMW |
| `AtomicInteger.incrementAndGet()` | **Có**, update atomic theo API | Có memory effects của atomic operation |
| `synchronized(lock) { count++; }` | Critical section được bảo vệ với cùng lock | Có monitor HB khi release/acquire đúng |

### 3.2. Lost update

~~~text
Ban đầu count = 5

Thread A                         Thread B
--------                         --------
read count -> 5
                                 read count -> 5
calculate 6
                                 calculate 6
write count = 6
                                 write count = 6

Kết quả: 6, dù đã có hai lần increment -> kỳ vọng logic: 7
~~~

Một **interleaving như trên** đủ giải thích vì sao hai reads cộng hai writes không phải nguyên tử. Với plain unsynchronized field, thực tế JMM còn cho phép thêm những hành vi visibility/ordering phức tạp; sơ đồ chỉ minh họa một failure mode.

### 3.3. Volatile không cứu `++`

~~~java
class VolatileCounter {
    volatile int count;

    void increment() {
        count++;
    }
}
~~~

**`volatile` không biến expression `count++` thành atomic read-modify-write.** JIT có thể tạo volatile read, add và volatile write với memory ordering phù hợp, nhưng vẫn có thể xen giữa thread khác. Đối với shared counter, dùng `AtomicInteger.incrementAndGet()`, `LongAdder.increment()` khi chấp nhận semantics cộng dồn phù hợp, hoặc `synchronized`/`Lock` nếu cần bảo vệ nhiều biến cùng lúc.

## 4. Visibility: vì sao dữ liệu đã ghi có thể chưa quan sát được?

Ví dụ:

~~~java
class StopFlag {
    boolean running = true;

    void work() {
        while (running) {
            // công việc
        }
    }

    void stop() {
        running = false;
    }
}
~~~

Đây là class minh họa **có data race nếu hai threads gọi `work`/`stop` không đồng bộ**, và không nên dùng nguyên xi trong ứng dụng. Một thread ghi `running=false` không tạo HB edge tới thread khác chỉ bằng plain assignment.

**Lỗi có thể gặp:** reader tiếp tục thấy stale value hoặc compiler/JIT tối ưu đường loop theo giả định không có synchronized modification phù hợp. JLS `17.4` có ví dụ về loop không terminating khi biến không được đồng bộ.

Không được kết luận từ việc chương trình “luôn dừng trên máy tôi” rằng nó đúng theo JMM: CPU cache, compiler tier, workload và timing có thể khiến bug chỉ xuất hiện lúc khác.

### `volatile` cung cấp gì?

~~~java
class StopFlag {
    volatile boolean running = true;

    void stop() {
        running = false;
    }
}
~~~

Đọc/ghi volatile có **ordering/visibility effects** theo JMM. Một volatile write tới `running` **synchronizes-with** các volatile reads tiếp theo của **cùng variable** trong synchronization order.

Điều đó cho phép flag protocol đúng về visibility nếu các actions khác được đặt đúng trước/sau. Tuy nhiên:

- JMM không hứa một thread chắc chắn được scheduler cho CPU trong một deadline.
- `volatile` không khóa nhiều statements lại thành transaction.
- Nếu chương trình cần wait/notify, blocking hoặc cancellation, ưu tiên `Interrupt`, `BlockingQueue`, `CountDownLatch` hay API concurrency phù hợp thay busy spin không giới hạn.

**Đừng giải thích volatile đơn giản là “mỗi lần đọc bắt buộc lấy từ RAM, không dùng CPU cache”.** Cơ chế vật lý có thể là cache coherence, barriers, compiler scheduling và CPU ISA; điều Java bảo đảm là **observable ordering/visibility semantics**, không chi tiết cache truy cập.

## 5. Ordering: code đúng trong một thread có thể sai khi suy liên luồng

Compiler/JIT và CPU có thể thực hiện transformations/reorderings **miễn giữ intra-thread semantics và hành vi được JMM cho phép**. Câu “Java chạy lệnh từ trên xuống dưới” chỉ an toàn khi nói **hành vi một thread như được quan sát theo quy tắc**; nó không dựng thứ tự toàn phần chung của plain unsynchronized accesses giữa tất cả threads.

Một litmus test nổi tiếng:

~~~java
// Ban đầu x = 0, y = 0
// Thread A                     // Thread B
x = 1;                           y = 1;
r1 = y;                          r2 = x;
~~~

Đây là **pseudocode concurrent litmus**, không phải Java class độc lập. Nếu `x`/`y` là plain shared `int` không đồng bộ, kết quả `r1 = 0` **và** `r2 = 0` là một behavior hợp lệ trong mô hình có data race ở tình huống này.

**Không cần giả định hai threads đã “du hành thời gian”.** Hai writes/reads không có HB edges nên JMM có thể cho phép những quan sát không tương ứng trực giác tuần tự của người đọc source.

Nếu cả `x` và `y` là `volatile` và các actions như trên, ordering đồng bộ làm cấu hình “cả hai reads đều 0” không phù hợp: mỗi thread có volatile write trước volatile read, nên synchronization order toàn phần không thể xếp cả hai reads trước writes tương ứng mà vẫn tôn trọng program order.

**Điểm quan trọng:** không nên dùng `Thread.sleep` hoặc `Thread.yield` để “chữa reordering”. Chúng không thiết lập cạnh HB thông thường giữa các plain writes/reads của hai threads.

## 6. Happens-before: cách hợp thức hóa visibility và ordering

JLS `17.4.5` xác định HB từ:

1. **Program order:** nếu action A trước B trong cùng thread thì A happens-before B.
2. **Synchronizes-with:** những cặp synchronization actions mà JLS quy định.
3. **Transitivity:** nếu A HB B và B HB C thì A HB C.

Những synchronization edges đáng nhớ:

| Nguồn (release) | Đích (acquire) | HB quan trọng |
| --- | --- | --- |
| `unlock` monitor M | `lock` sau trên **cùng monitor M** | Writes trong critical section trước được nhìn thấy phù hợp |
| `volatile write` field V | `volatile read` tiếp theo của **V** theo synchronization order | Các ordinary writes trước release có thể được publish |
| `Thread.start()` từ thread A | Những actions của thread được start | Dữ liệu chuẩn bị trước start được publish |
| Thread T hoàn tất | Actions ở thread khác phát hiện termination (`join` thành công, v.v.) | Kết quả do T ghi có thể đọc sau join |
| `interrupt` tới thread | Action nhận biết interrupt theo JLS | Ordering liên quan interruption |
| Class initialization theo JLS | Những accesses cần thấy class initialized | Các bảo đảm class-init synchronization |
| `CountDownLatch.countDown` | `await` return tương ứng | Memory consistency guarantee theo `java.util.concurrent` API |

**Cẩn thận:** `Thread.join()` có thể hoàn tất không bình thường qua `InterruptedException`/timeout; chỉ suy HB từ đúng semantics phát hiện thread termination. `await` cũng phải thành công theo contract để suy action relationship tương ứng.

### Release/acquire và transitive visibility

~~~text
Thread A                         Thread B
data = 42
ready = true   [volatile write] --sw--> [volatile read] ready == true
                                                |
                                                v
                                         print(data) => 42

data write --program order--> volatile write
volatile read --program order--> data read

=> data write happens-before data read.
~~~

Đây là **message-passing pattern** khi `data` chỉ được writer publish trước `ready`, không có competing writers ngoài protocol. Nếu reader chỉ xem `ready` như plain variable, không có synchronization edge và suy luận trên không còn đúng.

### HB không đồng nghĩa “real-time before”

Ngay cả khi log timestamp từ thread A nhỏ hơn timestamp từ thread B, không có đồng bộ thì log order không xây dựng HB cho shared field bạn muốn kiểm chứng. Logger/I/O có thể có locking riêng, vô tình đổi timing và che race.

## 7. Runnable Java 17+: AtomicInteger và synchronized counter

Tạo file **`AtomicityDemo.java`**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicityDemo {
    private static final Object LOCK = new Object();

    private static int protectedCount;
    private static final AtomicInteger atomicCount = new AtomicInteger();

    private static void runIncrements() {
        for (int i = 0; i < 100_000; i++) {
            synchronized (LOCK) {
                protectedCount++;
            }
            atomicCount.incrementAndGet();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread first = new Thread(AtomicityDemo::runIncrements);
        Thread second = new Thread(AtomicityDemo::runIncrements);

        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("synchronized=" + protectedCount);
        System.out.println("atomic=" + atomicCount.get());
    }
}
~~~

Chạy:

~~~bash
javac -g AtomicityDemo.java
java AtomicityDemo
~~~

**Stdout xác định nếu threads hoàn tất bình thường:**

~~~text
synchronized=200000
atomic=200000
~~~

Vì:

- Hai threads, mỗi thread chạy **100.000** increments.
- Cả hai truy cập `protectedCount` trong **cùng monitor LOCK**; increments không xen vào critical section đồng thời.
- `AtomicInteger.incrementAndGet()` là atomic update operation.
- Main gọi `join()` cả hai threads trước khi in, nên các actions trước completion được bảo đảm quan sát thích hợp theo HB.
- Chỉ main thread in stdout, nên thứ tự hai dòng cố định.

**Không đồng nhất semantics tổng thể:** synchronized bảo vệ được nhiều fields nếu cùng nằm trong protocol lock; `AtomicInteger` cung cấp atomic operations trên giá trị của riêng nó. Nếu muốn cập nhật **hai trường như một transaction**, chỉ hai `AtomicInteger` độc lập thường không đủ.

### Vì sao không đưa plain `count++` vào kết quả kỳ vọng?

Nếu thêm một plain `int racyCount++` bên cạnh, giá trị có thể nhỏ hơn 200.000, đôi khi bằng 200.000 do scheduling; **không có output cố định đáng tin**. Bài này không đo race bằng câu lệnh `println` mà chứng minh những cách triển khai đúng.

## 8. Runnable Java 17+: Volatile publication và message passing

Một ví dụ dùng volatile làm **cờ công bố dữ liệu**:

Tạo file **`VolatileMessage.java`**:

~~~java
public class VolatileMessage {
    private static int payload;
    private static volatile boolean ready;

    public static void main(String[] args) throws InterruptedException {
        Thread reader = new Thread(() -> {
            while (!ready) {
                Thread.onSpinWait();
            }
            System.out.println("payload=" + payload);
        });

        reader.start();

        Thread writer = new Thread(() -> {
            payload = 42;
            ready = true;
        });

        writer.start();
        writer.join();
        reader.join();

        System.out.println("ready=" + ready);
    }
}
~~~

Chạy:

~~~bash
javac -g VolatileMessage.java
java VolatileMessage
~~~

**Stdout khi các threads được lên lịch và hoàn tất bình thường:**

~~~text
payload=42
ready=true
~~~

Lý do `payload=42`:

1. Writer ghi plain `payload = 42` trước volatile write `ready=true`.
2. Reader kiểm tra `ready` bằng volatile read và thoát loop khi thấy true.
3. Volatile write/read cùng biến thiết lập synchronization/HB edge.
4. Theo transitivity, ghi `payload` happens-before đọc `payload` trong reader.
5. Main `join()` cả threads, sau đó đọc `ready` và in kết quả.

**Điều kiện thực tế:** JMM không cam kết thread sẽ luôn được scheduler cấp CPU trong một khoảng thời gian cụ thể; vòng spin không có deadline. Ví dụ phù hợp để hiểu visibility, không phải cách chờ production tốt cho hiệu suất. Với application thật, sử dụng `CountDownLatch`, `BlockingQueue`, `Condition` hoặc `CompletableFuture` để chờ thay vì busy spin; `Thread.onSpinWait` chỉ là hint cho CPU/JVM, không tự tạo memory synchronization edge.

**Không nhầm:** `payload` không cần khai báo volatile trong protocol này vì **volatility của cờ ready** đã đủ publish ordinary write trước cờ nếu protocol được tuân thủ. Nhưng nếu một thread khác vẫn sửa `payload` không đồng bộ, bài toán thay đổi.

## 9. `synchronized`: mutual exclusion đồng thời tạo HB

Một block:

~~~java
synchronized (lock) {
    balance = balance + amount;
    transactionCount++;
}
~~~

Là **đoạn code minh họa**, cần `lock`/`balance`/`transactionCount` và class thích hợp.

Nếu **mọi threads** dùng **cùng object monitor `lock`** để đọc/ghi các fields liên quan, monitor bảo đảm:

- **Mutual exclusion:** hai threads không chạy phần critical section của **cùng monitor** tại cùng thời điểm.
- **Visibility/ordering:** một monitor unlock **synchronizes-with** một lock tiếp theo của monitor đó.
- **Compound invariant:** có thể bảo vệ cả `balance` và `transactionCount` để chúng được cập nhật/quan sát thành một đơn vị logical transaction dưới lock.

**Sai phổ biến:** writer dùng `synchronized(lockA)`, reader dùng `synchronized(lockB)` và nghĩ vì “cả hai đều synchronized” nên đã có HB. Không — locks khác nhau không tạo edge unlock→lock của **cùng monitor**.

### Instance synchronized vs static synchronized

- `synchronized void f()` lock trên `this`.
- `static synchronized void g()` lock trên `YourClass.class`.
- `synchronized(otherObject)` lock trên `otherObject`.

Vì vậy `f()` của hai instances khác nhau không khóa cùng monitor. Dùng một shared lock cố định hoặc cùng instance theo ownership thực tế.

### Lock ở mọi đường đọc lẫn ghi

Nếu writer bảo vệ field dưới lock nhưng reader đọc field plain ngoài lock, không thể mặc định reader được synchronized-with writer. Thread safety cần **protocol đồng nhất** trên mọi accesses đến shared mutable state.

## 10. `volatile`: visibility/ordering không phải multi-field transaction

Xét:

~~~java
class PairState {
    volatile int x;
    volatile int y;

    void setBoth(int a, int b) {
        x = a;
        y = b;
    }

    int sum() {
        return x + y;
    }
}
~~~

Hai writes là **hai volatile writes riêng**, và hai reads là **hai volatile reads riêng**. Reader có thể đọc cặp giá trị từ **hai trạng thái khác nhau** khi writer xen vào. `volatile` không làm hai writes/tham chiếu thành một atomic snapshot.

Giải pháp tùy bài toán:

- Dùng `synchronized`/`Lock` quanh cả set/read.
- Dùng **immutable snapshot** được publish qua **một volatile reference**:
  
~~~java
record Snapshot(int x, int y) {}

volatile Snapshot current = new Snapshot(0, 0);
~~~

Đây là đoạn minh họa trong một class, không phải file độc lập. Khi writer gán một **new immutable Snapshot** vào `current` và reader đọc `current` **một lần** rồi lấy cả `x`/`y`, reader có snapshot nhất quán, không bị trộn fields giữa hai object versions.

**Cẩn thận:** nếu reader viết `current.x() + current.y()` mà gọi `current` hai lần, hai volatile reads có thể thấy hai snapshots khác nhau. Nên dùng:

~~~java
Snapshot snapshot = current;
int total = snapshot.x() + snapshot.y();
~~~

Đây là đoạn tiếp nối minh họa và cần context class. **Volatile reference không biến object được trỏ tới thành immutable**. Nếu object mutable bên trong bị sửa sau publish không đồng bộ, vẫn có race.

## 11. Atomic classes và Compare-And-Set (CAS)

`AtomicInteger`, `AtomicLong` và `AtomicReference` cung cấp atomic operations và memory effects được API ghi rõ, thường dựa trên VarHandle primitives/các machine-level synchronization instructions phù hợp.

### Compare-and-set

~~~java
AtomicInteger value = new AtomicInteger(10);

boolean ok = value.compareAndSet(10, 20);
int now = value.get();
~~~

Là đoạn minh họa cần import `java.util.concurrent.atomic.AtomicInteger`, kết quả `ok == true` và `now == 20` nếu không có thread khác can thiệp.

**CAS** kiểm tra giá trị hiện hành so với expected và chỉ cập nhật khi phù hợp như một thao tác atomic. Nó là building block cho lock-free algorithms, nhưng:

- **CAS failure** có thể do concurrent interference.
- Những **weak CAS** variants có thể **spuriously fail** theo API.
- `compareAndSet` một field **không tạo transaction** đồng thời trên nhiều fields.
- Nếu logic retry tính toán có **side effects**, gọi lại lambda trong `updateAndGet` có thể lặp side effects nhiều lần — API khuyến cáo function không có side effects.
- **ABA problem:** giá trị A→B→A khiến CAS so A vẫn thành công dù state thay đổi trong khoảng đó; với cấu trúc lock-free phức tạp cần version/stamps/algorithms phù hợp.

### `LongAdder` và snapshot consistency

`LongAdder` thích hợp cho counters có contention cao và `sum()` có thể tổng hợp nhiều cells, nhưng **`sum()` không phải atomic snapshot khi updates chạy đồng thời**. Không dùng `LongAdder` nếu mọi đọc phải mang tính linearizable như `AtomicLong.get()` trong một protocol cần atomic state.

## 12. Runnable Java 17+: toàn bộ state dưới cùng monitor

Tạo file **`LockedAccount.java`**:

~~~java
public class LockedAccount {
    private int balance;
    private int operations;

    public synchronized void deposit(int amount) {
        balance += amount;
        operations++;
    }

    public synchronized String snapshot() {
        return balance + ":" + operations;
    }

    public static void main(String[] args) throws InterruptedException {
        LockedAccount account = new LockedAccount();

        Thread a = new Thread(() -> {
            for (int i = 0; i < 5_000; i++) {
                account.deposit(2);
            }
        });
        Thread b = new Thread(() -> {
            for (int i = 0; i < 5_000; i++) {
                account.deposit(3);
            }
        });

        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("balance:operations=" + account.snapshot());
    }
}
~~~

Chạy:

~~~bash
javac -g LockedAccount.java
java LockedAccount
~~~

**Stdout chính xác nếu cả hai threads hoàn tất bình thường:**

~~~text
balance:operations=25000:10000
~~~

Giải thích:

- A đóng góp `5000 * 2 = 10000`, B đóng góp `5000 * 3 = 15000`, tổng **25000**.
- Tổng số lần `deposit` là **10000**.
- `deposit` và `snapshot` là **instance synchronized** trên **cùng `account`**, nên critical sections loại trừ nhau; main `join` bảo đảm hai threads đã hoàn tất trước snapshot.
- Đây là ví dụ nhiều fields phải nhất quán với nhau, khác với một `AtomicInteger` counter.

**Giới hạn:** ví dụ không kiểm soát amount âm hoặc overflow; nếu viết một tài khoản ngân hàng thực tế cần validation, semantics tiền tệ, transaction storage và domain rules riêng. Bài chỉ tập trung vào JMM.

## 13. Thread.start, Thread.join và safe handoff

Không phải chỉ `volatile`/`synchronized` mới tạo HB; lifecycle của threads cũng có synchronization rules.

### `Thread.start()`

~~~java
class StartExample {
    int data;

    void runExample() throws InterruptedException {
        data = 42;
        Thread worker = new Thread(() -> {
            System.out.println(data);
        });
        worker.start();
        worker.join();
    }
}
~~~

Đây là class minh họa, không có `main`. Ghi `data=42` trong parent thread xảy ra trước `start()` theo program order; `start()` tạo synchronizes-with edge tới thread worker. Vì thế worker thấy dữ liệu được publish phù hợp nếu không có competing writes.

### `Thread.join()`

Khi main thành công phát hiện worker đã kết thúc qua `join()`, actions của worker xảy ra trước việc main quan sát dữ liệu sau join theo JMM. Trong hai demos `AtomicityDemo` và `LockedAccount`, `join()` là phần quan trọng khiến main đọc kết quả sau khi mọi increments hoàn tất.

**Đừng suy** rằng `Thread.sleep(100)` thay thế `join()`. Sleep chỉ tạm dừng một thread, không đợi chính xác worker completion và không có cùng HB guarantee.

### Publication qua concurrent utilities

`java.util.concurrent` định nghĩa **memory consistency effects** cho nhiều utilities:

- Actions trước `CountDownLatch.countDown()` happen-before actions sau một `await()` tương ứng trả về thành công.
- Actions trước khi submit task cho `ExecutorService` happen-before task actions theo documented contract.
- Task actions happen-before actions sau `Future.get()` nhận kết quả thành công theo documented contract.
- Enqueue/dequeue trong concurrent collections có memory consistency guarantees theo từng API.

**Không phải cứ “dùng một method trong concurrent package” là mọi fields tự động thread-safe**. Luôn đọc contract của đúng operation và xây protocol phù hợp.

## 14. Runnable Java 17+: CountDownLatch thay busy spin

Tạo file **`LatchHandoff.java`**:

~~~java
import java.util.concurrent.CountDownLatch;

public class LatchHandoff {
    private static int payload;
    private static final CountDownLatch READY = new CountDownLatch(1);

    public static void main(String[] args) throws InterruptedException {
        Thread consumer = new Thread(() -> {
            try {
                READY.await();
                System.out.println("payload=" + payload);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();

        Thread producer = new Thread(() -> {
            payload = 42;
            READY.countDown();
        });

        producer.start();
        producer.join();
        consumer.join();
        System.out.println("completed");
    }
}
~~~

~~~bash
javac -g LatchHandoff.java
java LatchHandoff
~~~

**Stdout trong lần chạy bình thường, không bị interrupt:**

~~~text
payload=42
completed
~~~

Ở đây `payload` **không cần volatile** vì producer ghi trước `countDown()`, consumer đọc sau `await()`; API `CountDownLatch` cung cấp HB, còn main `join` bảo đảm thứ tự in hai dòng.

Đây là pattern thích hợp hơn một vòng spin dài vì consumer **chờ có blocking mechanism**. Nếu consumer bị interrupt, code khôi phục interrupt flag và không in `payload`; output phía trên được nêu rõ chỉ cho normal execution.

**Không dùng `CountDownLatch` như resettable event:** count đã về 0 không reset. Nếu cần repeated signaling, cân nhắc `Phaser`, `Condition` hoặc API phù hợp.

## 15. Final fields và safe publication

JLS `17.5` đưa ra semantics đặc biệt cho **final fields**, rất hữu ích với immutable objects.

~~~java
final class Point {
    final int x;
    final int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
~~~

Đây là class minh họa. Nếu constructor được thực hiện đúng, không để `this` escape trước khi hoàn tất, final-field semantics giúp những threads nhận reference thấy các final values được khởi tạo đúng trong những điều kiện JLS quy định.

**Không suy rằng toàn bộ object graph tự động thread-safe chỉ vì một reference là final.** Ví dụ:

~~~java
final class Holder {
    final int[] values = {1, 2, 3};
}
~~~

Reference field `values` là final, nhưng **các phần tử array vẫn mutable**. Nếu một thread sửa `values[0]` và thread khác đọc không đồng bộ, vẫn có race đối với phần tử.

### Safe publication hữu ích

Các lựa chọn an toàn để publish object mới:

- Chia sẻ thông qua **volatile reference** đúng protocol.
- Publish trong monitor/lock và đọc dưới cùng monitor/lock.
- Put/take qua một concurrent collection có memory consistency effects.
- Dùng thread-start/handoff và join theo documented HB.
- Xây object immutable và dùng **final fields** đúng, tránh `this` escape trong constructor.

**Final-field guarantees không phải license công bố mọi mutable state không đồng bộ**. Một object có final fields có thể an toàn hơn về initialization, nhưng mutable nested objects và việc publish reference vẫn cần cân nhắc protocol.

### Double-checked locking

Khi viết lazy singleton theo double-checked locking, trường `instance` phải dùng cơ chế publication thích hợp (thường **`volatile`**) để ngăn nguy cơ đọc instance chưa được publish đúng. Cách đơn giản và dễ kiểm chứng hơn là **static holder idiom** hoặc `enum` singleton khi phù hợp.

## 16. Reordering không đồng nghĩa chương trình được trả giá trị “tùy ý”

Một hiểu nhầm khác: nếu thiếu `volatile` thì Java “có thể trả bất kỳ bit pattern nào”. JMM **không** cho phép tùy tiện tạo các giá trị từ hư vô; nó có quy tắc well-formed executions và causality (§17.4.7–§17.4.8). Tuy vậy chương trình có data race có thể có hành vi không trực giác mà programmer không được phép bỏ qua.

Cách học đúng:

- Với **data-race-free design**, bạn có một mô hình để suy luận nhất quán theo synchronization/HB.
- Với **racy design**, đừng thay mô hình bằng những suy luận “CPU chắc chạy theo source” hoặc “trên x86 cache coherence sẽ cứu tôi”.
- Với **volatile**, dùng đúng release/acquire communication pattern hoặc atomic update methods, không xem nó như một lock.
- Với **locks**, bảo vệ mọi accesses cùng dữ liệu theo cùng protocol.

### Data race freedom và sequential consistency

JLS nói chương trình **correctly synchronized** có các executions trông như **sequentially consistent**. Nhưng sequential consistency **không tự làm một phép nhiều bước thành atomic**: nếu logic yêu cầu đọc/sửa/ghi như một transaction, bạn vẫn cần mutual exclusion hoặc atomic RMW. Đừng đồng nhất “không có data race” với “mọi invariant của nghiệp vụ chắc chắn đúng”.

## 17. VarHandle: Plain, Opaque, Acquire/Release, Volatile

Từ Java 9, `java.lang.invoke.VarHandle` cung cấp các access modes với những memory-ordering guarantees khác nhau, hữu ích khi xây primitives/concurrent structures cấp thấp.

| Mode | Ví dụ operations | Ý nghĩa khái quát |
| --- | --- | --- |
| **Plain** | `get`, `set` | Như truy cập non-volatile field, ít ordering; không tự giải quyết race |
| **Opaque** | `getOpaque`, `setOpaque` | Atomic/coherent đối với cùng variable theo API, ordering yếu hơn acquire/release |
| **Acquire** | `getAcquire` | Ngăn các accesses sau bị reorder lên trước theo semantics acquire; phối hợp với release |
| **Release** | `setRelease` | Publish các accesses trước theo semantics release; phối hợp với acquire |
| **Volatile** | `getVolatile`, `setVolatile` | Có volatile ordering guarantees mạnh hơn |
| **Atomic RMW** | `compareAndSet`, `getAndAdd` | Atomic update với memory semantics tương ứng mode |

**Mỗi access mode có hợp đồng riêng**; không được nói tất cả acquire/release/opaque/volatile đều tương đương hoặc mọi thao tác plain 64-bit là bitwise atomic. Java SE 17 `VarHandle` tài liệu hóa chi tiết differences, bao gồm spurious failures của weak CAS.

### Cẩn thận khi dùng fences

`VarHandle.fullFence()`, `acquireFence()`, `releaseFence()` và các methods liên quan hỗ trợ đặt ordering constraints, nhưng **không tự biến mọi plain variable access thành thread-safe protocol**. Fences không chữa một phép `count++` non-atomic thành atomic.

**Ưu tiên** `volatile`, `AtomicInteger`, `synchronized`, `Lock` hoặc concurrent utilities trước; chỉ dùng VarHandle/fences khi thật sự cần low-level algorithm và có khả năng kiểm chứng.

## 18. Java Memory Model và CPU memory model

CPU có cache coherence, store buffers và instruction reordering; compiler JIT còn tối ưu machine code theo target architecture. **JMM đặt contract ở tầng Java**, còn HotSpot/CPU phải triển khai bằng instructions/barriers/phần cứng phù hợp.

~~~text
Java code + JMM promises
          |
          v
Compiler (interpreter, C1, C2, etc.)
          |
          v
Architecture-specific machine code
          |
          v
CPU caches, store buffers, memory ordering
          |
          v
Observable Java behavior phải phù hợp JMM
~~~

Trên x86-64, một số ordering guarantees của CPU mạnh hơn trên kiến trúc yếu hơn; trên ARM/AArch64 có instructions acquire/release và memory barriers phù hợp. Tuy nhiên **không viết Java code dựa vào thói quen “x86 thường có vẻ đúng”**: JIT và runtime được phép tối ưu theo JMM, và ứng dụng có thể chạy trên hardware/JVM khác.

**`volatile` không đồng nghĩa “mỗi lần phải đưa dữ liệu ra DRAM”**. Cache lines có thể ở cache nhưng được quản lý coherence/ordering đúng; điều cần đo hoặc chứng minh là Java-level HB semantics.

## 19. Chọn công cụ đúng với yêu cầu

| Nhu cầu | Cách phù hợp | Tránh hiểu nhầm |
| --- | --- | --- |
| Một cờ báo hoàn thành/publish một lần | `volatile boolean` hoặc `CountDownLatch` | `volatile` không hứa scheduler wake deadline |
| Một counter atomic đơn giản | `AtomicInteger` / `AtomicLong` | `volatile int++` không atomic |
| Counter thống kê contention cao | `LongAdder` | `sum` không phải snapshot atomic khi updates chạy |
| Bất biến nhiều fields | `synchronized` hoặc `Lock` | Nhiều volatile fields không thành transaction |
| Trao thông điệp giữa threads | `BlockingQueue` / concurrent collection | Plain shared list không đủ an toàn |
| Wait for completion/result | `join()` / `Future.get()` / latch | `sleep()` không thay synchronization |
| Shared immutable snapshot | Immutable record/object + volatile publication | Volatile reference không làm object mutable trở thành immutable |
| Lock-free primitive algorithm | CAS/VarHandle + thiết kế kiểm chứng | CAS một field không atomic nhiều fields |
| Tạm dừng/cancel worker | Interrupt/protocol phù hợp | Plain boolean loop có visibility risk |
| Chỉ thread-local state | Không cần shared lock cho dữ liệu không chia sẻ | Đừng đồng bộ hóa thừa nhưng phải xác nhận không escape |

**Đối với code doanh nghiệp**, bắt đầu từ abstractions cao như executors, queues, structured handoff, immutable values và locks đúng protocol. Chỉ hạ xuống memory modes yếu hơn khi có benchmark và kiểm chứng về correctness.

## 20. Những ngộ nhận phổ biến

| Ngộ nhận | Điều đúng |
| --- | --- |
| “Một source statement tương đương một thao tác atomic.” | `count++` gồm read-modify-write, không atomic tự thân. |
| “Một `volatile int++` là atomic.” | Sai; volatile read/write đơn lẻ không tạo RMW atomic. |
| “Atomicity tự kéo theo ordering của mọi fields khác.” | Không; xét memory effects của operation và HB edges. |
| “Mọi thread luôn đọc giá trị ghi gần nhất theo wall clock.” | JMM xét synchronization/HB, không chỉ thời gian thực. |
| “`volatile` nghĩa không dùng CPU cache.” | JMM quy định observable semantics, không phương thức truy cập DRAM. |
| “`Thread.sleep` giúp flush cache/happens-before.” | Không tạo HB giữa plain shared accesses. |
| “`Thread.yield` sửa visibility.” | Không; chỉ là scheduling hint. |
| “Hai blocks `synchronized` với locks khác nhau đồng bộ được.” | Unlock→lock HB cần đúng **cùng monitor**. |
| “Một method instance synchronized khóa mọi instances.” | Nó khóa `this` instance đang gọi. |
| “`static synchronized` khóa trên instance.” | Khóa trên Class object tương ứng. |
| “Volatile reference khiến object được trỏ tới thread-safe.” | Không; mutable fields cần protocol riêng. |
| “Hai volatile fields luôn là snapshot nhất quán.” | Không; các accesses là riêng lẻ. |
| “`AtomicInteger` tự tạo transaction nhiều biến.” | Không, bảo vệ atomic operations của giá trị đó. |
| “`LongAdder.sum()` atomic khi updates đang chạy.” | Không bảo đảm snapshot atomic dưới concurrent updates. |
| “Một lần chạy không tái hiện race nghĩa code đúng.” | Không; timing/JIT/architecture có thể che bug. |
| “`Thread.join()` chỉ chờ và không liên quan visibility.” | Thành công quan sát termination có HB theo JMM. |
| “Data-race-free nghĩa mọi logic tự động đúng.” | Cần cả invariant và atomic grouping đúng. |
| “`final List` nghĩa list immutable.” | Final reference không làm object được trỏ tới immutable. |
| “`volatile long` có thể bị tearing như plain long.” | Volatile 64-bit read/write atomic theo JLS. |
| “`VarHandle.fullFence` khiến `x++` atomic.” | Fence không thay thế atomic RMW. |
| “Cứ dùng volatile/atomic là không cần xét thứ tự thao tác.” | Vẫn phải thiết kế đúng protocol. |
| “Java Memory Model chỉ là quy tắc cho HotSpot.” | JMM là contract Java language, mọi implementation hợp lệ phải đáp ứng. |

## 21. Câu hỏi tự kiểm tra (có đáp án)

**1.** Atomicity, visibility và ordering khác gì?  
**Đáp án:** Atomicity nói về thao tác được thực hiện như đơn vị không thể bị xen ngang theo hợp đồng; visibility nói về giá trị ghi thread khác được phép nhìn thấy; ordering nói về quan hệ thứ tự quan sát giữa các actions.

**2.** Vì sao `count++` không atomic?  
**Đáp án:** Nó đọc, tính toán rồi ghi; hai threads có thể cùng đọc giá trị cũ và ghi một kết quả, gây lost update.

**3.** `volatile int count; count++;` có đúng cho shared counter không?  
**Đáp án:** Không; dùng atomic RMW như `AtomicInteger.incrementAndGet` hoặc critical section với cùng lock.

**4.** Volatile publication `payload=42; ready=true` yêu cầu reader gì?  
**Đáp án:** `ready` phải volatile và reader phải đọc flag thấy giá trị được publish theo đồng bộ phù hợp trước khi đọc payload, đồng thời không có competing writes phá protocol.

**5.** `sleep()` có bảo đảm thread A thấy writer update không?  
**Đáp án:** Không. Sleep không tạo required HB edge.

**6.** Tại sao `synchronized(lockA)` và `synchronized(lockB)` không đồng bộ nhau?  
**Đáp án:** Chúng bảo vệ các monitors khác nhau, không có unlock(lockA)→lock(lockB) synchronizes-with rule.

**7.** `AtomicityDemo` in gì khi hoàn tất?  
**Đáp án:** `synchronized=200000` và `atomic=200000`, nhờ atomic/synchronized increments và join.

**8.** Tại sao thread main cần `join()` trước khi in?  
**Đáp án:** Đợi completion và có HB từ thread actions tới việc quan sát sau join.

**9.** `VolatileMessage` có thể khiến reader thấy ready=true nhưng payload cũ không?  
**Đáp án:** Không theo protocol bài với một writer, không competing writes: volatile HB publish payload=42.

**10.** Hai volatile fields `x`/`y` có là snapshot atomic không?  
**Đáp án:** Không; dùng lock hoặc immutable object được publish qua một volatile reference.

**11.** Plain `long` read/write luôn portable 64-bit atomic không?  
**Đáp án:** Không được bảo đảm theo JLS `17.7`; volatile long read/write được bảo đảm atomic.

**12.** Một `final int[]` có làm array elements immutable không?  
**Đáp án:** Không, reference không đổi nhưng contents vẫn có thể mutable và racy.

**13.** CAS có hoàn toàn loại ABA problem không?  
**Đáp án:** Không; A→B→A có thể khiến CAS so expected A vẫn thành công.

**14.** Acquire/release và plain VarHandle modes có cùng semantics không?  
**Đáp án:** Không; release/acquire cung cấp ordering mà plain không có.

**15.** Câu “mọi executions của correctly synchronized program có vẻ sequentially consistent” có biến multi-step invariant thành atomic không?  
**Đáp án:** Không; logic multi-step cần critical section/atomic grouping riêng.

## 22. Tài liệu tham khảo chính thức

- [Java Language Specification SE 17 — Chapter 17 Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): đặc biệt **§17.4** JMM, §17.4.4 synchronization order, §17.4.5 happens-before, §17.5 final-field semantics, §17.7 non-atomic treatment of double/long.
- [Java SE 17 — `java.util.concurrent` package](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html): concurrent utility memory consistency effects.
- [Java SE 17 — `AtomicInteger`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html): atomic increment/CAS và memory effects.
- [Java SE 17 — `AtomicLong`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicLong.html): atomic 64-bit values.
- [Java SE 17 — `LongAdder`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html): high-contention counters và non-atomic `sum()` under concurrent updates.
- [Java SE 17 — `CountDownLatch`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): blocking handoff và memory consistency effect.
- [Java SE 17 — `Thread`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): `start`, `join`, `interrupt` và `onSpinWait`.
- [Java SE 17 — `VarHandle`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/VarHandle.html): Plain/Opaque/Acquire/Release/Volatile access modes, CAS và fences.
- [Java SE 17 — `Future`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): task execution và memory consistency after `get()`.
- [Java SE 17 — `ReentrantLock`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html): explicit lock với memory synchronization semantics.
- [OpenJDK JSR 133 — Java Memory Model FAQ](https://www.cs.umd.edu/~pugh/java/memoryModel/jsr-133-faq.html): tài liệu nền tảng mang tính giải thích, nên ưu tiên JLS hiện hành khi cần xác định rule chính xác.

**Ghi nhớ:** Khi nhiều threads dùng chung mutable state, **atomicity** quyết định thao tác có bị xen kẽ không, **visibility** quyết định một thread quan sát dữ liệu của thread khác như thế nào, và **ordering** quyết định những giới hạn tái sắp xếp/quan sát thao tác. Java kết nối chúng bằng **happens-before**. `volatile` phù hợp cho publication flags và independent reads/writes có ordering, nhưng **không biến `++` thành atomic**. `synchronized`/`Lock` bảo vệ critical sections và invariants; atomic classes/CAS cung cấp atomic operations; `start`/`join`/latches/queues có memory-consistency effects. Thiết kế protocol đúng và đọc contract API quan trọng hơn phỏng đoán thứ tự CPU thực thi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-threads-and-synchronization-008">Quan hệ happens-before qua khóa, volatile, start và join</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ happens-before qua khóa, volatile, start và join

**Happens-before (HB)** là quan hệ thứ tự được **Java Memory Model (JMM)** định nghĩa để giải thích khi nào những thao tác của một thread phải được quan sát đúng bởi thread khác. Nó đặc biệt quan trọng khi nhiều threads cùng truy cập một trạng thái có thể thay đổi: một thread ghi dữ liệu, thread khác đọc dữ liệu; nếu không có quan hệ đồng bộ cần thiết, chương trình có thể quan sát **giá trị cũ**, thứ tự không như kỳ vọng hoặc kết quả khác nhau giữa những lần chạy.

Bốn cơ chế trọng tâm:

- **Khóa monitor (`synchronized`):** một lần **unlock** trên monitor `m` happens-before mọi lần **lock tiếp theo** trên **cùng monitor** theo synchronization order.
- **`volatile`:** một lần **write** vào biến volatile `v` happens-before các lần **read tiếp theo** của **chính biến `v`** theo synchronization order.
- **`Thread.start()`:** lệnh gọi `start()` trên thread `t` happens-before mọi action trong thread `t` được khởi động.
- **`Thread.join()`:** mọi action của thread `t` happens-before action ở thread khác **phát hiện `t` đã kết thúc**, như khi `join()` hoàn tất thành công do thread đã kết thúc.

Các quy tắc này cộng với **program order** trong từng thread và **tính bắc cầu (transitivity)** tạo thành mạng lưới happens-before. **Happens-before không đồng nghĩa “chạy trước theo đồng hồ”, “tất cả operations tự nguyên tử” hoặc “scheduler sẽ cho thread chạy ngay”.** Nó là hợp đồng về **thứ tự/visibility hợp lệ giữa actions**, không phải lịch thi hành thời gian thực.

Phạm vi bài: Java SE 17+ và Java Memory Model chính thức. Các chi tiết CPU cache, fences, HotSpot/JIT có thể giúp hình dung nhưng **không thay thế luật của JLS**.

## 1. Vì sao cần Java Memory Model?

Ví dụ có hai threads:

```java
// Pseudocode đặt trong cùng một class.
int data = 0;
boolean ready = false;

// Writer thread:
data = 42;
ready = true;

// Reader thread:
if (ready) {
    System.out.println(data);
}
```

Nếu `data` và `ready` là những fields thông thường, có **data race** vì threads truy cập cùng fields, ít nhất một truy cập là write, nhưng thiếu HB ordering giữa những truy cập xung đột. Một lập trình viên chỉ dựa trên thứ tự trong source dễ nghĩ: “Reader thấy `ready == true` thì chắc chắn `data == 42`”. **Kết luận đó không được JMM bảo đảm** cho chương trình racy này.

Điều có thể xảy ra dưới JMM/JIT/runtime:

- Reader chưa thấy `ready` đổi và đi theo nhánh false.
- Reader có thể thấy `ready == true` nhưng vẫn thấy một giá trị `data` không như ý định publish do thiếu đồng bộ.
- Một vòng `while (!ready) {}` không volatile có thể không kết thúc; JIT có thể hoist/cache những reads hợp lệ theo execution thiếu HB.
- Kết quả có thể “đúng hàng triệu lần” trên máy của bạn nhưng vẫn **không an toàn về mặt Java semantics**.

Không nên giải thích đơn giản rằng “CPU luôn chạy đảo đúng hai dòng đó”. JMM cho phép nhiều cách thực thi/quan sát khi thiếu đồng bộ; nguyên nhân có thể liên quan compiler optimizations, memory hierarchy, visibility hoặc reorderings hợp lệ. **Tiêu chuẩn cần kiểm tra là HB**, không chỉ một sơ đồ CPU caches.

### Thuật ngữ nền tảng

| Thuật ngữ | Ý nghĩa |
| --- | --- |
| **Action** | Một thao tác trong mô hình JMM: read/write, lock/unlock, volatile access, thread actions... |
| **Program order (po)** | Thứ tự actions do một thread thực hiện theo ngữ nghĩa chương trình |
| **Synchronization order (so)** | Thứ tự toàn phần của synchronization actions, nhất quán với program order từng thread |
| **Synchronizes-with (sw)** | Cạnh đồng bộ xuyên threads hoặc giữa những actions được JMM xác định |
| **Happens-before (hb)** | Quan hệ bao gồm program order, synchronizes-with và tính bắc cầu |
| **Data race** | Hai accesses xung đột cùng một biến, ít nhất một write, nhưng không được HB sắp thứ tự |
| **Correctly synchronized program** | Không có data races trong những sequentially consistent executions; JMM bảo đảm tính chất sequential consistency theo định lý liên quan |
| **Safe publication** | Công bố object/state để thread khác nhìn thấy những writes khởi tạo cần thiết theo đúng HB rules |

**Ghi nhớ:** chương trình có data race không nhất thiết crash; nó có thể chạy ra output đúng trong hầu hết thí nghiệm. Điều đó không biến một chương trình không có HB thành chương trình an toàn.

## 2. Quan hệ happens-before được xây dựng thế nào?

Java Language Specification, **JLS `17.4.4–`17.4.5**, định nghĩa các cạnh synchronization và happens-before.

### 2.1. Program order trong cùng thread

Nếu action `A` đứng trước `B` trong program order của một thread thì:

```text
Thread T:
   A  ──program-order──►  B

hb(A, B)
```

Điều này không bắt CPU phải thực hiện mọi machine instruction theo đúng thứ tự trực quan của source. Compiler/CPU có thể reorder khi **bảo toàn ngữ nghĩa theo JMM**. Program order là thành phần của quan hệ HB, không phải bản ghi thời gian từng micro-operation trong hardware.

### 2.2. Synchronizes-with qua hành động đồng bộ

Một số cặp actions được JMM công nhận là có cạnh `synchronizes-with`:

- Monitor **unlock → subsequent lock** của cùng monitor.
- Volatile **write → subsequent read** của cùng volatile variable.
- Thread start action → first action của started thread.
- Final action của thread → action ở thread khác phát hiện thread đã kết thúc (chẳng hạn thông qua `join`/`isAlive`).
- Còn có các trường hợp khác như interrupt detection và default initialization, nhưng bài tập trung bốn cơ chế chính.

Cạnh `synchronizes-with` tạo thành cạnh `happens-before`. Sau đó ghép tiếp với program order và các cạnh HB khác.

### 2.3. Tính bắc cầu

Nếu `hb(A, B)` và `hb(B, C)` thì `hb(A, C)`.

```text
Writer                Synchronization          Reader
data = 42         →   ready = true       →    thấy ready == true
                                                │
                                                ▼
                                             đọc data
```

Sơ đồ này **chỉ có bảo đảm** nếu `ready` được publish/consume qua cơ chế đồng bộ phù hợp, chẳng hạn `volatile` và reader thật sự đọc được giá trị `true` từ một trạng thái đã được publish. Khi đó:

1. `data = 42` program-order trước volatile write.
2. Volatile write synchronizes-with volatile read tiếp theo.
3. Volatile read program-order trước read `data`.
4. Tính bắc cầu đưa write `data` trước read `data` theo HB.

Với **một writer, không có writes cạnh tranh vào `data`**, reader thấy `ready` ở trạng thái đã publish sẽ thấy `data == 42`.

### 2.4. HB là quan hệ thứ tự từng phần

Không phải hai actions của hai threads bất kỳ đều có cạnh HB. Nếu không thể suy ra `hb(A, B)` hoặc `hb(B, A)`, các actions có thể **không được sắp thứ tự bởi HB**. Một kết quả quan sát “A chạy trước B theo log” không tự tạo cạnh HB.

**Chú ý:** `synchronized`, volatile và thread lifecycle là các cơ chế tạo cạnh; `Thread.sleep()` và “hy vọng đã chờ đủ lâu” không tự tạo ra HB.

## 3. Visibility, ordering và atomicity là ba việc khác nhau

| Khía cạnh | Câu hỏi | Ví dụ |
| --- | --- | --- |
| **Visibility** | Thread B có thấy giá trị đã được thread A publish hợp lệ không? | Reader thấy `payload == 42` sau volatile flag |
| **Ordering** | Những actions được bảo đảm xuất hiện theo quan hệ cần thiết chưa? | Write payload HB reader read payload |
| **Atomicity** | Operation nhiều bước có được thực hiện như một thao tác nguyên khối đối với threads cạnh tranh không? | `counter++` **không** atomic dù counter volatile |
| **Mutual exclusion** | Có cho phép hai threads vào critical section cùng lúc không? | `synchronized` trên cùng monitor ngăn điều đó |

**Volatile cho visibility/ordering đối với accesses liên quan, không làm một phép read-modify-write (`++`) thành atomic.**

**Monitor lock cung cấp mutual exclusion và HB qua unlock/lock**, nhưng chỉ khi **mọi access cần bảo vệ đều tuân thủ cùng protocol**; một đoạn writer dùng lock còn reader đọc ngoài lock sẽ không tự nhận cùng guarantee.

## 4. Happens-before qua monitor lock (`synchronized`)

`synchronized` dùng **object monitor**. Khi thread vào block synchronized, nó acquire monitor; khi rời block bình thường hoặc do exception, nó release monitor theo JVM semantics.

```java
final class LockedState {
    private final Object monitor = new Object();
    private int value;

    void write(int newValue) {
        synchronized (monitor) {
            value = newValue;
        }
    }

    int read() {
        synchronized (monitor) {
            return value;
        }
    }
}
```

Đây là class Java hợp lệ, chưa có `main`. Nếu thread A hoàn tất `write(42)` trước khi thread B acquire **cùng monitor** để gọi `read()`, thì:

```text
Thread A                          Thread B
lock(m)
value = 42
unlock(m)  ──synchronizes-with──► lock(m)
                                    read value
                                  unlock(m)
```

`unlock(m)` happens-before subsequent `lock(m)`; nhờ program order/transitivity, write tới `value` trước khi unlock xảy ra trước read ở critical section sau. Với không có writer cạnh tranh khác chen giữa, reader sẽ thấy **42**.

### 4.1. Phải là cùng một monitor

Ví dụ **sai**:

```java
void wrong(int newValue) {
    synchronized (new Object()) {
        shared = newValue;
    }
}
```

Đây là method minh họa; `shared` là field trong class giả định. Mỗi lần `new Object()` sinh monitor riêng, **không** có cùng khóa giữa các lần gọi. Writer và reader đồng bộ trên những monitor khác nhau sẽ không tự tạo cặp unlock/lock cần thiết.

Tương tự, một method `synchronized` của instance A dùng monitor `A`, một method `synchronized` của instance B dùng monitor `B`. Hai instances khác nhau **không phải cùng khóa**. `static synchronized` dùng monitor của **Class object**, cũng khác instance monitor trừ khi thiết kế đặc biệt.

### 4.2. Lock không tự bảo vệ field bị đọc ngoài khóa

```java
synchronized (monitor) {
    shared = 42;
}
// Thread khác đọc shared mà không dùng monitor hoặc
// cơ chế đồng bộ hợp lệ khác: thiếu HB cần thiết.
```

Đây là snippet trong class với `monitor` và `shared`. Lock protocol phải được các threads **phối hợp nhất quán**. Không phải cứ một thread dùng `synchronized` thì mọi plain reads của mọi threads khác đều thấy giá trị mới.

### 4.3. `synchronized` hỗ trợ các cập nhật compound

```java
final class SafeCounter {
    private int count;

    synchronized void increment() {
        count++;
    }

    synchronized int get() {
        return count;
    }
}
```

Ở đây `increment()`/`get()` khóa **cùng instance**; two threads không thể đồng thời thực hiện `count++` trên cùng monitor. Vì thế không bị lost updates nếu mọi accesses tuân thủ protocol, và việc đọc `get()` cũng an toàn.

`synchronized` không làm Java code “tuần tự toàn bộ process”; nó chỉ loại trừ những threads cạnh tranh **trên cùng monitor** trong critical sections tương ứng.

### 4.4. Reentrant monitor và exception

Java monitors **reentrant**: thread giữ monitor có thể lock lại monitor đó mà không deadlock chính nó. Monitor tự release khi synchronized block kết thúc vì exception, nhưng **state đã cập nhật một phần không tự rollback**. HB không là cơ chế transaction. Nếu invariants cần cập nhật nhiều fields cùng nhau, thiết kế critical section để reader không quan sát trạng thái giữa chừng.

## 5. Happens-before qua volatile

Một field khai báo `volatile` có các memory-consistency guarantees riêng:

```java
final class Message {
    int number;
    String text;
    volatile boolean ready;
}
```

Một writer:

```java
message.number = 42;
message.text = "OK";
message.ready = true; // volatile write
```

Một reader:

```java
if (message.ready) {  // volatile read
    System.out.println(message.number);
    System.out.println(message.text);
}
```

Các đoạn trên nằm trong methods với biến `message` được truyền/khai báo hợp lệ. Giả sử **một publisher**, reader quan sát `ready == true` và không có concurrent writers sửa `number/text`, thì reader phải quan sát **42 và "OK"**. Lý do không phải vì hai fields đã thành volatile; chúng vẫn là **plain fields**, nhưng được **publish trước volatile write** và **consume sau volatile read**.

### 5.1. Sơ đồ HB cho volatile publication

```text
WRITER THREAD                         READER THREAD

number = 42                            if (ready) {  // volatile read
text = "OK"                                read number
ready = true  // volatile write              read text
                                            }
   │                                       ▲
   └──── volatile synchronizes-with ──────┘

program order + sw + transitivity:
write number/text HB read number/text
```

**Điều kiện:** `ready` phải là **cùng một volatile variable**. Viết `flagA` mà reader đọc `flagB` không tạo cạnh tương ứng chỉ vì cả hai đều volatile.

### 5.2. Đảo thứ tự write có thể làm mất guarantee

**Sai:**

```java
message.ready = true; // công bố quá sớm
message.number = 42;
```

Reader có thể thấy `ready == true` rồi đọc `number` khi writer chưa thực hiện write. Volatile **không đưa một phép ghi ở phía sau nó trong program order quay ngược thời gian** để reader chắc chắn thấy.

**Quy tắc publication:** khởi tạo shared state cần thiết **trước** release action, và consumer đọc release/ready signal **trước** khi đọc các plain fields.

### 5.3. Volatile field không biến object graph thành “volatile mọi nơi”

```java
volatile int[] values = new int[4];
```

Chỉ **reference field `values`** là volatile. `values[0]` không tự trở thành volatile array element. Nếu threads đồng thời update từng phần tử mà không có protocol đồng bộ, vẫn có thể có data race. Thay array reference sau khi khởi tạo bằng volatile write là một cơ chế publication; sửa nội dung array sau khi đã publish cần quy tắc đồng bộ riêng.

### 5.4. Volatile không có mutual exclusion

```java
volatile int counter;

void increment() {
    counter++;
}
```

Đây là snippet trong class. `counter++` gồm logic read → add → write. Hai threads có thể cùng đọc 5, cả hai ghi 6, làm mất một update. Volatile **bảo vệ visibility và các thứ tự theo JMM**, nhưng **không atomically serialize toàn bộ read-modify-write**.

Để đếm an toàn, dùng `synchronized`, `AtomicInteger.incrementAndGet()`, hoặc một cấu trúc phù hợp workload như `LongAdder` khi chỉ cần aggregate counter.

## 6. Happens-before qua `Thread.start()`

`Thread.start()` tạo cạnh HB **từ hành động start tới mọi hành động của thread mới**. Điều quan trọng là các writes **trước** khi gọi start được bảo đảm visibility tới code của child.

```java
final class StartExample {
    static int value;

    public static void main(String[] args) throws InterruptedException {
        value = 17;
        Thread child = new Thread(() -> {
            System.out.println("child sees: " + value);
        });

        child.start();
        child.join();
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 StartExample.java
java StartExample
```

**Standard output chính xác:**

```text
child sees: 17
```

Sơ đồ:

```text
MAIN THREAD                  CHILD THREAD

value = 17
child.start()  ────────────► đọc value
                            in 17

main program order + start sw:
write value HB child read
```

**Không cần `value` là volatile trong ví dụ này**, bởi vì publication xảy ra qua **`start()`**. Một thread được khởi động mới không tự có bảo đảm nhìn thấy các plain writes từ main **sau khi** main đã gọi `start()`, trừ khi có cạnh HB khác.

### 6.1. `new Thread(...)` không thay `start()`

Tạo instance `new Thread(...)` và giữ reference trong biến **không** có nghĩa child body đã chạy hoặc đã nhận start edge. Cần thực sự gọi `start()`; gọi `run()` trực tiếp chỉ là một method call bình thường trên thread đang gọi, không khởi động OS/JVM thread mới qua cơ chế Thread.start.

### 6.2. Start là một chiều

```text
MAIN:     x = 1; start(child); x = 2;
CHILD:                       read(x)
```

`x = 1` HB child read nhờ `start`. Nhưng `x = 2` sau start **không tự** HB child read. Nếu cả main và child cùng truy cập plain `x` sau start mà ít nhất một write, có thể có data race. **Không có bảo đảm child thấy 2 hay chỉ thấy 1** nếu thiếu đồng bộ khác.

### 6.3. Đừng nhầm thread object với task submission

`ExecutorService.submit()` có memory-consistency guarantees được document riêng trong `java.util.concurrent`. Không nên diễn giải rằng gọi `run()` trên một `Runnable` tương đương start thread, hay rằng mọi async framework dùng chính cùng cặp `Thread.start/join` trong user code. Với executors/futures, dùng đúng contract của API thay vì đoán scheduler implementation.

## 7. Happens-before qua `Thread.join()`

Khi thread `t` kết thúc và một thread khác **phát hiện việc kết thúc** đó (đặc biệt `join()` hoàn tất thành công), **mọi actions của `t` happen-before** code của thread đã chờ sau điểm phát hiện.

```java
final class JoinExample {
    static int answer;

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> answer = 84);
        worker.start();

        worker.join();
        System.out.println("after join: " + answer);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 JoinExample.java
java JoinExample
```

**Standard output chính xác:**

```text
after join: 84
```

Sơ đồ:

```text
WORKER THREAD                 MAIN THREAD

answer = 84
thread terminates
      │                         worker.join() kết thúc
      └── termination detection ───────────► read answer
                                              in 84
```

Không cần `answer` volatile khi **đã chờ thread hoàn tất thành công** và không có writer khác cạnh tranh. `join()` cung cấp cả việc **chờ hoàn thành** (ở dạng không timeout) và **visibility guarantee** cho những writes của worker đã kết thúc.

### 7.1. `join` không làm phép tính của worker atomic khi đang chạy

Nếu hai workers cùng thực hiện `plainCounter++` không có lock/atomic, main gọi `join()` cho cả hai rồi mới đọc, main **thấy được kết quả cuối cùng thực sự đã được viết**, nhưng kết quả đó có thể **thiếu increments** do lost updates. HB từ mỗi worker tới main **không tạo mutual exclusion giữa hai workers**.

### 7.2. `join` có timeout không bảo đảm thread đã chết

```java
worker.join(100); // chờ tối đa khoảng 100 ms
if (!worker.isAlive()) {
    // Đã phát hiện worker kết thúc, có termination HB.
} else {
    // Worker có thể còn chạy; không được đọc plain fields
    // như thể worker chắc chắn đã hoàn tất.
}
```

Đây là snippet trong method; thời lượng chờ thực tế còn chịu scheduler và JVM. **Không được** suy vì `join(100)` return bình thường thì worker đã hoàn thành. Cần xác minh **termination**; gọi `isAlive()` và quan sát `false` phù hợp với quy tắc detection được JLS mô tả.

### 7.3. `InterruptedException` khi chờ

`join()` có thể ném `InterruptedException` khi waiting thread bị interrupt. Khi đó **không được tự coi như worker đã kết thúc**; cần quyết định retry, propagate, cancel hoặc khôi phục interrupt status theo contract ứng dụng. Nếu main bỏ qua exception rồi đọc plain state, không có cơ sở nói `join` đã cung cấp edge termination mà chương trình định dựa vào.

### 7.4. Join là một chiều ngược lại Start

- `start`: những actions **trước start ở starter** được publish tới **started thread**.
- `join`: những actions **trong child trước termination** được publish tới **joiner sau successful detection**.

Kết hợp hai quy tắc, có thể tạo chuỗi HB hai chiều cho một lần trao đổi dữ liệu, nhưng chúng không tạo live, bidirectional communication xuyên suốt vòng đời thread.

## 8. Ví dụ tổng hợp Java 17+: khóa, volatile, start và join

Tạo file duy nhất **`HappensBeforeDemo.java`**:

```java
public class HappensBeforeDemo {
    static final class Box {
        int value;
        String text;
    }

    static final class Publication {
        int payload;
        int checksum;
        int observed;
        volatile boolean ready;
    }

    static final class Counter {
        private final Object monitor = new Object();
        private int value;

        void increment() {
            synchronized (monitor) {
                value++;
            }
        }

        int get() {
            synchronized (monitor) {
                return value;
            }
        }
    }

    public static void main(String[] args)
            throws InterruptedException {

        // 1) Actions trước start HB actions ở started thread.
        Box fromMain = new Box();
        fromMain.value = 11;
        fromMain.text = "prepared";

        Thread starter = new Thread(() -> {
            if (fromMain.value != 11
                    || !"prepared".equals(fromMain.text)) {
                throw new AssertionError("start visibility");
            }
        });
        starter.start();
        starter.join();
        System.out.println("start: " + fromMain.value
                + " / " + fromMain.text);

        // 2) Actions của worker HB code sau join thành công.
        Box fromWorker = new Box();
        Thread producer = new Thread(() -> {
            fromWorker.value = 22;
            fromWorker.text = "finished";
        });
        producer.start();
        producer.join();
        System.out.println("join: " + fromWorker.value
                + " / " + fromWorker.text);

        // 3) Volatile flag publish hai plain fields.
        Publication publication = new Publication();

        Thread observer = new Thread(() -> {
            while (!publication.ready) {
                Thread.onSpinWait();
            }
            publication.observed =
                    publication.payload + publication.checksum;
        });
        Thread publisher = new Thread(() -> {
            publication.payload = 30;
            publication.checksum = 12;
            publication.ready = true;
        });

        observer.start();
        publisher.start();
        publisher.join();
        observer.join();
        System.out.println("volatile: " + publication.observed);

        // 4) Cùng monitor làm read-modify-write atomic
        // đối với hai writers phối hợp qua khóa.
        Counter counter = new Counter();
        Runnable incrementTask = () -> {
            for (int i = 0; i < 10_000; i++) {
                counter.increment();
            }
        };

        Thread first = new Thread(incrementTask);
        Thread second = new Thread(incrementTask);
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("lock count: " + counter.get());
        System.out.println("done");
    }
}
```

### Biên dịch/chạy

```bash
javac -encoding UTF-8 HappensBeforeDemo.java
java HappensBeforeDemo
```

**Standard output của chương trình khi các threads được scheduler cho chạy và hoàn tất bình thường:**

```text
start: 11 / prepared
join: 22 / finished
volatile: 42
lock count: 20000
done
```

**Lưu ý tiến độ:** như mọi ví dụ concurrency, Java không đưa ra cam kết fairness của OS scheduler hay deadline để một thread luôn được chạy trong X milliseconds. Vòng đợi volatile này mang tính giảng giải, không phải mẫu thiết kế production (xem mục 14). Khi các threads tiến hành và kết thúc bình thường, kết quả **không phụ thuộc thứ tự interleaving hợp lệ**.

### Giải thích tại sao output ổn định

1. Main đặt `fromMain.value=11` và `text="prepared"` **trước** `starter.start()`. Start HB lần đọc của child; child xác nhận giá trị đã publish. Main join trước khi in nên việc validate không còn đang chạy.
2. Worker ghi `fromWorker.value=22` và `text="finished"` rồi kết thúc. Main gọi `join()` hoàn tất trước khi đọc hai fields. Không cần volatile trong trường hợp này.
3. Publisher ghi **30** và **12** trước volatile write `ready=true`. Observer chờ tới khi một volatile read thấy `true`, rồi đọc hai plain fields. HB thông qua volatile bảo đảm tổng **42**, vì không có writer khác. Main `join()` observer để đọc `observed` sau khi observer kết thúc.
4. Hai writers cùng dùng `counter.increment()` khóa **cùng monitor**. Mỗi increment read-modify-write không thể interleave bên trong critical section, nên **20.000 increments** không bị mất. `join()` chỉ bảo đảm main đợi đủ hai writers; **lock** mới ngăn lost updates.
5. Mỗi phần được chạy lần lượt trong `main`, nên thứ tự các dòng println cũng ổn định.

### Một chi tiết dễ hiểu nhầm trong ví dụ

Ở phần volatile, main cũng gọi `join()`, và `join` có HB riêng tới main. **Điều chứng minh volatile publication nằm ở reader thread**, nơi observer chờ `ready` rồi đọc `payload/checksum`. Nếu bỏ volatile và chỉ dựa vào main join ở cuối, main vẫn có thể thấy writes của publisher sau join, **nhưng observer đọc payload trước đó không có cạnh đồng bộ cần thiết**. Đây là hai thời điểm/cặp reads khác nhau.

Trong production, thay busy-wait bằng `CountDownLatch`, `BlockingQueue`, `Future` hoặc cơ chế notification phù hợp khi không cần low-level spin.

## 9. Tại sao `volatile++` vẫn sai: minh họa lost update

```java
final class BrokenCounter {
    volatile int value;

    void increment() {
        value++;
    }
}
```

Nếu hai threads thực hiện `increment()` đồng thời:

```text
Ban đầu: value=5

Thread A           Thread B
read value=5       read value=5
add 1 → 6          add 1 → 6
write value=6      write value=6

Kỳ vọng hai increments: 7
Kết quả có thể: 6
```

Volatile read/write riêng lẻ có memory-consistency guarantees, nhưng không tạo một **atomic transaction** bao trùm cả ba bước. Một volatile write của A có thể được thứ tự cùng các volatile operations khác mà vẫn không bảo đảm `value++` của A và B tách biệt hoàn toàn.

### Cách 1: synchronized trên cùng monitor

```java
final class SynchronizedCounter {
    private int value;

    synchronized void increment() {
        value++;
    }

    synchronized int get() {
        return value;
    }
}
```

### Cách 2: AtomicInteger

```java
final class AtomicCounter {
    private final java.util.concurrent.atomic.AtomicInteger value =
            new java.util.concurrent.atomic.AtomicInteger();

    void increment() {
        value.incrementAndGet();
    }

    int get() {
        return value.get();
    }
}
```

`AtomicInteger.incrementAndGet()` dùng atomic read-modify-write theo contract API, với memory effects được định nghĩa. Đây là cách phù hợp cho concurrent counter không cần ràng buộc phức tạp giữa nhiều fields. Với thống kê tổng hợp chịu contention rất cao, `LongAdder` có thể là lựa chọn khác nhưng không phải phép thay thế tuyệt đối nếu cần đọc nhất quán mỗi thời điểm.

## 10. Không có HB giữa hai plain fields nếu chỉ “chờ lâu”

Ví dụ:

```java
final class UnsafeFlag {
    int result;
    boolean done;

    void produce() {
        result = 42;
        done = true;
    }

    int consume() {
        while (!done) {
            Thread.onSpinWait();
        }
        return result;
    }
}
```

Đây là class hợp lệ nhưng **thiếu đồng bộ**, không nên chạy như test yêu cầu chắc chắn kết thúc. `Thread.onSpinWait()` là **performance hint cho JVM/CPU**, không tạo happens-before và không làm `done` volatile.

Tương tự:

```java
Thread.sleep(100);
```

**Không phải memory synchronization primitive.** Lời gọi sleep có thể giúp thread khác được CPU chạy, nhưng JMM không định nghĩa “Thread.sleep(100) happens-before đọc plain field từ thread khác” chỉ vì đủ milliseconds.

### Println có thể che bug

`System.out.println()` trên những implementation thông dụng có synchronized I/O internals. Nếu bạn chèn println giữa hai accesses, nó có thể **thay timing hoặc tạo thêm synchronization ngoài ý định**; bug dễ biến mất khi thêm log.

Điều đúng: không dùng output có log như bằng chứng chương trình đã có HB an toàn. Hãy phân tích **protocol mà code thực sự dựa vào**, không các khóa vô tình của diagnostic library.

## 11. Sửa data-race flag đúng cách

### Cách A: volatile flag

```java
final class VolatileFlag {
    int result;
    volatile boolean done;

    void produce() {
        result = 42;
        done = true;
    }

    int consume() {
        while (!done) {
            Thread.onSpinWait();
        }
        return result;
    }
}
```

Với **một writer**, nếu reader nhận ra `done == true` và không có concurrent writes khác vào `result`, volatile HB làm `consume()` trả **42**. Nhưng vòng chờ tiêu thụ CPU, thiếu liveness guarantee về scheduling và không xử lý cancellation/timeouts; đây vẫn **không phải mô hình production mặc định**.

### Cách B: monitor condition

```java
final class MonitorFlag {
    private int result;
    private boolean done;

    synchronized void produce() {
        result = 42;
        done = true;
        notifyAll();
    }

    synchronized int consume() throws InterruptedException {
        while (!done) {
            wait();
        }
        return result;
    }
}
```

`wait()` phải được gọi khi thread giữ đúng monitor; nó release monitor trong thời gian chờ và phải reacquire trước khi return. `notifyAll()` không tự nhường khóa ngay lập tức: producer phải exit synchronized method/monitor, consumer mới có thể reacquire.

**Luôn dùng `while` để kiểm tra lại điều kiện**, do spurious wakeups và sự thay đổi state bởi threads khác. Cặp monitor unlock/lock cũng tạo HB cần thiết.

### Cách C: CountDownLatch

```java
import java.util.concurrent.CountDownLatch;

final class LatchFlag {
    private int result;
    private final CountDownLatch ready = new CountDownLatch(1);

    void produce() {
        result = 42;
        ready.countDown();
    }

    int consume() throws InterruptedException {
        ready.await();
        return result;
    }
}
```

Đây là một class runnable về mặt cú pháp nhưng không có `main`; nó dùng concurrency API cấp cao. Theo API, các actions trước `countDown()` happens-before actions sau một `await()` thành công tương ứng, nên `result` được publish an toàn.

**Không lẫn các cơ chế:** latch có memory-consistency effects riêng; không cần thêm volatile cho `result` chỉ vì nó được một thread khác đọc sau successful await (với điều kiện không có writes cạnh tranh khác).

## 12. Happens-before không có nghĩa “mọi plain read luôn thấy lần write cuối cùng theo đồng hồ”

JMM dùng quan hệ HB và những quy tắc hợp lệ để xác định reads có thể nhìn thấy writes nào. Với nhiều writers:

```java
int data; // shared

// Thread A:
data = 1;

// Thread B:
data = 2;

// Thread C:
int observed = data;
```

Đây là ví dụ khái niệm, không có synchronization mô tả giữa A, B, C. Không thể dùng wall-clock timestamp, việc một logger in trước hay giả định CPU chạy theo thứ tự để đưa ra kết quả portable của `observed`.

Ngay cả khi C có HB từ A, nếu B ghi `data` mà không được ordered hợp lệ với C, chương trình vẫn có conflict/data race khác có thể làm giá trị đọc không được quyết định bởi “A vừa publish 1”. **Cần xét mọi writes xung đột**, không chỉ một cạnh mong muốn.

### Cách suy luận một protocol đúng

1. Xác định **mọi shared variables**.
2. Xác định **mọi writes** vào chúng và thread nào có thể ghi.
3. Xác định action **publication/release**.
4. Xác định action **observation/acquire** thật sự xảy ra.
5. Nối program order + synchronizes-with thành HB path.
6. Kiểm tra có **competing unordered writes** nào phá tính quyết định không.
7. Kiểm tra atomicity nếu code đọc/sửa nhiều fields.
8. Kiểm tra thread có thật sự hoàn tất/chạy theo điều kiện liveness không.

## 13. Happens-before và memory reordering

Ví dụ kinh điển:

```java
int x = 0;
int y = 0;
int r1;
int r2;

// Thread A:
x = 1;
r1 = y;

// Thread B:
y = 1;
r2 = x;
```

Đây là pseudocode các actions trong class hai threads, **không phải chương trình runnable độc lập**. Khi hai threads thực thi không có HB liên kết hai accesses, **kết quả `r1 == 0 && r2 == 0` được JMM cho phép** trong bài toán store buffering điển hình. Điều đó không có nghĩa lần chạy nào cũng sẽ trả 0/0.

### Vì sao kết quả 0/0 không nhất thiết “CPU sai”?

Không có cross-thread HB bắt buộc Thread A phải thấy write của B trước read `y`, cũng không có cross-thread HB buộc B thấy write của A trước read `x`. JMM bảo vệ ngữ nghĩa intra-thread và các điều kiện hợp lệ, nhưng một data-racy execution không tự có sequential consistency.

**Sửa đúng** phụ thuộc yêu cầu logic:

- Nếu mỗi thread cần thấy một event từ thread kia, dùng volatile flags hoặc synchronization protocol.
- Nếu thao tác trao đổi trạng thái cần atomic, dùng lock hoặc atomic class hợp lệ.
- Nếu chỉ cần thu kết quả sau khi cả hai hoàn tất, caller `join()` hai threads và đọc lúc đó; nhưng join không làm các reads trong quá trình họ chạy vốn thiếu HB trở nên safe.

### Volatile làm khác bài toán

Đổi `x` và `y` thành volatile fields sẽ thêm synchronization order/visibility constraints. Với **cả hai biến volatile**, case 0/0 của pattern store-buffering trên không còn phù hợp với thứ tự volatile toàn phần nhất quán theo program order; nhưng còn các interleavings hợp lệ khác, và không nên dùng logic đó để suy một transaction atomic giữa nhiều fields.

## 14. Thread lifecycle và các bẫy tiến độ thực thi

HB nói **nếu một event quan sát hợp lệ xảy ra** thì thứ tự/visibility được bảo đảm. Nó **không cam kết** event sẽ xuất hiện trong 100 ms hay mọi thread được scheduler chạy công bằng.

### 14.1. Busy-wait có vấn đề gì?

```java
while (!ready) {
    Thread.onSpinWait();
}
```

Nếu `ready` volatile, vòng lặp có semantics visibility phù hợp khi writer publish. Nhưng nó có thể:

- Tiêu thụ CPU nếu chờ lâu.
- Không có timeout/cancellation.
- Chạy vô hạn nếu writer không bao giờ publish do lỗi logic.
- Bị chậm do CPU quota/OS scheduling.
- Không phù hợp cho hầu hết I/O-bound services.

Thường ưu tiên `CountDownLatch`, `BlockingQueue`, `Condition`, `CompletableFuture` hoặc blocking primitive phù hợp.

### 14.2. `join(timeout)` vs `join()`

`join()` không timeout sẽ chờ thread kết thúc trừ khi bị interrupt hoặc có lỗi hợp lệ khác. `join(timeout)` **có thể** trả về khi hết thời gian; cần kiểm tra termination trước khi dựa vào edge completion.

Ví dụ:

```java
worker.join(200);
if (worker.isAlive()) {
    // Timeout hoặc worker chưa kết thúc:
    // chưa được coi là đã nhận final state qua join.
} else {
    // Đã phát hiện worker terminated.
}
```

### 14.3. InterruptedException không đồng nghĩa child terminated

Thread gọi `join` hoặc `wait` có thể bị interrupt. Exception signaling có HB semantics riêng liên quan interrupt detection, nhưng **không** làm worker tự động kết thúc. Đừng nuốt exception rồi đọc plain fields như thể đã có successful join.

### 14.4. Deadlock dù có `synchronized`

`synchronized` tạo mutual exclusion và HB edges; nó **không bảo đảm** hệ thống tránh deadlocks:

```text
Thread A giữ lock1, chờ lock2.
Thread B giữ lock2, chờ lock1.
```

Đó là vòng chờ khóa. Quy tắc HB chỉ nói về thứ tự những actions đã có/có thể xảy ra; không tạo progress khi các threads đang deadlock.

## 15. Safe publication của object: không chỉ boolean flag

Một object có nhiều fields cần khởi tạo an toàn trước khi thread khác sử dụng:

```java
final class Config {
    final String host;
    final int port;

    Config(String host, int port) {
        this.host = host;
        this.port = port;
    }
}
```

**Final fields** có initialization safety rules riêng trong JMM nếu object được xây dựng đúng (không cho `this` escape trong constructor), nhưng không nên viện dẫn final fields để biện hộ một protocol truy cập mutable shared state không có đồng bộ.

### Public reference qua volatile

```java
final class Holder {
    private Config config; // plain field

    private volatile boolean ready;

    void publish() {
        config = new Config("localhost", 8080);
        ready = true;
    }

    Config await() {
        while (!ready) {
            Thread.onSpinWait();
        }
        return config;
    }
}
```

Đây là class ví dụ, có thể biên dịch nhưng không có `main`. Sau volatile read thấy `true`, reader có HB từ những actions trước volatile write, nên `config` đã được publish trước đó được nhìn thấy hợp lệ. Không nên biến ví dụ spin-wait này thành thư viện production; dùng primitive cấp cao nếu muốn blocking/cancellation.

### Volatile reference đơn giản hơn flag riêng

```java
final class VolatileConfig {
    private volatile Config config;

    void publish(Config next) {
        config = next;
    }

    Config snapshot() {
        return config;
    }
}
```

Với construction hoàn tất trước volatile write, một reader nhận được non-null `config` qua volatile read có **safe-publication** của state được chuẩn bị trước publish. Tuy nhiên, những **mutable fields được sửa tiếp sau khi publish** vẫn cần đồng bộ riêng.

### Escape khỏi constructor là vấn đề khác

Nếu một constructor publish `this` trước khi khởi tạo hoàn chỉnh, thread khác có thể quan sát object dở dang; **final-field semantics cũng có hạn chế trong tình huống this escape**. Đừng giải quyết bằng việc chỉ thêm `final` mọi nơi mà vẫn publish object quá sớm.

## 16. Double-checked locking và volatile

Mẫu lazy singleton truyền thống:

```java
final class LazyService {
    private static volatile LazyService instance;

    private LazyService() {
    }

    static LazyService get() {
        LazyService local = instance;
        if (local == null) {
            synchronized (LazyService.class) {
                local = instance;
                if (local == null) {
                    local = new LazyService();
                    instance = local;
                }
            }
        }
        return local;
    }
}
```

**Đây là class hợp lệ theo Java 17.** Trong mẫu double-checked locking này, volatile reference đóng vai trò **safe publication** ở fast path không khóa.

Vì sao cần `volatile`?

- Constructor hoàn tất trước volatile write `instance = local` theo program order liên quan.
- Reader đọc non-null `instance` qua volatile read có HB tới những writes đã publish.
- Lock trong slow path bảo vệ tránh nhiều threads cùng tạo instance một cách không kiểm soát.
- Đọc lần đầu vào biến local làm logic rõ, chỉ dùng instance đã đọc được; second check dưới lock ngăn duplicate construction.

**Không có nghĩa** mọi singleton cần DCL: **initialization-on-demand holder idiom** hoặc static initialization thường đơn giản hơn. Và nếu `LazyService` có mutable fields được threads cập nhật sau construction, singleton publication không tự giải quyết races trên các fields ấy.

## 17. ReentrantLock và concurrency utilities tạo memory-consistency edges

Nhiều API trong `java.util.concurrent` cung cấp effects tương tự hoặc xây trên JMM:

| API | Contract memory consistency ở mức khái quát |
| --- | --- |
| `ReentrantLock` / `Lock` | Successful `lock/unlock` có memory synchronization semantics như monitor lock/unlock phù hợp |
| `CountDownLatch` | Actions trước `countDown()` HB actions sau `await()` thành công tương ứng |
| `Future.get()` | Actions thực hiện bởi asynchronous computation HB actions sau successful `get()` phù hợp |
| `ExecutorService` | Actions trước submit task HB actions trong task; task actions HB sau `Future.get` |
| `BlockingQueue` | Actions trước đặt object vào queue HB actions sau khi consumer lấy object tương ứng |
| `ConcurrentHashMap` | Các API có memory effects được document; visibility cho những access patterns cụ thể |
| Atomic classes | Atomic operations và memory effects theo contract (`get`/`set`/CAS...) |

**Luôn đọc contract cụ thể của API**, vì có các method thất bại/timeout/cancel và những access modes yếu hơn như `lazySet`, `getAcquire`, `setRelease`, `getOpaque` trong `VarHandle`/`Atomic*` APIs. Không có câu “tất cả method trên concurrent classes tạo một global happens-before”.

### Lock API so với synchronized

```java
import java.util.concurrent.locks.ReentrantLock;

final class LockCounter {
    private final ReentrantLock lock = new ReentrantLock();
    private int value;

    void increment() {
        lock.lock();
        try {
            value++;
        } finally {
            lock.unlock();
        }
    }

    int snapshot() {
        lock.lock();
        try {
            return value;
        } finally {
            lock.unlock();
        }
    }
}
```

Đây là class không có `main` nhưng có thể biên dịch. `try/finally` quan trọng để luôn unlock. Giao thức đúng đòi cả writes/reads truy cập `value` phối hợp cùng một lock; `ReentrantLock` cho thêm khả năng như `tryLock`/`lockInterruptibly` nhưng không “tự tốt hơn” `synchronized` về mọi mặt.

## 18. `wait/notifyAll` không chỉ là “gửi thông báo”

Nhiều người học viết:

```java
synchronized (monitor) {
    monitor.notifyAll();
}
```

và tưởng thread chờ ngay lập tức chạy tiếp. Thực tế:

1. Thread gọi `wait()` phải sở hữu monitor.
2. `wait()` release monitor và chờ theo luật JMM/`Object.wait`.
3. Thread gọi `notify/notifyAll` cũng phải sở hữu monitor.
4. Notification làm một hoặc nhiều waiters **đủ điều kiện thức dậy**, nhưng họ cần **reacquire monitor**.
5. Producer có thể vẫn đang giữ monitor sau notify cho tới khi thoát synchronized block.
6. Waiter phải kiểm tra lại condition trong **vòng `while`**, vì spurious wakeup hoặc state thay đổi.

**Không dựa vào notify mà thiếu condition variable/state**. Notification có thể được gửi trước khi thread bắt đầu wait, dẫn đến mất tín hiệu nếu protocol không giữ state đúng. Với một flag đọc/ghi dưới cùng monitor, cặp unlock/lock và condition loop làm giao thức vững hơn.

## 19. Rủi ro race vẫn còn nếu đồng bộ sai phạm vi

### 19.1. Hai khóa khác nhau

```java
final Object lockA = new Object();
final Object lockB = new Object();
int state;

// Writer:
synchronized (lockA) {
    state = 123;
}

// Reader:
synchronized (lockB) {
    System.out.println(state);
}
```

Đây là snippet minh họa trong class. `unlock(lockA)` **không** synchronizes-with `lock(lockB)` chỉ vì cả hai đều là monitors. Nếu không có HB khác, accesses `state` có thể racy.

### 19.2. Một field volatile, nhiều fields cần invariant

```java
volatile int left;
volatile int right;
```

Nếu writer cập nhật hai fields và reader cần thấy chúng như **một cặp nguyên tử**, volatile **không đủ**. Reader có thể quan sát pair gồm một write mới và một write cũ tại một thời điểm interleaving hợp lệ. Dùng monitor hoặc publish **immutable snapshot** qua một volatile reference khi cần giữ invariant nhiều fields.

### 19.3. CAS bảo vệ một địa điểm, không tự làm toàn object graph atomic

`AtomicReference.compareAndSet(expected, update)` có tính nguyên tử cho reference update theo API, nhưng không biến những writes bất đồng bộ tới fields của object đang được tham chiếu thành một transaction. Thiết kế concurrent state nên phân biệt **atomic publication of reference** với **atomic mutation of contents**.

### 19.4. Locking quanh writer nhưng reader không theo protocol

Writer có thể khóa đúng; reader đọc field trực tiếp mà không khóa và không volatile, vẫn thiếu guarantee dự kiến. Phải vẽ đủ **release-to-acquire chain cho read được hỏi**, không chỉ đánh dấu một chỗ có keyword `synchronized`.

## 20. Một công thức suy luận HB rõ ràng

Khi nhận một đoạn Java concurrency, đừng đoán theo thời gian. Hãy tạo bảng actions:

| Thread | Action | Loại |
| --- | --- | --- |
| T1 | `x = 7` | Plain write |
| T1 | `ready = true` | Volatile write |
| T2 | `if (ready)` | Volatile read |
| T2 | `r = x` | Plain read |

Giả sử T2 thật sự thấy `ready == true` do publication từ T1 (và không có writer khác làm nhiễu):

```text
T1: write x = 7
       │ program order
       ▼
T1: volatile write ready = true
       │ synchronizes-with
       ▼
T2: volatile read ready == true
       │ program order
       ▼
T2: plain read x

Kết luận: hb(write x, read x).
Nếu không có conflicting write khác: r == 7.
```

### Checklist suy luận từng bước

1. Liệt kê **đối tượng và các biến** shared.
2. Xác định **thread nào ghi**, **thread nào đọc**, thời điểm logic.
3. Tìm cạnh **program order** ở từng thread.
4. Tìm cạnh **synchronizes-with**: cùng monitor, cùng volatile variable, `start`, termination detection.
5. Ghép cạnh theo **transitivity**.
6. Kiểm tra **mọi conflicting accesses**: còn một unordered writer nào không?
7. Xác định thứ đang bảo đảm: **visibility**, **ordering**, **atomicity** hay **progress**?
8. Với vòng chờ, kiểm tra **liveness**, interrupt, timeout, cancellation.
9. Không diễn giải kết quả logging hoặc `sleep` thành cạnh JMM không tồn tại.
10. Nếu kết quả phức tạp, dùng stress testing và đối chiếu JLS chính thức.

## 21. JCStress và testing: điều gì có thể kiểm tra được?

**JCStress** là concurrency testing harness của OpenJDK chuyên thử các outcomes của concurrent actors; nó hữu ích khi học JMM litmus tests hoặc tìm race conditions rất nhỏ. Tuy nhiên:

- Một outcome không xuất hiện sau hàng triệu iterations **không chứng minh** JMM cấm nó.
- Một outcome xuất hiện có thể là tín hiệu bug về synchronization/protocol, phải kiểm tra định nghĩa test.
- VM flags, JIT warm-up, OS/CPU/architecture và test structure có thể thay tập outcomes quan sát.
- Không viết unit test phụ thuộc “data race chắc chắn làm sai sau N lần”.
- Không thêm println hay sleep vào critical path rồi coi như vẫn đang đo cùng litmus test.

**Thí nghiệm sai phổ biến:** assert một plain-field spinning loop phải thoát trong 100 ms. Việc nó thoát một lần có thể do timing hoặc compiled code chưa tối ưu, không tạo portability guarantee.

### ThreadSanitizer, profilers và runtime tools

Các công cụ diagnostic/profiler có thể giúp tìm contention, thread states hoặc dữ liệu race tùy JDK/build. Nhưng **JFR, `jstack`, `jcmd Thread.print`** chủ yếu cung cấp góc nhìn runtime, **không thay phân tích JMM**. Race detection support và false positives/negatives phụ thuộc tool; dùng đúng công cụ được hỗ trợ trên environment đang chạy.

## 22. Những nhầm lẫn thường gặp

| Nhầm lẫn | Sự thật |
| --- | --- |
| “Happens-before nghĩa là action A chạy sớm hơn theo đồng hồ.” | Là quan hệ JMM về ordering/visibility, không phải timeline vật lý |
| “Hai actions thuộc hai threads bất kỳ luôn được HB xếp thứ tự.” | HB là partial order; có thể không có cạnh |
| “`Thread.sleep` đủ để publish plain fields.” | Sleep không tạo HB edge |
| “`Thread.onSpinWait` làm field thành volatile.” | Chỉ là hint, không thêm JMM synchronization |
| “`volatile` làm `counter++` atomic.” | Read-modify-write vẫn có thể lost updates |
| “Volatile flag publish chỉ bản thân flag.” | Những writes trước release có thể được publish qua transitivity |
| “Đặt volatile vào array reference khiến mọi phần tử volatile.” | Không; elements cần protocol riêng |
| “Write vào một volatile var HB read của mọi volatile var.” | Quy tắc sw cần **cùng volatile variable** |
| “`synchronized(lockA)` đồng bộ với `synchronized(lockB)`.” | Khác monitor không tạo unlock/lock edge tương ứng |
| “Chỉ writer dùng synchronized cũng đủ.” | Reader phải theo protocol hoặc có HB hợp lệ khác |
| “`start()` publish mọi writes của starter kể cả sau start.” | Start edge bảo đảm writes trước start; sau start cần cơ chế riêng |
| “`new Thread` đồng nghĩa thread đã chạy.” | Phải gọi `start()` |
| “Gọi `run()` trực tiếp tạo thread mới.” | Không, chỉ là method call thông thường |
| “`join(timeout)` return nghĩa child đã chết.” | Có thể timeout; cần phát hiện termination |
| “`join` bảo vệ hai workers khỏi lost increments.” | Join chỉ ordering với joiner sau completion; không serialize workers |
| “Reader thấy `ready=true` dù ready plain thì chắc chắn thấy payload mới.” | Thiếu HB, không portable |
| “`notifyAll` lập tức chuyển quyền monitor cho reader.” | Waiter cần reacquire monitor sau unlock của notifier |
| “`volatile` bảo vệ invariant nhiều fields.” | Không như một transaction; dùng lock/immutable snapshot |
| “Final field đồng nghĩa object luôn published an toàn trong mọi construction.” | Có rules riêng và giới hạn khi `this` escape |
| “Đã thấy đúng output trên x86 là race-free.” | Outcome thường gặp không chứng minh JMM guarantee |
| “HB có thể sửa một chương trình sai về liveness/fairness.” | Ordering không bảo đảm scheduler tiến triển |
| “Có GC/JIT logs giúp suy ra tất cả HB edges.” | Logs không định nghĩa program synchronization protocol |

## 23. Bài tập có đáp án

**Bài 1.** Thread A viết `data=42`, rồi `ready=true` (volatile). Thread B đọc `ready==true` rồi đọc `data`. Một writer, không update khác. B thấy gì?  
**Đáp án:** `42`, vì program order + volatile synchronizes-with + transitivity tạo HB.

**Bài 2.** Writer viết `ready=true` trước rồi mới `data=42`. Reader thấy `ready==true`. Có chắc `data==42`?  
**Đáp án:** Không. Write payload nằm sau volatile publication.

**Bài 3.** Writer dùng `synchronized(lockA)`, reader dùng `synchronized(lockB)`. Có HB unlock/lock không?  
**Đáp án:** Không nếu hai monitors khác và không có cạnh đồng bộ khác.

**Bài 4.** Hai threads cùng `volatileCounter++` 1000 lần, main join cả hai. Kết quả luôn 2000?  
**Đáp án:** Không. Lost updates có thể xảy ra; `join` chỉ chờ và publish kết quả thực tế, không làm increments atomic.

**Bài 5.** `AtomicInteger.incrementAndGet()` có giúp không?  
**Đáp án:** Có, nó cung cấp atomic increment theo contract Java concurrency API.

**Bài 6.** Main ghi `x=5` rồi `child.start()`; child in `x`. Có cần x volatile khi không có writer cạnh tranh?  
**Đáp án:** Không, start HB child actions.

**Bài 7.** Main ghi `x=5` **sau** `child.start()`; child đọc plain `x`. `start` tự publish write này?  
**Đáp án:** Không. Write sau start thiếu edge start HB; cần đồng bộ phù hợp.

**Bài 8.** Worker ghi `result=10`; main gọi `worker.join()` hoàn tất rồi đọc. Có cần result volatile nếu không writer khác?  
**Đáp án:** Không, actions của terminated thread HB code sau successful join.

**Bài 9.** `join(100)` return mà `worker.isAlive()==true`; main có thể coi worker đã publish kết quả cuối cùng?  
**Đáp án:** Không. Worker chưa được phát hiện kết thúc.

**Bài 10.** `join()` ném `InterruptedException`. Có thể bỏ qua và đọc state như đã join thành công?  
**Đáp án:** Không. Chưa có successful termination detection theo logic đang dựa vào.

**Bài 11.** Một thread gọi `run()` trực tiếp trên Thread object. Nó nhận start HB edge?  
**Đáp án:** Không có new-thread start event; đó là lời gọi method thông thường.

**Bài 12.** Field `volatile int[] arr` làm `arr[0]++` atomic?  
**Đáp án:** Không. Volatile áp dụng reference field, không tự áp dụng array elements hoặc composite update.

**Bài 13.** `wait()` có thể return mà condition vẫn false?  
**Đáp án:** Có, vì spurious wakeups hoặc state thay đổi; dùng `while (!condition) wait()`.

**Bài 14.** `notifyAll()` làm waiter chạy ngay lập tức trước khi notifier unlock?  
**Đáp án:** Không. Waiter còn cần reacquire monitor và scheduler tiến hành.

**Bài 15.** Mỗi lần chạy data race test đều in đúng. Có chứng minh đúng JMM?  
**Đáp án:** Không. Cần phân tích HB hoặc kiểm thử stress chỉ để tìm lỗi, không để chứng minh absence of errors.

**Bài 16.** `volatile` cung cấp mutual exclusion như `synchronized`?  
**Đáp án:** Không. Nó có ordering/visibility semantics, không khóa critical section.

**Bài 17.** Cùng monitor bảo vệ writer/reader nhưng reader đọc ngoài lock trong một đường khác. Đường đó an toàn tự động?  
**Đáp án:** Không; cần xét HB với read thực tế.

**Bài 18.** Một object immutable gồm final fields được publish qua volatile reference. Có thể đọc state đã construct sau acquire read?  
**Đáp án:** Có với construction và publication hợp lệ; mutable fields cập nhật sau đó vẫn cần protocol riêng.

**Bài 19.** DCL singleton không có volatile có an toàn theo mô hình Java 17?  
**Đáp án:** Mẫu double-checked locking cổ điển không volatile là không an toàn về publication; dùng volatile hoặc phương án class initialization.

**Bài 20.** Trong `HappensBeforeDemo`, dòng `volatile:` in gì?  
**Đáp án:** `volatile: 42` nếu các threads hoàn tất bình thường; volatile ready đọc true publish 30 và 12.

**Bài 21.** Trong `HappensBeforeDemo`, dòng `lock count:` in gì?  
**Đáp án:** `lock count: 20000` do hai writers mỗi người tăng 10.000 lần dưới cùng monitor.

**Bài 22.** Thread A và B ghi plain `x=1`/`x=2` song song, C đọc không synchronization. Có thể dùng “write nào chạy cuối theo log” để kết luận C thấy gì?  
**Đáp án:** Không; không có cross-thread HB đủ rõ, logging có thể thay timing/protocol.

**Bài 23.** Writer ghi data rồi `CountDownLatch.countDown()`; reader `await()` thành công rồi đọc data. Có HB?  
**Đáp án:** Có, theo memory-consistency contract của CountDownLatch.

**Bài 24.** `Future.get()` trả kết quả task, có phải cần đọc thêm plain ready flag để đảm bảo thấy writes từ task không?  
**Đáp án:** Thường không; Future API có memory-consistency guarantee cho actions của asynchronous computation trước actions sau successful get, theo contract của Future.

## 24. Checklist trước khi kết luận một thiết kế thread-safe

- [ ] Liệt kê các shared mutable fields và tất cả readers/writers.
- [ ] Có **data races** nào không? Nếu có, đã xác định tại sao outcome vẫn hợp lệ?
- [ ] Nếu dùng monitor, mọi critical accesses có khóa **cùng object** không?
- [ ] Nếu dùng volatile publication, writes có đứng **trước** release write và reads sau acquire read không?
- [ ] Có writer cạnh tranh hoặc post-publication mutations làm mất tính quyết định không?
- [ ] Có composite operations như `++`, check-then-act, read-modify-write cần atomicity không?
- [ ] `Thread.start()` đã được gọi thật hay chỉ gọi `run()`?
- [ ] Phép đọc từ child có phụ thuộc đúng actions **trước start** không?
- [ ] Phép đọc từ joiner có diễn ra **sau termination detection thành công** không?
- [ ] Có dùng timed join và kiểm tra child thực sự kết thúc không?
- [ ] Có xử lý `InterruptedException` đúng contract ứng dụng không?
- [ ] Waiter có kiểm tra condition bằng `while` dưới cùng monitor không?
- [ ] Có bounded wait, timeout/cancellation thay vì infinite spin khi phù hợp không?
- [ ] Có dùng utilities cấp cao để tránh tự chế signaling protocol khi không cần?
- [ ] Có phân biệt visibility/ordering với mutual exclusion/atomicity?
- [ ] Có tránh dùng println, sleep hoặc debugger timing làm bằng chứng đúng JMM?
- [ ] Có hiểu final-field construction rules và nguy cơ `this` escape không?
- [ ] Có test correctness theo specification, không chỉ tin vào một stress outcome?
- [ ] Có dùng JLS §17.4.5 làm chuẩn khi tranh luận về HB edge không?

## 25. Tổng kết: bốn đường tạo HB phải nhớ

```text
1) MONITOR
   T1: writes → unlock(m)  ---HB---> lock(m) → reads :T2

2) VOLATILE
   T1: writes → write(v)   ---HB---> read(v) → reads :T2
                 volatile              volatile

3) THREAD.START
   T1: writes → t.start()  ---HB---> actions in thread t

4) THREAD.JOIN / TERMINATION DETECTION
   Thread t: writes → termination  ---HB---> joiner
                                           sau successful join

HB có tính bắc cầu với program order.
Không có HB ≠ chắc chắn thấy giá trị cũ, nhưng
cũng không có bảo đảm visibility như mong muốn.
```

**Happens-before là công cụ chứng minh**, không phải mẹo khiến CPU “flush cache”. Với khóa, volatile, start và join, câu hỏi đúng luôn là **“action nào đồng bộ với action nào, và transitivity đưa writes tới reads nào?”** Khi đã vẽ được đường HB, bạn mới có cơ sở nói về visibility; sau đó vẫn phải kiểm tra atomicity, writers cạnh tranh, progress và resource lifecycle.

### Tài liệu chuẩn để đối chiếu

1. **Java Language Specification, Java SE 17, Chapter 17 — Threads and Locks:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html
2. **JLS §17.4.4 — Synchronization Order:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.4
3. **JLS §17.4.5 — Happens-before Order:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5
4. **Java SE 17 — Thread API, start/join/isAlive and interruptions:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
5. **Java SE 17 — java.util.concurrent package memory-consistency effects:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html
6. **Java SE 17 — CountDownLatch and Future API:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html ; https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html
7. **Java SE 17 — AtomicInteger:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html
8. **OpenJDK JCStress project:** https://github.com/openjdk/jcstress

**Bài học cốt lõi:** `synchronized` tạo HB qua **cùng monitor** và cung cấp mutual exclusion; `volatile` tạo HB qua **cùng biến** nhưng không làm compound operation nguyên tử; `Thread.start()` công bố writes của starter tới child; `Thread.join()` công bố writes của child đã kết thúc tới joiner. Dùng **program order + synchronizes-with + transitivity** để chứng minh, thay vì hy vọng theo timing hay cache behavior.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-threads-and-synchronization-009">Quy tắc trường final, hoàn thành khởi tạo và công bố đối tượng an toàn</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc trường final, hoàn thành khởi tạo và công bố đối tượng an toàn trong Java Memory Model

## Mục tiêu bài học

Một luồng tạo object, gán các fields trong constructor rồi đưa reference của object đó cho luồng khác. Có phải luồng đọc **chắc chắn thấy mọi field đã được gán**? Không thể trả lời chỉ bằng câu “constructor đã chạy xong”. Trong Java Memory Model (JMM), việc luồng đọc có được bảo đảm nhìn thấy các writes của luồng tạo phụ thuộc **happens-before**, hình thức **publication**, và **quy tắc đặc biệt dành cho final fields**. Nếu reference `this` lọt ra ngoài trước khi constructor kết thúc, ngay cả bảo đảm đặc biệt của `final` cũng có thể bị phá vỡ.

Bài học này tập trung vào ba câu hỏi khác nhau:

1. **Hoàn thành khởi tạo:** constructor đã thiết lập invariants và hoàn tất đúng cách trước khi object được quan sát từ thread khác hay chưa?
2. **Công bố reference:** thread khác nhận được reference bằng cơ chế nào: racy ordinary field, `volatile`, synchronized, class initialization, thread start, concurrent collection hay Future?
3. **Sử dụng sau công bố:** object có thực sự bất biến không? Nếu vẫn mutable, các writes/reads diễn ra về sau được đồng bộ hóa bằng gì?

Sau bài này, bạn có thể:

- Diễn giải chính xác §17.4 và §17.5 của **Java Language Specification**, đặc biệt `happens-before`, `synchronizes-with`, `freeze action` và khái niệm **properly constructed**.
- Phân biệt `final`, `volatile` và `synchronized`; nhận ra **final-field initialization safety khác full safe publication**.
- Tránh `this`-escape qua static field, registry, listener, callback, thread được start trong constructor hoặc overridable method.
- Hiểu vì sao `final List<T>` hoặc `final byte[]` không khiến các elements trở thành bất biến; biết dùng defensive copy và snapshot.
- Công bố một object an toàn bằng `volatile`, monitor lock, `AtomicReference`, class initialization, `Thread.start()`, `BlockingQueue`, `Future.get()` hoặc công cụ concurrent phù hợp.
- Viết đúng **double-checked locking** với `volatile`, hoặc chọn initialization-on-demand holder khi phù hợp.
- Kiểm tra code Java 17+ chạy được, phân biệt **stdout xác định** của ví dụ đã đồng bộ với **kết quả không xác định** của chương trình cố ý chứa data race.
- Sử dụng code review, stress testing và OpenJDK **jcstress** để nghiên cứu memory ordering, không dựa vào `Thread.sleep()` làm phép thử correctness.

## 1. Một object được tạo như thế nào?

Khi chạy:

~~~java
Profile p = new Profile("Lan", 20);
~~~

đây là **snippet minh họa** yêu cầu class `Profile` thích hợp. HotSpot/JVM phải thực hiện đúng Java semantics, thường liên quan:

1. Cấp phát hoặc biểu diễn object với các instance fields ban đầu có **default values** theo JLS, ví dụ `0`, `false`, `null`.
2. Thực thi các phần khởi tạo instance fields/instance initializers và constructor chain theo thứ tự Java quy định.
3. Thực thi statements trong constructor để gán các fields và thiết lập invariants.
4. Constructor hoàn thành bình thường, sau đó biểu thức `new Profile(...)` trả reference cho caller.

Ở mức implementation, JIT có thể tối ưu mạnh, chẳng hạn **escape analysis/scalar replacement** loại physical allocation trong trường hợp hợp lệ. Tuy nhiên việc tối ưu phải bảo toàn behavior JMM, kể cả bảo đảm đặc biệt của `final`.

**Hai việc không được gộp làm một:**
- Constructor **hoàn tất** trong thread A.
- Thread B **được bảo đảm thấy các writes** mà thread A đã thực hiện.

Nếu A viết reference vào ordinary shared field rồi B đọc mà không có synchronization, có thể có **data race**. Dù code trên A chạy đúng thứ tự source, Java không mặc định cung cấp một edge happens-before từ writes ở A tới reads ở B chỉ vì thời gian đồng hồ ở B đến sau.

## 2. Happens-before, program order và synchronizes-with

Theo JLS §17.4.5, nếu action X **happens-before** action Y, X được ordered/visible với Y theo JMM. Đây là quan hệ được xây dựng từ program order trong một thread và các edges **synchronizes-with**, cùng tính bắc cầu phù hợp.

Các edges thường dùng:

| Sự kiện | Bảo đảm trong JMM |
| --- | --- |
| Program order trong một thread | Action trước happens-before action sau trong cùng thread |
| Monitor `unlock` rồi `lock` kế tiếp cùng monitor | Unlock synchronizes-with subsequent lock |
| `volatile write` rồi subsequent `volatile read` cùng variable | Write synchronizes-with read theo volatile synchronization order |
| `Thread.start()` | Những actions trước start happens-before actions trong started thread |
| Thread termination được phát hiện bằng `join()` thành công | Actions trong worker happens-before actions sau join ở thread chờ |
| `java.util.concurrent` APIs | Có thêm memory consistency effects tùy class/method contract |
| Class initialization | JVM thực hiện synchronization cần thiết cho initialization safety khi class được sử dụng |

Ví dụ về volatile publication:

~~~text
THREAD A (writer)                       THREAD B (reader)

obj = new Config(...);
  constructor writes
shared = obj;                 <---      Config c = shared;
  volatile write                         volatile read
                                            |
                                            v
                                        reads c.fields

                 happens-before chain
          constructor writes -> volatile write
            -> volatile read -> field reads
~~~

Sơ đồ đòi hỏi `shared` được khai báo **volatile**, reader thực sự đọc variable đó và quan sát reference đã công bố theo đúng sequence. Không phải chỉ có keyword volatile ở một field không liên quan là mọi object/field trong chương trình đều an toàn.

### Happens-before không chỉ là “đến trước theo thời gian”

Nếu hai threads cùng truy cập một ordinary field không có ordering phù hợp, việc luồng A chạy trước theo wall-clock không tạo happens-before. OS cache coherence, CPU scheduling và những lần test in đúng giá trị **không phải bằng chứng** rằng publication đã có guarantee JMM.

Ngược lại, nếu được đồng bộ đúng, JVM/JIT và hardware có thể reorder tối ưu bên dưới nhưng **không được phá** Java-level observable guarantees.

### Data race và visibility

Ví dụ:

~~~java
final class Data {
    int a;
    int b;
}

static Data shared; // ordinary, không volatile
~~~

Đây là class/field snippets. Writer có thể chạy `shared = new Data(); shared.a=1; shared.b=2;` và reader chạy `Data d=shared; if(d!=null) print(d.a,d.b);` mà không có HB. Reader có thể nhìn thấy reference cùng các field values không như mong đợi, hoặc gặp lỗi do timing. Hơn nữa fields được ghi **sau khi published** càng không được bảo vệ bởi bất kỳ final-constructor guarantee nào.

**Không viết benchmark tự đoán một output cố định cho data race.** Một lần chạy luôn in 1,2 không chứng minh nó được bảo đảm.

## 3. final field: bảo đảm đặc biệt từ JLS §17.5

Ví dụ class:

~~~java
final class Point {
    final int x;
    final int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
~~~

Nếu `Point` được **properly constructed** — đặc biệt reference tới object **không được làm visible cho thread khác trước khi constructor hoàn tất** — JMM có những bảo đảm đặc biệt cho reads của `final` fields, **ngay cả khi object reference được truyền qua một racy ordinary field**.

JLS mô tả `final` fields:
- Khởi tạo một lần theo cách thông thường, compiler được phép tối ưu reads mạnh hơn một ordinary mutable field.
- Có một **freeze action** liên quan tới final field khi constructor ghi field đó **kết thúc**.
- Thread đọc một reference tới object đã hoàn tất khởi tạo được đảm bảo nhìn thấy **giá trị đúng của final fields** theo final-field semantics.
- Với object/array được tham chiếu bởi final field, các writes được thực hiện thích hợp trước freeze có một bảo đảm nhìn thấy đặc biệt **qua đường dereference đó**.

**Đây không phải full happens-before edge tổng quát từ constructor tới mọi arbitrary field read.** JLS quy định cơ chế **dereference chain** và **memory chain** dành cho final fields; HB đặc biệt được dùng trong một phép xác định giá trị nhất định, không được áp tính bắc cầu tùy tiện như một synchronization edge toàn cục.

### Minh họa mẫu trong JLS

~~~java
final class Example {
    final int finalized;
    int ordinary;

    Example() {
        finalized = 42;
        ordinary = 17;
    }
}
~~~

Nếu hoàn tất constructor, rồi reference được công bố **racy**:

~~~java
static Example shared; // ordinary field, ví dụ minh họa
// writer:
shared = new Example();
// reader ở thread khác:
Example e = shared;
if (e != null) {
    int a = e.finalized;
    int b = e.ordinary;
}
~~~

**Diễn giải thận trọng:**
- Nếu reader đã thấy reference tới instance được khởi tạo đúng cách, final-field semantics bảo đảm `a == 42`.
- Không có một ordinary HB từ writer sang reader, nên **không có bảo đảm tương đương cho `b`**; có thể quan sát default `0` hoặc `17` trong những execution phù hợp JMM.
- Không có guarantee reader sẽ **sớm hoặc luôn thấy `shared != null`**, vì reference truyền qua data race.
- Nếu constructor có `this`-escape trước khi gán `finalized`, không được sử dụng kết luận trên.

**Khuyến nghị kỹ thuật:** dù `final` có initialization safety đặc biệt, **hãy dùng safe publication cho toàn bộ object** để readers có happens-before rõ ràng, thay vì cố tình dựa vào racy publication.

## 4. Freeze action là gì? Không phải đóng băng toàn bộ heap

Một mô hình:

~~~text
Thread A tạo Object O

ALLOCATE O (default fields)
        |
        v
constructor:
    write final x = 42
    write ordinary y = 17
        |
        v
constructor exit  ---> FREEZE of final x
        |
        v
O được đưa tới nơi thread khác có thể thấy
        |
        v
Thread B đọc O.x qua reference phù hợp
    -> final field được bảo vệ bởi JMM 17.5
~~~

**Freeze là khái niệm của Java Memory Model**, không phải gọi một public API hoặc buộc HotSpot khóa mọi CPU cache lines. JLS 17.5.1 quy định freeze khi constructor có write tới final field kết thúc (kể cả các chi tiết constructor delegation; specification còn xét trường hợp abrupt completion). Đừng tự khái quát freeze thành “mọi fields trở thành volatile”.

### Nếu constructor gọi constructor khác

~~~java
final class Pair {
    final int a;
    final int b;

    Pair(int a) {
        this(a, 0);
    }

    Pair(int a, int b) {
        this.a = a;
        this.b = b;
    }
}
~~~

Constructor delegation với `this(...)` có quy tắc freeze gắn với constructor thực hiện writes. Khi thiết kế class, vẫn cần **không để `this` escape trong toàn bộ construction path**, bởi một thread khác có thể thấy object trước khi initialization toàn bộ hierarchy hoàn tất.

### final field được gán ở declaration có khác không?

~~~java
final class Status {
    final int code = 200;

    Status() {
        // Có thể thực hiện thêm khởi tạo.
    }
}
~~~

Đây là **instance final constant-variable-like expression** nhưng điều quan trọng của bài là final field initialization semantics khi object được constructed properly. Compiler có thể inline literal rất sớm; không dùng `Status.code==200` trong mọi test để kết luận publication an toàn cho những mutable fields khác.

### final fields bị sửa qua reflection/Unsafe

JLS §17.5.3 có các tình huống đặc biệt của deserialization/reflection, nhưng code ứng dụng bình thường **không được dựa vào việc thay đổi final field sau construction**. Những thay đổi đó có thể có visibility/optimization surprises; strong encapsulation, records và các JDK mới tiếp tục hạn chế deep reflection hoặc unsafe mutation.

Tư duy an toàn: coi final fields như **chỉ được thiết lập trong constructor/instance initializer**; không dùng reflection để đổi chúng sau publication.

## 5. Properly constructed: vì sao `this` không được escape?

Một object có thể lọt ra khỏi constructor thông qua:
- Gán `this` vào static/shared field.
- Đăng ký `this` làm listener/callback ở registry bên ngoài, nơi thread khác có thể chạy callback tức thì.
- Start một thread trong constructor rồi Runnable capture `this`.
- Submit task cho executor trong constructor, task tham chiếu tới fields của object đang tạo.
- Đưa `this` vào một concurrent collection trước khi constructor hoàn tất.
- Gọi overridable instance method từ constructor, subclass override publish object hoặc đọc fields chưa khởi tạo.
- Gọi method của bên thứ ba và truyền `this` khi không kiểm soát việc method ấy làm gì.

### Anti-pattern: đăng ký sớm

~~~java
final class UnsafeListener {
    final int id;

    UnsafeListener(Registry registry) {
        registry.register(this); // this có thể bị thread khác thấy
        id = 42;
    }
}
~~~

Đây là **snippet minh họa**, cần định nghĩa `Registry` phù hợp. Nếu registry làm object visible trước khi `id=42`, thread khác có thể đọc `id=0` (default value) trong một lịch chạy, và final-field initialization guarantee cho instance này **không còn được áp dụng như properly constructed object**.

Một registry dùng `volatile`, `ConcurrentHashMap` hoặc `synchronized` **không cứu được** việc object được công bố **trước khi fields được gán**. Safe publication có thể công bố một object **chưa hoàn tất construction** một cách rõ ràng và hợp lệ về ordering, nhưng kết quả vẫn thiếu dữ liệu nếu publish quá sớm.

### Anti-pattern: gọi overridable method trong constructor

~~~java
class Base {
    Base() {
        initialize(); // dynamic dispatch có thể gọi subclass
    }

    void initialize() { }
}

class Child extends Base {
    final int value = 42;

    @Override
    void initialize() {
        // value có thể chưa được khởi tạo khi Base() gọi.
        System.out.println(value);
    }
}
~~~

Đây là **hai class fragments**. Điểm quan trọng: trong Java, dynamic dispatch xảy ra ngay cả khi đang thực thi base constructor, khi subclass field initializers chưa hoàn tất. Ngoài nguy cơ publish `this`, đây còn là nguồn quan sát **partially initialized subclass state**.

Với final constant initialized bằng compile-time literal, compiler có thể inline giá trị, làm ví dụ in ra không phản ánh rõ vấn đề. Để chứng minh tình huống thực tế, dùng subclass instance field không phải compile-time constant và tổ chức code cẩn thận; đừng hứa output của fragment trên.

### Cách sửa: factory đăng ký sau khi constructor hoàn tất

~~~java
final class Listener {
    final int id;

    private Listener(int id) {
        this.id = id;
    }

    static Listener createAndRegister(Registry registry, int id) {
        Listener listener = new Listener(id);
        registry.register(listener);
        return listener;
    }
}
~~~

Đây là code snippet cần `Registry`. Khi `new Listener(id)` trả về, constructor hoàn tất trước khi gọi `register`. Vẫn cần registry có **publication semantics phù hợp** cho các readers khác, và không sửa mutable state thiếu đồng bộ sau đó.

## 6. final reference khác deep immutability

Xét:

~~~java
final class Bag {
    final int[] values;

    Bag() {
        values = new int[] {4, 5};
    }
}
~~~

Điều `final` ngăn cấm theo Java language bình thường là **gán lại reference field**:

~~~java
// bag.values = new int[] {1, 2};  // compile-time error
bag.values[0] = 99;               // hợp lệ
~~~

Đây là snippets, cần biến `bag` phù hợp.

Sơ đồ:

~~~text
[Bag object]
    final values -------+
                        |
                        v
                    [int array]
                    [4][5]
                        |
                    element 0 mutable

FINAL: reference field không gán lại theo Java source
NOT FINAL: các array elements là dữ liệu mutable
~~~

### Bảo đảm khi arrays được khởi tạo trước freeze

JLS §17.5 giải thích rằng với một array/object được tham chiếu qua final field, những writes phù hợp đã xảy ra **trước freeze** có visibility guarantee đặc biệt khi thread khác dereference qua final field đó. Vì vậy nếu constructor tạo array, điền element trước khi kết thúc, reader đọc qua final reference có thể được bảo vệ cho trạng thái khởi tạo ban đầu.

Nhưng **sau khi object đã published**, nếu một thread sửa `values[0]` mà thread khác đọc không synchronization, đó vẫn có thể là **data race trên array element**. Bảo đảm của final reference không biến mọi lần mutate tương lai thành volatile writes.

Cũng không thể tùy tiện suy “mọi object reachable nhiều cấp đều tự động immutable” từ một final reference. Với nested mutable graphs và aliases bên ngoài, cần xem chính xác các writes/read paths và ownership.

### Defensive copying

Nếu class cần immutable snapshot của một mảng do caller cung cấp:

~~~java
final class ByteMessage {
    private final byte[] payload;

    ByteMessage(byte[] incoming) {
        this.payload = incoming.clone();
    }

    byte[] payload() {
        return payload.clone();
    }
}
~~~

Nếu không clone ở constructor, caller còn giữ `incoming` và có thể sửa array sau khi gửi vào object. Nếu trả raw `payload` trong getter, caller có thể sửa state nội bộ. **Clone cả vào và ra** giúp tránh aliasing ở cấp một mảng primitive. Với nested arrays hoặc mutable element objects, cần deep copy hoặc thiết kế ownership/immutability phù hợp.

**`List.copyOf`, `Map.copyOf` và `Collections.unmodifiableList` không tự deep-copy các objects mutable bên trong.** Immutable container không đồng nghĩa immutable element graph.

## 7. Ví dụ Java 17+ chạy độc lập: immutable snapshot và defensive copy

Tạo file **`ImmutableSnapshotDemo.java`**:

~~~java
import java.util.Arrays;

public class ImmutableSnapshotDemo {
    static final class Message {
        private final String id;
        private final byte[] payload;

        Message(String id, byte[] source) {
            this.id = id;
            this.payload = source.clone();
        }

        String id() {
            return id;
        }

        byte[] payload() {
            return payload.clone();
        }

        int firstByte() {
            return Byte.toUnsignedInt(payload[0]);
        }
    }

    public static void main(String[] args) {
        byte[] input = {1, 2, 3};
        Message message = new Message("m1", input);

        input[0] = 9;
        byte[] externalCopy = message.payload();
        externalCopy[1] = 8;

        System.out.println("id=" + message.id());
        System.out.println("first=" + message.firstByte());
        System.out.println("snapshot=" +
                Arrays.toString(message.payload()));
        System.out.println("input=" + Arrays.toString(input));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 ImmutableSnapshotDemo.java
java ImmutableSnapshotDemo
~~~

**Stdout chính xác:**

~~~text
id=m1
first=1
snapshot=[1, 2, 3]
input=[9, 2, 3]
~~~

Giải thích:
1. `Message` có final reference fields và không để `this` escape.
2. `payload` được clone trong constructor, nên caller sửa `input[0]` không tác động snapshot.
3. Getter trả clone mới, nên caller sửa `externalCopy[1]` không tác động internal array.
4. Không có writes tới internal array sau construction, nên đây là một object có **effective immutability** ở biên API nêu trên.
5. Code chỉ chứng minh copy/encapsulation semantics; nó **không kích hoạt hoặc quan sát một freeze event** trực tiếp. Freeze là mô hình JMM do JVM triển khai.

Nếu dữ liệu là `List<MutableUser>`, sao chép danh sách references không tự snapshot từng mutable user. Cần thiết kế immutable element type hoặc deep-copy theo nhu cầu.

## 8. final field visibility không bằng safe publication

Bảng so sánh:

| Tình huống | final fields của object properly constructed | ordinary fields khởi tạo trước công bố | updates sau công bố |
| --- | --- | --- | --- |
| Reference qua **ordinary field, data race** | Có **final-field guarantee** khi reader thấy object đúng | Không có full happens-before guarantee chỉ nhờ final | Không có guarantee nếu tiếp tục racy |
| Reference qua **volatile field** | Được bảo đảm nếu properly constructed | Writes trước volatile publish được ordered tới subsequent volatile read | Cần synchronization cho những updates sau publish |
| Reference dưới **cùng monitor lock** | Được bảo đảm | Writes trước unlock được ordered tới lock/read sau | Phải bảo vệ mutable access đúng lock/discipline |
| Reference trong **class initialized safely** | Được bảo đảm | Initialization visibility theo class init | Mutable fields sau đó vẫn cần đồng bộ |
| Reference qua **BlockingQueue/Future** đúng contract | Được bảo đảm | Handoff memory consistency theo API | Hậu công bố vẫn có thể có races nếu mutate |
| Reference qua **Thread.start()** trước worker actions | Được bảo đảm | Actions trước start HB worker actions | Những concurrent writes sau start không tự HB |
| Reference thoát **trước constructor exit** | Không có normally constructed guarantee | Có nguy cơ thấy partial state | Tiếp tục cần đồng bộ |

**Quan trọng:** bên ngoài mệnh đề “reader thấy object/reference”, racy publication có thể còn gặp visibility/liveness issues trên **chính reference**. Final-field semantics không khiến publisher phải công bố reference kịp thời hoặc cho mọi reader eventually thấy non-null.

### Các câu hỏi review hữu ích

1. Constructor hoàn tất lúc nào? Object có bị registry/callback nhìn thấy trước đó không?
2. Reference nằm ở ordinary field, volatile field hay concurrent container?
3. Reader đọc reference qua đường nào, có cùng synchronization protocol?
4. Fields nào final; fields nào mutable; elements/referents nào mutable?
5. Có writes sau khi publish không? Cơ chế bảo vệ từng write/read là gì?
6. Getter có trả alias tới internal mutable graph không?
7. Có subclass override/callback từ constructor không?
8. Có dependency là native/reflection/serialization framework sửa final fields không?

## 9. Safe publication bằng volatile

Ví dụ một snapshot object gồm cả final và ordinary fields, với một biến reference **volatile**:

~~~java
final class Settings {
    final String region;
    int timeoutMs;

    Settings(String region, int timeoutMs) {
        this.region = region;
        this.timeoutMs = timeoutMs;
    }
}

static volatile Settings current;
~~~

Đây là class/field snippets, `current` cần được khai báo trong một class hợp lệ.

Writer:

~~~java
current = new Settings("ap-southeast", 300);
~~~

Reader:

~~~java
Settings snapshot = current;
if (snapshot != null) {
    use(snapshot.region, snapshot.timeoutMs);
}
~~~

Các đoạn trên là snippets; method `use` chỉ minh họa. Nếu reader quan sát reference đã được published qua **volatile read**, toàn bộ constructor writes trước volatile write có HB tới những reads sau đó. Do đó `timeoutMs` ordinary field cũng có visibility guarantee **cho giá trị được ghi trước publication**.

**Nhưng:** nếu sau đó thread writer chạy `current.timeoutMs = 500;` mà reader tiếp tục đọc `timeoutMs` không synchronization, volatile nature của **reference field `current`** không tự làm `timeoutMs` trở thành volatile. Thay vì mutate snapshot, có thể publish **một immutable object mới** qua cùng volatile reference.

### Read volatile đúng lần khi cần nhất quán snapshot

~~~java
Settings snapshot = current;
if (snapshot != null) {
    // dùng tất cả fields từ cùng snapshot
}
~~~

Dùng local `snapshot` để đảm bảo cùng một instance xuyên các reads logic. Nếu đọc `current` nhiều lần giữa chừng và thread khác thay reference, code có thể lấy region từ instance A và timeout từ instance B, gây một “mixed configuration”, dù từng volatile read riêng lẻ hợp lệ.

## 10. Ví dụ Java 17+ chạy độc lập: publish immutable snapshot bằng volatile

Tạo file **`VolatilePublicationDemo.java`**:

~~~java
public class VolatilePublicationDemo {
    static final class Config {
        final String label;
        final int port;

        Config(String label, int port) {
            this.label = label;
            this.port = port;
        }
    }

    private static volatile Config published;

    public static void main(String[] args)
            throws InterruptedException {
        Thread writer = new Thread(() -> {
            Config next = new Config("READY", 8080);
            published = next; // volatile publication
        });

        writer.start();
        writer.join();

        Thread reader = new Thread(() -> {
            Config snapshot = published; // volatile read
            System.out.println("label=" + snapshot.label);
            System.out.println("port=" + snapshot.port);
        });

        reader.start();
        reader.join();
    }
}
~~~

Biên dịch/chạy JDK 17+:

~~~bash
javac --release 17 VolatilePublicationDemo.java
java VolatilePublicationDemo
~~~

**Stdout chính xác:**

~~~text
label=READY
port=8080
~~~

Trong ví dụ này:
- Writer tạo và hoàn tất `Config` trước khi gán volatile `published`.
- `writer.join()` cũng tạo một HB path tới main, làm ví dụ **deterministic**; reader được start sau join nên đảm bảo sẽ thấy object. **Đây không phải benchmark chỉ riêng cơ chế volatile** vì có thêm HB từ join/start.
- Volatile ở `published` cung cấp một cơ chế publication rõ ràng nếu mở rộng sang thiết kế writer/reader chạy đồng thời và đọc đúng volatile variable.
- `Config` có final fields; trong code này cả final guarantee lẫn safe publication đều được duy trì.

Đừng xóa `join()` rồi viết một reader có một phép đọc duy nhất và đoán nó chắc chắn thấy non-null ngay. Một reader có thể chạy trước khi writer publish. **Ordering/visibility** không tự đồng nghĩa **scheduling/arrival time**.

## 11. Safe publication bằng synchronized monitor

Writer:

~~~java
synchronized (lock) {
    shared = new Settings("ap-southeast", 300);
}
~~~

Reader:

~~~java
Settings copy;
synchronized (lock) {
    copy = shared;
}
~~~

Đây là snippets, cần `lock` và `shared` trong class hợp lệ. Nếu writer **unlock monitor** sau assignment và reader **subsequently lock chính monitor đó**, JMM thiết lập synchronizes-with edge. Các writes trong constructor trước publication có thể được quan sát qua HB tại reader.

Hai lỗi thường gặp:
- Writer lock object A, reader lock object B: **không có cùng monitor** nên không nhận được edge mong muốn.
- Writer dùng synchronized, reader đọc `shared` trực tiếp ngoài lock: reader có thể **không** hưởng visibility guarantee từ monitor protocol.

### synchronized không chỉ là mutual exclusion

Nó cung cấp **atomicity** cho critical section theo mutual exclusion và **visibility/ordering** khi lock/unlock đúng cặp monitor. Tuy nhiên nó không tự giải quyết object escaping từ constructor hoặc việc trả raw aliases tới mutable array sau khi lock được thả.

## 12. Safe publication qua class initialization

Initialization-on-demand holder là một idiom đơn giản:

~~~java
final class Service {
    final int id;

    private Service(int id) {
        this.id = id;
    }

    private static final class Holder {
        static final Service INSTANCE = new Service(42);
    }

    static Service instance() {
        return Holder.INSTANCE;
    }
}
~~~

JVM phối hợp class initialization theo JLS §12.4.2, do đó instance được tạo trong static initializer và công bố thông qua Holder có initialization visibility đúng cho những threads sử dụng class. Không cần tự viết `volatile` hoặc `synchronized` trong accessor mỗi lần.

### Eager static final

~~~java
final class Constants {
    static final Service INSTANCE = new Service(42);
}
~~~

Tạo object từ static field initializer cũng hưởng cơ chế class initialization tương ứng. Phải phân biệt **static final reference** với **JLS constant variable**: `Service` là reference type, không thể trở thành compile-time constant variable như một `static final int ANSWER=42`.

### Enum singleton

~~~java
enum GlobalService {
    INSTANCE;
}
~~~

Enum initialization cũng được JVM quản lý và thường hữu ích để tạo singleton an toàn với built-in serialization semantics. Nhưng nếu enum singleton giữ mutable fields, các operations trên fields vẫn phải thread-safe.

## 13. Ví dụ Java 17+ chạy độc lập: holder singleton và thread join

Tạo file **`HolderPublicationDemo.java`**:

~~~java
public class HolderPublicationDemo {
    static final class Service {
        final int id;
        final String name;

        private Service() {
            id = 42;
            name = "single";
        }
    }

    static final class Holder {
        static final Service INSTANCE = new Service();
    }

    public static void main(String[] args)
            throws InterruptedException {
        Service[] observed = new Service[4];
        Thread[] workers = new Thread[observed.length];

        for (int i = 0; i < observed.length; i++) {
            final int index = i;
            workers[i] = new Thread(
                    () -> observed[index] = Holder.INSTANCE);
            workers[i].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        boolean same = true;
        for (Service service : observed) {
            same &= service == observed[0];
        }

        System.out.println("same=" + same);
        System.out.println("id=" + observed[0].id);
        System.out.println("name=" + observed[0].name);
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 HolderPublicationDemo.java
java HolderPublicationDemo
~~~

**Stdout chính xác:**

~~~text
same=true
id=42
name=single
~~~

Các threads có thể đụng `Holder.INSTANCE` cùng lúc, nhưng JVM chỉ thực hiện class initialization phù hợp **một lần** cho một class loader/class instance. `join()` làm main chờ và cung cấp HB để đọc các phần tử của array `observed`. Code này không đo thô chi phí synchronization; nó minh họa một cách publication **không cần tự viết double-checked locking**.

## 14. Double-checked locking: vì sao cần volatile?

**Double-checked locking (DCL)** nhằm giảm overhead synchronized sau khi singleton đã được tạo:

~~~java
final class LazyService {
    private static volatile LazyService instance;

    static LazyService get() {
        LazyService result = instance;
        if (result == null) {
            synchronized (LazyService.class) {
                result = instance;
                if (result == null) {
                    result = new LazyService();
                    instance = result;
                }
            }
        }
        return result;
    }
}
~~~

Đây là class snippet. Cần khai báo constructor phù hợp.

**Vì sao có hai checks?**
- Check ngoài giảm việc vào monitor khi instance đã có.
- Check trong bảo đảm sau khi acquire lock, nếu thread khác đã tạo singleton thì không tạo lần hai.

**Vì sao `volatile` quan trọng?**
- Một thread có thể quan sát reference ngoài synchronized block.
- Without volatile hoặc một publication mechanism tương đương, reference read/write ở ngoài lock có data race và không bảo đảm visibility thích hợp cho toàn bộ constructor writes.
- Volatile publication tạo ordering/visibility: readers nhìn thấy reference sau publication sẽ nhìn thấy trạng thái khởi tạo trước đó.
- Một `final` field trong singleton **không tự biến ordinary fields hay mutable graph của singleton thành safely published**, và không biến toàn bộ DCL không volatile thành một idiom nên dùng.

### Cách đọc tránh race giữa hai volatile reads

Dùng local `result` để đọc volatile một lần trên mỗi check level; cũng phù hợp khi value được thay đổi trong những patterns khác. Với singleton chỉ khởi tạo một lần, mục tiêu chính là correct publication và avoiding unnecessary reads.

### Không cần DCL mọi nơi

Nếu singleton không cần lazy creation, dùng static final field. Nếu muốn lazy nhưng ít phức tạp, dùng **initialization-on-demand holder**. DCL hữu ích khi cần lazy initialization với lifecycle/constructor args hoặc logic đặc biệt nhưng tăng chi phí review.

## 15. Ví dụ Java 17+ chạy độc lập: DCL singleton đúng

Tạo file **`DclSingletonDemo.java`**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class DclSingletonDemo {
    static final class Service {
        private static final AtomicInteger CREATED =
                new AtomicInteger();
        private static volatile Service instance;

        final int value;
        final String label;

        private Service() {
            value = 42;
            label = "ready";
            CREATED.incrementAndGet();
        }

        static Service get() {
            Service result = instance;
            if (result == null) {
                synchronized (Service.class) {
                    result = instance;
                    if (result == null) {
                        result = new Service();
                        instance = result;
                    }
                }
            }
            return result;
        }

        static int creationCount() {
            return CREATED.get();
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Service[] results = new Service[8];
        Thread[] threads = new Thread[results.length];

        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(
                    () -> results[index] = Service.get());
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        boolean allSame = true;
        for (Service result : results) {
            allSame &= result == results[0];
        }

        System.out.println("same=" + allSame);
        System.out.println("created=" + Service.creationCount());
        System.out.println("value=" + results[0].value);
        System.out.println("label=" + results[0].label);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 DclSingletonDemo.java
java DclSingletonDemo
~~~

**Stdout chính xác:**

~~~text
same=true
created=1
value=42
label=ready
~~~

Lý do đúng:
- Chỉ một thread có thể tạo instance trong critical section nếu result vẫn null.
- `volatile instance` cung cấp release/acquire semantics cho publication và lock-free read path.
- Các fields final được thiết lập trước khi constructor trả về.
- `AtomicInteger` đếm một lần constructor invocation trong ví dụ hợp lệ, nhưng **không phải điều kiện cần** cho DCL; nó chỉ phục vụ trình diễn.
- `join()` giúp main đọc các phần tử results đã được threads gán.

**Không được bỏ `volatile` rồi lấy output trên một máy làm chứng minh DCL vẫn đúng.** Failure có thể hiếm trên phần cứng/test cụ thể mà JMM vẫn không hứa correctness.

## 16. Safe handoff qua Thread.start và Thread.join

Khởi tạo object trên main rồi start worker là một handoff JMM hợp lệ:

~~~java
final class WorkData {
    int ordinary;
    final int fixed;

    WorkData() {
        ordinary = 17;
        fixed = 42;
    }
}
~~~

Trong main:

~~~java
WorkData data = new WorkData();
Thread worker = new Thread(() -> {
    System.out.println(data.ordinary);
    System.out.println(data.fixed);
});
worker.start();
worker.join();
~~~

Đây là **hai snippets minh họa**, cần bọc trong class/method, checked exception `InterruptedException`. Actions trước `start()` happen-before actions của worker. Vì vậy trong tình huống này **cả ordinary và final fields** được initialized visible tới worker; không cần thêm volatile chỉ để pass reference trước start.

Nhưng nếu main mutate `data.ordinary` **sau khi start** đồng thời với worker read, thread-start HB không tự order write mới đó. Cần synchronization thích hợp.

### Thread.join trả kết quả theo hướng ngược

Worker writes trước termination happen-before main actions sau `join()` thành công. Đây là lý do các ví dụ multi-thread có thể thu kết quả vào các slots khác nhau và chỉ print từ main sau khi joins.

**Cả platform threads và virtual threads** (JDK 21+) đều tuân những Java Memory Model rules tương ứng; carrier mapping không thay đổi final-field semantics.

## 17. Handoff qua BlockingQueue, Future và concurrent collections

Một số Java concurrency APIs quy định memory consistency effects rõ ràng.

### BlockingQueue

Writer tạo và hoàn tất immutable message rồi `put(message)` vào `BlockingQueue<Message>`. Reader `take()` cùng queue để nhận message. Các actions trước việc đặt object vào queue happen-before actions sau khi thread khác truy cập/loại nó qua queue theo API memory consistency properties.

~~~text
Producer                        Consumer
  new Message(...)
  fields initialized
        |
  queue.put(message) ----------> queue.take()
                                     |
                               message fields read
~~~

Đây là mô hình **safe handoff**, miễn rằng producer không mutate shared mutable payload thiếu đồng bộ sau khi put.

### ConcurrentHashMap

Dùng `ConcurrentHashMap` để đưa **fully initialized objects** vào map, và reader dùng retrieval tương ứng theo API. Memory visibility guarantee của map không khiến object đã publish ở constructor trước khi init hoàn tất “hoàn chỉnh trở lại”.

### Future / ExecutorService

Các actions trước task submission happen-before task execution theo `ExecutorService` contract; task actions happen-before **result được lấy qua `Future.get()`** theo package/API memory consistency rules.

**Đừng nhầm** “nộp task vào executor” với “task đã hoàn tất”. Reader cần `Future.get()`, join, latch hoặc cơ chế tương ứng để chờ kết quả và ordering phù hợp.

### AtomicReference

Một `AtomicReference<T>` dùng `set`/`get` cung cấp visibility semantics tương tự volatile reference cho thao tác tương ứng. Nhưng tham chiếu atomic không biến toàn bộ state của T thành immutable hoặc mọi field access thành atomic transaction.

## 18. Ví dụ Java 17+ chạy độc lập: safe publication bằng BlockingQueue

Tạo file **`QueuePublicationDemo.java`**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class QueuePublicationDemo {
    static final class Payload {
        final String name;
        final int code;

        Payload(String name, int code) {
            this.name = name;
            this.code = code;
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        BlockingQueue<Payload> queue =
                new ArrayBlockingQueue<>(1);

        Thread producer = new Thread(() -> {
            Payload payload = new Payload("TASK", 27);
            try {
                queue.put(payload);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();

        Payload received = queue.take();
        producer.join();

        System.out.println("name=" + received.name);
        System.out.println("code=" + received.code);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 QueuePublicationDemo.java
java QueuePublicationDemo
~~~

**Stdout chính xác khi chương trình hoàn tất bình thường:**

~~~text
name=TASK
code=27
~~~

Constructor kết thúc trước khi producer enqueue. Queue đảm bảo safe handoff tới consumer. `producer.join()` chỉ chờ thread kết thúc để lifecycle rõ ràng; visibility giữa put/take đã được API đảm bảo. Nếu producer bị interrupt trước khi put, ví dụ sẽ chờ trên `take()`; chương trình đơn giản này không có timeout/cancellation policy riêng, vì vậy production code nên thiết kế timeout hoặc một result/error channel phù hợp.

## 19. final reference không làm mutable data thread-safe

Ví dụ:

~~~java
final class Stats {
    final int[] counts = new int[1];

    void increment() {
        counts[0]++;
    }

    int read() {
        return counts[0];
    }
}
~~~

`counts` luôn tham chiếu cùng array, nhưng `counts[0]++` là một read-modify-write không atomic. Hai threads cùng gọi `increment()` có thể mất cập nhật. `final` **không** bảo đảm các element reads/writes có visibility/happens-before mới sau initialization.

Có thể sửa bằng:

~~~java
final class StatsSafe {
    private final java.util.concurrent.atomic.AtomicInteger count =
            new java.util.concurrent.atomic.AtomicInteger();

    void increment() {
        count.incrementAndGet();
    }

    int read() {
        return count.get();
    }
}
~~~

Đây là snippet class hoàn chỉnh với fully qualified class name. AtomicInteger có operations thread-safe theo contract. Field reference `count` là final để reference không bị gán lại sau constructor, còn **atomic state** của chính AtomicInteger được đồng bộ theo API.

Một alternative là `synchronized` ở cả `increment()` và `read()`; hoặc thiết kế immutable snapshots publish lại mỗi update qua `volatile`/AtomicReference.

### AtomicReference không làm nhiều field updates thành atomic snapshot

Nếu một object có `name` và `timeout` mutable, viết hai fields rồi để readers đọc qua một AtomicReference không tự tạo atomicity xuyên hai field assignments. Tốt hơn tạo **một immutable Config mới** chứa cả hai giá trị rồi đổi reference bằng một atomic/volatile store. Reader lấy **một snapshot** rồi sử dụng cả hai fields của nó.

## 20. Records và immutable classes

JDK 17 hỗ trợ records ổn định. Record components tương ứng private final fields, đem lại cú pháp ngắn cho những value aggregates.

~~~java
record Endpoint(String host, int port) { }
~~~

Nếu `host` là immutable String và `port` là primitive, record này là một value carrier dễ dùng theo style immutable. Nhưng một record có component `byte[]` **không deep immutable**:

~~~java
record Packet(byte[] bytes) { }
~~~

Caller có thể sửa array được đưa vào canonical constructor hoặc array getter trả về. Record compiler tạo final reference field, **không tự clone** array.

### Record với defensive copy

~~~java
record Packet(byte[] bytes) {
    Packet {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
~~~

Đây là **đoạn record snippet**, có thể biên dịch trên Java 17+ khi đặt đúng file/package. Compact canonical constructor cho phép chuẩn hóa parameter trước khi tự động gán component field; clone ở accessor ngăn trả alias. Đối với nested mutable graphs, phải thiết kế deep copying hoặc immutable elements.

### Immutable là một contract thiết kế

Một class có `final int x` nhưng getter tùy tiện trao raw references tới mutable internal state không tự thành immutable class. An toàn với nhiều threads đòi hỏi **không có mutation quan sát được sau khi constructed** hoặc phải đồng bộ mutation. Java final-field rules giúp xây **thread-safe immutable objects**, không thay thế thiết kế API.

## 21. Mutable singleton và giới hạn của class initialization

Holder singleton tạo object an toàn một lần, nhưng nếu singleton chứa mutable state:

~~~java
final class CounterService {
    static final CounterService INSTANCE =
            new CounterService();

    int count;

    void increment() {
        count++;
    }
}
~~~

Việc `INSTANCE` được published an toàn **không** khiến `count++` atomic hoặc thread-safe. Class initialization chỉ bảo đảm initialization visibility theo quy tắc, không biến mọi subsequent method calls thành synchronized.

Cần kết hợp:
- AtomicInteger/LongAdder cho counters theo workload.
- Synchronized/locks cho compound invariants.
- Immutable snapshots qua AtomicReference nếu state có nhiều fields cần nhất quán.
- Concurrent collections phù hợp.
- Thread confinement hoặc message passing nếu không muốn shared mutable state.

**Câu ghi nhớ:** *safe initialization + safe publication ≠ automatic safe mutation forever.*

## 22. Constructor escape qua Thread.start: vì sao nguy hiểm?

Anti-pattern:

~~~java
final class EarlyWorker {
    final int port;

    EarlyWorker() {
        new Thread(() -> System.out.println(port)).start();
        port = 8080;
    }
}
~~~

Đây là **class minh họa**; Java source có thể biên dịch khi blank final được gán đúng. Runnable capture `this`; start xảy ra **trước** `port=8080`. Thread có thể đọc `0` nếu nó chạy sớm hoặc `8080` theo scheduling và memory effects thực tế; không có guarantee properly constructed publication cho final field ở thời điểm start.

**Không làm demo tự động in `0` với `Thread.sleep()` để “chứng minh JMM”**: scheduler và JIT khác nhau, timing không quyết định legality của memory model.

Cách sửa:

~~~java
final class ReadyWorker {
    final int port;

    ReadyWorker(int port) {
        this.port = port;
    }

    Thread startWorker() {
        Thread t = new Thread(() ->
                System.out.println(port));
        t.start();
        return t;
    }
}
~~~

Caller chỉ gọi `startWorker()` **sau khi `new ReadyWorker(...)` hoàn tất**, tránh escape trong constructor. Nếu có superclass/subclass inheritance, cách bố trí constructors/callbacks vẫn cần kiểm tra. Có thể dùng static factory để đảm bảo lifecycle.

### Gọi start ở cuối constructor liệu đủ?

Không nên làm vậy. Ngay cả khi `start()` nằm ở dòng cuối source, constructor chưa thực sự hoàn tất; object có thể escape trước constructor exit/freeze. Với subclassing, superclass constructor có thể start thread khi subclass fields chưa được gán. Giải pháp đơn giản nhất là **không publish `this` từ constructor**, mà tách start/register ra sau factory/construction.

## 23. Publication sai: không thể sửa bằng sleep, yield hay join sai hướng

Một pattern sai:

~~~java
static Example shared;

static void writer() {
    shared = new Example();
}

static void reader() {
    while (shared == null) {
        Thread.yield();
    }
    use(shared);
}
~~~

Đây là snippets cần Example/use thích hợp. `Thread.yield()` chỉ là scheduling hint, **không** hình thành volatile/monitor HB và không hứa một thread sẽ thấy reference thay đổi. Compiler/JMM có thể không cung cấp tiến triển/visibility như code mong đợi.

Tương tự:
- `Thread.sleep(10)` không tạo memory barrier đúng nghĩa mà bạn được quyền dùng để publish fields.
- Nhiều lần chạy đúng không chứng minh data race không có.
- `Thread.join()` chỉ có memory effect khi **thread chờ thực sự phát hiện termination** theo contract; không thể rải một `join` vô nghĩa vào đường thực thi khác rồi cho rằng mọi writes từ mọi threads đã visible.
- Ghi console đôi khi thay đổi timing/internal synchronization khiến race khó tái hiện, nhưng **không sửa correctness**.
- `synchronized` ở writer mà reader không đồng bộ cùng lock không phải safe publication protocol.

**Giải pháp:** tạo một HB path rõ ràng giữa những writes trước publication và reads sau publication, hoặc dùng đúng final-field semantics của immutable constructed object kết hợp thiết kế publication phù hợp.

## 24. Stress test JMM: đừng dùng số lần chạy bình thường làm chứng minh

Một concurrency bug có thể chỉ xuất hiện khi:
- JIT tối ưu đủ lâu, reorder/hoist reads.
- CPU architecture cho phép memory behavior khác máy dev.
- Thread scheduling thay đổi ở tải cao.
- Class initialization/safepoint/GC timings khác.
- Test trên nhiều CPU cores và compiler tiers.
- Publication diễn ra sát lúc constructor chưa hoàn tất.

Một bài thử cố gắng “đo số lần field bằng 0” với một vòng loop ngắn có thể trả về 0 lần lỗi **ngay cả khi code có data race**. Đó không phải bằng chứng an toàn.

### jcstress

OpenJDK **jcstress** là framework dành cho nghiên cứu các kết quả có thể quan sát trong chương trình concurrent theo memory-model litmus tests. Nó thực thi actors đồng thời qua nhiều iterations, thu outcomes và phân biệt expected/acceptable/interesting/forbidden states theo test định nghĩa.

**Điều nó giúp:** tăng cơ hội phát hiện outcome hiếm, thử nhiều JVM modes/hardware, học về visibility/ordering.

**Điều nó không hứa:** một race hợp lệ sẽ luôn xảy ra trên máy của bạn; không quan sát kết quả X không chứng minh X bị JMM cấm; stress testing không thay thế lập luận happens-before/JLS.

Trang dự án: https://openjdk.org/projects/code-tools/jcstress/ và https://github.com/openjdk/jcstress.

### Không dùng JMH thay jcstress

**JMH** đo hiệu năng microbenchmarks và kiểm soát JIT warmup/forks; **jcstress** kiểm thử outcomes concurrency. Chúng phục vụ câu hỏi khác nhau. Dùng JMH để kiểm tra correctness JMM hoặc jcstress để so p99 REST latency đều không đúng mục đích.

## 25. Chẩn đoán production: nhìn vào ownership, publication và API contract

Các công cụ như JFR, thread dumps, `jcmd` hoặc profiler hữu ích để thấy locks, thread states, CPU, GC và class loading, nhưng chúng **không tự chứng minh** chương trình không có data races. Một JMM bug phải được đánh giá qua code review và happens-before graph.

Một checklist điều tra:
1. Tìm fields có thể được nhiều threads cùng đọc/ghi. Phân biệt immutable snapshots và mutable shared state.
2. Tìm mọi nơi object reference được lưu vào shared static/instance fields, registries, maps hoặc queues.
3. Xác định thời điểm constructor hoàn tất so với những nơi publish reference.
4. Kiểm tra callbacks/listeners/overridable methods khiến `this` escape sớm.
5. Với reader, xác định acquire operation tương ứng với writer's release.
6. Vẽ happens-before chain cụ thể; không dừng ở “đã dùng synchronized somewhere”.
7. Với final reference chứa array/collection, kiểm tra defensive copy và mutations sau công bố.
8. Kiểm tra DCL `volatile`, lock monitor identity và class-init holder.
9. Khi nghi race, thiết kế jcstress test để xem outcomes mà JMM cho phép.
10. Sửa cách ownership/synchronization, rồi kiểm tra regression cả trên Java 17 và JDK triển khai thực.

### Khả năng quan sát và giới hạn

`jcmd PID Thread.print` hoặc JFR có thể giúp thấy thread stacks và lock contention; race trên ordinary field không bắt buộc có lock contention hoặc event rõ ràng. Thread dumps thường chỉ là ảnh chụp thời điểm; code có race không nhất thiết xuất hiện trong ảnh chụp.

Các lệnh diagnostics lấy PID từ process thật, không đưa dữ liệu nhạy cảm của production cho bên ngoài khi chưa được phép.

## 26. Các ngộ nhận phổ biến

| Ngộ nhận | Sự thật |
| --- | --- |
| “Constructor kết thúc nghĩa thread khác chắc chắn thấy mọi field.” | Chỉ khi có safe publication/HB hoặc những bảo đảm đặc biệt như final-field semantics. |
| “final và volatile giống nhau.” | final có initialization safety/immutability của field; volatile có ordering/visibility cho accesses tới variable. |
| “final ref khiến array/list bất biến.” | Chỉ field reference không được gán lại; referent và elements có thể mutable. |
| “final-field rules publish reference tới mọi thread.” | Không; ordinary racy publication vẫn có visibility/liveness issues trên reference. |
| “Constructor freeze tạo HB tổng quát cho mọi fields.” | §17.5 có điều kiện và dereference semantics đặc biệt; không tương đương volatile publish. |
| “Nếu dùng ConcurrentHashMap thì publish giữa constructor cũng ổn.” | Có thể safely publish **partial object**; phải construct đầy đủ trước put. |
| “Chạy new Thread trong constructor là cách khởi động tiện và luôn an toàn.” | Runnable capture this có thể observe object chưa initialized. |
| “final field gán ở cuối constructor là đủ dù this đã escape trước đó.” | Proper construction đã bị vi phạm, không dựa được guarantee đặc biệt. |
| “static final singleton không cần đồng bộ mọi method.” | Khởi tạo an toàn không làm mutations về sau tự thread-safe. |
| “volatile ref làm object fields thành volatile.” | Chỉ publication trước write được bảo đảm; post-publication mutable writes cần synchronization. |
| “Một thread thấy volatile ref là mọi object reachable đều immutable.” | Visibility của pre-publication writes khác với bất biến của graphs có aliases/mutations. |
| “DCL không volatile vẫn đúng vì constructor có final fields.” | DCL không volatile thiếu safe publication phù hợp cho ordinary state và reader path. |
| “Cùng chạy trên x86 không tái hiện lỗi thì JMM guarantee.” | Một execution không chứng minh portable correctness. |
| “Thread.sleep tạo memory barrier.” | Sleep không phải safe-publication protocol. |
| “volatile write đồng nghĩa compound action atomic.” | Volatile riêng field không làm nhiều bước read-modify-write thành transaction. |
| “Record luôn deep immutable.” | Record component có thể là mutable array/collection object. |
| “Immutable list luôn deep copy phần tử.” | CopyOf/unmodifiable wrappers có thể giữ mutable element references. |
| “jcstress không thấy lỗi tức chương trình chắc chắn an toàn.” | Stress outcomes không thay thế lập luận theo JLS. |
| “GC/JIT tự sửa race thiếu synchronization.” | JVM giữ đúng memory-model semantics, không tự cung cấp guarantees mà program thiếu. |

## 27. Câu hỏi tự kiểm tra kèm đáp án

**1. final-field freeze xảy ra khi nào?**

Theo JLS 17.5.1, có freeze action khi constructor ghi final field kết thúc. Cần hiểu chi tiết constructor delegation, không để reference escape sớm.

**2. Một object được constructed đúng, published qua ordinary racy field; reader nhìn thấy reference. Field `final int x=42` được đảm bảo thế nào?**

Properly constructed final-field guarantee cho phép reader thấy giá trị khởi tạo đúng của x. Nhưng publication qua ordinary field vẫn không cung cấp full HB cho arbitrary non-final fields và không bảo đảm reader sớm thấy reference.

**3. Tại sao `int y=17` ordinary field của cùng object không nhận bảo đảm giống final x khi racy publish?**

Vì final-field semantics đặc biệt không tự trở thành một synchronization HB edge tổng quát cho mọi ordinary fields.

**4. Nếu `this` được đưa vào registry trước khi gán final x, reader có chắc x=42 không?**

Không. Object không được properly constructed trước publication; có thể bị quan sát partial state.

**5. final `byte[] payload` có làm `payload[0]++` atomic không?**

Không. Final áp dụng cho reference field; array elements mutable và increments không atomic.

**6. clone trong constructor nhưng getter trả raw internal byte[] có immutable không?**

Không. Caller có thể mutate internal array qua getter. Cần defensive copy ở cả boundaries phù hợp.

**7. Writer lưu object fully initialized vào volatile field; reader đọc field đó. Điều gì được bảo đảm?**

Writes trước volatile publication HB những reads sau volatile acquire, nên readers thấy initial state theo HB. Updates mutable về sau vẫn cần đồng bộ.

**8. Writer khóa monitor A, reader khóa B, có safe publication không?**

Không chỉ từ hai locks khác nhau. Phải có đúng matching synchronization protocol.

**9. Holder singleton có cần synchronized trên mỗi accessor call?**

Không; class initialization cung cấp initialization safety cho static final INSTANCE. Mutable state trong instance vẫn phải thread-safe riêng.

**10. DCL cần volatile vì sao?**

Fast path đọc reference ngoài monitor; volatile cung cấp ordering/visibility cho publication và tránh data race trên reference.

**11. Nếu main tạo data rồi Thread.start(worker), worker được bảo đảm thấy writes trước start không?**

Có; start happens-before actions trong worker.

**12. Nếu worker ghi output rồi main join(worker), main thấy writes không?**

Có sau khi join thành công, theo termination/join HB.

**13. Volatile `current` thay reference mới, reader đọc current hai lần có thể thấy hai snapshots khác nhau không?**

Có, nếu publisher thay reference giữa hai reads; capture một local snapshot nếu cần coherence trên một object version.

**14. Một ConcurrentHashMap chứa fully initialized object có cần final cho mọi field để visibility đúng không?**

Không phải điều kiện của publication; map API cung cấp memory consistency cho insert/retrieval. Nhưng final vẫn có ích cho immutable design và fields mutate sau đó cần synchronization.

**15. Tại sao sleep không chứng minh không có data race?**

Sleep thay timing nhưng không tự tạo volatile/monitor/thread handoff edge cần thiết.

**16. JMH có thể chứng minh JMM cấm một outcome không?**

Không. JMH đo performance; cần JLS reasoning, code review và công cụ như jcstress cho stress outcomes.

**17. final field trong object mà constructor đã throw exception có ý nghĩa giống object hợp lệ được published không?**

Đừng thiết kế publication từ failed constructors. JLS mô tả freeze details cả abrupt exit, nhưng một object không xây dựng invariants hoàn chỉnh không nên được đưa cho application threads sử dụng.

**18. Record có component int[] và accessor trả original array có deep immutable không?**

Không. Record final field chỉ cố định reference, không ngăn mutations của array.

**19. final `AtomicInteger` có phải giá trị counter cố định không?**

Không. Reference tới AtomicInteger cố định, nhưng internal atomic value có thể cập nhật thread-safe.

**20. Cần hỏi gì đầu tiên nếu một thread đôi khi đọc config timeout=0?**

Kiểm tra constructor, publication timing, đường volatile/lock/queue/handoff ở writer-reader, và xem timeout được gán trước hay sau publish. Không kết luận “compiler reorder bug” khi thiếu HB.

## 28. Bản đồ ghi nhớ

~~~text
                CREATE OBJECT O
                       |
              DEFAULT FIELD VALUES
                       |
                 CONSTRUCTOR
                       |
            +----------+----------+
            |                     |
        NO this ESCAPE       this ESCAPES EARLY
            |                     |
       fields initialized      possible partial view
            |                     |
      CONSTRUCTOR EXIT         final guarantee may fail
            |
      FINAL FIELD FREEZE
            |
            v
          PUBLICATION
            |
       +----+---------------------------+
       |                                |
  RACY ORDINARY FIELD              SAFE PUBLICATION
       |                                |
  final-field special              volatile/lock/start/
  guarantee only                   class init/queue/Future
       |                                |
  non-final not fully              constructor writes
  ordered; ref liveness            happen-before reads
  not guaranteed                        |
       +----------------+---------------+
                        |
                   AFTER PUBLISH
                        |
                IS OBJECT MUTABLE?
                     /      \
                    NO       YES
                    |         |
             final + deep   locks/atomic/
             immutability   volatile/snapshot/
                    |       ownership discipline
                    +---------+
                              |
                    THREAD-SAFE DESIGN
                              |
                  Verify with JLS HB graph
                  and targeted jcstress tests
~~~

**Kết luận:** `final` fields có **initialization safety** đặc biệt nhờ **constructor freeze**, với điều kiện object được **properly constructed** và reference không escape sớm. Tuy nhiên final field **không tự tạo full safe publication cho mọi non-final fields**, cũng không làm một referent graph mutable trở thành deep immutable. Để code concurrent đáng tin, hãy **hoàn tất construction trước publication**, tạo một **happens-before path rõ ràng** bằng volatile/locks/class initialization/Thread.start/concurrent handoff phù hợp, và bảo vệ **mọi mutations về sau** theo ownership/synchronization protocol riêng.

## Tài liệu tham khảo chính thức

- [Java Language Specification SE 17 — §17.4 Memory Model](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4).
- [Java Language Specification SE 17 — §17.4.5 Happens-before Order](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5).
- [Java Language Specification SE 17 — §17.5 final Field Semantics](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.5).
- [Java Language Specification SE 17 — §17.5.1 Semantics of final Fields](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.5.1).
- [Java Language Specification SE 17 — §12.4.2 Detailed Initialization Procedure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2).
- [Java Language Specification SE 17 — §15.29 Constant Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.29).
- [Java SE 17 API — Thread.start, join, and interrupt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html).
- [Java SE 17 API — BlockingQueue memory consistency](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html).
- [Java SE 17 API — ExecutorService memory consistency](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html).
- [Java SE 17 API — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html).
- [Java SE 17 API — ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).
- [OpenJDK jcstress — Java Concurrency Stress tests](https://openjdk.org/projects/code-tools/jcstress/).
- [OpenJDK jcstress source repository](https://github.com/openjdk/jcstress).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-threads-and-synchronization-010">Sắp xếp lại thao tác, data race và những bảo đảm mà sleep không cung cấp</a>

<details>
<summary>Xem chi tiết</summary>

# Sắp xếp lại thao tác, data race và những bảo đảm mà `Thread.sleep` không cung cấp

## 1. Mục tiêu: phân biệt “trông có vẻ theo thứ tự” và “được Java bảo đảm”

Java chạy trên nhiều CPU, JVM và bộ tối ưu JIT khác nhau. Nếu hai threads cùng truy cập trạng thái mutable mà không đồng bộ đúng, chương trình có thể quan sát kết quả **khác trực giác đọc source từ trên xuống**, dù từng thread vẫn hoạt động đúng theo các quy tắc của Java Memory Model (JMM).

Ba khái niệm cần phân biệt:

1. **Reordering (sắp xếp lại thao tác):** compiler, JVM hoặc CPU có thể tổ chức thực thi theo thứ tự khác thứ tự câu lệnh nhìn thấy, miễn các quan sát hợp lệ mà mô hình bộ nhớ yêu cầu vẫn được bảo toàn.
2. **Data race:** hai thao tác truy cập cùng một shared variable, ít nhất một là **write**, nhưng không có quan hệ **happens-before** sắp thứ tự chúng. Các kết quả racy có thể khó dự đoán và không được mô hình sequential consistency thông thường bảo vệ.
3. **`Thread.sleep` không phải synchronization:** ngủ làm thread ngừng chạy tạm thời theo scheduler, **không tạo cạnh happens-before, không bắt JVM flush/reload shared data, không thả intrinsic monitors đang giữ**.

Bài sử dụng **Java 17**, dựa trực tiếp trên **JLS `17.3`, `17.4`, `17.5`** và API `Thread`. Các ví dụ đã tách rõ **chương trình chạy được với stdout xác định** và **litmus test có kết quả không xác định**. Không suy luận hiệu năng hoặc bảo đảm bộ nhớ từ một lần thực thi may mắn trên một máy.

## 2. Ba loại “thứ tự” thường bị nhầm

Khi đọc:

```java
int a = 1;
int b = 2;
System.out.println(a + b);
```

Đây là fragment chạy tuần tự. Compiler có thể tối ưu phép toán hoặc giữ values trong registers, nhưng kết quả phải tương đương một chương trình đúng: in `3`. Với **một thread**, Java tôn trọng **intra-thread semantics**: kết quả có thể quan sát của thread phải phù hợp cách chạy riêng của nó theo thứ tự chương trình (`as-if-serial` về trực giác).

Ở chương trình **nhiều threads**, cần phân biệt:

| Thuật ngữ | Vai trò |
| --- | --- |
| **Source order** | Thứ tự code được trình bày trong `.java`; không luôn là thứ tự machine instructions |
| **Program order** | Quan hệ thứ tự các actions trong **một thread** theo mô hình JMM |
| **Synchronization order** | Một total order trên các **synchronization actions** trong một execution, nhất quán với program order từng thread |
| **Synchronizes-with** | Các cạnh đồng bộ đặc thù: volatile write→read, monitor unlock→lock, thread start/join, v.v. |
| **Happens-before (HB)** | Quan hệ thứ tự được sinh từ program order và synchronizes-with (có tính bắc cầu) |
| **Wall-clock order** | Thời gian thực tế scheduler cho threads chạy; tự nó không định nghĩa HB |

Một câu “A chắc chắn chạy trước B trong thế giới thật vì tôi gọi `sleep(100)`” **không phải** bằng chứng memory-ordering. Thứ tự wall-clock dự đoán từ delays không được thay thế bằng một quan hệ HB.

### HB không chỉ là đồng hồ

Viết `hb(A, B)` nghĩa là action A xảy ra **trước theo quan hệ JMM** B: những yêu cầu visibility/ordering thích hợp được áp dụng. Có thể tạo chuỗi:

```text
Trong Thread 1:
    A -> B (program order)

B synchronizes-with C ở Thread 2

Trong Thread 2:
    C -> D (program order)

Suy ra:
    hb(A, D) theo tính bắc cầu.
```

Nếu không có cạnh kết nối hai threads, chỉ riêng việc một thread được `sleep` lâu hơn thread kia không xây được đường HB ấy.

## 3. Bộ nhớ dùng chung: biến nào có thể rơi vào data race?

JLS `17.4.1` xem **instance fields, static fields và array elements** là shared variables có thể được nhiều threads truy cập. Local variables nằm trong frame riêng của thread không trực tiếp là shared variables theo nghĩa đó; tuy nhiên local references có thể trỏ tới một **object shared**.

Ví dụ:

```java
static int counter;

static void increment() {
    counter++;
}
```

Đây là fragment. Nếu hai threads cùng gọi `increment` mà không có synchronization:

- Cả hai có thể đọc cùng giá trị ban đầu.
- Mỗi thread tính cộng 1.
- Cả hai ghi kết quả, làm **lost update**.

Phép `counter++` gồm logic **read-modify-write**, không phải phép toán nguyên tử được đảm bảo bởi JMM chỉ vì nó là một Java statement.

### Định nghĩa data race chặt chẽ hơn “hai threads cùng chạy”

Hai accesses **conflict** nếu cùng variable và ít nhất một là write. Có data race khi các accesses conflict ấy **không được sắp thứ tự bởi happens-before**.

Không phải cứ có hai threads chạm một object là data race:

- Cả hai chỉ **đọc** một field immutable/safely published: không conflict write.
- Mọi truy cập shared state được bảo vệ nhất quán bằng **cùng intrinsic monitor**: có synchronization thích hợp.
- Một thread ghi trước `Thread.start()` và thread mới đọc sau khi start: có HB edge đặc thù.
- Một thread hoàn tất, thread khác `join()` thành công rồi đọc: có HB edge.

Ngược lại, việc code “thực tế có vẻ không bao giờ xảy ra đồng thời” nhờ `sleep` **không** tự đưa ra một synchronization protocol đúng.

## 4. Reordering: khác thứ tự câu lệnh nhưng vẫn hợp lệ

JLS nêu ví dụ mô hình với hai biến ban đầu bằng 0:

```text
Khởi tạo: A = 0, B = 0

Thread 1:            Thread 2:
    r2 = A;              r1 = B;
    B = 1;               A = 2;
```

Khi không có synchronization giữa các threads, có thể có kết quả khiến người đọc nghĩ các ghi/đọc “đảo thứ tự”. Các compiler và CPU được phép tối ưu nếu vẫn tuân thủ JMM, trong đó **happens-before và causality constraints** ngăn một số execution bất hợp lệ.

**Không cần có một dòng assembly move thật sự** để quan sát kết quả bất ngờ. Nhiều lý do có thể góp phần: JVM optimizer hoán đổi thứ tự operations khi không ảnh hưởng single-thread behavior, hardware store buffers, visibility delays và scheduler interleavings.

### Không đánh đồng source instruction với machine instruction

Một phép `int x = obj.value` ở Java source có thể tương ứng các bytecode actions và machine instructions thay đổi theo JIT tier. Compiler có thể inline, constant-fold hoặc giữ dữ liệu ở registers khi được phép. Ngược lại, một volatile access có thể buộc machine-level constraints phù hợp mà source `volatile` không thể hiện thành “một dòng fence” cố định.

Vì vậy đọc `javap -c` chỉ biết **class-file bytecode**, chưa chứng minh machine operations đã chạy theo một order nhất định.

## 5. Litmus nổi tiếng: hai threads đều thấy 0

Giả sử `x = y = 0` và mỗi thread chạy:

```text
Thread A:            Thread B:
    x = 1;               y = 1;
    r1 = y;              r2 = x;
```

**Kết quả `r1 == 0 && r2 == 0` là một outcome được JMM cho phép** trong litmus không đồng bộ này. Nó có thể xảy ra dù mỗi thread thấy chương trình mình chạy “đúng”, vì **không có HB** buộc mỗi thread thấy ghi của thread kia đúng lúc.

Không được suy ra:

- Cứ chạy là nhận `(0, 0)`.
- Nếu thử một triệu lần không thấy `(0, 0)` thì đoạn code đã an toàn.
- JVM nhất thiết đã đảo thứ tự hai statements trong generated machine code.
- Tất cả cặp số tùy tiện đều được phép. JMM có constraints về source values, well-formed executions và causality.

### Java 17 hoàn chỉnh: một lần thử nhưng output không cố định

Tạo file **`ReorderingLitmus.java`**:

```java
public class ReorderingLitmus {
    static int x, y;
    static int r1, r2;

    public static void main(String[] args)
            throws InterruptedException {
        x = y = r1 = r2 = 0;

        Thread a = new Thread(() -> {
            x = 1;
            r1 = y;
        }, "A");

        Thread b = new Thread(() -> {
            y = 1;
            r2 = x;
        }, "B");

        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("r1=" + r1 + ",r2=" + r2);
    }
}
```

```bash
javac --release 17 ReorderingLitmus.java
java ReorderingLitmus
```

**Stdout không xác định duy nhất**; ví dụ có thể là:

```text
r1=0,r2=1
```

Một execution khác có thể in `r1=1,r2=0`, `r1=1,r2=1`, hoặc outcome `r1=0,r2=0` mà JMM cho phép. Dòng ví dụ **không phải một lần chạy đã được đo**. Chạy test này nhiều lần không đủ làm phép chứng minh formal; frequency của từng outcome phụ thuộc machine/JVM/scheduler/JIT.

### Vì sao `join()` không “sửa” race giữa A và B?

`join` bảo đảm khi main đọc `r1,r2` sau khi hai threads kết thúc, những writes đã xảy ra trong A/B được main quan sát thích hợp. Nhưng nó **không tạo cạnh HB** từ `x = 1` của A sang `r2 = x` của B hay từ `y = 1` của B sang `r1 = y` của A. Hai worker threads vẫn có racy accesses.

## 6. Happens-before: thứ cần tìm thay vì đoán CPU timing

Các nguồn HB quan trọng trong JLS 17:

| Tình huống | Cạnh đồng bộ và điều được bảo đảm |
| --- | --- |
| Một thread tự thực hiện `A` rồi `B` | `hb(A, B)` bởi program order |
| Thread A `unlock(M)`, thread B `lock(M)` tiếp theo theo synchronization order | Unlock **synchronizes-with** subsequent lock của **cùng M** |
| A `volatileField = value`, B đọc `volatileField` sau đó theo synchronization order | Volatile write **synchronizes-with** subsequent reads cùng variable |
| A gọi `t.start()` | `start` synchronizes-with first action của thread t |
| A `join()` thread t và phát hiện t terminated | Actions của t happen-before continuation của A sau successful join |
| Thread A `interrupt` B và B phát hiện interrupt | Có synchronization edge được JLS quy định |
| Đối tượng được khởi tạo mặc định | Default-initialization writes có HB đến các actions thích hợp |
| `final` field initialization | Có quy tắc freeze đặc thù ở `17.5` khi construction hợp lệ |

**Không thuộc danh sách bảo đảm synchronization tự động:** `Thread.sleep`, `Thread.yield`, gọi `System.nanoTime`, `println` ở các đoạn không được thiết kế làm protocol, busy-wait đọc plain field, `Thread.onSpinWait`.

Một API thư viện có thể định nghĩa memory-consistency effects riêng (ví dụ `CountDownLatch`, `BlockingQueue`, `Future`). Hãy dựa vào **contract API**, không phỏng đoán việc bên trong API có dùng lock hay không.

## 7. Vì sao `sleep` KHÔNG tạo happens-before?

JLS Java SE 17 **`17.3 Sleep and Yield`** nêu rõ:

- `Thread.sleep` làm thread hiện tại **ngưng thực thi tạm thời** theo khoảng thời gian yêu cầu, chịu giới hạn độ chính xác của timers/scheduler.
- Sleep **không nhả monitors** mà thread đang sở hữu.
- `Thread.sleep` và `Thread.yield` **không có synchronization semantics**.
- JVM không bắt buộc flush writes từ registers ra shared memory trước sleep hoặc reload values từ shared memory sau sleep.

Hệ quả: code kiểu sau **không đúng**:

```java
// Fragment KHÔNG an toàn (chỉ minh họa, không nên chạy vô hạn)
class BrokenWorker {
    boolean done; // KHÔNG volatile

    void waitUntilDone() throws InterruptedException {
        while (!done) {
            Thread.sleep(100);
        }
    }
}
```

Thread khác có thể viết `done = true`, nhưng waiter **không được bảo đảm quan sát thay đổi**. JLS minh họa compiler có thể tái sử dụng giá trị đã đọc trong loop. Sleep **không phải polling-with-visibility guarantee**.

Một chương trình chạy thành công 100 lần chỉ cho thấy một nhóm interleavings, không tạo contract cross-thread.

### `sleep(0)` hoặc `yield()` càng không giúp

`Thread.sleep(0)` không phải `VarHandle.fullFence`; `Thread.yield()` chỉ là scheduling hint và có thể bị scheduler bỏ qua. Không có quy tắc JMM biến việc “nhường CPU” thành release–acquire hay memory fence công khai.

### `sleep` không phải cơ chế đảm bảo thứ tự

```java
Thread worker = new Thread(() -> {
    // doSomething();
});
worker.start();
Thread.sleep(100);
 // Không được mặc định worker đã hoàn tất.
```

Đây là fragment trong method throws/catch `InterruptedException`. Nếu cần chờ thread **hoàn thành**, dùng `join`, `Future.get` hoặc cơ chế completion tương đương. Nếu cần chờ một **điều kiện mutable**, dùng `volatile` đúng cách, `wait/notify`, `CountDownLatch`, `BlockingQueue` hoặc abstraction concurrency phù hợp.

## 8. Sleep cũng không nhả monitor: minh họa chạy được

Tạo file **`SleepKeepsMonitorDemo.java`**:

```java
public class SleepKeepsMonitorDemo {
    public static void main(String[] args)
            throws InterruptedException {
        Object lock = new Object();

        synchronized (lock) {
            System.out.println("beforeSleep="
                    + Thread.holdsLock(lock));
            Thread.sleep(20);
            System.out.println("afterSleep="
                    + Thread.holdsLock(lock));
        }

        System.out.println("afterBlock="
                + Thread.holdsLock(lock));
    }
}
```

```bash
javac --release 17 SleepKeepsMonitorDemo.java
java SleepKeepsMonitorDemo
```

**Stdout chính xác nếu không bị interrupt ngoài:**

```text
beforeSleep=true
afterSleep=true
afterBlock=false
```

Sleep có thể kéo dài **khác chính xác 20 ms** và thread có thể không được chạy lại ngay sau mốc timer. Nhưng nó **không thả monitor** trong lúc chờ; `Thread.holdsLock` vẫn true trước và sau sleep bên trong block. Khi ra khỏi synchronized block, ownership mới được release.

**Cảnh báo thực hành:** giữ monitor trong lúc ngủ làm thread khác đang cần monitor ấy phải chờ. Không dùng `Thread.sleep` để “giảm tranh chấp” ngay trong critical section; thường nó **kéo dài** contention.

## 9. `volatile` sửa bài toán visibility của flag như thế nào?

Khai báo một flag `volatile` khiến các accesses tới flag tham gia **synchronization order**. Một volatile write (release-like) trước một volatile read (acquire-like) tiếp theo của **chính field đó** tạo quan hệ synchronizes-with; từ đó transitive HB có thể chuyển các writes trước publish tới reader.

```java
class Signal {
    int payload;
    volatile boolean ready;
}
```

Mô hình:

```text
Writer thread:                 Reader thread:

payload = 42                  while (!ready) ...
ready = true  --sw--->         read ready == true
                               read payload == 42

Các writes vào payload trước volatile write được sắp
trước các reads của reader sau volatile read tương ứng.
```

**Không có nghĩa `payload` tự biến thành volatile.** Chúng được order nhờ protocol publish/read qua **cùng volatile ready** và không có các writes khác gây race lên payload trong lúc sử dụng.

### Java 17 runnable: volatile publication

Tạo file **`VolatileSignalDemo.java`**:

```java
public class VolatileSignalDemo {
    private int payload;
    private volatile boolean ready;

    private void publish() {
        payload = 42;
        ready = true;
    }

    private int awaitValue() {
        while (!ready) {
            Thread.onSpinWait();
        }
        return payload;
    }

    public static void main(String[] args)
            throws InterruptedException {
        VolatileSignalDemo signal = new VolatileSignalDemo();
        Thread producer = new Thread(signal::publish, "producer");
        producer.start();

        int result = signal.awaitValue();
        producer.join();
        System.out.println("payload=" + result);
    }
}
```

```bash
javac --release 17 VolatileSignalDemo.java
java VolatileSignalDemo
```

**Stdout sau khi cả hai threads tiến triển và không gặp sự cố runtime:**

```text
payload=42
```

Reader chỉ đọc `payload` sau khi quan sát `ready=true` từ volatile. Vì `payload=42` theo program order trước volatile write và reader đọc ready trước payload, có HB qua volatile.

**Đây là demo spin-wait để giải thích JMM, không phải mẫu waiting production**: nó đốt CPU, JVM/scheduler không cam kết một deadline mà thread producer phải được chạy, và có thể kéo dài vô hạn nếu producer không được scheduling. `Thread.onSpinWait` chỉ là hint cho CPU/runtime; **nó không thay thế volatile** và không tự tạo HB. Trong production, cân nhắc `CountDownLatch`, `BlockingQueue` hoặc `CompletableFuture`.

## 10. `volatile` không biến `count++` thành atomic

Sai lầm thường gặp:

```java
volatile int count;

void increment() {
    count++; // vẫn là read + add + write
}
```

Đây là fragment. `volatile` áp đặt các quy tắc visibility/ordering và atomicity của **từng volatile read/write**, nhưng phép read–modify–write không trở thành transaction nguyên tử.

Hai threads vẫn có thể:

```text
count ban đầu = 7
A read count => 7
B read count => 7
A write 8
B write 8

Kết quả = 8, dù có hai increment operations.
```

Nếu nhiều threads cùng tăng counter, dùng `synchronized`/`ReentrantLock` hoặc `AtomicInteger.incrementAndGet()` theo yêu cầu. `LongAdder` phù hợp một số thống kê throughput cao nhưng semantics của `sum()` dưới concurrent updates khác với một atomic snapshot.

### Ví dụ `AtomicInteger` có kết quả xác định

Tạo file **`AtomicIncrementDemo.java`**:

```java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicIncrementDemo {
    public static void main(String[] args)
            throws InterruptedException {
        AtomicInteger count = new AtomicInteger();
        Runnable job = () -> {
            for (int i = 0; i < 10_000; i++) {
                count.incrementAndGet();
            }
        };

        Thread a = new Thread(job, "A");
        Thread b = new Thread(job, "B");
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("count=" + count.get());
    }
}
```

```bash
javac --release 17 AtomicIncrementDemo.java
java AtomicIncrementDemo
```

**Stdout khi threads kết thúc bình thường:**

```text
count=20000
```

`incrementAndGet` cung cấp atomic read-modify-write; `join` còn cho main biết hai worker threads đã kết thúc và mọi updates được quan sát đúng.

## 11. `synchronized`: mutual exclusion và happens-before

`synchronized(lock)` không chỉ làm hai threads không thể cùng sở hữu **một monitor**. Theo JMM, **unlock của monitor M** synchronizes-with **mọi lock tiếp theo** của chính M trong synchronization order.

```java
class Box {
    int value;

    synchronized void write(int x) {
        value = x;
    }

    synchronized int read() {
        return value;
    }
}
```

Cả hai instance methods dùng monitor **`this`**. Khi reader chạy sau writer đã unlock **cùng instance** và không có write khác, reader thấy update phù hợp. Nếu gọi methods trên **hai Box objects khác nhau**, không có chung monitor để bảo vệ một shared state chung.

### Mẫu dùng synchronized không cần volatile riêng

Tạo file **`MonitorStateDemo.java`**:

```java
public class MonitorStateDemo {
    static final class Box {
        private int value;

        synchronized void set(int v) {
            value = v;
        }

        synchronized int get() {
            return value;
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Box box = new Box();
        Thread writer = new Thread(() -> box.set(42));
        writer.start();
        writer.join();

        System.out.println("value=" + box.get());
    }
}
```

```bash
javac --release 17 MonitorStateDemo.java
java MonitorStateDemo
```

**Stdout:**

```text
value=42
```

Ví dụ có **hai nguồn HB**: writer unlock→main get lock của cùng Box, và writer completion→main successful `join`. Vì vậy không dùng nó để chứng minh riêng synchronized là cạnh duy nhất; nó cho thấy pattern hợp lệ và kết quả xác định.

Muốn kiểm chứng locking correctness không dựa vào sleeps, có thể thêm nhiều readers/writers cùng dùng Box, dùng barrier khởi động và assertions về invariants, nhưng scheduling và throughput sẽ không xác định.

## 12. `Thread.start` tạo HB mà sleep không tạo được

JLS `17.4.4–17.4.5` xác định gọi `t.start()` synchronizes-with action đầu tiên trong thread `t`. Tất cả actions được thực hiện bởi thread khởi chạy **trước start** theo program order có đường HB tới actions của thread mới.

Ví dụ:

```java
int[] data = {1, 2, 3};
Thread worker = new Thread(() ->
        System.out.println(data[0] + data[1] + data[2]));
worker.start();
```

Đây là fragment. Mảng `data` được tạo và điền trước `start`, thread mới được bảo đảm thấy initialization phù hợp. **Không cần sleep để “đợi mảng xuất hiện trong RAM”**.

Tuy nhiên `start()` **không** tự đồng bộ các writes xảy ra **sau** `start` từ parent sang worker. Nếu parent còn sửa `data` sau start trong khi worker đọc mà không có synchronization, vẫn có thể có data race.

### `join` tạo chiều ngược lại

Khi main gọi `worker.join()` và thành công trở lại sau khi worker kết thúc, actions của worker happen-before những actions tiếp theo trong main.

```text
Main:  initialize input
       start(worker) --sw---> Worker đọc input
                                   |
                                   | tính result = 42
                                   v
Main:  join(worker) thành công <-- thread termination
       read result (đảm bảo visibility)
```

`Thread.sleep` không có vai trò đồng bộ trong cả hai hướng ấy.

## 13. Java 17: `join` giải quyết đúng thứ tự completion

Tạo file **`JoinVisibilityDemo.java`**:

```java
public class JoinVisibilityDemo {
    private static int result;

    public static void main(String[] args)
            throws InterruptedException {
        int input = 21;
        Thread worker = new Thread(() -> result = input * 2);

        worker.start();
        worker.join();

        System.out.println("result=" + result);
    }
}
```

```bash
javac --release 17 JoinVisibilityDemo.java
java JoinVisibilityDemo
```

**Stdout:**

```text
result=42
```

Field `result` không volatile nhưng main đọc **sau successful join**: JMM cung cấp HB qua termination/join. Viết `Thread.sleep(100)` thay cho `join` sẽ **không** có cùng bảo đảm, kể cả nếu trên máy thử worker thường hoàn tất trong vài milliseconds.

### `join(timeout)` cần thêm điều kiện

Nếu dùng `worker.join(100)`, lời gọi có thể trở lại khi hết thời hạn dù worker còn chạy. Không thể chỉ vì `join(timeout)` returned mà giả định thread đã terminated. Kiểm tra `isAlive()`/completion condition phù hợp và semantics của API. Thực hành đơn giản nhất khi cần đợi kết thúc chắc chắn là `join()` không timeout, có chiến lược interruption/cancellation phù hợp.

## 14. CountDownLatch: thay sleep bằng một tín hiệu có contract

`CountDownLatch` có định nghĩa **memory consistency effects**: những actions trong thread trước khi gọi `countDown()` happen-before actions của một thread khác sau khi `await()` tương ứng thành công.

Tạo file **`LatchPublicationDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;

public class LatchPublicationDemo {
    private static int payload;
    private static String label;

    public static void main(String[] args)
            throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);

        Thread writer = new Thread(() -> {
            payload = 42;
            label = "prepared";
            ready.countDown();
        }, "writer");
        writer.start();

        ready.await();

        System.out.println("payload=" + payload);
        System.out.println("label=" + label);
        writer.join();
    }
}
```

```bash
javac --release 17 LatchPublicationDemo.java
java LatchPublicationDemo
```

**Stdout khi writer hoàn tất bình thường:**

```text
payload=42
label=prepared
```

Vì writer ghi `payload`, `label` rồi `countDown()`, main `await()` thành công trước khi đọc: **HB đã được thư viện concurrency cam kết**, dù hai fields thông thường không volatile.

Điểm khác biệt với sleep:

| Sleep | CountDownLatch |
| --- | --- |
| Chờ một khoảng thời gian tương đối | Chờ một **sự kiện** count đạt 0 |
| Không biết producer đã chuẩn bị xong chưa | `await()` thành công xác nhận latch đã mở |
| Không cung cấp HB do chính sleep | Có memory-consistency edge được API quy định |
| Không đảm bảo scheduler chạy producer | Cũng không hứa deadline, nhưng protocol sự kiện là đúng khi producer tiến triển |
| Không có kết quả điều kiện nghiệp vụ | Có completion signal hợp lệ |

`CountDownLatch` dùng một lần; nếu cần barrier lặp lại qua nhiều phases, dùng `CyclicBarrier`/`Phaser` hoặc thiết kế workflow khác.

## 15. `wait/notify`: khác `sleep` ở quyền sở hữu monitor

Một thread đang trong `synchronized(lock)` có thể gọi `lock.wait()` khi giữ monitor:

- `wait()` **nhả monitor của chính object `lock`** và chuyển thread sang wait set theo quy tắc API.
- Khi thức dậy (notification, interruption, timeout/spurious wakeup theo semantics), nó phải **tái acquire monitor** trước khi tiếp tục/throw phù hợp.
- `notify()`/`notifyAll()` cần caller đang sở hữu monitor; việc notify không tự nhả monitor ngay lập tức.
- Luôn dùng `while (!condition) lock.wait()`, vì thức dậy không đảm bảo business condition đã đúng.

```java
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
    consumePayload();
}
```

Đây là fragment trong method có `throws InterruptedException` hoặc catch phù hợp, và `lock`, `ready`, `consumePayload` được khai báo.

**`sleep` khác hoàn toàn:** ngủ bên trong synchronized giữ monitor, không tạo event-notification protocol và không bảo đảm field plain `ready` sẽ được reload.

## 16. Java 17: wait/notifyAll có điều kiện và output ổn định

Tạo file **`WaitConditionDemo.java`**:

```java
public class WaitConditionDemo {
    static final class Mailbox {
        private boolean ready;
        private int data;

        synchronized void publish(int value) {
            data = value;
            ready = true;
            notifyAll();
        }

        synchronized int receive() throws InterruptedException {
            while (!ready) {
                wait();
            }
            return data;
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Mailbox box = new Mailbox();
        Thread writer = new Thread(() -> box.publish(42));
        writer.start();

        int observed = box.receive();
        writer.join();
        System.out.println("observed=" + observed);
    }
}
```

```bash
javac --release 17 WaitConditionDemo.java
java WaitConditionDemo
```

**Stdout khi hoàn tất bình thường:**

```text
observed=42
```

- Nếu writer publish trước, `ready` đã true và reader không cần wait.
- Nếu reader tới trước, nó `wait` và nhả monitor cho writer, rồi tái acquire sau notify.
- `data` plain field vẫn an toàn theo **monitor ordering**: cả writes và reads được thực hiện trong synchronized methods của **cùng Mailbox instance**.
- `writer.join()` sau `receive` không phải lý do duy nhất reader thấy data; điều kiện và monitor protocol đã xử lý an toàn.

Đừng dùng `if (!ready) wait()`: phải chịu **spurious wakeups** và kiểm tra lại điều kiện sau khi reacquire.

## 17. Vậy một “data-race-free” chương trình được bảo đảm điều gì?

JLS `17.4.5` nêu nguyên tắc nổi bật: một chương trình **correctly synchronized** (không có data races trong các executions theo sequential consistency) cho kết quả có thể hiểu theo **sequential consistency**. Nói thực dụng: nếu mọi shared mutable state có synchronization protocol đúng, bạn không cần đoán compiler/CPU đã hoán đổi machine instructions nào để suy ra các giá trị racy bất ngờ.

**Sequential consistency** nghĩa tồn tại một total order các actions của nhiều threads mà mỗi thread vẫn giữ program order của nó và reads thấy các writes phù hợp với thứ tự đó.

Nhưng tránh ba nhầm lẫn:

- **Không** có nghĩa thread A chạy toàn bộ trước thread B; actions từ nhiều threads có thể interleave.
- **Không** có nghĩa scheduler công bằng hoặc không thể deadlock; JMM là mô hình memory behavior, không bảo đảm liveness.
- **Không** có nghĩa mọi method call bất kỳ tự atomic; critical section/atomic operations phải bao đúng invariant.

Một program data-race-free vẫn có thể có **logical race** (ví dụ hai clients đặt hàng cuối cùng khi nghiệp vụ không đặt đủ lock/transaction), hoặc deadlock vì thứ tự locks ngược, dù riêng memory visibility có thể tuân theo JMM.

## 18. Data race trong Java không có nghĩa “mọi thứ đều có thể xảy ra”

Không nên lấy định nghĩa `undefined behavior` của C/C++ để áp máy móc lên Java. JMM vẫn giới hạn tập executions hợp lệ:

- Compiler phải bảo toàn intra-thread semantics phù hợp.
- Các reads có ràng buộc về writes mà chúng có thể quan sát theo JMM.
- Volatile, locks, thread start/join và các synchronization actions phải tuân thủ ordering.
- `final` field initialization có các quy tắc đặc biệt.
- **Causality requirements** ngăn một số giá trị “out of thin air”.

Tuy nhiên, **không có HB** cho conflicting plain accesses khiến kết quả có thể khác một sequentially consistent trace và ứng dụng không được dựa vào sự “thường thấy” trên phần cứng.

Đừng thiết kế correctness dựa vào chuyện “x86 có strong memory ordering nên plain field chắc ổn”, hoặc “JDK HotSpot của tôi vẫn đọc lại biến sau mỗi `sleep`”. JVM phải portable qua compiler optimizations, CPU architectures và updates.

## 19. Safe publication: xuất bản object graph đúng cách

Một shared object có thể được dựng đúng bên thread A nhưng thread B cần **một sự kiện publish** hợp lệ để nhận những writes trước đó.

Mẫu volatile reference:

```java
final class Config {
    final int port;
    final String host;

    Config(int port, String host) {
        this.port = port;
        this.host = host;
    }
}

class Holder {
    volatile Config current;

    void publish() {
        Config cfg = new Config(8080, "localhost");
        current = cfg;
    }
}
```

Đây là fragments, không có `main`. Khi thread khác đọc `holder.current` qua volatile và thấy reference được publish, các writes trước volatile write có các HB guarantees phù hợp. **Không sửa `cfg` sau publish** nếu muốn giữ một immutable snapshot ổn định, trừ khi có synchronization khác.

Ngoài volatile, safe publication có thể đạt bằng:

- Khởi tạo trước `Thread.start` rồi truyền object cho worker.
- Guard publication và consumption bằng cùng monitor.
- Thread-safe concurrent collections/queues với API memory-consistency effects.
- `CountDownLatch`/`Future`/`CompletableFuture` theo đúng contract.
- Class initialization: JVM bảo đảm initialization synchronization phù hợp.
- Final field semantics khi object được xây dựng đúng và `this` không thoát sớm.

### Safe publication không tự làm object mutable thread-safe vĩnh viễn

Sau khi một reference được publish an toàn, nếu nhiều threads vẫn sửa non-final fields của object mà không có protocol mới, **data races vẫn có thể xảy ra**. Safe publication giúp người đọc thấy trạng thái **tại thời điểm publish** trong những điều kiện phù hợp; các mutations sau đó cần synchronization thích hợp.

## 20. `final` fields và constructor: lợi ích nhưng có giới hạn

JLS `17.5` quy định **final field semantics** đặc biệt: khi object được constructed đúng và `this` không escape khỏi constructor, các final fields có những bảo đảm initialization ngay cả khi reference được chuyển tới thread khác mà không có synchronization đầy đủ.

```java
final class Message {
    final int code;
    final String text;

    Message(int code, String text) {
        this.code = code;
        this.text = text;
    }
}
```

Đây là class fragment, không có `main`. `final` giúp bảo vệ trạng thái đã xây dựng đúng nhưng không đồng nghĩa mọi mutable object con sâu trong graph **tự động thread-safe** hoặc mọi hành vi sau initialization là an toàn.

Đặc biệt tránh **this escape**:

```java
// Sai thiết kế: xuất bản this trong constructor.
class BadPublication {
    static BadPublication shared;
    final int number;

    BadPublication() {
        shared = this;
        number = 42;
    }
}
```

Đây là snippet minh họa **cách làm không an toàn**, không phải chương trình được khuyến nghị. Nếu reference thoát trước khi final fields được gán/constructor hoàn thành, các bảo đảm initialization mong muốn có thể bị phá.

Không viết rằng “đọc một final field luôn thấy 0 nếu race”; cũng không viết rằng “final thay được synchronized cho mọi mutable state”. Hãy đọc đúng `JLS 17.5`.

## 21. `volatile` và compound invariants: visibility không đủ

Giả sử:

```java
class Range {
    volatile int min;
    volatile int max;

    void update(int low, int high) {
        min = low;
        max = high;
    }
}
```

Hai fields `volatile` bảo đảm các reads/writes riêng có ordering, nhưng reader có thể quan sát **min mới, max cũ** nếu thread khác đọc giữa hai writes. Nếu cần invariant `min <= max` luôn được quan sát theo một snapshot nhất quán, thiết kế phải dùng synchronized, immutable snapshot qua **một volatile reference**, hoặc atomic protocol cho toàn bộ trạng thái.

### Sửa bằng immutable snapshot

```java
record RangeSnapshot(int min, int max) {}

class RangeHolder {
    private volatile RangeSnapshot snapshot =
            new RangeSnapshot(0, 0);

    void set(int low, int high) {
        snapshot = new RangeSnapshot(low, high);
    }

    RangeSnapshot get() {
        return snapshot;
    }
}
```

Đây là fragment Java 17. Một volatile reference update publish **cả object immutable snapshot** theo một hành động read/write reference nguyên tử; reader dùng một local `RangeSnapshot r = holder.get()` rồi đọc cả `r.min()` và `r.max()` để có values thuộc **cùng snapshot**.

Nếu caller gọi `holder.get()` **hai lần khác nhau**, nó có thể đọc hai versions khác nhau; không biến composite read thành snapshot nếu không dùng cùng local reference.

## 22. `sleep` không bảo đảm deadline, fairness hay thread completion

Ngoài memory visibility, `Thread.sleep` không có các guarantees sau:

| Mong muốn | `sleep` có đảm bảo? | Thay bằng gì khi cần |
| --- | --- | --- |
| Thread khác đã chạy tới một statement cụ thể | **Không** | `CountDownLatch`/barrier/handshake |
| Thread khác đã kết thúc | **Không** | `join`/`Future.get` |
| Các writes trong thread khác đã visible | **Không chỉ vì sleep** | HB qua volatile/locks/latch/join |
| Nhả monitor trong lúc ngủ | **Không** | `Object.wait`/`Condition.await` khi phù hợp |
| Ngủ chính xác N milliseconds | **Không** | Scheduler/timer có giới hạn; xử lý timeout bằng deadlines/clock |
| Nhường CPU cho thread được chọn | **Không** | Scheduler quyết định; `yield` chỉ hint |
| Đảm bảo một thread cuối cùng được schedule | **Không** | Liveness fairness không phải contract của sleep |
| Đảm bảo composite operation atomic | **Không** | Atomic API/lock/transaction |
| Bảo vệ queue và tránh mất notifications | **Không** | BlockingQueue/Condition/monitor protocol |
| Tránh deadlock khi đang giữ locks | **Không** | Lock ordering, timeouts, quản lý critical section |

Trong test concurrency, `Thread.sleep(100)` “để thread A chạy trước B” thường là **flaky test pattern**. Trên CI chậm, thread B có thể chưa được CPU cho tới sau 100 ms; trên máy nhanh, B có thể vượt A ngay lập tức. Thiết kế test phải dùng latch/barrier để đặt đúng interleaving hoặc điều kiện cần quan sát.

## 23. Hai ví dụ kiểm thử: race condition và đồng bộ sự kiện

### Sai: ngủ để chắc producer đã ghi xong

```java
// Mã minh họa sai, KHÔNG có output cố định.
static int result;

static void broken() throws InterruptedException {
    new Thread(() -> result = 42).start();
    Thread.sleep(100); // không chứng minh writer finished
    System.out.println(result);
}
```

Thread có thể chưa được schedule, nên main có thể thấy `0`; kể cả trong runs writer đã thực hiện write trước khi main đọc theo wall-clock, vẫn thiếu HB cho field plain `result` nếu không có synchronization.

### Đúng: chờ producer hoàn thành

```java
static int result;

static void corrected() throws InterruptedException {
    Thread t = new Thread(() -> result = 42);
    t.start();
    t.join();
    System.out.println(result); // 42 nếu thread completed bình thường
}
```

Đây là fragments. `join` tạo HB từ actions của producer tới main sau successful join. Nếu producer throw exception trước assignment hoặc bị cancel, phải xử lý completion/failure theo business protocol; `join` không tự bảo đảm business work thành công.

### Đúng: chờ event “dữ liệu đã sẵn sàng” thay vì cả thread kết thúc

Dùng `CountDownLatch` khi producer còn có công việc khác sau khi dữ liệu ready; dùng `BlockingQueue` nếu producer/consumer trao nhiều messages; dùng `CompletableFuture`/`Future` nếu cần truyền value hoặc failure.

## 24. Thread interruption: sleep bị interrupt khác synchronization protocol

`Thread.sleep` có thể ném `InterruptedException` nếu thread bị interrupt. Khi ném exception đó, **interrupted status được clear** theo API `Thread.sleep`. Nhưng hành động `interrupt` và phát hiện interrupt có thể tham gia **synchronizes-with** theo JLS. Đây là synchronization từ **interrupt protocol**, **không phải bản thân sleep**.

Hai ý cần tách:

1. **`Thread.sleep` không có synchronization semantics** riêng.
2. Một event khác như `interrupt()/InterruptedException` có những semantics được JMM quy định.

Nếu bắt `InterruptedException` nhưng không thể xử lý, thường nên khôi phục trạng thái interrupt:

```java
try {
    Thread.sleep(100);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
```

Đây là fragment. Khôi phục interrupt là một quyết định về **cancellation contract**; trong code có API ném `InterruptedException` ra caller, nhiều khi tốt hơn là propagate. Không dùng loop nuốt exception rồi tiếp tục sleep vô hạn khiến worker không thể shutdown sạch.

## 25. Busy-wait, `Thread.onSpinWait` và backoff không thay JMM

Một loop:

```java
while (!ready) {
    Thread.onSpinWait();
}
```

là fragment **chỉ đúng visibility nếu `ready` đã được đồng bộ phù hợp**, chẳng hạn volatile. `Thread.onSpinWait()` là **performance hint** cho ngắn hạn spin-wait loops, không tự đặt memory fence/happens-before giữa producer và consumer.

Các kỹ thuật khác như exponential backoff và `LockSupport.parkNanos` phục vụ scheduling/CPU usage nhưng **không thay thế protocol truyền trạng thái**. Khi dùng `park/unpark` cần tuân thủ semantics cấp phép của API; không suy mọi plain field write xung quanh bất kỳ sleep/park đều visible một cách phổ quát.

Một busy-wait với non-volatile flag có thể bị JIT tối ưu làm hoist field read ra ngoài loop khi các constraints cho phép. Thêm sleep vào giữa không bắt compiler phải reload theo JLS.

## 26. Lưu ý về `long`/`double` và atomicity của một access

JLS `17.7` cho phép những **non-volatile `long`/`double` reads/writes** được xử lý như hai 32-bit halves trong những trường hợp mà specification mô tả; vì vậy portability code không nên mặc định mọi nonvolatile 64-bit primitive access đều atomic như volatile.

Đồng thời:

- **Volatile `long` và `double`** có atomicity được JMM đảm bảo cho các accesses đơn lẻ phù hợp.
- Atomicity của **một write/read** khác với atomicity của `counter++`.
- Một số HotSpot/hardware configurations thực tế thực hiện nonvolatile 64-bit access atomically, nhưng không thể lấy điều đó làm universal Java SE guarantee.
- Thêm volatile không làm operations gồm nhiều fields thành transaction nguyên tử.

Không dùng mô hình “tất cả races chỉ có stale reads” — còn có lost updates, inconsistent snapshots và các reordering effects.

## 27. Khi nào memory barrier/fence thật sự cần được nắm?

`volatile`, intrinsic locks, atomics và thư viện concurrency được JVM hiện thực bằng tập cơ chế thấp hơn, có thể gồm compiler barriers, CPU fences, atomic read-modify-write instructions và cache-coherence behavior. Nhưng JMM là **semantic contract**, không phải danh sách instruction assembly cố định.

Ví dụ:

- Trên một CPU architecture, compiler có thể cần machine fence để đáp ứng volatile ordering.
- Trên architecture khác, một số ordering có thể tự được bảo đảm bởi instruction/store model, nên codegen khác.
- JIT có thể gộp, di chuyển hoặc loại các barriers không cần thiết nếu vẫn giữ Java semantics.
- Bytecode `monitorenter`/`monitorexit` là cơ chế synchronization ở class-file level; JIT code có thể dùng fast paths hoặc lock elimination trong điều kiện hợp lệ.
- VarHandle modes (`getAcquire`, `setRelease`, `getVolatile`, `setVolatile`) có contracts riêng, không phải mọi mode tương đương full volatile.

Vì vậy **đừng đọc JVM assembly từ một máy rồi viết kết luận universal về Java ordering**. Khi cần correctness, chọn public abstraction đúng (`volatile`, synchronized, atomic, concurrent collections). Khi cần tối ưu low-level, nghiên cứu `VarHandle` memory-order modes và đo theo JDK/hardware cụ thể.

## 28. Một lỗi phổ biến: giữ lock khi `sleep` gây starvation

Giả sử:

```java
synchronized (lock) {
    updateState();
    Thread.sleep(500);
}
```

Đây là fragment trong method throws/catch `InterruptedException`. Sleep **không thả monitor**, nên thread khác cần `lock` có thể bị block suốt 500 ms hoặc lâu hơn. Nếu lặp lại nhiều lần dưới lock, throughput giảm và tail latency tăng.

Sửa hướng thiết kế nếu invariant cho phép:

```java
synchronized (lock) {
    updateState();
}
Thread.sleep(500);
```

Nhưng **không di chuyển sleep ngoài lock chỉ theo cảm tính** nếu business protocol đòi một thao tác atomic hoặc condition waiting; khi đó có thể dùng `wait/notify` hoặc `Condition.await`. Điều phải xác định trước là **cái gì cần mutual exclusion** và **khi nào release lock an toàn**.

## 29. Điều gì `System.out.println` có thể che giấu trong race demo?

Một số demo concurrency dùng `System.out.println` liên tục giữa threads và kết luận “không race” vì output có vẻ tuần tự. Thực tế, implementation của `PrintStream` và I/O có thể có synchronization hoặc scheduling side effects, làm interleavings thay đổi.

Không dựa vào nó để suy plain field accesses ở những paths khác đã được đồng bộ đúng. Ngược lại, println chỉ trong **main sau join** như `ReorderingLitmus` không sửa data race giữa hai worker threads.

Thực hành benchmark/race tests:

- Tách sampling/output khỏi hot critical path.
- Dùng tests stress có barriers để tạo concurrency, không sleep cố đoán.
- Không đồng nhất “stress test không fail” với proof.
- Dùng formal JMM reasoning hoặc công cụ concurrency testing chuyên dụng cho cases nhạy cảm.
- Đối chiếu interpreter/JIT/hardware configurations mà không dựa vào một CPU duy nhất.

## 30. Checklist code review về sleep, ordering và race

Trước khi merge code nhiều threads, trả lời các câu sau:

1. **Biến nào shared?** Instance field, static field, array element, hoặc object graph được publish?
2. Có **conflicting accesses** không (ít nhất một write)?
3. Hai accesses có HB edge qua **cùng volatile variable**, **cùng monitor**, `start`/`join` hay API concurrency contract không?
4. Có phép `count++`, `check-then-act` hoặc hai writes cùng tạo invariant cần **atomicity** không?
5. Có dùng `Thread.sleep` để “đợi worker xong” thay vì `join`/`Future`?
6. Có dùng `Thread.sleep` trong synchronized block khiến thread khác chờ lâu?
7. Có spin-loop trên **plain boolean** và tưởng sleep/onSpinWait sẽ flush cache?
8. Có một `volatile` flag publish các payload writes **trước** signal, reader chỉ đọc sau acquire không?
9. Reader có còn đọc fields **trước** khi kiểm tra flag không?
10. Có writers khác sửa payload mà không đồng bộ dù đã publish?
11. Có test flaky vì dùng sleep để sắp xếp threads?
12. Có object bị **this-escape trong constructor**, phá safe-final-field publication không?
13. Có **join(timeout)** hoặc `await(timeout)` bị hiểu lầm là đã thành công dù hết hạn?
14. Có xử lý `InterruptedException` phù hợp cancellation contract?
15. Concurrent API được dùng có memory-consistency effects gì theo Java 17 docs?
16. Đã phân biệt **thread-safety**, **progress/liveness**, **fairness** và performance chưa?

## 31. Các hiểu lầm thường gặp

| Nhận định sai | Điều đúng |
| --- | --- |
| “Sleep 1 giây thì thread khác chắc đã chạy” | Scheduler/timer không bảo đảm thứ tự hoặc completion |
| “Sleep tự flush CPU caches” | JLS `17.3` nói sleep/yield **không có synchronization semantics** |
| “Sleep nhả synchronized lock” | Sleep **giữ** monitors; `Object.wait` có release/reacquire monitor đang wait |
| “Yield là memory fence” | `yield` chỉ hint scheduling, không tạo JMM HB |
| “Hai threads có thứ tự wall-clock là có HB” | Cần program-order/synchronizes-with edges thích hợp |
| “Không nhìn thấy race khi test thì không có data race” | Một lịch chạy thành công không phải proof |
| “Data race chỉ làm kết quả chậm” | Có thể gây sai giá trị, stale reads, lost updates, nontermination |
| “Java data race cho phép mọi giá trị bất kỳ” | JMM vẫn ràng buộc executions và causality |
| “volatile làm ++ nguyên tử” | Chỉ các accesses riêng; increment là read–modify–write |
| “Volatile reference làm toàn bộ object graph bất biến” | Mutations sau publication cần protocol riêng |
| “Mỗi volatile field đều tạo snapshot nguyên tử cho cả object” | Cần atomic composition hoặc immutable snapshot |
| “join chỉ chờ lịch scheduler, không giúp visibility” | Successful join tạo HB tới caller |
| “Thread.start chỉ schedule, không publish writes trước đó” | `start` có synchronizes-with edge |
| “CountDownLatch giống sleep vì đều chờ” | Latch có event protocol và memory-consistency effects |
| “wait() và sleep() giống nhau” | `wait` release/reacquire monitor; `sleep` không |
| “onSpinWait giúp plain boolean luôn được reload” | Hint CPU, không thay volatile |
| “x86 thấy ổn thì portable Java đúng” | JMM phải đúng trên JVM/hardware khác |
| “JIT tối ưu reorder tức vi phạm Java” | Reorder hợp lệ nếu vẫn tuân JMM/inter-thread constraints |
| “println làm các plain shared fields thread-safe” | I/O implementation side effects không thay protocol thích hợp |
| “Data-race-free nghĩa không bao giờ deadlock” | Deadlock/liveness khác memory consistency |
| “Chỉ cần final fields thì mọi graph mutable an toàn” | Final initialization rules có giới hạn và yêu cầu construction đúng |

## 32. Câu hỏi tự kiểm tra

**Câu 1.** Thread A ghi `done = true` vào field plain; B loop `while(!done) Thread.sleep(100)`. Có chắc B dừng?

**Không.** Không có HB/visibility guarantee; sleep không tạo synchronization, compiler có thể tái dùng giá trị cũ theo JLS.

**Câu 2.** Vì sao hai threads ghi mỗi thread một biến rồi đọc biến của nhau có thể cùng đọc 0?

Thiếu synchronization, JMM cho phép outcome `(0,0)` trong litmus ấy; việc này không chứng minh một reorder cụ thể đã xảy ra trong machine code.

**Câu 3.** Thread A gọi `t.start()` sau khi tạo mảng. Thread t có thấy giá trị đã gán trước start không?

**Có**, do HB từ actions trước start đến actions trong thread mới.

**Câu 4.** Main dùng `Thread.sleep(500)` thay `worker.join()`. Có được bảo đảm worker hoàn tất và data visible?

**Không**. Dùng successful `join` hoặc một completion primitive.

**Câu 5.** `volatile int count; count++;` có làm increment atomic không?

**Không**. Dùng `AtomicInteger.incrementAndGet` hoặc lock.

**Câu 6.** Writer ghi `payload=42` rồi `ready=true` volatile; reader quan sát ready true rồi đọc payload. Vì sao reader có thể tin payload?

Volatile write/read tạo synchronizes-with; HB bắc cầu nối payload write tới payload read, với điều kiện không có competing writes làm data race.

**Câu 7.** Sleep bên trong `synchronized(lock)` có thả lock để thread khác vào không?

**Không.** Thả khi monitor exit; `Object.wait` mới tạm release monitor được wait khi gọi đúng điều kiện.

**Câu 8.** Vì sao `while(!condition) wait()` tốt hơn `if(!condition) wait()`?

Vì spurious wakeups và cạnh tranh sau notification; phải kiểm tra lại predicate dưới cùng monitor.

**Câu 9.** `join(timeout)` return có luôn nghĩa thread đã terminated?

**Không.** Timeout có thể hết trước completion; kiểm tra trạng thái/kết quả.

**Câu 10.** Một chương trình đã data-race-free có thể deadlock không?

**Có.** Memory ordering correctness không đảm bảo progress/fairness.

**Câu 11.** `Thread.onSpinWait` có biến plain field thành volatile không?

**Không.** Nó là runtime hint, không tạo required memory consistency.

**Câu 12.** Tại sao `final` field tốt nhưng không sửa được mọi unsafe publication?

Constructor không được làm `this` escape sớm; và mutable state sau construction vẫn cần synchronization hợp lệ.

## 33. Tài liệu tham chiếu Java SE 17

- [JLS 17 `17.3` — Sleep and Yield](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.3): giải thích trực tiếp **không có synchronization semantics**, không flush/reload guarantees và sleep không thả monitors.
- [JLS 17 `17.4.1` — Shared Variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.1): instance fields, static fields, array elements.
- [JLS 17 `17.4.3` — Program Order](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.3): actions trong mỗi thread.
- [JLS 17 `17.4.4` — Synchronization Order](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.4): volatile, monitor, thread start/termination, interrupts.
- [JLS 17 `17.4.5` — Happens-before Order](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5): HB, data races, correctly synchronized programs và sequential consistency.
- [JLS 17 `17.4.8` — Executions and Causality](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.8): ràng buộc causality, không suy mọi giá trị đều hợp lệ trong race.
- [JLS 17 `17.5` — Final Field Semantics](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.5): constructor, final initialization, this-escape.
- [JLS 17 `17.7` — Non-Atomic Treatment of double and long](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.7): phân biệt nonvolatile và volatile 64-bit accesses.
- [Java SE 17 `Thread.sleep`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#sleep(long)): timeout, interrupted status, không thả monitors.
- [Java SE 17 `Thread.onSpinWait`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#onSpinWait()): performance hint, không phải volatile.
- [Java SE 17 `CountDownLatch`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): memory consistency effects của `countDown`/`await`.
- [Java SE 17 `AtomicInteger`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html): atomic read–modify–write operations.
- [Java SE 17 `Object.wait/notify`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): wait sets, monitor release/reacquisition.
- [Java SE 17 `VarHandle`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/VarHandle.html): các memory-ordering modes khi cần nghiên cứu cấp thấp.

**Ghi nhớ:** Java cho phép các phép biến đổi/reordering miễn giữ đúng **JMM**, nên một chương trình có **data race** không được bảo vệ bởi trực giác “các dòng source sẽ đến từng thread theo cùng thứ tự”. `Thread.sleep` chỉ là công cụ trì hoãn scheduler: **không đồng bộ memory, không bảo đảm worker đã chạy xong, không thả monitor, không làm plain flag thành volatile**. Muốn chương trình đúng, hãy xây dựng **happens-before** bằng `volatile`, intrinsic locks, `Thread.start/join` và concurrency APIs có contract rõ ràng; dùng atomic operations khi cần cả visibility **và** read–modify–write atomicity.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-threads-and-synchronization-011">Phân biệt deadlock, chờ khóa và JVM đang thực hiện công việc nền</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt deadlock, chờ khóa và JVM đang thực hiện công việc nền

## 1. Bài toán: thấy thread không chạy tiếp có chắc JVM deadlock?

Trong Java, một ứng dụng có thể “đứng yên”, một HTTP request có thể mất hàng chục giây, hoặc một thread dump có rất nhiều dòng **WAITING**, **TIMED_WAITING**, **BLOCKED** mà **không hề xảy ra deadlock**. Ngược lại, một deadlock thực có thể chỉ liên quan đến hai application threads; những GC threads hoặc compiler threads của JVM vẫn hoạt động bình thường.

Cần phân biệt ít nhất bốn loại hiện tượng:

1. **Deadlock:** có một vòng phụ thuộc chờ tài nguyên, trong đó các threads liên quan không thể tiến triển để nhả những locks đang giữ nếu không có can thiệp bên ngoài hoặc thay đổi điều kiện thích hợp.
2. **Lock contention / chờ khóa:** thread muốn acquire một monitor hoặc lock nhưng tạm thời thread khác đang giữ; owner vẫn có thể hoàn tất, release, cho waiter tiếp tục. Chờ khóa không tự động là deadlock.
3. **Chờ điều kiện / I/O / completion:** thread có thể đang chờ tín hiệu, một task, dữ liệu mạng hoặc timeout theo đúng thiết kế; chưa có bằng chứng về lock cycle.
4. **JVM background work:** GC, JIT compilation, reference processing, service/runtime work và những threads phục vụ JVM có thể chạy, ngủ hoặc chờ theo chu kỳ mà không biểu thị ứng dụng bị kẹt.

Tình huống giả định:

~~~text
Dịch vụ trả HTTP chậm hơn bình thường.

Thread dump lúc 10:00:
  http-worker-1     BLOCKED trên object monitor A
  http-worker-2     RUNNABLE
  background-poller WAITING trên BlockingQueue
  C2 CompilerThread RUNNABLE
  G1 concurrent GC thread RUNNABLE

Điều cần biết:
  Ai đang giữ monitor A?
  Owner đang làm gì?
  Có vòng chờ hay chỉ cần owner nhả monitor?
  Queue/poller đang chờ việc hay thiếu tín hiệu?
  GC/JIT chỉ đang chạy công việc nền hay thực sự chiếm CPU?
  HTTP chậm vì DB/network, lock, GC pause, CPU starvation, hay việc khác?
~~~

**Không kết luận deadlock chỉ từ một từ BLOCKED hoặc WAITING.** Phải dựng được một quan hệ chờ cụ thể, tìm owner và xác định nó có đường tiến triển hay không.

### Mục tiêu học tập

Sau bài, bạn cần:

- Đọc đúng **Thread.State** của Java 17: NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED.
- Phân biệt **intrinsic object monitors (synchronized)** và **ownable synchronizers** như ReentrantLock; biết khi nào thread dump/JMX có thể tìm deadlock.
- Vẽ **wait-for graph** để phân biệt một cạnh chờ thông thường với chu trình deadlock.
- Nhận diện **starvation, livelock, condition-wait hang, thread-pool starvation** và **I/O wait** không nhất thiết được deadlock detector của JVM báo.
- Biết rằng **Object.wait() tạm nhả receiver monitor**, còn **Thread.sleep()** và **LockSupport.park() không tự nhả intrinsic monitors**.
- Hiểu JVM **GC/JIT/background threads**, safepoints và tại sao một JVM vẫn dùng CPU dù business threads đang chờ.
- Dùng **jcmd Thread.print**, **jstack**, **ThreadMXBean**, **jcmd Compiler.queue**, **GC logs** và **JFR** để phân tích.
- Chạy các ví dụ Java 17+ và giải thích stdout xác định so với snapshot timing-dependent.
- Thiết kế **lock ordering, timeouts, cancellation, condition predicates** và instrumented diagnostics phù hợp.

**Phiên bản:** kiến thức cốt lõi theo Java SE 17; ví dụ/flags chẩn đoán dựa trên **HotSpot Java 17**. Các internals, thread names, GC algorithms, virtual-thread handling và diagnostic outputs khác nhau theo JDK version/vendor/OS. Java 17 chưa có virtual threads như tính năng production; Java 21+ cần đối chiếu tài liệu runtime tương ứng.

## 2. Từ “ứng dụng đứng” đến mô hình nguyên nhân

Một dịch vụ không đáp ứng request có thể vẫn có JVM tiến triển:

~~~text
Client request
      |
      v
  HTTP worker
      |
      +-- đang BLOCKED chờ synchronized(lock)
      |
      +-- đang WAITING chờ Future/get()/join()
      |
      +-- đang TIMED_WAITING do sleep/timeout
      |
      +-- RUNNABLE nhưng dùng CPU trong vòng lặp nóng
      |
      +-- RUNNABLE nhưng native socket read đang chờ I/O
      |
      +-- đang bị hạn chế CPU bởi container/OS scheduler
      |
      +-- gặp GC safepoint, allocation stalls hoặc backpressure
      |
      +-- bị kẹt trong deadlock cycle nhiều locks
~~~

Một JVM vẫn có thể ghi GC logs, chạy compiler, dọn references hoặc cập nhật metrics trong khi những threads nghiệp vụ hoàn toàn không tạo thêm kết quả.

**Cần điều tra nguyên nhân cụ thể**, không đặt nhãn “JVM deadlock” cho tất cả hiện tượng không tiến triển.

### 2.1. Safety và liveness

- **Safety:** không có kết quả sai như đọc state inconsistent, mất cập nhật, vi phạm invariant.
- **Liveness:** chương trình cuối cùng có thể tiến triển/hoàn tất trong những điều kiện được giả định.
- **Deadlock:** thất bại về liveness do vòng chờ.
- **Starvation:** một thread có thể không được cấp CPU/lock/permit đủ lâu dù threads khác vẫn tiến triển.
- **Livelock:** các threads vẫn hoạt động/chuyển trạng thái nhưng tương tác khiến không hoàn thành công việc hữu ích.
- **Condition wait kéo dài:** thread chờ predicate hoặc event chưa xảy ra; đôi khi đây là behavior mong đợi, đôi khi là bug.

Không có bảo đảm “vì chương trình thread-safe nên luôn tiến triển”: code có thể tránh data races nhưng vẫn deadlock hoặc starve.

## 3. Thread.State của Java 17: đọc đúng ý nghĩa

**Thread.State** là tập enum states của Java-level thread. Đây **không phải** các trạng thái native kernel scheduler một-một.

| State | Ý nghĩa Java-level | Ví dụ thường gặp | Có tự chứng minh deadlock? |
| --- | --- | --- | --- |
| **NEW** | Đã tạo Thread object, chưa start | new Thread(task) | Không |
| **RUNNABLE** | Đang thực thi hoặc sẵn sàng trong VM; có thể chờ tài nguyên OS | CPU loop, phần native I/O | Không |
| **BLOCKED** | Chờ **intrinsic object monitor** | Đợi synchronized(lock) | Không |
| **WAITING** | Chờ hành động khác, không có timeout xác định | Object.wait(), join(), LockSupport.park() | Không |
| **TIMED_WAITING** | Chờ với deadline/timeout | Thread.sleep, wait(timeout), join(timeout), parkNanos | Không |
| **TERMINATED** | Đã kết thúc thực thi | run() kết thúc | Không |

### 3.1. RUNNABLE không đồng nghĩa dùng CPU 100%

Một RUNNABLE thread có thể:

- Đang chạy Java code trên CPU.
- Sẵn sàng chạy nhưng đang đợi scheduler.
- Đang ở native code hoặc một hoạt động I/O tùy implementation.
- Chạy trong vòng spin tiêu tốn CPU.
- Bị container CPU throttling làm thực tế ít có tiến độ.

Cần đối chiếu **CPU usage per thread**, stack frames, native I/O, profiling và nhiều snapshots trước khi nói một RUNNABLE thread “là thủ phạm”.

### 3.2. BLOCKED có nghĩa rất cụ thể

**BLOCKED** là Java thread đang chờ **object monitor** của synchronized statement/method, hoặc chờ reacquire monitor sau Object.wait(). Nó không phải state generic cho “đang chờ bất cứ lock nào”.

Một thread đang chờ **ReentrantLock.lock()** có thể xuất hiện như **WAITING** hoặc **TIMED_WAITING** liên quan LockSupport.park/AQS; không mặc nhiên là BLOCKED. Phải xem lock owner và stack.

### 3.3. WAITING có thể hoàn toàn bình thường

Các trạng thái có thể là thiết kế hợp lệ:

- Thread pool worker đợi task trong BlockingQueue.take().
- Consumer đợi CountDownLatch.await().
- Main thread đợi worker.join().
- Object.wait() đợi predicate/notify.
- LockSupport.park() dùng để quản lý lịch công việc.
- Scheduled executor đang chờ lần chạy tiếp theo.

Nếu task chưa đến và không có request chờ response bị ảnh hưởng, thread WAITING là trạng thái “nghỉ đúng chỗ”, không phải lỗi.

### 3.4. TIMED_WAITING không hứa thời gian chính xác

**Thread.sleep(100)** đặt một yêu cầu ngủ khoảng thời gian, nhưng elapsed duration có thể bị tác động bởi OS scheduler, timer precision, GC hoặc CPU contention. Nếu Thread đang sleep trong synchronized block, nó **vẫn sở hữu monitor** đó.

Không dùng thread state polling như một coordination protocol: **getState() chỉ là snapshot**, trạng thái có thể đổi ngay sau khi đọc.

## 4. Một cạnh chờ chưa phải deadlock

Ví dụ có hai threads:

~~~text
Thread T1:
  owns monitor M
  performs 10 ms work
  releases M

Thread T2:
  tries synchronized(M)
  BLOCKED while T1 owns M
  then enters after T1 unlocks
~~~

Đây là **lock contention bình thường**. T2 chưa tiến triển tại khoảnh khắc quan sát, nhưng T1 có đường hoàn tất và giải phóng resource.

Mô hình quan hệ:

~~~text
T2 --waits-for M--> T1 (owner)
T1 --doing work--> eventually unlock(M)

Wait-for graph:
  T2 ----> T1
          (không có cycle)
~~~

Dù T2 đã chờ vài giây, vẫn cần xem T1 đang làm gì. T1 có thể thực hiện tác vụ lâu bất hợp lý hoặc bị treo I/O — nhưng **không tự động có một vòng deadlock monitor**.

### 4.1. Lock convoy

Khi nhiều threads cùng cần một global lock, owner kế tiếp có thể liên tục làm hàng đợi kéo dài:

~~~text
T1 owns M
T2 waits for M
T3 waits for M
T4 waits for M

T1 releases -> one contender acquires -> others continue waiting
~~~

Đây có thể là **throughput bottleneck/lock contention**, không có circular wait. Tăng số threads có thể làm contention nặng hơn; thường cần rút ngắn critical section hoặc phân vùng state/locks.

### 4.2. Owner giữ lock trong lúc ngủ hoặc I/O

~~~java
// Minh họa trong một method, không phải class độc lập.
synchronized (lock) {
    Thread.sleep(300);
}
~~~

Thread vẫn giữ monitor trong lúc ngủ. Các threads khác đang cố synchronized(lock) có thể BLOCKED. Một thao tác như DB call trong synchronized cũng kéo dài lock hold time.

Để sửa, kiểm tra thiết kế invariant rồi chuyển I/O hoặc các phần không cần độc quyền ra ngoài critical section khi an toàn.

## 5. Demo Java 17+ #1: chờ monitor rồi tiếp tục, không deadlock

Tạo **MonitorContentionDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class MonitorContentionDemo {
    public static void main(String[] args)
            throws InterruptedException {
        Object lock = new Object();
        CountDownLatch holderReady = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        AtomicInteger completedSections = new AtomicInteger();

        Thread holder = new Thread(() -> {
            synchronized (lock) {
                holderReady.countDown();
                try {
                    releaseHolder.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
                completedSections.incrementAndGet();
            }
        }, "lock-holder");

        Thread contender = new Thread(() -> {
            synchronized (lock) {
                completedSections.incrementAndGet();
            }
        }, "lock-contender");

        holder.start();
        holderReady.await();
        contender.start();

        releaseHolder.countDown();

        holder.join();
        contender.join();

        System.out.println("completedSections="
                + completedSections.get());
        System.out.println("bothTerminated="
                + (!holder.isAlive() && !contender.isAlive()));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 -encoding UTF-8 MonitorContentionDemo.java
java MonitorContentionDemo
~~~

**Stdout chính xác khi không có external interruption và tiến trình được cấp CPU để hoàn tất:**

~~~text
completedSections=2
bothTerminated=true
~~~

Giải thích:

1. Holder acquire lock, phát **holderReady**, rồi chờ releaseHolder **trong khi vẫn sở hữu intrinsic monitor**.
2. Main chỉ start contender sau khi holderReady đã được phát. Contender cần cùng lock nên không thể vào trước khi holder release.
3. Main phát releaseHolder; holder chạy tiếp, tăng counter rồi ra synchronized.
4. Contender acquire lock, tăng counter rồi thoát.
5. Main join cả hai và in kết quả xác định.

**Không có circular wait:** holder chờ latch do main điều khiển; main không cần monitor lock để phát tín hiệu. Nếu holder chỉ chờ mãi mà main không phát, chương trình có thể treo do thiếu tín hiệu nhưng chưa có bằng chứng intrinsic-monitor deadlock cycle.

**Quan sát timing:** Không cố định rằng tại một System.out.println cụ thể contender chắc đang ở state BLOCKED, vì có thể chưa được scheduler cho chạy hoặc holder đã release. Muốn chụp state phải giữ workload ở một mốc đủ lâu với công cụ chẩn đoán; không dùng một lần getState() làm chứng minh.

## 6. Deadlock là một vòng chờ: wait-for graph

Mẫu monitor deadlock kinh điển:

~~~text
Thread T1:
    synchronized (A) {      // owns A
        synchronized (B) {  // waits B held by T2
            ...
        }
    }

Thread T2:
    synchronized (B) {      // owns B
        synchronized (A) {  // waits A held by T1
            ...
        }
    }
~~~

Nếu T1 đã giữ A và T2 đã giữ B, sau đó cả hai cùng cố acquire lock của bên kia:

~~~text
        owns A                   owns B
         T1                       T2
          |                        |
          +-- waits for B -------->+
          <-------- waits for A ---+

Wait-for graph:
     T1 ------> T2
      ^         |
      |         v
      +---------+
      CYCLE
~~~

T1 không thể acquire B vì T2 không release trước khi thoát block; T2 không thể acquire A vì T1 không release trước khi thoát block. Với intrinsic monitor entry, bình thường các threads đang chờ lock không thể tự giải quyết chỉ nhờ một timeout do synchronized không có API **tryLock(timeout)**.

### 6.1. Bốn điều kiện kinh điển

Các điều kiện Coffman thường dùng để giải thích resource deadlock:

1. **Mutual exclusion:** resource không thể dùng đồng thời theo kiểu độc quyền.
2. **Hold and wait:** thread giữ ít nhất một resource và đợi resource khác.
3. **No preemption:** resource không thể đơn giản tước từ owner bất kỳ mà vẫn giữ invariant.
4. **Circular wait:** các owners tạo một vòng phụ thuộc chờ.

Nếu loại bỏ ít nhất một điều kiện bằng thiết kế đúng, có thể ngăn kiểu deadlock liên quan. Ví dụ **global lock ordering** ngăn circular wait cho tập locks được tuân thủ.

### 6.2. Không nhầm deadlock với “thread A bị chặn lâu”

T1 đợi B do T2 giữ nhưng T2 đang tính toán rồi sẽ release B: **chưa phải một deadlock cycle**.

T1 đợi B, T2 đợi A, mỗi thread đang giữ monitor bên kia: **có vòng đợi**; nếu không có đường nhả hợp lệ, đây là deadlock.

T1 giữ A rồi **Object.wait() trên B** trong khi vẫn giữ A; T2 muốn acquire A để có thể notify B: có thể gây **condition/lock dependency deadlock** dù trạng thái trên dump có sự kết hợp WAITING và BLOCKED, không chỉ hai BLOCKED.

### 6.3. Deadlock là tính chất của toàn bộ tập phụ thuộc

Một thread dump cho thấy “waiting to lock B owned by T2” mới cho một cạnh. Cần điều tra T2 đang chờ gì, rồi tiếp tục đến khi:

- Thấy một vòng về thread đã ghé qua ⇒ có circular wait.
- Gặp thread owner không chờ resource khác ⇒ có đường khả dĩ để tiến triển.
- Gặp wait trên external system/condition không quan sát được ⇒ chưa thể kết luận; cần logs, runtime state hoặc external-system diagnostics.

## 7. Phân biệt object monitor và ReentrantLock

Java có nhiều cơ chế đồng bộ hóa:

| Cơ chế | Cách acquire | Chờ thường thấy | Cơ chế release |
| --- | --- | --- | --- |
| **synchronized(obj)** | JVM monitorenter/implicit method monitor | Contender thường BLOCKED | Tự exit block/method |
| **ReentrantLock.lock()** | java.util.concurrent.locks | Thường dùng AQS/LockSupport.park, có thể WAITING | Programmer gọi unlock trong finally |
| **ReentrantLock.lockInterruptibly()** | Interruptible acquisition | Có thể xử lý interruption | unlock trong finally sau khi acquire |
| **ReentrantLock.tryLock(timeout)** | Timed attempt | Có thể timed wait | unlock khi acquire thành công |
| **ReadWriteLock** | Read/write lock permits | Tùy implementation và mode | unlock tương ứng |

**Java Thread.State.BLOCKED chỉ nói về object monitor**; chờ java.util.concurrent synchronizer có thể được báo là WAITING/TIMED_WAITING. Việc chọn ReentrantLock không tự ngăn deadlock; hai ReentrantLocks vẫn có thể tạo vòng wait.

### 7.1. Ownable synchronizer

Một số synchronizers có khái niệm owner (ví dụ ReentrantLock và ReentrantReadWriteLock.WriteLock). JVM management APIs có khả năng tìm deadlocks liên quan **object monitors** và **ownable synchronizers** ở mức hỗ trợ.

Không suy từ tên lock nào cũng được **ThreadMXBean** nhận biết: custom locks, semaphores, latches, distributed locks, futures hoặc external resources không nhất thiết hiện dưới dạng cycle của Java monitor/ownable-synchronizer graph.

### 7.2. Reentrant monitor không tự deadlock với chính nó

Thread đã sở hữu intrinsic monitor M có thể reenter synchronized(M) nhiều lần; JVM theo dõi reentrant depth cho đến khi exits cân bằng. Vì thế:

~~~java
synchronized (lock) {
    synchronized (lock) {
        // Cùng owner thread tái nhập cùng monitor: hợp lệ.
    }
}
~~~

Không phải deadlock chỉ vì nested synchronized. Nhưng nếu nested locks **A rồi B** ở thread này và **B rồi A** ở thread khác, circular wait vẫn có thể xảy ra.

## 8. ThreadMXBean: Java API để chẩn đoán deadlocks

**java.lang.management.ThreadMXBean** cung cấp:

- **findMonitorDeadlockedThreads():** tìm cycles liên quan object monitors.
- **findDeadlockedThreads():** tìm cycles liên quan object monitors **và ownable synchronizers** mà implementation hỗ trợ.
- **getThreadInfo(...):** thread details, lock owner, stack trace và monitor/synchronizer metadata theo overload.
- **dumpAllThreads(...):** snapshot threads với stack traces và lock information khi VM hỗ trợ.
- **getThreadCpuTime(...):** CPU time theo thread nếu bật/hỗ trợ.

**Null** từ deadlock finder nghĩa là **không phát hiện một cycle thuộc phạm vi kiểm tra ở thời điểm lấy mẫu**, không phải bằng chứng chương trình hoàn toàn không có liveness bugs.

### 8.1. Runnable Java 17 demo: chẩn đoán các cycles hiện có

Tạo **DeadlockInspector.java**:

~~~java
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;

public class DeadlockInspector {
    public static void main(String[] args) {
        ThreadMXBean bean =
                ManagementFactory.getThreadMXBean();

        long[] ids = bean.findMonitorDeadlockedThreads();

        System.out.println("monitorCycleDetected="
                + (ids != null && ids.length > 0));

        if (ids != null) {
            System.out.println("affectedThreadCount="
                    + ids.length);
            ThreadInfo[] infos =
                    bean.getThreadInfo(ids, true, true);
            for (ThreadInfo info : infos) {
                if (info != null) {
                    System.out.println(
                            "thread=" + info.getThreadName());
                    System.out.println(
                            "state=" + info.getThreadState());
                    System.out.println(
                            "lock=" + info.getLockName());
                    System.out.println(
                            "owner=" + info.getLockOwnerName());
                }
            }
        }

        try {
            long[] allTypes = bean.findDeadlockedThreads();
            System.out.println("broaderCycleDetected="
                    + (allTypes != null
                    && allTypes.length > 0));
        } catch (UnsupportedOperationException unsupported) {
            System.out.println("broaderCycleSupported=false");
        }
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 DeadlockInspector.java
java DeadlockInspector
~~~

**Không có một stdout tuyệt đối cho mọi JVM** vì counts và thread names phản ánh trạng thái tiến trình và khả năng quản lý synchronizers. Trong một JVM bình thường không có deadlock:

~~~text
monitorCycleDetected=false
broaderCycleDetected=false
~~~

Đây là **output thường gặp trong môi trường không có deadlock**, không phải contract tuyệt đối. Một JVM có thể hỗ trợ monitor detection nhưng không hỗ trợ broad ownable-synchronizer detection, hoặc có background/runtime state bất thường. Nếu code in tên threads, thứ tự/names khác nhau theo run.

**Quan trọng:** demo này chỉ kiểm tra **JVM của chính chương trình**. Chạy một chương trình inspector hoàn toàn mới không thể tìm deadlock ở process server khác. Để chẩn đoán server, dùng **jcmd/jstack/JMX trong process đích** hoặc tích hợp ThreadMXBean trong service có thẩm quyền.

### 8.2. Vì sao getThreadInfo thêm thông tin?

ThreadInfo có thể cung cấp:

- Thread name, ID, state.
- Lock name chờ acquire/wait.
- Lock owner name/ID khi biết.
- Stack trace tại snapshot.
- Locked monitors và ownable synchronizers theo overload.

Nhưng dump có race: thread có thể release/acquire lock ngay sau khi snapshot. Không có public API bảo đảm một bản dump là lịch sử mọi interleavings.

## 9. Một số hiện tượng không phải Java monitor deadlock

### 9.1. Chờ điều kiện chưa xảy ra

Thread A chờ CountDownLatch.await() và thread B chưa xử lý xong task. Nếu B còn khả năng hoàn thành và countDown, đó có thể là **legitimate waiting**. Nếu producer quên countDown, hệ có thể treo vì **missing signal**; deadlock detector không nhất thiết báo cycle.

### 9.2. Thread-pool starvation/self-deadlock

Giả sử ExecutorService chỉ có **một worker thread**. Task A chạy trên worker ấy, submit task B vào **cùng executor**, rồi gọi **futureB.get()**. Task B nằm trong queue, không có worker khác để chạy; A chờ B, B chờ worker mà A đang chiếm.

~~~text
Single-thread executor:
 Worker-1 executes Task A
    |
    +-- submits Task B to same executor
    |
    +-- waits Future.get(B) indefinitely

 Task B stays queued:
    cannot start because Worker-1 blocked in Task A
~~~

Đây là **executor starvation deadlock** theo nghĩa liveness của hệ, **không nhất thiết là monitor/ownable-lock deadlock** mà ThreadMXBean.findDeadlockedThreads nhận diện.

Cách tránh: không block trong một task để đợi task khác chỉ có thể chạy trên cùng pool không còn capacity; dùng completion composition, worker capacity phù hợp, hoặc tách executor theo resource constraints.

### 9.3. External resource deadlock

Hai requests có thể bị deadlock ở database row locks, distributed locks hoặc file locks. JVM stack có thể chỉ cho thấy socket I/O chờ DB response, thường không tạo Java-level lock ownership cycle. Cần DB deadlock graphs, slow queries và resource-level diagnostics.

### 9.4. Starvation

Một thread có thể chờ lock hoặc CPU rất lâu vì lock thường được những threads khác giành được trước hoặc process bị CPU-throttled. Không có circular wait không có nghĩa latency đã đạt yêu cầu.

### 9.5. Livelock

Hai threads đều “nhường đường” và retry theo quy tắc không hiệu quả, liên tục thay đổi trạng thái nhưng không hoàn tất. Chúng có thể hiển thị RUNNABLE thay vì BLOCKED/WAITING. CPU có thể cao.

### 9.6. Busy spin

~~~java
// Minh họa lỗi, không dùng làm solution:
while (!ready) {
    // ready là ordinary non-volatile field.
}
~~~

Có thể gây CPU cao, không bảo đảm visibility do data race, và không tạo một wait-for graph. **Thread.onSpinWait** là performance hint chứ không biến ordinary read thành volatile read.

## 10. Demo Java 17+ #2: WAITING hợp lệ qua CountDownLatch

Tạo **LegitimateWaitingDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class LegitimateWaitingDemo {
    public static void main(String[] args)
            throws InterruptedException {
        CountDownLatch workerReady = new CountDownLatch(1);
        CountDownLatch mayProceed = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger();

        Thread worker = new Thread(() -> {
            workerReady.countDown();
            try {
                mayProceed.await();
                result.set(7);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "legitimate-waiter");

        worker.start();
        workerReady.await();

        mayProceed.countDown();
        worker.join();

        System.out.println("result=" + result.get());
        System.out.println("terminated=" + !worker.isAlive());
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 LegitimateWaitingDemo.java
java LegitimateWaitingDemo
~~~

**Stdout nếu không có external interruption và chạy thành công:**

~~~text
result=7
terminated=true
~~~

Worker có thể chuyển sang WAITING khi chờ latch, nhưng snapshot thread state không được chương trình in và **không nên gán một expected intermediate state cố định** vì main có thể countDown rất sớm. Điều quan trọng là **worker có đường hoàn tất** sau tín hiệu, và main dùng join để nhận kết quả xác định.

### 10.1. Vì sao dùng latch chứ không sleep?

CountDownLatch có protocol và memory consistency effects: actions trước countDown **happens-before** actions sau successful await tương ứng. **Thread.sleep** chỉ ngừng thread một thời gian, không bảo đảm bên kia đã đến milestone hay publish kết quả.

### 10.2. Timeout là công cụ chẩn đoán, không phải sửa correctness

~~~java
// Minh họa cần import TimeUnit:
boolean signaled =
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
if (!signaled) {
    throw new IllegalStateException("Wait exceeded deadline");
}
~~~

Timeout có thể giúp service tránh chờ vô hạn, nhưng không tự làm producer phát signal. Khi timed await trả false, không dựa vào success HB contract của latch.

## 11. Object.wait(), Thread.sleep() và LockSupport.park() khác nhau

| API | Trạng thái thường gặp | Có tự nhả intrinsic monitor hiện đang giữ? | Ghi chú |
| --- | --- | --- | --- |
| **Object.wait()** trên M | WAITING/TIMED_WAITING | **Có, với receiver monitor M** | Cần thread sở hữu M; reacquire M trước khi return |
| **Thread.sleep()** | TIMED_WAITING | **Không** | Không có synchronization semantics riêng |
| **LockSupport.park()** | WAITING | **Không tự nhả intrinsic monitors** | Có thể được unpark/interrupt hoặc spuriously return |
| **Thread.join()** | WAITING/TIMED_WAITING | **Không tự nhả monitors tùy ý** | Completion synchronization với target thread |
| **CountDownLatch.await()** | Thường WAITING/TIMED_WAITING qua AQS | **Không tự nhả intrinsic monitors** | Latch signal dùng coordination protocol |
| **ReentrantLock Condition.await()** | Thường WAITING/TIMED_WAITING | Nhả **associated ReentrantLock** theo contract | Khác với intrinsic monitor; dùng đúng lock discipline |

### 11.1. Một deadlock khó nhìn từ wait()

~~~java
// Mô hình lỗi: giữ A khi chờ điều kiện trên B.
synchronized (a) {
    synchronized (b) {
        b.wait();
    }
}
~~~

**Object.wait() trên B** tạm nhả các acquisition của B rồi reacquire trước return, nhưng **không nhả A**. Nếu notifier cần synchronized(a) trước mới có thể thực hiện synchronized(b) và b.notifyAll(), toàn hệ có thể bị kẹt vì vòng phụ thuộc trên A và tín hiệu B.

Không nói “wait() nhả mọi locks mà thread sở hữu”. Nó nhả **monitor của receiver object**, không tự nhả unrelated locks.

### 11.2. notifyAll không trao monitor ngay

~~~java
synchronized (lock) {
    ready = true;
    lock.notifyAll();
    // Notifier vẫn giữ monitor tới khi block kết thúc.
}
~~~

Các waiters thức dậy phải reacquire lock trước khi return khỏi wait. Nếu notifier tiếp tục làm việc dài trong synchronized, waiters vẫn chưa tiến triển.

### 11.3. Condition predicate phải được kiểm tra lại

~~~java
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
}
~~~

Dùng **while**, không chỉ if, vì spurious wakeups, nhiều waiters và condition có thể thay đổi trước khi thread reacquire. Đây là correctness protocol, không phải performance tuning.

## 12. Demo Java 17+ #3: monitor wait có đường kết thúc

Tạo **MonitorWaitDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;

public class MonitorWaitDemo {
    public static void main(String[] args)
            throws InterruptedException {
        Object monitor = new Object();
        CountDownLatch entered = new CountDownLatch(1);
        boolean[] ready = {false};
        boolean[] observed = {false};

        Thread waiter = new Thread(() -> {
            synchronized (monitor) {
                entered.countDown();
                while (!ready[0]) {
                    try {
                        monitor.wait();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                observed[0] = true;
            }
        }, "monitor-waiter");

        waiter.start();
        entered.await();

        synchronized (monitor) {
            ready[0] = true;
            monitor.notifyAll();
        }

        waiter.join();
        System.out.println("condition=" + ready[0]);
        System.out.println("observed=" + observed[0]);
        System.out.println("terminated=" + !waiter.isAlive());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 -encoding UTF-8 MonitorWaitDemo.java
java MonitorWaitDemo
~~~

**Stdout xác định khi không external interrupt và hoàn thành:**

~~~text
condition=true
observed=true
terminated=true
~~~

Các synchronization edges:

- Waiter gọi entered.countDown khi còn giữ monitor.
- Main entered.await(), sau đó muốn synchronized(monitor). Nếu waiter chưa gọi wait, main sẽ chờ; khi waiter wait nó nhả monitor.
- Main set ready=true và notifyAll.
- Waiter reacquire monitor rồi đọc ready, đi tiếp và cập nhật observed.
- Main join đảm bảo nhìn kết quả sau waiter kết thúc.

Nếu waiter bị spurious wakeup trước khi ready=true, vòng while kiểm tra lại điều kiện nên không đi tiếp sai.

## 13. JVM background threads: có thể RUNNABLE hoặc WAITING hoàn toàn bình thường

Một JVM HotSpot thường có threads phục vụ runtime, ví dụ:

| Nhóm | Vai trò | Điều không được suy |
| --- | --- | --- |
| **GC worker/concurrent threads** | Thu gom, đánh dấu, xử lý heap, đôi khi concurrent với application | RUNNABLE GC thread không đồng nghĩa GC deadlock |
| **C1/C2 CompilerThread** | Biên dịch hot bytecode thành native machine code | Dùng CPU JIT không chứng minh business thread bị deadlock |
| **Reference Handler** | Phục vụ cơ chế xử lý references theo thiết kế VM | WAITING không mặc nhiên là hỏng |
| **Finalizer (nếu tồn tại)** | Hỗ trợ finalization lịch sử đã bị discourage/deprecate | Không dùng tên thread làm cam kết mọi JDK có finalizer |
| **Common-Cleaner / cleaners** | Dọn resource qua cleaning mechanisms | WAITING khi chưa có task là bình thường |
| **Service Thread / VM Thread** | Công việc và coordination nội bộ VM | Thread name/states khác theo build |
| **Attach Listener (nếu được tạo)** | Phục vụ các thao tác chẩn đoán/attach | Không phải HTTP worker nghiệp vụ |
| **Signal Dispatcher** | Xử lý signaling/runtime events trên nền tảng hỗ trợ | Trạng thái WAITING thường hợp lệ |
| **Sweeper / code-cache support** | Theo dõi một số phần của compiled code cache khi có | Không đồng nghĩa app đã hoàn tất warm-up |
| **User-created background pool threads** | Scheduled tasks, HTTP clients, executor maintenance | Không phải tự động là JVM internal threads |

**Tên thực tế thay đổi theo JDK, collector, architecture, flags và tình trạng khởi tạo subsystem.** Đừng yêu cầu thread dump phải chứa chính xác một danh sách trên mới gọi là Java 17.

### 13.1. GC đồng thời không phải stop-the-world toàn thời gian

Ví dụ collector có **concurrent mark threads** đang chạy trong lúc mutators cũng chạy. GC thread dùng CPU nhưng không đồng nghĩa application threads bị dừng suốt giai đoạn đó.

Ngược lại khi JVM có **GC STW pause/safepoint**, nhiều application threads có thể đồng thời không tiến triển trong một khoảng, nhưng đó **không phải deadlock giữa application locks**. Cần đối chiếu GC logs/safepoint logs với p99 latency và timestamps.

### 13.2. JIT compiler có thể tiêu tốn CPU mà ứng dụng vẫn đúng

C1/C2 compiler threads tạo optimized native code khi methods trở nên hot. Trong warm-up, CPU JIT có thể cao và application response time dao động do resource competition.

Nếu application mất throughput, phải phân biệt:

- JIT đang compile rất nhiều methods và giữ CPU?
- Code cache full khiến compilation bị hạn chế?
- CPU quota/cgroup throttling?
- GC cùng chiếm CPU?
- Chính business code đang busy-spin?

**CompilerThread RUNNABLE không là một dấu hiệu deadlock.** Dùng JFR, compiler logs, jcmd Compiler.queue/codecache để kiểm chứng JIT activity.

### 13.3. Những thread nền chờ công việc là bình thường

Một pool thread có stack tại **LinkedBlockingQueue.take** hoặc **ScheduledThreadPoolExecutor delayed queue** có thể đang idle đúng thiết kế.

Nếu không có backlog và requests vẫn xử lý bình thường, chưa có issue. Nếu task queue tăng mãi mà workers đều đang WAITING theo cách bất thường, cần xác nhận có producer/consumer handoff, permits, cancellation hoặc task submission bug.

## 14. Daemon threads và JVM process lifetime

**Daemon thread** là một thuộc tính ảnh hưởng điều kiện JVM kết thúc khi không còn non-daemon threads đang sống. Daemon **không đồng nghĩa** “thread của JVM nội bộ” hoặc “thread được ưu tiên CPU thấp”.

Các điểm đúng:

- Application có thể tự tạo daemon threads.
- JVM có runtime threads với policies khác nhau.
- Một daemon thread WAITING có thể hoàn toàn bình thường.
- JVM có thể kết thúc khi chỉ còn daemon threads, không chờ chúng hoàn tất công việc nghiệp vụ.
- Một non-daemon thread WAITING vô hạn có thể khiến JVM process vẫn sống.
- Một daemon thread bị deadlock vẫn là bug nếu công việc cần nó bị ảnh hưởng, dù daemon status khác.

Không chỉnh tất cả threads thành daemon để “chữa treo khi shutdown”; cần sửa lifecycle, task cancellation và executor shutdown protocol.

### 14.1. Shutdown issues khác deadlock

Một server không thoát sau shutdown request có thể vì:

- Non-daemon executor worker chưa shutdown.
- Scheduled task vẫn active.
- Consumer chờ queue không được signal/cancel.
- Socket listener chưa đóng.
- A thread giữ monitor bị kẹt, có thể deadlock hoặc long wait.
- Shutdown hook chờ external resource.
- JVM thật đang thực hiện work mà application chưa accounting.

Dùng thread dumps tại shutdown và kiểm tra executor shutdownNow/awaitTermination theo protocol; **không tự gọi System.exit** để che root cause trong thiết kế thư viện.

## 15. JVM còn đang làm việc: cách đối chiếu logs với thread dumps

Một tình huống thực tế:

~~~text
14:00:00 Request starts
14:00:01 HTTP thread waiting
14:00:02 JIT/compiler activity
14:00:03 GC concurrent mark
14:00:04 Request still waiting
14:00:05 New thread dump
~~~

Điều này **không chứng minh** JIT hoặc GC là nguyên nhân request chưa xong. Hãy dựng timeline dựa trên dữ liệu:

1. Request trace cho biết nó đang chờ lock, DB, socket, future hay task?
2. Thread dump cho biết waiting location và lock owner?
3. GC log có STW pause đúng thời gian không, duration bao nhiêu?
4. CPU profile có compiler/GC thread đang chiếm phần lớn CPU quota không?
5. Service metrics có queue backlog, connection pool exhausted hoặc request concurrency spike không?
6. Thread states có giống nhau qua nhiều snapshots cách nhau một khoảng có chủ đích không?

**Correlation chưa đủ causation.** GC/JIT có thể đồng thời hiện diện như background noise trong incident do deadlock của ứng dụng.

## 16. jcmd: bước đầu tiên để xem JVM đang sống

Trên HotSpot JDK 17, từ máy/process namespace thích hợp:

~~~bash
jcmd
jcmd <PID> help
jcmd <PID> VM.version
jcmd <PID> VM.uptime
jcmd <PID> Thread.print
jcmd <PID> Thread.print -l
~~~

**<PID>** là placeholder cần thay bằng process ID thực, không phải literal string. Quyền attach thường cần cùng user/permissions phù hợp; trong containers phải chú ý PID namespace. Nếu jcmd command không được hỗ trợ, đọc **jcmd <PID> help** trên build đích.

**Thread.print -l** có thể bổ sung thông tin java.util.concurrent ownable synchronizers. Thread dump có overhead (impact thường medium, phụ thuộc thread count); tránh chụp quá dày trên máy nhiều threads.

### 16.1. jstack

~~~bash
jstack <PID>
jstack -l <PID>
~~~

jstack có thể in full thread dumps và lock details tùy JDK build/attach support. Dùng **jcmd Thread.print** như lựa chọn quan sát ban đầu với HotSpot hiện đại; không coi jstack đã deprecated/không tồn tại ở mọi Java17 distributions.

### 16.2. Cách đọc một stack fragment

~~~text
"http-worker-7" ... java.lang.Thread.State: BLOCKED
    at com.example.Service.update(Service.java:77)
    - waiting to lock <0xABC> (a java.lang.Object)

"http-worker-3" ... java.lang.Thread.State: RUNNABLE
    at com.example.Service.persist(Service.java:32)
    - locked <0xABC> (a java.lang.Object)
~~~

Đây là **thread-dump example minh họa**, không phải một output được lấy từ JVM thật. Nó cho biết worker-7 muốn acquire monitor **0xABC**, worker-3 đang giữ monitor đó tại lúc dump.

Để chẩn đoán, tiếp tục điều tra worker-3 đang thực hiện gì, đang chờ I/O hay locks khác không, sau bao lâu nó release.

### 16.3. Thread dump ID/lock identity

- Thread name do application/JVM đặt, có thể trùng hoặc đổi.
- Thread ID trong Java API khác **native OS thread ID** theo nền tảng.
- Một stack có thể hiển thị **nid** hexadecimal và **tid** dạng VM ID/native pointer tùy dump format.
- Lock identity trong thread dump thường là VM object identity/address-like token, **không phải Java reference public API**.
- Không so sánh chỉ theo tên object class khi xác định same monitor; cần đúng lock identity.

### 16.4. Multi-snapshot: hai hoặc ba thời điểm

Chụp một bản dump, chờ khoảng thời gian hữu ích với service (ví dụ 5–10 giây nếu phù hợp), rồi chụp tiếp:

~~~bash
jcmd <PID> Thread.print -l
# Sau một khoảng quan sát thích hợp:
jcmd <PID> Thread.print -l
~~~

Mục tiêu:

- Same thread vẫn waiting on same lock?
- Lock owner thay đổi hay không?
- Owner stack thay đổi hay “đứng” trên một call?
- Có cùng monitor cycle nhiều snapshots?
- Request queue backlog tăng/giảm?
- GC/JIT background activity thay đổi?
- CPU per-thread counters/stack sampling cho biết thread thực sự làm việc?

**Không lấy “stack giống nhau hai lần” làm bằng chứng tuyệt đối deadlock.** Một blocking read hoặc polling loop cũng có thể có stack giống qua nhiều lần chụp.

## 17. Quy trình dựng wait-for graph từ thread dump

Giả sử dump có:

~~~text
T1: BLOCKED waiting to lock B; owns A
T2: BLOCKED waiting to lock C; owns B
T3: BLOCKED waiting to lock A; owns C
~~~

Vẽ:

~~~text
T1 --waits for B owned by--> T2
T2 --waits for C owned by--> T3
T3 --waits for A owned by--> T1

Cycle: T1 -> T2 -> T3 -> T1
~~~

Nếu lock infos đáng tin và không có thay đổi phá cycle, đây là một Java monitor deadlock.

### 17.1. Kiểm chứng theo từng bước

1. Chọn thread bị ảnh hưởng trực tiếp đến request/SLA.
2. Ghi lock identity mà thread đang cố acquire.
3. Tìm owner thread của lock ấy ở snapshot.
4. Đọc owner stack và resource mà owner đang chờ.
5. Tiếp tục theo quan hệ owner→waiter đến khi có vòng, dead end hoặc external wait.
6. Nếu có vòng, đối chiếu với **ThreadMXBean.findDeadlockedThreads()**/dump diagnostic khi có.
7. Nếu không có vòng, điều tra long-held lock, I/O, starvation hoặc missing signal.
8. So với timeline request/GC/CPU để xác định tác động thật.

### 17.2. Deadlock detection không bao quát toàn bộ thế giới

Những tình huống có thể thoát khỏi detector chuẩn:

- Thread-pool starvation do Future.get() trên cùng worker pool.
- Distributed lock/DB row deadlocks ngoài JVM.
- Missing CountDownLatch countdown.
- Logical condition cycles không biểu diễn bằng ownable lock owners.
- Một deadlock chỉ xảy ra trong thời gian ngắn rồi được xử lý bằng timeout/interrupt.
- Mismatch giữa JMX visibility và virtual-thread implementations ở JDK mới.

Vì vậy **findDeadlockedThreads()==null không có nghĩa mọi threads có progress guarantee**.

## 18. Java Flight Recorder (JFR) để đo lock contention và waiting

JFR hỗ trợ ghi events để liên hệ thread behavior với timeline. Tùy Java 17 recording settings:

- **jdk.JavaMonitorEnter**: các sự kiện contended monitor entry phù hợp.
- **jdk.JavaMonitorWait**: Object.wait related.
- **jdk.ThreadPark**: LockSupport park-related events.
- **GC events**: GC pauses/concurrent phases.
- **Execution/CPU samples**: business or compiler/GC CPU activity khi event tương ứng được bật.

Ví dụ khởi động một app main class:

~~~bash
java -XX:StartFlightRecording=filename=thread-diag.jfr,duration=60s,settings=profile MyService
~~~

Cần có class MyService thực và môi trường hỗ trợ JFR. Nếu app dừng trước 60s, recording duration và output phụ thuộc lifecycle/settings.

Đọc bằng CLI:

~~~bash
jfr summary thread-diag.jfr
jfr print --events jdk.JavaMonitorEnter,jdk.JavaMonitorWait,jdk.ThreadPark thread-diag.jfr
~~~

**Không có event không chứng minh code không có synchronized hoặc park.** Event có thể bị tắt, threshold lọc duration, hoặc không xuất hiện trong sampling interval.

### 18.1. Quan sát tiến trình đã chạy bằng jcmd JFR

~~~bash
jcmd <PID> JFR.start name=threadcase settings=profile duration=60s filename=threadcase.jfr
jcmd <PID> JFR.check
~~~

Cú pháp options tùy HotSpot JDK build; đọc **jcmd <PID> help JFR.start** để xác nhận. Recording có chi phí CPU/disk/storage theo cấu hình, vì vậy cần quản lý retention.

### 18.2. Dùng JFR để đo thay vì đếm BLOCKED snapshots

Một request có thể BLOCKED 2 ms mỗi lần nhưng xảy ra hàng nghìn lần/s, gây throughput problems; một snapshot mỗi 10s có thể không trúng thời điểm BLOCKED. JFR duration-based events giúp nhìn contention frequency/time distribution tốt hơn khi được bật với thresholds thích hợp.

## 19. GC logs và safepoints: phân biệt JVM pause với application lock wait

Trên HotSpot Java 17, bật Unified Logging có chọn lọc:

~~~bash
java -Xlog:gc=info -Xlog:safepoint=info MyService
~~~

**MyService** là class minh họa có main. Tag availability/format xác nhận bằng:

~~~bash
java -Xlog:help
~~~

Khi JVM làm một stop-the-world GC phase, application threads có thể tạm dừng để GC xử lý. Những threads ấy **không thể tự tạo lock cycle** chỉ từ việc GC pause.

### 19.1. GC overhead vs application stall

| Quan sát | Khả năng | Cần thêm chứng cứ |
| --- | --- | --- |
| GC pause log trùng request p99 spike | Có thể do STW GC | Request trace, duration correlation |
| GC concurrent marking kéo dài | Có thể GC dùng CPU đồng thời | CPU profile, GC logs và app throughput |
| Nhiều BLOCKED trên cùng monitor | Lock contention | Owner stack, hold time, wait-for graph |
| WAITING tại BlockingQueue.take | Có thể idle hợp lệ | Task queue depth, producers, pending requests |
| RUNNABLE C2 CompilerThread | JIT đang tiêu CPU | Compiler.queue/codecache, CPU load |
| CPU bị container throttle | Thiếu CPU progress | cgroup/OS metrics |
| JVM heap gần max và allocation stalls | Memory pressure | GC/heap telemetry |
| Future.get() chờ task trên single-thread pool | Pool starvation | Executor queue và worker stacks |

### 19.2. “JVM đang hoạt động” không chứng minh request progressing

GC/JIT/Reference Handler threads có thể còn làm việc, trong khi HTTP threads deadlocked. Process vẫn có thể trả health check bằng một thread pool khác, che sự cố của đường nghiệp vụ.

Do đó health checks nên kiểm tra readiness/end-to-end components phù hợp, không chỉ “JVM PID tồn tại”.

## 20. JIT work: theo dõi compiler queue và code cache

Trong Java 17 HotSpot:

~~~bash
jcmd <PID> Compiler.queue
jcmd <PID> Compiler.codecache
jcmd <PID> Compiler.codelist
~~~

- **Compiler.queue:** compilation tasks đang đợi hoặc được quản lý theo tool.
- **Compiler.codecache:** layout/usage của generated native code cache.
- **Compiler.codelist:** compiled methods đang sống được tool in ra.

CompilerThread RUNNABLE có thể là JIT warm-up hợp lệ. Nếu compiler queue backlog kéo dài và JIT CPU chiếm resource nhiều, request throughput có thể bị ảnh hưởng dù không deadlock.

**Không lấy một snapshot queue trống làm chứng minh JVM không chạy JIT.** Tasks có thể đã xử lý xong; code cache vẫn chứa compiled methods.

### 20.1. Background thread chiếm CPU nhưng không gây deadlock

Có thể do:

- Startup/warm-up compilation.
- GC marking concurrent.
- Reference processing backlog.
- Scheduled internal VM operations.
- Application logging/metrics daemon threads.
- An application busy loop disguised as daemon service.

Cần thread CPU samples theo tên/native thread ID và stack, không chỉ xem Thread.State.

### 20.2. CPU throttling không tạo Java monitor cycle

Một owner thread có thể mất nhiều wall-clock time để release lock vì không được OS scheduler cấp CPU. Waiters sẽ BLOCKED lâu, nhưng vấn đề root cause là CPU quota/oversubscription, không phải lock-order inversion. Điều này đặc biệt quan trọng trong containers với cgroup quotas.

## 21. Cách tránh deadlock: thiết kế lock ordering

Một cách phòng deadlock nhiều object monitors là **global lock order**.

Giả sử hai locks cố định A và B, mọi threads luôn acquire A trước B, và release theo lexical nesting:

~~~java
synchronized (a) {
    synchronized (b) {
        // Cập nhật state liên quan cả a và b.
    }
}
~~~

Không cho một code path acquire B rồi A; nếu tuân thủ nhất quán, vòng chờ kiểu A→B/B→A không thể hình thành cho hai locks ấy.

### 21.1. Demo Java 17+ #4: nhiều threads nhưng có lock order

Tạo **OrderedLocksDemo.java**:

~~~java
public class OrderedLocksDemo {
    private static final Object FIRST = new Object();
    private static final Object SECOND = new Object();
    private static int count;

    private static void increment() {
        synchronized (FIRST) {
            synchronized (SECOND) {
                count++;
            }
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Thread[] workers = new Thread[4];

        for (int t = 0; t < workers.length; t++) {
            workers[t] = new Thread(() -> {
                for (int i = 0; i < 2_000; i++) {
                    increment();
                }
            });
        }

        for (Thread thread : workers) {
            thread.start();
        }
        for (Thread thread : workers) {
            thread.join();
        }

        System.out.println("count=" + count);
        System.out.println("expected=" + (4 * 2_000));
        System.out.println("correct="
                + (count == 4 * 2_000));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 -encoding UTF-8 OrderedLocksDemo.java
java OrderedLocksDemo
~~~

**Stdout chính xác khi kết thúc bình thường:**

~~~text
count=8000
expected=8000
correct=true
~~~

- Mỗi worker thực hiện 2.000 increments.
- Mỗi increment acquire FIRST rồi SECOND, không có code path đảo thứ tự.
- count++ nằm trong critical section chung nên không lost updates.
- Main join tất cả threads rồi đọc count.
- **Output không chứng minh mọi thiết kế khác của hệ đều không deadlock**, chỉ cho thấy ví dụ này có ordering và progress bình thường.

### 21.2. Nếu locks phụ thuộc object identity động?

Nếu cần acquire hai account locks dựa trên objects động, hãy chọn một **stable total order** như account ID duy nhất và luôn acquire từ ID nhỏ đến lớn. Tránh dùng hashCode/identityHashCode đơn độc như total order nếu có nguy cơ collision; khi collision, cần tie-breaker lock hoặc một giải pháp deterministic bổ sung. Việc chỉ sắp xếp theo tên biến a,b trong mỗi caller không đủ nếu callers đảo arguments.

## 22. ReentrantLock: timeouts và interruption

Với intrinsic synchronized, **không có built-in tryLock(timeout)** và một thread đang BLOCKED chờ monitor entry không tự thoát chỉ vì bạn gọi interrupt như với một interruptible lock API.

ReentrantLock hỗ trợ:

~~~java
// Ví dụ phương thức trong class có ReentrantLock lock:
if (lock.tryLock(100,
        java.util.concurrent.TimeUnit.MILLISECONDS)) {
    try {
        // Critical section.
    } finally {
        lock.unlock();
    }
} else {
    // Timeout: log, retry/backoff hoặc fail fast theo hợp đồng.
}
~~~

Cần catch/declare InterruptedException phù hợp. TryLock timeout cho đường **không acquire được lock** một kết quả có thể xử lý, nhưng không tự khắc phục tất cả deadlocks: nếu thread giữ A rồi timeout B, bạn vẫn phải **release A đúng trong finally** và xử lý state consistency.

### 22.1. lockInterruptibly

**ReentrantLock.lockInterruptibly()** cho phép bỏ chờ lock khi có interrupt, khác intrinsic synchronized entry. Một thiết kế cancellation tốt có thể dùng method này, nhưng phải đảm bảo khi đã acquire thì unlock trong finally.

### 22.2. Đừng nới synchronization bằng cách xóa lock

Nếu app deadlock vì lock order sai, bỏ synchronized có thể tạo data races, mất cập nhật hoặc corrupted state. Cần sửa protocol: thống nhất ordering, giảm scope, dùng immutable messages, actor pattern/queues hoặc locks có timeout và rollback logic đúng.

## 23. Long-held locks và I/O trong critical section

Một pattern không tốt:

~~~java
synchronized (lock) {
    databaseCall(); // Có thể block vì DB/network.
    cache.put(key, value);
}
~~~

Nếu databaseCall mất 20 giây, các threads khác cần lock sẽ chờ suốt 20 giây. Đó có thể là **lock convoy do blocking I/O**, không phải circular deadlock trong JVM.

Cân nhắc:

~~~java
Result result = databaseCall();

synchronized (lock) {
    cache.put(key, result);
}
~~~

Nhưng chỉ làm vậy nếu **business invariant** không đòi DB call và cache update phải nằm trong cùng một atomic protocol. Nếu có yêu cầu ordering/versioning/transactions, cần thiết kế lại kỹ thay vì chuyển code ra ngoài lock một cách máy móc.

### 23.1. Lock scope là vấn đề correctness lẫn latency

- Scope quá rộng: contention, tail latency, khả năng giữ lock qua I/O.
- Scope quá hẹp: phá invariants và tạo check-then-act races.
- Nested locks không nhất quán: deadlock risk.
- Calling externally supplied callbacks dưới lock: khó đoán locks mà code ngoài có thể acquire.
- Synchronized on public objects/String literals: nguy cơ code không liên quan vô tình share monitor.

Hãy ghi rõ state nào thuộc lock nào và transaction boundary ở đâu.

## 24. Một thread dump cho biết gì và không cho biết gì?

**Có thể giúp tìm:**

- Thread names/IDs và trạng thái tại snapshot.
- Stack frame hiện tại.
- Địa điểm chờ acquire monitor/ownable synchronizer.
- Lock owner/held locks khi JVM/tool cung cấp.
- Một số Java-level deadlock cycles.
- JVM runtime/background threads đang hiện diện.

**Không đủ để khẳng định:**

- Tổng CPU usage trong nhiều phút.
- Hoạt động I/O downstream có hoàn thành không.
- Deadlock ở external DB row locks.
- Root cause chính xác của mọi request latency.
- Một WAITING thread là leak hoặc deadlock.
- Background JVM thread đang RUNNABLE nghĩa application threads bị dừng.
- Không có output mới nghĩa Java heap GC ngừng hoạt động.
- Lock acquisition order đã đúng ở mọi code path.

Cần ghép dump với logs, traces, counters và điều kiện cụ thể của service.

## 25. Phương pháp điều tra production theo thứ tự ưu tiên

### Bước 1 — Ghi triệu chứng và phạm vi

- Cả JVM mất response hay chỉ một endpoint?
- Các requests mới còn được nhận?
- Throughput bằng 0 hay chỉ giảm?
- Health check phản ánh đúng business readiness?
- CPU%, RSS, heap/GC, network/DB state?
- Sự cố xảy ra từ deploy/change traffic nào?

### Bước 2 — Ghi JDK và cấu hình

~~~bash
java -version
jcmd <PID> VM.version
jcmd <PID> VM.flags
jcmd <PID> VM.uptime
~~~

VM.flags có thể chứa options liên quan GC/JIT/logging/CPU. Đừng dùng danh sách HotSpot background thread names của một JDK khác làm tiêu chuẩn.

### Bước 3 — Thu nhiều thread dumps an toàn

~~~bash
jcmd <PID> Thread.print -l
~~~

Lưu output kèm timestamp; sau một khoảng hữu ích lại lấy snapshot. Tránh ghi credentials hoặc request payload nhạy cảm vào báo cáo vì stack/dump có thể chứa class names, identifiers và business context.

### Bước 4 — Phân loại affected threads

- BLOCKED trên object monitor nào?
- WAITING tại wait/join/park/latch/future?
- RUNNABLE trong business loop hay native I/O?
- TIMED_WAITING theo timeout bình thường?
- Thread nào là app workers, thread nào là JVM internal?
- Có một owner giữ nhiều locks không?

### Bước 5 — Dựng wait-for graph

Theo các lock owners để tìm cycles. Nếu có cycle, kiểm tra ThreadMXBean/jcmd deadlock report và class/method order dẫn tới inversion.

### Bước 6 — Nếu không thấy cycle, tìm missing progress

- Một future/task đang chờ pool có worker trống không?
- Producer có quên countDown/notify/completion không?
- I/O có timeout và circuit breaker không?
- CPU quota có đang throttle owner thread không?
- Thread đang spin hay có blocking state hợp lệ?
- GC/safepoint pauses hoặc allocation stalls có trùng p99?

### Bước 7 — So timeline JVM vs service

- GC log/safepoint log.
- JFR monitor enter/wait/park, CPU samples.
- Request traces, queue depth và DB timings.
- Compiler.queue/codecache nếu nghi JIT warm-up.
- Thread CPU consumption theo tool phù hợp.

### Bước 8 — Đưa biện pháp đúng nguyên nhân

- Deadlock lock order → thống nhất global order, giảm nesting.
- Long-held lock → giảm I/O trong critical section nếu an toàn.
- Missing signal → sửa predicate/cancellation/notifyAll protocol.
- Pool starvation → loại synchronous dependency trong same pool hoặc tăng capacity phù hợp.
- CPU throttling → sửa quotas/scaling/expensive work, không đổ lỗi “synchronized deadlock”.
- GC pause → tune/giảm allocation, nhưng chỉ sau khi logs xác nhận.
- JIT overhead → warm-up/CPU budget/diagnostics, không xóa synchronized tùy tiện.
- External DB locks → xử lý transaction order ở DB layer.

### Bước 9 — Ngăn tái phát

- Viết stress/liveness tests có timeouts hợp lý.
- Instrument lock wait time, queue age, pending futures, executor active/queued tasks.
- Alert trên request p99, throughput, CPU throttling, GC pauses, backlog.
- Gắn thread names với subsystem ownership.
- Dùng graceful shutdown/cancellation.
- Code review lock graphs khi thêm synchronization mới.

## 26. Tình huống chẩn đoán có lời giải

### Tình huống A — Một thread BLOCKED 2 giây, owner đang xử lý

**Dữ kiện:** T2 chờ M của T1, T1 chưa chờ lock khác; T1 đang persist dữ liệu. Đây là **contention/long-held lock**, chưa chứng minh deadlock. Kiểm tra I/O duration, critical section scope, T1 progress; nếu T1 release thì T2 có thể chạy.

### Tình huống B — Hai thread đều BLOCKED trên monitor của nhau

**Dữ kiện:** T1 holds A waits B; T2 holds B waits A. Có circular wait, là **monitor deadlock** nếu dependencies vẫn tồn tại. Sửa lock ordering, tránh hold-and-wait.

### Tình huống C — 100 pool workers WAITING tại BlockingQueue.take()

**Dữ kiện:** Queue trống, không có requests tồn đọng. Có thể là **idle đúng thiết kế**. Không cần chữa deadlock.

### Tình huống D — 100 workers WAITING nhưng queue requests đang tăng

**Dữ kiện:** Có mismatch giữa incoming tasks và dispatch/permits/wakeup. Cần kiểm tra có đang nhìn đúng queue và đúng executor, producer flow, task submission và pool lifecycle. Không vội kết luận JVM deadlock.

### Tình huống E — G1 concurrent GC thread RUNNABLE, HTTP thread WAITING

**Dữ kiện:** Có thể hai điều kiện độc lập. GC thread làm việc nền; HTTP thread có thể chờ future. Dùng timestamps/GC logs/CPU data để kiểm chứng quan hệ nhân quả.

### Tình huống F — C2 CompilerThread dùng 80% một CPU

**Dữ kiện:** Có thể warm-up hoặc compilation churn; không phải deadlock. Xem Compiler.queue, codecache, JFR/CPU profile, JVM uptime và throughput.

### Tình huống G — Single-thread executor A chờ B do chính pool thực thi

**Dữ kiện:** A giữ sole worker và Future.get chờ B đang queued; đây là **thread-pool starvation deadlock**, không nhất thiết được monitor deadlock detector báo. Sửa task dependency architecture hoặc executor capacity.

### Tình huống H — Thread.sleep trong synchronized

**Dữ kiện:** Thread vẫn giữ monitor, các contenders có thể BLOCKED. Đó là lock contention/possibly poor design, không phải sleep nhả lock.

### Tình huống I — Thread đang Object.wait()

**Dữ kiện:** Waiter đã tạm release receiver monitor, và phải reacquire trước khi return. Nếu notifier quên phát predicate/signal, đây là missing condition progress. Nếu notifier bị kẹt trên một unrelated lock mà waiter vẫn giữ, có thể thành liveness deadlock.

### Tình huống J — findDeadlockedThreads trả null, service vẫn đứng

**Dữ kiện:** Không thấy covered owner cycles tại thời điểm đo, nhưng có thể external resource wait, pool starvation, condition wait, livelock, starvation hoặc CPU/GС problems. Tiếp tục điều tra.

### Tình huống K — HTTP worker RUNNABLE ở native socketRead

**Dữ kiện:** RUNNABLE không phải nhất định busy CPU loop. Có thể thread đang chờ I/O theo implementation; kiểm tra socket/DB/network timeout.

### Tình huống L — JVM chỉ còn daemon threads và sắp thoát

**Dữ kiện:** Đây là process lifetime semantics, không tự chứng minh deadlock. Kiểm tra business tasks nào đang chạy trong daemon workers và liệu shutdown có cancel đúng.

## 27. 25 câu hỏi tự kiểm tra có đáp án

**Câu 1.** Một thread BLOCKED có chứng minh deadlock không?

**Đáp án:** Không. BLOCKED nghĩa chờ object monitor; có thể là contention nếu owner sẽ release.

**Câu 2.** Deadlock monitor kinh điển cần gì ngoài một thread chờ lock?

**Đáp án:** Cần vòng chờ, ví dụ T1 owns A waits B và T2 owns B waits A.

**Câu 3.** RUNNABLE nghĩa thread chắc đang chạy CPU 100%?

**Đáp án:** Không; có thể sẵn sàng chạy, đang native I/O hoặc chưa được scheduler cấp CPU.

**Câu 4.** Thread pool idle thường có WAITING có phải bug?

**Đáp án:** Không nếu đang chờ task/condition hợp lệ và không có backlog bị bỏ quên.

**Câu 5.** Thread.sleep có nhả monitor hiện giữ không?

**Đáp án:** Không.

**Câu 6.** Object.wait có nhả monitor không?

**Đáp án:** Có, nhả receiver monitor theo rules và phải reacquire trước return; không nhả unrelated locks.

**Câu 7.** notifyAll có trao monitor lập tức cho waiter không?

**Đáp án:** Không; notifier vẫn giữ monitor tới khi unlock.

**Câu 8.** WAITING tại Future.get trên single-thread executor có thể là liveness bug gì?

**Đáp án:** Thread-pool starvation/self-deadlock khi task đang chạy chờ task queued vào chính sole worker pool.

**Câu 9.** JVM background GC thread RUNNABLE có chứng minh toàn JVM STW không?

**Đáp án:** Không; concurrent GC work có thể chạy cùng application threads.

**Câu 10.** C2 CompilerThread tiêu CPU có phải monitor deadlock?

**Đáp án:** Không; nó có thể đang biên dịch code.

**Câu 11.** ReentrantLock.lock() wait luôn là BLOCKED?

**Đáp án:** Không; AQS/LockSupport có thể hiển thị WAITING/TIMED_WAITING.

**Câu 12.** API nào tìm monitor-only deadlock cycle?

**Đáp án:** ThreadMXBean.findMonitorDeadlockedThreads().

**Câu 13.** API nào tìm thêm ownable synchronizers khi được hỗ trợ?

**Đáp án:** ThreadMXBean.findDeadlockedThreads().

**Câu 14.** findDeadlockedThreads trả null chứng minh không có liveness bug?

**Đáp án:** Không; detector không bao quát external locks, missing signals, pool starvation và mọi conditions.

**Câu 15.** Dùng jcmd gì in thread dump?

**Đáp án:** jcmd <PID> Thread.print; thêm -l để quan sát java.util.concurrent locks khi hỗ trợ.

**Câu 16.** Vì sao cần nhiều snapshots?

**Đáp án:** Phân biệt temporary wait với trạng thái kéo dài, xác định owner progress và đối chiếu timeline.

**Câu 17.** Biết lock class là Object có đủ xác định same monitor không?

**Đáp án:** Không; cần đúng object identity/lock token, không chỉ class type.

**Câu 18.** Synchronized nested cùng monitor của owner có tự deadlock không?

**Đáp án:** Không, Java intrinsic monitors reentrant.

**Câu 19.** Nested A→B so với B→A tạo rủi ro gì?

**Đáp án:** Lock-order inversion, circular wait deadlock nếu xảy ra interleaving phù hợp.

**Câu 20.** Lock ordering phòng deadlock như thế nào?

**Đáp án:** Mọi paths acquire nhiều locks theo một total order, ngăn hình thành cycles cho các locks liên quan.

**Câu 21.** Chờ DB row lock có được Java monitor detector thấy chắc chắn không?

**Đáp án:** Không; cần DB diagnostics/external resource waits.

**Câu 22.** Một thread ngủ trong synchronized 30s khiến 50 threads BLOCKED. Root cause phù hợp?

**Đáp án:** Long-held monitor do sleep/I/O trong critical section, chưa chắc circular deadlock.

**Câu 23.** Điều gì xảy ra với daemon threads khi chỉ còn chúng trong JVM?

**Đáp án:** JVM có thể kết thúc mà không chờ daemon tasks hoàn tất theo process lifetime semantics.

**Câu 24.** Thread dump hiển thị WAITING trên BlockingQueue và queue trống: sửa gì?

**Đáp án:** Có thể không cần sửa gì; đây là idle hợp lệ. Kiểm tra đúng executor/task backlog.

**Câu 25.** Ba nhóm dữ liệu chính để phân tích một service không tiến triển?

**Đáp án:** Thread dumps/wait-for graph, application traces/queue/resource state, và JVM/OS metrics như CPU/GC/JIT/safepoint timing.

## 28. Checklist cuối bài

- **Deadlock ≠ BLOCKED ≠ WAITING**; các từ biểu thị những lớp thông tin khác nhau.
- Một cạnh waiter→owner chưa phải deadlock; tìm **cycle**.
- **Thread.State** không map 1–1 sang native OS thread states.
- WAITING trên idle queue, join hoặc latch thường là hợp lệ.
- **Thread.sleep không release monitor** và không tạo synchronization.
- **Object.wait release receiver monitor** rồi reacquire trước return.
- **Intrinsic synchronized reentrant**; nested cùng lock không self-deadlock.
- **ReentrantLock** wait có thể hiển thị WAITING/TIMED_WAITING, không nhất thiết BLOCKED.
- **ThreadMXBean** tìm monitor/ownable-synchronizer cycles trong phạm vi hỗ trợ; null không chứng minh toàn hệ live.
- **JVM GC/JIT/runtime background threads** có thể tiếp tục hoạt động khi application workers không tiến triển.
- JIT/GC CPU activity không là bằng chứng nguyên nhân request latency nếu thiếu timeline.
- **jcmd Thread.print -l** và JFR giúp quan sát, nhưng snapshots/events cần được diễn giải đúng.
- **Global lock ordering** là một biện pháp phòng circular wait quan trọng.
- **Missing signal/pool starvation/external DB locks** có thể treo dù Java deadlock detector không báo.
- Giữ lock scope hợp lý, tránh I/O chậm dưới global lock và có cancellation/timeout khi phù hợp.
- Sau khi chẩn đoán, sửa nguyên nhân thực và đo lại throughput/p99/liveness, không chỉ đổi JVM flags.

### Tài liệu chính thức

- **Java SE 17 — Thread.State enum:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.State.html
- **Java SE 17 — Thread API, sleep, join, daemon threads:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
- **Java SE 17 — ThreadMXBean deadlock detection:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ThreadMXBean.html
- **Java SE 17 — ThreadInfo (lock owner, monitor info):** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ThreadInfo.html
- **Java SE 17 — jcmd command and Thread.print:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- **Java SE 17 — jstack command:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/jstack.html
- **Java SE 17 — jfr command:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/jfr.html
- **Java SE 17 — Java Flight Recorder guide:** https://docs.oracle.com/en/java/javase/17/jfapi/
- **Java SE 17 — Object.wait/notify:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html
- **Java SE 17 — LockSupport:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/LockSupport.html
- **Java SE 17 — ReentrantLock:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html
- **Java SE 17 — CountDownLatch:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html
- **JLS SE 17 — Threads and Locks:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html
- **OpenJDK — JEP 444 Virtual Threads (JDK 21):** https://openjdk.org/jeps/444

**Kết luận:** Một ứng dụng Java có thể có nhiều threads BLOCKED/WAITING, có GC/JIT threads RUNNABLE và vẫn không deadlock. **Deadlock thực đòi xác định một vòng phụ thuộc chờ tài nguyên**; còn chờ monitor tạm thời, điều kiện chưa được phát, pool starvation, I/O chậm và JVM background work có nguyên nhân/biện pháp khác nhau. Quy trình đúng là **đọc thread dump, lần theo owner và wait-for graph, đối chiếu nhiều snapshots với request/GC/JIT/CPU timeline**, rồi sửa đúng lock ordering, signals, pool design hoặc resource bottleneck thay vì chẩn đoán từ một Thread.State đơn lẻ.


</details>

- [Quay lại đầu trang](#back-to-top)
