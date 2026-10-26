# TƯ DUY OOP — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — OOP là gì và bài toán tổ chức chương trình

Nhận diện vấn đề tổ chức hệ thống khi dữ liệu, hành vi và quy tắc phát triển; dùng BankAccount và luồng checkout để thấy giá trị của ranh giới trách nhiệm.

↓ **Từ nhu cầu tổ chức hệ thống → phân tích thành phần nhỏ nhất phải chịu trách nhiệm và giữ trạng thái hợp lệ.**

### Bài 2 — Đối tượng, trạng thái, hành vi và trách nhiệm

Đi sâu vào một Order cụ thể: trạng thái, invariant, hành vi hợp lệ và trách nhiệm bảo vệ chúng qua vòng đời.

↓ **Từ bản chất object → đọc chính xác class, type, instance và tham chiếu trong mã Java.**

### Bài 3 — Lớp, kiểu và thể hiện: các khái niệm nền tảng

Phân biệt định nghĩa class, type nhìn từ compiler, instance ở runtime và biến tham chiếu để dự đoán những đối tượng thực sự được tạo ra.

↓ **Từ một object riêng lẻ → xem các object trao đổi lời gọi để hoàn thành nghiệp vụ.**

### Bài 4 — Tương tác giữa đối tượng và lời gọi phương thức

Theo dõi người gọi, receiver, argument, kết quả và exception trong luồng mượn sách khi nhiều đối tượng phối hợp.

↓ **Từ lời gọi có hợp đồng → học những cơ chế bảo vệ và mở rộng cách thực hiện hợp đồng đó.**

### Bài 5 — Đóng gói, trừu tượng, kế thừa và đa hình

Nhận diện bốn công cụ thiết kế qua Cart và DiscountPolicy; phân tích hợp đồng chung, trạng thái được bảo vệ và hành vi được chọn lúc chạy.

↓ **Từ các công cụ OOP → đối chiếu với cách tổ chức bằng hàm để hiểu lợi ích theo từng yêu cầu.**

### Bài 6 — OOP và lập trình thủ tục: cách phân chia bài toán

So sánh hai cách tổ chức cùng một phép tính giỏ hàng 405.000 đồng, đánh giá trách nhiệm, cohesion, coupling và nhu cầu thay đổi.

↓ **Từ tổ chức trách nhiệm trong Java → mở rộng cách nhìn sang một mô hình OOP dùng nguyên mẫu.**

### Bài 7 — OOP dựa trên lớp và OOP dựa trên nguyên mẫu

Đối chiếu class/method dispatch của Java với object/prototype chain của JavaScript, gồm shadowing và dữ liệu mutable dùng chung.

↓ **Từ các biến thể mô hình OOP → lựa chọn mức thiết kế cần thiết dựa trên tình huống thật.**

### Bài 8 — Khi nên và không nên sử dụng OOP

Dùng các tiêu chí trạng thái, invariant, biến thể hành vi và độ phức tạp để chọn object, hàm hoặc sự kết hợp phù hợp với bài toán.

**Mối liên hệ:** Chương bắt đầu bằng nhu cầu tổ chức hệ thống rồi đi từ đối tượng và cơ chế Java tới tương tác, bốn công cụ OOP và các lựa chọn kiến trúc. Việc so sánh với thủ tục và nguyên mẫu làm rõ giới hạn của một cách cài đặt; bài cuối giúp quyết định khi nào vận dụng OOP thực sự có lợi.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | OOP là gì và bài toán tổ chức chương trình | Đối tượng, trạng thái, hành vi và trách nhiệm | Lớp, kiểu và thể hiện: các khái niệm nền tảng | Tương tác giữa đối tượng và lời gọi phương thức | Đóng gói, trừu tượng, kế thừa và đa hình | OOP và lập trình thủ tục: cách phân chia bài toán | OOP dựa trên lớp và OOP dựa trên nguyên mẫu | Khi nên và không nên sử dụng OOP |
| Vấn đề cần hiểu | Vì sao cần OOP để tổ chức hệ thống? | Object giữ trạng thái và quy tắc thế nào? | Class, type, instance và reference khác nhau? | Các object phối hợp và truyền kết quả/lỗi thế nào? | Bốn cơ chế bảo vệ và mở rộng hành vi ra sao? | Chia bài toán theo hàm hay trách nhiệm đối tượng? | Method được tìm qua class hay prototype chain? | Khi nào OOP đáng chi phí thiết kế? |
| Giá trị đối với OOP/module | Đặt nền tảng ranh giới nghiệp vụ | Xây object duy trì invariant | Đọc chính xác mô hình Java runtime | Thiết kế collaboration và method contract | Áp dụng encapsulation, abstraction, inheritance, polymorphism | Đánh giá cohesion và coupling giữa các cách tổ chức | Hiểu mô hình OOP khác ngoài Java | Tránh overengineering và chọn abstraction có ích |
| Tư duy hình thành | Từ chức năng rời rạc sang trách nhiệm | Từ dữ liệu sang hành vi bảo vệ dữ liệu | Từ tên biến sang identity và type | Từ lời gọi đơn lẻ sang chuỗi cộng tác | Từ cơ chế cú pháp sang hợp đồng thiết kế | Từ sở thích paradigm sang đánh đổi | Từ class cố định sang delegation động | Từ mô hình có thể dùng sang mô hình nên dùng |