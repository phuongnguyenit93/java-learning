# THỰC HÀNH — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Bài tập lớp và đối tượng qua tài khoản ngân hàng

Thiết kế BankAccount với dữ liệu riêng, API nạp/rút/chuyển và test các invariant số dư, overflow, lỗi giao dịch.

↓ **Từ thực hành tạo object có trạng thái → thực hành bảo vệ mọi đường sửa trạng thái bằng encapsulation**

### Bài 2 — Bài tập đóng gói quy tắc của đơn hàng

Áp dụng encapsulation vào Order: kiểm soát dòng hàng, tổng tiền, DRAFT/PLACED/CANCELLED và các thay đổi thất bại.

↓ **Từ invariant của một aggregate → vận dụng interface và đa hình cho nhiều hành vi thanh toán**

### Bài 3 — Bài tập giao diện và đa hình thanh toán

Dùng PaymentMethod và dynamic dispatch để xử lý nhiều loại thanh toán với chung hợp đồng mà không thêm nhánh ở processor.

↓ **Từ hợp đồng interface → quyết định dùng kế thừa hay kết hợp khi mở rộng thiết kế**

### Bài 4 — Bài tập chọn kế thừa hay kết hợp

Chọn inheritance theo is-a và hợp đồng Shape; chọn composition cho formatter/sink thay thế được trong ReportPublisher.

↓ **Từ quan hệ lớp/cộng tác viên → xây value object và quy tắc so sánh ổn định**

### Bài 5 — Bài tập đối tượng giá trị và so sánh bằng

Chuẩn hóa Sku/Money, bảo đảm equals/hashCode/compareTo, khóa collection bất biến và kiểm tra tác hại mutable alias.

↓ **Từ thiết kế từng object đúng hợp đồng → học nhận diện dấu hiệu thiết kế sai trong code có thật**

### Bài 6 — Bài tập nhận diện lỗi thiết kế

Review đoạn checkout có mùi thiết kế: chỉ ra bằng chứng, tách Order, payment, ledger và bảo vệ hành vi bằng test.

↓ **Từ phát hiện lỗi thiết kế → áp dụng quy trình refactor có kiểm thử hồi quy**

### Bài 7 — Bài tập tái cấu trúc đoạn mã thủ tục thành mô hình đối tượng

Chuyển hàm tính tiền thủ tục thành Order, DiscountPolicy, ShippingPolicy, CheckoutService và Receipt có regression tests.

↓ **Từ cải tiến bài toán nhỏ → khởi động dự án nhiều use case bằng phân tích yêu cầu**

### Bài 8 — Dự án tổng hợp: phân tích yêu cầu và tình huống sử dụng

Thu thập yêu cầu thư viện, actor, BR/FR/NFR, use case, luồng lỗi và bảng truy vết tới ca kiểm thử.

↓ **Từ yêu cầu/ca nghiệm thu → thiết kế các class, quan hệ và invariant cho hệ thống thư viện**

### Bài 9 — Dự án tổng hợp: thiết kế lớp và quan hệ

Biến yêu cầu thành Book, BookCopy, Member, Loan và service/policy; kiểm soát quan hệ, danh tính và invariant.

↓ **Từ thiết kế đối tượng → hiện thực các use case và kiểm chứng hành vi trong Java/JUnit**

### Bài 10 — Dự án tổng hợp: hiện thực và kiểm thử hành vi

Triển khai mượn/trả theo copyId với Clock và JUnit; kiểm tra trạng thái khi thành công/thất bại và giới hạn đồng thời.

↓ **Từ ứng dụng đã có kiểm thử → tiếp nhận thay đổi quy tắc mà giữ đúng dữ liệu lịch sử**

### Bài 11 — Dự án tổng hợp: thay đổi yêu cầu và đánh giá thiết kế

Thích nghi hệ thống với giới hạn 5 phiếu và hạn 7 ngày cho sách tham khảo; giữ nguyên dueOn lịch sử và đánh giá qua ma trận test.

**Mối liên hệ:** Bảy bài tập đầu biến các nguyên tắc OOP thành năng lực thực hành từ lớp/đối tượng, đóng gói, đa hình, thiết kế quan hệ tới value object và refactor có kiểm thử. Bốn bài sau là chuỗi dự án thư viện: phân tích yêu cầu → thiết kế lớp/quan hệ → hiện thực và kiểm thử → thay đổi yêu cầu có bảo toàn hợp đồng. Các thao tác thất bại phải giữ invariant, còn thay đổi chính sách phải được kiểm chứng cả với dữ liệu cũ và mới.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 | Bài 9 | Bài 10 | Bài 11 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Bài tập lớp và đối tượng qua tài khoản ngân hàng | Bài tập đóng gói quy tắc của đơn hàng | Bài tập giao diện và đa hình thanh toán | Bài tập chọn kế thừa hay kết hợp | Bài tập đối tượng giá trị và so sánh bằng | Bài tập nhận diện lỗi thiết kế | Bài tập tái cấu trúc đoạn mã thủ tục thành mô hình đối tượng | Dự án tổng hợp: phân tích yêu cầu và tình huống sử dụng | Dự án tổng hợp: thiết kế lớp và quan hệ | Dự án tổng hợp: hiện thực và kiểm thử hành vi | Dự án tổng hợp: thay đổi yêu cầu và đánh giá thiết kế |
| Vấn đề cần hiểu | Class, instance, chuyển tiền an toàn trong RAM? | Order bảo vệ trạng thái và tổng tiền thế nào? | Nhiều PaymentMethod cùng một API hoạt động thế nào? | Khi nào is-a phù hợp, khi nào dùng collaborator? | Giá trị bằng nhau và collection key chính xác ra sao? | Đâu là bug, code smell và trách nhiệm bị đặt sai? | Giữ phép tính gốc khi tách chính sách thế nào? | Ai cần gì, trong phạm vi nào, nghiệm thu ra sao? | Class nào quản lý đầu sách, bản sách, phiếu và invariant? | Mượn/trả đúng trong các trường hợp lỗi/ngày biên thế nào? | Thay policy mới mà không phá dueOn cũ thế nào? |
| Giá trị đối với OOP/module | Củng cố class-object, encapsulation, invariant, test. | Thiết kế aggregate Order có API an toàn. | Áp dụng đa hình runtime và tách processor. | Giữ hợp đồng thay thế, giảm hierarchy cứng. | Sử dụng value object bất biến đúng equals/hash/ordering. | Có phương pháp review và phân trách nhiệm. | Tái cấu trúc có test, giảm phụ thuộc vào magic string. | Đặt yêu cầu/test làm cơ sở thiết kế. | Chuyển yêu cầu sang mô hình object có quan hệ rõ. | Kiểm tra luồng nghiệp vụ có nhiều thực thể. | Đánh giá OOP khi chính sách và hợp đồng thay đổi. |
| Tư duy hình thành | Giữ dữ liệu trong lớp và kiểm tra trước khi thay. | Đổi trạng thái qua hành vi, không mở field tùy ý. | Lập trình theo contract, thay implementation. | Chọn quan hệ theo ngữ nghĩa và tiền/hậu điều kiện. | Chuẩn hóa trước khi so sánh và lưu collection. | Review theo bằng chứng, hậu quả và test. | Đổi từng bước, kiểm tra equivalence và lỗi biên. | Hỏi rõ giả định, luồng thất bại và tiêu chí đo. | Xác định identity và chủ sở hữu invariant. | Quan sát trạng thái sau từng nhánh thành công/lỗi. | Giữ hợp đồng lịch sử và chứng minh tác động bằng test. |