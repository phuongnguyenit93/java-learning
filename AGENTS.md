# AGENTS.md

## Mục đích

File này là hướng dẫn làm việc dành cho Codex/AI agent khi đọc, phân tích hoặc chỉnh sửa repository.

Mục tiêu chính:

- hiểu đúng architecture hiện tại trước khi thao tác;
- tôn trọng ranh giới giữa build-time, runtime và learning module;
- không tự mở rộng phạm vi thay đổi;
- không coi generated artifact là source of truth;
- ưu tiên implementation thực tế khi documentation và source không đồng nhất;
- giữ mọi thay đổi nhỏ, có chủ đích và dễ review.

---

# 1. Nguyên tắc làm việc bắt buộc

## 1.1. Không tự ý chỉnh sửa

Khi người dùng chỉ yêu cầu:

- đọc;
- phân tích;
- review;
- tìm lỗi;
- giải thích;
- đề xuất architecture;
- đánh giá module;

thì chỉ báo cáo:

- hiện trạng;
- evidence;
- nguyên nhân;
- rủi ro;
- giải pháp đề xuất.

Không tự chỉnh sửa file.

Chỉ sửa khi người dùng yêu cầu rõ việc sửa trong câu lệnh hiện tại.

Nếu phạm vi sửa chưa rõ, phải hỏi lại trước khi thay đổi.

Không tự mở rộng việc sửa sang file khác chỉ vì thấy có thể refactor thêm.

---

## 1.2. Không tự chạy destructive/generative task

Không tự chạy task có khả năng:

- generate file;
- rewrite file;
- synchronize metadata;
- delete file/folder;
- migrate dữ liệu;
- thay đổi runtime state;
- chạy Docker operation;
- backup/restore database;
- update remote service;
- thay đổi generated source/resource.

Trước khi chạy phải:

1. giải thích task sẽ ảnh hưởng gì;
2. nêu các file/resource có thể thay đổi;
3. nhận xác nhận của người dùng.

Các task read-only như inspection có thể chạy nếu phù hợp với yêu cầu.

---

## 1.3. Evidence trước, suy luận sau

Khi mô tả architecture:

- ưu tiên source code;
- ưu tiên Gradle configuration;
- ưu tiên filesystem thực tế;
- ưu tiên canonical schema;
- ưu tiên behavior đang được implementation thực thi.

Nếu tài liệu và implementation không đồng nhất:

1. mô tả behavior thực tế;
2. chỉ rõ điểm khác biệt;
3. không tự sửa;
4. không giả định tài liệu là đúng hơn code.

Mọi kết luận chưa đủ evidence phải được đánh dấu là:

- `Target`;
- `Known Gap`;
- hoặc `Inference`.

Không viết target architecture như thể nó đã tồn tại.

---

# 2. Thứ tự đọc repository

Khi bắt đầu khảo sát repository hoặc một vấn đề architecture cấp project, đọc theo thứ tự:

1. root `settings.gradle`;
2. root `build.gradle`;
3. `project-orchestration`;
4. `project-build/gradle-runtime`;
5. canonical metadata schemas;
6. generated structure/catalog nếu cần;
7. metadata của module liên quan;
8. module `build.gradle`;
9. source code của module chỉ khi yêu cầu cần đến.

Không bắt đầu bằng việc đọc sâu toàn bộ source module nếu vấn đề chỉ thuộc build architecture.

---

# 3. Architecture overview

Repository được chia theo các responsibility chính:

```text
Root Project
│
├── project-build/
│   ├── gradle-runtime/
│   │   └── build-time automation
│   │
│   └── springboot-runtime/
│       └── shared Spring Boot runtime capability
│
├── project-orchestration/
│   └── orchestration / composition policy
│
└── module/
    └── learning modules
```

`project-build` là folder cha về mặt organization.

`project-build/gradle-runtime` là Gradle build độc lập và được include bằng composite build.

`project-build/springboot-runtime` chứa shared runtime code/config cho Spring Boot và không được trộn vào Gradle plugin artifact.

---

# 4. Build-time và Runtime boundary

## Build-time

Thuộc:

```text
project-build/gradle-runtime
```

Bao gồm các concern như:

- Gradle plugin;
- task;
- service;
- utility;
- module discovery;
- metadata synchronization;
- dependency setup;
- ENV/YML/README generation;
- Swagger build automation;
- structure generation;
- database build operations;
- cleanup;
- generated catalogs.

## Runtime

