# STEP 11 — Dịch nguyên file VI → ngôn ngữ đích (Prime + typed workers)

**Thứ tự hiện hành:** STEP 10 tạo cây VI→EN lần đầu; STEP 11 lấy nguyên trạng file của ngôn ngữ đích hiện có xuống temp để dịch lần đầu hoặc dịch lại, sau đó publish nguyên file về module; STEP 12 Prime QA và xuất XLSX theo languageList. Không clone/ghi đè bản dịch đang có để bắt đầu dịch lại.

## 0. Prime sở hữu hàng đợi và phân quyền

Prime đọc GENERAL_AGENT_RULES.md và rules/, STEP này, xác minh module và cây file đích được chọn. Prime tạo manifest/journal, **copy nguyên byte các file đang có của ngôn ngữ đích xuống temp bất kể đã dịch hay chưa**, rồi giao độc quyền mỗi worker đúng 1 temp file + 1 typed worker rule. Worker chỉ đọc/sửa file temp rồi dùng task publish nguyên file về module; Prime REPORT/QA_PASS sau khi worker xong. Worker không sửa VI/Sheet/Java/master/journal hoặc commit/deploy.

**Hai mode:**
- `NORMAL MODE` (mặc định): tối đa **3 worker đồng thời**.
- `SPECIAL MODE`: số worker **N phải được user chỉ định rõ khi giao lệnh** trong session. Thiếu N, N không phải số nguyên dương, hay mâu thuẫn → **`BLOCKER: SPECIAL_MODE_WORKER_COUNT_REQUIRED`** (hoặc `WORKER_MODE_CONFLICT`), dừng **trước khi spawn/giao job**; tuyệt đối không dùng mặc định 3. N chỉ áp dụng session đó; không lưu thành cấu hình toàn dự án.

**Worker SLEEPING/Idle được tái sử dụng:** Prime ưu tiên worker idle; mỗi lần giao đúng 1 job = 1 file temp (không gộp Common hay Summary), giới hạn NORMAL tối đa 3 ACTIVE + SLEEPING, SPECIAL theo N được user cho phép. Prime ghi ASSIGN trước khi giao. Worker dịch, chạy publishModuleTranslationFile, báo Prime, nhả ownership và về SLEEPING. Prime REPORT/QA rồi mới được giao job mới; tuyệt đối không có hai worker cùng sửa một file.

## 1. Loại job, số file và file rule

| Job | Đúng **1 file** ngôn ngữ đích (thay `<LANG>` bằng `translationLanguage`) | Rule |
| --- | --- | --- |
| Knowledge | `content/readme/docs/<LANG>/menu/<chapter>/knowledge/<name>.md` (hoặc root Knowledge) | `worker/STEP_11_WORKER/STEP_11_WORKER_KNOWLEDGE.md` |
| Summary | `summary/section.yml` **hoặc** `summary/summaries.yml`; **hai job khác nhau**, vẫn yêu cầu đủ cặp nguồn | `worker/STEP_11_WORKER/STEP_11_WORKER_SUMMARY.md` |
| Common | **Ba job riêng**: `BASE.md`, `content/reference/<LANG>/references.yml`, `content/roadmap/<LANG>/roadmap.yml` khi có nguồn | `worker/STEP_11_WORKER/STEP_11_WORKER_COMMON.md` |
| Quiz | `content/quiz/<LANG>/quiz.yml` | `worker/STEP_11_WORKER/STEP_11_WORKER_QUIZ.md` |
| Interview | `content/interview/<LANG>/interview.yml` | `worker/STEP_11_WORKER/STEP_11_WORKER_INTERVIEW.md` |
| Case Study | `content/casestudy/<LANG>/casestudy.yml` | `worker/STEP_11_WORKER/STEP_11_WORKER_CASE_STUDY.md` |
| Swagger Controller | `swagger/<LANG>/controller-description.yml` | `worker/STEP_11_WORKER/STEP_11_WORKER_SWAGGER_CONTROLLER.md` |
| Swagger Descriptions | `swagger/<LANG>/api-descriptions.yml` | `worker/STEP_11_WORKER/STEP_11_WORKER_SWAGGER_DESCRIPTIONS.md` |
| Swagger Execution | `swagger/<LANG>/api-execution.yml` | `worker/STEP_11_WORKER/STEP_11_WORKER_SWAGGER_EXECUTION.md` |
| Swagger Cases | `swagger/<LANG>/api-cases.yml` | `worker/STEP_11_WORKER/STEP_11_WORKER_SWAGGER_CASES.md` |

