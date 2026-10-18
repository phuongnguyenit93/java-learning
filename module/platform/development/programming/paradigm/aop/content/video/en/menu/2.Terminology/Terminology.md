---
video:
  url: ""
---

# Core AOP Model: Aspects and Execution Points

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe the transition, title card, or screen change.

**Script:**

Write the short bridge from the previous section to the current section.

**Purpose:**

Explain why this transition exists.

SCENE FORMAT

### Scene N — <optional scene title>

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe what the viewer sees: slide, diagram, source code, terminal, API request/response, runtime output, highlight, or callout.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates, or proves.
-->


## Aspect: A Unit for Cross-Cutting Concerns

<!-- VIDEO_SECTION -->

### Scene 1 — Aspect: A Unit for Cross-Cutting Concerns

**Time:** `00:00–01:07`

**Visual:**

Open a single ServiceTiming card containing two compartments: matching rule and timer behavior; align it above transfer, invoice, inventory call boxes.

**Script:**

Think of an aspect as the place where one shared concern lives. Our ServiceTiming aspect owns what the timing policy does and the rule describing which operations it concerns. It does not own how transferFunds computes or commits anything. Keeping that distinction matters when we add more services: the aspect should not accumulate their business decisions.

**Purpose:**

Show aspect as the modular home of one concern, not another name for a method or annotation.


## Join Point: A Point in the Execution Model

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:07–01:19`

**Visual:**

From the two-compartment ServiceTiming card, draw a transferFunds execution timeline with separate method, construction and field-event markers.

**Script:**

Naming the shared concern is not enough; to apply it correctly, we must distinguish the possible points of execution.

**Purpose:**

Illustrate why an aspect needs eligible join points before its advice can participate.

### Scene 1 — Join Point: A Point in the Execution Model

**Time:** `01:19–02:26`

**Visual:**

Place small markers on a timeline of transferFunds execution, a constructor, and a field update. Shade method execution for Spring proxy, wider possible points for AspectJ.

**Script:**

A join point is a point within a particular execution model. A method executing is one example; some AOP systems expose other kinds. Do not start by assuming every framework can intercept a field write or object construction. Our payment method is eligible only if the chosen implementation model exposes that event.

**Purpose:**

Teach join point as a framework-dependent candidate location, distinct from selection.


## Pointcut: Selecting Join Points

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:26–02:38`

**Visual:**

Keep the join-point markers and overlay a filtering frame that selects transferFunds and refund while excluding formatMoney.

**Script:**

Having possible points does not mean every point should receive timing.

**Purpose:**

Convert candidate execution events into the specific subset selected by a pointcut.

### Scene 1 — Pointcut: Selecting Join Points

**Time:** `02:38–03:45`

**Visual:**

On a grid of transferFunds(), refund(), formatMoney(), highlight only first two with a green matching-rule overlay; label the third NO MATCH.

**Script:**

Now apply a selection rule. The pointcut describes which available join points are selected. In this imagined policy, transferFunds and refund match, while formatMoney does not. The names are not enough for production code: we would verify the real selection criteria in tests. An overly broad rule turns a shared concern into an accidental global side effect.

**Purpose:**

Make pointcut a predicate over supported join points, not the added behavior itself.


## Advice: Behavior Added at Selected Points

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:45–03:57`

**Visual:**

Preserve green MATCH cells and a gray NO MATCH cell; attach clock badges only to selected rows and bracket a target call with start/finish arrows.

**Script:**

A match tells us where. We still have to say what the aspect does there.

**Purpose:**

Separate the predicate deciding WHERE to intervene from advice specifying WHAT happens.

### Scene 1 — Advice: Behavior Added at Selected Points

**Time:** `03:57–05:04`

**Visual:**

Attach a small timer block to the two matched rows; show BEFORE and AFTER log chips wrapping transferFunds without changing the service body.

**Script:**

Advice is the extra behavior executed at a selected point. Our advice can record a start time, let the target run and report elapsed time. If we want the method to retain its business meaning, the advice must preserve its result and exception unless altering those is an explicit policy. Selection and action are separate: the pointcut decides where, the advice says what happens.

**Purpose:**

Distinguish advice semantics from pointcut matching and highlight observable result preservation.


## Target: The Original Component or Behavior

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:04–05:16`

**Visual:**

Split the timed operation into an unchanged TransferService method inside and a transparent timing shell outside; display a target-only test lane.

**Script:**

The original operation has not disappeared. Let us name its role.

**Purpose:**

Show that the target still owns business correctness even when its execution is advised.

### Scene 1 — Target: The Original Component or Behavior

**Time:** `05:16–06:23`

**Visual:**

Split the diagram into target transferFunds() code and a surrounding timing shell; run the cursor through the unchanged target first.

**Script:**

The target is the original component or behavior being advised. The transfer method still knows about money movements and business validation; the timer does not take those responsibilities. We can test the target alone for transfer rules and test the effective advised execution for policy coverage. Those are two different observations.

**Purpose:**

Keep original business semantics separate from cross-cutting additions in both diagrams and tests.


