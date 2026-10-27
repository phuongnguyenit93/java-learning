# STEP 12 — PRIME ONLY: Export một tab Google Sheets thành `content/sheet/vi/module.xlsx`



> **STEP 8 extension:** Khi Sheet có Practice, snapshot phải giữ đủ **33 cột A:AG**, bao gồm AD18/AD19:AG19 và toàn bộ YAML AD:AG hàng cha. `AD:AG` là **một YAML `scenarios[]` của mỗi chapter chia thành fragment**, không được xuất riêng từng scenario. AC vẫn chứa Summary theo quy tắc cũ. Excel giữ giới hạn 32.767 UTF-16 units/ô.

## 0. Mục tiêu và phạm vi

STEP 12 là bước **lưu snapshot Excel offline của đúng một tab module tiếng Việt**, sau khi những nội dung và liên kết bắt buộc thuộc STEP 1–11 đã được xử lý/QA (**STEP 9 là Interview, STEP 10 là Quiz; Video là optional sau STEP_FINAL**). **Không dịch sang EN trong bước này** (bản dịch/chuyển ngữ thuộc STEP 14). Các ô Video X:AA còn trống hoàn toàn hợp lệ; Video được tạo riêng sau STEP_FINAL khi người dùng yêu cầu.

- **Prime tự thực hiện toàn bộ, KHÔNG giao worker**; không cần `STEP_12_WORKER.md`, không spawn hoặc đánh thức worker để export.
- **Dùng task đã có**: `:<EXACT_MODULE_GRADLE_PATH>:exportModuleSheetToExcel`. Không viết exporter mới, không tự dùng Google Sheets download cả workbook, không dùng `generateModuleContentFromSheetByFiles`, các importer khác hay task import ngược Excel lên Sheet.
- Nguồn: **một tab** được xác minh từ `module-generate-info.txt` (workbook URL + gid). Đích duy nhất: `<I3>/content/sheet/vi/module.xlsx`, chứa **đúng một worksheet**. Không tạo `content/sheet/en/module.xlsx` ở STEP 12.
- Google Sheets là **nguồn dữ liệu**; Excel chỉ là snapshot offline, không thay thế Sheet/README/YAML canonical. **Không sửa bất kỳ nội dung hoặc trạng thái nào trên Google Sheets.** Khi STEP 12 hoàn tất, Prime chỉ cập nhật `CURRENT_STEP.VALUE = "12"` trong `master.json`.
- Không tự commit/push, tạo PR, chuyển sang STEP 13 hoặc upload/sao chép `module.xlsx` sang nơi khác. File Excel đã bị ignore bằng `**/content/sheet/*/module.xlsx` trong `.gitignore`.

## 1. Prime preflight bắt buộc

1. Đọc **toàn văn** `module-generate-agent/GENERAL_AGENT_RULES.md`, liệt kê và đọc **toàn bộ** `module-generate-agent/rules/` (đặc biệt `PRIME_RULES.md`), đọc file STEP 12 này và `module-generate-agent/module-generate-info.txt`. Đây là **Prime-only**, không có hợp đồng bốn dòng cho worker.
2. Từ TXT lấy `Excel Page URL` và chính xác mapping **tên module → sheet id (gid)**; xác minh spreadsheet ID, tên tab, gid và URL gốc. Không nhầm workbook ID với gid, không lấy tab lân cận hoặc `Template`, không hardcode URL từ session cũ. Nếu mapping trùng/xung đột → `BLOCKER: SHEET MAPPING`.
3. Đọc tab đích: `A1`, `B1`, `I3`, header và vùng dữ liệu cần kiểm kê. Đọc riêng `CURRENT_STEP.VALUE` từ `master.json` đã xác minh theo I3; chứng minh STEP 11 hoàn tất bằng nguồn/đầu ra thật, không chỉ tin metadata. Nếu worker hoặc thao tác ghi Sheet đang hoạt động, dừng để tránh snapshot nửa chừng.
4. Xác minh `I3` **khớp chính xác root-relative path** của **một module Gradle thật nằm trong `java-learning/module/`**, có task `exportModuleSheetToExcel` được đăng ký. Đối chiếu task Gradle path được khai báo trong settings/module; **không suy diễn từ tên tab**. Không cho phép đường dẫn vượt module/junction/symlink tới nơi khác; importer/exporter phải tự xác minh I3 lần nữa. Sai → `BLOCKER: MODULE I3 MISMATCH`.
5. Kiểm tra file đích đã tồn tại hay chưa; nếu đã có, ghi lại size, hash SHA-256, modification time và trạng thái local để phát hiện thay đổi đồng thời trước APPLY. Không xóa file cũ trước; task có cơ chế ghi file tạm, kiểm tra rồi atomic replace. **Không ghi đè bản Excel người dùng đang chỉnh sửa** hoặc file không rõ ownership; nếu có xung đột → `BLOCKER: EXISTING OFFLINE SNAPSHOT`.
6. Kiểm tra task setup và quyền Google ADC (service dùng Sheets API read-only), JDK/Gradle tương thích. Không hardcode credential/token, không tự đổi cấu hình hệ thống hoặc dùng Google Sheets write scope cho export. Nếu task hoặc xác thực không chạy được → `BLOCKER: XLSX EXPORT CONFIG`.