Swagger nằm tại `src/main/resources/swagger/<LANG>/`; Introduction là `content/readme/docs/<LANG>/BASE.md`. Worker không dịch `LIST.md`, `knowledge-metadata.yml`, Excel, Java, metadata kỹ thuật, VI hay Video. **Chỉ cần cây ngôn ngữ đích đã tồn tại** (nếu chưa có thì chuẩn bị lần đầu riêng); PREPARE không tự clone, không sửa nội dung module và **không yêu cầu VI == file đích**. Nếu nguồn có Summary thì cần đủ `section.yml` và `summaries.yml` nhưng vẫn giao từng file riêng.

Mỗi rule worker riêng phải ghi **trách nhiệm, file ownership, whitelist, blacklist, cấu trúc bất biến, kiểm tra trước/sau, báo cáo và blocker**. Worker phải đọc đúng **một rule** Prime chỉ định, không tự chọn rule khác.

## 2. Baseline, manifest và preflight

Chạy từ repository root với module Gradle path đã xác minh:

```powershell
.\gradlew.bat :<MODULE_GRADLE_PATH>:prepareModuleTranslation -PtranslationRunId=<RUN_ID> -PtranslationLanguage=en -PtranslationMode=DRY_RUN -PtranslationWorkerMode=NORMAL --console=plain
.\gradlew.bat :<MODULE_GRADLE_PATH>:prepareModuleTranslation -PtranslationRunId=<RUN_ID> -PtranslationLanguage=en -PtranslationMode=APPLY -PtranslationWorkerMode=NORMAL --console=plain
```

Đổi `-PtranslationLanguage=en` thành `ja`, `fr`, ... nếu cây đích đã tồn tại, **dù nội dung đã được dịch trước đó**. Mỗi RUN_ID chỉ có một ngôn ngữ đích; dịch lại sau một run đã hoàn tất phải dùng RUN_ID mới để không ghi đè manifest/temp cũ. SPECIAL cần `-PtranslationWorkerMode=SPECIAL -PtranslationWorkerCount=<N>` ở cả hai lệnh; N phải do user xác nhận.

**Quy tắc bất biến của PREPARE:** DRY_RUN chỉ kiểm kê; APPLY lấy **đúng bytes hiện tại** của mỗi file `<LANG>` (bản VI clone, bản tiếng Anh/Nhật đã dịch, v.v.), ghi checksum và snapshot `baseline/`, rồi copy nguyên byte vào `module-generate-agent/temp/step11/files/<MODULE_PATH>/<RUN_ID>/<RELATIVE_TARGET_PATH>`. Không yêu cầu checksum hay nội dung file đích giống VI; **không đánh giá đã dịch/chưa dịch, không bắt nội dung phải qua QA mới được copy**. Vẫn kiểm cây file, đường dẫn, quyền sở hữu RUN_ID và tránh đè temp/manifest đã tồn tại. Tạo `build/step11/<RUN_ID>/manifest.json` với `baselinePhase=TARGET_SNAPSHOT`, mỗi job một file; Prime khởi tạo journal dựa trên SHA của baseline **chính RUN_ID đó**, không dùng VI làm tiêu chí PENDING. Kiểm tra cấu trúc và giá trị technical là việc của publish/VALIDATE, không chặn bước copy.

### 2A. Checkpoint tạm để Prime khôi phục nếu GPT bị gián đoạn (BẮT BUỘC)

Đường dẫn duy nhất theo module và RUN_ID:

```text
module-generate-agent/temp/step11/<MODULE_ROOT_RELATIVE_PATH>/<RUN_ID>.json
# Ví dụ: module-generate-agent/temp/step11/module/platform/development/programming/paradigm/object-oriented/run-01.json
```

Journal nằm trong Git-ignored temp, **chỉ Prime** đọc/sửa/xóa; worker không được truy cập. Manifest/baseline nằm trong `build/step11/<RUN_ID>/`; `targetLanguage`, jobs và checksum trong manifest là bằng chứng bất biến. Mỗi journal job chỉ chứa **một đường dẫn MODULE_FILE** và owner, trạng thái, SHA tại ASSIGN/REPORT, QA note, revision. Ghi atomic, không lưu nội dung dịch.

