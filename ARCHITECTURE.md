# Java Learning Repository Architecture

## 1. Mục đích của tài liệu

Tài liệu này mô tả **kiến trúc hiện tại của repository `java-learning`** dựa trên implementation thực tế của project.

Mục tiêu của tài liệu là trả lời các câu hỏi cấp repository:

- Repository được chia thành những boundary nào?
- Build system phát hiện và cấu hình module như thế nào?
- Metadata nào là source of truth?
- `project-orchestration`, `gradle-runtime` và `springboot-runtime` khác nhau ở đâu?
- Generated file và human-owned file được phân biệt như thế nào?
- Build-time capability và runtime capability kết nối với nhau ra sao?
- Một capability mới nên được đặt ở đâu?

Tài liệu này **không phân tích package/source code bên trong `module/`**. `module/` chỉ được đề cập ở mức contract, metadata, build lifecycle và dependency boundary.

---

## 2. Tech baseline

Repository hiện được xây dựng quanh các baseline chính:

- Java 21.
- Spring Boot 3.3.x.
- Gradle multi-project kết hợp composite build.
- Groovy cho phần lớn custom Gradle plugin.
- JUnit Platform cho project có Java plugin.
- Docker Compose cho module cần runtime infrastructure.

Root project quản lý các plugin và convention dùng chung như:

- Spring Boot.
- Spring Dependency Management.
- Java Library.
- Lombok.
- Docker Compose plugin.
- Spring Cloud BOM.

---

## 3. Repository landscape

```text
java-learning/
├── module/                         # Learning modules
├── internal/                       # Internal supporting modules/resources
├── project-build/
│   ├── gradle-runtime/             # Build-time implementation
│   └── springboot-runtime/         # Shared Spring Boot runtime capability
├── project-orchestration/          # Build composition/policy
├── build.gradle                    # Root project conventions
├── settings.gradle                 # Composite build entry point
├── README.md
├── ARCHITECTURE.md
└── STRUCTURE.md                    # Generated module map
```

Ba boundary quan trọng nhất của infrastructure là:

```text
project-orchestration
        ↓ decides what should run

project-build/gradle-runtime
        ↓ implements build-time behavior

project-build/springboot-runtime
        ↓ implements shared application runtime behavior
```

`module/` là consumer chính của các capability trên, nhưng nội dung package bên trong module nằm ngoài phạm vi tài liệu này.

---

## 4. Nguyên tắc phân tầng

Project hiện theo một rule khá rõ:

```text
Orchestration → chọn capability và thứ tự áp dụng
Plugin        → tích hợp với Gradle lifecycle
Service       → chứa implementation logic
Task          → explicit execution entry / side effect
Extension     → typed DSL/configuration surface
Utils         → stateless reusable helper
```

Điểm quan trọng là **orchestration không nên chứa thuật toán implementation chi tiết**.

Ví dụ:

```text
ModuleOrchestrationPlugin
        ↓ quyết định BUILD_README có bật hay không
ReadmeSetupPlugin
        ↓ wiring lifecycle
Readme service
        ↓ implementation
Readme task
        ↓ explicit execution khi developer gọi
```

---

## 5. Composite build architecture

Root `settings.gradle` nạp hai included build:

```groovy
pluginManagement {
    includeBuild('project-build/gradle-runtime')
    includeBuild('project-orchestration')
}
```

Sau đó apply:

```groovy
plugins {
    id 'com.example.settings-orchestration'
}
```

`project-orchestration` tự nó lại include:

```text
../project-build/gradle-runtime
```

và khai báo dependency:

```text
com.example.learning:gradle-runtime:1.0.0
```

Trong composite build, dependency này được resolve về included build `gradle-runtime`.

Dependency tổng thể:

```text
java-learning
│
├── includeBuild(project-build/gradle-runtime)
│
└── includeBuild(project-orchestration)
        │
        └── depends on gradle-runtime
```

Thiết kế này cho phép custom Settings plugin có classpath ngay từ Gradle initialization phase mà không phải nhét toàn bộ build logic vào root `buildSrc`.

---

## 6. Project orchestration

`project-orchestration` là **policy/composition layer**.

Nó đăng ký ba plugin:

```text
com.example.settings-orchestration
com.example.root-orchestration
com.example.module-orchestration
```

### 6.1 Settings orchestration

`SettingsOrchestrationPlugin` apply theo thứ tự:

```text
PresetSetupPlugin
        ↓
PropertiesSetupPlugin
```

Thứ tự này là một phần của architecture contract.

`PresetSetupPlugin` cần chạy trước để:

- scan repository;
- tìm real module;
- include Gradle project;
- synchronize metadata;
- generate catalog cần thiết ở settings phase.

