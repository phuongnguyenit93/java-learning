# Suppressed Exception

Việc dọn dẹp cũng có thể thất bại. Nếu phần thân của `try-with-resources` đã throw một exception rồi `close()` lại throw thêm exception khác, Java cần giữ cả hai mà không làm mất lỗi chính.

## <a id="primary-vs-suppressed">Primary và Suppressed Exception</a>

Trong `try-with-resources`:

```text
body throw exception A
        ↓
close() throw exception B
```

thì A thường được giữ làm **primary exception**, còn B được gắn vào A dưới dạng **suppressed exception**.

Điều này tránh việc lỗi khi dọn dẹp che mất lỗi gốc đã khiến thao tác thất bại ngay từ đầu.

## <a id="get-suppressed">Đọc Suppressed Exception</a>

Có thể truy cập các suppressed throwable bằng:

```java
Throwable[] suppressed = ex.getSuppressed();
```

Thông tin này hữu ích khi chẩn đoán quá trình dọn dẹp tài nguyên phức tạp, đặc biệt khi cả thao tác chính và `close()` đều thất bại.

Thông thường ứng dụng logic không nên phụ thuộc sâu vào số lượng suppressed exception; đây chủ yếu là chẩn đoán ngữ cảnh.

## <a id="close-failure">Lỗi khi Close</a>

Nếu chỉ `close()` fail và body không fail, close exception trở thành exception chính.

Nếu body đã fail trước, close lỗi được suppressed để giữ lỗi chính.

```text
body thành công + close lỗi
→ lỗi khi close là primary

body lỗi + close lỗi
→ lỗi của body là primary
→ lỗi khi close bị suppressed
```

Chương tiếp theo chuyển từ cơ chế runtime sang thiết kế: **khi nào nên tạo kiểu exception riêng cho ứng dụng hoặc domain?**
