# Project Architecture Overview

## 1. Mục tiêu dự án

Đây là repository học tập dùng để hệ thống hóa kiến thức về Java, Spring Boot, hệ thống phân tán, hạ tầng và các công nghệ liên quan.

Kiến thức được chia thành các module nhỏ, mỗi module tập trung vào một mục tiêu học tập tương đối độc lập. Không phải mọi module đều là Spring Boot application.

Java mặc định của toàn dự án là **Java 21**.

---

## 2. Bức tranh kiến trúc tổng thể

```text
Root Project
│
├── project-build/
│   │
│   ├── gradle-runtime/
│   │   └── Build-time automation
│   │       ├── Gradle plugins
│   │       ├── setup logic
│   │       ├── tasks
│   │       ├── services
│   │       ├── utilities
│   │       ├── canonical schemas
│   │       └── generated build metadata
│   │
│   └── springboot-runtime/
│       └── Shared Spring Boot runtime configuration
│           ├── swagger
│           └── future shared runtime capabilities
│
├── project-orchestration/
│   └── Quyết định capability nào được áp dụng ở
│       Settings / Root / Module scope
│
├── module/
│   └── Các learning module
│
├── settings.gradle
├── build.gradle
└── STRUCTURE.md
```

Kiến trúc được chia rõ thành hai nhóm lớn:

- **Build-time**: logic phục vụ Gradle, discovery, setup, generation và build automation.
- **Runtime**: code/config thực sự được sử dụng khi Spring Boot application chạy.

Hai nhóm này được tách riêng để application runtime không phụ thuộc trực tiếp vào Gradle implementation.

---

## 3. Cấu trúc repository

### Root project

Root project giữ các policy chung và đóng vai trò entry point của repository.

Các file quan trọng:

- `settings.gradle`: bootstrap composite build và settings orchestration.
- `build.gradle`: global build policy.
- `STRUCTURE.md`: tài liệu cấu trúc module được sinh tự động.
- `module/`: cây learning module chính.

Root build script được giữ tương đối mỏng; implementation chi tiết được đẩy xuống build infrastructure.

### `project-build/gradle-runtime`

Đây là Gradle build chứa build logic của repository.

Nó chịu trách nhiệm cho các nhóm chức năng như:

- module discovery;
- metadata synchronization;
- module/root/settings setup;
- generated catalogs;
- generated documentation;
- dependency support;
- cleanup utilities;
- ENV/YML/README/Swagger build automation;
- database build support;
- các Gradle task dùng chung.

Canonical configuration schema hiện nằm tại:

```text
project-build/gradle-runtime/src/main/resources/automation/
├── master.json
└── properties.json
```

Generated text structure hiện nằm tại:

```text
project-build/gradle-runtime/src/main/resources/structure/module-structure.txt
```

### `project-build/springboot-runtime`

Đây là vùng dành cho các capability dùng ở **Spring Boot runtime**.

Ví dụ hiện tại:

```text
springboot-runtime
└── swagger
```

Về sau khu vực này có thể chứa thêm các shared runtime capability như logging, security, Jackson configuration hoặc các cấu hình Spring Boot dùng chung khác.

### `project-orchestration`

Chứa các plugin điều phối chính:

```text
com.example.settings-orchestration
com.example.root-orchestration
com.example.module-orchestration
```

Orchestration quyết định **capability nào được áp dụng**, còn implementation của capability nằm trong build logic.

---

## 4. Mô hình module

Một **real module** là thư mục có file `gradle.properties` riêng.

```text
có local gradle.properties
        ↓
     real module
```

`gradle.properties` ở root không được coi là module.

Các thư mục trung gian chỉ dùng để tổ chức hierarchy nhưng không có `gradle.properties` được xem là **intermediate project**.

### Module type

Module hiện được phân loại bằng `MODULE_TYPE`.

#### `APPLICATION`

- Có application entry point.
- Có thể chạy độc lập.
- Thường tạo Spring Boot executable JAR.
- Có thể sử dụng shared runtime capabilities.

#### `LIBRARY`

- Chứa code hoặc cấu hình dùng lại.
- Không có entry point độc lập.
- Được module khác sử dụng thông qua dependency.

#### `PLATFORM`

- Chủ yếu chứa configuration, resource hoặc platform concern.
- Không nhất thiết có Java source.
- Không nhất thiết tạo executable artifact.

---

## 5. Module contract

Một real module có thể chứa:

```text
<module>/
├── gradle.properties
├── master.json
├── properties.json
├── build.gradle
├── task.gradle
├── src/
└── readme/
```

Không phải file nào cũng bắt buộc tồn tại trong mọi module.

Vai trò chính:

- `gradle.properties`
    - nhận diện real module;
    - chứa Gradle properties trực tiếp.

- `master.json`
    - metadata chính;
    - `MODULE_TYPE`;
    - `SERVICE_NAME`;
    - feature flags.

- `properties.json`
    - cấu hình chi tiết cho các feature đang bật.

- `build.gradle`
    - dependency và cấu hình riêng của module.

- `task.gradle`
    - danh sách task được sinh khi module bật cơ chế task.

---

## 6. Metadata model

Canonical schema được quản lý tại:

```text
project-build/gradle-runtime/src/main/resources/automation/master.json
project-build/gradle-runtime/src/main/resources/automation/properties.json
```

Các feature flag quan trọng gồm:

```text
BUILD_ENV
BUILD_YML
BUILD_README
BUILD_TESTER
BUILD_SWAGGER
BUILD_DATABASE_MODULE
ADD_MODULE_DEPEND
USE_DATABASE
USE_TASK
```

Khi đồng bộ metadata:

- `VALUE` của module được xem là dữ liệu do người dùng/module sở hữu và được giữ lại nếu hợp lệ.
- Schema, description, type, group và các metadata còn lại đến từ canonical schema.
- Generated output chỉ được rewrite khi nội dung thực sự thay đổi.

---

## 7. Gradle orchestration

### Settings scope

Root `settings.gradle` sử dụng composite build để nạp:

```text
project-build/gradle-runtime
project-orchestration
```

Settings orchestration chịu trách nhiệm cho các bước xảy ra trước khi Gradle cấu hình toàn bộ project graph, ví dụ:

- scan real module;
- include module;
- synchronize module metadata;
- tạo một số generated catalog cần ở settings/build phase.

### Root scope

Root `build.gradle` áp dụng root orchestration.

Root-level capability hiện gồm các nhóm như:

- dependency catalog;
- project structure;
- database support;
- cleanup support.

Root cũng quản lý các policy chung như:

- `group`;
- `version`;
- Java 21;
- JUnit Platform;
- repositories;
- Spring Cloud BOM.

### Module scope

Chỉ subproject có local `gradle.properties` mới được áp dụng module orchestration.

Module orchestration chịu trách nhiệm:

- base dependency/setup;
- module type;
- YML setup;
- ENV;
- README;
- Swagger;
- task;
- database;
- module dependency;
- Docker setup khi module có `docker-compose.yml`.

Module `build.gradle` chỉ nên chứa dependency hoặc cấu hình đặc thù của chính module.

---

## 8. Plugin, Service, Task và Utility

Build logic tuân theo separation of responsibility:

```text
Plugin
→ wiring / lifecycle / WHEN

Service
→ use case / HOW

Task
→ explicit execution entry

Utility
→ stateless reusable primitive

Orchestration
→ policy / WHICH capability
```

Mục tiêu là tránh đưa business/build implementation trực tiếp vào root `build.gradle` hoặc `settings.gradle`.

---

## 9. Build-time và Runtime

Đây là một ranh giới quan trọng của kiến trúc.

### Build-time

Thuộc:

```text
project-build/gradle-runtime
```

Ví dụ:

- scan module;
- generate metadata;
- configure Gradle project;
- generate README/YML/ENV;
- generate structure;
- dependency catalog;
- build task;
- cleanup task.

### Runtime

Thuộc:

```text
project-build/springboot-runtime
```

Ví dụ:

- Swagger/Springdoc runtime configuration;
- shared Spring Boot beans;
- future logging/security/Jackson runtime configuration.

Rule cơ bản:

```text
Spring Boot runtime code
không phụ thuộc ngược vào
Gradle build implementation.
```

---

## 10. Dependency direction

Ở mức đơn giản, dependency direction có thể hình dung như sau:

```text
settings.gradle
      ↓
Settings Orchestration
      ↓
Gradle Runtime


build.gradle
      ↓
Root Orchestration
      ↓
Gradle Runtime


Real Module
      ↓
Module Orchestration
      ↓
Gradle Runtime capabilities


Application Module
      ↓
Shared Runtime Capability
      ↓
springboot-runtime
```

Module dependency giữa các learning module được quản lý thông qua metadata và build logic thay vì để mỗi module tự triển khai lại cùng một cơ chế.

---

## 11. Generated catalogs

Build process tạo một số typed/generated catalog để tránh việc scan và parse cùng một metadata lặp đi lặp lại.

### `ModuleListEnum`

Package hiện tại:

```text
com.example.learning.generated.settings.ModuleListEnum
```

Vai trò:

- registry của real module;
- định danh module bằng `SERVICE_NAME`;
- cung cấp metadata như module path, relative path, module type và module dependency information.

