---
video:
  url: ""
---

# Lazy initialization and startup optimization

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

## What Does Lazy Initialization Change at Startup?

<!-- VIDEO_SECTION -->

### Scene 1 — What Does Lazy Initialization Change at Startup?

**Time:** `00:00–00:48`

**Visual:**

Progressive reveal on the chapter visual: move selected bean-creation blocks from startup to first use instead of deleting the work.

**Script:**

On the startup critical path, lazy initialization changes that timing: eligible beans are created when they are first needed instead of eagerly during startup. A normal Boot startup creates many singleton beans during context startup. Boot exposes this as `spring.main.lazy-initialization=true` and through the `SpringApplication`/builder API. That can reduce the amount of work on the critical startup path, especially when parts of the bean graph are not needed immediately. It does not remove the work; it moves some work to first use. The right mental model is therefore deferred initialization, not free startup performance.

**Purpose:**

Frame lazy initialization as deferred bean creation that moves cost rather than eliminating it.


## Which Failures and Costs Can Lazy Initialization Defer?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:48–01:02`

**Visual:**

Keep the startup critical-path bar visible and move the cost/failure marker from startup to first use, measurement, or downstream observability as the chapter advances.

**Script:**

Moving bean creation off the startup path trades startup latency for later work, so the next question is which failures and costs are merely deferred rather than removed.

**Purpose:**

Expose the cost moved by lazy initialization so the next section can evaluate deferred failures instead of celebrating a smaller startup number.

### Scene 2 — Which Failures and Costs Can Lazy Initialization Defer?

**Time:** `01:02–01:49`

**Visual:**

Progressive reveal on the chapter visual: let a misconfigured lazy bean pass startup then fail on first use; show deferred memory/cost accumulating later.

**Script:**

When initialization is deferred, a misconfigured bean that would normally fail while the application starts may now fail only when a request or background task first needs it. The main trade-off of lazy initialization is delayed failure discovery. That changes both the timing and operational impact of the error. Lazy initialization also does not mean the JVM only needs memory for the beans created at startup. The application may eventually create the full graph, so capacity planning must consider the steady-state object set. Spring Boot therefore leaves lazy initialization disabled by default.

**Purpose:**

Expose delayed failure discovery and eventual steady-state memory cost so faster startup is not treated as a free optimization.


## Why Measure Startup Before Optimizing It?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:49–01:59`

**Visual:**

Keep the startup critical-path bar visible and move the cost/failure marker from startup to first use, measurement, or downstream observability as the chapter advances.

**Script:**

That trade-off makes blind tuning dangerous. Before enabling another optimization, measure which startup phase is actually expensive.

**Purpose:**

Turn the lazy-init trade-off into a measurement requirement before another optimization knob is considered.

### Scene 3 — Why Measure Startup Before Optimizing It?

**Time:** `01:59–02:55`

**Visual:**

Progressive reveal on the chapter visual: compare an unmeasured optimization guess with a measured startup baseline and circle the expensive phase.

**Script:**

Before tuning anything, possible causes live in different ownership domains: bean initialization, external network calls in startup code, condition-heavy application configuration, logging setup, a runner, or a web-server concern. Startup optimization should start with a phase and a measurement, not a property. "Startup takes 12 seconds" is an observation; the useful question is which work consumes that time and whether it belongs on the startup critical path. Tuning lazy initialization cannot fix all of them. Establish a repeatable baseline, compare like-for-like runs, then change one mechanism whose cost you can explain. The goal is not the smallest startup number at any price; it is acceptable startup time while preserving early failure detection and predictable first-use latency.

**Purpose:**

Require a repeatable baseline and phase evidence before choosing any startup optimization knob.


## How Does `ApplicationStartup` Provide Startup Evidence?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:55–03:05`

**Visual:**

Keep the startup critical-path bar visible and move the cost/failure marker from startup to first use, measurement, or downstream observability as the chapter advances.

**Script:**

A useful measurement needs structured evidence, which is why `ApplicationStartup` and recorded startup steps follow the baseline discussion.

**Purpose:**

Move from “measure first” to the concrete startup-step evidence that `ApplicationStartup` can record.

### Scene 4 — How Does `ApplicationStartup` Provide Startup Evidence?

**Time:** `03:05–03:43`

**Visual:**

Progressive reveal on the chapter visual: show recorded `StartupStep` name/tag/duration rows and compare the same step before/after one change.

**Script:**

A wall-clock number says startup is slow; `ApplicationStartup` helps show where time went. Configure a recorder such as `BufferingApplicationStartup`, run the application, then inspect the recorded `StartupStep` names, tags, and timing. The goal is not permanent tracing of everything. Compare the same startup path before and after one change, identify the expensive phase, and only then decide whether the owner is bean creation, framework startup, a runner, or another integration.

**Purpose:**

Show how `ApplicationStartup` records structured startup steps that can identify expensive phases instead of relying on one wall-clock number.


## Where Does Startup Tracking Hand Off to Actuator and Observability?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:43–03:54`

**Visual:**

Keep the startup critical-path bar visible and move the cost/failure marker from startup to first use, measurement, or downstream observability as the chapter advances.

**Script:**

Once startup steps are captured, exposing or transporting that data becomes an Actuator/observability concern rather than more application-runtime behavior.

**Purpose:**

Separate collecting startup evidence from exposing and transporting that evidence in production.

### Scene 5 — Where Does Startup Tracking Hand Off to Actuator and Observability?

**Time:** `03:54–04:47`

**Visual:**

Progressive reveal on the chapter visual: move buffered startup evidence toward Actuator/observability exposure while keeping native-image on a separate runtime lane.

**Script:**

At the observability handoff, exposing buffered startup information through production endpoints is an Actuator concern, and broader tracing/metrics pipelines belong to observability curricula. This chapter owns the use of startup tracking as runtime evidence and the decision model around lazy initialization. That split keeps the learning path clean: first understand what startup measurement tells you about Boot lifecycle cost; then learn how production tooling exposes or transports that information. Similarly, AOT/native-image startup characteristics belong to the dedicated `native-image` module. Do not use normal JVM startup tuning rules as a substitute for understanding a different runtime form. Classify the runtime and measurement source before comparing results.

**Purpose:**

Hand production exposure of startup data to Actuator/observability and separate normal JVM tuning from native-image runtime behavior.
