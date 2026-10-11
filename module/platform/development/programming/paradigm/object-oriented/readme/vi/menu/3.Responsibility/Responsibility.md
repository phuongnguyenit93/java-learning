<a id="back-to-top"></a>

# Trách nhiệm, hợp đồng và quan hệ cộng tác giữa các đối tượng

## Menu
- [Object Responsibility — Trách nhiệm của một đối tượng](#oop-responsibility-purpose)
- [Responsibility Assignment — Chọn đối tượng sở hữu hành vi](#oop-behavior-owner)
- [Cohesion — Các hành vi cùng phục vụ một trách nhiệm](#oop-responsibility-cohesion)
- [Behavioral Contract — Điều kiện và kết quả được cam kết](#oop-behavioral-contract)
- [Object Collaboration — Cách các đối tượng phối hợp xử lý yêu cầu](#oop-collaboration-flow)
- [Association và Dependency — Liên kết và phụ thuộc giữa các đối tượng](#oop-association-dependency)
- [Object Composition — Ghép các đối tượng để tạo hành vi lớn hơn](#oop-object-composition)
- [Coupling — Mức độ phụ thuộc giữa các đối tượng](#oop-collaboration-coupling)

## <a id="oop-responsibility-purpose">Object Responsibility — Trách nhiệm của một đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

**Trách nhiệm** chỉ rõ phần nào của hệ thống phải bảo đảm một kết quả hay quy tắc. Tài khoản chịu trách nhiệm bảo đảm rút tiền hợp lệ; bộ gửi thông báo chịu trách nhiệm chuyển thông tin tới người dùng, chứ không tự thay đổi số dư.

Chia trách nhiệm hợp lý giúp một thay đổi về giới hạn rút tiền không buộc sửa dịch vụ gửi email. Mỗi đối tượng không nhất thiết chỉ có một phương thức; các hành vi cùng phục vụ một vai trò có thể thuộc chung một trách nhiệm.

**Một kết quả có thể cần nhiều trách nhiệm khác nhau.** Trong lần mua hàng 30, Account chịu trách nhiệm xác nhận/rút số tiền hợp lệ, Checkout theo dõi yêu cầu mua hàng, còn Notifier chỉ truyền kết quả đã được xác nhận. Nếu Notifier tự trừ 30 trên số dư, khi chính sách thấu chi đổi, cả Account lẫn Notifier có thể phải sửa và trả hai kết quả khác nhau. Trách nhiệm của Notifier là **gửi thông tin**, không phải quyết định tài khoản có đủ tiền.

Một đối tượng có thể có nhiều hành vi liên quan: Account nạp, rút và cung cấp số dư khả dụng đều phục vụ quản lý tài khoản. Chia trách nhiệm không phải biến **mỗi hàm thành một object**; đó là chọn nơi có thể giữ lời hứa khi yêu cầu thay đổi. **Tự kiểm tra:** nếu đổi ngưỡng số dư tối thiểu, trong ba đối tượng Account, Checkout, Notifier, ai có lý do trực tiếp phải sửa quy tắc?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-behavior-owner">Responsibility Assignment — Chọn đối tượng sở hữu hành vi</a>

<details>
<summary>Xem chi tiết</summary>

Để xác định nơi sở hữu hành vi, hỏi **ai có đủ thông tin và quyền quyết định** mà không buộc nhiều bên phối hợp tùy tiện. Kiểm tra số dư nằm gần tài khoản hơn màn hình thanh toán; chọn cách gửi thông báo nằm gần thành phần thông báo.

Nếu một lớp điều phối vừa tự tính số dư, vừa xác thực điều kiện, vừa ghi lịch sử, nó có thể đang giành trách nhiệm của các đối tượng khác. Tuy nhiên không được chuyển mọi hành vi vào thực thể tài khoản khi hành vi đó thuộc phối hợp nhiều tài khoản.

**So sánh hai cách giao nhiệm vụ.** Cách A: Checkout đọc `balance=100`, tự tính 120 có rút được không, rồi đổi số dư; một batch khác làm lại phép kiểm tra. Cách B: Checkout gửi `Account.withdraw(120)`; chính Account, nơi có dữ liệu và quyền bảo vệ invariant, xét 100 không đủ và trả `Failure(insufficientFunds)`, giữ 100. Cách B giúp quy tắc thuộc nơi thực sự có thẩm quyền và tránh các caller đưa ra quyết định riêng.

Nhưng “đưa quyết định về gần dữ liệu” không đồng nghĩa nhét *mọi* quy trình vào Account: Checkout mới là nơi điều phối giá hàng, chọn phương thức thanh toán và yêu cầu Notifier phát biên nhận; một lần chuyển giữa **hai** tài khoản còn có phần phối hợp nhiều đối tượng. Đó là lý do phân biệt **trách nhiệm** (cam kết nào thuộc đối tượng) với **phân công trách nhiệm** (ta quyết định giao cam kết đó cho ai). **Tự kiểm tra:** tài khoản hay màn hình mua hàng nên từ chối rút khi thiếu tiền, và ai quyết định có gửi biên nhận?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-responsibility-cohesion">Cohesion — Các hành vi cùng phục vụ một trách nhiệm</a>

<details>
<summary>Xem chi tiết</summary>

**Tính gắn kết (cohesion)** thể hiện các hành vi trong một đối tượng phục vụ cùng mục đích đến đâu. `deposit`, `withdraw` và `availableBalance` cùng giải quyết quản lý tài khoản; `sendPromotionalEmail` không cùng nhóm lý do thay đổi.

Một đối tượng gom nhiều công việc không liên quan sẽ thường phải sửa vì nhiều nguyên nhân độc lập. Tách thành các trách nhiệm có gắn kết giúp hiểu và kiểm thử dễ hơn, dù tăng số đối tượng cần phối hợp.

**Thử thay đổi yêu cầu ở hai lĩnh vực.** Yêu cầu thứ nhất: mỗi tài khoản phải giữ tối thiểu 20 sau khi rút. Yêu cầu thứ hai: email khuyến mại phải đổi mẫu câu. Nếu cả hai sửa cùng một `EverythingAccount`, đối tượng đó đang chứa hai lý do thay đổi không liên quan. Tách hành vi quản lý số dư (nạp, rút, xem khả dụng) khỏi việc gửi email khiến hai thay đổi đụng vào những trách nhiệm riêng.

Cohesion không phải số lượng phương thức ít hay nhiều: một đối tượng có năm hành vi cùng phục vụ quản lý số dư có thể gắn kết hơn một đối tượng chỉ có hai hành vi nhưng thuộc hai nghiệp vụ xa nhau. Tăng cohesion có thể đồng thời tăng số lần **cộng tác**; vì thế cần cân bằng với coupling ở bài sau, không tách máy móc. **Tự kiểm tra:** `sendPromotionalEmail` và `withdraw` cùng đặt trong Account có chung một lý do nghiệp vụ để thay đổi không?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-behavioral-contract">Behavioral Contract — Điều kiện và kết quả được cam kết</a>

<details>
<summary>Xem chi tiết</summary>

**Hợp đồng hành vi** nêu điều bên gọi phải cung cấp và điều bên nhận bảo đảm. `withdraw(amount)` có thể yêu cầu số tiền dương, thành công giảm đúng số dư hoặc thất bại giữ trạng thái nguyên vẹn, đồng thời trả lý do từ chối rõ ràng.

Hợp đồng không nên chỉ ghi kiểu dữ liệu đầu vào/đầu ra; hai cách triển khai cùng nhận số nhưng một cách bỏ qua kiểm tra số dư không thể thay thế hợp lệ cho cách kia. Hợp đồng là nền tảng của cộng tác và đa hình ở các chương sau.

**Viết lời hứa đủ để bên gọi dự đoán được hai nhánh.** Với tài khoản không thấu chi, hợp đồng khái niệm `withdraw(amount)` yêu cầu `amount > 0`. Nếu đầu vào hợp lệ **và có đủ tiền**, thao tác trả `Success` và giảm số dư đúng `amount`; nếu thiếu tiền, nó trả `Failure(insufficientFunds)` và **không đổi số dư**. Đầu vào 0/âm cần có cách báo `InvalidInput` được thống nhất. Các trạng thái lỗi này là **quy ước của ví dụ**, không phải cú pháp hoặc ngoại lệ bắt buộc của mọi ngôn ngữ.

| Ban đầu | Yêu cầu | Quyết định của Account | Quan sát |
| --- | --- | --- | --- |
| 100 | rút 30 | đủ tiền, cập nhật | `Success`, còn 70 |
| 100 | rút 120 | thiếu tiền, bỏ qua cập nhật | `Failure(insufficientFunds)`, còn 100 |
| 100 | rút 0 | đầu vào không hợp lệ | `InvalidInput`, còn 100 |

Bên gọi chịu trách nhiệm đưa yêu cầu phù hợp; bên cung cấp chịu trách nhiệm xử lý đúng lời hứa, kể cả **báo thất bại đúng cách**. “Cùng tên và kiểu tham số” không chứng minh hai triển khai có cùng hợp đồng hành vi. **Tự kiểm tra:** bị từ chối vì thiếu tiền có phải vi phạm hợp đồng không, nếu thất bại đã được hứa?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-collaboration-flow">Object Collaboration — Cách các đối tượng phối hợp xử lý yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

Một ca thanh toán có thể đi theo luồng: đối tượng điều phối yêu cầu tài khoản xác nhận/rút tiền, nhận kết quả rồi yêu cầu đối tượng thông báo gửi biên nhận. Mỗi bên thực hiện trách nhiệm của mình; điều phối không tự sửa trường nội bộ của cộng tác viên.

Vết cộng tác nên nêu thông điệp, kết quả và nhánh thất bại: nếu rút tiền bị từ chối, không được gửi biên nhận thành công. Luồng yêu cầu–phản hồi này là trọng tâm OOP ở cấp mô hình, không cần sơ đồ class cụ thể.

**Theo dõi một chuỗi lời gọi có nhánh.** Giỏ hàng báo tổng giá 30; Checkout hỏi FeePolicy để lấy phí (giả sử phí 0), rồi gửi yêu cầu thanh toán 30. Account/PaymentMethod kiểm tra tiền và trả `Success(receiptId)`; **chỉ sau khi có Success**, Checkout mới nhờ Notifier gửi biên nhận. Sơ đồ khái niệm:

```text
Checkout → FeePolicy.quote(30) → tổng 30
Checkout → PaymentMethod.pay(30)
    ├─ Success(receiptId) → Notifier.sendReceipt(receiptId)
    └─ Failure(reason)   → báo lỗi, KHÔNG gửi biên nhận thành công
```

Với A=100, rút30 hợp lệ: tài khoản còn70 và có kết quả thành công. Với một yêu cầu 120 từ trạng thái 100: kiểm tra thiếu tiền, tài khoản giữ100, nhánh thất bại dừng trước lời gọi gửi biên nhận. Checkout **điều phối**, Account **bảo vệ số dư**, FeePolicy **định phí**, Notifier **truyền tin**. Sơ đồ không chứng minh hệ thống có giao dịch nguyên tử hay email chắc chắn đã đến hộp thư. **Tự kiểm tra:** Notifier có được gửi “thanh toán thành công” trước khi nhận xác nhận không?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-association-dependency">Association và Dependency — Liên kết và phụ thuộc giữa các đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

Hai khái niệm diễn tả **mối liên hệ**, không phải quyền sở hữu dữ liệu mặc định. **Association (liên kết)**: hai đối tượng có quan hệ được mô hình hóa, ví dụ Account A gắn với chủ tài khoản X và còn được nhận diện là tài khoản của X ở nhiều thao tác. **Dependency (phụ thuộc)**: một thành phần cần **khả năng** của thành phần khác để hoàn thành công việc, ví dụ Checkout cần gọi `Notifier.sendReceipt` sau một lần thanh toán. Checkout có thể nhận Notifier như một cộng tác viên cho lời gọi ấy, không nhất thiết phải giữ tham chiếu Notifier suốt vòng đời.

**Quan sát trong một yêu cầu:** Checkout nhận `Success(receipt42)` → cần Notifier → yêu cầu gửi biên nhận → Notifier báo kết quả gửi. Đó là dependency vào **hành vi gửi**, không chứng minh Checkout tạo, sở hữu hay hủy Notifier. Association có thể đi kèm dependency khi một đối tượng vừa giữ quan hệ vừa gọi hành vi, nhưng hai khái niệm không đồng nghĩa. Cả hai cũng **không tự tạo quan hệ hợp thành mạnh theo UML** (Unified Modeling Language, ngôn ngữ dùng để mô hình hóa hệ thống): quan hệ toàn thể–thành phần với ràng buộc vòng đời được xét riêng. **Tự kiểm tra:** chỉ vì Checkout gọi Notifier một lần, có thể kết luận Checkout sở hữu vòng đời của Notifier không?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-object-composition">Object Composition — Ghép các đối tượng để tạo hành vi lớn hơn</a>

<details>
<summary>Xem chi tiết</summary>

**Hợp thành (composition)** cho phép một đối tượng sử dụng các cộng tác viên có trách nhiệm riêng để tạo hành vi lớn hơn. Một quy trình thanh toán có thể phối hợp `Account`, `FeePolicy` và `Notifier` mà không cần coi chúng là các kiểu con của cùng một loại.

Thay chính sách phí có thể chỉ cần thay đối tượng chính sách đáp ứng cùng hợp đồng. Dù vậy cấu trúc gồm quá nhiều lớp trung gian chỉ chuyển tiếp lời gọi lại tạo thêm chi phí hiểu và kiểm thử; hợp thành có ý nghĩa khi ranh giới trách nhiệm thực sự hữu ích.

**Lắp hành vi lớn từ các trách nhiệm nhỏ.** Checkout dùng FeePolicy để tính phí, PaymentMethod để thanh toán và Notifier để thông báo. Giả sử phí ban đầu bằng 0, giỏ30 tạo yêu cầu thanh toán30. Khi chính sách phí chuyển sang cộng2 đơn vị, **thay FeePolicy** cùng hợp đồng `quote` có thể khiến tổng thành32; Checkout vẫn hỏi `quote` và `pay(total)`, không cần trở thành kiểu con của FeePolicy. Đó là **object composition theo nghĩa ghép các đối tượng để phối hợp hành vi**.

Cần tránh một bẫy thuật ngữ: nói “Checkout **có** Notifier” hoặc “Checkout **dùng** Account” chưa có nghĩa hai đối tượng có **UML composition** mạnh về sở hữu/vòng đời. Một ví dụ part–whole mạnh hơn là Order sở hữu các OrderLine như các thành phần của đơn hàng, nếu mô hình thực sự áp đặt ràng buộc vòng đời tương ứng; đó là giả định về nghiệp vụ, không phải kết quả tự động của một trường tham chiếu. **Aggregation** là một cách mô tả quan hệ phần–toàn thể nhưng không nên ép mọi “has-a” vào một loại UML. **Tự kiểm tra:** thay FeePolicy có buộc viết cây kế thừa PaymentMethod không?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-collaboration-coupling">Coupling — Mức độ phụ thuộc giữa các đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

**Mức độ phụ thuộc (coupling)** mô tả một đối tượng cần biết bao nhiêu về đối tượng khác. Nếu thanh toán biết cả tên trường số dư, định dạng lưu trữ và cách ghi lịch sử của tài khoản, bất kỳ thay đổi nhỏ nào cũng dễ lan rộng.

Khi chỉ biết hợp đồng `withdraw`, thanh toán phụ thuộc vào hành vi công bố, không lệ thuộc cấu trúc nội bộ. Tuy nhiên hợp đồng quá mơ hồ có thể che giấu lỗi; mục tiêu là phụ thuộc vào lời hứa đủ chính xác chứ không xóa mọi liên kết.

**Đo coupling qua thay đổi phải lan tới đâu.** Nếu Checkout biết Account có trường `balance`, biết tên bảng lưu và tự trừ 30, thì khi Account chuyển sang tính số dư từ sổ giao dịch, Checkout phải sửa vì nó phụ thuộc vào **biểu diễn nội bộ**. Nếu Checkout chỉ biết hợp đồng `withdraw(30)` và các kết quả `Success/Failure`, Account có thể đổi cách lưu bên trong, còn Checkout vẫn gọi như cũ. Kết quả hợp lệ A:100→70 phải giống nhau; chỉ chi tiết triển khai thay đổi.

Coupling không phải đếm số mũi tên: Checkout **vẫn cần** phụ thuộc vào khả năng thanh toán để làm việc. Mục tiêu là phụ thuộc **đủ ít và đủ rõ** vào những điều ổn định, chứ không giả vờ mọi đối tượng độc lập tuyệt đối. Đồng thời, một hợp đồng quá mơ hồ không nói lỗi thiếu tiền thì bên gọi khó xử lý, nên giảm coupling không được đánh đổi sự chính xác của lời hứa. **Tự kiểm tra:** đổi Account từ lưu một số dư sang sổ giao dịch, phương án nào buộc Checkout sửa nhiều hơn và vì sao?

</details>

- [Quay lại đầu trang](#back-to-top)
