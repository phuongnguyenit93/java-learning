<a id="back-to-top"></a>

# Điều khiển luồng bằng rẽ nhánh và vòng lặp

## Menu
- [Vai trò của điều khiển luồng trong chương trình mệnh lệnh](#imperative-control-purpose)
- [Lựa chọn nhánh dựa trên điều kiện](#imperative-branch-selection)
- [Lặp lại thao tác theo điều kiện hoặc tập phần tử](#imperative-repetition)
- [Theo dõi trạng thái và số lần lặp](#imperative-loop-progress)
- [Điều kiện dừng và vòng lặp không kết thúc](#imperative-termination)
- [Theo dõi một luồng có rẽ nhánh và lặp](#imperative-control-trace)

## <a id="imperative-control-purpose">Vai trò của điều khiển luồng trong chương trình mệnh lệnh</a>

<details>
<summary>Xem chi tiết</summary>

Điều khiển luồng quyết định **lệnh nào sẽ chạy tiếp**, thay vì luôn thực hiện mọi dòng đúng một lần. Rẽ nhánh chọn đường đi theo điều kiện; vòng lặp thực hiện lại một nhóm thao tác. Cả hai sử dụng mô hình trạng thái và thứ tự đã học.

Chẳng hạn giao dịch rút tiền phải dừng nếu số tiền không hợp lệ, còn một danh sách giao dịch yêu cầu lặp qua từng mục. Nếu bỏ qua điều kiện chọn đường đi, cùng một tập lệnh có thể cho kết quả sai dù phép tính riêng lẻ đều chính xác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-branch-selection">Lựa chọn nhánh dựa trên điều kiện</a>

<details>
<summary>Xem chi tiết</summary>

Một điều kiện được đánh giá tại thời điểm chương trình đi tới nhánh. Với `balance >= withdrawal`, giá trị `balance` tại lúc kiểm tra quyết định nhánh thành công hay từ chối. Không thể thay kết quả điều kiện bằng một dự đoán dựa trên số dư ban đầu nếu trạng thái đã đổi.

Mỗi nhánh cần có trách nhiệm rõ: nhánh hợp lệ cập nhật số dư, nhánh từ chối giữ số dư và thông báo lý do. Khi điều kiện không đầy đủ (ví dụ chỉ kiểm tra đủ tiền mà bỏ `withdrawal > 0`), một số đầu vào bất thường có thể đi nhầm đường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-repetition">Lặp lại thao tác theo điều kiện hoặc tập phần tử</a>

<details>
<summary>Xem chi tiết</summary>

Vòng lặp gom một hành động áp dụng cho nhiều phần tử hoặc cho tới khi điều kiện đổi. Để cộng các khoản thu `10, 20, 5`, biến tổng lần lượt nhận 0 → 10 → 30 → 35. Bước lặp thường đọc phần tử hiện tại, cập nhật kết quả trung gian và chuyển sang phần tiếp theo.

Có thể lặp theo số lần xác định hoặc theo điều kiện chưa biết trước số lần. Cả hai đều cần hiểu công việc lặp lại và trạng thái nào tiến triển; tên vòng lặp/cú pháp cụ thể thuộc module ngôn ngữ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-loop-progress">Theo dõi trạng thái và số lần lặp</a>

<details>
<summary>Xem chi tiết</summary>

Đọc vòng lặp qua **một lần lặp mẫu**: kiểm tra điều kiện, thực hiện thân vòng lặp, cập nhật biến theo dõi rồi kiểm tra lại. Nếu xử lý ba khoản thu, sau lần thứ hai phải giải thích được vì sao tổng là 30 và còn một khoản chưa xử lý.

Một lỗi phổ biến là cập nhật chỉ số quá sớm hoặc quá muộn, khiến bỏ qua mục đầu hoặc xử lý một mục hai lần. Bảng `lần lặp | chỉ số | mục hiện tại | tổng` thường làm lỗi lệch một đơn vị rõ hơn cách nhìn tổng kết cuối.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-termination">Điều kiện dừng và vòng lặp không kết thúc</a>

<details>
<summary>Xem chi tiết</summary>

Vòng lặp dừng khi điều kiện tiếp tục không còn đúng hoặc có một quyết định kết thúc rõ ràng. Một vòng chờ `remaining > 0` chỉ có thể kết thúc nếu có bước làm `remaining` giảm, hoặc sự kiện bên ngoài khiến điều kiện thay đổi.

Nếu biến điều kiện không bao giờ đổi, vòng lặp có nguy cơ chạy vô hạn. Ngược lại, việc giảm quá mức có thể làm hỏng bất biến như `remaining >= 0`. Đánh giá tính dừng cần xem cả điều kiện lẫn tiến triển trạng thái, không chỉ đọc thân vòng lặp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="imperative-control-trace">Theo dõi một luồng có rẽ nhánh và lặp</a>

<details>
<summary>Xem chi tiết</summary>

Thử chuỗi giao dịch `[+20, -30, -200]` từ số dư 100, quy tắc không cho âm. Nhận 20 → 120; rút 30 → 90; rút 200 bị từ chối → 90. Bảng vết cho thấy cả ba lần lặp được xét nhưng chỉ hai lần cập nhật thành công.

Vết này kết hợp thứ tự, nhánh và vòng lặp: thay thứ tự giao dịch có thể làm nhánh thành công/thất bại thay đổi, dù số tiền cuối cùng được yêu cầu giống nhau. Đây là minh chứng vì sao kiểm thử chương trình mệnh lệnh cần xét đường thực thi.

</details>

- [Quay lại đầu trang](#back-to-top)
