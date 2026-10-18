---
video:
  url: ""
---

# Functions as Values and Composed Transformations

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

## First-Class Functions as Data and Behavior

<!-- VIDEO_SECTION -->
### Scene 1 — Carry a pricing rule without calling it

**Time:** `00:00–01:23`

**Visual:**

Reveal `regular=(p)=>p` and `vip=(p)=>p*0.9`, assign the VIP function to `chosen`, then invoke `chosen(100)` to show 90.

**Script:**

We can treat a function as a value: select a behavior, pass it to another part of the program, and call it later. Choosing the VIP pricing rule does not run the calculation. Calling that selected function with one hundred produces ninety. This is the first-class-function idea; we have not needed Java lambda syntax. And carrying a function as a value does not make its behavior pure—it might still log or read shared state.

**Purpose:**

Separate first-class selection from invocation while preserving the purity caveat.

## Higher-Order Functions and Reusable Processing

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:23–01:35`

**Visual:**

Show the same input list and swap just the function-valued argument.

**Script:**

Once behavior can be passed, one traversal can accept different processing rules.

**Purpose:**

Demonstrate the higher-order abstraction concretely.

### Scene 2 — A reusable transformation

**Time:** `01:35–02:58`

**Visual:**

Keep `[10,20]` stationary while changing `transform(items,double)` → `[20,40]` to `transform(items,add5)` → `[15,25]`; highlight which part of the diagram stays unchanged.

**Script:**

A higher-order function receives or returns another function. Here the traversal remains the same, but the caller supplies different processing behavior. We can reuse the shape of a transformation without rewriting the loop. This feature is independent of purity. If the passed function reads a changing global value, the computation still has hidden dependencies. Higher-order functions help us organize behavior; purity helps us reason about it.

**Purpose:**

Prove reuse through one controlled change and distinguish higher-order abstraction from pure results.

## Composition of Input-to-Output Functions

<!-- VIDEO_SECTION -->
### Transition

**Time:** `02:58–03:10`

**Visual:**

Bring back order A and pin the intermediate value at each boundary.

**Script:**

Passing a function is useful; composing results gives us an explainable sequence.

**Purpose:**

Ground composition in explicit intermediate outputs.

### Scene 3 — Connect discount and tax

**Time:** `03:10–04:33`

**Visual:**

Animate `100 → discount10% → 90 → tax5% → 94.5`, with 4.5 displayed as tax and 94.5 as amount due.

**Script:**

The result of the first calculation becomes the input to the second. Discounting one hundred by ten percent yields ninety; five percent tax on ninety is four point five, so the amount due is ninety-four point five. That is composition: separate rules joined by values. These operations are not universally interchangeable. Currency rounding and business order may change the result if we reverse or restructure the steps.

**Purpose:**

Make composition verifiable with numeric evidence while retaining ordering constraints.

## Mapping, Filtering, and Aggregating Collections

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:33–04:45`

**Visual:**

Replace the order with `[20,40,50]` and progressively reveal filter, map and reduce.

**Script:**

One item passed through two stages; let's do the same for a small collection.

**Purpose:**

Teach each transform through its visible effect on actual data.

### Scene 4 — Filter, map, then sum

**Time:** `04:45–06:08`

**Visual:**

Show `[20,40,50]`; remove 20 for `>=30`; transform `[40,50]` to `[36,45]` using a 10% discount; calculate `0+36+45=81`.

**Script:**

First we filter: only amounts at least thirty remain, so we keep forty and fifty. Mapping applies a discount to each of those values, giving thirty-six and forty-five. Reducing from the identity value zero adds the results to eighty-one. These words describe meaningful operations on values; they are not Java Stream syntax requirements. The reduction rule and any external effects would need explicit contracts before reordering stages.

**Purpose:**

Build a full stage-by-stage numerical proof for the three operations.

## A Complete Transformation from Input to Output

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:08–06:20`

**Visual:**

Replace the numeric array by three approved/pending order cards.

**Script:**

The arithmetic is clear; now the same steps can process records with meaningful states.

**Purpose:**

Tie together data selection, pricing, and a stable snapshot.

### Scene 5 — A, B, and C make eighty-one

**Time:** `06:20–07:43`

**Visual:**

Display A(approved,40), B(pending,20), C(approved,50). Filter pending B out, project amounts, apply discounts, and sum 36+45.

**Script:**

Our next filter is not a price threshold: it selects approved orders. A and C pass, B is still pending. Their amounts are forty and fifty; after discount those values are thirty-six and forty-five, totaling eighty-one. Notice that both examples reach eighty-one for different selection reasons. That is why each stage must be named and its intermediate data checked. In a real workflow we first need a stable snapshot and a stated rounding policy.

**Purpose:**

Explain semantic stage roles, not just a final numeric coincidence.

## Readable Composition and Overly Complex Pipelines

<!-- VIDEO_SECTION -->
### Transition

**Time:** `07:43–07:55`

**Visual:**

Compare ten unnamed function boxes to three named business steps with visible values.

**Script:**

A readable pipeline makes failures local; excessive composition can do the opposite.

**Purpose:**

Show a practical stopping criterion for composition.

### Scene 6 — When fewer steps are clearer

**Time:** `07:55–09:18`

**Visual:**

Show `f1→f2→...→f10` with unclear debugging point versus `validateOrder → calculateDiscount → buildReceipt`, each with a labeled intermediate output.

**Script:**

A chain of unnamed transformations may hide more than it reveals. If the output is wrong, the first question is which meaningful stage made the wrong decision. Give stages business names and expose intermediate values when needed. Also avoid hiding networking inside a function promised to be pure. If a straightforward local loop communicates intent with less work for the reader, it is a valid design choice. The next chapter will separate real-world effects from these transformations.

**Purpose:**

End with readability and effect-boundary criteria rather than a blanket preference for chains.
