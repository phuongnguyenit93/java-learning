<a id="back-to-top"></a>

# Programmatic Transaction Management

## Menu
- [Declarative vs Programmatic Demarcation](#transaction-declarative-vs-programmatic)
- [TransactionTemplate and Callback-Based Control](#transaction-template)
- [Direct PlatformTransactionManager and TransactionStatus Control](#transaction-direct-manager)
- [TransactionalOperator and Reactive Programmatic Styles](#transaction-transactional-operator)
- [Programmatic Control Trade-Offs](#transaction-programmatic-tradeoffs)

## <a id="transaction-declarative-vs-programmatic">Declarative vs Programmatic Demarcation</a>

<details>
<summary>Click for details</summary>

Declarative and programmatic transaction management use the same Spring transaction abstractions; they differ in where the boundary is expressed. `@Transactional` keeps policy outside the method body and is usually easier to read for stable service boundaries. Programmatic APIs make transaction control explicit in code.

Programmatic control is useful when the boundary is dynamic, when only a small portion of a method should be transactional, or when code must react to transaction status directly. The cost is coupling: business code now imports and reasons about Spring transaction APIs.

Prefer declarative demarcation for regular application-service units of work. Reach for programmatic APIs when they genuinely make the boundary more precise, not merely because they feel more "explicit".

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-template">TransactionTemplate and Callback-Based Control</a>

<details>
<summary>Click for details</summary>

`TransactionTemplate` is Spring's recommended convenience API for imperative programmatic transactions. It wraps a `PlatformTransactionManager` and executes a callback inside a managed transaction, handling begin/commit/rollback boilerplate around the callback.

```java
Order result = transactionTemplate.execute(status -> {
    Order order = repository.save(new Order());
    auditRepository.record(order.id());
    return order;
});
```

The template itself can be configured with propagation, isolation, timeout, read-only, and name. Instances are generally safe to share once configured, but changing configuration on a shared template changes the policy used by later executions.

Use the callback to express the unit of work; avoid burying a large unrelated workflow inside one template just because the API makes it easy to do so.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-direct-manager">Direct PlatformTransactionManager and TransactionStatus Control</a>

<details>
<summary>Click for details</summary>

The lowest-level common imperative API is `PlatformTransactionManager`: obtain a `TransactionStatus` from `getTransaction(definition)`, then call `commit(status)` or `rollback(status)`.

```java
TransactionStatus status = txManager.getTransaction(definition);
try {
    doWork();
} catch (RuntimeException | Error ex) {
    txManager.rollback(status);
    throw ex;
}
txManager.commit(status);
```

This style gives full control but also makes correct exception handling, cleanup, and nested participation your responsibility. The example catches both `RuntimeException` and `Error`, mirroring Spring's normal rollback expectations for unchecked failures; production code must also decide deliberately how checked failures are represented. Notice that `commit(status)` is outside the business-work `try`: a commit failure is already a transaction-completion failure and should not be followed blindly by another rollback attempt. `TransactionStatus` can expose whether the transaction is new, whether a savepoint exists, and whether rollback-only has been requested; it also supports explicit rollback-only signaling.

Prefer `TransactionTemplate` unless direct manager control is necessary. Reimplementing transaction workflow by hand increases the chance of commit/rollback mistakes.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-transactional-operator">TransactionalOperator and Reactive Programmatic Styles</a>

<details>
<summary>Click for details</summary>

`TransactionalOperator` is the reactive counterpart to `TransactionTemplate`. It works with a `ReactiveTransactionManager` and scopes a reactive sequence so subscription-time work participates in the transaction context.

```java
TransactionalOperator operator = TransactionalOperator.create(txManager);

Mono<Order> result = repository.save(order)
    .flatMap(saved -> audit.save(saved.id()).thenReturn(saved))
    .as(operator::transactional);
```

The operator style applies transaction semantics to the upstream publisher chain passed through it. The callback style can provide more explicit scoping when several publishers exist and only selected work should participate.

Do not call `block()` just to use imperative transaction reasoning inside a reactive flow. The transaction context is carried through Reactor context, not by assuming the same thread remains in use.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-programmatic-tradeoffs">Programmatic Control Trade-Offs</a>

<details>
<summary>Click for details</summary>

Programmatic control trades declarative separation for local precision. It is strongest when transaction scope depends on runtime branching, when a short transaction must be isolated from slow non-transactional work, or when rollback-only decisions must be made explicitly.

Its weaknesses are equally important:

- Spring transaction APIs appear in application code;
- nested callbacks can obscure the business flow;
- direct manager usage can duplicate infrastructure logic;
- mixing declarative and programmatic boundaries without a clear model can create surprising participation.

A useful review question is: **does the code need dynamic transaction control, or is a stable service-level contract enough?** If the latter, declarative transaction management is usually simpler.

</details>

- [Back to top](#back-to-top)