Không được khởi tạo RUN_ID mới khi cùng module còn journal chưa được hoàn tất: `PREPARE APPLY` kiểm tra và trả `BLOCKER: STEP11_PROGRESS_OTHER_RUN_ACTIVE`. `workerMode/maxWorkers` trong journal là **bằng chứng về run gốc**, không tự cấp quyền SPECIAL MODE cho session khác: Prime phải đối chiếu lại quyền/giới hạn worker của session hiện tại trước khi spawn.

Prime dùng task module-local `trackModuleTranslation`, với `-PtranslationRunId=<RUN_ID>` trên mọi lệnh:

```powershell
# Xem ngay trước khi dispatch, sau context compaction hoặc lúc resume:
.\gradlew.bat :<MODULE_GRADLE_PATH>:trackModuleTranslation -PtranslationRunId=<RUN_ID> -PtranslationProgressAction=SHOW

# Ghi lại job đang giao TRƯỚC khi spawn worker (revision lấy từ SHOW):
.\gradlew.bat :<MODULE_GRADLE_PATH>:trackModuleTranslation -PtranslationRunId=<RUN_ID> -PtranslationProgressAction=ASSIGN -PtranslationProgressJobId="<JOB_ID>" -PtranslationProgressWorkerId=worker-01 -PtranslationProgressRevision=0

# Worker báo xong, Prime xác minh không còn writer, rồi ghi REPORT:
.\gradlew.bat :<MODULE_GRADLE_PATH>:trackModuleTranslation -PtranslationRunId=<RUN_ID> -PtranslationProgressAction=REPORT -PtranslationProgressJobId="<JOB_ID>" -PtranslationProgressWorkerId=worker-01 -PtranslationProgressRevision=1

# Sau khi Prime QA cấu trúc + ngữ nghĩa PASS mới cập nhật QA_PASS:
.\gradlew.bat :<MODULE_GRADLE_PATH>:trackModuleTranslation -PtranslationRunId=<RUN_ID> -PtranslationProgressAction=QA_PASS -PtranslationProgressJobId="<JOB_ID>" -PtranslationProgressNote="Prime reviewed meaning and technical integrity" -PtranslationProgressRevision=2
```

Revision lấy từ SHOW thực tế; 0/1/2 chỉ minh họa. Tên rule và MODULE_FILE lấy từ manifest; TEMP_FILE tính từ `module-generate-agent/temp/step11/files/<MODULE_PATH>/<RUN_ID>/<MODULE_FILE>`. Prime lấy EXPECTED_TEMP_SHA256 từ checksum file temp trước ASSIGN và EXPECTED_TARGET_SHA256 từ assignedHashes; không tự bịa hash.

| Hành động | Điều kiện và hiệu quả |
| --- | --- |
| `INIT` | Khôi phục file journal **chỉ nếu manifest tồn tại và chưa có EN thay đổi so baseline**; journal đã có thì đọc lại, tuyệt đối không reset |
| `SHOW` (mặc định) | Read-only: in toàn bộ queue, revision, owner, số PENDING/ASSIGNED/REPORTED/QA_PASS/REVIEW/BLOCKER |
| `ASSIGN` | `PENDING → ASSIGNED`, ghi owner/SHA/attempt trước spawn; chặn vượt `maxWorkers` hoặc trùng owner |
| `REPORT` | `ASSIGNED → REPORTED`, chỉ owner đúng mới báo; validate EN liên quan và ghi SHA sau |
| `QA_PASS` | `REPORTED → QA_PASS`; Prime có note QA, file không đổi từ REPORT, structure PASS |
| `SKIP_NO_TRANSLATABLE_TEXT` | Chỉ sau REPORT, nguồn EN không đổi so baseline **và** Prime ghi lý do xác minh thực sự không có field cần dịch |
| `REVIEW` / `BLOCKER` | Đánh dấu job cần xử lý, kèm lý do; không biến thành QA_PASS |
| `REQUEUE` | Chỉ từ ASSIGNED/REPORTED/REVIEW/BLOCKER, bắt buộc note về **worker cũ đã dừng, scope đã được kiểm và không còn writer**; trả về PENDING để giao lại phần còn thiếu, không ghi đè EN |
| `COMPLETE` | **Chỉ sau** tất cả job QA_PASS/SKIP hợp lệ, full `validateModuleTranslation` PASS, source/SHA bất biến và `master.json.CURRENT_STEP.VALUE="11"` exact readback; mới xóa file journal |

