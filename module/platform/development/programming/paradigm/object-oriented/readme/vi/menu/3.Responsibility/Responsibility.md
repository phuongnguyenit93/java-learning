<a id="back-to-top"></a>

# Trách nhiệm, hợp đồng và sự cộng tác giữa đối tượng

## Menu
- [Trách nhiệm của đối tượng trong một bài toán](#oop-responsibility-purpose)
- [Xác định nơi sở hữu hành vi và quyết định](#oop-behavior-owner)
- [Tính gắn kết giữa các hành vi cùng trách nhiệm](#oop-responsibility-cohesion)
- [Hợp đồng hành vi giữa đối tượng cộng tác](#oop-behavioral-contract)
- [Luồng phối hợp nhiều đối tượng cho một yêu cầu](#oop-collaboration-flow)
- [Hợp thành đối tượng từ các phần có trách nhiệm riêng](#oop-object-composition)
- [Mức độ phụ thuộc và tác động của thay đổi thiết kế](#oop-collaboration-coupling)

## <a id="oop-responsibility-purpose">Trách nhiệm của đối tượng trong một bài toán</a>

<details>
<summary>Xem chi tiết</summary>

**Trách nhiệm** chỉ rõ phần nào của hệ thống phải bảo đảm một kết quả hay quy tắc. Tài khoản chịu trách nhiệm bảo đảm rút tiền hợp lệ; bộ gửi thông báo chịu trách nhiệm chuyển thông tin tới người dùng, chứ không tự thay đổi số dư.

Chia trách nhiệm hợp lý giúp một thay đổi về giới hạn rút tiền không buộc sửa dịch vụ gửi email. Mỗi đối tượng không nhất thiết chỉ có một phương thức; các hành vi cùng phục vụ một vai trò có thể thuộc chung một trách nhiệm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-behavior-owner">Xác định nơi sở hữu hành vi và quyết định</a>

<details>
<summary>Xem chi tiết</summary>

Để xác định nơi sở hữu hành vi, hỏi **ai có đủ thông tin và quyền quyết định** mà không buộc nhiều bên phối hợp tùy tiện. Kiểm tra số dư nằm gần tài khoản hơn màn hình thanh toán; chọn cách gửi thông báo nằm gần thành phần thông báo.

Nếu một lớp điều phối vừa tự tính số dư, vừa xác thực điều kiện, vừa ghi lịch sử, nó có thể đang giành trách nhiệm của các đối tượng khác. Tuy nhiên không được chuyển mọi hành vi vào thực thể tài khoản khi hành vi đó thuộc phối hợp nhiều tài khoản.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-responsibility-cohesion">Tính gắn kết giữa các hành vi cùng trách nhiệm</a>

<details>
<summary>Xem chi tiết</summary>

**Tính gắn kết (cohesion)** thể hiện các hành vi trong một đối tượng phục vụ cùng mục đích đến đâu. `deposit`, `withdraw` và `availableBalance` cùng giải quyết quản lý tài khoản; `sendPromotionalEmail` không cùng nhóm lý do thay đổi.

Một đối tượng gom nhiều công việc không liên quan sẽ thường phải sửa vì nhiều nguyên nhân độc lập. Tách thành các trách nhiệm có gắn kết giúp hiểu và kiểm thử dễ hơn, dù tăng số đối tượng cần phối hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-behavioral-contract">Hợp đồng hành vi giữa đối tượng cộng tác</a>

<details>
<summary>Xem chi tiết</summary>

**Hợp đồng hành vi** nêu điều bên gọi phải cung cấp và điều bên nhận bảo đảm. `withdraw(amount)` có thể yêu cầu số tiền dương, thành công giảm đúng số dư hoặc thất bại giữ trạng thái nguyên vẹn, đồng thời trả lý do từ chối rõ ràng.

Hợp đồng không nên chỉ ghi kiểu dữ liệu đầu vào/đầu ra; hai cách triển khai cùng nhận số nhưng một cách bỏ qua kiểm tra số dư không thể thay thế hợp lệ cho cách kia. Hợp đồng là nền tảng của cộng tác và đa hình ở các chương sau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-collaboration-flow">Luồng phối hợp nhiều đối tượng cho một yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

Một ca thanh toán có thể đi theo luồng: đối tượng điều phối yêu cầu tài khoản xác nhận/rút tiền, nhận kết quả rồi yêu cầu đối tượng thông báo gửi biên nhận. Mỗi bên thực hiện trách nhiệm của mình; điều phối không tự sửa trường nội bộ của cộng tác viên.

Vết cộng tác nên nêu thông điệp, kết quả và nhánh thất bại: nếu rút tiền bị từ chối, không được gửi biên nhận thành công. Luồng yêu cầu–phản hồi này là trọng tâm OOP ở cấp mô hình, không cần sơ đồ class cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-object-composition">Hợp thành đối tượng từ các phần có trách nhiệm riêng</a>

<details>
<summary>Xem chi tiết</summary>

**Hợp thành (composition)** cho phép một đối tượng sử dụng các cộng tác viên có trách nhiệm riêng để tạo hành vi lớn hơn. Một quy trình thanh toán có thể phối hợp `Account`, `FeePolicy` và `Notifier` mà không cần coi chúng là các kiểu con của cùng một loại.

Thay chính sách phí có thể chỉ cần thay đối tượng chính sách đáp ứng cùng hợp đồng. Dù vậy cấu trúc gồm quá nhiều lớp trung gian chỉ chuyển tiếp lời gọi lại tạo thêm chi phí hiểu và kiểm thử; hợp thành có ý nghĩa khi ranh giới trách nhiệm thực sự hữu ích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-collaboration-coupling">Mức độ phụ thuộc và tác động của thay đổi thiết kế</a>

<details>
<summary>Xem chi tiết</summary>

**Mức độ phụ thuộc (coupling)** mô tả một đối tượng cần biết bao nhiêu về đối tượng khác. Nếu thanh toán biết cả tên trường số dư, định dạng lưu trữ và cách ghi lịch sử của tài khoản, bất kỳ thay đổi nhỏ nào cũng dễ lan rộng.

Khi chỉ biết hợp đồng `withdraw`, thanh toán phụ thuộc vào hành vi công bố, không lệ thuộc cấu trúc nội bộ. Tuy nhiên hợp đồng quá mơ hồ có thể che giấu lỗi; mục tiêu là phụ thuộc vào lời hứa đủ chính xác chứ không xóa mọi liên kết.

</details>

- [Quay lại đầu trang](#back-to-top)
