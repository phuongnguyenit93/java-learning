# MÔ HÌNH HÓA — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Thu thập tình huống sử dụng và quy tắc nghiệp vụ

Thu thập actor/use case, rule có BR-ID và phản ví dụ để tìm invariant trước khi thiết kế lớp.

↓ **Từ biết mục tiêu và quy tắc → nhận diện các đối tượng có danh tính, vòng đời và trách nhiệm phù hợp**

### Bài 2 — Nhận diện đối tượng từ yêu cầu

Từ mô tả thư viện nhận diện entity, value object, policy và service; tách đối tượng theo danh tính, trách nhiệm, vòng đời.

↓ **Từ danh sách đối tượng ứng viên → xác định chủ sở hữu của mỗi invariant và những người cộng tác**

### Bài 3 — Phân chia trách nhiệm và đối tượng cộng tác

Phân công trách nhiệm và xác định thông điệp cộng tác giữa `Member`, `BookCopy`, `Loan` và `LendingService`.

↓ **Từ phân công trách nhiệm → diễn tập qua các thẻ CRC để kiểm tra các kịch bản và chỗ thiếu nghĩa vụ**

### Bài 4 — Thẻ CRC: lớp, trách nhiệm và cộng tác

Role-play bằng thẻ CRC, phát hiện thiếu hoặc trùng trách nhiệm, rồi chuyển sang API/UML/test.

↓ **Từ thẻ trách nhiệm → mô tả cấu trúc class, API, quan hệ và bội số bằng ký pháp rõ ràng**

### Bài 5 — Sơ đồ lớp: thành viên và quan hệ

Biểu diễn cấu trúc tĩnh và ownership bằng UML class diagram: thành viên, quan hệ, bội số và hướng phụ thuộc.

↓ **Từ cấu trúc ở mức lớp → kiểm tra đồ thị các instance cụ thể tại một thời điểm**

### Bài 6 — Sơ đồ đối tượng: các thể hiện tại một thời điểm

Xem snapshot t0/t1 của các object và link cụ thể, nhận ra aliasing và khác biệt với class diagram.

↓ **Từ snapshot các object → theo dõi thông điệp giữa chúng trong một scenario**

### Bài 7 — Sơ đồ tuần tự: thứ tự tương tác

Mô tả thứ tự lời gọi giữa các participant, các nhánh lỗi và thời điểm có side effect bằng sequence diagram.

↓ **Từ thứ tự tương tác → làm rõ sự kiện nào được phép đổi trạng thái của một đối tượng**

### Bài 8 — Sơ đồ trạng thái: chuyển trạng thái hợp lệ

Định nghĩa event, guard và transition hợp lệ cho một object theo vòng đời, từ chối cạnh sai mà không đổi state.

↓ **Từ các sơ đồ thiết kế → đối chiếu implementation, test và nghiệp vụ thực tế để phát hiện sai lệch**

### Bài 9 — Đối chiếu mô hình với mã và tình huống thực tế

Đối chiếu yêu cầu, mô hình, Java, test và dữ liệu chạy; bảo đảm business rule tiếp tục đúng khi đổi mã hoặc môi trường.

**Mối liên hệ:** Một vòng mô hình hóa OOP đi từ xác nhận tình huống sử dụng và business rule đến xác định đối tượng, phân vai, diễn tập cộng tác và mô tả bằng các sơ đồ bổ trợ. Class diagram giải thích cấu trúc, object diagram cho ảnh chụp instance, sequence diagram theo dõi thông điệp, state diagram bảo vệ vòng đời; bước đối chiếu cuối cùng phải kiểm chứng từng bất biến bằng Java, test và dữ liệu thực tế. Các kỹ thuật sơ đồ phản ánh những góc nhìn bổ trợ, không bắt buộc luôn áp dụng theo một quy trình tuyến tính cố định.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 | Bài 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Thu thập tình huống sử dụng và quy tắc nghiệp vụ | Nhận diện đối tượng từ yêu cầu | Phân chia trách nhiệm và đối tượng cộng tác | Thẻ CRC: lớp, trách nhiệm và cộng tác | Sơ đồ lớp: thành viên và quan hệ | Sơ đồ đối tượng: các thể hiện tại một thời điểm | Sơ đồ tuần tự: thứ tự tương tác | Sơ đồ trạng thái: chuyển trạng thái hợp lệ | Đối chiếu mô hình với mã và tình huống thực tế |
| Vấn đề cần hiểu | Ai đạt mục tiêu gì theo điều kiện nào? | Khái niệm nào xứng đáng thành object? | Đối tượng nào sở hữu rule và nhờ ai? | Diễn tập trách nhiệm qua thẻ thế nào? | Lớp có thành viên/quan hệ/bội số gì? | Instance/link nào đang tồn tại tại t0/t1? | Ai gọi ai, theo thứ tự nào? | Event nào cho phép đổi state? | Mô hình và code có thực thi đúng BR? |
| Giá trị đối với OOP/module | Xác lập use case, invariant, acceptance criteria | Phân biệt entity/VO/service và vòng đời | Giữ logic đúng owner và luồng cộng tác | Kiểm tra phân vai bằng role-play | Làm rõ cấu trúc và quyền sở hữu | Minh họa aliasing và snapshot | Ngăn side effect sai thứ tự | Bảo vệ chuyển trạng thái nghiệp vụ | Truy vết rule qua API, test và persistence |
| Tư duy hình thành | Phỏng vấn và biến yêu cầu mơ hồ thành luật kiểm chứng được | Phản biện class ứng viên bằng danh tính và trách nhiệm | Giao việc nơi có thông tin, giảm coupling | Thử các luồng và sửa thẻ trước khi code | Đọc ký pháp, bội số và chọn đúng quan hệ | Phân biệt loại, instance và dữ liệu tại một thời điểm | Xét thứ tự message và nhánh thất bại | Xét trạng thái, event, guard, effect | Kiểm chứng hai chiều và cập nhật đồng bộ theo thay đổi |