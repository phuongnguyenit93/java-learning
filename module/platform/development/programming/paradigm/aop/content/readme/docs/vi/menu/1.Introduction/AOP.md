<a id="back-to-top"></a>

# Lập trình hướng khía cạnh (AOP): Vấn đề và mô hình tư duy

## Menu
- [AOP: Khái niệm, phạm vi và mối quan tâm xuyên suốt](#aop-what)
- [Sự lặp lại của hành vi dùng chung trong nhiều thành phần](#aop-why)
- [Giải pháp tường minh bằng helper, wrapper và decorator](#aop-without)
- [Giới hạn của lời gọi thủ công và chính sách phân tán](#aop-limit)
- [Mô hình ghép logic nghiệp vụ với hành vi xuyên suốt](#aop-solution)
- [Trường hợp phù hợp và giới hạn ban đầu của AOP](#aop-when)
- [AOP trong quan hệ với trách nhiệm đối tượng và nguyên lý phân tách mối quan tâm](#aop-object-responsibility-bridge)
- [Từ nhu cầu xuyên suốt đến thuật ngữ, cơ chế ghép và quyết định thiết kế](#aop-learning-journey)

## <a id="aop-what">AOP: Khái niệm, phạm vi và mối quan tâm xuyên suốt</a>

<details>
<summary>Xem chi tiết</summary>

Lập trình hướng khía cạnh (AOP) là cách tổ chức chương trình nhằm tách những hành vi **xuyên suốt nhiều thành phần** khỏi logic nghiệp vụ chính.

Những yêu cầu như ghi log, theo dõi đường thực thi, đo thời gian, kiểm toán hoặc kiểm tra chính sách thường được gọi là **mối quan tâm xuyên suốt** (cross-cutting concern).

**Ví dụ xuyên suốt:** một ứng dụng xử lý `transferFunds` và `createInvoice`. Chuyển tiền và tạo hóa đơn là trách nhiệm nghiệp vụ riêng; ghi thời gian xử lý, gắn mã truy vết và ghi nhận lần gọi lại có thể xuất hiện ở cả hai. Chúng ta gọi loại yêu cầu lan qua nhiều ranh giới chức năng này là **mối quan tâm xuyên suốt** (cross-cutting concern). Nó không nhất thiết quan trọng kém nghiệp vụ; chỉ là cách chia theo từng đối tượng khó chứa trọn vẹn nó.

AOP (lập trình hướng khía cạnh) bổ sung **một trục tổ chức hành vi**: mô tả chính sách dùng chung và những điểm thực thi chịu chính sách ấy. Nó không tự động thay thế mọi lời gọi tường minh, không yêu cầu nghiệp vụ phải dùng Spring và cũng không có một tập join point giống nhau ở mọi công nghệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-why">Sự lặp lại của hành vi dùng chung trong nhiều thành phần</a>

<details>
<summary>Xem chi tiết</summary>

Nếu cùng một chính sách phải xuất hiện ở nhiều nơi, mã nghiệp vụ dễ bị lặp và lẫn với logic không thuộc trách nhiệm chính của từng thành phần.

Ví dụ, hàng chục tình huống sử dụng cùng cần đo thời gian và ghi nhận kiểm toán. Nếu mỗi phương thức tự viết lại đoạn xử lý đó, việc thay đổi chính sách về sau vừa tốn công vừa dễ thiếu nhất quán.

Giả sử năm dịch vụ cùng chèn `startTimer`, `stopTimer`, `writeAudit`. Nếu sau này cần che thông tin nhạy cảm trong log, ta phải tìm đủ năm nơi và mọi nhánh thoát sớm, nhánh lỗi. Chi phí đáng ngại không chỉ là số dòng lặp, mà là **một chính sách có năm bản triển khai dễ lệch nhau**.

Một yêu cầu chỉ xuất hiện ở một hàm vẫn có thể xử lý tốt bằng mã thông thường. Ta cân nhắc AOP khi chính sách áp dụng theo **ranh giới thực thi có thể nhận diện**, còn từng thành phần nên tập trung vào quyết định nghiệp vụ của nó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-without">Giải pháp tường minh bằng helper, wrapper và decorator</a>

<details>
<summary>Xem chi tiết</summary>

Cách đơn giản hơn là gọi helper, wrapper hoặc decorator một cách tường minh:

```text
business code
→ gọi logging helper
→ gọi audit helper
→ chạy logic chính
```

Cách này hoàn toàn hợp lệ và thường còn dễ hiểu hơn khi concern chỉ xuất hiện ở ít nơi.

`helper` nhận dữ liệu để ghi log nhưng người gọi vẫn chịu trách nhiệm gọi đúng lúc. `wrapper` bọc cả một thao tác và kiểm soát đường đi qua nó; `decorator` cung cấp cùng hợp đồng với đối tượng được bọc để thêm hành vi. Ba lựa chọn này không phụ thuộc vào bộ ghép aspect và rất dễ nhìn thấy từ lời gọi.

Chẳng hạn, `auditedTransfer.transfer(request)` cho biết rõ việc kiểm toán nằm trên đường gọi. Chỉ khi cùng lớp bao này phải lặp ở quá nhiều điểm và tiêu chí áp dụng có thể khai báo rõ, một aspect mới mang lại lợi ích nổi bật. Đừng xem cách tường minh là giải pháp tạm thời phải thay bỏ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-limit">Giới hạn của lời gọi thủ công và chính sách phân tán</a>

<details>
<summary>Xem chi tiết</summary>

Khi cùng một yêu cầu trải rộng trên nhiều module hoặc nhiều điểm thực thi, lời gọi hàm hỗ trợ thủ công dễ tạo mã lặp và đòi người phát triển nhớ áp dụng đúng ở mọi nơi.

AOP đưa yêu cầu xuyên suốt đó thành một đơn vị riêng và mô tả **nơi nào nó được áp dụng** thay vì chèn mã xử lý thủ công vào từng phương thức nghiệp vụ.

Khi nhánh xử lý lỗi trở về sớm, lời gọi `auditSuccess()` đặt ở cuối thủ tục có thể bị bỏ qua; thêm nhánh mới lại phải nhớ sao chép cả chính sách. Việc kiểm tra thiếu sót đòi rà soát từng vị trí gọi, không chỉ kiểm tra quy tắc ở một nơi.

Tuy vậy, **tập trung chính sách không đồng nghĩa tự động đúng**: một pointcut sai có thể bỏ sót nhiều đường thực thi cùng lúc. Chọn aspect nghĩa là chuyển rủi ro từ “quên gọi helper” sang “chọn nhầm điểm áp dụng”; cả hai đều cần kiểm thử.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-solution">Mô hình ghép logic nghiệp vụ với hành vi xuyên suốt</a>

<details>
<summary>Xem chi tiết</summary>

Mental model tổng quát:

```text
business behavior
        +
cross-cutting behavior
        ↓
composition mechanism
        ↓
effective runtime behavior
```

AOP không thay business logic. Nó bổ sung behavior tại những điểm được chọn trong execution model.

Hãy đọc sơ đồ theo hai trục. **Target** xử lý việc chuyển tiền; **aspect** định nghĩa đo thời gian; **pointcut** chọn những lần thực thi thuộc phạm vi cần đo; **advice** đo thời điểm bắt đầu/kết thúc. Bộ ghép phối hợp chúng tạo ra hành vi người dùng thực sự quan sát được.

Ví dụ một lời gọi thành công có thể được quan sát như `bắt đầu đo → transferFunds → kết quả → ghi thời gian`; khi lỗi, đường đo phải có nhánh xử lý tương ứng. Một aspect không “thay” phép chuyển tiền, nhưng loại advice bao quanh đủ quyền **không chạy tiếp** target nếu thiết kế cho phép. Điều này là lý do cần quan sát luồng thực thi hiệu lực thay vì chỉ đọc target.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-when">Trường hợp phù hợp và giới hạn ban đầu của AOP</a>

<details>
<summary>Xem chi tiết</summary>

AOP phù hợp khi concern thực sự cắt ngang nhiều phần của hệ thống và selection rule có thể mô tả rõ ràng.

Không nên dùng AOP chỉ để tránh viết vài dòng code. Nếu behavior là business flow quan trọng hoặc cần nhìn thấy trực tiếp trong control flow, code tường minh thường dễ đọc và dễ bảo trì hơn.

Hãy cân nhắc một yêu cầu theo bốn câu hỏi: nó có cắt qua nhiều đơn vị không, có thể nêu tiêu chí chọn điểm một cách ổn định không, tác động khi thất bại có kiểm soát được không, và có kiểm chứng được toàn bộ lời gọi bị ảnh hưởng không? Logging đo thời gian thường trả lời tốt hơn một quy tắc chấp thuận chuyển tiền.

Với chính sách an toàn có ảnh hưởng đến kết quả, sự minh bạch của luồng điều khiển có thể quan trọng hơn số dòng giảm được. Khi quyết định chính đến từ trạng thái nghiệp vụ đặc thù, giữ nó trong thành phần chịu trách nhiệm thay vì che qua pointcut rộng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-object-responsibility-bridge">AOP trong quan hệ với trách nhiệm đối tượng và nguyên lý phân tách mối quan tâm</a>

<details>
<summary>Xem chi tiết</summary>

Trong OOP, mỗi đối tượng nhận trách nhiệm rõ ràng: ví dụ `TransferService` kiểm tra số dư còn `InvoiceService` tính giá hóa đơn. Không đối tượng nào tự nhiên sở hữu toàn bộ chính sách đo thời gian của cả hai. Đây là hai **trục phân chia khác nhau**: trách nhiệm theo nghiệp vụ và mối quan tâm chạy qua nhiều trách nhiệm.

Nguyên lý phân tách mối quan tâm (separation of concerns) có phạm vi thiết kế rộng hơn AOP. AOP là **một kỹ thuật cụ thể** để mô-đun hóa phần xuyên suốt, không phải tên khác của nguyên lý đó. Kiến thức tiên quyết chỉ là lời gọi, đối tượng, trách nhiệm và sự phân biệt giữa logic chính với thao tác hỗ trợ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-learning-journey">Từ nhu cầu xuyên suốt đến thuật ngữ, cơ chế ghép và quyết định thiết kế</a>

<details>
<summary>Xem chi tiết</summary>

Ta đã nhận ra vấn đề lặp chính sách và các giải pháp tường minh. Chương kế tiếp đặt tên cho **aspect, join point, pointcut, advice, target, weaving** rồi lần theo một lời gọi từ đầu tới kết quả và lỗi. Chương ba đối chiếu ghép bytecode với chặn lời gọi qua proxy; vì cách ghép quyết định nơi nào thực sự áp dụng được advice.

Cuối cùng, ta trở lại ví dụ chuyển tiền/ghi log để kiểm tra phạm vi, độ khó truy vết và so sánh với wrapper/decorator. Người học không cần viết cú pháp AspectJ hoặc cấu hình bean Spring ở đây: mục đích là có thể **giải thích và đánh giá** một thiết kế AOP trước khi học công nghệ cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)
