<a id="back-to-top"></a>

# Hình dạng dữ liệu, schema và ranh giới kiểm tra

## Menu
- [Hình dạng dữ liệu và phần mô tả schema độc lập](#dop-schema-concept)
- [Dữ liệu linh hoạt và kiểm tra schema theo nhu cầu](#dop-schema-optional)
- [Kiểm tra tại ranh giới đầu vào, đầu ra và độ tin cậy](#dop-validation-boundaries)
- [Dữ liệu không hợp lệ, thông tin lỗi và lựa chọn xử lý](#dop-validation-failure)
- [Thay đổi schema và chi phí của biểu diễn linh hoạt](#dop-schema-tradeoffs)

## <a id="dop-schema-concept">Hình dạng dữ liệu và phần mô tả schema độc lập</a>

<details>
<summary>Xem chi tiết</summary>

**Hình dạng dữ liệu** là cách các trường và phần tử lồng nhau được sắp xếp, ví dụ đơn hàng có `id`, `lines` và từng dòng có `qty`, `price`. **Schema** là phần mô tả độc lập quy định trường nào bắt buộc, phải mang kiểu gì và thỏa điều kiện nào. Map mang dữ liệu của một đơn cụ thể; schema mô tả **những map nào được chấp nhận**.

```text
dữ liệu: {id:"A", lines:[{qty:2, price:30}]}
schema: id bắt buộc; lines là danh sách;
        qty nguyên dương; price không âm
```

Tách schema khỏi biểu diễn là nguyên tắc thứ tư trong hướng Sharvit. Nhiều thao tác có thể dùng chung hình dạng và chọn kiểm tra phù hợp, thay vì mang một bộ kiểm tra riêng trong mỗi giá trị.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-schema-optional">Dữ liệu linh hoạt và kiểm tra schema theo nhu cầu</a>

<details>
<summary>Xem chi tiết</summary>

Schema độc lập **không có nghĩa mọi giá trị tạm đều phải được kiểm tra bằng cùng một schema cứng**. Một bản tóm tắt chỉ gồm `id` và `total` có thể phục vụ báo cáo mà không cần mang đầy đủ các trường của đơn hàng gốc. Ngược lại, đầu vào thanh toán từ bên ngoài cần kiểm tra nghiêm ngặt trước khi tính tiền.

Mức kiểm tra nên dựa vào **độ tin cậy và hậu quả lỗi**. Một map tạm sinh ra trong hàm thuần đã được kiểm soát có rủi ro khác với JSON do người dùng gửi. Chọn schema ở nơi nó bảo vệ một hợp đồng thực, không chỉ để tăng số dòng cấu hình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-validation-boundaries">Kiểm tra tại ranh giới đầu vào, đầu ra và độ tin cậy</a>

<details>
<summary>Xem chi tiết</summary>

**Ranh giới tin cậy** là nơi dữ liệu chuyển từ nguồn chưa được xác minh sang phần hệ thống được phép dựa vào dữ kiện đó. Khi nhận đơn hàng, hãy kiểm tra mã, danh sách dòng, mã sản phẩm, số lượng nguyên dương và biểu diễn đơn giá đúng định dạng trước khi chạy `tinhTong`.

Đầu ra gửi cho dịch vụ khác cũng phải khớp hợp đồng của nơi nhận; không chỉ kiểm tra lúc nhập. Nếu dữ liệu nội bộ có thể bị tạo ra sai bởi một thao tác khác, kiểm tra tại điểm tạo hoặc trước lúc xuất vẫn cần thiết. **Validation** là trách nhiệm thiết kế, không đồng nghĩa phải dùng một thư viện JSON hay ORM cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-validation-failure">Dữ liệu không hợp lệ, thông tin lỗi và lựa chọn xử lý</a>

<details>
<summary>Xem chi tiết</summary>

Xét đơn có `lines[1].qty = -3`. Kết quả kiểm tra hữu ích phải mô tả **đường dẫn trường, quy tắc bị vi phạm và giá trị đã nhận**. Không được âm thầm đổi -3 thành 0, vì việc này vừa che dữ liệu sai vừa làm thay đổi ý nghĩa tiền hàng.

```text
path: lines[1].qty
expected: số nguyên dương
actual: -3
decision: từ chối hoặc yêu cầu sửa
```

Bên gọi có thể trả lỗi cho người dùng hoặc áp dụng chính sách chuẩn hóa đã được phê duyệt. Lỗi schema phải ngăn phép tính tiếp tục với dữ liệu không hợp lệ, chứ không bị gom thành ngoại lệ chung không có vị trí để chẩn đoán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-schema-tradeoffs">Thay đổi schema và chi phí của biểu diễn linh hoạt</a>

<details>
<summary>Xem chi tiết</summary>

Cấu trúc phổ dụng giúp thay đổi nhanh, nhưng **thay đổi schema** cũng là thay đổi hợp đồng. Đổi khóa `qty` thành `quantity` làm những hàm vẫn đọc khóa cũ không thấy dữ liệu; thêm trường bắt buộc có thể khiến các bản ghi cũ không còn hợp lệ.

Hãy cân nhắc khả năng tương thích, thời gian chuyển phiên bản và giá trị mặc định chỉ khi **đúng nghĩa nghiệp vụ**. Mô hình có kiểu tĩnh phát hiện một số lỗi sớm hơn; schema kiểm tra lúc chạy linh hoạt với đầu vào bên ngoài. Cả hai vẫn cần kế hoạch khi hợp đồng dữ liệu thay đổi.

### Tài liệu tham khảo

- [Separate data schema from data representation — Yehonathan Sharvit](https://blog.klipse.tech/databook/2022/06/22/data-validation.html): tách quy tắc về cấu trúc khỏi từng bản dữ liệu và lựa chọn ranh giới kiểm tra.

</details>

- [Quay lại đầu trang](#back-to-top)
