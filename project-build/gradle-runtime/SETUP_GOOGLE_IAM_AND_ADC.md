# Google Sheets ADC Setup — gradle-runtime

Tài liệu thiết lập **Application Default Credentials (ADC)** cho Gradle task
`generateModuleContentFromSheetByLocation` và
`generateModuleContentFromSheetByFiles` trên Windows. Cách thiết lập này dùng
**Service Account Impersonation**, không cần tải xuống JSON private key.

> **Hai entrypoint chung một coordinator:** task module-local
> `generateModuleContentFromSheetByLocation` nhận `fileUrl` + `sheetId`
> qua **extension của module**, xác minh `I3` trùng module thực tế;
> task root `generateModuleContentFromSheetByFiles` chỉ nhận
> `-PsheetFileUrl` (**không có extension**), quét mọi tab trừ tab tên đúng
> `Template`. Cả hai import Roadmap `F:H` → `content/roadmap/vi`
> **và** Menu/Knowledge `A:B` + `C:N` (12 ô) → `content/readme/docs/vi/`, với
> `DRY_RUN` mặc định và `-PsheetMode=APPLY` tường minh. ByFiles
> không yêu cầu module đích có `master.json`/`gradle.properties`.
> Menu/Knowledge được ghép bởi `ReadmeSheetImportService` và tái sử dụng
> `GenerateInternalReadmeMenuService`; đã có VideoSheetImportService.

## 1. Cách hoạt động

```text
Module Gradle (USE_TASK=TRUE)
    └── project-orchestration → TaskSetupPlugin → SheetsTaskPlugin
            └── generateModuleContentFromSheetByLocation
                    ├── extension: sheetId, fileUrl
                    └── SheetContentImportCoordinator
                            └── SheetsMetadataReaderService
                            ├── GoogleCredentials.getApplicationDefault()
                            ├── ImpersonatedCredentials (Service Account)
                            ├── scope: spreadsheets.readonly
                            ├── Sheets API: spreadsheets.get (metadata only)
                            └── Sheets API: values.get (đọc I3 và xác minh module)

Root Gradle
    └── generateModuleContentFromSheetByFiles
            ├── -PsheetFileUrl=<URL> (không extension, không gid)
            ├── lấy tab metadata một lần, bỏ qua Template
            └── SheetContentImportCoordinator
                    ├── RoadmapSheetImportService
                    └── ReadmeSheetImportService
```

Hai thông số `sheetId`, `fileUrl` **không phải thông tin xác thực**:

- `fileUrl`: URL của **file spreadsheet**, ví dụ
  `https://docs.google.com/spreadsheets/d/SPREADSHEET_ID/edit`.
- `sheetId`: **gid của tab** trong file đó (không phải spreadsheet ID).

Trong môi trường local đã kiểm thử:

| Thông số | Giá trị ví dụ |
| --- | --- |
| Google Cloud Project ID | `java-learning-sheet-import` |
| Service Account | `java-learning-sheets-reader@java-learning-sheet-import.iam.gserviceaccount.com` |
| Hệ điều hành | Windows, CMD / PowerShell |
| Gradle / JDK đã kiểm thử | Gradle 8.5 / JDK 21 |

## 2. Thiết lập Google Cloud (một lần)

### 2.1. Project và APIs

