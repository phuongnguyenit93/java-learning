# CURRENT_STEP — nguồn duy nhất của tiến độ module

Từ 2026-10-25, **không đọc/ghi tiến độ STEP ở Excel/Google Sheets**. Cột hoặc ô `Current STEP` còn tồn tại trong template/tab cũ chỉ là di sản; không được sử dụng để quyết định hoặc cập nhật tiến độ. Khả năng tìm module từ Google Sheets `I3` vẫn giữ nguyên.

## Nguồn lưu trữ

Đúng một file `<I3>/master.json` của module đã được xác minh, khóa chuẩn canonical:

```json
"CURRENT_STEP": {
  "DESCRIPTION": "Last successfully completed module generation step (0, numeric STEP, 12A, FINAL)",
  "TYPE": "string",
  "VALUE": "0"
}
```

- Giá trị **string**, không dùng số JSON. Mặc định `"0"`, frontend ẩn Step 0.
- STEP số nguyên hoàn thành: `"1"`, `"2"`, ... `"12"`, `"14"`, `"15"`.
- STEP đặc biệt đã hoàn thành: `"12A"` sau STEP 12A; `"FINAL"` sau STEP_FINAL. Frontend hiển thị `Step 12A` và `Finished` tương ứng.
- STEP 8/13 chưa định nghĩa không tự đánh dấu hoàn thành. STEP_OPTIONAL_VIDEO/STEP_OPTIONAL_VIDEO_TRANSLATE không đổi giá trị `"FINAL"`.
- Đây là **STEP đã hoàn thành**; worker không được ghi file, Prime không cập nhật lúc STEP chưa đủ QA.

## Trách nhiệm Prime — áp dụng cho mọi STEP có completion gate

1. Xác minh Google Sheet tab/gid và `I3` từ module-generate-info, đường dẫn thực bên trong `java-learning/module/`, `master.json` có schema chuẩn, worktree cùng quyền sở hữu. Không suy luận module chỉ từ tên tab.
2. Trước khi thực thi, đọc và snapshot `CURRENT_STEP.TYPE`/`VALUE` trong `master.json`. Kiểm tra `TYPE=string` và VALUE thuộc các giá trị hợp lệ, không phải formula/số/ngữ cảnh Excel. File chưa có khóa: chạy canonical module configuration sync an toàn để thêm mặc định `"0"` rồi exact readback; không tự chế cấu trúc.
3. Chỉ khi mọi worker, import, build/test, QA/readback và các blocker của **chính module** đều PASS/UNCHANGED, Prime mới thay **duy nhất** `CURRENT_STEP.VALUE` thành string kết quả tương ứng. Nếu có nhiều module, ghi độc lập theo module đã QA, không ghi hàng loạt dựa vào tên Sheet.
4. Trạng thái chỉ tiến lên theo thứ tự: `0 → 1 → 2 → 3 → 4 → 5 → 6 → 7 → 9 → 10 → 11 → 12 → 12A → 14 → 15 → FINAL`. Nếu giá trị cũ đã ở bước muộn hơn, không ghi lùi. Nếu muốn rerun STEP cũ, giữ nguyên trạng thái cao hơn sau khi QA. Nếu nguồn hiện hữu không hợp lệ, báo `BLOCKER: CURRENT_STEP_UPDATE`, không suy đoán, không ghi đè.
5. Cập nhật tệp an toàn, giữ nguyên **toàn bộ key và VALUE khác**, dùng backup/atomic replace hoặc patch an toàn và exact readback sau ghi. Nếu file bị sửa đồng thời, dừng và đối chiếu; không ghi mù. **Không cập nhật Google Sheets J2/J3**, không sửa Template, các tab module khác hay tiến độ module khác.
6. Báo `CURRENT_STEP_MODULE_PATH`, `CURRENT_STEP_BEFORE`, `CURRENT_STEP_AFTER`, `CURRENT_STEP_READBACK` và blocker (nếu có). Nếu update thất bại, không tuyên bố STEP đã đóng đầy đủ.

## Luồng hiển thị

`module/<...>/master.json` → `ProjectStructureService` → `module-catalog.json` (`currentStep`) → `build-module-navigation.mjs` → `module-navigation.json` → Module Picker.

UI: `"0"` hoặc thiếu field (catalog cũ) thì ẩn; các STEP số thì hiện `Step N`; `"12A"` là `Step 12A`; `"FINAL"` là `Finished`. Dữ liệu Portal được xuất bản tĩnh, muốn thấy thay đổi cần rebuild Java data, sync Java data sang learning-platform và deploy.

**Migration:** Không tự backfill các module chưa được xác minh từ các ô Excel cũ. Đợt UI thử nghiệm này chỉ đặt OOP ở `"5"`, các module khác mặc định `"0"`.
