<a id="back-to-top"></a>

# Coordination - Điều phối

## Menu
- [1. Phân biệt Synchronization (Đồng bộ) và Coordination (Điều phối)](#coordination)
- [2. Điều phối luồng cơ bản: join(), wait(), notify() và notifyAll()](#coordination-classic)



## <a id="coordination">1. Phân biệt Synchronization (Đồng bộ) và Coordination (Điều phối)</a>
<details>
<summary>Click for details</summary>


Trong lập trình đa luồng, hai khái niệm này thường đi đôi nhưng mục đích của chúng hoàn toàn khác nhau. Việc hiểu rõ sự khác biệt này sẽ giúp bạn tổ chức code trong dự án `java-learning` một cách logic và chuyên nghiệp.

Để dễ hiểu nhất, hãy tưởng tượng một **Đội cứu hỏa**:
* **Synchronization (Tranh chấp):** Đảm bảo tại một thời điểm, chỉ có một người được cầm vòi phun nước. Nó tập trung vào việc **bảo vệ tài nguyên**.
* **Coordination (Hợp tác):** Đảm bảo rằng người cầm vòi chỉ phun nước *sau khi* người ở xe cứu hỏa đã bật van bơm. Nó tập trung vào việc **phối hợp thứ tự**.

---

### 1. Synchronization (Bảo vệ tài nguyên)
Mục tiêu chính là **Mutual Exclusion** (Loại trừ lẫn nhau). Nó giải quyết vấn đề: *"Làm sao để các luồng không dẫm chân lên nhau khi cùng sửa một biến?"*

* **Trạng thái:** Các luồng đối đầu nhau (Competitive).
* **Công cụ:** `synchronized`, `ReentrantLock`, `ReadWriteLock`, `StampedLock`.
* **Vấn đề giải quyết:** Race Condition, Data Inconsistency.

---

### 2. Coordination (Phối hợp thứ tự)
Mục tiêu chính là **Signaling** (Báo hiệu). Nó giải quyết vấn đề: *"Làm sao để luồng A đợi luồng B làm xong rồi mới chạy tiếp?"*

* **Trạng thái:** Các luồng hợp tác với nhau (Cooperative).
* **Công cụ:** `wait()` / `notify()`, `CountDownLatch`, `CyclicBarrier`, `Semaphore`, `Phaser`.
* **Vấn đề giải quyết:** Thread Dependency, Execution Order (Thứ tự thực thi).

---

### 3. Bảng so sánh chi tiết

| Đặc điểm | Synchronization | Coordination |
| :--- | :--- | :--- |
| **Câu hỏi chính** | Ai được quyền truy cập bây giờ? | Khi nào tôi có thể bắt đầu làm? |
| **Mối quan hệ** | Luồng này chặn luồng kia. | Luồng này đợi luồng kia báo hiệu. |
| **Từ khóa chính** | Khóa (Locking), Độc quyền (Exclusive). | Chờ (Waiting), Tín hiệu (Signaling). |
| **Ví dụ thực tế** | Hai người cùng rút tiền từ một ATM. | Bếp nấu xong thì phục vụ mới bưng món. |

---

### 4. Tại sao chúng thường đi cùng nhau?
Trong thực tế, **Coordination** thường dựa trên nền tảng của **Synchronization** để hoạt động an toàn.

**Ví dụ điển hình:** Để dùng `wait()` và `notify()`, bạn bắt buộc phải nằm trong khối `synchronized`.
1.  Bạn dùng `synchronized` để lấy quyền kiểm soát "bộ đàm" (**Synchronization**).
2.  Sau đó bạn dùng `wait()` để thông báo: *"Tôi đang đợi cơm, ai nấu xong thì gọi nhé"* (**Coordination**).

---

### Tổng kết cho dự án `java-learning`
* Nếu bạn muốn bảo vệ một biến `count` hoặc một `ArrayList`: Hãy tìm đến **Synchronization**.
* Nếu bạn muốn luồng chính đợi 10 luồng con xử lý xong dữ liệu rồi mới tổng hợp kết quả: Hãy tìm đến **Coordination** (ví dụ: `CountDownLatch`).


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="coordination-classic">2. Điều phối luồng cơ bản: join(), wait(), notify() và notifyAll()</a>
<details>
<summary>Click for details</summary>


Trong hệ thống **Coordination (Điều phối)**, các phương thức này đóng vai trò là những "tín hiệu giao thông" cơ bản nhất để thiết lập thứ tự thực thi giữa các luồng. Việc nắm vững chúng là nền tảng để hiểu các bộ điều phối cao cấp hơn trong Java.

---

### 1. join(): Vai trò là "Rào chắn kết thúc" (Termination Barrier)
`join()` là cách đơn giản nhất để một luồng phối hợp với vòng đời của một luồng khác.

* **Vai trò:** Cho phép luồng hiện tại (thường là luồng cha) tạm dừng và chờ cho đến khi một luồng cụ thể khác (luồng con) kết thúc hoàn toàn công việc của nó.
* **Ứng dụng:** Dùng khi kết quả của bước tiếp theo phụ thuộc hoàn toàn vào việc bước trước đó phải chạy xong.
* **Hình ảnh minh họa:** Bạn đợi shipper giao hàng xong rồi mới có thể khui quà. Bạn không thể khui quà khi shipper vẫn đang trên đường đi.

---

### 2. wait(): Vai trò là "Trạng thái chờ điều kiện" (Condition Waiting)
`wait()` cho phép một luồng tự đưa mình vào trạng thái "ngủ đông" để nhường tài nguyên cho luồng khác.

* **Vai trò:** Tạm thời giải phóng khóa (**Monitor Lock**) mà luồng đang giữ và đứng vào danh sách chờ (**Wait-set**). Luồng sẽ không làm gì cả cho đến khi điều kiện logic mà nó mong đợi được thỏa mãn.
* **Ứng dụng:** Dùng trong mô hình Producer-Consumer. Khi kho hàng trống, luồng Consumer gọi `wait()` để không chiếm dụng CPU vô ích.
* **Hình ảnh minh họa:** Bạn vào quán ăn nhưng hết bàn, bạn đứng sang một bên đợi (nhường lối đi cho khách khác ra về) cho đến khi nhân viên gọi tên bạn.

---

### 3. notify() và notifyAll(): Vai trò là "Bộ phát tín hiệu" (Signaling)
Đây là những lệnh "đánh thức" các luồng đang nằm trong trạng thái `wait()`.

* **`notify()`:** Đánh thức một luồng ngẫu nhiên đang đợi trên đối tượng đó. Nó giống như việc nhân viên quán ăn chỉ gọi duy nhất một người đang đợi vào bàn.
* **`notifyAll()`:** Đánh thức tất cả các luồng đang đợi. Đây là cách điều phối an toàn hơn, tránh việc đánh thức nhầm hoặc bỏ sót luồng. Sau khi thức dậy, các luồng sẽ tranh chấp khóa để kiểm tra lại điều kiện.
* **Vai trò:** Thông báo rằng: *"Tình trạng tài nguyên đã thay đổi, các anh hãy tỉnh dậy và kiểm tra lại đi!"*.
* **Hình ảnh minh họa:** Khi có bàn trống, nhân viên hét lớn: *"Có bàn rồi!"* để tất cả những người đang đứng đợi biết và tiến vào.

---

### Tóm tắt mối quan hệ phối hợp

| Phương thức | Luồng gọi nó sẽ làm gì? | Mục đích phối hợp |
| :--- | :--- | :--- |
| **`join()`** | Bị chặn (Blocked) | Đợi một luồng khác kết thúc hoàn toàn. |
| **`wait()`** | Giải phóng khóa & Ngủ | Đợi một tín hiệu/điều kiện từ luồng khác. |
| **`notify(All)`** | Tiếp tục chạy | Gửi tín hiệu để đánh thức luồng khác dậy. |

---

### Ví dụ phối hợp thực tế (The Sequence)

1.  **Main** tạo ra một luồng **Worker**.
2.  **Worker** bắt đầu chạy nhưng ngay lập tức gọi `wait()` vì chưa có dữ liệu đầu vào từ Main.
3.  **Main** chuẩn bị dữ liệu xong, gọi `notify()` để "đánh thức" **Worker**.
4.  **Main** tiếp tục gọi `Worker.join()` để đảm bảo **Worker** xử lý xong xuôi dữ liệu đó thì **Main** mới in báo cáo cuối cùng ra màn hình.

> **Lưu ý quan trọng:** Bạn chỉ có thể gọi `wait()` và `notify()` khi đang giữ khóa của đối tượng đó (nằm trong khối `synchronized`), nếu không Java sẽ ném ra `IllegalMonitorStateException`.

</details>

- [Quay lại đầu trang](#back-to-top)