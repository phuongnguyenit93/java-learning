---
video:
  url: ""
---

# Data Shapes, Schemas, and Validation Boundaries

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

## Data Shapes and Separate Schema Descriptions

<!-- VIDEO_SECTION -->
### Scene 1 — Data and its separate rule book

**Time:** `00:00–01:30`

**Visual:**

Put order A's nested JSON beside a schema rules card: id string, status pending, lines 1..20, qty positive integer, price nonnegative integer.

**Script:**

A concrete order value is not the same thing as the rules that describe which order values are allowed. One independent schema can validate many incoming maps. Here the rules define field names, nested arrays, lengths, integer quantities and acceptable prices. A map could hold a string for quantity, but our declared contract rejects it. This separation of data from its schema is one of Sharvit's four data-oriented principles; it is different from embedding the validator as a method into each data value.

**Purpose:**

Present the independent shape contract before running the HTTP demonstration.

## Flexible Data and Selective Schema Validation

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:30–01:43`

**Visual:**

Highlight the red untrusted-JSON boundary and a green zone for already checked internal values.

**Script:**

A schema exists, but where does validation add the most value?

**Purpose:**

Explain the correct role of boundary checks.

### Scene 2 — Validate where trust changes

**Time:** `01:43–03:13`

**Visual:**

Show input JSON → OrderShapeSchema → calculation; route a string quantity to the rejection branch before the calculation begins.

**Script:**

External callers can omit keys, send a different nested structure, or use a string where an integer is required. The right time to reject that shape is before arithmetic starts. A short-lived internal value with a known contract does not require a full external input validation routine after every line of code. Separating the gateway from calculation makes validation independently testable and prevents a misleading total produced by guessing what bad fields should mean.

**Purpose:**

Connect selective validation with untrusted data and downstream assumptions.

## Validation at Input, Output, and Trust Boundaries

<!-- VIDEO_SECTION -->
### Transition

**Time:** `03:13–03:26`

**Visual:**

Move the schema diagram into live HTTP request and response panels.

**Script:**

We can now test two payloads with only one field changed.

**Purpose:**

Prove accepted versus rejected shapes with actual responses.

### Scene 3 — Order A passes; invalid quantity fails

**Time:** `03:26–04:56`

**Visual:**

Submit POST /paradigm/data-oriented/orders/preview?discountPercent=10. Show P qty2 price30 and Q qty1 price40 returning HTTP200, then Q.qty=-3 returning HTTP422 with errors path lines[1].qty.

**Script:**

The first request supplies a valid nested order. Its subtotal is two times thirty plus one times forty, or one hundred, and the discount produces ninety. For the second request, we change only Q's quantity to minus three. JSON parsing still succeeds, but the independent shape contract rejects the negative quantity. The real response is HTTP 422 with accepted=false and an error path lines[1].qty. It does not calculate a plausible but incorrect amount, showing why the trust-boundary experiment adds practical evidence.

**Purpose:**

Use actual HTTP responses to establish schema validation causally.

## Invalid Data, Error Information, and Recovery Choices

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:56–05:09`

**Visual:**

Split the pipeline into a parse-400 route and a shape-422 route.

**Script:**

The validation error is not the same as a document the JSON parser cannot interpret.

**Purpose:**

Make parser and schema failures separate.

### Scene 4 — Malformed input and wrong shape differ

**Time:** `05:09–06:39`

**Visual:**

Compare duplicate JSON status or nested qty fields →400 with well-formed status paid →422 and actual string value paid. Show a long id diagnostic reporting only length.

**Script:**

Malformed JSON and duplicate object keys are rejected by strict parsing before the independent schema sees a map, so the endpoint returns HTTP 400. A syntactically valid map with status paid violates the preview rule and returns 422 with a field-specific diagnostic. Diagnostics must not echo arbitrary untrusted strings without limits; our tested implementation bounds values and unknown-field names. These responses illustrate two separate contracts rather than treating every bad request as the same error.

**Purpose:**

Distinguish status 400 versus 422 and safe error-message evidence.

## Schema Evolution and the Cost of Loose Representations

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:39–06:52`

**Visual:**

Place hypothetical schema v2 next to v1, with client compatibility choices.

**Script:**

The current rules work, but a public data contract has to evolve carefully.

**Purpose:**

Discuss evolution without claiming to have implemented versioning.

### Scene 5 — New fields affect old clients

**Time:** `06:52–08:22`

**Visual:**

Show v1 id/status/lines and hypothetical v2 adding currency. Connect an unchanged v1 client to optional, migrate, reject decisions. Caption proposed only.

**Script:**

Adding currency in a future version sounds like a simple map edit, but clients that do not send it need a compatibility policy. Is the field optional, is a default safe, or must the client migrate? Our current endpoint intentionally rejects unexpected fields and does not claim to implement schema version two. The example is here to expose the cost of governing a flexible representation. A separate schema makes those decisions inspectable; it does not remove the need to make them.

**Purpose:**

Show contract governance as a real trade-off rather than an invented API feature.
