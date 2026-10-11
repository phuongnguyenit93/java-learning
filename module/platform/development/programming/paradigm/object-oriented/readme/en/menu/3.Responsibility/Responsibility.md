<a id="back-to-top"></a>

# Object Responsibilities, Contracts, and Collaboration

## Menu
- [Object Responsibility — What an Object Must Handle](#oop-responsibility-purpose)
- [Responsibility Assignment — Choosing Which Object Owns Behavior](#oop-behavior-owner)
- [Cohesion — Keeping Related Behaviors Together](#oop-responsibility-cohesion)
- [Behavioral Contract — Promised Conditions and Outcomes](#oop-behavioral-contract)
- [Object Collaboration — Requests and Results across Objects](#oop-collaboration-flow)
- [Association and Dependency — Knowing and Using Other Objects](#oop-association-dependency)
- [Object Composition — Combining Collaborators for Larger Behavior](#oop-object-composition)
- [Coupling — How Objects Depend on Each Other](#oop-collaboration-coupling)

## <a id="oop-responsibility-purpose">Object Responsibility — What an Object Must Handle</a>

<details>
<summary>Click for details</summary>

A **responsibility** identifies which part of a system must uphold a rule or outcome. An account governs valid withdrawals; a notification component delivers messages rather than deciding account balances.

Clear responsibilities keep a withdrawal-rule change from forcing edits to an email sender. One object need not have only one method: several related behaviors can support a single coherent responsibility.

**One outcome can involve distinct responsibilities.** For a purchase of 30, Account is responsible for valid withdrawals, Checkout coordinates the purchase, and Notifier delivers an already determined result. If Notifier also deducts money, a changed overdraft policy must be maintained in two components that can disagree. Notifier's role is **communicating** payment information, not approving changes to the account.

One object can have multiple related behaviors: depositing, withdrawing, and reporting available funds all belong to account management. Responsibility assignment does not mean making **one object per function**; it means identifying who can uphold a promise as requirements change. **Check:** if the minimum permitted balance changes, which of Account, Checkout, and Notifier has a direct reason to change the withdrawal rule?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-behavior-owner">Responsibility Assignment — Choosing Which Object Owns Behavior</a>

<details>
<summary>Click for details</summary>

To choose a behavior owner, ask **who has the knowledge and authority** to make the decision without scattered coordination. Sufficient-balance checks belong near the account, not in a checkout screen; notification delivery belongs near a notifier.

If an orchestrator calculates balances, applies account rules, and stores history, responsibilities may be misplaced. Do not force multi-account coordination into one account object when the task inherently spans collaborators.

**Compare two ownership decisions.** Option A: Checkout reads `balance=100`, decides whether 120 can be deducted, and writes a new balance; a separate batch repeats the check. Option B: Checkout asks `Account.withdraw(120)`. Account, which knows and protects its validity rule, finds insufficient funds, returns `Failure(insufficientFunds)`, and keeps 100. Option B concentrates decision authority instead of distributing it among callers.

However, “put behavior near its information” does **not** mean putting every business flow inside Account. Checkout owns orchestration across pricing, payment method selection, and notifications; a transfer between **two** accounts also needs coordination across participants. **Responsibility** is what must be guaranteed; **responsibility assignment** is the design decision about which object owns that guarantee. **Check:** who should reject an unaffordable withdrawal, and who should decide whether a receipt is requested?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-responsibility-cohesion">Cohesion — Keeping Related Behaviors Together</a>

<details>
<summary>Click for details</summary>

**Cohesion** measures how closely an object's behaviors support its purpose. `deposit`, `withdraw`, and `availableBalance` belong to account management; `sendPromotionalEmail` serves a different concern.

A catch-all object tends to change for unrelated reasons. Splitting coherent responsibilities makes them easier to explain and test, though the resulting design may need more collaboration between objects.

**Imagine two unrelated requirement changes.** First, accounts must retain a minimum reserve of 20. Second, marketing emails need new wording. If both changes touch `EverythingAccount`, it contains unrelated reasons to change. Keeping deposits, withdrawals, and available balance in Account while moving notification delivery to Notifier makes those reasons easier to isolate.

Cohesion is not just method count: an object with five closely related balance operations may be more cohesive than one with only two unrelated operations. Separation can introduce **more collaboration**, so later we also consider coupling; we should not split every small method mechanically. **Check:** do `sendPromotionalEmail` and `withdraw` share the same reason to change?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-behavioral-contract">Behavioral Contract — Promised Conditions and Outcomes</a>

<details>
<summary>Click for details</summary>

A **behavioral contract** states what a caller must provide and what an object guarantees. `withdraw(amount)` may require a positive amount and promise either a correct deduction or a failure leaving the balance unchanged, with an understandable reason.

A contract is more than input and return types. Two routines accepting numbers are not interchangeable if one bypasses the sufficient-funds rule. Behavioral contracts support both collaboration and later polymorphism.

**Write a promise precise enough to trace success and failure.** For a non-overdraft account, our conceptual `withdraw(amount)` requires a positive amount. When the amount is valid **and funds are sufficient**, it returns `Success` and deducts exactly that amount. If funds are insufficient, it returns `Failure(insufficientFunds)` **without changing the balance**. A zero or negative input returns a documented `InvalidInput` outcome. These results are **the contract chosen for our example**, not universal Java exception rules.

| Initial balance | Request | Account's decision | Observable result |
| --- | --- | --- | --- |
| 100 | withdraw 30 | sufficient funds; update | `Success`, balance 70 |
| 100 | withdraw 120 | insufficient funds; skip update | `Failure(insufficientFunds)`, balance 100 |
| 100 | withdraw 0 | invalid input; skip update | `InvalidInput`, balance 100 |

The caller supplies the permitted request; the supplier honors the promised outcomes, including **a correctly reported failure**. Two implementations accepting the same parameter types may still have different behavioral contracts. **Check:** if insufficient funds are explicitly documented as a failure result, is reporting that failure a contract violation?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-collaboration-flow">Object Collaboration — Requests and Results across Objects</a>

<details>
<summary>Click for details</summary>

A checkout use case may flow through an orchestrator requesting an account withdrawal, receiving its result, and asking a notifier to deliver a receipt. Each object performs its own responsibility; coordination never rewrites a collaborator's internal fields.

Trace messages, results, and failures: if the withdrawal is rejected, the system must not send a success receipt. Such request–response collaboration is the conceptual OOP model, independent of class-diagram syntax.

**Trace messages and branches across participants.** Cart supplies price 30; Checkout asks FeePolicy for a quote (assume zero fee) and requests payment of 30. Account/PaymentMethod checks what it owns and returns `Success(receiptId)`. **Only then** does Checkout ask Notifier to deliver a success receipt. A conceptual message trace:

```text
Checkout → FeePolicy.quote(30) → total 30
Checkout → PaymentMethod.pay(30)
    ├─ Success(receiptId) → Notifier.sendReceipt(receiptId)
    └─ Failure(reason)   → report reason; DO NOT send success receipt
```

With A=100, valid withdrawal 30 leaves 70 and can yield success. With request 120 from the initial 100, insufficient funds leaves the balance unchanged and the failure branch skips success notification. Checkout **coordinates**, Account **protects funds**, FeePolicy **calculates prices**, and Notifier **delivers messages**. This trace does not prove transaction atomicity or guaranteed email delivery. **Check:** why can a success receipt not be requested before payment confirmation?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-association-dependency">Association and Dependency — Knowing and Using Other Objects</a>

<details>
<summary>Click for details</summary>

These terms describe **relationships**, not automatic ownership. An **association** means two objects participate in a modeled relationship: Account A is associated with account holder X, and the system retains that relationship across operations. A **dependency** means one participant needs another's **capability** to perform its task: Checkout depends on `Notifier.sendReceipt` after a successful payment. Checkout might receive Notifier only for that operation; it does not necessarily store a reference for its whole lifetime.

**Observe the request:** Checkout receives `Success(receipt42)` → requires notification → invokes Notifier → receives a delivery outcome. That is a dependency on **message delivery**, not evidence that Checkout creates, owns, or destroys Notifier. An association may also support dependent behavior, so the terms are not mutually exclusive. Neither by itself implies **strong composition in UML** (Unified Modeling Language, a language for modeling systems), which has whole–part lifecycle constraints requiring additional model rules. **Check:** does calling Notifier once imply Checkout owns Notifier's lifetime?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-object-composition">Object Composition — Combining Collaborators for Larger Behavior</a>

<details>
<summary>Click for details</summary>

**Composition** assembles larger behavior from collaborators with distinct responsibilities. A checkout flow can use `Account`, `FeePolicy`, and `Notifier` without treating them as subtypes of one another.

A fee policy can be replaced by another implementation honoring the same contract. Composition is not automatically simpler if it creates many forwarding layers; use it when responsibilities and change boundaries are meaningful.

**Assemble larger behavior from smaller responsibilities.** Checkout uses FeePolicy to determine charges, PaymentMethod to pay, and Notifier to communicate. With a zero fee, cart price 30 leads to payment request 30. Replacing FeePolicy with another implementation of `quote` that adds a fee of 2 changes the total to 32; Checkout still asks `quote` and then `pay(total)`. Checkout did not have to inherit from FeePolicy. This is **object composition in the sense of assembling behavioral collaborators**.

Avoid conflating that idea with **UML strong composition**, which concerns whole–part ownership/lifetime semantics. Saying Checkout **has** a Notifier or **uses** Account does not prove such ownership. Order and its OrderLines might be modeled as a strong whole–part relationship **if** the business truly enforces that lifetime rule; a reference field alone does not establish it. **Aggregation** is part–whole vocabulary, not a mandatory classification for every “has-a” statement. **Check:** must replacing FeePolicy produce an inheritance hierarchy of payment methods?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-collaboration-coupling">Coupling — How Objects Depend on Each Other</a>

<details>
<summary>Click for details</summary>

**Coupling** describes how much one object must know about another. If checkout knows account balance-field names, storage layouts, and history formatting, a local account change can ripple through the system.

Relying on a precise `withdraw` contract reduces knowledge of account internals. Yet an overly vague contract can hide failures; the goal is dependency on adequate behavioral promises, not eliminating every relationship.

**Measure coupling by the reach of a change.** If Checkout knows Account's `balance` field, its storage format, and how to subtract 30, moving Account to a ledger-based representation forces Checkout to change. If Checkout knows only the precise `withdraw(30)` behavior and `Success/Failure` outcomes, Account can change storage internally while Checkout uses the same call. In both cases the observable valid withdrawal from 100 still leaves 70.

Coupling is not merely a count of arrows: Checkout **must** depend on a payment capability to do its work. We seek dependencies on **stable and accurate commitments**, not the fiction that components share no knowledge. An overly vague contract that hides why payment failed would create its own problem; loose coupling must not erase necessary behavioral guarantees. **Check:** which checkout design changes when Account stops storing one balance field and derives balances from a ledger?

</details>

- [Back to top](#back-to-top)
