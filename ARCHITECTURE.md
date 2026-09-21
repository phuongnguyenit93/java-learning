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
- React 19 + TypeScript + Vite + React Router cho `project-portal` frontend.

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
├── project-portal/                 # Repository-level Java Learning portal
├── project-build/
│   ├── gradle-runtime/             # Build-time implementation
│   └── springboot-runtime/         # Shared Spring Boot runtime capability
├── project-orchestration/          # Build composition/policy
├── build.gradle                    # Root project conventions
├── settings.gradle                 # Composite build entry point
├── README.md
├── ARCHITECTURE.md
├── PROJECT_PORTAL.md               # Portal-specific architecture/design
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

### 3.1 `project-portal` boundary

`project-portal/` là application ở cấp repository dùng để trình bày learning experience chung. Nó được đặt ngang hàng với `module/`, `project-build/`, `project-orchestration/` vì trách nhiệm của nó khác với một learning module:

```text
module/
→ nội dung/chủ đề được học

project-portal/
→ nơi người dùng khám phá, đọc và tương tác với nội dung học
```

Portal hiện có cả Spring Boot và React để phục vụ mục tiêu học full-stack, nhưng frontend được thiết kế **static-first** và chưa phụ thuộc backend API.

Current physical structure:

```text
project-portal/
├── build.gradle
├── gradle.properties
├── master.json
├── src/main/java/com/example/projectportal/
│   └── ProjectPortalApplication.java
├── src/main/resources/
│   └── application.yml             # port 9098
├── build/generated/portal-data/
│   ├── module-catalog.json          # generated global Portal catalog
│   └── module/{ROUTE_ID}/
│       ├── overview/{lang}.md
│       ├── knowledge/{lang}/...
│       └── api/{lang}/              # copied complete Swagger metadata set
└── frontend/
    ├── package.json
    ├── vite.config.ts
    └── src/
        ├── main.tsx
        ├── App.tsx
        ├── components/
        ├── pages/
        ├── data/
        ├── state/
        └── styles/
```

Current build/serve flow:

```text
module/ filesystem + module metadata
        ↓
Portal build-time generators
        ↓
build/generated/portal-data
├── module-catalog.json
└── module/{ROUTE_ID}/{feature}/...
        ↓ Vite static input
React + TypeScript source
project-portal/frontend/src
        ↓
npm run build
        ↓
Vite output: frontend/dist
        ↓
Gradle processResources
        ↓
build/resources/main/static
        ↓
Spring Boot classpath:/static
        ↓
Embedded Tomcat :9098
        ↓
Browser
```

Điểm quan trọng: Spring Boot **không render React** và `ProjectPortalApplication` không cần controller để trả trang chính. Spring Boot tự serve `classpath:/static/index.html`; browser tải JavaScript bundle và chính browser mới chạy React.

Frontend entry point hiện là:

```text
index.html
    ↓
main.tsx
    ↓
App.tsx
    ↓
React Router
    ↓
pages/components
```

Routing production hiện dùng `HashRouter`:

```text
http://localhost:9098/#/
http://localhost:9098/#/learning
http://localhost:9098/#/learning/THREAD
```

Phần sau `#` được browser/React xử lý và không được gửi lên Spring Boot. Nhờ vậy phase hiện tại chưa cần SPA fallback controller. Nếu sau này chuyển sang `BrowserRouter`, backend/static server phải có fallback về `index.html` cho client routes.

Current UI scope:

```text
Header
├── Java Learning
├── Trang chủ
├── Learning
├── global search UI
├── VI ↔ EN switch
└── Light ↔ Dark theme toggle

Learning page
├── generated module hierarchy sidebar
│   ├── module search
│   └── Full tree ↔ Real modules switch
├── "Tìm kiến thức" search
├── Overview
├── Menu
├── Knowledge
├── Quiz
├── API Docs
├── Execution
└── Download action
```

Module hierarchy/routing hiện lấy từ generated `module-catalog.json`. `Overview` consume build-time projection từ language `BASE.md`. `Menu` và `Knowledge` consume Knowledge build-time projection theo module/language/category/section contract; section Markdown chỉ được fetch khi user mở section. Build-time API projection cũng đã có: module có đủ bốn Swagger YAML chuẩn cho một language sẽ được copy nguyên vẹn vào `module/{ROUTE_ID}/api/{lang}/` và catalog expose base path tương ứng. `API Docs` frontend hiện đã consume projection này trực tiếp trong browser; `Quiz` vẫn còn fake. Knowledge count và API count ở sidebar được preload từ generated/static projection theo active language, còn Quiz count tiếp tục derive từ fixture cho tới khi có real Quiz contract. `Execution` và những menu cần backend/runtime thật có thể giữ empty state trong phase đầu. CSS dùng stylesheet riêng; inline CSS không phải convention của Portal.

Current sidebar interaction contract:

