<a id="back-to-top"></a>

# Đánh đổi và tổng hợp thiết kế hướng đối tượng

## Menu
- [Đóng gói: Lợi ích bảo vệ quy tắc và chi phí của ranh giới](#oop-encapsulation-benefits)
- [Overengineering — Khi quá nhiều đối tượng làm thiết kế phức tạp](#oop-overdesign-risks)
- [Fragile Inheritance — Kế thừa mong manh và hợp đồng bị phá vỡ](#oop-fragile-inheritance)
- [Shared Mutable State — Rủi ro nhiều đối tượng cùng sửa dữ liệu](#oop-shared-state-risks)
- [Khi thủ tục hoặc hàm biến đổi dữ liệu phù hợp hơn OOP](#oop-alternative-paradigms)
- [Từ yêu cầu đến nhóm đối tượng cộng tác: Ví dụ thiết kế xuyên suốt](#oop-design-walkthrough)
- [Phạm vi OOP và hướng học tiếp: Java Core, nguyên lý thiết kế, DDD, AOP](#oop-owner-handoff)

## <a id="oop-encapsulation-benefits">Đóng gói: Lợi ích bảo vệ quy tắc và chi phí của ranh giới</a>

<details>
<summary>Xem chi tiết</summary>

Đóng gói giúp đổi cách lưu số dư mà không yêu cầu mọi bên gọi sửa, miễn hợp đồng rút/nạp vẫn đúng. Nó cũng tạo một nơi bảo vệ điều kiện nhất quán của đối tượng và khoanh vùng lỗi khi kết quả bất hợp lệ xuất hiện.

Đổi lại, việc kiểm tra hành vi có thể cần gọi qua đối tượng thay vì quan sát cấu trúc tự do; quá nhiều ranh giới mỏng khiến việc theo dõi đường đi khó hơn. Chỉ che dữ liệu khi điều đó bảo vệ một trách nhiệm hoặc giảm phụ thuộc có ý nghĩa.

**Kiểm chứng lợi ích bằng một thay đổi thật.** Phiên bản 1 của Account giữ `balance=100` trong một trường; phiên bản 2 lưu các khoản nạp/rút và tính số dư từ sổ lịch sử. Nếu các caller chỉ yêu cầu `withdraw(30)`, cả hai phiên bản đều kiểm tra đủ tiền, chấp nhận và công bố **70**. Giả sử Checkout truy cập thẳng trường `balance`: khi cách lưu bị thay đổi, Checkout phải sửa; khi ranh giới hành vi ổn định, chỉ Account cần đổi nội bộ.

Chi phí tương ứng là người bảo trì phải xem hành vi và kết quả thay vì tùy tiện chỉnh một field; quá nhiều đối tượng chỉ chuyển tiếp có thể khiến lần vết khó. Ranh giới tốt là ranh giới bảo vệ một invariant hoặc một lựa chọn có khả năng đổi, không phải việc **che tất cả** cho có vẻ hướng đối tượng. **Tự kiểm tra:** trong hai phiên bản Account, điều gì được phép đổi, điều gì Checkout có quyền kỳ vọng giữ nguyên?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-overdesign-risks">Overengineering — Khi quá nhiều đối tượng làm thiết kế phức tạp</a>

<details>
<summary>Xem chi tiết</summary>

Nếu mỗi phép cộng nhỏ được bọc trong ba lớp điều phối và nhiều đối tượng chỉ chuyển tiếp lời gọi, chương trình có thể phức tạp hơn bài toán. Dấu hiệu khác là một đối tượng `EverythingManager` xử lý tài khoản, thanh toán, gửi thư và báo cáo.

Hãy xét liệu các ranh giới đối tượng có giúp bảo vệ điều kiện nhất quán, cô lập thay đổi hoặc mở rộng hành vi thật hay không. Khi không có trách nhiệm riêng đáng kể, một hàm thuần hoặc thủ tục rõ ràng có thể tốt hơn cả cây lớp lớn.

**Thử gỡ một lớp không có trách nhiệm.** Một luồng nhỏ `Checkout → PaymentForwarderA → PaymentForwarderB → PaymentMethod` có thể chỉ chuyển tiếp nguyên `pay(30)` mà không thêm quy tắc, thông tin hay cách xử lý lỗi. Xóa hai Forwarder làm đường đi ngắn hơn nhưng hợp đồng thanh toán và kết quả **không đổi**. Ngược lại, giữ FeePolicy là hợp lý nếu giá/điều kiện phí có thể thay độc lập; giữ Account là hợp lý vì nó bảo vệ số dư.

Một `EverythingManager` vừa duyệt rút, ghi giá, gửi email sẽ phải thay đổi khi bất kỳ nghiệp vụ nào đổi; phân chia Account, Checkout, Notifier giúp xác định lý do sửa. Tuy nhiên tách thành quá nhiều “manager” mới mà chỉ gọi nhau theo dây chuyền là **đổi tên sự phức tạp**, không giải quyết nó. **Tự kiểm tra:** đối tượng trung gian này bảo vệ hợp đồng hay chỉ chuyển nguyên tham số đi tiếp?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-fragile-inheritance">Fragile Inheritance — Kế thừa mong manh và hợp đồng bị phá vỡ</a>

<details>
<summary>Xem chi tiết</summary>

Cây kế thừa mong manh xảy ra khi sửa hành vi của kiểu cha vô tình thay đổi nhiều kiểu con phụ thuộc vào chi tiết đó. Ví dụ kiểu cha thêm phí mới nhưng một kiểu con đã giả định phí luôn bằng 0, khiến kết quả sai mà bên gọi không biết.

Tình huống nguy hiểm hơn là kiểu con bỏ qua hợp đồng của kiểu cha, như trả trạng thái thành công khi thanh toán thực sự thất bại. Kiểm thử hợp đồng chung và ưu tiên hợp thành khi quan hệ “là một loại” không bền vững giúp giảm rủi ro.

**Lần theo giả định bị phá.** Một kiểu thanh toán cha mặc định `fee=0`; kiểu con WalletPayment dựa vào đó và từng xử lý yêu cầu 30 với tổng 30. Khi cha được sửa để cộng phí 5, lời gọi tương tự nay có thể tính 35; nếu kiểu con hoặc bên gọi vẫn tin cam kết tổng không đổi, lỗi hành vi phát sinh mà không sửa chính file kiểu con. Cơ chế gây lỗi là **thay đổi được thừa hưởng làm lộ giả định ngầm**.

Không phải mọi thay đổi phí đều sai: nếu hợp đồng công bố phí biến thiên, kết quả 35 có thể hợp lệ. Ta phải kiểm tra lời hứa thực tế, không chỉ nhìn con số. Chọn một FeePolicy riêng cho phần thay đổi độc lập có thể giảm tác động lan truyền; đồng thời chạy bộ tình huống hợp đồng ở các biến thể để phát hiện sai lệch. **Tự kiểm tra:** khi cha thay phí, đâu là bằng chứng cần có trước khi tuyên bố kiểu con vi phạm hợp đồng?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-shared-state-risks">Shared Mutable State — Rủi ro nhiều đối tượng cùng sửa dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

Hai đối tượng cùng giữ tham chiếu tới một dữ liệu có thể thay đổi sẽ khó dự đoán nếu bên này chỉnh nó ngoài hợp đồng của bên kia. Ví dụ báo cáo đọc số dư từ dữ liệu tài khoản mà dịch vụ khác âm thầm sửa có thể tạo kết quả không nhất quán.

Giới hạn đường cập nhật, dùng bản sao giá trị khi phù hợp và kiểm tra điều kiện nhất quán sau thao tác là các lựa chọn thiết kế. Tuy nhiên việc chia sẻ trạng thái giữa các luồng hoặc hệ thống khác nhau đòi hỏi cơ chế đồng bộ và giao dịch riêng, không phải cứ áp dụng OOP là tự giải quyết được.

**Một lần đọc và một lần cập nhật không được phối hợp.** Report giữ cách truy cập tới cùng dữ liệu của Account A khi A=100. Trong lúc Report chuẩn bị ghi “số dư 100”, một phần khác bỏ qua `withdraw` và sửa trực tiếp thành 70. Report có thể công bố **100** dù trạng thái quan sát sau đó là **70**. Vấn đề là nhiều bên có đường sửa/đọc cùng dữ liệu mà không có hợp đồng rõ về thời điểm và kết quả, không phải “cứ có nhiều object là có race condition”.

Giải pháp ở cấp mô hình là giới hạn quyền cập nhật và xác định khi nào báo cáo lấy một bản ghi ổn định hoặc snapshot. Nếu hai luồng chạy đồng thời thực sự, cần quy tắc đồng bộ/transaction phù hợp ở module chuyên sâu; OOP **không tự bảo đảm** dữ liệu không bị race hoặc giao dịch hai tài khoản nguyên tử. **Tự kiểm tra:** Report đọc 100 có chứng minh nó đã đọc trạng thái mới nhất sau khi rút không?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-alternative-paradigms">Khi thủ tục hoặc hàm biến đổi dữ liệu phù hợp hơn OOP</a>

<details>
<summary>Xem chi tiết</summary>

Nếu mục tiêu chỉ tính tổng khoản thu từ danh sách bất biến, một hàm nhận danh sách và trả số có thể rõ ràng hơn việc tạo nhiều đối tượng giữ trạng thái. Nếu công việc chủ yếu là tuần tự đọc–tính–in, nhóm thủ tục nhỏ cũng đủ.

Chọn mô hình đối tượng khi có danh tính, quy tắc trạng thái và trách nhiệm cộng tác đáng để bảo vệ. Lối mệnh lệnh, hàm và khai báo có thể hiện diện bên trong một thiết kế OOP; lựa chọn hợp lý không phải cam kết “chỉ được dùng đối tượng”.

**Chọn phương án theo việc cần làm.** Muốn tính tổng ba khoản thu bất biến `[10,20,30]`, một hàm `sum(receipts)` đi qua ba số cho kết quả **60**; việc tạo `ReceiptSumManager`, `SumExecutor` và `SumResultHolder` có thể làm vấn đề đơn giản khó đọc hơn. Ngược lại, khi một Account có danh tính, quy tắc không âm, lịch sử và nhiều caller đòi rút tiền, một ranh giới hành vi `withdraw` có ý nghĩa vì nó sở hữu quyền quyết định và kết quả lỗi.

Hai lựa chọn **không loại trừ nhau**: Checkout hướng đối tượng hoàn toàn có thể gọi một hàm thuần để tính tổng giá; một chương trình mệnh lệnh vẫn có cấu trúc dữ liệu rõ ràng và kiểm thử tốt. Tiêu chí là chi phí bảo vệ quy tắc, thay đổi độc lập và độ dễ đọc — không phải khẩu hiệu “phải dùng OOP cho mọi thứ”. **Tự kiểm tra:** trong hai nhiệm vụ tính tổng và duyệt rút tiền, nhiệm vụ nào thật sự hưởng lợi từ một đối tượng có trách nhiệm trạng thái?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-design-walkthrough">Từ yêu cầu đến nhóm đối tượng cộng tác: Ví dụ thiết kế xuyên suốt</a>

<details>
<summary>Xem chi tiết</summary>

**Ca xuyên suốt: thanh toán giỏ hàng 30 bằng tài khoản ban đầu có 100.** Trước khi tổ chức trách nhiệm, Checkout có thể tự đọc số dư, tự trừ, tự gửi thư. Thay vào đó ta chọn các object với lời hứa rõ: **Account** quản lý số dư và invariant `balance >= 0`; **Checkout** điều phối yêu cầu; **FeePolicy** tính tổng/phí; **PaymentMethod** xử lý một yêu cầu thanh toán; **Notifier** gửi thông tin sau kết quả. Hai tài khoản cùng số dư vẫn là hai danh tính riêng.

**Vết thành công** (giả sử phí 0): (1) giỏ hàng báo giá30; (2) FeePolicy trả tổng30; (3) Checkout yêu cầu `WalletPayment.pay(30)`; (4) WalletPayment nhờ Account kiểm tra `30>0` và `100>=30` → đúng, Account chuyển **100→70**; (5) PaymentMethod trả `Success(receiptId)`; (6) Checkout **sau đó mới** nhờ Notifier gửi biên nhận. **Vết thất bại độc lập** bắt đầu lại từ A=100 với yêu cầu120: Account thấy thiếu tiền → **không trừ** → `Failure(insufficientFunds)` → Checkout báo lý do và **không gửi biên nhận thành công**. Đây là nhánh minh họa, không phải cam kết mọi email được gửi thành công.

**Thử đổi yêu cầu:** thêm `BankPayment` thay `WalletPayment`. Checkout vẫn gửi `pay(amount)` và nhận `Success(receiptId)` hoặc `Failure(reason)`; ngân hàng từ chối thật và trả Failure đúng hợp đồng vẫn là một biến thể hợp lệ. Thành phần lựa chọn/đăng ký phương thức có thể cần sửa, còn caller dùng hợp đồng chung không cần phân nhánh theo loại. Nếu FeePolicy đổi phí0→2, tổng phải tính lại thành32 và phản ánh trong lời hứa báo giá; thay cộng tác viên phù hợp có thể giữ cách gọi của Checkout. Đặt câu hỏi *ai đổi, ai không đổi, tại sao* để kiểm tra lợi ích cô lập trách nhiệm.

**Ranh giới quan trọng:** một ví dụ **khác** như chuyển 30 từ A=100 sang B=20 có hai cập nhật (A→70, B→50). Nếu ghi nợ thành công nhưng ghi có thất bại, OOP **không tự làm hai cập nhật nguyên tử**; khôi phục/transaction là vấn đề riêng. Đừng dùng ví dụ chuyển tiền này thay cho bài tổng hợp Checkout/PaymentMethod đang học. **Tự kiểm tra:** hãy kể đúng thứ tự gọi của ca mua30, chỉ rõ hai nhánh và thành phần nào đổi khi thêm ngân hàng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-owner-handoff">Phạm vi OOP và hướng học tiếp: Java Core, nguyên lý thiết kế, DDD, AOP</a>

<details>
<summary>Xem chi tiết</summary>

Module này dừng ở **mô hình trách nhiệm và hợp đồng đối tượng**. Java Core dạy chi tiết `class`, `interface`, quyền truy cập và quy tắc ghi đè; DDD dạy mô hình miền, ranh giới aggregate và hành vi nghiệp vụ quy mô lớn; AOP dạy mối quan tâm xuyên suốt nhiều đối tượng.

Khi một lỗi đòi hỏi hiểu cách Java lựa chọn phương thức cụ thể, chuyển sang module ngôn ngữ. Khi cần điều phối giao dịch hoặc đồng bộ trạng thái, học thêm về kiến trúc, giao dịch và xử lý đồng thời; OOP chỉ giúp phân chia trách nhiệm chứ không tạo bảo đảm vận hành tự động.

**Phân loại câu hỏi theo nơi sở hữu kiến thức.** “Account cần bảo đảm điều gì sau `withdraw`?” → đây là tư duy trách nhiệm/invariant thuộc OOP. “Trong Java viết `class`, `interface`, `private` hoặc `override` thế nào và JVM chọn phương thức nào?” → Java Core. “Một nguyên lý SOLID hoặc Law of Demeter áp dụng trên hệ thống lớn ra sao?” → Design Principles. “Order aggregate nào bảo vệ tính nhất quán miền?” → Domain-Driven Design. “Ghi audit xuyên mọi hành vi mà không lặp logic thế nào?” → AOP hoặc kỹ thuật cross-cutting chuyên biệt.

Nếu hai tài khoản cần chuyển tiền và bước ghi có thất bại sau khi đã ghi nợ, thiết kế object giúp xác định ai phối hợp nhưng **không tự cung cấp transaction, rollback hoặc đồng bộ**; hãy tìm hiểu phần giao dịch/kiến trúc tương ứng. Không cần sinh thêm H2 về Java syntax, UML notation hay Spring vào module paradigm này. **Tự kiểm tra:** bạn sẽ học ở đâu để hiểu vì sao một Java `override` cụ thể được gọi?

</details>

- [Quay lại đầu trang](#back-to-top)
