<a id="back-to-top"></a>

# Lập trình hướng dữ liệu: Mục đích và mô hình cốt lõi

## Menu
- [Lập trình hướng dữ liệu: Khái niệm và phạm vi](#dop-concept)
- [Chi phí khi dữ liệu gắn chặt với hành vi xử lý](#dop-motivation)
- [Điểm xuất phát: Giá trị, tập dữ liệu và lối lập trình đã biết](#dop-prerequisites)
- [Đối tượng, hàm và dữ liệu dưới các góc nhìn thiết kế](#dop-context)
- [Bốn nguyên tắc Sharvit và mối quan hệ giữa chúng](#dop-sharvit-model)
- [Phân biệt DOP theo Sharvit, DOP trong Java và DOD tối ưu hiệu năng](#dop-terminology)
- [Lộ trình từ biểu diễn dữ liệu đến quyết định thiết kế](#dop-learning-path)

## <a id="dop-concept">Lập trình hướng dữ liệu: Khái niệm và phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

**Lập trình hướng dữ liệu** (Data-Oriented Programming, DOP) bắt đầu bằng câu hỏi: dữ liệu ứng dụng được biểu diễn ra sao để nhiều thao tác khác nhau có thể đọc, kiểm tra và biến đổi nó? Theo hướng của Yehonathan Sharvit, ta thường dùng giá trị đơn giản như map, danh sách, số và chuỗi; các thao tác nằm ở những hàm riêng thay vì được gắn chặt vào đối tượng chứa dữ liệu.

Ví dụ đơn A có mã, danh sách mặt hàng và trạng thái. Hàm tính tiền, kiểm tra đơn và tạo báo cáo đều có thể nhận **cùng một bản dữ liệu dễ quan sát**. Đây là một lựa chọn thiết kế chứ không bắt mọi chương trình loại bỏ đối tượng, bỏ kiểm tra kiểu hay công khai bí mật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-motivation">Chi phí khi dữ liệu gắn chặt với hành vi xử lý</a>

<details>
<summary>Xem chi tiết</summary>

Giả sử một đối tượng `Order` gộp cả tính giá, trình bày báo cáo, xuất dữ liệu và kiểm tra thanh toán. Khi bộ phận phân tích chỉ cần đọc danh sách hàng, họ phải phụ thuộc vào những phương thức hoặc bộ chuyển đổi gắn với đối tượng ấy. Thay đổi một hình thức báo cáo có thể kéo theo sửa lớp không thực sự sở hữu yêu cầu báo cáo.

Tách **dữ liệu được lưu** khỏi **cách xử lý** giúp nhiều thao tác cùng tái sử dụng giá trị đơn hàng. Đổi lại, những điều kiện nhất quán từng được giữ bên trong đối tượng phải được kiểm soát bằng hợp đồng dữ liệu và kiểm tra ranh giới. DOP không khẳng định đóng gói luôn xấu; vấn đề là lựa chọn đúng nơi sở hữu hành vi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-prerequisites">Điểm xuất phát: Giá trị, tập dữ liệu và lối lập trình đã biết</a>

<details>
<summary>Xem chi tiết</summary>

Bạn chỉ cần biết **giá trị** là dữ liệu như số 2 hoặc chuỗi `"A"`, **danh sách** lưu một dãy phần tử, còn **map** ghép khóa với giá trị, chẳng hạn `{"id":"A"}`. **Hàm** nhận đầu vào và trả kết quả hoặc thực hiện công việc. Không cần biết thư viện schema, cú pháp Java record hay cơ sở dữ liệu trước khi bắt đầu.

Lập trình hướng đối tượng đã giới thiệu việc đối tượng giữ trách nhiệm, còn lập trình hàm giới thiệu phép biến đổi và tính bất biến. Module này chỉ kết hợp vừa đủ những tiền đề ấy để giải thích **tư duy tập trung vào dữ liệu**; các thuật ngữ DOP sẽ được dạy lại từ đầu, không giả định đã học một thư viện cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-context">Đối tượng, hàm và dữ liệu dưới các góc nhìn thiết kế</a>

<details>
<summary>Xem chi tiết</summary>

Cùng đơn hàng A, có ba cách nhìn bổ trợ nhau. Theo **hướng đối tượng**, đơn có thể tự bảo vệ điều kiện hợp lệ và cung cấp hành vi. Theo **hướng hàm**, ta nhấn mạnh phép tính nhận đơn và cho ra kết quả dự đoán được. Theo **hướng dữ liệu**, ta làm cấu trúc đơn dễ đọc, rồi đặt các thao tác độc lập bên ngoài.

Ví dụ `tinhTong(don)` và `taoBaoCao(don)` cùng đọc `don.lines`, không cần mỗi hàm là phương thức của đơn. Tách dữ liệu khỏi thao tác **không tự làm hàm thuần**: `guiEmail(don)` vẫn tạo tác động phụ. Các phong cách có thể kết hợp tại những ranh giới phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-sharvit-model">Bốn nguyên tắc Sharvit và mối quan hệ giữa chúng</a>

<details>
<summary>Xem chi tiết</summary>

Bốn nguyên tắc DOP theo Yehonathan Sharvit là: **(1) tách code khỏi dữ liệu; (2) biểu diễn dữ liệu bằng cấu trúc phổ dụng; (3) ưu tiên dữ liệu bất biến; (4) tách mô tả cấu trúc (schema) khỏi chính dữ liệu**. Chúng giải quyết bốn vấn đề khác nhau: quyền sở hữu hành vi, cách nhìn giá trị, sự thay đổi và cách xác minh hình dạng dữ liệu.

```text
đơn dạng map → kiểm schema tại ranh giới
            → hàm tính tổng riêng → map kết quả mới
```

Map dễ dùng lại nhưng cũng dễ sai tên khóa, nên nguyên tắc thứ tư rất quan trọng khi dữ liệu đến từ nơi không tin cậy. DOP không yêu cầu schema phải chặn mọi phép biến đổi tạm thời; chọn kiểm tra dựa trên rủi ro và hợp đồng sử dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-terminology">Phân biệt DOP theo Sharvit, DOP trong Java và DOD tối ưu hiệu năng</a>

<details>
<summary>Xem chi tiết</summary>

**DOP của Sharvit** đặt trọng tâm ở map/list phổ dụng, dữ liệu bất biến, thao tác tách riêng và schema độc lập. **DOP trong Java/Project Amber v1.1** là cách diễn đạt có kiểu tĩnh, ưu tiên biểu diễn dữ liệu bất biến minh bạch, đầy đủ và chính xác, loại trừ trạng thái bất hợp lệ cùng thao tác tách khỏi dữ liệu. Chúng có mục tiêu tương đồng nhưng không đồng nhất về biểu diễn.

Còn **Data-Oriented Design (DOD)** theo hướng hiệu năng lại nghiên cứu cách bố trí bộ nhớ, cache, SIMD và xử lý hàng loạt. Đặt tên gần nhau không đồng nghĩa dùng map bất biến sẽ làm CPU nhanh hơn. Bài này học **mô hình lập trình**, không đi sâu tối ưu phần cứng hoặc cú pháp Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-learning-path">Lộ trình từ biểu diễn dữ liệu đến quyết định thiết kế</a>

<details>
<summary>Xem chi tiết</summary>

Trước tiên ta tách giá trị đơn hàng khỏi các hàm sử dụng nó, rồi dùng map và danh sách lồng nhau để biểu diễn một đơn có hai dòng hàng. Kế tiếp, tạo phiên bản mới thay vì sửa trực tiếp bản cũ và xác định **schema** nào cần áp dụng khi nhận dữ liệu ngoài.

Một chương sau sẽ lần theo luồng đầy đủ: nhận yêu cầu, kiểm tra, tính tổng, tạo kết quả mới, rồi điều phối việc lưu hoặc thanh toán. Cuối cùng đối chiếu mô hình dữ liệu phổ dụng của Sharvit với kiểu dữ liệu minh bạch/biến thể hợp lệ trong Java, và quyết định khi nào thiết kế hướng dữ liệu mang lại giá trị hơn trách nhiệm đặt trong đối tượng.

</details>

- [Quay lại đầu trang](#back-to-top)
