---
video:
  url: ""
---

# Spring Boot application runtime model

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

## What Does Spring Boot Own in the Application Runtime?

<!-- VIDEO_SECTION -->

### Scene 1 — What Does Spring Boot Own in the Application Runtime?

**Time:** `00:00–01:03`

**Visual:**

Progressive reveal on the chapter visual: separate Boot runtime coordination from Spring-container/JVM/application ownership.

**Script:**

On the runtime map, by this point the learner already knows the basic bootstrap model; here the focus shifts to when Boot performs work and which runtime hook owns it. Boot’s application-runtime layer is the coordination layer that turns a configured `SpringApplication` into a running process with a managed `ApplicationContext`. Boot coordinates the environment and context startup, publishes Boot lifecycle events, invokes startup runners, updates application availability, supplies selected runtime infrastructure through auto-configuration, and closes the context when the JVM shuts down. The application still contains ordinary Spring beans and Java code; Boot adds conventions and integration around them rather than replacing the Spring container or JVM. That distinction is useful during debugging: first ask whether a symptom comes from Boot's runtime coordination, a Spring Framework mechanism, or application code.

**Purpose:**

Separate Boot runtime coordination from Spring-container, JVM, and application-code ownership before any debugging decision.


## Why Does Spring Boot Need a Runtime Coordination Layer?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:03–01:15`

**Visual:**

Keep the runtime timeline on screen and move the highlight to the prerequisite or phase introduced next.

**Script:**

Once Boot’s coordination boundary is clear, the reason for that layer becomes concrete: startup work has prerequisites that become true at different times.

**Purpose:**

Turn the ownership definition into the timing problem that motivates Boot’s runtime coordination layer.

### Scene 2 — Why Does Spring Boot Need a Runtime Coordination Layer?

**Time:** `01:15–02:17`

**Visual:**

Progressive reveal on the chapter visual: place environment/config/classpath prerequisites before bean creation and readiness.

**Script:**

At this point in startup, treating all of those moments as interchangeable callbacks creates ordering bugs and unclear failure behavior. Real applications have work that must happen at different moments: configuration must exist before beans are created, some observers must see very early startup, initialization work may need to run after the context is refreshed, and traffic should not be accepted until required startup work has finished. `SpringApplication` gives these moments a common timeline. Boot can therefore attach events, runners, availability changes, failure analysis, logging initialization, managed executors, and development services to known phases. The benefit is not more callbacks; it is a shared runtime vocabulary for placing work at the phase where its prerequisites are actually true. This chapter supplies that vocabulary before later chapters examine individual hooks.

**Purpose:**

Show that lifecycle phases exist to satisfy different prerequisites, not to provide interchangeable callback names.


## Which Inputs Shape the Runtime Before the Context Is Ready?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:17–02:31`

**Visual:**

Keep the runtime timeline on screen and move the highlight to the prerequisite or phase introduced next.

**Script:**

A shared timeline only helps if we know what shapes it before refresh, so move from the need for coordination to the inputs that create the runtime state.

**Purpose:**

Move from the need for coordination to the concrete inputs that determine what state each later hook can see.

### Scene 3 — Which Inputs Shape the Runtime Before the Context Is Ready?

**Time:** `02:31–03:19`

**Visual:**

Progressive reveal on the chapter visual: feed command-line/config/classpath/auto-configuration inputs into the pre-refresh timeline.

**Script:**

For diagnosis, command-line arguments participate in runtime input, externalized configuration prepares values and profiles, the classpath influences what auto-configuration can match, and auto-configuration contributes beans to the context. Before application startup work can run, the runtime has already been shaped by inputs owned by earlier Spring Boot modules. The key runtime idea is dependency: events and hooks do not all see the same world. An early environment event can observe an `Environment` before an `ApplicationContext` exists. A later context event can observe registered beans. A runner executes after context refresh and can use ordinary application beans.

**Purpose:**

Make the learner recognize that early events, context-aware hooks, and runners see different runtime state because inputs arrive at different phases.


## What Are the Major Phases from `SpringApplication.run` to Ready State?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:19–03:30`

**Visual:**

Keep the runtime timeline on screen and move the highlight to the prerequisite or phase introduced next.

**Script:**

With those inputs identified, we can stop speaking abstractly and place the exact checkpoints between `run` and readiness on the timeline.