## Weaving: Combining Aspects with Targets

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:23–06:35`

**Visual:**

Slide separate ServiceTiming and TransferService layers together into an effective execution band; label compile, class load and proxy placement beneath.

**Script:**

So how does the extra behavior become part of actual execution?

**Purpose:**

Advance from identifying aspect and target to how their behavior becomes composed.

### Scene 1 — Weaving: Combining Aspects with Targets

**Time:** `06:35–07:42`

**Visual:**

Show two transparent film layers labelled target and timing aspect converging into one effective execution timeline. Underneath list "compile", "load", "runtime proxy" as alternatives.

**Script:**

Weaving is the linking of aspects with a target so the extra behavior participates. It is a general idea, not one universal tool command. Some systems change bytecode before execution; others coordinate through an interception boundary when the program runs. We should therefore ask how the behavior is connected before assuming which operations it can see.

**Purpose:**

Introduce general composition and leave concrete compiler/agent/proxy configuration for the next chapter.


## Before, After, and Around Advice in an Execution Flow

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:42–07:54`

**Visual:**

Turn compile/load/proxy labels into two call timelines, success and exception; pin BEFORE, AFTER RETURNING and AFTER THROWING badges at their distinct points.

**Script:**

Now that the layers are combined, the exact position of advice in a call matters.

**Purpose:**

Use contrasting outcomes to explain advice placement rather than treating all after advice alike.

### Scene 1 — Before, After, and Around Advice in an Execution Flow

**Time:** `07:54–09:01`

**Visual:**

Animate three timelines: before at entry, after-returning only on success, after-throwing on failure, after/finally for both; around encloses the entire lane.

**Script:**

On success, before runs on entry, after-returning observes a normal result, and finally-style after work still executes. On failure, after-throwing may observe the exception while after/finally remains relevant; after-returning does not fire for a thrown exception. Around advice encloses the call and can decide whether continuation happens at all. These names describe different effects on our timeline, not interchangeable annotations.

**Purpose:**

Give success/error paths separate visual evidence and distinguish all advice kinds.


## Effective Execution, Results, and Failures with Advice

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:01–09:13`

**Visual:**

Wrap the success/failure timeline in an AROUND bracket, then branch into proceed 0, 1 and 2 lanes with target execution counters.

**Script:**

Around advice in particular can change the number of times business code actually runs.

**Purpose:**

Show how around continuation changes the number of real business executions and possible external effects.

### Scene 1 — Effective Execution, Results, and Failures with Advice

**Time:** `09:13–10:20`

**Visual:**

Build a 3-row execution table. Row A: proceed 0 → no transfer; row B: proceed 1 → one transfer; row C: proceed 2 → two transfers. Add one separate failure lane with timer cleanup and rethrown exception.

**Script:**

Watch the counter on transferFunds. If around advice returns without continuing, the target runs zero times. Continue once and one business transfer may occur. Continue twice in a model that supports it and the target can execute twice: that is not merely two log lines; it could mean two money movements. The ordinary timing policy must normally continue exactly once and preserve failures. Skipping, swallowing errors, or retrying is a business contract decision, never a harmless logging optimization.

**Purpose:**

Force the learner to reason about actual effects and exceptions rather than a simplistic always-before/after diagram.

### Scene 2 — Stress-test the model

**Time:** `10:20–11:13`

**Visual:**

Keep the same three-row table, but now play a failing transfer. Show advice logging elapsed time in a finally-shaped lane, and the original failure continuing outward unchanged.

**Script:**

There is one more trap. A timer that catches the error and returns a success-looking value has changed the contract. A safe timing concern records elapsed time even if the target throws, then lets the error propagate. The counting diagram explains how many target executions happened; the failure lane tells us whether advice has secretly changed what callers can observe.

**Purpose:**

Expose exception propagation and cleanup as a separate observable consequence from proceed count.


## A Worked Model of Concerns, Aspects, Join Points, Pointcuts, and Advice

<!-- VIDEO_SECTION -->

### Transition

**Time:** `11:13–11:25`

**Visual:**

Collapse the proceed table to one corner and assemble concern → aspect → pointcut MATCH → advice → target, plus a parallel renderPage NO MATCH lane.

**Script:**

After inspecting individual advice, put concern, match rule, and target together in one trace.

**Purpose:**

Synthesize every AOP term using selected, unselected and failing execution paths.

### Scene 1 — A Worked Model of Concerns, Aspects, Join Points, Pointcuts, and Advice

**Time:** `11:25–12:32`

**Visual:**

Two-lane dry-run with explicit trace chips: transferFunds(100) MATCH → start → target → elapsed; renderPage() NO MATCH → target only. Replay with transfer failure and log error.

**Script:**

Here is the complete model on one screen. ServiceTiming is the aspect; transfer execution is the join point; our service-selection rule is the pointcut; timing is the advice; the transfer service is the target. renderPage is not selected. We verify both matched and unmatched calls, and we run a failure case so the timer still records completion without hiding the exception.

**Purpose:**

Synthesize all five AOP terms into a falsifiable selected/unselected and success/failure trace.
