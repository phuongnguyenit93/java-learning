# Step 1 - Curriculum Rules

Đây là **Step 1** của quá trình thiết kế learning area. Tài liệu này định nghĩa vai trò, phạm vi và cấu trúc chuẩn của một `CURRICULUM_MAP` dành cho một **area lớn** có nhiều learning module con trong dự án `java-learning`.

Mục tiêu của Curriculum không phải là thiết kế chi tiết bài học bên trong từng module, mà là thiết kế **kiến trúc học tập ở cấp area**: area gồm những module nào, mỗi module sở hữu kiến thức gì, module nào phụ thuộc module nào, boundary nằm ở đâu và learner nên đi qua toàn bộ area theo thứ tự nào.

### General Agent Rules context guard

Trước khi thực hiện Step 1:

```text
Nếu context của GENARAL_AGENT_RULES.md vẫn còn rõ ràng trong working context
→ không cần đọc lại

Nếu context đã bị loại khỏi cửa sổ làm việc, bị quên, bị truncate,
hoặc agent không chắc mình còn nhớ đầy đủ các global rules
→ đọc lại ./GENARAL_AGENT_RULES.md trước khi tiếp tục
```

Không suy đoán các orchestration, preservation, gap-routing hoặc cross-step rules từ trí nhớ khi context không còn chắc chắn.

### Temporary Curriculum Map artifact

Trong Step 1, mọi Curriculum Map tạm dùng để phân tích/review trước khi được promote sang source-of-truth chính thức phải được đặt tại:

```text
<repository-root>/module-generate-agent/temp/<MODULE>_CURRICULUM_MAP.md
```

Naming contract:

```text
<MODULE>_CURRICULUM_MAP.md
```

Ví dụ:

```text
module-generate-agent/temp/JAVA_CORE_CURRICULUM_MAP.md
module-generate-agent/temp/SPRING_CORE_CURRICULUM_MAP.md
```

Không đặt temporary Curriculum Map rải rác trong module source, repository root hoặc các folder learning khác. `module-generate-agent/temp/` là canonical working location cho artifact tạm của Step 1.

---

## 1. Vai trò của Curriculum

Curriculum trả lời câu hỏi ở cấp **toàn bộ area**, không phải ở cấp từng lesson.

```text
CURRICULUM MAP
        ↓
Area-level learning architecture
        ↓
Module ROADMAP
        ↓
Menu / Knowledge
        ↓
API Docs / Video / Quiz / Interview
```

Curriculum nên quản lý:

- phạm vi của area;
- danh sách module;
- ownership của concept;
- boundary giữa các module;
- dependency giữa các module;
- recommended learning path;
- cross-module concept;
- terminology ownership;
- coverage toàn area.

Curriculum không được trở thành một bản sao chi tiết của ROADMAP hoặc Knowledge của từng module.

---

## 2. Scope của area

Curriculum phải xác định rõ:

- area này dạy gì;
- area này không dạy gì;
- learner mục tiêu là ai;
- prerequisite tổng thể;
- mức độ depth mong muốn;
- boundary với các area lớn khác.

Ví dụ với một area lớn:

```text
Spring
├── Core / IoC
├── Boot
├── MVC
├── Data
├── Security
├── Integration
└── ...
```

Trước khi tạo từng module, phải biết toàn bộ area đang cố gắng bao phủ những gì và đâu là giới hạn của nó.

---

## 3. Canonical module inventory

Curriculum phải định nghĩa danh sách module chính thức của area.

Không chỉ liệt kê folder hiện có, mà phải chỉ rõ vai trò của từng module, ví dụ:

- foundation;
- core;
- supporting;
- optional;
- advanced.

Ví dụ:

```yaml
modules:
  - id: spring-core
    role: foundation

  - id: spring-mvc
    role: core

  - id: spring-security
    role: advanced
```

Danh sách này giúp phân biệt module nào là bắt buộc trong learning journey và module nào chỉ là nhánh mở rộng.

---

## 4. Responsibility và ownership của từng module

Đây là một trong những trách nhiệm quan trọng nhất của Curriculum.

Curriculum phải trả lời:

> Concept này do module nào chịu trách nhiệm dạy chính?

Ví dụ:

```text
Dependency Injection
→ spring-core owns

HTTP request lifecycle
→ spring-mvc owns

Authentication / Authorization
→ spring-security owns
```

Một module khác có thể reference hoặc sử dụng concept đó, nhưng không nên tự dạy lại toàn bộ nếu không phải owner chính.

Mục tiêu là tránh:

- duplicate Knowledge;
- concept không có owner;
- nhiều module cùng tự coi mình là source-of-truth cho cùng một chủ đề.

---

## 5. Boundary giữa các module

