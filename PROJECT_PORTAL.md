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

Current phase vẫn static-first và chưa gọi REST API của chính Portal. Module hierarchy/routing lấy từ generated `module-catalog.json`; Overview, Menu, Knowledge, Quiz, Interview và API Docs đều consume build-time projection thật.

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
├── build/generated/portal-data/
│   ├── module-catalog.json
│   └── module/
│       └── {ROUTE_ID}/
│           ├── overview/
│           ├── knowledge/
│           ├── quiz/{lang}/question.yml
│           ├── interview/{lang}/question.yml
│           └── api/{lang}/
│               ├── api-descriptions.yml
│               ├── api-execution.yml
│               ├── api-params.yml
│               └── controller-description.yml
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
module/ filesystem + module metadata
        ↓
Portal build-time generators
        ↓
build/generated/portal-data/
├── module-catalog.json
└── module/{ROUTE_ID}/{feature}/...
        ↓ Vite static input
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

Theme hiện có Light/Dark toggle ở header. Lần đầu Portal dùng `prefers-color-scheme`; khi user chủ động đổi theme thì lựa chọn được lưu trong `localStorage`. Các semantic capability colors không đổi giữa hai theme:

```text
Overview   → gray
Knowledge  → blue
Quiz       → amber
Interview  → teal
API Docs   → red
Local Run  → purple
Download   → green
```

### 1.4 Development và production mode

Hai mode có mục tiêu khác nhau:

```text
Frontend development
→ Vite dev server
→ nhanh, hot reload
→ hiện không cần Spring Boot vì Portal data đang static/generated ở build-time

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

[Overview] [Menu] [Knowledge] [API Docs] [Quiz] [Interview] [Local Run]        [Download]

Progress
████████░░

Knowledge
1. Basic Thread
2. Interruption
3. Concurrency Problems
...

Quiz
52 questions

Interview
46 questions

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

Current phase đã dựng navigation shell và đã migrate Overview/Menu/Knowledge/API Docs/Quiz/Interview sang generated/static data. `Local Run` có UI hai khung Build/Download JAR + Run Locally và dùng cùng relative API contract `/api/local-run/*` ở mọi môi trường. Localhost xử lý contract bằng Spring Boot `project-portal`; production xử lý contract bằng Cloudflare Pages Functions. Hai adapter độc lập, cùng dispatch/poll GitHub Actions và cùng đọc rolling GitHub Release `local-run`; token GitHub luôn nằm server-side.

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

Current flow cho module hierarchy:

```text
Canonical module structure/model
        │
        ├── generate → module-structure.txt
        ├── generate → STRUCTURE.md
        └── generate → project-portal/build/generated/portal-data/module-catalog.json
```

Portal consume machine-readable catalog này trực tiếp; frontend không parse `module-structure.txt`.

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

Hiện tại `ProjectStructureService` kết hợp filesystem/module metadata để generate hierarchy catalog:

```text
module filesystem hierarchy
        +
real-module identity từ local gradle.properties
        +
module metadata/master.json
        ↓
module-catalog.json
        ↓
Learning Portal
```

Catalog hiện đã advertise localized Overview/Knowledge/Quiz/Interview/API path dựa trên declared module capability + resource thực tế mà generator tìm thấy. Một capability resolver tổng quát hơn cho mọi future artifact/runtime vẫn là target phase sau; không nhét fake availability vào catalog.

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
module README/menu Markdown + localized knowledge metadata
        ↓
build-time collection/generation
        ↓
static portal assets
        ↓
Markdown renderer
```

Ví dụ generated assets:

```text
project-portal/build/generated/portal-data/
└── module/
    └── THREAD/
        └── knowledge/
            ├── vi/
            │   ├── index.json
            │   └── content/
            └── en/
                ├── index.json
                └── content/
```

Learning Portal trở thành nơi đọc README chung của mọi module.

Swagger vẫn có thể giữ README integration hiện tại đối với module runnable, nhưng không còn là nơi duy nhất để đọc knowledge.

Knowledge content và metadata có owner riêng:

```text
readme/{lang}/menu/**/*.md
→ canonical Markdown + exact anchored H2 sections

src/main/resources/readme/{lang}/knowledge-metadata.yml
→ difficulty / aiGenerated / reviewed
```

Metadata file được key theo README relative path rồi section anchor, ví dụ:

```yaml
1.Basic/Basic.md:
  thread-state:
    difficulty: ADVANCED
    aiGenerated: true
    reviewed: false
