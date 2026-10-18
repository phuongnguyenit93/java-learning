# STEP 7 — PRIME ONLY: Điều phối Video Script YAML trên Google Sheets

## 0. Mục tiêu và phạm vi

- **Một Menu Cấp 1 = một Video YAML hoàn chỉnh ghi tại `P:S` trên CHÍNH hàng chứa Menu Cấp 1 ở cột A** (không dùng bảng Video riêng).
- Các Menu Cấp 2 trở thành `sections[].title` đúng thứ tự; mỗi section chứa `items[]` gồm SCENE/TRANSITION với time, visual, script, purpose.
- **Không còn bảng "Chủ đề" ở cột N**. Menu Cấp 1 `A<PARENT_ROW>` là `title`; worker viết YAML vào `P<PARENT_ROW>:S<PARENT_ROW>`. Difficulty `O` dành riêng cho các hàng Menu Cấp 2.
- **Mỗi worker chỉ có MỘT lượt công việc trọn vẹn**: đọc Knowledge → soạn toàn văn YAML → chia ô → preflight → ghi → đọc lại → QA → báo cáo. **Không có PHA A/PHA B hoặc PREPARED**.
- Tối đa 3 worker ACTIVE + SLEEPING mặc định, ưu tiên tái sử dụng. Mỗi worker sở hữu một chương, một hàng video; các worker làm song song không tranh chấp hàng.
- Không sửa Knowledge A:N, Difficulty O, Template, các tab khác, Gradle/source/video YAML file, cấu hình BUILD_VIDEO; không commit/push hoặc tự chạy STEP khác.

### Cổng context và phân tách PRIME / WORKER

**File `STEP_7.md` này chỉ dành cho Prime.** Prime phải đọc đầy đủ `module-generate-agent/temp/GENERAL_AGENT_RULES.md`, liệt kê và đọc **mọi file hiện có** trong `module-generate-agent/rules/`, toàn văn STEP 7 này, **toàn văn `worker/STEP_7_WORKER.md`**, `module-generate-agent/temp/module-generate-info.txt`. `module-generate-agent/STEP_6_VIDEO.md` là tài liệu **legacy Markdown** thuộc workflow khác, không phải schema của STEP 7 này; không áp dụng định dạng Markdown của tài liệu đó. Đọc lại nếu context bị rút gọn.

**Toàn bộ quy định thực thi của worker nằm riêng trong `/java-learning/module-generate-agent/temp/worker/STEP_7_WORKER.md`.** Worker **chỉ đọc toàn văn file worker-only đó**. Prime gửi **4 dòng**: link tab, tọa độ nguồn `A<PARENT_ROW>` và bài con, vùng Video Script `P:S` **trên cùng hàng cha**, đường dẫn worker-only. **Không đưa nội dung, script mẫu hoặc quy tắc vào tin giao.** Một lần giao = một chương = một Video YAML hoàn chỉnh.

---

## PHẦN PRIME — WORKER KHÔNG ĐỌC PHẦN NÀY

### 1. Xác nhận module, schema và nguồn

1. Lấy URL gốc và ánh xạ tên module → gid từ `module-generate-info.txt`, đối chiếu metadata tên tab/gid. Không nhầm spreadsheet ID với gid.
2. Đọc `A1,B1`, header menu và **toàn bộ vùng A/B của tab**, kể cả khi đợt thử nghiệm chỉ chọn một số chương. Mỗi hàng có A là chương; các hàng B tiếp theo thuộc chương đó đến trước chương kế. Lập thứ tự chính xác `(parent cell, title, child rows, P:S cùng hàng cha)`. **Không cấp phát hàng Video mới, không tính lại hàng bằng thứ tự chương; phạm vi giao worker có thể chỉ là một tập con được yêu cầu.**
3. Đối chiếu Template và tab module **theo trạng thái hiện hành**, không suy ra từ phiên bản cũ. Schema mới:
   - `A19 = Menu Cấp 1`, `B19 = Menu Cấp 2`; `C19:N19 = Câu trả lời`, `O19 = Difficulty`.
   - `P18 = Video`, **`P19:S19 = Video Script`** (bốn cột ghép thành một YAML).
   - **Ví dụ:** chương ở `A20` → script `P20:S20`; chương kế tiếp ở `A29` → script `P29:S29`. `B20` chứa filename do worker **STEP 5** tự điền. Mỗi hàng con `B21:B28` giữ Knowledge `C:N` và Difficulty `O`, không chứa Video.
   - **Không còn bảng Video riêng N3:R... hoặc cơ chế Prime điền cột N.** Các script cũ ở vùng khác không được tự di chuyển, xóa hoặc gán lại; nếu tab chưa chuyển schema, dừng `BLOCKER: VIDEO SCHEMA`, không tự sửa Template/tab.
