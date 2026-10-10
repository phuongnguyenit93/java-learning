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

Current phase vẫn static-first và chưa gọi REST API của chính Portal. Module hierarchy/routing lấy từ generated `module-catalog.json`; Overview, Menu, Knowledge, Roadmap, Quiz, Interview và API Docs đều có build-time projection thật. Roadmap projection copy trực tiếp source `roadmap/<lang>/roadmap.yml`; Portal chỉ consume source roadmap này, không tự suy ra roadmap từ Knowledge.

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
│           ├── roadmap/{lang}/roadmap.yml
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
http://localhost:9098/learning
http://localhost:9098/learning/java/knowledge/THREAD
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
BrowserRouter
    ↓
pages/components
```

Portal hiện dùng `BrowserRouter` để URL client sạch, ví dụ `/about-me` và `/learning/java/knowledge/THREAD`. Spring MVC chỉ forward các route SPA đã biết về `index.html`; URL trên browser được giữ nguyên để React Router resolve đúng page khi deep-link hoặc refresh trực tiếp. Frontend còn normalize legacy `/#/...` URL sang clean path bằng `history.replaceState` để bookmark/link cũ tiếp tục hoạt động.

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

Learning hiện có hai lớp điều hướng trước khi tới module dashboard:

```text
Header: Home | Learning | Project | About Me
   ↓
/learning
   → Topic picker: Java | JavaScript | NodeJS
   → Java opens /learning/java/knowledge
   → JavaScript / NodeJS show localized, animated unavailable-content dialog
   ↓
Java section navigation: Knowledge
   → Knowledge contains the existing module tree/dashboard/tabs
```

Đây là **navigation/presentation hierarchy** mới; `Java → Knowledge` bao bọc **toàn bộ** learning module UI hiện tại và không đồng nghĩa với tab `Knowledge` nằm bên trong một module. Java section navigation nằm giữa, ngay dưới global header, chỉ còn mục Knowledge và nút quay lại danh sách chủ đề. Project là một mục độc lập của primary header, không nằm trong Java Learning. VI/EN áp dụng cho cả unavailable modal và nội dung Project.

`Project` hiện có route canonical duy nhất `/project`, được truy cập trực tiếp từ primary header bên phải Learning **không cần chọn ngôn ngữ**. `/learning/java/project` không còn render Project trong Java; đường cũ chỉ redirect sang `/project` và giữ query/hash. Component `ProjectPage.tsx` có ba thẻ domain study Banking, Logistics, E-commerce; eyebrow chỉ là `Project`, không còn nhãn `JAVA / PROJECT` hoặc dòng hướng dẫn hover/chạm. Các thẻ xếp dọc, chiếm toàn bộ chiều rộng của content area; tông màu khác nhau theo từng domain. Trạng thái thường dùng linear gradient đậm → nhạt từ trái sang phải; trên hover/focus/click, đường chéo chuyển thành split thẳng đứng với vùng logo và vùng mô tả là hai nền **màu phẳng** khác tông. Logo SVG luôn một màu, vị trí icon và split pane luân phiên hai phía theo từng thẻ. Mỗi thẻ là một nút disclosure chỉ mở mô tả, chưa dẫn tới một module/project implementation. Touch click toggle, keyboard focus và `prefers-reduced-motion` đều được hỗ trợ; mô tả được localized VI/EN.

Global header brand thay đổi theo route: chỉ các route `/learning/java/**` hiển thị **Java Learning** với SVG Java; `/about-me` hiển thị **Nguyen Do Dinh Phuong** với toàn bộ tên màu xanh và biểu tượng cũ; các route còn lại (bao gồm `/learning` topic picker) hiển thị **Learning Platform** với biểu tượng cũ. Ba topic card trên `/learning` hiển thị SVG logo Java/JavaScript/NodeJS thay cho chữ viết tắt; logo được đóng gói trực tiếp từ `frontend/src/assets/logos/` (Devicon, kèm MIT license), không fetch logo qua CDN lúc runtime.

