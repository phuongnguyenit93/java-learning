<a id="back-to-top"></a>

# Interrupt trong Thread : Ngăn cản luồng

## Menu
- [Cơ chế Interrupt trong Java: Nghệ thuật dừng Luồng lịch sự](#interrupt-basic)
- [Những quy tắc "Vàng" về Interrupt trong Java](#interrupt-rules)


## <a id="interrupt-basic">Cơ chế Interrupt trong Java: Nghệ thuật dừng Luồng lịch sự</a>
<details>
<summary>Click for details</summary>


Trong Java, chúng ta không "giết" một luồng một cách thô bạo. Thay vào đó, chúng ta sử dụng cơ chế **Interrupt** để yêu cầu luồng dừng lại một cách có kiểm soát và an toàn.

---

## 1. Interrupt là gì? Tại sao nó cần thiết?

**Interrupt** là một cơ chế báo hiệu (Signaling). Nó không dừng luồng ngay lập tức mà chỉ thiết lập một "cờ hiệu" (internal flag) thành `true`.

### Tại sao không dùng `stop()`?
* **An toàn dữ liệu:** Phương thức `stop()` cũ đã bị loại bỏ vì nó ngắt luồng đột ngột, có thể gây hỏng dữ liệu hoặc tạo ra lỗi **Deadlock**.
* **Tính hợp tác (Cooperative):** Interrupt cho phép luồng có thời gian để **dọn dẹp tài nguyên** (đóng kết nối Database, đóng file) trước khi kết thúc.
* **Đánh thức luồng:** Đây là cách duy nhất để "đánh thức" một luồng đang ngủ (`sleep`) hoặc đang chờ (`wait`).

---

## 2. Phân biệt bộ ba phương thức Interrupt

Để điều khiển cờ ngắt, Java cung cấp 3 phương thức với vai trò hoàn toàn khác nhau:

| Phương thức | Loại | Mục đích | Tác động đến cờ ngắt |
| :--- | :--- | :--- | :--- |
| **`interrupt()`** | Instance | **Người gửi:** Ra lệnh ngắt một luồng nào đó. | Đổi `false` -> `true`. |
| **`isInterrupted()`** | Instance | **Người nhận:** Kiểm tra xem mình có bị ngắt không. | **Không thay đổi** (Giữ nguyên `true`). |
| **`Thread.interrupted()`** | **Static** | **Người nhận:** Kiểm tra và **Reset** cờ. | Đổi `true` -> `false`. |

> **💡 Mẹo nhớ:** `interrupted()` (có chữ 'ed') giống như một câu hỏi: *"Bạn đã bị ngắt chưa? Nếu rồi thì xóa đi (reset) để tôi làm việc tiếp."*

---

## 3. Khi nào cần dùng `Thread.interrupted()` (Xóa cờ)?

Thông thường ta dùng `isInterrupted()` để giữ trạng thái ngắt cho đến khi luồng dừng hẳn. Tuy nhiên, việc xóa cờ ngắt là cần thiết khi:

1.  **Tái sử dụng luồng (Thread Reusability):** Trong các Thread Pool, một luồng có thể thực hiện nhiều Task liên tiếp. Sau khi một Task bị ngắt và dọn dẹp xong, bạn cần xóa cờ để luồng có thể nhận Task tiếp theo một cách bình thường.
2.  **Ngăn chặn lặp lỗi:** Một số thư viện sẽ liên tục ném Exception nếu thấy cờ ngắt vẫn là `true`. Bạn xóa cờ sau khi đã xử lý xong để đưa hệ thống về trạng thái ổn định.

---

## 4. Xử lý `InterruptedException` chuẩn (Best Practice)

Khi một luồng đang `sleep()`, `wait()`, hoặc `join()` mà bị ngắt, Java sẽ ném ra `InterruptedException`.

**⚠️ Lưu ý quan trọng:** Khi ngoại lệ này được ném ra, Java sẽ **tự động xóa cờ ngắt** (reset về `false`).

### Cách xử lý đúng:
* **Trường hợp 1: Dừng hẳn luồng.**
    ```java
    try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        // Dọn dẹp tài nguyên và kết thúc
        return; 
    }
    ```
* **Trường hợp 2: Muốn báo cáo trạng thái ngắt lên tầng trên.**
    ```java
    try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        // QUAN TRỌNG: Bật lại cờ ngắt vì Java đã tự động xóa nó
        Thread.currentThread().interrupt(); 
        log.warn("Thread was interrupted, propagating status...");
    }
    ```
---


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="interrupt-rules">Những quy tắc "Vàng" về Interrupt trong Java</a>
<details>
<summary>Click for details</summary>


Để trở thành một lập trình viên Java Backend chuyên nghiệp, bạn cần ghi nhớ 3 quy tắc cốt lõi sau đây khi xử lý đa luồng.

---

## A. 🛑 Đừng bao giờ "nuốt" InterruptedException

Khi bạn viết một khối `catch` rỗng cho `InterruptedException`, bạn đang thực hiện hành vi "nuốt" tín hiệu báo động. Điều này khiến các hệ thống quản lý luồng cấp cao (như Thread Pool) không biết rằng luồng đã bị yêu cầu dừng lại.

**Cách xử lý đúng:**
1.  **Ném ngược lại:** Để phương thức gọi nó xử lý.
2.  **Khôi phục trạng thái:** Nếu không thể ném tiếp, hãy gọi `Thread.currentThread().interrupt()`.

    ```java
    try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        // Bảo toàn trạng thái ngắt cho các logic phía sau
        Thread.currentThread().interrupt(); 
        log.error("Thread interrupted, state restored.");
    }
    ```

---

## B. 🧱 Giới hạn: I/O truyền thống và Synchronized

Lệnh `interrupt()` không phải là "vạn năng". Nó sẽ **không có tác dụng** nếu luồng đang bị kẹt ở các trạng thái sau:
1.  **I/O cũ (Blocking I/O):** Các hàm như `inputStream.read()`.
2.  **Synchronized:** Luồng đang đứng đợi ở cửa khối `synchronized` để lấy khóa.

**Giải pháp:**
* Với I/O hiện đại: Hãy sử dụng **Java NIO** (`InterruptibleChannel`). Khi bị ngắt, nó sẽ ném ra `ClosedByInterruptException` và đóng channel ngay lập tức.
* Với Lock: Sử dụng `ReentrantLock` với phương thức `lockInterruptibly()` thay cho `synchronized`.

---

## C. ⚡ Interrupt vs Biến boolean `stop` tự chế

Nhiều người có thói quen dùng một biến `volatile boolean stop = false` để dừng vòng lặp. Tuy nhiên, biến này có một nhược điểm chí tử so với `interrupt()`:

> **Biến boolean không thể "đánh thức" một luồng đang ngủ.**

* **Với biến boolean:** Nếu luồng đang gọi `Thread.sleep(100000)`, nó sẽ ngủ hết 100 giây rồi mới thức dậy kiểm tra biến `stop`.
* **Với `interrupt()`:** Nó sẽ tạo ra một cú hích (`InterruptedException`) đánh thức luồng ngay lập tức để xử lý việc dừng lại.

---

## 🎯 Tổng kết quy tắc thực chiến

1.  **Luôn ưu tiên `interrupt()`** vì nó có khả năng đánh thức luồng.
2.  **Luôn khôi phục trạng thái ngắt** trong khối `catch`.
3.  **Sử dụng NIO và Lock hiện đại** nếu cần khả năng ngắt trong lúc chờ I/O hoặc khóa.

---

## 🎯 Tổng kết

> "Interrupt là một lời đề nghị dừng lại. Luồng nhận có thể đồng ý dừng ngay (với `InterruptedException`) hoặc làm nốt việc rồi mới nghỉ (với `isInterrupted`), nhưng tuyệt đối **không nên phớt lờ nó**."

---

</details>

- [Quay lại đầu trang](#back-to-top)