4. Kiểm tra Knowledge từng bài của các chương dự kiến thực thi trong **C:N** đã có đủ và Difficulty **O trên các hàng con** đã hợp lệ theo STEP 5. Không lấy title thay nội dung.
   **Việc đặt filename Knowledge đã gộp vào STEP 5**: worker STEP 5 tự điền B cùng hàng Menu Cấp 1 và Prime đã QA tên/prefix toàn tab. **STEP 6 là Interview JSON ở T:W hàng cha**. Trong STEP 7, **B hàng cha vẫn là filename Knowledge `.md` hợp lệ và duy nhất** dùng để suy ra thư mục Video `<chapter-index>.<stem>/video-script.yml` khi importer chạy; worker không đổi B thành `.yml` và không tự đặt path. Nếu thiếu filename QA thì `BLOCKER: INCOMPLETE STEP 5 FILENAME QA`. Không đọc/ghi T:W.
5. Kiểm tra **toàn bộ P:S trên hàng cha** trước khi giao worker, kể cả các ô trống. Không ghi đè, dịch chuyển, xóa hay tái bố trí video cũ; chương có script sẵn phải được đọc/đánh giá hiện trạng, chỉ đánh dấu hoàn thành nếu đủ QA, nếu chưa rõ thì `BLOCKER: EXISTING VIDEO`.

### 2. Prime ánh xạ hàng Menu Cấp 1 trực tiếp tới P:S

1. Lấy **TẤT CẢ Menu Cấp 1 từ A20 trở đi**, không chỉ ba chương đầu, giữ **nguyên văn** mỗi tên/ô A cha; **không ghi thêm danh sách chủ đề ở cột nào**.
2. Ánh xạ theo **hàng thực tế**: nếu ô nguồn là `A<R>`, Video của chương đó nằm **chính xác tại `P<R>:S<R>`**; ví dụ A20 → P20:S20, A29 → P29:S29. **Không dùng công thức `3+i`** và không dùng hàng con.
3. Preflight **đúng P:S hàng cha** (entered value/formula, effective/formatted value, note, dataValidation/chipRuns), kiểm tra ownership. Bất kỳ ô có script/sở hữu khác phải được bảo toàn; không giao worker ghi mới nếu đã chiếm. Không dùng `O` vì dành cho Difficulty.
4. Chỉ giao worker cho chương có nguồn đủ, hàng cha P:S hoàn toàn an toàn. **Không có bước ghi tiêu đề N, không ghi vào P:S của các hàng con**. Chương đã có Video phải được đánh giá độc lập hoặc BLOCKER, không ghi đè.
5. Ghi sổ điều phối: `PARENT_CELL, CHILD_ROWS, SCRIPT_RANGE_P_TO_S_PARENT, WORKER, STATUS, READBACK, QA, BLOCKER`. Không dự đoán `ROWS_NEEDED` hay dùng `TOPIC_CELL_N_ROW`.

### 3. Giao MỘT lần/worker và theo dõi đến hoàn tất