Curriculum không chỉ xác định module nào sở hữu concept, mà còn phải chỉ rõ **điểm dừng** của từng module.

Ví dụ:

```text
spring-mvc
→ giải thích authentication ở mức integration với request flow

spring-security
→ sở hữu authentication architecture và security mechanics đầy đủ
```

Boundary giúp ngăn một module phình quá lớn và giúp learner biết khi nào nên chuyển sang module chuyên sâu khác.

Cross-link nên được dùng thay vì duplicate toàn bộ kiến thức đã có owner.

---

## 6. Dependency graph giữa các module

Curriculum phải mô tả dependency ở cấp module:

```text
cái gì nên học trước
cái gì phụ thuộc cái gì
cái gì có thể học song song
```

Ví dụ:

```text
Spring Core
    ↓
Spring Boot Fundamentals
    ↓
┌───────────────┬───────────────┐
MVC             Data            Integration
↓               ↓
Security        Transaction
```

Dependency graph ở đây chỉ nói về **quan hệ giữa các module**.

Chi tiết learning order bên trong từng module thuộc trách nhiệm của `roadmap.yml` của module đó.

---

## 7. Recommended learning path

Dependency kỹ thuật và learning path không phải lúc nào cũng giống nhau.

Curriculum nên cung cấp một recommended learning journey ở cấp area, ví dụ:

```text
Wave 1 — Foundation
        ↓
Wave 2 — Core usage
        ↓
Wave 3 — Production mechanisms
        ↓
Wave 4 — Advanced internals
```

Learning waves giúp learner biết nên đi qua area theo thứ tự nào mà không biến Curriculum thành roadmap chi tiết của từng module.

### 7.1 Materialize learning order bằng `module-order.yml`

Sau khi canonical module inventory và recommended learning path của area đã đủ ổn định, Step 1 phải materialize thứ tự sibling tương ứng bằng file:

```text
<area-parent>/module-order.yml
```

File này là **parent-local ordering contract** cho generated module tree. Nó chỉ điều khiển **direct child directories** của parent chứa file.

Ví dụ:

```text
module/platform/development/programming/language/java/advance/
├── dynamic-runtime/
├── instrumentation/
├── jvm/
├── native-interoperability/
├── networking/
├── runtime-diagnostics/
├── runtime-extensibility/
├── security-cryptography/
└── module-order.yml
```

Trong ví dụ trên, `module-order.yml` chỉ order 8 direct children của `java/advance`. Nó không order chapter bên trong từng module và không tự order descendant sâu hơn.

#### File này dùng để làm gì?

`module-order.yml` không phải Curriculum source-of-truth và không thay thế dependency graph / learning waves.

Ownership đúng là:

```text
CURRICULUM
→ quyết định canonical module inventory + recommended learning order
        ↓
module-order.yml
→ materialize sibling order đó cho generated module hierarchy
        ↓
ProjectStructureService
→ apply order trước khi generate STRUCTURE / module catalog
        ↓
Portal
→ preserve catalog order, không đọc/sort lại module-order.yml
```

Vì vậy:

- Step 1 quyết định **vì sao** module A đứng trước module B;
- `module-order.yml` chỉ lưu **thứ tự hiển thị/projection** tương ứng;
- Step 2 ROADMAP vẫn chỉ quản lý learning journey **bên trong một module**;
- không dùng `module-order.yml` để suy ngược Curriculum khi Curriculum chưa được review.

#### Cách tạo/sync file

Không tự dựng shape file bằng trí nhớ nếu repository task đang khả dụng. Dùng explicit root task:

```text
./gradlew generateModuleOrder --path=<parent-path-relative-to-module>
```

Trên Windows có thể dùng:

```text
.\gradlew.bat generateModuleOrder --path=<parent-path-relative-to-module>
```

Ví dụ cho `java/advance`:

```text
.\gradlew.bat generateModuleOrder --path=platform/development/programming/language/java/advance
```

Task này:

```text
discover direct child directories
→ create/sync module-order.yml
→ preserve existing human-owned order values
→ add child mới với order null/rỗng
→ remove stale child đã không còn tồn tại
```

Ordinary Gradle sync/generation chỉ **đọc** file này; không được tự mutate human-owned order.

#### Cấu trúc và giá trị cần điền

Schema hiện tại:

```yaml
version: 1
children:
  module-a:
    order: 10
  module-b:
    order: 20
  module-c:
    order:
```

Rules:

```text
version
→ hiện phải là integer 1

children
→ map của direct child directory name

order
→ non-negative integer hoặc null/rỗng
→ số nhỏ hơn đứng trước
→ cùng order thì fallback ABC
→ null/rỗng đứng sau các child có order rồi fallback ABC
```