Sau đó `PropertiesSetupPlugin` đăng callback `projectsLoaded` để inject metadata vào từng Gradle `Project`.

### 6.2 Root orchestration

`RootOrchestrationPlugin` chỉ được phép apply vào root project.

Nó compose:

```text
CatalogSetupPlugin
StructureSetupPlugin
DatabaseSetupPlugin
CleanupSetupPlugin
```

Trong đó:

- Catalog và Structure đợi `projectsEvaluated` trước khi generate output.
- Database plugin đăng ký explicit root tasks.
- Cleanup plugin đăng ký explicit cleanup tasks.

### 6.3 Module orchestration

`ModuleOrchestrationPlugin` là nơi quyết định capability cho từng real module.

Flow hiện tại:

```text
Real module?
    ↓ yes
Resolve SERVICE_NAME
    ↓
DependencySetupPlugin
    ↓
ConfigSetupPlugin
    ↓
YmlSetupPlugin
    ↓
ENV setup        [conditional]
    ↓
README setup     [conditional]
    ↓
Swagger setup    [conditional]
    ↓
Task setup       [conditional]
    ↓
Docker setup     [docker-compose.yml exists]
```

Condition chính:

| Capability | Condition |
| --- | --- |
| ENV | `BUILD_ENV=TRUE` hoặc `BUILD_ENV_PROFILE=TRUE` |
| README | `BUILD_README=TRUE` |
| Swagger | `BUILD_SWAGGER=TRUE` |
| Task system | `USE_TASK=TRUE` |
| Docker | `docker-compose.yml` tồn tại |

`ConfigSetupPlugin` và `YmlSetupPlugin` được apply cho mọi real module theo convention hiện tại.

---

## 7. Real module contract

Một real module được nhận diện bằng file:

```text
<module>/gradle.properties
```

`gradle.properties` ở đây chủ yếu đóng vai trò **physical marker** để scanner và orchestration phân biệt real module với Gradle intermediate project.

Rule:

```text
có local gradle.properties
        ↓
real module

không có local gradle.properties
        ↓
container / intermediate project
```

Không được giả định rằng mọi Gradle subproject đều là real module.

---

## 8. Module metadata architecture

Metadata module được chia thành hai tầng:

```text
master.json
    ↓ high-level identity and feature flags

properties.json
    ↓ detailed configuration of enabled feature groups
```

### 8.1 Canonical schema

Canonical schema nằm trong:

```text
project-build/gradle-runtime/src/main/resources/automation/
├── master.json
└── properties.json
```

Canonical schema sở hữu:

- key nào tồn tại;
- description;
- type;
- group;
- default value;
- metadata cấu trúc khác.

Module-local JSON sở hữu **giá trị cấu hình thực tế của module**.

### 8.2 `master.json`

`master.json` chứa các metadata cấp cao, ví dụ:

```text
MODULE_TYPE
JAVA_BASE_PACKAGE
SERVICE_NAME
SERVICE_NAME_DESCRIBE
IS_MODULE_DEPEND
BUILD_ENV
BUILD_YML
BUILD_README
BUILD_TESTER
BUILD_SWAGGER
BUILD_DATABASE_MODULE
ADD_MODULE_DEPEND
USE_DATABASE
USE_TASK
```

`JAVA_BASE_PACKAGE` hiện có canonical default:

```text
com.example.learning
```

Module có quyền override giá trị này.

Ví dụ shared Swagger runtime dùng:

```text
com.example.projectbuild.swagger
```

### 8.3 `properties.json`

`properties.json` chỉ chứa các group đang active.

Một group được xem là active khi metadata tương ứng trong `master.json`:

```text
VALUE = TRUE
và
GROUP != null
```

Ví dụ các group hiện được sử dụng có thể phục vụ:

- ENV;
- YML;
- README;
- Swagger;
- database;
- module dependency;
- task configuration.

### 8.4 Synchronization rule

`ModuleConfigurationSyncService` đồng bộ module-local JSON với canonical schema.

Contract hiện tại:

```text
Canonical schema metadata
        ↓ authoritative

Existing non-blank VALUE in module
        ↓ preserved
```

Nói cách khác:

- schema thuộc build system;
- `VALUE` thuộc module/developer;
- key không còn trong canonical schema có thể bị loại khỏi synchronized output;
- key mới trong canonical schema được thêm vào module khi sync.

### 8.5 Property injection

Sau khi Gradle project hierarchy được tạo, metadata được inject vào `Project.extraProperties`.

Thứ tự:

```text
master.json
    ↓
properties.json
```

Nếu cùng một key xuất hiện ở cả hai nơi thì giá trị được inject sau từ `properties.json` sẽ thắng trong phase này.

