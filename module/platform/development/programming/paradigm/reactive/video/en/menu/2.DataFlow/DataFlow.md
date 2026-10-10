---
video:
  url: ""
---

# Reactive Data Flow: Sources, Signals, and Subscriptions

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

## Sources, Producers, and Consumers in a Flow

<!-- VIDEO_SECTION -->

### Scene 1 — Sources, Producers, and Consumers in a Flow

**Time:** `00:00–00:58`

**Visual:**

Put physical sensor, sensor-adapter, validator and dashboard in separate lanes. A red 33°C token crosses them; overlay “source”, “producer”, “both”, “consumer” role labels.

**Script:**

The physical sensor originates a measurement. Its adapter exposes it to the rest of our flow, and the dashboard consumes it. Between them, validation is both a receiver of upstream values and a producer of downstream values. Calling these roles by name lets us ask who owns the buffer and connection. It does not mean each box runs on a dedicated thread.

**Purpose:**

Show that source and producer may differ and an intermediate stage can be both receiver and sender.

## Transforming, Filtering, and Combining Signals

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:12`

**Visual:**

Keep the adapter and dashboard lanes fixed; insert a validation gate and show the -300°C token reaching it.

**Script:**

Now that we know where the reading comes from and where it goes, which values should the middle stage allow through?

**Purpose:**

Connect producer/consumer roles to the first real filter and transformation.

### Scene 2 — Transforming, Filtering, and Combining Signals

**Time:** `01:12–02:10`

**Visual:**

Animate three raw readings 27, -300, 33 through a validity gate; discard -300 into a gray tray, convert 27°C to 80.6°F, then attach a warning flag only to 33.

**Script:**

One raw value does not always produce one output. Our validity filter drops minus three hundred entirely; twenty-seven passes through; thirty-three becomes an alarm when classified. A unit conversion can turn twenty-seven Celsius into eighty point six Fahrenheit. Another combination could attach device status. These are stage contracts, not a promise that one upstream item always costs one downstream request.

**Purpose:**

Make zero-output filtering, one-to-one transformation and enrichment distinguishable before discussing demand.

## Push, Pull, and Hybrid Data Delivery

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:24`

**Visual:**

Keep the dropped -300 slot empty, then widen the producer and consumer lanes and animate their clocks at different speeds.

**Script:**

The stream can shrink or change shape through operators. Now ask who decides when the resulting data moves.

**Purpose:**

Connect cardinality changes to push, pull and timing responsibilities.

### Scene 3 — Push, Pull, and Hybrid Data Delivery

**Time:** `02:24–03:22`

**Visual:**

Three aligned mini timelines: PUSH sends every 100ms; PULL waits for consumer poll; DEMAND AWARE shows granted slots before items; mark a dashboard requiring 500ms per value.

**Script:**

With push, the adapter sends readings when they arrive, even when our dashboard processes one every five hundred milliseconds. Pull means the recipient asks when it wants data. A demand-aware flow combines these ideas: the recipient grants an allowance, then the producer sends available items within it. The last pattern works only when the relevant boundary honors demand; simply calling a stream reactive does not make fast sensors slow down.

**Purpose:**

Compare authority over arrival and delivery without applying Reactive Streams request rules to every event API.

## Composing Propagated Data Processing Stages

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:36`

**Visual:**

Draw four stage boxes linked by arrows. The downstream clock pauses while one classification rule is exchanged with a different rule.

**Script:**

Now that we know who may initiate delivery, we can arrange several transformations without losing sight of the lifecycle.

**Purpose:**

Move from delivery mechanics to readable composition of domain stages.

### Scene 4 — Composing Propagated Data Processing Stages

**Time:** `03:36–04:34`

**Visual:**

Progressively reveal validate → normalize → classify → display. Keep classify highlighted as a pure transformation and display colored as an observable UI effect; add error and cancel rails.

**Script:**

We can change the classification threshold without rewriting the sensor adapter. That is the benefit of composing stages with clear contracts. But the dashboard update is a side effect, even if classification itself is a pure calculation. A real flow also needs somewhere for errors to go and a way to stop work when nobody is watching. Chaining functions alone does not specify that lifecycle.

**Purpose:**

Distinguish composable pure processing stages from effectful consumers and lifecycle rails.

## Subscription: Beginning a Relationship and Receiving Signals

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:34–04:48`

