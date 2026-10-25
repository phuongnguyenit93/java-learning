<a id="back-to-top"></a>

# Reactive Streams: Signal and Demand Contracts

## Menu
- [Reactive Streams Specification versus the Reactive Paradigm](#reactive-spec-scope)
- [Publisher, Subscriber, Subscription, and Processor Roles](#reactive-four-roles)
- [Signal Order: onSubscribe, onNext, onError, and onComplete](#reactive-signal-sequence)
- [request(n), Accumulated Demand, and onNext Limits](#reactive-request-n)
- [Invalid Requests, Termination, and Protocol Errors](#reactive-protocol-failures)
- [Contract-Based Interoperability and Implementing Libraries](#reactive-interoperability)

## <a id="reactive-spec-scope">Reactive Streams Specification versus the Reactive Paradigm</a>

<details>
<summary>Click for details</summary>

Reactive Streams specifies **asynchronous stream processing with non-blocking backpressure** across interoperable components. It defines publisher/subscriber responsibilities, signal ordering, and request/cancel behavior. It does not prescribe HTTP controllers or a particular scheduler's implementation.

The normative words **MUST/SHOULD/MAY** in the specification express rule strength. Claims about when `onComplete` can occur must be checked against that contract, not generalized from a single library demonstration. [Reactive Streams JVM Specification](https://github.com/reactive-streams/reactive-streams-jvm).

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-four-roles">Publisher, Subscriber, Subscription, and Processor Roles</a>

<details>
<summary>Click for details</summary>

A **Publisher** offers a potentially unbounded sequence; a **Subscriber** receives elements and terminal signals; a **Subscription** controls demand and cancellation for their particular relationship; a **Processor** acts both as a Subscriber of its upstream and a Publisher for downstream.

```text
upstream Publisher → Processor → downstream Subscriber
                    (Subscriber / Publisher)
each boundary has its own subscription and demand obligations
```

Processors are optional, and downstream demand does not by itself guarantee a chain of unlimited intermediate queues is safe. Every component must meet the protocol at its own boundary.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-signal-sequence">Signal Order: onSubscribe, onNext, onError, and onComplete</a>

<details>
<summary>Click for details</summary>

For one Subscriber, the signal sequence is **`onSubscribe → onNext* → (onError | onComplete)?`** while not cancelled. `onSubscribe` comes **before any other signals**. `onNext` requires previously requested demand; signals to the Subscriber must be serially ordered, not overlapping arbitrarily.

```text
Publisher → Subscriber: onSubscribe(subscription)   first signal
Subscriber → Subscription: request(2)                control call
Publisher → Subscriber: onNext(A), onNext(B)          within demand
Publisher → Subscriber: onComplete OR onError        at most one terminal

ALTERNATIVE control path:
Subscriber → Subscription: cancel()                  not an onX signal
```

The arrows separate **Publisher-to-Subscriber signals** from **Subscriber-to-Subscription control calls**: `cancel()` is not a signal emitted by the Publisher. `onComplete` or `onError` can also follow `onSubscribe` directly without `request` or `onNext`. No `onNext`, `onError`, or `onComplete` may follow a **terminal signal**. Error and successful completion are alternative terminal outcomes, not a pair that always occur together.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-request-n">request(n), Accumulated Demand, and onNext Limits</a>

<details>
<summary>Click for details</summary>

For one subscription, `request(n)` with **n > 0** adds permitted demand, and each delivered `onNext` consumes one allowance. A `request(2)` followed by `request(3)` permits **at most five** `onNext` signals before more demand. The publisher may deliver fewer and terminate if the source ends.

```text
request(2) + request(3) → accumulated demand 5
onNext(A), onNext(B), onNext(C) → remaining demand 2
onComplete → valid, even with unused demand
```

The specification requires that total delivered `onNext` **never exceed total requested** at any time. Implementations must account for large demand totals and overflow safely. [Reactive Streams JVM — Publisher rules](https://github.com/reactive-streams/reactive-streams-jvm).

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-protocol-failures">Invalid Requests, Termination, and Protocol Errors</a>

<details>
<summary>Click for details</summary>

`request(0)` or `request(-1)` is **not a pause**. Under Reactive Streams, nonpositive demand violates the protocol; the Publisher must signal an `onError` with `IllegalArgumentException` according to Subscription rule 3.9. A consumer wanting to stop should `cancel()`, not send an invalid request.

Source failure is signaled through **`onError`** and ends the interaction; no subsequent `onNext` is permitted. `onComplete` or `onError` **need not wait for demand**, so an empty source can finish right after `onSubscribe`. Distinguish domain error values that can flow as data from a terminal stream failure.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-interoperability">Contract-Based Interoperability and Implementing Libraries</a>

<details>
<summary>Click for details</summary>

If library A provides a compliant Publisher and library B a compliant Subscriber, they can exchange signals without sharing every operator name. This is the point of a common protocol: agree about **demand, order, failure, and cancellation**. Compatibility depends on intermediates respecting those rules too.

Reactor illustrates a library that builds composable abstractions on Reactive Streams. RxJava includes both demand-aware types and other observable types; Spring WebFlux uses reactive concepts in web processing. The specification **does not replace the implementation** and does not prescribe framework APIs.

</details>

- [Back to top](#back-to-top)
