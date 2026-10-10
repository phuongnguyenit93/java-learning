---
video:
  url: ""
---

# Correctness, Trade-offs, and Paradigm Connections

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

## Preconditions, Transitions, and Expected Outcomes

<!-- VIDEO_SECTION -->

### Scene 1 — Preconditions, Transitions, and Expected Outcomes

**Time:** 00:00–01:20

**Visual:** Build a PRE–ACTION–POST table. Show a valid withdrawal 30 on 100 ending at 70 and a rejected 90 on 70 leaving 70.

**Script:** Correctness needs more than one happy path. A withdrawal of 30 from 100 satisfies the amount and funds preconditions and should end at 70, never below zero. A request for 90 with only 70 should be rejected, leaving the state unchanged. Preconditions tell us when a transition may occur; postconditions tell us what the outcome must guarantee. We can inspect those expectations without committing to a particular testing framework.

**Purpose:** Convert the account trace into explicit acceptance and rejection test evidence.

## Risks of Mutable State and Action Ordering

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:20–01:33

**Visual:** Keep the rejected withdrawal's PRE/POST card with the unchanged balance 70 circled, then put READ and WRITE on a shared timeline.

**Script:** A clear contract still leaves room for mistakes when operations share data or run in the wrong order.

**Purpose:** Move from checking an expected postcondition to the risks of reading or writing at the wrong point.

### Scene 2 — Risks of Mutable State and Action Ordering

**Time:** 01:33–02:48

**Visual:** Display READ, DISCOUNT and WRITE cards. Run DISCOUNT twice and flag a fee calculated using an outdated balance snapshot.

**Script:** Many bugs are caused not by bad arithmetic but by using the right formula at the wrong time. Applying a discount twice, reading a stale balance or writing in a rejected branch can all create incorrect state. When several actions share data, ask which step last changed it and which version each calculation reads. Thread locks and memory scheduling are separate topics; here we first inspect the history of one execution.

**Purpose:** Expose order dependence, duplicate updates and stale reads through concrete examples.

## Debugging Through State-Change Histories

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:48–03:01

**Visual:** Place the faulty negative-20 balance in a blank `before | branch | after` row and mark the missing last-WRITE attribution.

**Script:** Once an outcome is wrong, how can a trace lead us back to the offending command?

**Purpose:** Make a wrong outcome create a concrete debugging question about the most recent state write.

### Scene 3 — Debugging Through State-Change Histories

**Time:** 03:01–04:16

**Visual:** Alter the trace so withdrawing 90 after 30 changes 70 to -20. Follow a red arrow back from the violated nonnegative invariant to an unguarded subtraction.

**Script:** This run is intentionally broken. Starting at 100, withdrawing 30 gives 70. If the next request for 90 produces negative 20, the subtraction ran when it should have been skipped. Rather than changing the formula at random, work backward from the failed postcondition. Which step last wrote the balance? Which branch should have guarded it? A trace with before, condition, branch and after entries makes the error reproducible.

**Purpose:** Teach backward debugging from a violated invariant to a misplaced write.

## Benefits and Costs of Explicit Execution Control

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:16–04:29

**Visual:** Condense a long state trace into two neutral cards, `explicit steps` and `desired result`, without selecting a winner.

**Script:** State tracing is powerful, but should every problem expose each implementation step this explicitly?

**Purpose:** Question whether the low-level detail useful for debugging is needed for every expression of a problem.

### Scene 4 — Benefits and Costs of Explicit Execution Control

**Time:** 04:29–05:44

**Visual:** Place a complex branch-and-loop flow next to one card reading sum all positive amounts. Zoom into the index and mutable accumulator on the left.

**Script:** Explicit steps are valuable when order and side effects matter, such as transferring money or controlling equipment. However, when the goal is simply to select a subset of values, managing indexes and temporary totals may obscure the intent. The best style is the one that makes the relevant correctness questions easiest to answer. We should compare complete clarity and maintenance cost rather than treat one paradigm as universally superior.

**Purpose:** Balance the benefits of visible control against the complexity of verbose stateful code.

## Imperative Steps versus Declarative Descriptions

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:44–05:57

**Visual:** Keep an indexed loop at left and `sum positive amounts` at right, with the same input `[10,-2,25]` pinned in the middle.

**Script:** The same input list lets us compare an action-by-action description with a statement of the goal.

**Purpose:** Prepare a fair procedural-versus-declarative comparison using identical evidence.

### Scene 5 — Imperative Steps versus Declarative Descriptions

**Time:** 05:57–07:12

**Visual:** Trace [10,-2,25] with an imperative accumulator on the left and the phrase sum positive amounts on the right; both display 35.

**Script:** An imperative solution visits 10, negative two and 25, checks which amounts are positive and updates a running sum to 35. A declarative description asks for the sum of positive amounts without prescribing each accumulator update. Both can yield 35, but they differ in how much execution strategy the author describes. A declarative evaluator still performs actual work and may have relevant ordering or effects; it is not magic.

**Purpose:** Compare identical numerical results while highlighting the different level of procedural detail.

## Procedures, Other Paradigms, and the Java Boundary

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:12–07:25

**Visual:** Fix result 35 between the two descriptions; link PROCEDURE to OBJECT and FUNCTION cards while retaining the question `which step runs?`.

**Script:** These styles can coexist, so where does procedural organization fit in Java and object-oriented code?

**Purpose:** Carry the imperative/declarative contrast into how procedures coexist with other programming paradigms.

### Scene 6 — Procedures, Other Paradigms, and the Java Boundary

**Time:** 07:25–08:40

**Visual:** Join a PROCEDURE card to OBJECT METHOD and PURE TRANSFORMATION cards, then return to the before/after state trace from the first video.

**Script:** Procedural programming organizes imperative actions into callable units. A method on a Java object can use ordered statements and loops, and may invoke a value transformation before updating shared state. These approaches can coexist in one application. Keep three questions when reading code: which action runs, what was the state before and after, and which condition allowed the write? Java syntax, object responsibility and functional purity have separate learning modules when you want to go deeper.

**Purpose:** Close with reusable diagnostic questions and a clear handoff to neighboring paradigms.