Consumer sau đó sử dụng:

```groovy
project.findProperty('...')
```

---

## 9. Module type model

`MODULE_TYPE` có bốn loại chính:

| Type | Physical Java structure | Runnable | Artifact behavior |
| --- | --- | --- | --- |
| `SERVLET` | Có | Có | Spring MVC/Servlet Boot application / `bootJar` |
| `REACTIVE` | Có | Có | Spring WebFlux Boot application / `bootJar` |
| `LIBRARY` | Có | Không | plain `jar`, `bootJar`/`bootRun` disabled |
| `PLATFORM` | Không tự tạo physical Java structure | Không | plain `jar`, `bootJar`/`bootRun` disabled |

Hai runnable type có dependency mặc định khác nhau:

```text
SERVLET
→ spring-boot-starter-web

REACTIVE
→ spring-boot-starter-webflux
```

`APPLICATION` là legacy metadata value và được `ModuleConfigurationSyncService` migrate thành:

```text
APPLICATION → SERVLET
```

Canonical schema và code mới không tiếp tục expose `APPLICATION` như một `ModuleType` hợp lệ.

Common build configuration vẫn được apply trước khi specialized module behavior được quyết định.

Nếu `MODULE_TYPE` blank hoặc invalid:

```text
common setup vẫn chạy
specialized structure/config bị skip
```

---

## 10. Java package ownership

Java base package không còn là global hidden property trong root `settings.gradle`.

Nó hiện là metadata chính thức của từng module:

```text
JAVA_BASE_PACKAGE
```

Flow:

```text
automation/master.json
    JAVA_BASE_PACKAGE=com.example.learning
            ↓
module master.json
            ↓ optional override
SettingPropertiesInjectionService
            ↓
project.JAVA_BASE_PACKAGE
            ↓
ModuleStructureService
```

Với `SERVLET` và `REACTIVE`, `ModuleStructureService` dùng package này để:

- tạo source directory;
- generate main application class nếu chưa tồn tại;
- tạo `MAIN_CLASS_PATH`.

Main class được generate theo web stack:

```text
SERVLET
→ Spring Boot application class có Servlet deployment support

REACTIVE
→ regular @SpringBootApplication main class
→ không phụ thuộc SpringBootServletInitializer
```

`MAIN_CLASS_PATH` sau đó là source of truth cho những consumer như IntelliJ run configuration generation.

IntelliJ task không tự suy luận package lần thứ hai.

```text
JAVA_BASE_PACKAGE
        ↓
ModuleStructureService
        ↓
MAIN_CLASS_PATH
        ↓
IntellijTaskPlugin
```

---

## 11. Gradle runtime

`project-build/gradle-runtime` là **build-time control plane** của repository.

Nó chứa các nhóm responsibility chính:

```text
settings setup
root setup
module setup
manual tasks
generated plugin/catalog source
shared utilities
canonical automation resources
task/extension registry resources
generator scripts for build framework itself
```

### 11.1 Settings layer

Settings layer chịu trách nhiệm:

- scan filesystem;
- include real modules;
- sync `master.json` và `properties.json`;
- generate typed module/database information;
- inject properties vào project hierarchy.

### 11.2 Root layer

Root layer chịu trách nhiệm repository-wide behavior như:

- dependency catalog projection;
- structure documentation;
- database maintenance tasks;
- cleanup tasks.

### 11.3 Module layer

Module layer cung cấp các capability như:

- common/type-specific build configuration;
- source structure;
- module dependency wiring;
- YML setup;
- ENV setup;
- README setup;
- Swagger build-time setup;
- task dispatcher;
- Docker setup.

### 11.4 Manual task layer

Task plugin cung cấp các action developer chủ động chạy như:

- generate ENV;
- README generation/translation;
- YAML merge;
- IntelliJ run configuration;
- Swagger description generation.

External hoặc destructive side effect nên nằm trong explicit task execution thay vì tự chạy chỉ vì Gradle reload.

---

## 12. Plugin registry architecture

Build framework có generated registry `ProjectPluginEnum`.

Registry biểu diễn mapping:

```text
logical plugin
    ↓
plugin id
    ↓
implementation class
```

Module orchestration sử dụng registry này để apply capability thay vì hardcode implementation class.

Registry là **generated projection**, không phải canonical input.

Các generator script của `gradle-runtime` chịu trách nhiệm cập nhật plugin list, enum và stub khi framework thay đổi.

---

## 13. Task architecture

Task system có hai tầng enablement.

Tầng 1:

```text
USE_TASK=TRUE
    ↓
TaskSetupPlugin được apply
```

Tầng 2:

`TaskSetupPlugin` đọc:

