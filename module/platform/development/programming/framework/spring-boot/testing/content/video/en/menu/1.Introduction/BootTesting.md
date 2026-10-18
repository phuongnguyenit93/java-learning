---
video:
  url: ""
---

# Spring Boot Test Bootstrap and Configuration Discovery

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## What Does Spring Boot Add to Application-Context Testing?

<!-- VIDEO_SECTION -->

### Scene 1 — What Does Spring Boot Add to Application-Context Testing?

**Time:** `00:00–01:03`

**Visual:**

Show the smallest concrete code/configuration fragment needed for “What Does Spring Boot Add to Application-Context Testing?”, then connect that input to the resulting test-context or runtime boundary.

**Script:**

Spring Framework already gives us the TestContext Framework for loading and reusing application contexts in tests. Boot then layers in a Boot-aware layer on top of that foundation: it can start the test context through `SpringApplication`, discover the application's primary Boot configuration, apply Boot external configuration and auto-configuration, and expose focused test annotations for common application slices. The practical question for this module is therefore not “how does testing work in Java?” but “which Boot facilities should participate when a test needs application infrastructure?”. JUnit execution, assertion libraries, Mockito stubbing semantics, and the generic TestContext lifecycle remain separate concerns.

**Purpose:**

Distinguish the Spring TestContext foundation from the extra Boot-aware assembly that makes production-style configuration, auto-configuration, and environment behavior testable.

## Why Is Boot-Aware Test Bootstrap Needed?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:03–01:18`

**Visual:**

Carry the observable result from the previous scene into the next mechanism: show the smallest concrete code/configuration fragment needed for “Why Is Boot-Aware Test Bootstrap Needed?”, then connect that input to the resulting test-context or runtime boundary.

**Script:**

Spring TestContext supplies the lifecycle, but it does not automatically reproduce Boot's environment processing and auto-configuration; that gap is why Boot-aware test bootstrap exists.

**Purpose:**

Make the missing Boot startup behavior visible so the learner understands why Spring TestContext alone is not the fidelity model used by the next section.

### Scene 1 — Why Is Boot-Aware Test Bootstrap Needed?

**Time:** `01:18–02:17`

**Visual:**

Show the smallest concrete code/configuration fragment needed for “Why Is Boot-Aware Test Bootstrap Needed?”, then connect that input to the resulting test-context or runtime boundary.

**Script:**

A production Boot application typically depends on more than plain bean registration. It may rely on externalized configuration, auto-configuration, environment detection, configuration-properties binding, and conditional infrastructure. Loading a context with a generic Spring test annotation can skip Boot behavior that the production application depends on. Boot-aware test bootstrap exists so a test can mirror the relevant Boot startup model without manually rebuilding it. The right level still depends on the behavior under test: some tests need the full Boot context, while others should deliberately use a smaller slice.

**Purpose:**

Show why a generic Spring context can miss Boot-created behavior, so learners know when bootstrap fidelity—not assertion code—is the source of a test failure.

## How Do `spring-boot-test`, `spring-boot-test-autoconfigure`, and `spring-boot-starter-test` Relate?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:17–02:32`

**Visual:**

Carry the current configuration graph forward and change only the next input or import: draw a three-level dependency graph: spring-boot-test and spring-boot-test-autoconfigure underneath spring-boot-starter-test; fan out JUnit Jupiter, AssertJ, Hamcrest, and Mockito as independently owned libraries.

**Script:**

Once bootstrap fidelity is the requirement, the next practical question is which Boot modules provide core test support, focused test auto-configuration, and the usual testing stack.

**Purpose:**

Shift from why Boot-aware bootstrap is needed to which test modules provide that capability, without mixing Boot modules with the independent testing libraries they aggregate.

### Scene 1 — How Do `spring-boot-test`, `spring-boot-test-autoconfigure`, and `spring-boot-starter-test` Relate?

**Time:** `02:32–03:23`

**Visual:**

Draw a three-level dependency graph: spring-boot-test and spring-boot-test-autoconfigure underneath spring-boot-starter-test; fan out JUnit Jupiter, AssertJ, Hamcrest, and Mockito as independently owned libraries.

**Script:**

`spring-boot-test` contains core Boot testing support such as `@SpringBootTest` and Boot-specific test utilities. `spring-boot-test-autoconfigure` contains the slice annotations and test auto-configuration used for focused contexts. Most projects depend on `spring-boot-starter-test` instead of selecting these modules individually. The starter brings both Boot test modules plus commonly used testing libraries such as JUnit Jupiter, AssertJ, Hamcrest, and Mockito. Those libraries remain independently owned even though the starter makes them convenient to consume together.

**Purpose:**

Make the roles of `spring-boot-test`, `spring-boot-test-autoconfigure`, `spring-boot-starter-test`, and external test libraries explicit so dependency composition is not confused with ownership.

## How Does `@SpringBootTest` Bootstrap Through `SpringApplication`?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:23–03:35`

**Visual:**

Extend the existing lifecycle/timeline into the next phase instead of resetting the diagram: use a bootstrap timeline: test annotation → Boot test bootstrapper → `SpringApplication` → Environment → configuration sources → auto-configuration → ready `ApplicationContext`.

**Script:**

Knowing the modules answers the build-time question; now follow `@SpringBootTest` at runtime to see how those capabilities actually construct the context.

**Purpose:**