Sau compaction/crash, Prime SHOW cùng RUN_ID và kiểm worker còn sống, SHA module/temp. Không PREPARE lại, không ghi đè file temp đang dịch. ASSIGNED phải đối chiếu worker trước khi REPORT hoặc REQUEUE; QA_PASS giữ nguyên. Nếu cần dịch lại file đã publish, Prime REQUEUE có ghi lý do sau khi worker cũ dừng, kiểm file module rồi **tạo lại duy nhất bản temp của file đó từ phiên bản module hiện tại** (không clone lại cả module); ASSIGN ghi hash mới. Không xóa journal trước COMPLETE.

**Quy tắc cleanup khác với file worker contract tạm:** Journal STEP 11 phải **tồn tại cho tới SAU khi Prime ghi/readback checkpoint `"11"`**, vì nếu mất GPT ngay ở gate cuối Prime vẫn cần biết job nào đã QA. Đừng xóa nó theo quy tắc dọn `temp` thông thường trước checkpoint; sau đó `COMPLETE` xóa đúng journal RUN_ID. Lỗi khi xóa → BLOCKER và giữ nguyên bằng chứng, chưa tuyên bố STEP 11 hoàn thành.

### 2B. Worker dịch nguyên file trong temp, sau đó tự publish

**Chỉ có một luồng chính thức:** worker dịch trực tiếp toàn bộ file Markdown/YAML đã được Prime copy xuống temp. Không phân rã nội dung thành segment, không dùng file ánh xạ hoặc placeholder. Prime không yêu cầu worker đọc VI hay module đích.

```text
MODULE: module/platform/development/programming/paradigm/object-oriented
MODULE_FILE: content/readme/docs/en/menu/1.OOPBasic/knowledge/OOPBasic.md
TEMP_FILE: module-generate-agent/temp/step11/files/<MODULE_PATH>/<RUN_ID>/content/readme/docs/en/menu/1.OOPBasic/knowledge/OOPBasic.md
WORKER_RULE: module-generate-agent/worker/STEP_11_WORKER/STEP_11_WORKER_KNOWLEDGE.md
```

Sau khi Prime ASSIGN, worker kiểm SHA ban đầu, dịch toàn bộ TEMP_FILE, readback và QA. Worker chạy **chính task publish một file** dưới đây (task chỉ nhận đường dẫn module-relative đã nằm trong manifest/journal):

```powershell
.\gradlew.bat :<MODULE_GRADLE_PATH>:publishModuleTranslationFile -PtranslationRunId=<RUN_ID> -PtranslationLanguage=en -PtranslationFile="content/readme/docs/en/menu/1.OOPBasic/knowledge/OOPBasic.md" -PtranslationWorkerId=worker-01 -PtranslationMode=DRY_RUN --console=plain
.\gradlew.bat :<MODULE_GRADLE_PATH>:publishModuleTranslationFile -PtranslationRunId=<RUN_ID> -PtranslationLanguage=en -PtranslationFile="content/readme/docs/en/menu/1.OOPBasic/knowledge/OOPBasic.md" -PtranslationWorkerId=worker-01 -PtranslationMode=APPLY --console=plain
```

`publishModuleTranslationFile` là **copy nguyên byte một file**, không phải merge nội dung. Task xác minh đúng worker ASSIGNED, SHA VI/baseline và SHA đích tại ASSIGN, kiểm cấu trúc Markdown/YAML rồi atomic replace đúng file module. Chỉ sau readback thành công mới xóa đúng TEMP_FILE; không đụng temp của worker khác. Nếu fail, giữ temp để worker/Prime kiểm tra. Prime ghi REPORT/QA_PASS sau khi worker báo cáo; task không ghi journal.

Với ngôn ngữ đích **chưa tồn tại**, Prime chuẩn bị cây `<LANG>` từ VI và metadata được bảo vệ trước PREPARE. **Nếu ngôn ngữ đã có, dù module đang `FINAL` hoặc nội dung đã dịch, Prime chỉ copy nguyên trạng**, tuyệt đối không chạy lại STEP 10 để lấy nguồn VI. Đặt RUN_ID mới cho mỗi lần dịch lại, không ghi đè run đang hoạt động, không hạ `CURRENT_STEP` khi chỉ chuẩn bị temp.