```text
task/module-task-list.json
task/task-extension-list.json
```

và quyết định task plugin nào được bật theo `propCheck`.

Ví dụ conceptual:

```text
USE_TASK=TRUE
        ↓
Task dispatcher active

BUILD_README=TRUE
        ↓
README task capability active

BUILD_SWAGGER=TRUE
        ↓
Swagger task capability active

propCheck blank
        ↓
task capability luôn active khi dispatcher đã được bật
```

`task.gradle` là generated DSL reference cho những extension/task đang được expose.

Nó không phải source of truth và không nên chứa manual configuration cần được bảo tồn.

---

## 14. Dependency architecture

Module dependency được resolve bằng stable logical identity:

```text
SERVICE_NAME
```

Thay vì buộc consumer hardcode Gradle path ở mọi nơi, build logic có thể:

```text
SERVICE_NAME
    ↓
ModuleProjectUtils
    ↓
Gradle Project
    ↓
project dependency
```

`DependencySetupPlugin` tạo DSL extension sớm nhưng trì hoãn resolution thực tế tới `projectsEvaluated`.

Điều này cho phép:

- mọi project hoàn thành configuration trước;
- module build script khai báo dependency DSL;
- configuration như `implementation`, `api`, `annotationProcessor` đã tồn tại trước khi dependency được wire.

Dependency catalog được generate sau khi toàn bộ project đã evaluate.

Generated dependency catalog không phải canonical dependency declaration.

---

## 15. Configuration ownership

Một trong những rule quan trọng của repository là phân biệt **human-owned** và **generated**.

### 15.1 Human-owned

Các file trở thành human-owned sau khi được khởi tạo hoặc do developer trực tiếp quản lý, ví dụ:

- application source;
- main application class sau lần generate đầu;
- `application.yml` của application;
- README content do developer viết;
- module-local metadata `VALUE`.

Build automation không được tùy ý overwrite các nội dung này.

### 15.2 Generated

Generated artifact có thể được rebuild hoàn toàn từ source-of-truth khác, ví dụ:

- `ModuleListEnum`;
- `DatabaseListEnum`;
- `ProjectPluginEnum`;
- `module-depend.json`;
- `STRUCTURE.md`;
- `module-structure.txt`;
- `task.gradle`;
- `application-merged.yml`;
- những phần README/menu được đánh dấu là generated.

Developer không nên chỉnh generated output để thay đổi behavior gốc.

---

## 16. Idempotency và determinism

Build automation ưu tiên hai property:

```text
same input → same output
same output → do not rewrite file
```

Nhiều generator hiện thực hiện comparison trước khi write để tránh:

- timestamp thay đổi vô ích;
- IntelliJ nhận file thay đổi không cần thiết;
- generated diff nhiễu;
- duplicate generated markup.

README internal-menu generator còn có requirement chặt hơn:

```text
N lần generate
    =
1 lần generate
```

Generated markup cũ phải được remove/rebuild từ canonical content thay vì append lặp lại.

---

## 17. YML architecture

YML capability phân biệt rõ runtime truth và generated suggestion.

### 17.1 `application.yml`

Đối với application:

```text
application.yml
```

là runtime configuration chính và được xem là **human-owned** sau khi có meaningful content.

YML của library/platform đóng vai trò configuration source/reference cho composition, không tự động biến thành runtime truth của application.

### 17.2 `application-merged.yml`

YML merge task tạo:

```text
application-merged.yml
```

với mục đích:

```text
dependency/module configuration
        +
application configuration
        ↓
generated suggestion
        ↓
developer review
        ↓
application.yml
```

`application-merged.yml` không phải runtime source of truth.

### 17.3 Capability-derived YML dependency

YML composition không chỉ đọc dependency khai báo explicit. Một số dependency được derive trực tiếp từ module type/capability:

```text
MODULE_TYPE=SERVLET
→ SPRING_WEB

MODULE_TYPE=REACTIVE
→ SPRING_REACTIVE

BUILD_SWAGGER=TRUE
→ GLOBAL_SWAGGER_CONFIG
```

Mỗi dependency ở đây đóng góp `application-module.yml` vào graph merge. Vì vậy một module `REACTIVE + BUILD_SWAGGER=TRUE` có conceptual YML graph:

```text
SPRING_REACTIVE/application-module.yml
        +
GLOBAL_SWAGGER_CONFIG/application-module.yml
        +
current module application-module.yml
        ↓
application-merged.yml
```

Swagger adapter Java không được thêm vào YML graph chỉ để phản ánh runtime dependency; YML core được dùng chung cho cả hai web stack.

---

## 18. ENV architecture

ENV setup và explicit ENV generation có ownership khác nhau.

