# STEP 5 — PRIME ONLY: Module Setup, Chapter Review, Difficulty, Filename & Package Skeleton



> **CURRENT Summary:** Worker writes C-parent base ID and authored-only AC parent. AC roadmap items contain only description/transition. Prime fills A-child IDs; Summary importer materializes full module section.yml using A/B child rows. EN export reverses this projection. AC-child Markdown remains unchanged.

**CURRENT STEP 5 ID/SUMMARY CONTRACT (overrides older wording below):**
Worker fills B-parent filename and C-parent base ID (OOPBasic.md -> oop-basic), together with
all existing chapter review, Difficulty, isImportant, AC and skeleton duties. Worker never
writes A child. After all workers and filename/base-ID QA, Prime runs code-only task
fillModuleLessonIdsFromSheetByLocation DRY_RUN -> APPLY, with exact readback of A children.
Already assigned IDs are preserved; new IDs use base + an available three-digit suffix.
Knowledge importer reads A child as canonical anchor, not basename + current index.
AC parent is authored-only YAML: each roadmap entry has only description and transition;
nonfinal transition has only description and the last is null. No id/order/title/to in AC.
Summary importer materializes full module section.yml from AC parent + A/B children by
row index; EN export reverses that projection. Old full-schema AC YAML must be explicitly
migrated, not silently overwritten. No ID import or progress advancement before QA.

## Mục tiêu

Sau STEP 4, **mỗi Menu Cấp 2 đã có Knowledge Markdown ở `C:N`**. Ngay đầu STEP 5, Prime chuẩn bị module `SERVLET`, bật Swagger, sync Gradle và **provision cột `AC` an toàn** trước khi giao worker. **Mỗi worker sở hữu toàn bộ một Menu Cấp 1**, lần lượt đọc/review/refactor Knowledge, gán Difficulty `O` và isImportant `AB`, tự ghi filename `B` cha, **tạo định hướng học tập theo barem 0/1/2/3 ở `AC` từng hàng con dựa trên bản Knowledge đã refactor**, viết mạch liên kết và bảng đối chiếu toàn chương tại `AC` cha, rồi tạo/QA package skeleton Java/test. STEP 6 Interview + Case Study, STEP 10 Quiz; Video tùy chọn sau STEP_FINAL giữ nguyên.

**Mỗi sub-worker nhận trực tiếp và duy nhất một Menu Cấp 1 (mọi mục cấp 2 của chương đó)**, với **hai vùng ghi độc quyền cùng lúc**: (1) các ô Sheet thuộc chương, bao gồm **AC cha và AC từng bài**, AB isImportant; (2) **hai đường dẫn package skeleton Java main/test của chính chương**. Các chương khác nhau có thể làm song song khi package không giao nhau, nhưng **không chia một chương cho nhiều worker, không có hai writer cùng Sheet/package**.

**Hạn mức pool theo Mode A/B của session (A=3 mặc định; B=N do user chỉ định, thiếu N → BLOCKER), tính cả ACTIVE và SLEEPING**. Tuân thủ `GENERAL_AGENT_RULES.md` và `PRIME_RULES.md §0`; tái sử dụng worker SLEEPING trước, không spawn khi còn worker SLEEPING; khi cần thì spawn lần lượt từng worker.

### Phân tách PRIME / WORKER (giống STEP 4)

- **File `STEP_5.md` này chỉ dành cho Prime; worker không đọc.**
- **Toàn bộ hướng dẫn thực thi dành cho worker nằm trong `/java-learning/module-generate-agent/worker/STEP_5_WORKER.md`.** Worker phải **tự đọc toàn văn file worker-only**, kể cả sau context compaction; Prime tuyệt đối không rút gọn quy trình review/refactor thành tin nhắn ngắn thay cho tài liệu. Worker không đọc STEP Prime, STEP khác, GENERAL, rules hoặc TXT.
- Prime phải đọc `GENERAL_AGENT_RULES.md`, **liệt kê và đọc tất cả file hiện có trong `module-generate-agent/rules/`**, `module-generate-info.txt`, toàn bộ cả `STEP_5.md` và `worker/STEP_5_WORKER.md` trước khi giao; đọc lại khi mất context.
- **Tin nhắn Prime giao worker chỉ gồm 4 dòng định vị:** link tab, tọa độ ô nguồn, **vùng ghi Sheet của đúng chương + hai đường dẫn package main/test được cấp quyền**, đường dẫn file worker-only. Không thêm lời dẫn, rules, giải thích, nội dung README, bản refactor mẫu hoặc tóm tắt. **Một lần giao = một chương = một lượt thực hiện trọn vẹn**.

