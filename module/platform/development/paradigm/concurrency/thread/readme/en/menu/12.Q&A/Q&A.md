<a id="back-to-top"></a>

# Thread & Concurrency - Q&A / General

## Menu
- [1. Overall Knowledge Map](#mental-map)
- [2. What is the difference between Thread, Runnable, Callable, and Executor?](#thread-vs-task)
- [3. What is the difference between run() and start()?](#run-vs-start)
- [4. Does interrupt() kill Thread?](#interrupt-question)
- [5. What is the relationship between Stack, Heap and thread safety?](#stack-heap-thread-safety)
- [6. Why does local variable capture in lambda have to be final/effectively final?](#effectively-final)
- [7. How to choose volatile, atomic and synchronized?](#volatile-atomic-sync)
- [8. What is the difference between race condition and visibility bug?](#race-vs-visibility)
- [9. Does Happens-before mean that A runs before B on the clock?](#happens-before-question)
- [10. What is the difference between synchronization and coordination?](#synchronization-vs-coordination)
- [11. Why does wait() have to be in while?](#wait-notify-question)
- [12. How is BlockingQueue different from ConcurrentLinkedQueue?](#blocking-queue-question)
- [13. If the Queue of ThreadPoolExecutor is full, will the caller block it by itself?](#thread-pool-question)
- [14. Is Async Non-blocking?](#async-question)
- [15. What is the difference between Future and CompletableFuture?](#future-question)
- [16. Does ThreadLocal make thread-safe code?](#thread-local-question)
- [17. Do @Async create new Threads yourself each time?](#spring-async-question)
- [18. When is a Thread/Pool considered a leak?](#leak-question)
- [19. Will Virtual Thread replace Thread Pool?](#virtual-thread-question)
- [20. What does Schedule have to do with Thread?](#schedule-thread)
- [21. Is schedule wake-up interrupted?](#schedule-interrupt)
- [22. What is the difference between Batch and Schedule?](#batch-schedule)
- [23. Checklist before writing concurrency code](#final-checklist)
- [24. Module Conclusion](#module-conclusion)

The last part is used to re-test the mental model of the entire module, without introducing a new concurrency framework.

## <a id="mental-map">1. Overall Knowledge Map</a>

<details>
<summary>Click for details</summary>

```text
Thread lifecycle
    ↓
Interruption / Cancellation
    ↓
Shared mutable state
    ↓
Atomicity / Visibility / Ordering / JMM
    ↓
Synchronization
    ↓
Coordination
    ↓
Executor/Thread Pool
    ↓
Future / CompletableFuture
    ↓
ThreadLocal / Context propagation
    ↓
Spring TaskExecutor / @Async
    ↓
Lifecycle / Leak
    ↓
Virtual Thread
```

If a concurrency issue arises, first determine which group it belongs to instead of selecting the tool by API name.

</details>

- [Back to top](#back-to-top)

---

## <a id="thread-vs-task">2. What is the difference between Thread, Runnable, Callable, and Executor?</a>

<details>
<summary>Click for details</summary>

```text
Runnable/Callable
→ Task Description

Thread
→ an execution flow

Executor
→ abstraction determines how the task is executed

Future / CompletableFuture
→ abstraction of completion/result
```

`Callable` not a "Thread type".

</details>

- [Back to top](#back-to-top)

---

## <a id="run-vs-start">3. What is the difference between run() and start()?</a>

<details>
<summary>Click for details</summary>

```text
thread.run()
→ Normal Call Method on Caller Thread

thread.start()
→ start the Thread lifecycle
→ JVM calls run() on the new execution
```

A `Thread` The object starts only once.

</details>

- [Back to top](#back-to-top)

---

## <a id="interrupt-question">4. Does interrupt() kill Thread?</a>

<details>
<summary>Click for details</summary>

Nope.

`interrupt()` Send a Cooperative Cancellation Signal.

The running code must:

- check interrupt status; or
- are in an interruptible blocking operation; or
- propagate/restore interruption properly.

A tight loop that doesn't check for interrupts won't stop itself just because the caller calls. `interrupt()`.

</details>

- [Back to top](#back-to-top)

---

## <a id="stack-heap-thread-safety">5. What is the relationship between Stack, Heap and thread safety?</a>

<details>
<summary>Click for details</summary>

Each Thread has its own stack.

Local variables are in the stack frame, but local variables can contain references to objects on the heap.

For example:

```java
List<String> list = sharedList;
```

`List` is a local reference, but the object it points to can still be accessed by multiple threads.

So:

```text
local variable
≠ auto thread-safe
```

The question is whether the object/state has shared mutable access.

</details>

- [Back to top](#back-to-top)

---

## <a id="effectively-final">6. Why does local variable capture in lambda have to be final/effectively final?</a>

<details>
<summary>Click for details</summary>

This is first of all **Java language/closure semantics**, not because the JVM "forces the local variable to be thread-safe".

Valid examples:

```java
int taskId = i;
executor.execute(() -> use(taskId));
```

If `taskId` are not reassigned after initialization.

Lambda captures the value/reference of the local variable according to the effectively-final rule to avoid ambiguous semantics of the mutable local stack variable after the enclosing method continues/exits.

That doesn't make the captured object immutable:

```java
final List<String> list = new ArrayList<>();
```

The reference remains unchanged, but the list content is still mutable.

</details>

- [Back to top](#back-to-top)

---

## <a id="volatile-atomic-sync">7. How to choose volatile, atomic and synchronized?</a>

<details>
<summary>Click for details</summary>

### Volatile

Suitable for the visibility/order of a variable state when the update does not require an atomic invariant compound.

```text
Stop Flag
Ready Flag
Published Configuration Reference
```

### Atomic

Suitable for simple atomic state transition:

```text
counter
CAS reference update
```

CAS only checks the current expected value/reference. With a reference that can occur ABA (`A → B → A`): Plain CAS may see the final state as A and do not know that there has been a change in the middle. When the version of the state is partially correctness, consider abstraction as `AtomicStampedReference` instead of the default plain `AtomicReference` enough for all protocols.

### synchronized/Lock

Suitable when multi-step critical section or invariant protection is required.

Not used `Volatile Int Count` then expect `count++` atomic.

</details>

- [Back to top](#back-to-top)

---

## <a id="race-vs-visibility">8. What is the difference between race condition and visibility bug?</a>

<details>
<summary>Click for details</summary>

```text
Atomicity problem
→ operation is interleft
→ lost update

Visibility problem
→ Thread does not have a guarantee of seeing the write of another Thread

Ordering problem
→ cross-thread observation without matching ordering guarantee
```

The three groups can appear together, but they should not be mixed up into a "CPU cache issue" concept.

</details>

- [Back to top](#back-to-top)

---

## <a id="happens-before-question">9. Does Happens-before mean that A runs before B on the clock?</a>

<details>
<summary>Click for details</summary>

Not only that.

Happens-before is a relation of the Java Memory Model with visibility/ordering guarantee.

Important edges learned:

- program order;
- monitor unlock → later lock;
- volatile write → subsequent volatile read;
- `Thread.start()`;
- Successful `Thread.join()`;
- submission to `Executor` → task execution;
- Asynchronous computation → code after successful `Future.get()`;
- release/acquire edge of synchronizers such as latch/semaphore/lock;
- transitivity.

### What does safe publication and final field have to do with happens-before/JMM?

The `new` then an object that does not answer the question by itself that the other thread receives the reference through which memory-consistency boundary. Publication should go through a mechanism with a clear contract such as volatile, lock/monitor, concurrent collection, etc. `Thread.start()`, executor submission or boundary equivalent.

`FINAL` field has a special initialization-safety in JMM if the object is construct correctly and `this` Don't escape before the constructor is complete. That makes immutable objects with final fields easier to reason in concurrent code, but doesn't make mutable fields or mutations that occur after the constructor thread-safe.

Rules to remember:

```text
Complete Construct
→ don't let this escape anytime soon
→ publish via clear boundaries
New → share object between threads
```

</details>

- [Back to top](#back-to-top)

---

## <a id="synchronization-vs-coordination">10. What is the difference between synchronization and coordination?</a>

<details>
<summary>Click for details</summary>

```text
Synchronization
→ who can touch Shared State at what point?

Coordination
→ which thread has to wait for another event/phase/thread?
```

`synchronized` Counter protection is synchronization.

`CountDownLatch` Waiting for the two workers to complete is coordination.

A reality show usually uses both.

### Does fair lock mean all acquisitions are fair?

Nope. Example `new ReentrantLock(true)` Fair Lock configuration, but untimed `tryLock()` is still an opportunistic acquisition and can barge if the lock is available. Timed `tryLock(timeout, unit)` to join the Fair Ordering Policy when they have to wait. With `ReentrantReadWriteLock`, non-blocking `ReadLock.tryLock()` / `WriteLock.tryLock()` nor should it be considered fairness-preserving admission.

</details>

- [Back to top](#back-to-top)

---

## <a id="wait-notify-question">11. Why does wait() have to be in while?</a>

<details>
<summary>Click for details</summary>

Notification is not proof that the condition is correct.

Pattern:

```java
synchronized (monitor) {
    while (!ready) {
        monitor.wait();
    }
}
```

Help to handle:

- spurious wakeup;
- many competitive waiters;
- The state may change before the waiter reacquires the monitor.

With `LockSupport.park()` also need a condition/cancellation loop. `park()` Can return for permit, interrupt or spurious return and do not throw `InterruptedException`; The caller must check the Interrupt Status/Predicate by himself.

</details>

- [Back to top](#back-to-top)

---

## <a id="blocking-queue-question">12. How is BlockingQueue different from ConcurrentLinkedQueue?</a>

<details>
<summary>Click for details</summary>

`ConcurrentLinkedQueue` is a concurrent non-blocking queue API; `poll()` when empty pays `null`.

`BlockingQueue` have condition-waiting semantics such as `put/take` and Timed Operations.

If the Producer–Consumer needs explicit backpressure/waiting, `BlockingQueue` is often more suitable than self-busy-polling `ConcurrentLinkedQueue`.

</details>

- [Back to top](#back-to-top)

---

## <a id="thread-pool-question">13. If the Queue of ThreadPoolExecutor is full, will the caller block it by itself?</a>

<details>
<summary>Click for details</summary>

Not under the contract of `execute()`.

Flow:

```text
CORE
→ queue
→ Max Workers
→ rejection
```

Backpressure must come from a specific policy/architecture, for example `CallerRunsPolicy`, bounded admission control or upstream rate limiting.

### Are execute() and submit() the same in terms of failure observation?

Nope.

```text
execute(Runnable)
Void →
→ uncaught failure follows the worker uncaught-exception path

submit(Runnable/Callable)
→ Future
→ result/failure is held in Future
→ get() expose failure via ExecutionException
```

Submit and leave `Future` This can cause the application to lose an important failure-observation channel.

</details>

- [Back to top](#back-to-top)

---

## <a id="async-question">14. Is Async Non-blocking?</a>

<details>
<summary>Click for details</summary>

Nope.

```java
CompletableFuture.supplyAsync(() -> jdbcCall());
```

can free the caller but the worker is still blocked in JDBC.

Async talks about completion/caller relationship; non-blocking talks about how operations wait for external events/resources.

</details>

- [Back to top](#back-to-top)

---

## <a id="future-question">15. What is the difference between Future and CompletableFuture?</a>

<details>
<summary>Click for details</summary>

`Future` Mainly handle to:

- completion inspection;
- cancel;
- Blocking `get()` result.

`CompletableFuture` Add completion pipeline/composition:

- transform;
- compose;
- combined;
- race;
- error recovery.

Two nuances to remember:

```text
Future returned by ExecutorService.submit(...)
→ future.cancel(true)
→ can request interrupt running task

CompletableFuture.cancel(true)
→ mayInterruptIfRunning does not control the processing of CompletableFuture implementation
```

`allOf()` / `anyOf()` also only aggregate completion. Completion can be exceptional, and the aggregate operation does not default to a policy that automatically cancels the remaining sibling futures. Separate `allOf()` It should not be interpreted as a fail-fast barrier: an early fail does not mean that the aggregate is complete immediately when other children are not yet complete.

and `join()` non-blocking variant of `get()`: it can still wait for completion; the notable difference lies in the exception API (`CompletionException` Unchecked vs `ExecutionException` Checked's `get()`).

</details>

- [Back to top](#back-to-top)

---

## <a id="thread-local-question">16. Does ThreadLocal make thread-safe code?</a>

<details>
<summary>Click for details</summary>

ThreadLocal helps avoid sharing a context value between Threads.

It does not make other shared objects self-thread-safe and is not a replacement for lock/Atomic on the shared state.

Especially with pools, always think about worker reuse and cleanup.

A bean scope is also not a Thread scope. `@RequestScope` does not mean "one bean per worker", and the session-scoped mutable state can still be accessed by many requests with the same session accessing concurrent.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-async-question">17. Do @Async create new Threads yourself each time?</a>

<details>
<summary>Click for details</summary>

Such an inference should not be made.

`@Async` dispatch invocation via Spring async infrastructure and the executor is resolved for the method/application.

The application must have the async method execution infrastructure enabled, for example by `@EnableAsync` in the corresponding configuration. With `void @Async`, caller does not have a Future to receive failure; need `AsyncUncaughtExceptionHandler`/logging/metrics or your own channel if the failure is critical.

If used `ThreadPoolTaskExecutor`, workers are managed/reused by the pool.

If the execution strategy uses virtual-thread-per-task, the behavior is different.

Let's look at the actual executor instead of seeing annotation as the only execution mechanism.

</details>

- [Back to top](#back-to-top)

---

## <a id="leak-question">18. When is a Thread/Pool considered a leak?</a>

<details>
<summary>Click for details</summary>

When the resource lifecycle is longer than the ownership/intention of the system and is no longer properly managed.

For example:

- request to create a pool but forget to shutdown;
- Thread infinite loop without cancellation path;
- the scheduler is registered repeatedly unintentionally;
- ThreadLocal workers keep the stale context;
- The queue backlog increases infinitely.

</details>

- [Back to top](#back-to-top)

---

## <a id="virtual-thread-question">19. Will Virtual Thread replace Thread Pool?</a>

<details>
<summary>Click for details</summary>

With task concurrency, virtual-thread-per-task reduces the need for virtual pool threads to limit the number of OS workers like traditional platform-thread pools.

But the system still needs to be limited **Resource Downstream**:

- DB connections;
- API rate limit;
- memory;
- file descriptors;
- critical resource capacity.

The resource's semaphore/rate limiter/pool still plays a role.

In Java 21, a virtual thread is a thread daemon and uses `Thread.NORM_PRIORITY`; Don't use Thread Priority to control scalability. `InheritableThreadLocal` can still be inherit when the virtual thread is created, but `Thread.ofVirtual().inheritInheritableThreadLocals(false)` allow inheritance to be disabled. So explicit context propagation is still a safer mental model for request/security/logging context.

</details>

- [Back to top](#back-to-top)

---

## <a id="schedule-thread">20. What does Schedule have to do with Thread?</a>

<details>
<summary>Click for details</summary>

Scheduler Decision **When a task is triggered**; executor/thread decision **Where does the task run**.

```text
Schedule / Timer
→ trigger time
→ submit/run task
→ execution thread
```

The scheduler should not be replaced with:

```java
while (true) {
    Thread.sleep(...);
    runJob();
}
```

Because lifecycle, drift, cancellation, and exception handling are more difficult to manage.

### fixedRate and overlap

It should not be generalized that if a periodic task is not finished, the scheduler will definitely create hundreds of threads that overlap the task itself.

With `ScheduledThreadPoolExecutor`, successive executions of **Same Periodic Task** do not run simultaneously with itself.

If an execution of a periodic task to an exception exits, subsequent executions of the same periodic task are suppressed according to the scheduled executor's contract. So periodic job production needs a clear error-observation/handling policy, not just a scheduling policy.

In Spring, the actual overlap also depends on the scheduler, the number of scheduled registrations, and how the task is dispatched to another executor. The specific configuration must be analyzed.

</details>

- [Back to top](#back-to-top)

---

## <a id="schedule-interrupt">21. Is schedule wake-up interrupted?</a>

<details>
<summary>Click for details</summary>

Nope.

The scheduler waits for the deadline using an internal scheduling/condition mechanism and then brings the task to an executable state.

`interrupt()` is the cancellation/interruption signal for the Thread/task that is running or interruptible waiting.

The two concepts are related to "wake/wait" but the semantics are different.

</details>

- [Back to top](#back-to-top)

---

## <a id="batch-schedule">22. What is the difference between Batch and Schedule?</a>

<details>
<summary>Click for details</summary>

```text
Schedule
→ WHEN: when to start?

Batch
→ WHAT/HOW: how is a large workload divided and handled?
```

Batches can use Thread Pools, partitioning, queues, transactions, etc. but are not required to be triggered by a scheduler.

</details>

- [Back to top](#back-to-top)

---

## <a id="final-checklist">23. Checklist before writing concurrency code</a>

<details>
<summary>Click for details</summary>

1. Which state is actually shared?
2. Is that state mutable?
3. What atomicity, visibility, or ordering guarantee does correctness need?
4. What boundary is an object/reference published to another thread? Yes `this` Is Escape too soon?
5. Can sharing be avoided with immutability/message passing?
6. If you use a lock, what is lock owner/scope/order?
7. If Thread has to wait, which primitive coordination is suitable?
8. Does Executor have bounded capacity?
9. What is an Overload/rejection strategy?
10. Does Task have cancellation/timeout?
11. Who owns the lifecycle and shutdown resource?
12. Does ThreadLocal/context have a cleanup?
13. Does the demo/benchmark really prove what you conclude?

</details>

- [Back to top](#back-to-top)

---

## <a id="module-conclusion">24. Module Conclusion</a>

<details>
<summary>Click for details</summary>

The ultimate goal is not to memorize as many classes as possible.

The mental model to keep is:

```text
Correctness
→ JMM + synchronization

Progress
→ coordination + liveness design

Resource control
→ executor + queue + rejection + lifecycle

Result composition
→ Future / CompletableFuture

Context
→ explicit propagation + cleanup

Scalability
→ choose the right execution model, including Virtual Thread
```

Once you've properly categorized the issue, it's much easier to choose an API.

</details>

- [Back to top](#back-to-top)
