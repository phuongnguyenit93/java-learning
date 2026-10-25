<a id="back-to-top"></a>

# Instrumentation Use Cases

## Menu
- [Profiling with Bytecode Instrumentation](#profiling-use-case)
- [Code Coverage](#coverage-use-case)
- [Monitoring and Event Logging](#monitoring-event-logging-use-case)
- [Tracing and Probe Injection](#tracing-probe-use-case)
- [Framework, APM, and Agent-Based Tooling](#framework-agent-use-case)
- [Data Collection and the Runtime Diagnostics Boundary](#diagnostic-boundary)

## <a id="profiling-use-case">Profiling with Bytecode Instrumentation</a>

<details>
<summary>Click for details</summary>

A profiler needs runtime measurements without requiring every application method to contain hand-written timing code.

An agent can:

~~~text
match OrderService.placeOrder
→ inject timestamp at method entry
→ record duration on normal/exceptional exit
→ send samples/counters to profiler runtime
~~~

Overhead is the central trade-off. Timing every method with expensive probes can alter the behavior being measured.

Real profilers often:

- target selected methods/packages;
- sample or aggregate rather than record every detailed event;
- avoid allocation on hot paths;
- use optimized helper runtimes;
- allow probes to be enabled/disabled dynamically.

Instrumentation owns **probe injection**. Statistical analysis, visualization, and full performance methodology are broader profiler/diagnostics concerns.

</details>

- [Back to top](#back-to-top)

---

## <a id="coverage-use-case">Code Coverage</a>

<details>
<summary>Click for details</summary>

A coverage agent injects markers/counters to learn which code executed.

Conceptually:

~~~java
if (condition) {
    // probe A
    process();
} else {
    // probe B
    fallback();
}
~~~

A transformer may place probes at:

- method entry;
- basic blocks;
- branches;
- line-mapping points.

After the test run, a helper runtime maps probe IDs into a coverage report.

The challenge is not only incrementing counters. The agent must preserve:

- useful line/debug attributes;
- valid control flow;
- exception paths;
- a policy for generated/synthetic code;
- correct probe storage across class loaders.

Coverage is a strong example of additive instrumentation: business results should remain unchanged while observation data is added.

</details>

- [Back to top](#back-to-top)

---

## <a id="monitoring-event-logging-use-case">Monitoring and Event Logging</a>

<details>
<summary>Click for details</summary>

Monitoring/APM agents commonly instrument framework boundaries such as:

~~~text
HTTP handler
database client
message consumer
scheduled task
selected business operation
~~~

Probes may record:

- start/end time;
- status/error;
- endpoint/query category;
- correlation metadata;
- counters/histograms.

Event logging can similarly inject hooks where the application emits no event itself.

An important design concern is **cardinality and privacy**. Instrumentation may technically observe arguments/return values, but capturing everything is unsafe. Agents need policies that avoid secrets/PII and prevent high-cardinality labels.

A good monitoring agent improves observability with controlled overhead and boundaries; it does not turn every method into a log statement.

</details>

- [Back to top](#back-to-top)

---

## <a id="tracing-probe-use-case">Tracing and Probe Injection</a>

<details>
<summary>Click for details</summary>

A tracing agent usually injects probes at boundaries that create or propagate spans:

~~~text
incoming HTTP
→ start server span

outgoing HTTP / DB / messaging
→ child span + context propagation

method exit/error
→ finish span
~~~

Instrumentation lets a tracing library integrate with a framework without requiring application code to call the tracing API directly.

Context propagation itself can involve thread-local/context objects, async callbacks, or reactive flows. The transformer inserts hooks; propagation semantics belong to the tracing/runtime library.

A common mistake is instrumenting both a low-level and high-level boundary and producing duplicate spans. Targeting must understand framework layering and select stable semantic boundaries.

</details>

- [Back to top](#back-to-top)

---

## <a id="framework-agent-use-case">Framework, APM, and Agent-Based Tooling</a>

<details>
<summary>Click for details</summary>

Many frameworks and tools use a Java agent as the bootstrap for cross-cutting runtime behavior:

- APM/observability agents;
- test coverage;
- runtime mocking/profiling tools;
- security scanning/protection hooks;
- ORM/framework diagnostics.

A common architecture is:

~~~text
Java Agent
→ discover runtime/framework version
→ choose instrumentation modules
→ install transformers
→ inject calls to agent runtime/helper
→ collect/control behavior
~~~

That explains why production agents contain more layers than a small tutorial:

- matcher registry;
- compatibility/version rules;
- helper injection;
- configuration;
- telemetry pipeline;
- safe disable/rollback.

This module focuses on Java Instrumentation foundations so those systems are understandable without recreating every vendor framework.

</details>

- [Back to top](#back-to-top)

---

## <a id="diagnostic-boundary">Data Collection and the Runtime Diagnostics Boundary</a>

<details>
<summary>Click for details</summary>

Instrumentation and Runtime Diagnostics overlap in practice but own different questions:

~~~text
Instrumentation
→ "how do I inject a probe to collect data?"

Runtime Diagnostics
→ "what does this data/thread dump/JFR/heap evidence say about the incident?"
~~~

Suppose an agent injects timing probes and reports rising p99 latency. Instrumentation explains:

- where the probe was inserted;
- which classes were transformed;
- probe overhead;
- runtime configuration/retransformation.

Runtime Diagnostics continues with:

- is latency caused by CPU, locks, GC, or downstream I/O?
- which thread/JFR evidence confirms the hypothesis?

This boundary prevents Instrumentation from becoming a complete observability course while still explaining why it is a powerful data-collection mechanism.

</details>

- [Back to top](#back-to-top)
