# STEP 9 — PRIME ONLY: Điều phối Video Script YAML trên Google Sheets

## 0. Mục tiêu và phạm vi

- **Một Menu Cấp 1 = một Video YAML hoàn chỉnh ghi tại `X:AA` trên CHÍNH hàng chứa Menu Cấp 1 ở cột A** (không dùng bảng Video riêng).
- Các Menu Cấp 2 trở thành `sections[].title` đúng thứ tự; mỗi section chứa `items[]` gồm SCENE/TRANSITION với time, visual, script, purpose.
- **Không còn bảng "Chủ đề" ở cột N**. Menu Cấp 1 `A<PARENT_ROW>` là `title`; worker viết YAML vào `X<PARENT_ROW>:AA<PARENT_ROW>`. Difficulty `O` dành riêng cho các hàng Menu Cấp 2.
- **Mỗi worker chỉ có MỘT lượt công việc trọn vẹn**: đọc Knowledge → phân loại `NEW_FILL/FORMAT_FIX/UNCHANGED` → chỉ soạn toàn văn YAML khi tạo mới, hoặc sửa đúng format khi đã có script → preflight → ghi nếu cần → đọc lại → QA → báo cáo. **Không có PHA A/PHA B hoặc PREPARED**.
- Pool theo **Mode A/B của session (A=3 mặc định; B=N do user chỉ định, thiếu N → BLOCKER; tính cả ACTIVE + SLEEPING)**, ưu tiên tái sử dụng và spawn tuần tự mỗi lệnh một worker. Mỗi worker sở hữu một chương, một hàng video; các worker làm song song không tranh chấp hàng.
- Không sửa Knowledge A:N, Difficulty O, Template, các tab khác, Gradle/source/video YAML file, cấu hình BUILD_VIDEO; không commit/push hoặc tự chạy STEP khác.

### Cổng context và phân tách PRIME / WORKER

**File `STEP_9.md` này chỉ dành cho Prime.** Prime phải đọc đầy đủ `module-generate-agent/temp/GENERAL_AGENT_RULES.md`, liệt kê và đọc **mọi file hiện có** trong `module-generate-agent/rules/`, toàn văn STEP 9 này, **toàn văn `worker/STEP_9_WORKER.md`**, `module-generate-agent/temp/module-generate-info.txt`. `module-generate-agent/STEP_6_VIDEO.md` là tài liệu **legacy Markdown** thuộc workflow khác, không phải schema của STEP 9 này; không áp dụng định dạng Markdown của tài liệu đó. Đọc lại nếu context bị rút gọn.

**Toàn bộ quy định thực thi của worker nằm riêng trong `/java-learning/module-generate-agent/temp/worker/STEP_9_WORKER.md`.** Worker **chỉ đọc toàn văn file worker-only đó**. Prime gửi **4 dòng**: link tab, tọa độ nguồn `A<PARENT_ROW>` và bài con, vùng Video Script `X:AA` **trên cùng hàng cha**, đường dẫn worker-only. **Không đưa nội dung, script mẫu hoặc quy tắc vào tin giao.** Một lần giao = một chương = một Video YAML hoàn chỉnh.

---

## PHẦN PRIME — WORKER KHÔNG ĐỌC PHẦN NÀY

### 1. Xác nhận module, schema và nguồn

1. Lấy URL gốc và ánh xạ tên module → gid từ `module-generate-info.txt`, đối chiếu metadata tên tab/gid. Không nhầm spreadsheet ID với gid.
2. Đọc `A1,B1`, header menu và **toàn bộ vùng A/B của tab**, kể cả khi đợt thử nghiệm chỉ chọn một số chương. Mỗi hàng có A là chương; các hàng B tiếp theo thuộc chương đó đến trước chương kế. Lập thứ tự chính xác `(parent cell, title, child rows, X:AA cùng hàng cha)`. **Không cấp phát hàng Video mới, không tính lại hàng bằng thứ tự chương; phạm vi giao worker có thể chỉ là một tập con được yêu cầu.**
3. Đối chiếu Template và tab module **theo trạng thái hiện hành**, không suy ra từ phiên bản cũ. Schema mới:
   - `A19 = Menu Cấp 1`, `B19 = Menu Cấp 2`; `C19:N19 = Câu trả lời`, `O19 = Difficulty`.
   - `X18 = Video`, **`X19:AA19 = Video Script`** (bốn cột ghép thành một YAML).
   - **Ví dụ:** chương ở `A20` → script `X20:AA20`; chương kế tiếp ở `A29` → script `X29:AA29`. `B20` chứa filename do worker **STEP 5** tự điền. Mỗi hàng con `B21:B28` giữ Knowledge `C:N` và Difficulty `O`, không chứa Video.
   - **Không còn bảng Video riêng N3:R... hoặc cơ chế Prime điền cột N.** Các script cũ ở vùng khác không được tự di chuyển, xóa hoặc gán lại; nếu tab chưa chuyển schema, dừng `BLOCKER: VIDEO SCHEMA`, không tự sửa Template/tab.