**Visual:**

Dim all stages while placing one highlighted handshake between the adapter and dashboard; reveal a “subscriber joins” icon without data yet.

**Script:**

A pipeline is now drawn. Does drawing or constructing it mean it has emitted anything? We need to examine subscription.

**Purpose:**

Raise the distinction between pipeline description and a live subscriber relationship.

### Scene 5 — Subscription: Beginning a Relationship and Receiving Signals

**Time:** `04:48–05:46`

**Visual:**

Draw Publisher.subscribe(subscriber), then a single onSubscribe(subscription) Publisher→Subscriber arrow FIRST; place request(n) and cancel() as arrows back to Subscription.

**Script:**

A subscription is a relationship with a source. Under the particular Reactive Streams protocol, the Publisher sends onSubscribe first, handing the Subscriber its Subscription. Through that object, the Subscriber can request items or cancel. We have not sent any onNext yet. Notice the arrow directions: onSubscribe carries the control handle downstream, while request and cancel are invoked from the recipient side.

**Purpose:**

Introduce the real RS handshake and distinguish data signals from upstream control calls without inventing emission on subscribe.

## Emission Timing versus Describing a Flow

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:46–06:00`

**Visual:**

Hold the onSubscribe arrow at the beginning of the lane and add two join times 09:00 and 09:02 to separate subscribers.

**Script:**

Once two subscribers appear at different times, the next question is whether they replay history or see only new values.

**Purpose:**

Link the act of subscribing to the source-specific hot/cold timing semantics.

### Scene 6 — Emission Timing versus Describing a Flow

**Time:** `06:00–06:58`

**Visual:**

Place cold source at left drawing a new 27→29 sequence for each subscriber, and hot sensor at right continuing its shared 27→29→33 clock as viewer B joins at 09:02.

**Script:**

A cold source commonly begins its work for a new subscriber, so two subscribers can start their own sequences. A hot source can keep producing even while one dashboard is absent; B joining at nine oh two may miss the earlier twenty-seven. These are source and library behaviors, not universal rules that every stream replays the past. Also, declaring a pipeline never proves that a reading has already traveled through it.

**Purpose:**

Show distinct histories for cold per-subscriber behavior and hot shared timing without promising universal replay.

## Tracing Events from Source to Consumer

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:58–07:12`

**Visual:**

Merge hot/cold panels back into a single timeline; insert a broken value -300 at 09:01 and a cancel marker at 09:01:30.

**Script:**

We have described when listening can begin. Let us trace every actual reading, including one dropped by validation and one that arrives after a viewer leaves.

**Purpose:**

Prepare a precise event-time flow trace that includes filtering and lifecycle stops.

### Scene 7 — Tracing Events from Source to Consumer

**Time:** `07:12–08:10`

**Visual:**

Create a four-column board time/source/valid/dashboard. Reveal rows 09:00 27→27→normal, 09:01 -300→empty→empty, 09:02 33→33→alarm; then overlay cancel at 09:01:30.

**Script:**

At nine the sensor reports twenty-seven and the dashboard says normal. At nine oh one, minus three hundred is rejected at validation; it never becomes a dashboard event. At nine oh two, thirty-three would become an alarm for an active recipient. But if that viewer cancelled at nine oh one thirty, it may not receive the later alarm. An asynchronous queue could still contain in-flight signals. The trace has to include time and who is still subscribed, not just a row count.

**Purpose:**

Trace each discarded and transformed event and show how subscription timing changes observable output.
