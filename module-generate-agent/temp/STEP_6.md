# STEP 6 — Video Script Markdown trên Google Sheets (một lượt/worker)

## 0. Mục tiêu và phạm vi

- **Một Menu Cấp 1 = một Video Markdown hoàn chỉnh = một hàng trong bảng Video**.
- Các Menu Cấp 2 của chương trở thành các H2/Section đúng thứ tự; mỗi Section chứa các Scene/Transition có Time, Visual, Script, Purpose.
- **Prime điền trước TOÀN BỘ tiêu đề Menu Cấp 1 vào cột "Chủ đề" (N)** theo thứ tự menu; sau đó các worker viết video độc lập vào các ô "Video Script" trên **chính hàng đã được Prime gán**.
- **Mỗi worker chỉ có MỘT lượt công việc trọn vẹn**: đọc Knowledge → soạn toàn văn Markdown → chia ô → preflight → ghi → đọc lại → QA → báo cáo. **Không có PHA A/PHA B, PREPARED, đợi cấp slot, hoặc lượt chat thứ hai để xin số hàng**.
- Tối đa 3 worker ACTIVE + SLEEPING mặc định, ưu tiên tái sử dụng. Mỗi worker sở hữu một chương, một hàng video; các worker làm song song không tranh chấp hàng.
- Không sửa Knowledge A:M, Template, các tab khác, Gradle/source/video Markdown file, cấu hình BUILD_VIDEO; không commit/push hoặc tự chạy STEP khác.

### Cổng context và vùng worker-only

Prime phải đọc đầy đủ `module-generate-agent/temp/GENERAL_AGENT_RULES.md`, liệt kê và đọc **mọi file hiện có** trong `module-generate-agent/rules/`, toàn văn STEP 6 này, `module-generate-agent/temp/module-generate-info.txt` và quy tắc Video liên quan trong `module-generate-agent/STEP_6_VIDEO.md`. Đọc lại nếu context bị rút gọn hoặc không chắc chắn.

Worker **chỉ đọc các dòng nằm giữa** `BEGIN_WORKER_ONLY_STEP_6` và `END_WORKER_ONLY_STEP_6` bên dưới, **không đọc cả STEP_6.md**. Prime tính lại khoảng dòng mỗi lần giao (không hardcode số dòng); chỉ gửi **link tab + tọa độ nguồn + đúng ô chủ đề + vùng ghi riêng + đường dẫn/đoạn dòng worker-only**. Không gửi Knowledge, kịch bản mẫu từ Prime, lời giải hoặc nội dung chat cũ. Phần worker-only phải đủ để tự làm một lượt.

---

## PHẦN PRIME — WORKER KHÔNG ĐỌC PHẦN NÀY

### 1. Xác nhận module, schema và nguồn

1. Lấy URL gốc và ánh xạ tên module → gid từ `module-generate-info.txt`, đối chiếu metadata tên tab/gid. Không nhầm spreadsheet ID với gid.
2. Đọc `A1,B1`, header menu và **toàn bộ vùng A/B của tab**, kể cả khi đợt thử nghiệm chỉ chọn một số chương. Mỗi hàng có A là chương; các hàng B tiếp theo thuộc chương đó đến trước chương kế. Lập thứ tự chính xác `(parent cell, title, child rows, output row)`. **Danh sách chủ đề N luôn chứa toàn bộ Menu Cấp 1; phạm vi giao worker có thể chỉ là một tập con được yêu cầu.**
3. Đối chiếu Template và tab module **theo trạng thái hiện hành**, không suy ra từ phiên bản cũ. Schema Video mới xác nhận khi soạn file:
   - `N1 = Video`.
   - `N2 = Chủ đề`.
   - `O2:R2 = Video Script` (bốn cột hiện có để chia **một** Markdown).
   - Dữ liệu bắt đầu hàng 3: `N3` là chương thứ nhất, `N4` chương thứ hai, v.v.; nội dung tương ứng `O3:R3`, `O4:R4`...
   - **Không còn bảng mỗi Scene một hàng, không còn cột T = Section, không cấp N:T theo số Scene.**
   - Nếu schema/tab bị sửa và khác những điều trên, dừng với `BLOCKER: VIDEO SCHEMA`; không tự thay Template.
