# STEP 10 — PRIME ONLY: Clone toàn bộ nội dung VI → EN (không dùng worker)

> **Mục tiêu mới:** STEP 10 thay thế nhiệm vụ Quiz legacy trước đây và tiếp nhận chức năng clone VI → EN của STEP 13 cũ. Đây là thao tác sao chép file thuần code, **không gọi AI, không phân công worker, không biên soạn hay dịch nội dung**.
>
> **Trạng thái triển khai:** Task module-local `cloneModuleViToEn` đã được cập nhật để tự phát hiện các subtree `vi/` thuộc `content/` (trừ `sheet/`) và Swagger, CLEAN → FILL toàn bộ thư mục EN, kiểm tra readback rồi xóa backup/staging tạm. Không sử dụng lại hành vi clone STEP 13 cũ.

## 0. Vị trí trong workflow và quyền sở hữu

- Thực hiện **sau STEP 9** (Swagger VI và các nguồn VI thuộc bước trước đã nghiệm thu), **trước STEP 11** (GPT Worker dịch EN). STEP 10 chỉ tạo bộ file EN ban đầu; STEP 11 dịch nội dung hiển thị, STEP 12 QA kỹ thuật/ngữ nghĩa rồi xuất Excel offline từ module.
- **Prime-only**: Prime kiểm kê, chạy đúng một Gradle task module-local theo DRY_RUN → APPLY, kiểm tra manifest/readback và **bắt buộc cập nhật `master.json.CURRENT_STEP.VALUE = "10"` sau khi toàn bộ gate PASS**. **Không có worker STEP 10**, không sử dụng worker/STEP_10_WORKER.md.
- **Không tạo Quiz mới**. Quiz module-wide đã thuộc STEP 6/7 tại content/quiz/vi/quiz.yml. Không gọi normalizeQuizQuestionsFromSheet hay generateModuleQuizFromSheetByLocation của luồng legacy STEP 10.
- Không gọi Sheets API, không sửa Google Sheets, Excel, Java source/test, master.json ngoài CURRENT_STEP sau QA, tài nguyên Portal đã generate hoặc module khác.
- Tất cả thao tác theo module được xác định bởi **I3 và Gradle path thật**. Nếu không xác minh được module I3 thì BLOCKER; không đoán theo tên tab.

## 1. Phạm vi clone — lấy toàn bộ VI, chỉ loại trừ content/sheet

**Quy tắc tổng quát — CLEAN → FILL:** Với mỗi **thư mục `vi/` nguồn** dưới content/ ngoại trừ content/sheet/, xác định thư mục `en/` tương ứng; sau khi preflight và staging đầy đủ, **xóa/thay thế toàn bộ cây EN đích**, rồi copy **toàn bộ cây VI** sang EN, giữ nguyên cấu trúc/tên file. **Không giữ lại bất kỳ file/folder EN cũ nào** trong thư mục được migrate, kể cả file EN-only hoặc bản dịch trước đó. Clone đệ quy, không giới hạn theo tên folder hay đuôi file. Tương tự cho toàn bộ `src/main/resources/swagger/vi/` → `swagger/en/`. Chỉ dùng backup **tạm thời để rollback trong transaction**; khi thành công phải **xóa sạch backup và staging của lượt chạy trước khi task trả về**, không giữ bản lưu lịch sử. Không dùng cơ chế merge từng file.

