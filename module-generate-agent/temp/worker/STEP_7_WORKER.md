# STEP 7 — WORKER ONLY: Quiz YAML cho MỘT chương

> **BẮT BUỘC tự đọc TOÀN VĂN file worker-only này.** Prime chỉ cung cấp đúng URL tab, tọa độ nguồn, vùng T:W hàng cha và đường dẫn file này. Không tự đọc file Prime/rules/STEP khác, source repository hay lịch sử chat để thay Knowledge trên Sheet.

**FILL-ONLY — KHÔNG REPLACE:** Kiểm tra **cả `T<PARENT_ROW>:W<PARENT_ROW>`** ngay khi nhận nhiệm vụ, trước khi soạn Quiz YAML. Nếu bất kỳ ô đích nào đã có nội dung, `BLOCKER: EXISTING TARGET CONTENT`, `WRITE=NOT_RUN`, **DỪNG ngay toàn nhiệm vụ**. Không thay thế Quiz cũ, không fill fragment còn thiếu, không ghi một phần hoặc tự coi `COMPLETED/UNCHANGED`. Chỉ **chỉ thị đặc biệt tường minh từ người dùng** cho đúng vùng này mới cho phép replace.

## W1 — Phạm vi nguồn và ghi

1. Một worker = **một Menu Cấp 1**. Chỉ đọc `A1`, `B1`, A cha, B filename cha, tất cả tiêu đề B con và **toàn văn Knowledge C:N của mỗi hàng con** Prime giao (ghép C→N không separator), Difficulty O nếu được giao.
2. **Chỉ được ghi T:W trên đúng hàng cha** của chương. Không sửa A:S hay X:AA, Difficulty O, Video X:AA, Interview P:S, các hàng con/Template/tab khác, J3 hoặc source module. Không spawn worker, chạy Gradle/import hay commit/push.
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
- **Không ghi** question `id`, `aiGenerated`, `reviewed`, `readmeRelated`, `apiRelated` hoặc metadata; code Normalize sẽ bổ sung. Không dùng JSON/JSONL, YAML multiple documents, custom tag, alias/anchor, placeholder hoặc TODO.
- `answer` và `explanation` có thể dùng YAML literal block `|-` để giữ Markdown nhiều dòng; YAML sau parse phải bảo toàn nội dung dự kiến.

## W4 — Preflight, write, readback

1. Soạn toàn bộ YAML, QA kỹ thuật, parse chính xác root/keys/types/correctAnswerId và tính đa dạng. Không cố sinh số lượng cố định.
2. Nếu YAML dài >120.000 Unicode code points: `BLOCKER: QUIZ CAPACITY`, không truncate hoặc mượn cột khác. Chia tối đa 4 fragments ≤30.000 Unicode/ô không cắt giữa surrogate pair, ghép T→W không thêm separator phải đúng 100%.
3. Preflight **cả bốn ô T:W trước khi soạn và ngay trước WRITE**: `userEnteredValue`/formula, `effectiveValue`, `formattedValue`, `note`, `dataValidation`, `chipRuns`; ownership và nguồn Knowledge phải không đổi. Có bất kỳ dữ liệu/note/formula → `BLOCKER: EXISTING TARGET CONTENT`, `WRITE=NOT_RUN`, dừng toàn nhiệm vụ, không fill phần còn trống; nguồn đổi → `BLOCKER: SOURCE CHANGED`, không ghi.
4. Ghi text-only vào T:W cùng hàng cha, không đổi format. Đọc lại chính xác cả bốn ô, nối không separator, kiểm tra Unicode/whitespace/newline, parse và đối chiếu toàn văn; ô đuôi phải trống. Write lỗi không rõ → đọc trạng thái thật trước, không retry mù.
5. Báo Prime: `STATUS`, `PARENT_CELL`, `CHILD_ROWS`, `SOURCE_COVERAGE`, `OUTPUT_RANGE`, `QUESTION_COUNT`, `TOTAL_UNICODE_CHARS`, `FILLED_CELLS`, `PREFLIGHT`, `YAML_PARSE`, `TECHNICAL_QA`, `DISTRACTOR_QA`, `READBACK`, `BLOCKER`. Sau đó **SLEEPING**, không chạy Normalize/import/J3.