4. Kiểm tra Knowledge từng bài của các chương dự kiến thực thi trong C:M đã có đủ, điều kiện STEP 5 liên quan đã đạt. Không lấy title thay nội dung.
5. Kiểm tra trạng thái Video hiện có trước khi điền chủ đề hoặc giao worker. Không ghi đè, dịch chuyển, xóa hay tái bố trí các video cũ; chương có script sẵn phải được đọc/đánh giá hiện trạng, chỉ đánh dấu hoàn thành nếu đủ QA, nếu chưa rõ thì `BLOCKER: EXISTING VIDEO`.

### 2. Prime điền toàn bộ "Chủ đề" trước khi giao worker

1. Lấy **TẤT CẢ Menu Cấp 1 của tab**, không chỉ ba chương đầu hoặc các chương đang giao worker, theo thứ tự xuất hiện ở cột A. Mỗi tên giữ **nguyên văn**, không tự sửa cách viết/đổi nội dung. Thực hiện bước này trước khi giao bất kỳ worker nào.
2. Ánh xạ tuần tự: Menu Cấp 1 thứ `i` (đếm từ 0) → `N(3+i)`; Video Markdown của chương ấy → `O(3+i):S(3+i)`. Số Scene và độ dài không làm thay đổi hàng của chương kế tiếp.
3. Preflight **toàn bộ vùng N đích** và O:R trên cùng các hàng (entered value/formula/note), gồm kiểm tra quyền sở hữu. N đã bằng đúng tên chương thì **giữ nguyên**; N trống hoàn toàn mới được ghi tiêu đề; N khác tên, có formula/note hoặc xung đột thì báo blocker, không ghi đè. O:R có script phải bảo toàn và kiểm định, không gán worker ghi mới vào hàng đã chiếm.
4. Điền **toàn bộ các N còn trống và an toàn** trong một batch hợp lý; đọc lại từng ô N, so khớp chính xác với danh sách A nguồn trước khi giao worker. Nếu có xung đột không thể bảo toàn mapping, ngừng giao các hàng bị ảnh hưởng, không lấp bằng cách chuyển chủ đề sang hàng khác.
5. Ghi sổ điều phối: `PARENT_CELL, CHILD_ROWS, VIDEO_ROW, TOPIC_CELL, SCRIPT_RANGE, WORKER, STATUS, READBACK, QA, BLOCKER`. Không phải dự đoán `ROWS_NEEDED`.

### 3. Giao MỘT lần/worker và theo dõi đến hoàn tất

- Mỗi worker được giao chính xác: URL tab; `A1,B1,A<PARENT>,B<children>,C<children>:M<children>`; `N<VIDEO_ROW>` chỉ đọc; `O<VIDEO_ROW>:R<VIDEO_ROW>` vùng ghi **duy nhất**; đường dẫn STEP 6 và khoảng dòng worker-only.
- Prime **đã phân bổ hàng trước**: không cần PREPARE, chờ trả về số Scene, rồi gửi chat WRITE; worker tự làm đến COMPLETED trong **một phiên giao việc**.
- Chỉ một worker chịu trách nhiệm viết trên từng hàng. Nhiều worker có thể ghi **các hàng khác nhau** đồng thời. Kiểm tra nguồn/sẵn sàng của từng chương độc lập.
- Khi worker xong, Prime đọc lại Markdown **ghép O→R không separator**, kiểm tra H1/H2/Scene/Transition/Time/Visual/Script/Purpose, Java correctness, chính tả, coverage từng B và chất lượng sư phạm. Xác nhận không chạm Knowledge/J3/ô ngoài vùng. `COMPLETED` đòi hỏi worker readback PASS **và** Prime QA độc lập PASS.
- Dùng worker SLEEPING cho chương kế tiếp chỉ sau khi giải phóng ownership; không spawn khi còn sleeping. Không dừng điều phối sau lượt giao đầu; theo `rules/PRIME_RULES.md` và `rules/HEARTBEAT.md`. Không ping worker đang chạy chỉ để tạo hoạt động.

