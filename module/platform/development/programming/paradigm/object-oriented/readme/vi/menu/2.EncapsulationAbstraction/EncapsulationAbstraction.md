<a id="back-to-top"></a>

# Danh tính, trạng thái và ranh giới đóng gói đối tượng

## Menu
- [Danh tính của đối tượng và sự khác biệt với giá trị](#oop-object-identity)
- [Trạng thái và hành vi có liên quan nhưng không luôn thay đổi](#oop-state-behavior)
- [Đóng gói: Bảo vệ trạng thái nội bộ qua hành vi](#oop-encapsulation-purpose)
- [Điều kiện bất biến của đối tượng khi thực hiện hành vi](#oop-object-invariants)
- [Điều kiện nhất quán và dữ liệu không thể thay đổi](#oop-invariant-vs-immutability)
- [Ranh giới hành vi công bố và chi tiết bị che giấu](#oop-behavior-boundary)
- [Trừu tượng hóa theo điều đối tượng có thể làm](#oop-abstraction-value)

## <a id="oop-object-identity">Danh tính của đối tượng và sự khác biệt với giá trị</a>

<details>
<summary>Xem chi tiết</summary>

**Danh tính đối tượng** trả lời “đây là thực thể nào?”, còn **bằng nhau về giá trị** trả lời “nội dung đang giống nhau đến đâu?”. Hai tài khoản cùng số dư 100 không phải một tài khoản; lịch sử và quyền truy cập có thể khác nhau.

Ngược lại, hai giá trị biểu diễn cùng một khoản tiền `100 VND` có thể được coi là tương đương theo nội dung dù được tạo ở hai nơi. Chọn danh tính hay giá trị làm trọng tâm tùy ý nghĩa bài toán, không thể suy từ cú pháp tạo đối tượng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-state-behavior">Trạng thái và hành vi có liên quan nhưng không luôn thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

Trạng thái là dữ liệu liên quan tới một đối tượng, còn hành vi là những yêu cầu đối tượng có thể đáp ứng. Tài khoản có số dư và hành vi rút tiền; hành vi kiểm tra `canWithdraw` có thể chỉ đọc trạng thái, không thay đổi gì.

Đối tượng không buộc phải cho phép sửa: giá trị địa chỉ hoặc chính sách phí có thể được tạo một lần và cung cấp hành vi tính toán. Đánh đồng OOP với dữ liệu luôn thay đổi khiến ta bỏ qua các thiết kế đối tượng bất biến hữu ích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-encapsulation-purpose">Đóng gói: Bảo vệ trạng thái nội bộ qua hành vi</a>

<details>
<summary>Xem chi tiết</summary>

**Đóng gói (encapsulation)** tạo ranh giới giữa dữ liệu nội bộ với cách bên ngoài được phép tác động lên nó. Nếu mọi nơi tự đặt `balance = -50`, tài khoản không thể bảo đảm quy tắc số dư không âm.

Một ranh giới hợp lý yêu cầu các bên gọi hành vi `withdraw(50)` và để chính tài khoản kiểm tra hợp lệ. `private` trong Java có thể hỗ trợ che giấu dữ liệu, nhưng mục tiêu thiết kế là bảo vệ **quy tắc và quyền quyết định**, không đơn thuần đánh dấu trường dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-object-invariants">Điều kiện bất biến của đối tượng khi thực hiện hành vi</a>

<details>
<summary>Xem chi tiết</summary>

**Điều kiện bất biến của đối tượng (object invariant)** là mệnh đề phải đúng ở những thời điểm trạng thái được công bố hợp lệ. Ví dụ tài khoản không thấu chi yêu cầu `balance >= 0`; mọi hành vi tạo hoặc cập nhật tài khoản phải giữ điều kiện đó.

Trước rút 30 từ 100, điều kiện đúng; sau rút còn 70, điều kiện vẫn đúng. Yêu cầu rút 120 phải thất bại mà không tạo trạng thái -20. Đừng nhầm invariant với một biến không bao giờ đổi: số dư có thể thay đổi nhiều lần trong khi quy tắc vẫn được giữ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-invariant-vs-immutability">Điều kiện nhất quán và dữ liệu không thể thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

**Bất biến về điều kiện** là quy tắc luôn đúng, còn **dữ liệu bất biến (immutability)** nghĩa là giá trị không bị sửa sau khi tạo. Tài khoản có số dư thay đổi 100 → 70 vẫn giữ `balance >= 0`, dù trạng thái tài khoản có tính thay đổi.

Một bản ghi giao dịch bất biến vẫn có thể sai ngay từ lúc tạo, ví dụ số tiền âm trái quy tắc nghiệp vụ. Vì vậy chỉ làm dữ liệu không thể sửa không tự bảo đảm tính hợp lệ; cần kiểm tra lúc tạo và hợp đồng của hành vi liên quan.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-behavior-boundary">Ranh giới hành vi công bố và chi tiết bị che giấu</a>

<details>
<summary>Xem chi tiết</summary>

Đối tượng nên công bố những **hành vi mang ý nghĩa với người sử dụng** thay vì buộc bên gọi biết phải sửa các trường nào. `withdraw(amount)` diễn đạt một yêu cầu nghiệp vụ; công khai `setBalance(value)` để mọi nơi tự tính có thể phá vỡ quy tắc.

Chi tiết lưu trữ số dư, quy tắc ghi lịch sử và cách chọn công thức phí có thể được thay đổi phía sau hợp đồng nếu kết quả quan sát được vẫn đúng. Ranh giới này giúp khoanh vùng ảnh hưởng thay đổi nhưng phải tránh giấu các tác động phụ quan trọng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-abstraction-value">Trừu tượng hóa theo điều đối tượng có thể làm</a>

<details>
<summary>Xem chi tiết</summary>

**Trừu tượng hóa (abstraction)** tập trung vào điều một đối tượng **cam kết làm được**, không phải từng chi tiết triển khai. Bên gọi dịch vụ thanh toán chỉ cần yêu cầu `pay(amount)` và biết trường hợp thành công/thất bại; không cần biết phí được tính trong bao nhiêu bước.

Một hợp đồng trừu tượng hữu ích không nên quá rộng như `doEverything()`, cũng không nên lộ toàn bộ dữ liệu nội bộ. Hãy diễn đạt yêu cầu nghiệp vụ cùng điều kiện và kết quả quan sát được để bên trong còn không gian cải tiến.

</details>

- [Quay lại đầu trang](#back-to-top)
