# Project Portal / Java Learning Portal

## 1. Trạng thái tài liệu

Tài liệu này mô tả **current implementation + target architecture** của `project-portal`, trang chung cấp repository cho `java-learning`.

`project-portal` đã được tạo ở root repository và hiện có:

```text
Spring Boot
+
React 19
+
TypeScript
+
Vite
+
React Router
+
CSS stylesheet thuần
```

Mục tiêu dài hạn vẫn là tạo một **Learning Portal** độc lập với runtime của từng learning module, để mọi learning module đều có một điểm truy cập chung dù module đó có hay không có Spring Boot `Application`.

Current phase intentionally chỉ dựng frontend static/fake data và navigation. Backend Spring Boot đã tồn tại để học full-stack và làm host production bundle, nhưng Portal chưa gọi REST API nào của chính nó.

### 1.1 Current physical structure

```text
project-portal/
├── build.gradle
├── gradle.properties
├── master.json
├── src/main/java/com/example/projectportal/
│   └── ProjectPortalApplication.java
├── src/main/resources/
│   └── application.yml
└── frontend/
    ├── package.json
    ├── vite.config.ts
    └── src/
        ├── main.tsx
        ├── App.tsx
        ├── pages/
        ├── components/
        ├── data/
        ├── state/
        └── styles/
```

`project-portal/gradle.properties` hiện có `IS_MODULE=TRUE`; đồng thời theo real-module contract hiện tại, chính sự tồn tại của local `gradle.properties` làm repository scanner nhận diện/include project này.

Spring Boot hiện chạy ở:

```text
http://localhost:9098
```

Current production Learning route ví dụ:

```text
http://localhost:9098/#/learning/THREAD
```

### 1.2 Current build and serve model

Portal không dùng Java controller để render React.

Flow hiện tại:

```text
frontend/src/**/*.tsx + CSS
        ↓
npm run build
        ↓
Vite
        ↓
frontend/dist
        ↓
Gradle processResources
        ↓
build/resources/main/static
        ↓
Spring Boot classpath:/static
        ↓
Embedded Tomcat :9098
        ↓
Browser loads index.html + JS/CSS
        ↓
Browser executes React
```

`ProjectPortalApplication` chỉ cần khởi động Spring Boot. Spring Boot tự nhận `classpath:/static/index.html` làm welcome page/static resource. React runtime thực sự chạy trong browser, không chạy trong JVM.

Current frontend entry flow:

```text
index.html
    ↓
main.tsx
    ↓
App.tsx
    ↓
HashRouter
    ↓
pages/components
```

`HashRouter` được chọn cho phase đầu để client route không cần Spring MVC fallback. Phần sau dấu `#` không được gửi lên server.

### 1.3 Vai trò của React / TypeScript / Vite / React Router

Bốn phần này không phải bốn lựa chọn thay thế nhau; chúng phối hợp theo các responsibility khác nhau:

```text
React
→ component/UI model

TypeScript
→ ngôn ngữ dùng để viết frontend với static type checking

Vite
→ dev server + frontend build tool

React Router
→ client-side navigation giữa Home/Learning/module routes
```

Current stack dùng cả bốn:

```text
React + TypeScript + Vite + React Router + CSS stylesheet
```

Styling rule hiện tại: ưu tiên CSS class trong file stylesheet, tránh inline CSS trừ khi có technical reason rõ ràng.

### 1.4 Development và production mode

Hai mode có mục tiêu khác nhau:

```text
Frontend development
→ Vite dev server
→ nhanh, hot reload
→ hiện không cần Spring Boot vì data đang fake/static

Integrated application test
→ Gradle buildFrontend/processResources
→ Spring Boot serve compiled React bundle
→ http://localhost:9098
```

Current Gradle integration dùng Node Gradle plugin để download Node/npm cho build, nên build production không phụ thuộc bắt buộc vào Node global trên máy developer.

Trong IntelliJ có thể test application tích hợp bằng Gradle `:project-portal:bootRun`. Frontend-only development có thể chạy npm/Vite task tương ứng. Khi Portal bắt đầu gọi backend API thật, Vite dev mode có thể bổ sung proxy tới Spring Boot `:9098` thay vì đóng bundle lại sau mỗi thay đổi UI.

---

## 2. Vấn đề hiện tại