### 4. Sức chứa và trường hợp ngoại lệ

Schema hiện hành có **4 ô O:R/hàng**, mỗi ô tối đa **30.000 ký tự Unicode**. Một video có thể dài tùy Knowledge; Prime **không cần và không được đoán** số Scene, độ dài hoặc số ô trước khi giao. Worker tính sau khi hoàn tất Markdown và tự chia theo giới hạn.

Nếu toàn văn cần **hơn 4 ô** (>120.000 ký tự khi mỗi ô dùng đủ hạn mức), đó là ngoại lệ `BLOCKER: VIDEO CAPACITY`: worker báo độ dài và số ô thật sự cần, **không ghi một phần/truncate**, không tự chạm cột S trở đi hay đổi schema chung khi các worker khác đang làm. Prime chỉ thiết kế/mở rộng các cột "Video Script" bổ sung sau khi xác nhận phạm vi an toàn và quyền sửa schema; không coi đây là PHA A/B mặc định và không giả báo chương hoàn tất. Nếu được mở rộng, phải xác minh header/range mới và giao một nhiệm vụ độc lập an toàn để hoàn tất **một lần** theo contract hiện hành.

### 5. Hoàn tất STEP

Với mỗi chương, yêu cầu một Markdown hoàn chỉnh, tất cả H2 đúng thứ tự B; H2 đầu ≥1 Scene; mỗi H2 sau ≥1 Transition + ≥1 Scene; các block đủ bốn trường; thời gian liên tục; nội dung không placeholder; readback Unicode/newline 100% và QA chương PASS.

**Chỉ khi toàn bộ chương thuộc phạm vi STEP của tab đã được xác minh hoàn thành** mới đọc `J2 = Current STEP`, rồi cập nhật `J3 = STEP 6` đúng tab và readback theo Prime Rules. Nếu chỉ **thử nghiệm một số chương**, giữ nguyên J3. Không tự xuất source Markdown, chạy Gradle, sửa cấu hình hoặc commit/push.

### 6. Mẫu thông điệp DUY NHẤT cho worker

> STEP 6 — MỘT nhiệm vụ hoàn chỉnh, MỘT Video Markdown. Tab: `<LINK_TAB>`. Chỉ đọc nguồn `A1,B1,<PARENT_CELL>,<CHILD_B_RANGE>,<CHILD_C_TO_M_RANGE>`. Chủ đề đã được Prime điền: `<TOPIC_CELL>` (chỉ đọc). Chỉ được ghi `<SCRIPT_RANGE_O_TO_R_ON_ONE_ROW>`. **Bắt buộc đọc CHỈ dòng `<WORKER_START>–<WORKER_END>` của `/java-learning/module-generate-agent/temp/STEP_6.md`; không đọc cả file.** Tự đọc toàn văn các Knowledge, viết một Video Markdown theo skeleton, chia ≤30.000 ký tự/ô, preflight toàn bộ ô đích, ghi một lần, exact readback, Chapter QA, báo COMPLETED/BLOCKER/FAILED rồi SLEEPING. Không có lượt PREPARED/WRITE, không tự chọn hàng/cột, không ghi đè, không sửa file hay nguồn, không spawn/commit.

---

<!-- BEGIN_WORKER_ONLY_STEP_6 -->
## VÙNG RIÊNG CHO WORKER — CHỈ ĐỌC GIỮA HAI MARKER

**Bạn được giao một chương, một hàng Video Script, MỘT lượt công việc trọn vẹn.** Không có PHA A/PHA B. Chỉ đọc vùng dòng worker-only này trong STEP_6.md; không đọc phần Prime, STEP khác, rules/MD/TXT khác hoặc dùng lịch sử chat làm nội dung. Đường dẫn Sheet và tọa độ ô đã được Prime cung cấp.

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
<!-- END_WORKER_ONLY_STEP_6 -->
