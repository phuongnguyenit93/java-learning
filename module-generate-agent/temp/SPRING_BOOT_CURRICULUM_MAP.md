# Spring Boot Curriculum Map

Status: **STEP 1 LOCKED**

Target area:

```text
module/platform/development/programming/framework/spring-boot
```

Repository baseline:

```text
Java          21
Spring Boot   3.3.x
Spring        6.1.x family through the Boot 3.3 dependency baseline
```

This Curriculum Map owns the **area-level learning architecture** for Spring Boot. It defines module inventory, primary ownership, boundaries, dependencies, learning waves and cross-module handoff. It deliberately does not define Roadmap milestones, H2 identities, Knowledge bodies, API experiments, Quiz questions or Interview questions.

---

## 1. Area purpose

The Spring Boot area teaches how Spring Boot turns Spring applications into convention-driven, auto-configured, externally configurable, runnable, testable, observable and packageable applications.

The learner should leave the area with this mental model:

```text
Spring Framework provides core application/framework mechanisms
        ↓
Spring Boot chooses conventions and bootstrap defaults around those mechanisms
        ↓
configuration + classpath + conditions shape the application context
        ↓
the application runs with Boot-managed runtime services and, when applicable, an embedded web server
        ↓
Boot build/test/actuator/native tooling supports development, verification and production operation
```

Spring Boot is therefore taught here as an **application bootstrap, convention, integration and runtime/tooling layer**, not as a replacement curriculum for Spring Framework, Spring Data, Spring Security, messaging frameworks, generic observability, Docker, servlet containers or GraalVM internals.

---

## 2. Learner profile and prerequisites

Primary learner:

- already understands Java fundamentals;
- has basic Spring Framework container/bean/DI knowledge;
- can read ordinary Gradle/Maven dependency declarations;
- can run a JVM application and inspect configuration/log output.

Recommended neighboring prerequisites:

```text
spring-framework/core-container
→ Bean/ApplicationContext/DI foundation

spring-framework/web or reactive
→ required before deep MVC/WebFlux mechanics, but not required before Boot fundamentals
```

The Spring Boot area may introduce Boot-facing references to Spring mechanisms, but it must hand off framework internals to their primary owners rather than duplicate them.

---

## 3. Canonical module inventory

The Spring Boot area contains exactly these nine learning modules for the current curriculum:

| Order | Module | Role | Primary responsibility |
| ---: | --- | --- | --- |
| 10 | `fundamentals` | Foundation | What Spring Boot is, why it exists, basic bootstrap model, `SpringApplication`, `@SpringBootApplication`, starters, high-level embedded server/lifecycle/packaging mental models, DevTools orientation, Framework-vs-Boot boundary |
| 20 | `externalized-configuration` | Core mechanism | Config Data, property sources and precedence, `Environment`, `@Value`, `@ConfigurationProperties`, relaxed/nested binding, profiles, validation and configuration metadata |
| 30 | `auto-configuration` | Core mechanism | Auto-configuration model, conditional registration, classpath/property-driven behavior, ordering, exclusion, diagnostics, custom auto-configuration and starter authoring |
| 40 | `application-runtime` | Runtime integration | Runtime lifecycle/events/runners, availability/failure/exit handling, task execution/scheduling auto-configuration, virtual threads, lazy initialization, Boot logging integration, generic SSL bundle abstraction, Docker Compose development-time integration and runtime customization boundary |
| 50 | `web-runtime` | Runtime integration | Boot web-application type detection, embedded server selection/auto-configuration, server properties/customization, HTTP server features, web-server TLS consumption, forwarded headers/proxy deployment and graceful shutdown |
| 60 | `build-tooling-packaging` | Build/deployment handoff | Boot Gradle/Maven plugins, Boot BOM/dependency-management usage, `bootRun`, `bootJar`/`bootWar`, executable archives/Boot Loader, layered archives, reproducible packaging, `bootBuildImage`, Cloud Native Buildpacks and Boot-specific container build strategy |
| 70 | `actuator` | Production runtime surface | Production-ready Actuator model, endpoint model/exposure, health/info, metrics integration, loggers/runtime diagnostics, custom endpoints, security boundary and relation to observability |
| 80 | `testing` | Verification | Boot test bootstrap, `@SpringBootTest`, web environment, test slices, test auto-configuration, property overrides, dependency replacement, Testcontainers service connections and Boot-oriented integration-test strategy |
| 90 | `native-image` | Advanced | Spring Boot AOT/native-image integration, GraalVM build path, closed-world consequences, runtime hints as consumed by the Boot/Spring AOT pipeline, native testing, trade-offs and limitations |

