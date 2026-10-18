<a id="back-to-top"></a>

# Tính đúng đắn, đánh đổi và liên hệ các lối lập trình

## Menu
- [Điều kiện đầu vào, bước chuyển và kết quả mong đợi](#imperative-correctness)
- [Rủi ro của cập nhật trạng thái và thứ tự thao tác](#imperative-state-risks)
- [Truy vết lỗi qua lịch sử thay đổi trạng thái](#imperative-debug-tracing)
- [Lợi ích và chi phí của điều khiển thực thi tường minh](#imperative-control-tradeoffs)
- [Đối chiếu lời giải mệnh lệnh và mô tả khai báo](#imperative-declarative-contrast)
- [Kết hợp thủ tục với các paradigm khác và phạm vi Java](#imperative-paradigm-bridges)

## <a id="imperative-correctness">Điều kiện đầu vào, bước chuyển và kết quả mong đợi</a>

<details>
<summary>Xem chi tiết</summary>

Để kiểm chứng, xác định **điều kiện trước**, các phép chuyển trạng thái được phép và **điều kiện sau**. Với rút tiền 30 từ 100, điều kiện trước là số tiền dương và đủ số dư; điều kiện sau khi thành công là 70 và không có số dư âm.

Nếu thao tác bị từ chối, điều kiện sau phải nêu số dư không đổi. Kiểm thử hai trường hợp thành công/thất bại cho bằng chứng mạnh hơn chỉ chạy một ví dụ thuận lợi. Đây là suy luận về phép chuyển trạng thái, chưa cần cú pháp assertion cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-state-risks">Rủi ro của cập nhật trạng thái và thứ tự thao tác</a>

<details>
<summary>Xem chi tiết</summary>

Lỗi có thể xuất hiện khi nhiều bước cùng đọc/ghi một giá trị: cập nhật ở sai nhánh, áp dụng khoản giảm hai lần hoặc dùng lại giá trị đã cũ. Ví dụ tính phí sau khi ghi số dư nhưng dựa trên số dư trước đó có thể tạo kết quả không phù hợp quy tắc.

Một trạng thái dùng chung cũng khiến đường thực thi phụ thuộc lịch sử trước đó. Cần theo dõi quyền sở hữu thao tác ghi, thứ tự đọc/ghi và điều kiện kiểm tra lại; cơ chế khóa hoặc bộ nhớ đa luồng thuộc module concurrency.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-debug-tracing">Truy vết lỗi qua lịch sử thay đổi trạng thái</a>

<details>
<summary>Xem chi tiết</summary>

Khi kết quả sai, truy từ hậu điều kiện bị vi phạm về lần cập nhật gần nhất thay vì sửa ngẫu nhiên. Ghi `balance trước | số tiền | nhánh | balance sau` cho từng giao dịch giúp xác định lỗi do điều kiện, phép gán hay thứ tự.

Ví dụ nếu rút 90 sau rút 30 mà số dư từ 70 xuống -20, vết cho thấy nhánh từ chối đã không bảo vệ lệnh cập nhật. Tách dữ liệu đầu vào khỏi đầu ra và trạng thái giúp tái hiện lỗi một cách nhất quán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-control-tradeoffs">Lợi ích và chi phí của điều khiển thực thi tường minh</a>

<details>
<summary>Xem chi tiết</summary>

Điều khiển từng bước phù hợp khi thứ tự hành động mang ý nghĩa, chẳng hạn cập nhật tài khoản, trình tự kiểm tra hoặc xử lý thiết bị. Ưu điểm là người đọc nhìn thấy chỗ trạng thái được thay đổi và nơi lỗi có thể xuất hiện.

Giá phải trả là mã có thể dài, phụ thuộc trình tự và khó sửa khi nhiều nhánh/vòng lặp tương tác. Với bài toán chỉ cần mô tả điều kiện chọn dữ liệu, một đặc tả khai báo có thể truyền đạt ý định ngắn gọn hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-declarative-contrast">Đối chiếu lời giải mệnh lệnh và mô tả khai báo</a>

<details>
<summary>Xem chi tiết</summary>

Với danh sách giao dịch, lời giải mệnh lệnh có thể lặp từng mục, kiểm tra `amount > 0` rồi cộng vào tổng. Lối khai báo diễn đạt “tổng các khoản dương” mà không bắt người đọc theo dõi từng lần tăng biến cộng dồn.

Hai mô tả có thể cùng tạo kết quả 35 cho `[10, -2, 25]`; sự khác nhau nằm ở mức độ chỉ định **cách thực hiện**. Bên dưới bộ đánh giá khai báo vẫn có các thao tác thực tế; không nên kết luận cách khai báo không có thứ tự hoặc không có tác động phụ trong mọi môi trường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-paradigm-bridges">Kết hợp thủ tục với các paradigm khác và phạm vi Java</a>

<details>
<summary>Xem chi tiết</summary>

**Lập trình thủ tục** là cách tổ chức hành động mệnh lệnh thành đơn vị gọi lại được, không phải một đối thủ tách rời hoàn toàn. OOP đưa trách nhiệm cùng trạng thái/hành vi vào đối tượng; lập trình hàm ưu tiên biến đổi đầu vào/đầu ra rõ ràng; khai báo mô tả điều phải đúng.

Trong ứng dụng thực tế, một phương thức đối tượng có thể dùng câu lệnh mệnh lệnh bên trong, còn một phép biến đổi hàm có thể được gọi trong luồng thủ tục. Học tiếp module Java để nắm cú pháp lệnh, phạm vi biến, vòng lặp và phương thức cụ thể; các quan hệ paradigm được giữ ở cấp mô hình.

</details>

- [Quay lại đầu trang](#back-to-top)
