<a id="back-to-top"></a>

# Lập trình mệnh lệnh: Cách diễn đạt phép tính bằng thao tác

## Menu
- [Lập trình mệnh lệnh: Khái niệm và phạm vi](#imperative-what)
- [Vai trò của việc kiểm soát từng bước thực thi](#imperative-why)
- [Bài toán cần thứ tự thao tác tường minh](#imperative-before)
- [Mô hình lệnh, trạng thái và kết quả](#imperative-solution)
- [Quan hệ với lập trình khai báo, hướng đối tượng và hàm](#imperative-boundary)
- [Điểm xuất phát và hành trình học: Từ thao tác đến tổ chức thủ tục](#imperative-learning-path)

## <a id="imperative-what">Lập trình mệnh lệnh: Khái niệm và phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

Lập trình mệnh lệnh mô tả chương trình bằng **các thao tác được thực hiện theo thứ tự xác định**. Một thao tác có thể thay đổi trạng thái, nhưng cũng có thể tính toán hoặc thực hiện công việc mà không sửa dữ liệu.

```text
state hiện tại
→ command
→ state mới
→ command tiếp theo
```

Sơ đồ này minh họa trường hợp trạng thái thay đổi; một lệnh cũng có thể giữ nguyên trạng thái hiện tại.

Lập trình mệnh lệnh diễn đạt lời giải bằng **các thao tác có thứ tự**. Một thao tác có thể tính một giá trị, kiểm tra điều kiện, in kết quả hoặc cập nhật trạng thái; không phải lệnh nào cũng làm dữ liệu thay đổi. Người viết quan tâm chương trình đi qua những bước nào trước khi đạt kết quả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-why">Vai trò của việc kiểm soát từng bước thực thi</a>

<details>
<summary>Xem chi tiết</summary>

Máy tính thực hiện các chỉ thị theo trình tự xác định trong một luồng xử lý, đồng thời có thể cập nhật dữ liệu đang lưu. Lập trình mệnh lệnh thể hiện trực tiếp các bước của thuật toán nên người học có thể theo dõi từng thao tác và trạng thái liên quan.

Khi tính tiền tại quầy, phải kiểm tra số dư trước khi trừ tiền; đảo hai bước có thể khiến hệ thống thông báo sai hoặc tạm xuất hiện số dư âm. Việc điều khiển thứ tự rõ ràng giúp mô tả chính xác hành vi phải diễn ra, nhưng cũng buộc người lập trình chịu trách nhiệm về thứ tự đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-before">Bài toán cần thứ tự thao tác tường minh</a>

<details>
<summary>Xem chi tiết</summary>

Khi bài toán cần kiểm soát thứ tự thao tác, rẽ nhánh, lặp hoặc cập nhật trạng thái, lối mệnh lệnh cho phép viết rõ từng bước hệ thống phải thực hiện.

Xét số dư ban đầu 100, yêu cầu rút 30 và ghi lại số dư. Kết quả 70 chưa nói đủ rằng phải kiểm tra số tiền hợp lệ, xác nhận đủ tiền, cập nhật rồi mới ghi nhật ký; đó là phần **trình tự hành động**. Với một phép cộng thuần túy, việc liệt kê từng lệnh lại có thể dài dòng hơn mô tả kết quả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-solution">Mô hình lệnh, trạng thái và kết quả</a>

<details>
<summary>Xem chi tiết</summary>

```text
do A
then B
if condition → do C
repeat D
update state
```

Những khái niệm cốt lõi là phép gán, trạng thái có thể thay đổi, điều khiển luồng, thủ tục và thứ tự thực hiện tường minh.

Để lần theo chương trình, hãy giữ ba thành phần riêng: **lệnh đang thực hiện**, **trạng thái trước lệnh** và **kết quả hoặc trạng thái sau lệnh**. Chẳng hạn `balance = 100` rồi `balance = balance - 30` tạo trạng thái 100 → 70; phép tính `100 - 30` đơn lẻ chỉ tạo giá trị 70, không tự sửa biến nào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-boundary">Quan hệ với lập trình khai báo, hướng đối tượng và hàm</a>

<details>
<summary>Xem chi tiết</summary>

Lối mệnh lệnh không đối lập tuyệt đối với lập trình hướng đối tượng hay lập trình hàm. Phương thức của một đối tượng vẫn có thể thực hiện các lệnh tuần tự; hàm cũng có thể dùng biến cục bộ và vòng lặp bên trong.

Đây là một góc nhìn về **cách diễn đạt quá trình tính toán**, không phải cách phân chia các phong cách lập trình thành những nhóm loại trừ lẫn nhau.

Một bài toán có thể phối hợp nhiều cách tư duy: bước kiểm tra và cập nhật là mệnh lệnh; quy tắc “chỉ rút khi đủ tiền” có thể được mô tả theo lối khai báo; một đối tượng tài khoản có thể sở hữu trách nhiệm cập nhật. Các paradigm không loại trừ nhau, và cú pháp cụ thể thuộc module ngôn ngữ Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-learning-path">Điểm xuất phát và hành trình học: Từ thao tác đến tổ chức thủ tục</a>

<details>
<summary>Xem chi tiết</summary>

Bắt đầu với biến, giá trị, phép tính và điều kiện đơn giản. Sau chương này, học cách quan sát trạng thái trước/sau từng lệnh; tiếp tục với nhánh và vòng lặp; sau đó gom thao tác thành thủ tục và kiểm chứng tính đúng đắn. Không cần biết cơ chế bộ nhớ Java hay hệ thống đồng thời để hiểu các ví dụ.

</details>

- [Quay lại đầu trang](#back-to-top)
