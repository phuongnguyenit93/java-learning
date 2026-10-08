---
video:
  url: ""
---

# Availability states, failure analysis, and exit handling

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

## What Do Liveness and Readiness Mean in Boot Runtime?

<!-- VIDEO_SECTION -->

### Scene 1 — What Do Liveness and Readiness Mean in Boot Runtime?

**Time:** `00:00–00:46`

**Visual:**

Progressive reveal on the chapter visual: show separate LIVENESS and READINESS gauges and a temporary dependency outage that should not automatically break liveness.

**Script:**

Operationally, liveness asks whether the application's internal state is healthy enough to keep running or recover by itself. Liveness and readiness deliberately answer different operational questions. Readiness asks whether the application should currently receive traffic. That difference changes how dependencies should influence each state. A temporary database outage may make some requests impossible, but using that external outage as a liveness failure can cause the platform to restart every application instance and amplify the incident. Readiness can be more conservative because refusing new traffic does not necessarily kill the process.

**Purpose:**

Separate liveness from readiness so external dependency trouble does not automatically become a restart signal.


## When Do Availability States Change During Startup?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:46–00:58`

**Visual:**

Keep the LIVE/READY state machine visible and move the state marker to the startup, shutdown, failure, or exit branch discussed next.

**Script:**

The liveness/readiness distinction becomes operational only when we place the default state changes on the same startup timeline as started, runners, and ready.

**Purpose:**

Make the liveness/readiness distinction observable by attaching both states to concrete startup checkpoints.

### Scene 2 — When Do Availability States Change During Startup?

**Time:** `00:58–01:36`

**Visual:**

Progressive reveal on the chapter visual: animate refresh → Started → CORRECT → runners → Ready → ACCEPTING_TRAFFIC beside lifecycle evidence.

**Script:**

Boot’s default availability changes follow startup. After context refresh and `ApplicationStartedEvent`, Boot publishes liveness as `LivenessState.CORRECT`; runners still execute after that point. Only after they finish does Boot publish `ApplicationReadyEvent` and readiness as `ReadinessState.ACCEPTING_TRAFFIC`. The sequence is refresh → started → LIVE → runners → ready → READY. That gap explains why a process can be alive while still refusing traffic, and the lifecycle experiment lets you verify the ordering directly.

**Purpose:**

Tie Boot’s default availability transitions to started, runners, and ready so LIVE and READY are not treated as synonyms.


## How Does Readiness Change as Shutdown Begins?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:36–01:48`

**Visual:**

Keep the LIVE/READY state machine visible and move the state marker to the startup, shutdown, failure, or exit branch discussed next.

**Script:**

Availability also has a path out of steady state, so after startup transitions we examine what readiness should do when shutdown begins.

**Purpose:**

Extend availability from startup into shutdown so readiness is understood as a changing runtime signal, not a one-time status.

### Scene 3 — How Does Readiness Change as Shutdown Begins?

**Time:** `01:48–02:43`

**Visual:**

Progressive reveal on the chapter visual: flip readiness to REFUSING_TRAFFIC during shutdown while keeping HTTP draining on a separate web-runtime lane.

**Script:**

As startup advances, before or during shutdown, readiness should move away from accepting new traffic so infrastructure can stop routing new work while existing lifecycle shutdown proceeds. Availability also matters when an application leaves steady state. At the generic Boot level, think in terms of runtime state transitions and context shutdown. The exact draining behavior of an embedded HTTP server—how it stops accepting requests and how long existing requests receive—is owned by `web-runtime` and its graceful-shutdown configuration. This boundary prevents two different problems from being conflated: declaring that the application should no longer receive traffic and implementing protocol/server-specific request draining. They cooperate, but they are not the same mechanism.

**Purpose:**

Separate “stop receiving new traffic” from protocol-specific draining when the application leaves steady state.


## How Do `ApplicationAvailability` and `AvailabilityChangeEvent` Expose Runtime State?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:43–02:57`

**Visual:**

Keep the LIVE/READY state machine visible and move the state marker to the startup, shutdown, failure, or exit branch discussed next.

**Script:**

Those state changes are not useful only as diagrams; Boot exposes a read side and an event side so application code can inspect or intentionally publish them.

**Purpose:**

Move from state meaning to the APIs that read and intentionally publish those state changes.

### Scene 4 — How Do `ApplicationAvailability` and `AvailabilityChangeEvent` Expose Runtime State?

**Time:** `02:57–03:35`

**Visual:**

Progressive reveal on the chapter visual: split `ApplicationAvailability` read-side from `AvailabilityChangeEvent` observe/publish-side.

**Script:**

Use `ApplicationAvailability` to read the current liveness or readiness state. `AvailabilityChangeEvent` is the transition side: code can observe it, or publish a state deliberately when the application has enough domain evidence—for example `ReadinessState.REFUSING_TRAFFIC` before a controlled transition. Do not convert every dependency hiccup into `LivenessState.BROKEN`; liveness should stay focused on internal unrecoverable state. The API communicates state, while application policy decides when a change is justified.

**Purpose:**

Show the read/publish sides of Boot availability and the responsibility to publish state only from meaningful domain evidence.


## How Does Boot Turn Startup Failure into Actionable Diagnostics?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:35–03:47`

**Visual:**

Keep the LIVE/READY state machine visible and move the state marker to the startup, shutdown, failure, or exit branch discussed next.

**Script:**

Availability describes runtime state, but a startup that aborts needs a different tool: failure analysis that preserves the root exception and adds actionable context.

**Purpose:**

Separate runtime availability from startup diagnostics when the application fails before it can establish a usable state.