## 2. Task module-local có sẵn (không tạo service mới)

Nguồn code để Prime đối chiếu nếu task lỗi hoặc drift:

- `project-build/gradle-runtime/src/main/groovy/com/example/learning/task/sheets/task/ExportModuleSheetToExcelTask.groovy`
- `project-build/gradle-runtime/src/main/groovy/com/example/learning/task/sheets/service/ModuleSheetExcelExportService.groovy`
- `project-build/gradle-runtime/SHEET_EXCEL_EXPORT.md`

Chạy ở **root `java-learning`**, thay đủ **ba placeholder** (`<EXACT_MODULE_GRADLE_PATH>`, `<SPREADSHEET_ID>`, `<GID>`). Sử dụng PowerShell hoặc terminal tương đương; URL tab có `#gid` **phải được quote**.

```powershell
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:exportModuleSheetToExcel `
  "-PfileURL=https://docs.google.com/spreadsheets/d/<SPREADSHEET_ID>/edit" `
  "-PsheetURL=https://docs.google.com/spreadsheets/d/<SPREADSHEET_ID>/edit#gid=<GID>" `
  -Planguage=vi `
  -PsheetMode=DRY_RUN `
  --console=plain
```

- **DRY_RUN mặc định**: yêu cầu exit code 0, log `[SHEET-XLSX] DRY_RUN`, `module`, `tab/gid`, `language=vi`, `cells`, `unsupported-dropdowns`, `output` đúng; không được thay `module.xlsx` ở lượt này. `DRY_RUN` **chưa phải** hoàn thành STEP.
- **APPLY**: chỉ khi DRY_RUN PASS và nguồn không thay đổi, chạy **chính xác cùng task/tham số**, đổi **duy nhất** `-PsheetMode=APPLY`. Yêu cầu exit code 0 và `[SHEET-XLSX] EXPORTED`; task tự tạo file tạm, reopen bằng Apache POI, kiểm tra worksheet/cell count và atomic replace.
- Không gọi task root, không xuất cả workbook, không dùng `importExcelFromModuleSheet` (task đó tạo tab **mới** trên Google Sheets, hoàn toàn không thuộc STEP 12).

## 3. QA snapshot Excel sau APPLY — bắt buộc

Prime phải **tự mở và kiểm tra file offline**, không chỉ tin exit code hoặc dòng `EXPORTED`:

1. Đường dẫn **chính xác** `<I3>/content/sheet/vi/module.xlsx`, file `.xlsx` hợp lệ, không phải file rỗng; mở được bằng Apache POI/openpyxl hoặc trình đọc XLSX phù hợp. **Đúng 1 worksheet**, tên trùng tab/gid đã xác minh. Không có worksheet khác trong workbook.
2. Đối chiếu tổng ô được xuất với log `cells`, hàng/cột có dữ liệu thực, các mốc `A1`, `B1`, `I3`, `A17:B17`, `A19:B19`, Menu Cấp 1/Cấp 2, `C:N` Knowledge, `O` Difficulty, `P:S` Interview, `T:W` Quiz, `X:AA` Video, `AB` isImportant, `AC` Summary và **`AD:AG` Practice**, nếu nguồn có. Với Practice: kiểm tra đúng header, 33 cột, ghép AD→AG không separator, YAML COMPLETE và exact readback. Vùng trống hợp lệ không phải lý do tự sinh dữ liệu. So sánh snapshot với nguồn Sheet bằng giá trị effective; **không yêu cầu Excel chứa tiến độ master.json**.
3. Kiểm tra **newline/Unicode**, rich text cơ bản/format quan trọng, freeze panes, merged cells, note, row height, column width và dropdown cơ bản; báo rõ `unsupported-dropdowns` từ task. Nếu dropdown quan trọng (đặc biệt Difficulty `O` hoặc isImportant `AB`) bị mất/không tương thích, `BLOCKER: XLSX VALIDATION LOSS` hoặc giải trình và nhận xác nhận rõ trước khi coi là bản sao đạt yêu cầu. Không mặc định mọi định dạng Google-only đều có thể tái hiện trong Excel.
4. **Formula:** task lưu **giá trị đã tính (effective/cached)**, không giữ công thức live. So sánh theo giá trị hiển thị/effective, không đòi formula gốc còn tồn tại. Nếu công thức lỗi/chưa resolve, text dài hơn **32.767 ký tự mỗi ô**, lỗi đọc Sheets hoặc giới hạn Excel thì task phải FAIL; **không cắt bớt/truncated**, không tự thay công thức.
5. Sau APPLY, đọc lại Google Sheet hoặc snapshot nguồn đã xác thực: **không thay đổi bất kỳ nội dung/tiến độ Sheet nào**, không chạm Template hoặc module khác. Xác minh `.xlsx` vẫn bị Git ignore. Nếu bất kỳ QA fail: giữ bằng chứng/hash, **không ghi `CURRENT_STEP.VALUE`**.

## 4. Completion gate: update master.json only

After Sheet export DRY_RUN/APPLY and ALL Excel QA PASS, Prime reads `<I3>/master.json`, verifies `CURRENT_STEP.TYPE = "string"`, confirms no concurrent edit and updates only `CURRENT_STEP.VALUE = "12"`. Exact readback is required, theo `rules/PRIME_RULES.md` `2.

