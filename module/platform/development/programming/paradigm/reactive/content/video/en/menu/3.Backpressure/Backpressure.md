---
video:
  url: ""
---

# Producer–Consumer Rate Mismatch and Backpressure

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

## Rate Mismatch and Work Accumulation with Faster Producers

<!-- VIDEO_SECTION -->

### Scene 1 — Rate Mismatch and Work Accumulation with Faster Producers

**Time:** `00:00–00:58`

**Visual:**

Place two counters over a queue: SENSOR +100/s and DISPLAY -40/s. Animate net +60/s, then pause at t=1s queue60 and t=10s queue600 (initially empty, idealized model).

**Script:**

Our dashboard is slower than the sensor: a hundred readings arrive every second and only forty leave. If arrivals continue without a limit or drop policy, sixty remain each second. With an initially empty queue, that is roughly six hundred pending after ten seconds. The reactive label on the pipeline does not delete this arithmetic. We must decide where the queue lives and who may slow the source.

**Purpose:**

Make the 100 minus 40 equals 60 growth rate and ten-second accumulation observable and conditional.

## Buffering, Resource Limits, and Lost Data Risks

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:12`

**Visual:**

Keep the +60/s queue-growth counter visible and draw a container with exactly 120 available slots.

**Script:**

The queue grows by sixty every second. How quickly does a buffer of one hundred twenty places fill?

**Purpose:**

Move from rate arithmetic into bounded-capacity and stale-data consequences.

### Scene 2 — Buffering, Resource Limits, and Lost Data Risks

**Time:** `01:12–02:10`

**Visual:**

Set queue capacity to 120 cells; animate 60 new pending at one second and all 120 occupied at about two seconds. Beside it compare bounded queue / unbounded queue / age of oldest reading.

**Script:**

Now add a capacity of one hundred twenty elements. In our simplified continuous-rate model, the sixty-per-second surplus fills it in about two seconds. A bounded queue needs a policy for the next item. An unbounded queue does not make capacity disappear; it postpones failure into memory usage and increasingly stale readings. For a live dashboard, a five-minute-old temperature can be a problem even if no item was lost.

**Purpose:**

Expose time-to-fill, memory and staleness rather than treating buffering as a free solution.

## Demand and Coordinating Signal Counts

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:24`

**Visual:**

Freeze the full buffer, replace the input clock with a “granted tokens” meter reading 3 and allow the next reading through only when a token exists.

**Script:**

A bigger queue did not fix a persistent mismatch. Can the recipient say how many items it is ready to accept?

**Purpose:**

Move from absorbing excess work to an explicit per-subscription demand allowance.

### Scene 3 — Demand and Coordinating Signal Counts

**Time:** `02:24–03:22`

**Visual:**

Animate demand tokens: request(3) meter3; onNext(A)→2, onNext(B)→1, request(2)→3, onNext(C)→2; stop before inventing another emission.

**Script:**

Here is demand as a count, not a delay timer. A subscriber grants three items; after A and B arrive, one allowance remains. Requesting two more increases the outstanding total to three. C consumes one, leaving two. A publisher may send fewer than the allowance when data is not available; it must not exceed it. And this meter alone does not prove an intermediate buffer elsewhere is bounded.

**Purpose:**

Demonstrate accumulated demand and each onNext decrement without promising a bounded entire application.

## Trade-offs among Buffering, Dropping, and Throttling

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:36`

**Visual:**

Split the overflowing queue into three labeled strategies: buffer with age, replace stale sample, throttle cooperative source; mark each with a loss/latency icon.

**Script:**

Demand works where upstream cooperates. For a physical sensor that keeps measuring regardless, we still need a policy for the excess readings.

**Purpose:**

Bridge protocol capacity to the domain decision about loss and delay.

### Scene 4 — Trade-offs among Buffering, Dropping, and Throttling

**Time:** `03:36–04:34`

**Visual:**

Show three live dashboard panels at source100/s display40/s: BUFFER (queue length rises), DROP OLD (current 33°C survives), THROTTLE (source-adapter caps delivery). Add a separate finance icon crossed out beside DROP.

**Script:**

There is no universally best overload strategy. Buffering preserves samples for a while but increases latency and memory. Dropping older readings can be reasonable for a live display that only cares about the newest temperature; it is not acceptable to silently drop payments or required safety events. Throttling works only where some upstream component can reduce delivery. Start with the loss and freshness contract, not the operator name.

**Purpose:**

Make the loss, latency and upstream-cooperation trade-offs concrete across sensor and financial cases.

## Demand-Based Backpressure and Its API Boundaries

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:34–04:48`

**Visual:**

Remove the sensor details and draw a clean one-subscription arrow with request(3) above it; add a thick outline labeled CONTRACT to distinguish it from the general overload palette.

**Script:**

These are engineering choices. We now need to ask what exactly a demand-aware interoperability contract promises, and what it leaves to us.

**Purpose:**

Move from overload policy choices to normative RS scope.

### Scene 5 — Demand-Based Backpressure and Its API Boundaries

**Time:** `04:48–05:46`

**Visual:**

Draw one Subscriber→Subscription request(3) and three reserved onNext slots; beside it show an upstream hot sensor still emitting 100/s into a separately bounded adapter queue.

**Script:**

Demand-based backpressure limits what one compliant publisher sends on one subscription. It does not necessarily stop the physical sensor from measuring. Our hot-source adapter might still receive a hundred readings a second and need a buffer or drop rule before its Publisher boundary. This is why protocol compliance and end-to-end resource safety are related but not identical. Next we will read the precise signals and roles of Reactive Streams.

**Purpose:**

Keep the contract local to its boundary and expose unregulated physical/hot upstream work.
