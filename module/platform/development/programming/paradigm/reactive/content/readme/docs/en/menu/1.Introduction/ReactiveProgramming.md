<a id="back-to-top"></a>

# Reactive Programming: Streams and Change over Time

## Menu
- [Reactive Programming: Concept and Scope](#reactive-what)
- [Motivation: Time-Varying Data and Disconnected Callbacks](#reactive-why)
- [Immediate Values versus Events Arriving over Time](#reactive-values-vs-streams)
- [Starting Point: Transformations, Intent, and Asynchrony](#reactive-foundation-bridge)
- [Source, Signal Flow, Processing Stages, and Consumer](#reactive-solution)
- [Reactive Streams: A Contract, Not the Entire Paradigm](#reactive-streams-boundary)
- [Boundaries between the Paradigm, Libraries, and Frameworks](#reactive-framework-boundary)
- [From Data Propagation to Subscription, Backpressure, and Termination](#reactive-learning-path)

## <a id="reactive-what">Reactive Programming: Concept and Scope</a>

<details>
<summary>Click for details</summary>

Reactive Programming is a paradigm that organizes logic around **data or event streams that change over time** and the propagation of those changes through processing stages.

Instead of always asking for a value immediately, reactive code often describes what should happen when new data or events arrive.

**Running example:** a sensor reports temperatures over time, while a dashboard filters implausible readings and alerts when the temperature crosses a threshold. The input is not a fully available list: readings may continue to arrive while the application runs. Reactive programming organizes **the description, transformation, and propagation of these signals** rather than only computing one immediate answer.

This is a model for responding to changing data. It does not require a particular framework, automatically create new threads, guarantee every step is non-blocking, or define one universal flow-control API. Specific emission limits arise only under **contracts that support backpressure**.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-why">Motivation: Time-Varying Data and Disconnected Callbacks</a>

<details>
<summary>Click for details</summary>

Systems with continuous events, asynchronous I/O, or producers and consumers operating at different speeds need a model that represents data flow and timing explicitly.

Manual callbacks can solve individual cases, but long flows can make composition, error propagation, and cancellation difficult to reason about.

Independent callbacks for `onTemperature`, `onSensorError`, and `onDisconnect` can scatter filtering, failures, and cleanup across unrelated handlers. Add a ten-second aggregation window and it becomes harder to see which callbacks must terminate and in what order.

A flow model brings these concerns into **one processing story**: who produces values, how they are transformed, how recipients express interest, and what happens on failure or cancellation. Explicit callbacks remain effective for small tasks; reactive composition helps when sources, stages, and lifecycles grow.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-values-vs-streams">Immediate Values versus Events Arriving over Time</a>

<details>
<summary>Click for details</summary>

`temperature = 27` is a value available now. A stream might deliver `27` at 09:00, `29` at 09:01, and `33` at 09:02—or never deliver another value. Both **the timing and possibly unknown length** are essential parts of the problem.

```text
09:00    09:01     09:02         ...
  27 ----- 29 ------ 33 ---------?   → alert if > 30
```

Filtering a fixed list and filtering future events can use the same predicate, but only the ongoing flow needs to specify when listening starts, where errors go, and how the recipient stops.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-foundation-bridge">Starting Point: Transformations, Intent, and Asynchrony</a>

<details>
<summary>Click for details</summary>

Start with a **transformation function** taking an input to an output, and with the idea that processing stages can be described before running them. This connects briefly to functional and declarative programming: `filter → transform → consume` says **what to do with each signal** but does not yet say when a signal will arrive.

**Asynchrony** means a result may arrive after the current call has returned; it does not necessarily mean parallel execution on multiple threads. Keep three questions separate: **what** is the value, **when** does it arrive, and **how many** items may be delivered under a particular demand contract?

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-solution">Source, Signal Flow, Processing Stages, and Consumer</a>

<details>
<summary>Click for details</summary>

```text
source
  ↓
stream of signals
  ↓
transform / filter / combine
  ↓
consumer
```

Important concerns include push/pull models, asynchronous boundaries, backpressure, cancellation, and error signaling.

For our sensor, the **source/producer** creates readings, the **filter/transform** stages reject impossible values and normalize units, and the **consumer** updates the dashboard. Besides data, the overall interaction needs an account of errors, successful completion, and cancellation. Producing and consuming are different roles even when both execute on one thread.

```text
sensor: 27, -300, 33
  → filter(valid) → 27, 33
  → classify         normal, alarm
  → dashboard
```

This diagram alone neither starts emission nor guarantees flow control. Those are reasons to study subscription and then backpressure separately.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-streams-boundary">Reactive Streams: A Contract, Not the Entire Paradigm</a>

<details>
<summary>Click for details</summary>

Reactive Streams is a **specific interoperability contract for asynchronous stream exchange with non-blocking backpressure**, not the definition of all reactive programming. It defines `Publisher`, `Subscriber`, `Subscription`, and `Processor`, along with `request(n)`, data-signal ordering, and termination rules.

An event API that pushes everything as events arrive may be reactive in style while offering neither `request(n)` nor Reactive Streams compliance. Do not apply Reactive Streams demand requirements to every library using the word reactive. See the [Reactive Streams JVM Specification](https://github.com/reactive-streams/reactive-streams-jvm).

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-framework-boundary">Boundaries between the Paradigm, Libraries, and Frameworks</a>

<details>
<summary>Click for details</summary>

**RxJava** offers types such as `Observable`, `Flowable`, `Single`, `Maybe`, and `Completable`. These are particular library abstractions, not mandatory ingredients of reactive programming. Spring WebFlux implementation mechanics belong to `framework/spring-framework/reactive`.

Distinguish three layers: the **paradigm** explains event/flow-oriented reasoning; a **library** offers composable types and operations, such as Reactor or RxJava; a **framework** applies such ideas within an application domain, such as Spring WebFlux for HTTP. Reactive Streams can provide a shared contract between compatible libraries.

`Flux`, `Mono`, `Observable`, or a WebFlux controller is not the definition of all reactive programming, and observable abstractions do not necessarily share the same backpressure semantics. Scheduling, Java Flow APIs, and HTTP runtime behavior belong to their specialist modules. See the [Reactor Core Reference](https://projectreactor.io/docs/core/release/reference/gettingStarted.html).

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-learning-path">From Data Propagation to Subscription, Backpressure, and Termination</a>

<details>
<summary>Click for details</summary>

We will trace the sensor's data **from source to recipient**, including filtering, transformations, combination, and subscribing. When a source can outpace its recipient, the backpressure chapter explores queues, rate mismatch, and demand-based control.

With that motivation established, we will study the **Reactive Streams contract** and its roles and signals. Next we trace completion, failure, and cancellation, including in-flight events. An end-to-end case then helps determine when these techniques justify their cost.

</details>

- [Back to top](#back-to-top)
