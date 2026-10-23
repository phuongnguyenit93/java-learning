# STEP 12 — PRIME ONLY: Export một tab Google Sheets thành `content/sheet/vi/module.xlsx`

## 0. Mục tiêu và phạm vi

STEP 12 là bước **lưu snapshot Excel offline của đúng một tab module tiếng Việt**, sau khi những nội dung và liên kết thuộc STEP 1–11 đã được xử lý/QA. **Không dịch sang EN trong bước này** (bản dịch/chuyển ngữ sẽ thuộc STEP tiếp theo).

- **Prime tự thực hiện toàn bộ, KHÔNG giao worker**; không cần `STEP_12_WORKER.md`, không spawn hoặc đánh thức worker để export.
- **Dùng task đã có**: `:<EXACT_MODULE_GRADLE_PATH>:exportModuleSheetToExcel`. Không viết exporter mới, không tự dùng Google Sheets download cả workbook, không dùng `generateModuleContentFromSheetByFiles`, các importer khác hay task import ngược Excel lên Sheet.
- Nguồn: **một tab** được xác minh từ `module-generate-info.txt` (workbook URL + gid). Đích duy nhất: `<I3>/content/sheet/vi/module.xlsx`, chứa **đúng một worksheet**. Không tạo `content/sheet/en/module.xlsx` ở STEP 12.
- Google Sheets là **nguồn dữ liệu**; Excel chỉ là bản snapshot offline, **không thay thế Sheet/README/YAML canonical**. Không sửa bất kỳ nội dung Knowledge, Quiz, Interview, Video hay tab Google Sheets nào, ngoại trừ **`J3 = STEP 12`** sau completion gate.
- Không tự commit/push, tạo PR, chuyển sang STEP 13 hoặc upload/sao chép `module.xlsx` sang nơi khác. File Excel đã bị ignore bằng `**/content/sheet/*/module.xlsx` trong `.gitignore`.

## 1. Prime preflight bắt buộc

1. Đọc **toàn văn** `module-generate-agent/GENERAL_AGENT_RULES.md`, liệt kê và đọc **toàn bộ** `module-generate-agent/rules/` (đặc biệt `PRIME_RULES.md`), đọc file STEP 12 này và `module-generate-agent/module-generate-info.txt`. Đây là **Prime-only**, không có hợp đồng bốn dòng cho worker.
2. Từ TXT lấy `Excel Page URL` và chính xác mapping **tên module → sheet id (gid)**; xác minh spreadsheet ID, tên tab, gid và URL gốc. Không nhầm workbook ID với gid, không lấy tab lân cận hoặc `Template`, không hardcode URL từ session cũ. Nếu mapping trùng/xung đột → `BLOCKER: SHEET MAPPING`.
3. Đọc tab đích: `A1`, `B1`, `I3`, `J2:J3`, header và vùng dữ liệu cần kiểm kê. **Xác minh `J2 = Current STEP`** và đọc cả giá trị/formula/note của `J3`. Xác định STEP 11 đã hoàn tất bằng **bằng chứng nguồn/đầu ra thật**, không chỉ tin `J3` (lưu ý lịch sử đổi số STEP). Nếu có worker hoặc thao tác ghi dữ liệu cùng tab đang hoạt động, dừng để tránh chụp snapshot nửa chừng.
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
2. Đối chiếu tổng ô được xuất với log `cells`, hàng/cột có dữ liệu thực, các mốc `A1`, `B1`, `I3`, `A17:B17`, `A19:B19`, `J2:J3`, một số hàng Menu Cấp 1/Cấp 2, `C:N` Knowledge, `O` Difficulty, `P:S` Interview, `T:W` Quiz, `X:AA` Video và `AB` isImportant **nếu vùng đó có dữ liệu**. Vùng trống hợp lệ không phải lý do tự sinh dữ liệu. Nếu cần chứng minh đầy đủ, so sánh snapshot từng ô đã nhập/giá trị effective của Google Sheet với nội dung Excel, không chỉ kiểm tra số lượng.
3. Kiểm tra **newline/Unicode**, rich text cơ bản/format quan trọng, freeze panes, merged cells, note, row height, column width và dropdown cơ bản; báo rõ `unsupported-dropdowns` từ task. Nếu dropdown quan trọng (đặc biệt Difficulty `O` hoặc isImportant `AB`) bị mất/không tương thích, `BLOCKER: XLSX VALIDATION LOSS` hoặc giải trình và nhận xác nhận rõ trước khi coi là bản sao đạt yêu cầu. Không mặc định mọi định dạng Google-only đều có thể tái hiện trong Excel.
4. **Formula:** task lưu **giá trị đã tính (effective/cached)**, không giữ công thức live. So sánh theo giá trị hiển thị/effective, không đòi formula gốc còn tồn tại. Nếu công thức lỗi/chưa resolve, text dài hơn **32.767 ký tự mỗi ô**, lỗi đọc Sheets hoặc giới hạn Excel thì task phải FAIL; **không cắt bớt/truncated**, không tự thay công thức.
5. Sau APPLY, đọc lại nguồn Google Sheet hoặc snapshot nguồn đã xác thực: **không thay đổi nội dung** (ngoài bước cập nhật `J3` riêng ở mục 4), không chạm `Template` hoặc module khác. Xác minh `git status` không đưa `.xlsx` vào staging và output đang bị `.gitignore` bỏ qua. Nếu có bất kỳ QA fail: giữ lại bằng chứng/hash, **không ghi J3**.