`module-order.yml` must materialize this exact sibling order unless a future Step 1 curriculum migration changes it.

---

## 4. Module ownership and boundaries

### 4.1 `fundamentals`

Primary ownership:

```text
Spring Boot purpose and value proposition
SpringApplication bootstrap mental model
@SpringBootApplication composition at learner-introduction level
starter concept and dependency convenience model
high-level embedded-server mental model
high-level application lifecycle mental model
high-level packaging mental model
DevTools orientation
Spring Framework vs Spring Boot responsibility boundary
```

Boundary:

- does **not** own detailed Config Data/property precedence → `externalized-configuration`;
- does **not** own condition evaluation/custom auto-configuration → `auto-configuration`;
- does **not** own detailed runtime events/execution/logging/Compose → `application-runtime`;
- does **not** own embedded-server configuration/TLS/proxy/shutdown → `web-runtime`;
- does **not** own executable archive/build-image internals → `build-tooling-packaging`;
- does **not** own Actuator/testing/AOT detail.

The existing Embedded Server, Application Lifecycle and Packaging chapters are intentionally **mental-model handoff chapters**, not alternate deep owners.

### 4.2 `externalized-configuration`

Primary ownership:

```text
Config Data
application.properties / application.yml as Boot configuration sources
PropertySource ordering and override model
Environment-facing Boot configuration behavior
@Value usage in Boot configuration context
@ConfigurationProperties
relaxed binding
nested/collection binding
profile-specific configuration
configuration-properties validation
configuration metadata / configuration processor
```

Boundary:

- Spring Framework `Environment`, conversion, binding and validation internals remain with Spring Framework owners;
- external secret-management products remain infrastructure/security curricula;
- Spring Cloud Config remains Spring Cloud ownership;
- test-only property override mechanics are consumed by `testing` but the general precedence model remains owned here.

### 4.3 `auto-configuration`

Primary ownership:

```text
@EnableAutoConfiguration and Boot auto-configuration model
classpath-driven conditions
property-driven conditions
ConditionalOn* usage in Boot auto-configuration
back-off / ConditionalOnMissingBean-style convention
auto-configuration ordering
exclusions
Condition Evaluation Report / debug diagnostics
custom @AutoConfiguration
AutoConfiguration.imports registration
custom starter composition
testing an auto-configuration at the Boot layer
```

Boundary:

- Spring Framework `@Configuration`, bean registration and container mechanics remain `spring-framework/core-container`;
- generic condition semantics that are purely Spring Framework belong to Framework curriculum; this module owns their Boot auto-configuration role;
- dependency/BOM mechanics remain `build-tooling-packaging`.

### 4.4 `application-runtime`

Primary ownership:

```text
SpringApplication runtime lifecycle beyond the fundamentals overview
application events/listeners at the Boot lifecycle boundary
ApplicationArguments
ApplicationRunner / CommandLineRunner
availability states and Boot liveness/readiness model
Boot failure-analysis / exit handling concepts
Boot task execution and scheduling auto-configuration
Java 21 virtual-thread integration via Boot configuration
lazy initialization and Boot startup/runtime tuning boundaries
Boot LoggingSystem/default logging integration and logging configuration behavior
SslBundle / SslBundles as a reusable Boot runtime abstraction
Spring Boot Docker Compose development-time integration and service-connection lifecycle
runtime customization handoff points
```