Hiện tại Swagger đang vô tình đóng vai trò là điểm tựa để người dùng:

```text
Swagger
├── đọc README
├── thử API
└── xem Execution Context
```

Điều này hoạt động tốt với module runnable, nhưng tạo ra một dependency không lý tưởng:

```text
Muốn đọc README đẹp
        ↓
phải mở được Swagger
        ↓
muốn mở Swagger
        ↓
module phải có Spring Boot Application/runtime
```

Trong repository có nhiều loại module khác nhau:

```text
SERVLET
REACTIVE
LIBRARY
PLATFORM
```

Không phải module nào cũng nên hoặc có thể chạy một Spring Boot application.

Tuy nhiên một module không runnable vẫn có thể có:

- README/knowledge;
- quiz;
- source code;
- build artifact/library artifact;
- metadata mô tả module.

Vì vậy documentation/learning UI không nên phụ thuộc vào Swagger hoặc vào việc module có runtime application hay không.

---

## 3. Ý tưởng chính

Tạo một trang chung ở cấp root/repository:

```text
Java Learning Portal
```

Portal là entry point dành cho người học.

Swagger trở thành một capability tùy chọn của module thay vì là entry point của toàn bộ trải nghiệm học.

Target relationship:

```text
                    Learning Portal
                          │
             ┌────────────┼─────────────┐
             │            │             │
          Knowledge      Quiz        Module info
             │            │             │
             └────────────┴─────────────┘
                          │
                 always available
                          │
                          ├───────────────┐
                          │               │
                          ▼               ▼
                     API Docs         Execution
                      optional         optional
                          │               │
                          └──── module runtime ────
```

Nguyên tắc:

> Spring Boot Application không phải điều kiện để một module trở thành learning module.

Spring runtime chỉ là một capability tùy chọn.

---

## 4. Trang module hiện tại và mục tiêu

Ví dụ với module runnable `THREAD`:

```text
Thread & Concurrency

[Overview] [Knowledge] [Quiz] [API Docs] [Execution]        [Download]

Progress
████████░░

Knowledge
1. Basic Thread
2. Interruption
3. Concurrency Problems
...

Quiz
42 questions

API Docs
78 experiments
```

Ví dụ với module không runnable:

```text
Gradle Cache

[Overview] [Knowledge] [Quiz]                               [Download]

Knowledge
1. Cache Concepts
2. Build Cache
3. Configuration Cache
...

Quiz
25 questions
```

Current phase đã dựng đủ navigation shell trên bằng fake data. `Execution` và `Download` có thể hiển thị empty/placeholder state cho tới khi integration thật được thêm.

Các tab/action về lâu dài phải được render theo capability thực tế của module.

Không nên tạo Spring Boot Application giả chỉ để module có thể hiển thị README hoặc quiz.

---

## 5. Navigation theo module hierarchy

Portal không hard-code các category như một website interview thông thường.

Navigation được sinh từ cùng source/model đang dùng để mô tả module hierarchy của repository.

Ví dụ:

```text
Infrastructure
├── DevOps
│   ├── Docker
│   ├── Git
│   └── K8s
└── System
    ├── Database
    └── Observability

Integration
└── Broker
    └── Kafka

Platform
└── Development
    ├── Framework
    │   └── Spring
    ├── Language
    │   └── Java
    └── Paradigm
        ├── AOP
        └── Concurrency
            └── Thread
```

`module-structure.txt` chỉ nên được xem là một generated human-readable projection.

Không nên để frontend parse trực tiếp file text này.

Target flow:

```text
Canonical module structure/model
        │
        ├── generate → module-structure.txt
        ├── generate → STRUCTURE.md
        └── generate → machine-readable module catalog
```

Portal consume machine-readable catalog.

---

## 6. Giữ `master.json` làm Module Manifest

Không thay thế `master.json` bằng một schema capability mới độc lập.

`master.json` tiếp tục là manifest quan trọng của từng module và chứa các thông tin như:

```text
MODULE_TYPE
SERVICE_NAME
SERVICE_NAME_DESCRIBE
BUILD_README
BUILD_SWAGGER
BUILD_EXECUTION_CONTEXT
...
```

Portal không nên đọc trực tiếp hàng trăm `master.json`.

Thay vào đó build-time generator sẽ kết hợp:

