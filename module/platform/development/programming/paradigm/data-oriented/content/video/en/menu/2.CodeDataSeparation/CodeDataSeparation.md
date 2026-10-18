---
video:
  url: ""
---

# Separating Code and Data

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

## Data Values and Functions as Separate Responsibilities

<!-- VIDEO_SECTION -->
### Scene 1 — A data value beside a separate operation

**Time:** `00:00–01:17`

**Visual:**

Place order A as a plain `{id,lines}` value and a `total(order)` function box outside it. Highlight the arrow carrying data, not a hidden method on the map.

**Script:**

Order A holds supplied values: an identifier, status, and nested line items. The rule for summing quantity multiplied by price can be a separate operation rather than behavior attached to every data object. The same value can feed a report or a total calculator. That makes reuse and testing more direct, but separation alone does not imply that every function is pure; a poorly designed function could still read global state.

**Purpose:**

Teach code-data responsibility separation without claiming automatic purity.

## Behavior-Bearing Objects and Reusable Data

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:17–01:30`

**Visual:**

Keep the plain order visible; bring an object lock icon alongside it.

**Script:**

The data is reusable. Should every object now lose its methods?

**Purpose:**

Respect legitimate object-owned invariants.

### Scene 2 — Objects still have valuable responsibilities

**Time:** `01:30–02:47`

**Visual:**

Contrast an Order object protecting a status transition against a plain map read by two reporting functions. Check both when appropriate.

**Script:**

An object can legitimately protect invariants and control who may change a state. That responsibility may be more coherent inside the object than in a scattered set of functions. Data-oriented programming asks us to separate generic data processing when it improves visibility and reuse, not to remove encapsulation everywhere. The key question is whether a method owns essential behavior or merely hides information many independent operations need.

**Purpose:**

Show the ownership trade-off rather than declaring a winner.

## Inspecting and Sharing Data across Operations

<!-- VIDEO_SECTION -->
### Transition

**Time:** `02:47–03:00`

**Visual:**

Split one unchanged snapshot into total and line-count branches.

**Script:**

We have separated responsibilities; let two operations read the same input.

**Purpose:**

Demonstrate inspectability through multiple independent queries.

### Scene 3 — Two readers, one snapshot

**Time:** `03:00–04:17`

**Visual:**

Display `A.lines=[P:2×30,Q:1×40]` flowing into `total=100` and `lineCount=2` without any WRITE arrow.

**Script:**

The pricing operation computes one hundred from the same order that a simple report counts as two lines. Neither consumer needs to know the other one's implementation. Sharing a stable documented value increases opportunities to inspect and transform it. But a freely mutable shared map could be changed from another place between those reads. We therefore need explicit decisions about immutability and about what incoming field names are permitted.

**Purpose:**

Show observable reuse plus the remaining need for stable ownership.

## Encapsulation and Coupling Trade-offs of Separation

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:17–04:30`

**Visual:**

Turn the successful `qty` read into an accidental `quantity` field at the input boundary.

**Script:**

Easy-to-read maps carry a hidden cost: a misspelled field name.

**Purpose:**

Lead from flexibility to independent schema checks.

### Scene 4 — Open representation, explicit responsibilities

**Time:** `04:30–05:47`

**Visual:**

Replace `qty:2` with `quantity:2`, then display `expected qty` beside the transformation, without substituting zero. Add a boundary schema card.

**Script:**

A map is flexible, but flexibility means a caller can send a different shape. If our operation silently treats a missing quantity as zero, the total may look reasonable while being wrong. A strongly typed object model might reject some mistakes earlier. Generic data needs explicit shape rules, tests, and diagnostics that tell the caller which path is incorrect. Separating data and code is useful only when those responsibilities are made clear rather than ignored.

**Purpose:**

Explain why schema validation will become an essential fourth principle.
