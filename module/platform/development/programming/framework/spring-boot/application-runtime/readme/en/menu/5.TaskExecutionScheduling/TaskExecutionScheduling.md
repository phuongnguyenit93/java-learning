<a id="back-to-top"></a>

# Task execution and scheduling auto-configuration

## Menu
- [Why Does Boot Auto-Configure Task Execution Infrastructure?](#boot-task-execution-purpose)
- [When Does Boot Provide and Consume an `AsyncTaskExecutor`?](#application-task-executor)
- [How Do `spring.task.execution.*` Properties Tune Platform-Thread Execution?](#task-execution-properties)
- [When Does Boot Provide a Task Scheduler?](#boot-task-scheduler)
- [How Do `spring.task.scheduling.*` Properties Tune Scheduling?](#task-scheduling-properties)
- [What Changes When the Application Supplies Its Own Executor or Scheduler?](#custom-executor-scheduler-boundary)
- [Where Does Boot Auto-Configuration Hand Off to Spring Concurrency Semantics?](#framework-concurrency-handoff)

## <a id="boot-task-execution-purpose">Why Does Boot Auto-Configure Task Execution Infrastructure?</a>

<details>
<summary>Click for details</summary>

Applications frequently need an executor even when they are not teaching concurrency as a topic. Framework integrations may need to run asynchronous application work, and scheduled work needs a scheduler. Spring Boot's role is to provide sensible infrastructure when the application has not already supplied it, then expose configuration properties for common tuning.

In Spring Boot 3.3, when no `Executor` bean exists, Boot auto-configures an `AsyncTaskExecutor`. With normal platform threads this is a configured `ThreadPoolTaskExecutor`; with Boot's virtual-thread switch enabled it becomes a `SimpleAsyncTaskExecutor` using virtual threads. Scheduler auto-configuration follows the same integration idea for scheduled task execution.

This chapter teaches the Boot defaults and back-off rules. The semantics of `@Async`, `@Scheduled`, task rejection, locking, and concurrent correctness belong to Spring Framework or Java concurrency curricula.

</details>

- [Back to top](#back-to-top)

---

## <a id="application-task-executor">When Does Boot Provide and Consume an `AsyncTaskExecutor`?</a>

<details>
<summary>Click for details</summary>

The auto-configured `AsyncTaskExecutor` is more than a bean named for application convenience. Boot wires it into several framework integrations that need asynchronous or blocking-work execution. In Boot 3.3 this includes `@EnableAsync`, Spring for GraphQL `Callable` handling, Spring MVC asynchronous request processing, and Spring WebFlux blocking execution support.

That shared use makes executor customization a runtime decision with a wider blast radius than one service method. If the application defines a custom `Executor`, some integrations back off to it, while MVC and WebFlux require an `AsyncTaskExecutor` named `applicationTaskExecutor` for their specific integration contract.

Before replacing the default, inventory who consumes the executor. A custom bean that works for one `@Async` method can accidentally stop satisfying another Boot integration if its type or bean name no longer matches the expected contract.

</details>

- [Back to top](#back-to-top)

---

## <a id="task-execution-properties">How Do `spring.task.execution.*` Properties Tune Platform-Thread Execution?</a>

<details>
<summary>Click for details</summary>

With the platform-thread `ThreadPoolTaskExecutor`, Boot exposes the `spring.task.execution.*` namespace for common pool behavior. Spring Boot 3.3 starts with eight core threads and lets configuration tune values such as maximum pool size, queue capacity, keep-alive time, shutdown behavior, and thread-name prefix.

```yaml
spring:
  task:
    execution:
      pool:
        max-size: 16
        queue-capacity: 100
        keep-alive: 10s
```

The properties describe an executor capacity model; they are not universal performance knobs. A bounded queue changes when the pool is allowed to grow, and workload latency/throughput determines whether larger numbers help. Measure workload behavior and understand the underlying executor semantics before tuning aggressively.

### References

- [Spring Boot 3.3 — Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.3/reference/features/task-execution-and-scheduling.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="boot-task-scheduler">When Does Boot Provide a Task Scheduler?</a>

<details>
<summary>Click for details</summary>

Boot can auto-configure a task scheduler when scheduled task execution needs one, for example when scheduling is enabled. On the normal platform-thread path, Boot provides a `ThreadPoolTaskScheduler`; in Boot 3.3 its default pool contains one thread.

The scheduler is runtime infrastructure, not the source of scheduling semantics. Spring Framework decides how `@Scheduled` methods are discovered and invoked. Boot's responsibility is to provide and configure the scheduler implementation that those mechanisms can use.

That separation is useful when a scheduled job seems late. First determine whether the scheduler is undersized or blocked; then determine whether the scheduling expression, task duration, or concurrent execution policy is the real issue. Only the first part is Boot auto-configuration.

</details>

- [Back to top](#back-to-top)

---

## <a id="task-scheduling-properties">How Do `spring.task.scheduling.*` Properties Tune Scheduling?</a>

<details>
<summary>Click for details</summary>

For a platform-thread `ThreadPoolTaskScheduler`, `spring.task.scheduling.*` configures Boot's scheduler defaults. Common settings include the pool size, thread-name prefix, and shutdown waiting behavior.

```yaml
spring:
  task:
    scheduling:
      thread-name-prefix: scheduling-
      pool:
        size: 2
```

The default single scheduler thread is intentionally conservative. Increasing the pool can allow independent scheduled tasks to overlap, but it also changes concurrency pressure on downstream resources. A larger scheduler does not make a slow task correct or safe to run concurrently.

When virtual threads are enabled, Boot uses a different scheduler strategy and pooling-related scheduler properties no longer describe the active model. That transition is the subject of the next chapter.

</details>

- [Back to top](#back-to-top)

---

## <a id="custom-executor-scheduler-boundary">What Changes When the Application Supplies Its Own Executor or Scheduler?</a>

<details>
<summary>Click for details</summary>

Boot auto-configuration is designed to back off when the application deliberately supplies its own infrastructure. Defining custom executor or scheduler beans can therefore replace part of the default arrangement rather than merely adding another object to the context.

The important question is compatibility with consumers. Multiple executors may require conventional bean names such as `taskExecutor` or `applicationTaskExecutor`, and web integrations can require `AsyncTaskExecutor` specifically. Boot also exposes builder beans so custom executors or schedulers can reuse its configured conventions without copying every default manually.

Customize because the workload needs a different isolation, capacity, naming, or lifecycle policy. Avoid replacing the default only to reproduce it with more code; every custom bean becomes an application-owned runtime contract that must be maintained across upgrades.

</details>

- [Back to top](#back-to-top)

---

## <a id="framework-concurrency-handoff">Where Does Boot Auto-Configuration Hand Off to Spring Concurrency Semantics?</a>

<details>
<summary>Click for details</summary>

This module stops at Boot's provisioning boundary. It explains which executor or scheduler Boot creates, which configuration namespace tunes it, which consumers use it, and when auto-configuration backs off.

Spring Framework owns the semantics of `@Async`, `@EnableAsync`, `@Scheduled`, `@EnableScheduling`, task decorators, and the framework abstractions themselves. Java concurrency owns thread safety, memory visibility, synchronization, executors as a language/runtime topic, interruption, and virtual-thread mechanics.

When debugging, classify the problem first. "Why did Boot create this executor?" belongs here. "Why is my scheduled method overlapping?" is primarily a Spring scheduling question. "Why is shared mutable state corrupted?" is a Java concurrency question. Clear ownership prevents configuration changes from masking correctness bugs.

</details>

- [Back to top](#back-to-top)
