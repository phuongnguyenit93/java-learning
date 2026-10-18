---
video:
  url: ""
---

# Program State, Commands, and Execution Order

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

## Program State and Values at a Point in Time

<!-- VIDEO_SECTION -->

### Scene 1 — Program State and Values at a Point in Time

**Time:** 00:00–01:20

**Visual:** Build a table at t0 with balance=100 and transactions=0. Run READ with no stored change, then WITHDRAW 30 to add state at t1.

**Script:** State is the currently stored set of values, not the entire story of every previous operation. A read can produce the output 100 while leaving balance and transaction count untouched. An approved withdrawal can change the balance to 70. A rejected withdrawal may produce an explanation while preserving the old state. Keep output and stored state in separate columns; they are different kinds of observations.

**Purpose:** Teach the state snapshot and distinguish output from a mutation.

## Commands that Act and Expressions that Calculate

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:20–01:33

**Visual:** Freeze the earlier `balance=70` state row as completed history, then open a clearly labelled NEW EXAMPLE card with `balance=100` and `fee=5`; show the scratch expression separately and keep WRITE off.

**Script:** Seeing a value of 70 is ambiguous until we know whether it was computed or stored.

**Purpose:** Connect the current state snapshot to evaluating a value without changing stored state.

### Scene 2 — Commands that Act and Expressions that Calculate

**Time:** 01:33–02:48

**Visual:** Within the NEW EXAMPLE card explicitly reset to `balance=100, fee=5`; show `100−5=95` in scratch without changing stored 100, then animate a separate assignment that finally stores 95.

**Script:** Imagine a fee of five against a balance of 100. Evaluating balance minus fee gives 95, yet the account can still store 100. Only a write, such as assignment, changes that cell. Checking balance greater than amount also produces a Boolean value without deducting anything. An imperative sequence can include all these steps. To debug it, find the actual writes rather than assuming each line mutates something.

**Purpose:** Contrast expression evaluation, condition checks and assignment in one visual.

## Assignment and State Transitions

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:48–03:01

**Visual:** Move the scratch result `95` beside unchanged `balance=100`; progressively highlight READ → CALCULATE → WRITE labels.

**Script:** Let us zoom into assignment and follow the right-hand side before the write.

**Purpose:** Bridge expression evaluation and assignment's observable read–calculate–write sequence.

### Scene 3 — Assignment and State Transitions

**Time:** 03:01–04:16

**Visual:** Animate balance=balance-30: read 100, calculate 70, store 70. Repeat once to obtain 40.

**Script:** Assignment is not a mathematical equation claiming that a number equals itself minus 30. The right side reads the current value and computes the next one; the left side receives it. Starting from 100, two valid withdrawals of 30 give 70 and then 40. Each repetition must use the latest state. If the next request fails validation, the write should never execute.

**Purpose:** Show read–calculate–write in explicit sequence and explain repeated effects.

## Statement Order and Dependencies Between Actions

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:16–04:29

**Visual:** Pause the account assignment at 40; position two policy cards `order discount` and `per-item discount` under unit price 20.

**Script:** Even a correctly written assignment can be placed in the wrong part of a workflow.

**Purpose:** Take the idea of ordered writes into the business meaning of operation order.

### Scene 4 — Statement Order and Dependencies Between Actions

**Time:** 04:29–05:44

**Visual:** Compare total=(20×3)-5=55 against total=(20-5)×3=45, with arrows showing dependencies between operations.

**Script:** Here price is 20, quantity is three and discount is five. Discounting the whole order gives 55; discounting every unit first gives 45. Neither arithmetic expression tells us which business rule was intended. The order of operations becomes part of the meaning. Some independent steps can safely move; others read values written earlier. Mark those dependencies before changing the sequence.

**Purpose:** Demonstrate real semantic consequences of action order using different numeric outcomes.

## Tracing State Before and After an Action

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:44–05:57

**Visual:** Keep results 55 and 45 while fading the arithmetic, and bring in a blank `before | condition | after` trace table.

**Script:** Now we need a trace that records not just values but the branch taken at each request.

**Purpose:** Make the different outcomes motivate evidence about what each step read and changed.

### Scene 5 — Tracing State Before and After an Action

**Time:** 05:57–07:12

**Visual:** Display columns request, condition, before, after and output. Show withdrawal 30 as 100→70, then withdrawal 90 as 70→70 REJECTED.

**Script:** This is the central trace. Beginning with 100, withdrawing 30 succeeds and stores 70. Requesting 90 afterwards must compare against the current 70, not the original 100. The request is rejected, so the subtraction does not execute; the balance remains 70. The program may still print an insufficient-funds message. An output can happen without a state change, which is why the columns must be separate.

**Purpose:** Provide inspectable evidence for both an accepted and a rejected state transition.

## Consequences of Unexpected Mutation

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:12–07:25

**Visual:** Circle withdrawal 90 with `70→70` and its rejection output, then place a `displayBalance()` icon with a WRITE question mark over the same state cell.

**Script:** What happens when a routine claiming only to display state secretly writes to it?

**Purpose:** Lead from a correct trace to the possibility that observing state could secretly modify it.

### Scene 6 — Consequences of Unexpected Mutation

**Time:** 07:25–08:40

**Visual:** Run a normal displayBalance call that prints 70, then an erroneous version that charges 5 on every read and changes the next observation to 65.

**Script:** A function named displayBalance sounds read-only. If it secretly charges five each time, asking to observe the account changes what we are observing. That hidden write makes later test results hard to explain. A reliable trace should mark every permitted write, even one inside a helper. Concurrent writers create additional scheduling problems, but here we are first learning to reason about a single ordered execution.

**Purpose:** Reveal hidden side effects as a source of history-dependent bugs.
