---
video:
  url: ""
---

# Pure Functions and Referential Transparency

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

## Pure Functions: Definition and Observable Behavior

<!-- VIDEO_SECTION -->
### Scene 1 — Two tests for purity

**Time:** `00:00–01:18`

**Visual:**

Show two calls `discount(100,0.1)` at different times with result 90 both times. Keep order A unchanged and no logging indicator lit.

**Script:**

A pure function has two requirements: equal inputs give equal results, and evaluation creates no observable side effect beyond its result. Our discount rule returns ninety on either call without editing the customer's cart. Creating a fresh result object is fine if nothing external changes. The property belongs to the behavior, not the function's name or an annotation in a particular language.

**Purpose:**

Use a repeatable pricing example to establish both observational criteria for purity.

## Predictability and the Cost of Hidden Dependencies

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:18–01:31`

**Visual:**

Move from a visible fixed discount to a hidden exchange rate and changing clock.

**Script:**

The calculation looks stable. What if a rate is missing from the visible inputs?

**Purpose:**

Expose the cost of invisible dependencies.

### Scene 2 — What the function signature hides

**Time:** `01:31–02:49`

**Visual:**

Place `convert(100)` twice, show `globalRate` changing from 23 to 24, then reveal differing outputs. Replace with `convert(100,23)` for a controlled trial.

**Script:**

A result can change even though the visible argument remains one hundred if the function reads an external exchange rate or clock. A failing test may then require reconstructing the machine's earlier state. Passing the rate explicitly captures what determines the answer. This does not promise that every pure function is fast; it means our reasoning depends on known data instead of an invisible environment.

**Purpose:**

Illustrate dependency injection as a reasoning boundary rather than a performance claim.

## Explicit Inputs, Results, and External State

<!-- VIDEO_SECTION -->
### Transition

**Time:** `02:49–03:02`

**Visual:**

Show a nested cart list and highlight who still holds a reference to it.

**Script:**

Explicit parameters are a start, but an object passed as an argument can still be modified.

**Purpose:**

Separate named inputs from effect-free evaluation.

### Scene 3 — The danger inside an input object

**Time:** `03:02–04:20`

**Visual:**

Highlight `calculate(order,rule)` and incoming `order.items=[A,B]`; cross out `order.items.add(C)` within the calculation, replacing it with `return newAmount`.

**Script:**

A function accepting an order parameter is not automatically pure. If it modifies the order's nested list, the caller can notice the change after the calculation. Read external data in a boundary operation, then pass the resulting values to a calculation that returns something new. The function does not need to know whether those values came from a file, a service, or a test fixture.

**Purpose:**

Prevent confusing parameter visibility with absence of mutation and effects.

## Referential Transparency and Substitution

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:20–04:33`

**Visual:**

Replace matching calls by one constant and contrast with reading a clock.

**Script:**

Once a result depends only on its values, we can reason through substitution.

**Purpose:**

Prove referential transparency through an algebraic step.

### Scene 4 — Replace the expression, keep the meaning

**Time:** `04:33–05:51`

**Visual:**

Write `twice(4)+twice(4)` → `8+8` → `16`; at the side show two clock readings crossing a changing time line.

**Script:**

If `twice(4)` is always eight and has no external effects, replacing each call by eight preserves the program's observable meaning. The final sum is sixteen. The same step fails for a function reading current time: two evaluations can disagree. That is the practical idea of referential transparency. Numerical types still have real rounding behavior, so substitution does not give permission to ignore the semantics of floating-point operations.

**Purpose:**

Use an explicit valid substitution and a clock counterexample to anchor the concept.

## Purity Violations and Testable Calculations

<!-- VIDEO_SECTION -->
### Transition

**Time:** `05:51–06:04`

**Visual:**

Split price calculation from a failing external pricing service.

**Script:**

The arithmetic can be pure while a real application still needs data from outside.

**Purpose:**

Lead into isolating effects and testing boundaries.

### Scene 5 — Pure rules inside an effectful workflow

**Time:** `06:04–07:22`

**Visual:**

Draw `loadRate [I/O] → convert(100,23) [pure] → save [I/O]`; change `loadRate` to a visible timeout without changing the arithmetic block.

**Script:**

There is no need to pretend that networks disappear in functional programming. Obtaining a live rate and saving a result are operations with observable effects. The conversion formula can live in the pure middle: give it known inputs, assert its result, and test it offline. Test the network adapter separately for timeouts and malformed replies. Keeping these responsibilities explicit tells us what a particular test can prove and where integration evidence is still needed.

**Purpose:**

Draw a testable pure-core/effectful-shell boundary without claiming external reliability.
