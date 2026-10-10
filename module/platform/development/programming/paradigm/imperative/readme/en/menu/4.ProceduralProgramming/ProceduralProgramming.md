<a id="back-to-top"></a>

# Organizing an Imperative Program with Procedures

## Menu
- [Procedures as Groups of Related Actions](#imperative-procedure-purpose)
- [Procedure Inputs, Outputs, and Contracts](#imperative-procedure-contract)
- [Local Data and State Boundaries](#imperative-procedure-local-state)
- [Side Effects of Procedure Calls](#imperative-procedure-effects)
- [Decomposing Work into Collaborating Procedures](#imperative-procedure-decomposition)
- [Limitations of Procedures with Hidden External State](#imperative-procedure-limitations)

## <a id="imperative-procedure-purpose">Procedures as Groups of Related Actions</a>

<details>
<summary>Click for details</summary>

A procedure is a named group of actions performing one task, such as `withdraw(amount)` instead of repeating validation and subtraction in many places. It provides an **organizational boundary** while its body may still execute imperative commands.

Do not extract a procedure solely because code is long. Give it a coherent purpose: updating a balance differs from calculating a potential withdrawal. Such boundaries make failures easier to localize and individual tasks easier to test.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-procedure-contract">Procedure Inputs, Outputs, and Contracts</a>

<details>
<summary>Click for details</summary>

A procedure's contract describes **valid inputs, outcomes, and permitted changes**. `withdraw(30)` may require a positive amount and sufficient balance, promising to deduct exactly 30 on success; on failure, it returns a reason without changing the balance.

Callers should know the required conditions without inspecting every internal command. Any logging or external update the procedure performs should also be stated to avoid unexpected observable effects.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-procedure-local-state">Local Data and State Boundaries</a>

<details>
<summary>Click for details</summary>

Local state belongs to one procedure invocation, such as a `newBalance` value computed before committing an update. It keeps intermediate calculations away from unrelated work. A similarly named variable in another call need not refer to the same storage.

Distinguish local scratch values from shared state read or written by the procedure. Changing a local `newBalance` during a trial calculation does not update the account until an explicit account-state write occurs.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-procedure-effects">Side Effects of Procedure Calls</a>

<details>
<summary>Click for details</summary>

A procedure can return a value and also produce **side effects**: logging, printing, storing data, or changing shared account state. Repeating a call is not necessarily harmless, even if the returned value is ignored.

A routine called `displayBalance()` should ordinarily read state. If it also charges a fee on every call, the behavior is hidden by its name. Stating effects explicitly and separating computation from writes improves predictability.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-procedure-decomposition">Decomposing Work into Collaborating Procedures</a>

<details>
<summary>Click for details</summary>

A transfer flow can be decomposed into `validateTransfer`, `checkBalance`, `debit`, `credit`, and `recordResult`. Each procedure owns a comprehensible task; coordination specifies the execution order and handles failures between actions.

Decomposing a workflow does not make it atomic. If the source is debited and the destination credit fails, the system needs a recovery policy; actual transaction guarantees are outside the programming-paradigm scope.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-procedure-limitations">Limitations of Procedures with Hidden External State</a>

<details>
<summary>Click for details</summary>

If `withdraw(amount)` silently reads a global balance, identical arguments may yield different results depending on previous calls. Hidden dependencies make contracts harder to understand and tests require an entire environment rather than a simple input.

Reduce coupling by supplying necessary values explicitly, limiting what may be changed, and documenting effects. Some procedures necessarily interact with application state; the goal is **controlled and explainable mutation**, not banning all writes.

</details>

- [Back to top](#back-to-top)