4. Kiểm tra Knowledge từng bài của các chương dự kiến thực thi trong **C:N** đã có đủ và Difficulty **O trên các hàng con** đã hợp lệ theo STEP 5. Không lấy title thay nội dung.
   **Việc đặt filename Knowledge đã gộp vào STEP 5**: worker STEP 5 tự điền B cùng hàng Menu Cấp 1 và Prime đã QA tên/prefix toàn tab. **STEP 7 là Interview YAML ở P:S hàng cha**. Trong STEP 9, **B hàng cha vẫn là filename Knowledge `.md` hợp lệ và duy nhất** dùng để suy ra thư mục Video `<chapter-index>.<stem>/video-script.yml` khi importer chạy; worker không đổi B thành `.yml` và không tự đặt path. Nếu thiếu filename QA thì `BLOCKER: INCOMPLETE STEP 5 FILENAME QA`. Không đọc/ghi P:S.
5. Kiểm tra **toàn bộ X:AA trên hàng cha** trước khi giao worker, kể cả các ô trống. Không ghi đè nội dung, xóa hoặc đổi cấu trúc video cũ. Chương đã có script: hợp lệ → `UNCHANGED`, không ghi; nếu **chỉ có lỗi cú pháp/định dạng YAML/Markdown nhúng** → cho phép worker `FORMAT_FIX` tối thiểu trong X:AA với diff chứng minh không đổi title, sections, scenes, time, visual, script, purpose, URL hoặc thứ tự; không chứng minh được → `BLOCKER: EXISTING VIDEO`.

### 2. Prime ánh xạ hàng Menu Cấp 1 trực tiếp tới X:AA

1. Lấy **TẤT CẢ Menu Cấp 1 từ A20 trở đi**, không chỉ ba chương đầu, giữ **nguyên văn** mỗi tên/ô A cha; **không ghi thêm danh sách chủ đề ở cột nào**.
2. Ánh xạ theo **hàng thực tế**: nếu ô nguồn là `A<R>`, Video của chương đó nằm **chính xác tại `X<R>:AA<R>`**; ví dụ A20 → X20:AA20, A29 → X29:AA29. **Không dùng công thức `3+i`** và không dùng hàng con.
3. Preflight **đúng X:AA hàng cha** (entered value/formula, effective/formatted value, note, dataValidation/chipRuns), kiểm tra ownership. Phân biệt `NEW_FILL` (trống), `UNCHANGED` (YAML hợp lệ) và `FORMAT_FIX` (lỗi định dạng thuần túy). Dữ liệu thuộc ownership khác hoặc lỗi nội dung → BLOCKER, không dùng `O` vì dành cho Difficulty.
4. Chỉ giao worker khi nguồn đủ và vùng X:AA thuộc ownership an toàn. **Không có bước ghi tiêu đề N, không ghi vào X:AA của các hàng con**. Video đã có chỉ được sửa đúng lỗi format đã xác minh, không được sinh mới hay viết lại script. Với `FORMAT_FIX`, Prime kiểm tra snapshot/diff và bảo toàn toàn bộ YAML payload; nếu không chứng minh được, không cho write.
5. Ghi sổ điều phối: `PARENT_CELL, CHILD_ROWS, SCRIPT_RANGE_X_TO_AA_PARENT, WORKER, STATUS, READBACK, QA, BLOCKER`. Không dự đoán `ROWS_NEEDED` hay dùng `TOPIC_CELL_N_ROW`.

### 3. Giao MỘT lần/worker và theo dõi đến hoàn tất

- Mỗi worker được giao chính xác: URL tab; `A1,B1,A<PARENT>,B<PARENT>,B<children>,C<children>:N<children>`; `X<PARENT>:AA<PARENT>` là vùng ghi **duy nhất**; và đường dẫn **`worker/STEP_9_WORKER.md`**. Không truyền nội dung ô hay các đoạn tóm tắt vào tin nhắn.
- **Hàng Video là hàng cha có sẵn**, không cấp row/slot độc lập; worker tự làm đến COMPLETED trong **một phiên giao việc**.
- Chỉ một worker chịu trách nhiệm viết trên từng hàng. Nhiều worker có thể ghi **các hàng khác nhau** đồng thời. Kiểm tra nguồn/sẵn sàng của từng chương độc lập.
- Khi worker xong, Prime đọc lại YAML **ghép X→AA trên hàng cha không separator**, kiểm tra parse YAML và `title/sections/items/type/time/visual/script/purpose`, tính liên tục thời gian, Java correctness, coverage từng B và chất lượng sư phạm. Với `FORMAT_FIX`, so snapshot/diff bảo đảm **chỉ sửa cú pháp** và mọi nội dung, thời gian, lời thoại, dữ liệu cũ bất biến; không tranh thủ sửa chính tả/văn phong/nội dung. Xác nhận không chạm Knowledge/Difficulty/J3/ô ngoài vùng. `COMPLETED` đòi hỏi worker readback PASS **và** Prime QA độc lập PASS; `UNCHANGED` chỉ khi không write và QA PASS.
- Dùng worker SLEEPING cho chương kế tiếp chỉ sau khi giải phóng ownership; không spawn khi còn sleeping. Không dừng điều phối sau lượt giao đầu; theo `rules/PRIME_RULES.md` và `rules/HEARTBEAT.md`. Không ping worker đang chạy chỉ để tạo hoạt động.

