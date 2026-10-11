<a id="back-to-top"></a>

# Object-Oriented Trade-offs and Design Synthesis

## Menu
- [Encapsulation Benefits and Costs of Object Boundaries](#oop-encapsulation-benefits)
- [Overengineering — When Too Many Objects Increase Complexity](#oop-overdesign-risks)
- [Fragile Inheritance — When Changes Break Behavioral Contracts](#oop-fragile-inheritance)
- [Shared Mutable State — Risks of Multiple Objects Changing Data](#oop-shared-state-risks)
- [When Procedures or Data Transformations Work Better Than OOP](#oop-alternative-paradigms)
- [From Requirement to Collaborating Objects: A Worked Design](#oop-design-walkthrough)
- [OOP Scope and Next Topics: Java Core, Design Principles, DDD, AOP](#oop-owner-handoff)

## <a id="oop-encapsulation-benefits">Encapsulation Benefits and Costs of Object Boundaries</a>

<details>
<summary>Click for details</summary>

Encapsulation lets an account change its storage representation while callers keep using the same deposit and withdrawal contract. It also provides a place to guard invariants and investigate invalid outcomes.

The trade-off is that behavior must be exercised through the object instead of arbitrary structure access; too many thin boundaries can complicate tracing. Hide implementation where it protects responsibility or meaningful independence.

**Verify the benefit through a real representation change.** Version 1 of Account stores `balance=100` in one field. Version 2 stores deposits and withdrawals in a ledger and derives the balance from entries. If clients only request `withdraw(30)`, both versions check funds, approve the action, and expose **70**. A Checkout that directly reads the balance field must change when the representation changes; a Checkout that honors the behavioral boundary may use the same request and result.

The cost is that maintainers inspect operations and outcomes instead of editing arbitrary fields; too many thin forwarding objects can make tracing harder. A useful boundary protects a real invariant or a decision likely to change, rather than **hiding everything** merely to look object-oriented. **Check:** between the two Account representations, what may change internally and what can Checkout legitimately expect to stay stable?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-overdesign-risks">Overengineering — When Too Many Objects Increase Complexity</a>

<details>
<summary>Click for details</summary>

If every tiny arithmetic operation passes through three coordinating objects, architecture can become more complex than the problem. Another warning sign is an `EverythingManager` handling accounts, payments, emails, and reporting.

Ask whether each boundary protects an invariant, localizes expected change, or supports real behavioral variation. Without meaningful separate responsibility, a pure function or clear procedure may be preferable to a large hierarchy.

**Remove an object that contributes no responsibility.** A tiny flow such as `Checkout → PaymentForwarderA → PaymentForwarderB → PaymentMethod` might pass `pay(30)` unchanged through two extra layers, adding no rule, state, or failure handling. Removing the forwarders shortens the trace while preserving the same payment contract and result. Keeping FeePolicy makes sense if its pricing decisions change independently; keeping Account makes sense because it protects balance validity.

An `EverythingManager` that approves withdrawals, calculates prices, and sends email has unrelated reasons to change. Splitting genuine Account, Checkout, and Notifier responsibilities can help; replacing it with many managers that merely call one another just **renames complexity**. **Check:** does a proposed intermediary own a real decision or promise, or does it only relay the same arguments?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-fragile-inheritance">Fragile Inheritance — When Changes Break Behavioral Contracts</a>

<details>
<summary>Click for details</summary>

Fragile inheritance occurs when changing a parent behavior unexpectedly affects child types that depend on its internal decisions. A parent adds a fee, while one child assumed that the fee would always be zero, silently changing outcomes.

A more serious failure is a subtype violating its parent's contract, such as reporting payment success after a genuine failure. Shared contract tests and composition where an “is-a” relation is weak reduce these risks.

**Follow a broken assumption.** A parent payment type originally uses `fee=0`, and a WalletPayment child was designed around that behavior. When the parent is modified to add a fee of 5, an unchanged child may now calculate 35 for a request that previously totaled 30. If the child or caller still promises an unchanged total, the behavior breaks even though the child file was untouched. The mechanism is **an inherited change invalidating an implicit assumption**.

Not every fee change is wrong: if the contract explicitly allows variable fees, 35 might be legitimate. The correct test is the actual observable promise, not the number alone. A separate FeePolicy can reduce unwanted hierarchy-wide impact when the fee rule varies independently; shared contract cases should be rerun for affected payment variants. **Check:** what evidence would you need before calling the parent's new fee a behavioral-contract violation?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-shared-state-risks">Shared Mutable State — Risks of Multiple Objects Changing Data</a>

<details>
<summary>Click for details</summary>

Two objects sharing mutable data can interfere when one changes that data outside the other's expected contract. A reporting object reading a balance that another component silently updates may observe an inconsistent snapshot.

Limit mutation pathways, use copied values when appropriate, and verify invariants after permitted operations. Concurrency and distributed transactions require additional coordination mechanisms; adopting OOP does not automatically solve them.

**Trace one read and one poorly coordinated write.** Report holds access to the same underlying Account A data when A=100. While Report prepares a statement saying “balance 100,” another component bypasses `withdraw` and modifies the value to 70. Report may publish **100**, although a later observation shows **70**. The issue is shared access without a clear contract about updates and observation time; it does not mean “many objects automatically create a race condition.”

At the object-model level, limit mutation pathways and specify whether reporting needs a stable snapshot. If genuinely concurrent threads participate, synchronization and transaction rules are separate concerns. OOP **does not automatically provide** race freedom, consistent distributed reads, or atomic transfers across accounts. **Check:** does Report's observation of 100 prove it saw the latest state after a withdrawal?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-alternative-paradigms">When Procedures or Data Transformations Work Better Than OOP</a>

<details>
<summary>Click for details</summary>

If the task only totals receipts from immutable input, a function returning a number may be clearer than several stateful objects. A simple read–compute–print workflow may work well as a small group of procedures.

Choose object modeling when identity, state rules, and collaborating responsibilities deserve explicit protection. Imperative, functional, and declarative styles can all appear inside an OOP design; good design is not a pledge to use objects everywhere.

**Choose the tool based on the task.** To total three immutable receipts `[10,20,30]`, a simple `sum(receipts)` traverses three values and returns **60**. Creating ReceiptSumManager, SumExecutor, and SumResultHolder may make this easy calculation harder to follow. In contrast, when Account has identity, a nonnegative-balance rule, history, and multiple withdrawal callers, an `withdraw` behavior boundary earns its existence by owning decisions and documented failures.

The approaches **can coexist**. An object-oriented Checkout can call a pure function to total receipt values; procedural programs can also centralize rules and test them properly. The decision depends on independent change, important state contracts, and comprehensibility rather than a pledge to use objects everywhere. **Check:** which of the receipt sum and withdrawal approval benefits more from an object with accountable state?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-design-walkthrough">From Requirement to Collaborating Objects: A Worked Design</a>

<details>
<summary>Click for details</summary>

**One integrated checkout: cart price 30, account initially 100.** Before allocating responsibilities, Checkout might read the balance, subtract money, and send mail by itself. Instead, define objects with specific promises: **Account** guards funds and `balance >= 0`; **Checkout** coordinates the request; **FeePolicy** quotes price and fees; **PaymentMethod** processes payment; and **Notifier** delivers an outcome once it is known. Two accounts with equal balances still have separate identities.

**Successful trace** (zero fee): (1) Cart supplies 30; (2) FeePolicy quotes total 30; (3) Checkout asks `WalletPayment.pay(30)`; (4) the payment method asks its Account to check `30>0` and `100>=30` → both true, so Account changes **100→70**; (5) PaymentMethod reports `Success(receiptId)`; (6) Checkout **only now** requests a receipt from Notifier. **Separate failure trace:** reset A to 100 and request payment 120. Account rejects for insufficient funds, stays at **100**, PaymentMethod reports `Failure(insufficientFunds)`, and Checkout skips success notification. This does not claim that external delivery must always succeed.

**Change the requirement:** replace WalletPayment with **BankPayment**. Checkout still calls `pay(amount)` and handles `Success(receiptId)` or `Failure(reason)`; a real bank decline properly returned as Failure is an allowed outcome. The selection/registration boundary may change, while callers of the established contract need no type-specific branch. If FeePolicy changes from fee zero to fee two, the quoted total becomes **32** and the amount passed to payment must match that quote; replacing a contract-compatible policy can preserve Checkout's call pattern. Ask *what changed, what stayed stable, and why*.

**Important limit:** a **different** example, transfer 30 between A=100 and B=20, would debit A to 70 and credit B to 50. If the credit fails after the debit, OOP **does not make the two updates atomic**. Transaction handling and recovery require additional mechanisms. That transfer is a boundary illustration, not a replacement for the Checkout/PaymentMethod synthesis. **Check:** trace the cart-30 flow in order, identify both result branches, and explain which ownership boundary changes when adding bank payment.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-owner-handoff">OOP Scope and Next Topics: Java Core, Design Principles, DDD, AOP</a>

<details>
<summary>Click for details</summary>

This module ends at the **object responsibility and contract mental model**. Java Core teaches `class`, `interface`, access rules, and dispatch; DDD teaches domain-model boundaries and aggregates; AOP handles cross-cutting behavior spanning several objects.

When a bug depends on Java-specific dispatch, follow the language modules. When an operation requires transactionality or shared-state coordination, consult architecture, transaction, and concurrency topics; OOP organization alone does not provide those runtime guarantees.

**Route a question to its true learning owner.** “What must Account guarantee after `withdraw`?” belongs to the OOP responsibility/invariant model. “How do I declare Java `class`, `interface`, `private`, or `override`, and which implementation does the JVM dispatch?” belongs to Java Core. “How do SOLID or the Law of Demeter guide larger designs?” belongs to Design Principles. “Which aggregate controls domain consistency?” is deeper Domain-Driven Design. “How can audit behavior cross several objects without repetition?” points toward AOP or another cross-cutting technique.

When transferring funds across two accounts, a debit may complete before a credit fails; object responsibility boundaries alone **do not ensure rollback, synchronization, or transaction atomicity**. Those belong to transaction/architecture/concurrency learning. We need no extra Java syntax, UML notation, or Spring lesson in this language-neutral paradigm module. **Check:** which neighboring module should explain why a particular Java `override` was selected at runtime?

</details>

- [Back to top](#back-to-top)
