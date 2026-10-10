<a id="back-to-top"></a>

# Dữ liệu bất biến và trạng thái ứng dụng

## Menu
- [Giá trị bất biến trong mô hình hướng dữ liệu](#dop-immutable-principle)
- [Cập nhật dữ liệu bằng cách tạo phiên bản mới](#dop-state-update)
- [Bản dữ liệu trước sau, so sánh và lịch sử thay đổi](#dop-state-history)
- [Điều phối trạng thái và ranh giới xử lý đồng thời](#dop-state-coordination)

## <a id="dop-immutable-principle">Giá trị bất biến trong mô hình hướng dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

Nguyên tắc thứ ba của Sharvit ưu tiên **giá trị dữ liệu bất biến**. Hàm giảm giá nhận đơn A và trả bản B đã giảm, trong khi A vẫn dùng được để đối chiếu và ghi nhận lịch sử. Gán một biến sang B không phải là sửa nội dung của A.

```text
A = {status:pending, total:100}
B = giamGia(A,10%) → {status:pending, total:90}
A vẫn giữ total:100
```

Điều này phải áp dụng cả dữ liệu lồng nhau: sửa trực tiếp một map dòng hàng cùng được tham chiếu bởi A và B sẽ phá tính bất biến. **Bản sao nông** chưa chắc là đủ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-state-update">Cập nhật dữ liệu bằng cách tạo phiên bản mới</a>

<details>
<summary>Xem chi tiết</summary>

Thay vì `don.lines[0].qty = 3`, ta có thể dùng `capNhatSoLuong(don,"P",3)` để tạo dòng hàng mới, tạo danh sách mới và trả **bản đơn mới**. Bản cũ giữ số lượng 2 giúp kiểm thử so sánh trước–sau mà không cần biết ai từng sửa dữ liệu.

```text
đơn v1: P × 2 → hàm cập nhật → đơn v2: P × 3
đơn v1 vẫn có P × 2
```

Hàm tạo kết quả không quyết định bản v2 đã trở thành trạng thái chính thức. Ứng dụng còn phải chọn lúc lưu và cách xử lý trường hợp ghi thất bại.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-state-history">Bản dữ liệu trước sau, so sánh và lịch sử thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

Các ảnh chụp bất biến làm bằng chứng lịch sử rõ hơn: đơn phiên bản 1 có hai mặt hàng, phiên bản 2 thêm món C. Ta có thể so sánh hai bản mà không sợ một lần sửa sau này làm dữ liệu của bản cũ thay đổi ngầm.

Nhưng **không bắt buộc lưu mọi phiên bản mãi mãi**. Thời hạn lưu trữ, chi phí bộ nhớ và kỹ thuật chia sẻ cấu trúc dữ liệu là quyết định riêng. Mã đơn hàng gắn với cùng danh tính nghiệp vụ, còn từng bản dữ liệu là một trạng thái ở thời điểm xác định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-state-coordination">Điều phối trạng thái và ranh giới xử lý đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

Bất biến không phải cơ chế khóa. Hai yêu cầu cùng đọc phiên bản 4, mỗi bên tính ra **hai phiên bản 5 khác nhau** rồi cùng muốn lưu. Dù mỗi kết quả riêng lẻ không bị sửa, việc chọn bản chính thức vẫn có thể tranh chấp.

Bộ lưu cần kiểm tra phiên bản, khóa, giao dịch hoặc chính sách nhất quán phù hợp. Tương tự, map có trạng thái `paid` chưa chứng minh ngân hàng đã thu tiền. Phải tách **tạo dữ liệu dự kiến** khỏi **điều phối cập nhật trạng thái thật** và xử lý tác động bên ngoài.

</details>

- [Quay lại đầu trang](#back-to-top)
