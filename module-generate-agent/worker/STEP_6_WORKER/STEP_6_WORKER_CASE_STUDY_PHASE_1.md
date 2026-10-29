# STEP 6 — CASE STUDY — PHASE 1 (WORKER ONLY)

**Hợp đồng:** Một worker, đúng **CASE_STUDY / Phase 1**, không thay loại công việc. Vùng đích cố định **`S25:U26`**.

## Nhiệm vụ duy nhất

Tạo **danh sách các vấn đề/tình huống xảy ra trong thực tế** liên quan chủ đề `A1,B1`.

- **Chỉ đọc:** `A1,B1`.
- **Chỉ ghi:** `S25:U26` (đúng vùng Prime giao); văn bản thô, một vấn đề mỗi dòng.
- **Không nhóm, không YAML/JSON, không `resolve`, `knowledge`, code hoặc giải pháp**. Prime sẽ QA tình huống trước Phase 2.

## Phạm vi và an toàn bắt buộc

- **Chỉ thực hiện đúng loại và Phase của file này**; báo Prime rồi dừng, không tự sang Phase khác. Tin giao Prime chỉ chứa URL tab với `READ=A1,B1`, vùng `WRITE`, và đường dẫn file này; **không yêu cầu Prime thêm hướng dẫn hay cung cấp nội dung**.
- Tự đọc `A1,B1` trên tab được chỉ định để xác minh đúng chủ đề; các nguồn bổ sung (nếu có) **đã cố định ngay trong file này**. Không lấy Knowledge/Menu/Swagger/source Java/Quiz khác hay lịch sử chat làm nguồn sáng tác.
- Chỉ ghi **chính xác vùng WRITE được giao**, với giá trị thuần văn bản (không tạo công thức, đổi format/validation/notes/header). Kiểm tra trước khi viết: ô có formula, chip, dữ liệu lạ, owner khác hoặc nội dung đã thay đổi không rõ nguyên nhân → `BLOCKER`, không overwrite.
- **BẮT BUỘC CHIA Ô THAM LAM (MAX-FILL), KHÔNG CHIA ĐỀU:** Hoàn thiện toàn bộ chuỗi đầu ra trước. Điền **ô đầu tiên tối đa có thể**: lấy tiền tố dài nhất của phần còn lại sao cho **≤30.000 Unicode code points và ≤32.767 UTF-16 code units**. **Chỉ chuyển sang ô kế tiếp khi thêm ký tự Unicode tiếp theo sẽ vượt một trong hai giới hạn.** Tiếp tục theo thứ tự trái→phải, trên→dưới. Nếu cả chuỗi còn lại vừa một ô, **phải ghi trọn vào đúng một ô**, bỏ trống các ô sau. **CẤM** chia đều thành các phần, chia theo câu/group/line/YAML, chia để dễ đọc, đặt cỡ chunk tự chọn (2.000/2.895/5.000/28.000...), hoặc sang ô khác khi ô hiện tại còn chứa được ký tự tiếp theo. Trường hợp phần còn lại vượt 30.000 ký tự thường thì mọi ô không phải ô cuối **phải chứa đúng 30.000 code points**, trừ khi giới hạn UTF-16 chạm trước. Không chèn/thay/xóa ký tự phân cách hoặc cắt giữa surrogate pair. Không đủ tổng sức chứa → `BLOCKER: STEP6_CELL_CAPACITY`, không ghi bản thiếu. Sau ghi phải kiểm **max-fill cho từng ô không cuối** và exact readback của từng ô + chuỗi ghép.
- Sau khi ghi, đọc lại **từng ô đã ghi**, so sánh cả chuỗi ghép với bản đã soạn. Báo `TYPE`, `PHASE`, `WRITE_RANGE`, `ITEM_COUNT`, `READBACK=PASS/BLOCKER`; nếu BLOCKER ghi rõ lý do. Không chạy Gradle/importer, không sửa module/`CURRENT_STEP.VALUE`, không commit/push, không tự xóa/merge vùng khác.

**Ràng buộc mới:** Phase 1 **vẫn 2 hàng**, nhưng được dời xuống **25–26**. Phase 2 (20–24) đọc Phase 1 tại vùng mới này. Không đọc hoặc overwrite hàng 22–23 cũ.