## 1. Phạm vi và cấu trúc Google Sheets

| Thành phần | Ô/vùng | Quy định |
| --- | --- | --- |
| Nhãn và tên module | `A1`, `B1` | Ngữ cảnh, **chỉ đọc**. |
| Menu Cấp 1 của chương | `A<PARENT_ROW>` | Prime gửi **chính xác một ô cha**, chỉ đọc. |
| Filename của chương | `B<PARENT_ROW>` | **Worker cùng chương tự chọn và điền vào ô B cha nếu còn trống**; B cha có filename hợp lệ thì giữ nguyên. Prime KHÔNG viết filename. |
| Base ID của chương | `C<PARENT_ROW>` | **Worker điền base ID kebab-case suy ra từ filename B cha**; ví dụ `OOPBasic.md` → `oop-basic`. Không đụng C:N hàng con ngoài quyền review Knowledge. |
| ID của từng bài | `A<CHILD_ROW>` | **Prime code-only fill sau QA toàn tab**; worker không ghi. ID tồn tại được giữ nguyên. |
| Menu Cấp 2 của chương | `B<START_ROW>:B<END_ROW>` | Prime gửi **đúng khoảng hàng con**, không giao worker tự quét/tìm. |
| README của từng mục cấp 2 | `C<R>:N<R>` với mỗi `R` là hàng con được giao | Đọc, review, refactor và **chỉ ghi lại tại đúng hàng R**. |
| Difficulty của từng mục cấp 2 | `O<R>` trên hàng con R | Chọn đúng một giá trị `BASIC` / `INTERMEDIATE` / `ADVANCED`; không ghi O hàng cha. |
| Kiến thức quan trọng | `AB<R>` trên hàng con R | Đánh giá TRUE/FALSE theo từng bài sau review cả chương; không ghi AB hàng cha. |
| Summary từng bài (STEP 5) | `AC<R>` trên từng hàng con R | **Worker ghi sau review/refactor Knowledge**; barem bốn mục 0/1/2/3, đúng bài và tối đa 30.000 ký tự/ô. |
| Liên kết toàn chương (STEP 5) | **`AC<PARENT_ROW>`** tại đúng hàng Menu Cấp 1 | **Worker chương tự tạo/ghi toàn bộ YAML `section.yml`**: `roadmap` là danh sách bài và transition (để Portal dựng ô bài/mũi tên); `comparisonMarkdown` chứa nguyên một bảng GFM. |

- Mỗi Menu Cấp 1 ở **một hàng riêng trong cột A**; ô B cùng hàng **ban đầu trống** hoặc đã có filename .md. Nhận diện hàng cha từ A có text; B của hàng cha **không phải Menu Cấp 2**. Hàng cấp 2 ở các hàng B liên tiếp bên dưới có A trống, cho tới trước Menu Cấp 1 kế tiếp.
- Ví dụ thuần tọa độ: chương tại `A20`, các mục `B21:B27` thì worker đọc `A1`, `B1`, `A20`, `B20`, `B21:B27`, `C21:N27`, `O21:O27`, `AB21:AB27`, **`AC20` và `AC21:AC27`**; được ghi **`B20` (nếu trống), `C21:N27`, `O21:O27`, `AB21:AB27`, `AC20` (YAML `section.yml` toàn chương), `AC21:AC27` (Summary Markdown từng bài)**. Tuyệt đối không ghi `AC` của chương khác; mỗi worker chỉ ghi AC thuộc chapter được giao.
- Phải nối các ô câu trả lời cùng hàng **C → N, không tự thêm ký tự ngăn cách**, mới có được Markdown nguyên bản của từng bài. Không nhầm các mảnh thành bài riêng.
- **Giới hạn hiện hành của STEP 5:** tối đa **30.000 ký tự/ô Knowledge**, **12 ô C–N**, **360.000 ký tự/bài**; **mỗi `AC<CHILD>` tối đa 30.000 ký tự cho Summary Markdown, `AC<PARENT>` tối đa 30.000 ký tự cho YAML `section.yml`**. **O hàng con chỉ dùng cho Difficulty; AB hàng con dùng cho isImportant; AC cha và con đều do STEP 5 sở hữu theo từng tọa độ được giao**; **B hàng cha là filename `.md`**; X:AA dùng cho Video, ngoài phạm vi STEP 5. Không sửa A, B hàng con, O hàng cha, Template, Roadmap, Video hoặc **Java source/config ngoài package skeleton được giao**. Prime được phép provision riêng `AC18/AC19` và độ rộng grid trong preflight, worker không sửa header.
- Filename phải là ASCII PascalCase/acronym hợp lệ theo importer: `^[A-Za-z][A-Za-z0-9]*\.md$`, đại diện đúng ngữ nghĩa cả chương; không chọn tên mơ hồ kiểu `Chapter1.md`/`Topic.md`, tên hệ thống Windows `CON`, `PRN`, `AUX`, `NUL`, `COM1–COM9`, `LPT1–LPT9`. **Trùng tên case-insensitive hoặc trùng kebab-case anchor prefix được Prime kiểm tra CUỐI CÙNG trên toàn tab sau khi mọi worker đã tự điền**; không yêu cầu Prime chọn hoặc điền tên thay worker.

