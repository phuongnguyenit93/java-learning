<a id="back-to-top"></a>

# Virtual Thread in Java 21

## Menu
- [1. Platform Thread and Virtual Thread](#virtual-thread-basic)
- [2. Create a Live Virtual Thread](#virtual-thread-creation)
- [3. Blocking does not turn into Non-blocking](#mount-unmount)
- [4. Virtual thread per task executor](#virtual-executor)
- [5. Throughput is not Latency](#throughput-latency)
- [6. Virtual Thread doesn't do infinite downstreams](#resource-limits)
- [7. ThreadLocal with Virtual Thread](#virtual-thread-thread-local)
- [8. Pinning in Java 21](#pinning-java21)
- [9. Locking with Virtual Thread](#virtual-thread-locking)
- [10. Spring Boot and Virtual Thread](#spring-virtual-thread)
- [11. ExecutorService and try-with-resources in Java 21](#executor-autoclose)
- [12. When to Consider Virtual Thread?](#virtual-thread-choice)
- [13. Virtual Thread Experiments](#virtual-thread-experiments)

Virtual Thread is lightweight `Thread` implementation is managed by the JVM to help the thread-per-task scale model better for workloads with a lot of timeout/blocking.

It does not change the learned rules of correctness:

```text
Race condition still exists
volatile/JMM still exists
Lock still needed when shared mutable state needs protection
Deadlock can still happen
```

## <a id="virtual-thread-basic">1. Platform Thread and Virtual Thread</a>

<details>
<summary>Click for details</summary>

Platform Thread is more closely tied to OS threads in the execution lifecycle.

Virtual Thread is Java `Thread` are mounted by the JVM scheduler on the carrier/platform thread when it needs to run.

Mental model:

```text
Virtual Thread
      ↓ mounted
Carrier/Platform Thread
      ↓
CPU code execution
```

When a virtual thread encounters a supported blocking operation, the runtime can unmount the virtual thread for the carrier to execute another virtual thread.

This allows for a larger number of pending tasks without the need for a separate OS thread for each task.

### Daemon and priority in Java 21

Virtual threads are still `Thread`, but don't bring the entire assumption of the platform thread to it.

In Java 21:

```text
virtual thread
→ is always Daemon Thread
→ Fixed Priority in Thread.NORM_PRIORITY
```

Since virtual threads are daemons, only virtual threads live **not enough to keep the JVM running**. This is a direct contact with the lifecycle daemon learned in the Basic section.

Priority also does not have to tune the knob to control the scheduling of the virtual thread. If you need to limit the concurrency/resource, use the appropriate abstraction such as semaphore, executor/resource pool, or rate limiter instead of trying to increase/decrease the thread priority.

References:

```text
VirtualThreadController#basic()
GET /virtual-thread/basic
```

Expected results:

```text
isVirtual = true
daemon = true
priority = 5
normalPriority = 5
stateAfterJoin=TERMINATED
```

</details>

- [Back to top](#back-to-top)

---

## <a id="virtual-thread-creation">2. Create a Live Virtual Thread</a>

<details>
<summary>Click for details</summary>

Virtual threads are not required to go through `ExecutorService`. Java 21 has two direct APIs worth knowing:

```java
Thread.startVirtualThread(task);

Thread.ofVirtual()
        .name("worker")
        .start(task);
```

Builder is useful when you need to configure the name/uncaught-exception handler before starting.

References:

```text
VirtualThreadController#creationApi()
GET /virtual-thread/creation-api
```

Experiment creates a Thread using each API and confirms:

```text
startVirtualThreadIsVirtual = true
builderIsVirtual = true
bothTerminated = true
```

**Final Thoughts** `newVirtualThreadPerTaskExecutor()` is a convenient abstraction for task submission/lifecycle; the virtual thread itself is still a `Thread` and can be created directly.

</details>

- [Back to top](#back-to-top)

---

## <a id="mount-unmount">3. Blocking does not turn into Non-blocking</a>

<details>
<summary>Click for details</summary>

A virtual thread that calls the blocking API is still **semantically blocked**.

The difference is that the JVM can free up the carrier while the virtual thread is waiting if the operation supports unmounting.

So don't say:

```text
Virtual Thread = non-blocking programming
```

Rather:

```text
Blocking Style Code
        +
cheap virtual-thread suspension
        ↓
High Concurrency with Familiar Sequential Programming Model
```

</details>

- [Back to top](#back-to-top)

---

## <a id="virtual-executor">4. Virtual thread per task executor</a>

<details>
<summary>Click for details</summary>

Java 21 provides:

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(task);
}
```

Each task is executed in a new virtual thread.

There is no need to pool virtual threads in the way of traditional platform thread pools, as virtual threads are designed to create as much and not reuse as worker pools to save OS threads.

References:

```text
VirtualThreadController#blockingScale()
GET /virtual-thread/blocking-scale
```

The demo compares a small amount of sleep-based blocking tasks:

- fixed platform pool has 10 workers;
- virtual-thread-per-task executor.

Timing is for illustrative purposes only, **Not a standard benchmark**. `sleep` used to clarify the concurrency shape, not to conclude the production performance.

</details>

- [Back to top](#back-to-top)

---

## <a id="throughput-latency">5. Throughput is not Latency</a>

<details>
<summary>Click for details</summary>

Virtual threads often help **throughput/scalability** when the system needs to keep a lot of concurrent blocking tasks.

They don't make a 500 ms database query into a 50 ms database.

```text
Latency One Operation
→ operation/downstream dependencies

concurrent capacity
→ Virtual Thread can help increase the right workload
```

</details>

- [Back to top](#back-to-top)

---

## <a id="resource-limits">6. Virtual Thread doesn't do infinite downstreams</a>

<details>
<summary>Click for details</summary>

A lot of virtual threads can be created, but database connection pools, downstream APIs, file descriptors, and rate limits are still finite.

Therefore, it usually still needs a concurrency limiter like `Semaphore`.

References:

```text
VirtualThreadController#limitedResource()
GET /virtual-thread/limited-resource
```

The demo creates multiple virtual tasks but only puts up to 5 tasks in the critical external-resource section at the same time.

Implementation of special keeping rules:

```text
Only release permit if the acquire is really successful
```

Avoid bugs that increase permits when Threads are interrupted before acquire.

</details>

- [Back to top](#back-to-top)

---

## <a id="virtual-thread-thread-local">7. ThreadLocal with Virtual Thread</a>

<details>
<summary>Click for details</summary>

Virtual threads are still available `ThreadLocal` semantics by Thread. The difference in scalability is that applications can create a much larger number of threads, so placing large objects in ThreadLocal for each virtual thread can become significant heap pressure.

The rule is the same as the Context section:

```text
Set
→ try
→ work
→ finally remove/restore
```

Don't use ThreadLocal as a global cache just because virtual threads are cheap.

References:

```text
VirtualThreadController#threadLocal()
GET /virtual-thread/thread-local
```

Plain Experiment `ThreadLocal` On two virtual threads:

```text
firstThreadContext = request-A
firstThreadCleaned = true
secondThreadInheritedPlainThreadLocal=null
plainThreadLocalIsPerThread = true
```

**Final Thoughts** Virtual Thread doesn't change ThreadLocal's isolation/cleanup rule; its large scale makes memory discipline even more important.

### InheritableThreadLocal with Virtual Thread

`InheritableThreadLocal` There are still creation-time inheritance semantics. The newly created virtual thread can receive inherited value from the thread that created it.

But the Java 21 virtual-thread builder allows this behavior to be disabled:

```java
Thread.ofVirtual()
        .inheritInheritableThreadLocals(false)
        .start(task);
```

Experiment `/thread-local` Further observation:

```text
inheritableThreadLocalDefault = parent-context
inheritableThreadLocalWhenDisabled = null
virtualBuilderCanDisableInheritance = true
```

The important point remains: inheritance does not replace explicit context propagation. Contexts with their own lifecycle/request semantics should still be captured/restored/cleaned intentionally instead of implicitly depending on which new thread is created from.

</details>

- [Back to top](#back-to-top)

---

## <a id="pinning-java21">8. Pinning in Java 21</a>

<details>
<summary>Click for details</summary>

This part must be tied to the version because the implementation virtual thread changes through the JDKs.

In **Java 21**, the virtual thread can be pinned to the carrier in some cases, notably when blocking while holding the monitor `synchronized`, or through some native/foreign operations.

Short pinning does not mean correctness error, but long/frequent blocking in the pinned state can reduce scalability because the carrier is not released.

This rule should not be made a permanent truth for any newer JDK; always check the behavior/version being deployed.

</details>

- [Back to top](#back-to-top)

---

## <a id="virtual-thread-locking">9. Locking with Virtual Thread</a>

<details>
<summary>Click for details</summary>

Virtual threads do not eliminate the need for synchronization.

If multiple virtual threads update share mutable state:

```text
100000 virtual threads
        +
unsafe count++
        ↓
still have race condition
```

`ReentrantLock`, concurrent collections, atomic classes, and designs that avoid sharing still play the same role as with platform threads.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-virtual-thread">10. Spring Boot and Virtual Thread</a>

<details>
<summary>Click for details</summary>

Spring Boot supports virtual-thread-oriented task execution when the appropriate property is enabled, for example in the Boot version it supports:

```properties
spring.threads.virtual.enabled=true
```

But it should not be interpreted as:

> turn on this property, then every Thread in the application becomes a virtual thread.

Custom executors, own libraries, custom schedulers, or code that create their own Thread platform still have their own lifecycle/execution strategy.

When you need to be sure, look at the actual executor/thread instead of inferring from the property absolutely.

Since virtual threads are daemon threads, applications with only daemon threads can exit the JVM even though there is still background work expected. With Spring Boot, when the application needs to be kept independent of non-daemon workers, it can be used `spring.main.keep-alive=true` so that Boot keeps the JVM alive. This nuance is especially noticeable with the scheduler/background component when virtual threads are enabled.

</details>

- [Back to top](#back-to-top)

---

## <a id="executor-autoclose">11. ExecutorService and try-with-resources in Java 21</a>

<details>
<summary>Click for details</summary>

In modern Java, `ExecutorService` Lifecycle support in accordance with Try-With-Resources.

For example:

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(...);
}
```

Scope lexical makes the executor's ownership clearer: the code that creates the executor is also responsible for ending its scope.

`ExecutorService` Support `AutoCloseable` from Java 19, so this syntax works with Java 21 but not with the `ExecutorService` of Java 17.

More important than the syntax is the semantics of `close()`:

```text
TRY Stem Finish
→ close(): start orderly shutdown
→ wait for the received task to be completed and the executor terminate
→ move on after the try block
```

Others `shutdown()`, `close()` There is a wait for termination. It doesn't have a default timeout to ensure a quick exit if the task gets stuck.
When the Thread is waiting in `close()` interrupted, the default implementation tries to stop the task as `shutdownNow()`, still waiting for termination and restoring interrupt status before returning. Tasks still have to cooperate with cancellation.

Contact experiment `VirtualThreadController#blockingScale()`: the time measured **After the TRY block**, should include the time it takes to complete the task, not just the time to submit. Both executors are closed before the response is returned. If the measurement is transferred to the end of the body `try` then the meaning of the measurement will be different.

References: [ExecutorService.close() — Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html#close()).

</details>

- [Back to top](#back-to-top)

---

## <a id="virtual-thread-choice">12. When to Consider Virtual Thread?</a>

<details>
<summary>Click for details</summary>

Suitable for testing when:

- a lot of concurrent requests/tasks;
- the task is mainly waiting for I/O;
- want to keep an imperative/thread-per-task style;
- Downstream capacity has been reasonably limited.

Not defaulting is the best option when:

- pure CPU-bound workload;
- the concurrency is really low;
- bottlenecks located entirely downstream with small capacity;
- the code keeps monitor/pinned blocking for a long time on the Java version you are using.

</details>

- [Back to top](#back-to-top)

---

## <a id="virtual-thread-experiments">13. Virtual Thread Experiments</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint |
| --- | --- | --- |
| `#virtual-thread-basic` | `VirtualThreadController#basic()` | `GET /virtual-thread/basic` |
| `#virtual-thread-creation` | `VirtualThreadController#creationApi()` | `GET /virtual-thread/creation-api` |
| `#virtual-executor` | `VirtualThreadController#blockingScale()` | `GET /virtual-thread/blocking-scale` |
| `#resource-limits` | `VirtualThreadController#limitedResource()` | `GET /virtual-thread/limited-resource` |
| `#virtual-thread-thread-local` | `VirtualThreadController#threadLocal()` | `GET /virtual-thread/thread-local` |

</details>

- [Back to top](#back-to-top)