Thuộc:

```text
project-build/springboot-runtime
```

Bao gồm shared configuration/code cần khi Spring Boot application chạy.

Ví dụ:

```text
springboot-runtime/
└── swagger/
```

Có thể mở rộng trong tương lai bằng:

- logging;
- security;
- Jackson;
- common Spring configuration;
- các runtime concern dùng chung khác.

## Rule

Spring Boot runtime code không được phụ thuộc ngược vào Gradle build implementation.

Không đưa runtime Spring code vào cùng artifact với Gradle plugin chỉ để tái sử dụng.

---

# 5. Orchestration architecture

Ba orchestration plugin chính:

```text
com.example.settings-orchestration
com.example.root-orchestration
com.example.module-orchestration
```

Vai trò:

```text
Settings Orchestration
→ capability nào chạy trong Settings scope

Root Orchestration
→ capability nào chạy ở Root Project scope

Module Orchestration
→ capability nào chạy ở real module scope
```

Orchestration quyết định **WHAT/WHICH**.

Implementation cụ thể thuộc `project-build/gradle-runtime`.

Không đặt implementation logic lớn trực tiếp trong orchestration plugin.

---

# 6. Build logic responsibility

Convention chung:

```text
Plugin
→ wiring / lifecycle / WHEN

Service
→ use-case implementation / HOW

Task
→ explicit execution entry

Utils
→ stateless reusable primitive

Orchestration
→ composition / policy / WHICH
```

Khi thêm capability mới, ưu tiên giữ đúng separation này.

Không quay lại pattern legacy kiểu:

```groovy
ext.someMethod = { ... }
```

cho logic có behavior phức tạp.

---

# 7. Real Module

Một directory được xem là **real module** khi nó có local:

```text
gradle.properties
```

Rule:

```text
local gradle.properties
→ real module
```

Root `gradle.properties` không phải real module.

Directory trung gian chỉ để tổ chức hierarchy và không có local `gradle.properties` là **intermediate project**.

Không sử dụng `IS_MODULE` để xác định module.

`IS_MODULE` là convention cũ và không được tái sử dụng.

---

# 8. Module types

Module được phân loại bởi:

```text
MODULE_TYPE
```

Các loại hiện tại:

## APPLICATION

- có application entry point;
- có thể chạy độc lập;
- thường tạo executable Spring Boot JAR;
- có thể dùng shared runtime capability.

## LIBRARY

- code/config reusable;
- không có independent application entry point;
- được module khác sử dụng như dependency.

## PLATFORM

- thiên về configuration/resource/platform concern;
- không bắt buộc có Java source;
- không nhất thiết tạo executable artifact.

Không suy luận module type từ tên folder nếu metadata đã có.

---

# 9. Module metadata

Canonical schemas:

```text
project-build/gradle-runtime/src/main/resources/automation/master.json
project-build/gradle-runtime/src/main/resources/automation/properties.json
```

Một real module có thể có:

```text
gradle.properties
master.json
properties.json
build.gradle
task.gradle
src/
readme/
```

Vai trò:

```text
gradle.properties
→ real-module identity + direct Gradle properties

master.json
→ module metadata + feature flags

properties.json
→ detailed configuration for enabled feature groups

build.gradle
→ module-specific dependencies/configuration

task.gradle
→ generated task declarations when enabled
```

---

# 10. Metadata synchronization rules

Canonical schema thuộc build infrastructure.

Module-local values thuộc module/human.

Khi sync:

- giữ `VALUE` hiện có nếu hợp lệ/nonblank theo behavior implementation;
- schema metadata lấy từ canonical schema;
- key bị xóa khỏi canonical schema có thể biến mất khỏi module-local output;
- inactive properties group không được giữ chỉ vì từng tồn tại;
- malformed module JSON có thể được regenerate theo implementation hiện tại;
- write only when content changes.

Không thay đổi semantics này nếu người dùng không yêu cầu.

---

# 11. Feature flags

Các feature flag quan trọng hiện có:

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

Không invent feature flag mới nếu chưa có requirement.

Khi feature bị disable, phải kiểm tra behavior hiện tại trước khi quyết định generated state được giữ hay xóa.

---

# 12. Generated settings catalogs

Generated enums hiện nằm trong package:

```text
com.example.learning.generated.settings
```

Các catalog quan trọng:

```text
ModuleListEnum
DatabaseListEnum
```

## ModuleListEnum

Là generated typed view của module registry.

Có thể chứa:

- enum name = logical `SERVICE_NAME`;
- `modulePath`;
- `relativePath`;
- `moduleType`;
- description;
- dependency metadata.

Khi cần resolve module theo logical service name trong build logic, ưu tiên dùng `ModuleListEnum` nếu use case phù hợp.

Không scan metadata lại nếu generated typed catalog đã là dependency đúng của use case.

## DatabaseListEnum

Là generated typed view của database modules.

Có thể chứa:

- database name;
- module name;
- database type;
- module path.

## Important

Generated enum là **projection**, không phải canonical source of truth.

Không chỉnh tay generated enum để thay đổi module metadata.

---

# 13. Other generated artifacts

Các generated artifact quan trọng có thể gồm:

```text
ModuleListEnum
DatabaseListEnum
module-depend.json
STRUCTURE.md
module-structure.txt
```

Generated outputs phải ưu tiên:

- deterministic;
- idempotent;
- stable ordering;
- no timestamp nếu không thật sự cần;
- write-if-changed;
- không duplicate generated content qua nhiều lần chạy.

Không biến generated artifact thành source-of-truth mới nếu dữ liệu gốc đã tồn tại ở nơi khác.

---

# 14. Project structure documentation

Generated Markdown:

```text
<root>/STRUCTURE.md
```

Generated text structure:

```text
project-build/gradle-runtime/src/main/resources/structure/module-structure.txt
```

Source thực tế của structure là:

- filesystem dưới `module/`;
- module metadata liên quan.

`module-structure.txt` không phải template cấu trúc.

Không sử dụng legacy path:

```text
internal/info/structure/module_structure.txt
```

---

# 15. Resource ownership

Phân biệt ba nhóm:

## Human-owned

Ví dụ:

- source code;
- module `build.gradle`;
- module `gradle.properties`;
- module-owned metadata value;
- learning README content.

## Canonical build-owned

Ví dụ:

```text
project-build/gradle-runtime/src/main/resources/automation/master.json
project-build/gradle-runtime/src/main/resources/automation/properties.json
```

## Generated

Ví dụ:

```text
ModuleListEnum
DatabaseListEnum
module-depend.json
STRUCTURE.md
module-structure.txt
```

Trước khi sửa file, phải xác định nó thuộc nhóm nào.

---

# 16. Gradle lifecycle awareness

Luôn xác định logic thuộc phase nào:

```text
Initialization / Settings
Configuration
Execution
projectsEvaluated / late configuration
Runtime
```

Không di chuyển logic sang phase khác chỉ để code ngắn hơn.

Ví dụ:

- module discovery/include thuộc Settings scope;
- dependency catalog cần evaluated project state có thể cần chạy sau project evaluation;
- task action chỉ nên chạy ở execution phase;
- Spring runtime configuration không thuộc Gradle lifecycle.

---

# 17. Module dependency behavior

Không giả định mọi dependency đều giống nhau.

Các configuration có semantics khác nhau, ví dụ:

```text
implementation
api
annotationProcessor
developmentOnly
```

Không chuyển `annotationProcessor` thành `api`/`implementation`.

Khi copy external dependency giữa module, phải bảo toàn role/configuration của dependency.

Không resolve full dependency graph nếu requirement chỉ cần declared direct dependency.

---

# 18. Root build policy

Root `build.gradle` nên giữ mỏng.

Root có thể giữ global policy như:

- plugin declarations;
- group/version;
- Java version;
- repository definitions;
- BOM/import chung;
- module orchestration application;
- packaging policy.

Không đưa filesystem algorithm, generator implementation hoặc business build logic dài trở lại root `build.gradle`.

---

# 19. Root setup capability

Root-level capability có thể gồm:

- dependency catalog;
- project structure;
- database support;
- cleanup support;
- future root-wide utilities.

Root capability implementation thuộc `project-build/gradle-runtime`.

Root orchestration chỉ compose/apply chúng.

---

# 20. Cleanup capability

Cleanup là root capability lâu dài.

Task names:

```text
cleanupFiles
cleanupEmptyFolders
```

Properties:

```text
-PFileName=<exact-name>
-PFolderName=<exact-name>
-PProjectName=<SERVICE_NAME>   # optional
```

Rules:

- `FileName` required cho `cleanupFiles`;
- `FolderName` required cho `cleanupEmptyFolders`;
- nếu không có `ProjectName`, scan root và warn;
- nếu có `ProjectName`, resolve bằng `ModuleListEnum`;
- project name matching case-sensitive;
- file/folder name matching case-sensitive;
- target chỉ là simple name, không phải path;
- delete failure phải fail task;
- no result = success;
- empty folder chỉ xóa nếu đang rỗng, không recursive cleanup.

Excluded subtrees hiện được thống nhất ở cleanup implementation:

```text
.gradle
build
.git
.idea
```

Không tự thêm blacklist file/folder khác nếu chưa có yêu cầu.

---

# 21. Database build capability

Database operation là root concern.

Database module sở hữu runtime/configuration như `.env`.

Root task resolve database module thông qua generated database catalog.

Không hardcode đường dẫn database module nếu catalog đã cung cấp identity/path.

Runtime backup output không đặt trong `src/main/resources`.

Failure quan trọng phải fail task thay vì `println + return`.

---

# 22. IntelliJ build automation

IntelliJ run configuration template thuộc build infrastructure resource.

Template nên được load từ classpath resource của `gradle-runtime`, không dùng legacy root filesystem path.

Template là XML semantic content.

Generated run configurations được ghi vào root `.run/`.

Nếu template content được dùng làm Gradle task input, ưu tiên để Gradle track **content** thay vì chỉ resource path.

---

# 23. Spring Boot shared runtime

`project-build/springboot-runtime` dành cho runtime capability dùng chung.

Swagger runtime configuration thuộc khu vực này nếu nó cần tồn tại khi application chạy.

Không đặt runtime Swagger/Spring beans vào Gradle plugin artifact.

Build-time Swagger generation vẫn thuộc `gradle-runtime`.

Phân biệt rõ:

```text
Swagger build automation
→ gradle-runtime

Swagger Spring Boot runtime configuration
→ springboot-runtime
```

---

# 24. Package conventions hiện tại

Build setup được gom dưới:

```text
com.example.learning.setup
├── settings
├── root
└── module
```

Generated settings catalogs:

```text
com.example.learning.generated.settings
```

Không sử dụng package naming cũ nếu source đã được refactor.

Các naming/path legacy như sau được xem là obsolete:

```text
project-build
com.example.learning.settings...
com.example.learning.root...
com.example.learning.module...
com.example.learning.generated.settings.preset
internal/auto-build
```

Chỉ nhắc đến legacy khi đang xử lý migration hoặc evidence cho code chưa được dọn sạch.

---

# 25. Coding/refactor rules

Khi refactor:

- giữ behavior hiện tại trừ khi requirement yêu cầu đổi;
- không refactor unrelated code;
- ưu tiên thay đổi nhỏ và dễ review;
- giữ deterministic ordering;
- giữ idempotency;
- tránh rewrite generated file vô nghĩa;
- fail fast khi configuration bắt buộc bị thiếu/sai;
- không che lỗi bằng `println + return` nếu operation phải fail;
- không tạo abstraction chỉ vì có thể;
- abstraction phải phản ánh responsibility thực tế.

Khi người dùng nói:

```text
"chưa gen code"
"nhận định thôi"
"khoan code"
```

không generate implementation code.

Khi requirement đã được chốt và người dùng nói bắt đầu/tiến hành, mới đưa code cụ thể.

---

# 26. Khi phân tích một learning module

Nếu người dùng yêu cầu làm việc với một module cụ thể, ưu tiên thu thập:

```text
SERVICE_NAME
MODULE_TYPE
module path
gradle.properties
master.json
properties.json
build.gradle
direct module dependencies
relevant runtime dependencies
source tree
README
```

Sau đó thực hiện theo workflow:

1. xác định mục tiêu học tập;
2. xác định current implementation;
3. tổng hợp kiến thức từ cơ bản đến nâng cao;
4. đánh giá phần đã có và còn thiếu;
5. đề xuất README structure;
6. đề xuất examples/practice;
7. giải thích source code;
8. đưa self-review questions;
9. đưa extended exercises nếu phù hợp.

Không tự chỉnh source module nếu người dùng chỉ yêu cầu phân tích/giải thích.

---

# 27. Learning repository principles

Đây là repository học tập, không phải một monolithic production application.

Không ép tất cả module vào cùng một runtime architecture.

Một module có thể tồn tại chỉ để minh họa:

- Java concept;
- Spring feature;
- database feature;
- distributed-system concept;
- build tool;
- infrastructure technology;
- design pattern;
- testing technique.

Architecture phải hỗ trợ learning isolation mà vẫn tái sử dụng common build/runtime infrastructure.

