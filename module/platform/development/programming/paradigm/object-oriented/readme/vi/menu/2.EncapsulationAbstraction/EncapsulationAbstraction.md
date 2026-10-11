<a id="back-to-top"></a>

# Đối tượng, đóng gói và trừu tượng hóa

## Menu
- [Object Identity — Danh tính đối tượng khác sự bằng nhau về giá trị](#oop-object-identity)
- [State và Behavior — Trạng thái, hành vi và đối tượng bất biến](#oop-state-behavior)
- [Encapsulation — Đóng gói để kiểm soát thay đổi trạng thái](#oop-encapsulation-purpose)
- [Object Invariant — Điều kiện luôn đúng khi đối tượng hợp lệ](#oop-object-invariants)
- [Invariant và Immutability — Quy tắc nhất quán khác dữ liệu bất biến](#oop-invariant-vs-immutability)
- [Information Hiding — Che giấu chi tiết và quyết định nội bộ](#oop-behavior-boundary)
- [Abstraction — Công bố khả năng cần thiết, giấu cách thực hiện](#oop-abstraction-value)

## <a id="oop-object-identity">Object Identity — Danh tính đối tượng khác sự bằng nhau về giá trị</a>

<details>
<summary>Xem chi tiết</summary>

**Danh tính đối tượng** trả lời “đây là thực thể nào?”, còn **bằng nhau về giá trị** trả lời “nội dung đang giống nhau đến đâu?”. Hai tài khoản cùng số dư 100 không phải một tài khoản; lịch sử và quyền truy cập có thể khác nhau.

Ngược lại, hai giá trị biểu diễn cùng một khoản tiền `100 VND` có thể được coi là tương đương theo nội dung dù được tạo ở hai nơi. Chọn danh tính hay giá trị làm trọng tâm tùy ý nghĩa bài toán, không thể suy từ cú pháp tạo đối tượng.

**Quan sát hai trường hợp dễ nhầm.** Trường hợp một: A và B là hai tài khoản độc lập, cùng có 100. `A.withdraw(30)` thành công nên A còn 70, B vẫn 100; bằng nhau về số dư lúc đầu không gộp hai danh tính. Trường hợp hai: hai vị trí trong ứng dụng cùng nói tới **chính tài khoản A**, chẳng hạn màn hình thanh toán và báo cáo cùng đang xem A. Nếu màn hình gửi yêu cầu rút 30 được chấp nhận, khi báo cáo đọc lại A, nó thấy 70. Đây là hai cách sử dụng **một danh tính**, không phải hai tài khoản.

Ngược lại, `100 VND` thường được quan tâm dưới góc **giá trị**: hai khoản tiền cùng đơn vị và lượng có thể bằng nhau dù được tạo từ hai phép tính khác nhau. Chọn phép so sánh phải xuất phát từ câu hỏi nghiệp vụ: *cùng tài khoản hay cùng số tiền?* Không cần dùng Java `==` hoặc `equals()` để hiểu điều này. **Tự kiểm tra:** nếu A và B đều có 70 sau các giao dịch độc lập, ta có thể gộp lịch sử của chúng không?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-state-behavior">State và Behavior — Trạng thái, hành vi và đối tượng bất biến</a>

<details>
<summary>Xem chi tiết</summary>

Trạng thái là dữ liệu liên quan tới một đối tượng, còn hành vi là những yêu cầu đối tượng có thể đáp ứng. Tài khoản có số dư và hành vi rút tiền; hành vi kiểm tra `canWithdraw` có thể chỉ đọc trạng thái, không thay đổi gì.

Đối tượng không buộc phải cho phép sửa: giá trị địa chỉ hoặc chính sách phí có thể được tạo một lần và cung cấp hành vi tính toán. Đánh đồng OOP với dữ liệu luôn thay đổi khiến ta bỏ qua các thiết kế đối tượng bất biến hữu ích.

**Một hành vi có thể đọc hoặc thay đổi trạng thái.** A có `balance = 100`. Yêu cầu `canWithdraw(30)` chỉ **đọc** số dư và trả “có thể”, nên sau khi hỏi A vẫn có 100. Yêu cầu `withdraw(30)` khác: tài khoản kiểm tra điều kiện rồi **cập nhật** 100 thành 70 và trả kết quả. Nếu yêu cầu rút 120 bị từ chối, hành vi đã được thực hiện ở mức xử lý yêu cầu nhưng không có cập nhật số dư. Như vậy, “behavior” không đồng nghĩa “luôn thay đổi dữ liệu”.

Một `FeePolicy` bất biến có thể chứa quy tắc phí đã chọn và trả phí tính được khi nhận `calculateFee(30)`; nó không cần thay đổi chính mình sau mỗi lời gọi. Khi một object bảo vệ cách sử dụng dữ liệu của nó, người dùng thấy **hành vi có ý nghĩa**, không chỉ một chiếc túi trường dữ liệu. **Tự kiểm tra:** vì sao `canWithdraw` là hành vi dù không thay đổi `balance`?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-encapsulation-purpose">Encapsulation — Đóng gói để kiểm soát thay đổi trạng thái</a>

<details>
<summary>Xem chi tiết</summary>

**Đóng gói (encapsulation)** tạo ranh giới giữa dữ liệu nội bộ với cách bên ngoài được phép tác động lên nó. Nếu mọi nơi tự đặt `balance = -50`, tài khoản không thể bảo đảm quy tắc số dư không âm.

Một ranh giới hợp lý yêu cầu các bên gọi hành vi `withdraw(50)` và để chính tài khoản kiểm tra hợp lệ. `private` trong Java có thể hỗ trợ che giấu dữ liệu, nhưng mục tiêu thiết kế là bảo vệ **quy tắc và quyền quyết định**, không đơn thuần đánh dấu trường dữ liệu.

**Lần theo hai cách xử lý.** Ban đầu A có 100 và quy tắc không cho số dư âm. Nếu Checkout trực tiếp tính `100 - 120 = -20` rồi gán vào A trước khi kiểm tra, trạng thái -20 đã lọt qua ranh giới; phát hiện lỗi sau đó không tự khôi phục dữ liệu. Trong cách đóng gói hành vi, Checkout chỉ yêu cầu `withdraw(120)`. A kiểm tra số tiền phải dương, sau đó kiểm tra có đủ 120 không; điều kiện sai, A **trả từ chối và vẫn 100**. Với 30, cả hai điều kiện đúng nên A mới đổi thành 70.

Đóng gói không bắt buộc mỗi phương thức phải giữ mọi dữ liệu bí mật: điều quan trọng là **bên ngoài không thể đi đường vòng để vô hiệu hóa quyết định thuộc về A**. Từ khóa `private` có thể hỗ trợ hạn chế truy cập, nhưng nếu vẫn công bố hành vi `setBalance(-20)` thì quy tắc chưa được bảo vệ. **Tự kiểm tra:** bước nào phải xảy ra trước khi số dư được đổi và tại sao?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-object-invariants">Object Invariant — Điều kiện luôn đúng khi đối tượng hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

**Điều kiện bất biến của đối tượng (object invariant)** là mệnh đề phải đúng ở những thời điểm trạng thái được công bố hợp lệ. Ví dụ tài khoản không thấu chi yêu cầu `balance >= 0`; mọi hành vi tạo hoặc cập nhật tài khoản phải giữ điều kiện đó.

Trước rút 30 từ 100, điều kiện đúng; sau rút còn 70, điều kiện vẫn đúng. Yêu cầu rút 120 phải thất bại mà không tạo trạng thái -20. Đừng nhầm invariant với một biến không bao giờ đổi: số dư có thể thay đổi nhiều lần trong khi quy tắc vẫn được giữ.

**Một vết trạng thái làm rõ điều kiện bất biến.** Với A có số dư 100, quy tắc `balance >= 0` đang đúng. Nhận `withdraw(30)` → kiểm tra `30 > 0` đúng → kiểm tra `100 >= 30` đúng → cập nhật số dư thành **70** → trả thành công; bất biến vẫn đúng sau hành vi. Quay lại trạng thái ban đầu 100, nhận `withdraw(120)` → số tiền dương nhưng không đủ tiền → **không cập nhật** → trả thất bại và số dư vẫn **100**. Đó là hai nhánh chứng minh cùng một quy tắc.

“Invariant luôn đúng” ở đây nói về **trạng thái hợp lệ được công bố tại ranh giới thao tác**, không yêu cầu mọi bước tính toán tạm thời bên trong là trạng thái đối tượng được quan sát được. Một quy tắc có thể khác theo sản phẩm: tài khoản cho phép thấu chi sẽ có invariant khác; không áp quy tắc không âm cho mọi hệ thống. Điều này cũng chưa giải quyết hai yêu cầu đồng thời hoặc giao dịch phân tán. **Tự kiểm tra:** sau khi từ chối rút 120, invariant nào đúng và giá trị cụ thể nào chứng minh?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-invariant-vs-immutability">Invariant và Immutability — Quy tắc nhất quán khác dữ liệu bất biến</a>

<details>
<summary>Xem chi tiết</summary>

**Bất biến về điều kiện** là quy tắc luôn đúng, còn **dữ liệu bất biến (immutability)** nghĩa là giá trị không bị sửa sau khi tạo. Tài khoản có số dư thay đổi 100 → 70 vẫn giữ `balance >= 0`, dù trạng thái tài khoản có tính thay đổi.

Một bản ghi giao dịch bất biến vẫn có thể sai ngay từ lúc tạo, ví dụ số tiền âm trái quy tắc nghiệp vụ. Vì vậy chỉ làm dữ liệu không thể sửa không tự bảo đảm tính hợp lệ; cần kiểm tra lúc tạo và hợp đồng của hành vi liên quan.

**Đặt ba trường hợp cạnh nhau.** (1) Tài khoản A **có thể thay đổi** từ 100 xuống 70 sau rút 30, nhưng luôn giữ điều kiện `balance >= 0`: *mutable, invariant được bảo toàn*. (2) Một bản ghi giao dịch **không cho sửa sau khi tạo** nhưng ngay từ lúc tạo đã chứa `amount = -5` trái hợp đồng: *immutable, nhưng không hợp lệ*. (3) Chính sách phí được tạo với mức phí hợp lệ và không thay đổi: *immutable và hợp lệ*. Hai thuộc tính trả lời **hai câu hỏi khác nhau**.

Invariant là *quy tắc về trạng thái được coi là hợp lệ*; immutability là *khả năng sửa dữ liệu sau khi tạo*. Một đối tượng bất biến vẫn cần kiểm tra ở lúc tạo, còn đối tượng thay đổi được cần giữ invariant sau các hành vi công bố. Tránh dùng “bất biến” chung chung để kết luận dữ liệu luôn đứng yên. **Tự kiểm tra:** chỉ vì một transaction không sửa được, có kết luận số tiền âm của nó hợp lệ không?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-behavior-boundary">Information Hiding — Che giấu chi tiết và quyết định nội bộ</a>

<details>
<summary>Xem chi tiết</summary>

Đối tượng nên công bố những **hành vi mang ý nghĩa với người sử dụng** thay vì buộc bên gọi biết phải sửa các trường nào. `withdraw(amount)` diễn đạt một yêu cầu nghiệp vụ; công khai `setBalance(value)` để mọi nơi tự tính có thể phá vỡ quy tắc.

Chi tiết lưu trữ số dư, quy tắc ghi lịch sử và cách chọn công thức phí có thể được thay đổi phía sau hợp đồng nếu kết quả quan sát được vẫn đúng. Ranh giới này giúp khoanh vùng ảnh hưởng thay đổi nhưng phải tránh giấu các tác động phụ quan trọng.

**Che giấu một quyết định thật sự có thể đổi.** Phiên bản đầu của Account có thể lưu trực tiếp `balance = 100`; phiên bản sau lưu danh sách khoản nạp/rút rồi **tính số dư từ lịch sử**. Checkout không nên cần biết Account đã dùng một trường số hay cộng các dòng lịch sử. Checkout vẫn gửi `withdraw(30)`; Account kiểm tra số dư khả dụng, chấp nhận và khi bên gọi hỏi thì thấy **70** trong cả hai phiên bản. Chính **quyết định biểu diễn nội bộ** được giấu, còn hợp đồng và kết quả cần thiết được giữ nguyên.

Phân biệt ba góc nhìn: **encapsulation** kiểm soát *ai được phép đổi trạng thái qua hành vi nào*; **information hiding** bảo vệ *lựa chọn lưu trữ/quy tắc nội bộ dễ đổi* khỏi sự lệ thuộc của caller; **abstraction** cho caller biết *khả năng và kết quả nào có thể trông đợi*. Không che lỗi thanh toán hoặc các tác động phụ đã hứa thông báo. **Tự kiểm tra:** đổi từ một trường số dư sang sổ lịch sử có bắt Checkout tính lại số dư không, nếu hợp đồng vẫn đúng?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oop-abstraction-value">Abstraction — Công bố khả năng cần thiết, giấu cách thực hiện</a>

<details>
<summary>Xem chi tiết</summary>

**Trừu tượng hóa (abstraction)** tập trung vào điều một đối tượng **cam kết làm được**, không phải từng chi tiết triển khai. Bên gọi dịch vụ thanh toán chỉ cần yêu cầu `pay(amount)` và biết trường hợp thành công/thất bại; không cần biết phí được tính trong bao nhiêu bước.

Một hợp đồng trừu tượng hữu ích không nên quá rộng như `doEverything()`, cũng không nên lộ toàn bộ dữ liệu nội bộ. Hãy diễn đạt yêu cầu nghiệp vụ cùng điều kiện và kết quả quan sát được để bên trong còn không gian cải tiến.

**Đặt mình vào vị trí người gọi.** Checkout cần yêu cầu `pay(30)` và hiểu kết quả `Success(receiptId)` hoặc `Failure(reason)`. Nó cần biết một số tiền hợp lệ để xử lý và cách phản hồi thất bại; **không cần biết** nhà cung cấp dùng bao nhiêu bước, phí được tra ở đâu hoặc dữ liệu được lưu kiểu nào. Abstraction gom những điều bên gọi thực sự cần thành một **khả năng dễ hiểu**, thay vì đưa ra hành vi mơ hồ như `doEverything()` hoặc hàng chục thao tác sửa field nội bộ.

Nếu hai phương thức thanh toán khác nhau cùng giữ lời hứa trả kết quả tường minh, bên gọi có thể giữ nguyên cách tương tác dù phần bên trong thay đổi. Ngược lại, giấu cả điều kiện lỗi sẽ làm abstraction kém hữu ích: *trừu tượng hóa* không phải *bịt mọi thông tin*. Ở bước này ta chỉ xác định lời hứa, chưa cần lập trình `interface` Java. **Tự kiểm tra:** điều gì Checkout bắt buộc cần biết để xử lý giao dịch bị từ chối?

</details>

- [Quay lại đầu trang](#back-to-top)
