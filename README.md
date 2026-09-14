# Java Learning Repository

Repository học tập/thử nghiệm với **Java 21, Spring Boot 3.3.x và Gradle multi-module/composite build**.

## Dành cho người tiếp tục phát triển project

### Project flow

```text
settings.gradle
    ↓
project-orchestration              # quyết định capability và thứ tự apply
    ↓
project-build/gradle-runtime       # build-time automation
    ↓
module/                            # module được cấu hình/generated
    ↓
project-build/springboot-runtime   # shared runtime capability
```

Build convention:

```text
Orchestration → chọn capability
Plugin        → wiring/lifecycle
Service       → implementation
Task          → explicit execution
```

Một **real module** được nhận diện bằng local `gradle.properties`.

### Tạo module mới từ số 0

1. Tạo thư mục dưới `module/` và file:

   ```text
   <module>/gradle.properties
   ```

2. Reload/Sync Gradle. Build system sẽ nhận diện module và tạo/sync `master.json`, `properties.json`.

3. Cấu hình các metadata chính trong `master.json`:

   ```text
   MODULE_TYPE        = APPLICATION | LIBRARY | PLATFORM
   JAVA_BASE_PACKAGE  = com.example.learning   # có thể override
   SERVICE_NAME       = logical name duy nhất
   ```

   Bật capability cần dùng bằng các flag như `BUILD_YML`, `BUILD_ENV`, `BUILD_README`, `USE_TASK`, `ADD_MODULE_DEPEND`, `USE_DATABASE`, `BUILD_SWAGGER`.

4. Reload Gradle lần nữa để tạo structure/capability tương ứng. `APPLICATION` và `LIBRARY` có Java/resource structure; `APPLICATION` được khởi tạo main class nếu chưa tồn tại.

### YML

Đặt `BUILD_YML=TRUE`.

- `application.yml`: runtime source of truth. Setup chỉ tạo skeleton khi file missing/blank; sau đó file là human-owned và không bị overwrite.
- `application-module.yml`: module composition contract. Setup chỉ tạo skeleton khi file missing/blank; sau đó file là human-owned và không bị overwrite.
- `combineYaml` (`USE_TASK=TRUE`): chỉ merge `application-module.yml` của module hiện tại và dependency. Dependency/file/cycle/YAML không hợp lệ sẽ fail task.
- YAML dependency có 2 nguồn: explicit từ `BUILD_YML_MODULE_DEPEND` và capability-derived; ví dụ `BUILD_SWAGGER=TRUE` tự thêm `GLOBAL_SWAGGER_CONFIG`.
- `application-merged.yml`: generated reference; review rồi cập nhật phần cần thiết vào `application.yml`.

```text
dependency application-module.yml(s)
        ↓
current application-module.yml
        ↓ combineYaml
application-merged.yml
        ↓ review
application.yml
```

### ENV

Đặt:

```text
BUILD_ENV=TRUE
USE_TASK=TRUE
```

Chạy `generateEnvFile` để tạo/sync:

```text
.env
.env.example
```

`.env` có timestamp để biết thời điểm generate/sync.

### README của module

Đặt:

```text
BUILD_README=TRUE
USE_TASK=TRUE
```

Nếu cần nhiều ngôn ngữ, cấu hình `README_LANGUAGE` trong `properties.json`.

Các task chính:

```text
generateInternalReadmeMenu
generateFinalReadme
translateMarkdown
```

README generation phải idempotent: chạy nhiều lần không được duplicate generated markup.

### Dependency giữa module

Module được tham chiếu bằng `SERVICE_NAME`. Khi dùng auto dependency, bật capability tương ứng và cấu hình `MODULE_DEPEND_LIST`.

### File ownership

```text
Human-owned → source code, application.yml, application-module.yml, README content, VALUE trong metadata
Generated   → task.gradle, application-merged.yml, generated enums/catalogs, STRUCTURE.md
```

Không chỉnh generated output để thay đổi source configuration.

## Dành cho người học

Learning guide và learning path **sẽ được cập nhật sau**. Nội dung trong `module/` hiện chưa được chuẩn hóa đủ để cung cấp một lộ trình học chính thức.

## Tài liệu

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — kiến trúc chi tiết.
- [`AGENTS.md`](./AGENTS.md) — context/working rules cho AI.
- [`STRUCTURE.md`](./STRUCTURE.md) — cây module generated.
