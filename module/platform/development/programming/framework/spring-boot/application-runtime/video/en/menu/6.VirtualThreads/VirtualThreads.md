---
video:
  url: ""
---

# Virtual threads in Spring Boot

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

## What Does `spring.threads.virtual.enabled` Change?

<!-- VIDEO_SECTION -->

### Scene 1 — What Does `spring.threads.virtual.enabled` Change?

**Time:** `00:00–00:38`

**Visual:**

Progressive reveal on the chapter visual: toggle `spring.threads.virtual.enabled` and swap platform-thread executor/scheduler implementations for virtual-thread-backed simple implementations.

**Script:**

Treat `spring.threads.virtual.enabled=true` as a Boot integration switch. With Java 21, Boot keeps the executor/scheduler abstractions but changes supported infrastructure behind them: task execution uses a virtual-thread-enabled `SimpleAsyncTaskExecutor` instead of the normal `ThreadPoolTaskExecutor`, and scheduling uses a virtual-thread-enabled `SimpleAsyncTaskScheduler` instead of the normal pooled scheduler. The property does not change executors that application code creates itself; it aligns Boot-managed infrastructure with one application-level choice.

**Purpose:**

Make the Boot property a runtime-integration switch for supported infrastructure, not a claim about Java-wide virtual-thread behavior.


## How Do Boot's Executor and Scheduler Strategies Change?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:38–00:50`

**Visual:**

Split the execution diagram into platform-thread and virtual-thread lanes, then move the highlight to the strategy or JVM-lifetime consequence discussed next.

**Script:**

The application switch matters because it changes the implementation behind Boot-managed executor and scheduler abstractions, not because it changes Java semantics globally.

**Purpose:**

Translate the single Boot switch into the concrete executor/scheduler implementation change it actually controls.

### Scene 2 — How Do Boot's Executor and Scheduler Strategies Change?

**Time:** `00:50–01:45`

**Visual:**

Progressive reveal on the chapter visual: keep the same executor/scheduler abstractions while changing the implementation underneath.

**Script:**

Behind the executor abstraction, the application still interacts with executor/scheduler abstractions, but the implementation no longer represents a fixed worker pool in the same way as the platform-thread path. Virtual-thread enablement changes the implementation strategy behind Boot-managed task infrastructure. `SimpleAsyncTaskExecutor` can start virtual threads for submitted tasks, while `SimpleAsyncTaskScheduler` uses virtual-thread execution for scheduled work. Boot also configures its corresponding builder beans so custom infrastructure created from those builders follows the virtual-thread choice. This is why migration should be evaluated at the abstraction boundary rather than by searching for every `newVirtualThreadPerTaskExecutor` call. If application code bypasses Boot and creates its own executors, the Boot property cannot automatically govern that application-owned infrastructure.

**Purpose:**

Show the concrete executor/scheduler strategy swap behind the same abstractions so migration is reasoned at the Boot boundary.


## Why Do Pool-Sizing Properties Stop Describing the Same Model?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:45–01:56`

**Visual:**

Split the execution diagram into platform-thread and virtual-thread lanes, then move the highlight to the strategy or JVM-lifetime consequence discussed next.

**Script:**

Once the implementation strategy changes, old pool arithmetic no longer describes the same runtime model, so pool-size assumptions must be revisited.

**Purpose:**

Use that implementation change to invalidate platform-pool tuning assumptions before discussing virtual-thread capacity.

### Scene 3 — Why Do Pool-Sizing Properties Stop Describing the Same Model?

**Time:** `01:56–02:45`

**Visual:**

Progressive reveal on the chapter visual: gray out platform pool-sizing controls on the virtual-thread lane and explain why fixed-worker math no longer maps directly.

**Script:**

Once the worker model changes, that model does not map directly to Boot's virtual-thread-backed `SimpleAsyncTaskExecutor` and `SimpleAsyncTaskScheduler`. Pool-size properties belong to how a bounded set of platform worker threads is managed. Spring Boot 3.3 explicitly notes that pooling-related scheduler properties are ignored when virtual threads are enabled. The practical consequence is that an old tuning rule such as "increase `spring.task.scheduling.pool.size`" may stop describing the active runtime. Do not carry platform-thread pool arithmetic into the virtual-thread path and assume it still controls parallelism the same way. Virtual threads still consume CPU, memory, connections, rate limits, and downstream capacity.

**Purpose:**

Prevent platform-thread pool-size tuning from being carried unchanged into a virtual-thread-backed execution model.


## Why Can Daemon Virtual Threads Require `spring.main.keep-alive`?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:45–02:55`

**Visual:**

Split the execution diagram into platform-thread and virtual-thread lanes, then move the highlight to the strategy or JVM-lifetime consequence discussed next.

**Script:**

Virtual-thread-backed infrastructure also changes process-liveness assumptions because virtual threads are daemon threads; that is where `spring.main.keep-alive` becomes relevant.

**Purpose:**

Connect daemon-thread semantics to application process lifetime, which is the specific reason the keep-alive property exists.

### Scene 4 — Why Can Daemon Virtual Threads Require `spring.main.keep-alive`?

**Time:** `02:55–03:33`

**Visual:**

Progressive reveal on the chapter visual: show only daemon virtual threads remaining, JVM exit risk, then `spring.main.keep-alive=true` as the process-lifetime anchor.

**Script:**

Virtual threads are daemon threads, so the JVM may exit when no non-daemon thread remains. That matters for non-web or scheduling-oriented applications whose continuing work may live entirely on virtual threads. `spring.main.keep-alive=true` tells Boot to keep the process alive in that situation. Enable it for that lifetime requirement, not as a performance setting: it does not fix task correctness, scheduling policy, or downstream capacity.

**Purpose:**

Connect daemon virtual threads to JVM process lifetime and explain when `spring.main.keep-alive=true` is needed.


## What Remains Java Virtual-Thread Semantics Rather Than Boot Ownership?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:33–03:45`

**Visual:**

Split the execution diagram into platform-thread and virtual-thread lanes, then move the highlight to the strategy or JVM-lifetime consequence discussed next.

**Script:**

Boot integrates the switch, but scheduling, pinning, interruption, and workload suitability are still Java virtual-thread semantics, so the chapter ends at that ownership boundary.

**Purpose:**

Stop at Boot integration and hand JVM-level virtual-thread behavior to the Java concurrency owner.

### Scene 5 — What Remains Java Virtual-Thread Semantics Rather Than Boot Ownership?

**Time:** `03:45–04:43`

**Visual:**

Progressive reveal on the chapter visual: draw Boot switch/integration on one side and Java scheduling/carrier/pinning/blocking/interruption semantics on the other.

**Script:**

At the Java concurrency boundary, Java owns the semantics underneath: virtual-thread scheduling, blocking behavior, pinning, carrier threads, daemon status, interruption, thread-local behavior, and the general decision of whether a workload is appropriate for virtual threads. Boot owns the integration switch and the supported integrations that react to it. That boundary matters because `spring.threads.virtual.enabled=true` is not a promise that every workload becomes faster. A CPU-bound workload still competes for CPU, and code or libraries can have behavior that changes the expected scalability of virtual threads. Use Boot documentation to understand which Boot-managed components change. Use the Java concurrency curriculum and official JDK guidance to understand why virtual threads behave as they do and how to diagnose Java-level concurrency problems.

**Purpose:**

Keep Boot ownership to the switch/integration and hand pinning, carriers, blocking, interruption, and workload suitability to Java concurrency.
