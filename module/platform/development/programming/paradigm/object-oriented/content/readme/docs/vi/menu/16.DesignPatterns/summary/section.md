# MẪU THIẾT KẾ — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Mẫu thiết kế: vấn đề, bối cảnh và đánh đổi

Mô tả pattern qua vấn đề, bối cảnh, ràng buộc và đánh đổi; so sánh switch/Strategy trên checkout trước khi chọn abstraction.

↓ **Từ cách cân nhắc vấn đề và trade-off → chọn kỹ thuật tạo đối tượng theo quan hệ biến thiên thực.**

### Bài 2 — Factory Method và Abstract Factory

Tách quyết định tạo Product bằng Creator subclass hoặc Abstract Factory cho họ UI Light/Dark nhất quán.

↓ **Từ factory chọn class/họ sản phẩm → xét cách cấu hình từng object phức tạp và các bất biến khởi tạo.**

### Bài 3 — Builder và việc tạo đối tượng phức tạp

Tổ chức các bước tạo Order nhiều trường bằng fluent Builder, kiểm tra bất biến tại build() và chụp snapshot dữ liệu.

↓ **Từ chất lượng khởi tạo object → đánh giá vòng đời của instance dùng chung và rủi ro mutable state.**

### Bài 4 — Singleton và rủi ro trạng thái dùng chung

Quản lý một instance theo phạm vi xác định, phân biệt initialization với thread safety, scope Spring và dependency injection.

↓ **Từ quyền sở hữu và vòng đời object → chuyển sang ranh giới tích hợp SDK với contract khác.**

### Bài 5 — Adapter và tích hợp giao diện khác nhau

Chuyển chữ ký, dữ liệu tiền tệ, trạng thái và lỗi từ SDK cũ sang hợp đồng PaymentGateway do ứng dụng sở hữu.

↓ **Từ đổi hợp đồng bằng Adapter → chuyển góc nhìn sang giữ nguyên interface nhưng bọc thêm hành vi.**

### Bài 6 — Decorator và bổ sung hành vi

Bổ sung prefix, giới hạn độ dài, metric cho MessageChannel qua wrapper giữ cùng interface, lưu ý thứ tự và lỗi.

↓ **Từ chuỗi wrapper quanh một component → đơn giản hóa nhiều subsystem bằng một điểm vào cấp cao.**

### Bài 7 — Facade và đơn giản hóa cách sử dụng

Gom quy trình báo giá qua Catalog, Inventory, Discount, Shipping thành API quote rõ ràng và kiểm thử được.

↓ **Từ API cấp cao ổn định → đóng gói các công thức thay thế bên trong workflow.**

### Bài 8 — Strategy và thay thế thuật toán

Thay chính sách giảm giá bằng các DiscountStrategy qua composition, bảo toàn contract và giới hạn lựa chọn.

↓ **Từ việc chọn thuật toán → xem hành vi đổi theo chính trạng thái vòng đời và transition hợp lệ.**

### Bài 9 — State và hành vi theo trạng thái

Đóng gói hành vi theo trạng thái Order, bảo vệ ma trận transition và kiểm soát consistency.

↓ **Từ chuyển trạng thái nội bộ → thông báo nhiều thành phần quan tâm mà không ghép cứng chúng.**

### Bài 10 — Observer và thông báo thay đổi

Thông báo trạng thái đổi tới nhiều listener, bảo vệ subscription lifecycle, event order và transaction boundary.

↓ **Từ phản ứng theo sự kiện → xem một loại phối hợp khác: khung thuật toán cố định với bước tùy biến.**

### Bài 11 — Template Method và khung thuật toán

Giữ khung xuất báo cáo trong final generate(), dành primitive operations và hooks cho subclass tùy biến.

↓ **Từ khung thuật toán chia sẻ → đóng gói một yêu cầu riêng thành object có thể thực thi và ghi lịch sử.**

### Bài 12 — Command và đóng gói yêu cầu

Biến yêu cầu AddTask/CompleteTask thành object với Receiver, Invoker và undo/redo có lịch sử.

↓ **Từ nhiều cách tổ chức linh hoạt → đánh giá lại chi phí pattern, giữ thiết kế đơn giản khi biến thể không thực sự cần.**

### Bài 13 — Khi mẫu thiết kế làm hệ thống phức tạp hơn cần thiết

So sánh lợi ích mở rộng với chi phí abstraction, tránh God Factory/Facade và refactor dựa trên hành vi.

**Mối liên hệ:** Chương bắt đầu bằng cách chọn pattern theo bối cảnh, rồi xem lần lượt cách tạo object, tích hợp và tổ chức cấu trúc, thay đổi thuật toán/trạng thái, thông báo sự kiện và đóng gói workflow/yêu cầu. Các mẫu chia sẻ kỹ thuật composition, kế thừa và đa hình nhưng giải quyết những vấn đề khác nhau; bài cuối yêu cầu so sánh lợi ích với chi phí thực tế, chỉ giữ ranh giới nào bảo vệ contract và khả năng thay đổi đã có bằng chứng.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 | Bài 9 | Bài 10 | Bài 11 | Bài 12 | Bài 13 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Mẫu thiết kế: vấn đề, bối cảnh và đánh đổi | Factory Method và Abstract Factory | Builder và việc tạo đối tượng phức tạp | Singleton và rủi ro trạng thái dùng chung | Adapter và tích hợp giao diện khác nhau | Decorator và bổ sung hành vi | Facade và đơn giản hóa cách sử dụng | Strategy và thay thế thuật toán | State và hành vi theo trạng thái | Observer và thông báo thay đổi | Template Method và khung thuật toán | Command và đóng gói yêu cầu | Khi mẫu thiết kế làm hệ thống phức tạp hơn cần thiết |
| Vấn đề cần hiểu | Vì sao cần pattern? | Tạo một Product hay cả họ? | Tạo object nhiều ràng buộc? | Một instance có an toàn? | SDK khác contract ra sao? | Bọc thêm hành vi theo thứ tự? | Client biết quá nhiều subsystem? | Thay thuật toán nào? | Đổi state nào hợp lệ? | Ai nhận sự kiện và lúc nào? | Bước workflow nào giữ cố định? | Ai thực thi và hoàn tác lệnh? | Abstraction nào thật sự đáng có? |
| Giá trị đối với OOP/module | Quyết định dựa trên bối cảnh | Giảm phụ thuộc loại object cụ thể | Khởi tạo hợp lệ, dễ đọc | Tránh rò request/race | Bảo vệ domain khỏi vendor API | Ghép hành vi mà không sửa lõi | Ranh giới module gọn | Đa hình thuật toán theo hợp đồng | Giữ invariant vòng đời | Giảm coupling giữa publisher/listeners | Tái sử dụng khung ổn định | Tách nguồn gọi khỏi xử lý | Giảm indirection vô ích |
| Tư duy hình thành | Vấn đề trước tên mẫu | Tạo đúng product/họ | Dựng đủ trạng thái hợp lệ | Scope khác thread safety | Map cả ý nghĩa và lỗi | Thứ tự bọc là hành vi | Đơn giản phía client, không giấu rủi ro | Biến thể có chủ sở hữu | Transition theo luật | Phát sau thay đổi, quản lý lifecycle | Ổn định trước, mở rộng điểm nhỏ | Yêu cầu là một đơn vị thực thi | Đủ đơn giản nhưng giữ contract |