**Never update the source Sheet or exported Excel progress cell**, and **do not export Excel a second time** just to write a STEP value. The xlsx is a data snapshot, not the source of STEP status. If master.json write/readback fails, report `BLOCKER: CURRENT_STEP_UPDATE`; keep the validated XLSX intact for safe recovery.

## 5. Resume, giới hạn và báo cáo Prime

- Nếu session dừng giữa chừng, **kiểm tra thực tế** source gid/I3/CURRENT_STEP.VALUE, file đích, mtime/hash và log/checkpoint trước khi chạy lại. Không mặc định file đang có là bản mới, không APPLY lại chỉ vì lượt trước mất tool output. Phân biệt `DRY_RUN_ONLY`, `EXPORTED_WAITING_MASTER_UPDATE`, `COMPLETE` và `BLOCKER`.
- Lúc nào cũng bảo toàn nội dung Google Sheets, không thay đổi data validation/notes/formats trực tiếp trên Sheet, không chỉnh README/Swagger/Roadmap/Quiz/Interview/Video. Không đọc/ghi module khác. Nếu user chỉ yêu cầu chạy thử (trial), **không cập nhật CURRENT_STEP.VALUE = "12"** hoặc coi toàn STEP đã xong.
- Báo cáo bắt buộc: `MODULE_I3`, `TAB_NAME`, `TAB_GID`, `SOURCE_URL`, `OUTPUT_PATH`, `SOURCE_CURRENT_STEP.VALUE`, `DRY_RUN_STATUS`, `APPLY_STATUS`, `XLSX_WORKSHEETS`, `SOURCE/EXPORTED_CELLS`, `UNSUPPORTED_DROPDOWNS`, `FORMULA_SNAPSHOT`, `FILE_HASH`, `EXCEL_QA`, `CURRENT_STEP.VALUE_READBACK`, `FINAL_EXPORT`, `GIT_STATUS`, `BLOCKER`.
- **Không commit/push** hoặc tự chạy STEP 13. STEP 12 chỉ tạo **một file Excel offline của đúng module**, bị Git ignore theo thiết kế.