Nên dùng khoảng cách như `10, 20, 30, ...` khi area có khả năng chèn module về sau; `1, 2, 3, ...` cũng hợp lệ nếu không cần khoảng trống.

Agent phải điền `order` theo **recommended learning path đã được Curriculum chấp nhận**, không theo alphabetical order, folder creation order hoặc historical Portal order.

Nếu một parent có direct child là GROUP thay vì real module, `module-order.yml` vẫn order direct child directory đó. File này là tree-sibling ordering contract, không phải danh sách riêng chỉ dành cho real module.

#### Validation sau khi điền

Sau khi chỉnh `order`:

1. chạy lại `generateModuleOrder --path=...` để xác nhận shape vẫn hợp lệ và human-owned order được preserve;
2. chạy repository structure/catalog generation phù hợp khi task đang khả dụng;
3. kiểm tra generated hierarchy/catalog giữ đúng thứ tự;
4. kiểm tra git diff để đảm bảo chỉ có intended Curriculum/order changes.

Nếu file có child không tồn tại, parser có thể warning/ignore stale entry; canonical Step 1 workflow vẫn phải dùng generator để sync và loại stale entry thay vì giữ cấu hình cũ.

---

## 8. Cross-module concepts

Một số concept xuất hiện ở nhiều module và cần được quản lý ở cấp area.

Ví dụ:

```text
transaction
configuration
proxy
lifecycle
serialization
concurrency
error handling
```

Curriculum nên xác định:

```text
Primary owner
Secondary consumers
```

Ví dụ:

```text
Proxy

Primary owner:
→ spring-aop

Used by:
→ spring-transaction
→ spring-security
→ spring-cache
```

Module sử dụng concept không cần tự giải thích lại từ đầu nếu owner đã tồn tại.

---

### Cross-area ownership: one primary home per concept

Before adding a topic to a module, inspect neighboring learning areas and choose one primary canonical home. Do not duplicate full curricula merely because a concept is related to several areas.

Typical boundary examples:

