---
video:
  url: ""
---

# Branching and Repetition in Control Flow

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

## The Role of Control Flow in Imperative Programs

<!-- VIDEO_SECTION -->

### Scene 1 — The Role of Control Flow in Imperative Programs

**Time:** 00:00–01:20

**Visual:** Change a straight command chain into a diamond labelled CHECK with ACCEPT and REJECT edges. Add a loop around three transaction cards.

**Script:** Control flow decides what executes next. A rejected request should skip the debit, and a list of requests should cause the same processing step to be repeated. Not every line runs once on every path. Branches and loops extend the state-and-sequencing model we already have. The important new question is not just what a command computes, but whether this particular execution ever reaches that command.

**Purpose:** Connect familiar state snapshots to conditional and repeated execution paths.

## Condition-Based Branch Selection

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:20–01:33

**Visual:** Preserve the CHECK→ACCEPT/REJECT tree, insert `amount>0` as an earlier gate, and highlight an unvalidated negative request.

**Script:** A branch reads a condition; which balance should it read, and which inputs are invalid?

**Purpose:** Turn the general idea of selecting a path into a concrete input precondition.

### Scene 2 — Condition-Based Branch Selection

**Time:** 01:33–02:48

**Visual:** Show two gates amount>0 and balance>=amount. Route withdrawal -10 and then withdrawal 90 with balance 70 to rejection paths.

**Script:** Checking only available funds misses an important case: a negative withdrawal could pass that test and subtracting a negative amount would add money. Our policy needs a positive amount and sufficient current balance. A request for minus 10 fails the first gate. A request for 90 against 70 fails the second. Neither is allowed to reach the write. This is how a condition protects stored-state invariants.

**Purpose:** Show two distinct validation failures and why they must not update state.

## Repeating Actions by Condition or Collection

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:48–03:01

**Visual:** Hold the two validation gates in place, line up three receipt cards, and brighten the return arrow for processing the next item.

**Script:** One request is easy to branch on; what changes when there is a whole list to process?

**Purpose:** Extend one branch decision into repeated handling of several inputs.

### Scene 3 — Repeating Actions by Condition or Collection

**Time:** 03:01–04:16

**Visual:** Move a pointer over receipts [10,20,5] while the accumulator grows from 0 to 10, then 30, then 35.

**Script:** A loop repeats a meaningful action. For these receipts, we read 10 and update the sum to 10; then read 20 and obtain 30; finally read five and finish with 35. The intermediate totals tell us how we reached the final number. Some loops have a known iteration count, while others continue until a condition changes. In either case, identify what progresses between iterations.

**Purpose:** Make accumulation and progression visible instead of simply displaying a final sum.

## Tracing State and Iteration Counts

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:16–04:29

**Visual:** Shrink receipts `[10,20,5]` to three rows, leave sum 35 at the bottom, and reveal an empty index column beside the values.

**Script:** A correct-looking final sum does not prove we visited every element exactly once.

**Purpose:** Shift attention from aggregation to the index and iteration history that produced it.

### Scene 4 — Tracing State and Iteration Counts

**Time:** 04:29–05:44

**Visual:** Create a four-column table of iteration, index, item and sum. Highlight index 0,1,2 and deliberately increment too early to skip the first receipt.

**Script:** Pause after the second iteration. We have processed 10 and 20, so the sum must be 30 and receipt five remains. If the index advances before we read the current item, the first receipt may be skipped. Advancing it too late may process an item twice. A row-by-row trace catches this off-by-one mistake much more reliably than staring at the final sum.

**Purpose:** Turn loop progress into a practical method for finding off-by-one errors.

## Termination Conditions and Nonterminating Loops

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:44–05:57

**Visual:** Freeze pointer index 2; exchange the total box for `remaining=3` and a question mark on the `>0` continuation condition.

**Script:** Tracking the index helps, but what guarantees a repeating condition ever becomes false?

**Purpose:** Link progress through a collection to the condition governing loop termination.

### Scene 5 — Termination Conditions and Nonterminating Loops

**Time:** 05:57–07:12

**Visual:** Animate remaining=3 under while remaining>0, counting 3→2→1→0. Beside it, freeze an incorrect program at remaining=3 forever.

**Script:** To explain why a loop ends, name its continuation condition and the step that changes that condition. Decreasing remaining from three to zero gives an understandable termination argument. If we forget the decrement, the loop may never end. If we decrement too far, we could still break a separate condition that remaining must not become negative. Termination and state correctness are related checks, not the same promise.

**Purpose:** Contrast finite progress with a stuck loop and an invalid overshoot.

## Tracing an Execution with Branches and Loops

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:12–07:25

**Visual:** Leave `remaining=0` beside a STOP indicator, then queue `[+20,-30,-200]` beside a fixed balance of 100.

**Script:** We can now combine branching, repeated requests and an account state in one execution.

**Purpose:** Bridge a terminating loop to combining accepted and rejected transaction branches.

### Scene 6 — Tracing an Execution with Branches and Loops

**Time:** 07:25–08:40

**Visual:** Feed [+20,-30,-200] through the balance checker. Update the visible state 100→120, 120→90, 90→90 and mark the last operation rejected.

**Script:** Let us run all three transactions from 100. The deposit of 20 creates 120. The withdrawal of 30 leaves 90. The attempted withdrawal of 200 fails and keeps the balance at 90. We examined all three items, yet only two caused successful writes. That difference between iterations and mutations is easy to miss in a final answer. Change their order, and some permitted branches may change as well.

**Purpose:** Synthesize branching, looping and skipped writes with one numerical trace.
