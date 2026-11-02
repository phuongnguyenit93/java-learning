# STEP 1 — Tạo sheet cho chủ đề mới

## Mục tiêu

Khi người dùng đưa ra **một chủ đề cần generate nội dung**, xác minh thư mục module trong `java-learning`, **khởi tạo `gradle.properties` nếu module chưa có và chạy Gradle sync để sinh cấu hình canonical cần thiết**, sau đó tạo **một tab (sheet) mới** trong Google Sheets bằng cách **nhân bản tab `Template`**, đặt tên tab theo chủ đề, ghi tên chủ đề vào ô `B1`, điền đường dẫn module tương ứng vào ô `I3` của tab mới, **định dạng 700 hàng đầu của toàn bộ các cột trên tab mới**, và lưu **sheet ID (gid)** của tab đó vào file TXT để các STEP sau định vị chính xác.

STEP này chuẩn bị **Gradle module và sheet**, không generate Roadmap, Menu hoặc nội dung bài học.

## Nguồn Google Sheets

Đọc file `module-generate-agent/module-generate-info.txt` trong repository hiện tại và lấy URL ở dòng dưới nhãn `Excel Page URL`.

Giá trị hiện tại trong file:

`https://docs.google.com/spreadsheets/d/1UuhvmOvlIg3x2EAz4mAg2o7EuWVIe8gd-QGf5EkoR_8`

**Luôn ưu tiên đọc URL từ file `.txt` ở thời điểm thực thi.** URL hiển thị bên trên chỉ để đối chiếu, không thay thế nguồn cấu hình nếu file được cập nhật sau này.

## Đầu vào

- Một prompt của người dùng nêu rõ chủ đề, ví dụ: `Tạo module về Spring Boot`.
- Chủ đề rút ra từ prompt: `Spring Boot`.

Nếu prompt chưa thể hiện rõ chủ đề, hỏi lại để xác nhận; không tự đoán và không tạo sheet rỗng.

## Module Project — Location

Vùng mới trên tab `Template` gồm:

- **`I1`**: tiêu đề `Module Project`.
- **`I2`**: nhãn `Location`.
- **`I3`**: ô **giá trị** cần điền cho module tương ứng sau khi nhân bản. Không ghi đường dẫn vào `I1` hoặc `I2` và không sửa hai ô tiêu đề này.

`Location` là **đường dẫn tương đối của thư mục module tính từ thư mục gốc repository `java-learning/`**, **không** tính từ `java-learning/module/`. Vì vậy giá trị thường bắt đầu bằng `module/`; dùng dấu `/` thống nhất, không chứa `java-learning/` ở đầu, đường dẫn tuyệt đối của máy, URL, hoặc tên file con như `README.md`.

Ví dụ đã đối chiếu với cây module thực tế:

| Tab / chủ đề | `I3` — Location |
| --- | --- |
| `OOP` | `module/platform/development/programming/paradigm/object-oriented` |
| `Aspect` | `module/platform/development/programming/paradigm/aop` |

**Cách xác định:** Dò cấu trúc trong `project-build/gradle-runtime/src/main/resources/structure/module-structure.txt` và **đối chiếu thư mục thực tế** dưới `java-learning/module/`, cùng định danh/metadata module nếu cần. Tên tab có thể khác tên folder (`OOP` ↔ `object-oriented`); không suy ra đường dẫn bằng cách tự động slugify tên chủ đề. Nếu người dùng cung cấp Location, vẫn phải chuẩn hóa theo mốc `java-learning/` và kiểm tra đường dẫn tương ứng. Phải tìm được **đúng một thư mục module hợp lệ**; nếu chưa tồn tại, không xác định được hoặc có nhiều kết quả, **báo BLOCKER và làm rõ trước khi clone** thay vì điền path phỏng đoán hay để `I3` rỗng rồi báo hoàn thành. **Riêng việc chưa có `gradle.properties` không phải lý do loại thư mục module hợp lệ:** STEP 1 sẽ khởi tạo file này theo bước 6, nhưng không được dùng việc tạo file để hợp thức hóa một đường dẫn chưa xác minh.

