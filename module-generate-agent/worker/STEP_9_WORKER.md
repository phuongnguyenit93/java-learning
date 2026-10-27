# STEP 9 — WORKER ONLY: Interview hoặc Summary Questions

**Bắt buộc đọc toàn văn file worker-only này trước khi làm.** Prime giao ROLE rõ ràng (INTERVIEW/SUMMARY), URL/gid, tọa độ đọc, vùng ghi/staging, và đường dẫn contract này. Một worker = một Menu Cấp 1 cho **một ROLE**. Không tự chọn ROLE, thay scope, đọc rules Prime/STEP khác, spawn worker, commit/push hay ghi CURRENT_STEP.

## Chung

Tự sáng tác câu hỏi/đáp án đúng kỹ thuật, bao quát điểm cốt lõi của chương, không trivia/ép số lượng, không lấy nguyên mẫu ví dụ trong hướng dẫn. Dùng một YAML document hợp lệ; mỗi item chỉ `question`, `answer` là nonblank strings, đáp án có thể chứa Markdown/literal block `|-` và code fence đúng kỹ thuật. Không viết metadata/defaults/related (code Normalize xử lý Interview), không tạo JSON/YAML multi-doc. Existing valid content → UNCHANGED. FORMAT_FIX chỉ khi có thể chứng minh nội dung, thứ tự, metadata không thay đổi; không được phát minh dữ liệu bổ sung.

## ROLE=INTERVIEW

- **Chỉ role này được đọc** A/B chương, toàn bộ Knowledge C:N ở tất cả Menu Cấp 2 của chương được giao (ghép các fragment trên từng hàng không separator), Difficulty O nếu Prime chỉ định. Không đọc Summary Questions staging hoặc sản phẩm worker SUMMARY.
- Sinh câu hỏi phỏng vấn sâu về giải thích nguyên lý, trade-off, debugging, ứng dụng, ngoại lệ; answer giải thích khi nào/vì sao/giới hạn, đủ để người đọc tự hiểu.
- **Chỉ ghi P<SAME_PARENT_ROW>:S<SAME_PARENT_ROW>** nếu cả bốn ô trống và được cấp ownership. Nếu đã có YAML hợp lệ, trả UNCHANGED; chỉ FORMAT_FIX lỗi cú pháp/Markdown không đổi ý nghĩa, **không rewrite Interview để chèn Summary**.
- Định dạng:
  ```yaml
  questions:
    - question: "Khi nào composition tốt hơn inheritance?"
      answer: |-
        Giải thích trade-off của các phương án.
  ```
- P:S là bốn fragment liên tục, mỗi fragment ≤30.000 Unicode code points, ghép đúng P→Q→R→S không separator. Preflight formula/note/chip/validation, no orphan, snapshot, exact readback P:S, parser, Markdown và diff. Không ghi T:W/X:AA/C:N, source/module hay tab khác.

## ROLE=SUMMARY

- **Chỉ được đọc** tên module, danh sách tên Menu Cấp 1 toàn module, tiêu đề Menu Cấp 2 thuộc đúng chương được giao ở A:B. **CẤM đọc Knowledge C:N, Difficulty O, Interview P:S, Quiz T:W, STEP 5 Summary, Swagger/API, code/README, file của worker khác, lịch sử chat**. Những thông tin này có thể gây thiên lệch cho câu hỏi cần tư duy độc lập.
- Sinh câu hỏi tổng hợp để người học tự kiểm tra hiểu biết toàn chương; có đáp án đúng, rõ ràng, không biến thành phỏng vấn tình huống quá sâu hoặc bài Real-world Practice STEP 8. Worker tự chọn phạm vi nội dung dựa trên kiến thức đã biết.
- **Chỉ ghi staging độc quyền Prime cấp** `<MODULE>/build/step9/summary/<chapter>/summary.yml`; không đọc/ghi Interview hoặc P:S. File chứa **root `summaryQuestions[]`**, mỗi item đúng question/answer:
  ```yaml
  summaryQuestions:
    - question: "Encapsulation bảo vệ điều gì?"
      answer: "Bảo vệ các quy tắc trạng thái hợp lệ của đối tượng."
  ```
- Preflight đường dẫn và ownership; nếu YAML staging hợp lệ đã có → UNCHANGED, không ghi đè. QA YAML, nội dung, exact readback toàn file. **Không tự ghép** hai nhóm; Prime sẽ dùng code merger và code importer.

## Báo Prime

Báo `ROLE`, chapter/parent row, URL/gid, `READ_SCOPE`, `WRITE_SCOPE`, `QUESTION_COUNT`, `MODE=NEW_FILL|UNCHANGED|FORMAT_FIX`, `PREFLIGHT`, `YAML_PARSE`, `CONTENT_QA`, `READBACK`, `CONTENT_PRESERVED` và `BLOCKERS`. Chuyển SLEEPING khi hoàn thành. Worker không gọi Normalize, importer, STEP 10 hay cập nhật tiến độ.