<a id="back-to-top"></a>

# Reactive Stream Lifecycles: Completion, Errors, and Cancellation

## Menu
- [Successful Completion and Signals after onComplete](#reactive-terminal-success)
- [Failure Termination and onError Propagation](#reactive-terminal-failure)
- [Cancellation: The Consumer Ends Its Interest](#reactive-cancellation)
- [In-Flight Signals during Asynchronous Cancellation](#reactive-inflight-signals)
- [Resource Cleanup at Stream Termination](#reactive-resource-cleanup)
- [Comparing Completion, Failure, and Cancellation in One Flow](#reactive-lifecycle-trace)

## <a id="reactive-terminal-success">Successful Completion and Signals after onComplete</a>

<details>
<summary>Click for details</summary>

`onComplete` announces **successful source completion** with no further elements on that subscription. Reactive Streams Subscribers must accept this signal **even without any previous request**: an empty Publisher may finish immediately after `onSubscribe`.

```text
onSubscribe → onComplete                              valid
onSubscribe → request(2) → onNext(A) → onComplete      valid
onComplete → onNext(B)                                invalid
```

Demand governs **`onNext` data emissions**, not whether a terminal signal must wait until all requested elements are delivered. Terminal signaling also ends the subscription.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-terminal-failure">Failure Termination and onError Propagation</a>

<details>
<summary>Click for details</summary>

`onError(cause)` is a **terminal failure signal**. A Publisher may emit it immediately after `onSubscribe` if it cannot start, even when there is no outstanding demand. Once signaled, it cannot emit further `onNext` or `onComplete` for that Subscriber.

If our sensor disconnects permanently, the consumer records the cause and releases its associated work. Retrying is a **separate policy with its own lifecycle and possible side effects**, not a way to revive a terminated subscription and keep emitting into it.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-cancellation">Cancellation: The Consumer Ends Its Interest</a>

<details>
<summary>Click for details</summary>

**Cancellation** means the consumer no longer wants elements from its subscription, for example when a user closes the dashboard while the sensor continues operating. It differs from `onComplete` (the source has finished) and `onError` (the source failed). A Reactive Streams `cancel()` does **not itself require an `onComplete` notification**.

The Publisher must **eventually stop signaling** the cancelled subscriber. It cannot be assumed to stop synchronously at the instant `cancel()` returns, because asynchronous stages may already have messages in flight.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-inflight-signals">In-Flight Signals during Asynchronous Cancellation</a>

<details>
<summary>Click for details</summary>

Suppose the consumer calls `cancel()` at 09:02:00 while an `onNext(33)` already emitted by upstream is traveling through an asynchronous queue. That signal **may still arrive after cancellation**. Reactive Streams requires signals to **eventually cease**, not that every in-flight signal vanish instantly.

Recipients must therefore tolerate already requested signals still propagating and should not interpret cancellation as immediate queue deletion. A UI may additionally check whether the viewer still wants the result before displaying a late reading.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-resource-cleanup">Resource Cleanup at Stream Termination</a>

<details>
<summary>Click for details</summary>

A flow can own a subscription, sensor connection, queues, and per-stage resources. **Normal completion**, **error**, and **cancellation** all need suitable cleanup, but they arise from different causes. A finite source can release its connection at completion; failure needs diagnostics; early cancellation should stop unnecessary work.

Do not assume `cancel()` synchronously frees every resource. A downstream subscriber stopping does not necessarily shut down a hot source that still serves others. Tests should inspect connection counts, pending buffers, and live subscriptions after each exit path.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-lifecycle-trace">Comparing Completion, Failure, and Cancellation in One Flow</a>

<details>
<summary>Click for details</summary>

For our sensor and dashboard, the three outcomes look different:

```text
SUCCESS: subscribe → request(2) → 27 → 33 → onComplete
FAILURE: subscribe → request(2) → 27 → onError(disconnected)
CANCEL:  subscribe → request(2) → 27 → cancel() → eventual stop
```

The first two have **terminal signals**, after which no further emissions are allowed. The third is a **cancellation request**, not necessarily followed by a terminal callback; an in-flight item may arrive briefly afterward. Test all three paths rather than assuming one “done” handler covers identical behavior.

</details>

- [Back to top](#back-to-top)
