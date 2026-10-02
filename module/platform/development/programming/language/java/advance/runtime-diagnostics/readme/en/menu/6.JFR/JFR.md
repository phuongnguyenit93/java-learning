<a id="back-to-top"></a>

# Event and Performance Diagnostics with Java Flight Recorder

## Menu
- [What JFR Is and Why Diagnostics Needs an Event Timeline](#jfr-diagnostics-purpose)
- [The Flight Recording Lifecycle](#recording-lifecycle)
- [Recording Settings, Duration, and Overhead](#recording-settings-and-overhead)
- [The JFR Event Model and Selecting Useful Evidence](#jfr-event-model)
- [Analyzing CPU Samples, Threads, and Locks](#cpu-thread-lock-analysis)
- [Analyzing Allocation, GC, and I/O Events](#memory-gc-io-analysis)
- [Continuous Recording and After-the-Fact Investigation](#continuous-recording-and-postmortem)
- [The jfr CLI, JDK Mission Control, and Tool Boundaries](#jfr-cli-and-jmc-boundary)

## <a id="jfr-diagnostics-purpose">What JFR Is and Why Diagnostics Needs an Event Timeline</a>

<details>
<summary>Click for details</summary>

Thread dumps, heap histograms, and NMT summaries are point-in-time snapshots. They are valuable, but they can miss problems that last only seconds or require understanding the **sequence before, during, and after** an incident.

Java Flight Recorder (JFR) is the JDK/HotSpot event-recording mechanism. It captures runtime and application events on a timeline with overhead designed to be low enough for many production scenarios.

JFR is useful for questions such as:

- which code paths coincide with a CPU spike?
- what lock activity preceded a latency increase?
- when did allocation begin to grow?
- do GC, I/O, and thread activity line up in the same interval?
- are virtual threads being pinned or failing to submit?

Mental model:

~~~text
snapshot tool
→ state at one point in time

JFR
→ timestamped event stream with duration, context, and optional stacks
~~~

JFR does not automatically produce a root cause; it provides a contextual timeline for testing hypotheses.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="recording-lifecycle">The Flight Recording Lifecycle</a>

<details>
<summary>Click for details</summary>

A recording has a basic lifecycle:

~~~text
configure
   ↓
start
   ↓
record events
   ↓
dump / stop
   ↓
analyze
~~~

Start at JVM launch:

~~~text
java -XX:StartFlightRecording=filename=app.jfr,settings=default -jar app.jar
~~~

or on a running JVM:

~~~text
jcmd <pid> JFR.start name=incident settings=default
jcmd <pid> JFR.check
jcmd <pid> JFR.dump name=incident filename=incident.jfr
jcmd <pid> JFR.stop name=incident filename=incident-final.jfr
~~~

Dumping does not have to stop the recording, which is useful when you need an evidence window while continuous recording remains active.

Record the generated name/ID when starting a recording, especially when multiple recordings may exist in one JVM.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="recording-settings-and-overhead">Recording Settings, Duration, and Overhead</a>

<details>
<summary>Click for details</summary>

The JDK includes common JFR configurations:

- **default.jfc**: a low-overhead event set suitable for continuous recording;
- **profile.jfc**: collects more data for shorter, deeper profiling sessions.

`default.jfc` is designed for low overhead and continuous recording. Do not assume one fixed `JFR.start` impact label across all JDK 21 updates: the Oracle Java 21 man page describes the command as Low impact, while some current JDK 21 update builds (for example 21.0.9) report Medium because recording settings can range from low to high impact. Treat `jcmd <pid> help JFR.start` on the **target JVM** as the runtime contract. Real overhead still depends on enabled events, thresholds, stack traces, and workload.

Important choices include:

- recording duration versus continuous operation;
- disk=true/false;
- maxage and maxsize for bounded retention;
- event thresholds;
- whether stack traces are needed;
- enable GC-root path collection only when leak analysis requires it, because the work can add significant cost or pauses. The option spelling is version/tool-surface sensitive: the Oracle Java 21 `jcmd` man page documents `path-to-gc-root` (singular), while some current JDK 21 update builds such as 21.0.9 expose `path-to-gc-roots` (plural). Always check `jcmd <pid> help JFR.start` on the target JVM before using it.

Start with low-overhead/default evidence and increase detail only when the current question remains unanswered.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jfr-event-model">The JFR Event Model and Selecting Useful Evidence</a>

<details>
<summary>Click for details</summary>

JFR records **typed events**, not one uniform text log. Event types represent behaviors such as CPU samples, allocation, GC phases, file/socket I/O, monitor contention, thread parks, exceptions, and virtual-thread activity.

An event may contain:

- timestamp;
- duration or instant semantics;
- related thread;
- event-specific fields;
- a stack trace when configured.

Do not enable every event simply because it exists. Start from the hypothesis:

~~~text
high CPU
→ execution / CPU sample events

lock contention
→ monitor / park / thread events

memory growth
→ allocation / GC / old-object evidence

I/O latency
→ socket / file events
~~~

For Java 21 virtual threads:

- `jdk.VirtualThreadPinned` is enabled by default with a 20 ms threshold;
- `jdk.VirtualThreadSubmitFailed` is enabled by default;
- `jdk.VirtualThreadStart` and `jdk.VirtualThreadEnd` are disabled by default and should be enabled when lifecycle visibility is actually needed.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cpu-thread-lock-analysis">Analyzing CPU Samples, Threads, and Locks</a>

<details>
<summary>Click for details</summary>

For CPU, threads, and locks, JFR gives a time-based view that complements thread snapshots.

**CPU samples** show stacks observed statistically while execution is sampled. They are evidence of hot paths, not nanosecond-perfect accounting.

**Thread and lock events** can reveal:

- park or block duration;
- monitor contention;
- synchronization context;
- virtual-thread pinning;
- whether contention overlaps the latency interval.

Typical flow:

~~~text
CPU/latency spike time
        ↓
select the matching JFR interval
        ↓
top execution stacks
        +
lock/park events
        ↓
correlate with request/log context
~~~

If the remaining question is about happens-before, lock correctness, or synchronization design, hand off to the Concurrency curriculum. JFR supplies runtime evidence, not the full correctness model.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="memory-gc-io-analysis">Analyzing Allocation, GC, and I/O Events</a>

<details>
<summary>Click for details</summary>

JFR also connects three areas that are often investigated separately: **allocation, GC, and I/O**.

For memory:

- allocation events identify allocating code paths;
- GC events place collection activity on the same timeline;
- old-object sampling can support leak analysis when configured appropriately.

For I/O:

- file/socket events can expose duration and call context;
- correlate I/O with thread parks and request latency to see where time is spent.

High allocation rate is not automatically a memory leak. An application can allocate quickly while GC reclaims objects normally. A leak hypothesis requires retention or trend evidence.

Likewise, frequent GC events are a signal for further JVM/heap analysis; JFR does not replace collector mechanics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="continuous-recording-and-postmortem">Continuous Recording and After-the-Fact Investigation</a>

<details>
<summary>Click for details</summary>

Continuous recording turns JFR into a “black box recorder” for rare failures.

A common pattern:

~~~text
JVM starts
→ JFR runs continuously with default settings
→ maxage/maxsize bounds retention
→ incident occurs
→ dump before restart
→ analyze the period immediately before the incident
~~~

This is valuable because the exact failure time does not have to be predicted in advance.

Example startup configuration:

~~~text
-XX:StartFlightRecording=name=continuous,settings=default,disk=true,maxage=2h,maxsize=512m
~~~

Capacity and retention still matter. Recordings may expose class/method names, file paths, application context, or custom event data, so treat them as production diagnostic artifacts.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jfr-cli-and-jmc-boundary">The jfr CLI, JDK Mission Control, and Tool Boundaries</a>

<details>
<summary>Click for details</summary>

The JDK includes the **jfr CLI** for inspecting recordings without a GUI:

~~~text
jfr summary recording.jfr
jfr print recording.jfr
jfr print --events <event-list> recording.jfr
jfr metadata recording.jfr
~~~

The CLI works well for automation, quick inspection, and reproducible filtering.

**JDK Mission Control (JMC)** provides richer visualization, time-range selection, automated analysis, hot methods, memory views, and event correlation. JMC is companion tooling in the JDK ecosystem and should not be assumed to be bundled with every runtime installation.

Keep the roles separate:

- JFR = event-recording mechanism;
- jcmd / StartFlightRecording = recording control;
- jfr CLI = file inspection;
- JMC = visualization and analysis UI.

Learning JFR does not require learning every JMC feature. This module focuses on using recordings as diagnostic evidence.

</details>

- [Quay lại đầu trang](#back-to-top)
