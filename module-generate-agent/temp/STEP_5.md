# STEP 5 — PRIME ONLY: Module Setup, Chapter Review, Difficulty, Filename & Package Skeleton

## Mục tiêu

Sau STEP 4, **mỗi Menu Cấp 2 đã có một bài học README Markdown độc lập** ở các ô `C:N` cùng hàng. **Ngay đầu STEP 5, Prime chuẩn bị module `SERVLET`, bật Swagger và chạy đồng bộ cấu hình Gradle trước khi giao worker.** Mỗi worker vừa **review/refactor toàn bộ chương**, **gán Difficulty ở O từng hàng con**, **tự ghi filename tiếng Anh `.md` vào B hàng cha**, vừa **tạo package skeleton Java/test của chính chương đó** từ Menu Cấp 1. Toàn bộ nhiệm vụ filename cũ đã gộp vào STEP 5; **STEP 6 chỉ xây dựng/nghiệm thu API; Swagger info, examples và mapping sẽ có STEP khác**. STEP 8 Interview, STEP 9 Quiz, STEP 10 Video giữ nguyên phạm vi nghiệp vụ.

**Mỗi sub-worker nhận trực tiếp và duy nhất một Menu Cấp 1 (mọi mục cấp 2 của chương đó)**, với **hai vùng ghi độc quyền cùng lúc**: (1) các ô Sheet thuộc chương, bao gồm AB isImportant và (2) **hai đường dẫn package skeleton Java main/test của chính chương**. Các chương khác nhau có thể làm song song khi package không giao nhau, nhưng **không chia một chương cho nhiều worker, không có hai writer cùng Sheet/package**.

**Hạn mức pool theo Mode A/B của session (A=3 mặc định; B=N do user chỉ định, thiếu N → BLOCKER), tính cả ACTIVE và SLEEPING**. Tuân thủ `GENERAL_AGENT_RULES.md` và `PRIME_RULES.md §0`; tái sử dụng worker SLEEPING trước, không spawn khi còn worker SLEEPING; khi cần thì spawn lần lượt từng worker.

### Phân tách PRIME / WORKER (giống STEP 4)

- **File `STEP_5.md` này chỉ dành cho Prime; worker không đọc.**
- **Toàn bộ hướng dẫn thực thi dành cho worker nằm trong `/java-learning/module-generate-agent/temp/worker/STEP_5_WORKER.md`.** Worker phải **tự đọc toàn văn file worker-only**, kể cả sau context compaction; Prime tuyệt đối không rút gọn quy trình review/refactor thành tin nhắn ngắn thay cho tài liệu. Worker không đọc STEP Prime, STEP khác, GENERAL, rules hoặc TXT.
- Prime phải đọc `GENERAL_AGENT_RULES.md`, **liệt kê và đọc tất cả file hiện có trong `module-generate-agent/rules/`**, `module-generate-info.txt`, toàn bộ cả `STEP_5.md` và `worker/STEP_5_WORKER.md` trước khi giao; đọc lại khi mất context.
- **Tin nhắn Prime giao worker chỉ gồm 4 dòng định vị:** link tab, tọa độ ô nguồn, **vùng ghi Sheet của đúng chương + hai đường dẫn package main/test được cấp quyền**, đường dẫn file worker-only. Không thêm lời dẫn, rules, giải thích, nội dung README, bản refactor mẫu hoặc tóm tắt. **Một lần giao = một chương = một lượt thực hiện trọn vẹn**.

## 1. Phạm vi và cấu trúc Google Sheets

| Thành phần | Ô/vùng | Quy định |
| --- | --- | --- |
| Nhãn và tên module | `A1`, `B1` | Ngữ cảnh, **chỉ đọc**. |
| Menu Cấp 1 của chương | `A<PARENT_ROW>` | Prime gửi **chính xác một ô cha**, chỉ đọc. |
| Filename của chương | `B<PARENT_ROW>` | **Worker cùng chương tự chọn và điền vào ô B cha nếu còn trống**; B cha có filename hợp lệ thì giữ nguyên. Prime KHÔNG viết filename. |
| Menu Cấp 2 của chương | `B<START_ROW>:B<END_ROW>` | Prime gửi **đúng khoảng hàng con**, không giao worker tự quét/tìm. |
| README của từng mục cấp 2 | `C<R>:N<R>` với mỗi `R` là hàng con được giao | Đọc, review, refactor và **chỉ ghi lại tại đúng hàng R**. |
| Difficulty của từng mục cấp 2 | `O<R>` trên hàng con R | Chọn đúng một giá trị `BASIC` / `INTERMEDIATE` / `ADVANCED`; không ghi O hàng cha. |
| Kiến thức quan trọng | `AB<R>` trên hàng con R | Đánh giá TRUE/FALSE theo từng bài sau review cả chương; không ghi AB hàng cha. |

