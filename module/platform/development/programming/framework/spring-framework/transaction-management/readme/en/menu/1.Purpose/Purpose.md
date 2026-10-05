<a id="back-to-top"></a>

# Why Transaction Management Exists

## Menu
- [What Spring Transaction Management Is For](#transaction-purpose)
- [Transaction as an Application Unit of Work](#transaction-unit-of-work)
- [Service Boundary vs Individual Repository Calls](#transaction-service-boundary)
- [What Spring Adds Above Native Transaction APIs](#transaction-framework-value)
- [Where Local Transaction Guarantees Stop](#transaction-local-boundary)

## <a id="transaction-purpose">What Spring Transaction Management Is For</a>

<details>
<summary>Click for details</summary>

Transaction management exists because a business operation often changes more than one piece of state, while the business result must still behave as one unit. If an order row is inserted but inventory reservation fails, the application usually must not leave the database in a half-finished state.

Spring transaction management gives application code one abstraction for declaring **where that unit begins and ends, which transaction policy applies, and which resource-specific transaction manager executes it**. It does not invent database atomicity; it coordinates native transaction capabilities through a consistent programming model.

The module therefore answers four recurring questions:

```text
Where is the unit of work?
→ Which transaction manager owns it?
→ Which calls participate?
→ What makes it commit or roll back?
```

Learn those questions before memorizing `@Transactional` attributes. The annotation is only one way Spring expresses this model.

The learning path follows those questions deliberately: first build the manager and resource-participation model, then learn declarative policy, rollback, and propagation, then compare programmatic and reactive transaction styles, and finally connect transaction events to production boundary decisions. Each later chapter adds one layer to the same unit-of-work mental model rather than introducing an unrelated feature.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-unit-of-work">Transaction as an Application Unit of Work</a>

<details>
<summary>Click for details</summary>

A transaction is most useful when its boundary matches a **business unit of work**: the smallest set of state changes that must succeed or fail together to preserve an invariant.

For example, placing an order might require an order row and an inventory reservation to be persisted together. The unit of work is not “one repository method”; it is the business operation that owns the invariant.

```text
placeOrder()
  ├─ save order
  ├─ reserve stock
  └─ record payment state

all required for one consistent outcome
```

This mental model drives later choices about propagation and rollback. A boundary that is too small can commit partial state. A boundary that is too large can hold locks and connections while unrelated work runs. Start with the invariant, then choose the transaction scope.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-service-boundary">Service Boundary vs Individual Repository Calls</a>

<details>
<summary>Click for details</summary>

Repository calls represent data-access operations; service methods usually represent business intentions. For that reason, the service/application layer is often the better place to define the transaction that groups several repository calls.

```java
@Transactional
public void placeOrder(Command command) {
    orders.insert(command.order());
    inventory.reserve(command.sku());
}
```

If each repository method opens and commits its own independent transaction, `orders.insert()` could commit before `inventory.reserve()` fails. A service-level boundary allows both operations to participate in one transaction when the resource manager supports it.

This is a design guideline, not a rule that every service method must be transactional. Read-only orchestration, remote-only workflows, and operations with different resource boundaries may need a different scope.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-framework-value">What Spring Adds Above Native Transaction APIs</a>

<details>
<summary>Click for details</summary>

Without Spring, application code can manage transactions directly through JDBC, JPA, R2DBC, JTA, or vendor APIs. The problem is not that those APIs cannot transact; the problem is that each exposes different lifecycle and integration details.

Spring adds a common policy model and separates it from the resource strategy:

```text
transaction policy
  propagation / isolation / timeout / rollback
            ↓
Spring TransactionManager abstraction
            ↓
JDBC / JPA / R2DBC / JTA / other resource strategy
```

This separation lets declarative and programmatic code reason about the same transaction concepts while concrete managers translate them to backend capabilities. Resource-specific behavior still matters—Spring cannot make an unsupported isolation level, nested transaction, or distributed transaction appear by abstraction alone.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-local-boundary">Where Local Transaction Guarantees Stop</a>

<details>
<summary>Click for details</summary>

A local transaction guarantees only what its transaction manager and participating resources can actually coordinate. It does not automatically cross process boundaries, threads, unrelated resource managers, or external services.

```text
local DB transaction
  ✓ rows managed by that transaction/resource
  ✗ HTTP service in another process
  ✗ arbitrary async task on another thread
  ✗ unrelated broker without coordinated transaction support
```

This boundary is essential because “transactional” is often mistaken for “the entire business workflow is atomic.” In distributed systems that assumption is usually false.

Later chapters show how transaction-bound events, `REQUIRES_NEW`, coordinated transaction managers, outbox-style designs, and explicit asynchronous boundaries address different problems. None should be treated as a universal extension of one local transaction.

</details>

- [Back to top](#back-to-top)