## 2. Trách nhiệm của Prime

### 2.0. BẮT BUỘC ĐẦU STEP 5: module setup rồi sync Gradle, trước khi giao bất kỳ worker

1. Dùng `I3` tab đã xác minh để nhận đúng module và `master.json` **của module đó**, kiểm tra clean/dirty và snapshot các field có thể thay đổi. Chỉ sửa `VALUE` của `MODULE_TYPE` thành **`SERVLET` nếu chưa là SERVLET**; bật Swagger bằng **`BUILD_SWAGGER.VALUE = \"TRUE\"`** nếu chưa bật. Trong schema hiện tại **không có `USE_SWAGGER`**; `BUILD_SWAGGER` mới là khóa thật, không thêm field giả. Giữ các trường khác, không ghi đè thay đổi đồng thời hoặc cấu hình mâu thuẫn chưa được kiểm tra. Đây là **việc của Prime**, không phải của worker.
2. Sau khi đổi cấu hình, **thực hiện Gradle settings/project configuration sync** theo pipeline hiện có: `PresetSetupPlugin → ModuleConfigurationSyncService` đồng bộ schema `master.json/properties.json` và module setup áp `SERVLET`/Swagger. Sử dụng lệnh Gradle **module-scoped, không chạy task import Sheet**; cấu hình settings có thể quét nhiều module nên snapshot/diff toàn workspace, kiểm tra side effects và chỉ giữ những thay đổi cần thiết của module đích, không xóa sửa đổi có từ trước. Nếu sync thất bại → `BLOCKER: MODULE_CONFIG_SYNC_FAILED`, **không giao worker**.
3. **Exact readback** `master.json` (MODULE_TYPE=SERVLET, BUILD_SWAGGER=TRUE), `properties.json`/capability Swagger và cấu hình Gradle; xác nhận `*Application.java` duy nhất được setup tạo (hoặc đã tồn tại), không tạo thêm bằng tay, không ghi đè class human-owned. Preflight compile/task availability khi thích hợp; nếu thiếu cấu hình hoặc Application → blocker, **không chuyển phần setup sang worker**.
4. **Sync này chỉ là sync cấu hình Gradle đầu STEP 5**, khác hoàn toàn **sync nội dung README từ Sheet** ở **cuối STEP 5** sau khi mọi chương QA PASS. Không chạy `generateModuleReadmeFromSheetByLocation`, ByFiles, combined import hoặc nâng CURRENT_STEP.VALUE lúc module setup.

### 2.1. Xác định từng package chương và khóa ownership

