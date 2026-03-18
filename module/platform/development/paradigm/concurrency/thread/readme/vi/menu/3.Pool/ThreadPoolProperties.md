<a id="back-to-top"></a>

# Thread Pool Properties

## Menu
- [Tìm hiểu về Thread Pool và Cấu hình TaskExecutor](#thread-pool-basic)
- [corePoolSize: Đội ngũ nhân viên biên chế](#core-pool-size)
- [maximumPoolSize: Đội ngũ nhân viên thời vụ và Ngưỡng giới hạn](#maximum-pool-size)
- [keepAliveTime: Thời gian chờ và Cơ chế "Sa thải" Thread](#keep-alive-time)
- [QueueCapacity: Băng chuyền điều tiết và Kho lưu trữ](#queue-capacity)
- [prestartAllCoreThreads: Chiến thuật "Nóng máy" hệ thống](#prestart-all-core-thread)
- [Graceful Shutdown: Cơ chế "Hạ cánh an toàn" cho Thread Pool](#wait-for-task)
- [AwaitTerminationSeconds: Giới hạn sự kiên nhẫn khi Shutdown](#await-termination-shutdown)
- [TaskDecorator: "Người vận chuyển" Context xuyên suốt các luồng](#task-decorator)
- [allowCoreThreadTimeOut: Linh hoạt hóa "Nhân viên biên chế"](#allow-core-thread-time-out)
- [Tìm hiểu về RejectedExecutionHandler](#rejected-execution-handler)
- [RejectedExecutionHandler: Các ví dụ cho các Policy"](#reject-execution-handler-example)

---
## <a id="thread-pool-basic">Tìm hiểu về Thread Pool và Cấu hình TaskExecutor</a>
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

---


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="core-pool-size">corePoolSize: Đội ngũ nhân viên biên chế</a>
<details>
<summary>Click for details</summary>


`corePoolSize` là thông số quan trọng nhất trong một Thread Pool. Bạn có thể hiểu đơn giản đây là số lượng **"nhân viên biên chế"** luôn có mặt tại nhà máy.

## 1. Định nghĩa cơ bản

`corePoolSize` là số lượng Thread tối thiểu mà Thread Pool cố gắng duy trì.
* Các Thread này sẽ luôn sống ngay cả khi đang rảnh rỗi.
* Khi một Task mới được gửi đến, nếu số Thread hiện tại < `corePoolSize`, hệ thống sẽ **tạo mới** Thread ngay lập tức thay vì dùng lại các Thread rảnh (giúp tăng tốc độ xử lý ban đầu).

## 2. Vai trò trong quy trình xử lý Task

Để hiểu `corePoolSize`, chúng ta cần xem xét mối quan hệ giữa nó với `BlockingQueue` và `maximumPoolSize`:

1.  **Giai đoạn Khởi động:** Task < `corePoolSize` $\rightarrow$ Tạo mới Thread biên chế.
2.  **Giai đoạn Ổn định:** Task gửi đến khi đã đủ `corePoolSize` $\rightarrow$ Đưa Task vào `BlockingQueue` (Hàng đợi/Kho chứa).
3.  **Giai đoạn Quá tải:** Khi `BlockingQueue` đã đầy $\rightarrow$ Tạo thêm Thread thời vụ (cho đến khi chạm ngưỡng `maximumPoolSize`).

## 3. Tại sao lại cần corePoolSize?

* **Tiết kiệm tài nguyên:** Việc tạo mới Thread rất "đắt đỏ" (tốn CPU/RAM). Duy trì một lượng Thread sẵn có giúp tránh việc hủy/tạo lại liên tục.
* **Phản hồi nhanh:** Giảm độ trễ (latency) cho các yêu cầu đầu tiên vì luôn có sẵn nhân viên chờ việc.

## 4. Cấu hình corePoolSize bao nhiêu là đủ?

Tùy thuộc vào loại tác vụ trong module `concurrency` của bạn:

| Loại Task | Đặc điểm | Công thức gợi ý |
| :--- | :--- | :--- |
| **CPU-Bound** | Tính toán nặng, xử lý thuật toán. | $N_{threads} = N_{CPUs}$ |
| **IO-Bound** | Gọi API, Database, Đọc/Ghi file. | $N_{threads} = N_{CPUs} \times 2$ (hoặc hơn) |

## 5. Lưu ý đặc biệt cho Java Developer

* **`allowCoreThreadTimeOut`:** Mặc định core thread sống mãi. Nếu set `true`, core thread sẽ bị tiêu hủy nếu rảnh rỗi quá thời gian quy định (giảm tải cho hệ thống khi ít việc).
* **`prestartAllCoreThreads()`:** Giúp "warm-up" hệ thống. Vừa bật máy là các core thread được tạo sẵn ngay, không đợi task đến mới tạo.

## 6. Ví dụ thực tế trong Project

Nếu bạn cấu hình `ThreadPoolTaskExecutor` như sau:

    ```java
    executor.setCorePoolSize(5);
    executor.setQueueCapacity(100);
    executor.setMaxPoolSize(10);
    ```

**Hệ quả:** Hệ thống sẽ luôn ưu tiên dùng 5 thread. Chỉ khi có hơn 105 task (5 core + 100 trong queue) thì thread thứ 6 mới được huy động.


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="maximum-pool-size">maximumPoolSize: Đội ngũ nhân viên thời vụ và Ngưỡng giới hạn</a>
<details>
<summary>Click for details</summary>


Nếu `corePoolSize` là số lượng nhân viên "biên chế", thì `maximumPoolSize` chính là tổng số lượng nhân viên tối đa (biên chế + thời vụ) mà một Thread Pool có thể huy động. Đây là chốt chặn cuối cùng để bảo vệ hệ thống khỏi việc sập do quá tải luồng.

## 1. Khi nào maximumPoolSize mới được kích hoạt?

Đây là điểm cực kỳ dễ nhầm lẫn. Thread Pool **không** tăng lên `maximumPoolSize` ngay khi vừa vượt quá `corePoolSize`. Nó chỉ được kích hoạt khi đồng thời thỏa mãn các điều kiện:

1.  Số lượng Thread đang chạy đã đạt đến `corePoolSize`.
2.  Hàng đợi (**BlockingQueue**) đã đầy 100%.
3.  Có thêm một Task mới gửi đến.

Lúc này, hệ thống mới bắt đầu tạo thêm các **"nhân viên thời vụ"** để xử lý Task mới đó, cho đến khi tổng số Thread bằng `maximumPoolSize`.

## 2. Đặc điểm của các "Thread thời vụ"

Các Thread được tạo ra trong khoảng từ `core` đến `max` có tính chất:
* **Tính tạm thời:** Chỉ xuất hiện khi hệ thống rơi vào trạng thái "khẩn cấp" (Queue đã kín chỗ).
* **Tự hủy:** Nếu sau một khoảng thời gian (`keepAliveTime`) mà các Thread này không có việc gì làm, chúng sẽ bị tiêu hủy để trả lại tài nguyên RAM và CPU cho hệ thống.

## 3. Điều gì xảy ra khi vượt ngưỡng maximumPoolSize?

Khi số lượng Thread đã chạm mốc `maximumPoolSize` và hàng đợi vẫn đang đầy:
* Hệ thống không tạo thêm Thread nào nữa.
* Kích hoạt **`RejectedExecutionHandler`** (Chiến thuật từ chối) để xử lý Task bị dư thừa (ném lỗi, vứt bỏ, hoặc bắt luồng Main tự xử lý).

## 4. Ví dụ phối hợp thông số trong Project

Hãy xem ví dụ cấu hình `ThreadPoolTaskExecutor` sau:

    ```java
    executor.setCorePoolSize(5);      // 5 nhân viên biên chế
    executor.setQueueCapacity(100);  // Kho chứa 100 đơn hàng
    executor.setMaxPoolSize(20);     // Tổng cộng tối đa 20 nhân viên
    ```

* **Task 1 - 5:** Tạo 5 Thread (Core).
* **Task 6 - 105:** Đẩy vào Queue (Core vẫn giữ nguyên là 5).
* **Task 106 - 120:** Bắt đầu tạo thêm từ Thread thứ 6 đến thứ 20 (Max).
* **Task 121+:** Kích hoạt chiến thuật từ chối (Rejected).

## 5. Lưu ý quan trọng khi đặt giá trị max

* **Tránh đặt quá lớn:** Có thể dẫn đến hiện tượng **Context Switching** (CPU mất quá nhiều thời gian chuyển đổi giữa các luồng), gây treo máy.
* **Bẫy Queue vô hạn:** Nếu dùng `LinkedBlockingQueue` mặc định (vô hạn), thông số `maximumPoolSize` sẽ trở nên **vô dụng** vì Queue không bao giờ đầy để kích hoạt thêm Thread mới.

| Thông số | Ý nghĩa | Trạng thái luồng |
| :--- | :--- | :--- |
| **Core** | Biên chế | Luôn sống |
| **Max** | Tổng lực | Tự hủy sau `keepAliveTime` |
| **Queue** | Lưu trữ | Chờ xử lý khi Core bận |


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="keep-alive-time">keepAliveTime: Thời gian chờ và Cơ chế "Sa thải" Thread</a>
<details>
<summary>Click for details</summary>


`keepAliveTime` là khoảng thời gian mà các **"nhân viên thời vụ"** (các Thread vượt quá con số `corePoolSize`) được phép rảnh rỗi trước khi bị tiêu hủy để tiết kiệm tài nguyên cho hệ thống.

## 1. Cơ chế hoạt động của keepAliveTime

Khi khối lượng công việc giảm xuống và một Thread (không phải core thread) hoàn thành nhiệm vụ, nó sẽ không biến mất ngay mà rơi vào trạng thái chờ:
* **Nếu có Task mới đến** trong khoảng thời gian này: Thread đó sẽ lập tức "bắt" lấy task và tiếp tục làm việc.
* **Nếu hết thời gian** (`keepAliveTime`) mà vẫn không có việc: Thread đó sẽ chính thức bị tiêu hủy (terminate).

## 2. Đối tượng áp dụng

Mặc định, cơ chế này có sự phân biệt rõ ràng giữa các loại nhân sự trong Pool:
* **Core Threads:** Sống mãi mãi ngay cả khi rảnh rỗi.
* **Non-core Threads (Thời vụ):** Phụ thuộc hoàn toàn vào `keepAliveTime`.

> **Mẹo nâng cao:** Nếu bạn bật `allowCoreThreadTimeOut(true)`, ngay cả các Core Threads cũng sẽ bị tiêu hủy nếu rảnh rỗi quá lâu. Đây là cách tuyệt vời để giải phóng hoàn toàn tài nguyên khi ứng dụng không có truy cập.

## 3. Cấu hình trong ThreadPoolTaskExecutor (Spring)

Thông thường, thông số này được cấu hình bằng đơn vị giây:

```java
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setKeepAliveSeconds(60); // Sau 60s rảnh rỗi, thread thứ 6-10 sẽ bị hủy
```

## 4. Tại sao thông số này lại quan trọng?

Hãy nhìn vào vòng đời của hệ thống `java-learning`:
1. **Giờ cao điểm (9h sáng):** Task đổ về liên tục, Queue đầy, hệ thống huy động tối đa 20 Threads.
2. **Giờ thấp điểm (2h sáng):** Không có ai dùng.

Nếu không có `keepAliveTime`, 20 Threads này sẽ chiếm dụng RAM vĩnh viễn. `keepAliveTime` giúp hệ thống tự động **"thu mình lại"** để tiết kiệm tài nguyên máy chủ khi không cần thiết.

## 5. Chiến thuật chọn giá trị hợp lý

| Giá trị | Ưu điểm | Nhược điểm |
| :--- | :--- | :--- |
| **Nhỏ (Vài giây)** | Giải phóng tài nguyên cực nhanh. | Tốn CPU để tạo/hủy Thread liên tục nếu tải trồi sụt (nhấp nhả). |
| **Lớn (Vài phút)** | Thread sẵn sàng lâu hơn, phản hồi nhanh khi có đợt tải mới. | Chiếm dụng RAM lâu hơn dù không làm việc. |

> **Khuyên dùng:** Đối với Microservices thông thường, giá trị từ **30 đến 60 giây** là con số cân bằng lý tưởng.

## Tóm tắt mối quan hệ "Bộ ba nguyên tử"

| Thông số | Hình ảnh ẩn dụ | Trạng thái |
| :--- | :--- | :--- |
| **corePoolSize** | Nhân viên biên chế | Luôn ở đó chờ việc. |
| **maximumPoolSize** | Tổng nhân lực tối đa | Chỉ huy động khi "vỡ trận" (Queue đầy). |
| **keepAliveTime** | Thông báo nghỉ việc | Thời gian chờ trước khi cho nhân viên thời vụ nghỉ. |


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="queue-capacity">QueueCapacity: Băng chuyền điều tiết và Kho lưu trữ</a>
<details>
<summary>Click for details</summary>


`QueueCapacity` chính là dung lượng của "kho chứa" hoặc "hàng đợi" (`BlockingQueue`). Trong mô hình nhà máy, nếu `corePoolSize` là số công nhân, thì `QueueCapacity` là độ dài của băng chuyền chứa các linh kiện đang chờ lắp ráp.

## 1. Cơ chế hoạt động của QueueCapacity

Trong `ThreadPoolTaskExecutor`, thứ tự ưu tiên khi một Task mới đến được quy định như sau:
1.  **Nếu số luồng < `corePoolSize`:** Tạo luồng mới để xử lý ngay.
2.  **Nếu số luồng = `corePoolSize`:** Task bị đẩy vào **Queue** để nằm chờ cho đến khi có luồng biên chế rảnh tay.
3.  **Nếu Queue đã đầy:** Lúc này hệ thống mới bắt đầu tạo thêm luồng mới (lên đến `maximumPoolSize`).

## 2. Phân loại Queue và tác động

| Loại Queue | Cách thiết lập | Đặc điểm & Hệ quả |
| :--- | :--- | :--- |
| **Bounded Queue** | `setQueueCapacity(100)` | **Khuyên dùng:** Giúp kiểm soát RAM, kích hoạt luồng Max khi cần thiết. |
| **Unbounded Queue** | Không set (mặc định) | **Nguy hiểm:** `maximumPoolSize` trở nên vô dụng. Dễ gây lỗi `OutOfMemory` (OOM). |

## 3. Chiến thuật chọn QueueCapacity hợp lý

Việc chọn dung lượng hàng đợi là một bài toán đánh đổi (**trade-off**):

    | Giá trị | Ưu điểm | Nhược điểm |
    | :--- | :--- | :--- |
    | **Nhỏ (10-50)** | Kích hoạt luồng Max sớm, xử lý song song mạnh khi tải tăng. | Tốn CPU do tạo/hủy luồng liên tục. |
    | **Lớn (500-1000)** | Giảm tải CPU, hệ thống ổn định khi có đợt tải đột ngột (spike). | Độ trễ (Latency) cao, tốn nhiều RAM hơn. |

> **Công thức thực dụng:** Bắt đầu với con số khoảng **100 - 500** cho các ứng dụng Web/Microservices thông thường.

## 4. Ví dụ kịch bản "Vỡ trận"

Giả sử cấu hình: **Core: 5, Queue: 10, Max: 10**.
* **15 Task đầu tiên:** 5 cái chạy ngay, 10 cái nằm chờ trong Queue.
* **Task thứ 16:** Queue đã đầy (10/10). Hệ thống tạo thêm luồng thứ 6 (biến chế thời vụ).
* **Task thứ 21:** Tổng số luồng đạt Max (10) và Queue đã đầy (10). Task này bị **Rejected**.

## 5. Lưu ý quan trọng cho Project `java-learning`

Trong Spring, nếu bạn không set `QueueCapacity`, mặc định nó sẽ dùng `Integer.MAX_VALUE`. Điều này cực kỳ nguy hiểm vì nó sẽ "nuốt" sạch RAM trước khi kịp kích hoạt các luồng bổ sung.

    ```java
    executor.setCorePoolSize(10);
    executor.setQueueCapacity(200); // Luôn giới hạn kho chứa để bảo vệ RAM
    executor.setMaxPoolSize(30);
    ```

---

### Tóm tắt quy trình:
1.  **Chưa đủ Core?** $\rightarrow$ Thuê thêm nhân viên biên chế.
2.  **Đủ Core rồi?** $\rightarrow$ Đẩy hàng vào kho (Queue).
3.  **Kho đầy rồi?** $\rightarrow$ Thuê thêm nhân viên thời vụ (Max).
4.  **Tất cả đều đầy?** $\rightarrow$ Từ chối nhận đơn hàng (Reject).


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="prestart-all-core-thread">prestartAllCoreThreads: Chiến thuật "Nóng máy" hệ thống</a>
<details>
<summary>Click for details</summary>


Đây là một tính năng cực kỳ hữu ích để tối ưu hiệu năng ngay khi ứng dụng khởi động (**Startup**), giúp hệ thống sẵn sàng chiến đấu ngay từ giây đầu tiên thay vì chờ đợi Task đến mới khởi tạo luồng.

## 1. Bản chất của prestartAllCoreThreads

Mặc định, các luồng trong Thread Pool hoạt động theo cơ chế **Lazy Initialization** (Khởi tạo lười biếng):
* **Bình thường:** Khi Server khởi động, dù bạn cấu hình `corePoolSize = 10`, số luồng thực tế vẫn là **0**. Task 1 đến mới tạo Thread 1, Task 2 đến mới tạo Thread 2...
* **Khi dùng prestart:** Ngay khi Pool được khởi tạo, hệ thống bị ép buộc tạo đủ số luồng bằng đúng `corePoolSize`. Các luồng này sẽ ở trạng thái `WAITING` để chờ việc.

## 2. Tại sao bạn nên dùng nó? (Kịch bản Warm-up)

Khi ứng dụng vừa deploy hoặc khởi động lại, nếu có một lượng lớn Request tràn vào cùng lúc:
* **Vấn đề:** CPU bị vọt lên (**Spike**) vì phải vừa xử lý logic, vừa tốn tài nguyên khởi tạo hàng loạt Thread mới. Điều này gây ra độ trễ (**Latency**) cho những người dùng đầu tiên.
* **Giải pháp:** Trả chi phí tạo luồng ngay từ lúc Startup. Khi Request đến, "đội quân" đã đứng chờ sẵn, giúp phản hồi nhanh tức thì.

## 3. Cách cấu hình trong Spring (ThreadPoolTaskExecutor)

Trong Spring Boot, chúng ta cần can thiệp vào giai đoạn khởi tạo Bean để kích hoạt tính năng này:

    ```java
    @Bean(name = "completableFutureExecutor")
    public Executor completableFutureExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("JavaLearn-");

        // Bắt buộc phải gọi initialize() trước khi prestart
        executor.initialize();

        // Truy cập vào ThreadPoolExecutor thô bên dưới để gọi prestart
        executor.getThreadPoolExecutor().prestartAllCoreThreads();

        return executor;
    }
    ```

## 4. Khi nào NÊN và KHÔNG NÊN dùng?

| Nên dùng khi... | Không nên dùng khi... |
| :--- | :--- |
| Cần độ trễ thấp nhất cho các yêu cầu đầu tiên. | Có quá nhiều Thread Pool (gây tốn RAM đồng loạt). |
| Hệ thống có lượng truy cập lớn ngay sau deploy. | Tài nguyên hạn hẹp (Máy ảo yếu, máy cá nhân). |
| Muốn đảm bảo RAM được cấp phát đủ từ đầu. | Các tác vụ Async rất hiếm khi xảy ra. |

## 5. Lưu ý quan trọng

* **Tính khả dụng:** Nếu bạn cấu hình `corePoolSize = 0`, hàm này sẽ không có tác dụng vì không có "nhân viên biên chế" nào để khởi động trước.
* **Tài nguyên:** Hãy cân nhắc con số `corePoolSize` vừa phải để tránh lãng phí bộ nhớ cho những luồng không bao giờ dùng tới.


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="wait-for-task">Graceful Shutdown: Cơ chế "Hạ cánh an toàn" cho Thread Pool</a>
<details>
<summary>Click for details</summary>


Trong Spring `ThreadPoolTaskExecutor`, việc đảm bảo an toàn dữ liệu (**Data Integrity**) khi tắt ứng dụng là cực kỳ quan trọng. Thuộc tính `setWaitForTasksToCompleteOnShutdown` chính là chốt chặn để thực hiện điều đó.

## 1. Bản chất của setWaitForTasksToCompleteOnShutdown(true)

Mặc định, khi JVM tắt, các Thread Pool bị buộc dừng ngay lập tức. Điều này khiến các Task đang chạy dở dang bị "khai tử" ngay tại chỗ.

* **Khi set là `false` (Mặc định):** Server tắt $\rightarrow$ Ngắt điện toàn bộ luồng $\rightarrow$ Task đang xử lý giao dịch bị mất dữ liệu giữa chừng.
* **Khi set là `true`:** Server nhận lệnh tắt $\rightarrow$ Thread Pool ngừng nhận Task mới, nhưng sẽ **đợi** các Task hiện tại làm xong và giải phóng hết hàng đợi rồi mới đóng lại.

## 2. Tại sao dự án `java-learning` cần cái này?

Hãy tưởng tượng kịch bản trong Module Order:
1. Hệ thống đẩy một Task Async để trừ tiền và gửi Email hóa đơn.
2. Bạn tiến hành deploy bản mới qua GitHub Actions (Restart server).
3. **Nếu không có thuộc tính này:** Tiền có thể đã trừ nhưng Email chưa gửi, hoặc logic DB bị rollback không đồng bộ do tiến trình bị ngắt đột ngột.

## 3. "Người bạn đồng hành": awaitTerminationSeconds

Nếu chỉ đợi mà không giới hạn thời gian, Server có thể bị treo vĩnh viễn nếu Task bị lỗi. Spring cung cấp `setAwaitTerminationSeconds` để giới hạn "sự kiên nhẫn":

    ```java
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30); // Đợi tối đa 30 giây
    ```

**Cơ chế:** Nếu sau 30 giây các Task vẫn chưa xong, Spring sẽ cưỡng ép đóng Thread Pool để Server có thể tắt hoàn toàn.

## 4. Cấu hình chuẩn trong AsyncConfig

Bạn nên đưa cặp bài trùng này vào class cấu hình để đảm bảo hệ thống luôn "hạ cánh" êm ái:

    ```java
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);

        // Cấu hình hạ cánh an toàn
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60); // Đợi 1 phút trước khi "rút phích điện"

        executor.initialize();
        return executor;
    }
    ```

## 5. Bảng so sánh tóm tắt

| Trạng thái | Hành vi khi Shutdown | Hậu quả |
| :--- | :--- | :--- |
| **Mặc định (false)** | Dừng ngay lập tức (Stop Now). | Dễ mất dữ liệu, corrupt DB, log dở dang. |
| **Bật (true)** | Đợi hoàn tất (Graceful Shutdown). | **An toàn dữ liệu**, Shutdown chậm hơn một chút. |

> **Lưu ý cho môi trường Docker/K8s:** > Thời gian `awaitTerminationSeconds` nên nhỏ hơn thời gian timeout của Container (thường là 30s) để Spring kịp dọn dẹp trước khi bị Docker "kill process" một cách thô bạo.


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="await-termination-shutdown">AwaitTerminationSeconds: Giới hạn sự kiên nhẫn khi Shutdown</a>
<details>
<summary>Click for details</summary>


Nếu `setWaitForTasksToCompleteOnShutdown` là lệnh "Hãy đợi tôi", thì `setAwaitTerminationSeconds` chính là câu trả lời cho câu hỏi: **"Đợi trong bao lâu?"**.

## 1. Bản chất của AwaitTerminationSeconds

Thông số này định nghĩa thời gian tối đa (tính bằng giây) mà Spring sẽ tạm dừng quá trình tắt máy để chờ các luồng hoàn thành công việc.

* **Nếu Task xong trước thời hạn:** Ứng dụng sẽ tắt ngay lập tức (Lý tưởng).
* **Nếu hết thời gian mà Task chưa xong:** Spring sẽ không đợi nữa, nó thực hiện **Force Shutdown** (đóng cưỡng ép) để đảm bảo toàn bộ ứng dụng không bị treo.

## 2. Tại sao không nên chỉ dùng lệnh "Đợi" mà không có "Thời hạn"?

Nếu bạn chỉ bật `WaitForTasksToCompleteOnShutdown(true)` mà không giới hạn thời gian:
* **Nguy cơ treo máy:** Nếu một Task bị vòng lặp vô hạn hoặc treo do lỗi kết nối API/DB, Thread Pool sẽ không bao giờ đóng.
* **Hệ quả:** Docker Container hoặc Server sẽ kẹt ở trạng thái "Stopping..." mãi mãi, buộc bạn phải can thiệp thủ công bằng `kill -9`.

## 3. Cách cấu hình tối ưu trong Spring

Bạn nên cấu hình dựa trên thời gian thực thi lâu nhất của một Task trong hệ thống:

    ```java
    @Bean
    public Executor myExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // ... các cấu hình khác ...

        // Bật chế độ đợi
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Thời gian chờ đợi tối đa (ví dụ 30 giây)
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }
    ```

## 4. Mối liên hệ với Docker & CI/CD (Lưu ý cho DevOps)

Khi vận hành trên Docker hoặc Kubernetes, đây là quy tắc "xương máu":
1. **Docker Stop:** Gửi tín hiệu `SIGTERM` và đợi mặc định **10 giây** trước khi gửi `SIGKILL` (giết chết ngay lập tức).
2. **Kịch bản lỗi:** Nếu bạn để `AwaitTerminationSeconds(60)` nhưng Docker chỉ đợi 10 giây, thì sau 10 giây Docker vẫn sẽ "giết" ứng dụng trước khi nó kịp dọn dẹp xong.

> **Giải pháp:** Luôn đảm bảo: **AwaitTerminationSeconds < Thời gian chờ của Docker/Kubernetes.**

## 5. So sánh các kịch bản thực tế

| Kịch bản | Cấu hình | Kết quả khi Shutdown |
| :--- | :--- | :--- |
| **Cẩu thả** | Không set gì cả | Task bị ngắt điện ngay lập tức $\rightarrow$ **Lỗi dữ liệu.** |
| **Cực đoan** | Wait=true, Await=0 | Giống như không set, vì thời gian chờ bằng 0. |
| **Chuyên nghiệp** | Wait=true, Await=30 | Đợi Task xong, nếu quá 30s mới cưỡng chế $\rightarrow$ **An toàn & Ổn định.** |

---

> **Tóm lại:** `AwaitTerminationSeconds` là "cái phanh" an toàn giúp bạn kiểm soát quá trình đóng ứng dụng một cách văn minh (Graceful Shutdown).


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="task-decorator">TaskDecorator: "Người vận chuyển" Context xuyên suốt các luồng</a>
<details>
<summary>Click for details</summary>


Trong hệ sinh thái Spring, `TaskDecorator` là một Interface cực kỳ mạnh mẽ, đóng vai trò can thiệp vào quá trình copy dữ liệu từ luồng cha (Parent Thread) sang luồng con (Child Thread) trước khi một tác vụ Async được thực thi.

## 1. Tại sao cần TaskDecorator?

Hãy tưởng tượng kịch bản trong dự án `java-learning`:
* **Luồng chính:** Đang giữ thông tin người dùng trong `SecurityContext` hoặc mã định danh log trong `MDC` (Mapped Diagnostic Context).
* **Vấn đề:** Khi gọi `@Async`, Spring lấy một Thread mới từ Pool. Thread này hoàn toàn "trống rỗng", nó không biết người dùng hiện tại là ai hoặc Trace ID là gì.
* **Giải pháp:** `TaskDecorator` sẽ thực hiện nhiệm vụ "chụp ảnh" (Snapshot) dữ liệu ở luồng cha và "dán" (Paste) vào luồng con.

## 2. Cách thức hoạt động (Cơ chế Wrap)

`TaskDecorator` hoạt động theo cơ chế **Wrapper**. Nó bọc lấy `Runnable` gốc của bạn bằng một `Runnable` mới có chứa logic truyền dẫn dữ liệu.

    ```java
    public interface TaskDecorator {
        Runnable decorate(Runnable runnable);
    }
    ```

## 3. Ví dụ thực tế: Truyền thông tin Log (MDC)

Để log có thể xuyên suốt từ lúc nhận Request đến khi xử lý Async, bạn định nghĩa Decorator như sau:

    ```java
    public class MdcTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            // 1. Chụp ảnh context của luồng cha (đang đứng ở luồng cha)
            Map<String, String> contextMap = MDC.getCopyOfContextMap();

            return () -> {
                try {
                    // 2. Thiết lập context cho luồng con (đã sang luồng con)
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    // 3. Thực thi task gốc
                    runnable.run();
                } finally {
                    // 4. Dọn dẹp để tránh Memory Leak (CỰC KỲ QUAN TRỌNG!)
                    MDC.clear();
                }
            };
        }
    }
    ```

## 4. Cấu hình vào ThreadPoolTaskExecutor

Sau khi định nghĩa, bạn "gắn" nó vào Executor trong file `AsyncConfig`:

    ```java
    @Bean
    public Executor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // ... (các cấu hình core, max, queue) ...

        // Gắn "người vận chuyển" vào đây
        executor.setTaskDecorator(new MdcTaskDecorator());

        executor.initialize();
        return executor;
    }
    ```

## 5. So sánh các phương pháp truyền dữ liệu

| Phương pháp | Ưu điểm | Nhược điểm |
| :--- | :--- | :--- |
| **Thủ công (Copy tay)** | Đơn giản cho 1-2 vị trí nhỏ lẻ. | Code bị lặp lại (Boilerplate) khắp nơi. |
| **InheritableThreadLocal** | Tự động copy cho luồng con trực tiếp. | **Không dùng cho Thread Pool** (vì Thread được tái sử dụng, gây rác dữ liệu). |
| **TaskDecorator** | **Tối ưu nhất cho Spring:** Sạch sẽ, an toàn với Thread Pool. | Cần cấu hình thông qua Spring Bean. |

## Tổng kết quy trình:
1.  **Luồng cha:** Gọi task $\rightarrow$ Decorator "chụp ảnh" dữ liệu.
2.  **Hàng đợi:** Task nằm chờ trong Queue kèm theo "tấm ảnh" dữ liệu đó.
3.  **Luồng con:** Lấy task ra $\rightarrow$ Decorator "dán" ảnh vào luồng con $\rightarrow$ Chạy task $\rightarrow$ **Xóa ảnh** (Clean up).


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="allow-core-thread-time-out">allowCoreThreadTimeOut: Linh hoạt hóa "Nhân viên biên chế"</a>
<details>
<summary>Click for details</summary>


Trong một Thread Pool thông thường, các "nhân viên biên chế" (`corePoolSize`) có đặc quyền là **sống vĩnh viễn**. Thuộc tính `allowCoreThreadTimeOut` cho phép bạn "tước bỏ" đặc quyền đó để tiết kiệm tài nguyên RAM tối đa.

## 1. Cơ chế hoạt động

Mặc định, thuộc tính này có giá trị là `false`.

* **Khi `false` (Mặc định):** Chỉ những Thread thời vụ (vượt ngưỡng Core) mới bị ảnh hưởng bởi `keepAliveTime`. Các luồng Core luôn tồn tại dù rảnh rỗi.
* **Khi `true`:** Thời gian chờ `keepAliveTime` sẽ áp dụng cho **tất cả** các luồng trong Pool. Nếu một luồng Core rảnh rỗi quá thời gian quy định, nó cũng sẽ bị tiêu hủy.

## 2. Tại sao bạn cần thông số này?

Trong quá trình phát triển dự án `java-learning`, bạn có thể chạy nhiều Module hoặc Docker Container trên cùng một máy:
* **Vấn đề:** Nếu có 5 Thread Pool, mỗi cái giữ 10 luồng Core, bạn sẽ có 50 luồng luôn chiếm dụng RAM chạy ngầm dù không có việc.
* **Giải pháp:** Bật `allowCoreThreadTimeOut(true)` giúp hệ thống có khả năng **"về 0" (Zero Threads)** khi không có tải, giải phóng hoàn toàn bộ nhớ cho máy tính.

## 3. Cách cấu hình trong Spring (AsyncConfig)

Bạn có thể thiết lập trực tiếp trong Bean cấu hình như sau:

    ```java
    @Bean
    public Executor myExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setKeepAliveSeconds(60);

        // Kích hoạt tính năng tiêu hủy luồng Core rảnh rỗi
        executor.setAllowCoreThreadTimeOut(true); 

        executor.initialize();
        return executor;
    }
    ```

## 4. So sánh Ưu và Nhược điểm

| Đặc điểm | Khi để `false` (Mặc định) | Khi để `true` |
| :--- | :--- | :--- |
| **Tài nguyên** | Tốn RAM để duy trì luồng chờ. | **Tiết kiệm RAM tối đa** khi hệ thống rảnh. |
| **Phản hồi** | **Cực nhanh** vì luôn có luồng sẵn sàng. | Có độ trễ nhỏ (**Latency**) do phải khởi tạo lại luồng khi có Task mới. |
| **Ứng dụng** | Server có tải ổn định, liên tục. | Môi trường Dev, máy cá nhân, Server tải không thường xuyên. |

## 5. Lưu ý quan trọng (Thread Thrashing)

Nếu bạn bật tính năng này, hãy đảm bảo `keepAliveTime` không quá ngắn (ví dụ 1-2 giây). Nếu không, hệ thống sẽ rơi vào vòng lặp **"hủy rồi lại tạo"** liên tục, gây lãng phí tài nguyên CPU một cách vô ích.

---

> **Tóm lại:** Đây là "nút gạt" giúp hệ thống của bạn trở nên linh hoạt hơn, đặc biệt hiệu quả trong việc tối ưu hóa chi phí vận hành tài nguyên (Infrastructure Cost).


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="rejected-execution-handler">Tìm hiểu về RejectedExecutionHandler</a>
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

| Chiến lược | Hành động | Đánh giá                                      | Khi nào nên dùng? |
| :--- | :--- |:---| :--- |
| **AbortPolicy** | Ném ra ngoại lệ `RejectedExecutionException`. | An toàn nhưng gây crash nếu không xử lý.| **Mặc định.** Dùng khi bạn muốn hệ thống dừng lại và báo lỗi ngay lập tức.  |
| **CallerRunsPolicy** | Luồng gọi (thường là luồng chính/Controller) sẽ tự chạy tác vụ đó. | Khuyên dùng vì tính ổn định và điều tiết tải. | Dùng khi **không được phép mất dữ liệu** và muốn làm chậm tốc độ gửi request (Backpressure). Khi luồng chính phải tự làm việc, nó sẽ chậm lại và không thể gửi thêm Task mới vào Pool nữa, giúp hệ thống có thời gian để "thở".|
| **DiscardPolicy** | Lặng lẽ bỏ qua tác vụ, không báo lỗi, không làm gì cả. | Dễ mất dữ liệu mà không biết. | Dùng cho các tác vụ **không quan trọng** (vd: ghi log phụ, tracking).  |
| **DiscardOldestPolicy** | Xóa tác vụ cũ nhất trong hàng đợi để nhường chỗ cho tác vụ mới. | Tốt cho các dữ liệu real-time. | Dùng cho các hệ thống ưu tiên **dữ liệu mới nhất** (vd: giá chứng khoán, tỉ số bóng đá).     |

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
## <a id="reject-execution-handler-example">RejectedExecutionHandler: Các ví dụ cho các Policy"</a>
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