```text
module hierarchy
        +
module master.json
        +
actual module resources/artifacts
        ↓
Module Capability Resolver
        ↓
module-catalog.json
        ↓
Learning Portal
```

Điều này giữ được nguyên tắc:

```text
source of truth
      ↓
generator
      ↓
projection
```

---

## 7. Capability model

`capabilities` là projection dành cho consumer/UI, không phải source of truth mới thay thế `master.json`.

Nên phân biệt:

```text
enabled
→ module khai báo/được cấu hình để hỗ trợ capability

available
→ resource/artifact thực tế đã tồn tại và có thể sử dụng
```

Ví dụ:

```json
{
  "serviceName": "THREAD",
  "moduleType": "SERVLET",
  "capabilities": {
    "knowledge": {
      "enabled": true,
      "available": true
    },
    "quiz": {
      "enabled": true,
      "available": true
    },
    "apiDemo": {
      "enabled": true,
      "available": true
    },
    "execution": {
      "enabled": true,
      "available": true
    }
  }
}
```

Một module non-runnable có thể là:

```json
{
  "serviceName": "GRADLE_CACHE",
  "moduleType": "LIBRARY",
  "capabilities": {
    "knowledge": {
      "enabled": true,
      "available": true
    },
    "quiz": {
      "enabled": true,
      "available": true
    },
    "apiDemo": {
      "enabled": false,
      "available": false
    },
    "execution": {
      "enabled": false,
      "available": false
    }
  }
}
```

Frontend chỉ cần render những capability phù hợp.

---

## 8. Overview

`Overview` là trang giới thiệu module.

Nguồn dữ liệu nên tận dụng metadata hiện có trước khi thêm schema mới.

Ví dụ:

```text
SERVICE_NAME
SERVICE_NAME_DESCRIBE
MODULE_TYPE
module path
available capabilities
```

`SERVICE_NAME_DESCRIBE` có thể trở thành description chính hiển thị trên Portal.

Không nên thêm metadata mới nếu thông tin đã có owner phù hợp trong `master.json` hoặc `properties.json`.

---

## 9. Knowledge / README

Knowledge là capability luôn có thể hoạt động mà không cần module runtime.

Target:

```text
module README/menu Markdown
        ↓
build-time collection/generation
        ↓
static portal assets
        ↓
Markdown renderer
```

Ví dụ generated assets:

```text
project-portal/
└── data/
    └── readme/
        ├── THREAD/
        │   ├── vi/
        │   └── en/
        ├── ASPECT/
        └── GRADLE_CACHE/
```

Learning Portal trở thành nơi đọc README chung của mọi module.

Swagger vẫn có thể giữ README integration hiện tại đối với module runnable, nhưng không còn là nơi duy nhất để đọc knowledge.

---

## 10. Quiz

Quiz là một capability learning độc lập với Swagger.

MVP ưu tiên:

```text
single-choice
4 đáp án
1 đáp án đúng
explanation
next/previous
score
restart
search/filter
```

Dữ liệu quiz nên nằm trong project và được version cùng source code.

Ví dụ:

```text
module/thread/
└── quiz/
    ├── vi/
    │   └── questions.yml
    └── en/
        └── questions.yml
```

Ví dụ schema khái niệm:

```yaml
- id: thread-start-vs-run
  question: "Khác biệt chính giữa start() và run() là gì?"
  answers:
    - "start() gọi run() trên cùng thread"
    - "start() tạo execution trên thread mới"
    - "run() luôn tạo thread mới"
    - "Hai method giống nhau"
  correct: 1
  explanation: >
    start() yêu cầu JVM bắt đầu một thread mới,
    sau đó thread mới thực thi run().
```

Quiz có thể liên kết với knowledge và runtime demo:

```yaml
readmeRelated:
  file: "1.Basic/Basic.md"
  anchor: "start-vs-run"

apiRelated:
  controller: BasicThreadController
  method: startVsRun
```

Learning flow có thể trở thành:

```text
Knowledge
   ↓
README
   ↓
Quiz
   ↓
Experiment/API
   ↓
Execution Context
```

---

## 11. Static-first data architecture

Learning Portal không cần database cho phiên bản đầu.

Knowledge/quiz/module catalog là static knowledge, phù hợp để lưu trong Git.

Target:

```text
Git / YAML / JSON / Markdown
        ↓
Gradle validation + generation
        ↓
Static JSON / static assets
        ↓
Frontend
```

Không cần REST business API để render dữ liệu này.

Frontend có thể load static assets trực tiếp.

Progress cá nhân có thể lưu local:

```text
browser localStorage
```

Ví dụ dữ liệu local:

- câu đã làm;
- đáp án đã chọn;
- điểm;
- bookmark;
- module đang học;
- filter/search state.

Backend/database chỉ cần cân nhắc khi có yêu cầu như:

- login;
- sync progress nhiều thiết bị;
- leaderboard;
- multi-user analytics;
- admin CRUD online;
- persistent AI-generated content.

---

## 12. API Docs / API Execution

Portal tách khái niệm **API documentation** khỏi **API execution**.

Current UI dùng tên:

```text
API Docs
```

và đang render fake pseudo REST operations từ TypeScript data.

Target model:

```text
apiDocumentation
→ có thể tồn tại dưới dạng static OpenAPI/API catalog
→ không bắt buộc module đang chạy

apiExecution
→ chỉ available khi có runtime endpoint phù hợp
```

Ví dụ runnable module:

```text
THREAD
apiDocumentation = true
apiExecution     = true khi runtime available
```

Ví dụ non-runnable learning module vẫn có thể có pseudo/static API documentation để diễn giải experiment contract:

```text
apiDocumentation = true
apiExecution     = false
```

Khi generator thật được triển khai, nên ưu tiên OpenAPI JSON/YAML hoặc một machine-readable projection tương thích OpenAPI thay vì invent proprietary API schema không cần thiết.

Điều này đảo dependency cũ:

```text
Trước:
Swagger → README

Target:
Learning Portal → README
                → Quiz
                → API Docs (static when available)
                → Swagger/API execution (optional runtime)
                → Execution (optional)
```

---

## 13. Execution

Execution Context tiếp tục là capability độc lập theo architecture hiện tại.

Portal chỉ consume/launch capability khi module có Execution Context runtime.

Không chuyển ownership của Execution Context vào Portal.

Target:

```text
Learning Portal
      │
      └── Execution tab/action
              ↓
      existing Execution Context UI/API
```

Portal không duplicate capture/query/store logic.

---

## 14. Download

`Download` nên là action, không nhất thiết là content tab ngang hàng.

Ví dụ UI:

```text
[Overview] [Knowledge] [Quiz] [API Docs] [Execution]        [↓ Download]
```

Bấm `Download`:

```text
Download THREAD

Source Code
├── Source ZIP

Runnable Package
├── Executable JAR
├── WAR                     nếu module hỗ trợ

Docker
└── Docker Bundle
```

Không phải JAR nào cũng runnable.

Phân biệt artifact type:

```text
sourceZip
libraryJar
executableJar
war
dockerBundle
```

Ví dụ capability:

```json
{
  "download": {
    "source": true,
    "libraryJar": false,
    "executableJar": true,
    "war": false,
    "docker": true
  }
}
```

Một Docker bundle có thể có dạng:

```text
THREAD-docker.zip
├── thread.jar
├── Dockerfile
├── docker-compose.yml      # nếu cần
├── .env.example
└── README.md
```

Người dùng có thể chạy:

```bash
java -jar thread.jar
```

hoặc:

```bash
docker build -t java-learning-thread .
docker run -p 8080:8080 java-learning-thread
```

Artifact **không cần developer manual build sẵn từng JAR**.

Hai hướng đều hợp lệ:

```text
Pre-build
→ CI/GitHub Actions build artifact khi push/tag/release

On-demand
→ user request artifact chưa tồn tại
→ trigger build đúng module/version
→ publish/lưu artifact
→ request sau reuse artifact đã có
```

Với GitHub Actions, on-demand có thể dùng `workflow_dispatch`/API để truyền module + source ref. Điều này cho phép repository bắt đầu với **0 JAR được tạo thủ công**; artifact chỉ được build khi cần hoặc được pre-build tự động theo push/tag tùy policy sau này.

Identity của artifact nên gắn với module + source version/commit để tránh reuse JAR cũ sau khi source thay đổi.

Không dùng dependency/build cache như nguồn download lâu dài. Cache dùng để tăng tốc build; JAR/ZIP dành cho user nên là artifact/release asset hoặc storage tương đương.