`PageMetadata.tsx` cập nhật favicon và `document.title` theo React Router location, độc lập với Header: `/` và `/learning` dùng favicon gốc + `Learning Platform`; `/learning/java/knowledge/**` dùng SVG Java + `Java Learning - Knowledge`; `/project` dùng favicon gốc + `Project - Learning Platform`; `/about-me` dùng favicon gốc + `Nguyen Do Dinh Phuong`. Khi client-side navigate, browser-tab metadata đổi không cần reload. `index.html` giữ fallback title `Learning Platform`.

`About Me` (canonical route `/about-me`, legacy `/my-cv` redirects client-side) hiển thị phần tải CV căn giữa: heading **Download My CV**, hai nút PDF/DOC. PDF dùng Google Docs `export?format=pdf`; DOC dùng `export?format=docx` (tệp Microsoft Word .docx). Phía dưới có nút localized `Xem CV Online`/`Ẩn CV của tôi`, lazy-mount iframe Google Docs hiện tại chỉ khi người dùng bật; viewer căn giữa, rộng 80% (100% ở màn hình nhỏ), cao cố định 620px desktop/480px mobile với scroll nội bộ để chừa không gian cho nội dung sau này. Legacy query/hash được giữ khi redirect.

Ví dụ với module runnable `THREAD`:

```text
Thread & Concurrency

[Overview] [Menu] [Roadmap] [Knowledge] [API Docs] [Quiz] [Interview] [Local Run]

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

[Overview] [Menu] [Roadmap] [Knowledge] [Quiz]

Knowledge
1. Cache Concepts
2. Build Cache
3. Configuration Cache
...

Quiz
25 questions
```

Current phase đã dựng navigation shell và đã migrate Overview/Menu/Roadmap/Knowledge/API Docs/Quiz/Interview sang generated/static data. `Local Run` có UI hai khung Build/Download JAR + Run Locally và dùng cùng relative API contract `/api/local-run/*` ở mọi môi trường. Localhost xử lý contract bằng Spring Boot `project-portal`; production xử lý contract bằng Cloudflare Pages Functions. Hai adapter độc lập, cùng dispatch/poll GitHub Actions và cùng đọc rolling GitHub Release `local-run`; token GitHub luôn nằm server-side.

`Roadmap` nằm kế bên `Menu` nhưng có responsibility khác:

```text
Roadmap
→ milestone cấp cao
→ thứ tự học
→ quan hệ trước/sau và dependency
→ cho learner biết đang ở đâu và học gì tiếp theo

Menu
→ danh sách Knowledge chapter/section cụ thể
→ điều hướng chi tiết vào nội dung
```

Roadmap interaction có hai loại relation riêng: `relatedKnowledge` thuộc module hiện tại và luôn hiển thị ở phía đối diện milestone trên timeline, click item sẽ chuyển sang đúng Knowledge category; numbered marker vẫn giữ pulse animation để nhấn mạnh milestone có Knowledge hỗ trợ. `relatedModules` hiển thị thành card phụ cạnh milestone và click để chuyển sang module khác. Cả hai chỉ là navigation/support relation, không tạo nested curriculum graph và không thay đổi learning order của Level 1. Roadmap source/projection + frontend Roadmap tab đều đã implement và Roadmap vẫn đứng upstream của Menu/Knowledge theo `module-generate-agent/STEP_2_ROADMAP_REFERENCE.md`.

Visual contract hiện tại:

```text
milestone ở trái  ···  (marker pulse)  ···  Related Knowledge ở phải
Related Knowledge trái  ···  (marker pulse)  ···  milestone ở phải

Related Modules
→ satellite card nằm ngoài milestone card khi được khai báo
→ click sang module đích
```

`relatedKnowledge` resolve title + section count từ Knowledge index hiện hành; click item chuyển sang tab `Knowledge` và active category tương ứng, không reload module. Marker pulse là affordance thị giác, không còn là trigger mở popup.

