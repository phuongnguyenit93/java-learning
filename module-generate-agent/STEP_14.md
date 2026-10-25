# STEP 14 — Translation VI → EN (Prime + Worker)

> **Quy trình:** Hoàn tất preflight gate của STEP 12A → lập catalog/backup → giao worker **mỗi lượt một file** → Prime QA từng file → QA toàn module → J3 readback. Whitelist/blacklist nằm trong `worker/STEP_14_WORKER.md`. Không thực thi STEP 15 khi STEP 14 chưa hoàn tất.

## 0. PRE-FLIGHT GATE — BẮT BUỘC trước khi bắt đầu STEP 14

**Prime chịu trách nhiệm nghiệm thu STEP 12A trước khi giao bất kỳ chương nào cho worker dịch.** Đây là một **hard gate**: chỉ cần một hạng mục FAIL/UNKNOWN thì **KHÔNG giao worker, KHÔNG viết EN, KHÔNG cập nhật J3 sang STEP 14**.

Trước khi kiểm tra, Prime đọc **toàn văn** `module-generate-agent/GENERAL_AGENT_RULES.md`, liệt kê/đọc **toàn bộ** `module-generate-agent/rules/`, đọc STEP 14 này, `STEP_12A.md`, `module-generate-info.txt`; xác định đúng module từ **I3 + workbook/gid đã xác minh**, không suy luận từ tên tab. Đọc kết quả thực thi STEP 12A, không tin trạng thái J3 một cách đơn độc.

### Gate 1 — Đối chiếu toàn bộ cấu trúc VI/EN

- So sánh **chính xác danh sách file và đường dẫn tương đối** của các subtree được STEP 12A quản lý:
  - `content/readme/docs/{vi,en}/` (`BASE.md`, `LIST.md`, `menu/**`).
  - `content/roadmap/{vi,en}/`.
  - `src/main/resources/swagger/{vi,en}/`.
- Kiểm tra thứ tự/chỉ số Menu Cấp 1, tên folder/filename, chapter và section Knowledge. Mỗi file Markdown EN phải có **cùng tập và thứ tự anchor ID** với VI; `menu/<chapter>/metadata/knowledge-metadata.yml` phải khớp bytes/anchor/giá trị kỹ thuật (`difficulty`, `isImportant`, `aiGenerated`, `reviewed`). `menu/<chapter>/{quiz,interview}/question.yml` thuộc cùng cây docs.
- Vì STEP 12A chỉ clone, **nội dung EN tại thời điểm này vẫn là tiếng Việt**. Đối chiếu byte/hash VI/EN cho mọi file nằm trong scope, **ngoại trừ `LIST.md`**: ở EN, chỉ header ngôn ngữ và đích link `content/readme/docs/vi/menu/` → `content/readme/docs/en/menu/` được khác.
- Nếu một khả năng thuộc phạm vi bắt buộc không có nguồn VI (ví dụ Quiz không được tạo), xác minh vùng EN tương ứng **không còn dữ liệu cũ**. **Video nằm ngoài ownership của STEP 12A/14**; không kiểm kê, xóa, clone hay dịch Video trong bước này.

**FAIL:** file thiếu/thừa, chapter sai thứ tự, anchor đổi, metadata lệch, EN chưa phải clone hoặc dữ liệu EN cũ còn sót.

### Gate 2 — Quiz/Interview giữ nguyên cấu trúc và relation

- Đối chiếu `questions[]` giữa VI và EN: **cùng số lượng, cùng thứ tự và cùng nội dung tại thời điểm clone**.
- **Quiz:** bảo toàn `id` từng câu, thứ tự bốn đáp án `A/B/C/D`, `correctAnswerId`, `explanation`, `aiGenerated`, `reviewed`, `readmeRelated` và `apiRelated`. Không tái tạo ID mới.
- **Interview:** bảo toàn thứ tự và toàn bộ câu hỏi/câu trả lời, metadata và relation. Interview không có ID câu hỏi bắt buộc như Quiz; đối chiếu **theo vị trí + chapter chứa file question.yml** (kèm fingerprint nếu cần).
- Validate `readmeRelated.anchor` bằng **Knowledge của cùng chapter và ngôn ngữ**; file không được authored trong relation. Validate `apiRelated.controller + methodSignature` bằng Swagger/API thực tế. Relation trống hợp lệ theo schema thì được giữ trống, **không được tự suy diễn**.

