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

Portal dùng React + TypeScript + Vite, theo hướng **static-first**. Module catalog, Overview, Knowledge, Quiz, Interview và API Docs đều được materialize ở build-time; `Local Run` là ngoại lệ dynamic, dùng backend server-side để build/download executable JAR qua GitHub Actions + rolling Release. Production deploy qua GitHub Actions lên Cloudflare Pages tại `https://java-learning-cly.pages.dev` (Wrangler project: `java-learning`).

Chi tiết Portal: [`PROJECT_PORTAL.md`](./PROJECT_PORTAL.md).

## Cách học một module

Với learning module, hãy **xem Roadmap trước** để hiểu thứ tự và quan hệ giữa các đầu mục lớn; sau đó mới dùng Menu/Knowledge để đi vào bài học chi tiết. Kiến trúc nội dung của project đi theo hướng **Roadmap first → Knowledge second → API/Quiz/Interview later**.

Chi tiết contract: [`MODULE_ROADMAP.md`](./MODULE_ROADMAP.md) và [`MODULE_LEARNING_AGENTS.md`](./MODULE_LEARNING_AGENTS.md).

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
- [`MODULE_ROADMAP.md`](./MODULE_ROADMAP.md) — roadmap-first learning architecture và source-of-truth pipeline.
- [`MODULE_LEARNING_AGENTS.md`](./MODULE_LEARNING_AGENTS.md) — workflow xây/review learning content của một module.
- [`PROJECT_PORTAL.md`](./PROJECT_PORTAL.md) — Portal hiện tại, data contract và deployment.
- [`STRUCTURE.md`](./STRUCTURE.md) — cây module generated.
