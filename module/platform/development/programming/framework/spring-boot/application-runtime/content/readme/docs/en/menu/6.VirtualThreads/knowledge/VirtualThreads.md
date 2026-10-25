<a id="back-to-top"></a>

# Virtual threads in Spring Boot

## Menu
- [What Does `spring.threads.virtual.enabled` Change?](#virtual-thread-switch)
- [How Do Boot's Executor and Scheduler Strategies Change?](#virtual-executor-scheduler)
- [Why Do Pool-Sizing Properties Stop Describing the Same Model?](#virtual-thread-pool-properties)
- [Why Can Daemon Virtual Threads Require `spring.main.keep-alive`?](#virtual-thread-daemon-keepalive)
- [What Remains Java Virtual-Thread Semantics Rather Than Boot Ownership?](#virtual-thread-java-boundary)

## <a id="virtual-thread-switch">What Does `spring.threads.virtual.enabled` Change?</a>

<details>
<summary>Click for details</summary>

Spring Boot 3.3 integrates Java 21 virtual threads through one application-level switch: `spring.threads.virtual.enabled=true`. The property does not teach the JVM how virtual threads work; it tells Boot to choose virtual-thread-backed implementations for supported runtime infrastructure.

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

For task execution, Boot then uses a `SimpleAsyncTaskExecutor` configured for virtual threads instead of the normal `ThreadPoolTaskExecutor`. For scheduling, it uses a `SimpleAsyncTaskScheduler` configured for virtual threads instead of the normal pooled scheduler.

The value of the switch is integration consistency: supported Boot-managed execution infrastructure follows the same application setting. Whether a workload benefits from virtual threads still depends on Java-level behavior and workload characteristics.

</details>

- [Back to top](#back-to-top)

---

## <a id="virtual-executor-scheduler">How Do Boot's Executor and Scheduler Strategies Change?</a>

<details>
<summary>Click for details</summary>

Virtual-thread enablement changes the *implementation strategy* behind Boot-managed task infrastructure. The application still interacts with executor/scheduler abstractions, but the implementation no longer represents a fixed worker pool in the same way as the platform-thread path.

`SimpleAsyncTaskExecutor` can start virtual threads for submitted tasks, while `SimpleAsyncTaskScheduler` uses virtual-thread execution for scheduled work. Boot also configures its corresponding builder beans so custom infrastructure created from those builders follows the virtual-thread choice.

This is why migration should be evaluated at the abstraction boundary rather than by searching for every `newVirtualThreadPerTaskExecutor` call. If application code bypasses Boot and creates its own executors, the Boot property cannot automatically govern that application-owned infrastructure.

</details>

- [Back to top](#back-to-top)

---

## <a id="virtual-thread-pool-properties">Why Do Pool-Sizing Properties Stop Describing the Same Model?</a>

<details>
<summary>Click for details</summary>

Pool-size properties describe how a bounded set of platform worker threads is managed. That model does not map directly to Boot's virtual-thread-backed `SimpleAsyncTaskExecutor` and `SimpleAsyncTaskScheduler`. Spring Boot 3.3 explicitly notes that pooling-related scheduler properties are ignored when virtual threads are enabled.

The practical consequence is that an old tuning rule such as "increase `spring.task.scheduling.pool.size`" may stop describing the active runtime. Do not carry platform-thread pool arithmetic into the virtual-thread path and assume it still controls parallelism the same way.

Virtual threads still consume CPU, memory, connections, rate limits, and downstream capacity. Concurrency may need limits elsewhere, but those limits are a workload/concurrency design topic rather than a reason to pretend the old pool settings still apply.

</details>

- [Back to top](#back-to-top)

---

## <a id="virtual-thread-daemon-keepalive">Why Can Daemon Virtual Threads Require `spring.main.keep-alive`?</a>

<details>
<summary>Click for details</summary>

Virtual threads are daemon threads. A JVM can exit when only daemon threads remain, so an application that relies on virtual-thread-based background activity may not have a non-daemon thread keeping the process alive.

Spring Boot provides `spring.main.keep-alive=true` for applications that need the JVM to remain alive even when all application work is running on daemon virtual threads. This matters especially for non-web or scheduling-oriented applications where there may be no other runtime component keeping a non-daemon thread alive.

```yaml
spring:
  main:
    keep-alive: true
```

Treat this as process-lifecycle configuration, not a performance option. If the process exits unexpectedly after enabling virtual threads, inspect which threads are responsible for liveness before adding unrelated executor tuning.

</details>

- [Back to top](#back-to-top)

---

## <a id="virtual-thread-java-boundary">What Remains Java Virtual-Thread Semantics Rather Than Boot Ownership?</a>

<details>
<summary>Click for details</summary>

Boot owns the switch and the supported integrations that react to it. Java owns the semantics underneath: virtual-thread scheduling, blocking behavior, pinning, carrier threads, daemon status, interruption, thread-local behavior, and the general decision of whether a workload is appropriate for virtual threads.

That boundary matters because `spring.threads.virtual.enabled=true` is not a promise that every workload becomes faster. A CPU-bound workload still competes for CPU, and code or libraries can have behavior that changes the expected scalability of virtual threads.

Use Boot documentation to understand which Boot-managed components change. Use the Java concurrency curriculum and official JDK guidance to understand *why* virtual threads behave as they do and how to diagnose Java-level concurrency problems.

</details>

- [Back to top](#back-to-top)
