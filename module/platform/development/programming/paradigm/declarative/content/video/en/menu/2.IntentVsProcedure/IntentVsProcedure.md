---
video:
  url: ""
---

# Specifying Intent and Understanding Execution Strategy

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

## Specifying Desired Results and Required Properties

<!-- VIDEO_SECTION -->

### Scene 1 — Specifying Desired Results and Required Properties

**Time:** `00:00–01:18`

**Visual:**

Remove minus two at the filter gate, keep ten and twenty-five, then reveal an underspecified top-three card.

**Script:**

The condition amount greater than zero specifies which elements can be included: ten and twenty-five qualify, while minus two does not. There is no need to name an accumulator in this description. But if the request changes to the three largest receipts, we must specify a ranking rule, limit and perhaps how ties are handled. A short sentence is not a substitute for a complete acceptance contract.

**Purpose:**

Distinguish result properties from missing constraints.

## Conditions, Predicates, and Constraints

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:18–01:30`

**Visual:**

Layer account=A over amount>0 and show positive account-B rows excluded by the second filter.

**Script:**

One predicate works for amounts; what changes when the data belongs to several accounts?

**Purpose:**

Explain conjunction and scope restrictions.

### Scene 1 — Conditions, Predicates, and Constraints

**Time:** `01:30–02:48`

**Visual:**

Highlight amount and account in four rows; show how a missing ownership filter changes what is exposed.

**Script:**

A predicate holds or fails for a candidate record. The desired rows can be required to have both positive amounts and the account identifier A. Leaving out the account restriction may yield mathematically correct positive numbers but disclose another user's records. That is a specification mistake, not simply a poor scan algorithm. Visible constraints make it easier to ask whether every business requirement has been included.

**Purpose:**

Turn access control into a concrete declarative constraint.

## Completeness and Ambiguity in a Specification

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:48–03:00`

**Visual:**

Blur "recent transactions"; reveal createdAt, postedAt and updatedAt clocks, plus two tied timestamps and a tie-breaker placeholder.

**Script:**

What does the phrase recent transactions actually guarantee?

**Purpose:**

Expose ambiguity before choosing an execution strategy.

### Scene 1 — Completeness and Ambiguity in a Specification

**Time:** `03:00–04:18`

**Visual:**

Show created, posted and modified dates for two records, then a tie-breaker ID for matching timestamps.

**Script:**

One person may mean the newest created records and another the latest posted records. Without an explicit time field, limit and ranking policy, both might think they followed the request. If the screen needs a single stable top-three selection, tied timestamps need another criterion. Some problems legitimately permit several satisfying outcomes, but when an application needs one, that policy belongs in the specification.

**Purpose:**

Show incompleteness and intentional nondeterminism distinctly.

## Evaluators: Turning Descriptions into Computation

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:18–04:30`

**Visual:**

Hold amount>0 above two candidate evaluator diagrams, one table scan and one index lookup; both lead to the same qualifying rows.

**Script:**

Now the request is precise. How does a machine satisfy it?

**Purpose:**

Separate evaluator mechanics from written intent.

### Scene 1 — Evaluators: Turning Descriptions into Computation

**Time:** `04:30–05:48`

**Visual:**

Place ten thousand illustrative rows between scan/index routes and the same valid results, with no fabricated timing.

**Script:**

An evaluator obtains input data, checks the predicates and constructs the answer. A database might inspect all rows or use an index when one is available and suitable. We are describing two possible implementations, not running EXPLAIN or claiming particular timings. Both must respect the account and amount conditions. An engine cannot repair a query that simply failed to include a required restriction.

**Purpose:**

Show execution flexibility and its limits.

## Semantic Results versus Concrete Execution Plans

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:48–06:00`

**Visual:**

Show two result lists containing the same qualifying rows in different orders; then attach an explicit ORDER BY label.

**Script:**

Two valid plans can walk the data differently. When can their results still be considered equivalent?

**Purpose:**

Separate semantic result from incidental ordering.

### Scene 1 — Semantic Results versus Concrete Execution Plans

**Time:** `06:00–07:18`

**Visual:**

Show the same rows in two different orders, then apply ORDER BY plus an ID tie-breaker for stable display.

**Script:**

Query semantics define which rows qualify, including any duplicate multiplicity required by the language. An execution plan describes the steps used to find them. Without an ordering requirement, one observed row order is not a promise for another run. When a business UI depends on order, state the ranking explicitly and add a tie-breaker if values can match. A query's correctness cannot be inferred from a convenient accidental display.

**Purpose:**

Teach semantic equivalence without invented order guarantees.

## Strategy, Observable Order, and Performance Trade-offs

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:18–07:30`

**Visual:**

Compare diagrams of 100 versus one million rows, with no fake runtime figures; place a warning next to reordered log/write operations.

**Script:**

We removed traversal details from the specification. What cost became less visible?

**Purpose:**

Close with performance and observable effects.

### Scene 1 — Strategy, Observable Order, and Performance Trade-offs

**Time:** `07:30–08:48`

**Visual:**

Compare small/large data volumes, then swap write and notify operations to reveal an observable effect warning.

**Script:**

A declarative query does not make CPU time or memory free. Different volumes, indexes and statistics can lead to different execution costs. Measure plans with real tools when performance matters. Also distinguish reordering independent pure calculations from reordering writes or notifications; the latter can change what users observe. A declarative description is not a license for an evaluator to ignore its effects or its documented execution contract.

**Purpose:**

Avoid unconditional optimization and purity claims.