| Nguồn VI | Đích EN | Phạm vi |
| --- | --- | --- |
| content/readme/docs/vi/** | content/readme/docs/en/** | BASE, LIST, Knowledge, metadata chương, Summary, Quiz/Interview legacy nếu thực sự tồn tại, Video và file phụ |
| content/readme/metadata/vi/** | content/readme/metadata/en/** | Metadata cấp module, kể cả cấu trúc cũ |
| content/roadmap/vi/** | content/roadmap/en/** | Roadmap và mọi file con |
| content/reference/vi/** | content/reference/en/** | Reference riêng theo ngôn ngữ |
| content/interview/vi/** | content/interview/en/** | Interview module-wide theo group của STEP 6 |
| content/casestudy/vi/** | content/casestudy/en/** | Case Study module-wide theo group của STEP 6 |
| content/quiz/vi/** | content/quiz/en/** | Quiz module-wide theo group của STEP 6 |
| content/<other>/vi/** | content/<other>/en/** | Tự khám phá mọi nhánh content mới, không hardcode whitelist |
| src/main/resources/swagger/vi/** | src/main/resources/swagger/en/** | Toàn bộ Swagger canonical: controller-description, api-descriptions, api-execution, api-cases và file mới nếu có |
| content/sheet/** | **Không có** | Loại trừ hoàn toàn, bao gồm XLSX và publish-state |

**Nguyên tắc không bỏ sót:**

- Kiểm kê **toàn bộ cây content/** trước, không chỉ bốn vùng OWNED của hàm clone legacy trước đây; các nhánh mới phải được phát hiện và xuất hiện trong manifest.
- Các đường dẫn không nằm dưới vi/en, ví dụ content/readme/docs/BASE.md hoặc LIST.md cũ, **không thể suy ra bản dịch bằng cách đổi segment**. Prime phải liệt kê chúng là UNLOCALIZED. File dùng chung thật sự được giữ nguyên; file thuộc nội dung cần dịch phải được phân loại/di trú rõ trước APPLY, không âm thầm bỏ qua hay tùy tiện nhân đôi.
- Chỉ clone nguồn **canonical**. Không đưa staging như src/main/resources/swagger/temp/, build/, thư mục ẩn/temporary hoặc tài liệu generated ngoài nguồn vào EN. Các file này được giữ nguyên.
- **Video không bị loại trừ nữa:** nếu VI Video đã tồn tại tại STEP 10 thì clone. Video không bắt buộc có vì vẫn được tạo riêng ở STEP_OPTIONAL_VIDEO sau FINAL; thiếu VI Video không được tự tạo placeholder. **Nếu Video EN cũ nằm trong thư mục docs/en được migrate mà VI không có Video tương ứng, file Video EN cũ cũng bị CLEAN**, đúng quy tắc không giữ file dư; chỉ backup để phục hồi nếu transaction lỗi.
- Không tạo file giả cho source thiếu. Không gộp ba assessment module-wide vào các Quiz/Interview YAML legacy theo chương; giữ nguyên định dạng group và nội dung từng file.
- Bảo toàn **toàn bộ bytes** của file nguồn. Ngoại lệ kỹ thuật duy nhất: content/readme/docs/en/LIST.md có thể thay chính xác các link trỏ đến content/readme/docs/vi/menu/ bằng en/menu/ và nhãn cấu trúc VI → EN theo quy tắc xác định sẵn; không sửa câu chữ bài học. Ghi rõ phép biến đổi này trong manifest.
- File EN vừa clone **vẫn chứa tiếng Việt**. Đây là bản staging cho STEP 11 Translation, không phải nội dung EN đã dịch và không được đánh dấu TRANSLATED.

## 2. Preflight bắt buộc trước khi chạy

1. Prime đọc GENERAL_AGENT_RULES.md, các rules/ liên quan, STEP_9.md, STEP_10.md và module-generate-info.txt; kiểm đúng module I3, master.json (SERVICE_NAME, MODULE_LANGUAGE, CURRENT_STEP), Gradle project và nguồn VI đã có. Không lấy một lần đọc Sheet làm bằng chứng thay cho file thật.
2. Xác nhận STEP 9 và mọi writer VI trước đó đã kết thúc. Snapshot danh sách file nguồn, kích thước, SHA-256 và đích tương ứng; xác nhận không có worker/tiến trình khác đang sửa VI/EN.
3. Kiểm tra quyền ghi, file thường, symlink/junction/reparse point, path traversal, tên file đụng nhau khi thay vi → en, đường dẫn thoát module, và các thư mục đích sai kiểu. Bất kỳ path không an toàn → BLOCKER trước khi ghi.
4. Kiểm tra **tất cả nhánh dưới content/** để không bỏ sót nhóm mới; thống kê các file UNLOCALIZED, các file đang ở en mà không có VI (EN_ONLY), và các file trong content/sheet/ phải được loại trừ. **EN_ONLY nằm trong thư mục EN được migrate là dữ liệu sẽ bị CLEAN**, không được giữ lại.
5. So sánh **toàn bộ cây EN đích** với snapshot clone dự kiến; lập manifest rõ ràng:
   - **MISSING:** chưa có EN → tạo sau staging.
   - **UNCHANGED:** cả cây EN (không chỉ các file trùng tên) khớp snapshot clone → không ghi lại.
   - **DIFFERENT:** EN khác VI/khác phép chuyển LIST cho phép → **được thay hoàn toàn bằng bản VI** theo chủ đích STEP 10; phải báo rõ file sẽ bị thay, backup tạm thời EN cũ để rollback rồi **xóa backup sau khi clone PASS**.
   - **EN_ONLY:** file không có VI tương ứng **trong cùng thư mục đang migrate** → **sẽ bị xóa sạch** khi thay subtree; file ngoài subtree scope không được đụng tới.
6. Nếu EN đã dịch tại STEP 11 hoặc module đã qua STEP 11–12, preflight phải cảnh báo **CLEAN sẽ mất bản dịch hiện tại**, ghi manifest và backup tạm thời để rollback; **không có backup được giữ sau khi thành công**. Không tự chạy lại STEP 10 sau khi bắt đầu dịch như một tác vụ nền; chỉ chạy lại khi có chỉ thị rõ về reset EN và không được ghi lùi CURRENT_STEP.
7. Nếu source VI thiếu phần bắt buộc của workflow trước đó → BLOCKER ở bước sở hữu nguồn. Nếu một nhóm optional chưa có thì NOT_APPLICABLE, không tự tạo.
8. DRY_RUN phải trình bày chính xác source/target, số file theo nhánh, bytes, hash, phép đổi LIST (nếu có), MISSING/UNCHANGED/DIFFERENT/EN_ONLY/UNLOCALIZED, **danh sách toàn bộ thư mục EN sẽ bị CLEAN**, cảnh báo và blocker. **DRY_RUN tuyệt đối không ghi file**.

## 3. Chạy một hàm Gradle — không giao worker

Chạy task `cloneModuleViToEn` phiên bản STEP 10 từ root java-learning với **đúng một module**:

```powershell
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:cloneModuleViToEn -PsheetMode=DRY_RUN --console=plain
```

Chỉ khi DRY_RUN PASS, cơ chế rollback bằng backup tạm thời đã sẵn sàng, phạm vi CLEAN đúng module và **không còn blocker** (DIFFERENT/EN_ONLY trong thư mục EN đích **không phải blocker**, vì sẽ bị thay/xóa có chủ đích), Prime mới chạy:

```powershell
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:cloneModuleViToEn -PsheetMode=APPLY --console=plain
```

**Hợp đồng của hàm:**

1. Chạy hoàn toàn bằng Gradle/Groovy và filesystem; **không worker, không AI, không Google Sheets, không dịch YAML/Markdown**.
2. Phát hiện tự động các thư mục vi dưới content/ (trừ sheet/) và Swagger canonical, lập manifest đích một-một.
3. Preflight **toàn bộ** rồi stage **đầy đủ cây EN mới** vào <MODULE>/build/step10-staging/<RUN_ID>/; kiểm lại checksum nguồn và đích trước commit. **Không xóa trước rồi mới đi tìm source.**
4. Sao lưu **tạm thời nguyên thư mục EN đích cũ** vào <MODULE>/build/step10-backups/<RUN_ID>/, bao gồm file DIFFERENT và EN_ONLY; chỉ dùng để rollback trong transaction, không giữ lại sau khi thành công. Không chạm ngoài những thư mục EN nằm trong manifest migrate.
5. **CLEAN toàn bộ thư mục EN đích → FILL bằng nguyên thư mục đã stage** như một transaction. Không giữ file/folder EN cũ, kể cả file authored ngoài danh sách file VI bên trong subtree đó. Nếu thất bại giữa chừng, rollback **nguyên subtree** từ backup; báo rõ nếu rollback chưa đầy đủ.
6. Readback chính xác cây file/folder và bytes/SHA-256: **EN sau APPLY phải bằng đúng snapshot VI** (trừ phép đổi LIST được ghi lại), **không có file dư**. Kiểm không chạm nguồn VI, content/sheet/, Java hoặc Swagger temp.
7. **Bắt buộc dọn dẹp:** sau readback PASS phải xóa **toàn bộ** `build/step10-backups/<RUN_ID>/` và `build/step10-staging/<RUN_ID>/` **trước khi hàm trả về `CLONED`**, xác minh hai đường dẫn không còn tồn tại. Không giữ backup sau thành công, dù có DIFFERENT/EN_ONLY. Nếu cleanup thất bại: báo BLOCKER `STEP10_TEMP_CLEANUP`, không tuyên bố COMPLETED và không cập nhật CURRENT_STEP. Nếu transaction lỗi thì rollback và xóa dữ liệu tạm sau khi rollback đầy đủ; **chỉ giữ bản phục hồi nếu rollback thất bại**, báo rõ đường dẫn và `ROLLBACK_INCOMPLETE` để tránh mất dữ liệu không thể khôi phục.
8. Chạy lại khi nguồn không đổi và toàn bộ cây EN đã khớp phải **UNCHANGED/idempotent**, không tạo backup mới; nếu EN thay đổi sau clone thì lần rerun được ủy quyền sẽ CLEAN và thay lại, không merge.

**Kiểm tra bắt buộc:** nếu task được chạy từ bản code cũ chỉ clone danh sách `OWNED` của STEP 13, dùng backup `step13-backups`, hoặc giữ lại Video/EN-only thì phải coi là sai contract STEP 10 và dừng APPLY.

## 4. Completion gate — Prime-only

Chỉ cập nhật CURRENT_STEP.VALUE = "10" khi **đồng thời** đạt:

- Module I3/path/master đã được kiểm tra; STEP 9 và source VI đủ điều kiện.
- DRY_RUN = PASS; APPLY = CLONED hoặc UNCHANGED; **toàn bộ thư mục EN nằm trong phạm vi được CLEAN → FILL đúng bản VI**, không còn file dư hoặc blocker.
- Manifest bao phủ **toàn bộ VI trong content/ ngoài sheet/** và Swagger canonical; các file ngoài vùng ngôn ngữ được thống kê/xử lý minh bạch.
- Danh sách file đích đầy đủ, sha256/readback PASS; LIST chỉ thay đúng những link kỹ thuật cho phép.
- Không thay đổi content/sheet/, VI, Java/test, nguồn Swagger VI, Swagger staging, dữ liệu Portal, module khác và **mọi file EN nằm ngoài subtree được migrate**. File EN_ONLY **bên trong** subtree được migrate phải bị xóa.
- Rollback đã được kiểm thử/QA; **không còn thư mục backup hay staging của lần chạy STEP 10** sau `CLONED` hoặc `UNCHANGED`, không còn writer đang hoạt động. Nếu rollback thất bại thì chưa hoàn tất STEP và phải giữ dữ liệu phục hồi để xử lý lỗi.
- **BẮT BUỘC:** Sau toàn bộ QA/DRY_RUN/APPLY/readback PASS và không còn blocker, **Prime phải cập nhật duy nhất `master.json.CURRENT_STEP.VALUE = "10"`** trên đúng module, rồi exact readback. **Không được báo STEP 10 hoàn tất nếu mới copy xong nhưng chưa ghi/readback "10".** Gradle task không tự ghi tiến độ. Không ghi lùi nếu module đã ở STEP cao hơn.

Báo cáo tối thiểu: MODULE_I3, GRADLE_PATH, SOURCE_STEP, VI_TOTAL, SCOPES, FILES_BY_SCOPE, UNLOCALIZED, EN_ONLY, MISSING, DIFFERENT, UNCHANGED, CLONED, SOURCE_SHA256, TARGET_SHA256, LIST_REWRITE, DRY_RUN, APPLY, TEMP_BACKUP_CLEANED, TEMP_STAGING_CLEANED, ROLLBACK, READBACK, CURRENT_STEP_VALUE_READBACK, GIT_STATUS, BLOCKERS. `BACKUP_PATH` chỉ báo nếu có lỗi rollback chưa hoàn tất, không phải artifact đầu ra khi thành công.

**Không tự commit, push, deploy hoặc chuyển STEP 11/14 khi chỉ mới DRY_RUN.**

## 5. Ranh giới với STEP 11–12 và workflow optional

- **STEP 11:** dịch các field hiển thị của toàn bộ nội dung EN bằng GPT worker theo job và rule chuyên biệt; không dịch ID, group/index, anchorList, đáp án đúng, Java code, URL hoặc Swagger technical fields. **Không gọi lại cloneModuleViToEn sau khi dịch bắt đầu.**
- **STEP 12:** Prime QA technical relations and VI/EN semantics, then one module-local `generateModuleExcel` task creates offline XLSX from canonical module (Video X:AA reserved blank). No Sheet exporter/publisher.
- **STEP_OPTIONAL_VIDEO và STEP_OPTIONAL_VIDEO_TRANSLATE:** khi tạo Video VI sau FINAL thì sử dụng workflow optional độc lập để seed EN thiếu và dịch; **không chạy lại clone toàn module** vì cơ chế CLEAN của STEP 10 sẽ xóa mọi bản dịch EN thuộc thư mục được migrate.
- STEP 13/14/15 documentation was retired; never use their former Sheet snapshot or online EN publishing flows. Export at STEP 12 is module→XLSX only.

**Kết quả STEP 10:** tạo bản EN cấu trúc đầy đủ theo snapshot VI (nếu có), CLEAN những EN-only/bản dịch cũ nằm trong các thư mục đích được chọn, bảo toàn dữ liệu **ngoài scope** và nguồn VI, bàn giao cho STEP 11 Translation. Không dịch, không tạo nội dung mới, không cần worker.