```text
node row có children
→ click toàn row để expand/collapse
→ `+` = đóng, `−` = mở
→ default toàn tree là collapsed

real module
→ có nút tròn `>` ở bên phải
→ chỉ nút này navigate tới #/learning/{routeId}

module search: self-match
→ giữ toàn bộ subtree của node match

module search: descendant-match
→ chỉ giữ ancestor path cần thiết tới kết quả

`Real modules / Module thật` mode
→ đây là content-availability filter của Portal, không thay đổi definition repository-level của “real module” theo `gradle.properties`
→ giữ MODULE nếu `Knowledge > 0 OR Quiz > 0 OR API Docs > 0`
→ chỉ ẩn MODULE khi cả ba count đều bằng 0
→ giữ các GROUP ancestor cần thiết
→ prune branch không còn descendant hợp lệ
→ không flatten hierarchy

global tree controls
→ Expand all / Collapse all áp dụng trên projected tree hiện tại
```

Portal giữ state UI gần nhất theo module/language trong browser session khi user đổi tab rồi quay lại. Menu/Knowledge/API Docs được lazy-mount rồi giữ mounted; chúng không bị remount chỉ vì tab khác đang active. Knowledge hỗ trợ nhiều section mở đồng thời; mở section mới không đóng section đang mở trước đó. Đổi module/language tạo state boundary mới.

`API Docs` hiện là **reference-only static documentation**. Nó không chạy/debug API trực tiếp trong Portal. Dữ liệu hiển thị được parse từ bốn Swagger YAML generated; rich `execution` HTML được sanitize trước khi render. Controller order bám theo numeric chapter order từ `controller-description.yml -> readmeRelated.file`; method order bám theo resolved README anchor position. Điều này giữ API reference theo learning path thay vì sort theo controller name/path.

Download UI hiện dùng một shared popover contract. Popup phải anchor ngay dưới action đã mở nó; click bên ngoài, nhấn `Escape`, hoặc bấm lại chính action sẽ đóng popup. API Reference notice có thể reuse cùng Download action nhưng không được tự biến API Docs thành live execution surface.

Theme dùng `prefers-color-scheme` cho lần đầu và lưu lựa chọn user vào `localStorage`. Theme surface/text thay đổi theo Light/Dark, còn semantic capability colors giữ cố định giữa hai theme: Overview gray, Knowledge blue, Quiz amber, API Docs red, Execution purple, Download green.

Portal không được trở thành source of truth mới cho module metadata hoặc learning content. Target data flow vẫn là:

```text
canonical module structure/model
        +
module master.json / resources
        +
actual README/quiz/API/artifact state
        ↓
build-time generated portal projection
        ↓
React Portal
```

Spring Boot backend trong `project-portal` chỉ nên nhận responsibility khi feature thực sự cần server-side behavior, ví dụ runtime aggregation, secured external integration, server-side persistence hoặc dynamic operations. Dữ liệu static/generated không nên bị ép thành REST API chỉ vì backend tồn tại.

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
Execution Context setup [conditional]
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
| Execution Context | `BUILD_EXECUTION_CONTEXT=TRUE` |
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
MODULE_LANGUAGE
JAVA_BASE_PACKAGE
SERVICE_NAME
SERVICE_NAME_DESCRIBE
IS_MODULE_DEPEND
BUILD_ENV
BUILD_YML
BUILD_README
BUILD_TESTER
BUILD_SWAGGER
BUILD_EXECUTION_CONTEXT
BUILD_DATABASE_MODULE
ADD_MODULE_DEPEND
USE_DATABASE
USE_TASK
```

`MODULE_LANGUAGE` là metadata cấp module dùng chung cho mọi capability cần localization. Canonical type là `list`, default hiện tại là `[vi, en]`. README, Swagger build-time, Knowledge metadata sync, Portal projection và runtime Swagger không sở hữu language list riêng; chúng consume `MODULE_LANGUAGE`. Hai key cũ `README_LANGUAGE` và `BUILD_SWAGGER_LANGUAGE_LIST` đã được loại bỏ khỏi `properties.json`.

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
- Execution Context build-time source-context setup;
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

README language structure và `generateFinalReadme` lấy danh sách language trực tiếp từ `MODULE_LANGUAGE`; module `build.gradle` không còn khai báo `generateFinalReadme { languages = ... }`.

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
- expose Swagger generation task.

`generateApiSwaggerDescription` lấy language từ `MODULE_LANGUAGE`; module `build.gradle` không còn sở hữu extension `generateApiSwaggerDescription { languages = ... }`. Cùng contract này được dùng để filter/publish Swagger API projection cho Portal.

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

Ngoài `api-descriptions.yml`, Swagger build-time hiện còn quản lý metadata execution riêng tại:

```text
swagger/<language>/api-execution.yml
```

File này có mixed ownership:

```text
Controller
    ↓
methodSignature
    ↓
execution  → human-owned HTML
usage      → generated-owned
```

`methodSignature` là identity chính của method thay vì chỉ dùng `methodName`, để overloaded method không collision.

Generator chỉ initialize `execution` khi field chưa tồn tại. Sau khi developer đã viết nội dung, regeneration phải preserve nguyên giá trị đó, kể cả khi entry trở thành historical/stale với `usage=false`. `usage` vẫn do generator đồng bộ theo source hiện tại.

### 20.1 Vai trò học tập của `execution`

`execution` không được xem là bản diễn giải tuần tự của source code. Nó là **guided execution explanation** gắn trực tiếp với API để người học có thể hiểu phần lớn experiment ngay trong Swagger mà chưa cần mở Java source.

Ba lớp metadata có trách nhiệm khác nhau:

```text
summary
→ API/chủ đề này là gì?

