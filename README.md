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

GitHub Release java-data        # published learning dataset
    ↓
learning-platform (repo riêng)  # React + Cloudflare Worker website
```

Một **real module** được nhận diện bằng local `gradle.properties`. Metadata/capability chính nằm trong generated/synchronized `master.json`; `MODULE_LANGUAGE` là source of truth chung cho language của README, Swagger, Portal projection và runtime language metadata.

Build convention:

```text
Orchestration → chọn capability
Plugin        → wiring/lifecycle
Service       → implementation
Task          → explicit execution
```

## Learning Platform — website độc lập

Repository `java-learning` **không còn chứa Spring Boot/React application `project-portal/`**. Website được duy trì riêng tại [phuongnguyenit93/learning-platform](https://github.com/phuongnguyenit93/learning-platform) và chạy trên [Cloudflare Workers](https://nguyendodinhphuong.name.vn).

**Java data:** push `main` → [`publish-java-data.yml`](.github/workflows/publish-java-data.yml) chạy `./gradlew generatePortalData` (task này vẫn là generator dataset, không phải ứng dụng portal) → validate/package ZIP + SHA-256 → GitHub Release `java-data` → `repository_dispatch` sang `learning-platform` → CI/test/build/deploy website.

**Local Run:** khi người dùng nhấn Build JAR trên website, Cloudflare Worker/SQLite Durable Object validate module/fingerprint, chống dispatch trùng và giới hạn request → dispatch [`local-run-build.yml`](.github/workflows/local-run-build.yml) tại repo Java → GitHub Actions setup JDK 21, chạy `<resolved-module>:bootJar`, xác minh fingerprint V2 và publish executable JAR lên rolling Release `local-run`. Frontend kiểm tra trạng thái và tải file từ GitHub Release. Không còn Java backend hoặc Cloudflare Pages Functions adapter cũ.

**Ownership:** `java-learning` sở hữu module/Gradle, static content producer và workflow build JAR; `learning-platform` sở hữu UI, Cloudflare API/DO, chống Build trùng, pipeline deploy website. Đọc thêm [Learning Platform Architecture](https://github.com/phuongnguyenit93/learning-platform/blob/main/ARCHITECTURE.md).

## Cách học một module

Với learning module, hãy **xem Roadmap trước** để hiểu thứ tự và quan hệ giữa các đầu mục lớn; sau đó mới dùng Menu/Knowledge để đi vào bài học chi tiết. Portal đặt `Roadmap` ngay kế bên `Menu`: milestone nằm trên timeline chính, `relatedKnowledge` luôn hiển thị ở phía đối diện để nhảy vào Knowledge category cùng module, còn `relatedModules` là cross-module navigation. Hai relation này không thay đổi learning order. Kiến trúc nội dung của project đi theo hướng **Roadmap first → Knowledge second → API/Quiz/Interview later**.

Chi tiết workflow: [`GENERAL_AGENT_RULES.md`](module-generate-agent/GENERAL_AGENT_RULES.md) và canonical rules trong `module-generate-agent/`.

## Bắt đầu với module mới

1. Tạo module dưới `module/` với local `gradle.properties`.
2. Reload Gradle để build system nhận diện và sync metadata.
3. Cấu hình `MODULE_TYPE`, `SERVICE_NAME`, `JAVA_BASE_PACKAGE`, `MODULE_LANGUAGE` và các capability cần dùng trong `master.json`.
4. Với learning content, thiết kế và review **Module Roadmap trước README/Knowledge**.
5. Từ roadmap đã duyệt mới tạo Knowledge Menu/lesson; API Docs, Video, Quiz và Interview đi sau Knowledge.
6. Reload/run explicit tasks cần thiết; không chỉnh generated output để thay đổi source configuration.

## Tài liệu

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — kiến trúc chi tiết và ownership/lifecycle.
- [`AGENTS.md`](./AGENTS.md) — working rules/context cho AI và contributor.
- [`GENERAL_AGENT_RULES.md`](module-generate-agent/GENERAL_AGENT_RULES.md) — orchestrator/routing cho toàn bộ module-generation workflow.
- [`STEP_1_CURRICULUM_RULES.md`](./module-generate-agent/STEP_1_CURRICULUM_RULES.md) — Curriculum, scope, ownership và boundary.
- [`STEP_2_ROADMAP_REFERENCE.md`](./module-generate-agent/STEP_2_ROADMAP_REFERENCE.md) — canonical ROADMAP + module Reference architecture, contract và authoring/review rules.
- [`STEP_3_MENU.md`](./module-generate-agent/STEP_3_MENU.md) — Menu + title skeleton cho new module; refactor module skip step này.
- [`STEP_4_KNOWLEDGE.md`](./module-generate-agent/STEP_4_KNOWLEDGE.md) — Knowledge; với refactor module thì Menu + Knowledge được xử lý cùng nhau.
- [`STEP_5_API.md`](./module-generate-agent/STEP_5_API.md) — API learning documentation / experiments.
- [`STEP_6_VIDEO.md`](./module-generate-agent/STEP_6_VIDEO.md) — Video Script / Presentation Plan, one Knowledge Menu → one Video.
- [`STEP_7_QUIZ.md`](./module-generate-agent/STEP_7_QUIZ.md) — Quiz.
- [`STEP_8_INTERVIEW.md`](./module-generate-agent/STEP_8_INTERVIEW.md) — Interview.
- [`STEP_9_VALIDATION.md`](./module-generate-agent/STEP_9_VALIDATION.md) — integrated validation + Coverage Review.
- [Learning Platform — Product & Frontend Architecture](https://github.com/phuongnguyenit93/learning-platform/blob/main/ARCHITECTURE.md) — Portal hiện tại, data contract và deployment ở repository frontend riêng.
- [`STRUCTURE.md`](./STRUCTURE.md) — cây module generated.
