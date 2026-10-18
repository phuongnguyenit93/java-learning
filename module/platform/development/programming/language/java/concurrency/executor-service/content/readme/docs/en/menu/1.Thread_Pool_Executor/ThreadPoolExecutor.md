<a id="back-to-top"></a>

# Thread Pools & Executors in Java

## Menu
- [1. Executor and ExecutorService](#executor-abstraction)
- [2. ThreadPoolExecutor's Processing Process](#thread-pool-flow)
- [3. corePoolSize, maximumPoolSize, queue and keepAlive](#pool-properties)
- [4. Pool sizing: heuristic is not an absolute formula](#pool-sizing)
- [5. Factories in Executors](#executors-factories)
- [6. Delayed and periodic scheduling](#scheduled-execution)
- [7. RejectedExecutionHandler and overload](#rejection-policy)
- [8. Custom RejectedExecutionHandler: overload is observable event](#custom-rejection-handler)
- [9. Lifecycle and graceful shutdown](#graceful-shutdown)
- [10. ThreadFactory and observability](#thread-factory-observability)
- [11. Thread Pool Experiments](#thread-pool-experiments)

After understanding Thread Direct, coordination and synchronization, this section moves on to abstraction management **multiple tasks on a worker Thread**.

```text
Task
→ Executor
→ worker threads + queue + lifecycle + rejection policy
```

## <a id="executor-abstraction">1. Executor and ExecutorService</a>

<details>
<summary>Click for details</summary>

`Executor` Separation of work **submit task** from the way the task is executed.

```java
executor.execute(task);
```

Caller does not need to be self-sufficient `new Thread(...)` for each task.

`ExecutorService` Additional lifecycle and result-oriented APIs such as:

- `submit()`;
- `shutdown()`;
- `shutdownNow()`;
- `awaitTermination()`.

Important Points:

```text
Runnable/Callable = work
Executor = job execution strategy
ThreadPoolExecutor = an implementation that manages the worker pool
```

### <a id="execute-vs-submit">What's the difference between execute() and submit()?</a>

Both APIs can include `Runnable` to the executor, but the completion/result channel is different:

| API | Return | Result/exception observation |
| --- | --- | --- |
| `execute(Runnable)` | `Void` | None `Future`; Exit Task Exit Exception Follow Worker's Uncaught-Exception Path |
| `submit(Runnable/Callable)` | `Future<?>` / `Future<T>` | result/exception is kept in `Future`; Caller observes when `get()` |

Here's the actual footgun:

```java
executor.submit(() -> {
    throw new IllegalStateException("boom");
});
```

if the code is always removed `Future`, the exception may not appear in the way the developer expects from `execute()`.

References:

```text
ThreadPoolController#executeVsSubmit()
GET /thread-pool/execute-vs-submit
```

Experiment Use `UncaughtExceptionHandler` Separate for observation `execute()` and use `Future.get()` to observe `submit()`:

```text
execute(task throws)
→ uncaught exception path

submit(task throws)
→ Future complete exceptionally
→ get() throws an ExecutionException with root cause
```

**Final Thoughts** If you choose `submit()` For important tasks, there must be a strategy to observe completion/failure instead of submitting and forgetting the handle.

</details>

- [Back to top](#back-to-top)

---

## <a id="thread-pool-flow">2. ThreadPoolExecutor's Processing Process</a>

<details>
<summary>Click for details</summary>

With `ThreadPoolExecutor`, when calling `execute(task)`, the core mental model is:

```text
worker count < corePoolSize ?
        ├─ yes → create a core worker to run the task
        └─ No
            ↓
        Try Enqueue Task
            ├─ Successful → Waiting Task in Queue
            └─ queue full
                ↓
        worker count < maximumPoolSize ?
            ├─ yes → create additional workers
            └─ no → rejection policy
```

This is different from the sentence:

> queue is full `execute()` Block the caller yourself until there is space.

`ThreadPoolExecutor.execute()` There is no such contract. If you want to backpressure the caller, you need to choose the appropriate policy/architecture.

### Demo flow

References:

```text
ThreadPoolController#executorFlow()
GET /thread-pool/executor-flow
```

Demo pools use small configurations:

```text
core = 1
max = 2
queue = 1
AbortPolicy
```

Tasks are retained by latch for observation in turn:

```text
Task 1 → Core Worker
Task 2 → queue
Task 3 → the 2nd worker because the queue is full
task 4 → rejected
```

**Final Thoughts** `maximumPoolSize` There is only a chance to promote when Queue no longer accepts new tasks.

</details>

- [Back to top](#back-to-top)

---

## <a id="pool-properties">3. corePoolSize, maximumPoolSize, queue and keepAlive</a>

<details>
<summary>Click for details</summary>

### corePoolSize

The number of workers that the pool tries to maintain according to the core policy. By default, core workers are usually created when there is a task, unless actively prestarted.

### maximumPoolSize

Limit the maximum number of workers when the pool needs to expand after the queue does not accept additional tasks.

### workQueue

Queue determines how the backlog is kept.

A very large queue can do `maximumPoolSize` is almost not used because the task keeps being enqueued.

A bounded queue helps limit the backlog but forces the system to be strategic when capacity runs out.

### keepAliveTime

The time the worker exceeds the core allowed to idle before being removed. If enabled `allowCoreThreadTimeOut`, the rule can be applied to the core worker as well.

By default, the core worker is retained even if idle. When calling:

```java
executor.allowCoreThreadTimeOut(true);
```

Core Workers can also be revoked later `keepAliveTime`. Trade-off is a reduction in resources when the workload is sparse, but later requests may incur the cost of recreating the worker. If the workload goes up/down continuously, the timeout is too short and can cause thread churn instead of useful savings.

### prestartAllCoreThreads

Prepare your core workers before the next task. Useful when warm-up latency is important, but in exchange for creating resources early.

</details>

- [Back to top](#back-to-top)

---

## <a id="pool-sizing">4. Pool sizing: heuristic is not an absolute formula</a>

<details>
<summary>Click for details</summary>

Type formulas:

```text
CPU-bound → Ncpu range
I/O-bound → larger than Ncpu
```

is just the starting point.

Sizing production should be based on:

- arrival rate;
- service time;
- latency SLO;
- downstream capacity;
- memory for queued task;
- contention;
- behavior when overloading.

None `queueCapacity=500` or `maxPoolSize = Ncpu*2` True for every system.

</details>

- [Back to top](#back-to-top)

---

## <a id="executors-factories">5. Factories in Executors</a>

<details>
<summary>Click for details</summary>

### newFixedThreadPool(n)

Fixed number of workers. This factory uses unbounded `LinkedBlockingQueue`, so the backlog can grow large if producers are faster than long-term consumers.

### newCachedThreadPool()

Pools can increase workers drastically and use `SynchronousQueue`. Suitable for short tasks in some workloads but need to understand the risk of creating multiple platform threads.

### newSingleThreadExecutor()

A worker, which helps serialize tasks according to the executor's queue.

The factory also uses an unbounded queue according to a small application capacity configured by the caller. If the producer submits faster than a single worker processes for a long time, the backlog/memory/latency can still increase dramatically. "One worker" does not mean "self-backpressure".

### newScheduledThreadPool(n)

Supports delayed/periodic execution.

Don't choose a factory just because of the name; in production, it is often necessary to understand the queue, worker limit, and rejection behavior.

References:

```text
ThreadPoolController#fixedPoolReuse()
GET /thread-pool/fixed-pool-reuse

ThreadPoolController#executorFactories()
GET /thread-pool/executor-factories
```

`fixed-pool-reuse` send multiple tasks but only observe up to two worker names, indicating that the worker is reused instead of creating a thread for each task.

`Executor-Factories` Run four bounded experiments to observe the Semantics represented:

```text
newFixedThreadPool(2)
→ two workers keep the task running
→ Extra Tasks Waiting for Queue

newCachedThreadPool()
→ there is no idle worker, many tasks can increase the pool of workers at the same time.

newSingleThreadExecutor()
→ tasks are serialized according to the submission order of the queue

newScheduledThreadPool(1)
→ task is run after a delay without writing a sleep-loop yourself.
```

Executors are shutdown/awaitTermination in the experiment itself so that the request does not leave the worker alive.

</details>

- [Back to top](#back-to-top)

---

## <a id="scheduled-execution">6. Delayed and periodic scheduling</a>

<details>
<summary>Click for details</summary>

`ScheduledExecutorService` Supports three different semantics to distinguish:

```text
schedule()
→ run once after a delay

scheduleAtFixedRate()
→ try to maintain the cadence according to the expected start milestone

scheduleWithFixedDelay()
→ wait for a delay after the previous run is complete before scheduling the next one
```

`scheduleAtFixedRate()` no contract allows for the same **A Periodic Task** overlaps itself. If one run lasts longer than the period, the next may be delayed; the scheduler does not clone itself to the same task to run in parallel just because of the missed cadence.

References:

```text
ThreadPoolController#scheduledExecution()
GET /thread-pool/scheduled-execution
```

The experiment runs only a few iterations and then cancels both the periodic task and the shutdown executor. The goal is to learn scheduling semantics without letting the background task live after the request.

Method runs each mode separately with `configuredIntervalMillis = 30`, the task simulation works at least about `60 ms`, and recorded the first three executions.
In `fixedRate.timeline` and `fixedDelay.timeline`, observe:

- `startMillis`, `EndMillis`: the actual time from the time of schedule preparation.
- `workMillis`: measured execution time.
- `gapAfterPreviousEndMillis`: the interval from the end of the previous time to the beginning of this time; the first time is `null`.

When a task is longer than the period, fixed-rate usually runs the next time almost immediately after the previous one is finished to process the delayed schedule. Fixed-delay still waits for the configured delay period after completion.
Scheduler/OS can cause additional latency, so don't assert an exact timing number or conclude that the fixed-rate is always faster on every run.
`noOverlapObserved` are calculated from the timelines. Since the demo uses a worker, this field only confirms non-overlapping observations; the non-overlap guarantee of the same periodic task comes from the Java API, not this benchmark.

### <a id="scheduled-failure">Exception in periodic task</a>

A very important production semantics of `ScheduledExecutorService`: if an execution of a periodic task throws an exception out, the next execution of the **Same Periodic Task** suppressed.

This is different from thinking:

```text
Run #1 Error
→ the scheduler logs itself and still runs #2, #3, #4...
```

Don't rely on that assumption.

References:

```text
ThreadPoolController#scheduledFailure()
GET /thread-pool/scheduled-failure
```

Experiment Creation `scheduleAtFixedRate()` which always throws for the first time:

```text
Run #1
→ IllegalStateException
→ ScheduledFuture complete exceptionally
→ runsObserved = 1
→ laterExecutionsSuppressed = true
```

`ScheduledFuture.get()` Allows the caller to observe the failure via `ExecutionException`, but Production Periodic Jobs usually don't have callers sitting `get()`. Therefore, periodic tasks need appropriate logging/metrics/alerting and exception boundaries; if the business wants the job to continue after a processed failure, the task must catch/handle the failure according to a clear policy.

</details>

- [Back to top](#back-to-top)

---

## <a id="rejection-policy">7. RejectedExecutionHandler and overload</a>

<details>
<summary>Click for details</summary>

Tasks are rejected when the executor is still RUNNING but no longer has the appropriate worker/queue capacity, or when the executor has shut down.

Default policies:

| Policy | Behavior |
| --- | --- |
| `AbortPolicy` | Throw `RejectedExecutionException` |
| `CallerRunsPolicy` | Caller Runs Task Manually if Executor Hasn't Shut Down |
| `DiscardPolicy` | Silent Task Drop |
| `DiscardOldestPolicy` | Remove the task at **Head of Work Queue** then try submitting a new task again |

### CallerRunsPolicy as a form of feedback/backpressure

References:

```text
ThreadPoolController#callerRuns()
GET /thread-pool/caller-runs
```

The pool is filled intentionally. The next task runs right on the caller thread.

Points to observe:

```text
fallbackThread = HTTP/caller thread
```

Callers are busy handling tasks themselves, so the submission speed is reduced. This is feedback pressure, not self-blocking queues `execute()`.

`DiscardPolicy` and `DiscardOldestPolicy` It should only be used when the semantics that lose the task are clearly understood and accepted.

### DiscardPolicy and DiscardOldestPolicy: Losing tasks is business semantics

`DiscardPolicy` Drop a new task without throwing an exception. `DiscardOldestPolicy` Call by Semantics **Head Work Queue** then try submitting a new task again.

With FIFO queue as `LinkedBlockingQueue` In this experiment, the head is also the task that has been in the queue the longest. But don't translate that into a general contract: if the work queue has other ordering, such as priority ordering, the head depends on the semantics of the queue, not necessarily the "oldest over time" task.

This means that they are not just "technical handling when the pool is full". They change **Which tasks are allowed to disappear**.

References:

```text
ThreadPoolController#discardPolicies()
GET /thread-pool/discard-policies
```

The experiment fills the pool/queue with a latch and observes:

```text
DiscardPolicy
→ new task does not run and there is no exception

DiscardOldestPolicy
→ head of LinkedBlockingQueue in the demo does not run
→ new task replaced and executed
```

**Final Thoughts** Only choose the Discard policy when the application has a clear rule on dropping data/tasks; if losing the task is unacceptable, this policy is not suitable.

</details>

- [Back to top](#back-to-top)

---

## <a id="custom-rejection-handler">8. Custom RejectedExecutionHandler: overload is observable event</a>

<details>
<summary>Click for details</summary>

Four default policies aren't the only options. `RejectedExecutionHandler` is an extension point for the application to decide on its own semantics when the admission fails.

For example, production usually wants:

- Increase metrics `executor_rejected_total`;
- log pool/queue saturation;
- broadcast alert;
- explicit reject for upstream retry/rate-limit;
- Fallback Sang Durable Queue **Only if the business contract allows and has designed idempotency/delivery semantics**.

Don't automatically "push to Kafka" in the handler just because the task is rejected; that behavior changes the delivery contract of the system.

References:

```text
ThreadPoolController#customRejectionHandler()
GET /thread-pool/custom-rejection-handler
```

Experiment filling worker + queue then custom handler:

```text
rejectionCount = 1
exceptionObserved = true
customHandlerCanRecordOverload = true
```

**Final Thoughts** Rejection should be considered an overload signal that can be observed and processed according to business semantics, not just a technical exception.

</details>

- [Back to top](#back-to-top)

---

## <a id="graceful-shutdown">9. Lifecycle and graceful shutdown</a>

<details>
<summary>Click for details</summary>

The executor generated by the code must have an owner responsible for the shutdown.

Java Core Pattern:

```java
executor.shutdown();

if (!executor.awaitTermination(timeout, unit)) {
    executor.shutdownNow();
}
```

`shutdown()`:

- do not accept new tasks;
- for the queued/running task to continue.

`shutdownNow()`:

- trying to interrupt running tasks;
- returns a task that has not yet started;
- It is impossible to force code that does not cooperate with interruption must be stopped immediately.

References:

```text
ThreadPoolController#gracefulShutdown()
GET /thread-pool/graceful-shutdown
```

The demo creates a local executor, completes the task, calls a shutdown, and confirms the pool terminated before the request ends.

### Experiment forced shutdown

References:

```text
ThreadPoolController#forcedShutdown()
GET /thread-pool/forced-shutdown
```

One worker keeps the task running in the latch; the second task is in the queue. Next `shutdown()`, workers have not been released yet, so `awaitTermination(50 ms)` Pay `false` in a controlled manner.
Then `shutdownNow()` Interrupt Running Task and Return Unstarted Task:

```text
terminatedBeforeForce = false
interruptedWorkers = 1
queuedTaskReturned = true
neverStartedCount = 1
queuedTaskRuns = 0
terminatedAfterForce = true
```

The running task stops because `await()` support interruption and worker termination after catch. This does not prove `shutdownNow()` It is possible to kill an uncooperative task. The service uses the local executor and cleans up even when the caller is interrupted.

</details>

- [Back to top](#back-to-top)

---

## <a id="thread-factory-observability">10. ThreadFactory and observability</a>

<details>
<summary>Click for details</summary>

`ThreadFactory` allows control over how worker Threads are created, especially useful for:

- thread name prefix;
- Daemon Policy when really needed;
- uncaught exception handling;
- observability/thread dump.

Names like:

```text
payment-worker-1
payment-worker-2
```

much more useful than `pool-7-thread-3` when debugging production.

</details>

- [Back to top](#back-to-top)

---

## <a id="thread-pool-experiments">11. Thread Pool Experiments</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint |
| --- | --- | --- |
| `#execute-vs-submit` | `ThreadPoolController#executeVsSubmit()` | `GET /thread-pool/execute-vs-submit` |
| `#thread-pool-flow` | `ThreadPoolController#executorFlow()` | `GET /thread-pool/executor-flow` |
| `#executors-factories` | `ThreadPoolController#fixedPoolReuse()` | `GET /thread-pool/fixed-pool-reuse` |
| `#executors-factories` | `ThreadPoolController#executorFactories()` | `GET /thread-pool/executor-factories` |
| `#scheduled-execution` | `ThreadPoolController#scheduledExecution()` | `GET /thread-pool/scheduled-execution` |
| `#scheduled-failure` | `ThreadPoolController#scheduledFailure()` | `GET /thread-pool/scheduled-failure` |
| `#rejection-policy` | `ThreadPoolController#callerRuns()` | `GET /thread-pool/caller-runs` |
| `#rejection-policy` | `ThreadPoolController#discardPolicies()` | `GET /thread-pool/discard-policies` |
| `#custom-rejection-handler` | `ThreadPoolController#customRejectionHandler()` | `GET /thread-pool/custom-rejection-handler` |
| `#graceful-shutdown` | `ThreadPoolController#gracefulShutdown()` | `GET /thread-pool/graceful-shutdown` |
| `#graceful-shutdown` | `ThreadPoolController#forcedShutdown()` | `GET /thread-pool/forced-shutdown` |

</details>

- [Back to top](#back-to-top)
