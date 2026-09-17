# AGENTS.md

## 1. Purpose

This file is the bootstrap context for AI agents working in `java-learning`.

Read this file before making architecture or code changes. It summarizes the repository structure, source-of-truth rules, Gradle lifecycle, ownership boundaries, current intentional decisions, and the expected workflow when analyzing or modifying code.

This file is intentionally more operational than `ARCHITECTURE.md`.

Use:

```text
AGENTS.md       → how an AI should work in this repository
ARCHITECTURE.md → why the repository is structured this way
README.md       → high-level project orientation
STRUCTURE.md    → generated module tree/navigation
```

When documentation conflicts with live implementation, inspect the implementation and report the inconsistency instead of silently assuming the documentation is correct.

---

## 2. Repository purpose

`java-learning` is a Java/Spring Boot learning repository organized as a Gradle multi-project/composite build.

It is not one monolithic production application.

The repository is designed so that many independent learning/application modules can reuse the same build automation and shared runtime infrastructure.

Current technical baseline:

```text
Java            21
Spring Boot     3.3.x
Gradle          repository wrapper 8.5
Build language  Groovy custom plugins
Testing         JUnit Platform
Runtime infra   Docker Compose where needed
```

Do not assume every module has the same runtime architecture or purpose.

---

## 3. Read order

For repository-level work, read in this order:

```text
1. AGENTS.md
2. ARCHITECTURE.md
3. README.md
4. settings.gradle
5. build.gradle
6. project-orchestration/
7. project-build/gradle-runtime/
8. project-build/springboot-runtime/
9. STRUCTURE.md when module navigation is needed
10. module metadata/build.gradle for the concrete task
11. module source packages only when the requested work requires them
```

Do not read the entire `module/` source tree just to answer a build-system question.

The architecture documentation intentionally does not claim knowledge of package design inside every learning module.

---

## 4. Main architecture boundaries

Repository shape:

```text
java-learning/
├── module/                         # learning/application modules
├── internal/                       # internal supporting modules/resources
├── project-build/
│   ├── gradle-runtime/             # Gradle/build-time implementation
│   └── springboot-runtime/         # shared Spring Boot runtime implementation
├── project-orchestration/          # policy/composition layer
├── settings.gradle
├── build.gradle
├── README.md
├── ARCHITECTURE.md
├── AGENTS.md
└── STRUCTURE.md
```

The three infrastructure boundaries are:

```text
project-orchestration
    = WHAT should be applied and WHEN

project-build/gradle-runtime
    = HOW build-time behavior is implemented

project-build/springboot-runtime
    = HOW shared runtime behavior works inside Spring Boot applications
```

Do not move code between these boundaries just to reduce file count.

---

## 5. Responsibility model

The repository follows this responsibility split:

```text
Orchestration → capability selection and ordering
Plugin        → Gradle lifecycle wiring
Service       → implementation logic
Task          → explicit execution / side effect
Extension     → typed DSL/configuration surface
Utils         → stateless reusable helpers
```

Prefer this model when adding or refactoring build capabilities.

Avoid putting large algorithms directly in:

```text
root build.gradle
settings.gradle
orchestration plugins
Gradle ext closures
```

unless the behavior is genuinely trivial.

---

## 6. Composite build structure

Root `settings.gradle` includes:

```groovy
pluginManagement {
    includeBuild('project-build/gradle-runtime')
    includeBuild('project-orchestration')
}
```

and applies:

```groovy
id 'com.example.settings-orchestration'
```

`project-orchestration` itself includes `../project-build/gradle-runtime` and depends on:

```text
com.example.learning:gradle-runtime:1.0.0
```

The composite build substitutes that dependency with the local included build.

Do not replace this with ad-hoc copies of Gradle logic in the root project.

---

## 7. Orchestration plugins

Three orchestration entry points exist:

```text
com.example.settings-orchestration
com.example.root-orchestration
com.example.module-orchestration
```

### Settings orchestration

Current order:

```text
PresetSetupPlugin
    ↓
PropertiesSetupPlugin
```

This order matters.

Preset setup performs module discovery, inclusion, metadata synchronization, and settings-time generated information.

Properties setup injects synchronized metadata into Gradle `Project` objects through `projectsLoaded`.

### Root orchestration

Current capabilities:

```text
CatalogSetupPlugin
StructureSetupPlugin
DatabaseSetupPlugin
CleanupSetupPlugin
```

Root-only plugins should remain root-only.

### Module orchestration

Current conceptual flow:

```text
real module guard
    ↓
DependencySetupPlugin
    ↓
ConfigSetupPlugin
    ↓
YmlSetupPlugin
    ↓
EnvSetupPlugin       if enabled
    ↓
ReadmeSetupPlugin    if enabled
    ↓
SwaggerSetupPlugin   if enabled
    ↓
ExecutionContextSetupPlugin if BUILD_EXECUTION_CONTEXT=TRUE
    ↓
TaskSetupPlugin      if USE_TASK=TRUE
    ↓
DockerSetupPlugin    if docker-compose.yml exists
```

`ConfigSetupPlugin` and `YmlSetupPlugin` are currently applied to every real module by convention.

---

## 8. Real module identity

A real module is identified physically by a local:

```text
gradle.properties
```

Rule:

```text
<directory>/gradle.properties exists
    → real module

otherwise
    → intermediate/container project or ordinary directory
```

Do not infer module identity from folder name, `IS_MODULE`, or Gradle hierarchy alone.

`IS_MODULE` is legacy and must not be reintroduced as the real-module discriminator.

---

## 9. Module types

Module behavior is controlled by:

```text
MODULE_TYPE
```

Current values:

```text
SERVLET
REACTIVE
LIBRARY
PLATFORM
```

High-level semantics:

```text
SERVLET
→ runnable Spring MVC / Servlet application
→ Java/resources structure
→ Spring Boot executable behavior
→ spring-boot-starter-web
→ generated main class may extend SpringBootServletInitializer

REACTIVE
→ runnable Spring WebFlux application
→ Java/resources structure
→ Spring Boot executable behavior
→ spring-boot-starter-webflux
→ generated main class is a regular @SpringBootApplication class

LIBRARY
→ reusable Java/configuration code
→ Java/resources structure
→ bootJar/bootRun disabled, plain jar enabled

PLATFORM
→ platform/resource/config composition
→ physical Java structure is not automatically created
→ plain jar behavior, not runnable
```

