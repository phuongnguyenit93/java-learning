---
video:
  url: ""
---

# Immutable Data and Application State

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

## Immutable Values in the Data-Oriented Model

<!-- VIDEO_SECTION -->
### Scene 1 — Keep the original order snapshot

**Time:** `00:00–01:25`

**Visual:**

Place order A `{status:pending,total:100}` on the board; prepare a discount arrow without editing that card.

**Script:**

An immutable value is not modified after creation. Our order A begins with a subtotal of one hundred, and a ten-percent discount can derive a separate result of ninety without changing the original. We can keep both snapshots on screen and compare exactly what changed. However, a fixed outer reference is not proof that a nested list cannot still be modified by another owner. Data-oriented immutability requires us to understand the full reachable structure, not just an immutable variable binding.

**Purpose:**

Demonstrate preserved old/new values while identifying the nested mutable-alias caveat.

## Updating Data by Producing a New Version

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:25–01:38`

**Visual:**

Bring a discount parameter into a function box beside order A.

**Script:**

Two snapshots are possible; let's derive one without touching the other.

**Purpose:**

Demonstrate a real transformation using the validated API shape.

### Scene 2 — Calculate a new order result

**Time:** `01:38–03:03`

**Visual:**

Display `discount(orderA,10%)` returning `subtotal:100,discountedTotal:90`. Expand `before.lines` and `after.lines`, preserving both P and Q and `status:pending`.

**Script:**

A valid order with P two times thirty and Q one times forty has subtotal one hundred. Our independent operation calculates a ten-percent discount and derives a result of ninety. The real HTTP 200 preview exposes both `before` and `after` objects, while their item quantities remain unchanged. Importantly, the status remains pending; pricing does not authorize a payment transition. The response is useful precisely because we can compare the original nested values with a new derived amount.

**Purpose:**

Tie abstract immutability to actual API output without inventing persistence.

## Snapshots, Comparison, and Change History

<!-- VIDEO_SECTION -->
### Transition

**Time:** `03:03–03:16`

**Visual:**

Line up the snapshots on a timeline but keep the database box disconnected.

**Script:**

We can compare before and after; does returning two values mean they were saved?

**Purpose:**

Separate inspectable history from persisted business state.

### Scene 3 — Calculated snapshots are not persisted history

**Time:** `03:16–04:41`

**Visual:**

Table A1 before100 pending and A2 after90 pending; put `payment:false` and `persistence:false` beneath the response.

**Script:**

The response supplies two values for comparison, not a durable history. A persistent application could save versions with suitable coordination, but this preview endpoint does not write anything and does not charge a card. That distinction matters when we interpret tests: observing before and after proves a non-mutating transformation for the accepted shape, not that a business transaction occurred. The difference between calculating a new value and committing it remains visible.

**Purpose:**

Limit the inference supported by response snapshots.

## State Coordination and Concurrency Boundaries

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:41–04:54`

**Visual:**

Split one version 3 into two proposed version 4 snapshots, then focus on a save gate.

**Script:**

Even correct immutable versions need a decision about which one becomes current.

**Purpose:**

Explain concurrency without claiming immutable data solves races.

### Scene 4 — Two correct proposals can conflict

**Time:** `04:54–06:19`

**Visual:**

Draw X and Y both reading A version3, producing A4x and A4y. `saveIfVersion(3)` accepts one, rejects the other; both snapshots remain untouched.

**Script:**

If two requests read the same stored order version, they can independently compute valid immutable next values. Those values do not choose the winner. A store must detect stale writes through version checks or another coordination mechanism. Immutability prevents unexpected edits to a value; it does not guarantee transactions, consistency, or safe payment retries. Our learning endpoint deliberately has no store, so this remains a conceptual boundary rather than an invented feature.

**Purpose:**

Show the exact scope of immutability before external trust-boundary validation.
