<a id="back-to-top"></a>

# Object Responsibilities, Contracts, and Collaboration

## Menu
- [The Purpose of Object Responsibilities](#oop-responsibility-purpose)
- [Choosing the Owner of a Behavior or Decision](#oop-behavior-owner)
- [Cohesion among Related Responsibilities](#oop-responsibility-cohesion)
- [Behavioral Contracts between Collaborators](#oop-behavioral-contract)
- [Collaboration Flow for a Single Use Case](#oop-collaboration-flow)
- [Object Composition from Smaller Responsible Parts](#oop-object-composition)
- [Coupling and the Impact of Design Changes](#oop-collaboration-coupling)

## <a id="oop-responsibility-purpose">The Purpose of Object Responsibilities</a>

<details>
<summary>Click for details</summary>

A **responsibility** identifies which part of a system must uphold a rule or outcome. An account governs valid withdrawals; a notification component delivers messages rather than deciding account balances.

Clear responsibilities keep a withdrawal-rule change from forcing edits to an email sender. One object need not have only one method: several related behaviors can support a single coherent responsibility.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-behavior-owner">Choosing the Owner of a Behavior or Decision</a>

<details>
<summary>Click for details</summary>

To choose a behavior owner, ask **who has the knowledge and authority** to make the decision without scattered coordination. Sufficient-balance checks belong near the account, not in a checkout screen; notification delivery belongs near a notifier.

If an orchestrator calculates balances, applies account rules, and stores history, responsibilities may be misplaced. Do not force multi-account coordination into one account object when the task inherently spans collaborators.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-responsibility-cohesion">Cohesion among Related Responsibilities</a>

<details>
<summary>Click for details</summary>

**Cohesion** measures how closely an object's behaviors support its purpose. `deposit`, `withdraw`, and `availableBalance` belong to account management; `sendPromotionalEmail` serves a different concern.

A catch-all object tends to change for unrelated reasons. Splitting coherent responsibilities makes them easier to explain and test, though the resulting design may need more collaboration between objects.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-behavioral-contract">Behavioral Contracts between Collaborators</a>

<details>
<summary>Click for details</summary>

A **behavioral contract** states what a caller must provide and what an object guarantees. `withdraw(amount)` may require a positive amount and promise either a correct deduction or a failure leaving the balance unchanged, with an understandable reason.

A contract is more than input and return types. Two routines accepting numbers are not interchangeable if one bypasses the sufficient-funds rule. Behavioral contracts support both collaboration and later polymorphism.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-collaboration-flow">Collaboration Flow for a Single Use Case</a>

<details>
<summary>Click for details</summary>

A checkout use case may flow through an orchestrator requesting an account withdrawal, receiving its result, and asking a notifier to deliver a receipt. Each object performs its own responsibility; coordination never rewrites a collaborator's internal fields.

Trace messages, results, and failures: if the withdrawal is rejected, the system must not send a success receipt. Such request–response collaboration is the conceptual OOP model, independent of class-diagram syntax.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-object-composition">Object Composition from Smaller Responsible Parts</a>

<details>
<summary>Click for details</summary>

**Composition** assembles larger behavior from collaborators with distinct responsibilities. A checkout flow can use `Account`, `FeePolicy`, and `Notifier` without treating them as subtypes of one another.

A fee policy can be replaced by another implementation honoring the same contract. Composition is not automatically simpler if it creates many forwarding layers; use it when responsibilities and change boundaries are meaningful.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-collaboration-coupling">Coupling and the Impact of Design Changes</a>

<details>
<summary>Click for details</summary>

**Coupling** describes how much one object must know about another. If checkout knows account balance-field names, storage layouts, and history formatting, a local account change can ripple through the system.

Relying on a precise `withdraw` contract reduces knowledge of account internals. Yet an overly vague contract can hide failures; the goal is dependency on adequate behavioral promises, not eliminating every relationship.

</details>

- [Back to top](#back-to-top)