Do not infer type from the path when `MODULE_TYPE` exists.

`APPLICATION` is a legacy value. Metadata synchronization migrates legacy:

```text
APPLICATION → SERVLET
```

Do not add `APPLICATION` back to `ModuleType` as a compatibility alias. New metadata and generated schema must use:

```text
SERVLET,REACTIVE,LIBRARY,PLATFORM
```

Current convention intentionally still gives PLATFORM modules the common Java/Spring Boot plugin baseline because the project currently relies on it for source/build behavior. Do not remove that baseline unless the user explicitly asks to redesign it.

---

## 10. Canonical metadata

Canonical schemas live at:

```text
project-build/gradle-runtime/src/main/resources/automation/master.json
project-build/gradle-runtime/src/main/resources/automation/properties.json
```

Module-local projections:

```text
<module>/master.json
<module>/properties.json
```

`master.json` contains identity and feature flags.

`properties.json` contains detailed settings for enabled feature groups.

Current important master metadata includes:

```text
MODULE_TYPE
JAVA_BASE_PACKAGE
SERVICE_NAME
SERVICE_NAME_DESCRIBE
IS_MODULE_DEPEND
BUILD_ENV
BUILD_YML
BUILD_README
BUILD_TESTER
BUILD_SWAGGER
BUILD_EXECUTION_CONTEXT
BUILD_DATABASE_MODULE
ADD_MODULE_DEPEND
USE_DATABASE
USE_TASK
```

Do not add new metadata keys casually. Check whether the concept already has an owner and whether it belongs in `master.json` or grouped `properties.json`.

---

## 11. `JAVA_BASE_PACKAGE`

Java package ownership was intentionally moved out of root/global Gradle extra properties.

The canonical default is:

```text
JAVA_BASE_PACKAGE = com.example.learning
```

Modules may override it in their own `master.json`.

Example:

```text
project-build/springboot-runtime/swagger
JAVA_BASE_PACKAGE = com.example.projectbuild.swagger
```

`ModuleStructureService` reads:

```groovy
project.findProperty('JAVA_BASE_PACKAGE')
```

Do not restore hidden global state like:

```groovy
gradle.extensions.extraProperties.set('basePackage', ...)
```

`MAIN_CLASS_PATH` is the source used by IntelliJ run-config generation; do not rebuild the main-class package independently from a global base package.

---

## 12. Metadata synchronization ownership

Canonical schema metadata is build-owned.

Module-local `VALUE` is developer/module-owned.

Current synchronization rule:

```text
canonical key/schema
    ↓
copy DESCRIPTION / TYPE / GROUP / defaults

existing module VALUE is nonblank
    ↓
preserve module VALUE
```

When a canonical field is newly introduced with a default, existing modules receive the default unless they already have a nonblank override.

`MODULE_TYPE` has one explicit legacy migration during synchronization:

```text
APPLICATION → SERVLET
```

This migration is intentional and is not a generic rule that arbitrary module-owned values may be rewritten.

Do not overwrite module-specific nonblank values during schema synchronization.

Do not treat generated module JSON structure as independently authoritative from the canonical schema.

---

## 13. Property injection precedence

`SettingPropertiesInjectionService` injects:

```text
master.json
    ↓ first
properties.json
    ↓ second
```

Therefore, when the same property key exists in both, the later `properties.json` value wins in the Gradle project extra properties.

Preserve this precedence unless a requirement explicitly changes configuration semantics.

---

## 14. Source of truth map

Use this map before changing data:

```text
Real module existence
→ filesystem + local gradle.properties

Canonical master schema
→ gradle-runtime resources/automation/master.json

Canonical grouped settings schema
→ gradle-runtime resources/automation/properties.json

Module-specific values
→ module-local master.json / properties.json VALUE

Plugin registry input
→ plugin registry/generator resources in gradle-runtime

Typed/generated registry
→ generated enums such as ProjectPluginEnum, ModuleListEnum, DatabaseListEnum

Project tree documentation
→ filesystem/module metadata → generated STRUCTURE.md

Task DSL reference
→ task definition resources + enabled module features → generated task.gradle
```

Never edit a generated projection as a substitute for editing its source of truth.

---

## 15. Generated artifacts

Important generated artifacts include:

```text
ProjectPluginEnum
ModuleListEnum
DatabaseListEnum
module-depend.json
STRUCTURE.md
module-structure.txt
task.gradle
application-merged.yml
generated README/menu fragments
META-INF/execution-context/source-context.json
```

Generated output must prefer:

```text
deterministic ordering
idempotency
write-if-changed
no duplicate generated content
minimal timestamp churn
```

Do not manually patch generated output unless the task is specifically about the generator output itself and the user understands the source-of-truth implications.

---

## 16. Strict README idempotency

README/internal-menu generation has a strict invariant:

```text
run N times
=
run once
```

Generated menu entries, `<details>` blocks, separators, back-to-top links, and similar generated markup must never duplicate across runs.

Preferred strategy:

```text
identify/remove previous generated structure
    ↓
rebuild from canonical anchor/content structure
```

Do not implement append-only generation for these sections.

---

## 17. Human-owned vs generated-owned files

Before editing a file, determine its ownership.

### Human-owned examples

```text
application source code
module build.gradle
module-specific metadata VALUE
application.yml after meaningful content exists
README prose written by developer
main application class after initial generation
```

### Generated-owned examples

```text
ProjectPluginEnum
ModuleListEnum
DatabaseListEnum
STRUCTURE.md
module-depend.json
module-structure.txt
task.gradle
application-merged.yml
generated menu fragments
```

### Mixed ownership example

`master.json`:

```text
schema fields → build-owned
VALUE         → module/developer-owned
```

Never overwrite human-owned content without an explicit requirement.

---

## 18. Gradle lifecycle awareness

Always determine which phase owns the behavior:

```text
Settings / Initialization
Configuration
projectsLoaded
projectsEvaluated
Task execution
Spring runtime
```

Important current examples:

```text
module discovery/include
→ Settings phase

metadata injection into Project
→ projectsLoaded

module plugin wiring
→ project configuration

module dependency resolution
→ deferred until projectsEvaluated

dependency catalog / structure generation
→ projectsEvaluated

explicit destructive/external operation
→ task execution

Spring beans / web configuration
→ application runtime
```

