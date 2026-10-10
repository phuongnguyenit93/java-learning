---
video:
  url: ""
---

# Data-Oriented Decisions and Boundaries

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

## Choosing a Data-First or Object-Centric Approach

<!-- VIDEO_SECTION -->
### Scene 1 — Data-first or behavior-first?

**Time:** `00:00–01:31`

**Visual:**

Keep order A at center; branch into shared generic data operations and an object guarding a sensitive state transition.

**Script:**

There is no universal rule that a map with separate functions is better than an object. Generic data can work well when reports, validators and calculators all need to inspect a shared shape. But an object may be the best place to protect an invariant, control a sensitive state transition or own a lifecycle. For order A, the external boundary can receive a generic map while payment coordination remains an independently owned responsibility. The choice should reflect which contracts are clearer and safer, not the number of classes or functions in the program.

**Purpose:**

Establish actionable design criteria for data-first versus object-centric responsibilities.

## Schema Costs, Visibility, and Loss of Encapsulation

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:31–01:45`

**Visual:**

Put malformed quantity and its schema diagnosis beside the successful payload.

**Script:**

Generic maps improve visibility, but who pays for their flexibility?

**Purpose:**

Evaluate the cost of validation in the real experiment.

### Scene 2 — Open shapes require clear gates

**Time:** `01:45–03:16`

**Visual:**

Show `qty:'2'` rejected 422 and `qty:2` accepted 200 with schema tests; a separate encapsulation card marks authority over writes.

**Script:**

A generic map makes fields inspectable, but it also lets callers send a string instead of an integer, omit a price, or add unexpected keys. The real preview endpoint rejects such invalid shapes with field-specific 422 responses because its independent schema enforces the contract. Without validation, a calculator could crash or produce misleading totals. Encapsulation and static types may prevent some mistakes inside an application. Flexible data therefore carries an explicit testing, diagnostics and schema-maintenance cost.

**Purpose:**

Make the trade-off visible through two real input outcomes.

## Shared Ideas and Differences from Functional Programming

<!-- VIDEO_SECTION -->
### Transition

**Time:** `03:16–03:30`

**Visual:**

Split intersecting concepts into DOP-specific and FP-specific circles.

**Script:**

Immutable transformations overlap with functional programming, but the curricula are not identical.

**Purpose:**

Differentiate DOP representation/schema from FP function purity.

### Scene 3 — The shared region is not the whole picture

**Time:** `03:30–05:01`

**Visual:**

Venn diagram common immutable values and transformations. DOP-only: generic structures, independent schema. FP-only: referential transparency and purity. Keep the HTTP adapter outside both circles.

**Script:**

Functional programming emphasizes pure functions, referential transparency, composition and explicit effect boundaries. Sharvit-style DOP also uses non-mutating transformations, but additionally asks how data is represented and independently validated. A pure function does not have to use a generic map. And a function separated from data is not necessarily pure if it reads external state. These perspectives complement one another inside a computation but their teaching questions differ.

**Purpose:**

Avoid collapsing two paradigms into a single definition.

## Programming Model versus Memory-Layout Data-Oriented Design

<!-- VIDEO_SECTION -->
### Transition

**Time:** `05:01–05:15`

**Visual:**

Slide from generic order maps to cache lines and arrays.

**Script:**

A third data-oriented phrase refers to CPU-oriented layout, not the same programming model.

**Purpose:**

Separate conceptual DOP from performance DOD.

### Scene 4 — Memory layout is a different course

**Time:** `05:15–06:46`

**Visual:**

Compare map/list with a hypothetical struct-of-arrays and labeled cache/SIMD hardware concepts. Caption: no measured benchmark.

**Script:**

Performance-oriented data-oriented design asks how values are laid out in memory so that processors can use caches and vectorized operations efficiently. That is not Sharvit's model of generic data and separate schemas. A flexible map may even be a poor choice when locality and bulk numerical throughput dominate. We should measure workloads before claiming a performance benefit. This module intentionally stops at the distinction; low-level memory layout and SIMD have their own specialist learning paths.

**Purpose:**

Prevent unjustified speed claims and respect the curriculum boundary.

## End-to-End Decision and Handoff to Specialized Modules

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:46–07:00`

**Visual:**

Collapse each principle into one checkpoint and show the tested result statuses.

**Script:**

After weighing the trade-offs, what can our order experiment actually prove, and what remains outside its boundary?

**Purpose:**

Give an actionable synthesis and proper handoff.

### Scene 5 — One order, four principles, bounded evidence

**Time:** `07:00–08:31`

**Visual:**

Show A JSON map P2×30 Q1×40 → independent schema → 200 subtotal100 discounted90 and snapshots; error branches 422 shape and 400 duplicate JSON. Place Java typed models, payments, FP and cache performance outside.

**Script:**

Our final example begins with visible generic order data. An independent schema accepts the correct shape and rejects bad quantities before arithmetic. Separate operations calculate the subtotal of one hundred and derive a discounted value of ninety without changing the original snapshot. The running HTTP endpoint gives us evidence: 200 for valid input, 422 for a valid JSON document with wrong shape, and 400 for malformed or duplicate-key JSON. It does not charge, persist or resolve concurrent updates. The takeaway is a clearer separation of data, rules and responsibilities—not a replacement for Java modeling, transaction design or performance engineering.

**Purpose:**

Close with verifiable results and the limits of the module-owned API.
