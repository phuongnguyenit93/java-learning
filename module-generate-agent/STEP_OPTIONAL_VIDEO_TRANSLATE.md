# STEP_OPTIONAL_VIDEO_TRANSLATE — OPTIONAL AFTER STEP_OPTIONAL_VIDEO (Prime + Worker)



**Optional, không nằm trong chuỗi STEP 1–12 → STEP_FINAL.** Chỉ chạy theo yêu cầu, sau khi Video VI của `STEP_OPTIONAL_VIDEO` đã QA và import vào `content/readme/docs/vi/menu/<chapter>/video/video-script.yml`. Không chạy lại STEP 12 hoặc các workflow legacy để dịch Video; các bước đó đã hoàn thành trước Video và cố ý bỏ Video khỏi phạm vi dù Video cùng nằm trong Docs.

## 1. Prime preflight

1. Read `GENERAL_AGENT_RULES.md`, all `rules/*`, this STEP and the Video EN worker-only file. Verify the exact local module path and require `CURRENT_STEP.VALUE = "FINAL"` in `master.json`. No EN gid, `publish-state.json`, online Sheet or previously published tab is required.
2. Kiểm kê **đúng các file** `content/readme/docs/vi/menu/<chapter>/video/video-script.yml`, đối chiếu Knowledge VI cùng chương và parse `VideoYamlContentUtils`. Source VI chưa hoàn tất, file lạ hoặc thiếu → blocker. Không dùng API/Quiz/Interview related để đoán Video; X:AA Excel nằm ngoài scope.
3. Mỗi file VI đã có phải có **đúng một** file EN cùng relative path. Nếu EN chưa tồn tại: **Prime** seed đúng bytes VI vào EN sau kiểm tra symlink/ownership và tạo backup/manifest trong `<I3>/build/optional-video-translate/<RUN_ID>/`. Nếu EN đã có: giữ nguyên, không clone đè bản dịch; kiểm tra trạng thái/semantic diff và resume file còn thiếu. Prime không dịch nội dung.
4. Prime lập ledger hash VI/EN, số chapters và assign **một file EN độc quyền cho mỗi lượt worker**. Pool Mode A mặc định 3, Mode B theo N của session; ưu tiên worker SLEEPING, spawn tuần tự. Worker không đọc VI/source khác; file EN đã được seed VI chứa đủ chữ để tự dịch.

## 2. Worker chỉ dịch một Video EN file mỗi lượt

Giao đúng bốn dòng, không paste nội dung hoặc hướng dẫn:

```text
MODULE=<VERIFIED_MODULE_ROOT_RELATIVE_PATH>
FILE_TYPE=VIDEO | EN_TARGET=<ABSOLUTE_EN_VIDEO_YAML> | EXPECTED_SHA256=<CURRENT_HASH>
READ/WRITE=EXACT_EN_TARGET_ONLY | OWNER=<WORKER_ID>
/java-learning/module-generate-agent/worker/STEP_OPTIONAL_VIDEO_TRANSLATE_WORKER.md
```

- Worker tự đọc toàn văn file worker-only, dịch các scalar hiển thị: `title`, `sections[].title`, `sections[].items[].title` (nếu có), `visual`, `script`, `purpose`. Giữ `video.url`, `type`, `time`, scene/transition count/order, code, URL, định danh và cấu trúc YAML bất biến.
- Prime exact readback, so cấu trúc/scenes/timeline/field khóa VI↔EN, QA tiếng Anh tự nhiên/chính xác kỹ thuật, bảo toàn toàn bộ scene. Nếu thiếu bản dịch, loss, YAML lỗi → REVIEW/BLOCKER. Không ghi vào Google Sheets từ worker.
- Chỉ khi **toàn bộ Video EN file** đã PASS và không còn writer mới được xem là hoàn thành phần dịch nội dung offline; không publish Sheet hay cập nhật XLSX Video trong phiên bản hiện tại.

## 3. Lưu Video EN offline (Prime only)

- Không tái chạy core `cloneModuleViToEn` hoặc task STEP 11. Không gọi `publishModuleEnSheet` / `publishOptionalVideoEn` hay cập nhật Google Sheets.
- Canonical EN Video: `content/readme/docs/en/menu/<chapter>/video/video-script.yml`; sau semantic QA, file đã được lưu offline ngay trong module. STEP 12 Phase 11 chủ động để trống Video X:AA. Việc xuất Video vào XLSX sẽ được làm sau khi ExcelVideoService được mở rộng; không tự chèn dữ liệu vào XLSX ngoài contract hiện tại.
- Giữ `master.json.CURRENT_STEP.VALUE="FINAL"`. Không tự commit/push; sau optional workflow có thể chạy lại STEP_FINAL theo yêu cầu.

## 4. Completion

- COMPLETED chỉ cho **Video EN content translation** khi các Video EN YAML đã được QA, không có job/writer PENDING/BLOCKER, và module/Knowledge/Quiz/Interview/Swagger khác bất biến. Trạng thái offline Excel Video = NOT_IMPLEMENTED, không nhận nhầm là đã đồng bộ.
- Báo `MODULE,VIDEO_FILES,WORKER_QA,TRANSLATION_COVERAGE,EN_FILES_READBACK,CURRENT_STEP_UNCHANGED,XLSX_VIDEO=NOT_IMPLEMENTED,BLOCKERS`.
