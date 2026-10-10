<a id="back-to-top"></a>

# Hàm thuần và khả năng thay thế biểu thức bằng giá trị

## Menu
- [Hàm thuần: Khái niệm và hành vi quan sát được](#purity-concept)
- [Kết quả dự đoán được và chi phí của phụ thuộc ẩn](#purity-motivation)
- [Đầu vào tường minh, kết quả và trạng thái bên ngoài](#purity-input-output)
- [Tính thay thế biểu thức bằng giá trị](#referential-transparency)
- [Vi phạm tính thuần và phép tính dễ kiểm thử](#purity-boundaries)

## <a id="purity-concept">Hàm thuần: Khái niệm và hành vi quan sát được</a>

<details>
<summary>Xem chi tiết</summary>

Một **hàm thuần** có hai đặc điểm: với cùng đầu vào nó cho cùng kết quả, và việc đánh giá nó không tạo **tác động phụ quan sát được** bên ngoài phép tính. Ví dụ `giamGia(100, 0.1)` trả 90 mỗi lần; nó không sửa danh sách hàng trong đơn hay tự ghi nhật ký. Tính thuần mô tả hành vi chứ không nằm ở tên hàm hoặc từ khóa ngôn ngữ.

```text
giamGia(gia, tyLe) = gia × (1 - tyLe)
giamGia(100, 0.1) = 90
```

Việc cấp phát một kết quả trung gian riêng không nhất thiết phá vỡ tính thuần nếu không có khác biệt quan sát được. Ngược lại, hàm đọc đồng hồ có thể không sửa biến nào nhưng vẫn cho kết quả khác ở hai thời điểm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="purity-motivation">Kết quả dự đoán được và chi phí của phụ thuộc ẩn</a>

<details>
<summary>Xem chi tiết</summary>

Một hàm âm thầm đọc tỷ giá từ dịch vụ hoặc chương trình có thể không cho kết quả giống nhau cho cùng số tiền. Đầu vào được ghi ở chữ ký hàm không phản ánh **tất cả phụ thuộc thực**; kiểm thử muốn tái hiện lỗi còn phải dựng đúng giờ, cấu hình và trạng thái máy chủ.

Đưa tỷ giá hoặc thời điểm định giá vào tham số giúp phép chuyển đổi trở nên có thể lặp lại. Lợi ích nổi bật là lý giải kết quả từ ít dữ kiện hơn và dễ kiểm thử độc lập; điều đó không khẳng định mọi tính toán thuần đều nhanh hay không cần bộ nhớ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="purity-input-output">Đầu vào tường minh, kết quả và trạng thái bên ngoài</a>

<details>
<summary>Xem chi tiết</summary>

Hãy xem `tinhTong(donHang, quyTac)`: giá từng món, lượng mua, mức giảm và quy tắc làm tròn là **đầu vào thật** nếu chúng ảnh hưởng tổng. Giá trị trả về là một bản kết quả mới, không phải việc sửa ngầm trường `tongTien` của đối tượng mà bên gọi đang giữ.

Truyền một đối tượng qua tham số **chưa đủ bảo đảm tính thuần**: nếu hàm sửa danh sách món bên trong, người gọi vẫn quan sát được thay đổi. Hãy đọc cấu hình hay gọi dịch vụ ở ranh giới ngoài rồi truyền ảnh chụp dữ liệu vào phép tính. Điều này làm quan hệ giữa dữ kiện và kết quả có thể kiểm tra được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="referential-transparency">Tính thay thế biểu thức bằng giá trị</a>

<details>
<summary>Xem chi tiết</summary>

**Tính minh bạch tham chiếu** (referential transparency) nghĩa là có thể thay một biểu thức bằng giá trị tính được của nó mà không làm thay đổi hành vi chương trình quan sát được. Nếu `gapDoi(4) = 8`, ta có thể suy luận `gapDoi(4) + gapDoi(4) = 8 + 8 = 16` mà không cần chạy lại hai lời gọi để hiểu kết quả.

Một hàm đọc thời gian hiện tại không cho phép thay thế như vậy, ngay cả khi nó không ghi dữ liệu ra ngoài. Khi áp dụng suy luận này cho số thực, vẫn phải tôn trọng độ chính xác và thứ tự làm tròn; tính thuần không thay thế các quy tắc toán học và số học máy tính.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="purity-boundaries">Vi phạm tính thuần và phép tính dễ kiểm thử</a>

<details>
<summary>Xem chi tiết</summary>

Hàm cập nhật tồn kho chung, gửi thư điện tử hoặc tự đọc tỷ giá trực tuyến là hàm có tác động phụ, dù nó trả một con số giống lần trước. Cách thiết kế rõ ràng là **đọc tỷ giá ở ngoài**, truyền tỷ giá vào hàm chuyển đổi thuần và sau đó thực hiện thao tác lưu hoặc gửi nếu cần.

Kiểm thử phép chuyển đổi chỉ cần đầu vào cố định; kiểm thử dịch vụ tỷ giá cần thử timeout, mất kết nối và phản hồi sai. Phân chia này không biến toàn bộ hệ thống thành thuần, nhưng chỉ ra cụ thể đoạn nào có thể chứng minh bằng ví dụ và đoạn nào cần bằng chứng tích hợp.

</details>

- [Quay lại đầu trang](#back-to-top)
