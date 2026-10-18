---
video:
  url: ""
---

# Application lifecycle and events

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

## How Does the SpringApplication Event Timeline Progress?

<!-- VIDEO_SECTION -->

### Scene 1 — How Does the SpringApplication Event Timeline Progress?

**Time:** `00:00–00:38`

**Visual:**

Progressive reveal on the chapter visual: build Starting → EnvironmentPrepared → ContextInitialized → Prepared → Started → LIVE → Ready → READY, with FAILED as a branch.

**Script:**

On the event timeline, in Spring Boot 3.3 the important ordering is `ApplicationStartingEvent`, `ApplicationEnvironmentPreparedEvent`, `ApplicationContextInitializedEvent`, `ApplicationPreparedEvent`, `ApplicationStartedEvent`, a liveness `AvailabilityChangeEvent`, `ApplicationReadyEvent`, and then a readiness `AvailabilityChangeEvent`. Boot lifecycle events expose named checkpoints on the startup path. `ApplicationFailedEvent` represents an exception on the startup path. The order matters because each checkpoint has different guarantees. Before the context exists you cannot rely on beans. After refresh, normal context state is available.

**Purpose:**

Turn event names into ordered lifecycle guarantees so listener placement is reasoned from available state.


## Which Events Happen Before an ApplicationContext Exists?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:38–00:52`

**Visual:**

Keep the event timeline; slide the phase marker forward or branch it to FAILED so the changed guarantee is visible.

**Script:**

The full event order is useful only if we know what state exists at each checkpoint; start with the events that fire before a normal bean graph exists.

**Purpose:**

Use the event order to expose the first important boundary: which notifications occur before normal beans exist.

### Scene 2 — Which Events Happen Before an ApplicationContext Exists?

**Time:** `00:52–01:51`

**Visual:**

Progressive reveal on the chapter visual: shade the bean graph as unavailable while highlighting the pre-context events and what objects already exist.

**Script:**

At this checkpoint, `ApplicationStartingEvent` occurs near the beginning of `run`, `ApplicationEnvironmentPreparedEvent` occurs once the `Environment` is known but before the context is created, and `ApplicationContextInitializedEvent` occurs after context initializers have run but before bean definitions are loaded. The earliest lifecycle notifications exist specifically before a normal bean graph is available. These phases are useful for infrastructure that truly needs early bootstrap visibility. They are a poor place for ordinary application services because dependency injection and application beans are not ready yet. The design question is therefore not "which event can I listen to?" but "what state must this work observe?" If the work needs repositories, services, or other regular beans, wait for a later phase instead of forcing application behavior into bootstrap infrastructure.

**Purpose:**

Show why pre-context events are bootstrap hooks and cannot safely depend on normal application beans.


## How Do Context Refresh, Started, and Ready Differ?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:51–02:03`

**Visual:**

Keep the event timeline; slide the phase marker forward or branch it to FAILED so the changed guarantee is visible.

**Script:**

Once the pre-context boundary is visible, compare it with the later refresh, started, and ready milestones where progressively stronger guarantees are available.

**Purpose:**

Contrast pre-context visibility with the stronger guarantees available after refresh, started, and ready.

### Scene 3 — How Do Context Refresh, Started, and Ready Differ?

**Time:** `02:03–02:43`

**Visual:**

Progressive reveal on the chapter visual: zoom to refresh → Started → LIVE → runners → Ready → READY beside `/runtime/lifecycle` observations.

**Script:**

Refresh, started, and ready are three different guarantees. A refreshed context means the bean graph is available. `ApplicationStartedEvent` comes after refresh but before `ApplicationRunner` and `CommandLineRunner`; `ApplicationReadyEvent` comes only after those runners finish. Boot aligns availability with the same order: liveness becomes `CORRECT` around started, while readiness becomes `ACCEPTING_TRAFFIC` after ready. `/spring-boot/runtime/lifecycle` captures those observations in order, which is why a slow runner can leave the process live while still delaying readiness.

**Purpose:**

