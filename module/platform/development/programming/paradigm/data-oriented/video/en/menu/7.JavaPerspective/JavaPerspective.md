---
video:
  url: ""
---

# Java and Project Amber Data-Oriented Perspective

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

## Java DOP as a Related but Distinct Model

<!-- VIDEO_SECTION -->
### Scene 1 — Java DOP asks a related but different question

**Time:** `00:00–01:29`

**Visual:**

Place Sharvit generic structures beside Java's typed data family around order A.

**Script:**

Java's Project Amber discussions also use the name Data-Oriented Programming, but they do not simply translate Sharvit's four principles into Java syntax. Sharvit emphasizes generic inspectable maps and lists, independent schemas and non-mutating transformations. The Java perspective emphasizes transparent typed models, careful valid variants and operations outside the data. Both put data at the center but establish guarantees in different ways. We will compare the conceptual models here without turning this lesson into a Java records, sealed classes or pattern matching syntax tutorial.

**Purpose:**

Distinguish Sharvit's generic-schema model from the separate Java Amber typed-data perspective.

## Transparent and Immutable Data Models

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:29–01:42`

**Visual:**

Move id/status/lines from a map card to a typed record-shaped diagram.

**Script:**

Maps make fields visible at runtime; a Java type can declare the same fields more precisely.

**Purpose:**

Explain visibility and immutability without overclaiming Java record behavior.

### Scene 2 — A typed view of order data

**Time:** `01:42–03:11`

**Visual:**

Compare a map with `OrderData(id,status,lines)` typed fields; highlight visible components, and place a warning next to a mutable nested list.

**Script:**

A Java record-like value can make its components explicit in the type model, so callers and tools know which values a complete order must contain. But a record does not automatically turn its nested collection into deeply immutable data. If a list can change, a supposedly stable outer value may still be observably different later. Good data modeling must address both the visible shape and the mutability of components. The benefit is stronger static information than an unconstrained generic map supplies.

**Purpose:**

Teach transparency and realistic immutability limits of typed models.

## Complete Data Models That Make Illegal States Unrepresentable

<!-- VIDEO_SECTION -->
### Transition

**Time:** `03:11–03:24`

**Visual:**

Split the state of payment into separate valid alternatives.

**Script:**

A record explains fields; now define which combinations of fields are legal.

**Purpose:**

Introduce precise complete variants and invalid-state exclusion.

### Scene 3 — Make invalid combinations impossible to construct

**Time:** `03:24–04:53`

**Visual:**

Display conceptual `PaymentState = Pending | Paid(receipt)`; strike through a generic `{status:paid,receipt:null}` shape. No misleading executable Java snippet.

**Script:**

A useful typed model can represent different legitimate cases as different variants. For example, pending payment needs no receipt, while a paid case includes a receipt. If the type and constructors enforce that distinction, a paid state without its necessary evidence cannot be represented through the model's normal operations. This is the idea behind making illegal states unrepresentable. A sealed hierarchy alone does not enforce every business invariant or make an actual gateway reliable; the model must still reflect the domain correctly.

**Purpose:**

Show the semantic reason for valid variants, not a keyword demonstration.

## Separating Operations from Data and Handling Variants with Patterns

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:53–05:06`

**Visual:**

Fan out one typed state value to two case-handling paths.

**Script:**

Once variants exist, an operation can handle each without embedding all behavior inside them.

**Purpose:**

Connect external operations and pattern-based case handling.

### Scene 4 — Operations can match data variants

**Time:** `05:06–06:35`

**Visual:**

Draw `describe(payment)` with Pending → waiting, Paid(receipt) → confirmed. Add a missing-case marker if one branch is absent.

**Script:**

An operation outside the data model can inspect which legitimate variant it received and do the corresponding work. That is the conceptual role of pattern matching. With a closed, carefully defined set of variants, tools can help expose unhandled cases. Yet putting every operation into one enormous branch expression would not improve design. Choose cohesive operations and keep their contracts visible. The earlier HTTP experiment used a generic map and schema rather than an invented Java sealed implementation.

**Purpose:**

Explain typed case handling without falsely implying the DOP API uses it.

## Comparing Java's Typed Models with Sharvit's Generic Data

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:35–06:48`

**Visual:**

Compare external JSON runtime validation with a typed Java model's static checks.

**Script:**

We have two valid modeling techniques. What evidence does each actually guarantee?

**Purpose:**

Conclude by distinguishing transport trust from compile-time type safety.

### Scene 5 — Runtime schema and static types solve different problems

**Time:** `06:48–08:17`

**Visual:**

Show HTTP JSON → schema → 200/422 beside a typed Java model checked during compilation. Connect them through a deserialization boundary.

**Script:**

Even a perfectly typed Java service receives untrusted HTTP data from callers that did not compile against its implementation. Runtime validation still matters there. Typed modeling can rule out many invalid combinations within the program, while generic maps and independent schemas can be flexible at boundaries and produce detailed field-path diagnostics. Both have costs and both can coexist. We should not treat one as a syntax translation of the other or claim that map-based DOP is universally better.

**Purpose:**

Give a balanced, curriculum-aligned comparison of the two DOP models.