description
→ API này dùng để minh họa hoặc chứng minh điều gì?

execution
→ experiment thực sự chạy như thế nào,
  tại sao từng bước quan trọng,
  bằng chứng nào trong code/runtime/response xác nhận behavior,
  và người học cần rút ra kết luận gì?
```

Với learning API không trivial, một `execution` tốt nên bao phủ các lớp sau khi phù hợp:

```text
Concept
→ experiment muốn chứng minh điều gì

Flow
→ request/call đi qua những thành phần nào và theo thứ tự nào

Meaning
→ ý nghĩa của các bước quan trọng, không chỉ tên method nào được gọi

Code evidence
→ chi tiết implementation cụ thể làm phát sinh behavior,
  được đặt ngay tại execution step mà đoạn code đó chứng minh

Observation
→ response field, trace event, thread state, ordering, exception, result... nào là bằng chứng

Conclusion
→ kiến thức cần ghi nhớ sau khi chạy experiment
```

Ví dụ conceptual cho một `@Around` Advice bỏ qua target:

```text
caller
  ↓
Spring AOP proxy
  ↓
@Around advice
  ↓
không gọi proceed()
  ↓
target không chạy
  ↓
advice tự trả kết quả
```

Tài liệu `execution` cần giải thích vì sao việc không xuất hiện target trace là bằng chứng target chưa được thực thi và vì sao điều đó chứng minh quyền điều khiển của `@Around`, thay vì chỉ liệt kê tên controller/service/aspect đã chạy.

Source-level diễn giải vẫn là một phần quan trọng của `execution`, nhưng nó đóng vai trò **technical evidence cho concept**, không phải điều kiện tiên quyết để người đọc hiểu nội dung.

### 20.2 Colocate code evidence với execution step

Code snippet không nên được gom thành một khối source lớn ở cuối `execution` nếu có thể gắn trực tiếp nó với bước runtime tương ứng. Cấu trúc ưu tiên là:

```text
<li>
  bước này đang xảy ra điều gì
  ↓
  tại sao bước này quan trọng
  ↓
  đoạn code ngắn trực tiếp tạo ra/chứng minh behavior
  ↓
  response/trace/state nào cần quan sát nếu phù hợp
</li>
```

Ví dụ với một `@Around` Advice, step giải thích việc Advice không gọi `proceed()` nên đặt ngay snippet chứa phần return trực tiếp trong chính `<li>` đó. Step tiếp theo có thể đặt snippet của target method để cho thấy target sẽ ghi một trace marker nếu thật sự được chạy; việc marker này không xuất hiện trở thành evidence cho conclusion.

Không bắt buộc mọi `<li>` đều có code. Snippet chỉ nên xuất hiện khi source thực sự giúp người học hiểu hoặc kiểm chứng step đó. Nếu một đoạn code đã xuất hiện ở step gần trước và không có thêm ý nghĩa học tập, không duplicate chỉ để làm cho mọi step có cùng hình thức.

Snippet phải nhỏ và tập trung vào evidence cần thiết. Tránh copy nguyên controller/service/aspect/helper hoặc nguyên method dài nếu chỉ vài dòng quyết định behavior. Mục tiêu là giữ mối liên hệ trực tiếp:

```text
concept/meaning
↕
small source evidence
↕
observable runtime result
```

Khi cần chỉ rõ nguồn, ưu tiên class/file + method name thay vì line number vì line number dễ stale sau refactor.

Do `execution` được render dưới dạng raw HTML, code trong `<pre><code>...</code></pre>` phải escape các ký tự có thể bị browser hiểu là HTML, ví dụ `<` → `&lt;`, `>` → `&gt;`, và `&` → `&amp;` khi cần.

HTML nên đủ ngắn để đọc ngay trong Swagger, thường dùng `<p>` và `<ol><li>...</li></ol>`; nội dung dài nên lưu bằng YAML block scalar để an toàn với dấu câu và multiline HTML.

### 20.3 Liên kết Swagger với README learning path

Swagger cần có quan hệ rõ ràng với README để người học biết một controller/method đang thuộc chapter/section kiến thức nào và để thứ tự hiển thị bám theo learning path thay vì phụ thuộc vào tên tag/path.

Thiết kế dùng mô hình **manual relationship + generated derivation**:

```text
human
→ khai báo controller thuộc README file nào
→ khai báo method thuộc README anchor nào

generator
→ validate mapping
→ derive chapter number/title
→ derive section title/position
→ derive sorting/status/navigation metadata
```

Không auto-match controller với README dựa trên tên. Controller name và README chapter name không bắt buộc giống nhau.

#### Controller mapping

`controller-description.yml` sở hữu human-owned mapping:

```yaml
ProxyMentalModelController:
  description: |-
    ...
  readmeRelated:
    file: 3.Proxy/Proxy.md
```

`file` là path tương đối từ:

```text
readme/<language>/menu/
```

Folder chứa file phải có numeric chapter prefix:

```text
3.Proxy/Proxy.md
→ chapterOrder = 3

