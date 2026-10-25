# STEP_OPTIONAL_VIDEO_TRANSLATE — OPTIONAL AFTER STEP_OPTIONAL_VIDEO (Prime + Worker)

**Optional, không nằm trong chuỗi STEP 1–15 → STEP_FINAL.** Chỉ chạy theo yêu cầu, sau khi Video VI của `STEP_OPTIONAL_VIDEO` đã QA và import vào `content/readme/docs/vi/menu/<chapter>/video/video-script.yml`. Không chạy lại `STEP_12A`, `STEP_14` hoặc `STEP_15` để dịch Video; các bước đó đã hoàn thành trước Video và cố ý bỏ Video khỏi phạm vi dù Video cùng nằm trong Docs.

## 1. Prime preflight

1. Đọc toàn văn `GENERAL_AGENT_RULES.md`, mọi `rules/*`, file này, `worker/STEP_OPTIONAL_VIDEO_TRANSLATE_WORKER.md` và mapping `module-generate-info.txt`. Xác minh module I3, VI gid, EN gid theo `content/sheet/en/publish-state.json` (chỉ đọc), trạng thái core `J3 = STEP 15` trên tab VI và EN, và bản publish đã QA. Không tạo EN tab mới.
2. Kiểm kê **đúng các file** `content/readme/docs/vi/menu/<chapter>/video/video-script.yml`, đối chiếu Knowledge VI cùng chương và parse `VideoYamlContentUtils`. Video VI chưa hoàn tất, X:AA chưa sync, file lạ hoặc thiếu → blocker. Không dùng API/Quiz/Interview related để đoán Video.
3. Mỗi file VI đã có phải có **đúng một** file EN cùng relative path. Nếu EN chưa tồn tại: **Prime** seed đúng bytes VI vào EN sau kiểm tra symlink/ownership và tạo backup/manifest trong `<I3>/build/optional-video-translate/<RUN_ID>/`. Nếu EN đã có: giữ nguyên, không clone đè bản dịch; kiểm tra trạng thái/semantic diff và resume file còn thiếu. Prime không dịch nội dung.
4. Prime lập ledger hash VI/EN, số chapters và assign **một file EN độc quyền cho mỗi lượt worker**. Pool Mode A mặc định 3, Mode B theo N của session; ưu tiên worker SLEEPING, spawn tuần tự. Worker không đọc VI/source khác; file EN đã được seed VI chứa đủ chữ để tự dịch.

## 2. Worker chỉ dịch một Video EN file mỗi lượt

Giao đúng bốn dòng, không paste nội dung hoặc hướng dẫn:

```text
<VERIFIED_VI_TAB_URL#gid=<VI_GID>>
FILE_TYPE=VIDEO | EN_TARGET=<ABSOLUTE_EN_VIDEO_YAML> | EXPECTED_SHA256=<CURRENT_HASH>
READ/WRITE=EXACT_EN_TARGET_ONLY | OWNER=<WORKER_ID>
/java-learning/module-generate-agent/worker/STEP_OPTIONAL_VIDEO_TRANSLATE_WORKER.md
```

- Worker tự đọc toàn văn file worker-only, dịch các scalar hiển thị: `title`, `sections[].title`, `sections[].items[].title` (nếu có), `visual`, `script`, `purpose`. Giữ `video.url`, `type`, `time`, scene/transition count/order, code, URL, định danh và cấu trúc YAML bất biến.
- Prime exact readback, so cấu trúc/scenes/timeline/field khóa VI↔EN, QA tiếng Anh tự nhiên/chính xác kỹ thuật, bảo toàn toàn bộ scene. Nếu thiếu bản dịch, loss, YAML lỗi → REVIEW/BLOCKER. Không ghi vào Google Sheets từ worker.
- Chỉ khi **toàn bộ video file thuộc module** đã PASS và không còn writer mới được đồng bộ EN vào Sheet. Partial/trial không publish hàng loạt.

## 3. Đồng bộ EN sau STEP 15 (Prime only)

- **Không tái chạy** `publishModuleEnSheet`, `cloneModuleViToEn`, `prepareModuleTranslation` của core: chúng có source hash/baseline riêng và có thể thay nội dung ngoài Video.
- Nguồn chuẩn EN là `content/readme/docs/en/menu/<chapter>/video/video-script.yml`, đích được phép cập nhật trên tab EN hiện hữu **chỉ `X:AA` các hàng Menu Cấp 1**. Giữ nguyên J3 ở VI/EN = `STEP 15`, Google Template, A:W, AB, toàn bộ metadata, translations khác. `content/sheet/vi/module.xlsx` đã được cập nhật bởi STEP_OPTIONAL_VIDEO; snapshot `content/sheet/en/module.xlsx` phải được export lại từ **chính tab EN** sau khi Sheet readback PASS. STEP 12A, STEP 14 và STEP 15 không được tự clone/dịch/ghi đè Video trong Docs.
- Prime dùng task module-local `publishOptionalVideoEn` với `-PfileUrl=<SPREADSHEET_URL> -PviGid=<VI_GID> -PsheetMode=DRY_RUN` rồi `APPLY`. Task phải preflight đúng module/state/gid, validate file EN tương ứng từng chapter, chỉ cập nhật X:AA và verify exact readback; không được full-resync nội dung STEP 15 hoặc chấp nhận `DRY_RUN` là đã ghi. Nếu task không tồn tại/FAIL, báo `BLOCKER: OPTIONAL_VIDEO_EN_PUBLISH`; không tự gọi một task publish toàn Sheet thay thế.
- Kiểm tra Excel VI/EN chứa Video phù hợp Sheet tại X:AA; nếu source thay đổi đồng thời hoặc workbook/export không khớp → BLOCKER, giữ trạng thái khôi phục và không tuyên bố completed.

## 4. Completion và Git

- `COMPLETED` chỉ khi: đủ Video EN files, worker+Prime semantic QA PASS, EN Sheet X:AA và Excel EN exact readback PASS, VI/EN J3 vẫn `STEP 15`, core Knowledge/Quiz/Interview/Swagger và mọi unrelated Sheet cells nguyên vẹn.
- Không cập nhật J3 thành tên optional step. **Không tự commit/push**; sau STEP_FINAL, Video thay đổi vẫn là local until người dùng yêu cầu chạy lại STEP_FINAL để commit/push riêng phần Video, không tạo dependency ngược cho core.
- Báo cáo: `MODULE_I3, VI_GID, EN_GID, VIDEO_FILES, WORKER_QA, TRANSLATION_COVERAGE, DRY_RUN, APPLY, EN_SHEET_READBACK, VI_XLSX, EN_XLSX, J3_UNCHANGED, REMAINING_DIRTY_FILES, BLOCKERS`.
