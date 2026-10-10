<a id="back-to-top"></a>

# Lựa chọn và kết hợp lập trình hàm

## Menu
- [Những tình huống phù hợp với phép biến đổi theo lối hàm](#functional-use-cases)
- [Đánh đổi về khả năng đọc, cấp phát và gỡ lỗi](#functional-costs)
- [Lựa chọn giữa lối hàm, mệnh lệnh và hướng đối tượng](#functional-vs-other-styles)
- [Suy luận đầu cuối cho một bài toán xử lý nhỏ](#functional-end-to-end)
- [Ranh giới với API hàm Java và luồng reactive](#functional-boundaries)

## <a id="functional-use-cases">Những tình huống phù hợp với phép biến đổi theo lối hàm</a>

<details>
<summary>Xem chi tiết</summary>

Lối hàm phù hợp với dữ liệu xác định và các quy tắc có thể diễn đạt thành biến đổi nhỏ: tính giá, chuẩn hóa báo cáo, đối chiếu điều kiện đặt hàng và tổng hợp thông tin. Một lời giải tốt cho phép người đọc kiểm tra kết quả từ đầu vào và từng bước xử lý, không phải truy tìm nơi vừa thay đổi đối tượng dùng chung.

Khi công việc chủ yếu là giữ một kết nối thiết bị hoặc điều phối nhiều hệ thống, trạng thái thật vẫn cần người quản lý. Các phép tính bên trong quy trình ấy có thể thuần, nhưng toàn bộ quy trình không cần bị ép thành một hàm thuần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-costs">Đánh đổi về khả năng đọc, cấp phát và gỡ lỗi</a>

<details>
<summary>Xem chi tiết</summary>

Giá trị bất biến thuận lợi cho việc giữ bản cũ và giảm sửa ngầm, nhưng có thể tăng chi phí cấp phát và thu gom rác. Cấu trúc dữ liệu lưu phiên bản có thể **chia sẻ các nhánh không đổi**, song không phải mọi tập hợp bất biến đều được triển khai như vậy.

Nhiều hàm vô danh lồng nhau cũng khiến truy vết khó khăn. Nên đặt tên bước theo nghiệp vụ, kiểm tra giá trị trung gian và đo hiệu năng ở những nơi cần thiết. Một biến đếm cục bộ được kiểm soát vẫn có thể đơn giản và hiệu quả hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-vs-other-styles">Lựa chọn giữa lối hàm, mệnh lệnh và hướng đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

Lối **mệnh lệnh** biểu diễn thứ tự công việc, **hướng đối tượng** đặt trách nhiệm trong các đối tượng cộng tác, còn **hướng hàm** nhấn mạnh biến đổi giá trị và ghép hàm. Chúng là những cách tổ chức khác nhau, không phải ba loại chương trình loại trừ nhau.

Ví dụ `Order` giữ các điều kiện nhất quán, một hàm thuần tính mức giảm giá, và bộ điều phối tuần tự gọi cổng thanh toán. Mỗi phần nên có ranh giới rõ giúp kiểm thử và bảo trì, thay vì áp một phong cách lên tất cả các bước.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-end-to-end">Suy luận đầu cuối cho một bài toán xử lý nhỏ</a>

<details>
<summary>Xem chi tiết</summary>

Với đơn A giá 100, hãy đọc bản dữ liệu ổn định và chính sách giảm 10% cùng thuế 5% **tính sau giảm giá**. Hàm giảm giá trả 90, hàm tính thuế trả 4,5, và tổng thanh toán là 94,5. Mỗi kết quả có thể được kiểm thử riêng để xác định bước sai.

```text
đọc A(100) → giảm 10% → 90 → thuế 5% → tổng 94,5
                                        → thu tiền → lưu
```

Bộ điều phối mới là nơi gọi cổng thanh toán, ghi dữ liệu và xử lý yêu cầu trùng. Khi mức giảm thay đổi, kết quả phần tính toán phải đổi dự đoán được; không vì thế mà kết luận giao dịch ngoài đã thành công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-boundaries">Ranh giới với API hàm Java và luồng reactive</a>

<details>
<summary>Xem chi tiết</summary>

Ở đây ta học **tư duy lập trình hàm**: hàm thuần, giá trị bất biến, hàm như giá trị và ghép phép biến đổi. Cú pháp lambda, functional interface, method reference và cơ chế Java Stream thuộc module Java, không phải những kỹ năng tiên quyết phải biết.

Lập trình **hướng dữ liệu** chú trọng biểu diễn dữ liệu tường minh và tách thao tác khỏi dữ liệu. Lập trình **reactive** quan tâm tín hiệu đến theo thời gian, subscription và đôi khi backpressure. Chúng có thể dùng những phép biến đổi theo lối hàm, nhưng không đồng nhất với lập trình hàm thuần.

### Tài liệu tham khảo

- [Clojure — Functional Programming](https://clojure.org/about/functional_programming): hàm như giá trị và giá trị bất biến.
- [Values and Change](https://clojure.org/about/state): danh tính và trạng thái thay đổi qua những ảnh chụp dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)