Make refresh, started/LIVE, runners, ready, and readiness distinct checkpoints so long startup work is placed correctly.


## What Happens on the Failed Startup Path?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:43–02:54`

**Visual:**

Keep the event timeline; slide the phase marker forward or branch it to FAILED so the changed guarantee is visible.

**Script:**

The successful path now has clear checkpoints; the same lifecycle also needs a failure branch for startups that never reach ready.

**Purpose:**

Add the missing failure branch so the lifecycle model explains startups that never reach the ready checkpoint.

### Scene 4 — What Happens on the Failed Startup Path?

**Time:** `02:54–03:37`

**Visual:**

Progressive reveal on the chapter visual: branch the timeline into `ApplicationFailedEvent`, root exception, and failure-analysis evidence.

**Script:**

After context refresh, if an exception escapes the startup process, Boot publishes `ApplicationFailedEvent`. Not every startup reaches `ApplicationReadyEvent`. This gives bootstrap-level listeners a final event containing the failed application context when available and the exception that ended startup. The event is only one part of the failure path. Boot also allows `FailureAnalyzer` implementations to turn known failures into a focused description and action. When the problem involves auto-configuration conditions, the condition evaluation report is additional evidence rather than a replacement for the original exception.

**Purpose:**

Show how `ApplicationFailedEvent` and failure analysis preserve evidence when the happy path never reaches ready.


## Why Must Some Listeners Be Registered Before Bean Creation?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:37–03:49`

**Visual:**

Keep the event timeline; slide the phase marker forward or branch it to FAILED so the changed guarantee is visible.

**Script:**

An early event creates a practical registration problem: a listener declared as a normal bean may not exist yet when that event is published.

**Purpose:**

Reveal the registration consequence of early events: a listener cannot observe an event that occurs before that listener bean exists.

### Scene 5 — Why Must Some Listeners Be Registered Before Bean Creation?

**Time:** `03:49–04:38`

**Visual:**

Progressive reveal on the chapter visual: compare an early listener registered on `SpringApplication`/builder with an `@Bean` listener that does not exist yet.

**Script:**

On the failure branch, Boot therefore supports registering early listeners directly on `SpringApplication` with `addListeners(...)`, through `SpringApplicationBuilder.listeners(...)`, or through the supported automatic listener registration mechanism. A listener created only as a normal `@Bean` cannot observe events that happen before the `ApplicationContext` has created that bean. This is a lifecycle constraint, not a dependency-injection trick. Register an early listener outside the bean lifecycle only when the event itself is early. Later events can usually use ordinary bean-based listeners, which keeps dependencies and testing simpler. An early listener should also have a narrow responsibility.

**Purpose:**

Explain why very early listeners must be registered outside the normal bean lifecycle and should keep narrow responsibilities.


## When Is an Application Event the Right Runtime Hook?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:38–04:51`

**Visual:**

Keep the event timeline; slide the phase marker forward or branch it to FAILED so the changed guarantee is visible.

**Script:**

After timing and registration are clear, the remaining design question is intent—when should lifecycle observation be an event rather than a runner or background task?

**Purpose:**

Shift from event timing to hook selection so lifecycle observation is not confused with startup work or background execution.

### Scene 6 — When Is an Application Event the Right Runtime Hook?

**Time:** `04:51–05:46`

**Visual:**

Progressive reveal on the chapter visual: place event, runner, and executor/scheduler cards on the lifecycle phase each one is meant to serve.

**Script:**

For listener placement, examples include recording that the environment was prepared, reacting to the context being started, or observing readiness changes. An application event fits when the work is fundamentally observation or reaction to a lifecycle transition. Events are also useful when several independent listeners should react without the publisher knowing them directly. Do not use a synchronous event listener for lengthy startup work merely because the event occurs at a convenient time. Spring application events are delivered in the same thread by default, so expensive work can extend or block the startup path. Work that must complete before readiness often fits a runner; ongoing work belongs on an executor or scheduler.

**Purpose:**

Give a selection rule: use events for lifecycle observation/reaction, not as a convenient place for long startup or background work.
