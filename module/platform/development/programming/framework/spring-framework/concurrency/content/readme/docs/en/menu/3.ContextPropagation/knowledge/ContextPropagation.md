<a id="back-to-top"></a>

# Context Propagation Across Task Boundaries

## Menu
- [Why Thread-Bound Context Gets Lost](#context-propagation-problem)
- [TaskDecorator Capture, Restore, and Cleanup](#task-decorator)
- [ContextPropagatingTaskDecorator in Spring 6.1](#context-propagating-task-decorator)
- [Security, Transaction, Request, and Reactive Context Boundaries](#context-ownership-boundaries)
- [Executable Evidence for Context Propagation](#spring-async-demo)

## <a id="context-propagation-problem">Why Thread-Bound Context Gets Lost</a>

<details>
<summary>Click for details</summary>

A thread boundary is also a context boundary. Plain `ThreadLocal` state belongs to the thread that currently holds it; submitting a `Runnable` to another thread does not automatically copy MDC values, request attributes, tenant information, trace context, or arbitrary application ThreadLocals.

```text
caller thread
  ThreadLocal = REQUEST-123
      ↓ submit
worker thread
  ThreadLocal = whatever already belongs to this worker
```

This becomes especially dangerous with pools because worker threads are reused. If a task sets context but forgets to restore or clear it, a later unrelated task can inherit stale data from the previous execution.

The design question is therefore not simply "how do I copy ThreadLocal?" It is:

1. Which context is actually required by the async task?
2. Which abstraction owns that context?
3. Can a small immutable value be passed explicitly instead?
4. If thread-bound propagation is justified, where is capture/restore/cleanup centralized?

Explicit method arguments are often the safest option for business identifiers. Task decoration is useful when infrastructure context such as logging or observation must follow many task submissions consistently.

</details>

- [Back to top](#back-to-top)

---

## <a id="task-decorator">TaskDecorator Capture, Restore, and Cleanup</a>

<details>
<summary>Click for details</summary>

`TaskDecorator` gives an executor one centralized hook to wrap the execution callback before it runs. The normal pattern is **capture on the submitting thread → restore on the execution thread → run → restore/clear previous worker state in `finally`**.

The module's `DemoTaskDecorator` deliberately propagates three kinds of evidence:

- `DemoContext`, a custom ThreadLocal used only for learning;
- SLF4J MDC for log correlation;
- Spring `RequestAttributes` from `RequestContextHolder`.

```text
caller
  capture context
      ↓
decorate(Runnable)
      ↓
worker
  save previous worker context
  install captured context
  run callback
  finally restore previous worker context
```

The `finally` restore is essential. "Set the context before running" without cleanup is a data-leak bug when threads are reused.

A decorator also is **not** a universal async exception handler. Spring may decorate an execution callback that wraps the original user task. For future-based submission, an exception can be captured into a `FutureTask`/completion handle instead of escaping directly from `Runnable.run()`. Use the failure mechanism that belongs to the submission contract: future completion, `AsyncUncaughtExceptionHandler`, rejection handling, or scheduler error handling.

### Request lifetime caveat

Propagating `RequestAttributes` does not extend the HTTP request's lifetime. A background task can outlive the request and must not assume that request-scoped objects remain valid indefinitely. When the task only needs values such as request id, tenant id, or principal id, copying those immutable values explicitly is usually safer than retaining a broad request context.

</details>

- [Back to top](#back-to-top)

---

## <a id="context-propagating-task-decorator">ContextPropagatingTaskDecorator in Spring 6.1</a>

<details>
<summary>Click for details</summary>

Spring Framework 6.1 adds `ContextPropagatingTaskDecorator` for a common infrastructure problem: capture context from the submitting thread and restore it around execution using Micrometer Context Propagation's `ContextSnapshotFactory`.

Its primary use cases are logging and observation context. Conceptually:

```text
registered context accessors
      ↓ capture snapshot
submit task
      ↓
worker thread
      ↓ restore snapshot scope
run task
      ↓ close scope / restore previous values
```

This is more systematic than hand-writing copy/cleanup logic for every executor, but it is not magic propagation of every possible ThreadLocal. What can be captured depends on the context accessors registered with the Micrometer context-propagation infrastructure.

Using this decorator also requires Micrometer Context Propagation to be available on the application classpath. The class lives in Spring Core, but the snapshot mechanism it delegates to is Micrometer infrastructure rather than a second Spring-specific context store.

There is also cost: capturing and restoring context adds work around every task. Spring's Javadoc explicitly warns that the decorator is not ideal for workloads consisting of huge numbers of extremely small tasks where the propagation overhead is material.

Use it when the application already has a context model that Micrometer Context Propagation understands and cross-thread continuity is valuable. For a domain value that is naturally a method argument, passing the value directly can still be clearer.

</details>

- [Back to top](#back-to-top)

---

## <a id="context-ownership-boundaries">Security, Transaction, Request, and Reactive Context Boundaries</a>

<details>
<summary>Click for details</summary>

Different kinds of "context" have different owners and lifetimes. A generic task decorator should not casually clone all of them.

- **Logging/observation context** is a strong fit for generic propagation because it exists to correlate execution across boundaries. `ContextPropagatingTaskDecorator` is designed for this kind of use case.
- **Security context** has dedicated Spring Security propagation support and security-specific rules. The Security curriculum owns those semantics.
- **Transactions** are normally thread-bound resource scopes. An `@Async` call moves execution to another thread; the caller's transaction should not be assumed to travel with it. Start or define transaction boundaries in the async execution according to the transaction module instead of copying transaction ThreadLocals.
- **Request context** belongs to the web request lifecycle. Copy only what remains meaningful after the caller returns.
- **Reactive context** belongs to the reactive chain, not ordinary ThreadLocal propagation. Reactor Context/backpressure semantics are owned by Reactive Programming/Spring Reactive.

A useful rule is: propagate **identity/correlation context** deliberately; recreate **resource scopes** such as transactions in their owning abstraction; pass business data explicitly. This prevents an executor decorator from becoming an unsafe second context framework.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-async-demo">Executable Evidence for Context Propagation</a>

<details>
<summary>Click for details</summary>

The module provides a focused experiment at:

```text
TaskExecutorController#asyncWithContext()
GET /spring-executor/async-context
```

The controller records the caller thread name and thread id, sets `DemoContext=REQUEST-123` and MDC `requestId=REQUEST-123`, then calls `TaskExecutorService#runAsync(...)` through another Spring bean:

```java
@Async("threadLearningTaskExecutor")
public CompletableFuture<Map<String, Object>> runAsync(
        String callerThread,
        long callerThreadId
) { ... }
```

`threadLearningTaskExecutor` is configured with `DemoTaskDecorator`. The response exposes:

- caller and worker thread names and ids;
- whether the thread ids are different;
- the custom context value observed by the worker;
- MDC request id observed by the worker;
- whether Spring `RequestAttributes` were visible on the worker.

Expected evidence includes different caller/worker thread ids plus the captured demo/MDC values. Thread names remain useful diagnostic metadata, but the identity check uses ids rather than relying on names. The decorator also restores the worker's previous state in `finally`. The controller restores the caller's demo/MDC values after submission, so the experiment itself does not leak state on the request thread.

The experiment demonstrates *mechanics*, not a recommendation to propagate full request state into long-running jobs. Production code should capture the smallest stable context required by the task.

</details>

- [Back to top](#back-to-top)
