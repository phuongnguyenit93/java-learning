<a id="back-to-top"></a>

# Lập trình hướng đối tượng: Mô hình đối tượng và trách nhiệm

## Menu
- [Lập trình hướng đối tượng: Khái niệm, bản chất và phạm vi](#oop-what)
- [Động cơ tổ chức dữ liệu và hành vi theo trách nhiệm](#oop-why)
- [Cách tổ chức thủ tục và dữ liệu tách rời](#oop-before)
- [Đối tượng, danh tính, trạng thái, hành vi và cộng tác](#oop-solution)
- [Quan hệ giữa tư duy OOP và cơ chế ngôn ngữ Java](#oop-boundary)
- [Kiến thức khởi đầu và lộ trình từ đối tượng đến thiết kế](#oop-learning-path)

## <a id="oop-what">Lập trình hướng đối tượng: Khái niệm, bản chất và phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

Lập trình hướng đối tượng là cách tổ chức chương trình quanh **các đối tượng có danh tính, trạng thái khi cần, hành vi và trách nhiệm**. Các đối tượng cộng tác với nhau qua những hợp đồng hành vi rõ ràng.

OOP không đồng nghĩa với việc “chia mã nguồn thành nhiều lớp”. Lớp (`class`) chỉ là một cơ chế mà một số ngôn ngữ dùng để tạo mô hình đối tượng.

Một **đối tượng** không chỉ là một gói dữ liệu: nó có danh tính riêng, một vai trò đối với hệ thống và những hành vi người khác có thể yêu cầu. Hai tài khoản có cùng số dư vẫn là hai đối tượng khác nhau vì đại diện hai thực thể và lịch sử giao dịch khác nhau. Trạng thái có thể thay đổi, nhưng đối tượng bất biến cũng thuộc tư duy OOP.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-why">Động cơ tổ chức dữ liệu và hành vi theo trách nhiệm</a>

<details>
<summary>Xem chi tiết</summary>

Khi dữ liệu và các quy tắc xử lý liên quan nằm rải rác khắp hệ thống, một thay đổi nghiệp vụ có thể ảnh hưởng tới nhiều thành phần không đáng phải sửa.

OOP hướng tới đặt trạng thái và hành vi liên quan trong đúng ranh giới trách nhiệm, giúp khoanh vùng tác động của thay đổi.

Khi quy tắc rút tiền được sao chép vào màn hình, dịch vụ và tác vụ nền, một thay đổi giới hạn số dư phải sửa ở nhiều nơi. Đặt trách nhiệm kiểm tra rút tiền tại đối tượng quản lý tài khoản giúp các bên gọi cùng một hợp đồng thay vì tự viết lại quy tắc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-before">Cách tổ chức thủ tục và dữ liệu tách rời</a>

<details>
<summary>Xem chi tiết</summary>

Lập trình thủ tục có thể tổ chức chương trình thành các thủ tục xử lý và cấu trúc dữ liệu riêng biệt. Cách này vẫn hiệu quả trong nhiều bài toán, không phải một lựa chọn “thấp hơn” OOP.

OOP hữu ích hơn khi bài toán có nhiều thực thể hoặc đối tượng cộng tác, mỗi bên có vòng đời, trách nhiệm và hành vi tương tác đáng kể.

Lối thủ tục có thể biểu diễn `AccountData` riêng và gọi `withdraw(account, amount)` để xử lý; đây là phương án sáng sủa cho nhiều bài toán. OOP đặc biệt có ích khi tài khoản, lịch sử, chính sách thanh toán và thông báo phát triển thành những trách nhiệm cần cộng tác, mỗi phần có cam kết riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-solution">Đối tượng, danh tính, trạng thái, hành vi và cộng tác</a>

<details>
<summary>Xem chi tiết</summary>

```text
trạng thái + hành vi
      ↓
đối tượng chịu trách nhiệm
      ↓
cộng tác qua hợp đồng
```

Những khái niệm tiếp theo gồm đóng gói (encapsulation), trừu tượng hóa (abstraction), hợp thành (composition), kế thừa và kiểu con (inheritance/subtyping), cùng đa hình (polymorphism).

Trong mô hình tài khoản, **danh tính** xác định tài khoản nào, **trạng thái** có thể gồm số dư, **hành vi** như nạp/rút quyết định thay đổi hợp lệ và **trách nhiệm** quy định ai được quyết định. Đối tượng thanh toán yêu cầu tài khoản thực hiện hành vi thay vì tùy ý sửa trường số dư.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-boundary">Quan hệ giữa tư duy OOP và cơ chế ngôn ngữ Java</a>

<details>
<summary>Xem chi tiết</summary>

Module này giảng dạy **mô hình tư duy hướng đối tượng không phụ thuộc ngôn ngữ**.

Từ khóa, quyền truy cập, cơ chế lớp/giao diện và cách Java lựa chọn phương thức lúc chạy thuộc `programming/language/java/core/oop` và các module Java Core liên quan.

Các từ `class`, `interface` hay `private` là cơ chế có thể dùng trong Java để hiện thực đối tượng, không phải toàn bộ định nghĩa OOP. Chúng ta tập trung vào việc xác định trách nhiệm, hợp đồng, tính nhất quán và cộng tác trước; chi tiết kế thừa, ghi đè và phân phối lời gọi trong Java thuộc Java Core.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-learning-path">Kiến thức khởi đầu và lộ trình từ đối tượng đến thiết kế</a>

<details>
<summary>Xem chi tiết</summary>

Người học cần quen với biến, thao tác và dữ liệu đơn giản; không cần thành thạo Java class. Hãy theo dõi tài khoản từ **danh tính/trạng thái**, qua **đóng gói và điều kiện nhất quán**, tới **trách nhiệm và hợp đồng**, sau đó học **hợp thành, kiểu con, đa hình**, cuối cùng đánh giá thiết kế và rủi ro.

</details>

- [Quay lại đầu trang](#back-to-top)
