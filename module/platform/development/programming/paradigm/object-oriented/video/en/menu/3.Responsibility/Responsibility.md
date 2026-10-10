---
video:
  url: ""
---

# Object Responsibilities, Contracts, and Collaboration

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


## The Purpose of Object Responsibilities

<!-- VIDEO_SECTION -->

### Scene 1 — The Purpose of Object Responsibilities

**Time:** 00:00–01:14

**Visual:** Arrange ACCOUNT, CHECKOUT and NOTIFIER boxes; connect Account to balance and permitted withdrawal, Notifier to message delivery, and Checkout to coordinating arrows.

**Script:** Who has to keep this system correct? Account is responsible for deciding whether a withdrawal is valid. Notifier is responsible for delivering the result to the user. Checkout coordinates the request. These responsibilities should be named before drawing a long list of classes: a change to notification wording should not force us to revisit the nonnegative balance rule.

**Purpose:** Explain responsibility through change boundaries, not one-method-per-class dogma.



## Choosing the Owner of a Behavior or Decision

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:14–01:27

**Visual:** Zoom into the Checkout→Account call and highlight an unauthorized balance check living in the caller before moving it.

**Script:** We have named responsibilities; where should the actual decision that protects each rule live?

**Purpose:** Make ownership testable through who makes a sufficient-funds decision.

### Scene 2 — Choosing the Owner of a Behavior or Decision

**Time:** 01:27–02:41

**Visual:** Put the sufficient-funds IF statement mistakenly inside Checkout; move the decision next to Account and leave Checkout with request/result arrows. Keep cross-account transfer coordination separate.

**Script:** Suppose Checkout inspects the account balance and decides whether withdrawal is allowed. Now every other caller must know that rule too. The account has the data and authority to decide it, so move the decision there. But do not force the account to own transfer coordination between two accounts: that is a wider responsibility. We choose owners according to knowledge and authority.

**Purpose:** Distinguish owner of account invariant from owner of cross-object coordination.



## Cohesion among Related Responsibilities

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:41–02:54

**Visual:** After relocating the withdrawal decision, group the account operations by why they change; watch a promotional email slide out.

**Script:** Correct location of one rule is not enough if we bundle unrelated responsibilities around it.

**Purpose:** Connect ownership to cohesion and change reasons.

### Scene 3 — Cohesion among Related Responsibilities

**Time:** 02:54–04:08

**Visual:** Color deposit, withdraw and availableBalance green inside Account, then drag sendPromotionalEmail outside into Notifier. Show two independent reasons for change.

**Script:** An account may have several operations, but they should support one coherent purpose. Depositing, withdrawing and reporting available balance belong together. Sending promotional email does not need to change whenever balance rules do. When an EverythingManager handles both, unrelated requests create unrelated edits in one place. Cohesion is about the reasons behavior belongs together, not the count of methods.

**Purpose:** Visualize cohesive responsibility and why a catch-all object changes for unrelated reasons.



## Behavioral Contracts between Collaborators

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:08–04:21

**Visual:** Set the Account box beside the Notifier box, then place a contract card on the message arrow between caller and account.

**Script:** We have an owner and a coherent role. What exactly may another object assume when calling it?

**Purpose:** Prepare a falsifiable communication promise beyond interface syntax.

### Scene 4 — Behavioral Contracts between Collaborators

**Time:** 04:21–05:35

**Visual:** Put a contract card beside withdraw(amount): amount>0; if funds enough, exactly one deduction; otherwise failure with reason and unchanged balance. Replay balance70/request90.

**Script:** A collaboration needs more than a method name. The caller supplies a positive amount; the account promises success with the appropriate deduction or a clear failure that leaves state unchanged. At balance seventy, withdraw ninety must not produce a success-looking result after a negative write. The contract includes what observers may rely on, not just types and parameter names.

**Purpose:** Define a precise precondition and both success/failure postconditions as collaboration currency.



## Collaboration Flow for a Single Use Case

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:35–05:48

**Visual:** Carry the withdraw contract into a two-outcome timeline and add Notifier only after the account result is known.

**Script:** A contract only has meaning when a caller handles both its successful and failing outcomes.

**Purpose:** Use distinct branches to introduce collaboration flow.

### Scene 5 — Collaboration Flow for a Single Use Case

**Time:** 05:48–07:02

**Visual:** Sequence diagram Checkout→Account.withdraw(30)→SUCCESS→Notifier.receipt; branch on withdraw(90) at balance70 to FAILURE and strike through success receipt.

**Script:** Trace the sequence, not just the boxes. Checkout asks Account to withdraw. On success, it asks Notifier to deliver a receipt. If Account rejects an excessive request, Checkout must not send a success message. The collaborator controls its own decision; the coordinator controls the order of interaction. A call diagram helps us find which responsibility was bypassed when the result is wrong.

**Purpose:** Demonstrate messages and failure propagation without hidden field mutation.



## Object Composition from Smaller Responsible Parts

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:02–07:15

**Visual:** Transform the Checkout sequence diagram into three reusable collaborator cards, isolating FeePolicy as a replaceable piece.

**Script:** Several objects collaborate already; how can we assemble them without claiming they are subclasses of one another?

**Purpose:** Move from messages to component composition by role.

### Scene 6 — Object Composition from Smaller Responsible Parts

**Time:** 07:15–08:29

**Visual:** Arrange Checkout containing references to Account, FeePolicy and Notifier, each separate; swap FlatFeePolicy for PercentFeePolicy without making either a subtype of Account.

**Script:** Composition assembles a larger behavior from smaller responsible objects. Checkout works with Account to change balances, a FeePolicy to decide charges, and Notifier to deliver messages. Replacing one fee policy should not require changing account storage. These are collaborators, not different kinds of Account. Do not add wrapper after wrapper unless the boundary protects a meaningful responsibility.

**Purpose:** Explain has-a composition with substitutable policy collaborator and bounded complexity.



## Coupling and the Impact of Design Changes

<!-- VIDEO_SECTION -->

### Transition

**Time:** 08:29–08:42

**Visual:** Hold composition fixed but draw all the internal account fields Checkout can either read directly or ignore.

**Script:** Composition creates relationships; we still need to measure what each partner must know.

**Purpose:** Close responsibility chapter on contract-level dependencies.

### Scene 7 — Coupling and the Impact of Design Changes

**Time:** 08:42–09:56

**Visual:** Show a high-coupling Checkout reading Account.balance, ledger table and fee field versus low-coupling Checkout using only withdraw result. Change ledger storage in both diagrams.

**Script:** Here is the cost of looking inside every collaborator. If Checkout knows the account field names and ledger format, a storage change spreads outward. If it knows only the withdrawal contract, the account can change its internals without changing checkout logic. Of course, an empty promise such as doSomething is not useful either. Good coupling means depending on the correct behavioral facts and no unnecessary structure.

**Purpose:** Demonstrate change propagation through internal knowledge versus stable contract knowledge.
