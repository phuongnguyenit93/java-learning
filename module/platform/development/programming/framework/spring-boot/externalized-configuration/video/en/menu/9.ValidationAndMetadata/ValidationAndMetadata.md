---
video:
  url: ""
---

# Validation and Configuration Metadata

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

## Why Validate Configuration at Startup?

<!-- VIDEO_SECTION -->

### Scene 1 — Type-Correct Can Still Be Domain-Wrong

**Time:** `00:00–00:45`

**Visual:**

Show `max-connections=-1` successfully converting to Java `int`, then failing a `> 0` validation gate before startup completes.

**Script:**

Type conversion can prove that `-1` is an integer, but it cannot prove that negative connections make sense for the application. Validation adds domain constraints after binding so invalid configuration fails close to startup instead of surfacing later as an obscure runtime problem.

**Purpose:**

Establish validation as a separate domain-contract stage after successful binding and conversion.

## Validating @ConfigurationProperties with @Validated

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:45–00:55`

**Visual:**

Keep the invalid value on screen and reveal the annotations that define the configuration contract.

**Script:**

For configuration-properties beans, Spring can evaluate Bean Validation constraints during startup.

**Purpose:**

Move from the reason for validation to the concrete `@Validated` mechanism.

### Scene 2 — Put Constraints on the Property Contract

**Time:** `00:55–01:40`

**Visual:**

Show a `ClientProperties` record annotated with `@ConfigurationProperties("client")` and `@Validated`. Highlight `@NotBlank String baseUrl` and `@Positive int maxConnections`, then launch with `client.max-connections=-1`.

**Script:**

Annotate the configuration type with Spring's `@Validated` and place Jakarta Validation constraints on the relevant members. Binding can still create the integer `-1`, but `@Positive` rejects the resulting configuration object. The constraint both documents and enforces the configuration contract.

**Purpose:**

Show how typed configuration gains fail-fast domain validation through `@Validated` and Jakarta constraints.

## Nested Validation with @Valid

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:40–01:50`

**Visual:**

Expand the property object to include a nested `Security` configuration object.

**Script:**

When the contract contains nested objects, validation also needs to know whether it should traverse into those nested values.

**Purpose:**

Move from top-level constraints to cascading validation of nested configuration.

### Scene 3 — Cascade into Nested Configuration Explicitly

**Time:** `01:50–02:35`

**Visual:**

Show `MailProperties` containing `Security security`. Highlight `@Valid` on the nested member, then zoom into `Security.protocol` with `@NotBlank`. Compare the validation graph with and without the cascade marker.

**Script:**

Validating the outer object does not automatically mean every nested object is traversed in the way the contract requires. Mark the nested member with `@Valid` when constraints inside it must participate. If an obviously invalid nested value passes unnoticed, inspect the validation graph before blaming property resolution.

**Purpose:**

Teach explicit cascading validation for nested configuration properties.

## Binding, Conversion, and Validation Failures

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:35–02:45`

**Visual:**

Place three startup failures side by side and label the pipeline stage for each one.

**Script:**

Several problems look like “bad configuration,” but the failing stage tells you what kind of fix is actually needed.

**Purpose:**

Synthesize loading, conversion, and validation into distinct diagnostic stages.

### Scene 4 — Classify the Failure Before Fixing It

**Time:** `02:45–03:30`

**Visual:**

Build a three-row table: missing required import -> loading; `timeout=banana` for `Duration` -> binding/conversion; `max-connections=-1` with `@Positive` -> validation.

**Script:**

A missing import means Boot never obtained the data. A conversion failure means the key exists but cannot become the target type. A validation failure means the typed value violates a declared domain constraint. Classifying the stage prevents fixes such as weakening validation when the real issue is conversion, or debugging precedence when the file never loaded.

**Purpose:**

Give the learner a stage-oriented model for startup configuration failures.

## Why Configuration Metadata Exists

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:30–03:40`

**Visual:**

Move from runtime startup to an IDE editing `application.properties` before the application is running.

**Script:**

Runtime correctness is only one part of a good configuration contract. Developers also need tools to discover supported keys while editing configuration.

**Purpose:**

Move from runtime validation into compile-time and IDE-oriented metadata.

### Scene 5 — Metadata Describes the Configuration Contract

**Time:** `03:40–04:25`

**Visual:**

Show a `@ConfigurationProperties` type producing `META-INF/spring-configuration-metadata.json`, then display IDE completion for `client.timeout` with its type and description.

**Script:**

Configuration metadata describes supported keys, target types, descriptions, defaults, deprecations, and hints so tools can assist the developer. It does not create values, change precedence, or perform binding. That separation lets an IDE provide useful completion without starting the application.

**Purpose:**

Define configuration metadata as descriptive tooling data rather than runtime configuration behavior.

## Generating Metadata with the Configuration Processor

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:25–04:35`

**Visual:**

Keep the metadata file visible and trace it backward to a compile-time annotation processor.

**Script:**

For application-owned property types, Boot can generate much of that metadata automatically during compilation.

**Purpose:**

Connect the metadata artifact to its compile-time generation mechanism.

### Scene 6 — Generate Metadata at Compile Time

**Time:** `04:35–05:20`

**Visual:**

Show Gradle `annotationProcessor "org.springframework.boot:spring-boot-configuration-processor"`, then a compile arrow from a property class to `META-INF/spring-configuration-metadata.json`.

**Script:**

The Spring Boot configuration processor inspects `@ConfigurationProperties` types at compile time and writes metadata. With Gradle it normally belongs on the `annotationProcessor` configuration rather than the runtime classpath. Generated metadata reflects the source contract; it does not prove that a runtime value was supplied or that validation succeeded.

**Purpose:**

Explain how the configuration processor generates tooling metadata and keep that generation separate from runtime evidence.

## Metadata, IDE Assistance, and Tooling Boundary

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:20–05:30`

**Visual:**

Split the screen into compile-time tooling on the left and runtime resolution on the right.

**Script:**

That compile-time path and the runtime configuration path solve different problems, so they should also be debugged separately.

**Purpose:**

Close the chapter by separating tooling failures from runtime configuration failures.

### Scene 7 — Debug Tooling and Runtime on Different Paths

**Time:** `05:30–06:15`

**Visual:**

Left side: `@ConfigurationProperties -> processor -> metadata -> IDE`. Right side: `property sources -> Environment -> binding -> validation`. Put one bug icon on IDE completion and another on an unexpected runtime value.

**Script:**

A property can bind at runtime even when custom metadata is missing, and a metadata entry does not prove the application actually consumes that key. If completion is wrong, inspect metadata generation. If the running application receives the wrong value, inspect property sources, precedence, binding, and validation instead.

**Purpose:**

Make the compile-time tooling boundary explicit so metadata issues are not confused with runtime resolution problems.