- Prime đọc **toàn bộ các hàng Menu Cấp 1** để tạo bảng `PARENT_CELL → ENGLISH_CHAPTER_NAME → CHAPTER_PACKAGE → MAIN_SKELETON_PATH → TEST_SKELETON_PATH → OWNER_WORKER`. **Package phải mang tên TIẾNG ANH đúng ý nghĩa Menu Cấp 1**, giống quy tắc tên file Markdown tiếng Anh: ưu tiên basename tiếng Anh của `B` cha khi đã có filename hợp lệ (ví dụ `GenericMethods.md → generic_methods`, `OOPBasic.md → oop_basic`); bỏ đuôi `.md`, chuyển PascalCase/acronym thành **lowercase snake_case**. Nếu B cha trống, Prime chỉ xác định trước **English semantic chapter name** từ A cha và nội dung chương, không phiên âm/chuyển tự tiếng Việt thành ASCII: `Đóng gói → encapsulation`, **không** phải `dong_goi`. Worker STEP 5 dùng đúng package Prime giao và tiếp tục tự đặt/ghi filename B cha theo quyền sở hữu đã có; Prime không điền B thay worker. Tên package phải hợp lệ `[a-z][a-z0-9_]*`, không là Java keyword (thêm hậu tố `_chapter` khi basename trùng keyword, ví dụ `Class.md → class_chapter`), không trùng case-insensitive; mâu thuẫn giữa filename và tên/ý nghĩa chương → báo blocker, không tự đoán.
- **Khi worker STEP 5 hoàn thành filename**, Prime đối chiếu lại **B cha ↔ tên English package đã giao**. Nếu filename mới khác tên/package dự kiến, phải giải quyết chênh lệch qua chapter ownership và kiểm tra conflict trước khi nghiệm thu STEP 5; không để sai lệch giữa filename và package. Lượt sửa trùng filename B cha cuối STEP 5 cũng phải QA đồng bộ tên package (nếu tên file mới dẫn tới package khác, chỉ sửa skeleton qua lượt được phân quyền riêng, không sử dụng quyền chỉ sửa B để đụng filesystem).
- Xác định root Java từ đúng module, `JAVA_BASE_PACKAGE` và cấu trúc feature package đã tồn tại; preflight đường dẫn chuẩn hóa **nằm dưới đúng module**, không bị path traversal/symlink, không nằm trong package của chương khác. Prime chỉ **xác định và giao** paths; **chính STEP_5_WORKER tạo skeleton**, không để Prime tạo hộ. Nếu module đã có source trong vùng package dự kiến, Prime xác minh owner, **không tự cho worker ghi đè**.
- Cấu trúc phải giao cho worker (main + test) theo mỗi chương: `<main-package>/{controller,dto,service,model,exception,repository}/` và `<test-package>/`. **Worker tạo các thư mục và marker rỗng `.gitkeep` khi cần để Git/worktree giữ lại skeleton**, không tạo class Java, source hoặc file logic giả. Giữ skeleton hiện có để tương thích; **STEP 6 mới** chỉ xử lý Interview/Case Study, không nhận quyền viết Java theo skeleton này.

1. **Bước xác minh đầu tiên, trước mục 2.0 module setup:** Prime tự đọc `/java-learning/module-generate-agent/GENERAL_AGENT_RULES.md` và `/java-learning/module-generate-agent/module-generate-info.txt`. Lấy URL từ `Excel Page URL`, mapping `<TÊN MODULE> sheet id : <SHEET_ID>`; xây dựng URL trực tiếp `<URL_GỐC>/edit?gid=<SHEET_ID>#gid=<SHEET_ID>`. Không hardcode, không nhầm gid với spreadsheet ID. Xác minh tab, `A1`, `B1`, **`I3` module path**; sau đó **hoàn tất mục 2.0 cấu hình và Gradle sync** rồi mới preflight/giao worker các bước 2–7.
2. Prime reads all A/B menu rows and identifies chapter parents via B-parent filename and child rows via B-child title (A child may be blank before fill or contain an existing valid lesson ID on reruns). Record exact parent A/B/C coordinates and every child row. Missing or malformed chapter filename/base ID -> blocker; never guess ownership.
3. Trước khi giao, Prime kiểm tra các bài đã sinh trong `C:N` của chương. Nếu hàng mục cấp 2 không có bài từ STEP 4 hoặc bài nằm ngoài C:N, ghi nhận `BLOCKER: INCOMPLETE STEP 4 CHAPTER`; **không coi chương đã sẵn sàng refactor đầy đủ**. Kiểm tra header `C19:N19 = Câu trả lời`, `O19 = Difficulty`, `AB19 = isImportant`, `X19:AA19 = Video Script`. **Chính Prime provision `AC` an toàn**: đọc `gridProperties.columnCount`, snapshot `AC18:AC<END>` (entered/effective/formatted, formula, note, validation, chip), chỉ mở rộng `columnCount` đến ít nhất 29 bằng `updateSheetProperties` với field mask `gridProperties.columnCount` khi thiếu; chỉ ghi chính xác **`AC18 = Summary`, `AC19 = Summary YAML`** nếu hai header còn sạch/trống, giữ nguyên formatting và mọi dữ liệu khác. Chỉ chấp nhận nguyên cặp legacy **`Learning Objectives` + `Learning Objectives YAML`**; cặp `Summary Markdown`/`Learning Objectives Markdown` cũ phải được migrate có kiểm soát sang YAML trước khi import, không tự diễn giải nội dung cũ. Header lẫn lộn hoặc khác schema → `BLOCKER: STEP_5_AC_SCHEMA_CONFLICT`. **Exact readback** grid/header trước khi giao worker, không sửa Template hoặc tab khác. Kiểm kê **cả `AC` hàng cha lẫn tất cả `AC` hàng con**: trống → `NEW_FILL`; AC cha có YAML đúng schema, AC con có guide Markdown đúng barem và vẫn khớp Knowledge cuối → `UNCHANGED`; có formula, chip, dữ liệu lạ hoặc guide lỗi thời → `BLOCKER: EXISTING_AC_CONTENT/STALE_OBJECTIVES`, không cấp quyền overwrite mù. Đọc O/AB và AC từng bài trước handoff, không coi mục tiêu cũ là đã khớp nếu Knowledge sẽ bị refactor.
   - Backfill from existing full module section.yml requires explicit permission and reviewed projection into authored-only AC. No blind copy or overwrite of existing human content.
