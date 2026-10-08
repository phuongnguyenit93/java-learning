---
video:
  url: ""
---

# Application arguments and runners

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

## How Does `ApplicationArguments` Interpret Command-Line Input?

<!-- VIDEO_SECTION -->

### Scene 1 — How Does `ApplicationArguments` Interpret Command-Line Input?

**Time:** `00:00–00:38`

**Visual:**

Progressive reveal on the chapter visual: show terminal input `--mode=demo sample` beside the real `/runtime/arguments` response and highlight option/non-option views.

**Script:**

Use `--mode=demo sample` as the concrete invocation. Boot keeps the original source arguments, while `ApplicationArguments` also separates options from positional data: `mode` is an option and `sample` is non-option input. `/spring-boot/runtime/arguments` returns those views from the process that actually started, so no manual `String[]` parser is needed. This is the right abstraction for invocation-specific startup flags; property-source precedence remains an externalized-configuration concern.

**Purpose:**

Demonstrate Boot’s parsed argument model with real option/non-option evidence instead of manual `String[]` parsing.


## When Do `ApplicationRunner` and `CommandLineRunner` Execute?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:38–00:52`

**Visual:**

Keep the command-line pane and startup timeline together; shift focus from parsed input to the runner phase or runner ordering card.

**Script:**

Parsed startup input becomes useful when code needs to act on it, so place runners on the lifecycle line where normal beans exist but readiness still waits.

**Purpose:**

Connect parsed invocation data to the lifecycle phase where bean-backed startup work can consume it.

### Scene 2 — When Do `ApplicationRunner` and `CommandLineRunner` Execute?

**Time:** `00:52–01:41`

**Visual:**

Progressive reveal on the chapter visual: zoom to Started → runner phase → Ready with normal beans available and the readiness gate still closed.

**Script:**

Inside the runner phase, they run before `ApplicationReadyEvent` and before Boot marks readiness as `ACCEPTING_TRAFFIC`. Boot invokes `ApplicationRunner` and `CommandLineRunner` after the `ApplicationContext` has been refreshed and `ApplicationStartedEvent` has been published. That timing makes runners a natural place for startup work that needs ordinary beans and must finish before the application is considered ready: validating an application-specific invariant, warming a small required cache, or executing a command-style workload in a non-web application. The timing also creates responsibility. If a runner blocks for minutes, readiness is delayed for minutes. If a runner throws, startup does not complete normally.

**Purpose:**

Place runners precisely after started/context refresh but before ready so their effect on readiness is visible.


## How Do `ApplicationRunner` and `CommandLineRunner` Differ?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:41–01:51`

**Visual:**

Keep the command-line pane and startup timeline together; shift focus from parsed input to the runner phase or runner ordering card.

**Script:**

Both runner interfaces occupy the same phase; the useful difference is the shape of the arguments each one receives.

**Purpose:**

Separate two runner APIs that share timing but expose different argument contracts.

### Scene 3 — How Do `ApplicationRunner` and `CommandLineRunner` Differ?

**Time:** `01:51–02:32`

**Visual:**

Progressive reveal on the chapter visual: compare `ApplicationRunner(ApplicationArguments)` with `CommandLineRunner(String...)` while keeping lifecycle timing identical.

**Script:**

With normal beans available, `CommandLineRunner.run(String... args)` receives the raw command-line strings. Both runner interfaces occupy the same lifecycle phase; their main difference is the argument shape they receive. `ApplicationRunner.run(ApplicationArguments args)` receives Boot's parsed representation with option and non-option accessors. Prefer `ApplicationRunner` when the code cares about command-line options as structured input. Prefer `CommandLineRunner` when the exact raw argument sequence is itself the useful contract. Neither interface is more asynchronous or "later" than the other.

**Purpose:**

Distinguish `ApplicationRunner` and `CommandLineRunner` by argument contract while keeping their lifecycle timing identical.