```

`difficulty` nhận `BASIC`, `INTERMEDIATE`, `ADVANCED`; missing metadata mặc định `BASIC`, `aiGenerated=true`, `reviewed=false`. Task `syncMetadataReadme` là explicit build/documentation action để đồng bộ skeleton từ Markdown hiện tại, preserve custom metadata của topic còn tồn tại, loại stale topic/file và write-if-changed. Portal generation chỉ consume metadata này; nó không được tự ý ghi ngược metadata vào source.

Frontend hiển thị difficulty bằng label localized (`CƠ BẢN / TRUNG BÌNH / NÂNG CAO` ở VI) và governance badge `AI Generated`, `Reviewed/Not Reviewed` với tooltip giải thích trạng thái. Metadata này là thông tin provenance/review của nội dung, không thay đổi Markdown source.

---

## 10. Quiz

Quiz là một capability learning độc lập với Swagger.

Current Quiz contract:

```text
single-choice
4 đáp án
1 đáp án đúng
explanation riêng cho từng đáp án
stable answer identity A/B/C/D
shuffle ở frontend khi load document
```

Dữ liệu Quiz nằm trong module và version cùng source code. Enablement đi đúng build boundary:

```text
project-build/gradle-runtime
  canonical automation/master.json → BUILD_QUIZ
  canonical quiz/question-schema.yml
  QuizSetupPlugin + QuizStructureService
        ↓
project-orchestration
  BUILD_QUIZ=TRUE → apply QUIZ_SETUP_PLUGIN
        ↓
module
  src/main/resources/quiz/{lang}/question.yml
```

`MODULE_LANGUAGE` quyết định language skeleton. Mỗi `question.yml` có block `# <quiz-schema> ... # </quiz-schema>` được generate từ canonical schema với comment theo đúng language. Khi canonical schema đổi, generator được phép update block comment đó nhưng không được overwrite phần `questions:` do developer sở hữu.

Source layout hiện tại:

```text
module/.../
└── src/main/resources/quiz/
    ├── vi/
    │   └── question.yml
    └── en/
        └── question.yml
```

Canonical item shape:

```yaml
questions:
  - id: thread-start-method
    question: Phương thức nào bắt đầu execution trên một thread mới?
    aiGenerated: true
    reviewed: false
    readmeRelated:
      file: "1.Basic/Basic.md"
      anchor: "start-vs-run"
    apiRelated:
      controller: BasicThreadController
      methodSignature: startVsRun()
    answers:
      - id: A
        answer: start()
        explanation: Đúng. start() bắt đầu execution trên thread mới.
      - id: B
        answer: run()
        explanation: Sai. run() trực tiếp chạy trên thread hiện tại.
      - id: C
        answer: join()
        explanation: Sai. join() dùng để chờ thread khác kết thúc.
      - id: D
        answer: yield()
        explanation: Sai. yield() không tạo thread mới.
    correctAnswerId: A
```

`answers[*].id` là identity canonical và luôn là đúng tập `A/B/C/D`; nó không phải label vị trí cố định trên màn hình. Khi browser load một Quiz document, frontend shuffle `answers[]` đúng một lần cho mỗi question context rồi gán lại display label A/B/C/D theo vị trí mới. Correctness vẫn check `selectedAnswer.id === correctAnswerId`, nên shuffle không làm mất identity hay explanation tương ứng. Ordinary React re-render không được shuffle lại.

`aiGenerated` và `reviewed` dùng chung governance model với Knowledge/API. `readmeRelated` và `apiRelated` là optional relation pair: để trống cả cặp nghĩa là không có mapping; không điền nửa cặp hoặc đoán relation khi source không hỗ trợ. Related Knowledge/API chỉ được expose trong Quiz sau khi user chọn đúng đáp án.

Build-time `PortalQuizProjectionService` validate source bằng cùng canonical schema rồi copy nguyên localized `question.yml` sang `portal-data/module/{ROUTE_ID}/quiz/{lang}/question.yml`; `module-catalog.json` expose path `quiz.{lang}`. Service còn validate relation target: README relation phải trỏ tới file/anchor thực tế, API relation phải resolve tới exact `controller + methodSignature` đang active (`usage: true`). Blank/blank là hợp lệ; partial/unresolved relation được log warning thay vì generator tự sửa nội dung human-owned. Frontend parse YAML trực tiếp, preload Quiz count, và bỏ fixture Quiz cũ.

