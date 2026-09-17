# 📂 README MODULE STRUCTURE (EN)

* **1.Basic**
    * [Basic](readme/en/menu/1.Basic/Basic.md)
* **2.Interruption**
    * [Interruption](readme/en/menu/2.Interruption/Interruption.md)
* **3.Concurrency_Problems**
    * [ConcurrencyProblem](readme/en/menu/3.Concurrency_Problems/ConcurrencyProblem.md)
* **4.Synchronization**
    * [Synchronization](readme/en/menu/4.Synchronization/Synchronization.md)
* **5.Coordination**
    * [Coordination](readme/en/menu/5.Coordination/Coordination.md)
* **6.Thread_Pool_Executor**
    * [ThreadPoolExecutor](readme/en/menu/6.Thread_Pool_Executor/ThreadPoolExecutor.md)
* **7.Async**
    * [Async](readme/en/menu/7.Async/Async.md)
* **8.Context**
    * [ThreadLocal](readme/en/menu/8.Context/ThreadLocal.md)
* **9.Spring_Task_Executor**
    * [SpringExecutor](readme/en/menu/9.Spring_Task_Executor/SpringExecutor.md)
* **10.Leak**
    * [Leak](readme/en/menu/10.Leak/Leak.md)
* **11.Virtual_Thread**
    * [VirtualThread](readme/en/menu/11.Virtual_Thread/VirtualThread.md)
* **12.Q&A**
    * [Q&A](readme/en/menu/12.Q&A/Q&A.md)

# Thread & Concurrency

This module is designed to teach Thread and Concurrency from **fundamentals → real-world problems → problem-solving tools → Spring Boot usage**.

The goal is not just to memorize APIs, but to understand:

- How Threads are created and executed.
- What happens when multiple threads access the same data.
- Why race conditions, visibility problems, ordering issues, and deadlocks appear.
- When to use synchronization, locks, atomic classes, concurrent collections, or coordination primitives.
- How Thread Pools and Executors manage resources.
- How async differs from parallel and non-blocking execution.
- How context is propagated between threads.
- How Spring Boot manages task executors.
- Lifecycle problems such as thread leaks and pool leaks, and how to recognize them.
- How Virtual Threads address scalability and what their limitations are.

---

## Learning Flow

```text
1. Basic Thread
        ↓
2. Interruption & Cancellation
        ↓
3. Concurrency Problems & Java Memory Model
        ↓
4. Synchronization
        ↓
5. Thread Coordination
        ↓
6. Thread Pool & Executor
        ↓
7. Future / CompletableFuture / Async
        ↓
8. ThreadLocal & Context Propagation
        ↓
9. Spring Task Executor
        ↓
10. Thread / Pool Lifecycle & Leak
        ↓
11. Virtual Thread
        ↓
12. Q&A / Review
```

---

## 1. Basic Thread

Start with the nature of a Thread before learning higher-level concurrency tools.

Main topics:

- Process and Thread.
- Creating work with `Thread`, `Runnable`, and `Callable`.
- How `start()` differs from `run()`.
- Thread lifecycle and `Thread.State`.
- Daemon Threads.
- Observing thread names, states, and execution flow.

After this section, you should be able to answer:

> How is a Thread created, started, executed, and terminated?

---

## 2. Interruption & Cancellation

Java Threads should not be forcibly "killed". Stopping a Thread is primarily based on cooperative cancellation.

Main topics:

- What `interrupt()` actually does.
- The interrupt flag.
- `InterruptedException`.
- Blocking operations and interruption.
- How to write loops that can stop safely.
- How to clean up resources when a task is cancelled.

After this section, you should be able to answer:

> How do you correctly request that a Thread stop?

---

## 3. Concurrency Problems & Java Memory Model

Before learning synchronization, first understand the problems synchronization is intended to solve.

Main topics:

- Shared mutable state.
- Race conditions.
- Atomicity.
- Visibility.
- Ordering.
- Java Memory Model.
- Happens-before.
- Safe publication, `final` field initialization safety, and `this` escape.
- `volatile`.
- Deadlock, livelock, and starvation.

After this section, you should be able to answer:

> Why can code behave correctly with one Thread but fail when multiple Threads execute it concurrently?

---

## 4. Synchronization

Once the causes of concurrency bugs are understood, the next step is learning mechanisms that protect shared state.

Main topics:

- `synchronized` and monitors.
- `ReentrantLock`.
- Fair locks, `tryLock()`, and `lockInterruptibly()`.
- `ReadWriteLock`.
- `StampedLock`.
- Atomic classes and CAS.
- `LongAdder`, `LongAccumulator`.
- Concurrent collections.

The goal of this section is not to find one "best" tool, but to understand the trade-offs of each mechanism.

---

## 5. Thread Coordination