Boundary:

- generic Java threads/virtual-thread semantics belong to Java concurrency curricula;
- Spring Framework task execution/scheduling abstractions belong to `spring-framework/concurrency`;
- generic logging concepts, pipelines, storage and analysis belong to `infrastructure/system/observability/logging`;
- generic TLS/PKI concepts belong to security/network curricula;
- generic Docker image/container/network/volume/Compose mechanics belong to `infrastructure/devops/containerization`;
- web-server-specific SSL use and graceful shutdown belong to `web-runtime`;
- Actuator health endpoints consume availability state but do not own the lifecycle model.

### 4.5 `web-runtime`

Primary ownership:

```text
Boot web application type detection
embedded web server dependency/default selection
Servlet embedded server auto-configuration
Reactive embedded server auto-configuration
server.* property model at the Boot layer
WebServerFactory/customizer-style server customization
Boot HTTP server features such as compression/connection settings
web-server TLS configuration and consumption of SslBundle
forwarded-header / reverse-proxy Boot deployment configuration
graceful shutdown of Boot-managed web servers
Boot web defaults and ownership handoff
```

Boundary:

- DispatcherServlet, controller mapping, MVC binding, WebFlux request processing and framework error-resolution mechanics remain Spring Framework `web` / `reactive`;
- Tomcat/Jetty/Undertow generic container internals belong to `infrastructure/system/runtime/servlet-container` or their technology owner;
- generic HTTP, TLS and reverse-proxy protocol mechanics remain infrastructure/network ownership.

### 4.6 `build-tooling-packaging`

Primary ownership:

```text
Spring Boot Gradle Plugin
Spring Boot Maven Plugin
spring-boot-dependencies/BOM usage from Boot build tooling perspective
bootRun / plugin-run workflow
bootJar / bootWar
repackaged executable archives
Spring Boot Loader / BOOT-INF packaging mental model
layered JAR/WAR
reproducible packaging concerns exposed by Boot tooling
bootBuildImage
Cloud Native Buildpacks integration
Boot-specific Dockerfile vs Buildpacks/layer/cache decision
handoff from Boot artifact/image production to deployment infrastructure
```

Boundary:

- generic Gradle/Maven concepts belong build-tool curricula;
- generic Dockerfile, image layers, BuildKit, registry and container runtime knowledge belongs `infrastructure/devops/containerization`;
- CI/CD pipelines and deployment strategies belong DevOps curricula;
- native-image production remains primarily `native-image`, though build plugins are supporting mechanisms.

### 4.7 `actuator`

Primary ownership:

```text
Actuator purpose and production-ready feature model
endpoint model
endpoint discovery/exposure/configuration
health and HealthIndicator integration
info/environment-facing operational endpoints at the Actuator layer
metrics endpoint and Boot/Micrometer integration boundary
loggers endpoint
thread dump / heap dump diagnostic endpoints
custom Actuator endpoint authoring
Actuator endpoint security/exposure boundary
relationship to external observability systems
```

Boundary:

- metrics/tracing/logging theory, telemetry pipelines and backends remain `infrastructure/system/observability`;
- Spring Security authorization/authentication mechanics remain `spring-security`;
- JVM diagnostic mechanics such as thread/heap analysis remain Java/JVM/runtime-analysis owners; Actuator only owns the Boot endpoint surface exposing them.

### 4.8 `testing`

Primary ownership:

```text
Boot-aware test application bootstrap
@SpringBootTest
WebEnvironment
Boot test slices
web/data slice selection at the Boot layer
test auto-configuration
Boot-specific test property overrides
dependency/bean replacement patterns supported by the Boot testing stack
Testcontainers + Spring Boot service connections
Boot integration-test strategy and context-size trade-offs
```

Boundary:

- Spring TestContext Framework mechanics remain `spring-framework/testing`;
- JUnit/Mockito/AssertJ fundamentals belong testing-tool owners;
- generic Testcontainers container mechanics are not duplicated beyond what is necessary to explain Boot service connections.

