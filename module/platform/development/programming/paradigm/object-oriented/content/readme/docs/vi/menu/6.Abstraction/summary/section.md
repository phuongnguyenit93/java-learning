# TRỪU TƯỢNG — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Trừu tượng hóa và chọn đặc điểm thiết yếu

Xuất phát từ use case để giữ đúng dữ liệu, hành vi và quy tắc cần công bố. Ví dụ báo phí và theo dõi kiện hàng cho thấy cùng một đối tượng thực tế có nhiều góc nhìn thiết kế.

↓ **Từ chọn khả năng cần công bố → phân biệt cách bảo vệ trạng thái nội bộ**

### Bài 2 — Phân biệt trừu tượng với đóng gói

Tách câu hỏi caller cần dùng API nào khỏi câu hỏi ai được sửa trạng thái. SeatInventory minh họa hợp đồng chung song song với private field, validation và bản sao collection để duy trì invariant.

↓ **Từ ranh giới công bố và kiểm soát → xác định lời hứa mà client được dựa vào**

### Bài 3 — Hợp đồng sử dụng và chi tiết thực hiện

Làm rõ đầu vào, kết quả, lỗi, side effect và khả năng thay thế implementation bằng LeavePolicy. Phân biệt khớp chữ ký Java với tuân thủ ngữ nghĩa và kiểm thử hợp đồng.

↓ **Từ mô tả lời hứa tổng thể → đặc tả trạng thái trước, sau và bất biến có thể kiểm tra**

### Bài 4 — Tiền điều kiện, hậu điều kiện và cam kết hành vi

Biểu diễn hợp đồng bằng precondition, postcondition và invariant qua reserve/release ghế. Kiểm tra đường thành công, thất bại, LSP và những giới hạn về đồng thời, bất đồng bộ.

↓ **Từ hợp đồng hành vi → cơ chế Java chia sẻ quy trình nhưng yêu cầu lớp con hoàn thiện bước riêng**

### Bài 5 — Lớp trừu tượng và phương thức trừu tượng

Sử dụng abstract class, constructor, abstract method, final method và override để triển khai Template Method của ShippingPlan. Tìm đúng phần chung và điểm biến thiên qua đa hình động.

↓ **Từ cách định nghĩa lớp nền → nhận diện lớp nào thật sự được khởi tạo và hoàn thiện hợp đồng**

### Bài 6 — Phân biệt lớp cụ thể với lớp trừu tượng

Đối chiếu NotificationChannel với EmailChannel và SmsChannel: kiểu khai báo có thể trừu tượng, nhưng object được new là lớp cụ thể. Củng cố luật biên dịch, gọi constructor và dynamic dispatch.

↓ **Từ hiểu công cụ ngôn ngữ → quyết định dùng bao nhiêu tầng trừu tượng cho yêu cầu thực**

### Bài 7 — Mức trừu tượng phù hợp với bài toán

Chọn ParkingFeeCalculator đơn giản khi chỉ có một công thức; tạo ParkingFeePolicy khi xuất hiện nhiều chính sách dùng chung checkout. Phân tích chi phí interface, kế thừa, composition và kiểm tra bảo toàn kết quả sau refactor.

↓ **Từ chọn đúng mức cấu trúc → đánh giá giao diện có thực sự cách ly chi tiết có thể thay đổi**

### Bài 8 — Rò rỉ chi tiết thực hiện qua giao diện sử dụng

Phát hiện API bắt caller biết HashMap, SQL, session hoặc hành vi không được cam kết. Chuyển sang BookCatalog có hợp đồng tìm kiếm rõ, phân biệt không tìm thấy với lỗi hệ thống và kiểm soát dữ liệu trả về.

**Mối liên hệ:** Chương tiến từ lựa chọn đặc điểm thiết yếu theo ngữ cảnh, phân biệt hợp đồng với bảo vệ trạng thái, đặc tả cam kết hành vi, đến cách thể hiện bằng lớp trừu tượng và lớp cụ thể. Hai bài cuối đưa kiến thức vào quyết định thiết kế thực tế: chỉ tạo mức trừu tượng có ích và tránh để chi tiết backend chi phối caller.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Trừu tượng hóa và chọn đặc điểm thiết yếu | Phân biệt trừu tượng với đóng gói | Hợp đồng sử dụng và chi tiết thực hiện | Tiền điều kiện, hậu điều kiện và cam kết hành vi | Lớp trừu tượng và phương thức trừu tượng | Phân biệt lớp cụ thể với lớp trừu tượng | Mức trừu tượng phù hợp với bài toán | Rò rỉ chi tiết thực hiện qua giao diện sử dụng |
| Vấn đề cần hiểu | Đặc điểm, hành vi nào thiết yếu cho use case? | Công bố khả năng khác bảo vệ trạng thái ra sao? | Caller dựa được vào lời hứa nào? | Điều gì phải đúng trước và sau lời gọi? | Làm sao chia sẻ quy trình và buộc lớp con bổ sung bước riêng? | Lớp nào new được, kiểu tham chiếu và object khác nhau thế nào? | Khi nào cần thêm interface hoặc giữ class đơn giản? | API đang bắt caller biết chi tiết backend nào? |
| Giá trị đối với OOP/module | Giảm dữ liệu và phụ thuộc dư thừa | Giữ đúng ranh giới API và invariant | Thay implementation an toàn theo ngữ nghĩa | Kiểm chứng trạng thái và tính thay thế | Tái sử dụng logic nền qua kế thừa có chủ đích | Hiểu quy tắc Java và đa hình lúc chạy | Giảm chi phí thiết kế, tiến hóa đúng nhu cầu | Bảo vệ ranh giới module khi đổi công nghệ |
| Tư duy hình thành | Mô hình theo mục đích | Tách điều được thấy và quyền được sửa | Thiết kế dựa trên cam kết có thể kiểm tra | Lập luận bằng điều kiện và bất biến | Xác định phần chung và điểm mở rộng | Phân tích kiểu khai báo so với kiểu runtime | Quyết định theo biến thể thực tế và thử nghiệm | Xem API từ góc nhìn caller, che những chi tiết không cần biết |