**FAIL:** thiếu/thừa/tráo câu, sai đáp án đúng, ID/metadata/relationship drift hoặc relation không xác định được đích.

### Gate 3 — Swagger giữ nguyên cấu trúc và hành vi kỹ thuật

- Đối chiếu toàn bộ các file Swagger được quản lý, đặc biệt `controller-description.yml`, `api-descriptions.yml`, `api-execution.yml`, `api-cases.yml`, `api-params.yml` **nếu có**.
- Đối chiếu **chính xác** tập Controller, method signature, URL/path, HTTP method, param/request/response schema, status code, case ID, thứ tự cases, request examples, expected results và các field kỹ thuật. Không chỉ so số Controller hay số method.
- Kiểm tra Swagger `readmeRelated` trỏ đúng README/chapter/anchor; `apiRelated` trong Quiz/Interview trỏ đúng Controller/method. Kiểm tra Java source/test khi cần xác minh API thực sự tồn tại.
- **Giai đoạn ngay sau clone:** Swagger VI và EN phải **khớp bytes và nội dung**, vì chưa có thay đổi ngôn ngữ nào ở STEP 14.

**FAIL:** Controller/method/case thiếu hoặc khác, request/response không khớp, API metadata thay đổi, link sai.

### Gate 4 — YAML schema + toàn bộ related links

- Parse **toàn bộ YAML** trong scope, kiểm tra key/type theo schema tương ứng; dùng validator/contract hiện có trong `project-build/gradle-runtime` thay vì tự đoán format. Nếu schema có ngoại lệ compatibility đã được chuẩn hóa, phải ghi rõ chế độ kiểm tra và phạm vi ngoại lệ.
- Kiểm tra `readmeRelated`, `apiRelated`, Roadmap `relatedKnowledge` / `relatedModules.routeId`, Swagger links và Knowledge metadata mapping. Video chapter/section mapping thuộc riêng `STEP_OPTIONAL_VIDEO_TRANSLATE`.
- **Chỉ tự động chấp nhận relation khi đích tồn tại và xác định duy nhất**. Không dịch anchor/route ID, không gán link theo tên gần giống, không coi warning hoặc unknown là PASS.
- VI có lỗi liên kết → **`BLOCKER: VI_RELATION_INVALID`**: chuyển về sửa/QA nguồn (STEP 11 hoặc bước liên quan), sau đó chạy lại STEP 12A **trước khi bắt đầu dịch**. Không cho worker STEP 14 sửa nguồn VI.

**FAIL:** schema lỗi, relation dangling/ambiguous, target không tồn tại hoặc metadata sai. Video không phải điều kiện FAIL của STEP 14.

### Gate 5 — EN cũ đã được thay thế và có đường khôi phục

- Kiểm tra EN sau APPLY **không còn file/folder thuộc cấu trúc cũ nằm ngoài manifest clone**; chỉ những vùng do STEP 12A quản lý được thay thế.
- Xác minh trạng thái `CLONED` hoặc `UNCHANGED`, exact readback và manifest file/hash VI–EN; không coi `DRY_RUN` là completion.
- Khi EN cũ đã tồn tại và có thay đổi, xác nhận bản backup nằm tại `<MODULE>/build/step12a-backups/<RUN_ID>/` và có dữ liệu đọc được; staging/backup nằm trong `build/` (Git-ignored). Nếu xảy ra rollback, phải xác minh khôi phục đầy đủ và **không** tiếp tục STEP 14 khi trạng thái bất định.
- Xác minh ngoài scope: **VI, Excel VI/EN, Java source/test, Reference, generated Portal** không bị STEP 12A sửa.

**FAIL:** EN sót file cũ, backup thiếu/corrupt khi cần, rollback incomplete, có tác động ngoài scope.

### Gate 6 — J3 của STEP 12A đã cập nhật và readback

