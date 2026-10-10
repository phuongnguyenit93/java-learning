---
video:
  url: ""
---

# Data-Oriented Programming: Purpose and Core Model

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

## Data-Oriented Programming: Meaning and Module Scope

<!-- VIDEO_SECTION -->
### Scene 1 — One order, two organizational views

**Time:** `00:00–01:22`

**Visual:**

Place order A with id A, items P and Q at center. Left: an object with methods `addItem` and `total`. Right: a plain map `id/lines` passed to `total(order)`; no network request yet.

**Script:**

The same order can be modeled as an object with behavior or as data passed into separate operations. For order A, we will use the latter view to inspect its shape, validate it, and derive new values. Data-oriented programming does not rule out objects. It asks a practical question: when several operations need to inspect the same information, which organization makes the data and responsibilities clearest?

**Purpose:**

Open with the concrete model and the scope of data-oriented programming.

## The Cost of Coupling Data to Its Operations

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:22–01:36`

**Visual:**

Carry the order card across pricing, reporting and policy-check jobs; mark the shared object-method coupling.

**Script:**

The representation looks simple; what changes when several people need different operations on it?

**Purpose:**

Show the source of coupling without dismissing OOP.

### Scene 2 — When operations own too much data access

**Time:** `01:36–02:58`

**Visual:**

Show three features calling the same Order class with methods; introduce a new audit operation and mark all the behavior-oriented dependencies needing coordination.

**Script:**

A method on an Order object can be exactly right for a small, cohesive responsibility. Yet reporting, pricing, and auditing may need to inspect the same values through different processes. When every new operation depends on an object's private behavior surface, reuse can become awkward. Separating data from operations lets these consumers share a documented shape, at the cost of needing deliberate validation and ownership. The lesson is a trade-off, not an attack on object-oriented design.

**Purpose:**

Make the WHY tangible and preserve legitimate object responsibilities.

## Starting Point: Values, Collections, and Existing Paradigms

<!-- VIDEO_SECTION -->
### Transition

**Time:** `02:58–03:12`

**Visual:**

Replace the three feature boxes with simple value/list/map/function cards.

**Script:**

Before discussing schemas, identify the few basic building blocks on our screen.

**Purpose:**

Bridge novice prerequisites with observable values.

### Scene 3 — Values, collections and functions

**Time:** `03:12–04:34`

**Visual:**

Reveal number `30`, list `[P,Q]`, map `{sku:P,qty:2,price:30}`, and `total(lines)`. Place `2×30+1×40` as an unfinished calculation.

**Script:**

We only need familiar foundations: numbers and text are values, lists collect several values, maps associate field names with values, and functions accept inputs to produce outputs. Order A has item P with quantity two at thirty, and item Q with quantity one at forty. Later a learner will submit JSON over HTTP, but JSON is a transport representation here, not a definition of this paradigm. First learn what the data means.

**Purpose:**

Ensure beginner entry before introducing generic data and runtime validation.

## Objects, Functions, and Data as Different Design Views

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:34–04:48`

**Visual:**

Shift the map into three boxes Data, Operation, Coordinator.

**Script:**

We can read the map now. Which work belongs to data, and which work belongs elsewhere?

**Purpose:**

Distinguish source data, pure computation, and external action.

### Scene 4 — A map cannot charge a card

**Time:** `04:48–06:10`

**Visual:**

Display `order map → total(order) → 100`; keep a separate `checkout/gateway` box beyond a marked boundary.

**Script:**

An order map holds information supplied by a caller. An independent function calculates the subtotal. A coordinator might later decide whether to store the new state or request a payment. For A, two times thirty is sixty, and one times forty is forty, so the subtotal is one hundred. That arithmetic is not a payment. Real persistence and transactions have their own constraints. Keeping the boundary visible will prevent our demonstration from claiming effects it never performs.

**Purpose:**

Teach ownership through one complete and bounded calculation.

## Sharvit's Four Principles and Their Relationships

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:10–06:24`

**Visual:**

Surround order A with four principle cards and a small processing pipeline.

**Script:**

Data, operations and coordination are distinct. How do Sharvit's principles reinforce that distinction?

**Purpose:**

Connect all four principles causally.

### Scene 5 — Four principles in one order flow

**Time:** `06:24–07:46`

**Visual:**

Reveal separate code/data, generic map/list, immutable snapshots, independent schema. Highlight `receive → validate → total → new value`, ending 100→90.

**Script:**

Sharvit's data-oriented programming combines four ideas: separate operations from the data representation; use generic inspectable data structures such as maps and lists; transform without mutating the original shared value; and describe the valid shape in a separate schema. Order A can therefore be submitted, validated, totaled at one hundred, and transformed to a discounted value of ninety. A flexible map with no validation is not enough. Each principle addresses a different source of complexity.

**Purpose:**

Demonstrate the relationship among principles, not a memorized inventory.

## Sharvit DOP, Java DOP, and Performance-Oriented DOD

<!-- VIDEO_SECTION -->
### Transition

**Time:** `07:46–08:00`

**Visual:**

Split the board into Sharvit, Java Amber and performance DOD columns.

**Script:**

The word data-oriented has other uses; we need a clear boundary.

**Purpose:**

Prevent false equivalence of similarly named approaches.

### Scene 6 — Three data-centered interpretations

**Time:** `08:00–09:22`

**Visual:**

Column Sharvit: generic map/list plus schema. Java Amber: typed record/sealed data variants. Performance DOD: memory locality, cache and SIMD. Keep the same order card without equality arrows.

**Script:**

Java's Project Amber discussion also calls its approach data-oriented programming, but emphasizes transparent immutable types, precise valid variants and operations outside data. Sharvit's model favors generic data and independently stated schemas. Performance-focused data-oriented design is different again: it asks how data layout affects CPU caches and throughput. They may share motivations, but they are not the same curriculum. We will primarily develop Sharvit's model here.

**Purpose:**

Maintain technical ownership of distinct traditions.

## Learning Sequence from Data Representation to Decisions

<!-- VIDEO_SECTION -->
### Transition

**Time:** `09:22–09:36`

**Visual:**

Turn the four principles into an eight-chapter path linked to order A.

**Script:**

We have separated these three meanings of data-oriented. How will we build our understanding of order A, one responsibility at a time?

**Purpose:**

Conclude with a prerequisite-safe chapter handoff.

### Scene 7 — Eight stops for order A

**Time:** `09:36–10:58`

**Visual:**

Show introduction→separation→generic data→immutability→schema→flow→Java perspective→tradeoffs. Attach `2×30+1×40=100`, discount10%→90, and real API codes 200/422 to schema/flow.

**Script:**

First we separate code from data, then represent A using maps and lists. We will preserve old values while deriving a discounted result. An independent schema will reject malformed incoming shapes before calculations begin. In the full flow lesson we can submit actual HTTP requests and see a valid two-line order return 200 while invalid quantities return 422. Only after that will we contrast Java's typed view and choose among design styles. Each chapter adds one responsibility to the same example.

**Purpose:**

Provide a coherent progression with genuinely implemented runtime evidence.