- Mỗi Menu Cấp 1 ở **một hàng riêng trong cột A**; ô B cùng hàng **ban đầu trống** hoặc đã có filename .md. Nhận diện hàng cha từ A có text; B của hàng cha **không phải Menu Cấp 2**. Hàng cấp 2 ở các hàng B liên tiếp bên dưới có A trống, cho tới trước Menu Cấp 1 kế tiếp.
- Ví dụ thuần tọa độ: chương tại `A13`, các mục `B14:B20` thì worker đọc `A1`, `B1`, `A13`, `B13`, `B14:B20`, `C14:N20` và `O14:O20`; được ghi **`B13` (nếu trống), `C14:N20`, `O14:O20`**. Không sửa A13, B14:B20, O13, hoặc các hàng 21 trở đi.
- Phải nối các ô câu trả lời cùng hàng **C → N, không tự thêm ký tự ngăn cách**, mới có được Markdown nguyên bản của từng bài. Không nhầm các mảnh thành bài riêng.
- **Giới hạn hiện hành của STEP 5:** tối đa **30.000 ký tự/ô Knowledge**, **12 ô C–N**, **360.000 ký tự/bài**. **O hàng con chỉ dùng cho Difficulty; AB hàng con dùng cho isImportant**; **B hàng cha là filename `.md`**; X:AA dùng cho Video, ngoài phạm vi STEP 5. Không sửa A, B hàng con, O hàng cha, Template, Roadmap, header, Video hoặc **Java source/config ngoài package skeleton được giao**.
- Filename phải là ASCII PascalCase/acronym hợp lệ theo importer: `^[A-Za-z][A-Za-z0-9]*\.md$`, đại diện đúng ngữ nghĩa cả chương; không chọn tên mơ hồ kiểu `Chapter1.md`/`Topic.md`, tên hệ thống Windows `CON`, `PRN`, `AUX`, `NUL`, `COM1–COM9`, `LPT1–LPT9`. **Trùng tên case-insensitive hoặc trùng kebab-case anchor prefix được Prime kiểm tra CUỐI CÙNG trên toàn tab sau khi mọi worker đã tự điền**; không yêu cầu Prime chọn hoặc điền tên thay worker.

## 2. Trách nhiệm của Prime

### 2.0. BẮT BUỘC ĐẦU STEP 5: module setup rồi sync Gradle, trước khi giao bất kỳ worker

1. Dùng `I3` tab đã xác minh để nhận đúng module và `master.json` **của module đó**, kiểm tra clean/dirty và snapshot các field có thể thay đổi. Chỉ sửa `VALUE` của `MODULE_TYPE` thành **`SERVLET` nếu chưa là SERVLET**; bật Swagger bằng **`BUILD_SWAGGER.VALUE = \"TRUE\"`** nếu chưa bật. Trong schema hiện tại **không có `USE_SWAGGER`**; `BUILD_SWAGGER` mới là khóa thật, không thêm field giả. Giữ các trường khác, không ghi đè thay đổi đồng thời hoặc cấu hình mâu thuẫn chưa được kiểm tra. Đây là **việc của Prime**, không phải của worker.
2. Sau khi đổi cấu hình, **thực hiện Gradle settings/project configuration sync** theo pipeline hiện có: `PresetSetupPlugin → ModuleConfigurationSyncService` đồng bộ schema `master.json/properties.json` và module setup áp `SERVLET`/Swagger. Sử dụng lệnh Gradle **module-scoped, không chạy task import Sheet**; cấu hình settings có thể quét nhiều module nên snapshot/diff toàn workspace, kiểm tra side effects và chỉ giữ những thay đổi cần thiết của module đích, không xóa sửa đổi có từ trước. Nếu sync thất bại → `BLOCKER: MODULE_CONFIG_SYNC_FAILED`, **không giao worker**.
3. **Exact readback** `master.json` (MODULE_TYPE=SERVLET, BUILD_SWAGGER=TRUE), `properties.json`/capability Swagger và cấu hình Gradle; xác nhận `*Application.java` duy nhất được setup tạo (hoặc đã tồn tại), không tạo thêm bằng tay, không ghi đè class human-owned. Preflight compile/task availability khi thích hợp; nếu thiếu cấu hình hoặc Application → blocker, **không chuyển phần setup sang worker**.
4. **Sync này chỉ là sync cấu hình Gradle đầu STEP 5**, khác hoàn toàn **sync nội dung README từ Sheet** ở **cuối STEP 5** sau khi mọi chương QA PASS. Không chạy `generateModuleReadmeFromSheetByLocation`, ByFiles, combined import hoặc nâng J3 lúc module setup.