Do not move behavior to an earlier phase just because it makes the code shorter.

---

## 19. Dependency setup

Logical module lookup is based primarily on:

```text
SERVICE_NAME
```

`ModuleProjectUtils` resolves logical service names to Gradle projects.

`DependencySetupPlugin` creates its DSL during configuration but performs the actual dependency wiring after project evaluation.

When propagating dependencies, preserve Gradle configuration semantics.

Do not convert:

```text
annotationProcessor
```

into:

```text
implementation
api
```

just to simplify dependency handling.

---

## 20. Root build policy

Keep root `build.gradle` relatively thin.

Root responsibilities currently include:

```text
plugin versions/declarations
group/version
Java version
repositories
Spring Cloud BOM
module orchestration application
bootJar destination policy
```

Do not move build algorithms or filesystem generators back into root `build.gradle` unless explicitly requested.

---

## 21. Task architecture

The manual task system has two enablement layers.

Layer 1:

```text
USE_TASK=TRUE
→ TaskSetupPlugin is applied
```

Layer 2:

```text
task/module-task-list.json
task/task-extension-list.json
→ feature-specific task plugins selected by propCheck
```

An empty `propCheck` means the task type is always enabled once the task system itself is active.

`task.gradle` is a generated DSL reference, not a human configuration file.

Do not reintroduce `USE_TASK` checking inside every task plugin when orchestration already owns that decision.

---

## 22. YML rules

`application.yml` and `application-merged.yml` have different ownership.

```text
application.yml
→ runtime source of truth for the application
→ human-owned after meaningful content exists

application-merged.yml
→ generated suggestion/reference
→ not runtime source of truth
```

Conceptual flow:

```text
dependency/library/platform config
        +
application config
        ↓
application-merged.yml
        ↓
developer review
        ↓
application.yml
```

Do not make runtime automatically depend on `application-merged.yml` unless the user explicitly redesigns the contract.

YML composition dependencies come from both explicit module configuration and derived capabilities.

Current derived rules include:

```text
MODULE_TYPE=SERVLET
→ SPRING_WEB/application-module.yml

MODULE_TYPE=REACTIVE
→ SPRING_REACTIVE/application-module.yml

BUILD_SWAGGER=TRUE
→ GLOBAL_SWAGGER_CONFIG/application-module.yml
```

The Swagger YML dependency intentionally points to the stack-neutral core, not to the Servlet/Reactive runtime adapters. Do not duplicate the same Swagger YML contract in both adapters unless stack-specific configuration actually becomes necessary.

---

## 23. ENV rules

ENV setup and explicit ENV generation intentionally have different ownership behavior.

Setup phase:

```text
create defaults only when missing
avoid overwriting existing .env
```

Explicit ENV generation task:

```text
developer intentionally invokes generator
→ generator may rebuild/synchronize according to its task contract
```

The `.env` generator intentionally includes a timestamp so the user can see when synchronization/generation happened.

Do not flag the timestamp as an idempotency bug without understanding this explicit product decision.

---

## 24. Swagger build-time ownership

Swagger build automation belongs to:

```text
project-build/gradle-runtime
```

Responsibilities include generation/copying of Swagger-related descriptions, README resources, metadata and task support.

Per-API human-owned metadata includes fields such as:

```text
summary
description
videoYoutubeId
execution
```

`videoYoutubeId` rule:

```text
if missing during generation
→ create default placeholder value

once present
→ never overwrite during regeneration
```

This preservation rule applies to both active entries and historical/stale entries such as `usage=false`.

Do not regenerate human-owned Swagger fields from scratch.

`api-execution.yml` is a mixed-ownership Swagger artifact with the shape:

```text
Controller
    ↓
methodSignature
    ↓
execution  → human-owned HTML
usage      → generated-owned activity flag
```

The method key must use method signature identity rather than method name alone so overloaded controller methods do not collide.

`execution` follows the same preservation rule as other human-owned Swagger metadata:

```text
execution key missing
→ initialize a localized HTML placeholder

execution key already exists
→ preserve it exactly, including intentionally blank content

method no longer exists
→ keep the historical entry and set usage=false
```

When writing or reviewing `execution`, do not treat it as a source-code paraphrase. Its primary role is a **guided execution explanation** that should still teach the experiment to a reader who has not opened the Java source.

Responsibility split:

```text
summary
→ what topic/API is this?

description
→ what is this API intended to demonstrate or teach?

execution
→ how does the experiment unfold step by step, why do the steps matter,
  what source-level evidence supports the behavior, what should the user observe,
  and what conclusion should be learned from the result?
```

For nontrivial learning APIs, `execution` should normally cover these semantic layers when applicable:

```text
1. Concept
   → what the method/experiment is proving

2. Flow
   → what happens step by step at runtime

3. Meaning
   → why each important step matters

4. Code evidence
   → relevant method/advice/lock/future/queue/etc. details from the implementation,
     colocated with the execution step that the code proves

5. Observation
   → which response field, trace event, state, ordering, exception or result proves the behavior

6. Conclusion
   → the learning point the reader should retain
```

Do not require the reader to inspect the source code before the `execution` text becomes understandable. Source-level details are valuable and should remain, but they are evidence for the concept rather than a prerequisite for understanding it.

Code evidence should normally be embedded directly inside the `<li>` for the runtime step it explains instead of being collected into one large code section after the whole flow. The preferred reading order is:

```text
step explanation
→ why the step matters
→ focused code snippet that causes/proves the behavior
→ observation/evidence when applicable
```

Do not force every `<li>` to contain code. Add a snippet only when source lines materially help explain or prove that step. If the same code has already been shown in a nearby step, do not repeat it without a learning reason.

Keep snippets small and focused. Prefer the minimum lines needed to establish the behavior rather than copying complete controllers, services, aspects, helpers, or methods. Code is evidence, not decoration and not a substitute for the explanation.

When useful, identify the source context immediately before the snippet, for example the relevant class/method name. Prefer stable class/file + method identity over source line numbers because line numbers become stale easily.

Because `execution` is raw HTML, escape source characters that could be interpreted as HTML when placing code inside `<pre><code>...</code></pre>` (for example `<` as `&lt;`, `>` as `&gt;`, and `&` as `&amp;` where required).