Setup phase ưu tiên **không overwrite existing `.env`**.

Explicit ENV generator được developer chủ động gọi có thể rebuild/synchronize nội dung theo contract của task.

Timestamp trong generated `.env` là intentional metadata để developer biết thời điểm file được generate/sync.

Secret handling không nên biến secret thành canonical committed metadata.

---

## 19. README/documentation architecture

README automation gồm các responsibility chính:

- initialize structure;
- generate internal navigation;
- assemble final README;
- translate Markdown;
- preserve human-owned content;
- expose generated documentation resources cho runtime capability khi cần.

Translation pipeline tách:

```text
prepare markdown
    ↓
translation spans
    ↓
translator
    ↓
render back into markdown
```

Prepare/render là hai phía của cùng một transformation contract; translated spans được replace theo offset an toàn.

Generated documentation phải idempotent.

---

## 20. Build-time Swagger architecture

Swagger có **hai nửa tách biệt**.

Nửa build-time thuộc:

```text
project-build/gradle-runtime
```

Nó chịu trách nhiệm những việc như:

- enable Swagger capability theo metadata;
- chọn shared Swagger runtime adapter theo `MODULE_TYPE`;
- generate/synchronize API description metadata;
- copy README/resources cần thiết vào application artifact;
- expose Swagger task DSL.

Runtime dependency selection hiện là:

```text
BUILD_SWAGGER=TRUE + MODULE_TYPE=SERVLET
→ GLOBAL_SWAGGER_SERVLET

BUILD_SWAGGER=TRUE + MODULE_TYPE=REACTIVE
→ GLOBAL_SWAGGER_REACTIVE
```

Trong khi YML capability vẫn derive:

```text
BUILD_SWAGGER=TRUE
→ GLOBAL_SWAGGER_CONFIG
```

Sự tách này là intentional: Java runtime cần stack-specific adapter, còn Swagger YML contract hiện stack-neutral.

Swagger generator coi một số field là **human-owned metadata**.

Ví dụ `videoYoutubeId` được preserve qua regeneration sau khi đã tồn tại, kể cả entry historical/stale không còn active.

---

## 21. Spring Boot runtime architecture

`project-build/springboot-runtime` chứa **runtime capability dùng chung cho Spring Boot application**.

Khác với `gradle-runtime`, code ở đây chạy trong application process, không chạy trong Gradle build process.

Runtime không được phụ thuộc ngược Gradle API hoặc build generator.

Swagger runtime hiện được implementation thành ba module phối hợp:

```text
springboot-runtime/swagger
springboot-runtime/swagger-servlet
springboot-runtime/swagger-reactive
```

---

## 22. Shared Swagger runtime

Swagger runtime dùng mô hình **stack-neutral core + stack-specific adapter**.

```text
GLOBAL_SWAGGER_CONFIG
        │
        ├── GLOBAL_SWAGGER_SERVLET
        │       └── springdoc-openapi-starter-webmvc-ui
        │
        └── GLOBAL_SWAGGER_REACTIVE
                └── springdoc-openapi-starter-webflux-ui
```

### 22.1 Core `GLOBAL_SWAGGER_CONFIG`

Core là `LIBRARY` module:

```text
project-build/springboot-runtime/swagger
SERVICE_NAME=GLOBAL_SWAGGER_CONFIG
```

Nó dùng Java package riêng:

```text
com.example.projectbuild.swagger
```

thông qua module metadata:

```text
JAVA_BASE_PACKAGE=com.example.projectbuild.swagger
```

Điều này cố ý tách runtime library khỏi default application package:

```text
com.example.learning
```

để Swagger runtime không vô tình hoạt động nhờ component scan của consumer.

Core sở hữu những phần không phụ thuộc web stack:

- `DynamicSwaggerAutoConfiguration`;
- `DynamicSwaggerCondition`;
- `DynamicSwaggerRegistrar`;
- `GroupedOpenApi`/customizer behavior;
- custom Swagger static assets;
- shared Swagger YML composition contract.

Core dùng `springdoc-openapi-starter-common`. Nó không sở hữu `WebMvcConfigurer` hay `WebFluxConfigurer`.

### 22.2 Servlet adapter

```text
project-build/springboot-runtime/swagger-servlet
SERVICE_NAME=GLOBAL_SWAGGER_SERVLET
depends on GLOBAL_SWAGGER_CONFIG
```

Adapter này sở hữu:

- `springdoc-openapi-starter-webmvc-ui`;
- `ServletSwaggerAutoConfiguration`;
- `ServletSwaggerResourceConfiguration implements WebMvcConfigurer`;
- Servlet-specific resource/view mapping.