### 2.1. Xác định từng package chương và khóa ownership

- Prime đọc **toàn bộ các hàng Menu Cấp 1** để tạo bảng `PARENT_CELL → ENGLISH_CHAPTER_NAME → CHAPTER_PACKAGE → MAIN_SKELETON_PATH → TEST_SKELETON_PATH → OWNER_WORKER`. **Package phải mang tên TIẾNG ANH đúng ý nghĩa Menu Cấp 1**, giống quy tắc tên file Markdown tiếng Anh: ưu tiên basename tiếng Anh của `B` cha khi đã có filename hợp lệ (ví dụ `GenericMethods.md → generic_methods`, `OOPBasic.md → oop_basic`); bỏ đuôi `.md`, chuyển PascalCase/acronym thành **lowercase snake_case**. Nếu B cha trống, Prime chỉ xác định trước **English semantic chapter name** từ A cha và nội dung chương, không phiên âm/chuyển tự tiếng Việt thành ASCII: `Đóng gói → encapsulation`, **không** phải `dong_goi`. Worker STEP 5 dùng đúng package Prime giao và tiếp tục tự đặt/ghi filename B cha theo quyền sở hữu đã có; Prime không điền B thay worker. Tên package phải hợp lệ `[a-z][a-z0-9_]*`, không là Java keyword (thêm hậu tố `_chapter` khi basename trùng keyword, ví dụ `Class.md → class_chapter`), không trùng case-insensitive; mâu thuẫn giữa filename và tên/ý nghĩa chương → báo blocker, không tự đoán.
- **Khi worker STEP 5 hoàn thành filename**, Prime đối chiếu lại **B cha ↔ tên English package đã giao**. Nếu filename mới khác tên/package dự kiến, phải giải quyết chênh lệch qua chapter ownership và kiểm tra conflict trước khi nghiệm thu STEP 5; không để STEP 6 nhận package khác nghĩa. Lượt sửa trùng filename B cha cuối STEP 5 cũng phải QA đồng bộ tên package (nếu tên file mới dẫn tới package khác, chỉ sửa skeleton qua lượt được phân quyền riêng, không sử dụng quyền chỉ sửa B để đụng filesystem).
- Xác định root Java từ đúng module, `JAVA_BASE_PACKAGE` và cấu trúc feature package đã tồn tại; preflight đường dẫn chuẩn hóa **nằm dưới đúng module**, không bị path traversal/symlink, không nằm trong package của chương khác. Prime chỉ **xác định và giao** paths; **chính STEP_5_WORKER tạo skeleton**, không để Prime tạo hộ. Nếu module đã có source trong vùng package dự kiến, Prime xác minh owner, **không tự cho worker ghi đè**.
- Cấu trúc phải giao cho worker (main + test) theo mỗi chương: `<main-package>/{controller,dto,service,model,exception,repository}/` và `<test-package>/`. **Worker tạo các thư mục và marker rỗng `.gitkeep` khi cần để Git/worktree giữ lại skeleton**, không tạo class Java, source hoặc file logic giả. STEP 6 tiếp nhận hai package path do STEP 5 tạo và Prime xác thực.