## Quy tắc bắt buộc — `SERVICE_NAME` trong `master.json`

- `SERVICE_NAME.VALUE` là **định danh kỹ thuật của module**, không phải tên hiển thị tab/`B1`. Bắt buộc viết **UPPER_SNAKE_CASE**: chữ hoa ASCII, các từ cách nhau bằng một dấu `_`, chữ số chỉ được xuất hiện sau chữ cái đầu. Regex: `[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*`. Không có dấu cách, chữ thường, dấu `-`, dấu tiếng Việt, ký tự đặc biệt, `_` đầu/cuối hoặc `__` liên tiếp.
- Ví dụ: `Object Oriented` → `OBJECT_ORIENTED`; `Object Oriented Programming` → `OBJECT_ORIENTED_PROGRAMMING`; `Spring Boot` → `SPRING_BOOT`; `Spring Cloud Gateway` → `SPRING_CLOUD_GATEWAY`. Với tên chủ đề mới, chuyển khoảng trắng thành `_`, viết hoa; tên có ký tự không phải ASCII phải xác nhận định danh phù hợp, **không tự đoán**.
- **Giữ nguyên tên đã tồn tại:** Đối chiếu `master.json`, tên đầy đủ của module và catalog `ModuleListEnum` trước khi quyết định. Ví dụ module OOP đã có `OBJECT_ORIENTED_PROGRAMMING`: **không tự đổi thành `OOP` hoặc `OBJECT_ORIENTED`** chỉ vì tên tab. Không dùng tên folder làm nguồn duy nhất; không rename/backfill hàng loạt các module cũ.
- Tên mới **phải duy nhất trên toàn module catalog**. Trùng tên/mơ hồ → `BLOCKER: SERVICE_NAME_CONFLICT`. VALUE đã có, khác rỗng nhưng sai format → `BLOCKER: SERVICE_NAME_FORMAT`, **không tự sửa nội dung đã có**. VALUE rỗng của module vừa canonical sync: Prime phải điền định danh đúng format sau sync, rồi sync lại và exact readback, không dùng fallback tạm do Gradle tự tạo làm tên chính thức.

## Quy trình thực hiện

1. **Đọc cấu hình:** Mở `module-generate-info.txt`, xác định URL và spreadsheet ID hợp lệ.
2. **Kiểm tra nguồn:** Mở spreadsheet đó, xác nhận tồn tại tab nguồn có tên chính xác là `Template`; xác nhận `Template!I1` là `Module Project`, `Template!I2` là `Location` và `Template!I3` chưa chứa giá trị/công thức cần bảo toàn. Nếu bố cục hoặc ô đích không đúng, dừng để tránh ghi nhầm.
3. **Xác định Location:** Từ chủ đề, tìm đúng module trong `module-structure.txt`, đối chiếu thư mục thực tế và lấy đường dẫn **từ `java-learning/`**, bao gồm tiền tố `module/`. Xác nhận Location không rỗng, duy nhất, chính xác trước khi clone; nếu không xác định được, báo BLOCKER theo mục trên.
4. **Xác định tên tab mới:** Dùng tên chủ đề mà người dùng cung cấp, giữ nguyên cách viết hợp lý. Nếu tên không hợp lệ theo quy định Google Sheets (ví dụ ký tự cấm, tên quá dài), chỉ chuẩn hóa phần bắt buộc và thông báo tên cuối cùng.
5. **Kiểm tra trùng tên:** Nếu tab cùng tên đã tồn tại, **không nhân bản lần nữa và không ghi đè dữ liệu**. Báo cho người dùng biết để họ quyết định dùng tab cũ hay đặt tên mới.
6. **Bootstrap `gradle.properties` cho đúng module:** Chỉ thao tác tại `<Location đã xác minh>/gradle.properties`, không duyệt toàn bộ repository để tự tạo file hàng loạt.
   - Nếu **chưa tồn tại**, tạo file mới bằng UTF-8 (không BOM), nội dung chính xác `IS_MODULE=true` và một newline cuối file (`IS_MODULE=true\n`). Không tạo `gradle.properties` trong thư mục cha, root, module khác hoặc đường dẫn suy đoán.
   - Nếu **đã là file**, giữ nguyên **toàn bộ nội dung** (kể cả các property khác hoặc giá trị `IS_MODULE` hiện hữu); không ghi đè, không tự đổi `FALSE` thành `true`. Trong `gradle-runtime` hiện tại, scanner phát hiện module bằng **sự hiện diện của `gradle.properties`**, không filter theo giá trị `IS_MODULE`.
   - Nếu đường dẫn đã tồn tại nhưng không phải file, ghi thất bại, hoặc có người khác đang sửa cùng module, báo `BLOCKER: MODULE_GRADLE_PROPERTIES`. Readback xác nhận file có mặt và, nếu vừa tạo, nội dung đúng `IS_MODULE=true`.
