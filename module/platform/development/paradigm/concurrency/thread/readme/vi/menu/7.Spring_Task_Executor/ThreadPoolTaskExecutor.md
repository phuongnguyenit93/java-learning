<a id="back-to-top"></a>

# ThreadPoolTaskExecutor và các vấn đề liên quan

## Menu
- [ThreadPoolTaskExecutor: "Người quản gia" thông minh của Spring](#thread-pool-task-executor)
- [So sánh Executors (Java Core) vs. ThreadPoolTaskExecutor (Spring)](#executor-compare)



## <a id="thread-pool-task-executor">ThreadPoolTaskExecutor: "Người quản gia" thông minh của Spring</a>
<details>
<summary>Click for details</summary>


Trong hệ sinh thái Spring, `ThreadPoolTaskExecutor` đóng vai trò là lớp bọc (**Wrapper**) cho `java.util.concurrent.ThreadPoolExecutor`. Nếu lớp thuần Java là "động cơ", thì Spring version chính là chiếc xe hoàn chỉnh với đầy đủ tiện nghi điều khiển.

## 1. Tại sao nên dùng ThreadPoolTaskExecutor?

Lớp này được thiết kế để "hiểu" và tương tác hoàn hảo với các quy chuẩn của Spring:
* **Lifecycle Awareness:** Tự động khởi tạo (`initialize`) và đóng lại (`shutdown`) đồng bộ với vòng đời của `ApplicationContext`.
* **Bean Configuration:** Dễ dàng cấu hình thông số qua `@Bean`, file `.yaml` hoặc `.properties`.
* **TaskDecorator:** Hỗ trợ cực tốt việc can thiệp vào luồng để truyền dẫn dữ liệu (như UserContext hoặc Trace ID).

## 2. Các thông số "Vàng" cần nắm vững

Khi cấu hình trong project `java-learning`, bạn cần làm chủ 4 thông số cốt lõi sau:

| Thông số | Ý nghĩa |
| :--- | :--- |
| **`CorePoolSize`** | Số lượng luồng "biên chế" tối thiểu luôn được duy trì. |
| **`MaxPoolSize`** | Số lượng luồng tối đa có thể mở rộng khi "kho chứa" đã đầy. |
| **`QueueCapacity`** | Dung lượng của `BlockingQueue` (nơi chứa task đang chờ). |
| **`ThreadNamePrefix`** | Tiền tố tên luồng (ví dụ: `JavaLearn-`), giúp debug qua log dễ dàng hơn. |

## 3. Quy trình xử lý Task (Kịch bản thực tế)

Giả sử cấu hình: **Core: 5, Queue: 100, Max: 10**. Quy trình sẽ diễn ra theo thứ tự:
1.  **Giai đoạn 1:** Task 1-5 đến $\rightarrow$ Tạo ngay 5 luồng để xử lý.
2.  **Giai đoạn 2:** Task 6-105 đến $\rightarrow$ Đẩy vào **Queue** xếp hàng (Core vẫn giữ nguyên 5).
3.  **Giai đoạn 3:** Task 106 đến (Queue đầy) $\rightarrow$ Bắt đầu mở rộng thêm luồng (từ luồng 6 đến 10).
4.  **Giai đoạn 4:** Task 111 đến $\rightarrow$ Hệ thống "vỡ trận", kích hoạt `RejectedExecutionHandler`.

> **Lưu ý:** Sai lầm phổ biến là nghĩ rằng quá Core sẽ tăng lên Max ngay. Thực tế, **Queue phải đầy trước** thì Max mới được huy động.

## 4. Cách khai báo trong Project

Dựa trên cấu trúc folder `paradigm/concurrency/thread/pool`, bạn có thể triển khai như sau:

```java
    @Configuration
    @EnableAsync
    public class AsyncConfig {

        @Bean(name = "completableFutureExecutor")
        public Executor completableFutureExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

            executor.setCorePoolSize(5);
            executor.setMaxPoolSize(10);
            executor.setQueueCapacity(100);
            executor.setThreadNamePrefix("JavaLearn-");

            // Tích hợp TaskDecorator nếu có
            // executor.setTaskDecorator(new MdcTaskDecorator());

            executor.initialize();
            return executor;
        }
    }
```

## 5. Khi nào nên sử dụng?

* Khi sử dụng annotation `@Async` của Spring.
* Làm `Executor` cho `CompletableFuture` trong các service xử lý logic.
* Khi cần quản lý tập trung và giám sát các Thread Pool trong toàn bộ hệ thống Spring Boot.


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="executor-compare">So sánh Executors (Java Core) vs. ThreadPoolTaskExecutor (Spring)</a>
<details>
<summary>Click for details</summary>


Việc hiểu sự khác biệt giữa "Cốt lõi" (Core Java) và "Khung làm việc" (Spring Framework) giúp bạn chọn đúng công cụ cho từng môi trường phát triển.

## 1. Nguồn gốc và Sự quản lý (Lifecycle)

* **Executors (Java Core):** Bạn phải tự tay khởi tạo (`new`) và tự tay đóng (`shutdown`). Nếu quên, ứng dụng có thể bị treo hoặc rò rỉ bộ nhớ khi stop server.
* **ThreadPoolTaskExecutor (Spring):** Là một **Spring Bean**. Nó tự động khởi tạo khi ứng dụng Start và tự động đóng an toàn khi ứng dụng Stop (Lifecycle-aware).

## 2. Khả năng cấu hình: "Cứng" vs. "Linh hoạt"

* **Executors:** Các phương thức như `newFixedThreadPool(10)` thiết lập thông số khá "cứng". Việc thay đổi số lượng luồng hay cấu hình lại hàng đợi (`Queue`) khi ứng dụng đang chạy là cực kỳ khó khăn.
* **ThreadPoolTaskExecutor:** Cho phép cấu hình linh hoạt qua file `application.yml` hoặc `@Value`. Bạn kiểm soát được toàn bộ: `corePoolSize`, `maxPoolSize` và đặc biệt là `queueCapacity`.

## 3. Quy trình xử lý Task (Khác biệt lớn nhất)

| Đặc điểm | Executors (Fixed/Single) | ThreadPoolTaskExecutor (Spring) |
| :--- | :--- | :--- |
| **Hàng đợi (Queue)** | Thường là `LinkedBlockingQueue` không giới hạn dung lượng. | Cho phép đặt giới hạn rõ ràng qua `queueCapacity`. |
| **Logic mở rộng** | **Fixed:** Luôn giữ cố định `n` luồng. | **Spring:** Co giãn từ Core lên Max khi Queue đầy. |
| **Tính an toàn** | Dễ gây lỗi `OutOfMemory` do Queue phình to vô hạn. | An toàn hơn vì kiểm soát được độ dài hàng đợi và RAM. |

## 4. Các tính năng độc quyền của Spring

`ThreadPoolTaskExecutor` cung cấp những công cụ mà Java thuần không có sẵn:
1. **`setTaskDecorator`:** "Chìa khóa" để truyền dữ liệu như Trace ID (MDC) hay Security Context xuyên suốt các luồng.
2. **`setThreadNamePrefix`:** Đặt tên luồng trực tiếp (vd: `Order-Pool-`) giúp dễ dàng giám sát qua log.
3. **Tích hợp `@Async`:** Spring tối ưu hóa hoàn toàn việc sử dụng các Bean kiểu `TaskExecutor` cho các hàm bất đồng bộ.

## 5. Khi nào dùng cái nào?

* **Dùng Executors (Java Core) khi:** Viết ứng dụng Java thuần, làm các bài Lab nhỏ để hiểu bản chất luồng (thư mục `paradigm/concurrency/thread/core`).
* **Dùng ThreadPoolTaskExecutor khi:** Phát triển dự án Spring Boot thực tế (`java-learning`). Nó giúp kiểm soát "sức khỏe" hệ thống và tích hợp mượt mà với hệ sinh thái Spring.

</details>

- [Quay lại đầu trang](#back-to-top)