1. **Bước xác minh đầu tiên, trước mục 2.0 module setup:** Prime tự đọc `/java-learning/module-generate-agent/temp/GENERAL_AGENT_RULES.md` và `/java-learning/module-generate-agent/temp/module-generate-info.txt`. Lấy URL từ `Excel Page URL`, mapping `<TÊN MODULE> sheet id : <SHEET_ID>`; xây dựng URL trực tiếp `<URL_GỐC>/edit?gid=<SHEET_ID>#gid=<SHEET_ID>`. Không hardcode, không nhầm gid với spreadsheet ID. Xác minh tab, `A1`, `B1`, **`I3` module path**; sau đó **hoàn tất mục 2.0 cấu hình và Gradle sync** rồi mới preflight/giao worker các bước 2–7.
2. **Prime tự đọc vùng A/B của Menu** để xác định từng chương, chính xác `PARENT_CELL`, `PARENT_FILENAME_CELL` và danh sách/vùng `CHILD_CELLS` (chỉ những hàng A trống và B là mục cấp 2). **B trên hàng A được phép còn trống**; nếu đã có filename sai định dạng hoặc có formula/note xung đột thì báo `BLOCKER: INVALID PARENT FILENAME` trước khi giao. Prime không đặt tên thay worker. Không xác định được chương thì blocker, không đoán.
3. Trước khi giao, Prime kiểm tra các bài đã sinh trong `C:N` của chương. Nếu hàng mục cấp 2 không có bài từ STEP 4 hoặc bài nằm ngoài C:N, ghi nhận `BLOCKER: INCOMPLETE STEP 4 CHAPTER`; **không coi chương đã sẵn sàng refactor đầy đủ**. Kiểm tra header `C19:N19 = Câu trả lời`, `O19 = Difficulty`, `X19:AA19 = Video Script`. Đọc O hàng con (chỉ BASIC/INTERMEDIATE/ADVANCED) và AB hàng con (trống hoặc TRUE/FALSE); formula, note xung đột hoặc giá trị sai định dạng ở từng cột đều là blocker. Không tự tạo bài mới thay STEP 4.
4. **Kiểm tra trạng thái pool** và chỉ điều phối trong hạn mức **Mode A/B của session (A=3 mặc định; B=N do user chỉ định, thiếu N → BLOCKER; tính cả ACTIVE + SLEEPING)**. Tái sử dụng worker SLEEPING; **không spawn khi còn worker SLEEPING**. Nếu cần spawn thì tạo một worker mỗi lệnh và kiểm tra kết quả trước khi tạo tiếp. Nếu pool hiện có bất thường vượt hạn mức đang áp dụng, không tạo thêm worker hoặc đánh thức vượt giới hạn; báo/điều phối lại theo GENERAL.
5. **Mỗi worker chỉ nhận 1 chương tại một thời điểm.** Prime quản lý bảng `PARENT_CELL → PARENT_FILENAME_CELL → CHILD_CELLS → MAIN_SKELETON_PATH → TEST_SKELETON_PATH → OWNER_WORKER → STATUS`, khóa **B cha, C:N/O/AB hàng con và hai package path** trước khi giao. Không giao cùng chương cho nhiều worker; không sửa trực tiếp Sheet hoặc package đang có owner. Worker của các chương khác có thể làm song song trong namespace riêng.
6. **Prime gửi duy nhất tọa độ và file worker-only:** link tab, `A1`, `B1`, **A/B hàng Menu Cấp 1**, dải B Menu Cấp 2, C:N/O/AB hàng con; dòng thứ ba ghi **B cha (nếu trống), C:N/O/AB hàng con và hai đường dẫn tuyệt đối main/test skeleton chỉ dành cho đúng chương**, cuối cùng file worker-only. **Không gửi tên filename đề xuất, nội dung README, package implementation mẫu, tóm tắt/rules worker.**
7. Prime chỉ tiếp tục giao chương khác cho worker **sau khi worker đã báo cáo, xác minh và giải phóng cả ownership Sheet và skeleton package**. Không khởi động STEP tiếp theo, không commit/push hoặc sửa các file khác ngoài module setup đã quy định và vùng skeleton.

### Mẫu tin nhắn Prime giao một worker — CHỈ bốn dòng