Move from module composition to the runtime bootstrap chain so the learner sees where Boot behavior enters before any assertion runs.

### Scene 1 — How Does `@SpringBootTest` Bootstrap Through `SpringApplication`?

**Time:** `03:35–04:30`

**Visual:**

Use a bootstrap timeline: test annotation → Boot test bootstrapper → `SpringApplication` → Environment → configuration sources → auto-configuration → ready `ApplicationContext`.

**Script:**

`@SpringBootTest` creates the test `ApplicationContext` through `SpringApplication` rather than treating it as a plain Spring context. That allows Boot features such as external properties, logging configuration, environment processing, and auto-configuration to participate in the test startup path. That gives full-context tests high production fidelity at the Boot integration layer. It also means failures during test startup should be reasoned about like Boot startup failures: configuration discovery, conditions, properties, and infrastructure can all matter before the first test method executes.

**Purpose:**

Trace `@SpringBootTest` through `SpringApplication` so startup failures can be debugged against the same Boot phases that prepare the real application context.

## How Does Boot Find the Primary `@SpringBootConfiguration`?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:30–04:44`

**Visual:**

Carry the current configuration graph forward and change only the next input or import: show a package tree and move upward from the test package until the first `@SpringBootApplication` / `@SpringBootConfiguration` is found; add an explicit configuration class as the escape hatch.

**Script:**

`SpringApplication` can only build the intended application model after it has a primary configuration source, so configuration discovery is the next link in the bootstrap chain.

**Purpose:**

Carry the SpringApplication bootstrap forward to the configuration-source search that determines which application model the test actually starts.

### Scene 1 — How Does Boot Find the Primary `@SpringBootConfiguration`?

**Time:** `04:44–05:37`

**Visual:**

Show a package tree and move upward from the test package until the first `@SpringBootApplication` / `@SpringBootConfiguration` is found; add an explicit configuration class as the escape hatch.

**Script:**

When a Boot test annotation does not explicitly specify configuration classes, Boot searches upward from the package containing the test until it finds a class annotated with `@SpringBootApplication` or `@SpringBootConfiguration`. In a conventional package layout, this typically finds the application's main configuration automatically. Package placement therefore affects test bootstrap. A test placed outside the application's package hierarchy may fail to locate the primary configuration unless the configuration class is supplied explicitly.

**Purpose:**

Turn primary-configuration discovery into a concrete diagnostic rule: package placement and explicit configuration determine which application model a full-context test starts from.

## Where Does Boot Testing Hand Off to Spring TestContext and Test Libraries?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:37–05:49`

**Visual:**

Keep the ownership lanes visible and move the next responsibility across the correct boundary: use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

After the context is created, not every testing behavior remains Boot-owned; separate framework and library responsibilities before diagnosing later failures.

**Purpose:**

Finish the current mechanism by assigning the next responsibility to the framework or library that actually owns it.

### Scene 1 — Where Does Boot Testing Hand Off to Spring TestContext and Test Libraries?

**Time:** `05:49–06:59`

**Visual:**

Use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

On the Boot side, the owned responsibility is Boot-specific context bootstrap, slice selection, test auto-configuration, Boot test annotations, mock/spy bean replacement integration, and service-connection support. It relies on Spring TestContext underneath for context lifecycle, caching, test-managed transactions, property-source support, and test execution integration. JUnit remains responsible for test discovery and execution semantics. Mockito remains responsible for mock behavior and verification. AssertJ/Hamcrest own assertion APIs. Testcontainers remains responsible for container definitions and its JUnit-managed container lifecycle, along with Docker interaction, images, networking, wait strategies, and other generic container behavior. When a container is declared as a Spring bean, its lifecycle follows the Spring application context instead. Keeping these boundaries explicit prevents Boot convenience annotations from being mistaken for the implementation of the underlying testing tools.

**Purpose:**

Give the learner an ownership map for routing failures correctly among Boot testing, Spring TestContext, JUnit, Mockito, assertion libraries, and Testcontainers.

## Which Boot Testing Choice Comes Next?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:59–07:14`

**Visual:**

Carry the observable result from the previous scene into the next mechanism: build a decision tree from the assertion boundary: full context → `WebEnvironment`; focused web/data behavior → slice; scenario customization → properties/config/mock; real dependency → service connection.

**Script:**

With ownership clear, the final introductory step is turning those responsibilities into a deliberate test-scope and customization choice.

**Purpose:**

Turn the ownership map into a concrete next-choice decision so the learner selects scope before adding test customization.

### Scene 1 — Which Boot Testing Choice Comes Next?

**Time:** `07:14–08:08`

**Visual:**

Build a decision tree from the assertion boundary: full context → `WebEnvironment`; focused web/data behavior → slice; scenario customization → properties/config/mock; real dependency → service connection.

**Script:**

Start by deciding the boundary that the test must prove. Use a full `@SpringBootTest` context when Boot-wide integration matters. Choose the correct `WebEnvironment` when HTTP-server fidelity matters. Prefer a focused slice when only a web or data layer must be tested. After choosing context scope, customize only what the scenario needs: selected test auto-configuration, local properties, test-only configuration, mock/spy replacement, or service connections to real dependencies. The final chapter combines those choices into a deliberate testing strategy.

**Purpose:**

Synthesize the introduction into a scope-first decision: choose full context, server mode, slice, local customization, replacement, or service connection only when the assertion boundary requires it.
