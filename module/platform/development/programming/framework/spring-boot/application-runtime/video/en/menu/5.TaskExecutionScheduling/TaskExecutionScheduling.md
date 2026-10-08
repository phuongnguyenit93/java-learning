---
video:
  url: ""
---

# Task execution and scheduling auto-configuration

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

## Why Does Boot Auto-Configure Task Execution Infrastructure?

<!-- VIDEO_SECTION -->

### Scene 1 — Why Does Boot Auto-Configure Task Execution Infrastructure?

**Time:** `00:00–00:52`

**Visual:**

Progressive reveal on the chapter visual: show Boot auto-configuration choosing executor/scheduler implementations only when application infrastructure is absent.

**Script:**

On the managed-execution path, framework integrations may need to run asynchronous application work, and scheduled work needs a scheduler. Many applications need an executor even when they are not teaching concurrency as a topic. Spring Boot's role is to provide sensible infrastructure when the application has not already supplied it, then expose configuration properties for common tuning. In Spring Boot 3.3, when no `Executor` bean exists, Boot auto-configures an `AsyncTaskExecutor`. With normal platform threads this is a configured `ThreadPoolTaskExecutor`; with Boot's virtual-thread switch enabled it becomes a `SimpleAsyncTaskExecutor` using virtual threads. Scheduler auto-configuration follows the same integration idea for scheduled task execution.

**Purpose:**

Explain why Boot provisions task infrastructure as a runtime integration while leaving concurrency correctness to lower layers.


## When Does Boot Provide and Consume an `AsyncTaskExecutor`?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:52–01:03`

**Visual:**

Keep caller → executor/scheduler → worker on screen and move the highlight to the consumer, tuning namespace, custom bean, or ownership edge introduced next.

**Script:**

After establishing why Boot supplies task infrastructure, inspect who actually consumes the shared `AsyncTaskExecutor`; that determines the blast radius of customization.

**Purpose:**

Reveal the real consumers of Boot’s executor before any tuning or replacement decision changes shared infrastructure.

### Scene 2 — When Does Boot Provide and Consume an `AsyncTaskExecutor`?

**Time:** `01:03–01:43`

**Visual:**

Progressive reveal on the chapter visual: open `/runtime/task-executor`, highlight caller/worker/executor type/virtual flag, and fan the executor out to supported consumers.

**Script:**

Boot’s `applicationTaskExecutor` is shared runtime infrastructure, not merely a convenience bean. Supported integrations can consume an `AsyncTaskExecutor`, so replacing it can affect more than one `@Async` method. `GET /spring-boot/runtime/task-executor` submits one bounded task and returns the caller thread, worker thread, concrete executor type, and `workerVirtual`; `differentThread=true` proves real dispatch through the executor. Before replacing the default, check the type and conventional bean-name contracts required by every integration that consumes it.

**Purpose:**

Expose the shared-consumer contract of `applicationTaskExecutor` and prove real dispatch with the Step 5 endpoint before customization.


## How Do `spring.task.execution.*` Properties Tune Platform-Thread Execution?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:43–01:54`

**Visual:**

Keep caller → executor/scheduler → worker on screen and move the highlight to the consumer, tuning namespace, custom bean, or ownership edge introduced next.

**Script:**

Once the shared executor contract is visible, tune the platform-thread implementation with capacity properties rather than changing unrelated application code.

**Purpose:**

Move from consumer impact to the capacity controls that actually tune the platform-thread executor implementation.

### Scene 3 — How Do `spring.task.execution.*` Properties Tune Platform-Thread Execution?

**Time:** `01:54–02:32`

**Visual:**

Progressive reveal on the chapter visual: draw `ThreadPoolTaskExecutor` core=8, queue, max-size, keep-alive and map `spring.task.execution.*` onto the capacity model.

**Script:**

On the platform-thread path, `spring.task.execution.*` describes a real pool-capacity model. Boot 3.3 starts with eight core threads, while `pool.max-size`, `queue-capacity`, `keep-alive`, shutdown settings, and the thread-name prefix adjust `ThreadPoolTaskExecutor`. A bounded queue changes when the pool is allowed to grow, so increasing `max-size` alone may do nothing until queue pressure exists. Treat these as workload-capacity controls rather than “make it faster” knobs; measure latency, queueing, and downstream limits before tuning.

**Purpose:**

Connect `spring.task.execution.*` to platform-thread pool capacity without presenting pool numbers as universal performance tuning.


## When Does Boot Provide a Task Scheduler?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:32–02:46`

**Visual:**

Keep caller → executor/scheduler → worker on screen and move the highlight to the consumer, tuning namespace, custom bean, or ownership edge introduced next.

**Script:**

Task execution and scheduling are related runtime services but not the same one, so move from the application executor to the scheduler Boot provides for scheduled work.

**Purpose:**

Split executor work from scheduled work before introducing the scheduler as a separate Boot-provided runtime service.

### Scene 4 — When Does Boot Provide a Task Scheduler?

**Time:** `02:46–03:30`

**Visual:**

Progressive reveal on the chapter visual: show the default one-thread `ThreadPoolTaskScheduler` and two jobs waiting on the same scheduler.

**Script:**

