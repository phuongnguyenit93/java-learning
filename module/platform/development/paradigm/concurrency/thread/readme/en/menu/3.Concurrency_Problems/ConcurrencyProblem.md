<a id="back-to-top"></a>

# Concurrency Problems & Java Memory Model

## Menu
- [1. Shared Mutable State, Atomicity, and Race Condition](#shared-state-race-condition)
- [2. Visibility and Java Memory Model](#jmm-visibility)
- [3. Happens-Before: guarantee to transmit visibility](#happens-before)
- [4. Safe Publication, final field and this escape](#safe-publication-final)
- [5. Voatile: Visibility + Ordering, not Atomicity](#volatile-publication)
- [6. Ordering and Reordering Instruction](#ordering-reordering)
- [7. Deadlock, Livelock, and Starvation](#progress-problems)
- [8. Coffman conditions and Deadlock prevention by lock ordering](#deadlock-prevention)
- [9. Synthesizing Atomicity, Visibility, Ordering and Progress](#problem-summary)
- [10. Experiments of the Concurrency Problems section](#problem-experiments)

This section answers the most important question before learning synchronization:

> Why can a piece of code that is correct when running a thread can be wrong when multiple threads are running at the same time?

Mental model throughout:

```text
Shared Mutable State
        ↓
Concurrent Access
        ↓
Atomicity / Visibility / Ordering problems
        ↓
Java Memory Model + Happens-Before
        ↓
Synchronization tools in the next chapter
```

After this section, it is necessary to distinguish:

- race condition and data race;
- atomicity and visibility;
- visibility and ordering;
- happens-before is a guarantee of the Java Memory Model, not a description of the CPU cache;
- `Volatile` resolve visibility/order but do not turn compound operations into atomic;
- Deadlock, LiveLock, and Starvation are all Liveness Problems but differ in their manifestations.

## <a id="shared-state-race-condition">1. Shared Mutable State, Atomicity, and Race Condition</a>

<details>
<summary>Click for details</summary>

### What is Shared Mutable State?

An object becomes a shared mutable state when:

```text
shared
→ multiple Threads can access the same object

mutable
→ object has a changeable state
```

For example:

```java
class Counter {
    int value;
}
```

If two Threads perform the same operation on `value`, the results may depend on how the operations alternate.

### Why is count++ not atomic?

`count++` It looks like an operation, but logically it's a read-modify-write sequence:

```text
read count
    ↓
count + 1
    ↓
write count
```

Two Threads can read the same old value and then write a new result based on that snapshot.

For example:

```text
Initial Count = 0

Thread A Read 0
Thread B reads 0

Thread A Record 1
Thread B Record 1

Sequential Expectation = 2
actual = 1
```

This is **Lost Update**.

### How is race condition different from race data?

**Race condition** is a problem of correctness: the true/false result depends on the alternating order of operations that the program has not controlled.

**Data race** Specific meaning in JMM: two Threads access the same variable, at least one access is write, and those two conflicting accesses are not ordered by happens-before.

For example:

| Code | Problem |
| --- | --- |
| Two Threads Together `count++` on the field `int` Normal, asynchronous | There is a data race and can be lost update |
| Two Threads Together `count++` on the field `Volatile int` | Read/write used to have volatile semantics, but compound increment still has race conditions |
| `get()` then `put()` separately on `ConcurrentHashMap` to increase counter | Collection protects each operation; check/read/update sequences can still race |

Therefore, **No Data Race Is Not Enough to Secure Every Atomic Business Operation**. The range of invariants to be protected should be determined.
Two endpoints `race-condition` and `volatile-not-atomic` Below helps to compare: both can be lost updates, although the Field's sync mechanism is different.

### Demo in module

Refer to the controller:

```text
ConcurrencyProblemController#raceCondition()
```

Endpoint:

```text
GET /concurrency/problem/race-condition
```

The experiment deliberately divides the increment operation into two phases:

```text
Thread A reading snapshot
Thread B reads snapshots
        ↓
Both are allowed to record
```

This coordination is only for race purposes **deterministic** for learning purposes.

Expected results:

```text
snapshotA=0
snapshotB=0
expectedSequentialResult = 2
actualResult = 1
lostUpdates = 1
```

**Final Thoughts** Race condition doesn't mean that the result is always wrong. It means that correctness depends on interleaving/timing that the code doesn't control.

</details>

- [Back to top](#back-to-top)

---

## <a id="jmm-visibility">2. Visibility and Java Memory Model</a>

<details>
<summary>Click for details</summary>

Visibility answers the question:

> If Thread A has written a value, what is the guarantee that Thread B will definitely see that value?

The Java Memory Model should not be interpreted with a simple mental model such as:

```text
Thread A writes RAM
Thread B reads RAM
```

Java does not require programmers to infer directly from the L1/L2/L3 cache or the CPU's register.

What programmers need to rely on is **Java Memory Model (JMM)**.

The JMM defines what the JVM must ensure about:

- visibility;
- ordering;
- synchronization actions;
- happens-before relationship.

If there is no consistent synchronization guarantee between the two actions, the fact that one thread is "written" in real time does not automatically guarantee that the other thread will observe the new value in the way the program intends.

### Stale data

Stale data is where one thread continues to observe the old state while another thread has updated the state.

Important Points:

```text
No stale data seen in one run
≠
thread-safe code
```

A visibility bug can depend on JVM optimization, architecture, timing, and how the program is compiled/run.

Therefore, it is not recommended to design lessons based on the objectives:

> "Run until the stale data appears to prove JMM."

The thing to learn is **Guarantee**, not hunting a random phenomenon.

</details>

- [Back to top](#back-to-top)

---

## <a id="happens-before">3. Happens-Before: guarantee to transmit visibility</a>

<details>
<summary>Click for details</summary>

Happens-before is not simply "A happens before B clockwise".

If action A happens-before action B, then the effects of A are guaranteed to be visible to B according to the Java Memory Model.

Some foundational rules:

### Program order

In a Thread, the previous action happens—before the subsequent action follows program order semantics.

### Thread start rule

Actions that occur first:

```java
thread.start();
```

happens-before Thread actions are started.

### Thread join rule

Every action in the Thread worker happens-before code continues after another Thread `join()` successful workers.

### Monitor rule

Unlock on a monitor happens-before locking that monitor later.

### Volatile rule

Write to a volatile variable happens—before a subsequent read of the volatile variable itself according to the JMM rule.

### Transitivity

If:

```text
A happens-before B
B happens-before C
```

then:

```text
A happens-before C
```

### Higher-level java.util.concurrent also carries memory guarantees.

Happens-before doesn't stop at `synchronized`, `Volatile`, `start()` and `join()`.

The abstraction in `java.util.concurrent` Build a higher-level guarantee on top of the same memory model. Some edges are very realistic:

```text
actions before Executor.execute/submit(task)
→ happen-before actions in a task when the task starts running

Actions of Asynchronous Computation
→ happen-before code after Future.get() succeeds in another thread

actions before CountDownLatch.countDown()
→ happen-before actions after await() success

actions before Semaphore.release()
→ happen-before actions after successful acquire() on the same synchronizer

Put/Access via Concurrent Collection
→ have a publication guarantee defined by the API for that element
```

This is very important about mental models: use `Executor`, `Future`, synchronizer, or concurrent collection doesn't mean the programmer leaves JMM; those APIs provide higher-level synchronization edges so that programmers don't have to build everything themselves using volatile/monitors.

### Demo start/join

References:

```text
ConcurrencyProblemController#startJoinHappensBefore()
```

Endpoint:

```text
GET /concurrency/problem/happens-before-start-join
```

Experiment with flow:

```text
request thread write beforeStart = 7
        ↓
worker.start()
        ↓
worker read beforeStart
worker writtenByWorker = 42
        ↓
worker.join()
        ↓
request thread read writtenByWorker
```

Expected results:

```text
workerObservedBeforeStart=7
parentObservedAfterJoin=42
```

**Final Thoughts** `start()` and `join()` not only coordinate the lifecycle. They also carry a memory-consistency guarantee.

</details>

- [Back to top](#back-to-top)

---

## <a id="safe-publication-final">4. Safe Publication, final field and this escape</a>

<details>
<summary>Click for details</summary>

The creation of an object in Thread A does not automatically mean that Thread B can read the object through any shared reference and receive all necessary guarantees by default.

What needs to be determined is **reference to the object published to another thread using any boundary**.

Some familiar publication boundaries have appeared in the module:

```text
Write the reference to the Volatile field
→ volatile happens-before edge

write/read under monitor or Lock
→ Synchronization Edge

Put objects in a concurrent collection using an API with a memory-consistency guarantee
→ publication under the contract of the collection

prepare the object before Thread.start()
→ start rule

prepare the object before Executor.execute/submit(...)
→ executor submission guarantee
```

### Final Field has special initialization-safety

The Java Memory Model has special rules for `FINAL` Field.

For example:

```java
final class UserSnapshot {
    private final String id;
    private final int level;

    UserSnapshot(String id, int level) {
        this.id = id;
        this.level = level;
    }
}
```

If the object is **construct properly** and reference `this` without escape before the constructor is finished, JMM provides stronger initialization-safety for the `FINAL` field assigned in the constructor.

Mental models should keep:

```text
Constructor completed properly
        ↓
final fields are frozen according to JMM
        ↓
Other threads get references to objects
        ↓
There is a special guarantee to see that the final value has been initialized
```

This is one reason immutable objects with `FINAL` Fields are very useful in concurrent design.

But it shouldn't be reduced to:

```text
Object has several final fields
→ all objects automatically thread-safe
→ every mutable field/mutation after the constructor is published safely
```

`FINAL` protect the semantics of the final field initialization; it does not replace synchronization for the mutable shared state afterwards.

### This Escape: Publish Object Too Soon

An object is **this escape** when its own reference can be observed by other threads before the constructor is complete.

Examples of design hazards:

```java
class Listener {
    private final String configuration;

    Listener(EventBus bus) {
        bus.register(this); this escaped
        this.configuration="READY";
    }
}
```

If `register(this)` making this reference reachable from another thread immediately, that thread can observe the object while the construction is not complete. This is why the constructor should complete the invariant before the object is published.

Pattern is easier to reason:

```text
Complete Construct
→ Invariant Setup
→ constructor return
→ publish object via explicit boundary
```

### Why is there no "unsafe publication" endpoint?

Similar to the reordering/stale-read demo, partially initialized observation of code with a data race is not a deterministic outcome that the endpoint can guarantee reproducibility on any JVM/machine.

Failure to observe errors in 10,000 runs does not prove that unsafe publication is correct.

So the module uses **positive guarantees** To learn:

```text
Final-Field Initialization Safety
volatile / monitor / Lock
Thread.start /join
Executor / Future / concurrent collection
```

instead of hunting a random execution of code with no guarantees.

**Final Thoughts** A good concurrency designer not only asks "Has the object been created?", but also asks **object published to another thread using which memory-consistency boundary** and **whether there is an escape before the construction is completed.**.

</details>

- [Back to top](#back-to-top)

---

## <a id="volatile-publication">5. Voatile: Visibility + Ordering, not Atomicity</a>

<details>
<summary>Click for details</summary>

`Volatile` Often used when a variable plays the role of publication/signal.

For example:

```java
int data = 0;
Boolean Ready = False;

Writer
data = 42;
ready = true;

reader
if (ready) {
    System.out.println(data);
}
```

Mental models should be used:

```text
write data = 42
        ↓ program order
write volatile ready = true
        ↓ volatile happens-before
read volatile ready == true
        ↓ program order
read data
```

Thanks to transitivity, the reader sees `ready == true` according to volatile synchronization, the writer's previous writes are also published in accordance with JMM.

No need to paraphrase with sentences:

```text
volatile = always read RAM straight
```

This is not a contract that Java programmers need to rely on.

### Demo volatile publication

References:

```text
ConcurrencyProblemController#volatilePublication()
```

Endpoint:

```text
GET /concurrency/problem/volatile-publication
```

Expected results:

```text
publishedData = 42
readerObservedData = 42
ready = true
```

**Final Thoughts** The Volatile Flag can publish the writes that occurred before it to the reader who reads that flag later.

### Volatile does not do count++ atomic

A variable can be `Volatile` But Compound Operation still races:

```java
volatile int count;

count++;
```

Read/write visibility doesn't turn the entire read-modify-write sequence into an atomic action.

References:

```text
ConcurrencyProblemController#volatileIsNotAtomic()
```

Endpoint:

```text
GET /concurrency/problem/volatile-not-atomic
```

The experiment uses the same deterministic lost-update technique as the demo race, but the field is declared. `Volatile`.

The result remains:

```text
expectedSequentialResult = 2
actualResult = 1
```

**Final Thoughts** `Volatile` solves a class of memory visibility/ordering issues; it does not replace `AtomicInteger`, `synchronized` or lock for Compound Update.

</details>

- [Back to top](#back-to-top)

---

## <a id="ordering-reordering">6. Ordering and Reordering Instruction</a>

<details>
<summary>Click for details</summary>

Compilers, JITs, and CPUs have execution optimization rights as long as the observed behavior complies with the Java Memory Model.

Therefore, programmers should not deduce:

```text
line 1 is on line 2 in source
→ Other Thread Definitely Observe Line 1 Before Line 2
```

if there is no corresponding synchronization rule between threads.

Example of a false message publication:

```java
int data = 0;
boolean ready = false;

Writer
data = 42;
ready = true;

reader
if (ready) {
    use(data);
}
```

There is no volatile/lock/other happens-before edge between writer and reader.

Such code has **Data Race**.

### Why doesn't the module create an endpoint that "definitely catches reordering"?

A stress test can sometimes observe strange outcomes and sometimes not.

But:

```text
No strange outcome can be observed
≠
code is guaranteed by JMM
```

Therefore, this chapter uses `volatile-publication` Like Positive Experiment: Instead of hunting for unwarranted behavior, we demonstrate how to create a clear guarantee.

**Final Thoughts** Multi-threaded correctness should be based on happens-before, not on the probability of "my machine is running correctly".

</details>

- [Back to top](#back-to-top)

---

## <a id="progress-problems">7. Deadlock, Livelock, and Starvation</a>

<details>
<summary>Click for details</summary>

Atomicity/visibility/ordering is mainly about **Correctness of state**.

Deadlock/livelock/starvation talks about **Progress of the system**.

### Deadlock

A deadlock occurs when threads form a dependency cycle and no thread can continue.

Classic Scenario:

```text
Thread A holds Lock A
Thread A wait for Lock B

Thread B holds Lock B
Thread B waits for Lock A
```

This is a circular wait.

A real deadlock if created directly in the HTTP demo will leave the Thread stuck for a long time, so the module does not create an intentional permanent deadlock.

Instead, use experiment bounded to simulate the correct dependency shape and then use `tryLock()` to escape.

References:

```text
ConcurrencyProblemController#deadlockRisk()
```

Endpoint:

```text
GET /concurrency/problem/deadlock-risk
```

Expected results:

```text
threadAHasFirstLock = true
threadBHasFirstLock = true
threadASecondLockAcquired = false
threadBSecondLockAcquired = false
recoveredWithoutLeak = true
```

**Final Thoughts** This endpoint demonstrates circular-lock dependency and deadlock avoidance, without pretending that the timeout is deadlock.

### Livelock

A livelock occurs when threads are still running and constantly reacting to each other but failing to make progress.

Mental model example:

```text
A sees B trying
→ A stepped back

B sees A trying
→ B stepped back

both try again at the same time
→ Repeat
```

References:

```text
ConcurrencyProblemController#boundedLivelock()
```

Endpoint:

```text
GET /concurrency/problem/livelock
```

The demo deliberately keeps two workers reacting symmetrically within a finite number of rounds and then ends.

Expected results:

```text
progressMade = false
workerABackoffs > 0
workerBBackoffs > 0
workersTerminated = true
```

It illustrates **active but no progress** without leaving an infinite loop.

### Starvation

Starvation occurs when a thread can theoretically continue but in practice continuously does not receive the required CPU/lock/resource for a very long time.

Quick Difference:

| Problem | Does Thread work? | System progress? |
| --- | --- | --- |
| Deadlock | Unable to move forward | No |
| Livelock | Yes, Continuous Reaction | No |
| Starvation | Some other Threads are still moving forward | One Thread can be starved |

Starvation depends on the scheduler/lock policy and workload, so it is not recommended to use a few lines `println` to "prove" it. Fair/unfair lock will be learned carefully in the Synchronization section.

</details>

- [Back to top](#back-to-top)

---

## <a id="deadlock-prevention">8. Coffman conditions and Deadlock prevention by lock ordering</a>

<details>
<summary>Click for details</summary>

The classic deadlock requires four conditions at the same time, often called **Coffman conditions**:

1. **Mutual exclusion**: Resource has only one owner at a time.
2. **Hold and wait**: Thread holds this resource while waiting for another resource.
3. **No preemption**: The resource is not forcibly removed from the current owner.
4. **Circular wait**: exists a waiting loop A → B → ... → A.

It is not always possible to eliminate the first three conditions. A pragmatic strategy is to break the **Circular Wait** by **Global Lock Ordering**.

For example, if the entire application agrees:

```text
everywhere needs both Lock A and Lock B
→ always acquire A first
→ then acquire B
```

then you can't write another code path to acquire `B → A`.

References:

```text
ConcurrencyProblemController#deadlockPrevention()
GET /concurrency/problem/deadlock-prevention
```

Experiment has two workers competing with two locks, but they both use the following order:

```text
A → B
```

Expected results:

```text
completedWorkers = 2
circularWaitPossibleByDesign = false
workersTerminated = true
```

**Final Thoughts** `tryLock(timeout)` is a way to escape the dangerous wait; **consistent lock ordering** is a design way to eliminate circular-wait dependency in the first place.

</details>

- [Back to top](#back-to-top)

---

## <a id="problem-summary">9. Synthesizing Atomicity, Visibility, Ordering and Progress</a>

<details>
<summary>Click for details</summary>

| Problem Groups | Questions | Examples |
| --- | --- | --- |
| Atomicity | Is an operation interrupted? | Lost Update with `count++` |
| Visibility | Are other threads guaranteed to see write? | stale flag/data |
| Ordering | What are the actions observed by other Threads? | Publication without happens-before |
| Progress | Does Thread/system continue to get work done? | Deadlock, Livelock, Starvation |

Mental Final Model:

```text
Concurrency correctness
    ├── state correctness
    │ ├── atomicity
    │ ├── visibility
    │ └── ordering
    │
    └── progress / liveness
           ├── deadlock
           ├── LiveLock
           └── Starvation
```

There is no single keyword that solves every problem.

The next chapter is just starting to learn specific tools:

```text
synchronized
Lock
Atomic
Concurrent Collection
...
```

</details>

- [Back to top](#back-to-top)

---

## <a id="problem-experiments">10. Experiments of the Concurrency Problems section</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint | Purpose |
| --- | --- | --- | --- |
| `#shared-state-race-condition` | `ConcurrencyProblemController#raceCondition()` | `GET /concurrency/problem/race-condition` | Create a deterministic lost update |
| `#happens-before` | `ConcurrencyProblemController#startJoinHappensBefore()` | `GET /concurrency/problem/happens-before-start-join` | Observe the start/join memory guarantee |
| `#volatile-publication` | `ConcurrencyProblemController#volatilePublication()` | `GET /concurrency/problem/volatile-publication` | Publish plain data via volatile flag |
| `#volatile-publication` | `ConcurrencyProblemController#volatileIsNotAtomic()` | `GET /concurrency/problem/volatile-not-atomic` | Proving volatile doesn't do compound update atomic |
| `#progress-problems` | `ConcurrencyProblemController#deadlockRisk()` | `GET /concurrency/problem/deadlock-risk` | Observing circular lock dependency but cleanup is possible |
| `#deadlock-prevention` | `ConcurrencyProblemController#deadlockPrevention()` | `GET /concurrency/problem/deadlock-prevention` | Break the Circular Wait with Global Lock Ordering |
| `#progress-problems` | `ConcurrencyProblemController#boundedLivelock()` | `GET /concurrency/problem/livelock` | Illustrating active-without-progress in a bounded way |

After this part, you need to be able to answer yourself:

1. Why `count++` Can it take an update?
2. Why doesn't race condition mean "it's wrong every time you run"?
3. What problem does JMM solve?
4. How is Happens-before different from chronological order?
5. `Volatile` What is guaranteed and what is not guaranteed?
6. Why shouldn't volatile be explained by "read/write RAM directly"?
7. Why does a stress test that doesn't catch reordering prove the code is correct?
8. What is the difference between deadlock, livelock, and starvation?
9. What are the four Coffman conditions and what does global lock ordering break?
10. How is safe publication different from just "new object finished"?
11. `FINAL` What Guarantees Does Field Offer, and Why `this` Escape in Constructor is design dangerous?

</details>

- [Back to top](#back-to-top)
