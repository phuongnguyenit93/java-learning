---
video:
  url: ""
---

# Reactive Streams: Signal and Demand Contracts

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** MM:SS–MM:SS

**Visual:**
Describe what changes visually between the previous concept and the next.
**Script:**
Write a short spoken bridge into the following section.
**Purpose:**
Explain why this transition is pedagogically needed.

SCENE FORMAT

### Scene N — optional presentation title

**Time:** MM:SS–MM:SS
**Visual:**
Describe the exact diagram, timeline, source, or observation visible.
**Script:**
Write natural narration for the filmed demonstration.
**Purpose:**
State the specific concept or evidence this scene demonstrates.
-->

## Reactive Streams Specification versus the Reactive Paradigm

<!-- VIDEO_SECTION -->

### Scene 1 — Reactive Streams Specification versus the Reactive Paradigm

**Time:** `00:00–00:58`

**Visual:**

Put “reactive idea” (clock and changing values) outside a boxed “Reactive Streams JVM specification”; reveal MUST request-bound onNext, serial signals and non-blocking backpressure inside.

**Script:**

This boxed area is the specific Reactive Streams specification, not every event stream in the world. Its goal is interoperable asynchronous exchange with non-blocking backpressure. The words must, should and may have normative force here. We can therefore test how many onNext signals were requested and how signals end. But this contract does not define our sensor alarm rule, prescribe HTTP endpoints, or select a scheduler for the app.

**Purpose:**

Identify where MUST-level protocol guarantees apply versus where domain behavior remains an application decision.

## Publisher, Subscriber, Subscription, and Processor Roles

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:12`

**Visual:**

Zoom into the boxed Reactive Streams contract and split the interface into four role-shaped ports.

**Script:**

The standard defines rules for compatible participants. Let us identify the four roles before following their signals.

**Purpose:**

Move from a protocol boundary to the exact participating API roles.

### Scene 2 — Publisher, Subscriber, Subscription, and Processor Roles

**Time:** `01:12–02:10`

**Visual:**

Draw Publisher(sensors) → Processor(validate) → Subscriber(dashboard). Under each arrow add a separate Subscription; show Processor has an upstream Subscriber face and downstream Publisher face.

**Script:**

The Publisher offers values. The Subscriber receives data and terminal signals. The Subscription is the control relationship for that particular pair, through which the subscriber requests or cancels. The Processor is both a subscriber upstream and a publisher downstream. So even when the dashboard asks for three readings, a validating Processor may not forward exactly request three to the physical adapter: filtering changes cardinality and each boundary has its own obligations.

**Purpose:**

Show four distinct roles, optional Processor and why per-boundary demand need not be passed through unchanged.

## Signal Order: onSubscribe, onNext, onError, and onComplete

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:24`

**Visual:**

Replace role labels with a numbered message ladder; hold the Publisher first arrow until onSubscribe(subscription) is drawn.

**Script:**

Now we can distinguish participants. In what order are the actual callback signals allowed to reach a Subscriber?

**Purpose:**

Prepare serialized callback order rather than confusing Subscription control calls with publisher signals.

### Scene 3 — Signal Order: onSubscribe, onNext, onError, and onComplete

**Time:** `02:24–03:22`

**Visual:**

Animate lanes: Publisher→Subscriber onSubscribe first; Subscriber→Subscription request(2); Publisher→Subscriber onNext(A), onNext(B), then choose ONE branch onComplete OR onError. Place cancel on a separate upstream control arrow.

**Script:**

For one subscriber, onSubscribe comes first. Then we may see zero or more onNext values, each supported by previously requested demand. An optional terminal signal is either onComplete or onError, not both. Those callbacks must be serialized for this subscriber, even if an implementation uses multiple threads internally. Request and cancel are control calls from the subscriber to its Subscription; do not draw cancel as an extra Publisher onX message.

**Purpose:**

Make first signal, serialization, exclusive terminals and control-call direction visible in one ladder.

## request(n), Accumulated Demand, and onNext Limits

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:36`

**Visual:**

Erase the terminal branch and place two request arrows onto one Subscription counter: request(2) followed by request(3).

**Script:**

We have the legal signal ordering. Before letting A through, let us count how many data items the subscriber actually allowed.

**Purpose:**

Bridge serial signal order to the numeric onNext demand bound.

### Scene 4 — request(n), Accumulated Demand, and onNext Limits

**Time:** `03:36–04:34`

**Visual:**

Counter starts at 0; request(2)→2, request(3)→5; onNext(A/B/C) reduces to 2. Reveal legal onComplete with unused two slots, and a crossed-out sixth onNext without another request.

**Script:**

On one Subscription, requests of two and three accumulate to an allowance of five. A, B and C consume three units, leaving two. The publisher may send fewer than five, and it may complete now if its source has ended; it does not owe us two invented values. What it cannot do is emit a sixth onNext without additional demand. Large request totals also need safe overflow handling in a real implementation.

**Purpose:**

Demonstrate monotonic allowance addition, onNext-only consumption and demand-independent completion.

## Invalid Requests, Termination, and Protocol Errors

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:34–04:48`

**Visual:**

Change request(3) to request(0), tint the request line red, and keep pause and cancel controls visible as different actions.

**Script:**

What if the subscriber asks for zero, hoping to pause? Is that the same operation as cancelling?

**Purpose:**

Set up the difference between nonpositive protocol violations and normal cancellation.

### Scene 5 — Invalid Requests, Termination, and Protocol Errors

**Time:** `04:48–05:46`

**Visual:**

Side-by-side Subscription calls request(0), request(-1) stamped INVALID; show Publisher→Subscriber onError(IllegalArgumentException) and cross out any subsequent onNext/onComplete. A separate empty source shows onSubscribe→onComplete without request.

**Script:**

Zero or negative request is not a pause command. Under Subscription rule three point nine it violates the contract, so the Publisher must signal onError with IllegalArgumentException. A consumer that wants to stop should call cancel instead. Also note the important exception to our demand counter: an empty source may send onComplete just after onSubscribe without any request, and failure can end the stream without demand as well. Terminal signals are not extra data items.

**Purpose:**

Teach invalid-demand failure while showing that onError/onComplete do not consume or await requested data allowance.

## Contract-Based Interoperability and Implementing Libraries

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:46–06:00`

**Visual:**

Erase the red invalid-request overlay and connect a Publisher from library A to a Subscriber from library B; put a compatibility check on the Processor between them.

**Script:**

The rules are specific enough to test. Can two different libraries interoperate without sharing the same operator names?

**Purpose:**

Move from rule correctness to protocol-based interoperability between independent libraries.

### Scene 6 — Contract-Based Interoperability and Implementing Libraries

**Time:** `06:00–06:58`

**Visual:**

Draw library A Publisher → contract checkpoint demand/order/terminal/cancel → library B Subscriber; add a Processor checkpoint and an optional TCK test icon; separate WebFlux card outside.

**Script:**

If both sides and every intermediate Processor honor the same Publisher and Subscriber contract, they can exchange values without using identical library operator names. That is interoperability. The implementation still has to satisfy demand, ordering and terminal rules, and protocol tests can help. Reactor is one example of a library; RxJava contains different kinds of observables; WebFlux is an application framework. A common protocol does not make every API identical.

**Purpose:**

Clarify actual interoperability obligations without turning the specification into a framework or operator catalogue.
