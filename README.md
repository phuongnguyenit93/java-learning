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

project-portal/                    # Spring Boot + React learning UI
```

Build convention:

```text
Orchestration → chọn capability
Plugin        → wiring/lifecycle
Service       → implementation
Task          → explicit execution
```

Một **real module** được nhận diện bằng local `gradle.properties`.

### Project Portal

`project-portal` là Learning Portal ở cấp root, tách khỏi `module/`:

```text
Spring Boot :9098
+ React + TypeScript + Vite + React Router
```

Frontend hiện static-first và chưa gọi backend API. `ProjectStructureService` generate `module-catalog.json` cho Portal; Overview/Menu/Knowledge được build từ README projections, còn API Docs consume generated localized Swagger metadata trực tiếp trong browser. Quiz vẫn đang dùng fixture tạm. API Docs hiện là reference-only, không live execute/debug trong Portal. Gradle build React bằng Vite rồi copy `dist` vào `classpath:/static`. Current route dùng `HashRouter`, ví dụ `http://localhost:9098/#/learning/THREAD`.

Chi tiết xem [`PROJECT_PORTAL.md`](./PROJECT_PORTAL.md).

### Tạo module mới từ số 0

1. Tạo thư mục dưới `module/` và file:

   ```text
   <module>/gradle.properties
   ```

2. Reload/Sync Gradle. Build system sẽ nhận diện module và tạo/sync `master.json`, `properties.json`.

3. Cấu hình các metadata chính trong `master.json`:

   ```text
   MODULE_TYPE        = SERVLET | REACTIVE | LIBRARY | PLATFORM
   JAVA_BASE_PACKAGE  = com.example.learning   # có thể override
   SERVICE_NAME       = logical name duy nhất
   ```

   Bật capability cần dùng bằng các flag như `BUILD_YML`, `BUILD_ENV`, `BUILD_README`, `USE_TASK`, `ADD_MODULE_DEPEND`, `USE_DATABASE`, `BUILD_SWAGGER`, `BUILD_EXECUTION_CONTEXT`.

4. Reload Gradle lần nữa để tạo structure/capability tương ứng. `SERVLET`, `REACTIVE` và `LIBRARY` có Java/resource structure; `SERVLET` và `REACTIVE` được khởi tạo main class nếu chưa tồn tại. `SERVLET` dùng Spring MVC, `REACTIVE` dùng Spring WebFlux.

### YML

Đặt `BUILD_YML=TRUE`.

- `application.yml`: runtime source of truth. Setup chỉ tạo skeleton khi file missing/blank; sau đó file là human-owned và không bị overwrite.
- `application-module.yml`: module composition contract. Setup chỉ tạo skeleton khi file missing/blank; sau đó file là human-owned và không bị overwrite.
- `combineYaml` (`USE_TASK=TRUE`): chỉ merge `application-module.yml` của module hiện tại và dependency. Dependency/file/cycle/YAML không hợp lệ sẽ fail task.
- YAML dependency có 2 nguồn: explicit từ `BUILD_YML_MODULE_DEPEND` và capability-derived. `MODULE_TYPE=SERVLET` tự thêm `SPRING_WEB`, `MODULE_TYPE=REACTIVE` tự thêm `SPRING_REACTIVE`; `BUILD_SWAGGER=TRUE` tự thêm stack-neutral `GLOBAL_SWAGGER_CONFIG`.
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

### Swagger

`BUILD_SWAGGER=TRUE` sử dụng core dùng chung và web-stack adapter tương ứng:

```text
GLOBAL_SWAGGER_CONFIG
        └── common registrar/customizer/static assets/YAML

SERVLET
        └── GLOBAL_SWAGGER_SERVLET
                └── springdoc-openapi-starter-webmvc-ui

REACTIVE
        └── GLOBAL_SWAGGER_REACTIVE
                └── springdoc-openapi-starter-webflux-ui
```

Hai adapter đều phụ thuộc `GLOBAL_SWAGGER_CONFIG`. Java dependency chọn adapter theo `MODULE_TYPE`, trong khi `combineYaml` chỉ merge `GLOBAL_SWAGGER_CONFIG/application-module.yml` để tránh duplicate Swagger configuration.

Swagger có contract `readmeRelated` để liên kết controller/method với chapter/section trong README. Mapping do developer khai báo; hệ thống dùng mapping đó để hiển thị chapter, sắp xếp API theo learning path và mở đúng README anchor ngay trong Swagger. Controller/method chưa được liên kết sẽ được đưa xuống cuối và hiển thị trạng thái chưa có nội dung README.

### Execution Context / AI

`BUILD_EXECUTION_CONTEXT=TRUE` tạo context của một lần chạy gồm request/response, log/runtime và source liên quan.

```text
Phase 1–4: Capture → Observation → Source → Query          [implemented]
Phase 5–7: CoS Core → CoS transport → ChatGPT              [delegated]
```

User có 2 cách dùng với chat AI:

```text
Manual Mode    → Explorer/REST → ZIP/JSON → upload vào chat AI
Connected Mode → ChatGPT → Chat On Steroids → query Execution Context REST + project source
```

Phase 5–7 hiện không cần tự xây MCP/tunnel/chat backend trong project: Chat On Steroids cung cấp tool/MCP bridge và transport, còn ChatGPT xử lý conversation/reasoning. Embedded AI chat trong Swagger là optional; chỉ cân nhắc sau nếu có requirement UX riêng.

Hai hướng vẫn dùng chung một `ExperimentContext`; Swagger chỉ trigger/inspect/export execution, không sở hữu AI integration.

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

Ngôn ngữ của module được cấu hình tập trung bằng `MODULE_LANGUAGE` trong `master.json` và được dùng chung cho các generator README/Swagger.

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
- [`PROJECT_PORTAL.md`](./PROJECT_PORTAL.md) — Learning Portal hiện tại và target architecture.
- [`STRUCTURE.md`](./STRUCTURE.md) — cây module generated.
