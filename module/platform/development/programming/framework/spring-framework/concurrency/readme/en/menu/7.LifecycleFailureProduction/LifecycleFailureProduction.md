<a id="back-to-top"></a>

# Lifecycle, Failures, and Production Reasoning

## Menu
- [Coordinated Lifecycle, Shutdown, and Termination](#executor-lifecycle)
- [Failure Boundaries: Rejection, Async Failure, and Scheduled Failure](#failure-boundaries)
- [Runtime Diagnostics and Observability Signals](#runtime-diagnostics)
- [Spring Framework vs Spring Boot Task Configuration](#boot-auto-config-boundary)
- [End-to-End Executor and Scheduler Decisions](#production-synthesis)

## <a id="executor-lifecycle">Coordinated Lifecycle, Shutdown, and Termination</a>

<details>
<summary>Click for details</summary>

A major benefit of Spring-managed executors is that they can participate in the `ApplicationContext` lifecycle instead of living as unmanaged thread infrastructure. That gives the container a place to coordinate stop and shutdown, but it does **not** guarantee that every queued task will finish.

For `ThreadPoolTaskExecutor`, shutdown policy is explicit. In this module:

```java
executor.setWaitForTasksToCompleteOnShutdown(true);
executor.setAwaitTerminationSeconds(2);
```

`setWaitForTasksToCompleteOnShutdown(true)` asks the executor to let running tasks and queued tasks complete rather than interrupting/clearing them immediately. In Spring Framework 6.1, enabling this flag also means the executor does not go through the normal coordinated lifecycle-stop phase; it performs its soft shutdown later in the executor's destruction step. `setAwaitTerminationSeconds(2)` additionally blocks container shutdown for at most two seconds while waiting for termination. These settings solve different problems: the first chooses the work-completion/shutdown path; the second chooses how long the rest of the container waits. Expiry of that two-second wait is not a task-kill deadline: if the process remains alive, executor work may keep completing while the rest of shutdown proceeds.

A production timeout must reflect the deployment termination window and the resources the task still needs. Waiting ten seconds is useless if the platform sends a hard kill after five seconds. Conversely, letting executor work continue while database/network resources are already being destroyed can make "graceful" shutdown fail in a different way.

Treat task submission during shutdown as part of the lifecycle model too. Once shutdown starts, new work may be rejected depending on executor state and configuration. Business flows should not assume that a Spring bean remains a valid place to enqueue fresh background work until the final line of process termination.

</details>

- [Back to top](#back-to-top)

---

## <a id="failure-boundaries">Failure Boundaries: Rejection, Async Failure, and Scheduled Failure</a>

<details>
<summary>Click for details</summary>

"An asynchronous task failed" is too vague for production diagnosis. There are several distinct boundaries:

1. **Submission/rejection failure** happens before business code starts. A saturated or shutting-down executor can reject the task synchronously.
2. **`@Async` execution failure with a future-like return** is captured by the returned `Future`/`CompletableFuture`. The caller observes it when joining/getting or composing the future.
3. **`@Async void` execution failure** has no returned future, so Spring routes uncaught exceptions to `AsyncUncaughtExceptionHandler`.
4. **Scheduled-task failure** belongs to scheduler/error-handler semantics. It should not be confused with executor rejection, and a recurring schedule should be designed with explicit retry/idempotency expectations.

The module's `/spring-executor/void-exception` experiment demonstrates one useful distinction: rejection may be thrown while the proxy is trying to submit the task, whereas an exception thrown later by the actual `void` method is observed through `AsyncUncaughtExceptionHandler`.

Operational handling should therefore record *where* the failure happened. Retrying a rejected submission, retrying business logic that already ran, and alerting on a broken recurring job are different policies with different duplicate-work risks.

</details>

- [Back to top](#back-to-top)

---

## <a id="runtime-diagnostics">Runtime Diagnostics and Observability Signals</a>

<details>
<summary>Click for details</summary>

Concurrency problems become easier to diagnose when execution policy is visible. Give threads meaningful prefixes, document executor/scheduler configuration, and observe runtime state such as active threads, pool size, queue depth, completed work, rejection, task duration, and shutdown delays where the chosen implementation exposes those signals.

Spring Framework 6.1 also has observability support for `@Scheduled` method execution. A `ScheduledTaskRegistrar` can be configured with an `ObservationRegistry`; Spring then creates observations for scheduled executions with information such as the method, outcome, and error. This is scheduled-method instrumentation, not automatic instrumentation of every arbitrary task submitted to every `TaskExecutor`.

Useful evidence is layered:

```text
Thread names / pool state
→ where and how work is executing

Task/application correlation ids
→ which request or business operation produced the task

Scheduled-task observations
→ duration/outcome/error of @Scheduled execution

Application metrics/traces/logs
→ downstream effects and business impact
```

Observability is not a substitute for bounded design. A dashboard can show that a queue is growing, but it does not decide the correct queue capacity or rejection policy.

</details>

- [Back to top](#back-to-top)

---

## <a id="boot-auto-config-boundary">Spring Framework vs Spring Boot Task Configuration</a>

<details>
<summary>Click for details</summary>

Spring Framework owns the abstractions and mechanisms taught in this module: `TaskExecutor`, `ThreadPoolTaskExecutor`, `TaskScheduler`, `@Async`, `@Scheduled`, task decoration, lifecycle hooks, and related infrastructure.

Spring Boot may create and configure executor/scheduler beans automatically from Boot conventions and configuration properties. That convenience belongs to the Spring Boot curriculum. It must not be used as the explanation for Framework semantics because the same Spring Framework APIs can be used without Boot.

A good debugging sequence is:

```text
1. Which Spring Framework abstraction is the application using?
2. Which concrete executor/scheduler bean actually handles the work?
3. Who created/configured that bean: application code or Spring Boot auto-configuration?
4. Which pool/queue/virtual-thread/scheduling/lifecycle settings are effective?
```

This boundary prevents two common mistakes: assuming Boot defaults are universal Spring defaults, and teaching a property name before the learner understands the behavior that property eventually configures.

</details>

- [Back to top](#back-to-top)

---

## <a id="production-synthesis">End-to-End Executor and Scheduler Decisions</a>

<details>
<summary>Click for details</summary>

A production design should start from workload semantics, not from an annotation:

```text
ordinary background execution
→ choose a TaskExecutor policy

method-level fire-and-continue behavior
→ @Async + executor + explicit failure observation

time-based execution
→ TaskScheduler/@Scheduled + trigger semantics
```

Then make the policy explicit:

- **Capacity:** What actually limits safe concurrency — CPU, DB connections, remote quotas, memory, or latency?
- **Admission:** Is work queued, throttled, rejected, or handed a fresh virtual thread?
- **Context:** Which values must cross the task boundary, and who owns them?
- **Failure:** How are rejection, future failure, `void` async failure, and scheduled failure observed?
- **Lifecycle:** What happens to running/queued work during context close and process termination?
- **Scheduling:** Can executions overlap? Does fixed delay, fixed rate, cron, or one-time execution match the business rule?
- **Diagnostics:** Can operators identify the executor/scheduler, task origin, duration, and outcome?

The final choice is usually a combination rather than a single class name. A blocking I/O service might use virtual threads but still apply a concurrency limit equal to downstream capacity; a CPU-heavy batch might use a bounded `ThreadPoolTaskExecutor`; a recurring maintenance job might use `ThreadPoolTaskScheduler` with a deliberately small pool and idempotent job logic.

The goal of this module is not "use more threads." It is to make execution, scheduling, context, failure, and shutdown behavior intentional and observable.

</details>

- [Back to top](#back-to-top)