```text
<LINK_TAB_ĐÃ_XÁC_MINH>
A1, B1, <PARENT_CELL_A>, <PARENT_FILENAME_CELL_B>, <CHILD_B_RANGE>, <SOURCE_ANSWER_RANGE_C_TO_N>, <SOURCE_DIFFICULTY_RANGE_O>, <SOURCE_IMPORTANCE_RANGE_AB>
<WRITE_PARENT_FILENAME_CELL_B_NẾU_TRỐNG>, <WRITE_RANGE_C_TO_N_CỦA_CÁC_HÀNG_CON>, <WRITE_DIFFICULTY_RANGE_O_CỦA_CÁC_HÀNG_CON>, <WRITE_IMPORTANCE_RANGE_AB_CỦA_CÁC_HÀNG_CON> ; <ABSOLUTE_MAIN_CHAPTER_PACKAGE_PATH> ; <ABSOLUTE_TEST_CHAPTER_PACKAGE_PATH>
/java-learning/module-generate-agent/temp/worker/STEP_5_WORKER.md
```

Prime thay placeholder bằng tọa độ/range và **hai đường dẫn package tuyệt đối đã preflight**. Nếu hàng con không liên tiếp, liệt kê đủ các ô nguồn và đích tương ứng trong hai dòng tọa độ. **Chỉ worker đọc toàn văn `worker/STEP_5_WORKER.md` để biết cách tạo skeleton và review, Prime không gửi nội dung/tóm tắt bài học hoặc code.** Giao lượt sửa trùng filename cuối STEP vẫn giữ bốn dòng scope **chỉ B cha**, không có quyền tạo/chỉnh skeleton.

### STEP 5 — Đánh giá isImportant (AB) trong cùng lượt review

- Prime preflight `AB19 = isImportant` và vùng `AB<CHILD_START>:AB<CHILD_END>` của mỗi chương. Cột AB chỉ dành cho từng hàng Menu Cấp 2, không ghi hàng cha; chỉ nhận ô trống hoặc giá trị TRUE/FALSE, không chạm formula/notes/validation lạ. Hàng 20:700 kế thừa dropdown từ Template; hàng >700 vẫn chỉ được dùng TRUE/FALSE.
- Ownership và handoff bốn dòng phải bao gồm dải AB hàng con **cùng chapter** ở cả vùng đọc lẫn ghi, như mẫu giao worker bên trên. Việc viết AB là quyền worker sở hữu chương, Prime không đánh dấu thay khi STEP 5 đang chạy.
- Worker chọn TRUE nếu bài là nền tảng/prerequisite quan trọng, API/cơ chế thiết yếu cần thành thạo, hoặc giúp tránh lỗi nghiêm trọng phổ biến; FALSE nếu bài đào sâu hoặc tham khảo không bắt buộc. Phải đọc toàn bộ bài và so sánh với các bài cùng chương; không suy từ độ dài, Difficulty, hoặc đặt tỷ lệ TRUE cố định.
- Sau khi worker hoàn tất, Prime readback **từng AB hàng con**, đối chiếu đánh giá và những giá trị TRUE đã có. Worker không được âm thầm đổi TRUE cũ sang FALSE nếu chưa có căn cứ rõ ràng; nếu cần đổi phải nêu lý do.
- Importer STEP 5 lấy Difficulty từ O và isImportant từ AB; **AB trống bảo toàn giá trị metadata cũ**, hoặc default false nếu ID chưa có; AB TRUE/FALSE sẽ thay thế metadata tương ứng. Prime kiểm tra YAML sau DRY_RUN/APPLY và đảm bảo không làm mất cờ TRUE ngoài scope.


## 3. Kiểm tra kết quả và nghiệm thu của Prime