### 4.9 `native-image`

Primary ownership:

```text
why Boot applications need AOT preparation for native image
Spring Boot AOT pipeline
Boot/GraalVM native-image build integration
closed-world consequences for a Boot application
runtime hints as inputs to Spring AOT/native integration
reflection/resource/proxy hint categories at the Boot/Spring AOT usage level
native build and run workflow
native testing/AOT test processing
startup/memory/build-time trade-offs
known compatibility/metadata limitations
```

Boundary:

- GraalVM compiler/runtime internals do not become Boot curriculum;
- generic Java reflection/proxy semantics belong Java/Spring Framework owners;
- `build-tooling-packaging` may show the plugin command that triggers a native build but does not own native-image semantics.

---

## 5. Cross-area ownership guardrails

The following concepts are intentionally **not new Spring Boot modules**.

| Concept | Spring Boot treatment | Primary owner elsewhere |
| --- | --- | --- |
| Logging | Boot logging bootstrap/configuration/runtime integration is a chapter of `application-runtime` | `infrastructure/system/observability/logging` owns general logging, pipelines, storage and analysis |
| Docker Compose | Boot development-time Compose integration is a chapter of `application-runtime` | `infrastructure/devops/containerization` owns Docker/Compose mechanics |
| Docker image / Dockerfile | Boot packaging decision/integration appears in `build-tooling-packaging` | `infrastructure/devops/containerization` owns generic image/Dockerfile/BuildKit mechanics |
| Observability | Actuator owns Boot production endpoints and integration boundary | `infrastructure/system/observability` owns logging/metrics/tracing systems and telemetry operations |
| Web MVC / WebFlux | `web-runtime` owns Boot bootstrap/server runtime | `spring-framework/web` and `spring-framework/reactive` own framework request-processing mechanics |
| Servlet container internals | Boot owns selection/configuration integration | `infrastructure/system/runtime/servlet-container` owns container internals |
| Security | Boot modules may discuss endpoint/config integration boundaries | `spring-security` owns authentication/authorization/security filters |
| Data access | Boot may auto-configure data technology, but this area does not duplicate it as a module | `spring-data` / Spring Framework data-access owners |
| Messaging | Boot starter/auto-config examples are supporting context only | Spring AMQP/Kafka/Integration/JMS owners |
| Java virtual threads | Boot owns enablement/integration behavior | Java concurrency/version curricula own Java semantics |
| Generic testing framework | Boot owns Boot test bootstrap/slices/service connections | Spring Framework testing + JUnit/test-tool owners |
| Generic GraalVM internals | Boot owns application integration and AOT/native workflow | GraalVM/Java runtime sources own compiler/runtime internals |

This prevents the Spring Boot area from turning into a duplicate index of every technology that Boot can auto-configure.

---

## 6. Dependency graph

Learning dependencies are intentionally different from Gradle dependencies.

```text
fundamentals
    ├──→ externalized-configuration
    └──→ auto-configuration

externalized-configuration ──┐
auto-configuration ──────────┼──→ application-runtime
fundamentals ────────────────┘

application-runtime
    └──→ web-runtime

fundamentals + externalized-configuration + auto-configuration
    └──→ build-tooling-packaging

application-runtime + externalized-configuration
    └──→ actuator

fundamentals + externalized-configuration + auto-configuration
    └──→ testing

auto-configuration + application-runtime + build-tooling-packaging + testing
    └──→ native-image
```

Notes:

- `externalized-configuration` and `auto-configuration` can be learned in either local order after `fundamentals`, but recommended presentation puts configuration first because property/classpath conditions are easier to reason about after the learner understands Boot configuration inputs.
- `web-runtime` depends on the generic runtime/bootstrap model but does not require Actuator.
- `build-tooling-packaging`, `actuator` and `testing` are supporting/core-production domains that can be partially parallelized after the central Boot model is understood.
- `native-image` is deliberately last because AOT/native behavior changes assumptions established by configuration, auto-configuration, runtime and build/test flows.

