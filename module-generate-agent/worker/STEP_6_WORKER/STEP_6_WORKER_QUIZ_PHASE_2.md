# STEP 6 — QUIZ — PHASE 2 (WORKER ONLY)

**Hợp đồng:** Một worker, đúng **QUIZ / Phase 2**, không thay loại công việc. Vùng đích cố định **`V20:W24`**.

## Nhiệm vụ duy nhất

Đọc nguồn Phase 1 của đúng Quiz tại **`V25:W26`** (ghép **V25→W25→V26→W26**); đọc `A1,B1` để xác nhận module. **Chỉ chuyển các câu hỏi và 4 lựa chọn thành khung YAML theo group**.

- **Chỉ ghi:** `V20:W24` (đúng vùng Prime giao).
- Mỗi câu hỏi phải giữ chính xác nội dung, 4 lựa chọn A/B/C/D và **một `correctAnswerId`** như Phase 1; không tự đổi đáp án hoặc thêm câu hỏi. GroupID 1..N, mỗi nhóm không rỗng.
- **`explanation` có mặt nhưng rỗng cho cả 4 đáp án** (tương thích cách gọi giải thích của STEP 10):

~~~yaml
quiz:
  - groupID: 1
    groupName: "Tên nhóm"
    questions:
      - question: "Câu hỏi?"
        answers:
          - { id: A, answer: "Lựa chọn A", explanation: "" }
          - { id: B, answer: "Lựa chọn B", explanation: "" }
          - { id: C, answer: "Lựa chọn C", explanation: "" }
          - { id: D, answer: "Lựa chọn D", explanation: "" }
        correctAnswerId: B
~~~

- Không viết giải thích trong Phase 2, không tạo Phase 4 cho Quiz. Parse YAML, kiểm đếm câu, kiểm mỗi nhóm đủ 4 ID duy nhất và đáp án đúng tồn tại; exact readback.

## Phạm vi và an toàn bắt buộc

- **Chỉ thực hiện đúng loại và Phase của file này**; báo Prime rồi dừng, không tự sang Phase khác. Tin giao Prime chỉ chứa URL tab với `READ=A1,B1`, vùng `WRITE`, và đường dẫn file này; **không yêu cầu Prime thêm hướng dẫn hay cung cấp nội dung**.
- Tự đọc `A1,B1` trên tab được chỉ định để xác minh đúng chủ đề; các nguồn bổ sung (nếu có) **đã cố định ngay trong file này**. Không lấy Knowledge/Menu/Swagger/source Java/Quiz khác hay lịch sử chat làm nguồn sáng tác.
- Chỉ ghi **chính xác vùng WRITE được giao**, với giá trị thuần văn bản (không tạo công thức, đổi format/validation/notes/header). Kiểm tra trước khi viết: ô có formula, chip, dữ liệu lạ, owner khác hoặc nội dung đã thay đổi không rõ nguyên nhân → `BLOCKER`, không overwrite.
- **BẮT BUỘC CHIA Ô THAM LAM (MAX-FILL), KHÔNG CHIA ĐỀU:** Hoàn thiện toàn bộ chuỗi đầu ra trước. Điền **ô đầu tiên tối đa có thể**: lấy tiền tố dài nhất của phần còn lại sao cho **≤30.000 Unicode code points và ≤32.767 UTF-16 code units**. **Chỉ chuyển sang ô kế tiếp khi thêm ký tự Unicode tiếp theo sẽ vượt một trong hai giới hạn.** Tiếp tục theo thứ tự trái→phải, trên→dưới. Nếu cả chuỗi còn lại vừa một ô, **phải ghi trọn vào đúng một ô**, bỏ trống các ô sau. **CẤM** chia đều thành các phần, chia theo câu/group/line/YAML, chia để dễ đọc, đặt cỡ chunk tự chọn (2.000/2.895/5.000/28.000...), hoặc sang ô khác khi ô hiện tại còn chứa được ký tự tiếp theo. Trường hợp phần còn lại vượt 30.000 ký tự thường thì mọi ô không phải ô cuối **phải chứa đúng 30.000 code points**, trừ khi giới hạn UTF-16 chạm trước. Không chèn/thay/xóa ký tự phân cách hoặc cắt giữa surrogate pair. Không đủ tổng sức chứa → `BLOCKER: STEP6_CELL_CAPACITY`, không ghi bản thiếu. Sau ghi phải kiểm **max-fill cho từng ô không cuối** và exact readback của từng ô + chuỗi ghép.
- Sau khi ghi, đọc lại **từng ô đã ghi**, so sánh cả chuỗi ghép với bản đã soạn. Báo `TYPE`, `PHASE`, `WRITE_RANGE`, `ITEM_COUNT`, `READBACK=PASS/BLOCKER`; nếu BLOCKER ghi rõ lý do. Không chạy Gradle/importer, không sửa module/`CURRENT_STEP.VALUE`, không commit/push, không tự xóa/merge vùng khác.

**Hợp đồng Phase 2/3:** Phase 2 viết YAML skeleton đầy đủ group vào vùng 5 hàng (20–24). **Phase 3 của cùng loại đọc và cập nhật trực tiếp chính vùng 5 hàng đó**, chỉ lấp các field đã được giao; không tạo block group riêng ở hàng 27+, không xem việc cập nhật Phase 2 là xung đột. Phase 4 mới dùng block 3 hàng/group từ 27 (Interview/Case Study). Có dữ liệu lạ không thuộc STEP 6 hoặc writer khác → BLOCKER, không ghi đè.
