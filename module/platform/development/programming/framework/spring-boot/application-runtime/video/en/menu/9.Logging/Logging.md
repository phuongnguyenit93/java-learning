---
video:
  url: ""
---

# Spring Boot logging system and runtime logging

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe the transition, title card, or screen change.

**Script:**

Write the short bridge from the previous section to the current section.

**Purpose:**

Explain why this transition exists.

SCENE FORMAT

### Scene N — <optional scene title>

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe what the viewer sees: slide, diagram, source code, terminal, API request/response, runtime output, highlight, or callout.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates, or proves.
-->

## Why Does Boot Initialize Logging Before the ApplicationContext?

<!-- VIDEO_SECTION -->

### Scene 1 — Why Does Boot Initialize Logging Before the ApplicationContext?

**Time:** `00:00–00:53`

**Visual:**

Progressive reveal on the chapter visual: show log output before `ApplicationContext`, with `@PropertySources` arriving too late for bootstrap logging.

**Script:**

During bootstrap, Spring Boot therefore initializes its logging system before the `ApplicationContext` is created. Logging must work while the application is still starting, including during failures that happen before normal beans exist. That early timing is why logging behaves like runtime bootstrap infrastructure rather than an ordinary application bean. One consequence is that `@PropertySources` declared on Spring `@Configuration` classes are too late to control logging initialization. Boot can consume supported logging properties from the environment it prepares early, but application bean configuration cannot retroactively change which logging system initialized the bootstrap messages. When early and later log behavior differ unexpectedly, inspect when the configuration becomes available.

**Purpose:**

Explain why logging must bootstrap before normal beans and why late `@PropertySources` cannot control early logging output.


## What Role Does `LoggingSystem` Play?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:53–01:04`

**Visual:**

Keep the pre-context logging timeline visible and shift focus from bootstrap to abstraction, common properties, native config, local output, then observability.

**Script:**

Because logs must exist before normal beans, Boot needs a bootstrap abstraction over the concrete logging implementation; that role is `LoggingSystem`.

**Purpose:**

Use logging’s pre-context timing to motivate the abstraction Boot needs before normal application beans exist.

### Scene 2 — What Role Does `LoggingSystem` Play?

**Time:** `01:04–01:58`

**Visual:**

Progressive reveal on the chapter visual: draw logging implementation on classpath → Boot `LoggingSystem` → early configured output, with normal SLF4J calls on a separate lane.

**Script:**

At the logging abstraction, Boot detects an implementation from the classpath and uses the abstraction to initialize and configure logging before the application context is ready. Boot uses `LoggingSystem` as Spring Boot's abstraction over the supported logging implementations. The application normally works through SLF4J/logging APIs; it does not need to call `LoggingSystem` for everyday logging. Boot's abstraction explains why the same high-level properties can configure common runtime behavior while native configuration files remain framework-specific. It also provides an explicit escape hatch: the logging system can be selected or disabled through the documented `org.springframework.Boot.logging.LoggingSystem` system property when startup truly requires it. Treat that low-level selection as bootstrap configuration.

**Purpose:**

Place `LoggingSystem` as Boot’s bootstrap abstraction while keeping normal application logging on SLF4J/logging APIs.


## Which Runtime Logging Concerns Can Boot Properties Configure?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:58–02:12`

**Visual:**

Keep the pre-context logging timeline visible and shift focus from bootstrap to abstraction, common properties, native config, local output, then observability.

**Script:**

`LoggingSystem` gives Boot one common layer, and the next question is how much common runtime behavior can be expressed with Boot logging properties before native configuration is needed.

**Purpose:**

Move from the bootstrap abstraction to the common logging behavior Boot can expose consistently through properties.

### Scene 3 — Which Runtime Logging Concerns Can Boot Properties Configure?

**Time:** `02:12–02:51`

**Visual:**

Progressive reveal on the chapter visual: show YAML for root/package levels plus file output; place implementation-specific appenders beyond the Boot property boundary.

**Script:**

Use Boot properties for the common logging surface. `logging.level.<logger-name>` and `logging.level.root` control levels; `logging.file.name` or `logging.file.path` adds local file output; supported pattern and charset properties adjust Boot’s default configuration. A small YAML example with `root: INFO`, an application package at `DEBUG`, and `logs/application.log` shows the boundary. If the requirement is an implementation-specific appender, filter, encoder, or routing rule, move to native logging configuration instead of stretching the generic property model.