11.ProxyFactory/ProxyFactory.md
→ chapterOrder = 11
```

Prefix phải parse thành integer, không sort như string. Folder không có numeric prefix hợp lệ làm mapping trở thành invalid.

Chapter title được derive từ Markdown H1 đầu tiên của file. Controller UI hiển thị hai dòng:

```text
Chapter 03 · Spring AOP Proxy Mental Model
Proxy Mental Model Controller
```

Controller sorting:

```text
valid README mapping
→ sort theo chapterOrder

nhiều controller cùng chapter
→ sort alphabetically

không có mapping
→ cuối danh sách
→ sort alphabetically
→ VI: Chưa có tài liệu tương ứng trong README
→ EN: No related documentation in README yet
```

Mapping đã cấu hình nhưng file không tồn tại hoặc chapter prefix không hợp lệ không được coi là “unlinked”; đó là invalid mapping. Generator log warning nhưng vẫn tiếp tục generate Swagger.

#### Method mapping

Method relationship nằm trong `api-descriptions.yml` dưới exact method-signature key:

```yaml
ProxyMentalModelController:
  inspectProxy():
    summary: ...
    description: ...
    readmeRelated:
      anchor: proxy-demo
    usage: true
```

Method mặc định inherit README file từ controller. Khi knowledge của method thuộc chapter/file khác, method được phép override:

```yaml
readmeRelated:
  file: 11.ProxyFactory/ProxyFactory.md
  anchor: proxy-factory-demo
```

Method-to-anchor binding là manual. Generator chỉ exact-match configured anchor với markup README dạng:

```html
<a id="proxy-demo">
```

Không dùng fuzzy search, semantic matching hoặc suy luận từ method name để tự chọn section.

Khi anchor hợp lệ, generator derive:

```text
resolved README file
resolved chapter number/title
section heading chứa anchor
anchor position trong file
```

Anchor position là source cho method order trong controller. Nhiều method được phép map cùng anchor; các method đó sort alphabetical. Method không có mapping hoặc không có valid resolved section nằm sau các method linked và sort alphabetical.

Fallback cho method chưa mapping:

```text
VI: Method này chưa có nội dung README
EN: This method does not have README content yet
```

Configured mapping invalid phải được phân biệt với missing mapping. Resolver có thể biểu diễn trạng thái semantic tương đương:

```text
LINKED
UNLINKED
INVALID_FILE
INVALID_ANCHOR
```

`INVALID_FILE` / `INVALID_ANCHOR` chỉ warning và không fail Swagger generation.

#### Language, ownership và navigation

README relationship resolve độc lập theo từng language. VI thiếu file/anchor không được fallback sang EN và ngược lại; language bị lỗi chỉ nhận warning/status của chính nó.

`readmeRelated.file` và `readmeRelated.anchor` là human-owned metadata. Generator tạo sẵn blank field cho entry mới để feature dễ discover, sau đó preserve nguyên mapping qua regeneration, kể cả historical/stale entry có `usage=false`.

Resolved method UI hiển thị chapter + section để người học vẫn biết context khi method override sang chapter khác:

```text
README · Chapter 03 · 2. Demo trong module
```

Dòng này là navigation action. Click sẽ dùng cơ chế `swagger-readme-config.js` hiện có để mở đúng README file + anchor ngay trong Swagger cùng tab. Không embed toàn bộ README vào operation panel và không mở tab mới mặc định.

Contract này hiện đã được implementation ở cả build-time generator/validator và shared Swagger runtime/UI: generator tạo/preserve mapping, runtime resolve metadata theo language, OpenAPI được sort theo chapter/anchor, và Swagger UI hiển thị/navigation tới README cùng tab.

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

Execution Context runtime hiện có hai module:

```text
springboot-runtime/execution-context
springboot-runtime/execution-context-servlet
```

Trong đó core là stack-neutral và Servlet adapter sở hữu Spring MVC/Servlet integration. Reactive adapter là target tương lai, chưa phải implementation hiện tại.

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

## 22A. Execution Context và kiến trúc tích hợp chat AI

Execution Context là một runtime/build capability độc lập với Swagger.

Mục tiêu của nó là tạo một `ExperimentContext` trung lập mô tả **execution thực tế vừa xảy ra**, thay vì buộc công cụ AI chỉ suy luận từ source tĩnh.

Boundary hiện tại:

```text
project-build/gradle-runtime
        │
        └── generate source context at build time
                    ↓
       META-INF/execution-context/source-context.json
                    ↓
project-build/springboot-runtime/execution-context
        │
        └── model / store / service / source lookup
                    ↑
project-build/springboot-runtime/execution-context-servlet
        │
        └── request / response / handler / logs / stdout-stderr capture
```

Enablement của Servlet application:

```text
BUILD_EXECUTION_CONTEXT=TRUE
        ↓
GLOBAL_EXECUTION_CONTEXT_SERVLET
        ↓
GLOBAL_EXECUTION_CONTEXT
```

Swagger không nằm trong dependency chain này. Swagger UI có thể là nơi user bấm Execute, nhưng HTTP request sau đó được Execution Context capture như bất kỳ request bình thường nào.

### 22A.1 Roadmap 7 phase

Bốn phase đầu tạo **Execution Context Core + Query Layer** và không phụ thuộc chat AI:

```text
Phase 1: Execution Capture
→ xác định API/controller method đã chạy
→ request / response
→ status / duration / exception
→ executionId correlation