7. **Chạy Gradle sync và xác minh cấu hình:** Sau bước 6, từ **gốc `java-learning/`** chạy `.\\gradlew.bat help --no-daemon --console=plain` (Windows/PowerShell; hệ Unix dùng `./gradlew help --no-daemon --console=plain`). Đây là bước **Gradle Settings initialization**, không phải task import sheet: `PresetSetupPlugin` gọi `SettingScannerService` để phát hiện/include module rồi `ModuleConfigurationSyncService.sync()` sinh/đồng bộ **`master.json`**, **`properties.json`** từ canonical resources `automation/master.json`, `automation/properties.json`; `SettingInfoGeneratorService` cập nhật catalog **`ModuleListEnum.groovy`**, **`DatabaseListEnum.groovy`**.
   - Trước khi sync, snapshot các file cấu hình **đã có** của module (`master.json`, `properties.json`, nhất là `CURRENT_STEP`) và kiểm tra thay đổi đang có trong worktree để không nhận nhầm/revert thay đổi từ việc khác. Gradle Settings có thể sync **các module khác** đã được scanner nhận diện; không xóa, reset, commit hay ghi đè thủ công các thay đổi đó.
   - Chỉ PASS nếu Gradle chạy thành công, module tương ứng đã được include, `<Location>/master.json` và `<Location>/properties.json` tồn tại, parse JSON hợp lệ, `CURRENT_STEP.TYPE` là `string`, `CURRENT_STEP.VALUE` hợp lệ, các giá trị đã có được bảo toàn theo canonical sync; kiểm tra lại `gradle.properties` và catalog được cập nhật/không cần đổi. **Không tự tạo `master.json`/`properties.json` bằng JSON thủ công.** Nếu sync/readback không đạt, báo `BLOCKER: MODULE_GRADLE_SYNC`, không clone tab và không coi STEP 1 hoàn tất.
   - **Cổng SERVICE_NAME ngay sau canonical sync:** Đọc `SERVICE_NAME.TYPE` và `VALUE`; TYPE phải là `string`. VALUE không rỗng, đúng UPPER_SNAKE_CASE và không trùng → **giữ nguyên**. Với module mới có VALUE rỗng, Prime chọn tên theo quy tắc trên, snapshot và cập nhật **chỉ `SERVICE_NAME.VALUE` trên `master.json` do sync tạo**, bảo toàn toàn bộ key khác và `CURRENT_STEP`; dùng ghi an toàn/rollback, không tự tạo JSON canonical. Chạy **lại Gradle sync** để cập nhật `ModuleListEnum`, exact readback VALUE/TYPE, bảo đảm các module khác không bị rename. Lỗi, trùng, chưa xác định được tên hoặc còn trống → BLOCKER, không clone sheet.
