<a id="back-to-top"></a>

# Propagation and Transaction Participation

## Menu
- [Physical Transactions vs Logical Transaction Scopes](#transaction-physical-logical)
- [PROPAGATION_REQUIRED and Shared Participation](#transaction-propagation-required)
- [PROPAGATION_REQUIRES_NEW and Resource Pressure](#transaction-propagation-requires-new)
- [PROPAGATION_NESTED and Savepoint Semantics](#transaction-propagation-nested)
- [SUPPORTS, MANDATORY, NOT_SUPPORTED, and NEVER](#transaction-propagation-supports)
- [Suspension and Resumption with REQUIRES_NEW and NOT_SUPPORTED](#transaction-propagation-suspension)
- [Rollback-Only Participation and UnexpectedRollbackException](#transaction-propagation-rollback-only)

## <a id="transaction-physical-logical">Physical Transactions vs Logical Transaction Scopes</a>

<details>
<summary>Click for details</summary>

Propagation describes how a **logical transactional scope** behaves when it is entered while another scope may already exist. The key is to separate logical scope from physical resource transaction.

Two methods annotated with `REQUIRED` can each have their own logical scope while participating in one physical database transaction. Conversely, `REQUIRES_NEW` creates a distinct physical transaction for the inner scope.

```text
serviceA() @Transactional(REQUIRED)  ← logical scope A
    └─ serviceB() @Transactional(REQUIRED) ← logical scope B
          both can share physical transaction T1
```

Rollback-only markers are attached to these logical decisions but eventually affect the physical transaction outcome. Understanding that separation makes `UnexpectedRollbackException`, `REQUIRES_NEW`, and `NESTED` much easier to reason about.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-propagation-required">PROPAGATION_REQUIRED and Shared Participation</a>

<details>
<summary>Click for details</summary>

`PROPAGATION_REQUIRED` is Spring's default. If no transaction exists, the current scope starts one. If a transaction already exists, the scope participates in it instead of starting another physical transaction.

That makes `REQUIRED` a strong default for service-facade flows where several repository operations must succeed or fail together. A participating scope normally inherits the existing transaction characteristics; locally declared isolation, timeout, or read-only hints do not create a second physical transaction.

An important consequence is rollback-only propagation. If an inner `REQUIRED` scope decides that the shared transaction must roll back, the outer scope cannot later turn the same physical transaction back into a committable one.

Use `validateExistingTransaction` on supporting transaction managers when you want incompatible isolation/read-only declarations on participating scopes to be rejected instead of silently joining the existing characteristics.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-propagation-requires-new">PROPAGATION_REQUIRES_NEW and Resource Pressure</a>

<details>
<summary>Click for details</summary>

`PROPAGATION_REQUIRES_NEW` always runs the affected scope in an independent physical transaction. If an outer transaction exists, Spring suspends its transactional resources/synchronizations as supported by the transaction manager, starts the inner transaction, then resumes the outer context afterward.

The inner transaction has its own commit/rollback decision, isolation, timeout, and read-only characteristics. Its locks can be released when the inner transaction finishes, independently of the outer transaction.

The trade-off is resource pressure. With JDBC-style local transactions, the outer transaction may keep one connection while the inner transaction needs another. Under concurrency this can exhaust the connection pool or contribute to deadlock. Size pools with this pattern in mind; the Spring reference specifically warns that the pool should exceed concurrent threads by at least one when this pattern is used.

Use `REQUIRES_NEW` for intentionally independent work, not as a generic way to "fix" rollback behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-propagation-nested">PROPAGATION_NESTED and Savepoint Semantics</a>

<details>
<summary>Click for details</summary>

`PROPAGATION_NESTED` is different from `REQUIRES_NEW`. Its classic Spring semantics use **one physical transaction with savepoints**. An inner nested scope can roll back to its savepoint while the outer physical transaction may continue.

```text
physical transaction T1
  savepoint S1
    nested work
    ↓ failure
  rollback to S1
  outer work can continue
```

This capability depends on the underlying transaction manager and resource. Spring's reference describes it primarily for JDBC resource transactions backed by savepoints, such as `DataSourceTransactionManager`. Do not assume every JPA, JTA, or reactive manager supports the same nested semantics.

Choose `NESTED` only when partial rollback inside one physical transaction is truly the desired model; otherwise the code becomes difficult to reason about.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-propagation-supports">SUPPORTS, MANDATORY, NOT_SUPPORTED, and NEVER</a>

<details>
<summary>Click for details</summary>

The remaining propagation modes express preconditions around the current transaction context:

- `SUPPORTS`: join a transaction if one exists; otherwise execute without an actual transaction. With synchronization enabled, this can still define a synchronization scope, so it is not always identical to "no transaction infrastructure at all".
- `MANDATORY`: require an existing transaction or fail.
- `NOT_SUPPORTED`: execute non-transactionally, suspending an existing transaction when the manager can do so.
- `NEVER`: require that no transaction exists; fail if one is active.

These modes are useful when the method contract truly requires or forbids a transaction. Avoid using them just because they sound more explicit; propagation should encode a real boundary invariant.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-propagation-suspension">Suspension and Resumption with REQUIRES_NEW and NOT_SUPPORTED</a>

<details>
<summary>Click for details</summary>

Suspension is a transaction-manager capability, not a universal property of every backend. `REQUIRES_NEW` and `NOT_SUPPORTED` need the surrounding transaction context to be suspended so the inner scope can run with a different transactional state.

Conceptually, suspension means Spring preserves the outer transaction's bound resources and synchronizations, removes them from the current execution context while the inner scope runs, then restores them afterward. It does **not** mean the outer transaction has committed.

Some managers cannot perform true suspension without additional platform integration. For example, JTA suspension depends on access to an appropriate Jakarta `TransactionManager`. Therefore, always check the concrete manager's contract instead of assuming propagation constants guarantee identical backend behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-propagation-rollback-only">Rollback-Only Participation and UnexpectedRollbackException</a>

<details>
<summary>Click for details</summary>

When nested `REQUIRED` scopes share one physical transaction, each logical scope can independently decide that the shared transaction must roll back. For example, an exception can escape the inner transactional boundary and cause that scope to mark the shared transaction rollback-only even if an outer caller later catches or translates the exception. An exception caught entirely inside the inner method does not, by itself, trigger Spring's interceptor rollback decision unless code explicitly marks rollback-only or another rollback rule is activated.

The outer scope may be unaware of that decision and eventually call commit. Spring then raises `UnexpectedRollbackException` because silently returning success would be misleading.

This behavior is not a bug in `REQUIRED`; it protects transaction outcome transparency. If the inner work must be able to fail without dooming the outer physical transaction, reconsider the unit-of-work boundary. Depending on requirements, that may mean `REQUIRES_NEW`, savepoint-based `NESTED`, compensating logic, or simply a different service decomposition.

</details>

- [Back to top](#back-to-top)
