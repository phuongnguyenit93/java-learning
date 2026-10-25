<a id="back-to-top"></a>

# Reactive Data Flow: Sources, Signals, and Subscriptions

## Menu
- [Sources, Producers, and Consumers in a Flow](#reactive-source-producer-consumer)
- [Transforming, Filtering, and Combining Signals](#reactive-transform-combine)
- [Push, Pull, and Hybrid Data Delivery](#reactive-push-pull)
- [Composing Propagated Data Processing Stages](#reactive-composition)
- [Subscription: Beginning a Relationship and Receiving Signals](#reactive-subscribe-lifecycle)
- [Emission Timing versus Describing a Flow](#reactive-execution-timing)
- [Tracing Events from Source to Consumer](#reactive-flow-trace)

## <a id="reactive-source-producer-consumer">Sources, Producers, and Consumers in a Flow</a>

<details>
<summary>Click for details</summary>

A **source** originates values; a **producer** presents them to the flow; a **consumer** handles them at the receiving end. A physical sensor is the source, its adapter may be the producer, and a dashboard is the consumer. Simple applications may combine these roles, but distinguishing them clarifies ownership of capacity and resources.

An intermediate stage both receives upstream data and produces downstream data. `sensor → validate → dashboard` describes **the direction of signal propagation**, not a promise that each participant uses a different thread.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-transform-combine">Transforming, Filtering, and Combining Signals</a>

<details>
<summary>Click for details</summary>

A **transformation** can convert Celsius to Fahrenheit, a **filter** can reject physically impossible readings, and a **combination** can enrich a reading with device status. Each stage should have an understandable input/output contract and known error behavior.

```text
raw:     27, -300, 33
valid:   27,       33
alert:   false,    true       (threshold 30°C)
```

One input does not always produce one output: a filter can emit **zero**, while combination may wait for several signals. Those cardinality changes matter when we later count demand and pending work.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-push-pull">Push, Pull, and Hybrid Data Delivery</a>

<details>
<summary>Click for details</summary>

In **push** delivery, a producer initiates emission whenever it has a value, leaving the receiver to keep up or handle overload. In **pull**, a recipient requests data when ready, although the source might not be able to produce it immediately. Some designs coordinate both: the recipient declares **demand**, then the producer emits available items within that allowance.

A sensor emitting every 100 ms can overwhelm a dashboard processing every 500 ms. Demand-based control helps when the producer and relevant intermediate stages respect it. Not every reactive API offers such a mechanism.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-composition">Composing Propagated Data Processing Stages</a>

<details>
<summary>Click for details</summary>

**Composition** builds a longer flow out of clear stages: `validate → normalize → classify → display`. Functional ideas help express transformations, but a reactive flow also tracks **time, error, completion, and cancellation**. A classification function can be pure while updating a UI necessarily has an external effect.

Good composition allows the classification rule to change without rewriting the sensor adapter. It still requires a deliberate error boundary and ownership of any buffered items. This is a connection to functional programming, not an equation between reactive streams and Java Stream APIs.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-subscribe-lifecycle">Subscription: Beginning a Relationship and Receiving Signals</a>

<details>
<summary>Click for details</summary>

A **subscription** is the relationship between an interested consumer and a stream source. Building that relationship does not automatically prove that data was emitted at that moment: some sources initiate work per subscriber while hot sources may already be producing for others.

Under Reactive Streams, `Publisher.subscribe(subscriber)` establishes the interaction and `onSubscribe(subscription)` is the first signal before `onNext/onError/onComplete`. The subscriber then requests data or cancels through the subscription. This order is a particular protocol, not a universal definition of every observable library.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-execution-timing">Emission Timing versus Describing a Flow</a>

<details>
<summary>Click for details</summary>

`source → filter → dashboard` describes a flow; it does not prove the sensor started, or that the filter has run. A **cold** source commonly starts work for each subscription, whereas a **hot** source may already be producing whether or not a particular consumer is listening. The exact behavior depends on the source and library.

Imagine subscriber A joins at 09:00 and B at 09:02. Both might receive new readings after 09:02, but their histories can differ. Do not infer that declaring a pipeline triggers execution or that every new subscriber receives every past value.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-flow-trace">Tracing Events from Source to Consumer</a>

<details>
<summary>Click for details</summary>

Suppose the adapter observes `27` at 09:00, `-300` at 09:01, and `33` at 09:02. Validation rejects `-300`; classification turns `33` into an alarm. The dashboard receives only `normal(27)` and `alarm(33)`. Track **the stage where each event was dropped or transformed**.

```text
time        source      after validation      dashboard
09:00       27          27                    normal
09:01       -300        —                     —
09:02       33          33                    alarm
```

If the dashboard unsubscribes at 09:01:30, the 09:02 reading might not reach it; queued signals may complicate the exact stop time. A meaningful flow trace needs **time and lifecycle**, not only a static pipeline drawing.

</details>

- [Back to top](#back-to-top)
