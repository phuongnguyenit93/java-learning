# STEP 14 — Translation VI → EN (Prime + Worker)

> **Tình trạng tài liệu:** Đã chốt **PRE-FLIGHT GATE** từ STEP 12A trước khi cho phép dịch. Quy trình chia worker, phạm vi biên dịch từng field, merge và completion gate của chính STEP 14 sẽ được thiết kế riêng. **Không bắt đầu dịch chỉ dựa vào tài liệu hiện tại** khi chưa có hợp đồng worker-only `worker/STEP_14_WORKER.md` và phần điều phối đầy đủ.

## 0. PRE-FLIGHT GATE — BẮT BUỘC trước khi bắt đầu STEP 14

**Prime chịu trách nhiệm nghiệm thu STEP 12A trước khi giao bất kỳ chương nào cho worker dịch.** Đây là một **hard gate**: chỉ cần một hạng mục FAIL/UNKNOWN thì **KHÔNG giao worker, KHÔNG viết EN, KHÔNG cập nhật J3 sang STEP 14**.

Trước khi kiểm tra, Prime đọc **toàn văn** `module-generate-agent/GENERAL_AGENT_RULES.md`, liệt kê/đọc **toàn bộ** `module-generate-agent/rules/`, đọc STEP 14 này, `STEP_12A.md`, `module-generate-info.txt`; xác định đúng module từ **I3 + workbook/gid đã xác minh**, không suy luận từ tên tab. Đọc kết quả thực thi STEP 12A, không tin trạng thái J3 một cách đơn độc.

### Gate 1 — Đối chiếu toàn bộ cấu trúc VI/EN

- So sánh **chính xác danh sách file và đường dẫn tương đối** của các subtree được STEP 12A quản lý:
  - `content/readme/docs/{vi,en}/` (`BASE.md`, `LIST.md`, `menu/**`).
  - `content/readme/metadata/{vi,en}/`.
  - `content/roadmap/{vi,en}/`.
  - `content/quiz/{vi,en}/`.
  - `content/interview/{vi,en}/`.
  - `content/video/{vi,en}/`.
  - `src/main/resources/swagger/{vi,en}/`.
- Kiểm tra thứ tự/chỉ số Menu Cấp 1, tên folder/filename, chapter và section Knowledge. Mỗi file Markdown EN phải có **cùng tập và thứ tự anchor ID** với VI; metadata phải có cùng file keys, section keys và giá trị kỹ thuật (`difficulty`, `isImportant`, `aiGenerated`, `reviewed`).
- Vì STEP 12A chỉ clone, **nội dung EN tại thời điểm này vẫn là tiếng Việt**. Đối chiếu byte/hash VI/EN cho mọi file nằm trong scope, **ngoại trừ `LIST.md`**: ở EN, chỉ header ngôn ngữ và đích link `content/readme/docs/vi/menu/` → `content/readme/docs/en/menu/` được khác.
- Nếu một khả năng không có nguồn VI (ví dụ Video hoặc Quiz không được tạo), xác minh vùng EN tương ứng **không còn dữ liệu cũ**. Không tự điền skeleton mới.

**FAIL:** file thiếu/thừa, chapter sai thứ tự, anchor đổi, metadata lệch, EN chưa phải clone hoặc dữ liệu EN cũ còn sót.

### Gate 2 — Quiz/Interview giữ nguyên cấu trúc và relation

- Đối chiếu `questions[]` giữa VI và EN: **cùng số lượng, cùng thứ tự và cùng nội dung tại thời điểm clone**.
- **Quiz:** bảo toàn `id` từng câu, thứ tự bốn đáp án `A/B/C/D`, `correctAnswerId`, `explanation`, `aiGenerated`, `reviewed`, `readmeRelated` và `apiRelated`. Không tái tạo ID mới.
- **Interview:** bảo toàn thứ tự và toàn bộ câu hỏi/câu trả lời, metadata và relation. Interview không có ID câu hỏi bắt buộc như Quiz; đối chiếu **theo vị trí + chapter thuộc `readmeRelated.file`** (kèm fingerprint nếu cần).
- Validate `readmeRelated.file + anchor` bằng **README của cùng ngôn ngữ**, và `apiRelated.controller + methodSignature` bằng Swagger/API thực tế. Relation trống hợp lệ theo schema thì được giữ trống, **không được tự suy diễn**.

**FAIL:** thiếu/thừa/tráo câu, sai đáp án đúng, ID/metadata/relationship drift hoặc relation không xác định được đích.

### Gate 3 — Swagger giữ nguyên cấu trúc và hành vi kỹ thuật

- Đối chiếu toàn bộ các file Swagger được quản lý, đặc biệt `controller-description.yml`, `api-descriptions.yml`, `api-execution.yml`, `api-cases.yml`, `api-params.yml` **nếu có**.
- Đối chiếu **chính xác** tập Controller, method signature, URL/path, HTTP method, param/request/response schema, status code, case ID, thứ tự cases, request examples, expected results và các field kỹ thuật. Không chỉ so số Controller hay số method.
- Kiểm tra Swagger `readmeRelated` trỏ đúng README/chapter/anchor; `apiRelated` trong Quiz/Interview trỏ đúng Controller/method. Kiểm tra Java source/test khi cần xác minh API thực sự tồn tại.
- **Giai đoạn ngay sau clone:** Swagger VI và EN phải **khớp bytes và nội dung**, vì chưa có thay đổi ngôn ngữ nào ở STEP 14.

**FAIL:** Controller/method/case thiếu hoặc khác, request/response không khớp, API metadata thay đổi, link sai.

### Gate 4 — YAML schema + toàn bộ related links

- Parse **toàn bộ YAML** trong scope, kiểm tra key/type theo schema tương ứng; dùng validator/contract hiện có trong `project-build/gradle-runtime` thay vì tự đoán format. Nếu schema có ngoại lệ compatibility đã được chuẩn hóa, phải ghi rõ chế độ kiểm tra và phạm vi ngoại lệ.
- Kiểm tra `readmeRelated`, `apiRelated`, Roadmap `relatedKnowledge` / `relatedModules.routeId`, Swagger links, Knowledge metadata mapping và Video chapter/section mapping.
- **Chỉ tự động chấp nhận relation khi đích tồn tại và xác định duy nhất**. Không dịch anchor/route ID, không gán link theo tên gần giống, không coi warning hoặc unknown là PASS.
- VI có lỗi liên kết → **`BLOCKER: VI_RELATION_INVALID`**: chuyển về sửa/QA nguồn (STEP 11 hoặc bước liên quan), sau đó chạy lại STEP 12A **trước khi bắt đầu dịch**. Không cho worker STEP 14 sửa nguồn VI.

**FAIL:** schema lỗi, relation dangling/ambiguous, target không tồn tại, metadata/Video không khớp.

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

## 1. Translation implementation — CHƯA CHỐT

Phần điều phối Prime, ownership Menu Cấp 1, hợp đồng worker-only, danh sách field được dịch/khóa, staging/merge, QA nội dung EN và completion gate của STEP 14 **sẽ được xây dựng riêng**. Cho đến khi có đủ các phần đó, **không điều phối worker và không cập nhật `J3 = STEP 14`**.