```text
programming/language/java/core
→ stable language/standard-library foundations used broadly across versions

programming/paradigm/functional
→ language-neutral functional programming paradigm, pure functions, immutability, composition, referential transparency and trade-offs

programming/language/java/core/functional-programming
→ Java functional programming mechanics such as functional interfaces, lambdas, method references, variable capture and Optional

development/data/persistence/relational-access
→ application-side relational data access technologies such as JDBC and MyBatis

development/data/persistence/orm
→ persistence specifications and ORM implementations such as Jakarta Persistence (JPA) and Hibernate

programming/framework/spring-data
→ Spring-specific repository/data-access abstractions and integrations

programming/framework/spring-framework/core-container
→ Spring IoC container fundamentals including beans, dependency injection, component scanning, Java configuration, bean resolution, scopes, lifecycle, Environment, profiles, @Import, @Conditional and container extension points

programming/framework/spring-framework/testing
→ Spring TestContext Framework including context configuration, ApplicationContext testing, active profiles, @TestPropertySource, context caching, transactional testing and integration-test infrastructure

programming/framework/spring-boot/fundamentals
→ Spring Boot fundamentals including SpringApplication, @SpringBootApplication, starters, embedded servers, application lifecycle, packaging, DevTools and the Spring Framework vs Spring Boot boundary

programming/framework/spring-boot/auto-configuration
→ Spring Boot auto-configuration including conditional registration, classpath/property-driven configuration, ordering, exclusion, diagnostics, custom auto-configuration and custom starters

programming/framework/spring-boot/externalized-configuration
→ Spring Boot externalized configuration including Config Data, properties/YAML, binding, precedence, profile-specific configuration, validation and configuration metadata

programming/framework/spring-boot/testing
→ Spring Boot-specific testing including @SpringBootTest, web environments, test slices, test auto-configuration, dependency replacement, Testcontainers and integration-test strategy

programming/framework/spring-boot/native-image
→ Spring Boot AOT and native-image support including GraalVM, closed-world constraints, runtime hints, native testing and runtime/build trade-offs

programming/framework/spring-boot/actuator
→ Spring Boot production-ready features including Actuator endpoints, health, metrics, runtime diagnostics, endpoint exposure, custom endpoints, security and observability integration

programming/framework/spring-integration
→ Spring Integration and Enterprise Integration Patterns including messages, channels, endpoints, gateways, routers, filters, transformers, splitters, aggregators and adapters

programming/framework/springdoc/openapi
→ Springdoc/OpenAPI learning knowledge and examples; runtime Swagger/OpenAPI capability belongs to project-build/springboot-runtime

programming/framework/spring-security/fundamentals
→ Spring Security fundamentals including SecurityFilterChain, SecurityContext, authentication, authorization, method security, session management, CSRF, CORS and exception handling

programming/framework/spring-security/oauth2
→ Spring Security OAuth2/OpenID Connect including clients, resource servers, JWT, scopes, token validation and authorization-server concepts

programming/framework/spring-data/mongodb
→ Spring Data MongoDB repositories, object mapping, query methods, MongoTemplate, aggregation, transactions, auditing and reactive integration

programming/framework/spring-data/redis
→ Spring Data Redis templates, serialization, repositories, TTL, cache integration, Pub/Sub, Streams and reactive access

programming/framework/spring-data/r2dbc
→ Spring Data R2DBC reactive relational persistence including mapping, repositories, DatabaseClient, R2dbcEntityTemplate, transactions and backpressure-aware access

programming/framework/spring-data/jdbc
→ Spring Data JDBC aggregate-oriented relational persistence, repositories, mapping, queries, transactions and its boundary with JPA

programming/framework/spring-batch
→ Spring Batch jobs, steps, chunk processing, item reader/processor/writer, tasklets, restartability, fault tolerance and scaling

programming/framework/spring-session
→ Spring Session externalized session management including repositories, Redis/JDBC backing stores, expiration and Spring Security integration

programming/framework/spring-modulith
→ Spring Modulith application modules, structural verification, module events, testing, observability and generated documentation

integration/messaging/message-broker
→ native message-broker technologies and messaging semantics such as RabbitMQ and ActiveMQ; Spring AMQP abstractions belong to programming/framework/spring-amqp

integration/messaging/event-streaming
→ native event-streaming technologies and semantics such as Apache Kafka; Spring for Apache Kafka abstractions belong to programming/framework/spring-kafka

integration/http/request-response
→ HTTP request/response integration concepts plus application-to-application client choices; Integration owns usage patterns, trade-offs and cross-client comparison, while language/framework owners retain deep implementation internals

integration/http/request-response/client/java
→ Java HTTP client options used for integration, including legacy HttpURLConnection and modern java.net.http.HttpClient; Java networking owns deep JDK API/runtime mechanics

integration/http/request-response/client/spring-framework
→ Spring Framework HTTP client options used for integration, including RestClient, RestTemplate and WebClient; programming/framework/spring-framework/web owns deep framework internals

integration/http/request-response/client/spring-cloud
→ Spring Cloud declarative/service-to-service HTTP clients such as OpenFeign; Spring Cloud/microservice curriculum owns discovery, load-balancing and framework-specific internals

integration/http/server-sent-events
→ HTTP server-to-client event streaming with Server-Sent Events

integration/http/webhook
→ HTTP callback integration including delivery, retry, duplicate handling, idempotency, signatures and replay protection

integration/rpc/grpc
→ native gRPC/RPC concepts including protobuf contracts, unary and streaming interaction models; Spring gRPC integration belongs to programming/framework/spring-grpc

integration/realtime/websocket
→ full-duplex WebSocket communication concepts and protocol behavior

integration/realtime/rsocket
→ RSocket interaction models and protocol-level reactive communication

platform/code-quality/static-analysis
→ source/compiler/bytecode analysis that detects coding-rule violations, bug patterns and code smells; examples include Checkstyle, PMD, SpotBugs and Error Prone

platform/code-quality/formatting
→ deterministic source formatting and format enforcement; Spotless may orchestrate formatting engines, while formatting must not be treated as bug/static analysis

platform/code-quality/continuous-inspection
→ continuous aggregation of quality/security/maintainability measurements and policy gates across analysis results; SonarQube is the canonical example

platform/code-quality/test-quality
→ reserved ownership for test-effectiveness evidence such as coverage or mutation testing when that curriculum is introduced; do not place JaCoCo/PIT under static-analysis merely because they produce quality metrics

infrastructure/devops/source-control
→ source-control workflows and tooling such as Git, branching, merge/rebase, tags and repository collaboration mechanics

infrastructure/devops/ci-cd
→ continuous integration/delivery/deployment concepts and pipeline implementations; fundamentals owns pipeline/stage/artifact-promotion/quality-gate concepts, while Jenkins, GitHub Actions and GitLab CI own tool-specific mechanics

infrastructure/devops/containerization
→ container build/runtime packaging knowledge such as Docker images, containers, Dockerfile, layers, BuildKit, volumes, networks and Compose

infrastructure/devops/orchestration
→ container orchestration knowledge such as Kubernetes scheduling, workloads, services, configuration, rollout and cluster-level application management

infrastructure/devops/artifact-management
→ artifact publication, repository management, coordinates, immutability and promotion workflows; Maven Central and Nexus are canonical examples

infrastructure/devops/infrastructure-as-code
→ declarative infrastructure provisioning and state/drift management such as Terraform

infrastructure/devops/configuration-management
→ host/application configuration automation and desired-state operations such as Ansible

infrastructure/devops/environment-management
→ environment strategy and promotion across local/dev/test/staging/production, including parity, configuration boundaries and ephemeral environments

infrastructure/devops/deployment
→ application deployment strategies such as rolling, recreate, blue-green, canary, rollback and zero-downtime delivery

DevOps knowledge under module/** is curriculum. Repository-specific build/generation/runtime machinery under project-build/** and project-orchestration/** remains implementation infrastructure for java-learning itself and must not replace the learning modules above.

Keep neighboring infrastructure ownership explicit: observability stays under infrastructure/system/observability, protocols/proxy/generic API-gateway/service-mesh stay under infrastructure/system/network, identity/access and secrets lifecycle stay under infrastructure/system/security, runtime containers stay under infrastructure/system/runtime, and microservice architecture/patterns stay under module/microservice. Framework-specific implementations such as Spring Cloud Gateway remain with their framework owner.

infrastructure/system/database
→ database engines plus database-side operational concerns. Engines are grouped by data/workload model (relational, document, key-value, graph, search, time-series, analytical). Connection management and schema migration are explicit supporting domains.

infrastructure/system/database/performance
→ database-side performance mechanics such as query optimization, indexing, execution plans, statistics, partitioning, locking and database caching. ORM performance does not belong here because ORM is an application/persistence abstraction.

infrastructure/system/database/engine/search/elasticsearch
→ Elasticsearch as a search/analytics engine. Observability/ELK curricula may reference this canonical engine module instead of recreating Elasticsearch internals.

infrastructure/system/observability
→ runtime visibility and diagnosis: logging, metrics, tracing, profiling/runtime analysis, thread dumps and management consoles. Loki belongs here as logging infrastructure rather than under generic database taxonomy.

infrastructure/system/network
→ system-level protocol, proxy, generic API-gateway, traffic-management and service-mesh concerns. Spring Cloud Gateway is framework-specific and belongs to programming/framework/spring-cloud/gateway.

infrastructure/system/security
→ infrastructure identity/access management and secrets management, including Keycloak, token lifecycle, HashiCorp Vault and secret lifecycle. Spring Security remains the application/framework security owner.

infrastructure/system/runtime
→ application runtime/container infrastructure such as servlet containers Tomcat and Jetty.

development/data/data-mapping/object-mapping
→ object-to-object mapping concepts and implementations such as manual mapping, MapStruct and ModelMapper

development/data/serialization/jackson
→ Jackson data binding and Java object ↔ JSON serialization/deserialization

programming/language/java
→ Java language, Java SE/runtime APIs and Java-specific mechanics; mapping/serialization libraries should not be owned here merely because they are implemented in Java

development/software-process/methodology/lifecycle-models
→ software development lifecycle models and phase/feedback structures such as Waterfall, Iterative, Incremental, Spiral and V-Model

development/software-process/methodology/agile-development
→ Agile principles and related methods/frameworks such as Scrum, Kanban, Extreme Programming and Lean Software Development

development/software-process/methodology/driven-development
→ driven development approaches and practices such as TDD, BDD, ATDD, FDD and MDD

programming/language/java/version/java8/stream-api
→ Stream API mechanics and pipeline behavior

programming/language/java/version/java9/module-system
→ JPMS / module-info / module path / strong encapsulation

programming/language/java/version/java*/<feature>
→ version-specific Java feature/change modules. A material language, library, tooling, runtime, GC, security, removal, or migration change may have its own real module directly under the Java release that introduced/finalized that change.

Java version modules are intentionally feature-granular. Do not force multiple independent changes into one release-summary module merely to reduce module count. A related concept may also have another learning owner elsewhere (for example Generics in Java Core or Virtual Threads in Concurrency); version placement records the release/evolution dimension, and navigation/cross-linking may connect the related modules.

Do not create a version feature module for every microscopic release-note item. The threshold is still a meaningful learning topic with its own definition, reason for existence, behavioral/mechanical impact, or migration consequence.

programming/language/java/advance/jvm
→ JVM internals, bytecode, runtime memory, GC, JIT, Java Memory Model

programming/language/java/advance/networking
→ Java networking APIs such as Socket/ServerSocket, NIO channels/selectors, URI/URL and HttpClient

programming/language/java/advance/security-cryptography
→ Java security/cryptography APIs such as providers, digests, MAC, cipher, signatures, keys, KeyStore and certificates
```