Phase 2: Runtime Observation
→ logs theo executionId
→ thread information
→ System.out / System.err khi bật
→ async/virtual-thread observation khi context propagation hỗ trợ

Phase 3: Source Context
→ Controller method source
→ related Service source khi resolve được
→ Javadoc / README / annotation / config context khi có
→ source index được generate ở build time và lookup ở runtime

Phase 4: Context Query Service
→ query latest/by-id/recent/time range và filterable execution history
→ `ExecutionSummary` cho list/search
→ `ExperimentContext` cho detail/export/AI

Sau Phase 4 mới tách Manual Mode và Connected Mode.

Với repository hiện tại, Connected Mode **không tiếp tục bằng cách tự xây MCP/tunnel/chat backend trong Spring Boot**. Phase 5–7 được delegate cho Chat On Steroids (CoS):

Phase 5: AI Tool Bridge
→ CoS Core cung cấp MCP/tool surface cho ChatGPT
→ AI dùng workspace/file tools để đọc source và terminal/local HTTP để query Execution Context REST API
→ application không cần tự expose MCP tool riêng cho current path

Phase 6: Reachability / transport
→ CoS quản lý MCP tunnel/transport khi ChatGPT cần reach local machine
→ application không sở hữu tunnel credential/lifecycle/provider-specific configuration

Phase 7: ChatGPT usage through CoS
→ ChatGPT web/app của user tiếp tục host model và conversation
→ CoS nối conversation đó với local tools
→ ChatGPT lấy Execution Context từ REST/query layer hiện có và correlate với project source
```

Connected Mode ưu tiên của repository trở thành:

```text
ChatGPT web/app
        ↓
Chat On Steroids Core
(MCP/tool bridge)
        ├── workspace/file access
        └── terminal / local HTTP
                    ↓
        Execution Context REST API
                    ↓
        Phase 4 Context Query Service
                    ↓
              ExperimentContext
```

Boundary ownership:

```text
Application          → capture/query/serve structured Execution Context
Chat On Steroids     → MCP tools + local workspace/terminal + tunnel/transport
ChatGPT              → reasoning + conversation
```

Luồng này không cần OpenAI API key trong application và không cần embedded Swagger chat. Nếu sau này repository cần chạy độc lập không phụ thuộc CoS, có thể bổ sung application-owned MCP adapter/tunnel như một adapter thay thế; adapter đó vẫn phải consume Phase 4 contract hiện có.

Embedded Swagger AI chat vẫn có thể được nghiên cứu sau, nhưng là optional UX/product mode. Gọi OpenAI API trực tiếp sẽ có credential/billing riêng; reuse ChatGPT browser conversation sẽ cần browser/companion bridge riêng. Cả hai đều không phải requirement của Phase 5–7 hiện tại.

Status hiện tại:

| Phase | Trạng thái hiện tại |
| --- | --- |
| 1. Execution Capture | Implemented cho Servlet baseline |
| 2. Runtime Observation | Implemented cho Servlet baseline |
| 3. Source Context | Implemented bằng build-time generation + runtime lookup |
| 4. Context Query Service | Implemented: `ExecutionQuery`, `ExecutionSummary`, REST query/detail, Explorer, ZIP export |
| 5. AI Tool Bridge | Delegated to Chat On Steroids Core khi CoS được cấu hình |
| 6. Reachability / transport | Delegated to CoS tunnel/transport khi cần |
| 7. ChatGPT usage | Delegated to ChatGPT + CoS connected session |

Phase 5–7 không phải Spring Boot runtime module của repository trong current architecture; chúng là external integration capabilities.

### 22A.2 `ExperimentContext` là contract dùng chung

Conceptual model:

```text
ExperimentContext
├── execution
│   ├── executionId
│   ├── handler/controller/method
│   ├── request
│   ├── response
│   ├── logs
│   ├── exception
│   └── duration
└── sourceContext
    ├── controller source
    ├── documentation
    └── related source
```

Source index dùng method signature identity để không collision khi controller overload method:

```text
ControllerFqcn#method(TypeA,TypeB)
```

Phase 3 output hiện được package vào application artifact dưới:

```text
META-INF/execution-context/source-context.json
```

Điều này cho phép runtime lookup source context khi chạy bằng `bootRun`, Docker hoặc executable JAR mà không cần source tree nằm cạnh process.

### 22A.3 Hai hướng user có thể dùng với công cụ chat AI

Sau Phase 1–4, user có **hai hướng độc lập**. Đây là product/architecture requirement, không chỉ là lựa chọn UI.

#### Hướng A — Manual Mode

User muốn AI hỗ trợ nhưng không muốn MCP/tunnel:

Đây là branch sau Phase 4, không phải một phase đánh số riêng trong roadmap 7 phase. Servlet adapter hiện đã có REST JSON detail và ZIP exporter.

```text
Execute API/application
        ↓
Execution Context Core
        ↓
Phase 4: Context Query Service
        ↓
AI Context Exporter
        ↓
