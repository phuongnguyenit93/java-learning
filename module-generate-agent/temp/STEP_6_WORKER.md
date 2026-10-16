# STEP 6 — WORKER ONLY: Một chương, một Video Markdown

> Chỉ dành cho worker. Prime điều phối trong STEP_6.md; bạn chỉ đọc TOÀN VĂN STEP_6_WORKER.md này, không đọc STEP_6.md, file rules/MD/TXT khác hoặc lịch sử chat. Prime chỉ chuyển link tab, tọa độ nguồn, ô chủ đề và vùng Video Script, cùng đường dẫn file này. Không có giao việc hai pha.

## PHẦN RIÊNG DÀNH CHO WORKER

**Bạn được giao một chương, một hàng Video Script, MỘT lượt công việc trọn vẹn.** Không có PHA A/PHA B. Chỉ đọc toàn văn file STEP_6_WORKER.md; không đọc phần Prime, STEP khác, rules/MD/TXT khác hoặc dùng lịch sử chat làm nội dung. Đường dẫn Sheet và tọa độ ô đã được Prime cung cấp.

### W1 — Nguồn và quyền sở hữu

1. Xác minh URL tab/gid Prime đưa. Đọc trực tiếp **chỉ** `A1,B1,A<PARENT>`, các `B<child>` của chương, toàn bộ `C:M` của chính các hàng con. Mỗi bài có thể trải qua nhiều ô: ghép nguyên văn C→M **không tự thêm separator**; đọc **toàn bộ** nội dung, không chỉ đọc đầu/cuối hoặc tóm tắt.
2. `A<PARENT>` là H1 và tên chủ đề; mỗi B con là một H2/Section nguyên văn, đúng thứ tự. Đọc `N<video_row>` để xác minh chủ đề đã được Prime điền, **không sửa N**. Không đọc nội dung các chương/bài khác.
3. Bạn chỉ được ghi **các ô O:R của đúng một hàng Prime đã giao**. Không chọn hàng từ số Scene, không dùng hàng mới, không ghi cột N/S, không sửa A:M, header, J3, Template, tab khác hoặc repo.
4. Nếu Knowledge thiếu/bị cắt, tên N không khớp A, header/schema khác, hoặc có script không rõ quyền sở hữu: báo `BLOCKER` trước khi ghi, không tự suy đoán.

### W2 — Markdown Video phải tạo

Tạo **một chuỗi Markdown hoàn chỉnh** (không phải một Scene mỗi ô, không phải text rời) theo skeleton video của repo:

```markdown
---
video:
  url: ""
---

# <Menu Cấp 1 nguyên văn>

## <Menu Cấp 2 đầu tiên nguyên văn>

<!-- VIDEO_SECTION -->

### Scene 1 — <Tên Scene tùy chọn>

**Time:** `00:00–00:45`

**Visual:**

Mô tả cụ thể slide, code/diagram, terminal, output hoặc thao tác cần quay.

**Script:**

Lời thoại có thể đọc thành tiếng, mạch lạc, tự nhiên, dùng ví dụ phù hợp.

**Purpose:**

Điểm học tập hoặc bằng chứng mà Scene cung cấp.

## <Menu Cấp 2 kế tiếp nguyên văn>

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:45–00:58`

**Visual:**

Hình chuyển, đối chiếu hoặc thao tác nối từ phần trước.

**Script:**

Câu cầu nối thể hiện quan hệ nhân quả giữa hai phần.

**Purpose:**

Vì sao cần chuyển ý tại đây.

### Scene 1 — <Tên Scene tùy chọn>

**Time:** `00:58–01:45`

**Visual:**

Hình cụ thể cần dựng.

**Script:**

Lời thoại tiếp diễn.

**Purpose:**