1. Tạo hoặc chọn project trong [Google Cloud Console](https://console.cloud.google.com/).
2. Bật **Google Sheets API** (`sheets.googleapis.com`).
3. Bật **IAM Service Account Credentials API** (`iamcredentials.googleapis.com`)
   để lấy token thay mặt Service Account.

Có thể bật bằng Google Cloud CLI, sau khi đăng nhập bằng tài khoản có quyền quản trị dịch vụ:

```cmd
gcloud auth login
gcloud config set project java-learning-sheet-import
gcloud services enable sheets.googleapis.com iamcredentials.googleapis.com --project=java-learning-sheet-import
```

Không cần bật Google Drive API chỉ để gọi `spreadsheets.get` lấy metadata.

### 2.2. Tạo Service Account

Trong **IAM & Admin → Service Accounts**, tạo Service Account có tên ví dụ
`java-learning-sheets-reader`. Ghi lại email đầy đủ của Service Account.

**Không** tạo/download Service Account JSON key và **không** cấp role
`Owner`/`Editor` trên project cho Service Account chỉ để đọc Sheets.

### 2.3. Cấp quyền impersonation đúng Principal

Tại Service Account vừa tạo, chọn **Principals with access** → **Grant access**
(hoặc vào phần quản lý IAM policy của chính Service Account):

| Mục | Cần chọn |
| --- | --- |
| Resource | Chính Service Account `java-learning-sheets-reader` |
| Principal nhận quyền | **Email tài khoản Google cá nhân** dùng chạy `gcloud auth application-default login` |
| Role | **Service Account Token Creator** (`roles/iam.serviceAccountTokenCreator`) |

**Lưu ý:** Không cấp role này cho chính email Service Account, và không cấp
trên **toàn project** nếu chỉ cần impersonate Service Account này. Quyền cần có
là `iam.serviceAccounts.getAccessToken`.

### 2.4. Chia sẻ Google Sheet cho Service Account

Mở file Google Sheets → **Share** → nhập **email Service Account** → chọn
**Viewer**. Đây là quyền đọc file spreadsheet, độc lập với IAM Token Creator.
Việc share áp dụng cho file spreadsheet, không chỉ riêng một tab `gid`.

## 3. Thiết lập ADC trên Windows

1. Cài [Google Cloud CLI](https://cloud.google.com/sdk/docs/install).
2. Mở **CMD** hoặc **PowerShell** mới và đăng nhập tài khoản Google có quyền
   impersonate Service Account.
3. Tạo ADC local (lệnh một dòng dễ tránh lỗi nhất):

```cmd
gcloud auth application-default login --impersonate-service-account=java-learning-sheets-reader@java-learning-sheet-import.iam.gserviceaccount.com
```

Google sẽ mở trình duyệt để xác thực. File ADC thường được lưu ở Windows tại:

```text
%APPDATA%\gcloud\application_default_credentials.json
```

> Lưu ý thực tế trên máy đã kiểm thử: mặc dù dùng tùy chọn
> `--impersonate-service-account`, ADC được lưu dưới dạng `authorized_user`
> (`UserCredentials`) chứ không phải `ImpersonatedCredentials`. Khi đó,
> `createScoped()` trên refresh token cá nhân **không tự cấp thêm** scope
> `spreadsheets.readonly`. Mục 4 bên dưới là cách task xử lý trường hợp này.

### Kiểm tra ADC không lộ access token

Trong **CMD**:

```cmd
gcloud auth application-default print-access-token >NUL && echo ADC TOKEN OK
```

Trong **PowerShell**:

```powershell
gcloud auth application-default print-access-token | Out-Null
if ($LASTEXITCODE -eq 0) { Write-Host 'ADC TOKEN OK' }
```

Đây chỉ là bước xác nhận **có thể lấy token**; chưa chứng minh token có scope
Sheets hoặc Service Account đã đọc được spreadsheet.

**Phân biệt shell:** CMD dùng `^` để tiếp dòng; PowerShell dùng dấu backtick
(`` ` ``) ở **cuối dòng**. Không nhập backtick thừa trong CMD: nếu gặp
`You attempted to log in as account` với tên tài khoản là dấu backtick,
CLI đã hiểu nhầm ký tự xuống dòng thành tên tài khoản.

## 4. Chỉ định Service Account cho Gradle Task

Trên môi trường Windows đã kiểm thử, cần đặt biến môi trường sau để reader
tạo **short-lived impersonated access token** với scope
`https://www.googleapis.com/auth/spreadsheets.readonly`:

**CMD — chỉ áp dụng trong cửa sổ hiện tại:**

```cmd
set "GOOGLE_SHEETS_IMPERSONATE_SERVICE_ACCOUNT=java-learning-sheets-reader@java-learning-sheet-import.iam.gserviceaccount.com"
```

**PowerShell — chỉ áp dụng trong phiên hiện tại:**

```powershell
$env:GOOGLE_SHEETS_IMPERSONATE_SERVICE_ACCOUNT = 'java-learning-sheets-reader@java-learning-sheet-import.iam.gserviceaccount.com'
```

Nếu chạy từ IntelliJ, đặt biến này trong **môi trường của tiến trình Gradle**
(và khởi động lại Gradle Daemon/IDE nếu cần). Không đặt email này vào
`sheetId` hay `fileUrl`; hai thuộc tính của extension giữ nguyên.

Reader nằm tại:

```text
src/main/groovy/com/example/learning/task/sheets/service/SheetsMetadataReaderService.groovy
```

Nó tải ADC khi **task thực thi**, sau đó dùng `ImpersonatedCredentials`
để yêu cầu scope Sheets read-only cho Service Account. Nếu ADC đã là
`ImpersonatedCredentials` của đúng Service Account, reader sẽ dùng lại
credential phù hợp. Không cần hardcode access token hoặc đường dẫn JSON key.

## 5. Cấu hình và kiểm tra Gradle Task

### Root Interview YAML Normalize — tác vụ ghi Google Sheets, khác với importer đọc

`normalizeInterviewQuestionsFromSheet` là task root nhận **`fileUrl`** (URL spreadsheet) và **`sheetID`** (gid tab), chạy bằng Groovy/Google Sheets API. Nó đọc YAML `questions` ở `P:S` trên các hàng Menu Cấp 1 (chấp nhận legacy JSON), thêm mặc định `aiGenerated:true`, `reviewed:false`, `readmeRelated.file` theo tên file chương; `anchor`/API được để trống nếu chưa có. Metadata đã có giữ nguyên; kết quả **YAML** chuẩn hóa ghi lại **chính P:S**, không sửa module.

Phân quyền có chủ đích:

- Các task import và `normalizeInterviewQuestionsFromSheet` **DRY_RUN** vẫn yêu cầu scope `spreadsheets.readonly`; Viewer là đủ.
- `normalizeInterviewQuestionsFromSheet` **APPLY** yêu cầu scope `https://www.googleapis.com/auth/spreadsheets` và **Service Account phải được share file Sheet với quyền Editor**. IAM Token Creator vẫn cần cho người dùng chạy impersonation. Không cần Service Account key hoặc Google Drive API.

Từ root `java-learning/`:

```powershell
.\gradlew.bat normalizeInterviewQuestionsFromSheet -PfileUrl="https://docs.google.com/spreadsheets/d/<SPREADSHEET_ID>/edit" -PsheetID=<GID> -PinterviewMode=DRY_RUN
.\gradlew.bat normalizeInterviewQuestionsFromSheet -PfileUrl="https://docs.google.com/spreadsheets/d/<SPREADSHEET_ID>/edit" -PsheetID=<GID> -PinterviewMode=APPLY
```

Task có mặc định `DRY_RUN`, nhận alias `sheetFileUrl`/`sheetId`. APPLY chỉ ghi nếu mọi JSON hợp lệ, preflight toàn bộ tab PASS và nội dung nguồn không thay đổi; một batch update, sau đó đọc lại. **Không chạy APPLY trên tab chưa hoàn tất STEP 6** nếu muốn bảo đảm tất cả chương có Interview.

Task được `project-orchestration` đăng ký cho module có `USE_TASK=TRUE`.
Extension DSL được cấu hình ở **`build.gradle` của module** (nếu module
có script cấu hình), ví dụ:

```groovy
generateModuleContentFromSheetByLocation {
    sheetId = 'YOUR_TAB_GID'
    fileUrl = 'https://docs.google.com/spreadsheets/d/YOUR_SPREADSHEET_ID/edit'
}
```

**Không bắt buộc phải tạo/sửa `build.gradle` của từng module.** Các task
`ByLocation` nhận thêm `-PfileUrl=<URL>` và `-PsheetID=<numeric gid>`
(alias `sheetFileUrl`/`sheetId`), ưu tiên cao hơn extension. Prime chỉ dùng
URL/gid đã xác minh từ mapping `module-generate-info.txt` và I3 của tab;
`task.gradle` do generator tạo chỉ là tài liệu tham khảo, không phải config.

### Ba task import độc lập cho một module

Tất cả dùng **chung extension `generateModuleContentFromSheetByLocation`** với `fileUrl` và `sheetId`, xác minh `I3` khớp module, mặc định `DRY_RUN`. Có thể chọn từng task:

```powershell
.\gradlew.bat :<MODULE_PATH>:generateModuleRoadmapFromSheetByLocation
.\gradlew.bat :<MODULE_PATH>:generateModuleReadmeFromSheetByLocation
.\gradlew.bat :<MODULE_PATH>:generateModuleVideoFromSheetByLocation
```

Ví dụ gọi đúng một module mà không sửa build script:

```powershell
.\gradlew.bat :<EXACT_MODULE_PATH>:generateModuleRoadmapFromSheetByLocation -PfileUrl=<VERIFIED_URL> -PsheetID=<VERIFIED_GID> -PsheetMode=DRY_RUN
.\gradlew.bat :<EXACT_MODULE_PATH>:generateModuleRoadmapFromSheetByLocation -PfileUrl=<VERIFIED_URL> -PsheetID=<VERIFIED_GID> -PsheetMode=APPLY
```

Chỉ APPLY khi nguồn đầy đủ và DRY_RUN hợp lệ. `SKIPPED_*` và DRY_RUN
không phải bằng chứng đã cập nhật module.

Thêm `-PsheetMode=APPLY` để thực sự cập nhật **chỉ cây đầu ra tương ứng** (Roadmap, README hoặc Video), không chạy ngầm các importer khác. **Task tổng hợp** `generateModuleContentFromSheetByLocation` nay chạy Roadmap → README → Video → Interview. **Task root toàn file** `generateModuleContentFromSheetByFiles` vẫn giữ tên/tham số/cách quét mọi tab trừ Template, và nay import cả bốn.

### Task Interview thứ tư — từ JSON trong Sheet ra question.yml

Đã thêm `generateModuleInterviewFromSheetByLocation`, sử dụng chung extension `fileUrl/sheetId` và cùng guard `I3`. Task **chỉ** nhập Interview:

```powershell
.\gradlew.bat :<MODULE_PATH>:generateModuleInterviewFromSheetByLocation
.\gradlew.bat :<MODULE_PATH>:generateModuleInterviewFromSheetByLocation -PsheetMode=APPLY
```

Importer đọc `P18:S19` để kiểm tra header và đọc các mảnh **YAML `P:S` tại hàng Menu Cấp 1** (cũng nhận JSON cũ). Groovy nối các mảnh theo đúng Unicode, parse danh sách `questions`, điền/giữ đầy đủ `question`, `answer`, `aiGenerated`, `reviewed`, `readmeRelated.file/anchor` và `apiRelated` theo schema rồi serialize YAML **một file duy nhất**:

`<module>/content/interview/vi/question.yml`

**Không dùng AI và không ghi ngược Google Sheets.** Với tab thiếu P:S/header Interview hoặc chưa có dữ liệu, task báo `SKIPPED_NO_INTERVIEW_SCHEMA` hoặc `SKIPPED_EMPTY_INTERVIEW`, **giữ nguyên file Interview cũ**. Khi có Interview, mọi YAML/JSON nguồn phải hợp lệ trước khi stage/replace; lỗi cấu trúc hoặc đường dẫn README xung đột là blocker. `DRY_RUN` không ghi file; `APPLY` thay thế snapshot `interview/vi` sau schema validation.

Task tổng hợp `generateModuleContentFromSheetByLocation` giữ thứ tự legacy Roadmap → README → Video → Interview; root `generateModuleContentFromSheetByFiles` vẫn có cơ chế quét tab như trước. **Thứ tự cột Sheet mới** là Interview `P:S`, Quiz `T:W`, Video `X:AA`; mỗi STEP Prime chạy riêng task import, không gọi combined/ByFiles. Quiz có module-local `generateModuleQuizFromSheetByLocation` và root `normalizeQuizQuestionsFromSheet` độc lập.

**Không cấu hình trực tiếp vào `task.gradle`:** đây là file DSL tham chiếu
do generator tự sinh và có thể bị ghi lại khi Gradle cấu hình.

### Kiểm tra không cần sửa module đang có

Nếu module chưa có `build.gradle` riêng (ví dụ OOP), tạo một file tạm
`%TEMP%\verify-sheets.init.gradle` với nội dung dưới đây; **không lưu file
thử nghiệm này vào repository**:

```groovy
gradle.beforeProject { project ->
    if (project.path == ':module:platform:development:programming:paradigm:object-oriented') {
        project.pluginManager.withPlugin('com.example.learning.task.sheets.plugin') {
            def sheets = project.extensions.getByName('generateModuleContentFromSheetByLocation')
            sheets.sheetId.set('YOUR_TAB_GID')
            sheets.fileUrl.set('https://docs.google.com/spreadsheets/d/YOUR_SPREADSHEET_ID/edit')
        }
    }
}
```

Từ **thư mục gốc `java-learning/`**, chạy bằng CMD sau khi đặt biến môi trường
ở mục 4:

```cmd
gradlew.bat --init-script "%TEMP%\verify-sheets.init.gradle" :module:platform:development:programming:paradigm:object-oriented:generateModuleContentFromSheetByLocation
```

Kết quả mong đợi:

```text
[SHEETS-ROADMAP] DRY_RUN: tab="..." (gid=...), location=module/..., milestones=..., file=...
BUILD SUCCESSFUL
```

Task lấy đường dẫn từ **thư mục thực tế của module đang chạy Gradle**, tính tương đối
với root `java-learning/`, ví dụ
`module/platform/development/programming/paradigm/object-oriented`. Sau khi xác
nhận đúng tab bằng `gid`, task chỉ đọc **I3** và yêu cầu giá trị tại đó trùng
đúng đường dẫn module (phân biệt chữ hoa/thường, không chấp nhận chỉ trùng tên
module). Nếu I3 trống/khác, task throw error và dừng trước mọi bước sinh nội dung.

Kết quả setup ADC trước khi bổ sung kiểm tra I3: **spreadsheet `Module Content`,
tab `OOP` (gid `1323584551`) đọc metadata thành công** bằng ADC impersonation.

## 6. Các lỗi đã gặp và cách xử lý

| Lỗi / triệu chứng | Nguyên nhân | Cách khắc phục |
| --- | --- | --- |
| `You attempted to log in as account` với account là dấu backtick | Gõ backtick trong CMD | Chạy lệnh một dòng hoặc dùng `^` khi tiếp dòng trong CMD. |
| `iam.serviceAccounts.getAccessToken` / `IAM_PERMISSION_DENIED` | Tài khoản Google cá nhân chưa được cấp quyền impersonation | Grant **Service Account Token Creator** cho **user** trên **Service Account đích**, không cho chính Service Account. |
| `Error requesting access token` | Có thể là lỗi impersonation/IAM | Chạy lại lệnh kiểm tra ADC token, đối chiếu lỗi `PERMISSION_DENIED` và IAM. |
| `Request had insufficient authentication scopes` | ADC dạng `authorized_user` không tự bổ sung được scope Sheets bằng `createScoped()` | Đặt `GOOGLE_SHEETS_IMPERSONATE_SERVICE_ACCOUNT`; để reader lấy impersonated token với `spreadsheets.readonly`. |
| `Google Sheets API has not been used ... or is disabled` | Sheets API chưa bật trên project thực tế | Bật `sheets.googleapis.com` ở project được báo lỗi; chờ vài phút nếu vừa enable. |
| `403` hoặc `404` khi đọc spreadsheet | Có thể sai spreadsheet ID, thiếu Viewer hoặc API/configuration | Kiểm tra `fileUrl`, share file với email Service Account; đọc chi tiết response trước khi kết luận. |
| `tab gid ... was not found` | Truy cập spreadsheet được nhưng `sheetId` không trùng tab | Lấy `gid` trong URL tab đúng. |
| `Module Location mismatch` / `Module Location is empty` | I3 của tab không khớp module đang chạy task hoặc chưa được điền | Kiểm tra tab được chọn bằng `sheetId` và I3 trong Sheet; không tự sửa/ghi đè giá trị. |
| `Unsupported class file major version 68` khi dùng Gradle 8.5 | Java 24 không tương thích với Groovy của Gradle 8.5 trong repo | Chạy build với **JDK 21** trong shell/IDE, không cần đổi Java toàn máy. |

**Lưu ý về project:** Project ID dùng để enable API và project mặc định
trong `gcloud` có thể khác nhau. Trong lần kiểm thử, project đúng là
`java-learning-sheet-import` (project number `638226940279`). Nên dùng
`--project=java-learning-sheet-import` khi bật API để tránh chọn nhầm.

## 7. Bảo mật và bước tiếp theo

- **Không commit** `application_default_credentials.json`, access token,
  refresh token, JSON private key hoặc các file secret khác.
- Không log token, không gửi nội dung credential vào issue/chat/build log.
- Chỉ share spreadsheet ở mức **Viewer**, không yêu cầu Editor.
- `Service Account Token Creator` dành cho tài khoản chạy local, chỉ trên
  Service Account cần impersonate.
- Trên GitHub Actions sau này có thể dùng **Workload Identity Federation**
  thay vì lưu Service Account key. Đó là bước cấu hình CI/CD riêng,
  **chưa được triển khai** trong task này.
- Menu/Knowledge dùng `ReadmeSheetImportService` với internal-menu renderer;
  Video importer hiện sử dụng VideoSheetImportService.

## 8. Import từ root project (ByFiles) hoặc module (ByLocation)

Chạy ở thư mục gốc `java-learning/`, với JDK 21 và cùng ADC/Service Account
đã cấu hình bên trên. Task `generateModuleContentFromSheetByFiles` không yêu
cầu thư mục đích phải có `master.json`, `gradle.properties` hoặc Gradle
module. **Không khai báo extension ở root**: URL truyền thẳng bằng Gradle property.

```powershell
# ByFiles: mặc định DRY_RUN, tự duyệt toàn bộ tab, bỏ Template.
.\gradlew.bat generateModuleContentFromSheetByFiles "-PsheetFileUrl=https://docs.google.com/spreadsheets/d/1ghIDu-knT1o5uE4LHpSf5_l3aZMJxQpyB2c7UT-BTnU/edit"

# ByFiles: chỉ khi đã duyệt kết quả, cho phép ghi các file đủ điều kiện.
.\gradlew.bat generateModuleContentFromSheetByFiles "-PsheetFileUrl=https://docs.google.com/spreadsheets/d/1ghIDu-knT1o5uE4LHpSf5_l3aZMJxQpyB2c7UT-BTnU/edit" "-PsheetMode=APPLY"

# ByLocation: cấu hình fileUrl/sheetId qua extension của module như mục 5,
# rồi chạy task tại đường dẫn Gradle module; vẫn DRY_RUN theo mặc định.
.\gradlew.bat :module:platform:development:programming:paradigm:object-oriented:generateModuleContentFromSheetByLocation
```

Luồng xử lý chung:

1. **ByLocation** xác minh tab gid và I3 khớp chính xác đường dẫn module thực
   tế. **ByFiles** lấy metadata/client một lần, quét tab theo thứ tự spreadsheet
   và bỏ qua tab tên đúng `Template`. Đọc I3 tất cả tab, ngăn hai tab cùng
   đích (cả hai bị đánh dấu FAILED), chỉ chấp nhận đường dẫn tương đối an toàn
   nằm trong `module/` của repository.
2. Kiểm tra header F2:H2, giới hạn Menu và các mốc F3:H...; dữ liệu
   phải đầy đủ, đúng thứ tự và không có khoảng trống xen giữa.
   Nếu F3:H3 trống hoàn toàn, APPLY xóa Roadmap VI cũ và trả CLEARED_EMPTY;
   DRY_RUN không xóa. Dữ liệu chỉ trống một phần là lỗi BLOCKER.
3. Chuyển Title → ID slug + title; Purpose → purpose; mỗi dòng Objectives
   → phần tử danh sách. Chưa tự sinh dependsOn/relatedKnowledge/relatedModules.
4. Sinh một file roadmap.yml mới chỉ từ F:G:H, bằng Groovy và SnakeYAML.
   Không lấy lại comment, id, quan hệ hoặc metadata cũ từ file đích.
5. APPLY: dựng snapshot mới trong stage, exact readback và preflight,
   rồi xóa vĩnh viễn toàn bộ roadmap/vi (bao gồm file cũ/stale)
   trước khi cài snapshot từ Sheet. KHÔNG backup. Nếu Sheet trống hợp lệ,
   roadmap/vi được làm sạch và không sinh roadmap.yml. DRY_RUN không ghi.
   Lỗi nguồn phải dừng trước khi xóa. Không sửa roadmap/en hay Sheet.

### Import README từ Menu + Knowledge (hai phần ghép cùng một service)

`ReadmeSheetImportService` chạy cùng task sau Roadmap, nhưng có điều kiện
`SKIPPED` độc lập. **Google Sheets là nguồn chuẩn** cho
`<I3>/content/readme/docs/vi/`. Cách đọc:

1. Kiểm tra header Menu. Nếu A20 trống: APPLY xóa sạch content/readme/docs/vi
   theo snapshot rỗng, DRY_RUN không thay đổi file.
2. Hàng có A (Menu Cấp 1) phải có **filename tiếng Anh** tại B cùng hàng,
   ví dụ `OOPBasic.md`; hàng A trống và B có text là Menu Cấp 2. Nếu
   **bất kỳ** tên file cấp 1 nào chưa có, `WARN SKIPPED_MISSING_FILENAME`
   cho toàn bộ README của tab, không đọc Knowledge và không ghi một phần.
   Tên filename phải hợp lệ theo `[A-Za-z][A-Za-z0-9]*.md`, duy nhất
   không phân biệt hoa/thường. Công đoạn AI đặt tên file diễn ra trước
   importer, **Gradle không gọi AI**.
3. Chỉ khi đủ filename, đọc các câu trả lời `C:N` (12 ô) ở hàng Menu Cấp 2,
   **ghép nội dung mỗi bài không có separator**. Không có câu trả lời vẫn
   giữ một section rỗng. Các mảnh trả lời không được có lỗ hổng.
4. Tạo một file cho mỗi Menu Cấp 1, ví dụ
   `content/readme/docs/vi/1.OOPBasic/OOPBasic.md`. Section anchor được tạo từ
   stem Camel/PascalCase chuyển `kebab-case` + số thứ tự bắt đầu
   `001` trong mỗi file: `OOPBasic.md` → `oop-basic-001`;
   `Class.md` → `class-001`. Dùng
   `GenerateInternalReadmeMenuService` để tự tạo `## Menu`,
   `<details>`, nút về đầu trang.
5. APPLY dựng và xác minh toàn bộ Markdown trước, sau đó xóa vĩnh viễn
   content/readme/docs/vi (kể cả orphan và folder phụ) và cài snapshot mới từ Sheet.
   Ngay cả khi không đổi nội dung vẫn thay toàn bộ. KHÔNG backup.
   Menu trống hợp lệ thì CLEARED_EMPTY. Thiếu filename hoặc cấu trúc Menu
   lỗi khi đã có dữ liệu → SKIPPED/BLOCKER và không xóa. DRY_RUN không ghi.
   Không tác động readme/en, BASE, LIST hay Google Sheets.

### Import Video Script từ STEP 8 — 100% Groovy/Java (không AI)

VideoSheetImportService chạy sau Roadmap và README trong cả hai task.
Mỗi Menu Cấp 1 ở `A<R>` có Video Script YAML tại đúng `P<R>:S<R>`
(header `X19:AA19 = Video Script`); ghép X→AA không thêm separator.
Importer dùng filename Knowledge `.md` ở `B<R>` làm định danh thư mục
`<ordinal>.<stem>/video-script.yml`; không suy ra row từ số thứ tự chương.
Validate cú pháp YAML, `title` với A, `sections[].title` với B con,
Scene/Transition, Time/Visual/Script/Purpose và mỗi fragment tối đa
30.000 Unicode code points, không gọi AI.

APPLY dựng/validate/stage/readback snapshot trước, rồi xóa vĩnh viễn
toàn bộ `content/video/vi/menu` và cài chỉ các Video Script có nội dung trên Sheet.
KHÔNG giữ video.url, skeleton, video đã biên soạn hay orphan cũ.
Hàng script trống không có file tương ứng; toàn bộ trống → CLEARED_EMPTY.
Không backup. DRY_RUN không ghi. Script lỗi → BLOCKER trước khi xóa.
Không chỉnh BUILD_VIDEO, video/en hay Google Sheets.

Tài liệu chính thức: [Application Default Credentials](https://cloud.google.com/docs/authentication/application-default-credentials),
[Service Account Impersonation](https://cloud.google.com/docs/authentication/use-service-account-impersonation),
[Google Sheets API](https://developers.google.com/workspace/sheets/api/guides/authorizing).
