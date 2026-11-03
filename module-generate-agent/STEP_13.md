# STEP 13 — LEGACY CONTRACT (chức năng clone đã chuyển sang STEP 10)

> **QUY TẮC MỚI CÓ HIỆU LỰC:** Bắt đầu từ workflow cập nhật, việc clone và **CLEAN toàn bộ thư mục EN đích rồi FILL nguyên cây VI** thuộc **STEP 10 Prime-only**, xem `STEP_10.md`. **KHÔNG thực hiện task `cloneModuleViToEn` tại STEP 13 nữa.** STEP 13 sẽ là bước QA đồng bộ VI/EN read-only trước STEP 14; phần hướng dẫn lịch sử bên dưới **đã bị thay thế, không được thi hành** cho tới khi hợp đồng STEP 13 read-only được viết lại. Không tự cập nhật `CURRENT_STEP.VALUE = "13"` chỉ vì chạy lại clone.

---



**Summary and Reference clone:** STEP 13 clones the VI `summary/section.yml` (**directly authored as YAML in STEP 5 parent AC**) and `summaries.yml` (generated from child AC Markdown) under each chapter to EN, excluding Video. Validate root `section.yml` schema, `roadmap[]` ID/order/transition, `comparisonMarkdown` GFM table, root `summaries`, child counts/order/IDs and Knowledge mapping before APPLY. It also clones `content/reference/vi/references.yml` to `content/reference/en/references.yml` **byte-for-byte as untranslated staging**. STEP 14 translates only the EN Reference `title`/`description`, preserving `url` and item order.

## 0. Mục tiêu và ranh giới

- STEP 13 diễn ra **sau STEP 12, trước STEP 14**. Mục tiêu: xóa lệch cấu trúc EN cũ, thay bằng bản clone chính xác của VI theo technical identity để worker STEP 14 **chỉ dịch văn bản**, không phải tái tạo structure. **Video là ngoại lệ loại theo FILE, dù nằm trong subtree Docs**: không clone/ghi đè/xóa `content/readme/docs/{vi,en}/menu/<chapter>/video/video-script.yml`; Video xử lý hoàn toàn trong optional steps sau STEP_FINAL.
- **Prime-only**, không giao worker, không dịch tự động, không tạo Google Sheets EN hoặc Excel EN (để dành STEP 15).
- **Không clone** Java source/test, build config, `master.json`, `content/sheet/{vi,en}/module.xlsx`, JSON Portal đã generate hoặc source ở module khác. Reference clone chỉ ghi đúng `content/reference/en/references.yml`, giữ nguyên file VI.
- **Chỉ dùng task module-local** `cloneModuleViToEn` ở `project-build/gradle-runtime`. Không dùng các Sheet importer để tái tạo EN và không gọi task dịch.
- Bản EN sau STEP 13 **vẫn là tiếng Việt**. STEP 14 mới dịch nội dung.

## 1. Prime preflight

1. Đọc đầy đủ `module-generate-agent/GENERAL_AGENT_RULES.md`, toàn bộ `module-generate-agent/rules/`, file STEP này và `module-generate-info.txt`. Xác nhận module I3 hợp lệ, source VI đã nghiệm thu STEP 11 và STEP 12.
2. Xác nhận không có worker/actor đang ghi vào VI hoặc EN; không có tiến trình STEP 14 đang dịch EN.
3. Kiểm tra bốn vùng source VI: `content/readme/docs/vi/` (`BASE.md`, `LIST.md`, `menu/<chapter>/knowledge/*.md`, `metadata/knowledge-metadata.yml`, `questions/interview.yml`, `questions/summary.yml` nếu có, `quiz/question.yml` bên trong chapter), `content/roadmap/vi/`, `src/main/resources/swagger/vi/` và `content/reference/vi/references.yml`. **Không kiểm kê/cloning Video hay các Portal output `content/interview/` / `content/quiz/`.**
4. Mỗi subtree EN tương ứng là **đích bị thay thế toàn bộ**. Khi VI optional không tồn tại, EN cũ của thành phần đó bị dọn. Reference EN cũng là đích riêng có backup; nếu Reference VI không có thì không tự tạo Reference dịch giả. Knowledge và metadata của từng chapter VI phải đủ dữ liệu mới được APPLY.
5. Nếu đã dịch EN ở STEP 14, **KHÔNG chạy lại STEP 13** vì nó sẽ thay toàn bộ bản dịch.

