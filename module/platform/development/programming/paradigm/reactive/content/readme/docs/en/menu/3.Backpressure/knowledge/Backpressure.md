<a id="back-to-top"></a>

# Producer–Consumer Rate Mismatch and Backpressure

## Menu
- [Rate Mismatch and Work Accumulation with Faster Producers](#reactive-rate-mismatch)
- [Buffering, Resource Limits, and Lost Data Risks](#reactive-queue-overflow)
- [Demand and Coordinating Signal Counts](#reactive-demand-flow-control)
- [Trade-offs among Buffering, Dropping, and Throttling](#reactive-overload-strategies)
- [Demand-Based Backpressure and Its API Boundaries](#reactive-backpressure-boundary)

## <a id="reactive-rate-mismatch">Rate Mismatch and Work Accumulation with Faster Producers</a>

<details>
<summary>Click for details</summary>

Suppose a producer emits **100 readings per second** while the consumer processes **40 per second**. With continuous unbounded arrival and no other control, pending work grows by about **60 readings per second**: roughly 600 items after 10 seconds. This is a **rate mismatch**, not something reactive programming automatically eliminates.

Ask where items are queued, how much memory each needs, whether the upstream rate can be reduced, and whether loss is acceptable. Backpressure begins with concrete capacity constraints, not by memorizing an operator name.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-queue-overflow">Buffering, Resource Limits, and Lost Data Risks</a>

<details>
<summary>Click for details</summary>

A **bounded** buffer handles short bursts, but eventually fills if the average incoming rate remains higher. With capacity 120 and a net increase of 60 items per second, an initially empty buffer could fill in roughly **two seconds** in this simple model. Unbounded buffering substitutes memory exhaustion and stale results for immediate data loss.

When the buffer fills, the system needs an explicit policy: propagate upstream slowdown where possible, reject work, drop selected data, or hand off to durable storage. Monitor both queue length and the age of queued readings; successfully displaying old temperatures may be worse than intentionally choosing a recent sample.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-demand-flow-control">Demand and Coordinating Signal Counts</a>

<details>
<summary>Click for details</summary>

**Demand** is the outstanding allowance the consumer has granted the producer under a demand-aware flow contract. If it requests three items, the producer may deliver zero through three, but not four without further requests. The consumer may request more after processing some of the granted elements.

```text
request(3)   demand=3
onNext(A)    demand=2
onNext(B)    demand=1
request(2)   demand=3
onNext(C)    demand=2
```

This regulates one subscription's emission. Demand alone **does not prove total application memory is bounded** if an intermediate stage accumulates data without limits.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-overload-strategies">Trade-offs among Buffering, Dropping, and Throttling</a>

<details>
<summary>Click for details</summary>

**Buffering** preserves data at a cost in memory and latency; **dropping** may suit a live GPS position or replaceable display update but is unsafe for silently discarding financial transactions; **throttling/backpressure** reduces upstream delivery when the source and intermediate stages can cooperate.

A live temperature display may keep only the newest sample, whereas safety alerts might require durable delivery or extra capacity. Choose the **acceptable loss and latency contract** first, and only then select an implementation technique.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-backpressure-boundary">Demand-Based Backpressure and Its API Boundaries</a>

<details>
<summary>Click for details</summary>

In **Reactive Streams**, backpressure uses demand: delivered `onNext` signals must never exceed the total requested elements. `Publisher`, `Subscription`, and `Subscriber` must participate in the protocol; it is not merely a recommendation to “process more slowly”.

An event `Observable` that pushes without corresponding demand signals **does not automatically satisfy** this protocol, even though it may still support reactive-style applications. Keep the **paradigm** (responding to changing data) separate from the **Reactive Streams exchange contract** (demand, serial signaling, and non-blocking backpressure).

</details>

- [Back to top](#back-to-top)
