<a id="back-to-top"></a>

# Object-Oriented Trade-offs and Design Boundaries

## Menu
- [A Design Walkthrough from Requirements to Collaborating Objects](#oop-design-walkthrough)
- [Benefits and Costs of Encapsulation Boundaries](#oop-encapsulation-benefits)
- [Overengineering with Objects and Scattered Responsibilities](#oop-overdesign-risks)
- [Fragile Inheritance and Broken Behavioral Contracts](#oop-fragile-inheritance)
- [Risks of Shared Mutable State across Objects](#oop-shared-state-risks)
- [Cases Favoring Procedures or Data Transformations](#oop-alternative-paradigms)
- [Boundaries with Java Core, DDD, AOP, and Other Paradigms](#oop-owner-handoff)

## <a id="oop-design-walkthrough">A Design Walkthrough from Requirements to Collaborating Objects</a>

<details>
<summary>Click for details</summary>

Start with a requirement: transfer 30 from account A with 100 to B with 20 and report the outcome. Accounts own balance rules, a transfer coordinator spans both accounts, and a notifier only delivers results.

A successful trace debits A to 70, credits B to 50, and sends confirmation. If crediting B fails after debiting A, consistency across objects requires recovery and transaction mechanisms; OOP collaboration alone does not make the operation atomic.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-encapsulation-benefits">Benefits and Costs of Encapsulation Boundaries</a>

<details>
<summary>Click for details</summary>

Encapsulation lets an account change its storage representation while callers keep using the same deposit and withdrawal contract. It also provides a place to guard invariants and investigate invalid outcomes.

The trade-off is that behavior must be exercised through the object instead of arbitrary structure access; too many thin boundaries can complicate tracing. Hide implementation where it protects responsibility or meaningful independence.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-overdesign-risks">Overengineering with Objects and Scattered Responsibilities</a>

<details>
<summary>Click for details</summary>

If every tiny arithmetic operation passes through three coordinating objects, architecture can become more complex than the problem. Another warning sign is an `EverythingManager` handling accounts, payments, emails, and reporting.

Ask whether each boundary protects an invariant, localizes expected change, or supports real behavioral variation. Without meaningful separate responsibility, a pure function or clear procedure may be preferable to a large hierarchy.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-fragile-inheritance">Fragile Inheritance and Broken Behavioral Contracts</a>

<details>
<summary>Click for details</summary>

Fragile inheritance occurs when changing a parent behavior unexpectedly affects child types that depend on its internal decisions. A parent adds a fee, while one child assumed that the fee would always be zero, silently changing outcomes.

A more serious failure is a subtype violating its parent's contract, such as reporting payment success after a genuine failure. Shared contract tests and composition where an “is-a” relation is weak reduce these risks.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-shared-state-risks">Risks of Shared Mutable State across Objects</a>

<details>
<summary>Click for details</summary>

Two objects sharing mutable data can interfere when one changes that data outside the other's expected contract. A reporting object reading a balance that another component silently updates may observe an inconsistent snapshot.

Limit mutation pathways, use copied values when appropriate, and verify invariants after permitted operations. Concurrency and distributed transactions require additional coordination mechanisms; adopting OOP does not automatically solve them.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-alternative-paradigms">Cases Favoring Procedures or Data Transformations</a>

<details>
<summary>Click for details</summary>

If the task only totals receipts from immutable input, a function returning a number may be clearer than several stateful objects. A simple read–compute–print workflow may work well as a small group of procedures.

Choose object modeling when identity, state rules, and collaborating responsibilities deserve explicit protection. Imperative, functional, and declarative styles can all appear inside an OOP design; good design is not a pledge to use objects everywhere.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-owner-handoff">Boundaries with Java Core, DDD, AOP, and Other Paradigms</a>

<details>
<summary>Click for details</summary>

This module ends at the **object responsibility and contract mental model**. Java Core teaches `class`, `interface`, access rules, and dispatch; DDD teaches domain-model boundaries and aggregates; AOP handles cross-cutting behavior spanning several objects.

When a bug depends on Java-specific dispatch, follow the language modules. When an operation requires transactionality or shared-state coordination, consult architecture, transaction, and concurrency topics; OOP organization alone does not provide those runtime guarantees.

</details>

- [Back to top](#back-to-top)
