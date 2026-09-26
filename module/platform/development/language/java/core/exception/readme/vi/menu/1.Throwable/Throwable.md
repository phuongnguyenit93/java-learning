# Throwable

Trong luồng thực thi bình thường, ta giả định một method sẽ hoàn thành công việc rồi trả kết quả. Nhưng chương trình thực tế luôn có khả năng **không thể tiếp tục theo đường thành công**: dữ liệu sai, file không tồn tại, kết nối lỗi, tài nguyên đóng thất bại, hoặc một invariant bị phá.

Java dùng exception để biểu diễn **sự kết thúc bất thường của luồng thực thi** và truyền thông tin lỗi qua nhiều tầng lời gọi mà không buộc mọi method phải trả về một “mã lỗi” riêng.

Lộ trình của module:

```text
Java biểu diễn lỗi như thế nào?
Throwable
        ↓
Lỗi nào phải xuất hiện trong hợp đồng ở compile time?
Checked vs Unchecked
        ↓
Làm sao ném và khai báo lỗi?
throw / throws
        ↓
Nếu không xử lý tại đây, exception đi đâu?
Propagation
        ↓
Xử lý lỗi và dọn dẹp bằng cách nào?
try / catch / finally
        ↓
Tài nguyên được đóng an toàn ra sao?
try-with-resources
        ↓
Nếu body và close đều fail thì giữ lỗi nào?
Suppressed Exception
        ↓
Khi nào cần exception riêng cho domain hoặc ứng dụng?
Custom Exception
        ↓
Nên bắt, chuyển đổi, ghi log, khôi phục hay truyền tiếp ở đâu?
Exception Design
```

## <a id="throwable-hierarchy">Hệ thống Throwable</a>

### KHÁI NIỆM

`Throwable` là kiểu gốc của cơ chế exception trong Java. Hai nhánh lớn là:

```text
Throwable
├── Error
└── Exception
    └── RuntimeException
```

Chỉ đối tượng thuộc hệ phân cấp `Throwable` mới được dùng trực tiếp với cơ chế `throw`/`catch`.

`RuntimeException` là nhánh unchecked phổ biến trong mã ứng dụng; các exception khác dưới `Exception` thường là checked exception nếu không thuộc nhánh này.

### MÔ HÌNH TƯ DUY

Đừng xem exception chỉ là “một object lỗi”. Nó đồng thời mang hai vai trò:

1. **thay đổi luồng điều khiển** — luồng thực thi rời khỏi đường chạy bình thường;
2. **mang thông tin chẩn đoán** — type, message, cause và stack trace.

## <a id="error-vs-exception">Error và Exception</a>

`Error` thường đại diện cho các điều kiện nghiêm trọng liên quan tới JVM, môi trường chạy, linkage hoặc assertion mà mã ứng dụng không nên cố khôi phục theo cách chung chung.

`Exception` thường đại diện cho lỗi mà ứng dụng/API có thể mô hình hóa, xử lý, chuyển đổi hoặc truyền tiếp.

Điều này không có nghĩa “Error không bao giờ catch được về mặt cú pháp”; vấn đề là **ý nghĩa thiết kế**. Catch `Throwable` quá rộng có thể vô tình nuốt cả các lỗi mà ứng dụng không nên tiếp tục như bình thường.

## <a id="stack-trace-cause">Stack Trace và Cause</a>

Một `Throwable` thường mang:

- stack trace — chuỗi call frame gần thời điểm exception được tạo hoặc ném;
- message — mô tả lỗi;
- cause — throwable gốc hoặc tầng thấp hơn dẫn tới lỗi hiện tại.

Khi bọc một exception bằng exception khác, nên giữ exception gốc làm `cause`:

```java
throw new OrderException("Cannot load order", original);
```

Nhờ đó exception ở tầng cao có thể dùng ngôn ngữ phù hợp với tầng hiện tại, nhưng chuỗi chẩn đoán vẫn dẫn về nguyên nhân gốc.

Stack trace là dữ liệu chẩn đoán, không phải một API ổn định để logic ứng dụng phân tích cú pháp.

Chương tiếp theo phân biệt **checked** và **unchecked**: khi nào compiler buộc bên gọi phải xử lý hoặc khai báo lỗi?