Mục tiêu học tập của Scene.
```

- **H1 duy nhất** đúng tên Menu Cấp 1; **mỗi B con = đúng một H2** cùng tên và thứ tự. Mỗi H2 có thể đặt `<!-- VIDEO_SECTION -->` giống skeleton canonical.
- H2 đầu có **ít nhất 1 Scene**; từ H2 thứ hai có **ít nhất 1 Transition rồi 1 Scene**. Thêm số Scene/Transition hợp lý theo độ phức tạp, không ép số lượng/thời lượng/số từ. Đánh số Scene rõ ràng trong từng section.
- Mỗi `### Scene N — ...` hoặc `### Transition` có **đủ bốn trường** `**Time:**`, `**Visual:**`, `**Script:**`, `**Purpose:**`, theo đúng thứ tự. Thời gian dạng `MM:SS–MM:SS`, bắt đầu `00:00`, liền mạch, tăng liên tục qua mọi section và phù hợp lời thoại/thao tác.
- Frontmatter `video.url` để rỗng khi chưa có URL thật; **không bịa URL**. Không sao chép nguyên văn comment hướng dẫn/skeleton với placeholder thành nội dung xuất bản; Markdown đầu ra phải là script thực tế hoàn chỉnh.
- **Script** là lời người trình bày nói được, không đọc chép README. **Visual** mô tả thao tác/hình cụ thể, vùng code, output, sequence, console, diagram khi có ý nghĩa. **Purpose** là dụng ý sư phạm, không lặp lời thoại. Transition phải liên kết phần trước với sau; kết thúc video có takeaway hữu ích.
- Dùng đúng kiến thức Java và bằng chứng thực trong Knowledge, không bịa output/runtime/API. Không tự thêm chương, H2 ngoài Menu hoặc sửa Knowledge để hợp script. Nếu có knowledge gap ảnh hưởng tính đúng, báo Prime.
- Giữ fenced code block và Markdown hợp lệ, không để thiếu closing fence, trường rỗng, `TODO` hoặc đoạn placeholder. Không xuất Video Markdown vào repository; chỉ ghi nội dung vào ô Sheets.

### W3 — Một lượt: soạn → kiểm tra → ghi → đọc lại

1. Đọc đủ mọi bài, tự soạn **toàn bộ chuỗi Markdown** gồm frontmatter/H1 và tất cả H2/Scene/Transition trước khi ghi.
2. Tự QA: đúng B coverage/thứ tự, đúng Time, câu nói tự nhiên, ví dụ Java chính xác, Visual khả thi, Purpose cụ thể, không lặp hoặc thiếu phần. Không báo `PREPARED` và không chờ Prime phê duyệt số cảnh.
3. Đếm ký tự Unicode của toàn chuỗi; chia **các đoạn liên tiếp không thêm/xóa/thay ký tự**, mỗi đoạn **≤30.000 ký tự Unicode**. Ghi theo thứ tự **O → P → Q → R** của **cùng một hàng**; các ô còn thừa giữ trống. Có thể cắt gần ranh giới đoạn khi phù hợp nhưng **nối lại không thêm dấu phân cách** phải tái lập 100% bản gốc, kể cả newline/Unicode. Không tách đôi Unicode scalar/surrogate pair.
4. Nếu cần **hơn 4 ô O:R**, báo `BLOCKER: VIDEO CAPACITY`, tổng ký tự/số ô cần; **không ghi một phần**, không tự sang S/hàng khác, không mở rộng grid. Trường hợp này chỉ Prime được xem xét thay đổi schema.
5. Trước **mọi write**, đọc lại header `N2:R2`, `N<row>` và **toàn bộ ô O:R đích** ở cùng hàng; mọi ô đích phải không có entered value/formula/note. Nếu bị chiếm, báo `BLOCKER: TARGET OCCUPIED`, không ghi đè hoặc ghi một phần. Preflight cả những ô dự kiến để trống.
6. Ghi **chỉ** các mảnh vào O:R cùng hàng; không thay đổi format/notes/validation hay vùng khác. Nếu thao tác write trả lỗi/unknown, **đọc trạng thái thực** trước khi quyết định; không tự retry ghi đè.
7. Đọc lại **từng ô O:R**, nối đúng O→R **không separator** rồi so khớp **exact 100% Unicode, whitespace, newline** với chuỗi Markdown gốc; xác nhận không có mảnh thừa. Sau readback, QA lại toàn bộ chương. Chỉ `COMPLETED` nếu cả READBACK và CHAPTER_QA đều PASS.
8. Nếu partial write, mismatch, blocker hay nguồn bị thay đổi, báo đúng ô, nguyên nhân và hiện trạng, không tự sửa bừa hoặc ghi đè dữ liệu lạ.

### W4 — Báo cáo và SLEEPING

Gửi Prime một báo cáo duy nhất: `STATUS=COMPLETED/BLOCKER/FAILED`, tab/gid, `PARENT_CELL`, `CHILD_ROWS`, `VIDEO_ROW`, `TOPIC_CELL`, `SCRIPT_RANGE`, `READ_COVERAGE`, `SECTIONS`, `SCENES`, `TRANSITIONS`, `TOTAL_UNICODE_CHARS`, `FILLED_CELLS`, `READBACK=PASS/FAIL`, `CHAPTER_QA=PASS/FAIL`, `PRESERVATION`, `BLOCKERS`.

**Một nhiệm vụ, một lần giao, làm trọn tới cuối.** Không tự nhận chương khác, không spawn, không sửa file/source/commit, không cập nhật J3. Báo xong rồi chuyển SLEEPING; Prime tự QA độc lập.