- Prime xác minh tab nguồn đúng workbook/gid, `J2 = Current STEP`, đối chiếu I3/module và lịch sử STEP để tránh hiểu nhầm trạng thái cũ.
- **Chính xác `J3 = STEP 12A`**, đã được **ghi và readback từ Google Sheets** sau completion gate của STEP 12A. Không chấp nhận chỉ có log Gradle PASS hoặc giá trị snapshot Excel cũ.
- Nếu giá trị J3 khác hoặc không có bằng chứng readback → `BLOCKER: STEP_12A_J3_NOT_CONFIRMED`. Không tự ghi J3 thành STEP 14, cũng không tự đánh dấu STEP 12A hoàn thành thay cho quy trình riêng.

### Quyết định cuối cùng của Prime

Chỉ khi **cả 6 gate = PASS** thì ghi `STEP_14_PRECHECK=PASS` vào báo cáo Prime, khóa snapshot technical fields/anchors/links làm baseline và mới được phép chuẩn bị dịch. Báo cáo tối thiểu: `MODULE_I3, TAB_GID, GATE_1_STRUCTURE, GATE_2_QUESTIONS, GATE_3_SWAGGER, GATE_4_SCHEMA_RELATIONS, GATE_5_BACKUP_SCOPE, GATE_6_J3, BASELINE_HASHES, BLOCKER`.

**Quy tắc resume quan trọng:** Sáu gate này áp dụng **ngay trước lần dịch đầu tiên**, khi EN còn là bản clone tiếng Việt. Nếu STEP 14 đã dịch dở và resume, **không so EN với VI theo từng byte** và **tuyệt đối không chạy lại `cloneModuleViToEn`**. Prime phải kiểm tra snapshot/baseline đã khóa, bảo toàn file/ID/anchor/related/technical fields, giữ phần dịch hợp lệ và chỉ giao phần còn thiếu.

---

## 1. Ownership — MỘT WORKER = MỘT FILE HOÀN CHỈNH / MỘT LƯỢT

**Không chia theo Menu Cấp 1** và không chia một file thành nhiều worker. Một worker dịch **toàn bộ các field được phép dịch của một file EN**, xong một file mới tái sử dụng cho file kế tiếp. Trước giao việc, Prime lập danh mục **toàn bộ** files EN tương ứng source VI đã clone từ STEP 12A:

| Type | Một file cho một lượt worker |
| --- | --- |
| README | Mỗi `content/readme/docs/en/menu/<chapter>/knowledge/<file>.md` |
| INTRODUCTION | `content/readme/docs/en/BASE.md` |
| QUIZ | Mỗi `content/readme/docs/en/menu/<chapter>/quiz/question.yml` |
| INTERVIEW | Mỗi `content/readme/docs/en/menu/<chapter>/interview/question.yml` |
| ROADMAP | Toàn bộ `content/roadmap/en/roadmap.yml` |
| SWAGGER | **Mỗi file riêng**: `controller-description.yml`, `api-descriptions.yml`, `api-execution.yml`, `api-cases.yml`, `api-params.yml` dưới `src/main/resources/swagger/en/` nếu hiện hữu |

**Prime-only/không dịch:** `content/readme/docs/en/LIST.md` (link/menu kỹ thuật STEP 12A), mọi `content/readme/docs/en/menu/<chapter>/metadata/knowledge-metadata.yml`, schema comment của YAML, Java code/test, Reference, Excel, Google Sheets, Portal, Gradle/config, toàn bộ VI. Module thiếu capability thì `NOT_APPLICABLE`; file bất thường/không xác định type thì BLOCKER.

## 2. Prime lập danh mục, snapshot và giao đúng location

