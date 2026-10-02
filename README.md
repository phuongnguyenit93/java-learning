# Java Learning Repository

Repository học tập/thử nghiệm với **Java 21, Spring Boot 3.3.x và Gradle multi-module/composite build**.

## Kiến trúc ngắn gọn

```text
settings.gradle
    ↓
project-orchestration            # WHAT / WHEN
    ↓
project-build/gradle-runtime     # build-time HOW
    ↓
module/                          # learning modules
    ↓
project-build/springboot-runtime # shared runtime HOW

project-portal/                  # root-level Learning Portal
```

Một **real module** được nhận diện bằng local `gradle.properties`. Metadata/capability chính nằm trong generated/synchronized `master.json`; `MODULE_LANGUAGE` là source of truth chung cho language của README, Swagger, Portal projection và runtime language metadata.

Build convention:

```text
Orchestration → chọn capability
Plugin        → wiring/lifecycle
Service       → implementation
Task          → explicit execution
```

## Project Portal

Portal dùng React + TypeScript + Vite, theo hướng **static-first**. Module catalog, Overview, Roadmap, Knowledge, Quiz, Interview và API Docs đều được materialize ở build-time; `Local Run` là ngoại lệ dynamic, dùng backend server-side để build/download executable JAR qua GitHub Actions + rolling Release. Production deploy qua GitHub Actions lên Cloudflare Pages tại `https://java-learning-cly.pages.dev` (Wrangler project: `java-learning`).

Chi tiết Portal: [`PROJECT_PORTAL.md`](./PROJECT_PORTAL.md).

## Cách học một module

Với learning module, hãy **xem Roadmap trước** để hiểu thứ tự và quan hệ giữa các đầu mục lớn; sau đó mới dùng Menu/Knowledge để đi vào bài học chi tiết. Portal đặt `Roadmap` ngay kế bên `Menu`: milestone nằm trên timeline chính, `relatedKnowledge` luôn hiển thị ở phía đối diện để nhảy vào Knowledge category cùng module, còn `relatedModules` là cross-module navigation. Hai relation này không thay đổi learning order. Kiến trúc nội dung của project đi theo hướng **Roadmap first → Knowledge second → API/Quiz/Interview later**.

Chi tiết workflow: [`GENARAL_AGENT_RULES.md`](./module-generate-agent/GENARAL_AGENT_RULES.md) và các canonical step rules trong `module-generate-agent/STEP_1_...` đến `STEP_8_...`.

## Bắt đầu với module mới

1. Tạo module dưới `module/` với local `gradle.properties`.
2. Reload Gradle để build system nhận diện và sync metadata.
3. Cấu hình `MODULE_TYPE`, `SERVICE_NAME`, `JAVA_BASE_PACKAGE`, `MODULE_LANGUAGE` và các capability cần dùng trong `master.json`.
4. Với learning content, thiết kế và review **Module Roadmap trước README/Knowledge**.
5. Từ roadmap đã duyệt mới tạo Knowledge Menu/lesson; API Docs, Quiz và Interview đi sau Knowledge.
6. Reload/run explicit tasks cần thiết; không chỉnh generated output để thay đổi source configuration.

## Tài liệu

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — kiến trúc chi tiết và ownership/lifecycle.
- [`AGENTS.md`](./AGENTS.md) — working rules/context cho AI và contributor.
- [`GENARAL_AGENT_RULES.md`](./module-generate-agent/GENARAL_AGENT_RULES.md) — orchestrator/routing cho toàn bộ module-generation workflow.
- [`STEP_1_CURRICULUM_RULES.md`](./module-generate-agent/STEP_1_CURRICULUM_RULES.md) — Curriculum, scope, ownership và boundary.
- [`STEP_2_ROADMAP.md`](./module-generate-agent/STEP_2_ROADMAP.md) — canonical ROADMAP architecture, contract và authoring/review rules.
- [`STEP_3_MENU.md`](./module-generate-agent/STEP_3_MENU.md) — Menu + title skeleton cho new module; refactor module skip step này.
- [`STEP_4_KNOWLEDGE.md`](./module-generate-agent/STEP_4_KNOWLEDGE.md) — Knowledge; với refactor module thì Menu + Knowledge được xử lý cùng nhau.
- [`STEP_5_API.md`](./module-generate-agent/STEP_5_API.md) — API learning documentation / experiments.
- [`STEP_6_QUIZ.md`](./module-generate-agent/STEP_6_QUIZ.md) — Quiz.
- [`STEP_7_INTERVIEW.md`](./module-generate-agent/STEP_7_INTERVIEW.md) — Interview.
- [`STEP_8_VALIDATION.md`](./module-generate-agent/STEP_8_VALIDATION.md) — integrated validation + Coverage Review.
- [`PROJECT_PORTAL.md`](./PROJECT_PORTAL.md) — Portal hiện tại, data contract và deployment.
- [`STRUCTURE.md`](./STRUCTURE.md) — cây module generated.
