<a id="back-to-top"></a>

# TaskScheduler and Programmatic Scheduling

## Menu
- [Why Spring Provides TaskScheduler](#task-scheduler-purpose)
- [One-Time, Fixed-Rate, Fixed-Delay, and Trigger Scheduling](#scheduling-time-model)
- [Trigger and TriggerContext](#trigger-context)
- [TaskScheduler Implementations and Deployment Environments](#task-scheduler-implementations)
- [Scheduler Thread and Execution Models](#scheduler-execution-models)
- [Runtime Rescheduling and Cancellation](#runtime-rescheduling-cancellation)
- [Pause, Resume, Shutdown, and Termination](#scheduler-lifecycle)
- [When TaskScheduler Is Enough and When Quartz Enters](#quartz-boundary)

## <a id="task-scheduler-purpose">Why Spring Provides TaskScheduler</a>

<details>
<summary>Click for details</summary>

`TaskScheduler` is Spring's abstraction for work whose eligibility depends on time. It complements `TaskExecutor`: an executor answers "how should runnable work execute?", while a scheduler also answers "when should this work become eligible?".

The Spring 6.1 contract uses `Instant` and `Duration` rather than forcing application code to manipulate raw millisecond timestamps:

```java
ScheduledFuture<?> schedule(Runnable task, Instant startTime);
@Nullable
ScheduledFuture<?> schedule(Runnable task, Trigger trigger);
ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period);
ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay);
```

`TaskScheduler#getClock()` exposes the clock used for scheduling calculations. That matters for trigger logic and testability because "now" is part of the scheduling model.

As with `TaskExecutor`, the abstraction separates application intent from deployment mechanics. A local application can use a thread-pool scheduler, a Jakarta EE application can delegate to a managed scheduled executor, and code that only depends on `TaskScheduler` can retain the same scheduling intent.

</details>

- [Back to top](#back-to-top)

---

## <a id="scheduling-time-model">One-Time, Fixed-Rate, Fixed-Delay, and Trigger Scheduling</a>

<details>
<summary>Click for details</summary>

Scheduling policies differ in the **time relation they preserve**:

- **One-time** scheduling runs once at or after a given `Instant`.
- **Fixed rate** targets a regular cadence between scheduled start times.
- **Fixed delay** waits for one execution to complete, then waits the configured delay before the next execution.
- **Trigger-based** scheduling asks a `Trigger` to calculate the next `Instant` from execution history.

```text
fixed rate
scheduled start ── period ── scheduled start

fixed delay
start → run → complete ── delay ── next start
```

Choose from business semantics, not from the shortest method name. Polling that must rest after each completion is naturally fixed-delay. A wall-clock business calendar is normally cron/trigger based. Sampling intended every N seconds may be fixed-rate, but long executions must be interpreted together with the concrete scheduler's execution model.

Timing policy and execution capacity are separate. With `ThreadPoolTaskScheduler`, the underlying `ScheduledThreadPoolExecutor` does not overlap successive executions of the **same** fixed-rate or fixed-delay registration; a slow execution makes later executions run late instead. `SimpleAsyncTaskScheduler` is different for fixed-rate/cron work because each firing is normally handed to a separate execution thread, so one execution can still be active when a later firing is dispatched. Distinct registrations can also overlap independently. Always reason about timing together with the chosen scheduler rather than treating fixed rate itself as a parallelism guarantee.

</details>

- [Back to top](#back-to-top)

---

## <a id="trigger-context">Trigger and TriggerContext</a>

<details>
<summary>Click for details</summary>

A `Trigger` is the extension point for schedules that cannot be expressed as a constant rate or delay. In Spring 6.1 it calculates the next execution as an `Instant`:

```java
@Nullable
Instant nextExecution(TriggerContext context);
```

`TriggerContext` exposes scheduling history:

- `lastScheduledExecution()` — when the previous execution was supposed to run;
- `lastActualExecution()` — when it actually started;
- `lastCompletion()` — when it completed;
- `getClock()` — the clock used for time calculations.

That history lets a trigger choose its semantics deliberately. A backoff trigger may calculate from completion; a wall-clock trigger may ignore task duration and calculate from a calendar rule. Returning `null` from `nextExecution` means that there is no next firing.

The word "completion" is scheduler-relative. With `ThreadPoolTaskScheduler`, the task body runs on the scheduler thread, so `lastCompletion()` reflects completion of that task body. With trigger-driven work on `SimpleAsyncTaskScheduler`, the scheduled callback normally hands the user task to a separate execution thread; the trigger context can therefore record completion of that hand-off callback before the business task itself finishes. Do not base a completion-sensitive backoff policy on `lastCompletion()` without first checking the concrete scheduler execution model.

Spring provides `CronTrigger` for cron expressions and `PeriodicTrigger` when an API specifically wants a generic `Trigger`. When code already knows that it needs fixed rate or fixed delay, the direct `TaskScheduler` methods are generally clearer.

Keep custom triggers focused on *time calculation*. Business work belongs in the scheduled task, not in `nextExecution(...)`.

</details>

- [Back to top](#back-to-top)

---

## <a id="task-scheduler-implementations">TaskScheduler Implementations and Deployment Environments</a>

<details>
<summary>Click for details</summary>

The main `TaskScheduler` implementations differ in thread ownership and execution model:

- `ThreadPoolTaskScheduler` wraps a local `ScheduledThreadPoolExecutor` and exposes bean-style configuration. It is Spring's traditional local scheduler.
- `ConcurrentTaskScheduler` adapts an existing `ScheduledExecutorService` when scheduling infrastructure already exists.
- `DefaultManagedTaskScheduler` delegates to the environment's managed `ManagedScheduledExecutorService` in a Jakarta EE/JSR-236 style runtime.
- `SimpleAsyncTaskScheduler`, introduced in Spring 6.1, uses one scheduler thread and normally a separate execution thread per firing; it aligns well with JDK 21 Virtual Threads.

The implementation choice is partly a deployment decision. Creating local threads is normal in a standalone Spring application, while a managed application-server environment may require delegation to its managed scheduler.

It is also an execution-policy decision. A fixed scheduler pool and a thread-per-firing scheduler have different overlap, saturation, and lifecycle characteristics. The common `TaskScheduler` API reduces call-site coupling, but it does not erase those operational differences.

</details>

- [Back to top](#back-to-top)

---

## <a id="scheduler-execution-models">Scheduler Thread and Execution Models</a>

<details>
<summary>Click for details</summary>

A scheduler may separate "who tracks time?" from "who runs the task body?" in different ways.

`ThreadPoolTaskScheduler` stays close to `ScheduledExecutorService` semantics: task bodies execute on the scheduler thread(s) themselves. Its pool size is therefore both scheduling capacity and execution capacity. A slow task can occupy one of those scheduler threads.

That also means a `ScheduledFuture` returned by `ThreadPoolTaskScheduler` represents the actual execution/completion of the scheduled task or recurring series, not merely a hand-off to a different worker pool.

`SimpleAsyncTaskScheduler` has a different shape:

```text
one scheduler thread
      ↓ firing decision
separate execution thread per firing
```

except for fixed-delay tasks, which execute on the scheduler thread to preserve fixed-delay semantics.

Its returned `ScheduledFuture` represents the scheduler's hand-off to the execution thread rather than the actual completion of the user task. Code that needs to observe business-task completion must therefore use its own completion signal instead of interpreting that scheduling handle as a worker-task result.

When diagnosing overlap or latency, identify the concrete scheduler first. Ask whether task bodies run on scheduler threads, are handed off to per-execution threads, or are delegated to another target executor.

</details>

- [Back to top](#back-to-top)

---

## <a id="runtime-rescheduling-cancellation">Runtime Rescheduling and Cancellation</a>

<details>
<summary>Click for details</summary>

Programmatic scheduling normally returns a runtime handle. The time-based and periodic `TaskScheduler` methods return a `ScheduledFuture<?>`; `schedule(Runnable, Trigger)` is the exception because it is nullable and returns `null` when the supplied `Trigger` never produces a next execution. When a handle exists, keeping it lets the application cancel future executions with `cancel(...)` according to the underlying scheduler semantics.

Dynamic rescheduling is normally a **cancel-and-register** operation:

```text
keep current handle
→ cancel old registration
→ build/calculate new policy
→ register again
→ store new handle
```

With Spring's higher-level registration infrastructure, `ScheduledTask` is another runtime representation that can be cancelled. The design lesson is the same: keep a registration handle if the business requirement includes runtime cancellation.

Do not treat `@Scheduled` annotation attributes as mutable runtime state. Annotation-driven schedules are discovered from bean metadata and registered by the container. If a schedule must change because of database/configuration events, use `TaskScheduler` directly or programmatic registration through `ScheduledTaskRegistrar`/`SchedulingConfigurer`.

Cancellation is not rollback. Cancelling future firings does not undo side effects from an execution that already started, so job logic still needs appropriate idempotency and interruption handling.

</details>

- [Back to top](#back-to-top)

---

## <a id="scheduler-lifecycle">Pause, Resume, Shutdown, and Termination</a>

<details>
<summary>Click for details</summary>

Schedulers are long-lived infrastructure and need a shutdown model. Spring-managed scheduler implementations participate in container lifecycle so the application can stop scheduling new work and terminate execution resources as the context closes.

Spring Framework 6.1 adds pause/resume behavior to `ThreadPoolTaskScheduler` through its `SmartLifecycle` integration, alongside lifecycle-based graceful shutdown. In this model, `stop()` pauses task execution and `start()` resumes it while the scheduler remains reusable; this is lifecycle behavior rather than a pair of public `pause()`/`resume()` methods. Shutdown is different: it permanently closes the underlying execution resource for that application context.

Operational policy still needs explicit answers:

- Should delayed tasks execute after shutdown begins?
- Should existing periodic tasks continue?
- How long should container shutdown wait for running work?
- Can cancellation interrupt running tasks?
- What happens to work blocked on I/O?

The underlying scheduled executor exposes policies for several of these questions and Spring surfaces configuration hooks around it. "Managed by Spring" does not mean "all scheduled work is guaranteed to finish before process exit"; the deployment termination window remains the outer boundary.

Test shutdown with realistic long-running/blocking work rather than only instant demo callbacks.

</details>

- [Back to top](#back-to-top)

---

## <a id="quartz-boundary">When TaskScheduler Is Enough and When Quartz Enters</a>

<details>
<summary>Click for details</summary>

`TaskScheduler` is a good fit for in-process scheduling where the application's lifecycle is also the schedule's lifecycle: maintenance, cache refresh, polling, metrics collection, or other work that can simply be registered again when the process starts.

Quartz becomes relevant when scheduling itself needs stronger infrastructure capabilities such as persisted job/trigger state, durable job identity, richer calendars and misfire handling, or coordination across application instances. Those requirements are not solved by merely increasing a `ThreadPoolTaskScheduler` pool size.

Spring Framework provides Quartz integration so Quartz jobs and triggers can participate in Spring configuration and dependency injection. This module only establishes the boundary; Quartz job stores, clustering, trigger semantics, and operations remain Quartz-specific knowledge.

A practical persistence test is useful: if losing the process and therefore losing the in-memory registration is unacceptable, an in-process `TaskScheduler` alone is probably not the complete scheduling architecture.

</details>

- [Back to top](#back-to-top)
