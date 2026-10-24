# STEP 12A — PRIME ONLY: Clone cấu trúc và nội dung VI → EN bằng Gradle

## 0. Mục tiêu và ranh giới

- STEP 12A diễn ra **sau STEP 12, trước STEP 14**. Mục tiêu: xóa lệch cấu trúc EN cũ, thay bằng bản clone chính xác của VI theo technical identity để worker STEP 14 **chỉ dịch văn bản**, không phải tái tạo structure. **Video không thuộc sáu subtree clone bắt buộc**, không đọc/ghi/xóa `content/video/en`; Video xử lý hoàn toàn trong optional steps sau STEP_FINAL.
- **Prime-only**, không giao worker, không dịch tự động, không tạo Google Sheets EN hoặc Excel EN (để dành STEP 15).
- **Không clone** Java source/test, build config, `master.json`, `content/sheet/{vi,en}/module.xlsx`, `content/reference/references.yml`, JSON Portal đã generate hoặc source ở module khác.
- **Chỉ dùng task module-local** `cloneModuleViToEn` ở `project-build/gradle-runtime`. Không dùng các Sheet importer để tái tạo EN và không gọi task dịch.
- Bản EN sau STEP 12A **vẫn là tiếng Việt**. STEP 14 mới dịch nội dung.

## 1. Prime preflight

1. Đọc đầy đủ `module-generate-agent/GENERAL_AGENT_RULES.md`, toàn bộ `module-generate-agent/rules/`, file STEP này và `module-generate-info.txt`. Xác nhận module I3 hợp lệ, source VI đã nghiệm thu STEP 11 và STEP 12.
2. Xác nhận không có worker/actor đang ghi vào VI hoặc EN; không có tiến trình STEP 14 đang dịch EN.
3. Kiểm tra các vùng source VI: `content/readme/docs/vi/` (BASE, LIST, menu), `content/readme/metadata/vi/`, `content/roadmap/vi/`, `content/quiz/vi/`, `content/interview/vi/` và `src/main/resources/swagger/vi/`. **Không kiểm kê/cloning Video như một cổng bắt buộc.**
4. Mỗi subtree EN tương ứng là **đích bị thay thế toàn bộ**. Khi VI optional không tồn tại, EN cũ của thành phần đó bị dọn. README/docs và metadata VI phải đủ dữ liệu mới được APPLY.
5. Nếu đã dịch EN ở STEP 14, **KHÔNG chạy lại STEP 12A** vì nó sẽ thay toàn bộ bản dịch.

## 2. Task và quy trình

Chạy từ repository root, thay đúng Gradle module path:

```powershell
.\gradlew.bat :<MODULE_GRADLE_PATH>:cloneModuleViToEn -PsheetMode=DRY_RUN --console=plain
```

- DRY_RUN: không ghi EN, không thay Excel, không tác động Google Sheets; trả danh sách **6 vùng clone** bắt buộc, số file và thay đổi dự kiến.
- Validation **trước APPLY**: README anchors, Knowledge metadata, `LIST.md` (đổi link vi/menu → en/menu và heading VI → EN), Roadmap schema/relatedKnowledge, Quiz/Interview schema + `readmeRelated` + `apiRelated`, Swagger YAML + `readmeRelated`. Video không thuộc gate này. Relation không có target chắc chắn sẽ **FAIL**, không đoán bằng AI hay đổi anchor/ID.
- Vấn đề trong VI phải xử lý ở nguồn (đặc biệt STEP 11), QA rồi chạy DRY_RUN lại.
- Khi DRY_RUN PASS, chạy cùng task, đổi `-PsheetMode=APPLY`.

```powershell
.\gradlew.bat :<MODULE_GRADLE_PATH>:cloneModuleViToEn -PsheetMode=APPLY --console=plain
```

- APPLY staging trong `<MODULE>/build/step12a-staging/` (Git-ignored), kiểm tra đầy đủ, recheck VI/EN không thay đổi đồng thời rồi mới thay từng subtree.
- EN cũ được di chuyển sang `<MODULE>/build/step12a-backups/<RUN_ID>/` trước khi thay. Nếu có lỗi commit, task rollback; khi rollback không đầy đủ phải báo đường dẫn phục hồi thủ công. **Giữ backup EN cũ sau khi task PASS**.
- Readback so sánh **toàn bộ file/bytes** EN mới với bản clone. Chạy lại khi chưa dịch và VI không đổi cho kết quả `UNCHANGED`.
- Ngoại lệ: `LIST.md` EN đổi **chỉ** heading ngôn ngữ và link `content/readme/docs/vi/menu/` thành `en/menu/`. Các file khác giữ nguyên bytes từ VI.

## 3. Completion gate

- DRY_RUN = PASS; APPLY = `CLONED` hoặc `UNCHANGED`; không có blocker.
- VI không bị sửa, tất cả EN được quản lý khớp VI về file, anchor, metadata, schema và related; LIST EN trỏ đúng EN.
- Excel VI/EN (nếu tồn tại), Java/test, Reference và Portal generated không đổi.
- Xác minh backup/staging/Git status, không có staging ngoài `build/`. **Không** commit/push hoặc chạy STEP 14.
- **Prime** kiểm tra `J2 = Current STEP`, đọc lại J3, cập nhật **`J3 = STEP 12A`** trên đúng tab nguồn khi tất cả gate PASS và readback nguyên văn. Task Gradle **không tự cập nhật Google Sheets**.
- Báo cáo: `MODULE_I3`, `VI_FOLDERS`, `EN_REPLACED`, `FILES`, `RELATED_QA`, `DRY_RUN`, `APPLY`, `BACKUP_PATH`, `ROLLBACK`, `READBACK`, `J3_READBACK`, `GIT_STATUS`, `BLOCKER`.

## 4. Phân ranh với STEP 14/15

- STEP 14: worker dịch EN nhưng giữ nguyên technical identity: filename, anchors, IDs, methodSignature, correctAnswerId, Java code, input/output và metadata. **Không tự gọi cloneModuleViToEn**.
- STEP 15: task `publishModuleEnSheet` clone tab `Template` sang tab EN có gid ổn định, dùng VI Excel và nội dung EN để ghi các ô bằng code, kiểm tra readback rồi export chính tab EN sang `content/sheet/en/module.xlsx`. Không dùng AI, không tạo tab mới từ Excel.
