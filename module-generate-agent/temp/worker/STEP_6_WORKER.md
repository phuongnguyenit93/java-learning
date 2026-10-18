# STEP 6 — WORKER ONLY: Viết Interview JSON cho MỘT chương

> **BẮT BUỘC tự đọc TOÀN VĂN đúng file `/java-learning/module-generate-agent/temp/worker/STEP_6_WORKER.md` này trước khi thực hiện**; đọc lại nếu context bị rút gọn. Đây là tài liệu nhiệm vụ duy nhất cho worker. Không đọc STEP_6.md (Prime-only), STEP khác, GENERAL/rules/TXT, source repo hoặc lịch sử chat để lấy nội dung thay cho Google Sheets. Prime chỉ gửi link tab, tọa độ nguồn và đúng bốn ô đích.

## W1 — Nguồn và quyền ghi

1. **Một nhiệm vụ = một Menu Cấp 1 và tất cả Menu Cấp 2 thuộc chương đó.** Trên đúng tab/gid, chỉ đọc các tọa độ Prime giao: `A1`, `B1`, **A/B hàng cha**, tiêu đề `B` các hàng con, **toàn bộ Knowledge `C:N` trên từng hàng con**, Difficulty `O` hàng con nếu được giao. **Ghép C→N không separator theo từng hàng**, đọc hết mọi bài, không suy từ tiêu đề hoặc tóm tắt.
2. **Chỉ được ghi vào `T<PARENT_ROW>:W<PARENT_ROW>` của hàng Menu Cấp 1** đã giao. Tối đa 4 ô, mỗi ô tối đa 30.000 ký tự Unicode. Không ghi Interview trên các hàng con, không sửa A:S, X trở đi, header, Template, J3, tab/module/source khác. Không spawn worker, không commit/push.
3. `A<PARENT_ROW>` là chủ đề chương; `B<PARENT_ROW>` là filename có sẵn, **chỉ đọc**. Header và quyền sở hữu đã được Prime kiểm tra; worker phải báo `BLOCKER` nếu thiếu nguồn, dữ liệu sai, quyền ghi không rõ hoặc mục không thuộc chương.

## W2 — Viết câu hỏi có giá trị sử dụng thực tế

**Nhiệm vụ nội dung rất đơn giản:** thiết kế các **câu hỏi và câu trả lời có thể dùng trong phỏng vấn**, hoặc **câu hỏi giúp người học hệ thống lại kiến thức** của chương.

- **Ưu tiên tính thực tế:** người học hiểu, giải thích, áp dụng, so sánh, chọn giải pháp, nhận biết lỗi phổ biến hoặc đánh giá tình huống thật. Câu trả lời phải đúng kỹ thuật, rõ ràng, tự đứng được và phù hợp với Knowledge đã đọc.
- **Không tạo câu hỏi quá vụn vặt hoặc hỏi những chi tiết hiếm gặp, không có giá trị áp dụng/phỏng vấn/ôn tập** chỉ để đủ số lượng. Không ra câu hỏi đánh đố, bắt nhớ tên nội bộ, số liệu/phiên bản kỳ lạ hay chi tiết triển khai không liên quan.
- Chọn số lượng câu hỏi **theo lượng kiến thức có ích**, không có quota tối thiểu/cố định cho mỗi bài. Bao quát các ý quan trọng của chương, tránh lặp câu hỏi và tránh lặp nguyên văn câu trả lời từ bài học. Không tự thêm nội dung ngoài phạm vi đã đọc, không bịa API hay giả định kỹ thuật.

## W3 — Định dạng JSON duy nhất

Soạn **MỘT chuỗi JSON hợp lệ** (không Markdown fence, không lời dẫn, không YAML), chính xác dạng:

```json
{
  "questions": [
    {
      "question": "Khi nào nên ưu tiên composition thay cho inheritance?",
      "answer": "Ưu tiên composition khi cần thay đổi hoặc kết hợp hành vi linh hoạt mà không tạo cây kế thừa cứng; đánh giá dựa trên quan hệ thực tế giữa các đối tượng."
    }
  ]
}
```

**Đây là ví dụ định dạng, không phải câu hỏi bắt buộc hay câu trả lời phải sao chép.**

- Root chỉ có **`questions` là một JSON array không rỗng**. Mỗi phần tử chỉ có **hai trường `question` và `answer`**, đều là chuỗi không blank. Không sinh `aiGenerated`, `reviewed`, `readmeRelated`, `apiRelated`, `difficulty` hay metadata khác ở STEP 6. Không dùng JSONL, không tách một câu hỏi thành nhiều item không đồng bộ.
- JSON phải **parse được**: escape dấu nháy kép, backslash, newline trong string đúng JSON; không có comment, trailing comma hoặc placeholder. Phần tự động parse, bổ sung default/related rồi ghi lại Sheet được thực hiện trong **bước code riêng sau này**, không thuộc quyền worker.

## W4 — Preflight, ghi và đọc lại

1. **Soạn và tự QA toàn bộ JSON trước bất kỳ write nào.** Kiểm tra cú pháp, mọi cặp question/answer đầy đủ và có ý nghĩa, không trùng, không lan sang ngoài chương. Đếm Unicode code points, chia thành **tối đa 4 đoạn liên tiếp** (mỗi đoạn ≤30.000 ký tự), ghi theo thứ tự **T→U→V→W trên CÙNG một hàng cha**. **Nối không separator phải trả về nguyên 100% chuỗi JSON gốc**, kể cả Unicode/newline; không cắt surrogate pair. Nếu >4 ô: `BLOCKER: INTERVIEW CAPACITY`, không rút nội dung để vượt blocker, không ghi một phần, không sang X/hàng khác.
2. **Ngay trước khi ghi**, đọc lại **toàn bộ bốn ô T:W đích kể cả ô chưa dùng**: `userEnteredValue`/formula, `effectiveValue`, `formattedValue`, `note`, `dataValidation`, `chipRuns`; ô đích phải trống hoàn toàn và không có worker khác sở hữu. Đọc lại A/B cha và các nguồn giao để xác minh không thay đổi. Có ô bị chiếm/nguồn đổi → `BLOCKER: TARGET OCCUPIED / SOURCE CHANGED`, không overwrite hoặc ghi một phần.
3. Ghi **chỉ các mảnh JSON vào T:W của hàng cha**, bằng value text thông thường, không biến JSON thành công thức và không thay đổi format/notes/validation. Nếu write trả lỗi/không rõ kết quả, **đọc lại trạng thái thực trước khi quyết định**, không retry mù.
4. **Exact readback từng ô trong cả T:W**: các ô đã ghi khớp từng đoạn, ô đuôi vẫn rỗng; ghép T→W không separator so khớp **100% Unicode/whitespace/newline** với JSON gốc. Parse lại JSON đã đọc về, kiểm tra root `questions`, từng `question`/`answer` không blank và không có field khác. Chỉ `COMPLETED` khi readback và QA đều PASS.

## W5 — Báo Prime và SLEEPING

Báo một lần: `STATUS=COMPLETED/BLOCKER/FAILED`, tab/gid, `PARENT_CELL`, `CHILD_ROWS`, `READ_COVERAGE`, `OUTPUT_RANGE_T_TO_W`, `QUESTION_COUNT`, `TOTAL_UNICODE_CHARS`, `FILLED_CELLS`, `PREFLIGHT`, `JSON_PARSE`, `READBACK=PASS/FAIL`, `CONTENT_QA=PASS/FAIL`, `PRESERVATION`, `BLOCKERS`. Chỉ báo COMPLETED nếu mọi điều kiện đều PASS. Xong chuyển **SLEEPING**, không tự nhận chương khác, không chạy STEP 7 và không cập nhật J3.
