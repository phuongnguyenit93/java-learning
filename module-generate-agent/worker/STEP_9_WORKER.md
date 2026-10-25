# STEP 9 — WORKER ONLY: Interview YAML cho MỘT chương

> **BẮT BUỘC tự đọc TOÀN VĂN file worker-only này.** Không đọc file Prime, STEP khác, GENERAL/rules/TXT, source repo hoặc lịch sử chat để thay nguồn Google Sheets. Prime chỉ gửi link tab, tọa độ nguồn, vùng P:S được ghi và đường dẫn file này.

**FILL-ONLY VỀ NỘI DUNG — CHO PHÉP FORMAT_FIX:** Preflight **cả `P<PARENT_ROW>:S<PARENT_ROW>`**. Vùng trống → sinh Interview YAML mới. Vùng đã có YAML → **tuyệt đối không sửa/thêm/bớt câu hỏi, câu trả lời, Markdown có ý nghĩa, thứ tự hoặc metadata**, trừ chỉ thị đặc biệt tường minh của người dùng. Chỉ được sửa tối thiểu lỗi cú pháp/định dạng YAML hoặc Markdown nhúng (indent, quote, block scalar, code fence...) mà vẫn giữ đúng giá trị nội dung. YAML hợp lệ → `UNCHANGED`; lỗi định dạng không thể sửa mà chứng minh giữ nguyên nội dung → blocker, không ghi.

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

- Root chỉ có `questions` là **danh sách không rỗng**. Mỗi phần tử **chỉ có `question` và `answer`**, đều là YAML string không blank. Không sinh `readmeRelated`, `apiRelated`, `difficulty` hay metadata khác. Các field này do code Normalize thêm sau khi Prime QA toàn tab.
- Không dùng JSON, JSONL, YAML nhiều document (`---` lặp lại), alias/anchor YAML, custom tag hoặc comments xen vào dữ liệu. Quote giá trị chứa dấu `:`/ký tự đặc biệt hoặc dùng `|-` cho multiline; không để parser tự chuyển câu trả lời thành map/list/boolean.
- Mỗi câu trả lời có thể chứa Markdown, Java code fence, bảng hoặc xuống dòng, **nhưng toàn bộ nội dung phải nằm trong scalar `answer`**. Sau khi YAML parse, phải còn đúng chuỗi Markdown/whitespace đã soạn.

## W4 — Soạn, preflight, ghi và exact readback

1. **Chỉ `NEW_FILL` mới soạn YAML mới toàn bộ**; với YAML có sẵn, kiểm tra `UNCHANGED/FORMAT_FIX`, không sinh lại câu hỏi/đáp án. QA YAML parser, root `questions`, question/answer, Markdown và code fence; không tự thêm mặc định/related.
2. Nếu YAML dài hơn **120.000 Unicode code points**, báo `BLOCKER: INTERVIEW CAPACITY`, không cắt bớt nội dung, không mượn cột X/hàng khác. Ngược lại chia thành tối đa bốn đoạn liên tục ≤30.000 code points, ghi P→Q→R→S trên **cùng hàng cha**, không cắt giữa surrogate pair. Nối không separator phải tái lập chính xác 100% YAML gốc.
3. Preflight **cả P:S trước khi soạn và ngay trước WRITE**: `userEnteredValue/formula`, `effectiveValue`, `formattedValue`, `note`, `dataValidation`, `chipRuns`, ownership và A/B/Knowledge nguồn. **Trống hết** → `NEW_FILL`. **Có YAML sẵn** → đọc ghép P→S, giữ snapshot, phân loại `UNCHANGED` nếu đúng format/schema hoặc `FORMAT_FIX` chỉ khi lỗi định dạng thật sự. Không sửa/chêm câu hỏi còn thiếu, không tự khởi tạo lại 2-field YAML từ YAML đã Normalize. Công thức/chip, quyền sở hữu xung đột hoặc nguồn thay đổi → BLOCKER.
4. `FORMAT_FIX` chỉ sửa ký tự cấu trúc YAML/Markdown nhúng (kể cả ranh giới fragment khi thật sự gây lỗi), **giữ nguyên toàn bộ question/answer, số lượng/thứ tự item và mọi metadata hiện có**. YAML parse được trước sửa → so sánh object/giá trị trước–sau; không parse được → đối chiếu tường minh từng scalar/đoạn nguyên văn với snapshot. Nếu không chứng minh được → `BLOCKER: FORMAT_FIX_NOT_PROVABLY_CONTENT_PRESERVING`, `WRITE=NOT_RUN`. Chỉ ghi text value đúng P:S sau preflight lại, không đổi format/notes/validation; không retry mù.
5. Exact readback cả bốn ô P:S, kiểm tra fragment/tail, ghép không separator và so khớp **100% Unicode/whitespace/newline với bản cuối đã QA**; YAML parser, nội dung câu hỏi/đáp án, Markdown code fence và diff bảo toàn nội dung PASS. `NEW_FILL/FORMAT_FIX` → `COMPLETED`; tài liệu đúng sẵn, không write → `UNCHANGED`; không đủ bằng chứng → BLOCKER.

## W5 — Báo Prime và SLEEPING

Báo một lần: `STATUS=COMPLETED/UNCHANGED/BLOCKER/FAILED`, `MODE=NEW_FILL/FORMAT_FIX/UNCHANGED`, tab/gid, `PARENT_CELL`, `CHILD_ROWS`, `READ_COVERAGE`, `OUTPUT_RANGE_P_TO_S`, `QUESTION_COUNT`, `TOTAL_UNICODE_CHARS`, `FILLED_CELLS`, `PREFLIGHT`, `YAML_PARSE=PASS/FAIL`, `ANSWER_MARKDOWN_QA=PASS/FAIL`, `READBACK=PASS/FAIL`, `CONTENT_QA=PASS/FAIL`, `FORMAT_DIFF`, `CONTENT_PRESERVED=PASS/FAIL`, `PRESERVATION`, `BLOCKERS`. Sau đó chuyển **SLEEPING**; không tự chạy Normalize, import, STEP 10 hay cập nhật J3.
