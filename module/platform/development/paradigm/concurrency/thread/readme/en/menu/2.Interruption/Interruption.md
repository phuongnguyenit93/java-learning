<a id="back-to-top"></a>

# Interruption & Cooperative Cancellation in Java

## Menu
- [1. Interrupt is a signal, not a kill order](#interrupt-mental-model)
- [2. interrupt(), isInterrupted() and Thread.interrupted()](#interrupt-flag)
- [3. InterruptedException and blocking operation](#interrupted-exception)
- [4. Cleanup when the task is canceled](#interrupt-cleanup)
- [5. interrupt() doesn't make every standby state interruptible](#interrupt-limitations)
- [6. What is the difference between interrupt() and volatile stop flag?](#interrupt-vs-stop-flag)
- [7. Code of Practice](#interrupt-rules)
- [8. Experiments of the Interruption section](#interruption-experiments)

This section learns how to **require a Thread to stop controlled.**.

The most important mental model:

```text
interrupt()
    ≠ kill thread

interrupt()
    = Send signal interruption
      Let the running code cooperate on its own
```

After this part, it is necessary to answer:

- `interrupt()` what to actually do.
- Why do busy loops have to actively check interrupt flags?
- `isInterrupted()` Others `Thread.interrupted()` where.
- Why `sleep()`, `wait()` and `join()` Throwable `InterruptedException`.
- Why interrupt flags often return `false` When `InterruptedException` are thrown.
- When to restore interrupt status.
- Why cleanup should be in `finally`.
- What states cannot be escaped by just `interrupt()`.
- `Volatile Boolean Stop` Others `interrupt()` at what point when the worker is blocking.

## <a id="interrupt-mental-model">1. Interrupt is a signal, not a kill order</a>

<details>
<summary>Click for details</summary>

Java does not design `interrupt()` as a forced order to end the Thread.

When calling:

```java
worker.interrupt();
```

The caller is sending a **Interruption Request** to `worker`.

What happens next depends on the state and code of the worker.

If the worker is running the code normally:

```text
interrupt()
    ↓
interrupt status = true
    ↓
Workers must check the signal themselves
    ↓
Worker decides cleanup and ends
```

If the worker is in an operation that supports interruption, such as `sleep()`, `wait()` or `join()`, that operation can end as soon as possible `InterruptedException`.

So the concept is correct:

```text
Cooperative Cancellation

caller
  → Request a Stop

worker
  → observation requirements
  → cleanup
  → Safe End
```

### Why not use Thread.stop()?

`Thread.stop()` is a deprecated API because it can stop the thread at an unsafe time and break the invariant of the shared state.

Modern code should design tasks that are able to react to cancellations instead of forcibly killing threads.

### Demo in module

References:

```text
InterruptionController#interruptBusyWorker()
```

Endpoint:

```text
GET /interruption/busy-loop
```

Experiment creates a worker running the CPU loop:

```java
while (! Thread.currentThread().isInterrupted()) {
    work
}
```

The controller calls service, service start worker and then sends `interrupt()`.

When running, it is necessary to observe:

```text
Worker Start
→ interrupt is sent
→ Worker Observation Interrupt Flag
→ End Loop
→ Thread TERMINATED
```

**Final Thoughts** with non-block code in the interruptible API, `interrupt()` only set the signal; the worker must actively check that signal.

</details>

- [Back to top](#back-to-top)

---

## <a id="interrupt-flag">2. interrupt(), isInterrupted() and Thread.interrupted()</a>

<details>
<summary>Click for details</summary>

These three APIs have different roles.

| API | Type | Which Thread to check? | Is there a clear flag? |
| --- | --- | --- | --- |
| `thread.interrupt()` | instance | target thread | send interruption request |
| `thread.isInterrupted()` | instance | thread called | No |
| `Thread.interrupted()` | static | current thread | Yes |

The easiest point to be mistaken for is `Thread.interrupted()`.

Example worker has interrupt status = `true`:

```java
Thread.currentThread().isInterrupted();
true

Thread.currentThread().isInterrupted();
Still true

Thread.interrupted();
Pay true and clear status

Thread.interrupted();
false
```

Mental model:

```text
isInterrupted()
→ observe
→ don't consume signals

Thread.interrupted()
→ observe current thread
→ consume/clear signal
```

Do not clear the interrupt status arbitrarily. Clearing the signal may cause the code on the upper floor to no longer know that the cancellation has been requested.

### Demo in module

References:

```text
InterruptionController#inspectInterruptFlag()
```

Endpoint:

```text
GET /interruption/flag
```

The worker is interrupted while spinning, then takes turns calling:

```text
isInterrupted()
isInterrupted()
Thread.interrupted()
Thread.interrupted()
isInterrupted()
```

Results to observe:

```text
true
true
true
false
false
```

**Final Thoughts** `isInterrupted()` only reads flags; `Thread.interrupted()` Read the Clear Interrupt status of the current thread.

</details>

- [Back to top](#back-to-top)

---

## <a id="interrupted-exception">3. InterruptedException and blocking operation</a>

<details>
<summary>Click for details</summary>

Some operations support direct interruption, for example:

- `Thread.sleep(...)`;
- `Object.wait(...)`;
- `Thread.join(...)`;
- multiple API blocking in `java.util.concurrent`.

For example:

```java
try {
    Thread.sleep(30_000);
} catch (InterruptedException e) {
    Thread received interruption request
}
```

When `InterruptedException` thrown by APIs of this type, interrupt status is often **clear**.

Therefore in `catch`:

```java
Thread.currentThread().isInterrupted()
```

usually returned `false`.

If the method is not possible `throw InterruptedException` Next, it is necessary to preserve the cancellation signal for the code above, the common pattern is:

```java
catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
```

This is called **Restore Interrupt Status**.

Not every `catch (InterruptedException)` are required to restore. If the method propagate the exception goes out, the caller has received a cancellation signal via the exception. Restore is especially useful when the exception is caught again and can't/reasonably shouldn't be thrown again.

### Demo in module

References:

```text
InterruptionController#interruptSleepingWorker()
```

Endpoint:

```text
GET /interruption/sleep
```

Worker Starts `sleep()` long, then service call `interrupt()`.

The response should be expressed:

```text
InterruptedException occurs
flag in catch before restore = false
flag after Thread.currentThread().interrupt() = true
Worker Ends
```

**Final Thoughts** Interruption of the blocking operation usually goes through `InterruptedException`; This exception and the interrupt flag are two ways to represent the same cancellation signal at different times.

</details>

- [Back to top](#back-to-top)

---

## <a id="interrupt-cleanup">4. Cleanup when the task is canceled</a>

<details>
<summary>Click for details</summary>

Cancellation doesn't just mean exiting the loop.

Tasks may hold:

- file;
- socket;
- temporary resource;
- transaction/resource scope;
- state to be reset.

So a safe lifecycle usually takes the form of:

```java
try {
    while (! Thread.currentThread().isInterrupted()) {
        work
    }
} finally {
    Cleanup
}
```

Or with blocking operation:

```java
try {
    doBlockingWork();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
} finally {
    cleanup();
}
```

`finally` It's important because cleanup shouldn't depend on workers ending up on the "happy path" or cancellation path.

### Demo in module

References:

```text
InterruptionController#cancelWithCleanup()
```

Endpoint:

```text
GET /interruption/cleanup
```

The experiment simulates a resource that is opened before the worker starts working.

After Sending `interrupt()`, observe:

```text
interruptionObserved = true
cleanupCall=true
resourceOpen = false
workerAlive = false
```

Workers can receive interrupts before the loop condition check or during the `sleep()`.
In the first case, it exits the loop without passing through `catch`; in the latter case, `sleep()` Throw `InterruptedException`.
`interruptionObserved` Record the cancellation signal on both paths, not using whether there is an exception or not to conclude that the Thread has received an interrupt.

**Final Thoughts** The cancellation must not only stop the execution, but also return the resource to a valid state.

</details>

- [Back to top](#back-to-top)

---

## <a id="interrupt-limitations">5. interrupt() doesn't make every standby state interruptible</a>

<details>
<summary>Click for details</summary>

Don't understand:

```text
interrupt()
→ all blocking operations are immediately broken
```

An important example is a thread waiting for your intrinsic monitor `synchronized`.

```java
synchronized (monitor) {
    Other threads are holding monitors
}
```

If the worker is in `Thread.State.BLOCKED` because waiting for this monitor, call `interrupt()`:

- set interrupt status;
- **None** make workers get rid of waiting for monitors;
- Workers still have to wait for the monitor to be released.

Once the monitor is obtained, the code can check the interrupt status and handle the cancellation.

If lock acquisition is needed to react to interruption, the Synchronization chapter will introduce `ReentrantLock.lockInterruptibly()`.

Blocking I/O should also not be grouped into a general rule. Interruption capabilities depend on the specific API; some NIO channels have their own semantics interruption while many other blocking APIs require a different resource closure or cancellation mechanism.

### Demo in module

References:

```text
InterruptionController#interruptSynchronizedWaiter()
```

Endpoint:

```text
GET /interruption/synchronized-blocked
```

The service keeps a monitor, starts the worker so that the worker falls into `BLOCKED`, then call `interrupt()` when the worker still hasn't gotten the monitor.

Points to observe:

```text
state before interrupt = BLOCKED
state immediately after interrupt = BLOCKED
interrupt flag = true
```

After the service release monitor:

```text
Worker gets monitor
→ still observe interrupt flag = true
→ worker ends
```

**Final Thoughts** interrupt signals can exist while Threads continue to suffer from `BLOCKED`; the ability to "wake up" depends on the operation Thread is waiting for.

</details>

- [Back to top](#back-to-top)

---

## <a id="interrupt-vs-stop-flag">6. What is the difference between interrupt() and volatile stop flag?</a>

<details>
<summary>Click for details</summary>

A custom stop flag is still a valid cooperative cancellation technique in several loops:

```java
while (!stopRequested) {
    doWork();
}
```

If `stopRequested` shared between multiple Threads, visibility must be ensured, e.g. by `Volatile` or a primitive equivalent.

The key differences are **Stop flag is just the application state**. It doesn't integrate with Java's interruption protocol and doesn't automatically wake up a thread that's in an interruptible operation like `sleep()`, `wait()` or `join()`.

For example:

```java
volatile boolean stopRequested;

while (!stopRequested) {
    Thread.sleep(30_000);
}
```

If the Thread is in the middle `sleep(30_000)` and other Threads just do:

```java
stopRequested = true;
```

workers haven't had a chance to re-read the flag until `sleep()` natural finish.

While:

```java
worker.interrupt();
```

can do `sleep()` Ending early with `InterruptedException`.

Mental Model Comparison:

| Mechanism | Visibility signal | Wake-up `sleep/wait/join` | Have interrupt status | Integrate with multiple concurrency APIs |
| --- | --- | --- | --- | --- |
| `Volatile Boolean Stop` | Yes | No | No | No |
| `interrupt()` | Yes interruption signal | Yes with API support interruption | Yes | Yes |

This does not mean that it must be used all the time. `interrupt()` instead of every flag. A state flag can better represent the business lifecycle, for example: `RUNNING`, `PAUSED`, `STOPPING`. But if the task can be blocked and needs responsive cancellation, interruption is usually the more suitable protocol.

### Demo in module

References:

```text
InterruptionController#stopFlagVsInterrupt()
GET /interruption/stop-flag-vs-interrupt
```

Experiment for workers to enter `sleep(30s)`, then:

```text
set volatile stop flag = true
→ worker is still alive
→ interrupt worker
→ sleep throw InterruptedException
→ worker ends
```

Critical Response:

```text
stopFlagVisible = true
aliveAfterOnlyStopFlag = true
stateAfterOnlyStopFlag = TIMED_WAITING
interruptWokeBlockingOperation = true
workerAliveAfterCleanup = false
```

**Final Thoughts** Stop Flag helps workers know *should stop*, and `interrupt()` In addition to carrying cancellation signals, there is also the ability to make many API blocks end early so that workers have the opportunity to react immediately.

</details>

- [Back to top](#back-to-top)

---

## <a id="interrupt-rules">7. Code of Practice</a>

<details>
<summary>Click for details</summary>

### No swallow InterruptedException

The code is as follows to lose the cancellation signal:

```java
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    Skip
}
```

Instead, usually choose one of two directions:

```text
1. propagate InterruptedException

or

2. Restore Interrupt Status and End/Return
```

### CPU-bound tasks must have a cancellation point

If the task runs a long loop without calling the interruptible API, it is necessary to actively check:

```java
Thread.currentThread().isInterrupted()
```

It is not necessary to check after each instruction; the cancellation point should be placed in a reasonable position to balance responsiveness and testing costs.

### Cleanup must be deterministic

Normal learning demos must not leave threads alive after the endpoint is complete.

Service in this chapter is always `join()` worker with timeout and have a cleanup fallback if needed.

### Do not mix Thread Pool shutdown into the basic mental model

`ExecutorService.shutdown()`, `shutdownNow()`, `awaitTermination()` and the Spring executor lifecycle is associated with interruption but belongs to higher abstraction.

They'll be learned in the Thread Pool/Executor and Lifecycle sections instead of being used to explain basic interrupts here.

</details>

- [Back to top](#back-to-top)

---

## <a id="interruption-experiments">8. Experiments of the Interruption section</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint | Purpose |
| --- | --- | --- | --- |
| `#interrupt-mental-model` | `InterruptionController#interruptBusyWorker()` | `GET /interruption/busy-loop` | Proof of cooperative cancellation of CPU loop |
| `#interrupt-flag` | `InterruptionController#inspectInterruptFlag()` | `GET /interruption/flag` | Differentiation `isInterrupted()` and `Thread.interrupted()` |
| `#interrupted-exception` | `InterruptionController#interruptSleepingWorker()` | `GET /interruption/sleep` | Observation `InterruptedException`, flag clear and restore |
| `#interrupt-cleanup` | `InterruptionController#cancelWithCleanup()` | `GET /interruption/cleanup` | Proof of cleanup in the cancellation path |
| `#interrupt-limitations` | `InterruptionController#interruptSynchronizedWaiter()` | `GET /interruption/synchronized-blocked` | Proof `synchronized` Non-Interruptible Monitor Acquisition |
| `#interrupt-vs-stop-flag` | `InterruptionController#stopFlagVsInterrupt()` | `GET /interruption/stop-flag-vs-interrupt` | Comparing stop flags to interruptions when workers are blocking |

After this part, you need to be able to answer yourself:

1. Why `interrupt()` is not synonymous with killing Thread?
2. What does a busy loop have to do in response to a cancellation?
3. `isInterrupted()` and `Thread.interrupted()` Where is the difference?
4. Why an interrupt flag can be `false` Inside `catch (InterruptedException)`?
5. When should I restore interrupt status?
6. Why cleanup should be in `finally`?
7. Why a Thread is `BLOCKED` standby `synchronized` still doesn't escape immediately after `interrupt()`?
8. Why `Volatile Boolean Stop = True` can't wake up a Thread by itself `sleep()`?

</details>

- [Back to top](#back-to-top)
