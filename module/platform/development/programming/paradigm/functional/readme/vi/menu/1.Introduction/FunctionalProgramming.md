<a id="back-to-top"></a>

# Lập trình hàm: Mục đích và mô hình tư duy

## Menu
- [Lập trình hàm: Khái niệm và phạm vi](#functional-what)
- [Khó khăn khi suy luận về trạng thái dùng chung có thể thay đổi](#functional-why)
- [Lối mệnh lệnh và động cơ của cách lập trình hàm](#functional-without)
- [Điểm xuất phát: Hàm, giá trị và các lối tổ chức chương trình](#functional-prerequisites)
- [Mô hình cốt lõi: Đầu vào tường minh, biến đổi và ghép hàm](#functional-solution)
- [Tình huống áp dụng và giới hạn của lối lập trình hàm](#functional-when)
- [Lộ trình học: Hàm thuần, giá trị, ghép hàm và tác động phụ](#functional-learning-journey)

## <a id="functional-what">Lập trình hàm: Khái niệm và phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

Lập trình hàm (Functional Programming) là cách tổ chức chương trình xoay quanh **hàm, phép biến đổi dữ liệu và ghép hàm**, thay vì đặt việc sửa dữ liệu và chuỗi lệnh thay đổi trạng thái làm trung tâm.

Mục tiêu không phải là “mọi thứ đều phải là hàm”, mà là làm cho phần lớn logic có thể được hiểu qua mô hình:

```text
input
→ transformation
→ output
```

Hãy hình dung việc tính tiền một đơn hàng. Thay vì hỏi “biến tổng tiền đã bị lệnh nào thay đổi?”, ta có thể hỏi “với đơn hàng này và quy tắc này, kết quả tính toán là bao nhiêu?”. **Lập trình hàm** ưu tiên diễn đạt phần tính toán như những phép biến đổi giá trị có đầu vào và đầu ra rõ ràng, rồi kết hợp các phép biến đổi nhỏ thành công việc lớn hơn.

Một chương trình thực tế vẫn phải nhận yêu cầu, lưu dữ liệu và gọi dịch vụ. Lối hàm không phủ nhận những công việc đó; nó giúp tách phần tính toán có thể suy luận độc lập khỏi phần tương tác với thế giới bên ngoài. Đây là tư duy thiết kế, không phải yêu cầu đổi ngôn ngữ hoặc thay mọi vòng lặp bằng một chuỗi hàm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-why">Khó khăn khi suy luận về trạng thái dùng chung có thể thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

Khi logic phụ thuộc nhiều vào **trạng thái dùng chung có thể thay đổi**, kết quả của một đoạn mã có thể bị ảnh hưởng bởi những thay đổi xảy ra ở nơi khác và vào thời điểm khác.

Lập trình hàm giảm loại phụ thuộc đó bằng cách ưu tiên **hàm có đầu vào và đầu ra rõ ràng**, hạn chế tác động phụ và khuyến khích dữ liệu bất biến.

Giả sử hàm tính giảm giá tự đọc một biến `tyLeKhuyenMai` dùng chung. Cùng đơn hàng 100 đồng, lần đầu cho kết quả 90, lần sau lại 80 vì biến thay đổi ở nơi khác. Muốn giải thích sự khác biệt, người đọc phải biết **trạng thái ẩn** và thời điểm có người sửa nó.

Nếu truyền tỷ lệ giảm giá vào tham số, `giamGia(100, 0.1)` luôn tính 90 theo cùng quy tắc; ca kiểm thử không cần dựng lại toàn bộ ứng dụng. Lợi ích chính là giảm phụ thuộc ngầm, giúp tái sử dụng và kiểm thử. Không nên đồng nhất điều này với lời hứa rằng code hàm luôn nhanh hơn hay không bao giờ cần trạng thái.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-without">Lối mệnh lệnh và động cơ của cách lập trình hàm</a>

<details>
<summary>Xem chi tiết</summary>

Lập trình mệnh lệnh vẫn hoàn toàn hợp lệ: ta có thể thay đổi biến, cập nhật đối tượng và điều khiển luồng xử lý bằng các câu lệnh.

Vấn đề xuất hiện khi việc sửa trạng thái và tác động phụ lan rộng đến mức khó suy luận, kiểm thử hoặc xử lý đồng thời.

Lối mệnh lệnh có thể dùng một vòng lặp cộng dồn vào biến cục bộ rất dễ đọc; **thay đổi trạng thái không mặc nhiên là sai**. Vấn đề tăng lên khi nhiều thành phần cùng giữ tham chiếu đến danh sách đơn hàng và sửa nó trong lúc tính tổng, khiến kết quả phụ thuộc thứ tự chạy.

Một lựa chọn theo lối hàm là đọc một bản dữ liệu ổn định, lọc những đơn được duyệt rồi tính ra tổng mới mà không sửa nguồn. Hai cách viết có thể cùng tồn tại: vòng lặp cục bộ để xử lý hiệu quả, hàm thuần cho quy tắc giá và bộ điều phối tuần tự cho việc thanh toán. Chọn cách làm giúp người bảo trì dễ nhận ra **ai được phép thay đổi cái gì**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-prerequisites">Điểm xuất phát: Hàm, giá trị và các lối tổ chức chương trình</a>

<details>
<summary>Xem chi tiết</summary>

Trước khi bắt đầu, bạn chỉ cần hiểu giá trị như số 100, biến giữ giá trị, điều kiện lọc dữ liệu và hàm nhận tham số để trả kết quả. **Giá trị** mô tả dữ liệu; **trạng thái** mô tả giá trị hiện tại của một thực thể có thể thay đổi theo thời gian. Trong module này ta sẽ học lại từ đầu hai khái niệm mới: hàm **thuần** và giá trị **bất biến**.

Tư duy khai báo nhấn mạnh kết quả cần đạt; tư duy hàm đi sâu vào cách biểu diễn kết quả bằng phép biến đổi có thể ghép. Bạn không cần biết cú pháp lambda, functional interface hoặc Java Stream trước khi đọc tiếp. Những phần đó thuộc module Java, không phải điều kiện để hiểu mô hình đang học.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-solution">Mô hình cốt lõi: Đầu vào tường minh, biến đổi và ghép hàm</a>

<details>
<summary>Xem chi tiết</summary>

Mô hình tư duy:

```text
explicit input
    ↓
pure or controlled transformation
    ↓
explicit output
    ↓
compose transformations
```

Các ý tưởng quan trọng gồm hàm thuần (pure function), giá trị bất biến (immutability), hàm như giá trị và hàm bậc cao (first-class/higher-order function), ghép hàm (composition) và tính minh bạch tham chiếu (referential transparency).

Lấy đơn hàng có giá gốc 100, mức giảm 10% và thuế 5% tính **sau giảm giá**. Ta viết hai bước rõ nghĩa: `giamGia(100, 10%) = 90` và `tinhThue(90, 5%) = 94,5`. Đầu ra bước trước trở thành đầu vào bước sau; có thể kiểm tra riêng từng bước trước khi kiểm tra tổng thể.

```text
giá gốc 100 → giảm 10% → 90 → cộng thuế 5% → 94,5
```

Nếu thay quy tắc thuế hoặc thêm làm tròn tiền tệ, thay đổi phải được biểu diễn thành một phép tính rõ ràng. **Việc thu tiền** vẫn là thao tác bên ngoài không thể được thay bằng một giá trị trên giấy; nó cần được điều phối sau khi phần tính toán đã cho kết quả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-when">Tình huống áp dụng và giới hạn của lối lập trình hàm</a>

<details>
<summary>Xem chi tiết</summary>

Lối lập trình này đặc biệt hữu ích cho phép biến đổi dữ liệu, chuỗi xử lý, quy tắc nghiệp vụ có thể biểu diễn thành các hàm nhỏ và logic cần dễ kiểm thử.

Lập trình hàm không loại bỏ tác động phụ. Thao tác vào/ra (I/O), cơ sở dữ liệu, mạng và tương tác có trạng thái vẫn tồn tại; điểm quan trọng là **cô lập và kiểm soát chúng**.

Những bài toán có dữ liệu đầu vào xác định như chuẩn hóa báo cáo, kiểm tra điều kiện đặt hàng, tính giá, xếp loại điểm hoặc tổng hợp số liệu thường phù hợp với các hàm biến đổi nhỏ. Ta có thể chạy kiểm thử cho từng quy tắc bằng ví dụ cố định, rồi ghép kết quả để xem toàn bộ luồng có đúng không.

Ngược lại, kết nối mạng, ghi cơ sở dữ liệu và thanh toán thay đổi hệ thống thật. Đừng gọi chúng là “thuần” chỉ vì nằm bên trong pipeline. Nếu một vòng lặp ngắn hoặc một đối tượng chịu trách nhiệm biểu diễn vấn đề rõ hơn, hãy dùng nó. Lập trình hàm là một **công cụ lựa chọn ranh giới**, không phải mục tiêu phải áp dụng cho mọi dòng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-learning-journey">Lộ trình học: Hàm thuần, giá trị, ghép hàm và tác động phụ</a>

<details>
<summary>Xem chi tiết</summary>

Chương tiếp theo giải thích **hàm thuần**: thế nào là cùng đầu vào cùng kết quả, đầu vào nào bị giấu và tại sao một biểu thức có thể được thay bằng giá trị. Sau đó ta phân biệt **giá trị bất biến** với **trạng thái ứng dụng thay đổi**; tạo phiên bản mới không đồng nghĩa chương trình không còn trạng thái.

Khi đã nắm hai nền tảng ấy, ta học **hàm như giá trị**, hàm bậc cao và phép ghép qua ví dụ đơn hàng. Chương tác động phụ đưa I/O, lỗi và thanh toán về ranh giới rõ; chương cuối đánh giá chi phí cấp phát, khả năng đọc và lựa chọn phối hợp với lối mệnh lệnh, hướng đối tượng, hướng dữ liệu hoặc reactive.

</details>

- [Quay lại đầu trang](#back-to-top)