For scheduled work, on the normal platform-thread path, Boot provides a `ThreadPoolTaskScheduler`; in Boot 3.3 its default pool contains one thread. Boot provides a task scheduler when scheduled task execution needs one, for example when scheduling is enabled. The scheduler is runtime infrastructure, not the source of scheduling semantics. Spring Framework decides how `@Scheduled` methods are discovered and invoked. Boot's responsibility is to provide and configure the scheduler implementation that those mechanisms can use. That separation is useful when a scheduled job seems late.

**Purpose:**

Separate scheduler provisioning from scheduling semantics and show the default single-thread platform scheduler path.


## How Do `spring.task.scheduling.*` Properties Tune Scheduling?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:30–03:43`

**Visual:**

Keep caller → executor/scheduler → worker on screen and move the highlight to the consumer, tuning namespace, custom bean, or ownership edge introduced next.

**Script:**

Now that the scheduler exists on the diagram, its platform-thread defaults can be tuned through the scheduling namespace—with different concurrency consequences from executor tuning.

**Purpose:**

Attach scheduling properties to the scheduler model so their concurrency effect is not confused with executor tuning.

### Scene 5 — How Do `spring.task.scheduling.*` Properties Tune Scheduling?

**Time:** `03:43–04:23`

**Visual:**

Progressive reveal on the chapter visual: increase scheduler pool size and animate overlapping jobs plus downstream concurrency pressure.

**Script:**

For the platform-thread `ThreadPoolTaskScheduler`, `spring.task.scheduling.*` controls defaults such as `pool.size`, thread-name prefix, and shutdown waiting. Boot begins conservatively with one scheduler thread. A larger pool can let independent scheduled jobs overlap, but it also increases concurrency against databases, APIs, locks, and shared state. It cannot make a slow or non-thread-safe task correct. With virtual threads enabled, this pooled scheduler model is no longer active, so the same pool settings do not carry over unchanged.

**Purpose:**

Explain what `spring.task.scheduling.*` changes and why a larger pool changes concurrency pressure rather than fixing slow jobs.


## What Changes When the Application Supplies Its Own Executor or Scheduler?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:23–04:35`

**Visual:**

Keep caller → executor/scheduler → worker on screen and move the highlight to the consumer, tuning namespace, custom bean, or ownership edge introduced next.

**Script:**

Those properties describe Boot defaults only until the application supplies its own beans; the next step is understanding back-off and consumer compatibility.

**Purpose:**

Show where property-driven defaults stop and application-owned executor/scheduler beans begin.

### Scene 6 — What Changes When the Application Supplies Its Own Executor or Scheduler?

**Time:** `04:35–05:26`

**Visual:**

Progressive reveal on the chapter visual: inject custom executor/scheduler beans and show Boot defaults backing off while consumer name/type contracts remain.

**Script:**

When the application supplies its own bean, defining custom executor or scheduler beans can replace part of the default arrangement rather than merely adding another object to the context. Boot auto-configuration is designed to back off when the application deliberately supplies its own infrastructure. The important question is compatibility with consumers. Multiple executors may require conventional bean names such as `taskExecutor` or `applicationTaskExecutor`, and web integrations can require `AsyncTaskExecutor` specifically. Boot also exposes builder beans so custom executors or schedulers can reuse its configured conventions without copying every default manually. Customize because the workload needs a different isolation, capacity, naming, or lifecycle policy.

**Purpose:**

Show that custom executor/scheduler beans trigger back-off and create application-owned compatibility contracts with consumers.


## Where Does Boot Auto-Configuration Hand Off to Spring Concurrency Semantics?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:26–05:38`

**Visual:**

Keep caller → executor/scheduler → worker on screen and move the highlight to the consumer, tuning namespace, custom bean, or ownership edge introduced next.

**Script:**

After provisioning, tuning, and back-off are clear, stop at the correct boundary: Boot supplies infrastructure, while Spring Framework and Java define concurrency semantics.

**Purpose:**

Finish by separating Boot provisioning from Spring scheduling semantics and Java thread correctness.

### Scene 7 — Where Does Boot Auto-Configuration Hand Off to Spring Concurrency Semantics?

**Time:** `05:38–06:36`

**Visual:**

Progressive reveal on the chapter visual: triage ownership: Boot provisioning/properties, Spring `@Async`/`@Scheduled`, Java thread-safety semantics.

**Script:**

At the concurrency handoff, it explains which executor or scheduler Boot creates, which configuration namespace tunes it, which consumers use it, and when auto-configuration backs off. This chapter stops at Boot's provisioning boundary. Spring Framework owns the semantics of `@Async`, `@EnableAsync`, `@Scheduled`, `@EnableScheduling`, task decorators, and the framework abstractions themselves. Java concurrency owns thread safety, memory visibility, synchronization, executors as a language/runtime topic, interruption, and virtual-thread mechanics. When debugging, classify the problem first. "Why did Boot create this executor?" belongs here. "Why is my scheduled method overlapping?" is primarily a Spring scheduling question. "Why is shared mutable state corrupted?" is a Java concurrency question. Clear ownership prevents configuration changes from masking correctness bugs.

**Purpose:**

Stop Boot configuration at the provisioning boundary and route `@Async`/`@Scheduled` behavior and thread correctness to Spring/Java concurrency.
