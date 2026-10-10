<a id="back-to-top"></a>

# Suy luận, đánh đổi và liên hệ với các paradigm

## Menu
- [Ưu điểm của việc làm rõ ý định hơn chi tiết thao tác](#declarative-intent-benefits)
- [Chi phí khi phụ thuộc bộ đánh giá và công cụ](#declarative-engine-costs)
- [Đặc tả không đầy đủ và kết quả ngoài dự kiến](#declarative-spec-pitfalls)
- [Giới hạn điều khiển thứ tự và tác động phụ](#declarative-effect-limits)
- [Đối chiếu lời giải khai báo với điều khiển mệnh lệnh](#declarative-imperative-contrast)
- [Mối liên hệ với functional và các module công nghệ](#declarative-paradigm-handoff)

## <a id="declarative-intent-benefits">Ưu điểm của việc làm rõ ý định hơn chi tiết thao tác</a>

<details>
<summary>Xem chi tiết</summary>

Lợi ích lớn nhất là làm nổi bật tiêu chí chấp nhận kết quả. Yêu cầu “tìm mọi giao dịch trên 1 triệu của tài khoản A” dễ thẩm định hơn một đoạn mã trộn vòng lặp, điều kiện và cách ghi vào danh sách kết quả.

Bộ đánh giá cũng có thể thay đổi kế hoạch khi dữ liệu tăng mà người gọi không sửa mô tả. Giá trị này phụ thuộc điều kiện đã viết đủ rõ; một đặc tả mơ hồ không trở nên đúng chỉ vì biểu diễn ngắn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-engine-costs">Chi phí khi phụ thuộc bộ đánh giá và công cụ</a>

<details>
<summary>Xem chi tiết</summary>

Ẩn chi tiết thực thi giúp tập trung vào ý định nhưng cũng khiến việc tìm nguyên nhân chậm, tốn bộ nhớ hay kết quả khác kỳ vọng khó hơn. Người học cần biết công cụ có chiến lược, giới hạn và hành vi lỗi cụ thể, dù không trực tiếp điều khiển từng bước.

Ví dụ cùng một truy vấn có thể chạy tốt với vài trăm hàng nhưng trở nên nặng khi dữ liệu tăng lớn. Khi hiệu năng quan trọng, phải quan sát kế hoạch và tài nguyên thực tế; tính khai báo không có nghĩa chi phí bằng không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-spec-pitfalls">Đặc tả không đầy đủ và kết quả ngoài dự kiến</a>

<details>
<summary>Xem chi tiết</summary>

Một đặc tả thiếu tiêu chí sắp xếp có thể khiến danh sách hiển thị thay thứ tự giữa các lần chạy; bỏ điều kiện tài khoản có thể trả sai phạm vi truy cập; quên tính trùng lặp có thể dẫn đến tổng hợp đếm sai.

Để kiểm tra, hãy tạo ví dụ đầu vào gồm hàng trùng, giá trị rỗng, hai giao dịch cùng thời điểm và dữ liệu thuộc tài khoản khác. Nếu kết quả kỳ vọng chưa diễn đạt rõ, cần bổ sung đặc tả trước khi tối ưu bộ thực thi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-effect-limits">Giới hạn điều khiển thứ tự và tác động phụ</a>

<details>
<summary>Xem chi tiết</summary>

Một câu truy vấn đọc thuần túy khác với đặc tả có thể kích hoạt cập nhật, gửi thông báo hoặc thao tác bên ngoài. Khi tác động phụ xuất hiện, thứ tự và số lần thực hiện có thể ảnh hưởng hành vi quan sát được.

Không được suy rằng vì code nhìn giống biểu thức khai báo nên công cụ được phép chạy lại hoặc đổi thứ tự tùy ý. Phải xem hợp đồng của hệ thống: tính lặp lại, giao dịch và giới hạn tác động phụ là vấn đề riêng cần kiểm chứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-imperative-contrast">Đối chiếu lời giải khai báo với điều khiển mệnh lệnh</a>

<details>
<summary>Xem chi tiết</summary>

Với đầu vào `[10, -2, 25]`, lời giải mệnh lệnh dùng bộ cộng dồn và lặp từng số dương; mô tả khai báo nêu “tổng các số dương”. Cả hai đều có thể cho 35 nhưng thể hiện trách nhiệm khác nhau.

Khi trình tự nghiệp vụ cần kiểm tra, cập nhật và thông báo theo thứ tự chính xác, cách mệnh lệnh thường rõ hơn. Trong cùng ứng dụng, ta vẫn có thể dùng truy vấn khai báo để chọn dữ liệu rồi dùng các bước mệnh lệnh để xử lý tác động.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-paradigm-handoff">Mối liên hệ với functional và các module công nghệ</a>

<details>
<summary>Xem chi tiết</summary>

Tư duy khai báo giao với lập trình logic ở quan hệ, dữ kiện và quy tắc suy ra; với lập trình hàm ở cách ghép phép biến đổi; và với truy vấn ở mô tả kết quả. Tuy nhiên hàm thuần, giá trị bất biến hay bộ suy diễn cụ thể có phạm vi giảng dạy riêng.

Từ đây có thể học module Functional Programming để hiểu hàm và tác động phụ; học database/SQL để hiểu thực thi truy vấn; học ngôn ngữ logic để xem cơ chế tìm lời giải. Không nhập nhằng điểm giao giữa các lối viết thành định nghĩa duy nhất của paradigm.

</details>

- [Quay lại đầu trang](#back-to-top)
