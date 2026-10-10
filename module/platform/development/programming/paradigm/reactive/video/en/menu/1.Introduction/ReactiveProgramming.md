---
video:
  url: ""
---

# Reactive Programming: Streams and Change over Time

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

## Reactive Programming: Concept and Scope

<!-- VIDEO_SECTION -->

### Scene 1 — Reactive Programming: Concept and Scope

**Time:** `00:00–00:58`

**Visual:**

Draw a sensor at left, a clock ticking above three readings 27°C, 29°C, 33°C, and a dashboard alarm at right; reveal arrows only when each sample arrives.

**Script:**

Imagine opening a temperature dashboard at nine o’clock. The number is twenty-seven now, but the sensor has not finished its work. A minute later it might be twenty-nine; another minute later thirty-three triggers an alarm. Reactive programming gives us a way to describe what should happen whenever these future values arrive. That is a model of change over time, not the name of a web framework.

**Purpose:**

Introduce time-dependent values and visible downstream reactions without promising threads, non-blocking execution or a particular library.

## Motivation: Time-Varying Data and Disconnected Callbacks

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:12`

**Visual:**

Keep the sensor timeline running but scatter its alert, error and disconnect arrows into separate callback handlers.

**Script:**

We have a stream of changing readings. What happens when filtering, errors and disconnects are handled by different callbacks?

**Purpose:**

Move the beginner from a changing value into the concrete callback-coordination problem.

### Scene 2 — Motivation: Time-Varying Data and Disconnected Callbacks

**Time:** `01:12–02:10`

**Visual:**

Split the same sensor into three callback boxes onTemperature, onSensorError and onDisconnect; draw scattered arrows to filtering, a ten-second window and cleanup.

**Script:**

What if our dashboard also has to discard impossible readings, group data over ten seconds and close resources after a disconnect? Three disconnected callbacks can work, but their interactions become difficult to trace. The motivation for a flow is not to make callbacks illegal. It is to make transformations, failures and stopping conditions visible in one story.

**Purpose:**

Show why growing lifecycle coordination motivates a composed flow rather than calling every callback a mistake.

## Immediate Values versus Events Arriving over Time

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:24`

**Visual:**

Collapse the three callback arrows into one horizontal time lane but leave a question mark over its failure branch.

**Script:**

We have identified scattered callbacks; now let us compare a number that exists already with readings whose arrival time is unknown.

**Purpose:**

Move from scattered handling toward the key missing dimension: arrival time.

### Scene 3 — Immediate Values versus Events Arriving over Time

**Time:** `02:24–03:22`

**Visual:**

Contrast left fixed value temperature=27 with right timeline 09:00→27, 09:01→29, 09:02→33, then append an open dotted future and unknown next timestamp.

**Script:**

On the left we can calculate with twenty-seven immediately. On the right, twenty-seven is only the first event. We do not know whether another reading will arrive or when it will arrive. A filter on both sides can use the same predicate, but only the right-hand stream must answer when observation starts, how failures are reported and when the recipient leaves.

**Purpose:**

Make time, uncertain stream length and lifecycle visibly different from processing one fixed value.

## Starting Point: Transformations, Intent, and Asynchrony

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:36`

**Visual:**

Slide the filter predicate from the fixed-value card onto the timeline, then leave its input arrow empty until the next tick.

**Script:**

The filter rule is familiar in both pictures. What changes is the moment the input arrives and how processing is connected.

**Purpose:**

Use a shared predicate to connect static transformation to temporal execution.

### Scene 4 — Starting Point: Transformations, Intent, and Asynchrony

**Time:** `03:36–04:34`

**Visual:**

Show filter → transform → consume as three pure-looking blocks; above them separate WHAT (predicate), WHEN (clock), HOW MANY (only if a demand contract exists).

**Script:**

A transformation function is still a function: given a reading, it can return a classification. We can describe filter, transform and consume before seeing any reading. But describing a pipeline does not tell us that it is executing. Asynchronous means a result may arrive after the initiating call; it does not automatically mean another CPU core or a parallel thread.

**Purpose:**

Bridge familiar function composition and declarative intent to time without equating asynchrony and parallelism.

## Source, Signal Flow, Processing Stages, and Consumer

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:34–04:48`

**Visual:**

Keep the three processing blocks but detach the clock from them, then pin the sensor icon at the input.

**Script:**