**Purpose:**

Convert the early-input discussion into one exact lifecycle timeline before introducing more specialized hooks.

### Scene 4 — What Are the Major Phases from `SpringApplication.run` to Ready State?

**Time:** `03:30–04:13`

**Visual:**

Progressive reveal on the chapter visual: reveal the exact `run → refresh → started/LIVE → runners → ready/ACCEPTING_TRAFFIC` sequence beside `/runtime/lifecycle` evidence.

**Script:**

Read the startup line from left to right. `SpringApplication.run` prepares the environment, creates and initializes the context, loads bean definitions, and completes refresh. Only then does Boot publish `ApplicationStartedEvent` and mark liveness `CORRECT`. `ApplicationRunner` and `CommandLineRunner` run next. `ApplicationReadyEvent` follows them, and readiness then becomes `ACCEPTING_TRAFFIC`. The real `/spring-boot/runtime/lifecycle` experiment records started → live → runner → ready → accepting, so this order is observable. A hook that needs beans belongs after refresh; work that readiness must wait for belongs before the ready transition.

**Purpose:**

Establish the complete `run` → refresh → started/LIVE → runners → ready/ACCEPTING_TRAFFIC sequence, backed by the lifecycle experiment.


## What Changes Once the Application Reaches Steady-State Execution?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:13–04:25`

**Visual:**

Keep the runtime timeline on screen and move the highlight to the prerequisite or phase introduced next.

**Script:**

Reaching READY does not end Boot’s runtime role; carry the same process forward into background work, availability, logging, SSL, and shutdown.

**Purpose:**

Prevent READY from looking like the end of the story by carrying the same process into steady-state runtime services and exit paths.

### Scene 5 — What Changes Once the Application Reaches Steady-State Execution?

**Time:** `04:25–05:30`

**Visual:**

Progressive reveal on the chapter visual: move the highlight beyond READY to executors, logging, availability, SSL, Compose, failure, and shutdown.

**Script:**

Before picking a hook, during local development, Boot may also manage Docker Compose services. Once readiness is reached, the question changes from "how does the application start?" to "what runtime services and state does Boot continue to coordinate?" Background work can use Boot-managed task infrastructure, logging remains configured through Boot's integration layer, components can query or publish availability, and supported clients can consume named SSL bundles. Steady state does not mean startup concerns disappear. A bad executor choice can starve work, availability can change, logs can reveal runtime failures, and the process still needs a clean exit path. The runtime model therefore spans startup, normal execution, failure, and shutdown. Boot provides integration points for these concerns; business scheduling policy, generic concurrency, log aggregation, TLS protocol design, and container operations remain outside this module.

**Purpose:**

Extend the mental model past startup so background infrastructure, availability changes, failures, and shutdown remain part of one runtime story.


## Where Does Application Runtime Hand Off to Neighboring Modules?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:30–05:42`

**Visual:**

Keep the runtime timeline on screen and move the highlight to the prerequisite or phase introduced next.

**Script:**

Those steady-state concerns touch several technologies, so the final step is to separate Boot integration from the neighboring owners that define deeper semantics.

**Purpose:**

Close the chapter by separating Boot’s integration role from the technologies whose deeper semantics live elsewhere.

### Scene 6 — Where Does Application Runtime Hand Off to Neighboring Modules?

**Time:** `05:42–06:33`

**Visual:**

Progressive reveal on the chapter visual: replace the timeline with the neighboring-module ownership map.

**Script:**

In the running process, configuration-source precedence and binding belong to `externalized-configuration`; conditional bean creation and custom auto-configuration belong to `auto-configuration`. This runtime map is most useful when it tells you where to stop. Detailed web-server selection, server properties, server TLS, proxies, and graceful server shutdown belong to `web-runtime`. `actuator` exposes production-oriented health and diagnostic endpoints that may consume runtime state, but this module owns the underlying Boot availability lifecycle. Java concurrency owns virtual-thread semantics; Spring Framework concurrency owns `@Async` and `@Scheduled` semantics. Observability/logging owns pipelines and backends; security/network curricula own TLS/PKI; containerization owns Docker and Compose mechanics.

**Purpose:**

Prevent cross-module duplication by making the handoff from Boot runtime integration to configuration, web, concurrency, observability, TLS, and container owners explicit.
