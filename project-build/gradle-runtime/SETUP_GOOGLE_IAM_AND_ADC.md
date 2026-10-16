# Google Sheets ADC Setup — gradle-runtime

Tài liệu thiết lập **Application Default Credentials (ADC)** cho Gradle task
`generateModuleContentFromSheet` trên Windows. Cách thiết lập này dùng
**Service Account Impersonation**, không cần tải xuống JSON private key.

> **Trạng thái hiện tại:** task chỉ gọi Google Sheets API với quyền **read-only**
> để kiểm tra metadata của spreadsheet/tab (`gid`) và đọc duy nhất ô **I3**
> (`Module Location`). Nếu I3 khác thư mục module đang thực thi Gradle task,
> task dừng ngay bằng lỗi. Chưa đọc nội dung bài học, chưa generate Knowledge/Video
> và chưa ghi nội dung vào repository hoặc Sheet.

## 1. Cách hoạt động

```text
Module Gradle (USE_TASK=TRUE)
    └── project-orchestration → TaskSetupPlugin → SheetsTaskPlugin
            └── generateModuleContentFromSheet
                    ├── extension: sheetId, fileUrl
                    └── SheetsMetadataReaderService
                            ├── GoogleCredentials.getApplicationDefault()
                            ├── ImpersonatedCredentials (Service Account)
                            ├── scope: spreadsheets.readonly
                            ├── Sheets API: spreadsheets.get (metadata only)
                            └── Sheets API: values.get (đọc I3 và xác minh module)
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

Task được `project-orchestration` đăng ký cho module có `USE_TASK=TRUE`.
Extension DSL được cấu hình ở **`build.gradle` của module** (nếu module
có script cấu hình), ví dụ:

```groovy
generateModuleContentFromSheet {
    sheetId = 'YOUR_TAB_GID'
    fileUrl = 'https://docs.google.com/spreadsheets/d/YOUR_SPREADSHEET_ID/edit'
}
```

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
            def sheets = project.extensions.getByName('generateModuleContentFromSheet')
            sheets.sheetId.set('YOUR_TAB_GID')
            sheets.fileUrl.set('https://docs.google.com/spreadsheets/d/YOUR_SPREADSHEET_ID/edit')
        }
    }
}
```

Từ **thư mục gốc `java-learning/`**, chạy bằng CMD sau khi đặt biến môi trường
ở mục 4:

```cmd
gradlew.bat --init-script "%TEMP%\verify-sheets.init.gradle" :module:platform:development:programming:paradigm:object-oriented:generateModuleContentFromSheet
```

Kết quả mong đợi:

```text
[SHEETS] Module Location verified: module/... matches tab "..." (gid=...), cell I3.
[SHEETS] Read-only access OK ... spreadsheet "...", tab "..." (gid=...)
[SHEETS] Knowledge/Video generation is not implemented yet; no files were written.
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
- Khi bắt đầu triển khai import Knowledge/Video, bổ sung kiểm tra mapping,
  validate/stage trước khi ghi file; **hiện tại mới xác minh quyền đọc metadata**.

Tài liệu chính thức: [Application Default Credentials](https://cloud.google.com/docs/authentication/application-default-credentials),
[Service Account Impersonation](https://cloud.google.com/docs/authentication/use-service-account-impersonation),
[Google Sheets API](https://developers.google.com/workspace/sheets/api/guides/authorizing).
