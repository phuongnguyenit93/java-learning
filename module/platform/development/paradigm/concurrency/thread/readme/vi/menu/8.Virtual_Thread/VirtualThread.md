<a id="back-to-top"></a>

# Virtual Thread (Project Loom) - Cách mạng hóa Concurrency

## Menu
- [Định nghĩa cơ bản Virtual Thread](#virtual-thread-basic)
- [Bài Test "Hủy Diệt": Platform Threads vs. Virtual Threads](#virtual-thread-and-platform-thread)
- [Phân tích chuyên sâu: Virtual Thread = Work-stealing + Yielding](#virtual-thread-advance)
- [Hướng dẫn thực tế: Cách sử dụng Virtual Thread trong Java 21](#virtual-thread-how-to-use)
- [ Giải mã: Tại sao try(ExecutorService) Java 21 chạy được mà Java 17 lại "tịt"?](#try-executor-service)



## <a id="virtual-thread-basic">Định nghĩa cơ bản Virtual Thread</a>
<details>
<summary>Click for details</summary>


Virtual Thread chính thức xuất hiện trong Java 21 và nó thay đổi hoàn toàn cuộc chơi. Để hiểu tại sao nó "tiên tiến", hãy so sánh nó với những gì bạn vừa học về **Work-Stealing** và **Platform Thread**.

## 1. Vấn đề của "Platform Thread" (Truyền thống)
Trước Java 21, mỗi Thread bạn tạo ra là một **Platform Thread** (luồng hệ điều hành).
* **Rất đắt:** Mỗi luồng tốn khoảng 1MB bộ nhớ cho stack.
* **Số lượng hạn chế:** Bạn không thể tạo 1 triệu Platform Thread, máy sẽ sập ngay vì cạn kiệt RAM và CPU chết chìm trong việc chuyển đổi ngữ cảnh (**context switching**).
* **Lãng phí:** Khi luồng gọi Database hoặc API (I/O), nó sẽ đứng đợi (**Blocked**). Luồng OS vẫn nằm đó, chiếm tài nguyên nhưng không làm gì cả.

## 2. Virtual Thread là gì?
Hãy tưởng tượng Platform Thread là những chiếc xe tải khổng lồ, còn Virtual Thread là hàng vạn kiện hàng nhỏ trên xe tải đó.
* **Siêu nhẹ:** Mỗi Virtual Thread chỉ tốn vài trăm bytes thay vì MB.
* **Số lượng khổng lồ:** Bạn có thể tạo hàng triệu luồng trên một chiếc laptop bình thường.
* **Không bị chặn (Non-blocking):** Khi một Virtual Thread gặp lệnh đợi I/O, nó sẽ tự động "nhường ghế" (**yield**) cho Virtual Thread khác chạy trên Platform Thread đó. Khi dữ liệu trả về, nó sẽ quay lại làm tiếp.

## 3. Mối quan hệ mật thiết với "Work-Stealing"
Thật tình cờ, Virtual Threads thực chất chạy trên nền của **ForkJoinPool**!
* Java sử dụng một `ForkJoinPool` ngầm định để làm **Scheduler**.
* Các Platform Thread đóng vai trò là **Carrier Threads** (luồng vận chuyển).
* Các Virtual Thread sẽ được cơ chế **Work-Stealing** điều phối để chạy trên các Carrier Threads này.

## 4. Code mẫu: Thử thách 1 triệu luồng
Thay vì chia nhỏ mảng phức tạp, với Virtual Thread, bạn chỉ cần giao mỗi tác vụ cho một luồng riêng biệt.

```java
	import java.util.concurrent.Executors;
	import java.util.stream.IntStream;

	public class VirtualThreadDemo {
	    public static void main(String[] args) {
	        long start = System.currentTimeMillis();

	        // Tạo một Executor sử dụng Virtual Threads
	        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
	            IntStream.range(0, 1_000_000).forEach(i -> {
	                executor.submit(() -> {
	                    // Giả lập một tác vụ xử lý đợi I/O
	                    Thread.sleep(100); 
	                    return i;
	                });
	            });
	        } // Executor sẽ tự đóng và đợi tất cả luồng ảo xong

	        long end = System.currentTimeMillis();
	        System.out.println("Xử lý xong 1 triệu luồng trong: " + (end - start) + "ms");
	    }
	}
```

## 5. So sánh: Virtual Thread vs. Work-Stealing thủ công

| Tiêu chí | Work-Stealing (Fork/Join) | Virtual Threads |
| :--- | :--- | :--- |
| **Mục đích** | **CPU-bound** (Tính toán nặng). | **I/O-bound** (Gọi DB, API, Web). |
| **Cách làm** | Bạn phải tự chia nhỏ task (Recursive). | Cứ mỗi task là một luồng, Java tự lo. |
| **Hiệu quả** | Nghiền nát các con số. | Quản lý hàng triệu kết nối đồng thời. |

---

### 💡 Lời khuyên cho dự án java-learning:
Từ Java 21 trở đi, hãy ưu tiên sử dụng **Virtual Threads** cho các ứng dụng Web/Microservices (nơi phải đợi I/O nhiều). Chỉ quay lại dùng **Fork/Join** thủ công khi bạn cần tối ưu hóa các thuật toán tính toán đệ quy phức tạp mà không có sự chờ đợi của mạng hay ổ đĩa.


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="virtual-thread-and-platform-thread">Bài Test "Hủy Diệt": Platform Threads vs. Virtual Threads</a>
<details>
<summary>Click for details</summary>


Chúng ta sẽ thực hiện một bài test để thấy tại sao Virtual Thread lại là bước tiến kinh khủng của Java.

**Kịch bản:** Tạo ra **100.000 nhiệm vụ**. Mỗi nhiệm vụ chỉ đơn giản là ngủ (`sleep`) 1 giây — giả lập việc đợi Database hoặc gọi API (I/O Bound).

## 1. Cuộc đối đầu: Mã nguồn so sánh

Dưới đây là đoạn mã để bạn chạy trong module `virtual-thread`:

```java
	import java.util.concurrent.Executors;
	import java.time.Duration;

	public class ThreadChallenge {
	    public static void main(String[] args) {
	        int TASK_COUNT = 100_000;

	        // --- TEST 1: Platform Threads (Luồng truyền thống) ---
	        System.out.println("Bắt đầu với Platform Threads (Pool 1000)...");
	        long start1 = System.currentTimeMillis();
	        try (var executor = Executors.newFixedThreadPool(1000)) { 
	            for (int i = 0; i < TASK_COUNT; i++) {
	                executor.submit(() -> {
	                    Thread.sleep(Duration.ofSeconds(1));
	                    return null;
	                });
	            }
	        }
	        System.out.println("Platform Threads hoàn thành: " + (System.currentTimeMillis() - start1) + "ms");

	        // --- TEST 2: Virtual Threads (Java 21+) ---
	        System.out.println("\nBắt đầu với Virtual Threads...");
	        long start2 = System.currentTimeMillis();
	        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
	            for (int i = 0; i < TASK_COUNT; i++) {
	                executor.submit(() -> {
	                    Thread.sleep(Duration.ofSeconds(1));
	                    return null;
	                });
	            }
	        }
	        System.out.println("Virtual Threads hoàn thành: " + (System.currentTimeMillis() - start2) + "ms");
	    }
	}
```

## 2. Phân tích "Kẻ thắng - Người thua"

| Đặc điểm | Platform Threads (Fixed Pool 1000) | Virtual Threads |
| :--- | :--- | :--- |
| **Cơ chế** | Xử lý tuần tự theo đợt (100 đợt x 1000 luồng). | Xử lý song song gần như toàn bộ 100.000 luồng. |
| **Thời gian** | ~100 giây (Rất chậm). | ~1.2 giây (Cực nhanh). |
| **Tài nguyên** | Tốn RAM để duy trì 1000 luồng OS. | Tốn rất ít RAM (mỗi luồng ảo chỉ vài trăm byte). |
| **Giới hạn** | Dễ bị lỗi `OutOfMemory` nếu không dùng Pool. | Có thể tạo hàng triệu luồng trên máy cá nhân. |

## 3. Tại sao đây là thứ "Tiên tiến nhất"?

* **Lập trình đồng bộ, hiệu suất bất đồng bộ:** Trước đây để xử lý hàng vạn kết nối, ta phải dùng lập trình phản ứng (**Reactive - WebFlux**) rất khó đọc. Virtual Thread cho phép viết code tuần tự đơn giản mà hiệu suất vẫn tương đương.
* **Tương thích ngược:** Các thư viện cũ như Hibernate, JDBC hay Spring đều có thể chạy trên Virtual Thread mà không cần sửa code logic.
* **Tận dụng tối đa tài nguyên:** Với Platform Thread, máy chủ thường hết RAM trước khi hết CPU. Với Virtual Thread, bạn sẽ tận dụng được tối đa sức mạnh tính toán của CPU.

---

### 💡 Ghi chú cho dự án java-learning:
Khi bạn thực hiện các bài toán về Web Crawler hoặc gửi thông báo hàng loạt (Notification Service), **Virtual Thread** sẽ giúp bạn tiết kiệm hàng ngàn USD chi phí server so với cách dùng Thread truyền thống.


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="virtual-thread-advance">Phân tích chuyên sâu: Virtual Thread = Work-stealing + Yielding</a>
<details>
<summary>Click for details</summary>


Cách hiểu về việc Virtual Thread là sự kết hợp giữa **Work-stealing** và cơ chế **Nhường thread (Yielding)** là cực kỳ chuẩn xác. Có thể coi Virtual Thread là một "bản nâng cấp" thông minh dựa trên nền tảng mà bạn đã học.

## 1. Work-Stealing: Động cơ điều phối (The Scheduler)
Virtual Thread không tự chạy được, nó cần một luồng thật (Platform Thread) để "cưỡi" lên. Java sử dụng một `ForkJoinPool` đặc biệt (gọi là **Carrier Pool**) để điều phối.

* **Carrier Threads:** Các Platform Thread đóng vai trò là những "người vận chuyển".
* **Cơ chế:** Thuật toán Work-stealing đảm bảo rằng không có Platform Thread nào đứng chơi khi vẫn còn Virtual Thread đang chờ. Nếu Platform Thread A hết việc, nó sẽ sang "trộm" Virtual Thread từ hàng đợi của Platform Thread B.

## 2. Cơ chế Nhường (Yielding): Phép màu khi gặp I/O
Đây là điểm mà Virtual Thread vượt xa Fork/Join truyền thống nhờ khả năng giải phóng tài nguyên khi gặp vật cản.

* **Mount (Leo lên):** Virtual Thread gắn vào một Platform Thread để bắt đầu thực thi.
* **Blocking Call (Gặp vật cản):** Khi gặp lệnh I/O (ví dụ: `socket.read()` hoặc `db.query()`), nó sẽ thực hiện lệnh **Yield** (Nhường).
* **Unmount (Leo xuống):** Trạng thái (stack) của Virtual Thread được lưu vào bộ nhớ RAM (Heap), và nó giải phóng Platform Thread ngay lập tức.
* **Chạy tiếp:** Platform Thread rảnh tay sẽ đi lấy Virtual Thread khác về chạy.
* **Remount:** Khi I/O hoàn tất, Virtual Thread cũ được đưa lại vào hàng đợi để chờ một Platform Thread khác chở đi tiếp.

## 3. Sự khác biệt về mục đích sử dụng

| Đặc điểm | Work-Stealing thủ công (Fork/Join) | Virtual Threads |
| :--- | :--- | :--- |
| **Bản chất** | Chia nhỏ bài toán lớn (**Data Parallelism**). | Quản lý hàng triệu luồng (**Task Parallelism**). |
| **Ưu tiên cho** | **CPU-bound** (Tính toán, xử lý ảnh). | **I/O-bound** (Gọi API, Database). |
| **Cách dùng** | Chia đệ quy `fork()` và `join()`. | Cứ mỗi request tạo 1 luồng, viết tuần tự. |
| **Khi gặp Block** | Platform Thread bị nghẽn (Tệ). | Platform Thread được giải phóng (Tuyệt vời). |

---

### 💡 Kết luận cho dự án java-learning:

Bạn có thể coi Virtual Thread là một "người quản lý cao cấp" sử dụng Work-stealing làm trợ lý để điều phối công việc:

1.  **Nếu xử lý 1 triệu record trong bộ nhớ** (tính thuế, xử lý logic phức tạp...): Hãy dùng **Work-stealing (Fork/Join)** để "nghiền nát" dữ liệu bằng CPU.
2.  **Nếu xử lý 1 triệu record mà mỗi record phải gọi API bên ngoài:** Hãy dùng **Virtual Thread** để hệ thống không bị "chết chìm" trong việc chờ đợi I/O.

```java
    // Ví dụ tư duy chọn công cụ:
    if (isCpuIntensive) {
        // Dùng ForkJoinPool hoặc Parallel Stream
        return computeParallel(data); 
    } else {
        // Dùng Virtual Thread Executor
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return data.stream().map(d -> executor.submit(() -> callApi(d))).toList();
        }
    }
```


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="virtual-thread-how-to-use">Hướng dẫn thực tế: Cách sử dụng Virtual Thread trong Java 21</a>
<details>
<summary>Click for details</summary>


Mục tiêu của các kỹ sư thiết kế Java là giúp bạn "viết code đa luồng dễ như code đơn luồng". Dưới đây là các cách phổ biến để áp dụng vào dự án từ cơ bản đến chuyên nghiệp.

## 1. Cách đơn giản nhất: Thread.ofVirtual()
Bạn có thể tạo và chạy một luồng ảo ngay lập tức mà không cần quản lý phức tạp.

```java
	// Cách 1: Tạo và chạy ngay lập tức
	Thread.startVirtualThread(() -> {
	    System.out.println("Đang chạy trong luồng ảo: " + Thread.currentThread());
	});

	// Cách 2: Sử dụng Builder để cấu hình (tên luồng, số thứ tự...)
	Thread vThread = Thread.ofVirtual()
	        .name("batch-worker-", 1)
	        .start(() -> {
	            System.out.println("Nhiệm vụ đang xử lý...");
	        });
```

## 2. Cách chuyên nghiệp: newVirtualThreadPerTaskExecutor()
Đây là cách khuyên dùng cho module `virtual-thread`. Thay vì dùng Pool cố định, Java sẽ tạo một luồng ảo mới cho mỗi nhiệm vụ.

```java
	try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
	    IntStream.range(0, 10_000).forEach(i -> {
	        executor.submit(() -> {
	            // Giả lập xử lý I/O (gọi API, đọc file)
	            Thread.sleep(Duration.ofSeconds(1));
	            System.out.println("Xong task " + i);
	            return i;
	        });
	    });
	} // Tự động đóng executor và đợi các luồng ảo hoàn thành (AutoCloseable)
```

## 3. Kiểm soát tài nguyên: Sử dụng Semaphore
Việc thả 1 triệu Virtual Thread gọi Database cùng lúc là thảm họa. Bạn nên dùng **Semaphore** như một cái "barie" để giới hạn số lượng luồng ảo thực thi đồng thời.

```java
	public class SafeDatabaseBatch {
	    // Chỉ cho phép tối đa 50 luồng ảo truy cập Database cùng lúc
	    private static final Semaphore SEMAPHORE = new Semaphore(50);

	    public static void main(String[] args) {
	        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
	            for (int i = 0; i < 1_000_000; i++) {
	                executor.submit(() -> {
	                    try {
	                        SEMAPHORE.acquire(); // Xin phép đi qua barie
	                        // Logic gọi SQL ở đây
	                        System.out.println("Đang gọi DB...");
	                    } catch (InterruptedException e) {
	                        Thread.currentThread().interrupt();
	                    } finally {
	                        SEMAPHORE.release(); // Trả lại phép cho người khác
	                    }
	                });
	            }
	        }
	    }
	}
```

## 4. Tích hợp Spring Boot (Dự án java-learning)
Từ Spring Boot 3.2+, bạn chỉ cần một dòng cấu hình trong `application.properties`:

```properties
	spring.threads.virtual.enabled=true
```
Khi bật cấu hình này, tất cả các request HTTP và các phương thức `@Async` sẽ tự động chạy trên Virtual Threads.

---

## 💡 Lưu ý "sống còn" khi dùng Virtual Thread

* **Đừng dùng Pool cho Virtual Threads:** Virtual Thread cực rẻ, đừng bao giờ dùng `FixedThreadPool` để giới hạn chúng. Hãy dùng **Semaphore** để giới hạn tài nguyên đích (như DB Connection).
* **Tránh "Pinning":** Nếu dùng từ khóa `synchronized` bao quanh một đoạn code có I/O nặng, Virtual Thread sẽ bị "ghim" (pinned) vào Platform Thread và không thể leo xuống được.
  > **Giải pháp:** Thay thế bằng `ReentrantLock`.
* **ThreadLocal:** Cẩn thận khi dùng `ThreadLocal` với hàng triệu luồng ảo, nó có thể gây ngốn RAM (Heap) nếu mỗi luồng giữ một đối tượng lớn.


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="try-executor-service"> Giải mã: Tại sao try(ExecutorService) Java 21 chạy được mà Java 17 lại "tịt"?</a>
<details>
<summary>Click for details</summary>


Lý do nằm ở một thay đổi cực kỳ quan trọng về Interface: **ExecutorService chính thức kế thừa AutoCloseable** từ Java 19 trở đi. Đây là chi tiết nhỏ nhưng thay đổi hoàn toàn cách chúng ta quản lý Thread Pool.

## 1. Tại sao Java 17 không chạy được?

Trong Java 17, `ExecutorService` **không** kế thừa `AutoCloseable`. Cú pháp *try-with-resources* (`try (var x = ...)`) yêu cầu đối tượng đó phải thực thi Interface `AutoCloseable` hoặc `Closeable`.

**Ở Java 17:** Bạn buộc phải đóng pool thủ công trong khối `finally`:

```java
	ExecutorService executor = Executors.newFixedThreadPool(1000);
	try {
	    executor.submit(() -> { /* logic */ });
	} finally {
	    executor.shutdown(); // Bắt buộc phải đóng thủ công
	}
```

## 2. Tại sao Java 21 lại chạy được?

Từ Java 19 (và ổn định ở Java 21), các kỹ sư Java đã nâng cấp `ExecutorService` để nó hỗ trợ `AutoCloseable`.

* **Cơ chế hoạt động:** Khi khối `try` kết thúc, Java sẽ tự động gọi phương thức `.close()` của Executor.
* **Điều đặc biệt:** Phương thức `.close()` này bên dưới sẽ tự động gọi `.shutdown()` và sau đó là `.awaitTermination(...)`. Nó đợi cho đến khi tất cả các task trong pool hoàn thành rồi mới đóng hẳn.

## 3. Sự khác biệt về tư duy lập trình

Sự thay đổi này đi đôi với sự ra đời của **Virtual Threads (Project Loom)**.

| Đặc điểm | Java 17 (Old school) | Java 21 (Modern) |
| :--- | :--- | :--- |
| **Vòng đời Pool** | Tài nguyên "nặng" và sống lâu (Long-lived). | Công cụ "dùng xong rồi bỏ" (Short-lived). |
| **Cách quản lý** | Tạo một lần khi khởi động App và dùng mãi. | Tạo ngay trong hàm xử lý, dùng xong tự hủy qua `try`. |
| **Độ sạch của code** | Dễ rò rỉ (leak) luồng nếu quên `shutdown()`. | Code cực sạch, an toàn tuyệt đối nhờ `try-with-resources`. |

---

### 💡 Bài học cho dự án java-learning:

Việc hỗ trợ `AutoCloseable` giúp chúng ta sử dụng `newVirtualThreadPerTaskExecutor()` một cách cực kỳ linh hoạt. Bạn không còn phải lo lắng về việc tạo ra hàng triệu luồng ảo mà quên không đóng chúng lại, vì Java sẽ tự dọn dẹp giúp bạn ngay khi khối lệnh kết thúc.

```java
	// Style code chuẩn Java 21+
	try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
	    executor.submit(task);
	} // Tự động shutdown và đợi kết quả tại đây
```

</details>

- [Quay lại đầu trang](#back-to-top)