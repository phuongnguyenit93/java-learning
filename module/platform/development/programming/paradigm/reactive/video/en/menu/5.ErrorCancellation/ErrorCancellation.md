---
video:
  url: ""
---

# Reactive Stream Lifecycles: Completion, Errors, and Cancellation

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

## Successful Completion and Signals after onComplete

<!-- VIDEO_SECTION -->

### Scene 1 — Successful Completion and Signals after onComplete

**Time:** `00:00–00:58`

**Visual:**

On a signal ladder show a legal empty source onSubscribe→onComplete with demand=0. Beneath show onSubscribe→request(2)→onNext(A)→onComplete. Cross out a post-complete onNext(B).

**Script:**

Completion means this subscription has no more data. Notice the first legal example: onSubscribe followed immediately by onComplete, even with zero requested items. Another source gives A under request two, then completes without using the second allowance. Demand gates onNext data, not the terminal notification. After onComplete there can be no more onNext or onError for that subscriber.

**Purpose:**

Make demand-independent successful terminal delivery and the prohibition on post-terminal signals observable.

## Failure Termination and onError Propagation

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:12`

**Visual:**

Keep the green onComplete lane, replace it with a red sensor-disconnected event without adding a later callback.

**Script:**

We saw a source finish normally. How should its subscriber learn that the source failed instead?

**Purpose:**

Contrast normal terminal completion with a different, exclusive failure terminal.

### Scene 2 — Failure Termination and onError Propagation

**Time:** `01:12–02:10`

**Visual:**

Swap the end of the successful ladder for onError(disconnected). Also show onSubscribe→onError(cannotStart) when no request was made, with all following message slots crossed out.

**Script:**

A lost sensor connection is a failure, not normal completion. The publisher signals onError with a cause and that subscription is terminal. It may even fail immediately after onSubscribe before any demand is requested. Retrying is a new lifecycle strategy, possibly with new side effects; it does not revive a terminated subscriber and allow more onNext messages on the same subscription.

**Purpose:**

Distinguish fatal stream error from a domain value and avoid the invented post-error continuation.

## Cancellation: The Consumer Ends Its Interest

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:24`

**Visual:**

Remove source-failure icon, let dashboard user press Close, and draw an upstream arrow labeled cancel() to its Subscription rather than downstream onComplete.

**Script:**

The source can finish or fail on its own. What if the source is healthy, but this viewer no longer wants to listen?

**Purpose:**

Switch cause of stopping from Publisher termination to Subscriber cancellation.

### Scene 3 — Cancellation: The Consumer Ends Its Interest

**Time:** `02:24–03:22`

**Visual:**

Show dashboard viewer Close→Subscriber→Subscription.cancel(); upstream publisher remains active for another subscriber; leave terminal onComplete icon gray and unused.

**Script:**

Cancellation begins at the consumer side. A viewer closes one dashboard, the subscriber calls cancel through its Subscription, and the publisher is required to eventually stop sending to that subscriber. This is not onComplete, and cancel itself does not require a success callback. The physical hot sensor might keep serving other viewers. We should never equate one subscription ending with the global source switching off.

**Purpose:**

Show the upstream direction of cancel and its per-subscriber semantics without inventing a terminal callback.

## In-Flight Signals during Asynchronous Cancellation

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:36`

**Visual:**

Slide cancel marker to timestamp 09:02:00; keep a 33°C token already moving through a queue, halfway to the former subscriber.

**Script:**

Calling cancel has returned, but an item is already in transit. Do we erase it instantly?

**Purpose:**

Motivate asynchronous in-flight delivery and the eventual rather than synchronous stop guarantee.

### Scene 4 — In-Flight Signals during Asynchronous Cancellation

**Time:** `03:36–04:34`

**Visual:**

Animate onNext(33) leaving upstream at 09:01:59, cancel() called at 09:02:00, then token reaching the subscriber after cancel; shade the later steady state with no further emissions.

**Script:**

This thirty-three was already queued before cancellation. It may still arrive after cancel returns; Reactive Streams expects publishers to eventually stop signaling, not to teleport every in-flight message away. If the dashboard no longer has a viewer, its UI handler can check that state before showing the late alarm. That check is an application decision in addition to the protocol lifecycle.

**Purpose:**

Demonstrate the permitted in-flight race without claiming cancellation permits indefinite signaling.

## Resource Cleanup at Stream Termination

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:34–04:48`

**Visual:**

Move the late reading into a cleanup board listing connection, queue and subscription, with success/error/cancel checkboxes.

**Script:**

An in-flight item reminds us that stopping signals and releasing resources are related, but not the same instant.

**Purpose:**

Prepare explicit resource ownership checks across all three exits.

### Scene 5 — Resource Cleanup at Stream Termination

**Time:** `04:48–05:46`

**Visual:**

Use a matrix with three exits on rows (COMPLETE, ERROR, CANCEL) and resources on columns (sensor connection, queue, subscription); illuminate diagnostics only for ERROR and shared hot source still alive for another viewer.

**Script:**

Finishing a finite stream should release what that stream owns. A failure also needs a useful diagnostic. Cancellation should stop work no longer needed, but it need not release every connection synchronously. And closing one view of a hot sensor must not break other subscriptions. For each exit, inspect which resources belonged to that subscription and whether queue depth and connection counts eventually settle.

**Purpose:**

Test cleanup by ownership and exit path rather than assuming cancel closes every shared resource immediately.

## Comparing Completion, Failure, and Cancellation in One Flow

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:46–06:00`

**Visual:**

Replace the cleanup matrix with three parallel lifeline traces starting from the same onSubscribe, using differently colored terminal/control marks.

**Script:**

We have inspected each exit separately. Let us now compare the complete traces to make sure no callback is silently invented.

**Purpose:**

Synthesize terminal and cancel differences in one side-by-side protocol trace.

### Scene 6 — Comparing Completion, Failure, and Cancellation in One Flow

**Time:** `06:00–06:58`

**Visual:**

Draw three full sensor lanes: SUCCESS request(2)→27→33→onComplete; FAILURE request(2)→27→onError(disconnected); CANCEL request(2)→27→cancel()→eventual stop with optional dashed in-flight token.

**Script:**

These three stories do not end the same way. Completion means the source has no more data; failure reports a terminal cause, and neither may send more signals afterward. Cancellation is the subscriber saying it no longer wants data; it does not require onComplete, and an already traveling reading can briefly arrive. If our tests assert only that every path calls one identical done callback, they have misunderstood the protocol.

**Purpose:**

Consolidate success, failure and cancellation without treating cancellation as a third terminal message.