Prefer concise HTML such as `<p>` + `<ol><li>...</li></ol>` and a final conclusion paragraph. Use YAML block scalars for substantial HTML so punctuation such as `: ` and multiline content remain parse-safe.

### Swagger ↔ README relationship contract

Swagger learning documentation may link controllers and methods back to module README content through human-owned `readmeRelated` metadata.

This contract is manual/semi-manual by design. Do not infer controller-to-README relationships from controller names, package names, folder names, summaries, or semantic similarity. The human declares the relationship; the generator may derive ordering, labels, validation status, and navigation metadata from that relationship.

Controller relationship belongs in `controller-description.yml`:

```yaml
ProxyMentalModelController:
  description: |-
    ...
  readmeRelated:
    file: 3.Proxy/Proxy.md
```

`readmeRelated.file` is relative to `readme/<language>/menu/` and is human-owned. The generator should create the field for newly generated controllers so developers can discover the feature, but once present it must not overwrite the configured value during regeneration.

Controller README resolution rules:

```text
valid file mapping
→ folder name must start with a numeric chapter prefix such as 3.Proxy or 11.ProxyFactory
→ parse that prefix as an integer, never lexicographically
→ read the first Markdown H1 as the localized chapter title
→ display chapter information on a separate line above the controller name

same chapter mapped by multiple controllers
→ sort those controllers alphabetically

missing mapping
→ sort after all valid README-linked controllers, alphabetically
→ VI: Chưa có tài liệu tương ứng trong README
→ EN: No related documentation in README yet

configured file missing or folder prefix invalid
→ mark the mapping invalid
→ warn during generation
→ continue generating Swagger
```

Method relationship belongs in `api-descriptions.yml` under the exact method-signature entry:

```yaml
ProxyMentalModelController:
  inspectProxy():
    summary: ...
    description: ...
    readmeRelated:
      anchor: proxy-demo
    usage: true
```

By default a method inherits the controller `readmeRelated.file`. A method may override the file when its knowledge belongs to another README chapter:

```yaml
readmeRelated:
  file: 11.ProxyFactory/ProxyFactory.md
  anchor: proxy-factory-demo
```

Method mapping is manual. Do not use fuzzy or semantic matching to guess an anchor. Resolve the configured anchor exactly against README anchor markup such as `<a id="proxy-demo">`.

Method README resolution and ordering rules:

```text
valid anchor
→ derive the localized section heading from the heading that owns the anchor
→ derive method order from anchor position inside the resolved README file

multiple methods mapped to the same anchor
→ allowed
→ sort alphabetically within that anchor position

missing mapping
→ sort after methods with valid README mappings, alphabetically
→ VI: Method này chưa có nội dung README
→ EN: This method does not have README content yet

configured file or anchor invalid
→ mark INVALID_FILE or INVALID_ANCHOR as appropriate
→ warn during generation
→ continue generating Swagger
```

README relationships resolve independently for each configured language. Do not silently fall back from VI to EN or from EN to VI when a localized README file or anchor is missing.

The resolved Swagger UI should expose the relationship clearly:

```text
Controller:
Chapter 03 · <localized README H1>
<Controller display name>

Method:
README · Chapter 03 · <localized README section heading>
```

The method README relationship should be clickable and should use the existing Swagger README navigation so the selected README file and anchor open in the same Swagger tab. Do not duplicate the whole README inside an operation panel and do not open a new tab by default.

`readmeRelated.file` and `readmeRelated.anchor` are human-owned metadata. Preserve them for active and stale entries, including `usage=false`. Missing fields should be generated as discoverable blank placeholders rather than omitted entirely.

This contract is implemented across Swagger build-time metadata generation/validation and the shared Swagger runtime/UI. When changing it, keep generator preservation, localized README resolution, OpenAPI sorting/extensions, and same-tab README navigation consistent with one another.

---

## 25. Swagger runtime ownership

Shared Swagger runtime is split into a stack-neutral core plus web-stack adapters:

```text
project-build/springboot-runtime/swagger
project-build/springboot-runtime/swagger-servlet
project-build/springboot-runtime/swagger-reactive
```

Service identities:

```text
GLOBAL_SWAGGER_CONFIG
GLOBAL_SWAGGER_SERVLET
GLOBAL_SWAGGER_REACTIVE
```

Core Java package:

```text
com.example.projectbuild.swagger
```

Core module metadata override:

```text
JAVA_BASE_PACKAGE = com.example.projectbuild.swagger
```

The core owns stack-neutral behavior such as:

```text
DynamicSwaggerAutoConfiguration
DynamicSwaggerCondition
DynamicSwaggerRegistrar
custom Swagger static assets
shared Swagger YAML composition
```

The core uses `springdoc-openapi-starter-common`. It may use `spring-web` as a compile-only API requirement for shared Spring Web types, but it must not own MVC- or WebFlux-specific runtime configuration.

Servlet adapter:

```text
GLOBAL_SWAGGER_SERVLET
→ depends on GLOBAL_SWAGGER_CONFIG
→ springdoc-openapi-starter-webmvc-ui
→ ServletSwaggerAutoConfiguration
→ ServletSwaggerResourceConfiguration implements WebMvcConfigurer
```

Reactive adapter:

```text
GLOBAL_SWAGGER_REACTIVE
→ depends on GLOBAL_SWAGGER_CONFIG
→ springdoc-openapi-starter-webflux-ui
→ ReactiveSwaggerAutoConfiguration
→ ReactiveSwaggerResourceConfiguration implements WebFluxConfigurer
```

`ModuleBuildConfigurationService` selects exactly one runtime adapter when `BUILD_SWAGGER=TRUE`:

```text
MODULE_TYPE=SERVLET
→ GLOBAL_SWAGGER_SERVLET

MODULE_TYPE=REACTIVE
→ GLOBAL_SWAGGER_REACTIVE
```

Do not make `GLOBAL_SWAGGER_CONFIG` depend on both `spring-webmvc` and `spring-webflux`. The adapter split exists so the unused web stack is absent from the consumer dependency graph rather than merely disabled by runtime conditions.

The auto-configuration registration file must live at:

```text
src/main/resources/META-INF/spring/
org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

The core registration references:

```text
com.example.projectbuild.swagger.DynamicSwaggerAutoConfiguration
```

Each adapter also owns its own `META-INF/spring/...AutoConfiguration.imports` entry for the corresponding adapter auto-configuration.

Do not move this file under `static/` or `resources/spring/`.

The package was intentionally moved outside `com.example.learning` so runtime activation does not depend on accidental component scanning by consumer applications.

Preserve auto-configuration-based discovery.

---

## 26. Swagger runtime handoff

Build-time Swagger generation produces classpath resources consumed by the runtime Swagger module.

Conceptual boundary:

```text
gradle-runtime Swagger generation
        ↓
generated/copied classpath resources
        ↓
springboot-runtime/swagger core
        ↓
MODULE_TYPE selects exactly one adapter
        ├── SERVLET  → swagger-servlet
        └── REACTIVE → swagger-reactive
        ↓
Springdoc/OpenAPI runtime behavior
```

Do not make runtime code call back into Gradle services/tasks.

Current runtime supports language-based Swagger grouping and custom README/YAML metadata consumption.

Keep Java runtime dependency selection and YML composition selection separate:

```text
Java runtime
→ Servlet/Reactive adapter selected by MODULE_TYPE

YML composition
→ GLOBAL_SWAGGER_CONFIG shared core
```

---

## 26A. Execution Context and AI integration roadmap

Execution Context is an independent capability. Swagger may be one UI that triggers an HTTP request, but Execution Context must not depend on OpenAPI, springdoc, Swagger UI, GPT, MCP, or tunnel infrastructure.

Current implementation boundaries:

```text
project-build/springboot-runtime/execution-context
    → stack-neutral execution model/store/service/source lookup

project-build/springboot-runtime/execution-context-servlet
    → Spring MVC / Servlet request, response, handler and log capture

project-build/gradle-runtime
    → build-time source-context generation
```

Current service identities:

```text
GLOBAL_EXECUTION_CONTEXT
GLOBAL_EXECUTION_CONTEXT_SERVLET
```

Current Servlet enablement:

```text
BUILD_EXECUTION_CONTEXT=TRUE
        ↓
GLOBAL_EXECUTION_CONTEXT_SERVLET
        ↓
GLOBAL_EXECUTION_CONTEXT
```

Reactive support is not yet implemented. Do not claim that `execution-context-reactive` exists until it is actually added and validated.

The seven-phase roadmap is:

```text
CORE — independent from chat AI
────────────────────────────────────────────

Phase 1: Execution Capture
→ identify the API/controller method that executed
→ capture request / response
→ capture status / duration / exception
→ assign and preserve executionId correlation

Phase 2: Runtime Observation
→ capture logs correlated by executionId
→ capture thread information
→ capture System.out / System.err when enabled
→ support async/virtual-thread observations where propagation is available

Phase 3: Source Context
→ capture/index Controller method source
→ include related Service source where resolvable
→ include Javadoc/README/annotation/config context when available
→ generate source metadata at build time and consume it at runtime

Phase 4: Context Query Service
→ query latest/by-id/recent/time-range/filterable execution history
→ return lightweight ExecutionSummary for list/search
→ return ExperimentContext for detail/export/AI consumers

CONNECTED MODE — delegated external AI integration
────────────────────────────────────────────

Phase 5: AI Tool Bridge
→ prefer Chat On Steroids (CoS) Core as the external MCP/tool bridge
→ let the AI use CoS workspace/terminal tools to read project source and call Phase 4 Execution Context REST endpoints
→ do not add an application-owned MCP adapter unless a future standalone requirement explicitly needs one

Phase 6: Reachability / transport
→ prefer CoS-managed MCP tunnel/transport when remote ChatGPT needs to reach the local workspace/tools
→ the application must not own tunnel credentials, tunnel lifecycle, or provider-specific transport configuration for the current project path

Phase 7: ChatGPT usage through CoS
→ ChatGPT web/app remains the model/conversation host
→ CoS supplies the local tool surface; the application supplies structured Execution Context through Phase 4 REST/query contracts
→ ChatGPT can retrieve runtime context on demand without an embedded Swagger chat and without the application calling the OpenAI API
```

The preferred Connected Mode for this repository is therefore **ChatGPT + Chat On Steroids + existing Execution Context APIs**:

```text
ChatGPT web/app
        ↓
Chat On Steroids Core (MCP/tool bridge)
        ├── project/workspace read tools
        └── terminal / local HTTP access
                    ↓
        Execution Context REST API
                    ↓
        Phase 4 Context Query Service
                    ↓
              ExperimentContext
```

In this path, Phase 5–7 capabilities are **delegated to external tooling rather than implemented as new Spring Boot runtime modules**. The application remains responsible for producing safe, structured runtime context; CoS is responsible for the MCP/tool/tunnel bridge; ChatGPT is responsible for model reasoning and conversation.

A future standalone product may replace CoS with an application-owned MCP adapter and tunnel implementation. If that happens, the new adapter must consume the same Phase 4 query contract and must not duplicate capture, source scanning, or execution correlation.

An **embedded Swagger AI chat** is optional and currently unnecessary for the primary workflow. If later implemented by calling provider APIs directly, it is a separate product mode with server-side credentials/billing. If later implemented by reusing a browser ChatGPT conversation, it requires a separate browser/companion bridge. Neither option is required to complete Phase 5–7 through CoS.

Current implementation/status:

```text
Phase 1  implemented for Servlet baseline
Phase 2  implemented for Servlet baseline
Phase 3  implemented with build-time generated source context + runtime lookup
Phase 4  implemented with ExecutionQuery/ExecutionSummary/ExecutionQueryService + REST/Explorer/export adapters
Phase 5  delegated to Chat On Steroids Core when CoS is configured
Phase 6  delegated to Chat On Steroids tunnel/transport when required
Phase 7  delegated to the user's ChatGPT + CoS connection
```

Phase 3 currently generates a deterministic classpath artifact:

```text
META-INF/execution-context/source-context.json
```

Source identity must include the method signature rather than method name alone so overloaded controller methods cannot collide conceptually:

```text
ControllerFqcn#method(TypeA,TypeB)
```

After Phase 1–3, the neutral domain object is conceptually:

```text
ExperimentContext
├── execution
│   ├── executionId
│   ├── handler/controller/method
│   ├── request
│   ├── response
│   ├── logs
│   ├── exception
│   └── duration
└── sourceContext
    ├── controller source
    ├── documentation
    └── related source
