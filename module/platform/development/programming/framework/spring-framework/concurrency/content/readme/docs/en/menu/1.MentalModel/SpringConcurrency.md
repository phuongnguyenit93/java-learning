<a id="back-to-top"></a>

# Spring Concurrency Mental Model

## Menu
- [Why Spring Task Concurrency Exists](#spring-concurrency-purpose)
- [Execution, Async Invocation, and Scheduling](#execution-async-scheduling)
- [Spring Responsibilities vs JDK Concurrency](#spring-vs-jdk-concurrency)
- [Container-Managed Executors and Schedulers](#container-managed-task-infrastructure)

## <a id="spring-concurrency-purpose">Why Spring Task Concurrency Exists</a>

<details>
<summary>Click for details</summary>

Java already has threads, `Executor`, `ExecutorService`, futures, scheduled executors, and virtual threads. Spring task concurrency exists because applications usually need those mechanisms to participate in the same configuration, dependency-injection, lifecycle, proxy, and environment model as the rest of the Spring application.

The Framework therefore adds **integration and policy**, not a replacement concurrency model. `TaskExecutor` abstracts task submission, `TaskScheduler` abstracts time-based execution, `@Async` turns a bean-method call into executor-backed work, and `@Scheduled` lets the container register recurring or one-time scheduled methods.

A useful mental model is:

```text
Java concurrency primitives
→ provide the actual thread/executor/future semantics

Spring task infrastructure
→ selects/configures those semantics as beans
→ connects them to proxies, annotations, lifecycle and application context
```

This module matters when the question changes from "how does a thread pool work?" to "which execution policy does this Spring application use, who owns it, and what happens at invocation, shutdown, failure, or context boundaries?"

### Learning path

Use the module in this order:

```text
Spring task concurrency mental model (you are here)
→ separate task execution, async invocation, scheduling, and ownership boundaries

TaskExecutor and @Async
→ learn how ready work is executed and how method calls cross an async boundary

Context propagation
→ learn what is lost when execution moves to another thread

TaskScheduler
→ learn the programmatic time model before annotations

@Scheduled
→ apply that time model through declarative registration

Virtual-thread integration
→ compare thread-per-task execution with pooled platform-thread policies

Lifecycle, failures, and production reasoning
→ combine admission, context, scheduling, failure observation, diagnostics, and shutdown
```

By the end, the learner should be able to trace work from submission or schedule registration to actual execution, identify which Spring/JDK abstraction owns each behavior, and choose an executor or scheduler policy with explicit capacity, context, failure, and lifecycle expectations.

</details>

- [Back to top](#back-to-top)

---

## <a id="execution-async-scheduling">Execution, Async Invocation, and Scheduling</a>

<details>
<summary>Click for details</summary>

Three concerns are related but not interchangeable.

**Task execution** means work is ready now and must be handed to an execution strategy. A `TaskExecutor` decides whether that work runs in the caller thread, on a new thread, or through a pool.

**Asynchronous method invocation** is a call-site behavior. Under the normal Spring proxy model, an `@Async` method call is intercepted, packaged as work, submitted to an executor, and control returns to the caller without waiting for the method body to finish.

**Scheduling** introduces time as an input. A `TaskScheduler` or `@Scheduled` declaration decides when work becomes eligible: once at an instant, periodically by fixed rate/delay, through cron, or from a custom `Trigger`.

```text
execute now        → TaskExecutor
call method async  → proxy + @Async + TaskExecutor
execute by time    → TaskScheduler / @Scheduled
```

The distinction prevents configuration mistakes. Increasing an async executor pool does not change a cron expression; changing a scheduler pool does not fix self-invocation of `@Async`; and neither mechanism automatically propagates caller thread-local context.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-vs-jdk-concurrency">Spring Responsibilities vs JDK Concurrency</a>

<details>
<summary>Click for details</summary>

Spring builds on Java concurrency rather than hiding its correctness rules.

The **JDK owns** thread behavior, the Java Memory Model, synchronization/atomicity, `Executor`/`Future` semantics, `ThreadPoolExecutor` admission behavior, `ScheduledExecutorService`, interruption, and JDK 21 Virtual Thread semantics. Those foundations should be learned in Java Concurrency.

Spring owns the **application integration layer**: task-executor/scheduler abstractions, bean-style configuration, adapters for different deployment environments, proxy-based `@Async`, annotation-driven scheduling, task decoration, lifecycle coordination, and Framework-specific exception/contracts at those boundaries.

This has two practical consequences:

- Spring cannot make mutable shared state safe merely because the code runs through `@Async`.
- Spring can make the chosen executor or scheduler easier to configure, replace, inject, observe, and stop consistently with the application.

When a problem is about visibility, races, locks, interruption, or virtual-thread runtime semantics, return to Java Concurrency. When it is about which Spring bean executes work, proxy interception, scheduling registration, task decoration, or context-close behavior, it belongs here.

</details>

- [Back to top](#back-to-top)

---

## <a id="container-managed-task-infrastructure">Container-Managed Executors and Schedulers</a>

<details>
<summary>Click for details</summary>

Putting an executor or scheduler under Spring management turns execution policy into application infrastructure. It can be named, injected, configured in one place, decorated, and coordinated with `ApplicationContext` lifecycle instead of being created ad hoc inside business code.

For a typical standalone Spring application, a bean such as `ThreadPoolTaskExecutor` or `ThreadPoolTaskScheduler` owns a local JDK executor internally. In a managed Jakarta EE environment, Spring can instead delegate through `DefaultManagedTaskExecutor` or `DefaultManagedTaskScheduler` so the application uses the environment's managed executor services rather than creating threads directly.

That distinction is important: "Spring-managed bean" and "Jakarta EE managed thread" are not the same concept. Spring can manage the bean lifecycle in both cases while the underlying thread ownership differs.

Centralizing the policy also creates one place to answer production questions: What is the pool or thread-per-task strategy? What queue/admission policy applies? What thread names appear in logs? How is context decorated? What happens during shutdown? Which `@Async` or `@Scheduled` methods use this infrastructure?

Avoid constructing raw executors in arbitrary service methods unless their lifetime is intentionally local and fully managed there. Long-lived application executors are infrastructure resources and should have an explicit owner.

</details>

- [Back to top](#back-to-top)
