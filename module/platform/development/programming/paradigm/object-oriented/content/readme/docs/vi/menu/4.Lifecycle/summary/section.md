# VÒNG ĐỜI — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Tạo đối tượng và cấp phát tài nguyên

Phân biệt instance, tham chiếu và tài nguyên ngoài JVM, theo dõi quá trình `new`, rồi chủ động đóng resource thay vì chờ GC.

↓ **Từ tạo instance → xác định thế nào là trạng thái khởi tạo hợp lệ**

### Bài 2 — Hàm khởi tạo và thiết lập trạng thái hợp lệ

Định nghĩa invariant và tổ chức validation, defensive copy, `final` để một constructor hoàn tất luôn tạo trạng thái hợp lệ.

↓ **Từ bảo vệ invariant → lựa chọn những đường gọi constructor duy trì invariant đó**

### Bài 3 — Khởi tạo mặc định, tham số và nhiều cách khởi tạo

Phân biệt default/no-arg/parameterized constructors, thiết kế overload và nối chuỗi `this(...)`/`super(...)` bảo vệ cùng quy tắc.

↓ **Từ chuỗi this/super → truy vết toàn bộ thứ tự khởi tạo trong kế thừa**

### Bài 4 — Thứ tự khởi tạo trong quan hệ kế thừa

Truy vết static initializer, instance initializer và constructor theo chiều lớp cha đến lớp con; phát hiện override chạy quá sớm.

↓ **Từ cơ chế constructor → mở rộng sang API tạo đối tượng có tên và cấu hình**

### Bài 5 — Hàm tạo tĩnh, Factory và Builder

Lựa chọn constructor, static factory, Factory chọn implementation hoặc Builder để diễn đạt cách tạo object phù hợp API.

↓ **Từ cách tạo object → xem object còn tồn tại bao lâu và ai đang giữ tham chiếu**

### Bài 6 — Thời gian tồn tại, khả năng truy cập và giải phóng bộ nhớ

Đọc đồ thị GC roots và lifetime theo reachability, phát hiện alias, cache, listener hay ThreadLocal làm đối tượng bị giữ lại.

↓ **Từ đồ thị reachability → hiểu GC thực sự thu hồi gì và đo bộ nhớ thế nào**

### Bài 7 — Thu gom rác và quản lý bộ nhớ thủ công

Giải thích trách nhiệm của GC đối với heap và giới hạn với native resource, đo đạc các dạng memory pressure khác nhau.

↓ **Từ cơ chế quản lý heap → quy định thời điểm hoàn trả tài nguyên ngoài heap**

### Bài 8 — Đóng tài nguyên và giới hạn của hàm hủy

Thiết kế quyền sở hữu và `close()` có thời điểm rõ ràng bằng try-with-resources, kể cả khi có nhiều tài nguyên và lỗi khi đóng.

↓ **Từ đóng đúng vòng đời sử dụng → bảo đảm không công bố object trước khi sẵn sàng**

### Bài 9 — Tránh để đối tượng chưa khởi tạo xong bị sử dụng

Bảo đảm object được khởi tạo hoàn tất trước khi công bố cho callback, thread hay registry; sử dụng safe publication phù hợp.

**Mối liên hệ:** Chương đi từ bản chất tạo đối tượng và bảo vệ trạng thái ban đầu, qua các cách xây dựng đối tượng, tới khả năng truy cập và thu hồi bộ nhớ. Sau đó, người học chuyển sang trách nhiệm đóng tài nguyên và cuối cùng bảo vệ thời điểm object được đưa ra cho mã ngoài hoặc luồng khác. GC, `close()` và safe publication giải quyết ba khía cạnh khác nhau của vòng đời; cần kiểm tra đúng hợp đồng của từng khía cạnh.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 | Bài 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Tạo đối tượng và cấp phát tài nguyên | Hàm khởi tạo và thiết lập trạng thái hợp lệ | Khởi tạo mặc định, tham số và nhiều cách khởi tạo | Thứ tự khởi tạo trong quan hệ kế thừa | Hàm tạo tĩnh, Factory và Builder | Thời gian tồn tại, khả năng truy cập và giải phóng bộ nhớ | Thu gom rác và quản lý bộ nhớ thủ công | Đóng tài nguyên và giới hạn của hàm hủy | Tránh để đối tượng chưa khởi tạo xong bị sử dụng |
| Vấn đề cần hiểu | `new` tạo instance khác gì gán tham chiếu, còn file/socket phải đóng thế nào? | Constructor bảo vệ invariant và dữ liệu mutable ra sao? | Default/no-arg/overload và `this`/`super` được chọn thế nào? | Static, instance initializer và constructor cha/con chạy theo thứ tự nào? | Khi nào dùng static factory, Factory hoặc Builder? | Scope, GC roots, weak reference và object retention có quan hệ gì? | GC xử lý heap đến đâu, khi nào cần quản lý native memory? | Đóng resource theo ownership và xử lý exception khi close thế nào? | Làm sao ngăn `this escape`, callback sớm và công bố giữa các thread? |
| Giá trị đối với OOP/module | Hiểu ranh giới instance và resource khi bắt đầu vòng đời. | Tạo object hợp lệ, bảo vệ trạng thái và các tiền điều kiện. | Xây dựng API constructor rõ nghĩa, không lỗi chuỗi khởi tạo. | Đọc đúng hành vi kế thừa và tránh kết quả khởi tạo bất ngờ. | Thiết kế cách tạo object và lựa chọn implementation linh hoạt. | Tránh giữ object vô hạn, chủ động giới hạn cache/listener. | Tách giám sát heap, native memory và chính sách GC thực tế. | Hoàn trả file, JDBC, socket đúng lúc và đúng người sở hữu. | Ngăn dùng object dở dang, giảm lỗi đồng thời khó tái hiện. |
| Tư duy hình thành | Phân tích hai vòng đời: bộ nhớ và tài nguyên. | Bắt đầu từ hợp đồng trạng thái trước khi gán field. | Xem mọi overload như một đường tới invariant chung. | Dự đoán bằng trace thực thi thay vì chỉ nhìn thứ tự khai báo. | Chọn mức trừu tượng tạo đối tượng theo bài toán. | Lần theo đường từ roots đến object và chính sách retention. | Dựa trên số liệu và hợp đồng API, không kỳ vọng GC tất định. | Chủ động quản lý quyền sở hữu, cleanup và lỗi. | Khởi tạo xong, rồi kích hoạt và công bố có đồng bộ. |