THREAD hiện có **52 câu Quiz cho mỗi language VI/EN**, được bổ sung dựa trên README của module. Nội dung localized nằm trong source module, không nằm trong frontend fixture.

Quiz có thể liên kết với knowledge và runtime demo:

```yaml
readmeRelated:
  file: "1.Basic/Basic.md"
  anchor: "start-vs-run"

apiRelated:
  controller: BasicThreadController
  methodSignature: startVsRun()
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

## 10A. Interview

Interview là capability ôn phỏng vấn/reference Q&A độc lập với Quiz. Nó dùng cùng static-first ownership model nhưng schema đơn giản hơn: mỗi item là một câu hỏi và một câu trả lời tham khảo, không có lựa chọn A/B/C/D.

Build boundary:

```text
project-build/gradle-runtime
  canonical automation/master.json → BUILD_INTERVIEW
  canonical interview/question-schema.yml
  InterviewSetupPlugin + InterviewStructureService
        ↓
project-orchestration
  BUILD_INTERVIEW=TRUE → apply INTERVIEW_SETUP_PLUGIN
        ↓
module
  src/main/resources/interview/{lang}/question.yml
```

`MODULE_LANGUAGE` quyết định localized skeleton. Generator tạo file mới với `questions: []`, refresh block `# <interview-schema> ... # </interview-schema>` khi canonical schema đổi và bổ sung required fields còn thiếu bằng default canonical, nhưng không overwrite nội dung câu hỏi/câu trả lời human-owned.

Canonical item shape hiện tại:

```yaml
questions:
  - question: "run() và start() khác nhau thế nào?"
    answer: "run() là lời gọi method bình thường trên caller thread; start() bắt đầu lifecycle của Thread và JVM thực thi run() trên execution mới."
    aiGenerated: true
    reviewed: false
    readmeRelated:
      file: "1.Basic/Basic.md"
      anchor: "start-vs-run"
    apiRelated:
      controller: BasicThreadController
      methodSignature: startVsRun()
```

Frontend render danh sách câu hỏi theo thứ tự source. `Reference Answer / Câu trả lời tham khảo` mặc định collapsed và từng câu có thể mở/đóng độc lập; nhiều câu trả lời có thể cùng mở. Governance badge `AI Generated` và `Reviewed/Not Reviewed` được hiển thị giống các learning surface khác. Khi relation resolve được, phần answer có thể mở `Related Knowledge` hoặc `Related API`; Markdown được lazy-load, còn rich API execution HTML tiếp tục đi qua DOMPurify trước khi render.

`PortalInterviewProjectionService` hiện schema-validate rồi copy exact localized YAML sang `portal-data/module/{ROUTE_ID}/interview/{lang}/question.yml`, remove stale projection và expose catalog path `interview.{lang}`. Khác Quiz, current Interview projection **chưa có build-time relation-target validation**; frontend chỉ render Related action khi target thực tế resolve được. Không mô tả hai pipeline này là giống hoàn toàn cho tới khi relation validation được bổ sung cho Interview.

THREAD hiện có **46 câu Interview cho mỗi language VI/EN**, được soạn dựa trên README hiện tại. `readmeRelated`/`apiRelated` chỉ được điền khi có target phù hợp; câu tổng hợp hoặc câu không có một API trực tiếp duy nhất có thể để relation trống thay vì ép mapping.

---

## 11. Static-first data architecture

Learning Portal không cần database cho phiên bản đầu.

Knowledge/quiz/interview/module catalog là static knowledge, phù hợp để lưu trong Git.

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

và hiện render dữ liệu thật từ generated Swagger metadata.

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

Build-time projection hiện ưu tiên reuse trực tiếp canonical Swagger metadata thay vì scan source Java lần thứ hai hoặc invent proprietary API schema. Với mỗi language có đủ bốn file:

```text
src/main/resources/swagger/{lang}/
├── api-descriptions.yml
├── api-execution.yml
├── api-params.yml
└── controller-description.yml
```

generator copy nguyên bộ sang:

```text
project-portal/build/generated/portal-data/module/{ROUTE_ID}/api/{lang}/
```

