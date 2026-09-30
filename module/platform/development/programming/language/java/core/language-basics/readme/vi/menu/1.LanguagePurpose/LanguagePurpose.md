# Bức tranh tổng thể về nền tảng ngôn ngữ Java

## <a id="language-basics-roadmap">Nền tảng ngôn ngữ Java học gì và để làm gì?</a>

Một chương trình Java không chỉ là tập hợp các câu lệnh đúng cú pháp. Để đọc và viết chương trình có chủ đích, người học cần hiểu Java **biểu diễn giá trị, gắn kiểu cho dữ liệu, tổ chức tên, điều khiển luồng thực thi và truyền dữ liệu giữa các phương thức** như thế nào.

Mô-đun này tồn tại để xây dựng nền móng đó trước khi đi vào các chủ đề lớn hơn như lập trình hướng đối tượng, Generics, Collections hay cơ chế thời điểm chạy. Nếu bỏ qua nền tảng này, người học rất dễ nhớ từng cú pháp riêng lẻ nhưng khó giải thích vì sao trình biên dịch chấp nhận một dòng mã và từ chối dòng gần giống nó.

Các chặng học kết nối theo câu chuyện sau:

```text
Một chương trình Java được tổ chức ra sao?
        ↓
Java thao tác trên những loại giá trị nào?
Kiểu nguyên thủy / Kiểu tham chiếu
        ↓
Biến tồn tại ở đâu và nhìn thấy trong phạm vi nào?
Biến / Phạm vi / Vòng đời
        ↓
Một tham chiếu không trỏ tới đối tượng nào nghĩa là gì?
null
        ↓
Giá trị nguyên thủy tham gia API dựa trên đối tượng bằng cách nào?
Kiểu bao / Đóng hộp / Mở hộp
        ↓
Giá trị được kết hợp và chuyển đổi kiểu ra sao?
Biểu thức / Toán tử / Ép kiểu
        ↓
Chương trình lựa chọn đường thực thi như thế nào?
Luồng điều khiển
        ↓
Java biểu diễn một dãy có kích thước cố định ra sao?
Mảng
        ↓
Hành vi được đóng gói và dữ liệu được truyền qua lời gọi thế nào?
Phương thức / `varargs` / Truyền bằng giá trị
        ↓
Tên kiểu được tổ chức và phân giải ra sao?
package / import
        ↓
Điều gì được quyết định khi biên dịch và điều gì còn phải kiểm tra khi chạy?
Ranh giới của hệ thống kiểu
        ↓
Ghép tất cả thành một mô hình tư duy xuyên suốt
```

Đến cuối mô-đun, người học cần giải thích được chuỗi quan hệ từ **giá trị → biến → biểu thức → luồng điều khiển → mảng → lời gọi phương thức → tổ chức tên → kiểm tra kiểu** thay vì xem chúng như các chương độc lập.