**Purpose:**

Define the boundary of common Boot logging properties before implementation-specific appenders, filters, encoders, or routing require native config.


## Why Do `-spring` Logging Configuration Files Matter?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:51–03:03`

**Visual:**

Keep the pre-context logging timeline visible and shift focus from bootstrap to abstraction, common properties, native config, local output, then observability.

**Script:**

When common properties are not enough, file-loading timing matters; the `-spring` variants let Boot participate instead of letting the logging library initialize too early.

**Purpose:**

Explain why native logging configuration becomes necessary only after the common Boot surface is insufficient.

### Scene 4 — Why Do `-spring` Logging Configuration Files Matter?

**Time:** `03:03–03:52`

**Visual:**

Progressive reveal on the chapter visual: compare `logback.xml` loading directly with `logback-spring.xml` loading through Boot and gaining profile/environment extensions.

**Script:**

When native logging configuration is needed, the reason is lifecycle timing. Boot can use native logging files, but it recommends the `-spring` variants when available: for example `logback-spring.xml` or `log4j2-spring.xml`. A standard file such as `logback.xml` can be loaded directly by the logging implementation too early for Boot to fully control initialization and apply its extensions. With `logback-spring.xml`, Boot can participate in configuration and features such as its Logback extensions can read Spring profiles or environment properties at the supported phase. `logging.config` can also point to an explicit configuration location. Use native configuration when the requirement is genuinely implementation-specific.

**Purpose:**

Show why `logback-spring.xml`/`log4j2-spring.xml` let Boot participate at the supported lifecycle phase while standard native files may initialize too early.


## How Do Logger Groups, Output, and Rotation Fit the Boot Layer?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:52–04:04`

**Visual:**

Keep the pre-context logging timeline visible and shift focus from bootstrap to abstraction, common properties, native config, local output, then observability.

**Script:**

With the common/native boundary established, logger groups, local output, and rotation are the remaining Boot conveniences to place on that layer.

**Purpose:**

Place logger groups, file output, and rotation after the config-file boundary so they stay recognizable as Boot conveniences.

### Scene 5 — How Do Logger Groups, Output, and Rotation Fit the Boot Layer?

**Time:** `04:04–04:48`

**Visual:**

Progressive reveal on the chapter visual: show one logger group controlling several categories, console/file output, and rotation limits.

**Script:**

Logger groups let an application assign a logical name to several logger categories, then control the group through `logging.level.<group>`. This is useful when a subsystem spans multiple packages and a developer wants one runtime switch rather than several individual logger entries. Boot also provides convenient output controls. Console logging is available by default; file output can be enabled with `logging.file.name` or `logging.file.path`. With the default Logback integration, Boot exposes properties for file rotation such as maximum file size, history, and total size cap.

**Purpose:**

Place logger groups, local file output, and rotation in the Boot convenience layer without conflating them with centralized log operations.


## Where Does Boot Logging Integration End and Logging Operations Begin?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:48–04:59`

**Visual:**

Keep the pre-context logging timeline visible and shift focus from bootstrap to abstraction, common properties, native config, local output, then observability.

**Script:**

Once correctly configured log events leave the application, shipping, retention, search, correlation, and incident analysis belong to logging operations and observability.

**Purpose:**

Close the Boot integration layer where local log events leave the application and operational observability begins.

### Scene 6 — Where Does Boot Logging Integration End and Logging Operations Begin?

**Time:** `04:59–05:56`

**Visual:**

Progressive reveal on the chapter visual: draw configured local log events leaving the process toward collector/backend, with Actuator logger management on its own handoff.

**Script:**

At the observability boundary, it stops once the application emits correctly configured log events and local outputs. This chapter covers Boot's logging bootstrap, `LoggingSystem`, common logging properties, and the relationship between Boot and native logging configuration. Log aggregation agents, centralized backends, structured event schemas, retention, indexing, dashboards, alerting, trace/log correlation, and incident investigation are observability responsibilities. Actuator's runtime logger endpoint is also a production-management surface owned by the Actuator module, even though it changes logger levels at runtime. When a logging issue appears, classify it before editing configuration: "Boot did not apply this startup property" belongs here; "the collector did not ship the file" or "the backend cannot query the field" belongs to infrastructure observability.

**Purpose:**

Hand log shipping, retention, query, correlation, alerting, and production logger management to observability/Actuator owners.
