---
video:
  url: ""
---

# Imperative Programming: Expressing Computation as Actions

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

## Imperative Programming: Meaning and Scope

<!-- VIDEO_SECTION -->

### Scene 1 — Imperative Programming: Meaning and Scope

**Time:** 00:00–01:20

**Visual:** Draw a COMMAND box beside balance=100. Run a read-only command, then a withdrawal of 30; highlight the stored balance only on the write.

**Script:** Start with a tiny question: what did the program actually do? Reading a balance of 100 does not change it. Assigning balance minus 30 produces a new stored balance of 70. Imperative programming describes a computation as actions in an explicit order. Those actions may calculate, check, print or update state. We must not mistake every executed step for a mutation.

**Purpose:** Introduce imperative execution while separating actions from stored-state changes.

## Purpose and Role of Explicit Execution Control

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:20–01:33

**Visual:** Keep `balance=100` fixed after READ, and drag a CHECK tile in front of DEBIT with a question mark over the ordering arrow; do not yet replay the subtraction.

**Script:** If some commands are read-only, why does controlling their order matter so much?

**Purpose:** Bridge read-only versus writing commands to the need to check before a state-changing debit.

### Scene 2 — Purpose and Role of Explicit Execution Control

**Time:** 01:33–02:48

**Visual:** Put CHECK and DEBIT cards on screen. With balance=20 and withdrawal=30, run DEBIT first to reveal -10, then correct the order and show rejection.

**Script:** Try subtracting before checking. An account holding 20 becomes negative 10 when asked for 30. Checking first lets us refuse the operation without changing the balance. The arithmetic did not suddenly become wrong; the execution order was wrong for the business rule. That is why writing the steps explicitly can be useful when one action depends on the outcome of another.

**Purpose:** Make sequencing matter through an observable invalid state rather than an abstract claim.

## Problems Requiring Ordered Actions

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:48–03:01

**Visual:** Freeze the red `-10` result from the incorrect debit-first order, then replace its path with a validation gate before the next request against balance 100.

**Script:** Let us keep one small account example so every step can be inspected, not just its final answer.

**Purpose:** Use the failed withdrawal to motivate an explicit sequence of validations for a new request.

### Scene 3 — Problems Requiring Ordered Actions

**Time:** 03:01–04:16

**Visual:** Write balance=100 and request=30 on a board. Reveal four cards in order: VALID AMOUNT, FUNDS, DEBIT, REPORT.

**Script:** A result of 70 alone does not establish that the program behaved correctly. Was the amount positive? Did we check sufficient funds before subtracting? Did the reporting step occur afterwards? We will follow an account starting at 100, first withdrawing 30 and then requesting 90. That gives us a continuous example for evaluating a command, its condition and the state before and after it.

**Purpose:** Set up a learner-sized problem that motivates an execution trace.

## Commands, State, and Result: A First Model

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:16–04:29

**Visual:** Collapse the four command tiles into one flow, then pull `100−30=70` into a scratch-value area while the stored account cell still shows 100.

**Script:** These actions produce both intermediate values and stored state; the two must stay separate.

**Purpose:** Move from the sequence of actions to the distinction between a calculated result and committed state.

### Scene 4 — Commands, State, and Result: A First Model

**Time:** 04:29–05:44

**Visual:** Split the display: calculate 100-30 into a scratch value 70, then show balance=balance-30 change the stored cell from 100 to 70.

**Script:** Watch the difference between calculating and assigning. The expression gives 70 without saying where to store it. The assignment writes 70 to balance. To reason through the program, record the current command, state before the command and state after it. For a read or a print step, the last two entries may match. This is a more reliable technique than assuming the name of a command tells us what it changes.

**Purpose:** Provide a concrete command–value–state tracing method.

## Connections to Declarative, Object-Oriented, and Functional Styles

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:44–05:57

**Visual:** Keep `expression result=70` and `stored balance=70` visible, then branch a small arrow to RULE, COMMAND and ACCOUNT labels without replaying the withdrawal.

**Script:** Does describing actions this way prevent us from also using objects or declarative rules?

**Purpose:** Connect the stored-state model to complementary ways of describing a single domain problem.

### Scene 5 — Connections to Declarative, Object-Oriented, and Functional Styles

**Time:** 05:57–07:12

**Visual:** Arrange three views of the withdrawal: ordered CHECK/DEBIT actions, the sufficient-funds rule and an ACCOUNT object.

**Script:** The same system supports several ways of thinking. Ordered checking and updating are imperative actions. The rule that a withdrawal requires enough funds describes what must be true. An account object may be responsible for preserving that rule. We do not need to label an entire application as only one paradigm. This module owns the action-and-state perspective; Java syntax and deeper object design have their own modules.

**Purpose:** Show that different paradigms can describe different aspects of the same problem.

## Starting Point and Learning Path: From Actions to Procedures

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:12–07:25

**Visual:** Fold the RULE/COMMAND/ACCOUNT views into one example card; trace STATE → BRANCH → PROCEDURE → CORRECTNESS below it.

**Script:** With that overview, what learning order gets a beginner from actions to correctness?

**Purpose:** Turn the overview into an ordered learning path without resetting the running example.

### Scene 6 — Starting Point and Learning Path: From Actions to Procedures

**Time:** 07:25–08:40

**Visual:** Reveal a five-card route: MODEL, STATE, BRANCH/LOOP, PROCEDURE, CORRECTNESS, each using the same account icon.

**Script:** We have the starting mental model. Next we will inspect stored values and assignment, then decide which path executes and how loops process more than one request. We will group related actions into procedures and finally examine preconditions, outcomes and failure cases. Basic variables and expressions are enough to begin. You do not need advanced Java memory rules or multithreaded scheduling to follow these examples.

**Purpose:** Give a prerequisite-safe handoff across the five knowledge chapters.
