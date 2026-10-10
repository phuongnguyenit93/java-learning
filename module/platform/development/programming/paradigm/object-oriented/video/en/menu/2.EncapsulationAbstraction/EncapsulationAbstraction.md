---
video:
  url: ""
---

# Object Identity, State, and Encapsulation

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


## Object Identity versus Value Equality

<!-- VIDEO_SECTION -->

### Scene 1 — Object Identity versus Value Equality

**Time:** 00:00–01:14

**Visual:** Give A and B separate identity tokens but equal balance=100. In a second lane, draw two 100 VND value cards with equal numeric contents; label identity versus value equality.

**Script:** An account is a continuing entity. A and B may show the same one hundred, but withdrawing from A must not silently affect B. Two money values of one hundred, by contrast, can be equal by content without being the same account. We choose identity or value equality according to the business meaning, not according to whether a language happens to allocate a new object.

**Purpose:** Teach entity identity with equal balances and value equality without Java reference-operator shortcuts.



## Associated State and Behavior without Mandatory Mutation

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:14–01:27

**Visual:** Lock the ID of account A in place, then animate a read-only question and a changing request on that same identity.

**Script:** We know which object it is. What can it do without changing, and which operation may write?

**Purpose:** Move from identity to the meaning of object behavior and optional state change.

### Scene 2 — Associated State and Behavior without Mandatory Mutation

**Time:** 01:27–02:41

**Visual:** Keep account A at 100; animate canWithdraw(30) as a read-only answer, then withdraw(30) as a conditional state change. Show separate immutable FeePolicy card.

**Script:** Look carefully at the verbs. Asking canWithdraw does not require changing the balance. Calling withdraw may change it, but only if the condition allows it. A policy object can even be immutable and still answer fee queries through behavior. Having behavior does not imply mutation, and object-oriented design does not require every object to be mutable.

**Purpose:** Distinguish read-only requests, state transitions and immutable objects.



## Encapsulation: Protecting Internal State through Behavior

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:41–02:54

**Visual:** On the same account, reveal a red arrow from outside to the balance field and replace it with a request arrow labeled withdraw.

**Script:** If behavior may mutate, who gets to decide whether that mutation is valid?

**Purpose:** Motivate encapsulation through protection of object rules.

### Scene 3 — Encapsulation: Protecting Internal State through Behavior

**Time:** 02:54–04:08

**Visual:** Contrast external account.balance=-50 in red with caller→withdraw(50) across an Account boundary; rejected request leaves balance unchanged at 30.

**Script:** If a checkout screen can assign minus fifty directly into the account, the account cannot enforce a no-overdraft rule. Encapsulation means drawing a boundary so outsiders request a meaningful action, and the owner decides whether to allow the change. It is not merely about marking a field private; the important point is who has authority to protect the business rule.

**Purpose:** Explain encapsulation as protected decision-making with a concrete forbidden write.



## Object Invariants across Behavioral Operations

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:08–04:21

**Visual:** Keep the withdraw arrow and add a guard labeled balance≥0; replay an accepted request beside an excessive one.

**Script:** The boundary now has a job: every public state change must preserve a stated condition.

**Purpose:** Connect encapsulation to its checkable invariant rather than access syntax.

### Scene 4 — Object Invariants across Behavioral Operations

**Time:** 04:21–05:35

**Visual:** Use a balance>=0 gauge. Show 100→70 after withdraw30 as green, then attempted withdraw120 rejected while gauge stays green at 70; mark forbidden -50 in red.

**Script:** An invariant is a condition that must hold whenever the account exposes a valid state. We choose balance at least zero. A successful withdrawal can change one hundred to seventy without breaking that condition. An attempt to remove one hundred twenty from seventy must fail and leave seventy in place. The invariant is a rule about valid states, not a demand that values never change.

**Purpose:** Demonstrate permitted mutation that preserves the invariant and rejected mutation that would violate it.



## Consistency Conditions versus Immutable Data

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:35–05:48

**Visual:** Freeze the green balance≥0 predicate while allowing the displayed number to move; beside it freeze an invalid negative record.

**Script:** A rule can stay true while data changes, and data can stay unchanged while a rule is already broken.

**Purpose:** Use two contrasting timelines to separate invariant from immutability.

### Scene 5 — Consistency Conditions versus Immutable Data

**Time:** 05:48–07:02

**Visual:** Split screen: mutable account balance 100→70 still satisfies green balance≥0; immutable transaction record amount=-5 never changes but is marked INVALID.

**Script:** There are two very different uses of the word invariant. An account invariant is a rule, such as nonnegative balance, that stays true across allowed transitions. Immutable data is data that cannot be changed after creation. A frozen transaction record containing a forbidden negative amount is still invalid. Making a value immutable does not replace construction-time validation.

**Purpose:** Prevent confusion between preserved business predicates and frozen values.



## Exposed Behavioral Boundaries and Hidden Implementation

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:02–07:15

**Visual:** Replace the raw balance assignment arrow with withdraw(amount); fade storage details but keep result and failure visible.

**Script:** A boundary protects the account only if callers rely on its behavior instead of its storage layout.

**Purpose:** Bridge guarded state to a usable behavioral interface.

### Scene 6 — Exposed Behavioral Boundaries and Hidden Implementation

**Time:** 07:15–08:29

**Visual:** Hide the internal balance field, history store and fee algorithm behind Account. Expose withdraw(amount) and a success/failure result card; cross out public setBalance.

**Script:** What should other objects see? They should see a request that makes sense for the domain, such as withdraw, and the result it promises. They should not need to know whether the account stores cents, a decimal amount or a ledger. We can change those internals if the observable contract stays the same. Hidden details do not mean hidden critical side effects; the contract must still tell callers what can happen.

**Purpose:** Show a stable request and outcome despite changing internal representation.



## Abstraction as the Behavior an Object Promises

<!-- VIDEO_SECTION -->

### Transition

**Time:** 08:29–08:42

**Visual:** Keep withdraw outcomes visible and replace the implementation box with a generic pay(amount) request and its possible results.

**Script:** Once a useful behavior is exposed, callers can depend on the promise without seeing its implementation.

**Purpose:** Hand off encapsulation into behavioral contracts and later polymorphic variants.

### Scene 7 — Abstraction as the Behavior an Object Promises

**Time:** 08:42–09:56

**Visual:** Show Checkout→pay(30)→PaymentMethod contract. Under a curtain swap a wallet fee algorithm for a bank transfer path while preserving VALID, SUCCESS and FAILURE cards.

**Script:** Abstraction is the promise visible to a caller. Checkout asks pay thirty, then handles either a confirmed payment or a reported failure. It should not require all payment objects to hold matching fields or calculate fees in the same steps. A useful abstraction describes a real request and its observable obligations. A vague doEverything command tells us too little; exposing every internal field tells us too much.

**Purpose:** Define abstraction as a concrete behavioral promise preparing future polymorphism.
