<a id="back-to-top"></a>

# Thread Coordination in Java

## Menu
- [1. Thread.join(): wait for a Thread to end](#join)
- [2. wait(), notify(), notifyAll() and condition predicate](#wait-notify)
- [3. Condition with ReentrantLock](#condition)
- [4. LockSupport: park/unpark and permit](#lock-support)
- [5. Producer–Consumer and BlockingQueue](#producer-consumer)
- [6. Select BlockingQueue by semantics](#blocking-queue-variants)
- [7. PriorityBlockingQueue: priority does not mean fairness](#priority-queue-semantics)
- [8. SynchronousQueue: direct handoff](#synchronous-queue)
- [9. Synchronizers: coordinate multiple Threads at a higher level](#synchronizers)
- [10. Semaphore permit accounting and over-release](#semaphore-permit-accounting)
- [11. Timeout and failure semantics of the Synchronizer](#synchronizer-timeout-failure)
- [12. Choose primitive by dependency](#coordination-choice)
- [13. Experiments of the Coordination section](#coordination-experiments)

Synchronization and Coordination are related but not the same problem.

```text
Synchronization
→ protect shared states from false concurrent access

Coordination
→ decide which threads to wait, when to continue, and how the phases work together.
```

## <a id="join">1. Thread.join(): wait for a Thread to end</a>

<details>
<summary>Click for details</summary>

`join()` is lifecycle coordination.

```text
Thread A start worker
        ↓
Thread A calls worker.join()
        ↓
Thread A Waiting
        ↓
TERMINATED
        ↓
Thread A continues
```

In addition to waiting for the end, `join()` there is also the happens-before guarantee learned in the JMM section.

References:

```text
CoordinationController#join()
GET /coordination/join
```

**Final Thoughts** `join()` appropriate when the dependency is "I only continue after this thread is completed".

</details>

- [Back to top](#back-to-top)

---

## <a id="wait-notify">2. wait(), notify(), notifyAll() and condition predicate</a>

<details>
<summary>Click for details</summary>

`Object.wait()` is only called when Thread is holding the corresponding monitor.

When calling `wait()`:

```text
Thread Holder Monitor
→ wait()
→ release monitor
→ to the wait set
```

`notify()` wake up a waiter; `notifyAll()` Wake up all the waiters so they compete to get the monitor back.

Three rules are easy to mistake:

1. `wait()`, `notify()` and `notifyAll()` must be operated when the caller is in possession of the corresponding monitor; otherwise, it can be received `IllegalMonitorStateException`.
2. `wait()` release monitor in the standby timeout, but `Thread.sleep()` **No release monitor** just because Thread is sleeping.
3. `notify()`/`notifyAll()` do not transfer the monitor immediately to the waiter. Waiters who are awakened still have to wait for the notifier to leave `synchronized`/release monitor and then you can reacquire and continue.

### Always wait with a predicate loop

Correct Pattern:

```java
synchronized (monitor) {
    while (!condition) {
        monitor.wait();
    }
    condition is correct
}
```

Not used `if` just to test once. Thread can wake up but the condition isn't suitable, and Java allows spurious wakeup.

References:

```text
CoordinationController#waitNotify()
GET /coordination/wait-notify
```

Experiment makes sure the waiter actually enters the wait set before the notifier updates the predicate and calls `notifyAll()`.

The demo uses three waiters to observe two steps separately:

```text
notify()
→ only one waiter is awakened to retest the predicate
→ predicate is still false, so the waiter returns to wait

ready = true + notifyAll()
→ the entire waiter is awakened
→ each Thread competes to acquire the monitor
→ all done because the predicate was true
```

**Final Thoughts** The notification is just a "check again" signal; the predicate decides whether to continue or not.

</details>

- [Back to top](#back-to-top)

---

## <a id="condition">3. Condition with ReentrantLock</a>

<details>
<summary>Click for details</summary>

`Condition` Provide Condition Queue tied with Explicit `Lock`.

```java
lock.lock();
try {
    while (!ready) {
        condition.await();
    }
} finally {
    lock.unlock();
}
```

A `ReentrantLock` can create multiple `Condition`, useful when multiple groups of waiters wait for different predicates.

Varieties `wait/notify` requires ownership of the monitor, operations such as `await()`, `signal()` and `signalAll()` must be used when the Thread is on hold **associated Lock**. Otherwise, the implementation may throw `IllegalMonitorStateException`.

`Condition.await()` There is a mental model similar to the Monitor's Condition Wait:

```text
caller holding associated lock
→ await()
→ release Lock in the meantime
→ be signaled/interrupt/timeout
→ must reacquire the Lock before await() return/throw according to the contract
```

So the following code `await()` still running in the critical section until `finally` Call `unlock()`.

References:

```text
CoordinationController#condition()
GET /coordination/condition
```

Experiment creates two independent wait-sets on the same lock:

```text
dataAvailable → wake up data waiter
shutdownRequested → wake shutdown waiter
```

After `dataAvailable.signal()`, the shutdown waiter continues to wait until the main `shutdownRequested.signal()` called.

**Final Thoughts** `Condition` is an explicit-lock version of condition waiting; a predicate loop is still required.

</details>

- [Back to top](#back-to-top)

---

## <a id="lock-support">4. LockSupport: park/unpark and permit</a>

<details>
<summary>Click for details</summary>

`LockSupport.park()` pause Threads and `unpark(thread)` grant permission to Thread by name.

Useful differences from `notify()`:

- no need to keep the intrinsic monitor;
- `unpark` accurate Thread designation;
- Permit can be issued before Thread calls `park()`.

Permit of `LockSupport` not an infinitely cumulative counter. Mental model is close to a "permitted/unpermitted" state: many times `unpark(thread)` before once `park()` do not accumulate into many parks in the future all pass by themselves.

`park()` There are also other interruption semantics APIs such as `sleep()`/`wait()`:

```text
park()
→ can return because of permit/unpark
→ can return because the Thread is interrupted
→ can spurious return

park()
→ does not throw an InterruptedException
→ caller must self-check the predicate and/or interrupt status
```

So the actual pattern is still a loop around the condition/cancellation state, not a call `park()` once and then default that the cause of the return is definitely `unpark()`.

Another nuance: permit can be granted **after the Thread has started but before the `park()` corresponding**. Don't expand that sentence to guarantee that call `unpark(thread)` on a Thread **Not started yet** will save the permit for future runs.

When building lower primitives/frameworks, overload `park(blocker)` useful for diagnostics. JVM can expose blocker objects via `LockSupport.getBlocker(thread)`, which helps thread dump/debugging indicate which abstraction Thread is parking instead of just seeing a vague park point.

References:

```text
CoordinationController#lockSupport()
GET /coordination/lock-support
```

Demo park three workers. The first two workers use a predicate loop to prove targeted `unpark`; The third worker was interrupted during park to observe interruption semantics. Main results:

```text
targetedWorkerResumed = true
otherWorkerStillParked = true
parkReturnedWhenInterrupted = true
interruptStatusObservedAfterPark = true
parkThrowsInterruptedException = false
blockerVisibleWhileParked = true
```

Then experiment `unpark(first)` and cleanup all workers before returning.

This directly proves that `unpark(thread)` target a specific thread, and interrupting is another reason to do `park()` return. Permit pre-issue (`unpark` post-start but before `Park`) are still valid semantics of `LockSupport`, but not as the sole evidence because `park()` Also allows for Spurious Return.

</details>

- [Back to top](#back-to-top)

---

## <a id="producer-consumer">5. Producer–Consumer and BlockingQueue</a>

<details>
<summary>Click for details</summary>

Producer–Consumer separates the data creation speed from the data processing speed with an intermediate buffer.

```text
Producer
   ↓ Put
BlockingQueue
   ↓ Take
Consumer
```

With bounded queues, producers can experience backpressure when the buffer is full.

Common method groups:

| Behavior | Insert | Remove |
| --- | --- | --- |
| Exception | `add` | `remove` |
| Special value | `Offer` | `Poll` |
| Block | `Put` | `take` |
| Timed wait | `offer(timeout)` | `poll(timeout)` |

References:

```text
CoordinationController#blockingQueue()
GET /coordination/blocking-queue
```

Demo produce and consume a finite number of items and then join both Threads. Don't let consumers `while(true)` exists after the request.

Operations `put()`, `take()`, `offer(timeout)` and `poll(timeout)` can react to interruption. So the producer/consumer loop still has to preserve cancellation semantics instead of swallowing `InterruptedException`.

</details>

- [Back to top](#back-to-top)

---

## <a id="blocking-queue-variants">6. Select BlockingQueue by semantics</a>

<details>
<summary>Click for details</summary>

Not every `BlockingQueue` only different implementation detail.

| Queue | Capacity / ordering | Points to remember |
| --- | --- | --- |
| `ArrayBlockingQueue` | bounded, array-backed | fixed capacity; constructor with fairness option |
| `LinkedBlockingQueue` | bounded if transmission capacity; default capacity is very large | suitable for FIFO backlog but still need to actively limit in production |
| `SynchronousQueue` | do not save elements | direct handoff between producer and consumer |
| `PriorityBlockingQueue` | priority-ordered, effectively unbounded | priority by comparator/natural order, not regular FIFO |

`PriorityBlockingQueue` does not provide backpressure by capacity as `ArrayBlockingQueue`. If producers are faster than consumers, the backlog can still increase.

Also, if two elements have the same priority, FIFO should not be relied upon as a default contract. If the business needs stable ordering, the comparator/data model must encode an explicit tie-breaker, e.g. sequence number.

`drainTo(...)` useful when it is necessary to remove a batch of elements from `BlockingQueue` without repetition `poll()` each item.

References:

```text
CoordinationController#blockingQueueVariants()
GET /coordination/blocking-queue-variants
```

Experiment shows:

```text
ArrayBlockingQueue full → offer() pays false
LinkedBlockingQueue → drainTo get finite batch
PriorityBlockingQueue → otp before delivery before promotion by priority
```

**Final Thoughts** Select queues based on capacity, ordering, and handoff semantics; do not select based on general benchmarks alone.

</details>

- [Back to top](#back-to-top)

---

## <a id="priority-queue-semantics">7. PriorityBlockingQueue: priority does not mean fairness</a>

<details>
<summary>Click for details</summary>

The priority queue returns the element according to the comparator/natural order, but has two footguns:

```text
Equal Priority
→ should not default to FIFO

High-priority item to continuously
→ low-priority item may have to wait a long time
```

This is **Business Scheduling Semantics**, not just the implementation detail.

References:

```text
CoordinationController#priorityQueueSemantics()
GET /coordination/priority-queue-semantics
```

The experiment creates a high-priority task and two tasks with the same priority. The output returns the observed order but at the same time specifies:

```text
higherPriorityComesFirst = true
equalPriorityFifoIsContract = false
boundedCapacityBackpressure = false
```

**Final Thoughts** if FIFO is required in the same priority, include the sequence in the ordering rule instead of relying on the implicit insertion order.

</details>

- [Back to top](#back-to-top)

---

## <a id="synchronous-queue">8. SynchronousQueue: direct handoff</a>

<details>
<summary>Click for details</summary>

`SynchronousQueue` Don't save the element like a regular buffer.

```text
producer put(item)
        ↕ rendezvous
consumer take()
```

Each handoff needs a corresponding opposite side operation.

References:

```text
CoordinationController#synchronousQueue()
GET /coordination/synchronous-queue
```

**Final Thoughts** This is a direct coordination point, not a queue used to store the backlog.

</details>

- [Back to top](#back-to-top)

---

## <a id="synchronizers">9. Synchronizers: coordinate multiple Threads at a higher level</a>

<details>
<summary>Click for details</summary>

### CountDownLatch

One or more threads wait counter to 0.

```text
new CountDownLatch(N)
workers countDown()
coordinator await()
```

Latch is a one-shot: the counter that has reached 0 does not reset to use a new round.

`await(timeout, unit)` Pay `false` when the timeout is up, the counter has not reached 0. Thread Timeout Waiting **No self-cancellation** the workers are running.

### CyclicBarrier

A group of Threads wait together at the barrier. When enough parties are enough, the group continues. The barrier can be reused for generations if it is not broken.

If a participant timeouts/interrupts during a wait, the barrier may go into a **broken**. Other participants can receive `BrokenBarrierException`. `reset()` Create a new generation but should only be used when the application has a clear understanding of the state of the old participants.

Barrier can also receive a `barrierAction`, which runs when the last party reaches the checkpoint before the party is released.

### Semaphore

Semaphore manages the number of permits, which is suitable for limiting concurrent access to a resource with finite capacity.

Rule cleanup:

```java
boolean obtained = false;
try {
    semaphore.acquire();
    obtained = true;
    use resource
} finally {
    if (acquired) {
        semaphore.release();
    }
}
```

None `release()` if not successfully acquired.

### Fair and non-fair Semaphore

`new Semaphore(n)` non-fair default. `new Semaphore(n, true)` Select Fairness for acquisitions to which this policy applies, in order to the internal queuing point. It does not guarantee the order `Thread.start()` or log order.

Fairness helps limit barging/starvation when disputing permits, but can reduce throughput. In particular, `tryAcquire()` **No timeout** Doesn't follow fairness: It can get the permit right even though there are other threads waiting. Don't infer the policy just from the constructor's name.

Experiment `synchronizers()` below focuses on concurrent access limits and permit returns; it's not a fairness measurement. This is a policy selection criterion, not a conclusion from the order of several output lines.

References: [Semaphore — Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Semaphore.html).

Very important points: `Semaphore` do not enforce ownership like lock. Code can be called `release()` even though Thread is not currently a Thread that has acquired permission. So unmatched `release()` This can cause the permit count to increase incorrectly and break the concurrency limit.

In addition to `acquire()`, memorable APIs include:

```text
acquire(n)
tryAcquire()
tryAcquire(timeout)
```

They help represent resources that require multiple permits or admissions with timeouts.

### Phaser

`Phaser` Suitable for multi-phase coordination and dynamic number of participants who can register/deregister.

Important operations:

```text
register()
arrive()
arriveAndAwaitAdvance()
arriveAndDeregister()
```

Others `CountDownLatch`, Phaser can progress through multiple phases, and participants can change according to the lifecycle of the workflow.

The Phase Wait APIs do not have the same interruption semantics:

```text
awaitAdvance(...)
→ wait for phase advance
→ is not an interruptible wait API

awaitAdvanceInterruptibly(...)
→ reacts to interrupts

awaitAdvanceInterruptibly(..., timeout, unit)
→ reacts to interrupt + has bounded timeout
```

So if the coordinator has to support cancellation/timeout, choose the interruptible/timed API instead of just looking at the name `await`.

### Exchanger

Two Threads meet at an exchange point and exchange objects with each other.

If the partner doesn't come, `exchange(value)` can wait. When a workflow needs bounded waiting, use timed overload:

```java
exchange(value, timeout, unit)
```

Important Semantics:

```text
Partner to
→ exchange success

Partners don't arrive before the deadline
→ TimeoutException

Thread waiting to be interrupted
→ InterruptedException
```

References:

```text
CoordinationController#synchronizers()
GET /coordination/synchronizers
```

The endpoint runs small, bounded examples and returns a result map to see the semantics of each synchronizer.

</details>

- [Back to top](#back-to-top)

---

## <a id="semaphore-permit-accounting">10. Semaphore permit accounting and over-release</a>

<details>
<summary>Click for details</summary>

References:

```text
CoordinationController#semaphorePermitAccounting()
GET /coordination/semaphore-permit-accounting
```

The experiment starts with a permit and then deliberately calls `release()` but have not yet acquired:

```text
initialPermits = 1
permitsAfterUnmatchedRelease = 2
semaphoreEnforcesOwner = false
```

After the over-release, the code can even acquire two licenses at the same time even though the initial resource only has a capacity of 1.

**Final Thoughts** With Semaphore, permit accounting is the responsibility of the application. Pattern `Boolean acquired` + `finally` Helps avoid release when acquisition fails/interrupted.

</details>

- [Back to top](#back-to-top)

---

## <a id="synchronizer-timeout-failure">11. Timeout and failure semantics of the Synchronizer</a>

<details>
<summary>Click for details</summary>

Happy-path demos aren't enough to use a safe synchronizer. It's important to understand what happens when a participant doesn't arrive on time.

References:

```text
CoordinationController#synchronizersTimeout()
GET /coordination/synchronizers-timeout
```

Endpoint illustration bounded:

```text
CountDownLatch.await(timeout)
→ false when caller timed out
→ real workers continue to run and complete normally

CyclicBarrier participant timeout
→ barrier broken
→ reset() brings barriers to a new generation
→ Next Generation Can Run Successfully with New Participants

Semaphore acquire(2) + tryAcquire(timeout)
→ timeout if there is no longer a permit

Phaser arrive /arriveAndAwaitAdvance /deregister
→ a participant leaves after phase 0
The remaining → participants continue to phase 1
→ represent dynamic registration/deregistration

Phaser coordinator
→ use awaitAdvanceInterruptibly(..., timeout, ...)
→ bounded wait and react to interruption

Exchanger without a partner
→ timed exchange throws TimeoutException

Exchanger waiting for partner
→ interrupt blocking exchange ends with InterruptedException
```

As for the demo latch, the worker is held by another latch and is only released **after the caller has observed the timeout**. Do not use the difference between the two intervals `sleep()` to assume the timeout definitely happens.

Fields to observe in `countDownLatch`:

```text
completedBeforeTimeout = false
workerContinuedAfterCallerTimeout = true
workerTerminatedNormally = true
```

If the caller is interrupted or the observation step fails, the service still releases the gate and cleanup worker in the `finally`. With Phaser, the coordinator uses timed interruptible wait; the cleanup force-terminate Phaser line so that workers don't get stuck in the phase where there are not enough participants. With Exchanger, the experiment runs both timed wait and interrupt path so that participants don't wait for an infinite partner.

**Final Thoughts** Timeout isn't just about "waiting less"; each primitive has its own failure state and recovery semantics.

</details>

- [Back to top](#back-to-top)

---

## <a id="coordination-choice">12. Choose primitive by dependency</a>

<details>
<summary>Click for details</summary>

```text
Wait for the Thread to end
→ join

Wait for the predicate on the monitor
→ wait/notifyAll

Wait for the predicate with explicit Lock
→ Condition

specific park/unpark Thread
→ LockSupport

Buffer Producer-Consumer
→ BlockingQueue

Wait for N work to be completed
→ CountDownLatch

N Thread meets at the repeat checkpoint
→ CyclicBarrier

Limit Concurrent Permits
→ Semaphore

Multi-phase workflow/dynamic parties
→ Phaser

Two Thread Exchange Objects
→ Exchanger
```

</details>

- [Back to top](#back-to-top)

---

## <a id="coordination-experiments">13. Experiments of the Coordination section</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint |
| --- | --- | --- |
| `#join` | `CoordinationController#join()` | `GET /coordination/join` |
| `#wait-notify` | `CoordinationController#waitNotify()` | `GET /coordination/wait-notify` |
| `#condition` | `CoordinationController#condition()` | `GET /coordination/condition` |
| `#lock-support` | `CoordinationController#lockSupport()` | `GET /coordination/lock-support` |
| `#producer-consumer` | `CoordinationController#blockingQueue()` | `GET /coordination/blocking-queue` |
| `#blocking-queue-variants` | `CoordinationController#blockingQueueVariants()` | `GET /coordination/blocking-queue-variants` |
| `#priority-queue-semantics` | `CoordinationController#priorityQueueSemantics()` | `GET /coordination/priority-queue-semantics` |
| `#synchronous-queue` | `CoordinationController#synchronousQueue()` | `GET /coordination/synchronous-queue` |
| `#synchronizers` | `CoordinationController#synchronizers()` | `GET /coordination/synchronizers` |
| `#semaphore-permit-accounting` | `CoordinationController#semaphorePermitAccounting()` | `GET /coordination/semaphore-permit-accounting` |
| `#synchronizer-timeout-failure` | `CoordinationController#synchronizersTimeout()` | `GET /coordination/synchronizers-timeout` |

</details>

- [Back to top](#back-to-top)