Related modules may later link to or reference the canonical topic, but they should not independently recreate the same full explanation. This keeps Knowledge ownership clear and makes cross-module relations meaningful instead of duplicative.

Keep these Java API curricula separate from neighboring system/framework areas. For example, `infrastructure/system/network` owns protocol, gateway, proxy, service-mesh and system-level TLS concerns; Spring Security owns framework authentication/authorization/resource-server behavior. Java networking/security modules should teach the JDK/runtime APIs and their semantics, not duplicate those infrastructure/framework curricula.

---

## 9. Area-level terminology map

Curriculum có thể giữ vocabulary cốt lõi của toàn area, nhưng không cần định nghĩa chi tiết như Knowledge.

Ví dụ:

```text
Container
Bean
Dependency Injection
ApplicationContext
Auto Configuration
Proxy
Filter
Interceptor
Repository
Transaction
```

Điều quan trọng là xác định:

- term xuất hiện lần đầu ở module nào;
- module nào chịu trách nhiệm giải thích đầy đủ term đó;
- module nào chỉ sử dụng lại term.

Terminology map giúp learner không gặp một thuật ngữ quan trọng trước khi module owner của nó được giới thiệu hợp lý.

---

## 10. Coverage matrix

Với một area lớn, Curriculum nên có coverage matrix để kiểm tra ownership và overlap.

