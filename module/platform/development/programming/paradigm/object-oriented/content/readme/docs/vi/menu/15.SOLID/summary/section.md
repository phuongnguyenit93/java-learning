# SOLID — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Mục đích và phạm vi áp dụng SOLID
Đặt SOLID vào mục tiêu giảm tác động thay đổi và kiểm thử qua SolidScopeDemo; phân biệt năm nguyên tắc với các vấn đề như transaction, security không được giải quyết tự động.

↓ **Từ mục tiêu SOLID → đào sâu cách xác định một nhóm trách nhiệm và lý do thay đổi**

### Bài 2 — Trách nhiệm đơn: một nhóm lý do thay đổi
SRP nhóm các quy tắc vòng đời và tổng tiền trong Invoice, trong khi formatter, repository và sender nhận các nhóm lý do thay đổi riêng; InvoiceWorkflow điều phối use case.

↓ **Từ trách nhiệm đã rõ → đặt điểm mở rộng cho biến thể mà không liên tục sửa lõi**

### Bài 3 — Đóng-mở: mở rộng hành vi với thay đổi có kiểm soát
OCP đặt trục thay đổi ở ShippingPolicy: thêm Standard, Express, SameDay và decorator phụ phí mà ShippingService giữ lời gọi feeFor, với registration ở composition root.

↓ **Từ mở rộng nhiều implementation → bảo đảm mỗi loại giữ đúng hợp đồng**

### Bài 4 — Thay thế Liskov: bảo toàn hợp đồng của kiểu cha
LSP yêu cầu mọi DiscountPolicy giữ precondition, postcondition, invariant và lỗi; BadVipDiscount và BadFixedDiscount là hai vi phạm khác nhau dù implements hợp lệ.

↓ **Từ hợp đồng thay thế → thu hẹp nghĩa vụ theo nhu cầu riêng của từng client**

### Bài 5 — Phân tách giao diện: hợp đồng phù hợp từng bên sử dụng
ISP tách DocumentReader, DocumentWriter, DocumentDeleter đúng client; kho chỉ đọc không cần giả vờ hỗ trợ ghi/xóa, còn một implementation có thể thực hiện nhiều vai trò.

↓ **Từ client chỉ cần hợp đồng phù hợp → điều chỉnh hướng phụ thuộc giữa nghiệp vụ và hạ tầng**

### Bài 6 — Đảo ngược phụ thuộc: phụ thuộc vào sự trừu tượng
DIP giữ CustomerSpendingService phụ thuộc PurchaseReader, còn in-memory/JDBC adapter triển khai port; dịch vụ giữ quy tắc tổng mua hàng độc lập chi tiết SQL.

↓ **Từ đảo hướng phụ thuộc → phân biệt nguyên lý kiến trúc với kỹ thuật lắp ghép object**

### Bài 7 — Phân biệt đảo ngược phụ thuộc và tiêm phụ thuộc
So sánh DIP (hướng source dependency), DI (cấp dependency) và IoC (quyền điều khiển rộng hơn) bằng PaymentGateway, CheckoutService và composition root/Spring.

↓ **Từ hiểu năm nguyên tắc và DI → cân nhắc chi phí abstraction trong tình huống thực tế**

### Bài 8 — Cân bằng các nguyên tắc với độ phức tạp thực tế
Đánh giá KISS/YAGNI/DRY và change surface: từ SimpleCheckout một rule đến DeliveryFeePolicy khi Standard/Express thay đổi riêng, giữ kiểm thử và tránh overengineering.

**Mối liên hệ:** Bài đầu tạo bản đồ năm nguyên tắc, sau đó lần lượt đi sâu vào SRP để xác định ranh giới trách nhiệm, OCP để mở rộng có kiểm soát, LSP để bảo vệ tính thay thế và ISP để giữ interface trung thực với client. DIP sắp lại hướng phụ thuộc, bài kế tiếp làm rõ DI là kỹ thuật lắp ghép phục vụ thiết kế; bài cuối dùng áp lực thay đổi thực tế, test và chi phí abstraction để quyết định mức SOLID phù hợp.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Mục đích và phạm vi áp dụng SOLID | Trách nhiệm đơn: một nhóm lý do thay đổi | Đóng-mở: mở rộng hành vi với thay đổi có kiểm soát | Thay thế Liskov: bảo toàn hợp đồng của kiểu cha | Phân tách giao diện: hợp đồng phù hợp từng bên sử dụng | Đảo ngược phụ thuộc: phụ thuộc vào sự trừu tượng | Phân biệt đảo ngược phụ thuộc và tiêm phụ thuộc | Cân bằng các nguyên tắc với độ phức tạp thực tế |
| Vấn đề cần hiểu | SOLID cần để giải quyết điều gì và ở phạm vi nào? | Một nhóm trách nhiệm và actor thay đổi là gì? | Khi thêm biến thể, sửa phần lõi bao nhiêu? | Subtype có còn đáp ứng lời hứa của supertype? | Client có phải thấy API nó không dùng? | Nghiệp vụ đang import công nghệ hay port? | DIP và DI khác nhau cụ thể ở đâu? | Khi nào abstraction có lợi hơn code đơn giản? |
| Giá trị đối với OOP/module | Có khung đánh giá năm nguyên tắc | Cohesion cao, tách I/O khỏi Invoice | Thêm policy giảm hồi quy vào ShippingService | Tương thích hành vi giữa các policy | ReadOnlyArchive không giả vờ ghi/xóa | Thay JDBC/REST mà không đổi use case | Lắp ghép/test được và đúng hướng module | Giảm độ phức tạp không có giá trị |
| Tư duy hình thành | Nhìn thay đổi và contract | Nhóm theo lý do thay đổi | Xác định phần ổn định và biến thiên | Viết tiền/hậu điều kiện, test chung | Thiết kế từ nhu cầu client | Abstraction thuộc phía dùng | Tách dependency graph khỏi wiring | Kiểm chứng đánh đổi qua ca thực tế |