4. **Kiểm tra trạng thái pool** và chỉ điều phối trong hạn mức **Mode A/B của session (A=3 mặc định; B=N do user chỉ định, thiếu N → BLOCKER; tính cả ACTIVE + SLEEPING)**. Tái sử dụng worker SLEEPING; **không spawn khi còn worker SLEEPING**. Nếu cần spawn thì tạo một worker mỗi lệnh và kiểm tra kết quả trước khi tạo tiếp. Nếu pool hiện có bất thường vượt hạn mức đang áp dụng, không tạo thêm worker hoặc đánh thức vượt giới hạn; báo/điều phối lại theo GENERAL.
5. **Mỗi worker chỉ nhận 1 chương tại một thời điểm.** Prime quản lý bảng `PARENT_CELL → PARENT_FILENAME_CELL → CHILD_CELLS → AC_PARENT_LINK_CELL → AC_CHILD_CELLS → MAIN_SKELETON_PATH → TEST_SKELETON_PATH → OWNER_WORKER → STATUS`, khóa **B cha + AC cha, C:N/O/AB/AC hàng con và hai package path** trước khi giao. Không giao cùng chương cho nhiều worker; không sửa trực tiếp Sheet hoặc package đang có owner. Worker các chương khác có thể song song trong namespace riêng.
6. Prime worker handoff remains exactly four lines. Include A/B/C parent, the exact child B/C:N/O/AB/AC cells, AC parent, AC18/19 and Special Note in READ; include B/C parent, reviewed child C:N/O/AB/AC, AC parent and main/test skeleton paths in WRITE. A child IDs are NOT worker write scope.
7. Prime chỉ tiếp tục giao chương khác cho worker **sau khi worker đã báo cáo, xác minh và giải phóng cả ownership Sheet và skeleton package**. Không khởi động STEP tiếp theo, không commit/push hoặc sửa các file khác ngoài module setup đã quy định và vùng skeleton.

### Mẫu tin nhắn Prime giao một worker — CHỈ bốn dòng

```text
<LINK_TAB_ĐÃ_XÁC_MINH>
A1, B1, <PARENT_CELL_A>, <PARENT_FILENAME_CELL_B>, <PARENT_BASE_ID_CELL_C>, <CHILD_B_RANGE>, <SOURCE_ANSWER_RANGE_C_TO_N>, <SOURCE_DIFFICULTY_RANGE_O>, <SOURCE_IMPORTANCE_RANGE_AB>, AC18, AC19, <AC_PARENT_CELL>, <AC_CHILD_CELLS>, <SPECIAL_NOTE_TITLE_CELL>, <SPECIAL_NOTE_VALUE_CELL>
<WRITE_PARENT_FILENAME_CELL_B_NẾU_TRỐNG>, <WRITE_PARENT_BASE_ID_CELL_C_NẾU_TRỐNG>, <WRITE_RANGE_C_TO_N_CỦA_CÁC_HÀNG_CON>, <WRITE_DIFFICULTY_RANGE_O_CỦA_CÁC_HÀNG_CON>, <WRITE_IMPORTANCE_RANGE_AB_CỦA_CÁC_HÀNG_CON>, <WRITE_AC_CHILD_CELLS_THEO_TỪNG_BÀI>, <WRITE_AC_PARENT_CELL> ; <ABSOLUTE_MAIN_CHAPTER_PACKAGE_PATH> ; <ABSOLUTE_TEST_CHAPTER_PACKAGE_PATH>
/java-learning/module-generate-agent/worker/STEP_5_WORKER.md
```