8. **Nhân bản Template:** Duplicate tab `Template` **trong cùng spreadsheet**, tạo một tab mới mang tên chủ đề. Phải giữ nguyên cấu trúc, định dạng, công thức và các vùng nội dung mẫu của `Template`, bao gồm toàn bộ vùng `Module Project`.
9. **Điền thông tin module:** Trên **tab vừa clone**, ghi `B1` bằng đúng tên chủ đề và **`I3` bằng Location đã xác minh**. Giữ nguyên các nhãn `I1`, `I2`, không chỉnh sửa tab `Template` hoặc tab khác; không ghi đè giá trị/công thức có sẵn trong ô đích.
10. **Định dạng ngay sau khi clone:** Trên **tab mới**, áp dụng đồng thời cho **tất cả các cột hiện có** và **700 hàng đầu tiên (hàng 1–700)**:
   - **Chiều cao mỗi hàng: `60 px`**, tương đương khoảng **3 dòng văn bản** ở font thông thường. Đây là chiều cao *hàng* chứ không phải độ rộng cột.
   - **Text wrapping = `CLIP`**: chữ dài không tràn sang ô kế bên và không khiến hàng tự giãn theo nội dung. Không dùng `WRAP` hoặc `OVERFLOW_CELL`.
   - Google Sheets API: `updateDimensionProperties` với `range: {sheetId: <GID_MỚI>, dimension: "ROWS", startIndex: 0, endIndex: 700}`, `properties: {pixelSize: 60}`, `fields: "pixelSize"`; và `repeatCell` với `range: {sheetId: <GID_MỚI>, startRowIndex: 0, endRowIndex: 700, startColumnIndex: 0, endColumnIndex: <SỐ_CỘT_THỰC_TẾ>}`, `cell: {userEnteredFormat: {wrapStrategy: "CLIP"}}`, `fields: "userEnteredFormat.wrapStrategy"`.
   - Lấy `rowCount`/`columnCount` và `sheetId` của **tab vừa clone** từ metadata. Nếu `rowCount < 700`, bổ sung đủ hàng bằng `updateSheetProperties` trên **tab mới** trước khi format; không ghi request vượt lưới. Không sửa giá trị, công thức, ghi chú, border, font, màu sắc, kích thước cột, validation hoặc định dạng khác.
   - **Chỉ định dạng tab vừa clone** khi chạy STEP 1 thông thường. Không format lại `Template` hoặc các tab cũ như một hiệu ứng phụ. Áp dụng hàng loạt cho các tab đã tồn tại chỉ khi người dùng yêu cầu riêng.
11. **Lấy ID của tab mới:** Từ kết quả duplicate hoặc metadata của spreadsheet, lấy **`sheetId` của tab vừa tạo** (số `gid` trên link mở tab). **Không dùng spreadsheet ID/file ID** trong phần `/spreadsheets/d/...`.
12. **Lưu mapping vào TXT:** Ngay sau khi clone, định dạng và xác định sheet ID, bổ sung một dòng ở **cuối** `module-generate-agent/module-generate-info.txt` theo đúng mẫu:

   ```text
   <TÊN MODULE> sheet id : <SHEET_ID>
   ```

   Ví dụ: `Aspect sheet id : 279051076`. Tên module trong dòng mapping phải khớp tên tab mới. Giữ nguyên dòng `Excel Page URL`, URL gốc và các mapping của module khác. Không tạo dòng trùng lặp hoặc ghi đè mapping xung đột; nếu ghi TXT thất bại, báo rõ tab đã tạo nhưng STEP 1 **chưa hoàn thành**, không tự clone thêm lần nữa.