Ví dụ:

| Concept | Primary owner | Related modules |
| --- | --- | --- |
| Dependency Injection | `spring-core` | `spring-boot`, `spring-test` |
| Bean lifecycle | `spring-core` | `spring-boot` |
| HTTP request lifecycle | `spring-mvc` | `spring-security` |
| Transaction | `spring-transaction` | `spring-data-jpa` |
| Proxy | `spring-aop` | `spring-transaction`, `spring-security` |

Coverage matrix giúp phát hiện:

- concept chưa có owner;
- concept bị nhiều module cùng sở hữu;
- duplicate scope;
- curriculum gap;
- boundary không rõ ràng.

---

## 11. Module creation criteria

Không phải topic nào cũng nên được tách thành một module riêng.

Curriculum nên quy định tiêu chí tạo module mới.

Một topic nên trở thành module riêng khi phần lớn các điều kiện sau đúng:

- có mental model riêng;
- có learning journey đủ lớn;
- có API/mechanism hoặc behavior riêng đáng kể;
- có boundary rõ ràng với module khác;
- có đủ depth để justify một module độc lập;
- nếu để trong module hiện tại sẽ khiến module đó mất cohesion hoặc quá lớn.

Ngược lại, nếu topic chỉ là:

- một utility nhỏ;
- một API nhỏ;
- một keyword;
- một sub-concept tự nhiên của module khác;

thì nên giữ nó như một phần của Knowledge trong module owner thay vì tạo thêm module.

---

### Module granularity: a module is a learning domain, not one tiny concept

Do not automatically create one real module for every small Java concept, keyword, API, annotation, or language rule.

For learning-authoring purposes, prefer this model:

```text
real learning module
→ one coherent topic/domain large enough to have its own curriculum

README chapter / Knowledge section
→ a smaller concept that belongs inside that curriculum
```

For example, concepts such as:

```text
boxing
casting
pass-by-value
constructor
access modifier
```

are usually better treated as chapters/sections of broader modules such as `language-basics` or `class-object` rather than five independent real modules.

By contrast, a topic such as `generics`, `collection`, `exception`, or `reflection` can justify its own module when it contains several connected concepts, mental models, pitfalls and practical behaviors that form a meaningful curriculum.

A useful heuristic is to draft the topic outline before creating/splitting the module:

```text
too small
→ definition
→ one example
→ one pitfall

likely large enough
→ mental model
→ core mechanics
→ important variants
→ runtime/practical behavior
→ edge cases
→ comparisons
→ pitfalls
→ trade-offs / usage guidance
→ advanced implications when relevant
```

This is a learning-design heuristic, not a physical module-discovery rule. The repository still identifies a physical real module by its local `gradle.properties` according to `../AGENTS.md` and `../ARCHITECTURE.md`.

There is no mandatory numeric minimum, but as a rough quality signal a mature topic often supports several meaningful Knowledge sections and enough non-duplicative Quiz/Interview material to justify an independent curriculum. Do not create filler merely to reach an arbitrary count.

API presence is **not** a requirement for a topic to deserve its own module:

```text
Knowledge + Quiz + Interview
→ may form a complete learning module without API

API
→ optional practical/experiment layer when the module genuinely exposes useful runnable behavior
```

Do not convert a naturally library/concept-oriented topic into a `SERVLET`/`REACTIVE` application merely to manufacture REST endpoints. Conversely, when a `SERVLET`/`REACTIVE` module does expose meaningful learning APIs, the API learning-documentation step requires those APIs to connect back to real Knowledge concepts.

---

## 12. Curriculum planning và lock cho area lớn

Khi một area có nhiều module, không đi thẳng từ folder/taxonomy hiện tại sang authoring từng module. Trước tiên cần ổn định kiến trúc học tập cấp area để tránh module ownership, boundary và dependency bị thay đổi liên tục sau khi ROADMAP/Knowledge đã bắt đầu được viết.

