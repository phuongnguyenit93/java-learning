<a id="back-to-top"></a>

# Runtime Diagnostic Mindset

## Menu
- [What Runtime Diagnostics Is and Why It Exists](#runtime-diagnostics-purpose)
- [From Symptoms to Evidence, Hypotheses, and Root Cause](#diagnostic-reasoning-loop)
- [Evidence Quality, Cost, and Intrusiveness](#evidence-quality-and-cost)
- [Preparing Diagnostic Readiness Before an Incident](#diagnostic-readiness-principle)
- [Boundaries with JVM Internals, Instrumentation, and Observability](#diagnostic-scope-boundaries)

## <a id="runtime-diagnostics-purpose">What Runtime Diagnostics Is and Why It Exists</a>

<details>
<summary>Click for details</summary>

Runtime diagnostics is the process of observing a running JVM, or the artifacts it leaves behind, to answer a specific question about an incident. The goal is not to run as many tools as possible. The goal is to turn an external symptom into evidence strong enough to test a hypothesis.

The following are still only **symptoms**:

- an API suddenly becomes slow;
- the process consumes high CPU;
- process memory keeps increasing;
- requests appear to hang;
- an `OutOfMemoryError` occurs;
- the JVM crashes.

Each symptom has several possible causes. High latency can come from blocked threads, lock contention, GC pauses, I/O, a downstream dependency, or hot application code. Runtime diagnostics therefore follows a reasoning chain such as:

```text
Symptom
    ↓
What is happening inside the JVM?
    ↓
What evidence can we observe?
    ↓
Which hypothesis fits that evidence?
    ↓
What additional data would confirm or reject it?
```

Evidence in this module includes thread dumps, class histograms, heap dumps, GC logs, JFR recordings, Native Memory Tracking output, MXBean/JMX data, and crash artifacts. Each source exposes only part of the runtime. No single artifact explains every class of failure.

It is important to separate **mechanism** from **diagnosis**. The JVM module explains how heap memory, GC, JIT compilation, and runtime execution work. The Concurrency modules explain synchronization, happens-before, and correctness. Runtime Diagnostics consumes those mental models to interpret observable evidence when a system misbehaves.

By the end of this module, you should be able to start from a symptom, choose an appropriate source of evidence, collect it at a reasonable cost, and build a conclusion that can be tested rather than guessed.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnostic-reasoning-loop">From Symptoms to Evidence, Hypotheses, and Root Cause</a>

<details>
<summary>Click for details</summary>

A strong diagnostic process keeps three things separate: **what was observed**, **the hypothesis that explains it**, and **the root cause**.

For example:

```text
Observation:
  p95 latency rises from 100 ms to 4 s

Hypothesis A:
  many request threads are waiting on the same lock

Hypothesis B:
  long GC pauses are stopping progress

Hypothesis C:
  downstream I/O is slow
```

None of these hypotheses is a conclusion yet. You need evidence that can distinguish them: repeated thread dumps for A, GC logs or JFR for B, and stack plus I/O evidence for C.

A practical reasoning loop usually looks like this:

1. **Define the symptom and its time window** — when did it happen, for which requests, for how long, and does it repeat?
2. **Narrow the subsystem** — thread/liveness, Java heap, GC, native memory, CPU, I/O, or crash.
3. **State a falsifiable hypothesis** — for example, “worker threads are waiting on the same monitor,” rather than “Java is hung.”
4. **Choose the cheapest evidence that can distinguish competing hypotheses.**
5. **Collect and correlate over time** — do not rely on one snapshot for a trend problem.
6. **Update the hypothesis** — discard it when the data does not fit instead of forcing the evidence to match the initial guess.
7. **Confirm the cause with behavior that can be rechecked** — after a fix, the related signal should change in the predicted direction.

“The heap is almost full” is not yet a root cause. “An unbounded cache retains millions of entries that are no longer useful, causing the live set to grow across load cycles” is a much more specific hypothesis that can be tested with histograms, heap dumps, and trends.

Always preserve a **timeline**. A thread dump at 10:01, a GC pause at 10:01:03, and a latency spike at 10:01:03 provide stronger evidence than three artifacts with no temporal relationship.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="evidence-quality-and-cost">Evidence Quality, Cost, and Intrusiveness</a>

<details>
<summary>Click for details</summary>

Diagnostic evidence differs not only in what it reveals but also in **collection cost** and in how much the act of collecting it can perturb the target process.

A useful mental model is a spectrum:

```text
process / flag information
        ↓
counters / logs / lightweight events
        ↓
thread dump
        ↓
class histogram
        ↓
heap dump or another very large artifact

usually lighter -------------------- usually heavier
```

This is only a guide. Actual impact depends on the JVM, number of threads, heap size, collector, machine load, and the specific command. For many diagnostic commands, `jcmd <pid> help <command>` reports an impact level.

For every evidence source, ask:

- **What question can it answer?** A thread dump is strong for execution and lock state but does not tell you which objects dominate the heap.
- **Is it a snapshot or a timeline?** A histogram is a snapshot; GC logs and JFR preserve events over time.
- **Can it pause or materially load the JVM?** Some heap operations can require a safepoint or full GC, and a heap dump can produce substantial I/O.
- **Is there enough storage for the artifact?** Heap dumps can be very large.
- **Can observation change the behavior?** On a process already constrained by CPU or I/O, a heavy operation can worsen the incident.

Evidence quality matters as much as detail. A technically correct snapshot taken after a restart can be useless because the failure state has disappeared. A modest continuous log can be more valuable than a very detailed artifact captured at the wrong time.

Start with low-impact evidence that can separate plausible hypotheses, then increase depth when necessary. More data is not automatically better evidence.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnostic-readiness-principle">Preparing Diagnostic Readiness Before an Incident</a>

<details>
<summary>Click for details</summary>

Important runtime evidence can be transient. Restarting may release the heap, remove lock state, terminate threads, discard crash context, or rotate away useful logs. Diagnostic capability therefore needs to be prepared **before** the incident.

A useful operational baseline commonly includes:

- knowing the exact JDK/JVM version and startup flags;
- having retention and rotation rules for JVM and application logs;
- considering GC logging appropriate to the environment;
- configuring `-XX:+HeapDumpOnOutOfMemoryError` and `-XX:HeapDumpPath=...` when the storage budget allows it;
- considering continuous JFR when pre-incident event history is valuable;
- ensuring operators can access the correct process with the required permissions;
- knowing where artifacts will be written and whether enough disk space exists;
- keeping timestamps comparable across application logs, JVM logs, and monitoring systems.

Oracle's JDK troubleshooting guidance explicitly recommends preparing heap dumps for `OutOfMemoryError`, GC logging, and continuous flight recording as useful troubleshooting readiness measures. This does not mean that every production JVM should enable every facility with the same settings. Collection policy should reflect storage, data-handling, and runtime-cost constraints.

A small pre-incident checklist prevents a large amount of wasted time:

```text
Which JVM?
Which PID/container?
Where are the JDK tools?
Who can attach?
Where will artifacts be written?
How much disk is available?
Which timezone do timestamps use?
What evidence disappears after restart?
```

Readiness turns troubleshooting into a repeatable workflow. During the incident, time can be spent interpreting evidence instead of discovering that the process cannot be accessed or that the filesystem cannot hold the required dump.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnostic-scope-boundaries">Boundaries with JVM Internals, Instrumentation, and Observability</a>

<details>
<summary>Click for details</summary>

Runtime Diagnostics sits next to several domains, so it is important to know **where deeper ownership moves elsewhere**.

**JVM internals** explain the underlying mechanism. When a GC log shows long pauses, diagnostics helps establish when they happened, how often they occurred, and whether they align with the symptom. Collector algorithms, allocation mechanics, or JIT strategy belong to the JVM module.

**Concurrency** owns semantics and correctness. A thread dump can show a held monitor, many `BLOCKED` threads, or a deadlock cycle. The Java Memory Model, happens-before rules, synchronization design, and the correctness of a fix belong to Java Concurrency.

**Instrumentation** actively changes or augments runtime behavior, for example through Java Agents and bytecode transformation. This module primarily uses observation capabilities already exposed by the JDK/JVM. When the problem requires transforming classes to insert probes, the work has crossed into the Instrumentation boundary.

**Observability platforms** provide large-scale collection and analysis of metrics, logs, traces, and often vendor-specific runtime data. This module focuses on JDK/JVM diagnostic primitives and reasoning from evidence rather than on telemetry-platform architecture.

Keep this relationship in mind:

```text
JVM / Concurrency
→ explain mechanisms

Runtime Diagnostics
→ observe evidence and troubleshoot

Instrumentation
→ actively modify or insert observation

Observability platform
→ collect, store, and analyze telemetry at system scale
```

Real incidents often cross these boundaries. You might detect a problem in a metric, use `jcmd` to capture thread evidence, move to JVM knowledge to understand GC behavior, and then use Concurrency knowledge to repair locking. The boundary exists so each topic retains clear ownership, not because production troubleshooting stays inside one box.

</details>

- [Quay lại đầu trang](#back-to-top)
