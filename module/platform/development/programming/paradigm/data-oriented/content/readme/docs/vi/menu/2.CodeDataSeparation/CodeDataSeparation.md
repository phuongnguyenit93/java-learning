<a id="back-to-top"></a>

# Tách hành vi xử lý khỏi dữ liệu

## Menu
- [Giá trị dữ liệu và hàm xử lý có trách nhiệm riêng](#code-data-concept)
- [Đối tượng giữ hành vi và dữ liệu có thể tái sử dụng](#behavior-coupling)
- [Quan sát và chia sẻ dữ liệu giữa các phép xử lý](#data-visibility)
- [Đánh đổi về đóng gói và phụ thuộc khi tách code khỏi dữ liệu](#separation-tradeoffs)

## <a id="code-data-concept">Giá trị dữ liệu và hàm xử lý có trách nhiệm riêng</a>

<details>
<summary>Xem chi tiết</summary>

Một giá trị đơn hàng có thể chỉ chứa `id`, `lines` và `status`, trong khi `tinhTong(don)`, `kiemTraDon(don)` và `taoBaoCao(don)` là **những hàm xử lý riêng**. Dữ liệu mô tả đơn gì; hàm quyết định phải làm gì với thông tin đó. Điều này giúp một giá trị phục vụ nhiều thao tác mà không buộc đối tượng dữ liệu sở hữu mọi phương thức.

Tách code và dữ liệu **không cấm tổ chức hàm thành module** theo nghiệp vụ. Các hàm vẫn cần trách nhiệm rõ ràng, yêu cầu đầu vào được mô tả cụ thể và quy tắc truy cập dữ liệu nhạy cảm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="behavior-coupling">Đối tượng giữ hành vi và dữ liệu có thể tái sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

Một đối tượng `Order` có thể mang `order.total()`, `order.export()` và `order.applyPolicy()`. Cách làm ấy có lợi khi đối tượng cần giữ bất biến, nhưng bộ phận báo cáo có thể phải phụ thuộc vào phương thức không thực sự thuộc nhu cầu của họ. DOP thử chuyển sang `tinhTong(don)` và `xuatBaoCao(don)` để hai thao tác phát triển độc lập.

Không nên đưa mọi quy tắc nhất quán ra khỏi đối tượng rồi **quên kiểm tra lại**: một map có trạng thái “đã trả” nhưng không có bằng chứng thanh toán vẫn sai. Hãy chọn ranh giới theo dữ liệu dùng chung, số thao tác và độ phức tạp của bất biến.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-visibility">Quan sát và chia sẻ dữ liệu giữa các phép xử lý</a>

<details>
<summary>Xem chi tiết</summary>

Khi đơn hàng là dữ liệu dễ quan sát, kiểm thử có thể so sánh trực tiếp bản trước và sau giảm giá. Báo cáo có thể chỉ đọc `id` và `total`, còn phép kiểm tra duyệt `lines`, thay vì gọi những phương thức trình bày đặc biệt của một đối tượng.

**Dễ quan sát không đồng nghĩa công khai dữ liệu nhạy cảm**. Nếu map chứa địa chỉ, số tài khoản hoặc thông tin cá nhân, chỉ chuyển những trường người nhận được phép đọc. Kiểm soát quyền truy cập và hợp đồng đầu ra vẫn là trách nhiệm thiết kế, không bị thay thế bởi tính linh hoạt của map.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="separation-tradeoffs">Đánh đổi về đóng gói và phụ thuộc khi tách code khỏi dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

Tách thao tác làm dữ liệu dễ dùng lại, nhưng các hàm có thể phụ thuộc trực tiếp vào tên khóa và cấu trúc lồng nhau. Đổi `total` thành `subtotal` mà không cập nhật các bên đọc sẽ gây lỗi. Nếu ranh giới kiểm tra kém, bất biến vốn được bảo vệ bởi đối tượng có thể bị bỏ qua.

Các cách giảm rủi ro gồm mô tả schema, kiểm tra tại đầu vào và quản lý phiên bản cấu trúc. Với bài toán nhỏ chỉ có một đối tượng sở hữu quy tắc phức tạp, giữ hành vi bên trong đối tượng đôi khi **dễ hiểu hơn** việc chia ra nhiều hàm đọc map phổ dụng.

</details>

- [Quay lại đầu trang](#back-to-top)
