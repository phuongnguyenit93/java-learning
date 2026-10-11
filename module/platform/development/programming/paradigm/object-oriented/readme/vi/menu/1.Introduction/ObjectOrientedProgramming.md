<a id="back-to-top"></a>

# Nền tảng OOP: Vì sao cần đối tượng và chúng cộng tác ra sao

## Menu
- [Lập trình hướng đối tượng (OOP): Khái niệm và phạm vi](#oop-what)
- [Từ thủ tục và dữ liệu tách rời đến nhu cầu phân chia trách nhiệm](#oop-before)
- [Mục đích của OOP: Đưa quy tắc về đúng đối tượng chịu trách nhiệm](#oop-why)
- [Bản đồ khái niệm OOP: Đối tượng, nguyên lý và quan hệ](#oop-solution)
- [Object và Class — Đối tượng khác với bản mô tả tạo đối tượng](#oop-class-vs-object)
- [Tư duy OOP và cơ chế class, interface trong Java](#oop-boundary)
- [Điểm xuất phát và lộ trình từ đối tượng đến thiết kế](#oop-learning-path)

## <a id="oop-what">Lập trình hướng đối tượng (OOP): Khái niệm và phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

Lập trình hướng đối tượng là cách tổ chức chương trình quanh **các đối tượng có danh tính, trạng thái khi cần, hành vi và trách nhiệm**. Các đối tượng cộng tác với nhau qua những hợp đồng hành vi rõ ràng.

OOP không đồng nghĩa với việc “chia mã nguồn thành nhiều lớp”. Lớp (`class`) chỉ là một cơ chế mà một số ngôn ngữ dùng để tạo mô hình đối tượng.

Một **đối tượng** không chỉ là một gói dữ liệu: nó có danh tính riêng, một vai trò đối với hệ thống và những hành vi người khác có thể yêu cầu. Hai tài khoản có cùng số dư vẫn là hai đối tượng khác nhau vì đại diện hai thực thể và lịch sử giao dịch khác nhau. Trạng thái có thể thay đổi, nhưng đối tượng bất biến cũng thuộc tư duy OOP.

**Hình dung bằng một yêu cầu cụ thể.** Hệ thống có tài khoản A và B, mỗi tài khoản đang có 100 đơn vị tiền. Người mua yêu cầu rút 30 từ A. Bộ phận thanh toán không tự gán lại số dư; nó gửi yêu cầu `A.withdraw(30)`. A xác định có đủ tiền không, chấp nhận và chuyển trạng thái **A: 100 → 70**; B vẫn là **100**. Người gọi nhận kết quả thành công, còn A chịu trách nhiệm về thay đổi. Như vậy, danh tính trả lời *đang nói chuyện với tài khoản nào*, trạng thái trả lời *tài khoản ấy đang có gì*, hành vi trả lời *nó có thể đáp ứng yêu cầu nào*, và trách nhiệm trả lời *ai có quyền quyết định*. Cộng tác là các đối tượng yêu cầu nhau thực hiện phần việc đã cam kết.

Nếu thay `withdraw(30)` bằng `withdraw(120)` khi A có 100, tài khoản phải từ chối và giữ nguyên 100 theo quy tắc không thấu chi. Đây là **một mô hình minh họa**, không phải quy định rằng mọi ứng dụng OOP đều là ngân hàng hay mọi đối tượng phải chứa dữ liệu thay đổi. **Tự kiểm tra:** cùng số dư có chứng minh A và B là cùng một đối tượng không?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-before">Từ thủ tục và dữ liệu tách rời đến nhu cầu phân chia trách nhiệm</a>

<details>
<summary>Xem chi tiết</summary>

Lập trình thủ tục có thể tổ chức chương trình thành các thủ tục xử lý và cấu trúc dữ liệu riêng biệt. Cách này vẫn hiệu quả trong nhiều bài toán, không phải một lựa chọn “thấp hơn” OOP.

OOP hữu ích hơn khi bài toán có nhiều thực thể hoặc đối tượng cộng tác, mỗi bên có vòng đời, trách nhiệm và hành vi tương tác đáng kể.

Lối thủ tục có thể biểu diễn `AccountData` riêng và gọi `withdraw(account, amount)` để xử lý; đây là phương án sáng sủa cho nhiều bài toán. OOP đặc biệt có ích khi tài khoản, lịch sử, chính sách thanh toán và thông báo phát triển thành những trách nhiệm cần cộng tác, mỗi phần có cam kết riêng.

**So sánh trên cùng một nhiệm vụ, không so sánh nhãn ngôn ngữ.** Ở phương án thủ tục có kiểm soát, chương trình giữ `AccountData` và một hàm `withdraw(account, amount)`; tất cả nơi gọi dùng chung hàm. A=100, rút30 vẫn cho 70 và cách này hoàn toàn có thể đúng, dễ kiểm thử. Vấn đề chỉ xuất hiện nếu màn hình, tác vụ nền và thanh toán **mỗi bên tự viết** kiểm tra đủ tiền: khi quy tắc đổi từ cho phép số dư còn 0 sang phải giữ tối thiểu 20, người sửa có ba điểm dễ bỏ sót.

Phương án hướng đối tượng đặt `withdraw` sau ranh giới trách nhiệm của Account: các bên gửi yêu cầu và nhận kết quả. Khi thay quy tắc, bên sở hữu hành vi được sửa, bên gọi không cần đọc cấu trúc số dư. **Không kết luận OOP luôn ít code hơn:** nếu một hàm thủ tục duy nhất đã tập trung và bảo vệ quy tắc tốt, chuyển nó thành class chỉ để đổi cú pháp không tự mang lại lợi ích. **Tự kiểm tra:** đâu là nguyên nhân làm ba nơi phải sửa: “thủ tục” hay “quy tắc bị nhân bản”?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-why">Mục đích của OOP: Đưa quy tắc về đúng đối tượng chịu trách nhiệm</a>

<details>
<summary>Xem chi tiết</summary>

Khi dữ liệu và các quy tắc xử lý liên quan nằm rải rác khắp hệ thống, một thay đổi nghiệp vụ có thể ảnh hưởng tới nhiều thành phần không đáng phải sửa.

OOP hướng tới đặt trạng thái và hành vi liên quan trong đúng ranh giới trách nhiệm, giúp khoanh vùng tác động của thay đổi.

Khi quy tắc rút tiền được sao chép vào màn hình, dịch vụ và tác vụ nền, một thay đổi giới hạn số dư phải sửa ở nhiều nơi. Đặt trách nhiệm kiểm tra rút tiền tại đối tượng quản lý tài khoản giúp các bên gọi cùng một hợp đồng thay vì tự viết lại quy tắc.

**Theo dấu nguyên nhân và hậu quả.** Giả sử ban đầu chính sách cho phép rút đến số dư bằng 0; A đang có 100. Khi yêu cầu mới đòi luôn để lại ít nhất 20, rút 90 **trước đây được chấp nhận**, nay **phải từ chối**. Nếu màn hình cập nhật điều kiện còn tác vụ nền vẫn kiểm tra theo quy tắc cũ, cùng yêu cầu 90 có thể nhận hai kết quả trái nhau. Sai khác xảy ra vì *quyền quyết định* đã bị phân tán, không phải vì phép trừ 100−90 khó.

Cho Account quản lý quy tắc giữ số dư khiến màn hình, batch và Checkout hỏi cùng một hành vi. Account kiểm tra `100 - 90 >= 20` là sai, trả từ chối, giữ 100; các bên không tự quyết định phép trừ. Mục tiêu OOP ở đây là làm **nơi quyết định và tác động của thay đổi** dễ xác định, không hứa mọi hệ thống sẽ tự hết lỗi. **Tự kiểm tra:** nếu thêm một màn hình mới, thành phần nào nên quyết định yêu cầu rút 90 hợp lệ?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-solution">Bản đồ khái niệm OOP: Đối tượng, nguyên lý và quan hệ</a>

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

**Đọc bản đồ theo từng cấp, không như bốn “thành phần bắt buộc”.** Cấp **mô hình đối tượng** nói tới *A là tài khoản nào, đang giữ trạng thái gì và đáp ứng yêu cầu nào*. Cấp **ranh giới thiết kế** nói *ai có quyền đổi số dư, điều kiện hợp lệ nào được bảo vệ, điều gì được công bố hoặc che giấu*. Cấp **cộng tác/quan hệ** nói *Checkout nhờ Account xử lý, rồi nhờ Notifier báo kết quả như thế nào*. Cấp **biến thể hành vi** nói *một yêu cầu thanh toán có thể do ví hay chuyển khoản đáp ứng theo cùng một cam kết*. Những cấp này liên quan nhưng **không phải các loại đối tượng ngang hàng**.

Ví dụ: `Account A` có danh tính riêng; `withdraw(30)` là hành vi; `balance >= 0` là quy tắc nhất quán; `Checkout → Account` là quan hệ yêu cầu–phản hồi; `WalletPayment` và `BankPayment` là hai cách đáp ứng yêu cầu thanh toán. **Class** chỉ là một cơ chế mô tả/tạo đối tượng ở ngôn ngữ dựa trên lớp, không nằm cùng cấp khái niệm với việc một yêu cầu có thành công hay không. **Tự kiểm tra:** “đa hình” thuộc biến thể hành vi hay thuộc dữ liệu số dư?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-class-vs-object">Object và Class — Đối tượng khác với bản mô tả tạo đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

**Object (đối tượng)** là một thực thể cụ thể trong lúc chương trình hoạt động: tài khoản A có mã riêng, số dư 100 và có thể nhận yêu cầu rút tiền. **Class (lớp)** trong một ngôn ngữ *dựa trên lớp* là **bản mô tả** những thuộc tính và hành vi mà các đối tượng kiểu đó có thể có. Hãy hình dung một bản mẫu “Tài khoản” và **hai tài khoản thực tế A, B** được tạo theo bản mẫu ấy: cả hai có thể bắt đầu với số dư 100 nhưng vẫn là hai đối tượng khác danh tính.

Theo dõi một thao tác: gửi yêu cầu rút 30 tới **A** → A kiểm tra đủ tiền → A còn **70** → **B vẫn 100**. Bản mô tả class không phải một trong hai số dư đang được thay đổi. Nếu có hai cách gọi cùng tham chiếu đến *chính A*, thì thao tác qua cách gọi thứ nhất khiến cách gọi thứ hai nhìn thấy A=70; đó là **hai cách trỏ tới một đối tượng**, khác với hai đối tượng A và B.

Không phải mọi kiểu OOP trên thế giới đều bắt buộc có class: một số mô hình đối tượng dùng cơ chế khác, chẳng hạn đối tượng làm nguyên mẫu. Bài này chỉ dạy sự khác biệt **bản mô tả ↔ thực thể đang tham gia xử lý**; cú pháp tạo instance, constructor hay quản lý tham chiếu Java thuộc Java Core. **Tự kiểm tra:** tạo hai tài khoản từ cùng một class có làm chúng trở thành một tài khoản không?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-boundary">Tư duy OOP và cơ chế class, interface trong Java</a>

<details>
<summary>Xem chi tiết</summary>

Module này giảng dạy **mô hình tư duy hướng đối tượng không phụ thuộc ngôn ngữ**.

Từ khóa, quyền truy cập, cơ chế lớp/giao diện và cách Java lựa chọn phương thức lúc chạy thuộc `programming/language/java/core/oop` và các module Java Core liên quan.

Các từ `class`, `interface` hay `private` là cơ chế có thể dùng trong Java để hiện thực đối tượng, không phải toàn bộ định nghĩa OOP. Chúng ta tập trung vào việc xác định trách nhiệm, hợp đồng, tính nhất quán và cộng tác trước; chi tiết kế thừa, ghi đè và phân phối lời gọi trong Java thuộc Java Core.

**Cùng một quyết định, hai tầng câu hỏi.** Ở tầng tư duy OOP, ta phát biểu: “Account nhận `withdraw(30)`, kiểm tra điều kiện, trả kết quả và giữ số dư hợp lệ; Checkout không tự sửa số dư.” Chưa cần chọn ngôn ngữ vẫn nhận xét được trách nhiệm đúng hay sai. Ở tầng Java, ta mới hỏi **khai báo class thế nào**, field có quyền truy cập gì, interface biểu diễn hợp đồng ra sao, lời gọi được chọn phương thức nào lúc chạy.

Thêm `private balance` vào một class không tự bảo đảm mô hình đúng: nếu một phương thức công khai `setBalance(-20)` cho phép phá quy tắc, ranh giới hành vi vẫn yếu. Tương tự, một `interface` không tự định nghĩa điều gì xảy ra khi thanh toán thất bại; ta phải giải thích lời hứa hành vi trước. Các mô tả ở đây là **ví dụ khái niệm**, không phải code Java chạy được. **Tự kiểm tra:** “ai được quyết định rút tiền?” và “Java dùng từ khóa nào?” là cùng một câu hỏi hay hai tầng khác nhau?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-learning-path">Điểm xuất phát và lộ trình từ đối tượng đến thiết kế</a>

<details>
<summary>Xem chi tiết</summary>

Người học cần quen với biến, thao tác và dữ liệu đơn giản; không cần thành thạo Java class. Hãy theo dõi tài khoản từ **danh tính/trạng thái**, qua **đóng gói và điều kiện nhất quán**, tới **trách nhiệm và hợp đồng**, sau đó học **hợp thành, kiểu con, đa hình**, cuối cùng đánh giá thiết kế và rủi ro.

**Điểm bắt đầu:** chỉ cần biết một biến giữ giá trị, một điều kiện quyết định nhánh và một hàm/thủ tục nhận yêu cầu. Không cần biết `class`, `interface` hay Java trước khi học mô hình. **Đích đến:** nhìn một yêu cầu thanh toán nhỏ và giải thích được ai giữ quy tắc, đối tượng nào cộng tác, điều gì xảy ra khi thành công/thất bại, thêm phương thức thanh toán mới ảnh hưởng chỗ nào.

Bắt đầu với **danh tính và mô hình object/class**: vẽ A và B cùng 100, rút ở A mà B không đổi. Tiếp theo học **đóng gói và invariant**: từ 100 thử rút 30 được 70, thử rút 120 bị từ chối và vẫn 100. Khi hiểu *ai giữ trạng thái hợp lệ*, học **trách nhiệm và hợp đồng**: viết lời hứa `withdraw` trước khi điều phối. Rồi chuyển sang **cộng tác**: Checkout nhận kết quả, Notifier chỉ gửi biên nhận thành công sau xác nhận; thay chính sách phí vẫn giữ hợp đồng. Cuối cùng học **đa hình và khả năng thay thế**: ví và chuyển khoản cùng trả kết quả đúng hợp đồng; đánh giá lúc nào lớp trung gian hoặc kế thừa khiến thiết kế nặng hơn lợi ích.

**Tự đánh giá tiến độ:** bạn có thể (1) phân biệt hai đối tượng cùng giá trị; (2) tự lần vết nhánh từ chối giữ trạng thái; (3) kể đúng trách nhiệm của Checkout, Account, Notifier; (4) giải thích cách thêm BankPayment mà Checkout không phải hỏi loại? Nếu được, bạn đã có nền tảng để phân tích một thiết kế OOP nhỏ, trước khi học cơ chế Java.

</details>

- [Quay lại đầu trang](#back-to-top)
