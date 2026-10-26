# GIAO DIỆN — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Giao diện như một hợp đồng và một vai trò
Xác lập interface là một hợp đồng về khả năng và hành vi, đồng thời là vai trò mà đối tượng có thể đảm nhận. Chữ ký Java mới là phần compiler xác nhận; đầu vào, kết quả và lỗi còn cần quy ước riêng.

↓ **Từ ý nghĩa lời hứa → biết khai báo và thực hiện đúng cú pháp Java**

### Bài 2 — Khai báo và thực hiện giao diện
Thực hành interface, implements, public override, static method và đa hình qua hai chính sách phí giao hàng. Xác minh compile, kết quả tính phí và kiểm tra hành vi dùng chung.

↓ **Từ một ví dụ implements → chủ động tổ chức hướng phụ thuộc của service**

### Bài 3 — Lập trình dựa trên giao diện
Cho CheckoutService nhận PaymentGateway từ constructor, chọn adapter ở composition root và thay bằng fake để kiểm thử. Phân biệt dependency injection với dependency inversion và nêu contract thanh toán.

↓ **Từ một dependency trừu tượng → xét cách một instance đảm nhiệm đồng thời nhiều vai trò**

### Bài 4 — Một đối tượng đảm nhận nhiều giao diện
Một OfficeMachine có ba vai trò và bộ trạng thái chung, trong khi kiểu tham chiếu chỉ bộc lộ API của từng vai trò. Bài mở rộng sang interface kế thừa nhiều interface và default method trùng tên.

↓ **Từ khả năng phối hợp vai trò → chọn mô hình chia sẻ hành vi/trạng thái phù hợp**

### Bài 5 — Chọn giao diện hay lớp trừu tượng
Dùng interface cho hợp đồng DeliveryFeePolicy, abstract class cho họ WeightDistancePolicy dùng chung constructor và thuật toán; cân nhắc composition khi các lớp không cùng họ.

↓ **Từ chọn abstraction → thu hẹp hợp đồng theo đúng nhu cầu người sử dụng**

### Bài 6 — Giao diện nhỏ và phân tách vai trò
Áp dụng ISP để tách DocumentReader, DocumentWriter, DocumentPublisher, tránh ép kho chỉ đọc triển khai API ghi. Các service phụ thuộc vào đúng vai trò nhưng một class vẫn có thể hỗ trợ nhiều vai trò.

↓ **Từ kích thước và trách nhiệm contract → tìm hiểu những thành viên Java thật sự cho phép**

### Bài 7 — Thành viên mặc định và giới hạn tùy ngôn ngữ
Phân biệt public abstract, default, static, private và field public static final, cùng quy tắc class wins, default conflict, giới hạn Object và khác biệt Java với các ngôn ngữ khác.

↓ **Từ hiểu quy tắc khai báo và dispatch → kiểm chứng thay implementation có giữ lời hứa hay không**

### Bài 8 — Thay thế cách thực hiện mà giữ hợp đồng
Đổi HashMapPriceCatalog sang TreeMapPriceCatalog nhưng giữ semantics về SKU, giá 0, thứ tự, snapshot và lỗi. Contract tests và LSP phân biệt tương thích hành vi với chỉ tương thích chữ ký.

↓ **Từ các nguyên tắc thay thế → tổng hợp vào bài toán có hai ranh giới hạ tầng và nguy cơ giao dịch**

### Bài 9 — Thiết kế giao diện thanh toán hoặc lưu trữ
Tách PaymentGateway, PaymentRepository và CheckoutService qua DTO và constructor injection; dùng fake kiểm tra luồng yêu cầu. Bài chỉ rõ giới hạn idempotency trong RAM khi gateway thành công nhưng lưu kết quả thất bại.

**Mối liên hệ:** Chương bắt đầu bằng việc xác định interface là lời hứa và vai trò, chuyển sang triển khai, tổ chức phụ thuộc và kết hợp nhiều vai trò, rồi ra quyết định interface/abstract class/composition và phân tách ISP. Quy tắc thành viên Java giải thích tính khả thi của thiết kế; behavioral contract/LSP cho tiêu chí kiểm chứng khi thay implementation; bài checkout cuối áp dụng toàn bộ vào hai adapter hạ tầng và những tình huống lỗi thực tế.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 | Bài 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Giao diện như một hợp đồng và một vai trò | Khai báo và thực hiện giao diện | Lập trình dựa trên giao diện | Một đối tượng đảm nhận nhiều giao diện | Chọn giao diện hay lớp trừu tượng | Giao diện nhỏ và phân tách vai trò | Thành viên mặc định và giới hạn tùy ngôn ngữ | Thay thế cách thực hiện mà giữ hợp đồng | Thiết kế giao diện thanh toán hoặc lưu trữ |
| Vấn đề cần hiểu | Chữ ký khác lời hứa hành vi ra sao? | Implements và override đúng như thế nào? | Service nên phụ thuộc vào ai? | Một instance mang nhiều vai trò thế nào? | Khi nào cần chung state và thuật toán? | Client có phụ thuộc method không dùng? | Java hỗ trợ thành viên và phân giải default ra sao? | Hai implementation có thật sự tương đương? | Tách thanh toán/lưu dữ liệu và xử lý gọi lặp thế nào? |
| Giá trị đối với OOP/module | Xác định contract tin cậy | Biên dịch và thực thi đúng giao diện | Giảm coupling, tăng khả năng test | Tái sử dụng instance qua vai trò | Tránh kế thừa gượng ép | Giữ ranh giới API theo client | Tránh sai cú pháp và API evolution | Thay adapter mà giữ semantics | Tích hợp nhiều ranh giới có kiểm soát |
| Tư duy hình thành | Thiết kế từ lời hứa | Chuyển contract thành mã chạy | Đảo hướng dependency | Tách kiểu tham chiếu khỏi object | Chọn cơ chế trừu tượng hóa | Tập trung nhu cầu từng client | Phân tích giới hạn compiler/runtime | Chứng minh substitutability bằng test | Thiết kế failure handling và idempotency |