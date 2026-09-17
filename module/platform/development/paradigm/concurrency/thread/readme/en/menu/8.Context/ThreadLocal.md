<a id="back-to-top"></a>

# ThreadLocal & Context Propagation

## Menu
- [1. ThreadLocal isolation](#thread-local-isolation)
- [2. remove() and lifecycle of worker Thread](#thread-local-cleanup)
- [3. InheritableThreadLocal](#inheritable-thread-local)
- [4. Why Does InheritableThreadLocal Fail with Thread Pool?](#thread-pool-problem)
- [5. Context propagation: capture → restore → cleanup](#context-propagation)
- [6. Spring singleton bean and thread safety](#spring-bean-thread-safety)
- [7. Stack/Heap and context isolation](#object-memory)
- [8. When to Use ThreadLocal?](#thread-local-usage)
- [9. Context experiments](#context-experiments)

`ThreadLocal<T>` provides a way to attach a value to **Current Thread**.

Mental models should be used:

```text
Thread
→ have ThreadLocal-associated storage
→ ThreadLocal key used to access the corresponding value of that Thread
```

It is not recommended to:

```text
ThreadLocal = copy a shared variable for each Thread
```

and it is not:

```text
ThreadLocal = shared state visibility resolution tool
```

ThreadLocal Normal **Avoid sharing** a type of context, instead of synchronizing access to the same state.

## <a id="thread-local-isolation">1. ThreadLocal isolation</a>

<details>
<summary>Click for details</summary>

For example:

```java
ThreadLocal<String> requestId = new ThreadLocal<>();

requestId.set("REQ-123");
try {
    code with Thread can requestId.get()
} finally {
    requestId.remove();
}
```

Two Threads Using the Same `ThreadLocal` The object can see two different values.

References:

```text
ThreadLocalController#isolation()
GET /context/isolation
```

The experiment creates two threads, each set a different request id and then rereads its own value.

**Final Thoughts** Isolation comes from thread-local association, not from lock or visibility synchronization on a shared value.

</details>

- [Back to top](#back-to-top)

---

## <a id="thread-local-cleanup">2. remove() and lifecycle of worker Thread</a>

<details>
<summary>Click for details</summary>

Workers in a pool can live for a long time and run many tasks.

If the task does:

```java
context.set(value);
```

But without a cleanup, the following task running on the same worker may encounter the stale context or hold the reference for longer than necessary.

Safety Pattern:

```java
context.set(value);
try {
    task.run();
} finally {
    context.remove();
}
```

Here's Why Context Propagation Wrapper/Spring `TaskDecorator` Always need the Restore + Cleanup step.

</details>

- [Back to top](#back-to-top)

---

## <a id="inheritable-thread-local">3. InheritableThreadLocal</a>

<details>
<summary>Click for details</summary>

`InheritableThreadLocal` allows the child Thread to receive the initial value from the parent at the time the child Thread is created.

References:

```text
ThreadLocalController#inheritance()
GET /context/inheritance
```

Deliberate Demo:

```text
parent = A
new Thread(...)
parent = B
child.start()
```

Child still observes the initial inherited value A.

**Final Thoughts** Inheritance associated with **Thread creation**, not "every time you submit a task, copy a new context".

</details>

- [Back to top](#back-to-top)

---

## <a id="thread-pool-problem">4. Why Does InheritableThreadLocal Fail with Thread Pool?</a>

<details>
<summary>Click for details</summary>

The worker pool is created once and then reused:

```text
parent context = USER_A
submit task 1
        ↓
worker-1 is created → inherit USER_A

parent context = USER_B
submit task 2
        ↓
still old worker-1
→ no new child Thread to inherit USER_B
```

References:

```text
ThreadLocalController#poolReuseProblem()
GET /context/pool-reuse-problem
```

Expected results:

```text
task1 = USER_A
task2 = USER_A
parent = USER_B
```

This is the stale context reused by the worker.

</details>

- [Back to top](#back-to-top)

---

## <a id="context-propagation">5. Context propagation: capture → restore → cleanup</a>

<details>
<summary>Click for details</summary>

When a task is submitted to the executor, context propagation typically requires three steps:

```text
caller Thread
   ↓ capture context
Runnable/Callable wrap
   ↓
worker Thread
   ↓ Restore context
run task
   ↓ finally
cleanup / restore previous context
```

References:

```text
ThreadLocalController#explicitPropagation()
GET /context/explicit-propagation
```

Demo create the worker before the parent sets the context, then wrap the task so that the worker still receives the correct context at the time of submission.

In Spring, `TaskDecorator` is a suitable place to pack this pattern for `ThreadPoolTaskExecutor`; the Spring Task Executor section will practice live.

In addition to wrapping the task yourself or using `TaskDecorator`, the Java ecosystem also has dedicated propagation solutions. Example `TransmittableThreadLocal` of Alibaba extends the idea of capturing/replaying context to executor reuse. However, this is **library-specific solution**, without changing the core mental model:

```text
Capture context at submission boundary
→ restore before the task runs
→ cleanup/restore previous state after task
```

So this chapter prioritizes explicit propagation and Spring `TaskDecorator`; Third-party libraries should only be selected when their semantics and integrations match the real application.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-bean-thread-safety">6. Spring singleton bean and thread safety</a>

<details>
<summary>Click for details</summary>

Spring singleton only states that containers typically share a bean instance.

That doesn't automatically make a thread-safe or thread-unsafe bean.

A stateless service type:

```java
@Service
class PriceService {
    BigDecimal calculate(Order order) {
        Use only appropriate local variables and dependency thread-safe
    }
}
```

Avoid a large group of race conditions by not keeping the mutable request-specific state in the field.

But don't say "stateless bean is absolutely safe". The service can still manipulate shared database rows, mutable arguments, external resources, or non-thread-safe dependencies.

### Bean scope not Thread scope

Spring also has scopes such as singleton, request, and session, but descriptive scopes **Bean's Lifecycle/Lookup Boundary**, not a concurrency guarantee.

Mental model:

```text
singleton
→ a widely used bean instance in an application context

Request Scope
→ bean instance tied to the lifecycle of an HTTP request

Session Scope
→ bean instance tied to an HTTP session
```

It should not be transformed into:

```text
request scope = one object per Thread
session scope = one object per Thread
```

Request and Thread are two different lifecycles. Async processing can transfer execution to another Thread; the framework uses context/proxy to resolve the scoped object according to the request semantics rather than because the bean "belongs" to a worker thread.

In particular, session-scoped mutable state is not automatically thread-safe: the same user/session can have multiple concurrent requests. Bean scope does not replace synchronization or design avoids shared mutable state.

</details>

- [Back to top](#back-to-top)

---

## <a id="object-memory">7. Stack/Heap and context isolation</a>

<details>
<summary>Click for details</summary>

Java objects such as entities, DTOs, and collections are still objects on the heap.

Threads have their own stack containing frames/local references, but the fact that a reference is a local variable does not create a separate "local heap" for the object.

Thread safety depends on whether the object is shared/reachable from multiple threads and has a mutable state.

JPA persistence context/transaction isolation is its own framework/database semantics; they should not be explained by the concept of "each thread has its own heap".

</details>

- [Back to top](#back-to-top)

---

## <a id="thread-local-usage">8. When to Use ThreadLocal?</a>

<details>
<summary>Click for details</summary>

Common use cases:

- request/correlation id;
- MDC logging (MDC itself is usually thread-bound context-based);
- security/request context in the framework;
- transaction/session context managed by the framework.

ThreadLocal should not be used to turn every parameter into an implicit global state. Explicit parameters are usually easier to test and reason if the context doesn't really need thread-bound semantics.

</details>

- [Back to top](#back-to-top)

---

## <a id="context-experiments">9. Context experiments</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint |
| --- | --- | --- |
| `#thread-local-isolation` | `ThreadLocalController#isolation()` | `GET /context/isolation` |
| `#inheritable-thread-local` | `ThreadLocalController#inheritance()` | `GET /context/inheritance` |
| `#thread-pool-problem` | `ThreadLocalController#poolReuseProblem()` | `GET /context/pool-reuse-problem` |
| `#context-propagation` | `ThreadLocalController#explicitPropagation()` | `GET /context/explicit-propagation` |

</details>

- [Back to top](#back-to-top)
