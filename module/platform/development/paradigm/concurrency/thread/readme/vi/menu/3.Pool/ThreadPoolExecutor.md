<a id="back-to-top"></a>

# Thread Pool Executor

## Menu
- [Executor Service & BlockingQueue: Nhà máy và Băng chuyền](#thread-executor)
- [4 Mẫu rập (Factory Methods) phổ biến từ class Executors](#4-executor-method)

## <a id="thread-executor">Executor Service & BlockingQueue: Nhà máy và Băng chuyền</a>
<details>
<summary>Click for details</summary>


Nếu `CompletableFuture` là người điều phối, thì **Executor Service** chính là nhà máy, và **BlockingQueue** là băng chuyền kết nối giữa khách hàng và công nhân.

## 1. Mối quan hệ "Kiềng ba chân"

Để hiểu Executor, bạn cần nắm vững cách 3 thành phần này phối hợp:
1.  **Executor (Nhà máy):** Quản lý toàn bộ vòng đời của các luồng.
2.  **Thread Pool (Công nhân):** Tập hợp các luồng sẵn sàng xử lý task.
3.  **BlockingQueue (Kho chứa):** Nơi chứa các task đang chờ khi công nhân đang bận.

## 2. Đi sâu vào ThreadPoolExecutor (Trọng tâm)

Đây là class quan trọng nhất mà bạn cần cấu hình trong dự án `java-learning`. Các thông số cốt lõi bao gồm:
* **Core Pool Size:** Số lượng công nhân "biên chế" (luôn túc trực).
* **Maximum Pool Size:** Số lượng công nhân tối đa (kể cả thời vụ) khi quá tải.
* **Keep Alive Time:** Thời gian công nhân thời vụ sẽ bị "sa thải" nếu không có việc làm.
* **BlockingQueue:** "Kho chứa" tạm thời cho các task.

## 3. BlockingQueue - Cơ chế điều phối thông minh

Tại sao gọi là **Blocking**? Vì nó có khả năng tự động điều tiết:
* **Chặn người lấy (Worker):** Nếu kho rỗng, công nhân sẽ đứng đợi (block) cho đến khi có task mới.
* **Chặn người bỏ vào (Producer):** Nếu kho đầy, người đưa task vào phải đợi (block) cho đến khi có chỗ trống.

| Loại Queue | Đặc điểm | Kịch bản sử dụng |
| :--- | :--- | :--- |
| **LinkedBlockingQueue** | Dung lượng lớn (mặc định là vô hạn). | Hầu hết các ứng dụng thông thường. |
| **ArrayBlockingQueue** | Dung lượng cố định, bộ nhớ liên tục. | Kiểm soát chặt bộ nhớ, tránh OutOfMemory. |
| **SynchronousQueue** | Dung lượng bằng 0 (chuyển tay trực tiếp). | Tạo luồng mới ngay lập tức (CachedThreadPool). |

## 4. Quy trình xử lý Task (Rất quan trọng)

Quy trình này thường bị hiểu nhầm. Đây là thứ tự đúng mà Java thực hiện:
1.  **Task đến:** Nếu số luồng < `corePoolSize` $\rightarrow$ **Tạo luồng mới**.
2.  **Task tiếp theo:** Nếu đã đủ `corePoolSize` $\rightarrow$ **Đẩy task vào BlockingQueue**.
3.  **Khi Queue đầy:** Lúc này mới **tạo thêm luồng mới** (lên đến `maxPoolSize`).
4.  **Khi vượt ngưỡng:** Kích hoạt `RejectedExecutionHandler`.

## 5. Cấu hình Executor chuẩn cho Project

Thay vì dùng `Executors.newFixedThreadPool()` (dễ gây tràn bộ nhớ do Queue vô hạn), bạn nên tự build như sau:

```java
    @Bean(name = "completableFutureExecutor")
    public Executor completableFutureExecutor() {
        return new ThreadPoolExecutor(
            5,                      // corePoolSize
            10,                     // maxPoolSize
            60, TimeUnit.SECONDS,   // keepAliveTime
            new ArrayBlockingQueue<>(100), // Kho chứa tối đa 100 task
            new ThreadPoolExecutor.CallerRunsPolicy() // Chiến thuật Back-pressure
        );
    }
```

> **Giải thích CallerRunsPolicy:** > Đây là chiến thuật cực hay. Khi "nhà máy" quá tải, thay vì vứt bỏ task, nó bắt chính "luồng Main" phải tự làm việc đó. Điều này giúp hệ thống tự động chậm lại (**Back-pressure**), ngăn chặn việc đẩy thêm task khi hệ thống không còn sức chứa.


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="4-executor-method">4 Mẫu rập (Factory Methods) phổ biến từ class Executors</a>
<details>
<summary>Click for details</summary>


Dù tất cả đều tạo ra `ExecutorService`, nhưng chúng khác nhau hoàn toàn về cách quản lý số lượng luồng và loại hàng đợi (`Queue`) sử dụng bên dưới.

## 1. `newFixedThreadPool(n)`

* **Cơ chế:** Tạo ra một pool với số lượng luồng cố định.
* **Đặc điểm:** * `corePoolSize` = `maxPoolSize` = `n`.
    * Sử dụng `LinkedBlockingQueue` với dung lượng **vô hạn** (unbounded).
* **Khi nào dùng:** Khi bạn muốn giới hạn tài nguyên chính xác (ví dụ: chỉ cho 10 luồng xử lý DB để tránh làm sập Database).
* **Rủi ro:** Nếu task đổ về quá nhanh, hàng đợi phình to gây `OutOfMemoryError` (OOM).

## 2. `newCachedThreadPool()`

* **Cơ chế:** Pool "co giãn" linh hoạt theo nhu cầu thực tế.
* **Đặc điểm:** * `corePoolSize` = 0, `maxPoolSize` = `Integer.MAX_VALUE`.
    * Sử dụng `SynchronousQueue` (hàng đợi dung lượng bằng 0, task đi thẳng sang luồng xử lý).
    * Luồng rảnh quá 60 giây sẽ bị tiêu hủy.
* **Khi nào dùng:** Cho các tác vụ ngắn, số lượng lớn, yêu cầu xử lý ngay lập tức.
* **Rủi ro:** Nếu đẩy hàng triệu task chạy lâu, nó sẽ tạo hàng triệu luồng gây treo hệ thống.

## 3. `newSingleThreadExecutor()`

* **Cơ chế:** Chỉ có duy nhất một luồng hoạt động tại một thời điểm.
* **Đặc điểm:** * Đảm bảo tác vụ xử lý tuần tự (FIFO).
    * Nếu luồng chết do lỗi, một luồng khác sẽ tự động được tạo ra thay thế.
* **Khi nào dùng:** Cần đảm bảo tính thứ tự tuyệt đối hoặc xử lý các tác vụ không cho phép song song (ghi log, update bảng tuần tự).

## 4. `newScheduledThreadPool(n)`

* **Cơ chế:** Cho phép lập lịch chạy task (sau một khoảng trễ hoặc định kỳ).
* **Đặc điểm:** Sử dụng `DelayedWorkQueue` để quản lý thời gian.
* **Khi nào dùng:** Thay thế cho lớp `Timer`. Dùng để cleanup dữ liệu, gửi email nhắc lịch, hoặc health check định kỳ.

## Bảng so sánh tổng hợp

**| Loại Executor | Số luồng (Core/Max) | Loại Queue | Kịch bản lý tưởng |
| :--- | :--- | :--- | :--- |
| **Fixed** | n / n | Linked (Vô hạn) | Hệ thống ổn định, giới hạn tài nguyên. |
| **Cached** | 0 / Vô hạn | Synchronous (Trực tiếp) | Nhiều task ngắn, cần phản hồi nhanh. |
| **Single** | 1 / 1 | Linked (Vô hạn) | Xử lý tuần tự, tránh tranh chấp. |
| **Scheduled** | n / Vô hạn | Delayed (Ưu tiên) | Tác vụ định kỳ, lập lịch. |**

</details>

- [Quay lại đầu trang](#back-to-top)