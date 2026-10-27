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

**Java data:** relevant changes pushed to `main` (module content or Local Run fingerprint inputs) → [`publish-java-data.yml`](.github/workflows/publish-java-data.yml) runs `./gradlew generatePortalData` (dataset generator, not a portal application) → validates and compares all generated asset bytes with the checksummed rolling Release, ignoring commit-only ZIP metadata. If unchanged, stop without release upload or frontend dispatch. Only changed output is packaged and published to Release `java-data`, re-downloaded/verified, then dispatched to `learning-platform` for CI/test/build/deploy. The generated `module-structure.txt` is excluded from change detection and Local Run fingerprints. After an unsuccessful downstream deployment, rerun the failed `learning-platform` job or manually invoke the publisher with `force_publish=true` to verify/publish/dispatch again.

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
- [`STEP_1.md`](./module-generate-agent/STEP_1.md) — chuẩn bị Google Sheets cho module.
- [`STEP_2.md`](./module-generate-agent/STEP_2.md) — Roadmap và Introduction.
- [`STEP_3.md`](./module-generate-agent/STEP_3.md) — Menu.
- [`STEP_4.md`](./module-generate-agent/STEP_4.md) — Knowledge.
- [`STEP_5.md`](./module-generate-agent/STEP_5.md) — review Knowledge, metadata và Java chapter skeleton.
- [`STEP_6.md`](./module-generate-agent/STEP_6.md) — Java API và tests.
- [`STEP_7.md`](./module-generate-agent/STEP_7.md) — Swagger VI/EN theo chương.
- **STEP 8** — Chưa định nghĩa, để dành cho bước mới.
- [`STEP_9.md`](./module-generate-agent/STEP_9.md) — Interview + Summary Questions YAML, import thành hai file riêng trong `questions/`.
- [`STEP_10.md`](./module-generate-agent/STEP_10.md) — Quiz YAML.
- [`STEP_11.md`](./module-generate-agent/STEP_11.md) — Related metadata cho Swagger, Quiz, Interview theo chương và Roadmap toàn module.
- [`STEP_12.md`](./module-generate-agent/STEP_12.md) — Xuất snapshot Google Sheets của module thành Excel offline.
- [`STEP_13.md`](./module-generate-agent/STEP_13.md) — Clone sáu vùng VI → EN, loại trừ Video.
- [`STEP_14.md`](./module-generate-agent/STEP_14.md) — Dịch tài liệu VI → EN, không dịch Video.
- [`STEP_15.md`](./module-generate-agent/STEP_15.md) — Publish dữ liệu EN và Excel, loại trừ Video.
- [`STEP_FINAL.md`](./module-generate-agent/STEP_FINAL.md) — Prime QA Git, commit và push toàn bộ thay đổi thuộc module sau STEP cuối.
- [`STEP_OPTIONAL_VIDEO.md`](./module-generate-agent/STEP_OPTIONAL_VIDEO.md) — Tùy chọn sau FINAL: tạo/import Video VI (Prime + Worker).
- [`STEP_OPTIONAL_VIDEO_TRANSLATE.md`](./module-generate-agent/STEP_OPTIONAL_VIDEO_TRANSLATE.md) — Tùy chọn sau Video VI: dịch và đồng bộ Video EN (Prime + Worker).
- [`worker/`](./module-generate-agent/worker/) — hướng dẫn dành riêng cho worker của từng STEP.
- [Learning Platform — Product & Frontend Architecture](https://github.com/phuongnguyenit93/learning-platform/blob/main/ARCHITECTURE.md) — Portal hiện tại, data contract và deployment ở repository frontend riêng.
- [`STRUCTURE.md`](./STRUCTURE.md) — cây module generated.
