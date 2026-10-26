# KẾ THỪA — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Lớp cha, lớp con và quan hệ kế thừa

Nắm bản chất quan hệ is-a, phân định superclass/subclass và hợp đồng chung; nhận diện đa hình qua bài toán nhân viên.

↓ **Từ nhận diện loại đối tượng → phân tích dữ liệu và phương thức hiện diện trong chính object lớp con.**

### Bài 2 — Kế thừa trạng thái và hành vi

Xác định trạng thái và hành vi nằm ở cha/con; phân biệt kế thừa, bổ sung, override và field hiding.

↓ **Từ hiểu thành phần kế thừa → thực hành bổ sung hành vi theo biến thể cụ thể.**

### Bài 3 — Mở rộng hành vi của lớp cha

Vận dụng override kết hợp super và Template Method để thêm chức năng mà vẫn giữ quy trình chung.

↓ **Từ cách mở rộng hành vi → kiểm tra chặt quy tắc override, super và dispatch.**

### Bài 4 — Ghi đè phương thức và gọi hành vi lớp cha

Đọc chính xác dynamic dispatch, quy tắc @Override, super.method() và ranh giới hợp đồng của phương thức.

↓ **Từ override hợp lệ → xác định thành viên nào có thể được nhìn thấy và gọi từ từng package.**

### Bài 5 — Khả năng truy cập thành viên được kế thừa

Phán đoán khả năng truy cập public/protected/package-private/private, nhất là subclass nằm khác package.

↓ **Từ quyền truy cập member → theo dõi trình tự khởi tạo trước khi các hành vi ấy được dùng.**

### Bài 6 — Khởi tạo lớp con và chuỗi khởi tạo lớp cha

Truy vết static/instance initialization và constructor chaining, tránh gọi override trên trạng thái chưa hoàn chỉnh.

↓ **Từ nguy cơ khi khởi tạo → quyết định phần nào phải khóa để bảo toàn quy tắc của superclass.**

### Bài 7 — Thành viên không thể ghi đè và lớp không thể kế thừa

Kiểm soát điểm mở rộng bằng final class/final method, cân nhắc sealed và các điểm hook hợp lệ.

↓ **Từ khóa điểm mở rộng trên một class → tìm cách kết hợp nhiều hợp đồng qua interface trong giới hạn Java.**

### Bài 8 — Kế thừa đơn, kế thừa đa và xung đột hành vi

Kết hợp nhiều interface trong giới hạn một superclass, giải quyết va chạm default method bằng luật ưu tiên Java.

↓ **Từ default method và xung đột Java → xem khả năng chia sẻ hành vi cho các lớp khác nhánh và so sánh với trait.**

### Bài 9 — Mixin, trait và tái sử dụng hành vi tùy ngôn ngữ

Mở rộng tái sử dụng hành vi qua interface default method, phân biệt mixin/trait giữa các ngôn ngữ và composition.

↓ **Từ các kỹ thuật tái sử dụng → đánh giá chi phí dài hạn của cây kế thừa nhiều tầng.**

### Bài 10 — Giới hạn và chi phí của cây kế thừa sâu

Nhận diện fragile base class, chi phí kiểm thử của cây sâu và lựa chọn giữ nguyên hợp đồng khi tái cấu trúc.

**Mối liên hệ:** Chương bắt đầu từ hợp đồng is-a và cấu trúc đối tượng, chuyển sang mở rộng/ghi đè hành vi, giới hạn truy cập và khởi tạo, rồi xác định các điểm mở rộng hợp lệ. Hai bài về interface và mixin bổ sung cách ghép khả năng ngoài một cây lớp; bài cuối đặt mọi lựa chọn trong bối cảnh chi phí bảo trì và rủi ro thay đổi. Thứ tự này hỗ trợ học theo chiều sâu, nhưng mixin/trait cũng là một góc nhìn so sánh độc lập với kỹ thuật kế thừa lớp.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 | Bài 9 | Bài 10 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Lớp cha, lớp con và quan hệ kế thừa | Kế thừa trạng thái và hành vi | Mở rộng hành vi của lớp cha | Ghi đè phương thức và gọi hành vi lớp cha | Khả năng truy cập thành viên được kế thừa | Khởi tạo lớp con và chuỗi khởi tạo lớp cha | Thành viên không thể ghi đè và lớp không thể kế thừa | Kế thừa đơn, kế thừa đa và xung đột hành vi | Mixin, trait và tái sử dụng hành vi tùy ngôn ngữ | Giới hạn và chi phí của cây kế thừa sâu |
| Vấn đề cần hiểu | Kiểu con có thực sự là kiểu cha? | State và behavior của cha tồn tại/sử dụng thế nào? | Mở rộng chức năng mà giữ bước chung ra sao? | Override và super quyết định phương thức chạy thế nào? | Ai được truy cập member nào ở package nào? | new Child chạy các bước khởi tạo theo thứ tự nào? | Khi nào cấm extends hoặc override? | Ghép interface và giải quyết default conflict thế nào? | Lớp không cùng cha chia sẻ hành vi bằng cơ chế nào? | Cây lớp sâu gây rủi ro gì và giảm sâu thế nào? |
| Giá trị đối với OOP/module | Thiết lập quan hệ subtype có hợp đồng rõ. | Giữ dữ liệu chung và invariant trong superclass. | Xây điểm mở rộng cho biến thể nghiệp vụ. | Bảo đảm dispatch và xử lý super đúng. | Thiết kế API phù hợp phạm vi truy cập. | Khởi tạo subclass nhất quán và an toàn. | Cố định invariant, quy trình và phạm vi subtype. | Dùng nhiều capability mà không đa kế thừa state. | Tái sử dụng behavior ngang cây lớp khi phù hợp. | Giảm coupling và rủi ro hồi quy ở thiết kế lớn. |
| Tư duy hình thành | Kiểm tra is-a và khả năng thay thế. | Phân biệt hiện diện state với quyền nhìn thấy nó. | Chuyên biệt hóa có kiểm soát. | Theo dõi lời gọi runtime và cam kết contract. | Suy từ modifier, package, kiểu qualifier. | Truy vết vòng đời và trạng thái chưa sẵn sàng. | Chọn phần mở/phần khóa có chủ đích. | Giải va chạm hợp đồng trước khi ghép. | Tách capability khỏi quan hệ họ hàng lớp. | Đánh giá chi phí thay đổi trên toàn phân cấp. |