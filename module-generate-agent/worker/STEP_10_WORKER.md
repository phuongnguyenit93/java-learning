# STEP 10 — WORKER ONLY: Quiz YAML cho MỘT chương

**CURRENT_STEP policy (2026-10-25):** Progress is owned ONLY by `CURRENT_STEP.VALUE` (string) in the verified module `master.json`, never by any Google Sheet/Excel cell. Prime updates after full QA using `module-generate-agent/CURRENT_STEP.md`; Worker does not write it. Any old cell, label, formula, or Sheet-readback instructions about progress below are superseded. The STEP-specific completion gates still apply.


> **BẮT BUỘC tự đọc TOÀN VĂN file worker-only này.** Prime chỉ cung cấp đúng URL tab, tọa độ nguồn, vùng T:W hàng cha và đường dẫn file này. Không tự đọc file Prime/rules/STEP khác, source repository hay lịch sử chat để thay Knowledge trên Sheet.

**FILL-ONLY VỀ NỘI DUNG — CHO PHÉP FORMAT_FIX:** Preflight cả `T<PARENT_ROW>:W<PARENT_ROW>`. Trống hết → tạo Quiz YAML mới. Đã có nội dung → **không thay câu hỏi, bốn đáp án/giải thích, `correctAnswerId`, thứ tự hoặc metadata**, trừ chỉ thị đặc biệt tường minh của người dùng; chỉ được sửa lỗi YAML/Markdown nhúng về mặt cú pháp (indent, quote, dấu phân cách, code fence...) và chứng minh toàn bộ giá trị nội dung giữ nguyên. YAML hợp lệ → `UNCHANGED` không ghi; không chứng minh được sửa format thuần túy → BLOCKER.

## W1 — Phạm vi nguồn và ghi

1. Một worker = **một Menu Cấp 1**. Chỉ đọc `A1`, `B1`, A cha, B filename cha, tất cả tiêu đề B con và **toàn văn Knowledge C:N của mỗi hàng con** Prime giao (ghép C→N không separator), Difficulty O nếu được giao.
2. **Chỉ được ghi T:W trên đúng hàng cha** của chương. Không sửa A:S hay X:AA, Difficulty O, Video X:AA, Interview P:S, các hàng con/Template/tab khác, CURRENT_STEP.VALUE hoặc source module. Không spawn worker, chạy Gradle/import hay commit/push.
3. Header `T18=Quiz`, `T19:W19=Quiz YAML`, và filename `.md` từ B cha phải đúng; nguồn không đủ hoặc ô đích không an toàn → BLOCKER, không đoán.

## W2 — Chất lượng trắc nghiệm

- Sinh những câu hỏi đánh giá kiến thức cốt lõi và khả năng suy luận/vận dụng, runtime behavior, debugging, trade-off, misconception, pitfall, không chép câu README và không nhồi câu vô nghĩa.
- **Mỗi question đúng một đáp án** trong A/B/C/D, ba distractors phải sai nhưng plausible, phản ánh nhầm lẫn thực sự. Không cố tạo filler; nếu không tìm được bốn lựa chọn có giá trị thì đổi cách hỏi hoặc bỏ câu.
- Tránh lộ đáp án đúng qua độ dài, grammar, thuật ngữ, chi tiết, giọng điệu, hoặc đáp án vô lý. Tự QA che `correctAnswerId` (blind-answer). Đáp án tương đồng hợp lý về độ dài, cấu trúc và scope, nhưng không ép bằng nhau máy móc.
- **`explanation` bắt buộc cho từng A/B/C/D**: nêu vì sao lựa chọn ấy đúng hoặc sai. Ví dụ Java phải biên dịch đúng trong context; output, loại ngoại lệ, compile/runtime, API phải chính xác. Markdown/code fence `java`, `text` khi hữu ích, không đặt Markdown fence bao cả YAML.

## W3 — YAML nguồn duy nhất