No curriculum dependency cycle is allowed between these modules.

---

## 7. Recommended learning waves

### Wave 1 — Boot foundation

```text
fundamentals
```

Goal: understand what Boot adds around Spring and the basic bootstrap/application model.

### Wave 2 — Boot decision inputs

```text
externalized-configuration
        ↓
auto-configuration
```

Goal: understand the two major forces that shape a Boot application: configuration inputs and conditional convention-based configuration.

### Wave 3 — Runtime model

```text
application-runtime
        ↓
web-runtime
```

Goal: understand how a Boot application behaves while starting/running/stopping and how that runtime specializes for web applications.

### Wave 4 — Build, operations and verification

Recommended presentation order:

```text
build-tooling-packaging
        ↓
actuator
        ↓
testing
```

These modules are not strictly linear dependencies. The order is pedagogical: produce/run the application artifact, observe a production-ready application, then study Boot-specific verification strategies in depth.

### Wave 5 — Advanced runtime form

```text
native-image
```

Goal: revisit the Boot application under build-time/AOT and closed-world constraints after the normal JVM model is stable.

---

## 8. Recommended sibling order

`module-order.yml` should contain:

```yaml
version: 1
children:
  fundamentals:
    order: 10
  externalized-configuration:
    order: 20
  auto-configuration:
    order: 30
  application-runtime:
    order: 40
  web-runtime:
    order: 50
  build-tooling-packaging:
    order: 60
  actuator:
    order: 70
  testing:
    order: 80
  native-image:
    order: 90
```

The order values are a projection of this Curriculum Map; they are not an independent curriculum source.

---

## 9. Area terminology ownership

| Term | First/full owner in this area | Consumed by |
| --- | --- | --- |
| Spring Boot | `fundamentals` | all modules |
| `SpringApplication` | `fundamentals` introduction; detailed lifecycle in `application-runtime` | testing, web-runtime |
| `@SpringBootApplication` | `fundamentals` | auto-configuration, testing |
| Starter | `fundamentals` concept; custom starter mechanics in `auto-configuration` | build-tooling-packaging |
| Config Data | `externalized-configuration` | application-runtime, testing |
| `@ConfigurationProperties` | `externalized-configuration` | application-runtime, web-runtime, auto-configuration |
| Auto-configuration | `auto-configuration` | application-runtime, web-runtime, testing, native-image |
| Condition evaluation | `auto-configuration` | testing, diagnostics |
| Application availability | `application-runtime` | actuator, web-runtime |
| `LoggingSystem` / Boot logging integration | `application-runtime` | actuator/loggers relation |
| `SslBundle` / `SslBundles` | `application-runtime` generic abstraction | `web-runtime` server TLS use |
| Docker Compose integration | `application-runtime` | testing/development workflows |
| Embedded web server | `web-runtime` deep Boot ownership; `fundamentals` overview only | actuator/testing |
| Graceful shutdown | `web-runtime` | actuator/runtime relation |
| Boot build plugin | `build-tooling-packaging` | native-image |
| Executable archive / Boot Loader | `build-tooling-packaging` | deployment handoff |
| Buildpack / `bootBuildImage` | `build-tooling-packaging` | native-image may consume supporting command |
| Actuator endpoint | `actuator` | operations/observability relations |
| Boot test slice | `testing` | technology-specific test examples |
| Service connection | `testing` for Testcontainers integration; `application-runtime` only for Docker Compose development integration | integration testing/development |
| AOT/native image | `native-image` | build-tooling-packaging supports invocation only |

---

## 10. Coverage matrix