## 3. Prime giao việc — file + worker rule chính xác

```text
MODULE=<VERIFIED_I3> | MODULE_GRADLE_PATH=<VERIFIED_GRADLE_PATH> | RUN_ID=<MANIFEST_RUN_ID> | JOB_ID=<MODULE_FILE>
TARGET_LANGUAGE=<en|ja|fr|...> | OWNER=<WORKER_ID> | MODE=<NORMAL|SPECIAL>
TEMP_FILE=<ABSOLUTE_TEMP_FILE> | MODULE_FILE=<MODULE_RELATIVE_TARGET_FILE> | EXPECTED_TEMP_SHA256=<SHA256>
WORKER_RULE=/java-learning/module-generate-agent/worker/STEP_11_WORKER/STEP_11_WORKER_<TYPE>.md
```

Prime giao **duy nhất một TEMP_FILE nguyên bản và một WORKER_RULE** kèm ngôn ngữ đích, JOB_ID (bằng MODULE_FILE), EXPECTED_TEMP_SHA256, WORKER_ID. Worker chỉ đọc rule và temp; không tự đọc/sửa module đích hay journal. Prime ASSIGN trước khi giao, REPORT sau khi publish và worker báo cáo, QA_PASS sau kiểm dịch nghĩa.

## 4. Quy tắc chung mọi worker

Dịch đủ **toàn bộ nội dung người đọc thấy** thuộc whitelist ngay trong file temp Markdown/YAML, không extract thành đoạn, không dùng placeholder hay file ánh xạ. Giữ nguyên YAML ID/key/count, Java code, fence/inline code, HTTP, URL, anchor, metadata, Markdown cấu trúc, bảng và thứ tự. Nếu khó xác định whitelist/giá trị, báo REVIEW, không sửa sang module theo cảm tính.

Worker kiểm SHA file temp trước khi sửa, dịch toàn file, kiểm cấu trúc/readback, chạy `publishModuleTranslationFile` DRY_RUN rồi APPLY để task kiểm source/target/ownership/structure và **copy nguyên file vào module**. Thành công task xóa đúng temp file đã publish; thất bại giữ temp. Worker báo Prime SHA trước/sau và QA, rồi về SLEEPING. Prime độc quyền REPORT/QA_PASS và nhật ký.

## 5. Prime QA và completion

Prime chạy:

```powershell
.\gradlew.bat :<MODULE_GRADLE_PATH>:validateModuleTranslation -PtranslationRunId=<RUN_ID> -PtranslationLanguage=<LANG> --console=plain
```

VALIDATE đọc VI, baseline và file đích `<LANG>` để kiểm cấu trúc/technical identity; `PENDING` chưa đổi từ baseline, `STRUCTURE_PASS_NEEDS_SEMANTIC_QA` chưa chứng minh dịch đúng nghĩa. Prime QA ngữ nghĩa độc lập; báo cáo worker hoặc chỉ hết queue không đồng nghĩa PASS.

Chỉ sau khi **mọi job** có `QA_PASS` hoặc `SKIP_NO_TRANSLATABLE_TEXT` hợp lệ **trong journal**, không còn worker/writer/PENDING/REVIEW/BLOCKER, VI/Sheet/Java/Video bất biến và cấu trúc/YAML/relations/semantics PASS, Prime mới ghi **duy nhất** `master.json.CURRENT_STEP.VALUE="11"` và exact readback. Sau đó bắt buộc chạy:

```powershell
.\gradlew.bat :<MODULE_GRADLE_PATH>:trackModuleTranslation -PtranslationRunId=<RUN_ID> -PtranslationProgressAction=COMPLETE -PtranslationProgressRevision=<CURRENT_REVISION>
```

COMPLETE kiểm manifest, source/target và checkpoint rồi mới xóa journal. Nếu lỗi giữ journal, không tuyên bố hoàn thành. Không tự chạy STEP 12/commit/push/deploy. Báo MODULE_I3,GID,RUN_ID,TARGET_LANGUAGE,MODE,MAX_WORKERS,TOTAL_FILES,TOTAL_JOBS,QA_PASS,PENDING,REVIEW,BLOCKER,STRUCTURE_QA,SEMANTIC_QA,PROGRESS_REVISION,PROGRESS_CLEANUP,CURRENT_STEP_READBACK.
