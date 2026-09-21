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

Portal dùng React + TypeScript + Vite, theo hướng **static-first**. Module catalog, Overview, Knowledge và API Docs được materialize ở build-time; Quiz vẫn là fixture tạm. Production được deploy qua GitHub Actions lên Cloudflare Pages tại `https://java-learning-cly.pages.dev` (Wrangler project: `java-learning`).

Chi tiết Portal: [`PROJECT_PORTAL.md`](./PROJECT_PORTAL.md).

## Bắt đầu với module mới

1. Tạo module dưới `module/` với local `gradle.properties`.
2. Reload Gradle để build system nhận diện và sync metadata.
3. Cấu hình `MODULE_TYPE`, `SERVICE_NAME`, `JAVA_BASE_PACKAGE`, `MODULE_LANGUAGE` và các capability cần dùng trong `master.json`.
4. Reload/run explicit tasks cần thiết; không chỉnh generated output để thay đổi source configuration.

## Tài liệu

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — kiến trúc chi tiết và ownership/lifecycle.
- [`AGENTS.md`](./AGENTS.md) — working rules/context cho AI và contributor.
- [`PROJECT_PORTAL.md`](./PROJECT_PORTAL.md) — Portal hiện tại, data contract và deployment.
- [`STRUCTURE.md`](./STRUCTURE.md) — cây module generated.