Các tab/action về lâu dài phải được render theo capability thực tế của module.

Không nên tạo Spring Boot Application giả chỉ để module có thể hiển thị README hoặc quiz.

---

## 5. Navigation theo module hierarchy

Portal không hard-code các category như một website interview thông thường.

Navigation được sinh từ cùng source/model đang dùng để mô tả module hierarchy của repository.

Thứ tự sibling trong hierarchy được resolve ở build layer trước khi tới Portal. Parent directory có thể khai báo optional `module-order.yml` cho direct children; nếu không có file thì giữ ABC. Khi có file: `order` tăng dần, trùng `order` thì ABC, child không có order nằm cuối và cũng ABC. Portal không đọc file này và không sort lại; nó render đúng thứ tự đã được project vào `module-catalog.json`.

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

Catalog hiện đã advertise localized Overview/Roadmap/Knowledge/Quiz/Interview/API path dựa trên declared module capability + resource thực tế mà generator tìm thấy. Với Roadmap, catalog chỉ advertise language khi `roadmap.yml` thực sự có ít nhất một milestone, nên skeleton `roadmap: []` không được tính là available content. Một capability resolver tổng quát hơn cho mọi future artifact/runtime vẫn là target phase sau; không nhét fake availability vào catalog.

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

`SERVICE_NAME_DESCRIBE` hiện là description chính hiển thị ngay dưới tên module ở Learning header. Sidebar chỉ hiển thị tên menu ngắn gọn và capability badges; Roadmap dùng badge tròn `R` theo màu semantic của tab Roadmap, còn Knowledge/Quiz/Interview/API Docs dùng count badge. Không lặp lại detailed description dưới dạng tooltip.

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
thread.jar  label=THREAD · fingerprint:<THREAD tree SHA>
aspect.jar  label=ASPECT · fingerprint:<ASPECT tree SHA>
```

Khi mở tab Local Run, UI check Release Asset trước. Nếu filename tồn tại và label fingerprint khớp thì hiện `Download JAR` ngay. Nếu asset không có hoặc fingerprint khác thì hiện `Build JAR`. Workflow recompute tree SHA sau checkout và reject request nếu catalog fingerprint đã stale. Upload dùng stable filename + `--clobber`, vì vậy build THREAD chỉ thay `thread.jar`; `aspect.jar` không bị build/download lại. Current fingerprint scope là directory của chính module và chưa bao gồm transitive dependency/shared build-input closure.

`POST /api/local-run/build` không tin state UI cũ và luôn re-check server-side trước khi dispatch. Contract decision order:

```text
fresh Release Asset?
├── YES → result=AVAILABLE, status=SUCCESS, không dispatch
└── NO
    ↓
same module + fingerprint workflow đang active?
├── YES → result=REUSED, trả lại existing runId
└── NO  → result=DISPATCHED, tạo workflow run mới
```

Workflow dùng deterministic run name `Local Run <MODULE_ID> · fingerprint:<sourceFingerprint>`, nên local và production adapter cùng nhìn thấy chung một active build qua GitHub REST API mà không cần D1/database. Nhiều browser cùng request một version sẽ reuse cùng run; nếu một user đã build xong asset trước khi browser khác bấm Build thì request sau nhận `AVAILABLE` và chuyển thẳng sang Download. `concurrency` theo module vẫn là safety net cho residual race window và để fingerprint mới có thể thay thế build stale cũ.

Release Asset là raw JAR, không phải Actions Artifact ZIP. Production Pages Function không tải toàn bộ ZIP vào memory và không unzip JAR; nó chỉ trả `browser_download_url` của Release Asset. Điều này loại bỏ large-file proxy path từng có nguy cơ gây 502 trên Worker.

Execution Context tiếp tục là capability độc lập và giữ ownership runtime/capture/query/store riêng; Local Run không duplicate hoặc phụ thuộc vào Execution Context.

---

## 14. Download

Không còn `Download` action ở thanh module tabs tổng. Trong API Docs, nút `Download` là shortcut chuyển sang tab `Local Run`; chính `Local Run` sở hữu luồng build/download executable JAR thật. API Docs không mở placeholder popup riêng.

Ví dụ nội dung download theo context:

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
java --enable-preview -jar thread.jar
```