và `module-catalog.json` expose base path `api.{lang}`. Language Swagger bị thiếu một trong bốn file không được publish thành Portal API projection hoàn chỉnh. Frontend API Docs hiện consume trực tiếp các file static này; không cần module runtime và không cần Portal REST API.

Danh sách language hợp lệ của module lấy từ `master.json -> MODULE_LANGUAGE`. Portal chỉ publish một language đã được module khai báo và có đủ source projection tương ứng; filesystem không tự trở thành source of truth cho language support.

Current browser-side mapping:

```text
controller-description.yml
→ controller description + readmeRelated.file

api-descriptions.yml
→ method signature/name + HTTP metadata + summary/description + readmeRelated + aiGenerated/reviewed

api-execution.yml
→ guided execution HTML

api-params.yml
→ parameter description khi có
```

Ordering của API Reference bám theo learning path của README:

```text
controller
→ numeric chapter prefix từ readmeRelated.file
→ cùng chapter thì alphabetical
→ invalid/missing mapping theo fallback rule

method
→ vị trí exact readmeRelated.anchor trong resolved README file
→ không sort thay bằng path/controller name
```

`execution` có thể chứa rich HTML, vì vậy frontend sanitize nội dung trước khi render. API Reference hiện là **documentation/reference only**: Portal không live execute/debug API ở panel này. Muốn chạy/debug experiment, user phải tải source/module về chạy local hoặc dùng runtime tooling của module.

API method governance hiện dùng `aiGenerated=true` và `reviewed=false` làm default khi generator gặp method mới; sau khi field tồn tại thì chúng là human-owned metadata và được preserve qua regeneration. API Docs và Related API popup dùng cùng badge/tooltip convention với Knowledge. `difficulty` chỉ thuộc Knowledge metadata, không thuộc API description.

`readmeRelated` vẫn là relationship canonical giữa API và Knowledge: controller cung cấp `readmeRelated.file`, method cung cấp exact `readmeRelated.anchor`, method `file` rỗng thì inherit controller file. Không tạo thêm relationship song song kiểu `knowledgeRelated`.

Frontend dùng relationship này theo cả hai chiều mà không tạo source metadata thứ hai:

```text
API Docs method detail
→ Execution | Related Knowledge

Knowledge section
→ Related APIs popup
```

Related Knowledge resolve từ effective README file + exact anchor. Related APIs được derive ngược từ cùng `readmeRelated` mapping đã parse. Popup phía Knowledge chỉ preview API metadata/execution cần thiết; không thêm action “Go to Details” và không biến popup thành live execution surface.

Expanded API method detail giữ lower-area split `Execution | Related Knowledge`; Execution là phần chính không cần internal scroll riêng, còn Related Knowledge có thể scroll độc lập khi nội dung dài. Đây là presentation contract hiện tại, không thay đổi ownership của source metadata.

API Docs mặc định collapsed lần đầu. Sau khi user mở/đóng controller hoặc method, trạng thái gần nhất được giữ khi đổi tab rồi quay lại trong cùng module/language. Global Expand all/Collapse all là explicit user action và không thay đổi source data.

Điều này đảo dependency cũ:

```text
Trước:
Swagger → README

Target:
Learning Portal → README
                → Quiz
                → Interview
                → API Docs (static when available)
                → Swagger/API execution (optional runtime)
                → Execution (optional)
```

---

## 13. Local Run / Execution

`Local Run` là capability build/download runnable JAR và độc lập với Execution Context. Frontend không biết backend adapter cụ thể; nó luôn gọi cùng relative API:

```text
POST /api/local-run/build
GET  /api/local-run/status?runId=...&moduleId=...
GET  /api/local-run/artifact?moduleId=...&sourceFingerprint=...
```

Local runtime:

```text
React on localhost
      ↓ same-origin /api/local-run/*
Spring Boot project-portal
      ↓ GITHUB_ACTION_TOKEN from process env or ignored project-portal/.env
GitHub REST API
      ↓
local-run-build.yml
      ↓
guarded module bootJar
      ↓
rolling GitHub Release `local-run`
      ↓ raw Release Asset: thread.jar / aspect.jar / ...
GitHub browser_download_url
```

Production runtime:

```text
React static deployment
      ↓ same-origin /api/local-run/*
Cloudflare Pages Functions
      ↓ GITHUB_ACTION_TOKEN from Cloudflare secret
GitHub REST API / GitHub Actions / rolling Release metadata
```

Local và production không gọi qua nhau. Browser chỉ gửi `moduleId` và `sourceFingerprint`; backend/workflow resolve module thật và không nhận arbitrary Gradle task/path từ client. `.env` là local human-owned/ignored; `.env.example` chỉ chứa placeholder. `GITHUB_ACTION_TOKEN` của local/production adapter cần Actions Read/Write để dispatch/poll và Contents Read để đọc Release metadata; upload Release Asset dùng workflow `GITHUB_TOKEN` riêng với `contents: write`.

Artifact freshness dùng module-scoped Git tree SHA:

```text
module-catalog.json
THREAD.sourceFingerprint = git rev-parse HEAD:module/.../thread
ASPECT.sourceFingerprint = git rev-parse HEAD:module/.../aop

Release: local-run
thread.jar  label=fingerprint:<THREAD tree SHA>
aspect.jar  label=fingerprint:<ASPECT tree SHA>
```

Khi mở tab Local Run, UI check Release Asset trước. Nếu filename tồn tại và label fingerprint khớp thì hiện `Download JAR` ngay. Nếu asset không có hoặc fingerprint khác thì hiện `Build JAR`. Workflow recompute tree SHA sau checkout và reject request nếu catalog fingerprint đã stale. Upload dùng stable filename + `--clobber`, vì vậy build THREAD chỉ thay `thread.jar`; `aspect.jar` không bị build/download lại. Current fingerprint scope là directory của chính module và chưa bao gồm transitive dependency/shared build-input closure.

Release Asset là raw JAR, không phải Actions Artifact ZIP. Production Pages Function không tải toàn bộ ZIP vào memory và không unzip JAR; nó chỉ trả `browser_download_url` của Release Asset. Điều này loại bỏ large-file proxy path từng có nguy cơ gây 502 trên Worker.

Execution Context tiếp tục là capability độc lập và giữ ownership runtime/capture/query/store riêng; Local Run không duplicate hoặc phụ thuộc vào Execution Context.

---

## 14. Download

`Download` nên là action, không nhất thiết là content tab ngang hàng.

Ví dụ UI:

```text
[Overview] [Menu] [Knowledge] [API Docs] [Quiz] [Interview] [Local Run]        [↓ Download]
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
├── module-catalog.json
└── module/
    ├── THREAD/
    │   ├── overview/
    │   ├── knowledge/
    │   └── api/
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

Bundle vẫn được đóng vào Spring Boot application để test/đóng gói full-stack local:

```text
frontend/dist
    ↓ Gradle processResources
classpath:/static
    ↓
Spring Boot executable JAR
```

Backend hiện không cung cấp business REST API cho frontend. Spring Boot trước mắt đóng vai trò host static bundle và là chỗ để bổ sung server-side capability về sau khi thật sự cần.

Public production hiện được deploy theo static path riêng:

```text
push/merge vào main hoặc workflow_dispatch
        ↓
.github/workflows/deploy-portal.yml
        ↓
GitHub Actions + JDK 21
        ↓
./gradlew :project-portal:buildFrontend --no-daemon
        ↓
project-portal/frontend/dist
        ↓ Cloudflare Wrangler
Cloudflare Pages Direct Upload
        ↓
https://java-learning-cly.pages.dev
```

Workflow không dùng Cloudflare Git integration; GitHub Actions là CI/CD owner và upload build output vào Pages project đã tạo theo Direct Upload. Hai credential `CLOUDFLARE_ACCOUNT_ID` và `CLOUDFLARE_API_TOKEN` chỉ tồn tại dưới GitHub Repository Secrets. Merge Pull Request vào `main` cũng kích hoạt workflow vì `main` nhận commit mới và phát sinh `push` event.

Cloudflare project identifier dùng trong Wrangler là **`java-learning`**; `java-learning-cly.pages.dev` là public hostname, không phải project name. Current deploy command vì vậy giữ contract:

```text
pages deploy project-portal/frontend/dist --project-name=java-learning --branch=main
```

GitHub Actions chạy trên Linux. Các Gradle generator tham gia `:project-portal:buildFrontend` phải an toàn với case-sensitive filesystem; plugin stub generator hiện reuse existing filename theo case-insensitive match và fail nếu có nhiều match mơ hồ để tránh tạo duplicate khác casing.

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
3. phát hiện README/quiz/interview/artifact thực tế
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
+ src/main/resources/quiz/vi/question.yml exists
→ quiz.enabled=true
→ quiz.available=true
```

