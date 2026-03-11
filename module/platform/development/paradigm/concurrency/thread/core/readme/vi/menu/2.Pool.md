<a id="back-to-top"></a>

# Thread Pool và các yếu tố liên quan

## Menu
- [1. Tìm hiểu về Thread Pool và Cấu hình TaskExecutor](#thread-pool-basic)
- [2. Tìm hiểu về RejectedExecutionHandler](#rejected-execution-handler)
- [3. RejectedExecutionHandler: Các ví dụ cho các Policy"](#reject-execution-handler-example)

---
## <a id="thread-pool-basic">1. Tìm hiểu về Thread Pool và Cấu hình TaskExecutor</a>
<details>
<summary>Click for details</summary>


Thread Pool là một tập hợp các luồng (threads) được tạo sẵn và quản lý tập trung. Thay vì mỗi lần có việc lại tạo một luồng mới (`new Thread()`), hệ thống sẽ tái sử dụng các luồng có sẵn để tiết kiệm chi phí CPU và RAM.

---

## 1. Tại sao phải dùng Thread Pool?

* **Giảm độ trễ:** Task có thể thực thi ngay lập tức bằng luồng sẵn có, không mất thời gian khởi tạo.
* **Kiểm soát tài nguyên:** Ngăn chặn việc tạo hàng ngàn luồng dẫn đến lỗi `OutOfMemory`.
* **Quản lý tập trung:** Dễ dàng theo dõi trạng thái, giới hạn tài nguyên và debug lỗi.

---

## 2. Giải mã các thông số cấu hình (Parameters)

Dựa trên cấu hình `defaultTaskExecutor`, đây là ý nghĩa thực tế của từng thông số:

| Thông số | Giá trị tiêu biểu | Ý nghĩa thực tế |
| :--- | :--- | :--- |
| **CorePoolSize** | `cores` (số CPU) | Số lượng luồng "nòng cốt" luôn được duy trì, kể cả khi rảnh rỗi. |
| **QueueCapacity** | `500` | "Hàng đợi". Khi các luồng Core bận, task mới sẽ được đẩy vào đây chờ. |
| **MaxPoolSize** | `cores * 2` | Giới hạn tối đa luồng được tạo ra khi hàng đợi đã đầy. |
| **KeepAliveSeconds** | `60` | Thời gian tồn tại của các luồng "vượt định mức" (luồng ngoài core) khi rảnh rỗi. |
| **ThreadNamePrefix** | `"default-"` | Tiền tố tên luồng, giúp phân biệt log giữa các Pool (VD: `default-1`). |
| **CorePoolSize** | `cores` (số CPU) | Số lượng luồng "nòng cốt" luôn được duy trì, kể cả khi rảnh rỗi. |
| **TaskDecorator** | `CustomDecorator` | Cho phép "trang trí" (can thiệp) vào Task trước khi chạy. Thường dùng để **truyền dữ liệu từ Thread cha sang Thread con** (như `ThreadLocal`, `SecurityContext`). |
| **RejectedExecutionHandler** | `Abort`, `CallerRuns`,... | Chính sách xử lý khi **hàng đợi và số luồng đều đã đầy**. Quyết định xem sẽ ném lỗi, bắt luồng chính tự chạy, hay âm thầm bỏ qua Task. |
| **WaitForTasksToCompleteOnShutdown** | `true` / `false` | Nếu là `true`, Spring sẽ **đợi các Task đang chạy hoàn thành xong** rồi mới tắt ứng dụng (Shutdown), giúp tránh mất mát dữ liệu dở dang. |
| **AwaitTerminationSeconds** | `30` - `60` (giây) | Thời gian **tối đa** mà hệ thống sẽ đợi các Task hoàn thành khi Shutdown. Nếu quá thời gian này, hệ thống sẽ cưỡng ép tắt luồng. |
---

## 3. Cơ chế vận hành (Cực kỳ quan trọng)

Nhiều người lầm tưởng Pool sẽ tăng lên `MaxPoolSize` ngay khi `CorePoolSize` bận. Thực tế, Spring/Java vận hành theo thứ tự nghiêm ngặt sau:

1.  **Giai đoạn 1:** Nếu số luồng < `CorePoolSize` -> Tạo luồng mới.
2.  **Giai đoạn 2:** Nếu số luồng = `CorePoolSize` -> Đẩy task vào **QueueCapacity**.
3.  **Giai đoạn 3:** Nếu **Queue đầy** -> Tạo thêm luồng mới cho đến khi đạt `MaxPoolSize`.
4.  **Giai đoạn 4:** Nếu vượt quá `MaxPoolSize` -> Kích hoạt chính sách từ chối (**RejectedExecutionHandler**).

---

## 4. Các thiết lập nâng cao (Advanced)

* **`setTaskDecorator`**: "Người vận chuyển" dữ liệu (MDC, SecurityContext) từ luồng cha sang luồng con.
* **`availableProcessors()`**: Giúp Pool tự thích nghi với phần cứng (Server 16 cores sẽ tự mạnh hơn Laptop 4 cores).
* **Graceful Shutdown**:
    * `setWaitForTasksToCompleteOnShutdown(true)`: Không "giết" luồng đột ngột khi tắt Server, đợi task hoàn thành.
    * `setAwaitTerminationSeconds(60)`: Thời gian tối đa chờ đợi trước khi cưỡng chế đóng ứng dụng.

---

## 5. Tổ chức dự án chuyên nghiệp

Nên kết hợp Bean cấu hình với `application.yml` để thay đổi thông số theo môi trường (Dev/Prod) mà không cần sửa code.

**Trong `application.yml`:**
```yaml
app:
  thread-pool:
    core-size: 8
    max-size: 16
    queue-capacity: 1000
```

**Trong code Java:**
Sử dụng `@Value` hoặc `@ConfigurationProperties` để nạp các thông số này vào `defaultTaskExecutor`.

</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="rejected-execution-handler">2. Tìm hiểu về RejectedExecutionHandler</a>
<details>
<summary>Click for details</summary>


Khi một **Thread Pool** rơi vào tình trạng quá tải (Hàng đợi đã đầy và số luồng đã đạt tới `MaxPoolSize`), mọi tác vụ mới gửi đến sẽ bị từ chối. Lúc này, `RejectedExecutionHandler` sẽ được kích hoạt để xử lý các tác vụ "bơ vơ" này.

## 1. Khi nào một Task bị từ chối?

Có hai tình huống chính khiến Handler này hoạt động:
1.  **Hệ thống quá tải:** Khi Thread Pool đã dùng hết `MaxPoolSize` và hàng đợi (`QueueCapacity`) đã không còn chỗ trống.
2.  **Hệ thống đang tắt:** Khi `Executor` đã bắt đầu quá trình shutdown, nó sẽ không chấp nhận thêm bất kỳ tác vụ mới nào.

---

## 2. 4 Chiến lược mặc định trong Java

Java cung cấp sẵn 4 chiến lược phổ biến. Bạn có thể chọn chiến lược phù hợp tùy theo độ quan trọng của tác vụ:

| Chiến lược | Hành động | Khi nào nên dùng? |
| :--- | :--- | :--- |
| **AbortPolicy** | Ném ra ngoại lệ `RejectedExecutionException`. | **Mặc định.** Dùng khi bạn muốn hệ thống dừng lại và báo lỗi ngay lập tức. |
| **CallerRunsPolicy** | Luồng gọi (thường là luồng chính/Controller) sẽ tự chạy tác vụ đó. | Dùng khi **không được phép mất dữ liệu** và muốn làm chậm tốc độ gửi request (Backpressure). |
| **DiscardPolicy** | Lặng lẽ bỏ qua tác vụ, không báo lỗi, không làm gì cả. | Dùng cho các tác vụ **không quan trọng** (vd: ghi log phụ, tracking). |
| **DiscardOldestPolicy** | Xóa tác vụ cũ nhất trong hàng đợi để nhường chỗ cho tác vụ mới. | Dùng cho các hệ thống ưu tiên **dữ liệu mới nhất** (vd: giá chứng khoán, tỉ số bóng đá). |

---

## 3. Cách triển khai Custom Handler (Chuyên nghiệp)

Trong các dự án lớn, bạn thường muốn **ghi log**, **đẩy metric** lên Prometheus/Grafana hoặc **gửi cảnh báo** (Slack/Telegram) khi Pool bị đầy.

```groovy
    public class CustomRejectedHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            // 1. Ghi log cảnh báo
            log.error("Hệ thống quá tải! Task {} bị từ chối. Pool size: {}, Queue: {}", 
                      r.toString(), executor.getPoolSize(), executor.getQueue().size());

            // 2. Gửi cảnh báo về Telegram/Slack (tùy chọn)
            alertService.send("Thread Pool Overload Alert!");

            // 3. Có thể ném lỗi hoặc thực hiện chiến lược dự phòng
            throw new RejectedExecutionException("Task rejected due to resource limits");
        }
    }
```

---

## 4. Cấu hình vào TaskExecutor trong Spring Boot

Bạn chỉ cần nạp Handler này vào Bean cấu hình của mình:

```groovy
    @Bean(name = "defaultTaskExecutor")
    public Executor defaultTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // ... các cấu hình core, max, queue ...

        // Thiết lập chiến lược: Ví dụ chọn CallerRunsPolicy
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }
```

---

## 💡 Lời khuyên thực tế

* **CallerRunsPolicy là lựa chọn "an toàn" nhất:** Nó giúp ngăn hệ thống bị sập do quá tải request bằng cách bắt luồng chính phải xử lý tác vụ, từ đó gián tiếp làm chậm tốc độ nhận request mới.
* **Luôn theo dõi (Monitor):** Nếu `RejectedExecutionHandler` bị kích hoạt thường xuyên, đó là dấu hiệu bạn cần tăng cấu hình phần cứng hoặc tối ưu lại code xử lý tác vụ.

</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="reject-execution-handler-example">3. RejectedExecutionHandler: Các ví dụ cho các Policy"</a>
<details>
<summary>Click for details</summary>


Việc cấu hình **RejectedExecutionHandler** là bước cuối cùng để bảo vệ hệ thống không bị "vỡ trận" khi cả Thread Pool và Queue đều đã đầy. Với chip i7, việc xử lý hàng nghìn Task mỗi giây là bình thường, nhưng khi Task quá nặng, 4 chính sách này sẽ giúp hệ thống của bạn ứng phó một cách chủ động.

### 1. AbortPolicy (Mặc định - "Dừng hình")
Đây là chính sách nghiêm ngặt nhất. Nếu Pool đầy, nó ném ra `RejectedExecutionException`. Bạn phải dùng `try-catch` để xử lý.

* **Ví dụ:** Hệ thống thanh toán. Thà báo lỗi để User biết và thử lại sau, còn hơn là lẳng lặng bỏ qua khiến giao dịch bị treo.

**Java**
```java
  ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, 
                                      new LinkedBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy());
  try {
      executor.execute(() -> System.out.println("Thanh toán đơn hàng..."));
  } catch (RejectedExecutionException e) {
      System.err.println("Hệ thống quá tải! Vui lòng thử lại sau 30s.");
  }
```

---

### 2. CallerRunsPolicy (Chiến thuật "Phanh gấp" - Backpressure)
Thay vì ném lỗi, luồng gọi (ví dụ: Main Thread) sẽ tự mình chạy cái Task bị từ chối đó.

* **Ví dụ:** Import dữ liệu từ Excel 1 triệu dòng. Khi Pool đầy, luồng Main (đang đọc file) phải dừng việc đọc để tự xử lý dòng dữ liệu đó. Điều này tạo ra **Backpressure**, giúp Pool có thời gian để "thở".

**Java**
```java
    ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
    new LinkedBlockingQueue<>(1), new ThreadPoolExecutor.CallerRunsPolicy());

        executor.execute(() -> {
            // Luồng chính sẽ bị kẹt ở đây để tự chạy Task này nếu Pool đầy
            System.out.println(Thread.currentThread().getName() + " đang tự xử lý Task...");
        });
```

---

### 3. DiscardPolicy ("Bỏ rơi lặng lẽ")
Task bị từ chối sẽ bị ném vào "thùng rác" mà không có bất kỳ thông báo hay lỗi nào.

* **Ví dụ:** Hệ thống Tracking hành vi người dùng (User Click). Nếu quá tải, việc mất vài bản ghi log không ảnh hưởng đến hệ thống chính. Thà bỏ qua để giữ hiệu năng cho chức năng mua hàng.

**Java**
```java
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
    new LinkedBlockingQueue<>(1), new ThreadPoolExecutor.DiscardPolicy());

        // Task này sẽ biến mất vĩnh viễn nếu hàng đợi đầy
        executor.execute(() -> System.out.println("Ghi log tracking..."));
```

---

### 4. DiscardOldestPolicy ("Ưu tiên người mới")
Nó sẽ bốc cái Task nằm lâu nhất trong hàng đợi (Oldest) vứt đi để lấy chỗ trống cho Task mới nhất vừa đến.

* **Ví dụ:** Cập nhật giá vàng hoặc chứng khoán. Giá của 5 giây trước đã lỗi thời, giá vừa nhảy vào mới là quan trọng nhất.

**Java**
```java
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
    new LinkedBlockingQueue<>(1), new ThreadPoolExecutor.DiscardOldestPolicy());

        // Nếu Task mới đến, Task đang đợi lâu nhất trong Queue sẽ bị "hy sinh"
        executor.execute(() -> System.out.println("Cập nhật giá vàng mới nhất..."));
```

---

### 5. Tự viết Handler (Custom Policy)
Nếu 4 chính sách trên không đủ, bạn có thể tự viết để ghi Log chuyên sâu hoặc đẩy Task vào một hệ thống dự phòng như Kafka/RabbitMQ.

**Java**
```java
    executor.setRejectedExecutionHandler((runnable, exec) -> {
        System.err.println("Task bị từ chối! Đang đẩy vào Kafka để xử lý sau...");
        // Logic đẩy vào Message Queue ở đây
    });
```
---

### Tổng kết lựa chọn

| Chính sách | Đặc điểm chính | Khi nào nên dùng? |
| :--- | :--- | :--- |
| **AbortPolicy** | Ném Exception | Cần kiểm soát chặt chẽ, không được phép mất Task mà không biết. |
| **CallerRunsPolicy** | Luồng gọi tự chạy | Cần an toàn tuyệt đối, chấp nhận làm chậm luồng chính (Backpressure). |
| **DiscardPolicy** | Lặng lẽ xóa bỏ | Chấp nhận mất mát dữ liệu để đổi lấy tốc độ (Logging, Tracking). |
| **DiscardOldestPolicy** | Xóa Task cũ nhất | Cần dữ liệu tươi mới nhất (Giá thị trường, Real-time data). |

</details>

- [Quay lại đầu trang](#back-to-top)