```

### Two user paths when using chat AI

The user must be able to choose between two independent consumption modes. Do not design the system so using chat AI requires MCP/tunnel infrastructure.

**Path A — Manual Mode / portable AI Context Export**

This branches after Phase 4 and is not a separate numbered phase in the seven-phase roadmap. The current Servlet adapter already exposes REST JSON detail and ZIP bundle export.

```text
Execute application/API
        ↓
Execution Context Core (Phase 1–3)
        ↓
Phase 4: Context Query Service
        ↓
AI Context Exporter
        ↓
REST JSON / ZIP bundle
        ↓
user uploads bundle to ChatGPT / Claude / Gemini / another chat AI
```

This path is intentionally vendor-neutral and should not require:

```text
MCP
tunnel
OpenAI API key
ChatGPT-specific runtime code
```

Prefer an AI Context Bundle containing source/context/log information over asking an AI to inspect only a compiled JAR. The application JAR may be optional supporting input, not the primary AI context format.

**Path B — Connected Mode / automatic retrieval through CoS**

This path continues after the shared Phase 4, but Phase 5–7 are delegated to Chat On Steroids instead of being implemented inside the application.

```text
Execute application/API
        ↓
Execution Context Core (Phase 1–3)
        ↓
Phase 4: Context Query Service / REST
        ↓
Phase 5: CoS Core tool bridge
        ↓
Phase 6: CoS tunnel/transport when needed
        ↓
Phase 7: ChatGPT + CoS connected session
        ↓
chat asks for latest/specific execution without manual upload
```

ChatGPT remains the model host. CoS reads the workspace and can query the local Execution Context REST API through its tool surface. The application does not need OpenAI API calls, a repository-owned MCP server, or an embedded Swagger chat for this workflow.

Both paths must consume the same `ExperimentContext`. Manual export and the CoS-driven connected path are consumers; neither owns execution capture, runtime observation, or source scanning.

Target dependency direction:

```text
                       Execution Context Core
                               │
                    ExperimentContext
                               │
                  Phase 4 Query Service
                               │
                ┌──────────────┴──────────────┐
                │                             │
                ▼                             ▼
        Manual AI Exporter            Chat On Steroids Core
                │                     (external integration)
        REST JSON / ZIP                       │
                │                     workspace + terminal
                ▼                             │
        upload to any AI                      ▼
                                      ChatGPT web/app account
