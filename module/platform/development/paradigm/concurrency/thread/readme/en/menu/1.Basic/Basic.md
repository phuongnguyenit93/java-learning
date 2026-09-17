<a id="back-to-top"></a>

# Basic Threads in Java

## Menu
- [1. Process and Thread](#process-and-thread)
- [2. Distinguish Thread, Runnable, Callable and Executor](#thread-task)
- [3. What is the difference between start() and run()?](#start-vs-run)
- [4. Create and Run Live Threads](#create-thread)
- [5. Thread lifecycle and Thread.State](#thread-state)
- [6. Daemon Thread and JVM lifecycle](#daemon-thread)
- [7. Experiments of the Basic section](#basic-endpoints)

This section builds the foundational mental model before going into interruption, Java Memory Model, synchronization, coordination, or thread pool.

Objectives after completing the course:

- Differentiate between Process, Thread and Task.
- Understand `start()` Others `run()` at what point.
- Know the two direct ways to create a Platform Thread using `Thread` and `Runnable`.
- Understand `Callable` does not create the Thread itself; it only describes a task with results.
- Read the statuses in the `Thread.State`.
- Understand exactly how Daemon Thread affects when a JVM ends.

## <a id="process-and-thread">1. Process and Thread</a>

<details>
<summary>Click for details</summary>

### What is Process?

A process is a program that is executing and has its own resource space managed by the operating system.

For example:

- IntelliJ IDEA is running as a process.
- A JVM running a Spring Boot application is a process.
- Chrome can use multiple processes for different tabs or components.

Processes are isolated from each other. If they want to exchange data, they usually have to use an IPC mechanism such as sockets, pipes, files, or shared memory.

### What is Thread?

A thread is an execution thread inside a process.

A JVM process usually has multiple threads that coexist:

```text
JVM Process
│
├── Main Thread
├── HTTP worker thread
├── GC thread
├── Scheduler Thread
└── Other Threads
```

Threads in the same process can access the same objects in the process heap. Each thread also has its own execution stack to store the call stack and local data of that execution.

It is important to:

```text
Multiple Threads
    ↓
Shared Mutable State can be accessed together
    ↓
Concurrency Problem may appear
```

Race condition, visibility, ordering, and synchronization will be learned in the following sections. In the Basic section, just keep in mind that just because multiple threads see the same object does not mean that it is always safe to access that object.

### What's the difference between Process and Thread?

| Characteristics | Process | Thread |
| --- | --- | --- |
| Scope | A running program | An execution thread inside the process |
| Memory space | Have your own address space | Same process, so you can access the same heap |
| Stack | A process can contain multiple threads, each with its own stack | Each thread has its own stack |
| Communication | Usually IPC Required | Can Communicate via Shared Object |
| Creation/conversion costs | Usually larger than threads | Usually lighter than process |
| Concurrency risk | Better isolation between processes | Prone to errors when sharing mutable state |

### Demo in module

Refer to the controller:

```text
BasicThreadController#processAndThread()
```

Endpoint:

```text
GET /basic/process-thread
```

When running this endpoint, the response returns:

- PID of the current JVM process.
- The name of the thread that is processing the HTTP request.
- The ID of that thread.
- Is that Thread a daemon or not.
- The current state of the thread.

The point to observe is **an HTTP request that does not exist independently**. It is being executed by a specific thread inside the JVM process.

**Final Thoughts** A process is a range of resources and multiple threads; a thread is an execution flow that is actually running code within that process.

</details>

- [Back to top](#back-to-top)

---

## <a id="thread-task">2. Distinguish Thread, Runnable, Callable and Executor</a>

<details>
<summary>Click for details</summary>

This is a very easy point to learn wrong if you only remember individual APIs.

### Thread

`Thread` represents an execution flow.

For example:

```java
Thread worker = new Thread(() -> {
    System.out.println(Thread.currentThread().getName());
});

worker.start();
```

### Runnable

`Runnable` Job Description:

```java
Runnable task = () -> {
    System.out.println("Doing work");
};
```

`Runnable` is not a Thread and does not create a new thread by itself.

Want a task to run on a new Thread:

```java
Thread worker = new Thread(task);
worker.start();
```

Mental model:

```text
Runnable
   = WHAT to run

Thread
   = WHERE execution happens
```

### Callable

`Callable<V>` also describes a task but can:

- returns results;
- throw checked exception.

```java
Callable<String> task = () -> "result";
```

`Callable` nor do they create their own Threads.

In the Basic section, the module uses `FutureTask` to connect `Callable` with a Direct Thread:

```java
Callable<String> task = () -> "result";
FutureTask<String> futureTask = new FutureTask<>(task);

Thread worker = new Thread(futureTask, "callable-worker");
worker.start();

String result = futureTask.get();
```

`Future`, `ExecutorService` and `CompletableFuture` will be studied carefully in the following chapters.

### Executor

An executor is an abstraction used to receive a task and decide how the task is executed.

In the Basic section, just keep in mind:

```text
Task abstraction
    Runnable/Callable
            ↓
Execution mechanism
    Thread/Executor
```

Don't call `Callable` or Thread Pools are "how to create Threads" because they take on different roles.

### Contact the code in the module

References:

```text
CreateThreadController#createByExtends()
CreateThreadController#createByRunnable()
CreateThreadController#createByCallable()
```

These three methods are placed side by side to clearly see three different roles:

```text
MyWorker extends Thread
    → task and execution mechanism are attached to the same class

Runnable + Thread
    → task and Thread are separated

Callable + FutureTask + Thread
    → resulting task still needs an execution mechanism to run
```

**Final Thoughts** `Runnable` and `Callable` Description **Jobs**; `Thread` or `Executor` Take responsibility **Job execution**.

</details>

- [Back to top](#back-to-top)

---

## <a id="start-vs-run">3. What is the difference between start() and run()?</a>

<details>
<summary>Click for details</summary>

This is the most basic knowledge when working directly with `Thread`.

Suppose there is:

```java
Thread worker = new Thread(task, "worker-thread");
```

### Call run()

```java
worker.run();
```

This is just a regular Java method call.

No new threads are started.

`task` Run right on the incoming thread `run()`.

```text
HTTP request thread
      ↓
worker.run()
      ↓
the task still runs on the HTTP request thread
```

### Call start()

```java
worker.start();
```

`start()` ask the JVM to start the execution of that Thread. The JVM will then call the `run()` on the new thread.

```text
HTTP request thread
      │
      └── worker.start()
              ↓
        worker-thread
              ↓
            run()
```

So:

```text
run() → normal method call
start() → start the lifecycle of a new thread
```

An object `Thread` can only `start()` once. If calling `start()` the second time after it has been started, the JVM throws `IllegalThreadStateException`.

### Demo in module

Refer to the controller:

```text
BasicThreadController#startVsRun()
```

Endpoint:

```text
GET /basic/start-vs-run
```

Demo runs the same `Runnable` in two ways:

1. Direct Call `run()`;
2. create Threads and call `start()`.

When running, compare the thread names:

```text
run() directly
→ task running on caller/request thread

start()
→ task running on basic-start-worker
```

The point to observe is not the log order, but the **Which thread is executing `run()`**.

**Final Thoughts** Call `run()` just call the normal method; call `start()` just start the lifecycle of a separate Thread.

</details>

- [Back to top](#back-to-top)

---

## <a id="create-thread">4. Create and Run Live Threads</a>

<details>
<summary>Click for details</summary>

In the Basic section, the goal is to understand low-level APIs before learning Executor.

### Option 1: extend Thread

```java
public class MyWorker extends Thread {

    publicMyWorker(String name) {
        super(name);
    }

    @Override
    public void run() {
        System.out.println(
                "Running on " + Thread.currentThread().getName()
        );
    }
}
```

Usage:

```java
Thread worker = new MyWorker("extends-thread-worker");
worker.start();
```

This helps to clearly see the inherited relationship with `Thread`, but it makes the work class tied tightly to the execution mechanism.

### Method 2: Runnable + Thread

```java
Runnable task = () -> {
    System.out.println(
            "Running on " + Thread.currentThread().getName()
    );
};

Thread worker = new Thread(task, "runnable-worker");
worker.start();
```

This way clearly separates:

```text
Runnable = task
Thread = execution
```

This is an important mental model for understanding the Executor later.

### Callable + FutureTask + Thread

`Callable` Can't stream directly to Constructor `Thread` because `Thread` GET `Runnable`.

`FutureTask` Wrapable `Callable` and simultaneously implements `Runnable`:

```java
Callable<String> task = () -> "Result";
FutureTask<String> futureTask = new FutureTask<>(task);

Thread worker = new Thread(futureTask, "callable-worker");
worker.start();

String result = futureTask.get();
```

Points to understand:

```text
Callable
    ↓ Wrapped by
FutureTask
    ↓ Run by
Thread
```

### Demo in module

Refer to the controller:

```text
CreateThreadController#createByExtends()
CreateThreadController#createByRunnable()
CreateThreadController#createByCallable()
```

Endpoints:

```text
GET /create/extends
GET /create/runnable
GET /create/callable
```

As you run each endpoint, look at the worker's name in the response:

```text
/extends
→ extends-thread-worker

/runnable
→ runnable-thread-worker

/callable
→ callable-thread-worker
```

`/extends` and `/runnable` all create an execution with `Thread`, but the way the task is organized is different. `/callable` show `Callable` does not run by itself: it is `FutureTask` wrapped up and then got a `Thread` enforcement.

Every demo `join()` or wait for the results before the end of the experiment, so don't leave the worker thread unexpectedly.

**Final Thoughts** This section learns how to join **task** with **Execution Mechanism**, not having to learn three equivalent APIs.

</details>

- [Back to top](#back-to-top)

---

## <a id="thread-state">5. Thread lifecycle and Thread.State</a>

<details>
<summary>Click for details</summary>

Java defines six states in `Thread.State`:

```text
NEW
RUNNABLE
BLOCKED
WAITING
TIMED_WAITING
TERMINATED
```

No enum state named `RUNNING`.

A thread that is actually using the CPU is still represented by the JVM `RUNNABLE`.

### NEW

Thread object has been created but has not yet `start()`:

```java
Thread worker = new Thread(task);
System.out.println(worker.getState()); NEW
```

### RUNNABLE

The thread has been started and is either executable or executable.

Java doesn't separate "ready" and "running" into two values `Thread.State`.

### BLOCKED

Thread waiting for the intrinsic monitor to enter a block or method `synchronized`.

For example:

```text
Thread A holds monitor X
        ↓
Thread B tries to synchronize(X)
        ↓
Thread B = BLOCKED
```

`BLOCKED` has a very specific meaning: wait for the monitor lock of `synchronized`.

Every case should not be called "waiting" `BLOCKED`.

### WAITING

Threads are waiting indefinitely until another condition or event makes it possible to continue.

Some APIs can include threads `WAITING`:

- `Object.wait()` no timeout;
- `Thread.join()` no timeout;
- `LockSupport.park()`.

Example with `wait()`:

```java
synchronized (monitor) {
    monitor.wait();
}
```

Thread is calling `wait()` must be awakened by `notify()`, `notifyAll()`, interrupt, or a mechanism that matches the API being used.

Private `join()` There are other semantics: the thread is calling `join()` Wait for the target thread to finish.

### TIMED_WAITING

Thread is waiting with a time limit.

For example:

- `Thread.sleep(...)`;
- `Object.wait(timeout)`;
- `Thread.join(timeout)`;
- `LockSupport.parkNanos(...)`.

### TERMINATED

Method `run()` completed or terminated by exception not processed.

The thread object still exists as a Java object, but the execution of that thread has ended and cannot be `start()` again.

### Mental model diagram

```text
new Thread(...)
      ↓
     NEW
      ↓ start()
   RUNNABLE
      ↓
 ┌────┼───────────────┐
 ↓    ↓               ↓
BLOCKED WAITING / TIMED_WAITING
 └────┴──────┬────────┘
             ↓
          RUNNABLE
             ↓ run() ends
         TERMINATED
```

This is the conceptual model. A thread can be switched between `RUNNABLE` and multiple standby states throughout the lifecycle.

### Demo in module

Refer to the controller:

```text
BasicThreadController#threadLifeCycle()
```

Endpoint:

```text
GET /basic/thread-life-cycle
```

The new demo actively controls a worker to pass through one after another:

```text
NEW
→ RUNNABLE
→ TIMED_WAITING
→ BLOCKED
→ WAITING
→ TERMINATED
```

The service uses internal signals to observe the state instead of relying solely on intervals. `sleep()` random of the request thread. Separate `TIMED_WAITING` created with a latch with a long timeout; the caller observes the state and then actively releases the latch so that the worker continues to move on `BLOCKED`. So the demo doesn't depend on the scheduler having to "catch up" with a short sleep window.

When running an endpoint, the response should represent enough strings:

```text
NEW -> NEW
RUNNABLE -> RUNNABLE
TIMED_WAITING -> TIMED_WAITING
BLOCKED -> BLOCKED
WAITING -> WAITING
TERMINATED -> TERMINATED
```

The point to observe is that each state appears because **a specific cause**:

- `TIMED_WAITING` due to waiting for a timeout;
- `BLOCKED` due to waiting for the intrinsic monitor of `synchronized`;
- `WAITING` Red `wait()` no timeout;
- `TERMINATED` after `run()` end.

**Final Thoughts** Every state of "waiting" should not be lumped together into a general concept. `Thread.State` describe different types of waits with different semantics.

</details>

- [Back to top](#back-to-top)

---

## <a id="daemon-thread">6. Daemon Thread and JVM lifecycle</a>

<details>
<summary>Click for details</summary>

Java threads have daemon attributes.

The most important point is not that the daemon "runs in the background", but how the JVM decides when it ends.

### User threads and daemon threads

The JVM continues to exist as long as the user thread is alive.

When there are no user threads alive, the JVM can terminate even though the thread daemon has not yet completed.

```text
User Thread is alive
    → JVM keeps running

Only Daemon Thread Left
    → JVM is not required to wait for the daemon to complete
```

Therefore, daemon threads are not suitable for tasks that must be completed before the process is finished, such as recording important data without other assurance mechanisms.

Daemon also **does not mean low priority**. `Daemon` is a property related to JVM liveness/lifecycle; thread scheduling priority is another concept. It is not inferred that a thread daemon will automatically be given lower priority by the CPU just because `isDaemon() == true`.

### New thread inherits daemon status

When creating a new thread, the default status daemon is inherited from the thread that created it.

Can be changed before start:

```java
Thread worker = new Thread(task);
worker.setDaemon(true);
worker.start();
```

After the Thread has been started, the daemon flag cannot be changed with `setDaemon(...)`.

### Why not use HTTP endpoints to prove JVM exit?

In Spring Boot, the end of an HTTP request does not mean the end of the JVM.

The server still has many other user threads alive.

Hence the demo type:

```text
Request End
→ conclusion Daemon Thread must die
```

is wrong.

Controllers are only suitable for observing daemon properties, not for proving JVM shutdown rules.

### Demo in Spring Boot

Refer to the controller:

```text
DaemonThreadController#inspectDaemonRules()
```

Endpoint:

```text
GET /daemon/inspect
```

The demo shows:

- the status of the HTTP request thread;
- the status daemon that the Child Thread inherits by default;
- value after actively calling `setDaemon(true)` Previous `start()`.

When running this endpoint, there is no conclusion about when the JVM shutdown. Endpoints are for observational purposes only **Daemon Flag and Inheritance Rule**.

### Standalone JVM lifecycle demo

File:

```text
src/main/java/com/example/learning/module/basic/thread/DaemonJvmExitDemo.java
```

This class has `main()` and must run as a standalone Java application.

Runs with:

```text
True → Worker is Daemon Thread
false → worker is user thread
```

When `true`, `Main` end before the worker and the JVM does not wait for the worker to finish.

When `false`, the worker is the user thread, so the JVM continues to live until the worker is finished.

**Final Thoughts** HTTP demo helps to observe the daemon property; `DaemonJvmExitDemo.main()` is the right experiment to prove JVM exit semantics.

</details>

- [Back to top](#back-to-top)

---

## <a id="basic-endpoints">7. Experiments of the Basic section</a>

<details>
<summary>Click for details</summary>

| README section | Controller method / Demo | Endpoint | Purpose |
| --- | --- | --- | --- |
| `#process-and-thread` | `BasicThreadController#processAndThread()` | `GET /basic/process-thread` | Observe JVM processes and HTTP request threads |
| `#start-vs-run` | `BasicThreadController#startVsRun()` | `GET /basic/start-vs-run` | Proof `run()` No new threads are created `start()` Yes |
| `#thread-state` | `BasicThreadController#threadLifeCycle()` | `GET /basic/thread-life-cycle` | Observe the Six Values `Thread.State` |
| `#create-thread` | `CreateThreadController#createByExtends()` | `GET /create/extends` | Create a Thread using the subclass of `Thread` |
| `#create-thread` | `CreateThreadController#createByRunnable()` | `GET /create/runnable` | Separate tasks `Runnable` from `Thread` |
| `#create-thread` | `CreateThreadController#createByCallable()` | `GET /create/callable` | Running `Callable` by `FutureTask` and a Live Thread |
| `#daemon-thread` | `DaemonThreadController#inspectDaemonRules()` | `GET /daemon/inspect` | Observing the daemon flag and inheritance |
| `#daemon-thread` | `DaemonJvmExitDemo.main()` | Standalone Java Application | Proving JVM doesn't wait for daemon threads |

After this section, it is necessary to answer the questions yourself:

1. `Thread`, `Runnable` and `Callable` Where are the different roles?
2. Why `thread.run()` not equivalent `thread.start()`?
3. `Thread.State.BLOCKED` Others `WAITING` How?
4. Why a Thread Was `TERMINATED` Can't restart?
5. Why doesn't ending an HTTP request in Spring Boot prove that the thread daemon is dead?
6. Why shouldn't Executor be considered simply "a way to create threads"?

</details>

- [Back to top](#back-to-top)