1. Đọc General + tất cả rules + STEP 14 + **toàn văn** `worker/STEP_14_WORKER.md` và module-info. Qua đủ 6 PRE-FLIGHT GATE mục 0 **trước khi giao worker đầu tiên**. Không coi trạng thái J3 đơn độc là bằng chứng đã clone.
2. Prime dùng code catalog trong `gradle-runtime` để rà **toàn bộ file**, cặp VI/EN, type, SHA-256 và kiểm tra cấu trúc ban đầu. Tạo ledger/manifest trong `<MODULE>/build/step14/<RUN_ID>/`, backup EN trước dịch và readback. Đây là backup **riêng STEP 14**; không xóa backup STEP 12A.
3. Cấp quyền **đọc/ghi độc quyền đúng MỘT EN file** cho worker hiện tại. Worker không được ghi VI, file EN khác, file dùng chung hay các file của Prime. Không hai worker cùng chỉnh cùng một file, kể cả các Controller khác nhau nằm trong một Swagger YAML.
4. Pool tuân `PRIME_RULES.md`: Mode A mặc định tối đa **3 worker ACTIVE+SLEEPING**; Mode B chỉ có N do user chỉ định trong session. **Tận dụng SLEEPING trước**, chỉ spawn từng worker một. Prime tiếp tục phân công các file đang chờ cho đến hết danh mục; không tạo worker dư.

### Gradle catalog và validation (Prime only)

Chạy trên đúng module ở I3, từ repository root; `RUN_ID` chọn một lần, giữ nguyên khi resume:

```powershell
.\gradlew.bat :<MODULE_GRADLE_PATH>:prepareModuleTranslation -PtranslationRunId=<RUN_ID> -PtranslationMode=DRY_RUN --console=plain
.\gradlew.bat :<MODULE_GRADLE_PATH>:prepareModuleTranslation -PtranslationRunId=<RUN_ID> -PtranslationMode=APPLY --console=plain
```

`PREPARE DRY_RUN` chỉ kiểm tra và liệt kê file; `PREPARE APPLY` chỉ ghi **manifest và bản copy snapshot EN trong `build/step14/`**, **KHÔNG dịch hay sửa bất kỳ VI/EN**. Đòi hỏi bản EN chưa dịch còn clone byte-identical từ VI (trừ LIST kỹ thuật). Nếu manifest đã tồn tại, **không chạy PREPARE lại**: để bảo vệ snapshot, task sẽ báo lỗi. Khi resume phải dùng manifest hiện có.

Khi toàn bộ worker được giao đã giải phóng ownership, chạy:

```powershell
.\gradlew.bat :<MODULE_GRADLE_PATH>:validateModuleTranslation -PtranslationRunId=<RUN_ID> --console=plain
```

`VALIDATE` là **read-only**: so file VI/EN theo whitelist/structure, bảo vệ metadata và LIST, xác minh baseline/VI chưa drift; ghi ra từng file `PENDING` (byte-identical VI) hoặc `STRUCTURE_PASS_NEEDS_SEMANTIC_QA` (có thay đổi trong whitelist). **Hai trạng thái trên không phải chứng nhận chất lượng dịch:** Prime vẫn phải rà đủ coverage và ý nghĩa. Không tự nâng file `PENDING` thành PASS hoặc chỉ nhìn counters để cập nhật J3.

### Hợp đồng giao việc — đúng bốn dòng

```text
<VERIFIED_VI_SOURCE_TAB_URL_WITH_GID> [IDENTITY ONLY, NEVER WRITE]
FILE_TYPE=<TYPE> | VI_SOURCE=<ABSOLUTE_VI_FILE_FOR_PRIME_AUDIT> | EN_TARGET=<ABSOLUTE_EN_FILE>
READ/WRITE=EXACT_EN_TARGET_ONLY | OWNER=<WORKER_ID> | EXPECTED_EN_SHA256=<CURRENT_HASH>
/java-learning/module-generate-agent/worker/STEP_14_WORKER.md
```

Prime cấp **đường dẫn tuyệt đối thực**, không paste nội dung nguồn hoặc tóm tắt instructions. VI_SOURCE là định danh để Prime QA, **không cấp quyền đọc VI cho worker**. Worker đọc file EN đã được clone (vẫn là tiếng Việt), tự dịch đúng field whitelist trong worker-only file.

## 3. Worker dịch và ghi nguyên MỘT file EN

