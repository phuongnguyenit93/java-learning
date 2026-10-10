<a id="back-to-top"></a>

# Choosing Reactive: Use Cases and Boundaries

## Menu
- [Designing a Signal Flow from Source to Consumer and Termination](#reactive-end-to-end-example)
- [Reactive versus Sequential Processing and Explicit Callbacks](#reactive-compare-callback)
- [Asynchrony, Non-Blocking Behavior, Parallelism, and Scheduling Are Different](#reactive-async-parallel-boundary)
- [Costs of Tracing, Failures, Resources, and Complexity](#reactive-debug-complexity)
- [Boundaries with Project Reactor, Spring WebFlux, RxJava, and Java Flow](#reactive-framework-handoff)
- [Criteria for Choosing Reactive or a Simpler Alternative](#reactive-final-decision)

## <a id="reactive-end-to-end-example">Designing a Signal Flow from Source to Consumer and Termination</a>

<details>
<summary>Click for details</summary>

Design a sensor dashboard: a source emits readings over time, validation rejects impossible values, classification produces alerts, and a consumer updates the display. If the consumer handles 40 samples per second while the source can emit 100, decide **whether demand can limit delivery or buffering, dropping, or throttling is required** before production.

```text
DATA downstream:    sensor → validate → classify → dashboard
CONTROL upstream:   dashboard → its Subscription at classify
                    classify  → its upstream Subscription at validate
                    validate  → its upstream Subscription at source
                    (request/cancel, if supported and propagated)
BUFFERS:            each boundary needs a capacity and overload policy
OUTCOMES:           source completes | source fails | consumer cancels
```

The **data signals travel downstream**, while requests and cancellation **originate downstream and may be coordinated upstream one subscription at a time**; a Processor can translate demand rather than forwarding the same count verbatim. A physical or hot sensor need not slow its production merely because the last Subscriber requested fewer readings: each adapter or intermediate boundary still needs an overload policy. Under Reactive Streams, a Subscriber can `request(3)`, receive two readings, and then get `onComplete` without a third. If the dashboard closes, it can `cancel()` while tolerating signals already in transit.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-compare-callback">Reactive versus Sequential Processing and Explicit Callbacks</a>

<details>
<summary>Click for details</summary>

**Sequential code** excels when all data is available and the task is short enough to trace directly. **Explicit callbacks** can be simplest for a few event handlers with straightforward cleanup. Reactive composition is useful when several sources and stages require **coordinated error propagation, cancellation, and demand** over an ongoing lifecycle.

Every approach has costs: reactive adds concepts and can make causality across asynchronous stages harder to inspect. A long pipeline is not maintainable solely because it replaces several callback functions. Compare complete lifecycles, not line counts in a toy example.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-async-parallel-boundary">Asynchrony, Non-Blocking Behavior, Parallelism, and Scheduling Are Different</a>

<details>
<summary>Click for details</summary>

**Asynchronous** means results need not arrive within the initiating call. **Non-blocking** means a step does not tie up a thread waiting in the usual blocking manner. **Parallel** means multiple pieces of work actually progress concurrently. **Scheduling** determines where and when execution runs. These concepts are related, but none is a synonym for the others.

A pipeline may be entirely **synchronous on one thread** while retaining a reactive representation; conversely, a reactive API may be used with blocking I/O in an inappropriate stage. Reactive Streams requires **non-blocking backpressure and responsiveness of participants**, not automatic dispatch of every operator to a different thread.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-debug-complexity">Costs of Tracing, Failures, Resources, and Complexity</a>

<details>
<summary>Click for details</summary>

When the 33°C alarm disappears, the reading might have been filtered incorrectly, dropped under overload, not yet requested, delivered after cancellation, or lost to a terminal failure. Instrument **data and signal boundaries**: before/after-filter counts, queue depth, outstanding demand, failures, and cancelled subscriptions.

Debugging across asynchronous stages can be harder because a stack trace may show only part of the causal chain. Operational documentation should identify who owns errors, what happens on full buffers, and how resources are observed instead of treating an operator chain as evidence of reliability.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-framework-handoff">Boundaries with Project Reactor, Spring WebFlux, RxJava, and Java Flow</a>

<details>
<summary>Click for details</summary>

**Project Reactor** supplies types and operators for composing flows, **RxJava** supplies several observable and flow abstractions, **Spring WebFlux** applies reactive techniques to web applications, and **Java Flow** contains standard Java publisher/subscriber interfaces. They are libraries, frameworks, or APIs, not definitions of the reactive paradigm.

A WebFlux application can use Reactive Streams and still suffer when application code blocks the wrong threads. Study schedulers, `Mono/Flux`, `Flow.Publisher`, HTTP runtime behavior, and adapter compatibility in their own specialist modules once signal and backpressure contracts make sense here. See the [Spring WebFlux Reference](https://docs.spring.io/spring-framework/reference/web/webflux.html).

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-final-decision">Criteria for Choosing Reactive or a Simpler Alternative</a>

<details>
<summary>Click for details</summary>

Reactive is a good candidate when **data arrives continuously or unpredictably over time**, multiple stages need composing, lifecycle/error/cancellation must be explicit, and unequal processing rates demand deliberate handling. Check whether the actual source supports backpressure, who owns buffers, and what loss is permitted.

For averaging twenty already loaded values, a loop or pure function may be much simpler. For a continuously updating sensor dashboard, a reactive flow can be worthwhile with explicit overload/error policies, protocol tests, and resource monitoring. **Choose according to data behavior and operational cost**, not a fashionable framework label.

</details>

- [Back to top](#back-to-top)