13. **Đọc lại và xác minh:** Kiểm tra tên tab, giá trị `B1`, `I3` khớp **chính xác** Location đã xác minh, `I1`/`I2` còn nguyên, `sheetId` thực tế và dòng mapping trong TXT đều trùng khớp. **Xác minh `pixelSize = 60` trên hàng 1 và hàng 700, `wrapStrategy = CLIP` trên vùng A1 đến cột cuối ở hàng 700** (có thể dùng API đọc grid/dimension metadata), và không thay đổi các trường ngoài field mask. Xác nhận `Template` vẫn tồn tại và không bị thay đổi. Nếu ghi/đọc lại `I3` hoặc định dạng thất bại, STEP 1 **chưa hoàn thành**; không tự clone thêm tab.
14. **Prime cập nhật tiến độ đúng module sau QA:** Chỉ sau khi bước 7, bước 13 và toàn bộ kiểm tra kế thừa Template/dropdown/mapping đều PASS, Prime dùng **`I3` đã exact-readback** để định vị đúng duy nhất `<I3>/master.json` dưới `java-learning/module/`. Đọc/snapshot `CURRENT_STEP.TYPE` và `CURRENT_STEP.VALUE`; `TYPE` phải là `string`. Canonical sync ở bước 7 phải khởi tạo `CURRENT_STEP` hợp lệ nếu trước đó chưa có; nếu vẫn thiếu hoặc sai schema thì báo `BLOCKER: CURRENT_STEP_UPDATE`, **không tự thêm JSON thủ công**. Đối chiếu thứ tự STEP hiện hành **theo quy tắc Prime, không so sánh chuỗi hoặc coi `"8"` lịch sử là STEP mới**: chỉ nâng `"0"` → `"1"` khi hoàn thành STEP 1 lần đầu; `"1"` thì giữ nguyên; trạng thái đã đi xa hơn (kể cả trạng thái lịch sử chưa xác minh) thì **không ghi lùi**, chỉ bảo toàn và báo trạng thái thực. Nếu sai schema, provenance mâu thuẫn, thiếu module hoặc có concurrent writer → `BLOCKER: CURRENT_STEP_UPDATE`, không đoán. Khi cần ghi, chỉ thay **`CURRENT_STEP.VALUE`** bằng JSON string `"1"`, giữ nguyên các key khác, snapshot/atomic replace hoặc rollback an toàn và **exact readback** từ `master.json`. Không ghi trạng thái vào Google Sheets/Excel `J2/J3` hay module khác; write/readback FAIL thì STEP 1 chưa COMPLETED.
15. **Báo cáo:** Trả về trạng thái `gradle.properties` (`CREATED`/`UNCHANGED`), `GRADLE_SYNC=PASS/BLOCKER`, kết quả readback của `master.json`/`properties.json` và module include; tên tab, chủ đề ở `B1`, **Location tại `I3`**, `sheetId`, dòng TXT vừa thêm, tình trạng format 700 hàng × tất cả cột (60 px, CLIP) và đường dẫn mở trực tiếp tab mới `<URL_GỐC>/edit?gid=<SHEET_ID>#gid=<SHEET_ID>`. Báo thêm `CURRENT_STEP_MODULE_PATH`, `CURRENT_STEP_BEFORE`, `CURRENT_STEP_AFTER`, `CURRENT_STEP_READBACK=PASS/FAIL`, `UNCHANGED` nếu không cần ghi, hoặc blocker nếu có. Chỉ tuyên bố STEP 1 hoàn tất sau cả cổng Gradle sync và cổng trạng thái này.

**Bắt buộc bổ sung báo cáo bước 15:** `SERVICE_NAME_VALUE`, `SERVICE_NAME_FORMAT=PASS/BLOCKER`, `SERVICE_NAME_UNIQUE=PASS/BLOCKER`, `SERVICE_NAME_READBACK=PASS/FAIL`. Nếu thiếu bằng chứng tên hợp lệ và duy nhất, STEP 1 chưa hoàn thành.

## Cấu trúc Template và Summary

**Bố cục authoring hoạt động đến AC (29 cột):** `AC18=Summary`, `AC19=Summary YAML` (cặp Learning Objectives YAML cũ chỉ xử lý qua migration có kiểm tra). Không tạo Practice AD:AG; những cột AD:AG hiện hữu được giữ nguyên để tránh mất dữ liệu, không có producer/importer Practice.

## Dropdown Difficulty bắt buộc kế thừa từ Template

