# LỚP — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Cấu trúc lớp: thuộc tính và phương thức

Nhận diện field, constructor và method trên lớp `InventoryItem`; thấy trạng thái được thay đổi qua API có kiểm tra.

↓ **Từ cấu trúc class → quan sát việc khai báo lớp tạo ra những instance và tham chiếu cụ thể**

### Bài 2 — Khai báo lớp và tạo thể hiện

Phân biệt class và instance được tạo bằng `new`, đồng thời theo dấu các alias và trạng thái độc lập qua `Workshop`.

↓ **Từ trạng thái mỗi instance → phân biệt dữ liệu riêng với thành viên được lớp chia sẻ**

### Bài 3 — Thành viên của thể hiện và thành viên dùng chung

Chọn instance hay `static` theo dữ liệu thuộc từng đối tượng hay thuộc lớp; nhận diện giới hạn trạng thái chung.

↓ **Từ nơi dữ liệu thuộc về → chọn mức quyền truy cập cho từng thành viên**

### Bài 4 — Phạm vi truy cập của thành viên

Xác định phạm vi sử dụng field/method bằng bốn access level và thiết kế API bảo vệ dữ liệu.

↓ **Từ quyền gọi thành viên → làm rõ đầu vào, đầu ra và tác động của method**

### Bài 5 — Tham số, giá trị trả về và tác động của phương thức

Xác định hợp đồng tham số, return, tác động và lỗi; lý giải đúng Java pass-by-value.

↓ **Từ tác động của lời gọi → xác định trạng thái nào được phép thay đổi và cách bảo vệ nó**

### Bài 6 — Thành viên chỉ đọc và thành viên có thể thay đổi

Chọn quyền thay đổi và mức bất biến của trạng thái; không làm lộ mutable collection qua API.

↓ **Từ trạng thái được bảo vệ → hiểu `this` đang thao tác trên instance nào**

### Bài 7 — Tham chiếu đến đối tượng hiện tại

Xác định đối tượng hiện tại bằng `this` trong constructor/method, chaining và inner class.

↓ **Từ từng cơ chế riêng → kết hợp chúng vào một thiết kế ngân hàng có quy tắc nghiệp vụ**

### Bài 8 — Thiết kế lớp qua ví dụ tài khoản ngân hàng

Áp dụng các nguyên tắc trên vào `BankAccount` có bất biến số dư và giao dịch được kiểm tra.

**Mối liên hệ:** Chương bắt đầu từ cấu trúc class, vòng đời instance rồi đi sâu vào quyền sở hữu và truy cập dữ liệu, hợp đồng method, khả năng thay đổi trạng thái và `this`. Bài cuối kết nối những khái niệm đó thành mô hình tài khoản ngân hàng bảo vệ bất biến. Các bài cung cấp các góc nhìn bổ trợ để thiết kế một class đúng đắn.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Cấu trúc lớp: thuộc tính và phương thức | Khai báo lớp và tạo thể hiện | Thành viên của thể hiện và thành viên dùng chung | Phạm vi truy cập của thành viên | Tham số, giá trị trả về và tác động của phương thức | Thành viên chỉ đọc và thành viên có thể thay đổi | Tham chiếu đến đối tượng hiện tại | Thiết kế lớp qua ví dụ tài khoản ngân hàng |
| Vấn đề cần hiểu | Field và method kết hợp ra sao? | `new` và alias tạo/trỏ instance thế nào? | Instance và static thuộc về ai? | Thành viên được truy cập từ đâu? | Method nhận, trả và sửa điều gì? | Dữ liệu được đổi bởi ai và bằng cách nào? | `this` là đối tượng nào? | Làm sao giao dịch giữ bất biến số dư? |
| Giá trị đối với OOP/module | Cấu trúc class và giữ trạng thái hợp lệ | Phân biệt định nghĩa, đối tượng và tham chiếu | Chọn đúng phạm vi riêng/chung của state | Giữ API tối thiểu và bảo vệ dữ liệu | Định nghĩa hợp đồng method và side effect | Dùng `final`, getter, copy và hành vi nghiệp vụ | Khởi tạo/gọi đúng receiver và chaining hợp lệ | Tổng hợp encapsulation, invariant và kiểm tra lỗi |
| Tư duy hình thành | Đọc thành phần và trách nhiệm lớp | Theo dấu alias và trạng thái instance | Xác định chủ sở hữu/vòng đời state | Thiết kế ranh giới truy cập | Dự đoán trạng thái trước/sau lời gọi | Phân biệt đọc, gán lại, sửa nội dung | Luôn nhận diện instance hiện tại | Đánh giá tính đúng đắn và giới hạn mô hình |