| Major concept/domain | Primary owner | Supporting/related module | External owner/boundary |
| --- | --- | --- | --- |
| Boot purpose and convention model | `fundamentals` | all | Spring Framework remains underlying framework |
| Application bootstrap | `fundamentals` | `application-runtime`, `testing` | Spring container internals → Spring Framework |
| External configuration | `externalized-configuration` | testing/runtime | Spring `Environment` internals → Spring Framework |
| Profiles | `externalized-configuration` | testing | environment-management concepts may be referenced, not duplicated |
| Auto-configuration | `auto-configuration` | runtime/web/testing/native | Spring bean/config mechanics → Spring Framework |
| Runtime events/runners/availability | `application-runtime` | actuator/web | generic lifecycle/thread semantics elsewhere |
| Task execution and virtual-thread Boot integration | `application-runtime` | web | Java/Spring concurrency semantics elsewhere |
| Boot logging integration | `application-runtime` | actuator | observability/logging owns general logging |
| Docker Compose Boot integration | `application-runtime` | testing | DevOps containerization owns Docker/Compose |
| SSL bundle abstraction | `application-runtime` | `web-runtime` | security/network owns TLS/PKI |
| Embedded web runtime | `web-runtime` | fundamentals/testing/actuator | Spring MVC/WebFlux and container internals elsewhere |
| Web-server TLS/proxy/shutdown | `web-runtime` | application-runtime | network/runtime infrastructure boundaries |
| Boot Gradle/Maven plugins | `build-tooling-packaging` | native | generic build-tool knowledge elsewhere |
| Executable/layered packaging | `build-tooling-packaging` | fundamentals overview | generic archive/container internals elsewhere |
| OCI/Buildpacks Boot integration | `build-tooling-packaging` | native-image | DevOps containerization owns generic Docker/image mechanics |
| Production endpoints/health | `actuator` | runtime/web | observability/security owners provide adjacent depth |
| Metrics/loggers/diagnostic endpoint exposure | `actuator` | application-runtime | observability/JVM diagnostics own underlying domain |
| Boot test bootstrap/slices | `testing` | all modules may provide examples | Spring TestContext/JUnit own foundations |
| Testcontainers service connections | `testing` | application-runtime | Testcontainers/container mechanics are supporting external knowledge |
| AOT/native integration | `native-image` | auto-config/build/testing | GraalVM/runtime internals external |

No major Spring Boot-specific domain in the 3.3 baseline is currently ownerless at area level.

---

## 11. Module candidates intentionally kept as chapters, not modules

### Logging

Do not create `spring-boot/logging`.

Reason:

- Boot-specific learning journey is cohesive with application runtime initialization/configuration;
- generic logging already has a repository owner;
- a separate Boot logging module would create disproportionate taxonomy for an integration concern.

Owner: `application-runtime` chapter(s).

### Docker / Docker Compose

Do not create `spring-boot/docker`.

Split ownership:

```text
Docker Compose development-time Boot integration
→ application-runtime

bootBuildImage / Buildpacks / Boot-specific image production
→ build-tooling-packaging

generic Docker/Compose/Dockerfile/BuildKit/image/container mechanics
→ infrastructure/devops/containerization
```

### Observability

Do not create `spring-boot/observability` in the current area.

Boot-specific production endpoint/exposure integration belongs to `actuator`; telemetry systems and operational observability remain under `infrastructure/system/observability`.

### Dependency management

Do not create a dedicated module. Boot BOM/plugin-facing dependency management belongs to `build-tooling-packaging`; starters remain introduced by `fundamentals`.

### DevTools

Do not create a dedicated module. It remains developer-experience content in `fundamentals` unless future scope grows materially.

### SSL

Do not create a dedicated module. The generic Boot `SslBundle` abstraction belongs to `application-runtime`; embedded-web-server consumption belongs to `web-runtime`; TLS/PKI fundamentals remain external owners.

---

## 12. Authoritative baseline evidence

Curriculum design is checked against the Spring Boot 3.3 reference family, especially these official areas:

- Spring Boot core/features reference: application bootstrap, externalized configuration, logging, task execution/scheduling and SSL;
- auto-configuration reference: Boot auto-configuration, conditions and custom auto-configuration;
- web reference/how-to: embedded Servlet/Reactive servers and graceful shutdown;
- production-ready/Actuator reference;
- testing reference: `@SpringBootTest`, slices and Boot test support;
- Gradle/Maven plugin references: executable archives, packaging and image building;
- native-image/AOT reference: AOT processing, GraalVM integration and native testing.

