---
video:
  url: ""
---

# Choosing Reactive: Use Cases and Boundaries

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

## Designing a Signal Flow from Source to Consumer and Termination

<!-- VIDEO_SECTION -->

### Scene 1 — Designing a Signal Flow from Source to Consumer and Termination

**Time:** `00:00–00:58`

**Visual:**

Draw DATA sensor→validate→classify→dashboard, CONTROL dashboard→Subscription→upstream stages, capacities at each queue and 100/s versus 40/s counters. Add complete/error/cancel exits.

**Script:**

Let us design the whole dashboard. Data moves downstream through validation and classification. Where a stage honors demand, requests and cancellation begin downstream and may be coordinated upstream per Subscription. A Processor can translate request counts because filtering can drop readings. The physical sensor might still produce a hundred a second while the screen handles forty, so each boundary needs an overload choice. We also have to design each of the three endings.

**Purpose:**

Integrate opposite data/control directions, rate mismatch and lifecycle without pretending an HTTP application ran.

## Reactive versus Sequential Processing and Explicit Callbacks

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:12`

**Visual:**

Keep the full 100/s sensor design on one side and reveal a fixed twenty-reading list with a simple averaging loop opposite it.

**Script:**

We can build the complicated stream, but is it necessary for every dataset? Compare it with a task whose values are already in memory.

**Purpose:**

Challenge the completed reactive design with a realistic simpler alternative.

### Scene 2 — Reactive versus Sequential Processing and Explicit Callbacks

**Time:** `01:12–02:10`

**Visual:**

Three distinct columns: fixed list [27,29,33] with a loop; three standalone callbacks; continuous stream with filter/error/cancel/demand. Add lifecycle obligations under each.

**Script:**

If all twenty readings are already loaded and we just need an average, a loop or pure function is clearer. A few callbacks with obvious cleanup can also be right. The reactive approach becomes more compelling when continuous data needs several transformations, pressure control and explicit cancellation. Count the actual lifecycle cases, not the number of lines in an operator chain.

**Purpose:**

Make sequential processing and callbacks credible alternatives, not straw men.

## Asynchrony, Non-Blocking Behavior, Parallelism, and Scheduling Are Different

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:24`

**Visual:**

Shift the reactive chain onto a one-thread timeline; beside it draw waiting I/O and two simultaneous CPU tasks as separate examples.

**Script:**

A data flow can be long-lived without running parallel work. Let us separate timing, blocking and actual threads.

**Purpose:**

Prepare independent runtime terms rather than assuming reactive always runs asynchronously.

### Scene 3 — Asynchrony, Non-Blocking Behavior, Parallelism, and Scheduling Are Different

**Time:** `02:24–03:22`

**Visual:**

Use four independent labels: ASYNC result later, NONBLOCKING no waiting thread occupied, PARALLEL simultaneous work, SCHEDULER execution placement. Show synchronous reactive code on one thread and an inserted blocking call.

**Script:**

Asynchronous means the result need not arrive inside the initiating call. Non-blocking means an operation does not unnecessarily hold a waiting thread. Parallel means work can progress at the same moment. Scheduling decides where and when tasks run. A reactive pipeline can be synchronous on one thread, and a bad blocking call can still stall it. Reactive Streams specifies non-blocking backpressure and responsiveness; it does not give each operator a free thread.

**Purpose:**

Explain four non-equivalent runtime properties through contradictory but possible timelines.

## Costs of Tracing, Failures, Resources, and Complexity

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:36`

**Visual:**

Replace thread lanes with a missing 33°C alarm at center and five instrument probes: filter, queue drop, demand, cancel and error.

**Script:**

If the expected alarm vanishes, the next skill is distinguishing the cause with evidence rather than blaming a scheduler.

**Purpose:**

Lead from execution concepts into concrete diagnostic measurements.

### Scene 4 — Costs of Tracing, Failures, Resources, and Complexity

**Time:** `03:36–04:34`

**Visual:**

Missing alarm(33) board with counters before/after validation, queue depth, outstanding demand, terminal/cancel timestamps; animate five diverging root-cause paths.

**Script:**

A thirty-three degree reading can disappear because a predicate filtered it, a full queue dropped it, no demand was outstanding, a viewer cancelled, or onError ended the subscription. Put counters and timestamps at those boundaries. A stack trace across asynchronous stages may tell only part of the story. Logging every operator without resource ownership or error policy is not a substitute for evidence.

**Purpose:**

Give an actionable path to isolate five different reasons for one missing event.

## Boundaries with Project Reactor, Spring WebFlux, RxJava, and Java Flow

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:34–04:48`

**Visual:**

Turn five diagnostic probes into links to Reactor, RxJava, Java Flow and WebFlux specialist shelves, without changing the underlying contract.

**Script:**

These flow questions survive a change of library. Where should we study the actual operators and runtime details next?

**Purpose:**

Connect technology-independent troubleshooting to specialist module ownership.

### Scene 5 — Boundaries with Project Reactor, Spring WebFlux, RxJava, and Java Flow

**Time:** `04:48–05:46`

**Visual:**

Four shelves: Reactor Flux/Mono; RxJava Observable/Flowable; Java Flow interfaces; WebFlux HTTP. Underneath, mark non-identical backpressure behavior and warning “reactive API ≠ automatic nonblocking code”.

**Script:**

Reactor offers composable types, RxJava has several kinds of observables with different demand semantics, Java Flow has standard interfaces, and WebFlux applies reactive processing to web applications. Their operators, scheduling and HTTP mechanics belong to focused technology modules. Using one of these APIs cannot by itself prove the code is non-blocking, nor does it mean every type obeys precisely the same Reactive Streams rules.

**Purpose:**

Route implementation-specific learning without confusing APIs, libraries or the paradigm.

## Criteria for Choosing Reactive or a Simpler Alternative

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:46–06:00`

**Visual:**

Close four technology shelves and contrast preloaded twenty-value average versus endless sensor dashboard with explicit buffer, failure and cancel checklist.

**Script:**

Before picking any one library, let us decide whether the problem warrants a reactive design at all.

**Purpose:**

Move from tool choice back to workload and lifecycle evidence.

### Scene 6 — Criteria for Choosing Reactive or a Simpler Alternative

**Time:** `06:00–06:58`

**Visual:**

Final two cards: fixed twenty-number list→one loop versus continuously updating sensor→validation→alarm→display. Tick arrivals, transformations, demand support, overload policy, termination, cleanup.

**Script:**

For twenty numbers already in memory, a simple function often wins. For a sensor that keeps producing, with transformations, unequal speeds and viewers who leave or see errors, reactive composition may be worth it. But do not deploy based on the label. Determine whether upstream honors demand, who owns buffers, what data may be dropped and how all exits clean up. The paradigm helps frame those obligations; the implementation must prove them.

**Purpose:**

End with an evidence-based decision and responsibility boundary for implementation-specific testing.