Flow khuyến nghị:

```text
1. Inventory toàn bộ area
        ↓
2. Xác định scope và boundary của area
        ↓
3. Xác định canonical module inventory
        ↓
4. Gán primary owner cho các concept lớn
        ↓
5. Xác định boundary giữa các module
        ↓
6. Xác định dependency graph giữa các module
        ↓
7. Xác định recommended learning path / waves
        ↓
8. Review các foundational gap ở cấp area
        ↓
9. Lock Curriculum
        ↓
10. Generate/sync module-order.yml và điền order theo learning path đã lock
        ↓
11. Handoff từng module sang Module ROADMAP
```

Curriculum planning chỉ dừng ở **area/module architecture**. Không dùng phase này để thiết kế H2, Knowledge anchor, API experiment, Quiz coverage hoặc Interview coverage của từng module; các chi tiết đó thuộc downstream module workflow.

### Large-gap review

Trong lúc review Curriculum, ưu tiên phát hiện gap có thể làm learner thiếu một phần mental model quan trọng của cả area.

Hỏi:

```text
Nếu concept/domain này không có module owner rõ ràng,
learner có bị thiếu một phần nền tảng quan trọng của area không?
```

Ví dụ gap đáng chặn Curriculum lock:

```text
foundation concept không có owner
module quan trọng bị thiếu hoàn toàn
hai module cùng own một concept lớn
boundary khiến cùng một curriculum bị duplicate
dependency giữa module bị vòng hoặc sai prerequisite
advanced module xuất hiện trước foundation cần thiết
```

Các chi tiết nhỏ bên trong một module như một utility class, convenience method, annotation hiếm gặp hoặc một API variant nhỏ thường không phải lý do để tiếp tục mở rộng Curriculum. Chúng được xử lý trong Module ROADMAP/Knowledge nếu thực sự cần.

### Curriculum lock

Một Curriculum có thể được coi là đủ ổn định để handoff xuống module-level khi:

```text
area scope đã rõ
canonical module inventory đã ổn định
module role đã rõ
primary concept ownership đã rõ
module boundaries đã rõ
dependency graph không còn major issue
recommended learning path phù hợp với learner mục tiêu
cross-module concept không còn duplicate owner đáng kể
major foundational gap đã được review
module-order.yml của area parent đã materialize đúng recommended sibling learning order khi area có nhiều direct child cần custom order
```

Sau khi lock, thay đổi module inventory, primary owner, boundary hoặc dependency graph phải được coi là **Curriculum migration**, không phải chỉnh sửa cục bộ trong một module.

### Wave planning cho scope rất lớn

Khi area có nhiều module, có thể chia việc triển khai thành các wave theo dependency học tập:

```text
Wave 1 — Foundation
        ↓
Wave 2 — Core mechanisms / usage
        ↓
Wave 3 — Supporting domains
        ↓
Wave 4 — Advanced / production topics
```

Wave thể hiện dependency học tập, không nhất thiết là dependency Gradle/runtime. Trước khi bắt đầu một wave, thực hiện một lần **large-gap-only review**; nếu không còn gap nền tảng đáng kể thì bắt đầu triển khai thay vì tiếp tục mở rộng taxonomy vô hạn.

### CURRICULUM GAP

Trong quá trình xây một module, nếu phát hiện vấn đề thuộc một trong các nhóm sau:

```text
thiếu module
sai primary owner
boundary giữa module không rõ hoặc sai
dependency giữa module sai
learning wave cấp area không hợp lý
concept lớn không có owner hoặc bị duplicate owner
```

thì ghi nhận **CURRICULUM GAP** và quay lại Step 1 để review Curriculum. Không tự sửa ownership/boundary cấp area bên trong Module ROADMAP hoặc Knowledge.

---

## 13. Area-level acceptance check

Trước khi coi Curriculum của một area là ổn định, cần kiểm tra ít nhất các câu hỏi sau:

```text
Có foundational concept nào chưa có owner?

Có concept nào bị hai hoặc nhiều module cùng own?

Có dependency vòng giữa các module không?

Có module nào quá nhỏ để tồn tại độc lập không?

Có module nào quá lớn và nên tách không?

Có prerequisite nào bị thiếu không?

Recommended learning path có phù hợp với newbie không?

Có module advanced nào bị đưa lên quá sớm không?

Có cross-module duplication đáng kể không?

Có boundary nào mơ hồ khiến nhiều module cùng dạy một nội dung không?

module-order.yml đã được generate/sync từ direct children thật và order có phản ánh recommended learning path không?
```

Curriculum chỉ nên được xem là ổn định khi không còn major conceptual gap ở cấp area.

---

## 14. Những gì Curriculum không nên chứa

