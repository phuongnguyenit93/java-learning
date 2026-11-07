<a id="back-to-top"></a>

# Virtual Threads

## Menu
- [Virtual thread trong Java 21 và mục tiêu thiết kế](#virtual-threads-001)
- [Khác biệt virtual thread với platform thread](#virtual-threads-002)
- [Tạo luồng bằng Thread.ofVirtual() và startVirtualThread()](#virtual-threads-003)
- [newVirtualThreadPerTaskExecutor()](#virtual-threads-004)
- [Thread-per-request cho I/O blocking](#virtual-threads-005)
- [Carrier threads và scheduling của JVM](#virtual-threads-006)
- [Vì sao không pool virtual threads?](#virtual-threads-007)
- [Giới hạn tài nguyên ngoài bằng Semaphore/pool kết nối](#virtual-threads-008)
- [Pinning với monitor/native code ở Java 21](#virtual-threads-009)
- [ThreadLocal và chi phí dữ liệu theo từng virtual thread](#virtual-threads-010)
- [CPU-bound workload không tự nhanh hơn nhờ virtual threads](#virtual-threads-011)
- [Chẩn đoán bằng thread dump và Java Flight Recorder](#virtual-threads-012)
- [Structured Concurrency (preview ở Java 21): vai trò và giới hạn](#virtual-threads-013)
- [Thread.ofVirtual().unstarted(), ThreadFactory và vòng đời khởi chạy](#virtual-threads-014)
- [JDK 21 so với JDK 24+: synchronized thông thường không còn gây pinning kiểu cũ](#virtual-threads-015)
- [Native method và foreign function: các trường hợp pinning còn phải đánh giá](#virtual-threads-016)
- [JFR VirtualThreadPinned, VirtualThreadSubmitFailed và ngưỡng ghi sự kiện](#virtual-threads-017)
- [Giới hạn ThreadMXBean và cách dùng jcmd Thread.dump_to_file cho virtual threads](#virtual-threads-018)

## <a id="virtual-threads-001">Virtual thread trong Java 21 và mục tiêu thiết kế</a>

<details>
<summary>Xem chi tiết</summary>

# Virtual thread trong Java 21 và mục tiêu thiết kế

**Virtual thread** là một loại `java.lang.Thread` được Java runtime quản lý để nhiều tác vụ đang chờ I/O có thể tồn tại đồng thời với ít luồng hệ điều hành hơn. Java 21 đưa virtual threads thành tính năng chính thức qua **JEP 444 (Project Loom)**; không cần bật preview. Đây là nền tảng *concurrency thuần Java*, độc lập với các framework.

Sau bài học, người đọc có thể giải thích vì sao virtual threads ra đời, phân biệt chúng với platform threads, mô tả cơ chế mount/unmount và pinning trong JDK 21, sử dụng API chuẩn, giới hạn tài nguyên và đánh giá đúng hiệu quả.

## 1. Bài toán: nhiều tác vụ chờ nhưng OS threads có hạn

Trong mô hình **thread-per-request**, mỗi yêu cầu được giao cho một platform thread. Một yêu cầu thường chỉ dùng CPU trong thời gian ngắn, phần lớn thời gian còn lại chờ truy vấn dữ liệu, socket hoặc dịch vụ bên ngoài. Platform thread vốn gắn với một OS thread trong vòng đời nên giữ tài nguyên trong cả khoảng chờ. Tạo hàng nghìn OS threads làm tăng chi phí stack, scheduling và chuyển ngữ cảnh; dùng thread pool cố định thì các yêu cầu vượt kích thước pool phải xếp hàng, kể cả khi các worker đang chỉ chờ I/O.

Có thể xử lý vấn đề bằng callback hay `CompletableFuture`, nhưng luồng điều khiển, propagation của exception và việc debug đôi khi khó theo dõi hơn code tuần tự. Virtual threads giúp viết thao tác blocking theo cú pháp đơn giản mà JVM có thể giải phóng carrier trong các khoảng chờ tương thích.

```text
Tác vụ A: chạy Java → chờ I/O ──────────→ chạy tiếp → hoàn thành
Tác vụ B:      chạy Java → chờ I/O ─→ chạy tiếp → hoàn thành
Tác vụ C:           chạy Java → chờ I/O ────→ chạy tiếp
               ^ carrier có thể phục vụ tác vụ sẵn sàng khác
```

Mục tiêu thiết kế của virtual threads gồm:

1. **Tăng khả năng mở rộng của concurrency I/O-bound:** nhiều tác vụ chờ độc lập không buộc số OS threads tăng tương ứng.
2. **Duy trì mô hình dễ đọc:** mỗi tác vụ có một `Thread`, dùng code tuần tự, `try/catch`, call stack và công cụ debugging chuẩn.
3. **Giảm nhu cầu pool các luồng đắt đỏ:** virtual thread đủ nhẹ để đại diện cho *một tác vụ* thay vì là worker tái sử dụng.
4. **Tận dụng API Java quen thuộc:** `Runnable`, `Callable`, `Thread`, `ExecutorService`, `Future` và các công cụ quan sát của JDK vẫn có ích.

**Không có phép màu về tốc độ CPU:** virtual threads chủ yếu cho phép tăng *throughput* khi có nhiều tác vụ đang chờ. Chúng không tự làm một request ít latency hơn, không làm một phép tính CPU nhanh hơn và không tạo thêm lõi CPU.

## 2. Phân biệt platform thread và virtual thread

| Tiêu chí | Platform thread | Virtual thread trên JDK 21 |
|---|---|---|
| Kiểu trong Java | `java.lang.Thread` | Vẫn là `java.lang.Thread` |
| Quan hệ với OS thread | Thường gắn một OS thread cho cả vòng đời | Được mount lên carrier OS thread khi cần chạy |
| Lập lịch | OS lập lịch platform thread | JVM lập lịch virtual thread, OS lập lịch carrier |
| Khi chờ I/O tương thích | OS thread thường tiếp tục bị chiếm | JVM có thể unmount virtual thread, tái sử dụng carrier |
| Chi phí khi có nhiều tác vụ đợi | Lớn hơn | Thường nhỏ hơn đáng kể |
| Mẫu sử dụng | Hữu hạn OS threads hoặc thread pool | Virtual thread mới cho mỗi tác vụ |
| Phù hợp điển hình | Công việc CPU, số luồng hữu hạn hoặc API đặc thù | Nhiều tác vụ blocking I/O độc lập |
| Giới hạn | OS threads, bộ nhớ, CPU | Vẫn có giới hạn heap, sockets, kết nối, CPU, pinning |

Ba thuật ngữ dễ nhầm:

- **Concurrency:** nhiều tác vụ có tiến trình xử lý chồng lên nhau; chúng có thể luân phiên trên một CPU.
- **Parallelism:** nhiều tác vụ thực sự chạy cùng một thời điểm trên các lõi CPU khác nhau.
- **Throughput vs. latency:** số tác vụ hoàn thành trên đơn vị thời gian vs. thời gian một tác vụ hoàn thành. Virtual threads hướng đến throughput ở workload chờ I/O; tăng concurrency không đồng nghĩa latency của từng I/O giảm.

Một hệ thống có 10.000 virtual threads đang chờ network không nhất thiết cần 10.000 OS threads. Nhưng nó vẫn có thể cần nhiều socket, buffer và bộ nhớ tương ứng; số connection của database hay quota API hoàn toàn không được “ảo hóa”.

## 3. Cơ chế carrier, mount, unmount và reschedule

Virtual thread vẫn cần CPU thực, tức vẫn phải chạy trên một **carrier** là platform thread. JVM quản lý việc gắn (*mount*) và tách (*unmount*) virtual thread khỏi carrier:

```text
 Virtual thread A ──┐
 Virtual thread B ──┤    JVM virtual-thread scheduler
 Virtual thread C ──┼──────────────┐
 ...                │              v
 Virtual thread N ──┘       Carrier OS threads
                                │
                                v
                          OS scheduler → CPU
```

1. **Mount:** một virtual thread sẵn sàng chạy được gắn lên carrier; code của nó chạy như Java code thông thường.
2. **Blocking tương thích:** khi tác vụ chờ socket, `Thread.sleep` hoặc một số cơ chế đồng bộ hỗ trợ park, JVM có thể lưu trạng thái tác vụ, unmount nó và trả carrier cho virtual thread khác.
3. **Ready/resume:** khi điều kiện chờ hoàn tất, tác vụ được đưa vào hàng đợi để chạy tiếp; JVM có thể mount nó lên carrier khác.

Bên trong tác vụ, `Thread.currentThread()` trả về **virtual thread của tác vụ**, không trả về carrier. Không nên thiết kế logic phụ thuộc carrier nào đã chạy tác vụ hay thứ tự scheduler chọn các virtual threads.

Với JDK 21, triển khai HotSpot thường dùng một `ForkJoinPool` riêng để lập lịch carrier; mặc định mức parallelism liên quan số bộ xử lý khả dụng, có thuộc tính triển khai `jdk.virtualThreadScheduler.parallelism`. Đây là số carrier hoạt động mục tiêu, **không** là giới hạn tổng số virtual threads. Không chỉnh thuộc tính này theo số lượng requests một cách tùy tiện.

**Giới hạn quan trọng:** không phải mọi thao tác “blocking” đều giải phóng carrier. Một số file I/O trên JDK 21, lời gọi native và blocking trong lúc pinned có thể giữ OS thread. Hiệu quả thực tế phụ thuộc loại I/O, phiên bản JDK và thư viện.

## 4. API `Thread` trong Java 21

Các API chính:

- `Thread.ofVirtual().start(Runnable)`: tạo và bắt đầu virtual thread.
- `Thread.ofVirtual().name("...").start(Runnable)`: đặt tên để quan sát.
- `Thread.startVirtualThread(Runnable)`: factory tĩnh viết ngắn.
- `Thread.currentThread().isVirtual()`: kiểm tra thread hiện tại có phải virtual không.
- `Thread.join()`: chờ tác vụ kết thúc.

Ví dụ **độc lập, chạy bằng JDK 21**; lưu thành `VirtualThreadHello.java`:

```java
public class VirtualThreadHello {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = Thread.ofVirtual()
                .name("vt-demo")
                .start(() -> {
                    Thread current = Thread.currentThread();
                    System.out.println("virtual=" + current.isVirtual());
                    System.out.println("name=" + current.getName());
                });
        worker.join();
    }
}
```

Biên dịch và chạy:

```bash
javac VirtualThreadHello.java
java VirtualThreadHello
```

Đầu ra xác định:

```text
virtual=true
name=vt-demo
```

`join()` giữ main sống đến khi virtual thread kết thúc. Điều này quan trọng vì **virtual threads là daemon threads**, không tự giữ JVM sống khi tất cả non-daemon threads đã kết thúc. Mặc định virtual thread không có tên; đặt tên có chủ đích giúp tìm dấu vết.

Một cú pháp tương đương:

```java
Thread worker = Thread.startVirtualThread(() -> doWork());
worker.join();
```

Đây chỉ là ví dụ minh họa API, `doWork()` là phương thức giả định chứ đoạn mã không phải chương trình độc lập.

## 5. Một virtual thread cho mỗi tác vụ bằng `ExecutorService`

`Executors.newVirtualThreadPerTaskExecutor()` tạo executor mà **mỗi tác vụ được giao có một virtual thread mới**, không tái sử dụng một pool virtual threads cố định. `Future` trả về giúp xử lý kết quả/lỗi và đợi các tác vụ.

Ví dụ lưu thành `VirtualThreadTasks.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VirtualThreadTasks {
    public static void main(String[] args) throws Exception {
        List<Future<String>> results = new ArrayList<>();

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= 3; i++) {
                int taskId = i;
                results.add(executor.submit(() -> {
                    Thread.sleep(100); // Mô phỏng chờ, không phải network I/O.
                    return "task " + taskId
                            + ": virtual=" + Thread.currentThread().isVirtual();
                }));
            }

            // Đọc Future theo thứ tự submit, dù hoàn thành có thể khác thứ tự.
            for (Future<String> future : results) {
                System.out.println(future.get());
            }
        }
    }
}
```

```bash
javac VirtualThreadTasks.java
java VirtualThreadTasks
```

Kết quả:

```text
task 1: virtual=true
task 2: virtual=true
task 3: virtual=true
```

`Thread.sleep(100)` giúp quan sát kiểu chờ; ví dụ này không chứng minh tốc độ hay tính năng I/O của driver. `Future.get()` đợi và nhận kết quả, ném `ExecutionException` nếu code tác vụ thất bại. `try-with-resources` dùng `ExecutorService.close()` để chờ kết thúc theo hợp đồng API; không cần tự kết hợp shutdown thủ công trong ví dụ đơn giản. Với Java 21, mã này không yêu cầu cờ `--enable-preview`.

### Vì sao không pool virtual threads?

Thread pool truyền thống hữu ích khi cần **giới hạn số OS threads đắt đỏ**. Với virtual threads, nên tạo một thread cho mỗi tác vụ và để JVM lập lịch. Pool cố định virtual threads có thể vô tình tạo hàng chờ dù các thread pool worker chỉ đang chờ I/O.

Điều đó **không cho phép gửi vô hạn tác vụ**. Phải quản lý riêng các giới hạn của tài nguyên: connection pool, quota API, RAM, socket, tốc độ tiếp nhận và số tác vụ chờ. Dùng `Semaphore`, bounded queue, timeout, backpressure hoặc giới hạn kết nối để bảo vệ đúng tài nguyên.

## 6. Ví dụ giới hạn một tài nguyên có công suất hữu hạn

Giả sử có 12 tác vụ chờ sử dụng dịch vụ bên ngoài, nhưng hệ thống chỉ cho phép tối đa 3 lượt truy cập cùng lúc. Vẫn có thể tạo 12 virtual threads và dùng `Semaphore` giới hạn **vùng sử dụng tài nguyên**, không giới hạn bằng số thread của một pool.

Lưu thành `VirtualThreadLimit.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualThreadLimit {
    public static void main(String[] args) throws Exception {
        Semaphore permits = new Semaphore(3);
        AtomicInteger completed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 12; i++) {
                futures.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        Thread.sleep(50); // Giả lập I/O sử dụng tài nguyên.
                        completed.incrementAndGet();
                    } finally {
                        permits.release();
                    }
                    return null;
                }));
            }

            for (Future<?> future : futures) {
                future.get();
            }
        }

        System.out.println("completed=" + completed.get());
    }
}
```

```bash
javac VirtualThreadLimit.java
java VirtualThreadLimit
```

Kết quả xác định:

```text
completed=12
```

Có 12 tác vụ được giao, nhưng chỉ tối đa 3 tác vụ nằm giữa `acquire()` và `release()` cùng lúc. Tác vụ khác chờ giấy phép. `finally` đảm bảo hoàn trả permit sau khi acquire thành công, ngay cả khi phần việc trong `try` bị ngắt hoặc báo lỗi. `AtomicInteger` cập nhật an toàn từ nhiều threads. Trong hệ thống thật, chọn số permits theo kết nối, quota hoặc khả năng của dịch vụ; cấu hình quá cao vẫn có thể làm quá tải nguồn lực bên dưới.

## 7. Pinning: giới hạn rất quan trọng trên **JDK 21**

**Pinning** nghĩa là virtual thread bị giữ trên carrier, không thể unmount khi nó phải chờ. Trong JDK 21, hai trường hợp chính:

1. Code virtual thread đang thực thi trong `synchronized` block/method và gặp blocking.
2. Code đang thực thi native method hoặc foreign function khiến JVM không thể unmount.

Đoạn dưới đây *chỉ minh họa*, các biến/hàm không được định nghĩa để biên dịch độc lập:

```java
synchronized (sharedLock) {
    blockingRemoteCall(); // Blocking lâu ở đây có thể pin carrier (JDK 21).
}
```

**Pinning không trực tiếp làm sai kết quả hoặc đồng nghĩa deadlock.** Tác động là hiệu suất: nếu nhiều virtual threads bị pin lâu, số carrier rảnh ít đi, làm giảm khả năng phục vụ tác vụ mới. `synchronized` cực ngắn bảo vệ một phép gán hoặc cập nhật bộ nhớ thường không đáng lo. Rủi ro là **blocking I/O dài và thường xuyên** trong vùng synchronized.

Ưu tiên thiết kế là chụp trạng thái dưới khóa, thả khóa, gọi I/O ngoài khóa, rồi cập nhật trạng thái đúng cách. Nếu thực sự cần giữ khóa qua lời gọi blocking và không thể thay đổi mô hình, `java.util.concurrent.locks.ReentrantLock` có thể tránh pinning do intrinsic monitor trên JDK 21:

```java
lock.lock();
try {
    blockingRemoteCall();
} finally {
    lock.unlock();
}
```

Đây cũng là mã minh họa chưa độc lập: `lock` và `blockingRemoteCall()` cần được định nghĩa. **ReentrantLock không xóa tranh chấp khóa**; các tác vụ dùng cùng một khóa vẫn có thể chờ nhau. Không thay mọi `synchronized` bằng `ReentrantLock` nếu không có dấu hiệu pinning ảnh hưởng tới hệ thống.

**Phân biệt phiên bản:** hạn chế pinning do `synchronized` là đặc trưng của **JDK 21–23**; **JDK 24** đã thay đổi cơ chế này qua JEP 491. Bài học và ví dụ về pinning ở đây chủ đích mô tả **Java 21**.

## 8. Các giới hạn và bẫy thường gặp

**CPU-bound:** Công việc tính toán liên tục, nén dữ liệu hoặc chạy thuật toán nặng vẫn bị giới hạn bởi số lõi và CPU time. Tạo vô số virtual threads cho bài toán CPU thuần không tăng tốc phép tính; có thể làm overhead tăng. Hãy đo và dùng mức parallelism phù hợp.

**Blocking không hỗ trợ unmount:** Đừng suy luận rằng chỉ vì API blocking thì JVM sẽ giải phóng carrier. Kiểm tra thư viện, JDBC driver, native calls, file I/O và dấu hiệu pinning trên phiên bản JDK thật.

**ThreadLocal:** Virtual threads hỗ trợ `ThreadLocal` và `InheritableThreadLocal`; tuy nhiên một ứng dụng có thể tạo lượng rất lớn threads. Nếu mỗi thread lưu buffer hoặc cache lớn trong `ThreadLocal`, tổng bộ nhớ sẽ tăng mạnh. Tránh dùng thread-local để giữ tài nguyên đắt đỏ cho từng tác vụ và gọi `remove()` ở `finally` khi phải tự quản lý giá trị.

**Cancellation và timeout:** `Future.cancel(true)` yêu cầu interrupt tác vụ đang chạy; hiệu quả phụ thuộc code/thư viện có đáp ứng interrupt hay không. `Future.get(timeout, unit)` ném `TimeoutException` khi hết giờ chờ nhưng **không tự hủy tác vụ**. Thiết kế timeout cả ở tầng I/O và điểm chờ; xử lý ngắt phù hợp với hợp đồng chương trình.

**Daemon lifecycle:** Virtual threads không giữ JVM sống. `join()`, quản lý bằng `ExecutorService` hoặc một cơ chế đợi rõ ràng giúp tránh thoát sớm khi công việc chưa xong.

**Một `Thread` không thể start lại:** Mỗi virtual thread là một `Thread` thật; không gọi `start()` lần hai. Tác vụ mới dùng virtual thread mới.

**Giới hạn thực tế:** Nhiều virtual threads vẫn tiêu thụ stack frames, object, bộ nhớ giữ bởi tác vụ, sockets và tài nguyên ngoài. Chặn tải ở điểm nhận nếu số request có thể vượt năng lực phục vụ; virtual threads không thay thế rate limiting hoặc backpressure.

## 9. Đo đạc và chẩn đoán trên Java 21

Nên so sánh cùng workload trước và sau khi đổi executor: **throughput**, p95/p99 latency, CPU, heap, số request đang chờ, connection pool và lỗi timeout. Một demo chỉ gọi `sleep` không phải benchmark đại diện cho production.

Công cụ JDK 21:

- **JFR:** sự kiện `jdk.VirtualThreadPinned` để phát hiện pinning; cấu hình mặc định báo pinning trên ngưỡng khoảng 20 ms. `jdk.VirtualThreadStart` và `jdk.VirtualThreadEnd` có thể bật khi cần (mặc định thường tắt), cùng `jdk.VirtualThreadSubmitFailed`.
- **Trace pinning:** chạy ứng dụng với `-Djdk.tracePinnedThreads=full` hoặc `short` trong JDK 21 để xem stack liên quan đến pinning; cân nhắc dung lượng log.
- **Thread dump:** `jcmd <PID> Thread.dump_to_file -format=json threads.json` giúp quan sát cả virtual và platform threads; dạng dump này không phải một snapshot đồng bộ tuyệt đối.

Các lệnh dưới đây cần thay PID bằng tiến trình thực, còn file JFR phải được ghi trước:

```bash
jcmd 12345 Thread.dump_to_file -format=json threads.json
jfr print --events jdk.VirtualThreadPinned recording.jfr
```

Khi thấy throughput thấp, hãy kiểm tra CPU có quá tải không, connection pool có hết không, virtual threads có bị pin bởi `synchronized`/native không, hay thực chất dịch vụ bên ngoài đang chậm. Chỉ chỉnh scheduler song song sau khi có bằng chứng rõ ràng; không xem nó như nút điều chỉnh “số virtual threads tối đa”.

## 10. Quy tắc chọn và áp dụng

**Nên chọn virtual threads** cho nhiều tác vụ độc lập có tỷ lệ chờ network I/O, JDBC hoặc dịch vụ ngoài cao; đặc biệt khi muốn code tuần tự dễ debug và thư viện blocking được JVM hỗ trợ tốt.

**Không kỳ vọng lợi ích đáng kể** nếu nút thắt là CPU, tranh chấp chung một khóa, giới hạn kết nối, bộ nhớ, database yếu hoặc API bên ngoài có quota thấp. Phải giải quyết nút thắt thực và xác nhận bằng đo đạc.

Quy trình gợi ý:

1. Phân loại tác vụ: bao nhiêu CPU, bao nhiêu thời gian blocking I/O?
2. Dùng một virtual thread cho một tác vụ phù hợp; quản lý kết quả và vòng đời.
3. Giới hạn đúng các tài nguyên khan hiếm (`Semaphore`, connection pool, quota, timeout).
4. Xác minh tương thích thư viện, pinning và hành vi interruption/shutdown.
5. Đo hiệu năng và mức sử dụng tài nguyên trong điều kiện gần thực tế.

**Tư duy cốt lõi:** virtual threads giúp *concurrency có nhiều khoảng chờ* trở nên rẻ và dễ viết hơn; chúng không thay thế việc thiết kế giới hạn tài nguyên, tính đúng đắn của đồng bộ hoặc năng lực CPU.

### Câu hỏi tự kiểm tra

1. Vì sao 10.000 tác vụ cùng chờ socket không nhất thiết cần 10.000 OS threads?
2. `Thread.currentThread()` gọi trong virtual thread trả về virtual thread hay carrier?
3. Sự khác nhau giữa tăng throughput và giảm latency của một request là gì?
4. Tại sao `synchronized` bao quanh network I/O lâu là một vấn đề trên JDK 21?
5. Một database chỉ cho phép 8 connections: nên giới hạn số virtual threads hay số thao tác dùng connection đồng thời?
6. `Future.get(timeout, unit)` hết hạn có tự hủy tác vụ không?

**Đáp án gợi ý:** (1) JVM unmount các virtual threads đang chờ I/O tương thích và tái sử dụng carrier; (2) virtual thread; (3) số việc hoàn thành trên thời gian và thời gian một việc; (4) carrier bị pin khi blocking; (5) lượt sử dụng tài nguyên/connection; (6) không, muốn hủy phải yêu cầu rõ ràng.

### Nguồn chính thức

- [OpenJDK JEP 444 — Virtual Threads](https://openjdk.org/jeps/444)
- [Oracle Java SE 21 — Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
- [Java SE 21 — `Thread` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html)
- [Java SE 21 — `Executors` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-002">Khác biệt virtual thread với platform thread</a>

<details>
<summary>Xem chi tiết</summary>

# Virtual Threads và Platform Threads trong Java: khác biệt và cách lựa chọn

> Phạm vi: cơ chế luồng của Java nói chung, tập trung vào Java 21 trở lên. Virtual Threads là tính năng chính thức từ **Java 21** (JEP 444). Các ví dụ bên dưới dùng API chuẩn của JDK, không phụ thuộc framework.

## 1. Bản chất: hai loại `Thread`, hai cách dùng tài nguyên

Trong Java, `Thread` biểu diễn một luồng thực thi: có stack, trạng thái, quyền bị ngắt và danh tính riêng. Từ Java 21 có hai loại:

- **Platform thread**: một `java.lang.Thread` được JVM gắn với một luồng hệ điều hành (OS thread) trong suốt vòng đời. Hệ điều hành lập lịch thực thi luồng đó. Tạo nhiều platform thread tiêu tốn stack native và tài nguyên kernel; số lượng thực tế bị giới hạn khá sớm.
- **Virtual thread**: vẫn là `java.lang.Thread`, nhưng JVM quản lý việc thực thi và lưu/khôi phục trạng thái khi luồng chờ. Virtual thread chỉ **tạm thời chạy trên** một platform thread gọi là **carrier thread**. Nhiều virtual thread có thể lần lượt dùng một số lượng carrier nhỏ, nên phù hợp với lượng lớn tác vụ chờ I/O.

Cả hai vẫn dùng mô hình mã tuần tự quen thuộc: gọi hàm, `try/catch/finally`, `ThreadLocal`, `interrupt()`, `join()`, lock, và các API `java.util.concurrent`. Virtual thread không có CPU riêng, cũng không khiến một công việc tính toán thuần CPU tự chạy nhanh hơn.

### Hình dung lập lịch

```text
Platform threads (thường 1:1):
Task A -> Java platform thread A -> OS thread A
Task B -> Java platform thread B -> OS thread B
Task C -> Java platform thread C -> OS thread C

Virtual threads (nhiều tác vụ chia sẻ carriers):
Task A -> virtual thread A --mount--> carrier 1 -> OS thread 1
          khi chờ I/O --unmount--> carrier 1 rảnh
Task B -> virtual thread B --mount--> carrier 1 -> OS thread 1
Task C -> virtual thread C --mount--> carrier 2 -> OS thread 2
```

**Mount**: JVM cho virtual thread tiếp tục chạy trên một carrier. **Unmount**: virtual thread tạm ngừng, lưu trạng thái tiếp tục và nhường carrier để tác vụ khác chạy; điều này thường xảy ra với thao tác chờ được JDK hỗ trợ, ví dụ nhiều thao tác I/O blocking và `Thread.sleep`. Khi I/O sẵn sàng, virtual thread được đưa vào lịch chạy lại, có thể trên carrier khác.

Vì vậy, một virtual thread **không có quan hệ 1:1 cố định** với một OS thread. Carrier vẫn là platform thread có thật, và các tác vụ đang chạy Java code vẫn cạnh tranh CPU như bình thường.

## 2. Bảng so sánh các điểm cần nhớ

| Tiêu chí | Platform thread | Virtual thread |
|---|---|---|
| Ai lập lịch? | OS lập lịch OS thread tương ứng | JVM lập lịch virtual thread lên carrier; OS vẫn lập lịch carrier |
| Gắn với OS thread | Thường giữ OS thread trong suốt vòng đời | Chỉ gắn carrier khi đang được thực thi; có thể unmount khi chờ |
| Chi phí tạo và duy trì | Tương đối cao: native stack, OS resources | Thấp hơn nhiều cho tác vụ chủ yếu chờ; stack/continuation do JVM quản lý |
| Quy mô thực tế | Thường hàng chục/hàng trăm hoặc vài nghìn tùy môi trường | Có thể hàng chục nghìn đến nhiều hơn khi tài nguyên và workload cho phép |
| `Thread.sleep`, I/O blocking | OS thread thường cũng bị chiếm trong lúc chờ | Với thao tác hỗ trợ unmount, carrier có thể phục vụ việc khác |
| Công việc CPU-bound | Phù hợp nếu số luồng chạy song song được giới hạn hợp lý | Không tăng số lõi CPU hay tăng throughput phép tính |
| Quản lý vòng đời | Thường dùng `newFixedThreadPool` / `newCachedThreadPool` để tái dùng | Tạo một virtual thread cho mỗi tác vụ; **không pool virtual threads** |
| Mức độ tương thích | `Thread` và `java.util.concurrent` | Cũng là `Thread` và hỗ trợ hầu hết API hiện có; kiểm tra giới hạn của thư viện/native call |
| `ThreadLocal` | Có; dễ tái dùng trong pooled threads | Có; nhưng nhiều tác vụ = nhiều bản sao state, dễ đội bộ nhớ nếu cache lớn |
| Daemon | Có thể daemon hoặc non-daemon | Luôn là daemon; không tự giữ JVM sống |
| Chặn do lock/native | Có thể block OS thread | Có thể bị **pinning** hoặc block carrier tùy thao tác và phiên bản JDK |
| Mục tiêu | Số luồng hợp lý, CPU tasks, xử lý nền lâu sống | Nhiều tác vụ độc lập, phần lớn thời gian chờ I/O |

“Virtual thread nhẹ” là nhận xét tương đối, **không đồng nghĩa miễn phí**: mỗi virtual thread vẫn giữ object, stack frame/state, `ThreadLocal` và tài nguyên mà tác vụ sử dụng. Không thể suy ra một ngưỡng số luồng cố định cho mọi máy.

## 3. Ví dụ chạy được: kiểm tra hai loại thread

Tạo tệp **`VirtualVsPlatform.java`**:

```java
public class VirtualVsPlatform {
    public static void main(String[] args) throws InterruptedException {
        Thread platform = Thread.ofPlatform()
                .name("platform-demo")
                .unstarted(() -> { });

        Thread virtual = Thread.ofVirtual()
                .name("virtual-demo")
                .unstarted(() -> { });

        platform.start();
        virtual.start();

        platform.join();
        virtual.join();

        System.out.println("platform.isVirtual() = " + platform.isVirtual());
        System.out.println("virtual.isVirtual() = " + virtual.isVirtual());
    }
}
```

Biên dịch và chạy bằng **JDK 21+**:

```bash
javac VirtualVsPlatform.java
java VirtualVsPlatform
```

Kết quả chính xác:

```text
platform.isVirtual() = false
virtual.isVirtual() = true
```

`Thread.ofPlatform()` / `Thread.ofVirtual()` tạo builder; `unstarted(...)` tạo luồng chưa khởi chạy; `start()` mới bắt đầu thực thi. Gọi `join()` để bảo đảm luồng kết thúc trước khi in kết quả. `isVirtual()` kiểm tra loại, không phụ thuộc tên thread. Cách ngắn gọn khác là `Thread.startVirtualThread(runnable)`: vừa tạo vừa khởi chạy virtual thread.

**Không nhầm:** virtual thread không phải `CompletableFuture`, cũng không phải object của executor. Executor là cơ chế nhận và quản lý task; task được executor có thể thực thi trên loại thread tương ứng.

## 4. Chờ I/O: tại sao virtual threads có thể tăng throughput

Giả sử mỗi yêu cầu cần khoảng 20 ms làm việc trên CPU và 180 ms chờ một dịch vụ mạng. Một platform thread xử lý tuần tự từng yêu cầu sẽ bị chiếm cả 200 ms. Nếu dùng 100 platform threads, phần lớn thời gian nhiều OS threads đang chờ. Với virtual threads, khi chờ I/O tương thích, carrier có thể nhận task khác, giúp phục vụ nhiều yêu cầu đồng thời mà không cần một OS thread cho mỗi request.

Đây là **lợi ích về khả năng phục vụ nhiều tác vụ đồng thời (scalability/throughput)**; không bảo đảm một request đơn lẻ có latency thấp hơn. Tổng năng lực còn bị giới hạn bởi CPU, database connections, socket/file descriptor, rate limit, remote service và bộ nhớ.

Có thể dùng quy tắc xấp xỉ `L = λ × W` (Little's Law) để hình dung: nếu dòng công việc ổn định đạt 1.000 yêu cầu/giây và mỗi yêu cầu ở trong hệ thống 0,2 giây, trung bình sẽ có khoảng 200 yêu cầu đang xử lý/chờ. Đây là phép tính công suất/độ đồng thời, **không phải phép dự đoán** rằng cứ tạo 200 virtual threads là sẽ đạt 1.000 req/s.

Tác vụ phù hợp: gọi HTTP/database, giao tiếp socket, pipeline đọc kết quả từ nhiều nguồn, hoặc xử lý yêu cầu dạng “mỗi yêu cầu là một flow tuần tự” mà phần lớn thời gian đợi kết quả.

Tác vụ không tự hưởng lợi: nén/giải mã/tính toán số lớn, tính toán ma trận hay vòng lặp CPU-bound thuần túy. Nếu có 8 lõi CPU, 100.000 virtual threads cùng tính toán không biến 8 lõi thành 100.000 lõi; cạnh tranh CPU và scheduler overhead vẫn có.

## 5. Ví dụ chạy được: một virtual thread cho mỗi task

Tạo tệp **`VirtualTaskExecutor.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VirtualTaskExecutor {
    public static void main(String[] args) throws Exception {
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> results = new ArrayList<>();

            for (int i = 1; i <= 3; i++) {
                int taskId = i;
                results.add(executor.submit(() -> {
                    Thread.sleep(100); // Mô phỏng thời gian chờ
                    return "task-" + taskId
                            + ": virtual=" + Thread.currentThread().isVirtual();
                }));
            }

            // Đọc theo thứ tự submit, không phụ thuộc thứ tự hoàn thành.
            for (Future<String> result : results) {
                System.out.println(result.get());
            }
        }
    }
}
```

Chạy:

```bash
javac VirtualTaskExecutor.java
java VirtualTaskExecutor
```

Kết quả chính xác:

```text
task-1: virtual=true
task-2: virtual=true
task-3: virtual=true
```

Mỗi lần `submit` sẽ chạy task bằng một **virtual thread mới**, không giữ sẵn một pool virtual threads để tái dùng. `Future.get()` đợi kết quả hoặc ném lỗi nếu task thất bại; lấy theo thứ tự danh sách khiến phần in ra xác định dù thứ tự scheduler không xác định. `try-with-resources` gọi `ExecutorService.close()`, chờ các task đã gửi kết thúc theo hợp đồng của API.

`Thread.sleep(100)` chỉ mô phỏng “đang chờ” để nhìn rõ mô hình; thời gian chạy thực tế phụ thuộc máy và lập lịch. Trong chương trình thực, phần lớn lợi ích phát sinh từ **I/O blocking tương thích** như network I/O, không phải vì thêm sleep vào code làm ứng dụng nhanh hơn.

### Cùng kiểu công việc nếu dùng platform thread pool

```java
// Đoạn minh họa: các import và main nằm trong ví dụ bên trên.
try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
    // 3 platform worker threads được tái dùng cho các task.
    // Task vượt quá số worker được đưa vào hàng đợi.
}
```

Pool platform threads hữu ích khi muốn giới hạn số worker tốn tài nguyên hoặc số task CPU thực thi song song. Với `newVirtualThreadPerTaskExecutor()`, số virtual threads theo số task được gửi, **không áp dụng giới hạn 3 workers như pool trên**. Do đó phải giới hạn tài nguyên downstream riêng.

## 6. Không pool virtual threads; giới hạn tài nguyên đúng chỗ

Sai lầm phổ biến là coi giới hạn kích thước platform pool là giới hạn nghiệp vụ rồi chuyển sang virtual threads và để mọi task cùng mở kết nối bên ngoài. Virtual threads không làm database, HTTP server hay disk nhanh thêm. Nếu hệ thống chỉ cho phép 10 kết nối hoặc tối đa 10 request đồng thời, ta cần giữ giới hạn đó.

Dùng `Semaphore` để giới hạn truy cập tài nguyên cụ thể. Ví dụ sau là **đoạn minh họa bên trong một phương thức**; `callRemoteService()` đại diện cho lời gọi I/O thật:

```java
Semaphore permits = new Semaphore(10); // Khởi tạo và dùng chung giữa các task

void limitedCall() throws InterruptedException {
    permits.acquire();                 // Chờ tới khi có permit
    try {
        callRemoteService();           // Có nhiều nhất 10 lời gọi cùng vào đây
    } finally {
        permits.release();             // Không rò permit khi có exception
    }
}
```

Trong mã thật cần `import java.util.concurrent.Semaphore;` và định nghĩa `callRemoteService()`; đoạn trên **không phải một lớp biên dịch độc lập**. Nếu `acquire()` bị interrupt thì chưa lấy permit, do đó nằm **ngoài** `try/finally` giải phóng. Không dùng số virtual threads để thay thế giới hạn connection pool hoặc rate limit vốn đã có. Đối với công việc CPU-bound, dùng executor bị giới hạn mức song song phù hợp với CPU thay vì vô hạn task đồng thời.

## 7. Virtual thread blocking khác gì pinning?

**Blocking thông thường:** virtual thread đợi thao tác mà JVM/JDK có thể unmount; carrier được trả về cho scheduler. Ví dụ `Thread.sleep(...)` hoặc nhiều thao tác chờ I/O mạng do JDK hỗ trợ.

**Pinning:** virtual thread vẫn bám trên carrier trong lúc không thể unmount; nếu cùng lúc thực hiện một thao tác chờ dài, OS thread carrier cũng bị chiếm. Khi pinning xảy ra thường xuyên, nhiều carrier bị giữ và ưu thế mở rộng có thể giảm. Pinning không tự làm kết quả tính toán sai.

Phải **phân biệt theo phiên bản JDK**:

- **JDK 21–23:** thực hiện blocking trong `synchronized` block/method có thể pin carrier; các lời gọi native/foreign cũng có thể pin. Chỉ nên thay `synchronized` bằng `ReentrantLock` tại các đoạn **blocking lâu hoặc lặp lại nhiều** khi đã quan sát thấy bottleneck; không cần thay mọi synchronized bảo vệ cập nhật bộ nhớ rất ngắn.
- **JDK 24+ (JEP 491):** JVM đã thay đổi monitor implementation để virtual thread có thể chờ/đi qua `synchronized` mà không bị pin chỉ vì monitor. Vì thế câu “mọi synchronized đều pin virtual thread” **không còn đúng** trên các JDK mới. Một số trường hợp liên quan native/foreign hoặc thao tác runtime đặc biệt vẫn cần kiểm tra thực tế.

Hãy đo trên **phiên bản JDK triển khai thật**, dùng JDK Flight Recorder (JFR) và sự kiện `jdk.VirtualThreadPinned` khi phù hợp, đồng thời theo dõi throughput, latency, CPU, carriers và giới hạn kết nối. Đừng kết luận pinning chỉ từ việc thấy lock hay thời gian chờ. Khi nâng cấp JDK, đánh giá lại workaround cũ dựa trên đo lường.

## 8. Những đặc tính `Thread` vẫn phải quản lý

**Interrupt và hủy:** virtual thread hỗ trợ `interrupt()` như platform thread. Blocking APIs như `Thread.sleep` có thể ném `InterruptedException`. Nếu một hàm không thể xử lý ngay, thường cần truyền tiếp exception hoặc khôi phục cờ bằng `Thread.currentThread().interrupt()`; đừng nuốt tín hiệu hủy rồi tiếp tục vô thời hạn. `Future.cancel(true)` yêu cầu ngắt task khi phù hợp, không bảo đảm một lời gọi I/O tùy ý có thể dừng tức thì.

**Lỗi task:** exception trong `Runnable` có thể được báo qua `UncaughtExceptionHandler`; với `ExecutorService.submit`, lỗi task nằm trong `Future` và `get()` có thể ném `ExecutionException`. Luôn có chiến lược timeout/hủy và xử lý thất bại phù hợp với nghiệp vụ; virtual thread không tự sinh retry hoặc timeout.

**ThreadLocal:** mỗi virtual thread có vùng dữ liệu ThreadLocal riêng; API này vẫn hoạt động. Nhưng mô hình “mỗi task tạo một virtual thread mới” khiến dữ liệu cache đắt tiền trong ThreadLocal thường bị tạo lại cho từng task; số lượng task lớn có thể tăng bộ nhớ/GC. Dùng tham số truyền rõ ràng khi thuận tiện; kiểm tra các thư viện dựa vào ThreadLocal và tránh giữ đối tượng lớn không cần thiết.

**Daemon và vòng đời JVM:** virtual threads luôn là daemon. Một JVM chỉ còn virtual threads đang chạy có thể kết thúc khi tất cả non-daemon threads đã xong. Với ví dụ tự viết, dùng `join()`, `Future.get()` hoặc quản lý vòng đời executor để đợi tác vụ quan trọng; đừng giả định virtual thread sẽ tự giữ process sống.

**Dùng chung dữ liệu:** virtual thread không loại bỏ race condition, deadlock, data race hay vấn đề visibility. Các nguyên tắc `volatile`, `synchronized`, lock, atomics, thread-safe collection và happens-before vẫn giữ nguyên. Ít OS threads hơn không có nghĩa shared mutable state an toàn hơn.

**Tác vụ rất dài hoặc giới hạn ở thư viện:** virtual threads thường phù hợp các công việc có thời hạn rõ ràng, xử lý một request/task rồi kết thúc. Không phải tất cả native calls, driver, thư viện bên thứ ba hoặc lời gọi blocking đều unmount giống nhau. Cần thử nghiệm chính workload và JDK được triển khai.

## 9. Chọn mô hình nào trong thực tế?

| Tình huống | Gợi ý | Lý do |
|---|---|---|
| Rất nhiều request độc lập, chủ yếu chờ network/database | Virtual thread cho mỗi request/task | Viết code tuần tự dễ đọc, carrier có thể được tái dùng trong thời gian chờ |
| Công việc CPU-bound liên tục | Platform pool giới hạn theo tài nguyên CPU, hoặc dùng chiến lược parallelism có kiểm soát | Năng lực CPU có giới hạn; virtual thread không tạo thêm lõi |
| Ứng dụng cần tối đa N lời gọi vào một API/database | Virtual threads kèm `Semaphore`/pool kết nối/rate limiter | Giới hạn tài nguyên nghiệp vụ độc lập với loại thread |
| Tác vụ nền lâu sống, cần quản trị số worker rõ ràng | Đánh giá platform threads và executor tương ứng | Thread không nhất thiết nhiều và phải sống lâu |
| Nhiều blocking call nằm trong monitor trên JDK 21–23 | Đo pinning, cải thiện hotspot, cân nhắc JDK 24+ | Tránh giữ carrier quá lâu ở những đường thực thi thường xuyên |
| Đang dùng thư viện có JNI/native blocking | Benchmark và profile thực tế trước khi chuyển | Một số thao tác có thể giữ carrier hoặc chặn tiến độ |

### Checklist chuyển đổi có kiểm soát

1. Xác định workload: tỷ lệ thời gian **CPU** so với **chờ I/O**, số request đồng thời và ràng buộc tài nguyên thực tế.
2. Dùng JDK 21+; thay mô hình platform pool lớn cho I/O bằng `Executors.newVirtualThreadPerTaskExecutor()` khi phù hợp.
3. Giữ timeout, cancellation, rate limit, connection pool và cơ chế shutdown rõ ràng. Nếu cần giới hạn truy cập tài nguyên, chọn `Semaphore` hoặc giới hạn có sẵn của resource pool.
4. Audit `ThreadLocal`, blocking native calls và điểm lock thường xuyên; với JDK 21–23 đặc biệt chú ý blocking trong `synchronized`.
5. Load test cùng một workload trên hai cấu hình, đo **throughput, p95/p99 latency, CPU, bộ nhớ, số kết nối, lỗi/timeouts**; chỉ kết luận khi số liệu và giới hạn bottleneck được xác định.

## 10. Hiểu đúng qua vài câu hỏi tự kiểm tra

- **“Một triệu virtual threads thì một triệu tác vụ chạy CPU đồng thời?”** Không. Tại mỗi thời điểm, những tác vụ đang chạy vẫn cần carrier và CPU thật; nhiều virtual threads có thể ở trạng thái chờ.
- **“Virtual thread luôn nhanh hơn platform thread?”** Không. Virtual threads chủ yếu giúp mở rộng số lượng tác vụ blocking, không cải thiện tốc độ CPU của một task riêng lẻ.
- **“Vẫn phải pool virtual threads để tránh lãng phí?”** Không. Virtual threads được thiết kế để tạo theo từng task; hạn chế downstream bằng resource limit thích hợp.
- **“`synchronized` luôn pin virtual thread?”** Không. Nguy cơ này đặc biệt quan trọng trong JDK 21–23 khi chờ lâu bên trong monitor; JDK 24+ đã xử lý phần lớn vấn đề này qua JEP 491.
- **“Virtual thread loại bỏ nhu cầu đồng bộ, timeout và backpressure?”** Không. Các yêu cầu đó vẫn còn, đôi khi còn quan trọng hơn do số task đồng thời có thể tăng mạnh.

## Tài liệu chính thức để đào sâu

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444) — mục tiêu, API và mô hình chính thức của Java 21.
- [Oracle Java 21 — Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html) — lập lịch, pinning và hướng dẫn thực hành của bản 21.
- [JEP 491: Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491) — thay đổi về monitor và pinning từ JDK 24.
- [Oracle Java 26 — Virtual Threads](https://docs.oracle.com/en/java/javase/26/core/virtual-threads.html) — góc nhìn tài liệu JDK mới, cần phân biệt với hành vi của JDK 21.

**Ghi nhớ:** Platform thread là luồng JVM gắn chặt với OS thread; virtual thread là luồng Java nhẹ được JVM lên lịch lên carrier theo thời điểm. Chọn virtual threads khi có rất nhiều tác vụ tuần tự độc lập phải chờ I/O, và đo giới hạn thực của hệ thống thay vì suy đoán từ số lượng thread.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-003">Tạo luồng bằng Thread.ofVirtual() và startVirtualThread()</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo luồng bằng `Thread.ofVirtual()` và `Thread.startVirtualThread()` trong Java

## 1. Mục tiêu và điều kiện chạy

**Virtual thread (luồng ảo)** là một `java.lang.Thread` do Java runtime quản lý và lập lịch, thay vì gắn cố định với một luồng hệ điều hành trong suốt vòng đời. Virtual thread phù hợp cho lượng lớn công việc **chờ I/O**: truy vấn cơ sở dữ liệu, gọi HTTP, đọc/ghi socket, chờ phản hồi từ dịch vụ khác. Mục tiêu là giữ phong cách lập trình tuần tự dễ đọc mà vẫn hỗ trợ nhiều tác vụ đồng thời đang chờ.

Bài này tập trung vào hai cách tạo và khởi chạy virtual thread trong **Java thuần**:

- `Thread.startVirtualThread(Runnable task)`: tạo **và khởi chạy ngay** một virtual thread bằng lời gọi ngắn.
- `Thread.ofVirtual()`: lấy `Thread.Builder.OfVirtual` để cấu hình tên, trình xử lý lỗi, tạo thread chưa chạy, khởi chạy thread hoặc tạo `ThreadFactory`.

**Yêu cầu:** JDK **21 trở lên**; virtual threads trở thành tính năng chính thức từ Java 21 (JEP 444). Ví dụ bên dưới biên dịch với `javac` và chạy với `java` mà không cần `--enable-preview`. Trong JDK 19–20, virtual threads là tính năng preview nên điều kiện và API sử dụng khác.

Kiểm tra môi trường:

~~~bash
java -version
javac -version
~~~

Không nên đánh đồng virtual thread với việc làm CPU chạy nhanh hơn. Chúng giúp mở rộng **số tác vụ đồng thời đang đợi**, không làm tăng số lõi CPU.

## 2. Hai API cốt lõi

### 2.1. `Thread.startVirtualThread(Runnable)` — tạo và chạy ngay

Chữ ký API:

~~~java
public static Thread startVirtualThread(Runnable task)
~~~

- Nhận một `Runnable`, chạy nội dung `run()` trên **virtual thread mới**.
- Trả về đối tượng `Thread` **đã được start**; công việc có thể chạy trước hoặc sau thời điểm lời gọi trả về.
- Muốn đợi hoàn tất, gọi `join()`. Muốn yêu cầu dừng khi đang chờ hoặc đang xử lý, gọi `interrupt()` và thiết kế tác vụ biết hợp tác với interrupt.
- Không có tùy chọn đặt tên trực tiếp trong lời gọi này; khi cần cấu hình, dùng builder.

Ví dụ độc lập, lưu thành **`StartVirtualThreadDemo.java`**:

~~~java
public class StartVirtualThreadDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = Thread.startVirtualThread(() -> {
            System.out.println(
                    "Đang chạy trên virtual thread: "
                            + Thread.currentThread().isVirtual());
        });

        worker.join();
        System.out.println("Đã kết thúc: " + !worker.isAlive());
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 StartVirtualThreadDemo.java
java StartVirtualThreadDemo
~~~

Kết quả chính xác:

~~~text
Đang chạy trên virtual thread: true
Đã kết thúc: true
~~~

`Thread.currentThread()` trả về **virtual thread đang thực thi** mã, và `isVirtual()` cho biết loại thread. `join()` buộc luồng `main` chờ cho worker kết thúc, nên thứ tự hai dòng kết quả là xác định. Virtual thread mặc định là **daemon thread**, vì vậy với chương trình ngắn cần đợi hoặc quản lý vòng đời rõ ràng; không nên trông chờ JVM giữ chương trình sống chỉ vì một virtual thread vẫn đang chạy.

### 2.2. `Thread.ofVirtual()` — builder có thể cấu hình

`Thread.ofVirtual()` **không tự tạo hoặc start thread**. Nó trả về một builder:

~~~java
Thread.Builder.OfVirtual builder = Thread.ofVirtual();
~~~

Các thao tác quan trọng:

| Phương thức | Ý nghĩa |
| --- | --- |
| `builder.start(task)` | Tạo và start virtual thread, trả về `Thread` đã chạy |
| `builder.unstarted(task)` | Tạo virtual thread ở trạng thái `NEW`; cần gọi `thread.start()` |
| `builder.name("ten")` | Đặt tên xác định cho thread tạo từ builder |
| `builder.name("prefix-", 1)` | Đặt tiền tố và bộ đếm tên, ví dụ `prefix-1`, `prefix-2` |
| `builder.uncaughtExceptionHandler(handler)` | Xử lý ngoại lệ không bắt được của thread |
| `builder.inheritInheritableThreadLocals(false)` | Không kế thừa các giá trị `InheritableThreadLocal` từ thread tạo |
| `builder.factory()` | Tạo `ThreadFactory` có cấu hình tương ứng |

Ví dụ độc lập, lưu thành **`VirtualThreadBuilderDemo.java`**:

~~~java
public class VirtualThreadBuilderDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = Thread.ofVirtual()
                .name("vt-report")
                .unstarted(() -> System.out.println(
                        "Tên worker: " + Thread.currentThread().getName()));

        System.out.println("Trước start: " + worker.getState());
        worker.start();
        worker.join();
        System.out.println("Sau join: " + worker.getState());
    }
}
~~~

~~~bash
javac -encoding UTF-8 VirtualThreadBuilderDemo.java
java VirtualThreadBuilderDemo
~~~

Kết quả chính xác:

~~~text
Trước start: NEW
Tên worker: vt-report
Sau join: TERMINATED
~~~

Đối chiếu ba cách viết:

~~~java
Runnable job = () -> System.out.println("Hello");

// A: ngắn nhất, tạo và chạy ngay
Thread a = Thread.startVirtualThread(job);

// B: tạo và chạy ngay, có thể cấu hình thêm trên builder
Thread b = Thread.ofVirtual().name("vt-b").start(job);

// C: tạo trước, chủ động quyết định thời điểm start
Thread c = Thread.ofVirtual().name("vt-c").unstarted(job);
c.start();

a.join();
b.join();
c.join();
~~~

Đoạn trên là **mảnh mã đặt bên trong phương thức có khả năng ném `InterruptedException`**, không phải toàn bộ tệp Java. Ba dòng `Hello` có thể xuất hiện theo thứ tự bất kỳ; gọi `start()` **không** đồng nghĩa chạy xong công việc trước khi thực hiện lệnh tiếp theo.

**Quy tắc lifecycle:** một đối tượng `Thread` chỉ được `start()` **một lần**. Gọi `start()` lại (kể cả sau khi thread đã kết thúc) gây `IllegalThreadStateException`. Gọi `run()` trực tiếp không thay thế `start()`; với virtual thread, `Thread.run()` trực tiếp không thực thi tác vụ được gắn vào thread theo cách mong đợi.

## 3. Chọn cách nào cho từng tình huống?

| Nhu cầu | Lựa chọn |
| --- | --- |
| Chạy nhanh một `Runnable` rồi `join()` | `Thread.startVirtualThread(task)` |
| Đặt tên hoặc handler cho một worker | `Thread.ofVirtual().name(...).start(task)` |
| Tạo thread trước nhưng chủ động start sau | `Thread.ofVirtual().unstarted(task)` rồi `start()` |
| Tạo nhiều worker cùng quy ước tên | `Thread.ofVirtual().name(prefix, start).factory()` |
| Chạy rất nhiều tác vụ độc lập, cần `Future`/kết quả | `Executors.newVirtualThreadPerTaskExecutor()` |

`Thread.startVirtualThread(task)` tương đương về mặt tạo/start cơ bản với `Thread.ofVirtual().start(task)`. Sự khác biệt hữu ích là **builder có thể cấu hình** trước khi tạo thread. Không cần builder chỉ để dùng virtual threads.

Khi sử dụng `Thread.Builder.OfVirtual` để tạo nhiều thread, không nên chia sẻ một builder có thể thay đổi giữa các luồng một cách tùy tiện: builder **không được bảo đảm thread-safe**. Với nhiều tác vụ, `ThreadFactory` hoặc `ExecutorService` thường đơn giản hơn.

## 4. `ThreadFactory` và tạo nhiều virtual thread có tên

`factory()` trả về `java.util.concurrent.ThreadFactory`; gọi `newThread(task)` sẽ tạo một virtual thread **chưa start**. Đây là điểm khác với `builder.start(task)`.

Ví dụ độc lập, lưu thành **`VirtualThreadFactoryDemo.java`**:

~~~java
import java.util.concurrent.ThreadFactory;

public class VirtualThreadFactoryDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadFactory factory = Thread.ofVirtual()
                .name("vt-worker-", 1)
                .factory();

        Thread first = factory.newThread(() -> {});
        Thread second = factory.newThread(() -> {});

        System.out.println(first.getName());
        System.out.println(second.getName());
        System.out.println("Chưa chạy: " + (first.getState() == Thread.State.NEW));

        first.start();
        second.start();
        first.join();
        second.join();
    }
}
~~~

~~~bash
javac -encoding UTF-8 VirtualThreadFactoryDemo.java
java VirtualThreadFactoryDemo
~~~

Kết quả chính xác:

~~~text
vt-worker-1
vt-worker-2
Chưa chạy: true
~~~

`factory.newThread(...)` tạo thread theo cấu hình factory; chính người gọi chịu trách nhiệm `start()` nếu dùng factory trực tiếp. Một factory cũng có thể được truyền cho API nhận `ThreadFactory`, miễn là API đó phù hợp với mô hình tạo thread theo tác vụ.

## 5. Chạy nhiều tác vụ chờ I/O: mô hình một virtual thread cho mỗi tác vụ

Hai API ở đề bài thích hợp để nắm rõ cơ chế tạo thread. Khi chương trình cần trả về kết quả, thu gom lỗi và quản lý vòng đời của **nhiều tác vụ**, Java 21 còn cung cấp `Executors.newVirtualThreadPerTaskExecutor()`. Executor này tạo **một virtual thread mới cho mỗi tác vụ được submit**; không phải pool tái sử dụng virtual thread.

Ví dụ độc lập, lưu thành **`VirtualThreadTasksDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VirtualThreadTasksDemo {
    public static void main(String[] args) throws Exception {
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {

            List<Future<String>> futures = new ArrayList<>();

            for (int i = 1; i <= 3; i++) {
                int taskId = i; // mỗi lambda giữ một bản sao riêng
                futures.add(executor.submit(() -> {
                    Thread.sleep(100); // mô phỏng thao tác chờ I/O
                    return "Task " + taskId
                            + ": virtual=" + Thread.currentThread().isVirtual();
                }));
            }

            for (Future<String> future : futures) {
                System.out.println(future.get());
            }
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 VirtualThreadTasksDemo.java
java VirtualThreadTasksDemo
~~~

Kết quả chính xác:

~~~text
Task 1: virtual=true
Task 2: virtual=true
Task 3: virtual=true
~~~

Điểm cần hiểu:

1. Ba tác vụ có thể chạy **đồng thời**; `Thread.sleep(100)` là giả lập việc chờ, không chứng minh hiệu suất thực tế.
2. Vòng `get()` đọc `Future` theo **thứ tự submit**, vì vậy thứ tự in ra được xác định ngay cả khi thời điểm hoàn thành khác nhau. Nếu tác vụ ném lỗi, `Future.get()` báo `ExecutionException` với nguyên nhân gốc.
3. `try-with-resources` gọi `ExecutorService.close()`, đợi các tác vụ hoàn tất khi thoát scope; không bỏ quên vòng đời các worker.
4. Không nên biến mỗi virtual thread thành một worker tồn tại mãi để nhận nhiều nhiệm vụ. Mô hình được khuyến nghị là **tạo virtual thread mới cho mỗi tác vụ**, và chỉ giới hạn truy cập tài nguyên thực sự khan hiếm.

Ví dụ giới hạn truy cập dịch vụ chỉ cho phép hai yêu cầu đồng thời bằng `Semaphore` (mảnh mã bên trong lớp/phương thức thích hợp):

~~~java
import java.util.concurrent.Semaphore;

Semaphore permits = new Semaphore(2);

Runnable job = () -> {
    try {
        permits.acquire();
        try {
            // Thực hiện thao tác I/O giới hạn song song ở đây.
        } finally {
            permits.release();
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
};

Thread worker = Thread.startVirtualThread(job);
~~~

Semaphore là cách giới hạn số thao tác I/O đối với một tài nguyên; không cần lập một “virtual-thread pool” chỉ để khống chế concurrency. Ví dụ này có placeholder **diễn giải** trong comment thay vì một thao tác I/O thực và không có stdout cố định.

## 6. Cơ chế chạy và điểm khác với platform thread

Một `Thread` platform truyền thống thường ánh xạ với thread của hệ điều hành; số lượng lớn tạo ra chi phí stack và scheduling đáng kể. Với virtual threads, Java runtime lập lịch rất nhiều virtual thread lên một tập platform thread tương đối nhỏ gọi là **carrier threads**.

Khi một virtual thread đang thực thi, nó có thể được **mount** lên carrier. Khi gặp thao tác chờ được runtime hỗ trợ, ví dụ `Thread.sleep` và nhiều thao tác blocking I/O trong JDK, virtual thread có thể **unmount** để carrier chạy công việc khác. Khi sẵn sàng, virtual thread được lên lịch chạy tiếp, có thể trên carrier khác. Do đó **không** được dựa vào danh tính carrier hoặc giả định mỗi virtual thread có một OS thread riêng.

### Khác biệt về mục đích

| Khía cạnh | Platform thread | Virtual thread |
| --- | --- | --- |
| Tài nguyên tạo thread | Tương đối đắt | Rẻ hơn đáng kể cho nhiều tác vụ |
| Scheduling chủ yếu | Hệ điều hành | Java runtime lập lịch lên carrier |
| Nhiều tác vụ bị chặn I/O | Cần nhiều OS threads, dễ tốn tài nguyên | Thường mở rộng tốt hơn |
| Tác vụ CPU-bound kéo dài | Phù hợp nếu dùng số worker hợp lý | Không tự làm CPU tính toán nhanh hơn |
| API điều khiển | `start`, `join`, `interrupt`... | Dùng chính các API `Thread` quen thuộc |

**Lưu ý theo phiên bản về pinning:** trên JDK **21–23**, virtual thread có thể bị *pinned* vào carrier khi thực hiện thao tác chặn trong vùng `synchronized` hoặc khi chạy native/foreign code, làm giảm khả năng mở rộng. **JDK 24** triển khai JEP 491 để xử lý phần lớn trường hợp pinning liên quan đến `synchronized`. Các thao tác native/foreign và trường hợp chặn không hỗ trợ vẫn cần được đo đạc thực tế. Không nên áp dụng máy móc lời khuyên “thay hết `synchronized` bằng `ReentrantLock`” cho mọi phiên bản.

## 7. Vòng đời, lỗi và hủy công việc

### 7.1. `join()` và daemon

`start()` chỉ lên lịch công việc, không chờ kết quả. `join()` chờ thread được chỉ định kết thúc; phương thức có thể ném `InterruptedException` nếu **thread đang chờ** bị interrupt. Virtual thread là daemon thread: khi không còn non-daemon thread nào giữ JVM sống, virtual thread chưa xong có thể không kịp hoàn tất. Với công việc cần hoàn thành, dùng `join()`, `Future.get()` hoặc một cơ chế quản lý vòng đời tương đương.

Không lạm dụng `join()` **ngay sau từng lần start** trong một vòng lặp nếu muốn các tác vụ chạy đồng thời:

~~~java
// Tuần tự hóa công việc (không có lợi khi đang chờ I/O):
for (Runnable task : tasks) {
    Thread.startVirtualThread(task).join();
}

// Khởi chạy trước, sau đó chờ tất cả:
List<Thread> workers = new ArrayList<>();
for (Runnable task : tasks) {
    workers.add(Thread.startVirtualThread(task));
}
for (Thread worker : workers) {
    worker.join();
}
~~~

Đây là đoạn **mã minh họa**: đặt trong phương thức có `tasks` và import `java.util.List`, `java.util.ArrayList`, đồng thời xử lý `InterruptedException`. Trong hệ thống lớn, executor thường thích hợp hơn để quản lý nhiều tác vụ.

### 7.2. `interrupt()` là yêu cầu hủy hợp tác

`interrupt()` **không** cưỡng bức dừng bytecode đang chạy. Nhiều thao tác chờ như `Thread.sleep` sẽ ném `InterruptedException`, thường đồng thời xóa cờ interrupt; khi không thể xử lý trọn yêu cầu hủy, ta khôi phục cờ với `Thread.currentThread().interrupt()`.

Ví dụ độc lập, lưu thành **`VirtualThreadInterruptDemo.java`**:

~~~java
public class VirtualThreadInterruptDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = Thread.ofVirtual().name("vt-sleeper").start(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                System.out.println("Worker nhận yêu cầu interrupt");
                Thread.currentThread().interrupt();
            }
        });

        worker.interrupt();
        worker.join();
        System.out.println("Worker đã dừng");
    }
}
~~~

~~~bash
javac -encoding UTF-8 VirtualThreadInterruptDemo.java
java VirtualThreadInterruptDemo
~~~

Kết quả chính xác:

~~~text
Worker nhận yêu cầu interrupt
Worker đã dừng
~~~

Vì `interrupt()` được gửi gần như ngay sau `start()`, worker có thể chưa kịp gọi `sleep()`; ngay cả vậy, `sleep()` kiểm tra cờ interrupt và ném ngoại lệ. Không sử dụng `Thread.stop()` để dừng cưỡng bức.

### 7.3. Ngoại lệ trong `Runnable`

Một ngoại lệ không bắt được trong `Runnable` kết thúc virtual thread; `join()` **không tự ném lại** ngoại lệ đó cho caller. Có thể cấu hình `uncaughtExceptionHandler` khi dùng builder, hoặc dùng `Callable` và `Future` nếu caller cần nhận kết quả/lỗi.

Ví dụ độc lập, lưu thành **`VirtualThreadErrorDemo.java`**:

~~~java
public class VirtualThreadErrorDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = Thread.ofVirtual()
                .name("vt-validate")
                .uncaughtExceptionHandler((thread, error) ->
                        System.out.println(thread.getName()
                                + " báo lỗi: " + error.getMessage()))
                .start(() -> {
                    throw new IllegalStateException("Dữ liệu không hợp lệ");
                });

        worker.join();
        System.out.println("Đã join");
    }
}
~~~

~~~bash
javac -encoding UTF-8 VirtualThreadErrorDemo.java
java VirtualThreadErrorDemo
~~~

Kết quả chính xác:

~~~text
vt-validate báo lỗi: Dữ liệu không hợp lệ
Đã join
~~~

Handler chỉ được gọi khi có **uncaught exception**; nếu tác vụ tự `catch` ngoại lệ thì handler không chạy. Với executor, ngoại lệ trong tác vụ `submit` thường được ghi vào `Future` và đọc qua `get()`.

## 8. Những hiểu nhầm và lỗi phổ biến

1. **“`Thread.ofVirtual()` đã tạo thread.”** Chưa đúng: đó là builder. Gọi `start(task)` hoặc `unstarted(task)` mới tạo thread.
2. **“`unstarted(task)` tự chạy.”** Sai: thread đang ở `NEW`. Phải gọi `start()` đúng một lần.
3. **“`startVirtualThread(task)` chạy xong trước khi trả về.”** Sai: hàm tạo và start; worker chạy bất đồng bộ với caller.
4. **“Có thể `start()` một `Thread` nhiều lần.”** Sai: gọi lần hai ném `IllegalThreadStateException`.
5. **“Virtual thread làm vòng lặp CPU-bound nhanh hơn.”** Không mặc định; tài nguyên CPU không tăng. Đo throughput và latency của workload thực.
6. **“Virtual threads không chiếm bộ nhớ.”** Sai: stack và trạng thái vẫn tiêu thụ bộ nhớ; tạo quá nhiều tác vụ giữ `ThreadLocal` hoặc dữ liệu lớn sẽ gây áp lực heap/GC.
7. **“Virtual thread cần pool như OS thread.”** Thường không cần. Khởi tạo một thread cho một tác vụ, giới hạn số kết nối hoặc request tới tài nguyên bằng semaphore/connection pool khi cần.
8. **“Dùng `synchronized` luôn khiến virtual thread bị pinned.”** Phụ thuộc phiên bản JDK và vị trí blocking; JDK 24 cải thiện rõ rệt cho monitor, nhưng native/foreign blocking vẫn đáng xem xét.
9. **“`join()` chuyển ngoại lệ từ worker về main.”** Sai: `join()` chỉ đợi. Muốn thu kết quả/lỗi, dùng `Future` hoặc cơ chế truyền lỗi rõ ràng.
10. **“Mọi lời gọi blocking I/O đều tự động hiệu quả với virtual threads.”** Không bảo đảm với từng thư viện/native API; cần thử nghiệm, giám sát, áp dụng timeout, và kiểm tra giới hạn hệ thống.

## 9. Checklist khi áp dụng

- Dùng JDK 21+; chọn `Thread.startVirtualThread` cho tác vụ đơn giản hoặc `Thread.ofVirtual` khi cần tên, handler, tạo chưa chạy hay factory.
- Mỗi tác vụ có điểm kết thúc, timeout/hủy hợp tác và xử lý lỗi; xác định ai sẽ `join`/`close`/lấy `Future`.
- Dùng virtual threads cho workload phần lớn chờ I/O; giữ giới hạn hợp lý cho database connections, socket và các dịch vụ downstream.
- Khi đo hiệu năng, phân biệt throughput, latency, CPU, heap, số connection và các tình huống pinning. Không kết luận từ việc tạo nhiều thread hay `sleep` giả lập.
- Đặt tên worker có ý nghĩa và dùng công cụ quan sát (thread dump/JFR) khi cần xác định trạng thái chờ; nhớ rằng virtual thread mặc định có tên rỗng nếu không đặt.
- Dựa vào semantics của `Thread` (start một lần, interrupt hợp tác, join chờ), đồng thời lưu ý cơ chế carrier có thể khác theo phiên bản JDK.

### Tài liệu chính thức để tra cứu

- [Java SE 21 — `Thread`, `ofVirtual()` và `startVirtualThread()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html).
- [Java SE 21 — `Thread.Builder.OfVirtual`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.Builder.OfVirtual.html).
- [JEP 444 — Virtual Threads (Java 21)](https://openjdk.org/jeps/444).
- [JEP 491 — Synchronize Virtual Threads without Pinning (JDK 24)](https://openjdk.org/jeps/491).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-004">newVirtualThreadPerTaskExecutor()</a>

<details>
<summary>Xem chi tiết</summary>

# `Executors.newVirtualThreadPerTaskExecutor()` — một virtual thread cho mỗi tác vụ

`Executors.newVirtualThreadPerTaskExecutor()` là API từ **Java 21** để tạo `ExecutorService` chạy **mỗi tác vụ được gửi vào trên một virtual thread mới**. Đây là lựa chọn hữu ích cho chương trình Java có rất nhiều tác vụ độc lập phải chờ I/O như HTTP, socket hoặc cơ sở dữ liệu. Ta tiếp tục viết code blocking, dễ đọc, trong khi JVM có thể quản lý nhiều virtual thread mà không cần dành một OS thread riêng suốt thời gian chờ của mỗi task.

> **Phạm vi:** Java concurrency nói chung, không phụ thuộc Spring. Mã nguồn dùng Java 21+ và thư viện chuẩn JDK; không cần bật preview feature.

## 1. API thực sự làm gì?

Chữ ký:

~~~java
public static ExecutorService newVirtualThreadPerTaskExecutor()
~~~

Phương thức thuộc lớp `java.util.concurrent.Executors`. Khi gọi `executor.execute(runnable)` hoặc `executor.submit(callable)`, executor tạo và khởi chạy virtual thread riêng cho task đó. Không có hạn mức cố định về số virtual thread mà executor tạo. Một virtual thread hoàn tất task sẽ kết thúc, **không được đưa trở lại pool để tái sử dụng**.

Cần phân biệt:

- **Task:** công việc `Runnable` (không trả giá trị) hoặc `Callable<T>` (trả `T` và được phép ném checked exception).
- **Virtual thread:** một `Thread` nhẹ được JVM lập lịch; mỗi task có virtual thread của riêng mình.
- **Carrier thread:** platform/OS thread tạm chạy virtual thread. Khi virtual thread chờ I/O tương thích, JVM có thể tách nó khỏi carrier để carrier phục vụ tác vụ khác. Không phải thao tác blocking nào cũng giải phóng carrier.
- **ExecutorService:** API gửi task, nhận `Future`, yêu cầu hủy và đóng vòng đời executor; không đồng nghĩa với thread pool có số worker cố định.

~~~text
submit(A) ---> virtual thread A --\
submit(B) ---> virtual thread B ---> JVM scheduler ---> platform carrier threads
submit(C) ---> virtual thread C --/
                      |
             chờ I/O tương thích:
             virtual thread có thể unmount để giải phóng carrier
~~~

Thứ tự gửi task **không bảo đảm** thứ tự chạy hoặc hoàn thành. Mỗi virtual thread cũng **không chiếm một OS thread riêng** suốt đời. Đây là điểm khác biệt lớn so với mô hình mỗi yêu cầu mở một platform thread.

## 2. Ví dụ đầy đủ: ba task độc lập

Lưu đoạn sau vào file `VirtualThreadPerTaskDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VirtualThreadPerTaskDemo {
    public static void main(String[] args) throws Exception {
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {

            List<Future<String>> futures = new ArrayList<>();

            for (int id = 1; id <= 3; id++) {
                final int taskId = id;
                futures.add(executor.submit(() -> {
                    Thread.sleep(100); // Mô phỏng thời gian chờ I/O.
                    return "task " + taskId + ": virtual="
                            + Thread.currentThread().isVirtual();
                }));
            }

            // Tất cả task đã được gửi trước khi lấy kết quả.
            for (Future<String> future : futures) {
                System.out.println(future.get());
            }
        }
    }
}
~~~

Biên dịch và chạy bằng **JDK 21+**:

~~~bash
javac VirtualThreadPerTaskDemo.java
java VirtualThreadPerTaskDemo
~~~

Stdout:

~~~text
task 1: virtual=true
task 2: virtual=true
task 3: virtual=true
~~~

Vì sao kết quả này ổn định?

1. `submit(Callable<String>)` trả lại `Future<String>`. Mỗi lambda chạy trong virtual thread mới.
2. `Thread.currentThread().isVirtual()` được gọi **bên trong** lambda nên trả về `true`.
3. Ta submit cả ba task trước khi `get()`. Các task có thể thực thi đồng thời; vòng `for` phía dưới chỉ **in kết quả theo thứ tự future**. Thứ tự task chạy và kết thúc thực tế vẫn có thể khác.
4. `Thread.sleep(100)` mô phỏng chờ, không phải benchmark. Chương trình không có cam kết kết thúc sau chính xác 100 ms.
5. `try-with-resources` gọi `ExecutorService.close()` khi rời khối, bảo đảm chờ các task đã submit kết thúc.

**Bẫy:** viết `executor.submit(task).get()` ngay trong một vòng lặp có thể tuần tự hóa việc gửi task, vì caller đợi task trước hoàn thành mới gửi task kế tiếp. Hãy submit nhóm task trước rồi xử lý kết quả khi nghiệp vụ cho phép.

## 3. Các cách gửi task và nhận kết quả

| API | Nhận đầu vào | Kết quả cho caller | Xử lý lỗi |
|---|---|---|---|
| `execute(Runnable)` | `Runnable` không trả dữ liệu | `void` | Exception không được xử lý chạy theo cơ chế uncaught exception của thread |
| `submit(Runnable)` | `Runnable` | `Future<?>`; `get()` thành công trả `null` | `get()` ném `ExecutionException` |
| `submit(Callable<T>)` | `Callable<T>` | `Future<T>`, `get()` trả `T` | `get()` ném `ExecutionException` |
| `invokeAll(tasks)` | Tập `Callable` | Danh sách `Future` theo thứ tự đầu vào | Kiểm tra từng future |
| `invokeAny(tasks)` | Tập `Callable` | Một kết quả thành công | Có thể ném `ExecutionException` nếu không task nào thành công |

`Future.get()` chặn **thread gọi get** cho đến lúc task trả kết quả, bị hủy, lỗi hoặc thread chờ bị interrupt. Exception từ `Callable` được bọc trong `ExecutionException` và có thể lấy bằng `getCause()`. Bỏ hết future đồng nghĩa có thể bỏ sót lỗi task.

Snippet sau giả sử biến `future` là `Future<String>` đã được gửi:

~~~java
try {
    String result = future.get();
    System.out.println(result);
} catch (java.util.concurrent.ExecutionException e) {
    System.err.println("Task failed: " + e.getCause());
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // Caller quyết định dừng hoặc hủy công việc còn lại.
}
~~~

Việc chờ kết quả từng future theo thứ tự không có nghĩa các task đã chạy theo đúng thứ tự đó.

## 4. Vòng đời `ExecutorService` và `close()`

Quy trình phổ biến: tạo executor → gửi công việc → thu `Future` → đóng executor.

- **`close()`:** `ExecutorService` hỗ trợ `AutoCloseable` từ Java 19; `close()` bắt đầu shutdown có trật tự và **đợi toàn bộ task đã gửi kết thúc**. Nếu thread gọi `close()` bị interrupt trong lúc đợi, implementation mặc định cố ngắt các task đang chạy theo cơ chế `shutdownNow()`, tiếp tục đợi rồi phục hồi interrupt trước khi trả về.
- **`shutdown()`:** không chấp nhận task mới, cho phép task đã gửi tiếp tục; lời gọi không tự đợi hoàn tất.
- **`shutdownNow()`:** cố interrupt task đang chạy và trả danh sách task chưa chạy; **không bảo đảm** task dừng tức thì.
- **`awaitTermination(timeout, unit)`:** chờ kết thúc khi quản lý vòng đời thủ công.
- **`RejectedExecutionException`:** có thể xảy ra nếu tiếp tục gửi công việc sau shutdown.

Dùng `try (ExecutorService executor = ...)` cho nhóm tác vụ có vòng đời rõ ràng. Với executor sống lâu theo vòng đời của ứng dụng Java, phải xác định chủ sở hữu và thời điểm shutdown/awaitTermination. `close()` có thể đợi mãi nếu task không kết thúc; đóng executor **không tự đóng** socket, file handle hay connection bị task bỏ quên.

## 5. Timeout, cancellation và interrupt

Cần phân biệt **timeout của `Future.get`**, **timeout của I/O trong task** và **timeout tổng thể của nhóm công việc**. `Future.get(500, MILLISECONDS)` chỉ giới hạn thời gian caller chờ; task thường vẫn chạy sau khi `TimeoutException` xuất hiện. `cancel(true)` yêu cầu interrupt virtual thread chạy task nếu task đã bắt đầu, nhưng interrupt là cơ chế hợp tác, không phải cách kill thread cưỡng bức.

Snippet, giả sử `future` thuộc kiểu `Future<String>`:

~~~java
try {
    String value = future.get(
            500, java.util.concurrent.TimeUnit.MILLISECONDS);
    System.out.println(value);
} catch (java.util.concurrent.TimeoutException e) {
    future.cancel(true);
} catch (java.util.concurrent.ExecutionException e) {
    System.err.println("Cause: " + e.getCause());
} catch (InterruptedException e) {
    future.cancel(true);
    Thread.currentThread().interrupt();
}
~~~

Bên trong task, code hủy cần xử lý đúng:

~~~java
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return; // Ví dụ trong thân Runnable.
}
~~~

Đây là snippet minh họa trong một `Runnable`; nếu dùng `Callable<T>` thì phải trả đúng kiểu hoặc ném lại exception. Một số hoạt động I/O không lập tức dừng khi interrupt, vì vậy luôn thiết lập timeout ngay trên HTTP client, JDBC query, socket hoặc API tương ứng và đóng tài nguyên bằng `try-with-resources`/`finally`.

## 6. Khi nào virtual thread giúp hiệu năng?

**I/O-bound:** Nếu chương trình có hàng nghìn request độc lập chủ yếu chờ network hoặc DB, virtual thread giúp biểu diễn mỗi request bằng một thread mà không cần dành OS thread riêng cho suốt thời gian chờ. Ở thao tác blocking được JVM hỗ trợ, virtual thread có thể unmount khỏi carrier; carrier có thể chạy công việc khác. Lợi ích chính là **mức đồng thời của các task đang chờ**, không phải làm dịch vụ HTTP hay cơ sở dữ liệu phản hồi nhanh hơn.

**CPU-bound:** Với mã hóa, tính toán hay vòng lặp nặng, virtual thread không tạo thêm lõi CPU. Một executor có số worker hữu hạn, chẳng hạn `Executors.newFixedThreadPool(n)`, thường hợp hơn để kiểm soát parallelism. Cần đo throughput, độ trễ, CPU, memory và chi phí downstream trên workload thực tế.

| Tính chất | `newFixedThreadPool(n)` | `newVirtualThreadPerTaskExecutor()` |
|---|---|---|
| Thread cho task | Tối đa khoảng `n` platform workers cùng chạy | Mỗi task có virtual thread mới |
| Tái sử dụng thread | Có | Không |
| Khi gửi lượng task lớn | Task thừa thường chờ queue của pool | Không đặt giới hạn cố định số virtual thread |
| Phù hợp | Kiểm soát số worker, CPU-bound | Rất nhiều task blocking I/O độc lập |
| Giới hạn DB/downstream | Vẫn cần | **Vẫn cần** |

Không dùng virtual thread như pool kết nối, không giả định virtual thread nào cũng giải phóng carrier ở mọi thao tác blocking, và không kỳ vọng CPU-bound tự nhanh lên.

## 7. Giới hạn tài nguyên thật bằng `Semaphore`

`newVirtualThreadPerTaskExecutor()` không giới hạn số task: nếu 50.000 task cùng gọi một DB chỉ cho 20 kết nối, virtual thread không làm DB có thêm 49.980 kết nối. Giới hạn phần công việc dùng tài nguyên bên ngoài bằng cơ chế riêng như `Semaphore`.

Ví dụ đầy đủ giới hạn tối đa **hai task** cùng nằm trong phần mô phỏng I/O. Tạo file `BoundedVirtualTasks.java`:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

public class BoundedVirtualTasks {
    public static void main(String[] args) throws Exception {
        Semaphore permits = new Semaphore(2);

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {

            List<Future<String>> futures = new ArrayList<>();

            for (int id = 1; id <= 5; id++) {
                final int taskId = id;

                futures.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        Thread.sleep(100);
                        return "done-" + taskId;
                    } finally {
                        permits.release();
                    }
                }));
            }

            for (Future<String> future : futures) {
                System.out.println(future.get());
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac BoundedVirtualTasks.java
java BoundedVirtualTasks
~~~

Stdout:

~~~text
done-1
done-2
done-3
done-4
done-5
~~~

`Semaphore(2)` chỉ cho phép hai task giữ permit đồng thời. `acquire()` nằm **trước** `try/finally` để chỉ task lấy permit thành công mới được `release()`. Nếu `Thread.sleep()` bị interrupt, `finally` vẫn trả permit. Vòng in đọc future theo thứ tự submit nên stdout ổn định, dù thứ tự task thực chạy khác.

**Giới hạn của ví dụ:** semaphore trong lambda chỉ khống chế đoạn làm việc giữ permit. Một lượng virtual thread rất lớn vẫn có thể **tích tụ trong lúc chờ permit**, tiêu thụ bộ nhớ. Với nguồn task liên tục, thêm backpressure **trước khi submit**: bounded input queue, giới hạn số future đang chờ, chia batch hoặc điều phối producer-consumer. Đồng thời quản lý connection pool, rate limit, timeout và chính sách retry.

## 8. Shared state, `ThreadLocal` và pinning

Virtual thread vẫn là `Thread` và tuân thủ **Java Memory Model**. Nó không tự sửa race condition, deadlock, lỗi visibility hay việc cập nhật shared mutable state không an toàn. Dùng dữ liệu bất biến, `AtomicInteger`, lock hoặc concurrent collections khi phù hợp. `Future.get()` giúp chờ và nhận kết quả, không biến toàn bộ chương trình thành thread-safe.

`ThreadLocal` hoạt động với virtual thread, nhưng nếu mỗi virtual thread giữ cache hay object lớn, tạo rất nhiều thread có thể tiêu tốn bộ nhớ đáng kể. Không dựa vào giả định một task khác sẽ được chạy trên lại **cùng virtual thread** hay carrier thread. Thường nên truyền context qua tham số và giữ trạng thái theo tác vụ nhỏ gọn.

**Pinning thay đổi theo JDK:**

- Trên **JDK 21–23**, một virtual thread có thể bị *pinned* vào carrier khi thực hiện blocking trong `synchronized` hoặc trong một số native/foreign call. Nếu chặn lâu và thường xuyên, carrier bị giữ, giảm mức đồng thời. `synchronized` ngắn bảo vệ thao tác trong bộ nhớ thường không đáng lo.
- Với Java 21, Java Flight Recorder (JFR) có sự kiện `jdk.VirtualThreadPinned` giúp quan sát. Chỉ thay cấu trúc đồng bộ bao quanh I/O bằng `ReentrantLock` (kèm `try/finally`) khi xác định đúng điểm nghẽn; không thay mọi `synchronized` vô điều kiện.
- **JDK 24+** cải thiện trường hợp pinning liên quan monitor/`synchronized` nhờ JEP 491. Native/foreign call và một số thao tác chặn khác vẫn cần xem xét. Vì vậy phải nêu rõ phiên bản JDK khi đánh giá pinning.

Cuối cùng, kiểm tra thư viện I/O thực tế, driver JDBC, connection pool, socket, DNS, transaction, thời gian chờ của downstream và profiling. Số virtual thread lớn không chứng minh hệ thống tự mở rộng tốt nếu nút thắt nằm ở tài nguyên ngoài.

## 9. Những lỗi thường gặp

| Lỗi | Hậu quả | Cách khắc phục |
|---|---|---|
| Tưởng executor là pool giới hạn bằng CPU cores | Quá nhiều task đồng thời nhắm downstream | Giới hạn tài nguyên và backpressure |
| `submit().get()` ngay trong vòng lặp | Công việc có thể bị tuần tự hóa | Submit trước, thu future sau |
| Không đọc future khi task có thể thất bại | Bỏ sót ngoại lệ | Xem `ExecutionException.getCause()` |
| Nghĩ `get(timeout)` sẽ dừng task | Task vẫn chạy sau timeout | `cancel(true)` và timeout I/O |
| Nuốt `InterruptedException` | Hủy không hiệu quả | Dừng công việc hoặc truyền lại interrupt |
| Quên `close()/shutdown()` | Khó quản lý vòng đời, task tồn đọng | `try-with-resources` hoặc lifecycle rõ ràng |
| Không đồng bộ dữ liệu shared | Race condition | Immutable, atomic, locks, collections concurrent |
| Dùng virtual thread để tăng tốc CPU-bound | Không tăng năng lực CPU | Đo workload, dùng bounded pool khi hợp lý |
| Lưu dữ liệu lớn trong mỗi `ThreadLocal` | Tiêu thụ bộ nhớ | State nhỏ, không giữ cache per-thread lớn |
| Blocking dưới `synchronized` dài trên JDK 21 | Có thể pin carrier | Đo bằng JFR rồi tối ưu điểm cần thiết |

## 10. Câu hỏi tự kiểm tra và thực hành

**Tình huống 1:** Có 500 task, DB cho 20 kết nối. Executor có tự giới hạn số task vào DB ở mức 20 không? **Không.** Phải có giới hạn connection/permit/backpressure tương ứng.

**Tình huống 2:** Task 3 hoàn thành trước task 1. Vòng `for` đọc `Future.get()` theo danh sách 1, 2, 3 có in task 3 trước không? **Không.** Thứ tự in theo thứ tự đọc future; thứ tự thực thi không được bảo đảm.

**Tình huống 3:** `future.get(10, MILLISECONDS)` ném `TimeoutException`. Task đã dừng chưa? **Chưa chắc; thông thường vẫn chạy.** Timeout chỉ chấm dứt thời gian caller đợi. Hủy cần `cancel(true)` và cơ chế xử lý interrupt/timeout I/O.

**Bài tập:** Chỉnh `VirtualThreadPerTaskDemo` để task 2 ném `IllegalStateException`, thử bắt `ExecutionException` và in `getCause()`. Trong `BoundedVirtualTasks`, đổi `Semaphore(2)` sang `Semaphore(1)`, dùng `AtomicInteger` đo số task giữ permit lớn nhất. Giải thích vì sao kết quả thứ tự in vẫn giữ nguyên nhưng thời gian chạy thay đổi.

## 11. Những điểm cần nhớ

1. `newVirtualThreadPerTaskExecutor()` (Java 21+) trả về `ExecutorService` tạo **một virtual thread mới cho mỗi task**, không tái sử dụng virtual thread và không có trần đồng thời cố định.
2. Virtual thread có thể tạm nhường carrier khi chờ thao tác blocking tương thích. Lợi ích chủ yếu ở workload I/O-bound có nhiều tác vụ chờ.
3. `Future` cung cấp giá trị/lỗi và cancellation; `get(timeout)` chỉ giới hạn thời gian đợi của caller.
4. `close()` đợi task đã gửi kết thúc; mỗi task vẫn phải quản lý connection, stream và resource của mình.
5. Phải tự đặt giới hạn downstream, backpressure, timeout, cơ chế interrupt, thread safety và giám sát pinning theo phiên bản JDK.

### Tài liệu chính thức

- Java SE 21 `Executors` API: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html
- Java SE 21 `ExecutorService` API: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html
- Oracle Java 21 Virtual Threads Guide: https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html
- JEP 444 — Virtual Threads: https://openjdk.org/jeps/444


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-005">Thread-per-request cho I/O blocking</a>

<details>
<summary>Xem chi tiết</summary>

# Thread-per-request cho I/O blocking bằng Virtual Threads (Java 21+)

## 1. Bài toán và mục tiêu

**Thread-per-request** là mô hình xử lý trong đó mỗi yêu cầu độc lập được giao cho một luồng thực thi riêng. Luồng nhận yêu cầu, thực hiện các bước tuần tự (đọc đầu vào, gọi dịch vụ hoặc cơ sở dữ liệu, chờ I/O, tạo phản hồi) rồi kết thúc. Mô hình này dễ đọc, dễ debug vì mã xử lý giữ phong cách đồng bộ, tuyến tính.

Với **platform thread** truyền thống (luồng Java thường gắn với luồng hệ điều hành trong lúc chạy), việc giữ hàng nghìn luồng chỉ để chờ socket, file hoặc kết quả từ hệ thống bên ngoài tiêu tốn bộ nhớ và chi phí lập lịch. Một cách đối phó là giới hạn thread pool, nhưng khi mọi worker bị kẹt ở I/O, yêu cầu mới phải xếp hàng dù CPU còn rảnh.

Từ **Java 21**, virtual thread (JEP 444) là tính năng chính thức. Một virtual thread vẫn là đối tượng `Thread` và cung cấp cách lập trình đồng bộ quen thuộc, nhưng Java runtime có thể cho nó **tạm ngừng và nhường platform thread đang chạy hộ** khi gặp nhiều thao tác chờ được hỗ trợ. Nhờ vậy có thể dùng **một virtual thread cho mỗi request** với độ đồng thời rất lớn mà không cần giữ một OS thread cho mỗi request đang đợi.

Sau bài này, bạn cần:
- Mô tả được luồng xử lý một request và vì sao I/O blocking làm nghẽn platform-thread pool.
- Viết được mã Java 21+ xử lý mỗi kết nối/yêu cầu trên một virtual thread.
- Phân biệt **số request đồng thời**, **số platform thread**, **throughput** và **latency**.
- Áp dụng timeout, backpressure và giới hạn tài nguyên thực; nhận ra các tình huống pinning và lỗi triển khai thường gặp.

## 2. Mô hình hoạt động

### 2.1. Với platform thread

Ví dụ một worker xử lý request theo thứ tự:

1. Nhận yêu cầu.
2. Đọc dữ liệu từ socket (có thể đợi).
3. Gọi một hệ thống bên ngoài bằng API đồng bộ (có thể đợi).
4. Tính toán kết quả và ghi phản hồi.
5. Kết thúc yêu cầu.

Khi thao tác I/O chờ, platform thread cũng thường bị chặn. Nếu pool có 200 thread, 200 request đang đợi có thể chiếm toàn bộ pool, khiến request thứ 201 phải đứng trong hàng đợi. Tăng kích thước pool vô hạn không giải quyết bền vững vì mỗi OS thread có chi phí riêng.

### 2.2. Với virtual thread

Virtual thread được **mount** lên một platform thread gọi là **carrier** để thực thi. Khi gặp thao tác blocking mà JDK hỗ trợ, virtual thread có thể **unmount**, lưu trạng thái cần thiết và nhường carrier cho virtual thread khác. Khi I/O sẵn sàng, nó được lên lịch chạy tiếp, có thể trên carrier khác.

```text
Request A → Virtual thread A ──[đọc socket, chờ]──┐
                                                   │ unmount
                                                   ▼
                              Carrier thread → chạy request B/C/...
                                                   │
                          I/O của A hoàn tất ──────┘
                                                   ▼
                        Virtual thread A được mount lại
                                                   ▼
                                      Ghi phản hồi A
```

Đây vẫn là **blocking ở góc nhìn mã ứng dụng**: lệnh `readLine()` chỉ trả về khi có dữ liệu, EOF, lỗi hoặc timeout. Điểm thay đổi nằm ở cách JVM sử dụng carrier trong thời gian chờ. Không phải mọi thao tác native, thư viện cũ hay lời gọi blocking đều bảo đảm unmount.

### 2.3. Điều gì cải thiện, điều gì không?

| Đặc tính | Platform thread-per-request | Virtual thread-per-request |
|---|---|---|
| Đơn vị lập trình | Một request trên một luồng | Một request trên một virtual thread |
| Khi đợi I/O phù hợp | Thường giữ OS thread | Có thể nhường carrier |
| Số task cùng chờ khả thi | Bị ràng buộc mạnh bởi OS thread | Thường cao hơn nhiều nếu tài nguyên khác cho phép |
| Mã nghiệp vụ đồng bộ | Có | Có |
| Tăng tốc tác vụ CPU-bound | Không tự động | Không tự động |
| Kết nối, heap, downstream, quota | Vẫn là giới hạn | **Vẫn là giới hạn** |

Virtual threads chủ yếu giúp **khả năng phục vụ nhiều tác vụ I/O đang chờ (throughput/scalability)**. Chúng **không làm một lần truy vấn mạng vốn mất 300 ms tự biến thành 3 ms**, không tạo thêm CPU và cũng không loại bỏ giới hạn kết nối.

## 3. API Java 21+ để tạo một thread cho mỗi tác vụ

Cách phổ biến là dùng `Executors.newVirtualThreadPerTaskExecutor()`. Mỗi lần `submit` hoặc `execute` sẽ tạo một virtual thread mới để chạy task; executor này **không phải một pool tái sử dụng một số virtual thread cố định**.

Ví dụ đầu tiên dùng `Thread.sleep` mô phỏng thời gian chờ để thấy cấu trúc xử lý độc lập. Đây **không phải ví dụ network I/O thật**; phần 4 sẽ dùng socket thực.

**Tệp `VirtualIoBatch.java`:**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VirtualIoBatch {
    public static void main(String[] args) throws Exception {
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> results = new ArrayList<>();

            for (int id = 1; id <= 3; id++) {
                final int requestId = id;
                results.add(executor.submit(() -> {
                    Thread.sleep(100); // Mô phỏng thời gian chờ
                    return "request " + requestId
                            + ", virtual=" + Thread.currentThread().isVirtual();
                }));
            }

            // Lấy theo thứ tự submit để stdout xác định.
            for (Future<String> result : results) {
                System.out.println(result.get());
            }
        }
    }
}
```

Chạy với JDK 21 trở lên:

```bash
javac VirtualIoBatch.java
java VirtualIoBatch
```

Stdout:

```text
request 1, virtual=true
request 2, virtual=true
request 3, virtual=true
```

Ba tác vụ có thể chờ **đồng thời**, nhưng kết quả được in đúng thứ tự vì vòng lặp gọi `Future.get()` theo thứ tự đã lưu. `get()` chờ hoàn thành và có thể ném `ExecutionException` hoặc `InterruptedException` (ví dụ khai báo `throws Exception` chỉ nhằm giữ ví dụ ngắn). `try` với `ExecutorService` đóng executor và chờ các task đã gửi hoàn thành; điều này đòi hỏi task phải có khả năng kết thúc.

Có thể tạo trực tiếp bằng `Thread.ofVirtual().start(runnable)` hoặc tạo `ThreadFactory` bằng `Thread.ofVirtual().factory()`. Khi xử lý nhiều request độc lập và cần lấy kết quả, executor per-task thường dễ quản lý hơn.

## 4. Ví dụ thực tế: một virtual thread cho mỗi kết nối TCP

Ví dụ gồm một server và một client thuần Java, sử dụng socket blocking. Mỗi lần `accept()` trả về một kết nối, server gửi công việc xử lý kết nối đó cho một **virtual thread mới**. Đoạn `BufferedReader.readLine()` trong handler thực sự chờ dữ liệu mạng. Ở ví dụ này, **mỗi kết nối chỉ gửi một dòng và nhận một dòng**; coi một kết nối tương đương một request để minh họa mô hình.

### 4.1. Server

**Tệp `VirtualEchoServer.java`:**

```java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VirtualEchoServer {
    public static void main(String[] args) throws IOException {
        try (ServerSocket server = new ServerSocket()) {
            // Chỉ lắng nghe trên máy hiện tại.
            server.bind(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), 9090));

            try (ExecutorService requests =
                         Executors.newVirtualThreadPerTaskExecutor()) {
                System.out.println("Listening on loopback port 9090");

                while (true) {
                    Socket client = server.accept();
                    requests.execute(() -> handle(client));
                }
            }
        }
    }

    private static void handle(Socket client) {
        try (client) {
            client.setSoTimeout(5_000); // Giới hạn thời gian read bị chờ.
            BufferedReader input = new BufferedReader(
                    new InputStreamReader(
                            client.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter output = new BufferedWriter(
                    new OutputStreamWriter(
                            client.getOutputStream(), StandardCharsets.UTF_8));

            String line = input.readLine(); // Blocking socket I/O
            if (line != null) {
                output.write("echo: " + line);
                output.newLine();
                output.flush();
            }
        } catch (IOException exception) {
            System.err.println("Request failed: " + exception.getMessage());
        }
    }
}
```

Server giữ một platform thread (luồng `main`) để `accept()`, sau đó xử lý **từng kết nối trên virtual thread riêng**. Nó không giữ một platform thread chờ dữ liệu từ mỗi client. `setSoTimeout(5_000)` giới hạn thời gian một thao tác đọc socket chờ trước khi ném `SocketTimeoutException`; đây không phải tổng deadline cho toàn bộ request.

Vòng `while (true)` là vòng phục vụ liên tục, chủ động dừng ứng dụng bằng Ctrl+C. Đây là demo tối giản, chưa có shutdown mềm, xác thực, giới hạn số kết nối hoặc chính sách chống client gửi dữ liệu quá chậm.

### 4.2. Client

**Tệp `EchoClient.java`:**

```java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class EchoClient {
    public static void main(String[] args) throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(
                InetAddress.getLoopbackAddress(), 9090), 3_000);
        socket.setSoTimeout(5_000);

        try (socket) {
            BufferedReader input = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter output = new BufferedWriter(
                    new OutputStreamWriter(
                            socket.getOutputStream(), StandardCharsets.UTF_8));

            output.write("xin chao");
            output.newLine();
            output.flush();
            System.out.println(input.readLine());
        }
    }
}
```

Trong thư mục chứa hai tệp, biên dịch:

```bash
javac VirtualEchoServer.java EchoClient.java
```

Terminal 1:

```bash
java VirtualEchoServer
```

Stdout ban đầu của server:

```text
Listening on loopback port 9090
```

Terminal 2:

```bash
java EchoClient
```

Stdout của client:

```text
echo: xin chao
```

Mở nhiều client đồng thời: mỗi kết nối có một handler riêng, nên một client còn đang chờ gửi trọn dòng sẽ không bắt các handler khác chờ theo. Thứ tự hoàn thành giữa những client **không được bảo đảm**. Cổng 9090 phải rảnh, và server phải được chạy trước client.

### 4.3. Theo dõi một request qua các trạng thái

1. `ServerSocket.accept()` trả về `Socket` mới.
2. `requests.execute(...)` khởi tạo virtual thread cho handler.
3. Handler tạo reader/writer và gọi `readLine()`, có thể bị chờ vì chưa đủ dữ liệu.
4. Khi thao tác socket này có thể unmount, carrier sẵn sàng chạy virtual thread khác.
5. Khi dữ liệu đến, handler tiếp tục ngay sau `readLine()`, tạo phản hồi và `flush()`.
6. `try (client)` đóng socket kể cả khi có lỗi I/O; virtual thread kết thúc.

Mô hình giữ mã handler tuyến tính như xử lý một request thông thường, nhưng tăng số request đang chờ I/O có thể phục vụ trên cùng một số carrier.

## 5. Khi áp dụng vào code ứng dụng

Một handler đồng bộ có thể được hình dung như sau (đây là **pseudocode**, không phải chương trình Java độc lập):

```java
Response handle(Request request) {
    Input input = validate(request);       // CPU, ngắn
    Data data = blockingFetch(input);      // I/O waiting
    Result result = transform(data);       // CPU
    blockingWrite(result);                 // I/O waiting
    return Response.ok(result);
}
```

Khi mỗi request có virtual thread, không cần tách thủ công mọi điểm chờ thành callback, nhưng vẫn phải tuân thủ hợp đồng của từng API, kiểm tra timeout và xử lý lỗi. `blockingFetch`, `blockingWrite` ở đây là các hàm minh họa chưa định nghĩa; ứng dụng phải cung cấp implementation thực tế.

Một quyết định triển khai quan trọng: **đơn vị concurrency nên phản ánh một tác vụ nghiệp vụ** (một request, một job độc lập) chứ không phải tạo virtual thread tùy tiện cho từng phép toán nhỏ. Nếu một request cần gọi song song hai nguồn dữ liệu độc lập, có thể dùng hai task/future rồi ghép kết quả; phải tính đến hủy công việc còn lại nếu một nguồn lỗi hoặc timeout.

## 6. Giới hạn tài nguyên: nhiều virtual thread không đồng nghĩa vô hạn request

`newVirtualThreadPerTaskExecutor()` không giới hạn lượng task nộp vào. Virtual thread rẻ hơn platform thread, nhưng **request vẫn giữ object, buffer, socket, bộ nhớ và có thể chiếm kết nối downstream**. Một server nhận thêm 100.000 kết nối đến cơ sở dữ liệu chỉ vì có 100.000 virtual thread có thể làm hệ thống phía sau sụp đổ.

Dùng **backpressure/admission control** trước khi bắt đầu công việc đắt đỏ hoặc truy cập tài nguyên hữu hạn:

- **Semaphore** giới hạn số tác vụ cùng dùng một tài nguyên; permit được trả trong `finally`.
- **Connection pool** điều tiết số kết nối thực tới downstream (giới hạn phải dựa vào khả năng phục vụ, không theo số virtual thread).
- **Timeout/deadline** cho kết nối, đọc, gọi API và toàn bộ nghiệp vụ; trả lỗi hoặc từ chối sớm khi quá tải.
- **Bounded queue / rejection** ở ranh giới nhận request khi cần bảo vệ hệ thống; tránh xếp hàng vô hạn khiến latency tăng và tốn heap.

Ví dụ sau là **đoạn mã đặt trong một handler**, giới hạn tối đa 20 lần đồng thời vào tài nguyên `slowDependency`, giả sử `Semaphore permits` được chia sẻ và `slowDependency.call()` là lời gọi I/O có timeout:

```java
if (!permits.tryAcquire()) {
    throw new IllegalStateException("Dependency is busy");
}
try {
    return slowDependency.call();
} finally {
    permits.release();
}
```

Không đặt `new Semaphore(20)` bên trong **mỗi** handler; làm vậy mỗi request lại có bộ permit riêng và giới hạn không có tác dụng toàn cục. Với API thật, hãy thiết kế mức chờ permit, xử lý interrupt, mã lỗi quá tải và cleanup tài nguyên phù hợp.

**Hai giới hạn khác nhau:** virtual thread cho phép nhiều request đồng thời *đang đợi*, còn semaphore/pool xác định có bao nhiêu request được *sử dụng tài nguyên hạn chế*. Không cần biến virtual threads thành một pool kích thước cố định để bảo vệ downstream.

## 7. Pinning, blocking và khác biệt giữa phiên bản JDK

Một virtual thread chỉ nhường được carrier nếu runtime có thể unmount tại điểm chờ đó. **Pinning** là lúc virtual thread không thể nhả carrier trong một số đoạn chờ, khiến số carrier khả dụng giảm.

- Trên **JDK 21–23**, blocking lâu và lặp lại trong `synchronized` có thể pin carrier. Tránh giữ monitor khi gọi I/O dài; nếu cần khóa quanh hoạt động đó, đánh giá dùng `ReentrantLock` hoặc thu hẹp critical section.
- Từ **JDK 24**, JEP 491 thay đổi cơ chế monitor để virtual threads có thể đồng bộ hóa mà không bị pin bởi `synchronized` theo kiểu cũ. Đừng áp dụng nguyên xi lời cảnh báo của JDK 21 cho mọi JDK mới.
- Blocking trong một số **native/foreign-function** vẫn có thể pin carrier; hành vi tùy phiên bản và thư viện. Việc có virtual thread không biến mọi thư viện blocking thành nonblocking ở cấp runtime.
- `Thread.sleep` là ví dụ chờ được runtime virtual-thread hỗ trợ; với các thư viện I/O cụ thể, hãy đo thay vì mặc định chúng luôn nhường carrier.

Trên JDK 21, có thể kiểm tra `jdk.VirtualThreadPinned` qua JDK Flight Recorder (JFR) khi chẩn đoán pinning. Có thể dùng tùy chọn `-Djdk.tracePinnedThreads=full` để in stack trace trong JDK 21; các công cụ/tùy chọn và ý nghĩa phải đối chiếu phiên bản JDK đang chạy.

Quan trọng: pinning **không mặc nhiên gây sai kết quả**. Nó chủ yếu ảnh hưởng đến khả năng mở rộng khi xuất hiện nhiều lần và mỗi lần kéo dài đáng kể.

## 8. Timeout, interruption và lỗi

- **Blocking không có timeout:** có thể để một virtual thread, socket và các object liên quan tồn tại vô hạn; quá nhiều tác vụ treo vẫn gây cạn heap hoặc tài nguyên ngoài JVM.
- **`Future.cancel(true)`:** yêu cầu interrupt task; không bảo đảm mọi thư viện hoặc I/O sẽ hủy đúng lúc. Thiết kế kết thúc tác vụ và đóng tài nguyên rõ ràng.
- **`InterruptedException`:** nếu bắt để dọn dẹp mà không ném lại, thường cần `Thread.currentThread().interrupt()` để giữ tín hiệu; đừng âm thầm nuốt interrupt.
- **Task lỗi:** khi dùng `submit`, ngoại lệ của task xuất hiện qua `Future.get()` dưới `ExecutionException`. Khi dùng `execute`, xử lý và log ngoại lệ tại ranh giới handler.
- **`ExecutorService.close()`:** chờ các task hoàn thành; nếu task bị kẹt dài, shutdown cũng có thể bị kẹt. Tạo cơ chế deadline/đóng socket/hủy task cho server production.
- **ThreadLocal:** virtual threads vẫn hỗ trợ `ThreadLocal` nhưng đừng dùng nó như cache dung lượng lớn cho mỗi thread; số virtual thread lớn sẽ nhân chi phí heap. Dữ liệu request nên có vòng đời rõ ràng.

Với TCP demo, `setSoTimeout` giới hạn từng thao tác đọc, **không** tự giới hạn toàn bộ vòng đời kết nối hoặc thời gian ghi. Hệ thống thực cần kiểm soát cả kích thước bản tin, tổng deadline, idle time, memory buffer và số kết nối.

## 9. Anti-pattern thường gặp

| Sai lầm | Vì sao gây vấn đề | Hướng xử lý |
|---|---|---|
| Pool cố định 50 virtual thread vì sợ tạo nhiều thread | Dùng giới hạn thread để che giới hạn tài nguyên khác; request lại xếp hàng | Một virtual thread mỗi request, giới hạn downstream bằng pool/permit |
| Tăng virtual thread để tăng tốc CPU-heavy workload | CPU cores vẫn hữu hạn | Đo CPU, tối ưu thuật toán hoặc giới hạn CPU jobs phù hợp |
| Bỏ mọi timeout vì virtual thread rẻ | Tác vụ vẫn giữ bộ nhớ/socket/kết nối | Timeout, deadline và cleanup |
| Mở hàng nghìn kết nối tới tài nguyên chỉ chịu nổi 100 | Quá tải downstream dù JVM còn tài nguyên | Admission control, bounded pool và backpressure |
| Cho rằng mọi lệnh blocking đều nhả carrier | Pinning/native blocking tùy JDK và thư viện | Kiểm tra runtime bằng profiling/JFR và load test |
| Dùng `ThreadLocal` để cache object lớn mỗi request | Không có thread reuse như pool; tốn heap | Truyền context rõ ràng, thu gọn dữ liệu theo request |
| Không thu thập `Future` hoặc bỏ qua lỗi task | Lỗi bị mất/khó phát hiện | Chờ, tổng hợp kết quả và ghi lỗi có context |

## 10. Đo hiệu quả đúng cách

Chạy load test với **cùng nghiệp vụ và cùng giới hạn tài nguyên** khi so sánh platform-thread pool với virtual-thread-per-task. Tăng dần request đồng thời và đo:

1. **Throughput**: request/giây hoàn thành thực sự.
2. **Latency p50/p95/p99**: gồm thời gian xếp hàng, chờ I/O và xử lý.
3. **CPU, heap/GC, số kết nối, file descriptors** và mức sử dụng của downstream.
4. **Timeout, lỗi, rejected requests** và số task đang chờ; quan sát pinning nếu dùng JDK liên quan.

Ví dụ, một request tốn khoảng 10 ms CPU và 300 ms đợi I/O có thể hưởng lợi rõ khi tăng concurrency. Nếu mỗi request chỉ làm 300 ms CPU thì virtual threads không làm CPU nhanh lên. Mức cải thiện phải được **đo trong điều kiện thực**, không suy từ tỷ lệ 300/10 thành cam kết hiệu năng.

## 11. Checklist áp dụng

- [ ] Đã xác định request/job độc lập và các điểm I/O blocking.
- [ ] Chạy JDK 21+ và chọn `newVirtualThreadPerTaskExecutor()` hoặc `Thread.ofVirtual()` đúng nhu cầu.
- [ ] Mỗi tác vụ có vòng đời, đầu ra và xử lý lỗi xác định.
- [ ] Có timeout/deadline và giải phóng socket, stream, connection trong mọi đường đi.
- [ ] Có giới hạn tài nguyên hữu hạn (downstream, database, connection pool, bộ nhớ).
- [ ] Đã xem pinning theo **đúng phiên bản JDK**, không suy diễn từ tài liệu phiên bản khác.
- [ ] Đã thử tải và kiểm tra throughput, latency, tài nguyên, thất bại khi quá tải.

## 12. Tự kiểm tra

1. Nếu 2.000 request cùng đợi I/O trong 500 ms, vì sao platform thread pool 200 worker có thể sinh hàng đợi dù CPU chưa bận?
2. Khi handler gọi `readLine()` trên một virtual thread, **mã nguồn** có còn blocking không? Carrier có luôn bị giữ không?
3. Vì sao không cần tạo pool 100 virtual threads để giới hạn 100 kết nối tới database? Cơ chế nào thể hiện giới hạn đúng?
4. Hai biến thể của một nghiệp vụ: chờ API 300 ms và tính toán 300 ms. Biến thể nào dễ có lợi hơn khi chuyển sang virtual thread, và vì sao?
5. Vì sao cảnh báo pinning của `synchronized` trong JDK 21 cần được đánh giá lại khi ứng dụng nâng lên JDK 24+?

**Đáp án định hướng:** (1) worker chờ I/O vẫn chiếm slot; (2) handler vẫn viết blocking, carrier có thể được nhả nếu điểm chờ được hỗ trợ và không pin; (3) dùng semaphore/connection pool cho tài nguyên có giới hạn; (4) workload nhiều thời gian chờ I/O; (5) JEP 491 thay đổi hành vi monitor.

## Tài liệu gốc để tra cứu

- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444) (tính năng chính thức ở Java 21).
- [Oracle Java 21 — Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html) (mô hình, scheduling, ví dụ client/server, pinning theo JDK 21).
- [Java 21 API — Executors](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html) (`newVirtualThreadPerTaskExecutor()`).
- [JEP 491 — Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491) (thay đổi từ JDK 24).

> **Ghi nhớ:** Virtual threads cho phép viết code **đồng bộ, rõ ràng** và phục vụ nhiều request **đang chờ I/O** với chi phí thread thấp hơn. Chúng không thay thế timeout, giới hạn tài nguyên, backpressure và kiểm thử tải.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-006">Carrier threads và scheduling của JVM</a>

<details>
<summary>Xem chi tiết</summary>

# Carrier threads và scheduling của JVM trong Virtual Threads

## 1. Bức tranh tổng thể: ai lập lịch cho ai?

Từ Java 21, **virtual thread** là một loại `java.lang.Thread` chính thức. Nó giúp ứng dụng tạo nhiều luồng tác vụ đồng thời, đặc biệt khi phần lớn thời gian là chờ I/O, mà không cần một luồng hệ điều hành (OS thread) riêng cho mỗi tác vụ. Virtual thread vẫn thực thi mã Java trên CPU thông qua **platform thread**; JVM không tạo ra CPU hay thực thi mã ở “ngoài” OS thread.

Ba vai trò cần phân biệt:

| Thành phần | Vai trò | Điểm cần nhớ |
| --- | --- | --- |
| Virtual thread | Đơn vị thực thi logic mà mã ứng dụng tạo, thường một tác vụ trên một thread | Có danh tính, stack, `ThreadLocal` và trạng thái riêng; không sở hữu OS thread suốt vòng đời |
| Carrier thread | Platform thread đang **chở** một virtual thread trong lúc mã của virtual thread chạy | Một carrier có thể lần lượt chạy nhiều virtual threads; cùng một virtual thread có thể chạy trên các carrier khác nhau |
| JVM scheduler và OS scheduler | JVM chọn virtual thread để **mount** lên carrier; OS chọn lúc platform/carrier thread được CPU chạy | Hai tầng lập lịch khác nhau, không có ánh xạ cố định một-một |

Sơ đồ khái niệm (các mũi tên diễn tả những thời điểm khác nhau, không phải tất cả virtual threads chạy đồng thời):

```text
Ứng dụng:     VT-A   VT-B   VT-C   VT-D   VT-E ... VT-N
                \      |      /       \      /
                 JVM virtual-thread scheduler
                          |
             +------------+------------+
             |                         |
          Carrier-1                 Carrier-2   ... (platform threads)
             |                         |
             +----- OS scheduler ------+
                          |
                       CPU cores
```

**Mount** là lúc JVM gắn ngữ cảnh thực thi của virtual thread vào một carrier để nó chạy. **Unmount** là lúc JVM tách ngữ cảnh ấy, thường khi virtual thread chờ một thao tác có hỗ trợ cơ chế đình chỉ. Carrier vừa được giải phóng có thể chạy virtual thread khác. Đó là cách **M virtual threads được ghép lên N platform threads** (M:N scheduling).

Lập lịch virtual threads là chi tiết triển khai của JDK. Những mô tả về `ForkJoinPool`, tham số cấu hình, sự kiện quan sát trong bài này áp dụng cho triển khai OpenJDK/HotSpot hiện đại; không nên coi chúng là cam kết cố định của mọi JVM.

## 2. Một vòng đời mount → park → unmount → remount

Ví dụ một virtual thread đọc dữ liệu qua một API blocking I/O được JDK hỗ trợ:

1. Ứng dụng gọi `Thread.start()` hoặc gửi tác vụ vào `Executors.newVirtualThreadPerTaskExecutor()`. Virtual thread được đưa vào hàng công việc sẵn sàng chạy.
2. JVM scheduler chọn một carrier khả dụng và mount virtual thread. Mã chạy bình thường; trong mã ứng dụng, `Thread.currentThread()` vẫn trả về **virtual thread**, không phải carrier.
3. Tác vụ gọi thao tác chờ, chẳng hạn đọc socket khi chưa có dữ liệu. JDK có thể **park/unmount** virtual thread, lưu trạng thái tiếp tục của stack và nhường carrier. Không phải mọi lời gọi “blocking” đều chắc chắn unmount; phụ thuộc loại thao tác và phiên bản JDK.
4. Khi I/O sẵn sàng, hết thời gian chờ hoặc có tín hiệu đánh thức phù hợp, JDK làm virtual thread **runnable** trở lại và đưa nó về scheduler.
5. Scheduler remount nó lên **một carrier khả dụng bất kỳ**. Mã tiếp tục sau điểm chờ, rồi hoàn thành như một thread bình thường.

```text
VT mới -> runnable -> mounted/running -> chờ I/O
                       ^                    |
                       |                    v
                       +--- runnable <- parked/unmounted
                            (được đánh thức)
```

Một virtual thread có thể park và resume nhiều lần; carrier lúc resume **không bắt buộc là carrier cũ**. Phải phân biệt “được đánh thức và chờ vào hàng runnable” với “ngay lập tức được CPU thực thi”: còn cần scheduler JVM chọn carrier và OS cho carrier chạy.

Điều này không làm thay đổi ngữ nghĩa `synchronized`, `volatile`, `Lock`, `interrupt` hay quy tắc happens-before của Java. Virtual threads đổi cách **sử dụng tài nguyên chờ**, không tự tạo thêm bảo đảm thứ tự thực thi.

## 3. Scheduler mặc định hoạt động như thế nào?

Trong JDK OpenJDK/HotSpot, scheduler mặc định của virtual threads dùng một `ForkJoinPool` theo mô hình **work-stealing** và thiên về tác vụ bất đồng bộ (FIFO). Đây là pool dành cho việc chạy các *continuation* của virtual threads và **khác** với `ForkJoinPool.commonPool()` dùng bởi nhiều tác vụ khác, chẳng hạn một số `CompletableFuture` hoặc parallel streams.

- **Target parallelism**: số carrier platform threads mà scheduler hướng tới để chạy virtual threads. Mặc định gắn với số processor khả dụng do JVM nhìn thấy; đó là **mức song song mục tiêu**, không phải số virtual threads được phép tạo.
- **Runnable queue**: virtual threads đã sẵn sàng nhưng chưa được mount sẽ chờ scheduler. Work-stealing giúp các carrier san sẻ công việc; không hứa hẹn FIFO tuyệt đối trên toàn bộ hệ thống.
- **Compensation**: khi một số hoạt động blocking làm carrier không khả dụng, triển khai có thể tăng số platform threads trong scheduler trong một số tình huống, chịu giới hạn cấu hình và tài nguyên. Cơ chế này không chữa mọi trường hợp pinning hoặc blocking native.
- **OS scheduling**: ngay cả khi có nhiều carrier, số mã chạy CPU đồng thời vẫn phụ thuộc CPU thực tế, mức tải và scheduler của hệ điều hành.

Một vòng lặp CPU rất dài không tự nhiên được virtual-thread scheduler chia đều thành những “time slice” công bằng cho các virtual threads khác. Virtual threads không phù hợp làm phương tiện tăng tốc tính toán CPU-bound; OS preempt carrier không đồng nghĩa JVM preempt tác vụ virtual thread đang giữ carrier.

**Ví dụ tư duy:** 10.000 tác vụ đang chờ phản hồi socket có thể tương ứng với 10.000 virtual threads nhưng chỉ cần một số nhỏ carrier *đang thực thi* mã ở mỗi thời điểm. Ngược lại, 10.000 tác vụ cùng tính toán liên tục vẫn tranh chấp số lõi CPU hữu hạn. Không có phép tính “10.000 virtual threads = 10.000 tác vụ chạy trên CPU đồng thời”.

## 4. Cấu hình scheduler: hiểu đúng trước khi chỉnh

Các thuộc tính sau là **system properties của triển khai JDK**, truyền lúc JVM khởi động:

| Thuộc tính | Ý nghĩa | Không có nghĩa là |
| --- | --- | --- |
| `jdk.virtualThreadScheduler.parallelism` | Target parallelism của scheduler; mặc định thường bằng số processor JVM nhận thấy | Giới hạn số virtual threads hay số yêu cầu nghiệp vụ |
| `jdk.virtualThreadScheduler.maxPoolSize` | Giới hạn số platform threads scheduler có thể sử dụng trong các tình huống cần bù trừ; JDK 21/25 mặc định 256 | Giá trị nên tăng tới số tác vụ; cách chữa code bị block lâu |

Ví dụ chạy chương trình minh họa với scheduler chỉ nhắm tới hai carrier:

```bash
javac CarrierSchedulingDemo.java
java -Djdk.virtualThreadScheduler.parallelism=2 CarrierSchedulingDemo
```

Nên bắt đầu từ mặc định, đo các chỉ số hàng đợi/running/pinning và throughput trước khi điều chỉnh. Cố tăng parallelism trên workload CPU-bound có thể làm tăng context switching. Hạ quá thấp có thể khiến tác vụ runnable phải xếp hàng lâu nếu carrier bị giữ bởi mã chạy dài. Giới hạn kết nối mạng, database, RAM, file descriptor hay tốc độ dịch vụ bên ngoài vẫn cần quản lý **riêng**; scheduler không thay thế giới hạn tài nguyên nghiệp vụ.

**Không nhầm** `Executors.newVirtualThreadPerTaskExecutor()` với một pool chứa sẵn N virtual threads: executor này tạo **một virtual thread mới cho mỗi tác vụ**, còn carrier pool nằm ở tầng lập lịch của JDK. Tránh dùng fixed thread pool để “pool virtual threads”. Để giới hạn số truy vấn đồng thời tới một tài nguyên, dùng cơ chế chuyên dụng như `Semaphore`/pool kết nối.

## 5. Thí nghiệm Java 21+: nhiều virtual threads chờ, ít carrier vẫn tiến triển

Tệp **CarrierSchedulingDemo.java** sau tạo 100 virtual threads, cho từng thread dừng ở một `CountDownLatch`, rồi main chỉ mở chốt sau khi cả 100 đã tới điểm chờ. Trong thực thi chuẩn trên JDK 21+, việc chờ latch ở virtual thread không cần chiếm vĩnh viễn một carrier.

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CarrierSchedulingDemo {
    public static void main(String[] args) throws InterruptedException {
        final int tasks = 100;
        CountDownLatch allWaiting = new CountDownLatch(tasks);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger virtualCount = new AtomicInteger();

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < tasks; i++) {
                executor.submit(() -> {
                    if (Thread.currentThread().isVirtual()) {
                        virtualCount.incrementAndGet();
                    }
                    allWaiting.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            allWaiting.await();
            System.out.println("Da den diem cho: "
                    + (tasks - allWaiting.getCount()));
            release.countDown();
        } // close() đợi các tác vụ đã gửi kết thúc

        System.out.println("Virtual threads: " + virtualCount.get());
    }
}
```

Biên dịch và chạy:

```bash
javac CarrierSchedulingDemo.java
java -Djdk.virtualThreadScheduler.parallelism=2 CarrierSchedulingDemo
```

**Stdout**:

```text
Da den diem cho: 100
Virtual threads: 100
```

Giải thích:

- Mỗi task chạy trong một virtual thread riêng; `Thread.currentThread().isVirtual()` kiểm tra đúng loại thread. Việc tăng biến đếm xảy ra **trước** `countDown()`, nên khi main chờ xong thì số đếm đã đủ 100.
- Mọi task tới `release.await()` và tạm ngừng. Main vẫn có thể tiếp tục, rồi `countDown()` đánh thức các task đang chờ.
- Với `parallelism=2`, chương trình vẫn cho 100 virtual threads đạt tới điểm chờ. Điều này **không đo trực tiếp** số carrier thực tế và không chứng minh mọi thư viện blocking I/O đều unmount; nó minh họa cách virtual threads chia sẻ năng lực chạy.
- `CountDownLatch`, `AtomicInteger` và cơ chế chờ bảo đảm stdout ổn định; thứ tự cụ thể giữa 100 task không được giả định. Trong mã thực tế cần xử lý hoặc báo cáo exception từ `Future` khi muốn phát hiện lỗi tác vụ; `submit` có thể giữ exception trong `Future`.

Bạn có thể đổi `tasks` thành 1.000 nếu môi trường đủ tài nguyên để quan sát tác vụ chờ, nhưng tránh suy diễn benchmark từ một lần chạy. Để so sánh có ý nghĩa, đo CPU, heap, throughput, độ trễ và hàng đợi trên workload gần thực tế.

## 6. Pinning: khi carrier không được giải phóng

**Pinning** xảy ra khi virtual thread đang ở điểm chờ nhưng JVM không thể unmount nó, khiến carrier OS thread bị giữ/block cùng. Tác vụ thường vẫn đúng về mặt logic; rủi ro chính là **giảm khả năng mở rộng** khi nhiều carrier bị giữ lâu.

Cần phân biệt phiên bản:

| Phiên bản | Điều cần biết |
| --- | --- |
| **JDK 21–23** | Blocking bên trong `synchronized` method/block có thể pin carrier. Nếu việc chờ dài và lặp thường xuyên, đây là vấn đề throughput đáng kiểm tra; JDK 21 hỗ trợ `-Djdk.tracePinnedThreads=short` hoặc `full` để khảo sát |
| **JDK 24+** | **JEP 491** cho phép virtual threads unmount khi chờ trong hoặc chờ lấy Java monitor (`synchronized`), nên không còn quy tắc chung “`synchronized` luôn gây pinning” |
| **Các JDK hiện đại** | Những điểm phụ thuộc native/foreign-function frames và một số đoạn mã/hoạt động đặc biệt vẫn có thể pin; cần quan sát trên chính bản JDK và nền tảng đang chạy |

Không nên “đổi toàn bộ `synchronized` sang `ReentrantLock`” theo thói quen. Với JDK 21–23, cân nhắc thay đúng **đoạn blocking dài và thường xuyên** nếu pinning thực sự gây nghẽn. Với JDK 24+, đo lại vì cách xử lý monitor đã thay đổi. Ngoài pinning, **CPU-bound code dài** hay **native blocking** cũng có thể chiếm carrier và gây thiếu tiến triển, dù nguyên nhân không giống nhau.

Đặc biệt, mã có khóa bao trùm I/O sẽ còn có **tranh chấp khóa logic** ngay cả khi không pin. “Không pin carrier” không đồng nghĩa nhiều task có thể cùng vào critical section hoặc dịch vụ phụ thuộc xử lý nhanh hơn.

## 7. Quan sát scheduler và chẩn đoán tắc nghẽn

**JDK 21+:** dùng các khả năng hỗ trợ virtual thread của Java Flight Recorder (JFR) và thread dump:

```bash
# JVM đang chạy: thay PID bằng process id thật
jcmd <PID> Thread.dump_to_file -format=json threads.json

# Ví dụ khởi chạy app có ghi JFR đến khi JVM kết thúc
java -XX:StartFlightRecording=filename=recording.jfr,dumponexit=true CarrierSchedulingDemo

jfr print --events jdk.VirtualThreadPinned,jdk.VirtualThreadSubmitFailed recording.jfr
```

- `jdk.VirtualThreadPinned` giúp phát hiện trường hợp block trong lúc pinned; các cấu hình JFR phổ biến bật sự kiện này với ngưỡng 20 ms, nên không có event **không đủ chứng minh** không có pinning ngắn.
- `jdk.VirtualThreadSubmitFailed` phản ánh thất bại khi đưa tác vụ virtual thread vào lịch chạy, thường cần kiểm tra sức ép tài nguyên.
- `jdk.VirtualThreadStart`/`jdk.VirtualThreadEnd` thường không bật mặc định vì khối lượng sự kiện có thể rất lớn. Bật có chủ đích nếu cần phân tích vòng đời.
- Thread dump giúp xem virtual threads, stack và nơi chờ. Không nên đọc số lượng virtual threads trong dump thành số carrier hay số thread đang chạy CPU.

**JDK 24+:** có thêm công cụ quan sát trực tiếp scheduler:

```bash
jcmd <PID> Thread.vthread_scheduler
jcmd <PID> Thread.vthread_pollers
```

JDK 24 cũng đưa vào `jdk.management.VirtualThreadSchedulerMXBean` (JMX), có các số đo như target parallelism, số carrier đã tạo, ước lượng virtual threads đang mounted và số đang queued; có hỗ trợ chỉnh target parallelism qua MXBean. Các con số là **ảnh chụp/ước lượng nhất thời**, không thể dùng riêng lẻ để kết luận rò rỉ hay deadlock. Các lệnh `Thread.vthread_scheduler` và MXBean **không có trong JDK 21**.

Đọc hiện tượng theo chuỗi nguyên nhân:

1. **Nhiều virtual threads parked** nhưng CPU thấp, throughput bình thường: thường là tác vụ đang chờ I/O/tín hiệu; không tự coi là lỗi.
2. **Nhiều virtual threads runnable/queued**, throughput thấp: kiểm tra carrier bận CPU, tác vụ không nhường tài nguyên, pinning/native blocking, mức parallelism và giới hạn tài nguyên.
3. **Nhiều sự kiện pinned kéo dài**: tìm stack/đường đi tới native hay đoạn blocking, xét phiên bản JDK và tần suất.
4. **CPU gần bão hòa**: profile điểm nóng CPU; tăng số virtual threads không tạo thêm lõi.
5. **Độ trễ cao nhưng scheduler khỏe**: xem connection pool, giới hạn downstream, thời gian I/O, timeouts, số kết nối mở đồng thời.

## 8. Những nhầm lẫn thường gặp

- **“Virtual thread luôn chạy không cần OS thread.”** Sai: khi thực thi mã, nó chạy trên carrier platform thread được OS lập lịch.
- **“Mỗi virtual thread có một carrier riêng.”** Sai: nhiều virtual threads dùng lại tập carrier, mỗi virtual thread có thể remount ở carrier khác.
- **“Mỗi thao tác blocking tự động nhường carrier.”** Sai: phụ thuộc khả năng hỗ trợ unmount, trạng thái pinning và runtime/native boundary.
- **“parallelism=2 chỉ được tạo hai virtual threads.”** Sai: đó là target song song của scheduler, không phải hạn mức số tác vụ.
- **“maxPoolSize là số task tối đa và tăng lên sẽ chữa mọi nghẽn.”** Sai: thuộc tính này quản lý platform threads của scheduler và không sửa mã block/pin hoặc giới hạn I/O bên ngoài.
- **“Gọi `Thread.currentThread()` để xem carrier.”** Sai: khi đang chạy trong virtual thread, lời gọi đó trả về chính virtual thread.
- **“Virtual threads luôn chạy công bằng và nhanh hơn platform threads.”** Sai: không có bảo đảm time-slicing công bằng ở tầng virtual-thread scheduler; điểm mạnh là tiết kiệm tài nguyên **khi chờ**, không tăng tốc CPU.
- **“Từ JDK 24, `synchronized` vẫn pin như JDK 21.”** Sai: JEP 491 thay đổi hành vi với Java monitor, dù những nguyên nhân pinning khác vẫn có thể tồn tại.
- **“Có triệu virtual threads thì bỏ mọi giới hạn.”** Sai: heap, network, socket, file descriptor, connection pools, timeouts và throughput dịch vụ ngoài vẫn hữu hạn.

## 9. Khi áp dụng vào chương trình Java

Suy nghĩ theo **tác vụ**: nếu có nhiều việc độc lập, phần lớn chờ I/O, mô hình một virtual thread cho mỗi tác vụ giúp giữ mã đồng bộ dễ đọc mà không dùng một platform thread trong toàn bộ thời gian chờ. Chọn công cụ concurrency của Java (`ExecutorService`, `Future`, `Semaphore`, `CountDownLatch`, `Lock`, `ThreadLocal`) theo ngữ nghĩa của bài toán; virtual threads không thay thế synchronization, cancellation hay backpressure.

Để đánh giá một ứng dụng thực tế:

1. Xác định phần CPU-bound, I/O-bound, thời gian chờ và giới hạn downstream.
2. Kiểm tra thư viện/I/O dùng JDK API có hỗ trợ park/unmount; đừng giả định mọi native call đều thân thiện với virtual threads.
3. Giữ xử lý `interrupt`, hủy tác vụ, timeout và tài nguyên (`try-with-resources`) rõ ràng.
4. Giới hạn truy cập tài nguyên khan hiếm bằng cơ chế phù hợp, không dùng “số virtual threads” làm bộ giới hạn gián tiếp.
5. Theo dõi JFR, thread dump và (nếu có JDK 24+) scheduler metrics trước khi chỉnh properties.

**Tự kiểm tra:**

1. Một virtual thread ngủ/chờ I/O rồi chạy lại trên carrier khác có làm đổi danh tính `Thread.currentThread()` không? **Không.** Danh tính thread logic vẫn là virtual thread.
2. Vì sao 1.000 virtual threads parked thường không cần 1.000 carrier? **Vì các virtual threads có thể unmount, nhường carrier cho công việc runnable khác.**
3. Vì sao hàng đợi runnable tăng khi CPU bão hòa? **Vì virtual threads chờ lượt mount/chạy trên số carrier/CPU hữu hạn; thêm tác vụ không tăng năng lực CPU.**
4. Tại sao kết luận “mọi synchronized đều pin” lỗi thời? **JEP 491 trong JDK 24 thay đổi cách virtual threads chờ Java monitor mà không giữ carrier.**

## 10. Tài liệu chính thức để đối chiếu theo phiên bản

- [Oracle Java 21 — Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html): cơ chế mount/unmount, trường hợp pinning cũ và JFR.
- [Oracle Java 25 — Virtual Threads](https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html): hướng dẫn vận hành/adoption và pinning hiện đại.
- [Java 25 — Thread API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Thread.html): đặc điểm virtual thread và system properties của scheduler.
- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444): nền tảng thiết kế và mô hình M:N.
- [JEP 491 — Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491): thay đổi Java monitor kể từ JDK 24.
- [Java 25 — VirtualThreadSchedulerMXBean](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.management/jdk/management/VirtualThreadSchedulerMXBean.html): giám sát scheduler từ JDK 24.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-007">Vì sao không pool virtual threads?</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao không pool virtual threads?

**Câu trả lời:** Với Java 21 trở lên, hãy tạo **một virtual thread cho mỗi tác vụ đồng thời** thay vì duy trì một nhóm virtual thread để tái sử dụng. Thread pool truyền thống giải quyết sự khan hiếm và chi phí tạo **platform thread** (luồng gắn với luồng hệ điều hành). Virtual thread nhẹ hơn nhiều, được JVM quản lý để có thể tạo, chạy, chờ rồi kết thúc theo vòng đời của từng tác vụ. Pool chúng thường áp đặt một giới hạn giả tạo, tăng hàng đợi và làm mất lợi ích mô hình *thread-per-task*.

Điều này **không có nghĩa là cho phép mọi thao tác truy cập tài nguyên không giới hạn**: vẫn cần giới hạn kết nối, truy vấn, request, bộ nhớ và lượng công việc đang chờ bằng cơ chế đúng mục đích như `Semaphore`, connection pool và backpressure. Phạm vi bài này là **concurrency của Java nói chung**, không đi vào cấu hình hay cơ chế của Spring.

## 1. Pool platform threads ra đời để làm gì?

Với `java.lang.Thread` kiểu **platform thread**, một luồng Java thường gắn với một luồng hệ điều hành trong suốt vòng đời của nó. Việc có quá nhiều luồng như vậy gây tốn tài nguyên hệ thống: stack, bộ nhớ liên quan, chi phí tạo/hủy và điều phối. Bởi vậy, thay vì tạo một platform thread mới cho mỗi tác vụ, ta thường dùng `Executors.newFixedThreadPool(n)`:

1. Hệ thống giữ một số hữu hạn platform thread, ví dụ 16.
2. Tác vụ đến được đưa vào hàng đợi.
3. Mỗi worker lấy một tác vụ, chạy xong rồi tái sử dụng cùng platform thread để chạy tác vụ khác.

Đây là lựa chọn hợp lý khi ta cần quản lý số lượng **platform thread** hoặc số tác vụ CPU đang chạy. Nhưng nếu 16 platform thread đều gọi I/O chặn trong 500 ms, các tác vụ khác phải chờ trong hàng đợi, dù CPU có thể đang rảnh. Tăng pool liên tục để che độ trễ I/O lại tiêu tốn thêm OS threads.

## 2. Virtual thread đổi điều gì?

Virtual thread là một đối tượng `Thread` thực sự trong Java API nhưng **không giữ riêng một OS thread trong toàn bộ thời gian sống**. Virtual thread được thực thi trên **carrier thread** (platform thread dùng bởi bộ lập lịch của JVM). Khi virtual thread gặp một thao tác blocking được JDK hỗ trợ như `Thread.sleep` hoặc nhiều thao tác socket I/O, JVM có thể *unmount* (tách) virtual thread khỏi carrier, để carrier chạy tác vụ khác; khi thao tác sẵn sàng, virtual thread có thể được chạy tiếp trên một carrier thích hợp.

Hình dung:

```text
Platform-thread pool:
Task A ─── blocking I/O ─── hoàn thành ──> worker #1
Task B ─── blocking I/O ─── hoàn thành ──> worker #2
Task C ─── đợi worker rảnh ──────────────> worker #1

Virtual threads:
Task A / virtual thread A ── I/O chờ ────> hoàn thành
Task B / virtual thread B ── I/O chờ ────> hoàn thành
Task C / virtual thread C ── I/O chờ ────> hoàn thành
       Các carrier thread được JVM chia sẻ trong thời gian thực thi.
```

Vì vậy, virtual thread phù hợp với lượng lớn công việc *độc lập, thường xuyên chờ I/O*. Nó giữ mô hình viết mã tuần tự dễ hiểu (`read`, `sleep`, `get`, gọi dịch vụ...), trong khi giảm số platform thread phải nằm chờ.

**Điểm chính:** Một virtual thread thường đại diện **một tác vụ**, chứ không đại diện một worker tồn tại lâu dài để lấy hết tác vụ này đến tác vụ khác. Virtual thread kết thúc khi tác vụ kết thúc. Chính JVM đã quản lý việc dùng lại **carrier thread** ở lớp thấp hơn; ứng dụng không cần tự xây thêm pool virtual threads.

## 3. Tại sao pool virtual threads không có lợi?

| Vấn đề | Giữ pool virtual threads | Một virtual thread cho mỗi task |
| --- | --- | --- |
| Vòng đời | Virtual thread tồn tại lâu để nhận nhiều task | Virtual thread được tạo cho một task và kết thúc |
| Giới hạn | Số worker cố định khiến task phải xếp hàng | Số virtual thread đang sống tùy theo nhu cầu công việc |
| Chi phí tránh được | Chủ yếu cố tiết kiệm tạo/hủy virtual thread vốn đã nhẹ | Không cần tái sử dụng virtual thread |
| Điều tiết tài nguyên | Lẫn lộn số worker với giới hạn tài nguyên thực | Giới hạn rõ ngay nơi dùng tài nguyên |
| Quan sát/debug | Một worker có thể xử lý nhiều tác vụ kế tiếp | Stack trace và vòng đời gắn trực tiếp với từng tác vụ |

Giả sử cần thực hiện 5.000 lần gọi I/O, mỗi lần mất khá nhiều thời gian để chờ phản hồi. Một pool chỉ có 20 virtual threads sẽ chỉ cho 20 tác vụ đi vào phần xử lý tại một thời điểm và xếp các tác vụ còn lại vào hàng đợi, dù việc cho nhiều virtual threads *đang chờ* thường không đòi hỏi tương ứng chừng ấy carrier threads. Ta đã vô tình tạo lại nút thắt cổ chai tương tự một pool nhỏ.

Ngược lại, nếu hệ thống đích chỉ cho phép **20 request đồng thời**, việc mở 5.000 request cùng lúc là sai. Giới hạn đúng là **số request đến hệ thống đích**, không phải số virtual threads của toàn ứng dụng: các công việc không sử dụng hệ thống đích vẫn có thể tiếp tục độc lập.

Một lỗi thiết kế là:

```java
// KHÔNG NÊN: giữ cố định 20 virtual threads để tái sử dụng như worker.
ExecutorService executor =
        Executors.newFixedThreadPool(20, Thread.ofVirtual().factory());
```

Đoạn trên tạo **pool các worker là virtual threads** và áp trần 20 tác vụ đang chạy trong executor. Đây là API có thể dùng được, nhưng không phải mô hình khuyến nghị để tận dụng virtual threads cho tác vụ I/O độc lập. Ngược lại, `Executors.newVirtualThreadPerTaskExecutor()` **không phải pool virtual threads**: mỗi task được submit sẽ có virtual thread riêng.

## 4. Cách dùng chuẩn: virtual thread per task

Java 21 hỗ trợ trực tiếp `Executors.newVirtualThreadPerTaskExecutor()` và `Thread.ofVirtual()`. Ví dụ sau chạy độc lập bằng Java 21+, không cần thư viện ngoài:

**Tệp `WhyNotPoolVirtualThreads.java`:**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

public class WhyNotPoolVirtualThreads {
    private static final int TASK_COUNT = 8;
    private static final Semaphore SERVICE_SLOTS = new Semaphore(2);

    public static void main(String[] args) throws Exception {
        List<Future<String>> results = new ArrayList<>();

        // Mỗi submit() tạo một virtual thread cho một tác vụ.
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= TASK_COUNT; i++) {
                final int taskId = i;
                results.add(executor.submit(() -> {
                    // Giới hạn thao tác lên tài nguyên, không giới hạn số virtual threads.
                    SERVICE_SLOTS.acquire();
                    try {
                        Thread.sleep(50); // Giả lập chờ I/O; không phải gọi mạng thật.
                        return "Task " + taskId
                                + ": virtual=" + Thread.currentThread().isVirtual();
                    } finally {
                        SERVICE_SLOTS.release();
                    }
                }));
            }

            // Lấy Future theo thứ tự submit để stdout luôn có thứ tự xác định.
            for (Future<String> result : results) {
                System.out.println(result.get());
            }
        } // close() chờ các tác vụ hoàn tất; executor không giữ pool VT.
    }
}
```

Biên dịch và chạy:

```bash
javac WhyNotPoolVirtualThreads.java
java WhyNotPoolVirtualThreads
```

Kết quả:

```text
Task 1: virtual=true
Task 2: virtual=true
Task 3: virtual=true
Task 4: virtual=true
Task 5: virtual=true
Task 6: virtual=true
Task 7: virtual=true
Task 8: virtual=true
```

**Giải thích chính xác:**

- Có **8 task và 8 virtual threads** được executor tạo ra, nhưng tại một thời điểm chỉ **tối đa 2 task** được vào vùng giả lập gọi dịch vụ, vì `Semaphore(2)`.
- `acquire()` có thể làm một virtual thread chờ; đặt `release()` trong `finally` để trả permit ngay cả khi tác vụ lỗi hoặc bị ngắt sau khi acquire thành công. Nếu bị ngắt ngay khi đang `acquire()`, chương trình chưa lấy permit nên sẽ không chạy `finally` tương ứng.
- `Thread.sleep(50)` mô phỏng khoảng chờ, không chứng minh hiệu năng I/O thật. Kết quả `virtual=true` là xác định; thứ tự **thực thi** các tác vụ và carrier cụ thể không được đảm bảo, chỉ thứ tự **in** được cố định bằng danh sách `Future`.
- `Future.get()` cho phép lấy kết quả và nhận lỗi của tác vụ qua `ExecutionException`; `try-with-resources` đóng executor sau khi dùng xong.

Nếu chỉ cần một virtual thread riêng lẻ, cũng có thể gọi `Thread.ofVirtual().start(runnable)`; phải quản lý việc chờ kết thúc và xử lý lỗi thích hợp. Với nhiều task và `Future`, executor theo từng tác vụ thường dễ tổ chức hơn.

## 5. Giới hạn đúng thứ cần giới hạn

### 5.1. Tài nguyên bên ngoài: `Semaphore` hoặc connection pool

Ví dụ một dịch vụ chỉ xử lý được 10 request đồng thời. Dùng `Semaphore(10)` **quanh thao tác gọi dịch vụ**: request thứ 11 chờ permit; tác vụ khác không đụng tới dịch vụ đó vẫn được chạy. Khi mỗi truy vấn phải lấy kết nối từ một connection pool hữu hạn, chính **connection pool** đã có chức năng chờ/giới hạn số kết nối; thường không cần thêm semaphore trùng mục đích, trừ khi có yêu cầu bảo vệ khác.

Không nên hiểu nhầm rằng vì virtual threads rẻ mà database, socket, file descriptor, băng thông, API quota, bộ nhớ hay transaction cũng trở thành vô hạn.

### 5.2. Áp lực đầu vào: backpressure và giới hạn số công việc đã nhận

`Semaphore` đặt **bên trong** virtual thread bảo vệ phần việc được giới hạn, nhưng nếu tạo hàng triệu virtual threads cùng chờ permit thì vẫn tốn bộ nhớ, task context và công sức quản lý. Khi đầu vào không bị chặn, cần thêm **giới hạn số request đang nhận/xử lý**, hàng đợi có kích thước hữu hạn, từ chối có kiểm soát, hoặc làm chậm phía producer. Đây là **admission control/backpressure**, một quyết định riêng với số carrier threads.

Có thể đặt permit **trước khi submit task** để producer phải chờ khi lượng task đang tồn tại đạt ngưỡng; khi task kết thúc thì trả permit, kể cả khi lỗi. Cần xử lý cả tình huống `submit()` bị từ chối để không làm rò permit. Không dùng giới hạn tùy tiện như "pool 100 virtual threads" để thay thế phân tích yêu cầu tải, độ trễ và tài nguyên thực.

### 5.3. CPU-bound: không có phép màu tăng tốc

Virtual threads cải thiện **khả năng mở rộng khi nhiều task chờ**, chứ không làm CPU có thêm lõi. Với tác vụ chủ yếu tính toán (mã hóa, nén, xử lý số học), thêm virtual threads có thể chỉ làm tăng cạnh tranh CPU và context switching. Số công việc CPU chạy song song thực tế vẫn phụ thuộc số lõi/CPU được cấp, scheduler và cấu hình JVM. Một pool platform threads hữu hạn hoặc cơ chế điều phối riêng cho khối lượng CPU có thể phù hợp hơn. Đừng kết luận "virtual threads luôn nhanh hơn platform threads" chỉ từ số lượng thread.

## 6. Các lỗi hiểu thường gặp

1. **"Virtual threads không dùng pool nào cả."** Chưa chính xác. Ứng dụng không nên pool **virtual threads**; JVM vẫn dùng các **carrier platform threads** và bộ lập lịch nội bộ để thực thi chúng. Đừng cố điều khiển carrier bằng cách tự làm pool virtual threads.
2. **"Dùng `newVirtualThreadPerTaskExecutor()` nghĩa là không giới hạn concurrency nên an toàn."** Sai. Executor tạo một virtual thread cho mỗi task được submit; các hạn mức downstream, bộ nhớ, hàng đợi và áp lực đầu vào vẫn phải được quản lý.
3. **"Semaphore biến virtual threads thành pool."** Sai. Semaphore giới hạn một *đoạn truy cập tài nguyên*, không tái sử dụng một nhóm virtual threads cố định. Các virtual threads vẫn sống theo task của chúng.
4. **"Có 10.000 virtual threads tức 10.000 OS threads."** Sai. Nhiều virtual threads có thể luân phiên dùng một số lượng nhỏ hơn nhiều carrier threads, đặc biệt khi phần lớn đang chờ thao tác blocking được hỗ trợ. Số carrier thực tế do JVM và workload quyết định.
5. **"I/O chặn luôn nhả carrier trong mọi tình huống."** Sai. Một số thao tác native, foreign-function hoặc tình huống runtime đặc biệt có thể giữ carrier (pinning); cần kiểm tra bằng đo đạc trên JDK đang chạy.
6. **"Mỗi virtual thread có thể giữ cache lớn trong `ThreadLocal` vì thread được tái sử dụng."** Sai. `ThreadLocal` vẫn hoạt động, nhưng các virtual threads thường sống ngắn và rất đông; cache nặng theo thread dễ phát sinh cấp phát và áp lực GC. Chỉ giữ dữ liệu ngữ cảnh thật sự cần và đo footprint.
7. **"Có thể bỏ connection pool khi chuyển sang virtual threads."** Sai. Connection pool tồn tại để quản lý **kết nối thật**, vốn hữu hạn và tốn kém, nên vẫn có giá trị.

## 7. Lưu ý theo phiên bản JDK và vận hành

- **Java 21:** Virtual threads là tính năng chính thức (JEP 444). Khi chạy blocking trong `synchronized`, virtual thread có thể bị **pin** vào carrier; nếu xảy ra nhiều và lâu sẽ ảnh hưởng scalability. Cần nhận diện vị trí I/O có thể block kéo dài bên trong monitor; thay đổi sang cơ chế khóa thích hợp chỉ khi cần dựa trên profiling.
- **Java 24+:** JEP 491 cho phép phần lớn tình huống blocking liên quan `synchronized` không còn pin carrier như trước. Vì thế **không được lặp lại một cách máy móc** lời khuyên của JDK 21 rằng mọi `synchronized` đều phải đổi thành `ReentrantLock`. Một số trường hợp native/foreign vẫn có thể pin; phải dựa vào phiên bản JDK và quan sát thực tế.
- **Quan sát:** Đo throughput, p95/p99 latency, số request đang chờ permit, thời gian chờ connection pool, memory/GC, CPU và tình huống pinning. JDK Flight Recorder có sự kiện `jdk.VirtualThreadPinned`; có thể dùng thread dump hỗ trợ virtual threads (`jcmd <pid> Thread.dump_to_file -format=json <file>`) tùy phiên bản/công cụ. Đừng dùng riêng "số lượng thread" làm thước đo tối ưu.
- **Hủy và lỗi:** Task có thể thất bại, bị interrupt hoặc quá hạn. Bảo đảm giải phóng permit, đóng tài nguyên, thu nhận lỗi từ `Future` và quản lý vòng đời executor. Virtual threads giúp chờ hiệu quả hơn; chúng không tự giải quyết timeout, retry hay cancellation.

## 8. Khi nào áp dụng?

**Nên chọn virtual-thread-per-task** khi nhiều công việc độc lập dùng Java blocking API để gọi I/O và cần giữ lối viết tuần tự. **Chọn giới hạn riêng** ở từng tài nguyên có quota hoặc chi phí thực. **Kiểm soát đầu vào** khi lượng task có thể tăng vô hạn. **Đo thực tế** nếu ứng dụng thiên về CPU hoặc có nhiều blocking native/pinning.

Ghi nhớ: **Pool platform threads vì platform threads đắt; không pool virtual threads vì virtual threads được tạo theo task. Giới hạn tài nguyên bằng cơ chế giới hạn tài nguyên.**

## Tài liệu tham khảo

- [OpenJDK JEP 444 — Virtual Threads](https://openjdk.org/jeps/444): mô hình virtual thread per task, vòng đời và scalability.
- [Oracle Java Virtual Threads Guide](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html): không pool virtual threads, semaphore, thread-local và lưu ý trên Java 21.
- [OpenJDK JEP 491 — Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491): thay đổi pinning từ Java 24.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-008">Giới hạn tài nguyên ngoài bằng Semaphore/pool kết nối</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn tài nguyên ngoài bằng Semaphore và pool kết nối khi dùng Virtual Threads

## 1. Vấn đề: nhiều virtual thread không đồng nghĩa nhiều tài nguyên ngoài

Virtual Threads (ổn định từ Java 21) cho phép viết mã đồng bộ, blocking theo kiểu một tác vụ — một luồng, với chi phí luồng thấp hơn platform thread trong các công việc chờ I/O. Tuy nhiên, **virtual thread rẻ không làm database connection, socket, phiên kết nối, quota API hay dịch vụ phía ngoài trở nên vô hạn**.

Giả sử ứng dụng nhận 5.000 yêu cầu cùng lúc. Mỗi yêu cầu chạy trên một virtual thread và phải truy vấn một dịch vụ chỉ cho phép tối đa 20 lời gọi đồng thời. Nếu cứ gọi ngay mà không giới hạn, bên ngoài có thể quá tải, trả lỗi, tăng độ trễ hoặc tự từ chối kết nối. Cần tách hai loại giới hạn:

- **Độ đồng thời của tác vụ Java:** có thể có rất nhiều virtual thread đang sống, bao gồm những luồng đang chờ.
- **Độ đồng thời dùng tài nguyên ngoài:** chỉ cho phép một số hữu hạn tác vụ ở trong đoạn sử dụng tài nguyên tại cùng thời điểm.

**Nguyên tắc:** tạo virtual thread theo tác vụ; dùng `Semaphore` hoặc cơ chế cấp phát của **connection pool** để kiểm soát tài nguyên khan hiếm. Không tạo một “pool virtual threads” chỉ để giới hạn số tác vụ đang truy cập dịch vụ.

## 2. Semaphore là gì, giới hạn được điều gì?

`java.util.concurrent.Semaphore` giữ một số lượng **permit** (giấy phép logic). Mỗi lời gọi `acquire()` xin một permit; khi hết permit, bên gọi phải chờ. `release()` trả permit để một tác vụ khác có thể tiếp tục. Không có đối tượng “permit” vật lý nào được cấp: đây là bộ đếm và cơ chế đồng bộ.

Ví dụ `new Semaphore(3)` cho phép tối đa **ba tác vụ đã lấy permit nhưng chưa trả permit** ở trong đoạn bảo vệ cùng lúc. Một virtual thread bị chặn tại `acquire()` đang chờ quyền truy cập, chứ không giữ một kết nối thật. Đoạn chờ thường phù hợp với mô hình virtual thread vì Java có thể tạm dừng virtual thread và dành carrier cho việc khác.

```text
Nhiều tác vụ trên virtual threads
    ├── tác vụ A ── acquire ──► dùng dịch vụ ngoài ──► release
    ├── tác vụ B ── acquire ──► dùng dịch vụ ngoài ──► release
    ├── tác vụ C ── acquire ──► dùng dịch vụ ngoài ──► release
    ├── tác vụ D ── chờ permit ──────────────────────► ...
    └── tác vụ E ── chờ permit ──────────────────────► ...
                 Semaphore(3)
```

Điều quan trọng là **ranh giới giữ permit phải bao trọn đúng phần sử dụng tài nguyên cần giới hạn**. Nếu lấy permit trước lời gọi I/O nhưng trả ngay khi lời gọi bất đồng bộ vừa được khởi chạy, số thao tác thực sự đang chạy có thể vượt mức giới hạn. Với API bất đồng bộ, trả permit sau khi thao tác hoàn tất, kể cả thất bại hoặc hủy.

Các thao tác nên biết:

| API | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `acquire()` | Chờ đến khi lấy được một permit | Có thể ném `InterruptedException`; chưa lấy được thì không được `release()` |
| `tryAcquire()` | Thử lấy ngay, không chờ | Trả `false` khi thiếu permit; bản không timeout có thể vượt thứ tự chờ của semaphore công bằng |
| `tryAcquire(timeout, unit)` | Chờ tối đa một khoảng thời gian | Trả `false` khi hết thời gian, hoặc ném `InterruptedException` khi bị ngắt |
| `release()` | Tăng số permit sẵn có thêm một | `Semaphore` không kiểm tra người gọi có từng `acquire()`; trả thừa phá giới hạn |
| `availablePermits()` | Số permit đang sẵn có tại một thời điểm | Chỉ là ảnh chụp tức thời, không dùng để kiểm tra rồi mới `acquire()` |
| `new Semaphore(n, true)` | Yêu cầu chính sách chọn người chờ công bằng | Cân bằng thứ tự chờ với chi phí/throughput; không bảo đảm thứ tự bắt đầu tuyệt đối |

`Semaphore` giới hạn **số lượt sử dụng đồng thời**, không tự tạo, tái sử dụng, đóng hoặc kiểm tra tình trạng kết nối thật. Một semaphore cũng không phải cơ chế giới hạn **số request trên giây**: 10 permit có thể phục vụ nhiều hơn 10 request/giây nếu mỗi request hoàn tất rất nhanh.

## 3. Ví dụ Java 21+: 12 virtual threads, tối đa 3 lời gọi ngoài

Ví dụ độc lập dưới đây mô phỏng một dịch vụ I/O bằng `Thread.sleep(100)`. Trong ứng dụng thực tế, thay đoạn giả lập bằng lời gọi JDBC, HTTP client hoặc thao tác I/O cần giới hạn. Mọi tác vụ vẫn có virtual thread riêng; chỉ **ba** tác vụ tối đa được ở trong “vùng tài nguyên ngoài” cùng lúc.

Lưu thành `LimitExternalCalls.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class LimitExternalCalls {
    private static final int LIMIT = 3;
    private static final Semaphore GATE = new Semaphore(LIMIT, true);

    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
    private static final AtomicInteger PEAK = new AtomicInteger();
    private static final AtomicInteger COMPLETED = new AtomicInteger();

    private static void callExternalService() throws InterruptedException {
        // Nếu đang chờ mà bị interrupt, chưa sở hữu permit nào.
        GATE.acquire();
        try {
            int now = IN_FLIGHT.incrementAndGet();
            PEAK.accumulateAndGet(now, Math::max);
            try {
                // Mô phỏng một lời gọi I/O blocking.
                Thread.sleep(100);
                COMPLETED.incrementAndGet();
            } finally {
                IN_FLIGHT.decrementAndGet();
            }
        } finally {
            // Chỉ chạy sau khi acquire() đã thành công.
            GATE.release();
        }
    }

    public static void main(String[] args) throws Exception {
        List<Future<?>> futures = new ArrayList<>();

        try (ExecutorService executor =
                 Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 12; i++) {
                futures.add(executor.submit(() -> {
                    callExternalService();
                    return null;
                }));
            }

            // Nhận lỗi từ từng tác vụ, không chỉ chờ executor kết thúc.
            for (Future<?> future : futures) {
                future.get();
            }
        }

        System.out.println("completed=" + COMPLETED.get());
        System.out.println("active=" + IN_FLIGHT.get());
        System.out.println("peak<=3: " + (PEAK.get() <= LIMIT));
    }
}
```

Biên dịch và chạy bằng **JDK 21+**:

```bash
javac LimitExternalCalls.java
java LimitExternalCalls
```

```text
completed=12
active=0
peak<=3: true
```

Ba dòng trên là kết quả xác định khi chương trình kết thúc bình thường. Giá trị thực tế của `PEAK` có thể từ 1 đến 3 tùy cách lập lịch; điều cần chứng minh là **không bao giờ lớn hơn 3**. `Future.get()` đợi tác vụ và chuyển lỗi của tác vụ thành `ExecutionException` tại luồng gọi, tránh tình trạng tưởng rằng tất cả thành công dù có tác vụ lỗi. `ExecutorService` trong `try-with-resources` đóng sau khi các tác vụ hoàn thành.

Phân tích đoạn bảo vệ:

1. Lấy permit **trước** khi tăng `IN_FLIGHT` hoặc truy cập dịch vụ ngoài.
2. Dùng `try/finally` để giảm bộ đếm hoạt động ngay cả khi I/O lỗi hoặc bị ngắt.
3. Dùng `try/finally` bên ngoài để trả permit **đúng một lần** sau một `acquire()` thành công.
4. Không đặt `release()` vào `finally` bao cả lời gọi `acquire()` theo cách có thể chạy khi `acquire()` ném `InterruptedException` trước khi lấy được permit.

`AtomicInteger` chỉ giúp phép đo an toàn giữa các luồng; **`Semaphore` mới là cơ chế bảo vệ tài nguyên**. Đặt semaphore trong một đối tượng được chia sẻ giữa mọi tác vụ truy cập cùng hạn mức. Nếu mỗi yêu cầu tự tạo `new Semaphore(3)`, mỗi yêu cầu có quota riêng và giới hạn toàn hệ thống không còn hiệu lực.

## 4. Chờ có hạn, xử lý timeout và ngắt đúng cách

`acquire()` có thể chờ vô thời hạn nếu không permit nào trở lại. Trong đường xử lý yêu cầu, hãy cân nhắc ngân sách thời gian chờ, giới hạn tải và chính sách báo lỗi. Ví dụ dưới đây là **phương thức minh họa**, giả định đã có một `Semaphore gate` dùng chung và phương thức blocking `callRemote()` cần bảo vệ:

```java
String callWithTimeout(Semaphore gate) throws InterruptedException {
    boolean acquired = gate.tryAcquire(500, java.util.concurrent.TimeUnit.MILLISECONDS);
    if (!acquired) {
        throw new IllegalStateException("Dich vu dang qua tai: het thoi gian cho permit");
    }
    try {
        return callRemote(); // Phương thức do ứng dụng cung cấp.
    } finally {
        gate.release();
    }
}
```

`callRemote()` ở đây không được khai báo nên đoạn mã này **không phải chương trình chạy độc lập**. Trong chương trình thật, lời gọi đó có thể ném ngoại lệ đặc thù của HTTP/JDBC; `finally` vẫn phải trả permit. Nếu bị interrupt trong lúc `tryAcquire(...)` đang chờ, Java ném `InterruptedException` và **không có permit nào cần trả**. Khi tác vụ xử lý `InterruptedException` thay vì truyền tiếp, thường cần khôi phục cờ ngắt bằng `Thread.currentThread().interrupt()` và kết thúc sớm thay vì tiếp tục làm I/O.

Timeout chờ permit không thay cho timeout kết nối, truy vấn hay HTTP. Chúng là các thời hạn độc lập: hết thời gian chờ permit, thời gian tạo kết nối và thời gian thực thi lệnh đều cần được quản lý riêng. Cũng không nên retry vô hạn sau khi hết permit; mỗi lần retry làm tăng lượng tác vụ chờ.

## 5. Connection pool đã quản lý số kết nối thật

**Connection pool** quản lý tập hữu hạn kết nối vật lý để tái sử dụng. Mượn một kết nối từ pool thường làm giảm số kết nối rảnh; khi trả lại, kết nối có thể phục vụ tác vụ khác. Pool có các chính sách riêng như số kết nối tối đa, timeout khi mượn, kiểm tra kết nối, vòng đời và thu hồi.

Với JDBC, ứng dụng thường lấy kết nối từ `javax.sql.DataSource`; **bản thân interface `DataSource` không bảo đảm có pool** — phải cấu hình một implementation pooling thực sự. Ví dụ minh họa:

```java
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

long countUsers(DataSource dataSource) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement =
             connection.prepareStatement("SELECT COUNT(*) FROM users");
         ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getLong(1);
    }
}
```

Đây là **phương thức minh họa**, không phải lớp chạy độc lập; cần bảng `users` và một `DataSource` đã cấu hình. Khi dùng connection pool, `connection.close()` thông thường **trả kết nối về pool**; đóng tài nguyên qua `try-with-resources` là bắt buộc để pool không cạn kiệt. `DataSource.getConnection()` có thể chờ một kết nối rảnh hoặc lỗi khi hết thời hạn, tùy pool. Mỗi tác vụ cần mượn và trả kết nối trong phạm vi ngắn nhất có thể; không gắn một kết nối suốt vòng đời virtual thread chỉ vì luồng nhẹ.

**Một pool có kích thước tối đa 10 vốn đã giới hạn số kết nối mà nó cấp cùng lúc.** Không mặc định bọc thêm `Semaphore(10)` quanh việc mượn connection: sẽ có hai hàng chờ và hai cơ chế timeout, có thể làm hệ thống khó điều chỉnh và chẩn đoán. Chỉ thêm semaphore nếu nó bảo vệ **một giới hạn độc lập** mà pool không nắm được, chẳng hạn DB chỉ cho phép 10 truy vấn nặng đồng thời dù pool có 30 kết nối, hoặc API đi kèm còn giới hạn số yêu cầu xử lý.

| Tình huống | Công cụ chính | Lý do |
| --- | --- | --- |
| API ngoài chỉ nhận 8 tác vụ cùng lúc | `Semaphore(8)` dùng chung | Giới hạn lời gọi logic; không sở hữu kết nối |
| Database chỉ cấp tối đa 20 kết nối | Pool có `maximumPoolSize=20` (tên tùy implementation) | Pool quản lý, tái sử dụng và cấp phát kết nối thật |
| 20 DB connections nhưng chỉ được chạy 5 truy vấn nặng cùng lúc | Pool 20 + semaphore 5 **riêng cho đường truy vấn nặng** | Hai giới hạn độc lập, cần đo timeout ở cả hai chỗ |
| Bên ngoài quy định 100 request/giây | Rate limiter | Semaphore giới hạn concurrent in-flight, không đặt quota theo thời gian |
| Nguồn phát sinh tác vụ vượt quá khả năng chờ của hệ thống | Bounded admission/queue và backpressure | Semaphore không tự giới hạn số virtual thread đang chờ |

## 6. Chọn giới hạn phù hợp với tài nguyên

Giới hạn phải dựa trên **năng lực và quota thật** của bên ngoài, không dựa trên số virtual thread có thể tạo hoặc số core của máy Java. Những câu hỏi cần trả lời:

- Dịch vụ/DB cho phép tối đa bao nhiêu connection, request đang xử lý hoặc phiên hoạt động? Hạn mức này được chia sẻ với những ứng dụng nào?
- Mỗi tác vụ giữ tài nguyên trong bao lâu, tỷ lệ request lỗi/timeout bao nhiêu, và lượng tác vụ xếp hàng tăng ra sao khi tải tăng?
- Cùng một yêu cầu có thể chiếm đồng thời bao nhiêu tài nguyên khác nhau? Có nguy cơ hai đường xử lý giữ A chờ B và giữ B chờ A hay không?
- Hệ thống cần phản hồi “đang bận” sau bao lâu thay vì chờ mãi?

Định luật Little cho hệ ổn định cho trực giác: **số tác vụ trung bình đang xử lý ≈ throughput × thời gian xử lý trung bình**. Ví dụ dịch vụ cần 0,2 giây mỗi lời gọi ở mức 50 lời gọi/giây thì trung bình khoảng 10 lời gọi đang chạy. Đây là phép ước lượng để bắt đầu đo đạc, **không phải công thức tự động chọn permit**, vì còn giới hạn dịch vụ, phân bố đuôi độ trễ, số replica và đường đi của request.

Giữ permit trong đoạn thực sự cần quota: không nên khóa slot khi đang parse JSON độc lập, đọc cache hay chuẩn bị dữ liệu dài dòng. Ngược lại, nếu một workflow gồm hai I/O đều tiêu thụ cùng hạn mức trong toàn bộ khoảng thời gian, phải xác định rõ lúc nào slot thực sự được giữ. Nếu cấu hình nhiều instance, **mỗi instance có semaphore riêng**: 5 replica × 8 permit có thể gây 40 tác vụ đồng thời lên một dịch vụ chỉ cho phép 8. Khi quota là toàn hệ thống, cần chia ngân sách cho replica hoặc một cơ chế phối hợp phân tán; semaphore nội bộ JVM không tự phối hợp giữa các process.

## 7. Tình huống lỗi và cách khắc phục

**Quên trả permit.** Một số lần ném exception hoặc return sớm làm rò permit; sau đủ lần, toàn bộ tác vụ tiếp theo chờ vô hạn. Khắc phục bằng `try/finally` ngay sau lần lấy permit thành công, gói việc mượn/trả tài nguyên thật trong `try-with-resources`.

**Trả permit khi chưa lấy hoặc trả hai lần.** `Semaphore.release()` không buộc caller là người sở hữu permit và không chặn việc trả thừa. Kết quả có thể cho phép vượt qua hạn mức ban đầu. Không viết `release()` trong nhánh timeout/interrupt chưa acquire thành công; không trao trách nhiệm release cho hai tầng cùng lúc.

**Dùng `availablePermits() > 0` để quyết định vào.** Giá trị có thể thay đổi ngay khi kiểm tra; một tác vụ khác đã lấy permit. Hãy gọi trực tiếp `acquire()` hoặc `tryAcquire()` để thao tác lấy permit được đồng bộ.

**Dùng fixed thread pool để điều tiết virtual threads.** Giới hạn worker thread không diễn đạt trực tiếp số lượt dùng dịch vụ. Một pool virtual threads cũng phá mục tiêu mỗi tác vụ một virtual thread. Dùng semaphore ngay quanh đoạn I/O; giữ executor tạo virtual threads theo tác vụ.

**Đặt semaphore trùng kích thước với connection pool ở mọi nơi.** Thừa hàng chờ và timeout, khó hiểu chỗ nào cạn trước. Kiểm tra xem pool đã áp đúng giới hạn kết nối chưa; chỉ thêm gate nếu có quota nghiệp vụ/ngoài riêng.

**Tăng permit để giảm hàng chờ mà không đo hệ thống ngoài.** Có thể biến hàng chờ trong Java thành hàng chờ lâu hơn ở DB/API, tăng timeout và lỗi. Giới hạn là công cụ bảo vệ phía ngoài; cần đồng thời kiểm soát lượng công việc đầu vào.

**Cho rằng FIFO luôn tuyệt đối.** `new Semaphore(n, true)` cấp permit cho người chờ theo điểm xếp hàng nội bộ, nhưng không hứa thứ tự bắt đầu/thành công của các thao tác I/O. `tryAcquire()` không timeout có thể giành permit trước người chờ ngay cả khi `fair=true`; xem yêu cầu fairness thực tế trước khi dùng.

**Giữ permit khi làm việc không liên quan.** Nếu chờ lock khác, chờ phản hồi không dùng tài nguyên hoặc làm CPU dài trong đoạn bảo vệ, thông lượng hữu ích giảm. Đo thời gian giữ permit và thu hẹp phạm vi theo quota cần bảo vệ; tránh nhiều gate với thứ tự khóa không nhất quán.

## 8. Quan sát và kiểm chứng khi vận hành

Theo dõi riêng **lượng công việc**, **thời gian chờ**, và **tài nguyên thật**:

- Semaphore: số slot đang dùng, thời gian chờ `acquire`, số lần `tryAcquire` timeout, số tác vụ đang chờ; `getQueueLength()` chỉ là **ước lượng**, không phải bộ đếm chính xác để điều tiết.
- Connection pool: active/idle/max connections, số yêu cầu chờ mượn connection, timeout khi mượn, thời gian giữ connection, rò connection.
- Dịch vụ ngoài: số lời gọi đang chạy, throughput, timeout, tỷ lệ lỗi, p95/p99 latency, throttling do quota.
- Tải đầu vào: số tác vụ đang chờ ở cổng nhận request, lượng bộ nhớ dành cho tác vụ/đầu vào chưa được xử lý. Virtual thread nhẹ nhưng không vô hạn bộ nhớ.

Một bài kiểm tra tốt là phát sinh đồng thời nhiều hơn số permit, đo `inFlight` của chính đoạn thao tác ngoài và khẳng định `peak <= limit`. Sau đó cố ý tạo exception, interrupt, timeout để kiểm tra permit vẫn trở về mức ban đầu và connection được trả. Nếu thử với pool, kiểm tra số connection vật lý không vượt cấu hình. Không dùng số lượng virtual thread đang sống để suy ra số connection đang sử dụng.

## 9. Ghi nhớ

- Virtual thread giúp giữ mô hình code blocking dễ đọc khi có nhiều tác vụ I/O; **nó không tăng quota của database/API**.
- Semaphore bảo vệ một **đoạn công việc có giới hạn concurrent in-flight**; `acquire` thành công mới được `release`, đúng một lần, trong `finally`.
- Connection pool quản lý **kết nối thật** và đã có cơ chế chặn/timeout cấp phát; thường không cần thêm semaphore có cùng nhiệm vụ.
- Timeout, ngắt, backpressure, giới hạn toàn hệ thống và observability là những phần cần thiết để giới hạn tài nguyên vận hành an toàn.

## Tài liệu tham khảo

- Oracle, [Virtual Threads — Java SE 21](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html): hướng dẫn virtual threads, semaphore và connection pools.
- Oracle, [`Semaphore` Java API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Semaphore.html): các thao tác và bảo đảm của semaphore.
- Oracle, [`Executors` Java API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html): `newVirtualThreadPerTaskExecutor()`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-009">Pinning với monitor/native code ở Java 21</a>

<details>
<summary>Xem chi tiết</summary>

# Pinning với monitor/native code ở Java 21

> **Phạm vi:** Java SE 21, virtual threads theo JEP 444. Bài tập trung vào cơ chế JVM và các API đồng thời của Java; không bàn về cấu hình hay tích hợp của Spring.

## 1. Pinning là gì và vì sao cần quan tâm?

Virtual thread là một `java.lang.Thread` nhẹ, do JVM lập lịch. Khi chạy, virtual thread được **mount** lên một platform thread (thường gọi là **carrier thread**). Nhiều virtual thread có thể lần lượt sử dụng số carrier nhỏ hơn rất nhiều số lượng virtual thread. Mô hình này đặc biệt có lợi khi tác vụ phải đợi I/O: nếu thao tác chờ được JDK hỗ trợ, JVM có thể **unmount** virtual thread, giải phóng carrier để chạy tác vụ khác.

**Pinning** là tình huống virtual thread không thể unmount khỏi carrier trong một đoạn thực thi nhất định. Bản thân việc bị pin không gây lỗi về tính đúng đắn. Vấn đề xuất hiện khi virtual thread **chặn (block) trong lúc bị pin**: carrier cũng phải đứng chờ, làm giảm số carrier còn phục vụ những virtual thread khác.

Luồng bình thường khi đợi I/O:

~~~text
Virtual thread A ── chạy trên carrier #1 ── gặp điểm chờ
                                               ↓
                                    A unmount / chờ
Carrier #1 ── rảnh ── chạy virtual thread B, C, ...
                                               ↓
                                    I/O của A hoàn tất
Virtual thread A ── được mount lại lên một carrier sẵn có
~~~

Trường hợp bị pin:

~~~text
Virtual thread A ── đang ở trong monitor/native frame
             ↓
          gặp điểm chờ lâu
             ↓
A không thể unmount → carrier #1 cũng bị giữ lại trong lúc đợi
             ↓
Ít carrier hơn để chạy B, C, ... → throughput/latency có thể xấu đi
~~~

Ba khái niệm cần tách bạch:

- **Blocking**: tác vụ phải đợi một điều kiện, ví dụ `Thread.sleep`, socket I/O hoặc chờ khóa. Blocking không tự động có nghĩa là pinning; nhiều thao tác chờ có thể nhường carrier.
- **Pinning**: ràng buộc kỹ thuật khiến virtual thread không thể rời carrier tại thời điểm đó. Pinning chỉ thực sự đáng lo khi sự chờ đợi kéo dài hoặc lặp lại với tần suất lớn.
- **Lock contention/deadlock**: các vấn đề phối hợp truy cập tài nguyên. Thay cách khóa có thể giải quyết carrier pinning nhưng không tự động giải quyết tranh chấp khóa, deadlock, giới hạn kết nối hay các điểm nghẽn khác.

Virtual thread không làm CPU chạy nhanh hơn và không loại bỏ chi phí của một I/O chậm; lợi ích chủ yếu là giảm lượng OS thread phải đứng chờ.

## 2. Hai nguồn pinning quan trọng ở Java 21

### 2.1. `synchronized` và monitor của JVM

Ở JDK 21, khi virtual thread đang thực thi mã trong một phương thức hoặc khối `synchronized`, nó bị pin vào carrier. Nếu bên trong vùng giữ monitor xảy ra thao tác chờ được hỗ trợ unmount ở điều kiện bình thường, việc unmount **không thể diễn ra**; carrier bị giữ trong thời gian chờ.

Ví dụ:

~~~java
private final Object guard = new Object();

void updateAndWait() throws InterruptedException {
    synchronized (guard) {
        // Việc cập nhật trạng thái nhỏ thường không đáng lo.
        Thread.sleep(200); // Trên Java 21: chờ khi đang giữ monitor -> carrier bị pin.
    }
}
~~~

Đây là **đoạn minh họa trong một lớp**, không phải chương trình chạy độc lập. `Thread.sleep` mô phỏng thao tác chờ; trong ứng dụng thực, một lời gọi I/O chậm bên trong vùng `synchronized` có thể gây tác động tương tự.

**Không nên kết luận rằng mọi `synchronized` đều xấu.** Một khối monitor chỉ cập nhật vài biến trong bộ nhớ và kết thúc rất nhanh ít khi tạo ra vấn đề đáng kể. Điều cần tìm là **blocking kéo dài hoặc xảy ra thường xuyên khi còn giữ monitor**. Phương thức `synchronized` có cùng vấn đề: phạm vi giữ monitor bao trùm thân phương thức, bao gồm cả các lời gọi gián tiếp có thể chặn.

Monitor vẫn đảm bảo các quy tắc mutual exclusion và happens-before thông thường. Pinning là đặc tính của cơ chế chạy virtual thread ở JDK 21, không làm thay đổi hợp đồng đồng bộ hóa hay tự nó tạo data race.

### 2.2. Native method và foreign function

JDK 21 cũng có thể pin virtual thread khi đang thực thi **native method** (ví dụ qua JNI) hoặc **foreign function** (giao diện gọi mã ngoài JVM). Khi mã native vẫn đang nằm trên stack, JVM không thể tùy ý tách continuation của virtual thread như với điểm chờ Java được hỗ trợ.

Ví dụ tình huống thực tế:

~~~text
Virtual thread
    └─ Java wrapper
         └─ JNI / foreign call
              └─ thư viện native đợi socket, khóa hoặc thiết bị 500 ms
                   └─ carrier bị chiếm trong thời gian chờ
~~~

Không phải cứ gọi một native method là ứng dụng gặp sự cố: một lời gọi native rất nhanh có thể gần như không đáng kể. Cần đo **thời lượng và tần suất chờ trong đoạn native**. Việc đổi monitor Java bên ngoài lời gọi JNI thành `ReentrantLock` không loại bỏ pinning do chính native frame.

Ở Java 21, Foreign Function & Memory API vẫn ở trạng thái **preview** (JEP 442); bài này chỉ xét bản chất tương tác native, không yêu cầu sử dụng API preview.

## 3. Vì sao pinning làm giảm khả năng mở rộng?

Giả sử bộ lập lịch có một số lượng carrier hữu hạn. Có nhiều virtual thread đang phục vụ các tác vụ I/O độc lập:

1. Nếu virtual thread chờ I/O và unmount thành công, carrier được sử dụng lại cho tác vụ khác; số tác vụ đang chờ có thể lớn hơn nhiều số OS thread.
2. Nếu mỗi tác vụ đi vào một monitor rồi chờ 200 ms khi bị pin, carrier phải chờ theo từng tác vụ.
3. Khi nhiều carrier cùng bị pin, các virtual thread khác có thể phải chờ đến lượt chạy, dù chúng không tranh chấp cùng một monitor hoặc tài nguyên.
4. Hệ quả có thể là giảm throughput, hàng đợi tăng, độ trễ ở phân vị cao tăng. Mức độ ảnh hưởng phụ thuộc tải, độ dài blocking, số carrier và các nút thắt khác.

**Lưu ý:** tăng số carrier hoặc thay đổi cấu hình scheduler không phải biện pháp chữa tận gốc. Tạo thêm OS thread có thể che giấu một phần triệu chứng và làm tăng tiêu thụ tài nguyên. Trước tiên hãy nhận diện đoạn blocking khi bị pin.

## 4. Ví dụ Java 21 chạy được: monitor so với `ReentrantLock`

Tạo tệp **`PinningJava21Demo.java`**:

~~~java
import java.util.concurrent.locks.ReentrantLock;

public class PinningJava21Demo {
    private static final Object MONITOR = new Object();
    private static final ReentrantLock LOCK = new ReentrantLock();

    public static void main(String[] args) throws InterruptedException {
        Thread monitorThread = Thread.ofVirtual()
                .name("monitor-demo")
                .start(() -> {
                    synchronized (MONITOR) {
                        pause(150);
                    }
                });
        monitorThread.join();

        Thread lockThread = Thread.ofVirtual()
                .name("reentrant-lock-demo")
                .start(() -> {
                    LOCK.lock();
                    try {
                        pause(150);
                    } finally {
                        LOCK.unlock();
                    }
                });
        lockThread.join();

        System.out.println("Hoàn tất hai ví dụ");
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tác vụ bị ngắt", ex);
        }
    }
}
~~~

Biên dịch và chạy với **JDK 21** (không cần `--enable-preview`):

~~~bash
javac PinningJava21Demo.java
java PinningJava21Demo
~~~

Đầu ra chuẩn của chương trình nếu không bị ngắt:

~~~text
Hoàn tất hai ví dụ
~~~

Giải thích:

- `Thread.ofVirtual().start(...)` tạo và khởi động virtual thread. `join()` giúp chương trình đợi mỗi đoạn demo hoàn tất.
- Đoạn thứ nhất giữ **intrinsic monitor** trong khi `Thread.sleep(150)`. Trên Java 21, đây là ví dụ minh họa pinning: thao tác ngủ không thể nhường carrier do đang ở trong vùng `synchronized`.
- Đoạn thứ hai giữ **`ReentrantLock`** trong khi ngủ. `Thread.sleep` có thể unmount virtual thread theo cơ chế bình thường vì việc giữ `ReentrantLock` tự nó không pin carrier như monitor ở Java 21.
- `finally` bảo đảm mở khóa ngay cả khi logic trong vùng khóa ném ngoại lệ. Nếu bỏ `unlock()` có thể gây kẹt khóa logic.
- Đây là **demo cơ chế**, không phải benchmark: hai tác vụ chạy lần lượt và có thể kết thúc với thời gian gần nhau. Khác biệt nằm ở carrier được giải phóng khi chờ, không phải ở số mili giây ngủ hay dòng stdout.

Thực tế, dùng `ReentrantLock` vẫn phải giữ tính đúng đắn của critical section; việc unmount virtual thread không có nghĩa là lock đã được thả. Các tác vụ khác cần chính lock đó vẫn phải đợi.

## 5. Quan sát pinning bằng công cụ JDK 21

### 5.1. Stack trace: `jdk.tracePinnedThreads`

Chạy trên JDK 21:

~~~bash
java -Djdk.tracePinnedThreads=full PinningJava21Demo
~~~

JDK có thể in stack trace khi virtual thread thực hiện thao tác blocking trong trạng thái pinned, làm nổi bật các frame native và frame giữ monitor. Cần xem các frame gần dòng `synchronized`/`pause` của demo; **không nên cố định chính xác stack trace**, vì tên frame, dòng nguồn và thứ tự có thể khác theo bản JDK.

Chế độ ngắn:

~~~bash
java -Djdk.tracePinnedThreads=short PinningJava21Demo
~~~

- `full`: nhiều frame, hữu ích để tìm đường gọi đầy đủ và điểm giữ monitor.
- `short`: thông tin ngắn hơn, hữu ích để sàng lọc nhanh.
- Thuộc tính này phục vụ chẩn đoán Java 21; không nên giả định hành vi hay khả năng hỗ trợ của nó hoàn toàn giống nhau trên mọi JDK đời sau.

**Quan trọng:** không thấy stack trace không chứng minh ứng dụng không có vấn đề hiệu năng. Một số tình huống không chạm điểm chờ, chỉ xảy ra dưới tải hoặc không được công cụ này ghi nhận theo cách bạn mong đợi.

### 5.2. Java Flight Recorder: `jdk.VirtualThreadPinned`

Thu thập JFR khi chạy demo:

~~~bash
java -XX:StartFlightRecording=filename=pinning.jfr,dumponexit=true PinningJava21Demo
jfr print --events jdk.VirtualThreadPinned pinning.jfr
~~~

Trong JDK 21, sự kiện `jdk.VirtualThreadPinned` được bật mặc định với **ngưỡng thời lượng 20 ms** trong cấu hình ghi thông dụng. Nó ghi nhận một virtual thread bị pin lâu hơn ngưỡng trong thao tác chờ. Với đoạn ngủ 150 ms bên trong monitor, demo được thiết kế để vượt ngưỡng này; sự xuất hiện cụ thể của sự kiện còn tùy môi trường ghi nhận và cấu hình JFR.

Khi đọc sự kiện, quan tâm đến thời lượng, thời điểm, virtual thread liên quan và stack trace. Đối chiếu stack trace với vị trí giữ monitor hoặc lời gọi native. Có thể lọc và phân tích JFR bằng JDK Mission Control khi cần xem toàn bộ mẫu tải.

Nếu không có sự kiện, hãy kiểm tra recording thực sự được tạo, cấu hình sự kiện/ngưỡng và workload có đi qua nhánh blocking hay không. Đừng diễn giải một lần chạy ngắn, ít tải là bằng chứng hệ thống không bao giờ pin.

### 5.3. Đo tác động, không chỉ đếm sự kiện

Trong môi trường kiểm thử mô phỏng lưu lượng thực, nên đánh giá đồng thời:

- **Tần suất/thời lượng pinning**: sự kiện xảy ra ở đâu, có lặp thường xuyên và dài không.
- **Khả năng phục vụ**: throughput, độ trễ trung bình, p95/p99 và các đợt tăng bất thường.
- **Carrier và hàng đợi**: có nhiều tác vụ chờ cơ hội chạy khi số carrier hữu hạn không.
- **Nút thắt độc lập**: DB connection pool, giới hạn socket/file descriptor, khóa chung, downstream API chậm hoặc CPU bão hòa.

Chỉ sửa chỗ pinning thật sự ảnh hưởng thay vì làm một cuộc thay thế `synchronized` trên toàn dự án.

## 6. Cách khắc phục đúng theo nguyên nhân

### Cách 1: Chuyển I/O ra ngoài vùng `synchronized` khi giữ được bất biến

Ví dụ ý tưởng trước–sau (đây là **mã minh họa**, cần thích nghi với tính nhất quán của trạng thái trong ứng dụng cụ thể):

~~~java
// Trước: I/O chậm nằm trong monitor.
synchronized (guard) {
    updateState();
    sendSlowRequest();
}

// Sau: chỉ bảo vệ thao tác trên state, thực hiện I/O ở ngoài.
synchronized (guard) {
    updateState();
}
sendSlowRequest();
~~~

Cách sau giảm thời gian giữ monitor và tránh pinning do monitor trong lúc I/O, nhưng **chỉ hợp lệ nếu quy tắc nghiệp vụ không yêu cầu update và send phải nguyên tử với nhau**. Nếu cần bảo đảm giao dịch, thứ tự, snapshot dữ liệu hoặc chống race, hãy thiết kế cơ chế phù hợp (snapshot, queue, state machine, v.v.); không tự ý di chuyển lời gọi khỏi critical section.

### Cách 2: Thay monitor cần bao bọc blocking bằng `ReentrantLock`

~~~java
// Minh họa bên trong một lớp có field lock là ReentrantLock.
lock.lock();
try {
    blockingOperation();
} finally {
    lock.unlock();
}
~~~

`ReentrantLock` cho phép virtual thread chờ ở một số điểm blocking mà không bị pin bởi **intrinsic monitor**; đây là biện pháp được tài liệu Java 21 khuyến nghị cho vùng khóa có blocking dài và thường xuyên.

Nhưng cần rà soát hợp đồng trước khi đổi:

- `synchronized` tự nhả monitor khi thoát scope; `ReentrantLock` cần `unlock()` trong `finally`.
- `wait()/notify()/notifyAll()` gắn với intrinsic monitor; không thể sao chép nguyên xi. Với lock tường minh, cơ chế chờ/tín hiệu tương ứng là `Condition` và phải thiết kế lại điều kiện chờ đúng.
- Khóa reentrant không giải quyết tình trạng tranh chấp cùng tài nguyên: nhiều virtual thread cùng cần một lock vẫn phải xếp hàng.
- Không giữ lock qua I/O nếu có thể tổ chức lại công việc an toàn và đơn giản hơn.

### Cách 3: Đối với native/JNI/foreign calls, xử lý nguyên nhân trong lời gọi ngoài JVM

Nếu thư viện native thường xuyên chặn lâu:

1. Đo thời gian trong lời gọi native và tìm phiên bản API không blocking/asynchronous nếu có.
2. Tránh giữ thêm các monitor Java qua lời gọi khi không cần.
3. Cân nhắc chuyển công việc native blocking sang **một nhóm platform thread có giới hạn** hoặc một thành phần chuyên trách có kiểm soát tải, nếu chưa thể thay đổi API. Cách này cô lập nhu cầu OS thread, **không biến lời gọi native thành non-blocking**.
4. Giới hạn số tác vụ đồng thời theo năng lực native resource; xử lý timeout, cancellation và backpressure theo hợp đồng API.

Thay `synchronized` thành `ReentrantLock` ở một wrapper **không tự loại bỏ native pinning**. Tương tự, tăng số lượng virtual thread không làm thư viện native có thêm tài nguyên.

### Cách 4: Cân nhắc phiên bản JDK khi có điều kiện

**JDK 21–23** có giới hạn pinning liên quan đến monitor như mô tả ở trên. **JDK 24** đưa vào **JEP 491 — Synchronize Virtual Threads without Pinning**: virtual thread có thể nhả carrier khi chặn trong nhiều tình huống `synchronized` mà JDK 21 phải pin.

Điều này **không xóa mọi nguồn pinning**: tương tác native/foreign và các tình huống JVM khác vẫn phải được kiểm tra. Khi ứng dụng thực tế phải chạy trên **Java 21**, phải chẩn đoán và xử lý theo hành vi Java 21; không áp dụng ngược kỳ vọng của JDK 24.

## 7. Những hiểu lầm và lỗi thường gặp

| Nhận định sai | Cách hiểu đúng ở Java 21 |
|---|---|
| “Dùng `synchronized` là virtual threads bị hỏng.” | Monitor vẫn đúng về đồng bộ; điều đáng lo là virtual thread block **lâu/thường xuyên** khi pinned. |
| “Bất cứ lệnh blocking nào cũng pin.” | Nhiều thao tác blocking được JDK hỗ trợ unmount virtual thread; pinning phụ thuộc ngữ cảnh monitor/native. |
| “`ReentrantLock` làm tất cả các thread dùng khóa chạy song song.” | Lock vẫn loại trừ lẫn nhau. Chỉ carrier có thể được giải phóng khi virtual thread block ở điểm hỗ trợ. |
| “Đổi wrapper Java sang `ReentrantLock` sửa được pinning của JNI.” | Native frame còn tồn tại có thể tiếp tục pin; phải xem lời gọi native và cách thực thi của nó. |
| “Không có `jdk.VirtualThreadPinned` tức là không có pinning.” | Event có ngưỡng, cấu hình và phụ thuộc tải; vắng event không đồng nghĩa không có mọi pinning. |
| “Tăng số virtual thread là tự động tăng throughput.” | Carrier, CPU, khóa, connection pool và downstream vẫn là giới hạn thực; cần đo dưới tải. |
| “Java 24 hết mọi vấn đề carrier pinning.” | JEP 491 xử lý pinning do monitor; vẫn còn các trường hợp khác, nhất là native/foreign. |

## 8. Quy trình kiểm tra một hệ thống Java 21

1. **Xác nhận runtime thực tế** bằng `java -version`. Không diễn giải hành vi Java 21 từ kết quả chạy trên JDK 24+.
2. **Tìm vị trí nghi ngờ**: `synchronized` methods/blocks bao quanh socket/DB/file I/O, sleep hoặc lời gọi native có thể chặn.
3. **Tái hiện bằng workload thực tế**; bật JFR và, khi cần, `-Djdk.tracePinnedThreads=full` để liên kết sự kiện với stack.
4. **Ưu tiên theo tác động**: số lần pin, thời gian giữ carrier và ảnh hưởng tới p95/p99/throughput.
5. **Sửa nhỏ nhất mà vẫn đúng semantics**: thu gọn phạm vi monitor, chuyển I/O ra ngoài khi an toàn, dùng `ReentrantLock` đúng cách hoặc cô lập native blocking.
6. **Đo lại và kiểm tra tính đúng đắn đồng thời**: dữ liệu chia sẻ, thứ tự thao tác, race conditions, thời gian chờ, timeout, giới hạn tài nguyên. Chỉ kết luận cải thiện khi số liệu sau thay đổi xác nhận.

## 9. Ghi nhớ

**Pinning không phải deadlock và cũng không phải lỗi của `Thread.sleep` hay `synchronized` nói riêng.** Trên Java 21, nó là hệ quả của việc virtual thread không thể unmount khi nằm trong một số ngữ cảnh giữ monitor hoặc native frame. Nếu ngữ cảnh đó còn bao quanh một điểm blocking dài, carrier bị giữ chờ, làm mất lợi thế mở rộng số tác vụ I/O của virtual threads.

Tập trung vào **blocking trong vùng có pinning**, chứng minh bằng JFR/trace, và sửa theo ràng buộc đồng bộ hóa của chương trình. Các vùng `synchronized` ngắn, thuần in-memory thường có thể giữ nguyên.

## Tài liệu tham khảo

- [JEP 444 — Virtual Threads, chuẩn hóa trong JDK 21](https://openjdk.org/jeps/444).
- [Oracle Java SE 21 — Virtual Threads: scheduling, pinning và diagnostics](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html).
- [OpenJDK JEP 491 — Synchronize Virtual Threads without Pinning (JDK 24)](https://openjdk.org/jeps/491).
- [Dev.java — Virtual Threads, giới hạn pinning theo phiên bản JDK](https://dev.java/learn/new-features/virtual-threads/).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-010">ThreadLocal và chi phí dữ liệu theo từng virtual thread</a>

<details>
<summary>Xem chi tiết</summary>

# ThreadLocal và chi phí dữ liệu theo từng virtual thread

## 1. Vấn đề đặt ra

Từ Java 21, **virtual threads** (JEP 444) cho phép Java chạy rất nhiều tác vụ đồng thời, đặc biệt khi tác vụ chờ I/O. Mỗi virtual thread vẫn là một `java.lang.Thread`; JVM có thể tạm dừng nó, trả platform thread đang chạy nó (thường gọi là *carrier*) về scheduler, rồi tiếp tục virtual thread trên carrier khác.

`ThreadLocal<T>` vẫn được hỗ trợ đầy đủ: **mỗi virtual thread có giá trị riêng cho từng khóa ThreadLocal**. Khi virtual thread thay carrier, giá trị không tự mất, không chuyển sang virtual thread khác và cũng không thuộc về carrier. Vấn đề nằm ở **quy mô dữ liệu**: số virtual threads sống đồng thời có thể lớn hơn rất nhiều so với số platform threads truyền thống. Cache hoặc trạng thái gắn với từng thread có thể trở thành nguồn tiêu thụ heap và cấp phát lớn.

Ví dụ, ứng dụng cũ có 32 platform threads trong pool, mỗi thread giữ một buffer 64 KiB qua `ThreadLocal`. Tổng payload ước lượng 2 MiB khi cả 32 thread đều khởi tạo. Chuyển cùng logic sang 10.000 virtual threads đang sống, mỗi thread tự tạo buffer 64 KiB, payload riêng đã khoảng **625 MiB**, chưa tính metadata, object header, stack, tham chiếu và những object khác. Đây là ước lượng dung lượng, **không phải benchmark**.

Mục tiêu: hiểu `ThreadLocal` trên virtual threads, phân biệt chi phí bộ nhớ với khả năng lập lịch, tổ chức vòng đời dữ liệu hợp lý và lựa chọn API thay thế khi cần.

## 2. ThreadLocal lưu gì và lưu ở đâu?

Một đối tượng `ThreadLocal<T>` là **khóa** giúp thread hiện tại truy cập giá trị thuộc riêng nó; khóa dùng chung không có nghĩa value dùng chung. Các phương thức:

| API | Hành vi |
| --- | --- |
| `new ThreadLocal<>()` | Giá trị ban đầu của thread chưa đặt là `null`. |
| `ThreadLocal.withInitial(supplier)` | Tạo giá trị khi thread gọi `get()` lần đầu; sau `remove()`, lần `get()` tiếp theo có thể khởi tạo lại. |
| `get()` | Trả giá trị của thread **đang thực thi**. |
| `set(value)` | Gắn value vào thread hiện tại, không cập nhật thread khác. |
| `remove()` | Xóa liên kết của thread hiện tại với khóa. |

Trong JDK, các liên kết này được quản lý bằng cấu trúc dạng map gắn với đối tượng `Thread`. Trên virtual thread, map đó thuộc **virtual thread**, không thuộc carrier. Chi phí dữ liệu là theo những ThreadLocal **thực sự được sử dụng**, không có nghĩa cứ tạo một virtual thread là ngay lập tức cấp phát buffer của mọi `ThreadLocal.withInitial` khai báo trong chương trình.

Diễn tiến minh họa:

1. Virtual thread V1 gọi `TRACE_ID.set("A")`.
2. V1 chờ I/O và tạm dừng; carrier có thể chuyển sang chạy V2.
3. V2 gọi `TRACE_ID.get()`: nó đọc giá trị riêng của V2, không được tự động thấy `"A"`.
4. V1 tiếp tục chạy trên một carrier khác: `TRACE_ID.get()` vẫn trả `"A"`, cho đến khi V1 thay đổi hoặc xóa giá trị.

**Giới hạn:** `ThreadLocal` bảo vệ tính tách biệt của ánh xạ từng thread, không khiến một object mutable được chia sẻ theo cách khác trở nên thread-safe. Nếu hai ThreadLocal values cùng trỏ một object, mã sửa object đó vẫn phải đồng bộ khi cần.

## 3. Ví dụ Java 21+: dữ liệu tách biệt và sống qua lời gọi hàm

Lưu thành `VirtualThreadLocalDemo.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VirtualThreadLocalDemo {
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    static String handle(String requestId) {
        REQUEST_ID.set(requestId);
        try {
            return "virtual=" + Thread.currentThread().isVirtual()
                    + ", id=" + readFromNestedMethod();
        } finally {
            REQUEST_ID.remove();
        }
    }

    static String readFromNestedMethod() {
        return REQUEST_ID.get();
    }

    public static void main(String[] args) throws Exception {
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> first = executor.submit(() -> handle("REQ-101"));
            Future<String> second = executor.submit(() -> handle("REQ-202"));

            System.out.println(first.get());
            System.out.println(second.get());
        }
        System.out.println("Caller=" + REQUEST_ID.get());
    }
}
```

Biên dịch và chạy bằng JDK 21 trở lên, không cần cờ preview:

```bash
javac -encoding UTF-8 VirtualThreadLocalDemo.java
java VirtualThreadLocalDemo
```

Output chính xác:

```text
virtual=true, id=REQ-101
virtual=true, id=REQ-202
Caller=null
```

`Executors.newVirtualThreadPerTaskExecutor()` tạo một virtual thread mới cho mỗi task. `Future.get()` được đọc tuần tự nên thứ tự hai dòng đầu xác định. `readFromNestedMethod()` nhận context ngầm qua `ThreadLocal` trong cùng virtual thread; main thread không có giá trị đó. Khối `finally` bảo đảm dọn giá trị cả khi xử lý lỗi.

Lưu ý: `REQUEST_ID.remove()` chỉ xóa giá trị ở **thread gọi nó**. Gọi `remove()` ở main thread không thể dọn ThreadLocal của những worker khác.

## 4. Chi phí bộ nhớ và vòng đời trong thực tế

Có ba nhóm chi phí:

- **Bản thân virtual thread:** object, trạng thái thực thi, continuation/stack và tham chiếu khác mà JVM cần quản lý.
- **Cấu trúc lưu ThreadLocal:** metadata và entry cho các khóa được sử dụng.
- **Value và đồ thị object được giữ:** String, byte array, buffer, context, cache, tài nguyên hoặc những object được value trỏ tới.

Có thể hình dung:

```text
Tổng dữ liệu ThreadLocal còn sống ≈
  tổng giá trị và metadata còn được giữ bởi các virtual threads còn sống
```

Tính chất của object sharing và garbage collection khiến đây không phải một công thức đo heap chính xác. Nếu hai value cùng trỏ một immutable object, không tính double dung lượng object đó. Ngược lại, một value rất nhỏ có thể tham chiếu đến một đồ thị object lớn.

**Hai con số phải phân biệt:** số virtual threads *đã tạo tổng cộng* và số thread *đang còn sống cùng thời điểm*. Một triệu tác vụ ngắn chạy theo đợt có thể tạo áp lực **allocation/GC** lớn nhưng không nhất thiết giữ dữ liệu của cả triệu thread trong heap cùng lúc. Ngược lại, hàng chục nghìn virtual threads đang đợi mạng hoặc bị giữ bởi các tham chiếu khác có thể giữ dữ liệu ThreadLocal lâu hơn dự kiến.

### Cache theo thread không còn là cache theo worker

Đây là tình huống dễ mắc nhất khi chuyển từ platform-thread pool sang virtual-thread-per-task.

- Pool 16 platform threads: một cache `ThreadLocal.withInitial` có thể được tái sử dụng qua rất nhiều task chạy lần lượt trên cùng worker.
- Virtual thread cho từng task: mỗi task là một thread khác; cache lần đầu chỉ được dùng lại **trong chính task đó**, sau đó có thể bị loại bỏ cùng thread.
- Nếu một triệu task đều tạo cache 64 KiB, số **lượt cấp phát** và băng thông bộ nhớ rất lớn ngay cả khi không phải một triệu cache tồn tại đồng thời.

Không giải quyết bằng cách tạo pool virtual threads để tái sử dụng buffer. Mô hình khuyến nghị là một virtual thread cho một tác vụ; nếu cần pool tài nguyên đắt tiền, hãy tạo **pool tài nguyên có giới hạn riêng**, độc lập với danh tính thread.

## 5. Ví dụ Java 21+: quan sát chi phí khởi tạo per-task

Lưu thành `VirtualThreadLocalCostDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualThreadLocalCostDemo {
    private static final AtomicInteger CREATED = new AtomicInteger();

    private static final ThreadLocal<byte[]> BUFFER =
            ThreadLocal.withInitial(() -> {
                CREATED.incrementAndGet();
                return new byte[64 * 1024];
            });

    private static boolean task() {
        try {
            byte[] first = BUFFER.get();
            byte[] second = BUFFER.get();
            return first == second;
        } finally {
            BUFFER.remove();
        }
    }

    public static void main(String[] args) throws Exception {
        List<Future<Boolean>> results = new ArrayList<>();

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 4; i++) {
                results.add(executor.submit(
                        VirtualThreadLocalCostDemo::task));
            }
            for (Future<Boolean> result : results) {
                if (!result.get()) {
                    throw new IllegalStateException("Not the same buffer");
                }
            }
        }
        System.out.println("Buffers created: " + CREATED.get());
    }
}
```

```bash
javac -encoding UTF-8 VirtualThreadLocalCostDemo.java
java VirtualThreadLocalCostDemo
```

```text
Buffers created: 4
```

Mỗi virtual thread gọi `get()` hai lần và nhận **cùng một buffer trong thread đó**. Tuy nhiên bốn task tương ứng bốn virtual threads; supplier chạy bốn lần. `AtomicInteger` giúp đếm chính xác trong môi trường đa luồng. Sau `remove()`, nếu thread đó tiếp tục gọi `get()` thì supplier có thể chạy lại và cấp phát một buffer mới.

Bài này dùng số tác vụ nhỏ để chứng minh cơ chế. Đừng tùy tiện tăng bài demo tới hàng trăm nghìn task đồng thời với buffer lớn trên máy có heap hạn chế.

## 6. Cleanup đúng và rủi ro giữ tham chiếu

Một quy tắc hữu ích với context theo tác vụ là **đặt, dùng, dọn trong `finally`**. Đoạn sau là minh họa phương thức nằm trong lớp, không phải chương trình độc lập:

```java
private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

static void process(String traceId) {
    TRACE_ID.set(traceId);
    try {
        doWork();
    } finally {
        TRACE_ID.remove();
    }
}
```

Nếu có context lồng nhau cần quay lại giá trị cũ sau khi xử lý nhánh con, chỉ `remove()` là chưa đủ; phải thiết kế lưu/khôi phục state cha đúng semantics. Tránh để thư viện nằm sâu trong call chain tùy ý thay đổi giá trị được kỳ vọng là bất biến.

**ThreadLocal có gây memory leak vĩnh viễn không?** Không phải cứ thiếu `remove()` trong virtual thread là leak vĩnh viễn. Khi virtual thread đã kết thúc và không còn được giữ tham chiếu, dữ liệu đi cùng nó có thể được GC. Tuy nhiên, nếu thread còn sống lâu (kể cả đang chờ I/O) thì value vẫn có thể được giữ; code chạy trên platform-thread pool cũng có thể giữ state qua nhiều request. Vì vậy cleanup có chủ đích giúp kiểm soát vòng đời và tránh rò dữ liệu giữa task khi code được sử dụng trong nhiều môi trường.

Trong cách triển khai ThreadLocalMap của JDK, key là tham chiếu yếu và value thường là tham chiếu mạnh. Nếu key không còn được tham chiếu ở nơi khác, entry cũ không nhất thiết được dọn ngay; dựa hoàn toàn vào việc GC của key để giải phóng value là thiếu an toàn.

**Đừng nhầm `remove()` với đóng tài nguyên.** Nếu value chứa socket, kết nối hoặc resource `AutoCloseable`, `remove()` chỉ bỏ liên kết; code sở hữu tài nguyên vẫn phải đóng nó (`close()`/`try-with-resources`). Không đưa toàn bộ body dữ liệu hay buffer quá lớn vào ThreadLocal chỉ để tránh truyền tham số.

## 7. ThreadLocal và InheritableThreadLocal khác nhau

`ThreadLocal` thông thường không tự truyền value từ parent thread sang thread mới. `InheritableThreadLocal` hỗ trợ kế thừa **giá trị khởi đầu khi tạo thread**. Trong Java 21, `Thread.ofVirtual()` mặc định kế thừa `InheritableThreadLocal`; muốn tắt có thể gọi `inheritInheritableThreadLocals(false)`.

Lưu thành `VirtualInheritanceDemo.java`:

```java
public class VirtualInheritanceDemo {
    private static final InheritableThreadLocal<String> TRACE =
            new InheritableThreadLocal<>();

    public static void main(String[] args) throws InterruptedException {
        TRACE.set("parent");
        try {
            Thread inheriting = Thread.ofVirtual().start(
                    () -> System.out.println("Inherited: " + TRACE.get()));
            inheriting.join();

            Thread isolated = Thread.ofVirtual()
                    .inheritInheritableThreadLocals(false)
                    .start(() -> System.out.println("Isolated : " + TRACE.get()));
            isolated.join();
        } finally {
            TRACE.remove();
        }
    }
}
```

```bash
javac -encoding UTF-8 VirtualInheritanceDemo.java
java VirtualInheritanceDemo
```

```text
Inherited: parent
Isolated : null
```

Điều cần nhớ:

- Giá trị được kế thừa khi thread mới được tạo, **không đồng bộ liên tục** mỗi khi thread cha gọi `set()`.
- Với mutable object, mặc định child có thể nhận cùng tham chiếu với parent; hai thread sửa object đó có thể gây race. Đây không phải deep copy.
- Sao chép các ánh xạ inheritable vào nhiều virtual threads có chi phí, nhất là khi parent có nhiều entries.
- Với executor, cần biết thread **thực sự tạo virtual thread** là thread nào và tạo lúc nào; đừng mặc định mọi context ở nơi gọi `submit()` đều được tự động truyền đúng trong mọi executor và cơ chế async.

Không dùng `InheritableThreadLocal` như một phương án truyền context tổng quát qua mọi async boundary. Hãy xác định rõ ai tạo thread, ai sở hữu value, và value có được phép chia sẻ hay không.

## 8. Chọn phương án khác khi cần truyền context hoặc tái sử dụng tài nguyên

| Mục đích | Cách phù hợp để cân nhắc | Lưu ý |
| --- | --- | --- |
| Chỉ vài phương thức cần một giá trị | Truyền tham số tường minh hoặc record immutable | Dễ biết ownership, lifetime, dễ test. |
| Thư viện Java 21 hiện hữu dựa vào context cục bộ | `ThreadLocal` với `finally/remove` | Tương thích và tiện nhưng phải kiểm soát kích thước value. |
| Ngữ cảnh chỉ đọc truyền xuyên các phương thức | `ScopedValue` từ Java 25 | Lifetime theo lexical scope, giảm trạng thái ẩn có thể thay đổi. |
| Tài nguyên dùng chung và đắt tiền | Pool tài nguyên bounded, service shared với thread safety rõ ràng | Dùng chung thực sự giữa task; quản lý cấp phát và đóng. |
| Giới hạn số tác vụ dùng tài nguyên | `Semaphore` hoặc capacity limit | Không cần pool virtual threads để giới hạn DB/network. |

### ScopedValue: phân biệt phiên bản Java

`ScopedValue` có mặt ở dạng **preview trong Java 21–24**; API preview có thể thay đổi giữa các bản và yêu cầu bật preview. Nó trở thành API chuẩn từ **Java 25** (JEP 506). Do đó các ví dụ runnable bên trên dùng Java 21+ không cần preview; đoạn sau chỉ minh họa một phần của lớp trên **JDK 25+**, không phải tệp độc lập Java 21:

```java
private static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

static void dispatch() {
    ScopedValue.where(TRACE_ID, "REQ-7")
            .run(() -> System.out.println(TRACE_ID.get()));
}
```

Giá trị ràng buộc tồn tại trong vùng `run` và được kết thúc tự động khi ra khỏi vùng. Đây là lựa chọn rất phù hợp cho ngữ cảnh chỉ đọc. `ScopedValue` không thể thay thế một cách máy móc các use case cần thay đổi tùy ý state cục bộ giữa quá trình thực thi; lúc đó phải thiết kế API và state theo nhu cầu thực.

## 9. Sai lầm thường gặp

**“Giá trị ThreadLocal chạy theo carrier.”** Sai. Nó gắn với virtual thread; scheduler thay carrier không làm các virtual threads dùng chung value.

**“Có một triệu virtual threads nghĩa là có một triệu bản sao của mọi ThreadLocal.”** Sai. Values thường được khởi tạo theo nhu cầu; lượng dữ liệu phụ thuộc những ThreadLocal được truy cập và số thread còn sống. Nhưng nếu mọi task đều `get()` cache lớn, chi phí thực sự rất cao.

**“static final ThreadLocal đồng nghĩa static singleton value.”** Sai. Đối tượng khóa là static, còn mapping value vẫn độc lập cho từng thread.

**“withInitial chỉ chạy một lần.”** Sai. Nó chạy theo thread ở lần khởi tạo cần thiết và có thể chạy lại sau `remove()`.

**“ThreadLocal làm dữ liệu mutable bên trong an toàn.”** Sai. Nếu object được chia sẻ bằng một con đường khác hoặc qua `InheritableThreadLocal`, cần kiểm soát data race riêng.

**“remove sẽ đóng kết nối và trả tài nguyên về pool.”** Sai. `remove()` không `close()` value và không quản lý lifecycle của pool.

**“Giữ ThreadLocal cache và pool virtual threads để tái sử dụng.”** Không phù hợp với mô hình virtual-thread-per-task; quản lý cache hoặc tài nguyên dùng chung ở lớp abstraction khác.

## 10. Đo đạc và ra quyết định

Trước khi đưa `ThreadLocal` vào workload có nhiều virtual threads:

1. Đếm số virtual threads **còn sống đồng thời**, không chỉ số task đã xử lý.
2. Đo kích thước value và những object giữ qua tham chiếu; phân biệt object chia sẻ và object tạo riêng từng task.
3. Theo dõi allocation rate, heap live set, GC pauses, p95/p99 latency, đặc biệt lúc tải cao và các task chờ I/O lâu.
4. Dùng JFR, Java heap profiler hoặc một bộ đếm khởi tạo có kiểm soát để phát hiện cache được tạo mới theo từng task.
5. Kiểm tra giá trị có bị giữ quá thời gian cần thiết, có cleanup đúng khi exception hay không.
6. Tách bạch mục đích **truyền ngữ cảnh** với mục đích **tái sử dụng tài nguyên**. Trường hợp thứ hai thường nên có pool/capacity độc lập.
7. Nếu context chỉ đọc và dự án đã ở Java 25+, cân nhắc `ScopedValue`. Với dự án Java 21, cân nhắc tham số tường minh hoặc `ThreadLocal` có scope rõ ràng.

Virtual threads cải thiện khả năng mở rộng của code blocking I/O, không biến phép tính CPU-bound thành nhanh hơn, và cũng không gỡ bỏ giới hạn của database/network phía sau.

## 11. Câu hỏi tự kiểm tra

**Hỏi:** V1 tạm dừng, V2 được chạy trên cùng carrier. V2 có thấy `ThreadLocal` của V1 không?  
**Đáp:** Không, vì ThreadLocal thuộc từng virtual thread.

**Hỏi:** 20.000 virtual threads mỗi thread giữ 128 KiB có ảnh hưởng gì?  
**Đáp:** Payload có thể lên tới hàng GiB nếu tất cả cùng sống và giữ giá trị; còn phải tính metadata, object khác và áp lực GC.

**Hỏi:** Virtual thread kết thúc nhưng không gọi `remove()` có nghĩa chắc chắn memory leak vĩnh viễn?  
**Đáp:** Không. Thread và dữ liệu không còn được giữ tham chiếu có thể được thu gom. Vấn đề là thời gian dữ liệu sống và số thread còn hoạt động, cùng khả năng code được dùng trên thread lâu sống.

**Hỏi:** `InheritableThreadLocal` có sao chép sâu một `ArrayList` mutable từ cha sang con?  
**Đáp:** Không theo mặc định; child có thể chia sẻ tham chiếu, cần thiết kế tránh race.

**Hỏi:** Trong Java 21, có thể dùng trực tiếp `ScopedValue` như API ổn định không?  
**Đáp:** Chưa. Java 21–24 là preview; Java 25 trở đi mới có API chuẩn.

**Ghi nhớ:** `ThreadLocal` vẫn hoạt động trên virtual threads, nhưng mỗi virtual thread có dữ liệu cục bộ của riêng nó. Khi tăng số task đồng thời, hãy đặc biệt thận trọng với **cache theo thread, buffer lớn, khả năng kế thừa context và vòng đời cleanup**.

### Tài liệu tham khảo

- [OpenJDK JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Java 21 API: ThreadLocal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html)
- [Java 21 API: Thread.Builder.OfVirtual](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.Builder.OfVirtual.html)
- [OpenJDK JEP 506: Scoped Values](https://openjdk.org/jeps/506)
- [Java 25 API: ScopedValue](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ScopedValue.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-011">CPU-bound workload không tự nhanh hơn nhờ virtual threads</a>

<details>
<summary>Xem chi tiết</summary>

# CPU-bound workload không tự nhanh hơn nhờ virtual threads

## Mục tiêu bài học

Sau bài này, bạn có thể:

- Giải thích vì sao **virtual thread** tiết kiệm chi phí giữ thread khi chờ nhưng **không làm một phép tính CPU chạy nhanh hơn**.
- Phân biệt **concurrency** (nhiều công việc cùng tồn tại và tiến triển) với **parallelism** (thực sự tính toán đồng thời trên nhiều bộ xử lý).
- Hiểu quan hệ giữa virtual threads, carrier/platform threads, CPU cores, hàng đợi công việc, mức song song và throughput.
- Chọn chiến lược hợp lý cho tác vụ thuần CPU, tác vụ thường xuyên chờ I/O và workload pha trộn.
- Viết ví dụ Java 21+ để kiểm tra kết quả, đo hiệu năng cẩn thận và nhận diện các bẫy benchmark.

Bài học chỉ sử dụng Java SE và các khái niệm concurrency của JVM.

## 1. Virtual threads giải quyết vấn đề gì?

Java 21 đưa **virtual threads** thành tính năng chính thức. Chúng vẫn là `java.lang.Thread`, nhưng được JVM quản lý nhẹ hơn và không sở hữu một OS thread cố định trong toàn bộ vòng đời. Một platform thread thường gắn với một OS thread. Khi virtual thread thực hiện thao tác chờ tương thích với cơ chế của JVM, nó có thể **unmount** khỏi carrier thread; carrier được dùng để chạy virtual thread khác.

Khái niệm thiết yếu:

| Thuật ngữ | Ý nghĩa |
| --- | --- |
| **Virtual thread** | Luồng nhẹ do JVM lập lịch, phù hợp việc có nhiều tác vụ độc lập thường bị chặn để chờ. |
| **Carrier thread** | Platform thread hiện đang thực thi mã Java của một virtual thread khi virtual thread được mount. |
| **Mount / unmount** | Gắn virtual thread lên carrier để chạy / tạm tách ra trong những thao tác chờ được hỗ trợ. |
| **CPU-bound** | Phần lớn thời gian thực sự tiêu thụ CPU để tính toán, ví dụ tính hash hàng loạt, biến đổi dữ liệu, nén, thuật toán số. |
| **I/O-bound / blocking-heavy** | Phần lớn thời gian chờ socket, cơ sở dữ liệu, tệp hoặc thao tác chặn khác; CPU không tính toán liên tục trong lúc đợi. |
| **Throughput** | Số tác vụ hoàn thành trên một đơn vị thời gian. |
| **Latency** | Thời gian một tác vụ mất từ lúc bắt đầu yêu cầu đến khi hoàn tất; có thể bao gồm cả thời gian xếp hàng. |

**Virtual threads là công cụ mở rộng khả năng phục vụ nhiều tác vụ đang chờ; chúng không phải phiên bản chạy phép cộng, phép nhân, mã hóa hoặc tính toán nhanh hơn của platform threads.** Khi một virtual thread bận thực hiện vòng lặp tính toán, nó vẫn phải chạy trên một carrier, và carrier vẫn dùng một luồng CPU thực tế.

### Concurrency khác parallelism

Giả sử máy có 4 logical processors:

~~~text
100 virtual threads CPU-bound được tạo ra
        |
        v
JVM scheduler sắp lịch chạy trên các carrier
        |
        v
CPU 0 | CPU 1 | CPU 2 | CPU 3
        |
        v
Chỉ một số công việc có thể thực thi CPU thực sự tại cùng một thời điểm.
Những công việc còn lại phải chờ đến lượt hoặc chia sẻ CPU.
~~~

Hình trên minh họa giới hạn năng lực CPU; số carrier thực tế và cách JVM lập lịch phụ thuộc phiên bản JVM, cấu hình scheduler, môi trường container và hành vi blocking. `Runtime.getRuntime().availableProcessors()` cung cấp số logical processors mà JVM thấy; nó không phải bảo đảm số phép tính luôn chạy song song hiệu quả.

- **Concurrency:** có 100 tác vụ được quản lý cùng lúc.
- **Parallelism:** nếu chỉ có 4 logical processors sẵn có, không thể biến 100 tác vụ thuần CPU thành 100 phép tính thực sự diễn ra đồng thời chỉ nhờ đổi loại thread.
- **I/O waiting:** khi 96 tác vụ đang chờ I/O, một số virtual threads khác có thể tiếp tục chạy bằng carrier được giải phóng. Đây là lợi ích rất khác với gia tốc phép tính.

Câu hỏi cần đặt là **“tác vụ đang tốn thời gian tính toán hay đang tốn thời gian chờ?”**, chứ không đơn thuần “số thread hiện có là bao nhiêu?”.

## 2. Tại sao CPU-bound không tự tăng tốc?

### 2.1. Vẫn chịu giới hạn tài nguyên tính toán

Một phép tính CPU thuần phải thực hiện số lượng phép toán nhất định. Virtual thread và platform thread đều sử dụng cùng CPU, cache, băng thông bộ nhớ và các đơn vị thực thi. Chuyển từ:

~~~java
executor.submit(this::calculate);
~~~

sang:

~~~java
Thread.startVirtualThread(this::calculate);
~~~

không thay đổi độ phức tạp thuật toán của `calculate()`, không tăng số lõi CPU và không tự động vector hóa phép tính. Cả hai đoạn là mã minh họa; `executor` và `calculate()` phải được khai báo trong chương trình thực tế.

Nếu chỉ có **một** tác vụ CPU-bound chạy trên một logical processor, việc tạo thêm virtual threads không làm **chính tác vụ đó** chia thành nhiều phần độc lập. Muốn tận dụng nhiều lõi, cần chia bài toán theo dữ liệu hoặc tác vụ, xử lý đồng thời và kết hợp kết quả đúng cách.

### 2.2. Virtual thread không phải CPU worker “miễn phí”

Khi một virtual thread tính toán mà không chặn, nó giữ carrier suốt đoạn chạy đó (cho đến khi hoàn tất hoặc tới điểm chuyển trạng thái phù hợp). Các virtual threads khác cần CPU vẫn phải được lập lịch. Tạo hàng nghìn virtual threads để tính toán đồng thời:

- Không tăng tổng năng lực CPU vật lý.
- Có thể thêm chi phí tạo task, chuyển ngữ cảnh, quản lý đối tượng và bộ nhớ.
- Có thể làm tăng tranh chấp cache/băng thông, lock hoặc chi phí GC.
- Không bảo đảm giảm thời gian của từng task; thậm chí tăng latency vì nhiều task tranh cùng CPU.

**Chú ý:** không nên kết luận mọi workload CPU-bound chạy trên virtual threads đều chậm hơn. Nếu so với một triển khai platform thread trước đây bị giới hạn quá thấp, chương trình mới có thể nhanh hơn do **mức parallelism tăng**. Đó là lợi ích của việc dùng thêm CPU còn rảnh, không phải vì một virtual thread thực thi mã CPU nhanh hơn. Để so sánh công bằng, phải xem xét cùng công việc và mức song song thực tế.

### 2.3. Giới hạn bởi phần tuần tự và tài nguyên chung

Ngay cả chương trình có thể dùng nhiều lõi, tốc độ tối đa vẫn bị phần tuần tự, thao tác đồng bộ, bộ nhớ và truyền dữ liệu giới hạn. Theo **định luật Amdahl**, nếu phần tuần tự chiếm tỷ lệ `s` và phần còn lại có thể song song trên `p` bộ xử lý:

\[
S(p) \leq \frac{1}{s+\frac{1-s}{p}}
\]

Ví dụ nếu 20% thời gian không thể song song hóa (`s = 0,2`), ngay cả khi số bộ xử lý tăng rất lớn thì speedup lý tưởng cũng không vượt `1 / 0,2 = 5` lần; các chi phí phối hợp thực tế còn khiến kết quả thấp hơn. Việc dùng virtual threads không xóa bỏ phần tuần tự này.

## 3. Ví dụ chạy được: cùng bài toán CPU trên hai loại executor

Chương trình đếm số nguyên tố nhỏ hơn 1.000.000 bằng 100 task độc lập, mỗi task xử lý một khoảng 10.000 số:

- Cách A: `Executors.newFixedThreadPool(...)` với số worker gần mức CPU mà JVM quan sát.
- Cách B: `Executors.newVirtualThreadPerTaskExecutor()`, mỗi task dùng một virtual thread.
- Cả hai thực hiện **cùng phép tính** và phải trả về **cùng kết quả**.
- Thời gian đo một lần chỉ để quan sát. Không được xem đây là kết luận benchmark vì JVM cần warm-up, JIT, CPU có thể thay đổi xung/khối lượng công việc và hệ điều hành có lịch chạy khác nhau.

Tạo tệp `CpuWorkloadDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CpuWorkloadDemo {
    private static final int LIMIT = 1_000_000;
    private static final int CHUNK = 10_000;

    public static void main(String[] args) throws Exception {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        int fixedWorkers = Math.max(1, Math.min(cpuCount, LIMIT / CHUNK));

        long beginFixed = System.nanoTime();
        int fixedCount;
        try (ExecutorService pool = Executors.newFixedThreadPool(fixedWorkers)) {
            fixedCount = countAll(pool);
        }
        long fixedMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - beginFixed);

        long beginVirtual = System.nanoTime();
        int virtualCount;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            virtualCount = countAll(pool);
        }
        long virtualMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - beginVirtual);

        System.out.println("Platform pool: " + fixedCount + " primes");
        System.out.println("Virtual threads: " + virtualCount + " primes");
        System.out.println("Equal results: " + (fixedCount == virtualCount));
        System.out.printf("Measured times (ms): fixed=%d, virtual=%d%n",
                fixedMillis, virtualMillis);
    }

    private static int countAll(ExecutorService pool) throws Exception {
        List<Future<Integer>> results = new ArrayList<>();
        for (int from = 0; from < LIMIT; from += CHUNK) {
            final int low = from;
            final int high = Math.min(from + CHUNK, LIMIT);
            results.add(pool.submit(() -> countPrimes(low, high)));
        }
        int count = 0;
        for (Future<Integer> result : results) {
            count += result.get();
        }
        return count;
    }

    private static int countPrimes(int low, int high) {
        int count = 0;
        for (int n = low; n < high; n++) {
            if (isPrime(n)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isPrime(int n) {
        if (n < 2) return false;
        for (int divisor = 2; divisor * divisor <= n; divisor++) {
            if (n % divisor == 0) return false;
        }
        return true;
    }
}
~~~

Biên dịch và chạy bằng **JDK 21 trở lên** (virtual threads là tính năng chính thức từ Java 21):

~~~bash
javac --release 21 CpuWorkloadDemo.java
java CpuWorkloadDemo
~~~

Ba dòng đầu ra cố định:

~~~text
Platform pool: 78498 primes
Virtual threads: 78498 primes
Equal results: true
~~~

Dòng cuối có dạng `Measured times (ms): fixed=<số đo>, virtual=<số đo>`; hai số thay đổi theo máy và lần chạy. Đây là phép kiểm tra chức năng đồng thời giới thiệu cách ghi nhận thời gian, **không** đủ để công bố tốc độ của hai mô hình.

**Phân tích:**

1. `countPrimes(low, high)` là tác vụ CPU-bound, không cố ý chờ network/file/lock.
2. `countAll` submit toàn bộ 100 task, rồi lấy các `Future`; vì `get()` nằm ở thread điều phối `main`, không có worker nào phải chờ task con trên chính pool của nó.
3. Pool platform cố định dùng số worker không lớn hơn số CPU logic (và không lớn hơn số task); virtual-thread-per-task executor cấp một virtual thread cho mỗi task.
4. Cả hai phải trả 78.498; nếu khác, trước tiên kiểm tra lỗi tính toán/partition, chưa bàn tốc độ.
5. Nếu một lần chạy virtual nhanh hơn hoặc chậm hơn, có thể do JVM warm-up, JIT compilation, GC, số worker, chi phí tạo executor, thermal throttling hoặc tác vụ quá nhỏ. Thứ tự “fixed chạy trước, virtual chạy sau” cũng tạo thiên lệch vì đoạn code đã được JVM tối ưu dần ở lần chạy thứ hai.

### Bài toán chia nhỏ đến mức nào?

Chia một triệu phần tử thành một triệu task cực nhỏ có thể tạo nhiều chi phí quản lý hơn là thời gian tính toán hữu ích. Ngược lại, chỉ chia thành một task sẽ không dùng hết nhiều CPU. Chọn **granularity** dựa trên kích thước công việc, chi phí chia/gộp, bộ nhớ và throughput đo được. Với CPU-bound, mô hình **bounded parallelism** (số worker hợp lý, các task đủ lớn) thường là điểm xuất phát có thể dự đoán được.

## 4. Khi nào virtual threads thật sự có lợi? Tác vụ thường xuyên phải chờ

Giả sử 24 yêu cầu độc lập, mỗi yêu cầu cần chờ 200 ms (ví dụ tượng trưng cho một thao tác I/O). Với pool gồm 4 platform threads, thường phải xử lý thành nhiều đợt vì mỗi thread bị giữ trong lúc chờ. Với virtual threads, khi `Thread.sleep` park virtual thread, carrier có thể dùng cho công việc khác.

Đây là **mô phỏng thời gian chờ**, không phải phép đo mạng hay cơ sở dữ liệu thật. Tạo `WaitingWorkloadDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class WaitingWorkloadDemo {
    private static final int TASK_COUNT = 24;

    public static void main(String[] args) throws Exception {
        long fixed = run(Executors.newFixedThreadPool(4));
        long virtual = run(Executors.newVirtualThreadPerTaskExecutor());

        System.out.printf("Measured times (ms): fixed=%d, virtual=%d%n",
                fixed, virtual);
    }

    private static long run(ExecutorService pool) throws Exception {
        long start = System.nanoTime();
        try (pool) {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < TASK_COUNT; i++) {
                tasks.add(() -> {
                    Thread.sleep(200);
                    return 1;
                });
            }
            List<Future<Integer>> futures = pool.invokeAll(tasks);
            int total = 0;
            for (Future<Integer> future : futures) {
                total += future.get();
            }
            System.out.println("Completed: " + total);
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
~~~

Chạy:

~~~bash
javac --release 21 WaitingWorkloadDemo.java
java WaitingWorkloadDemo
~~~

Hai dòng có kết quả cố định là:

~~~text
Completed: 24
Completed: 24
~~~

Dòng thứ ba in thời gian thực tế, thay đổi theo môi trường. Trong điều kiện ít tải, pool 4 platform workers thường cần khoảng 6 đợt chờ 200 ms (xấp xỉ 1,2 giây cộng overhead), còn virtual threads có thể cùng chờ và hoàn thành gần một đợt (xấp xỉ 200 ms cộng overhead). Đây là **lợi ích về khả năng đồng thời khi chờ**, không chứng minh virtual threads tính CPU nhanh hơn.

**Giới hạn thực tế:** kết nối mạng, connection pool, API rate limit và database không thể nhận số request vô hạn. Nếu tăng concurrency quá cao, hệ thống bên ngoài có thể nghẽn, timeout hoặc tăng latency. Dùng virtual threads không bỏ qua các giới hạn nghiệp vụ và tài nguyên.

## 5. Vì sao một bài đo CPU thấy virtual threads nhanh hơn?

Một kết quả như “virtual nhanh hơn 3 lần trong lần chạy đầu” cần được giải thích trước khi kết luận. Hãy kiểm tra:

| Hiện tượng | Câu hỏi cần xác minh |
| --- | --- |
| Đổi mã nguồn cùng lúc với đổi loại thread | Hai phiên bản có dùng cùng thuật toán, dữ liệu, chia task, thiết lập JVM và kết quả không? |
| Pool cũ chỉ 1–2 workers nhưng máy có nhiều lõi | Tốc độ tăng do sử dụng thêm CPU hay do virtual thread? |
| Fixed chạy trước, virtual chạy sau | JIT compilation, class loading, allocation và warm-up có tạo lợi thế cho lượt chạy sau không? |
| Bài toán quá nhỏ | Overhead tạo executor, Future, submit có lấn át tính toán không? |
| Máy đang có tải khác | Background processes, CPU frequency, GC, container quota có khác nhau không? |
| Một số task chờ disk/network/lock | Workload có thực sự CPU-bound hay đang pha trộn blocking? |
| Dùng “thời gian tạo hết thread” làm chuẩn | Bài đo có đợi **toàn bộ task hoàn tất** hay chỉ đo thời gian submit? |
| Đo tổng throughput nhưng so sánh độ trễ một task | Đang tối ưu chỉ số nào, và có tính cả thời gian queue không? |

Ví dụ, nếu một chương trình ban đầu dùng `newFixedThreadPool(1)` trên máy 8 lõi, sau đó chuyển sang nhiều virtual threads, throughput CPU có thể tăng đáng kể vì lúc trước chỉ dùng một lõi. Để phân tích nguyên nhân, hãy so sánh thêm với **platform thread pool có mức song song phù hợp**. Khi đó lợi thế chỉ do tạo ra nhiều logical threads sẽ không còn là lời giải thích đủ.

### Benchmark hợp lý cần làm gì?

- Sử dụng **JMH (Java Microbenchmark Harness)** khi đo vi mô, để xử lý warm-up, iteration/fork và tránh nhiều lỗi đo JVM. Không xem phép đo dùng `System.nanoTime()` một lần là benchmark kết luận.
- Đảm bảo chương trình thực sự sử dụng kết quả tính toán, không để JIT loại bỏ công việc không cần thiết.
- Thử các mức kích thước task, số tác vụ và mức CPU parallelism; ghi rõ JDK, số logical processors, container CPU quota và giới hạn bộ nhớ.
- Kiểm tra **CPU utilization**, throughput (task/giây), p50/p95/p99 latency, bộ nhớ/GC, chi phí scheduling và thời gian chờ.
- Cho mỗi cấu hình chạy nhiều iteration sau warm-up, thay đổi thứ tự chạy hoặc chạy trong các fork riêng, công bố độ phân tán kết quả.
- Đo đúng mục tiêu: một tác vụ CPU độc lập cần giảm latency, tổng lô CPU cần tăng throughput hay một dịch vụ pha trộn cần chịu nhiều kết nối đồng thời?

`jcmd`, Java Flight Recorder và Java Mission Control giúp quan sát thread states, CPU samples, allocation và blocking. Đọc dữ liệu đo cùng nhau: ví dụ CPU đã gần bão hòa mà hàng đợi tăng thì tăng thêm virtual threads khó có thể giảm phần tính toán CPU.

## 6. Lựa chọn executor phù hợp theo workload

| Loại công việc | Điểm xuất phát hợp lý | Vì sao |
| --- | --- | --- |
| Ít task, mỗi task tính CPU nặng | Xử lý trực tiếp hoặc pool platform kích thước phù hợp số CPU | Tận dụng CPU có giới hạn, ít overhead chia task. |
| Nhiều khối CPU độc lập có thể chia đều | Fixed thread pool / `ForkJoinPool` / parallel algorithm khi phù hợp | Kiểm soát mức parallelism, work-stealing với thuật toán chia nhỏ phù hợp. |
| Nhiều request chủ yếu chờ I/O | Virtual thread per task executor | Worker hệ điều hành không phải bị chiếm suốt thời gian chờ tương thích. |
| Task pha trộn CPU và I/O | Đo profile, tách phần blocking và phần tính toán hoặc giới hạn CPU work | Tránh việc tăng số task đang chờ đẩy CPU vào overload. |
| Phụ thuộc dịch vụ ngoài có quota/connection pool | Virtual threads + giới hạn concurrency/queue hoặc backpressure | Virtual threads không tăng sức chứa của dịch vụ ngoài. |
| Một phép tính tuần tự đơn lẻ | Tối ưu thuật toán, dữ liệu hoặc chia bài toán đúng cách | Chỉ đổi thread type không tạo ra parallel algorithm. |

### Chọn số lượng CPU worker

`Runtime.getRuntime().availableProcessors()` là **điểm bắt đầu** để đo, không phải công thức tối ưu bất biến. Workload dùng CPU cache/memory bandwidth, SMT/hyper-threading và giới hạn CPU của container có thể làm mức worker hiệu quả khác số logical processors. Pool platform cố định cho CPU-bound giúp giới hạn lượng task hoạt động; task vượt số worker có thể chờ trong hàng đợi. Với công việc tính toán có thể chia nhỏ một cách đệ quy, `ForkJoinPool` có thể thích hợp nhưng cũng cần profile vì một thuật toán chia việc kém vẫn gây overhead.

### Đừng dùng virtual threads như CPU pool tự động

`Executors.newVirtualThreadPerTaskExecutor()` tạo một virtual thread mới cho mỗi task; nó **không cố giới hạn số virtual threads theo CPU**. Với CPU-bound, gửi hàng triệu task nhỏ có thể tạo hàng triệu đối tượng, Future, metadata và kết quả chờ, ngay cả khi chỉ một phần đang chạy trên CPU.

Không nên xây “pool các virtual threads” như cách tái sử dụng platform threads; điểm mạnh của virtual threads là rẻ khi tạo theo task. Nhưng **rẻ không đồng nghĩa miễn phí**. Khi nguồn dữ liệu rất lớn, hãy giới hạn số task được tạo đồng thời từ phía producer, chia batch, hoặc dùng bounded executor phù hợp thay vì submit vô hạn. Với tác vụ I/O, giới hạn concurrency theo tài nguyên ngoài bằng `Semaphore`, connection pool và rate limiting; đừng dùng số virtual threads làm đại diện cho capacity của backend.

## 7. Điều xảy ra trong scheduler và ảnh hưởng của pinning

Virtual threads được JVM lập lịch trên các carrier platform threads. Khi virtual thread chạy mã CPU không chặn, nó vẫn chiếm carrier trong khoảng thời gian chạy đó. Khi gặp thao tác blocking được JVM hỗ trợ để unmount, carrier có thể rảnh để làm việc khác. Scheduler của virtual threads thường có mức parallelism mục tiêu liên quan số processor, nhưng đây không phải cam kết rằng mọi virtual thread đang tồn tại đều chạy song song, cũng không có “một CPU riêng cho mỗi virtual thread”.

**Pinning** là khi virtual thread không thể unmount khỏi carrier trong một thao tác chờ nhất định; carrier tiếp tục bị giữ, có thể làm giảm khả năng mở rộng của workload blocking:

- **JDK 21–23:** thao tác blocking trong vùng `synchronized` có thể gây pinning carrier; native/foreign code cũng có những tình huống pinning.
- **JDK 24 trở lên:** JEP 491 thay đổi cơ chế monitor để blocking bên trong `synchronized` không còn là nguyên nhân pinning thông thường; vẫn còn những trường hợp liên quan native/foreign interaction cần quan sát.
- Với cả hai giai đoạn, **mã CPU-bound không cần unmount** vì nó đang dùng CPU để tính; sửa pinning không làm phép tính tự thực thi nhanh hơn.

Không chọn API dựa trên mẹo “thay mọi `synchronized` bằng `ReentrantLock`” áp dụng cho mọi phiên bản. Phân tích theo JDK thực tế và đo bằng JFR khi có vấn đề. Tránh nhầm giữa **pinning**, **lock contention**, **CPU saturation** và **thread starvation**: đó là những nguyên nhân khác nhau, dù đều có thể gây giảm throughput.

## 8. Bảo vệ tính đúng và tài nguyên khi dùng concurrency lớn

Khi chia bài toán ra nhiều task, loại thread không giải quyết các rủi ro thông thường:

- **Race condition / data race:** mỗi task cập nhật chung một bộ đếm không đồng bộ có thể mất kết quả. Trong ví dụ, mỗi task trả `Integer` riêng và thread điều phối cộng các kết quả để tránh ghi chung.
- **Overhead phối hợp:** hàng chục nghìn task nhỏ có thể dành nhiều thời gian cho submit, Future và queue hơn cho công việc tính toán.
- **Unbounded fan-out:** tạo quá nhiều task đồng thời có thể dùng nhiều heap/metadata; cần kiểm soát lượng work-in-flight ở producer.
- **Blocking trong CPU pool:** một task CPU worker lại chờ Future/task con từ chính pool đã bão hòa có thể gây thread-starvation deadlock. Dùng composition hoặc đợi ở thread điều phối bên ngoài pool.
- **Cancellation:** `Future.cancel(true)` gửi tín hiệu interrupt, không cưỡng ép mã CPU đang phớt lờ interrupt dừng ngay. Tác vụ dài nên chủ động kiểm tra tín hiệu dừng tại điểm phù hợp và dọn tài nguyên trong `finally`.
- **ThreadLocal:** mỗi virtual thread có thể có biến `ThreadLocal`, nhưng nếu tạo rất nhiều virtual threads thì dùng nó làm nơi cache đối tượng đắt tiền theo thread có thể phình bộ nhớ, khác mô hình platform thread pool tái sử dụng ít worker.
- **Observability:** luôn đo kết quả đúng, thời gian hoàn tất, CPU và độ dài queue; một dòng log “started 100k threads” không cho biết công việc được xử lý nhanh hay chậm.

`ExecutorService` hỗ trợ `close()` khi dùng try-with-resources trên Java 21+, giúp đợi task hoàn tất khi đóng trong các demo. Ứng dụng thực tế vẫn cần chính sách timeout, ngắt, backpressure và lifecycle phù hợp; một tác vụ treo có thể khiến chương trình chờ đóng executor lâu hơn dự kiến.

## 9. Bài tập thực hành và cách đọc kết quả

**Bài 1 — Giữ nguyên loại thread, thay mức song song.** Chạy `CpuWorkloadDemo` với platform pool lần lượt 1, 2, 4, 8 workers (theo giới hạn môi trường). Quan sát tốc độ tăng có ngừng khi tới giới hạn CPU hay không; đối chiếu kết quả đếm luôn là 78.498.

**Bài 2 — Thay kích thước task.** Trong `CpuWorkloadDemo`, thử `CHUNK` bằng 1.000, 10.000, 100.000. Theo dõi số task, chi phí submit/gộp và khả năng phân phối công việc; giải thích vì sao rất nhỏ và rất lớn đều có thể kém.

**Bài 3 — Đổi chờ thành tính.** Trong `WaitingWorkloadDemo`, thay `Thread.sleep(200)` bằng một công đoạn tính CPU đủ nặng và vẫn trả về kết quả. Quan sát lợi ích về thời gian biến đổi như thế nào. Không so sánh bằng một lượt chạy; lập quy trình warm-up và đo nhiều lượt.

**Bài 4 — Phát hiện benchmark sai.** Xem một phép đo chỉ tính thời gian gọi `submit` nhưng không `get` hoặc `awaitTermination`. Hãy chỉ ra nó đang đo **thời gian gửi task**, không đo thời gian hoàn thành công việc. Sửa để có điểm hoàn tất rõ ràng, cùng input và checksum.

**Bài 5 — Đánh giá hệ thống pha trộn.** Giả sử một task đọc dữ liệu 300 ms rồi tính CPU 50 ms. Hãy nêu vì sao virtual threads có thể cải thiện concurrency khi đợi dữ liệu nhưng không làm phần CPU 50 ms chạy nhanh hơn; đề xuất giới hạn kết nối và CPU work-in-flight.

Khi thực hành, có thể quan sát hiện tượng trái với trực giác ở một lần chạy vì hệ điều hành và JIT không cố định. Hãy giải thích bằng tài nguyên thực tế, dữ liệu đo và kết quả đúng trước khi thay executor.

## 10. Tổng hợp để áp dụng

1. **Tập trung vào điểm nghẽn:** mã đang tính CPU hay đang chờ? Nếu là CPU, thay thread type không tạo thêm lõi và không làm thuật toán nhanh hơn.
2. **Với CPU-bound:** ưu tiên chia việc đúng, thuật toán phù hợp, bounded parallelism và benchmark có warm-up; kích thước thread pool thường bắt đầu từ số CPU mà JVM thấy rồi hiệu chỉnh theo phép đo.
3. **Với blocking-heavy:** virtual threads là lựa chọn rất hữu ích để phục vụ nhiều tác vụ đang chờ mà không phải giữ nhiều platform threads.
4. **Với workload pha trộn:** phân tích cả CPU, I/O, queue, backend capacity và latency; mở rộng số virtual threads phải đi cùng giới hạn tài nguyên ngoài.
5. **Không đọc một kết quả benchmark đơn lẻ thành quy luật:** cùng input, cùng công việc, cùng điều kiện, đủ warm-up và kết quả đo nhiều lần mới có cơ sở đánh giá.

**Thông điệp chính:** virtual threads làm việc quản lý **nhiều tác vụ đồng thời, đặc biệt là tác vụ chờ**, trở nên rẻ hơn. Một tác vụ **CPU-bound** vẫn phải tiêu thụ tài nguyên CPU thật; muốn nhanh hơn cần cải thiện thuật toán, song song hóa hợp lý hoặc thêm năng lực xử lý thực tế.

### Tài liệu Java tham khảo

- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444): mục tiêu thiết kế, giới hạn và cách dùng virtual threads.
- [Oracle Java 21 — Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html): cơ chế mount/unmount, thread-per-task, trường hợp blocking phù hợp.
- [JEP 491 — Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491): thay đổi cơ chế monitor từ Java 24.
- [JMH](https://openjdk.org/projects/code-tools/jmh/): công cụ benchmark hiệu năng vi mô cho JVM.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-012">Chẩn đoán bằng thread dump và Java Flight Recorder</a>

<details>
<summary>Xem chi tiết</summary>

# Chẩn đoán Virtual Threads bằng thread dump và Java Flight Recorder (JFR)

Virtual Threads là luồng nhẹ do Java runtime quản lý, được chuẩn hóa trong **Java 21** (JEP 444). Chúng giúp mô hình *mỗi tác vụ có một thread* mở rộng tốt khi tác vụ thường xuyên chờ I/O. Tuy nhiên, “có rất nhiều virtual thread” không tự chứng minh ứng dụng hoạt động hiệu quả: các tác vụ vẫn có thể nghẽn vì tài nguyên hữu hạn, khóa, vòng chờ, CPU, hệ thống bên ngoài hoặc những đoạn thực thi khiến virtual thread không nhường được carrier.

Bài này hướng dẫn **chẩn đoán hoạt động của Virtual Threads trong Java nói chung** bằng hai công cụ sẵn có trong JDK:

- **Thread dump:** ảnh chụp các stack đang tồn tại tại một thời điểm, để trả lời “thread đang ở đâu, chờ gì, stack nào lặp lại?”.
- **Java Flight Recorder (JFR):** chuỗi sự kiện và số đo theo thời gian, để trả lời “bất thường xuất hiện khi nào, kéo dài bao lâu, tần suất thế nào, có pinning hay tranh chấp tài nguyên không?”.

Hai công cụ bổ sung cho nhau. Một dump đơn lẻ thường không đủ để kết luận về nguyên nhân hiệu năng; một JFR không được bật đúng event hoặc không thu đúng thời điểm cũng có thể bỏ lỡ dữ kiện.

## 1. Mô hình cần hiểu trước khi đọc dấu vết

**Virtual thread** là một đối tượng `Thread` thật trong API Java: có tên, stack, trạng thái, khả năng interrupt và stack trace. Khác platform thread gắn sát với OS thread, virtual thread được JVM **mount** lên một platform thread gọi là **carrier** khi cần chạy. Khi gặp thao tác chờ mà JVM hỗ trợ unmount, virtual thread có thể rời carrier và nhường carrier để chạy virtual thread khác.

Ví dụ, một virtual thread đang ở `CountDownLatch.await()` hoặc chờ network I/O không nhất thiết chiếm một OS thread suốt thời gian chờ. Điều này không có nghĩa việc chờ là miễn phí: hàng chục nghìn tác vụ cùng đợi một connection pool nhỏ vẫn gây độ trễ và backlog.

Phân biệt bốn khái niệm:

| Khái niệm | Ý nghĩa khi chẩn đoán |
|---|---|
| *Waiting/parked* | Thread chờ điều kiện hoặc kết quả; có thể là hành vi bình thường, cần nhìn stack và thời gian |
| *Blocked on monitor/lock* | Thread chưa giành được khóa; đo cạnh tranh khóa và xác định vùng khóa dài |
| *Carrier pinning* | Virtual thread gặp tình huống không thể unmount, khiến carrier bị giữ trong một khoảng thời gian |
| *Starvation / saturation* | Tác vụ không được tiến triển kịp vì CPU, scheduler hoặc một tài nguyên hữu hạn; cần chứng cứ vượt ra ngoài tên trạng thái thread |

**Một thread ở trạng thái `WAITING` không mặc nhiên là deadlock**, và một virtual thread `RUNNABLE` không mặc nhiên đang dùng CPU. Trạng thái là ảnh chụp JVM-level, phải gắn với stack, số liệu thời gian và tốc độ xử lý thực tế.

### Khác biệt theo phiên bản JDK: đặc biệt quan trọng với pinning

- **Java 21–23:** chờ hoặc blocking trong vùng `synchronized` có thể giữ virtual thread trên carrier. Vì vậy một thao tác I/O lâu trong monitor là nghi vấn pinning có giá trị.
- **Từ Java 24 (JEP 491):** JVM cải thiện `synchronized` để virtual thread có thể unmount trong các tình huống trước kia gây pinning vì monitor. Không kết luận “có synchronized ⇒ pinning” khi dùng JDK 24+.
- **Các phiên bản mới vẫn có trường hợp pinning khác**, điển hình có native/foreign call hoặc VM frame không thể nhường carrier. Phải dựa vào JFR và stack trên **đúng JDK đang chạy**.

Pinning kéo dài có thể hạn chế khả năng mở rộng, nhưng không đồng nghĩa chương trình sai về mặt chức năng. Một sự kiện ngắn, hiếm gặp có thể không phải vấn đề ưu tiên.

## 2. Khi nào dùng thread dump, khi nào dùng JFR?

| Câu hỏi | Thread dump | JFR |
|---|---|---|
| Những stack nào đang đợi tại thời điểm này? | Rất phù hợp | Có thể hỗ trợ qua event/stack sampling |
| Nhiều virtual thread đang chờ cùng semaphore, latch, I/O? | Tốt để tìm stack tương tự | Tốt để kiểm tra diễn biến và thời lượng |
| Vấn đề chỉ xảy ra trong vài giây rồi biến mất? | Dễ bỏ lỡ | Phù hợp nếu ghi đúng thời điểm |
| Pinning kéo dài hoặc lặp lại có thật không? | Chỉ gợi ý | Event `jdk.VirtualThreadPinned` là chứng cứ trực tiếp hơn |
| CPU cao, allocation, GC hoặc lock contention kéo dài? | Một phần | JFR có sự kiện/sampling tương ứng |
| Kết luận “bị deadlock”? | Cần nhiều mẫu và thông tin lock; dump mới có thể thiếu một phần | Cần tương quan thêm dữ liệu về khóa và tiến triển |

**Quy trình thường dùng:** xác định triệu chứng và thời điểm → chụp nhiều dump có khoảng cách → ghi JFR trong khoảng có tải → đối chiếu stack/event/tài nguyên → kiểm chứng giả thuyết bằng sửa đổi hoặc phép đo lặp lại.

## 3. Chuẩn bị môi trường và nhận diện JVM đúng

Yêu cầu tối thiểu:

- **JDK 21+** để có Virtual Threads bản chính thức; JDK được dùng để chạy và bộ công cụ `jcmd`/`jfr` nên tương thích với JVM đích.
- Quyền phù hợp để attach vào tiến trình Java. Trong container, cần chạy lệnh ở môi trường thấy được tiến trình JVM và có quyền attach tương ứng.
- Có đủ dung lượng cho file dump/JFR; với hàng trăm nghìn virtual thread, thread dump có thể lớn. Thu thập có kiểm soát thay vì lặp liên tục không giới hạn.

Liệt kê tiến trình mà `jcmd` nhìn thấy:

```bash
jcmd -l
```

Chọn **PID của đúng JVM ứng dụng**, sau đó kiểm tra các chẩn đoán mà JVM đó hỗ trợ:

```bash
jcmd <PID> help
jcmd <PID> help Thread.dump_to_file
jcmd <PID> help JFR.start
```

Trong các lệnh ở bài này, `<PID>` là ký hiệu thay thế bằng **số PID thực tế**, không nhập cả dấu ngoặc nhọn. Cú pháp tham số và thông tin được ghi có thể được cải thiện giữa các phiên bản JDK, vì vậy `jcmd <PID> help ...` là nguồn kiểm chứng trực tiếp cho JVM đang chạy.

## 4. Lấy thread dump có Virtual Threads

Dùng lệnh `Thread.dump_to_file`, được thiết kế để quan sát cả virtual threads:

```bash
jcmd <PID> Thread.dump_to_file vt-dump.txt
jcmd <PID> Thread.dump_to_file -format=json vt-dump.json
```

- Không chỉ định định dạng sẽ dùng kiểu văn bản mặc định của JDK đang chạy.
- `-format=json` phù hợp khi muốn phân tích bằng công cụ. Hình dạng/kiểu dữ liệu JSON có thể khác giữa các đời JDK; đừng xây parser dựa trên một ví dụ định dạng cố định.
- Đường dẫn file được hiểu **trong môi trường của tiến trình JVM đích**. Dùng đường dẫn tuyệt đối khi làm việc qua container, service hoặc các thư mục chạy khác nhau để tránh nhầm nơi lưu.
- Nên lưu **2–3 dump cách nhau vài giây** khi điều tra vấn đề kéo dài, rồi so sánh các stack tương tự. Mẫu lặp lại có giá trị hơn một ảnh chụp riêng lẻ.

Có thể dùng:

```bash
jcmd <PID> Thread.print -l
```

như một nguồn bổ sung cho thông tin platform threads và lock, nhưng **không dùng riêng `Thread.print` để kết luận về tất cả virtual threads**. Virtual threads đang unmount có thể không hiện đầy đủ ở dạng dump truyền thống; mức độ thông tin về lock/monitor trong `Thread.dump_to_file` cũng phụ thuộc phiên bản JDK (đặc biệt các bản cũ).

### Đọc gì trong dump?

Theo thứ tự ưu tiên:

1. **Tên thread/stack phổ biến:** tìm các virtual threads cùng chờ tại một `CountDownLatch.await`, `Semaphore.acquire`, thao tác socket, `Future.get` hoặc `LockSupport.park`. Một stack trùng lặp với số lượng lớn có thể là nơi backlog tích tụ.
2. **Điểm chờ thật:** xem cả những frame ở phía trên (nơi đang đợi) lẫn frame ứng dụng ở dưới (tại sao tác vụ tới đây). Nên phân biệt thư viện runtime với chỗ gọi của ứng dụng.
3. **Sự thay đổi giữa các lần chụp:** nhiều thread cùng đứng nguyên tại một điểm trong các dump liên tiếp là tín hiệu nên tìm hiểu kỹ, nhưng vẫn chưa chứng minh deadlock.
4. **Carrier và platform threads:** nếu carrier bận CPU/blocked quá lâu, ghép với JFR để tìm điều gì giữ nó. Không nhầm tên carrier với danh tính công việc logic của virtual thread.
5. **Tài nguyên phụ thuộc:** nếu nhiều virtual threads đợi `Semaphore` hay pool, kiểm tra giới hạn pool, số tác vụ đang chạy, thời gian chờ và tốc độ giải phóng tài nguyên.

**Không có một “con số virtual threads tối đa tốt” chung cho mọi ứng dụng.** Cần xem throughput, p95/p99 latency, số request đang chờ, mức dùng CPU, số connection hữu hạn và tần suất timeout.

## 5. Bài thực hành Java 21+: tạo những virtual threads dễ quan sát

Lưu thành **`VirtualThreadDiagnosticsDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VirtualThreadDiagnosticsDemo {
    public static void main(String[] args) throws InterruptedException {
        int count = 3;
        CountDownLatch allWaiting = new CountDownLatch(count);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < count; i++) {
                final int id = i;
                executor.submit(() -> {
                    Thread.currentThread().setName("diag-vt-" + id);
                    allWaiting.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            allWaiting.await();
            System.out.println("Virtual threads waiting: " + count);
            System.out.println("PID: " + ProcessHandle.current().pid());

            try {
                // Giữ tiến trình sống để thu thread dump và JFR từ cửa sổ khác.
                Thread.sleep(60_000);
            } finally {
                release.countDown();
            }
        }

        System.out.println("Finished");
    }
}
```

Biên dịch và chạy trong một terminal:

```bash
javac VirtualThreadDiagnosticsDemo.java
java VirtualThreadDiagnosticsDemo
```

**Ví dụ cấu trúc stdout** (PID khác nhau mỗi lần chạy):

```text
Virtual threads waiting: 3
PID: <PID-thuc-te>
Finished
```

Dòng `Finished` chỉ được in **sau khoảng 60 giây**, khi chương trình đã giải phóng `CountDownLatch` và các tác vụ hoàn tất. Từ một terminal khác, **trong khi chương trình đang ngủ**, thay PID vừa in vào:

```bash
jcmd <PID> Thread.dump_to_file -format=json vt-lab.json
jcmd <PID> Thread.dump_to_file vt-lab.txt
```

Mở file và tìm tên `diag-vt-0`, `diag-vt-1`, `diag-vt-2` cùng stack chờ `CountDownLatch.await` (các frame nội bộ khác có thể phụ thuộc JDK). Ba virtual threads này đang chờ một tín hiệu có chủ đích: **đây không phải deadlock hay pinning**.

Ví dụ sử dụng `Executors.newVirtualThreadPerTaskExecutor()`, **không tạo pool cố định các virtual threads**: executor cấp một virtual thread cho mỗi tác vụ. Việc giới hạn số tác vụ đang chạy phải được thực hiện theo nhu cầu tài nguyên thực (ví dụ semaphore cho kết nối hoặc dịch vụ ngoại vi), không phải bằng cách thay virtual threads thành một pool nhỏ cố định để che giấu số tác vụ tồn đọng.

### Nếu không tìm thấy thread trong dump

Kiểm tra trước tiên:

- Có chụp **trước khi chương trình kết thúc** không? Tác vụ đã xong thì thread sẽ biến mất.
- Có dùng đúng PID, đúng JVM và đúng file output không?
- Có dùng `Thread.dump_to_file` hay chỉ `Thread.print`?
- Có tìm theo tên thread và stack thay vì chỉ tìm trạng thái `RUNNABLE`/`WAITING` không?
- Dung lượng dump có bị công cụ đọc cắt ngắn không? Dùng công cụ mở file toàn vẹn hoặc trình phân tích phù hợp.

## 6. Thu Java Flight Recorder ngay trên JVM đang chạy

JFR là bộ ghi sự kiện/sampling tích hợp vào HotSpot/JDK, có thể thu thông tin về scheduling, blocking, CPU, allocation, GC, monitor contention và sự kiện riêng của virtual threads. JFR thường có overhead thấp khi dùng cấu hình phù hợp, nhưng **không phải mọi event đều bật sẵn**, và recording quá dài hoặc quá chi tiết có thể tiêu tốn tài nguyên.

Trong khoảng 60 giây mà chương trình demo đang sống, có thể chạy:

```bash
jcmd <PID> JFR.start name=vt-diagnostics settings=profile duration=20s filename=vt-diagnostics.jfr
jcmd <PID> JFR.check
```

Sau khi recording đủ thời lượng và file xuất hiện, xem tổng quan và event:

```bash
jfr summary vt-diagnostics.jfr
jfr print --events jdk.VirtualThreadPinned,jdk.VirtualThreadSubmitFailed vt-diagnostics.jfr
```

Nếu ghi một recording **không có thời hạn**, cần kiểm soát vòng đời của nó, chẳng hạn:

```bash
jcmd <PID> JFR.start name=vt-manual settings=default
jcmd <PID> JFR.dump name=vt-manual filename=vt-manual.jfr
jcmd <PID> JFR.stop name=vt-manual
```

Lệnh `JFR.dump` sao chép dữ liệu tại thời điểm yêu cầu; `JFR.stop` kết thúc recording. Nếu dùng `duration=20s` thì recording tự dừng theo thời lượng đã chỉ định. Với `JFR.start`, file `filename` được tạo bởi tiến trình JVM đích; nên dùng đường dẫn tuyệt đối để tránh nhầm working directory. Lệnh `jfr summary`/`jfr print` thực hiện trên máy có file JFR và công cụ JDK tương thích.

**Đừng chờ đợi demo ở mục 5 tự tạo ra `VirtualThreadPinned`.** Nó chỉ chờ `CountDownLatch` đúng cách; file JFR có thể không chứa một event pinning nào, và điều này là kết quả hợp lệ.

### Các event quan trọng cho Virtual Threads

| Event JFR | Khi nào có ích? | Ghi chú |
|---|---|---|
| `jdk.VirtualThreadPinned` | Nghi virtual thread giữ carrier khi blocking | Thường bật mặc định với ngưỡng 20 ms trong cấu hình JFR chuẩn; điều kiện phát sinh thay đổi theo JDK |
| `jdk.VirtualThreadSubmitFailed` | Nghi việc start/unpark virtual thread gặp vấn đề tài nguyên | Thường bật mặc định; cần xem nguyên nhân và thời điểm |
| `jdk.VirtualThreadStart` | Cần biết thời điểm tạo virtual thread, tốc độ khởi tạo | **Tắt mặc định**; bật có chọn lọc khi cần |
| `jdk.VirtualThreadEnd` | Cần biết vòng đời virtual thread, tốc độ hoàn tất | **Tắt mặc định**; bật có chọn lọc khi cần |
| `jdk.JavaMonitorEnter` / `jdk.JavaMonitorWait` | Nghi tranh chấp monitor hoặc thời gian chờ trên monitor | Kiểm tra cấu hình và threshold; không đồng nhất monitor contention với pinning |
| `jdk.ThreadPark` | Nghi các khoảng đợi/park kéo dài | Có thể giúp tìm thao tác chờ; xác minh event và threshold được bật trong recording |

Một lệnh in bốn event riêng của Virtual Threads (chỉ có kết quả khi các event đã thực sự được ghi):

```bash
jfr print --events jdk.VirtualThreadStart,jdk.VirtualThreadEnd,jdk.VirtualThreadPinned,jdk.VirtualThreadSubmitFailed vt-diagnostics.jfr
```

Nếu không có `VirtualThreadStart` và `VirtualThreadEnd`, điều đó **không chứng minh không có virtual thread**: hai event này mặc định thường bị tắt. Muốn đo tạo/hủy thread, bật tường minh chúng thông qua JDK Mission Control, file cấu hình JFR tùy chỉnh hoặc API `jdk.jfr.Recording`, đồng thời cân nhắc overhead khi lượng thread rất lớn.

### Cách đọc một event pinning

Khi thấy `jdk.VirtualThreadPinned`, xem:

1. **Start time + duration:** có rơi đúng cửa sổ bị tăng latency không, mỗi lần kéo dài bao lâu?
2. **Stack trace:** frame ứng dụng nào dẫn tới thao tác blocking? Đừng chỉ dừng ở frame VM/`LockSupport`.
3. **Event thread và carrier** (nếu runtime/JFR ghi): xác định virtual thread và carrier liên quan.
4. **Tần suất và tổng ảnh hưởng:** hàng nghìn lần pinning trong hot path khác xa một lần ngắn khi khởi tạo.
5. **Phiên bản JDK:** trên Java 21–23 cần xem blocking nằm trong `synchronized`; trên Java 24+ phải điều tra loại pinning còn tồn tại và chính thông tin stack/event được ghi.

JFR ghi nhận **sự kiện vượt threshold**, không có nghĩa mọi thao tác blocking đều phải xuất hiện. Nếu tần suất sự kiện bằng 0, hãy kiểm tra cấu hình, threshold, thời gian ghi, tính khả dụng của event và phiên bản JVM trước khi loại bỏ giả thuyết.

## 7. Phân biệt chờ bình thường, lock contention, deadlock và pinning

### Tình huống A — Hàng nghìn virtual threads cùng đợi tài nguyên

**Hiện tượng:** latency tăng và thread dump lặp lại `Semaphore.acquire` hoặc vị trí lấy kết nối từ pool.

**Giả thuyết hợp lý:** số tác vụ đồng thời vượt năng lực cấp tài nguyên; thời gian đợi ở semaphore/pool dài hơn thời gian thực thi công việc.

**Cách xác minh:** thu 2–3 dump trong giai đoạn nghẽn; đo số connection đang sử dụng, hàng đợi, timeout và thời gian thực hiện truy vấn/remote I/O; ghi JFR để thấy các khoảng chờ và CPU. Đếm nhiều virtual thread đang `WAITING` chưa đủ kết luận scheduler lỗi hoặc JFR pinning.

**Hướng xử lý:** giới hạn concurrency theo tài nguyên thực, dùng timeout/cancellation rõ ràng, giảm thời gian giữ kết nối hoặc tối ưu hệ thống phụ thuộc. Giảm số carrier không làm tăng số connection thực tế.

### Tình huống B — Pinning trên Java 21–23

**Hiện tượng:** throughput giảm mạnh khi nhiều tác vụ thực hiện blocking I/O bên trong `synchronized`; JFR có nhiều `jdk.VirtualThreadPinned` với stack tương ứng.

**Giả thuyết:** virtual threads bị giữ trên carrier trong thời gian blocking, làm giảm số carrier sẵn sàng chạy tác vụ khác.

**Xử lý có điều kiện:** nếu khóa được dùng chỉ để bảo vệ trạng thái ngắn, hãy tách phần I/O lâu ra khỏi vùng `synchronized` nếu bảo đảm tính đúng đắn; hoặc cân nhắc `ReentrantLock` ở hot path có blocking, luôn dùng `unlock()` trong `finally`. Tránh máy móc đổi **tất cả** `synchronized` thành `ReentrantLock`. Nếu có thể, so sánh sau khi nâng JDK 24+ (JEP 491) vì cơ chế monitor đã thay đổi.

Mẫu minh họa ý tưởng (không phải chương trình độc lập):

```java
// Rủi ro trên JDK 21–23 nếu remoteCall() blocking lâu.
synchronized (guard) {
    remoteCall();
}

// Giải pháp chỉ hợp lệ khi việc gọi remoteCall() không cần giữ monitor:
// kiểm tra lại bất biến dữ liệu trước khi áp dụng.
synchronized (guard) {
    snapshot = buildSnapshot();
}
remoteCallWith(snapshot);
```

Không tách thao tác khỏi monitor khi yêu cầu đồng bộ của dữ liệu thực sự cần bảo vệ xuyên suốt giao dịch đó; tính đúng đắn quan trọng hơn một giả thuyết tối ưu.

### Tình huống C — Deadlock hoặc vòng chờ logic

**Hiện tượng:** nhiều stack giữ nguyên ở `Future.get`, `CountDownLatch.await` hoặc monitor qua các lần chụp; tác vụ không hoàn thành, các tín hiệu giải phóng không bao giờ xảy ra.

**Giả thuyết:** có vòng phụ thuộc, một stage đợi chính kết quả mình phải tạo, hoặc khóa bị giữ bởi tác vụ cũng đang chờ.

**Xác minh:** tái dựng đồ thị **ai đang đợi ai** từ mã và stack; đối chiếu thời điểm submit, complete và timeout; sử dụng thông tin monitor/lock bổ sung nếu có. Không suy luận deadlock chỉ từ một stack `WAITING`; nhiều tác vụ có thể đang chờ hợp lệ một latch chưa được giải phóng.

**Hướng xử lý:** loại bỏ vòng phụ thuộc, thiết kế tín hiệu release trong `finally`, bổ sung deadline và propagation của interrupt/cancellation khi đúng hợp đồng. Virtual Threads làm cho việc chờ rẻ hơn về OS threads, **không tự giải quyết deadlock logic**.

### Tình huống D — CPU cao, không thấy pinning

**Hiện tượng:** CPU gần tối đa, latency tăng; JFR sampling cho thấy nhiều frame tính toán; `VirtualThreadPinned` không nổi bật.

**Giả thuyết:** công việc tính toán thuần CPU đang dùng hết tài nguyên, hoặc GC/allocation tăng. Virtual threads không tạo thêm năng lực CPU và không giúp phép tính CPU-heavy chạy nhanh hơn chỉ nhờ đổi thread.

**Hướng xử lý:** dùng JFR CPU/profile, phân tích hot methods, allocation và GC; tách hoặc giới hạn tác vụ CPU-heavy; đo hiệu quả sau thay đổi.

## 8. Phân tích JFR sâu hơn bằng Java API

Ngoài `jfr summary` và `jfr print`, có thể đọc JFR bằng API `jdk.jfr.consumer.RecordingFile` trên JDK có module `jdk.jfr`. Ví dụ hoàn chỉnh sau đếm số event pinning **đã được ghi trong file**, không đoán số thread hiện hành.

Lưu thành **`CountPinnedEvents.java`**:

```java
import java.io.IOException;
import java.nio.file.Path;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

public class CountPinnedEvents {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java CountPinnedEvents <file.jfr>");
            return;
        }

        long total = 0;
        try (RecordingFile recording =
                     new RecordingFile(Path.of(args[0]))) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                if ("jdk.VirtualThreadPinned"
                        .equals(event.getEventType().getName())) {
                    total++;
                }
            }
        }

        System.out.println("Recorded pinned events: " + total);
    }
}
```

Chạy bằng JDK 21+:

```bash
javac CountPinnedEvents.java
java CountPinnedEvents vt-diagnostics.jfr
```

Ví dụ, nếu file không chứa event pinning, stdout là:

```text
Recorded pinned events: 0
```

Con số có thể khác trên file thực tế; nó chỉ đếm những event JFR **đã phát sinh và được bật/ghi**, không đo mọi lần giữ monitor hay mọi thao tác I/O. Khi cần phân tích nhiều loại event, có thể mở file bằng **JDK Mission Control (JMC)** để lọc theo event type, duration, stack trace và liên hệ với CPU/lock/GC.

## 9. Quy trình chẩn đoán từng bước trong môi trường thực

**Bước 1 — Ghi nhận triệu chứng và cửa sổ thời gian.** Ví dụ “p99 tăng từ 100 ms lên 3 giây trong 15:20–15:25”, có timestamp, throughput, CPU, heap/GC, timeout, số tác vụ đang chờ và giới hạn kết nối. Nếu không có số đo nền, rất dễ nhầm thời gian chờ bình thường thành lỗi.

**Bước 2 — Xác định PID, phiên bản JVM, cấu hình chẩn đoán.** Dùng `java -version` với runtime thực tế, `jcmd -l` và `jcmd <PID> help`. Ghi rõ JDK 21–23 hay 24+ khi nghi pinning.

**Bước 3 — Chụp chuỗi thread dump.** Dùng `Thread.dump_to_file` (ưu tiên cả JSON và text nếu cần); tìm nhóm stack tương tự và quan sát thay đổi qua thời gian. Với tải cực lớn, cân nhắc kích thước dump và chi phí chụp.

**Bước 4 — Thu JFR phủ đúng lúc vấn đề.** Dùng `settings=profile` trong một khoảng ngắn có kiểm soát. Xem `jdk.VirtualThreadPinned`, `jdk.VirtualThreadSubmitFailed`, CPU samples, monitor/park/GC events phù hợp. Bật thêm start/end nếu cần phân tích vòng đời virtual thread.

**Bước 5 — Đối chiếu ba lớp bằng chứng.** (a) stack tại điểm chờ, (b) event/duration/tần suất theo thời gian, (c) metric tài nguyên như CPU, connection pool, queue và latency. Một kết luận nguyên nhân chỉ mạnh khi giải thích được cả hiện tượng quan sát và thay đổi của metric.

**Bước 6 — Kiểm chứng sửa đổi.** Thay đúng nút thắt: monitor trên JDK cũ, pool quá nhỏ, timeout chưa rõ, blocking có vòng chờ hoặc đoạn CPU-heavy. Dùng cùng cách đo trên cùng tải để so sánh trước–sau; tránh “tối ưu” toàn bộ code chỉ vì thấy nhiều virtual threads.

## 10. Các bẫy chẩn đoán thường gặp

1. **Dùng `Thread.print` rồi tưởng đã thấy mọi virtual thread.** Với virtual threads cần `Thread.dump_to_file`; dùng dump truyền thống như bằng chứng bổ sung.
2. **Coi `WAITING` là deadlock.** Cần kiểm tra tác vụ nào sẽ giải phóng điều kiện, thời gian chờ và chuỗi dump.
3. **Thấy `synchronized` trên JDK 24+ là kết luận pinning.** JEP 491 đã thay đổi hành vi monitor; dùng version đúng và JFR event thực tế.
4. **Không có `jdk.VirtualThreadPinned` là khẳng định “không có vấn đề”.** Event có ngưỡng, có khoảng thời gian ghi và có điều kiện phụ thuộc JDK.
5. **Thấy vắng `VirtualThreadStart/End` là khẳng định không có virtual threads.** Hai event này thường bị tắt mặc định.
6. **Đếm virtual threads thay vì đo tài nguyên.** Hàng chục nghìn thread ngủ hợp lệ có thể ít nghiêm trọng hơn vài trăm tác vụ đợi pool đã cạn.
7. **Gán mọi chậm chạp cho scheduler.** CPU, GC, file descriptors, network và upstream/downstream timeout vẫn là những giới hạn thực.
8. **Thu quá muộn hoặc chụp duy nhất một dump.** Sự cố ngắn có thể đã qua; phải đặt recording đúng thời điểm và đối chiếu nhiều mẫu.
9. **Ghi file mà không tìm được trên máy.** `jcmd` ra lệnh cho JVM đích ghi file: thư mục làm việc/quyền ghi nằm ở môi trường JVM đó.
10. **Đưa dump/JFR lên nơi công khai vô ý.** Stack traces, class names, tham số JVM và chuỗi có trong recording có thể chứa thông tin vận hành nhạy cảm; giữ quyền truy cập phù hợp.

## 11. Tóm lược kiến thức và tài liệu chính thức

- **Thread dump** cho thấy virtual threads ở đâu tại thời điểm chụp; dùng `jcmd <PID> Thread.dump_to_file ...`, đọc stack theo ngữ cảnh và so sánh nhiều lần.
- **JFR** cho thấy sự kiện kéo dài theo thời gian; phân tích `jdk.VirtualThreadPinned`, `jdk.VirtualThreadSubmitFailed` và các event CPU/lock/GC để kiểm tra giả thuyết.
- **Pinning khác với waiting** và phải xét **phiên bản JDK**: monitor pinning quan trọng ở Java 21–23, được cải thiện lớn từ Java 24; vẫn có các tình huống pinning khác.
- **Virtual Threads không tự chữa** deadlock, nghẽn pool, CPU saturation hoặc I/O chậm. Chẩn đoán đáng tin phải ghép dump, JFR và số liệu tài nguyên thực tế.

Tham khảo thêm:

- [Oracle: Virtual Threads (Java 21)](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
- [OpenJDK JEP 444 — Virtual Threads](https://openjdk.org/jeps/444)
- [OpenJDK JEP 491 — Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491)
- [Oracle: `jcmd` command reference](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-013">Structured Concurrency (preview ở Java 21): vai trò và giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Structured Concurrency (preview ở Java 21): vai trò và giới hạn

## 1. Vấn đề: tác vụ cha phải chịu trách nhiệm về các tác vụ con

Giả sử một thao tác xử lý cần đồng thời lấy **thông tin người dùng** và **điểm thưởng**, sau đó ghép thành một kết quả. Hai việc độc lập nên có thể chạy song song. Nhưng nếu người dùng hủy thao tác, một tác vụ thất bại, hoặc hết hạn chờ, điều gì xảy ra với tác vụ còn lại?

Cách viết bất đồng bộ thiếu cấu trúc thường có các vấn đề:

- **Vòng đời rời rạc:** tác vụ con vẫn chạy sau khi phương thức tạo ra nó đã thoát, vì không có nơi duy nhất chịu trách nhiệm chờ và dọn dẹp.
- **Lỗi bị thất lạc:** một future thất bại nhưng không được lấy kết quả, dẫn tới lỗi không được xử lý trong luồng nghiệp vụ.
- **Hủy chưa đồng bộ:** tác vụ cha biết không cần kết quả nữa nhưng các tác vụ con khác vẫn tiếp tục chiếm tài nguyên.
- **Khó quan sát:** khi điều tra sự cố, khó nhìn thấy những thread nào thuộc cùng một thao tác cha.

**Structured Concurrency** tổ chức các tác vụ con thành **một nhóm có phạm vi sống rõ ràng**. Trong Java 21, API preview `StructuredTaskScope` cho phép bắt đầu các subtask trong cùng một scope, chờ chúng, áp dụng chính sách khi thành công/thất bại, và đóng scope khi hoàn tất.

Ý tưởng cốt lõi: **code tạo và sở hữu công việc con có trách nhiệm kết thúc phạm vi công việc ấy**. Việc mở scope, `fork`, `join` rồi `close` giống một khối có điểm vào/ra có thể theo dõi. Nó giúp quản lý nhóm tác vụ an toàn và dễ đọc hơn, nhưng vẫn cần xử lý riêng tài nguyên ngoài JVM và những thao tác không hợp tác với hủy.

Bài này tập trung **Java 21 / JEP 453**, trong ngữ cảnh Virtual Threads của Java. Structured Concurrency ở Java 21 là **preview API**, chưa phải hợp đồng API ổn định để mang nguyên mã nguồn sang mọi bản JDK mới.

## 2. Mô hình một scope: `fork → join → result → close`

Trong Java 21, class chính là `java.util.concurrent.StructuredTaskScope<T>`. Một vòng đời thông thường:

~~~text
Thread gọi (owner)
│
├─ Mở StructuredTaskScope bằng try-with-resources
│   ├─ fork(task A) → thread con A ───────┐
│   ├─ fork(task B) → thread con B ───────┤ chạy đồng thời
│   ├─ join()/joinUntil(...) ◀───────────┘
│   ├─ xem kết quả / xử lý lỗi
│   └─ close() khi ra khỏi try
└─ Tiếp tục sau khi scope được đóng
~~~

Các bước và ý nghĩa:

1. **Mở scope:** tạo một đối tượng scope ở thread gọi, gọi là *owner*. Trong Java 21, constructor mặc định tạo **virtual thread** cho mỗi subtask.
2. **`fork(Callable)`:** chạy subtask trong thread riêng, trả về đối tượng `Subtask` (một số ví dụ dùng kiểu `Supplier<T>` vì `Subtask<T>` cũng cung cấp `get()`). Sau `fork`, hai công việc có thể tiến triển đồng thời.
3. **`join()` / `joinUntil(Instant)`:** owner chờ kết quả theo chính sách của scope. `joinUntil` áp dụng một hạn chờ tuyệt đối và có thể ném `TimeoutException`.
4. **Xem kết quả và lỗi:** với chính sách yêu cầu tất cả thành công, `throwIfFailed()` giúp không đọc kết quả như thể mọi thứ đều ổn khi có task thất bại.
5. **`close()`:** `try-with-resources` đóng scope, chờ các thread con còn sống kết thúc khi cần. Không được hiểu `close` là hủy cưỡng chế một tác vụ không chịu kết thúc.

Code khung cho Java 21:

~~~java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var first = scope.fork(() -> loadFirst());
    var second = scope.fork(() -> loadSecond());

    scope.join().throwIfFailed();

    return combine(first.get(), second.get());
}
~~~

Đoạn trên **chỉ minh họa**: `loadFirst()`, `loadSecond()` và `combine(...)` là các hàm của ứng dụng, chưa được định nghĩa trong đoạn mã. Phần 4 có chương trình Java 21 độc lập và stdout được xác định.

Khác với việc gọi `Thread.startVirtualThread(...)` trực tiếp rồi bỏ tham chiếu thread, scope thiết lập mối quan hệ cha/con và điểm chờ chung. Khác với một lệnh gọi tuần tự, các subtask trong scope vẫn có thể chạy song song với nhau.

### Scope là phạm vi sở hữu, không chỉ là danh sách thread

- Thread tạo scope được gọi là **owner**; các thao tác điều phối như `join` và `close` phải do owner thực hiện.
- Scope có thể **lồng nhau**: subtask của scope cha có thể mở scope con, tạo cấu trúc công việc dạng cây.
- Nếu scope được dùng với `try-with-resources`, khi rời khối do thành công hoặc exception, `close` vẫn được gọi.
- Sau khi `join` thành công và xử lý các kết quả cần thiết, không còn lý do nghiệp vụ để giữ scope sống.
- Việc đóng scope giúp không bỏ quên các thread thuộc phạm vi, nhưng chỉ có thể hoàn tất nhanh nếu các tác vụ con phản hồi với gián đoạn/hủy.

## 3. Hai chính sách quan trọng của Java 21

Java 21 cung cấp hai subclass preview để mô tả quy tắc kết thúc nhóm công việc.

| Khía cạnh | `ShutdownOnFailure` | `ShutdownOnSuccess<T>` |
|---|---|---|
| Bài toán thích hợp | **Cần tất cả kết quả**; một task lỗi làm cả thao tác thất bại | **Chỉ cần một kết quả thành công** từ nhiều phương án |
| Khi nào short-circuit | Khi có subtask đầu tiên thất bại | Khi có subtask đầu tiên thành công |
| Hành vi với các subtask còn lại | Yêu cầu shutdown, **interrupt** thread chưa xong | Yêu cầu shutdown, **interrupt** thread chưa xong |
| `join()` | Chờ tất cả hoàn tất hoặc có thất bại khiến scope shutdown | Chờ một subtask thành công hoặc tất cả đều hoàn tất |
| Lấy kết quả | Sau `join().throwIfFailed()`, đọc `Supplier.get()` của từng subtask | Sau `join()`, gọi `scope.result()` |
| Mọi subtask đều thất bại | `throwIfFailed()` ném lỗi | `result()` ném lỗi vì không có kết quả thành công |
| Hợp đồng ý nghĩa | Các thành phần kết quả phải nhất quán, thiếu một thành phần là lỗi | Những lời giải thay thế; một lời giải đủ đáp ứng |

### Khi phải nhận đủ kết quả: `ShutdownOnFailure`

Ví dụ tạo hồ sơ gồm tên và điểm. Nếu truy vấn tên thất bại, việc dùng điểm đơn lẻ không tạo được hồ sơ hợp lệ. `ShutdownOnFailure` chủ động báo thất bại và yêu cầu dừng công việc còn lại.

~~~java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var name = scope.fork(() -> "Lan");
    var points = scope.fork(() -> 120);

    scope.join().throwIfFailed();
    String summary = name.get() + ": " + points.get();
    System.out.println(summary);
}
~~~

Sau `join().throwIfFailed()`, hai subtask đã thành công thì mới dùng `get()`. Với API Java 21, `throwIfFailed()` ném `ExecutionException` khi có subtask thất bại; thông tin gốc thường nằm trong `getCause()`. Trái lại, bản `throwIfFailed(Function<Throwable, ? extends X>)` cho phép tự tạo exception để thích hợp với hợp đồng ứng dụng.

### Khi chỉ cần một kết quả tốt: `ShutdownOnSuccess<T>`

Ví dụ đọc dữ liệu tương đương từ cache và nguồn dự phòng:

~~~java
try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
    scope.fork(() -> readCache());
    scope.fork(() -> readReplica());

    scope.join();
    return scope.result();
}
~~~

`readCache()` và `readReplica()` ở đây là các hàm minh họa đã có trong ứng dụng. Kết quả là **tác vụ hoàn thành thành công đầu tiên**, không có nghĩa là tác vụ được `fork` đầu tiên, cũng không phải tác vụ *kết thúc đầu tiên nếu nó thất bại*. Nếu một task lỗi trước, scope tiếp tục chờ cơ hội thành công ở task còn lại. Nếu tất cả đều thất bại, `scope.result()` ném `ExecutionException`. Những lời gọi đọc khác bị yêu cầu dừng khi một lời gọi thành công, nhưng không có đảm bảo chúng ngừng tức thời.

**Chọn policy theo nghiệp vụ trước khi chọn cách chạy thread.** Đối với một yêu cầu cần cả A và B, dùng `ShutdownOnSuccess` là sai nghĩa; với hai nguồn thay thế chỉ cần một, `ShutdownOnFailure` có thể thất bại quá sớm khi nguồn còn lại vẫn hoạt động.

## 4. Chương trình Java 21 độc lập, có kiểm chứng

Tạo file **`StructuredConcurrencyDemo.java`**:

~~~java
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public class StructuredConcurrencyDemo {
    static String combine() throws InterruptedException, ExecutionException {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Supplier<String> name = scope.fork(() -> "Lan");
            Supplier<Integer> points = scope.fork(() -> 120);
            scope.join().throwIfFailed();
            return name.get() + ": " + points.get();
        }
    }

    static String firstSuccess() throws InterruptedException, ExecutionException {
        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
            scope.fork(() -> "cache");
            scope.fork(() -> {
                Thread.sleep(1000);
                return "remote";
            });
            scope.join();
            return scope.result();
        }
    }

    static String firstFailure() throws InterruptedException {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            scope.fork(() -> { throw new IllegalStateException("bad data"); });
            scope.fork(() -> {
                Thread.sleep(1000);
                return "slow";
            });
            try {
                scope.join().throwIfFailed();
                return "unexpected";
            } catch (ExecutionException ex) {
                return ex.getCause().getClass().getSimpleName();
            }
        }
    }

    static String timeout() throws InterruptedException, ExecutionException {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Supplier<String> slow = scope.fork(() -> {
                Thread.sleep(500);
                return "late";
            });
            try {
                scope.joinUntil(Instant.now().plusMillis(20));
                scope.throwIfFailed();
                return slow.get();
            } catch (TimeoutException ex) {
                scope.shutdown();
                scope.join();
                return "timeout";
            }
        }
    }

    static boolean usesVirtualThread() throws InterruptedException, ExecutionException {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Supplier<Boolean> child = scope.fork(
                    () -> Thread.currentThread().isVirtual());
            scope.join().throwIfFailed();
            return child.get();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println(combine());
        System.out.println(firstSuccess());
        System.out.println(firstFailure());
        System.out.println(timeout());
        System.out.println(usesVirtualThread());
    }
}
~~~

Dùng **JDK 21 thực sự** để biên dịch và chạy preview API:

~~~bash
javac --enable-preview --release 21 -encoding UTF-8 StructuredConcurrencyDemo.java
java --enable-preview StructuredConcurrencyDemo
~~~

**stdout** của chương trình:

~~~text
Lan: 120
cache
IllegalStateException
timeout
true
~~~

Trình biên dịch Java 21 có thể in thêm thông báo `Note: ... uses preview features of Java SE 21` ra kênh thông báo; đó không phải stdout của chương trình.

Đọc lần lượt năm tình huống:

1. **`combine()`**: hai callable cùng hoàn thành thành công; kết quả được ghép sau `join().throwIfFailed()`, in `Lan: 120`.
2. **`firstSuccess()`**: callable cache trả về tức thì, callable kia ngủ 1 giây; kết quả thắng là `cache`, callable chậm nhận tín hiệu interrupt khi scope shutdown.
3. **`firstFailure()`**: một callable ném `IllegalStateException`; `throwIfFailed()` ném `ExecutionException` có `cause` tương ứng; kết quả in tên exception gốc.
4. **`timeout()`**: chờ đến một `Instant` cách thời điểm gọi khoảng 20 ms, trong khi task ngủ 500 ms. Khi hết hạn, code gọi `shutdown()`, `join()` để bảo đảm đường đóng scope rồi trả `timeout`.
5. **`usesVirtualThread()`**: thread được scope Java 21 tạo theo mặc định là virtual thread; vì vậy in `true`.

Để stdout ổn định, chương trình không in từ các subtask và không phụ thuộc vào thứ tự completion của hai kết quả khác nhau. `Thread.sleep` phản hồi với interrupt, nên nhánh short-circuit và đóng scope hoàn tất bình thường. Trong thực tế, một tác vụ I/O không phản hồi hủy có thể khiến việc đóng scope tốn nhiều thời gian hơn.

**Không biên dịch đoạn này bằng JDK 17:** cả virtual threads (`Thread.isVirtual()`) và Structured Concurrency preview của Java 21 đều chưa có ở đó. Lệnh `--enable-preview --release 21` cũng cần chạy với trình biên dịch **JDK 21**; không thể yêu cầu javac ở một JDK mới biên dịch preview của bản 21 bằng cách thay riêng `--release`.

## 5. `join`, ngoại lệ và tính đúng đắn

### `join()` không có nghĩa là mọi task thành công

Mục đích của `join()` là đưa owner tới điểm đồng bộ của scope. Với `ShutdownOnFailure`, nó có thể kết thúc khi một tác vụ thất bại, trước khi các tác vụ khác đã thực sự ngừng hoạt động. Vì vậy gọi `scope.join()` rồi lấy tất cả `get()` ngay mà không kiểm tra chính sách lỗi có thể dẫn tới suy luận sai.

Mẫu đúng cho trường hợp “cần tất cả” là:

~~~java
scope.join().throwIfFailed();
var a = first.get();
var b = second.get();
~~~

`throwIfFailed()` kiểm tra lỗi mà policy đã ghi nhận. Khi nó ném exception, code phía sau không chạy. Lỗi trong callable vẫn là lỗi nghiệp vụ thực tế; structured concurrency giúp truyền và nhóm hóa theo vòng đời, không tự sửa lỗi hay đưa ra fallback.

### `joinUntil(Instant)` chỉ giới hạn thời gian chờ

~~~java
try {
    scope.joinUntil(Instant.now().plusSeconds(2));
    scope.throwIfFailed();
} catch (TimeoutException ex) {
    scope.shutdown();
    scope.join();
    throw ex;
}
~~~

Đoạn này minh họa bên trong một `try` đã mở `ShutdownOnFailure scope`, là mã **không độc lập**. `joinUntil` nhận **thời điểm tuyệt đối** `Instant`, không nhận thời lượng `Duration`. Nếu hết hạn, nó ném `TimeoutException`, và ứng dụng cần quyết định chính sách hủy/dọn dẹp. `scope.shutdown()` yêu cầu interrupt các thread chưa hoàn thành, còn `scope.join()` giúp owner đáp ứng trình tự điều phối trước khi đóng scope.

Hạn chờ **không phải lời hứa** phương thức cha luôn trả trong đúng hai giây: `close()` vẫn chờ các task sống kết thúc, kể cả task không phản hồi interrupt. Với các giao tiếp I/O bên ngoài, nên thiết lập deadline/timeout riêng ở client hoặc API thao tác thật sự.

### `InterruptedException` là tín hiệu điều khiển

`join()`, `joinUntil(...)` và các hoạt động chờ có thể ném `InterruptedException`. Nếu thread owner bị interrupt, một `try-with-resources` vẫn gọi `close` để kết thúc phạm vi. Nếu mã bắt `InterruptedException` mà không truyền tiếp, cần khôi phục interrupt flag khi phù hợp:

~~~java
try {
    doInterruptibleWork();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
}
~~~

Đoạn này chỉ minh họa cách xử lý khi `doInterruptibleWork()` là tác vụ hỗ trợ interrupt trong một phương thức trả `void`. Nếu method có thể khai báo `throws InterruptedException`, thường nên truyền exception lên caller. Đừng nuốt interrupt rồi tiếp tục một vòng lặp vô hạn; các scope đang yêu cầu shutdown sẽ phải đợi lâu hoặc không bao giờ kết thúc.

### Owner và thứ tự thao tác

Các phương thức `join`/`joinUntil`/`close` dành cho thread owner. Một số lời gọi vào scope từ thread không hợp lệ có thể bị từ chối. Không chuyển đối tượng scope cho một request độc lập rồi cố đóng từ thread khác. Các thao tác dùng `Subtask.get()` và `Subtask.exception()` cũng có quy tắc kiểm tra trạng thái/owner; trong ví dụ thông thường, hãy đợi `join` và xác thực thành công trước khi đọc kết quả.

Nếu fork rồi thoát `try` mà bỏ qua `join`, đó không phải cách dùng đúng: Java 21 áp dụng kiểm tra cấu trúc và `close()` có thể báo `IllegalStateException`. Tổ chức code sao cho mọi nhánh bình thường đều tới bước join, còn nhánh lỗi/hết hạn xử lý shutdown và cleanup rõ ràng.

## 6. Hủy trong scope: hợp tác, không phải cưỡng bức

`StructuredTaskScope.shutdown()` được thiết kế để:

1. Ngăn việc khởi động các task mới trong scope đã shutdown.
2. Gửi **interrupt** đến các thread con đang chạy và chưa kết thúc (trừ thread hiện tại trong các trường hợp thích hợp).
3. Đánh thức owner đang đợi trong `join`/`joinUntil` để xử lý trạng thái nhóm.

**Interrupt là yêu cầu hợp tác.** Nếu subtask gọi `Thread.sleep`, `BlockingQueue.take` hoặc API hỗ trợ interrupt, nó thường có thể thoát nhanh. Nếu subtask đang thực hiện code bỏ qua interrupt, vòng lặp không kiểm tra cờ interrupt, hoặc I/O không có cơ chế hủy hữu hiệu, thread có thể tiếp tục chạy. Khi đó `close()` phải đợi.

Ví dụ một subtask được thiết kế hợp tác với hủy:

~~~java
static long countUntilInterrupted() {
    long count = 0;
    while (!Thread.currentThread().isInterrupted()) {
        count++;
        // Trong thực tế, xen kẽ công việc hữu ích và kiểm tra hủy.
    }
    return count;
}
~~~

Hàm này chạy độc lập như một phương thức static nhưng **không có điểm dừng tự động**; nó chỉ thoát khi thread thực sự bị interrupt. Không dùng vòng lặp CPU như vậy trong ứng dụng thật để chờ điều kiện; ở đây nó chỉ cho thấy cờ `isInterrupted()`. Tác vụ thật phải tránh busy-wait và đảm bảo dọn tài nguyên trong `finally` hoặc `try-with-resources` của chính nó.

Giả sử một callable đã gửi yêu cầu ghi dữ liệu sang dịch vụ khác. Sau khi sibling thất bại, scope có thể interrupt callable đang đợi phản hồi nhưng **không rollback giao dịch bên ngoài**. Hủy thread Java cũng không đồng nghĩa phía xa ngừng xử lý. Vì vậy, với thao tác có side effect, cần cơ chế nghiệp vụ phù hợp: idempotency, transaction, deadline ở I/O, hoặc trạng thái bù trừ.

## 7. Quan hệ với Virtual Threads và các API concurrency khác

**Virtual Threads** và **Structured Concurrency** giải quyết hai vấn đề khác nhau:

- **Virtual thread:** cung cấp thread nhẹ hơn nhiều thread hệ điều hành trong các workload blocking I/O phù hợp; giúp có thể tạo rất nhiều thread với phong cách đồng bộ.
- **Structured Concurrency:** quản lý **quan hệ vòng đời, kết quả, lỗi và hủy** giữa một nhóm subtask. Nó có thể dùng virtual thread mặc định nhưng ý tưởng cấu trúc không phụ thuộc riêng vào virtual thread.

Trong Java 21, constructor mặc định của `StructuredTaskScope` sử dụng virtual thread. Constructor nhận `ThreadFactory` cho phép chọn kiểu thread theo nhu cầu. Tuy nhiên, `Thread.ofVirtual()` tự nó **không** cung cấp scope, không gom lỗi hay đợi các thread con một cách có cấu trúc.

| Công cụ | Chức năng chính | Điều cần tự quản lý |
|---|---|---|
| `Thread.startVirtualThread(...)` | Bắt đầu một virtual thread | Chờ thread, truyền lỗi, hủy và ownership giữa parent/child |
| `Executors.newVirtualThreadPerTaskExecutor()` | Thực thi các task, một virtual thread mỗi task | Quy ước nhóm tác vụ, chọn kết quả, xử lý lỗi và hủy siblings |
| `CompletableFuture` | Biểu diễn và ghép pipeline bất đồng bộ, callback, exception | Quan hệ lexical giữa task cha/con, hủy thực thi nền, ownership |
| `StructuredTaskScope` (Java 21 preview) | Gom các subtask liên quan thành scope với chính sách join/shutdown | Logic nghiệp vụ, timeout I/O, giới hạn tải và tác dụng phụ |

Có thể dùng `CompletableFuture` cho pipeline/đồ thị phụ thuộc dài, phản hồi async qua nhiều lớp, hoặc callback trên những sự kiện bên ngoài. Structured Concurrency hữu ích nhất khi một thao tác **chia thành nhiều công việc con rồi phải tập hợp lại trước khi kết thúc phạm vi**. Không cần chuyển mọi future và executor thành scope; chọn cấu trúc có thể diễn đạt hợp đồng vòng đời rõ nhất.

### Structured Concurrency không phải cơ chế hạn chế đồng thời

Việc fork hàng nghìn subtask bằng virtual thread không có nghĩa database hoặc API ngoài có thể xử lý hàng nghìn truy vấn đồng thời. **Giới hạn kết nối, quota, pool, rate limit và backpressure** vẫn là trách nhiệm thiết kế. Scope tổ chức các task, không tự quyết định số lượng truy vấn đồng thời an toàn. Với fan-out rất lớn, cần cơ chế giới hạn tải bên cạnh scope.

### Quan hệ với Scoped Values

Cấu trúc cha/con cho phép các virtual thread con nhận ngữ cảnh `ScopedValue` được bind tại điểm tạo scope theo quy tắc Java 21. `ScopedValue` cũng là API preview ở Java 21; có ích khi truyền dữ liệu immutable theo phạm vi gọi. Đây không phải cơ chế chia sẻ biến mutable tự động, và cũng không thay thế việc đồng bộ hóa trạng thái chung giữa các subtask.

## 8. Giới hạn cụ thể của Structured Concurrency preview ở Java 21

### 8.1. API vẫn đang thử nghiệm

- Java 21 giới thiệu preview theo **JEP 453**. Muốn sử dụng, mã nguồn cần biên dịch và chương trình cần chạy với `--enable-preview` tương ứng.
- Preview có thể đổi API, điều kiện sử dụng hoặc bị rút ở bản sau. Một ứng dụng dùng API preview phải được kiểm tra lại khi nâng cấp JDK.
- Đừng viết tài liệu hướng dẫn người học Java 21 bằng chữ ký API của JDK mới. Chẳng hạn, JDK 25 có giao diện preview `StructuredTaskScope<T,R>` và phương thức `StructuredTaskScope.open(...)`; trong khi ví dụ **JDK 21** ở bài này dùng `new StructuredTaskScope.ShutdownOnFailure()` và `new StructuredTaskScope.ShutdownOnSuccess<T>()`. Chúng thuộc các phiên bản API khác nhau.
- Chạy `java -version` và `javac -version` để chắc chắn trình chạy và trình biên dịch phù hợp với cú pháp preview đang dùng.

### 8.2. Hủy không bảo đảm tức thời

`shutdown()` chủ yếu interrupt thread con. `close()` có thể chờ code chưa hợp tác với interrupt. Không có API nào trong scope bảo đảm giết an toàn một lệnh ghi ngoài hệ thống, dừng native call, hay hoàn nguyên side effect.

### 8.3. Không có “fire-and-forget” theo thiết kế

Nếu một công việc cần tiếp tục sống độc lập sau khi thao tác cha trả về, đừng cố giữ nó bên trong một scope rồi bỏ qua join. Đây không phải bài toán structured fan-out/fan-in. Hãy dùng một thành phần có vòng đời rõ ràng hơn (ví dụ executor/service quản lý riêng), cùng chính sách shutdown/monitoring phù hợp.

### 8.4. Không tự thay thế tất cả cơ chế điều phối

Scope không là bộ điều độ hàng đợi, không giới hạn băng thông, không retry tự động, không đảm bảo thứ tự hoàn thành, không biến các callable thành transaction phân tán. Nó cũng không làm CPU-bound task chạy nhanh hơn số core; mức song song thực tế còn phụ thuộc CPU, I/O và tài nguyên ngoài JVM.

### 8.5. Không tự bảo đảm an toàn dữ liệu chia sẻ

Hai subtask ghi vào cùng mutable object vẫn có thể race condition. Dùng immutable data, đồng bộ hóa, concurrent collection hoặc cơ chế chia sẻ trạng thái khác khi cần. `join` giúp xác định điểm hoàn tất và quan hệ happens-before; nó không biến thao tác update không an toàn thành atomic.

### 8.6. Scope không là API portable giữa mọi bản JDK

Đối với thư viện cần hỗ trợ nhiều bản Java, nhất là dự án có baseline thấp hơn Java 21, phụ thuộc vào preview có chi phí rõ ràng. Cần quyết định version support trước khi chọn API. Nếu không chấp nhận preview trong sản phẩm, có thể dùng `ExecutorService`, `Future`, `CompletableFuture` hoặc nền tảng tương thích và tự quản lý vòng đời, tuy nhiều bước hơn.

## 9. Cách áp dụng có kiểm soát

Một quy trình thiết kế thực tế:

1. **Xác định unit of work:** ví dụ một lần dựng báo cáo, một lượt tổng hợp kết quả; xác định đúng task cha và các subtask phải sống cùng nó.
2. **Chọn chính sách:** tất cả kết quả đều bắt buộc (failure làm cả yêu cầu thất bại), hay chỉ cần một nguồn thành công.
3. **Dự trù deadline:** xác định tổng ngân sách thời gian, và timeout của các tài nguyên I/O. `joinUntil` giúp giới hạn chờ, nhưng tính cả thời gian cleanup.
4. **Thiết kế hủy hợp tác:** callable phải phản hồi interrupt, có `try/finally` để đóng tài nguyên, không che nguyên nhân lỗi.
5. **Giới hạn fan-out:** khi số task lớn hoặc nguồn có quota thấp, đặt thêm giới hạn đồng thời thay vì fork không kiểm soát.
6. **Chỉ trả kết quả sau khi có trạng thái hợp lệ:** `join` rồi `throwIfFailed` hoặc `result` tùy policy; không dùng một giá trị chưa chắc thành công.
7. **Quan sát hoạt động:** ghi thời gian của thao tác cha, số task lỗi/hủy, nguồn dữ liệu; có thể dùng thông tin cây thread trong thread dump của JDK để tìm các nhóm subtask đang chạy.
8. **Kiểm tra đúng JDK:** xác minh source Java 21 preview bằng JDK 21 khi build, rồi kiểm tra lại trong kế hoạch nâng cấp phiên bản.

Với scope lồng nhau, yêu cầu hủy từ ancestor có thể lan xuống các subtask thông qua interruption và cấu trúc scope; nhưng **thời gian kết thúc phụ thuộc subtask có phản hồi với hủy hay không**. Thiết kế mỗi tầng như một owner có trách nhiệm, tránh để scope con giữ các thao tác sống quá thời gian mong muốn.

### Các lỗi dễ gặp

- **Gọi `fork` nhưng không `join`:** sai vòng đời, có thể gây exception khi đóng scope.
- **Dùng `ShutdownOnSuccess` cho kết quả cần đủ các thành phần:** cho phép trả sớm với dữ liệu không đầy đủ.
- **Quên `throwIfFailed`:** bỏ qua lỗi trong một task đã fork, sau đó vẫn coi aggregate thành công.
- **Giả định `close()` luôn trả tức thời:** task không hợp tác interrupt có thể làm kẹt thao tác đóng.
- **Bắt và nuốt `InterruptedException`:** khiến hủy chậm hoặc không hoạt động.
- **Chạy preview Java 21 trên compiler/runtime khác release:** gặp lỗi thiếu API hoặc cú pháp không khớp.
- **Tưởng virtual threads loại bỏ race condition:** virtual thread vẫn truy cập memory chung theo quy tắc Java Memory Model.
- **Fork quá nhiều truy vấn mà không giới hạn:** dễ làm cạn connection pool hoặc vượt quota nguồn.
- **Nhầm timeout với rollback:** deadline không hoàn nguyên ghi dữ liệu đã thực hiện ở hệ thống ngoài.

## 10. Câu hỏi tự kiểm tra

**1. Hai tác vụ A, B đều bắt buộc để tạo kết quả, B thất bại. Dùng policy nào?**  
`ShutdownOnFailure`, vì thao tác cần cả hai; lỗi ở B khiến yêu cầu dừng công việc A không còn cần thiết và báo thất bại. A có thể cần thời gian để phản hồi interrupt.

**2. Ba máy chủ trả cùng dữ liệu, chỉ cần một phản hồi thành công. Nếu máy thứ nhất thất bại nhưng máy thứ hai thành công thì sao?**  
`ShutdownOnSuccess` tiếp tục chờ cho tới khi có một thành công; `result()` trả kết quả thắng. Lỗi của máy thứ nhất không mặc nhiên khiến scope thất bại.

**3. `joinUntil` ném `TimeoutException` có đảm bảo tất cả task đã ngừng?**  
Không. Owner cần quyết định `shutdown` và hoàn tất cleanup. `close` vẫn có thể phải đợi task không chịu dừng.

**4. Dùng `Thread.startVirtualThread` có tự tạo structured concurrency không?**  
Không. Virtual thread là loại thread; structured concurrency cung cấp ràng buộc vòng đời và chính sách cho nhóm task.

**5. Có thể copy nguyên ví dụ dùng constructor Java 21 sang Java 25 rồi bật preview là chạy được không?**  
Không nên giả định như vậy: API preview đã thay đổi. Phải tham khảo đúng Java API Specification và JEP của phiên bản đang dùng, sửa code và chạy kiểm thử.

## 11. Tài liệu gốc để đối chiếu phiên bản

- [Oracle Java 21 — Structured Concurrency](https://docs.oracle.com/en/java/javase/21/core/structured-concurrency.html): hướng dẫn mô hình `fork`, `join`, `close` và chính sách kết thúc.
- [Java 21 API — StructuredTaskScope](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/StructuredTaskScope.html): chữ ký và semantics của API preview đúng Java 21.
- [OpenJDK JEP 453 — Structured Concurrency (Preview)](https://openjdk.org/jeps/453): bối cảnh và mục tiêu thiết kế Java 21.
- [Java 25 API — StructuredTaskScope](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.html): đọc khi so sánh quá trình thay đổi API qua phiên bản.

Structured Concurrency làm rõ **ai sở hữu các task, khi nào kết quả được dùng, ai chịu trách nhiệm khi lỗi hoặc bị hủy**. Sức mạnh chính của nó là phạm vi vòng đời có cấu trúc; tính đúng đắn tổng thể vẫn phụ thuộc vào policy phù hợp, hủy hợp tác, giới hạn tài nguyên và những ràng buộc nghiệp vụ thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-014">Thread.ofVirtual().unstarted(), ThreadFactory và vòng đời khởi chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Thread.ofVirtual().unstarted(), ThreadFactory và vòng đời khởi chạy

## 1. Mục tiêu và bối cảnh

Virtual thread là `java.lang.Thread` do JVM quản lý lịch chạy, phù hợp với khối lượng công việc có nhiều tác vụ chờ I/O. Virtual thread được đưa vào Java như tính năng chính thức từ **Java 21** (JEP 444). Vì thế ví dụ chạy độc lập trong bài dùng **JDK 21 trở lên**, không yêu cầu `--enable-preview`. Đây là kiến thức concurrency thuần Java; tất cả thao tác đều dùng JDK, không phụ thuộc Spring hay cơ chế async của framework.

Câu hỏi trung tâm: **khi nào một virtual thread mới chỉ được tạo, khi nào nó thật sự được khởi chạy, và `ThreadFactory` đóng vai trò gì?**

Sau bài học, bạn có thể:

- Dùng `Thread.ofVirtual().unstarted(task)` để tạo một đối tượng `Thread` ở trạng thái `NEW`, rồi chủ động gọi `start()`.
- Phân biệt `unstarted(task)`, `start(task)`, `Thread.startVirtualThread(task)` và `ThreadFactory.newThread(task)`.
- Tạo `ThreadFactory` tái sử dụng từ `Thread.ofVirtual().factory()`, tùy chỉnh tên để quan sát được từng tác vụ.
- Theo dõi `NEW`, `RUNNABLE`, `WAITING`, `TIMED_WAITING`, `BLOCKED` và `TERMINATED` mà không nhầm trạng thái Java với trạng thái chạy trên CPU.
- Quản lý vòng đời bằng `join()`, `interrupt()`, `Future` và executor; tránh quên `start()`, gọi `run()` trực tiếp hoặc để ứng dụng thoát trước khi tác vụ hoàn thành.

## 2. Virtual thread có gì khác platform thread?

`Thread.ofPlatform()` tạo builder cho **platform thread** thường gắn với luồng hệ điều hành. `Thread.ofVirtual()` tạo builder cho **virtual thread**: Java runtime có thể ghép nhiều virtual thread lên số lượng nhỏ hơn các platform thread dùng làm *carrier*. Khi virtual thread chờ tại những thao tác blocking hỗ trợ cơ chế này, JVM thường có thể tháo nó khỏi carrier để carrier phục vụ tác vụ khác.

Điểm cốt lõi: virtual thread vẫn là một `Thread` với `Runnable`, `start()`, `join()`, `interrupt()`, `getState()`, `isAlive()`, `ThreadLocal` và stack trace. Nó giúp lập trình theo kiểu tuần tự dễ đọc trong tác vụ chờ I/O, nhưng **không làm CPU-bound code chạy nhanh hơn một cách tự động**, không khiến lệnh blocking biến mất, và không bảo đảm thứ tự thực thi.

```java
Runnable task = () ->
        System.out.println(Thread.currentThread().isVirtual());

Thread virtual = Thread.ofVirtual().unstarted(task);
Thread platform = Thread.ofPlatform().unstarted(task);
```

Đoạn trên chỉ minh họa API, chưa khởi chạy hai thread. Cả hai đều chưa thực hiện `task`. `Thread.currentThread().isVirtual()` phải được kiểm tra **bên trong task đang chạy** nếu muốn quan sát loại thread thực thi, vì thread tạo task và thread chạy task có thể khác nhau.

Một virtual thread trên JDK 21 là **daemon thread**; không thể chuyển thành non-daemon để giữ JVM sống. Vì vậy một chương trình console cần `join()` hoặc cơ chế chờ hoàn tất có chủ đích. Đừng dựa vào việc JVM sẽ tự chờ những virtual thread chưa hoàn thành.

## 3. `Thread.ofVirtual().unstarted(task)`: tạo trước, chạy sau

```java
Runnable task = () -> System.out.println("work");
Thread worker = Thread.ofVirtual()
        .name("read-catalog")
        .unstarted(task);               // tạo Thread, chưa chạy Runnable
```

Ngay sau `unstarted`:

- `worker.isVirtual()` là `true`.
- `worker.getState()` là `Thread.State.NEW`.
- `worker.isAlive()` là `false`.
- Chưa có lời gọi `task.run()` từ thread này, và chưa có tác vụ nào được lịch chạy chỉ vì đối tượng `Thread` đã tồn tại.
- `worker.getName()` là `read-catalog`, tên được đặt để quan sát hoặc chẩn đoán.

Để đưa tác vụ vào lịch chạy:

```java
worker.start(); // chỉ được gọi một lần
worker.join();  // thread hiện tại đợi worker kết thúc
```

`start()` yêu cầu JVM bắt đầu thực thi `Runnable` trên **một thread riêng**; phương thức `start()` không có nghĩa phải chờ task kết thúc mới trả về. `join()` đợi cho thread kết thúc (hoặc bị ngắt khi thread đang đợi, tùy overload), nên phù hợp ở ranh giới cần kết quả.

**Không dùng `worker.run()` thay `worker.start()`.** Đối với virtual thread, gọi trực tiếp phương thức `Thread.run()` từ thread khác **không khởi chạy virtual thread**, và implementation mặc định của `Thread.run()` trên virtual thread không chạy task theo cách bạn mong đợi. Với platform thread, gọi trực tiếp `run()` cũng không tạo luồng độc lập, vì vậy nguyên tắc chung vẫn là gọi `start()` để tạo concurrency.

Một `Thread` chỉ được `start()` **tối đa một lần**. Gọi lần hai, kể cả sau `TERMINATED`, dẫn tới `IllegalThreadStateException`. Muốn chạy lại cùng logic, hãy tạo **một `Thread` mới** dùng cùng `Runnable` hoặc một task mới. Không thể tái sử dụng instance `Thread` đã kết thúc.

## 4. API nhanh để so sánh

| API | Kết quả tạo ra | Đã khởi chạy khi API trả về? | Khi nào dùng |
| --- | --- | --- | --- |
| `Thread.ofVirtual().unstarted(task)` | Một `Thread` virtual | **Chưa**; trạng thái `NEW` | Cần đặt tên/cấu hình và giữ quyền chủ động gọi `start()` |
| `Thread.ofVirtual().start(task)` | Một `Thread` virtual | **Đã yêu cầu khởi chạy** | Muốn tạo và start ngay |
| `Thread.startVirtualThread(task)` | Một `Thread` virtual | **Đã yêu cầu khởi chạy** | Viết ngắn, không cần tùy chỉnh builder |
| `Thread.ofVirtual().factory()` | Một `ThreadFactory` | Chưa có thread công việc | Muốn tạo nhiều thread theo cùng cấu hình |
| `factory.newThread(task)` với factory trên | Một `Thread` virtual | **Chưa**; cần gọi `start()` | Tạo từng thread thủ công hoặc giao cho executor |
| `Executors.newVirtualThreadPerTaskExecutor()` | `ExecutorService` | Task do executor nhận sẽ được executor khởi chạy | Tổ chức nhiều tác vụ, quản lý kết quả/hủy/đóng executor |

`Thread.ofVirtual().start(task)` về ý nghĩa tạo và khởi chạy tương đương việc tạo bằng `unstarted(task)` rồi gọi `start()`, nhưng bạn không có khoảng trống để quan sát/thiết lập một object chưa chạy. Cả hai cách đều không bảo đảm task đã **hoàn tất** khi lời gọi trả về.

`Thread.startVirtualThread(task)` là tiện ích tĩnh Java 21, tương đương `Thread.ofVirtual().start(task)`. Nó **không** trả về `Future`; để chờ task kết thúc, bạn có thể giữ `Thread` được trả về và `join()`.

## 5. `ThreadFactory`: chính sách tạo thread có thể tái sử dụng

`java.util.concurrent.ThreadFactory` là functional interface với phương thức `Thread newThread(Runnable runnable)`. Theo contract, factory tạo ra một **thread chưa start**, cho phép người gọi hoặc executor quyết định thời điểm chạy.

```java
ThreadFactory factory = Thread.ofVirtual()
        .name("io-vt-", 0)
        .factory();

Thread t0 = factory.newThread(() -> downloadChunk());
Thread t1 = factory.newThread(() -> downloadChunk());

// Đến đây cả hai còn NEW; downloadChunk() là hàm minh họa.
t0.start();
t1.start();
```

Đây là **mảnh mã minh họa** vì `downloadChunk()` chưa được định nghĩa. Prefix cùng chỉ số bắt đầu `0` tạo tên `io-vt-0`, `io-vt-1`, ... cho những thread được factory cấp phát. `factory()` chụp cấu hình hiện tại của builder để tạo thread nhất quán. Factory trả về được thiết kế **thread-safe**, phù hợp cho nhiều caller; ngược lại, bản thân `Thread.Builder` không phải một đối tượng để nhiều thread cùng chỉnh cấu hình một cách tự do.

Factory do `Thread.ofVirtual().factory()` tạo sẽ trả virtual thread; `Executors.defaultThreadFactory()` là factory platform thread, vì vậy không được dùng thay nếu mục tiêu rõ ràng là virtual thread.

`ThreadFactory.newThread()` theo interface nói chung **có thể trả `null`** khi factory từ chối tạo thread; đây là khả năng của contract đối với factory tùy biến, không phải hành vi thông thường của factory JDK thu được từ `Thread.ofVirtual()`. Hãy xử lý phù hợp nếu nhận một factory do thư viện khác cung cấp.

### Cấu hình trên builder

```java
ThreadFactory factory = Thread.ofVirtual()
        .name("request-", 100)
        .inheritInheritableThreadLocals(false)
        .uncaughtExceptionHandler((thread, failure) ->
                System.err.println(thread.getName() + ": "
                        + failure.getClass().getSimpleName()))
        .factory();
```

Các thiết lập có mục đích riêng:

- `name(prefix, start)` tạo tên theo bộ đếm. `name("fixed")` dùng một tên cố định cho các thread tạo ra từ cấu hình đó; nếu cần phân biệt hàng loạt thread, nên dùng tiền tố và chỉ số.
- `inheritInheritableThreadLocals(false)` ngăn kế thừa giá trị ban đầu của `InheritableThreadLocal` từ thread tạo; cân nhắc khi tránh rò rỉ context giữa tác vụ. `ThreadLocal` vẫn được virtual thread hỗ trợ, nhưng mỗi task không nên dựa vào giả định tất cả thread có cùng context.
- `uncaughtExceptionHandler(...)` dùng để quan sát exception không được xử lý trong task chạy trực tiếp trên thread. Cơ chế `Future`/executor có thể thu exception theo contract của nó, vì vậy không dựa duy nhất vào handler để thu kết quả.
- Virtual thread vốn là daemon và có các hạn chế riêng về priority; `Thread.Builder.OfVirtual` không có chuỗi tùy chỉnh daemon như `Thread.Builder.OfPlatform`.

Đừng tạo ra một factory mới trong mỗi vòng lặp chỉ để đánh số tên: cấu hình một lần rồi dùng lại giúp chính sách đặt tên và loại thread nhất quán.

## 6. Vòng đời: `NEW` → lịch chạy → `TERMINATED`

```text
ofVirtual().unstarted(task) hoặc factory.newThread(task)
                         |
                         v
                     NEW
                         |
                      start()
                         v
           RUNNABLE <----------+
               |               |
    đang chạy hoặc sẵn sàng    |
               |               |
               +--> WAITING ---+
               |      (join/park...)
               +--> TIMED_WAITING -+
               |      (sleep/đợi có hạn)
               +--> BLOCKED --------+
                      (chờ monitor)
               |
        Runnable kết thúc
        (bình thường hoặc ném lỗi)
               |
               v
           TERMINATED
```

Các trạng thái `Thread.State` là **ảnh chụp thời điểm**, không phải một luồng chuyển trạng thái bắt buộc đi qua mọi nút. Một virtual thread vừa được `start()` có thể vẫn được quan sát ở `RUNNABLE`, hoặc đã chạy xong rất nhanh và trở thành `TERMINATED`. `RUNNABLE` **không đồng nghĩa** chắc chắn đang chiếm CPU; đó là trạng thái Java bao gồm sẵn sàng chạy và đang chạy.

- `NEW`: đã tạo instance nhưng chưa `start()`.
- `RUNNABLE`: đủ điều kiện thực thi hoặc đang được thực thi, kể cả khi đang chờ được lịch trên carrier.
- `WAITING`: đợi vô thời hạn một sự kiện, chẳng hạn lời gọi chờ thích hợp.
- `TIMED_WAITING`: đợi có hạn thời gian, ví dụ `Thread.sleep(...)`.
- `BLOCKED`: đợi giành Java monitor khi vào vùng `synchronized` do thread khác nắm giữ.
- `TERMINATED`: `Runnable.run()` đã trả về hoặc thoát vì uncaught exception. Trạng thái này là cuối cùng đối với một instance thread.

Không dùng `getState()` để phối hợp chính xác giữa các thread: ngay sau khi đọc state, thread có thể chuyển trạng thái. Muốn xác nhận hoàn tất, dùng `join()`, `Future.get()`, `CountDownLatch` hay cơ chế đồng bộ phù hợp.

`isAlive()` trả `false` trước khi `start()` và sau khi hoàn tất, `true` trong quãng đời đã start nhưng chưa kết thúc. Do đó **chỉ `isAlive() == false` không đủ để kết luận task đã chạy xong**: nó cũng đúng đối với thread chưa từng start.

### Các mốc xảy ra trước về mặt bộ nhớ (happens-before)

Theo Java Memory Model:

- Các hành động trong thread tạo trước lời gọi `worker.start()` *happen-before* các hành động của thread mới sau khi được start.
- Mọi hành động của thread đã kết thúc *happen-before* một thread khác hoàn tất việc `join()` tương ứng thành công.

Nhờ vậy, `join()` vừa chờ kết thúc, vừa giúp thread gọi nhìn thấy những ghi nhớ hợp lệ của task trước khi kết thúc. Tuy nhiên, **không dùng kết luận này để bỏ qua đồng bộ** nếu nhiều thread đang ghi/đọc cùng trạng thái *trong lúc chúng còn chạy*. Khi có cập nhật song song, vẫn dùng `volatile`, atomic, lock hoặc cấu trúc dữ liệu thread-safe tùy trường hợp.

## 7. Ví dụ 1: tự khởi chạy và quan sát trạng thái, factory tạo tên

Chương trình chạy độc lập dưới đây cho thấy cả `unstarted()` và `ThreadFactory.newThread()` đều tạo thread ở trạng thái `NEW`. Nó cố ý `join()` trước khi in bước kế tiếp để output xác định được. Lưu thành tệp `VirtualThreadLifecycleDemo.java`:

```java
import java.util.concurrent.ThreadFactory;

public class VirtualThreadLifecycleDemo {
    private static final Runnable TASK = () -> {
        Thread current = Thread.currentThread();
        System.out.println("run=" + current.getName()
                + ", virtual=" + current.isVirtual());
    };

    public static void main(String[] args) throws InterruptedException {
        Thread manual = Thread.ofVirtual()
                .name("manual-vt")
                .unstarted(TASK);
        System.out.println("manual-before=" + manual.getState());
        System.out.println("manual-alive=" + manual.isAlive());
        manual.start();
        manual.join();
        System.out.println("manual-after=" + manual.getState());

        ThreadFactory factory = Thread.ofVirtual()
                .name("factory-vt-", 1)
                .factory();
        Thread first = factory.newThread(TASK);
        Thread second = factory.newThread(TASK);
        System.out.println("factory-before=" + first.getState()
                + "," + second.getState());
        first.start();
        first.join();
        second.start();
        second.join();
        System.out.println("factory-after=" + first.getState()
                + "," + second.getState());
    }
}
```

Biên dịch và chạy với JDK 21+:

```bash
javac VirtualThreadLifecycleDemo.java
java VirtualThreadLifecycleDemo
```

Kết quả:

```text
manual-before=NEW
manual-alive=false
run=manual-vt, virtual=true
manual-after=TERMINATED
factory-before=NEW,NEW
run=factory-vt-1, virtual=true
run=factory-vt-2, virtual=true
factory-after=TERMINATED,TERMINATED
```

Tại sao kết quả xác định?

1. `manual` mới được `unstarted()` nên ở `NEW` và `isAlive()` là `false`.
2. `manual.start()` kích hoạt task; `manual.join()` đợi task in dòng `run=...` trước khi main đọc `TERMINATED`.
3. `factory.newThread(TASK)` hai lần cấp hai thread chưa chạy, với tên theo bộ đếm bắt đầu từ `1`.
4. `first.start(); first.join(); second.start(); second.join();` làm hai dòng in của task có thứ tự rõ ràng. Nếu thay bằng `first.start(); second.start(); first.join(); second.join();`, **thứ tự hai dòng in không được bảo đảm** dù hai task đều chạy đúng.
5. Không có yêu cầu `--enable-preview` vì Virtual Threads đã chuẩn hóa từ Java 21.

## 8. Ví dụ 2: cấp `ThreadFactory` cho executor

Khi có nhiều task cùng loại, thường nên giao vòng đời thread cho `ExecutorService`. Executor gọi `ThreadFactory.newThread(task)` rồi **tự `start()`** thread vừa tạo: client không được start thủ công những thread do executor quản lý.

Lưu vào `VirtualThreadExecutorDemo.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

public class VirtualThreadExecutorDemo {
    public static void main(String[] args) throws Exception {
        ThreadFactory factory = Thread.ofVirtual()
                .name("task-", 0)
                .factory();
        try (ExecutorService executor = Executors.newThreadPerTaskExecutor(factory)) {
            Future<String> a = executor.submit(() -> Thread.currentThread().getName());
            Future<String> b = executor.submit(() -> Thread.currentThread().getName());
            System.out.println(a.get());
            System.out.println(b.get());
        }
    }
}
```

Chạy:

```bash
javac VirtualThreadExecutorDemo.java
java VirtualThreadExecutorDemo
```

Kết quả:

```text
task-0
task-1
```

Mỗi `submit()` tạo một task và executor dùng factory để tạo một virtual thread mới. Tên được gán khi tạo thread; hai task có thể chạy/hoàn tất theo thứ tự bất kỳ, nhưng thứ tự **in** trong ví dụ xác định vì main gọi `a.get()` rồi `b.get()`. `Future.get()` trả kết quả hoặc báo `ExecutionException` cho lỗi task, có thể ném `InterruptedException` nếu thread chờ bị ngắt.

Khối `try-with-resources` gọi `ExecutorService.close()` khi thoát; ở Java 21, thao tác đóng này chờ các task đã nộp hoàn tất theo contract. Nếu không cần đặt tên riêng, dùng tiện ích:

```java
try (var executor = java.util.concurrent.Executors
        .newVirtualThreadPerTaskExecutor()) {
    // executor.submit(task) ...
}
```

Đây là **mảnh mã minh họa**: biến `task` và logic thu kết quả cần được định nghĩa theo bài toán. Cách này hợp với mô hình *một virtual thread cho mỗi task*; nó không tạo pool cố định gồm vài virtual thread để dùng lại. Nếu workload có hàng chục nghìn request I/O, vẫn phải giới hạn tài nguyên phía sau như kết nối cơ sở dữ liệu, số request đồng thời và quota API.

### Khi nào quản lý trực tiếp `Thread`, khi nào dùng executor?

| Nhu cầu | Công cụ phù hợp | Lý do |
| --- | --- | --- |
| Một tác vụ đơn giản, muốn thấy chính xác `NEW` rồi `start()` | `Thread.ofVirtual().unstarted()` | Chủ động vòng đời và quan sát trạng thái |
| Một tác vụ chạy ngay, không cần kiểm soát lúc start | `Thread.ofVirtual().start()` | Ngắn gọn |
| Nhiều thread cùng cấu hình/tên | `ThreadFactory` | Tập trung cấu hình, caller quyết định khởi chạy |
| Nhiều task trả giá trị hoặc cần hủy/thu lỗi | `ExecutorService` với factory hoặc virtual-thread-per-task | Tích hợp `Future`, shutdown và quản lý task |
| Công việc chủ yếu CPU-bound | Thiết kế giới hạn song song theo CPU và tài nguyên | Virtual thread không tự tăng khả năng CPU |

## 9. `join()`, interrupt, timeout và lỗi trong task

### `join()` chờ hoàn tất nhưng không trả exception của task

`Thread.join()` đợi thread kết thúc; nó **không ném lại** exception do `Runnable` ở thread con ném. Nếu cần nhận dữ liệu/lỗi làm kết quả của một phép tính, hãy dùng `Callable` và `Future`, hoặc truyền kết quả qua kênh thread-safe. Với thread chạy trực tiếp, `UncaughtExceptionHandler` giúp ghi nhận lỗi không bắt được.

Nếu chính thread đang chờ `join()` bị interrupt, `join()` ném `InterruptedException`. Một mẫu phù hợp khi phương thức không thể ném tiếp là:

```java
try {
    worker.join();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // Thoát hoặc chuyển điều khiển để caller quyết định.
}
```

Đây là mảnh mã trong ngữ cảnh có biến `worker`, không phải chương trình độc lập. Khôi phục cờ interrupt ngăn việc “nuốt” tín hiệu hủy. Không tiếp tục mặc định coi worker đã hoàn thành nếu `join()` bị ngắt trước khi nó kết thúc.

### `interrupt()` là yêu cầu hợp tác

```java
Thread worker = Thread.ofVirtual().start(() -> {
    try {
        Thread.sleep(10_000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
    }
});
worker.interrupt();
```

Mảnh mã trên yêu cầu thread ngủ nhận interrupt và thoát; `interrupt()` không ép task dừng ở mọi tình huống. Một vòng lặp tính toán không kiểm tra cờ interrupt hoặc tín hiệu dừng riêng có thể tiếp tục chạy. Khi dùng executor, `Future.cancel(true)` cố gắng gửi interrupt cho task đang chạy, nhưng không bảo đảm task sẽ dừng ngay.

`join(Duration)` chỉ có ở các bản Java mới hơn; để bảo đảm ví dụ tương thích Java 21, dùng `join(long millis)` hoặc `join(long millis, int nanos)` khi cần chờ có hạn. Sau lời gọi `join(timeout)`, phải đọc `isAlive()` nếu muốn biết task đã kết thúc hay chỉ hết thời gian chờ.

### Task lỗi: trạng thái thread vẫn kết thúc

Nếu `Runnable` ném exception không được xử lý, thread chuyển thành `TERMINATED`; exception được chuyển tới uncaught-exception handler phù hợp. `Thread.join()` vẫn chỉ là chờ, không đồng nghĩa kết quả thành công. Muốn bảo đảm caller có thông tin lỗi, ưu tiên `Future.get()` đối với task trong executor, hoặc thiết kế một kênh nhận lỗi rõ ràng cho quản lý thread thủ công.

## 10. Đặc điểm concurrency đáng chú ý

**Virtual thread không tự tạo structured concurrency.** Tự tạo nhiều thread rồi bỏ qua kết quả dẫn đến task lỗi không ai xử lý hoặc ứng dụng kết thúc giữa chừng. Với phạm vi nhỏ, giữ danh sách thread và `join()`; với tác vụ cần giá trị, chọn `ExecutorService` và đóng tài nguyên. Tránh tạo hàng nghìn task chỉ để chứng minh API có thể tạo được: số virtual thread nhiều không đồng nghĩa backend nhận được cùng số kết nối.

**Không dùng pool virtual thread cố định theo lối cũ như một cơ chế giới hạn I/O.** Virtual thread có chi phí tạo thấp và thường dùng theo mỗi task. Nếu giới hạn 20 request cùng lúc là yêu cầu nghiệp vụ, hãy giới hạn 20 *request hoặc tài nguyên*, chẳng hạn với `Semaphore` hoặc connection pool phù hợp. Dùng số worker thread thấp làm proxy cho giới hạn tài nguyên dễ khiến các task chờ chiếm hết worker.

**Synchronized, native và pinning thay đổi theo JDK.** Trong JDK 21, một số vùng `synchronized` hoặc lời gọi native có thể khiến virtual thread bị *pin* vào carrier khi blocking; việc này có thể hại throughput khi nhiều tác vụ cùng chờ. Các bản JDK mới có cải tiến cơ chế monitor; không nên áp dụng kết luận về pinning của một phiên bản cho mọi JDK. Hãy đo trên phiên bản đang chạy, giữ lock ngắn và tránh giữ lock khi thực hiện I/O lâu khi có thể. Pinning không thay đổi quy tắc `NEW/start/join/TERMINATED` của bài.

**Tên thread hỗ trợ chẩn đoán, không phải định danh nghiệp vụ.** Một task có thể có trace/correlation ID riêng; tên thread có thể trùng, thay đổi hoặc được cấp phát bởi factory. Đừng dùng tên để đồng bộ hay kiểm tra tính duy nhất của yêu cầu.

**Thời điểm tạo và thời điểm chạy khác nhau.** Ví dụ `inheritInheritableThreadLocals(...)` ảnh hưởng giá trị được kế thừa khi **tạo thread**, không phải mỗi lần scheduler gắn một virtual thread vào carrier. Một `ThreadLocal` của carrier không tự trở thành context của mọi virtual thread. Không lưu trạng thái per-request vào biến static dùng chung mà thiếu bảo vệ concurrency.

## 11. Những lỗi phổ biến

| Cách viết / giả định | Vấn đề | Cách làm phù hợp |
| --- | --- | --- |
| `Thread.ofVirtual().unstarted(task)` rồi quên `start()` | Task không bao giờ chạy | Gọi `start()` đúng một lần; dùng `start(task)` nếu muốn chạy ngay |
| `factory.newThread(task)` và nghĩ task đã chạy | Factory chỉ tạo unstarted thread | Chủ động `start()` hoặc giao factory cho executor |
| Gọi `run()` thay `start()` | Không tạo luồng thực thi virtual riêng | Gọi `start()` |
| Gọi `start()` hai lần | `IllegalThreadStateException` | Tạo thread instance mới cho lần chạy tiếp theo |
| Kiểm tra `getState() == RUNNABLE` để chắc chắn task đã hoàn tất | State chỉ là ảnh chụp, có race | `join()` hoặc `Future.get()` |
| Chỉ gọi `isAlive() == false` | Không phân biệt `NEW` và `TERMINATED` | Theo dõi có start hay chưa và cơ chế completion rõ ràng |
| Chương trình main tạo virtual thread rồi thoát | Virtual thread là daemon, có thể không chạy xong | `join()` hoặc đóng executor đúng cách |
| `join()` xong cho rằng task không lỗi | `join()` không truyền exception của task | Dùng handler hoặc `Future` |
| Cho rằng mọi virtual thread đều chạy đồng thời trên CPU riêng | JVM chia sẻ carrier/scheduler | Hiểu đây là concurrency, không phải phép nhân số lõi |
| Tạo `ThreadFactory` chung nhưng tùy tiện chỉnh `Thread.Builder` đồng thời | Builder không thread-safe | Thiết lập builder trước, dùng factory đã tạo |
| Chặn I/O khi giữ monitor quá lâu | Tăng contention/pinning trên những JDK tương ứng | Rút ngắn vùng lock, đo và tối ưu nơi có vấn đề |

## 12. Tình huống tự kiểm tra

**Tình huống A.** Sau `Thread t = Thread.ofVirtual().unstarted(job);`, có thể khẳng định `job` đã chạy? **Không.** `t` còn `NEW` cho tới khi `start()` được gọi thành công.

**Tình huống B.** `ThreadFactory f = Thread.ofVirtual().factory(); Thread t = f.newThread(job);` thì ai gọi `start()`? **Caller** nếu tự quản lý; nếu giao factory cho `ExecutorService`, **executor** sẽ khởi chạy thread phục vụ task.

**Tình huống C.** Sau khi `t.join()` trả về bình thường, `t` có thể `start()` lại? **Không.** Thread instance đã được start không thể start lần hai.

**Tình huống D.** Hai tác vụ `factory.newThread(job)` được `start()` sát nhau; tên lần lượt là `job-1`, `job-2`. Tên nhỏ hơn có nhất thiết in kết quả trước? **Không.** Tên phản ánh thứ tự cấp phát thread, không quyết định lịch chạy.

**Tình huống E.** `Executors.newThreadPerTaskExecutor(factory)` dùng virtual factory có nhất thiết chỉ chạy một thread mỗi lúc? **Không.** Executor dạng này tạo thread mới cho mỗi task; số tác vụ chạy/chờ đồng thời phụ thuộc workload và tài nguyên.

**Tình huống F.** `Thread.State.TERMINATED` có nghĩa task chạy thành công? **Không.** Exception không được bắt cũng có thể làm task kết thúc.

## 13. Tài liệu chuẩn để tra cứu

- [Java SE 21 – Thread.Builder](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.Builder.html): `unstarted`, `start`, `factory` và thuộc tính builder.
- [Java SE 21 – Thread.Builder.OfVirtual](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.Builder.OfVirtual.html): đặt tên, kế thừa thread-local và handler.
- [Java SE 21 – ThreadFactory](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadFactory.html): contract `newThread` trả thread chưa khởi chạy.
- [Java SE 21 – Thread](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html): vòng đời, `start`, `join`, `interrupt`, `isVirtual`.
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444): mục tiêu và thiết kế virtual threads.

Một cách tự kiểm tra hữu ích là tạo thread bằng `unstarted()`, quan sát `NEW`, gọi `start()`, rồi `join()` để quan sát `TERMINATED`. Khi mở rộng sang nhiều task, giữ đúng sự phân công trách nhiệm: **builder định cấu hình, factory tạo thread, caller hoặc executor khởi chạy, và cơ chế completion xác nhận kết thúc**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-015">JDK 21 so với JDK 24+: synchronized thông thường không còn gây pinning kiểu cũ</a>

<details>
<summary>Xem chi tiết</summary>

# JDK 21 so với JDK 24+: synchronized thông thường không còn gây pinning kiểu cũ

## Mục tiêu

**JDK 24 triển khai JEP 491 — Synchronize Virtual Threads without Pinning.** Thay đổi này cho phép virtual thread đang đi qua cơ chế monitor của Java (gồm `synchronized`, chờ lấy monitor và `Object.wait()`) nhường carrier thread trong các tình huống blocking mà trước đây dễ gây pinning ở JDK 21. **Không có thay đổi về cú pháp hoặc ý nghĩa loại trừ tương hỗ của `synchronized`.**

Bài học dành cho Java concurrency nói chung, chỉ sử dụng JDK và Java SE. Nội dung cần nắm:

1. Phân biệt **virtual thread**, **carrier**, **mount/unmount**, **blocking**, **pinning** và **monitor contention**.
2. Hiểu chính xác lỗi hiệu năng của JDK 21 khi virtual thread block trong `synchronized` và phần JEP 491 sửa từ JDK 24.
3. Biết trường hợp nào vẫn có thể pin dù đã lên JDK 24+, cách đọc JFR và cách quyết định giữ `synchronized` hay đổi sang `ReentrantLock`.
4. Tự chạy một chương trình **Java 21+** để so sánh kết quả giữa các phiên bản JDK mà không cần thư viện bên ngoài.

## 1. Virtual thread và carrier: lúc nào xảy ra pinning?

Từ Java 21 (JEP 444), virtual thread là một `Thread` do JVM quản lý, thích hợp khi một tác vụ thường phải **đợi** I/O hoặc tín hiệu đồng bộ. Một virtual thread không giữ cố định một OS thread suốt vòng đời. Khi được chạy, virtual thread được **mount** lên một platform thread của scheduler, gọi là **carrier**; khi đợi tại điểm mà JVM hỗ trợ, nó có thể **unmount**, trả carrier lại để thực thi virtual thread khác.

~~~text
                  Khi runnable
Virtual thread A ──────────────► Carrier #1 ─► CPU
       │                              │
       └─ blocking I/O / sleep ───────┤
          (có thể unmount)            └─ Carrier #1 chạy virtual thread B

Nếu bị pinned lúc blocking:
Virtual thread A ──────────────► Carrier #1 ─► chờ cùng A
                                               (không được dùng cho B)
~~~

**Pinning** xảy ra khi virtual thread phải tiếp tục gắn với carrier tại thời điểm nó thực hiện thao tác blocking. Tác động đáng lo là **chiếm một OS thread để chờ** trong khi nhiều virtual thread khác cần carrier. Tình trạng này có thể làm giảm throughput và khả năng mở rộng, nhưng **không tự nó làm kết quả chương trình sai**.

Phân biệt ba hiện tượng:

- **Blocked virtual thread:** tác vụ đang đợi; hoàn toàn bình thường nếu carrier được giải phóng.
- **Pinned virtual thread:** tác vụ đang đợi mà carrier không thể được giải phóng; tốn tài nguyên platform thread.
- **Monitor contention:** nhiều thread tranh cùng một khóa; dù không pin carrier, các thread vẫn phải chờ tới lượt vào critical section. JDK 24 không biến một monitor thành khóa cho phép chạy song song.

Virtual thread không làm một đoạn CPU-bound chạy nhanh hơn. Điểm mạnh là khả năng phục vụ nhiều tác vụ chủ yếu chờ I/O với số carrier có giới hạn.

## 2. JDK 21: vì sao synchronized có thể gây pinning?

Trên JDK 21, khi virtual thread **đang ở trong một `synchronized` block hoặc method** và gọi một thao tác blocking như `Thread.sleep()`, `BlockingQueue.take()` hoặc một lời gọi I/O có khả năng nhường carrier, JVM thường **không unmount được virtual thread** vì nó đang giữ monitor. Carrier bị giữ lại cho thời gian đợi.

~~~java
private final Object monitor = new Object();

void process() throws InterruptedException {
    synchronized (monitor) {
        // Minh họa một thao tác chờ lâu.
        Thread.sleep(200);
    }
}
~~~

Đây là **đoạn mã nằm trong một class**, không phải chương trình chạy độc lập. Trên JDK 21, nếu `process()` được gọi từ virtual thread và `Thread.sleep(200)` thực sự chờ, nó có thể giữ carrier khoảng 200 ms. Nhiều request độc lập dùng **những monitor khác nhau** nhưng đều block trong synchronized vẫn có thể chiếm nhiều carrier đồng thời.

Có một số điểm dễ hiểu sai:

- `synchronized` chỉ thực thi vài phép gán hoặc phép tính trong bộ nhớ **không phải tình huống đáng sợ**: phần quan trọng là blocking **dài và thường xuyên** trong monitor.
- Việc chương trình chờ lock do contention không đồng nghĩa với pinning; cần xem khả năng **unmount** trong lần chờ đó.
- Pinning không làm monitor bị bỏ qua: `synchronized` vẫn bảo đảm mutual exclusion, quan hệ happens-before giữa unlock/lock phù hợp và tính tái nhập của Java monitor.
- Pinning khác deadlock. Deadlock là các vòng phụ thuộc khóa không thể tiến triển; JEP 491 không tự giải deadlock, lost notification hay race condition.

### Cách giảm pinning trên JDK 21

Nếu profiling cho thấy một vùng `synchronized` **thường xuyên giữ carrier khi đợi lâu**, bạn có thể:

1. **Đưa I/O hoặc thao tác chờ ra ngoài critical section** khi bất biến dữ liệu cho phép.
2. **Rút ngắn thời gian giữ monitor** và kiểm tra lại atomicity.
3. **Dùng `ReentrantLock`** cho đúng vùng cần bảo vệ khi không thể tránh blocking bên trong: cách đợi của AQS/`LockSupport` hỗ trợ virtual-thread-friendly parking, tránh hạn chế *do monitor* vốn có trên JDK 21.
4. **Nâng JDK lên 24+** nếu phù hợp vòng đời sản phẩm và kiểm thử, vì JEP 491 giải quyết trực tiếp nguồn pinning của Java monitor.

Không nên thay mọi `synchronized` thành `ReentrantLock` theo kiểu máy móc: đổi khóa có thể kéo theo thay đổi cách dùng `wait/notify`, điều kiện chờ, xử lý interrupt, quy tắc unlock và độ phức tạp code.

## 3. JDK 24+: JEP 491 thay đổi chính xác điều gì?

JEP 491 được đưa vào **JDK 24**. Việc dùng monitor thông thường không còn khiến một virtual thread nhất thiết phải giữ carrier trong khi nó bị block. JVM hỗ trợ **unmount** virtual thread trong các tình huống quan trọng sau:

| Tình huống | JDK 21 | JDK 24+ |
| --- | --- | --- |
| Virtual thread gọi `Thread.sleep` hoặc blocking API trong `synchronized` | Có thể pin carrier vì monitor đang được giữ | Monitor thông thường không còn là lý do pin |
| Virtual thread chờ vào `synchronized` do lock đang bị thread khác giữ | Hạn chế triển khai monitor cũ có thể giữ carrier trong một số đường chờ | Cơ chế monitor được điều chỉnh để chờ mà giải phóng carrier |
| Virtual thread gọi `Object.wait()` trong vùng sở hữu monitor | Có thể pin carrier do monitor | Có thể chờ mà unmount carrier trong đường thực thi bình thường |
| Critical section chỉ thực hiện tính toán ngắn | Thường không ảnh hưởng đáng kể bởi pinning | Vẫn cần monitor để bảo đảm mutual exclusion |
| Blocking trong native/foreign frame | Vẫn có thể pin | **Vẫn có thể pin**; JEP 491 không xóa mọi nguyên nhân |

Cần nói chính xác: **JDK 24+ loại bỏ pinning do synchronized thông thường**, chứ không tuyên bố một virtual thread sẽ *không bao giờ* bị pinned. Ví dụ, nếu lời gọi ở bên trong synchronized còn đi qua native/foreign frame có vấn đề, nó vẫn có thể bị pin vì **nguyên nhân khác**.

### Minh họa trình tự

~~~text
Ví dụ: virtual thread thực thi synchronized(lock) { Thread.sleep(250); }

JDK 21:
  A được mount trên carrier-1
  A vào monitor lock
  A ngủ 250 ms
  carrier-1 bị giữ trong lúc A ngủ (monitor-related pinning)
  A thức dậy, ra khỏi monitor, kết thúc

JDK 24+:
  A được mount trên carrier-1
  A vào monitor lock
  A ngủ 250 ms, được unmount (trong trường hợp thông thường)
  carrier-1 có thể chạy B
  A sẵn sàng, được remount, tiếp tục giữ logic monitor đúng như cũ
  A ra khỏi monitor, kết thúc
~~~

Việc **unmount** không có nghĩa `lock` được release khi `A` ngủ. Với `Thread.sleep` trong `synchronized`, A vẫn **giữ monitor**; các thread khác muốn vào cùng monitor phải chờ A ra khỏi critical section. Riêng `Object.wait()` vốn có ngữ nghĩa **tạm nhả monitor, chờ rồi giành lại monitor** khi thức dậy; quy tắc này vẫn giữ nguyên.

JEP 491 cải thiện việc sử dụng carrier, **không** xóa tính tuần tự của critical section, **không** nâng throughput nếu bản thân khóa hoặc downstream service là nút thắt.

## 4. Ví dụ đầy đủ chạy trên Java 21 và Java 24+

Tạo file **`MonitorPinningDemo.java`**:

~~~java
import java.util.concurrent.locks.ReentrantLock;

public class MonitorPinningDemo {
    private static final Object MONITOR = new Object();
    private static final ReentrantLock LOCK = new ReentrantLock();

    private static void pause() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static void runWithMonitor() {
        synchronized (MONITOR) {
            pause();
        }
    }

    private static void runWithReentrantLock() {
        LOCK.lock();
        try {
            pause();
        } finally {
            LOCK.unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread worker = Thread.ofVirtual().name("pinning-demo").start(() -> {
            System.out.println("virtual=" + Thread.currentThread().isVirtual());
            runWithMonitor();
            System.out.println("monitor finished");
            runWithReentrantLock();
            System.out.println("lock finished");
        });
        worker.join();
    }
}
~~~

Chạy với **bất kỳ JDK 21+**:

~~~bash
javac MonitorPinningDemo.java
java MonitorPinningDemo
~~~

Output của riêng chương trình (không tính log khởi động JVM/JFR nếu bật công cụ chẩn đoán):

~~~text
virtual=true
monitor finished
lock finished
~~~

Cùng một chương trình có output như nhau trên JDK 21 và JDK 24+, vì **ngữ nghĩa khóa và kết quả của hai lời gọi không đổi**. Điểm khác nhau cần kiểm chứng bằng **JDK Flight Recorder (JFR)** hoặc đo hiệu năng dưới tải đại diện: trên JDK 21, thao tác sleep trong `synchronized` thường tạo sự kiện pinning; trên JDK 24+, riêng việc giữ monitor không còn gây loại pinning này.

Vì chỉ tạo một virtual thread, chương trình này **minh họa cơ chế và cách đo**, không phải benchmark để khẳng định JDK 24 có throughput cao hơn bao nhiêu. Thời gian sleep thực tế và event có thể thay đổi theo JDK, JFR settings và môi trường.

## 5. Xác minh pinning bằng JFR

JFR ghi sự kiện `jdk.VirtualThreadPinned` khi virtual thread bị pin trên carrier lâu hơn ngưỡng cấu hình. Trên các bản JDK nêu trong tài liệu chính thức, sự kiện này mặc định được bật với ngưỡng thường là **20 ms**. Hãy đo với workload đủ dài và chạy cùng bài thử trên hai runtime **đã xác định rõ version/vendor/build**.

Chạy chương trình với JFR (lần lượt bằng lệnh `java` của JDK 21 rồi JDK 24+):

~~~bash
java -version
java -XX:StartFlightRecording=filename=pinning.jfr,dumponexit=true,settings=profile MonitorPinningDemo
jfr print --events jdk.VirtualThreadPinned pinning.jfr
~~~

Lệnh `java -XX:StartFlightRecording=...` có thể in thêm log khởi động JFR. File `pinning.jfr` được tạo khi recording kết thúc. Dùng tên file riêng cho từng JDK nếu muốn so sánh song song.

**Kỳ vọng hợp lý:**

- **JDK 21:** Khi `runWithMonitor()` ngủ khoảng 250 ms trong monitor, bạn có thể thấy `jdk.VirtualThreadPinned` gắn với `MonitorPinningDemo` và đường gọi `Thread.sleep`. `runWithReentrantLock()` không gây pinning *chỉ bởi monitor*, vì không dùng Java monitor cho lock đó.
- **JDK 24+:** Tình huống `Thread.sleep` thông thường bên trong `synchronized` sẽ không còn tạo sự kiện pinned do monitor theo JEP 491. **Điều này không có nghĩa JFR sẽ không có bất kỳ sự kiện pinning nào** khi chạy chương trình phức tạp khác.

Trên **JDK 21**, còn có chế độ chẩn đoán nhanh:

~~~bash
java -Djdk.tracePinnedThreads=full MonitorPinningDemo
~~~

Nếu phát hiện blocking trong vùng monitor, JVM có thể in stack trace với thông tin monitor. Tùy chọn `-Djdk.tracePinnedThreads` là công cụ chẩn đoán gắn với cách triển khai cũ; **không dùng nó làm phép kiểm tra chính trên JDK 24+**, nơi công cụ này đã bị loại bỏ. Ưu tiên JFR cho bản mới.

### Đọc kết quả sao cho đúng

Một trace JFR đáng xem cần ít nhất các yếu tố: sự kiện **VirtualThreadPinned**, **duration** (độ dài), stack trace (đường gọi) và, nếu runtime hỗ trợ, **blockingOperation**, **pinnedReason**, **carrierThread**. Trên các JDK mới, trường nguyên nhân giúp phân biệt native/VM frame với các tình huống khác.

**Không thấy event** có nhiều cách giải thích:

- Không có pinning.
- Pinning ngắn hơn threshold cấu hình.
- Workload không đi qua đường code gây pinning, hoặc tác vụ kết thúc trước recording.
- Cấu hình event/stack trace/recording không phản ánh workload đang chạy.

Không kết luận “không pin” chỉ vì một lần chạy cục bộ không có event. Với hệ thống thực, hãy thu thập JFR dưới tải đại diện, đối chiếu số carrier, độ trễ hàng đợi, throughput và các blocking operation đang thực sự xảy ra.

Dùng `jcmd` để tạo thread dump khi cần khảo sát virtual thread đang chờ:

~~~bash
jcmd <PID> Thread.dump_to_file -format=json threads.json
~~~

Thay `<PID>` bằng process ID JVM; dump giúp thấy các thread và stack đang chờ, nhưng **không thay thế** dữ liệu thời lượng pinning của JFR. `jcmd` cần đúng JVM, quyền truy cập và khả năng attach.

## 6. Những trường hợp JDK 24+ vẫn có thể pin

JEP 491 nhằm giải quyết pinning **do Java monitor**. Các đường sau vẫn cần điều tra:

| Nguồn tiềm năng | Vì sao còn vấn đề | Hướng xử lý |
| --- | --- | --- |
| **JNI/native methods** | Native frame có thể ngăn virtual thread unmount khi phát sinh blocking | Profiling native boundary; tránh đợi lâu trong đường native hoặc dùng kiến trúc phù hợp |
| **Foreign Function & Memory API / lời gọi foreign** | Chuyển điều khiển vào mã native theo FFM vẫn có thể tạo ràng buộc carrier | Kiểm tra thời gian/blocking và khả năng đặt việc đó ở đường thực thi khác |
| **Khởi tạo/lấy khóa class initialization** | Một số đường JVM phục vụ class initialization và chờ class khởi tạo có thể giữ carrier | Khởi tạo trước tài nguyên quan trọng; điều tra trace/JFR |
| **Từng dạng I/O hoặc thao tác JVM không unmount được** | Phụ thuộc đường API và runtime; không phải mọi thao tác chờ đều là virtual-thread-aware | Đo bằng JFR và quan sát thời gian carrier thực tế |

Sự tồn tại của các nguồn này không có nghĩa mọi đoạn có JNI, file I/O hay class loading **đều** pin. Cần nhìn vào **đường thực thi và thao tác block thực sự**. Tương tự, việc JFR báo `jdk.VirtualThreadPinned` trong một chương trình dùng `synchronized` trên JDK 24+ **không chứng minh** JEP 491 thất bại: nguyên nhân có thể là native/VM frame bên dưới.

## 7. Vì sao vẫn cần thiết kế synchronized cẩn thận?

**Không pin carrier** chỉ giải quyết một trong các chi phí khi concurrency cao. Ví dụ dưới đây vẫn có điểm nghẽn trên cả JDK 21 lẫn JDK 24+:

~~~java
private final Object shared = new Object();

void slowCriticalSection() throws InterruptedException {
    synchronized (shared) {
        // Mọi caller dùng cùng "shared" vẫn phải tuần tự chờ nhau.
        Thread.sleep(500);
    }
}
~~~

Với nhiều virtual thread cạnh tranh **cùng shared**, mỗi lần chỉ một thread vào critical section, tạo hàng chờ lớn ngay cả khi các thread chờ không giữ carrier. Nếu điều kiện nghiệp vụ không yêu cầu giữ khóa trong thời gian chờ, đặt lệnh chờ ra ngoài monitor và chỉ khóa phần cập nhật state:

~~~java
private final Object shared = new Object();
private int completed;

void improvedStructure() throws InterruptedException {
    Thread.sleep(500); // Chờ ngoài monitor nếu không cần bảo vệ bởi shared.
    synchronized (shared) {
        completed++;
    }
}
~~~

Hai ví dụ là **đoạn thành viên class**, không phải chương trình độc lập. Việc chuyển lệnh ra ngoài monitor **chỉ hợp lệ khi vẫn bảo đảm bất biến/atomicity** của nghiệp vụ: chẳng hạn nếu kết quả I/O và cập nhật state bắt buộc phải được khóa cùng nhau, việc tách có thể tạo race.

Đối với throughput thực tế, quan sát cả:

- **Độ dài critical section** và contention trên monitor.
- **Số kết nối/tài nguyên downstream** như database pool, HTTP connection cap, API rate limit.
- **Số tác vụ đang chờ** và throughput, không chỉ số virtual thread.
- **Blocking native** và scheduler starvation.
- **Bộ nhớ** của các tác vụ đang chờ và các biến `ThreadLocal` tạo mới theo task.

Virtual threads thường **không cần pool thread theo kích thước cố định**. Khi cần giới hạn truy cập tài nguyên khan hiếm, dùng ràng buộc trực tiếp như `Semaphore` hoặc giới hạn connection pool thay vì cố tình gom virtual thread vào pool nhỏ.

## 8. Có còn cần ReentrantLock trong JDK 24+?

**Có**, nhưng lựa chọn giờ dựa trên **tính năng khóa và cấu trúc code**, không phải quy tắc “virtual thread luôn tránh synchronized”.

| Nhu cầu | `synchronized` | `ReentrantLock` |
| --- | --- | --- |
| Critical section ngắn, cần mutual exclusion đơn giản | Rất phù hợp; tự unlock khi thoát block | Cũng được nhưng phải `unlock` trong `finally` |
| JDK 21, block lâu trong monitor và profiling thấy pinned | Có thể cần refactor | Có thể tránh loại pinning do monitor |
| JDK 24+, block trong monitor thông thường | Không còn pin do monitor theo JEP 491 | Không bắt buộc phải thay chỉ để tránh pinning cũ |
| Thử lock không chờ `tryLock()` | Không có phương thức trực tiếp tương đương | Có |
| Lấy lock hỗ trợ interrupt `lockInterruptibly()` | Chờ vào monitor không có API trực tiếp tương đương | Có |
| Nhiều điều kiện chờ độc lập | `wait/notify/notifyAll` trên cùng monitor | `Condition` hỗ trợ nhiều wait set |
| Fairness tùy chọn | Không có fairness constructor | Có optional fairness policy |

Ví dụ chuyển khóa để hỗ trợ yêu cầu cụ thể (có thể chạy trong class chứa trường `lock`):

~~~java
import java.util.concurrent.locks.ReentrantLock;

private final ReentrantLock lock = new ReentrantLock();

void updateWithInterruptibleLock() throws InterruptedException {
    lock.lockInterruptibly();
    try {
        // Cập nhật state chia sẻ hoặc tác vụ phải bảo vệ.
    } finally {
        lock.unlock();
    }
}
~~~

Đây là **trích đoạn class**, không thể biên dịch độc lập vì đặt field/method ở top level. `ReentrantLock` không tự làm thao tác bên trong nhanh hơn. Dù dùng loại lock nào, phần critical section dài và contention vẫn ảnh hưởng latency. Việc đổi `wait/notify` sang `Condition.await/signal` phải thiết kế lại đúng vòng điều kiện và tín hiệu, không đơn giản thay tên API.

## 9. Hướng xử lý khi nâng ứng dụng từ JDK 21 lên JDK 24+

Một lộ trình kiểm chứng hợp lý:

1. **Chốt phiên bản runtime** của từng môi trường, không chỉ `sourceCompatibility` hoặc `--release`. Lúc chạy trên JDK 21 vẫn có giới hạn pinning của 21, dù mã được biên dịch bằng toolchain khác.
2. **Lấy baseline với JDK 21**: JFR `jdk.VirtualThreadPinned`, throughput, độ trễ p95/p99 và số carrier đang hoạt động dưới workload đại diện.
3. **Thử cùng workload trên JDK 24+**: giữ code, khối lượng công việc và cấu hình so sánh nhất quán; xác nhận các event do monitor thông thường giảm/mất.
4. **Điều tra event còn lại**: tra stack trace, nguyên nhân native/VM, class initialization và đường I/O thực tế trước khi sửa code.
5. **Review các workaround cũ**: từng trường hợp đã đổi `synchronized` sang `ReentrantLock` *chỉ để tránh pinning* có thể không còn cần thiết về mặt performance trên 24+, nhưng không buộc phải đổi lại khi code đang tốt.
6. **Kiểm tra bottleneck kế tiếp**: cùng một monitor vẫn serialize truy cập, database pool vẫn có giới hạn, CPU-bound tasks không được tăng tốc bởi virtual thread.

Không tối ưu chỉ bằng một con số `jdk.VirtualThreadPinned`: có thể pin đã hết nhưng contention hoặc downstream limit vẫn chi phối tổng throughput.

## 10. Sai lầm phổ biến

1. **“JDK 24 làm synchronized không cần khóa nữa.”** Sai. Mutual exclusion, visibility/happens-before và tính tái nhập vẫn giữ nguyên.
2. **“Một virtual thread bị blocked tức là nó pin.”** Sai. Blocked nhưng đã unmount là cách virtual thread hoạt động hiệu quả.
3. **“JDK 24 loại bỏ mọi pinning.”** Sai. Native/foreign và một số đường VM vẫn có thể pin.
4. **“Có synchronized trong stack thì pinning event trên JDK 24 chắc do monitor.”** Sai. Cần đọc **pinnedReason/stack trace**; monitor có thể chỉ là khung gọi bên ngoài.
5. **“Nâng JDK sẽ chữa deadlock.”** Sai. Deadlock, lock ordering và race là vấn đề correctness khác.
6. **“Không có JFR event tức là không có pinning.”** Chưa chắc. Threshold, thời điểm bắt đầu recording và workload có thể che khuất.
7. **“Phải đổi toàn bộ synchronized sang ReentrantLock khi dùng virtual threads.”** Sai, nhất là từ JDK 24+. Trên JDK 21 cũng chỉ nên đổi những vùng thực sự gây pinning đáng kể.
8. **“Đã không pin thì có thể giữ monitor xuyên I/O dài tùy ý.”** Sai. Monitor vẫn chặn các thread muốn cùng truy cập dữ liệu.
9. **“Virtual thread làm request nhanh hơn.”** Không trực tiếp. Mục tiêu chính là khả năng scale khi nhiều request đang chờ I/O.
10. **“Kiểm chứng bằng JDK compile là đủ.”** Không đủ. Hành vi monitor pinning phụ thuộc phiên bản **JVM thực thi** và phải đo khi chạy.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Trên JDK 21, tại sao `synchronized(lock) { Thread.sleep(300); }` có thể giảm khả năng scale khi có nhiều virtual thread?

Vì khi đang giữ monitor, virtual thread không thể unmount theo cơ chế cũ; mỗi lần blocking giữ một carrier/OS thread. Nếu số thao tác này lớn và thường xuyên, scheduler có ít carrier rảnh để chạy task khác.

**Câu 2.** Cùng code trên JDK 24+, thread khác có thể vào cùng monitor trong lúc thread đầu ngủ không?

**Không.** JEP 491 cho phép giải phóng *carrier*, không giải phóng *monitor* của `synchronized` khi sleep. Thread khác phải chờ đến lúc monitor được unlock.

**Câu 3.** Trên JDK 24+, vì sao native blocking vẫn có thể làm JFR ghi `VirtualThreadPinned`?

Vì JEP 491 giải quyết pinning do Java monitor, không thể bảo đảm unmount an toàn trong mọi native/foreign hoặc VM frame.

**Câu 4.** Khi nào nên giữ `ReentrantLock` dù đã lên JDK 24?

Khi cần `tryLock`, `lockInterruptibly`, `Condition`, fairness hoặc khi cấu trúc hiện tại đã ổn định. Không có yêu cầu thay tất cả về `synchronized`.

**Câu 5.** Tại sao việc không còn `VirtualThreadPinned` không đồng nghĩa throughput chắc chắn tăng?

Vì chương trình vẫn có thể giới hạn bởi CPU, contention trên cùng monitor, connection pool, rate limit, bộ nhớ hoặc downstream service.

## Nguồn tài liệu chuẩn

- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444): nền tảng virtual thread, carrier và pinning ở các bản đầu.
- [JEP 491 — Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491): cơ chế thay đổi monitor vào JDK 24.
- [Oracle — Virtual Threads trên JDK 21](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html): giới hạn `synchronized` cũ, `ReentrantLock`, JFR và `jdk.tracePinnedThreads`.
- [Oracle — Significant Changes in JDK 24](https://docs.oracle.com/en/java/javase/24/migrate/significant-changes-jdk-24.html): xác nhận JEP 491 nằm trong JDK 24.
- [Oracle — Virtual Threads trên JDK 24](https://docs.oracle.com/en/java/javase/24/core/virtual-threads.html): pinning còn lại từ native/foreign, JFR và hướng dùng virtual threads.
- [OpenJDK — RFR triển khai JEP 491](https://mail.openjdk.org/pipermail/core-libs-dev/2024-November/134311.html): ba đường thay đổi quan trọng gồm unmount khi giữ monitor, chờ vào monitor và `Object.wait()`.

**Kết luận kỹ thuật:** trên **JDK 21**, blocking dài bên trong `synchronized` là một nguồn pinning cần đo và có thể phải xử lý. Từ **JDK 24**, JEP 491 sửa cơ chế đó, cho phép giữ cách viết synchronized thông thường mà không giữ carrier chỉ vì monitor. Hãy tiếp tục quan sát JFR cho native/VM pinning và kiểm soát contention, tài nguyên downstream theo đúng nhu cầu thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-016">Native method và foreign function: các trường hợp pinning còn phải đánh giá</a>

<details>
<summary>Xem chi tiết</summary>

# Native method và foreign function: những trường hợp pinning của Virtual Threads vẫn phải đánh giá

> Phạm vi: **Java concurrency ở cấp JVM/JDK**, từ Java 21 trở lên; đặc biệt chú ý thay đổi trong JDK 24 và các đường gọi **JNI** / **Foreign Function & Memory API (FFM)**. Kiến thức ở đây áp dụng cho ứng dụng Java thuần, không phụ thuộc Spring.

## 1. Vì sao native/foreign call là một ranh giới đặc biệt?

Một **virtual thread** là `java.lang.Thread` do JVM lập lịch trên một **carrier thread** (platform thread). Khi virtual thread đang chờ thao tác mà JDK hỗ trợ suspend/unmount, trạng thái thực thi của nó được lưu lại và carrier có thể phục vụ virtual thread khác.

Nhưng Java cũng phải gọi xuống thư viện/mã máy bên ngoài JVM qua hai cơ chế:

- **JNI native method:** phương thức Java được khai báo `native` và được hiện thực bằng mã C/C++ hoặc thư viện nền tảng. Java vẫn nhìn thấy chữ ký phương thức, nhưng mã thực thi và thao tác chặn bên trong nằm ở phía native.
- **Foreign function:** lời gọi từ Java vào hàm ngoài JVM bằng FFM API, thường qua `Linker.downcallHandle(...)` và `MethodHandle`. FFM có API mạnh hơn để mô tả ABI, biểu diễn bộ nhớ native và kiểm soát vòng đời, nhưng một downcall vẫn đi qua ranh giới Java–native.

Native frame có thể phụ thuộc ngăn xếp OS thread, thanh ghi, trạng thái kernel, bộ nhớ native và thread-local state của thư viện. JVM không thể tự do dời/ngắt một lời gọi native đang thực thi để “unmount rồi chạy tiếp trên carrier khác” như một số thao tác blocking Java do JDK quản lý.

Vì vậy, **khi virtual thread đang chạy native method hoặc foreign function, nó có thể bị pin với carrier**. Nếu lời gọi trở về nhanh, tác động không đáng kể. Nếu nó chờ lâu (đọc thiết bị, gọi thư viện đồng bộ, DNS qua native library, C API chờ lock, syscall, JNI callback bị chặn...), một carrier có thể bị giữ lâu dù ứng dụng tạo rất nhiều virtual threads.

### Ba khái niệm không được đánh đồng

| Khái niệm | Điều đang xảy ra | Hệ quả |
|---|---|---|
| **Mount** | Virtual thread đang chạy trên một carrier | Carrier đang dùng CPU/OS thread để phục vụ nó |
| **Unmount khi chờ** | JDK suspend virtual thread tại điểm hỗ trợ | Carrier được giải phóng, có thể làm việc khác |
| **Pinning** | Virtual thread không thể unmount tại điểm cần chờ | Nếu chờ lâu, carrier bị giữ; có thể giảm throughput |

**Một native call ngắn không tự trở thành “lỗi pinning”.** Rủi ro quan trọng là **mức độ thường xuyên × thời gian carrier bị chiếm × số task đồng thời**, chứ không phải chỉ sự hiện diện của `native`/`downcallHandle` trong source code.

```text
Đường chờ được JDK hỗ trợ:
Virtual thread -> Java socket read / park -> UNMOUNT -> carrier rảnh
                                               -> resume khi sẵn sàng

Đường native chặn lâu:
Virtual thread -> JNI/FFM -> native blocking call -> carrier vẫn bị chiếm
                                              (không có điểm unmount an toàn)
```

Đây là giản đồ hành vi điển hình, không khẳng định mọi lời gọi Java nội bộ có native code đều chặn carrier. JDK có các triển khai/chuyển đổi đặc biệt cho nhiều API blocking chuẩn; hãy đo đường thực thi cụ thể.

## 2. Phân biệt JDK 21–23 và JDK 24 trở lên

Virtual threads chính thức có trong **Java 21 (JEP 444)**. Khi phân tích pinning phải nêu phiên bản JDK:

| Trường hợp | JDK 21–23 | JDK 24+ |
|---|---|---|
| Blocking trong `synchronized` method/block | Có thể pin carrier | JEP 491 cho phép unmount trong trường hợp monitor thông thường |
| Chờ monitor / `Object.wait()` thông thường | Có thể gây hạn chế tương tự | JEP 491 cải thiện cách virtual thread chờ và tái chiếm monitor |
| JNI native method chạy dài/chặn | **Vẫn phải đánh giá** | **Vẫn phải đánh giá** |
| FFM downcall sang hàm C/native chặn | **Vẫn phải đánh giá** | **Vẫn phải đánh giá** |
| Native callback (JNI/FFM upcall) quay về Java và gọi API blocking | Có khả năng pin khi còn native frame | Vẫn có khả năng pin khi native frame còn trên stack |
| Chờ khởi tạo lớp hoặc một số vùng đặc biệt trong VM | Cần quan sát | Vẫn có trường hợp đặc biệt cần quan sát |

**JEP 491 không đồng nghĩa “Java 24 xóa hoàn toàn pinning”.** Nó giải quyết vấn đề monitor-related rất quan trọng, nhưng không thể biến các syscall và native library đồng bộ thành thao tác tự động suspend được. Thay toàn bộ `synchronized` bằng `ReentrantLock` để “sửa native pinning” là sai mục tiêu; thay đổi lock Java không giải phóng được một OS thread đang bị giữ trong native function.

Từ JDK 22, **FFM API được chuẩn hóa theo JEP 454**; nếu dùng JDK 21 thì FFM còn là preview và lệnh chạy/API có thể khác. Ví dụ FFM ở mục 5 dùng **JDK 25**, đã cố ý ghi rõ nền tảng để tránh nhầm cú pháp phiên bản.

## 3. Những dạng native/foreign path cần kiểm tra

### 3.1. JNI gọi một tác vụ I/O hoặc đồng bộ chặn lâu

Ví dụ chữ ký cầu nối JNI:

```java
final class NativeBridge {
    static {
        System.loadLibrary("nativebridge");
    }

    static native int readSensorBlocking();
}
```

*Đoạn này chỉ là khai báo cầu nối; muốn chạy phải có thư viện native tương ứng. Không phải chương trình Java độc lập.*

Nếu `readSensorBlocking()` đợi dữ liệu thiết bị 500 ms bên trong C/C++, virtual thread gọi nó không tự được JDK đưa vào danh sách chờ I/O để unmount. Hàng nghìn virtual threads cùng gọi hàm có thể chiếm nhiều OS threads hoặc khiến carrier khan hiếm.

Tương tự với thư viện mã hóa/hardware token, driver thiết bị, media codec, thư viện xử lý file chuyên dụng hay JNI wrapper gọi một thư viện C legacy. Cần xác minh **cơ chế chờ thực tế**: syscall blocking, mutex/condition variable, polling, callback, hay chỉ tính toán CPU.

### 3.2. FFM downcall gọi hàm native đồng bộ

Một lời gọi FFM có dạng:

```java
// Minh họa về API; cần tạo linker và function descriptor đúng ABI.
MethodHandle function = linker.downcallHandle(symbol, signature);
Object result = function.invokeWithArguments(arguments);
```

Dùng `Linker` và `MethodHandle` **không biến** hàm C đồng bộ thành Java non-blocking. Nếu hàm gốc chờ I/O hoặc lock bên native trong 2 giây, carrier thường không thể được trả cho scheduler trong khoảng đó. Với FFM, cần đọc cả tài liệu của C function: thao tác nào chặn, timeout, khả năng bị ngắt, affinity, thread-safety và hành vi callback.

### 3.3. Native gọi ngược vào Java (JNI callback / FFM upcall)

```text
Virtual thread
  -> Java wrapper
     -> JNI/FFM native function
        -> native C code
           -> callback Java
              -> LockSupport.park / Thread.sleep / socket read
                 -> không thể unmount khi native frame còn nằm trong stack
```

Đây là trường hợp dễ bị bỏ sót: lệnh chờ xuất hiện trong **mã Java callback**, nhưng stack vẫn có native frame ở phía dưới. Một cuộc gọi `Thread.sleep(...)` thông thường có thể unmount; cùng thao tác nằm trong chuỗi callback từ native lại có thể khiến carrier bị giữ. Khả năng tạo pinning phải được kiểm tra trên bản JDK, JVM, cách nối JNI/FFM và workload thật.

FFM **upcall stub** cho phép C gọi lại Java; khi thư viện ngoại tự tạo thread để gọi callback, các quy tắc thread và quan sát có thể khác so với downcall khởi đầu từ virtual thread. Không giả định callback chắc chắn đang chạy trên cùng loại thread; hãy kiểm tra bằng `Thread.currentThread().isVirtual()` và stack trace thực tế.

### 3.4. Native code tính toán CPU lâu

Native call có thể chiếm carrier lâu mà **không có một lệnh chờ blocking rõ ràng**. Đây là giới hạn sử dụng CPU/carrier, không nhất thiết là pinning event do virtual thread cố unmount rồi thất bại. Ví dụ: codec native xử lý ảnh hàng giây, thuật toán C chạy liên tục hoặc thư viện mã hóa ngốn CPU. Giải pháp chính là quản trị song song theo CPU, tránh tăng vô hạn virtual threads; chỉ nhìn event pinning sẽ bỏ sót bottleneck này.

### 3.5. Chờ native lock, khởi tạo lớp và vùng giới hạn của JVM

JNI/FFM có thể gọi những thư viện với **native mutex, condition variable, thread-local storage** hoặc yêu cầu thread-affinity. Cần xem đường gọi có trả về nhanh hay đợi lâu ở mutex/OS. Ngoài native boundary, JVM vẫn có một số tình huống hiếm khiến virtual thread không thể unmount, như chờ class initialization và vùng VM đặc biệt. Đừng quy toàn bộ hiện tượng không mở rộng được cho `synchronized` hay JNI khi chưa có stack/metrics.

## 4. Phân biệt “carrier bị chặn” và sự kiện `jdk.VirtualThreadPinned`

**Một điểm quan trọng khi đo:** JFR event `jdk.VirtualThreadPinned` chủ yếu giúp phát hiện các thời điểm virtual thread **định park/block mà không thể unmount**, kèm ngưỡng thời gian cấu hình. Native function tự chặn trong C trong một giây có thể làm carrier bận một giây **mà không nhất thiết tạo sự kiện pinned đúng khoảng thời gian đó**.

Ví dụ: virtual thread đang trong `sleep(1)` của C qua FFM. Nó chờ bên trong native call; JVM có thể không quan sát lệnh chờ đó như một điểm Java parking sinh `jdk.VirtualThreadPinned`. Ngược lại, nếu native frame gọi ngược vào Java rồi Java `LockSupport.park(...)`, JFR có thể ghi native frame là nguyên nhân không unmount được.

Vì thế:

- **Có sự kiện pinned dài và lặp lại**: bằng chứng mạnh về việc carrier không được trả khi một virtual thread bị chặn.
- **Không có sự kiện pinned**: **không chứng minh** không có thời gian chặn native hoặc không có carrier starvation.
- **Có frame native trong stack**: chỉ là manh mối; cần kết hợp duration/frequency và trạng thái carrier.
- **Số virtual threads rất cao**: không tự chứng minh bottleneck; theo dõi cả runnable backlog, CPU và tài nguyên downstream.

Khi điều tra một native path, hãy phối hợp JFR + thread dumps + profiler native/OS nếu cần; phân tích cả thời gian thực thi native chứ không dừng ở một loại event.

## 5. Demo FFM có thể chạy: gọi hàm C `sleep` trên Linux/x64

Ví dụ này dùng **JDK 25+, Linux/x64** và hàm C chuẩn trên hệ POSIX. Nó giúp thấy sự khác nhau giữa **chạy native trực tiếp trong virtual thread** và **đưa native blocking call vào một pool platform threads có giới hạn**. Ví dụ không tuyên bố rằng một lần chạy chắc chắn sẽ phát ra `jdk.VirtualThreadPinned`.

Tạo tệp **`NativeBoundaryDemo.java`**:

```java
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.lang.foreign.ValueLayout.JAVA_INT;

public class NativeBoundaryDemo {
    // C: unsigned int sleep(unsigned int seconds);
    // Trên Linux/x64, unsigned int có layout 32 bit.
    private static final MethodHandle NATIVE_SLEEP = findNativeSleep();

    private static MethodHandle findNativeSleep() {
        Linker linker = Linker.nativeLinker();
        return linker.downcallHandle(
                linker.defaultLookup().findOrThrow("sleep"),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    }

    private static int callNativeSleep() {
        try {
            return (int) NATIVE_SLEEP.invokeExact(1);
        } catch (Throwable problem) {
            throw new IllegalStateException("Native call failed", problem);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // Cách 1: gọi native trực tiếp trong virtual thread.
        Thread direct = Thread.ofVirtual().start(() -> {
            System.out.println("direct virtual="
                    + Thread.currentThread().isVirtual());
            System.out.println("direct remaining=" + callNativeSleep());
        });
        direct.join();

        // Cách 2: dùng số lượng platform threads có giới hạn
        // để thực thi phần native đang blocking.
        try (ExecutorService nativePool = Executors.newFixedThreadPool(2)) {
            Thread caller = Thread.ofVirtual().start(() -> {
                System.out.println("offload caller virtual="
                        + Thread.currentThread().isVirtual());
                Future<Integer> result = nativePool.submit(() -> {
                    System.out.println("native worker virtual="
                            + Thread.currentThread().isVirtual());
                    return callNativeSleep();
                });

                try {
                    // Future.get() chờ theo API Java; virtual caller có thể unmount.
                    System.out.println("offload remaining=" + result.get());
                } catch (InterruptedException interruption) {
                    result.cancel(true);
                    Thread.currentThread().interrupt();
                } catch (ExecutionException failure) {
                    throw new IllegalStateException(failure.getCause());
                }
            });
            caller.join();
        }
    }
}
```

Biên dịch/chạy trên **JDK 25+, Linux/x64**:

```bash
javac NativeBoundaryDemo.java
java --enable-native-access=ALL-UNNAMED NativeBoundaryDemo
```

Khi hai lần `sleep(1)` kết thúc bình thường (không có signal ngắt), stdout:

```text
direct virtual=true
direct remaining=0
offload caller virtual=true
native worker virtual=false
offload remaining=0
```

Với C `sleep`, return value khác 0 nếu bị ngắt và còn thời gian ngủ; do đó `remaining=0` là **kết quả trong trường hợp không bị gián đoạn**, không phải lời bảo đảm cho mọi môi trường. `defaultLookup()` và `unsigned int` là phụ thuộc ABI/nền tảng; trên Windows hoặc ABI khác phải chọn đúng thư viện, symbol và layout tương ứng. Không dùng nguyên mã này như demo đa nền tảng.

**Giải thích:** phần direct thực hiện native blocking trên carrier của virtual thread. Phần offload thực hiện native blocking trên **platform worker** của `nativePool`; virtual caller chờ `Future.get()` và có thể nhường carrier theo cơ chế JDK. Pool hai worker chỉ cho tối đa hai tác vụ native chạy đồng thời; nó không rút ngắn `sleep(1)` hay biến một hàm C thành non-blocking. Với một số API native, đặc biệt API đòi thread-affinity hoặc callback, phải kiểm tra có được phép chuyển thread hay không trước khi áp dụng.

Lưu ý `Future.cancel(true)`/interrupt của Java **không bảo đảm** dừng một native syscall hoặc thư viện C không hỗ trợ cancellation. Với hệ thống thực, phải dùng timeout/cancellation do chính native API cung cấp khi có.

### Demo này có giới hạn gì?

- Dùng 2 công việc và `sleep(1)` chỉ để minh họa ranh giới. Nó **không** cung cấp benchmark throughput, p99 hay xác nhận có/không pinning event.
- Nếu chạy đo, cần kiểm soát scheduler parallelism, số lượng công việc, thời gian chờ, platform pool và mức tải. Không dùng một kết quả chạy đơn lẻ để quyết định cấu hình production.
- Nếu native API trả về gần như ngay lập tức, việc đẩy qua pool có thể tốn thêm hàng đợi/context switching hơn là gọi trực tiếp; offload chủ yếu dành cho **native call có thể chặn lâu và xuất hiện thường xuyên**.

## 6. Quan sát và xác minh pinning trên JVM thật

### Bước 1: xác nhận runtime và các đường gọi

Ghi lại `java -version`, OS/ABI, tên/phiên bản thư viện native, JNI hay FFM, số kết nối/thiết bị, và native function cụ thể. Kiểm tra API nào thật sự blocking, có timeout, dùng callback, affinity hay native locks; không kết luận chỉ từ tên thư viện.

### Bước 2: bật JDK Flight Recorder

Ví dụ với chương trình `NativeBoundaryDemo` ở trên (JDK 25):

```bash
java --enable-native-access=ALL-UNNAMED \
  -XX:StartFlightRecording:filename=native-recording.jfr,dumponexit=true \
  NativeBoundaryDemo

jfr print --events jdk.VirtualThreadPinned native-recording.jfr
```

Dấu `\` cuối dòng ở ví dụ shell là cú pháp nối dòng của Bash/Linux. Nếu dùng Windows PowerShell, chạy lệnh `java` trên một dòng hoặc dùng cú pháp riêng của PowerShell.

Các event nên xét gồm `jdk.VirtualThreadPinned`, event blocking như `jdk.SocketRead`/`jdk.ThreadSleep` khi có, và các event về CPU/sampling tùy cấu hình. Trên các JDK có event `jdk.VirtualThreadPinned`, ngưỡng mặc định thường **20 ms**; sự kiện quá ngắn có thể không được ghi. Điều chỉnh threshold trong JFR nếu cần chứng minh vấn đề ở thời lượng ngắn.

Trên **JDK 21–23**, có thể dùng thêm `-Djdk.tracePinnedThreads=full` hoặc `short` để tìm stack khi virtual thread block trong lúc pinned. Đó là công cụ chẩn đoán theo phiên bản cũ; đừng mặc định những tùy chọn và nội dung trace giữ nguyên giữa các JDK.

### Bước 3: thu thread dump khi có tải

```bash
jcmd <PID> Thread.dump_to_file -format=json threads.json
```

`<PID>` là PID của JVM đang chạy; phải thay bằng số thực tế. Thread dump hỗ trợ xem nhiều virtual threads và trạng thái carrier, nhưng là **ảnh chụp tại một thời điểm**, chưa đủ đo thời lượng hoặc gọi tên native library. Nếu JFR chỉ vào native/VM frame, hãy đối chiếu stack, thời gian và mẫu CPU/native profiler (ví dụ công cụ profiler/OS tương thích môi trường). Chỉ dùng profiler native có hỗ trợ môi trường và bảo đảm quan sát được các stack frame cần thiết.

### Bước 4: đo một workload đại diện

Tối thiểu theo dõi:

| Nhóm phép đo | Câu hỏi cần trả lời |
|---|---|
| Throughput và p95/p99 latency | Với nhiều tác vụ đồng thời, hệ thống còn tăng thông lượng không hay bị đuôi trễ? |
| CPU / số OS threads / carrier | Native blocking có giữ nhiều platform threads mà CPU không bận tương ứng? |
| JFR pinned events | Có điểm chờ Java nào không unmount được do native frame? Tần suất/thời lượng? |
| Native function wall time / profiler | Hàm C thực sự chờ ở syscall, lock hay làm CPU? |
| Queue length / worker saturation | Pool offload có bị nghẽn và đẩy latency sang hàng đợi không? |
| Connection/native resource limits | Đã hết sockets, descriptors, buffer, native memory hay tài nguyên dịch vụ? |

Dùng so sánh trước–sau trên **cùng JDK, tải và giới hạn tài nguyên**, hoặc ghi rõ sự khác biệt khi đổi JDK. `jdk.VirtualThreadPinned` chỉ là một phần của bằng chứng. Nếu JFR im lặng mà wall time native tăng/OS threads bị chặn, tiếp tục điều tra native path.

## 7. Giảm ảnh hưởng mà vẫn giữ tính đúng đắn

### Cách A: ưu tiên API blocking Java được JDK quản lý khi có thể

Nếu nhu cầu là I/O mạng thông thường, cân nhắc API Java chuẩn tương thích virtual threads để JVM có cơ hội unmount lúc chờ. Việc chuyển từ JNI blocking sang API Java chuẩn **có thể** cải thiện concurrency, nhưng phải kiểm tra tính tương đương về chức năng, timeout, format, latency và tác dụng phụ. Không có quy tắc “mọi Java API đều unmount” hoặc “mọi native call đều phải bỏ”.

### Cách B: tách native blocking workload sang bounded platform pool

Dùng `Executors.newFixedThreadPool(n)` chuyên cho thư viện native có thể chặn dài, để virtual caller chờ `Future.get()` hoặc kết quả tương đương. Điều này **giới hạn số OS threads** bị chặn vì thư viện native; queue phải có giới hạn/backpressure và thời gian đợi phải được quản lý để tránh chuyển từ cạn carrier sang quá tải hàng đợi. `newFixedThreadPool` dùng hàng đợi không giới hạn mặc định; trong production cần thiết kế giới hạn số task in-flight/queue, hoặc semaphore/admission control phù hợp.

Không dùng pool này chỉ để “pool virtual threads”; mục đích là cô lập và kiểm soát một tài nguyên native hữu hạn. Không đổi thread tùy tiện với API native yêu cầu tất cả call diễn ra trên một OS thread, ví dụ một số API đồ họa, toolkit và thư viện có thread-local context; phải tôn trọng hợp đồng thư viện.

### Cách C: giới hạn concurrency tại tài nguyên gốc

Dùng `Semaphore`, connection pool, worker queue có giới hạn, timeout hoặc rate limit để quản lý số lời gọi native đồng thời. Chẳng hạn thư viện cho tối đa 8 phiên làm việc với thiết bị thì chỉ cấp 8 permits, dù có 100.000 virtual threads. Nhớ giải phóng permit trong `finally`; `Semaphore.acquire()` nên diễn ra **trước khi vào native path** để phần chờ permit có thể unmount trên virtual thread.

### Cách D: ưu tiên native API có timeout/asynchronous completion

Nếu thư viện cung cấp API không đồng bộ/callback từ thread riêng hoặc khả năng hủy, hãy cân nhắc nó sau khi đánh giá ownership, callback thread, memory lifetime và tương tác với Java. Một native API “async” có thể vẫn giữ lock nội bộ, spawn thread đắt tiền hay gọi callback giữ native frame; cần chứng minh thực nghiệm cơ chế chờ/hoàn thành.

### Cách E: nâng JDK khi bottleneck là monitor, không phải native

Nếu JFR cho thấy `synchronized` gây pin trên JDK 21–23, JDK 24+ có thể giải quyết bằng JEP 491. Nhưng nếu hàm C đang ngủ 500 ms trên carrier, nâng JDK chỉ vì JEP 491 **không đảm bảo** xử lý được native wait. Hãy phân loại đúng nguyên nhân trước khi đổi `synchronized`, thay JNI/FFM hay điều chỉnh scheduler.

## 8. Những lỗi chẩn đoán và thiết kế phổ biến

1. **“Không có `jdk.VirtualThreadPinned` nên native không block.”** Sai: native syscall blocking có thể chiếm carrier mà không tạo Java parking event tương ứng.
2. **“Đổi sang FFM thì sẽ hết pinning của JNI.”** Sai: FFM đổi giao diện và quản lý foreign call; downcall blocking vẫn chạy native trên OS thread.
3. **“Java 24 đã chữa mọi pinning.”** Sai: JEP 491 tập trung vào monitors; native/VM frames vẫn cần quan sát.
4. **“Lệnh native nào cũng làm nghẽn hệ thống.”** Sai: lệnh rất nhanh thường ít ảnh hưởng; quan trọng là thời gian, tần suất và đồng thời.
5. **“Tăng `jdk.virtualThreadScheduler.parallelism` để chữa triệt để.”** Thay đổi cấu hình có thể che bớt triệu chứng trong bài đo nhưng không bỏ blocking native hay giới hạn downstream; cần xem đúng bottleneck.
6. **“`Future.cancel(true)` chắc chắn hủy được hàm C.”** Không bảo đảm. Native API phải hỗ trợ hủy hoặc timeout theo giao thức của nó.
7. **“Platform thread pool tự tạo backpressure đủ tốt.”** `newFixedThreadPool` có hàng đợi mặc định không giới hạn; phải thiết kế admission/backpressure nếu số task không được kiểm soát.
8. **“Có thể chuyển native call sang bất kỳ thread nào.”** Không phải mọi thư viện native đều thread-safe hay không có thread-affinity; phải đọc API contract.
9. **“Native memory tự có GC giống heap Java.”** FFM `Arena` và JNI thường đòi quản lý lifetime/ownership rõ ràng; dùng segment sau khi arena đóng hoặc giữ pointer không hợp lệ gây lỗi nghiêm trọng, độc lập với pinning.
10. **“Đẩy mọi native function sang platform pool luôn nhanh hơn.”** Offload cũng có chi phí task queue/context switching và có thể tăng latency; cân nhắc nếu native blocking **thực sự dài và thường xuyên**.

## 9. Quy trình audit một JNI/FFM library trước khi triển khai Virtual Threads

1. **Lập bản đồ call path:** từ task Java → wrapper → JNI/FFM → thư viện C → syscall/lock/callback → trở về Java; đánh dấu những điểm có thể chờ hoặc phụ thuộc OS thread.
2. **Phân loại native call:** quick/CPU-bound/long blocking/callback/thread-affine; xác minh timeout, hủy, signal và thread-safety.
3. **Khóa phiên bản để đo:** JDK 21–23 khác JDK 24+ về monitors; FFM chuẩn từ JDK 22. Ghi lại ABI, OS và phiên bản native library.
4. **Thu JFR và stack/OS samples:** tìm cả Java-side pinning và native blocking vô hình với JFR event. Không kết luận chỉ từ trace đơn lẻ.
5. **Giới hạn tài nguyên trước khi mở tải:** native calls in-flight, queue, native memory, connection/file descriptors và downstream capacity.
6. **So sánh phương án:** direct virtual-thread call; API Java blocking tương thích; native platform executor có giới hạn; native asynchronous API. Kiểm tra throughput, tail latency, CPU, OS threads, lỗi và cancellation.
7. **Chốt thiết kế dựa trên dữ liệu:** chỉ sửa hotspot đã chứng minh; ghi rõ điều kiện cần đo lại khi nâng JDK hoặc thay JNI/FFM library.

## Tài liệu chuẩn

- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444): giới thiệu tính năng chính thức ở Java 21 và giới hạn native/foreign.
- [JEP 491 — Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491): thay đổi monitor từ JDK 24; không xóa mọi native pinning.
- [JEP 454 — Foreign Function & Memory API](https://openjdk.org/jeps/454): FFM thành API chính thức ở Java 22.
- [Oracle JDK 25 — Virtual Threads](https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html): mount/unmount, native pinning, JFR và thread dump.
- [Oracle JDK 25 — Linker API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Linker.html): downcall, layout và ABI.
- [Oracle JDK 25 — Memory Segments and Arenas](https://docs.oracle.com/en/java/javase/25/core/memory-segments-and-arenas.html): vòng đời native memory.

**Điểm cần nhớ:** JNI và FFM đều có thể giữ carrier khi đi qua mã native; điều gây hại là **chờ lâu và thường xuyên** trên tài nguyên OS thread hữu hạn. JDK 24+ sửa pinning do monitor thông thường, còn đường native phải được audit bằng cả Java/JFR **và quan sát native/OS**, sau đó mới chọn API khác, giới hạn số lời gọi hoặc offload có kiểm soát.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-017">JFR VirtualThreadPinned, VirtualThreadSubmitFailed và ngưỡng ghi sự kiện</a>

<details>
<summary>Xem chi tiết</summary>

# JFR: `VirtualThreadPinned`, `VirtualThreadSubmitFailed` và ngưỡng ghi sự kiện

## 1. Vì sao cần JFR khi dùng virtual threads?

**Java Flight Recorder (JFR)** là bộ ghi sự kiện chẩn đoán có sẵn trong JDK. Với **virtual threads** (chính thức từ Java 21), JFR giúp nhận biết các tình huống làm giảm khả năng mở rộng, đặc biệt:

- **`jdk.VirtualThreadPinned`**: virtual thread bị giữ trên một **carrier** (platform thread thực thi nó) trong lúc có thao tác chờ; carrier không thể được dùng cho virtual thread khác trong khoảng đó. Sự kiện được ghi khi điều kiện ghi được bật và thời lượng đạt ngưỡng.
- **`jdk.VirtualThreadSubmitFailed`**: Java runtime không thể submit công việc cần thiết để **khởi chạy hoặc tiếp tục chạy** một virtual thread, thường gợi ý sự cố tài nguyên hoặc scheduler. Đây là tín hiệu lỗi ở mức runtime, **không phải** ngoại lệ nghiệp vụ thông thường trong `Runnable`.
- **Ngưỡng `threshold`**: lọc các lần pinning ngắn trước khi ghi sự kiện. Ngưỡng giúp giảm nhiễu và chi phí ghi nhận; nó **không** thay đổi lịch chạy, không ngăn pinning và không tự chữa sự cố.

Đây là kiến thức về **Java/JVM concurrency thuần túy**. Ví dụ dùng `java.lang.Thread`, `jdk.jfr` và công cụ đi kèm JDK; không yêu cầu framework ứng dụng.

> **Nền tảng:** virtual thread được JDK lập lịch lên một hoặc nhiều platform thread gọi là *carrier*. Khi virtual thread chờ I/O có hỗ trợ, runtime thường có thể **unmount** nó để giải phóng carrier. *Pinning* xảy ra khi virtual thread không thể unmount trong lúc bị chặn; nếu kéo dài và lặp lại nhiều lần, carrier bị chiếm và throughput có thể giảm. Pinning tự nó không chứng minh ứng dụng bị deadlock hoặc sai logic.

## 2. Hai sự kiện JFR khác nhau như thế nào?

| Tiêu chí | `jdk.VirtualThreadPinned` | `jdk.VirtualThreadSubmitFailed` |
| --- | --- | --- |
| Phát hiện | Một lần **blocking while pinned** đủ dài để được ghi | Lỗi submit/tiếp tục thực thi virtual thread ở runtime |
| Ý nghĩa | Carrier không được giải phóng trong thời gian chờ | Runtime không lập lịch được một công việc virtual thread như mong muốn |
| Trạng thái mặc định trong cấu hình JFR `default.jfc` | **Bật**, ngưỡng thường là **20 ms** | **Bật** |
| Cần điều chỉnh `threshold`? | Có thể: 20 ms, 5 ms, 0 ms... | Không dùng ngưỡng thời lượng theo cách của pinned event |
| Có thể vắng mặt khi chạy bình thường? | Có: không có blocking pinning, hoặc dưới ngưỡng | Có: không xảy ra lỗi submit |
| Hướng điều tra | Call stack, blocking operation, JDK version, thư viện native, lượng carrier bị giữ | Tình trạng tài nguyên hệ thống/JVM, scheduler, số công việc chờ, lỗi liên quan |

Các event khác như `jdk.VirtualThreadStart` và `jdk.VirtualThreadEnd` theo dõi thời điểm bắt đầu/kết thúc virtual thread nhưng **mặc định không bật** trong cấu hình điển hình, vì lượng virtual thread lớn có thể sinh nhiều sự kiện. `jdk.ThreadSleep`, `jdk.SocketRead`, `jdk.JavaMonitorEnter` cũng có thể hữu ích khi ghép bối cảnh; chúng **không đồng nghĩa** với pinning.

### 2.1. `VirtualThreadPinned` đo gì?

Không phải cứ virtual thread đang trong `synchronized` là một event sẽ xuất hiện. Để có một sự kiện `jdk.VirtualThreadPinned`, phải có thao tác **bị chặn trong trạng thái pinned**, JFR đang ghi, sự kiện được bật và thời lượng thỏa ngưỡng. Event thường gồm thời điểm, duration, thông tin thread và stack trace (nếu bật). **Tên trường bổ sung** như nguyên nhân pinning hoặc blocking operation có thể thay đổi theo JDK; kiểm tra schema qua `jfr metadata` thay vì hard-code mọi tên trường.

Một số tình huống thực tế:

1. Trên **JDK 21–23**, blocking I/O/`Thread.sleep` **bên trong** một `synchronized` lâu và thường xuyên có thể giữ carrier.
2. Native/foreign calls hoặc vùng VM không thể unmount virtual thread vẫn có thể gây pinning trên các JDK mới hơn.
3. Tranh chấp monitor, chờ khởi tạo lớp và các chi tiết runtime khác cần xem theo phiên bản và event thực tế.

**Thay đổi quan trọng từ JDK 24:** JEP 491 cải tiến cơ chế monitor để virtual thread có thể unmount trong phần lớn trường hợp blocking ở `synchronized`. Vì vậy, một ví dụ “`synchronized` + `sleep` tạo pinned event” có thể hoạt động trên JDK 21 nhưng **không còn tạo event** trên JDK 24+. JFR `VirtualThreadPinned` vẫn hữu ích cho các nguyên nhân pinning còn lại; không nên suy luận “không thấy sự kiện” nghĩa là không có bottleneck.

### 2.2. `VirtualThreadSubmitFailed` báo gì?

Đây là event về **việc submit virtual thread** vào cơ chế thực thi, gồm khi tạo mới hoặc khi virtual thread cần được tiếp tục sau trạng thái chờ. Nó phản ánh một nỗ lực submit thất bại, có thể do áp lực tài nguyên hoặc trạng thái scheduler. Khi gặp sự kiện này, hãy kiểm tra tài nguyên và bối cảnh cùng thời điểm; không thể kết luận nguyên nhân duy nhất chỉ từ tên event.

Phân biệt:

- Một task ném `IllegalArgumentException`/`IOException` rồi kết thúc: **không tự động** phát sinh `VirtualThreadSubmitFailed`.
- `Future.get()` trả `ExecutionException`: đây là đường truyền lỗi task; **không đồng nghĩa** với submit failure của runtime.
- Hết connection trong connection pool hoặc request timeout: có thể gây thất bại nghiệp vụ nhưng **không mặc nhiên** là event này.
- Không có `VirtualThreadSubmitFailed` trong recording: chỉ biết **không ghi nhận** sự kiện đó trong phạm vi và cấu hình đã ghi; không thể xác nhận mọi tài nguyên đều khỏe.

## 3. Ngưỡng `threshold`: hiểu đúng con số 20 ms

Trong các cấu hình JFR JDK thông dụng, `jdk.VirtualThreadPinned` được bật với **`threshold=20 ms`**. Điều này có nghĩa event ghi lại khi thời lượng blocking pinning **đủ dài để vượt qua bộ lọc ngưỡng**; không phải mọi đoạn code chạy trên virtual thread lâu hơn 20 ms đều tạo event.

Ví dụ minh họa:

| Thao tác | Thời lượng | Cấu hình ngưỡng 20 ms | Có khả năng thành `Pinned` event? |
| --- | ---: | ---: | --- |
| Virtual thread sleep 200 ms và unmount bình thường | 200 ms | 20 ms | **Không**, vì không bị pinned |
| Blocking while pinned 8 ms | 8 ms | 20 ms | **Không** vì dưới ngưỡng |
| Blocking while pinned 75 ms | 75 ms | 20 ms | **Có** nếu đang ghi và event được bật |
| Lỗi scheduler submit virtual thread | Không phải duration của pinning | Không áp dụng | `VirtualThreadSubmitFailed` có thể được ghi |

**Các mức ngưỡng điển hình:**

- **20 ms:** điểm bắt đầu hợp lý để phát hiện pinning tương đối dài với nhiễu thấp (giá trị mặc định thường gặp).
- **5 ms:** tăng độ nhạy trong lần chẩn đoán ngắn, có thể tăng số lượng sự kiện và chi phí stack trace.
- **0 ms:** bỏ bộ lọc thời lượng; dễ có rất nhiều sự kiện. Chỉ dùng khi có mục tiêu đo cụ thể, thời gian đủ ngắn và theo dõi dung lượng/overhead.
- **Cao hơn 20 ms:** phù hợp khi chỉ muốn tìm các ca blocking dài nhất, nhưng sẽ bỏ sót nhiều ca ngắn cộng dồn.

`threshold` là **event setting**, không phải `period` hay `cutoff` của file JFR. `period` dành cho các event định kỳ; `maxsize`/`maxage` quản lý dung lượng/tuổi dữ liệu recording, không xác định mức duration cần để phát `VirtualThreadPinned`.

Không nên chuyển một event `duration=30 ms` thành kết luận rằng **người dùng bị chậm đúng 30 ms**: đó là khoảng thời gian gắn với event, còn độ trễ request có thể gồm nhiều thành phần khác.

## 4. Kiểm tra JDK và event schema trước khi phân tích

Các lệnh sau chạy từ JDK có công cụ `java`/`jfr`. Java 21+ được khuyến nghị cho virtual threads.

~~~bash
java -version
jfr help
jfr metadata --events jdk.VirtualThreadPinned
jfr metadata --events jdk.VirtualThreadSubmitFailed
~~~

`jfr metadata` in ra định nghĩa và các trường event mà **JDK hiện tại** biết. Trên các phiên bản JDK có bổ sung trường, đầu ra có thể khác. Nếu công cụ không tìm thấy một event, hãy kiểm tra JDK thực thi `jfr` có phải cùng bản với JVM tạo recording hay không. Với file `recording.jfr`, có thể tra metadata **của chính bản ghi**:

~~~bash
jfr metadata recording.jfr
~~~

Để xem cấu hình thực tế đang dùng, kiểm tra `$JAVA_HOME/lib/jfr/default.jfc` (Windows: `%JAVA_HOME%\lib\jfr\default.jfc`), hoặc cấu hình `.jfc` tùy chỉnh đã nạp. Giá trị mặc định có thể thay đổi giữa bản JDK/vendor/cấu hình; **không dùng giá trị 20 ms như quy luật bắt buộc của mọi recording**.

## 5. Ghi và đọc JFR bằng lệnh JDK

### 5.1. Khởi động ghi cùng chương trình

Ví dụ chương trình Java có main class `PinnedWorkloadDemo`, dùng cấu hình mặc định và ghi file khi JVM kết thúc:

~~~bash
java -XX:StartFlightRecording:settings=default,filename=vt-events.jfr,dumponexit=true PinnedWorkloadDemo
~~~

Khi JVM tắt bình thường, file `vt-events.jfr` chứa các event JFR đã ghi. Nếu khối công việc quá ngắn, ít mẫu hoặc không có sự kiện đủ ngưỡng, bản ghi có thể **không** chứa cả hai event đang tìm. Đường dẫn là tương đối theo thư mục làm việc hiện tại. Có thể dùng đường dẫn tuyệt đối khi triển khai thật.

Kiểm tra file và lọc sự kiện:

~~~bash
jfr summary vt-events.jfr
jfr print --events jdk.VirtualThreadPinned,jdk.VirtualThreadSubmitFailed vt-events.jfr
~~~

Nếu cần xem bối cảnh của các virtual thread:

~~~bash
jfr print --events jdk.VirtualThreadStart,jdk.VirtualThreadEnd,jdk.VirtualThreadPinned,jdk.VirtualThreadSubmitFailed vt-events.jfr
~~~

Lưu ý: `Start` và `End` có thể không xuất hiện do mặc định tắt. `jfr print --events` là **bộ lọc hiển thị**, không thể hồi sinh event không được ghi từ đầu. Để xuất theo dạng máy đọc:

~~~bash
jfr print --json --events jdk.VirtualThreadPinned,jdk.VirtualThreadSubmitFailed vt-events.jfr
~~~

Dùng `jfr summary` để biết có bao nhiêu event mỗi loại trong file. Dùng `jfr print` hoặc JDK Mission Control (JMC) để xem call stack, duration và thời điểm; ghép với CPU, socket, lock, GC và load cùng thời gian.

### 5.2. Ghi vào tiến trình đang chạy

Tìm process Java thích hợp:

~~~bash
jcmd -l
~~~

Ví dụ với PID thực tế **12345**:

~~~bash
jcmd 12345 JFR.start name=vt-diagnosis settings=default filename=vt-live.jfr
jcmd 12345 JFR.dump name=vt-diagnosis filename=vt-live.jfr
jcmd 12345 JFR.stop name=vt-diagnosis
~~~

PID chỉ là ví dụ. `JFR.start` bắt đầu recording trong JVM đích; `JFR.dump` lấy snapshot vào file; `JFR.stop` kết thúc recording. Để kiểm tra tham số tương ứng với JVM đang chạy, dùng:

~~~bash
jcmd 12345 help JFR.start
jcmd 12345 JFR.check
~~~

Trước khi phân tích, xác định **khoảng thời gian** đã ghi trùng với lúc ứng dụng có vấn đề. Một file ghi sau khi sự cố kết thúc có thể không có event tương ứng.

## 6. Tùy chỉnh ngưỡng với `.jfc`

Từ JDK 19, công cụ `jfr configure` hỗ trợ tạo cấu hình mới dựa trên `default.jfc`. Ví dụ giảm ngưỡng `VirtualThreadPinned` từ 20 ms xuống **5 ms**, tiếp tục bật `VirtualThreadSubmitFailed` và giữ stack trace:

~~~bash
jfr configure --input default --output vt-5ms.jfc "jdk.VirtualThreadPinned#enabled=true" "jdk.VirtualThreadPinned#threshold=5ms" "jdk.VirtualThreadPinned#stackTrace=true" "jdk.VirtualThreadSubmitFailed#enabled=true"
~~~

Sau đó chạy:

~~~bash
java -XX:StartFlightRecording:settings=vt-5ms.jfc,filename=vt-5ms.jfr,dumponexit=true PinnedWorkloadDemo
jfr print --events jdk.VirtualThreadPinned,jdk.VirtualThreadSubmitFailed vt-5ms.jfr
~~~

Nếu chỉ cần kiểm tra giá trị threshold, dùng `jfr help configure` và mở `vt-5ms.jfc`: phần tương ứng sẽ có dạng XML (trích đoạn **không phải** file đầy đủ):

~~~xml
<event name="jdk.VirtualThreadPinned">
  <setting name="enabled">true</setting>
  <setting name="stackTrace">true</setting>
  <setting name="threshold">5 ms</setting>
</event>
~~~

**Vì sao không cấu hình `VirtualThreadSubmitFailed#threshold`?** Submit failure là tín hiệu thất bại, không biểu diễn “blocking duration” cần lọc kiểu `VirtualThreadPinned`. Chỉ dùng các setting thực sự được event hỗ trợ. Có thể xem sự kiện và các setting hiện hữu trong `default.jfc` hoặc metadata. Việc sử dụng ngưỡng thấp với `VirtualThreadPinned` không làm lỗi submit xuất hiện nhiều hơn.

Cách đọc kết quả sau điều chỉnh:

1. Nếu ở ngưỡng 20 ms không thấy pinned event nhưng ở 5 ms thấy nhiều event 6–15 ms, chúng có thể là những lần pinning ngắn đã bị lọc trước đó.
2. Số event tăng sau khi hạ ngưỡng **không tự chứng minh** chương trình trở nên chậm hơn. Đó là thay đổi cách đo.
3. Để so sánh hai thời điểm, giữ cùng cấu hình, cùng JDK và workload tương đương. Không so sánh trực tiếp số raw events từ hai recording khác thời lượng hoặc ngưỡng.
4. Một event dài cũng chưa đủ chứng minh tác động throughput lớn; cần biết tần suất, số carrier bị chiếm, tình trạng scheduler và tải thực tế.

## 7. Ví dụ chạy được: minh họa pinning trên JDK 21–23

Ví dụ này có một virtual thread gọi `Thread.sleep(120)` khi đang giữ monitor `synchronized`. **Trên JDK 21–23**, đây là tình huống điển hình tạo `jdk.VirtualThreadPinned` vượt ngưỡng 20 ms. **Trên JDK 24+**, monitor được cải tiến nên ví dụ thường không còn gây pinning; đó là hành vi đúng theo phiên bản, không phải lỗi ghi JFR.

Lưu tệp **`PinnedWorkloadDemo.java`**:

~~~java
public class PinnedWorkloadDemo {
    private static final Object LOCK = new Object();

    public static void main(String[] args) throws InterruptedException {
        Thread worker = Thread.ofVirtual()
                .name("vt-pinning-demo")
                .start(() -> {
                    synchronized (LOCK) {
                        try {
                            Thread.sleep(120);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });

        worker.join();
        System.out.println("Workload completed");
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 PinnedWorkloadDemo.java
java -XX:StartFlightRecording:settings=default,filename=vt-demo.jfr,dumponexit=true PinnedWorkloadDemo
jfr print --events jdk.VirtualThreadPinned,jdk.VirtualThreadSubmitFailed vt-demo.jfr
~~~

**Stdout do chương trình Java tạo ra**, không tính thông báo khởi tạo JFR từ JVM:

~~~text
Workload completed
~~~

JVM có thể in thêm các dòng `[info][jfr]`; thông tin này thay đổi theo phiên bản. Kết quả `jfr print` không có stdout cố định:

- Trên **JDK 21–23**, nếu JFR đã ghi đúng thời gian và event đủ ngưỡng, sẽ thường có bản ghi `jdk.VirtualThreadPinned` với stack trace trỏ về `PinnedWorkloadDemo`.
- Trên **JDK 24+**, không nên chờ event chỉ từ `synchronized` trong ví dụ này.
- `jdk.VirtualThreadSubmitFailed` hầu như sẽ **không xuất hiện** trong ví dụ bình thường; không cố tình gây lỗi thiếu tài nguyên để tạo sự kiện.

**Tại sao `Thread.sleep(120)` một mình không đủ?** Virtual thread có thể unmount khi sleep thông thường. Trong ví dụ, điều quyết định trên JDK 21–23 là sleep diễn ra **khi đang giữ Java monitor**. Không đánh đồng duration của `Thread.sleep` với `VirtualThreadPinned`.

## 8. Tạo JFR recording bằng API Java (điều chỉnh ngưỡng trong code)

Nếu cần tự động hóa chẩn đoán trên JVM của chương trình, dùng API `jdk.jfr.Recording`. Ví dụ sau bật cả hai event, đặt `VirtualThreadPinned` ngưỡng **5 ms**, ghi `vt-api.jfr` rồi dừng sau khi worker kết thúc. Không phụ thuộc vào CLI `jfr configure`.

Lưu thành **`VirtualThreadJfrApiDemo.java`**:

~~~java
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import jdk.jfr.Recording;

public class VirtualThreadJfrApiDemo {
    public static void main(String[] args) throws Exception {
        Path output = Path.of("vt-api.jfr");
        Object lock = new Object();

        try (Recording recording = new Recording()) {
            recording.enable("jdk.VirtualThreadPinned")
                    .withThreshold(Duration.ofMillis(5))
                    .withStackTrace();
            recording.enable("jdk.VirtualThreadSubmitFailed")
                    .withStackTrace();

            recording.start();

            Thread worker = Thread.ofVirtual().start(() -> {
                synchronized (lock) {
                    try {
                        Thread.sleep(80);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });

            worker.join();
            recording.stop();
            recording.dump(output);
        }

        System.out.println("JFR file exists: " + Files.exists(output));
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 VirtualThreadJfrApiDemo.java
java VirtualThreadJfrApiDemo
jfr print --events jdk.VirtualThreadPinned,jdk.VirtualThreadSubmitFailed vt-api.jfr
~~~

**Stdout chính từ Java** khi có quyền ghi file vào thư mục hiện tại:

~~~text
JFR file exists: true
~~~

`recording.enable(...).withThreshold(...)` là cấu hình event cho recording này, không đổi vĩnh viễn default của JVM. `recording.stop()` trước `dump()` đảm bảo khoảng ghi kết thúc; `try-with-resources` đóng tài nguyên. File vẫn có thể không chứa `VirtualThreadPinned` trên JDK 24+ hoặc nếu JVM không có điều kiện ghi event; `Files.exists` chỉ kiểm tra file được tạo, **không** xác nhận pinning đã xảy ra.

Để biết những event đã thực sự có trong file, dùng `jfr summary vt-api.jfr`. Có thể viết bộ đọc riêng bằng `jdk.jfr.consumer.RecordingFile`, nhưng `jfr print` thường dễ hơn cho lần điều tra đầu.

## 9. Cách điều tra khi sự kiện xuất hiện

### 9.1. Khi thấy `jdk.VirtualThreadPinned`

1. **Xác nhận JDK và cấu hình JFR:** phiên bản/vendor, `enabled`, `threshold`, `stackTrace`, khoảng thời gian đo.
2. **Tìm hotspot:** nhóm sự kiện theo stack trace và blocking operation; ưu tiên đoạn gọi lặp lại nhiều lần và thời lượng lớn, thay vì chỉ sửa một event dài hiếm gặp.
3. **Xem carrier đang giữ tài nguyên:** có bao nhiêu virtual thread chờ trong thời điểm peak, số carrier đang bận, độ trễ request và throughput. Một stack trace chỉ là điểm xuất phát để kiểm tra mã.
4. **Phân biệt nguyên nhân theo JDK:** trên JDK 21–23, xem blocking trong monitor; trên JDK 24+, xem native/foreign calls, VM frames, các trường hợp đặc biệt.
5. **Giảm tác động đúng chỗ:** thu hẹp synchronized section, tránh blocking I/O khi đang giữ monitor trên JDK cũ, hoặc thay bằng khóa `ReentrantLock` khi semantics phù hợp. Với native calls, kiểm tra thư viện và kiến trúc gọi thay vì mặc định chỉ sửa `synchronized`.
6. **Đo lại** với cùng workload/cấu hình JFR; kiểm tra throughput, latency, CPU, GC và số event, thay vì chỉ xem số stack traces giảm.

Ví dụ **trước–sau có điều kiện trên JDK 21–23** (đoạn mã minh họa nằm trong phương thức, không phải file độc lập):

~~~java
// Trước: pinning có thể xảy ra khi blocking I/O trong monitor.
synchronized (lock) {
    readFromRemoteService(); // giả định đây là blocking I/O
}

// Sau: nếu logic cho phép, làm I/O ngoài critical section.
var data = readFromRemoteService();
synchronized (lock) {
    updateSharedState(data);
}
~~~

Hai bản **không tự động tương đương về mặt đồng bộ**: nếu phải bảo đảm dữ liệu không đổi giữa thao tác đọc và cập nhật, cần thiết kế giao thức đồng bộ khác. Không di chuyển I/O tùy tiện làm sai bất biến dữ liệu. Một `ReentrantLock` cũng vẫn có thể gây contention; mục tiêu là tránh giữ carrier và giảm thời gian giữ khóa, chứ không phải loại bỏ mọi khóa.

### 9.2. Khi thấy `jdk.VirtualThreadSubmitFailed`

1. Đọc toàn bộ event ở **cùng thời điểm**, stack trace và thông tin thêm nếu có; không đoán tên trường khác nhau giữa các bản JDK.
2. Kiểm tra CPU, RAM/heap, native memory, cgroup/container limits, số platform threads, process limits, cường độ khởi tạo/đánh thức virtual thread.
3. Ghép với `OutOfMemoryError`, sự cố scheduler, JVM logs và các cảnh báo liên quan. Có thể thất bại do tài nguyên, nhưng event tự nó **chưa chứng minh** heap OOM.
4. Kiểm tra thiết kế tải: số request đến, fan-out, công việc CPU-bound kéo dài, tần suất retry; áp dụng backpressure và giới hạn **tài nguyên đích** như database connections hoặc remote API concurrency.
5. Nếu submit failure xảy ra trong production, ưu tiên phân tích và tái hiện có kiểm soát; **không** thử cố tình cạn kiệt tài nguyên máy thật chỉ để tạo JFR event.

Đừng coi `VirtualThreadSubmitFailed` giống `RejectedExecutionException` của một `ThreadPoolExecutor` do người dùng cấu hình. Chúng liên quan những đường xử lý khác nhau; cần dựa trên bằng chứng của JVM và code ứng dụng.

### 9.3. Khi không thấy cả hai event

Kiểm tra tuần tự:

- JVM có bật recording và ghi đúng đoạn thời gian xảy ra sự cố không?
- File có event khác trong `jfr summary` không?
- `VirtualThreadPinned` có bật, `stackTrace` có bật, ngưỡng có đang quá cao không?
- Trên JDK 24+, testcase cũ dùng `synchronized` có còn tạo pinning không?
- Event `VirtualThreadSubmitFailed` có thể thật sự không xảy ra, dù request application thất bại.
- Workload có nút thắt khác: saturated database, socket timeout, CPU, blocking I/O không liên quan pinning, monitor contention thông thường, queue, GC?

**Không có event ≠ không có vấn đề.** JFR chỉ phản ánh sự kiện được cấu hình và thực sự ghi nhận trong cửa sổ quan sát.

## 10. Quan sát bổ sung và tính thực tiễn

Để xem virtual thread trong tiến trình đang chạy, JDK có thể hỗ trợ:

~~~bash
jcmd 12345 Thread.dump_to_file -format=json vt-threads.json
~~~

Thay PID bằng tiến trình thật. Thread dump có ích để xem các virtual thread đang chờ; nó là **ảnh chụp trạng thái**, còn JFR tập hợp sự kiện theo thời gian. Trên các JDK có hỗ trợ, có thể tham khảo thêm:

~~~bash
jcmd 12345 Thread.vthread_scheduler
jcmd 12345 Thread.vthread_pollers
~~~

Các diagnostic command có thể thay đổi theo phiên bản; dùng `jcmd 12345 help` để biết những lệnh JVM thực sự hỗ trợ.

Không nên bật đồng loạt `VirtualThreadStart`/`VirtualThreadEnd` cho hệ thống tạo rất nhiều virtual thread suốt thời gian dài mà không đo overhead và kích thước recording. Với `VirtualThreadPinned` cũng vậy, khi giảm `threshold` hãy ưu tiên **recording ngắn có mục đích** và điều chỉnh `maxsize`/`maxage` theo tài nguyên sẵn có.

### Checklist trước khi kết luận

- [ ] Đã ghi lại JDK version/vendor và chế độ recording.
- [ ] Đã biết `VirtualThreadPinned` có bật và ngưỡng thực tế; không suy từ giá trị mặc định nếu có custom `.jfc`.
- [ ] Đã phân biệt event duration với request latency và hiện tượng pinning với blocking thông thường.
- [ ] Đã kiểm tra thay đổi JEP 491 cho JDK 24+.
- [ ] Đã phân biệt submit failure của virtual-thread scheduler với exception nghiệp vụ.
- [ ] Đã đọc event trong ngữ cảnh thời gian, stack trace, CPU, scheduler, tài nguyên và cường độ workload.
- [ ] Khi sửa code, đã chạy lại benchmark/workload so sánh với cùng cấu hình đo.

## 11. Tài liệu chính thức

- [Oracle Java 21: Virtual Threads và JFR events](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html) — trạng thái mặc định và ngưỡng 20 ms.
- [Oracle Java 24: Virtual Threads](https://docs.oracle.com/en/java/javase/24/core/virtual-threads.html) — hành vi pinning mới khi sử dụng monitor.
- [Oracle Java 26: Virtual Threads và ví dụ JFR events](https://docs.oracle.com/en/java/javase/26/core/virtual-threads.html) — cách đọc blocking operation, pinned reason và các thao tác chẩn đoán.
- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444) — thiết kế và các JFR event liên quan.
- [JEP 491 — Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491) — cải tiến `synchronized` từ JDK 24.
- [`jdk.jfr` package documentation (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jfr/jdk/jfr/package-summary.html) — `enabled`, `threshold` và `stackTrace`.
- [JDK 21 `jfr` command reference](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jfr.html) — `jfr configure`, `summary`, `metadata` và `print`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-018">Giới hạn ThreadMXBean và cách dùng jcmd Thread.dump_to_file cho virtual threads</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của ThreadMXBean và cách dùng jcmd Thread.dump_to_file để quan sát Virtual Threads

## 1. Vì sao kiểm tra thread theo cách cũ dễ dẫn đến kết luận sai?

Từ Java 21, **virtual thread** là một `java.lang.Thread` do JVM quản lý. Nó phù hợp với nhiều công việc đồng thời phải chờ I/O; JVM có thể tạm dừng một virtual thread và dùng platform thread (carrier) để chạy công việc khác. Một ứng dụng có thể đang có hàng nghìn virtual threads trong khi số platform threads tương đối ít.

Vì vậy, việc kiểm tra một giá trị như `ManagementFactory.getThreadMXBean().getThreadCount()` rồi kết luận “ứng dụng chỉ có vài chục thread” **không đúng với tổng số virtual threads**. API này được định nghĩa để quản lý **platform threads**, không quản lý virtual threads. Muốn nhìn thấy stack của virtual threads tại một thời điểm, hãy dùng công cụ chẩn đoán hỗ trợ chúng như `jcmd <PID> Thread.dump_to_file`.

Phạm vi bài này là Java SE/JDK nói chung. Các ví dụ sử dụng **JDK 21+** và chạy được mà không cần framework.

## 2. Hiểu ranh giới: platform thread, carrier và virtual thread

- **Platform thread**: thread Java gắn với một luồng hệ điều hành. `ThreadMXBean` tập trung theo dõi nhóm này.
- **Virtual thread**: thread Java nhẹ, được JVM lập lịch. Một tác vụ đang chờ có thể tạm thời không chiếm carrier.
- **Carrier thread**: platform thread mà scheduler đang dùng để chạy một virtual thread trong khoảng nó được mount. Một carrier có thể lần lượt phục vụ nhiều virtual threads, nên **không có tỷ lệ 1:1**.

Ví dụ: một ứng dụng có 4.000 virtual threads đang chờ tác vụ I/O. `ThreadMXBean.getThreadCount()` có thể vẫn chỉ báo một lượng platform threads nhỏ. Hai giá trị đo **hai đối tượng khác nhau**; giá trị nhỏ của `ThreadMXBean` không chứng minh ứng dụng không có tồn đọng tác vụ.

```text
                Java process (JVM)
┌───────────────────────────────────────────────┐
│  Virtual threads (công việc logic)            │
│  VT-1  ─── chờ I/O                             │
│  VT-2  ─── chờ I/O                             │
│  VT-3  ─── đang chạy                           │
│  VT-4  ─── có thể sẵn sàng chạy                │
│  ...                                          │
│                     │                         │
│           JVM scheduler/carriers              │
│                     ▼                         │
│  Platform threads (OS threads)                 │
│  carrier-A, carrier-B, main, GC/JVM threads... │
└───────────────────────────────────────────────┘
  ThreadMXBean       → platform threads
  jcmd dump_to_file  → platform + virtual threads
```

Hình trên chỉ minh họa sự khác nhau về phạm vi quan sát, không mô tả số carrier cố định hay bảo đảm cách JVM lập lịch một tác vụ cụ thể.

## 3. ThreadMXBean hỗ trợ gì, giới hạn ở đâu?

`java.lang.management.ThreadMXBean` lấy qua `ManagementFactory.getThreadMXBean()` (hoặc JMX với ObjectName `java.lang:type=Threading`). Theo Java SE 21, giao diện này **không hỗ trợ theo dõi/quản lý virtual threads**.

| Phương thức | Giá trị trong ứng dụng có virtual threads |
| --- | --- |
| `getThreadCount()` | Số **platform threads** đang sống; không cộng số virtual threads |
| `getPeakThreadCount()` | Đỉnh số platform threads, không phải đỉnh số virtual threads |
| `getTotalStartedThreadCount()` | Số platform threads từng được khởi động, không cộng virtual threads |
| `getAllThreadIds()` | ID của các platform threads đang sống; không liệt kê ID virtual threads |
| `dumpAllThreads(...)` | `ThreadInfo`/stack của platform threads, không dump mọi virtual thread |
| `getThreadInfo(virtualThreadId)` | Trả `null` cho ID virtual thread, dù virtual thread đó còn sống |
| `findDeadlockedThreads()` / `findMonitorDeadlockedThreads()` | Phát hiện chu trình deadlock trong phạm vi platform threads; không phát hiện chu trình có virtual thread |
| `getThreadCpuTime(id)` | API CPU-time quản lý platform threads; không dùng làm bộ đo CPU-time của từng virtual thread |

Một số sai lầm thường gặp:

- Đặt cảnh báo “số thread tổng” theo `getThreadCount()` rồi nghĩ rằng nó đã tính mọi virtual thread.
- Gọi `dumpAllThreads(true, true)` và coi đó là ảnh chụp đầy đủ mọi công việc concurrent.
- Tìm virtual thread trong `getAllThreadIds()` và suy diễn rằng thread chưa được tạo vì không thấy ID.
- Dùng `Thread.getAllStackTraces()` để liệt kê hết virtual threads: từ JDK 21 API này cũng chỉ trả về platform threads.
- Thấy nhiều carrier thì cho rằng có bằng ấy virtual threads, hoặc thấy ít carrier thì cho rằng có ít yêu cầu.

**ThreadMXBean vẫn hữu ích** cho việc xem platform threads, thông tin đồng bộ liên quan và xu hướng số carrier/OS threads, nhưng phạm vi của nó phải được ghi rõ trên dashboard. Nếu cần số tác vụ đang chạy, đang chờ I/O, thời gian xử lý và timeout của ứng dụng, hãy bổ sung metric ở tầng xử lý tác vụ. Đừng cố suy số virtual threads chỉ từ platform-thread count.

## 4. Thực hành: một chương trình Java 21 có 12 virtual threads đang chờ

Ví dụ sau giữ 12 virtual threads sống trong lúc người học chạy `jcmd` từ cửa sổ terminal khác. Để dễ tìm trong dump, các virtual threads đều có tên bắt đầu bằng `demo-vt-`. Chương trình tự in PID của JVM, so sánh với `ThreadMXBean` và giữ trạng thái khoảng 90 giây.

Tạo file `VirtualThreadDumpDemo.java`:

```java
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class VirtualThreadDumpDemo {
    private static final int WORKERS = 12;

    public static void main(String[] args) throws Exception {
        CountDownLatch ready = new CountDownLatch(WORKERS);
        CountDownLatch release = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();

        for (int i = 0; i < WORKERS; i++) {
            Thread thread = Thread.ofVirtual()
                    .name("demo-vt-" + i)
                    .start(() -> {
                        ready.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
            workers.add(thread);
        }

        // Tất cả virtual threads đã đi đến đoạn báo ready.
        ready.await();

        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long sampleVirtualThreadId = workers.get(0).threadId();
        boolean listed = Arrays.stream(bean.getAllThreadIds())
                .anyMatch(id -> id == sampleVirtualThreadId);

        System.out.println("virtualThreadsStarted=" + WORKERS);
        System.out.println("sampleVirtualThreadListedByMXBean=" + listed);
        System.out.println("pid=" + ProcessHandle.current().pid());
        System.out.println("platformThreadsByMXBean=" + bean.getThreadCount());
        System.out.println("Run jcmd in another terminal within 90 seconds");

        try {
            Thread.sleep(90_000);
        } finally {
            // Cho virtual threads kết thúc và tránh rò công việc ở ví dụ.
            release.countDown();
            for (Thread thread : workers) {
                thread.join();
            }
        }
    }
}
```

Biên dịch/chạy bằng **JDK 21 trở lên**:

```bash
javac VirtualThreadDumpDemo.java
java VirtualThreadDumpDemo
```

Ví dụ phần đầu output (PID và số platform threads **không cố định**):

```text
virtualThreadsStarted=12
sampleVirtualThreadListedByMXBean=false
pid=<PID thực tế do chương trình in>
platformThreadsByMXBean=<số thay đổi theo JVM/môi trường>
Run jcmd in another terminal within 90 seconds
```

Vì sao `false` là kết quả mong đợi? `workers.get(0)` là một virtual thread còn sống, nhưng `ThreadMXBean.getAllThreadIds()` theo đặc tả chỉ liệt kê platform threads. `getThreadCount()` cũng có thể đổi theo hoạt động của JVM/scheduler; không có giá trị tuyệt đối nào đúng cho tất cả máy. Dòng `CountDownLatch.await()` khiến tác vụ virtual thread có stack đang chờ rõ ràng trong dump.

Mẹo khi luyện tập: hãy mở hai terminal, chạy chương trình trong terminal thứ nhất, dùng PID do chương trình vừa in trong terminal thứ hai. Nếu hết 90 giây, chạy lại chương trình và lấy PID mới. Không lấy PID của tiến trình `jcmd` thay cho PID ứng dụng.

## 5. Dùng jcmd Thread.dump_to_file từng bước

`jcmd` là tiện ích đi kèm **JDK**, gửi diagnostic command đến một JVM đang chạy. Lệnh `Thread.dump_to_file` tạo thread dump vào file, gồm **platform threads và virtual threads**. So với `ThreadMXBean.dumpAllThreads()`, đây là công cụ thích hợp để quan sát nhiều virtual threads, kể cả virtual threads bị chặn chờ công việc.

### Bước 1: xác định đúng JVM và quyền truy cập

Chạy từ terminal khác trên cùng máy/môi trường có thể attach đến tiến trình Java:

```bash
jcmd -l
```

Lệnh này hữu ích để liệt kê các Java processes nhìn thấy từ môi trường của `jcmd`. Trong container hoặc JVM không hiển thị được trong `jcmd -l`, dùng PID in từ `ProcessHandle.current().pid()`, `ps`, hoặc công cụ tiến trình tương ứng. Đừng mặc định PID ở host giống PID trong container khác PID namespace.

Điều kiện thông thường: công cụ `jcmd` phải tương thích với JVM đích, quyền người dùng đủ để attach, JVM chưa tắt và không chặn Attach API bằng cấu hình. Kiểm tra lệnh chẩn đoán hiện có cho **JVM đích**:

```bash
jcmd 12345 help
jcmd 12345 help Thread.dump_to_file
```

Thay **12345** bằng PID thật. Cú pháp, tùy chọn và định dạng có thể khác giữa các phiên bản JDK; nguồn chính xác khi thao tác là kết quả `help` của JVM đang chạy.

### Bước 2: tạo dump dạng JSON

Trên Linux/macOS, chọn **đường dẫn tuyệt đối** trong filesystem mà tiến trình Java đích có quyền ghi:

```bash
jcmd 12345 Thread.dump_to_file -format=json /tmp/virtual-threads-12345.json
```

Trên Windows, dùng một thư mục đã tồn tại và có thể ghi, chẳng hạn `C:\temp`:

```powershell
jcmd 12345 Thread.dump_to_file -format=json C:\temp\virtual-threads-12345.json
```

Đây là **mẫu lệnh**: PID và đường dẫn phải thay cho đúng môi trường. Đường dẫn được JVM đích sử dụng để ghi file, nên chạy `jcmd` ở host ngoài container không tự động đưa dump ra filesystem của host. Với container, nên chạy `jcmd` bên trong cùng container/namespace hoặc lấy file dump ra bằng cơ chế quản lý container sau khi tạo.

Tạo tên file mới cho mỗi lần dump để tránh lỗi file đã tồn tại. Một số phiên bản hỗ trợ tùy chọn `-overwrite`/`-overwrite=true`; hãy kiểm tra bằng `jcmd <PID> help Thread.dump_to_file` rồi chỉ dùng khi muốn thay thế file cũ.

### Bước 3: tạo dump dạng văn bản

Không ghi `-format` thì định dạng mặc định thường là **plain text** theo `help` của JVM hỗ trợ lệnh:

```bash
jcmd 12345 Thread.dump_to_file /tmp/virtual-threads-12345.txt
```

Hướng dẫn Virtual Threads của Oracle cho Java 21 còn minh họa `-format=text`. Tài liệu của các JDK mới có thể mô tả tên định dạng plain là `plain`. Để tránh phụ thuộc cách đặt tên tùy phiên bản, hãy ưu tiên **`-format=json`** khi cần xử lý bằng công cụ, hoặc dùng mặc định dạng text sau khi đã kiểm tra `help` trên JVM đích.

### Bước 4: xem kết quả

Một thread dump JSON thường có đối tượng gốc `threadDump`, thông tin tiến trình/thời điểm và dữ liệu thread containers. Các phần tử được nhóm theo container của thread; cấu trúc và trường có thể thay đổi theo phiên bản JDK. Với ví dụ trên, hãy tìm chuỗi `demo-vt-` và stack chứa `CountDownLatch.await`.

```bash
# Linux/macOS: chỉ lọc để định vị vị trí trong JSON;
# không dùng grep như trình phân tích JSON có cấu trúc.
grep -n "demo-vt-" /tmp/virtual-threads-12345.json
```

Ví dụ lọc bằng PowerShell khi chạy Windows:

```powershell
Select-String -Path C:\temp\virtual-threads-12345.json -Pattern 'demo-vt-'
```

Khi cần đếm/nhóm thread theo tên hoặc stack, nên dùng **JSON parser** phù hợp với cấu trúc của bản dump thật, không dựa vào việc “mỗi thread là một dòng JSON”. Đọc file dump trực tiếp bằng editor cũng hữu ích khi chẩn đoán một số lượng thread vừa phải.

## 6. Cách đọc virtual thread dump để trả lời câu hỏi thực tế

Dùng một lần dump như **ảnh chụp trạng thái tại thời điểm lấy**. Một dump có thể cho thấy virtual thread đang ở stack nào và đang chờ thao tác gì; nó không tự cho biết thời gian đã chờ bao lâu, nguyên nhân gốc hoặc quãng thời gian trước đó.

| Quan sát | Ý nghĩa có thể xảy ra | Việc nên kiểm chứng thêm |
| --- | --- | --- |
| Nhiều `demo-vt-*` chờ `CountDownLatch.await` | Đúng với thử nghiệm đang cố tình giữ thread | Sau `release.countDown()` chúng có biến mất không? |
| Nhiều virtual threads cùng chờ pool kết nối hoặc semaphore | Nút cổ chai ở quyền truy cập tài nguyên giới hạn | Pool max/active/pending, timeouts, thời gian giữ resource |
| Nhiều stack chờ I/O mạng | Bên ngoài trả lời chậm hoặc có nhiều lời gọi in-flight | Timeout, latency, tỷ lệ lỗi của dịch vụ ngoài |
| Nhiều stack lặp lại tại một khóa/đồng bộ | Có thể có contention hoặc thiết kế khóa chưa phù hợp | Đường sở hữu khóa, thời gian giữ khóa, trạng thái ở nhiều lần dump |
| Số platform threads thấp nhưng dump có rất nhiều virtual threads | `ThreadMXBean` đang đo một tập khác | Đếm và đo tasks trong ứng dụng, quan sát tốc độ vào/ra |

Quy trình thực tế:

1. Ghi nhận triệu chứng cụ thể: request chậm, timeout, dùng tài nguyên cao hoặc không tiến triển.
2. Xác nhận PID, phiên bản JDK, thời điểm lấy và chính sách dữ liệu nhạy cảm trong stack.
3. Lấy một dump bằng `Thread.dump_to_file`; nếu vấn đề kéo dài, lấy vài dump cách nhau hợp lý để so sánh stack có tiến triển hay không.
4. Nhóm theo tên thread, đường stack nổi bật và trạng thái chờ; đối chiếu với metric I/O, connection pool, queue và logs.
5. Xác định tài nguyên nào bị nghẽn, kiểm tra giới hạn đồng thời, timeout, đóng tài nguyên và tốc độ đầu vào trước khi thay đổi cấu hình.

**Giải thích đúng về trạng thái chờ:** virtual thread đang chờ I/O không nhất thiết tiêu tốn một carrier trong suốt thời gian chờ. Chờ có thể là hoạt động bình thường khi workload nhiều I/O. Vấn đề là hàng đợi tăng liên tục, độ trễ xấu đi, tài nguyên không trả về hoặc công việc không còn hoàn thành.

## 7. So sánh với Thread.print, jstack và các API trong code

`Thread.print` và `jstack` theo truyền thống hữu ích khi điều tra platform threads, native frames và vấn đề liên quan JVM/monitor. Tuy nhiên, đặc biệt trong JDK 21, đừng trông chờ một **traditional thread dump** của chúng liệt kê tất cả virtual threads đang unmounted. Cách được Oracle hướng dẫn để nhìn virtual threads là `jcmd Thread.dump_to_file`.

| Công cụ | Khi nên dùng | Giới hạn cần nhớ |
| --- | --- | --- |
| `ThreadMXBean` | Theo dõi số platform threads, stack/đồng bộ của platform threads và JMX metrics | Loại trừ virtual threads |
| `Thread.getAllStackTraces()` | Stack của platform threads thông qua Java API | Không bao quát mọi virtual threads |
| `jcmd <PID> Thread.print` | Traditional dump; thông tin platform threads/JVM bổ trợ | Không thay cho dump đầy đủ virtual threads, mức độ hiển thị tùy JDK |
| `jcmd <PID> Thread.dump_to_file` | Snapshot platform + virtual threads, xuất text/JSON | Không đầy đủ metadata truyền thống như object addresses, native/JNI/heap; khả năng thông tin lock phụ thuộc phiên bản |
| JDK Flight Recorder (JFR) | Phân tích sự kiện, khoảng thời gian, nguyên nhân latency/pinning | Cần cấu hình recording và sự kiện tương ứng |
| Metrics do ứng dụng ghi | Đếm tasks, độ trễ, timeout, queue, resource usage | Phải đặt phép đo tại đúng luồng nghiệp vụ và tài nguyên |

Không suy ra rằng `ThreadMXBean.findDeadlockedThreads() == null` chứng minh **không có deadlock nào** trong ứng dụng sử dụng virtual threads: nó chỉ được thiết kế cho các chu trình deadlock của platform threads. Để xác định sự cố tương tác nhiều khóa/tài nguyên, hãy phân tích stack, thay đổi trạng thái qua nhiều thời điểm và luồng điều khiển nghiệp vụ.

## 8. Quan sát lâu dài: JFR, metric ứng dụng và scheduler (JDK 24+)

Thread dump giúp trả lời **“đang xảy ra chuyện gì bây giờ?”**; JFR và metrics giúp trả lời **“nó diễn ra bao lâu, với tần suất nào và có xu hướng gì?”**.

Trên Java 21, JFR có các sự kiện như:

- `jdk.VirtualThreadStart` và `jdk.VirtualThreadEnd`: vòng đời virtual thread; **mặc định bị tắt** để tránh overhead khi số lượng thread rất lớn.
- `jdk.VirtualThreadPinned`: sự kiện virtual thread bị pin dài hơn ngưỡng đã cấu hình; theo hướng dẫn Java 21, sự kiện này mặc định bật với threshold 20 ms.
- `jdk.VirtualThreadSubmitFailed`: không thể submit/unpark virtual thread, có thể do vấn đề tài nguyên.

Có thể cấu hình recording theo yêu cầu, rồi đọc các sự kiện liên quan trong file `recording.jfr`:

```bash
jfr print --events jdk.VirtualThreadStart,jdk.VirtualThreadEnd,jdk.VirtualThreadPinned,jdk.VirtualThreadSubmitFailed recording.jfr
```

`recording.jfr` phải là file đã được ghi từ một JFR recording; lệnh trên **chỉ đọc**, không khởi tạo recording. Những thiết lập/sự kiện cụ thể nên kiểm tra theo bản JDK đang dùng.

Từ **JDK 24**, JDK bổ sung `jdk.management.VirtualThreadSchedulerMXBean`, cung cấp các chỉ số về scheduler như target parallelism, số carrier platform threads trong scheduler và số virtual threads đang đợi scheduler được lập lịch (các giá trị có thể là ước lượng). Đây là **MXBean khác** với `java.lang.management.ThreadMXBean`, và **không có sẵn trên JDK 21**.

Ví dụ cách lấy bean trên **JDK 24+** (đoạn minh họa, yêu cầu môi trường đã có module `jdk.management`):

```java
import java.lang.management.ManagementFactory;
import jdk.management.VirtualThreadSchedulerMXBean;

VirtualThreadSchedulerMXBean scheduler =
        ManagementFactory.getPlatformMXBean(VirtualThreadSchedulerMXBean.class);

if (scheduler != null) {
    System.out.println("parallelism=" + scheduler.getParallelism());
    System.out.println("carrierPoolSize=" + scheduler.getPoolSize());
    System.out.println("queuedVirtualThreads~=" + scheduler.getQueuedVirtualThreadCount());
}
```

Đoạn trên là **snippet**, không phải một lớp Java hoàn chỉnh; `jdk.management.VirtualThreadSchedulerMXBean` chỉ có từ JDK 24. `getQueuedVirtualThreadCount()` phản ánh virtual threads chờ **scheduler**, không đồng nghĩa số tác vụ đang chờ connection pool, semaphore hay kết quả I/O. Không dùng nó làm tổng số virtual threads tồn tại trong process.

Ở tầng ứng dụng, nên có các phép đo rõ ràng:

- Số tác vụ virtual-thread được bắt đầu/hoàn thành/hủy, số tác vụ in-flight và thời gian chờ.
- Tỷ lệ lỗi, timeout của I/O bên ngoài, active/idle/waiting của connection pool.
- Queue/admission backlog và số yêu cầu bị từ chối khi quá tải.
- CPU, memory, GC và các chỉ số platform-thread/carrier riêng biệt.

Khi chẩn đoán vấn đề, dùng JFR hoặc các công cụ profile thích hợp cùng dump/metrics thay vì chỉ xem một số `getThreadCount()`.

## 9. Lỗi phổ biến và cách tự kiểm tra

**Không thấy virtual thread trong `ThreadMXBean` → kết luận thread đã chết.** Kiểm tra `Thread.isVirtual()` và `Thread.isAlive()` khi đã có tham chiếu đến thread; dùng `jcmd Thread.dump_to_file` để quan sát stack. `ThreadMXBean` bỏ sót virtual threads theo thiết kế.

**Dump không chứa tên `demo-vt-*`.** Kiểm tra tiến trình còn sống, PID có đúng, có lấy dump trước khi các thread kết thúc không, và đã chạy đúng chương trình Java 21+ chưa. Thử lại khi các virtual threads đang chờ `CountDownLatch`.

**Dùng `jcmd` sai môi trường/container.** Kiểm tra attach permission, namespace PID, file path từ góc nhìn JVM đích, quyền ghi và tài khoản chạy công cụ. Xem `jcmd <PID> help Thread.dump_to_file`. Nếu tiến trình không cho attach, chẩn đoán theo cơ chế quản lý JVM/container hiện hành.

**Cho rằng file dump có toàn bộ metadata truyền thống.** Định dạng dump tối ưu cho virtual threads không cung cấp mọi thông tin như object addresses, JNI hoặc heap; dữ liệu về khóa có khác biệt theo JDK. Kết hợp `Thread.print`/JFR/metrics khi cần các góc nhìn đó.

**Đếm số lần xuất hiện chữ “virtual” trong file làm số thread chính xác.** Một stack hoặc metadata có thể chứa nhiều lần từ khóa. Hãy parse thread entries theo định dạng thực tế hoặc đo số in-flight tại ứng dụng.

**Lấy một dump rồi kết luận deadlock/leak.** Snapshot đơn lẻ không cho biết xu hướng. So sánh nhiều lần lấy dump và dữ liệu pool/queue; xác định tài nguyên nào không được giải phóng và tác vụ nào không tiến triển.

**Bật tất cả JFR start/end events trong tải rất lớn mà không cân nhắc.** Vòng đời virtual thread có thể xảy ra với tần suất cao. Cấu hình sự kiện, thời gian ghi và dung lượng recording theo mục tiêu điều tra.

## 10. Bài tập kiểm chứng

1. Chạy `VirtualThreadDumpDemo.java`, kiểm tra hai dòng chắc chắn: `virtualThreadsStarted=12` và `sampleVirtualThreadListedByMXBean=false`.
2. Dùng PID chương trình in ra để chạy `jcmd <PID> help Thread.dump_to_file`, sau đó xuất một JSON dump.
3. Tìm tên `demo-vt-0`, stack đợi `CountDownLatch.await` và xác nhận đó là virtual thread trong dump.
4. Chờ ví dụ kết thúc, chạy lại một lần và lấy dump sau khi bạn thay `WORKERS` từ 12 thành 100. So sánh số virtual threads tìm được với `getThreadCount()`; không kỳ vọng platform-thread count tăng tuyến tính.
5. Giải thích vì sao `ThreadMXBean` vẫn hữu ích để theo dõi platform threads, và dùng công cụ nào để chẩn đoán virtual threads cùng các nút cổ chai I/O.

## Tóm tắt kiến thức

- `ThreadMXBean` chỉ quản lý **platform threads**; `getThreadCount()`, `getAllThreadIds()`, `dumpAllThreads()` không cho tổng số hay stack đầy đủ của virtual threads.
- `jcmd <PID> Thread.dump_to_file -format=json <absolute-file>` là cách chuẩn từ JDK 21 để tạo thread dump gồm cả **platform và virtual threads**.
- Dump là **snapshot**. Phân tích stack với thời điểm và kết hợp metric/JFR để kết luận về contention, I/O, deadlock hoặc quá tải.
- `jdk.management.VirtualThreadSchedulerMXBean` từ JDK 24 có phạm vi scheduler, **không phải** cách thay thế hoàn toàn `ThreadMXBean` và không đo mọi virtual threads đang sống.

## Tài liệu chính thức

1. [Java SE 21 — ThreadMXBean API](https://docs.oracle.com/en/java/javase/21/docs/api/java.management/java/lang/management/ThreadMXBean.html): giới hạn phạm vi platform threads, `getThreadInfo`, `getAllThreadIds` và deadlock detection.
2. [Java SE 21 — Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html): thread dump qua `jcmd`, carrier, debugging và JFR.
3. [Java SE 22 — jcmd command](https://docs.oracle.com/en/java/javase/22/docs/specs/man/jcmd.html): cách dùng diagnostic commands và tham số `Thread.dump_to_file`.
4. [Java SE 24 — VirtualThreadSchedulerMXBean API](https://docs.oracle.com/en/java/javase/24/docs/api/jdk.management/jdk/management/VirtualThreadSchedulerMXBean.html): những chỉ số scheduler bổ sung từ JDK 24.


</details>

- [Quay lại đầu trang](#back-to-top)
