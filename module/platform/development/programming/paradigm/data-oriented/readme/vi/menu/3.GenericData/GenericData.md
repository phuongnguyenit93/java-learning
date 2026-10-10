<a id="back-to-top"></a>

# Biểu diễn dữ liệu bằng cấu trúc phổ dụng

## Menu
- [Cấu trúc dữ liệu phổ dụng: Map, danh sách và giá trị](#generic-concept)
- [Biểu diễn bài toán đơn giản bằng dữ liệu lồng nhau](#generic-domain-example)
- [Phép xử lý dùng lại trên cấu trúc dữ liệu dễ quan sát](#generic-operations)
- [Khóa bị thiếu, cấu trúc không hợp lệ và giá trị mơ hồ](#generic-failure-modes)
- [Tính linh hoạt, ràng buộc kiểu tĩnh và nhu cầu mô tả cấu trúc](#generic-contracts)

## <a id="generic-concept">Cấu trúc dữ liệu phổ dụng: Map, danh sách và giá trị</a>

<details>
<summary>Xem chi tiết</summary>

**Cấu trúc dữ liệu phổ dụng** lưu dữ liệu bằng những hình thức chung: map ghép khóa với giá trị, danh sách giữ thứ tự phần tử, còn mỗi trường có thể chứa số, chuỗi hoặc một cấu trúc lồng nhau. Ta không phải tạo một lớp riêng mang hành vi cho mỗi hình dạng tạm thời.

Chẳng hạn map đơn hàng có khóa `id` và `lines`; mỗi dòng trong `lines` là map gồm `sku`, `qty` và `price`. “Phổ dụng” không có nghĩa được đổi kiểu, đổi đơn vị hoặc bỏ khóa tùy tiện. **Ý nghĩa của cấu trúc** vẫn là hợp đồng giữa bên tạo và bên sử dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-domain-example">Biểu diễn bài toán đơn giản bằng dữ liệu lồng nhau</a>

<details>
<summary>Xem chi tiết</summary>

Biểu diễn đơn A có hai dòng: sản phẩm P mua 2 chiếc giá 30, sản phẩm Q mua 1 chiếc giá 40. Tổng trước giảm bằng `2 × 30 + 1 × 40 = 100`. Cùng bản map này có thể được báo cáo đọc mã đơn, phép tính đọc danh sách dòng và bộ kiểm tra đọc số lượng.

```json
{ "id":"A",
  "lines":[{"sku":"P","qty":2,"price":30},
           {"sku":"Q","qty":1,"price":40}],
  "status":"pending" }
```

Một con số `30` chỉ có ý nghĩa khi xác định nó là đơn giá theo đồng tiền nào. Cần thống nhất **đơn vị, khóa và trạng thái hợp lệ**, chứ không chỉ làm cho JSON nhìn gọn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-operations">Phép xử lý dùng lại trên cấu trúc dữ liệu dễ quan sát</a>

<details>
<summary>Xem chi tiết</summary>

Hàm `tinhTong(don)` có thể duyệt `lines` và cộng `qty × price`; hàm `taoTomTat(don)` chỉ lấy mã và số dòng. Hai hàm hoạt động trên cùng bản dữ liệu tường minh mà **không phải thêm phương thức vào chính đơn hàng**.

Một phép tính an toàn phải nêu rõ những trường bắt buộc. Nếu dòng thứ hai thiếu `price`, không được âm thầm dùng 0 vì kết quả tổng vẫn có vẻ hợp lệ nhưng sai. Dữ liệu phổ dụng hỗ trợ tái sử dụng; nó **không thay thế kiểm tra cấu trúc và hợp đồng kiểu**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-failure-modes">Khóa bị thiếu, cấu trúc không hợp lệ và giá trị mơ hồ</a>

<details>
<summary>Xem chi tiết</summary>

Trong map linh hoạt, gõ nhầm `quantity` thành `quantitty` có thể trả giá trị thiếu, không nhất thiết bị phát hiện khi biên dịch. Chuỗi `"2"` cũng không tự đồng nghĩa số nguyên 2; chuyển kiểu ngầm dễ tạo kết quả bất ngờ hoặc lỗi ở bước sau.

Trước khi tính tiền, cần kiểm tra khóa bắt buộc, loại giá trị, số lượng nguyên dương, đơn giá không âm và phần tử lồng nhau có dạng mong đợi. Khi từ chối, thông báo nên chỉ ra vị trí như `lines[1].qty` thay vì gán mặc định 0 cho mọi trường thiếu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-contracts">Tính linh hoạt, ràng buộc kiểu tĩnh và nhu cầu mô tả cấu trúc</a>

<details>
<summary>Xem chi tiết</summary>

Map và danh sách linh hoạt có thể giảm lượng kiểu phải khai báo, nhưng trong nhiều ngôn ngữ không đem lại bảo đảm kiểu tĩnh mạnh. Ta có thể tăng độ tin cậy bằng hợp đồng cấu trúc, ca kiểm thử, lớp dữ liệu có kiểu ở ranh giới hoặc **schema** tách riêng.

Với yêu cầu tạo đơn, hãy quy định `lines` là danh sách, `qty` là số nguyên dương và `price` là giá trị tiền không âm. Không nhất thiết định nghĩa schema lớn cho mỗi map trung gian chỉ tồn tại trong một hàm; ưu tiên dữ liệu dùng chung và đầu vào đến từ nơi chưa tin cậy.

</details>

- [Quay lại đầu trang](#back-to-top)