Canonical official documentation entry points:

```text
https://docs.spring.io/spring-boot/3.3/reference/
https://docs.spring.io/spring-boot/3.3/reference/features/external-config.html
https://docs.spring.io/spring-boot/3.3/reference/features/task-execution-and-scheduling.html
https://docs.spring.io/spring-boot/3.3/reference/features/ssl.html
https://docs.spring.io/spring-boot/3.3/reference/web/
https://docs.spring.io/spring-boot/3.3/reference/testing/
https://docs.spring.io/spring-boot/3.3/gradle-plugin/
https://docs.spring.io/spring-boot/3.3/maven-plugin/
https://docs.spring.io/spring-boot/3.3/reference/packaging/native-image/
```

The repository currently pins Spring Boot `3.3.5`; curriculum examples and version-sensitive claims in downstream steps must stay within the 3.3.x behavior line unless an explicit upgrade task changes the baseline.

---

## 13. Large-gap review

Reviewed candidate gaps:

```text
application runtime lifecycle/execution/logging/Compose
→ RESOLVED by application-runtime

embedded web runtime / server configuration / graceful shutdown
→ RESOLVED by web-runtime

build plugins / executable packaging / OCI images / Buildpacks
→ RESOLVED by build-tooling-packaging

externalized configuration
→ owned by externalized-configuration

auto-configuration/custom starter model
→ owned by auto-configuration

production endpoints/health/metrics integration
→ owned by actuator

Boot test bootstrap/slices/service connections
→ owned by testing

AOT/native-image integration
→ owned by native-image
```

No additional real Spring Boot module is required for the current 3.3.x curriculum. New topics should default to chapters of these nine owners unless they later satisfy the Step 1 independent-module criteria.

---

## 14. Area acceptance check

### Foundational ownership

- Spring Boot definition/purpose/bootstrap has an owner: `fundamentals`.
- Configuration and auto-configuration each have explicit owners.
- Normal JVM runtime and web runtime each have explicit owners.
- Build/package, production operations, testing and native runtime form each have explicit owners.

### Duplication

- `fundamentals` owns only high-level embedded-server/lifecycle/packaging orientation; deep content hands off to dedicated owners.
- `application-runtime` logging and Docker Compose sections are explicitly Boot integration, not generic infrastructure curricula.
- `web-runtime` does not own Spring MVC/WebFlux request-processing mechanics.
- `actuator` does not own observability backend/domain theory.
- `testing` does not re-own Spring TestContext/JUnit fundamentals.

### Dependency health

- no circular learning dependency exists;
- advanced native-image content appears after normal Boot runtime/configuration/build/testing foundations;
- the recommended order is readable by a learner new to Spring Boot but already familiar with basic Spring.

### Module granularity

- all nine modules support an independent mental model and substantial learning journey;
- logging, Docker, observability, dependency-management, DevTools and SSL remain chapters/integration topics because separate modules would either duplicate external owners or be too narrow.

### Ordering

- `module-order.yml` is expected to match the nine-module order in Section 8.

Result: **Curriculum architecture LOCKED.**

Repository order-generation validation completed successfully with all nine direct children preserved in the approved order. Step 1 is therefore **LOCKED** for the current Spring Boot 3.3.x curriculum.

---

## 15. Step 1 handoff

After Step 1 lock, Step 2 should design/review each module Roadmap independently while preserving this area-level ownership:

```text
fundamentals
externalized-configuration
auto-configuration
application-runtime
web-runtime
build-tooling-packaging
actuator
testing
native-image
```

Step 2 may choose milestone granularity and internal learning order inside each module. It must not silently move primary ownership between modules or create a tenth Spring Boot module. Any such need is a **CURRICULUM GAP** and must return to Step 1.
