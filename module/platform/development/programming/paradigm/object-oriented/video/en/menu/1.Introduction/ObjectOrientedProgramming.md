---
video:
  url: ""
---

# Object-Oriented Programming: Objects and Responsibilities

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


## Object-Oriented Programming: Meaning and Scope

<!-- VIDEO_SECTION -->

### Scene 1 — Object-Oriented Programming: Meaning and Scope

**Time:** 00:00–01:14

**Visual:** Draw two account cards A and B, each displaying 100; stamp distinct account IDs on them, then send withdraw(30) only to A. Keep B unchanged at 100.

**Script:** Two accounts may both show one hundred, but they are not the same account. One withdrawal changes A, not B. That distinction introduces object identity: we care which participant receives the request. An object can own behavior and state where appropriate, including immutable state. Object-oriented programming is about coordinating those responsibilities, not counting how many classes we have written.

**Purpose:** Start with observable identity versus equal values without equating objecthood with mutability.



## Purpose of Responsibility Boundaries for State and Behavior

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:14–01:27

**Visual:** Keep the two equal-looking account cards, then multiply the call sites around them and highlight three duplicate balance checks.

**Script:** These accounts are distinct; now ask who must enforce the same rule when three different callers want to withdraw.

**Purpose:** Carry identity into the motivation for shared behavioral responsibility.

### Scene 2 — Purpose of Responsibility Boundaries for State and Behavior

**Time:** 01:27–02:41

**Visual:** Show withdrawal checking copied in Screen, BatchJob and Checkout. Change the overdraft rule; display three edit warnings, then move the rule to an Account responsibility card.

**Script:** Imagine we change the withdrawal rule next month. If the screen, a batch job and checkout each calculate sufficient funds themselves, someone may update only two of them. The error is not lack of syntax; it is unclear ownership of the decision. A responsible account can accept a withdrawal request, enforce one rule and return a result that all callers understand.

**Purpose:** Motivate localized change and invariant ownership rather than claiming classes automatically fix maintenance.



## Procedural Organization over Separate Data

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:41–02:54

**Visual:** Shrink duplicated caller checks into one explicit withdraw(account,amount) function before placing that function beside a responsible Account object.

**Script:** Centralizing a rule helps either way. How does a free-standing procedure compare with an object that owns the decision?

**Purpose:** Prevent the false lesson that procedural organization is inherently incorrect.

### Scene 3 — Procedural Organization over Separate Data

**Time:** 02:54–04:08

**Visual:** Split screen: AccountData with free procedure withdraw(account,amount) on the left; Account with withdraw(amount) on the right. Both show the same successful 100→70 result.

**Script:** A procedural program can also be well designed. A function receiving account data and an amount can validate the request perfectly. The difference we want to explore is how responsibilities are organized as the system grows: is every caller free to coordinate account internals, or is the account itself the stable place for its balance rules? Choose based on complexity, not fashion.

**Purpose:** Compare both organizational styles fairly with identical evidence.



## Objects, Identity, State, Behavior, and Collaboration

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:08–04:21

**Visual:** Move the same account data and rule from the procedural diagram into an Account boundary, keeping the 100→70 trace unchanged.

**Script:** Both styles can work; the next frame shows what an owned behavioral boundary adds.

**Purpose:** Introduce the object model as a responsibility arrangement, not a different arithmetic result.

### Scene 4 — Objects, Identity, State, Behavior, and Collaboration

**Time:** 04:21–05:35

**Visual:** Animate ACCOUNT A with ID, current balance and withdraw behavior; a CHECKOUT caller sends withdraw(30), receives success and observes 100→70 without touching a balance field.

**Script:** Follow the message from Checkout to Account A. Checkout asks for a withdrawal; Account checks the request, decides whether it is allowed and updates its own state. The caller receives an outcome rather than instructions for modifying internal fields. Identity tells us which account is involved; the contract tells us which behavior the caller may rely on.

**Purpose:** Synthesize identity, state, behavior and collaboration as one call sequence.



## The OOP Mental Model versus Java Language Mechanics

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:35–05:48

**Visual:** Freeze Checkout→Account and lift the implementation syntax below a dividing line, retaining only request and promised outcome above.

**Script:** The responsibility model survives even when its language representation changes.

**Purpose:** Separate paradigm scope from its later language-specific implementation.

### Scene 5 — The OOP Mental Model versus Java Language Mechanics

**Time:** 05:48–07:02

**Visual:** Place design cards IDENTITY, CONTRACT, INVARIANT above a dashed line; beneath it place Java CLASS, INTERFACE, PRIVATE and DISPATCH marked separate language lessons.

**Script:** Notice what we have not needed so far: a Java access modifier or a particular class declaration. We can reason about who owns a withdrawal, which input is valid and what happens on failure before choosing a language mechanism. Java classes and interfaces may implement this model, but the OOP paradigm is the design reasoning above those details.

**Purpose:** Explicitly delimit OOP concepts from Java language mechanics and dispatch.



## Starting Knowledge and the Path from Objects to Design

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:02–07:15

**Visual:** Turn the Java mechanism cards face down and reveal the learning route from two accounts to a full checkout.

**Script:** Before choosing language features, follow the object responsibilities that those features should support.

**Purpose:** Preview chapter-to-chapter progression using the same account example.

### Scene 6 — Starting Knowledge and the Path from Objects to Design

**Time:** 07:15–08:29

**Visual:** Show a five-stop path with the account diagram evolving: identity → invariants → responsibility → polymorphic payment → design trade-offs; highlight the currently learned first stop.

**Script:** Here is the path through the next four videos. First distinguish identities, values and protected state. Then decide which object owns which behavior, and trace how they collaborate. Next compare several payment objects through one contract. Finally we will challenge our design: does inheritance preserve the contract, and is an object actually simpler than a small function for this task?

**Purpose:** Provide prerequisite-safe progression and an explicit useful handoff.