`Local Run` dùng Java 21 làm runtime baseline và luôn truyền `--enable-preview`.
Flag này là bắt buộc cho runnable module được compile bằng Java 21 preview
feature/API; module không dùng preview vẫn có thể chạy bằng cùng command.
Các quyền native như `--enable-native-access=...` không được cấp globally;
module thực sự gọi restricted native operation phải document quyền đó ở
learning/runtime surface của chính module.

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

**Production ownership đã chuyển sang repository độc lập `learning-platform`.**
Custom domain `nguyendodinhphuong.name.vn` hiện phục vụ React/Vite app từ
Cloudflare Workers `learning-platform`, không được deploy từ `java-learning`.

Workflow `.github/workflows/deploy-portal.yml` (trước đây chạy khi push/merge
`main` hoặc `workflow_dispatch` và upload lên Cloudflare Pages project
`java-learning`) đã được **disable trên GitHub và xóa khỏi repository này**.
Các lần push/merge Java tiếp theo không được build/deploy Portal production.
`local-run-build.yml` là workflow riêng, vẫn được giữ lại cho khả năng build JAR.

Phần Spring Boot + React legacy trong `project-portal/` vẫn giữ nguyên để phục
vụ local build/test và các Gradle projection generator. Dữ liệu Java trong
`learning-platform` hiện là static snapshot đã copy; pipeline xuất bản/sync
artifact tự động giữa hai repository là một công việc riêng chưa triển khai.
Giữ các Gradle generator Linux-safe về filename casing khi xây dựng pipeline
data export mới.

---

## 16. Route / context path

Portal là root-level/common entry point, độc lập với context path của từng learning module runtime.

Current routing dùng `BrowserRouter`:

```text
/
/about-me
/my-cv                     → legacy redirect /about-me
/learning
/project                   → Domain cards trực tiếp, không cần chọn language
/learning/java               → redirect /learning/java/knowledge
/learning/java/knowledge     → Java module learning, chọn module đầu khi chưa có ID
/learning/java/knowledge/THREAD
/learning/java/knowledge/ASPECT
/learning/java/project       → legacy redirect /project (không render Java subnav)
/learning/THREAD             → legacy redirect sang /learning/java/knowledge/THREAD
```

Logical `SERVICE_NAME` đang là lựa chọn phù hợp cho module route identity trong fake/current phase.

Spring MVC có SPA fallback explicit cho các client route hiện tại:

```text
/about-me
/my-cv
/learning
/project
/learning/{moduleId}
/learning/java
/learning/java/knowledge
/learning/java/knowledge/{moduleId}
/learning/java/project
```

Các route trên (cùng trailing slash tương ứng) forward nội bộ về `/index.html`; đây không phải redirect về `/`, nên refresh `/about-me` vẫn giữ URL `/about-me` và React Router tiếp tục render `AboutMePage`. `/my-cv` cũ client-redirect tới `/about-me`; `/learning/java/project` cũ redirect tới `/project`; cả hai đều giữ query/hash. Với `/learning/{moduleId}` cũ, React Router thực hiện client redirect sang Java Knowledge và giữ query/hash để không phá bookmark tới Knowledge section. Static assets, generated Portal data và `/api/**` không đi qua fallback này. Static production host cũng phải giữ contract SPA fallback tương đương cho các clean client routes.

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
GROUP non-terminal hoặc mixed GROUP/MODULE
→ click toàn row để expand/collapse
→ `+` = collapsed, `−` = expanded
→ hover chạy directional wave dọc: đóng thì xuống, mở thì lên
→ nếu mixed GROUP/MODULE thì direct leaf MODULE được UI wrap thành fake menu 1-item picker; catalog thật không thay đổi

