<a id="back-to-top"></a>

# Virtual-Thread Integration in Spring

## Menu
- [Spring vs JDK Virtual-Thread Responsibilities](#virtual-thread-boundary)
- [VirtualThreadTaskExecutor](#virtual-thread-task-executor)
- [SimpleAsyncTaskExecutor with Virtual Threads](#simple-async-task-executor-virtual)
- [SimpleAsyncTaskScheduler](#simple-async-task-scheduler)
- [Fixed-Delay Semantics in SimpleAsyncTaskScheduler](#virtual-thread-fixed-delay)
- [SimpleAsyncTaskExecutor Controls](#virtual-thread-concurrency-limits)
- [Choosing Virtual Threads vs Pooled Platform Threads](#virtual-thread-decision)

## <a id="virtual-thread-boundary">Spring vs JDK Virtual-Thread Responsibilities</a>

<details>
<summary>Click for details</summary>

Virtual threads are a JDK 21 concurrency mechanism; Spring does not redefine how they are scheduled, mounted, blocked, or pinned. Spring's role is narrower: it supplies task-execution and scheduling implementations that can create virtual threads, so application code can keep using `TaskExecutor`, `@Async`, or `TaskScheduler` instead of coupling every call site to JDK thread creation.

A useful ownership boundary is:

```text
JDK 21 virtual-thread semantics
→ Java Concurrency owns

Choosing/configuring a Spring executor or scheduler that uses virtual threads
→ this module owns
```

Virtual threads change the cost model of blocking concurrency, not the correctness rules of shared state. A task running on a virtual thread still needs safe publication, synchronization where required, bounded access to databases or remote services, and explicit context propagation when thread-local state must cross an executor boundary.

Do not read "virtual" as "unlimited". Virtual threads make large numbers of blocking tasks cheaper than one-platform-thread-per-task, but connection pools, rate limits, CPU, memory, file descriptors, and remote-service quotas remain finite.

</details>

- [Back to top](#back-to-top)

---

## <a id="virtual-thread-task-executor">VirtualThreadTaskExecutor</a>

<details>
<summary>Click for details</summary>

`VirtualThreadTaskExecutor` is Spring Framework 6.1's minimal `AsyncTaskExecutor` backed by JDK 21 virtual threads. Each submitted task runs on a newly created virtual thread. There is no worker pool and therefore no pool-size or queue-capacity tuning model.

Its deliberately small configuration surface is the point: the direct option is a thread-name prefix. It fits a requirement such as "run each task on a virtual thread" when the application does not need Spring-side throttling or task decoration.

```java
@Bean
VirtualThreadTaskExecutor virtualThreadExecutor() {
    return new VirtualThreadTaskExecutor("orders-vt-");
}
```

The class does not provide the richer controls available on `SimpleAsyncTaskExecutor`. If the design needs a concurrency limit, `TaskDecorator`, or task-termination tracking, prefer `SimpleAsyncTaskExecutor` with virtual threads enabled instead of trying to turn `VirtualThreadTaskExecutor` into a pool-like component.

Thread names are diagnostic metadata, not an isolation boundary. Business correctness must not depend on a particular virtual-thread name or carrier thread.

</details>

- [Back to top](#back-to-top)

---

## <a id="simple-async-task-executor-virtual">SimpleAsyncTaskExecutor with Virtual Threads</a>

<details>
<summary>Click for details</summary>

`SimpleAsyncTaskExecutor` follows a thread-per-task model and does not reuse threads. In Spring Framework 6.1 it can switch that per-task thread creation to JDK 21 virtual threads:

```java
@Bean
SimpleAsyncTaskExecutor applicationVirtualThreads() {
    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("app-vt-");
    executor.setVirtualThreads(true);
    executor.setConcurrencyLimit(200);
    executor.setTaskDecorator(new MyTaskDecorator());
    return executor;
}
```

This differs from `ThreadPoolTaskExecutor`. A pool reuses a bounded set of platform threads and commonly couples admission to a queue. `SimpleAsyncTaskExecutor` creates a thread for each admitted task; with virtual threads that model becomes much cheaper for blocking workloads, but it is still not a pool.

The richer feature set is why Spring's `VirtualThreadTaskExecutor` Javadoc points here when an application needs controls such as concurrency limiting or task decoration. In the Spring 6.1 baseline, a positive concurrency limit is a **throttle**: once the active count reaches the limit, the submitting thread waits until another task finishes and releases a slot. It is not a queue-capacity setting and does not reject merely because the limit is full. For `@Async` callers, that means the proxy call itself can block during submission when this throttle is saturated.

A concurrency limit protects downstream capacity; a decorator carries execution context; termination tracking lets `close()` wait for active tasks when a task-termination timeout is configured. Unlike the lifecycle coordination of executors such as `ThreadPoolTaskExecutor`, that close-time tracking is not participation in a coordinated `SmartLifecycle` stop. Those concerns remain separate even when task threads are virtual.

</details>

- [Back to top](#back-to-top)

---

## <a id="simple-async-task-scheduler">SimpleAsyncTaskScheduler</a>

<details>
<summary>Click for details</summary>

`SimpleAsyncTaskScheduler` is a Spring Framework 6.1 `TaskScheduler` designed around a thread-per-execution model. It keeps one scheduler thread that decides *when* work should fire, then normally starts a separate execution thread for each firing. On JDK 21, enabling virtual threads makes those per-execution threads virtual.

```text
ThreadPoolTaskScheduler
→ fixed scheduler pool
→ task body runs on scheduler thread(s)

SimpleAsyncTaskScheduler
→ one scheduler thread decides firing time
→ normally one separate execution thread per firing
```

Because it extends `SimpleAsyncTaskExecutor`, its normal **handed-off execution path** can use concurrency limiting, task decoration, and task-termination tracking. It can delegate that handed-off execution to a target executor as well, which is useful when an application wants one scheduling clock but a different execution policy. Fixed-delay execution is the important exception described next.

The key mental model is to separate *scheduling* from *execution*. A single scheduling thread does not imply single-concurrency execution, because most firings are handed off to separate threads.

</details>

- [Back to top](#back-to-top)

---

## <a id="virtual-thread-fixed-delay">Fixed-Delay Semantics in SimpleAsyncTaskScheduler</a>

<details>
<summary>Click for details</summary>

Fixed-delay scheduling is the intentional exception to `SimpleAsyncTaskScheduler`'s usual hand-off model. To preserve traditional fixed-delay semantics, fixed-delay tasks execute on the scheduler thread itself. The delay is measured after one execution completes before the next execution is eligible.

A slow fixed-delay task can therefore occupy the single scheduler thread and delay other scheduling work. This is a property of `SimpleAsyncTaskScheduler`, not a limitation of virtual threads in general.

Because this fixed-delay path runs the user task directly on the scheduler thread rather than through the normal `execute(...)` hand-off, the scheduler's inherited `TaskDecorator`, concurrency throttle, and configured target task executor do not govern that fixed-delay task body. Configure fixed-delay workloads with that boundary in mind rather than assuming every `SimpleAsyncTaskScheduler` firing passes through the same execution controls.

For this scheduler variant, Spring recommends fixed-rate or cron triggers when the goal is to benefit from the thread-per-execution/virtual-thread model. If true fixed-delay semantics are required and executions may block for a long time, consider whether another scheduler implementation better matches the workload.

```text
SimpleAsyncTaskScheduler + fixed rate/cron
→ scheduler decides time, execution is handed off

SimpleAsyncTaskScheduler + fixed delay
→ task runs on the scheduler thread
→ one slow execution can hold the scheduling lane
```

</details>

- [Back to top](#back-to-top)

---

## <a id="virtual-thread-concurrency-limits">SimpleAsyncTaskExecutor Controls</a>

<details>
<summary>Click for details</summary>

When `SimpleAsyncTaskExecutor` uses virtual threads, its surrounding controls still matter:

- **Concurrency limit** throttles admission so no more than the configured number of tasks are active at once. In Spring 6.1, with a **positive** limit, a submitter waits for a slot when the limit is reached; the limit is not a thread-pool size or rejection threshold. Setting the limit to `0` means no concurrency is allowed and execution fails with `IllegalStateException` rather than waiting.
- **Task decoration** wraps the execution callback so logging, observation, or application context can be captured and restored around the task. The cleanup rules from the Context Propagation chapter still apply.
- **Termination tracking** lets `close()` wait for active tasks when a task-termination timeout is configured. This is close-time task tracking, not a coordinated lifecycle-stop contract. Tracking has runtime overhead because executions must be monitored.

These controls explain why `SimpleAsyncTaskExecutor` is the more configurable virtual-thread choice. They also show why "virtual threads remove concurrency configuration" is a bad mental model: virtual threads reduce *thread scarcity*, but they do not remove admission control, context, lifecycle, or downstream-capacity concerns.

The same throttle semantics matter on `SimpleAsyncTaskScheduler` paths that use the normal hand-off mechanism, such as fixed-rate and trigger-driven execution. If a positive concurrency limit is saturated, dispatch from the scheduler thread can wait for a slot, which can in turn delay later firings. Fixed-delay task bodies bypass this throttle because they execute directly on the scheduler thread. A limit is therefore a deliberate backpressure choice rather than a free safety switch.

Do not conflate the knobs. A concurrency limit protects capacity; task decoration propagates context; a termination timeout controls how `close()` waits for tracked tasks. Tuning one does not solve the other two.

</details>

- [Back to top](#back-to-top)

---

## <a id="virtual-thread-decision">Choosing Virtual Threads vs Pooled Platform Threads</a>

<details>
<summary>Click for details</summary>

Choose virtual threads when the workload contains many independent tasks that spend substantial time blocked on I/O and the application benefits from a straightforward thread-per-task programming model. They are especially attractive when platform-thread pool sizing would otherwise be dominated by waiting rather than CPU work.

Prefer a bounded platform-thread pool, or at least keep an explicit concurrency limit, when concurrency itself must be constrained: CPU-heavy work, a small database connection pool, a fragile downstream service, or strict process resource budgets are common examples.

| Question | Virtual-thread-oriented executor | `ThreadPoolTaskExecutor` |
| --- | --- | --- |
| Thread reuse | Thread per task | Reuses platform threads |
| Best fit | Many blocking independent tasks | Explicit bounded worker/queue model |
| Admission/backpressure | Design separately or apply a concurrency limit | Pool + queue + rejection provide a natural boundary |
| Context propagation | Still explicit | Still explicit |
| Shared-state correctness | Unchanged | Unchanged |

Do not choose by thread count alone. Start from workload, latency targets, downstream capacity, shutdown behavior, and diagnostic needs. Spring provides multiple execution policies; it does not make one policy universally correct.

</details>

- [Back to top](#back-to-top)