### 22.3 Reactive adapter

```text
project-build/springboot-runtime/swagger-reactive
SERVICE_NAME=GLOBAL_SWAGGER_REACTIVE
depends on GLOBAL_SWAGGER_CONFIG
```

Adapter này sở hữu:

- `springdoc-openapi-starter-webflux-ui`;
- `ReactiveSwaggerAutoConfiguration`;
- `ReactiveSwaggerResourceConfiguration implements WebFluxConfigurer`;
- Reactive resource mapping;
- reactive root redirect thông qua `RouterFunction` thay vì MVC `ViewControllerRegistry`.

### 22.4 Tại sao phải tách adapter

Mục tiêu là làm cho dependency graph đảm bảo chỉ đúng web stack cần thiết tồn tại:

```text
SERVLET consumer
→ GLOBAL_SWAGGER_SERVLET
→ webmvc-ui
→ không cần GLOBAL_SWAGGER_REACTIVE / webflux-ui

REACTIVE consumer
→ GLOBAL_SWAGGER_REACTIVE
→ webflux-ui
→ không cần GLOBAL_SWAGGER_SERVLET / webmvc-ui
```

Nếu MVC và WebFlux configuration cùng nằm trong core, core phải compile/reference cả hai stack hoặc dựa nhiều hơn vào optional dependency, classloading condition và runtime filtering. Adapter split loại bỏ phần không dùng khỏi graph ngay từ dependency selection.

### 22.5 Auto-configuration contract

Runtime đăng ký Spring Boot auto-configuration bằng:

```text
META-INF/spring/
org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

với class:

```text
com.example.projectbuild.swagger.DynamicSwaggerAutoConfiguration
```

Auto-configuration sử dụng `@AutoConfiguration` và import runtime components cần thiết.

Activation hiện phụ thuộc các điều kiện như:

- Springdoc class tồn tại;
- `swagger.enabled=true`.

Core và mỗi adapter đều đăng auto-configuration qua file `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` của chính module đó.

### 22.6 Runtime responsibility

Swagger runtime chịu trách nhiệm:

- tạo `GroupedOpenApi` theo configured languages;
- đọc generated Swagger metadata từ classpath;
- đưa README vào OpenAPI information;
- map controller/method metadata vào OpenAPI;
- map YouTube metadata thành OpenAPI extension;
- expose README resources;
- cung cấp custom Swagger UI/static assets;
- hỗ trợ navigation README trong Swagger UI mà không cần reload toàn page.

### 22.7 Build/runtime handoff

Contract giữa hai nửa Swagger là:

```text
gradle-runtime
    ↓ generate/copy resources

application artifact
    ↓ classpath

springboot-runtime/swagger core
    ↓ shared runtime behavior

MODULE_TYPE-selected adapter
    ├── SERVLET  → swagger-servlet
    └── REACTIVE → swagger-reactive
    ↓ stack-specific web integration

Swagger UI / OpenAPI
```

Runtime không gọi Gradle generator ngược trở lại.

---

## 23. Docker architecture

Docker setup hiện sử dụng **physical capability detection**:

```text
docker-compose.yml exists
        ↓
DockerSetupPlugin
```

Không cần một `BUILD_DOCKER` flag riêng trong orchestration hiện tại.

Điều này phản ánh rule:

> Sự tồn tại của `docker-compose.yml` là declaration rằng module có Docker runtime setup.

---

## 24. Root-level operational tasks

Root project expose một số explicit maintenance operation.

Ví dụ:

```text
backupDatabase
updateMongoWhitelistIP
cleanupFiles
cleanupEmptyFolders
```

Các action có side effect không chạy chỉ vì Gradle sync/reload.

Chúng chỉ chạy khi developer gọi task tương ứng.

---

## 25. Generated repository projections

Một số output giúp developer hoặc build system nhìn repository dưới dạng projection thuận tiện hơn.

Ví dụ:

```text
ModuleListEnum
DatabaseListEnum
ProjectPluginEnum
module-depend.json
STRUCTURE.md
module-structure.txt
task.gradle
```

Nguyên tắc:

```text
canonical input
    ↓
generator
    ↓
projection
```

Không đảo ngược thành:

```text
generated projection
    ↓
