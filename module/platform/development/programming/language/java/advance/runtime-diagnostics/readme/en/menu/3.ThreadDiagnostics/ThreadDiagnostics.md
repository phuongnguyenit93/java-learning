<a id="back-to-top"></a>

# Thread and Liveness Diagnostics

## Menu
- [What Problems Thread Diagnostics Solves](#thread-diagnostics-purpose)
- [Thread Dump Structure and Meaning](#thread-dump-structure)
- [Thread States, Stack Traces, and Execution Evidence](#thread-states-and-stack-evidence)
- [Locks, Deadlocks, and Contention as Diagnostic Evidence](#locks-deadlocks-and-contention)
- [Repeated Thread Dumps and Liveness Analysis](#repeated-thread-dumps)
- [Platform and Virtual Thread Diagnostics in Java 21](#platform-vs-virtual-thread-diagnostics)
- [Common Pitfalls When Interpreting Thread Evidence](#thread-diagnostic-pitfalls)

## <a id="thread-diagnostics-purpose">What Problems Thread Diagnostics Solves</a>

<details>
<summary>Click for details</summary>

Thread diagnostics answers **“what are threads doing, and why is work not progressing?”** It is useful for hangs, slow requests, high CPU, exhausted thread pools, suspected deadlocks, or many threads waiting for the same resource.

A thread dump is not execution history. It is a point-in-time view of:

- thread identity;
- Java state;
- stack trace;
- monitor/lock information when supported by the tool;
- sometimes deadlock summaries.

Thread evidence should therefore be used to form and test a hypothesis, not as automatic proof from one snapshot.

The boundary matters: this module reads runtime evidence. Detailed synchronized/Lock semantics, happens-before, and concurrency correctness belong to Java Concurrency.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-dump-structure">Thread Dump Structure and Meaning</a>

<details>
<summary>Click for details</summary>

A traditional thread dump usually contains a block for each platform thread: name, id, state, stack frames, and lock/monitor annotations.

Simplified example:

~~~text
"worker-1" #31 ... RUNNABLE
   java.lang.Thread.State: RUNNABLE
        at com.example.OrderService.calculate(OrderService.java:84)
        at ...
~~~

Monitor annotations may conceptually show:

~~~text
- waiting to lock <0x...>
- locked <0x...>
- waiting on <0x...>
~~~

A useful reading order is:

1. identify whether the thread name/id belongs to the workload;
2. inspect the state;
3. read the top stack frames;
4. inspect lock/monitor relationships;
5. compare with other threads and later snapshots.

A stack frame reports **where the thread was observed**; it does not prove that frame is the root cause.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-states-and-stack-evidence">Thread States, Stack Traces, and Execution Evidence</a>

<details>
<summary>Click for details</summary>

Java Thread.State provides the first vocabulary for interpreting a dump:

- **RUNNABLE**: executing or runnable from the JVM's perspective; not proof that it is currently consuming CPU;
- **BLOCKED**: waiting to acquire a monitor for synchronized code;
- **WAITING**: waiting indefinitely through mechanisms such as Object.wait, Thread.join, or park;
- **TIMED_WAITING**: waiting with a timeout;
- NEW/TERMINATED are less common in ordinary live dumps.

The stack trace supplies the current call path. When many threads share one stack, ask whether that is an **expected wait point** or a bottleneck.

A consumer WAITING in a queue take can be perfectly normal when there is no work. Hundreds of request threads repeatedly BLOCKED on the same application lock is much stronger contention evidence.

Do not map state directly to “problem.” Read state together with workload, stack, and time.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="locks-deadlocks-and-contention">Locks, Deadlocks, and Contention as Diagnostic Evidence</a>

<details>
<summary>Click for details</summary>

**Contention** means threads compete for a shared resource or lock. **Deadlock** is stronger: a set of threads forms a wait cycle so none of them can make progress.

Example:

~~~text
Thread A holds Lock 1 → waits for Lock 2
Thread B holds Lock 2 → waits for Lock 1
~~~

Thread dumps can reveal owners and waiters, and JDK diagnostic tooling can identify some deadlocks explicitly.

For contention:

- find many threads BLOCKED on the same monitor;
- identify the owner;
- inspect what the owner is doing;
- repeat the dump to see whether the relationship persists;
- correlate with JFR monitor/park events when duration and timeline matter.

No detected deadlock does not prove liveness is healthy. Starvation, external I/O waits, executor saturation, or logical livelock can stop useful progress without a lock cycle.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="repeated-thread-dumps">Repeated Thread Dumps and Liveness Analysis</a>

<details>
<summary>Click for details</summary>

One dump shows one instant. To separate a transient state from a liveness problem, collect **several dumps at a short interval appropriate to the incident**.

Example:

~~~text
t0      thread A → same stack / same lock
t0+10s  thread A → same stack / same lock
t0+20s  thread A → same stack / same lock
~~~

If request threads preserve the same stack and lock relationships while traffic continues, the “stuck or blocked” hypothesis becomes stronger.

If stacks change continuously, work may still be progressing even though every isolated snapshot looks busy.

Repeated dumps should use the same PID, record timestamps, use a consistent interval, avoid excessive sampling, and be correlated with CPU/latency/logs from the same window.

When the problem needs a real timeline rather than manual snapshots, JFR is the natural next evidence source.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="platform-vs-virtual-thread-diagnostics">Platform and Virtual Thread Diagnostics in Java 21</a>

<details>
<summary>Click for details</summary>

Java 21 has two thread categories with important observability differences.

**Platform threads**  
ThreadMXBean monitors and manages them. Traditional dumps and jcmd Thread.print fit ordinary platform-thread counts.

**Virtual threads**  
They may exist in very large numbers. Java 21 ThreadMXBean does **not** monitor or manage virtual threads.

To dump both platform and virtual threads:

~~~text
jcmd <pid> Thread.dump_to_file -format=text threads.txt
jcmd <pid> Thread.dump_to_file -format=json threads.json
~~~

Oracle notes that this format does not contain all information present in traditional thread dumps, such as object addresses and some lock/JNI/heap statistics.

JFR adds virtual-thread-specific evidence such as VirtualThreadPinned and VirtualThreadSubmitFailed. For virtual-thread-heavy applications, JFR and thread-dump evidence are complementary.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-diagnostic-pitfalls">Common Pitfalls When Interpreting Thread Evidence</a>

<details>
<summary>Click for details</summary>

Common interpretation failures include:

**RUNNABLE means “using CPU”**  
Not necessarily. Correlate with CPU samples or OS/JFR evidence.

**WAITING or BLOCKED always means a bug**  
Many waits are expected by design.

**One dump proves a thread is stuck**  
A single snapshot lacks the time dimension.

**Many threads automatically means a problem**  
Large virtual-thread counts can be normal; thread type and workload matter.

**Reading only thread names**  
Names may be reused or framework-generated. Stack and context are more useful.

**No deadlock means liveness is healthy**  
Starvation, I/O stalls, and executor saturation may still exist.

**Using ThreadMXBean to count virtual threads**  
In Java 21 its management scope is platform threads.

When the question changes from “where are threads waiting?” to “is the synchronization design correct?”, hand off to the Concurrency curriculum.

</details>

- [Quay lại đầu trang](#back-to-top)