For filename collision correction, worker may update ONLY B and C parent before IDs exist in A children; AC and other cells remain out of scope.

### STEP 5 - Authored Summary at AC parent and Markdown at AC children

Worker authors AC child Markdown with the original 0/1/2/3 learning objectives format.
AC parent YAML root has exactly six keys: version (1), type (LEARNING_ROADMAP),
title, roadmapHeading, roadmap, comparisonMarkdown. The roadmap has N entries in
B-child row order. Each item has exactly two keys: description and transition.
For nonfinal items transition is a map containing only description; final transition
is null. Do not author id/order/title/transition.to: the Summary importer adds
these from A child IDs, B child titles, and index into the module's full section.yml.

comparisonMarkdown still contains one complete GFM table with N+1 columns and
the original heading and at least four comparison criteria. No redundant fake UI
arrows/links in roadmap description. Existing valid authored AC stays unchanged;
legacy full-schema/stale/human-owned AC is a migration blocker, not a silent rewrite.
AC parent and all AC child cells have the original 30,000-character cap.

### STEP 5 — Đánh giá isImportant (AB) trong cùng lượt review

- Prime preflight `AB19 = isImportant` và vùng `AB<CHILD_START>:AB<CHILD_END>` của mỗi chương. Cột AB chỉ dành cho từng hàng Menu Cấp 2, không ghi hàng cha; chỉ nhận ô trống hoặc giá trị TRUE/FALSE, không chạm formula/notes/validation lạ. Hàng 20:700 kế thừa dropdown từ Template; hàng >700 vẫn chỉ được dùng TRUE/FALSE.
- Ownership và handoff bốn dòng phải bao gồm dải AB hàng con **cùng chapter** ở cả vùng đọc lẫn ghi, như mẫu giao worker bên trên. Việc viết AB là quyền worker sở hữu chương, Prime không đánh dấu thay khi STEP 5 đang chạy.
- Worker chọn TRUE nếu bài là nền tảng/prerequisite quan trọng, API/cơ chế thiết yếu cần thành thạo, hoặc giúp tránh lỗi nghiêm trọng phổ biến; FALSE nếu bài đào sâu hoặc tham khảo không bắt buộc. Phải đọc toàn bộ bài và so sánh với các bài cùng chương; không suy từ độ dài, Difficulty, hoặc đặt tỷ lệ TRUE cố định.
- Sau khi worker hoàn tất, Prime readback **từng AB hàng con**, đối chiếu đánh giá và những giá trị TRUE đã có. Worker không được âm thầm đổi TRUE cũ sang FALSE nếu chưa có căn cứ rõ ràng; nếu cần đổi phải nêu lý do.
- Importer STEP 5 lấy Difficulty từ O và isImportant từ AB; **AB trống luôn thành false** và O trống luôn BASIC. Sheet là nguồn dữ liệu duy nhất: không lấy lại flags/ID/humanNote cũ từ module. Prime kiểm tra YAML sau DRY_RUN/APPLY và đảm bảo không có ID stale hay file Metadata cũ thuộc scope.


## 3. Kiểm tra kết quả và nghiệm thu của Prime

