# ĐA HÌNH — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Đa hình và nhiều cách thực hiện cùng một thao tác
Nhận diện một lời gọi ShippingPolicy.quote(Parcel) nhưng Economy, Standard, Express có công thức và thời gian báo giá riêng.

↓ **Từ hiện tượng một lời gọi nhiều hành vi → xác định quan hệ subtype cho phép gọi chung**

### Bài 2 — Đa hình kiểu con qua giao diện hoặc lớp cha
So sánh implements interface với extends superclass; upcast và final Report.preview gọi hook content của lớp con.

↓ **Từ subtype → tìm hiểu cách compiler và runtime quyết định phương thức**

### Bài 3 — Phân giải phương thức tĩnh và phân giải động
Compiler chọn overload dựa vào kiểu tĩnh receiver/đối số, JVM chọn implementation override theo object thực.

↓ **Từ quy tắc chọn method → thực hành override đúng điều kiện và giới hạn**

### Bài 4 — Ghi đè và lựa chọn phương thức khi chạy
DeliveryPlan.quote final bảo vệ quy trình, còn distanceFee overridable tạo khác biệt; gọi super có mục đích.

↓ **Từ override → phân biệt overload cùng tên khác tham số**

### Bài 5 — Phân biệt ghi đè với nạp chồng
Giải quyết cùng lúc overload và override trong DispatchDemo với cast, null và conversion.

↓ **Từ hai cơ chế method cùng tên → tránh nhầm với hiding các thành viên khác**

### Bài 6 — Phân biệt che khuất thành viên với ghi đè
Field và static method cùng tên được chọn theo kiểu tĩnh, khác với instance describe dispatch động.

↓ **Từ field/static hiding → tổng hợp giới hạn đa hình cho mọi loại member**

### Bài 7 — Phạm vi đa hình của phương thức và thuộc tính
Phân loại field/static/private/final/getter/abstract/default, chọn API qua method thay vì public field trùng tên.

↓ **Từ ranh giới member của Java → mở góc nhìn trừu tượng hóa theo khả năng**

### Bài 8 — Duck typing và đa hình theo khả năng
Phân biệt Java nominal typing với duck typing; dùng capability interface và adapter/method reference.

↓ **Từ khai báo đúng khả năng → tổ chức code nghiệp vụ để giảm kiểm tra loại lặp lại**

### Bài 9 — Đa hình thay cho điều kiện kiểm tra từng loại
Đưa thuật toán vào ShippingPolicy, giữ factory/switch chọn implementation ở ranh giới; kiểm thử bảo toàn hành vi.

↓ **Từ refactor strategy → vận dụng cho nhiều phương thức thanh toán và trạng thái**

### Bài 10 — Ví dụ nhiều phương thức thanh toán
CheckoutService dùng một PaymentMethod.pay cho thẻ, ví, chuyển khoản; phân biệt PAID/DECLINED/PENDING và kiểm thử với fake.

**Mối liên hệ:** Chương bắt đầu từ một lời gọi có nhiều cách thực hiện và quan hệ subtype, sau đó giải thích quy tắc compile-time và runtime chọn phương thức. Các bài ghi đè, nạp chồng, hiding và phạm vi thành viên giúp dự đoán chính xác hành vi Java; phần capability cùng refactor loại bỏ điều kiện trùng lặp; ví dụ thanh toán cuối đưa những kỹ thuật ấy vào bài toán nghiệp vụ có nhiều trạng thái.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 | Bài 9 | Bài 10 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Đa hình và nhiều cách thực hiện cùng một thao tác | Đa hình kiểu con qua giao diện hoặc lớp cha | Phân giải phương thức tĩnh và phân giải động | Ghi đè và lựa chọn phương thức khi chạy | Phân biệt ghi đè với nạp chồng | Phân biệt che khuất thành viên với ghi đè | Phạm vi đa hình của phương thức và thuộc tính | Duck typing và đa hình theo khả năng | Đa hình thay cho điều kiện kiểm tra từng loại | Ví dụ nhiều phương thức thanh toán |
| Vấn đề cần hiểu | Một lời gọi hoạt động khác nhau vì sao? | Subtype qua interface hoặc superclass là gì? | Compiler và JVM quyết định ở bước nào? | Làm sao override đúng và an toàn? | Cùng tên khi nào overload hay override? | Field/static cùng tên có override không? | Member nào thực sự được dynamic dispatch? | Cần hợp đồng kiểu hay chỉ method giống tên? | Khi nào đổi if/instanceof thành strategy? | Một pay có thể có trạng thái nào? |
| Giá trị đối với OOP/module | Client không lặp công thức | Dùng subtype theo API chung | Debug lời gọi chính xác | Mở rộng hook và giữ invariant | Tránh gọi nhầm chữ ký | Tránh field/static lookup gây lỗi | Ưu tiên method cho hành vi biến thiên | Tích hợp theo năng lực qua adapter | Giảm coupling khi thêm loại mới | Thanh toán mô phỏng dễ mở rộng/test |
| Tư duy hình thành | Hành vi đi theo runtime object | Một kiểu chung nhiều class | Chọn signature rồi chọn implementation | Giữ quy trình và mở hook | Tách tham số khỏi implementation | Phân biệt hiding với overriding | Định nghĩa rõ phạm vi polymorphism | Thiết kế từ capability | Chọn đối tượng một nơi, gọi contract mọi nơi | Phân biệt kết quả và giới hạn an toàn |