REST JSON / ZIP bundle
        ↓
upload thủ công vào ChatGPT / Claude / Gemini / chat AI khác
```

Manual Mode phải có thể hoạt động mà không cần:

```text
MCP
tunnel
OpenAI API key
ChatGPT-specific runtime dependency
```

Artifact khuyến nghị là **AI Context Bundle** chứa source, request/response, logs, exception, timing và documentation liên quan. Compiled JAR có thể là file bổ sung nếu cần, nhưng không nên là format context chính vì chat AI sẽ phải tự extract/decompile để tìm lại những thông tin repository đã biết sẵn.

#### Hướng B — Connected Mode qua Chat On Steroids

User muốn trải nghiệm tự động mà không phải upload bundle thủ công:

```text
Execute API/application
        ↓
Execution Context Core
        ↓
Phase 4: Context Query Service / REST
        ↓
Phase 5: CoS Core tool bridge
        ↓
Phase 6: CoS tunnel/transport khi cần
        ↓
Phase 7: ChatGPT + CoS connected session
        ↓
"phân tích lần execution vừa chạy"
```

ChatGPT query context thông qua tool surface của CoS. CoS có thể gọi local REST endpoint, đọc source trong workspace và đưa evidence trở lại cùng conversation. Application không cần triển khai MCP server riêng hoặc gọi OpenAI API cho workflow này.

Hai hướng dùng **cùng một `ExperimentContext`**:

```text
                         Execution Context Core
                                  │
                         ExperimentContext
                                  │
                       Phase 4 Query Service
                                  │
                  ┌───────────────┴───────────────┐
                  │                               │
                  ▼                               ▼
          Manual AI Exporter             Chat On Steroids Core
                  │                       (external adapter)
          REST JSON / ZIP                        │
                  │                      workspace + terminal
                  ▼                               │
        Upload to chat AI                        ▼
                                        ChatGPT web/app account
```

Manual exporter và CoS connected path là **consumer/integration layer**, không được sở hữu lại logic capture, log correlation hoặc source scanning.

Swagger tiếp tục tập trung vào run/inspect/export Execution Context. `Connect AI` hoặc floating chat nếu có chỉ là optional UX; không phải dependency hoặc ownership boundary của current Connected Mode.

Phase 4 hiện có thêm **Execution Context Explorer** độc lập với Swagger. Explorer dùng `ExecutionSummary` cho table/search, multi-select execution để bulk export, và chỉ load `ExperimentContext` khi xem detail hoặc export.

Swagger chỉ giữ quick action theo từng operation:

```text
[ Execute ]
[ View last execution ]
[ Export this execution ]
```

History, search/filter, multi-select và bulk export thuộc Execution Context Explorer.

### 22A.4 Phase 4 implementation hiện tại

Phase 4 được chia thành **neutral query contract trong core** và **Servlet/Web adapter**. Query contract không phụ thuộc Swagger UI hay chat vendor.

Core hiện có:

```text
ExecutionQuery
ExecutionSummary
ExecutionQueryService
DefaultExecutionQueryService
```

`ExecutionQuery` hỗ trợ các tiêu chí hiện tại:

```text
limit
from / to
search
httpMethod
path
controller
method
status
failed
minDuration / maxDuration
```

`ExecutionSummary` cố ý chỉ chứa dữ liệu nhẹ phục vụ list/search như execution id, thời gian, HTTP method/path, handler, status, duration, failed flag và log count. `ExperimentContext` đầy đủ chỉ được resolve khi cần xem detail, export hoặc cấp context cho AI consumer.

Servlet adapter expose query/detail API:

```text
GET /execution-context/api/executions
GET /execution-context/api/executions/latest
GET /execution-context/api/executions/{executionId}
```

Export API hiện có:

```text
GET  /execution-context/api/executions/{executionId}/export
POST /execution-context/api/export
```

`POST /execution-context/api/export` hỗ trợ export theo explicit `executionIds` hoặc theo `ExecutionQuery`, nhờ đó Explorer có thể bulk export nhiều execution mà không cần duplicate filtering logic ở frontend.

ZIP bundle hiện có structure chính:

```text
manifest.json
executions/<executionId>/context.json
executions/<executionId>/logs.txt
executions/<executionId>/source/controller-method.java
executions/<executionId>/source/documentation.txt
executions/<executionId>/source/related/*.java
```

`Execution Context Explorer` được serve tại:

```text
/execution-context/explorer.html
```

Explorer sở hữu history/search/time range, detail view, multi-select và bulk export. Đây là UI chính cho việc điều tra execution history; Swagger không được biến thành execution-history dashboard.

Swagger integration chỉ là một adapter tiện ích phía UI. Khi Execution Context runtime có mặt, `DynamicSwaggerRegistrar` đánh dấu operation bằng:

```text
x-execution-context-enabled=true
```

Custom Swagger plugin wrap component `execute` của Swagger UI và bổ sung hai action cạnh nút Execute:

```text
[ Execute ] [ View last execution ] [ Export this execution ]
```

`View last execution` query execution gần nhất bằng `path + httpMethod` rồi mở Explorer theo `executionId`. `Export this execution` dùng cùng query contract để tải ZIP của execution tương ứng. Nếu Swagger đã có response, nút native `Clear` vẫn do Swagger sở hữu và có thể xuất hiện cùng hàng.

Các endpoint `/execution-context/**` bị loại khỏi OpenAPI group để tránh Execution Context tự xuất hiện như learning/business API trong Swagger.

Phase 4 hiện vẫn query trên store runtime hiện tại; persistence ngoài process chưa phải current capability. Vì vậy restart application sẽ mất execution history đang nằm trong in-memory store.

### 22A.5 Boundary với Swagger

Relationship đúng:

```text
Swagger UI ───────┐
                  │ normal HTTP request
                  ▼
        Execution Context Core
                  │
        ┌─────────┴─────────┐
        ▼                   ▼
   AI Exporter         MCP / Query Adapter
```

Không phải:

```text
Execution Context
        ↓
Swagger
        ↓
MCP / AI
```

Nhờ boundary này, Execution Context vẫn reusable cho application không dùng Swagger và có thể phát triển thành library độc lập.

### 22A.6 Giới hạn propagation hiện tại

Servlet baseline hiện correlation tốt với request thread và thread mới kế thừa context. Existing executor pools, một số `@Async`/`CompletableFuture` flow hoặc reactive context cần propagation adapter riêng.

Không được coi `InheritableThreadLocal` là lời giải tổng quát cho mọi concurrency model. Khi mở rộng Phase 2, propagation phải được thiết kế theo execution model tương ứng.

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
| Execution Context enablement | `BUILD_EXECUTION_CONTEXT` | orchestration/runtime dependency selection |
| Execution source context | Java source + build-time scanner/generator | `META-INF/execution-context/source-context.json` |
| Portal module hierarchy/catalog | filesystem/module discovery + module metadata via `ProjectStructureService` | `project-portal/build/generated/portal-data/module-catalog.json` |
| Portal module Overview | `module/**/readme/{lang}/BASE.md` | `project-portal/build/generated/portal-data/module/{ROUTE_ID}/overview/{lang}.md` |
| Portal Knowledge | `module/**/readme/{lang}/menu/**/*.md` anchored sections | `project-portal/build/generated/portal-data/module/{ROUTE_ID}/knowledge/{lang}/...` |
| Portal API metadata | complete `module/**/src/main/resources/swagger/{lang}` four-file set | `project-portal/build/generated/portal-data/module/{ROUTE_ID}/api/{lang}/...` + catalog `api` base path |
| Portal current fake learning-content fixtures | `project-portal/frontend/src/data` fake TypeScript fixtures | Quiz frontend only until real Quiz projection is implemented |
| Portal production frontend | React/TypeScript source | Vite `dist` → Spring `classpath:/static` |

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

Riêng `project-portal`, build lifecycle hiện có thêm frontend handoff:

```text
:project-portal:processResources / bootJar / bootRun dependency chain
        ↓
