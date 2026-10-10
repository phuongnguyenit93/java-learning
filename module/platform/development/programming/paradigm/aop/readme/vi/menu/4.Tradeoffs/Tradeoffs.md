<a id="back-to-top"></a>

# Lựa chọn AOP: Lợi ích, rủi ro và tình huống áp dụng

## Menu
- [Giảm lặp và tập trung chính sách xuyên suốt](#aop-benefits)
- [Hành vi ngầm, độ phụ thuộc và chi phí truy vết](#aop-cost)
- [Mối quan tâm xuyên suốt phù hợp để tách thành aspect](#aop-good-fit)
- [Luồng nghiệp vụ cần giữ tường minh](#aop-bad-fit)
- [Ranh giới paradigm với framework và công nghệ AOP](#aop-boundary)
- [Pointcut quá rộng, advice chồng lấn và thứ tự khó dự đoán](#aop-pointcut-pitfalls)
- [Từ yêu cầu logging hoặc auditing đến quyết định dùng aspect](#aop-design-walkthrough)
- [So sánh quyết định cuối cùng với helper, wrapper và decorator](#aop-explicit-alternative-decision)

## <a id="aop-benefits">Giảm lặp và tập trung chính sách xuyên suốt</a>

<details>
<summary>Xem chi tiết</summary>

AOP có thể giảm mã lặp, tập trung chính sách xuyên suốt tại một nơi và giữ mã nghiệp vụ hướng về nhiệm vụ chính.

Giảm lặp mang ý nghĩa nhất khi **cùng chính sách** áp dụng cho nhiều đường độc lập. Đổi cấu trúc bản ghi đo thời gian ở một aspect có thể đồng bộ nhiều dịch vụ mà không chạm vào thuật toán chuyển tiền hay lập hóa đơn; test riêng pointcut cũng giúp mô tả phạm vi kỳ vọng.

Lợi ích này có điều kiện: lựa chọn join point phải ổn định và đội ngũ phải biết hành vi đang nằm ngoài source target. Nếu concern chỉ hiện ở hai lời gọi, wrapper tường minh có thể giảm tổng chi phí nhận thức hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-cost">Hành vi ngầm, độ phụ thuộc và chi phí truy vết</a>

<details>
<summary>Xem chi tiết</summary>

Hành vi thực sự được bổ sung có thể không xuất hiện trực tiếp trong mã nguồn của thành phần đích.

Việc gỡ lỗi, truy vết luồng điều khiển và suy luận về thứ tự thực thi sẽ khó hơn nếu có quá nhiều aspect hoặc quy tắc chọn điểm áp dụng quá rộng.

Một người đọc `transferFunds` có thể không thấy timer hoặc logic kiểm toán dù chúng chạy thực tế. Vì vậy stack trace, log lỗi và kết quả có thể không khớp trực giác chỉ đọc một hàm. Chi phí bảo trì nằm ở **mô hình thực thi hiệu lực**, không chỉ số lượng lớp aspect.

Điều tra lỗi nên bắt đầu từ “call đi qua proxy/weaver nào?”, “pointcut nào khớp?” và “advice có đổi kết quả/nuốt lỗi không?”. Test chỉ target trực tiếp có thể bỏ qua aspect; test tích hợp qua đúng ranh giới sẽ quan sát được chính sách.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-good-fit">Mối quan tâm xuyên suốt phù hợp để tách thành aspect</a>

<details>
<summary>Xem chi tiết</summary>

Những concern thường phù hợp có đặc điểm:

- lặp lại ở nhiều execution boundary;
- ít phụ thuộc vào business meaning riêng của từng use case;
- có rule áp dụng rõ ràng;
- có thể test độc lập.

Một concern thích hợp thường **độc lập tương đối với dữ liệu chuyên ngành của từng nghiệp vụ** và có tiêu chí chọn điểm kiểm chứng được. Đo thời gian của mọi operation trong một lớp dịch vụ hoặc gắn mã tương quan cho một tập call là ví dụ tương đối rõ; chính sách bảo mật có thể dùng AOP nhưng đòi kiểm tra coverage và fail-closed nghiêm ngặt.

Hãy chứng minh hai trường hợp `matched` và `not matched` cùng đầu ra quan sát, chứ không chỉ lập danh sách annotation dự định dùng. Nếu phải biết rất nhiều trạng thái đặc thù để quyết định mỗi nhánh, concern có thể đang thuộc logic nghiệp vụ thay vì một aspect.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-bad-fit">Luồng nghiệp vụ cần giữ tường minh</a>

<details>
<summary>Xem chi tiết</summary>

Quyết định nghiệp vụ quan trọng, quy trình chính hoặc hành vi cần theo dõi trực tiếp thường không nên bị ẩn sau aspect.

Rule thực tế:

```text
cross-cutting policy  → AOP có thể phù hợp
core business flow    → ưu tiên code tường minh
```

Ví dụ nghiệp vụ chuyển tiền cần `kiểm tra tài khoản → giữ hạn mức → ghi sổ → phản hồi`. Đó là **câu chuyện chính** mà người bảo trì cần nhìn trực tiếp và có thể có quy tắc bù trừ; nếu rải từng bước trong nhiều advice, thứ tự nghiệp vụ sẽ bị che và khó chứng minh đúng đắn.

Aspect có thể đo thời gian cả thao tác chuyển tiền, nhưng không nên biến “đã kiểm tra hạn mức” thành một tác dụng phụ không hiện trên hợp đồng của `TransferService`. Ranh giới tường minh giúp code review, test và xử lý lỗi hiểu cùng một luồng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-boundary">Ranh giới paradigm với framework và công nghệ AOP</a>

<details>
<summary>Xem chi tiết</summary>

Module này chỉ sở hữu AOP ở mức paradigm.

Các implementation cụ thể như framework proxy/interceptor hoặc weaving engine nên được học tại module công nghệ tương ứng thay vì đưa toàn bộ chi tiết implementation vào đây.

Ở mức paradigm, ta cần hiểu concern, join point, pointcut, advice, target và thời điểm ghép; **cấu hình** Spring proxy, thứ tự interceptor, cú pháp biểu thức pointcut, compiler AspectJ và annotation giao dịch là các cơ chế cụ thể. Không nên nhìn `@Transactional` rồi suy mọi hành vi giao dịch đều do AOP “tự nhiên” cung cấp.

Khi chuyển sang module Spring aspect/transaction, hãy mang theo câu hỏi về đường đi qua proxy, loại join point, cách xử lý lỗi và ranh giới giao dịch. Bài học hiện tại cung cấp khung lý luận để đọc tài liệu đó, không triển khai thay tài liệu framework.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-pointcut-pitfalls">Pointcut quá rộng, advice chồng lấn và thứ tự khó dự đoán</a>

<details>
<summary>Xem chi tiết</summary>

Pointcut kiểu “mọi method có tên `save*`” có thể chọn cả phương thức không cần audit hoặc bỏ sót `persistOrder`. Đây là lỗi **quá rộng và quá hẹp**, không chỉ là lỗi cú pháp. Thêm hai around advice cùng khớp có thể khiến phép đo lồng nhau, hoặc thứ tự kiểm tra policy/logging đổi theo cấu hình ưu tiên.

Hãy kiểm thử ma trận `transferFunds` có khớp, `formatMoney` không khớp, trường hợp thành công, ném lỗi và gọi nội bộ. Đừng giả định thứ tự aspect ổn định nếu chưa định nghĩa và kiểm chứng cơ chế sắp xếp của nền tảng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-design-walkthrough">Từ yêu cầu logging hoặc auditing đến quyết định dùng aspect</a>

<details>
<summary>Xem chi tiết</summary>

Yêu cầu: ba dịch vụ phải ghi **thời lượng xử lý cả khi thất bại**, nhưng không ghi số tài khoản. (1) Tách chính sách đo khỏi nghiệp vụ; (2) chọn tập operation dịch vụ bằng pointcut đủ hẹp; (3) dùng advice có đường dọn dẹp khi thành công hoặc lỗi; (4) đo tại ranh giới đủ điều kiện; (5) kiểm thử cả operation không được chọn.

```text
caller → [timing boundary] → transferFunds → success/error
       ← [elapsed + safe operation id] ←
```

Thử cố ý làm cho `transferFunds` ném lỗi: nếu không có bản ghi thời lượng, chính sách chưa thỏa yêu cầu. Nếu ghi cả dữ liệu nhạy cảm, tách concern vẫn chưa đủ tốt; thiết kế phải quy định nội dung ghi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-explicit-alternative-decision">So sánh quyết định cuối cùng với helper, wrapper và decorator</a>

<details>
<summary>Xem chi tiết</summary>

**Helper** phù hợp khi người gọi phải chủ động truyền dữ liệu; **wrapper/decorator** phù hợp khi cần ranh giới tường minh cùng hợp đồng; **aspect** phù hợp khi có nhiều execution boundary cần cùng một chính sách được chọn theo tiêu chí ổn định. Không có lựa chọn nào thắng chỉ vì ít dòng hơn.

Quay lại ví dụ đo thời gian: ba operation với wrapper rõ ràng có thể dễ kiểm thử hơn một bộ pointcut phức tạp; vài trăm operation chịu chính sách đồng nhất có thể khiến aspect hữu ích hơn. Quyết định cuối cần chứng cứ về phạm vi chọn, hành vi lỗi, khả năng debug và chi phí thay đổi — không chỉ là sở thích công nghệ.

</details>

- [Quay lại đầu trang](#back-to-top)
