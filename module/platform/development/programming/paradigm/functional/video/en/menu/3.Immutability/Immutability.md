---
video:
  url: ""
---

# Immutable Values and Changing State

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

## Immutable Values and Mutable References

<!-- VIDEO_SECTION -->
### Scene 1 — The previous order remains visible

**Time:** `00:00–01:18`

**Visual:**

Show `old.items=[A,B]`, then add item C by creating `next.items=[A,B,C]`. Keep both cards on screen and point to a nested-list warning.

**Script:**

An immutable value is not modified after creation. When we add an item, the old order can remain `[A,B]` while the new order includes C. That makes old snapshots useful for comparison and testing. Yet a fixed outer reference is not proof of deep immutability: a mutable nested list can still change underneath it. We must be explicit about which objects really cannot be altered and which references are merely stable.

**Purpose:**

Make immutable snapshots tangible, including the nested-mutation caveat.

## Values, Identity, and State over Time

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:18–01:31`

**Visual:**

Pin order ID A above two separate value cards pending and paid.

**Script:**

Two snapshots can refer to one logical order. Who selects the current one?

**Purpose:**

Explain identity versus immutable values and evolving state.

### Scene 2 — An identity with successive values

**Time:** `01:31–02:49`

**Visual:**

Reveal `Order A` snapshot 1 `{status:pending}` and snapshot 2 `{status:paid}`; move a `current` marker while leaving snapshot 1 intact.

**Script:**

An order identity persists through time, while its value can be represented by successive immutable snapshots. Order A was pending and is now paid, but the earlier pending version still exists for analysis. The application is therefore stateful: some coordinator must choose which version is current and record that decision. Functional values make histories easier to reason about; they do not magically remove the need to manage state.

**Purpose:**

Separate temporal state selection from in-place value mutation.

## State Transitions through New Values

<!-- VIDEO_SECTION -->
### Transition

**Time:** `02:49–03:02`

**Visual:**

Show a pure transition function and a payment gateway outside its border.

**Script:**

Now we can derive a candidate next value without claiming that a real payment succeeded.

**Purpose:**

Keep the payment confirmation boundary explicit.

### Scene 3 — Compute a transition from known evidence

**Time:** `03:02–04:20`

**Visual:**

Use `transition(pending, PaymentApproved) → paid` and `transition(pending, PaymentDeclined) → rejection`; place the gateway response as required input.

**Script:**

Given a confirmed event, a transition function can derive the next order value without mutating the old value. An approved event may produce a paid snapshot; a declined event may produce an explicit rejection. But the function cannot fabricate the approval. Contacting the gateway and deciding when to save the new version are external effects. That distinction keeps the value transformation pure without pretending the real transaction is pure.

**Purpose:**

Show the difference between interpreting an observed event and causing it in the world.

## Structural Sharing and Allocation Trade-offs

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:20–04:33`

**Visual:**

Zoom into a tree with three branches and a single changed leaf.

**Script:**

Keeping snapshots is useful, but repeatedly copying a large structure sounds expensive.

**Purpose:**

Introduce persistent structure sharing accurately.

### Scene 4 — Only the changed path needs new nodes

**Time:** `04:33–05:51`

**Visual:**

Show roots R1 and R2 sharing frozen branches A and B, with a newly allocated path to C2 highlighted. Caption `implementation technique, not universal guarantee`.

**Script:**

A persistent immutable structure can save work by creating a new path for a change while reusing the unchanged branches. The old root still points to valid data, and neither snapshot can accidentally rewrite a shared frozen branch. That is structural sharing. It depends on the data structure: not every immutable collection implements it. A real performance decision still needs measurements of allocation, lookup, and memory pressure.

**Purpose:**

Explain conditional structural sharing and its costs through a concrete tree.

## Immutability, Concurrency, and Practical Limits

<!-- VIDEO_SECTION -->
### Transition

**Time:** `05:51–06:04`

**Visual:**

Show two requests reading the same version, then a conflict at save.

**Script:**

Immutable candidates do not decide how a concurrent write wins.

**Purpose:**

Prevent false thread-safety and transaction guarantees.

### Scene 5 — Two competing version-four snapshots

**Time:** `06:04–07:22`

**Visual:**

Render `X: v3→v4X` and `Y: v3→v4Y`; a `saveIfVersion(3)` gate accepts one and rejects the stale other. Keep payment retry outside.

**Script:**

Both requests can read order version three and create different immutable version-four values. Each calculation may be sound, yet the shared store cannot simply accept both as the latest version. Version checks or another coordination mechanism must resolve the conflict. Likewise, an immutable receipt does not make a card charge idempotent. Immutability prevents accidental changes to values; coordination and external effects remain separate engineering problems.

**Purpose:**

Contrast snapshot safety with consistency of persisted state and effects.
