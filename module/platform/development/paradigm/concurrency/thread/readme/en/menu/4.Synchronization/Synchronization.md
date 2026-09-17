<a id="back-to-top"></a>

# Synchronization in Java

## Menu
- [1. Synchronized and intrinsic monitor](#synchronized-monitor)
- [2. ReentrantLock: explicit locking](#reentrant-lock)
- [3. ReadWriteLock](#read-write-lock)
- [4. ReadWriteLock fairness, starvation, and when to use it](#read-write-lock-policy)
- [5. StampedLock and optimistic read](#stamped-lock)
- [6. StampedLock is not ReadWriteLock faster](#stamped-lock-limitations)
- [7. Atomic variables and CAS](#atomic-cas)
- [8. LongAdder and LongAccumulator](#adder-accumulator)
- [9. Concurrent Collections](#concurrent-collections)
- [10. Which tool to choose?](#synchronization-choice)
- [11. Experiments of the Synchronization Section](#synchronization-experiments)

The previous section learned the problems to be solved: atomicity, visibility, ordering, and progress.

This section focuses on tools used to protect shared mutable states.

Mental model:

```text
shared mutable state
        ↓
Choosing the Right Synchronization Strategy
        ↓
synchronized / Lock / Atomic / Concurrent Collection
```

There is no one tool that is always best for every situation.

## <a id="synchronized-monitor">1. Synchronized and intrinsic monitor</a>

<details>
<summary>Click for details</summary>

Each Java object can be used as a monitor for `synchronized`.

```java
synchronized (lock) {
    Critical Section
}
```

When one thread is holding the monitor, another thread that wants to enter the critical section using the same monitor must wait.

`synchronized` Offer two important things:

- mutual exclusion: at a time only thread keeping monitor is running critical section;
- memory-consistency guarantee when released/acquired with monitor.

### Method and block

```java
public synchronized void update() {
}
```

Use your monitor `this`.

`Static synchronized` Use your own monitor `Class` object:

```java
public static synchronized void updateGlobal() {
}
```

Therefore, it is necessary to distinguish:

```text
instance synchronized method
→ lock this

Static synchronized method
→ lock MyClass.class
```

Two different instances don't block each other just because they call the same instance `synchronized` method; they only compete if they actually use the same monitor.

While:

```java
private final Object lock = new Object();

public void update() {
    synchronized (lock) {
    }
}
```

Allows selecting a separate monitor and keeping the lock scope smaller.

### Demo

References:

```text
SynchronizationController#synchronizedCounter()
GET /synchronization/synchronized-counter
```

Many workers increment the same counter but the increment method is `synchronized`.

Expected results:

```text
expected = actual
```

**Final Thoughts** `synchronized` Critical sections can be turned into mutual exclusion zones and Happens-Before can be created through Monitor Acquire/Release.

</details>

- [Back to top](#back-to-top)

---

## <a id="reentrant-lock">2. ReentrantLock: explicit locking</a>

<details>
<summary>Click for details</summary>

`ReentrantLock` provides the same mutual exclusion objectives but adds control capabilities that `synchronized` No Direct:

- `tryLock()`;
- `tryLock(timeout, unit)`;
- `lockInterruptibly()`;
- fairness option;
- Many `Condition` on the same lock.

Important Rule:

```java
lock.lock();
try {
    Critical Section
} finally {
    lock.unlock();
}
```

If you forget `unlock()`, the lock can be held indefinitely.

### Why is it called Reentrant?

Reentrant means **The thread holding the lock can acquire the lock itself** without deadlocking itself.

`ReentrantLock` Track Hold Count:

```text
lock() 1st time → holdCount = 1
lock() 2nd → holdCount = 2
unlock() → holdCount = 1
unlock() → holdCount = 0
```

Intrinsic monitor of `synchronized` also reentrant.

References:

```text
SynchronizationController#reentrantLock()
GET /synchronization/reentrant-lock
```

**Final Thoughts** Reentrancy allows a call chain on the same thread to go through multiple methods and acquire a lock without locking itself; but the number of releases still has to match the number of acquires.

### tryLock demo

References:

```text
SynchronizationController#tryLock()
GET /synchronization/try-lock
```

Worker A holds the lock. Worker B only waits for a finite amount equal to `tryLock(timeout)`.

Points to observe:

```text
holderAcquired = true
contenderAcquiredWhileHeld = false
workersTerminated = true
```

**Final Thoughts** `tryLock` allows for timeout/fallback construction instead of waiting for an infinite lock.

### lockInterruptibly demo

References:

```text
SynchronizationController#interruptibleLock()
GET /synchronization/interruptible-lock
```

A worker waiting for the lock `lockInterruptibly()` are sent interrupt.

Expected results:

```text
waiterInterrupted = true
waiterAcquired = false
```

**Final Thoughts** Explicit Lock can support cancellation while waiting for the lock.

### Fair and unfair lock

`new ReentrantLock(true)` Fairness Policy requirements are stronger than the default.

Fairness should not be judged by the order of a few log lines. Scheduler and when the Thread actually calls `lock()` May be different order `start()`.

Fair lock often exchanges throughput for the ability to limit barging/starvation. Benchmarking is required according to real workload if this option is important.

Don't use a benchmark of a few hundred or a few thousand threads and then conclude absolutely that:

```text
Fair = Never Starvation
unfair = definitely starvation
```

Fairness is **Acquisition Policy** of the lock, not the promise of order `Thread.start()`, the order in which the scheduler runs the Thread or the order in which the log appears.

An important footgun: with fair `ReentrantLock`, `tryLock()` **No timeout** It is not required to follow the fairness policy. It can be acquired immediately if the lock is available even though another thread has been waiting longer. In contrast, timed `tryLock(timeout, unit)` Honor Fairness Policy of Fair Lock when having to wait in line.

Mental model:

```text
new ReentrantLock(true)
→ lock()/lockInterruptibly()/timed tryLock() using the appropriate fair admission policy

untimed tryLock()
→ opportunistic immediate acquisition
→ can barge
```

So don't use untimed `tryLock()` and then deduce that all fair lock acquisitions are FIFO-like.

### Fair/unfair policy demo

References:

```text
SynchronizationController#reentrantLockPolicy()
GET /synchronization/reentrant-lock-policy
```

Experiment deliberately does not benchmark timing. It only observes the configuration contract:

```text
defaultIsFair = false
explicitUnfairIsFair = false
explicitFairIsFair = true
untimedTryLockHonorsFairness = false
timedTryLockHonorsFairness = true
fairnessGuaranteesThreadStartOrder = false
fairnessEliminatesSchedulingVariance = false
fairnessHasPotentialThroughputCost = true
```

**Final Thoughts** use fairness when semantics about reducing barging/starvation is worth more than throughput cost; if performance is important, benchmark the actual workload, not inferred from a few endpoint runs.

</details>

- [Back to top](#back-to-top)

---

## <a id="read-write-lock">3. ReadWriteLock</a>

<details>
<summary>Click for details</summary>

`ReentrantReadWriteLock` Split access into:

```text
Read Lock
→ multiple readers can hold the same

Write Lock
→ exclusive
```

Basic Rules:

| A is holding | B wants | Result |
| --- | --- | --- |
| Read | Read | Can run together |
| Read | Write | Write must wait |
| Write | Read | Read must wait |
| Write | Write | Write must wait |

References:

```text
SynchronizationController#readWriteLock()
GET /synchronization/read-write-lock
```

Experiment for two readers to hold the read lock together and then start writer.

Points to observe:

```text
maxConcurrentReaders >= 2
writerAcquiredWhileReadersHeld = false
writerEventuallyAcquired = true
```

### Lock downgrading

`ReentrantReadWriteLock` Support **Downgrade** From Write Lock to Read Lock by Flow:

```text
hold write lock
→ acquire read lock
→ release write lock
→ continue to keep read lock
```

In the opposite direction **Not supported as a lock upgrade**. A Thread that holds a read lock cannot rely on acquiring a write lock to "upgrade" the right to hold the lock while keeping the read lock.

Mental models to remember:

```text
write → read
→ supported downgrade

read → write
→ upgrade is not supported
→ have to redesign the flow, release read lock, and then compete to write lock according to the appropriate protocol
```

Refer to the private experiment:

```text
SynchronizationController#readWriteLockDowngrade()
GET /synchronization/read-write-lock-downgrade
```

Experiment keeps write lock, acquires more read lock, and then releases write lock. Another writer still has to wait until the read lock downgrade is released.

Then together with the experiment to keep **Read Lock Only** then try `writeLock().tryLock(timeout)`. Expected results:

```text
readToWriteUpgradeSucceeded = false
readToWriteUpgradeSupported = false
```

**Final Thoughts** downgrade retains continuity of reading rights after the end of mutation; `ReentrantReadWriteLock` Read → Write Upgrade is not supported. If write access is required, flow must release/re-coordinate instead of keeping read lock and waiting for the upgrade.

</details>

- [Back to top](#back-to-top)

---

## <a id="read-write-lock-policy">4. ReadWriteLock fairness, starvation, and when to use it</a>

<details>
<summary>Click for details</summary>

`ReentrantReadWriteLock` default is **non-fair**; A Fair Lock can be created by:

```java
new ReentrantReadWriteLock(true);
```

Fairness doesn't mean `Thread.start()` First definitely acquire first. It involves a policy serving the threads that are competing for locks, and in return there is often a throughput cost.

Similar `ReentrantLock`, non-blocking `tryLock()` of `ReadLock`/`WriteLock` It should not be used as proof that fair mode is always honored in all acquisition APIs. They can be acquired immediately if the lock state allows it, regardless of the waiter in line. If fairness is a protocol requirement, the acquisition API with the appropriate semantics must be chosen instead of relying solely on the constructor `new ReentrantReadWriteLock(true)`.

With read-heavy workloads, a non-fair policy can give good throughput, but writers may have to wait a long time if the reader keeps appearing. So don't choose `ReadWriteLock` just because I heard that "multiple readers run in parallel".

It's worth considering when:

- read more than write significantly;
- The critical section reads large enough for the concurrency to be beneficial;
- contention does exist;
- Benchmark/Workload shows greater benefits over overhead management of two types of locks.

References:

```text
SynchronizationController#readWriteLockPolicy()
GET /synchronization/read-write-lock-policy
```

The Experiment does not attempt to prove fairness by the log order. It directly observes the configured policy:

```text
defaultIsFair = false
explicitFairIsFair = true
untimedReadTryLockHonorsFairness = false
untimedWriteTryLockHonorsFairness = false
fairnessGuaranteesThreadStartOrder = false
```

**Final Thoughts** Fairness is a policy, not a benchmark shortcut; starvation/throughput must be evaluated according to the actual workload.

</details>

- [Back to top](#back-to-top)

---

## <a id="stamped-lock">5. StampedLock and optimistic read</a>

<details>
<summary>Click for details</summary>

`StampedLock` Support:

- write lock;
- read lock;
- optimistic read.

Optimistic read does not keep the read lock for the entire reading. The reader takes a stamp, reads the snapshot, and then calls `validate(stamp)`.

```text
tryOptimisticRead()
        ↓
Read the Snapshot
        ↓
validate(stamp)
   ├─ True → Snapshot is still valid
   └─ false → fallback to real read lock
```

References:

```text
SynchronizationController#stampedLock()
GET /synchronization/stamped-lock
```

Writers are deliberately interspersed in the middle of the optimistic read.

Expected results:

```text
optimisticStampValid = false
fallbackValue = updatedValue
```

**Final Thoughts** Optimistic Read is a read-and-proof strategy, not "read safely without validation".

</details>

- [Back to top](#back-to-top)

---

## <a id="stamped-lock-limitations">6. StampedLock is not ReadWriteLock faster</a>

<details>
<summary>Click for details</summary>

`StampedLock` There are some important semantics to remember:

- **No reentrant**: Threads holding write locks are not assumed to be re-acquiable as `ReentrantLock`;
- the `Lock` View of `StampedLock` Not Provided `Condition`;
- Programmer must manage `Long Stamp` and unlock the correct mode;
- Optimistic Read is always needed `validate(stamp)` before the snapshot;
- When cancellation/interruption is important, it is necessary to choose the appropriate Interruptible API instead of defaulting all acquisitions to cancellable.

References:

```text
SynchronizationController#stampedLockLimitations()
GET /synchronization/stamped-lock-limitations
```

Experiment hold write lock and call `tryWriteLock()` again on the same Thread. Since the lock does not reentrant, the second attempt fails. Experiment also calls `newCondition()` Read-Lock View and Operation Observation are not supported.

```text
sameThreadReentrantTryWriteLockSucceeded = false
reentrant = false
conditionSupported = false
```

**Final Thoughts** USE ONLY `StampedLock` When optimistic/read semantics are really worth it, complexity increases.

</details>

- [Back to top](#back-to-top)

---

## <a id="atomic-cas">7. Atomic variables and CAS</a>

<details>
<summary>Click for details</summary>

Atomic classes are suitable for many small transitions such as counter/reference updates.

For example:

```java
AtomicInteger count = new AtomicInteger();
count.incrementAndGet();
```

Mental model CAS:

```text
Read Expected Value
        ↓
New Value Calculation
        ↓
compare-and-set
   ├─ Success → completion
   └─ Failure → reread and retry
```

Atomic doesn't mean that every compound business rule becomes thread-safe. If the invariant goes through multiple objects/fields, locking or other designs may still be needed.

### Demo compareAndSet

References:

```text
SynchronizationController#atomicCas()
GET /synchronization/atomic-cas
```

The Experiment starts with:

```text
AtomicInteger = 10
```

then try two CAS:

```text
compareAndSet(10, 20)
→ true
→ value = 20

compareAndSet(10, 30)
→ false because expected=10 was stale
→ value still = 20
```

The important point is that CAS does not mean "set if you like". The update is only successful if the current state is still the expected value/reference that the caller relies on to calculate the transition.

### AtomicReference and ABA

The CAS on the reference usually compares the current value to the expected reference.

An advanced issue is ABA:

```text
A → B → A
```

CAS only sees the final value as A and may not know that the state has changed in between. Abstractions such as `AtomicStampedReference` a version/stamp can be attached when it is necessary to detect this case.

Experiment `/atomic-cas` Create the correct sequence:

```text
AtomicReference
A → B → A
stale observer still retains expected reference A
compareAndSet(A, C)
→ succeed in this experiment
```

because plain CAS only sees the current reference as `A`; It doesn't carry with it the history that the state has passed through `B`.

Then the same sequence is run with `AtomicStampedReference`:

```text
A, stamp=0
→ B, stamp=1
→ A, stamp=2

CAS equals expected A + stale stamp=0
→ false
```

**Final Thoughts** stamp/version helps to detect "the value looks the same but the history has changed". Not all CAS use cases have ABA problems; versioning is only added when the invariant really needs to know the intermediate transition.

### Atomic classes worth knowing

It is not necessary to memorize the entire package, but it is recommended to recognize the groups:

| Team | Example | Goal |
| --- | --- | --- |
| Primitive-like | `AtomicInteger`, `AtomicLong`, `AtomicBoolean` | Simple State Transition |
| Reference | `AtomicReference`, `AtomicStampedReference`, `AtomicMarkableReference` | Atomic Reference / Versioned Reference |
| Array | `AtomicIntegerArray`, `AtomicLongArray`, `AtomicReferenceArray` | Atomic Access by Element |
| High-contention counter | `LongAdder`, `DoubleAdder` | Multiple updates, next aggregate |
| Accumulator | `LongAccumulator`, `DoubleAccumulator` | reduction function as max/min/sum |
| Field updater | `AtomicIntegerFieldUpdater`, ... | Specialized Atomic Update Field |

The module uses two representative experiments:

```text
SynchronizationController#atomicCas()
→ CAS / AtomicReference / ABA / AtomicStampedReference

SynchronizationController#atomicTools()
→ AtomicInteger / LongAdder / LongAccumulator
```

</details>

- [Back to top](#back-to-top)

---

## <a id="adder-accumulator">8. LongAdder and LongAccumulator</a>

<details>
<summary>Click for details</summary>

`LongAdder` useful for counters with high contention. It scatters updates into multiple cells and then aggregates them by `sum()`.

### Limit when reading the total while still having the writer

`sum()` **Atomic Snapshot is not guaranteed** of the entire counter when other threads are still updating. It should not be interpreted as having only one fixed delay or always paying one snapshot at a single time.

When the concurrent update is no longer available, `sum()` pay the exact sum. So the experiment `atomicTools()` standby `join()` Whole Worker Before Reading Total:

```text
4 workers × 1,000 increments
→ join all workers
→ atomicInteger = longAdder = expectedCount = 4.000
```

Equal results after joining do not prove `LongAdder` replaces all use cases of `AtomicLong`.
Use an adder for statistics/metrics; with a sequence ID or admission type of "only for up to N requests", a suitable atomic state transition or primitive such as Semaphore. Not used `sum() < limit` then `increment()` like an atomic manipulation.

Refer to the contract: [LongAdder.sum() — Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html#sum()).

### LongAccumulator

`LongAccumulator` Generalize the idea with an accumulator function.

The accumulator function should be consistent with how the intermediate result is combined. Operations such as `Sum`, `max`, `Min` is an easy reasoning example.

It is not recommended to use a sequence-dependent or incombinable operation and then expect the result to be the same as sequential processing.

Also the accumulator function should **side-effect-free**. Implementation may have to reapply the function during the concurrent update/retry process, so it should not be considered a business action "exactly once" each time.

No Style:

```java
(current, value) -> {
    sendEmail();
    auditLog();
    return Math.max(current, value);
}
```

The side effect must be outside the reduction function; the function should only calculate the result from its arguments.

References:

```text
SynchronizationController#atomicTools()
GET /synchronization/atomic-tools
```

Demo Usage:

- `AtomicInteger` for exact counter;
- `LongAdder` for concurrent additions;
- `LongAccumulator` with permission `max`.

</details>

- [Back to top](#back-to-top)

---

## <a id="concurrent-collections">9. Concurrent Collections</a>

<details>
<summary>Click for details</summary>

Concurrent collections encapsulate the synchronization strategy into the data structure.

Some important groups:

| Collection | Objectives |
| --- | --- |
| `ConcurrentHashMap` | key/value concurrent access |
| `ConcurrentSkipListMap` | concurrent sorted map |
| `CopyOnWriteArrayList` | read-heavy, write little, snapshot iteration |
| `ConcurrentLinkedQueue` | non-blocking concurrent queue |
| `BlockingQueue` | queue with semantics waiting; deep learning in Coordination |

### Atomic compound methods

With `ConcurrentHashMap`, preferably the method that represents the atomic compound operation at the collection level:

```java
map.merge(key, 1, Integer::sum);
map.computeIfAbsent(key, k -> newValue());
map.putIfAbsent(key, value);
```

Instead of writing it yourself `get()` then `put()` and assume that the two separate operations become atomic.

### CopyOnWrite snapshot

Iterator's `CopyOnWriteArrayList` Observe the snapshot at the time the Iterator is created. New Write creates a new backing array that should be appropriate when reading more than writing significantly.

The trade-off point is that each mutation may have to copy backing arrays. So this collection is not suitable for write-heavy workloads or very large collections just because of the convenient read path.

### ConcurrentLinkedQueue

`poll()` no block. When the queue is empty it returns `null`.

Therefore, consumers should not write infinite busy loops, just call `poll()` if there is no backoff/termination strategy.

`size()` of `ConcurrentLinkedQueue` should not be considered a cheap constant-time metric for hot paths. If you just need to know if a queue has an element or not, prioritize the appropriate semantics such as `isEmpty()` instead of polling `size()` continuously.

Iterator of many concurrent collections is **weakly consistent**: It can reflect a partial update concurrent that doesn't fail-fast like a normal collection, but it shouldn't be inferred that it's an absolute snapshot. `CopyOnWriteArrayList` is another case because its Iterator actually uses a snapshot backing array.

### Some APIs/types worth recognizing

There is no need to turn this chapter into a catalog API, but you should be aware of the operations that help avoid the unatomic "check and update" pattern on `ConcurrentHashMap`:

```text
putIfAbsent
compute
computeIfAbsent
computeIfPresent
merge
replace(key, oldValue, newValue)
```

The types of the same family are also worth recognizing:

```text
CopyOnWriteArraySet
ConcurrentSkipListSet
```

They don't change the main mental model: choose a collection based on the access pattern and semantics it needs, not just because the name is written `Concurrent`.

### ConcurrentSkipListMap

`ConcurrentSkipListMap` suitable when needed simultaneously:

- concurrent access;
- the key is always observed according to the sorted order;
- Navigation operations such as `firstKey`, `lastKey`, range view.

It's not "ConcurrentHashMap but faster"; the two collections serve different access patterns.

References:

```text
SynchronizationController#concurrentSkipList()
GET /synchronization/concurrent-skip-list
```

Demo multiple thread insert keys in deliberately reversed order, then observe the last set of keys still sorted.

References:

```text
SynchronizationController#concurrentCollections()
GET /synchronization/concurrent-collections
```

The experiment uses local collections and always ends, not creating an infinite running consumer daemon like the old demo.

</details>

- [Back to top](#back-to-top)

---

## <a id="synchronization-choice">10. Which tool to choose?</a>

<details>
<summary>Click for details</summary>

```text
Simple State/Counter
→ Atomic / LongAdder

Multi-step critical section
→ synchronized or Lock

Read-Heavy Shared Structure
→ consider ReadWriteLock/CopyOnWrite depending on the pattern

Sorted Concurrent Map
→ ConcurrentSkipListMap

Conventional Concurrent Map
→ ConcurrentHashMap

Optimistic Read
→ StampedLock if complexity is worth it
```

Don't choose a tool based solely on "this is faster". Correctness first, then measure the actual contention/workload.

</details>

- [Back to top](#back-to-top)

---

## <a id="synchronization-experiments">11. Experiments of the Synchronization Section</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint |
| --- | --- | --- |
| `#synchronized-monitor` | `SynchronizationController#synchronizedCounter()` | `GET /synchronization/synchronized-counter` |
| `#reentrant-lock` | `SynchronizationController#tryLock()` | `GET /synchronization/try-lock` |
| `#reentrant-lock` | `SynchronizationController#interruptibleLock()` | `GET /synchronization/interruptible-lock` |
| `#reentrant-lock` | `SynchronizationController#reentrantLock()` | `GET /synchronization/reentrant-lock` |
| `#reentrant-lock` | `SynchronizationController#reentrantLockPolicy()` | `GET /synchronization/reentrant-lock-policy` |
| `#read-write-lock` | `SynchronizationController#readWriteLock()` | `GET /synchronization/read-write-lock` |
| `#read-write-lock` | `SynchronizationController#readWriteLockDowngrade()` | `GET /synchronization/read-write-lock-downgrade` |
| `#read-write-lock-policy` | `SynchronizationController#readWriteLockPolicy()` | `GET /synchronization/read-write-lock-policy` |
| `#stamped-lock` | `SynchronizationController#stampedLock()` | `GET /synchronization/stamped-lock` |
| `#stamped-lock-limitations` | `SynchronizationController#stampedLockLimitations()` | `GET /synchronization/stamped-lock-limitations` |
| `#atomic-cas` | `SynchronizationController#atomicCas()` | `GET /synchronization/atomic-cas` |
| `#adder-accumulator` | `SynchronizationController#atomicTools()` | `GET /synchronization/atomic-tools` |
| `#concurrent-collections` | `SynchronizationController#concurrentCollections()` | `GET /synchronization/concurrent-collections` |
| `#concurrent-collections` | `SynchronizationController#concurrentSkipList()` | `GET /synchronization/concurrent-skip-list` |

</details>

- [Back to top](#back-to-top)
