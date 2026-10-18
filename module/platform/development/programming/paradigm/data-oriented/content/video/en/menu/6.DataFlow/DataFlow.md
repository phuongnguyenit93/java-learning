---
video:
  url: ""
---

# An End-to-End Data Transformation Flow

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

## A Small Application Scenario and Its Data Boundaries

<!-- VIDEO_SECTION -->
### Scene 1 — Trace order A through five responsibilities

**Time:** `00:00–01:31`

**Visual:**

Draw RECEIVE→VALIDATE→TOTAL→DERIVE→RETURN; keep payment and persistence outside the experiment's border.

**Script:**

Let's trace a real request through our bounded order preview endpoint. The caller supplies order A with id A, pending status, and two lines: P at two times thirty and Q at one times forty. A separate schema validates the nested map before any arithmetic. Then an independent operation totals one hundred and derives a ten-percent discounted result of ninety. HTTP 200 exposes both before and after snapshots for comparison. The pipeline ends at the response; it does not charge a card, save a database row or commit shared state.

**Purpose:**

Ground the complete receive–validate–calculate–derive–respond pipeline in tested API evidence and explicit limits.

## Input Data, Shape Checks, and Rejected Values

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:31–01:45`

**Visual:**

Branch red at VALIDATE while leaving the TOTAL and DERIVE boxes disabled.

**Script:**

The successful path assumes valid data. Where does a wrong shape stop the flow?

**Purpose:**

Demonstrate that invalid input is rejected before arithmetic.

### Scene 2 — The failed request never reaches pricing

**Time:** `01:45–03:16`

**Visual:**

Try missing Q.price and negative Q.qty; display 422 with `lines[1].price` or `lines[1].qty`. On a separate branch show duplicate nested qty JSON returning 400 at PARSE.

**Script:**

If we omit the second line's price, the server must not invent a zero and produce a plausible subtotal. The shape validator returns HTTP 422 with a precise path to `lines[1].price`. A quantity of minus three similarly yields `lines[1].qty`. But repeating the qty key within the same JSON object is a different problem: strict parsing rejects it as HTTP 400 before validation. These failure paths help us locate responsibility rather than treating every invalid request as one generic computation error.

**Purpose:**

Show the transport parser, independent schema and calculator as separate actors.

## Independent Operations and Immutable Transformations

<!-- VIDEO_SECTION -->
### Transition

**Time:** `03:16–03:30`

**Visual:**

Pin before and after lists next to each other with fields highlighted.

**Script:**

Only accepted data can be transformed; compare the two returned snapshots closely.

**Purpose:**

Prove non-mutating transformation through actual response values.

### Scene 3 — Old item data, newly derived totals

**Time:** `03:30–05:01`

**Visual:**

Expand real HTTP200 JSON: `before.lines` and `after.lines` both P(2,30),Q(1,40), `before.status=pending`, `after.status=pending`, `after.subtotal=100`, `discountedTotal=90.00`.

**Script:**

The calculator reads only values that have passed the boundary check. It sums quantity times price for each line, then derives a new result with subtotal and discounted total. The response exposes both the original and derived snapshots, so the viewer can verify that item quantities and status have not been changed. This is evidence of this preview transformation, not a universal claim about all maps or the rest of a payment system. We are observing explicit data, operations and results.

**Purpose:**

Tie observed snapshot equality to the exact transformation code's contract.

## Coordinating New State and External Effects

<!-- VIDEO_SECTION -->
### Transition

**Time:** `05:01–05:15`

**Visual:**

Keep payment and database boxes greyed out while focusing on the API's false-effect flag.

**Script:**

A derived order result is not the same as a committed business state.

**Purpose:**

Clarify what this endpoint intentionally does not do.

### Scene 4 — The preview's stopping point

**Time:** `05:15–06:46`

**Visual:**

Display `externalPaymentOrPersistencePerformed:false` beneath response; separate X/Y proposed snapshots from hypothetical version3 into an unconnected storage gate.

**Script:**

Calculating ninety does not charge a customer or mark an order paid. A real coordinator would have to decide when to persist a version, what to do after a gateway decline, and how to handle two writes based on the same prior version. Immutable values do not solve those questions on their own. Our preview endpoint intentionally avoids persistence, shared state and payment so the lesson can focus on the trust boundary and the transformation. A real checkout would need separate engineering evidence.

**Purpose:**

Prevent false transaction or concurrency guarantees.

## Tracing Data Versions, Failures, and Tests

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:46–07:00`

**Visual:**

Collect accepted, rejected and parser-invalid cases with actual statuses.

**Script:**

We can now trace each outcome to its own boundary. Which promises should a test check, beyond seeing a green 200?

**Purpose:**

Make verification practical beyond one happy-path screenshot.

### Scene 5 — An explicit evidence matrix

**Time:** `07:00–08:31`

**Visual:**

Show table: valid A 200 subtotal100→90; negative quantity 422 lines[1].qty; duplicate status key 400; discount0 gives 100; discount100 gives 0. Keep versioned storage checks labelled outside scope.

**Script:**

One green response is insufficient evidence. We assert the numbers and the preserved fields inside before and after. For an invalid shape we check a specific nested path, not just that the request failed. Duplicate JSON keys must produce a parser-level 400 rather than silently picking one value. Discount boundaries also matter: zero percent must leave one hundred, and one hundred percent must yield zero. Each row corresponds to a contract the running API actually exposes; persistence conflicts belong elsewhere.

**Purpose:**

End the flow lesson with testable claims instead of an invented integration result.
