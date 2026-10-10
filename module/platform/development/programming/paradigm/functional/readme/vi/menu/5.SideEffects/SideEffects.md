<a id="back-to-top"></a>

# Tác động phụ và ranh giới chương trình

## Menu
- [Tác động phụ và thay đổi bên ngoài có thể quan sát](#effect-concept)
- [Phép tính thuần và thao tác có tác động phụ](#pure-core-effects)
- [Thứ tự tác động phụ và điều phối trạng thái](#effect-coordination)
- [Xử lý lỗi và kiểm thử tại ranh giới tác động phụ](#effect-testing)
- [Logic theo lối hàm cùng I/O và dịch vụ bên ngoài](#effects-real-world)

## <a id="effect-concept">Tác động phụ và thay đổi bên ngoài có thể quan sát</a>

<details>
<summary>Xem chi tiết</summary>

**Tác động phụ** là hành vi có thể quan sát được ngoài giá trị trả về: ghi file, gửi thư, cập nhật tồn kho hay đọc đồng hồ thay đổi. Hàm có thể trả đúng số tiền nhưng không thuần nếu nó tự sửa một đối tượng mà bên gọi đang giữ.

Trả về một **giá trị mô tả lỗi** có thể vẫn là phép tính thuần; thao tác I/O hoặc thay đổi hệ thống thật thì cần phân tích riêng. Lập trình hàm không cấm tác động phụ mà khuyến khích chỉ rõ chúng nằm ở đâu và xảy ra theo thứ tự nào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pure-core-effects">Phép tính thuần và thao tác có tác động phụ</a>

<details>
<summary>Xem chi tiết</summary>

Thiết kế **lõi tính toán thuần, lớp vỏ tương tác** tách quy tắc tính giá khỏi đọc mạng và lưu dữ liệu. Bộ điều phối đọc đơn và chính sách, truyền dữ liệu cố định vào phép tính, rồi quyết định gọi cổng thanh toán và ghi trạng thái từ kết quả đã biết.

```text
đọc đơn/chính sách [I/O] → tính tổng [thuần] → thu tiền/lưu [I/O]
```

Kiểm thử phần tính toán không cần mạng; kiểm thử bộ kết nối cần mô phỏng lỗi và chạy tích hợp thực. Sự phân chia này **không tự bảo đảm giao dịch nguyên tử**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="effect-coordination">Thứ tự tác động phụ và điều phối trạng thái</a>

<details>
<summary>Xem chi tiết</summary>

Thứ tự thực hiện tác động phụ ảnh hưởng tính đúng đắn. Đánh dấu đơn “đã trả” **trước khi** cổng thanh toán xác nhận có thể ghi sai trạng thái; gọi thu tiền lần nữa khi retry không có khóa chống trùng có thể tạo khoản thu kép.

Hàm thuần chỉ quyết định điều kiện đủ để đề xuất giao dịch. Thành phần điều phối phải gọi dịch vụ, nhận kết quả, xử lý thất bại từng phần và chọn khi nào ghi dữ liệu mới. Tách lõi tính toán không tự tạo **exactly-once**, idempotency hoặc nhất quán phân tán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="effect-testing">Xử lý lỗi và kiểm thử tại ranh giới tác động phụ</a>

<details>
<summary>Xem chi tiết</summary>

Hãy kiểm thử tính giá bằng đầu vào xác định: giỏ rỗng, số tiền âm, giảm giá hợp lệ và trường hợp cần làm tròn. Nếu phần này thuần, cùng dữ liệu luôn có cùng kết quả mà không cần đồng hồ hay cơ sở dữ liệu thật.

Thử bộ điều phối riêng với timeout, thanh toán bị từ chối, phản hồi trùng và ghi dữ liệu thất bại. Bộ kết nối giả có thể chứng minh thứ tự lời gọi dự kiến, nhưng không chứng minh mạng thật luôn ổn định. Thông tin lỗi phải đủ rõ để bên gọi chọn từ chối, retry hoặc xử lý bù trừ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="effects-real-world">Logic theo lối hàm cùng I/O và dịch vụ bên ngoài</a>

<details>
<summary>Xem chi tiết</summary>

Một ứng dụng web vẫn có thể dùng phép tính thuần ở giữa luồng: nhận yêu cầu, đọc đơn hàng, xác thực, tạo kết quả mới rồi kiểm tra phiên bản trước khi lưu. Thao tác đọc/ghi tương tác với hệ thống; phép tính ở giữa chỉ sử dụng dữ liệu được truyền vào.

```text
HTTP → đọc/kiểm tra → biến đổi thuần → kiểm phiên bản/lưu → HTTP
```

Hai yêu cầu có thể cùng đọc một phiên bản và tạo hai kết quả hợp lệ riêng. **Tính bất biến trong bộ nhớ** không giải quyết tranh chấp cập nhật tại cơ sở dữ liệu, nên bộ lưu vẫn cần kiểm tra xung đột.

</details>

- [Quay lại đầu trang](#back-to-top)