We know what each stage should do; next we need to name who creates the reading and who ultimately receives it.

**Purpose:**

Move from abstract function stages to concrete producers and recipients.

### Scene 5 — Source, Signal Flow, Processing Stages, and Consumer

**Time:** `04:48–05:46`

**Visual:**

Animate sensor [27,-300,33] → validate [27,33] → classify [normal,alarm] → dashboard; highlight the dropped -300 lane without drawing a fake request call.

**Script:**

Our source is the sensor; an adapter can play the producer role. Validation rejects minus three hundred, classification marks thirty-three as an alarm, and the consumer updates the dashboard. Data moves downstream through stages. There is still no evidence here that the stream has started, that it respects demand, or that the components use different threads. Those questions belong to the lifecycle and contract.

**Purpose:**

Make source/stage/consumer responsibilities and a real zero-output filter visible while avoiding implied startup or backpressure.

## Reactive Streams: A Contract, Not the Entire Paradigm

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:46–06:00`

**Visual:**

Freeze the dashboard alert, then add a dashed protocol boundary behind its upstream arrow, deliberately leaving the framework logo blank.

**Script:**

This diagram explains a reactive way of thinking. What additional promises would we need before counting exactly how many readings a consumer may receive?

**Purpose:**

Motivate a specific flow-control contract without claiming it defines the paradigm.

### Scene 6 — Reactive Streams: A Contract, Not the Entire Paradigm

**Time:** `06:00–06:58`

**Visual:**

Put two cards side-by-side: an event feed that pushes on arrival, and a Reactive Streams link labeled Publisher↔Subscriber with request(n) and cancel(); do not label the first as broken.

**Script:**

A basic event feed can be reactive in style even if it never exposes request. Reactive Streams is a more specific interoperability contract: asynchronous exchange, serialized signals and non-blocking backpressure. It introduces Publisher, Subscriber, Subscription and optionally Processor. We must not copy its demand rules onto every technology that calls itself reactive.

**Purpose:**

Separate general reactive event modeling from the normative Reactive Streams protocol.

## Boundaries between the Paradigm, Libraries, and Frameworks

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:58–07:12`

**Visual:**

Move the four RS role labels onto a middle layer, then slide separate Reactor, RxJava and WebFlux cards above it.

**Script:**

Once a protocol is distinct from the idea of a stream, we can place libraries and frameworks at their proper layers.

**Purpose:**

Prepare the learner to classify implementation tools without replacing the conceptual model.

### Scene 7 — Boundaries between the Paradigm, Libraries, and Frameworks

**Time:** `07:12–08:10`

**Visual:**

Build a three-level stack: PARADIGM (sensor reaction), LIBRARY (Reactor Flux/Mono, RxJava types), FRAMEWORK (WebFlux HTTP), with Reactive Streams shown as a possible compatible boundary.

**Script:**

Reactor supplies operators and types such as Flux and Mono. RxJava has several observable abstractions with different backpressure behavior. Spring WebFlux applies reactive techniques to web work. None of these brand names is the definition of the paradigm. A WebFlux endpoint is not needed to demonstrate a sensor stream, and a reactive library call alone does not prove non-blocking application code.

**Purpose:**

Prevent framework-name substitution and clarify that observable types differ in their demand semantics.

## From Data Propagation to Subscription, Backpressure, and Termination

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:10–08:24`

**Visual:**

Replace the framework layer with a chapter breadcrumb: flow → subscription → backpressure → terminal, and highlight where the next uncertainty lies.

**Script:**

The labels are now sorted. Let us follow one reading end to end, then ask who asks for data and what happens when the source outpaces the screen.

**Purpose:**

Set a concrete learning path from representation to lifecycle, capacity and termination.

### Scene 8 — From Data Propagation to Subscription, Backpressure, and Termination

**Time:** `08:24–09:22`

**Visual:**

Extend the sensor pipeline into four annotated stops: flow trace, subscription, demand meter, then three alternative ending icons; leave next chapter Data Flow highlighted.

**Script:**

Here is our route. First we trace each reading and the place it is transformed or discarded. Then we open a subscription and see when data actually starts. We will measure a producer of a hundred readings a second against a consumer of forty, and only then study Reactive Streams rules for demand. Finally we compare completion, failure and cancellation before choosing whether the whole design is worth its complexity.

**Purpose:**

Preview the dependency order using the same sensor rather than introducing new unexplained APIs.