Current Portal phase chưa implement build/download backend; UI có thể để placeholder/empty state.

---

## 15. Static-first Portal output và Spring Boot host

Một target output có thể là:

```text
project-portal/frontend/dist/
├── index.html
├── assets/
│   ├── css/
│   └── js/
├── data/
│   ├── modules.json
│   ├── readme/
│   └── quiz/
└── downloads/
    ├── THREAD/
    ├── ASPECT/
    └── ...
```

Portal về nguyên tắc vẫn có thể chạy như static website:

```text
HTML
CSS
JS
JSON
Markdown
download artifacts
```

Tuy nhiên current implementation **cố ý có cả Spring Boot và React** để phục vụ mục tiêu học full-stack.

Production bundle hiện được đóng vào Spring Boot application:

```text
frontend/dist
    ↓ Gradle processResources
classpath:/static
    ↓
Spring Boot executable JAR
```

Backend hiện không cung cấp business REST API cho frontend. Spring Boot trước mắt đóng vai trò host static bundle và là chỗ để bổ sung server-side capability về sau khi thật sự cần.

---

## 16. Route / context path

Portal là root-level/common entry point, độc lập với context path của từng learning module runtime.

Current routing dùng `HashRouter`:

```text
/#/
/#/learning
/#/learning/THREAD
/#/learning/ASPECT
```

Logical `SERVICE_NAME` đang là lựa chọn phù hợp cho module route identity trong fake/current phase.

Nếu sau này muốn clean URL:

```text
/learning/THREAD
```

thì có thể chuyển sang `BrowserRouter`, nhưng khi đó Spring/static host phải fallback unknown client route về `index.html`.

---

## 17. Vị trí implementation

Quyết định hiện tại đã chốt là root-level boundary:

```text
java-learning/
├── module/               # what is taught
├── project-portal/       # where it is presented
├── project-build/        # build/runtime infrastructure
└── project-orchestration/# policy/composition
```

Portal không nằm trong `project-build/springboot-runtime`, vì nó là product/presentation application chứ không phải reusable runtime library.

Điểm cần giữ cố định là:

```text
build-time catalog generation
        ≠
Spring application runtime
        ≠
Portal frontend rendering
```

---

## 18. Generator responsibilities

Build-time generator dự kiến có trách nhiệm:

```text
1. đọc canonical module hierarchy/model
2. đọc module metadata/master.json
3. phát hiện README/quiz/artifact thực tế
4. resolve effective capabilities
5. validate content
6. generate deterministic machine-readable catalog
7. collect/copy static learning content
8. collect/downloadable artifacts nếu được enable
```

Generated output phải theo rule chung của repository:

```text
deterministic ordering
idempotent
write-if-changed
no duplicated generated content
```

Không dùng generated projection làm source of truth ngược lại.

---

## 19. Capability resolution principle

Không nên chỉ derive capability từ một boolean duy nhất.

Ví dụ:

```text
BUILD_README=TRUE
```

cho biết intent/configuration, nhưng chưa đảm bảo resource thực tế tồn tại.

Target resolution:

```text
Declared intent
      +
Actual filesystem/artifact state
      ↓
Effective capability
```

Ví dụ:

```text
BUILD_QUIZ=TRUE
+ quiz/vi/questions.yml exists
→ quiz.enabled=true
→ quiz.available=true
```

Hoặc:

```text
BUILD_QUIZ=TRUE
+ không có quiz data
→ quiz.enabled=true
→ quiz.available=false
```

Điều này giúp UI và validation phân biệt rõ:

```text
feature không được hỗ trợ
vs
feature được hỗ trợ nhưng content chưa hoàn thiện
```

---

## 20. Search/filter UX

Current UI lấy cảm hứng từ pattern của các website luyện phỏng vấn nhưng không copy branding/content/assets.

Header hiện có:

```text
Java Learning
├── Trang chủ
├── Learning
├── global search UI
└── VI ↔ EN slider
```

`Trang chủ` hiện để trống để dành cho nội dung tương lai.

Learning page có thêm một search riêng:

```text
Tìm kiến thức...
```

Hai search có semantic khác nhau:

```text
Header search
→ target global search toàn Portal

Learning search
→ target search/filter knowledge trong Learning experience
```

