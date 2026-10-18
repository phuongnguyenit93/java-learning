<a id="back-to-top"></a>

# Task Execution and @Async

## Menu
- [TaskExecutor and AsyncTaskExecutor Contracts](#task-executor)
- [Task Execution Strategies and Implementations](#task-executor-strategies)
- [ThreadPoolTaskExecutor Configuration and Saturation](#spring-executor-config)
- [@EnableAsync, @Async, and Proxy Dispatch](#async-annotation)
- [AsyncConfigurer, Default Executor, and Qualification](#async-executor-selection)
- [Async Return Types and Failure Observation](#async-return-type)
- [Executable Evidence for Task Execution and @Async](#spring-executor-experiments)

Spring does not replace Java concurrency primitives. It provides abstraction and lifecycle integration to make their use more convenient in the application.

```text
Java Executor concepts
        ↓
Spring TaskExecutor / ThreadPoolTaskExecutor
        ↓
@Async proxy dispatch
```

## <a id="task-executor">TaskExecutor and AsyncTaskExecutor Contracts</a>

<details>
<summary>Click for details</summary>

`TaskExecutor` is Spring's core contract for "accept this `Runnable` for execution". Its single `execute(Runnable)` method deliberately mirrors `java.util.concurrent.Executor`; the Spring value is that the execution strategy can be configured, injected, adapted to the deployment environment, and managed as application infrastructure.

`AsyncTaskExecutor` extends that model with asynchronous submission conveniences, including `Callable` and future-oriented operations. Code that only needs fire-and-submit semantics can depend on `TaskExecutor`; code that needs a completion handle may require the richer contract or another future-producing API.

`ThreadPoolTaskExecutor` is the most common local pooled implementation. It wraps a JDK `ThreadPoolExecutor` but exposes Spring bean-style configuration for core/max pool size, queue capacity, keep-alive, thread naming, task decoration, rejection handling, and lifecycle.

The admission mental model remains the JDK one:

```text
below core size
→ create worker

core reached
→ queue work

queue full and below max size
→ grow toward max

queue full and max reached
→ reject
```

Spring does not make this algorithm different. What changes at the Spring abstraction boundary is how the component is configured and how rejection is surfaced. Application code using `TaskExecutor` should be prepared for Spring's `TaskRejectedException` contract rather than depending on a raw JDK `RejectedExecutionException` escaping unchanged.

That exception path exists only when the underlying executor/rejection handler actually rejects by throwing. A custom JDK `RejectedExecutionHandler` still controls the overload behavior: while the executor is still running, `CallerRunsPolicy` executes the rejected task on the submitting thread, while policies such as `DiscardPolicy` can drop work without throwing. A custom rejection handler therefore changes observable execution semantics, not merely the exception type. With `@Async`, `CallerRunsPolicy` can make the target invocation run on the caller before the proxy returns, while a silent discard policy can lose the async work without an exception signal.

</details>

- [Back to top](#back-to-top)

---

## <a id="task-executor-strategies">Task Execution Strategies and Implementations</a>

<details>
<summary>Click for details</summary>

The interface is intentionally small so the implementation can express very different execution strategies:

- `SyncTaskExecutor` runs the task in the caller thread. It is useful when asynchronous behavior is not wanted, including some tests, but it does not create concurrency.
- `SimpleAsyncTaskExecutor` creates a new thread per task and does not reuse threads. In Spring 6.1 it can use JDK 21 Virtual Threads; it can also apply a concurrency limit and task decoration.
- `ThreadPoolTaskExecutor` uses a configurable `ThreadPoolExecutor` and is the normal choice when the application wants an explicit worker-pool/queue/rejection model.
- `ConcurrentTaskExecutor` adapts an existing JDK `Executor` when Spring should delegate to infrastructure that already exists.
- `DefaultManagedTaskExecutor` delegates to the environment's managed executor service in a Jakarta EE/JSR-236 style runtime.
- `VirtualThreadTaskExecutor` is the minimal Spring 6.1 virtual-thread-per-task option and is covered in the Virtual Thread chapter.

Do not select by class-name familiarity. Select by the behavior the application needs: synchronous vs asynchronous, pool vs thread-per-task, local vs environment-managed thread ownership, bounded admission, context decoration, and lifecycle.

A useful test is to replace the concrete class mentally with its policy description. If "pooled execution with queue capacity 500 and abort rejection" is the real requirement, that policy should remain visible in configuration and operational documentation instead of disappearing behind a generic `Executor` variable.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-executor-config">ThreadPoolTaskExecutor Configuration and Saturation</a>

<details>
<summary>Click for details</summary>

The module uses a dedicated bean, `threadLearningTaskExecutor`, so the learning experiments have a deterministic execution policy:

```java
executor.setCorePoolSize(2);
executor.setMaxPoolSize(4);
executor.setQueueCapacity(8);
executor.setKeepAliveSeconds(30);
executor.setThreadNamePrefix("thread-learning-executor-");
executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
```

The important point is not the numbers `2/4/8`; it is the interaction. With a positive queue capacity, work is queued after the core workers are busy. The pool only grows beyond core size when that queue is full. Once both the queue and max pool are exhausted, the configured rejection policy applies.

This surprises developers who set a very large queue and expect `maxPoolSize` to be reached often. A large queue can absorb work for a long time, so the pool may remain near its core size while latency accumulates in the queue.

The opposite configuration is also important: `queueCapacity = 0` uses direct hand-off semantics rather than a buffering queue, so once core workers are busy the executor can grow toward `maxPoolSize` immediately. That changes overload shape dramatically and should be chosen deliberately.

Production sizing must come from workload and downstream constraints: arrival rate, task duration, CPU usage, database/HTTP connection capacity, latency SLO, queueing tolerance, and termination window. Demo values are intentionally small to make behavior visible.

It is often useful to externalize operational tuning values, but the binding mechanism is not owned by this module. The legacy content used Spring Boot `@ConfigurationProperties` as an example; the general operational principle is preserved here, while Boot-specific property binding belongs to the Spring Boot curriculum.

Do not expose every policy as an unchecked property. Rejection strategy, hard safety limits, and context/lifecycle behavior can change system semantics and should retain validated application-level invariants.

</details>

- [Back to top](#back-to-top)

---

## <a id="async-annotation">@EnableAsync, @Async, and Proxy Dispatch</a>

<details>
<summary>Click for details</summary>

`@EnableAsync` registers Spring's asynchronous method-execution infrastructure. Under its default `AdviceMode.PROXY`, Spring applies an async interceptor to eligible bean method calls that pass through the proxy. `@Async` marks the method or class whose invocation should be submitted to an executor.

```text
caller
→ Spring proxy intercepts @Async
→ resolve executor
→ submit method invocation
→ caller regains control / receives completion handle
→ worker executes target method
```

The annotation is not a Java language feature and does not make direct calls asynchronous by itself. That explains the classic self-invocation trap: `this.otherAsyncMethod()` stays inside the target object and normally bypasses the proxy, so proxy-mode async interception does not happen.

A clean design usually places the async boundary between collaborating Spring beans. AspectJ advice mode can intercept local calls differently, but deep proxy/weaving mechanics belong to the Spring AOP module.

Also avoid treating `@Async` as a generic "make it faster" marker. It changes control flow, failure observation, thread/context boundaries, transaction assumptions, and shutdown behavior. The caller must be designed for those consequences.

</details>

- [Back to top](#back-to-top)

---

## <a id="async-executor-selection">AsyncConfigurer, Default Executor, and Qualification</a>

<details>
<summary>Click for details</summary>

Async execution needs an executor-selection rule. At the application level there are two common paths:

- provide a default through `AsyncConfigurer#getAsyncExecutor()` or rely on Spring's default executor resolution;
- qualify a particular method with `@Async("beanNameOrQualifier")` when that method needs a specific executor.

If no explicit `AsyncConfigurer` executor is supplied, Spring's async infrastructure looks for a suitable default executor in the context, conventionally preferring a unique `TaskExecutor` and otherwise an `Executor` bean named `taskExecutor`. If neither can be resolved, Spring falls back to a `SimpleAsyncTaskExecutor`. Production applications should still configure the intended policy explicitly rather than treating that fallback as a sizing decision.

The current module makes selection explicit on the learning methods:

```java
@Async("threadLearningTaskExecutor")
public CompletableFuture<Map<String, Object>> runAsync(...) { ... }
```

`TaskExecutorConfig` implements `AsyncConfigurer` to provide the `AsyncUncaughtExceptionHandler`; it does not override `getAsyncExecutor()`. The demo methods therefore use their `@Async` qualifier to choose `threadLearningTaskExecutor` directly.

Use separate executors when workloads need genuinely different policies — for example, small latency-sensitive tasks versus slow blocking integration work. Do not create many executors merely to label code; every executor adds capacity, lifecycle, metrics, and tuning responsibility.

</details>

- [Back to top](#back-to-top)

---

## <a id="async-return-type">Async Return Types and Failure Observation</a>

<details>
<summary>Click for details</summary>

Spring's `@Async` contract requires the declared return type to be either `void` or a Future-compatible type such as `Future<T>`/`CompletableFuture<T>`. The choice determines how completion and failure are observed.

With a future-like return, the proxy gives the caller the asynchronous completion handle. The target method still has to satisfy its Java signature and therefore returns its own Future value internally — often an already-completed Future carrying the method result — but that target Future is not the handle returned directly to the caller. The interceptor submits the invocation to the chosen executor and returns the executor-backed async handle. The caller must still observe that handle by `get`, `join`, composition, callbacks, or returning it to another async boundary. Ignoring the future can make a real failure operationally invisible.

With `void`, no completion handle exists. If the method body throws after dispatch, Spring routes the uncaught exception to `AsyncUncaughtExceptionHandler`. The module registers `AsyncExceptionProbe` for that purpose:

```java
@Async("threadLearningTaskExecutor")
public void failWithoutFuture(String correlationId) {
    throw new IllegalStateException("async void failure: " + correlationId);
}
```

The `/spring-executor/void-exception` endpoint registers a correlation-aware observation future before calling the proxy. Two paths are intentionally distinguished:

```text
submission succeeds
→ worker runs method
→ method throws
→ AsyncUncaughtExceptionHandler observes failure

submission is rejected
→ failure occurs synchronously at proxy/executor boundary
→ method body never starts
```

`AsyncUncaughtExceptionHandler` handles uncaught exceptions from `void @Async` execution; it is not a rejection handler. If business logic requires success/failure feedback, a result-bearing completion type is usually clearer than fire-and-forget `void`.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-executor-experiments">Executable Evidence for Task Execution and @Async</a>

<details>
<summary>Click for details</summary>

The TaskExecutor surface keeps three small endpoints as executable evidence, not as production patterns or load benchmarks:

| Knowledge focus | Controller method | Endpoint | What it proves |
| --- | --- | --- | --- |
| Context + executor dispatch | `TaskExecutorController#asyncWithContext()` | `GET /spring-executor/async-context` | caller and worker differ; custom executor and decorator participate |
| `void @Async` failure | `TaskExecutorController#asyncVoidException()` | `GET /spring-executor/void-exception` | uncaught method-body failure reaches the handler; submission rejection is a separate path |
| Pool admission and saturation | `TaskExecutorController#saturation()` | `GET /spring-executor/saturation` | isolated `core → queue → grow-to-max → reject` behavior is observable without saturating the application executor |

The first experiment belongs primarily to the Context Propagation chapter because the interesting evidence is what crosses the thread boundary. The second belongs to `async-return-type` because it makes failure observation visible. The third maps to `spring-executor-config` because it makes the configured admission sequence observable.

These endpoints do not answer production sizing questions. They use small deterministic configuration so execution semantics are easy to observe. Pool tuning still requires workload tests, metrics, and downstream-capacity analysis.

</details>

- [Back to top](#back-to-top)
