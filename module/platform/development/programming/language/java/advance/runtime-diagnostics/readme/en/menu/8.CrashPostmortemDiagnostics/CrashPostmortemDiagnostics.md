<a id="back-to-top"></a>

# Crash Artifacts and Postmortem Diagnostics

## Menu
- [When to Move from Live Diagnostics to Postmortem Analysis](#postmortem-diagnostics-purpose)
- [HotSpot Fatal Error Logs and Crash Information](#fatal-error-log)
- [Core Dumps, the Serviceability Agent, and jhsdb](#core-dump-and-jhsdb)
- [Automatic Heap Dump Capture on OutOfMemoryError](#oome-heap-dump)
- [Preserving Logs, Recordings, and Crash Evidence Before Restart](#preserve-logs-and-recordings)
- [Correlating Crash Artifacts to Test a Hypothesis](#correlate-crash-artifacts)
- [When to Hand Off to Native Interoperability](#native-crash-handoff)

## <a id="postmortem-diagnostics-purpose">When to Move from Live Diagnostics to Postmortem Analysis</a>

<details>
<summary>Click for details</summary>

Live diagnostics works while a JVM is healthy enough to answer diagnostic requests. When the process has crashed, has been forcibly terminated, cannot be attached to safely, or must be restarted to restore service, the investigation shifts to **postmortem diagnostics**: analyzing state that was preserved at or before the failure.

The key distinction is:

- live diagnostics can still ask the JVM for new information;
- postmortem analysis can only reason from captured artifacts;
- therefore postmortem quality depends heavily on preparation such as core dumps, fatal-error logs, OOME heap dumps, GC logs, and continuous JFR.

A restart can erase the most useful transient evidence. Before restarting a problematic JVM, consider capturing thread state, a JFR dump, memory evidence, and current logs when doing so is safe and compatible with the service objective.

Postmortem is not a replacement for live diagnostics. It is the continuation of the same evidence-driven process after live state is no longer available or trustworthy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fatal-error-log">HotSpot Fatal Error Logs and Crash Information</a>

<details>
<summary>Click for details</summary>

When HotSpot encounters a fatal VM or native-level failure, it commonly writes a **fatal error log**. The default file name is typically hs_err_pid<PID>.log, and -XX:ErrorFile can configure its location.

A fatal error log brings several evidence layers together:

- the signal or exception that terminated the VM;
- the current thread at the failure point;
- related native and Java stacks;
- VM, heap, GC, and command-line information;
- loaded dynamic libraries;
- operating-system and CPU information;
- platform-dependent register or memory context.

Do not treat the “problematic frame” as automatic proof of root cause. It identifies where the crash surfaced. The real cause may be earlier memory corruption, a violated JNI contract, a faulty native library, or a VM defect.

A useful reading order is: confirm PID/time, inspect the header and problematic frame, classify the owning library, inspect current-thread stacks, review VM flags/environment, then decide whether core-dump or native-level analysis is required.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="core-dump-and-jhsdb">Core Dumps, the Serviceability Agent, and jhsdb</a>

<details>
<summary>Click for details</summary>

A **core dump** is an operating-system snapshot of process memory at a crash or explicit capture point. It preserves far more state than a text log and is valuable for native/VM postmortem analysis.

The JDK's **jhsdb** tools use the Serviceability Agent to inspect HotSpot state from a core dump or, in some modes, a process. Examples include:

~~~text
jhsdb jstack --exe <java-executable> --core <core-file>
jhsdb jmap   --histo --exe <java-executable> --core <core-file>
~~~

Core analysis works best with the matching Java executable/JDK build and appropriate native symbols. `jhsdb` is **experimental and unsupported**. The Java 21 documentation warns that attaching it to a live process **will cause the process to hang** and that the process **will probably crash when the debugger detaches**. Do not use Serviceability Agent attachment for ordinary live diagnostics; prefer jcmd, JFR, or JMX while the JVM is healthy enough to respond.

Core files can also contain sensitive application data because they reflect process memory. Storage, transfer, and retention should be controlled accordingly.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oome-heap-dump">Automatic Heap Dump Capture on OutOfMemoryError</a>

<details>
<summary>Click for details</summary>

For Java-heap exhaustion, an important readiness option is:

~~~text
-XX:+HeapDumpOnOutOfMemoryError
~~~

When an eligible OutOfMemoryError occurs, HotSpot writes an HPROF heap dump without requiring an operator to attach in time. Use -XX:HeapDumpPath to control where that dump is written.

Linux/macOS shell example:

~~~text
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/var/log/java/app-%p.hprof \
     -jar app.jar
~~~

Keep **capture** separate from **analysis**:

- this postmortem chapter owns automatic capture and preservation at failure time;
- the Heap/GC chapter owns analyzing the resulting heap data for memory-growth and leak hypotheses.

Heap dumps can be large and writing them can cause a noticeable pause. Production readiness therefore includes disk capacity, a writable destination, and protection for sensitive data contained in the dump.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="preserve-logs-and-recordings">Preserving Logs, Recordings, and Crash Evidence Before Restart</a>

<details>
<summary>Click for details</summary>

Rare incidents are usually reconstructed from several sources. Before a restart, preserve the evidence that may disappear:

- application logs and JVM/GC logs for the relevant time window;
- a continuous recording or freshly dumped JFR file;
- several thread dumps if the process still responds;
- a heap dump or histogram for a memory problem;
- NMT summary/diff output when NMT was enabled;
- fatal-error logs and core dumps after a crash;
- command line, runtime version, environment, and recent deployment timing.

Oracle's troubleshooting guidance recommends preparing these capabilities before an incident, including core-file readiness when appropriate, HeapDumpOnOutOfMemoryError, continuous Flight Recording, GC logging, and managed remote monitoring.

The operational rule is **preserve first, restart second** when the service objective allows it. Restarting may restore availability while destroying thread state, recording buffers, process memory, and other transient clues.

Treat dumps as production data. Heap and core files can contain credentials, request payloads, PII, or key material, so access and retention often need stronger controls than ordinary logs.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="correlate-crash-artifacts">Correlating Crash Artifacts to Test a Hypothesis</a>

<details>
<summary>Click for details</summary>

Postmortem evidence becomes much stronger when independent artifacts agree.

For a memory incident:

~~~text
OOME or growing process memory
        ↓
Do GC logs show a persistently full heap?
        ↓
Does the heap dump show dominant retained objects?
        ↓
Does JFR identify the allocation source?
        ↓
Does NMT show growth in a JVM native category?
~~~

For a native crash:

~~~text
hs_err problematic frame
        ↓
native stack from the core
        ↓
JFR/log context immediately before failure
        ↓
recent JNI or native-library change
~~~

Artifacts do not have to tell the same story. A disagreement can itself narrow the search: rising RSS with stable heap and NMT may point outside those scopes; a single “hot” stack that does not repeat may simply be a badly timed snapshot.

The goal is a hypothesis that evidence can falsify, not a collection of artifacts selected to support a conclusion already made.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-crash-handoff">When to Hand Off to Native Interoperability</a>

<details>
<summary>Click for details</summary>

Runtime Diagnostics owns enough native evidence handling to determine that the investigation has crossed the Java/native boundary. Once the remaining questions require JNI contracts, native library loading, ABI behavior, pointer/memory lifetime, or FFM mechanics, hand off to **Native Interoperability**.

Typical handoff signals include:

- the fatal log points into an application native library;
- failure occurs in a JNI call or callback;
- the core shows a native stack or memory-corruption evidence;
- process RSS grows while Java heap and JVM-internal NMT data cannot explain it;
- the failure depends on architecture, native symbols, or library versions.

Diagnostics still supplies the input evidence: PID, timestamps, hs_err, core file, stacks, JFR, and loaded-library information. Native Interoperability owns the semantics needed to explain JNI/FFM/native-memory behavior.

Do not hand off merely because a stack contains a native frame. Many valid JDK operations pass through native code; correlate the frame with the actual failure first.

</details>

- [Quay lại đầu trang](#back-to-top)
