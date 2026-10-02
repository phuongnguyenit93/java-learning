# 📂 README MODULE STRUCTURE (EN)

* **1.MentalModel**
    * [RuntimeDiagnostics](readme/en/menu/1.MentalModel/RuntimeDiagnostics.md)
* **2.JdkDiagnosticTools**
    * [JdkDiagnosticTools](readme/en/menu/2.JdkDiagnosticTools/JdkDiagnosticTools.md)
* **3.ThreadDiagnostics**
    * [ThreadDiagnostics](readme/en/menu/3.ThreadDiagnostics/ThreadDiagnostics.md)
* **4.HeapGcDiagnostics**
    * [HeapGcDiagnostics](readme/en/menu/4.HeapGcDiagnostics/HeapGcDiagnostics.md)
* **5.NativeMemoryTracking**
    * [NativeMemoryTracking](readme/en/menu/5.NativeMemoryTracking/NativeMemoryTracking.md)
* **6.JFR**
    * [JFR](readme/en/menu/6.JFR/JFR.md)
* **7.ManagementJMX**
    * [ManagementJMX](readme/en/menu/7.ManagementJMX/ManagementJMX.md)
* **8.CrashPostmortemDiagnostics**
    * [CrashPostmortemDiagnostics](readme/en/menu/8.CrashPostmortemDiagnostics/CrashPostmortemDiagnostics.md)
* **9.TroubleshootingWorkflow**
    * [TroubleshootingWorkflow](readme/en/menu/9.TroubleshootingWorkflow/TroubleshootingWorkflow.md)

# Runtime Diagnostics

## What This Module Is

Runtime Diagnostics focuses on observing a running JVM, collecting evidence deliberately, and using that evidence to test hypotheses when a system misbehaves. The core idea is **evidence-driven troubleshooting**: move from symptoms to data, from data to hypotheses, and only then toward conclusions.

This module does not reteach all JVM internals, concurrency theory, Java Agents, or a specific observability platform. Those domains are prerequisites or handoff targets when diagnostic evidence requires deeper analysis.

## Why Runtime Diagnostics Exists

Production failures rarely expose their root cause directly. High CPU, rising memory, stalled requests, OutOfMemoryError, or a JVM crash are symptoms. Treating one metric, one thread dump, or one heap dump as proof can easily confuse correlation with causation.

Runtime Diagnostics provides a workflow for:

- preparing diagnostic evidence before an incident occurs;
- choosing tools according to the question being investigated;
- distinguishing point-in-time snapshots from trends;
- correlating thread, heap, native-memory, JFR, JMX, log, and crash evidence;
- considering the runtime impact of diagnostic operations;
- deciding when evidence is sufficient and when deeper domain expertise is required.

## Prerequisites

Learners should already understand:

- JVM execution, memory, allocation, GC, and JIT at the mental-model level from `JAVA_JVM`;
- basic thread lifecycle, synchronization, and the Java Memory Model from `JAVA_CONCURRENCY_FUNDAMENTALS`;
- enough Java Core to read stack traces, exceptions, and resource behavior.

Deep JVM implementation knowledge and vendor-specific observability tooling are not prerequisites.

## Learning Flow

The module follows this sequence:

1. **Runtime diagnostic mindset** — symptoms, evidence, hypotheses, root cause, and evidence cost.
2. **JDK diagnostic sources and tools** — target JVM identification, a `jcmd`-first approach, and diagnostic readiness.
3. **Thread and liveness diagnostics** — thread evidence, deadlock/contention, and platform versus virtual threads.
4. **Heap and GC diagnostics** — heap usage, histograms, heap dumps, GC evidence, and memory-leak hypotheses.
5. **Native Memory Tracking** — investigating memory outside the Java heap with NMT.
6. **Java Flight Recorder** — using event timelines for performance and hard-to-reproduce runtime behavior.
7. **MXBeans and JMX** — observing and managing runtime state through Java management APIs.
8. **Crash and postmortem diagnostics** — preserving and interpreting fatal error logs, core dumps, heap dumps, and recordings.
9. **End-to-end troubleshooting** — combining evidence sources into a workflow with explicit stopping and handoff criteria.

## Boundaries and Handoffs

This module owns **diagnostic evidence and troubleshooting workflow**, not all theory behind that evidence.

- Deeper GC, JIT, and runtime-memory mechanics → `JAVA_JVM`.
- Concurrency correctness, happens-before, and synchronization semantics → `JAVA_CONCURRENCY_FUNDAMENTALS`.
- Java Agents, bytecode instrumentation, and runtime transformation → `JAVA_INSTRUMENTATION`.
- JNI or native-library behavior when crash evidence points to a native boundary → `JAVA_NATIVE_INTEROPERABILITY`.
- Arthas or vendor/tool-specific runtime analysis → the corresponding observability/tool module.
- Metrics, logs, and traces platform architecture → infrastructure observability.

After completing the module, a learner should be able to start from a runtime symptom, build an appropriate evidence plan, collect data with reasonable impact, test hypotheses across multiple sources, and recognize when the investigation must move into a deeper specialized domain.