Synchronization protects data; coordination helps multiple Threads control execution order and exchange signals.

Main topics:

- `join()`.
- `wait()`, `notify()`, `notifyAll()`.
- Condition predicates and spurious wakeups.
- `Condition`.
- `LockSupport`.
- Producer - Consumer.
- `BlockingQueue`.
- `CountDownLatch`.
- `CyclicBarrier`.
- `Semaphore`.
- `Phaser`.
- `Exchanger`.

After this section, the distinction should be clear:

```text
Synchronization → protect shared state
Coordination    → coordinate execution between threads
```

---

## 6. Thread Pool & Executor

After understanding direct Thread usage, move on to how real applications manage large numbers of tasks.

Main topics:

- Executor abstraction.
- `ExecutorService`.
- `ThreadPoolExecutor`.
- Core pool size and maximum pool size.
- Work queue.
- Keep-alive time.
- ThreadFactory.
- Rejection policies.
- Graceful shutdown.
- Bounded queues and backpressure.

After this section, you should be able to answer:

> Why do real applications usually submit tasks to an Executor instead of creating a new Thread for every unit of work?

---

## 7. Future / CompletableFuture / Async

Once tasks are executed by an Executor, the next problem is obtaining results and composing multiple asynchronous tasks.

Main topics:

- `Future`.
- `CompletableFuture`.
- Chaining and composition.
- Exception handling.
- `allOf()` / `anyOf()`.
- Executor selection for individual stages.
- The differences between synchronous, asynchronous, parallel, and non-blocking execution.

Key point to remember:

> Async does not mean non-blocking.

---

## 8. ThreadLocal & Context Propagation

Only after understanding Executors and thread reuse does it make sense to study data associated with each Thread's execution context.

Main topics:

- `ThreadLocal`.
- `InheritableThreadLocal`.
- Thread reuse and the risk of stale context.
- Cleanup with `remove()`.
- Context propagation.
- Task decorators.

After this section, you should understand that ThreadLocal primarily helps **avoid sharing a certain kind of state across threads**; it is not a replacement for synchronization when state is genuinely shared.

---

## 9. Spring Task Executor

Apply Executor knowledge in Spring Boot.

Main topics:

- `TaskExecutor`.
- `ThreadPoolTaskExecutor`.
- `@Async`.
- Custom executors.
- Context propagation in Spring.
- Shutdown and lifecycle management provided by Spring.

The goal is to understand which Java concurrency concepts Spring is wrapping, rather than treating `@Async` as an independent concurrency mechanism.

---

## 10. Thread / Pool Lifecycle & Leak

A concurrency demo is only correct when the resources it creates also have a correct lifecycle.

Main topics:

- Thread leaks.
- Executor leaks.
- Queue growth.
- Native thread exhaustion.
- Context-switching overhead.
- Scheduled-task duplication.
- Thread dumps and system observation.
- Correct shutdown behavior.

Module principle:

```text
Intentionally faulty demo
→ must be clearly documented and bounded in scope

Normal demo
→ must clean up every created thread / executor / resource
```

---

## 11. Virtual Thread

Virtual Threads come last, after the cost model and limitations of Platform Threads are already understood.

Main topics:

- Platform Threads and Virtual Threads.
- Carrier Threads.
- Mount / unmount.
- Blocking operations with Virtual Threads.
- Pinning in Java 21.
- Virtual Threads and Executors.
- Virtual Threads in Spring Boot.
- Throughput and latency.
- When Virtual Threads are appropriate and when they are not.

Virtual Threads do not change the principles of race conditions, synchronization, or shared mutable state learned in earlier sections.

---

## 12. Q&A / Review

The final section connects the concepts covered throughout the module and addresses questions that are easy to misunderstand.

Examples:

- How do Stack and Heap relate to Thread Safety?
- Are local variables always Thread Safe?
- How does `volatile` differ from `Atomic` and `synchronized`?
- How is a Thread Pool different from a Virtual Thread?
- Does async execution always create a new Thread?
- When should `BlockingQueue` be used instead of manual `wait/notify`?
- When should locks be used, and when is it better to avoid shared mutable state entirely?

---

## How to Learn with This Module

Each topic should follow the same learning flow:

```text
Knowledge / Mental Model
        ↓
Problem to solve
        ↓
Controller triggers the demo
        ↓
Service executes the experiment
        ↓
Observe thread / state / timing / output
        ↓
Explain the result
```

Controllers in this module act as **experiment triggers**, not business APIs.

Services implement the experiments that demonstrate the concepts documented in the README.

Each experiment should aim for these criteria:

1. The mental model is accurate.
2. The demo actually proves the behavior being explained.
3. The result is clear enough to observe and reproduce.
4. Threads, executors, and resources are cleaned up with the correct lifecycle.
5. README, endpoint, and code all express the same meaning.
