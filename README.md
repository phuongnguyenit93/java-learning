# Java Learning Repository

Repository học tập dùng để hệ thống hóa kiến thức về **Java, Spring Boot, hệ thống phân tán, database, build tooling và infrastructure**.

Mỗi chủ đề được tách thành các module nhỏ để dễ học, chạy thử và mở rộng.

## Tech baseline

- Java 21
- Spring Boot 3.3.x
- Gradle multi-module / composite build
- JUnit Platform
- Docker khi module cần runtime infrastructure

## Cấu trúc chính

```text
.
├── module/                         # Learning modules
├── project-build/
│   ├── gradle-runtime/             # Gradle build logic & automation
│   └── springboot-runtime/         # Shared Spring Boot runtime config
├── project-orchestration/          # Settings / Root / Module orchestration
├── settings.gradle
├── build.gradle
└── STRUCTURE.md                    # Generated module map
```

## Module types

Module được phân loại bằng `MODULE_TYPE`:

- `APPLICATION` — ứng dụng có thể chạy độc lập.
- `LIBRARY` — code/config dùng lại.
- `PLATFORM` — configuration/resource/platform module.

Một **real module** được nhận diện bằng local `gradle.properties`.

## Build architecture

Build logic được tách theo trách nhiệm:

```text
Plugin        → wiring / lifecycle
Service       → implementation logic
Task          → explicit execution entry
Utils         → stateless reusable logic
Orchestration → chọn capability được áp dụng
```

Ba orchestration plugin chính:

```text
com.example.settings-orchestration
com.example.root-orchestration
com.example.module-orchestration
```

## Module metadata

Canonical schema:

```text
project-build/gradle-runtime/src/main/resources/automation/
├── master.json
└── properties.json
```

Một module có thể có:

```text
gradle.properties
master.json
properties.json
build.gradle
task.gradle
src/
readme/
```

`master.json` chứa metadata và feature flags; `properties.json` chứa cấu hình chi tiết cho các feature đang bật.

## Build-time vs Runtime

```text
project-build/gradle-runtime
→ Gradle/build-time automation

project-build/springboot-runtime
→ shared Spring Boot runtime configuration
```

Runtime code không phụ thuộc ngược vào Gradle implementation.

## Generated artifacts

Một số artifact được sinh tự động:

- `ModuleListEnum`
- `DatabaseListEnum`
- `module-depend.json`
- `STRUCTURE.md`
- `module-structure.txt`

Generated output cần deterministic, idempotent và chỉ rewrite khi nội dung thay đổi.

## Cách khám phá project

Nếu mới vào repository:

1. Đọc `settings.gradle`.
2. Đọc root `build.gradle`.
3. Xem `STRUCTURE.md`.
4. Chọn module cần học.
5. Đọc metadata + `build.gradle` của module.
6. Sau đó mới đi sâu vào source code.

## Tài liệu

- `ARCHITECTURE_OVERVIEW.md` — kiến trúc ngắn gọn cho người mới/interviewer.
- `ARCHITECTURE.md` — kiến trúc chi tiết.
- `AGENTS.md` — quy tắc làm việc cho Codex/AI agents.
- `STRUCTURE.md` — sơ đồ module được generate.

## Mục tiêu

Repository ưu tiên:

- học theo module nhỏ;
- tái sử dụng build/runtime infrastructure;
- giữ module độc lập về mục tiêu học tập;
- tránh duplicate configuration;
- dễ mở rộng thêm module và capability mới.
