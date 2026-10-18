---
video:
  url: ""
---

# Organizing an Imperative Program with Procedures

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

## Procedures as Groups of Related Actions

<!-- VIDEO_SECTION -->

### Scene 1 — Procedures as Groups of Related Actions

**Time:** 00:00–01:20

**Visual:** Collapse CHECK, DEBIT and REPORT into a labelled withdraw(amount) frame, then show two callers sharing the same procedure.

**Script:** Rather than copying the withdrawal workflow into several places, we can name the sequence withdraw. Calling the procedure still executes imperative statements in order. The improvement is an organizational boundary around one recognizable task, so callers and tests know where to focus. A procedure is not better merely because it hides many lines; its name and responsibility must explain a coherent operation.

**Purpose:** Connect control flow to the purpose of organizing reusable procedures.

## Procedure Inputs, Outputs, and Contracts

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:20–01:33

**Visual:** Collapse CHECK/DEBIT/REPORT into `withdraw(amount)` and reveal `positive`, `enough funds`, and `unchanged on failure` along the procedure border.

**Script:** A useful procedure must also tell the caller which inputs and results it guarantees.

**Purpose:** Move from a named procedure to the behavior contract its caller can depend on.

### Scene 2 — Procedure Inputs, Outputs, and Contracts

**Time:** 01:33–02:48

**Visual:** Show a contract card: amount positive; enough funds; on success subtract amount; on failure preserve balance and return a reason.

**Script:** Imagine calling withdraw without looking inside it. You should still know the amount must be positive, sufficient funds are needed, and success deducts exactly that amount. If a request for 90 arrives while only 70 remains, the expected outcome is a refusal without any write. Logging or external updates should also be declared as possible effects. This contract allows us to test behavior rather than trust a method name.

**Purpose:** Teach input, output, failure and permitted side effects as a procedure contract.

## Local Data and State Boundaries

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:48–03:01

**Visual:** Keep the completed 100→70 and rejected 70→70 traces in a dimmed HISTORY strip; start a separately labelled NEW INVOCATION reset to `account.balance=100`, with candidate `newBalance=70` inside LOCAL and no WRITE yet.

**Script:** Before committing an update, the procedure may calculate a temporary value locally.

**Purpose:** Link postconditions to the separation of temporary values and shared account state.

### Scene 3 — Local Data and State Boundaries

**Time:** 03:01–04:16

**Visual:** Continue the NEW INVOCATION from fresh `account.balance=100`: calculate `newBalance=70` inside the procedure while shared state stays 100; only animate WRITE at the final permitted update.

**Script:** A temporary newBalance of 70 is not necessarily the account balance. Until the procedure performs an actual account write, the shared value could still be 100. Local variables help keep intermediate calculations inside one invocation. Two invocations using the same local variable name are not automatically sharing storage. The vital distinction is between computing a candidate value and committing the intended state change.

**Purpose:** Visualize local scratch data separately from a shared account update.

## Side Effects of Procedure Calls

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:16–04:29

**Visual:** Circle the WRITE arrow crossing the procedure border, and place inactive RETURN/PRINT/WRITE indicators next to the frame.

**Script:** Returning a result is only one part of what a procedure can make observable.

**Purpose:** Ask which observable effects may exist in addition to the returned value.

### Scene 4 — Side Effects of Procedure Calls

**Time:** 04:29–05:44

**Visual:** Use three output panels labelled RETURN, PRINT and WRITE. Run a read-only displayBalance and a buggy version charging five on each display.

**Script:** A procedure may return a result and still print a message, save data or modify shared account state. Those are observable side effects. Consider a displayBalance routine that secretly deducts five every time it is called; the name hides a costly behavior. The answer is not to ban all effects, since saving data is often essential. We must identify who owns them and make them explicit in the contract and trace.

**Purpose:** Separate output values from side effects and expose a deceptive read routine.

## Decomposing Work into Collaborating Procedures

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:44–05:57

**Visual:** Turn the RETURN/PRINT/WRITE indicators into validate/check/debit/credit/record stages, leaving a visible question mark between debit and credit.

**Script:** Several named procedures can collaborate, but does splitting an operation guarantee the whole workflow is safe?

**Purpose:** Move from one procedure's effects to partial failure between cooperating procedures.

### Scene 5 — Decomposing Work into Collaborating Procedures

**Time:** 05:57–07:12

**Visual:** Reveal VALIDATE→CHECK→DEBIT A→CREDIT B→RECORD. Turn CREDIT B red after the debit and freeze the state between the two accounts.

**Script:** A transfer can be decomposed into validation, a balance check, debiting the source, crediting the destination and recording the outcome. Named steps make responsibilities and failures easier to locate. But after debit succeeds, credit can still fail. Merely calling several procedures does not give them atomic transaction behavior; the system needs an explicit failure or recovery policy. Transaction implementations belong to another module, while we focus on the ordered workflow.

**Purpose:** Show the organizational benefit and the failure boundary of decomposed operations.

## Limitations of Procedures with Hidden External State

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:12–07:25

**Visual:** Freeze the workflow after debit A but before credit B; add a `global balance` cell with an invisible-read arrow into the next call.

**Script:** Even neatly named procedures may depend on hidden shared data that callers cannot see.

**Purpose:** Connect a collaboration failure boundary with dependence on hidden state outside the procedure signature.

### Scene 6 — Limitations of Procedures with Hidden External State

**Time:** 07:25–08:40

**Visual:** Display two calls withdraw(30) while global balance changes from 100 to 70; trace each call reading the changed global value.

**Script:** The same argument does not always produce the same outcome if a procedure silently reads the global account balance. The first withdrawal sees 100; the second sees 70 because state changed between calls. That may be the intended behavior, but hiding the dependency makes tests and contracts harder to understand. Prefer explicit necessary inputs when possible, and document any shared state a procedure can read or write.

**Purpose:** Make hidden state coupling visible through repeat calls with different history.
