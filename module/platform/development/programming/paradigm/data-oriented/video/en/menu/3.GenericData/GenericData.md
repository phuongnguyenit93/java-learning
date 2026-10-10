---
video:
  url: ""
---

# Generic and Inspectable Data Representation

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

## Generic Data Structures: Maps, Lists, and Values

<!-- VIDEO_SECTION -->
### Scene 1 — Order A as maps and lists

**Time:** `00:00–01:26`

**Visual:**

Explode order A into `id`, `status` and a `lines` array of nested maps. Use distinct visual marks for strings, numbers, maps and lists.

**Script:**

Generic data structures include maps, lists, and ordinary values. For order A, the outer map contains an identifier, status and nested line items. Each line map holds a SKU, quantity and price. A viewer can inspect the shape without first learning the definition of a custom Java class. That visibility supports reuse across operations, but the structure is not automatically valid when it comes from an outside caller.

**Purpose:**

Introduce the visible representation before the schema gate.

## Representing a Small Domain as Nested Data

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:26–01:39`

**Visual:**

Insert P and Q maps into the order's `lines` array.

**Script:**

We can see the nested shape now. What happens when we give both lines real quantities and prices?

**Purpose:**

Anchor the nested representation in correct arithmetic.

### Scene 2 — Two item maps sum to one hundred

**Time:** `01:39–03:05`

**Visual:**

Show `{id:A,status:pending,lines:[{sku:P,qty:2,price:30},{sku:Q,qty:1,price:40}]}`. Highlight `2×30=60`, then `1×40=40`, then subtotal `100`.

**Script:**

The first line has two units at thirty, totaling sixty. The second line has one unit at forty, adding another forty. Together they make one hundred. Throughout this lesson, the exact keys are `sku`, `qty` and `price`; replacing them with similarly named fields changes the input contract. We show this structure as JSON because it will later be submitted over HTTP, but the programming model is about representing visible data, not about a particular serialization format.

**Purpose:**

Give a concrete nested shape and stable field names for every later experiment.

## Reusable Operations on Visible Data Shapes

<!-- VIDEO_SECTION -->
### Transition

**Time:** `03:05–03:18`

**Visual:**

Branch the unchanged JSON card toward Total and Summary.

**Script:**

One order value can be inspected by more than one function.

**Purpose:**

Show generic data as reusable input, not behavior hidden inside it.

### Scene 3 — Two operations, one map

**Time:** `03:18–04:44`

**Visual:**

Keep the same JSON on left. Right: `total(lines) → 100`; below `summary(order) → 'A: 2 lines'`. Emphasize no in-place edit arrow.

**Script:**

One independent operation multiplies quantity by price for each line; another makes a short human-readable summary. Both can work on the same visible representation without embedding those methods into the map. But neither operation is free to guess what missing fields mean. A price omitted from an order must not silently become zero, and every operation should state which keys it requires. The next input will deliberately violate those expectations.

**Purpose:**

Demonstrate generic operation reuse with two different observable outputs.

## Missing Keys, Invalid Shapes, and Ambiguous Values

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:44–04:57`

**Visual:**

Replace one valid item with three error variants, keeping the previous subtotal crossed out.

**Script:**

The successful example depends on the caller using the promised field names.

**Purpose:**

Teach failure modes without generating a plausible incorrect total.

### Scene 4 — A typo changes the contract

**Time:** `04:57–06:23`

**Visual:**

Display a three-row comparison: `qty:'2'`, missing `price`, and `quantitty:2`. Place error paths `lines[1].qty` and `lines[1].price` beside the variants; do not recompute the subtotal.

**Script:**

Generic maps make wrong shapes easy to express. A string `'2'` is not an integer under our schema, an absent price cannot be treated as zero, and `quantitty` does not stand for `qty`. A calculator that guesses at these mistakes can return a credible but incorrect amount. Instead, validation should reject the specific field path and make the caller correct the input. Here the line index matters: `lines[1]` names the second line.

**Purpose:**

Make malformed nested fields visibly different from arithmetic mistakes.

## Flexibility, Static Contracts, and the Need for Schemas

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:23–06:36`

**Visual:**

Move the incorrect field behind a validation gate and reveal the rules one by one.

**Script:**

These maps are flexible, but the wrong keys can silently change their meaning. What rules should guard the next calculation?

**Purpose:**

Bridge generic representations to separately defined schemas.

### Scene 5 — The map and its rule book

**Time:** `06:36–08:02`

**Visual:**

Place order A alongside `lines:1..20`, `qty: integer 1..10000`, `price: integer 0..1000000`, `status:pending`; a separate SCHEMA gate precedes total.

**Script:**

A typed model might catch some mistakes before runtime, while a generic map needs its expectations made explicit. Sharvit's approach can keep an independent schema that documents the allowed field names, nested shapes and numeric ranges. At an untrusted boundary, data must pass those checks before the calculation runs. The rules live outside the particular value so they can be inspected and tested independently. In the Schema chapter we will exercise that gate using actual HTTP requests.

**Purpose:**

Explain what static guarantees are absent and prepare the live trust-boundary experiment.
