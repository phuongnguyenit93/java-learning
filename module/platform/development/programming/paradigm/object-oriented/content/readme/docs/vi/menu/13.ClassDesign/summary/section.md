# THIẾT KẾ LỚP — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Xác định trách nhiệm của từng lớp

Biết cách phân vai theo SRP, Information Expert và use case mượn sách; xác định nơi giữ LoanPolicy, Loan, LoanService, repository, rồi kiểm tra cộng tác bằng CRC Card.

↓ **Từ xác định owner của từng quy tắc → đánh giá xem hành vi trong cùng class có gắn kết**

### Bài 2 — Tính gắn kết bên trong lớp

Dùng cohesion, nhóm field/method và lý do thay đổi để phân tích lớp; tách Cart, repository, sender và checkout coordinator khi các trách nhiệm thực sự độc lập.

↓ **Từ trách nhiệm nội bộ → kiểm tra mức phụ thuộc giữa các lớp cộng tác**

### Bài 3 — Mức phụ thuộc giữa các lớp

Đánh giá phụ thuộc mã nguồn/runtime/change; dùng PaymentGateway, ReceiptSender và DI ở biên biến động, đồng thời xét lỗi thanh toán và gửi biên nhận.

↓ **Từ coupling giữa cộng tác viên → đặt đúng biên giữa miền và hạ tầng**

### Bài 4 — Tách dữ liệu, nghiệp vụ và tương tác bên ngoài

Phân chia DTO, OrderLine/Order, pricing, use case, repository/gateway; giữ invariant trong miền và xử lý side effect tại adapter.

↓ **Từ tách domain và I/O → biểu đạt ranh giới bằng tên class và API dễ hiểu**

### Bài 5 — Đặt tên lớp, thuộc tính và phương thức

Dùng naming convention Java, tên mang đơn vị và method thể hiện query/command; xét khả năng tương thích khi rename framework/public API.

↓ **Từ tên API nói đúng trách nhiệm → phân loại đối tượng theo danh tính, giá trị và hành vi phối hợp**

### Bài 6 — Đối tượng thực thể, đối tượng giá trị và dịch vụ

Nhận diện Account theo UUID, Money theo giá trị và TransferService theo phối hợp hai tài khoản, phân biệt miền với annotation JPA/Spring.

↓ **Từ ba vai trò entity/value/service → quyết định hành vi nào ở gần dữ liệu cần bảo vệ**

### Bài 7 — Mô hình giàu hành vi và mô hình chỉ chứa dữ liệu

So sánh Order có addItem/confirm với OrderData chứa setter; chọn DTO/projection khi không có state transition phức tạp.

↓ **Từ lựa chọn mô hình và nơi giữ invariant → thiết kế rõ ý định đọc và thao tác thay đổi trạng thái**

### Bài 8 — Tách truy vấn khỏi thao tác thay đổi trạng thái

Tách query khỏi command theo CQS; vẫn kiểm tra–ghi nguyên tử và xét idempotency khi command có retry.

↓ **Từ API đọc/ghi rõ ràng → hạn chế caller phụ thuộc cấu trúc bên trong của các đối tượng khác**

### Bài 9 — Giới hạn việc truy cập sâu vào chuỗi đối tượng

Áp dụng Law of Demeter và Tell, don't ask; Loan yêu cầu tính phí qua cộng tác viên gần thay vì service tự đi qua Member/Plan.

**Mối liên hệ:** Chương bắt đầu bằng phân chia trách nhiệm theo lý do thay đổi và xem xét cohesion bên trong, coupling bên ngoài. Từ đó người học thiết lập biên giữa miền và I/O, thể hiện hợp đồng bằng tên gọi, phân vai entity/value object/service, lựa chọn mô hình hành vi phù hợp. Hai bài cuối bảo vệ tính dự đoán của API khi đọc/ghi và hạn chế client phụ thuộc chuỗi cấu trúc nội bộ. Mỗi bước là một góc nhìn hỗ trợ ra quyết định thiết kế, không mặc định bài kề nhau luôn là điều kiện tiên quyết tuyệt đối.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 | Bài 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Xác định trách nhiệm của từng lớp | Tính gắn kết bên trong lớp | Mức phụ thuộc giữa các lớp | Tách dữ liệu, nghiệp vụ và tương tác bên ngoài | Đặt tên lớp, thuộc tính và phương thức | Đối tượng thực thể, đối tượng giá trị và dịch vụ | Mô hình giàu hành vi và mô hình chỉ chứa dữ liệu | Tách truy vấn khỏi thao tác thay đổi trạng thái | Giới hạn việc truy cập sâu vào chuỗi đối tượng |
| Vấn đề cần hiểu | Ai sở hữu từng invariant, policy và workflow? | Các field/method có phục vụ cùng trách nhiệm không? | Chi tiết thay đổi ở class khác lan truyền đến đâu? | Domain, use case và I/O được phân ranh thế nào? | Tên nào phản ánh nghĩa, đơn vị và side effect? | Phân biệt đối tượng bằng identity, value hay behavior? | Quy tắc nên ở entity hay ở service/data class? | Method đang đọc hay cập nhật trạng thái? | Caller có biết quá sâu cấu trúc object không? |
| Giá trị đối với OOP/module | Giữ một owner cho mỗi quy tắc | Gom invariant và thay đổi có liên quan | Thay provider/test không kéo sửa business logic | Cô lập HTTP/DB/email khỏi domain | Giảm hiểu sai API và lỗi rename/mapping | Đặt equality và domain rules đúng chủ thể | Bảo vệ chuyển trạng thái, tránh setter tùy tiện | Chống side effect ẩn và race/retry | Giảm coupling khi thay cấu trúc sâu |
| Tư duy hình thành | Đi từ use case tới trách nhiệm | Đánh giá ngữ nghĩa nội bộ thay vì kích cỡ | Hướng vào hợp đồng ổn định | Thiết kế theo ranh giới thay đổi | Đặt tên như một lời cam kết | Phân biệt danh tính với giá trị | Chọn mức rich/anemic có căn cứ | Đọc và ghi là hai ý định khác nhau | Yêu cầu hành vi từ cộng tác viên gần |