# QUAN HỆ — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Liên kết giữa các đối tượng

Xác định link giữa các object và cách thay đổi association, đồng thời phân biệt hướng truy cập, bội số với ownership.

↓ **Từ link giữa object → phân biệt liên kết nghiệp vụ với quan hệ sử dụng cộng tác viên**

### Bài 2 — Sự phụ thuộc tạm thời và liên kết lâu dài

Phân biệt cộng tác viên được dùng trong một thao tác với liên kết được lưu thành trạng thái; chọn tham số, field hoặc injection theo nhu cầu.

↓ **Từ kiểu quan hệ sử dụng → làm rõ nghĩa sở hữu và vòng đời của toàn thể–bộ phận**

### Bài 3 — Tập hợp và hợp thành

Thiết kế whole–part dựa vào tính độc lập và chủ sở hữu của phần tử; bảo vệ vòng đời Team–Employee và Order–LineItem.

↓ **Từ ownership của whole–part → xác định ràng buộc số lượng giữa các đầu**

### Bài 4 — Số lượng: một-một, một-nhiều và nhiều-nhiều

Đọc multiplicity ở cả hai đầu và áp ràng buộc 1–1, 1–N, N–N qua API, registry hoặc đối tượng Enrollment.

↓ **Từ bội số mỗi đầu → quyết định chiều truy cập nào thật sự cần trong object graph**

### Bài 5 — Hướng liên kết và khả năng điều hướng

Chỉ lưu chiều liên kết thực sự cần truy cập; dùng repository khi thích hợp và quản lý chi phí của liên kết hai chiều.

↓ **Từ lựa chọn hướng truy cập → bảo vệ invariant khi phải lưu cả hai hướng**

### Bài 6 — Quan hệ hai chiều và giữ dữ liệu nhất quán

Duy trì một sự thật nhất quán giữa `Employee.department` và `Department.employees` qua mọi thao tác, cả trong bộ nhớ lẫn JPA.

↓ **Từ giữ nhất quán từng liên kết → nhận diện coupling và vòng phụ thuộc giữa các thành phần**

### Bài 7 — Vòng phụ thuộc và cách phá vòng

Phân biệt vòng tham chiếu hợp lệ với chu trình phụ thuộc gây lỗi; chuyển orchestration và dependency sang các ranh giới có trách nhiệm rõ.

↓ **Từ cấu trúc quan hệ đã thiết kế → đọc và kiểm tra lại bằng ký hiệu UML**

### Bài 8 — Đọc ký hiệu quan hệ trong sơ đồ lớp

Tổng hợp sáu ký hiệu quan hệ và các nhãn UML để đọc sơ đồ, kiểm tra việc biểu diễn association, ownership, multiplicity và contract trong Java.

**Mối liên hệ:** Từ liên kết giữa từng cặp object, chương lần lượt phân biệt dependency và ownership, đặt ràng buộc bội số rồi lựa chọn đường điều hướng trong object graph. Khi cần điều hướng hai chiều, một invariant và cổng cập nhật thống nhất phải bảo vệ các tham chiếu tương ứng. Ở cấp thiết kế lớn hơn, sơ đồ phụ thuộc cần tránh chu trình không cần thiết. Bài UML cuối cùng gom các quyết định này thành một cách đọc và kiểm tra mô hình nhất quán.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Liên kết giữa các đối tượng | Sự phụ thuộc tạm thời và liên kết lâu dài | Tập hợp và hợp thành | Số lượng: một-một, một-nhiều và nhiều-nhiều | Hướng liên kết và khả năng điều hướng | Quan hệ hai chiều và giữ dữ liệu nhất quán | Vòng phụ thuộc và cách phá vòng | Đọc ký hiệu quan hệ trong sơ đồ lớp |
| Vấn đề cần hiểu | Association nối object nào, đổi link ra sao? | Phụ thuộc trong một thao tác khác association lưu trạng thái thế nào? | Khi nào part thuộc riêng một whole? | 1–1, 1–N, N–N ràng buộc số lượng nào? | Đứng ở một object có thể đi tới đầu kia theo chiều nào? | Làm sao giữ hai chiều cùng phản ánh một quan hệ khi có thay đổi/lỗi? | Vòng thiết kế, wiring, runtime xuất hiện và được phá thế nào? | Mỗi loại nét, đầu mũi tên, hình thoi, bội số trong UML nghĩa là gì? |
| Giá trị đối với OOP/module | Mô hình đúng quan hệ instance, API phân công/chuyển/gỡ. | Tránh nhầm dependency dịch vụ với trạng thái và vòng đời nghiệp vụ. | Bảo vệ ownership và tránh xóa nhầm phần tử chia sẻ. | Cưỡng chế số lượng, duy nhất và luật của liên kết trung gian. | Giảm trạng thái lưu dư và tránh thêm chiều liên kết vô cớ. | Ngăn object graph/JPA lệch dữ liệu hoặc cập nhật nửa chừng. | Giảm coupling và lỗi khởi tạo, giữ luồng nghiệp vụ kiểm thử được. | Đọc và truyền đạt mô hình lớp chính xác trước khi viết code. |
| Tư duy hình thành | Hỏi object nào thực sự liên kết tại thời điểm chạy. | Hỏi cần một công cụ để làm việc hay một quan hệ phải được lưu. | Hỏi ai là chủ và part có tồn tại độc lập không. | Hỏi mỗi A có bao nhiêu B và mỗi B có bao nhiêu A. | Hỏi truy vấn cần đi từ phía nào và có cần field ngược không. | Hỏi invariant hai phía và ranh giới cập nhật là gì. | Vẽ đồ thị cạnh có hướng, tìm trách nhiệm sai và kiểm tra sau refactor. | Đọc kết hợp nét, đầu đường, bội số, role và đối chiếu với Java. |