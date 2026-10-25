<a id="back-to-top"></a>

# JDK Diagnostic Sources and Tools

## Menu
- [Identifying the Correct JVM and Process](#target-jvm-identification)
- [A jcmd-First Diagnostic Approach](#jcmd-first-diagnostics)
- [Where jps, jstack, jmap, jinfo, and jstat Fit](#specialized-jdk-utilities)
- [Choosing Tools by Diagnostic Question](#diagnostic-tool-selection)
- [Live Diagnostics, Artifact Collection, and Postmortem Analysis](#live-vs-offline-evidence)
- [Preparing Logs, Dumps, Recordings, and Crash Evidence](#diagnostic-artifact-readiness)
- [Permissions, Runtime Impact, and Collection Timing](#diagnostic-permissions-and-impact)

## <a id="target-jvm-identification">Identifying the Correct JVM and Process</a>

<details>
<summary>Click for details</summary>

The first step in every diagnostic action is proving that you are looking at the **correct JVM**. One host can run many Java processes, containers can have separate PID namespaces, and multiple service instances can have the same main class or JAR name.

Start with:

```bash
jcmd -l
```

or:

```bash
jps -lv
```

`jcmd -l` lists JVMs visible to the tool together with main-class and command-line information. `jps` provides a similar view of instrumented HotSpot JVMs. Do not treat either list as the only source of process identity. Oracle notes that a JVM running in a separate Docker process/namespace might not be visible in the expected way, so operating-system or container tooling may also be required.

Once you have a candidate PID, verify the target:

```bash
jcmd 12345 VM.version
jcmd 12345 VM.command_line
jcmd 12345 VM.flags
jcmd 12345 VM.system_properties
```

Before collecting expensive evidence, answer:

- is this the correct service instance;
- which JDK version/vendor is running;
- does the command line and flag set match the deployment under investigation;
- is this a host PID or a PID inside a container namespace;
- has the process restarted since the symptom occurred?

In orchestrated environments, tie the PID to **service instance + container/pod + host + time**. A file named `thread-dump-12345.txt` without an instance identity and timestamp is easy to misinterpret later.

A stronger artifact name is:

```text
orders-api_pod-7f9c_pid-12345_2026-10-02T08-15-30Z_threads.txt
```

Correct target identification is a very low-cost step with very high value. Every later conclusion is invalid if the evidence came from the wrong JVM.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jcmd-first-diagnostics">A jcmd-First Diagnostic Approach</a>

<details>
<summary>Click for details</summary>

`jcmd` is the general command-line interface for sending **diagnostic commands** to a running JVM. On modern JDKs, Oracle recommends preferring `jcmd` over older utilities such as `jstack`, `jmap`, and `jinfo` when an equivalent `jcmd` operation exists.

A basic workflow is:

```bash
# List JVMs
jcmd -l

# Discover commands supported by the target JVM
jcmd 12345 help

# Inspect syntax and impact for one command
jcmd 12345 help Thread.print

# Common low-level questions
jcmd 12345 VM.version
jcmd 12345 VM.command_line
jcmd 12345 VM.flags
jcmd 12345 VM.system_properties
jcmd 12345 GC.heap_info
jcmd 12345 Thread.print -l
```

The available command set can vary by JVM build and version, so `jcmd <pid> help` against the **actual target** is more reliable than memorizing a static list.

`jcmd` is also an entry point for important diagnostic artifacts:

```bash
# Class histogram
jcmd 12345 GC.class_histogram

# HPROF heap dump
jcmd 12345 GC.heap_dump /diagnostics/heap.hprof

# Traditional thread dump for platform-thread-oriented evidence
jcmd 12345 Thread.print -l

# Virtual-thread-aware dump in Java 21
jcmd 12345 Thread.dump_to_file -format=json /diagnostics/threads.json
```

One tool does not mean one impact level. Oracle documents `VM.flags` as low impact, `Thread.print` as medium impact depending on the number of threads, and `GC.class_histogram` and `GC.heap_dump` as potentially high impact depending on heap size and content.

The correct usage model is:

```text
diagnostic question
    ↓
jcmd <pid> help
    ↓
choose a command that can answer it
    ↓
review impact + prepare output
    ↓
collect evidence
```

`jcmd` is a convenient control surface. It does not replace understanding the artifact produced by each command.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="specialized-jdk-utilities">Where jps, jstack, jmap, jinfo, and jstat Fit</a>

<details>
<summary>Click for details</summary>

The specialized JDK utilities still matter because you will encounter them in runbooks, older documentation, and workflows where their output is convenient. The key is to know **which question each tool answers**.

| Tool | Primary role | Example |
| --- | --- | --- |
| `jps` | List observable HotSpot JVMs in the local VM namespace | `jps -lv` |
| `jstack` | Print Java thread stack traces; `-l` adds lock information | `jstack -l 12345` |
| `jmap` | Heap histograms, heap dumps, and selected heap/class-loader statistics | `jmap -histo 12345` |
| `jinfo` | Inspect system properties and VM flags; some flags may be manageable depending on the JVM | `jinfo -flags 12345` |
| `jstat` | Sample HotSpot performance counters over time | `jstat -gcutil 12345 1000 10` |

Oracle marks `jstack`, `jmap`, and `jinfo` as experimental/unsupported in JDK 21 tool documentation and recommends `jcmd` for equivalent operations when available. For example:

```text
jstack -l <pid>
≈ when appropriate, prefer jcmd <pid> Thread.print -l

jmap -histo <pid>
≈ when appropriate, prefer jcmd <pid> GC.class_histogram

jmap -dump:format=b,file=heap.hprof <pid>
≈ when appropriate, prefer jcmd <pid> GC.heap_dump heap.hprof
```

`jstat` has a somewhat different role. It reads HotSpot instrumentation counters and is useful for observing **trends**:

```bash
jstat -gcutil 12345 1000 10
```

This command takes 10 samples at one-second intervals. Columns describing GC counts, GC time, and region/generation utilization can show that runtime behavior is changing. These counters are implementation-oriented, however, so a single `jstat` column should not be promoted directly into a root-cause claim without correlating it with the collector, logs, and the actual symptom.

`jps` also deserves caution. The local VM identifier often matches the operating-system PID, but automation should not assume identical process visibility across hosts and containers.

Treat these utilities as specialized windows into the JVM, with `jcmd` as the preferred general diagnostic surface for modern HotSpot.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnostic-tool-selection">Choosing Tools by Diagnostic Question</a>

<details>
<summary>Click for details</summary>

Tool selection should start from the **question**, not from the name of a command.

| Question | First evidence to consider |
| --- | --- |
| Which JVM is running and with which options? | `jcmd -l`, `VM.version`, `VM.command_line`, `VM.flags` |
| Is the application hung or are requests not making progress? | Repeated `Thread.print`; virtual-thread-aware dump when needed |
| Is there evidence of deadlock or lock contention? | Thread dumps with lock information, compared across snapshots |
| Which object types occupy the heap? | `GC.class_histogram` |
| Do I need detailed object-retention relationships? | Heap dump |
| How are heap/GC signals changing over time? | GC logs, `jstat`, JFR |
| Is process memory growing while the Java heap does not explain it? | NMT if enabled; continue in Native Memory Diagnostics |
| Do I need a timeline of CPU, allocation, lock, or I/O events? | JFR |

The same symptom can require more than one source. “Memory is growing” should first be split:

```text
RSS/process memory increases
        ↓
Is the Java heap increasing?
  ├─ yes → histogram / heap dump / GC evidence
  └─ no  → native-memory evidence / OS evidence
```

A common mistake is to capture a heap dump merely because process memory is high. A heap dump describes the Java heap. If growth comes from JVM-native structures or a native library, that artifact may not answer the question.

Likewise, one thread dump is not a profiler. It can show where threads are at one instant, but if the question is “which method consumed CPU over the last 20 minutes,” sampling or event data such as JFR is a better fit.

Use this selection rule:

1. Write the question as something data can answer.
2. Decide whether you need a snapshot or a timeline.
3. Choose the smallest artifact that contains enough information.
4. Check impact and permissions.
5. Escalate to heavier evidence only when current data cannot separate the hypotheses.

This is why the module begins with diagnostic reasoning before introducing individual tools.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="live-vs-offline-evidence">Live Diagnostics, Artifact Collection, and Postmortem Analysis</a>

<details>
<summary>Click for details</summary>

Not all diagnostic data is analyzed immediately on the JVM that is experiencing the incident. Three modes are common.

**1. Live diagnostics**

You query the running JVM and receive immediate output:

```bash
jcmd 12345 VM.flags
jcmd 12345 Thread.print -l
jcmd 12345 GC.heap_info
jstat -gcutil 12345 1000 10
```

The benefit is current state. The limitation is that state can change quickly and some operations have meaningful runtime impact.

**2. Artifact collection for later analysis**

You ask the JVM to write data to a file:

```bash
jcmd 12345 GC.heap_dump /diagnostics/heap.hprof
jcmd 12345 Thread.dump_to_file -format=json /diagnostics/threads.json
jcmd 12345 JFR.dump filename=/diagnostics/incident.jfr
```

The artifact can be moved to an analysis machine, attached to an incident record, or compared later. Protect it carefully because heap dumps and recordings can contain application data.

**3. Postmortem evidence**

Once the JVM has terminated, live commands are no longer possible. Investigation then depends on artifacts that already exist: fatal-error logs, core dumps, heap dumps, GC/application logs, and flight recordings. That workflow is covered in depth in the Crash/Postmortem chapter.

The connection between all three modes is **time**. If a failure lasts only 30 seconds, a heap dump captured ten minutes later may describe a different state. Record a timestamp, PID, host/container identity, and symptom window for each artifact.

Before restarting a JVM, ask:

```text
Which evidence exists only in the current process?
Which evidence is already durable?
What will restart destroy?
```

In a real incident, preserving the right evidence is often as important as knowing how to read a tool's output.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnostic-artifact-readiness">Preparing Logs, Dumps, Recordings, and Crash Evidence</a>

<details>
<summary>Click for details</summary>

Diagnostic readiness at the tool level means that **artifacts can be created at the right time, in the right place, and remain usable after the incident**.

An operational design should decide in advance:

- a directory dedicated to diagnostic artifacts;
- storage limits and cleanup/retention behavior;
- write permissions for the JVM process;
- a filename convention containing service, instance, PID, and timestamp;
- GC-log rotation;
- the heap-dump location for `OutOfMemoryError`;
- a continuous-recording policy when JFR is used;
- how large artifacts will be moved off the host without saturating disk or network during an incident.

Example startup options:

```text
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/myapp/heapdump.hprof
-Xlog:gc*:file=/var/log/myapp/gc.log:time,uptime,level,tags:filecount=5,filesize=20M
```

These demonstrate the shape of the configuration; paths and retention values must fit the deployment. If several JVMs write to one fixed filename, the design must also prevent collisions.

Heap dumps require particular planning. Oracle labels `GC.heap_dump` as high impact, and the resulting file can be very large. Before capturing one, verify:

```text
free disk
→ output path
→ permission
→ acceptable pause/I/O impact
→ how the artifact will be protected and transferred
```

GC logs and JFR are valuable because they preserve **history before the failure**, while thread dumps and histograms are commonly collected after the symptom has already appeared.

Readiness does not mean enabling every option. It means having a minimal evidence set that matches important failure modes and knowing exactly how to collect deeper evidence when required.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnostic-permissions-and-impact">Permissions, Runtime Impact, and Collection Timing</a>

<details>
<summary>Click for details</summary>

Diagnostic commands operate close to the target process, so two questions always travel together: **are you allowed to attach**, and **is this operation safe at this moment**?

According to the JDK 21 documentation, `jcmd` must run on the same machine as the target JVM and normally requires the same effective user and group identity that launched the JVM. Containers, PID namespaces, security policies, and whether the image contains a full JDK can all affect process visibility and attach behavior.

Before running a command:

1. Confirm the PID and service instance.
2. Run `jcmd <pid> help <command>` to verify support and inspect its documented impact.
3. Check the output path and write permissions when a command creates a file.
4. Check disk and I/O headroom.
5. Decide whether the current symptom window is a state that must be preserved first.

Examples of documented HotSpot JDK 21 impact:

- `VM.flags`: low impact;
- `Thread.print`: medium, depending on the number of threads;
- `GC.class_histogram`: high, depending on heap size/content;
- `GC.heap_dump`: high, depending on heap size/content, and it can request a full GC unless the relevant option is used.

An impact label is not an SLA. “Medium” on a JVM with a few dozen threads is different from a process with a huge thread population; “high” on a 512 MB heap is different from a heap measured in tens of gigabytes.

Avoid broadcasting commands simply for convenience. `jcmd 0 <command>` can send a command to all available JVMs; doing that with a heavy operation can create an avoidable operational incident. Target the intended PID.

Finally, diagnostic artifacts can contain sensitive application data. Heap dumps contain object contents, system properties can reveal configuration, and thread stacks can expose request/task names. Access, storage, and sharing of diagnostic data should follow the same production-data controls used elsewhere in the system.

</details>

- [Quay lại đầu trang](#back-to-top)
