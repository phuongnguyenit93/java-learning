# Các câu lệnh trong Arthas

## <a id="arthas-command">Arthas Cheat Sheet: Những "Vũ khí" Chẩn đoán Lợi hại</a>

Khi đã truy cập vào Web Console, hãy sử dụng các nhóm lệnh sau để bắt bệnh cho Microservices của bạn.



## 1. Nhóm "Soi" Tổng Quan (Inspection)
Trước khi đi sâu vào code, bạn cần biết hệ thống đang "thở" như thế nào.

* **`dashboard`:** Xem toàn bộ thông số thời gian thực (CPU, RAM, Thread, Runtime).
    * *Mẹo:* Kiểm tra ngay nếu có Thread nào đang chiếm gần 100% CPU.
* **`thread -b`:** Tìm ngay lập tức các **Thread đang bị Deadlock** (khóa lẫn nhau). Đây là cứu cánh khi App bị treo mà không rõ lý do.
* **`jvm`:** Xem mọi thông tin về máy ảo Java (Tham số `-Xmx`, `-Xms`, biến môi trường...).

## 2. Nhóm "Soi" Class & Metadata
Giúp bạn giải quyết các thắc mắc về ClassLoader hoặc các lớp Proxy được tạo ra bởi Spring.

* **`sc -d *ClassName*`:** (Search Class) Tìm xem Class đó đã được load chưa, thuộc ClassLoader nào.
* **`sm -d ClassName MethodName`:** (Search Method) Xem chi tiết các phương thức của một class.
* **`jad ClassName`:** **Decompile** ngược từ RAM ra mã nguồn. Cực kỳ hữu ích để kiểm tra xem bản code đang chạy trên Server đã là bản mới nhất chưa.



## 3. Nhóm "Bắt Quả Tang" (Diagnostics) - Quan trọng nhất
Dùng để debug luồng dữ liệu mà không cần đặt Breakpoint (vốn làm treo cả App).

* **`watch [Class] [Method] "{params, returnObj, throwExp}" -n 5`:**
    * **Tác dụng:** Xem giá trị tham số đầu vào (`params`) và kết quả trả về (`returnObj`) khi có người gọi hàm.
    * **Ví dụ:** `watch com.example.service.OrderService create "{params, returnObj}"` - Bạn sẽ thấy ngay dữ liệu đơn hàng là gì mà không cần in Log.
* **`trace [Class] [Method]`:**
    * **Tác dụng:** Đo thời gian thực thi của từng dòng code bên trong hàm.
    * **Ứng dụng:** Tìm "thủ phạm" gây chậm (SQL, Validation hay gọi API bên thứ ba?).
* **`stack [Class] [Method]`:**
    * **Tác dụng:** Xem **Stack Trace** (Ai là người đã gọi hàm này?). Cực kỳ tốt để hiểu luồng Proxy của Spring.



## 4. Nhóm "Thay đổi Thế giới" (Hot Swap)

* **`logger --name ROOT --level debug`:** Thay đổi Log Level sang **DEBUG** ngay lập tức mà không cần restart app.
* **`vmtool`:** Lấy một Instance của Bean từ RAM và ép nó thực thi một hàm bất kỳ để kiểm tra kết quả ngay lập tức.

---

## 🛠️ Kịch bản thực tế: Truy tìm nguyên nhân hàm trả về NULL

Giả sử `inventoryRepository.findById()` trả về `null` một cách vô lý, bạn hãy thực hiện:
1.  Dùng **`stack`** để xem Service nào đang gọi nó.
2.  Dùng **`watch`** để xem tham số ID truyền vào có thực sự đúng định dạng không.
3.  Nếu tham số đúng mà vẫn `null`, dùng **`trace`** để xem Hibernate mất bao lâu để query, có thể do Timeout hoặc lỗi kết nối ngầm định.

---

### 💡 Lưu ý cuối cùng:
Sau khi dùng xong trên Web Console, hãy luôn ghi nhớ gõ lệnh **`stop`**. Lệnh này sẽ yêu cầu Arthas dọn dẹp sạch sẽ các Agent (đã được cấy vào Bytecode) để trả lại hiệu năng ban đầu cho JVM, giúp ứng dụng chạy nhẹ nhàng trở lại.