```

Do not put CoS, MCP, tunnel, or chat-vendor logic inside `execution-context` core. Do not make `execution-context` depend on Swagger. Keep Phase 4 REST/query contracts stable enough that either CoS or a future standalone MCP adapter can consume them.

Swagger may keep Execution Context inspection/export controls, but an embedded `Connect AI`/floating chat is optional and not required by the current architecture. Do not make Swagger own MCP/tunnel/provider lifecycle.

### Phase 4 implementation contract

Keep Phase 4 split between a neutral core query API and web/UI adapters.

Core types currently are:

```text
ExecutionQuery
ExecutionSummary
ExecutionQueryService
DefaultExecutionQueryService
```

`ExecutionQuery` currently supports:

```text
limit
fromEpochMilli / toEpochMilli
search
httpMethod
path
controller
method
status
failed
minDurationMillis / maxDurationMillis
```

Use `ExecutionSummary` for list/search/history surfaces. Resolve full `ExperimentContext` only for detail, export, or downstream AI consumers. Do not duplicate query predicates in Swagger, Explorer, exporter, or future MCP code.

Current Servlet REST surface:

```text
GET /execution-context/api/executions
GET /execution-context/api/executions/latest
GET /execution-context/api/executions/{executionId}
GET /execution-context/api/executions/{executionId}/export
POST /execution-context/api/export
```

`POST /execution-context/api/export` may export explicit `executionIds` or results selected by an `ExecutionQuery`.

Current ZIP bundle contract includes:

```text
manifest.json
executions/<executionId>/context.json
executions/<executionId>/logs.txt
executions/<executionId>/source/controller-method.java
executions/<executionId>/source/documentation.txt
executions/<executionId>/source/related/*.java
```

The current standalone UI is:

```text
/execution-context/explorer.html
```

Explorer owns execution history, search/time-range filtering, detail inspection, multi-select, and bulk export.

The current store is in-memory. Do not describe execution history as persistent across application restart until a persistent store is implemented and validated.

Swagger integration must stay intentionally narrow per operation:

```text
[ Execute ]
[ View last execution ]
[ Export this execution ]
```

History/search/filter/multi-select/bulk export belong to the separate Execution Context Explorer, not Swagger UI.

Current Swagger integration contract:

```text
Execution Context runtime present
        ↓
OpenAPI operation gets x-execution-context-enabled=true
        ↓
swagger-execution-context.js wraps Swagger UI component `execute`
        ↓
[ Execute ] [ View last execution ] [ Export this execution ]
```

`View last execution` and `Export this execution` must resolve the latest matching execution through the Phase 4 query API by operation path + HTTP method; they must not inspect the in-memory store directly from Swagger code. `/execution-context/**` must remain excluded from the learning/business OpenAPI groups.

---

## 27. Plugin registry

`ProjectPluginEnum` is a generated plugin registry.

It maps:

```text
logical plugin enum
→ plugin ID
→ implementation class
```

Module orchestration uses this registry for capability application.

Do not manually edit stale enum entries as a permanent fix; fix the registry/generator input and regenerate.

Some build plugin packages have moved incrementally over time. Never assume package paths solely from old naming conventions; inspect the current generated registry/source.

---

## 28. Generator scripts

Gradle-runtime generator scripts under:

```text
project-build/gradle-runtime/gradle/
```

have their own local concept named `basePackage` for generating Gradle plugin source packages.

That local generator variable is **not** the same thing as module metadata:

```text
JAVA_BASE_PACKAGE
```

Do not rewrite generator `basePackage` settings merely because module-level Java package ownership changed.

---

## 29. Intentional current decisions

The following are known intentional decisions. Do not "fix" them proactively unless the user asks.

### PLATFORM common setup

PLATFORM currently receives the common Java/Spring Boot plugin baseline.

This is intentional for the current project because source/build behavior relies on it.

### ENV timestamp

Generated `.env` includes a timestamp intentionally so users can see generation/synchronization time.

### ENV ownership asymmetry

Setup-time `.env` creation and explicit ENV generator behavior are intentionally different.

### Google translation branch

`TranslationServiceFactory` has a Google branch that is not implemented and may currently return `null`.

It is not used by the current workflow and is intentionally deferred for future refactoring.

Do not redesign it unless the user is working on Google translation support.

### Database task plugin

`DatabaseTaskPlugin` is currently incomplete/TODO.

Do not treat it as a regression unless the user asks to implement it.

### Tests

The build framework currently does not have a complete `src/test` coverage architecture.

Do not block unrelated work on this unless testing architecture is the task.

---

## 30. Known cleanup that is not automatically a bug

Some things may be candidates for cleanup but should not be changed without scope confirmation, for example:

```text
unused private helper methods
generated task.gradle in infrastructure directories
direct-class imports vs enum-based plugin lookup in different orchestration scopes
debug logging in Swagger runtime/frontend
Gradle 9 deprecation warnings
```

Report them separately from blockers.

Do not mix opportunistic cleanup into a narrowly scoped fix.

---

## 31. Working with module source code

The internal package architecture of `module/` is not globally documented by this file.

When the user asks to work on a specific module, inspect only the relevant module and gather at least:

```text
module path
SERVICE_NAME
MODULE_TYPE
JAVA_BASE_PACKAGE
gradle.properties
master.json
properties.json
build.gradle
direct module dependencies
relevant runtime dependencies
README
source tree needed for the task
```

Do not generalize one module's controller/service/domain conventions to every other module without evidence.

---

## 32. Scope discipline

If the request is about:

```text
build automation
```

do not automatically refactor learning module source.

If the request is about:

```text
one learning/application module
```

do not automatically redesign global Gradle architecture.

If the request is about:

```text
Spring runtime behavior
```

do not move implementation into `gradle-runtime`.

If you discover an unrelated issue, report it separately and continue the requested scope unless it blocks correctness.

---

## 33. User intent: analysis vs editing

Vietnamese phrases frequently used by the repository owner have explicit intent:

```text
"đọc"
"check"
"review"
"đánh giá"
"gợi ý"
"khoan sửa"
"khoan code"
```

normally mean:

```text
inspect/analyze only
do not modify files
```

Only edit when the user clearly authorizes implementation or modification.

When the user says the equivalent of:

```text
"sửa đi"
"hãy sửa"
"tiến hành"
"implement"
```

perform the requested changes within scope.

Do not repeatedly ask for permission once the user has clearly authorized the change.

---

## 34. Evidence-first workflow

Before claiming something is wrong:

1. inspect the live file;
2. trace the consumer/caller;
3. inspect generated output if relevant;
4. inspect lifecycle ordering;
5. run a safe validation when useful;
6. distinguish blocker from cleanup.

Do not conclude from stale documentation, old build output, or file names alone.

If code and generated artifact disagree, determine which one is source of truth before proposing a fix.

---

## 35. Safe validation strategy

Prefer validation that proves integration without causing unrelated changes.

Useful repository command pattern on Windows:

```powershell
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain help --no-daemon --console=plain
```

This uses the repository Gradle wrapper version.

Important: the project targets Java 21.

Before running Java compilation, verify the active Java runtime/toolchain. A shell using Java 17 can fail with:

```text
invalid source release: 21
```

That is an environment mismatch, not automatically a project-source bug.

Also remember that even `help` executes project configuration, and configuration-time generators may synchronize metadata or generated files. Inspect git/status/diff when the task requires proving no unintended mutation.

---

## 36. Destructive and external operations

Be cautious with tasks that can:

```text
delete files/folders
rewrite generated resources
backup/restore databases
update remote whitelist/configuration
start/stop Docker infrastructure
call external APIs with side effects
```

Do not run destructive or external-side-effect tasks merely to "test" architecture.

Prefer read-only inspection or safe configuration/build validation.

If the user explicitly asks to execute such an operation, explain meaningful consequences when necessary and then perform the requested task.

---

## 37. Coding/refactor rules

When editing:

```text
preserve existing behavior unless change is requested
keep changes scoped
prefer small reviewable patches
respect ownership boundaries
preserve deterministic ordering
preserve idempotency
avoid unnecessary file rewrites
fail fast for required invalid configuration
avoid new hidden global state
avoid duplicate source of truth
avoid accidental cross-boundary dependencies
```

Do not create abstractions only because duplication is cosmetically undesirable.

Create abstractions when they reflect a stable shared responsibility.

---

## 38. Error handling expectations

For build infrastructure:

Use contextual `GradleException` or equivalent failure when required configuration or a required operation is invalid.

Avoid patterns like:

```text
println error
return
```

when silently continuing would leave the build in a misleading state.

Optional capabilities may log and skip when the contract says the condition is optional.

---

## 39. Generated content policy

Whenever adding a generator, explicitly define:

```text
input
output
owner
overwrite policy
idempotency strategy
ordering strategy
failure behavior
```

If output can be reconstructed from canonical input, treat it as generated-owned.

If developer edits must survive regeneration, encode preservation rules in the generator.

Do not rely on "developers will remember not to run it twice".

---

## 40. Adding a build capability

Before implementing a new build capability, answer:

```text
scope: Settings / Root / Module / Task?
enablement: metadata flag or physical input?
plugin entry point?
service implementation owner?
task needed?
extension needed?
canonical input?
generated output?
file ownership?
idempotency strategy?
orchestration insertion point?
plugin registry entry needed?
```

If these are unclear, inspect analogous existing capabilities before inventing a new pattern.

---

## 41. Adding a shared runtime capability

Before adding to `springboot-runtime`, define:

```text
runtime purpose
consumer applications
activation mechanism
auto-configuration vs explicit configuration
public properties/configuration surface
runtime dependencies
default behavior
opt-out behavior
classpath resources
build-time handoff, if any
package/component-scan behavior
```

Shared runtime code must not depend on Gradle API or Gradle implementation classes.

When using Spring Boot auto-configuration, verify the `META-INF/spring/...AutoConfiguration.imports` packaging in the built artifact instead of assuming component scanning will mask mistakes.

---

## 42. Documentation rules

When updating repository documentation:

```text
describe current implementation first
do not present a target as current state
do not resurrect legacy paths as active architecture
keep source-of-truth rules explicit
keep generated/human ownership explicit
do not claim knowledge of module package architecture without reading it
```

If a statement is only a proposal, label it as a recommendation/target rather than current behavior.

---

## 43. Current architecture invariants

Preserve these unless the user explicitly changes the architecture:

1. Real module identity comes from local `gradle.properties`.
2. Intermediate Gradle projects are not real modules.
3. Canonical metadata schemas belong to `gradle-runtime`.
4. Nonblank module-local metadata `VALUE` is preserved during sync.
5. `JAVA_BASE_PACKAGE` is module metadata, not a hidden global Settings property.
6. `SERVICE_NAME` is the logical identity used for module lookup.
7. Orchestration decides capabilities; capability owners implement them.
8. Build-time implementation belongs to `gradle-runtime`.
9. Shared Spring Boot runtime implementation belongs to `springboot-runtime`.
10. Runtime code does not depend on Gradle implementation.
11. Generated projections are not canonical source of truth.
12. Generators should be deterministic/idempotent according to their contract.
13. Human-owned content is not overwritten without an explicit contract.
14. External/destructive side effects belong in explicit actions/tasks.
15. `application.yml` is runtime truth; `application-merged.yml` is a generated review artifact.
16. Swagger build-time generation and Swagger runtime consumption are separate boundaries.
17. Active module types are `SERVLET`, `REACTIVE`, `LIBRARY`, and `PLATFORM`; legacy `APPLICATION` migrates to `SERVLET`.
18. `SERVLET` and `REACTIVE` must not accidentally pull each other's web starter through automatic module-type setup.
19. Swagger core is stack-neutral; `GLOBAL_SWAGGER_SERVLET` and `GLOBAL_SWAGGER_REACTIVE` own web-stack-specific runtime dependencies.
20. `BUILD_SWAGGER=TRUE` selects one Java Swagger adapter by `MODULE_TYPE`, while YML composition continues to consume only `GLOBAL_SWAGGER_CONFIG`.
21. Execution Context is independent from Swagger; Swagger may trigger requests or later expose UI, but it does not own capture/source/log correlation.
22. Execution Context Phase 1–4 form the reusable capture/source/query capability; manual export and external AI tooling consume the same query contract and `ExperimentContext`.
23. Chat AI usage has two paths after Phase 4: manual REST JSON/ZIP export, or connected retrieval through Chat On Steroids using workspace/terminal access plus the existing Execution Context REST API.
24. For the current repository, Phase 5–7 are delegated to CoS: Core provides the MCP/tool bridge, CoS provides tunnel/transport when needed, and ChatGPT remains the model/conversation host.
25. The application must not add MCP/tunnel/chat-vendor dependencies merely to support the current CoS workflow. A standalone application-owned MCP path may be added later only when explicitly required.
26. Embedded Swagger AI chat and any browser/companion bridge are optional UX/product features, not requirements for Phase 5–7 through CoS.
27. Swagger `execution` documentation is a guided learning explanation, not merely a code trace; it must explain concept, runtime flow, meaning, observable evidence, and conclusion without requiring the reader to open the source first.

---

## 44. Final pre-change checklist

Before editing code, verify:

```text
[ ] I understand the requested scope.
[ ] The user actually asked for an edit, not only analysis.
[ ] I know whether the target file is human-owned, generated-owned, or mixed.
[ ] I know the source of truth.
[ ] I know whether this is build-time or runtime behavior.
[ ] I know the relevant Gradle lifecycle phase.
[ ] I checked for existing metadata/capability patterns before inventing a new one.
[ ] The change does not restore hidden global state.
[ ] The change preserves idempotency/determinism where required.
[ ] The change does not overwrite human-owned data unintentionally.
[ ] Dependency configuration semantics remain correct.
[ ] Package/resource paths are current, not legacy.
[ ] Any validation command uses an appropriate Java/Gradle environment.
[ ] Unrelated cleanup is not mixed into the requested fix.
```

---

## 45. Context summary for another AI

If you only have time to remember one block, remember this:

```text
This repository contains its own small build platform.

project-orchestration
→ policy and capability selection

project-build/gradle-runtime
→ Gradle/build-time implementation

project-build/springboot-runtime
→ shared Spring Boot runtime implementation

Real module
→ directory with local gradle.properties

Canonical metadata
→ gradle-runtime resources/automation

Module values
→ module master.json / properties.json VALUE

JAVA_BASE_PACKAGE
→ module metadata, default com.example.learning

MODULE_TYPE
→ SERVLET | REACTIVE | LIBRARY | PLATFORM
→ legacy APPLICATION migrates to SERVLET

Generated artifacts
→ never treat as canonical input

README/menu generation
→ strict idempotency

application.yml
→ runtime source of truth

application-merged.yml
→ generated review/reference only

Swagger build automation
→ gradle-runtime

Swagger runtime
→ springboot-runtime/swagger = stack-neutral core
→ springboot-runtime/swagger-servlet = MVC adapter
→ springboot-runtime/swagger-reactive = WebFlux adapter
→ SERVLET selects GLOBAL_SWAGGER_SERVLET
→ REACTIVE selects GLOBAL_SWAGGER_REACTIVE
→ YML still composes GLOBAL_SWAGGER_CONFIG
→ Spring Boot AutoConfiguration.imports under META-INF/spring

Execution Context
→ BUILD_EXECUTION_CONTEXT is an independent capability flag
→ springboot-runtime/execution-context = neutral core
→ springboot-runtime/execution-context-servlet = current MVC/Servlet adapter
→ gradle-runtime generates META-INF/execution-context/source-context.json
→ Phase 1–3 = Execution Capture + Runtime Observation + Source Context
→ Manual Mode = export ZIP/JSON and upload to any compatible chat AI
→ Phase 4 Context Query Service is shared by both modes
→ Connected Mode = Phase 5 CoS Core tool bridge → Phase 6 CoS tunnel/transport when needed → Phase 7 ChatGPT + CoS session
→ Phase 5–7 are delegated externally for the current project; no application-owned MCP/tunnel implementation is required
→ preferred Connected Mode keeps ChatGPT as the model host and queries existing Execution Context REST APIs through CoS
→ embedded Swagger AI chat/browser bridge remains optional; API-backed chat, if ever added, is a separate billed/provider-key mode
→ both modes consume the same ExperimentContext

Do not infer module package architecture globally.
Inspect only the module/source needed for the task.
```