### 4. Sức chứa và trường hợp ngoại lệ

Schema hiện hành có **4 ô X:AA trên mỗi hàng Menu Cấp 1**, mỗi ô tối đa **30.000 ký tự Unicode**. Một video có thể dài tùy Knowledge; Prime **không cần và không được đoán** số Scene, độ dài hoặc số ô trước khi giao. Worker tính sau khi hoàn tất YAML và tự chia theo giới hạn.

Nếu toàn văn cần **hơn 4 ô** (>120.000 ký tự khi mỗi ô dùng đủ hạn mức), đó là ngoại lệ `BLOCKER: VIDEO CAPACITY`: worker báo độ dài và số ô thật sự cần, **không ghi một phần/truncate**, không tự chạm các cột ngoài vùng Video X:AA hay đổi schema chung khi các worker khác đang làm. Prime chỉ thiết kế/mở rộng các cột "Video Script" bổ sung sau khi xác nhận phạm vi an toàn và quyền sửa schema; không coi đây là PHA A/B mặc định và không giả báo chương hoàn tất.

### 5. Hoàn tất STEP

Với mỗi chương, yêu cầu một YAML parseable hoàn chỉnh theo `project-build/skeleton/video/video-script.yml`: `title` đúng A cha; `sections[].title` đúng B con theo thứ tự; section đầu ≥1 SCENE, các section sau bắt đầu bằng TRANSITION và có ≥1 SCENE; các item đủ bốn trường; thời gian liên tục; nội dung không placeholder; readback Unicode/newline 100% và QA chương PASS.

**Sau khi TẤT CẢ chương của tab được worker hoàn tất, giải phóng ownership và Prime QA/readback PASS**, Prime **bắt buộc đồng bộ Video về đúng module**: xác minh URL/gid/tên tab/B1 và `I3` khớp module Gradle đã đăng ký; chạy task module-local `generateModuleVideoFromSheetByLocation` với `-PsheetMode=DRY_RUN` rồi `APPLY` như `GENERAL_AGENT_RULES.md §6`. Không gọi `ByFiles` hoặc task tổng hợp, không chạy sau từng worker hoặc với trial một vài chương. Kiểm tra status thực và kết quả trong `<MODULE_ROOT>/content/video/vi/menu/` (mỗi chương `<ordinal>.<stem>/video-script.yml`); `DRY_RUN` đơn thuần, `SKIPPED_*`, output thiếu hoặc lỗi cấu hình là `BLOCKER: MODULE VIDEO SYNC`. **Chỉ sau module sync PASS/UNCHANGED được xác minh** mới đọc `J2 = Current STEP`, rồi cập nhật `J3 = STEP 9` đúng tab và exact readback theo Prime Rules. Nếu thử nghiệm một số chương hoặc import lỗi, giữ nguyên J3. Worker không xuất file, chạy Gradle, sửa cấu hình hoặc commit/push.

### 5.1 — Lệnh Prime bắt buộc tại root `java-learning`

```powershell
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:generateModuleVideoFromSheetByLocation -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=DRY_RUN
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:generateModuleVideoFromSheetByLocation -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=APPLY
```

Sau APPLY kiểm tra đúng `I3` và báo `MODULE_SYNC_TASK`, `MODULE_PATH`, `TAB_GID`, `DRY_RUN`, `APPLY`, `SYNC_STATUS`, `DESTINATION`, `BLOCKER`. Với Video, importer APPLY thay thế **snapshot đầy đủ** của `content/video/vi/menu`; nếu dữ liệu chương chưa đầy đủ thì không APPLY (tránh xóa nhầm video cũ). Tiêu chí nội dung: `script` là **lời thoại tự nhiên** không chép README; `visual` mô tả hình/chuyển động/code có thể dựng; `purpose` là mục tiêu sư phạm. Khi đưa Java/code/đầu ra vào script/visual, QA kỹ thuật như README: cú pháp, phiên bản, giải thích và stdout phải đúng; cú pháp Markdown/code fence nếu xuất hiện phải cân đối nhưng không biến YAML Video thành README.

### 6. Mẫu tin nhắn Prime gửi worker — CHỈ bốn dòng

```text
<LINK_TAB_ĐÃ_XÁC_MINH>
A1, B1, <PARENT_CELL_A>, <PARENT_FILENAME_CELL_B>, <CHILD_B_RANGE>, <CHILD_C_TO_N_RANGE>
<SCRIPT_RANGE_X_TO_AA_TRÊN_CHÍNH_HÀNG_PARENT>
/java-learning/module-generate-agent/temp/worker/STEP_9_WORKER.md
```

Prime phải thay mọi placeholder bằng tọa độ/range chính xác. Nếu các hàng con không liên tiếp, truyền danh sách đầy đủ các ô tương ứng trên cùng dòng nguồn. **Không đưa nội dung từng ô, tóm tắt Knowledge, chỉ dẫn QA hoặc đoạn quy định worker vào tin giao.** Worker tự đọc toàn văn file riêng.
