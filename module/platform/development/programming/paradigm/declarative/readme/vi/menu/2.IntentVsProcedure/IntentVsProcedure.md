<a id="back-to-top"></a>

# Đặc tả ý định và chiến lược thực thi

## Menu
- [Đặc tả kết quả và các thuộc tính phải thỏa mãn](#declarative-result-spec)
- [Điều kiện, vị từ và ràng buộc trong một mô tả](#declarative-predicates)
- [Mức độ đầy đủ và tính mơ hồ của đặc tả](#declarative-spec-completeness)
- [Bộ đánh giá: Vai trò biến mô tả thành phép tính](#declarative-evaluator-role)
- [Kết quả theo ngữ nghĩa và kế hoạch thực thi cụ thể](#declarative-semantics-plan)
- [Sự khác nhau giữa chiến lược, thứ tự quan sát và hiệu năng](#declarative-execution-tradeoffs)

## <a id="declarative-result-spec">Đặc tả kết quả và các thuộc tính phải thỏa mãn</a>

<details>
<summary>Xem chi tiết</summary>

Đặc tả kết quả mô tả tập các đầu ra chấp nhận được bằng **thuộc tính phải thỏa mãn**. Với danh sách giao dịch, điều kiện “số tiền lớn hơn 0” cho biết mục nào phù hợp mà không cần nêu biến đếm hay vòng lặp.

Nếu yêu cầu là “ba khoản thu lớn nhất”, đặc tả còn cần nói cách so sánh, xử lý trường hợp bằng nhau và giới hạn số lượng. Việc thiếu những chi tiết có ý nghĩa làm kết quả có thể vẫn hợp lệ theo máy nhưng không đúng ý người sử dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-predicates">Điều kiện, vị từ và ràng buộc trong một mô tả</a>

<details>
<summary>Xem chi tiết</summary>

**Vị từ** là điều kiện có thể đúng hoặc sai với dữ liệu đang xét, như `amount > 0`. **Ràng buộc** thu hẹp các kết quả được phép, chẳng hạn một người dùng chỉ được xem giao dịch thuộc tài khoản của mình.

Một mô tả có thể kết hợp nhiều điều kiện: khoản thu dương **và** thuộc đúng tài khoản. Nếu bỏ điều kiện quyền truy cập, kết quả truy vấn có thể đúng về số tiền nhưng sai về phạm vi dữ liệu; đây là lỗi đặc tả, không đơn thuần là lỗi thuật toán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-spec-completeness">Mức độ đầy đủ và tính mơ hồ của đặc tả</a>

<details>
<summary>Xem chi tiết</summary>

Đặc tả đủ rõ khi người đọc biết kết quả nào chấp nhận được và trường hợp nào phải bị loại. Câu “lấy các giao dịch gần đây” còn mơ hồ: gần đây tính theo ngày tạo, ngày ghi nhận hay lần chỉnh sửa cuối? Và lấy bao nhiêu mục?

Có nhiều kết quả thỏa một đặc tả chưa nhất thiết là lỗi: bài toán tìm một lịch họp phù hợp có thể có nhiều lời giải. Nhưng nếu ứng dụng cần một lựa chọn duy nhất, phải bổ sung tiêu chí chọn chứ không dựa vào thứ tự ngẫu nhiên của bộ đánh giá.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-evaluator-role">Bộ đánh giá: Vai trò biến mô tả thành phép tính</a>

<details>
<summary>Xem chi tiết</summary>

Bộ đánh giá biến mô tả thành hoạt động thực tế: đọc nguồn dữ liệu, xét điều kiện và tạo kết quả. Với cùng một truy vấn, nó có thể lựa chọn quét dữ liệu hoặc sử dụng chỉ mục nếu môi trường hỗ trợ.

Đây không phải phép màu xóa bỏ chi phí thực thi. Bộ đánh giá cần dữ liệu, tài nguyên và quy tắc ngữ nghĩa; câu khai báo sai không được bộ đánh giá tự đoán ý để sửa. Phần thuật toán tối ưu là kiến thức của công nghệ triển khai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-semantics-plan">Kết quả theo ngữ nghĩa và kế hoạch thực thi cụ thể</a>

<details>
<summary>Xem chi tiết</summary>

**Ngữ nghĩa** của truy vấn xác định những kết quả được chấp nhận; **kế hoạch thực thi** nêu bộ đánh giá sẽ làm gì để tính chúng. Hai kế hoạch có thể duyệt dữ liệu khác nhau mà trả cùng tập hàng, nếu chúng tuân thủ tất cả ràng buộc đã nêu.

Không nên dùng thứ tự mà một lần chạy tình cờ trả về để suy ra hợp đồng kết quả. Khi thứ tự là yêu cầu nghiệp vụ, phải diễn đạt nó tường minh; lúc đó chiến lược thực thi phải tôn trọng ràng buộc ấy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-execution-tradeoffs">Sự khác nhau giữa chiến lược, thứ tự quan sát và hiệu năng</a>

<details>
<summary>Xem chi tiết</summary>

Tách ý định khỏi kế hoạch cho phép công cụ điều chỉnh chiến lược khi dữ liệu lớn hơn hoặc hạ tầng thay đổi. Đổi lại, người viết có thể khó suy luận về chi phí thực thi nếu chỉ nhìn câu đặc tả.

Những tác động quan sát được cũng cần cân nhắc. Hai phép tính không có tác động phụ có thể đổi thứ tự thuận lợi, nhưng thao tác ghi log hoặc cập nhật bên ngoài có thể khiến việc sắp xếp lại thay đổi kết quả quan sát. Không mặc định mọi bộ đánh giá được quyền làm vậy.

</details>

- [Quay lại đầu trang](#back-to-top)
