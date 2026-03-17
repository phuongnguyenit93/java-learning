## <a id="async-with-task-executor">@Async và TaskExecutor: Lời khẳng định và Câu trả lời</a>

Trong Spring Framework, đây là sự kết hợp hoàn hảo giữa **Lập trình hướng khía cạnh (AOP)** và **Quản lý tài nguyên (Thread Pool)**.

* **@Async:** Là lời khẳng định: *"Này Spring, hãy chạy hàm này ở luồng khác, đừng bắt luồng chính phải đợi!"*.
* **TaskExecutor:** Là câu trả lời cho: *"Sử dụng luồng nào, quản lý chúng ra sao?"*.

## 1. Cơ chế hoạt động (Phía sau hậu trường)

Khi bạn đánh dấu một hàm là `@Async`, Spring thực hiện quy trình 4 bước:
1.  **Tạo Proxy:** Spring tạo một lớp bọc (Proxy) quanh Bean chứa hàm đó.
2.  **Intercept (Chặn):** Khi hàm được gọi, Proxy sẽ chặn cuộc gọi lại thay vì thực thi ngay.
3.  **Submit Task:** Proxy đi tìm một `TaskExecutor` phù hợp trong `ApplicationContext`.
4.  **Chạy ngầm:** Đẩy logic hàm vào Thread Pool. Luồng chính (Caller) thoát ra ngay lập tức để làm việc khác.


## 2. Tại sao BẮT BUỘC phải dùng chung với TaskExecutor?

Nếu bạn dùng `@Async` mà không cấu hình `TaskExecutor` cụ thể:
* Spring sử dụng `SimpleAsyncTaskExecutor` mặc định.
* **Nguy hiểm:** Cái mặc định này **không tái sử dụng Thread**. Mỗi lần gọi là một lần tạo Thread mới và hủy đi.
* **Hậu quả:** Nếu có 1000 request cùng lúc, server sẽ tạo 1000 Thread $\rightarrow$ Cạn kiệt tài nguyên và treo máy.

## 3. Cách triển khai trong Project

### Bước 1: Kích hoạt Async
Tại class `@Configuration`, bạn cần thêm Annotation kích hoạt:

```java
    @Configuration
    @EnableAsync // Bắt buộc để Spring quét các @Async
    public class AsyncConfig { }
```

### Bước 2: Khai báo "Nhà máy" Thread (TaskExecutor)

```java
    @Bean(name = "myCustomExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("JavaLearn-Async-");
        executor.initialize();
        return executor;
    }
```

### Bước 3: Sử dụng
Chỉ định rõ tên Bean Executor muốn sử dụng:

```java
    @Service
    public class OrderService {
        @Async("myCustomExecutor") 
        public void sendEmailConfirmation(Order order) {
            // Logic xử lý nặng thực hiện ở luồng khác
            System.out.println("Đang gửi mail bằng: " + Thread.currentThread().getName());
        }
    }
```

## 4. Các lưu ý "Sống còn"

* **Self-invocation (Tự gọi nội bộ):** Nếu bạn gọi hàm `@Async` từ một hàm khác **trong cùng một Class**, nó sẽ chạy **ĐỒNG BỘ** (không Async). Vì cuộc gọi này không đi xuyên qua Proxy của Spring.
* **Public method:** Hàm `@Async` bắt buộc phải là `public`.
* **Giá trị trả về:** * Dùng `void` nếu không cần lấy kết quả.
    * Dùng `CompletableFuture<T>` nếu cần nhận kết quả xử lý sau đó.

---

> **Tóm lại:** > * `@Async` giúp code sạch (không cần `new Thread().start()`).
> * `TaskExecutor` giúp hệ thống an toàn (kiểm soát tài nguyên).