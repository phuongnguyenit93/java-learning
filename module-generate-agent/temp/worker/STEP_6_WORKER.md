# STEP 6 — WORKER ONLY: Interview YAML cho MỘT chương

> **BẮT BUỘC tự đọc TOÀN VĂN file worker-only này.** Không đọc file Prime, STEP khác, GENERAL/rules/TXT, source repo hoặc lịch sử chat để thay nguồn Google Sheets. Prime chỉ gửi link tab, tọa độ nguồn, vùng P:S được ghi và đường dẫn file này.

**FILL-ONLY — KHÔNG REPLACE:** Trước khi sinh Interview YAML, kiểm tra **toàn vùng đích `P<PARENT_ROW>:S<PARENT_ROW>`**. Nếu bất kỳ ô nào đã có nội dung, báo `BLOCKER: EXISTING TARGET CONTENT`, `WRITE=NOT_RUN` rồi **DỪNG ngay toàn nhiệm vụ**; không sửa YAML, không fill mảnh còn trống, không nối bổ sung hay coi dữ liệu có sẵn là hoàn thành. Chỉ được replace theo **chỉ thị đặc biệt tường minh của người dùng** cho đúng phạm vi.

## W1 — Nguồn và quyền ghi

1. Một nhiệm vụ = một Menu Cấp 1 và mọi Menu Cấp 2 của chương đó. Chỉ đọc `A1`, `B1`, A/B hàng cha, tên B và **toàn bộ Knowledge C:N** trên từng hàng con được Prime giao; Difficulty O chỉ đọc nếu được giao. Ghép Knowledge **C→N không thêm separator**, đọc toàn văn từng bài chứ không chỉ tiêu đề.
2. **Chỉ ghi `P<PARENT_ROW>:S<PARENT_ROW>` ở hàng cha**; tối đa bốn ô × 30.000 Unicode code points/ô. Không ghi hàng con, không sửa A:O/T:AA, J3, Template, tab khác hay source/module. Không spawn worker hoặc commit/push.
3. A cha là chủ đề phỏng vấn, B cha là filename Knowledge `.md` (chỉ đọc). Nếu nguồn thiếu, hàng con lạc chương, filename/headers sai hoặc vùng ghi không an toàn, báo BLOCKER, không suy đoán.

## W2 — Nội dung và chất lượng câu trả lời

- Tạo các câu hỏi **có giá trị phỏng vấn hoặc ôn tập thực tế**: giải thích cơ chế, vận dụng, so sánh, đánh giá trade-off, debug tình huống, hiểu giới hạn và lựa chọn giải pháp. Không trivia, đánh đố hoặc ép số lượng cố định để đủ quota.
- Trả lời **đúng kỹ thuật, đủ để người học tự hiểu**, gắn với Knowledge C:N đã đọc; nêu *vì sao/khi nào*, điều kiện và ngoại lệ quan trọng; không bịa hành vi Java/framework/API. Các câu hỏi bao phủ các ý quan trọng của chương mà không lặp nhau hoặc sao chép nguyên văn bài README.
- **Format nội dung `answer` theo chuẩn trình bày README Markdown khi hữu ích**: chia đoạn rõ ràng, dùng danh sách/bảng/code fence có nhãn `java`, `bash`, `text`, ...; code phải hợp lệ với ngữ cảnh, nêu đầu vào và output chính xác nếu minh họa runnable. Mermaid chỉ khi cần, cân bằng code fence và heading; không ép mọi đáp án cùng khuôn/hệ thống heading. Câu ngắn vẫn có thể là một đoạn. Không chèn lời nhắn cho Prime, TODO hoặc placeholder.
- Markdown nằm **bên trong chuỗi YAML `answer`**, không phải một bài README khác và không phải JSON. Dùng YAML literal block `|-` cho câu trả lời nhiều dòng; thụt lề và newline phải đúng. Ví dụ ngắn dạng quoted scalar cũng hợp lệ.

## W3 — Định dạng YAML duy nhất

Soạn **MỘT tài liệu YAML hợp lệ** (không bọc Markdown fence toàn tài liệu, không thêm lời dẫn), đúng cấu trúc:

```yaml
questions:
  - question: "Khi nào nên ưu tiên composition thay cho inheritance?"
    answer: |-
      Ưu tiên **composition** khi cần thay đổi hành vi linh hoạt mà không tạo
      cây kế thừa cứng. Đánh giá theo quan hệ giữa các đối tượng và khả năng
      mở rộng/thay đổi độc lập.

      Ví dụ: đối tượng nhận dependency qua constructor thay vì kế thừa lớp
      chỉ để tái sử dụng một hành vi.
  - question: "Encapsulation bảo vệ điều gì?"
    answer: |-
      Bảo vệ **invariant** bằng cách kiểm soát nơi trạng thái được thay đổi.
```

Ví dụ trên **chỉ minh họa định dạng**, không phải nội dung cần sao chép.

- Root chỉ có `questions` là **danh sách không rỗng**. Mỗi phần tử **chỉ có `question` và `answer`**, đều là YAML string không blank. Không sinh `aiGenerated`, `reviewed`, `readmeRelated`, `apiRelated`, `difficulty` hay metadata khác. Các field này do code Normalize thêm sau khi Prime QA toàn tab.
- Không dùng JSON, JSONL, YAML nhiều document (`---` lặp lại), alias/anchor YAML, custom tag hoặc comments xen vào dữ liệu. Quote giá trị chứa dấu `:`/ký tự đặc biệt hoặc dùng `|-` cho multiline; không để parser tự chuyển câu trả lời thành map/list/boolean.
- Mỗi câu trả lời có thể chứa Markdown, Java code fence, bảng hoặc xuống dòng, **nhưng toàn bộ nội dung phải nằm trong scalar `answer`**. Sau khi YAML parse, phải còn đúng chuỗi Markdown/whitespace đã soạn.

## W4 — Soạn, preflight, ghi và exact readback

1. Soạn và QA **toàn bộ** YAML trước khi ghi. Kiểm tra YAML parser, root `questions`, từng string question/answer, Markdown và code fence trong answer, tính chính xác và không trùng câu hỏi. Không tự thêm mặc định/related.
2. Nếu YAML dài hơn **120.000 Unicode code points**, báo `BLOCKER: INTERVIEW CAPACITY`, không cắt bớt nội dung, không mượn cột X/hàng khác. Ngược lại chia thành tối đa bốn đoạn liên tục ≤30.000 code points, ghi P→Q→R→S trên **cùng hàng cha**, không cắt giữa surrogate pair. Nối không separator phải tái lập chính xác 100% YAML gốc.
3. Preflight **cả P:S trước khi soạn** và đọc lại ngay trước bất kỳ write: `userEnteredValue/formula`, `effectiveValue`, `formattedValue`, `note`, `dataValidation`, `chipRuns`. Cả bốn ô phải trống/an toàn, không ai khác sở hữu; A/B cha và mọi nguồn giao vẫn nguyên. Nếu có nội dung tại bất kỳ ô nào: `BLOCKER: EXISTING TARGET CONTENT`, `WRITE=NOT_RUN`, dừng ngay, không fill các ô thiếu; nguồn thay đổi → `BLOCKER: SOURCE CHANGED`, không ghi.
4. Chỉ ghi mảnh YAML vào P:S bằng text value, không đổi format/notes/validation. Lỗi hoặc kết quả write không rõ → đọc trạng thái thật trước, không retry mù.
5. Exact readback tất cả bốn ô, kiểm tra ô đuôi rỗng, ghép P→S không separator và so khớp **100% Unicode/whitespace/newline**; parse YAML đã đọc lại, QA cặp question/answer và Markdown code fence. Chỉ `COMPLETED` nếu mọi phép kiểm tra PASS.

## W5 — Báo Prime và SLEEPING

Báo một lần: `STATUS=COMPLETED/BLOCKER/FAILED`, tab/gid, `PARENT_CELL`, `CHILD_ROWS`, `READ_COVERAGE`, `OUTPUT_RANGE_P_TO_S`, `QUESTION_COUNT`, `TOTAL_UNICODE_CHARS`, `FILLED_CELLS`, `PREFLIGHT`, `YAML_PARSE=PASS/FAIL`, `ANSWER_MARKDOWN_QA=PASS/FAIL`, `READBACK=PASS/FAIL`, `CONTENT_QA=PASS/FAIL`, `PRESERVATION`, `BLOCKERS`. Sau đó chuyển **SLEEPING**; không tự chạy Normalize, import, STEP 8 hay cập nhật J3.
