<a id="back-to-top"></a>

# Luồng biến đổi dữ liệu từ đầu vào đến đầu ra

## Menu
- [Tình huống ứng dụng nhỏ và các ranh giới dữ liệu](#dop-flow-scenario)
- [Dữ liệu đầu vào, kiểm tra cấu trúc và giá trị bị từ chối](#dop-flow-validate)
- [Phép xử lý độc lập và biến đổi dữ liệu bất biến](#dop-flow-transform)
- [Điều phối trạng thái mới và tác động ra bên ngoài](#dop-flow-coordinate)
- [Theo dõi phiên bản dữ liệu, lỗi và kiểm thử](#dop-flow-observe)

## <a id="dop-flow-scenario">Tình huống ứng dụng nhỏ và các ranh giới dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

Hãy theo dõi yêu cầu mua hàng của đơn A có hai dòng: P số lượng 2, đơn giá 30; Q số lượng 1, đơn giá 40. Tổng trước giảm bằng **`2 × 30 + 1 × 40 = 100`**. Hệ thống nhận một map bên ngoài, kiểm tra hình dạng, tính tổng, tạo bản kết quả mới và chỉ sau đó mới điều phối việc lưu hoặc thanh toán.

```text
nhận đơn A → kiểm tra → tính tổng 100
          → tạo bản mới → ghi/lưu ở ranh giới ngoài
```

Cùng biểu diễn đơn hàng được dùng xuyên suốt, nhưng từng giai đoạn có nguyên nhân lỗi riêng. Đây là bài tập kết hợp bốn nguyên tắc Sharvit, không phải yêu cầu viết một transaction manager.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-flow-validate">Dữ liệu đầu vào, kiểm tra cấu trúc và giá trị bị từ chối</a>

<details>
<summary>Xem chi tiết</summary>

Trước phép tính, hãy kiểm tra `id` có giá trị, `lines` là danh sách, mỗi `qty` là số nguyên dương, `price` không âm và `status` thuộc tập được phép. Nếu dòng thứ hai thiếu giá, phải từ chối với vị trí rõ như `lines[1].price`; **không được tự coi giá thiếu là 0** để tạo tổng có vẻ hợp lệ.

Sau khi dữ liệu vượt qua ranh giới tin cậy, các hàm tính toán có thể dựa vào hình dạng đã cam kết. Kiểm tra đúng chỗ làm lỗi được phát hiện sớm, giảm lặp các nhánh tự vệ không liên quan bên trong từng phép tính.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-flow-transform">Phép xử lý độc lập và biến đổi dữ liệu bất biến</a>

<details>
<summary>Xem chi tiết</summary>

Đầu vào hợp lệ được đưa vào `tinhTong(don)` trả 100; `giamGia(don, 0.10)` tạo **bản đơn mới** có tổng sau giảm là 90. Bản trước vẫn giữ nguyên để người kiểm thử so sánh, trong khi từng hàm nêu rõ trường nào nó đọc và kết quả nào nó tạo.

```text
đơn v1: tổng 100 → giảm 10% → đơn v2: tổng 90
đơn v1 vẫn giữ tổng 100
```

Không nên giấu lệnh thanh toán hoặc sửa danh sách gốc bên trong phép biến đổi này. Nếu một bước thay đổi dữ liệu ngoài, nó cần được nhận diện là tác động phụ và xử lý trong bộ điều phối riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-flow-coordinate">Điều phối trạng thái mới và tác động ra bên ngoài</a>

<details>
<summary>Xem chi tiết</summary>

**Tạo bản đơn v2 chưa có nghĩa đã hoàn tất giao dịch**. Bộ điều phối phải xác nhận phiên bản đang lưu, gọi dịch vụ có liên quan, đọc kết quả và quyết định khi nào bản mới được công nhận. Nếu yêu cầu khác đã ghi phiên bản mới trước, việc ghi có thể bị từ chối hoặc phải tính lại từ trạng thái hiện hành.

Khi thanh toán thành công nhưng lưu trạng thái thất bại, hệ thống cần một **chính sách lỗi một phần**: truy vấn kết quả giao dịch, retry có kiểm soát, xử lý bù trừ hoặc chuyển sang luồng xác minh thủ công. DOP chỉ làm dữ liệu dự kiến dễ quan sát; nó không tự cung cấp atomicity hay idempotency cho I/O.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-flow-observe">Theo dõi phiên bản dữ liệu, lỗi và kiểm thử</a>

<details>
<summary>Xem chi tiết</summary>

Một trace hữu ích cho đơn A cần ghi **phiên bản đầu vào**, kết quả kiểm tra, số tiền tính được, phiên bản dự kiến và ranh giới ngoài nào thành công hay thất bại. Như vậy, khi hóa đơn sai, ta biết lỗi xuất hiện ở hình dạng dữ liệu, công thức, tranh chấp phiên bản hay dịch vụ thanh toán.

Kiểm thử đơn vị so sánh map trước/sau; kiểm thử ranh giới thử khóa thiếu hoặc số lượng âm; kiểm thử tích hợp thử lưu thất bại và yêu cầu trùng. Không được ghi log toàn bộ map nếu chứa dữ liệu cá nhân hoặc phương thức thanh toán: hãy chỉ ghi mã tương quan và thông tin đã che nhạy cảm.

</details>

- [Quay lại đầu trang](#back-to-top)
