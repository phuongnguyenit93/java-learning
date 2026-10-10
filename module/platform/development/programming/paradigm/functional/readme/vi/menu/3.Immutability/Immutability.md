<a id="back-to-top"></a>

# Giá trị bất biến và trạng thái thay đổi

## Menu
- [Giá trị bất biến và tham chiếu có thể thay đổi](#immutable-values)
- [Giá trị, danh tính và trạng thái theo thời gian](#value-vs-state)
- [Chuyển trạng thái bằng cách tạo giá trị mới](#immutable-updates)
- [Chia sẻ cấu trúc dữ liệu và chi phí cấp phát](#sharing-and-cost)
- [Bất biến, xử lý đồng thời và các giới hạn thực tế](#immutability-limits)

## <a id="immutable-values">Giá trị bất biến và tham chiếu có thể thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

**Giá trị bất biến** không bị sửa sau khi tạo; một biến hoặc tham chiếu có thể được gán sang **giá trị khác** mà không sửa bản cũ. Ví dụ thêm món C tạo đơn mới `[A,B,C]`, còn ảnh chụp đơn cũ vẫn là `[A,B]`. Người dùng giữ bản cũ không bị thay đổi bất ngờ khi người khác xử lý đơn mới.

```text
đơn cũ: [A, B]
thêm C → đơn mới: [A, B, C]
```

Cần kiểm tra cả cấu trúc lồng nhau: một vỏ đối tượng không đổi nhưng chứa danh sách có thể sửa thì chưa phải **bất biến sâu**. Chỉ chia sẻ dữ liệu giữa các thành phần khi biết phần nào thật sự không bị sửa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="value-vs-state">Giá trị, danh tính và trạng thái theo thời gian</a>

<details>
<summary>Xem chi tiết</summary>

**Giá trị** là một ảnh chụp thông tin, **danh tính** gắn các ảnh chụp vào cùng thực thể và **trạng thái** là giá trị hiện được chọn cho thực thể đó. Đơn hàng A có thể lần lượt ở trạng thái chờ và đã thanh toán mà không cần viết đè bản dữ liệu chờ ban đầu.

Vì vậy “dùng giá trị bất biến” không đồng nghĩa “ứng dụng không thay đổi trạng thái”. Một thành phần điều phối vẫn phải chọn phiên bản nào là hiện hành, lưu chuyển đổi và quản lý xung đột. Đó là lý do chương sau phân biệt phép biến đổi dữ liệu với việc thực sự cập nhật hệ thống.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="immutable-updates">Chuyển trạng thái bằng cách tạo giá trị mới</a>

<details>
<summary>Xem chi tiết</summary>

Ta có thể diễn đạt một chuyển đổi bằng **`trangThaiMoi = chuyen(trangThaiCu, suKien)`**. Nhận sự kiện `ThanhToanDuocXacNhan` cho đơn đang chờ thì tạo bản đơn đã thanh toán; nhận kết quả từ chối thì trả một kết quả thất bại có thông tin. Phép tính không cần sửa ảnh chụp trước.

Lưu ý **sự kiện phải là dữ kiện đã biết**, không phải dự đoán. Gọi cổng thanh toán để nhận xác nhận là thao tác ngoài thực tế, không thể bị “giả vờ thuần”. Kết quả của phép chuyển đổi và hành động ghi phiên bản mới phải được phối hợp với chính sách nhất quán của hệ thống.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sharing-and-cost">Chia sẻ cấu trúc dữ liệu và chi phí cấp phát</a>

<details>
<summary>Xem chi tiết</summary>

Nếu mỗi lần sửa một nhánh của cây dữ liệu lớn đều sao chép toàn bộ cây, thời gian và bộ nhớ có thể tăng mạnh. Một **cấu trúc dữ liệu bất biến lưu phiên bản** (persistent data structure) có thể tạo gốc mới, dùng lại các nhánh không đổi một cách an toàn và chỉ tạo mới đường đi tới phần cần sửa.

Các phiên bản cũ vẫn đọc được, hữu ích cho so sánh lịch sử và kiểm thử. Tuy nhiên **không phải tập hợp bất biến nào cũng tự chia sẻ cấu trúc**; kỹ thuật cụ thể phụ thuộc thư viện. Nếu hiệu năng quan trọng, hãy đo số cấp phát, độ trễ truy cập và thu gom rác thay vì mặc định bất biến luôn rẻ hoặc luôn đắt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="immutability-limits">Bất biến, xử lý đồng thời và các giới hạn thực tế</a>

<details>
<summary>Xem chi tiết</summary>

Dữ liệu bất biến giúp tránh việc sửa ngầm một ảnh chụp, nhưng **không tự giải quyết cập nhật cạnh tranh**. Hai yêu cầu cùng đọc đơn phiên bản 3 và cùng tạo ra hai kết quả phiên bản 4 khác nhau; không thể chỉ vì mỗi kết quả bất biến mà cả hai đều được lưu hợp lệ.

Thành phần điều phối vẫn phải dùng kiểm tra phiên bản, cơ chế khóa hoặc một chính sách nhất quán phù hợp. Tương tự, bản dữ liệu bất biến không bảo đảm thao tác trừ tiền là idempotent hay giao dịch bên ngoài có tính nguyên tử. Đây là giới hạn quan trọng khi đem tư duy hàm vào dịch vụ thật.

</details>

- [Quay lại đầu trang](#back-to-top)