GROUP terminal có toàn bộ direct children là leaf MODULE
→ không render các MODULE con inline trong sidebar
→ hover/focus mở Module Picker; click pin/unpin picker
→ click ngoài hoặc Escape đóng; toàn sidebar chỉ có một picker mở tại một thời điểm
→ module trong picker giữ catalog order và luôn đánh số 1, 2, 3... kể cả khi parent không có `module-order.yml`
→ default tree vẫn expanded đối với các branch bình thường

MODULE có children
→ không còn split row vừa expand vừa navigate
→ row gốc được render như structural parent thuần
→ UI chèn một fake child cùng tên ở đầu branch; fake child mở 1-item picker chứa chính MODULE gốc
→ fake child chỉ là presentation wrapper, không tạo catalog node mới và không làm thay đổi module count

MODULE chỉ có dashboard
→ click toàn row để navigate tới module page
→ hover chạy sweep trái → phải + `›››`

MODULE vừa có children vừa có dashboard
→ dấu `+`/`−` là boundary giữa hai vùng click
→ từ boundary về trái: expand/collapse
→ từ boundary sang phải: navigate dashboard
→ hover ở bất kỳ đâu trên row kích hoạt đồng thời wave dọc bên trái và wave ngang bên phải
→ mỗi wave bị clip trong đúng vùng của nó, không tràn qua boundary

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
→ giữ MODULE nếu Roadmap available OR Knowledge > 0 OR Quiz > 0 OR Interview > 0 OR API Docs > 0
→ Roadmap chỉ available khi localized `roadmap.yml` có ít nhất một milestone
→ chỉ ẩn MODULE khi Roadmap không available và cả bốn count đều = 0
→ giữ GROUP ancestor cần thiết để bảo toàn hierarchy
→ prune branch không chứa module còn content
→ không flatten thành list
```

MODULE đạt điều kiện `Module thật` **không có permanent background riêng**. Badge `R` của Roadmap cùng Knowledge/Quiz/Interview/API count badges là tín hiệu content availability; background persistent chỉ dành cho active/selected module. Directional hover color/wave chỉ xuất hiện khi pointer/focus đi vào interactive row.

Toàn sidebar có thể đóng/mở độc lập với tree branch state. Control đóng sidebar nằm giữa chiều cao ở mép phải sidebar; khi sidebar bị ẩn, main content giãn ra và một control `>` ở giữa mép trái màn hình mở sidebar lại. Desktop sidebar ưu tiên đủ rộng để tên menu hiển thị đầy đủ thay vì ellipsis. Header module hiển thị thêm description từ `SERVICE_NAME_DESCRIBE`; sidebar chỉ giữ tên menu ngắn gọn thay vì tooltip/detail description.

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
                         ▼
                    Overview
                         │
                         ▼
                       Menu
                         │
                         ▼
                     Roadmap
                         │
                         ▼
                    Knowledge
                         │
             ┌───────────┼───────────┐
             ▼           ▼           ▼
         API Docs      Quiz      Interview
             │
             ▼
      module runnable?
          /      \
        yes      no
         │        │
         ▼        │
     Local Run    │
         │        │
         └───┬────┘
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
9. real module hierarchy sidebar + dynamic `/learning/java/knowledge/{routeId}` routing (vẫn hỗ trợ redirect từ legacy `/learning/{routeId}`)
10. sidebar module search với self-match/descendant-match semantics
11. Full tree / Real modules switch, prune nhưng giữ ancestor hierarchy
12. sidebar interaction theo capability: child-only row toggle toàn row, dashboard-only module navigate toàn row, hybrid row chia click tại `+`/`−`; hover hybrid kích hoạt đồng thời hai directional waves nhưng mỗi wave bị clip đúng vùng; không còn circular `>` trên từng module
13. Overview từ language `BASE.md` qua build-time projection
14. build-only module-first Portal data layout: `portal-data/module/{ROUTE_ID}/{feature}/...`
15. build-time Knowledge projection theo category + anchored section
16. localized `knowledge-metadata.yml` + explicit `syncMetadataReadme` quản lý difficulty/AI provenance/review state cho exact README section; Portal projection đưa metadata này vào Knowledge index
17. Menu + Knowledge frontend consume generated Knowledge index/section Markdown; section content được lazy-load khi mở, nhiều Knowledge section có thể mở đồng thời, category strip hỗ trợ arrow-scroll + drag-scroll
18. build-time API metadata projection: copy complete localized four-file Swagger sets vào `module/{ROUTE_ID}/api/{lang}/` + expose `api` base path trong module catalog
19. API Docs frontend consume trực tiếp generated Swagger YAML; controller/method order follow README mapping, rich execution HTML được sanitize, API Reference là reference-only chứ không live execute/debug
20. API methods có human-owned `aiGenerated/reviewed`; Knowledge/API Docs/Related API popup render shared governance badge + tooltip; API ↔ Knowledge tiếp tục dùng duy nhất `readmeRelated`
21. Knowledge/Quiz/Interview/API counts đều lấy từ generated/static data; generated catalog expose localized Roadmap path chỉ khi roadmap source có milestone. Sidebar `Module thật` giữ module khi Roadmap available hoặc ít nhất một trong bốn count > 0
22. sidebar tree mặc định expanded, dùng `+`/`−`, có Expand all/Collapse all; qualifying content module nhận biết bằng badge Roadmap `R` + Knowledge/Quiz/Interview/API count badges thay vì permanent background, active module mới giữ selected highlight; whole sidebar có thể collapse/reopen từ control giữa cạnh màn hình
23. Menu mặc định collapsed + Expand all/Collapse all; Knowledge/API Docs/Menu/Quiz/Interview giữ state gần nhất khi đổi tab rồi quay lại trong cùng module/language
24. Download action trong API Docs chuyển trực tiếp sang `Local Run`; không còn placeholder popup `Download chưa có artifact`
25. Home là placeholder có CTA sang Learning; Knowledge category filter có collapse/expand + horizontal drag-scroll
26. semantic capability colors dùng chung cho sidebar/tabs và giữ nguyên giữa Light/Dark
27. `Local Run` là label hiện tại của internal `execution` tab; frontend luôn gọi relative `/api/local-run/*`; local dùng Spring Boot adapter còn production dùng Cloudflare Pages Functions adapter; cả hai dispatch/poll GitHub Actions và check rolling GitHub Release Asset theo module-scoped `sourceFingerprint`; không còn mock và hai môi trường không phụ thuộc nhau
28. production static bundle vẫn có thể được serve bởi Spring Boot khi chạy packaged application
29. Lịch sử: public static Portal từng được deploy lên Cloudflare Pages (`java-learning-cly.pages.dev`) bằng `.github/workflows/deploy-portal.yml` với Wrangler project name `java-learning`; workflow này đã nghỉ hoạt động sau khi production chuyển sang repo `learning-platform`
30. Gradle plugin stub generator đã Linux-safe về filename casing để CI không tạo duplicate plugin khác casing
31. Quiz source/generator đã migrate thật: `BUILD_QUIZ` → orchestration → localized `question.yml`; canonical schema drive localized comment + validation; Portal copy static projection, shuffle answer position một lần khi load và giữ stable answer identity để check đúng/sai. THREAD hiện có 52 câu VI và 52 câu EN dựa trên README, kèm governance + optional Knowledge/API relations
32. Interview source/generator đã migrate thật: `BUILD_INTERVIEW` → orchestration → localized `question.yml`; canonical schema drive localized comment + validation; Portal copy static projection, preload count và render reference answer collapsed/expandable với governance + Related Knowledge/API. THREAD hiện có 46 câu VI và 46 câu EN dựa trên README
33. top-level **module** tabs hiện theo thứ tự `Overview → Roadmap → Reference (nếu có) → Menu → Knowledge → API Docs → Quiz → Interview → Local Run (nếu runnable)`, với Feedback action riêng. Java section-level `Knowledge` nằm ngoài các module tabs này
34. Roadmap build projection đã implement: root task `generatePortalRoadmap` copy localized `roadmap/<lang>/roadmap.yml` của các module có `BUILD_ROADMAP=TRUE` vào `project-portal/build/generated/portal-data/module/{ROUTE_ID}/roadmap/<lang>/roadmap.yml`; `generatePortalData` đã include task này
35. Roadmap frontend đã implement: vertical center timeline, milestone card xen kẽ trái/phải, numbered ring marker, `relatedKnowledge` luôn hiển thị ở phía đối diện milestone, và `relatedModules` satellite cards nối dotted line; responsive layout collapse về single-column timeline trên màn hình nhỏ
36. Roadmap Related Knowledge prototype đã implement cho `JAVA_LANGUAGE_BASICS`: marker giữ pulse nhẹ nhưng không còn mở popup; Related Knowledge luôn visible, resolve category/count từ Knowledge index và click item chuyển sang Knowledge tab với đúng category active; current mapping chỉ là provisional mapping trên Knowledge cũ và không được coi là curriculum proof
37. Learning topic picker trên `/learning` hiện có Java/JavaScript/NodeJS; Java mở module UI cũ trong `/learning/java/knowledge`, các topic chưa sẵn sàng hiển thị localized animated dialog; Java section navigation chỉ còn Knowledge. Project độc lập tại `/project`, hiển thị ba interactive split-cards Banking/Logistics/E-commerce với gradient thông thường, flat-color hover reveal, alternating sides, responsive touch toggle và VI/EN. Spring Boot SPA fallback hỗ trợ deep-link mới và legacy module paths vẫn được client redirect.
38. Global header dùng brand theo route: `Java Learning` + Java SVG chỉ trong `/learning/java/**`; `Nguyen Do Dinh Phuong` màu xanh + icon cũ trên `/about-me`; `Learning Platform` + icon cũ trên các route khác. Topic picker có ba SVG Java/JavaScript/NodeJS từ local Devicon assets và license nội bộ.
39. Favicon và title trong tab trình duyệt được điều chỉnh theo route bởi `PageMetadata`: Java Knowledge/Project dùng logo Java, Home/Learning/My CV dùng favicon mặc định; title tương ứng với branding của từng trang.
40. Main navigation `My CV` đổi thành `About Me` trên `/about-me` (legacy `/my-cv` redirect). Download My CV hiện căn giữa với PDF và Word DOCX; Google Docs viewer mặc định đóng, mở/đóng qua nút VI/EN và hiển thị trong khung rộng 80%, cao cố định có scroll, responsive 100% trên màn nhỏ.
41. Primary header thêm `Project` ngay bên phải `Learning`, vào thẳng `/project` không chọn Java/JS/NodeJS. `ProjectPage.tsx` chỉ render trên `/project`; Java secondary nav chỉ còn Knowledge. Legacy `/learning/java/project` client-redirect sang `/project`, không render cards dưới Java; trang Project dùng eyebrow `Project` và bỏ dòng hướng dẫn hover/chạm; ba animated domain cards giữ nguyên.
42. Production Portal đã tách sang repo `learning-platform` và Cloudflare Workers; workflow GitHub Actions `deploy-portal.yml` trong `java-learning` được disable trên GitHub và xóa khỏi source để tránh deploy Portal cũ mỗi lần push Java. `local-run-build.yml` vẫn giữ nguyên; Java Portal data export/sync liên repo sẽ triển khai sau.
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