- Worker đọc `worker/STEP_14_WORKER.md` toàn văn; xác minh loại file, quyền sở hữu và checksum trước ghi. Hash thay đổi/có writer khác → BLOCKER, không overwrite.
- **Dịch trung thành 100% nội dung được phép dịch**, giữ nguyên ý, cấu trúc, ví dụ, điều kiện, kết luận, số liệu, trình tự. **Cấm tự ý thêm/bớt câu chữ về mặt nội dung, rút gọn, diễn giải thêm, tái soạn bài hoặc sửa lỗi nguồn VI**. Các trường hợp sai/khó hiểu → REVIEW cho Prime, không tự chữa bằng cách thay đổi sự thật.
- Giữ nguyên toàn bộ IDs, anchors, đường dẫn, markdown code fences và inline code, Java/API identifiers, YAML key, câu hỏi/đáp án identity, metadata, related và Swagger request/response. **Video đã tách sang STEP_OPTIONAL_VIDEO_TRANSLATE sau STEP_FINAL.** Whitelist/blacklist chi tiết theo type nằm trong worker-only file.
- Worker chỉ ghi trực tiếp file EN được Prime cấp (file-level isolation); không sinh proposal/file phụ. Ghi xong parse/readback **toàn bộ file**, báo coverage từng mục, status và blockers; giải phóng ownership, không tự nhận file tiếp theo.

## 4. Prime kiểm tra file và toàn module

1. So từng file EN hoàn tất với VI và baseline theo **field whitelist**, không chỉ nhìn diff text. Markdown bảo toàn số section/anchor, liên kết, code fences, list/table không mất item, heading hierarchy không thêm/bớt. YAML schema, keys, list lengths/order, non-translatable fields **semantic-equal VI**.
2. Quiz: cùng số câu, ID, A/B/C/D order, correctAnswerId, relations. Interview: cùng số câu/thứ tự và metadata/relations, đối chiếu theo index do không có question ID bắt buộc. Roadmap: cùng milestone ID/order/dependencies, relatedKnowledge/relatedModules.routeId. Swagger: cùng Controller/method, request/response/cases/status, chỉ đổi text mô tả. Video không thuộc STEP 14.
3. **Ngữ nghĩa phải QA riêng**: no omitted sentence/paragraph, no added explanation/hallucination; thuật ngữ Java/Spring nhất quán. Validator tự động **không thể chứng minh** bản dịch không thêm/bớt ý, nên Prime cần so nội dung thực tế. Không coi việc worker báo COMPLETED là đủ.
4. File sai → thu hồi ownership, ghi evidence, restore **chỉ file đó** từ STEP14 backup (không xóa bản dịch file khác) rồi giao lại. Tuyệt đối **không gọi cloneModuleViToEn** sau khi dịch bắt đầu.
5. Prime chạy code validation trên từng file và toàn manifest; tất cả files phải `PASSED` hoặc `SKIP_NO_TRANSLATABLE_TEXT` có căn cứ. PENDING/REVIEW/FAILED/BLOCKER → không cập nhật J3, không STEP 15.

## 5. Completion gate và bàn giao

- Sáu preflight gates đã PASS; không có worker đang ghi. Tất cả files trong manifest đã QA dịch và readback, không đổi cấu trúc/technical identity, không thêm/bớt nội dung, không sửa VI hay vùng ngoài scope.
- Cây VI/EN bắt buộc, Knowledge anchor/metadata, Quiz/Interview IDs/order/correct answer, Swagger kỹ thuật, Roadmap relations và toàn bộ YAML schema/related **PASS**; backup đọc được và có rollback theo file. Không đòi Video.
- Prime xác minh tab nguồn VI bằng gid/I3/J2, sau đó ghi **`J3 = STEP 14`** trên đúng tab và readback theo `PRIME_RULES.md`. **Không tạo tab EN hay Excel EN** ở STEP 14 — việc đó thuộc STEP 15.
- Báo cáo: `MODULE_I3, VI_GID, TOTAL_FILES, README, INTRODUCTION, QUIZ, INTERVIEW, ROADMAP, SWAGGER, PASSED, SKIPPED, PENDING, REVIEW, FAILED, STRUCTURE_QA, RELATION_QA, TRANSLATION_QA, J3_READBACK, BLOCKER`.

**Resume:** dùng ledger/backup/chỉ file chưa hoàn tất, không so EN↔VI bytes như preflight lần đầu, không chạy lại STEP 12A và không dịch đè file đã được QA PASS.
