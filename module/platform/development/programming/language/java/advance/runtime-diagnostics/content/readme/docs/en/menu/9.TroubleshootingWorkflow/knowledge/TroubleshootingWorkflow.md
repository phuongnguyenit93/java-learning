<a id="back-to-top"></a>

# End-to-End Troubleshooting Workflow

## Menu
- [Why Diagnostics Needs a Workflow Instead of Random Tool Use](#troubleshooting-workflow-purpose)
- [Classifying the Symptom Before Choosing Evidence](#classify-symptom)
- [Building an Evidence Collection Plan](#build-evidence-plan)
- [Collecting Low-Impact Evidence First](#collect-low-impact-first)
- [Correlating Multiple Signals to Test a Hypothesis](#correlate-multiple-signals)
- [Preserving Important State Before Restarting](#preserve-before-restart)
- [Common Diagnostic Reasoning Pitfalls](#common-diagnostic-pitfalls)
- [Stopping Criteria and Handoffs to Specialized Modules](#escalation-and-handoff)

## <a id="troubleshooting-workflow-purpose">Why Diagnostics Needs a Workflow Instead of Random Tool Use</a>

<details>
<summary>Click for details</summary>

A runtime incident is not solved efficiently by “running every tool and looking for something strange.” That produces data without a disciplined argument. A useful workflow forces the investigation through a sequence:

~~~text
What is the symptom?
        ↓
What question must be answered next?
        ↓
What is the cheapest evidence that can answer it?
        ↓
Which hypothesis does the result support or falsify?
        ↓
Do we need more evidence, a fix, or a handoff?
~~~

This structure reduces unnecessary invasive collection, limits confirmation bias, and creates a reasoning trail another engineer can review.

Tools are only mechanisms for collecting evidence. Every collection step should have a concrete question behind it.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classify-symptom">Classifying the Symptom Before Choosing Evidence</a>

<details>
<summary>Click for details</summary>

Before choosing a tool, classify the symptom broadly:

| Symptom | Useful first evidence |
| --- | --- |
| requests hang or stop progressing | thread dumps, JFR thread/lock events |
| high CPU | repeated thread evidence, JFR CPU samples |
| growing Java heap | GC logs, histograms, heap/JFR memory evidence |
| growing RSS with stable heap | NMT, OS memory view, native handoff |
| short latency spikes | JFR timeline, application-log correlation |
| JVM crash | hs_err, core, pre-crash JFR/logs |
| need remote runtime state | MXBeans/JMX |

This is not a deterministic lookup table. One symptom can have many causes. Classification simply chooses the first **evidence family** that can separate likely causes at reasonable cost.

Always record the time window, host/container, PID, Java version, and recent changes. Evidence from the wrong process or wrong interval is often worse than no evidence.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="build-evidence-plan">Building an Evidence Collection Plan</a>

<details>
<summary>Click for details</summary>

An evidence plan should be expressible as a testable question:

~~~text
Hypothesis:
Requests are stuck because of lock contention.

Need to know:
Do the same threads remain BLOCKED on the same lock over time?

Evidence:
Three thread dumps ten seconds apart plus JFR lock events.

Decision:
If owners and blocked stacks repeat → investigate concurrency.
If not → examine I/O or downstream latency.
~~~

A good plan states the current hypothesis, required data, expected collection impact, timing/repetition, interpretation criteria, and the next step for both a positive and a negative result.

That turns troubleshooting into a falsifiable process instead of an unstructured sequence of commands.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collect-low-impact-first">Collecting Low-Impact Evidence First</a>

<details>
<summary>Click for details</summary>

Prefer evidence in the rough order **low impact → focused → invasive**:

~~~text
existing logs / metrics
        ↓
jcmd information / thread snapshot / JMX
        ↓
default or continuous JFR
        ↓
histogram / focused recording
        ↓
heap dump / core dump / invasive attach
~~~

Cost is not only CPU overhead. Consider pause time, disk/network volume, risk of worsening memory pressure, data sensitivity, operator time, and whether unique evidence will disappear if collection is delayed.

Sometimes a heavy artifact must be captured immediately—for example, before an unavoidable restart or during a rare OOME. “Low impact first” should not destroy evidence that cannot be reproduced.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="correlate-multiple-signals">Correlating Multiple Signals to Test a Hypothesis</a>

<details>
<summary>Click for details</summary>

One signal rarely proves a root cause. Correlate evidence by **the same PID, time window, and hypothesis**.

For high CPU:

- OS/container data confirms the CPU increase is real;
- repeated thread dumps show recurring stacks;
- JFR CPU samples identify hot code paths;
- GC events show whether collection is consuming the CPU;
- deployment/log timelines reveal changes immediately before the spike.

For memory:

- process RSS establishes total growth;
- heap/GC data determines whether Java heap explains it;
- histograms and heap dumps show object populations;
- NMT checks JVM-native categories;
- JFR allocation or old-object evidence helps locate growth sources.

Correlation does not mean putting everything on one dashboard. It means checking whether independent observations fit the same causal story.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="preserve-before-restart">Preserving Important State Before Restarting</a>

<details>
<summary>Click for details</summary>

A restart is a recovery action, not a diagnostic conclusion. When operational constraints allow, ask before restarting:

1. Will important thread or JFR state disappear?
2. Is a heap dump or NMT diff needed?
3. Have fatal/core artifacts been copied from ephemeral storage?
4. Could log rotation remove the relevant interval?
5. Have the old PID/container identity and runtime flags been recorded?

A compact checklist helps:

~~~text
[ ] timestamp + PID/container
[ ] thread evidence
[ ] JFR dump
[ ] memory evidence when relevant
[ ] logs + JVM flags/version
[ ] crash artifacts when present
[ ] copy to persistent storage
~~~

Do not insist on collecting everything during a critical outage. Balance service objectives against the uniqueness and value of the evidence.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-diagnostic-pitfalls">Common Diagnostic Reasoning Pitfalls</a>

<details>
<summary>Click for details</summary>

Common reasoning failures include:

**Treating one snapshot as proof**  
A RUNNABLE thread or one large object class does not establish root cause. Look for repetition, trend, and correlation.

**Confusing a metric with a mechanism**  
90% CPU reports utilization, not the responsible code path. 80% heap usage does not prove a leak.

**Collecting only evidence that supports the current idea**  
Every hypothesis should have a condition that would falsify it.

**Using expensive tools too early**  
Heap/core dumps may increase pause time, disk pressure, and incident risk.

**Losing process/time identity**  
Correct artifact type from the wrong PID or time interval leads to false conclusions.

**Restarting before preserving evidence**  
Many runtime states cannot be reconstructed after restart.

**Crossing ownership boundaries**  
Diagnostics observes and narrows the problem; detailed GC algorithms, JMM semantics, JNI mechanics, or instrumentation internals belong elsewhere.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="escalation-and-handoff">Stopping Criteria and Handoffs to Specialized Modules</a>

<details>
<summary>Click for details</summary>

Stop or hand off an investigation when:

- evidence strongly confirms the hypothesis and the fix belongs to another domain;
- the next question requires semantics Runtime Diagnostics does not own;
- the remaining work depends on a specialized tool/platform;
- additional collection costs more than its expected diagnostic value.

Primary handoffs:

| Evidence points to | Hand off to |
| --- | --- |
| GC/JIT/runtime-memory mechanics | JAVA_JVM |
| locks/happens-before/concurrency correctness | JAVA_CONCURRENCY_FUNDAMENTALS |
| need for active bytecode instrumentation | JAVA_INSTRUMENTATION |
| JNI/FFM/native library or memory | JAVA_NATIVE_INTEROPERABILITY |
| Arthas or tool-specific runtime analysis | the observability tool owner |

A good handoff includes the collected evidence and current hypothesis, not merely “this looks like a JVM issue.” That lets the receiving domain continue the investigation instead of starting over.

</details>

- [Quay lại đầu trang](#back-to-top)
