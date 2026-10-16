# STEP 6 — PRIME ONLY: Điều phối Video Script Markdown trên Google Sheets

## 0. Mục tiêu và phạm vi

- **Một Menu Cấp 1 = một Video Markdown hoàn chỉnh = một hàng trong bảng Video**.
- Các Menu Cấp 2 của chương trở thành các H2/Section đúng thứ tự; mỗi Section chứa các Scene/Transition có Time, Visual, Script, Purpose.
- **Prime điền trước TOÀN BỘ tiêu đề Menu Cấp 1 vào cột "Chủ đề" (N)** theo thứ tự menu; sau đó các worker viết video độc lập vào các ô "Video Script" trên **chính hàng đã được Prime gán**.
- **Mỗi worker chỉ có MỘT lượt công việc trọn vẹn**: đọc Knowledge → soạn toàn văn Markdown → chia ô → preflight → ghi → đọc lại → QA → báo cáo. **Không có PHA A/PHA B, PREPARED, đợi cấp slot, hoặc lượt chat thứ hai để xin số hàng**.
- Tối đa 3 worker ACTIVE + SLEEPING mặc định, ưu tiên tái sử dụng. Mỗi worker sở hữu một chương, một hàng video; các worker làm song song không tranh chấp hàng.
- Không sửa Knowledge A:M, Template, các tab khác, Gradle/source/video Markdown file, cấu hình BUILD_VIDEO; không commit/push hoặc tự chạy STEP khác.

### Cổng context và phân tách PRIME / WORKER

**File `STEP_6.md` này chỉ dành cho Prime.** Prime phải đọc đầy đủ `module-generate-agent/temp/GENERAL_AGENT_RULES.md`, liệt kê và đọc **mọi file hiện có** trong `module-generate-agent/rules/`, toàn văn STEP 6 này, **toàn văn `STEP_6_WORKER.md`**, `module-generate-agent/temp/module-generate-info.txt` và quy tắc Video liên quan trong `module-generate-agent/STEP_6_VIDEO.md`. Đọc lại nếu context bị rút gọn hoặc không chắc chắn.

**Toàn bộ quy định thực thi của worker nằm riêng trong `/java-learning/module-generate-agent/temp/STEP_6_WORKER.md`.** Worker **chỉ đọc toàn văn file worker-only đó**, không đọc `STEP_6.md`, STEP khác, GENERAL, rules, TXT hay tài liệu nguồn repo khác để nhận hướng dẫn. Prime **không phải tính dòng hay gửi marker** nữa; chỉ gửi **4 dòng**: link tab, tọa độ nguồn/ô Chủ đề, vùng Video Script được ghi và đường dẫn `STEP_6_WORKER.md`. **Không thêm lời dẫn, lệnh, rules, tóm tắt, Knowledge, script mẫu, đáp án hoặc nội dung chat cũ** vào tin nhắn giao việc. Một lần giao = một chương = một Video Markdown hoàn chỉnh.

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
2. Ánh xạ tuần tự: Menu Cấp 1 thứ `i` (đếm từ 0) → `N(3+i)`; Video Markdown của chương ấy → `O(3+i):R(3+i)`. Số Scene và độ dài không làm thay đổi hàng của chương kế tiếp.
3. Preflight **toàn bộ vùng N đích** và O:R trên cùng các hàng (entered value/formula/note), gồm kiểm tra quyền sở hữu. N đã bằng đúng tên chương thì **giữ nguyên**; N trống hoàn toàn mới được ghi tiêu đề; N khác tên, có formula/note hoặc xung đột thì báo blocker, không ghi đè. O:R có script phải bảo toàn và kiểm định, không gán worker ghi mới vào hàng đã chiếm.
4. Điền **toàn bộ các N còn trống và an toàn** trong một batch hợp lý; đọc lại từng ô N, so khớp chính xác với danh sách A nguồn trước khi giao worker. Nếu có xung đột không thể bảo toàn mapping, ngừng giao các hàng bị ảnh hưởng, không lấp bằng cách chuyển chủ đề sang hàng khác.
5. Ghi sổ điều phối: `PARENT_CELL, CHILD_ROWS, VIDEO_ROW, TOPIC_CELL, SCRIPT_RANGE, WORKER, STATUS, READBACK, QA, BLOCKER`. Không phải dự đoán `ROWS_NEEDED`.

### 3. Giao MỘT lần/worker và theo dõi đến hoàn tất

- Mỗi worker được giao chính xác: URL tab; `A1,B1,A<PARENT>,B<children>,C<children>:M<children>`; `N<VIDEO_ROW>` chỉ đọc; `O<VIDEO_ROW>:R<VIDEO_ROW>` vùng ghi **duy nhất**; và đường dẫn **`STEP_6_WORKER.md`**. Không truyền nội dung ô hay các đoạn tóm tắt vào tin nhắn.
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

### 6. Mẫu tin nhắn Prime gửi worker — CHỈ bốn dòng

```text
<LINK_TAB_ĐÃ_XÁC_MINH>
A1, B1, <PARENT_CELL>, <CHILD_B_RANGE>, <CHILD_C_TO_M_RANGE>, <TOPIC_CELL_N_ROW>
<SCRIPT_RANGE_O_TO_R_CÙNG_MỘT_HÀNG>
/java-learning/module-generate-agent/temp/STEP_6_WORKER.md
```

Prime phải thay mọi placeholder bằng tọa độ/range chính xác. Nếu các hàng con không liên tiếp, truyền danh sách đầy đủ các ô tương ứng trên cùng dòng nguồn. **Không đưa nội dung từng ô, tóm tắt Knowledge, chỉ dẫn QA hoặc đoạn quy định worker vào tin giao.** Worker tự đọc toàn văn file riêng.
