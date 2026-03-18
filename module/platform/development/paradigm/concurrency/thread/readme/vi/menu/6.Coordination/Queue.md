<a id="back-to-top"></a>

# Queue : Vật chứa trung gian trong Thread

## Menu
- [Queue: Cơ chế điều phối luồng thông minh](#thread)
- [Queue: Sự giao thoa giữa Design Pattern và Lập trình Async](#queue-with-pattern-and-async)
- [1. LinkedBlockingQueue: "Nhà kho linh hoạt"](#linked-blocking-queue)
- [2. SynchronousQueue: "Chuyển phát nhanh - Trao tay ngay"](#synchronous-queue)
- [3. ArrayBlockingQueue: "Nhà kho cố định"](#array-blocking-queue)
- [4. PriorityBlockingQueue: "Hàng ưu tiên - VIP lên trước"](#priority-blocking-queue)



## <a id="thread">Queue: Cơ chế điều phối luồng thông minh</a>
<details>
<summary>Click for details</summary>


Trong hệ sinh thái Concurrency của Java, `BlockingQueue` là loại hàng đợi không chỉ chứa dữ liệu theo kiểu FIFO (First-In-First-Out) mà còn có khả năng tự động điều phối luồng thông qua cơ chế **"Chờ đợi" (Blocking)**.

## 1. Tại sao gọi là "Blocking" (Chặn)?

Cái tên này đến từ hai hành động đặc biệt giúp giải quyết bài toán **Producer-Consumer** (Người sản xuất - Người tiêu dùng) mà không cần dùng `wait()` hay `notify()` thủ công:

* **Chặn khi lấy (Take):** Nếu hàng đợi trống, luồng muốn lấy dữ liệu sẽ bị "treo" cho đến khi có dữ liệu mới được đẩy vào.
* **Chặn khi thêm (Put):** Nếu hàng đợi đầy, luồng muốn thêm dữ liệu sẽ bị "treo" cho đến khi có chỗ trống.

## 2. Vai trò trong Thread Pool

`BlockingQueue` đóng vai trò là "kho chứa" trung gian:
1.  Khi số lượng task vượt quá `corePoolSize`, task sẽ được đẩy vào đây.
2.  Các Thread rảnh rỗi sẽ liên tục gọi hàm `take()` từ Queue. Nếu Queue trống, các Thread này sẽ tự động ngủ yên (tiết kiệm CPU) cho đến khi có task mới được `put()` vào.

## 3. Các loại BlockingQueue phổ biến

Tùy vào cách cấu hình `QueueCapacity`, Java sẽ sử dụng các loại Queue khác nhau:

| Loại Queue | Đặc điểm | Thường dùng với... |
| :--- | :--- | :--- |
| **LinkedBlockingQueue** | Dung lượng có thể hữu hạn hoặc vô hạn. | `newFixedThreadPool`, `ThreadPoolTaskExecutor`. |
| **ArrayBlockingQueue** | Dung lượng cố định, dựa trên mảng. | Hệ thống cần kiểm soát bộ nhớ cực chặt chẽ. |
| **SynchronousQueue** | Dung lượng bằng 0. Task đi thẳng sang luồng xử lý. | `newCachedThreadPool` (Xử lý ngay lập tức). |
| **PriorityBlockingQueue** | Lấy task theo độ ưu tiên (không theo FIFO). | Các task cần xử lý khẩn cấp (VIP). |

## 4. Các bộ phương thức chính

Bạn có 3 lựa chọn xử lý khi hàng đợi đầy hoặc trống:

| Hành động | Ném lỗi (Exception) | Trả về giá trị đặc biệt | Chặn luồng (Blocking) |
| :--- | :--- | :--- | :--- |
| **Thêm vào** | `add(e)` | `offer(e)` (trả về false) | `put(e)` |
| **Lấy ra** | `remove()` | `poll()` (trả về null) | `take()` |

## 5. Ví dụ minh họa (Demo Code)

Bạn có thể triển khai một ví dụ nhỏ trong thư mục `paradigm/concurrency` để quan sát cơ chế chặn:

```java
    BlockingQueue<String> queue = new ArrayBlockingQueue<>(3);

    // Luồng Consumer (Người tiêu thụ)
    new Thread(() -> {
        try {
            while (true) {
                String task = queue.take(); // Sẽ treo tại đây nếu Queue trống
                System.out.println("Đang xử lý: " + task);
                Thread.sleep(2000);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }).start();

    // Luồng Producer (Người sản xuất)
    queue.put("Task 1");
    queue.put("Task 2");
    queue.put("Task 3"); 
    // queue.put("Task 4"); // Sẽ bị treo tại đây cho đến khi Consumer lấy bớt Task 1 ra
```


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="queue-with-pattern-and-async">Queue: Sự giao thoa giữa Design Pattern và Lập trình Async</a>
<details>
<summary>Click for details</summary>


BlockingQueue không chỉ đơn thuần là một cấu trúc dữ liệu; nó đóng vai trò kép: là công cụ thực thi vật lý cho lập trình Async và là linh hồn của mẫu thiết kế **Producer-Consumer**.

### 1. Về mặt Design Pattern: Cốt lõi của "Producer-Consumer"

BlockingQueue hiện thực hóa mẫu thiết kế tách rời (**Decoupling**) hoàn toàn giữa bên tạo việc và bên làm việc:
* **Producers (Người sản xuất):** Đưa dữ liệu vào Queue. Họ không cần quan tâm ai sẽ xử lý hay khi nào xử lý.
* **Consumers (Người tiêu dùng):** Lấy dữ liệu từ Queue ra. Họ không cần biết ai đã tạo ra nó.
* **BlockingQueue (Kênh trung gian):** Đóng vai trò là "vùng đệm" điều tiết, giúp giải quyết bài toán lệch pha tốc độ giữa hai bên.

### 2. Về mặt Async: "Vùng đệm" (Buffer) của thực thi

Trong lập trình không đồng bộ, `BlockingQueue` là thành phần giúp hiện thực hóa tính chất "không đợi":
* **Cơ chế:** Khi bạn gọi `executor.submit()`, luồng chính quay lại làm việc ngay lập tức. Việc Task được xử lý lúc nào là trách nhiệm của Thread Pool.
* **Quản lý trạng thái:** Queue là nơi lưu trữ trạng thái chờ của các tác vụ Async. Nếu không có Queue, hệ thống sẽ sập khi hàng nghìn yêu cầu đổ về cùng lúc mà các Thread đang bận.

### 3. So sánh: Khi Pattern phục vụ Async

| Khía cạnh | Producer-Consumer Pattern | Lập trình Async (ExecutorService) |
| :--- | :--- | :--- |
| **Bên tạo (Producer)** | Các luồng tự viết logic `put()` dữ liệu. | Luồng gọi hàm `submit()` hoặc `execute()`. |
| **Bên xử lý (Consumer)** | Các luồng tự viết logic `take()` dữ liệu. | Các **Worker Threads** trong Pool tự động `take()`. |
| **Mục đích** | Điều tiết tải, tách rời thành phần. | Thực hiện tác vụ ngầm, giải phóng luồng chính. |

### 4. Ứng dụng trong dự án `java-learning`

Dựa trên cấu trúc dự án của bạn (đặc biệt là module `broker` và `observability`), `BlockingQueue` đóng vai trò cực kỳ quan trọng:
* **Tại tầng Pattern:** Bạn dùng nó để tự xây dựng các hệ thống xử lý tin nhắn nội bộ (In-memory Message Broker).
* **Tại tầng Async:** Giúp bạn hiểu tại sao `@Async` của Spring không làm sập server nhờ có `QueueCapacity` giới hạn số lượng task chờ.

### 5. Ví dụ trực quan: Message Broker nội bộ

Trong module `broker`, bạn có thể thấy mô hình này hoạt động như sau:

```java
    // Producer: Web Controller nhận Request từ khách hàng
    public void handleRequest(Order order) {
        // Đẩy vào queue rồi trả về 200 OK ngay (Tính chất Async)
        blockingQueue.put(order); 
    }

    // Consumer: Background Service xử lý thanh toán
    public void processOrder() {
        while(true) {
            // Đứng đợi đơn hàng mới (Tính chất Blocking)
            Order order = blockingQueue.take(); 
            paymentService.charge(order);
        }
    }
```

### Tóm tắt lựa chọn

| Mục tiêu | Loại Queue khuyên dùng |
| :--- | :--- |
| **An toàn / Ổn định** | `LinkedBlockingQueue` (có giới hạn) |
| **Tốc độ / Phản hồi tức thì** | `SynchronousQueue` |
| **Tiết kiệm bộ nhớ / Hiệu năng cao** | `ArrayBlockingQueue` |
| **Xử lý theo mức độ quan trọng** | `PriorityBlockingQueue` |


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="linked-blocking-queue">1. LinkedBlockingQueue: "Nhà kho linh hoạt"</a>
<details>
<summary>Click for details</summary>


Đây là loại phổ biến nhất, được dùng mặc định trong `FixedThreadPool`. Nó có thể có giới hạn hoặc không.

* **Kịch bản:** Hệ thống xử lý đơn hàng. Khách hàng xếp hàng theo thứ tự, nhân viên xử lý dần dần.
* **Cách kết hợp:** Kết hợp với `corePoolSize` cố định để giữ hệ thống ổn định.

```java
    // Giới hạn 1000 đơn hàng trong hàng đợi để tránh tràn bộ nhớ (OOM)
    BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(1000);
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
        5, 10, 60L, TimeUnit.SECONDS, queue
    );
```
### Cơ chế Ngắt (Interrupt) trong BlockingQueue

Hiểu cách luồng "thức dậy" khi đang đứng chờ ở hàng đợi là yếu tố sống còn để quản lý tài nguyên hệ thống một cách bền vững.

#### 1. Tại sao `put()` và `take()` lại quan trọng?

Các phương thức cốt lõi của `BlockingQueue` như `put()`, `take()`, và `poll(timeout)` được thiết kế để **nhạy cảm với ngắt** (Interrupt-sensitive).

* **Cơ chế:** Nếu một luồng đang bị "treo" tại các phương thức này (vì hàng đợi đầy hoặc trống) và có một luồng khác gọi `.interrupt()`, Java sẽ ngay lập tức ném ra `InterruptedException`.
* **Lợi ích:** Điều này giúp luồng thoát khỏi trạng thái chờ ngay lập tức thay vì bị kẹt vĩnh viễn, cho phép hệ thống dọn dẹp và đóng lại an toàn.

#### 2. Cách xử lý InterruptedException đúng chuẩn

Trong dự án thực tế, tuyệt đối không bao giờ để trống khối `catch (InterruptedException e)`. Bạn có hai chiến thuật chính:

##### Chiến thuật A: Dừng luồng ngay lập tức
Phù hợp cho các tác vụ worker đơn giản. Ghi log, dọn dẹp tài nguyên và thoát khỏi vòng lặp xử lý.

##### Chiến thuật B: Khôi phục trạng thái ngắt
Sử dụng khi bạn muốn báo hiệu cho các phương thức cấp cao hơn rằng ngắt đã xảy ra mà không muốn nuốt mất tín hiệu đó.

```java
    try {
        String task = queue.take();
    } catch (InterruptedException e) {
        // Khôi phục trạng thái để các logic bên ngoài biết luồng đã bị ngắt
        Thread.currentThread().interrupt(); 
        log.error("Luồng xử lý bị ngắt quãng, đang dừng...");
    }
```

#### 3. `poll(timeout)` vs `take()`: Chọn "vũ khí" phù hợp

Việc lựa chọn phương thức lấy dữ liệu quyết định khả năng kiểm soát của bạn đối với vòng đời luồng:

| Phương thức | Hành vi | Khả năng Shutdown |
| :--- | :--- | :--- |
| **`take()`** | Đợi mãi mãi cho đến khi có dữ liệu hoặc bị ngắt. | Shutdown phụ thuộc hoàn toàn vào tín hiệu Interrupt bên ngoài. |
| **`poll(time, unit)`** | Đợi trong một khoảng thời gian, sau đó trả về `null` nếu không có gì. | Rất linh hoạt. Luồng có thể tự kiểm tra điều kiện `isInterrupted()` định kỳ. |

> **Mẹo nâng cao:** Sử dụng `poll(timeout)` giúp việc shutdown diễn ra mượt mà hơn trong các kịch bản phức tạp, vì luồng có cơ hội "tỉnh dậy" định kỳ để tự kiểm tra xem hệ thống có yêu cầu dừng hay không.


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="synchronous-queue">2. SynchronousQueue: "Chuyển phát nhanh - Trao tay ngay"</a>
<details>
<summary>Click for details</summary>


Loại này đặc biệt ở chỗ không có sức chứa. Nó không giống như một "nhà kho" để chứa đồ, mà giống như một "điểm giao dịch trực tiếp". Nó có dung lượng bằng 0. Một luồng đưa vào phải đợi đúng lúc có một luồng khác lấy ra thì mới giải phóng được.

* **Kịch bản:** Xác thực OTP. Yêu cầu phải được xử lý ngay lập tức, nếu luồng bận thì tạo luồng mới ngay (lên đến Max).
* **Cách kết hợp:** Đi kèm với `maximumPoolSize` rất lớn (như trong `CachedThreadPool`).

```java
    // Không có chỗ chứa, task đi thẳng từ luồng gửi sang luồng nhận
    BlockingQueue<Runnable> queue = new SynchronousQueue<>();
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
        0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, queue
    );
```

### 1. Tại sao lại dùng SynchronousQueue?

Trong dự án `java-learning`, loại queue này là "trái tim" của `Executors.newCachedThreadPool()` nhờ các đặc tính:

* **Tốc độ cực nhanh:** Dữ liệu đi thẳng từ luồng này sang luồng kia mà không tốn chi phí lưu trữ vào bộ nhớ đệm (Buffer), giúp giảm tối đa độ trễ (**Latency**).
* **Cơ chế "Bàn giao an toàn":** Người sản xuất (**Producer**) chỉ tiếp tục làm việc sau khi chắc chắn Người tiêu dùng (**Consumer**) đã nhận được hàng.
* **Tự động mở rộng luồng:** Khi đẩy một task vào, nếu không có Thread nào đang rảnh để `take()`, hệ thống bị ép buộc phải tạo một Thread mới ngay lập tức (lên đến `maxPoolSize`).

### 2. So sánh với LinkedBlockingQueue

Để dễ hình dung, hãy tưởng tượng về dịch vụ giao hàng:

| Đặc điểm | LinkedBlockingQueue(1) | SynchronousQueue |
| :--- | :--- | :--- |
| **Dung lượng** | Có ít nhất 1 phần tử (Hộp thư) | **0 phần tử** (Giao tận tay) |
| **Hành vi** | Shipper bỏ hàng vào hộp thư rồi đi về. | Shipper phải đợi bạn mở cửa mới về. |
| **Ứng dụng** | Khi muốn xử lý dần dần (**Buffering**). | Khi muốn xử lý ngay lập tức (**Direct Handoff**). |

### 3. Cơ chế ngắt (Interruption) trong SynchronousQueue

Vì các thao tác `put()` và `take()` của `SynchronousQueue` luôn ở trạng thái "đứng đợi" đối tác, chúng cực kỳ nhạy cảm với tín hiệu ngắt:

* **Tránh treo luồng:** Nếu luồng Shipper đang đợi khách mà bạn gọi `shipper.interrupt()`, nó sẽ ném ra `InterruptedException` ngay lập tức.
* **Tầm quan trọng:** Điều này giúp hệ thống thu hồi tài nguyên cực nhanh, không để các luồng bị kẹt vĩnh viễn khi không có đối tác "giao dịch".

### 4. Kịch bản ứng dụng thực tế

`SynchronousQueue` ép hệ thống phải đối mặt với áp lực tải ngay lập tức:
* **Ưu điểm:** Phản hồi cực nhanh, không có task nào bị "ngâm" trong kho.
* **Nhược điểm:** Nếu số lượng task đổ về quá lớn và xử lý chậm, hệ thống sẽ tạo ra vô số luồng, dẫn đến cạn kiệt tài nguyên máy tính.

---


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="array-blocking-queue">3. ArrayBlockingQueue: "Nhà kho cố định"</a>
<details>
<summary>Click for details</summary>


Sử dụng mảng để lưu trữ nên dung lượng là bất biến sau khi khởi tạo.Hãy tưởng tượng nó như một "Băng chuyền sản xuất có độ dài cố định". Đây là một loại hàng đợi sử dụng mảng (array) để lưu trữ, vì vậy bạn phải xác định kích thước của nó ngay từ khi khởi tạo và không thể thay đổi.

* **Kịch bản:** Hệ thống High-frequency Trading hoặc ứng dụng cần hiệu năng cực cao và bộ nhớ ổn định. Dùng mảng giúp giảm rác (GC) hơn so với các nút liên kết.
* **Cách kết hợp:** Dùng khi muốn kiểm soát chặt chẽ tài nguyên, không cho phép hàng đợi phình to.

```java
    // Dung lượng cố định là 50, không bao giờ thay đổi
    BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(50);
```
### 1. Phân tích cơ chế hoạt động

* **Tính cố định (Bounded):** Khác với `LinkedBlockingQueue` (có thể vô hạn), `ArrayBlockingQueue` bắt buộc phải có kích thước cụ thể. Điều này giúp bảo vệ hệ thống khỏi lỗi **OutOfMemory (OOM)**.
* **Hiệu năng Cache:** Do sử dụng mảng liên tục trong bộ nhớ, nó tận dụng tốt bộ nhớ đệm L1/L2 của CPU và giảm thiểu áp lực cho bộ thu gom rác (Garbage Collector).
* **Cơ chế Công bằng (Fairness):** Bạn có thể cấu hình `new ArrayBlockingQueue<>(capacity, true)` để đảm bảo các luồng đang đợi được phục vụ theo đúng thứ tự (FIFO), tránh tình trạng một luồng bị "đói" (starvation).

### 2. So sánh: ArrayBlockingQueue vs. LinkedBlockingQueue

| Đặc điểm | ArrayBlockingQueue | LinkedBlockingQueue |
| :--- | :--- | :--- |
| **Cấu trúc dữ liệu** | Mảng (Array) cố định. | Danh sách liên kết (Linked List). |
| **Quản lý RAM** | Rất tốt, không bao giờ phình to. | Có thể gây OOM nếu không giới hạn. |
| **Độ trễ (Latency)** | Thấp và ổn định hơn. | Cao hơn do chi phí cấp phát Node. |
| **Kịch bản lý tưởng** | Hệ thống High-frequency, cần hiệu năng cao. | Ứng dụng thông thường, cần sự linh hoạt. |

### 3. Hành vi khi hàng đợi đầy

Khi sử dụng phương thức `put()`, nếu hàng đợi đạt giới hạn, luồng sản xuất sẽ bị **chặn (block)** hoàn toàn cho đến khi có chỗ trống. Đây là cơ chế tự điều tiết tải (Backpressure) cực kỳ hiệu quả trong lập trình Concurrent.

Trong `ArrayBlockingQueue`, việc lựa chọn method nào phụ thuộc hoàn toàn vào cách bạn muốn hệ thống ứng xử khi "nhà kho" đã đầy hoặc khi "nhà kho" đang trống.

### 4. Bảng phân loại các nhóm Method

| Nhóm | Method | Hành vi khi Đầy (Put) / Trống (Take) | Dùng khi nào? |
| :--- | :--- | :--- | :--- |
| **Ném ngoại lệ** | `add(e)`, `remove()`, `element()` | Ném lỗi ngay lập tức (`IllegalStateException`). | Khi việc tràn queue là một lỗi nghiêm trọng cần dừng hệ thống. |
| **Giá trị đặc biệt** | `offer(e)`, `poll()`, `peek()` | Trả về `false` hoặc `null` thay vì báo lỗi. | Khi bạn muốn hệ thống tiếp tục chạy và xử lý logic "thử lại sau". |
| **Chặn (Blocking)** | `put(e)`, `take()` | Treo luồng cho đến khi có chỗ trống hoặc có dữ liệu. | Dùng nhiều nhất trong Async, đảm bảo không mất dữ liệu. |
| **Thời gian chờ** | `offer(e, t, u)`, `poll(t, u)` | Đợi trong một thời gian, nếu vẫn đầy/trống thì bỏ qua. | Khi bạn không muốn luồng bị treo mãi mãi (tránh Deadlock). |

### 5. Giải thích chi tiết từng Method

#### A. Nhóm Thêm (Producer side)
* **`put(e)`:** Nếu hàng đợi đầy, luồng Producer sẽ đứng đợi "vô thời hạn". Dùng khi dữ liệu là cực kỳ quan trọng (ví dụ: Giao dịch tiền tệ) - **Không được phép làm mất**.
* **`offer(e, timeout, unit)`:** Thử đợi một lúc (ví dụ 500ms). Nếu sau 500ms vẫn đầy thì mới trả về `false`. Rất tốt để tránh việc hệ thống bị nghẽn cổ chai quá lâu.

#### B. Nhóm Lấy (Consumer side)
* **`take()`:** Nếu hàng đợi trống, luồng Consumer sẽ ngủ yên cho đến khi có dữ liệu mới. Đây là **"trái tim"** của các Worker Thread trong Thread Pool.
* **`poll(time, unit)`:** Đợi một khoảng thời gian để lấy dữ liệu. Dùng cái này trong vòng lặp `while(!isInterrupted)` là cách tốt nhất để **Graceful Shutdown**.

#### C. Method "Xả kho": `drainTo(Collection<? super E> c)`
* **Cơ chế:** Lấy toàn bộ (hoặc một số lượng nhất định) các phần tử trong Queue và chuyển sang một List khác chỉ bằng một lệnh duy nhất.
* **Tại sao nó quan trọng?** * **Hiệu năng:** Thay vì gọi `take()` 1000 lần (tốn 1000 lần lock/unlock), bạn gọi `drainTo` 1 lần để lấy cả 1000 phần tử.
    * **Ứng dụng:** Dùng trong các bài toán **Batch Processing** (Xử lý hàng loạt). Ví dụ: Gom 500 log rồi mới ghi vào Database một lần để tối ưu hóa IO.

### Tóm tắt chiến thuật chọn Method:
1.  **An toàn tuyệt đối?** $\rightarrow$ Dùng `put()` / `take()`.
2.  **Hệ thống không được treo cứng?** $\rightarrow$ Dùng `offer(timeout)` / `poll(timeout)`.
3.  **Xử lý dữ liệu lớn, nhanh?** $\rightarrow$ Dùng `drainTo()`.


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="priority-blocking-queue">4. PriorityBlockingQueue: "Hàng ưu tiên - VIP lên trước"</a>
<details>
<summary>Click for details</summary>


Các Task được lấy ra dựa trên độ ưu tiên (**Priority**) thay vì thứ tự thời gian (FIFO).

* **Kịch bản:** Hệ thống gửi thông báo. Thông báo "Cảnh báo bảo mật" phải được gửi trước thông báo "Khuyến mãi".
* **Cách kết hợp:** Các Task đẩy vào phải implement interface `Comparable`.

```java
    // Task nào có priority cao sẽ được đưa lên đầu hàng đợi
    BlockingQueue<Runnable> queue = new PriorityBlockingQueue<>();
```
## 1. Cơ chế hoạt động của PriorityBlockingQueue

* **Không giới hạn (Unbounded):** Nó không có dung lượng cố định, sẽ tự nở ra khi cần thiết (giống như `ArrayList`). Do đó, lệnh `put()` sẽ không bao giờ bị chặn.
* **Sắp xếp dựa trên so sánh:** Các phần tử đẩy vào phải triển khai Interface `Comparable` hoặc bạn phải cung cấp một `Comparator` khi khởi tạo.
* **Cấu trúc dữ liệu:** Bên dưới sử dụng cấu trúc **Binary Heap** để luôn giữ phần tử có độ ưu tiên cao nhất ở đầu hàng đợi (`Head`).

## 2. Khi nào nên dùng PriorityBlockingQueue?

* **Hệ thống phân tầng dịch vụ (Tiered Services):** Ưu tiên xử lý yêu cầu của khách hàng VIP/Premium trước khách hàng miễn phí.
* **Hệ thống lập lịch (Scheduling):** Các tác vụ có thời hạn hoàn thành (`Deadline`) gần nhất sẽ được ưu tiên chạy trước.
* **Xử lý sự kiện (Event Processing):** Các sự kiện lỗi hệ thống (`Critical Error`) phải được xử lý trước các log thông tin (`Info`).

## 3. Lưu ý "xương máu" cho Software Developer

1. **Vấn đề "Đói tài nguyên" (Starvation):** Nếu Task ưu tiên cao liên tục đổ về, Task ưu tiên thấp có thể **không bao giờ** được xử lý.
  * *Giải pháp:* Cơ chế **"Aging"** (tự động tăng độ ưu tiên cho các task đã chờ quá lâu).
2. **Thứ tự của các Task cùng độ ưu tiên:** `PriorityBlockingQueue` **không đảm bảo** thứ tự FIFO cho các phần tử có cùng mức ưu tiên. Nếu cần, bạn phải thêm một biến `sequenceNumber` để so sánh phụ.
3. **Bộ nhớ:** Vì nó là Unbounded, nếu Producer đẩy việc quá nhanh mà Consumer xử lý không kịp, hàng đợi sẽ phình to gây `OutOfMemoryError`.

---

## 4. Phân biệt peek(), poll(), và take() trong PriorityBlockingQueue

Trong `PriorityBlockingQueue`, phần tử ở "đầu" (**Head**) luôn là phần tử có **độ ưu tiên cao nhất**, không nhất thiết là phần tử được đưa vào đầu tiên. Việc lựa chọn phương thức nào để tiếp cận phần tử này sẽ quyết định hành vi của luồng xử lý.

### 1. `peek()`: "Chỉ nhìn, không lấy"

* **Hành vi:** Trả về phần tử có độ ưu tiên cao nhất nhưng **vẫn giữ nó lại** trong hàng đợi.
* **Khi nào dùng:** Khi bạn muốn kiểm tra xem Task quan trọng nhất hiện tại là gì mà chưa muốn thực hiện nó ngay.
* **Nếu Queue trống:** Trả về `null`.
* **Tính chất:** Không chặn luồng (Non-blocking).

### 2. `poll()`: "Lấy ra ngay, nếu không có thì thôi"

* **Hành vi:** Lấy ra và **xóa bỏ** phần tử có độ ưu tiên cao nhất khỏi hàng đợi.
* **Khi nào dùng:** Khi bạn muốn luồng xử lý thực hiện công việc ngay, nhưng nếu không có việc thì luồng có thể đi làm việc khác hoặc kết thúc, thay vì đứng đợi.
* **Nếu Queue trống:** Trả về `null` ngay lập tức.
* **Biến thể:** `poll(timeout, unit)` – Đợi trong một khoảng thời gian nhất định, nếu vẫn không có phần tử nào thì mới trả về `null`.

### 3. `take()`: "Chờ bằng được mới thôi"

* **Hành vi:** Lấy ra và xóa bỏ phần tử có độ ưu tiên cao nhất.
* **Khi nào dùng:** Đây là phương thức đặc trưng của `BlockingQueue`. Dùng khi bạn muốn luồng **Consumer** luôn sẵn sàng; nếu Queue trống, luồng sẽ "ngủ yên" (**Blocked**) cho đến khi có dữ liệu mới.
* **Nếu Queue trống:** Luồng bị treo (Block) cho đến khi có phần tử xuất hiện.
* **Lưu ý:** Cần xử lý `InterruptedException` vì luồng có thể bị ngắt khi đang đứng đợi.

### Bảng so sánh nhanh

| Đặc điểm | `peek()` | `poll()` | `take()` |
| :--- | :--- | :--- | :--- |
| **Loại bỏ phần tử?** | Không | Có | Có |
| **Nếu Queue trống?** | Trả về `null` | Trả về `null` | **Đứng đợi (Blocked)** |
| **Tính chất chặn?** | Non-blocking | Non-blocking | **Blocking** |
| **Kịch bản lý tưởng** | Kiểm tra điều kiện trước khi làm. | Xử lý nếu có sẵn, không thì bỏ qua. | Duy trì Worker luôn chạy ngầm. |

</details>

- [Quay lại đầu trang](#back-to-top)