Interview resolve cùng nguyên tắc:

```text
BUILD_INTERVIEW=TRUE
+ src/main/resources/interview/vi/question.yml exists
→ interview.enabled=true
→ interview.available=true
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

`Trang chủ` hiện là placeholder tối giản: thông báo trang đang được cập nhật và có CTA `Đi tới Learning` / `Go to Learning`. Nó không chứa fake learning content.

Learning page hiện có hai search riêng ngoài header global-search UI:

```text
Lọc module...

và

Tìm kiến thức...
```

Ba search có semantic khác nhau:

```text
Header search
→ target global search toàn Portal

Sidebar module search
→ filter generated module tree

Learning search
→ target search/filter knowledge trong Learning experience
```

Current sidebar search chạy trên generated catalog; Menu/Knowledge search/filter chạy trên generated Knowledge index. Cả hai đều client-side và không gọi backend.

Portal dùng layout:

```text
Sidebar category tree
        +
Search
        +
Main content/question list
```

Nhưng category không hard-code theo ngôn ngữ/framework.

Category tree hiện được render từ generated `module-catalog.json`.

Current sidebar interaction:

```text
row có children
→ click toàn row để expand/collapse
→ `+` = collapsed, `−` = expanded
→ default toàn tree expanded

module row
→ có nút tròn `>` bên phải
→ nút `>` mới navigate tới module page

search match chính node
→ giữ full subtree của node đó

search chỉ match descendant
→ chỉ giữ ancestor path cần thiết tới kết quả
```

Search mode vẫn cho phép collapse/expand; nó không ép tất cả kết quả luôn mở.

Ngay cạnh switch có global tree actions:

```text
Expand all
→ mở toàn bộ projected tree hiện tại

Collapse all
→ thu gọn toàn bộ projected tree hiện tại
```

Ngay dưới module search có switch:

```text
Full tree / Đầy đủ
→ render toàn bộ generated tree

