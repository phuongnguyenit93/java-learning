<a id="back-to-top"></a>

# Thread/Pool Lifecycle & Leak

## Menu
- [1. Thread leak](#thread-leak)
- [2. Pool leak](#pool-leak)
- [3. Queue growth is also resource pressure](#queue-growth)
- [4. Cached pool and native thread pressure](#cached-pool-risk)
- [5. Scheduled task duplication](#scheduled-leak)
- [6. ThreadLocal and worker reuse](#thread-local-leak)
- [7. Observe leaks in production](#observability)
- [8. Ownership rule](#lifecycle-rule)
- [9. Experiments of the Leak section](#leak-experiments)

A leak in a concurrency is usually not a simple Java object leak. A resource with a longer lifecycle than the application's intended life can be held by Thread, native stack, queue, task, or context.

Rules of the learning module:

```text
unsafe pattern
→ observed within bounded range
→ cleanup right in the same experiment
```

No endpoint is created, but each call leaves an infinite live Thread/pool.

## <a id="thread-leak">1. Thread leak</a>

<details>
<summary>Click for details</summary>

Thread leaks occur when threads are created without a proper termination/lifecycle strategy and continue to live longer than the owner wants.

Dangerous examples:

```java
new Thread(() -> {
    while (true) {
        doSomething();
    }
}).start();
```

If there is no cancellation path and the owner no longer controls the reference/lifecycle.

Living Thread can hold:

- native thread resources;
- stack;
- object reachable from stack/thread-local;
- classloader/context reference;
- CPU if loop works.

### Demo bounded

References:

```text
LeakController#threadLeakPattern()
GET /leak/thread-pattern
```

Workers are deliberately held in a standby state to observe:

```text
aliveBeforeCleanup = true
```

Then Experiment Release Worker and join:

```text
aliveAfterCleanup = false
```

**Final Thoughts** The demo shows a leaked pattern without actually leaving a leaked thread.

</details>

- [Back to top](#back-to-top)

---

## <a id="pool-leak">2. Pool leak</a>

<details>
<summary>Click for details</summary>

A `ExecutorService` create Thread workers and often need an explicit shutdown if it is not managed by the framework/container.

Danger Pattern:

```java
void handleRequest() {
    ExecutorService pool = Executors.newFixedThreadPool(10);
    pool.submit(...);
    Forget Shutdown
}
```

Each request can create a new pool/worker.

References:

```text
LeakController#poolLeakPattern()
GET /leak/pool-pattern
```

The demo noted that the executor had not shut down in the middle of the experiment, then cleaned up with `shutdown/awaitTermination`.

</details>

- [Back to top](#back-to-top)

---

## <a id="queue-growth">3. Queue growth is also resource pressure</a>

<details>
<summary>Click for details</summary>

A stable thread count does not mean that the system is safe.

If the producer is faster than the consumer and the queue is not reasonably limited:

```text
arrival rate > service rate
        ↓
Increased backlog
        ↓
queued task hold object/context
        ↓
Memory + Latency Increase
```

This is why the sizing pool must look at the queue, not just the worker count.

References:

```text
LeakController#queuePressure()
GET /leak/queue-pressure
```

The demo uses a small bounded queue, deliberately fills it and then observes rejection; does not create a real unbounded backlog.

</details>

- [Back to top](#back-to-top)

---

## <a id="cached-pool-risk">4. Cached pool and native thread pressure</a>

<details>
<summary>Click for details</summary>

`newCachedThreadPool()` capable of expanding the number of platform workers when the task comes quickly and there are no free workers.

If the workload block is long and the submission rate is high, the number of native threads can increase dramatically.

Consequences may include:

- increased memory;
- increased context switching;
- scheduler overhead;
- may eventually encounter an error creating a new native Thread.

Cached pools should not be considered "self-optimal pools should always be safe".

### Platform Thread: memory and context-switching cost

Platform Thread is not free. A live Thread may include:

- native/JVM bookkeeping;
- stack reservation/commit depending on JVM/OS/configuration;
- Scheduling State;
- reachable references from the stack or ThreadLocal.

So don't reasoning type:

```text
1 Thread is quite light
→ 100,000 platform threads is also only 100,000 times "quite light"
→ is definitely fine
```

CPU cores are also finite. When the number **runnable** Platform Threads are much larger than the number of cores:

```text
Multiple runnable threads
        ↓
OS/JVM scheduler split CPU time
        ↓
execution context is passed back and forth
        ↓
Add Scheduling/context-switch overhead
+ Cache locality may deteriorate
        ↓
Throughput/tail latency can be worse
```

Context switches themselves are not bugs and are not always equally expensive. Threads that are blocking are also different from continuous runnable threads. So don't use a "N threads are too many" number for every machine/workload.

When thread pressure is suspected, look at thread count/state, CPU, scheduler/OS metrics, and JFR/profiler instead of conferring from the configuration alone `maxPoolSize`.

This is also why Virtual Thread in the following chapter focuses on reducing the cost of the thread-per-task model for proper workload blocking; it does not infinite CPU core or downstream resources.

</details>

- [Back to top](#back-to-top)

---

## <a id="scheduled-leak">5. Scheduled task duplication</a>

<details>
<summary>Click for details</summary>

Leaks/lifecycle bugs can also occur when the application accidentally creates multiple schedulers or registers the same periodic job multiple times.

Symptoms:

```text
A logical job
→ run 2, 3, 4 times per cycle
```

Must control the ownership of the scheduler and the registration lifecycle.

</details>

- [Back to top](#back-to-top)

---

## <a id="thread-local-leak">6. ThreadLocal and worker reuse</a>

<details>
<summary>Click for details</summary>

The ThreadLocal value associated with a worker can live a long time if the worker lives a long time.

Therefore:

```text
set context
→ try
→ work
→ finally remove/restore
```

is a lifecycle rule, not just a coding style.

</details>

- [Back to top](#back-to-top)

---

## <a id="observability">7. Observe leaks in production</a>

<details>
<summary>Click for details</summary>

Signs to watch:

- live thread count increases over time;
- pool count/worker count increases without going down;
- queue depth increases continuously;
- increased memory with backlog;
- CPU increase due to busy loop/context switch;
- prolonged shutdown/deploy;
- duplicate scheduled execution.

Useful Tools:

- thread dump;
- JFR/JDK tooling;
- executor metrics;
- queue depth metrics;
- application logs with thread name;
- OS/container metrics.

### Thread dump diagnostic workflows

Thread dumps are most useful when they're used to answer specific questions, not just taking a file and counting the number of lines.

An actual workflow:

```text
1. Detect an abnormal increase in live thread count
        ↓
2. Shoot multiple thread dumps at intervals
        ↓
3. Group by Thread Name/Pool Prefix
        ↓
4. View Thread.State and Stack Trace Iterations
        ↓
5. Identify the Thread/Pool Creator
        ↓
6. Reconciliation of queue depth, executor metrics, request rate
        ↓
7. Lifecycle test: shutdown/cancel/timeout/cleanup
```

A single dump is just a snapshot. Multiple snapshots help differentiate:

```text
Threads are temporarily busy
vs
Thread is kept alive for an unusually long time
```

### Read Thread.State properly

Some common patterns:

| State/pattern | Questions to ask |
| --- | --- |
| Multiple `RUNNABLE` Same Stack | Is there a busy loop, CPU hot path, or contention outside of the JVM? |
| Multiple `BLOCKED` with monitor | Who is keeping the monitor? Is the critical section too big? |
| Multiple `WAITING/TIMED_WAITING` in Executor | Is this a normal idle worker or a pool without owner/lifecycle? |
| Thread name prefix keeps increasing | Are you creating a new pool by request/job? |
| Stack keeps the same application object/context | Does ThreadLocal/task/reference have a longer lifecycle than intended? |

`WAITING` Not automatically means leak. A pool worker who lives for a long time and waits for a queue can be perfectly normal if the pool is resource-owned and has a clear lifecycle.

On the contrary, the thread count increases steadily even though the request rate returns to normal, which is a much stronger signal.

### Combining thread dumps with executor metrics

If there are executor metrics, they should be read together:

```text
poolSize
activeCount
queueSize
completedTaskCount
rejectionCount
```

For example:

```text
poolSize is stable
activeCount is always max
queueSize increases continuously
```

Often suggest saturation/backlog rather than thread leak.

While:

```text
Stable Request Rate
but the number of new pools/thread name prefixes keeps increasing.
```

Suggest an ownership/lifecycle bug in the creation of the executor.

### Practical Tooling

Depending on the environment, you can use:

- `jcmd <pid> Thread.print`;
- `jstack <pid>`;
- Java Flight Recorder / JDK Mission Control;
- Spring Boot Actuator metrics if the executor is instrumental;
- container/OS process metrics.

The ultimate goal is not to find "which thread looks strange", but to connect the thread:

```text
Symptom
→ resource
→ owner
→ missing/incorrect lifecycle rule
→ fix
```

</details>

- [Back to top](#back-to-top)

---

## <a id="lifecycle-rule">8. Ownership rule</a>

<details>
<summary>Click for details</summary>

Each resource concurrency needs to be able to answer:

```text
Who created it?
Who stopped it?
When to stop?
What if the shutdown timeout happens?
Does Task work with interruption?
```

If you can't answer the owner/lifecycle, resource leaks are very easy to appear.

</details>

- [Back to top](#back-to-top)

---

## <a id="leak-experiments">9. Experiments of the Leak section</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint |
| --- | --- | --- |
| `#thread-leak` | `LeakController#threadLeakPattern()` | `GET /leak/thread-pattern` |
| `#pool-leak` | `LeakController#poolLeakPattern()` | `GET /leak/pool-pattern` |
| `#queue-growth` | `LeakController#queuePressure()` | `GET /leak/queue-pressure` |

</details>

- [Back to top](#back-to-top)
