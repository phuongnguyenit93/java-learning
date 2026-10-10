---
video:
  url: ""
---

# Type Relationships, Inheritance, and Polymorphism

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


## Polymorphism: Concept and Motivation for Varying Behavior

<!-- VIDEO_SECTION -->

### Scene 1 — Polymorphism: Concept and Motivation for Varying Behavior

**Time:** 00:00–01:14

**Visual:** Show Checkout→pay(30) and two possible implementations WalletPayment and BankPayment; remove an if(wallet)/else if(bank) branch at the caller.

**Script:** Imagine checkout supports a wallet and a bank transfer. The caller should not need an expanding switch statement for every payment option. Polymorphism lets it make the same contracted request, pay thirty, while different objects perform different internal work. The important condition is that each option respects the outcome promises. One implementation can fail for a valid provider reason, but it cannot pretend the payment succeeded.

**Purpose:** Motivate polymorphism via a stable caller and genuinely varied behavior.



## A Shared Contract for Alternative Behaviors

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:14–01:27

**Visual:** Freeze the unchanged pay(30) caller and reveal the success, decline and invalid-input rows both implementations must satisfy.

**Script:** We have alternative implementations. What shared outcome rules permit checkout to use either one?

**Purpose:** Make the common behavioral contract explicit before talking about subtyping.

### Scene 2 — A Shared Contract for Alternative Behaviors

**Time:** 01:27–02:41

**Visual:** Draw a three-row contract table: positive amount+processed→confirmation ID; positive amount+decline→failure reason; invalid amount→documented input error. Place both variants under it.

**Script:** Let us write the common promise before comparing classes. A positive payment amount is eligible for processing, not guaranteed success. On completion the caller gets a valid confirmation identifier. On a provider decline it gets a clear failure reason. A zero or negative request follows the documented invalid-input path. These outcomes are the interface from the caller perspective; the two implementations may have completely different internals.

**Purpose:** Prevent equating eligible input with mandatory provider success and ground subtype tests.



## Subtyping and Promises Made by a Subtype

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:41–02:54

**Visual:** Keep the positive-input contract card on screen; place two wallet paths beneath it and test which outcome the caller was promised.

**Script:** Sharing a method name is not enough when a subtype changes which requests are even accepted for processing.

**Purpose:** Transition from a shared promise to the exact substitution obligation.

### Scene 3 — Subtyping and Promises Made by a Subtype

**Time:** 02:54–04:08

**Visual:** Show PaymentMethod accepting positive 30, WalletPayment subtype with two lanes: returned DECLINED reason allowed; undocumented UnsupportedAmount exception red crossed out.

**Script:** A subtype promises it can stand where a broader payment contract is expected. If the common contract accepts all positive amounts for processing, a wallet cannot suddenly require amount over fifty and throw an undocumented error at thirty. But it may report a real provider decline through the documented failure result. That distinction matters: a legitimate business failure is not the same as refusing to uphold the caller contract.

**Purpose:** Explain strengthened-precondition violation versus legitimate provider decline.



## Inheritance as Organization and Reuse

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:08–04:21

**Visual:** Split the subtype diagram into a blue code-reuse arrow and a green contract arrow instead of showing one undifferentiated inheritance line.

**Script:** A syntactic hierarchy may share implementation without preserving every promise.

**Purpose:** Prepare the learner for separate inheritance and behavioral tests.

### Scene 4 — Inheritance as Organization and Reuse

**Time:** 04:21–05:35

**Visual:** Build a parent Payment implementation with a common fee helper and child WalletPayment; show inherited code reuse in blue and a separate contract-compliance checklist in green.

**Script:** Inheritance is one way a language can organize and reuse behavior. A child may inherit fee calculation code, but that alone does not show that the child can safely replace its parent. If the child secretly imposes new constraints, the caller contract can break even while every method compiles. Keep two separate questions: is the shared code useful, and is the behavioral relationship valid?

**Purpose:** Distinguish code reuse from proof of correct substitutability.



## Substitutability and Behavioral Expectations

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:35–05:48

**Visual:** Move from the inheritance arrows to a shared test harness applying identical caller scenarios to both variants.

**Script:** Reuse of a parent implementation cannot substitute for checking the consequences seen by callers.

**Purpose:** Turn the abstract substitution claim into concrete test cases.

### Scene 5 — Substitutability and Behavioral Expectations

**Time:** 05:48–07:02

**Visual:** Run the same test cards on Wallet and Bank implementations: positive success, provider decline, zero input, timeout failure. Cross out wallet falsely returning SUCCESS on failed processing.

**Script:** To check substitutability, run the same contracted situations against each variant. Do both represent valid success with a confirmation? Do they report provider rejection without announcing success? Do they handle invalid inputs as promised? A child type that returns success before actual processing violates expectations even if its signature matches. Real substitutability is an observable behavioral property.

**Purpose:** Give contract-test evidence for legitimate and illegitimate subtype outcomes.



## Choosing Composition versus Inheritance

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:02–07:15

**Visual:** After seeing a green contract check, move the fee calculation outside the payment type and redraw it as a has-a dependency.

**Script:** Some variation belongs to a replaceable collaborator rather than a subtype of the whole payment service.

**Purpose:** Ground the relationship choice in responsibility and promised behavior.

### Scene 6 — Choosing Composition versus Inheritance

**Time:** 07:15–08:29

**Visual:** Compare PaymentService HAS FeePolicy connected by has-a versus FeePolicy IS-A PaymentMethod crossed out. Swap fee policy cards without editing a class tree.

**Script:** Should the payment service inherit from its fee policy? No: a fee policy is not itself a payment method. It is a collaborator the service has. Composition often makes that kind of change easier. Inheritance belongs where one type really satisfies another type’s contract, not merely where two classes share code. Composition still has a cost, so avoid creating many forwarding layers for one trivial calculation.

**Purpose:** Apply genuine subtype criterion instead of defaulting blindly to inheritance or composition.



## Extending Behavioral Variants through Polymorphism

<!-- VIDEO_SECTION -->

### Transition

**Time:** 08:29–08:42

**Visual:** Keep the contract and test cards from the previous scene, then slide a third payment variant underneath the same caller.

**Script:** We have chosen a meaningful extension point; can a new option join without changing every caller?

**Purpose:** Finish polymorphism with open variation constrained by a stable contract.

### Scene 7 — Extending Behavioral Variants through Polymorphism

**Time:** 08:42–09:56

**Visual:** Add FastTransferPayment beside Wallet and Bank; keep Checkout→pay(amount) and reuse the same four contract test cards with a new implementation.

**Script:** Now the business adds fast transfer. Ideally checkout still asks pay and handles the same confirmation or failure contract. The new implementation can choose its own internal process, but it must pass the existing behavior tests. This is useful only because payment behavior genuinely varies. If we were merely adding two numbers, a whole inheritance hierarchy would make the work harder, not clearer.

**Purpose:** Demonstrate extensibility without changing caller logic or weakening contract.