Current Learning search chạy trên fake frontend data; không gọi backend.

Portal dùng layout:

```text
Sidebar category tree
        +
Search
        +
Main content/question list
```

Nhưng category không hard-code theo ngôn ngữ/framework.

Category tree được generate từ module hierarchy.

Current phase chưa generate hierarchy thật; fake TypeScript tree dùng tên/module thật của repository và render bằng loop để component contract gần với target generated data.

Để tránh sidebar quá lớn:

```text
branch không chứa capability/content cần hiển thị
→ có thể ẩn

parent category có descendant hợp lệ
→ giữ để navigation

leaf module có content
→ hiển thị cùng count/metadata phù hợp
```

Ví dụ:

```text
Platform
└── Development
    └── Paradigm
        ├── AOP              22 questions
        └── Concurrency
            └── Thread      42 questions
```

---

## 21. AI Assistant — optional future capability

Embedded AI không phải requirement của Learning Portal MVP.

Nếu sau này thêm, nên coi nó là một capability/product feature riêng, ví dụ:

```text
Ask AI
```

Use case phù hợp:

- giải thích câu quiz;
- giải thích README section;
- giải thích API docs/API execution;
- giải thích execution vừa chạy.

Không nên để frontend chứa provider secret/API key.

Nếu gọi provider API thì credential phải nằm phía server/proxy.

Portal vẫn phải hoạt động đầy đủ khi không có AI provider.

---

## 22. Trải nghiệm học mục tiêu

Target user flow:

```text
                Java Learning Portal
                         │
                 chọn một module
                         │
             ┌───────────┼───────────┐
             │           │           │
             ▼           ▼           ▼
          Overview    Knowledge      Quiz
                         │           │
                         └─────┬─────┘
                               │
                      module runnable?
                         /            \
                       yes            no
                        │              │
                        ▼              │
                    API Docs           │
                        │              │
                    Execution          │
                        │              │
                        └──────┬───────┘
                               ▼
                           Download
```

Portal hướng đến việc liên kết bốn lớp học tập:

```text
Theory       → README / Knowledge
Assessment   → Quiz
Experiment   → API Docs / API Execution
Observation  → Execution Context
```

Download giúp người học mang example về chạy độc lập.

---

## 23. Architectural outcome

Thiết kế này tách rõ responsibility:

```text
Learning Portal
→ navigation và learning experience chung

README / Knowledge
→ lý thuyết và documentation

Quiz
→ assessment

Swagger
→ API experiment

Execution Context
→ runtime observation/inspection

Download artifacts
→ portable source/runtime package
```

Quan hệ mục tiêu:

```text
                   Learning Portal
                         │
          ┌──────────────┼──────────────┐
          │              │              │
          ▼              ▼              ▼
      Knowledge         Quiz         Download
          │
          │       Runtime optional
          │              │
          │       ┌──────┴──────┐
          │       ▼             ▼
          │    Swagger      Execution Context
          │
          └───────────────────────────────
```

Kết quả quan trọng nhất:

> Documentation và learning experience không còn phụ thuộc vào việc module có Spring Boot Application hay không.

Swagger tiếp tục có giá trị như API experiment tool, nhưng không còn phải gánh vai trò portal/documentation host cho toàn bộ repository.

---

## 24. MVP / current implementation status

MVP đã bắt đầu implementation. Current phase đã có:

```text
1. root-level project-portal
2. Spring Boot application host, port 9098
3. React + TypeScript + Vite + React Router
4. Gradle frontend build integrated with processResources/bootJar
5. header + Home + Learning navigation
6. VI/EN frontend language switch
7. fake module hierarchy sidebar
8. Learning knowledge search
9. Overview / Knowledge / Quiz / API Docs fake panels
10. Knowledge category filter + collapse/expand interaction
11. Execution/Download placeholder state
12. production static bundle served by Spring Boot
```

Chưa implement trong current phase:

```text
real module-catalog generation
real README projection/Markdown rendering
real quiz source/generator
real OpenAPI projection
backend REST integration
Execution Context aggregation
artifact build/download integration
database/login/multi-user progress
admin/online CRUD
AI provider integration
```

Portal nên tiếp tục giữ nguyên tắc:

```text
static-first
metadata-driven
capability-based
module-runtime-independent
backend-when-needed
```