buildFrontend
        ↓
npmInstall
        ↓
npm run build
        ↓
Vite frontend/dist
        ↓
copy vào Spring resources/static
        ↓
Spring Boot serve production bundle
```

Gradle Node plugin download Node/npm cho Portal build, vì vậy production build không dựa vào việc developer đã cài Node global. Khi phát triển frontend riêng, Vite dev server có thể chạy độc lập; khi test application đóng gói hoàn chỉnh, Spring Boot serve bundle đã build.

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

Portal bổ sung thêm một boundary browser-side:

```text
Gradle build process
    ↓ build React
Spring Boot process
    ↓ serve static files / future APIs
Browser process
    ↓ execute React / client routing / local state
```

Không được mô tả React như code chạy trong JVM. Spring Boot chỉ host static bundle ở current phase; React runtime chạy trong browser.

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
21. Execution Context độc lập với Swagger và không được phụ thuộc OpenAPI/springdoc chỉ để capture execution.
22. Phase 1–4 tạo reusable capture/source/query capability; exporter và external AI tooling chỉ consume query contract này, không duplicate capture/source logic.
23. User dùng chat AI có hai hướng sau Phase 4: Manual Mode bằng REST JSON/ZIP upload hoặc Connected Mode qua Chat On Steroids.
24. Current Connected Mode delegate Phase 5–7 cho CoS: Core cung cấp MCP/tool bridge, CoS quản lý tunnel/transport khi cần, ChatGPT host model/conversation.
25. Application không cần application-owned MCP/tunnel/chat backend cho current workflow; standalone MCP integration chỉ bổ sung khi có requirement độc lập khỏi CoS.
26. Embedded Swagger AI chat/browser bridge là optional product UX và không phải requirement của Phase 5–7 hiện tại.
27. Swagger `execution` documentation là guided learning explanation: phải giúp người đọc hiểu concept, flow, ý nghĩa, evidence và conclusion mà không bắt buộc phải mở source code trước.
28. `project-portal` là repository-level presentation application nằm ngoài `module/`; nó không phải learning topic.
29. Portal frontend ưu tiên static/generated data; có Spring Boot backend không đồng nghĩa mọi dữ liệu phải đi qua REST.
30. Production React bundle được Vite build rồi Gradle copy vào `classpath:/static`; Spring Boot phục vụ bundle bằng static-resource mechanism mặc định.
31. Current Portal routing dùng `HashRouter`, vì vậy `#/learning/...` thuộc browser/React và chưa cần Spring MVC SPA fallback.
32. Learning experience không phụ thuộc việc learning module có Spring Boot Application; API execution và Execution Context chỉ là optional runtime capabilities.
33. Portal module hierarchy phải consume generated `module-catalog.json`; browser không parse `module-structure.txt` làm canonical input.
34. Sidebar Portal `Real modules / Module thật` là content filter: giữ MODULE khi Knowledge hoặc Quiz hoặc API Docs có count > 0; chỉ loại module khi cả ba đều 0. Đây không phải định nghĩa physical real module của repository, vốn vẫn dựa trên local `gradle.properties`.
35. Module-search self-match giữ nguyên subtree; descendant-match chỉ giữ ancestor path. Search mode vẫn phải cho phép expand/collapse.
36. Module navigation dùng action riêng (`>`); row có children sở hữu expand/collapse interaction. Tree mặc định collapsed, dùng `+`/`−` và có global Expand all/Collapse all.
37. Menu/API Docs mặc định collapsed nhưng phải preserve state gần nhất khi user đổi tab rồi quay lại trong cùng module/language; Knowledge cho phép nhiều section cùng mở và không auto-close sibling section.
38. API Docs hiện consume static generated Swagger metadata, không live-execute/debug. Controller/method order phải follow valid README relationship order thay vì alphabetical/path order; rich execution HTML phải được sanitize trước khi render.
39. Portal generated data là build artifact: không generate `module-catalog`, Overview, Knowledge hoặc future module projections vào `project-portal/src/main/resources`.
40. Generated Portal layout là module-first: global `module-catalog.json` nằm ở `portal-data/`, còn module-owned feature data nằm dưới `portal-data/module/{ROUTE_ID}/{feature}/...`.

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
8. PROJECT_PORTAL.md + project-portal khi task liên quan Learning Portal
9. STRUCTURE.md
10. metadata/build.gradle của module cần khảo sát
11. source package của module chỉ khi phạm vi công việc yêu cầu
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
        │
        ├── Swagger core + web-stack adapters
        │
        └── Execution Context core + Servlet adapter