1. Theo dõi ownership `PARENT_CELL → PARENT_FILENAME_CELL → CHILD_ROWS → MAIN_SKELETON_PATH → TEST_SKELETON_PATH → OWNER_WORKER → STATUS` và không giao tiếp khi Sheet/package còn worker sở hữu; tái sử dụng worker SLEEPING chỉ sau khi worker đã báo cáo/release.
2. Đối chiếu báo cáo với dữ liệu thật: `READ_COVERAGE` phải đủ mọi bài C:N của chương, `FINDINGS` và `CHANGES` phải có căn cứ, `READBACK` từng hàng được sửa và `CHAPTER_QA` đều PASS. Prime đọc lại từng bài C:N, nối không dấu phân cách, **xác minh từng O/AB hàng con và B hàng cha**. **Đồng thời kiểm tra main package có sáu thư mục `controller/dto/service/model/exception/repository`, package test tồn tại, marker `.gitkeep` rỗng để Git giữ thư mục chưa có source, không chứa Java class/source giả do STEP 5 tự viết, không ghi ngoài namespace, cấu hình `SERVLET` + `BUILD_SWAGGER` và Application do setup tạo còn hợp lệ**. Chỉ công nhận `SKELETON_QA=PASS/UNCHANGED` sau khi xác thực filesystem thực. Worker tự ghi filename và tạo skeleton; Prime chỉ nghiệm thu, không làm hộ.
3. Không coi `COMPLETED` của worker là bằng chứng độc lập. Khi thiếu bài, chapter QA hoặc skeleton QA lỗi, trùng package/path, ghi rõ tọa độ và đường dẫn thực, xác minh phần đã làm trước khi giao chính worker sửa trong ownership hợp lệ; không retry mù/ghi đè dữ liệu worker khác.
4. **Kiểm tra filename TOÀN TAB ở CUỐI STEP 5**, sau khi worker của **mọi chương** đã điền/readback B cha: đọc lại tất cả A/B cha theo thứ tự, kiểm tra không thiếu filename, regex, tên có nghĩa với A và nhóm bài, không trùng basename **case-insensitive**, không trùng **kebab-case prefix** theo quy tắc Gradle, không dùng tên dành riêng Windows. Không coi worker `COMPLETED` riêng lẻ là đủ để vượt qua kiểm tra uniqueness toàn tab. Có trùng → `BLOCKER: DUPLICATE PARENT FILENAME` nêu chính xác các ô B/tên và **KHÔNG cập nhật J3**.
5. Nếu phát hiện trùng, **chỉ giao lại chính worker sở hữu chương gây trùng** (ưu tiên worker SLEEPING đã dùng, không spawn mới khi còn worker rảnh) với đúng bốn dòng phạm vi **A1/B1, A/B hàng cha, B các hàng con; chỉ B cha được sửa** và worker-only STEP 5. Worker tự chọn filename khác, preflight B cha đang chứa tên cũ chính xác và tự update/readback chỉ B cha; **Prime không tự sửa B**. Đây là **lượt correction riêng CHỈ filename**, không đọc/refactor/ghi lại Knowledge hoặc Difficulty; kết quả worker `COLLISION_FIXED` không tự coi STEP 5 hoàn tất. Việc sửa tên đã ghi **chỉ được phép trong nhiệm vụ khắc phục trùng tên do Prime xác thực/giao lại đúng owner**, không phải quyền ghi đè B thông thường. Nếu không thể giao lại đúng owner hoặc không bảo đảm không xung đột, dừng BLOCKER thay vì ép tên hoặc ghi đè. Kiểm tra lại uniqueness toàn tab sau mọi lần sửa.

### Mẫu giao worker SỬA TRÙNG sau QA cuối — cũng chỉ bốn dòng

```text
<LINK_TAB_ĐÃ_XÁC_MINH>
A1, B1, <PARENT_CELL_A>, <PARENT_FILENAME_CELL_B>, <CHILD_B_RANGE>
<WRITE_ONLY_PARENT_FILENAME_CELL_B_ĐÃ_CÓ_TÊN_TRÙNG>
/java-learning/module-generate-agent/temp/worker/STEP_5_WORKER.md
```

