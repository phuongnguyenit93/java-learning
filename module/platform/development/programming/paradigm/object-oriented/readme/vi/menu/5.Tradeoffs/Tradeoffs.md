<a id="back-to-top"></a>

# Đánh đổi thiết kế hướng đối tượng và phạm vi áp dụng

## Menu
- [Thiết kế ví dụ từ yêu cầu đến nhóm đối tượng cộng tác](#oop-design-walkthrough)
- [Lợi ích và chi phí của ranh giới đóng gói](#oop-encapsulation-benefits)
- [Lạm dụng đối tượng và phân tán trách nhiệm](#oop-overdesign-risks)
- [Quan hệ kế thừa mong manh và vi phạm hợp đồng](#oop-fragile-inheritance)
- [Rủi ro chia sẻ trạng thái thay đổi giữa các đối tượng](#oop-shared-state-risks)
- [Trường hợp thủ tục hoặc phép biến đổi dữ liệu đơn giản hơn](#oop-alternative-paradigms)
- [Ranh giới với Java Core, DDD, AOP và các paradigm khác](#oop-owner-handoff)

## <a id="oop-design-walkthrough">Thiết kế ví dụ từ yêu cầu đến nhóm đối tượng cộng tác</a>

<details>
<summary>Xem chi tiết</summary>

Bắt đầu từ yêu cầu: chuyển 30 từ tài khoản A có 100 sang B có 20 và báo kết quả. Ta xác định tài khoản sở hữu quy tắc số dư, quy trình chuyển tiền chịu trách nhiệm điều phối hai tài khoản, và bộ thông báo chỉ truyền kết quả.

Luồng thành công gồm kiểm tra, ghi nợ A còn 70, ghi có B thành 50 và thông báo. Nếu bước ghi có thất bại sau ghi nợ, đó là vấn đề bảo đảm tính nhất quán xuyên nhiều đối tượng; cần chính sách xử lý và cơ chế giao dịch riêng, không tự động do OOP cung cấp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-encapsulation-benefits">Lợi ích và chi phí của ranh giới đóng gói</a>

<details>
<summary>Xem chi tiết</summary>

Đóng gói giúp đổi cách lưu số dư mà không yêu cầu mọi bên gọi sửa, miễn hợp đồng rút/nạp vẫn đúng. Nó cũng tạo một nơi bảo vệ điều kiện nhất quán của đối tượng và khoanh vùng lỗi khi kết quả bất hợp lệ xuất hiện.

Đổi lại, việc kiểm tra hành vi có thể cần gọi qua đối tượng thay vì quan sát cấu trúc tự do; quá nhiều ranh giới mỏng khiến việc theo dõi đường đi khó hơn. Chỉ che dữ liệu khi điều đó bảo vệ một trách nhiệm hoặc giảm phụ thuộc có ý nghĩa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-overdesign-risks">Lạm dụng đối tượng và phân tán trách nhiệm</a>

<details>
<summary>Xem chi tiết</summary>

Nếu mỗi phép cộng nhỏ được bọc trong ba lớp điều phối và nhiều đối tượng chỉ chuyển tiếp lời gọi, chương trình có thể phức tạp hơn bài toán. Dấu hiệu khác là một đối tượng `EverythingManager` xử lý tài khoản, thanh toán, gửi thư và báo cáo.

Hãy xét liệu các ranh giới đối tượng có giúp bảo vệ điều kiện nhất quán, cô lập thay đổi hoặc mở rộng hành vi thật hay không. Khi không có trách nhiệm riêng đáng kể, một hàm thuần hoặc thủ tục rõ ràng có thể tốt hơn cả cây lớp lớn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-fragile-inheritance">Quan hệ kế thừa mong manh và vi phạm hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

Cây kế thừa mong manh xảy ra khi sửa hành vi của kiểu cha vô tình thay đổi nhiều kiểu con phụ thuộc vào chi tiết đó. Ví dụ kiểu cha thêm phí mới nhưng một kiểu con đã giả định phí luôn bằng 0, khiến kết quả sai mà bên gọi không biết.

Tình huống nguy hiểm hơn là kiểu con bỏ qua hợp đồng của kiểu cha, như trả trạng thái thành công khi thanh toán thực sự thất bại. Kiểm thử hợp đồng chung và ưu tiên hợp thành khi quan hệ “là một loại” không bền vững giúp giảm rủi ro.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-shared-state-risks">Rủi ro chia sẻ trạng thái thay đổi giữa các đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

Hai đối tượng cùng giữ tham chiếu tới một dữ liệu có thể thay đổi sẽ khó dự đoán nếu bên này chỉnh nó ngoài hợp đồng của bên kia. Ví dụ báo cáo đọc số dư từ dữ liệu tài khoản mà dịch vụ khác âm thầm sửa có thể tạo kết quả không nhất quán.

Giới hạn đường cập nhật, dùng bản sao giá trị khi phù hợp và kiểm tra điều kiện nhất quán sau thao tác là các lựa chọn thiết kế. Tuy nhiên việc chia sẻ trạng thái giữa các luồng hoặc hệ thống khác nhau đòi hỏi cơ chế đồng bộ và giao dịch riêng, không phải cứ áp dụng OOP là tự giải quyết được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-alternative-paradigms">Trường hợp thủ tục hoặc phép biến đổi dữ liệu đơn giản hơn</a>

<details>
<summary>Xem chi tiết</summary>

Nếu mục tiêu chỉ tính tổng khoản thu từ danh sách bất biến, một hàm nhận danh sách và trả số có thể rõ ràng hơn việc tạo nhiều đối tượng giữ trạng thái. Nếu công việc chủ yếu là tuần tự đọc–tính–in, nhóm thủ tục nhỏ cũng đủ.

Chọn mô hình đối tượng khi có danh tính, quy tắc trạng thái và trách nhiệm cộng tác đáng để bảo vệ. Lối mệnh lệnh, hàm và khai báo có thể hiện diện bên trong một thiết kế OOP; lựa chọn hợp lý không phải cam kết “chỉ được dùng đối tượng”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-owner-handoff">Ranh giới với Java Core, DDD, AOP và các paradigm khác</a>

<details>
<summary>Xem chi tiết</summary>

Module này dừng ở **mô hình trách nhiệm và hợp đồng đối tượng**. Java Core dạy chi tiết `class`, `interface`, quyền truy cập và quy tắc ghi đè; DDD dạy mô hình miền, ranh giới aggregate và hành vi nghiệp vụ quy mô lớn; AOP dạy mối quan tâm xuyên suốt nhiều đối tượng.

Khi một lỗi đòi hỏi hiểu cách Java lựa chọn phương thức cụ thể, chuyển sang module ngôn ngữ. Khi cần điều phối giao dịch hoặc đồng bộ trạng thái, học thêm về kiến trúc, giao dịch và xử lý đồng thời; OOP chỉ giúp phân chia trách nhiệm chứ không tạo bảo đảm vận hành tự động.

</details>

- [Quay lại đầu trang](#back-to-top)