### `DatabaseListEnum`

Package hiện tại:

```text
com.example.learning.generated.settings.DatabaseListEnum
```

Vai trò:

- registry của database module;
- lưu database type;
- module name;
- module path.

Các enum này là **generated projection**, không phải source of truth ban đầu của module metadata.

---

## 12. Human-owned và Generated files

Việc phân biệt ownership của file là một rule quan trọng.

### Human-owned / module-owned

Ví dụ:

```text
gradle.properties
VALUE trong master.json
VALUE trong properties.json
module build.gradle
source code
README nội dung học tập
```

### Build-owned canonical definitions

```text
project-build/gradle-runtime/src/main/resources/automation/master.json
project-build/gradle-runtime/src/main/resources/automation/properties.json
```

### Generated artifacts

Ví dụ:

```text
ModuleListEnum
DatabaseListEnum
module-depend.json
STRUCTURE.md
module-structure.txt
```

Generated artifact phải:

- deterministic;
- idempotent;
- không rewrite khi content không đổi;
- không được coi là nguồn dữ liệu chính nếu nó được sinh từ nguồn khác.

---

## 13. Generated project structure

Filesystem dưới `module/` cùng metadata của module là nguồn để sinh project structure.

Hai representation chính:

```text
STRUCTURE.md
→ tài liệu Markdown dành cho người đọc

project-build/gradle-runtime/src/main/resources/structure/module-structure.txt
→ text snapshot của module tree
```

`STRUCTURE.md` chứa link tới các module để người đọc repository có thể duyệt project dễ dàng.

---

## 14. Ví dụ vòng đời của một module

Ví dụ:

```text
module/microservice/module/service/order-service/
```

với:

```text
SERVICE_NAME = ORDER_SERVICE
MODULE_TYPE  = APPLICATION
```

Flow khái quát:

```text
Filesystem
    ↓
Settings phase discovers gradle.properties
    ↓
Module metadata được synchronize
    ↓
Module được include vào Gradle project graph
    ↓
Module orchestration được apply
    ↓
APPLICATION setup được cấu hình
    ↓
Dependencies/resources/tasks được chuẩn bị
    ↓
Build / Test / Package
    ↓
Spring Boot application runtime
```

---

## 15. Learning architecture

Repository được tổ chức trước hết để phục vụ việc học.

Mỗi learning module hướng tới:

1. Một learning objective rõ ràng.
2. Kiến thức được tổ chức từ cơ bản đến nâng cao.
3. Source/example minh họa.
4. README giải thích.
5. Ví dụ thực hành.
6. Self-review questions.
7. Extended exercises.
8. Liên kết với prerequisite và related modules.

Các module có thể độc lập về mục tiêu học tập nhưng vẫn chia sẻ build infrastructure và runtime capabilities dùng chung.

---

## 16. Các quyết định kiến trúc quan trọng

Một số decision quan trọng giúp hiểu nhanh repository:

- Root Gradle files được giữ mỏng.
- Build implementation được tách khỏi orchestration.
- Build-time và Spring Boot runtime được tách thành các artifact/vùng trách nhiệm khác nhau.
- Real module được nhận diện bằng local `gradle.properties`.
- Metadata dùng canonical schema và sync xuống module.
- Generated artifact phải deterministic và idempotent.
- `SERVICE_NAME` là định danh logic quan trọng của module.
- Shared build behavior không nên bị copy vào từng module.
- Shared runtime configuration không nên bị nhét vào Gradle plugin artifact.
- Intermediate project chỉ dùng để tổ chức hierarchy và không được xử lý như real module.

---

## 17. Cách đọc project nhanh

Nếu mới tiếp cận repository, nên đọc theo thứ tự:

1. `settings.gradle`
2. `build.gradle`
3. `project-orchestration`
4. `project-build/gradle-runtime`
5. canonical `master.json` và `properties.json`
6. `STRUCTURE.md`
7. chọn một real module cụ thể
8. đọc metadata và `build.gradle` của module đó
9. cuối cùng mới đi sâu vào source code của module

Cách đọc này giúp hiểu architecture trước khi bị cuốn vào implementation detail.

---

## 18. Tài liệu dành cho người đọc sâu hơn

File này chỉ là architecture overview dành cho:

- interviewer;
- junior developer;
- người mới vào project;
- người cần hiểu repository trong thời gian ngắn.

Các rule chi tiết hơn về:

- dependency direction;
- Gradle lifecycle;
- generated artifacts;
- source of truth;
- architecture invariants;
- governance;
- ADR;
- technical debt;
- roadmap;

nên được mô tả trong tài liệu architecture đầy đủ của repository.