Đây là phạm vi ghi B cha đã được Prime xác minh xung đột, **không phải yêu cầu worker chạy lại toàn bộ STEP 5**, và không ủy quyền đọc/ghi C:N/O/AB của bài con.
6. Tiếp tục điều phối **tất cả các chương** trong phạm vi STEP, không dừng sau batch đầu. Khi **mọi chương có cả Chapter QA + Skeleton QA PASS/UNCHANGED, mọi filename và chapter package đều duy nhất**, Prime **BẮT BUỘC giải quyết sync README đã hoãn ở STEP 3/4**: chạy task **đúng module** `generateModuleReadmeFromSheetByLocation` lần lượt `-PsheetMode=DRY_RUN` → `APPLY` với `-PfileUrl=<URL> -PsheetID=<GID>` đã xác minh theo `GENERAL_AGENT_RULES.md §6`. Importer đồng thời sinh/đồng bộ **`content/readme/metadata/vi/knowledge-metadata.yml`** từ các ID Knowledge (trong H2 anchor) và cột O Difficulty và AB isImportant; cột O trống → dùng `BASIC` nếu chưa có metadata, giữ `aiGenerated=true`, `reviewed=false`, `isImportant=false` mặc định khi chưa có và bảo toàn giá trị chỉnh sửa hợp lệ. Prime kiểm tra **cả Markdown trong `content/readme/docs/vi/menu` và metadata** sau APPLY (Difficulty phản ánh O theo bài, ID khớp, không trùng, không có metadata orphan). **Bất kỳ `SKIPPED_MISSING_FILENAME`, metadata validation lỗi, lỗi import/config đều là BLOCKER**; không dùng ByFiles hoặc task tổng hợp. Chỉ khi cả README, metadata **và toàn bộ skeleton packages** PASS/UNCHANGED Prime mới cập nhật an toàn `J3 = STEP 5` và exact readback. **STEP 6 xây dựng API trên skeleton đã tạo**, STEP 8 Interview YAML, STEP 9 Quiz, STEP 10 Video; không tự chạy.

## 4. Điều kiện hoàn thành STEP 5

- **ĐẦU STEP 5** Prime xác minh module, đặt `MODULE_TYPE=SERVLET`, `BUILD_SWAGGER=TRUE`, Gradle configuration sync thành công, kiểm tra đúng Application duy nhất và không làm hỏng thay đổi ngoài phạm vi **TRƯỚC khi giao worker**.
- Mỗi Menu Cấp 1 được giao cho **đúng một worker chịu trách nhiệm trọn chương và skeleton main/test**, pool không vượt hạn mức Mode A/B của session.
- Worker **đã đọc trọn worker/STEP_5_WORKER.md** và **đọc đầy đủ từng bài** của chương, lập bản đồ nội dung, đánh giá/refactor có căn cứ, QA toàn chương.
- Giảm **lặp gây ảnh hưởng chất lượng** nhưng **không đánh đổi chiều sâu, tính tự học, độ chính xác hoặc ví dụ cần thiết** để giảm số chữ. Không thay đổi bài đang tốt chỉ để sửa.
- Chỉ có các ô **C:N/O/AB hàng con, B hàng cha** và **các thư mục package skeleton main/test của đúng chương đã cấp** được phép thay đổi bởi worker; Markdown, Difficulty, isImportant, filename exact readback; file/config khác bảo toàn. Config module là quyền Prime **chỉ ở bước setup đầu STEP 5**, không phải quyền worker.
- Mỗi hàng Menu Cấp 2 phải có **đúng một Difficulty**: `BASIC` (kiến thức nền), `INTERMEDIATE` (vận dụng/phối hợp và trade-offs), `ADVANCED` (chiều sâu, cơ chế phức tạp, giới hạn/edge cases). Đánh giá dựa trên **nội dung thực sau review**, không đoán theo độ dài bài, thứ tự hay tên file. Ô O hàng Menu Cấp 1 không được ghi.
- Chương chỉ `COMPLETED` khi read coverage đủ, filename B cha hợp lệ/được worker tự ghi, readback từng hàng sửa **PASS**, Chapter QA **PASS**, **skeleton main/test được chính worker tạo và được Prime xác minh SKELETON_QA PASS/UNCHANGED**; lỗi bất kỳ nguồn/ownership/content/config/package phải ghi rõ `BLOCKER/FAILED`.
- STEP 5 toàn tab hoàn thành khi **mọi chương** review/refactor/Difficulty/filename và **skeleton package đều QA PASS**, Prime kiểm tra cuối không trùng filename/prefix/package và **đồng bộ README thật về module qua ByLocation PASS**. STEP 6 mới chỉ xây dựng/nghiệm thu API bằng skeleton do STEP 5 worker tạo, **Swagger info/examples và API mapping thuộc STEP riêng**. STEP 8 Interview, STEP 9 Quiz, STEP 10 Video giữ nguyên phạm vi nghiệp vụ. Không tự chạy STEP khác hoặc commit/push.