**Template chuẩn (gid=0):** tiêu đề Difficulty tại **O19**; toàn vùng **O20:O700** có Data Validation với condition **ONE_OF_LIST**, chính xác ba giá trị theo thứ tự **BASIC, INTERMEDIATE, ADVANCED**, **strict=true** (từ chối giá trị ngoài danh sách) và **showCustomUi=true** (hiện dropdown).

**Màu Difficulty đi kèm dropdown:** Template còn có **ba Conditional Formatting Rules** trên đúng vùng **O20:O700**, dùng điều kiện `TEXT_EQ` so khớp chính xác nội dung ô. `BASIC` → nền **xanh lá nhạt**, chữ xanh đậm; `INTERMEDIATE` → nền **vàng nhạt**, chữ vàng đậm; `ADVANCED` → nền **đỏ nhạt**, chữ đỏ đậm. Màu tự thay đổi khi chọn giá trị từ dropdown; ô trống không bị tô. Đây là **định dạng có điều kiện của ô**, không phải thiết lập màu riêng cho từng lựa chọn trong menu dropdown. Duplicate Sheet kế thừa cả Data Validation và Conditional Formatting Rules.

Quy tắc tích hợp vào quy trình STEP 1 ở trên:

1. **Tại bước 2 — preflight Template:** xác nhận tiêu đề O19 và đọc Data Validation ở ít nhất O20, O21 và O700. Kiểm tra ba Conditional Formatting Rules `TEXT_EQ` với vùng O20:O700, giá trị và màu tương ứng. Thiếu/sai cấu hình thì dừng **BLOCKER: DIFFICULTY VALIDATION**; không tự sửa tab Template trong STEP 1.
2. **Tại bước 8 — clone:** Duplicate Sheet phải sao chép **nguyên Data Validation và ba quy tắc màu** của Template sang tab mới. Không gọi AI, không tự gõ danh sách và không tự thiết lập từng ô trên tab mới. **Difficulty của Menu Cấp 2** được lựa chọn sau này tại cột O trên hàng bài học; **Menu Cấp 1 để trống O** dù cột có dropdown.
3. **Tại bước 10 — định dạng:** Chỉ cập nhật chiều cao hàng và wrapStrategy bằng đúng field mask đã nêu; **không xóa/đổi Data Validation hay Conditional Formatting Rules** hoặc dùng field mask toàn phần. Dropdown và màu trên toàn vùng O20:O700 phải còn nguyên sau thao tác CLIP.
4. **Tại bước 13 — readback:** Đọc lại Data Validation **O20, O21, O700 trên chính tab vừa tạo**, xác nhận condition ONE_OF_LIST đúng ba mục theo thứ tự, strict=true và showCustomUi=true; kiểm tra cả ba Conditional Formatting Rules và vùng áp dụng O20:O700. O19 còn nguyên chữ Difficulty; vùng ngoài như O701 không bị sửa. Nếu không PASS thì STEP 1 chưa hoàn thành, không tự clone lần nữa.
5. **Tại bước 15 — báo cáo:** Có trường **DIFFICULTY_DROPDOWN=PASS/BLOCKER**, **DIFFICULTY_COLORS=PASS/BLOCKER**, vùng O20:O700, ba lựa chọn/trạng thái strict và ba màu tương ứng. Những lần chạy STEP 1 thông thường **không thay đổi Template hoặc các tab cũ**.

## Nguyên tắc an toàn

**Bố cục Template:** A:AC gồm Interview P:S, Quiz T:W, Video X:AA và Summary AC. Các cột trung gian không được STEP 1 ghi dữ liệu. AD:AG nếu có được giữ nguyên, không có Practice importer. STEP 1 clone nguyên header/format mà không sửa dữ liệu. Interview P:S dùng YAML, Quiz T:W và Video X:AA theo contract riêng.

