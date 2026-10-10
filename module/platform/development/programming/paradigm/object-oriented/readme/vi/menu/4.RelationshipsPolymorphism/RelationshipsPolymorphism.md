<a id="back-to-top"></a>

# Quan hệ kiểu, kế thừa và đa hình trong mô hình đối tượng

## Menu
- [Đa hình: Khái niệm và động cơ thay đổi hành vi](#oop-polymorphism-purpose)
- [Hợp đồng chung cho nhiều kiểu thực hiện](#oop-shared-behavior-contract)
- [Quan hệ kiểu và lời hứa của kiểu con](#oop-subtype-relation)
- [Kế thừa như một cách tổ chức và tái sử dụng](#oop-inheritance-concept)
- [Khả năng thay thế và các kỳ vọng hành vi](#oop-substitutability)
- [Lựa chọn hợp thành đối tượng hay quan hệ kế thừa](#oop-composition-vs-inheritance)
- [Mở rộng biến thể hành vi qua đa hình](#oop-behavior-variants)

## <a id="oop-polymorphism-purpose">Đa hình: Khái niệm và động cơ thay đổi hành vi</a>

<details>
<summary>Xem chi tiết</summary>

**Đa hình (polymorphism)** cho phép bên gọi gửi cùng một yêu cầu theo hợp đồng, còn các đối tượng cụ thể thực hiện hành vi phù hợp với mình. Chẳng hạn `pay(amount)` có thể được xử lý bằng ví điện tử hoặc chuyển khoản với cách thực hiện khác nhau.

Điểm có giá trị là bên gọi không phải trải nhiều nhánh “nếu là ví điện tử thì...”. Nhưng các lựa chọn vẫn phải bảo đảm điều đã hứa, không thể viện lý do triển khai khác để âm thầm phá quy tắc thanh toán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-shared-behavior-contract">Hợp đồng chung cho nhiều kiểu thực hiện</a>

<details>
<summary>Xem chi tiết</summary>

Một hợp đồng thanh toán chung có thể nêu: nhận số tiền dương, trả thành công kèm mã xác nhận hoặc thất bại kèm lý do, không được thông báo thành công khi chưa xử lý xong. Các đối tượng khác nhau có thể triển khai nội bộ khác nhau.

Hợp đồng nên mô tả hành vi từ góc nhìn người sử dụng, gồm kết quả và lỗi quan sát được, thay vì bắt buộc mọi đối tượng có cùng trường dữ liệu. Đây là nền tảng để bổ sung biến thể mà bên gọi ít phải thay đổi.

| Yêu cầu và tình huống | Kết quả quan sát theo hợp đồng |
| --- | --- |
| Số tiền dương, thanh toán hoàn tất | Xác nhận có mã hợp lệ |
| Số tiền dương, nhà cung cấp từ chối hoặc lỗi | Kết quả thất bại kèm lý do |
| Số tiền bằng 0 hoặc âm | Kết quả đầu vào không hợp lệ theo quy ước |

Số tiền dương là đầu vào **được tiếp nhận để xử lý**, không phải lời hứa chắc chắn thanh toán thành công. Nhà cung cấp từ chối vì lý do thực tế vẫn là kết quả thất bại hợp lệ; báo thành công giả hoặc ném ngoại lệ ngoài hợp đồng thay vì trả lỗi tường minh thì không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-subtype-relation">Quan hệ kiểu và lời hứa của kiểu con</a>

<details>
<summary>Xem chi tiết</summary>

**Kiểu con (subtype)** là một kiểu cam kết có thể được sử dụng tại nơi yêu cầu hợp đồng của kiểu rộng hơn. Nếu `WalletPayment` là kiểu con của `PaymentMethod`, đối tượng ví phải đáp ứng các kỳ vọng mà bên gọi đặt vào phương thức thanh toán.

Quan hệ “có cùng vài phương thức” chưa đủ để thiết kế kiểu con đúng. Giả sử hợp đồng chung tiếp nhận mọi số tiền dương **để xử lý**, rồi trả thành công hoặc thất bại tường minh. Kiểu con tự ném ngoại lệ `unsupported amount` ngoài hợp đồng cho một yêu cầu dương sẽ phá cam kết. Ngược lại, nhà cung cấp từ chối thanh toán và trả đúng kết quả thất bại theo hợp đồng **không phải** vi phạm khả năng thay thế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-inheritance-concept">Kế thừa như một cách tổ chức và tái sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

**Kế thừa (inheritance)** là cơ chế tổ chức hoặc tái sử dụng đặc điểm giữa các kiểu, thường tạo quan hệ kiểu trong ngôn ngữ hỗ trợ. Nhưng sao chép hành vi chung và bảo đảm khả năng thay thế là hai mục tiêu khác nhau.

Ví dụ kiểu thanh toán con tái sử dụng đoạn mã tính phí từ kiểu cha nhưng thay đổi điều kiện chấp nhận tiền có thể phá hợp đồng. Do đó không nên chọn kế thừa chỉ vì tránh trùng một vài dòng mã; cần xét quan hệ bản chất và rủi ro ảnh hưởng chéo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-substitutability">Khả năng thay thế và các kỳ vọng hành vi</a>

<details>
<summary>Xem chi tiết</summary>

**Khả năng thay thế (substitutability)** yêu cầu dùng đối tượng của kiểu con mà không phá kỳ vọng hợp lý dành cho hợp đồng chung. Nếu mọi phương thức thanh toán đều hứa trả trạng thái thất bại rõ ràng khi không trả được, một triển khai không được giả vờ thành công.

Để kiểm tra, chạy cùng bộ tình huống hợp đồng trên các biến thể: số tiền hợp lệ, không hợp lệ, lỗi dịch vụ và xử lý kết quả. Kế thừa trong cấu trúc mã không phải bằng chứng đủ cho khả năng thay thế về hành vi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-composition-vs-inheritance">Lựa chọn hợp thành đối tượng hay quan hệ kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

**Hợp thành** phù hợp khi một đối tượng **có** một chính sách hoặc dịch vụ cộng tác; **kế thừa** chỉ hợp lý khi quan hệ kiểu con thật sự đáp ứng hợp đồng kiểu cha. Thanh toán **có** chính sách tính phí; chính sách không nhất thiết là một loại thanh toán.

Chọn hợp thành thường dễ thay cộng tác viên mà không làm mọi lớp con phụ thuộc vào thay đổi ở lớp cha. Nhưng thêm quá nhiều lớp cũng có chi phí; hãy cân nhắc hợp đồng ổn định và lợi ích thay đổi theo từng trách nhiệm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-behavior-variants">Mở rộng biến thể hành vi qua đa hình</a>

<details>
<summary>Xem chi tiết</summary>

Khi hệ thống bổ sung chuyển khoản nhanh, bên gọi có thể tiếp tục sử dụng hợp đồng thanh toán chung. Biến thể mới cần hiện thực hành vi của nó và vượt qua các kiểm thử hợp đồng đã có, thay vì buộc sửa tất cả nơi xử lý.

Đa hình phù hợp với những điểm **thực sự cần thay đổi cách hành xử**. Nếu bài toán chỉ có một phép tính đơn giản không có biến thể hay trạng thái độc lập, việc tạo một hệ phân cấp đối tượng lớn có thể phức tạp hơn thủ tục thông thường.

</details>

- [Quay lại đầu trang](#back-to-top)