## How Is Multiple-Runner Ordering Controlled?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:32–02:44`

**Visual:**

Keep the command-line pane and startup timeline together; shift focus from parsed input to the runner phase or runner ordering card.

**Script:**

Once a single runner is understood, multiple startup tasks introduce ordering, so the next concern is how Boot makes that order explicit.

**Purpose:**

Introduce ordering only after one runner’s contract is clear, so sequence is not mistaken for a different lifecycle phase.

### Scene 4 — How Is Multiple-Runner Ordering Controlled?

**Time:** `02:44–03:30`

**Visual:**

Progressive reveal on the chapter visual: stack ordered runner cards and animate lower order values running first within the same phase.

**Script:**

When several runners exist, a runner can implement `Ordered` or use `@Order` so Boot invokes the collection in a deterministic relative order. With multiple runners, Spring's ordering contract applies. Lower order values have higher precedence. Ordering is useful when there is a real startup dependency, such as "load reference data before validating a derived index". It should not become a hidden workflow engine. If runner B cannot make sense without runner A, make that dependency obvious in naming, tests, or orchestration rather than scattering many numeric priorities.

**Purpose:**

Show how `Ordered`/`@Order` makes multi-runner startup sequencing explicit without implying business transaction ordering.


## Which Startup Work Belongs in a Runner?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:30–03:41`

**Visual:**

Keep the command-line pane and startup timeline together; shift focus from parsed input to the runner phase or runner ordering card.

**Script:**

Ordering answers which startup task runs first; it does not answer whether a task belongs in the runner phase at all.

**Purpose:**

Move from “which runner runs first?” to the more important design question of whether the work belongs in startup at all.

### Scene 5 — Which Startup Work Belongs in a Runner?

**Time:** `03:41–04:34`

**Visual:**

Progressive reveal on the chapter visual: show a finite startup-work checklist ending before the readiness gate opens.

**Script:**

For required startup work, examples include validating runtime prerequisites that cannot be checked earlier, executing a short one-time startup migration owned by the application, or running a command-mode task that intentionally completes and then exits. Good runner work has three properties: it needs a fully refreshed context, it belongs to application startup, and readiness should wait for its successful completion. Keep the work bounded and observable. Log a clear start/failure outcome, propagate fatal exceptions, and make repeated execution safe when the deployment model may restart the process. For configuration validation that can happen during binding, use configuration validation instead. For bean construction invariants, use normal bean initialization.

**Purpose:**

Identify bounded, bean-dependent initialization that legitimately belongs before readiness.


## What Work Should Not Block the Runner Phase?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:34–04:48`

**Visual:**

Keep the command-line pane and startup timeline together; shift focus from parsed input to the runner phase or runner ordering card.

**Script:**

A runner is valid only while the work is bounded startup work, so finish by identifying jobs that should leave the startup path and use managed background infrastructure instead.

**Purpose:**

Draw the boundary between finite startup initialization and work that must leave the readiness-critical path.

### Scene 6 — What Work Should Not Block the Runner Phase?

**Time:** `04:48–05:40`

**Visual:**

Progressive reveal on the chapter visual: move long-lived loops/retries from the runner phase to a managed executor/scheduler lane.

**Script:**

Before readiness opens, those activities prevent the runner phase from finishing and therefore delay `ApplicationReadyEvent` and readiness. A runner is a poor home for an endless polling loop, a long-lived message consumer, or CPU-heavy background processing. Starting unmanaged threads inside a runner also bypasses Boot-managed execution infrastructure and makes shutdown harder to reason about. If work must continue after startup, submit it to an appropriate managed executor, scheduler, or technology-specific runtime component. If work can happen after traffic begins, do not force readiness to wait without a clear requirement. Another smell is using a runner to repair missing configuration or swallow fatal initialization errors.

**Purpose:**

Keep long-lived, blocking, or retry-forever work off the runner phase so startup remains finite and observable.