1. Theo dõi ownership `PARENT_CELL → PARENT_FILENAME_CELL → AC_PARENT_LINK → CHILD_ROWS → MAIN_SKELETON_PATH → TEST_SKELETON_PATH → OWNER_WORKER → STATUS` và không giao tiếp khi Sheet/package còn worker sở hữu; tái sử dụng worker SLEEPING chỉ sau khi worker đã báo cáo/release.
2. Prime validates all B/C parent identities, AC child Markdown, authored-only AC parent (N description/transition items with final null and one N+1-column GFM table), complete C:N/O/AB child coverage and real main/test skeleton. Existing stale or unowned content blocks QA.
3. Không coi `COMPLETED` của worker là bằng chứng độc lập. Khi thiếu bài, chapter QA hoặc skeleton QA lỗi, trùng package/path, ghi rõ tọa độ và đường dẫn thực, xác minh phần đã làm trước khi giao chính worker sửa trong ownership hợp lệ; không retry mù/ghi đè dữ liệu worker khác.
4. **Kiểm tra filename TOÀN TAB ở CUỐI STEP 5**, sau khi worker của **mọi chương** đã điền/readback B cha: đọc lại tất cả A/B cha theo thứ tự, kiểm tra không thiếu filename, regex, tên có nghĩa với A và nhóm bài, không trùng basename **case-insensitive**, không trùng **kebab-case prefix** theo quy tắc Gradle, không dùng tên dành riêng Windows. Không coi worker `COMPLETED` riêng lẻ là đủ để vượt qua kiểm tra uniqueness toàn tab. Có trùng → `BLOCKER: DUPLICATE PARENT FILENAME` nêu chính xác các ô B/tên và **KHÔNG cập nhật CURRENT_STEP.VALUE**.
5. Filename collision correction: Prime reassigns the same chapter worker with B and C parent write ownership only, before A-child ID fill. Worker changes B filename and C base ID together, snapshots and exact-readbacks both, and reports COLLISION_FIXED. Prime rechecks global uniqueness. If A child already contains IDs, block any implicit B/C rename; explicit ID migration is required.

### Mẫu giao worker SỬA TRÙNG sau QA cuối — cũng chỉ bốn dòng

```text
<LINK_TAB_ĐÃ_XÁC_MINH>
A1, B1, <PARENT_CELL_A>, <PARENT_FILENAME_CELL_B>, <PARENT_BASE_ID_CELL_C>, <CHILD_B_RANGE>
<WRITE_ONLY_PARENT_FILENAME_CELL_B_ĐÃ_CÓ_TÊN_TRÙNG>, <WRITE_ONLY_PARENT_BASE_ID_CELL_C_ĐỒNG_BỘ>
/java-learning/module-generate-agent/worker/STEP_5_WORKER.md
```

