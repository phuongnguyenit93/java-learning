# STEP 1 — Tạo sheet cho chủ đề mới

## Mục tiêu

Khi người dùng đưa ra **một chủ đề cần generate nội dung**, tạo **một tab (sheet) mới** trong Google Sheets bằng cách **nhân bản tab `Template`**, đặt tên tab theo chủ đề, ghi tên chủ đề vào ô `B1`, điền đường dẫn module tương ứng trong repository `java-learning` vào ô `I3` của tab mới và lưu **sheet ID (gid)** của tab đó vào file TXT để các STEP sau định vị chính xác.

STEP này chỉ chuẩn bị sheet. **Không generate Roadmap, Menu hoặc nội dung bài học** trong STEP 1.

## Nguồn Google Sheets

Đọc file `module-generate-agent/temp/module-generate-info.txt` trong repository hiện tại và lấy URL ở dòng dưới nhãn `Excel Page URL`.

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

**Cách xác định:** Dò cấu trúc trong `project-build/gradle-runtime/src/main/resources/structure/module-structure.txt` và **đối chiếu thư mục thực tế** dưới `java-learning/module/`, cùng định danh/metadata module nếu cần. Tên tab có thể khác tên folder (`OOP` ↔ `object-oriented`); không suy ra đường dẫn bằng cách tự động slugify tên chủ đề. Nếu người dùng cung cấp Location, vẫn phải chuẩn hóa theo mốc `java-learning/` và kiểm tra đường dẫn tương ứng. Phải tìm được **đúng một thư mục module hợp lệ**; nếu chưa tồn tại, không xác định được hoặc có nhiều kết quả, **báo BLOCKER và làm rõ trước khi clone** thay vì điền path phỏng đoán hay để `I3` rỗng rồi báo hoàn thành.

## Quy trình thực hiện

1. **Đọc cấu hình:** Mở `module-generate-info.txt`, xác định URL và spreadsheet ID hợp lệ.
2. **Kiểm tra nguồn:** Mở spreadsheet đó, xác nhận tồn tại tab nguồn có tên chính xác là `Template`; xác nhận `Template!I1` là `Module Project`, `Template!I2` là `Location` và `Template!I3` chưa chứa giá trị/công thức cần bảo toàn. Nếu bố cục hoặc ô đích không đúng, dừng để tránh ghi nhầm.
3. **Xác định Location:** Từ chủ đề, tìm đúng module trong `module-structure.txt`, đối chiếu thư mục thực tế và lấy đường dẫn **từ `java-learning/`**, bao gồm tiền tố `module/`. Xác nhận Location không rỗng, duy nhất, chính xác trước khi clone; nếu không xác định được, báo BLOCKER theo mục trên.
4. **Xác định tên tab mới:** Dùng tên chủ đề mà người dùng cung cấp, giữ nguyên cách viết hợp lý. Nếu tên không hợp lệ theo quy định Google Sheets (ví dụ ký tự cấm, tên quá dài), chỉ chuẩn hóa phần bắt buộc và thông báo tên cuối cùng.
5. **Kiểm tra trùng tên:** Nếu tab cùng tên đã tồn tại, **không nhân bản lần nữa và không ghi đè dữ liệu**. Báo cho người dùng biết để họ quyết định dùng tab cũ hay đặt tên mới.
6. **Nhân bản Template:** Duplicate tab `Template` **trong cùng spreadsheet**, tạo một tab mới mang tên chủ đề. Phải giữ nguyên cấu trúc, định dạng, công thức và các vùng nội dung mẫu của `Template`, bao gồm toàn bộ vùng `Module Project`.
7. **Điền thông tin module:** Trên **tab vừa clone**, ghi `B1` bằng đúng tên chủ đề và **`I3` bằng Location đã xác minh**. Giữ nguyên các nhãn `I1`, `I2`, không chỉnh sửa tab `Template` hoặc tab khác; không ghi đè giá trị/công thức có sẵn trong ô đích.
8. **Lấy ID của tab mới:** Từ kết quả duplicate hoặc metadata của spreadsheet, lấy **`sheetId` của tab vừa tạo** (số `gid` trên link mở tab). **Không dùng spreadsheet ID/file ID** trong phần `/spreadsheets/d/...`.
9. **Lưu mapping vào TXT:** Ngay sau khi clone và xác định sheet ID, bổ sung một dòng ở **cuối** `module-generate-agent/temp/module-generate-info.txt` theo đúng mẫu:

   ```text
   <TÊN MODULE> sheet id : <SHEET_ID>
   ```

   Ví dụ: `Aspect sheet id : 279051076`. Tên module trong dòng mapping phải khớp tên tab mới. Giữ nguyên dòng `Excel Page URL`, URL gốc và các mapping của module khác. Không tạo dòng trùng lặp hoặc ghi đè mapping xung đột; nếu ghi TXT thất bại, báo rõ tab đã tạo nhưng STEP 1 **chưa hoàn thành**, không tự clone thêm lần nữa.
10. **Đọc lại và xác minh:** Kiểm tra tên tab, giá trị `B1`, `I3` khớp **chính xác** Location đã xác minh, `I1`/`I2` còn nguyên, `sheetId` thực tế và dòng mapping trong TXT đều trùng khớp; xác nhận `Template` vẫn tồn tại và không bị thay đổi. Nếu ghi hoặc đọc lại `I3` thất bại, STEP 1 **chưa hoàn thành**; không tự clone thêm tab.
11. **Báo cáo:** Trả về tên tab, chủ đề ở `B1`, **Location tại `I3`**, `sheetId`, dòng TXT vừa thêm và đường dẫn mở trực tiếp tab mới `<URL_GỐC>/edit?gid=<SHEET_ID>#gid=<SHEET_ID>`.

## Nguyên tắc an toàn

- **Duplicate tab, không duplicate toàn bộ spreadsheet** và không tạo file Google Sheets mới.
- Không đổi tên, di chuyển, xóa hoặc chỉnh sửa tab nguồn `Template`.
- Luôn giữ nguyên `Module Project` (`I1`) và `Location` (`I2`); chỉ điền đường dẫn vào `I3` của tab mới, tính từ **gốc `java-learning/`** (có `module/` trong giá trị).
- Không tự ý xóa, làm sạch hoặc ghi đè tab có sẵn khi trùng tên.
- Sheet ID là ID của **tab**, không phải ID của cả spreadsheet; việc lưu mapping vào TXT là một phần bắt buộc của STEP 1.
- Không điền tiếp STEP 2/3 hay generate nội dung nếu người dùng chưa yêu cầu.
- Nếu thiếu quyền truy cập, tab `Template` không tồn tại hoặc thao tác tạo/ghi thất bại, dừng lại và báo lỗi thực tế; không tuyên bố đã hoàn tất.

## Ví dụ kết quả mong đợi

**Prompt:** `Tạo module với chủ đề OOP`

**Kết quả:** Trong spreadsheet từ `module-generate-info.txt`, có tab mới tên `OOP`, nhân bản từ `Template`; ô `OOP!B1` chứa `OOP`; ô `OOP!I3` chứa `module/platform/development/programming/paradigm/object-oriented`; file TXT được bổ sung dòng `OOP sheet id : <SHEET_ID_THỰC_TẾ>`; tab `Template` và các tab khác giữ nguyên.

**Điều kiện hoàn thành:** Tab mới tồn tại đúng tên, kế thừa `Template`, `B1` khớp chủ đề, `I3` chứa Location hợp lệ tính từ `java-learning/`, và TXT có mapping tên module → **sheet ID thực tế**; tất cả đã được đọc lại, xác minh.