---

# 28. Documentation rules

Khi tạo hoặc cập nhật documentation:

- current state trước;
- target state phải ghi rõ;
- known gap phải ghi rõ;
- không invent behavior;
- không ghi legacy path như current path;
- dùng `STRUCTURE.md` để tham chiếu module tree nếu phù hợp;
- ưu tiên link/path thực tế;
- tránh duplicate cùng một rule ở nhiều nơi nếu không cần.

Architecture documentation chi tiết và architecture overview có audience khác nhau:

```text
ARCHITECTURE_OVERVIEW.md
→ interviewer / junior / newcomer

ARCHITECTURE.md
→ deep architecture / maintainer / architect / AI context

AGENTS.md
→ working rules for Codex/AI agents
```

---

# 29. Source of Truth rule

Trước khi sử dụng dữ liệu, xác định source of truth.

Ví dụ:

```text
Module existence
→ filesystem + local gradle.properties

Canonical metadata schema
→ gradle-runtime resources/automation

Module-specific metadata values
→ module-local master.json / properties.json

Typed module registry
→ generated ModuleListEnum

Typed database registry
→ generated DatabaseListEnum

Project structure documentation
→ generated from filesystem/module metadata
```

Không đảo ngược dependency giữa source và generated view.

---

# 30. Khi thấy inconsistency

Nếu phát hiện:

- path cũ vẫn tồn tại;
- package naming cũ;
- documentation lệch implementation;
- duplicate source of truth;
- generated artifact bị chỉnh tay;
- lifecycle bất hợp lý;
- build-time/runtime coupling;

hãy báo:

```text
Current evidence
Expected convention
Impact
Recommended change
Files potentially affected
```

Không tự sửa nếu user chưa yêu cầu.

---

# 31. Cách báo cáo thay đổi đề xuất

Khi đề xuất refactor, ưu tiên format:

```text
Current
→ behavior/path hiện tại

Problem
→ coupling/risk/duplication

Target
→ trạng thái mong muốn

Affected files
→ file/package cần xem hoặc sửa

Behavior preserved
→ phần phải giữ nguyên

Behavior changed
→ phần được phép đổi
```

Nếu cần code, chỉ generate sau khi requirement đủ rõ.

---

# 32. Không được giả định từ tên

Không suy luận behavior chỉ từ:

- folder name;
- plugin name;
- service name;
- class name;
- generated file name.

Phải đọc implementation liên quan trước khi kết luận.

Tên chỉ là evidence phụ.

---

# 33. Scope discipline

Nếu request thuộc:

```text
project architecture
```

không tự đi sửa source code của learning module.

Nếu request thuộc:

```text
one module
```

không tự refactor global build architecture.

Nếu request thuộc:

```text
build automation
```

không tự thay đổi Spring runtime behavior.

Nếu phát hiện concern ngoài scope, báo riêng để người dùng quyết định.

---

# 34. Final checklist trước khi sửa code

Trước mỗi thay đổi, tự kiểm tra:

- [ ] Tôi đã hiểu scope?
- [ ] User có yêu cầu sửa thật không?
- [ ] File này là human-owned hay generated?
- [ ] Đây là build-time hay runtime concern?
- [ ] Logic thuộc Settings, Root, Module hay Task execution?
- [ ] Có source of truth nào đang bị duplicate không?
- [ ] Có giữ idempotency không?
- [ ] Có giữ deterministic output không?
- [ ] Có rewrite file không cần thiết không?
- [ ] Có phá dependency direction không?
- [ ] Có sử dụng package/path legacy không?
- [ ] Có thay đổi behavior ngoài requirement không?
- [ ] Có task destructive/generative nào cần user xác nhận trước khi chạy không?

Nếu bất kỳ điểm nào chưa rõ, hỏi người dùng trước.

---

# 35. Các tài liệu nên đọc cùng

Nếu tồn tại trong repository, ưu tiên đọc:

```text
ARCHITECTURE_OVERVIEW.md
ARCHITECTURE.md
STRUCTURE.md
```

Vai trò:

```text
ARCHITECTURE_OVERVIEW.md
→ high-level orientation

ARCHITECTURE.md
→ architecture contract chi tiết

STRUCTURE.md
→ module navigation/tree

AGENTS.md
→ rules để Codex/AI làm việc trong repository
```

Khi các tài liệu này xung đột với implementation, báo inconsistency và ưu tiên behavior thực tế từ source.