Đây là phạm vi ghi B cha đã được Prime xác minh xung đột, **không phải yêu cầu worker chạy lại toàn bộ STEP 5**, và không ủy quyền đọc/ghi C:N/O/AB của bài con.
6. After chapter QA and global filename/base ID collision fixes, Prime runs the mandatory A-child ID fill task and verifies all IDs. Only then run the two independent module-local imports (Knowledge+Metadata, then Summary). Never import while a worker owns Sheet/module content.
   - **(1) Knowledge + Metadata:** `generateModuleReadmeFromSheetByLocation`, DRY_RUN → APPLY, với URL/gid/I3 đã xác minh. Đây là bước trả công nợ `DEFERRED_UNTIL_STEP_5` của STEP 3/4. Importer preflight toàn module, **CLEAN toàn bộ `knowledge/` và `metadata/` VI** (cả chapter cũ và Metadata legacy VI) rồi FILL Markdown/Metadata từ Sheet C:N/O/AB và H2 anchor, không merge dữ liệu cũ. O trống → BASIC, AB trống → false,  Prime đối chiếu chính xác Markdown, anchor, Difficulty, isImportant và không còn ID stale; xác minh Interview/Quiz/Video/EN/Reference/Introduction không đổi.
   - **(2) Summary:** after Knowledge + Metadata APPLY/readback, run the module-local Summary importer DRY_RUN -> APPLY. Preflight authored-only AC parent, all A-child canonical IDs and B-child titles, N-step transitions and full GFM table. Materialize complete module section.yml (id/order/title/to derived from A/B and index), plus summaries.yml from AC children. Validate the generated full document, not literal equality to AC parent. Clean only owned VI summary directories.
   - **BẮT BUỘC cả hai task** có DRY_RUN hợp lệ, APPLY thành công hoặc UNCHANGED và kiểm tra đầu ra PASS. `SKIPPED_*`, lỗi schema/filename/metadata/AC/ID, thiếu file hoặc chỉ DRY_RUN đều là **`BLOCKER: MODULE KNOWLEDGE/SUMMARY SYNC`**. **Không cập nhật CURRENT_STEP.VALUE** khi chỉ Knowledge PASS nhưng Summary chưa PASS. Chỉ sau toàn bộ Chapter QA + Skeleton QA + **cả hai module sync** mới cập nhật rồi exact readback `CURRENT_STEP.VALUE = "5".

**MANDATORY PRIME ID FILL (after all workers, before the two importers):**
Prime validates every B-parent filename and C-parent base ID across the tab, including
uniqueness and exact C = slugFromStem(B without .md). With no concurrent writers, run:

    .\gradlew.bat :<MODULE_GRADLE_PATH>:fillModuleLessonIdsFromSheetByLocation -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=DRY_RUN
    .\gradlew.bat :<MODULE_GRADLE_PATH>:fillModuleLessonIdsFromSheetByLocation -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=APPLY

Only blank A-child cells are filled; existing IDs and A-parent titles must not be changed.
The task derives exact child regions from the verified Menu and reads C parent on each
chapter. Every ID needs exact readback, unique chapter prefix and global uniqueness.
Status UNCHANGED when all IDs are already valid is acceptable. Invalid/missing/colliding
base or lesson IDs -> BLOCKER: LESSON_ID_SYNC and no Knowledge/Summary import.
For filename collision correction before the fill, the worker must update B and C parent
together with explicit ownership; when A IDs exist, do not rename B/C automatically.

### Hai task module-local Prime bắt buộc chạy cuối STEP 5

```powershell
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:generateModuleReadmeFromSheetByLocation -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=DRY_RUN
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:generateModuleReadmeFromSheetByLocation -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=APPLY
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:generateModuleSummaryFromSheetByLocation -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=DRY_RUN
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:generateModuleSummaryFromSheetByLocation -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=APPLY
```

**Source của task `ByLocation` là Google Sheets**, không đọc trực tiếp bản offline `content/sheet/vi/module.xlsx`. `I3` xác định module đích, không suy luận tên module từ tab. Không dùng ByFiles hoặc task import kết hợp; Prime chạy import sau QA **toàn tab**, tuyệt đối không import sau mỗi chapter hay trong worker.

## 4. Điều kiện hoàn thành STEP 5

- **ĐẦU STEP 5** Prime xác minh module, đặt `MODULE_TYPE=SERVLET`, `BUILD_SWAGGER=TRUE`, Gradle configuration sync thành công, kiểm tra đúng Application duy nhất và không làm hỏng thay đổi ngoài phạm vi **TRƯỚC khi giao worker**.
- Mỗi Menu Cấp 1 được giao cho **đúng một worker chịu trách nhiệm trọn chương và skeleton main/test**, pool không vượt hạn mức Mode A/B của session.
- Worker **đã đọc trọn worker/STEP_5_WORKER.md** và **đọc đầy đủ từng bài** của chương, lập bản đồ nội dung, đánh giá/refactor có căn cứ, QA toàn chương.
- Giảm **lặp gây ảnh hưởng chất lượng** nhưng **không đánh đổi chiều sâu, tính tự học, độ chính xác hoặc ví dụ cần thiết** để giảm số chữ. Không thay đổi bài đang tốt chỉ để sửa.
- Normal worker-owned cells: B/C parent, C:N/O/AB/AC children and AC parent, plus strictly assigned main/test skeletons. Prime exclusively writes A-child IDs after worker QA. All existing ownership/readback rules remain in effect.
- Mỗi hàng Menu Cấp 2 phải có **đúng một Difficulty**: `BASIC` (kiến thức nền), `INTERMEDIATE` (vận dụng/phối hợp và trade-offs), `ADVANCED` (chiều sâu, cơ chế phức tạp, giới hạn/edge cases). Đánh giá dựa trên **nội dung thực sau review**, không đoán theo độ dài bài, thứ tự hay tên file. Ô O hàng Menu Cấp 1 không được ghi.
- Chapter COMPLETED requires complete Knowledge review, filename B parent, base ID C parent, all child Difficulty/isImportant and AC Markdown, authored-only AC parent YAML, exact readback, chapter QA and real skeleton QA. Prime separately validates/fills A-child IDs after all workers.
- STEP 5 completes only with all chapter/skeleton QA, global B/C/A identity validation and both separate Knowledge/Metadata and Summary DRY_RUN/APPLY/readback passes. Compare generated Summary structure, not verbatim AC-parent bytes; then and only then update CURRENT_STEP.VALUE to 5.