## 4. Completion gate và đồng bộ J3 vào bản Excel

**Chỉ khi tất cả preflight, DRY_RUN, APPLY, XLSX readback và integrity QA ở mục 1–3 PASS**, Prime mới thực hiện cập nhật trạng thái trên **chính tab nguồn**:

1. Đọc lại `J2`, `J3` **ngay trước WRITE**; đối chiếu gid/module, entered value/formula/notes và STEP history như `rules/PRIME_RULES.md §2`. Không xóa công thức, không viết lùi, không ghi đè thay đổi đồng thời; mâu thuẫn → `BLOCKER: CURRENT STEP UPDATE` và giữ nguyên J3.
2. Ghi **`J3 = STEP 12`** bằng Sheets write với quyền được cấp riêng cho trạng thái; **exact readback `J3` phải bằng nguyên văn `STEP 12`**. Worker không tồn tại trong STEP này; Prime chịu trách nhiệm toàn bộ.
3. **Quan trọng — snapshot trạng thái cuối:** bản Excel ở mục 3 được xuất trước khi J3 thay đổi, nên có thể còn `J3 = STEP 11`. Vì vậy **sau khi Sheet J3 đã readback `STEP 12`**, Prime chạy lại **cùng một task** `DRY_RUN → APPLY` trên đúng URL/gid/module, rồi mở lại `module.xlsx` và xác minh **Excel `J3 = STEP 12`** cùng toàn bộ dữ liệu khác vẫn đúng. Không chấp nhận hoàn thành khi Sheet và Excel khác trạng thái STEP.
4. Nếu lượt export sau J3 thất bại, báo `BLOCKER: XLSX FINAL STATUS SNAPSHOT`, không tuyên bố hoàn tất. Chỉ khôi phục J3 về giá trị cũ **khi xác minh không có chỉnh sửa đồng thời và có snapshot trạng thái gốc đáng tin cậy**; nếu không đủ điều kiện, giữ nguyên, báo trạng thái bị lệch để phục hồi có kiểm soát. Không lặp APPLY mù hoặc xóa snapshot offline đang còn dùng được.
5. **Gate cuối:** Google Sheet `J3 = STEP 12`, Excel `J3 = STEP 12`, workbook một worksheet và dữ liệu còn nguyên, file đích hợp lệ, không còn blocker → `COMPLETED`. Nếu file đã tồn tại và hoàn toàn khớp sau readback, có thể báo `UNCHANGED` thay cho ghi lại byte không cần thiết, nhưng vẫn phải hoàn thành tất cả gate còn thiếu.

## 5. Resume, giới hạn và báo cáo Prime

- Nếu session dừng giữa chừng, **kiểm tra thực tế** source gid/I3/J3, file đích, mtime/hash và log/checkpoint trước khi chạy lại. Không mặc định file đang có là bản mới, không APPLY lại chỉ vì lượt trước mất tool output. Phân biệt `DRY_RUN_ONLY`, `EXPORTED_WAITING_J3`, `J3_UPDATED_WAITING_FINAL_EXPORT`, `COMPLETE` và `BLOCKER`.
- Lúc nào cũng bảo toàn nội dung Google Sheets, không thay đổi data validation/notes/formats trực tiếp trên Sheet, không chỉnh README/Swagger/Roadmap/Quiz/Interview/Video. Không đọc/ghi module khác. Nếu user chỉ yêu cầu chạy thử (trial), **không cập nhật J3 = STEP 12** hoặc coi toàn STEP đã xong.
- Báo cáo bắt buộc: `MODULE_I3`, `TAB_NAME`, `TAB_GID`, `SOURCE_URL`, `OUTPUT_PATH`, `SOURCE_J3`, `DRY_RUN_STATUS`, `APPLY_STATUS`, `XLSX_WORKSHEETS`, `SOURCE/EXPORTED_CELLS`, `UNSUPPORTED_DROPDOWNS`, `FORMULA_SNAPSHOT`, `FILE_HASH`, `EXCEL_QA`, `J3_READBACK`, `XLSX_J3_READBACK`, `FINAL_EXPORT`, `GIT_STATUS`, `BLOCKER`.
- **Không commit/push** hoặc tự chạy STEP 13. STEP 12 chỉ tạo **một file Excel offline của đúng module**, bị Git ignore theo thiết kế.