- Mỗi worker được giao chính xác: URL tab; `A1,B1,A<PARENT>,B<PARENT>,B<children>,C<children>:N<children>`; `P<PARENT>:S<PARENT>` là vùng ghi **duy nhất**; và đường dẫn **`worker/STEP_7_WORKER.md`**. Không truyền nội dung ô hay các đoạn tóm tắt vào tin nhắn.
- **Hàng Video là hàng cha có sẵn**, không cấp row/slot độc lập; worker tự làm đến COMPLETED trong **một phiên giao việc**.
- Chỉ một worker chịu trách nhiệm viết trên từng hàng. Nhiều worker có thể ghi **các hàng khác nhau** đồng thời. Kiểm tra nguồn/sẵn sàng của từng chương độc lập.
- Khi worker xong, Prime đọc lại YAML **ghép P→S trên hàng cha không separator**, kiểm tra parse YAML và `title/sections/items/type/time/visual/script/purpose`, tính liên tục thời gian, Java correctness, chính tả, coverage từng B và chất lượng sư phạm. Xác nhận không chạm Knowledge/Difficulty/J3/ô ngoài vùng. `COMPLETED` đòi hỏi worker readback PASS **và** Prime QA độc lập PASS.
- Dùng worker SLEEPING cho chương kế tiếp chỉ sau khi giải phóng ownership; không spawn khi còn sleeping. Không dừng điều phối sau lượt giao đầu; theo `rules/PRIME_RULES.md` và `rules/HEARTBEAT.md`. Không ping worker đang chạy chỉ để tạo hoạt động.

### 4. Sức chứa và trường hợp ngoại lệ

Schema hiện hành có **4 ô P:S trên mỗi hàng Menu Cấp 1**, mỗi ô tối đa **30.000 ký tự Unicode**. Một video có thể dài tùy Knowledge; Prime **không cần và không được đoán** số Scene, độ dài hoặc số ô trước khi giao. Worker tính sau khi hoàn tất YAML và tự chia theo giới hạn.

Nếu toàn văn cần **hơn 4 ô** (>120.000 ký tự khi mỗi ô dùng đủ hạn mức), đó là ngoại lệ `BLOCKER: VIDEO CAPACITY`: worker báo độ dài và số ô thật sự cần, **không ghi một phần/truncate**, không tự chạm cột T trở đi hay đổi schema chung khi các worker khác đang làm. Prime chỉ thiết kế/mở rộng các cột "Video Script" bổ sung sau khi xác nhận phạm vi an toàn và quyền sửa schema; không coi đây là PHA A/B mặc định và không giả báo chương hoàn tất.

### 5. Hoàn tất STEP

Với mỗi chương, yêu cầu một YAML parseable hoàn chỉnh theo `project-build/skeleton/video/video-script.yml`: `title` đúng A cha; `sections[].title` đúng B con theo thứ tự; section đầu ≥1 SCENE, các section sau bắt đầu bằng TRANSITION và có ≥1 SCENE; các item đủ bốn trường; thời gian liên tục; nội dung không placeholder; readback Unicode/newline 100% và QA chương PASS.

**Chỉ khi toàn bộ chương thuộc phạm vi STEP của tab đã được xác minh hoàn thành** mới đọc `J2 = Current STEP`, rồi cập nhật `J3 = STEP 7` đúng tab và readback theo Prime Rules. Nếu chỉ **thử nghiệm một số chương**, giữ nguyên J3. Không tự xuất source Markdown, chạy Gradle, sửa cấu hình hoặc commit/push.

### 6. Mẫu tin nhắn Prime gửi worker — CHỈ bốn dòng

```text
<LINK_TAB_ĐÃ_XÁC_MINH>
A1, B1, <PARENT_CELL_A>, <PARENT_FILENAME_CELL_B>, <CHILD_B_RANGE>, <CHILD_C_TO_N_RANGE>
<SCRIPT_RANGE_P_TO_S_TRÊN_CHÍNH_HÀNG_PARENT>
/java-learning/module-generate-agent/temp/worker/STEP_7_WORKER.md
```

Prime phải thay mọi placeholder bằng tọa độ/range chính xác. Nếu các hàng con không liên tiếp, truyền danh sách đầy đủ các ô tương ứng trên cùng dòng nguồn. **Không đưa nội dung từng ô, tóm tắt Knowledge, chỉ dẫn QA hoặc đoạn quy định worker vào tin giao.** Worker tự đọc toàn văn file riêng.
