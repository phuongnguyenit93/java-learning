# STEP 11 — Translation VI → EN (Prime + typed workers)

**Thứ tự mới:** STEP 10 clone VI→EN; **STEP 11 dịch EN**; STEP 12 enrich relation; STEP 13 export VI Excel; STEP 14 QA VI/EN read-only; STEP 15 publish EN. Không chạy lại clone sau khi dịch EN.

## 0. Prime sở hữu hàng đợi và phân quyền

Prime đọc `GENERAL_AGENT_RULES.md`, toàn bộ `rules/`, STEP này, xác minh module qua Sheet gid/I3 và STEP 10 đã PASS thật + `master.json.CURRENT_STEP.VALUE="10"`. Chỉ Prime lập inventory/manifest, tạo job, giao file + rule, nhận báo cáo, QA toàn bộ và cập nhật `CURRENT_STEP.VALUE="11"` sau khi hoàn thành. Worker **không** tự chọn file/đọc module khác, không tự nhận job mới, không ghi VI/Sheet/Java/master hoặc commit/deploy.

**Hai mode:**
- `NORMAL MODE` (mặc định): tối đa **3 worker đồng thời**.
- `SPECIAL MODE`: số worker **N phải được user chỉ định rõ khi giao lệnh** trong session. Thiếu N, N không phải số nguyên dương, hay mâu thuẫn → **`BLOCKER: SPECIAL_MODE_WORKER_COUNT_REQUIRED`** (hoặc `WORKER_MODE_CONFLICT`), dừng **trước khi spawn/giao job**; tuyệt đối không dùng mặc định 3. N chỉ áp dụng session đó; không lưu thành cấu hình toàn dự án.

Prime spawn từng worker một, kiểm tra trạng thái rồi mới spawn tiếp, không vượt số ACTIVE đồng thời. **Worker xử lý xong một job → báo cáo Prime và kết thúc**; không tự điền job tiếp theo, không tiếp tục ở SLEEPING để chờ lệnh. Prime QA job, giải phóng ownership, rồi giao lượt kế tiếp từ hàng đợi (worker/phiên mới), cho đến khi hết tất cả job. Nếu worker FAILED, chỉ giao lại phạm vi lỗi sau khi kiểm tra hash/ownership.

## 1. Loại job, số file và file rule

| Job | Phạm vi EN độc quyền | Rule |
| --- | --- | --- |
| Knowledge | **1** `content/readme/docs/en/menu/<chapter>/knowledge/<name>.md` (hoặc legacy root Knowledge nếu có) | `worker/STEP_11_WORKER_KNOWLEDGE.md` |
| Summary | **2 file cùng Menu Cấp 1**: `summary/section.yml` và `summary/summaries.yml` | `worker/STEP_11_WORKER_SUMMARY.md` |
| Common | **1 job**, gồm `BASE.md` + `content/reference/en/references.yml` + `content/roadmap/en/roadmap.yml` (nếu nguồn VI có) | `worker/STEP_11_WORKER_COMMON.md` |
| Quiz | **Toàn bộ** `content/quiz/en/quiz.yml` | `worker/STEP_11_WORKER_QUIZ.md` |
| Interview | **Toàn bộ** `content/interview/en/interview.yml` | `worker/STEP_11_WORKER_INTERVIEW.md` |
| Case Study | **Toàn bộ** `content/casestudy/en/casestudy.yml` | `worker/STEP_11_WORKER_CASE_STUDY.md` |
| Swagger Controller | **1** `swagger/en/controller-description.yml` | `worker/STEP_11_WORKER_SWAGGER_CONTROLLER.md` |
| Swagger Descriptions | **1** `swagger/en/api-descriptions.yml` | `worker/STEP_11_WORKER_SWAGGER_DESCRIPTIONS.md` |
| Swagger Execution | **1** `swagger/en/api-execution.yml` | `worker/STEP_11_WORKER_SWAGGER_EXECUTION.md` |
| Swagger Cases | **1** `swagger/en/api-cases.yml` | `worker/STEP_11_WORKER_SWAGGER_CASES.md` |

Swagger thuộc `src/main/resources/swagger/en/`. Introduction `BASE.md` thuộc `content/readme/docs/en/`. Không dịch `LIST.md`, `knowledge-metadata.yml`, Excel, metadata kỹ thuật, Java/test, VI, hay Video (Video thuộc `STEP_OPTIONAL_VIDEO_TRANSLATE` sau FINAL). Không dùng Quiz/Interview legacy per-chapter thay cho ba YAML module-wide của STEP 6/7.

Mỗi rule worker riêng phải ghi **trách nhiệm, file ownership, whitelist, blacklist, cấu trúc bất biến, kiểm tra trước/sau, báo cáo và blocker**. Worker phải đọc đúng **một rule** Prime chỉ định, không tự chọn rule khác.

## 2. Baseline, manifest và preflight

Chạy từ repository root với module Gradle path đã xác minh:

```powershell
.\gradlew.bat :<MODULE_GRADLE_PATH>:prepareModuleTranslation -PtranslationRunId=<RUN_ID> -PtranslationMode=DRY_RUN -PtranslationWorkerMode=NORMAL --console=plain
.\gradlew.bat :<MODULE_GRADLE_PATH>:prepareModuleTranslation -PtranslationRunId=<RUN_ID> -PtranslationMode=APPLY -PtranslationWorkerMode=NORMAL --console=plain
```

Nếu user chọn SPECIAL và đã nêu N thì thay `-PtranslationWorkerMode=SPECIAL -PtranslationWorkerCount=<N>` trong **cả hai lệnh**. Mode SPECIAL thiếu N phải bị chặn từ bước dispatch, không thực thi âm thầm như NORMAL.

DRY_RUN chỉ kiểm kê; APPLY chỉ tạo `build/step11/<RUN_ID>/manifest.json` với `files[]`, `jobs[] (id,rule,files)`, `workerMode,maxWorkers` và snapshot `baseline/` (không dịch/ghi EN). Kiểm source VI/EN bytes sau STEP 10, cặp file, schema và SHA-256; `LIST.md` là protected exception do STEP 10 sửa link sang EN. Nếu manifest tồn tại, **không PREPARE lại** hoặc clone lại; resume từ baseline và trạng thái job. VI drift/ownership chồng chéo/thiếu cặp Summary → BLOCKER.

## 3. Prime giao việc — file + worker rule chính xác

```text
MODULE=<VERIFIED_I3> | RUN_ID=<MANIFEST_RUN_ID> | JOB_ID=<MANIFEST_JOB_ID>
MODE=<NORMAL|SPECIAL> | MAX_WORKERS=<N> | OWNER=<WORKER_ID>
EN_FILES=<FULL_ABSOLUTE_EN_PATH_LIST_FROM_JOB> | EXPECTED_SHA256=<ONE_HASH_PER_FILE>
WORKER_RULE=/java-learning/module-generate-agent/worker/STEP_11_WORKER_<TYPE>.md
```

Prime cấp **đường dẫn đầy đủ**, không phải `...` hoặc tên folder để worker tự tìm. Mỗi job có **duy nhất một rule**. Quyền đọc/ghi EN chỉ trong `EN_FILES`; worker được đọc toàn văn rule được giao. EN chứa bản VI clone là nguồn để dịch. Không cấp quyền worker ghi manifest/backup, file ngoài scope hay VI. Prime quản lý ledger `PENDING → ASSIGNED → REPORTED → QA_PASS` hoặc `REVIEW/BLOCKER` và SHA trước/sau.

## 4. Quy tắc chung mọi worker

Dịch đầy đủ **mọi field hiển thị thuộc whitelist** theo rule; không rút gọn/thêm ví dụ/sửa nguồn VI hoặc suy diễn đáp án. Giữ nguyên mọi YAML key/root/ID/groupID/anchorList, correctAnswerId, Java code fence/inline code/class/method/path, link URL, relation, technical request/response/status, metadata, table/list/heading và số item/thứ tự. Câu/đoạn không rõ → REVIEW cho Prime. File lớn xử lý tuần tự từng section/group/Controller **trong ownership độc quyền của worker đó**, ghi/checkpoint an toàn không mất nội dung.

Worker parse/readback **toàn bộ từng file**, kiểm tra schema/cấu trúc/ký tự còn tiếng Việt trong whitelist, checksum protected fields và coverage `TRANSLATABLE_ITEMS/TRANSLATED_ITEMS`; báo `COMPLETED/REVIEW/BLOCKER`, SHA trước/sau và lỗi. **Báo Prime rồi kết thúc**, không tự mở job khác hoặc nâng master.

## 5. Prime QA và completion

Prime chạy:

```powershell
.\gradlew.bat :<MODULE_GRADLE_PATH>:validateModuleTranslation -PtranslationRunId=<RUN_ID> --console=plain
```

Task read-only so VI, baseline và EN technical identity. `PENDING` = chưa dịch; `STRUCTURE_PASS_NEEDS_SEMANTIC_QA` = **chưa** chứng minh đúng nghĩa; Prime QA riêng dịch không sai/thiếu ý và thuật ngữ Java. Quiz 86, Interview 120, Case Study 156 chỉ là số lượng OOP minh họa, không hardcode cho module khác. Worker báo xong hoặc queue trống chưa phải PASS.

Chỉ sau khi **mọi job** có `QA_PASS` hoặc `SKIP_NO_TRANSLATABLE_TEXT` hợp lệ, không còn worker/writer/PENDING/REVIEW/BLOCKER, VI/Sheet/Java/Video bất biến và cấu trúc/YAML/relations/semantics PASS, Prime mới ghi **duy nhất** `master.json.CURRENT_STEP.VALUE="11"` và exact readback. Không tự chạy STEP 12, commit/push/deploy. Báo `MODULE_I3,GID,RUN_ID,MODE,MAX_WORKERS,TOTAL_FILES,TOTAL_JOBS,QA_PASS,PENDING,REVIEW,BLOCKER,STRUCTURE_QA,SEMANTIC_QA,CURRENT_STEP_READBACK`.