Curriculum không nên chứa chi tiết triển khai thuộc trách nhiệm của từng module.

Không nên đưa vào Curriculum:

```text
H2 chi tiết
chapter title chi tiết
Knowledge anchor
API experiment cụ thể
Quiz question cụ thể
Quiz coverage chi tiết theo lesson
Interview question cụ thể
roadmap milestone chi tiết bên trong module
nội dung Knowledge chính thức
```

Những phần đó thuộc flow:

```text
Module ROADMAP
        ↓
Menu
        ↓
Knowledge
        ↓
API Docs / Video / Quiz / Interview
```

Giữ boundary này giúp tránh việc Curriculum và module ROADMAP trở thành hai source-of-truth cạnh tranh.

---

## 15. Bảy câu hỏi cốt lõi của một Curriculum

Một Curriculum tốt cho area lớn phải trả lời được bảy câu hỏi:

```text
1. Area này bao gồm những gì?

2. Area gồm những module nào?

3. Mỗi module chịu trách nhiệm dạy cái gì?

4. Boundary giữa các module nằm ở đâu?

5. Module nào phụ thuộc module nào?

6. Learner nên đi qua các module theo thứ tự nào?

7. Có concept nào đang thiếu owner hoặc bị duplicate owner không?
```

Nếu một file `CURRICULUM_MAP` trả lời rõ bảy câu hỏi này thì nó đang thực hiện đúng vai trò của mình.

---

## 16. Kiến trúc tổng thể

Recommended architecture:

```text
AREA_CURRICULUM_MAP.md
        │
        ├── scope
        ├── module inventory
        ├── ownership
        ├── boundaries
        ├── dependency graph
        ├── learning waves
        ├── terminology ownership
        └── coverage matrix
                 ↓
        module-order.yml
        → materialized direct-child presentation order
                 ↓
        module A ROADMAP
        module B ROADMAP
        module C ROADMAP
                 ↓
        Menu
                 ↓
        Knowledge
                 ↓
        API Docs / Video / Quiz / Interview
```

Source-of-truth hierarchy:

```text
Area curriculum scope
        ↓
AREA_CURRICULUM_MAP.md
        ↓
module-order.yml
→ sibling-order projection of approved area learning order
        ↓
Module learning journey
        ↓
roadmap/<language>/roadmap.yml
        ↓
Detailed learning structure
        ↓
Menu / Knowledge
        ↓
Reinforcement
        ↓
API Docs / Video / Quiz / Interview
```

---

## 17. Module-level inspection / handoff for a concrete module

Do not start by generating content from the module name alone.

Inspect the actual module first. At minimum, determine:

```text
module path
gradle.properties / real-module identity
master.json
MODULE_TYPE
MODULE_LANGUAGE
enabled learning capabilities
build.gradle when relevant
existing README / Knowledge files
existing Java source relevant to the learning topic
existing controllers/endpoints for SERVLET/REACTIVE modules
existing Swagger metadata
existing Quiz / Interview files
existing tests/examples when they materially demonstrate behavior
```

Build an internal topic inventory before editing:

```text
What is the module actually teaching?
Which concepts exist in source or examples?
Which concepts already have Knowledge content?
Which concepts have executable experiments?
Which important concepts are missing?
Which existing content is duplicate, stale or misleading?
```

Use repository/module implementation as evidence, supplemented by technically authoritative knowledge when explanation requires broader context.

Never use this shortcut:

```text
module name
    ↓
generic AI course generated from memory
```

Preferred model:

```text
module implementation + repository intent + authoritative technical knowledge
                              ↓
                     module curriculum
```

At the end of Step 1, the agent should understand the module boundary, neighboring ownership, major concepts, existing learning surfaces and which capabilities are relevant. For a new module this becomes input to ROADMAP design; for an existing module it becomes input to ROADMAP review/refactor.

For a nontrivial module, Step 1 is incomplete until the agent can also state the learning story in plain language:

```text
This module is about ...
It exists because ...
The major terms are ...
They connect like ...
The learner should read them in this order because ...
```

## 18. Nguyên tắc kết luận

Curriculum không thiết kế bài học chi tiết bên trong module.

Curriculum thiết kế **kiến trúc học tập giữa các module**.

```text
CURRICULUM
→ quản lý toàn area

ROADMAP
→ quản lý learning journey bên trong một module

MENU / KNOWLEDGE
→ triển khai chi tiết roadmap

API DOCS / QUIZ / INTERVIEW
→ reinforce Knowledge
```

Giữ đúng boundary này giúp Curriculum vẫn hữu ích cho các area lớn như Spring, Database, Microservice, Messaging hoặc các hệ kiến thức tương tự mà không bị trùng trách nhiệm với ROADMAP của từng module.