Real modules / Module thật
→ đây là content filter của Portal, không phải định nghĩa physical real module theo `gradle.properties`
→ giữ MODULE nếu Knowledge > 0 OR Quiz > 0 OR Interview > 0 OR API Docs > 0
→ chỉ ẩn MODULE khi cả bốn count đều = 0
→ giữ GROUP ancestor cần thiết để bảo toàn hierarchy
→ prune branch không chứa module còn content
→ không flatten thành list
```

MODULE đạt điều kiện `Module thật` được highlight bằng background riêng để phân biệt trong cả Full tree và filtered mode. Màu highlight có token riêng cho Light/Dark theme; active row vẫn có state nổi bật hơn.

Tab state được giữ theo browser session cho module/language hiện tại: Menu, Knowledge, API Docs, Quiz và Interview được lazy-mount lần đầu rồi giữ mounted khi user đổi tab. Vì vậy manual expand/collapse, lựa chọn Quiz và Interview answer state gần nhất không tự reset chỉ vì tab đang bị ẩn. Khi đổi module/language, component key/reset boundary tạo state mới phù hợp context mới.

Menu và API Docs mặc định collapsed. Knowledge cũng bắt đầu chưa mở section nào, nhưng cho phép **nhiều section mở đồng thời**. Content Markdown của section đã load được cache trong panel để quay lại tab không phải fetch lại ngay.

Knowledge category strip hỗ trợ cả nút cuộn trái/phải và pointer drag ngang. Pointer chỉ chuyển sang drag sau threshold nhỏ (>5px); click bình thường vẫn chọn category, còn click phát sinh sau một drag thật sự bị suppress để tránh đổi category ngoài ý muốn. Cursor `grab/grabbing` phản ánh state tương tác này.

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
          Overview    Knowledge   Quiz / Interview
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

Portal hướng đến việc liên kết các lớp học tập:

```text
Theory       → README / Knowledge
Assessment   → Quiz
Interview    → reference Q&A dựa trên Knowledge
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
7. Light/Dark theme theo OS + localStorage persistence
8. ProjectStructureService-generated `module-catalog.json`
9. real module hierarchy sidebar + dynamic `#/learning/{routeId}` routing
10. sidebar module search với self-match/descendant-match semantics
11. Full tree / Real modules switch, prune nhưng giữ ancestor hierarchy
12. row expand/collapse + separate circular `>` navigation action cho real module
13. Overview từ language `BASE.md` qua build-time projection
14. build-only module-first Portal data layout: `portal-data/module/{ROUTE_ID}/{feature}/...`
15. build-time Knowledge projection theo category + anchored section
16. localized `knowledge-metadata.yml` + explicit `syncMetadataReadme` quản lý difficulty/AI provenance/review state cho exact README section; Portal projection đưa metadata này vào Knowledge index
17. Menu + Knowledge frontend consume generated Knowledge index/section Markdown; section content được lazy-load khi mở, nhiều Knowledge section có thể mở đồng thời, category strip hỗ trợ arrow-scroll + drag-scroll
18. build-time API metadata projection: copy complete localized four-file Swagger sets vào `module/{ROUTE_ID}/api/{lang}/` + expose `api` base path trong module catalog
19. API Docs frontend consume trực tiếp generated Swagger YAML; controller/method order follow README mapping, rich execution HTML được sanitize, API Reference là reference-only chứ không live execute/debug
20. API methods có human-owned `aiGenerated/reviewed`; Knowledge/API Docs/Related API popup render shared governance badge + tooltip; API ↔ Knowledge tiếp tục dùng duy nhất `readmeRelated`
21. Knowledge/Quiz/Interview/API counts đều lấy từ generated/static data; sidebar `Module thật` giữ module khi ít nhất một trong bốn count > 0 và chỉ ẩn khi cả bốn = 0
22. sidebar tree mặc định expanded, dùng `+`/`−`, có Expand all/Collapse all; qualifying content module được highlight trong cả Full tree và Module thật mode
23. Menu mặc định collapsed + Expand all/Collapse all; Knowledge/API Docs/Menu/Quiz/Interview giữ state gần nhất khi đổi tab rồi quay lại trong cùng module/language
24. Download action dùng popup dùng chung; popup đóng khi click ngoài, nhấn Escape hoặc toggle lại chính nút
25. Home là placeholder có CTA sang Learning; Knowledge category filter có collapse/expand + horizontal drag-scroll
26. semantic capability colors dùng chung cho sidebar/tabs và giữ nguyên giữa Light/Dark
27. `Local Run` là label hiện tại của internal `execution` tab; frontend luôn gọi relative `/api/local-run/*`; local dùng Spring Boot adapter còn production dùng Cloudflare Pages Functions adapter; cả hai dispatch/poll GitHub Actions và check rolling GitHub Release Asset theo module-scoped `sourceFingerprint`; không còn mock và hai môi trường không phụ thuộc nhau
28. production static bundle vẫn có thể được serve bởi Spring Boot khi chạy packaged application
29. public static Portal deploy lên Cloudflare Pages (`java-learning-cly.pages.dev`) bằng `.github/workflows/deploy-portal.yml`; Wrangler project name là `java-learning`; push/merge `main` hoặc `workflow_dispatch` → JDK 21 → `:project-portal:buildFrontend` → Wrangler Pages deploy
30. Gradle plugin stub generator đã Linux-safe về filename casing để CI không tạo duplicate plugin khác casing
31. Quiz source/generator đã migrate thật: `BUILD_QUIZ` → orchestration → localized `question.yml`; canonical schema drive localized comment + validation; Portal copy static projection, shuffle answer position một lần khi load và giữ stable answer identity để check đúng/sai. THREAD hiện có 52 câu VI và 52 câu EN dựa trên README, kèm governance + optional Knowledge/API relations
32. Interview source/generator đã migrate thật: `BUILD_INTERVIEW` → orchestration → localized `question.yml`; canonical schema drive localized comment + validation; Portal copy static projection, preload count và render reference answer collapsed/expandable với governance + Related Knowledge/API. THREAD hiện có 46 câu VI và 46 câu EN dựa trên README
33. top-level module tabs hiện theo thứ tự `Overview → Menu → Knowledge → API Docs → Quiz → Interview → Local Run`
```

Chưa implement trong current phase:

```text
capability availability resolver từ actual resource/artifact state
Spring Boot Portal REST integration ngoài Local Run local adapter
Execution Context aggregation
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
