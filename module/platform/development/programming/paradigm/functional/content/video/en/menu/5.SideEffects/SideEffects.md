---
video:
  url: ""
---

# Side Effects and Program Boundaries

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

## Side Effects and Observable External Changes

<!-- VIDEO_SECTION -->
### Scene 1 — A correct number can hide an effect

**Time:** `00:00–01:23`

**Visual:**

Show `calculate(100) → 94.5`; add a separate stock counter changing `8→7`, plus clock/log icons outside the return arrow.

**Script:**

A function can return the correct amount and still be impure if it silently changes inventory or writes a log. Even reading a changing clock creates a dependency on external state. Returning an error value is a different matter: that may be an ordinary result rather than an external operation. Functional design makes us ask what a caller can observe besides the returned value. It does not claim that real software can function without I/O.

**Purpose:**

Make side effects concrete and distinguish explicit error results from external changes.

## Pure Calculations and Effectful Operations

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:23–01:35`

**Visual:**

Rearrange the checkout diagram into an I/O shell surrounding a pricing core.

**Script:**

Now that we can see the effects, move them out of the calculation boundary.

**Purpose:**

Present a realistic pure-core/effectful-shell workflow.

### Scene 2 — Three boxes with different obligations

**Time:** `01:35–02:58`

**Visual:**

Reveal `load order/policy [I/O] → discount/tax [pure] → charge and save [I/O]`, pausing on `94.5` at the middle boundary.

**Script:**

A coordinator obtains the current order and pricing policy, then passes stable values into the pure calculation. It can compute ninety-four point five without knowing how a network works. Only afterward does the coordinator try to charge and persist. This separation makes arithmetic tests cheap and predictable, while integration tests handle provider and storage failures. The boxes do not magically make payment and persistence one atomic transaction.

**Purpose:**

Distinguish testability of the pure calculation from correctness of external coordination.

## Sequencing Effects and Coordinating State

<!-- VIDEO_SECTION -->
### Transition

**Time:** `02:58–03:10`

**Visual:**

Contrast a confirmed-before-save payment flow with a premature paid record.

**Script:**

The parts are separated. Their order still changes what users see.

**Purpose:**

Demonstrate temporal correctness and retry hazards.

### Scene 3 — The paid flag is a promise

**Time:** `03:10–04:33`

**Visual:**

Display flow `calculate→charge confirmed→save paid` in green, and `save paid→charge declined` in red; show a second `charge` arrow caused by naive retry.

**Script:**

Recording an order as paid before the provider confirms success can leave a false state. Retrying a request can also invoke the charge a second time if duplicate protection is absent. Pure functions help calculate eligibility and amounts, but the coordinator decides when to attempt effects and how to interpret their outcomes. Neither purity nor an immutable order automatically guarantees exactly-once processing or safe retries.

**Purpose:**

Show that effect sequencing and idempotency require explicit guarantees outside pricing functions.

## Error Handling and Testing at Effect Boundaries

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:33–04:45`

**Visual:**

Split the test board into a computation column and an integration column.

**Script:**

A fixed price test cannot prove that a payment provider is reliable.

**Purpose:**

Specify which evidence each test can provide.

### Scene 4 — Price assertions versus adapter failures

**Time:** `04:45–06:08`

**Visual:**

Show pure cases `empty, negative, rounding, discount` on left; simulated adapter cases `timeout, decline, duplicate response, persistence failure` on right with explicit expected handling.

**Script:**

The pricing function can be tested from fixed values: empty carts, negative inputs, valid discounts, and rounding boundaries. The adapter needs a different test set. What happens when the provider times out, declines payment, repeats a response, or a database write fails? A fake can verify the intended call order, but it cannot demonstrate real network reliability. Failure values and operational decisions should remain visible to the coordinator.

**Purpose:**

Teach test evidence boundaries rather than relying on an all-purpose mock.

## Functional Logic alongside I/O and External Services

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:08–06:20`

**Visual:**

Show an HTTP request enclosing a pure calculation and versioned save, with a competing request.

**Script:**

We have tested the calculation and the outside operations separately. What changes when both appear in one application flow?

**Purpose:**

Explain the embedded pure core and concurrent update limitation.

### Scene 5 — A pure middle inside an ordinary web request

**Time:** `06:20–07:43`

**Visual:**

Reveal `request → load/validate → compute 94.5 → version-check/save → response`; show request X and Y both reading v3 before one save is rejected.

**Script:**

A functional calculation can sit inside a conventional web service. An incoming request loads an order, computes a new value, then attempts to save the result. Reading and saving interact with the world, while the intermediate calculation can be pure. If two requests read the same version, a version check or similar coordination is still needed. Computing a valid local snapshot does not guarantee that an external payment or store operation succeeded.

**Purpose:**

Show how FP complements HTTP services without making HTTP an essential learning experiment.
