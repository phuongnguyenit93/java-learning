# STEP 6 — QUIZ — PHASE 1 (WORKER ONLY)

**Hợp đồng:** Một worker, đúng **QUIZ / Phase 1**, không thay loại công việc. Vùng đích cố định **`V25:W26`**.

## Nhiệm vụ duy nhất

Tạo **bộ câu hỏi trắc nghiệm về chủ đề `A1,B1`**. Mỗi câu có **đúng 4 đáp án A/B/C/D** và **ghi rõ một đáp án đúng**. Nội dung thô, chưa phân nhóm hay giải thích.

- **Chỉ đọc:** `A1,B1`.
- **Chỉ ghi:** `V25:W26`, văn bản thuần. Giữa hai câu có một dòng trống.
- **Ví dụ định dạng văn bản thô** (không phải YAML/JSON):

~~~text
Câu hỏi?
A. Lựa chọn A
B. Lựa chọn B
C. Lựa chọn C
D. Lựa chọn D
Đáp án đúng: B
~~~

- Đáp án đúng phải chính xác và có thể kiểm chứng; ba distractor hợp lý, không mơ hồ hoặc có nhiều hơn một đáp án hợp lệ. **Chưa giải thích bất kỳ lựa chọn nào**, không thêm metadata hoặc code đáp án. Prime QA đáp án trước Phase 2.

## Phạm vi và an toàn bắt buộc

- **Chỉ thực hiện đúng loại và Phase của file này**; báo Prime rồi dừng, không tự sang Phase khác. Tin giao Prime chỉ chứa URL tab với `READ=A1,B1`, vùng `WRITE`, và đường dẫn file này; **không yêu cầu Prime thêm hướng dẫn hay cung cấp nội dung**.
- Tự đọc `A1,B1` trên tab được chỉ định để xác minh đúng chủ đề; các nguồn bổ sung (nếu có) **đã cố định ngay trong file này**. Không lấy Knowledge/Menu/Swagger/source Java/Quiz khác hay lịch sử chat làm nguồn sáng tác.
- Chỉ ghi **chính xác vùng WRITE được giao**, với giá trị thuần văn bản (không tạo công thức, đổi format/validation/notes/header). Kiểm tra trước khi viết: ô có formula, chip, dữ liệu lạ, owner khác hoặc nội dung đã thay đổi không rõ nguyên nhân → `BLOCKER`, không overwrite.
- **BẮT BUỘC CHIA Ô THAM LAM (MAX-FILL), KHÔNG CHIA ĐỀU:** Hoàn thiện toàn bộ chuỗi đầu ra trước. Điền **ô đầu tiên tối đa có thể**: lấy tiền tố dài nhất của phần còn lại sao cho **≤30.000 Unicode code points và ≤32.767 UTF-16 code units**. **Chỉ chuyển sang ô kế tiếp khi thêm ký tự Unicode tiếp theo sẽ vượt một trong hai giới hạn.** Tiếp tục theo thứ tự trái→phải, trên→dưới. Nếu cả chuỗi còn lại vừa một ô, **phải ghi trọn vào đúng một ô**, bỏ trống các ô sau. **CẤM** chia đều thành các phần, chia theo câu/group/line/YAML, chia để dễ đọc, đặt cỡ chunk tự chọn (2.000/2.895/5.000/28.000...), hoặc sang ô khác khi ô hiện tại còn chứa được ký tự tiếp theo. Trường hợp phần còn lại vượt 30.000 ký tự thường thì mọi ô không phải ô cuối **phải chứa đúng 30.000 code points**, trừ khi giới hạn UTF-16 chạm trước. Không chèn/thay/xóa ký tự phân cách hoặc cắt giữa surrogate pair. Không đủ tổng sức chứa → `BLOCKER: STEP6_CELL_CAPACITY`, không ghi bản thiếu. Sau ghi phải kiểm **max-fill cho từng ô không cuối** và exact readback của từng ô + chuỗi ghép.
- Sau khi ghi, đọc lại **từng ô đã ghi**, so sánh cả chuỗi ghép với bản đã soạn. Báo `TYPE`, `PHASE`, `WRITE_RANGE`, `ITEM_COUNT`, `READBACK=PASS/BLOCKER`; nếu BLOCKER ghi rõ lý do. Không chạy Gradle/importer, không sửa module/`CURRENT_STEP.VALUE`, không commit/push, không tự xóa/merge vùng khác.

**Ràng buộc mới:** Phase 1 **vẫn 2 hàng**, nhưng được dời xuống **25–26**. Phase 2 (20–24) đọc Phase 1 tại vùng mới này. Không đọc hoặc overwrite hàng 22–23 cũ.
