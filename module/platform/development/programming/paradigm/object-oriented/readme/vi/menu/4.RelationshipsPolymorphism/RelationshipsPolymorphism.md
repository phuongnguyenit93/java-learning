<a id="back-to-top"></a>

# Đa hình, kiểu con và kế thừa

## Menu
- [Polymorphism — Một hợp đồng, nhiều cách thực hiện](#oop-polymorphism-purpose)
- [Hợp đồng hành vi chung cho các biến thể đa hình](#oop-shared-behavior-contract)
- [Subtyping — Kiểu con cam kết đáp ứng hợp đồng kiểu chung](#oop-subtype-relation)
- [Inheritance — Kế thừa để tổ chức và tái sử dụng hành vi](#oop-inheritance-concept)
- [Substitutability — Thay kiểu con mà không phá kỳ vọng hành vi](#oop-substitutability)
- [Composition so với Inheritance — Ghép đối tượng hay kế thừa](#oop-composition-vs-inheritance)
- [Mở rộng biến thể hành vi bằng Polymorphism](#oop-behavior-variants)

## <a id="oop-polymorphism-purpose">Polymorphism — Một hợp đồng, nhiều cách thực hiện</a>

<details>
<summary>Xem chi tiết</summary>

**Đa hình (polymorphism)** cho phép bên gọi gửi cùng một yêu cầu theo hợp đồng, còn các đối tượng cụ thể thực hiện hành vi phù hợp với mình. Chẳng hạn `pay(amount)` có thể được xử lý bằng ví điện tử hoặc chuyển khoản với cách thực hiện khác nhau.

Điểm có giá trị là bên gọi không phải trải nhiều nhánh “nếu là ví điện tử thì...”. Nhưng các lựa chọn vẫn phải bảo đảm điều đã hứa, không thể viện lý do triển khai khác để âm thầm phá quy tắc thanh toán.

**Cùng yêu cầu, hai đối tượng xử lý khác nhau.** Checkout cần thanh toán số tiền 30, nhưng người dùng có thể chọn `WalletPayment` hoặc `BankPayment`. Bên gọi chỉ gửi yêu cầu khái niệm `pay(30)` đến đối tượng PaymentMethod đã được chọn. Ví có thể kiểm tra số dư ví; chuyển khoản có thể yêu cầu nhà cung cấp xác nhận. Hai con đường nội bộ không giống nhau nhưng cùng trả **`Success(receiptId)` hoặc `Failure(reason)`** theo hợp đồng. Checkout xử lý kết quả chung, không cần viết `if wallet ... else if bank ...` tại mỗi nơi thanh toán.

Đa hình mô tả sự **thay đổi hành vi phía sau một cách gọi chung**, không khẳng định mọi biến thể dùng kế thừa, có cùng dữ liệu, hoặc đều thanh toán thành công. Ví dụ nhà cung cấp từ chối, BankPayment có thể trả `Failure(providerDeclined)` hoàn toàn hợp lệ. **Tự kiểm tra:** điều gì giữ nguyên tại Checkout và điều gì thay đổi giữa ví với chuyển khoản?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-shared-behavior-contract">Hợp đồng hành vi chung cho các biến thể đa hình</a>

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

**Áp dụng bảng hợp đồng vào hai biến thể thật.** Với yêu cầu `pay(30)`, WalletPayment kiểm tra tài khoản liên quan và có thể hoàn tất → `Success(walletReceipt)`; BankPayment gửi yêu cầu tới nhà cung cấp và có thể trả `Success(bankReceipt)` hoặc `Failure(providerDeclined)`. Khi số tiền bằng 0, cả hai phải dùng **cách báo InvalidInput đã được thống nhất**, thay vì một bên giả vờ xử lý thành công. Bên gọi đọc kết quả, gửi biên nhận thành công **chỉ khi có Success**.

Điều kiện **dương** chỉ khiến yêu cầu **đủ điều kiện được xử lý**, không khiến nhà cung cấp phải chấp nhận; vì thế thất bại có lý do trong nhánh hợp lệ **không tự vi phạm** hợp đồng. Bên cung cấp có thể dùng những bước khác nhau, nhưng không được trả thành công giả khi chưa hoàn tất hoặc tung lỗi không được ghi trong hợp đồng thay cho kết quả đã hứa. Đây là một **quy ước giao diện khái niệm** cho bài học, không phải API Java thực thi. **Tự kiểm tra:** `pay(30)` bị ngân hàng từ chối và trả `Failure(providerDeclined)` có phá hợp đồng không?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-subtype-relation">Subtyping — Kiểu con cam kết đáp ứng hợp đồng kiểu chung</a>

<details>
<summary>Xem chi tiết</summary>

**Kiểu con (subtype)** là một kiểu cam kết có thể được sử dụng tại nơi yêu cầu hợp đồng của kiểu rộng hơn. Nếu `WalletPayment` là kiểu con của `PaymentMethod`, đối tượng ví phải đáp ứng các kỳ vọng mà bên gọi đặt vào phương thức thanh toán.

Quan hệ “có cùng vài phương thức” chưa đủ để thiết kế kiểu con đúng. Giả sử hợp đồng chung tiếp nhận mọi số tiền dương **để xử lý**, rồi trả thành công hoặc thất bại tường minh. Kiểu con tự ném ngoại lệ `unsupported amount` ngoài hợp đồng cho một yêu cầu dương sẽ phá cam kết. Ngược lại, nhà cung cấp từ chối thanh toán và trả đúng kết quả thất bại theo hợp đồng **không phải** vi phạm khả năng thay thế.

**Thử đặt một đối tượng cụ thể vào chỗ bên gọi chỉ biết kiểu chung.** Checkout nhận bất kỳ `PaymentMethod` nào với lời hứa: số tiền dương sẽ **được tiếp nhận để xử lý**, sau đó trả kết quả thành công hoặc thất bại tường minh. WalletPayment là subtype hợp lệ nếu tuân thủ lời hứa ấy. Nếu WalletPayment bỗng yêu cầu “chỉ xử lý các số tiền dương dưới 20” rồi ném một lỗi `unsupported amount` **ngoài hợp đồng** khi được gọi `pay(30)`, nó đã **siết chặt điều kiện đầu vào** mà bên gọi không được báo trước.

Ngược lại, BankPayment có thể đưa yêu cầu 30 tới nhà cung cấp rồi nhận từ chối thật và trả `Failure(providerDeclined)`; chính hợp đồng cho phép nhánh thất bại này. Quan hệ kiểu con không phải bài kiểm tra “mọi request dương đều thành công” hay “hai class trùng tên hàm”. Nó là **cam kết về hành vi quan sát được khi thay thế**. **Tự kiểm tra:** vì sao lỗi ngoài hợp đồng khác với kết quả từ chối đã được công bố?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-inheritance-concept">Inheritance — Kế thừa để tổ chức và tái sử dụng hành vi</a>

<details>
<summary>Xem chi tiết</summary>

**Kế thừa (inheritance)** là cơ chế tổ chức hoặc tái sử dụng đặc điểm giữa các kiểu, thường tạo quan hệ kiểu trong ngôn ngữ hỗ trợ. Nhưng sao chép hành vi chung và bảo đảm khả năng thay thế là hai mục tiêu khác nhau.

Ví dụ kiểu thanh toán con tái sử dụng đoạn mã tính phí từ kiểu cha nhưng thay đổi điều kiện chấp nhận tiền có thể phá hợp đồng. Do đó không nên chọn kế thừa chỉ vì tránh trùng một vài dòng mã; cần xét quan hệ bản chất và rủi ro ảnh hưởng chéo.

**Quan sát rủi ro của tái sử dụng.** Giả sử một kiểu thanh toán cha có phép tính phí với mặc định `fee=0`; kiểu con được thiết kế trong giả định đó. Sau này kiểu cha thay công thức thành `fee=5`; kiểu con thừa hưởng thay đổi và hóa đơn `pay(30)` có thể trở thành 35 dù bên gọi vẫn kỳ vọng 30. Lỗi ở đây nằm ở **giả định bị chia sẻ qua kế thừa**, không phải ở cái tên “kiểu cha”.

Kế thừa là một **cách tổ chức/tái sử dụng hành vi hoặc đặc điểm**; kiểu con hợp lệ lại phải đáp ứng **hợp đồng hành vi** mà caller được quyền kỳ vọng. Dùng lại được phép tính phí không đủ để kết luận có quan hệ “là một loại” đúng nghĩa. Một FeePolicy riêng được cộng tác có thể tách thay đổi phí khỏi phân cấp thanh toán nếu bài toán phù hợp. Chương này không dạy cách viết `extends`, ghi đè hay dispatch của Java. **Tự kiểm tra:** nếu đã kế thừa được code, điều gì vẫn cần kiểm chứng trước khi coi một subtype là thay thế hợp lệ?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-substitutability">Substitutability — Thay kiểu con mà không phá kỳ vọng hành vi</a>

<details>
<summary>Xem chi tiết</summary>

**Khả năng thay thế (substitutability)** yêu cầu dùng đối tượng của kiểu con mà không phá kỳ vọng hợp lý dành cho hợp đồng chung. Nếu mọi phương thức thanh toán đều hứa trả trạng thái thất bại rõ ràng khi không trả được, một triển khai không được giả vờ thành công.

Để kiểm tra, chạy cùng bộ tình huống hợp đồng trên các biến thể: số tiền hợp lệ, không hợp lệ, lỗi dịch vụ và xử lý kết quả. Kế thừa trong cấu trúc mã không phải bằng chứng đủ cho khả năng thay thế về hành vi.

**Thử các biến thể trên cùng bộ tình huống**, thay vì chỉ nhìn sơ đồ kế thừa:

| Yêu cầu | WalletPayment đúng | BankPayment đúng | Biến thể sai hợp đồng |
| --- | --- | --- | --- |
| `pay(30)` xử lý thành công | `Success(walletId)` | `Success(bankId)` | Báo `Success` trước khi xử lý |
| `pay(30)` bị bên cung cấp từ chối | `Failure(reason)` | `Failure(providerDeclined)` | Ném lỗi không khai báo thay cho `Failure` |
| `pay(0)` | `InvalidInput` | `InvalidInput` | Âm thầm coi là thanh toán thành công |

Khi Checkout thay WalletPayment bằng BankPayment, nó vẫn có thể dựa vào cùng **phân loại kết quả**, vì nhà cung cấp từ chối đúng cách là **một thất bại có cam kết**, không phải vi phạm khả năng thay thế theo hợp đồng chung. Nhưng một biến thể tự áp điều kiện mới mà không được thông báo, trả thành công giả, hoặc bỏ mất kết quả lỗi bắt buộc khiến Checkout suy luận sai. Ta kiểm tra **điều người dùng quan sát và được hứa**, không kiểm tra “class có extends hay không”. **Tự kiểm tra:** trong bảng, kết quả nào cho thấy không thể dùng biến thể mới tại vị trí PaymentMethod chung?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-composition-vs-inheritance">Composition so với Inheritance — Ghép đối tượng hay kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

**Hợp thành** phù hợp khi một đối tượng **có** một chính sách hoặc dịch vụ cộng tác; **kế thừa** chỉ hợp lý khi quan hệ kiểu con thật sự đáp ứng hợp đồng kiểu cha. Thanh toán **có** chính sách tính phí; chính sách không nhất thiết là một loại thanh toán.

Chọn hợp thành thường dễ thay cộng tác viên mà không làm mọi lớp con phụ thuộc vào thay đổi ở lớp cha. Nhưng thêm quá nhiều lớp cũng có chi phí; hãy cân nhắc hợp đồng ổn định và lợi ích thay đổi theo từng trách nhiệm.

**So sánh cùng một yêu cầu thay phí.** Checkout cần thanh toán 30 và có thể hỏi một đối tượng `FeePolicy.quote(30)`. Nếu chính sách phí nằm trong cộng tác viên độc lập, đổi từ phí 0 sang 2 bằng cách thay FeePolicy phù hợp cùng hợp đồng làm tổng từ 30 thành 32; hành vi Checkout **vẫn là hỏi giá rồi yêu cầu thanh toán**. Nếu nhiều lớp thanh toán con phụ thuộc công thức phí ở lớp cha, thay lớp cha có thể kéo theo ảnh hưởng ngoài dự kiến ở tất cả lớp con.

“Có một chính sách phí” là quan hệ cộng tác, không phải “chính sách phí **là một loại thanh toán**”. Composition ở đây là **ghép khả năng của các object**, không khẳng định FeePolicy bị sở hữu vòng đời theo UML composition mạnh. Kế thừa vẫn có thể phù hợp khi quan hệ subtype bền vững và hợp đồng được giữ; còn bọc mọi phép tính vào nhiều object trung gian chỉ gây khó hiểu. **Tự kiểm tra:** vì sao đổi phí thường là thay một cộng tác viên, không phải tạo subtype mới của Checkout?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-behavior-variants">Mở rộng biến thể hành vi bằng Polymorphism</a>

<details>
<summary>Xem chi tiết</summary>

Khi hệ thống bổ sung chuyển khoản nhanh, bên gọi có thể tiếp tục sử dụng hợp đồng thanh toán chung. Biến thể mới cần hiện thực hành vi của nó và vượt qua các kiểm thử hợp đồng đã có, thay vì buộc sửa tất cả nơi xử lý.

Đa hình phù hợp với những điểm **thực sự cần thay đổi cách hành xử**. Nếu bài toán chỉ có một phép tính đơn giản không có biến thể hay trạng thái độc lập, việc tạo một hệ phân cấp đối tượng lớn có thể phức tạp hơn thủ tục thông thường.

**Thêm `FastTransferPayment` mà không phá caller.** Trước thay đổi, Checkout nhận `PaymentMethod`, gọi `pay(30)` rồi phân nhánh theo `Success(receiptId)` hoặc `Failure(reason)`. Sau thay đổi, nơi **chọn/cấu hình phương thức thanh toán** có thể bổ sung `FastTransferPayment`; Checkout vẫn sử dụng lời gọi và cách xử lý kết quả ấy. Biến thể mới có thể chọn một đường truyền khác, nhưng vẫn phải kiểm tra hợp đồng: yêu cầu dương được xử lý, nhà cung cấp từ chối trả Failure, đầu vào 0 trả InvalidInput theo quy ước.

Không được suy ra “thêm chức năng thì **không sửa dòng nào ở bất kỳ đâu**”: cấu hình lựa chọn, đăng ký phương thức hoặc giao diện chọn cách thanh toán có thể cần chỉnh. Lợi ích là thay đổi được **khoanh vùng**, không nhân bản nhánh theo loại thanh toán trong mọi caller. Nếu chỉ có một phép tính thuần không có biến thể thực sự, dùng nhiều lớp đa hình có thể thừa. **Tự kiểm tra:** phần nào phải biết FastTransfer mới tồn tại, và phần nào vẫn chỉ cần tin vào `PaymentMethod.pay`?

</details>

- [Quay lại đầu trang](#back-to-top)