## 2. Task và quy trình

Chạy từ repository root, thay đúng Gradle module path:

```powershell
.\gradlew.bat :<MODULE_GRADLE_PATH>:cloneModuleViToEn -PsheetMode=DRY_RUN --console=plain
```

- DRY_RUN: không ghi EN, không thay Excel, không tác động Google Sheets; trả danh sách **4 vùng clone** (gồm Reference), số file và thay đổi dự kiến.
- Validation **trước APPLY**: README anchors, metadata nằm trong từng chapter, `LIST.md` (đổi link vi/menu → en/menu và heading VI → EN), Roadmap schema/relatedKnowledge, `<chapter>/quiz/question.yml` và `<chapter>/questions/interview.yml` theo schema và related trong chính chapter; `<chapter>/questions/summary.yml` (nếu có) theo schema Summary Questions với **chỉ question/answer, không có related**; Swagger YAML + `readmeRelated`; Reference `references[]` giữ đúng `title`, `url`, `description`, thứ tự, schema và URL. Video không thuộc gate này. Relation không có target chắc chắn sẽ **FAIL**, không đoán bằng AI hay đổi anchor/ID.
- Vấn đề trong VI phải xử lý ở nguồn (đặc biệt STEP 11), QA rồi chạy DRY_RUN lại.
- Khi DRY_RUN PASS, chạy cùng task, đổi `-PsheetMode=APPLY`.

```powershell
.\gradlew.bat :<MODULE_GRADLE_PATH>:cloneModuleViToEn -PsheetMode=APPLY --console=plain
```

- APPLY staging trong `<MODULE>/build/step13-staging/` (Git-ignored), kiểm tra đầy đủ, recheck VI/EN không thay đổi đồng thời rồi mới thay từng subtree.
- EN cũ được di chuyển sang `<MODULE>/build/step13-backups/<RUN_ID>/` trước khi thay. Nếu có lỗi commit, task rollback; khi rollback không đầy đủ phải báo đường dẫn phục hồi thủ công. **Giữ backup EN cũ sau khi task PASS**.
- Readback so sánh **toàn bộ file/bytes** EN mới với bản clone. Chạy lại khi chưa dịch và VI không đổi cho kết quả `UNCHANGED`.
- Ngoại lệ: `LIST.md` EN đổi **chỉ** heading ngôn ngữ và link `content/readme/docs/vi/menu/` thành `en/menu/`. Reference EN cùng các file khác giữ nguyên bytes từ VI ở STEP 13; việc hiện chữ Việt tại EN là trạng thái chờ STEP 14.

## 3. Completion gate

- DRY_RUN = PASS; APPLY = `CLONED` hoặc `UNCHANGED`; không có blocker.
- VI không bị sửa, tất cả EN được quản lý khớp VI về file, anchor, metadata, schema và related; LIST EN trỏ đúng EN.
- Excel VI/EN (nếu tồn tại), Java/test, Reference **VI** và Portal generated không đổi; Reference EN khớp bytes với VI và có rollback khi bị thay.
- Xác minh backup/staging/Git status, không có staging ngoài `build/`. **Không** commit/push hoặc chạy STEP 14.
- **Prime** xác minh đúng module qua I3, snapshot `master.json`, cập nhật **`CURRENT_STEP.VALUE = "13"` trong `master.json`** khi tất cả gate PASS và exact readback nguyên văn. Task Gradle **không tự cập nhật Google Sheets**.
- Báo cáo: `MODULE_I3`, `VI_FOLDERS`, `EN_REPLACED`, `FILES`, `REFERENCE_VI_EN_SHA256`, `RELATED_QA`, `DRY_RUN`, `APPLY`, `BACKUP_PATH`, `ROLLBACK`, `READBACK`, `CURRENT_STEP.VALUE_READBACK`, `GIT_STATUS`, `BLOCKER`.

## 4. Phân ranh với STEP 14/15

- STEP 14: worker dịch EN nhưng giữ nguyên technical identity: filename, anchors, IDs, methodSignature, correctAnswerId, Java code, input/output và metadata. **Không tự gọi cloneModuleViToEn**.
- STEP 15: task `publishModuleEnSheet` clone tab `Template` sang tab EN có gid ổn định, dùng VI Excel và nội dung EN để ghi các ô bằng code, kiểm tra readback rồi export chính tab EN sang `content/sheet/en/module.xlsx`. Không dùng AI, không tạo tab mới từ Excel.