### Scene 5 — How Does Boot Turn Startup Failure into Actionable Diagnostics?

**Time:** `03:47–04:49`

**Visual:**

Progressive reveal on the chapter visual: freeze on root exception → FailureAnalyzer description/action → optional condition report.

**Script:**

On the failure path, the familiar `APPLICATION FAILED TO START` block is therefore a diagnostic layer over the underlying exception, not a substitute for it. If startup fails, Boot first preserves the exception path and then gives registered `FailureAnalyzer` implementations a chance to turn known failures into a concise description plus a concrete action. If no analyzer explains the failure, or if auto-configuration decisions are part of the question, enable Boot's debug output or the `ConditionEvaluationReportLoggingListener` to inspect the conditions report. Detailed condition matching belongs to the auto-configuration module; this chapter uses the report only as runtime failure evidence. A good investigation order is: identify the root exception, read any failure analysis, then inspect configuration/condition evidence relevant to that failure. Avoid randomly changing properties until startup succeeds.

**Purpose:**

Teach a failure-investigation order that preserves the root exception, then uses failure analysis and condition evidence rather than random property changes.


## What Does SpringApplication's Shutdown Hook Own?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:49–05:03`

**Visual:**

Keep the LIVE/READY state machine visible and move the state marker to the startup, shutdown, failure, or exit branch discussed next.

**Script:**

A failed startup is not the same as a normal process exit. Move from diagnostic failure handling to the generic shutdown hook that closes a healthy context.

**Purpose:**

Distinguish abnormal startup failure from the normal shutdown lifecycle that closes a healthy context.

### Scene 6 — What Does SpringApplication's Shutdown Hook Own?

**Time:** `05:03–05:49`

**Visual:**

Progressive reveal on the chapter visual: draw JVM normal exit → shutdown hook → context close → managed destruction callbacks, with unmanaged resources outside.

**Script:**

For process shutdown, closing the context allows Spring-managed destruction callbacks and lifecycle components to participate in shutdown rather than abruptly abandoning managed resources. By default, `SpringApplication` registers a JVM shutdown hook by default so the `ApplicationContext` closes gracefully when the JVM exits normally. The hook is generic application-runtime behavior. It does not define how every external resource or protocol drains work. Individual technologies may have their own lifecycle integration, and embedded web-server graceful shutdown is taught in `web-runtime`. The practical lesson is to keep long-lived resources inside managed lifecycles when possible.

**Purpose:**

Explain what the JVM shutdown hook guarantees for managed context lifecycle—and what it cannot fix for unmanaged resources.


## How Do `ExitCodeGenerator` and `SpringApplication.exit` Communicate Process Status?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:49–06:01`

**Visual:**

Keep the LIVE/READY state machine visible and move the state marker to the startup, shutdown, failure, or exit branch discussed next.

**Script:**

Closing the context cleans up managed state, but command-style applications may also need a machine-readable process result, which is where Boot exit codes enter.

**Purpose:**

Add process-result semantics after context cleanup so command-style applications can communicate outcomes to callers.

### Scene 7 — How Do `ExitCodeGenerator` and `SpringApplication.exit` Communicate Process Status?

**Time:** `06:01–06:57`

**Visual:**

Progressive reveal on the chapter visual: show domain outcome → exit-code generators/mappers → `SpringApplication.exit`, then a separate explicit `System.exit` step.

**Script:**

For command-style exit, Boot supports `ExitCodeGenerator` beans and `ExitCodeExceptionMapper` so application-specific outcomes can be translated into an exit code. Command-style applications sometimes need to communicate a process result to the operating system or an orchestrating script. `SpringApplication.exit(context)` collects the generators and returns the resulting code. Returning a code from `SpringApplication.exit` does not itself terminate the JVM; command-style applications commonly pass it to `System.exit(...)` when process termination is intended. This is especially useful for batch or command applications where "completed with a domain failure" must be machine-readable. Keep exit-code mapping small and documented; it is an integration contract with the process caller, not a replacement for exception handling or application logs.

**Purpose:**

Show how Boot derives an exit code while leaving actual JVM termination to the command application.


## Where Do Generic Runtime Shutdown and Web-Server Graceful Shutdown Split?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:57–07:10`

**Visual:**

Keep the LIVE/READY state machine visible and move the state marker to the startup, shutdown, failure, or exit branch discussed next.

**Script:**

Exit codes complete the generic process story. Embedded HTTP draining is a separate protocol concern, so the final boundary is generic shutdown versus web-server graceful shutdown.

**Purpose:**

End the generic process story exactly where protocol-specific HTTP draining begins.

### Scene 8 — Where Do Generic Runtime Shutdown and Web-Server Graceful Shutdown Split?

**Time:** `07:10–08:11`

**Visual:**

Progressive reveal on the chapter visual: stack generic process/context shutdown above protocol-specific web-server draining and route failures to the correct layer.

**Script:**

At the web-runtime boundary, that model applies whether the Boot application is web, command-line, or another shape. Generic runtime shutdown covers the process/context story: readiness can stop accepting work, the JVM shutdown hook closes the `ApplicationContext`, managed bean lifecycles run, and the process can return an exit code. Web-server graceful shutdown adds a protocol-specific layer: the embedded server must stop accepting new requests and allow in-flight requests a defined opportunity to finish. Those server choices, timeouts, and supported server behavior belong to `web-runtime`. When debugging shutdown, separate the layers. A context that never closes is an application-runtime problem; a server that drains HTTP requests differently than expected is a web-runtime problem; an unmanaged thread that keeps the JVM alive is a Java/application lifecycle problem.

**Purpose:**

Draw the boundary between generic context/process shutdown and embedded web-server graceful request draining.