**Quiz và Video:** `T18 = Quiz`, `T19:W19 = Quiz YAML`; `X18 = Video`, `X19:AA19 = Video Script`. **Không điền Quiz vào T20:W700 hoặc Video vào X20:AA700 khi clone**; STEP 10 worker sinh Quiz YAML vào hàng cha khi được giao; Video do STEP_OPTIONAL_VIDEO worker tạo sau STEP_FINAL. Ba spreadsheet `Module Content`, `Module Content 2`, `Module Content 3` đều đã được sắp xếp theo thứ tự này, không thay các vùng A:O.

- **Duplicate tab, không duplicate toàn bộ spreadsheet** và không tạo file Google Sheets mới.
- Chỉ tạo `gradle.properties` nếu thiếu cho **đúng một Location đã xác minh**, nội dung `IS_MODULE=true`; không sửa file hiện hữu hoặc khởi tạo module hàng loạt. Gradle sync **bắt buộc** và phải PASS trước khi clone. Không tạo thủ công JSON/enum do `gradle-runtime` quản lý; **ngoại lệ** chỉ cập nhật `SERVICE_NAME.VALUE` trên `master.json` đã được sync tạo, rồi sync lại và readback theo bước 7.
- Không đổi tên, di chuyển, xóa hoặc chỉnh sửa tab nguồn `Template`.
- Luôn giữ nguyên `Module Project` (`I1`) và `Location` (`I2`); chỉ điền đường dẫn vào `I3` của tab mới, tính từ **gốc `java-learning/`** (có `module/` trong giá trị).
- Không tự ý xóa, làm sạch hoặc ghi đè tab có sẵn khi trùng tên.
- Định dạng tab mới chỉ giới hạn **700 hàng đầu × toàn bộ cột hiện có**, bằng field mask chính xác cho chiều cao hàng và `wrapStrategy`. Những vùng ngoài 700 hàng và tất cả giá trị ô phải giữ nguyên. `CLIP` chỉ thay đổi hiển thị, **không cắt hoặc xóa chuỗi văn bản gốc**.
- Sheet ID là ID của **tab**, không phải ID của cả spreadsheet; việc lưu mapping vào TXT là một phần bắt buộc của STEP 1.
- Không điền tiếp STEP 2/3 hay generate nội dung nếu người dùng chưa yêu cầu.
- Nếu thiếu quyền truy cập, tab `Template` không tồn tại hoặc thao tác tạo/ghi thất bại, dừng lại và báo lỗi thực tế; không tuyên bố đã hoàn tất.

## Ví dụ kết quả mong đợi

**Prompt:** `Tạo module với chủ đề OOP`

**Kết quả:** Nếu thư mục `module/platform/development/programming/paradigm/object-oriented` chưa có `gradle.properties`, STEP 1 tạo file với `IS_MODULE=true`, chạy Gradle sync và kiểm tra `master.json`/`properties.json`; nếu file đã có thì giữ nguyên và vẫn sync. Trong spreadsheet từ `module-generate-info.txt`, có tab mới tên `OOP`, nhân bản từ `Template`; ô `OOP!B1` chứa `OOP`; ô `OOP!I3` chứa `module/platform/development/programming/paradigm/object-oriented`; file TXT được bổ sung dòng `OOP sheet id : <SHEET_ID_THỰC_TẾ>`; tab `Template` và các tab khác giữ nguyên.

**Điều kiện hoàn thành:** Module có `gradle.properties` (tạo `IS_MODULE=true` nếu ban đầu thiếu), Gradle sync PASS và có `master.json`/`properties.json` hợp lệ; tab mới tồn tại đúng tên, kế thừa `Template`, `B1` khớp chủ đề, `I3` chứa Location hợp lệ tính từ `java-learning/`, **700 hàng đầu × toàn bộ cột có hàng cao 60 px và `CLIP`**, và TXT có mapping tên module → **sheet ID thực tế**; tất cả đã được đọc lại, xác minh. **Prime phải nghiệm thu và đọc lại `CURRENT_STEP.VALUE` của đúng `master.json`: ghi `"1"` khi được phép, hoặc bảo toàn trạng thái hợp lệ đã đi xa hơn; không ghi lùi hay đánh dấu COMPLETED khi cập nhật bắt buộc thất bại.**