canonical source
```

trừ khi architecture contract của artifact đó được thay đổi rõ ràng.

---

## 26. Source of truth matrix

| Concern | Source of truth | Consumer / projection |
| --- | --- | --- |
| Real module identity | local `gradle.properties` | Settings scanner / orchestration |
| Metadata schema | `gradle-runtime` `automation/*.json` | module-local JSON |
| Module config value | module-local JSON `VALUE` | Gradle project properties |
| Java base package | `JAVA_BASE_PACKAGE` | source structure / `MAIN_CLASS_PATH` |
| Module type | `MODULE_TYPE` = `SERVLET | REACTIVE | LIBRARY | PLATFORM` | Config/source/dependency setup |
| Stable module identity | `SERVICE_NAME` | dependency/module lookup |
| Feature enablement | `master.json` flags | orchestration |
| Detailed feature config | `properties.json` | setup/task plugins |
| Runtime application config | application `application.yml` | Spring Boot runtime |
| Suggested composed config | dependency/application YML inputs | `application-merged.yml` |
| Build plugin registry input | plugin definitions/generator inputs | `ProjectPluginEnum` |
| Repository structure | filesystem/module discovery | `STRUCTURE.md` |

---

## 27. Gradle lifecycle

Flow tổng thể của một Gradle reload/build:

```text
Gradle start
    ↓
Load included builds
    ↓
Apply SettingsOrchestrationPlugin
    ↓
Preset setup
    ├── scan gradle.properties
    ├── include real modules
    ├── sync master/properties
    └── generate settings-time projections
    ↓
Create project hierarchy
    ↓
PropertiesSetupPlugin / projectsLoaded
    ↓
Inject module metadata into Project
    ↓
Configure root project
    ↓
RootOrchestrationPlugin
    ↓
Configure real modules
    ↓
ModuleOrchestrationPlugin
    ↓
Apply module capabilities
    ↓
projectsEvaluated
    ├── resolve deferred module dependencies
    ├── generate dependency catalog
    └── generate repository structure projection
    ↓
Execute requested tasks
```

Điểm quan trọng:

- metadata phải sẵn sàng trước module configuration;
- dependency resolution cần toàn project hierarchy đã evaluate;
- explicit task action chạy sau configuration;
- generated structure/catalog không nên làm side effect sớm hơn phase cần thiết.

---

## 28. Build-time vs runtime boundary

Boundary quan trọng nhất của project:

```text
Gradle process
    │
    │ project-build/gradle-runtime
    │ project-orchestration
    │
    └── produces/wires artifacts/resources
                ↓
Spring Boot application process
    │
    │ project-build/springboot-runtime
    │ application/module runtime code
```

Rules:

1. `project-orchestration` được phép phụ thuộc `gradle-runtime`.
2. `gradle-runtime` không phụ thuộc implementation package của learning module.
3. `springboot-runtime` không chứa Gradle scanning/generation logic.
4. Runtime code không gọi Gradle plugin/task API.
5. Build-time và runtime giao tiếp qua dependency, artifact, metadata và classpath resource.

---

## 29. File ownership rules

Mọi generator mới phải xác định file ownership trước khi implementation.

Ba loại ownership chính:

### Human-owned

Build system được phép initialize nhưng không được overwrite sau khi developer sở hữu nội dung.

### Generated-owned

Build system có quyền rebuild toàn bộ từ canonical inputs.

### Mixed ownership

Một file có schema/structure do build system quản lý nhưng field/value cụ thể do developer sở hữu.

`master.json` là ví dụ điển hình:

```text
schema metadata → build-owned
VALUE           → developer/module-owned
```

Generator phải encode ownership rule rõ ràng thay vì dựa vào assumption.

---

## 30. Architecture invariants

Các invariant dưới đây phản ánh architecture hiện tại và nên được giữ khi mở rộng project:

1. Real module được nhận diện bằng local `gradle.properties`.
2. Intermediate Gradle project không được coi là real module.
3. Canonical module metadata schema thuộc `gradle-runtime`.
4. Module-local non-blank `VALUE` được preserve khi schema sync.
5. `JAVA_BASE_PACKAGE` là module metadata, không phải hidden global settings property.
6. `SERVICE_NAME` là logical identity cho lookup giữa module.
7. Orchestration quyết định capability; implementation nằm dưới capability owner.
8. Build-time implementation thuộc `gradle-runtime`.
9. Shared Spring Boot runtime implementation thuộc `springboot-runtime`.
10. Runtime không phụ thuộc ngược Gradle implementation.
11. Generated projections không phải canonical source of truth.
12. Generator phải deterministic/idempotent trong phạm vi contract của nó.
13. Human-owned content không bị overwrite nếu không có explicit contract.
14. External/destructive side effect phải nằm trong explicit task/action.
15. `application.yml` của application là runtime configuration chính; `application-merged.yml` chỉ là generated reference.
16. Swagger build-time generation và Swagger runtime consumption là hai boundary riêng.
17. `APPLICATION` là legacy `MODULE_TYPE`; sync migrate nó thành `SERVLET` và canonical model chỉ dùng `SERVLET`, `REACTIVE`, `LIBRARY`, `PLATFORM`.
18. `SERVLET` mặc định dùng Spring MVC starter; `REACTIVE` mặc định dùng Spring WebFlux starter.
19. Swagger runtime core phải stack-neutral; MVC/WebFlux-specific code và UI starter thuộc adapter tương ứng.
20. `BUILD_SWAGGER=TRUE` chọn đúng một Java runtime adapter theo `MODULE_TYPE`, trong khi YML composition dùng chung `GLOBAL_SWAGGER_CONFIG`.

---

## 31. Khi thêm build capability mới

Một build capability mới nên trả lời lần lượt:

1. Scope là Settings, Root hay Module?
2. Nó được enable bằng metadata hay physical input?
3. Plugin entry point là gì?
4. Implementation logic nằm ở service nào?
5. Có explicit task không?
6. Input nào là source of truth?
7. Output nào generated?
8. File ownership là human/generated/mixed?
9. Generation có deterministic/idempotent không?
10. Capability được nối vào orchestration ở phase nào?
11. Có cần extension/task DSL không?
12. Có cần đăng ký vào generated plugin registry không?

---

## 32. Khi thêm shared runtime capability mới

Một capability mới trong `springboot-runtime` nên xác định:

1. Runtime purpose.
2. Consumer application nào có thể dùng.
3. Activation mechanism.
4. Auto-configuration hay explicit configuration.
5. Public properties/configuration surface.
6. Runtime dependencies.
7. Default behavior.
8. Opt-out behavior.
9. Classpath resources cần consume.
10. Build-time handoff nếu capability cần generated metadata.
11. Package phải tránh accidental component scan nếu capability dựa trên auto-configuration.
12. Không được đưa Gradle implementation vào runtime artifact.

Swagger runtime hiện là reference implementation rõ nhất cho boundary này.

---

## 33. Phạm vi của `module/`

Trong architecture document này, `module/` chỉ được xem ở các khía cạnh:

- module discovery;
- module type;
- metadata;
- dependency contract;
- build/runtime capability consumption;
- file ownership;
- generated artifacts.

Không có kết luận nào trong tài liệu này về:

- package architecture bên trong module;
- controller/service/repository design của module;
- domain model;
- business behavior;
- mức độ hoàn chỉnh của từng learning topic.

Các nội dung đó cần được khảo sát riêng khi có yêu cầu.

---

## 34. Cách đọc repository

Khi cần hiểu architecture của project, thứ tự đọc được khuyến nghị:

```text
1. README.md
2. ARCHITECTURE.md
3. settings.gradle
4. build.gradle
5. project-orchestration
6. project-build/gradle-runtime
7. project-build/springboot-runtime
8. STRUCTURE.md
9. metadata/build.gradle của module cần khảo sát
10. source package của module chỉ khi phạm vi công việc yêu cầu
```

Thứ tự này giúp phân biệt rõ:

```text
repository convention
        ↓
build policy
        ↓
build implementation
        ↓
shared runtime
        ↓
module-specific implementation
```

---

## 35. Tóm tắt kiến trúc

Repository hiện không chỉ là một Gradle multi-module project thông thường.

Nó có một build platform nhỏ bên trong chính repository:

```text
Filesystem + module metadata
        ↓
Settings orchestration
        ↓
Metadata synchronization/injection
        ↓
Root + Module orchestration
        ↓
Gradle runtime capabilities
        ↓
Generated build/documentation artifacts
        ↓
Application artifacts
        ↓
Shared Spring Boot runtime capabilities
```

Vai trò của ba phần infrastructure chính có thể tóm gọn:

```text
project-orchestration
    = policy: cái gì được chạy và khi nào

project-build/gradle-runtime
    = build implementation: chạy như thế nào trong Gradle

project-build/springboot-runtime
    = runtime implementation: ứng dụng dùng capability như thế nào khi chạy
```

`module/` đứng phía consumer của kiến trúc này và vẫn giữ quyền sở hữu nội dung học tập/application cụ thể của từng module.

---

## 36. Evidence boundary

Bản tài liệu này được xây dựng từ source thực tế của:

- root `settings.gradle`;
- root `build.gradle`;
- root README/structure contract;
- `project-orchestration`;
- `project-build/gradle-runtime`;
- `project-build/springboot-runtime/swagger`;
- canonical automation/task resources;
- generated plugin/catalog flow đã được kiểm tra;
- Gradle configuration flow đã được chạy bằng repository Gradle Wrapper 8.5.

Source package bên trong `module/` **không được dùng làm evidence để viết tài liệu này**.

Do đó tài liệu chỉ mô tả module ở mức repository/build/runtime contract, đúng với phạm vi đã xác định.