```yaml
questions:
  - question: "Điều gì xảy ra khi subclass override một instance method hợp lệ?"
    answers:
      - id: A
        answer: "Lời gọi luôn chạy method của superclass."
        explanation: "Sai; method động được chọn theo runtime class của receiver."
      - id: B
        answer: "Runtime dispatch chọn implementation theo đối tượng thực tế."
        explanation: "Đúng với instance method được override và lời gọi động."
      - id: C
        answer: "Mọi static method đều được override theo cách tương tự."
        explanation: "Sai; static method có thể bị hide, không override."
      - id: D
        answer: "Private method luôn bị override trong subclass."
        explanation: "Sai; private method không được kế thừa để override."
    correctAnswerId: B
```

- Root **chỉ** `questions` là list không rỗng. Mỗi item **chỉ** `question` (nonblank string), `answers` (chính xác bốn object có đúng `id`, `answer`, `explanation`) và `correctAnswerId` thuộc A/B/C/D. Mỗi ID A/B/C/D xuất hiện **một lần**; không gán nhiều đáp án đúng.
- **Không ghi** question `id` `readmeRelated`, `apiRelated` hoặc metadata; code Normalize sẽ bổ sung. Không dùng JSON/JSONL, YAML multiple documents, custom tag, alias/anchor, placeholder hoặc TODO.
- `answer` và `explanation` có thể dùng YAML literal block `|-` để giữ Markdown nhiều dòng; YAML sau parse phải bảo toàn nội dung dự kiến.

## W4 — Preflight, write, readback

1. **Chỉ `NEW_FILL` mới soạn toàn bộ Quiz YAML**; YAML có sẵn thì QA `UNCHANGED/FORMAT_FIX`, không tạo lại question/answers. QA kỹ thuật, parse root/keys/types/correctAnswerId và tính đa dạng; không cố sinh số lượng cố định.
2. Nếu YAML dài >120.000 Unicode code points: `BLOCKER: QUIZ CAPACITY`, không truncate hoặc mượn cột khác. Chia tối đa 4 fragments ≤30.000 Unicode/ô không cắt giữa surrogate pair, ghép T→W không thêm separator phải đúng 100%.
3. Preflight **cả T:W trước khi soạn và ngay trước WRITE**: `userEnteredValue`/formula, `effectiveValue`, `formattedValue`, `note`, `dataValidation`, `chipRuns`; ownership và Knowledge phải unchanged. Trống hoàn toàn → `NEW_FILL`. Có YAML cũ → nối T→W, giữ snapshot, phân biệt `UNCHANGED` (đã hợp lệ) với `FORMAT_FIX` (lỗi cú pháp/định dạng thật). Không tạo lại Quiz hoặc thêm đáp án vào tài liệu cũ; nguồn đổi, formula/chip hoặc ownership xung đột → BLOCKER.
4. `FORMAT_FIX` chỉ sửa ký tự cấu trúc và Markdown nhúng, giữ **mọi question/answer/explanation, `correctAnswerId`, số lượng, thứ tự, ID, metadata và nội dung code**. YAML hợp lệ trước sửa → so parsed values; YAML hỏng cú pháp → đối chiếu từng scalar gốc. Không chứng minh bảo toàn → `BLOCKER: FORMAT_FIX_NOT_PROVABLY_CONTENT_PRESERVING`, không ghi. Chỉ ghi text-only trong T:W, không đổi format/notes/validation. Readback bốn ô, nối không separator và so đúng bản cuối đã QA, parse và so diff trước–sau; lỗi write không rõ thì không retry mù.
5. Báo Prime: `STATUS=COMPLETED/UNCHANGED/BLOCKER/FAILED`, `MODE=NEW_FILL/FORMAT_FIX/UNCHANGED`, `PARENT_CELL`, `CHILD_ROWS`, `SOURCE_COVERAGE`, `OUTPUT_RANGE`, `QUESTION_COUNT`, `TOTAL_UNICODE_CHARS`, `FILLED_CELLS`, `PREFLIGHT`, `YAML_PARSE`, `TECHNICAL_QA`, `DISTRACTOR_QA`, `FORMAT_DIFF`, `CONTENT_PRESERVED=PASS/FAIL`, `READBACK`, `BLOCKER`. `COMPLETED` chỉ khi QA + READBACK PASS; `UNCHANGED` chỉ khi dữ liệu hợp lệ và không write. Sau đó **SLEEPING**, không chạy Normalize/import/CURRENT_STEP.VALUE.