```

Vai trò của ba phần infrastructure chính có thể tóm gọn:

```text
project-orchestration
    = policy: cái gì được chạy và khi nào

project-build/gradle-runtime
    = build implementation: chạy như thế nào trong Gradle

project-build/springboot-runtime
    = runtime implementation: ứng dụng dùng capability như thế nào khi chạy

project-portal
    = presentation application: Spring Boot host + React/TypeScript learning UI
```

`module/` đứng phía consumer của kiến trúc này và vẫn giữ quyền sở hữu nội dung học tập/application cụ thể của từng module.

`project-portal` đứng phía presentation/aggregation. Current flow của Portal là:

```text
filesystem/module metadata
        ↓ ProjectStructureService
build/generated/portal-data/module-catalog.json
        ↓
        + module-scoped generated Overview/Knowledge/API metadata projections
        + fake Quiz fixture cho capability chưa migrate
        ↓
React + TypeScript
        ↓ Vite build
static bundle
        ↓ Gradle processResources
Spring Boot classpath:/static
        ↓
browser at :9098
```

Trong phase hiện tại Java backend không sở hữu learning data API. Module catalog đã là generated static projection; README/quiz/OpenAPI projections khi được bổ sung cũng nên tiếp tục đi theo static-first data flow trừ khi feature có requirement server-side rõ ràng.

Execution Context bổ sung một đường dữ liệu khác từ runtime thực tế sang công cụ phân tích:

```text
Application execution
        ↓
Phase 1–4 Execution Context Core + Query Layer
        ↓
ExperimentContext
        ├── Manual Mode → ZIP/JSON → upload vào chat AI
        └── Connected Mode → Context Query/REST → Chat On Steroids → ChatGPT web/app account

Current Phase 5–7 ownership:
Phase 5 tool bridge → CoS Core
Phase 6 tunnel/transport → CoS when needed
Phase 7 conversation/model → ChatGPT connected through CoS

Embedded Swagger AI chat/browser bridge is optional and not required for the current path.
API-backed embedded chat, nếu có, là mode riêng và có provider credential/billing riêng.
```

---

## 36. Evidence boundary

Bản tài liệu này được xây dựng từ source thực tế của:

- root `settings.gradle`;
- root `build.gradle`;
- root README/structure contract;
- `project-orchestration`;
- `project-build/gradle-runtime`;
- `project-build/springboot-runtime/swagger`;
- `project-build/springboot-runtime/execution-context`;
- `project-build/springboot-runtime/execution-context-servlet`;
- `project-portal` Spring Boot + React/Vite build handoff;
- `PROJECT_PORTAL.md` cho product/portal-specific design;
- canonical automation/task resources;
- generated plugin/catalog flow đã được kiểm tra;
- Gradle configuration flow đã được chạy bằng repository Gradle Wrapper 8.5.

Source package bên trong `module/` **không được dùng làm evidence để viết tài liệu này**.

Do đó tài liệu chỉ mô tả module ở mức repository/build/runtime contract, đúng với phạm vi đã xác định.
