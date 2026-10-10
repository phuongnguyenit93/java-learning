---
video:
  url: ""
---

# Data Queries and Other Declarative Description Styles

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

## Queries as Specifications of Desired Result Rows

<!-- VIDEO_SECTION -->

### Scene 1 — Queries as Specifications of Desired Result Rows

**Time:** `00:00–01:18`

**Visual:**

Display two distinct A:50 transactions, project amount only, and retain both 50 rows with SELECT ALL.

**Script:**

Two different transactions for account A can each have amount fifty. When we select only their amount column, default SQL SELECT ALL preserves both resulting rows, so we see fifty twice. Asking for SELECT DISTINCT instead returns one projected amount. The slide illustrates SQL result semantics and does not pretend that a database command has just been executed. The key lesson is that a query result is not automatically a mathematical set of unique values.

**Purpose:**

Prove duplicates conceptually without a fake engine run.

## Filtering Conditions, Selection, and Relations

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:18–01:30`

**Visual:**

Apply account=A and amount>0 as two independent colored filters to the four-row source; eliminate B:10 and A:-5 for different reasons.

**Script:**

The duplicate amounts survived. Which combined conditions selected those rows?

**Purpose:**

Connect predicates to query membership and access scope.

### Scene 1 — Filtering Conditions, Selection, and Relations

**Time:** `01:30–02:48`

**Visual:**

Apply account=A and amount>0 to four transactions, excluding B:10 and A:-5 for different reasons.

**Script:**

An account-B transaction may be positive but is not owned by A. The negative account-A transaction passes the ownership test but fails the amount test. Both conditions matter. In a real SQL database, NULL does not behave like an ordinary two-valued boolean in every predicate, so the full details require a database lesson. Here we use known integer values to focus on the intent of selection rather than syntax tricks.

**Purpose:**

Clarify conjunction and avoid misrepresenting NULL behavior.

## Result Ordering, Duplicates, and Additional Constraints

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:48–03:00`

**Visual:**

Keep the duplicate test SELECT ALL → [50,50] beside DISTINCT → [50]. Under a clearly separate ORDER test, show distinct amounts [25,10] changing to [10,25] when ORDER BY amount ASC is selected.

**Script:**

Membership is clear, but did we ask for unique values or a stable presentation order?

**Purpose:**

Separate multiplicity and ordering explicitly.

### Scene 1 — Result Ordering, Duplicates, and Additional Constraints

**Time:** `03:00–04:18`

**Visual:**

First compare the two projected 50 rows with the single 50 after DISTINCT. Then switch to a separate two-row example, [25,10] versus [10,25] under ORDER BY amount ASC; label the unsorted view illustrative, not a guaranteed database output.

**Script:**

DISTINCT removes duplicate projected values, taking our two fifties down to one; that alone says nothing about order. In a separate example, twenty-five followed by ten can be displayed as ten then twenty-five when we explicitly request ascending amount. ORDER BY does not remove duplicates, and without it, a convenient sequence seen once is not guaranteed next time. If timestamps tie, add an ID tie-breaker for stable display. Multiplicity and order are two different promises.

**Purpose:**

Demonstrate why sorting and duplicate removal are not synonyms.

## Declarative Policies and Configuration Styles

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:18–04:30`

**Visual:**

Place the policy "only owners can view balances" beside a YAML document listing open, write, close instructions in sequence.

**Script:**

Queries are only one domain. Do policy rules and configuration files automatically count as declarative?

**Purpose:**

Judge description semantics rather than file format.

### Scene 1 — Declarative Policies and Configuration Styles

**Time:** `04:30–05:48`

**Visual:**

Compare an owner-only balance policy with an explicit open→write→close command sequence.

**Script:**

An authorization policy describes an allowed outcome: only the account owner may see the balance. An evaluator can enforce that property in different ways. But a YAML file containing a fixed sequence of open-file, write and close instructions is still describing procedural steps. Using braces, indentation or a domain-specific language does not decide which paradigm is involved. Ask which choices remain with the evaluator and which were dictated by the author.

**Purpose:**

Prevent file-syntax based classification.

## Evaluator Strategies for Executing a Query

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:48–06:00`

**Visual:**

Hold the account-A query above two possible execution plans, table scan and index lookup; keep two qualifying fifty rows unchanged in both outcomes.

**Script:**

The same query may use different plans. What must they preserve and what may vary?

**Purpose:**

Connect semantic contracts to engine strategies.

### Scene 1 — Evaluator Strategies for Executing a Query

**Time:** `06:00–07:18`

**Visual:**

Display hypothetical scan and index plans leading to the same duplicate-preserving query result.

**Script:**

A real database optimizer can choose among possible access paths based on indexes, statistics and distribution. Any valid plan must still satisfy the required filtering, duplicates and ordering if specified. The optimizer is not promised to find the fastest plan for every workload. These are plausible diagrams, not measured EXPLAIN output; the detailed plan inspection belongs to a concrete SQL engine module.

**Purpose:**

Avoid inventing performance evidence while explaining execution strategy.

## Limits of Treating Every DSL or Conditional as Declarative

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:18–07:30`

**Visual:**

Arrange a SQL query, ownership policy and ordered-command configuration in three columns; highlight the question "who chooses the execution steps?".

**Script:**

They look different, yet some are declarative and one is procedural. What is the deciding criterion?

**Purpose:**

Finish the DSL comparison with a usable test.

### Scene 1 — Limits of Treating Every DSL or Conditional as Declarative

**Time:** `07:30–08:48`

**Visual:**

Sort query, policy and ordered YAML instructions into WHAT versus HOW responsibility columns.

**Script:**

A domain-specific language may express properties of a valid result, or it may issue an exact sequence of commands. An if statement within imperative code also does not turn the entire program into declarative programming. Look at what the description means and how much strategy its evaluator is free to choose. When the author still names every operation and its order, the expression remains procedural no matter the file extension.

**Purpose:**

Classify by responsibility rather than surface appearance.
